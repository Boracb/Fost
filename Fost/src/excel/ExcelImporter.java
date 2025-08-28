package excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import logic.DateUtils;
import logic.WorkingTimeCalculator;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.AbstractTableModel;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ExcelImporter koji:
 * - radi u pozadini (SwingWorker) da ne blokira UI,
 * - publish/process dodaje retke odmah u model dok se čita (redovi se pojavljuju odmah),
 * - podržava Excel sa headerom (mapira kolone) ili bez headera (fiksni indeksi),
 * - izračunava mm/m/tisucl/m2/duration/predPlan i upisuje finalni red u model,
 * - opcionalni callback onComplete poziva se na EDT nakon dovršetka (npr. recomputeAllRows).
 */
public class ExcelImporter {

    private static final SimpleDateFormat DATE_FMT_DOTS = new SimpleDateFormat("dd.MM.yyyy");
    private static final DateTimeFormatter OUT_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final LocalTime PLAN_WORK_START = LocalTime.of(7, 0);
    private static final LocalTime PLAN_WORK_END = LocalTime.of(15, 0);
    private static final double DEFAULT_M2_PER_HOUR = 10.0;

    public static void importFromExcel(DefaultTableModel model) {
        importFromExcel(model, null);
    }

    /**
     * Pokreće uvoz u pozadini. Ako želiš da batch preracun predPlanova bude izveden
     * samo nakon završetka uvoza, proslijedi callback koji poziva recomputeAllRows
     * ili sličnu metodu u UI (pozvat će se na EDT).
     */
    public static void importFromExcel(DefaultTableModel model, Runnable onComplete) {
        if (model == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Odaberi Excel datoteku");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel Workbook (*.xlsx)", "xlsx"));
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (file == null || !file.exists()) {
            JOptionPane.showMessageDialog(null, "Odabrana datoteka ne postoji.", "Greška", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SwingWorker<Void, Object[]> worker = new SwingWorker<>() {
            int imported = 0;
            int skipped = 0;
            String errorMessage = null;

            @Override
            protected Void doInBackground() {
                try (FileInputStream fis = new FileInputStream(file);
                     Workbook wb = new XSSFWorkbook(fis)) {

                    Sheet sheet = wb.getSheetAt(0);
                    if (sheet == null) return null;

                    // Decide if first row is header (heuristic: any string cell -> header)
                    Row first = sheet.getRow(0);
                    boolean hasHeader = false;
                    Map<String,Integer> headerMap = Collections.emptyMap();
                    if (first != null) {
                        for (Cell c : first) {
                            if (c != null && c.getCellType() == CellType.STRING) { hasHeader = true; break; }
                        }
                        if (hasHeader) headerMap = buildHeaderMap(first);
                    }

                    int startRow = 1; // as in your examples data start from second row
                    int lastRow = sheet.getLastRowNum();

                    for (int r = startRow; r <= lastRow; r++) {
                        if (isCancelled()) break;
                        Row row = sheet.getRow(r);
                        if (row == null) continue;

                        try {
                            // read core fields (header or fixed indices)
                            String komitentOpis = hasHeader ? getByHeader(row, headerMap, "komitentopis", 2) : getCellString(row, 2);
                            String nazivRobe = hasHeader ? getByHeader(row, headerMap, "nazivrobe", 3) : getCellString(row, 3);

                            komitentOpis = komitentOpis == null ? "" : komitentOpis.trim();
                            nazivRobe = nazivRobe == null ? "" : nazivRobe.trim();

                            // skip invalid rows
                            if (nazivRobe.isEmpty() || !nazivRobe.contains("/")) { skipped++; continue; }

                            Object[] data = new Object[17];

                            // datumNarudzbe
                            String datumNar = hasHeader ? getByHeader(row, headerMap, "datumnarudzbe", 0) : getCellDateDots(row, 0);
                            if (datumNar == null || datumNar.isBlank()) datumNar = DATE_FMT_DOTS.format(new Date());
                            data[0] = datumNar;

                            // predDatumIsporuke
                            String predDatum = hasHeader ? getByHeader(row, headerMap, "preddatumisporuke", 1) : getCellDateDots(row, 1);
                            data[1] = predDatum == null ? "" : predDatum;

                            data[2] = komitentOpis;
                            data[3] = nazivRobe;

                            // neto / kom
                            Double neto = hasHeader ? safeDoubleFromRowByHeader(row, headerMap, "netovrijednost", 4) : safeDoubleFromCell(row.getCell(4));
                            Integer komInt = hasHeader ? safeIntegerFromRowByHeader(row, headerMap, "kom", 5) : safeIntegerFromCell(row.getCell(5));
                            data[4] = neto;
                            data[5] = komInt;

                            data[6] = hasHeader ? getByHeader(row, headerMap, "status", 6) : getCellString(row, 6);
                            data[7] = hasHeader ? getByHeader(row, headerMap, "djelatnik", 7) : getCellString(row, 7);

                            // mm/m/tisucl/m2
                            Double mm = hasHeader ? safeDoubleFromRowByHeader(row, headerMap, "mm", 8) : null;
                            Double mVal = hasHeader ? safeDoubleFromRowByHeader(row, headerMap, "m", 9) : null;
                            Double tisucl = hasHeader ? safeDoubleFromRowByHeader(row, headerMap, "tisucl", 10) : null;
                            Double m2 = hasHeader ? safeDoubleFromRowByHeader(row, headerMap, "m2", 11) : null;

                            // fallback parse from nazivRobe if needed
                            if ((mm == null || mVal == null) && nazivRobe != null) {
                                double[] parsed = parseDoublePair(nazivRobe);
                                if (mm == null && parsed[0] != 0.0) mm = round(parsed[0], 3);
                                if (mVal == null && parsed[1] != 0.0) mVal = round(parsed[1], 3);
                            }
                            if (tisucl == null && mm != null && mVal != null) tisucl = round((mm / 1000.0) * mVal, 3);
                            double komDouble = komInt == null ? 0.0 : komInt.doubleValue();
                            if (m2 == null && tisucl != null && komDouble > 0.0) m2 = round(tisucl * komDouble, 3);

                            data[8] = mm;
                            data[9] = mVal;
                            data[10] = tisucl;
                            data[11] = m2;

                            // start / end / duration
                            String startStr = hasHeader ? getByHeader(row, headerMap, "starttime", 12) : getCellString(row, 12);
                            String endStr = hasHeader ? getByHeader(row, headerMap, "endtime", 13) : getCellString(row, 13);
                            data[12] = startStr == null ? "" : startStr;
                            data[13] = endStr == null ? "" : endStr;
                            if (startStr != null && !startStr.isBlank() && endStr != null && !endStr.isBlank()) {
                                try {
                                    LocalDateTime sdt = DateUtils.parse(startStr);
                                    LocalDateTime edt = DateUtils.parse(endStr);
                                    if (sdt != null && edt != null && !edt.isBefore(sdt)) {
                                        long minutes = WorkingTimeCalculator.calculateWorkingMinutes(sdt, edt);
                                        data[14] = minutes > 0 ? String.format("%02d:%02d", minutes / 60, minutes % 60) : "";
                                    } else data[14] = "";
                                } catch (Exception ex) { data[14] = ""; }
                            } else data[14] = "";

                            // predPlan (simple per-row calculation)
                            String predPlan = "";
                            if (m2 != null && m2 > 0.0) {
                                long minutesNeeded = (long) Math.ceil((m2 / DEFAULT_M2_PER_HOUR) * 60.0);
                                LocalDateTime cursor = null;
                                if (startStr != null && !startStr.isBlank()) {
                                    try { cursor = DateUtils.parse(startStr); } catch (Exception ignored) { cursor = null; }
                                }
                                if (cursor == null) cursor = LocalDateTime.of(LocalDate.now(), PLAN_WORK_START);
                                if (!cursor.toLocalTime().isBefore(PLAN_WORK_END)) cursor = LocalDateTime.of(cursor.toLocalDate().plusDays(1), PLAN_WORK_START);

                                long remaining = minutesNeeded;
                                LocalDateTime segCursor = cursor;
                                while (remaining > 0) {
                                    LocalDate d = segCursor.toLocalDate();
                                    if (WorkingTimeCalculator.isHolidayOrWeekend(d)) {
                                        segCursor = LocalDateTime.of(d.plusDays(1), PLAN_WORK_START);
                                        continue;
                                    }
                                    LocalDateTime dayStart = LocalDateTime.of(d, PLAN_WORK_START);
                                    LocalDateTime dayEnd = LocalDateTime.of(d, PLAN_WORK_END);
                                    LocalDateTime segStart = segCursor.isAfter(dayStart) ? segCursor : dayStart;
                                    LocalDateTime segEnd = dayEnd;
                                    long avail = segEnd.isAfter(segStart) ? java.time.temporal.ChronoUnit.MINUTES.between(segStart, segEnd) : 0;
                                    if (avail <= 0) { segCursor = LocalDateTime.of(d.plusDays(1), PLAN_WORK_START); continue; }
                                    if (remaining <= avail) {
                                        LocalDateTime finish = segStart.plusMinutes(remaining);
                                        predPlan = finish.toLocalDate().format(OUT_DATE_FMT);
                                        break;
                                    } else { remaining -= avail; segCursor = LocalDateTime.of(d.plusDays(1), PLAN_WORK_START); }
                                }
                            }
                            data[15] = predPlan;

                            // trgovacki predstavnik
                            String tp = hasHeader ? getByHeader(row, headerMap, "trgovackipredstavnik", 16) : getCellString(row, 15);
                            data[16] = tp == null ? "" : tp;

                            // publish the completed row so it appears immediately in JTable via process()
                            publish(data);
                            imported++;
                        } catch (Exception rowEx) {
                            skipped++;
                        }
                    }

                } catch (IOException ex) {
                    errorMessage = ex.getMessage();
                } catch (Exception ex) {
                    errorMessage = ex.getMessage();
                }
                return null;
            }

            @Override
            protected void process(List<Object[]> chunks) {
                // runs on EDT: add rows immediately
                for (Object[] arr : chunks) {
                    model.addRow(arr);
                }
                // fire one update
                ((AbstractTableModel) model).fireTableDataChanged();
            }

            @Override
            protected void done() {
                if (errorMessage != null) {
                    JOptionPane.showMessageDialog(null, "Greška pri uvozu:\n" + errorMessage, "Greška", JOptionPane.ERROR_MESSAGE);
                } else {
                    String msg = "Uvezeno redaka: " + imported + (skipped > 0 ? " (preskočeno: " + skipped + ")" : "");
                    JOptionPane.showMessageDialog(null, msg, "Uvoz dovršen", JOptionPane.INFORMATION_MESSAGE);
                }
                if (onComplete != null) {
                    try { onComplete.run(); } catch (Exception ignored) {}
                }
            }
        };

        worker.execute();
    }

    // ---- helper methods ----

    private static Map<String,Integer> buildHeaderMap(Row header) {
        Map<String,Integer> map = new HashMap<>();
        if (header == null) return map;
        for (int c = 0; c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c);
            if (cell == null) continue;
            String name = cell.toString().trim().toLowerCase().replaceAll("\\s+","").replaceAll("[^\\p{ASCII}]", "");
            if (!name.isEmpty()) map.put(name, c);
        }
        return map;
    }

