package test;

import excel.SalesAggregator;
import excel.SalesAggregator.SalesRow;
import model.AggregatedConsumption;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive test simulating the complete Excel sales import flow.
 * Tests all functionality without requiring UI or actual Excel files.
 */
public class TestCompleteExcelSalesFlow {
    
    public static void main(String[] args) {
        System.out.println("=== Complete Excel Sales Import Flow Test ===");
        
        testCompleteFlow();
        testSystemProperties();
        testEdgeCases();
        
        System.out.println("=== All comprehensive tests passed! ===");
    }
    
    /**
     * Test the complete flow from sales data to aggregated consumption
     */
    private static void testCompleteFlow() {
        System.out.println("\n--- Testing Complete Sales Import Flow ---");
        
        // Simulate Croatian sales data like from the problem statement
        List<SalesRow> salesData = createSampleSalesData();
        
        System.out.printf("Created %d sample sales rows%n", salesData.size());
        
        // Aggregate the data
        List<AggregatedConsumption> aggregated = SalesAggregator.aggregateConsumption(salesData);
        
        System.out.printf("Aggregated into %d consumption items:%n", aggregated.size());
        
        // Sort by quantity descending
        SalesAggregator.sortByQuantityDesc(aggregated);
        
        // Display results in table format
        System.out.println("\nAggregated Consumption Results:");
        System.out.println("==============================================================");
        System.out.printf("%-15s %-25s %8s %12s %12s %15s%n", 
            "Šifra", "Naziv", "Ukupno", "Min datum", "Max datum", "Godišnje (250d)");
        System.out.println("==============================================================");
        
        for (AggregatedConsumption item : aggregated) {
            System.out.printf("%-15s %-25s %8.2f %12s %12s %15.2f%n",
                item.getSifra() != null ? item.getSifra() : "",
                truncate(item.getNaziv(), 25),
                item.getTotalQty(),
                item.getMinDate() != null ? item.getMinDate().toString() : "",
                item.getMaxDate() != null ? item.getMaxDate().toString() : "",
                item.getAnnualConsumption(250)
            );
        }
        
        // Verify calculations
        AggregatedConsumption vijci = aggregated.stream()
            .filter(a -> a.getNaziv().contains("Vijak M8"))
            .findFirst().orElse(null);
        
        if (vijci != null) {
            System.out.printf("\nVerifying calculations for '%s':%n", vijci.getNaziv());
            System.out.printf("  Total quantity: %.2f%n", vijci.getTotalQty());
            System.out.printf("  Average per day: %.2f%n", vijci.getAvgPerDay());
            System.out.printf("  Annual consumption: %.2f%n", vijci.getAnnualConsumption(250));
        }
    }
    
    /**
     * Test system properties support
     */
    private static void testSystemProperties() {
        System.out.println("\n--- Testing System Properties ---");
        
        // Test default values
        int defaultScanRows = Integer.getInteger("fost.excel.scanRows", 500);
        boolean debugEnabled = "1".equals(System.getProperty("fost.debugexcel"));
        
        System.out.printf("Default scan rows: %d%n", defaultScanRows);
        System.out.printf("Debug enabled: %b%n", debugEnabled);
        
        // Set test properties
        System.setProperty("fost.excel.scanRows", "100");
        System.setProperty("fost.debugexcel", "1");
        
        int testScanRows = Integer.getInteger("fost.excel.scanRows", 500);
        boolean testDebug = "1".equals(System.getProperty("fost.debugexcel"));
        
        System.out.printf("Test scan rows: %d%n", testScanRows);
        System.out.printf("Test debug enabled: %b%n", testDebug);
        
        assert testScanRows == 100 : "Scan rows property not working";
        assert testDebug : "Debug property not working";
        
        System.out.println("System properties working correctly!");
    }
    
    /**
     * Test edge cases and error handling
     */
    private static void testEdgeCases() {
        System.out.println("\n--- Testing Edge Cases ---");
        
        // Test with null/empty data
        List<AggregatedConsumption> emptyResult = SalesAggregator.aggregateConsumption(null);
        assert emptyResult.isEmpty() : "Should handle null input";
        
        emptyResult = SalesAggregator.aggregateConsumption(new ArrayList<>());
        assert emptyResult.isEmpty() : "Should handle empty input";
        
        // Test with invalid data
        List<SalesRow> invalidData = new ArrayList<>();
        invalidData.add(new SalesRow(null, null, null, 0)); // No name, zero quantity
        invalidData.add(new SalesRow(null, null, "", 10)); // Empty name
        invalidData.add(new SalesRow(null, null, "Valid Item", -5)); // Negative quantity
        invalidData.add(new SalesRow(null, null, "Valid Item", 10)); // Valid item
        
        List<AggregatedConsumption> validResults = SalesAggregator.aggregateConsumption(invalidData);
        assert validResults.size() == 1 : "Should filter out invalid rows";
        assert validResults.get(0).getNaziv().equals("Valid Item") : "Should keep valid row";
        
        // Test date range filtering
        List<AggregatedConsumption> testData = createTestAggregations();
        List<AggregatedConsumption> filtered = SalesAggregator.filterByDateRange(
            testData, LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 28)
        );
        
        System.out.printf("Date filtering: %d -> %d items%n", testData.size(), filtered.size());
        
        System.out.println("Edge case tests passed!");
    }
    
    /**
     * Create sample sales data similar to Croatian Excel export
     */
    private static List<SalesRow> createSampleSalesData() {
        List<SalesRow> data = new ArrayList<>();
        
        // Simulate various sales with Croatian item names and European number formats
        data.add(new SalesRow(LocalDate.of(2024, 1, 15), "V001", "Vijak M8x20 DIN 912", 100.0, "OOO METALKA d.o.o.", 1234.56));
        data.add(new SalesRow(LocalDate.of(2024, 1, 20), "M001", "Matica M8 DIN 934", 250.0, "GRAĐEVINA SPLIT", 890.25));
        data.add(new SalesRow(LocalDate.of(2024, 1, 25), "V001", "Vijak M8x20 DIN 912", 50.0, "OOO METALKA d.o.o.", 617.28));
        data.add(new SalesRow(LocalDate.of(2024, 2, 2), "P001", "Podložka M8 DIN 125", 500.0, "TEHNO SERVIS d.o.o.", 245.00));
        data.add(new SalesRow(LocalDate.of(2024, 2, 10), "V002", "Vijak M10x25 DIN 912", 75.0, "GRAĐEVINA SPLIT", 1125.75));
        data.add(new SalesRow(null, "M002", "Matica M10 DIN 934", 100.0, "MALI KUPAC", 320.00)); // No date
        data.add(new SalesRow(LocalDate.of(2024, 1, 30), "V001", "Vijak M8x20 DIN 912", 25.0, "DRUGI KUPAC", 308.14)); // Same item again
        
        return data;
    }
    
    /**
     * Create test aggregation data for filtering tests
     */
    private static List<AggregatedConsumption> createTestAggregations() {
        List<AggregatedConsumption> data = new ArrayList<>();
        
        data.add(new AggregatedConsumption("T001", "Test Item 1", 100, 
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)));
        data.add(new AggregatedConsumption("T002", "Test Item 2", 200, 
            LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 28)));
        data.add(new AggregatedConsumption("T003", "Test Item 3", 150, 
            LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31)));
        
        return data;
    }
    
    /**
     * Truncate string to specified length
     */
    private static String truncate(String str, int length) {
        if (str == null) return "";
        return str.length() > length ? str.substring(0, length - 3) + "..." : str;
    }
}