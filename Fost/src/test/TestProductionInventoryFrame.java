package test;

import javax.swing.*;

import ui.ProductionInventoryPanel;

import java.awt.*;

public class TestProductionInventoryFrame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Production Inventory");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setLayout(new BorderLayout());
            f.add(new ProductionInventoryPanel("jdbc:sqlite:fost.db"), BorderLayout.CENTER);
            f.setSize(1200, 700);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}