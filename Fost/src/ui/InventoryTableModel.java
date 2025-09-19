package ui;

import model.StockState;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Model tablice: Šifra | Naziv artikla | Jed.mj. | Količina | Nabavna cijena | Nabavna vrijednost
 * Uređuje se: Količina (col=3) i Nabavna cijena (col=4).
 */
public class InventoryTableModel extends AbstractTableModel {

    private final List<StockState> data = new ArrayList<>();
    private final String[] columns = {
            "Šifra", "Naziv artikla", "Jed.mj.", "Količina", "Nabavna cijena", "Nabavna vrijednost"
    };

    public void setData(List<StockState> list) {
        data.clear();
        data.addAll(list);
        fireTableDataChanged();
    }

    public List<StockState> snapshot() {
        return new ArrayList<>(data);
    }

    public StockState getAt(int row) {
        return data.get(row);
    }

    @Override public int getRowCount() { return data.size(); }
    @Override public int getColumnCount() { return columns.length; }
    @Override public String getColumnName(int column) { return columns[column]; }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 3,4,5 -> Double.class;
            default -> String.class;
        };
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return col == 3 || col == 4; // količina i jedinična cijena
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        StockState s = data.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> s.getProductCode();
            case 1 -> s.getName();
            case 2 -> s.getUnit();
            case 3 -> s.getQuantity();
            case 4 -> s.getPurchaseUnitPrice();
            case 5 -> s.getPurchaseTotalValue();
            default -> null;
        };
    }

    @Override
    public void setValueAt(Object aValue, int row, int col) {
        StockState old = data.get(row);
        if (aValue == null) return;
        try {
            double dbl = Double.parseDouble(aValue.toString().trim().replace(',', '.'));
            StockState updated = old;
            if (col == 3) {
                updated = old.withQuantity(dbl);
            } else if (col == 4) {
                updated = old.withUnitPrice(dbl);
            }
            data.set(row, updated);
            fireTableRowsUpdated(row, row);
        } catch (NumberFormatException ignored) {}
    }
}