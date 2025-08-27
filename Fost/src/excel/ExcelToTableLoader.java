package excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Klasa za uvoz Excel (.xlsx) datoteka u DefaultTableModel.
 * Omogućuje učitavanje podataka i izračun izvedenih vrijednosti (mm, m, tisucl, m2).
 */
// * - Podržava različite formate ćelija (string, broj, datum, formula).
public class ExcelToTableLoader {
// Stil za datume "dd.MM.yyyy"
    private static final SimpleDateFormat DATE_FMT_DOTS = new SimpleDateFormat("dd.MM.yyyy");

    /**
     * Učitava podatke iz Excel datoteke u zadani DefaultTableModel.
     * Izvedene kolone (mm, m, tisucl, m2) ostaju prazne do ručnog izračuna.
     */
    // Indeksi posebnih kolona (0-based)
    public static void importExcelToTable(DefaultTableModel model) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Odaberi Excel datoteku");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel Workbook (*.xlsx)", "xlsx"));
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            model.setRowCount(0);

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Object[] data = new Object[15];
                data[0]  = getCellDateDots(row, 0);
                data[1]  = getCellDateDots(row, 1);
                data[2]  = getCellString(row, 2);
                data[3]  = getCellString(row, 3);
                data[4]  = safeDoubleFromCell(row.getCell(4));
                data[5]  = safeIntegerFromCell(row.getCell(5));
                data[6]  = getCellString(row, 6);
                data[7]  = getCellString(row, 7);

                data[8]  = null;
                data[9]  = null;
                data[10] = null;
                data[11] = null;

                data[12] = getCellString(row, 12);
                data[13] = getCellString(row, 13);
                data[14] = getCellString(row, 14);

                model.addRow(data);
            }

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Greška pri uvozu:\n" + ex.getMessage(),
                    "Greška", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Računa izvedene vrijednosti (mm, m, tisucl, m2) na temelju naziva robe i količine.
     */
    // Indeksi posebnih kolona (0-based)
    // Naziv robe (kolona 3) sadrži "mm/m" format
    // Količina (kolona 5) je broj komada
    //	 Izvedene kolone: mm (8), m (9), tisucl (10), m2 (11)
    // Ako je mm ili m nepoznato, postavlja null
    // Ako je količina 0 ili nepoznata, m2 postavlja null
    // Zaokružuje na 3 decimale
    public static void calculateDerived(DefaultTableModel model) {
        for (int r = 0; r < model.getRowCount(); r++) {
            String nazivRobe = (String) model.getValueAt(r, 3);
            double[] pair = parseDoublePair(nazivRobe);

            Double mm   = pair[0] == 0.0 ? null : round(pair[0], 3);
            Double mVal = pair[1] == 0.0 ? null : round(pair[1], 3);

            Double tisucl = (mm == null || mVal == null) ? null : round((mm / 1000.0) * mVal, 3);
            Number komNum = (Number) model.getValueAt(r, 5);
            double kom    = komNum == null ? 0.0 : komNum.doubleValue();
            Double m2     = (tisucl == null || kom == 0.0) ? null : round(tisucl * kom, 3);

            model.setValueAt(mm,     r, 8);
            model.setValueAt(mVal,   r, 9);
            model.setValueAt(tisucl, r, 10);
            model.setValueAt(m2,     r, 11);
        }
    }

    // ====== POMOĆNE METODE ZA ČITANJE ĆELIJA ======
//* Dohvaća sadržaj ćelije kao String, bez obzira na tip ćelije.
    private static String getCellString(Row row, int c) {
        Cell cell = row.getCell(c);
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date d = cell.getDateCellValue();
                    String fmt = cell.getCellStyle().getDataFormatString();
                    boolean hasTime = fmt != null && fmt.toLowerCase().contains("h");
                    String pattern = hasTime ? "dd.MM.yyyy HH:mm" : "dd.MM.yyyy";
                    return new SimpleDateFormat(pattern).format(d);
                }
                return stripTrailingZeros(cell.getNumericCellValue());
            case BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    FormulaEvaluator ev = row.getSheet().getWorkbook()
                            .getCreationHelper().createFormulaEvaluator();
                    CellValue cv = ev.evaluate(cell);
                    if (cv == null) return "";
                    switch (cv.getCellType()) {
                        case STRING:  return cv.getStringValue().trim();
                        case NUMERIC:
                            if (DateUtil.isCellDateFormatted(cell)) {
                                Date d = DateUtil.getJavaDate(cv.getNumberValue());
                                String fmt2 = cell.getCellStyle().getDataFormatString();
                                boolean hasTime2 = fmt2 != null && fmt2.toLowerCase().contains("h");
                                String pattern2 = hasTime2 ? "dd.MM.yyyy HH:mm" : "dd.MM.yyyy";
                                return new SimpleDateFormat(pattern2).format(d);
                            }
                            return stripTrailingZeros(cv.getNumberValue());
                        case BOOLEAN: return Boolean.toString(cv.getBooleanValue());
                    }
                } catch (Exception e) {
                    return cell.toString().trim();
                }
            default:
                return cell.toString().trim();
        }
    }
