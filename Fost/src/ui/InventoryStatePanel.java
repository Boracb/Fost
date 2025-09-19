package ui;

import db.InventoryStateDatabaseHelper;
import excel.ExcelInventoryStateReader;
import logic.InventoryImportService;
import logic.InventoryService;
import model.StockState;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class InventoryStatePanel extends JPanel {

    private final InventoryTableModel tableModel = new InventoryTableModel();
    private final JTable table = new JTable(tableModel);

    private final InventoryService inventoryService;
    private final InventoryImportService importService;
    private final String dbUrl;

    public InventoryStatePanel(String dbUrl) {
        this.dbUrl = dbUrl;
        setLayout(new BorderLayout());

        InventoryStateDatabaseHelper helper = new InventoryStateDatabaseHelper(dbUrl);
        this.inventoryService = new InventoryService(helper);

        ExcelInventoryStateReader reader = new ExcelInventoryStateReader()
                .withColumns(0,1,2,3,4,5)   // Šifra, Naziv, Jed.mj., Količina, Nabavna cijena, Nabavna vrijednost
                .withHeader(true)
                .enableDebug(false);

        this.importService = new InventoryImportService(reader::parse, helper);

        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnReload = new JButton("Osvježi");
        JButton btnImport = new JButton("Import Excel");
        JButton btnSave = new JButton("Spremi uređeno");
        JButton btnClear = new JButton("Obriši sve");
        JButton btnTotal = new JButton("Ukupna vrijednost");

        buttons.add(btnReload);
        buttons.add(btnImport);
        buttons.add(btnSave);
        buttons.add(btnClear);
        buttons.add(btnTotal);

        add(buttons, BorderLayout.NORTH);

        btnReload.addActionListener(e -> reload());
        btnImport.addActionListener(e -> importExcel());
        btnSave.addActionListener(e -> saveChanges());
        btnClear.addActionListener(e -> clearAll());
        btnTotal.addActionListener(e -> showTotalValue());

        reload();
    }

    private void reload() {
        try {
            List<StockState> all = inventoryService.getAll();
            tableModel.setData(all);
        } catch (SQLException ex) {
            showError("Greška kod čitanja: " + ex.getMessage(), ex);
        }
    }

    private void importExcel() {
        JFileChooser fc = new JFileChooser();
        int res = fc.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                importService.safeReplaceAll(f);
                reload();
                JOptionPane.showMessageDialog(this, "Import završen.");
            } catch (Exception ex) {
                showError("Import nije uspio: " + ex.getMessage(), ex);
            }
        }
    }

    private void saveChanges() {
        try {
            // snapshot -> bulkUpsert
            new InventoryStateDatabaseHelper(dbUrl).bulkUpsert(tableModel.snapshot());
            JOptionPane.showMessageDialog(this, "Promjene spremljene.");
            reload();
        } catch (Exception ex) {
            showError("Spremanje nije uspjelo: " + ex.getMessage(), ex);
        }
    }

    private void clearAll() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Obrisati SVE stavke lagera?",
                "Potvrda",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            inventoryService.deleteAll();
            tableModel.setData(List.of());
        } catch (Exception ex) {
            showError("Brisanje nije uspjelo: " + ex.getMessage(), ex);
        }
    }

    private void showTotalValue() {
        try {
            double sum = inventoryService.totalPurchaseValue();
            NumberFormat nf = NumberFormat.getNumberInstance(new Locale("hr","HR"));
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            JOptionPane.showMessageDialog(this,
                    "Ukupna nabavna vrijednost: " + nf.format(sum),
                    "Suma",
                    JOptionPane.INFORMATION_MESSAGE);
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
            JFrame f = new JFrame("Stanje zaliha (prošireno)");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(new InventoryStatePanel("jdbc:sqlite:fost.db"));
            f.setSize(1150, 620);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}