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


//* Klasa za uvoz podataka iz Excel (.xlsx) datoteke u Swing DefaultTableModel.
//* Koristi Apache POI biblioteku za čitanje Excel datoteke.
//* Podržava različite tipove ćelija (string, numeric, date, formula).
//* Izračunava izvedene vrijednosti na temelju uvezenih podataka.
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
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            model.setRowCount(0);

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Object[] data = new Object[16];

                data[0]  = getCellDateDots(row, 0);              // datumNarudzbe
                data[1]  = getCellDateDots(row, 1);              // predDatumIsporuke
                data[2]  = getCellString(row, 2);                // komitentOpis
                data[3]  = getCellString(row, 3);                // nazivRobe
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
            }

            // Izračun izvedenih vrijednosti
            // mm/m = iz kolone "nazivRobe" (format "mm/m")
            // tisucl = (mm / 1000) * m
            // m2 = tisucl * kom
            // Zaokruživanje na 3 decimale
            // Postavlja se u kolone 8, 9, 10, 11
            // Indeksi kolona: 3=nazivRobe, 5=kom, 8=mm, 9=m, 10=tisucl, 11=m2
            // Primjer: nazivRobe="50/20", kom=3 → mm=50, m=20, tisucl=1.0, m2=3.0
            // Ako je nazivRobe=""/neispravan format → mm=null, m=null, tisucl=null, m2=null
            // Ako je kom=null/0 → m2=null
            // Ako je mm=0 ili m=0 → tisucl=null
            // Ako je tisucl=0 ili kom=0 → m2=null
            
            for (int r = 0; r < model.getRowCount(); r++) {
                String nazivRobe = model.getValueAt(r, 3) != null ? model.getValueAt(r, 3).toString() : "";
                double[] pair = parseDoublePair(nazivRobe);
                Double mm = pair[0] == 0.0 ? null : round(pair[0], 3);
                Double mVal = pair[1] == 0.0 ? null : round(pair[1], 3);
                Double tisucl = (mm == null || mVal == null) ? null : round((mm / 1000.0) * mVal, 3);
                Number komNum = (Number) model.getValueAt(r, 5);
                double kom = komNum == null ? 0.0 : komNum.doubleValue();
                Double m2 = (tisucl == null || kom == 0.0) ? null : round(tisucl * kom, 3);

                model.setValueAt(mm, r, 8);
                model.setValueAt(mVal, r, 9);
                model.setValueAt(tisucl, r, 10);
                model.setValueAt(m2, r, 11);
            }

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Greška pri uvozu:\n" + ex.getMessage(),
                    "Greška", JOptionPane.ERROR_MESSAGE);
        }
    }
    // ====== pomoćne metode ======
//    // Dohvaća sadržaj ćelije kao String, bez obzira na tip ćelije
    // Ako je ćelija prazna/NULL, vraća prazan String.
    // Za datume koristi format "dd.MM.yyyy".
    // Za numeričke vrijednosti uklanja nepotrebne decimale (npr. 10.0 → "10").
    // Za formule evaluira formulu i vraća rezultat kao String.
    // Ako evaluacija ne uspije, vraća prazan String.
    // Ako je tip ćelije nepoznat, koristi cell.toString().
    // U slučaju greške vraća prazan String.
    // Indeksi kolona: 0=datumNarudzbe, 1=predDatumIsporuke, 2=komitentOpis,
    // 3=nazivRobe, 4=netoVrijednost, 5=kom, 6=status, 7=djelatnik
    // 15=trgovackiPredstavnik
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
//    // Dohvaća sadržaj ćelije kao String, ali samo ako je ćelija datum.
    private static String getCellDateDots(Row row, int idx) {
        Cell c = row.getCell(idx);
        if (c != null && c.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(c)) {
            return DATE_FMT_DOTS.format(c.getDateCellValue());
        }
        return c == null ? "" : c.toString().trim();
    }
//    // Sigurno dohvaća Double iz ćelije.
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
 //   // Sigurno dohvaća Integer iz ćelije.
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
//    // Parsira string u obliku "num1/num2" u niz od dva double broja.
    private static double[] parseDoublePair(String s) {
        if (s == null) return new double[]{0, 0};
        String[] p = s.split("/");
        if (p.length != 2) return new double[]{0, 0};
        return new double[]{ parseSafe(p[0]), parseSafe(p[1]) };
    }
//    // Sigurno parsira string u double.
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
//    // Uklanja nepotrebne decimale iz double vrijednosti (npr. 10.0 → "10").
    private static String stripTrailingZeros(double v) {
        String str = Double.toString(v);
        return str.endsWith(".0") ? str.substring(0, str.length() - 2) : str;
    }
//    // Zaokružuje double na p decimala.
    private static Double round(double v, int p) {
        return BigDecimal.valueOf(v)
                         .setScale(p, RoundingMode.HALF_UP)
                         .doubleValue();
    }
}
