package dao;

import model.Supplier;

import java.sql.*;
import java.util.*;

public class SupplierDao {

    private final ConnectionProvider cp;

    public SupplierDao(ConnectionProvider cp) {
        this.cp = cp;
    }

    public void upsert(Supplier s) throws SQLException {
        String sql = """
            INSERT INTO suppliers(code,name,contact,active,updated_at)
            VALUES(?,?,?,?,CURRENT_TIMESTAMP)
            ON CONFLICT(code) DO UPDATE SET
              name=excluded.name,
              contact=excluded.contact,
              active=excluded.active,
              updated_at=CURRENT_TIMESTAMP
            """;
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getCode());
            ps.setString(2, s.getName());
            ps.setString(3, s.getContact());
            ps.setInt(4, s.isActive() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public List<Supplier> findAllActive() throws SQLException {
        String sql = "SELECT code,name,contact,active FROM suppliers WHERE active=1 ORDER BY name";
        List<Supplier> list = new ArrayList<>();
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    private Supplier map(ResultSet rs) throws SQLException {
        return new Supplier(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getInt(4) == 1
        );
    }
}