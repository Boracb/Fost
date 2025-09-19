package test;

import dao.*;
import excel.ExcelProductInventoryReader;
import service.ImportService;

import javax.swing.*;
import java.io.File;

public class ImportProductsAndInventoryMain {
    public static void main(String[] args) throws Exception {
        String dbUrl = "jdbc:sqlite:fost.db";
        File excel;

        if (args.length > 0) {
            excel = new File(args[0]);
        } else {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
                System.err.println("Prekid.");
                return;
            }
            excel = fc.getSelectedFile();
        }
        if (!excel.exists()) {
            System.err.println("Ne postoji: " + excel.getAbsolutePath());
            return;
        }

        var cp = new ConnectionProvider(dbUrl);
        var productDao = new ProductDao(cp);
        var inventoryDao = new InventoryDao(cp);
        var groupDao = new ProductGroupDao(cp);

        var reader = new ExcelProductInventoryReader()
                .withHeader(true)
                .enableDebug(true);

        ImportService importService = new ImportService(reader, productDao, inventoryDao, groupDao);
        importService.fullImport(excel);
        System.out.println("Import završen.");
    }
}