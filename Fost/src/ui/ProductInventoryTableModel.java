package ui;

import model.ProductInventoryView;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class ProductInventoryTableModel extends AbstractTableModel {

    private final String[] cols = {
            "Šifra","Naziv","Dobavljač","Vrsta","Količina (base)",
            "Base jedinica","Alt jedinica","Alt količina",
            "m2/kom","Pakiranje","Min.nar.","Jed. cijena","Ukupna vrijednost","Grupe"
    };

    private final List<ProductInventoryView> original = new ArrayList<>();
    private final List<ProductInventoryView> data = new ArrayList<>();

    public void setData(List<ProductInventoryView> list) {
        original.clear();
        original.addAll(list);
        data.clear();
        data.addAll(list);
        fireTableDataChanged();
    }

    public void applyFilter(Predicate<ProductInventoryView> pred) {
        data.clear();
        for (var piv : original) {
            if (pred.test(piv)) data.add(piv);
        }
        fireTableDataChanged();
    }

    public void sortBy(Comparator<? super ProductInventoryView> comp) {  // <— promjena
        data.sort(comp);
        fireTableDataChanged();
    }

    public ProductInventoryView getAt(int row) {
        return data.get(row);
    }

    @Override public int getRowCount() { return data.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int column) { return cols[column]; }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 4,7,8,9,10,11,12 -> Double.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        var v = data.get(rowIndex);
        var p = v.getProduct();
        var inv = v.getInventory();
        return switch (columnIndex) {
            case 0 -> p.getProductCode();
            case 1 -> p.getName();
            case 2 -> p.getSupplierCode();
            case 3 -> p.getMainType();
            case 4 -> inv.getQuantity();
            case 5 -> p.getBaseUnit();
            case 6 -> p.getAltUnit();
            case 7 -> v.getComputedAltQuantity();
            case 8 -> p.getAreaPerPiece();
            case 9 -> p.getPackSize();
            case 10 -> p.getMinOrderQty();
            case 11 -> p.getPurchaseUnitPrice();
            case 12 -> v.getTotalValue();
            case 13 -> String.join(",", v.getGroupCodes());
            default -> null;
        };
    }
}