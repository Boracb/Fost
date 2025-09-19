package test;

import java.sql.*;

public class DiagSchemaMain {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:sqlite:fost.db";
        for (String table : new String[]{"suppliers","products","inventory_state",
                "product_groups","product_group_assignment"}) {
            System.out.println("=== " + table + " ===");
            try (Connection c = DriverManager.getConnection(url);
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
                while (rs.next()) {
                    System.out.printf("- %s (%s)%n", rs.getString("name"), rs.getString("type"));
                }
            }
        }
    }
}