package ui;

import db.KomitentiDatabaseHelper;
import db.PredstavniciDatabaseHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * AssignTPDialog - dijalog za dodjelu TP onima koji trenutno nemaju TP.
 * Popravak: prije čitanja modela za spremanje poziva se stopCellEditing()
 * ako je neki editor otvoren, te koristi heavyweight popup za combo.
 */
public class AssignTPDialog extends JDialog {

    private final DefaultTableModel model;
    private final JTable table;
    private final JComboBox<String> tpComboPrototype;

    public AssignTPDialog(Window parent, DefaultTableModel mainTableModel) {
        super(parent, "Dodijeli trgovačke predstavnike", ModalityType.APPLICATION_MODAL);
        System.out.println("AssignTPDialog: otvoren");

        setLayout(new BorderLayout(8,8));
        ((JComponent)getContentPane()).setBorder(BorderFactory.createEmptyBorder(8,8,8,8));

        model = new DefaultTableModel(new Object[]{"Komitent", "Trg. predstavnik"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return col == 1; }
        };

        Map<String, String> fromDb = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
        if (fromDb == null) fromDb = Collections.emptyMap();

        Set<String> allKom = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        allKom.addAll(fromDb.keySet());

        if (mainTableModel != null) {
            int colOpis = -1;
            try { colOpis = mainTableModel.findColumn("Komitent"); } catch (Exception ignored) {}
            if (colOpis < 0) {
                try { colOpis = mainTableModel.findColumn("komitentOpis"); } catch (Exception ignored) {}
            }
            if (colOpis < 0) colOpis = 0;
            for (int r = 0; r < mainTableModel.getRowCount(); r++) {
                Object o = mainTableModel.getValueAt(r, colOpis);
                if (o != null) {
                    String s = o.toString().trim();
                    if (!s.isEmpty()) allKom.add(s);
                }
            }
        }

        for (String k : allKom) {
            String tp = fromDb.getOrDefault(k, "");
            if (mainTableModel != null) {
                int colOpis = -1;
                int colTP = -1;
                try { colOpis = mainTableModel.findColumn("Komitent"); } catch (Exception ignored) {}
                if (colOpis < 0) {
                    try { colOpis = mainTableModel.findColumn("komitentOpis"); } catch (Exception ignored) {}
                }
                try { colTP = mainTableModel.findColumn("Trg. predstavnik"); } catch (Exception ignored) {}
                if (colTP < 0) {
                    try { colTP = mainTableModel.findColumn("trgovackiPredstavnik"); } catch (Exception ignored) {}
                }
                if (colOpis >= 0 && colTP >= 0) {
                    for (int r = 0; r < mainTableModel.getRowCount(); r++) {
                        Object o = mainTableModel.getValueAt(r, colOpis);
                        if (o != null && k.equalsIgnoreCase(o.toString().trim())) {
                            Object tpObj = mainTableModel.getValueAt(r, colTP);
                            if (tpObj != null && !tpObj.toString().isBlank()) {
                                tp = tpObj.toString();
                                break;
                            }
                        }
                    }
                }
            }
            if (tp == null) tp = "";
            if (tp.isBlank()) {
                model.addRow(new Object[]{k, ""});
            }
        }

        table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        add(new JScrollPane(table), BorderLayout.CENTER);

        tpComboPrototype = new JComboBox<>();
        tpComboPrototype.setEditable(true);
        tpComboPrototype.setLightWeightPopupEnabled(false); // important fix for showing popup
        refreshTPItems(tpComboPrototype);
        tpComboPrototype.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) { refreshTPItems(tpComboPrototype); }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });

        TableColumn tpCol = table.getColumnModel().getColumn(1);
        tpCol.setCellEditor(new DefaultCellEditor(tpComboPrototype));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSave = new JButton("Spremi");
        JButton btnCancel = new JButton("Odustani");
        bottom.add(btnSave);
        bottom.add(btnCancel);
        add(bottom, BorderLayout.SOUTH);

        btnSave.addActionListener(ev -> {
            // Ako je korisnik trenutno u edit modu, osiguraj da se vrijednost iz editor-a prijeđe u model
            if (table.isEditing()) {
                TableCellEditor editor = table.getCellEditor();
                if (editor != null) {
                    try {
                        editor.stopCellEditing();
                    } catch (Exception ex) {
                        System.out.println("AssignTPDialog: greška pri stopCellEditing: " + ex.getMessage());
                    }
                }
            }

            int rowsInDialog = model.getRowCount();
            System.out.println("AssignTPDialog: model rows = " + rowsInDialog);

            Map<String, String> assignments = new LinkedHashMap<>();
            for (int r = 0; r < model.getRowCount(); r++) {
                String kom = Objects.toString(model.getValueAt(r,0),"").trim();
                String tp = Objects.toString(model.getValueAt(r,1),"").trim();
                if (!kom.isEmpty() && !tp.isEmpty()) assignments.put(kom, tp);
            }

            System.out.println("AssignTPDialog: prikupljeno assignments.size() = " + assignments.size());

            if (parent instanceof KomitentiUI) {
                KomitentiUI kUI = (KomitentiUI) parent;
                if (!assignments.isEmpty()) {
                    kUI.applyAssignments(assignments);
                    kUI.saveData();
                    System.out.println("AssignTPDialog: assignments primijenjene i spremljene preko parent UI.");
                } else {
                    System.out.println("AssignTPDialog: nema assignments za primjenu.");
                }
            } else if (!assignments.isEmpty()) {
                List<model.KomitentInfo> list = new ArrayList<>();
                for (Map.Entry<String, String> en : assignments.entrySet()) {
                    list.add(new model.KomitentInfo(en.getKey(), en.getValue()));
                }
                KomitentiDatabaseHelper.saveToDatabase(list);
                System.out.println("AssignTPDialog: assignments spremljene direktno u DB (fallback).");
            } else {
                System.out.println("AssignTPDialog: ništa za spremiti (fallback).");
            }

            dispose();
        });

        btnCancel.addActionListener(ev -> dispose());

        setPreferredSize(new Dimension(560,420));
        pack();
        setLocationRelativeTo(parent);
    }

    private void refreshTPItems(JComboBox<String> combo) {
        combo.removeAllItems();
        combo.addItem("");
        try {
            List<String> p1 = PredstavniciDatabaseHelper.loadAllPredstavnici();
            if (p1 != null) for (String s : p1) if (s != null && !s.isBlank()) combo.addItem(s);
        } catch (Throwable ignored) {}
        try {
            List<String> p2 = KomitentiDatabaseHelper.loadAllPredstavnici();
            if (p2 != null) for (String s : p2) if (s != null && !s.isBlank() && !containsIgnoreCase(combo, s)) combo.addItem(s);
        } catch (Throwable ignored) {}
    }

    private boolean containsIgnoreCase(JComboBox<String> combo, String val) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            String it = combo.getItemAt(i);
            if (it != null && it.equalsIgnoreCase(val)) return true;
        }
        return false;
    }
}