package ui;

import dao.ProductDao;
import model.Product;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

public class EditProductDialog extends JDialog {
    private final ProductDao productDao;
    private final Product original;

    private final JTextField tfCode = new JTextField();
    private final JTextField tfName = new JTextField();
    private final JTextField tfSupplier = new JTextField();
    private final JTextField tfMainType = new JTextField();
    private final JTextField tfBaseUnit = new JTextField();
    private final JTextField tfAltUnit = new JTextField();
    private final JFormattedTextField tfAreaPerPiece = new JFormattedTextField();
    private final JFormattedTextField tfPackSize = new JFormattedTextField();
    private final JFormattedTextField tfMinOrderQty = new JFormattedTextField();
    private final JFormattedTextField tfPurchaseUnitPrice = new JFormattedTextField();
    private final JCheckBox chkActive = new JCheckBox("Aktivan");

    public EditProductDialog(Window owner, ProductDao productDao, Product product) {
        super(owner, "Uredi proizvod", ModalityType.APPLICATION_MODAL);
        this.productDao = productDao;
        this.original = product;

        tfCode.setText(product.getProductCode());
        tfCode.setEditable(false);
        tfName.setText(nullToEmpty(product.getName()));
        tfSupplier.setText(nullToEmpty(product.getSupplierCode()));
        tfMainType.setText(nullToEmpty(product.getMainType()));
        tfBaseUnit.setText(nullToEmpty(product.getBaseUnit()));
        tfAltUnit.setText(nullToEmpty(product.getAltUnit()));
        tfAreaPerPiece.setValue(product.getAreaPerPiece());
        tfPackSize.setValue(product.getPackSize());
        tfMinOrderQty.setValue(product.getMinOrderQty());
        tfPurchaseUnitPrice.setValue(product.getPurchaseUnitPrice());
        chkActive.setSelected(product.isActive());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.fill = GridBagConstraints.HORIZONTAL;

        int r = 0;
        addRow(form, c, r++, "Šifra", tfCode);
        addRow(form, c, r++, "Naziv", tfName);
        addRow(form, c, r++, "Dobavljač (code)", tfSupplier);
        addRow(form, c, r++, "Vrsta (main_type)", tfMainType);
        addRow(form, c, r++, "Base jedinica", tfBaseUnit);
        addRow(form, c, r++, "Alt jedinica", tfAltUnit);
        addRow(form, c, r++, "m2/kom", tfAreaPerPiece);
        addRow(form, c, r++, "Pakiranje", tfPackSize);
        addRow(form, c, r++, "Min. nar.", tfMinOrderQty);
        addRow(form, c, r++, "Jed. cijena (nabavna)", tfPurchaseUnitPrice);

        c.gridx = 0; c.gridy = r; c.gridwidth = 2;
        form.add(chkActive, c);

        JButton btnSave = new JButton("Spremi");
        JButton btnCancel = new JButton("Odustani");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(btnCancel);
        buttons.add(btnSave);

        btnCancel.addActionListener(e -> dispose());
        btnSave.addActionListener(e -> onSave());

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        setSize(480, 520);
        setLocationRelativeTo(owner);
    }

    private void addRow(JPanel p, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0; c.gridy = row; c.weightx = 0; c.gridwidth = 1;
        p.add(new JLabel(label), c);
        c.gridx = 1; c.weightx = 1;
        p.add(field, c);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private Double getDouble(JFormattedTextField f) {
        Object v = f.getValue();
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.valueOf(v.toString().trim()); } catch (Exception e) { return null; }
    }

    private void onSave() {
        try {
            Product updated = new Product(
                    original.getProductCode(),
                    tfName.getText().trim().isEmpty() ? original.getName() : tfName.getText().trim(),
                    tfMainType.getText().trim().isEmpty() ? null : tfMainType.getText().trim(),
                    tfSupplier.getText().trim().isEmpty() ? null : tfSupplier.getText().trim(),
                    tfBaseUnit.getText().trim().isEmpty() ? null : tfBaseUnit.getText().trim(),
                    tfAltUnit.getText().trim().isEmpty() ? null : tfAltUnit.getText().trim(),
                    getDouble(tfAreaPerPiece),
                    getDouble(tfPackSize),
                    getDouble(tfMinOrderQty),
                    getDouble(tfPurchaseUnitPrice),
                    chkActive.isSelected()
            );
            productDao.upsert(updated);
            dispose();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Spremanje nije uspjelo: " + ex.getMessage(), "Greška", JOptionPane.ERROR_MESSAGE);
        }
    }
}