package test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class TestDeleteAllNarudzbe {

    private static final String DB_URL = "jdbc:sqlite:fost.db";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            int deletedRows = stmt.executeUpdate("DELETE FROM narudzbe");
            System.out.println("[INFO] Obrisano redova: " + deletedRows);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
