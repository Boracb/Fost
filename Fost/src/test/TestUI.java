package test;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import db.KomitentiDatabaseHelper;

import java.awt.*;
import java.util.List;

public class TestUI {
    private JFrame frame;
    private JTable table;
    private DefaultTableModel tableModel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            KomitentiDatabaseHelper.initializeDatabase();
            new TestUI().createAndShowGUI();
        });
    }

    private void createAndShowGUI() {
        frame = new JFrame("TEST Komitent/Predstavnik");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 400);
        frame.setLocationRelativeTo(null);

        // Model samo s dvije kolone
        tableModel = new DefaultTableModel(new Object[]{"komitentOpis", "trgovackiPredstavnik"}, 0);
        table = new JTable(tableModel);

        // Učitaj iz baze u model
        List<Object[]> data = KomitentiDatabaseHelper.loadAllRows();
        for (Object[] row : data) {
            tableModel.addRow(row);
        }

        // Podesi dropdownove
        setUpKomitentDropdown();
        setUpPredstavnikDropdown();

        frame.add(new JScrollPane(table), BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private void setUpKomitentDropdown() {
        int viewIdx = table.convertColumnIndexToView(0); // kolona komitentOpis
        if (viewIdx < 0) return;

        List<String> komitenti = KomitentiDatabaseHelper.loadAllKomitentNames();
        JComboBox<String> combo = new JComboBox<>(komitenti.toArray(new String[0]));
        combo.setEditable(true);

        combo.addActionListener(e -> {
            Object selObj = combo.getSelectedItem();
            if (selObj != null) {
                String noviKomitent = selObj.toString().trim();
                int row = table.getSelectedRow();
                if (row >= 0) {
                    int modelRow = table.convertRowIndexToModel(row);
                    Object predstavnikObj = tableModel.getValueAt(modelRow, 1);
                    String trenutniPredstavnik = predstavnikObj != null ? predstavnikObj.toString() : "";

                    tableModel.setValueAt(noviKomitent, modelRow, 0);
                    KomitentiDatabaseHelper.insertIfNotExists(noviKomitent, trenutniPredstavnik);

                    combo.removeAllItems();
                    for (String k : KomitentiDatabaseHelper.loadAllKomitentNames()) {
                        combo.addItem(k);
                    }
                    setUpPredstavnikDropdown();
                }
            }
        });

        table.getColumnModel().getColumn(viewIdx).setCellEditor(new DefaultCellEditor(combo));
    }

    private void setUpPredstavnikDropdown() {
        int viewIdx = table.convertColumnIndexToView(1); // kolona trgovackiPredstavnik
        if (viewIdx < 0) return;

        List<String> predstavnici = KomitentiDatabaseHelper.loadAllPredstavnici();
        JComboBox<String> combo = new JComboBox<>(predstavnici.toArray(new String[0]));
        combo.setEditable(true);

        combo.addActionListener(e -> {
            Object selObj = combo.getSelectedItem();
            if (selObj != null) {
                String noviPredstavnik = selObj.toString().trim();
                int row = table.getSelectedRow();
                if (row >= 0) {
                    int modelRow = table.convertRowIndexToModel(row);
                    Object komitentObj = tableModel.getValueAt(modelRow, 0);
                    String trenutniKomitent = komitentObj != null ? komitentObj.toString() : "";

                    tableModel.setValueAt(noviPredstavnik, modelRow, 1);
                    KomitentiDatabaseHelper.insertIfNotExists(trenutniKomitent, noviPredstavnik);

                    combo.removeAllItems();
                    for (String p : KomitentiDatabaseHelper.loadAllPredstavnici()) {
                        combo.addItem(p);
                    }
                }
            }
        });

        table.getColumnModel().getColumn(viewIdx).setCellEditor(new DefaultCellEditor(combo));
    }
}
