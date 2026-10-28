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

public class RaceTrace {

    static PrintWriter out;

    static synchronized void log(String msg) {
        String line = "[" + Instant.now() + "] " + msg;
        System.out.println(line);
        if (out != null) { out.println(line); out.flush(); }
    }

    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        int slotId = 1;
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path file = Path.of("evidence", "race-trace-" + stamp + ".log");
        Files.createDirectories(file.getParent());
        out = new PrintWriter(Files.newBufferedWriter(file));

        try (Connection c = Db.connect(); var st = c.createStatement()) {
            st.execute("TRUNCATE bookings RESTART IDENTITY");
            st.execute("UPDATE slots SET status='AVAILABLE', held_by=NULL, held_until=NULL");
        }
        log("RACE TRACE: " + n + " users, slot " + slotId + ", no unique index, no hold");

        CountDownLatch gate = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int u = 1; u <= n; u++) {
                final int uid = u;
                fs.add(pool.submit(() -> {
                    String who = "user=" + uid;
                    try (Connection c = Db.connect()) {
                        c.setAutoCommit(false);
                        gate.await();

                        String status;
                        try (PreparedStatement ps = c.prepareStatement("SELECT status FROM slots WHERE id = ?")) {
                            ps.setInt(1, slotId);
                            try (ResultSet rs = ps.executeQuery()) { rs.next(); status = rs.getString(1); }
                        }
                        log(who + " READ    slot status = " + status);
                        if (!"AVAILABLE".equals(status)) {
                            c.rollback();
                            log(who + " STOP    slot not available");
                            return null;
                        }

                        Thread.sleep(50);   // test aid: widens the gap between READ and WRITE

                        try (PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO bookings (slot_id, user_id) VALUES (?, ?)")) {
                            ps.setInt(1, slotId); ps.setInt(2, uid);
                            ps.executeUpdate();
                        }
                        log(who + " WRITE   INSERT booking row");

                        try (PreparedStatement ps = c.prepareStatement(
                                "UPDATE slots SET status = 'BOOKED' WHERE id = ?")) {
                            ps.setInt(1, slotId);
                            ps.executeUpdate();
                        }
                        log(who + " WRITE   UPDATE slot -> BOOKED");

                        c.commit();
                        log(who + " COMMIT  -> CONFIRMED");
                    }
                    return null;
                }));
            }
            Thread.sleep(1500);   // let every user open a connection and wait at the gate
            gate.countDown();     // release all users at once
            for (var f : fs) f.get();
        }

        int total = 0;
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM bookings WHERE slot_id = ? AND status = 'CONFIRMED'")) {
            ps.setInt(1, slotId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); total = rs.getInt(1); }
        }
        log("CONFIRMED bookings for slot " + slotId + " = " + total);
        log(total > 1 ? "RESULT: DOUBLE BOOKING (this demo is MEANT to fail)"
                      : "RESULT: no double booking this run (run again)");
        out.close();
    }
}