package booking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class BookingService {

    private static final String HOLD =
        "UPDATE slots SET held_by = ?, held_until = now() + make_interval(secs => ?) "
      + "WHERE id = ? AND status = 'AVAILABLE' "
      + "AND (held_until IS NULL OR held_until < now())";

    private static final String CONSUME =
        "UPDATE slots SET status = 'BOOKED', held_by = NULL, held_until = NULL "
      + "WHERE id = ? AND status = 'AVAILABLE' AND held_by = ? AND held_until > now()";

    private static final String INSERT_BOOKING =
        "INSERT INTO bookings (slot_id, user_id) VALUES (?, ?)";

    /** One statement: the availability check and the write are the same operation. */
    public Outcome requestHold(Connection c, int slotId, int userId, int seconds) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(HOLD)) {
            ps.setInt(1, userId);
            ps.setInt(2, seconds);
            ps.setInt(3, slotId);
            return ps.executeUpdate() == 1 ? Outcome.HELD : Outcome.UNAVAILABLE;
        }
    }

    /** One transaction: consume the hold, then insert the booking. */
    public Outcome confirm(Connection c, int slotId, int userId) throws SQLException {
        c.setAutoCommit(false);
        try (PreparedStatement consume = c.prepareStatement(CONSUME);
             PreparedStatement insert = c.prepareStatement(INSERT_BOOKING)) {
            consume.setInt(1, slotId);
            consume.setInt(2, userId);
            if (consume.executeUpdate() == 0) {
                c.rollback();
                return Outcome.REJECTED_HOLD_EXPIRED;
            }
            insert.setInt(1, slotId);
            insert.setInt(2, userId);
            insert.executeUpdate();
            c.commit();
            return Outcome.CONFIRMED;
        } catch (SQLException e) {
            c.rollback();
            String state = e.getSQLState();
            if ("23505".equals(state)) return Outcome.REJECTED_DUPLICATE;
            if ("40P01".equals(state) || "55P03".equals(state)) return Outcome.RETRY;
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }
}