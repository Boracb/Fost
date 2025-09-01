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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.*;
import java.util.regex.*;

/**
 * Glavna UI klasa aplikacije.
 * Spaja stare značajke tvoje klase i nove promjene (robustni recomputeDuration koji koristi
 * WorkingTimeCalculator.calculateWorkingMinutes, dodatni editori/renderer-i i admin funkcije).
 */

//dodati polje za planDatumIsporuke u tablicu i uvoz iz excela i izvoz u excel 


public class UI {

    // --- Polja klase ---
    private JFrame frame;
    private JTable table;
    private static DefaultTableModel tableModel;
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
            "djelatnik","mm","m","tisucl","m2","startTime","endTime","duration", "planDatumIsporuke",
            "trgovackiPredstavnik"
    };

    // Konstante — indeksi temeljeni na modelu
    private static final int STATUS_COL_MODEL = 6;
    private static final int START_TIME_COL   = 12;
    private static final int END_TIME_COL     = 13;
    private static final int DURATION_COL     = 14;
    private static final int KOMITENT_OPIS_COL = 2;
    private static final int TP_COL            = 16;
    private static final int PL_COL           = 15;

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
                // duration & trgovacki predstavnik su neuredivi
                if (modelCol == DURATION_COL || modelCol == TP_COL || modelCol == PL_COL) {
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

        int[] widths = {120,120,180,180,100,60,110,140,60,80,90,100,140,140,140,140};
        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        System.out.println("Rows loaded: " + tableModel.getRowCount());
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            System.out.println("Row " + r + " datumNarudzbe: " + tableModel.getValueAt(r, 0));
        }
       
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        

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
        debugPrintTableModelInfo();

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
				if (ed != null)
					try {
						ed.stopCellEditing();
					} catch (Exception ignored) {
					}
			}
			DatabaseHelper.loadFromDatabase(tableModel);
			UserDatabaseHelper.loadUserTableSettings(prijavljeniKorisnik, table);
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
        
        DatabaseHelper.loadFromDatabase(tableModel);
        System.out.println("Rows loaded: " + tableModel.getRowCount());
        debugPrintTableModelInfo();
        computePlanDatumIsporukeForAllRows();
        tableModel.fireTableDataChanged();
        table.repaint();

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
                    tableModel.setValueAt(noviKomitent, modelRow, KOMITENT_OPIS_COL);
                    tableModel.setValueAt(trenutniTP, modelRow, TP_COL);
                       // postavi planDatumIsporuke na predDatumIsporuke
            

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
 // Zamijeni postojeću metodu enableDoubleClickOpeners ovim kodom u ui/UI.java
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
                                        final JComboBox<?> cb = (JComboBox<?>) editor;
                                        // Ako je komponenta već prikazana, pokaži odmah popup.
                                        // Inače odgodi prikaz na EDT (i provjeri ponovno isShowing).
                                        try {
                                            if (cb.isShowing()) {
                                                cb.showPopup();
                                            } else {
                                                SwingUtilities.invokeLater(() -> {
                                                    try {
                                                        if (cb.isShowing()) cb.showPopup();
                                                    } catch (IllegalComponentStateException ex) {
                                                        // dodatna zaštita - ignoriraj ako još uvijek nije prikazano
                                                        System.out.println("showPopup aborted: " + ex.getMessage());
                                                    }
                                                });
                                            }
                                        } catch (IllegalComponentStateException ex) {
                                            // zaštitni fallback
                                            SwingUtilities.invokeLater(() -> {
                                                try {
                                                    if (cb.isShowing()) cb.showPopup();
                                                } catch (Exception ignored) {}
                                            });
                                        }
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
    



 // Java
