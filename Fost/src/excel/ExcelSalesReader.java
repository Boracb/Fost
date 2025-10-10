package excel;

import model.SalesRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DateUtil;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Reader za Excel izvještaj s kolonama:
 * Datum | Tip dok. | Br. dok. | Komitent / opis | Šifra | Naziv robe / opis | Količina |
 * Nabavna vrijednost | Bruto vrijednost | Iznos rabata | Rabat % | Neto vrijednost |
 * Marža | % Marže na NC | % Marže od VPC | PDV | Ukupna vrijednost
 *
 * Funkcionalnosti:
 *  - Mapiranje po headeru (case-insensitive, uklanja dijakritike/spaces).
 *  - "Carry forward" zadnji datum / tip dok. / broj dok. (često se ispuštaju u izvještaju).
 *  - Parsiranje hrvatskih decimala ("," -> ".").
 *  - Automatsko punjenje SalesRecord polja prisutnih u bazi:
 *      product_code, date, quantity, doc_type, doc_no,
 *      net_amount, gross_amount, vat_amount, discount_amount,
 *      customer_code, cogs_amount
 *  - COGS = “Nabavna vrijednost” (ukupna) – ako želiš drugačije, promijeni u kodu.
 *  - Debug ispis (enableDebug).
 */
public class ExcelSalesReader {

    /* Konfiguracija */
    private boolean hasHeader = true;
    private boolean debug = false;
    private int debugRows = 10;
    private boolean strictDate = false;

    /* Carry-forward vrijednosti */
    private LocalDate lastDate = null;
    private String lastDocType = "";
    private String lastDocNo = "";

