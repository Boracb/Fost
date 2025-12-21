package test;

import dao.ConnectionProvider;
import dao.ProductDao;
import excel.ExcelSalesReader;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.regex.Pattern;

public class CheckMissingProductCodesFromSalesExcel {

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

    private static String stripLeadingZeros(String s) {
        return s == null ? null : s.replaceFirst("^0+(?!$)", "");
    }

    public static void main(String[] args) throws Exception {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File excel = fc.getSelectedFile();

        String dbUrl = "jdbc:sqlite:fost.db";
        var cp = new ConnectionProvider(dbUrl);
        var productDao = new ProductDao(cp);

        Set<String> unique = new TreeSet<>();
        ExcelSalesReader r = new ExcelSalesReader();
        try (FileInputStream fis = new FileInputStream(excel)) {
            for (var row : r.read(fis)) {
                String code = normalizeCode(row.productCode);
                if (code != null && !code.isBlank()) unique.add(code);
            }
        }

        List<String> missing = new ArrayList<>();
        int present = 0;
        for (String code : unique) {
            var found = productDao.find(code);
            if (found.isEmpty()) {
                String alt = stripLeadingZeros(code);
                if (!alt.equals(code) && productDao.find(alt).isPresent()) {
                    present++;
                } else {
                    missing.add(code);
                }
            } else {
                present++;
            }
        }

        System.out.println("Ukupno unikatnih šifara u Excelu: " + unique.size());
        System.out.println("Postoje u bazi: " + present);
        System.out.println("Nedostaju (" + missing.size() + "):");
        for (String m : missing) System.out.println(" - " + m);
    }
}