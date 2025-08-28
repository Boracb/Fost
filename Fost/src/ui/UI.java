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
import logic.ProductionStatsCalculator;
import util.ActionLogger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

/**
 * Kompletan UI.java — spreman za copy/paste.
 * Sadrži sve setup/enable metode, debounce i suppress mehanizam,
 * DateOnly/DateTime editor/renderer-e, te computePredPlansBatch().
 *
 * Napomena: prilagodi indekse stupaca ako su u tvom projektu drugačiji.
 */
public class UI {

    // --- Polja klase ---
    private JFrame frame;
    private JTable table;
    private DefaultTableModel tableModel;
    private String prijavljeniKorisnik;
    private String ulogaKorisnika;
    private javax.swing.Timer inactivityTimer;
    private javax.swing.Timer debounceTimer;
    private final int INACTIVITY_DELAY = 60_000;
    private java.util.Map<Integer, java.util.List<String>> povijestPromjena = new java.util.HashMap<>();
    private Set<Integer> odmrznutiModelRedovi;
    private TableRowSorter<DefaultTableModel> sorter;
    private final String[] djelatnici = {"", "Marko", "Ivana", "Petra", "Boris", "Ana"};
    private Map<String, String> komitentTPMap;
    private final AtomicBoolean computingPredPlans = new AtomicBoolean(false);
    private String lastComputeSignature = "";
    private long lastComputeMillis = 0L;

    // Statistika panel (polje, da ga computePredPlansBatch može koristiti)
    private StatistikaPanel statistikaPanel;

    // suppress flag to prevent listener re-entry while programmatically updating model
    private volatile boolean suppressTableModelEvents = false;

    private static final int WORK_HOURS_PER_DAY = 8;

    private final String[] columnNames = {
            "datumNarudzbe","predDatumIsporuke","komitentOpis",
            "nazivRobe","netoVrijednost","kom","status",
            "djelatnik","mm","m","tisucl","m2","startTime","endTime","duration",
            "predPlanIsporuke","trgovackiPredstavnik"
    };

    // Konstante — indeksi temeljeni na modelu
    private static final int STATUS_COL_MODEL = 6;
    private static final int START_TIME_COL   = 12;
    private static final int END_TIME_COL     = 13;
    private static final int DURATION_COL     = 14;
    private static final int PRED_PLAN_COL    = 15; // predviđeni plan isporuke
    private static final int KOMITENT_OPIS_COL = 2;
    private static final int TP_COL            = 16; // trgovacki predstavnik

    private JTextField searchField; // polje pretrage

