package ui;

import logic.ProductionStatsCalculator;
import logic.ProductionStatsCalculator.StartMode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
 * StatistikaPanel - verzija sa starim funkcijama + nove značajke:
 * - Stara logika (izrađeno se ne uračunava u preostalo) ostaje u calculate(DefaultTableModel, double)
 * - Novi odabir početka plana (od sada / sutra 07:00) koristi overload s StartMode
 * - Prikaz planStart/planEnd u tablici
 */
public class StatistikaPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JButton btnRefresh = new JButton("🔄 Osvježi statistiku");
    private final JButton btnExport = new JButton("📤 Izvezi u Excel");

    // Odabir početka plana
    private final JRadioButton rbStartNow = new JRadioButton("Kreni od sada");
    private final JRadioButton rbStartTomorrow = new JRadioButton("Kreni od sutra 07:00", true);

    private final DefaultTableModel sourceModel;
    private double m2PoSatu;
    // volatile radi sigurnosti između niti
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

        // Bez zaglavlja – prikaz se radi u redovima
        statsTable.setTableHeader(null);

        add(new JScrollPane(statsTable), BorderLayout.CENTER);

        // Panel za odabir početka plana
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        optionsPanel.add(new JLabel("Plan početak:"));
        ButtonGroup grp = new ButtonGroup();
        grp.add(rbStartNow);
        grp.add(rbStartTomorrow);
        optionsPanel.add(rbStartNow);
        optionsPanel.add(rbStartTomorrow);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        btnPanel.add(btnRefresh);
        btnPanel.add(btnExport);

        JPanel south = new JPanel(new BorderLayout());
        south.add(optionsPanel, BorderLayout.WEST);
        south.add(btnPanel, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        btnRefresh.addActionListener(e -> updateStatsAsync());
        btnExport.addActionListener(e -> exportToExcel());
        rbStartNow.addActionListener(e -> updateStatsAsync());
        rbStartTomorrow.addActionListener(e -> updateStatsAsync());

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
                // deterministički redoslijed – LinkedHashMap na izvoru
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
                StartMode mode = rbStartNow.isSelected() ? StartMode.NOW : StartMode.TOMORROW_7;
                return ProductionStatsCalculator.calculate(sourceModel, m2PoSatu, mode);
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> stats = get();
                    if (stats == null) stats = Map.of();
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

    // Ako statistika još nije izračunata, izračunaj sinkrono (prema trenutnom StartMode-u)
    private synchronized void ensureStatsAvailable() {
        if (lastStats != null) return;
        try {
            StartMode mode = rbStartNow.isSelected() ? StartMode.NOW : StartMode.TOMORROW_7;
            Map<String, Object> stats = ProductionStatsCalculator.calculate(sourceModel, m2PoSatu, mode);
            if (stats == null) stats = Map.of();
            lastStats = new LinkedHashMap<>(stats);
            SwingUtilities.invokeLater(() -> statsTableModel.updateStats(lastStats));
        } catch (Exception ex) {
            lastStats = Map.of();
        }
    }

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

    private class StatsTableModel extends AbstractTableModel {
        private final String[] columns = {"Opis", "Vrijednost"};
        private Object[][] rows = new Object[0][2];

        public void updateStats(Map<String, Object> stats) {
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();

            // snapshot za export
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

            // 🕒 PLAN
            data.put("🕒 PLAN", null);
            data.put("Početak plana:", stats.getOrDefault(ProductionStatsCalculator.PLAN_START, "-"));
            data.put("Procijenjeni završetak:", stats.getOrDefault(ProductionStatsCalculator.PLAN_END, "-"));

            rows = new Object[data.size()][2];
            int i = 0;
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                rows[i][0] = entry.getKey();
                Object val = entry.getValue();
                if (val == null) {
                    rows[i][1] = "";
                } else {
                    if (val instanceof Number) {
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
            SwingUtilities.invokeLater(this::fireTableDataChanged);
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
            if (label.startsWith("📊") || label.startsWith("✅") || label.startsWith("🛠") || label.startsWith("📅") || label.startsWith("🕒")) {
                c.setFont(new Font("Segoe UI", Font.BOLD, 15));
                c.setBackground(sectionBg);
            } else {
                c.setBackground(normalBg);
            }

            if (column == 0) {
                setHorizontalAlignment(LEFT);
            } else {
                setHorizontalAlignment(RIGHT);
            }
            if (c instanceof JComponent) ((JComponent) c).setOpaque(true);

            return c;
        }
    }
}