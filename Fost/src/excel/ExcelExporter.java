package excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * Klasa za izvoz podataka iz Swing TableModel-a u Excel (.xlsx) datoteku.
 * Koristi Apache POI biblioteku za kreiranje i stiliziranje Excel radne knjige.
 */
public class ExcelExporter {

    // Podržani formati za parsiranje datuma i datuma+vremena 
    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE
    };

    /**
     * Otvara dijalog za spremanje i izvozi sadržaj TableModel-a u .xlsx datoteku.
     * - Kreira workbook, sheet i stilove za zaglavlja, datume, datume+vrijeme i decimalne brojeve.
     * - Prepoznaje posebne kolone po indeksu (djelatnik, start/end time, duration).
     * - Automatski prilagođava širinu stupaca.
     */
    
    // Indeksi posebnih kolona (0-based)
    public static void exportTableToExcel(TableModel model) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Spremi Excel datoteku");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel Workbook (*.xlsx)", "xlsx"));

        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return; // korisnik odustao
        }
// Osiguraj ekstenziju .xlsx
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".xlsx")) {
            file = new File(file.getParentFile(), file.getName() + ".xlsx");
        }

        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(file)) {

            Sheet sheet = wb.createSheet("Podaci");
            CreationHelper helper = wb.getCreationHelper();

            // Stil zaglavlja
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(headerFont);

            // Formati ćelija
            DataFormat dataFormat = helper.createDataFormat();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(dataFormat.getFormat("dd/MM/yyyy"));

            CellStyle dateTimeStyle = wb.createCellStyle();
            dateTimeStyle.setDataFormat(dataFormat.getFormat("dd/MM/yyyy HH:mm"));

            CellStyle doubleStyle = wb.createCellStyle();
            doubleStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));

            // 1) Red zaglavlja
            Row header = sheet.createRow(0);
            for (int c = 0; c < model.getColumnCount(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(model.getColumnName(c));
                cell.setCellStyle(headerStyle);
            }

            // 2) Ispis podataka
            for (int r = 0; r < model.getRowCount(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < model.getColumnCount(); c++) {
                    Object value = model.getValueAt(r, c);
                    Cell cell = row.createCell(c);

                    // Kolona 7: djelatnik → tekst
                    if (c == 7) {
                        cell.setCellValue(value != null ? value.toString() : "");
                        continue;
                    }

                    // Kolone 12 i 13: startTime / endTime → datum+vrijeme
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

                    // Kolona 14: duration → tekst
                    if (c == 14) {
                        cell.setCellValue(value != null ? value.toString() : "");
                        continue;
                    }

                    // Ostale kolone — automatsko prepoznavanje tipa
                    if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                        cell.setCellStyle(doubleStyle);
                    } else if (value instanceof Date) {
                        cell.setCellValue((Date) value);
                        cell.setCellStyle(dateStyle);
                    } else if (value instanceof String) {
                        String s = ((String) value).trim();
                        LocalDate ld = tryParseDate(s);
                        if (ld != null) {
                            Date utilDate = Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
                            cell.setCellValue(utilDate);
                            cell.setCellStyle(dateStyle);
                        } else {
                            cell.setCellValue(s);
                        }
                    } else {
                        cell.setCellValue(value != null ? value.toString() : "");
                    }
                }
            }

            // 3) Auto-width stupaca
            for (int c = 0; c < model.getColumnCount(); c++) {
                sheet.autoSizeColumn(c);
            }

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

    /**
     * Pokušava parsirati datum iz stringa (bez vremena).
     */
    private static LocalDate tryParseDate(String text) {
        if (text == null || text.isBlank()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(text, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    /**
     * Pokušava parsirati datum ili datum+vrijeme i vratiti kao java.util.Date.
     */
    private static Date tryParseDateTime(String text) {
        if (text == null || text.isBlank()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                // prvo pokušaj LocalDateTime
                try {
                    LocalDateTime ldt = LocalDateTime.parse(text, fmt);
                    return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
                } catch (DateTimeParseException e) {
                    // zatim probaj samo datum
                    LocalDate ld = LocalDate.parse(text, fmt);
                    return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
                }
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }
}
