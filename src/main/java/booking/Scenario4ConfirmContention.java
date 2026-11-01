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

public class Scenario4ConfirmContention {

    static PrintWriter out;

    static synchronized void log(String msg) {
        String line = "[" + Instant.now() + "] " + msg;
        System.out.println(line);
        if (out != null) { out.println(line); out.flush(); }
    }

    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        int slotId = 1;
        int holder = 1;
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path file = Path.of("evidence", "scenario4-confirm-contention-" + stamp + ".log");
        Files.createDirectories(file.getParent());
        out = new PrintWriter(Files.newBufferedWriter(file));

        BookingService svc = new BookingService();
        try (Connection c = Db.connect(); var st = c.createStatement()) {
            st.execute("TRUNCATE bookings RESTART IDENTITY");
            st.execute("UPDATE slots SET status='AVAILABLE', held_by=NULL, held_until=NULL");
            Outcome h = svc.requestHold(c, slotId, holder, 300);
            log("Environment: " + System.getProperty("os.name") + ", Java " + System.getProperty("java.version"));
            log("SCENARIO 4 confirm contention: threads=" + n + " slot=" + slotId
                + " holder=user " + holder + " (hold acquired: " + h + ")");
        }

        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Outcome>> fs = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int u = 1; u <= n; u++) {
                final int uid = u;
                fs.add(pool.submit(() -> {
                    try (Connection c = Db.connect()) {
                        gate.await();
                        Outcome o = svc.confirm(c, slotId, uid);
                        log("slot=" + slotId + " user=" + uid + " confirm -> " + o);
                        return o;
                    }
                }));
            }
            Thread.sleep(1500);
            gate.countDown();
            int confirmed = 0, expired = 0, other = 0;
            for (var f : fs) {
                Outcome o = f.get();
                if (o == Outcome.CONFIRMED) confirmed++;
                else if (o == Outcome.REJECTED_HOLD_EXPIRED) expired++;
                else other++;
            }
            log("CONFIRMED=" + confirmed + " REJECTED_HOLD_EXPIRED=" + expired + " other=" + other);
            log(confirmed == 1 && expired == n - 1 && other == 0
                ? "RESULT: PASS, only the holder confirmed" : "RESULT: FAIL");
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