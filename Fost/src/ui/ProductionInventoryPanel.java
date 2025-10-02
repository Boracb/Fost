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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Prošireni panel (dodani dobavljači, obrtaj, narudžbe).
 */
public class ProductionInventoryPanel extends JPanel {

    private final ProductInventoryTableModel tableModel = new ProductInventoryTableModel();
    private final JTable table = new JTable(tableModel);

    private final InventoryService inventoryService;
    private final ImportService importService;

    // NEW: Procurement
    private final procurementService procurementService;
    private final ProductSupplierDao productSupplierDao;
    private final SupplierDao supplierDao;

    private final ConnectionProvider cp;

    // Filtri
    private Predicate<ProductInventoryView> activePredicate = v -> true;
    private String activeGroupFilter = null;
    private String activeSearchText = "";

    private final JTextField txtSearch = new JTextField(14);
    private final JLabel lblStatus = new JLabel(" ");
    private final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("hr","HR"));

    // NEW period combo
    private final JComboBox<String> cmbPeriod = new JComboBox<>(new String[]{"1M","3M","6M","12M"});

    public ProductionInventoryPanel(String dbUrl) {
        setLayout(new BorderLayout());

        this.cp = new ConnectionProvider(dbUrl);
        var productDao = new ProductDao(cp);
        var invDao = new InventoryDao(cp);
        var groupDao = new ProductGroupDao(cp);
        this.productSupplierDao = new ProductSupplierDao(cp);
        this.supplierDao = new SupplierDao(cp);

        this.inventoryService = new InventoryService(invDao, productDao);

        ExcelProductInventoryReader reader = new ExcelProductInventoryReader()
                .withHeader(true)
                .enableDebug(false);

        this.importService = new ImportService(reader, productDao, invDao, groupDao);

        // NEW procurement service
        this.procurementService = new procurementService(invDao,
                new SalesDaoImpl(cp),
                productSupplierDao);

        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildStatusBar(), BorderLayout.SOUTH);

        hookSearchField();
        reload();
    }

    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton btnReload = new JButton("Osvježi");
        JButton btnImport = new JButton("Import");
        JButton btnSortSupplier = new JButton("Sort Dobavljač");
        JButton btnSortValue = new JButton("Sort Vrijednost");
        JButton btnFilterGroup = new JButton("Filter grupa");
        JButton btnClearFilter = new JButton("Poništi filter");
        JButton btnPlus = new JButton("+1");
        JButton btnMinus = new JButton("-1");
        JButton btnSetQty = new JButton("Postavi količinu");
        JButton btnTotal = new JButton("Ukupno");
        JLabel lblSearch = new JLabel("Traži:");

        // NEW
        JButton btnSuppliers = new JButton("Dobavljači");
        JButton btnAssign = new JButton("Dodjela dobavljača");
        JButton btnTurnover = new JButton("Obrtaj");
        JButton btnOrders = new JButton("Narudžbe");
        JLabel lblPeriod = new JLabel("Period:");

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

        // NEW group
        bar.add(lblPeriod);
        bar.add(cmbPeriod);
        bar.add(btnTurnover);
        bar.add(btnOrders);
        bar.add(btnSuppliers);
        bar.add(btnAssign);

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

        // NEW actions
        btnSuppliers.addActionListener(e -> new SuppliersDialog(SwingUtilities.getWindowAncestor(this), cp).setVisible(true));
        btnAssign.addActionListener(e -> openAssignDialog());
        btnTurnover.addActionListener(e -> showTurnoverForSelected());
        btnOrders.addActionListener(e -> generateOrders());

        return bar;
    }

    private JComponent buildStatusBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(lblStatus, BorderLayout.WEST);
        lblStatus.setBorder(BorderFactory.createEmptyBorder(2,6,2,6));
        return p;
    }

    private void hookSearchField() {
        txtSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateSearch(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateSearch(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateSearch(); }
        });
    }

    private void updateSearch() {
        activeSearchText = txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        applyCombinedFilter();
    }

    private void filterByGroup() {
        String g = JOptionPane.showInputDialog(this, "Unesi kod grupe:");
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
        java.util.function.Predicate<ProductInventoryView> p = v -> true;

        if (!activeSearchText.isBlank()) {
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
        updateStatus("Filtrirano: " + table.getRowCount());
    }

    private void reload() {
        try {
            tableModel.setData(inventoryService.fullView());
            tableModel.applyFilter(activePredicate);
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
        int vr = table.getSelectedRow();
        if (vr < 0) {
            JOptionPane.showMessageDialog(this, "Ništa nije odabrano.");
            return null;
        }
        int mr = table.convertRowIndexToModel(vr);
        return tableModel.getAt(mr);
    }

    private void adjustSelectedQuantity(double delta) {
        var v = getSelectedView();
        if (v == null) return;
        try {
            inventoryService.adjustQuantity(v.getProduct().getProductCode(), delta);
            reloadSelecting(v.getProduct().getProductCode());
        } catch (Exception ex) {
            showError("Ne mogu prilagoditi: " + ex.getMessage(), ex);
        }
    }

    private void setSelectedQuantity() {
        var v = getSelectedView();
        if (v == null) return;
        String code = v.getProduct().getProductCode();
        String s = JOptionPane.showInputDialog(this, "Nova količina za " + code,
                v.getInventory().getQuantity());
        if (s == null) return;
        try {
            double q = Double.parseDouble(s.replace(',', '.').trim());
            inventoryService.setQuantity(code, q);
            reloadSelecting(code);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Neispravan broj.");
        } catch (Exception ex) {
            showError("Greška: " + ex.getMessage(), ex);
        }
    }

    private void reloadSelecting(String code) {
        reload();
        for (int r = 0; r < table.getRowCount(); r++) {
            int mr = table.convertRowIndexToModel(r);
            var piv = tableModel.getAt(mr);
            if (piv != null && code.equals(piv.getProduct().getProductCode())) {
                table.getSelectionModel().setSelectionInterval(r, r);
                table.scrollRectToVisible(table.getCellRect(r,0,true));
                break;
            }
        }
    }

    private void showTotalValue() {
        double total = 0.0;
        for (int r = 0; r < table.getRowCount(); r++) {
            int mr = table.convertRowIndexToModel(r);
            var piv = tableModel.getAt(mr);
            if (piv != null && piv.getTotalValue() != null) total += piv.getTotalValue();
        }
        JOptionPane.showMessageDialog(this,
                "Ukupna vrijednost (filtrirano): " + nf.format(total));
    }

    // NEW – turnover
    private void showTurnoverForSelected() {
        var v = getSelectedView();
        if (v == null) return;
        try {
            var pm = procurementService.PeriodMonths.fromLabel((String) cmbPeriod.getSelectedItem());
            var res = procurementService.computeTurnover(v.getProduct().getProductCode(), pm);
            JOptionPane.showMessageDialog(this,
                    "Prodano: " + res.salesQty + "\n" +
                    "Prosj. zaliha (aproks.): " + res.avgQty + "\n" +
                    "Obrtaj (" + pm.months + "M): " + res.turnover,
                    "Obrtaj", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showError("Greška obrtaj: " + ex.getMessage(), ex);
        }
    }

    // NEW – order suggestions
    private void generateOrders() {
        try {
            var pm = procurementService.PeriodMonths.fromLabel((String) cmbPeriod.getSelectedItem());
            var list = procurementService.suggestOrders(pm, 30, 0.2); // coverage 30 dana, safety 20%
            if (list.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nema prijedloga.");
                return;
            }
            new OrderSuggestionsDialog(SwingUtilities.getWindowAncestor(this), list).setVisible(true);
        } catch (Exception ex) {
            showError("Greška narudžbe: " + ex.getMessage(), ex);
        }
    }

    // NEW – assign dialog
    private void openAssignDialog() {
        var v = getSelectedView();
        if (v == null) return;
        new ProductSupplierAssignDialog(
                SwingUtilities.getWindowAncestor(this),
                cp,
                v.getProduct().getProductCode()
        ).setVisible(true);
    }

    private void updateStatus(String m) { lblStatus.setText(m); }

    private void showError(String msg, Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, msg, "Greška", JOptionPane.ERROR_MESSAGE);
        updateStatus("Greška: " + msg);
    }
}