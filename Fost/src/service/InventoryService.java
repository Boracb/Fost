package service;

import dao.InventoryDao;
import dao.ProductDao;
import model.InventoryRecord;
import model.Product;
import model.ProductInventoryView;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class InventoryService {

    private final InventoryDao inventoryDao;
    private final ProductDao productDao;

    public InventoryService(InventoryDao inventoryDao,
                            ProductDao productDao) {
        this.inventoryDao = inventoryDao;
        this.productDao = productDao;
    }

    public void setQuantity(String productCode, double quantity) throws SQLException {
        Optional<Product> p = productDao.find(productCode);
        Double unitPrice = p.flatMap(pr -> Optional.ofNullable(pr.getPurchaseUnitPrice())).orElse(null);
        inventoryDao.upsertQuantity(productCode, quantity, unitPrice);
    }

    public void adjustQuantity(String productCode, double delta) throws SQLException {
        var current = inventoryDao.find(productCode).orElse(new InventoryRecord(productCode, 0, null, null));
        setQuantity(productCode, current.getQuantity() + delta);
    }

    public List<ProductInventoryView> fullView() throws SQLException {
        return inventoryDao.fullView();
    }

    public double totalValue() throws SQLException {
        return inventoryDao.totalValue();
    }
}