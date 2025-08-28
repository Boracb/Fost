package ui;

import db.KomitentiDatabaseHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Modal dialog for searching and selecting a "komitent" (client/partner).
 * Returns only the opis (name) of selected komitent or null.
 */
public final class KomitentSearchDialog extends JDialog {
    private final JTextField searchField = new JTextField(24);
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final TableRowSorter<DefaultTableModel> sorter;
    private String selectedOpis;
    private final JButton btnOk;

    private KomitentSearchDialog(Window parent, Map<String, String> data, String title) {
        super(parent, title, ModalityType.APPLICATION_MODAL);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        tableModel = new DefaultTableModel(new Object[]{"Opis", "TP"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        loadMapIntoModel(data);

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(new JLabel("Pretraži komitente:"), BorderLayout.WEST);
        top.add(searchField, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnAdd = new JButton("Dodaj komitenta");
        btnOk = new JButton("Odaberi");
        JButton btnCancel = new JButton("Odustani");
        btnOk.setEnabled(false);
        bottom.add(btnAdd);
        bottom.add(btnOk);
        bottom.add(btnCancel);
        add(bottom, BorderLayout.SOUTH);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        table.getSelectionModel().addListSelectionListener(e -> btnOk.setEnabled(table.getSelectedRow() != -1));

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() != -1) commitSelection();
            }
        });

        btnOk.addActionListener(e -> commitSelection());
        btnCancel.addActionListener(e -> cancel());
        btnAdd.addActionListener(e -> addNewKomitent());

        getRootPane().setDefaultButton(btnOk);
        getRootPane().registerKeyboardAction(ev -> cancel(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(() -> searchField.requestFocusInWindow());
            }
        });

        setPreferredSize(new Dimension(520, 420));
        pack();
        setLocationRelativeTo(parent);
    }

    private void loadMapIntoModel(Map<String, String> data) {
        tableModel.setRowCount(0);
        if (data == null || data.isEmpty()) return;
        data.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(e -> tableModel.addRow(new Object[]{e.getKey(), e.getValue() != null ? e.getValue() : ""}));
    }

    private void applyFilter() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) sorter.setRowFilter(null);
        else sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
    }

    private void commitSelection() {
        int viewRow = table.getSelectedRow();
        if (viewRow >= 0) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            selectedOpis = (String) tableModel.getValueAt(modelRow, 0);
        } else selectedOpis = null;
        dispose();
    }

    private void cancel() {
        selectedOpis = null;
        dispose();
    }

    private void addNewKomitent() {
        String name = JOptionPane.showInputDialog(this, "Naziv komitenta:");
        if (name == null) return;
        name = name.trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Naziv ne smije biti prazan.", "Greška", JOptionPane.ERROR_MESSAGE);
            return;
        }
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            String existing = (String) tableModel.getValueAt(r, 0);
            if (existing != null && existing.equalsIgnoreCase(name)) {
                JOptionPane.showMessageDialog(this, "Komitent već postoji u listi.", "Info", JOptionPane.INFORMATION_MESSAGE);
                int viewRow = table.convertRowIndexToView(r);
                if (viewRow >= 0) table.setRowSelectionInterval(viewRow, viewRow);
                return;
            }
        }
        String tp = JOptionPane.showInputDialog(this, "Trgovački predstavnik (opcionalno):");
        if (tp == null) tp = "";
        try {
            KomitentiDatabaseHelper.insertIfNotExists(name, tp);
        } catch (Throwable t) {
            t.printStackTrace();
            JOptionPane.showMessageDialog(this, "Neuspjelo spremanje u bazu:\n" + t.getMessage(), "Greška", JOptionPane.ERROR_MESSAGE);
            return;
        }
        var reload = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
        if (reload == null) reload = Collections.emptyMap();
        loadMapIntoModel(reload);
        // select new entry
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            String val = (String) tableModel.getValueAt(r, 0);
            if (val != null && val.equalsIgnoreCase(name)) {
                int viewRow = table.convertRowIndexToView(r);
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                }
                break;
            }
        }
    }

    public static String showDialog(Window parent) {
        var map = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
        if (map == null) map = Collections.emptyMap();
        KomitentSearchDialog dlg = new KomitentSearchDialog(parent, map, "Odabir komitenta");
        dlg.setVisible(true);
        return dlg.selectedOpis;
    }
}