package ui;

import db.KomitentiDatabaseHelper;
import db.PredstavniciDatabaseHelper;
import model.KomitentInfo;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * KomitentiUI - verzija s importFromNarudzbeTable metodom i robusnijim combo editorom.
 *
 * Poboljšanja:
 * - combo.setLightWeightPopupEnabled(false) radi stabilnijeg popup ponašanja
 * - Sigurno prikazivanje popupa kod editora (invokeLater + isShowing check + try/catch)
 * - Ako nema predstavnika u bazi, u combo se dodaje placeholder "(nema predstavnika)"
 * - refreshPredstavniciCombo skuplja predstavnike iz više izvora bez dupliciranja (case-insensitive)
 * - importFromNarudzbeTable javni API za uvoz komitenata iz modela narudžbi
 */
public class KomitentiUI extends JFrame {

    private DefaultTableModel tableModel;
    private JTable table;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;
    private JPanel bottomPanel;

    public KomitentiUI() {
        super("Komitenti i Trgovački predstavnici (TEST)");
        System.out.println("KomitentiUI: konstruktor start");

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(6,6));

        initTable();
        loadData();
        initSearchPanel();
        initButtonsPanel();

        pack();
        setLocationRelativeTo(null);
        printBottomButtonsInfo();
        setVisible(true);
        System.out.println("KomitentiUI: konstruktor gotov i prozor vidljiv");
    }

    private void initTable() {
        tableModel = new DefaultTableModel(new Object[]{"Komitent", "Trg. predstavnik"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return column == 1; }
        };
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(26);

        // Create a combo editor wrapped in SafeComboEditor to avoid IllegalComponentStateException
        JComboBox<String> comboTemplate = new JComboBox<>();
        comboTemplate.setEditable(true);
        comboTemplate.setLightWeightPopupEnabled(false); // important fix
        refreshPredstavniciCombo(comboTemplate);

        TableColumn tpCol = table.getColumnModel().getColumn(1);
        tpCol.setCellEditor(new SafeComboEditor(comboTemplate));

        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /**
     * Safe combo editor which tries to show popup only when the editor component is actually showing.
     * Uses invokeLater and guards against IllegalComponentStateException.
     */
    private static class SafeComboEditor extends DefaultCellEditor {
        private final JComboBox<String> comboTemplate;

        public SafeComboEditor(JComboBox<String> template) {
            super(new JComboBox<>());
            this.comboTemplate = template;
            // keep the editor's combo model in sync initially
            JComboBox<?> ed = (JComboBox<?>) getComponent();
            ed.setEditable(true);
            ed.setLightWeightPopupEnabled(false);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            JComboBox<String> ed = (JComboBox<String>) getComponent();

            // copy items from template safely
            DefaultComboBoxModel<String> cm = new DefaultComboBoxModel<>();
            for (int i = 0; i < comboTemplate.getItemCount(); i++) {
                cm.addElement(comboTemplate.getItemAt(i));
            }
            ed.setModel(cm);

            // set value
            if (value != null) ed.setSelectedItem(value.toString());
            else ed.setSelectedItem("");

            // request focus and try to show popup safely
            SwingUtilities.invokeLater(() -> {
                try {
                    ed.requestFocusInWindow();
                    if (ed.isShowing()) {
                        // show popup only if visible on screen
                        ed.showPopup();
                    } else {
                        // Another small delay to allow rendering, guard with try/catch
                        SwingUtilities.invokeLater(() -> {
                            try {
                                if (ed.isShowing()) ed.showPopup();
                            } catch (IllegalComponentStateException ex) {
                                // ignore: component not shown yet
                                System.out.println("SafeComboEditor: showPopup aborted: " + ex.getMessage());
                            } catch (Exception ignored) {}
                        });
                    }
                } catch (IllegalComponentStateException ex) {
                    System.out.println("SafeComboEditor (outer) showPopup aborted: " + ex.getMessage());
                } catch (Exception ignored) {}
            });

            return ed;
        }

        @Override
        public Object getCellEditorValue() {
            return ((JComboBox<?>) getComponent()).getSelectedItem();
        }
    }

    /**
     * Popuni combo template s predstavnicima iz PredstavniciDatabaseHelper i KomitentiDatabaseHelper.
     * Ako nema rezultata, dodaj placeholder "(nema predstavnika)".
     */
    private void refreshPredstavniciCombo(JComboBox<String> combo) {
        // Collect unique names case-insensitively
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        combo.removeAllItems();
        set.add(""); // keep empty first

        try {
            List<String> p1 = PredstavniciDatabaseHelper.loadAllPredstavnici();
            if (p1 != null) for (String s : p1) if (s != null && !s.isBlank()) set.add(s.trim());
        } catch (Throwable t) {
            System.out.println("refreshPredstavniciCombo: " + t.getMessage());
        }
        try {
            List<String> p2 = KomitentiDatabaseHelper.loadAllPredstavnici();
            if (p2 != null) for (String s : p2) if (s != null && !s.isBlank()) set.add(s.trim());
        } catch (Throwable t) {
            System.out.println("refreshPredstavniciCombo(komitenti): " + t.getMessage());
        }

        // If only empty is present, add placeholder
        if (set.size() <= 1) {
            combo.addItem("");
            combo.addItem("(nema predstavnika)");
            return;
        }

        // otherwise populate combo preserving ordering
        for (String s : set) {
            combo.addItem(s);
        }
    }

    private void initSearchPanel() {
        JPanel top = new JPanel(new BorderLayout(6,6));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(new JLabel("Pretraga:"));
        searchField = new JTextField(28);
        left.add(searchField);
        top.add(left, BorderLayout.WEST);
        add(top, BorderLayout.NORTH);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void filter() {
                String txt = searchField.getText().trim();
                if (txt.isEmpty()) sorter.setRowFilter(null);
                else sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(txt), 0));
            }
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }
        });
    }

    private void initButtonsPanel() {
        bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JButton btnAdd = new JButton("Dodaj komitenta");
        JButton btnEdit = new JButton("Uredi");
        JButton btnAssign = new JButton("Dodijeli predstavnika");
        JButton btnDelete = new JButton("Obriši");
        JButton btnSave = new JButton("Spremi promjene");
        JButton btnShowEmpty = new JButton("Prikaži bez predstavnika");
        JButton btnShowAll = new JButton("Prikaži sve");
        JButton btnSearchDialog = new JButton("Traži komitenta (dialog)");
        JButton btnAssignEmpty = new JButton("Dodijeli TP praznima");
        JButton btnImportNarudzbe = new JButton("Uvezi iz narudžbi"); // new debug import button
        JButton btnTestAdd = new JButton("Test dodaj");
        JButton btnCheckDB = new JButton("Provjeri DB");

        bottomPanel.add(btnAdd);
        bottomPanel.add(btnEdit);
        bottomPanel.add(btnAssign);
        bottomPanel.add(btnDelete);
        bottomPanel.add(btnSave);
        bottomPanel.add(btnShowEmpty);
        bottomPanel.add(btnShowAll);
        bottomPanel.add(btnSearchDialog);
        bottomPanel.add(btnAssignEmpty);
        bottomPanel.add(btnImportNarudzbe);
        bottomPanel.add(btnTestAdd);
        bottomPanel.add(btnCheckDB);

        add(bottomPanel, BorderLayout.SOUTH);

        btnAdd.addActionListener(e -> addKomitent());
        btnEdit.addActionListener(e -> editSelected());
        btnAssign.addActionListener(e -> assignPredstavnik());
        btnDelete.addActionListener(e -> deleteSelected());
        btnSave.addActionListener(e -> saveData());
        btnShowEmpty.addActionListener(e -> showKomitentiWithoutPredstavnik());
        btnShowAll.addActionListener(e -> sorter.setRowFilter(null));
        btnSearchDialog.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) { JOptionPane.showMessageDialog(this, "Odaberi redak za unos komitenta prije poziva dijaloga."); return; }
            int modelRow = table.convertRowIndexToModel(viewRow);
            String odabrani = KomitentSearchDialog.showDialog(this);
            if (odabrani != null) {
                tableModel.setValueAt(odabrani, modelRow, 0);
                var map = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
                if (map != null) tableModel.setValueAt(map.getOrDefault(odabrani, ""), modelRow, 1);
            }
        });

        btnAssignEmpty.addActionListener(e -> {
            AssignTPDialog dlg = new AssignTPDialog(this, tableModel);
            dlg.setVisible(true);
            loadData();
            // refresh cell editor model after possible DB changes
            TableColumn tpCol = table.getColumnModel().getColumn(1);
            if (tpCol.getCellEditor() instanceof SafeComboEditor) {
                // recreate template and set new editor
                JComboBox<String> template = new JComboBox<>();
                template.setEditable(true);
                template.setLightWeightPopupEnabled(false);
                refreshPredstavniciCombo(template);
                tpCol.setCellEditor(new SafeComboEditor(template));
            }
        });

        // Open a small input dialog to ask for column index of komitentOpis in orders model.
        btnImportNarudzbe.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(this, "Unesi column index u orders model za komitentOpis (0-based):", "0");
            if (input == null) return;
            try {
                int col = Integer.parseInt(input.trim());
                // In practice, you should call importFromNarudzbeTable from the class that has ordersModel.
                // Here we just show placeholder usage: ask user to paste values.
                String pasted = JOptionPane.showInputDialog(this, "Zalijepi sve komitentOpis vrijednosti (jedan po retku) ili pritisni Cancel:");
                if (pasted == null) return;
                String[] lines = pasted.split("\\r?\\n");
                DefaultTableModel dummy = new DefaultTableModel(new Object[]{"kol0"}, 0);
                for (String line : lines) dummy.addRow(new Object[]{line});
                importFromNarudzbeTable(dummy, 0);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Krivi format indeksa: " + ex.getMessage());
            }
        });

        // DEBUG: programatski dodaj jedinstven testni komitent i spremi u DB
        btnTestAdd.addActionListener(e -> {
            String testName = "TEST_ADD_" + System.currentTimeMillis();
            System.out.println("KomitentiUI: Test dodaj -> " + testName);
            tableModel.addRow(new Object[]{testName, ""});
            saveData();
            List<Object[]> rows = KomitentiDatabaseHelper.loadAllRows();
            boolean found = false;
            if (rows != null) {
                for (Object[] r : rows) {
                    if (r.length > 0 && testName.equals(r[0])) { found = true; break; }
                }
            }
            System.out.println("KomitentiUI: Test dodaj - nakon save, pronadeno u DB = " + found);
            JOptionPane.showMessageDialog(this, "Test dodan: " + testName + "\nPronađen u DB: " + found);
            loadData();
        });

        btnCheckDB.addActionListener(e -> {
            List<Object[]> rows = KomitentiDatabaseHelper.loadAllRows();
            int count = rows == null ? 0 : rows.size();
            System.out.println("KomitentiUI: Provjeri DB -> loadAllRows size = " + count);
            if (rows != null) {
                for (int i = 0; i < Math.min(5, rows.size()); i++) {
                    Object[] r = rows.get(i);
                    System.out.println("  DB row " + i + ": " + (r.length>0?r[0]:"") + " -> " + (r.length>1?r[1]:""));
                }
            }
            JOptionPane.showMessageDialog(this, "Broj redova u DB: " + count);
        });
    }

    private void printBottomButtonsInfo() {
        if (bottomPanel == null) {
            System.out.println("KomitentiUI: bottomPanel je null");
            return;
        }
        Component[] comps = bottomPanel.getComponents();
        System.out.println("KomitentiUI: bottomPanel ima komponenti = " + comps.length);
        for (Component c : comps) {
            if (c instanceof JButton) {
                System.out.println("  - Gumb: \"" + ((JButton) c).getText() + "\"");
            } else {
                System.out.println("  - Komponenta: " + c.getClass().getSimpleName());
            }
        }
    }

    private void loadData() {
        tableModel.setRowCount(0);
        List<Object[]> rows = KomitentiDatabaseHelper.loadAllRows();
        if (rows != null) {
            for (Object[] r : rows) {
                String k = r.length > 0 && r[0] != null ? r[0].toString() : "";
                String p = r.length > 1 && r[1] != null ? r[1].toString() : "";
                tableModel.addRow(new Object[]{k, p});
            }
        }
        System.out.println("KomitentiUI: loadData - učitano " + tableModel.getRowCount() + " redova.");
    }

    /**
     * PUBLIC API: import missing komitenti iz tablice narudzbi.
     * ordersModel - DefaultTableModel iz NarudzbeUI
     * ordersColumnIndex - index stupca koji sadrži komitentOpis
     *
     * Dodaje svaki jedinstveni komitentOpis koji nije prisutan (case-insensitive)
     * u lokalni tableModel i poziva saveData() ako je bilo novih.
     */
    public void importFromNarudzbeTable(DefaultTableModel ordersModel, int ordersColumnIndex) {
        if (ordersModel == null) {
            JOptionPane.showMessageDialog(this, "ordersModel je null");
            return;
        }
        Set<String> found = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        // postojeći nazivi u trenutnom modelu (case-insensitive)
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            String v = safeString(tableModel.getValueAt(r, 0)).trim();
            if (!v.isBlank()) found.add(v);
        }
        // također učitaj iz baze (ako ima još kojih)
        Map<String, String> fromDb = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
        if (fromDb != null) found.addAll(fromDb.keySet());

        int added = 0;
        Set<String> toAdd = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (int r = 0; r < ordersModel.getRowCount(); r++) {
            Object o = null;
            try { o = ordersModel.getValueAt(r, ordersColumnIndex); } catch (Exception ex) { o = null; }
            if (o == null) continue;
            String naziv = o.toString().trim();
            if (naziv.isEmpty()) continue;
            if (!found.contains(naziv) && !toAdd.contains(naziv)) {
                toAdd.add(naziv);
            }
        }
        if (!toAdd.isEmpty()) {
            for (String k : toAdd) {
                tableModel.addRow(new Object[]{k, ""});
                added++;
            }
            System.out.println("KomitentiUI: importFromNarudzbeTable - dodano novih komitenata = " + added);
            // spremi odmah
            saveData();
            // refresh editor combo after DB changed
            TableColumn tpCol = table.getColumnModel().getColumn(1);
            JComboBox<String> template = new JComboBox<>();
            template.setEditable(true);
            template.setLightWeightPopupEnabled(false);
            refreshPredstavniciCombo(template);
            tpCol.setCellEditor(new SafeComboEditor(template));
        } else {
            System.out.println("KomitentiUI: importFromNarudzbeTable - nema novih komitenata za dodati");
            JOptionPane.showMessageDialog(this, "Nema novih komitenata za dodati.");
        }
    }

    // public metoda dostupna iz dijaloga
    public void saveData() {
        int rc = tableModel.getRowCount();
        List<KomitentInfo> lista = new ArrayList<>();
        for (int i = 0; i < rc; i++) {
            String kom = safeString(tableModel.getValueAt(i, 0));
            String tp = safeString(tableModel.getValueAt(i, 1));
            if (kom.isBlank()) continue;
            lista.add(new KomitentInfo(kom.trim(), tp.trim()));
        }
        System.out.println("KomitentiUI: saveData - spremam " + lista.size() + " zapisa u DB (poziv KomitentiDatabaseHelper.saveToDatabase)");
        KomitentiDatabaseHelper.saveToDatabase(lista);

        List<Object[]> rows = KomitentiDatabaseHelper.loadAllRows();
        int countAfterSave = rows == null ? 0 : rows.size();
        System.out.println("KomitentiUI: saveData - nakon save loadAllRows size = " + countAfterSave);

        loadData();
        JOptionPane.showMessageDialog(this, "Promjene spremljene. DB rows: " + countAfterSave);
    }

    // public metoda dostupna iz dijaloga
    public void applyAssignments(Map<String, String> assignments) {
        if (assignments == null || assignments.isEmpty()) return;
        System.out.println("KomitentiUI: applyAssignments - primljeno " + assignments.size() + " dodjela");
        for (Map.Entry<String, String> en : assignments.entrySet()) {
            String kom = en.getKey();
            String tp = en.getValue();
            if (kom == null || kom.trim().isEmpty()) continue;
            boolean applied = false;
            for (int r = 0; r < tableModel.getRowCount(); r++) {
                Object o = tableModel.getValueAt(r, 0);
                if (o != null && kom.equalsIgnoreCase(o.toString().trim())) {
                    tableModel.setValueAt(tp, r, 1);
                    applied = true;
                }
            }
            if (!applied) {
                tableModel.addRow(new Object[]{kom, tp});
            }
        }
    }

    private String safeString(Object o) { return o == null ? "" : o.toString(); }

    private boolean komitentExists(String kom) {
        kom = kom == null ? "" : kom.trim();
        if (kom.isEmpty()) return false;
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            if (kom.equalsIgnoreCase(safeString(tableModel.getValueAt(r, 0)))) return true;
        }
        return false;
    }

    private void addKomitent() {
        String naziv = JOptionPane.showInputDialog(this, "Unesi naziv komitenta:");
        if (naziv == null) return;
        naziv = naziv.trim();
        if (naziv.isEmpty()) return;
        if (komitentExists(naziv)) {
            JOptionPane.showMessageDialog(this, "Komitent već postoji.");
            return;
        }
        String tp = JOptionPane.showInputDialog(this, "Trgovački predstavnik (opcionalno):");
        if (tp == null) tp = "";
        tableModel.addRow(new Object[]{naziv, tp});
        saveData();
    }

    private void editSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) { JOptionPane.showMessageDialog(this, "Odaberi red za uređivanje."); return; }
        int modelRow = table.convertRowIndexToModel(viewRow);
        String kom = safeString(tableModel.getValueAt(modelRow, 0));
        String tp = safeString(tableModel.getValueAt(modelRow, 1));

        String nk = JOptionPane.showInputDialog(this, "Uredi naziv komitenta:", kom);
        if (nk == null || nk.trim().isEmpty()) return;
        if (!nk.equalsIgnoreCase(kom) && komitentExists(nk.trim())) {
            JOptionPane.showMessageDialog(this, "Komitent s tim nazivom već postoji.");
            return;
        }
        String ntp = JOptionPane.showInputDialog(this, "Uredi trgovačkog predstavnika:", tp);
        if (ntp == null) ntp = "";
        tableModel.setValueAt(nk.trim(), modelRow, 0);
        tableModel.setValueAt(ntp.trim(), modelRow, 1);
        saveData();
    }

    private void assignPredstavnik() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) { JOptionPane.showMessageDialog(this, "Odaberi komitenta."); return; }
        int modelRow = table.convertRowIndexToModel(viewRow);
        String kom = safeString(tableModel.getValueAt(modelRow, 0));
        String tp = JOptionPane.showInputDialog(this, "Unesi trgovačkog predstavnika za: " + kom);
        if (tp != null) {
            tableModel.setValueAt(tp.trim(), modelRow, 1);
            saveData();
        }
    }

    private void deleteSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) { JOptionPane.showMessageDialog(this, "Odaberi redak za brisanje."); return; }
        int modelRow = table.convertRowIndexToModel(viewRow);
        String kom = safeString(tableModel.getValueAt(modelRow, 0));
        if (JOptionPane.showConfirmDialog(this, "Obrisati komitenta: " + kom + " ?", "Potvrda", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            KomitentiDatabaseHelper.deleteRow(kom, safeString(tableModel.getValueAt(modelRow,1)));
            tableModel.removeRow(modelRow);
            loadData();
        }
    }

    private void showKomitentiWithoutPredstavnik() {
        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                Object tp = entry.getValue(1);
                return tp == null || tp.toString().isBlank() || "(nema predstavnika)".equals(tp.toString());
            }
        });
    }

    // Optional: small main for manual testing
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // ensure DB exists
            try { KomitentiDatabaseHelper.initializeDatabase(); } catch (Exception ignored) {}
            new KomitentiUI();
        });
    }
}