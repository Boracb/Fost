package logic;

import db.InventoryStateDatabaseHelper;
import model.StockState;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class InventoryService {

    private final InventoryStateDatabaseHelper db;

    public InventoryService(InventoryStateDatabaseHelper db) {
        this.db = db;
    }

    public List<StockState> getAll() throws SQLException {
        return db.findAll();
    }

    public Optional<StockState> find(String code) throws SQLException {
        return db.findByCode(code);
    }

    public void updateQuantity(String code, double qty) throws SQLException {
        db.updateQuantity(code, qty);
    }

    public void updatePrice(String code, double price) throws SQLException {
        db.updatePrice(code, price);
    }

    public void deleteAll() throws SQLException {
        db.truncateAll();
    }

    public double totalPurchaseValue() throws SQLException {
        double sum = 0;
        for (StockState s : db.findAll()) {
            if (s.getPurchaseTotalValue() != null) sum += s.getPurchaseTotalValue();
        }
        return sum;
    }
}