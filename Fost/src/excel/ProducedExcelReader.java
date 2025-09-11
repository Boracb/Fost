package excel;

import model.ProducedExcelRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DateUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ProducedExcelReader {
    private ProducedExcelReader() {}

    private static final DateTimeFormatter[] DATE_PARSERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("d.M.yyyy[ H[:mm][:ss]]"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy[ H[:mm][:ss]]"),
            DateTimeFormatter.ofPattern("d/M/yyyy[ H[:mm][:ss]]"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy[ H[:mm][:ss]]"),
            DateTimeFormatter.ofPattern("yyyy-M-d['T'HH[:mm][:ss]]")
    };

    public static List<ProducedExcelRow> read(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) return Collections.emptyList();

            Map<String, Integer> colIndex = new HashMap<>();
            int headerRowIdx = findHeaderRow(sheet, colIndex);
            if (headerRowIdx < 0) return Collections.emptyList();

            List<ProducedExcelRow> rows = new ArrayList<>();
            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                LocalDateTime datum = readDateTime(row, colIndex.get("datum"));
                String komitent = readString(row, colIndex.get("komitent"));
                String naziv = readString(row, colIndex.get("naziv"));
                Integer kol = readInt(row, colIndex.get("kolicina"));
                BigDecimal neto = readBigDecimal(row, colIndex.get("neto"));

                if ((komitent == null || komitent.isBlank()) &&
                    (naziv == null || naziv.isBlank())) continue;

                if (kol == null) kol = 0;
                if (datum == null) datum = LocalDate.now().atStartOfDay();
                rows.add(new ProducedExcelRow(datum, komitent, naziv, kol, neto));
            }
            return rows;
        }
    }

    private static int findHeaderRow(Sheet sheet, Map<String, Integer> outIndexes) {
        for (int r = Math.max(sheet.getFirstRowNum(), 0); r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, Integer> m = tryMapHeader(row);
            if (m != null) {
                outIndexes.putAll(m);
                return r;
            }
        }
        return -1;
    }

    private static Map<String, Integer> tryMapHeader(Row row) {
        Map<String, Integer> map = new HashMap<>();
        short first = row.getFirstCellNum();
        short last = row.getLastCellNum();
        if (first < 0 || last < 0) return null;

        for (int c = first; c < last; c++) {
            Cell cell = row.getCell(c);
            String h = normalizeHeader(readCellAsString(cell));
            if (h.isEmpty()) continue;

            if (h.contains("datum")) map.putIfAbsent("datum", c);
            if ((h.contains("komitent") || h.contains("opis")) && (h.contains("sifra") || h.contains("šifra") || h.contains("opis"))) {
                map.putIfAbsent("komitent", c);
            }
            if (h.contains("naziv") && h.contains("rob")) map.putIfAbsent("naziv", c);
            if (h.contains("kolicina") || h.contains("količina")) map.putIfAbsent("kolicina", c);
            if ((h.contains("neto") || h.contains("netto")) && h.contains("vrijedn")) map.putIfAbsent("neto", c);
        }
        if (map.containsKey("komitent") && map.containsKey("naziv")) {
            return map;
        }
        return null;
    }

    private static String normalizeHeader(String s) {
        if (s == null) return "";
        s = s.toLowerCase(Locale.ROOT).trim();
        s = s.replaceAll("[^a-z0-9čćžšđ ]+", " ");
        s = s.replaceAll("\\s+", " ");
        return s;
    }

    private static String readString(Row row, Integer idx) {
        if (idx == null) return "";
        Cell cell = row.getCell(idx);
        String v = readCellAsString(cell);
        return v == null ? "" : v.trim();
    }

    private static Integer readInt(Row row, Integer idx) {
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) Math.round(cell.getNumericCellValue());
            }
            String s = readCellAsString(cell);
            if (s == null) return null;
            s = s.trim();
            if (s.isEmpty()) return null;
            s = s.replace(",", ".").replaceAll("[^0-9\\.-]", "");
            if (s.isEmpty()) return null;
            double d = Double.parseDouble(s);
            return (int) Math.round(d);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal readBigDecimal(Row row, Integer idx) {
        if (idx == null) return BigDecimal.ZERO;
        Cell cell = row.getCell(idx);
        if (cell == null) return BigDecimal.ZERO;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            String s = readCellAsString(cell);
            if (s == null) return BigDecimal.ZERO;
            s = s.trim();
            if (s.isEmpty()) return BigDecimal.ZERO;
            char dec = DecimalFormatSymbols.getInstance().getDecimalSeparator();
            s = s.replace(dec == ',' ? "." : ",", String.valueOf(dec));
            s = s.replaceAll("[^0-9\\,\\.\\-]", "");
            s = s.replace(",", ".");
            return new BigDecimal(s);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static LocalDateTime readDateTime(Row row, Integer idx) {
        if (idx == null) return null;
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                Date d = cell.getDateCellValue();
                return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()).withSecond(0).withNano(0);
            }
            String s = readCellAsString(cell);
            if (s == null) return null;
            s = s.trim();
            if (s.isEmpty()) return null;
            for (DateTimeFormatter f : DATE_PARSERS) {
                try {
                    try { return LocalDateTime.parse(s, f).withSecond(0).withNano(0); }
                    catch (Exception ignore) {
                        LocalDate ld = LocalDate.parse(s, f);
                        return ld.atStartOfDay();
                    }
                } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static String readCellAsString(Cell cell) {
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING: return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    }
                    double v = cell.getNumericCellValue();
                    if (Math.floor(v) == v) return String.valueOf((long) v);
                    return String.valueOf(v);
                case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try { return cell.getStringCellValue(); }
                    catch (IllegalStateException e) {
                        try { return String.valueOf(cell.getNumericCellValue()); }
                        catch (IllegalStateException ex) { return ""; }
                    }
                default: return "";
            }
        } catch (Exception e) {
            return "";
        }
    }
}