package ui;

import dao.ConnectionProvider;
import dao.InventoryDao;
import dao.ProductDao;
import dao.ProductGroupDao;
import dao.ProductSupplierDao;
import dao.SalesDaoImpl;
import dao.SupplierDao;
import excel.ExcelProductInventoryReader;
import model.ProductInventoryView;
import service.ImportService;
import service.InventoryService;
import service.ProductService;
import service.SalesImportService;
import service.SalesMaintenanceService;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.io.File;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * ProductionInventoryPanel
 *
 * STARE FUNKCIONALNOSTI:
 *  - Prikaz artikala i zaliha
 *  - Import artikala/početnih zaliha
 *  - Sortiranje po dobavljaču / vrijednosti
 *  - Filtriranje po grupi + tekst pretraga
 *  - Povećanje/smanjenje/postavljanje količine
 *  - Prikaz ukupne (filtrirane) vrijednosti
 *  - Dodjela dobavljača, pregled dobavljača
 *
 * NOVE FUNKCIONALNOSTI:
 *  - Import prodaje (Excel) + agregirana prodaja po periodu (1M,3M,6M,12M)
 *  - Kolone "Prodaja (period)" i "Obrtaj (period)"
 *  - Dodatni gumb “Obrtaj” (dijalog sa detaljima + dnevna potražnja)
 *  - Gumb “Narudžbe” (prijedlog narudžbi prema odabranom periodu i safety faktoru)
 *  - Brisanje svih zaliha / svih prodaja
 *  - Donji (drugi) toolbar s toggle kontrolama za kolone, export CSV itd.
 *
 * OČEKIVANJA:
 *  - ProductInventoryTableModel mora imati kolone Prodaja (period) i Obrtaj (period) (indeksi 12 i 13)
 *  - InventoryService mora pružiti fullViewWithSales(from,to)
 *  - ProductService mora imati suggestOrdersForPeriod(pm, safetyFactor) (vidi raniji patch)
 */
public class ProductionInventoryPanel extends JPanel {

    private final ProductInventoryTableModel tableModel = new ProductInventoryTableModel();
    private final JTable table = new JTable(tableModel);

    private final InventoryService inventoryService;
    private final ImportService importService;
    private final ProductService productService;

    private final ProductSupplierDao productSupplierDao;
    private final SupplierDao supplierDao;
    private final ConnectionProvider cp;

    private final SalesImportService salesImportService;
    private final SalesMaintenanceService maintenanceService;

    private Predicate<ProductInventoryView> activePredicate = v -> true;
    private String activeGroupFilter = null;
    private String activeSearchText = "";

    private final JTextField txtSearch = new JTextField(14);
    private final JLabel lblStatus = new JLabel(" ");
    private final NumberFormat nf = NumberFormat.getNumberInstance(new Locale("hr","HR"));
    private final JComboBox<String> cmbPeriod = new JComboBox<>(new String[]{"1M","3M","6M","12M"});

    // Trenutni raspon (period)
    private LocalDate currentFrom;
    private LocalDate currentTo;

    // Checkboxi (donji toolbar)
    private JCheckBox chkShowTurnoverCol;
    private JCheckBox chkShowSalesCol;
    private JCheckBox chkShowDailyDemandCol;
    private boolean dailyDemandVirtual = false;

    // Indeksi kolona u modelu
    private static final int COL_SALES = 12;
    private static final int COL_TURNOVER = 13;

    public ProductionInventoryPanel(String dbUrl) throws Exception {
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

        var salesDao = new SalesDaoImpl(cp);
        this.productService = new ProductService(invDao, salesDao, productSupplierDao);

        this.salesImportService = new SalesImportService(cp, productDao)
                .enableAutoCreateMissingProducts(true);
        this.maintenanceService = new SalesMaintenanceService(salesDao, invDao);

        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildToolbarTop(), BorderLayout.NORTH);
        add(buildBottomArea(), BorderLayout.SOUTH);

        hookSearchField();
        cmbPeriod.addActionListener(e -> reload());

