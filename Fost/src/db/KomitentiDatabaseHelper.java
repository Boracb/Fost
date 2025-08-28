package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.table.DefaultTableModel;
import model.KomitentInfo;

/**
 * Helper class for managing 'komitenti' table in SQLite database.
 *
 * Improvements:
 * - Uses an UPSERT statement so insertIfNotExists is atomic and can update
 *   trgovackiPredstavnik only when a non-empty value is provided.
 * - saveToDatabase(...) keeps legacy behavior (clear table + batch insert).
 * - insertIfNotExists returns boolean to indicate if insert/update happened.
 */
public class KomitentiDatabaseHelper {

    private static final String DB_URL = "jdbc:sqlite:fost.db";
    private static final String TABLE_NAME = "komitenti";

    // Create table: ensure komitentOpis is unique (one row per komitent)
    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    "komitentOpis TEXT NOT NULL, " +
                    "trgovackiPredstavnik TEXT, " +
                    "UNIQUE(komitentOpis)" +
                    ")";

    private static final String SQL_DELETE_ALL =
            "DELETE FROM " + TABLE_NAME;

    // Basic insert (used when table was cleared)
    private static final String SQL_INSERT =
            "INSERT INTO " + TABLE_NAME + " (komitentOpis, trgovackiPredstavnik) VALUES (?, ?)";

    // Upsert: on conflict on komitentOpis, update trgovackiPredstavnik only when
    // excluded.trgovackiPredstavnik is not an empty string.
    private static final String SQL_INSERT_UPSERT =
            "INSERT INTO " + TABLE_NAME + " (komitentOpis, trgovackiPredstavnik) VALUES (?, ?)\n" +
            "ON CONFLICT(komitentOpis) DO UPDATE SET trgovackiPredstavnik = " +
            "CASE WHEN excluded.trgovackiPredstavnik IS NOT NULL AND excluded.trgovackiPredstavnik <> '' " +
            "THEN excluded.trgovackiPredstavnik ELSE " + TABLE_NAME + ".trgovackiPredstavnik END";

    private static final String SQL_SELECT_ALL =
            "SELECT komitentOpis, trgovackiPredstavnik FROM " + TABLE_NAME;

    private static final String SQL_SELECT_BY_PREDSTAVNIK =
            "SELECT komitentOpis, trgovackiPredstavnik FROM " + TABLE_NAME + " WHERE trgovackiPredstavnik = ?";

    private static final String SQL_DELETE_ROW =
            "DELETE FROM " + TABLE_NAME + " WHERE komitentOpis = ?";

    private static final String SQL_DISTINCT_KOMITENTI =
            "SELECT DISTINCT komitentOpis FROM " + TABLE_NAME +
                    " WHERE komitentOpis IS NOT NULL AND komitentOpis <> '' ORDER BY komitentOpis";

    private static final String SQL_DISTINCT_PREDSTAVNICI =
            "SELECT DISTINCT trgovackiPredstavnik FROM " + TABLE_NAME +
                    " WHERE trgovackiPredstavnik IS NOT NULL AND trgovackiPredstavnik <> '' ORDER BY trgovackiPredstavnik";

