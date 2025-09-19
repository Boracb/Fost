package dao;

import model.ProductGroup;

import java.sql.*;
import java.util.*;

public class ProductGroupDao {

    private final ConnectionProvider cp;

    public ProductGroupDao(ConnectionProvider cp) {
        this.cp = cp;
    }

    public void upsert(ProductGroup g) throws SQLException {
        String sql = """
            INSERT INTO product_groups(code,name,parent_code,group_type)
            VALUES(?,?,?,?)
            ON CONFLICT(code) DO UPDATE SET
              name=excluded.name,
              parent_code=excluded.parent_code,
              group_type=excluded.group_type
            """;
        try (Connection c = cp.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g.getCode());
            ps.setString(2, g.getName());
            if (g.getParentCode() != null) ps.setString(3, g.getParentCode()); else ps.setNull(3, Types.VARCHAR);
            ps.setString(4, g.getGroupType());
            ps.executeUpdate();
        }
    }

    public void assignToProduct(String productCode, Collection<String> groups) throws SQLException {
        try (Connection c = cp.get()) {
            c.setAutoCommit(false);
            try (PreparedStatement del = c.prepareStatement("DELETE FROM product_group_assignment WHERE product_code=?")) {
                del.setString(1, productCode);
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO product_group_assignment(product_code,group_code) VALUES(?,?)")) {
                for (String g : groups) {
                    if (g == null || g.isBlank()) continue;
                    ins.setString(1, productCode);
                    ins.setString(2, g.trim());
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            c.commit();
        }
    }

    public List<String> groupsForProduct(String productCode) throws SQLException {
        String sql = """
            SELECT group_code FROM product_group_assignment WHERE product_code=? ORDER BY group_code
            """;
        List<String> list = new ArrayList<>();
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, productCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getString(1));
            }
        }
        return list;
    }
}