package ui;

import dao.ConnectionProvider;
import dao.InventoryDao;
import dao.ProductDao;
import dao.ProductGroupDao;
import excel.ExcelProductInventoryReader;
import model.ProductInventoryView;
import service.ImportService;
import service.InventoryService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * ProductionInventoryPanel
 *
 * Feature-rich inventory UI panel for production planning / stock monitoring.
 * Provides:
 *  - Reload data
 *  - Excel import
 *  - Sorting (supplier, total value)
 *  - Text search (code or name)
 *  - Group filtering
 *  - Quantity adjustments (+ / - / set)
 *  - Total value display
 *
 * Depends on ProductInventoryTableModel methods:
 *   setData(List), applyFilter(Predicate), sortBy(Comparator), getAt(int)
 * and ProductInventoryView methods:
 *   getProduct(), getInventory(), getGroupCodes(), getTotalValue()
 */
public class ProductionInventoryPanel extends JPanel {

    private final ProductInventoryTableModel tableModel = new ProductInventoryTableModel();
    private final JTable table = new JTable(tableModel);

    private final InventoryService inventoryService;
    private final ImportService importService;

    // Current filter state
    private Predicate<ProductInventoryView> activePredicate = v -> true;
    private String activeGroupFilter = null;
    private String activeSearchText = "";

