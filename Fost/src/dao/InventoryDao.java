package dao;

import model.InventoryRecord;
import model.Product;
import model.ProductInventoryView;

import java.sql.*;
import java.time.Instant;
import java.util.*;

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
                InventoryRecord inv = null;
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

    public void deleteAll() throws SQLException {
        try (Connection c = cp.get();
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM inventory_state");
        }
    }

    private InventoryRecord map(ResultSet rs) throws SQLException {
        return new InventoryRecord(
                rs.getString(1),
                rs.getDouble(2),
                rs.getObject(3) != null ? rs.getDouble(3) : null,
                rs.getObject(4) != null ? rs.getTimestamp(4).toInstant() : null
        );
    }
}