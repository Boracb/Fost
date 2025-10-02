package test;

import dao.ConnectionProvider;
import dao.ProductDao;
import service.SalesImportService;

import javax.swing.*;
import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ImportSalesMain {
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

        // Fallback datum (opcionalno). Ako ništa ne uneseš, a kolona Datum je prazna, koristit će se današnji.
        LocalDate fallbackDate = null;
        String ans = JOptionPane.showInputDialog(null,
                "Stupac 'Datum' je prazan? Unesi fallback datum (dd.MM.yyyy) ili ostavi prazno za današnji:",
                "");
        if (ans != null && !ans.isBlank()) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d.M.yyyy").withLocale(new Locale("hr","HR"));
                fallbackDate = LocalDate.parse(ans.trim().replace('/', '.').replace('-', '.'), fmt);
            } catch (Exception ignored) {
                JOptionPane.showMessageDialog(null, "Neispravan format datuma. Nastavljam bez fallbacka (koristit će se današnji ako treba).");
            }
        }

        int autoCreate = JOptionPane.showConfirmDialog(null,
                "Automatski kreirati nedostajuće proizvode (base_unit='kom') prema Excelu?",
                "Auto-create proizvoda", JOptionPane.YES_NO_OPTION);
        boolean enableAutoCreate = (autoCreate == JOptionPane.YES_OPTION);

        var cp = new ConnectionProvider(dbUrl);
        var productDao = new ProductDao(cp);

        SalesImportService svc = new SalesImportService(cp, productDao)
                .enableAutoCreateMissingProducts(enableAutoCreate);

        List<String> messages = svc.importSales(Paths.get(excel.getAbsolutePath()), fallbackDate);
        if (messages.isEmpty()) {
            System.out.println("Import prodaje završen bez poruka.");
        } else {
            System.out.println("Import prodaje završio s porukama:");
            messages.forEach(System.out::println);
        }
    }
}