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

            if (statusNorm.equals("izrađeno")) {
                komIzr += kom; m2Izr += m2; netoIzr += neto;
                LocalDate datum = toDate(model.getValueAt(r, 1));
                if (datum != null) m2PoDanuIzradjeno.merge(datum, m2, Double::sum);
            } else if (statusNorm.equals("u izradi") || statusNorm.isEmpty()) {
                komZai += kom; m2Zai += m2; netoZai += neto;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put(KOM, totalKom);  result.put(M2, totalM2);  result.put(NETO, totalNeto);
        result.put(KOM_IZR, komIzr); result.put(M2_IZR, m2Izr); result.put(NETO_IZR, netoIzr);
        result.put(KOM_ZAI, komZai); result.put(M2_ZAI, m2Zai); result.put(NETO_ZAI, netoZai);

        double prosjek = m2PoDanuIzradjeno.isEmpty() ? 0 : (m2Izr / m2PoDanuIzradjeno.size());
        result.put(PROSJEK_M2_PO_DANU, prosjek);

        if (prosjek > 0) {
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
