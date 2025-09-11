package excel;

import excel.SalesAggregator.SalesRow;
import model.AggregatedConsumption;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Excel sales importer for Croatian sales data.
 * Supports flexible header detection, European number formats, 
 * and handles optional dates with multiple format support.
 */
public class ExcelSalesImporter {
    
    // System properties for configuration
    private static final String PROP_SCAN_ROWS = "fost.excel.scanRows";
    private static final String PROP_DEBUG_EXCEL = "fost.debugexcel";
    
    // Default settings
    private static final int DEFAULT_SCAN_ROWS = 500;
    private static final boolean DEBUG = "1".equals(System.getProperty(PROP_DEBUG_EXCEL));
    
    // Header normalization patterns for Croatian headers
    private static final Map<String, String> HEADER_MAPPINGS = new HashMap<>();
    static {
        // Datum patterns
        HEADER_MAPPINGS.put("datum", "datum");
        
        // Šifra patterns  
        HEADER_MAPPINGS.put("sifra", "sifra");
        HEADER_MAPPINGS.put("kod", "sifra");
        HEADER_MAPPINGS.put("artikl", "sifra");
        
        // Naziv patterns
        HEADER_MAPPINGS.put("naziv", "naziv");
        HEADER_MAPPINGS.put("nazivrobe", "naziv");
        HEADER_MAPPINGS.put("roba", "naziv");
        HEADER_MAPPINGS.put("proizvod", "naziv");
        HEADER_MAPPINGS.put("opis", "naziv");
        
        // Količina patterns
        HEADER_MAPPINGS.put("kolicina", "kolicina");
        HEADER_MAPPINGS.put("qty", "kolicina");
        HEADER_MAPPINGS.put("kom", "kolicina");
        HEADER_MAPPINGS.put("komada", "kolicina");
        
        // Komitent patterns
        HEADER_MAPPINGS.put("komitent", "komitent");
        HEADER_MAPPINGS.put("kupac", "komitent");
        HEADER_MAPPINGS.put("klijent", "komitent");
        
        // Neto vrijednost patterns
        HEADER_MAPPINGS.put("neto", "neto");
        HEADER_MAPPINGS.put("netovrijednost", "neto");
        HEADER_MAPPINGS.put("vrijednost", "neto");
        HEADER_MAPPINGS.put("cijena", "neto");
        HEADER_MAPPINGS.put("iznos", "neto");
    }
    
