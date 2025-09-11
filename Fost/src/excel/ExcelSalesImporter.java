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
 * Ključ: Datum | (Šifra - OPCIONALNO) | Naziv | Količina
 * Radi i kad zaglavlje nije u prvom redu, skenira sve listove i prvih 200 redova.
 */
public final class ExcelSalesImporter {

    private ExcelSalesImporter() {}

    // Formati datuma (s i bez vremena; dopuštena točka na kraju).
    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy."),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy."),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy. HH:mm"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy. HH:mm:ss"),
            DateTimeFormatter.ofPattern("d.M.yyyy H:mm"),
            DateTimeFormatter.ofPattern("d.M.yyyy H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    // Izvlačenje dijela datuma iz teksta
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

                ColumnMapping mapping = findMappingInSheet(sheet);
                if (mapping == null) continue;

                int lastRow = sheet.getLastRowNum();
                for (int r = mapping.headerRowIndex + 1; r <= lastRow; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null || isRowEmpty(row)) continue;

                    LocalDate datum = parseDate(getCell(row, mapping.idxDatum));
                    String sifra = mapping.idxSifra != null ? getString(getCell(row, mapping.idxSifra)) : "";
                    String naziv = getString(getCell(row, mapping.idxNaziv));
                    double kolicina = getDouble(getCell(row, mapping.idxKolicina));

                    if (datum == null) continue;
                    if (isBlank(sifra) && isBlank(naziv)) continue;

                    result.add(new SalesRow(datum, nullSafe(sifra), nullSafe(naziv), kolicina));
                }

                if (!result.isEmpty()) break; // prvi sheet s podacima je dovoljan
            }

            return result;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Neuspješan uvoz Excela (prodaja): " + e.getMessage(), e);
        }
    }

    // ------- Mapping (header ili heuristika) -------

    private static ColumnMapping findMappingInSheet(Sheet sheet) {
        int scanRows = Math.min(200, sheet.getLastRowNum());
        // 1) pokušaj naći red zaglavlja po nazivima
        for (int r = sheet.getFirstRowNum(); r <= scanRows; r++) {
            Row row = sheet.getRow(r);
            if (row == null || isRowEmpty(row)) continue;
            Map<String, Integer> byName = mapHeaderIndexes(row);
            if (byName.containsKey("datum") && byName.containsKey("naziv") && byName.containsKey("kolicina")) {
                Integer idxDatum = byName.get("datum");
                Integer idxNaziv = byName.get("naziv");
                Integer idxKolicina = byName.get("kolicina");
                Integer idxSifra = byName.get("sifra"); // opcionalno
                if (idxDatum != null && idxNaziv != null && idxKolicina != null) {
                    return new ColumnMapping(r, idxDatum, idxNaziv, idxKolicina, idxSifra, "header");
                }
            }
        }
        // 2) heuristika: pokuša odrediti kolone po sadržaju
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
        int idxDatum = -1, idxNaziv = -1, idxKolicina = -1;
        Integer idxSifra = null;

        idxDatum = findBestDateColumn(sheet, scanRows, maxCol);
        idxKolicina = findBestQuantityColumn(sheet, scanRows, maxCol);
        idxNaziv = findBestNameColumn(sheet, scanRows, maxCol);
        idxSifra = findBestCodeColumn(sheet, scanRows, maxCol);

        if (idxDatum >= 0 && idxNaziv >= 0 && idxKolicina >= 0) {
            return new ColumnMapping(headerRow, idxDatum, idxNaziv, idxKolicina, idxSifra, "heuristic");
        }
        return null;
    }

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
        // prvo pokušaj iz naziva
        int byHeader = findColumnByHeader(sheet, scanRows, key -> key.contains("kolicin"));
        if (byHeader >= 0) return byHeader;

        // inače: stupac s najviše numeričkih vrijednosti i niskim udjelom postotaka
        int bestCol = -1;
        int bestNumeric = 0;
        for (int c = 0; c < maxCol; c++) {
            int numeric = 0, percentLike = 0;
            for (int r = sheet.getFirstRowNum(); r <= scanRows; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell == null) continue;
                switch (cell.getCellType()) {
                    case NUMERIC -> numeric++;
                    case STRING -> {
                        String s = cell.getStringCellValue().trim();
                        if (s.endsWith("%")) percentLike++;
                        else if (parseDouble(s) != 0.0) numeric++;
                    }
                    case FORMULA -> {
                        try {
                            FormulaEvaluator fe = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
                            CellValue cv = fe.evaluate(cell);
                            if (cv != null && cv.getCellType() == CellType.NUMERIC) numeric++;
                            else if (cv != null && cv.getCellType() == CellType.STRING && cv.getStringValue().trim().endsWith("%")) percentLike++;
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (numeric > bestNumeric && percentLike < numeric / 3) {
                bestNumeric = numeric;
                bestCol = c;
            }
        }
        return bestCol;
    }

    private static int findBestNameColumn(Sheet sheet, int scanRows, int maxCol) {
        int byHeader = findColumnByHeader(sheet, scanRows, key ->
                key.contains("naziv") || key.contains("roba") || key.contains("artikl") || key.contains("proizvod") || key.equals("nazivrobeopis"));
        if (byHeader >= 0) return byHeader;

        // inače: stupac s najviše dužih tekstova
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

        // inače: alfanumerički "kodovi" (mješavina slova i brojeva, kratki)
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

    private static Map<String, Integer> mapHeaderIndexes(Row header) {
        Map<String, Integer> map = new HashMap<>();
        short first = header.getFirstCellNum();
        short last = header.getLastCellNum();
        if (first < 0 || last < 0) return map;

        for (int i = first; i < last; i++) {
            Cell c = header.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (c == null) continue;

            String raw = (c.getCellType() == CellType.STRING)
                    ? c.getStringCellValue()
                    : NumberToTextConverter.toText(getDouble(c));
            String norm = normalizeHeader(raw);
            if (norm.isEmpty()) continue;

            String key = null;

            // Datum
            if (norm.equals("datum") || norm.equals("dat") || norm.equals("datumdok")
                    || norm.equals("datumdokumenta") || norm.equals("datumknjizenja")
                    || norm.equals("datumizdavanja") || norm.equals("datdok")) {
                key = "datum";
            }
            // Količina
            else if (norm.equals("kolicina") || norm.equals("kol") || norm.equals("kolicinaukupno")
                    || norm.equals("kolicinaizdano") || norm.equals("kolicinapotroseno")
                    || norm.contains("kolicin")) {
                key = "kolicina";
            }
            // Naziv (izbjegni komitentopis)
            else if ((norm.equals("naziv") || norm.equals("nazivrobe") || norm.equals("nazivrobeopis")
                    || norm.equals("nazivartikla") || norm.equals("nazivproizvoda")
                    || norm.equals("roba") || norm.equals("artikl") || norm.equals("proizvod")
                    || norm.equals("stavka"))
                    && !norm.contains("komitent")) {
                key = "naziv";
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
            // Uobičajene nebitne glave – ignorirati (npr. tipdok, brdok, pdv, marza...)
            // else if (norm.equals("tipdok") || norm.equals("brdok") || norm.contains("dok") || ... ) { key = null; }

            // Ako je prepoznato, zapamti prvi put viđeni indeks
            if (key != null && !map.containsKey(key)) {
                map.put(key, i);
            }
        }
        return map;
    }

    private static class ColumnMapping {
        final int headerRowIndex;
        final int idxDatum, idxNaziv, idxKolicina;
        final Integer idxSifra;
        final String mode;
        ColumnMapping(int headerRowIndex, int idxDatum, int idxNaziv, int idxKolicina, Integer idxSifra, String mode) {
            this.headerRowIndex = headerRowIndex;
            this.idxDatum = idxDatum;
            this.idxNaziv = idxNaziv;
            this.idxKolicina = idxKolicina;
            this.idxSifra = idxSifra;
            this.mode = mode;
        }
    }

    // ------- Helpers -------

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static boolean hasLetter(String s) { for (char ch: s.toCharArray()) if (Character.isLetter(ch)) return true; return false; }
    private static boolean hasLetterOrDigit(String s) { for (char ch: s.toCharArray()) if (Character.isLetterOrDigit(ch)) return true; return false; }
    private static boolean containsDigit(String s) { for (char ch: s.toCharArray()) if (Character.isDigit(ch)) return true; return false; }

    private static Cell getCell(Row row, int index) {
        return (index < 0) ? null : row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    }

    private static boolean isRowEmpty(Row row) {
        if (row == null) return true;
        short first = row.getFirstCellNum();
        short last = row.getLastCellNum();
        if (first < 0 || last < 0) return true;
        for (int i = first; i < last; i++) {
            Cell c = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (c == null) continue;
            if (c.getCellType() == CellType.STRING && !c.getStringCellValue().trim().isEmpty()) return false;
            if (c.getCellType() == CellType.NUMERIC || c.getCellType() == CellType.BOOLEAN || c.getCellType() == CellType.FORMULA) return false;
        }
        return true;
    }

    private static String normalizeHeader(String s) {
        if (s == null) return "";
        // zamijeni NBSP i sl.
        s = s.replace('\u00A0', ' ').trim();
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String nullSafe(String s) { return s == null ? "" : s.trim(); }

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
                    } catch (Exception e) {
                        yield 0.0;
                    }
                }
                default -> 0.0;
            };
        } catch (Exception e) { return 0.0; }
    }

    private static double parseDouble(String s) {
        if (s == null) return 0.0;
        String v = s.trim().replace('\u00A0', ' ');
        if (v.isEmpty()) return 0.0;
        v = v.replace(" ", "");
        if (v.contains(",") && v.contains(".")) v = v.replace(",", "");
        else v = v.replace(",", ".");
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return 0.0; }
    }

    private static LocalDate parseDate(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                if (isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                }
                try {
                    return getJavaDate(cell.getNumericCellValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                } catch (Exception ignored) {}
            }
            if (cell.getCellType() == CellType.STRING) {
                String s = cell.getStringCellValue().trim();
                if (s.isEmpty()) return null;
                LocalDate ld = tryParseDateString(s);
                if (ld != null) return ld;
            }
            if (cell.getCellType() == CellType.FORMULA) {
                try {
                    FormulaEvaluator fe = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cv = fe.evaluate(cell);
                    if (cv != null && cv.getCellType() == CellType.NUMERIC) {
                        return getJavaDate(cv.getNumberValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    } else if (cv != null && cv.getCellType() == CellType.STRING) {
                        String s = cv.getStringValue().trim();
                        LocalDate ld = tryParseDateString(s);
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
                int d = Integer.parseInt(m.group(1));
                int mo = Integer.parseInt(m.group(2));
                int y = Integer.parseInt(m.group(3));
                if (y < 100) y += 2000;
                try { return LocalDate.of(y, mo, d); } catch (Exception ignored) {}
            }
            if (m.group(4) != null) {
                int y = Integer.parseInt(m.group(4));
                int mo = Integer.parseInt(m.group(5));
                int d = Integer.parseInt(m.group(6));
                try { return LocalDate.of(y, mo, d); } catch (Exception ignored) {}
            }
        }
        return null;
    }
}