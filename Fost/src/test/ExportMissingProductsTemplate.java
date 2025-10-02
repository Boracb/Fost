package test;

import dao.ConnectionProvider;
import dao.ProductDao;
import excel.ExcelSalesReader;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class ExportMissingProductsTemplate {

    private static final Pattern NBSP = Pattern.compile("[\\u00A0\\u2007\\u202F]");

    private static String normalizeCode(String s) {
        if (s == null) return null;
        String t = s.trim();
        t = NBSP.matcher(t).replaceAll(" ");
        t = t.replaceAll("\\s+", "");
        t = t.replaceAll("[^0-9A-Za-z_-]", "");
        t = t.replaceAll("\\.0$", "");
        return t.toUpperCase(Locale.ROOT);
    }

    private static String cleanName(String s) {
        if (s == null) return "";
        return s.trim().replaceFirst("^[:\\-\\s]+", "").trim();
    }

    public static void main(String[] args) throws Exception {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File excel = fc.getSelectedFile();

        String dbUrl = "jdbc:sqlite:fost.db";
        var cp = new ConnectionProvider(dbUrl);
        var productDao = new ProductDao(cp);

        Set<String> unique = new TreeSet<>();
        Map<String, String> codeToName = new HashMap<>();

        ExcelSalesReader r = new ExcelSalesReader();
        try (FileInputStream fis = new FileInputStream(excel)) {
            for (var row : r.read(fis)) {
                String code = normalizeCode(row.productCode);
                if (code == null || code.isBlank()) continue;
                unique.add(code);
                if (!codeToName.containsKey(code)) {
                    codeToName.put(code, cleanName(row.productName));
                }
            }
        }

        List<String> missing = new ArrayList<>();
        for (String code : unique) {
            if (productDao.find(code).isEmpty()) {
                missing.add(code);
            }
        }

        if (missing.isEmpty()) {
            System.out.println("Nema nedostajućih proizvoda.");
            return;
        }

        File out = new File("missing_products_template.csv");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8))) {
            pw.println("product_code,name,base_unit,active");
            for (String code : missing) {
                String name = codeToName.getOrDefault(code, "");
                pw.printf("%s,%s,%s,%s%n",
                        code,
                        escapeCsv(name),
                        "kom",
                        "true");
            }
        }
        System.out.println("Zapisano: " + out.getAbsolutePath());
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        boolean needsQuotes = s.contains(",") || s.contains("\"") || s.contains("\n");
        String t = s.replace("\"", "\"\"");
        return needsQuotes ? ("\"" + t + "\"") : t;
    }
}