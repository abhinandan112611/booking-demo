package booking;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class Smoke {
    public static void main(String[] args) throws Exception {
        try (Connection c = Db.connect();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            rs.next();
            System.out.println("Java connected to PostgreSQL. SELECT 1 -> " + rs.getInt(1));
        }
    }
}