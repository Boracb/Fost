package test;

import db.InventoryStateDatabaseHelper;
import excel.ExcelStockStateReader;
import logic.InventoryImportService;

import javax.swing.*;
import java.nio.file.Path;
import java.nio.file.Files;

public class ImportStocksMain {
    public static void main(String[] args) throws Exception {
        String excelPath;
        if (args.length > 0) {
            excelPath = args[0];
        } else {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Odaberi Excel");
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                excelPath = fc.getSelectedFile().getAbsolutePath();
            } else {
                System.err.println("Prekid (nema odabira).");
                return;
            }
        }

        Path p = Path.of(excelPath);
        if (!Files.exists(p)) {
            System.err.println("Ne postoji: " + p.toAbsolutePath());
            return;
        }

        String dbUrl = "jdbc:sqlite:fost.db";

        var reader = new ExcelStockStateReader()
                .withColumns(0,1,3,2)   // code, name, quantity, unit
                .withHeader(true)
                .enableDebug(true);

        var service = new InventoryImportService(reader::parse,
                new InventoryStateDatabaseHelper(dbUrl));

        System.out.println("Import start...");
        service.safeReplaceAll(p.toFile());
        System.out.println("Import gotov.");
    }
}