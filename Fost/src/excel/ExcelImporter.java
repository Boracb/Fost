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
import java.math.RoundingMode;
import java.text.SimpleDateFormat;

/**
 * Klasa za uvoz podataka iz Excel (.xlsx) datoteke u Swing DefaultTableModel.
 * Koristi Apache POI biblioteku za čitanje Excel datoteke.
 * Podržava različite tipove ćelija (string, numeric, date, formula).
 * Izračunava izvedene vrijednosti na temelju uvezenih podataka.
 *
 * Pravilo uvoza: nazivRobe (kolona 3) mora biti NE-prazan i mora sadržavati znak '/'.
 * komitentOpis (kolona 2) može biti prazan.
 * Redovi koji ne zadovoljavaju uvjete se preskaču.
 */
public class ExcelImporter {

    private static final SimpleDateFormat DATE_FMT_DOTS = new SimpleDateFormat("dd.MM.yyyy");
    private static final String[] DJELATNICI = {"", "Marko", "Ivana", "Petra", "Boris", "Ana"};
//    // Indeksi posebnih kolona (0-based)
    public static void importFromExcel(DefaultTableModel model) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Odaberi Excel datoteku");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel Workbook (*.xlsx)", "xlsx"));
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        int importedCount = 0;

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            model.setRowCount(0);

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                // Read ključna polja za odluku: komitentOpis i nazivRobe
                String komitentOpis = getCellString(row, 2);
                String nazivRobe = getCellString(row, 3);

                komitentOpis = komitentOpis == null ? "" : komitentOpis.trim();
                nazivRobe = nazivRobe == null ? "" : nazivRobe.trim();

                // New rule: nazivRobe must be non-empty AND must contain '/'
                if (nazivRobe.isEmpty()) {
                    // skip: naziv artikla obavezan
                    // System.out.println("ExcelImporter: preskočen red " + r + " jer nazivRobe je prazan.");
                    continue;
                }
                if (nazivRobe.indexOf('/') < 0) {
                    // skip: naziv must contain '/'
                    // System.out.println("ExcelImporter: preskočen red " + r + " jer nazivRobe ne sadrži '/': " + nazivRobe);
                    continue;
                }

                Object[] data = new Object[16];

                data[0]  = getCellDateDots(row, 0);              // datumNarudzbe
                data[1]  = getCellDateDots(row, 1);              // predDatumIsporuke
                data[2]  = komitentOpis;                         // komitentOpis (može biti prazno)
                data[3]  = nazivRobe;                            // nazivRobe (obavezan, s '/')
                data[4]  = safeDoubleFromCell(row.getCell(4));   // netoVrijednost
                data[5]  = safeIntegerFromCell(row.getCell(5));  // kom
                data[6]  = getCellString(row, 6);                // status

                // -- POPRAVLJENA LOGIKA ZA DJELATNIK --
                Cell djelatnikCell = row.getCell(7);
                String djelatnikName = "";
                if (djelatnikCell != null) {
                    if (djelatnikCell.getCellType() == CellType.NUMERIC) {
                        int idx = (int) Math.round(djelatnikCell.getNumericCellValue());
                        djelatnikName = (idx >= 0 && idx < DJELATNICI.length) ? DJELATNICI[idx] : "";
                    } else {
                        djelatnikName = getCellString(row, 7);
                    }
                }
                data[7] = djelatnikName;

                // mm, m, tisucl, m2
                data[8]  = null;
                data[9]  = null;
                data[10] = null;
                data[11] = null;

                // startTime, endTime, duration
                data[12] = "";
                data[13] = "";
                data[14] = "";

                // trgovackiPredstavnik
                data[15] = getCellString(row, 15);

                model.addRow(data);
                importedCount++;
            }

            // Izračun izvedenih vrijednosti (kao prije)
            for (int r = 0; r < model.getRowCount(); r++) {
                String nazivRobe = model.getValueAt(r, 3) != null ? model.getValueAt(r, 3).toString() : "";
                double[] pair = parseDoublePair(nazivRobe);
                Double mm = pair[0] == 0.0 ? null : round(pair[0], 3);
                Double mVal = pair[1] == 0.0 ? null : round(pair[1], 3);
                Double tisucl = (mm == null || mVal == null) ? null : round((mm / 1000.0) * mVal, 3);
                Number komNum = null;
                try {
                    Object komObj = model.getValueAt(r, 5);
                    komNum = komObj instanceof Number ? (Number) komObj : (komObj == null || komObj.toString().trim().isEmpty() ? null : Double.parseDouble(komObj.toString()));
                } catch (Exception ignored) {}
                double kom = komNum == null ? 0.0 : komNum.doubleValue();
                Double m2 = (tisucl == null || kom == 0.0) ? null : round(tisucl * kom, 3);

                model.setValueAt(mm, r, 8);
                model.setValueAt(mVal, r, 9);
                model.setValueAt(tisucl, r, 10);
                model.setValueAt(m2, r, 11);
            }

            if (importedCount == 0) {
                JOptionPane.showMessageDialog(null, "Nije uvezen niti jedan redak: nema valjanih naziva artikala (naziv mora biti ne-prazan i sadržavati '/').", "Uvoz završen", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(null, "Uvezeno redaka: " + importedCount, "Uvoz završen", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Greška pri uvozu:\n" + ex.getMessage(),
                    "Greška", JOptionPane.ERROR_MESSAGE);
        }
    }
    // ====== pomoćne metode ======
    private static String getCellString(Row row, int c) {
        Cell cell = row.getCell(c);
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return DATE_FMT_DOTS.format(cell.getDateCellValue());
                    }
                    return stripTrailingZeros(cell.getNumericCellValue());
                case BOOLEAN:
                    return Boolean.toString(cell.getBooleanCellValue());
                case FORMULA:
                    FormulaEvaluator ev = row.getSheet().getWorkbook()
                                              .getCreationHelper()
                                              .createFormulaEvaluator();
                    CellValue cv = ev.evaluate(cell);
                    if (cv == null) return "";
                    switch (cv.getCellType()) {
                        case STRING:  return cv.getStringValue().trim();
                        case NUMERIC: return stripTrailingZeros(cv.getNumberValue());
                        case BOOLEAN: return Boolean.toString(cv.getBooleanValue());
                    }
                    break;
            }
        } catch (Exception ignored) {}
        return cell.toString().trim();
    }
    private static String getCellDateDots(Row row, int idx) {
        Cell c = row.getCell(idx);
        if (c != null && c.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(c)) {
            return DATE_FMT_DOTS.format(c.getDateCellValue());
        }
        return c == null ? "" : c.toString().trim();
    }
    private static Double safeDoubleFromCell(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
                return cell.getNumericCellValue();
            }
            if (cell.getCellType() == CellType.STRING) {
                String s = cell.getStringCellValue().trim().replace(',', '.');
                return s.isEmpty() ? null : Double.parseDouble(s);
            }
            if (cell.getCellType() == CellType.FORMULA) {
                FormulaEvaluator ev = cell.getSheet().getWorkbook()
                                          .getCreationHelper().createFormulaEvaluator();
                CellValue cv = ev.evaluate(cell);
                if (cv == null) return null;
                if (cv.getCellType() == CellType.NUMERIC) return cv.getNumberValue();
                if (cv.getCellType() == CellType.STRING) {
                    String s = cv.getStringValue().trim().replace(',', '.');
                    return s.isEmpty() ? null : Double.parseDouble(s);
                }
            }
        } catch (Exception ignored) {}
        return null;
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
                FormulaEvaluator ev = cell.getSheet().getWorkbook()
                                          .getCreationHelper().createFormulaEvaluator();
                CellValue cv = ev.evaluate(cell);
                if (cv == null) return null;
                if (cv.getCellType() == CellType.NUMERIC) return (int) Math.round(cv.getNumberValue());
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
        return new double[]{ parseSafe(p[0]), parseSafe(p[1]) };
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
    private static String stripTrailingZeros(double v) {
        String str = Double.toString(v);
        return str.endsWith(".0") ? str.substring(0, str.length() - 2) : str;
    }
    private static Double round(double v, int p) {
        return BigDecimal.valueOf(v)
                         .setScale(p, RoundingMode.HALF_UP)
                         .doubleValue();
    }
}