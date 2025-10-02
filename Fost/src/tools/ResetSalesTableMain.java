package tools;

import dao.ConnectionProvider;

import java.sql.Connection;
import java.sql.Statement;

public class ResetSalesTableMain {
    public static void main(String[] args) throws Exception {
        String dbUrl = "jdbc:sqlite:fost.db";
        var cp = new ConnectionProvider(dbUrl);
        try (Connection c = cp.get(); Statement st = c.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS sales");
            System.out.println("sales dropped. It will be recreated by SalesDaoImpl.ensureSchema().");
        }
    }
}