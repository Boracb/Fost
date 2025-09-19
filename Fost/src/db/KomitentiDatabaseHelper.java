package db;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.table.DefaultTableModel;
import model.KomitentInfo;

/**
 * KomitentiDatabaseHelper - sigurnija verzija koja osigurava PRIMARY KEY na komitentOpis
 * i radi migraciju ako tablica postoji bez PK-a.
 */
public class KomitentiDatabaseHelper {

    private static final String DB_URL = "jdbc:sqlite:fost.db";
    private static final String TABLE_NAME = "komitenti";

    // Create table with PRIMARY KEY on komitentOpis
    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    "komitentOpis TEXT PRIMARY KEY, " +
                    "trgovackiPredstavnik TEXT" +
                    ")";

    private static final String SQL_DELETE_ALL =
            "DELETE FROM " + TABLE_NAME;

    private static final String SQL_INSERT =
            "INSERT INTO " + TABLE_NAME + " (komitentOpis, trgovackiPredstavnik) VALUES (?, ?)";

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
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            // Ako tablica postoji, provjeri ima li PK; ako nema, migriraj podatke u novu tablicu s PK
            boolean needMigration = false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
                ps.setString(1, TABLE_NAME);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        // table exists - check schema for PRIMARY KEY on komitentOpis
                        try (Statement st = conn.createStatement();
                             ResultSet cols = st.executeQuery("PRAGMA table_info('" + TABLE_NAME + "')")) {
                            boolean hasPK = false;
                            while (cols.next()) {
                                String colName = cols.getString("name");
                                int pk = cols.getInt("pk");
                                if ("komitentOpis".equalsIgnoreCase(colName) && pk > 0) {
                                    hasPK = true;
                                    break;
                                }
                            }
                            if (!hasPK) needMigration = true;
                        }
                    } else {
                        // table doesn't exist - create with PK
                        try (Statement st = conn.createStatement()) {
                            st.execute(SQL_CREATE_TABLE);
                        }
                    }
                }
            }

            if (needMigration) {
                System.out.println("KomitentiDatabaseHelper: migrating table to add PRIMARY KEY on komitentOpis");
                // create temp table with PK, copy distinct rows (keep first non-empty predstavnik), drop old, rename
                String tmp = TABLE_NAME + "_tmp_new";
                try (Statement st = conn.createStatement()) {
                    st.execute("CREATE TABLE IF NOT EXISTS " + tmp + " (komitentOpis TEXT PRIMARY KEY, trgovackiPredstavnik TEXT)");
                    // copy distinct: choose first non-empty predstavnik
                    st.execute("INSERT OR IGNORE INTO " + tmp + " (komitentOpis, trgovackiPredstavnik) " +
                            "SELECT komitentOpis, COALESCE(trgovackiPredstavnik, '') FROM " + TABLE_NAME + " GROUP BY komitentOpis");
                    st.execute("DROP TABLE " + TABLE_NAME);
                    st.execute("ALTER TABLE " + tmp + " RENAME TO " + TABLE_NAME);
                }
            }

            // ensure table exists in final form
            try (Statement st = conn.createStatement()) {
                st.execute(SQL_CREATE_TABLE);
            }

            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("KomitentiDatabaseHelper: DB path = " + new java.io.File("fost.db").getAbsolutePath());
    }

    // ===== Delete all records (explicit) =====
    public static synchronized void clearTable() {
        System.out.println("KomitentiDatabaseHelper.clearTable() called - will DELETE ALL rows in " + TABLE_NAME);
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(SQL_DELETE_ALL);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void saveToDatabase(List<KomitentInfo> lista) {
        if (lista == null) return;
        upsertList(lista);
    }

    public static synchronized void saveToDatabase(DefaultTableModel model) {
        if (model == null) return;
        List<KomitentInfo> lista = new ArrayList<>();
        int idxKomitent = model.findColumn("komitentOpis");
        int idxPredstavnik = model.findColumn("trgovackiPredstavnik");
        if (idxKomitent < 0) idxKomitent = 0;
        if (idxPredstavnik < 0) idxPredstavnik = 1;
        for (int r = 0; r < model.getRowCount(); r++) {
            String k = safeString(model.getValueAt(r, idxKomitent));
            String p = safeString(model.getValueAt(r, idxPredstavnik));
            if (k == null || k.isBlank()) continue;
            lista.add(new KomitentInfo(k.trim(), p.trim()));
        }
        upsertList(lista);
    }

    public static synchronized void replaceAllInDatabase(List<KomitentInfo> lista) {
        System.out.println("KomitentiDatabaseHelper.replaceAllInDatabase() - replacing all rows (DELETE + INSERT)");
        if (lista == null) lista = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try (Statement s = conn.createStatement()) {
                s.execute(SQL_DELETE_ALL);
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

    public static synchronized void upsertList(List<KomitentInfo> lista) {
        if (lista == null || lista.isEmpty()) return;
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_UPSERT)) {
                for (KomitentInfo k : lista) {
                    ps.setString(1, safeString(k.getKomitentOpis()));
                    ps.setString(2, safeString(k.getTrgovackiPredstavnik()));
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
                return;
            } catch (SQLException ex) {
                System.out.println("KomitentiDatabaseHelper.upsertList: UPSERT batch failed: " + ex.getMessage());
                try { conn.rollback(); } catch (SQLException ignored) {}
            } finally {
                try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            System.out.println("KomitentiDatabaseHelper.upsertList: connection failed: " + e.getMessage());
        }
        // fallback per-row
        fallbackPerRowUpsert(lista);
    }

    private static void fallbackPerRowUpsert(List<KomitentInfo> lista) {
        if (lista == null || lista.isEmpty()) return;
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try (PreparedStatement check = conn.prepareStatement("SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE komitentOpis = ?");
                 PreparedStatement insert = conn.prepareStatement(SQL_INSERT);
                 PreparedStatement update = conn.prepareStatement("UPDATE " + TABLE_NAME + " SET trgovackiPredstavnik = ? WHERE komitentOpis = ?")) {

                for (KomitentInfo k : lista) {
                    String kom = safeString(k.getKomitentOpis());
                    String tp = safeString(k.getTrgovackiPredstavnik());
                    check.setString(1, kom);
                    try (ResultSet rs = check.executeQuery()) {
                        int count = rs.next() ? rs.getInt(1) : 0;
                        if (count == 0) {
                            insert.setString(1, kom);
                            insert.setString(2, tp);
                            insert.executeUpdate();
                        } else {
                            if (tp != null && !tp.isBlank()) {
                                update.setString(1, tp);
                                update.setString(2, kom);
                                update.executeUpdate();
                            }
                        }
                    }
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

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

    public static void deleteRow(String komitentOpis, String trgovackiPredstavnik) {
        if (komitentOpis == null || komitentOpis.isBlank()) return;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE_ROW)) {
            ps.setString(1, safeString(komitentOpis));
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

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
                if (existing == null || existing.isBlank()) {
                    mapa.put(komitent, predstavnik);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return mapa;
    }

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

    public static synchronized boolean insertIfNotExists(String komitentOpis, String trgovackiPredstavnik) {
        if (komitentOpis == null || komitentOpis.isBlank()) return false;
        if (trgovackiPredstavnik == null) trgovackiPredstavnik = "";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT_UPSERT)) {
            ps.setString(1, komitentOpis.trim());
            ps.setString(2, trgovackiPredstavnik.trim());
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException ex) {
            System.out.println("insertIfNotExists: UPSERT failed, falling back. Reason: " + ex.getMessage());
        }

        // fallback
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE komitentOpis = ?")) {
                check.setString(1, komitentOpis.trim());
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        if (trgovackiPredstavnik != null && !trgovackiPredstavnik.isBlank()) {
                            try (PreparedStatement upd = conn.prepareStatement(
                                    "UPDATE " + TABLE_NAME + " SET trgovackiPredstavnik = ? WHERE komitentOpis = ?")) {
                                upd.setString(1, trgovackiPredstavnik.trim());
                                upd.setString(2, komitentOpis.trim());
                                upd.executeUpdate();
                                return true;
                            }
                        }
                        return false;
                    }
                }
            }
            try (PreparedStatement ins = conn.prepareStatement(SQL_INSERT)) {
                ins.setString(1, komitentOpis.trim());
                ins.setString(2, trgovackiPredstavnik.trim());
                int n = ins.executeUpdate();
                return n > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static String safeString(Object val) {
        return val != null ? val.toString().trim() : "";
    }
}