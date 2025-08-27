package ui;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import com.toedter.calendar.JDateChooser;
import db.DatabaseHelper;
import db.KomitentiDatabaseHelper;
import db.UserDatabaseHelper;
import ui.DateCellEditor;
import excel.ExcelExporter;
import excel.ExcelImporter;
import logic.DateUtils;
import logic.WorkingTimeCalculator;
import util.ActionLogger;

import java.awt.*;
import java.awt.event.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/**
 * Glavna UI klasa aplikacije.
 * Odgovorna je za:
 * - Kreiranje i prikaz grafičkog sučelja
 * - Upravljanje tablicom i modelom podataka
 * - Obradu korisničkih akcija (event handling)
 * - Rad s bazom podataka i Excel import/export
 * - Evidenciju povijesti promjena i otključavanja ćelija
 */
public class UI {

    // --- Polja klase ---

    /** Glavni JFrame prozor aplikacije */
    private JFrame frame;

    /** JTable za prikaz i uređivanje podataka */
    private JTable table;

    /** Model podataka povezan s JTable */
    private DefaultTableModel tableModel;

    /** Trenutno prijavljeni korisnik */
    private String prijavljeniKorisnik;

    /** Uloga prijavljenog korisnika (npr. Administrator ili korisnik) */
    private String ulogaKorisnika;

    /** Timer za praćenje neaktivnosti korisnika */
    private javax.swing.Timer inactivityTimer;

    /** Vrijeme neaktivnosti prije automatske odjave (1 minuta) */
    private final int INACTIVITY_DELAY = 60_000;

    /** Povijest promjena po retku (red -> lista događaja) */
    private java.util.Map<Integer, java.util.List<String>> povijestPromjena = new java.util.HashMap<>();

    /** Set redova koje je administrator ručno odmrznuo */
    private Set<Integer> odmrznutiModelRedovi;

    /** Sorter za sortiranje i filtriranje tablice */
    private TableRowSorter<DefaultTableModel> sorter;

    /** Popis djelatnika za dropdown izbornik */
    private final String[] djelatnici = {"", "Marko", "Ivana", "Petra", "Boris", "Ana"};
    
    private Map<String, String> komitentTPMap;


    /** Nazivi kolona u tablici */
    private final String[] columnNames = {
    	    "datumNarudzbe","predDatumIsporuke","komitentOpis",
    	    "nazivRobe","netoVrijednost","kom","status",
    	    "djelatnik","mm","m","tisucl","m2","startTime","endTime","duration",
    	    "trgovackiPredstavnik"
    	};


    /**
     * Konstruktor UI klase.
     * @param korisnik prijavljeni korisnik
     * @param uloga uloga korisnika (Administrator / Korisnik)
     */
    public UI(String korisnik, String uloga) {
        this.prijavljeniKorisnik = korisnik;
        this.ulogaKorisnika = uloga;
    }
    /**
     * Kreira i prikazuje glavni GUI aplikacije.
     * 
     * Funkcionalnosti:
     * - Inicijalizacija podataka i struktura
     * - Kreiranje glavnog JFrame-a
     * - Postavljanje tablice i njezinog modela
     * - Dodavanje panela s kontrolama (pretraga, odjava, gumbi akcija)
     * - Postavljanje listenera i timera za neaktivnost
     * - Učitavanje i spremanje korisničkih postavki tablice
     */
    
    private static final int KOMITENT_OPIS_COL = 2;
    private static final int TP_COL            = 15;

