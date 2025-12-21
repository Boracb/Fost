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
            try {
				f.add(new ProductionInventoryPanel("jdbc:sqlite:fost.db"), BorderLayout.CENTER);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            f.setSize(1200, 700);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}