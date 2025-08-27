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
import java.util.Map;

public class StatistikaPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JButton btnRefresh = new JButton("🔄 Osvježi statistiku");
    private final JButton btnExport = new JButton("📤 Izvezi u Excel");

    private final DefaultTableModel model;
    private double m2PoSatu;
    private Map<String, Object> lastStats;
    private JTable statsTable;
    private StatsTableModel statsTableModel;

    private static final DecimalFormat THOUSANDS_FORMAT = new DecimalFormat("#,##0");
    private static final DecimalFormat THOUSANDS_2DEC_FORMAT = new DecimalFormat("#,##0.00");

    public StatistikaPanel(DefaultTableModel model, double m2PoSatu) {
        this.model = model;
        this.m2PoSatu = m2PoSatu;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        statsTableModel = new StatsTableModel();
        statsTable = new JTable(statsTableModel);
        statsTable.setRowHeight(32);
        statsTable.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        statsTable.setDefaultRenderer(Object.class, new StatsTableCellRenderer());

        add(new JScrollPane(statsTable), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnPanel.add(btnRefresh); btnPanel.add(btnExport);
        add(btnPanel, BorderLayout.SOUTH);

        btnRefresh.addActionListener(e -> updateStatsAsync());
        btnExport.addActionListener(e -> exportToExcel());

        updateStatsAsync();
    }
    private String fmt0(Object o) {
        if (o instanceof Number) return THOUSANDS_FORMAT.format(((Number) o).doubleValue());
        return "";
    }

    private String fmt2(Object o) {
        if (o instanceof Number) return THOUSANDS_2DEC_FORMAT.format(((Number) o).doubleValue());
        return "";
    }

    private void exportToExcel() {
        if (lastStats == null) {
            JOptionPane.showMessageDialog(this, "Nema podataka za izvoz.", "Upozorenje", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("statistika.xlsx"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("Statistika");
                int row = 0;
                for (Map.Entry<String, Object> e : lastStats.entrySet()) {
                    Row r = sheet.createRow(row++);
                    r.createCell(0).setCellValue(e.getKey());
                    r.createCell(1).setCellValue(
                        e.getValue() instanceof Number ?
                        ((Number) e.getValue()).doubleValue() : 0
                    );
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
                return ProductionStatsCalculator.calculate(model, m2PoSatu);
            }

            @Override
            protected void done() {
                try {
                    lastStats = get();
                    statsTableModel.updateStats(lastStats);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                        StatistikaPanel.this,
                        "Greška: " + ex.getMessage(),
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
    private class StatsTableModel extends AbstractTableModel {
        private final String[] columns = {"Opis", "Vrijednost"};
        private Object[][] rows = new Object[0][2];

        public void updateStats(Map<String, Object> stats) {
            LinkedHashMap<String, Object[]> data = new LinkedHashMap<>();

            // 📊 UKUPNO
            data.put("📊 UKUPNO", new Object[]{null, null});
            data.put("Ukupno kom:", new Object[]{fmt0(stats.getOrDefault(ProductionStatsCalculator.KOM, 0)), null});
            data.put("Ukupno m2:", new Object[]{fmt2(stats.getOrDefault(ProductionStatsCalculator.M2, 0)), null});
            data.put("Ukupna neto vrijednost (€):", new Object[]{fmt2(stats.getOrDefault(ProductionStatsCalculator.NETO, 0)), null});
            data.put("Kapacitet m2 po danu:", new Object[]{fmt2(stats.getOrDefault(ProductionStatsCalculator.PROSJEK_M2_PO_DANU, 0)), null});

            // ✅ IZRAĐENO
            data.put("✅ IZRAĐENO", new Object[]{null, null});
            data.put("Kom:", new Object[]{fmt0(stats.getOrDefault(ProductionStatsCalculator.KOM_IZR, 0)), null});
            data.put("m2:", new Object[]{fmt2(stats.getOrDefault(ProductionStatsCalculator.M2_IZR, 0)), null});
            data.put("Neto (€):", new Object[]{fmt2(stats.getOrDefault(ProductionStatsCalculator.NETO_IZR, 0)), null});

            // 🛠 ZA IZRADITI
            data.put("🛠 ZA IZRADITI", new Object[]{null, null});
            data.put("Kom:", new Object[]{fmt0(stats.getOrDefault(ProductionStatsCalculator.KOM_ZAI, 0)), null});
            data.put("m2:", new Object[]{fmt2(stats.getOrDefault(ProductionStatsCalculator.M2_ZAI, 0)), null});
            data.put("Neto (€):", new Object[]{fmt2(stats.getOrDefault(ProductionStatsCalculator.NETO_ZAI, 0)), null});

            // 📅 DANI ZA IZRADU
            data.put("📅 DANI ZA IZRADU", new Object[]{null, null});
            data.put("Kalendarski dani preostalo:", new Object[]{fmt2(stats.getOrDefault(ProductionStatsCalculator.KAL_DANI_PREOSTALO, 0)), null});
            data.put("Radni dani preostalo:", new Object[]{fmt2(stats.getOrDefault(ProductionStatsCalculator.RADNI_DANI_PREOSTALO, 0)), null});

            rows = new Object[data.size()][2];
            int i = 0;
            for (Map.Entry<String, Object[]> entry : data.entrySet()) {
                rows[i][0] = entry.getKey();
                rows[i][1] = entry.getValue()[0];
                i++;
            }
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.length; }
        @Override public int getColumnCount() { return columns.length; }
        @Override public Object getValueAt(int rowIndex, int columnIndex) { return rows[rowIndex][columnIndex]; }
        @Override public String getColumnName(int column) { return columns[column]; }
    }

    private class StatsTableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                      boolean isSelected, boolean hasFocus,
                                                      int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String label = (String) table.getValueAt(row, 0);
            if (label != null && (label.startsWith("📊") || label.startsWith("✅") || label.startsWith("🛠") || label.startsWith("📅"))) {
                c.setFont(new Font("Segoe UI", Font.BOLD, 18));
                c.setBackground(new Color(220, 230, 250));
            } else {
                c.setBackground(Color.WHITE);
            }
            return c;
        }
    }
}
