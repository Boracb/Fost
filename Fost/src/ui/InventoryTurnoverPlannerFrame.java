package ui;

import logic.InventoryTurnoverPlanner;
import logic.InventoryTurnoverPlanner.Params;
import logic.InventoryTurnoverPlanner.Plan;
import model.AggregatedConsumption;
import model.SalesRow;
import model.StockRow;
import excel.ExcelProductionImporter;
import excel.ExcelSalesImporter;
import excel.ExcelStockImporter;
import excel.SalesAggregator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.toedter.calendar.JDateChooser;

public class InventoryTurnoverPlannerFrame extends JFrame {

    // Ulazi
    private final JTextField tfNazivArtikla = new JTextField(20);
    private final JTextField tfGodisnjaPotrosnja = new JTextField(10);
    private final JTextField tfStanjeZaliha = new JTextField(10);
    private final JTextField tfRokIsporukeDana = new JTextField(10);
    private final JTextField tfMinimalnaZaliha = new JTextField(10);
    private final JTextField tfMOQ = new JTextField(10);
    private final JTextField tfKoefObrtaja = new JTextField(10);
    private final JTextField tfRadnihDana = new JTextField(10);

    // Izlazi
    private final JLabel lbDnevnaPotrosnja = new JLabel("-");
    private final JLabel lbROP = new JLabel("-");
    private final JLabel lbCiljnaCiklusna = new JLabel("-");
    private final JLabel lbCiljnaMax = new JLabel("-");
    private final JLabel lbNarucitiOdmah = new JLabel("-");
    private final JLabel lbDanaDoNabave = new JLabel("-");
    private final JLabel lbPreporucenaNarudzba = new JLabel("-");
    private final JLabel lbDatumNarudzbe = new JLabel("-");
    private final JLabel lbOcekivaniDolazak = new JLabel("-");
    private final JLabel lbProcijenjeniK = new JLabel("-");

    // Stanje skladišta
    private final StockTableModel stockModel = new StockTableModel();
    private final JTable stockTable = new JTable(stockModel);
    private final JLabel lbImportedStockFile = new JLabel("Nije učitano");

    // Prodaja + Proizvodnja (agregati)
    private final SalesAggTableModel aggModel = new SalesAggTableModel();
    private final JTable aggTable = new JTable(aggModel);
    private final JLabel lbImportedSalesFile = new JLabel("Nije učitano");
    private final JLabel lbImportedProdFile = new JLabel("Nije učitano");
    private final JCheckBox cbGroupByCode = new JCheckBox("Grupiraj po šifri (preporučeno)", true);
    private final JDateChooser dcFrom = new JDateChooser();
    private final JDateChooser dcTo = new JDateChooser();
    private List<SalesRow> salesRows = new ArrayList<>();
    private List<SalesRow> productionRows = new ArrayList<>();

