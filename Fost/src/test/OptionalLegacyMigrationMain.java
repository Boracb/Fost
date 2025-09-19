package test;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Pokreni SAMO ako imaš STARU tablicu inventory_state (monolitnu)
 * i želiš kreirati products + inventory_state (VARIJANTA B).
 *
 * Koraci:
 * 1. Pokreni RunMigrationMain (da nastanu nove tablice).
 * 2. Pokreni ovaj main.
 * 3. Provjeri products i inventory_state (nova).
 */
public class OptionalLegacyMigrationMain {

    private static final String DB_URL = "jdbc:sqlite:fost.db";

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL)) {
            if (!tableExists(c, "inventory_state")) {
                System.err.println("Ne postoji stara tablica inventory_state – prekid.");
                return;
            }
        }

        // Provjeri koje kolone postoje (heuristika stari vs. novi)
        boolean oldHasPurchasePrice;
        try (Connection c = DriverManager.getConnection(DB_URL);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(inventory_state)")) {
            Set<String> cols = new HashSet<>();
            while (rs.next()) cols.add(rs.getString("name").toLowerCase());
            oldHasPurchasePrice = cols.contains("purchase_price");
        }

        if (!oldHasPurchasePrice) {
            System.out.println("Izgleda da inventory_state već NIJE stara verzija (nema purchase_price) – preskačem.");
            return;
        }

        try (Connection c = DriverManager.getConnection(DB_URL)) {
            c.setAutoCommit(false);

            // Čitaj staro
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("""
                        SELECT product_code,name,unit,quantity,purchase_price,purchase_value
                        FROM inventory_state
                        """)) {

                int productsInserted = 0;
                int inventoryInserted = 0;

                while (rs.next()) {
                    String code = rs.getString(1);
                    String name = rs.getString(2);
                    String unit = rs.getString(3);
                    double qty = rs.getDouble(4);
                    Double price = rs.getObject(5) != null ? rs.getDouble(5) : null;
                    Double value = rs.getObject(6) != null ? rs.getDouble(6) : null;

                    // Upsert product
                    try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO products(product_code,name,base_unit,purchase_unit_price,active,updated_at)
                            VALUES(?,?,?,?,1,CURRENT_TIMESTAMP)
                            ON CONFLICT(product_code) DO UPDATE SET
                              name=COALESCE(excluded.name,products.name),
                              base_unit=COALESCE(excluded.base_unit,products.base_unit),
                              purchase_unit_price=COALESCE(excluded.purchase_unit_price,products.purchase_unit_price),
                              updated_at=CURRENT_TIMESTAMP
                            """)) {
                        ps.setString(1, code);
                        ps.setString(2, name);
                        ps.setString(3, unit);
                        if (price != null) ps.setDouble(4, price); else ps.setNull(4, Types.REAL);
                        ps.executeUpdate();
                        productsInserted++;
                    }

                    // Upsert inventory
                    try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO inventory_state(product_code,quantity,purchase_value,last_updated)
                            VALUES(?,?,?,CURRENT_TIMESTAMP)
                            ON CONFLICT(product_code) DO UPDATE SET
                              quantity=excluded.quantity,
                              purchase_value=COALESCE(excluded.purchase_value,inventory_state.purchase_value),
                              last_updated=CURRENT_TIMESTAMP
                            """)) {
                        ps.setString(1, code);
                        ps.setDouble(2, qty);
                        if (value != null) ps.setDouble(3, value);
                        else if (price != null) ps.setDouble(3, qty * price);
                        else ps.setNull(3, Types.REAL);
                        ps.executeUpdate();
                        inventoryInserted++;
                    }
                }
                System.out.printf("Migrirano proizvoda: %d | stanje: %d%n", productsInserted, inventoryInserted);
            }

            c.commit();
        }
        System.out.println("Legacy migracija završena.");
    }

    private static boolean tableExists(Connection c, String name) throws SQLException {
        try (ResultSet rs = c.getMetaData().getTables(null, null, name, null)) {
            return rs.next();
        }
    }
}