package dao;

import model.Supplier;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SupplierDao {

    private final ConnectionProvider cp;

    public SupplierDao(ConnectionProvider cp) { this.cp = cp; }

    public void upsert(Supplier s) throws SQLException {
        String sql = """
            INSERT INTO suppliers(supplier_code,name,contact,phone,email,active,updated_at)
            VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)
            ON CONFLICT(supplier_code) DO UPDATE SET
              name=excluded.name,
              contact=excluded.contact,
              phone=excluded.phone,
              email=excluded.email,
              active=excluded.active,
              updated_at=CURRENT_TIMESTAMP
            """;
        try (Connection c = cp.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getSupplierCode());
            ps.setString(2, s.getName());
            ps.setString(3, s.getContact());
            ps.setString(4, s.getPhone());
            ps.setString(5, s.getEmail());
            ps.setInt(6, s.isActive() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public Optional<Supplier> find(String code) throws SQLException {
        String sql = """
            SELECT supplier_code,name,contact,phone,email,active
              FROM suppliers WHERE supplier_code=?
            """;
        try (Connection c = cp.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public List<Supplier> findAll() throws SQLException {
        String sql = """
            SELECT supplier_code,name,contact,phone,email,active
              FROM suppliers
             ORDER BY supplier_code
            """;
        List<Supplier> out = new ArrayList<>();
        try (Connection c = cp.get(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    public void delete(String code) throws SQLException {
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM suppliers WHERE supplier_code=?")) {
            ps.setString(1, code);
            ps.executeUpdate();
        }
    }

    private Supplier map(ResultSet rs) throws SQLException {
        return new Supplier(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getInt(6) == 1
        );
    }
}