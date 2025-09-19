package excel;

import model.StockState;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.*;

/**
 * Čita Excel sa stupcima:
 * Šifra | Naziv artikla | Jed.mj. | Količina | Nabavna cijena | Nabavna vrijednost
 *
 * Zadani indeksi: 0,1,2,3,4,5
 * Možeš promijeniti: withColumns(code,name,unit,quantity,price,total)
 *
 * Opcije:
 *  - enableAutoDetect(true) pokušat će prepoznati stupce po headerima
 *  - forceRecalculateTotal(true) ignorira vrijednost iz Excela i računa (qty * unitPrice)
 *  - enableDebug(true) ispis prvih par redova
 */
public class ExcelInventoryStateReader {

    private int colCode = 0;
    private int colName = 1;
    private int colUnit = 2;
    private int colQuantity = 3;
    private int colPurchaseUnitPrice = 4;
    private int colPurchaseTotalValue = 5;

    private boolean hasHeader = true;
    private boolean autoDetect = false;
    private boolean debug = false;
    private boolean forceRecalculateTotal = false;
    private int debugRows = 5;

    public ExcelInventoryStateReader withColumns(int code,
                                                 int name,
                                                 int unit,
                                                 int quantity,
                                                 int unitPrice,
                                                 int totalValue) {
        this.colCode = code;
        this.colName = name;
        this.colUnit = unit;
        this.colQuantity = quantity;
        this.colPurchaseUnitPrice = unitPrice;
        this.colPurchaseTotalValue = totalValue;
        return this;
    }

    public ExcelInventoryStateReader withHeader(boolean header) {
        this.hasHeader = header;
        return this;
    }

    public ExcelInventoryStateReader enableAutoDetect(boolean enable) {
        this.autoDetect = enable;
        return this;
    }

    public ExcelInventoryStateReader enableDebug(boolean enable) {
        this.debug = enable;
        return this;
    }

    public ExcelInventoryStateReader forceRecalculateTotal(boolean enable) {
        this.forceRecalculateTotal = enable;
        return this;
    }

    public ExcelInventoryStateReader debugRows(int rows) {
        this.debugRows = rows;
        return this;
    }

    public List<StockState> parse(File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("Excel ne postoji: " + file.getAbsolutePath());
        }
        List<StockState> list = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return list;

            if (hasHeader && autoDetect) {
                Row header = sheet.getRow(0);
                if (header != null) autoDetectColumns(header);
            }

            if (debug) {
                System.out.printf(Locale.ROOT,
                        "Map: code=%d name=%d unit=%d qty=%d price=%d total=%d forceRecalc=%s%n",
                        colCode, colName, colUnit, colQuantity, colPurchaseUnitPrice, colPurchaseTotalValue, forceRecalculateTotal);
            }

            int startRow = hasHeader ? 1 : 0;

            for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String code = readString(row, colCode);
                if (code == null || code.isBlank()) continue;

                String name = readString(row, colName);
                String unit = readString(row, colUnit);
                Double qty = readNumeric(row, colQuantity);
                if (qty == null) qty = 0.0;
                Double unitPrice = readNumeric(row, colPurchaseUnitPrice);
                Double totalValue = readNumeric(row, colPurchaseTotalValue);

                if (forceRecalculateTotal) {
                    // zanemari total iz Excela i računaj ponovno (ako postoji cijena)
                    totalValue = (unitPrice != null) ? qty * unitPrice : null;
                } else {
                    // Ako total nije dan, a imamo cijenu – izračunaj
                    if (totalValue == null && unitPrice != null) {
                        totalValue = qty * unitPrice;
                    }
                }

                StockState item = new StockState(
                        code.trim(),
                        trimOrNull(name),
                        trimOrNull(unit),
                        qty,
                        unitPrice,
                        totalValue
                );
                list.add(item);

                if (debug && (r - startRow) < debugRows) {
                    System.out.printf("ROW %d => %s%n", r, item);
                }
            }
        }
        if (debug) {
            System.out.println("Ukupno parsirano: " + list.size());
        }
        return list;
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private void autoDetectColumns(Row header) {
        Map<String,Integer> map = new HashMap<>();
        for (int i = 0; i < header.getLastCellNum(); i++) {
            Cell c = header.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (c == null) continue;
            if (c.getCellType() != CellType.STRING) continue;
            String norm = normalize(c.getStringCellValue());
            map.put(norm, i);
        }
        colCode = pick(map, List.of("sifra","šifra","code","artikal","oznaka"), colCode);
        colName = pick(map, List.of("nazivartikla","naziv","opis","name","artikal"), colName);
        colUnit = pick(map, List.of("jed.mj.","jedmj.","jedinica","jm","unit","jed","mjera"), colUnit);
        colQuantity = pick(map, List.of("kolicina","količina","kol","qty","quantity"), colQuantity);
        colPurchaseUnitPrice = pick(map, List.of("nabavnacijena","nc","cijena","cijena(jed)","cijenajedinicna"), colPurchaseUnitPrice);
        colPurchaseTotalValue = pick(map, List.of("nabavnavrijednost","nv","ukupno","total","ukupnavrijednost"), colPurchaseTotalValue);

        if (debug) {
            System.out.printf("Autodetekcija -> code=%d name=%d unit=%d qty=%d price=%d total=%d%n",
                    colCode, colName, colUnit, colQuantity, colPurchaseUnitPrice, colPurchaseTotalValue);
        }
    }

    private int pick(Map<String,Integer> map, List<String> keys, int current) {
        for (String k : keys) {
            if (map.containsKey(k)) return map.get(k);
        }
        return current;
    }

    private String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+","");
    }

    private String readString(Row row, int col) {
        if (col < 0) return null;
        Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return null;
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> {
                double d = c.getNumericCellValue();
                if (Math.floor(d)==d) yield String.valueOf((long)d);
                else yield String.valueOf(d);
            }
            case FORMULA -> {
                try { yield c.getStringCellValue(); }
                catch (Exception e) {
                    if (c.getCachedFormulaResultType()==CellType.NUMERIC)
                        yield String.valueOf(c.getNumericCellValue());
                    if (c.getCachedFormulaResultType()==CellType.STRING)
                        yield c.getStringCellValue();
                    yield null;
                }
            }
            default -> null;
        };
    }

    private Double readNumeric(Row row, int col) {
        if (col < 0) return null;
        Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return null;
        return switch (c.getCellType()) {
            case NUMERIC -> c.getNumericCellValue();
            case STRING -> {
                String txt = c.getStringCellValue();
                if (txt == null) yield null;
                txt = txt.trim().replace(',', '.');
                try { yield Double.parseDouble(txt); }
                catch (NumberFormatException e) { yield null; }
            }
            case FORMULA -> {
                if (c.getCachedFormulaResultType()==CellType.NUMERIC)
                    yield c.getNumericCellValue();
                yield null;
            }
            default -> null;
        };
    }
}