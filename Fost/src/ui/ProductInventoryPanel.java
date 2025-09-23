package ui;

import dao.ConnectionProvider;
import dao.InventoryDao;
import dao.ProductDao;
import dao.ProductGroupDao;
import excel.ExcelProductInventoryReader;
import model.Product;
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

    // DAO polja za uređivanje iz dijaloga
    private final ProductDao productDao;
    private final InventoryDao invDao;
    private final ProductGroupDao groupDao;

    public ProductInventoryPanel(String dbUrl) {
        setLayout(new BorderLayout());

        var cp = new ConnectionProvider(dbUrl);
        this.productDao = new ProductDao(cp);
        this.invDao = new InventoryDao(cp);
        this.groupDao = new ProductGroupDao(cp);

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

        // Novi gumbi za uređivanje
        JButton btnEdit = new JButton("Uredi proizvod…");
        JButton btnGroups = new JButton("Grupe…");
        JButton btnQty = new JButton("Količina/Cijena…");
        JButton btnSupplier = new JButton("Postavi dobavljača…");

        top.add(btnReload);
        top.add(btnImport);
        top.add(btnSortSupplier);
        top.add(btnSortValue);
        top.add(btnFilterGroup);
        top.add(btnTotal);

        // Dodaj nove gumbe
        top.add(btnEdit);
        top.add(btnGroups);
        top.add(btnQty);
        top.add(btnSupplier);

        add(top, BorderLayout.NORTH);

        btnReload.addActionListener(e -> reload());
        btnImport.addActionListener(e -> importExcel());
        btnSortSupplier.addActionListener(e -> sortBySupplier());
        btnSortValue.addActionListener(e -> sortByValue());
        btnFilterGroup.addActionListener(e -> filterByGroupDialog());
        btnTotal.addActionListener(e -> showTotalValue());

        // Nove akcije za uređivanje
        btnEdit.addActionListener(e -> editSelectedProduct());
        btnGroups.addActionListener(e -> editSelectedGroups());
        btnQty.addActionListener(e -> adjustSelectedQuantity());
        btnSupplier.addActionListener(e -> setSelectedSupplier());

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

    // --- NOVO: pomoćne metode za uređivanje ---

    private ProductInventoryView getSelectedView() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        int modelRow = table.convertRowIndexToModel(row);
        return tableModel.getAt(modelRow);
    }

    private void editSelectedProduct() {
        var v = getSelectedView();
        if (v == null) { JOptionPane.showMessageDialog(this, "Odaberi red."); return; }
        Product p = v.getProduct();
        Window owner = SwingUtilities.getWindowAncestor(this);
        EditProductDialog dlg = new EditProductDialog(owner, productDao, p);
        dlg.setVisible(true);
        reload();
    }

    private void editSelectedGroups() {
        var v = getSelectedView();
        if (v == null) { JOptionPane.showMessageDialog(this, "Odaberi red."); return; }
        Window owner = SwingUtilities.getWindowAncestor(this);
        ManageProductGroupsDialog dlg =
                new ManageProductGroupsDialog(owner, groupDao, v.getProduct().getProductCode(), v.getGroupCodes());
        dlg.setVisible(true);
        reload();
    }

    private void adjustSelectedQuantity() {
        var v = getSelectedView();
        if (v == null) { JOptionPane.showMessageDialog(this, "Odaberi red."); return; }
        String code = v.getProduct().getProductCode();

        String qtyStr = JOptionPane.showInputDialog(this, "Nova količina (base):", v.getInventory().getQuantity());
        if (qtyStr == null) return;
        Double qty;
        try {
            qty = Double.valueOf(qtyStr.trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Neispravan broj.");
            return;
        }

        String priceStr = JOptionPane.showInputDialog(this, "Jedinična nabavna cijena (enter za zadržati):",
                v.getProduct().getPurchaseUnitPrice() == null ? "" : v.getProduct().getPurchaseUnitPrice().toString());
        Double price = null;
        if (priceStr != null && !priceStr.trim().isEmpty()) {
            try {
                price = Double.valueOf(priceStr.trim());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Neispravan broj za cijenu.");
                return;
            }
        }
        try {
            invDao.upsertQuantity(code, qty, price);
            reload();
        } catch (Exception ex) {
            showError("Spremanje nije uspjelo: " + ex.getMessage(), ex);
        }
    }

    private void setSelectedSupplier() {
        var v = getSelectedView();
        if (v == null) { JOptionPane.showMessageDialog(this, "Odaberi red."); return; }
        String current = v.getProduct().getSupplierCode();
        String sup = JOptionPane.showInputDialog(this, "Dobavljač (code):", current == null ? "" : current);
        if (sup == null) return;
        try {
            Product p = v.getProduct();
            Product updated = new Product(
                    p.getProductCode(),
                    p.getName(),
                    p.getMainType(),
                    sup.trim().isEmpty() ? null : sup.trim(),
                    p.getBaseUnit(),
                    p.getAltUnit(),
                    p.getAreaPerPiece(),
                    p.getPackSize(),
                    p.getMinOrderQty(),
                    p.getPurchaseUnitPrice(),
                    p.isActive()
            );
            productDao.upsert(updated);
            reload();
        } catch (Exception ex) {
            showError("Spremanje dobavljača nije uspjelo: " + ex.getMessage(), ex);
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