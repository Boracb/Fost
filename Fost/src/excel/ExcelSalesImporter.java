package excel;

import model.SalesRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.NumberToTextConverter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.poi.ss.usermodel.DateUtil.getJavaDate;
import static org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted;

/**
 * Uvoz prodaje (.xlsx/.xls).
 * Ključ za prikaz: Naziv + Količina (OBAVEZNO). Komitent, Neto i Datum su opcionalni.
 * Radi i kad zaglavlje nije u prvom redu, skenira sve sheetove i prvih N redova.
 */
public final class ExcelSalesImporter {
    private ExcelSalesImporter() {}

    private static final boolean DEBUG = "1".equals(System.getProperty("fost.debugexcel", "0"));
    private static final int HEADER_SCAN_ROWS = Integer.getInteger("fost.excel.scanRows", 500);

    // Datumi su opcionalni; liste formata pokrivaju yy i yyyy, s/bez vremena
    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[] {
        DateTimeFormatter.ofPattern("dd.MM.yyyy"), DateTimeFormatter.ofPattern("dd.MM.yyyy."),
        DateTimeFormatter.ofPattern("d.M.yyyy"),   DateTimeFormatter.ofPattern("d.M.yyyy."),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"), DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"), DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy. HH:mm"), DateTimeFormatter.ofPattern("dd.MM.yyyy. HH:mm:ss"),
        DateTimeFormatter.ofPattern("d.M.yyyy H:mm"),     DateTimeFormatter.ofPattern("d.M.yyyy H:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),  DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd.MM.yy"), DateTimeFormatter.ofPattern("d.M.yy"),
        DateTimeFormatter.ofPattern("dd/MM/yy"), DateTimeFormatter.ofPattern("d/M/yy")
    };

    private static final Pattern DATE_PICK =
        Pattern.compile("(\\d{1,2})[.\\-/](\\d{1,2})[.\\-/](\\d{2,4})\\.?|(\\d{4})-(\\d{1,2})-(\\d{1,2})");

    public static List<SalesRow> importFile(File file) throws IOException {
        if (file == null) throw new IllegalArgumentException("Datoteka je null");
        if (!file.exists()) throw new IOException("Datoteka ne postoji: " + file.getAbsolutePath());

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {

            List<SalesRow> result = new ArrayList<>();

            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                if (sheet == null) continue;

                ColumnMapping m = findMappingInSheet(sheet);
                if (m == null) {
                    if (DEBUG) System.out.println("[ExcelSalesImporter] '" + sheet.getSheetName() + "': mapping NOT found.");
                    continue;
                }
                if (DEBUG) System.out.println("[ExcelSalesImporter] '" + sheet.getSheetName() + "': headerRow=" + m.headerRowIndex
                        + ", datum=" + m.idxDatum + ", komitent=" + m.idxKomitent + ", naziv=" + m.idxNaziv
                        + ", kolicina=" + m.idxKolicina + ", sifra=" + m.idxSifra + ", neto=" + m.idxNeto + " (" + m.mode + ")");

                int lastRow = sheet.getLastRowNum();
                int added = 0;
                for (int r = m.headerRowIndex + 1; r <= lastRow; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null || isRowEmpty(row)) continue;

                    LocalDate datum   = (m.idxDatum >= 0) ? parseDate(getCell(row, m.idxDatum)) : null;
                    String komitent   = (m.idxKomitent >= 0) ? getString(getCell(row, m.idxKomitent)) : "";
                    String sifra      = (m.idxSifra != null) ? getString(getCell(row, m.idxSifra)) : "";
                    String naziv      = getString(getCell(row, m.idxNaziv));
                    double kolicina   = getDouble(getCell(row, m.idxKolicina));
                    Double neto       = (m.idxNeto >= 0) ? getDoubleObj(getCell(row, m.idxNeto)) : null;

                    if (isBlank(naziv) && isBlank(komitent) && isBlank(sifra)) continue; // nema smislenog sadržaja
                    result.add(new SalesRow(datum, komitent, sifra, naziv, kolicina, neto));
                    added++;
                    if (DEBUG && added <= 5) System.out.println("  -> " + datum + " | " + komitent + " | " + sifra + " | " + naziv + " | " + kolicina + " | " + neto);
                }
                if (DEBUG) System.out.println("[ExcelSalesImporter] '" + sheet.getSheetName() + "': extracted=" + added);

                if (!result.isEmpty()) break;
            }
            return result;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Neuspješan uvoz Excela (prodaja): " + e.getMessage(), e);
        }
    }

    // ---------- mapiranje (header/heuristika)

    private static ColumnMapping findMappingInSheet(Sheet sheet) {
        int scanRows = Math.min(HEADER_SCAN_ROWS, Math.max(sheet.getLastRowNum(), 0));
        // Pokušaj po zaglavlju
        for (int r = sheet.getFirstRowNum(); r <= scanRows; r++) {
            Row row = sheet.getRow(r);
            if (row == null || isRowEmpty(row)) continue;
            Map<String, Integer> byName = mapHeaderIndexes(row);
            // OBAVEZNO: naziv + količina; Ostalo opcionalno
            if (byName.containsKey("naziv") && byName.containsKey("kolicina")) {
                int idxNaziv = byName.get("naziv");
                int idxKolicina = byName.get("kolicina");
                int idxDatum = byName.getOrDefault("datum", -1);
                int idxKomitent = byName.getOrDefault("komitent", -1);
                Integer idxSifra = byName.get("sifra");
                int idxNeto = byName.getOrDefault("neto", -1);
                return new ColumnMapping(r, idxDatum, idxKomitent, idxNaziv, idxKolicina, idxSifra, idxNeto, "header");
            }
        }
        // Heuristika
        return detectByHeuristics(sheet, scanRows);
    }

    private static ColumnMapping detectByHeuristics(Sheet sheet, int scanRows) {
        int maxCol = 0;
        for (int r = sheet.getFirstRowNum(); r <= scanRows; r++) {
            Row row = sheet.getRow(r);
            if (row != null) maxCol = Math.max(maxCol, row.getLastCellNum());
        }
        if (maxCol <= 0) return null;

        int headerRow = sheet.getFirstRowNum();
        int idxNaziv = findBestNameColumn(sheet, scanRows, maxCol);
        int idxKolicina = findBestQuantityColumn(sheet, scanRows, maxCol);
        if (idxNaziv < 0 || idxKolicina < 0) return null;

        int idxDatum = findBestDateColumn(sheet, scanRows, maxCol);
        int idxKomitent = -1; // teško heuristički bez riskantnih pogodaka
        Integer idxSifra = findBestCodeColumn(sheet, scanRows, maxCol);
        int idxNeto = findColumnByHeader(sheet, scanRows, key -> key.contains("neto"));

        return new ColumnMapping(headerRow, idxDatum, idxKomitent, idxNaziv, idxKolicina, idxSifra, idxNeto, "heuristic");
    }

    // ----- pomoćne: prepoznavanje kolona

    private static int findBestDateColumn(Sheet sheet, int scanRows, int maxCol) {
        int bestCol = -1, bestScore = 0;
        for (int c = 0; c < maxCol; c++) {
            int score = 0;
            for (int r = sheet.getFirstRowNum(); r <= scanRows; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                LocalDate ld = parseDate(row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                if (ld != null) score++;
            }
            if (score > bestScore) { bestScore = score; bestCol = c; }
        }
        return bestScore >= 3 ? bestCol : -1;
    }

    private static int findBestQuantityColumn(Sheet sheet, int scanRows, int maxCol) {
        int byHeader = findColumnByHeader(sheet, scanRows, key -> key.contains("kolicin"));
        if (byHeader >= 0) return byHeader;

        int bestCol = -1, bestNumeric = 0, bestNonPct = 0;
        for (int c = 0; c < maxCol; c++) {
            int numeric = 0, percentLike = 0;
            for (int r = sheet.getFirstRowNum(); r <= scanRows; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell == null) continue;
                switch (cell.getCellType()) {
                    case NUMERIC -> { numeric++; bestNonPct++; }
                    case STRING -> {
                        String s = cell.getStringCellValue().trim();
                        if (s.endsWith("%")) percentLike++;
                        else if (parseDouble(s) != 0.0) { numeric++; bestNonPct++; }
                    }
                    case FORMULA -> {
                        try {
                            FormulaEvaluator fe = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
                            CellValue cv = fe.evaluate(cell);
                            if (cv != null && cv.getCellType() == CellType.NUMERIC) { numeric++; bestNonPct++; }
                            else if (cv != null && cv.getCellType() == CellType.STRING && cv.getStringValue().trim().endsWith("%")) percentLike++;
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (numeric > bestNumeric && percentLike < (numeric / 3)) {
                bestNumeric = numeric; bestCol = c;
            }
        }
        return bestCol;
    }

    private static int findBestNameColumn(Sheet sheet, int scanRows, int maxCol) {
        int byHeader = findColumnByHeader(sheet, scanRows, key ->
            key.contains("naziv") || key.contains("robe") || key.contains("artikl") || key.contains("proizvod") || key.equals("nazivrobeopis"));
        if (byHeader >= 0) return byHeader;

        int bestCol = -1, bestScore = 0;
        for (int c = 0; c < maxCol; c++) {
            int score = 0;
            for (int r = sheet.getFirstRowNum(); r <= scanRows; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell == null) continue;
                String s = getString(cell);
                if (s.length() >= 4 && hasLetter(s)) score++;
            }
            if (score > bestScore) { bestScore = score; bestCol = c; }
        }
        return bestCol;
    }

    private static Integer findBestCodeColumn(Sheet sheet, int scanRows, int maxCol) {
        int byHeader = findColumnByHeader(sheet, scanRows, key ->
            key.contains("sifra") || key.contains("kod") || key.contains("ean") || key.contains("sku") || key.contains("plu"));
        if (byHeader >= 0) return byHeader;

        int bestCol = -1, bestScore = 0;
        for (int c = 0; c < maxCol; c++) {
            int score = 0;
            for (int r = sheet.getFirstRowNum(); r <= scanRows; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String s = getString(row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL));
                if (s.isEmpty()) continue;
                String t = s.trim();
                if (t.length() <= 20 && hasLetterOrDigit(t) && containsDigit(t)) score++;
            }
            if (score > bestScore) { bestScore = score; bestCol = c; }
        }
        return bestScore >= 3 ? bestCol : null;
    }

    private interface HeaderPredicate { boolean test(String normalizedHeader); }
    private static int findColumnByHeader(Sheet sheet, int scanRows, HeaderPredicate pred) {
        for (int r = sheet.getFirstRowNum(); r <= scanRows; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            short first = row.getFirstCellNum(), last = row.getLastCellNum();
            if (first < 0 || last < 0) continue;
            for (int c = first; c < last; c++) {
                Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell == null || cell.getCellType() != CellType.STRING) continue;
                String key = normalizeHeader(cell.getStringCellValue());
                if (pred.test(key)) return c;
            }
        }
        return -1;
    }

    private static Map<String,Integer> mapHeaderIndexes(Row header) {
        Map<String,Integer> map = new HashMap<>();
        short first = header.getFirstCellNum();
        short last = header.getLastCellNum();
        if (first < 0 || last < 0) return map;

        for (int i = first; i < last; i++) {
            Cell c = header.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (c == null) continue;

            String raw = (c.getCellType() == CellType.STRING) ? c.getStringCellValue()
                    : NumberToTextConverter.toText(getDouble(c));
            String norm = normalizeHeader(raw);
            if (norm.isEmpty()) continue;

            String key = null;
            // Datum (opcionalno)
            if (norm.equals("datum") || norm.equals("dat") || norm.equals("datumdok")
                    || norm.equals("datumdokumenta") || norm.equals("datumknjizenja")
                    || norm.equals("datumizdavanja") || norm.equals("datdok")) {
                key = "datum";
            }
            // Komitent (partner/kupac)
            else if (norm.contains("komitentopis") || norm.equals("komitent") || norm.equals("kupac") || norm.equals("partner") || norm.equals("klijent")) {
                key = "komitent";
            }
            // Količina
            else if (norm.equals("kolicina") || norm.equals("kol") || norm.equals("kolicinaukupno")
                    || norm.equals("kolicinaizdano") || norm.equals("kolicinapotroseno") || norm.contains("kolicin")) {
                key = "kolicina";
            }
            // Naziv (izbjegni komitentopis)
            else if ((norm.equals("naziv") || norm.equals("nazivrobe") || norm.equals("nazivrobeopis")
                    || norm.equals("nazivartikla") || norm.equals("nazivproizvoda")
                    || norm.equals("roba") || norm.equals("artikl") || norm.equals("proizvod")
                    || norm.equals("stavka")) && !norm.contains("komitent")) {
                key = "naziv";
            }
            // Neto vrijednost
            else if (norm.contains("netovrijednost") || norm.equals("neto")) {
                key = "neto";
            }
            // Šifra (opc.)
            else if (norm.equals("sifra") || norm.equals("sif") || norm.equals("sifartikla")
                    || norm.equals("sifraartikla") || norm.equals("sifrarobe")
                    || norm.equals("sku") || norm.equals("kod") || norm.equals("code")
                    || norm.equals("itemcode") || norm.equals("productcode")
                    || norm.equals("barkod") || norm.equals("ean") || norm.equals("ean13")
                    || norm.equals("plu")) {
                key = "sifra";
            }
            // Ostalo ignoriramo (tipdok, brdok, pdv, marza, bruto...)
            if (key != null && !map.containsKey(key)) map.put(key, i);
        }
        return map;
    }

    private static class ColumnMapping {
        final int headerRowIndex;
        final int idxDatum;      // -1 = nema datuma
        final int idxKomitent;   // -1 = nema komitenta
        final int idxNaziv;
        final int idxKolicina;
        final Integer idxSifra;  // null = nema šifre
        final int idxNeto;       // -1 = nema neto vrijednosti
        final String mode;
        ColumnMapping(int headerRowIndex, int idxDatum, int idxKomitent, int idxNaziv, int idxKolicina, Integer idxSifra, int idxNeto, String mode) {
            this.headerRowIndex = headerRowIndex;
            this.idxDatum = idxDatum;
            this.idxKomitent = idxKomitent;
            this.idxNaziv = idxNaziv;
            this.idxKolicina = idxKolicina;
            this.idxSifra = idxSifra;
            this.idxNeto = idxNeto;
            this.mode = mode;
        }
    }

    // ---------- helpers

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static boolean hasLetter(String s) { for (char ch: s.toCharArray()) if (Character.isLetter(ch)) return true; return false; }
    private static boolean hasLetterOrDigit(String s) { for (char ch: s.toCharArray()) if (Character.isLetterOrDigit(ch)) return true; return false; }
    private static boolean containsDigit(String s) { for (char ch: s.toCharArray()) if (Character.isDigit(ch)) return true; return false; }

    private static Cell getCell(Row row, int index) { return (index < 0) ? null : row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL); }

    private static boolean isRowEmpty(Row row) {
        if (row == null) return true;
        short first = row.getFirstCellNum(), last = row.getLastCellNum();
        if (first < 0 || last < 0) return true;
        for (int i = first; i < last; i++) {
            Cell c = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (c == null) continue;
            switch (c.getCellType()) {
                case STRING -> { if (!c.getStringCellValue().trim().isEmpty()) return false; }
                case NUMERIC, BOOLEAN, FORMULA -> { return false; }
                default -> {}
            }
        }
        return true;
    }

    private static String normalizeHeader(String s) {
        if (s == null) return "";
        s = s.replace('\u00A0', ' ')
        	     .replace('\u202F', ' ')
        	     .replace('\u2007', ' ')
        	     .trim();
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String getString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> NumberToTextConverter.toText(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    FormulaEvaluator fe = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cv = fe.evaluate(cell);
                    if (cv == null) yield "";
                    if (cv.getCellType() == CellType.NUMERIC) yield NumberToTextConverter.toText(cv.getNumberValue());
                    if (cv.getCellType() == CellType.STRING) yield cv.getStringValue();
                    if (cv.getCellType() == CellType.BOOLEAN) yield String.valueOf(cv.getBooleanValue());
                    yield "";
                } catch (Exception e) {
                    yield cell.getCellFormula();
                }
            }
            default -> "";
        };
    }

    private static Double getDoubleObj(Cell cell) {
        if (cell == null) return null;
        double v = getDouble(cell);
        return Double.isNaN(v) ? null : v;
    }

    private static double getDouble(Cell cell) {
        if (cell == null) return 0.0;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> cell.getNumericCellValue();
                case STRING -> parseDouble(cell.getStringCellValue());
                case FORMULA -> {
                    try {
                        FormulaEvaluator fe = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                        CellValue cv = fe.evaluate(cell);
                        if (cv != null && cv.getCellType() == CellType.NUMERIC) yield cv.getNumberValue();
                        if (cv != null && cv.getCellType() == CellType.STRING) yield parseDouble(cv.getStringValue());
                        yield 0.0;
                    } catch (Exception e) { yield 0.0; }
                }
                default -> 0.0;
            };
        } catch (Exception e) { return 0.0; }
    }

    // Robusno parsiranje: "1.078,00", "1,078.00", "1 078,00", NBSP
    private static double parseDouble(String s) {
        if (s == null) return 0.0;
        String v = s.replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2007', ' ').trim();
        v = v.replaceAll("[^0-9,\\.\\-]", "");
        if (v.isEmpty() || v.equals("-")) return 0.0;

        int lastComma = v.lastIndexOf(',');
        int lastDot   = v.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            if (lastComma > lastDot) { v = v.replace(".", ""); v = v.replace(",", "."); }
            else { v = v.replace(",", ""); }
        } else if (lastComma >= 0) {
            v = v.replace(",", ".");
        }
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return 0.0; }
    }

    private static LocalDate parseDate(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                if (isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                }
                try { return getJavaDate(cell.getNumericCellValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate(); }
                catch (Exception ignored) {}
            }
            if (cell.getCellType() == CellType.STRING) {
                String s = cell.getStringCellValue().trim();
                if (!s.isEmpty()) {
                    LocalDate ld = tryParseDateString(s);
                    if (ld != null) return ld;
                }
            }
            if (cell.getCellType() == CellType.FORMULA) {
                try {
                    FormulaEvaluator fe = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cv = fe.evaluate(cell);
                    if (cv != null && cv.getCellType() == CellType.NUMERIC) {
                        return getJavaDate(cv.getNumberValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    } else if (cv != null && cv.getCellType() == CellType.STRING) {
                        LocalDate ld = tryParseDateString(cv.getStringValue().trim());
                        if (ld != null) return ld;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static LocalDate tryParseDateString(String s) {
        for (DateTimeFormatter f : DATE_FORMATS) {
            try { return LocalDate.parse(s, f); } catch (Exception ignored) {}
            int sp = s.indexOf(' ');
            if (sp > 0) {
                String onlyDate = s.substring(0, sp);
                try { return LocalDate.parse(onlyDate, f); } catch (Exception ignored2) {}
            }
        }
        Matcher m = DATE_PICK.matcher(s);
        if (m.find()) {
            if (m.group(1) != null) {
                int d = Integer.parseInt(m.group(1)), mo = Integer.parseInt(m.group(2)), y = Integer.parseInt(m.group(3));
                if (y < 100) y += 2000;
                try { return LocalDate.of(y, mo, d); } catch (Exception ignored) {}
            } else if (m.group(4) != null) {
                int y = Integer.parseInt(m.group(4)), mo = Integer.parseInt(m.group(5)), d = Integer.parseInt(m.group(6));
                try { return LocalDate.of(y, mo, d); } catch (Exception ignored) {}
            }
        }
        return null;
    }
}