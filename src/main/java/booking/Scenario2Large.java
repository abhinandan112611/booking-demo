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
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class Scenario2Large {

    static PrintWriter out;

    static synchronized void log(String msg, boolean console) {
        String line = "[" + Instant.now() + "] " + msg;
        if (console) System.out.println(line);
        if (out != null) { out.println(line); out.flush(); }
    }

    static int scalar(String sql) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int maxConc = args.length > 1 ? Integer.parseInt(args[1]) : 50;
        int slotId = 1;
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path file = Path.of("evidence", "scenario2-large-" + stamp + ".log");
        Files.createDirectories(file.getParent());
        out = new PrintWriter(Files.newBufferedWriter(file));

        log("Environment: " + System.getProperty("os.name") + ", Java " + System.getProperty("java.version"), true);
        int idx = scalar("SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'one_confirmed_booking_per_slot'");
        if (idx == 0) {
            log("ABORT: the unique index is not present, this scenario needs it", true);
            out.close();
            return;
        }
        try (Connection c = Db.connect(); var st = c.createStatement()) {
            st.execute("TRUNCATE bookings RESTART IDENTITY");
            st.execute("UPDATE slots SET status='AVAILABLE', held_by=NULL, held_until=NULL");
        }
        log("SCENARIO 2 LARGE: " + n + " threads, slot=" + slotId + ", at most " + maxConc
            + " database connections at a time (unique index PRESENT, hold bypassed)", true);

        Semaphore sem = new Semaphore(maxConc);
        AtomicInteger current = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<String>> fs = new ArrayList<>();
        long t0;
        int ok = 0, dup = 0, other = 0;
        String firstOther = "none";
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int u = 1; u <= n; u++) {
                final int uid = u;
                final int dbUser = ((u - 1) % 50) + 1;
                fs.add(pool.submit(() -> {
                    gate.await();
                    sem.acquire();
                    try (Connection c = Db.connect()) {
                        int now = current.incrementAndGet();
                        peak.accumulateAndGet(now, Math::max);
                        try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO bookings (slot_id, user_id) VALUES (?, ?)")) {
                            ps.setInt(1, slotId);
                            ps.setInt(2, dbUser);
                            ps.executeUpdate();
                            log("thread=" + uid + " INSERT ok -> CONFIRMED", true);
                            return "OK";
                        } catch (SQLException e) {
                            log("thread=" + uid + " INSERT failed (" + e.getSQLState() + ")", false);
                            return e.getSQLState() == null ? "EXC" : e.getSQLState();
                        } finally {
                            current.decrementAndGet();
                        }
                    } catch (SQLException e) {
                        log("thread=" + uid + " connection failed (" + e.getSQLState() + ") " + e.getMessage(), false);
                        return "CONN-" + e.getSQLState();
                    } finally {
                        sem.release();
                    }
                }));
            }
            Thread.sleep(1500);
            t0 = System.nanoTime();
            gate.countDown();
            for (var f : fs) {
                String r = f.get();
                if ("OK".equals(r)) ok++;
                else if ("23505".equals(r)) dup++;
                else { other++; if (firstOther.equals("none")) firstOther = r; }
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        log("successes=" + ok + " rejected_23505=" + dup + " other=" + other
            + " (first other state: " + firstOther + ")", true);
        log("elapsed " + ms + " ms, peak simultaneous connections used = " + peak.get(), true);

        int confirmed = scalar("SELECT COUNT(*) FROM bookings WHERE slot_id = 1 AND status = 'CONFIRMED'");
        int rows = scalar("SELECT COUNT(*) FROM bookings");
        int bad = scalar("SELECT COUNT(*) FROM (SELECT slot_id FROM bookings WHERE status='CONFIRMED' "
                       + "GROUP BY slot_id HAVING COUNT(*) > 1) x");
        log("CONFIRMED rows for slot 1 = " + confirmed + ", total booking rows = " + rows
            + ", slots violating the invariant = " + bad, true);
        boolean pass = ok == 1 && dup == n - 1 && other == 0 && confirmed == 1 && rows == 1 && bad == 0;
        log(pass ? "RESULT: PASS, the unique index allowed exactly one booking out of " + n
                 : "RESULT: FAIL or inconclusive, read the numbers above", true);
        out.close();
    }
}