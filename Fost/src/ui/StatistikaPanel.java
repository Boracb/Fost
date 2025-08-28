package ui;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import logic.ProductionStatsCalculator;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.FileOutputStream;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * StatistikaPanel - ispravljena i poboljšana verzija:
 * - radi sigurno s null vrijednostima
 * - model nije editabilan
 * - export u Excel radi s tekstualnim vrijednostima ako nisu brojevi
 * - renderer poravnava vrijednosti i stilizira sekcije
 * - dodane pristupne metode getAvgDailyM2() i getTotalRemainingM2()
 *
 * Dodatak: ako statistika još nije izračunata, ensureStatsAvailable() izračunava je sinkrono
 * kako bi potrošači (npr. computePredPlansBatch) dobili konzistentne vrijednosti bez race-a.
 */
public class StatistikaPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JButton btnRefresh = new JButton("🔄 Osvježi statistiku");
    private final JButton btnExport = new JButton("📤 Izvezi u Excel");

    private final DefaultTableModel sourceModel;
    private double m2PoSatu;
    // volatile radi sigurnosti između niti (reader iz UI klase može čitati)
    private volatile Map<String, Object> lastStats;
    private JTable statsTable;
    private StatsTableModel statsTableModel;

    private static final DecimalFormat THOUSANDS_FORMAT = (DecimalFormat) DecimalFormat.getNumberInstance(Locale.US);
    private static final DecimalFormat THOUSANDS_2DEC_FORMAT = (DecimalFormat) DecimalFormat.getNumberInstance(Locale.US);

    public StatistikaPanel(DefaultTableModel model, double m2PoSatu) {
        this.sourceModel = model;
        this.m2PoSatu = m2PoSatu;

        THOUSANDS_FORMAT.applyPattern("#,##0");
        THOUSANDS_2DEC_FORMAT.applyPattern("#,##0.00");

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        statsTableModel = new StatsTableModel();
        statsTable = new JTable(statsTableModel);
        statsTable.setRowHeight(30);
        statsTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        statsTable.setShowGrid(false);
        statsTable.setIntercellSpacing(new Dimension(0, 0));
        statsTable.setFillsViewportHeight(true);
        statsTable.setDefaultRenderer(Object.class, new StatsTableCellRenderer());

        // Hide table header (we display label column inside rows)
        statsTable.setTableHeader(null);

        add(new JScrollPane(statsTable), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        btnPanel.add(btnRefresh);
        btnPanel.add(btnExport);
        add(btnPanel, BorderLayout.SOUTH);

        btnRefresh.addActionListener(e -> updateStatsAsync());
        btnExport.addActionListener(e -> exportToExcel());

        // Start async calculation but consumers can also trigger synchronous calculation if necessary
        updateStatsAsync();
    }

    public void setM2PoSatu(double m2PoSatu) {
        this.m2PoSatu = m2PoSatu;
    }

    private String fmt0(Object o) {
        if (o instanceof Number) return THOUSANDS_FORMAT.format(((Number) o).doubleValue());
        try {
            if (o != null) return THOUSANDS_FORMAT.format(Double.parseDouble(o.toString().replace(',', '.')));
        } catch (Exception ignored) {}
        return "0";
    }

    private String fmt2(Object o) {
        if (o instanceof Number) return THOUSANDS_2DEC_FORMAT.format(((Number) o).doubleValue());
        try {
            if (o != null) return THOUSANDS_2DEC_FORMAT.format(Double.parseDouble(o.toString().replace(',', '.')));
        } catch (Exception ignored) {}
        return "0.00";
    }

    private void exportToExcel() {
        Map<String, Object> snapshot = lastStats;
        if (snapshot == null || snapshot.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nema podataka za izvoz.", "Upozorenje", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("statistika.xlsx"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("Statistika");
                int row = 0;
                // deterministički redoslijed -> LinkedHashMap je korišten kod updateStats
                for (Map.Entry<String, Object> e : snapshot.entrySet()) {
                    Row r = sheet.createRow(row++);
                    r.createCell(0).setCellValue(e.getKey());
                    Object val = e.getValue();
                    if (val instanceof Number) {
                        r.createCell(1).setCellValue(((Number) val).doubleValue());
                    } else {
                        r.createCell(1).setCellValue(val == null ? "" : val.toString());
                    }
                }
                try (FileOutputStream out = new FileOutputStream(fc.getSelectedFile())) {
                    wb.write(out);
                }
                JOptionPane.showMessageDialog(this, "Podaci su uspješno izvezeni!", "Info", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Greška pri izvozu: " + ex.getMessage(), "Greška", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void updateStatsAsync() {
        btnRefresh.setEnabled(false);
        Cursor old = getCursor();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<Map<String, Object>, Void> worker = new SwingWorker<>() {
            @Override
            protected Map<String, Object> doInBackground() {
                return ProductionStatsCalculator.calculate(sourceModel, m2PoSatu);
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> stats = get();
                    if (stats == null) stats = Map.of();
                    // store as LinkedHashMap for deterministic iteration/order
                    lastStats = new LinkedHashMap<>(stats);
                    statsTableModel.updateStats(lastStats);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                        StatistikaPanel.this,
                        "Greška pri izračunu statistike: " + ex.getMessage(),
                        "Greška",
                        JOptionPane.ERROR_MESSAGE
                    );
                } finally {
                    btnRefresh.setEnabled(true);
                    setCursor(old);
                }
            }
        };
        worker.execute();
    }

    /**
     * Ako statistika još nije izračunata, poziva se sinkrono i postavlja lastStats.
     * Ovo je kratko i determinističko: koristi isti ProductionStatsCalculator koji se inače koristi.
     * Služi za slučajeve kada pozivatelj (npr. computePredPlansBatch) treba odmah dobiti prosjek.
     */
    private synchronized void ensureStatsAvailable() {
        if (lastStats != null) return;
        try {
            Map<String, Object> stats = ProductionStatsCalculator.calculate(sourceModel, m2PoSatu);
            if (stats == null) stats = Map.of();
            lastStats = new LinkedHashMap<>(stats);
            // update UI model on EDT
            SwingUtilities.invokeLater(() -> statsTableModel.updateStats(lastStats));
        } catch (Exception ex) {
            lastStats = Map.of();
        }
    }

    /**
     * Vrati prosjek m2/dan iz posljednjeg izračuna statistike.
     * Ako još nije izračunato, izračuna se sinkrono (ensureStatsAvailable).
     */
    public double getAvgDailyM2() {
        ensureStatsAvailable();
        if (lastStats == null) return 0.0;
        Object o = lastStats.get(ProductionStatsCalculator.PROSJEK_M2_PO_DANU);
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            if (o != null) return Double.parseDouble(o.toString().replace(',', '.'));
        } catch (Exception ignored) {}
        return 0.0;
    }

    /**
     * Vrati ukupno preostalo m2 (ZA IZRADITI) iz posljednjeg izračuna.
     * Ako još nije izračunato, izračuna se sinkrono.
     */
    public double getTotalRemainingM2() {
        ensureStatsAvailable();
        if (lastStats == null) return 0.0;
        Object o = lastStats.get(ProductionStatsCalculator.M2_ZAI);
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            if (o != null) return Double.parseDouble(o.toString().replace(',', '.'));
        } catch (Exception ignored) {}
        return 0.0;
    }

    // ===== inner model & renderer =====

    private class StatsTableModel extends AbstractTableModel {
        private final String[] columns = {"Opis", "Vrijednost"};
        private Object[][] rows = new Object[0][2];

        public void updateStats(Map<String, Object> stats) {
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();

            // store raw values into lastStats for export/consumption
            lastStats = new LinkedHashMap<>(stats);

            // 📊 UKUPNO
            data.put("📊 UKUPNO", null);
            data.put("Ukupno kom:", stats.getOrDefault(ProductionStatsCalculator.KOM, 0));
            data.put("Ukupno m2:", stats.getOrDefault(ProductionStatsCalculator.M2, 0));
            data.put("Ukupna neto vrijednost (€):", stats.getOrDefault(ProductionStatsCalculator.NETO, 0));
            data.put("Kapacitet m2 po danu:", stats.getOrDefault(ProductionStatsCalculator.PROSJEK_M2_PO_DANU, 0));

            // ✅ IZRAĐENO
            data.put("✅ IZRAĐENO", null);
            data.put("Kom (izrađeno):", stats.getOrDefault(ProductionStatsCalculator.KOM_IZR, 0));
            data.put("m2 (izrađeno):", stats.getOrDefault(ProductionStatsCalculator.M2_IZR, 0));
            data.put("Neto (izrađeno) (€):", stats.getOrDefault(ProductionStatsCalculator.NETO_IZR, 0));

            // 🛠 ZA IZRADITI
            data.put("🛠 ZA IZRADITI", null);
            data.put("Kom (za izraditi):", stats.getOrDefault(ProductionStatsCalculator.KOM_ZAI, 0));
            data.put("m2 (za izraditi):", stats.getOrDefault(ProductionStatsCalculator.M2_ZAI, 0));
            data.put("Neto (za izraditi) (€):", stats.getOrDefault(ProductionStatsCalculator.NETO_ZAI, 0));

            // 📅 DANI ZA IZRADU
            data.put("📅 DANI ZA IZRADU", null);
            data.put("Kalendarski dani preostalo:", stats.getOrDefault(ProductionStatsCalculator.KAL_DANI_PREOSTALO, 0));
            data.put("Radni dani preostalo:", stats.getOrDefault(ProductionStatsCalculator.RADNI_DANI_PREOSTALO, 0));

            rows = new Object[data.size()][2];
            int i = 0;
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                rows[i][0] = entry.getKey();
                Object val = entry.getValue();
                // Format values consistently as strings for display
                if (val == null) {
                    rows[i][1] = "";
                } else {
                    // Decide formatting based on key or numeric
                    if (val instanceof Number) {
                        // choose 0 or 2 decimals depending on key name (simple heuristic)
                        String key = entry.getKey().toLowerCase(Locale.ROOT);
                        if (key.contains("m2") || key.contains("kapacitet") || key.contains("neto") || key.contains("dani")) {
                            rows[i][1] = fmt2(val);
                        } else {
                            rows[i][1] = fmt0(val);
                        }
                    } else {
                        rows[i][1] = val.toString();
                    }
                }
                i++;
            }
            SwingUtilities.invokeLater(() -> {
                fireTableDataChanged();
            });
        }

        @Override public int getRowCount() { return rows.length; }
        @Override public int getColumnCount() { return columns.length; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= rows.length) return null;
            return rows[rowIndex][columnIndex];
        }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }
        @Override public Class<?> getColumnClass(int columnIndex) { return String.class; }
    }

    private class StatsTableCellRenderer extends DefaultTableCellRenderer {
        private final Color sectionBg = new Color(235, 243, 255);
        private final Color normalBg = Color.WHITE;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                      boolean isSelected, boolean hasFocus,
                                                      int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            Object labelObj = table.getValueAt(row, 0);
            String label = labelObj == null ? "" : labelObj.toString();

            c.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            if (label.startsWith("📊") || label.startsWith("✅") || label.startsWith("🛠") || label.startsWith("📅")) {
                c.setFont(new Font("Segoe UI", Font.BOLD, 15));
                c.setBackground(sectionBg);
            } else {
                c.setBackground(normalBg);
            }

            // Align first column left, second column right
            if (column == 0) {
                setHorizontalAlignment(LEFT);
            } else {
                setHorizontalAlignment(RIGHT);
            }

            // ensure opaque for background color to show
            if (c instanceof JComponent) ((JComponent) c).setOpaque(true);

            return c;
        }
    }
}