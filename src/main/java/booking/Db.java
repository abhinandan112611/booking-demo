package booking;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Db {
    private static final String URL = "jdbc:postgresql://localhost:5432/bookingdb";

    public static Connection connect() throws SQLException {
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        if (user == null || password == null) {
            throw new IllegalStateException("Set DB_USER and DB_PASSWORD environment variables");
        }
        Connection c = DriverManager.getConnection(URL, user, password);
        try (Statement st = c.createStatement()) {
            st.execute("SET lock_timeout = '2s'");
            st.execute("SET statement_timeout = '5s'");
            st.execute("SET idle_in_transaction_session_timeout = '10s'");
        }
        return c;
    }
}