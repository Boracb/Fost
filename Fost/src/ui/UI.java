package ui;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import com.toedter.calendar.JDateChooser;
import db.DatabaseHelper;
import db.KomitentiDatabaseHelper;
import db.UserDatabaseHelper;
import excel.ExcelExporter;
import excel.ExcelImporter;
import logic.DateUtils;
import logic.WorkingTimeCalculator;
import util.ActionLogger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.*;
import java.awt.event.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;

/**
 * Glavna UI klasa aplikacije.
 * Spaja stare značajke tvoje klase i nove promjene (robustni recomputeDuration koji koristi
 * WorkingTimeCalculator.calculateWorkingMinutes, dodatni editori/renderer-i i admin funkcije).
 */
public class UI {

    // --- Polja klase ---
    private JFrame frame;
    private JTable table;
    private DefaultTableModel tableModel;
    private String prijavljeniKorisnik;
    private String ulogaKorisnika;
    private javax.swing.Timer inactivityTimer;
    private final int INACTIVITY_DELAY = 60_000;
    private java.util.Map<Integer, java.util.List<String>> povijestPromjena = new java.util.HashMap<>();
    private Set<Integer> odmrznutiModelRedovi;
    private TableRowSorter<DefaultTableModel> sorter;
    private final String[] djelatnici = {"", "Marko", "Ivana", "Petra", "Boris", "Ana"};
    private Map<String, String> komitentTPMap;

    private final String[] columnNames = {
            "datumNarudzbe","predDatumIsporuke","komitentOpis",
            "nazivRobe","netoVrijednost","kom","status",
            "djelatnik","mm","m","tisucl","m2","startTime","endTime","duration",
            "trgovackiPredstavnik"
    };

    // Konstante — indeksi temeljeni na modelu
    private static final int STATUS_COL_MODEL = 6;
    private static final int START_TIME_COL   = 12;
    private static final int END_TIME_COL     = 13;
    private static final int DURATION_COL     = 14;
    private static final int KOMITENT_OPIS_COL = 2;
    private static final int TP_COL            = 15;

    private JTextField searchField; // polje pretrage

    // Konstruktor
    public UI(String korisnik, String uloga) {
        this.prijavljeniKorisnik = korisnik;
        this.ulogaKorisnika = uloga;
    }

