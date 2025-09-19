package test;

import java.sql.*;

public class DiagInventorySchemaMain {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:sqlite:fost.db";
        try (Connection c = DriverManager.getConnection(url);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(inventory_state)")) {
            System.out.println("Kolone u inventory_state:");
            while (rs.next()) {
                System.out.printf("- %s (%s)%n",
                        rs.getString("name"),
                        rs.getString("type"));
            }
        }
    }
}