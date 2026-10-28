package booking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Deliberately unprotected baseline. CHECK, then ACT, with no lock on the decision. */
public class UnsafeBookingService {

    private String readStatus(Connection c, int slotId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT status FROM slots WHERE id = ?")) {
            ps.setInt(1, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void insertBooking(Connection c, int slotId, int userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bookings (slot_id, user_id) VALUES (?, ?)")) {
            ps.setInt(1, slotId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    private void setSlotStatus(Connection c, int slotId, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE slots SET status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, slotId);
            ps.executeUpdate();
        }
    }

    public String book(Connection c, int slotId, int userId) throws Exception {
        String status = readStatus(c, slotId);               // CHECK (takes no lock)
        if (!"AVAILABLE".equals(status)) return "ALREADY_BOOKED";
        Thread.sleep(50);                                    // widens the window, baseline only
        insertBooking(c, slotId, userId);                    // ACT
        setSlotStatus(c, slotId, "BOOKED");
        return "CONFIRMED";
    }
}