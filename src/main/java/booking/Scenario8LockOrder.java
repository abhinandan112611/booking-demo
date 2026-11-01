package booking;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Scenario8LockOrder {

    static PrintWriter out;

    static synchronized void log(String msg) {
        String line = "[" + Instant.now() + "] " + msg;
        System.out.println(line);
        if (out != null) { out.println(line); out.flush(); }
    }

    static void lockOne(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM slots WHERE id = ? FOR UPDATE")) {
            ps.setInt(1, id);
            ps.executeQuery().close();
        }
    }

    /** Returns "OK" or the SQLState of the failure. */
    static String lockTask(BookingService svc, boolean ordered, int first, int second,
                           CountDownLatch gate) throws Exception {
        try (Connection c = Db.connect()) {
            c.setAutoCommit(false);
            gate.await();
            try {
                if (ordered) {
                    svc.lockSlotsInOrder(c, first, second);
                    Thread.sleep(300);
                } else {
                    lockOne(c, first);
                    Thread.sleep(300);
                    lockOne(c, second);
                }
                c.commit();
                return "OK";
            } catch (SQLException e) {
                c.rollback();
                return e.getSQLState();
            }
        }
    }

    /** Two threads lock the same pair in opposite argument order. */
    static String[] runPair(BookingService svc, boolean ordered, int a, int b) throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> f1 = pool.submit(() -> lockTask(svc, ordered, a, b, gate));
            Future<String> f2 = pool.submit(() -> lockTask(svc, ordered, b, a, gate));
            Thread.sleep(500);
            gate.countDown();
            return new String[] { f1.get(), f2.get() };
        }
    }

    public static void main(String[] args) throws Exception {
        int rounds = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int a = 5, b = 6;
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path file = Path.of("evidence", "scenario8-lock-order-" + stamp + ".log");
        Files.createDirectories(file.getParent());
        out = new PrintWriter(Files.newBufferedWriter(file));

        BookingService svc = new BookingService();
        log("Environment: " + System.getProperty("os.name") + ", Java " + System.getProperty("java.version"));
        log("SCENARIO 8 lock order: slots " + a + " and " + b + ", thread 1 args (" + a + "," + b
            + "), thread 2 args (" + b + "," + a + ")");

        String[] r = runPair(svc, false, a, b);
        log("PART 1 control (locks in given order, no sorting): thread1=" + r[0] + " thread2=" + r[1]
            + "  (40P01 = deadlock detected, 55P03 = lock timeout, OK = no collision this time)");

        int allOk = 0;
        for (int i = 1; i <= rounds; i++) {
            r = runPair(svc, true, a, b);
            boolean ok = "OK".equals(r[0]) && "OK".equals(r[1]);
            if (ok) allOk++;
            log("PART 2 round " + i + " (lockSlotsInOrder): thread1=" + r[0] + " thread2=" + r[1]);
        }
        log("PART 2 rounds where both threads succeeded = " + allOk + " of " + rounds);
        log(allOk == rounds ? "RESULT: PASS, no deadlock and no timeout with ordered locking"
                            : "RESULT: FAIL");
        out.close();
    }
}