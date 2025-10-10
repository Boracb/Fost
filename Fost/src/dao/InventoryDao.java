package dao;

import model.InventoryRecord;
import model.Product;
import model.ProductInventoryView;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * DAO za stanje zaliha i pogled proizvoda, uključuje i metode find/findAll/upsertQuantity.
 */
public class InventoryDao {

    private final ConnectionProvider cp;

    public InventoryDao(ConnectionProvider cp) {
        this.cp = cp;
    }

    public void upsertQuantity(String productCode, double quantity, Double unitPrice) throws SQLException {
        String sql = """
            INSERT INTO inventory_state(product_code,quantity,purchase_value,last_updated)
            VALUES(?,?,?,CURRENT_TIMESTAMP)
            ON CONFLICT(product_code) DO UPDATE SET
              quantity=excluded.quantity,
              purchase_value=CASE
                   WHEN excluded.purchase_value IS NOT NULL THEN excluded.purchase_value
                   ELSE inventory_state.purchase_value END,
              last_updated=CURRENT_TIMESTAMP
            """;
        Double purchaseValue = (unitPrice != null) ? quantity * unitPrice : null;

        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, productCode);
            ps.setDouble(2, quantity);
            if (purchaseValue != null) ps.setDouble(3, purchaseValue); else ps.setNull(3, Types.REAL);
            ps.executeUpdate();
        }
    }

    public Optional<InventoryRecord> find(String code) throws SQLException {
        String sql = "SELECT product_code,quantity,purchase_value,last_updated FROM inventory_state WHERE product_code=?";
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public List<InventoryRecord> findAll() throws SQLException {
        String sql = "SELECT product_code,quantity,purchase_value,last_updated FROM inventory_state";
        List<InventoryRecord> list = new ArrayList<>();
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public double totalValue() throws SQLException {
        String sql = "SELECT SUM(purchase_value) FROM inventory_state";
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    public List<ProductInventoryView> fullView() throws SQLException {
        String sql = """
            SELECT p.product_code,p.name,p.main_type,p.supplier_code,p.base_unit,p.alt_unit,
                   p.area_per_piece,p.pack_size,p.min_order_qty,p.purchase_unit_price,p.active,
                   i.quantity,i.purchase_value,
                   group_concat(pga.group_code,';') as groups
              FROM products p
              LEFT JOIN inventory_state i ON i.product_code=p.product_code
              LEFT JOIN product_group_assignment pga ON pga.product_code=p.product_code
             GROUP BY p.product_code
             ORDER BY p.product_code
            """;
        List<ProductInventoryView> out = new ArrayList<>();
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Product prod = new Product(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getObject(7) != null ? rs.getDouble(7) : null,
                        rs.getObject(8) != null ? rs.getDouble(8) : null,
                        rs.getObject(9) != null ? rs.getDouble(9) : null,
                        rs.getObject(10) != null ? rs.getDouble(10) : null,
                        rs.getInt(11) == 1
                );
                InventoryRecord inv;
                if (rs.getObject(12) != null) {
                    inv = new InventoryRecord(
                            prod.getProductCode(),
                            rs.getDouble(12),
                            rs.getObject(13) != null ? rs.getDouble(13) : null,
                            Instant.now()
                    );
                } else {
                    inv = new InventoryRecord(prod.getProductCode(), 0, null, Instant.now());
                }
                String groups = rs.getString(14);
                List<String> groupList = groups != null ? Arrays.asList(groups.split(";")) : List.of();
                out.add(new ProductInventoryView(prod, inv, groupList));
            }
        }
        return out;
    }

    // Novi pun pogled + prodaja u periodu
    public List<ProductInventoryView> fullViewWithSales(LocalDate from, LocalDate to) throws SQLException {
        String sql = """
            WITH period_sales AS (
                SELECT product_code, SUM(quantity) AS sales_qty
                  FROM sales
                 WHERE date BETWEEN ? AND ?
                 GROUP BY product_code
            )
            SELECT p.product_code,p.name,p.main_type,p.supplier_code,p.base_unit,p.alt_unit,
                   p.area_per_piece,p.pack_size,p.min_order_qty,p.purchase_unit_price,p.active,
                   i.quantity,i.purchase_value,
                   group_concat(pga.group_code,';') as groups,
                   ps.sales_qty
              FROM products p
              LEFT JOIN inventory_state i ON i.product_code=p.product_code
              LEFT JOIN product_group_assignment pga ON pga.product_code=p.product_code
              LEFT JOIN period_sales ps ON ps.product_code=p.product_code
             GROUP BY p.product_code
             ORDER BY p.product_code
            """;
        List<ProductInventoryView> out = new ArrayList<>();
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, from.toString());
            ps.setString(2, to.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Product prod = new Product(
                            rs.getString(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6),
                            rs.getObject(7) != null ? rs.getDouble(7) : null,
                            rs.getObject(8) != null ? rs.getDouble(8) : null,
                            rs.getObject(9) != null ? rs.getDouble(9) : null,
                            rs.getObject(10) != null ? rs.getDouble(10) : null,
                            rs.getInt(11) == 1
                    );
                    InventoryRecord inv;
                    if (rs.getObject(12) != null) {
                        inv = new InventoryRecord(
                                prod.getProductCode(),
                                rs.getDouble(12),
                                rs.getObject(13) != null ? rs.getDouble(13) : null,
                                Instant.now()
                        );
                    } else {
                        inv = new InventoryRecord(prod.getProductCode(), 0, null, Instant.now());
                    }
                    String groups = rs.getString(14);
                    List<String> groupList = groups != null ? Arrays.asList(groups.split(";")) : List.of();
                    Double salesQty = rs.getObject(15) != null ? rs.getDouble(15) : null;
                    out.add(new ProductInventoryView(prod, inv, groupList, salesQty));
                }
            }
        }
        return out;
    }

    public void deleteAll() throws SQLException {
        try (Connection c = cp.get();
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM inventory_state");
        }
    }

    private InventoryRecord map(ResultSet rs) throws SQLException {
        String code = rs.getString(1);
        double qty = rs.getDouble(2);
        Double val = rs.getObject(3) != null ? rs.getDouble(3) : null;
        String ts = rs.getString(4);
        Instant lastUpdated = parseInstant(ts);
        return new InventoryRecord(code, qty, val, lastUpdated);
    }

    private Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        String trimmed = s.trim();
        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss.S",
                "yyyy-MM-dd HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.S",
                "yyyy-MM-dd'T'HH:mm:ss.SSS"
        };
        for (String p : patterns) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern(p));
                return ldt.atZone(ZoneId.systemDefault()).toInstant();
            } catch (Exception ignored) {}
        }
        int dot = trimmed.indexOf('.');
        if (dot > 0) {
            String base = trimmed.substring(0, dot);
            try {
                LocalDateTime ldt = LocalDateTime.parse(base, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                return ldt.atZone(ZoneId.systemDefault()).toInstant();
            } catch (Exception ignored) {}
        }
        return null;
    }
}