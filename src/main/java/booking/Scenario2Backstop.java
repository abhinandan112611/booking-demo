package booking;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Scenario2Backstop {

    static PrintWriter out;

    static synchronized void log(String msg) {
        String line = "[" + Instant.now() + "] " + msg;
        System.out.println(line);
        if (out != null) { out.println(line); out.flush(); }
    }

    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        int slotId = 1;
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path file = Path.of("evidence", "scenario2-backstop-" + stamp + ".log");
        Files.createDirectories(file.getParent());
        out = new PrintWriter(Files.newBufferedWriter(file));

        try (Connection c = Db.connect(); var st = c.createStatement()) {
            st.execute("TRUNCATE bookings RESTART IDENTITY");
            st.execute("UPDATE slots SET status='AVAILABLE', held_by=NULL, held_until=NULL");
        }
        log("Environment: " + System.getProperty("os.name") + ", Java " + System.getProperty("java.version"));
        log("SCENARIO 2 backstop alone: threads=" + n + " slot=" + slotId
            + " (unique index PRESENT, hold bypassed, direct inserts)");

        CountDownLatch gate = new CountDownLatch(1);
        List<Future<String>> fs = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int u = 1; u <= n; u++) {
                final int uid = u;
                fs.add(pool.submit(() -> {
                    try (Connection c = Db.connect()) {
                        gate.await();
                        try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO bookings (slot_id, user_id) VALUES (?, ?)")) {
                            ps.setInt(1, slotId);
                            ps.setInt(2, uid);
                            ps.executeUpdate();
                            log("slot=" + slotId + " user=" + uid + " INSERT ok -> CONFIRMED");
                            return "OK";
                        } catch (SQLException e) {
                            log("slot=" + slotId + " user=" + uid + " INSERT failed (" + e.getSQLState() + ") -> REJECTED");
                            return e.getSQLState();
                        }
                    }
                }));
            }
            Thread.sleep(1500);
            gate.countDown();
            int ok = 0, dup = 0, other = 0;
            for (var f : fs) {
                String r = f.get();
                if ("OK".equals(r)) ok++;
                else if ("23505".equals(r)) dup++;
                else other++;
            }
            log("successes=" + ok + " rejected_23505=" + dup + " other=" + other);
        }

        int bad = 0;
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT slot_id, COUNT(*) FROM bookings WHERE status='CONFIRMED' GROUP BY slot_id HAVING COUNT(*) > 1");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { bad++; log("INVARIANT VIOLATED: slot " + rs.getInt(1)); }
        }
        log(bad == 0 ? "RESULT: invariant holds, no double booking" : "RESULT: FAILED, double booking present");
        out.close();
    }
}