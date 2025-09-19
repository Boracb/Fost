package ui;

import model.AggregatedConsumption;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Tabela agregirane potrošnje (prodaja + proizvodnja).
 */
public class SalesAggTableModel extends AbstractTableModel {

    private final String[] cols = {
            "Šifra", "Naziv", "Ukupno (kom)", "Prosjek / dan", "Skalirano (kom/god)"
    };
    private final Class<?>[] types = {
            String.class, String.class, Double.class, Double.class, Double.class
    };

    private final List<AggregatedConsumption> data = new ArrayList<>();
    private int radnihDana = 365;

    public void setData(List<AggregatedConsumption> list, int radnihDana) {
        this.radnihDana = radnihDana <= 0 ? 365 : radnihDana;
        data.clear();
        if (list != null) data.addAll(list);
        fireTableDataChanged();
    }

    public AggregatedConsumption getRow(int idx) {
        if (idx < 0 || idx >= data.size()) return null;
        return data.get(idx);
    }

    @Override public int getRowCount() { return data.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int column) { return cols[column]; }
    @Override public Class<?> getColumnClass(int columnIndex) { return types[columnIndex]; }
    @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        AggregatedConsumption r = data.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> r.getSifra();
            case 1 -> r.getNaziv();
            case 2 -> round2(r.getTotalQty());
            case 3 -> round2(r.getAvgPerDay());
            case 4 -> round2(r.getAnnualConsumption(radnihDana));
            default -> null;
        };
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}