    /* Date patterni */
    private final List<DateTimeFormatter> dateFormats = new ArrayList<>(List.of(
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
            DateTimeFormatter.ofPattern("d.M.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    ));

    /* Normalizirani nazivi headera -> ključ */
    private static final Map<String, String> HEADER_CANON = Map.ofEntries(
            entry("datum", "DATE"),
            entry("tipdok.", "DOC_TYPE"),
            entry("tipdok", "DOC_TYPE"),
            entry("br.dok.", "DOC_NO"),
            entry("br.dok", "DOC_NO"),
            entry("komitent/opis", "CUSTOMER"),
            entry("komitentopis", "CUSTOMER"),
            entry("komitent", "CUSTOMER"),
            entry("šifra", "CODE"),
            entry("sifra", "CODE"),
            entry("nazivrobe/opis", "DESC"),
            entry("nazivrobe", "DESC"),
            entry("nazivrobeopis", "DESC"),
            entry("naziv", "DESC"),
            entry("količina", "QTY"),
            entry("kolicina", "QTY"),
            entry("nabavnavrijednost", "COST_VALUE"),
            entry("brutovrijednost", "GROSS_VALUE"),
            entry("iznosrabata", "DISCOUNT_VALUE"),
            entry("rabat%", "DISCOUNT_PCT"),
            entry("rabat", "DISCOUNT_PCT"), // fallback
            entry("netovrijednost", "NET_VALUE"),
            entry("marža", "MARGIN"),
            entry("marza", "MARGIN"),
            entry("%maržena nc", "MARGIN_PCT_NC"),
            entry("%maržena", "MARGIN_PCT_NC"),
            entry("%marzena", "MARGIN_PCT_NC"),
            entry("%maržeod vpc", "MARGIN_PCT_VPC"),
            entry("%marzeod vpc", "MARGIN_PCT_VPC"),
            entry("pdv", "VAT"),
            entry("ukupnavrijednost", "TOTAL_VALUE")
    );

    private static Map.Entry<String,String> entry(String a, String b) {
        return Map.entry(a, b);
    }

    /* Fluent konfiguracija */
    public ExcelSalesReader withHeader(boolean header) {
        this.hasHeader = header; return this;
    }
    public ExcelSalesReader enableDebug(boolean dbg) {
        this.debug = dbg; return this;
    }
    public ExcelSalesReader debugRows(int rows) {
        this.debugRows = rows; return this;
    }
    public ExcelSalesReader withStrictDate(boolean strict) {
        this.strictDate = strict; return this;
    }
    public ExcelSalesReader addDatePattern(String pattern) {
        dateFormats.add(DateTimeFormatter.ofPattern(pattern));
        return this;
    }

    /* Glavna metoda */
    public List<SalesRecord> parse(File file, LocalDate fallbackDate) throws Exception {
        if (!file.exists()) throw new IllegalArgumentException("Excel ne postoji: " + file.getAbsolutePath());
        List<SalesRecord> out = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                if (debug) System.out.println("SalesReader: sheet=null");
                return out;
            }

            int lastRow = sheet.getLastRowNum();
            if (debug) System.out.println("SalesReader: lastRowIndex=" + lastRow);

            // Mapiranje headera
            Map<String, Integer> colMap = new HashMap<>();
            int startRow = 0;
            if (hasHeader) {
                Row header = sheet.getRow(0);
                if (header == null) return out;
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    Cell cell = header.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell == null) continue;
                    String raw = getCellString(cell);
                    if (raw == null) continue;
                    String norm = normalize(raw);
                    String canon = HEADER_CANON.get(norm);
                    if (canon != null) {
                        colMap.put(canon, c);
                        if (debug) System.out.println("Header map: '" + raw + "' -> " + canon + " (col " + c + ")");
                    }
                }
                startRow = 1;
            }

            DataFormatter formatter = new DataFormatter(Locale.getDefault());

            for (int r = startRow; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                // DATUM
                LocalDate date = readDate(row, colMap.get("DATE"), formatter, fallbackDate);
                if (date == null) {
                    if (strictDate) {
                        if (debug) System.out.println("SKIP r=" + r + " (nema valjanog datuma, strict)");
                        continue;
                    } else {
                        date = (fallbackDate != null) ? fallbackDate : LocalDate.now();
                    }
                } else {
                    lastDate = date;
                }
                if (date == null) date = lastDate; // carry
                if (date == null) {
                    if (debug) System.out.println("SKIP r=" + r + " (date i nakon carry = null)");
                    continue;
                }

                // DOC TYPE
                String docType = readString(row, colMap.get("DOC_TYPE"), formatter);
                if (docType != null && !docType.isBlank()) lastDocType = docType;
                docType = (docType == null || docType.isBlank()) ? lastDocType : docType;

                // DOC NO
                String docNo = readString(row, colMap.get("DOC_NO"), formatter);
                if (docNo != null && !docNo.isBlank()) lastDocNo = docNo;
                docNo = (docNo == null || docNo.isBlank()) ? lastDocNo : docNo;

                // CUSTOMER
                String customer = readString(row, colMap.get("CUSTOMER"), formatter);

                // PRODUCT CODE (obvezno)
                String productCode = readString(row, colMap.get("CODE"), formatter);
                if (productCode == null || productCode.isBlank()) {
                    if (debug) System.out.println("SKIP r=" + r + " (nema Šifra)");
                    continue;
                }
                productCode = productCode.trim();
                // Često se dogodi da “Šifra” izgleda “: 32050035” u nekom scenariju
                if (productCode.startsWith(":")) productCode = productCode.substring(1).trim();

                // QUANTITY
                Double qty = readNumber(row, colMap.get("QTY"), formatter);
                if (qty == null || Math.abs(qty) < 1e-12) {
                    if (debug) System.out.println("SKIP r=" + r + " (qty null/0)");
                    continue;
                }

                // COST (nabavna vrijednost) – uzet ćemo kao cogs_amount (ukupno)
                Double costValue = readNumber(row, colMap.get("COST_VALUE"), formatter);

                // GROSS
                Double grossValue = readNumber(row, colMap.get("GROSS_VALUE"), formatter);

                // DISCOUNT VAL
                Double discountVal = readNumber(row, colMap.get("DISCOUNT_VALUE"), formatter);

                // NET
                Double netValue = readNumber(row, colMap.get("NET_VALUE"), formatter);

                // VAT
                Double vatValue = readNumber(row, colMap.get("VAT"), formatter);

                // Sastavi SalesRecord
                SalesRecord rec = new SalesRecord();
                rec.setProductCode(productCode);
                rec.setDate(date);
                rec.setQuantity(qty);

                // Doc meta
                rec.setDocType(docType == null ? "" : docType);
                rec.setDocNo(docNo == null ? "" : docNo);

                if (customer != null && !customer.isBlank()) {
                    // customer_code – ako želiš skratiti, može regex; ostavljam raw
                    rec.setCustomerCode(customer.trim());
                }

                if (netValue != null)    rec.setNetAmount(BigDecimal.valueOf(netValue));
                if (grossValue != null)  rec.setGrossAmount(BigDecimal.valueOf(grossValue));
                if (vatValue != null)    rec.setVatAmount(BigDecimal.valueOf(vatValue));
                if (discountVal != null) rec.setDiscountAmount(BigDecimal.valueOf(discountVal));
                if (costValue != null)   rec.setCogsAmount(costValue); // total cost

                out.add(rec);

                if (debug && out.size() <= debugRows) {
                    System.out.println("READ r=" + r +
                            " code=" + productCode +
                            " qty=" + qty +
                            " net=" + netValue +
                            " cost=" + costValue +
                            " gross=" + grossValue +
                            " date=" + date +
                            " doc=" + docType + "/" + docNo);
                }
            }
        }

