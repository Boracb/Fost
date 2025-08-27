package test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class ClearKomitenti {
    private static final String DB_URL = "jdbc:sqlite:fost.db";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            stmt.execute("DELETE FROM komitenti");
            System.out.println("Svi podaci iz tablice 'komitenti' su obrisani.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
