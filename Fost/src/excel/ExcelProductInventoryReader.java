package excel;

import model.Product;
import service.ImportService.ProductInventoryReader;
import service.ImportService.ReaderResult;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/**
 * Očekivani stupci (0-based) – prilagodi po potrebi:
 * 0 Šifra
 * 1 Naziv artikla
 * 2 Dobavljač (šifra)
 * 3 Glavna vrsta (SIROVINA|VLASTITA|TRGOVACKA)
 * 4 Osnovna jedinica (base_unit) npr. m2 ili kom
 * 5 Alternativna jedinica (alt_unit) (opcionalno)
 * 6 m2 po komadu (area_per_piece) (opcionalno)
 * 7 Pakiranje (pack_size)
 * 8 Min. narudžba (min_order_qty)
 * 9 Nabavna cijena (purchase_unit_price)
 * 10 Početna količina (quantity) – u base_unit
 * 11 Grupe (grupe odvojene ';' ili ',')
 *
 * Možeš promijeniti indekse kroz withColumns(...)
 */
public class ExcelProductInventoryReader implements ProductInventoryReader {

    private int colCode = 0;
    private int colName = 1;
    private int colSupplier = 2;
    private int colMainType = 3;
    private int colBaseUnit = 4;
    private int colAltUnit = 5;
    private int colAreaPerPiece = 6;
    private int colPackSize = 7;
    private int colMinOrder = 8;
    private int colUnitPrice = 9;
    private int colOpeningQty = 10;
    private int colGroups = 11;

    private boolean hasHeader = true;
    private boolean debug = false;
    private int debugRows = 5;

    public ExcelProductInventoryReader withColumns(int code, int name, int supplier, int mainType,
                                                   int baseUnit, int altUnit, int area,
                                                   int packSize, int minOrder, int unitPrice,
                                                   int openingQty, int groups) {
        this.colCode = code;
        this.colName = name;
        this.colSupplier = supplier;
        this.colMainType = mainType;
        this.colBaseUnit = baseUnit;
        this.colAltUnit = altUnit;
        this.colAreaPerPiece = area;
        this.colPackSize = packSize;
        this.colMinOrder = minOrder;
        this.colUnitPrice = unitPrice;
        this.colOpeningQty = openingQty;
        this.colGroups = groups;
        return this;
    }

    public ExcelProductInventoryReader withHeader(boolean header) {
        this.hasHeader = header;
        return this;
    }

    public ExcelProductInventoryReader enableDebug(boolean enable) {
        this.debug = enable;
        return this;
    }

    public ExcelProductInventoryReader debugRows(int rows) {
        this.debugRows = rows;
        return this;
    }

    @Override
    public ReaderResult parse(File file) throws Exception {
        if (!file.exists()) throw new IllegalArgumentException("Excel ne postoji: " + file.getAbsolutePath());

        List<Product> products = new ArrayList<>();
        Map<String, Double> opening = new LinkedHashMap<>();
        Map<String, List<String>> groupMap = new HashMap<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return emptyResult();

            int startRow = hasHeader ? 1 : 0;

            for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String code = readString(row, colCode);
                if (code == null || code.isBlank()) continue;
                String name = readString(row, colName);
                String supplier = readString(row, colSupplier);
                String mainType = readString(row, colMainType);
                String baseUnit = readString(row, colBaseUnit);
                String altUnit = readString(row, colAltUnit);
                Double areaPerPiece = readNumeric(row, colAreaPerPiece);
                Double packSize = readNumeric(row, colPackSize);
                Double minOrder = readNumeric(row, colMinOrder);
                Double unitPrice = readNumeric(row, colUnitPrice);
                Double qty = readNumeric(row, colOpeningQty);
                if (qty == null) qty = 0.0;
                String groupsRaw = readString(row, colGroups);

                Product p = new Product(
                        code.trim(),
                        trimOrNull(name),
                        trimOrNull(mainType),
                        trimOrNull(supplier),
                        trimOrNull(baseUnit),
                        trimOrNull(altUnit),
                        areaPerPiece,
                        packSize,
                        minOrder,
                        unitPrice,
                        true
                );
                products.add(p);
                opening.put(p.getProductCode(), qty);

                if (groupsRaw != null && !groupsRaw.isBlank()) {
                    String normalized = groupsRaw.replace(',', ';');
                    String[] parts = normalized.split(";");
                    List<String> gl = new ArrayList<>();
                    for (String part : parts) {
                        String g = part.trim();
                        if (!g.isEmpty()) {
                            // transform into machine code (npr. "zaštitna traka" -> zastitna_traka)
                            String codeG = g
                                    .toLowerCase(Locale.ROOT)
                                    .replaceAll("\\s+", "_")
                                    .replaceAll("[čć]", "c")
                                    .replaceAll("[š]", "s")
                                    .replaceAll("[ž]", "z")
                                    .replaceAll("[đ]", "dj");
                            gl.add(codeG);
                        }
                    }
                    groupMap.put(p.getProductCode(), gl);
                }

                if (debug && (r - startRow) < debugRows) {
                    System.out.printf(Locale.ROOT,
                            "ROW %d => code=%s qty=%.2f groups=%s%n",
                            r, p.getProductCode(), qty, groupMap.getOrDefault(p.getProductCode(), List.of()));
                }
            }
        }

        if (debug) {
            System.out.println("Parsed products: " + products.size());
        }

        List<Product> finalProducts = List.copyOf(products);
        Map<String, Double> finalOpening = Map.copyOf(opening);
        Map<String, List<String>> finalGroups = Map.copyOf(groupMap);

        return new ReaderResult() {
            @Override public List<Product> products() { return finalProducts; }
            @Override public Map<String, Double> openingQuantities() { return finalOpening; }
            @Override public Map<String, List<String>> groupAssignments() { return finalGroups; }
        };
    }

    private ReaderResult emptyResult() {
        return new ReaderResult() {
            @Override public List<Product> products() { return List.of(); }
            @Override public Map<String, Double> openingQuantities() { return Map.of(); }
            @Override public Map<String, List<String>> groupAssignments() { return Map.of(); }
        };
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private String readString(Row row, int col) {
        if (col < 0) return null;
        Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return null;
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> {
                double d = c.getNumericCellValue();
                yield (Math.floor(d) == d) ? String.valueOf((long)d) : String.valueOf(d);
            }
            case FORMULA -> {
                try { yield c.getStringCellValue(); }
                catch (Exception e) {
                    if (c.getCachedFormulaResultType()==CellType.NUMERIC)
                        yield String.valueOf(c.getNumericCellValue());
                    else if (c.getCachedFormulaResultType()==CellType.STRING)
                        yield c.getStringCellValue();
                    else yield null;
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
                try { yield Double.parseDouble(txt); } catch (NumberFormatException e) { yield null; }
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