//* Dohvaća sadržaj ćelije kao String, ako je datum formatira kao "dd.MM.yyyy".
    private static String getCellDateDots(Row row, int c) {
        Cell cell = row.getCell(c);
        if (cell != null && cell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return DATE_FMT_DOTS.format(cell.getDateCellValue());
        }
        return cell == null ? "" : cell.toString().trim();
    }
//* Sigurno dohvaća Double vrijednost iz ćelije.
    private static Double safeDoubleFromCell(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
                return cell.getNumericCellValue();
            }
            if (cell.getCellType() == CellType.STRING) {
                String txt = cell.getStringCellValue().trim().replace(',', '.');
                return txt.isEmpty() ? null : Double.parseDouble(txt);
            }
            if (cell.getCellType() == CellType.FORMULA) {
                FormulaEvaluator ev = cell.getSheet().getWorkbook()
                        .getCreationHelper().createFormulaEvaluator();
                CellValue cv = ev.evaluate(cell);
                if (cv.getCellType() == CellType.NUMERIC) return cv.getNumberValue();
                if (cv.getCellType() == CellType.STRING) {
                    String txt = cv.getStringValue().trim().replace(',', '.');
                    return txt.isEmpty() ? null : Double.parseDouble(txt);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
    /**
     * Sigurno dohvaća Integer vrijednost iz ćelije.
     */
    // Ako je ćelija decimalni broj, zaokružuje na najbliži cijeli.
    private static Integer safeIntegerFromCell(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC
                    && !DateUtil.isCellDateFormatted(cell)) {
                return (int) Math.round(cell.getNumericCellValue());
            }
            if (cell.getCellType() == CellType.STRING) {
                String txt = cell.getStringCellValue().trim();
                if (txt.isEmpty()) return null;
                return Integer.parseInt(txt);
            }
            if (cell.getCellType() == CellType.FORMULA) {
                FormulaEvaluator ev = cell.getSheet().getWorkbook()
                        .getCreationHelper().createFormulaEvaluator();
                CellValue cv = ev.evaluate(cell);
                if (cv.getCellType() == CellType.NUMERIC) return (int) Math.round(cv.getNumberValue());
                if (cv.getCellType() == CellType.STRING) {
                    String txt = cv.getStringValue().trim();
                    if (txt.isEmpty()) return null;
                    return Integer.parseInt(txt);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Parsira string oblika "vrijednost1/vrijednost2" u double par.
     */
    // Ako je format neispravan, vraća [0, 0].
    private static double[] parseDoublePair(String s) {
        if (s == null) return new double[]{0, 0};
        String[] parts = s.split("/");
        if (parts.length != 2) return new double[]{0, 0};
        return new double[]{ parseSafe(parts[0]), parseSafe(parts[1]) };
    }

    /**
     * Sigurno parsira string u double.
     */
    // Ako je null, prazan ili neispravan format, vraća 0.0.
    private static double parseSafe(String s) {
        if (s == null) return 0.0;
        s = s.trim().replace(',', '.');
        if (s.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Uklanja ".0" s kraja ako broj nema decimale.
     */
    private static String stripTrailingZeros(double val) {
        String s = Double.toString(val);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /**
     * Zaokružuje broj na zadani broj decimala.
     */
    private static Double round(double v, int p) {
        return BigDecimal.valueOf(v)
                .setScale(p, BigDecimal.ROUND_HALF_UP)
                .doubleValue();
    }
}
