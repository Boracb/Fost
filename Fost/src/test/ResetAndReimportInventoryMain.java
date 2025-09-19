package test;

import db.InventoryStateDatabaseHelper;
import excel.ExcelInventoryStateReader;
import logic.InventoryResetService;

import java.io.File;

public class ResetAndReimportInventoryMain {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Upotreba:");
            System.err.println("  java -cp . test.ResetAndReimportInventoryMain --clear");
            System.err.println("  java -cp . test.ResetAndReimportInventoryMain \"C:\\put\\do\\excel.xlsx\"");
            return;
        }

        String dbUrl = "jdbc:sqlite:fost.db";
        var helper = new InventoryStateDatabaseHelper(dbUrl);
        helper.ensureSchema();
        var reset = new InventoryResetService(helper);

        if ("--clear".equalsIgnoreCase(args[0])) {
            reset.clearAll();
            System.out.println("Sve obrisano.");
            return;
        }

        File excel = new File(args[0]);
        if (!excel.exists()) {
            System.err.println("Ne postoji: " + excel.getAbsolutePath());
            return;
        }

        ExcelInventoryStateReader reader = new ExcelInventoryStateReader()
                .withColumns(0,1,2,3,4,5)
                .withHeader(true)
                .enableDebug(true);

        reset.clearAndReimport(excel, reader);
        System.out.println("Reset + reimport gotovo.");
    }
}