// private void computePlanDatumIsporukeForAllRows() {
//	 System.out.println("Computing Plan Datum Isporuke for all rows...");
//     int idxOrderDate = tableModel.findColumn("datumNarudzbe");
//     System.out.println("Index of datumNarudzbe column: " + idxOrderDate);
//     if (idxOrderDate < 0) return;
//     System.out.println("Total rows in table model: " + tableModel.getRowCount());
//     DateTimeFormatter[] fmts = {
//  
//         DateTimeFormatter.ofPattern("dd/MM/yyyy"),
//         DateTimeFormatter.ofPattern("dd.MM.yyyy"),
//            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
//     };
//     for (int r = 0; r < tableModel.getRowCount(); r++) {
//    	 System.out.println("Processing row " + r);
//         Object orderDateObj = tableModel.getValueAt(r, idxOrderDate);
//         System.out.println("datumNarudzbe value: " + orderDateObj);
//         String orderDateStr = orderDateObj == null ? "" : orderDateObj.toString().trim();
//         System.out.println("Trimmed datumNarudzbe: '" + orderDateStr + "'");
//         LocalDate orderDate = null;
//         System.out.println("Attempting to parse datumNarudzbe...");
//         for (DateTimeFormatter fmt : fmts) {
//             try {
//                 orderDate = LocalDate.parse(orderDateStr, fmt);
//                 System.out.println("Parsed datumNarudzbe: " + orderDate);
//                 break;
//             } catch (Exception ignored) {}
//         }
////         String planDatumIsporukeValue = "";
////         if (orderDate != null) {
////             LocalDate planDate = orderDate.plusDays(7);
////             planDatumIsporukeValue = planDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
////             System.out.println("Computed planDatumIsporuke: " + planDatumIsporukeValue);
////         }
//        // tableModel.setValueAt(planDatumIsporukeValue, r, PL_COL);
//         //System.out.println("Set planDatumIsporuke for row " + r + " to: " + planDatumIsporukeValue);
//     }
//     tableModel.fireTableDataChanged();
//     System.out.println("Completed computing Plan Datum Isporuke for all rows.");
// }



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
        


        // Also override processMouseEvent to avoid some look-and-feel shortcuts
        // (optional) — but not necessary in most cases.
    }
    
    
    //metoda koja izračunava plan datum isporuke za sve redove na temelju datuma narudžbe izostavlja status "Izrađeno"  
    // sa naznakom da kreće od prvog reda i dodaje datume u kolonu planDatumIsporuke i produžuje za već datum koji je naznačen u
   // polju "daniZaIsporuku" jedan raed ili jedan artikal maksimalno 2800 m2, znači uzima prosjek m2 kapacitet po danu sum m2 sve šta ima status izrađeno i dijeli 
// sa ukupnim m2 koje je za ozraditi odnosno koji nema status izrađeno. kapacitet se uzima iz baze naši da se zbroji sve što je m2 status izrađeno u posljednjih 30 dana
    
    
    

 // Computes planned delivery dates for all rows, skipping "Izrađeno".
 // - Earliest date per row = datumNarudzbe + daniZaIsporuku (default 7 if column missing/blank).
 // - Schedules rows sequentially (model order) across days.
 // - Daily total capacity = average m2 completed over last 30 calendar days (from model/DB).
 // - Per-article-per-day cap = 2800 m2 (one article cannot exceed 2800 m2 in a single day).
 //   If article's m2 > 2800, it spans multiple days. The planned date becomes the last day needed.
 // - Writes into "planDatumIsporuke" if it exists, otherwise falls back to "predDatumIsporuke".
 // - Dates formatted as "dd.MM.yyyy".
 // Replace or add these methods in UI.java (next to your other recompute helpers).
 // Robust computePlanDatumIsporukeForAllRows() that handles Date/LocalDate/String values,
 // writes into planDatumIsporuke (fallback predDatumIsporuke) and logs useful diagnostics.
 // Replacement: compute with a minimum daily capacity fallback and detailed logging of updated rows.
 // Novi computePlanDatumIsporukeForAllRows() s dodatnom dijagnostikom i fallback upisom
 // Replace or add the following methods inside your UI class (UI.java).
 // computePlanDatumIsporukeForAllRows ensures EDT, updates model values, fires per-row updates and scrolls to first updated row.

 // Add these methods to your UI class (replace previous computePlanDatumIsporukeForAllRows and helpers).
 // This version:
 // - Uses a strict historical average: sum(m2 of "Izrađeno") in the last 30 WORKING days / 30
 // - Scheduling starts from TODAY and never schedules on weekends or specified non-working days
 // - One-article-per-day cap = 2800 m2
 // - No arbitrary minimum fallback for avgDailyCapacity (but if avg==0 we log and fall back to per-article cap to avoid infinite loop)
 // - Ensures EDT, fireTableRowsUpdated per updated row, and scrolls to the first updated visible row
 //
 // IMPORTANT: populate nonWorkingDaysSet with company holidays if needed (format LocalDate).
 // By default only weekends are considered non-working.
 private final java.util.Set<java.time.LocalDate> nonWorkingDaysSet = new java.util.HashSet<>(); // populate with holidays as needed