    private final DecimalFormat df = new DecimalFormat("#,##0.##");
    private final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public InventoryTurnoverPlannerFrame() {
        super("Planer nabave (obrtaji) — stanje + prodaja + proizvodnja");

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1150, 820));
        setLocationByPlatform(true);

        setContentPane(buildContent());
        setupActions();
        prefillDefaults();
    }

    private JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel(new GridLayout(3, 1, 8, 8));
        top.add(buildImportStockPanel());
        top.add(buildImportSalesPanel());
        top.add(buildImportProductionPanel());
        root.add(top, BorderLayout.NORTH);

        JPanel left = buildInputsPanel();
        JPanel right = buildOutputsPanel();
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.5);
        root.add(split, BorderLayout.CENTER);

        root.add(buildButtonsPanel(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildImportStockPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createTitledBorder("Stanje skladišta (Excel uvoz)"));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btImport = new JButton("Uvezi stanje (Excel)");
        btImport.addActionListener(e -> onImportStockExcel());
        top.add(btImport);
        top.add(new JLabel("Datoteka:"));
        top.add(lbImportedStockFile);

        JLabel hint = new JLabel("Stupci: Šifra | Naziv artikla | Jed.mj. | Količina | Nabavna cijena | Nabavna vrijednost");
        hint.setForeground(new Color(80, 80, 80));
        top.add(Box.createHorizontalStrut(12));
        top.add(hint);
        p.add(top, BorderLayout.NORTH);

        stockTable.setFillsViewportHeight(true);
        stockTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stockTable.getSelectionModel().addListSelectionListener(this::onStockSelectionChanged);
        JScrollPane sp = new JScrollPane(stockTable);
        sp.setPreferredSize(new Dimension(200, 180));
        p.add(sp, BorderLayout.CENTER);

        return p;
    }

    private JPanel buildImportSalesPanel() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createTitledBorder("Promet/potrošnja — prodaja + proizvodnja (agregacija)"));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton btSales = new JButton("Uvezi prodaju (Excel)");
        btSales.addActionListener(e -> onImportSalesExcel());
        top.add(btSales);
        top.add(new JLabel("Prodaja:"));
        top.add(lbImportedSalesFile);

        top.add(Box.createHorizontalStrut(16));
        top.add(new JLabel("Period: od"));
        top.add(dcFrom);
        top.add(new JLabel("do"));
        top.add(dcTo);

        top.add(cbGroupByCode);

        JButton btRefresh = new JButton("Osvježi agregate");
        btRefresh.addActionListener(e -> refreshAggregation());
        top.add(btRefresh);

        JLabel hint = new JLabel("Ključ: Datum | Šifra | Naziv | Količina. Klik na red -> popunjava Naziv + Godišnju potrošnju (i Stanje po šifri).");
        hint.setForeground(new Color(80, 80, 80));
        top.add(Box.createHorizontalStrut(8));
        top.add(hint);

        p.add(top, BorderLayout.NORTH);

        aggTable.setFillsViewportHeight(true);
        aggTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        aggTable.getSelectionModel().addListSelectionListener(this::onAggSelectionChanged);
        JScrollPane sp = new JScrollPane(aggTable);
        sp.setPreferredSize(new Dimension(200, 240));
        p.add(sp, BorderLayout.CENTER);

        return p;
    }

    private JPanel buildImportProductionPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(BorderFactory.createTitledBorder("Proizvodnja (potrošnja) — Excel uvoz"));

        JButton btProd = new JButton("Uvezi proizvodnju (Excel)");
        btProd.addActionListener(e -> onImportProductionExcel());
        p.add(btProd);
        p.add(new JLabel("Proizvodnja:"));
        p.add(lbImportedProdFile);

        JLabel hint = new JLabel("Stupci: Datum | Tip dok. | Br. dok. | Komitent/Opis | Šifra | Naziv robe | Količina | (ostalo opcionalno).");
        hint.setForeground(new Color(80, 80, 80));
        p.add(Box.createHorizontalStrut(12));
        p.add(hint);

        return p;
    }

    private JPanel buildInputsPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Ulazni podaci"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0;
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(4, 6, 4, 6);

        addRow(p, gc, "Naziv artikla:", tfNazivArtikla, "Popunjava se klikom iz agregata (Šifra - Naziv).");
        addRow(p, gc, "Godišnja potrošnja (kom/god):", tfGodisnjaPotrosnja, "Dolazi iz agregacije (skalirano na 'Radnih dana').");
        addRow(p, gc, "Stanje zaliha (kom):", tfStanjeZaliha, "Popunjava se klikom iz stanja.");
        addRow(p, gc, "Rok isporuke (dani):", tfRokIsporukeDana, "Lead time.");
        addRow(p, gc, "Minimalna zaliha (kom):", tfMinimalnaZaliha, "Safety stock.");
        addRow(p, gc, "MOQ (min. količina):", tfMOQ, "0 ako nema uvjeta.");
        addRow(p, gc, "Koeficijent obrtaja (x/god):", tfKoefObrtaja, "npr. 12 = mjesečno; 0 -> 30 dana.");
        addRow(p, gc, "Radnih dana u godini:", tfRadnihDana, "365 standard, ili 250.");

        return p;
    }

    private JPanel buildOutputsPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Rezultati"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0;
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(4, 6, 4, 6);

        addOutRow(p, gc, "Dnevna potrošnja (kom/dan):", lbDnevnaPotrosnja);
        addOutRow(p, gc, "Reorder point (ROP):", lbROP);
        addOutRow(p, gc, "Ciljna ciklusna zaliha (kom):", lbCiljnaCiklusna);
        addOutRow(p, gc, "Ciljna maks. razina (kom):", lbCiljnaMax);
        addOutRow(p, gc, "Naručiti odmah:", lbNarucitiOdmah);
        addOutRow(p, gc, "Dana do nabave:", lbDanaDoNabave);
        addOutRow(p, gc, "Preporučena narudžba (kom):", lbPreporucenaNarudzba);
        addOutRow(p, gc, "Datum narudžbe:", lbDatumNarudzbe);
        addOutRow(p, gc, "Očekivani dolazak:", lbOcekivaniDolazak);
        addOutRow(p, gc, "Procijenjeni koef. obrtaja:", lbProcijenjeniK);

        return p;
    }

    private JPanel buildButtonsPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btCalc = new JButton("Izračunaj");
        btCalc.setMnemonic(KeyEvent.VK_I);
        btCalc.addActionListener(e -> calculate());

        JButton btClear = new JButton("Očisti");
        btClear.addActionListener(e -> clearAll());

        JButton btClose = new JButton("Zatvori");
        btClose.addActionListener(e -> dispose());

        p.add(btClear);
        p.add(btCalc);
        p.add(btClose);
        getRootPane().setDefaultButton(btCalc);
        return p;
    }

    private void addRow(JPanel p, GridBagConstraints gc, String label, JTextField field, String tooltip) {
        JLabel lb = new JLabel(label);
        lb.setLabelFor(field);
        field.setToolTipText(tooltip);

        gc.gridx = 0; gc.weightx = 0; gc.fill = GridBagConstraints.NONE;
        p.add(lb, gc);
        gc.gridx = 1; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL;
        p.add(field, gc);

        gc.gridy++;
    }

    private void addOutRow(JPanel p, GridBagConstraints gc, String label, JLabel value) {
        JLabel lb = new JLabel(label);
        gc.gridx = 0; gc.weightx = 0; gc.fill = GridBagConstraints.NONE;
        p.add(lb, gc);

        value.setFont(value.getFont().deriveFont(Font.BOLD));
        gc.gridx = 1; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL;
        p.add(value, gc);
        gc.gridy++;
    }

    private void setupActions() {
        String hint = "Podržan je decimalni zarez i točka.";
        tfGodisnjaPotrosnja.setToolTipText(hint);
        tfStanjeZaliha.setToolTipText(hint);
        tfMinimalnaZaliha.setToolTipText(hint);
        tfMOQ.setToolTipText(hint);
        tfKoefObrtaja.setToolTipText(hint);

        PropertyChangeListener pcl = evt -> {
            // zadržavamo ručni refresh na gumbu
        };
        dcFrom.addPropertyChangeListener("date", pcl);
        dcTo.addPropertyChangeListener("date", pcl);
    }

    private void prefillDefaults() {
        tfRadnihDana.setText("365");
        tfKoefObrtaja.setText("12");
        tfRokIsporukeDana.setText("7");
        tfMinimalnaZaliha.setText("0");
        tfMOQ.setText("0");

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(365);
        dcFrom.setDate(Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        dcTo.setDate(Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }

    private void clearAll() {
        tfNazivArtikla.setText("");
        tfGodisnjaPotrosnja.setText("");
        tfStanjeZaliha.setText("");
        tfRokIsporukeDana.setText("");
        tfMinimalnaZaliha.setText("");
        tfMOQ.setText("");
        tfKoefObrtaja.setText("");
        tfRadnihDana.setText("365");
        setOutputs(null);
        tfNazivArtikla.requestFocusInWindow();

        stockModel.setRows(new ArrayList<>());
        salesRows = new ArrayList<>();
        productionRows = new ArrayList<>();
        aggModel.setData(new ArrayList<>(), getRadnihDana());
        lbImportedStockFile.setText("Nije učitano");
        lbImportedSalesFile.setText("Nije učitano");
        lbImportedProdFile.setText("Nije učitano");
    }

    private void calculate() {
        resetFieldColors();

        try {
            Params p = new Params()
                    .withNazivArtikla(tfNazivArtikla.getText().trim())
                    .withGodisnjaPotrosnja(parseDouble(tfGodisnjaPotrosnja.getText()))
                    .withStanjeZaliha(parseDouble(tfStanjeZaliha.getText()))
                    .withRokIsporukeDana(parseInt(tfRokIsporukeDana.getText()))
                    .withMinimalnaZaliha(parseDouble(tfMinimalnaZaliha.getText()))
                    .withMinimalnaKolicinaZaNaruciti(parseDouble(tfMOQ.getText()))
                    .withKoeficijentObrtaja(parseDouble(tfKoefObrtaja.getText()))
                    .withRadnihDanaUGodini(parseIntOrDefault(tfRadnihDana.getText(), 365));

            Plan plan = InventoryTurnoverPlanner.compute(p);
            setOutputs(plan);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Provjerite numerična polja. Dozvoljen je decimalni zarez ili točka.\n" + ex.getMessage(),
                    "Neispravan unos",
                    JOptionPane.WARNING_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Dogodila se greška: " + ex.getMessage(),
                    "Greška",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void resetFieldColors() {
        Color bg = UIManager.getColor("TextField.background");
        tfGodisnjaPotrosnja.setBackground(bg);
        tfStanjeZaliha.setBackground(bg);
        tfRokIsporukeDana.setBackground(bg);
        tfMinimalnaZaliha.setBackground(bg);
        tfMOQ.setBackground(bg);
        tfKoefObrtaja.setBackground(bg);
        tfRadnihDana.setBackground(bg);
    }

    private double parseDouble(String s) {
        String v = s.trim().replace(',', '.');
        if (v.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            markErrorFieldForValue(s);
            throw new NumberFormatException("Ne mogu parsirati broj: '" + s + "'");
        }
    }

    private int parseInt(String s) {
        String v = s.trim();
        if (v.isEmpty()) return 0;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            markErrorFieldForValue(s);
            throw new NumberFormatException("Ne mogu parsirati cijeli broj: '" + s + "'");
        }
    }

    private int parseIntOrDefault(String s, int def) {
        String v = s.trim();
        if (v.isEmpty()) return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            markErrorFieldForValue(s);
            return def;
        }
    }

    private void markErrorFieldForValue(String originalText) {
        JTextField[] fields = {
                tfGodisnjaPotrosnja, tfStanjeZaliha, tfRokIsporukeDana,
                tfMinimalnaZaliha, tfMOQ, tfKoefObrtaja, tfRadnihDana
        };
        for (JTextField f : fields) {
            if (originalText != null && originalText.equals(f.getText())) {
                f.setBackground(new Color(255, 235, 238));
                f.requestFocusInWindow();
                f.selectAll();
                break;
            }
        }
    }

    private void setOutputs(Plan plan) {
        if (plan == null) {
            lbDnevnaPotrosnja.setText("-");
            lbROP.setText("-");
            lbCiljnaCiklusna.setText("-");
            lbCiljnaMax.setText("-");
            lbNarucitiOdmah.setText("-");
            lbNarucitiOdmah.setForeground(UIManager.getColor("Label.foreground"));
            lbDanaDoNabave.setText("-");
            lbPreporucenaNarudzba.setText("-");
            lbDatumNarudzbe.setText("-");
            lbOcekivaniDolazak.setText("-");
            lbProcijenjeniK.setText("-");
            return;
        }

        lbDnevnaPotrosnja.setText(fmt(plan.dnevnaPotrosnja));
        lbROP.setText(fmt(plan.reorderPoint));
        lbCiljnaCiklusna.setText(fmt(plan.ciljnaCiklusnaZaliha));
        lbCiljnaMax.setText(fmt(plan.ciljnaMaxRazina));

        lbNarucitiOdmah.setText(plan.narucitiOdmah ? "DA" : "NE");
        lbNarucitiOdmah.setForeground(plan.narucitiOdmah ? new Color(0, 128, 0) : new Color(180, 0, 0));

        if (plan.danaDoNabave == Integer.MAX_VALUE) {
            lbDanaDoNabave.setText("N/A");
        } else {
            lbDanaDoNabave.setText(Integer.toString(plan.danaDoNabave));
        }

        lbPreporucenaNarudzba.setText(fmt(plan.preporucenaNarudzbaKom));
        lbDatumNarudzbe.setText(plan.datumNarudzbe.format(dateFmt));
        lbOcekivaniDolazak.setText(plan.ocekivaniDolazak.format(dateFmt));
        lbProcijenjeniK.setText(fmt(plan.procijenjeniKoeficijentObrtaja));
    }

    private String fmt(double v) {
        return df.format(Math.round(v * 100.0) / 100.0);
    }

    // ========== Import handlers ==========

    private void onImportStockExcel() {
        JFileChooser ch = new JFileChooser();
        ch.setDialogTitle("Odaberite Excel za stanje (.xlsx ili .xls)");
        if (ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = ch.getSelectedFile();
        try {
            List<StockRow> rows = ExcelStockImporter.importFile(file);
            stockModel.setRows(rows);
            lbImportedStockFile.setText(file.getName() + " (" + rows.size() + " redova)");
            if (!rows.isEmpty()) stockTable.setRowSelectionInterval(0, 0);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Greška pri uvozu stanja: " + ex.getMessage(), "Uvoz Excela", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onImportSalesExcel() {
        JFileChooser ch = new JFileChooser();
        ch.setDialogTitle("Odaberite Excel za prodaju (.xlsx ili .xls)");
        if (ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = ch.getSelectedFile();
        try {
            salesRows = ExcelSalesImporter.importFile(file);
            lbImportedSalesFile.setText(file.getName() + " (" + salesRows.size() + " redova)");
            if (salesRows.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Nije pronađen nijedan red prodaje.\n" +
                        "- Provjeri gdje je zaglavlje (Datum, (Šifra), Naziv, Količina) — tražimo ga unutar prvih 50 redova.\n" +
                        "- Provjeri format datuma (podržani su i tekstualni datumi s vremenom).",
                        "Uvoz prodaje", JOptionPane.INFORMATION_MESSAGE);
            }
            refreshAggregation();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Greška pri uvozu prodaje: " + ex.getMessage(), "Uvoz Excela", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onImportProductionExcel() {
        JFileChooser ch = new JFileChooser();
        ch.setDialogTitle("Odaberite Excel za proizvodnju (.xlsx ili .xls)");
        if (ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = ch.getSelectedFile();
        try {
            productionRows = ExcelProductionImporter.importFile(file);
            lbImportedProdFile.setText(file.getName() + " (" + productionRows.size() + " redova)");
            if (productionRows.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Nije pronađen nijedan red proizvodnje.\n" +
                        "- Provjeri gdje je zaglavlje (Datum, (Šifra), Naziv, Količina) — tražimo ga unutar prvih 50 redova.",
                        "Uvoz proizvodnje", JOptionPane.INFORMATION_MESSAGE);
            }
            refreshAggregation();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Greška pri uvozu proizvodnje: " + ex.getMessage(), "Uvoz Excela", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ========== NEW: refreshAggregation() ==========
    private void refreshAggregation() {
        List<SalesRow> union = new ArrayList<>();
        if (salesRows != null) union.addAll(salesRows);
        if (productionRows != null) union.addAll(productionRows);

        if (union.isEmpty()) {
            aggModel.setData(new ArrayList<>(), getRadnihDana());
            return;
        }

        LocalDate from = getChooserDate(dcFrom);
        LocalDate to = getChooserDate(dcTo);
        if (from == null || to == null) {
            JOptionPane.showMessageDialog(this, "Odaberite ispravan period (od/do).", "Agregacija", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean groupByCodeOnly = cbGroupByCode.isSelected();
        List<AggregatedConsumption> list = SalesAggregator.aggregateByItem(union, from, to, groupByCodeOnly);
        aggModel.setData(list, getRadnihDana());
        if (!list.isEmpty()) aggTable.setRowSelectionInterval(0, 0);
    }

    private int getRadnihDana() {
        return parseIntOrDefault(tfRadnihDana.getText(), 365);
    }

    private LocalDate getChooserDate(JDateChooser chooser) {
        Date d = chooser.getDate();
        if (d == null) return null;
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ========== Selection handlers ==========

    private void onStockSelectionChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        int idx = stockTable.getSelectedRow();
        if (idx < 0) return;
        StockRow r = stockModel.getRow(idx);
        if (r == null) return;

        String naziv = r.getSifra().isBlank() ? r.getNazivArtikla() : (r.getSifra() + " - " + r.getNazivArtikla());
        tfNazivArtikla.setText(naziv);
        tfStanjeZaliha.setText(df.format(r.getKolicina()));
    }

    private void onAggSelectionChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        int idx = aggTable.getSelectedRow();
        if (idx < 0) return;
        AggregatedConsumption ac = aggModel.getRow(idx);
        if (ac == null) return;

        String naziv = ac.getSifra().isBlank() ? ac.getNaziv() : (ac.getSifra() + " - " + ac.getNaziv());
        tfNazivArtikla.setText(naziv);
        double annual = ac.getAnnualConsumption(getRadnihDana());
        tfGodisnjaPotrosnja.setText(fmt(annual));

        if (!ac.getSifra().isBlank()) {
            Double qty = stockModel.findQtyBySifra(ac.getSifra());
            if (qty != null) tfStanjeZaliha.setText(fmt(qty));
        }
    }

    // ========== Table models (Stock) ==========

    private static class StockTableModel extends javax.swing.table.AbstractTableModel {
        private final String[] columns = {
                "Šifra", "Naziv artikla", "Jed.mj.", "Količina", "Nabavna cijena", "Nabavna vrijednost"
        };
        private final Class<?>[] types = {
                String.class, String.class, String.class, Double.class, Double.class, Double.class
        };
        private List<StockRow> rows = new ArrayList<>();

        public void setRows(List<StockRow> items) {
            this.rows = items != null ? new ArrayList<>(items) : new ArrayList<>();
            fireTableDataChanged();
        }

        public StockRow getRow(int idx) {
            if (idx < 0 || idx >= rows.size()) return null;
            return rows.get(idx);
        }

        public Double findQtyBySifra(String sifra) {
            if (sifra == null || sifra.isBlank()) return null;
            for (StockRow r : rows) {
                if (sifra.equalsIgnoreCase(r.getSifra())) {
                    return r.getKolicina();
                }
            }
            return null;
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public Class<?> getColumnClass(int columnIndex) { return types[columnIndex]; }
        @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            StockRow r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.getSifra();
                case 1 -> r.getNazivArtikla();
                case 2 -> r.getJedinicaMjere();
                case 3 -> r.getKolicina();
                case 4 -> r.getNabavnaCijena();
                case 5 -> r.getNabavnaVrijednost();
                default -> null;
            };
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new InventoryTurnoverPlannerFrame().setVisible(true);
        });
    }
}