        reload();
    }

    /* ------------------------------------------------------------------
       GORNJI TOOLBAR
     ------------------------------------------------------------------ */
    private JComponent buildToolbarTop() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton btnReload        = new JButton("Osvježi");
        JButton btnImport        = new JButton("Import artikli");
        JButton btnImportSales   = new JButton("Import prodaje");
        JButton btnSortSupplier  = new JButton("Sort Dobavljač");
        JButton btnSortValue     = new JButton("Sort Vrijednost");
        JButton btnFilterGroup   = new JButton("Filter grupa");
        JButton btnClearFilter   = new JButton("Poništi filter");
        JButton btnPlus          = new JButton("+1");
        JButton btnMinus         = new JButton("-1");
        JButton btnSetQty        = new JButton("Postavi količinu");
        JButton btnTotal         = new JButton("Ukupno");
        JLabel  lblSearch        = new JLabel("Traži:");

        JButton btnSuppliers = new JButton("Dobavljači");
        JButton btnAssign    = new JButton("Dodjela dobavljača");
        JButton btnTurnover  = new JButton("Obrtaj");
        JButton btnOrders    = new JButton("Narudžbe");
        JLabel  lblPeriod    = new JLabel("Period:");

        JButton btnClrStock  = new JButton("Obriši zalihe");
        JButton btnClrSales  = new JButton("Obriši prodaju");

        bar.add(btnReload);
        bar.add(btnImport);
        bar.add(btnImportSales);
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

        bar.add(lblPeriod);
        bar.add(cmbPeriod);
        bar.add(btnTurnover);
        bar.add(btnOrders);
        bar.add(btnSuppliers);
        bar.add(btnAssign);
        bar.add(btnClrStock);
        bar.add(btnClrSales);

        // Listeners stare + nove funkcije
        btnReload.addActionListener(e -> reload());
        btnImport.addActionListener(e -> importExcelProducts());
        btnImportSales.addActionListener(e -> importSalesExcel());
        btnSortSupplier.addActionListener(e -> sortBySupplier());
        btnSortValue.addActionListener(e -> sortByValue());
        btnFilterGroup.addActionListener(e -> filterByGroup());
        btnClearFilter.addActionListener(e -> clearFilters());
        btnPlus.addActionListener(e -> adjustSelectedQuantity(+1));
        btnMinus.addActionListener(e -> adjustSelectedQuantity(-1));
        btnSetQty.addActionListener(e -> setSelectedQuantity());
        btnTotal.addActionListener(e -> showTotalValue());
        btnSuppliers.addActionListener(e -> new SuppliersDialog(SwingUtilities.getWindowAncestor(this), cp).setVisible(true));
        btnAssign.addActionListener(e -> openAssignDialog());
        btnTurnover.addActionListener(e -> showTurnoverForSelected());
        btnOrders.addActionListener(e -> generateOrdersForPeriod()); // nova metoda
        btnClrStock.addActionListener(e -> clearAllInventory());
        btnClrSales.addActionListener(e -> clearAllSales());

        return bar;
    }

    /* ------------------------------------------------------------------
       DONJI TOOLBAR + STATUS
     ------------------------------------------------------------------ */
    private JComponent buildBottomArea() {
        JPanel container = new JPanel(new BorderLayout());

        JPanel extra = new JPanel(new FlowLayout(FlowLayout.LEFT));
        chkShowSalesCol = new JCheckBox("Kolona Prodaja", true);
        chkShowTurnoverCol = new JCheckBox("Kolona Obrtaj", true);
        chkShowDailyDemandCol = new JCheckBox("Dnevna potražnja u dijalogu", false);

        JButton btnRefreshPeriod = new JButton("Osvježi period");
        JButton btnExportCsv = new JButton("Export CSV");
        JButton btnRecalc = new JButton("Ponovno učitaj");
        JButton btnTurnoverDialog = new JButton("Obrtaj (dijalog)"); // dodatni gumb u donjoj traci

        extra.add(chkShowSalesCol);
        extra.add(chkShowTurnoverCol);
        extra.add(chkShowDailyDemandCol);
        extra.add(btnTurnoverDialog);
        extra.add(btnRefreshPeriod);
        extra.add(btnExportCsv);
        extra.add(btnRecalc);

        chkShowSalesCol.addActionListener(e -> toggleColumn(COL_SALES, chkShowSalesCol.isSelected()));
        chkShowTurnoverCol.addActionListener(e -> toggleColumn(COL_TURNOVER, chkShowTurnoverCol.isSelected()));
        chkShowDailyDemandCol.addActionListener(e -> dailyDemandVirtual = chkShowDailyDemandCol.isSelected());
        btnRefreshPeriod.addActionListener(e -> reload());
        btnExportCsv.addActionListener(e -> exportCsvQuick());
        btnRecalc.addActionListener(e -> reload());
        btnTurnoverDialog.addActionListener(e -> showTurnoverForSelected());

        container.add(extra, BorderLayout.NORTH);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(lblStatus, BorderLayout.WEST);
        lblStatus.setBorder(BorderFactory.createEmptyBorder(2,6,2,6));
        container.add(statusPanel, BorderLayout.SOUTH);

        return container;
    }

    /* ------------------------------------------------------------------
       Kolone show/hide
     ------------------------------------------------------------------ */
    private void toggleColumn(int modelIndex, boolean show) {
        if (modelIndex < 0 || modelIndex >= table.getColumnModel().getColumnCount()) return;
        try {
            TableColumn col = table.getColumnModel().getColumn(modelIndex);
            if (show) {
                col.setMinWidth(60);
                col.setMaxWidth(400);
                col.setPreferredWidth(120);
            } else {
                col.setMinWidth(0);
                col.setMaxWidth(0);
                col.setPreferredWidth(0);
            }
            table.doLayout();
        } catch (Exception ignored) {}
    }

    /* ------------------------------------------------------------------
       Search / filter
     ------------------------------------------------------------------ */
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
        Predicate<ProductInventoryView> p = v -> true;

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

    /* ------------------------------------------------------------------
       Reload (agregirana prodaja)
     ------------------------------------------------------------------ */
    private void reload() {
        try {
            ProductService.PeriodMonths pm =
                    ProductService.PeriodMonths.fromLabel((String) cmbPeriod.getSelectedItem());

            currentTo = LocalDate.now();
            currentFrom = currentTo.minusMonths(pm.months);

            var list = inventoryService.fullViewWithSales(currentFrom, currentTo);
            tableModel.setData(list);
            tableModel.applyFilter(activePredicate);

            toggleColumn(COL_SALES, chkShowSalesCol == null || chkShowSalesCol.isSelected());
            toggleColumn(COL_TURNOVER, chkShowTurnoverCol == null || chkShowTurnoverCol.isSelected());

            updateStatus("Učitano: " + table.getRowCount() +
                    " | Period: " + currentFrom + " .. " + currentTo);
        } catch (SQLException ex) {
            showError("Greška kod čitanja: " + ex.getMessage(), ex);
        }
    }

    /* ------------------------------------------------------------------
       Import artikala
     ------------------------------------------------------------------ */
    private void importExcelProducts() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (table.isEditing()) {
                try {
                    TableCellEditor ed = table.getCellEditor();
                    if (ed != null) ed.stopCellEditing();
                } catch (Exception ignored) {}
            }
            try {
                importService.fullImport(f);
                reload();
                JOptionPane.showMessageDialog(this, "Import artikala gotov.");
            } catch (Exception ex) {
                showError("Import artikala nije uspio: " + ex.getMessage(), ex);
            }
        }
    }

    /* ------------------------------------------------------------------
       Import prodaje
     ------------------------------------------------------------------ */
    private void importSalesExcel() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.exists()) {
            showError("Datoteka ne postoji: " + f.getAbsolutePath(), new RuntimeException("Missing file"));
            return;
        }
        try {
            var messages = salesImportService.importSales(f.toPath(), null);
            reload();
            JOptionPane.showMessageDialog(this,
                    "Import prodaje gotov.\n" + String.join("\n", messages),
                    "Prodaja", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showError("Import prodaje nije uspio: " + ex.getMessage(), ex);
        }
    }

    /* ------------------------------------------------------------------
       Brisanja
     ------------------------------------------------------------------ */
    private void clearAllInventory() {
        int c = JOptionPane.showConfirmDialog(this,
                "Obrisati SVE zalihe (inventory_state)?",
                "Potvrda", JOptionPane.YES_NO_OPTION);
        if (c != JOptionPane.YES_OPTION) return;
        try {
            maintenanceService.clearAllInventory();
            reload();
            JOptionPane.showMessageDialog(this, "Sve zalihe obrisane.");
        } catch (Exception ex) {
            showError("Brisanje zaliha nije uspjelo: " + ex.getMessage(), ex);
        }
    }

    private void clearAllSales() {
        int c = JOptionPane.showConfirmDialog(this,
                "Obrisati SVE prodaje (sales)?",
                "Potvrda", JOptionPane.YES_NO_OPTION);
        if (c != JOptionPane.YES_OPTION) return;
        try {
            maintenanceService.clearAllSales();
            reload();
            JOptionPane.showMessageDialog(this, "Sve prodaje obrisane.");
        } catch (Exception ex) {
            showError("Brisanje prodaje nije uspjelo: " + ex.getMessage(), ex);
        }
    }

    /* ------------------------------------------------------------------
       Sort
     ------------------------------------------------------------------ */
    private void sortBySupplier() {
        tableModel.sortBy(
                Comparator.comparing((ProductInventoryView v) -> {
                    String sc = v.getProduct().getSupplierCode();
                    return sc == null ? "" : sc;
                }).thenComparing(v -> v.getProduct().getProductCode())
        );
    }

    private void sortByValue() {
        tableModel.sortBy(
                Comparator.comparingDouble((ProductInventoryView v) -> {
                    Double val = v.getTotalValue();
                    return val != null ? val : 0.0;
                }).reversed()
        );
    }

    /* ------------------------------------------------------------------
       Manipulacija količinom
     ------------------------------------------------------------------ */
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
        String s = JOptionPane.showInputDialog(this,
                "Nova količina za " + code,
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

    /* ------------------------------------------------------------------
       Izračuni / prikazi
     ------------------------------------------------------------------ */
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

    private void showTurnoverForSelected() {
        var v = getSelectedView();
        if (v == null) return;

        long days = (currentFrom != null && currentTo != null)
                ? Math.max(1, ChronoUnit.DAYS.between(currentFrom, currentTo))
                : 30;
        Double sales = v.getSalesQtyPeriod();
        double currentQty = v.getInventory().getQuantity();
        Double turnover = (sales != null && currentQty > 0) ? (sales / currentQty) : null;
        Double dailyDemand = (sales != null) ? (sales / days) : null;

        StringBuilder sb = new StringBuilder();
        sb.append("Artikl: ").append(v.getProduct().getProductCode()).append("\n");
        sb.append("Period: ").append(currentFrom).append(" .. ").append(currentTo)
                .append(" (").append(days).append(" dana)\n");
        sb.append("Prodaja (kom): ").append(sales == null ? "—" : sales).append("\n");
        sb.append("Trenutna zaliha: ").append(currentQty).append("\n");
        sb.append("Obrtaj (Prodaja/Zaliha): ").append(turnover == null ? "—" : round2(turnover)).append("\n");
        if (dailyDemandVirtual) {
            sb.append("Dnevna potražnja: ").append(dailyDemand == null ? "—" : round2(dailyDemand)).append("\n");
        }

        JOptionPane.showMessageDialog(this,
                sb.toString(),
                "Obrtaj", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Prijedlog narudžbi – coverage = puni odabrani period (npr. 3M ≈ duljina perioda).
     * Safety factor ovdje 0.25 (promijeni po potrebi).
     */
    private void generateOrdersForPeriod() {
        try {
            var pm = ProductService.PeriodMonths.fromLabel((String) cmbPeriod.getSelectedItem());
            var list = productService.suggestOrdersForPeriod(pm, 0.25);
            if (list.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nema prijedloga za ovaj period.");
                return;
            }
            new OrderSuggestionsDialog(SwingUtilities.getWindowAncestor(this), list)
                    .setVisible(true);
        } catch (Exception ex) {
            showError("Greška narudžbe: " + ex.getMessage(), ex);
        }
    }

    /* ------------------------------------------------------------------
       Dodjela dobavljača
     ------------------------------------------------------------------ */
    private void openAssignDialog() {
        var v = getSelectedView();
        if (v == null) return;
        new ProductSupplierAssignDialog(
                SwingUtilities.getWindowAncestor(this),
                cp,
                v.getProduct().getProductCode()
        ).setVisible(true);
    }

    /* ------------------------------------------------------------------
       Export CSV (filtrirani prikaz)
     ------------------------------------------------------------------ */
    private void exportCsvQuick() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export inventure u CSV (filtrirano)");
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File out = fc.getSelectedFile();
        try (java.io.PrintWriter pw = new java.io.PrintWriter(out, java.nio.charset.StandardCharsets.UTF_8)) {
            // Header
            for (int c = 0; c < table.getColumnCount(); c++) {
                if (c > 0) pw.print(";");
                pw.print(escapeCsv(table.getColumnName(c)));
            }
            pw.println();
            // Rows
            for (int r = 0; r < table.getRowCount(); r++) {
                for (int c = 0; c < table.getColumnCount(); c++) {
                    if (c > 0) pw.print(";");
                    Object val = table.getValueAt(r, c);
                    pw.print(escapeCsv(val == null ? "" : val.toString()));
                }
                pw.println();
            }
        } catch (Exception ex) {
            showError("Export CSV nije uspio: " + ex.getMessage(), ex);
            return;
        }
        JOptionPane.showMessageDialog(this, "Exportirano u: " + out.getAbsolutePath());
    }

    private String escapeCsv(String s) {
        if (s.contains(";") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /* ------------------------------------------------------------------
       Util
     ------------------------------------------------------------------ */
    private void updateStatus(String m) { lblStatus.setText(m); }

    private void showError(String msg, Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, msg, "Greška", JOptionPane.ERROR_MESSAGE);
        updateStatus("Greška: " + msg);
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}