    void createAndShowGUI() {
    	// Inicijalizacija podataka
    	// Inicijalizacija skupa odmrznutih redova
    	// Inicijalizacija mape komitenta i trgovačkog predstavnika
    	        odmrznutiModelRedovi = new HashSet<>();
    	                // Povijest je već inicijalizirana pri deklaraciji
        initUnlockHistoryData();
// Kreiranje glavnog prozora
        
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
// Timer za automatsku odjavu nakon neaktivnosti
        inactivityTimer = new javax.swing.Timer(INACTIVITY_DELAY, e -> {
            UserDatabaseHelper.saveUserTableSettings(prijavljeniKorisnik, table);
            frame.dispose();
            new LoginUI();
        });
        inactivityTimer.setRepeats(false);
        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> resetInactivityTimer(),
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        inactivityTimer.start();
// Kreiranje modela tablice
        // Definiranje modela tablice s prilagođenim ponašanjem ćelija
        // Override metode za uređivanje ćelija i tipove podataka
        // Postavljanje modela na JTable i konfiguracija sortiranja
        // Postavljanje širina kolona i stilizacija tablice
        // Dodavanje tabova za podatke i statistiku
        // Učitavanje podataka iz baze i postavljanje korisničkih postavki
        // Kreiranje gornjeg panela s pretragom i odjavom
        // Postavljanje listenera za pretragu
        // Dodavanje gumba na donji panel s akcijama
        // Postavljanje dropdown izbornika i renderera za specifične kolone
        // Postavljanje listenera za model tablice
        // Postavljanje popup menija za administratore
        // Sakrivanje određenih kolona
        // Prikaz glavnog prozora
        // Inicijalizacija baze podataka i logiranje otvaranja
        // Povijest je već inicijalizirana pri deklaraciji
        // Inicijalizacija skupa odmrznutih redova
   
        
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int modelRow, int modelCol) {
                if (modelCol == KOMITENT_OPIS_COL) return false; // zabrana tipkanja

                int statusColModel = STATUS_COL_MODEL;
                Object statusVal = getValueAt(modelRow, statusColModel);
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
                if (modelCol == 14 || modelCol == 15) {
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
// Postavljanje širina kolona i stilizacija tablice
        int[] widths = {120,120,180,180,100,60,110,140,60,80,90,100,140,140,140};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        // Stilizacija tablice
        table.getTableHeader().setResizingAllowed(true);
        applyBrutalTableStyle();
        // --- TABOVI ---
        
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

        // --- GORNJI PANEL (Pretraga + Odjava) ---
        
        JPanel topPanel = new JPanel(new BorderLayout());
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField searchField = new JTextField(20);
        
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
            // Metoda za filtriranje redova na osnovi unosa u polje za pretragu
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

        // --- DROPDOWNOVI I RENDERERI ---
        
        setUpStatusDropdown();
        setUpDjelatnikDropdown();
        setUpDateColumns();

     // In createAndShowGUI()
        // Postavljanje dropdown izbornika za kolonu Komitent
        
     setUpKomitentDropdown();
     setUpPredstavnikDropdown();

        // --- DOUBLE-CLICK EDITORS AND OPENERS ---
        enforceDoubleClickEditors();
        enableDoubleClickOpeners();

        // --- LISTENERI I ADMIN POPUP ---
        
        setUpListeners();
        setUpAdminUnlockPopup();

        // --- SAKRIVANJE KOLONA ---
        hideColumns(8, 9, 10);
        // --- DONJI PANEL S GUMBIMA ---
        
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

        // --- ADMIN OPCIJA: Dodavanje korisnika ---
        // Prikaz gumba samo ako je korisnik administrator
        // 15 = kolona Trg. predstavnik
        // Postavljanje cellEditora za kolonu Trg. predstavnik
        // Koristi JComboBox s popisom djelatnika
        // Postavljanje renderera za bolji izgled ćelija
        // 15 = kolona Trg. predstavnik
        // Postavljanje cellEditora za kolonu Trg. predstavnik
        // Koristi JComboBox s popisom djelatnika
        // Postavljanje renderera za bolji izgled ćelija
        // 15 = kolona Trg. predstavnik
        // Postavljanje cellEditora za kolonu Trg. predstavnik
        // Koristi JComboBox s popisom djelatnika
        
        if ("Administrator".equals(ulogaKorisnika)) {
            JButton btnAddUser = new JButton("Dodaj korisnika");
            btnAddUser.addActionListener(e -> {
                new AddUserUI(null);
                ActionLogger.log(prijavljeniKorisnik, "Otvorio dodavanje korisnika");
            });
            bottom.add(btnAddUser);
        }

        // Dodavanje svih gumba na donji panel
        bottom.add(btnImport);
        bottom.add(btnExport);
        bottom.add(btnSaveDb);
        bottom.add(btnLoadDb);
        bottom.add(btnRefresh);
        bottom.add(btnDelete);

        // Primjena stila gumba
        applyBrutalButtonStyle(bottom);

        // Dodaj donji panel u frame
        frame.add(bottom, BorderLayout.SOUTH);

        // --- ZAVRŠNI DIO ---
        frame.setVisible(true);
        DatabaseHelper.initializeDatabase();
        ActionLogger.log(prijavljeniKorisnik, "Otvorio glavni prozor kao " + ulogaKorisnika);
    }



    /**
     * Logira promjenu statusa artikla.
     */
    private void logPromjenaStatusa(JTable table, int kolonaNazivRobe,
                                    String prijavljeniKorisnik, String noviStatus, String startTime) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) return;
        int modelRow = table.convertRowIndexToModel(selectedRow);
        ActionLogger.logTableAction(prijavljeniKorisnik,
                "Promjena statusa na '" + noviStatus + "'", tableModel, modelRow);
    }

    /**
     * Ponovno računa sve redove u tablici.
     */
    private void recomputeAllRows() {
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            recomputeRow(r);
            recomputeDuration(r);
        }
        tableModel.fireTableDataChanged();
    }

    /**
     * Računa trajanje između start i end vremena za jedan red.
     */
    // 14 = duration
    // 12 = startTime
    // 13 = endTime
    // Računa trajanje između start i end vremena za jedan red.
    private void recomputeDuration(int row) {
        String start = (String) tableModel.getValueAt(row, 12);
        String end   = (String) tableModel.getValueAt(row, 13);
        if (start == null || end == null || start.isBlank() || end.isBlank()) {
            tableModel.setValueAt("", row, 14);
            return;
        }
      
    // Pronalazak indeksa kolone "duration"
        
        int columnIndex = -1;
        for (int i = 0; i < columnNames.length; i++) {
            if ("duration".equals(columnNames[i])) {
                columnIndex = i;
                break;
            }
        }
         // Ako kolona nije pronađena, izlazimo iz metode
           String duration = WorkingTimeCalculator.calculateWorkingDuration(start, end);
           tableModel.setValueAt(duration, row, columnIndex);
           
        
    }

    /**
     * Dodaje listener na model tablice.
     */
    // Postavlja automatske izračune i ažuriranja ovisno o promjenama ćelija.
    // Također ažurira trgovačkog predstavnika na osnovi komitenta.
    // 0 = kolona KomitentOpis
    // 3 = kolona Naziv robe
    // 5 = kolona Kom
    // 8 = kolona mm
    //
    // 9 = kolona m
    // 10 = kolona tisucl
    // 11 = kolona m2
    // 12 = kolona startTime
    // 13 = kolona endTime
    // 14 = kolona duration
    // 15 = kolona Trg. predstavnik
    private void setUpListeners() {
        tableModel.addTableModelListener(e -> {
            if (e.getType() != TableModelEvent.UPDATE) return;
            int row = e.getFirstRow();
            int col = e.getColumn();

            // Ako se promijenio komitent (npr. kolona 0)
            if (col == 0) {
                Object komitentVal = tableModel.getValueAt(row, 0);
                if (komitentVal != null) {
                    String komitent = komitentVal.toString();
                    String predstavnik = komitentTPMap.getOrDefault(komitent, "");
                    tableModel.setValueAt(predstavnik, row, 15); // 15 = kolona Trg. predstavnik
                }
            }

            if (col == 3 || col == 5) {
                recomputeRow(row);
                tableModel.fireTableRowsUpdated(row, row);
            }
            if (col == 12 || col == 13) {
                recomputeDuration(row);
                tableModel.fireTableCellUpdated(row, 14);
            }
        });
    }


    /**
     * Parsira vrijednosti mm i m iz naziva robe.
     */
    
    // Naziv robe sadrži dimenzije u formatu "mm/m", npr. "50/2.5"
    // Vraća niz s dvije vrijednosti: [mm, m]
    // Ako nije moguće parsirati, vraća [0, 0]
    // Primjer: "50/2.5" -> [50.0, 2.5]
    // Primjer: "100,5 / 3,2" -> [100.5, 3.2]
    // Ako je naziv null ili neispravan, vraća [0, 0]
    private double[] parseMmM(String naz) {
        if (naz == null) return new double[]{0, 0};
        // Regex za pronalaženje formata "mm/m"
        // Podržava decimalne točke i zareze
        // Primjer: "50/2.5", "100,5 / 3,2"
        // Hvata dvije grupe brojeva
        // Prva grupa je mm, druga je m
        // Omogućava razmake oko kosa crte
        // Vraća null ako nije pronađeno podudaranje
        // Ako je parsiranje neuspješno, hvata iznimke i vraća [0, 0]
        // Vraća niz s dvije vrijednosti: [mm, m]
        Pattern p = Pattern.compile("(\\d+(?:[\\.,]\\d+)?)\\s*/\\s*(\\d+(?:[\\.,]\\d+)?)");
        Matcher m = p.matcher(naz);
        if (m.find()) {
            try {
                double mm = Double.parseDouble(m.group(1).replace(',', '.'));
                double mVal = Double.parseDouble(m.group(2).replace(',', '.'));
                return new double[]{mm, mVal};
            } catch (Exception ex) {
                // Ignorira pogreške parsiranja
            }
        }
        return new double[]{0, 0};
    }

    /**
     * Ponovno računa podatke za jedan redak tablice (mm, m, tisucl, m2).
     */
    // 3 = kolona Naziv robe
    // 8 = kolona mm
    // 9 = kolona m
    // 10 = kolona tisucl
    // 11 = kolona m2
    
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
     * Sakriva specificirane kolone tablice.
     */
    // Prima indekse kolona prema modelu tablice.
    // Postavlja širinu kolona na 0 i onemogućava njihovo mijenjanje veličine.
    // Indeksi se konvertiraju iz modela u prikaz (view) prije primjene.
    // Indeksi kolona su prema modelu tablice.
    // Primjer: hideColumns(8, 9, 10) sakriva kolone mm, m, tisucl.
    // 8 = kolona mm
    // 9 = kolona m
    // 10 = kolona tisucl
    
    private void hideColumns(int... modelIndexes) {
        for (int mi : modelIndexes) {
            int vi = table.convertColumnIndexToView(mi);
            if (vi >= 0) {
                TableColumn col = table.getColumnModel().getColumn(vi);
                col.setMinWidth(0);
                col.setMaxWidth(0);
                col.setPreferredWidth(0);
                col.setResizable(false);
            }
        }
    }

 // Konstante — prilagodi indekse svom modelu
    // 6 = kolona status
    // 12 = kolona startTime
    // 13 = kolona endTime
    // 14 = kolona duration
    // 15 = kolona Trg. predstavnik
    // Indeksi su prema modelu tablice (ne prema prikazu)
  
    private static final int STATUS_COL_MODEL = 6;
    private static final int START_TIME_COL   = 12;
    private static final int END_TIME_COL     = 13;

    private void setUpAdminUnlockPopup() {
        if (!"Administrator".equalsIgnoreCase(ulogaKorisnika)) return;

        JPopupMenu adminPopup = new JPopupMenu();

        // --- Otključavanje ---
        
        JMenuItem unlockItem = new JMenuItem("Otključaj ćeliju");
        unlockItem.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0 || viewRow >= table.getRowCount()) {
                System.out.println("[DEBUG unlockItem] Nema odabranog reda ili izvan raspona.");
                return;
            }
