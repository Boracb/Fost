package ui;

import model.AggregatedConsumption;

import javax.swing.table.AbstractTableModel;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class SalesAggTableModel extends AbstractTableModel {
    private final String[] columns = {
            "Šifra", "Naziv", "Količina (period)", "Dana u periodu", "Prosj. dnevno", "Godišnje (Radni dani)"
    };
    private final Class<?>[] types = {
            String.class, String.class, Double.class, Integer.class, Double.class, Double.class
    };

    private final DecimalFormat df = new DecimalFormat("#,##0.##");
    private List<AggregatedConsumption> data = new ArrayList<>();
    private int radnihDana = 365;

    public void setData(List<AggregatedConsumption> list, int radnihDana) {
        this.data = list != null ? new ArrayList<>(list) : new ArrayList<>();
        this.radnihDana = radnihDana <= 0 ? 365 : radnihDana;
        fireTableDataChanged();
    }

    public AggregatedConsumption getRow(int idx) {
        if (idx < 0 || idx >= data.size()) return null;
        return data.get(idx);
    }

    @Override public int getRowCount() { return data.size(); }
    @Override public int getColumnCount() { return columns.length; }
    @Override public String getColumnName(int column) { return columns[column]; }
    @Override public Class<?> getColumnClass(int columnIndex) { return types[columnIndex]; }
    @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        AggregatedConsumption ac = data.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> ac.getSifra();
            case 1 -> ac.getNaziv();
            case 2 -> round2(ac.getTotalQty());
            case 3 -> ac.getDaysInPeriod();
            case 4 -> round2(ac.getAvgDaily());
            case 5 -> round2(ac.getAnnualConsumption(radnihDana));
            default -> null;
        };
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}