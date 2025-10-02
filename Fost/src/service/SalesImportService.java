package service;

import dao.ConnectionProvider;
import dao.ProductDao;
import dao.SalesDao;
import dao.SalesDaoImpl;
import excel.ExcelSalesReader;
import model.Product;
import model.SalesRecord;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

public class SalesImportService {

    private final ProductDao productDao;
    private final SalesDao salesDao;

    // Podešavanje ponašanja
    private boolean autoCreateMissingProducts = false;

    public SalesImportService(ConnectionProvider cp, ProductDao productDao) throws Exception {
        this.productDao = productDao;
        this.salesDao = new SalesDaoImpl(cp);
    }

    // Omogući/isključi automatsko kreiranje nedostajućih proizvoda
    public SalesImportService enableAutoCreateMissingProducts(boolean enable) {
        this.autoCreateMissingProducts = enable;
        return this;
    }

    // Stari potpis ostaje
    public List<String> importSales(Path excelPath) throws Exception {
        return importSales(excelPath, null);
    }

    // Novi potpis s opcionalnim fallback datumom
    public List<String> importSales(Path excelPath, LocalDate fallbackDateIfMissing) throws Exception {
        File f = excelPath.toFile();
        if (!f.exists()) {
            throw new IllegalArgumentException("Excel ne postoji: " + f.getAbsolutePath());
        }

        List<String> messages = new ArrayList<>();
        Set<String> unknownCodes = new TreeSet<>();

        ExcelSalesReader reader = new ExcelSalesReader();
        try (FileInputStream fis = new FileInputStream(f)) {
            List<ExcelSalesReader.RawRow> rows = reader.read(fis);

            // Ako baš nitko nema datum i korisnik nije zadao fallback, koristi današnji datum
            boolean anyDate = rows.stream().anyMatch(r -> r.date != null);
            LocalDate effectiveFallback = anyDate ? null : (fallbackDateIfMissing != null ? fallbackDateIfMissing : LocalDate.now());

            // Cache da ne zovemo bazu stalno
            Set<String> known = new HashSet<>();
            Set<String> created = new HashSet<>();

            for (ExcelSalesReader.RawRow r : rows) {
                LocalDate date = r.date != null ? r.date : effectiveFallback;

                String code = normalizeCode(r.productCode);
                if (code == null || code.isBlank()) {
                    messages.add("Prazna Šifra na datumu " + date);
                    continue;
                }

                boolean exists = known.contains(code) || productDao.find(code).isPresent();
                if (!exists) {
                    // fallback: makni vodeće nule
                    String noLeadingZeros = stripLeadingZeros(code);
                    if (!noLeadingZeros.equals(code) && (known.contains(noLeadingZeros) || productDao.find(noLeadingZeros).isPresent())) {
                        code = noLeadingZeros;
                        exists = true;
                    }
                }

                if (!exists) {
                    if (autoCreateMissingProducts) {
                        // Kreiraj minimalni proizvod (name iz Excel-a, očisti prefiks ": ")
                        String name = cleanName(r.productName);
                        Product p = new Product(
                                code,
                                name,
                                null,          // mainType
                                null,          // supplier
                                "kom",         // baseUnit default
                                null,          // altUnit
                                null,          // areaPerPiece
                                null,          // packSize
                                null,          // minOrderQty
                                null,          // purchaseUnitPrice
                                true           // active
                        );
                        productDao.upsert(p);
                        created.add(code);
                        known.add(code);
                        messages.add("INFO: Automatski kreiran proizvod " + code + (name != null ? (" - " + name) : ""));
                    } else {
                        unknownCodes.add(code);
                        messages.add("Nepoznat proizvod za šifru: " + code + " (datum " + date + ")");
                        continue;
                    }
                } else {
                    known.add(code);
                }

                if (date == null) {
                    messages.add("Prazan datum za šifru: " + code);
                    continue;
                }

                SalesRecord sr = new SalesRecord();
                sr.setProductCode(code);
                sr.setDate(date);
                sr.setQuantity(r.quantity);
                sr.setDocType(r.docType);
                sr.setDocNo(r.docNo);
                sr.setNetAmount(r.netAmount);
                sr.setGrossAmount(r.grossAmount);
                sr.setVatAmount(r.vatAmount);
                sr.setDiscountAmount(r.discountAmount);
                salesDao.upsert(sr);
            }

            if (!unknownCodes.isEmpty()) {
                messages.add("Nepoznate šifre (unikatno): " + String.join(", ", unknownCodes));
            }
            if (!created.isEmpty()) {
                messages.add("Automatski kreirano proizvoda: " + created.size());
            }
        }
        return messages;
    }

    public double getSoldQtyForRange(String productCode, LocalDate from, LocalDate to) throws Exception {
        return salesDao.getSoldQtyByRange(productCode, from, to);
    }

    // ——— pomoćne ———
    private static final Pattern NBSP = Pattern.compile("[\\u00A0\\u2007\\u202F]");

    private String normalizeCode(String s) {
        if (s == null) return null;
        String t = s.trim();
        t = NBSP.matcher(t).replaceAll(" ");
        t = t.replaceAll("\\s+", "");
        t = t.replaceAll("[^0-9A-Za-z_-]", "");
        t = t.replaceAll("\\.0$", "");
        return t.toUpperCase(Locale.ROOT);
    }

    private String stripLeadingZeros(String s) {
        return s == null ? null : s.replaceFirst("^0+(?!$)", "");
    }

    private String cleanName(String s) {
        if (s == null) return null;
        String t = s.trim();
        // Makni eventualni prefiks ": " i višak whitespace-a
        t = t.replaceFirst("^[:\\-\\s]+", "").trim();
        return t.isEmpty() ? null : t;
    }
}