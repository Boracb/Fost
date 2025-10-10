package ui;

import service.ProductService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.List;

/**
 * Dijalog prikaza prijedloga narudžbi.
 */
public class OrderSuggestionsDialog extends JDialog {

    private final List<ProductService.OrderSuggestion> suggestions;
    private final JTable table;

    public OrderSuggestionsDialog(Window owner,
                                  List<ProductService.OrderSuggestion> suggestions) {
        super(owner, "Prijedlog narudžbi (" + suggestions.size() + ")", ModalityType.APPLICATION_MODAL);
        this.suggestions = suggestions;
        this.table = new JTable(new SuggestionTableModel(suggestions));

        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        pack();
        setSize(1000, 480);
        setLocationRelativeTo(owner);
    }

    private JComponent buildButtons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnExport = new JButton("Export CSV");
        JButton btnClose = new JButton("Zatvori");
        p.add(btnExport);
        p.add(btnClose);

        btnExport.addActionListener(e -> exportCsv());
        btnClose.addActionListener(e -> dispose());

        return p;
    }

    private void exportCsv() {
        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        var file = fc.getSelectedFile();
        try (java.io.PrintWriter pw = new java.io.PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
            pw.println("Šifra;Dobavljač;Trenutno;DnevnaPotražnja;Lead(d);ReorderPoint;SafetyStock;Predloženo;MinNar;CoverageDana");
            for (var s : suggestions) {
                pw.printf("%s;%s;%.2f;%.2f;%d;%.2f;%.2f;%.2f;%s;%d%n",
                        s.productCode,
                        s.supplierCode,
                        s.currentQty,
                        s.dailyDemand,
                        s.leadTimeDays,
                        s.reorderPoint,
                        s.safetyStock,
                        s.suggestedQty,
                        s.minOrderQty == null ? "" : s.minOrderQty,
                        s.coverageDays
                );
            }
            JOptionPane.showMessageDialog(this, "Exportirano: " + file.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Greška export: " + ex.getMessage(),
                    "Greška", JOptionPane.ERROR_MESSAGE);
        }
    }

    /* ---------------- Table model ---------------- */
    private static class SuggestionTableModel extends AbstractTableModel {
        private final String[] cols = {
                "Šifra","Dobavljač","Trenutno","Dnevna potr.","Lead(d)","ROP",
                "Safety","Predloženo","Min nar.","Coverage (d)"
        };
        private final List<ProductService.OrderSuggestion> data;
        public SuggestionTableModel(List<ProductService.OrderSuggestion> data) {
            this.data = data;
        }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int column) { return cols[column]; }
        @Override public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 2,3,5,6,7 -> Double.class;
                case 4,9 -> Integer.class;
                default -> String.class;
            };
        }
        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            var s = data.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> s.productCode;
                case 1 -> s.supplierCode;
                case 2 -> s.currentQty;
                case 3 -> s.dailyDemand;
                case 4 -> s.leadTimeDays;
                case 5 -> s.reorderPoint;
                case 6 -> s.safetyStock;
                case 7 -> s.suggestedQty;
                case 8 -> s.minOrderQty;
                case 9 -> s.coverageDays;
                default -> null;
            };
        }
    }
}