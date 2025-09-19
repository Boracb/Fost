package ui;

import db.PredstavniciDatabaseHelper;
import db.KomitentiDatabaseHelper;
import model.PredstavnikInfo;
import model.KomitentInfo;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * UI prozor za upravljanje trgovačkim predstavnicima.
 *
 * Poboljšanja u odnosu na original:
 * - koristi loadAllWithIds() za dohvat stvarnih DB ID-eva
 * - sakriva ID stupac po defaultu (može se prikazati klikom na gumb)
 * - sprječava brisanje predstavnika koji je dodijeljen komitentima bez potvrde;
 *   nudi opciju uklanjanja predstavnika iz svih komitenata (postavljanje TP = "")
 * - validira duplicirane nazive pri spremanju i kod dodavanja
 * - bolje rukovanje konverzijama model/view indeksa
 */
public class PredstavniciUI extends JFrame {

    private final DefaultTableModel tableModel;
    private final JTable table;
    private TableColumn idColumn; // držimo referencu za toggle prikaza ID stupca
    private boolean idVisible = false;

    public PredstavniciUI() {
        setTitle("Upravljanje trgovačkim predstavnicima");
        setSize(520, 380);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        tableModel = new DefaultTableModel(new Object[]{"ID", "Naziv"}, 0) {
            @Override public boolean isCellEditable(int row, int col) {
                return col == 1;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(24);

        // Buttons
        JButton btnAdd = new JButton("Dodaj");
        btnAdd.addActionListener(e -> addPredstavnik());

        JButton btnDelete = new JButton("Obriši");
        btnDelete.addActionListener(e -> deleteSelected());

        JButton btnSave = new JButton("Spremi promjene");
        btnSave.addActionListener(e -> saveChanges());

        JButton btnToggleId = new JButton("Prikaži ID");
        btnToggleId.addActionListener(e -> toggleIdColumn(btnToggleId));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bottom.add(btnAdd);
        bottom.add(btnDelete);
        bottom.add(btnSave);
        bottom.add(btnToggleId);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        loadData();
        // hide ID column by default (keep reference)
        SwingUtilities.invokeLater(() -> {
            if (table.getColumnModel().getColumnCount() > 0) {
                idColumn = table.getColumnModel().getColumn(0);
                hideIdColumn();
            }
        });

        setVisible(true);
    }

    private void loadData() {
        tableModel.setRowCount(0);
        List<PredstavnikInfo> lista = PredstavniciDatabaseHelper.loadAllWithIds();
        if (lista == null) return;
        for (PredstavnikInfo p : lista) {
            tableModel.addRow(new Object[]{p.getId(), p.getNaziv()});
        }
    }

    private void addPredstavnik() {
        String naziv = JOptionPane.showInputDialog(this, "Unesi naziv novog predstavnika:");
        if (naziv == null) return;
        naziv = naziv.trim();
        if (naziv.isEmpty()) return;

        // provjeri duplikat u tabeli (case-insensitive)
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            String existing = Objects.toString(tableModel.getValueAt(r, 1), "");
            if (existing.equalsIgnoreCase(naziv)) {
                JOptionPane.showMessageDialog(this, "Predstavnik s tim nazivom već postoji.", "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }

        PredstavniciDatabaseHelper.addPredstavnik(naziv);
        loadData();
    }

    private void deleteSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Odaberi predstavnika za brisanje.", "Upozorenje", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        Object idObj = tableModel.getValueAt(modelRow, 0);
        Object nazivObj = tableModel.getValueAt(modelRow, 1);
        if (idObj == null || nazivObj == null) return;
        int id = (int) idObj;
        String naziv = nazivObj.toString();

        // provjeri koristi li netko ovog predstavnika (u tablici komitenata)
        Map<String, String> komMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
        List<String> usingKomitenti = new ArrayList<>();
        if (komMap != null) {
            for (Map.Entry<String, String> en : komMap.entrySet()) {
                if (en.getValue() != null && en.getValue().equalsIgnoreCase(naziv)) {
                    usingKomitenti.add(en.getKey());
                }
            }
        }

        if (!usingKomitenti.isEmpty()) {
            // warn and offer options
            String msg = String.format("Predstavnik \"%s\" je dodijeljen %d komitentu(ima).\n" +
                    "Ako ga obrišeš, komitentima će biti uklonjen trgovački predstavnik (TP postaje prazan).\nŽeliš li nastaviti?", naziv, usingKomitenti.size());
            int opt = JOptionPane.showConfirmDialog(this, msg, "Predstavnik u uporabi", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (opt != JOptionPane.YES_OPTION) return;

            // clear predstavnik for those komitenti
            try {
                List<Object[]> rows = KomitentiDatabaseHelper.loadAllRows();
                List<KomitentInfo> newList = new ArrayList<>();
                for (Object[] row : rows) {
                    String kom = row.length > 0 && row[0] != null ? row[0].toString() : "";
                    String tp = row.length > 1 && row[1] != null ? row[1].toString() : "";
                    if (tp.equalsIgnoreCase(naziv)) tp = "";
                    newList.add(new KomitentInfo(kom, tp));
                }
                // save updated komitenti (this will overwrite komitenti table with cleared TP)
                KomitentiDatabaseHelper.saveToDatabase(newList);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Greška pri uklanjanju predstavnika iz komitenata:\n" + ex.getMessage(), "Greška", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // finally delete predstavnik
        PredstavniciDatabaseHelper.deletePredstavnik(id);
        loadData();
        JOptionPane.showMessageDialog(this, "Predstavnik obrisan.", "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private void saveChanges() {
        // validate unique names (case-insensitive)
        Set<String> seen = new HashSet<>();
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            String naziv = Objects.toString(tableModel.getValueAt(r, 1), "").trim();
            if (naziv.isEmpty()) continue;
            String key = naziv.toLowerCase(Locale.ROOT);
            if (seen.contains(key)) {
                JOptionPane.showMessageDialog(this, "Ima dupliciranih naziva predstavnika. Ispravi prije spremanja.", "Greška", JOptionPane.ERROR_MESSAGE);
                return;
            }
            seen.add(key);
        }

        // perform updates
        try {
            for (int r = 0; r < tableModel.getRowCount(); r++) {
                int id = (int) tableModel.getValueAt(r, 0);
                String naziv = Objects.toString(tableModel.getValueAt(r, 1), "").trim();
                PredstavniciDatabaseHelper.updatePredstavnik(id, naziv);
            }
            JOptionPane.showMessageDialog(this, "Promjene spremljene.", "Info", JOptionPane.INFORMATION_MESSAGE);
            loadData();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Greška pri spremanju:\n" + ex.getMessage(), "Greška", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleIdColumn(JButton btnToggle) {
        if (idColumn == null) {
            if (table.getColumnModel().getColumnCount() > 0) idColumn = table.getColumnModel().getColumn(0);
            else return;
        }
        if (idVisible) {
            hideIdColumn();
            btnToggle.setText("Prikaži ID");
        } else {
            showIdColumn();
            btnToggle.setText("Sakrij ID");
        }
    }

    private void hideIdColumn() {
        try {
            if (idColumn != null && table.getColumnModel().getColumnIndex("ID") >= 0) {
                table.getColumnModel().removeColumn(idColumn);
            }
        } catch (IllegalArgumentException ignored) {}
        idVisible = false;
    }

    private void showIdColumn() {
        try {
            // if the column is missing, re-create/insert at position 0 from model
            // Build a new TableColumn with index 0
            TableColumn col = new TableColumn(0);
            col.setHeaderValue("ID");
            table.getColumnModel().addColumn(col);
            // move to first position
            int last = table.getColumnModel().getColumnCount() - 1;
            table.getColumnModel().moveColumn(last, 0);
            // store reference
            idColumn = table.getColumnModel().getColumn(0);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        idVisible = true;
        // reload to ensure IDs match
        loadData();
    }
}