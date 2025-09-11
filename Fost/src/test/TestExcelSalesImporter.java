package test;

import excel.SalesAggregator;
import excel.SalesAggregator.SalesRow;
import model.AggregatedConsumption;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple test for the Excel Sales Importer functionality.
 */
public class TestExcelSalesImporter {
    
    public static void main(String[] args) {
        System.out.println("=== Test Excel Sales Importer ===");
        
        testSalesAggregation();
        testAggregatedConsumption();
        
        System.out.println("=== All tests completed successfully! ===");
    }
    
    /**
     * Test the SalesAggregator functionality
     */
    private static void testSalesAggregation() {
        System.out.println("\n--- Testing SalesAggregator ---");
        
        List<SalesRow> salesRows = new ArrayList<>();
        
        // Add some test data
        salesRows.add(new SalesRow(LocalDate.of(2024, 1, 15), "A001", "Vijak M8x20", 100.0, "Komitent A", 500.0));
        salesRows.add(new SalesRow(LocalDate.of(2024, 1, 20), "A001", "Vijak M8x20", 50.0, "Komitent B", 250.0));
        salesRows.add(new SalesRow(LocalDate.of(2024, 2, 10), "B002", "Matica M8", 200.0, "Komitent A", 400.0));
        salesRows.add(new SalesRow(null, null, "Vijak M10x25", 75.0, "Komitent C", 300.0)); // No date/code
        
        List<AggregatedConsumption> aggregated = SalesAggregator.aggregateConsumption(salesRows);
        
        System.out.printf("Aggregated %d sales rows into %d consumption items:%n", 
            salesRows.size(), aggregated.size());
        
        for (AggregatedConsumption item : aggregated) {
            System.out.printf("  %s (%s): %.2f kom, %.2f/dan, %.2f godišnje%n",
                item.getNaziv(), 
                item.getSifra() != null ? item.getSifra() : "bez šifre",
                item.getTotalQty(),
                item.getAvgPerDay(),
                item.getAnnualConsumption(250));
        }
    }
    
    /**
     * Test the AggregatedConsumption calculations
     */
    private static void testAggregatedConsumption() {
        System.out.println("\n--- Testing AggregatedConsumption ---");
        
        AggregatedConsumption consumption = new AggregatedConsumption(
            "TEST001", "Test Item", 300.0, 
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)
        );
        
        System.out.printf("Test item: %s%n", consumption.getNaziv());
        System.out.printf("Total quantity: %.2f%n", consumption.getTotalQty());
        System.out.printf("Date range: %s to %s%n", consumption.getMinDate(), consumption.getMaxDate());
        System.out.printf("Average per day: %.2f%n", consumption.getAvgPerDay());
        System.out.printf("Annual consumption (250 days): %.2f%n", consumption.getAnnualConsumption(250));
        
        // Test merging
        AggregatedConsumption other = new AggregatedConsumption(
            "TEST001", "Test Item", 150.0,
            LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 15)
        );
        
        consumption.addConsumption(other);
        
        System.out.println("\nAfter merging with additional 150.0 quantity:");
        System.out.printf("Total quantity: %.2f%n", consumption.getTotalQty());
        System.out.printf("Date range: %s to %s%n", consumption.getMinDate(), consumption.getMaxDate());
        System.out.printf("Average per day: %.2f%n", consumption.getAvgPerDay());
        System.out.printf("Annual consumption (250 days): %.2f%n", consumption.getAnnualConsumption(250));
    }
}