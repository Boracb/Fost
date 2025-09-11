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

import static org.apache.poi.ss.usermodel.DateUtil.getJavaDate;
import static org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted;

/**
 * Uvoz potrošnje u proizvodnji (.xlsx/.xls).
 * Koristimo: Datum | (Šifra - OPCIONALNO) | Naziv robe | Količina
 * Ignoriramo ostale kolone (Tip dok., Br. dok., Komitent, vrijednosti, marže, PDV...).
 * - pretražuje sve listove i automatski detektira zaglavlje u prvih 50 redova
 */
public final class ExcelProductionImporter {

    private ExcelProductionImporter() {}

    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    public static List<SalesRow> importFile(File file) throws IOException {
        if (file == null) throw new IllegalArgumentException("Datoteka je null");
        if (!file.exists()) throw new IOException("Datoteka ne postoji: " + file.getAbsolutePath());

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {

            List<SalesRow> result = new ArrayList<>();

            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                if (sheet == null) continue;

                HeaderInfo hi = findHeaderInSheet(sheet);
                if (hi == null) continue;

                int lastRow = sheet.getLastRowNum();
                for (int r = hi.headerRowIndex + 1; r <= lastRow; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null || isRowEmpty(row)) continue;

                    LocalDate datum = parseDate(getCell(row, hi.idxDatum));
                    String sifra = hi.idxSifra != null ? getString(getCell(row, hi.idxSifra)) : "";
                    String naziv = getString(getCell(row, hi.idxNaziv));
                    double kolicina = getDouble(getCell(row, hi.idxKolicina));

                    if (datum == null) continue;
                    if ((isBlank(sifra)) && (isBlank(naziv))) continue;

                    result.add(new SalesRow(datum, nullSafe(sifra), nullSafe(naziv), naziv, kolicina, kolicina));
                }

                if (!result.isEmpty()) break;
            }

            return result;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Neuspješan uvoz Excela (proizvodnja): " + e.getMessage(), e);
        }
    }

    private static class HeaderInfo {
        int headerRowIndex;
        int idxDatum;
        Integer idxSifra; // optional
        int idxNaziv;
        int idxKolicina;
    }

    private static HeaderInfo findHeaderInSheet(Sheet sheet) {
        int scanRows = Math.min(50, sheet.getLastRowNum());
        for (int r = sheet.getFirstRowNum(); r <= scanRows; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, Integer> map = mapHeaderIndexes(row);
            if (map.containsKey("datum") && map.containsKey("naziv") && map.containsKey("kolicina")) {
                Integer idxDatum = map.get("datum");
                Integer idxNaziv = map.get("naziv");
                Integer idxKolicina = map.get("kolicina");
                Integer idxSifra = map.get("sifra");
                if (idxDatum != null && idxNaziv != null && idxKolicina != null) {
                    HeaderInfo hi = new HeaderInfo();
                    hi.headerRowIndex = r;
                    hi.idxDatum = idxDatum;
                    hi.idxNaziv = idxNaziv;
                    hi.idxKolicina = idxKolicina;
                    hi.idxSifra = idxSifra;
                    return hi;
                }
            }
        }
        return null;
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

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
            if (c.getCellType() != CellType.BLANK) {
                if (c.getCellType() == CellType.STRING && !c.getStringCellValue().trim().isEmpty()) return false;
                if (c.getCellType() == CellType.NUMERIC) return false;
                if (c.getCellType() == CellType.BOOLEAN) return false;
                if (c.getCellType() == CellType.FORMULA) return false;
            }
        }
        return true;
    }

    private static Map<String, Integer> mapHeaderIndexes(Row header) {
        Map<String, Integer> map = new HashMap<>();
        short first = header.getFirstCellNum();
        short last = header.getLastCellNum();
        if (first < 0 || last < 0) return map;

        for (int i = first; i < last; i++) {
            Cell c = header.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (c == null) continue;
            String raw = c.getCellType() == CellType.STRING
                    ? c.getStringCellValue()
                    : NumberToTextConverter.toText(getDouble(c));
            String norm = normalizeHeader(raw);

            String key = switch (norm) {
                case "datum", "dat" -> "datum";
                case "sifra", "šifra", "sif", "sif.", "sifraartikla", "sifartikla", "šifraartikla",
                        "sifrarobe", "šifrarobe", "kod", "code", "itemcode", "productcode", "sku" -> "sifra";
                case "nazivrobe", "nazivartikla", "naziv", "opis", "nazivrobeopis" -> "naziv";
                case "kolicina", "kol" -> "kolicina";
                default -> null;
            };
            if (key != null && !map.containsKey(key)) map.put(key, i);
        }
        return map;
    }

    private static String normalizeHeader(String s) {
        if (s == null) return "";
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
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static double parseDouble(String s) {
        if (s == null) return 0.0;
        String v = s.trim();
        if (v.isEmpty()) return 0.0;
        v = v.replace(" ", "").replace("\u00A0", "");
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
                for (DateTimeFormatter f : DATE_FORMATS) {
                    try { return LocalDate.parse(s, f); } catch (Exception ignored) {}
                    int sp = s.indexOf(' ');
                    if (sp > 0) {
                        String onlyDate = s.substring(0, sp);
                        try { return LocalDate.parse(onlyDate, f); } catch (Exception ignored2) {}
                    }
                }
            }
            if (cell.getCellType() == CellType.FORMULA) {
                try {
                    FormulaEvaluator fe = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cv = fe.evaluate(cell);
                    if (cv != null && cv.getCellType() == CellType.NUMERIC) {
                        return getJavaDate(cv.getNumberValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    } else if (cv != null && cv.getCellType() == CellType.STRING) {
                        String s = cv.getStringValue().trim();
                        for (DateTimeFormatter f : DATE_FORMATS) {
                            try { return LocalDate.parse(s, f); } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }
}