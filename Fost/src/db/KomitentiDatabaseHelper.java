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
 * Helper class for managing 'komitenti' table in SQLite database. Table schema:
 * - komitentOpis (TEXT, NOT NULL) - trgovackiPredstavnik (TEXT)
 * 
 * Unique constraint on (komitentOpis, trgovackiPredstavnik) with ON CONFLICT
 * IGNORE.
 */
public class KomitentiDatabaseHelper {

private static final String DB_URL = "jdbc:sqlite:fost.db";
private static final String TABLE_NAME = "komitenti";

// SQL constants for table creation and queries
private static final String SQL_CREATE_TABLE =
        "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "komitentOpis TEXT NOT NULL, " +
                "trgovackiPredstavnik TEXT, " +
                "UNIQUE(komitentOpis, trgovackiPredstavnik) ON CONFLICT IGNORE" +
                ")";

private static final String SQL_DELETE_ALL =
        "DELETE FROM " + TABLE_NAME;

private static final String SQL_INSERT =
        "INSERT INTO " + TABLE_NAME + " (komitentOpis, trgovackiPredstavnik) VALUES (?, ?)";

private static final String SQL_SELECT_ALL =
        "SELECT komitentOpis, trgovackiPredstavnik FROM " + TABLE_NAME;

private static final String SQL_SELECT_BY_PREDSTAVNIK =
        "SELECT komitentOpis, trgovackiPredstavnik FROM " + TABLE_NAME + " WHERE trgovackiPredstavnik = ?";

private static final String SQL_DELETE_ROW =
        "DELETE FROM " + TABLE_NAME + " WHERE komitentOpis = ? AND trgovackiPredstavnik = ?";

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
public static void clearTable() {
    try (Connection conn = DriverManager.getConnection(DB_URL);
         Statement stmt = conn.createStatement()) {
        stmt.execute(SQL_DELETE_ALL);
    } catch (SQLException e) {
        e.printStackTrace();
    }
}

// ===== Save from list =====
public static void saveToDatabase(List<KomitentInfo> lista) {
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

// ===== Save from DefaultTableModel ===== 
public static void saveToDatabase(DefaultTableModel model) {
    try (Connection conn = DriverManager.getConnection(DB_URL)) {
        conn.setAutoCommit(false);
        try (Statement clean = conn.createStatement()) {
            clean.execute(SQL_DELETE_ALL);
        }
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
            int idxKomitent = model.findColumn("komitentOpis");
            int idxPredstavnik = model.findColumn("trgovackiPredstavnik");
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

//*==== Load all rows ===== 
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

// ===== Delete row =====
public static void deleteRow(String komitentOpis, String trgovackiPredstavnik) {
    try (Connection conn = DriverManager.getConnection(DB_URL);
         PreparedStatement ps = conn.prepareStatement(SQL_DELETE_ROW)) {
        ps.setString(1, safeString(komitentOpis));
        ps.setString(2, safeString(trgovackiPredstavnik));
        ps.executeUpdate();
    } catch (SQLException e) {
        e.printStackTrace();
    }
}

// ===== Map komitent → predstavnik ===== 
public static Map<String, String> loadKomitentPredstavnikMap() {
    Map<String, String> mapa = new HashMap<>();
    try (Connection conn = DriverManager.getConnection(DB_URL);
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(SQL_SELECT_ALL)) {
        while (rs.next()) {
            String komitent = rs.getString("komitentOpis");
            String predstavnik = rs.getString("trgovackiPredstavnik");
            if (komitent != null && predstavnik != null) {
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

// Helper to load distinct values from a given SQL and column name
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

/** Insert komitent if not exists */
public static void insertIfNotExists(String komitentOpis, String trgovackiPredstavnik) {
    if (komitentOpis == null || komitentOpis.isBlank()) return;
    if (trgovackiPredstavnik == null) trgovackiPredstavnik = "";

    try (Connection conn = DriverManager.getConnection(DB_URL);
         PreparedStatement check = conn.prepareStatement(
                 "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE komitentOpis = ? AND trgovackiPredstavnik = ?")) {
        check.setString(1, komitentOpis);
        check.setString(2, trgovackiPredstavnik);
        ResultSet rs = check.executeQuery();
        if (rs.next() && rs.getInt(1) == 0) {
            try (PreparedStatement ins = conn.prepareStatement(SQL_INSERT)) {
                ins.setString(1, komitentOpis);
                ins.setString(2, trgovackiPredstavnik);
                ins.executeUpdate();
            }
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
}

// ===== Helper for null values =====
private static String safeString(Object val) {
    return val != null ? val.toString().trim() : "";
}
}