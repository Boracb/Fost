package ui;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Panel za prijedlog narudžbe na temelju lagera i prodaje u odabranom periodu.
 * - Ne dira postojeću UI klasu; dodaje se kao novi tab: new OrderSuggestionPanel()
 * - TODO: Spoji loadSoldQuantities/loadCurrentStockQuantities/loadProductInfo na tvoje servise/DAO sloj.
 */
public class OrderSuggestionPanel extends JPanel {

    private final JSpinner fromDate;
    private final JSpinner toDate;
    private final JSpinner coverageDays;
    private final JSpinner safetyStock;     // opcionalno – sigurnosna zaliha po artiklu (globalno), možeš staviti 0
    private final JCheckBox onlyWithSuggestion;

    private final JButton calcBtn;
    private final JButton resetBtn;

    private final JTable table;
    private final OrderSuggestionTableModel model;

    public OrderSuggestionPanel() {
        super(new BorderLayout());

        // Gornje kontrole
        JPanel controls = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        // Datumi: default zadnjih 30 dana
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.DAY_OF_MONTH, -30);
        Date thirtyDaysAgo = cal.getTime();

        fromDate = new JSpinner(new SpinnerDateModel(thirtyDaysAgo, null, null, Calendar.DAY_OF_MONTH));
        toDate = new JSpinner(new SpinnerDateModel(now, null, null, Calendar.DAY_OF_MONTH));
        JSpinner.DateEditor edFrom = new JSpinner.DateEditor(fromDate, "yyyy-MM-dd");
        JSpinner.DateEditor edTo = new JSpinner.DateEditor(toDate, "yyyy-MM-dd");
        fromDate.setEditor(edFrom);
        toDate.setEditor(edTo);

