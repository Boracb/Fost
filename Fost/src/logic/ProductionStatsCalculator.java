package logic;

import javax.swing.table.DefaultTableModel;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Kalkulator proizvodnih statistika.
 * Računa ukupno, izrađeno i za izraditi, te dane potrebne za dovršetak.
 */

public class ProductionStatsCalculator {

    public static final String KOM = "kom";
    public static final String M2 = "m2";
    public static final String NETO = "neto";

    public static final String KOM_IZR = "komIzr";
    public static final String M2_IZR = "m2Izr";
    public static final String NETO_IZR = "netoIzr";

    public static final String KOM_ZAI = "komZai";
    public static final String M2_ZAI = "m2Zai";
    public static final String NETO_ZAI = "netoZai";

    public static final String RADNI_DANI_PREOSTALO = "radniDaniPreostalo";
    public static final String KAL_DANI_PREOSTALO = "kalendarskiDaniPreostalo";

    public static final String PROSJEK_M2_PO_DANU = "prosjekM2PoDanu";

    /**
     * Calculate statistics from the table model.
     * @param model table model with expected columns:
     *              1 = predDatumIsporuke (fallback), 4 = neto, 5 = kom, 11 = m2, 6 = status, 13 = endTime
     * @param m2PoSatu kapacitet u m2 po satu (m²/h) - koristi se kao fallback dnevnog kapaciteta (m2PoSatu * radniSatiPoDanu)
     * @return map sa ključevima definiranima iznad
     */
    public static Map<String, Object> calculate(DefaultTableModel model, double m2PoSatu) {
        if (m2PoSatu <= 0) throw new IllegalArgumentException("Kapacitet m²/h > 0");

        double totalKom = 0, totalM2 = 0, totalNeto = 0;
        double komIzr = 0, m2Izr = 0, netoIzr = 0;
        double komZai = 0, m2Zai = 0, netoZai = 0;
        Map<LocalDate, Double> m2PoDanuIzradjeno = new HashMap<>();

        for (int r = 0; r < model.getRowCount(); r++) {
            double kom  = toDouble(model.getValueAt(r, 5));
            double m2   = toDouble(model.getValueAt(r, 11));
            double neto = toDouble(model.getValueAt(r, 4));
            String status = (model.getValueAt(r, 6) == null) ? "" : model.getValueAt(r, 6).toString();

            totalKom  += kom; totalM2 += m2; totalNeto += neto;
            String statusNorm = status.trim().toLowerCase(Locale.ROOT);

            if (statusNorm.equals("izrađeno") || statusNorm.equals("izradeno")) {
                komIzr += kom; m2Izr += m2; netoIzr += neto;
                // KORIŠTENJE endTime kolone (13). Ako nije parsabilno, koristimo predDatumIsporuke (1) kao fallback.
                LocalDate datum = toDate(model.getValueAt(r, 13));
                if (datum == null) datum = toDate(model.getValueAt(r, 1));
                if (datum != null) m2PoDanuIzradjeno.merge(datum, m2, Double::sum);
            } else if (statusNorm.equals("u izradi") || statusNorm.isEmpty()) {
                komZai += kom; m2Zai += m2; netoZai += neto;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put(KOM, totalKom);  result.put(M2, totalM2);  result.put(NETO, totalNeto);
        result.put(KOM_IZR, komIzr); result.put(M2_IZR, m2Izr); result.put(NETO_IZR, netoIzr);
        result.put(KOM_ZAI, komZai); result.put(M2_ZAI, m2Zai); result.put(NETO_ZAI, netoZai);

        // Ako imamo povijesne dane, prosjek je stvarni prosjek (m2 po različitim danima kada je nešto završeno).
        // Inače koristimo procjenu kapaciteta: m2PoSatu * radniSatiPoDanu (ovdje fiksno 8h za smjenu 07:00-15:00).
        final double radniSatiPoDanu = 8.0;
        double fallbackDaily = m2PoSatu * radniSatiPoDanu;
        double prosjek = m2PoDanuIzradjeno.isEmpty() ? fallbackDaily : (m2Izr / m2PoDanuIzradjeno.size());
        result.put(PROSJEK_M2_PO_DANU, prosjek);

        if (prosjek > 0) {
            // radni dani preostalo (može biti decimalni broj, zaokružujemo na 2 decimale kao ranije)
            double rd = Math.ceil((m2Zai / prosjek) * 100) / 100.0;
            double kd = Math.ceil(countCalendarDaysFromToday(rd) * 100) / 100.0;
            result.put(RADNI_DANI_PREOSTALO, rd);
            result.put(KAL_DANI_PREOSTALO, kd);
        } else {
            result.put(RADNI_DANI_PREOSTALO, 0.0);
            result.put(KAL_DANI_PREOSTALO, 0.0);
        }
        return result;
    }

    /**
     * Procijeni datum dovršetka (kalendarski datum) na osnovi trenutnog stanja modela i kapaciteta m²/h.
     * Vraća LocalDate koji predstavlja zadnji dan potrebnih radnih dana (uključujući i današnje ako se broji).
     * Ako nema preostalog posla vraća danasnji datum.
     */
    public static LocalDate estimateCompletionDate(DefaultTableModel model, double m2PoSatu) {
        Map<String, Object> stats = calculate(model, m2PoSatu);
        Object rdObj = stats.get(RADNI_DANI_PREOSTALO);
        double rd = (rdObj instanceof Number) ? ((Number) rdObj).doubleValue() : 0.0;
        if (rd <= 0) return LocalDate.now();
        return dateAfterWorkingDays(rd);
    }

    /**
     * Vraća datum koji je nakon potrebnog broja radnih dana (wd može biti decimalni).
     * Računa minimalan broj kalendarskih dana potrebnih da se pređe wd radnih dana i vraća taj datum.
     */
    private static LocalDate dateAfterWorkingDays(double wd) {
        if (wd <= 0) return LocalDate.now();
        LocalDate today = LocalDate.now(); int total = 0; double work = 0;
        while (work < wd) {
            total++; LocalDate d = today.plusDays(total);
            if (isWorkingDay(d)) work++;
        }
        return today.plusDays(total);
    }

    private static double countCalendarDaysFromToday(double wd) {
        if (wd <= 0) return 0.0;
        LocalDate today = LocalDate.now(); int total = 0; double work = 0;
        while (work < wd) {
            total++; LocalDate d = today.plusDays(total);
            if (isWorkingDay(d)) work++;
        }
        return total;
    }
    private static double toDouble(Object val) { return (val instanceof Number) ? ((Number) val).doubleValue() : 0; }
    private static LocalDate toDate(Object val) {
        if (val == null) return null;
        DateTimeFormatter[] fmts = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy."),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy."),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH)
        };
        try {
            if (val instanceof LocalDate) return (LocalDate) val;
            String s = val.toString().trim();
            for (DateTimeFormatter fmt : fmts) try { return LocalDate.parse(s, fmt); } catch (Exception ignore) {}
        } catch (Exception ignored) {}
        return null;
    }
    private static boolean isWorkingDay(LocalDate d) {
        Set<LocalDate> holidays = WorkingTimeCalculator.getHolidaysForYear(d.getYear());
        return !(d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY || holidays.contains(d));
    }
}