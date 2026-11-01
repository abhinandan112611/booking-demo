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

public class ModifyTest {

    static PrintWriter out;

    static void log(String msg) {
        String line = "[" + Instant.now() + "] " + msg;
        System.out.println(line);
        if (out != null) { out.println(line); out.flush(); }
    }

    static String bookingsOf(Connection c, int userId) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT slot_id FROM bookings WHERE user_id=? AND status='CONFIRMED' ORDER BY slot_id")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) sb.append(sb.length() > 0 ? "," : "").append(rs.getInt(1));
            }
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    public static void main(String[] args) throws Exception {
        int userA = 1, userB = 2;
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path file = Path.of("evidence", "modify-test-" + stamp + ".log");
        Files.createDirectories(file.getParent());
        out = new PrintWriter(Files.newBufferedWriter(file));

        BookingService svc = new BookingService();
        try (Connection c = Db.connect(); var st = c.createStatement()) {
            st.execute("TRUNCATE bookings RESTART IDENTITY");
            st.execute("UPDATE slots SET status='AVAILABLE', held_by=NULL, held_until=NULL");
        }
        log("Environment: " + System.getProperty("os.name") + ", Java " + System.getProperty("java.version"));
        log("MODIFY TEST: A=" + userA + " starts on slot 1; B=" + userB + " takes slot 3");

        boolean pass = true;
        try (Connection c = Db.connect()) {
            Outcome o;
            o = svc.requestHold(c, 1, userA, 300); pass &= o == Outcome.HELD;
            o = svc.confirm(c, 1, userA);          log("A books slot 1 -> " + o); pass &= o == Outcome.CONFIRMED;
            o = svc.requestHold(c, 3, userB, 300); pass &= o == Outcome.HELD;
            o = svc.confirm(c, 3, userB);          log("B books slot 3 -> " + o); pass &= o == Outcome.CONFIRMED;

            o = svc.modify(c, 1, 3, userA, 300);
            log("A moves 1 -> 3 (taken by B) -> " + o + " (expected UNAVAILABLE)"); pass &= o == Outcome.UNAVAILABLE;
            String after = bookingsOf(c, userA);
            log("A's confirmed slots after failed move: " + after + " (expected 1)"); pass &= after.equals("1");

            o = svc.modify(c, 1, 2, userA, 300);
            log("A moves 1 -> 2 (free) -> " + o + " (expected MODIFIED)"); pass &= o == Outcome.MODIFIED;
            after = bookingsOf(c, userA);
            log("A's confirmed slots after move: " + after + " (expected 2)"); pass &= after.equals("2");

            o = svc.requestHold(c, 1, userB, 300);
            log("slot 1 freed, B hold -> " + o + " (expected HELD)"); pass &= o == Outcome.HELD;
        }
        log(pass ? "RESULT: PASS" : "RESULT: FAIL");
        out.close();
    }
}