package test;

import db.InventoryStateDatabaseHelper;
import model.StockState;

public class ShowInventoryStateMain {
    public static void main(String[] args) throws Exception {
        String dbUrl = "jdbc:sqlite:fost.db";
        var helper = new InventoryStateDatabaseHelper(dbUrl);
        helper.ensureSchema();
        var list = helper.findAll();
        System.out.println("Ukupno: " + list.size());
        System.out.printf("%-14s %-30s %-8s %10s %12s %15s%n",
                "Šifra","Naziv","JM","Količina","Nab.cijena","Nab.vrijed.");
        for (StockState s : list) {
            System.out.printf("%-14s %-30s %-8s %10.2f %12.2f %15.2f%n",
                    s.getProductCode(),
                    s.getName(),
                    s.getUnit(),
                    s.getQuantity(),
                    s.getPurchaseUnitPrice() != null ? s.getPurchaseUnitPrice() : 0.0,
                    s.getPurchaseTotalValue() != null ? s.getPurchaseTotalValue() : 0.0
            );
        }
    }
}