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

public class Scenario6DifferentSlots {

    static PrintWriter out;

    static synchronized void log(String msg) {
        String line = "[" + Instant.now() + "] " + msg;
        System.out.println(line);
        if (out != null) { out.println(line); out.flush(); }
    }

    public static void main(String[] args) throws Exception {
        int n = 6;
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path file = Path.of("evidence", "scenario6-different-slots-" + stamp + ".log");
        Files.createDirectories(file.getParent());
        out = new PrintWriter(Files.newBufferedWriter(file));

        try (Connection c = Db.connect(); var st = c.createStatement()) {
            st.execute("TRUNCATE bookings RESTART IDENTITY");
            st.execute("UPDATE slots SET status='AVAILABLE', held_by=NULL, held_until=NULL");
        }
        log("Environment: " + System.getProperty("os.name") + ", Java " + System.getProperty("java.version"));
        log("SCENARIO 6 different slots: " + n + " users, user N takes slot N, hold then confirm");

        BookingService svc = new BookingService();
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Outcome>> fs = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int u = 1; u <= n; u++) {
                final int uid = u;
                fs.add(pool.submit(() -> {
                    try (Connection c = Db.connect()) {
                        gate.await();
                        long t0 = System.nanoTime();
                        Outcome h = svc.requestHold(c, uid, uid, 300);
                        Outcome o = h == Outcome.HELD ? svc.confirm(c, uid, uid) : h;
                        long ms = (System.nanoTime() - t0) / 1_000_000;
                        log("user=" + uid + " slot=" + uid + " hold=" + h + " final=" + o + " took " + ms + " ms");
                        return o;
                    }
                }));
            }
            Thread.sleep(1500);
            gate.countDown();
            int ok = 0;
            for (var f : fs) if (f.get() == Outcome.CONFIRMED) ok++;
            log("CONFIRMED=" + ok + " of " + n);
            log(ok == n ? "RESULT: PASS, all users on different slots succeeded" : "RESULT: FAIL");
        }

        int bad = 0;
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT slot_id, COUNT(*) FROM bookings WHERE status='CONFIRMED' GROUP BY slot_id HAVING COUNT(*) > 1");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { bad++; log("INVARIANT VIOLATED: slot " + rs.getInt(1)); }
        }
        log(bad == 0 ? "INVARIANT: holds, no double booking" : "INVARIANT: FAILED");
        out.close();
    }
}