    // Parametri za izračun predviđene isporuke
    private static final double DEFAULT_M2_PER_HOUR = 10.0; // može se kasnije učiniti konfigurabilnim
    private static final LocalTime PLAN_WORK_START = LocalTime.of(7, 0);
    private static final LocalTime PLAN_WORK_END   = LocalTime.of(15, 0);

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
                    if (table.isEditing()) {
                        TableCellEditor ed = table.getCellEditor();
                        if (ed != null) try { ed.stopCellEditing(); } catch (Exception ignored) {}
                    }
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
                // duration, predPlanIsporuke & trgovacki predstavnik su neuredivi
                if (modelCol == DURATION_COL || modelCol == TP_COL || modelCol == PRED_PLAN_COL) {
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

        table = new DoubleClickTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        int[] widths = {120,120,180,180,100,60,110,140,60,80,90,100,140,140,140,120,140};
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

        // StatistikaPanel instance (polje)
        statistikaPanel = new StatistikaPanel(tableModel, DEFAULT_M2_PER_HOUR);

        tabs.addTab("Podaci", mainPanel);
        tabs.addTab("Statistika", statistikaPanel);
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
        // Setup debounce timer for batch compute (300ms)
        debounceTimer = new javax.swing.Timer(300, ev -> {
            ((javax.swing.Timer)ev.getSource()).stop();
            computePredPlansBatch();
        });
        debounceTimer.setRepeats(false);

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
            // Use importer with callback to recompute after import finished
            ExcelImporter.importFromExcel(tableModel, this::computePredPlansBatch);
            ActionLogger.log(prijavljeniKorisnik, "Kliknuo UVEZI iz Excela");
        });
        JButton btnExport  = new JButton("Izvezi u Excel");
        btnExport.addActionListener(e -> {
            ExcelExporter.exportTableToExcel(tableModel);
            ActionLogger.log(prijavljeniKorisnik, "Kliknuo IZVEZI iz Excel");
        });

        JButton btnSaveDb  = new JButton("Spremi u bazu");
        btnSaveDb.addActionListener(e -> {
            if (table.isEditing()) {
                TableCellEditor ed = table.getCellEditor();
                if (ed != null) try { ed.stopCellEditing(); } catch (Exception ignored) {}
            }
            DatabaseHelper.saveToDatabase(tableModel);
            UserDatabaseHelper.saveUserTableSettings(prijavljeniKorisnik, table);
            ActionLogger.log(prijavljeniKorisnik, "Spremio u bazu");
        });
        JButton btnLoadDb  = new JButton("Učitaj iz baze");
        btnLoadDb.addActionListener(e -> {
            if (table.isEditing()) {
                TableCellEditor ed = table.getCellEditor();
                if (ed != null) try { ed.stopCellEditing(); } catch (Exception ignored) {}
            }
            DatabaseHelper.loadFromDatabase(tableModel);
            ActionLogger.log(prijavljeniKorisnik, "Učitao iz baze");
            // schedule recompute once after loading (debounced)
            scheduleDebouncedCompute();
        });

        JButton btnRefresh = new JButton("Osvježi izračune");
        btnRefresh.addActionListener(e -> {
            recomputeAllRows(); // ako imaš
            computeNow();       // forced recalculation
            ActionLogger.log(prijavljeniKorisnik, "Osvježio izračune (manual)");
        });

        JButton btnAddItem = new JButton("Dodaj artikal");
        btnAddItem.addActionListener(e -> {
            Object[] emptyRow = new Object[]{
                    "", "", "", "", null, null, "", "", null, null, null, null, null, null, "", "", ""
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

        // NEW: import missing komitenti from current table (orders)
        JButton btnImportKomitenti = new JButton("Uvezi komitente");
        btnImportKomitenti.addActionListener(e -> {
            if (table.isEditing()) {
                TableCellEditor ed = table.getCellEditor();
                if (ed != null) try { ed.stopCellEditing(); } catch (Exception ignored) {}
            }
            int added = 0;
            Set<String> existing = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            java.util.List<String> namesFromDb = KomitentiDatabaseHelper.loadAllKomitentNames();
            if (namesFromDb != null) existing.addAll(namesFromDb);
            for (int r = 0; r < tableModel.getRowCount(); r++) {
                Object o = tableModel.getValueAt(r, KOMITENT_OPIS_COL);
                if (o == null) continue;
                String naziv = o.toString().trim();
                if (naziv.isEmpty()) continue;
                if (!existing.contains(naziv)) {
                    try {
                        KomitentiDatabaseHelper.insertIfNotExists(naziv, "");
                        existing.add(naziv);
                        added++;
                    } catch (Exception ex) {
                        System.out.println("Greška pri insertIfNotExists za: " + naziv + " -> " + ex.getMessage());
                    }
                }
            }
            komitentTPMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
            JOptionPane.showMessageDialog(frame, "Uvezeno novih komitenata: " + added);
            ActionLogger.log(prijavljeniKorisnik, "Uvezao komitente iz tablice, dodano: " + added);
        });

        bottom.add(btnImport);
        bottom.add(btnExport);
        bottom.add(btnSaveDb);
        bottom.add(btnLoadDb);
        bottom.add(btnRefresh);
        bottom.add(btnDelete);
        bottom.add(btnImportKomitenti);

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

    /**
     * recomputeAllRows sada recomputea mm/m2/duration za sve redove, a zatim radi batch scheduling
     * predviđenih datuma u poretku datumNarudzbe (najstarije -> najmlađe).
     */
    private void recomputeAllRows() {
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            recomputeRow(r);
            recomputeDuration(r);
        }
        // nakon što smo izračunali m2 i duration za sve, napravimo serijski raspored predPlan
        computePredPlansBatch();
        // emit while suppress==true to avoid listener re-entry
        if (tableModel instanceof AbstractTableModel) ((AbstractTableModel) tableModel).fireTableDataChanged();
        else table.repaint();
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

        Object startObj = tableModel.getValueAt(row, idxStart);
        Object endObj   = tableModel.getValueAt(row, idxEnd);

        String start = startObj == null ? "" : startObj.toString().trim();
        String end   = endObj   == null ? "" : endObj.toString().trim();

        if (start.isBlank() || end.isBlank()) {
            try {
                suppressTableModelEvents = true;
                tableModel.setValueAt("", row, idxDur);
            } finally {
                suppressTableModelEvents = false;
            }
            return;
        }

        String outValue = "";
        LocalDateTime startDT = tryParseLocalDateTime(start);
        LocalDateTime endDT = tryParseLocalDateTime(end);

        try {
            if (startDT != null && endDT != null) {
                if (endDT.isBefore(startDT)) {
                    try {
                        suppressTableModelEvents = true;
                        tableModel.setValueAt("", row, idxDur);
                    } finally {
                        suppressTableModelEvents = false;
                    }
                    return;
                }
                long minutes = WorkingTimeCalculator.calculateWorkingMinutes(startDT, endDT);
                if (minutes > 0) {
                    long hh = minutes / 60;
                    long mm = minutes % 60;
                    outValue = String.format("%02d:%02d", hh, mm);
                } else {
                    outValue = "";
                }
            } else {
                String workingCalc = WorkingTimeCalculator.calculateWorkingDuration(start, end);
                String hhmm = convertWorkingToHHMM_orFallback(workingCalc);
                outValue = hhmm;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            outValue = "";
        }

        try {
            suppressTableModelEvents = true;
            tableModel.setValueAt(outValue, row, idxDur);
        } finally {
            suppressTableModelEvents = false;
        }
    }

    /**
     * Robustni parser za razne formate datuma/vremena u LocalDateTime.
     * Utility: try common date/time formats and return LocalDateTime or null
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

        // try epoch seconds/millis
        try {
            long v = Long.parseLong(str);
            if (v > 1000000000000L) return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(v), ZoneId.systemDefault());
            else return LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(v), ZoneId.systemDefault());
        } catch (Exception ignored) {}

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
     * Reagira na promjene tablice, ali ignorira događaje koje je generirao sam program (suppressTableModelEvents).
     */
    private void setUpListeners() {
        tableModel.addTableModelListener(e -> {
            // Prevent handling events triggered by our own programmatic updates
            if (suppressTableModelEvents) return;
            if (e.getType() != TableModelEvent.UPDATE) return;

            int row = e.getFirstRow();
            int col = e.getColumn();

            // Komitent promjena -> automatski postavi predstavnika ako postoji u mapi
            if (col == KOMITENT_OPIS_COL && row >= 0 && row < tableModel.getRowCount()) {
                Object komitentVal = tableModel.getValueAt(row, KOMITENT_OPIS_COL);
                if (komitentVal != null) {
                    String komitent = komitentVal.toString();
                    String predstavnik = komitentTPMap.getOrDefault(komitent, "");
                    try {
                        suppressTableModelEvents = true;
                        tableModel.setValueAt(predstavnik, row, TP_COL);
                    } finally {
                        suppressTableModelEvents = false;
                    }
                }
            }

            // Ako su promijenile mm/m/kom/nazivRobe/kom -> recompute row
            if (col == 3 || col == 5) {
                recomputeRow(row);
                if (tableModel instanceof AbstractTableModel) {
                    ((AbstractTableModel) tableModel).fireTableRowsUpdated(row, row);
                }
            }

            // Ako su promijenili vremena -> recompute duration
            if (col == START_TIME_COL || col == END_TIME_COL) {
                recomputeDuration(row);
                if (tableModel instanceof AbstractTableModel) {
                    ((AbstractTableModel) tableModel).fireTableCellUpdated(row, DURATION_COL);
                }
            }

            // Ako je promjena statusa -> briši predPlan ako "Izrađeno"
            if (col == STATUS_COL_MODEL) {
                Object statusObj = tableModel.getValueAt(row, STATUS_COL_MODEL);
                String status = statusObj == null ? "" : statusObj.toString();
                if ("Izrađeno".equals(status)) {
                    try {
                        suppressTableModelEvents = true;
                        tableModel.setValueAt("", row, PRED_PLAN_COL);
                    } finally {
                        suppressTableModelEvents = false;
                    }
                }
            }

            // Nakon svake relevantne promjene — schedule debounced batch recompute
            scheduleDebouncedCompute();
        });
    }

    /**
     * Debounced scheduler — poziva computePredPlansBatch() nakon kratke pauze.
     * Restartira timer na svaki poziv (standardni debounce).
     */
    private void scheduleDebouncedCompute() {
        // create timer only once here
        if (debounceTimer == null) {
            debounceTimer = new javax.swing.Timer(300, ev -> {
                ((javax.swing.Timer) ev.getSource()).stop();
                // ensure compute runs on EDT
                SwingUtilities.invokeLater(this::computePredPlansBatch);
            });
            debounceTimer.setRepeats(false);
        }
        if (debounceTimer.isRunning()) debounceTimer.restart();
        else debounceTimer.start();
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

        try {
            suppressTableModelEvents = true;
            tableModel.setValueAt(mm == 0 ? null : mm, r, 8);
            tableModel.setValueAt(mVal == 0 ? null : mVal, r, 9);
            Double tisucl = (mm == 0 || mVal == 0) ? null : (mm / 1000) * mVal;
            tableModel.setValueAt(tisucl, r, 10);
            double kom = tableModel.getValueAt(r, 5) instanceof Number
                    ? ((Number) tableModel.getValueAt(r, 5)).doubleValue()
                    : 0;
            Double m2 = (tisucl == null || kom == 0) ? null : tisucl * kom;
            tableModel.setValueAt(m2, r, 11);
        } finally {
            suppressTableModelEvents = false;
        }
    }

    /**
     * Glavna metoda za serijski raspored predPlanIsporuke.
     * Koristi statistiku (StatistikaPanel) ako dostupna, inače ProductionStatsCalculator ili DB fallback.
     */
 // delegator za postojeće pozive
  

    // glavna metoda s force flagom
 // Paste these methods inside your UI class (replace existing computePredPlansBatch / computeNow).
 // Assumes fields exist in the class:
 //   private final java.util.concurrent.atomic.AtomicBoolean computingPredPlans = new java.util.concurrent.atomic.AtomicBoolean(false);
 //   private String lastComputeSignature = "";
 //   private long lastComputeMillis = 0L;
 //   private javax.swing.Timer debounceTimer;
 //   private boolean suppressTableModelEvents = false;
 //   private javax.swing.table.TableModel tableModel;
 //   private javax.swing.JTable table;
 //   private static final double DEFAULT_M2_PER_HOUR = 10.0; // or your project's constant
 //   private static final int PRED_PLAN_COL = /* index */;
 //   private static final int STATUS_COL_MODEL = /* index */;
 //   private static final java.time.LocalTime PLAN_WORK_START = java.time.LocalTime.of(7,0);
 //   private static final java.time.LocalTime PLAN_WORK_END = java.time.LocalTime.of(15,0);
 //   private static final int WORK_HOURS_PER_DAY = 8;
 //   private static final double DEFAULT_M2_PER_HOUR = 10.0; // adjust if you already have a constant
 // Also assumes helper methods exist in class: parseDoubleSafe(String, double), tryParseLocalDateTime(String), scheduleDebouncedCompute().
 // If some names differ in your class, adapt them accordingly.

 private void computePredPlansBatch() {
     computePredPlansBatch(false);
 }

 private void computePredPlansBatch(boolean force) {
     // Prevent overlapping runs
     if (!computingPredPlans.compareAndSet(false, true)) {
         // if already running, schedule a debounced rerun
         scheduleDebouncedCompute();
         return;
     }

     try {
         suppressTableModelEvents = true; // prevent listeners reacting to programmatic changes
         final java.time.format.DateTimeFormatter outFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

         int rowCount = tableModel.getRowCount();

         // 1) Build list of items to schedule
         class Item { int modelRow; java.time.LocalDate orderDate; double m2; String status; }
         java.util.ArrayList<Item> items = new java.util.ArrayList<>();

         double totalRemainingAll = 0.0;
         for (int r = 0; r < rowCount; r++) {
             Object statusObj = tableModel.getValueAt(r, STATUS_COL_MODEL);
             String status = statusObj == null ? "" : statusObj.toString().trim();

             Object m2obj = tableModel.getValueAt(r, 11); // kolona m2 (provjeri indeks)
             double m2 = 0.0;
             if (m2obj instanceof Number) m2 = ((Number) m2obj).doubleValue();
             else if (m2obj instanceof String) m2 = parseDoubleSafe((String) m2obj, 0.0);

             // Clear previous pred plan for finished or empty rows
             if ("Izrađeno".equalsIgnoreCase(status) || m2 <= 0.0) {
                 try { tableModel.setValueAt("", r, PRED_PLAN_COL); } catch (Exception ignored) {}
                 continue;
             }

             // parse datumNarudzbe if present (column 0 assumed)
             java.time.LocalDate ord = null;
             Object od = tableModel.getValueAt(r, 0);
             if (od instanceof String && !((String) od).isBlank()) {
                 java.time.LocalDateTime parsed = tryParseLocalDateTime(od.toString());
                 if (parsed != null) ord = parsed.toLocalDate();
             }

             Item it = new Item();
             it.modelRow = r;
             it.orderDate = ord == null ? java.time.LocalDate.MAX : ord;
             it.m2 = m2;
             it.status = status;
             items.add(it);
             totalRemainingAll += m2;
         }

         // --- Deterministic selection of avgDailyFromStats / globalDailyM2 ---
         double avgDailyFromStats = 0.0;
         double totalRemainingFromStats = 0.0;

         try {
             if (statistikaPanel != null) {
                 avgDailyFromStats = statistikaPanel.getAvgDailyM2();
                 totalRemainingFromStats = statistikaPanel.getTotalRemainingM2();
             }
         } catch (Exception ignored) {}

         if (avgDailyFromStats <= 1e-6 || totalRemainingFromStats <= 0.0) {
             try {
                 Map<String, Object> stats = ProductionStatsCalculator.calculate((javax.swing.table.DefaultTableModel) tableModel, DEFAULT_M2_PER_HOUR);
                 if (stats != null) {
                     if (avgDailyFromStats <= 1e-6) {
                         Object a = stats.get(ProductionStatsCalculator.PROSJEK_M2_PO_DANU);
                         if (a != null) avgDailyFromStats = parseDoubleSafe(a.toString(), 0.0);
                     }
                     if (totalRemainingFromStats <= 0.0) {
                         Object rObj = stats.get(ProductionStatsCalculator.M2_ZAI);
                         if (rObj != null) totalRemainingFromStats = parseDoubleSafe(rObj.toString(), 0.0);
                     }
                 }
             } catch (Exception ex) {
                 ex.printStackTrace();
             }
         }

         double globalDailyM2 = avgDailyFromStats;
         String avgSource = (avgDailyFromStats > 1e-6) ? "statPanel/statsCalc" : "db/fallback";
         System.out.println("computePredPlansBatch: avg source=" + avgSource + ", avgDaily=" + avgDailyFromStats);
         if (globalDailyM2 <= 1e-6) {
             try {
                 globalDailyM2 = DatabaseHelper.getAverageDailyM2(30);
             } catch (Exception ex) {
                 globalDailyM2 = 0.0;
             }
             System.out.println("computePredPlansBatch: DB fallback avgDaily=" + globalDailyM2);
         }

         long workHours = java.time.temporal.ChronoUnit.HOURS.between(PLAN_WORK_START, PLAN_WORK_END);
         if (workHours <= 0) workHours = WORK_HOURS_PER_DAY;
         long minutesPerWorkDay = java.time.temporal.ChronoUnit.MINUTES.between(PLAN_WORK_START, PLAN_WORK_END);
         if (minutesPerWorkDay <= 0) minutesPerWorkDay = workHours * 60;
         double localFallback = DEFAULT_M2_PER_HOUR * (double) workHours;
         if (globalDailyM2 <= 1e-6) globalDailyM2 = localFallback;

         if (totalRemainingFromStats > 0.0) {
             totalRemainingAll = totalRemainingFromStats;
         }

         // ----------------- signature (memoization) -----------------
         // Build signature from aggregated inputs (always build)
         String sig = String.format(java.util.Locale.ROOT, "tot=%.2f|g=%.3f|n=%d",
                 Math.round(totalRemainingAll * 100.0) / 100.0,
                 Math.round(globalDailyM2 * 1000.0) / 1000.0,
                 items.size()
         );
         String signature = Integer.toString(sig.hashCode());

         if (!force) {
             if (signature.equals(lastComputeSignature)) {
                 System.out.println("computePredPlansBatch: signature unchanged, skipping compute.");
                 return;
             }
         }
         // record the new signature so subsequent automatic triggers will be skipped
         lastComputeSignature = signature;
         lastComputeMillis = System.currentTimeMillis();
         // -------------------------------------------------------------------------------

         // per-minute rate and per-item cap
         double m2PerMinuteGlobal = globalDailyM2 / (double) minutesPerWorkDay;
         final double STATIC_PER_ITEM_DAILY_CAP = 2800.0;
         final double PER_ITEM_DAILY_CAP_FINAL = Math.min(STATIC_PER_ITEM_DAILY_CAP, globalDailyM2);

         System.out.println("computePredPlansBatch(aggr): totalRemainingAll=" + totalRemainingAll
                 + ", globalDailyM2=" + globalDailyM2
                 + ", minutesPerWorkDay=" + minutesPerWorkDay
                 + ", m2PerMinute=" + m2PerMinuteGlobal
                 + ", perItemCap=" + PER_ITEM_DAILY_CAP_FINAL);

         // 4) Cursor start: if earliest orderDate is in future, start at that date; else start today at PLAN_WORK_START
         java.time.LocalDate candidateStartDate = java.time.LocalDate.now();
         java.util.Optional<java.time.LocalDate> earliestOpt = items.stream()
                 .map(i -> i.orderDate)
                 .filter(d -> !d.equals(java.time.LocalDate.MAX))
                 .min(java.time.LocalDate::compareTo);
         if (earliestOpt.isPresent()) {
             java.time.LocalDate earliest = earliestOpt.get();
             if (earliest.isAfter(candidateStartDate)) candidateStartDate = earliest;
         }

         // Ensure start is a working day
         java.time.LocalDate startDay = candidateStartDate;
         while (WorkingTimeCalculator.isHolidayOrWeekend(startDay)) startDay = startDay.plusDays(1);
         java.time.LocalDateTime cursor = java.time.LocalDateTime.of(startDay, PLAN_WORK_START);

         // remainingDayCapacity initializes for first day based on time left in day
         double remainingDayCapacity;
         java.time.LocalDateTime dayStart = java.time.LocalDateTime.of(cursor.toLocalDate(), PLAN_WORK_START);
         java.time.LocalDateTime dayEnd = java.time.LocalDateTime.of(cursor.toLocalDate(), PLAN_WORK_END);
         long remainingMinutesToday = java.time.Duration.between(cursor, dayEnd).toMinutes();
         if (remainingMinutesToday < 0) remainingMinutesToday = 0;
         double possibleM2RemainingByTime = remainingMinutesToday * m2PerMinuteGlobal;
         remainingDayCapacity = Math.min(globalDailyM2, Math.max(0.0, possibleM2RemainingByTime));

         // If cursor is exactly at start, remainingDayCapacity == globalDailyM2
         if (cursor.toLocalTime().equals(PLAN_WORK_START)) remainingDayCapacity = globalDailyM2;

         // 5) Serial allocation: iterate items, consume day-by-day capacity
         for (Item it : items) {
             int r = it.modelRow;
             double remainingM2 = it.m2;
             boolean finished = false;

             // Do not schedule before datumNarudzbe (if set)
             if (!it.orderDate.equals(java.time.LocalDate.MAX)) {
                 java.time.LocalDateTime candidate = java.time.LocalDateTime.of(it.orderDate, PLAN_WORK_START);
                 if (cursor.isBefore(candidate)) {
                     cursor = candidate;
                     while (WorkingTimeCalculator.isHolidayOrWeekend(cursor.toLocalDate())) {
                         cursor = java.time.LocalDateTime.of(cursor.toLocalDate().plusDays(1), PLAN_WORK_START);
                     }
                     dayStart = java.time.LocalDateTime.of(cursor.toLocalDate(), PLAN_WORK_START);
                     dayEnd = java.time.LocalDateTime.of(cursor.toLocalDate(), PLAN_WORK_END);
                     long remMin = java.time.Duration.between(cursor, dayEnd).toMinutes();
                     if (remMin < 0) remMin = 0;
                     remainingDayCapacity = Math.min(globalDailyM2, remMin * m2PerMinuteGlobal);
                     if (cursor.toLocalTime().equals(PLAN_WORK_START)) remainingDayCapacity = globalDailyM2;
                 }
             }

             // Ensure cursor on a working day and within working hours
             if (cursor.toLocalTime().isBefore(PLAN_WORK_START)) cursor = java.time.LocalDateTime.of(cursor.toLocalDate(), PLAN_WORK_START);
             if (!cursor.toLocalTime().isBefore(PLAN_WORK_END)) {
                 java.time.LocalDate next = cursor.toLocalDate().plusDays(1);
                 while (WorkingTimeCalculator.isHolidayOrWeekend(next)) next = next.plusDays(1);
                 cursor = java.time.LocalDateTime.of(next, PLAN_WORK_START);
                 remainingDayCapacity = globalDailyM2;
             }
             while (WorkingTimeCalculator.isHolidayOrWeekend(cursor.toLocalDate())) {
                 java.time.LocalDate next = cursor.toLocalDate().plusDays(1);
                 while (WorkingTimeCalculator.isHolidayOrWeekend(next)) next = next.plusDays(1);
                 cursor = java.time.LocalDateTime.of(next, PLAN_WORK_START);
                 remainingDayCapacity = globalDailyM2;
             }

             // allocate this item across days
             while (remainingM2 > 1e-9) {
                 java.time.LocalDate currentDate = cursor.toLocalDate();
                 if (WorkingTimeCalculator.isHolidayOrWeekend(currentDate)) {
                     java.time.LocalDate next = currentDate.plusDays(1);
                     while (WorkingTimeCalculator.isHolidayOrWeekend(next)) next = next.plusDays(1);
                     cursor = java.time.LocalDateTime.of(next, PLAN_WORK_START);
                     remainingDayCapacity = globalDailyM2;
                     continue;
                 }

                 java.time.LocalDateTime segStart = cursor.isAfter(java.time.LocalDateTime.of(currentDate, PLAN_WORK_START)) ? cursor : java.time.LocalDateTime.of(currentDate, PLAN_WORK_START);
                 java.time.LocalDateTime segEnd = java.time.LocalDateTime.of(currentDate, PLAN_WORK_END);
                 long availMinutes = segEnd.isAfter(segStart) ? java.time.Duration.between(segStart, segEnd).toMinutes() : 0;
                 if (availMinutes <= 0) {
                     java.time.LocalDate next = currentDate.plusDays(1);
                     while (WorkingTimeCalculator.isHolidayOrWeekend(next)) next = next.plusDays(1);
                     cursor = java.time.LocalDateTime.of(next, PLAN_WORK_START);
                     remainingDayCapacity = globalDailyM2;
                     continue;
                 }

                 double availM2ByTime = availMinutes * m2PerMinuteGlobal;
                 double perDayCapForThisRow = Math.min(PER_ITEM_DAILY_CAP_FINAL, minutesPerWorkDay * m2PerMinuteGlobal);
                 double availM2ThisDay = Math.min(availM2ByTime, perDayCapForThisRow);
                 availM2ThisDay = Math.min(availM2ThisDay, remainingDayCapacity);

                 if (availM2ThisDay <= 1e-9) {
                     java.time.LocalDate next = currentDate.plusDays(1);
                     while (WorkingTimeCalculator.isHolidayOrWeekend(next)) next = next.plusDays(1);
                     cursor = java.time.LocalDateTime.of(next, PLAN_WORK_START);
                     remainingDayCapacity = globalDailyM2;
                     continue;
                 }

                 if (availM2ThisDay >= remainingM2 - 1e-6) {
                     long minutesNeeded = m2PerMinuteGlobal > 0
                             ? (long) Math.ceil(remainingM2 / m2PerMinuteGlobal)
                             : availMinutes;
                     if (minutesNeeded > availMinutes) minutesNeeded = availMinutes;
                     java.time.LocalDateTime finishTime = segStart.plusMinutes(minutesNeeded);
                     String outDate = finishTime.toLocalDate().format(outFmt);
                     try { tableModel.setValueAt(outDate, r, PRED_PLAN_COL); } catch (Exception ignored) {}
                     double consumed = Math.min(remainingM2, minutesNeeded * m2PerMinuteGlobal);
                     remainingDayCapacity = Math.max(0.0, remainingDayCapacity - consumed);
                     cursor = finishTime;
                     finished = true;
                     break;
                 } else {
                     long minutesConsumed = m2PerMinuteGlobal > 0
                             ? (long) Math.ceil(availM2ThisDay / m2PerMinuteGlobal)
                             : availMinutes;
                     if (minutesConsumed > availMinutes) minutesConsumed = availMinutes;
                     double actuallyConsumed = Math.min(availM2ThisDay, minutesConsumed * m2PerMinuteGlobal);
                     remainingM2 = Math.max(0.0, remainingM2 - actuallyConsumed);
                     remainingDayCapacity = Math.max(0.0, remainingDayCapacity - actuallyConsumed);

                     java.time.LocalDate next = currentDate.plusDays(1);
                     while (WorkingTimeCalculator.isHolidayOrWeekend(next)) next = next.plusDays(1);
                     cursor = java.time.LocalDateTime.of(next, PLAN_WORK_START);
                     remainingDayCapacity = globalDailyM2;
                 }
             }

             if (!finished) {
                 try { tableModel.setValueAt("", r, PRED_PLAN_COL); } catch (Exception ignored) {}
             }

             if (!cursor.toLocalTime().isBefore(PLAN_WORK_END)) {
                 java.time.LocalDate next = cursor.toLocalDate().plusDays(1);
                 while (WorkingTimeCalculator.isHolidayOrWeekend(next)) next = next.plusDays(1);
                 cursor = java.time.LocalDateTime.of(next, PLAN_WORK_START);
                 remainingDayCapacity = globalDailyM2;
             }
         }

     } catch (Exception ex) {
         ex.printStackTrace();
     } finally {
         if (tableModel instanceof javax.swing.table.AbstractTableModel) {
             ((javax.swing.table.AbstractTableModel) tableModel).fireTableDataChanged();
         } else {
             table.repaint();
         }
         suppressTableModelEvents = false;
         computingPredPlans.set(false);
     }
 }



    // ----------------- pomoćne metode -----------------

    /** Utility: safe double parse with comma support and fallback */
    private static double parseDoubleSafe(String s, double fallback) {
        if (s == null) return fallback;
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception ex) {
            try {
                // remove non-numeric chars
                String cleaned = s.replaceAll("[^0-9,\\.-]", "").replace(',', '.');
                return Double.parseDouble(cleaned);
            } catch (Exception ex2) {
                return fallback;
            }
        }
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
        combo.setLightWeightPopupEnabled(false); // fix for popup on some LAFs
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
                    try {
                        suppressTableModelEvents = true;
                        tableModel.setValueAt(noviKomitent, modelRow, KOMITENT_OPIS_COL);
                        tableModel.setValueAt(trenutniTP, modelRow, TP_COL);
                    } finally {
                        suppressTableModelEvents = false;
                    }
                    KomitentiDatabaseHelper.insertIfNotExists(noviKomitent, trenutniTP);
                    komitentTPMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
                    // refresh combo items
                    combo.removeAllItems();
                    for (String k : KomitentiDatabaseHelper.loadAllKomitentNames()) combo.addItem(k);
                    setUpPredstavnikDropdown();
                    scheduleDebouncedCompute();
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
     * Postavlja dropdown editor za kolonu Trg. predstavnik (kolona TP_COL).
     */
    private void setUpPredstavnikDropdown() {
        int viewIdx = table.convertColumnIndexToView(TP_COL);
        if (viewIdx < 0) return;

        JComboBox<String> combo = new JComboBox<>();
        combo.setEditable(true);
        combo.setLightWeightPopupEnabled(false); // fix for popup issues

        try {
            for (String p : KomitentiDatabaseHelper.loadAllPredstavnici()) combo.addItem(p);
        } catch (Exception ignored) {}

        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                try {
                    combo.removeAllItems();
                    for (String p : KomitentiDatabaseHelper.loadAllPredstavnici()) combo.addItem(p);
                } catch (Exception ignored) {}
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
                    try {
                        suppressTableModelEvents = true;
                        tableModel.setValueAt(trenutniKomitent, modelRow, KOMITENT_OPIS_COL);
                        tableModel.setValueAt(noviPredstavnik, modelRow, TP_COL);
                    } finally {
                        suppressTableModelEvents = false;
                    }
                    KomitentiDatabaseHelper.insertIfNotExists(trenutniKomitent, noviPredstavnik);
                    komitentTPMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
                    scheduleDebouncedCompute();
                }
            }
        });

        table.getColumnModel().getColumn(viewIdx).setCellEditor(new DefaultCellEditor(combo));
    }

    // DateTime/DateOnly editors and renderers (already included above)
    // ... (they are present earlier in this file) ...

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
     * Postavlja dropdown izbornik za kolonu "status".
     * Omogućuje automatsko postavljanje vremena i korisnika.
     */
    private void setUpStatusDropdown() {
        int viewIdx = table.convertColumnIndexToView(STATUS_COL_MODEL);
        if (viewIdx < 0) return;

        TableColumn col = table.getColumnModel().getColumn(viewIdx);
        String[] opcije = {"", "U izradi", "Izrađeno"};
        JComboBox<String> combo = new JComboBox<>(opcije);
        combo.setLightWeightPopupEnabled(false); // be safer on some LAFs

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
                    try {
                        suppressTableModelEvents = true;
                        tableModel.setValueAt("U izradi", modelRow, STATUS_COL_MODEL);
                        tableModel.setValueAt(DateUtils.formatWithoutSeconds(LocalDateTime.now()), modelRow, START_TIME_COL);
                        tableModel.setValueAt(prijavljeniKorisnik, modelRow, 7);
                        ActionLogger.logTableAction(prijavljeniKorisnik, "Status 'U izradi'", tableModel, modelRow);
                    } finally {
                        suppressTableModelEvents = false;
                    }
                    scheduleDebouncedCompute();
                    return sel;

                } else if ("Izrađeno".equals(sel)) {
                    int ans = JOptionPane.showConfirmDialog(
                            frame,
                            "Potvrdi 'Izrađeno'?",
                            "Potvrda",
                            JOptionPane.YES_NO_CANCEL_OPTION
                    );
                    if (ans == JOptionPane.YES_OPTION) {
                        try {
                            suppressTableModelEvents = true;
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
                            // clear predPlan for completed
                            tableModel.setValueAt("", modelRow, PRED_PLAN_COL);
                            ActionLogger.logTableAction(prijavljeniKorisnik, "Status 'Izrađeno'", tableModel, modelRow);
                        } finally {
                            suppressTableModelEvents = false;
                        }
                        scheduleDebouncedCompute();
                        return sel;
                    } else {
                        return originalValue == null ? "" : originalValue;
                    }

                } else {
                    try {
                        suppressTableModelEvents = true;
                        tableModel.setValueAt("", modelRow, STATUS_COL_MODEL);
                    } finally {
                        suppressTableModelEvents = false;
                    }
                    scheduleDebouncedCompute();
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
        combo.setLightWeightPopupEnabled(false);
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
                            try {
                                suppressTableModelEvents = true;
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
                            } finally {
                                suppressTableModelEvents = false;
                            }
                            komitentTPMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
                            dialog.dispose();
                            scheduleDebouncedCompute();
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

            try {
                suppressTableModelEvents = true;
                tm.setValueAt("", modelRow, STATUS_COL_MODEL);
            } finally {
                suppressTableModelEvents = false;
            }
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

            // recompute batch after unlocking
            scheduleDebouncedCompute();
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

                    if (modelCol == KOMITENT_OPIS_COL) {
                        openKomitentDialog(modelRow);
                    } else {
                        if (tableModel.isCellEditable(modelRow, modelCol)) {
                            if (table.editCellAt(viewRow, viewCol)) {
                                Component editor = table.getEditorComponent();
                                if (editor != null) {
                                    editor.requestFocus();
                                    if (editor instanceof JComboBox) {
                                        final JComboBox<?> cb = (JComboBox<?>) editor;
                                        try {
                                            if (cb.isShowing()) {
                                                cb.showPopup();
                                            } else {
                                                SwingUtilities.invokeLater(() -> {
                                                    try {
                                                        if (cb.isShowing()) cb.showPopup();
                                                    } catch (IllegalComponentStateException ex) {
                                                        // ignore
                                                    }
                                                });
                                            }
                                        } catch (IllegalComponentStateException ex) {
                                            SwingUtilities.invokeLater(() -> {
                                                try {
                                                    if (cb.isShowing()) cb.showPopup();
                                                } catch (Exception ignored) {}
                                            });
                                        }
                                    }
                                }
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
                try {
                    suppressTableModelEvents = true;
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
                } finally {
                    suppressTableModelEvents = false;
                }
                komitentTPMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
                dialog.dispose();
                scheduleDebouncedCompute();
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
    
    private void computeNow() {
        if (debounceTimer != null && debounceTimer.isRunning()) debounceTimer.stop();
        computePredPlansBatch(true); // forced recompute; the method will update lastComputeSignature
    }
    
 // Paste the following inner classes inside the UI class:

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

    // DateTime and DateOnly editors/renderers are defined earlier in the file (DateTimeCellEditor, DateTimeCellRenderer, DateOnlyCellEditor, DateOnlyCellRenderer)

    /**
     * Main metoda — ulazna točka aplikacije.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UserDatabaseHelper.initializeUserTable();
            new LoginUI();
        });
    }

    // Unutar klase UI, dodaj ovu unutarnju klasu:
    private static class DoubleClickTable extends JTable {
        public DoubleClickTable(TableModel model) {
            super(model);
        }

        // Override editCellAt to require double-click for mouse events
        @Override
        public boolean editCellAt(int row, int column, EventObject e) {
            // If editing was started by a mouse event, require double-click
            if (e instanceof MouseEvent) {
                MouseEvent me = (MouseEvent) e;
                if (me.getClickCount() < 2) {
                    // do not start editing on single click
                    return false;
                }
            }
            // allow keyboard / programmatic editing
            return super.editCellAt(row, column, e);
        }
    }
}