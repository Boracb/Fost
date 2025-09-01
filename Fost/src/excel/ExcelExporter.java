package excel;

import logic.ProductionStatsCalculator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ExcelExporter:
 * - exportTableToExcel(DefaultTableModel) and exportTableToExcel(TableModel) for backward compatibility
 * - exportTableAndStats(model, m2PoSatu) will calculate stats using ProductionStatsCalculator (converts TableModel -> DefaultTableModel)
 * - exportTableAndStats(model, stats) writes two sheets: "Podaci" and "Statistika"
 *
 * Statistika sheet follows exact structure requested:
 * UKUPNO: Ukupno kom, Ukupno m2, Kapacitet m2 po danu
 * IZRAĐENO: Kom (izrađeno), Neto (izrađeno), m2 (izrađeno)
 * ZA IZRADITI: Kom (za izraditi), Neto (za izraditi), m2 (za izraditi)
 * DANI ZA IZRADU: Kalendarski dani preostalo, Radni dani preostalo
 */
public class ExcelExporter {

    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy H:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    };

    private static final int MIN_COLUMNS = 16;

    // backward-compatibility
    public static void exportTableToExcel(DefaultTableModel model) {
        exportTableAndStats((TableModel) model, (Map<String, Object>) null);
    }

    public static void exportTableToExcel(TableModel model) {
        exportTableAndStats(model, (Map<String, Object>) null);
    }

    /**
     * Ensure DefaultTableModel for ProductionStatsCalculator, compute stats and export both sheets.
     */
    public static void exportTableAndStats(TableModel model, double m2PoSatu) {
        DefaultTableModel dtm;
        if (model instanceof DefaultTableModel) {
            dtm = (DefaultTableModel) model;
        } else {
            dtm = new DefaultTableModel();
            int cols = model.getColumnCount();
            for (int c = 0; c < cols; c++) {
                try {
                    dtm.addColumn(model.getColumnName(c));
                } catch (Exception ex) {
                    dtm.addColumn("Col " + c);
                }
            }
            for (int r = 0; r < model.getRowCount(); r++) {
                Object[] row = new Object[cols];
                for (int c = 0; c < cols; c++) {
                    try {
                        row[c] = model.getValueAt(r, c);
                    } catch (Exception ex) {
                        row[c] = null;
                    }
                }
                dtm.addRow(row);
            }
        }

        Map<String, Object> stats;
        try {
            stats = ProductionStatsCalculator.calculate(dtm, m2PoSatu);
        } catch (Exception ex) {
            stats = new LinkedHashMap<>();
        }
        exportTableAndStats(model, stats);
    }

    /**
     * Writes two sheets: "Podaci" (TableModel) and "Statistika" (ordered map)
     */
    public static void exportTableAndStats(TableModel model, Map<String, Object> stats) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Spremi Excel datoteku");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel Workbook (*.xlsx)", "xlsx"));

        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return; // korisnik odustao
        }

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".xlsx")) {
            file = new File(file.getParentFile(), file.getName() + ".xlsx");
        }

        try (Workbook wb = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(file)) {
            CreationHelper helper = wb.getCreationHelper();

            // Styles
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(headerFont);

            DataFormat dataFormat = helper.createDataFormat();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(dataFormat.getFormat("dd/MM/yyyy"));

            CellStyle dateTimeStyle = wb.createCellStyle();
            dateTimeStyle.setDataFormat(dataFormat.getFormat("dd/MM/yyyy HH:mm"));

            CellStyle doubleStyle = wb.createCellStyle();
            doubleStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));

            CellStyle integerStyle = wb.createCellStyle();
            integerStyle.setDataFormat(dataFormat.getFormat("#,##0"));

            // SHEET 1: Podaci
            Sheet sheet = wb.createSheet("Podaci");

            int modelCols = model.getColumnCount();
            int exportCols = Math.max(modelCols, MIN_COLUMNS);

            Row header = sheet.createRow(0);
            for (int c = 0; c < exportCols; c++) {
                Cell cell = header.createCell(c);
                String colName;
                if (c < modelCols) {
                    try {
                        colName = model.getColumnName(c);
                    } catch (Exception ex) {
                        colName = "Col " + c;
                    }
                } else {
                    switch (c) {
                        case 7:
                            colName = "djelatnik";
                            break;
                        case 12:
                            colName = "startTime";
                            break;
                        case 13:
                            colName = "endTime";
                            break;
                        case 14:
                            colName = "duration";
                            break;
                        case 15:
                            colName = "planDatumIsporuke";
                            break;
                        default:
                            colName = "Col " + c;
                    }
                }
                cell.setCellValue(colName);
                cell.setCellStyle(headerStyle);
            }

            for (int r = 0; r < model.getRowCount(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < exportCols; c++) {
                    Cell cell = row.createCell(c);
                    Object value = c < modelCols ? model.getValueAt(r, c) : null;

                    if (c == 7) {
                        cell.setCellValue(value != null ? value.toString() : "");
                        continue;
                    }
                    if (c == 12 || c == 13) {
                        String s = value != null ? value.toString().trim() : "";
                        Date parsedDT = tryParseDateTime(s);
                        if (parsedDT != null) {
                            cell.setCellValue(parsedDT);
                            cell.setCellStyle(dateTimeStyle);
                        } else {
                            cell.setCellValue(s);
                        }
                        continue;
                    }
                    if (c == 14 || c == 15) {
                        cell.setCellValue(value != null ? value.toString() : "");
                        continue;
                    }

                    if (value instanceof Number) {
                        double d = ((Number) value).doubleValue();
                        cell.setCellValue(d);
                        if (value instanceof Integer || value instanceof Long || value instanceof Short) {
                            cell.setCellStyle(integerStyle);
                        } else {
                            cell.setCellStyle(doubleStyle);
                        }
                    } else if (value instanceof Date) {
                        cell.setCellValue((Date) value);
                        cell.setCellStyle(dateStyle);
                    } else if (value instanceof String) {
                        String s = ((String) value).trim();
                        Date parsedDT = tryParseDateTime(s);
                        if (parsedDT != null) {
                            cell.setCellValue(parsedDT);
                            cell.setCellStyle(dateTimeStyle);
                        } else {
                            java.time.LocalDate ld = tryParseDate(s);
                            if (ld != null) {
                                Date utilDate = Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
                                cell.setCellValue(utilDate);
                                cell.setCellStyle(dateStyle);
                            } else {
                                cell.setCellValue(s);
                            }
                        }
                    } else {
                        cell.setCellValue(value != null ? value.toString() : "");
                    }
                }
            }

            for (int c = 0; c < exportCols; c++) sheet.autoSizeColumn(c);

            // SHEET 2: Statistika
            Sheet statSheet = wb.createSheet("Statistika");
            int statRowIdx = 0;
            if (stats == null) stats = new LinkedHashMap<>();

            Map<String, Object> ordered = new LinkedHashMap<>();
            // UKUPNO
            ordered.put("UKUPNO", null);
            ordered.put("Ukupno kom:", stats.getOrDefault(ProductionStatsCalculator.KOM, 0));
            ordered.put("Ukupno m2:", stats.getOrDefault(ProductionStatsCalculator.M2, 0));
            ordered.put("Kapacitet m2 po danu:", stats.getOrDefault(ProductionStatsCalculator.PROSJEK_M2_PO_DANU, 0));
            // IZRAĐENO
            ordered.put("IZRAĐENO", null);
            ordered.put("Kom (izrađeno):", stats.getOrDefault(ProductionStatsCalculator.KOM_IZR, 0));
            ordered.put("Neto (izrađeno) (€):", stats.getOrDefault(ProductionStatsCalculator.NETO_IZR, 0));
            ordered.put("m2 (izrađeno):", stats.getOrDefault(ProductionStatsCalculator.M2_IZR, 0));
            // ZA IZRADITI
            ordered.put("ZA IZRADITI", null);
            ordered.put("Kom (za izraditi):", stats.getOrDefault(ProductionStatsCalculator.KOM_ZAI, 0));
            ordered.put("Neto (za izraditi) (€):", stats.getOrDefault(ProductionStatsCalculator.NETO_ZAI, 0));
            ordered.put("m2 (za izraditi):", stats.getOrDefault(ProductionStatsCalculator.M2_ZAI, 0));
            // DANI ZA IZRADU
            ordered.put("DANI ZA IZRADU", null);
            ordered.put("Kalendarski dani preostalo:", stats.getOrDefault(ProductionStatsCalculator.KAL_DANI_PREOSTALO, 0));
            ordered.put("Radni dani preostalo:", stats.getOrDefault(ProductionStatsCalculator.RADNI_DANI_PREOSTALO, 0));

            // Header
            Row sHeader = statSheet.createRow(statRowIdx++);
            Cell sh0 = sHeader.createCell(0);
            sh0.setCellValue("Opis");
            sh0.setCellStyle(headerStyle);
            Cell sh1 = sHeader.createCell(1);
            sh1.setCellValue("Vrijednost");
            sh1.setCellStyle(headerStyle);

            for (Map.Entry<String, Object> e : ordered.entrySet()) {
                Row r = statSheet.createRow(statRowIdx++);
                r.createCell(0).setCellValue(e.getKey());
                Object val = e.getValue();
                Cell valCell = r.createCell(1);
                if (val == null) {
                    valCell.setCellValue("");
                } else if (val instanceof Number) {
                    double dv = ((Number) val).doubleValue();
                    valCell.setCellValue(dv);
                    if (Math.rint(dv) == dv) {
                        valCell.setCellStyle(integerStyle);
                    } else {
                        valCell.setCellStyle(doubleStyle);
                    }
                } else if (val instanceof Date) {
                    valCell.setCellValue((Date) val);
                    valCell.setCellStyle(dateTimeStyle);
                } else {
                    valCell.setCellValue(val.toString());
                }
            }

            statSheet.autoSizeColumn(0);
            statSheet.autoSizeColumn(1);

            // write
            wb.write(fos);

            JOptionPane.showMessageDialog(null,
                    "Excel datoteka uspješno spremljena:\n" + file.getAbsolutePath(),
                    "Spremljeno", JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Greška pri spremanju Excela:\n" + e.getMessage(),
                    "Greška", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static java.time.LocalDate tryParseDate(String text) {
        if (text == null || text.isBlank()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return java.time.LocalDate.parse(text, fmt);
            } catch (DateTimeParseException ignored) { }
        }
        return null;
    }

    private static Date tryParseDateTime(String text) {
        if (text == null || text.isBlank()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                try {
                    java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(text, fmt);
                    return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
                } catch (DateTimeParseException ex) {
                    java.time.LocalDate ld = java.time.LocalDate.parse(text, fmt);
                    return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
                }
            } catch (DateTimeParseException ignored) { }
        }
        return null;
    }
}