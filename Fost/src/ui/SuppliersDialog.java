package ui;

import dao.SupplierDao;
import dao.ConnectionProvider;
import model.Supplier;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SuppliersDialog extends JDialog {

    private final SupplierDao supplierDao;
    private final SupplierTableModel model = new SupplierTableModel();

    public SuppliersDialog(Window owner, ConnectionProvider cp) {
        super(owner, "Dobavljači", ModalityType.APPLICATION_MODAL);
        this.supplierDao = new SupplierDao(cp);

        JTable table = new JTable(model);
        JScrollPane sp = new JScrollPane(table);

        JButton btnReload = new JButton("Osvježi");
        JButton btnAdd = new JButton("Dodaj");
        JButton btnEdit = new JButton("Uredi");
        JButton btnDel = new JButton("Obriši");
        JButton btnClose = new JButton("Zatvori");

        btnReload.addActionListener(e -> reload());
        btnAdd.addActionListener(e -> addSupplier());
        btnEdit.addActionListener(e -> editSupplier(table.getSelectedRow()));
        btnDel.addActionListener(e -> deleteSupplier(table.getSelectedRow()));
        btnClose.addActionListener(e -> dispose());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(btnReload);
        top.add(btnAdd);
        top.add(btnEdit);
        top.add(btnDel);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(btnClose);

        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(sp, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);

        setSize(700, 400);
        setLocationRelativeTo(owner);
        reload();
    }

    private void reload() {
        try {
            model.setData(supplierDao.findAll());
        } catch (SQLException ex) {
            showError(ex);
        }
    }

    private void addSupplier() {
        Supplier s = editDialog(null);
        if (s != null) {
            try {
                supplierDao.upsert(s);
                reload();
            } catch (SQLException ex) { showError(ex); }
        }
    }

    private void editSupplier(int row) {
        if (row < 0) return;
        Supplier orig = model.get(row);
        Supplier edited = editDialog(orig);
        if (edited != null) {
            try {
                supplierDao.upsert(edited);
                reload();
            } catch (SQLException ex) { showError(ex); }
        }
    }

    private void deleteSupplier(int row) {
        if (row < 0) return;
        Supplier s = model.get(row);
        int conf = JOptionPane.showConfirmDialog(this,
                "Obrisati dobavljača " + s.getSupplierCode() + "?",
                "Potvrda", JOptionPane.YES_NO_OPTION);
        if (conf == JOptionPane.YES_OPTION) {
            try {
                supplierDao.delete(s.getSupplierCode());
                reload();
            } catch (SQLException ex) { showError(ex); }
        }
    }

    private Supplier editDialog(Supplier s) {
        JTextField txtCode = new JTextField(s != null ? s.getSupplierCode() : "", 15);
        JTextField txtName = new JTextField(s != null ? s.getName() : "", 25);
        JTextField txtContact = new JTextField(s != null ? s.getContact() : "", 25);
        JTextField txtPhone = new JTextField(s != null ? s.getPhone() : "", 20);
        JTextField txtEmail = new JTextField(s != null ? s.getEmail() : "", 25);
        JCheckBox chkActive = new JCheckBox("Aktivan", s == null || s.isActive());

        if (s != null) txtCode.setEditable(false);

        JPanel p = new JPanel(new GridLayout(0,2,6,6));
        p.add(new JLabel("Šifra:")); p.add(txtCode);
        p.add(new JLabel("Naziv:")); p.add(txtName);
        p.add(new JLabel("Kontakt:")); p.add(txtContact);
        p.add(new JLabel("Telefon:")); p.add(txtPhone);
        p.add(new JLabel("Email:")); p.add(txtEmail);
        p.add(new JLabel("")); p.add(chkActive);

        int ok = JOptionPane.showConfirmDialog(this, p,
                s == null ? "Novi dobavljač" : "Uredi dobavljača",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (ok == JOptionPane.OK_OPTION) {
            if (txtCode.getText().trim().isEmpty() || txtName.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Šifra i naziv su obavezni.");
                return null;
            }
            return new Supplier(
                    txtCode.getText().trim(),
                    txtName.getText().trim(),
                    txtContact.getText().trim(),
                    txtPhone.getText().trim(),
                    txtEmail.getText().trim(),
                    chkActive.isSelected()
            );
        }
        return null;
    }

    private void showError(Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Greška", JOptionPane.ERROR_MESSAGE);
    }

    private static class SupplierTableModel extends AbstractTableModel {
        private final String[] cols = {"Šifra","Naziv","Kontakt","Telefon","Email","Aktivan"};
        private final List<Supplier> data = new ArrayList<>();

        public void setData(List<Supplier> list) {
            data.clear();
            data.addAll(list);
            fireTableDataChanged();
        }

        public Supplier get(int r) { return data.get(r); }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int column) { return cols[column]; }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            Supplier s = data.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> s.getSupplierCode();
                case 1 -> s.getName();
                case 2 -> s.getContact();
                case 3 -> s.getPhone();
                case 4 -> s.getEmail();
                case 5 -> s.isActive();
                default -> null;
            };
        }

        @Override public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 5 ? Boolean.class : String.class;
        }
    }
}