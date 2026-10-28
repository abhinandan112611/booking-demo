package booking;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Db {
    private static final String URL = "jdbc:postgresql://localhost:5432/bookingdb";

    public static Connection connect() throws SQLException {
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        if (user == null || password == null) {
            throw new IllegalStateException("Set DB_USER and DB_PASSWORD environment variables");
        }
        return DriverManager.getConnection(URL, user, password);
    }
}