    private final JTextField txtSearch = new JTextField(16);
    private final JLabel lblStatus = new JLabel(" ");
    private final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("hr", "HR"));

    public ProductionInventoryPanel(String dbUrl) {
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

        // TABLE CONFIG
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Optional additional Swing sorting (on top of model sorts)
        TableRowSorter<ProductInventoryTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildStatusBar(), BorderLayout.SOUTH);

        hookSearchField();
        reload();
    }

    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton btnReload = new JButton("Osvježi");
        JButton btnImport = new JButton("Import Excel");
        JButton btnSortSupplier = new JButton("Sort: Dobavljač");
        JButton btnSortValue = new JButton("Sort: Vrijednost");
        JButton btnFilterGroup = new JButton("Filter grupa");
        JButton btnClearFilter = new JButton("Poništi filter");
        JButton btnPlus = new JButton("+1");
        JButton btnMinus = new JButton("-1");
        JButton btnSetQty = new JButton("Postavi količinu");
        JButton btnTotal = new JButton("Ukupna vrijednost");
        JLabel lblSearch = new JLabel("Traži:");

        bar.add(btnReload);
        bar.add(btnImport);
        bar.add(btnSortSupplier);
        bar.add(btnSortValue);
        bar.add(btnFilterGroup);
        bar.add(btnClearFilter);
        bar.add(lblSearch);
        bar.add(txtSearch);
        bar.add(btnPlus);
        bar.add(btnMinus);
        bar.add(btnSetQty);
        bar.add(btnTotal);

        btnReload.addActionListener(e -> reload());
        btnImport.addActionListener(e -> importExcel());
        btnSortSupplier.addActionListener(e -> sortBySupplier());
        btnSortValue.addActionListener(e -> sortByValue());
        btnFilterGroup.addActionListener(e -> filterByGroup());
        btnClearFilter.addActionListener(e -> clearFilters());
        btnPlus.addActionListener(e -> adjustSelectedQuantity(+1));
        btnMinus.addActionListener(e -> adjustSelectedQuantity(-1));
        btnSetQty.addActionListener(e -> setSelectedQuantity());
        btnTotal.addActionListener(e -> showTotalValue());

        return bar;
    }

    private JComponent buildStatusBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(lblStatus, BorderLayout.WEST);
        lblStatus.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return p;
    }

    private void hookSearchField() {
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateSearch(); }
            @Override public void removeUpdate(DocumentEvent e) { updateSearch(); }
            @Override public void changedUpdate(DocumentEvent e) { updateSearch(); }
        });
    }

    private void updateSearch() {
        activeSearchText = txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        applyCombinedFilter();
    }

    private void filterByGroup() {
        String g = JOptionPane.showInputDialog(this, "Unesi kod grupe (prazno = odustani):");
        if (g == null || g.isBlank()) return;
        activeGroupFilter = g.trim().toLowerCase(Locale.ROOT);
        applyCombinedFilter();
    }

    private void clearFilters() {
        activeSearchText = "";
        activeGroupFilter = null;
        txtSearch.setText("");
        applyCombinedFilter();
    }

    private void applyCombinedFilter() {
        Predicate<ProductInventoryView> p = v -> true;

        if (activeSearchText != null && !activeSearchText.isBlank()) {
            String s = activeSearchText;
            p = p.and(v -> {
                String code = Optional.ofNullable(v.getProduct().getProductCode()).orElse("").toLowerCase(Locale.ROOT);
                String name = Optional.ofNullable(v.getProduct().getName()).orElse("").toLowerCase(Locale.ROOT);
                return code.contains(s) || name.contains(s);
            });
        }

        if (activeGroupFilter != null && !activeGroupFilter.isBlank()) {
            String g = activeGroupFilter;
            p = p.and(v -> v.getGroupCodes().stream()
                    .filter(Objects::nonNull)
                    .map(x -> x.toLowerCase(Locale.ROOT))
                    .anyMatch(x -> x.contains(g)));
        }

        activePredicate = p;
        tableModel.applyFilter(activePredicate);
        updateStatus("Filtrirano: " + table.getRowCount() + " artikala");
    }

    private void reload() {
        try {
            tableModel.setData(inventoryService.fullView());
            tableModel.applyFilter(activePredicate); // re-apply filter
            updateStatus("Učitano: " + table.getRowCount());
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
        tableModel.sortBy(
                Comparator.comparing(
                        (ProductInventoryView v) -> {
                            String sc = v.getProduct().getSupplierCode();
                            return sc == null ? "" : sc;
                        }
                ).thenComparing(v -> v.getProduct().getProductCode())
        );
    }

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

    private ProductInventoryView getSelectedView() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            showInfo("Ništa nije odabrano.");
            return null;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        return tableModel.getAt(modelRow); // FIX: was getRow(...)
    }

    private void adjustSelectedQuantity(double delta) {
        ProductInventoryView piv = getSelectedView();
        if (piv == null) return;
        String code = piv.getProduct().getProductCode();
        try {
            inventoryService.adjustQuantity(code, delta);
            reloadSelecting(code);
        } catch (Exception e) {
            showError("Ne mogu prilagoditi količinu: " + e.getMessage(), e);
        }
    }

    private void setSelectedQuantity() {
        ProductInventoryView piv = getSelectedView();
        if (piv == null) return;
        String code = piv.getProduct().getProductCode();
        String s = JOptionPane.showInputDialog(this,
                "Nova količina za " + code,
                piv.getInventory().getQuantity());
        if (s == null) return;
        try {
            double q = Double.parseDouble(s.replace(',', '.').trim());
            inventoryService.setQuantity(code, q);
            reloadSelecting(code);
        } catch (NumberFormatException ex) {
            showInfo("Neispravan broj.");
        } catch (Exception ex) {
            showError("Greška pri postavljanju: " + ex.getMessage(), ex);
        }
    }

    private void reloadSelecting(String productCode) {
        reload();
        for (int r = 0; r < table.getRowCount(); r++) {
            int modelRow = table.convertRowIndexToModel(r);
            ProductInventoryView piv = tableModel.getAt(modelRow);
            if (piv != null && productCode.equals(piv.getProduct().getProductCode())) {
                table.getSelectionModel().setSelectionInterval(r, r);
                table.scrollRectToVisible(table.getCellRect(r, 0, true));
                break;
            }
        }
    }

    private void showTotalValue() {
        double total = 0.0;
        for (int r = 0; r < table.getRowCount(); r++) {
            int modelRow = table.convertRowIndexToModel(r);
            ProductInventoryView piv = tableModel.getAt(modelRow);
            if (piv != null && piv.getTotalValue() != null) {
                total += piv.getTotalValue();
            }
        }
        JOptionPane.showMessageDialog(this,
                "Ukupna vrijednost (filtrirano): " + nf.format(total),
                "Total",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateStatus(String msg) {
        lblStatus.setText(msg);
    }

    private void showError(String msg, Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, msg, "Greška", JOptionPane.ERROR_MESSAGE);
        updateStatus("Greška: " + msg);
    }

    private void showInfo(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
        updateStatus(msg);
    }
}