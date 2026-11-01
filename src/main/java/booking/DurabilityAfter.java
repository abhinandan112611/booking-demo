package booking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

public class DurabilityAfter {
    public static void main(String[] args) throws Exception {
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT b.id, b.slot_id, b.user_id, b.status, s.status FROM bookings b "
               + "JOIN slots s ON s.id = b.slot_id WHERE b.status='CONFIRMED'");
             ResultSet rs = ps.executeQuery()) {
            int n = 0;
            while (rs.next()) {
                n++;
                System.out.println("[" + Instant.now() + "] booking id=" + rs.getInt(1) + " slot=" + rs.getInt(2)
                    + " user=" + rs.getInt(3) + " booking_status=" + rs.getString(4) + " slot_status=" + rs.getString(5));
            }
            System.out.println("[" + Instant.now() + "] SCENARIO 7 after crash: confirmed rows found = " + n
                + (n == 1 ? " -> PASS, booking survived" : " -> FAIL"));
        }
    }
}