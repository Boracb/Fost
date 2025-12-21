package service;

import dao.ConnectionProvider;
import dao.ProductDao;
import dao.SalesDao;
import excel.ExcelSalesReader;
import model.Product;
import model.SalesRecord;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Uvoz prodaje iz Excela u tablicu sales.
 */
public class SalesImportService {

    private final ConnectionProvider cp;
    private final ProductDao productDao;
    private final SalesDao salesDao;
    private final ExcelSalesReader reader;

    private boolean autoCreateMissingProducts = false;

    public SalesImportService(ConnectionProvider cp,
                              ProductDao productDao,
                              SalesDao salesDao,
                              ExcelSalesReader reader) {
        this.cp = cp;
        this.productDao = productDao;
        this.salesDao = salesDao;
        this.reader = reader;
    }

    // ranija verzija (bez custom reader-a)
    public SalesImportService(ConnectionProvider cp, ProductDao productDao) {
        this(cp, productDao, new dao.SalesDaoImpl(cp), new ExcelSalesReader());
    }

    public SalesImportService enableAutoCreateMissingProducts(boolean enable) {
        this.autoCreateMissingProducts = enable;
        return this;
    }

    public List<String> importSales(Path excel, LocalDate fallbackDate) throws Exception {
        List<String> messages = new ArrayList<>();

        List<SalesRecord> parsed = reader.parse(excel.toFile(), fallbackDate);
        if (parsed.isEmpty()) {
            messages.add("Nema redova (parser vratio prazno).");
            return messages;
        }
        messages.add("Parser vratio " + parsed.size() + " redova.");

        int createdProducts = 0;
        int skipped = 0;
        int upserted = 0;

        for (SalesRecord r : parsed) {
            String code = r.getProductCode();
            Optional<Product> prodOpt = productDao.find(code);
            if (prodOpt.isEmpty()) {
                if (autoCreateMissingProducts) {
                    Product p = new Product(
                            code,
                            code,           // name = code
                            "TRGOVACKA",    // main_type default
                            null,           // supplier_code
                            "kom",          // base_unit
                            null,           // alt_unit
                            null,           // area_per_piece
                            null, null, null,
                            true
                    );
                    productDao.upsert(p);
                    createdProducts++;
                } else {
                    skipped++;
                    messages.add("SKIP product ne postoji: " + code);
                    continue;
                }
            }
            salesDao.upsert(r);
            upserted++;
        }
        messages.add("Upisano (upsert): " + upserted);
        if (createdProducts > 0) messages.add("Auto-kreirano proizvoda: " + createdProducts);
        if (skipped > 0) messages.add("Preskočeno (product ne postoji): " + skipped);

        return messages;
    }
}