//Replace computePlanDatumIsporukeForAllRows with this implementation (and include the helper methods below).
//This implementation fills each working day up to avgDailyCapacity; per-article cap applies but remaining capacity can be used by other articles.



//computePlanDatumIsporukeForAllRows - day-fill algorithm with sorted queue by earliest,
//strict historical avg over last N working days, skip weekends and holidays,
//per-article cap + shared daily capacity behaviour fixed.


//import logic.WorkingTimeCalculator;  // dodaj na vrh datoteke ako nije već importano

//computePlanDatumIsporukeForAllRows - fill each working day up to avgDailyCapacity before moving to next day.
//Uses WorkingTimeCalculator for holidays/weekends. If allowStartBeforeEarliest==true the scheduler may
//start orders before their earliest if needed to fill the day (set to false to forbid).
//import logic.WorkingTimeCalculator; // dodaj na vrh klase ako nije importano

//computePlanDatumIsporukeForAllRows - ista logika kao prethodno, ali dodan DIAG ispisi queue preview (prvih 30)
 private void computePlanDatumIsporukeForAllRows() {
	    // Ensure on EDT
	    if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
	        javax.swing.SwingUtilities.invokeLater(() -> computePlanDatumIsporukeForAllRows());
	        return;
	    }

	    System.out.println("Computing Plan Datum Isporuke (day-fill, per-article cap enforced, debug queue preview)...");

	    int idxOrderDate = tableModel.findColumn("datumNarudzbe");
	    int idxStatus = tableModel.findColumn("status");
	    int idxM2 = tableModel.findColumn("m2");
	    int idxDaniZaIsporuku = tableModel.findColumn("daniZaIsporuke");
	    int idxPlanDatumIsporuke = tableModel.findColumn("planDatumIsporuke");
	    int idxPred = tableModel.findColumn("predDatumIsporuke");

	    if (idxPlanDatumIsporuke < 0) idxPlanDatumIsporuke = idxPred; // fallback

	    if (idxOrderDate < 0 || idxStatus < 0 || idxM2 < 0 || idxPlanDatumIsporuke < 0) {
	        System.out.printf("Cannot compute: missing columns order=%d status=%d m2=%d plan/fallback=%d%n",
	                idxOrderDate, idxStatus, idxM2, idxPlanDatumIsporuke);
	        return;
	    }

	    final int windowWorkingDays = 30;
	    final double perArticleDailyCap = 2800.0;

	    // Strict historical average
	    double avgDailyCapacity = computeAverageDailyCapacityM2_LastNWorkingDays(windowWorkingDays);

	    // >>> FORCED DAILY CAPACITY: change this to your target (e.g. 4424.01 or 4600.0).
	    // If forcedDailyCapacity <= 0, code will use historical avgDailyCapacity.
	    final double forcedDailyCapacity = 4424.01; // <- set desired daily throughput here

	    if (avgDailyCapacity <= 0.0) {
	        System.out.printf("Warning: historical avgDailyCapacity=%.2f (<=0).%n", avgDailyCapacity);
	    }
	    System.out.printf("DIAG: Overriding avgDailyCapacity %.2f -> forcedDailyCapacity %.2f m2/day%n",
		        avgDailyCapacity, forcedDailyCapacity);
		avgDailyCapacity = forcedDailyCapacity;

	    final java.time.format.DateTimeFormatter outFmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
	    final java.time.format.DateTimeFormatter[] parseFmts = new java.time.format.DateTimeFormatter[]{
	            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"),
	            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
	            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
	    };

	    int totalRows = tableModel.getRowCount();
	    java.time.LocalDate today = java.time.LocalDate.now();

	    // Build queue: row, remaining m2, earliest (normalized), assignedToday
	    class Order {
	        final int row;
	        double remaining;
	        java.time.LocalDate earliest;
	        double assignedToday;
	        Order(int row, double remaining, java.time.LocalDate earliest) {
	            this.row = row; this.remaining = remaining; this.earliest = earliest; this.assignedToday = 0.0;
	        }
	    }
	    java.util.List<Order> queue = new java.util.ArrayList<>();
	    double totalRemaining = 0.0;

	    for (int r = 0; r < totalRows; r++) {
	        String status = safeString(tableModel.getValueAt(r, idxStatus));
	        if ("izrađeno".equalsIgnoreCase(status) || "izradjeno".equalsIgnoreCase(status)) continue;

	        Object orderObj = tableModel.getValueAt(r, idxOrderDate);
	        java.time.LocalDate orderDate = parseLocalDateGeneric(orderObj);
	        if (orderDate == null) orderDate = parseLocalDate(safeString(orderObj), parseFmts);
	        if (orderDate == null) {
	            tableModel.setValueAt("", r, idxPlanDatumIsporuke);
	            continue;
	        }

	        int daniZaIsporuku = 7;
	        if (idxDaniZaIsporuku >= 0) {
	            daniZaIsporuku = parseIntOrDefault(tableModel.getValueAt(r, idxPlanDatumIsporuke), 7);
	            if (daniZaIsporuku < 0) daniZaIsporuku = 0;
	        }

	        java.time.LocalDate earliest = orderDate.plusDays(daniZaIsporuku);
	        double m2 = parseDoubleOrZero(tableModel.getValueAt(r, idxM2));
	        if (m2 <= 0.0) {
	            // set trivial plan on next working day >= earliest or today
	            java.time.LocalDate candidate = earliest.isBefore(today) ? today : earliest;
	            java.time.LocalDate plan = nextWorkingDay(candidate);
	            tableModel.setValueAt(plan.format(outFmt), r, idxPlanDatumIsporuke);
	            continue;
	        }

	        // normalize earliest: if in past, make it today (eligible immediately)
	        if (earliest.isBefore(today)) earliest = today;

	        queue.add(new Order(r, m2, earliest));
	        totalRemaining += m2;
	    }

	    if (queue.isEmpty()) {
	        tableModel.fireTableDataChanged();
	        System.out.println("DIAG: No orders to schedule.");
	        return;
	    }

	    // Sort queue by earliest then by row (stable)
	    queue.sort((a, b) -> {
	        int c = a.earliest.compareTo(b.earliest);
	        if (c != 0) return c;
	        return Integer.compare(a.row, b.row);
	    });

	    // DIAG: preview first 30 queue items to spot bad earliest/parsing
	    System.out.println("DIAG: Queue preview (first 30 items):");
	    int preview = Math.min(queue.size(), 30);
	    for (int i = 0; i < preview; i++) {
	        Order o = queue.get(i);
	        System.out.printf("DIAG: [%d] row=%d remaining=%.2f earliest=%s%n", i, o.row, o.remaining, o.earliest.format(outFmt));
	    }
	    if (queue.size() > preview) System.out.printf("DIAG: ... (+%d more)%n", queue.size() - preview);
	    System.out.printf("DIAG: totalRemaining=%.2f m2%n", totalRemaining);

	    // Diagnostics
	    java.time.LocalDate minEarliest = null, maxEarliest = null;
	    for (Order o : queue) {
	        if (minEarliest == null || o.earliest.isBefore(minEarliest)) minEarliest = o.earliest;
	        if (maxEarliest == null || o.earliest.isAfter(maxEarliest)) maxEarliest = o.earliest;
	    }
	    double estimatedDays = totalRemaining / avgDailyCapacity;
	    java.time.LocalDate expectedLast = addWorkingDays(today, Math.max(0, (int)Math.ceil(estimatedDays) - 1));
	    System.out.printf("DIAG: queueSize=%d totalRemaining=%.2f estimatedDays=%.2f earliestRange=[%s..%s] expectedLast=%s%n",
	            queue.size(), totalRemaining, estimatedDays,
	            (minEarliest==null ? "<none>" : minEarliest.format(outFmt)),
	            (maxEarliest==null ? "<none>" : maxEarliest.format(outFmt)),
	            (expectedLast==null ? "<none>" : expectedLast.format(outFmt)));

	    // Scheduler policy: allow using orders even if earliest > currentDay to fill the day.
	    boolean allowStartBeforeEarliest = true;

	    // Scheduling loop: for each working day, keep assigning until day's capacity is (nearly) exhausted.
	    java.util.List<Integer> updatedRows = new java.util.ArrayList<>();
	    boolean[] planSet = new boolean[totalRows];
	    int remainingOrders = 0;
	    for (Order o : queue) if (o.remaining > 0) remainingOrders++;

	    java.time.LocalDate currentDay = nextWorkingDay(today);
	    int daysUsed = 0;
	    int safetyDaysLeft = 365 * 5; // safety guard
	    final double EPS = 1e-6;

	    while (remainingOrders > 0 && safetyDaysLeft-- > 0) {
	        // ensure currentDay is working
	        if (!isWorkingDay(currentDay)) { currentDay = nextWorkingDay(currentDay.plusDays(1)); continue; }

	        // reset per-article assignedToday at start of this day
	        for (Order o : queue) o.assignedToday = 0.0;

	        // dayRemaining starts as avgDailyCapacity
	        double dayRemaining = avgDailyCapacity;
	        boolean anyAssignedThisDay = false;

	        // Try to fill the day completely: loop until dayRemaining ~ 0 or we can't assign anything more this day
	        while (dayRemaining > EPS) {
	            // find next order to assign:
	            // prefer orders with earliest <= currentDay (eligible) in queue order and that still have per-day capacity
	            Order pick = null;
	            for (Order o : queue) {
	                if (o.remaining > EPS && !currentDay.isBefore(o.earliest) && (o.assignedToday + EPS) < perArticleDailyCap) {
	                    pick = o;
	                    break;
	                }
	            }
	            // if none eligible and policy allows, pick next available order regardless of earliest, but only if it still has per-day capacity
	            if (pick == null && allowStartBeforeEarliest) {
	                for (Order o : queue) {
	                    if (o.remaining > EPS && (o.assignedToday + EPS) < perArticleDailyCap) {
	                        pick = o;
	                        break;
	                    }
	                }
	            }
	            // if still none, break (no assignable orders left today)
	            if (pick == null) break;

	            // compute how much this article can still get this day
	            double canForArticle = Math.max(0.0, perArticleDailyCap - pick.assignedToday);
	            double assign = Math.min(pick.remaining, Math.min(canForArticle, dayRemaining));
	            if (assign <= EPS) break;

	            // assign
	            pick.remaining -= assign;
	            pick.assignedToday += assign;
	            dayRemaining -= assign;
	            anyAssignedThisDay = true;

	            // if finished, set plan to currentDay
	            if (pick.remaining <= EPS) {
	                tableModel.setValueAt(currentDay.format(outFmt), pick.row, idxPlanDatumIsporuke);
	                planSet[pick.row] = true;
	                updatedRows.add(pick.row);
	                remainingOrders--;
	            }
	            // else residual remains for next days
	        } // end filling day

	        if (anyAssignedThisDay) daysUsed++;

	        // If we couldn't assign anything this day, jump to next earliest remaining order's earliest (if any)
	        if (!anyAssignedThisDay) {
	            java.time.LocalDate nextEarliest = null;
	            for (Order o : queue) {
	                if (o.remaining > EPS) {
	                    if (nextEarliest == null || o.earliest.isBefore(nextEarliest)) nextEarliest = o.earliest;
	                }
	            }
	            if (nextEarliest == null) break; // nothing left
	            currentDay = nextWorkingDay(nextEarliest);
	        } else {
	            // move to next working day
	            currentDay = nextWorkingDay(currentDay.plusDays(1));
	        }
	    } // end scheduling loop

	    // Fallback: any remaining orders -> set to next working day >= earliest or today
	    int fallbackAssigned = 0;
	    for (Order o : queue) {
	        if (planSet[o.row]) continue;
	        if (o.remaining <= EPS) continue;
	        java.time.LocalDate candidate = o.earliest.isBefore(today) ? today : o.earliest;
	        java.time.LocalDate plan = nextWorkingDay(candidate);
	        tableModel.setValueAt(plan.format(outFmt), o.row, idxPlanDatumIsporuke);
	        fallbackAssigned++;
	        updatedRows.add(o.row);
	    }

	    // Fire updates for changed rows
	    for (int rr : updatedRows) {
	        try { tableModel.fireTableRowsUpdated(rr, rr); } catch (Exception ignored) {}
	    }
	    table.repaint();

	    // determine last scheduled date (max over all plan dates we wrote)
	    java.time.LocalDate lastScheduled = null;
	    for (int r : updatedRows) {
	        String s = safeString(tableModel.getValueAt(r, idxPlanDatumIsporuke));
	        java.time.LocalDate d = parseLocalDateGeneric(s);
	        if (d == null) d = parseLocalDate(s, parseFmts);
	        if (d != null) {
	            if (lastScheduled == null || d.isAfter(lastScheduled)) lastScheduled = d;
	        }
	    }

	    System.out.printf("DIAG: Scheduling complete: queueSize=%d scheduled=%d fallbackAssigned=%d daysUsed=%d lastScheduled=%s%n",
	            queue.size(), updatedRows.size(), fallbackAssigned, daysUsed, (lastScheduled == null ? "<none>" : lastScheduled.format(outFmt)));
	}

