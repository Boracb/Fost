package db;

import model.StockState;

import java.sql.*;
import java.util.*;

/**
 * Upravljanje tablicom inventory_state.
 * Shema (cilj):
 * product_code | name | unit | quantity | purchase_price | purchase_value | last_updated
 */
public class InventoryStateDatabaseHelper {

    private final String url;

    public InventoryStateDatabaseHelper(String url) {
        this.url = url;
    }

    public void ensureSchema() throws SQLException {
        // Kreiraj ako ne postoji (sa svim stupcima)
        try (Connection c = DriverManager.getConnection(url);
             Statement st = c.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS inventory_state(
                    product_code TEXT PRIMARY KEY,
                    name TEXT,
                    unit TEXT,
                    quantity REAL NOT NULL,
                    purchase_price REAL,
                    purchase_value REAL,
                    last_updated DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }

        // Provjeri postojeće kolone (ako je starija tablica)
        Set<String> cols = new HashSet<>();
        try (Connection c = DriverManager.getConnection(url);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(inventory_state)")) {
            while (rs.next()) {
                cols.add(rs.getString("name").toLowerCase(Locale.ROOT));
            }
        }

        // Dodaj nedostajuće
        try (Connection c = DriverManager.getConnection(url);
             Statement st = c.createStatement()) {
            if (!cols.contains("purchase_price")) {
                st.executeUpdate("ALTER TABLE inventory_state ADD COLUMN purchase_price REAL");
            }
            if (!cols.contains("purchase_value")) {
                st.executeUpdate("ALTER TABLE inventory_state ADD COLUMN purchase_value REAL");
            }
        }
    }

    public void bulkUpsert(List<StockState> list) throws SQLException {
        if (list.isEmpty()) return;
        String sql = """
            INSERT INTO inventory_state(product_code, name, unit, quantity, purchase_price, purchase_value, last_updated)
            VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)
            ON CONFLICT(product_code) DO UPDATE SET
              name=COALESCE(excluded.name, inventory_state.name),
              unit=COALESCE(excluded.unit, inventory_state.unit),
              quantity=excluded.quantity,
              purchase_price=COALESCE(excluded.purchase_price, inventory_state.purchase_price),
              purchase_value=COALESCE(
                  excluded.purchase_value,
                  CASE WHEN excluded.purchase_price IS NOT NULL
                       THEN excluded.quantity * excluded.purchase_price
                       ELSE inventory_state.purchase_value END
              ),
              last_updated=CURRENT_TIMESTAMP
            """;

        try (Connection c = DriverManager.getConnection(url);
             PreparedStatement ps = c.prepareStatement(sql)) {
            c.setAutoCommit(false);
            for (StockState s : list) {
                ps.setString(1, s.getProductCode());
                ps.setString(2, s.getName());
                ps.setString(3, s.getUnit());
                ps.setDouble(4, s.getQuantity());
                if (s.getPurchaseUnitPrice() != null) ps.setDouble(5, s.getPurchaseUnitPrice()); else ps.setNull(5, Types.REAL);
                if (s.getPurchaseTotalValue() != null) ps.setDouble(6, s.getPurchaseTotalValue()); else ps.setNull(6, Types.REAL);
                ps.addBatch();
            }
            ps.executeBatch();
            c.commit();
        }
    }

    public List<StockState> findAll() throws SQLException {
        List<StockState> list = new ArrayList<>();
        String sql = """
            SELECT product_code, name, unit, quantity, purchase_price, purchase_value
              FROM inventory_state
             ORDER BY product_code
            """;
        try (Connection c = DriverManager.getConnection(url);
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString(1);
                String name = rs.getString(2);
                String unit = rs.getString(3);
                double qty = rs.getDouble(4);
                Double price = rs.getObject(5) != null ? rs.getDouble(5) : null;
                Double value = rs.getObject(6) != null ? rs.getDouble(6) : null;
                list.add(new StockState(code, name, unit, qty, price, value));
            }
        }
        return list;
    }

    public Optional<StockState> findByCode(String code) throws SQLException {
        String sql = """
            SELECT product_code, name, unit, quantity, purchase_price, purchase_value
              FROM inventory_state
             WHERE product_code=?
            """;
        try (Connection c = DriverManager.getConnection(url);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Double price = rs.getObject(5) != null ? rs.getDouble(5) : null;
                    Double value = rs.getObject(6) != null ? rs.getDouble(6) : null;
                    return Optional.of(new StockState(
                            rs.getString(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getDouble(4),
                            price,
                            value
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public void updateQuantity(String productCode, double newQty) throws SQLException {
        String sql = """
            UPDATE inventory_state
               SET quantity=?,
                   purchase_value=CASE
                       WHEN purchase_price IS NOT NULL THEN ? * purchase_price
                       ELSE purchase_value END,
                   last_updated=CURRENT_TIMESTAMP
             WHERE product_code=?""";
        try (Connection c = DriverManager.getConnection(url);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, newQty);
            ps.setDouble(2, newQty);
            ps.setString(3, productCode);
            ps.executeUpdate();
        }
    }

    public void updatePrice(String productCode, double newPrice) throws SQLException {
        String sql = """
            UPDATE inventory_state
               SET purchase_price=?,
                   purchase_value=quantity * ?,
                   last_updated=CURRENT_TIMESTAMP
             WHERE product_code=?""";
        try (Connection c = DriverManager.getConnection(url);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setDouble(2, newPrice);
            ps.setString(3, productCode);
            ps.executeUpdate();
        }
    }

    public void truncateAll() throws SQLException {
        try (Connection c = DriverManager.getConnection(url);
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM inventory_state");
        }
    }
}