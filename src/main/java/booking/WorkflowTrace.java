package booking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

public class WorkflowTrace {

    static void log(String msg) {
        System.out.println("[" + Instant.now() + "] " + msg);
    }

    static void showAvailability(Connection c, int resourceId) throws Exception {
        String sql = "SELECT id, start_time, end_time, "
                + "CASE WHEN status = 'BOOKED' THEN 'BOOKED' "
                + "WHEN held_until > now() THEN 'HELD' ELSE 'AVAILABLE' END AS display_status "
                + "FROM slots WHERE resource_id = ? ORDER BY id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, resourceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    log("slot=" + rs.getInt("id") + " " + rs.getTimestamp("start_time")
                            + " -> " + rs.getString("display_status"));
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        UnsafeBookingService svc = new UnsafeBookingService();
        try (Connection c = Db.connect()) {
            log("AVAILABILITY before booking");
            showAvailability(c, 1);
            log("slot=1 user=1 BOOK requested");
            String result = svc.book(c, 1, 1);
            log("slot=1 user=1 result=" + result);
            log("AVAILABILITY after booking");
            showAvailability(c, 1);
        }
    }
}