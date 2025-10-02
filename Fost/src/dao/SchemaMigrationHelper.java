package dao;

import dao.ConnectionProvider;

import java.sql.*;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Idempotentna nadogradnja sheme za dobavljače i product_supplier.
 * Pozovi rano u aplikaciji: SchemaMigrationHelper.ensure(cp);
 */
public class SchemaMigrationHelper {

    public static void ensure(ConnectionProvider cp) {
        try (Connection c = cp.get()) {
            createSuppliers(c);
            createProductSupplier(c);
            addMissingColumnsProductSupplier(c);
        } catch (SQLException e) {
            throw new RuntimeException("Schema migration failed", e);
        }
    }

    private static void createSuppliers(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS suppliers(
                  supplier_code TEXT PRIMARY KEY,
                  name          TEXT NOT NULL,
                  contact       TEXT,
                  phone         TEXT,
                  email         TEXT,
                  active        INTEGER NOT NULL DEFAULT 1,
                  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
    }

    private static void createProductSupplier(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS product_supplier(
                  product_code   TEXT NOT NULL,
                  supplier_code  TEXT NOT NULL,
                  primary_flag   INTEGER NOT NULL DEFAULT 0,
                  lead_time_days INTEGER,
                  min_order_qty  REAL,
                  last_price     REAL,
                  updated_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY(product_code, supplier_code)
                )
                """);
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ps_product ON product_supplier(product_code)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ps_supplier ON product_supplier(supplier_code)");
        }
    }

    private static void addMissingColumnsProductSupplier(Connection c) throws SQLException {
        Set<String> cols = new HashSet<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(product_supplier)")) {
            while (rs.next()) {
                cols.add(rs.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        try (Statement st = c.createStatement()) {
            if (!cols.contains("primary_flag")) {
                st.executeUpdate("ALTER TABLE product_supplier ADD COLUMN primary_flag INTEGER DEFAULT 0");
            }
            if (!cols.contains("lead_time_days")) {
                st.executeUpdate("ALTER TABLE product_supplier ADD COLUMN lead_time_days INTEGER");
            }
            if (!cols.contains("min_order_qty")) {
                st.executeUpdate("ALTER TABLE product_supplier ADD COLUMN min_order_qty REAL");
            }
            if (!cols.contains("last_price")) {
                st.executeUpdate("ALTER TABLE product_supplier ADD COLUMN last_price REAL");
            }
        }
    }
}