    // ===== Initialization =====
    public static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(SQL_CREATE_TABLE);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ===== Delete all records =====
    public static synchronized void clearTable() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(SQL_DELETE_ALL);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ===== Save from list (legacy behavior: clear then insert all) =====
    public static synchronized void saveToDatabase(List<KomitentInfo> lista) {
        if (lista == null) return;
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try (Statement clean = conn.createStatement()) {
                clean.execute(SQL_DELETE_ALL);
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
                for (KomitentInfo k : lista) {
                    ps.setString(1, safeString(k.getKomitentOpis()));
                    ps.setString(2, safeString(k.getTrgovackiPredstavnik()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ===== Save from DefaultTableModel (legacy behavior: clear then insert all) =====
    public static synchronized void saveToDatabase(DefaultTableModel model) {
        if (model == null) return;
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try (Statement clean = conn.createStatement()) {
                clean.execute(SQL_DELETE_ALL);
            }
            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
                int idxKomitent = model.findColumn("komitentOpis");
                int idxPredstavnik = model.findColumn("trgovackiPredstavnik");
                if (idxKomitent < 0) idxKomitent = 2; // fallback index if column names differ
                if (idxPredstavnik < 0) idxPredstavnik = 15;
                for (int r = 0; r < model.getRowCount(); r++) {
                    ps.setString(1, safeString(model.getValueAt(r, idxKomitent)));
                    ps.setString(2, safeString(model.getValueAt(r, idxPredstavnik)));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ===== Load all rows =====
    public static List<Object[]> loadAllRows() {
        List<Object[]> lista = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL_SELECT_ALL)) {
            while (rs.next()) {
                String komitent = rs.getString("komitentOpis");
                String predstavnik = rs.getString("trgovackiPredstavnik");
                if (predstavnik == null) predstavnik = "";
                lista.add(new Object[]{komitent, predstavnik});
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    // ===== Filter by representative =====
    public static List<Object[]> loadAllRowsByPredstavnik(String predstavnik) {
        List<Object[]> lista = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_PREDSTAVNIK)) {
            ps.setString(1, safeString(predstavnik));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String komitent = rs.getString("komitentOpis");
                    String p = rs.getString("trgovackiPredstavnik");
                    if (p == null) p = "";
                    lista.add(new Object[]{komitent, p});
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    // ===== Delete row by komitentOpis (was by komitent+predstavnik) =====
    public static void deleteRow(String komitentOpis, String trgovackiPredstavnik) {
        // keep signature compatible: delete by komitentOpis only
        if (komitentOpis == null || komitentOpis.isBlank()) return;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE_ROW)) {
            ps.setString(1, safeString(komitentOpis));
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ===== Map komitent -> predstavnik (prefer non-empty predstavnik if duplicates exist) =====
    public static Map<String, String> loadKomitentPredstavnikMap() {
        Map<String, String> mapa = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL_SELECT_ALL)) {
            while (rs.next()) {
                String komitent = rs.getString("komitentOpis");
                String predstavnik = rs.getString("trgovackiPredstavnik");
                if (komitent == null) continue;
                if (predstavnik == null) predstavnik = "";
                String existing = mapa.get(komitent);
                // prefer non-empty predstavnik over empty
                if (existing == null || existing.isBlank()) {
                    mapa.put(komitent, predstavnik);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return mapa;
    }

    // ===== Lists of unique values =====
    public static List<String> loadAllKomitentNames() {
        return loadDistinctList(SQL_DISTINCT_KOMITENTI, "komitentOpis");
    }

    public static List<String> loadAllPredstavnici() {
        return loadDistinctList(SQL_DISTINCT_PREDSTAVNICI, "trgovackiPredstavnik");
    }

    private static List<String> loadDistinctList(String sql, String colName) {
        List<String> lista = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String val = rs.getString(colName);
                if (val == null) val = "";
                lista.add(val);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

    /**
     * Insert or update komitent.
     * Returns true if a new row was inserted or an update was applied.
     * Update will overwrite trgovackiPredstavnik only when the provided trgovackiPredstavnik
     * is non-empty (so we don't erase an existing representative with an empty value).
     */
    public static synchronized boolean insertIfNotExists(String komitentOpis, String trgovackiPredstavnik) {
        if (komitentOpis == null || komitentOpis.isBlank()) return false;
        if (trgovackiPredstavnik == null) trgovackiPredstavnik = "";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT_UPSERT)) {
            ps.setString(1, komitentOpis.trim());
            ps.setString(2, trgovackiPredstavnik.trim());
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ===== Helper for null/trim =====
    private static String safeString(Object val) {
        return val != null ? val.toString().trim() : "";
    }
}