package excel;

import model.SalesRow;
import org.apache.poi.ss.usermodel.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Uvoz proizvodnje iz Excel datoteka (.xlsx i .xls) u List<SalesRow>.
 * - Traži zaglavlje (Naziv, Količina) unutar prvih N redova (default 50; -Dfost.excel.scanRows=500 npr.)
 * - Podržava hrvatske nazive i varijacije (npr. "Naziv robe / opis", "Količina", "Šifra", "Datum", "Komitent / opis", "Neto vrijednost")
 * - Heuristički fallback ako zaglavlje nedostaje ili je poremećeno
 * - Robusno parsiranje količina (EU/US format, razni NBSP razmaci)
 * - Datum i Šifra su opcionalni; red se uvozi ako postoje Naziv i Količina
 *
 * Debug: -Dfost.debugexcel=1 ispisuje mapiranja i uzorke redaka.
 */
public final class ExcelProductionImporter {

    private ExcelProductionImporter() {}

    public static java.util.List<SalesRow> importViaChooser(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Odaberi Excel datoteku proizvodnje");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel (*.xlsx, *.xls)", "xlsx", "xls"));
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return java.util.List.of();
        File file = chooser.getSelectedFile();
        try {
            java.util.List<SalesRow> rows = importFile(file);
            if (rows.isEmpty()) {
                JOptionPane.showMessageDialog(parent,
                        "Nije pronađen nijedan red proizvodnje.\n" +
                                "- Provjeri gdje je zaglavlje (Datum, (Šifra), Naziv, Količina) — tražimo ga unutar prvih 50 redova.",
                        "Uvoz proizvodnje",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            return rows;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent, "Greška pri uvozu:\n" + ex.getMessage(),
                    "Uvoz proizvodnje", JOptionPane.ERROR_MESSAGE);
            return java.util.List.of();
        }
    }

    public static java.util.List<SalesRow> importFile(File file) throws IOException {
        boolean debug = "1".equals(System.getProperty("fost.debugexcel"));
        int scanRows = Integer.getInteger("fost.excel.scanRows", 50);

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {

            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                if (sheet == null) continue;

                HeaderMapping mapping = detectHeader(sheet, scanRows, debug);
                if (debug) System.out.println("[ExcelProductionImporter] Sheet=" + sheet.getSheetName() + " mapping=" + mapping);

                java.util.List<SalesRow> out = extractRows(sheet, mapping, scanRows, debug);
                if (!out.isEmpty()) return out;
            }
        }
        return java.util.List.of();
    }

    private static java.util.List<SalesRow> extractRows(Sheet sheet, HeaderMapping mapping, int scanRows, boolean debug) {
        java.util.List<SalesRow> result = new ArrayList<>();
        if (mapping == null || !mapping.hasNameAndQty()) {
            mapping = heuristicColumns(sheet, scanRows, debug);
            if (debug) System.out.println("[ExcelProductionImporter] Heuristic mapping: " + mapping);
            if (mapping == null || !mapping.hasNameAndQty()) return result;
        }

        int startRow = mapping.dataStartRow;
        int lastRow = sheet.getLastRowNum();
        for (int r = startRow; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String naziv = getCellAsString(row, mapping.colNaziv);
            if (naziv.isBlank()) continue;

            Double qty = getCellAsDouble(row, mapping.colKolicina);
            if (qty == null) {
                String s = getCellAsString(row, mapping.colKolicina);
                qty = parseSmartDouble(s);
            }
            if (qty == null) continue;

            LocalDate datum = null;
            if (mapping.colDatum >= 0) datum = getCellAsLocalDate(row, mapping.colDatum);

            String sifra = mapping.colSifra >= 0 ? getCellAsString(row, mapping.colSifra) : "";
            String komitent = mapping.colKomitent >= 0 ? getCellAsString(row, mapping.colKomitent) : "";
            Double neto = mapping.colNeto >= 0 ? getCellAsDouble(row, mapping.colNeto) : null;
            if (neto == null && mapping.colNeto >= 0) {
                String s = getCellAsString(row, mapping.colNeto);
                neto = parseSmartDouble(s);
            }

            // Redoslijed usklađen s SalesRow(datum, komitent, sifra, naziv, kolicina, neto)
            SalesRow sr = new SalesRow(datum, nullIfBlank(komitent), nullIfBlank(sifra), naziv, qty, neto);
            result.add(sr);
        }

        if (debug && !result.isEmpty()) {
            System.out.println("[ExcelProductionImporter] Sample rows:");
            for (int i = 0; i < Math.min(5, result.size()); i++) {
                System.out.println("  " + result.get(i));
            }
        }
        return result;
    }

    private static HeaderMapping detectHeader(Sheet sheet, int scanRows, boolean debug) {
        int maxRow = Math.min(sheet.getLastRowNum(), scanRows);
        HeaderMapping best = null;
        for (int r = 0; r <= maxRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            HeaderMapping hm = tryMapHeaderRow(row, r);
            if (hm != null && hm.hasNameAndQty()) {
                if (debug) System.out.println("[ExcelProductionImporter] Header at row " + r + ": " + hm);
                return hm;
            }
            if (best == null || (hm != null && hm.score() > best.score())) best = hm;
        }
        return best;
    }

    private static HeaderMapping tryMapHeaderRow(Row row, int headerRowIdx) {
        HeaderMapping hm = new HeaderMapping(headerRowIdx + 1);
        int lastCell = row.getLastCellNum();
        for (int c = 0; c < lastCell; c++) {
            String raw = getCellAsString(row, c);
            if (raw.isBlank()) continue;
            String key = normalizeHeader(raw);

            if (hm.colDatum < 0 && anyMatch(key, "datum", "dat", "datumdok", "datumdokumenta", "datumknjizenja", "datumizdavanja", "datdok"))
                hm.colDatum = c;

            if (hm.colSifra < 0 && anyMatch(key, "sifra", "sif", "sifartikla", "sifraartikla", "sifrarobe", "sku", "kod", "code", "itemcode", "productcode", "barkod", "ean", "ean13", "plu"))
                hm.colSifra = c;

            if (hm.colNaziv < 0 && isNazivKey(key))
                hm.colNaziv = c;

            if (hm.colKolicina < 0 && (key.contains("kolicin") || anyMatch(key, "kolicina", "kol", "kolicinaukupno", "kolicinaizdano", "kolicinapotroseno")))
                hm.colKolicina = c;

            if (hm.colKomitent < 0 && anyMatch(key, "komitent", "komitentopis", "kupac", "partner", "klijent"))
                hm.colKomitent = c;

            if (hm.colNeto < 0 && anyMatch(key, "netovrijednost", "neto"))
                hm.colNeto = c;
        }

        if (hm.hasAnyHit()) return hm;
        return null;
    }

    private static HeaderMapping heuristicColumns(Sheet sheet, int scanRows, boolean debug) {
        int lastRow = Math.min(sheet.getLastRowNum(), scanRows);
        int maxCols = 0;
        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row != null) maxCols = Math.max(maxCols, row.getLastCellNum());
        }
        if (maxCols <= 0) return null;

        int[] numericCounts = new int[maxCols];
        int[] textCounts = new int[maxCols];
        int[] dateCounts = new int[maxCols];
        double[] avgTextLen = new double[maxCols];

        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < maxCols; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) continue;
                switch (cell.getCellType()) {
                    case STRING:
                        String s = cell.getStringCellValue().trim();
                        if (!s.isEmpty()) {
                            textCounts[c]++;
                            avgTextLen[c] += Math.min(s.length(), 200);
                            if (tryParseDateText(s) != null) dateCounts[c]++;
                            if (parseSmartDouble(s) != null) numericCounts[c]++;
                        }
                        break;
                    case NUMERIC:
                        if (DateUtil.isCellDateFormatted(cell)) {
                            dateCounts[c]++;
                        } else {
                            numericCounts[c]++;
                        }
                        break;
                    default:
                        // ignore
                }
            }
        }
        for (int c = 0; c < maxCols; c++) {
            if (textCounts[c] > 0) avgTextLen[c] /= textCounts[c];
        }

        HeaderMapping hm = new HeaderMapping(0);
        hm.colKolicina = argMax(numericCounts);
        hm.colNaziv = argMax(avgTextLen);
        int dateCol = argMax(dateCounts);
        hm.colDatum = dateCounts[dateCol] > 0 ? dateCol : -1;

        hm.colSifra = findCodeLikeColumn(sheet, lastRow, maxCols, hm.colNaziv, hm.colKolicina, hm.colDatum);

        return hm;
    }

    private static int findCodeLikeColumn(Sheet sheet, int lastRow, int maxCols, int nazivCol, int qtyCol, int dateCol) {
        int bestCol = -1;
        int bestScore = -1;
        for (int c = 0; c < maxCols; c++) {
            if (c == nazivCol || c == qtyCol || c == dateCol) continue;
            int score = 0;
            int sample = 0;
            for (int r = 0; r <= lastRow && sample < 50; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String s = getCellAsString(row, c);
                if (s.isBlank()) continue;
                sample++;
                if (s.length() <= 24) score += 2;
                if (CODE_LIKE.matcher(s).matches()) score += 3;
                if (s.chars().filter(Character::isDigit).count() > 0) score++;
                if (s.trim().contains(" ")) score--;
            }
            if (sample >= 5 && score > bestScore) {
                bestScore = score;
                bestCol = c;
            }
        }
        return bestCol;
    }

    private static String getCellAsString(Row row, int col) {
        if (col < 0) return "";
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING:  return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        LocalDate d = getCellAsLocalDate(row, col);
                        return d != null ? d.toString() : "";
                    } else {
                        return stripTrailingZeros(cell.getNumericCellValue());
                    }
                case BOOLEAN: return Boolean.toString(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        FormulaEvaluator ev = row.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                        CellValue cv = ev.evaluate(cell);
                        if (cv == null) return "";
                        switch (cv.getCellType()) {
                            case STRING:  return cv.getStringValue().trim();
                            case NUMERIC: return stripTrailingZeros(cv.getNumberValue());
                            case BOOLEAN: return Boolean.toString(cv.getBooleanValue());
                            default: return "";
                        }
                    } catch (Exception e) {
                        return cell.toString().trim();
                    }
                default:
                    return cell.toString().trim();
            }
        } catch (Exception e) {
            return cell.toString().trim();
        }
    }

    private static Double getCellAsDouble(Row row, int col) {
        if (col < 0) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
                return cell.getNumericCellValue();
            }
            if (cell.getCellType() == CellType.STRING) {
                return parseSmartDouble(cell.getStringCellValue());
            }
            if (cell.getCellType() == CellType.FORMULA) {
                FormulaEvaluator ev = row.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                CellValue cv = ev.evaluate(cell);
                if (cv == null) return null;
                if (cv.getCellType() == CellType.NUMERIC) return cv.getNumberValue();
                if (cv.getCellType() == CellType.STRING) return parseSmartDouble(cv.getStringValue());
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static LocalDate getCellAsLocalDate(Row row, int col) {
        if (col < 0) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                Date d = cell.getDateCellValue();
                return d == null ? null : Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
            }
            if (cell.getCellType() == CellType.STRING) {
                return tryParseDateText(cell.getStringCellValue());
            }
            if (cell.getCellType() == CellType.FORMULA) {
                FormulaEvaluator ev = row.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                CellValue cv = ev.evaluate(cell);
                if (cv == null) return null;
                if (cv.getCellType() == CellType.NUMERIC) {
                    double v = cv.getNumberValue();
                    Date d = DateUtil.getJavaDate(v);
                    return d == null ? null : Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
                }
                if (cv.getCellType() == CellType.STRING) {
                    return tryParseDateText(cv.getStringValue());
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static final char[] NBSP = new char[]{'\u00A0', '\u202F', '\u2007'};

    private static Double parseSmartDouble(String rawIn) {
        if (rawIn == null) return null;
        String raw = rawIn.trim();
        if (raw.isEmpty()) return null;
        if (raw.contains("%")) return null;

        for (char nb : NBSP) raw = raw.replace(nb, ' ');
        raw = raw.replace(" ", "");

        raw = raw.replaceAll("[^0-9,.-]", "");
        if (raw.isEmpty() || raw.equals("-") || raw.equals(".") || raw.equals(",")) return null;

        int lastComma = raw.lastIndexOf(',');
        int lastDot = raw.lastIndexOf('.');
        char decimalSep;
        if (lastComma >= 0 && lastDot >= 0) {
            decimalSep = lastComma > lastDot ? ',' : '.';
        } else if (lastComma >= 0) {
            decimalSep = ',';
        } else {
            decimalSep = '.';
        }

        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isDigit(ch) || ch == '-') {
                sb.append(ch);
            } else if (ch == ',' || ch == '.') {
                if (ch == decimalSep) {
                    sb.append('.');
                }
            }
        }
        String norm = sb.toString();
        if (norm.equals("-") || norm.isEmpty() || norm.equals(".")) return null;
        try {
            return Double.parseDouble(norm);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate tryParseDateText(String sIn) {
        if (sIn == null) return null;
        String s = sIn.trim();
        if (s.isEmpty()) return null;
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1).trim();

        DateTimeFormatter[] fmts = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("d.M.yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy")
        };
        for (DateTimeFormatter f : fmts) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private static String stripTrailingZeros(double v) {
        String str = Double.toString(v);
        return str.endsWith(".0") ? str.substring(0, str.length() - 2) : str;
    }

    private static String normalizeHeader(String sIn) {
        if (sIn == null) return "";
        String s = sIn;
        for (char nb : NBSP) s = s.replace(nb, ' ');
        s = s.trim().toLowerCase(Locale.ROOT);
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replaceAll("[^a-z0-9]+", "");
        return s;
    }

    private static boolean isNazivKey(String key) {
        if (key.contains("komitentopis")) return false;
        return anyMatch(key, "naziv", "nazivrobe", "nazivrobeopis", "nazivartikla", "nazivproizvoda", "roba", "artikl", "proizvod", "stavka", "opis", "nazivrobeopis");
    }

    private static boolean anyMatch(String key, String... opts) {
        for (String o : opts) if (key.equals(o)) return true;
        return false;
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private static int argMax(int[] arr) {
        int idx = -1, best = Integer.MIN_VALUE;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] > best) { best = arr[i]; idx = i; }
        }
        return idx;
    }

    private static int argMax(double[] arr) {
        int idx = -1; double best = -1e300;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] > best) { best = arr[i]; idx = i; }
        }
        return idx;
    }

    private static final java.util.regex.Pattern CODE_LIKE = java.util.regex.Pattern.compile("^[A-Za-z0-9_\\-./]{2,24}$");

    private static final class HeaderMapping {
        int dataStartRow;
        int colDatum = -1;
        int colSifra = -1;
        int colNaziv = -1;
        int colKolicina = -1;
        int colKomitent = -1;
        int colNeto = -1;

        HeaderMapping(int dataStartRow) { this.dataStartRow = Math.max(0, dataStartRow); }

        boolean hasNameAndQty() { return colNaziv >= 0 && colKolicina >= 0; }
        boolean hasAnyHit() {
            return colNaziv >= 0 || colKolicina >= 0 || colDatum >= 0 || colSifra >= 0 || colKomitent >= 0 || colNeto >= 0;
        }

        int score() {
            int sc = 0;
            if (colNaziv >= 0) sc += 4;
            if (colKolicina >= 0) sc += 4;
            if (colDatum >= 0) sc += 2;
            if (colSifra >= 0) sc += 2;
            if (colKomitent >= 0) sc += 1;
            if (colNeto >= 0) sc += 1;
            return sc;
        }

        @Override public String toString() {
            return "Header{start=" + dataStartRow + ", datum=" + colDatum + ", sifra=" + colSifra +
                    ", naziv=" + colNaziv + ", kolicina=" + colKolicina + ", komitent=" + colKomitent +
                    ", neto=" + colNeto + "}";
        }
    }
}