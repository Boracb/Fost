package dao;

import model.ProductSupplier;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO za tablicu product_supplier:
 * (product_code, supplier_code, primary_flag, lead_time_days, min_order_qty, last_price)
 */
public class ProductSupplierDao {

    private final ConnectionProvider cp;

    public ProductSupplierDao(ConnectionProvider cp) {
        this.cp = cp;
    }

    public void upsert(ProductSupplier ps) throws SQLException {
        String sql = """
            INSERT INTO product_supplier(product_code,supplier_code,primary_flag,lead_time_days,min_order_qty,last_price,updated_at)
            VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)
            ON CONFLICT(product_code, supplier_code) DO UPDATE SET
              primary_flag=excluded.primary_flag,
              lead_time_days=excluded.lead_time_days,
              min_order_qty=excluded.min_order_qty,
              last_price=excluded.last_price,
              updated_at=CURRENT_TIMESTAMP
            """;
        try (Connection c = cp.get(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, ps.getProductCode());
            p.setString(2, ps.getSupplierCode());
            p.setInt(3, ps.isPrimary() ? 1 : 0);
            if (ps.getLeadTimeDays() != null) p.setInt(4, ps.getLeadTimeDays()); else p.setNull(4, Types.INTEGER);
            if (ps.getMinOrderQty() != null) p.setDouble(5, ps.getMinOrderQty()); else p.setNull(5, Types.REAL);
            if (ps.getLastPrice() != null) p.setDouble(6, ps.getLastPrice()); else p.setNull(6, Types.REAL);
            p.executeUpdate();
        }

        // Ako je primary, osiguraj da ostali nisu (jedan primarni)
        if (ps.isPrimary()) {
            String clearOthers = """
                UPDATE product_supplier SET primary_flag=0
                 WHERE product_code=? AND supplier_code<>?
            """;
            try (Connection c = cp.get(); PreparedStatement p = c.prepareStatement(clearOthers)) {
                p.setString(1, ps.getProductCode());
                p.setString(2, ps.getSupplierCode());
                p.executeUpdate();
            }
        }
    }

    public List<ProductSupplier> listByProduct(String productCode) throws SQLException {
        String sql = """
            SELECT product_code,supplier_code,primary_flag,lead_time_days,min_order_qty,last_price
              FROM product_supplier
             WHERE product_code=?
             ORDER BY primary_flag DESC, supplier_code
            """;
        List<ProductSupplier> out = new ArrayList<>();
        try (Connection c = cp.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, productCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public Optional<ProductSupplier> findPrimary(String productCode) throws SQLException {
        String sql = """
            SELECT product_code,supplier_code,primary_flag,lead_time_days,min_order_qty,last_price
              FROM product_supplier
             WHERE product_code=? AND primary_flag=1
            """;
        try (Connection c = cp.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, productCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public void delete(String productCode, String supplierCode) throws SQLException {
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM product_supplier WHERE product_code=? AND supplier_code=?")) {
            ps.setString(1, productCode);
            ps.setString(2, supplierCode);
            ps.executeUpdate();
        }
    }

    private ProductSupplier map(ResultSet rs) throws SQLException {
        return new ProductSupplier(
                rs.getString(1),
                rs.getString(2),
                rs.getInt(3) == 1,
                rs.getObject(4) != null ? rs.getInt(4) : null,
                rs.getObject(5) != null ? rs.getDouble(5) : null,
                rs.getObject(6) != null ? rs.getDouble(6) : null
        );
    }
}