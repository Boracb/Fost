package ui;

import dao.ProductSupplierDao;
import dao.SupplierDao;
import dao.ConnectionProvider;
import model.ProductSupplier;
import model.Supplier;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

public class ProductSupplierAssignDialog extends JDialog {

    private final ProductSupplierDao psDao;
    private final SupplierDao supplierDao;
    private final String productCode;

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> lstSuppliers = new JList<>(listModel);

    private final JTextField txtLead = new JTextField(6);
    private final JTextField txtMin = new JTextField(6);
    private final JTextField txtLastPrice = new JTextField(6);
    private final JCheckBox chkPrimary = new JCheckBox("Primarni");

    public ProductSupplierAssignDialog(Window owner,
                                       ConnectionProvider cp,
                                       String productCode) {
        super(owner, "Dobavljači za " + productCode, ModalityType.APPLICATION_MODAL);
        this.productCode = productCode;
        this.psDao = new ProductSupplierDao(cp);
        this.supplierDao = new SupplierDao(cp);

        setLayout(new BorderLayout(8,8));

        lstSuppliers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = new JScrollPane(lstSuppliers);

        JPanel right = new JPanel(new GridLayout(0,2,4,4));
        right.add(new JLabel("Lead time (d):")); right.add(txtLead);
        right.add(new JLabel("Min nar.:")); right.add(txtMin);
        right.add(new JLabel("Zadnja cijena:")); right.add(txtLastPrice);
        right.add(new JLabel(" ")); right.add(chkPrimary);

        JButton btnSave = new JButton("Spremi");
        JButton btnClose = new JButton("Zatvori");
        JButton btnAdd = new JButton("Dodaj vezu");
        JButton btnRemove = new JButton("Ukloni vezu");

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(btnAdd);
        south.add(btnRemove);
        south.add(btnSave);
        south.add(btnClose);

        add(sp, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        lstSuppliers.addListSelectionListener(e -> loadSelectedDetails());
        btnAdd.addActionListener(e -> addSupplierRelation());
        btnRemove.addActionListener(e -> removeRelation());
        btnSave.addActionListener(e -> saveCurrent());
        btnClose.addActionListener(e -> dispose());

        setSize(560, 340);
        setLocationRelativeTo(owner);
        reloadList();
    }

    private void reloadList() {
        listModel.clear();
        try {
            List<ProductSupplier> relations = psDao.listByProduct(productCode);
            for (var r : relations) {
                listModel.addElement(r.getSupplierCode() + (r.isPrimary() ? " *" : ""));
            }
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private String selectedSupplierCode() {
        String sel = lstSuppliers.getSelectedValue();
        if (sel == null) return null;
        return sel.replace(" *", "");
    }

    private void loadSelectedDetails() {
        String code = selectedSupplierCode();
        if (code == null) {
            txtLead.setText("");
            txtMin.setText("");
            txtLastPrice.setText("");
            chkPrimary.setSelected(false);
            return;
        }
        try {
            var all = psDao.listByProduct(productCode);
            for (var r : all) {
                if (r.getSupplierCode().equals(code)) {
                    txtLead.setText(r.getLeadTimeDays() != null ? String.valueOf(r.getLeadTimeDays()) : "");
                    txtMin.setText(r.getMinOrderQty() != null ? String.valueOf(r.getMinOrderQty()) : "");
                    txtLastPrice.setText(r.getLastPrice() != null ? String.valueOf(r.getLastPrice()) : "");
                    chkPrimary.setSelected(r.isPrimary());
                    return;
                }
            }
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void addSupplierRelation() {
        try {
            List<Supplier> all = supplierDao.findAll();
            if (all.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nema dobavljača – dodaj u 'Dobavljači'.");
                return;
            }
            String[] codes = all.stream()
                    .map(this::codeOf) // tolerantno
                    .toArray(String[]::new);
            String sel = (String) JOptionPane.showInputDialog(this,
                    "Odaberi dobavljača:",
                    "Dodaj vezu",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    codes,
                    codes[0]);
            if (sel == null) return;
            ProductSupplier ps = new ProductSupplier(productCode, sel, false, null, null, null);
            psDao.upsert(ps);
            reloadList();
            lstSuppliers.setSelectedIndex(listModel.size() - 1);
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private String codeOf(Supplier s) {
        try {
            return s.getSupplierCode();
        } catch (NoSuchMethodError | Exception ignored) {
            // fallback ako imaš getCode()
            try {
                return (String) s.getClass().getMethod("getCode").invoke(s);
            } catch (Exception e) {
                return "??";
            }
        }
    }

    private void removeRelation() {
        String sup = selectedSupplierCode();
        if (sup == null) return;
        int ok = JOptionPane.showConfirmDialog(this,
                "Ukloniti vezu s dobavljačem " + sup + "?",
                "Potvrda", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            try {
                psDao.delete(productCode, sup);
                reloadList();
            } catch (Exception ex) { showError(ex); }
        }
    }

    private void saveCurrent() {
        String sup = selectedSupplierCode();
        if (sup == null) return;
        try {
            Integer lead = txtLead.getText().isBlank() ? null : Integer.parseInt(txtLead.getText().trim());
            Double min = txtMin.getText().isBlank() ? null : Double.parseDouble(txtMin.getText().trim().replace(',', '.'));
            Double lastPrice = txtLastPrice.getText().isBlank() ? null : Double.parseDouble(txtLastPrice.getText().trim().replace(',', '.'));
            ProductSupplier ps = new ProductSupplier(productCode, sup, chkPrimary.isSelected(), lead, min, lastPrice);
            psDao.upsert(ps);
            reloadList();
        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this, "Neispravan broj u poljima.");
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void showError(Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Greška", JOptionPane.ERROR_MESSAGE);
    }
}