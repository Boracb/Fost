package service;

import dao.ProductDao;
import dao.ProductGroupDao;
import dao.SupplierDao;
import model.Product;
import model.ProductGroup;
import model.Supplier;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class ProductService {

    private final ProductDao productDao;
    private final ProductGroupDao groupDao;
    private final SupplierDao supplierDao;

    public ProductService(ProductDao productDao,
                          ProductGroupDao groupDao,
                          SupplierDao supplierDao) {
        this.productDao = productDao;
        this.groupDao = groupDao;
        this.supplierDao = supplierDao;
    }

    public void upsertSupplier(Supplier s) throws SQLException {
        supplierDao.upsert(s);
    }

    public List<Supplier> listSuppliers() throws SQLException {
        return supplierDao.findAllActive();
    }

    public void upsertGroup(ProductGroup g) throws SQLException {
        groupDao.upsert(g);
    }

    public void upsertProduct(Product p, Collection<String> groups) throws SQLException {
        productDao.upsert(p);
        groupDao.assignToProduct(p.getProductCode(), groups);
    }

    public Optional<Product> findProduct(String code) throws SQLException {
        return productDao.find(code);
    }

    public List<Product> allProducts() throws SQLException {
        return productDao.findAll();
    }

    public List<String> groupsForProduct(String productCode) throws SQLException {
        return groupDao.groupsForProduct(productCode);
    }
}