//--- helper methods using WorkingTimeCalculator (paste these into the same class if not present) ---

private boolean isWorkingDay(java.time.LocalDate day) {
  if (day == null) return false;
  // working day = NOT (weekend OR holiday)
  return !WorkingTimeCalculator.isHolidayOrWeekend(day);
}

private java.time.LocalDate nextWorkingDay(java.time.LocalDate day) {
  if (day == null) return null;
  java.time.LocalDate d = day;
  while (!isWorkingDay(d)) d = d.plusDays(1);
  return d;
}

//Helper: addWorkingDays(start, workDays)
//Moves forward from 'start' by 'workDays' working days (skips weekends and holidays).
//If workDays == 0 returns start.
private java.time.LocalDate addWorkingDays(java.time.LocalDate start, int workDays) {
  if (start == null) return null;
  if (workDays <= 0) return start;
  java.time.LocalDate d = start;
  int added = 0;
  int safety = 0;
  while (added < workDays && safety++ < 365*10) {
      d = d.plusDays(1);
      if (isWorkingDay(d)) {
          added++;
      }
  }
  return d;
}

//computeAverageDailyCapacityM2_LastNWorkingDays: strict historical average
private double computeAverageDailyCapacityM2_LastNWorkingDays(int lastN) {
  int idxStatus = tableModel.findColumn("status");
  int idxM2 = tableModel.findColumn("m2");
  int idxEnd = tableModel.findColumn("endTime");
  int idxPred = tableModel.findColumn("predDatumIsporuke");
  int idxNar = tableModel.findColumn("datumNarudzbe");

  if (idxStatus < 0 || idxM2 < 0) return 0.0;

  java.time.LocalDate today = java.time.LocalDate.now();

  // find startInclusive by walking back calendar days until we've counted lastN working days
  int counted = 0;
  java.time.LocalDate cursor = today;
  java.time.LocalDate startInclusive = today;
  while (counted < lastN) {
      if (isWorkingDay(cursor)) counted++;
      if (counted >= lastN) { startInclusive = cursor; break; }
      cursor = cursor.minusDays(1);
  }

  double total = 0.0;
  for (int r = 0; r < tableModel.getRowCount(); r++) {
      String status = safeString(tableModel.getValueAt(r, idxStatus));
      if (!"izrađeno".equalsIgnoreCase(status) && !"izradjeno".equalsIgnoreCase(status)) continue;
      double m2 = parseDoubleOrZero(tableModel.getValueAt(r, idxM2));
      if (m2 <= 0.0) continue;
      java.time.LocalDate doneDay = null;
      if (idxEnd >= 0) doneDay = parseLocalDateFromDateTime(safeString(tableModel.getValueAt(r, idxEnd)));
      if (doneDay == null && idxPred >= 0) doneDay = parseLocalDate(safeString(tableModel.getValueAt(r, idxPred)),
              new java.time.format.DateTimeFormatter[]{
                      java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                      java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                      java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
              });
      if (doneDay == null && idxNar >= 0) doneDay = parseLocalDate(safeString(tableModel.getValueAt(r, idxNar)),
              new java.time.format.DateTimeFormatter[]{
                      java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                      java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                      java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
              });
      if (doneDay == null) continue;
      if ((!doneDay.isBefore(startInclusive)) && (!doneDay.isAfter(today))) {
          total += m2;
      }
  }
  return total / Math.max(1, lastN);
}

