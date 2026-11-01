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

public class CancelTest {

    static PrintWriter out;

    static void log(String msg) {
        String line = "[" + Instant.now() + "] " + msg;
        System.out.println(line);
        if (out != null) { out.println(line); out.flush(); }
    }

    static int count(Connection c, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public static void main(String[] args) throws Exception {
        int slotId = 1, userA = 1, userB = 2;
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path file = Path.of("evidence", "cancel-test-" + stamp + ".log");
        Files.createDirectories(file.getParent());
        out = new PrintWriter(Files.newBufferedWriter(file));

        BookingService svc = new BookingService();
        try (Connection c = Db.connect(); var st = c.createStatement()) {
            st.execute("TRUNCATE bookings RESTART IDENTITY");
            st.execute("UPDATE slots SET status='AVAILABLE', held_by=NULL, held_until=NULL");
        }
        log("Environment: " + System.getProperty("os.name") + ", Java " + System.getProperty("java.version"));
        log("CANCEL TEST: slot " + slotId + ", user A=" + userA + ", user B=" + userB);

        boolean pass = true;
        try (Connection c = Db.connect()) {
            Outcome o;
            o = svc.requestHold(c, slotId, userA, 300);       log("A hold -> " + o);            pass &= o == Outcome.HELD;
            o = svc.confirm(c, slotId, userA);                log("A confirm -> " + o);         pass &= o == Outcome.CONFIRMED;
            o = svc.cancel(c, slotId, userB);                 log("B cancels A's booking -> " + o + " (expected NOTHING_TO_CANCEL)"); pass &= o == Outcome.NOTHING_TO_CANCEL;
            o = svc.cancel(c, slotId, userA);                 log("A cancels own booking -> " + o + " (expected CANCELLED)"); pass &= o == Outcome.CANCELLED;
            o = svc.cancel(c, slotId, userA);                 log("A cancels again -> " + o + " (expected NOTHING_TO_CANCEL)"); pass &= o == Outcome.NOTHING_TO_CANCEL;
            o = svc.requestHold(c, slotId, userB, 300);       log("B hold on freed slot -> " + o + " (expected HELD)"); pass &= o == Outcome.HELD;
            o = svc.confirm(c, slotId, userB);                log("B confirm (rebooking) -> " + o + " (expected CONFIRMED)"); pass &= o == Outcome.CONFIRMED;

            int cancelled = count(c, "SELECT COUNT(*) FROM bookings WHERE slot_id=1 AND status='CANCELLED'");
            int confirmed = count(c, "SELECT COUNT(*) FROM bookings WHERE slot_id=1 AND status='CONFIRMED'");
            log("rows kept for history: CANCELLED=" + cancelled + " (expected 1), CONFIRMED=" + confirmed + " (expected 1)");
            pass &= cancelled == 1 && confirmed == 1;
        }
        log(pass ? "RESULT: PASS" : "RESULT: FAIL");
        out.close();
    }
}