package ui;

import db.KomitentiDatabaseHelper;
import db.PredstavniciDatabaseHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * UI prozor za upravljanje trgovačkim predstavnicima. Prikazuje tablicu svih
 * predstavnika s mogućnošću dodavanja, uređivanja i brisanja. Koristi
 * PredstavniciDatabaseHelper klasu za interakciju s bazom podataka.
 */

public class PredstavniciUI extends JFrame {


    private final DefaultTableModel tableModel;
    private final JTable table;
// Konstruktor

    public PredstavniciUI() {
        setTitle("Upravljanje trgovačkim predstavnicima");
        setSize(500, 350);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Model: ID je neurediv, Naziv je urediv
        tableModel = new DefaultTableModel(new Object[]{"ID", "Naziv"}, 0) {
            @Override public boolean isCellEditable(int row, int col) {
                return col == 1; // dozvoli uređivanje samo naziva
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(24);

        // Editor za kolonu "Naziv" — odabir iz postojećih predstavnika (bez ručnog unosa)
        int viewIdxPred = table.convertColumnIndexToView(1);
        if (viewIdxPred >= 0) {
        	// učitaj sve predstavnike iz baze
        	// koristi se KomitentiDatabaseHelper
        	// jer su predstavnici vezani uz komitente
        	// npr. prilikom dodavanja komitenta
        	// se bira predstavnik iz liste
        	// lista se učitava iz baze
        	// KomitentiDatabaseHelper.loadAllPredstavnici()
            List<String> predstavnici = KomitentiDatabaseHelper.loadAllPredstavnici();
            JComboBox<String> comboPred = new JComboBox<>(predstavnici.toArray(new String[0]));
            comboPred.setEditable(false);
            table.getColumnModel().getColumn(viewIdxPred).setCellEditor(new DefaultCellEditor(comboPred));
        }

        // Gumbi
        JButton btnAdd = new JButton("Dodaj");
        btnAdd.addActionListener(e -> addPredstavnik());

        JButton btnDelete = new JButton("Obriši");
        btnDelete.addActionListener(e -> deleteSelected());

        JButton btnSave = new JButton("Spremi promjene");
        btnSave.addActionListener(e -> saveChanges());

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bottom.add(btnAdd);
        bottom.add(btnDelete);
        bottom.add(btnSave);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        loadData();
        setVisible(true);
    }

    /** Učitava sve predstavnike iz baze u tablicu */
    //tableModel
    
    private void loadData() {
        tableModel.setRowCount(0);
        List<String> lista = PredstavniciDatabaseHelper.loadAllPredstavnici();
        int id = 1;
        for (String naziv : lista) {
            tableModel.addRow(new Object[]{id++, naziv});
        }
    }

    /** Dodaje novog predstavnika */
    private void addPredstavnik() {
        String naziv = JOptionPane.showInputDialog(this, "Unesi naziv novog predstavnika:");
        if (naziv != null && !naziv.isBlank()) {
            PredstavniciDatabaseHelper.addPredstavnik(naziv.trim());
            loadData();
        }
    }

    /** Briše selektiranog predstavnika */
    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            int id = (int) tableModel.getValueAt(row, 0);
            if (JOptionPane.showConfirmDialog(this,
                    "Obrisati predstavnika ID " + id + "?",
                    "Potvrda", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                PredstavniciDatabaseHelper.deletePredstavnik(id);
                loadData();
            }
        } else {
            JOptionPane.showMessageDialog(this, "Odaberi predstavnika za brisanje.", "Upozorenje", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Sprema promjene naziva u bazu */
    private void saveChanges() {
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            int id = (int) tableModel.getValueAt(r, 0);
            String naziv = (String) tableModel.getValueAt(r, 1);
            PredstavniciDatabaseHelper.updatePredstavnik(id, naziv);
        }
        JOptionPane.showMessageDialog(this, "Promjene spremljene.", "Info", JOptionPane.INFORMATION_MESSAGE);
        loadData();
    }
}