        coverageDays = new JSpinner(new SpinnerNumberModel(30, 1, 365, 1));
        safetyStock = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1_000_000.0, 1.0));
        onlyWithSuggestion = new JCheckBox("Samo s preporukom", true);

        calcBtn = new JButton("Izračunaj");
        resetBtn = new JButton("Reset");

        int col = 0;
        addL(controls, gc, 0, col, new JLabel("Od:"));
        addC(controls, gc, 1, col++, fromDate);

        addL(controls, gc, 0, col, new JLabel("Do:"));
        addC(controls, gc, 1, col++, toDate);

        addL(controls, gc, 0, col, new JLabel("Pokriće (dana):"));
        addC(controls, gc, 1, col++, coverageDays);

        addL(controls, gc, 0, col, new JLabel("Sigurnosna zaliha:"));
        addC(controls, gc, 1, col++, safetyStock);

        addC(controls, gc, 0, col, onlyWithSuggestion);
        addC(controls, gc, 1, col++, calcBtn);
        addC(controls, gc, 1, col, resetBtn);

        add(controls, BorderLayout.NORTH);

        // Tablica
        model = new OrderSuggestionTableModel();
        table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {110, 240, 120, 90, 90, 90, 110, 90, 90, 110, 160};
        for (int i = 0; i < widths.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        add(new JScrollPane(table), BorderLayout.CENTER);

        // Akcije
        calcBtn.addActionListener(e -> recompute());
        resetBtn.addActionListener(e -> {
            fromDate.setValue(thirtyDaysAgo);
            toDate.setValue(now);
            coverageDays.setValue(30);
            safetyStock.setValue(0.0);
            onlyWithSuggestion.setSelected(true);
            recompute();
        });

        // Initial
        recompute();
    }

    private static void addL(JPanel p, GridBagConstraints gc, int x, int y, Component c) {
        GridBagConstraints t = (GridBagConstraints) gc.clone();
        t.gridx = x;
        t.gridy = y;
        t.anchor = GridBagConstraints.EAST;
        p.add(c, t);
    }

    private static void addC(JPanel p, GridBagConstraints gc, int x, int y, Component c) {
        GridBagConstraints t = (GridBagConstraints) gc.clone();
        t.gridx = x;
        t.gridy = y;
        t.weightx = x == 1 ? 1.0 : 0.0;
        p.add(c, t);
    }

    private void recompute() {
        LocalDate from = toLocalDate((Date) fromDate.getValue());
        LocalDate to = toLocalDate((Date) toDate.getValue());
        long days = Math.max(1, ChronoUnit.DAYS.between(from, to) + 1);

        int targetCoverageDays = ((Number) coverageDays.getValue()).intValue();
        double globalSafety = ((Number) safetyStock.getValue()).doubleValue();

        // 1) Dohvati podatke
        Map<String, Double> soldQty = loadSoldQuantities(from, to);               // productCode -> sold qty in period
        Map<String, Double> stockQty = loadCurrentStockQuantities();              // productCode -> current stock
        Map<String, ProductInfo> productInfo = loadProductInfo();                 // productCode -> info (naziv, dobavljač, minNar, pak)

        // 2) Skupljanje svih šifri (union)
        Set<String> allCodes = new HashSet<>();
        allCodes.addAll(soldQty.keySet());
        allCodes.addAll(stockQty.keySet());
        allCodes.addAll(productInfo.keySet());

        // 3) Izračun po artiklu
        List<OrderRow> rows = new ArrayList<>();
        for (String code : allCodes) {
            double sold = soldQty.getOrDefault(code, 0.0);
            double avgPerDay = sold / (double) days;

            double stock = stockQty.getOrDefault(code, 0.0);
            double coverage = avgPerDay > 0 ? stock / avgPerDay : (stock > 0 ? 9_999 : 0);

            ProductInfo info = productInfo.getOrDefault(code, ProductInfo.empty(code));
            // Procijenjena potrebna količina: (ciljni_dani * avg) + sigurnosna - lager
            double need = (targetCoverageDays * avgPerDay) + globalSafety - stock;
            double recommended = Math.max(0, need);

            // Zaokruživanje na pakiranje & poštivanje minimalne narudžbe
            if (recommended > 0) {
                recommended = ceilToPack(recommended, info.packSize);
                if (info.minOrderQty > 0 && recommended < info.minOrderQty) {
                    recommended = info.minOrderQty;
                }
            }

            OrderRow r = new OrderRow();
            r.productCode = code;
            r.name = info.name;
            r.supplier = info.supplierCode;
            r.stock = round2(stock);
            r.sold = round2(sold);
            r.avgPerDay = round2(avgPerDay);
            r.coverageDays = round2(coverage);
            r.minOrderQty = info.minOrderQty;
            r.packSize = info.packSize;
            r.recommended = round2(recommended);
            r.note = buildNote(coverage, targetCoverageDays, recommended, info);

            if (!onlyWithSuggestion.isSelected() || r.recommended > 0.0001) {
                rows.add(r);
            }
        }

        // Sort: artikli s preporukom prvo, potom po najkraćem pokriću
        rows.sort(Comparator
                .comparing((OrderRow r) -> r.recommended <= 0.0)
                .thenComparingDouble(r -> r.coverageDays));

        model.setData(rows);
    }

    private static String buildNote(double coverage, int targetCoverageDays, double rec, ProductInfo info) {
        List<String> parts = new ArrayList<>();
        if (rec > 0) parts.add("ispod cilj. pokrića");
        if (info.packSize > 0) parts.add("pak=" + trim0(info.packSize));
        if (info.minOrderQty > 0) parts.add("min=" + trim0(info.minOrderQty));
        parts.add("pok=" + trim0(coverage) + "/" + targetCoverageDays + "d");
        return String.join(", ", parts);
    }

    private static String trim0(double v) {
        String s = String.valueOf(v);
        if (s.endsWith(".0")) return s.substring(0, s.length() - 2);
        return s;
    }

    private static double ceilToPack(double qty, double pack) {
        if (pack <= 0) return qty;
        double n = Math.ceil(qty / pack);
        return n * pack;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static LocalDate toLocalDate(Date d) {
        return Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ====== MODEL TABLICE ======
    private static class OrderSuggestionTableModel extends AbstractTableModel {
        private final String[] cols = {
                "Šifra", "Naziv", "Dobavljač", "Lager", "Prodano", "Prosjek/dan",
                "Pokriće (dana)", "Min.nar.", "Pakiranje", "Predlagano", "Napomena"
        };
        private List<OrderRow> data = new ArrayList<>();

        public void setData(List<OrderRow> rows) {
            this.data = new ArrayList<>(rows);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int column) { return cols[column]; }
        @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case 3: case 4: case 5: case 6: case 7: case 8: case 9: return Double.class;
                default: return String.class;
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            OrderRow r = data.get(rowIndex);
            switch (columnIndex) {
                case 0: return r.productCode;
                case 1: return r.name;
                case 2: return r.supplier;
                case 3: return r.stock;
                case 4: return r.sold;
                case 5: return r.avgPerDay;
                case 6: return r.coverageDays;
                case 7: return r.minOrderQty;
                case 8: return r.packSize;
                case 9: return r.recommended;
                case 10: return r.note;
                default: return null;
            }
        }
    }

    // ====== DTO-ovi za model ======
    private static class OrderRow {
        String productCode;
        String name;
        String supplier;
        double stock;
        double sold;
        double avgPerDay;
        double coverageDays;
        double minOrderQty;
        double packSize;
        double recommended;
        String note;
    }

    private static class ProductInfo {
        final String productCode;
        final String name;
        final String supplierCode;
        final double minOrderQty;
        final double packSize;

        ProductInfo(String productCode, String name, String supplierCode, double minOrderQty, double packSize) {
            this.productCode = productCode;
            this.name = name;
            this.supplierCode = supplierCode;
            this.minOrderQty = minOrderQty;
            this.packSize = packSize;
        }

        static ProductInfo empty(String code) {
            return new ProductInfo(code, "", "", 0, 0);
        }
    }

    // ====== DATA LAYER (TODO: spoji na postojeće servise/DAO) ======

    /**
     * Vrati mapu: šifra -> ukupno prodana količina u periodu [from, to]
     * TODO: Implementiraj preko SalesDao ili odgovarajuće servis sloja.
     */
    private Map<String, Double> loadSoldQuantities(LocalDate from, LocalDate to) {
        // PRIMJER: vrati prazno dok se ne spoji na stvarne podatke
        return Collections.emptyMap();
    }

    /**
     * Vrati mapu: šifra -> trenutna količina na zalihi.
     * TODO: Implementiraj preko InventoryStateDatabaseHelper/InventoryService.
     */
    private Map<String, Double> loadCurrentStockQuantities() {
        // PRIMJER: vrati prazno dok se ne spoji na stvarne podatke
        return Collections.emptyMap();
    }

    /**
     * Vrati osnovne podatke o artiklima radi prikaza i zaokruživanja na pakiranje/min. narudžbu.
     * TODO: Implementiraj preko ProductService/ProductDao.
     */
    private Map<String, ProductInfo> loadProductInfo() {
        // PRIMJER: vrati prazno dok se ne spoji na stvarne podatke
        return Collections.emptyMap();
    }
}