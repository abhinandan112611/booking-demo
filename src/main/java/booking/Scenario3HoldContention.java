package booking;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Scenario3HoldContention {

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
        Path file = Path.of("evidence", "scenario3-hold-contention-" + stamp + ".log");
        Files.createDirectories(file.getParent());
        out = new PrintWriter(Files.newBufferedWriter(file));

        try (Connection c = Db.connect(); var st = c.createStatement()) {
            st.execute("TRUNCATE bookings RESTART IDENTITY");
            st.execute("UPDATE slots SET status='AVAILABLE', held_by=NULL, held_until=NULL");
        }
        log("Environment: " + System.getProperty("os.name") + ", Java " + System.getProperty("java.version"));
        log("SCENARIO 3 hold contention: threads=" + n + " slot=" + slotId + " hold=300s");

        BookingService svc = new BookingService();
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Outcome>> fs = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int u = 1; u <= n; u++) {
                final int uid = u;
                fs.add(pool.submit(() -> {
                    try (Connection c = Db.connect()) {
                        gate.await();
                        Outcome o = svc.requestHold(c, slotId, uid, 300);
                        log("slot=" + slotId + " user=" + uid + " -> " + o);
                        return o;
                    }
                }));
            }
            Thread.sleep(1500);
            gate.countDown();
            int held = 0, unavailable = 0, other = 0;
            for (var f : fs) {
                Outcome o = f.get();
                if (o == Outcome.HELD) held++;
                else if (o == Outcome.UNAVAILABLE) unavailable++;
                else other++;
            }
            log("HELD=" + held + " UNAVAILABLE=" + unavailable + " other=" + other);
            log(held == 1 && unavailable == n - 1 && other == 0
                ? "RESULT: PASS, exactly one holder" : "RESULT: FAIL");
        }

        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT held_by, held_until FROM slots WHERE id = ?")) {
            ps.setInt(1, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) log("slot " + slotId + " held_by=" + rs.getInt(1) + " held_until=" + rs.getTimestamp(2));
            }
        }
        out.close();
    }
}