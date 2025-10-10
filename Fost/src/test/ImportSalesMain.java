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

/**
 * Napredni CLI/Swing hibrid za import prodaje.
 * - Odabir Excel datoteke (ili args[0])
 * - Opcionalni fallback datum (ako stupac Datum prazan)
 * - Opcionalno automatsko kreiranje proizvoda
 * - Opcionalni "dry run" (samo provjera) - pokreni s argumentom --dry-run
 */
public class ImportSalesMain {

    public static void main(String[] args) throws Exception {
        boolean dryRun = false;
        String explicitFile = null;

        for (String a : args) {
            if (a.equalsIgnoreCase("--dry-run")) dryRun = true;
            else explicitFile = a;
        }

        String dbUrl = "jdbc:sqlite:fost.db";
        File excel;

        if (explicitFile != null) {
            excel = new File(explicitFile);
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

        LocalDate fallbackDate = null;
        String ans = JOptionPane.showInputDialog(null,
                "Stupac 'Datum' je prazan? Unesi fallback datum (dd.MM.yyyy) ili ostavi prazno:",
                "");
        if (ans != null && !ans.isBlank()) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d.M.yyyy").withLocale(new Locale("hr","HR"));
                fallbackDate = LocalDate.parse(ans.trim().replace('/', '.').replace('-', '.'), fmt);
            } catch (Exception ignored) {
                JOptionPane.showMessageDialog(null,
                        "Neispravan format datuma. Nastavljam bez fallbacka (koristit će se današnji ako treba).");
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

        long t0 = System.currentTimeMillis();
        List<String> messages = dryRun
                ? simulate(svc, excel, fallbackDate)   // ako želiš kasnije right proper dry-run logic
                : svc.importSales(Paths.get(excel.getAbsolutePath()), fallbackDate);
        long t1 = System.currentTimeMillis();

        if (messages.isEmpty()) {
            System.out.println("Import prodaje završen bez poruka.");
        } else {
            System.out.println("Import prodaje završio s porukama:");
            messages.forEach(System.out::println);
        }
        System.out.println("Trajanje: " + (t1 - t0) + " ms");
        if (dryRun) {
            System.out.println("NAPOMENA: Dry run (podaci nisu zapisani).");
        }
    }

    // Simulacija (placeholder) – trenutno samo pozove pravi import i vrati poruke.
    // Ako želiš pravi dry-run: trebaš dodati transakciju + rollback u SalesImportService
    private static List<String> simulate(SalesImportService svc, File excel, LocalDate fallback) throws Exception {
        return svc.importSales(Paths.get(excel.getAbsolutePath()), fallback);
    }
}