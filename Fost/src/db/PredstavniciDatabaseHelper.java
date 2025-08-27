package db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
//** Helper class for managing 'predstavnici' table in SQLite database. Table schema://
// - id (INTEGER, PRIMARY KEY, AUTOINCREMENT)
public class PredstavniciDatabaseHelper {

    private static final String DB_URL = "jdbc:sqlite:fost.db";
    private static final String TABLE_NAME = "predstavnici";

    // SQL konstante
    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "naziv TEXT UNIQUE NOT NULL" +
            ")";

    private static final String SQL_SELECT_ALL =
            "SELECT naziv FROM " + TABLE_NAME + " ORDER BY naziv";

    private static final String SQL_INSERT =
            "INSERT OR IGNORE INTO " + TABLE_NAME + " (naziv) VALUES (?)";

    private static final String SQL_UPDATE =
            "UPDATE " + TABLE_NAME + " SET naziv = ? WHERE id = ?";

    private static final String SQL_DELETE =
            "DELETE FROM " + TABLE_NAME + " WHERE id = ?";

    /** Kreira tablicu ako ne postoji */
    public static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(SQL_CREATE_TABLE);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Dohvaća sve predstavnike sortirane po nazivu */
    public static List<String> loadAllPredstavnici() {
        List<String> lista = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL_SELECT_ALL)) {
            while (rs.next()) {
                lista.add(rs.getString("naziv"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /** Dodaje predstavnika ako ne postoji (ignorira duplikate) */
    public static void addPredstavnik(String naziv) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
            ps.setString(1, safeString(naziv));
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Ažurira postojećeg predstavnika po ID-u */
    public static void updatePredstavnik(int id, String naziv) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
            ps.setString(1, safeString(naziv));
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Briše predstavnika po ID-u */
    public static void deletePredstavnik(int id) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Helper metoda za siguran String (bez null) */
    private static String safeString(String val) {
        return val != null ? val.trim() : "";
    }
}
