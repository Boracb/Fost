package test;

import java.sql.*;

/**
 * Pokreće VARIJANTA B shemu (products + inventory_state + suppliers + product_groups).
 * Idempotentno: više pokretanja neće razbiti ništa.
 *
 * Pokretanje:
 *   java -cp build/classes/java/main;build/classes/java/test;path/do/sqlite-jdbc.jar test.RunMigrationMain
 */
public class RunMigrationMain {

    private static final String DB_URL = "jdbc:sqlite:fost.db";

    private static final String DDL = """
        CREATE TABLE IF NOT EXISTS suppliers(
            code TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            contact TEXT,
            active INTEGER DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS products(
            product_code TEXT PRIMARY KEY,
            name TEXT,
            main_type TEXT,
            supplier_code TEXT REFERENCES suppliers(code),
            base_unit TEXT,
            alt_unit TEXT,
            area_per_piece REAL,
            pack_size REAL,
            min_order_qty REAL,
            purchase_unit_price REAL,
            active INTEGER DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS inventory_state(
            product_code TEXT PRIMARY KEY REFERENCES products(product_code) ON DELETE CASCADE,
            quantity REAL NOT NULL,
            purchase_value REAL,
            last_updated DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS product_groups(
            code TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            parent_code TEXT REFERENCES product_groups(code),
            group_type TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS product_group_assignment(
            product_code TEXT NOT NULL REFERENCES products(product_code) ON DELETE CASCADE,
            group_code TEXT NOT NULL REFERENCES product_groups(code) ON DELETE CASCADE,
            PRIMARY KEY(product_code, group_code)
        );

        CREATE INDEX IF NOT EXISTS idx_products_supplier ON products(supplier_code);
        CREATE INDEX IF NOT EXISTS idx_products_main_type ON products(main_type);
        CREATE INDEX IF NOT EXISTS idx_inventory_state_last_updated ON inventory_state(last_updated);
        CREATE INDEX IF NOT EXISTS idx_product_group_assignment_group ON product_group_assignment(group_code);
        """;

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL)) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                for (String stmt : DDL.split(";")) {
                    String sql = stmt.trim();
                    if (sql.isEmpty()) continue;
                    try {
                        st.execute(sql);
                        System.out.println("OK: " + shortPreview(sql));
                    } catch (SQLException e) {
                        System.err.println("WARN: " + e.getMessage() + " (" + shortPreview(sql) + ")");
                    }
                }
            }
            c.commit();
        }
        System.out.println("Migration (VARIJANTA B) dovršen.");
    }

    private static String shortPreview(String s) {
        return s.replaceAll("\\s+", " ").substring(0, Math.min(60, s.length()));
    }
}