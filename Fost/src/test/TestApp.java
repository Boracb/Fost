package test;
import ui.StatistikaPanel;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class TestApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Kreiramo testni model s kolonama
            String[] columns = {
                "ID", "Datum", "Col2", "Col3", "Neto €", "Kom", "Status",
                "Col7", "Col8", "Col9", "Col10", "m²"
            };
            DefaultTableModel model = new DefaultTableModel(columns, 0);

            // Dodajemo par redaka: izrađeno i u izradi
            model.addRow(new Object[]{
                1, "2025-08-20", null, null, 100.0, 5.0, "Izrađeno",
                null, null, null, null, 12.5
            });
            model.addRow(new Object[]{
                2, "2025-08-22", null, null, 200.0, 8.0, "U izradi",
                null, null, null, null, 20.0
            });
            model.addRow(new Object[]{
                3, "2025-08-23", null, null, 50.0, 2.0, "",
                null, null, null, null, 5.0
            });

            JFrame frame = new JFrame("Test StatistikaPanel");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);

            // m2PoSatu neka bude npr. 10
            frame.add(new StatistikaPanel(model, 10), BorderLayout.CENTER);

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