//Helpers: working day checks and parsers


private static String safeString(Object o) { return o == null ? "" : o.toString().trim(); }
private static int parseIntOrDefault(Object o, int def) { if (o == null) return def; try { return (int) Math.round(Double.parseDouble(o.toString().trim().replace(',', '.'))); } catch (Exception e) { return def; } }
private static double parseDoubleOrZero(Object o) { if (o == null) return 0.0; try { return Double.parseDouble(o.toString().trim().replace(',', '.')); } catch (Exception e) { return 0.0; } }
private static java.time.LocalDate parseLocalDate(String s, java.time.format.DateTimeFormatter[] fmts) { if (s == null || s.isBlank()) return null; for (java.time.format.DateTimeFormatter f : fmts) try { return java.time.LocalDate.parse(s.trim(), f); } catch (Exception ignored) {} return null; }
private static java.time.LocalDate parseLocalDateFromDateTime(String s) { if (s == null || s.isBlank()) return null; String t = s.trim(); java.time.format.DateTimeFormatter[] fmts = new java.time.format.DateTimeFormatter[]{ java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"), java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"), java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy") }; for (java.time.format.DateTimeFormatter f : fmts) try { java.time.temporal.TemporalAccessor ta = f.parse(t); if (ta.query(java.time.temporal.TemporalQueries.localDate()) != null) return java.time.LocalDate.from(ta); } catch (Exception ignored) {} return parseLocalDate(t, new java.time.format.DateTimeFormatter[]{ java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"), java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd") }); }
private static java.time.LocalDate parseLocalDateGeneric(Object o) { if (o == null) return null; if (o instanceof java.time.LocalDate) return (java.time.LocalDate) o; if (o instanceof java.sql.Date) return ((java.sql.Date) o).toLocalDate(); if (o instanceof java.util.Date) { java.util.Date d = (java.util.Date) o; return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate(); } if (o instanceof Number) { long v = ((Number) o).longValue(); try { return java.time.Instant.ofEpochMilli(v).atZone(java.time.ZoneId.systemDefault()).toLocalDate(); } catch (Exception ignored) {} } String s = o.toString().trim(); if (s.isEmpty()) return null; java.time.format.DateTimeFormatter[] fmts = new java.time.format.DateTimeFormatter[]{ java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"), java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"), java.time.format.DateTimeFormatter.ISO_LOCAL_DATE }; return parseLocalDate(s, fmts); }