// Dohvati status iz modela
            // 6 = kolona status
            // Konvertiraj viewRow u modelRow
            // Provjeri je li status "Izrađeno"
            // Ako nije, prikaži poruku i izađi
            // Ako jest, pitaj za potvrdu otključavanja
            // Ako korisnik odustane, izađi
            // Pitaj za komentar (opcionalno)
            // Ako je red već otključan, ne dodaj ponovo
            // Postavi status na prazan string ("")
            // Osvježi red u tablici
            // Zapiši povijest promjene s vremenom, korisnikom i komentarom
            // Očisti selekciju i fokus
            // Debug ispis
            
            int modelRow = table.convertRowIndexToModel(viewRow);
            TableModel tm = table.getModel();
            Object statusVal = tm.getValueAt(modelRow, STATUS_COL_MODEL);
            String status = statusVal == null ? "" : statusVal.toString();

            System.out.printf("[DEBUG unlockItem] viewRow=%d, modelRow=%d, status='%s'%n",
                    viewRow, modelRow, status);

            if (!"Izrađeno".equals(status)) {
                System.out.println("[DEBUG unlockItem] Red nije u statusu 'Izrađeno'.");
                return;
            }

            int ans = JOptionPane.showConfirmDialog(
                frame,
                "Želite li otključati ćeliju?",
                "Otključavanje ćelije",
                JOptionPane.YES_NO_OPTION
            );
            if (ans != JOptionPane.YES_OPTION) {
                System.out.println("[DEBUG unlockItem] Korisnik odustao.");
                return;
            }

            String komentar = JOptionPane.showInputDialog(
                frame,
                "Unesite kratki komentar (opcionalno):",
                ""
            );
            if (komentar == null) komentar = "";

            if (!odmrznutiModelRedovi.contains(modelRow)) {
                odmrznutiModelRedovi.add(modelRow);
                System.out.printf("[DEBUG unlockItem] Dodan modelRow=%d u otključane.%n", modelRow);
            }

            // --- NOVO: Postavi status na prazan string ("")
            tm.setValueAt("", modelRow, STATUS_COL_MODEL);
            System.out.printf("[DEBUG unlockItem] Status za red %d postavljen na prazan string.%n", modelRow);

            // Osvježi red
            if (tm instanceof AbstractTableModel atm) {
                atm.fireTableRowsUpdated(modelRow, modelRow);
            } else {
                table.repaint();
            }

            // Zapiši povijest
            String zapis = String.format(
                "%s | %s | %s",
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()),
                prijavljeniKorisnik,
                komentar.isBlank() ? "(bez komentara)" : komentar
            );
            povijestPromjena.computeIfAbsent(modelRow, k -> new ArrayList<>()).add(zapis);
            System.out.printf("[DEBUG unlockItem] Povijest za red %d: %s%n",
                    modelRow, povijestPromjena.get(modelRow));

            table.clearSelection();
            KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
        });
        adminPopup.add(unlockItem);

        // --- Povijest ---
        // Prikazuje povijest promjena za odabrani red
        // Ako nema povijesti, prikazuje odgovarajuću poruku
        // Prikazuje povijest u JTextArea unutar JScrollPane
        // Postavlja veličinu prozora povijesti
        //  Prikazuje dijalog s poviješću
        //  Ako nije odabran red, izlazi iz metode
        //  Dohvaća povijest iz mape prema modelRow
        //  Ako nema povijesti, prikazuje poruku
        //  Inače, prikazuje povijest u tekstualnom području
        //  Stilizira tekstualno područje za bolju čitljivost
        
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

        // --- Mouse listener ---
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { showPopup(e); }

            @Override
            public void mouseReleased(MouseEvent e) { showPopup(e); }

            // Prikazuje popup meni ako je kliknut desni klik na kolonu "status"
            // 6 = kolona status
            // Provjerava je li kliknut desni klik (popup trigger)
            // Dohvaća red i kolonu na osnovi točke klika
            // Ako je kliknut na kolonu "status", prikazuje popup meni
            // Postavlja selekciju na kliknuti red
            // Prikazuje popup meni na lokaciji klika
        
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
     * Postavlja dropdown izbornik za kolonu "status".
     * Omogućuje automatsko postavljanje vremena i korisnika.
     */
    // 6 = kolona status
    // 7 = kolona djelatnik
    // 12 = kolona startTime
    // 13 = kolona endTime
    // 14 = kolona duration
    // Postavlja JComboBox s opcijama statusa
    // Postavlja DefaultCellEditor s prilagođenim ponašanjem
    // Pri promjeni statusa, automatski postavlja vrijeme i korisnika
    // Logira promjenu statusa
    // Ako je odabran isti status, ne radi ništa
    // Ako je odabran "Izrađeno", traži potvrdu
    // Ako korisnik odustane, vraća originalnu vrijednost
    // Ako je odabran prazan status, briše status
    // Postavlja editor na kolonu "status"
    private void setUpStatusDropdown() {
        int viewIdx = table.convertColumnIndexToView(6);
        if (viewIdx < 0) return;

        TableColumn col = table.getColumnModel().getColumn(viewIdx);
        String[] opcije = {"", "U izradi", "Izrađeno"};

        JComboBox<String> combo = new JComboBox<>(opcije);

        DefaultCellEditor editor = new DefaultCellEditor(combo) {
            private Object originalValue;

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value,
                                                         boolean isSelected, int row, int column) {
                originalValue = value; // zapamti trenutno stanje
                return super.getTableCellEditorComponent(table, value, isSelected, row, column);
            }

            @Override
            public Object getCellEditorValue() {
                String sel = (String) super.getCellEditorValue();
//                System.out.printf("[DEBUG StatusEditor] Odabrano: '%s', Original: '%s'%n", sel, originalValue);	
                int modelRow = table.convertRowIndexToModel(table.getEditingRow());
//                System.out.printf("[DEBUG StatusEditor] modelRow=%d%n", modelRow);
                // Ako je isti status, ništa ne radi
                // Ovo sprječava višestruke logove i ažuriranja
                // Ali pazi na null vrijednosti
                // Ako su oba null ili oba prazna, tretiraj kao isto
                // Inače, ako su različiti, nastavi
                // Ovo je važno jer JComboBox može vratiti "" umjesto null
                // Dakle, tretiraj "" i null kao ekvivalentne
                // Ovo također sprječava probleme ako korisnik odabere isti status
                if ("U izradi".equals(sel)) {
                    tableModel.setValueAt("U izradi", modelRow, 6);
                    tableModel.setValueAt(DateUtils.formatWithoutSeconds(LocalDateTime.now()), modelRow, 12);
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
                    	// Postavi end time na sada ako nije već postavljeno
                        tableModel.setValueAt("Izrađeno", modelRow, 6);
                        // Ako endTime već nije postavljeno, postavi ga
                        tableModel.setValueAt(DateUtils.formatWithoutSeconds(LocalDateTime.now()), modelRow, 13);
                        // Ako startTime nije postavljeno, postavi ga
                        recomputeDuration(modelRow);
                        // Ako djelatnik nije postavljen, postavi ga
                        ActionLogger.logTableAction(prijavljeniKorisnik, "Status 'Izrađeno'", tableModel, modelRow);
                        return sel;
                    } else {
                        return originalValue == null ? "" : originalValue;
                    }

                } else {
                    // prazno
                    tableModel.setValueAt("", modelRow, 6);
                    return "";
                }
            }
        };

        col.setCellEditor(editor);
    }





    
    
    private void setUpKomitentDropdown() {
        int viewIdx = table.convertColumnIndexToView(2); // kolona komitentOpis
        if (viewIdx < 0) return; 

        JComboBox<String> combo = new JComboBox<>(KomitentiDatabaseHelper.loadAllKomitentNames().toArray(new String[0]));
        combo.setEditable(true); 

        combo.addActionListener(e -> {
            Object selObj = combo.getSelectedItem();
            if (selObj != null) {
                String noviKomitent = selObj.toString().trim();
                int row = table.getSelectedRow();
                if (row >= 0 && !noviKomitent.isEmpty()) {
                    int modelRow = table.convertRowIndexToModel(row);

                    String trenutniTP = (String) tableModel.getValueAt(modelRow, 15);
                    if (trenutniTP == null || trenutniTP.isBlank()) {
                        String unesenTP = JOptionPane.showInputDialog(frame,
                            "Unesi trgovačkog predstavnika za: " + noviKomitent, "");
                        if (unesenTP == null) unesenTP = "";
                        trenutniTP = unesenTP.trim();
                    }

                    tableModel.setValueAt(noviKomitent, modelRow, 2);
                    tableModel.setValueAt(trenutniTP, modelRow, 15);

                    KomitentiDatabaseHelper.insertIfNotExists(noviKomitent, trenutniTP);
                    komitentTPMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();

                    combo.removeAllItems();
                    for (String k : KomitentiDatabaseHelper.loadAllKomitentNames()) {
                        combo.addItem(k);
                    }
                    setUpPredstavnikDropdown();
                }
            }
        });

        if (viewIdx >= 0) { table.getColumnModel().getColumn(viewIdx).setCellRenderer(new DefaultTableCellRenderer() { @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) { super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column); setText(value == null ? "" : value.toString()); return this; } }); }
        
        table.getColumnModel().getColumn(viewIdx).setCellEditor(new DefaultCellEditor(combo));
    }







    private void setUpPredstavnikDropdown() {
    	
    	int viewIdx = table.convertColumnIndexToView(15); // "trgovackiPredstavnik" column if (viewIdx < 0) return;

    JComboBox<String> combo = new JComboBox<>();
    combo.setEditable(true);

    // Initial load
    for (String p : KomitentiDatabaseHelper.loadAllPredstavnici()) {
        combo.addItem(p);
    }

    combo.addPopupMenuListener(new PopupMenuListener() {
        @Override
        public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            combo.removeAllItems();
            for (String p : KomitentiDatabaseHelper.loadAllPredstavnici()) {
                combo.addItem(p);
            }
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

                String trenutniKomitent = (String) tableModel.getValueAt(modelRow, 2);
                if (trenutniKomitent == null || trenutniKomitent.isBlank()) {
                    String unesenKomitent = JOptionPane.showInputDialog(frame,
                        "Unesi komitenta za predstavnika: " + noviPredstavnik, "");
                    if (unesenKomitent == null) unesenKomitent = "";
                    trenutniKomitent = unesenKomitent.trim();
                }

                tableModel.setValueAt(trenutniKomitent, modelRow, 2);
                tableModel.setValueAt(noviPredstavnik, modelRow, 15);

                KomitentiDatabaseHelper.insertIfNotExists(trenutniKomitent, noviPredstavnik);
                komitentTPMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
            }
        }
    });

    table.getColumnModel().getColumn(viewIdx).setCellEditor(new DefaultCellEditor(combo));
    }

    





    /**
     * Postavlja dropdown izbornik za kolonu "djelatnik".
     */
    private void setUpDjelatnikDropdown() {
        int viewIdx = table.convertColumnIndexToView(7);
        if (viewIdx < 0) return;
        TableColumn col = table.getColumnModel().getColumn(viewIdx);
        JComboBox<String> combo = new JComboBox<>(djelatnici);
        col.setCellEditor(new DefaultCellEditor(combo));
    }
    
    
    private void enableKomitentSearchPopup() {
        int komitentColView = table.convertColumnIndexToView(2);
        if (komitentColView < 0) return;

        table.getColumnModel().getColumn(komitentColView).setCellEditor(null);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedColumn() == komitentColView) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow < 0) return;
                    int modelRow = table.convertRowIndexToModel(viewRow);

                    JDialog dialog = new JDialog(frame, "Odaberi komitenta", true);
                    dialog.setSize(400, 300);
                    dialog.setLocationRelativeTo(frame);
                    dialog.setLayout(new BorderLayout(5, 5));

                    JTextField searchField = new JTextField();
                    DefaultListModel<String> listModel = new DefaultListModel<>();
                    JList<String> list = new JList<>(listModel);
                    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

                    java.util.List<String> komitenti = KomitentiDatabaseHelper.loadAllKomitentNames();
                    komitenti.forEach(listModel::addElement);

                    searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                        public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                        public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                        public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                        private void filter() {
                            String txt = searchField.getText().toLowerCase();
                            listModel.clear();
                            komitenti.stream()
                                    .filter(k -> k.toLowerCase().contains(txt))
                                    .forEach(listModel::addElement);
                        }
                    });

                    Runnable selectAction = () -> {
                        String val = list.getSelectedValue();
                        if (val != null) {
                            tableModel.setValueAt(val, modelRow, 2);
                            String tp = KomitentiDatabaseHelper.loadKomitentPredstavnikMap().getOrDefault(val, "");
                            if (tp.isBlank()) {
                                String unesenTP = JOptionPane.showInputDialog(frame,
                                    "Unesi trgovačkog predstavnika za: " + val, "");
                                if (unesenTP == null) unesenTP = "";
                                tp = unesenTP.trim();
                                KomitentiDatabaseHelper.insertIfNotExists(val, tp);
                            }
                            tableModel.setValueAt(tp, modelRow, 15);
                            komitentTPMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
                            dialog.dispose();
                        }
                    };

                    list.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if (e.getClickCount() == 2) selectAction.run();
                        }
                    });

                    JButton btnSelect = new JButton("Odaberi");
                    btnSelect.addActionListener(ev -> selectAction.run());

                    dialog.add(searchField, BorderLayout.NORTH);
                    dialog.add(new JScrollPane(list), BorderLayout.CENTER);
                    dialog.add(btnSelect, BorderLayout.SOUTH);

                    dialog.setVisible(true);
                }
            }
        });
    }









    /**
     * Postavlja editor i renderer za kolone s datumom i vremenom.
     */
    
    
    private void setUpDateColumns() {
        int[] dateCols = {0, 1};
        for (int c : dateCols) {
            int v = table.convertColumnIndexToView(c);
            if (v < 0) continue;
            TableColumn col = table.getColumnModel().getColumn(v);
            col.setCellEditor(new CalendarTimeCellEditor());
            col.setCellRenderer(new CalendarTimeCellRenderer());
        }
        
        // Set DateCellEditor for start/end columns (model indexes 12 and 13) with "HH:mm" pattern
        int startView = table.convertColumnIndexToView(12);
        if (startView >= 0) table.getColumnModel().getColumn(startView).setCellEditor(new DateCellEditor("HH:mm"));
        int endView = table.convertColumnIndexToView(13);
        if (endView >= 0) table.getColumnModel().getColumn(endView).setCellEditor(new DateCellEditor("HH:mm"));
    }

    /**
     * Custom TableCellEditor za datum+vrijeme.
     */
    
    private static class CalendarTimeCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JDateChooser dateChooser;
        private final JSpinner timeSpinner;
        private final JPanel panel;

        CalendarTimeCellEditor() {
            dateChooser = new JDateChooser();
            dateChooser.setDateFormatString("dd.MM.yyyy");
            SpinnerDateModel model = new SpinnerDateModel();
            timeSpinner = new JSpinner(model);
            timeSpinner.setEditor(new JSpinner.DateEditor(timeSpinner, "HH:mm"));
            panel = new JPanel(new BorderLayout(4, 0));
            panel.add(dateChooser, BorderLayout.CENTER);
            panel.add(timeSpinner, BorderLayout.EAST);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            LocalDateTime dt = (value instanceof String) ? DateUtils.parse((String) value) : LocalDateTime.now();
            Date date = Date.from((dt != null ? dt : LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant());
            dateChooser.setDate(date);
            timeSpinner.setValue(date);
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            Date d = dateChooser.getDate();
            Date t = (Date) timeSpinner.getValue();
            LocalDateTime dt = LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault())
                    .withHour(t.getHours()).withMinute(t.getMinutes());
            return DateUtils.formatWithoutSeconds(dt);
        }
    }

    /**
     * Custom renderer za prikaz datuma+vremena.
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

    /**
     * Primjenjuje vizualni stil na JTable.
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
    
	private void setUpStatusRenderer() {
		int viewIdx = table.convertColumnIndexToView(STATUS_COL_MODEL);
		if (viewIdx < 0)
			return;

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
				if (sel)
					comp.setBackground(new Color(180, 205, 255));
				return comp;
			}
		});
	}
    





    /**
     * Primjenjuje vizualni stil na gumbe unutar panela.
     */
    private void applyBrutalButtonStyle(JPanel panel) {
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JButton btn) {
                btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
                btn.setBackground(new Color(40, 80, 180));
                btn.setForeground(Color.WHITE);
                btn.setFocusPainted(false);
                btn.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
                btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

                btn.addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(60, 110, 220)); }
                    @Override public void mouseExited(MouseEvent e) { btn.setBackground(new Color(40, 80, 180)); }
                });
            }
        }
    }

    /**
     * Inicijalizira interne strukture za otključavanje i povijest promjena.
     */
    private void initUnlockHistoryData() {
        odmrznutiModelRedovi = new HashSet<>();
        povijestPromjena = new HashMap<>();
    }
    
    

    /**
     * Resetira timer neaktivnosti.
     */
    
    private void resetInactivityTimer() {
        if (inactivityTimer != null) inactivityTimer.restart();
    }

    /**
     * Enforces double-click for table cell editors by setting clickCountToStart to 2.
     */
    private void enforceDoubleClickEditors() {
        // Set double-click for default editors (String, Integer, Double, Boolean)
        TableCellEditor defaultEditor = table.getDefaultEditor(String.class);
        if (defaultEditor instanceof DefaultCellEditor) {
            ((DefaultCellEditor) defaultEditor).setClickCountToStart(2);
        }
        
        defaultEditor = table.getDefaultEditor(Integer.class);
        if (defaultEditor instanceof DefaultCellEditor) {
            ((DefaultCellEditor) defaultEditor).setClickCountToStart(2);
        }
        
        defaultEditor = table.getDefaultEditor(Double.class);
        if (defaultEditor instanceof DefaultCellEditor) {
            ((DefaultCellEditor) defaultEditor).setClickCountToStart(2);
        }
        
        defaultEditor = table.getDefaultEditor(Boolean.class);
        if (defaultEditor instanceof DefaultCellEditor) {
            ((DefaultCellEditor) defaultEditor).setClickCountToStart(2);
        }
        
        // Set double-click for specific column editors (djelatnik column view index 7, start/end columns 12/13)
        int djelatnikView = table.convertColumnIndexToView(7);
        if (djelatnikView >= 0) {
            TableCellEditor editor = table.getColumnModel().getColumn(djelatnikView).getCellEditor();
            if (editor instanceof DefaultCellEditor) {
                ((DefaultCellEditor) editor).setClickCountToStart(2);
            }
        }
        
        int startView = table.convertColumnIndexToView(12);
        if (startView >= 0) {
            TableCellEditor editor = table.getColumnModel().getColumn(startView).getCellEditor();
            if (editor instanceof DefaultCellEditor) {
                ((DefaultCellEditor) editor).setClickCountToStart(2);
            }
        }
        
        int endView = table.convertColumnIndexToView(13);
        if (endView >= 0) {
            TableCellEditor editor = table.getColumnModel().getColumn(endView).getCellEditor();
            if (editor instanceof DefaultCellEditor) {
                ((DefaultCellEditor) editor).setClickCountToStart(2);
            }
        }
    }

    /**
     * Enables double-click openers for table cells.
     */
    private void enableDoubleClickOpeners() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    // Ignore single-click
                    return;
                }
                
                if (e.getClickCount() == 2) {
                    int viewRow = table.getSelectedRow();
                    int viewCol = table.getSelectedColumn();
                    if (viewRow < 0 || viewCol < 0) return;
                    
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    int modelCol = table.convertColumnIndexToModel(viewCol);
                    
                    System.out.println("Double-click detected: row=" + viewRow + ", col=" + viewCol + ", modelCol=" + modelCol);
                    
                    if (modelCol == 2) {
                        // Open komitent dialog (reuse existing logic from enableKomitentSearchPopup)
                        JDialog dialog = new JDialog(frame, "Odaberi komitenta", true);
                        dialog.setSize(400, 300);
                        dialog.setLocationRelativeTo(frame);
                        dialog.setLayout(new BorderLayout(5, 5));

                        JTextField searchField = new JTextField();
                        DefaultListModel<String> listModel = new DefaultListModel<>();
                        JList<String> list = new JList<>(listModel);
                        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

                        java.util.List<String> komitenti = KomitentiDatabaseHelper.loadAllKomitentNames();
                        komitenti.forEach(listModel::addElement);

                        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                            public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                            public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                            public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                            private void filter() {
                                String txt = searchField.getText().toLowerCase();
                                listModel.clear();
                                komitenti.stream()
                                        .filter(k -> k.toLowerCase().contains(txt))
                                        .forEach(listModel::addElement);
                            }
                        });

                        Runnable selectAction = () -> {
                            String val = list.getSelectedValue();
                            if (val != null) {
                                tableModel.setValueAt(val, modelRow, 2);
                                String tp = KomitentiDatabaseHelper.loadKomitentPredstavnikMap().getOrDefault(val, "");
                                if (tp.isBlank()) {
                                    String unesenTP = JOptionPane.showInputDialog(frame,
                                        "Unesi trgovačkog predstavnika za: " + val, "");
                                    if (unesenTP == null) unesenTP = "";
                                    tp = unesenTP.trim();
                                    KomitentiDatabaseHelper.insertIfNotExists(val, tp);
                                }
                                tableModel.setValueAt(tp, modelRow, 15);
                                komitentTPMap = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();
                                dialog.dispose();
                            }
                        };

                        list.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent e) {
                                if (e.getClickCount() == 2) selectAction.run();
                            }
                        });

                        JButton btnSelect = new JButton("Odaberi");
                        btnSelect.addActionListener(ev -> selectAction.run());

                        dialog.add(searchField, BorderLayout.NORTH);
                        dialog.add(new JScrollPane(list), BorderLayout.CENTER);
                        dialog.add(btnSelect, BorderLayout.SOUTH);

                        dialog.setVisible(true);
                    } else {
                        // Call table.editCellAt for other columns
                        table.editCellAt(viewRow, viewCol);
                        TableCellEditor editor = table.getCellEditor();
                        if (editor != null) {
                            Component editorComponent = table.getEditorComponent();
                            if (editorComponent instanceof JComboBox) {
                                ((JComboBox<?>) editorComponent).showPopup();
                            } else if (editorComponent != null) {
                                editorComponent.requestFocus();
                            }
                        }
                    }
                }
            }
        });
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
    
    
}
