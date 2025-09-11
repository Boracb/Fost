package excel;

import model.StockRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.NumberToTextConverter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.*;

public final class ExcelStockImporter {

    private ExcelStockImporter() {}

    public static List<StockRow> importFile(File file) throws IOException {
        if (file == null) throw new IllegalArgumentException("Datoteka je null");
        if (!file.exists()) throw new IOException("Datoteka ne postoji: " + file.getAbsolutePath());

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) throw new IOException("Excel ne sadrži listove.");

            Iterator<Row> it = sheet.rowIterator();
            if (!it.hasNext()) return Collections.emptyList();

            Row header = it.next();
            Map<String, Integer> colIdx = mapHeaderIndexes(header);

            int idxSifra = getRequiredIndex(colIdx, "sifra");
            int idxNaziv = getRequiredIndex(colIdx, "nazivartikla");
            int idxJedMj = getRequiredIndex(colIdx, "jedmj");
            int idxKolicina = getRequiredIndex(colIdx, "kolicina");
            Integer idxNabCijena = colIdx.get("nabavnakcijena");
            Integer idxNabVrij = colIdx.get("nabavnakvrijednost");

            List<StockRow> out = new ArrayList<>();
            while (it.hasNext()) {
                Row r = it.next();
                if (isRowEmpty(r)) continue;

                String sifra = getString(r.getCell(idxSifra));
                String naziv = getString(r.getCell(idxNaziv));
                String jm = getString(r.getCell(idxJedMj));
                double kol = getDouble(r.getCell(idxKolicina));
                double nc = idxNabCijena == null ? 0.0 : getDouble(r.getCell(idxNabCijena));
                double nv = idxNabVrij == null ? 0.0 : getDouble(r.getCell(idxNabVrij));

                if ((sifra == null || sifra.isBlank()) && (naziv == null || naziv.isBlank())) continue;

                out.add(new StockRow(
                        nullSafe(sifra),
                        nullSafe(naziv),
                        nullSafe(jm),
                        kol,
                        nc,
                        nv
                ));
            }
            return out;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Neuspješan uvoz Excela: " + e.getMessage(), e);
        }
    }

    private static boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell c = row.getCell(i);
            if (c == null) continue;
            if (c.getCellType() != CellType.BLANK) {
                if (c.getCellType() == CellType.STRING && !c.getStringCellValue().trim().isEmpty()) return false;
                if (c.getCellType() == CellType.NUMERIC) return false;
            }
        }
        return true;
    }

    private static String nullSafe(String s) { return s == null ? "" : s.trim(); }

    private static Map<String, Integer> mapHeaderIndexes(Row header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = header.getFirstCellNum(); i < header.getLastCellNum(); i++) {
            Cell c = header.getCell(i);
            if (c == null) continue;
            String raw = c.getCellType() == CellType.STRING ? c.getStringCellValue() : NumberToTextConverter.toText(getDouble(c));
            String norm = normalizeHeader(raw);

            String key = switch (norm) {
                case "sifra", "šifra" -> "sifra";
                case "nazivartikla", "naziv", "nazivrobe" -> "nazivartikla";
                case "jedmj", "jedinicamjere", "jm" -> "jedmj";
                case "kolicina", "kol" -> "kolicina";
                case "nabavnacijena", "nc", "cijena" -> "nabavnakcijena";
                case "nabavnavrijednost", "nv", "vrijednost" -> "nabavnakvrijednost";
                default -> null;
            };
            if (key != null && !map.containsKey(key)) {
                map.put(key, i);
            }
        }
        return map;
    }

    private static int getRequiredIndex(Map<String, Integer> map, String key) {
        Integer idx = map.get(key);
        if (idx == null) throw new IllegalArgumentException("Nedostaje obavezna kolona u Excelu: " + key);
        return idx;
    }

    private static String normalizeHeader(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        n = n.toLowerCase(Locale.ROOT)
             .replaceAll("[^a-z0-9]", "");
        return n;
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
                    yield cv != null ? (cv.getCellType() == CellType.NUMERIC
                            ? NumberToTextConverter.toText(cv.getNumberValue())
                            : cv.getStringValue()) : "";
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
                        yield cv != null && cv.getCellType() == CellType.NUMERIC ? cv.getNumberValue() : parseDouble(cell.getStringCellValue());
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
        if (v.contains(",") && v.contains(".")) {
            v = v.replace(",", "");
        } else {
            v = v.replace(",", ".");
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}