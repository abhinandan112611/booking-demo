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

public class Scenario9Atomicity {

    static PrintWriter out;

    static void log(String msg) {
        String line = "[" + Instant.now() + "] " + msg;
        System.out.println(line);
        if (out != null) { out.println(line); out.flush(); }
    }

    static String slotState(Connection c, int slotId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT status, held_by FROM slots WHERE id = ?")) {
            ps.setInt(1, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return "status=" + rs.getString(1) + " held_by=" + rs.getObject(2);
            }
        }
    }

    static int bookingRows(Connection c, int slotId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM bookings WHERE slot_id = ?")) {
            ps.setInt(1, slotId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public static void main(String[] args) throws Exception {
        int slotId = 1, userId = 1, badUser = 999;
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path file = Path.of("evidence", "scenario9-atomicity-" + stamp + ".log");
        Files.createDirectories(file.getParent());
        out = new PrintWriter(Files.newBufferedWriter(file));

        BookingService svc = new BookingService();
        try (Connection c = Db.connect(); var st = c.createStatement()) {
            st.execute("TRUNCATE bookings RESTART IDENTITY");
            st.execute("UPDATE slots SET status='AVAILABLE', held_by=NULL, held_until=NULL");
        }
        log("Environment: " + System.getProperty("os.name") + ", Java " + System.getProperty("java.version"));
        log("SCENARIO 9 atomicity: failure forced between the two statements of confirm (insert uses user id "
            + badUser + ", which does not exist)");

        boolean pass = true;
        try (Connection c = Db.connect()) {
            Outcome h = svc.requestHold(c, slotId, userId, 300);
            log("hold -> " + h + "; before: " + slotState(c, slotId) + ", booking rows=" + bookingRows(c, slotId));
            pass &= h == Outcome.HELD;

            c.setAutoCommit(false);
            String failure = "none";
            try {
                try (PreparedStatement consume = c.prepareStatement(
                        "UPDATE slots SET status='BOOKED', held_by=NULL, held_until=NULL "
                      + "WHERE id=? AND status='AVAILABLE' AND held_by=? AND held_until > now()")) {
                    consume.setInt(1, slotId);
                    consume.setInt(2, userId);
                    int rows = consume.executeUpdate();
                    log("statement 1 (consume hold) updated " + rows + " row(s)");
                    pass &= rows == 1;
                }
                String midway = slotState(c, slotId);
                log("inside the transaction, after statement 1: " + midway + " (expected status=BOOKED)");
                pass &= midway.startsWith("status=BOOKED");

                try (PreparedStatement insert = c.prepareStatement(
                        "INSERT INTO bookings (slot_id, user_id) VALUES (?, ?)")) {
                    insert.setInt(1, slotId);
                    insert.setInt(2, badUser);
                    insert.executeUpdate();
                }
            } catch (SQLException e) {
                failure = e.getSQLState();
                c.rollback();
            } finally {
                c.setAutoCommit(true);
            }
            log("statement 2 (insert) failed with SQLState " + failure + " (expected 23503), rolled back");
            pass &= "23503".equals(failure);

            String after = slotState(c, slotId);
            int rows = bookingRows(c, slotId);
            log("after rollback: " + after + ", booking rows=" + rows
                + " (expected status=AVAILABLE, booking rows=0)");
            pass &= after.startsWith("status=AVAILABLE") && rows == 0;
            pass &= after.contains("held_by=" + userId);
        }
        log(pass ? "RESULT: PASS, neither change is visible after rollback" : "RESULT: FAIL");
        out.close();
    }
}