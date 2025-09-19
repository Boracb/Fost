package ui;

import dao.*;
import excel.ExcelProductInventoryReader;
import model.ProductInventoryView;
import service.ImportService;
import service.InventoryService;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Predicate;

public class ProductInventoryPanel extends JPanel {

    private final ProductInventoryTableModel tableModel = new ProductInventoryTableModel();
    private final JTable table = new JTable(tableModel);

    private final InventoryService inventoryService;
    private final ImportService importService;

    public ProductInventoryPanel(String dbUrl) {
        setLayout(new BorderLayout());

        var cp = new ConnectionProvider(dbUrl);
        var productDao = new ProductDao(cp);
        var invDao = new InventoryDao(cp);
        var groupDao = new ProductGroupDao(cp);

        this.inventoryService = new InventoryService(invDao, productDao);

        ExcelProductInventoryReader reader = new ExcelProductInventoryReader()
                .withHeader(true)
                .enableDebug(false);

        this.importService = new ImportService(reader, productDao, invDao, groupDao);

        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnReload = new JButton("Osvježi");
        JButton btnImport = new JButton("Import Excel");
        JButton btnSortSupplier = new JButton("Sort: Dobavljač");
        JButton btnSortValue = new JButton("Sort: Vrijednost");
        JButton btnFilterGroup = new JButton("Filter grupa...");
        JButton btnTotal = new JButton("Ukupna vrijednost");

        top.add(btnReload);
        top.add(btnImport);
        top.add(btnSortSupplier);
        top.add(btnSortValue);
        top.add(btnFilterGroup);
        top.add(btnTotal);

        add(top, BorderLayout.NORTH);

        btnReload.addActionListener(e -> reload());
        btnImport.addActionListener(e -> importExcel());
        btnSortSupplier.addActionListener(e -> sortBySupplier());
        btnSortValue.addActionListener(e -> sortByValue());
        btnFilterGroup.addActionListener(e -> filterByGroupDialog());
        btnTotal.addActionListener(e -> showTotalValue());

        reload();
    }

    private void reload() {
        try {
            tableModel.setData(inventoryService.fullView());
        } catch (SQLException ex) {
            showError("Greška kod čitanja: " + ex.getMessage(), ex);
        }
    }

    private void importExcel() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                importService.fullImport(f);
                reload();
                JOptionPane.showMessageDialog(this, "Import gotov.");
            } catch (Exception ex) {
                showError("Import nije uspio: " + ex.getMessage(), ex);
            }
        }
    }

    private void sortBySupplier() {
        tableModel.sortBy(Comparator.comparing(
                (ProductInventoryView v) -> v.getProduct().getSupplierCode() == null ? "" : v.getProduct().getSupplierCode()
        ).thenComparing(v -> v.getProduct().getProductCode()));
    }

    // ISPRAVKA – eksplicitni tip u lambdi da ne “padne” na Comparator<Object>
    private void sortByValue() {
        tableModel.sortBy(
                Comparator.comparingDouble(
                        (ProductInventoryView v) -> {
                            Double val = v.getTotalValue();
                            return val != null ? val : 0.0;
                        }
                ).reversed()
        );
    }

    private void filterByGroupDialog() {
        String g = JOptionPane.showInputDialog(this, "Unesi kod grupe (npr. tiskana_traka):");
        if (g == null || g.isBlank()) {
            tableModel.applyFilter(t -> true);
            return;
        }
        String needle = g.trim().toLowerCase(Locale.ROOT);
        Predicate<ProductInventoryView> pred =
                piv -> piv.getGroupCodes().stream()
                        .anyMatch(code -> code.equalsIgnoreCase(needle));
        tableModel.applyFilter(pred);
    }

    private void showTotalValue() {
        try {
            double sum = inventoryService.totalValue();
            NumberFormat nf = NumberFormat.getNumberInstance(new Locale("hr","HR"));
            nf.setMaximumFractionDigits(2);
            JOptionPane.showMessageDialog(this,
                    "Ukupna vrijednost: " + nf.format(sum),
                    "Suma", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showError("Ne mogu izračunati: " + ex.getMessage(), ex);
        }
    }

    private void showError(String msg, Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, msg, "Greška", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Proizvodi & Stanje (Var. B)");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(new ProductInventoryPanel("jdbc:sqlite:fost.db"));
            f.setSize(1400, 720);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}