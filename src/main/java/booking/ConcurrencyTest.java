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

public class ConcurrencyTest {

    static PrintWriter out;

    static synchronized void log(String msg) {
        String line = "[" + Instant.now() + "] " + msg;
        System.out.println(line);
        if (out != null) { out.println(line); out.flush(); }
    }

    static void resetData() throws Exception {
        try (Connection c = Db.connect();
             var st = c.createStatement()) {
            st.execute("TRUNCATE bookings RESTART IDENTITY");
            st.execute("UPDATE slots SET status='AVAILABLE', held_by=NULL, held_until=NULL");
        }
    }

    static int invariantViolations() throws Exception {
        String sql = "SELECT slot_id, COUNT(*) FROM bookings WHERE status='CONFIRMED' "
                + "GROUP BY slot_id HAVING COUNT(*) > 1";
        int bad = 0;
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                bad++;
                log("INVARIANT VIOLATED: slot " + rs.getInt(1) + " has " + rs.getInt(2) + " CONFIRMED bookings");
            }
        }
        return bad;
    }

    static void baselineRace(int n, int slotId) throws Exception {
        log("SCENARIO 1 baseline (defect): threads=" + n + " slot=" + slotId + " (unique index must be ABSENT)");
        resetData();
        UnsafeBookingService svc = new UnsafeBookingService();
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int u = 1; u <= n; u++) {
                final int uid = u;
                results.add(pool.submit(() -> {
                    try (Connection c = Db.connect()) {
                        gate.await();
                        String r = svc.book(c, slotId, uid);
                        log("slot=" + slotId + " user=" + uid + " -> " + r);
                        return r;
                    }
                }));
            }
            Thread.sleep(1500);      // let every task open its connection and wait at the gate
            gate.countDown();        // release all at once
            int confirmed = 0;
            for (var f : results) if ("CONFIRMED".equals(f.get())) confirmed++;
            log("threads told CONFIRMED = " + confirmed);
        }
        int bad = invariantViolations();
        log(bad > 0 ? "RESULT: DOUBLE BOOKING REPRODUCED (this test is MEANT to fail)"
                    : "RESULT: no double booking this run (run again)");
    }

    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path file = Path.of("evidence", "scenario1-baseline-" + stamp + ".log");
        Files.createDirectories(file.getParent());
        out = new PrintWriter(Files.newBufferedWriter(file));
        log("Environment: " + System.getProperty("os.name") + ", Java " + System.getProperty("java.version"));
        baselineRace(n, 1);
        out.close();
    }
}