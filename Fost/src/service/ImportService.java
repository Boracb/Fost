package service;

import dao.InventoryDao;
import dao.ProductDao;
import dao.ProductGroupDao;
import model.Product;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Orkestrira import: prvo proizvodi, zatim stanje.
 * Reader se izvana daje (npr. ExcelProductInventoryReader).
 */
public class ImportService {

    public interface ReaderResult {
        List<Product> products();
        Map<String, Double> openingQuantities();  // product_code -> quantity
        Map<String, List<String>> groupAssignments();
    }

    @FunctionalInterface
    public interface ProductInventoryReader {
        ReaderResult parse(File file) throws Exception;
    }

    private final ProductInventoryReader reader;
    private final ProductDao productDao;
    private final InventoryDao inventoryDao;
    private final ProductGroupDao groupDao;

    public ImportService(ProductInventoryReader reader,
                         ProductDao productDao,
                         InventoryDao inventoryDao,
                         ProductGroupDao groupDao) {
        this.reader = reader;
        this.productDao = productDao;
        this.inventoryDao = inventoryDao;
        this.groupDao = groupDao;
    }

    public void fullImport(File excel) throws Exception {
        ReaderResult rr = reader.parse(excel);

        // 1. Upsert proizvodi
        for (Product p : rr.products()) {
            productDao.upsert(p);
            var groups = rr.groupAssignments().get(p.getProductCode());
            if (groups != null) {
                groupDao.assignToProduct(p.getProductCode(), groups);
            }
        }

        // 2. Stanje
        for (var e : rr.openingQuantities().entrySet()) {
            var prodOpt = productDao.find(e.getKey());
            Double price = prodOpt.flatMap(p -> {
                if (p.getPurchaseUnitPrice() != null) return java.util.Optional.of(p.getPurchaseUnitPrice());
                return java.util.Optional.empty();
            }).orElse(null);
            inventoryDao.upsertQuantity(e.getKey(), e.getValue(), price);
        }
    }
}