//computeAverageDailyCapacityM2_LastNWorkingDays: strict historical average


//Helpers: working day checks + date parsing


 // Compute strict historical average: sum(m2 of rows with status "Izrađeno" whose done day is within the lastN working days window) / lastN
 // lastN is number of working days (e.g., 30). We find the start date by walking back calendar days until we've counted lastN working days.




//Compute strict historical average over lastN WORKING days (walk back calendar days until lastN working days counted,
//sum m2 for rows with status "Izrađeno" whose done day in [startInclusive .. today], divide by lastN).


//Helpers: working day checks and date parsing


 // Helper: compute average daily capacity m2 from last N days (calendar days).
 private double computeAverageDailyCapacityM2FromModel(int lastNDays) {
     int idxStatus = tableModel.findColumn("status");
     int idxM2 = tableModel.findColumn("m2");
     int idxEnd = tableModel.findColumn("endTime");
     int idxPred = tableModel.findColumn("predDatumIsporuke");
     int idxNar = tableModel.findColumn("datumNarudzbe");

     if (idxStatus < 0 || idxM2 < 0) return 0.0;

     LocalDate today = LocalDate.now();
     LocalDate start = today.minusDays(Math.max(1, lastNDays) - 1);

     double total = 0.0;
     for (int r = 0; r < tableModel.getRowCount(); r++) {
         String status = safeString(tableModel.getValueAt(r, idxStatus));
         if (!"izrađeno".equalsIgnoreCase(status) && !"izradjeno".equalsIgnoreCase(status)) continue;

         double m2 = parseDoubleOrZero(tableModel.getValueAt(r, idxM2));
         if (m2 <= 0.0) continue;

         LocalDate doneDay = null;
         if (idxEnd >= 0) {
             doneDay = parseLocalDateFromDateTime(safeString(tableModel.getValueAt(r, idxEnd)));
         }
         if (doneDay == null && idxPred >= 0) {
             doneDay = parseLocalDate(safeString(tableModel.getValueAt(r, idxPred)),
                     new java.time.format.DateTimeFormatter[] {
                             java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                             java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                             java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                     });
         }
         if (doneDay == null && idxNar >= 0) {
             doneDay = parseLocalDate(safeString(tableModel.getValueAt(r, idxNar)),
                     new java.time.format.DateTimeFormatter[] {
                             java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                             java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                             java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                     });
         }
         if (doneDay == null) continue;

         if (!doneDay.isBefore(start) && !doneDay.isAfter(today)) {
             total += m2;
         }
     }

     return total / Math.max(1, lastNDays);
 }

 // ===== utility helpers =====



 // showColumn helper: attempt to unhide a model column in the JTable view

 // Helper: compute average daily capacity m2 from last N days (calendar days).
 // Sums m2 of rows with status "Izrađeno" whose completion date (endTime/predDatumIsporuke/datumNarudzbe) falls in window,
 // then divides by lastNDays.


 // ===== utility helpers =====



 // Try parse string with provided formatters

 // Parse date/time strings like "dd.MM.yyyy HH:mm" -> return LocalDate part

 // Generic parser: handles LocalDate, java.util.Date, java.sql.Date, String

 // Computes average daily capacity (m2/day) from last N calendar days.
 // Logic: sum m2 for rows with status "Izrađeno" whose completion date falls within the window,
 // then divide by N (calendar days). Preferred completion date is endTime (date part),
 // fallback to predDatumIsporuke, fallback to datumNarudzbe.


 // ===== Helpers =====


 


 // Parses "dd.MM.yyyy HH:mm" or similar; returns only the date part.