    private static String getByHeader(Row row, Map<String,Integer> headerMap, String canonicalName, int fallbackIndex) {
        if (headerMap != null && !headerMap.isEmpty()) {
            String[] synonyms = new String[] { canonicalName, canonicalName.replaceAll("[^a-z0-9]","") };
            for (String s : synonyms) {
                if (s == null) continue;
                Integer idx = headerMap.get(s);
                if (idx != null) return getCellString(row, idx);
            }
        }
        return getCellString(row, fallbackIndex);
    }

    private static Double safeDoubleFromRowByHeader(Row row, Map<String,Integer> headerMap, String canonicalName, int fallbackIndex) {
        if (headerMap != null && headerMap.containsKey(canonicalName)) {
            Cell c = row.getCell(headerMap.get(canonicalName));
            return safeDoubleFromCell(c);
        }
        Cell fallback = row.getCell(fallbackIndex);
        return safeDoubleFromCell(fallback);
    }

    private static Integer safeIntegerFromRowByHeader(Row row, Map<String,Integer> headerMap, String canonicalName, int fallbackIndex) {
        if (headerMap != null && headerMap.containsKey(canonicalName)) {
            Cell c = row.getCell(headerMap.get(canonicalName));
            return safeIntegerFromCell(c);
        }
        Cell fallback = row.getCell(fallbackIndex);
        return safeIntegerFromCell(fallback);
    }

