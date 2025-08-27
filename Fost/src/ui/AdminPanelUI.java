package ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import db.UserDatabaseHelper;
import java.awt.*;

/**
 * UI prozor za administraciju korisnika aplikacije. Prikazuje tablicu svih
 * korisnika s mogućnošću dodavanja, uređivanja i brisanja korisnika. Sadrži
 * funkcionalnost pretraživanja i sortiranja korisnika. Koristi
 * UserDatabaseHelper klasu za interakciju s bazom podataka. Validira akcije
 * (npr. ne dopušta brisanje ili uređivanje admin računa) i prikazuje poruke o
 * uspjehu ili greškama.
 */

public class AdminPanelUI extends JFrame {

    private DefaultTableModel model;
    private JTable table;
    private TableRowSorter<DefaultTableModel> sorter;
// Konstruktor
    public AdminPanelUI() {
        setTitle("Administracija korisnika");
        setSize(600, 450);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
		/**
		 * * Inicijalizacija tablice i modela
		 */
        model = new DefaultTableModel(new String[]{"Korisnik", "Uloga"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(model);
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
// Sortiranje po korisniku (prvi stupac) po defaultu
        refreshTable();
        sorter.toggleSortOrder(0);

       //* Top panel s poljem za pretragu
        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel lblSearch = new JLabel("Pretraga: ");
        JTextField txtSearch = new JTextField();
        txtSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            private void filter() {
                String text = txtSearch.getText();
                sorter.setRowFilter(text.trim().isEmpty() ? null : RowFilter.regexFilter("(?i)" + text));
            }
        });
        topPanel.add(lblSearch, BorderLayout.WEST);
        topPanel.add(txtSearch, BorderLayout.CENTER);

        // Gumbi
        JButton btnAdd = new JButton("Dodaj korisnika");
        btnAdd.addActionListener(e -> new AddUserUI(this::refreshTable));
		/**
		 * * * Gumb za uređivanje korisnika Provjerava je li odabran redak i nije li to
		 * admin korisnik Ako je sve u redu, otvara EditUserUI prozor za uređivanje
		 * Nakon uređivanja, osvježava tablicu
		 */
        JButton btnEdit = new JButton("Uredi korisnika");
        btnEdit.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            String username = (String) table.getValueAt(row, 0);
            if ("admin".equalsIgnoreCase(username)) {
                JOptionPane.showMessageDialog(this, "Admin račun se ne može uređivati.", "Upozorenje", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String role = (String) table.getValueAt(row, 1);
            new EditUserUI(username, role, this::refreshTable);
        });

        JButton btnDelete = new JButton("Obriši korisnika");
        btnDelete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            String username = (String) table.getValueAt(row, 0);
            if ("admin".equalsIgnoreCase(username)) {
                JOptionPane.showMessageDialog(this, "Admin se ne može obrisati.", "Upozorenje", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this, "Obrisati korisnika '" + username + "'?", "Potvrda", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                if (UserDatabaseHelper.deleteUser(username)) {
                    JOptionPane.showMessageDialog(this, "Korisnik obrisan.");
                    refreshTable();
                } else {
                    JOptionPane.showMessageDialog(this, "Greška pri brisanju korisnika.");
                }
            }
        });

        JButton btnRefresh = new JButton("Osvježi");
        btnRefresh.addActionListener(e -> refreshTable());

        JPanel bottom = new JPanel();
        bottom.add(btnAdd);
        bottom.add(btnEdit);
        bottom.add(btnDelete);
        bottom.add(btnRefresh);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        setVisible(true);
    }
// Osvježava sadržaj tablice dohvaćanjem svih korisnika iz baze podataka
    private void refreshTable() {
        model.setRowCount(0);
        for (String[] u : UserDatabaseHelper.getAllUsers()) {
            model.addRow(u);
        }
    }
}
