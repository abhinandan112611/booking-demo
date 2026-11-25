package booking.web;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/health: borrows ONE pooled connection, runs SELECT 1 and reads back the session
 * settings applied by the pool's connection-init-sql (time zone and the three timeouts).
 * The connection is returned to the pool by try-with-resources, also on failure.
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            int one;
            try (ResultSet rs = st.executeQuery("SELECT 1")) {
                rs.next();
                one = rs.getInt(1);
            }
            Map<String, String> session = new LinkedHashMap<>();
            for (String setting : new String[] {"TimeZone", "lock_timeout", "statement_timeout",
                                                "idle_in_transaction_session_timeout"}) {
                try (ResultSet rs = st.executeQuery("SHOW " + setting)) {
                    rs.next();
                    session.put(setting, rs.getString(1));
                }
            }
            out.put("status", "UP");
            out.put("db", "UP");
            out.put("select1", one);
            out.put("session", session);
            out.put("requestId", MDC.get(RequestIdFilter.MDC_KEY));
            return ResponseEntity.ok(out);
        } catch (SQLException e) {
            log.warn("event=HEALTH_DB_DOWN sqlstate={}", e.getSQLState());
            out.put("status", "DOWN");
            out.put("db", "DOWN");
            out.put("requestId", MDC.get(RequestIdFilter.MDC_KEY));
            return ResponseEntity.status(503).body(out);
        }
    }
}