    private static String getCellString(Row row, int c) {
        Cell cell = row.getCell(c);
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING: return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        Date d = cell.getDateCellValue();
                        String fmt = cell.getCellStyle() != null ? cell.getCellStyle().getDataFormatString() : null;
                        boolean hasTime = fmt != null && fmt.toLowerCase().contains("h");
                        String pattern = hasTime ? "dd.MM.yyyy HH:mm" : "dd.MM.yyyy";
                        return new SimpleDateFormat(pattern).format(d);
                    }
                    return stripTrailingZeros(cell.getNumericCellValue());
                case BOOLEAN: return Boolean.toString(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        FormulaEvaluator ev = row.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                        CellValue cv = ev.evaluate(cell);
                        if (cv == null) return "";
                        switch (cv.getCellType()) {
                            case STRING: return cv.getStringValue().trim();
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    Date d = DateUtil.getJavaDate(cv.getNumberValue());
                                    String fmt2 = cell.getCellStyle() != null ? cell.getCellStyle().getDataFormatString() : null;
                                    boolean hasTime2 = fmt2 != null && fmt2.toLowerCase().contains("h");
                                    String pattern2 = hasTime2 ? "dd.MM.yyyy HH:mm" : "dd.MM.yyyy";
                                    return new SimpleDateFormat(pattern2).format(d);
                                }
                                return stripTrailingZeros(cv.getNumberValue());
                            case BOOLEAN: return Boolean.toString(cv.getBooleanValue());
                            default: return cell.toString().trim();
                        }
                    } catch (Exception ex) {
                        return cell.toString().trim();
                    }
                default: return cell.toString().trim();
            }
        } catch (Exception ex) {
            return cell.toString().trim();
        }
    }

    private static String getCellDateDots(Row row, int c) {
        Cell cell = row.getCell(c);
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return DATE_FMT_DOTS.format(cell.getDateCellValue());
        }
        return cell == null ? "" : cell.toString().trim();
    }

    private static Double safeDoubleFromCell(Cell cell) {
        if (cell == null) return null;
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    if (!DateUtil.isCellDateFormatted(cell)) return cell.getNumericCellValue();
                    return null;
                case STRING: {
                    String s = cell.getStringCellValue().trim().replace(',', '.');
                    if (s.isEmpty()) return null;
                    return Double.parseDouble(s);
                }
                case FORMULA: {
                    FormulaEvaluator ev = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cv = ev.evaluate(cell);
                    if (cv == null) return null;
                    if (cv.getCellType() == CellType.NUMERIC) return cv.getNumberValue();
                    if (cv.getCellType() == CellType.STRING) {
                        String s = cv.getStringValue().trim().replace(',', '.');
                        if (s.isEmpty()) return null;
                        return Double.parseDouble(s);
                    }
                    return null;
                }
                default: return null;
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private static Integer safeIntegerFromCell(Cell cell) {
        if (cell == null) return null;
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    if (!DateUtil.isCellDateFormatted(cell)) return (int) Math.round(cell.getNumericCellValue());
                    return null;
                case STRING: {
                    String s = cell.getStringCellValue().trim();
                    if (s.isEmpty()) return null;
                    return Integer.parseInt(s);
                }
                case FORMULA: {
                    FormulaEvaluator ev = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cv = ev.evaluate(cell);
                    if (cv == null) return null;
                    if (cv.getCellType() == CellType.NUMERIC) return (int) Math.round(cv.getNumberValue());
                    if (cv.getCellType() == CellType.STRING) {
                        String s = cv.getStringValue().trim();
                        if (s.isEmpty()) return null;
                        return Integer.parseInt(s);
                    }
                    return null;
                }
                default: return null;
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private static double[] parseDoublePair(String s) {
        if (s == null) return new double[]{0.0, 0.0};
        String[] p = s.split("/");
        if (p.length != 2) return new double[]{0.0, 0.0};
        return new double[]{ parseSafe(p[0]), parseSafe(p[1]) };
    }

    private static double parseSafe(String s) {
        if (s == null) return 0.0;
        s = s.trim().replace(',', '.');
        if (s.isEmpty()) return 0.0;
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }

    private static String stripTrailingZeros(double val) {
        String str = Double.toString(val);
        return str.endsWith(".0") ? str.substring(0, str.length() - 2) : str;
    }

    private static Double round(double v, int p) {
        return BigDecimal.valueOf(v).setScale(p, java.math.RoundingMode.HALF_UP).doubleValue();
    }
}