package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import db.KomitentiDatabaseHelper;

import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.*;
import java.util.stream.Collectors;

//** Modal dialog for searching and selecting a "komitent" (client/partner).

public final class KomitentSearchDialog extends JDialog {
    private final JTextField searchField = new JTextField(24);
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final TableRowSorter<DefaultTableModel> sorter;
    private String selectedOpis;

    // --- konstruktor ---
    // private, koristi se samo kroz static showDialog metodu
    // data: map opis -> trg.predstavnik
    // title: naslov dijaloga
    // parent: roditeljski window
    // rezultat: odabrani opis ili null
    // ako je otkazano
    // primjer poziva:
    // String komitent = KomitentSearchDialog.showDialog(this);
    // ako je komitent null, korisnik je otkazao
    // inače je odabrani opis komitenta
    // map se učitava iz baze
    // KomitentiDatabaseHelper.loadKomitentPredstavnikMap()
    // map može biti i prazna
    // tada će tablica biti prazna
    private KomitentSearchDialog(Window parent, Map<String, String> data, String title) {
        super(parent, title, ModalityType.APPLICATION_MODAL);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        // --- priprema modela s dvije kolone ---
        tableModel = new DefaultTableModel(new Object[]{"Opis", "TP"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        data.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(e -> tableModel.addRow(new Object[]{e.getKey(), e.getValue()}));

        // --- search field ---
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(new JLabel("Pretraži komitente:"), BorderLayout.WEST);
        top.add(searchField, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        // --- JTable ---
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        JScrollPane sp = new JScrollPane(table);
        add(sp, BorderLayout.CENTER);

        // --- bottom panel ---
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnOk = new JButton("Odaberi");
        JButton btnCancel = new JButton("Odustani");
        bottom.add(btnOk);
        bottom.add(btnCancel);
        add(bottom, BorderLayout.SOUTH);

        // --- filtriranje ---
        
        // filtrira tablicu prema unosu u searchField
        // koristi regex filter, ne osjetljivo na velika/mala slova
        // escape-ira posebne znakove
        // ako je polje prazno, uklanja filter
        // poziva se na svaki update dokumenta
        // primjer: unos "abc" prikazuje sve redove koji u bilo kojoj koloni sadrže "abc" (velika/mala slova nebitna)
        // ako je unos prazan, prikazuju se svi redovi
        // koristi Pattern.quote za escape-iranje posebnih znakova
        // koristi (?i) za neosjetljivost na velika/mala slova
        
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        // --- listeners ---
        btnOk.addActionListener(e -> commitSelection());
        btnCancel.addActionListener(e -> cancel());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() != -1) {
                    commitSelection();
                }
            }
        });

        getRootPane().setDefaultButton(btnOk);
        getRootPane().registerKeyboardAction(ev -> cancel(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setPreferredSize(new Dimension(520, 420));
        pack();
        setLocationRelativeTo(parent);
    }

    // --- private metode ---
    // filtrira tablicu prema unosu u searchField
    private void applyFilter() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
        }
    }
// potvrđuje odabir
    // dohvaća odabrani redak iz view-a
    // konvertira u model indeks
    // dohvaća opis iz modela (prva kolona)
    // sprema u selectedOpis
    // zatvara dijalog
    private void commitSelection() {
        int viewRow = table.getSelectedRow();
        if (viewRow >= 0) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            selectedOpis = (String) tableModel.getValueAt(modelRow, 0); // samo opis
        }
        dispose();
    }
// otkazuje odabir
    // postavlja selectedOpis na null
    private void cancel() {
        selectedOpis = null;
        dispose();
    }

    // --- public API ---
    // prikazuje dijalog
    // parent: roditeljski window
    // rezultat: odabrani opis ili null ako je otkazano
    // učitava mapu iz baze
    // ako je mapa null, koristi praznu mapu
    // poziva konstruktor i prikazuje dijalog
    // vraća selectedOpis nakon zatvaranja
    // primjer poziva:
    //	String komitent = KomitentSearchDialog.showDialog(this);
    // ako je komitent null, korisnik je otkazao
    //	inače je odabrani opis komitenta
    // map se učitava iz baze
    // KomitentiDatabaseHelper.loadKomitentPredstavnikMap()
    public static String showDialog(Window parent) {
        Map<String, String> map = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
        if (map == null) map = Collections.emptyMap();
        KomitentSearchDialog dlg = new KomitentSearchDialog(parent, map, "Odabir komitenta");
        dlg.setVisible(true);
        return dlg.selectedOpis;
    }
}
