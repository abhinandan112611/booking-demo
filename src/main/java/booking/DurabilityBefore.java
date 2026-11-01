package booking;

import java.sql.Connection;
import java.time.Instant;

public class DurabilityBefore {
    public static void main(String[] args) throws Exception {
        BookingService svc = new BookingService();
        try (Connection c = Db.connect(); var st = c.createStatement()) {
            st.execute("TRUNCATE bookings RESTART IDENTITY");
            st.execute("UPDATE slots SET status='AVAILABLE', held_by=NULL, held_until=NULL");
            Outcome h = svc.requestHold(c, 1, 1, 300);
            Outcome o = svc.confirm(c, 1, 1);
            System.out.println("[" + Instant.now() + "] SCENARIO 7 before crash: hold=" + h + " confirm=" + o
                + " (slot 1, user 1)");
        }
    }
}