    // Date formatters for various Croatian date formats
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy."),
        DateTimeFormatter.ofPattern("d.M.yyyy"),
        DateTimeFormatter.ofPattern("d.M.yyyy."),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy")
    };
    
    // European number patterns
    private static final Pattern EUROPEAN_NUMBER_PATTERN = Pattern.compile(
        "([+-]?(?:\\d{1,3}(?:[. ]\\d{3})*(?:,\\d+)?|\\d+(?:,\\d+)?|\\d+(?:\\.\\d+)?))"
    );
    
    /**
     * Column mapping information detected from headers
     */
    public static class ColumnMapping {
        public int datumCol = -1;
        public int sifraCol = -1; 
        public int nazivCol = -1;
        public int kolicinaCol = -1;
        public int komitentCol = -1;
        public int netoCol = -1;
        
        public boolean isValid() {
            return nazivCol >= 0 && kolicinaCol >= 0; // Minimum required fields
        }
        
        @Override
        public String toString() {
            return String.format("ColumnMapping{datum=%d, sifra=%d, naziv=%d, kolicina=%d, komitent=%d, neto=%d}",
                    datumCol, sifraCol, nazivCol, kolicinaCol, komitentCol, netoCol);
        }
    }
    
    /**
     * Import sales data from Excel file with file chooser dialog.
     * 
     * @return List of aggregated consumption data, or null if cancelled/failed
     */
    public static List<AggregatedConsumption> importSalesFromExcel() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Odaberi Excel datoteku s prodajnim podacima");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel datoteke (*.xlsx, *.xls)", "xlsx", "xls"));
        
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return null; // User cancelled
        }
        
        File file = chooser.getSelectedFile();
        try {
            return importSalesFromExcel(file);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, 
                "Greška pri uvozu prodajnih podataka:\n" + ex.getMessage(),
                "Greška", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
    
    /**
     * Import sales data from specific Excel file.
     * 
     * @param file Excel file to import
     * @return List of aggregated consumption data
     * @throws IOException If file cannot be read
     */
    public static List<AggregatedConsumption> importSalesFromExcel(File file) throws IOException {
        if (DEBUG) {
            System.out.println("ExcelSalesImporter: Starting import of " + file.getName());
        }
        
        List<SalesRow> salesRows = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(file)) {
            Workbook workbook = createWorkbook(file, fis);
            Sheet sheet = workbook.getSheetAt(0); // Use first sheet
            
            if (sheet.getLastRowNum() < 1) {
                throw new IllegalArgumentException("Excel datoteka je prazna ili nema podataka");
            }
            
            // Detect column mapping
            ColumnMapping mapping = detectColumnMapping(sheet);
            if (!mapping.isValid()) {
                throw new IllegalArgumentException(
                    "Ne mogu pronaći potrebne kolone u Excel datoteci. " +
                    "Provjerite da postoje kolone za Naziv i Količinu.");
            }
            
            if (DEBUG) {
                System.out.println("ExcelSalesImporter: Detected mapping: " + mapping);
            }
            
            // Parse data rows
            int rowCount = 0;
            int parsedCount = 0;
            
            for (int r = 1; r <= sheet.getLastRowNum(); r++) { // Skip header row
                Row row = sheet.getRow(r);
                if (row == null) continue;
                
                rowCount++;
                SalesRow salesRow = parseRow(row, mapping);
                if (salesRow != null) {
                    salesRows.add(salesRow);
                    parsedCount++;
                    
                    if (DEBUG && parsedCount <= 5) {
                        System.out.println("ExcelSalesImporter: Sample row " + parsedCount + ": " + salesRow);
                    }
                }
            }
            
            if (DEBUG) {
                System.out.printf("ExcelSalesImporter: Processed %d rows, parsed %d valid sales rows%n", 
                    rowCount, parsedCount);
            }
            
        } catch (Exception ex) {
            throw new IOException("Greška pri čitanju Excel datoteke: " + ex.getMessage(), ex);
        }
        
        // Aggregate the sales data
        List<AggregatedConsumption> aggregated = SalesAggregator.aggregateConsumption(salesRows);
        
        if (DEBUG) {
            System.out.printf("ExcelSalesImporter: Aggregated into %d consumption items%n", 
                aggregated.size());
        }
        
        return aggregated;
    }
    
    /**
     * Create appropriate workbook based on file extension
     */
    private static Workbook createWorkbook(File file, FileInputStream fis) throws IOException {
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".xlsx")) {
            return new XSSFWorkbook(fis);
        } else if (fileName.endsWith(".xls")) {
            return new HSSFWorkbook(fis);
        } else {
            throw new IOException("Nepodržani format datoteke. Podržani su .xlsx i .xls formati.");
        }
    }
    
    /**
     * Detect column mapping from sheet headers
     */
    private static ColumnMapping detectColumnMapping(Sheet sheet) {
        int scanRows = Integer.getInteger(PROP_SCAN_ROWS, DEFAULT_SCAN_ROWS);
        ColumnMapping mapping = new ColumnMapping();
        
        // Try header-based detection first
        for (int r = 0; r < Math.min(scanRows, sheet.getLastRowNum() + 1); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            
            ColumnMapping headerMapping = tryHeaderMapping(row);
            if (headerMapping.isValid()) {
                if (DEBUG) {
                    System.out.println("ExcelSalesImporter: Found headers at row " + r);
                }
                return headerMapping;
            }
        }
        
        if (DEBUG) {
            System.out.println("ExcelSalesImporter: Header detection failed, trying heuristic approach");
        }
        
        // Fallback to heuristic detection
        return tryHeuristicMapping(sheet, scanRows);
    }
    
    /**
     * Try to detect columns based on header names
     */
    private static ColumnMapping tryHeaderMapping(Row row) {
        ColumnMapping mapping = new ColumnMapping();
        
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell == null) continue;
            
            String header = getCellStringValue(cell);
            if (header.isEmpty()) continue;
            
            String normalized = normalizeHeader(header);
            String mappedField = HEADER_MAPPINGS.get(normalized);
            
            if (mappedField != null) {
                switch (mappedField) {
                    case "datum":
                        mapping.datumCol = c;
                        break;
                    case "sifra":
                        mapping.sifraCol = c;
                        break;
                    case "naziv":
                        mapping.nazivCol = c;
                        break;
                    case "kolicina":
                        mapping.kolicinaCol = c;
                        break;
                    case "komitent":
                        mapping.komitentCol = c;
                        break;
                    case "neto":
                        mapping.netoCol = c;
                        break;
                }
            }
        }
        
        return mapping;
    }
    
    /**
     * Try to detect columns using heuristic analysis of data content
     */
    private static ColumnMapping tryHeuristicMapping(Sheet sheet, int scanRows) {
        ColumnMapping mapping = new ColumnMapping();
        
        int maxCol = 0;
        for (int r = 0; r < Math.min(scanRows, sheet.getLastRowNum() + 1); r++) {
            Row row = sheet.getRow(r);
            if (row != null) {
                maxCol = Math.max(maxCol, row.getLastCellNum());
            }
        }
        
        // Analyze each column to determine its likely type
        for (int c = 0; c < maxCol; c++) {
            ColumnType type = analyzeColumn(sheet, c, scanRows);
            
            switch (type) {
                case DATE:
                    if (mapping.datumCol == -1) mapping.datumCol = c;
                    break;
                case TEXT_LONG:
                    if (mapping.nazivCol == -1) mapping.nazivCol = c;
                    else if (mapping.komitentCol == -1) mapping.komitentCol = c;
                    break;
                case TEXT_SHORT:
                    if (mapping.sifraCol == -1) mapping.sifraCol = c;
                    break;
                case QUANTITY:
                    if (mapping.kolicinaCol == -1) mapping.kolicinaCol = c;
                    break;
                case MONEY:
                    if (mapping.netoCol == -1) mapping.netoCol = c;
                    break;
            }
        }
        
        return mapping;
    }
    
    private enum ColumnType {
        DATE, TEXT_LONG, TEXT_SHORT, QUANTITY, MONEY, UNKNOWN
    }
    
    /**
     * Analyze column content to determine likely data type
     */
    private static ColumnType analyzeColumn(Sheet sheet, int colIndex, int scanRows) {
        int dateCount = 0;
        int textLongCount = 0;
        int textShortCount = 0;
        int quantityCount = 0;
        int moneyCount = 0;
        int totalValues = 0;
        
        for (int r = 1; r < Math.min(scanRows, sheet.getLastRowNum() + 1); r++) { // Skip header
            Row row = sheet.getRow(r);
            if (row == null) continue;
            
            Cell cell = row.getCell(colIndex);
            if (cell == null) continue;
            
            String value = getCellStringValue(cell);
            if (value.isEmpty()) continue;
            
            totalValues++;
            
            // Check for date patterns
            if (tryParseDate(value) != null || DateUtil.isCellDateFormatted(cell)) {
                dateCount++;
            }
            // Check for number patterns
            else if (isLikelyQuantity(value)) {
                quantityCount++;
            }
            // Check for money patterns (larger numbers, currency symbols)
            else if (isLikelyMoney(value)) {
                moneyCount++;
            }
            // Check text length
            else {
                if (value.length() > 10) {
                    textLongCount++;
                } else {
                    textShortCount++;
                }
            }
        }
        
        if (totalValues == 0) return ColumnType.UNKNOWN;
        
        // Determine type based on majority content (>50% threshold)
        double threshold = totalValues * 0.5;
        
        if (dateCount > threshold) return ColumnType.DATE;
        if (quantityCount > threshold) return ColumnType.QUANTITY;
        if (moneyCount > threshold) return ColumnType.MONEY;
        if (textLongCount > threshold) return ColumnType.TEXT_LONG;
        if (textShortCount > threshold) return ColumnType.TEXT_SHORT;
        
        return ColumnType.UNKNOWN;
    }
    
    /**
     * Parse a single data row using the detected column mapping
     */
    private static SalesRow parseRow(Row row, ColumnMapping mapping) {
        try {
            // Extract naziv (required)
            String naziv = getCellStringValue(row.getCell(mapping.nazivCol));
            if (naziv.isEmpty()) {
                return null; // Skip rows without item name
            }
            
            // Extract kolicina (required)
            Double kolicina = parseEuropeanNumber(getCellStringValue(row.getCell(mapping.kolicinaCol)));
            if (kolicina == null || kolicina <= 0) {
                return null; // Skip rows without valid quantity
            }
            
            // Extract optional fields
            LocalDate datum = null;
            if (mapping.datumCol >= 0) {
                datum = parseDate(row.getCell(mapping.datumCol));
            }
            
            String sifra = null;
            if (mapping.sifraCol >= 0) {
                sifra = getCellStringValue(row.getCell(mapping.sifraCol));
                if (sifra.isEmpty()) sifra = null;
            }
            
            String komitent = null;
            if (mapping.komitentCol >= 0) {
                komitent = getCellStringValue(row.getCell(mapping.komitentCol));
                if (komitent.isEmpty()) komitent = null;
            }
            
            Double neto = null;
            if (mapping.netoCol >= 0) {
                neto = parseEuropeanNumber(getCellStringValue(row.getCell(mapping.netoCol)));
            }
            
            return new SalesRow(datum, sifra, naziv, kolicina, komitent, neto);
            
        } catch (Exception ex) {
            if (DEBUG) {
                System.err.println("ExcelSalesImporter: Error parsing row " + row.getRowNum() + ": " + ex.getMessage());
            }
            return null;
        }
    }
    
    /**
     * Normalize header text for mapping lookup
     */
    private static String normalizeHeader(String header) {
        if (header == null) return "";
        
        return header.toLowerCase()
                     .replace("č", "c").replace("ć", "c")
                     .replace("š", "s").replace("ž", "z")
                     .replace("đ", "d")
                     .replaceAll("[^a-z0-9]", "")  // Remove punctuation and spaces
                     .trim();
    }
    
    /**
     * Get string value from cell, handling various cell types
     */
    private static String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cleanStringValue(cell.getStringCellValue());
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return DateTimeFormatter.ofPattern("dd.MM.yyyy").format(
                            cell.getDateCellValue().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        );
                    }
                    return formatNumber(cell.getNumericCellValue());
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cellValue = evaluator.evaluate(cell);
                    if (cellValue.getCellType() == CellType.STRING) {
                        return cleanStringValue(cellValue.getStringValue());
                    } else if (cellValue.getCellType() == CellType.NUMERIC) {
                        return formatNumber(cellValue.getNumberValue());
                    }
                    break;
            }
        } catch (Exception ignored) {
            // Fall back to toString()
        }
        
        return cleanStringValue(cell.toString());
    }
    
    /**
     * Clean string value by removing non-breaking spaces and normalizing whitespace
     */
    private static String cleanStringValue(String value) {
        if (value == null) return "";
        
        return value.replace('\u00A0', ' ')     // Non-breaking space
                    .replace('\u202F', ' ')     // Narrow no-break space
                    .replace('\u2007', ' ')     // Figure space
                    .trim();
    }
    
    /**
     * Format number without unnecessary decimal places
     */
    private static String formatNumber(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        } else {
            DecimalFormat df = new DecimalFormat("#.##########");
            return df.format(value);
        }
    }
    
    /**
     * Parse European-style numbers (1.234,56 or 1 234,56)
     */
    private static Double parseEuropeanNumber(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        
        text = cleanStringValue(text);
        
        // Handle percentage values (skip them as they're not quantities)
        if (text.contains("%")) {
            return null;
        }
        
        try {
            // Remove currency symbols and extra spaces
            text = text.replaceAll("[€$£¥]", "").trim();
            
            // Simple number without separators
            if (text.matches("^[+-]?\\d+$")) {
                return Double.parseDouble(text);
            }
            
            // Number with decimal point only (US format)
            if (text.matches("^[+-]?\\d+\\.\\d+$") && !text.matches("\\d{1,3}(\\.\\d{3})+")) {
                return Double.parseDouble(text);
            }
            
            // European format detection
            int lastCommaPos = text.lastIndexOf(',');
            int lastDotPos = text.lastIndexOf('.');
            int lastSpacePos = text.lastIndexOf(' ');
            
            String number = text;
            
            // Check if comma is followed by 1-2 digits at the end (decimal separator)
            if (lastCommaPos >= 0 && 
                (lastCommaPos == text.length() - 3 || lastCommaPos == text.length() - 2)) {
                
                String afterComma = text.substring(lastCommaPos + 1);
                if (afterComma.matches("\\d{1,2}")) {
                    // Comma is decimal separator
                    String beforeComma = text.substring(0, lastCommaPos);
                    beforeComma = beforeComma.replaceAll("[. ]", ""); // Remove thousands separators
                    number = beforeComma + "." + afterComma;
                    return Double.parseDouble(number);
                }
            }
            
            // Check if dot is followed by 1-2 digits at the end (decimal separator)
            if (lastDotPos >= 0 && 
                (lastDotPos == text.length() - 3 || lastDotPos == text.length() - 2)) {
                
                String afterDot = text.substring(lastDotPos + 1);
                if (afterDot.matches("\\d{1,2}")) {
                    // Check if this looks like thousands separators pattern before dot
                    String beforeDot = text.substring(0, lastDotPos);
                    if (!beforeDot.matches(".*[. ]\\d{3}$")) {
                        // Dot is likely decimal separator
                        beforeDot = beforeDot.replaceAll("[, ]", ""); // Remove thousands separators  
                        number = beforeDot + "." + afterDot;
                        return Double.parseDouble(number);
                    }
                }
            }
            
            // Remove all separators and treat as integer
            number = text.replaceAll("[., ]", "");
            if (number.matches("^[+-]?\\d+$")) {
                return Double.parseDouble(number);
            }
            
        } catch (NumberFormatException ignored) {
        }
        
        return null;
    }
    
    /**
     * Parse date from cell, supporting multiple formats
     */
    private static LocalDate parseDate(Cell cell) {
        if (cell == null) return null;
        
        // Try Excel date format first
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            try {
                return cell.getDateCellValue().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            } catch (Exception ignored) {
            }
        }
        
        // Try parsing as text
        String dateText = getCellStringValue(cell);
        return tryParseDate(dateText);
    }
    
    /**
     * Try to parse date from text using various Croatian formats
     */
    private static LocalDate tryParseDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        
        text = text.trim();
        
        // Try each date formatter
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        
        return null;
    }
    
    /**
     * Check if text looks like a quantity (small positive number)
     */
    private static boolean isLikelyQuantity(String text) {
        Double number = parseEuropeanNumber(text);
        return number != null && number > 0 && number < 10000;
    }
    
    /**
     * Check if text looks like a money amount (larger number, possibly with currency)
     */
    private static boolean isLikelyMoney(String text) {
        if (text.contains("kn") || text.contains("€") || text.contains("$")) {
            return true;
        }
        Double number = parseEuropeanNumber(text);
        return number != null && number > 10;
    }
}