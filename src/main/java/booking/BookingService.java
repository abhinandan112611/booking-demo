package booking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

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

    private static final String CANCEL_BOOKING =
        "UPDATE bookings SET status = 'CANCELLED', cancelled_at = now() "
      + "WHERE slot_id = ? AND user_id = ? AND status = 'CONFIRMED'";

    private static final String FREE_SLOT =
        "UPDATE slots SET status = 'AVAILABLE' WHERE id = ?";

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

    /**
     * The one lock-order helper. Locks slot rows one by one in ascending id,
     * so no two transactions can wait on each other in a cycle.
     * Returns false if any slot does not exist.
     */
    public boolean lockSlotsInOrder(Connection c, int... ids) throws SQLException {
        int[] ordered = ids.clone();
        Arrays.sort(ordered);
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM slots WHERE id = ? FOR UPDATE")) {
            for (int id : ordered) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                }
            }
        }
        return true;
    }

    /** One transaction: lock slot row first, cancel own confirmed booking, free the slot. */
    public Outcome cancel(Connection c, int slotId, int userId) throws SQLException {
        c.setAutoCommit(false);
        try {
            if (!lockSlotsInOrder(c, slotId)) {
                c.rollback();
                return Outcome.INVALID;
            }
            try (PreparedStatement cancel = c.prepareStatement(CANCEL_BOOKING);
                 PreparedStatement free = c.prepareStatement(FREE_SLOT)) {
                cancel.setInt(1, slotId);
                cancel.setInt(2, userId);
                if (cancel.executeUpdate() == 0) {
                    c.rollback();
                    return Outcome.NOTHING_TO_CANCEL;
                }
                free.setInt(1, slotId);
                free.executeUpdate();
                c.commit();
                return Outcome.CANCELLED;
            }
        } catch (SQLException e) {
            c.rollback();
            String state = e.getSQLState();
            if ("40P01".equals(state) || "55P03".equals(state)) return Outcome.RETRY;
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    /**
     * Move a confirmed booking from oldSlot to newSlot.
     * Step 1: hold the new slot (single conditional UPDATE, outside the transaction).
     * Step 2: one transaction, slot rows locked in ascending id order.
     */
    public Outcome modify(Connection c, int oldSlot, int newSlot, int userId, int holdSecs) throws SQLException {
        if (oldSlot == newSlot) return Outcome.INVALID;
        Outcome held = requestHold(c, newSlot, userId, holdSecs);
        if (held != Outcome.HELD) return Outcome.UNAVAILABLE;

        c.setAutoCommit(false);
        try {
            if (!lockSlotsInOrder(c, oldSlot, newSlot)) {
                c.rollback();
                return Outcome.INVALID;
            }
            try (PreparedStatement consume = c.prepareStatement(CONSUME);
                 PreparedStatement insert = c.prepareStatement(INSERT_BOOKING);
                 PreparedStatement cancel = c.prepareStatement(CANCEL_BOOKING);
                 PreparedStatement free = c.prepareStatement(FREE_SLOT)) {
                consume.setInt(1, newSlot);
                consume.setInt(2, userId);
                if (consume.executeUpdate() == 0) {
                    c.rollback();
                    return Outcome.REJECTED_HOLD_EXPIRED;
                }
                cancel.setInt(1, oldSlot);
                cancel.setInt(2, userId);
                if (cancel.executeUpdate() == 0) {
                    c.rollback();
                    return Outcome.NOTHING_TO_CANCEL;
                }
                free.setInt(1, oldSlot);
                free.executeUpdate();
                insert.setInt(1, newSlot);
                insert.setInt(2, userId);
                insert.executeUpdate();
                c.commit();
                return Outcome.MODIFIED;
            }
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