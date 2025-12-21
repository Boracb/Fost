package dao;

import model.SalesRecord;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SQLite implementacija SalesDao.
 */
public class SalesDaoImpl implements SalesDao {

    private static final String TABLE = "sales";
    private static volatile boolean schemaEnsured = false;

    private final ConnectionProvider cp;

    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS sales (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          product_code   TEXT NOT NULL,
          date           TEXT NOT NULL,
          quantity       REAL NOT NULL,
          doc_type       TEXT NOT NULL DEFAULT '',
          doc_no         TEXT NOT NULL DEFAULT '',
          net_amount     REAL,
          gross_amount   REAL,
          vat_amount      REAL,
          discount_amount REAL,
          customer_code   TEXT,
          cogs_amount     REAL,
          UNIQUE(product_code, date, doc_type, doc_no)
        )
        """;

    private static final String SUM_QTY_RANGE_SQL = """
        SELECT COALESCE(SUM(quantity),0) FROM sales
        WHERE product_code=? AND date BETWEEN ? AND ?
        """;

    private static final String SUM_COGS_RANGE_SQL = """
        SELECT COALESCE(SUM(cogs_amount),0) FROM sales
        WHERE product_code=? AND date BETWEEN ? AND ?
        """;

    private static final String UPSERT_SQL = """
        INSERT INTO sales(product_code, date, quantity, doc_type, doc_no,
                          net_amount, gross_amount, vat_amount, discount_amount,
                          customer_code, cogs_amount)
        VALUES(?,?,?,?,?,?,?,?,?,?,?)
        ON CONFLICT(product_code, date, doc_type, doc_no) DO UPDATE SET
            quantity        = excluded.quantity,
            net_amount      = excluded.net_amount,
            gross_amount    = excluded.gross_amount,
            vat_amount      = excluded.vat_amount,
            discount_amount = excluded.discount_amount,
            customer_code   = excluded.customer_code,
            cogs_amount     = excluded.cogs_amount
        """;

    public SalesDaoImpl(ConnectionProvider cp) {
        this.cp = cp;
        ensureSchema();
    }

    private void ensureSchema() {
        if (schemaEnsured) return;
        synchronized (SalesDaoImpl.class) {
            if (schemaEnsured) return;
            try (Connection c = cp.get(); Statement st = c.createStatement()) {
                st.executeUpdate(CREATE_TABLE_SQL);
                Set<String> cols = getColumns(c, TABLE);
                if (!cols.contains("customer_code")) {
                    st.executeUpdate("ALTER TABLE " + TABLE + " ADD COLUMN customer_code TEXT");
                }
                if (!cols.contains("cogs_amount")) {
                    st.executeUpdate("ALTER TABLE " + TABLE + " ADD COLUMN cogs_amount REAL");
                }
                if (columnExists(c, TABLE, "product_code") && columnExists(c, TABLE, "date")) {
                    st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sales_product_date ON sales(product_code, date)");
                }
                if (columnExists(c, TABLE, "customer_code")) {
                    st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sales_customer ON sales(customer_code)");
                }
                schemaEnsured = true;
            } catch (SQLException e) {
                throw new RuntimeException("Failed ensuring sales schema", e);
            }
        }
    }

    private Set<String> getColumns(Connection c, String table) throws SQLException {
        Set<String> s = new HashSet<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) s.add(rs.getString("name").toLowerCase());
        }
        return s;
    }

    private boolean columnExists(Connection c, String table, String column) throws SQLException {
        String lc = column.toLowerCase();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) if (lc.equals(rs.getString("name").toLowerCase())) return true;
        }
        return false;
    }

    @Override
    public double getSoldQtyByRange(String productCode, LocalDate from, LocalDate to) throws Exception {
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(SUM_QTY_RANGE_SQL)) {
            ps.setString(1, productCode);
            ps.setString(2, from.toString());
            ps.setString(3, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    @Override
    public double getCOGSByRange(String productCode, LocalDate from, LocalDate to) throws Exception {
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(SUM_COGS_RANGE_SQL)) {
            ps.setString(1, productCode);
            ps.setString(2, from.toString());
            ps.setString(3, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    @Override
    public void upsert(SalesRecord rec) throws Exception {
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(UPSERT_SQL)) {
            bindRecord(ps, rec);
            ps.executeUpdate();
        }
    }

    public void batchUpsert(List<SalesRecord> records) throws Exception {
        if (records == null || records.isEmpty()) return;
        try (Connection c = cp.get();
             PreparedStatement ps = c.prepareStatement(UPSERT_SQL)) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                for (SalesRecord r : records) {
                    bindRecord(ps, r);
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(auto);
            }
        }
    }

    private void bindRecord(PreparedStatement ps, SalesRecord rec) throws SQLException {
        ps.setString(1, rec.getProductCode());
        ps.setString(2, rec.getDate().toString());
        ps.setDouble(3, rec.getQuantity());
        ps.setString(4, rec.getDocType() == null ? "" : rec.getDocType());
        ps.setString(5, rec.getDocNo() == null ? "" : rec.getDocNo());

        setNullableBigDecimal(ps, 6, rec.getNetAmount());
        setNullableBigDecimal(ps, 7, rec.getGrossAmount());
        setNullableBigDecimal(ps, 8, rec.getVatAmount());
        setNullableBigDecimal(ps, 9, rec.getDiscountAmount());

        if (rec.getCustomerCode() == null) ps.setNull(10, Types.VARCHAR); else ps.setString(10, rec.getCustomerCode());
        if (rec.getCogsAmount() == null) ps.setNull(11, Types.REAL); else ps.setDouble(11, rec.getCogsAmount());
    }

    private void setNullableBigDecimal(PreparedStatement ps, int idx, BigDecimal val) throws SQLException {
        if (val == null) ps.setNull(idx, Types.REAL); else ps.setDouble(idx, val.doubleValue());
    }

    @Override
    public void deleteAll() throws Exception {
        try (Connection c = cp.get(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM sales");
        }
    }
}