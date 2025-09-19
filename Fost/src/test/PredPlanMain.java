package test;

import db.DatabaseHelper;
import ui.StatistikaPanel;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Small launcher for PredPlanManager to test computePredPlansBatch.
 */
public class PredPlanMain {
    public static void main(String[] args) {
        // Ensure DB (optional) and start UI on EDT
        DatabaseHelper.initializeDatabase();

        SwingUtilities.invokeLater(() -> {
            // Column names must match indices used in PredPlanManager
            String[] cols = new String[] {
                "datumNarudzbe",        // 0
                "predDatumIsporuke",    // 1
                "komitentOpis",         // 2
                "nazivRobe",            // 3
                "netoVrijednost",       // 4
                "kom",                  // 5
                "status",               // 6  <- STATUS_COL_MODEL = 6
                "djelatnik",            // 7
                "mm",                   // 8
                "m",                    // 9
                "tisucl",               // 10
                "m2",                   // 11 <- m2 column used by manager
                "startTime",            // 12
                "endTime",              // 13
                "duration",             // 14
                "predPlanIsporuke",     // 15 <- PRED_PLAN_COL = 15
                "trgovackiPredstavnik"  // 16
            };

            DefaultTableModel model = new DefaultTableModel(cols, 0);

            // Add some example rows (datumNarudzbe, predDatumIsporuke, komitentOpis, nazivRobe, neto, kom, status, ..., m2, ...)
            model.addRow(new Object[] { "2025-08-20", "2025-08-28", "Kom1", "Proizvod A", 100.0, 10, "u izradi", "", 0,0,0, 120.0, "", "", "", "", "" });
            model.addRow(new Object[] { "2025-08-21", "2025-09-01", "Kom2", "Proizvod B", 200.0, 5, "u izradi", "", 0,0,0, 80.0, "", "", "", "", "" });
            model.addRow(new Object[] { "2025-08-01", "2025-08-15", "Kom3", "Proizvod C", 150.0, 3, "Izrađeno", "", 0,0,0, 50.0, "", "", "", "", "" });

            JTable table = new JTable(model);
            table.setFillsViewportHeight(true);

            // Create StatistikaPanel (use same model and a default m2/h, e.g. 10)
            StatistikaPanel statsPanel = new StatistikaPanel(model, 10.0);

            // Create PredPlanManager
            PredPlanManager manager = new PredPlanManager(model, table, statsPanel);

            // Build UI
            JFrame frame = new JFrame("PredPlan Test");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());

            // Center: split pane with table and stats
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    new JScrollPane(table), statsPanel);
            split.setResizeWeight(0.7);
            frame.add(split, BorderLayout.CENTER);

            // South: control buttons
            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton btnComputeNow = new JButton("Compute now (forced)");
            btnComputeNow.addActionListener(e -> {
                // immediate forced compute (will print ENTRY/EXIT logs)
                manager.computeNow();
            });
            JButton btnSchedule = new JButton("Schedule (debounced)");
            btnSchedule.addActionListener(e -> {
                // schedule a debounced compute
                manager.scheduleDebouncedCompute();
            });
            bottom.add(btnComputeNow);
            bottom.add(btnSchedule);
            frame.add(bottom, BorderLayout.SOUTH);

            frame.setSize(1000, 600);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}