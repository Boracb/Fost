package excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExcelSalesReader {

    public static class RawRow {
        public LocalDate date;
        public String docType;
        public String docNo;
        public String productCode; // Šifra
        public String productName; // Naziv robe / opis
        public double quantity;
        public BigDecimal netAmount;
        public BigDecimal grossAmount;
        public BigDecimal vatAmount;
        public BigDecimal discountAmount;
    }

    private static final String H_DATE = "datum";
    private static final String H_DOC_TYPE = "tip dok";
    private static final String H_DOC_NO = "br. dok";
    private static final String H_CODE = "šifra";
    private static final String H_NAME = "naziv robe";
    private static final String H_QTY = "količina";
    private static final String H_NET = "neto vrijednost";
    private static final String H_GROSS = "bruto vrijednost";
    private static final String H_PDV = "pdv";
    private static final String H_DISCOUNT = "iznos rabata";

    // Regex za datume u tekstu, tolerira razmake i točku na kraju: dd[./-]MM[./-]yyyy (s opcionalnom točkom)
    private static final Pattern DATE_RX = Pattern.compile(
            "\\b(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})\\s*[./-]\\s*(\\d{2,4})\\.?\\b"
    );

    public List<RawRow> read(InputStream in) throws Exception {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);

            int headerRowIdx = detectHeaderRow(sheet);
            if (headerRowIdx < 0) {
                throw new IllegalArgumentException("Header nije pronađen u prvih 50 redova.");
            }
            Row header = sheet.getRow(headerRowIdx);
            Map<String, Integer> idx = mapHeader(header);

            if (!idx.containsKey(H_DATE) || !idx.containsKey(H_CODE) || !idx.containsKey(H_QTY)) {
                StringBuilder sb = new StringBuilder("Header mora sadržavati barem: Datum, Šifra, Količina. Nađeno: ");
                List<String> found = new ArrayList<>();
                idx.forEach((k, v) -> found.add(k + "->" + v));
                sb.append(String.join(", ", found));
                throw new IllegalArgumentException(sb.toString());
            }

            int dateCol = idx.get(H_DATE);

            List<RawRow> rows = new ArrayList<>();
            LocalDate lastDateSeen = null;
            Deque<RawRow> pendingBeforeFirstDate = new ArrayDeque<>();

            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                if (isRowEmpty(row)) continue;

                LocalDate thisDate = readLocalDateSmart(sheet, row, dateCol);
                RawRow rr = new RawRow();
                rr.date = thisDate; // zasad stvarni datum iz reda (može biti null)
                rr.docType = readString(row, idx.get(H_DOC_TYPE));
                rr.docNo = readString(row, idx.get(H_DOC_NO));
                rr.productCode = readString(row, idx.get(H_CODE));
                rr.productName = readString(row, idx.get(H_NAME));
                rr.quantity = readDouble(row, idx.get(H_QTY));
                rr.netAmount = readDecimal(row, idx.get(H_NET));
                rr.grossAmount = readDecimal(row, idx.get(H_GROSS));
                rr.vatAmount = readDecimal(row, idx.get(H_PDV));
                rr.discountAmount = readDecimal(row, idx.get(H_DISCOUNT));

                if (thisDate != null) {
                    // Ako je prvi put viđen datum – back-fill svim pending redovima iznad
                    if (lastDateSeen == null) {
                        while (!pendingBeforeFirstDate.isEmpty()) {
                            RawRow prev = pendingBeforeFirstDate.pollFirst();
                            prev.date = thisDate;
                            rows.add(prev);
                        }
                    }
                    lastDateSeen = thisDate;
                    rows.add(rr);
                } else {
                    if (lastDateSeen != null) {
                        rr.date = lastDateSeen; // forward-fill
                        rows.add(rr);
                    } else {
                        // Još nismo vidjeli nijedan datum – spremi za back-fill kad naiđemo na prvi
                        pendingBeforeFirstDate.addLast(rr);
                    }
                }
            }

            // Ako nismo našli nijedan datum u cijeloj radnoj tablici, zadrži te retke bez datuma; caller će prijaviti
            rows.addAll(pendingBeforeFirstDate);
            return rows;
        }
    }

    private int detectHeaderRow(Sheet sheet) {
        int last = Math.min(sheet.getLastRowNum(), 50);
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, Integer> idx = mapHeader(row);
            if (idx.containsKey(H_DATE) && idx.containsKey(H_CODE) && idx.containsKey(H_QTY)) {
                return r;
            }
        }
        return -1;
    }

    private Map<String, Integer> mapHeader(Row header) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell c : header) {
            String raw = getString(c);
            if (raw == null) continue;
            String k = normalizeHeader(raw);

            if (containsAny(k, "datum", "date", "dat")) { map.putIfAbsent(H_DATE, c.getColumnIndex()); continue; }
            if (containsAll(k, "tip", "dok")) { map.putIfAbsent(H_DOC_TYPE, c.getColumnIndex()); continue; }
            if (containsAll(k, "dok") && containsAny(k, "br", "broj")) { map.putIfAbsent(H_DOC_NO, c.getColumnIndex()); continue; }
            if (containsAny(k, "šifra", "sifra", "sifraartikla", "šifraartikla", "sifrarobe", "šifrarobe", "code")) { map.putIfAbsent(H_CODE, c.getColumnIndex()); continue; }
            if (containsAny(k, "naziv") || containsAny(k, "opis")) { map.putIfAbsent(H_NAME, c.getColumnIndex()); continue; }
            if (containsAny(k, "količina", "kolicina", "qty", "quantity") || k.startsWith("kol")) { map.putIfAbsent(H_QTY, c.getColumnIndex()); continue; }
            if (containsAll(k, "neto", "vrijed")) { map.putIfAbsent(H_NET, c.getColumnIndex()); continue; }
            if (containsAll(k, "bruto", "vrijed")) { map.putIfAbsent(H_GROSS, c.getColumnIndex()); continue; }
            if (k.equals("pdv")) { map.putIfAbsent(H_PDV, c.getColumnIndex()); continue; }
            if (containsAll(k, "iznos", "rabat")) { map.putIfAbsent(H_DISCOUNT, c.getColumnIndex()); }
        }
        return map;
    }

    private String normalizeHeader(String s) {
        if (s == null) return null;
        String t = s.replace('\u00A0', ' ') // NBSP -> space
                    .replace('\u2007', ' ')
                    .replace('\u202F', ' ')
                    .trim()
                    .toLowerCase(Locale.ROOT);
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        t = t.replaceAll("[\\p{Punct}]+", " ");
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private boolean containsAny(String hay, String... needles) {
        for (String n : needles) if (hay.contains(n)) return true;
        return false;
    }

    private boolean containsAll(String hay, String... needles) {
        for (String n : needles) if (!hay.contains(n)) return false;
        return true;
    }

    private boolean isRowEmpty(Row row) {
        for (Cell c : row) {
            if (c == null) continue;
            if (c.getCellType() == CellType.BLANK) continue;
            String s = getString(c);
            if (s != null && !s.isBlank()) return false;
            if (c.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(c) && c.getNumericCellValue() != 0.0) {
                return false;
            }
        }
        return true;
    }

    private String getString(Cell c) {
        if (c == null) return null;
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(c) ? null : numericToString(c.getNumericCellValue());
            case FORMULA -> {
                try {
                    FormulaEvaluator evaluator = c.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cv = evaluator.evaluate(c);
                    yield switch (cv.getCellType()) {
                        case STRING -> cv.getStringValue().trim();
                        case NUMERIC -> DateUtil.isCellDateFormatted(c) ? null : numericToString(cv.getNumberValue());
                        default -> null;
                    };
                } catch (Exception e) { yield null; }
            }
            default -> null;
        };
    }

    private String numericToString(double v) {
        if (Math.floor(v) == v) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    private LocalDate readLocalDateSmart(Sheet sheet, Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null || isBlank(c)) {
            Cell topLeft = getMergedRegionTopLeft(sheet, row.getRowNum(), col);
            if (topLeft != null) c = topLeft;
        }
        return parseDateCell(c);
    }

    private boolean isBlank(Cell c) {
        if (c == null) return true;
        if (c.getCellType() == CellType.BLANK) return true;
        if (c.getCellType() == CellType.STRING) return c.getStringCellValue() == null || c.getStringCellValue().trim().isEmpty();
        return false;
    }

    private LocalDate parseDateCell(Cell c) {
        if (c == null) return null;

        try {
            // 1) Ako je Excel numeric i validan datum, uzmi ga (čak i bez date formata)
            if (c.getCellType() == CellType.NUMERIC) {
                double num = c.getNumericCellValue();
                if (DateUtil.isCellDateFormatted(c) || DateUtil.isValidExcelDate(num)) {
                    return DateUtil.getJavaDate(num).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                }
            }

            // 2) Ako je formula – evaluiraj pa ponovi logiku
            if (c.getCellType() == CellType.FORMULA) {
                FormulaEvaluator evaluator = c.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                CellValue cv = evaluator.evaluate(c);
                if (cv != null) {
                    if (cv.getCellType() == CellType.NUMERIC) {
                        double num = cv.getNumberValue();
                        if (DateUtil.isValidExcelDate(num)) {
                            return DateUtil.getJavaDate(num).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        }
                    } else if (cv.getCellType() == CellType.STRING) {
                        LocalDate parsed = parseDateText(cv.getStringValue());
                        if (parsed != null) return parsed;
                    }
                }
            }

            // 3) Tekstualni datum
            if (c.getCellType() == CellType.STRING) {
                String s = c.getStringCellValue();
                LocalDate parsed = parseDateText(s);
                if (parsed != null) return parsed;
            }
        } catch (Exception ignored) {}

        return null;
    }

    private LocalDate parseDateText(String s) {
        if (s == null) return null;
        String t = s.replace('\u00A0', ' ')
                    .replace('\u2007', ' ')
                    .replace('\u202F', ' ')
                    .trim();
        Matcher m = DATE_RX.matcher(t);
        if (m.find()) {
            int d = Integer.parseInt(m.group(1));
            int M = Integer.parseInt(m.group(2));
            int y = Integer.parseInt(m.group(3));
            if (y < 100) y += 2000;
            try { return LocalDate.of(y, M, d); } catch (Exception ignored) {}
        }
        return null;
    }

    private Cell getMergedRegionTopLeft(Sheet sheet, int row, int col) {
        int count = sheet.getNumMergedRegions();
        for (int i = 0; i < count; i++) {
            CellRangeAddress r = sheet.getMergedRegion(i);
            if (r.isInRange(row, col)) {
                Row topRow = sheet.getRow(r.getFirstRow());
                if (topRow == null) return null;
                return topRow.getCell(r.getFirstColumn());
            }
        }
        return null;
    }

    private String readString(Row row, Integer col) {
        if (col == null) return null;
        return getString(row.getCell(col));
    }

    private double readDouble(Row row, Integer col) {
        if (col == null) return 0.0;
        Cell c = row.getCell(col);
        if (c == null) return 0.0;
        if (c.getCellType() == CellType.NUMERIC) return c.getNumericCellValue();
        String s = getString(c);
        if (s == null || s.isBlank()) return 0.0;
        s = s.replace(".", "").replace(",", ".");
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }

    private java.math.BigDecimal readDecimal(Row row, Integer col) {
        double d = readDouble(row, col);
        return d == 0.0 ? null : java.math.BigDecimal.valueOf(d);
    }
}