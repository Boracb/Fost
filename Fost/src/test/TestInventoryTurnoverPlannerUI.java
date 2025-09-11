package test;

import ui.InventoryTurnoverPlannerFrame;

import javax.swing.*;

/**
 * Simple test to verify the InventoryTurnoverPlannerFrame displays correctly
 */
public class TestInventoryTurnoverPlannerUI {
    
    public static void main(String[] args) {
        // Set the system look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // Use default look and feel
        }
        
        // Enable system properties for testing
        System.setProperty("fost.debugexcel", "1");
        System.setProperty("fost.excel.scanRows", "100");
        
        System.out.println("=== Testing Inventory Turnover Planner UI ===");
        System.out.println("Debug mode enabled - check console output during Excel import");
        System.out.println("Opening InventoryTurnoverPlannerFrame...");
        
        SwingUtilities.invokeLater(() -> {
            InventoryTurnoverPlannerFrame frame = new InventoryTurnoverPlannerFrame();
            frame.setVisible(true);
            
            // Add a close handler to exit when window is closed
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            
            System.out.println("UI opened successfully!");
            System.out.println("Use the 'Uvezi prodaju' button to test Excel import functionality");
        });
    }
}