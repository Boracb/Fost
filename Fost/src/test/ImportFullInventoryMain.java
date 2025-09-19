package test;

import db.InventoryStateDatabaseHelper;
import excel.ExcelInventoryStateReader;
import logic.InventoryImportService;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ImportFullInventoryMain {
    public static void main(String[] args) throws Exception {
        String excelPath;
        if (args.length > 0) {
            excelPath = args[0];
        } else {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Odaberi Excel (stanje skladišta)");
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                excelPath = fc.getSelectedFile().getAbsolutePath();
            } else {
                System.err.println("Prekid: nije odabrano.");
                return;
            }
        }

        Path p = Path.of(excelPath);
        if (!Files.exists(p)) {
            System.err.println("Ne postoji: " + p.toAbsolutePath());
            return;
        }

        String dbUrl = "jdbc:sqlite:fost.db";

        var reader = new ExcelInventoryStateReader()
                .withColumns(0,1,2,3,4,5)   // mapping prema tvom Excelu
                .withHeader(true)
                .enableDebug(true)
                .forceRecalculateTotal(false); // stavi true ako želiš IGNORIRATI vrijednost iz Excela

        var helper = new InventoryStateDatabaseHelper(dbUrl);
        var service = new InventoryImportService(reader::parse, helper);

        System.out.println("Import start...");
        service.safeReplaceAll(p.toFile());
        System.out.println("Import gotov.");
    }
}