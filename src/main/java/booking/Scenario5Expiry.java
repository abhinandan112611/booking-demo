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

public class Scenario5Expiry {

    static PrintWriter out;

    static void log(String msg) {
        String line = "[" + Instant.now() + "] " + msg;
        System.out.println(line);
        if (out != null) { out.println(line); out.flush(); }
    }

    public static void main(String[] args) throws Exception {
        int slotId = 1, userA = 1, userB = 2, holdSecs = 5;
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path file = Path.of("evidence", "scenario5-expiry-" + stamp + ".log");
        Files.createDirectories(file.getParent());
        out = new PrintWriter(Files.newBufferedWriter(file));

        BookingService svc = new BookingService();
        try (Connection c = Db.connect(); var st = c.createStatement()) {
            st.execute("TRUNCATE bookings RESTART IDENTITY");
            st.execute("UPDATE slots SET status='AVAILABLE', held_by=NULL, held_until=NULL");
        }
        log("Environment: " + System.getProperty("os.name") + ", Java " + System.getProperty("java.version"));
        log("SCENARIO 5 expiry: hold length " + holdSecs + "s (short, test aid; default is 300s)");

        try (Connection a = Db.connect(); Connection b = Db.connect()) {
            log("user A hold -> " + svc.requestHold(a, slotId, userA, holdSecs));
            log("user B hold while A holds -> " + svc.requestHold(b, slotId, userB, holdSecs) + " (expected UNAVAILABLE)");
            log("waiting " + (holdSecs + 2) + "s for A's hold to lapse (no background job runs)...");
            Thread.sleep((holdSecs + 2) * 1000L);
            Outcome bHold = svc.requestHold(b, slotId, userB, 300);
            log("user B hold after expiry -> " + bHold + " (expected HELD)");
            Outcome aConfirm = svc.confirm(a, slotId, userA);
            log("user A confirm -> " + aConfirm + " (expected REJECTED_HOLD_EXPIRED)");
            Outcome bConfirm = svc.confirm(b, slotId, userB);
            log("user B confirm -> " + bConfirm + " (expected CONFIRMED)");
            boolean pass = bHold == Outcome.HELD && aConfirm == Outcome.REJECTED_HOLD_EXPIRED
                    && bConfirm == Outcome.CONFIRMED;
            log(pass ? "RESULT: PASS, expired hold freed lazily" : "RESULT: FAIL");
        }

        int bad = 0;
        try (Connection c = Db.connect();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT slot_id, COUNT(*) FROM bookings WHERE status='CONFIRMED' GROUP BY slot_id HAVING COUNT(*) > 1");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { bad++; log("INVARIANT VIOLATED: slot " + rs.getInt(1)); }
        }
        log(bad == 0 ? "INVARIANT: holds, no double booking" : "INVARIANT: FAILED");
        out.close();
    }
}