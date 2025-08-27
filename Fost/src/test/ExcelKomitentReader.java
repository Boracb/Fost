package test;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import model.KomitentInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Klasa za čitanje Excel (.xlsx) datoteke s komitentima i spremanje u bazu.
 * Traži stupce po nazivu iz zaglavlja:
 *  - "komitentOpis"
 *  - "trgovackiPredstavnik"
 */
public class ExcelKomitentReader {

    private static final SimpleDateFormat DATE_FMT_DOTS = new SimpleDateFormat("dd.MM.yyyy");
    private final String excelFilePath;

    public ExcelKomitentReader(String excelFilePath) {
        this.excelFilePath = excelFilePath;
    }

    /**
     * Čita Excel datoteku i vraća listu KomitentInfo objekata.
     */
    public List<KomitentInfo> readData() throws IOException {
        List<KomitentInfo> lista = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(new File(excelFilePath));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            if (!rowIterator.hasNext()) return lista; // prazan sheet

            // Čitanje header reda
            Row headerRow = rowIterator.next();
            int colKomitent = -1;
            int colPredstavnik = -1;

            for (Cell cell : headerRow) {
                String headerName = getCellString(cell);
                if ("komitentOpis".equalsIgnoreCase(headerName)) {
                    colKomitent = cell.getColumnIndex();
                } else if ("trgovackiPredstavnik".equalsIgnoreCase(headerName)) {
                    colPredstavnik = cell.getColumnIndex();
                }
            }

            if (colKomitent == -1 || colPredstavnik == -1) {
                throw new IllegalArgumentException("Excel ne sadrži očekivane kolone");
            }

            // Čitanje podataka
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                String komitentOpis = getCellString(row.getCell(colKomitent));
                String trgovackiPredstavnik = getCellString(row.getCell(colPredstavnik));

                if (!komitentOpis.isBlank() || !trgovackiPredstavnik.isBlank()) {
                    lista.add(new KomitentInfo(komitentOpis, trgovackiPredstavnik));
                }
            }
        }

        return lista;
    }

    /**
     * Sprema listu komitenata u bazu podataka.
     */
    public void saveToDatabase(Connection conn, List<KomitentInfo> lista) throws SQLException {
        String sql = "INSERT INTO komitenti (komitentOpis, trgovackiPredstavnik) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (KomitentInfo ki : lista) {
                ps.setString(1, ki.getKomitentOpis());
                ps.setString(2, ki.getTrgovackiPredstavnik());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ===== Pomoćne metode =====

    private static String getCellString(Cell cell) {
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    private static Integer safeIntegerFromCell(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
                return (int) Math.round(cell.getNumericCellValue());
            }
            if (cell.getCellType() == CellType.STRING) {
                String s = cell.getStringCellValue().trim();
                return s.isEmpty() ? null : Integer.parseInt(s);
            }
            if (cell.getCellType() == CellType.FORMULA) {
                FormulaEvaluator ev = cell.getSheet()
                        .getWorkbook()
                        .getCreationHelper()
                        .createFormulaEvaluator();
                CellValue cv = ev.evaluate(cell);
                if (cv.getCellType() == CellType.NUMERIC)
                    return (int) Math.round(cv.getNumberValue());
                if (cv.getCellType() == CellType.STRING) {
                    String s = cv.getStringValue().trim();
                    return s.isEmpty() ? null : Integer.parseInt(s);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static double[] parseDoublePair(String s) {
        if (s == null) return new double[]{0, 0};
        String[] p = s.split("/");
        if (p.length != 2) return new double[]{0, 0};
        return new double[]{parseSafe(p[0]), parseSafe(p[1])};
    }

    private static double parseSafe(String s) {
        if (s == null) return 0;
        s = s.trim().replace(',', '.');
        if (s.isEmpty()) return 0;
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String stripZeros(double v) {
        String str = Double.toString(v);
        return str.endsWith(".0") ? str.substring(0, str.length() - 2) : str;
    }

    @SuppressWarnings("deprecation")
    private static Double round(double v, int p) {
        return BigDecimal.valueOf(v)
                .setScale(p, BigDecimal.ROUND_HALF_UP)
                .doubleValue();
    }
}