    /**
     * Kreira i prikazuje glavni GUI aplikacije.
     */
    void createAndShowGUI() {
        odmrznutiModelRedovi = new HashSet<>();
        initUnlockHistoryData();

        frame = new JFrame("Fost – Excel Uvoz i SQLite");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setLocationRelativeTo(null);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int res = JOptionPane.showConfirmDialog(
                        frame,
                        "Želite li spremiti podatke prije zatvaranja?",
                        "Spremi podatke",
                        JOptionPane.YES_NO_CANCEL_OPTION
                );
                if (res == JOptionPane.YES_OPTION) {
                    DatabaseHelper.saveToDatabase(tableModel);
                    UserDatabaseHelper.saveUserTableSettings(prijavljeniKorisnik, table);
                    frame.dispose();
                } else if (res == JOptionPane.NO_OPTION) {
                    frame.dispose();
                }
            }
        });

        inactivityTimer = new javax.swing.Timer(INACTIVITY_DELAY, e -> {
            UserDatabaseHelper.saveUserTableSettings(prijavljeniKorisnik, table);
            frame.dispose();
            new LoginUI();
        });
        inactivityTimer.setRepeats(false);
        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> resetInactivityTimer(),
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        inactivityTimer.start();

        // Model tablice
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int modelRow, int modelCol) {
                if (modelCol == KOMITENT_OPIS_COL) return false; // zabrana tipkanja u komitentOpis

                Object statusVal = getValueAt(modelRow, STATUS_COL_MODEL);
                String status = statusVal == null ? "" : statusVal.toString();
                boolean otkljucanRed = "Administrator".equalsIgnoreCase(ulogaKorisnika)
                        && odmrznutiModelRedovi.contains(modelRow);

                if ("Izrađeno".equals(status)) {
                    if (!otkljucanRed) return false;
                    return modelCol == START_TIME_COL
                            || modelCol == END_TIME_COL
                            || modelCol == STATUS_COL_MODEL;
                }
                if (otkljucanRed) {
                    return modelCol == START_TIME_COL
                            || modelCol == END_TIME_COL
                            || modelCol == STATUS_COL_MODEL;
                }
                // duration & trgovacki predstavnik su neuredivi
                if (modelCol == DURATION_COL || modelCol == TP_COL) {
                    return false;
                }
                return true;
            }
            @Override
            public Class<?> getColumnClass(int col) {
                if (col == 4 || col == 8 || col == 9 || col == 10 || col == 11) return Double.class;
                if (col == 5) return Integer.class;
                return String.class;
            }
        };

        table = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        int[] widths = {120,120,180,180,100,60,110,140,60,80,90,100,140,140,140,140};
        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Apply enhanced visual style & status renderer
        applyBrutalTableStyle();
        setUpStatusRenderer();

        // TABOVI
        JTabbedPane tabs = new JTabbedPane();
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        tabs.addTab("Podaci", mainPanel);
        tabs.addTab("Statistika", new StatistikaPanel(tableModel, 10.0));
        frame.add(tabs, BorderLayout.CENTER);

        // Učitavanje podataka i postavki
        komitentTPMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
        UserDatabaseHelper.loadUserTableSettings(prijavljeniKorisnik, table);
        DatabaseHelper.loadFromDatabase(tableModel);

        // GORNJI PANEL (Pretraga + Odjava)
        JPanel topPanel = new JPanel(new BorderLayout());
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchField = new JTextField(20);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
            private void filter() {
                String text = searchField.getText();
                if (text.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
                }
            }
        });
        searchPanel.add(new JLabel("Pretraga:"));
        searchPanel.add(searchField);

        JPanel logoutPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnOdjava = new JButton("Odjava");
        btnOdjava.addActionListener(e -> {
            ActionLogger.log(prijavljeniKorisnik, "Kliknuo ODJAVA");
            UserDatabaseHelper.saveUserTableSettings(prijavljeniKorisnik, table);
            frame.dispose();
            new LoginUI();
        });
        logoutPanel.add(btnOdjava);
        topPanel.add(searchPanel, BorderLayout.WEST);
        topPanel.add(logoutPanel, BorderLayout.EAST);
        frame.add(topPanel, BorderLayout.NORTH);

        // DROPDOWNOVI I RENDERERI
        setUpStatusDropdown();
        setUpDjelatnikDropdown();
        setUpDateColumns();
        setUpKomitentDropdown();
        setUpPredstavnikDropdown();

        // Opcionalno: omogućiti napredni popup za komitenta (koristi ga ako želiš)
        enableKomitentSearchPopup();

        // LISTENERI I ADMIN POPUP
        setUpListeners();
        setUpAdminUnlockPopup();

        // DOUBLE-CLICK EDITORS AND HANDLERS
        enforceDoubleClickEditors();
        enableDoubleClickOpeners();

        // SAKRIVANJE KOLONA
        hideColumns(8, 9, 10);

        // DONJI PANEL S GUMBIMA
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JButton btnImport  = new JButton("Uvezi iz Excela");
        btnImport.addActionListener(e -> {
            ExcelImporter.importFromExcel(tableModel);
            ActionLogger.log(prijavljeniKorisnik, "Kliknuo UVEZI iz Excela");
        });
        JButton btnExport  = new JButton("Izvezi u Excel");
        btnExport.addActionListener(e -> {
            ExcelExporter.exportTableToExcel(tableModel);
            ActionLogger.log(prijavljeniKorisnik, "Kliknuo IZVEZI iz Excel");
        });
        JButton btnSaveDb  = new JButton("Spremi u bazu");
        btnSaveDb.addActionListener(e -> {
            DatabaseHelper.saveToDatabase(tableModel);
            UserDatabaseHelper.saveUserTableSettings(prijavljeniKorisnik, table);
            ActionLogger.log(prijavljeniKorisnik, "Spremio u bazu");
        });
        JButton btnLoadDb  = new JButton("Učitaj iz baze");
        btnLoadDb.addActionListener(e -> {
            DatabaseHelper.loadFromDatabase(tableModel);
            ActionLogger.log(prijavljeniKorisnik, "Učitao iz baze");
        });

        JButton btnRefresh = new JButton("Osvježi izračune");
        btnRefresh.addActionListener(e -> {
            recomputeAllRows();
            ActionLogger.log(prijavljeniKorisnik, "Osvježio izračune");
        });

        JButton btnAddItem = new JButton("Dodaj artikal");
        btnAddItem.addActionListener(e -> {
            Object[] emptyRow = new Object[]{
                    "", "", "", "", null, null, "", "", null, null, null, null, null, null, ""
            };
            tableModel.addRow(emptyRow);
            int newRowIndex = tableModel.getRowCount() - 1;
            table.changeSelection(newRowIndex, 0, false, false);
        });
        bottom.add(btnAddItem);

        JButton btnDelete  = new JButton("Obriši artikal");
        btnDelete.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow < 0) {
                JOptionPane.showMessageDialog(frame, "Niste označili red za brisanje.",
                        "Upozorenje", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(
                    frame, "Želite li obrisati označeni red?", "Potvrda brisanja", JOptionPane.YES_NO_OPTION
            );
            if (confirm == JOptionPane.YES_OPTION) {
                int modelRow = table.convertRowIndexToModel(selectedRow);
                String datumNarudzbe = (String) tableModel.getValueAt(modelRow, 0);
                String nazivRobe     = (String) tableModel.getValueAt(modelRow, 3);

                DatabaseHelper.deleteRow(datumNarudzbe, nazivRobe);
                ActionLogger.logTableAction(prijavljeniKorisnik, "Obrisao artikal", tableModel, modelRow);
                tableModel.removeRow(modelRow);
            }
        });

        if ("Administrator".equals(ulogaKorisnika)) {
            JButton btnAddUser = new JButton("Dodaj korisnika");
            btnAddUser.addActionListener(e -> {
                new AddUserUI(null);
                ActionLogger.log(prijavljeniKorisnik, "Otvorio dodavanje korisnika");
            });
            bottom.add(btnAddUser);
        }

        bottom.add(btnImport);
        bottom.add(btnExport);
        bottom.add(btnSaveDb);
        bottom.add(btnLoadDb);
        bottom.add(btnRefresh);
        bottom.add(btnDelete);

        applyBrutalButtonStyle(bottom);

        frame.add(bottom, BorderLayout.SOUTH);

        // ZAVRŠNI DIO
        frame.setVisible(true);
        DatabaseHelper.initializeDatabase();
        ActionLogger.log(prijavljeniKorisnik, "Otvorio glavni prozor kao " + ulogaKorisnika);
    }

    // --- Helpers / Listeners / Business logic ---

    private void resetInactivityTimer() {
        if (inactivityTimer != null) inactivityTimer.restart();
    }

    private void logPromjenaStatusa(JTable table, int kolonaNazivRobe,
                                    String prijavljeniKorisnik, String noviStatus, String startTime) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) return;
        int modelRow = table.convertRowIndexToModel(selectedRow);
        ActionLogger.logTableAction(prijavljeniKorisnik,
                "Promjena statusa na '" + noviStatus + "'", tableModel, modelRow);
    }

    private void recomputeAllRows() {
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            recomputeRow(r);
            recomputeDuration(r);
        }
        tableModel.fireTableDataChanged();
    }

    /**
     * Računa trajanje između start i end vremena za jedan red.
     * Preferira WorkingTimeCalculator.calculateWorkingMinutes(LocalDateTime, LocalDateTime).
     * Ako parsing ne uspije, fallback na tekstualni calculateWorkingDuration.
     */
    private void recomputeDuration(int row) {
        int idxStart = tableModel.findColumn("startTime");
        int idxEnd   = tableModel.findColumn("endTime");
        int idxDur   = tableModel.findColumn("duration");

        if (idxStart == -1) idxStart = START_TIME_COL;
        if (idxEnd == -1) idxEnd = END_TIME_COL;
        if (idxDur == -1) idxDur = DURATION_COL;

        System.out.println("recomputeDuration: row=" + row + " idxStart=" + idxStart + " idxEnd=" + idxEnd + " idxDur=" + idxDur);

        Object startObj = tableModel.getValueAt(row, idxStart);
        Object endObj   = tableModel.getValueAt(row, idxEnd);

        String start = startObj == null ? "" : startObj.toString().trim();
        String end   = endObj   == null ? "" : endObj.toString().trim();

        System.out.println("recomputeDuration: start='" + start + "' end='" + end + "'");

        if (start.isBlank() || end.isBlank()) {
            tableModel.setValueAt("", row, idxDur);
            System.out.println("recomputeDuration: prazni start ili end -> duration resetiran.");
            return;
        }

        String outValue = "";
        LocalDateTime startDT = tryParseLocalDateTime(start);
        LocalDateTime endDT = tryParseLocalDateTime(end);

        try {
            if (startDT != null && endDT != null) {
                if (endDT.isBefore(startDT)) {
                    System.err.println("recomputeDuration: end prije starta (red " + row + "). start=" + startDT + " end=" + endDT + " -> duration resetiran.");
                    tableModel.setValueAt("", row, idxDur);
                    return;
                }
                long minutes = WorkingTimeCalculator.calculateWorkingMinutes(startDT, endDT);
                System.out.println("recomputeDuration: working minutes = " + minutes);
                if (minutes > 0) {
                    long hh = minutes / 60;
                    long mm = minutes % 60;
                    outValue = String.format("%02d:%02d", hh, mm);
                } else {
                    outValue = "";
                }
            } else {
                System.out.println("recomputeDuration: parsiranje nije uspjelo za oba datuma, fallback na originalne stringove.");
                String workingCalc = WorkingTimeCalculator.calculateWorkingDuration(start, end);
                System.out.println("recomputeDuration: WorkingTimeCalculator raw output: \"" + workingCalc + "\"");
                String hhmm = convertWorkingToHHMM_orFallback(workingCalc);
                System.out.println("recomputeDuration: parsed outValue=\"" + hhmm + "\"");
                outValue = hhmm;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            outValue = "";
        }

        tableModel.setValueAt(outValue, row, idxDur);
    }

    /**
     * Robustni parser za razne formate datuma/vremena u LocalDateTime.
     */
    private LocalDateTime tryParseLocalDateTime(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        String str = s.trim();
        if (str.endsWith(".")) str = str.substring(0, str.length() - 1).trim();

        DateTimeFormatter[] fmts = new DateTimeFormatter[] {
                DateTimeFormatter.ofPattern("d.M.yyyy H:mm", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d.M.yyyy HH:mm", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd.MM.yyyy H:mm", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd.MM.yyyy.HH:mm", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd/MM/yyyy H:mm", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d/M/yyyy H:mm", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d/M/yyyy HH:mm", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
        };

        for (DateTimeFormatter fmt : fmts) {
            try {
                try {
                    return LocalDateTime.parse(str, fmt);
                } catch (DateTimeParseException pe) {
                    try {
                        java.time.LocalDate d = java.time.LocalDate.parse(str, fmt);
                        return d.atTime(0,0);
                    } catch (DateTimeParseException ignored) {
                        // continue
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Parsiranje tekstualnog outputa od WorkingTimeCalculator u HH:mm, s fallbackom na raw string.
     */
    private String convertWorkingToHHMM_orFallback(String working) {
        if (working == null) return "";
        String s = working.trim();
        if (s.isEmpty()) return "";

        Pattern p = Pattern.compile("(\\d+)");
        Matcher m = p.matcher(s);
        ArrayList<Integer> nums = new ArrayList<>();

        while (m.find()) {
            try { nums.add(Integer.parseInt(m.group(1))); } catch (NumberFormatException ignored) {}
        }

        System.out.println("convertWorkingToHHMM_orFallback: extracted numbers=" + nums);

        int hours = 0;
        int minutes = 0;

        if (nums.size() == 1) {
            String low = s.toLowerCase();
            if (low.contains("sat") || low.contains("sati") || low.contains("hour") || low.contains("h")) {
                hours = nums.get(0);
            } else {
                minutes = nums.get(0);
            }
        } else if (nums.size() >= 2) {
            hours = nums.get(0);
            minutes = nums.get(1);
        } else {
            return s; // return raw for debugging
        }

        int totalMinutes = Math.max(0, hours * 60 + minutes);
        if (totalMinutes == 0) return s;
        int hh = totalMinutes / 60;
        int mm = totalMinutes % 60;
        return String.format("%02d:%02d", hh, mm);
    }

    /**
     * Parsira format "sat X minuta Y" -> "HH:mm" (povrat prazan string ako 0).
     */
    private String convertWorkingToHHMM(String working) {
        if (working == null) return "";
        String s = working.trim();
        if (s.isEmpty()) return "";

        Pattern p = Pattern.compile("(\\d+)");
        Matcher m = p.matcher(s);
        ArrayList<Integer> nums = new ArrayList<>();

        while (m.find()) {
            try { nums.add(Integer.parseInt(m.group(1))); } catch (NumberFormatException ignored) {}
        }

        int hours = 0;
        int minutes = 0;

        if (nums.size() == 1) {
            if (s.toLowerCase().contains("sat") || s.toLowerCase().contains("sati")) {
                hours = nums.get(0);
            } else {
                minutes = nums.get(0);
            }
        } else if (nums.size() >= 2) {
            hours = nums.get(0);
            minutes = nums.get(1);
        } else {
            return "";
        }

        int totalMinutes = Math.max(0, hours * 60 + minutes);
        if (totalMinutes == 0) return "";
        int hh = totalMinutes / 60;
        int mm = totalMinutes % 60;
        return String.format("%02d:%02d", hh, mm);
    }

    /**
     * Listener setup za automatske izračune i ažuriranja.
     */
    private void setUpListeners() {
        tableModel.addTableModelListener(e -> {
            if (e.getType() != TableModelEvent.UPDATE) return;
            int row = e.getFirstRow();
            int col = e.getColumn();

            if (col == 0) { // komitent promjena
                Object komitentVal = tableModel.getValueAt(row, 0);
                if (komitentVal != null) {
                    String komitent = komitentVal.toString();
                    String predstavnik = komitentTPMap.getOrDefault(komitent, "");
                    tableModel.setValueAt(predstavnik, row, TP_COL);
                }
            }

            if (col == 3 || col == 5) {
                recomputeRow(row);
                tableModel.fireTableRowsUpdated(row, row);
            }
            if (col == START_TIME_COL || col == END_TIME_COL) {
                recomputeDuration(row);
                tableModel.fireTableCellUpdated(row, DURATION_COL);
            }
        });
    }

    /**
     * Parsira vrijednosti mm/m iz naziva robe.
     */
    private double[] parseMmM(String naz) {
        if (naz == null) return new double[]{0, 0};
        Pattern p = Pattern.compile("(\\d+(?:[\\.,]\\d+)?)\\s*/\\s*(\\d+(?:[\\.,]\\d+)?)");
        Matcher m = p.matcher(naz);
        if (m.find()) {
            try {
                double mm = Double.parseDouble(m.group(1).replace(',', '.'));
                double mVal = Double.parseDouble(m.group(2).replace(',', '.'));
                return new double[]{mm, mVal};
            } catch (Exception ex) {
                // ignore
            }
        }
        return new double[]{0, 0};
    }

    /**
     * Ponovno računa mm, m, tisucl, m2 za red.
     */
    private void recomputeRow(int r) {
        double[] mmM = parseMmM((String) tableModel.getValueAt(r, 3));
        double mm   = mmM[0];
        double mVal = mmM[1];
        tableModel.setValueAt(mm == 0 ? null : mm, r, 8);
        tableModel.setValueAt(mVal == 0 ? null : mVal, r, 9);
        Double tisucl = (mm == 0 || mVal == 0) ? null : (mm / 1000) * mVal;
        tableModel.setValueAt(tisucl, r, 10);
        double kom = tableModel.getValueAt(r, 5) instanceof Number
                ? ((Number) tableModel.getValueAt(r, 5)).doubleValue()
                : 0;
        Double m2 = (tisucl == null || kom == 0) ? null : tisucl * kom;
        tableModel.setValueAt(m2, r, 11);
    }

    /**
     * Sakriva kolone prema model indeksima.
     */
    private void hideColumns(int... modelIndexes) {
        for (int mi : modelIndexes) {
            int vi = table.convertColumnIndexToView(mi);
            if (vi >= 0 && vi < table.getColumnModel().getColumnCount()) {
                TableColumn col = table.getColumnModel().getColumn(vi);
                col.setMinWidth(0);
                col.setMaxWidth(0);
                col.setPreferredWidth(0);
                col.setResizable(false);
            }
        }
    }

    /**
     * Postavlja dropdown editor za kolonu komitent (kolona 2).
     */
    private void setUpKomitentDropdown() {
        int viewIdx = table.convertColumnIndexToView(KOMITENT_OPIS_COL);
        if (viewIdx < 0) return;
        java.util.List<String> all = KomitentiDatabaseHelper.loadAllKomitentNames();
        JComboBox<String> combo = new JComboBox<>(all.toArray(new String[0]));
        combo.setEditable(true);
        combo.addActionListener(e -> {
            Object selObj = combo.getSelectedItem();
            if (selObj != null) {
                String noviKomitent = selObj.toString().trim();
                int row = table.getSelectedRow();
                if (row >= 0 && !noviKomitent.isEmpty()) {
                    int modelRow = table.convertRowIndexToModel(row);
                    String trenutniTP = (String) tableModel.getValueAt(modelRow, TP_COL);
                    if (trenutniTP == null || trenutniTP.isBlank()) {
                        String unesenTP = JOptionPane.showInputDialog(frame,
                                "Unesi trgovačkog predstavnika za: " + noviKomitent, "");
                        if (unesenTP == null) unesenTP = "";
                        trenutniTP = unesenTP.trim();
                    }
                    tableModel.setValueAt(noviKomitent, modelRow, KOMITENT_OPIS_COL);
                    tableModel.setValueAt(trenutniTP, modelRow, TP_COL);
                    KomitentiDatabaseHelper.insertIfNotExists(noviKomitent, trenutniTP);
                    komitentTPMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
                    // refresh combo items
                    combo.removeAllItems();
                    for (String k : KomitentiDatabaseHelper.loadAllKomitentNames()) combo.addItem(k);
                    setUpPredstavnikDropdown();
                }
            }
        });

        table.getColumnModel().getColumn(viewIdx).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setText(value == null ? "" : value.toString());
                return this;
            }
        });

        table.getColumnModel().getColumn(viewIdx).setCellEditor(new DefaultCellEditor(combo));
    }

    /**
     * Postavlja dropdown editor za kolonu Trg. predstavnik (kolona 15).
     */
    private void setUpPredstavnikDropdown() {
        int viewIdx = table.convertColumnIndexToView(TP_COL);
        if (viewIdx < 0) return;

        JComboBox<String> combo = new JComboBox<>();
        combo.setEditable(true);

        for (String p : KomitentiDatabaseHelper.loadAllPredstavnici()) combo.addItem(p);

        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                combo.removeAllItems();
                for (String p : KomitentiDatabaseHelper.loadAllPredstavnici()) combo.addItem(p);
            }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(PopupMenuEvent e) {}
        });

        combo.addActionListener(e -> {
            Object selObj = combo.getSelectedItem();
            if (selObj != null) {
                String noviPredstavnik = selObj.toString().trim();
                int row = table.getEditingRow();
                if (row >= 0 && !noviPredstavnik.isEmpty()) {
                    int modelRow = table.convertRowIndexToModel(row);
                    String trenutniKomitent = (String) tableModel.getValueAt(modelRow, KOMITENT_OPIS_COL);
                    if (trenutniKomitent == null || trenutniKomitent.isBlank()) {
                        String unesenKomitent = JOptionPane.showInputDialog(frame,
                                "Unesi komitenta za predstavnika: " + noviPredstavnik, "");
                        if (unesenKomitent == null) unesenKomitent = "";
                        trenutniKomitent = unesenKomitent.trim();
                    }
                    tableModel.setValueAt(trenutniKomitent, modelRow, KOMITENT_OPIS_COL);
                    tableModel.setValueAt(noviPredstavnik, modelRow, TP_COL);
                    KomitentiDatabaseHelper.insertIfNotExists(trenutniKomitent, noviPredstavnik);
                    komitentTPMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
                }
            }
        });

        table.getColumnModel().getColumn(viewIdx).setCellEditor(new DefaultCellEditor(combo));
    }

    /**
     * Postavlja dropdown izbornik za kolonu "status".
     * Omogućuje automatsko postavljanje vremena i korisnika.
     */
    private void setUpStatusDropdown() {
        int viewIdx = table.convertColumnIndexToView(STATUS_COL_MODEL);
        if (viewIdx < 0) return;

        TableColumn col = table.getColumnModel().getColumn(viewIdx);
        String[] opcije = {"", "U izradi", "Izrađeno"};
        JComboBox<String> combo = new JComboBox<>(opcije);

        DefaultCellEditor editor = new DefaultCellEditor(combo) {
            private Object originalValue;
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value,
                                                         boolean isSelected, int row, int column) {
                originalValue = value;
                return super.getTableCellEditorComponent(table, value, isSelected, row, column);
            }
            @Override
            public Object getCellEditorValue() {
                String sel = (String) super.getCellEditorValue();
                int editingRowView = table.getEditingRow();
                int modelRow = editingRowView >= 0 ? table.convertRowIndexToModel(editingRowView) : -1;
                if (modelRow < 0) return sel;

                if ("U izradi".equals(sel)) {
                    tableModel.setValueAt("U izradi", modelRow, STATUS_COL_MODEL);
                    tableModel.setValueAt(DateUtils.formatWithoutSeconds(LocalDateTime.now()), modelRow, START_TIME_COL);
                    tableModel.setValueAt(prijavljeniKorisnik, modelRow, 7);
                    ActionLogger.logTableAction(prijavljeniKorisnik, "Status 'U izradi'", tableModel, modelRow);
                    return sel;

                } else if ("Izrađeno".equals(sel)) {
                    int ans = JOptionPane.showConfirmDialog(
                            frame,
                            "Potvrdi 'Izrađeno'?",
                            "Potvrda",
                            JOptionPane.YES_NO_CANCEL_OPTION
                    );
                    if (ans == JOptionPane.YES_OPTION) {
                        tableModel.setValueAt("Izrađeno", modelRow, STATUS_COL_MODEL);
                        Object existingEnd = tableModel.getValueAt(modelRow, END_TIME_COL);
                        if (existingEnd == null || existingEnd.toString().isBlank()) {
                            tableModel.setValueAt(DateUtils.formatWithoutSeconds(LocalDateTime.now()), modelRow, END_TIME_COL);
                        }
                        Object existingStart = tableModel.getValueAt(modelRow, START_TIME_COL);
                        if (existingStart == null || existingStart.toString().isBlank()) {
                            tableModel.setValueAt(DateUtils.formatWithoutSeconds(LocalDateTime.now()), modelRow, START_TIME_COL);
                        }
                        recomputeDuration(modelRow);
                        ActionLogger.logTableAction(prijavljeniKorisnik, "Status 'Izrađeno'", tableModel, modelRow);
                        return sel;
                    } else {
                        return originalValue == null ? "" : originalValue;
                    }

                } else {
                    tableModel.setValueAt("", modelRow, STATUS_COL_MODEL);
                    return "";
                }
            }
        };

        col.setCellEditor(editor);
    }

    /**
     * Postavlja dropdown za kolonu djelatnik (kolona 7).
     */
    private void setUpDjelatnikDropdown() {
        int viewIdx = table.convertColumnIndexToView(7);
        if (viewIdx < 0) return;
        TableColumn col = table.getColumnModel().getColumn(viewIdx);
        JComboBox<String> combo = new JComboBox<>(djelatnici);
        col.setCellEditor(new DefaultCellEditor(combo));
    }

    /**
     * Postavlja editore i renderere za datumske kolone.
     */
    private void setUpDateColumns() {
        int[] dateOnlyCols = {0, 1};
        int[] dateTimeCols = {12, 13};

        for (int c : dateOnlyCols) {
            int v = table.convertColumnIndexToView(c);
            if (v < 0) continue;
            TableColumn col = table.getColumnModel().getColumn(v);
            col.setCellEditor(new DateOnlyCellEditor());
            col.setCellRenderer(new DateOnlyCellRenderer());
        }

        for (int c : dateTimeCols) {
            int v = table.convertColumnIndexToView(c);
            if (v < 0) continue;
            TableColumn col = table.getColumnModel().getColumn(v);
            col.setCellEditor(new DateTimeCellEditor());
            col.setCellRenderer(new DateTimeCellRenderer());
        }
    }

    /**
     * Omogući napredni popup za pretragu komitenata (koristan kad ima puno komitenata).
     */
    private void enableKomitentSearchPopup() {
        int komitentColView = table.convertColumnIndexToView(KOMITENT_OPIS_COL);
        if (komitentColView < 0) return;

        table.getColumnModel().getColumn(komitentColView).setCellEditor(null);

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedColumn() == komitentColView) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow < 0) return;
                    int modelRow = table.convertRowIndexToModel(viewRow);

                    JDialog dialog = new JDialog(frame, "Odaberi komitenta", true);
                    dialog.setSize(400, 300);
                    dialog.setLocationRelativeTo(frame);
                    dialog.setLayout(new BorderLayout(5, 5));

                    JTextField search = new JTextField();
                    DefaultListModel<String> listModel = new DefaultListModel<>();
                    JList<String> list = new JList<>(listModel);
                    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

                    java.util.List<String> komitenti = KomitentiDatabaseHelper.loadAllKomitentNames();
                    komitenti.forEach(listModel::addElement);

                    search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                        public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                        public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                        public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                        private void filter() {
                            String txt = search.getText().toLowerCase();
                            listModel.clear();
                            komitenti.stream()
                                    .filter(k -> k.toLowerCase().contains(txt))
                                    .forEach(listModel::addElement);
                        }
                    });

                    Runnable selectAction = () -> {
                        String val = list.getSelectedValue();
                        if (val != null) {
                            tableModel.setValueAt(val, modelRow, KOMITENT_OPIS_COL);
                            String tp = KomitentiDatabaseHelper.loadKomitentPredstavnikMap().getOrDefault(val, "");
                            if (tp.isBlank()) {
                                String unesenTP = JOptionPane.showInputDialog(frame,
                                        "Unesi trgovačkog predstavnika za: " + val, "");
                                if (unesenTP == null) unesenTP = "";
                                tp = unesenTP.trim();
                                KomitentiDatabaseHelper.insertIfNotExists(val, tp);
                            }
                            tableModel.setValueAt(tp, modelRow, TP_COL);
                            komitentTPMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
                            dialog.dispose();
                        }
                    };

                    list.addMouseListener(new MouseAdapter() {
                        @Override public void mouseClicked(MouseEvent e) {
                            if (e.getClickCount() == 2) selectAction.run();
                        }
                    });

                    JButton btnSelect = new JButton("Odaberi");
                    btnSelect.addActionListener(ev -> selectAction.run());

                    dialog.add(search, BorderLayout.NORTH);
                    dialog.add(new JScrollPane(list), BorderLayout.CENTER);
                    dialog.add(btnSelect, BorderLayout.SOUTH);

                    dialog.setVisible(true);
                }
            }
        });
    }

    /**
     * Postavlja popup meni za administratore (otključavanje i pregled povijesti).
     */
    private void setUpAdminUnlockPopup() {
        if (!"Administrator".equalsIgnoreCase(ulogaKorisnika)) return;

        JPopupMenu adminPopup = new JPopupMenu();

        JMenuItem unlockItem = new JMenuItem("Otključaj ćeliju");
        unlockItem.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0 || viewRow >= table.getRowCount()) return;
            int modelRow = table.convertRowIndexToModel(viewRow);
            TableModel tm = table.getModel();
            Object statusVal = tm.getValueAt(modelRow, STATUS_COL_MODEL);
            String status = statusVal == null ? "" : statusVal.toString();
            if (!"Izrađeno".equals(status)) return;

            int ans = JOptionPane.showConfirmDialog(
                    frame,
                    "Želite li otključati ćeliju?",
                    "Otključavanje ćelije",
                    JOptionPane.YES_NO_OPTION
            );
            if (ans != JOptionPane.YES_OPTION) return;

            String komentar = JOptionPane.showInputDialog(
                    frame,
                    "Unesite kratki komentar (opcionalno):",
                    ""
            );
            if (komentar == null) komentar = "";

            if (!odmrznutiModelRedovi.contains(modelRow)) odmrznutiModelRedovi.add(modelRow);

            tm.setValueAt("", modelRow, STATUS_COL_MODEL);
            if (tm instanceof AbstractTableModel atm) {
                atm.fireTableRowsUpdated(modelRow, modelRow);
            } else {
                table.repaint();
            }

            String zapis = String.format(
                    "%s | %s | %s",
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()),
                    prijavljeniKorisnik,
                    komentar.isBlank() ? "(bez komentara)" : komentar
            );
            povijestPromjena.computeIfAbsent(modelRow, k -> new ArrayList<>()).add(zapis);
            table.clearSelection();
            KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
        });
        adminPopup.add(unlockItem);

        JMenuItem historyItem = new JMenuItem("Prikaži povijest promjena");
        historyItem.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) return;
            int modelRow = table.convertRowIndexToModel(viewRow);
            java.util.List<String> lista = povijestPromjena.get(modelRow);
            if (lista == null || lista.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Nema zabilježenih promjena.");
                return;
            }
            JTextArea ta = new JTextArea(String.join("\n", lista));
            ta.setEditable(false);
            ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JScrollPane sp = new JScrollPane(ta);
            sp.setPreferredSize(new Dimension(500, 300));
            JOptionPane.showMessageDialog(frame, sp, "Povijest promjena", JOptionPane.INFORMATION_MESSAGE);
        });
        adminPopup.add(historyItem);

        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { showPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { showPopup(e); }
            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = table.rowAtPoint(e.getPoint());
                    int col = table.columnAtPoint(e.getPoint());
                    if (row >= 0 && col == table.convertColumnIndexToView(STATUS_COL_MODEL)) {
                        table.setRowSelectionInterval(row, row);
                        adminPopup.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });
    }

    /**
     * Enforce editors to start on double-click.
     */
    private void enforceDoubleClickEditors() {
        // Set double-click for default table editors
        if (table.getDefaultEditor(String.class) instanceof DefaultCellEditor) {
            ((DefaultCellEditor) table.getDefaultEditor(String.class)).setClickCountToStart(2);
        }
        if (table.getDefaultEditor(Integer.class) instanceof DefaultCellEditor) {
            ((DefaultCellEditor) table.getDefaultEditor(Integer.class)).setClickCountToStart(2);
        }
        if (table.getDefaultEditor(Double.class) instanceof DefaultCellEditor) {
            ((DefaultCellEditor) table.getDefaultEditor(Double.class)).setClickCountToStart(2);
        }
        if (table.getDefaultEditor(Boolean.class) instanceof DefaultCellEditor) {
            ((DefaultCellEditor) table.getDefaultEditor(Boolean.class)).setClickCountToStart(2);
        }

        // Set double-click for specific column editors (djelatnik/startTime/endTime)
        int djelatnikViewIdx = table.convertColumnIndexToView(7);
        if (djelatnikViewIdx >= 0) {
            TableCellEditor editor = table.getColumnModel().getColumn(djelatnikViewIdx).getCellEditor();
            if (editor instanceof DefaultCellEditor) ((DefaultCellEditor) editor).setClickCountToStart(2);
        }
        int startTimeViewIdx = table.convertColumnIndexToView(START_TIME_COL);
        if (startTimeViewIdx >= 0) {
            TableCellEditor editor = table.getColumnModel().getColumn(startTimeViewIdx).getCellEditor();
            if (editor instanceof DefaultCellEditor) ((DefaultCellEditor) editor).setClickCountToStart(2);
        }
        int endTimeViewIdx = table.convertColumnIndexToView(END_TIME_COL);
        if (endTimeViewIdx >= 0) {
            TableCellEditor editor = table.getColumnModel().getColumn(endTimeViewIdx).getCellEditor();
            if (editor instanceof DefaultCellEditor) ((DefaultCellEditor) editor).setClickCountToStart(2);
        }
    }

    /**
     * Enables centralized double-click handling for opening popups and dialogs.
     */
    private void enableDoubleClickOpeners() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = table.rowAtPoint(e.getPoint());
                    int viewCol = table.columnAtPoint(e.getPoint());
                    if (viewRow < 0 || viewCol < 0) return;
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    int modelCol = table.convertColumnIndexToModel(viewCol);

                    System.out.println("Double-click at modelRow=" + modelRow + ", modelCol=" + modelCol);

                    if (modelCol == KOMITENT_OPIS_COL) {
                        openKomitentDialog(modelRow);
                    } else {
                        if (tableModel.isCellEditable(modelRow, modelCol)) {
                            if (table.editCellAt(viewRow, viewCol)) {
                                Component editor = table.getEditorComponent();
                                if (editor != null) {
                                    editor.requestFocus();
                                    if (editor instanceof JComboBox) {
                                        ((JComboBox<?>) editor).showPopup();
                                    }
                                }
                                System.out.println("Started editing cell at row=" + viewRow + ", col=" + viewCol);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Opens komitent selection dialog for the specified model row.
     */
    private void openKomitentDialog(int modelRow) {
        JDialog dialog = new JDialog(frame, "Odaberi komitenta", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(frame);
        dialog.setLayout(new BorderLayout(5, 5));

        JTextField sf = new JTextField();
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        java.util.List<String> komitenti = KomitentiDatabaseHelper.loadAllKomitentNames();
        komitenti.forEach(listModel::addElement);

        sf.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            private void filter() {
                String txt = sf.getText().toLowerCase();
                listModel.clear();
                komitenti.stream()
                        .filter(k -> k.toLowerCase().contains(txt))
                        .forEach(listModel::addElement);
            }
        });

        Runnable selectAction = () -> {
            String val = list.getSelectedValue();
            if (val != null) {
                tableModel.setValueAt(val, modelRow, KOMITENT_OPIS_COL);
                String tp = KomitentiDatabaseHelper.loadKomitentPredstavnikMap().getOrDefault(val, "");
                if (tp.isBlank()) {
                    String unesenTP = JOptionPane.showInputDialog(frame,
                        "Unesi trgovačkog predstavnika za: " + val, "");
                    if (unesenTP == null) unesenTP = "";
                    tp = unesenTP.trim();
                    KomitentiDatabaseHelper.insertIfNotExists(val, tp);
                }
                tableModel.setValueAt(tp, modelRow, TP_COL);
                komitentTPMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
                dialog.dispose();
                System.out.println("Selected komitent: " + val + ", tp: " + tp);
            }
        };

        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) selectAction.run();
            }
        });

        JButton btnSelect = new JButton("Odaberi");
        btnSelect.addActionListener(ev -> selectAction.run());

        dialog.add(sf, BorderLayout.NORTH);
        dialog.add(new JScrollPane(list), BorderLayout.CENTER);
        dialog.add(btnSelect, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    /* ---------------- Custom editors / renderers ---------------- */

    // Date+Time editor/renderer (dd/MM/yyyy HH:mm)
    private static class DateTimeCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JDateChooser dateChooser;
        private final JSpinner timeSpinner;
        private final JPanel panel;
        private static final DateTimeFormatter OUT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        DateTimeCellEditor() {
            dateChooser = new JDateChooser();
            dateChooser.setDateFormatString("dd/MM/yyyy");
            SpinnerDateModel model = new SpinnerDateModel();
            timeSpinner = new JSpinner(model);
            timeSpinner.setEditor(new JSpinner.DateEditor(timeSpinner, "HH:mm"));
            panel = new JPanel(new BorderLayout(4, 0));
            panel.add(dateChooser, BorderLayout.CENTER);
            panel.add(timeSpinner, BorderLayout.EAST);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            LocalDateTime dt = null;
            if (value instanceof String s && !s.isBlank()) {
                try {
                    dt = LocalDateTime.parse(s, OUT_FMT);
                } catch (Exception ex) {
                    try { dt = DateUtils.parse(s); } catch (Exception ignored) {}
                }
            }
            if (dt == null) dt = LocalDateTime.now();
            Date date = Date.from(dt.atZone(ZoneId.systemDefault()).toInstant());
            dateChooser.setDate(date);
            timeSpinner.setValue(date);
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            Date d = dateChooser.getDate();
            Date t = (Date) timeSpinner.getValue();
            if (d == null || t == null) return "";
            LocalDate datePart = d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalTime timePart = t.toInstant().atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0);
            LocalDateTime dt = LocalDateTime.of(datePart, LocalTime.of(timePart.getHour(), timePart.getMinute()));
            return dt.format(OUT_FMT); // dd/MM/yyyy HH:mm
        }
    }

    private static class DateTimeCellRenderer extends DefaultTableCellRenderer {
        private static final DateTimeFormatter OUT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value, boolean sel, boolean foc, int row, int column) {
            String text = "";
            if (value instanceof String s && !s.isBlank()) {
                try {
                    LocalDateTime dt = LocalDateTime.parse(s, OUT_FMT);
                    text = dt.format(OUT_FMT);
                } catch (Exception ex) {
                    try {
                        LocalDateTime dt = DateUtils.parse(s);
                        if (dt != null) text = dt.format(OUT_FMT);
                        else text = s;
                    } catch (Exception ex2) {
                        text = s;
                    }
                }
            }
            Component comp = super.getTableCellRendererComponent(tbl, text, sel, foc, row, column);
            comp.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            return comp;
        }
    }

    /**
     * Custom renderer za prikaz datuma+vremena (koristi DateUtils.formatWithoutSeconds ako moguće).
     */
    private static class CalendarTimeCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value, boolean sel, boolean foc, int row, int column) {
            String text = "";
            if (value instanceof String && !((String) value).isEmpty()) {
                LocalDateTime dt = DateUtils.parse((String) value);
                text = (dt != null) ? DateUtils.formatWithoutSeconds(dt) : (String) value;
            }
            Component comp = super.getTableCellRendererComponent(tbl, text, sel, foc, row, column);
            comp.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            return comp;
        }
    }

    // Date-only editor/renderer (dd/MM/yyyy)
    private static class DateOnlyCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JDateChooser dateChooser;
        private final JPanel panel;
        private static final DateTimeFormatter OUT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        DateOnlyCellEditor() {
            dateChooser = new JDateChooser();
            dateChooser.setDateFormatString("dd/MM/yyyy");
            panel = new JPanel(new BorderLayout(4, 0));
            panel.add(dateChooser, BorderLayout.CENTER);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            LocalDate date = null;
            if (value instanceof String s && !s.isBlank()) {
                try { date = LocalDate.parse(s, OUT_FMT); } catch (Exception ex) {
                    try {
                        LocalDateTime dt = DateUtils.parse(s);
                        if (dt != null) date = dt.toLocalDate();
                    } catch (Exception ignored) {}
                }
            }
            if (date == null) date = LocalDate.now();
            Date dd = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
            dateChooser.setDate(dd);
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            Date d = dateChooser.getDate();
            if (d == null) return "";
            LocalDate ld = d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            return ld.format(OUT_FMT);
        }
    }

    private static class DateOnlyCellRenderer extends DefaultTableCellRenderer {
        private static final DateTimeFormatter OUT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value, boolean sel, boolean foc, int row, int column) {
            String text = "";
            if (value instanceof String && !((String) value).isEmpty()) {
                LocalDate date = null;
                try { date = LocalDate.parse((String) value, OUT_FMT); }
                catch (Exception ex) {
                    try {
                        LocalDateTime dt = DateUtils.parse((String) value);
                        if (dt != null) date = dt.toLocalDate();
                    } catch (Exception ignored) {}
                }
                text = (date != null) ? date.format(OUT_FMT) : (String) value;
            }
            Component comp = super.getTableCellRendererComponent(tbl, text, sel, foc, row, column);
            comp.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            return comp;
        }
    }

    /**
     * Inicijalizacija podataka za otključavanje / povijest.
     */
    private void initUnlockHistoryData() {
        if (povijestPromjena == null) povijestPromjena = new HashMap<>();
        if (odmrznutiModelRedovi == null) odmrznutiModelRedovi = new HashSet<>();
    }

    /**
     * Primjenjuje vizualni stil na JTable (zebra, hover, padding, header).
     */
    private void applyBrutalTableStyle() {
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setForeground(new Color(50, 50, 50));
        table.setBackground(new Color(248, 250, 252)); // svijetlo siva s plavim tonom
        table.setGridColor(new Color(220, 225, 230));
        table.setRowHeight(30);

        // Zebra + hover + padding
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private final Color evenColor = new Color(240, 243, 245);
            private final Color oddColor = new Color(225, 230, 235);
            private final Color hoverColor = new Color(210, 225, 255);
            private int hoveredRow = -1;

            {
                table.addMouseMotionListener(new MouseMotionAdapter() {
                    @Override
                    public void mouseMoved(MouseEvent e) {
                        hoveredRow = table.rowAtPoint(e.getPoint());
                        table.repaint();
                    }
                });
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (isSelected) {
                    c.setBackground(new Color(180, 205, 255));
                } else if (row == hoveredRow) {
                    c.setBackground(hoverColor);
                } else {
                    c.setBackground(row % 2 == 0 ? evenColor : oddColor);
                }
                setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10)); // padding
                return c;
            }
        });

        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 15));
        header.setForeground(Color.WHITE);
        header.setBackground(new Color(58, 105, 180)); // elegantna plava
        header.setReorderingAllowed(true);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)));
    }

    /**
     * Postavlja renderer za status kolonu (boje ovisno o statusu).
     */
    private void setUpStatusRenderer() {
        int viewIdx = table.convertColumnIndexToView(STATUS_COL_MODEL);
        if (viewIdx < 0) return;

        table.getColumnModel().getColumn(viewIdx).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean sel, boolean foc, int row,
                                                           int column) {
                String text = value == null ? "" : value.toString();
                Component comp = super.getTableCellRendererComponent(tbl, text, sel, foc, row, column);
                switch (text) {
                    case "U izradi" -> comp.setBackground(new Color(255, 255, 200));
                    case "Izrađeno" -> comp.setBackground(new Color(200, 255, 200));
                    default -> comp.setBackground(row % 2 == 0 ? new Color(240, 243, 245) : new Color(225, 230, 235));
                }
                if (sel) comp.setBackground(new Color(180, 205, 255));
                return comp;
            }
        });
    }

    /**
     * Primjenjuje vizualni stil na gumbe unutar panela.
     */
    private void applyBrutalButtonStyle(Container c) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JButton b) {
                b.setFont(new Font("Segoe UI", Font.BOLD, 13));
                b.setBackground(new Color(40, 80, 180));
                b.setForeground(Color.WHITE);
                b.setFocusPainted(false);
                b.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
                b.setCursor(new Cursor(Cursor.HAND_CURSOR));

                b.addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { b.setBackground(new Color(60, 110, 220)); }
                    @Override public void mouseExited(MouseEvent e) { b.setBackground(new Color(40, 80, 180)); }
                });
            }
        }
    }

    /**
     * Main metoda — ulazna točka aplikacije.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UserDatabaseHelper.initializeUserTable();
            new LoginUI();
        });
    }

    // --- Kraj klase ---
}