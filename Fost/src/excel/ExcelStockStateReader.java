package excel;

import model.StockState;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.*;

/**
 * Osnovni Excel -> StockState reader.
 * Layout: 0=Šifra, 1=Naziv artikla, 2=Jed.mj., 3=Količina
 * Ispravan mapping: withColumns(0, 1, 3, 2)  (code, name, quantity, unit)
 *
 * Ova verzija radi s proširenim StockState (dodaje null za cijene/vrijednosti).
 */
public class ExcelStockStateReader {

    private int colCode = 0;
    private int colName = 1;
    private int colQuantity = 2;
    private int colUnit = 3;

    private boolean hasHeader = true;
    private boolean autoDetect = false;
    private boolean debug = false;
    private int debugRows = 5;

    public ExcelStockStateReader withColumns(int code, int name, int quantity, int unit) {
        this.colCode = code;
        this.colName = name;
        this.colQuantity = quantity;
        this.colUnit = unit;
        return this;
    }

    public ExcelStockStateReader withHeader(boolean header) {
        this.hasHeader = header;
        return this;
    }

    public ExcelStockStateReader enableAutoDetectColumns(boolean enable) {
        this.autoDetect = enable;
        return this;
    }

    public ExcelStockStateReader enableDebug(boolean enable) {
        this.debug = enable;
        return this;
    }

    public ExcelStockStateReader debugRows(int rows) {
        this.debugRows = rows;
        return this;
    }

    public List<StockState> parse(File file) throws IOException {
        if (!file.exists()) throw new IOException("Excel ne postoji: " + file.getAbsolutePath());

        List<StockState> out = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return out;

            if (hasHeader && autoDetect) {
                Row header = sheet.getRow(0);
                if (header != null) autoDetectColumns(header);
            }

            if (debug) {
                System.out.printf(Locale.ROOT,
                        "Indeksi -> code=%d name=%d quantity=%d unit=%d%n",
                        colCode, colName, colQuantity, colUnit);
            }

            int startRow = hasHeader ? 1 : 0;

            for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String code = readString(row, colCode);
                if (code == null || code.isBlank()) continue;

                String name = readString(row, colName);
                Double qtyObj = readNumeric(row, colQuantity);
                double qty = qtyObj != null ? qtyObj : 0.0;
                String unit = readString(row, colUnit);

                StockState ss = new StockState(
                        code.trim(),
                        trimOrNull(name),
                        trimOrNull(unit),
                        qty,
                        null,   // purchaseUnitPrice
                        null    // purchaseTotalValue
                );
                out.add(ss);

                if (debug && (r - startRow) < debugRows) {
                    System.out.printf("ROW %d => %s%n", r, ss);
                }
            }
        }
        if (debug) System.out.println("Ukupno parsirano: " + out.size());
        return out;
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
        colQuantity = pick(map, List.of("kolicina","količina","kol","qty","quantity"), colQuantity);
        colUnit = pick(map, List.of("jm","jedinica","jed","unit","mjera","jed.mj.","jedmj."), colUnit);
        int nc = pick(map, List.of("naziv","nazivartikla","opis","name","artikal"), colName);
        if (nc == colCode) nc = colName;
        colName = nc;

        if (debug) {
            System.out.printf("Autodetekcija -> code=%d name=%d quantity=%d unit=%d%n",
                    colCode, colName, colQuantity, colUnit);
        }
    }

    private int pick(Map<String,Integer> map, List<String> keys, int current) {
        for (String k : keys) if (map.containsKey(k)) return map.get(k);
        return current;
    }

    private String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}","")
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
                yield (Math.floor(d)==d) ? String.valueOf((long)d) : String.valueOf(d);
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
                String t = c.getStringCellValue();
                if (t == null) yield null;
                t = t.trim().replace(',', '.');
                try { yield Double.parseDouble(t); }
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