//DIJAGNOSTIKA: ispisi zaglavlja modela i mapping na view
private void debugPrintTableModelInfo() {
  System.out.println("DEBUG: tableModel rowCount=" + tableModel.getRowCount() + " colCount=" + tableModel.getColumnCount());
  for (int i = 0; i < tableModel.getColumnCount(); i++) {
      String name = tableModel.getColumnName(i);
      int viewIdx = table.convertColumnIndexToView(i); // -1 ako nije u view (skrivena)
      System.out.printf("  modelIdx=%d name='%s' viewIdx=%d%n", i, name, viewIdx);
  }
  // provjeri prvi red (ako ima)
  if (tableModel.getRowCount() > 0) {
      int idxPlan = tableModel.findColumn("planDatumIsporuke");
      int idxPred = tableModel.findColumn("predDatumIsporuke");
      System.out.println(" sample row0 plan=" + (idxPlan>=0 ? tableModel.getValueAt(0, idxPlan) : "<no plan col>") +
                         " pred=" + (idxPred>=0 ? tableModel.getValueAt(0, idxPred) : "<no pred col>"));
  }
}

//Poništi skrivenost kolone (ako je bila hideColumns postavom širine 0)
private void showColumn(int modelIndex, int preferredWidth) {
 int viewIndex = table.convertColumnIndexToView(modelIndex);
 if (viewIndex >= 0) {
     TableColumn col = table.getColumnModel().getColumn(viewIndex);
     col.setMinWidth(15);
     col.setPreferredWidth(preferredWidth);
     col.setMaxWidth(Integer.MAX_VALUE);
     col.setResizable(true);
 } else {
     // Ponekad može biti -1 ako je model/view neusklađen — pokušaj pronaći po imenu
     String colName = tableModel.getColumnName(modelIndex);
     for (int v = 0; v < table.getColumnModel().getColumnCount(); v++) {
         if (table.getColumnModel().getColumn(v).getHeaderValue().toString().equals(colName)) {
             TableColumn col = table.getColumnModel().getColumn(v);
             col.setMinWidth(15);
             col.setPreferredWidth(preferredWidth);
             col.setMaxWidth(Integer.MAX_VALUE);
             col.setResizable(true);
             return;
         }
     }
     System.out.println("showColumn: view index -1 and not found by header: " + colName);
 }
}

    // --- Kraj klase ---
}