        if (debug) System.out.println("Parsed sales rows: " + out.size());
        return out;
    }

    /* ----------------- Helpers ----------------- */

    private LocalDate readDate(Row row, Integer colIdx, DataFormatter fmt, LocalDate fallback) {
        Cell cell = (colIdx == null) ? null : row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            // carry-forward
            if (lastDate != null) return lastDate;
            return strictDate ? null : (fallback != null ? fallback : LocalDate.now());
        }
        try {
            CellType ct = cell.getCellType();
            if (ct == CellType.FORMULA) ct = cell.getCachedFormulaResultType();

            if (ct == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date d = cell.getDateCellValue();
                    return toLocalDate(d);
                } else {
                    double v = cell.getNumericCellValue();
                    if (v > 20000 && v < 90000) {
                        Date d = DateUtil.getJavaDate(v);
                        return toLocalDate(d);
                    }
                    String asText = fmt.formatCellValue(cell);
                    return parseDateString(asText, fallback);
                }
            } else if (ct == CellType.STRING) {
                String s = fmt.formatCellValue(cell);
                return parseDateString(s, fallback);
            }
            return strictDate ? null : (fallback != null ? fallback : LocalDate.now());
        } catch (Exception e) {
            return strictDate ? null : (fallback != null ? fallback : LocalDate.now());
        }
    }

    private LocalDate parseDateString(String txt, LocalDate fallback) {
        if (txt == null) return strictDate ? null : (fallback != null ? fallback : LocalDate.now());
        txt = txt.trim();
        if (txt.isEmpty()) return strictDate ? null : (fallback != null ? fallback : LocalDate.now());
        txt = txt.replace('\u00A0', ' '); // NBSP

        for (DateTimeFormatter f : dateFormats) {
            try { return LocalDate.parse(txt, f); } catch (Exception ignored) {}
        }
        // pokušaj izvući samo datum dio
        int space = txt.indexOf(' ');
        if (space > 0) {
            String only = txt.substring(0, space);
            for (DateTimeFormatter f : dateFormats) {
                try { return LocalDate.parse(only, f); } catch (Exception ignored) {}
            }
        }
        return strictDate ? null : (fallback != null ? fallback : LocalDate.now());
    }

    private LocalDate toLocalDate(Date d) {
        if (d == null) return null;
        return Instant.ofEpochMilli(d.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    private String readString(Row row, Integer colIdx, DataFormatter fmt) {
        if (colIdx == null) return null;
        Cell c = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return null;
        String v = fmt.formatCellValue(c);
        return (v == null || v.trim().isEmpty()) ? null : v.trim();
    }

    private Double readNumber(Row row, Integer colIdx, DataFormatter fmt) {
        if (colIdx == null) return null;
        Cell c = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return null;
        try {
            CellType ct = c.getCellType();
            if (ct == CellType.FORMULA) ct = c.getCachedFormulaResultType();
            if (ct == CellType.NUMERIC) return c.getNumericCellValue();
            String s = fmt.formatCellValue(c);
            if (s == null) return null;
            s = s.trim().replace('.', '#'); // ako ima tisućice s točkama -> privremeno
            s = s.replace(',', '.');
            s = s.replace("#", ""); // makni tisućice
            if (s.isEmpty()) return null;
            if (s.matches("[-+]?[0-9]*\\.?[0-9]+")) return Double.parseDouble(s);
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getCellString(Cell cell) {
        if (cell == null) return null;
        try {
            cell.setCellType(CellType.STRING);
            return cell.getStringCellValue().trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.ROOT).trim();
        // ukloni dijakritiku
        lower = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        // ukloni višak razmaka i točke/spojeve
        lower = lower.replaceAll("\\s+", "");
        return lower;
    }
}