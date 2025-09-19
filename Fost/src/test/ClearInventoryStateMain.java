package test;

import db.InventoryStateDatabaseHelper;

public class ClearInventoryStateMain {
    public static void main(String[] args) throws Exception {
        var helper = new InventoryStateDatabaseHelper("jdbc:sqlite:fost.db");
        helper.truncateAll();
        System.out.println("inventory_state ispražnjen.");
    }
}