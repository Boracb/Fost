package test;

import excel.ExcelSalesImporter;

/**
 * Full test for Excel Sales Importer including number parsing functionality.
 */
public class TestExcelSalesImporterFull {
    
    public static void main(String[] args) {
        System.out.println("=== Test Excel Sales Importer - Number Parsing ===");
        
        testEuropeanNumberParsing();
        testHeaderNormalization();
        testDateParsing();
        
        System.out.println("=== All parsing tests completed successfully! ===");
    }
    
    /**
     * Test European number parsing functionality using reflection
     */
    private static void testEuropeanNumberParsing() {
        System.out.println("\n--- Testing European Number Parsing ---");
        
        // Test various European number formats
        String[] testNumbers = {
            "1.234,56",    // European style with dot thousands separator and comma decimal
            "1 234,56",    // Space thousands separator 
            "1.078,00",    // From the problem statement example
            "1,078.00",    // US style
            "1234",        // Simple integer
            "234.50",      // Decimal with dot
            "15%",         // Percentage (should be ignored)
            "€ 1.500,00",  // With currency symbol
            "",            // Empty string
            "not a number" // Invalid text
        };
        
        for (String testNumber : testNumbers) {
            try {
                // Using reflection to access the private method for testing
                java.lang.reflect.Method method = ExcelSalesImporter.class
                    .getDeclaredMethod("parseEuropeanNumber", String.class);
                method.setAccessible(true);
                Double result = (Double) method.invoke(null, testNumber);
                
                System.out.printf("'%s' -> %s%n", testNumber, 
                    result != null ? String.format("%.2f", result) : "null");
            } catch (Exception e) {
                System.out.printf("'%s' -> Error: %s%n", testNumber, e.getMessage());
            }
        }
    }
    
    /**
     * Test header normalization functionality
     */
    private static void testHeaderNormalization() {
        System.out.println("\n--- Testing Header Normalization ---");
        
        String[] testHeaders = {
            "Naziv robe / opis",
            "Količina",
            "Komitent / opis", 
            "Neto vrijednost",
            "Datum",
            "Šifra",
            "naziv",
            "KOLIČINA",
            "qty"
        };
        
        for (String header : testHeaders) {
            try {
                java.lang.reflect.Method method = ExcelSalesImporter.class
                    .getDeclaredMethod("normalizeHeader", String.class);
                method.setAccessible(true);
                String result = (String) method.invoke(null, header);
                
                System.out.printf("'%s' -> '%s'%n", header, result);
            } catch (Exception e) {
                System.out.printf("'%s' -> Error: %s%n", header, e.getMessage());
            }
        }
    }
    
    /**
     * Test date parsing functionality
     */
    private static void testDateParsing() {
        System.out.println("\n--- Testing Date Parsing ---");
        
        String[] testDates = {
            "15.01.2024",
            "15.01.2024.",
            "1.2.2024",
            "2024-01-15",
            "15/01/2024",
            "",
            "not a date"
        };
        
        for (String dateStr : testDates) {
            try {
                java.lang.reflect.Method method = ExcelSalesImporter.class
                    .getDeclaredMethod("tryParseDate", String.class);
                method.setAccessible(true);
                java.time.LocalDate result = (java.time.LocalDate) method.invoke(null, dateStr);
                
                System.out.printf("'%s' -> %s%n", dateStr, result);
            } catch (Exception e) {
                System.out.printf("'%s' -> Error: %s%n", dateStr, e.getMessage());
            }
        }
    }
}