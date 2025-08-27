package ui;

import db.KomitentiDatabaseHelper;
import model.KomitentInfo;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * UI prozor za upravljanje komitentima i njihovim trgovačkim predstavnicima.
 * Prikazuje tablicu s mogućnošću dodavanja, uređivanja, brisanja i
 * pretraživanja komitenata. Podaci se učitavaju iz baze podataka pri pokretanju
 * i spremaju pri zatvaranju prozora.
 */

public class KomitentiUI extends JFrame {

    private DefaultTableModel tableModel;
    private JTable table;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;

    public KomitentiUI() {
        setTitle("Komitenti i Trgovački predstavnici");
        setSize(700, 450);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initTable();
        loadData();
        initSearchPanel();
        initButtonsPanel();
// Spremanje podataka pri zatvaranju prozora
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveData();
            }
        });

        setVisible(true);
    }

    /** Inicijalizacija tablice i modela */

// Konstruktor
 private void initTable() {
     tableModel = new DefaultTableModel(new Object[]{"Komitent", "Trgovački predstavnik"}, 0) {
         @Override
         public boolean isCellEditable(int row, int column) {
             return true;
         }
     };

     table = new JTable(tableModel);

     // --- ADD THIS BLOCK ---
     // Učitavanje svih predstavnika iz baze i postavljanje JComboBox editora za drugi stupac
     List<String> reps = db.PredstavniciDatabaseHelper.loadAllPredstavnici();
     JComboBox<String> combo = new JComboBox<>(reps.toArray(new String[0]));
     TableColumn col = table.getColumnModel().getColumn(1); // 1 = "Trgovački predstavnik"
     col.setCellEditor(new DefaultCellEditor(combo));
     // --- END BLOCK ---

     table.setFillsViewportHeight(true);
     table.setBackground(Color.BLACK);
     table.setForeground(Color.WHITE);
     table.setSelectionBackground(Color.DARK_GRAY);
     table.setSelectionForeground(Color.YELLOW);
     table.setGridColor(Color.GRAY);

     sorter = new TableRowSorter<>(tableModel);
     table.setRowSorter(sorter);

     JTableHeader header = table.getTableHeader();
     header.setBackground(Color.BLACK);
     header.setForeground(Color.WHITE);

     JScrollPane scrollPane = new JScrollPane(table);
     scrollPane.getViewport().setBackground(Color.BLACK);
     add(scrollPane, BorderLayout.CENTER);
 }


    /** Panel s poljem za pretragu */
 // Konstruktor
    private void initSearchPanel() {
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBackground(Color.BLACK);

        JLabel lblSearch = new JLabel("Pretraga:");
        lblSearch.setForeground(Color.WHITE);
        searchPanel.add(lblSearch);

        searchField = new JTextField(20);
        searchPanel.add(searchField);
// Dodavanje DocumentListener-a za dinamičko filtriranje
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterTable(); }
            public void removeUpdate(DocumentEvent e) { filterTable(); }
            public void changedUpdate(DocumentEvent e) { filterTable(); }
        });

        add(searchPanel, BorderLayout.NORTH);
    }

    /** Panel s gumbima i akcijama */
    // Konstruktor
    private void initButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(Color.BLACK);

        JButton btnAdd = new JButton("Dodaj");
        btnAdd.addActionListener(e -> addKomitent());

        JButton btnEdit = new JButton("Uredi");
        btnEdit.addActionListener(e -> editSelected());

        JButton btnAssign = new JButton("Dodijeli predstavnika");
        btnAssign.addActionListener(e -> assignPredstavnik());

        JButton btnDelete = new JButton("Obriši");
        btnDelete.addActionListener(e -> deleteSelected());

        JButton btnSaveAll = new JButton("Spremi sve");
        btnSaveAll.addActionListener(e -> saveData());

        JButton btnShowUnassigned = new JButton("Prikaži bez predstavnika");
        btnShowUnassigned.addActionListener(e -> showKomitentiWithoutPredstavnik());

        JButton btnShowAll = new JButton("Prikaži sve");
        btnShowAll.addActionListener(e -> sorter.setRowFilter(null));

        panelButtons.add(btnAdd);
        panelButtons.add(btnEdit);
        panelButtons.add(btnAssign);
        panelButtons.add(btnDelete);
        panelButtons.add(btnSaveAll);
        panelButtons.add(btnShowUnassigned);
        panelButtons.add(btnShowAll);

        add(panelButtons, BorderLayout.SOUTH);
    }
    /** Učitavanje podataka iz baze */
    // Konstruktor
    private void loadData() {
        tableModel.setRowCount(0);
        List<Object[]> podaci = KomitentiDatabaseHelper.loadAllRows();
        for (Object[] row : podaci) {
            if (row[1] == null) row[1] = "";
            tableModel.addRow(row);
        }
    }

    /** Spremanje podataka u bazu */
    private void saveData() {
        int rowCount = tableModel.getRowCount();
        java.util.List<KomitentInfo> lista = new java.util.ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            String komitent = (String) tableModel.getValueAt(i, 0);
            String predstavnik = (String) tableModel.getValueAt(i, 1);
            if (predstavnik == null) predstavnik = "";
            lista.add(new KomitentInfo(komitent, predstavnik));
        }
        KomitentiDatabaseHelper.saveToDatabase(lista);
        JOptionPane.showMessageDialog(this, "Podaci su spremljeni.");
    }

    /** Provjera postoji li komitent */
    private boolean komitentExists(String komitent) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String existing = (String) tableModel.getValueAt(i, 0);
            if (existing != null && existing.equalsIgnoreCase(komitent)) {
                return true;
            }
        }
        return false;
    }

    /** Filtriranje tablice po pojmu pretrage */
    private void filterTable() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 0));
        }
    }

    /** Dodavanje novog komitenta */
    private void addKomitent() {
        String komitent = JOptionPane.showInputDialog(this, "Unesi naziv komitenta:");
        if (komitent == null || komitent.trim().isEmpty()) return;
        if (komitentExists(komitent.trim())) {
            JOptionPane.showMessageDialog(this, "Komitent već postoji.");
            return;
        }
        String predstavnik = JOptionPane.showInputDialog(this, "Unesi trgovačkog predstavnika (ostavi prazno ako nema):");
        if (predstavnik == null) predstavnik = "";
        tableModel.addRow(new Object[]{komitent.trim(), predstavnik.trim()});
        saveData();
    }

    /** Uređivanje odabranog reda */
    // Konstruktor
    
    private void editSelected() {
    	// Dobivanje odabranog retka
        int viewRow = table.getSelectedRow();
        // Provjera je li redak odabran
        if (viewRow < 0) {
        	// Ako nije, prikazuje se poruka i izlazi iz metode
            JOptionPane.showMessageDialog(this, "Odaberi red za uređivanje.");
            return;
        }
        // Konverzija prikazanog retka u modelski redak
        int modelRow = table.convertRowIndexToModel(viewRow);
        // Dohvaćanje trenutnih vrijednosti komitenta i predstavnika
        String komitent = (String) tableModel.getValueAt(modelRow, 0);
        String predstavnik = (String) tableModel.getValueAt(modelRow, 1);

        // Uređivanje naziva komitenta
        String newKomitent = JOptionPane.showInputDialog(this, "Uredi naziv komitenta:", komitent);
        if (newKomitent == null || newKomitent.trim().isEmpty()) return;
        if (!komitent.equalsIgnoreCase(newKomitent.trim()) && komitentExists(newKomitent.trim())) {
            JOptionPane.showMessageDialog(this, "Komitent s tim nazivom već postoji.");
            return;
        }
// Uređivanje trgovačkog predstavnika
        String newPredstavnik = JOptionPane.showInputDialog(this, "Uredi trgovačkog predstavnika:", predstavnik);
        if (newPredstavnik == null) newPredstavnik = "";

        tableModel.setValueAt(newKomitent.trim(), modelRow, 0);
        tableModel.setValueAt(newPredstavnik.trim(), modelRow, 1);
        saveData();
    }

    /** Dodjela predstavnika odabranom komitentu */
    private void assignPredstavnik() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Odaberi komitenta kojem želiš dodijeliti predstavnika.");
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        String komitent = (String) tableModel.getValueAt(modelRow, 0);
        String newPredstavnik = JOptionPane.showInputDialog(this, "Unesi trgovačkog predstavnika za: " + komitent);
        if (newPredstavnik != null) {
            tableModel.setValueAt(newPredstavnik.trim(), modelRow, 1);
            saveData();
        }
    }

    /** Brisanje odabranog reda */
    private void deleteSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Odaberi red za brisanje.");
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Sigurno želiš obrisati odabrani red?",
                "Potvrda", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            String komitent = (String) tableModel.getValueAt(modelRow, 0);
            String predstavnik = (String) tableModel.getValueAt(modelRow, 1);
            KomitentiDatabaseHelper.deleteRow(komitent, predstavnik);
            tableModel.removeRow(modelRow);
        }
    }

    /** Filtriranje komitenata bez predstavnika */
    private void showKomitentiWithoutPredstavnik() {
    	// Postavljanje RowFilter-a koji uključuje samo redove gdje je predstavnik prazan ili null
        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            // Metoda koja određuje hoće li red biti uključen u filtrirani prikaz
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                Object predstavnik = entry.getValue(1);
                return predstavnik == null || predstavnik.toString().isBlank();
            }
        });
    }
}
