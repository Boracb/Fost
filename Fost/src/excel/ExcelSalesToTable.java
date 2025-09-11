package excel;

import db.KomitentiDatabaseHelper;
import model.SalesRow;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * UI helper: učitava "prodaju" u JTable (DefaultTableModel) i odmah popunjava izvedene vrijednosti.
 * Mapiranje u model tablice (isti kao u postojećem ExcelImporter-u):
 * 0=datumNarudzbe, 1=predDatumIsporuke, 2=komitentOpis, 3=nazivRobe, 4=netoVrijednost,
 * 5=kom, 6=status, 7=djelatnik, 8=mm, 9=m, 10=tisucl, 11=m2, 12=startTime, 13=endTime, 14=duration, 15=trgovackiPredstavnik
 */
public final class ExcelSalesToTable {
    private ExcelSalesToTable() {}

    private static final SimpleDateFormat DATE_FMT_DOTS = new SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("hr"));

    public static void importInto(DefaultTableModel model, Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Odaberi Excel (prodaja)");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel (*.xlsx, *.xls)", "xlsx", "xls"));
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            List<SalesRow> rows = ExcelSalesImporter.importFile(file);
            if (rows.isEmpty()) {
                JOptionPane.showMessageDialog(parent,
                        "Nijedan red nije uvezen.\nProvjeri da zaglavlje sadrži barem: Naziv i Količina.",
                        "Uvoz prodaje", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Učitaj mapu komitenta -> TP
            Map<String, String> komitentTP = KomitentiDatabaseHelper.loadKomitentPredstavnikMap();

            for (SalesRow sr : rows) {
                Object[] data = new Object[16];
                data[0]  = formatDate(sr.getDatum());            // datum (može biti prazan)
                data[1]  = "";                                   // predDatumIsporuke (nije dio prodaje)
                data[2]  = nullSafe(sr.getKomitent());           // komitent / opis
                data[3]  = nullSafe(sr.getNaziv());              // naziv robe / opis
                data[4]  = sr.getNetoVrijednost();               // neto vrijednost (može biti null)
                data[5]  = (int)Math.round(sr.getKolicina());    // količina → cijeli broj
                data[6]  = "";                                   // status
                data[7]  = "";                                   // djelatnik

                // izvedene (mm, m, tisucl, m2) — računamo ispod
                data[8]  = null; data[9] = null; data[10] = null; data[11] = null;

                // start, end, duration
                data[12] = ""; data[13] = ""; data[14] = "";

                // trg. predstavnik (ako postoji u bazi)
                String tp = komitentTP.getOrDefault(data[2].toString(), "");
                data[15] = tp;

                model.addRow(data);

                // izračun izvedenih iz naziva
                int r = model.getRowCount() - 1;
                recomputeDerived(model, r);
            }

            JOptionPane.showMessageDialog(parent,
                    "Uvezeno redova: " + rows.size(),
                    "Uvoz prodaje", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Greška pri uvozu prodaje:\n" + ex.getMessage(),
                    "Uvoz prodaje", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String formatDate(LocalDate ld) {
        if (ld == null) return "";
        Date util = java.sql.Date.valueOf(ld);
        return DATE_FMT_DOTS.format(util);
    }

    private static String nullSafe(String s) { return s == null ? "" : s.trim(); }

    // Račun mm/m iz naziva i izvedenih vrijednosti; ekvivalent UI.recomputeRow
    private static void recomputeDerived(DefaultTableModel model, int r) {
        String naziv = model.getValueAt(r, 3) == null ? "" : model.getValueAt(r, 3).toString();
        double[] mmM = parseMmM(naziv);
        double mm = mmM[0], m = mmM[1];

        model.setValueAt(mm == 0 ? null : mm, r, 8);
        model.setValueAt(m  == 0 ? null : m,  r, 9);
        Double tisucl = (mm == 0 || m == 0) ? null : (mm / 1000.0) * m;
        model.setValueAt(tisucl, r, 10);

        double kom = 0.0;
        Object komObj = model.getValueAt(r, 5);
        if (komObj instanceof Number) kom = ((Number) komObj).doubleValue();
        else {
            try { kom = Double.parseDouble(komObj.toString().trim()); } catch (Exception ignored) {}
        }
        Double m2 = (tisucl == null || kom == 0) ? null : tisucl * kom;
        model.setValueAt(m2, r, 11);
    }

    // Parsira "50/66", "50,5 / 66" u [mm, m]
    private static double[] parseMmM(String s) {
        if (s == null) return new double[]{0,0};
        String txt = s.trim();
        int slash = txt.indexOf('/');
        if (slash <= 0) return new double[]{0,0};
        String a = txt.substring(0, slash).replace(',', '.').replaceAll("[^0-9.]", "").trim();
        String b = txt.substring(slash + 1).replace(',', '.').replaceAll("[^0-9.]", "").trim();
        try {
            double mm = a.isEmpty() ? 0 : Double.parseDouble(a);
            double m  = b.isEmpty() ? 0 : Double.parseDouble(b);
            return new double[]{mm, m};
        } catch (Exception e) {
            return new double[]{0,0};
        }
    }
}