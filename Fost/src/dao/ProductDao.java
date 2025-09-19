package dao;

import model.Product;

import java.sql.*;
import java.util.*;

public class ProductDao {

    private final ConnectionProvider cp;

    public ProductDao(ConnectionProvider cp) {
        this.cp = cp;
    }

    public void upsert(Product p) throws SQLException {
        String sql = """
            INSERT INTO products(product_code,name,main_type,supplier_code,base_unit,alt_unit,
                                 area_per_piece,pack_size,min_order_qty,purchase_unit_price,active,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
            ON CONFLICT(product_code) DO UPDATE SET
              name=excluded.name,
              main_type=excluded.main_type,
              supplier_code=excluded.supplier_code,
              base_unit=excluded.base_unit,
              alt_unit=excluded.alt_unit,
              area_per_piece=excluded.area_per_piece,
              pack_size=excluded.pack_size,
              min_order_qty=excluded.min_order_qty,
              purchase_unit_price=excluded.purchase_unit_price,
              active=excluded.active,
              updated_at=CURRENT_TIMESTAMP
            """;
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.getProductCode());
            ps.setString(2, p.getName());
            ps.setString(3, p.getMainType());
            ps.setString(4, p.getSupplierCode());
            ps.setString(5, p.getBaseUnit());
            ps.setString(6, p.getAltUnit());
            if (p.getAreaPerPiece() != null) ps.setDouble(7, p.getAreaPerPiece()); else ps.setNull(7, Types.REAL);
            if (p.getPackSize() != null) ps.setDouble(8, p.getPackSize()); else ps.setNull(8, Types.REAL);
            if (p.getMinOrderQty() != null) ps.setDouble(9, p.getMinOrderQty()); else ps.setNull(9, Types.REAL);
            if (p.getPurchaseUnitPrice() != null) ps.setDouble(10, p.getPurchaseUnitPrice()); else ps.setNull(10, Types.REAL);
            ps.setInt(11, p.isActive() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public Optional<Product> find(String code) throws SQLException {
        String sql = """
            SELECT product_code,name,main_type,supplier_code,base_unit,alt_unit,
                   area_per_piece,pack_size,min_order_qty,purchase_unit_price,active
              FROM products WHERE product_code=?""";
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public List<Product> findAll() throws SQLException {
        String sql = """
            SELECT product_code,name,main_type,supplier_code,base_unit,alt_unit,
                   area_per_piece,pack_size,min_order_qty,purchase_unit_price,active
              FROM products
             ORDER BY product_code""";
        List<Product> list = new ArrayList<>();
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public void delete(String code) throws SQLException {
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM products WHERE product_code=?")) {
            ps.setString(1, code);
            ps.executeUpdate();
        }
    }

    private Product map(ResultSet rs) throws SQLException {
        return new Product(
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
    }
}