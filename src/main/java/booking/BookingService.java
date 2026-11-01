package booking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BookingService {

    private static final String HOLD =
        "UPDATE slots SET held_by = ?, held_until = now() + make_interval(secs => ?) "
      + "WHERE id = ? AND status = 'AVAILABLE' "
      + "AND (held_until IS NULL OR held_until < now())";

    /** One statement: the availability check and the write are the same operation. */
    public Outcome requestHold(Connection c, int slotId, int userId, int seconds) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(HOLD)) {
            ps.setInt(1, userId);
            ps.setInt(2, seconds);
            ps.setInt(3, slotId);
            int rows = ps.executeUpdate();
            return rows == 1 ? Outcome.HELD : Outcome.UNAVAILABLE;
        }
    }
}