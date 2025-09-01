package logic;

import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Kalkulator proizvodnih statistika.
 * Sada podržava TableModel i automatsko pronalaženje stupaca po nazivima zaglavlja.
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

    // očekivani default/fallback indeksi (0-based)
    private static final int FALLBACK_PRED_DATUM = 1;
    private static final int FALLBACK_NETO = 4;
    private static final int FALLBACK_KOM = 5;
    private static final int FALLBACK_STATUS = 6;
    private static final int FALLBACK_M2 = 11;
    private static final int FALLBACK_ENDTIME = 13;

    /**
     * Backward-compatible: original metoda koja prima DefaultTableModel.
     */
    public static Map<String, Object> calculate(DefaultTableModel model, double m2PoSatu) {
        return calculate((TableModel) model, m2PoSatu);
    }

    /**
     * Nova metoda: prima TableModel, traži stupce po zaglavlju ili koristi fallback indekse.
     */
    public static Map<String, Object> calculate(TableModel model, double m2PoSatu) {
        if (m2PoSatu <= 0) throw new IllegalArgumentException("Kapacitet m²/h mora biti > 0");

        // pronađi kolone prema imenima (normaliziraj nazive)
        int idxPredDatum = findColumnIndex(model,
                "preddatumisporuke","plandatumisporuke","planDatumIsporuke","planisporuke","preddatum","datumisporuke","plan");
        int idxNeto = findColumnIndex(model, "neto","net");
        int idxKom = findColumnIndex(model, "kom","kolicina","kolicina_kom","kolicina_komada","kolicina_kom.","komada","qty","quantity");
        int idxStatus = findColumnIndex(model, "status","stanje");
        int idxM2 = findColumnIndex(model, "m2","m^2","m²","površina","povrsina"," površina");
        int idxEndTime = findColumnIndex(model, "endtime","end_time","end time","end","vrijemezavrsetka","vrijeme_zavrsetka","završetak","zavrsetak","zavrseno");

        // ako koji nije pronađen, postavi fallback indekse (stari kod očekuje te indekse)
        if (idxPredDatum == -1) idxPredDatum = FALLBACK_PRED_DATUM;
        if (idxNeto == -1) idxNeto = FALLBACK_NETO;
        if (idxKom == -1) idxKom = FALLBACK_KOM;
        if (idxStatus == -1) idxStatus = FALLBACK_STATUS;
        if (idxM2 == -1) idxM2 = FALLBACK_M2;
        if (idxEndTime == -1) idxEndTime = FALLBACK_ENDTIME;

        // cllection for per-day produced m2 when status == finished
        Map<LocalDate, Double> m2PoDanuIzradjeno = new HashMap<>();

        double totalKom = 0, totalM2 = 0, totalNeto = 0;
        double komIzr = 0, m2Izr = 0, netoIzr = 0;
        double komZai = 0, m2Zai = 0, netoZai = 0;

        int rows = model.getRowCount();
        for (int r = 0; r < rows; r++) {
            double kom = safeToDouble(getModelValue(model, r, idxKom));
            double m2 = safeToDouble(getModelValue(model, r, idxM2));
            double neto = safeToDouble(getModelValue(model, r, idxNeto));
            Object statusObj = getModelValue(model, r, idxStatus);
            String status = statusObj == null ? "" : statusObj.toString().trim().toLowerCase(Locale.ROOT);

            totalKom += kom; totalM2 += m2; totalNeto += neto;

            if (status.equals("izrađeno") || status.equals("izradeno") || status.equals("završeno") || status.equals("finished")) {
                komIzr += kom; m2Izr += m2; netoIzr += neto;
                // uzmi datum iz endTime, fallback na predDatum
                LocalDate datum = toDate(getModelValue(model, r, idxEndTime));
                if (datum == null) datum = toDate(getModelValue(model, r, idxPredDatum));
                if (datum != null && m2 > 0) {
                    m2PoDanuIzradjeno.merge(datum, m2, Double::sum);
                }
            } else {
                // tretiraj kao za izraditi
                komZai += kom; m2Zai += m2; netoZai += neto;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(KOM, totalKom);
        result.put(M2, totalM2);
        result.put(NETO, totalNeto);

        result.put(KOM_IZR, komIzr);
        result.put(M2_IZR, m2Izr);
        result.put(NETO_IZR, netoIzr);

        result.put(KOM_ZAI, komZai);
        result.put(M2_ZAI, m2Zai);
        result.put(NETO_ZAI, netoZai);

        // prosjek m2 po danu (ako imamo povijesnih dana koristi stvarni prosjek, inače fallback)
        final double radniSatiPoDanu = 8.0;
        double fallbackDaily = m2PoSatu * radniSatiPoDanu;
        double prosjek = m2PoDanuIzradjeno.isEmpty() ? fallbackDaily : (m2Izr / m2PoDanuIzradjeno.size());
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

    // --- helper: pronađi index stupca prema listi mogućih naziva (normalizira)
    private static int findColumnIndex(TableModel model, String... possibleNames) {
        if (model == null) return -1;
        int cols = model.getColumnCount();
        for (int c = 0; c < cols; c++) {
            String colName;
            try {
                Object cn = model.getColumnName(c);
                colName = cn == null ? "" : cn.toString();
            } catch (Exception ex) {
                colName = "";
            }
            String norm = normalize(colName);
            for (String p : possibleNames) {
                if (norm.contains(normalize(p))) return c;
            }
        }
        return -1;
    }

    // normalize helper: lower-case, remove non-alphanumeric
    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9đčćšžμ²^]+", "");
    }

    // get value safely (returns null if index out of bounds)
    private static Object getModelValue(TableModel model, int row, int col) {
        if (model == null) return null;
        if (row < 0 || row >= model.getRowCount()) return null;
        if (col < 0 || col >= model.getColumnCount()) return null;
        try {
            return model.getValueAt(row, col);
        } catch (Exception ex) {
            return null;
        }
    }

    // safe numeric parsing (handles strings with commas and dots)
    private static double safeToDouble(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        String s = val.toString().trim();
        if (s.isEmpty()) return 0;
        // replace non-digit except comma/dot and minus
        s = s.replaceAll("\\s+", "");
        // If both dot and comma present, assume dot is thousands and comma is decimal (e.g. "1.234,56")
        int dots = countChar(s, '.');
        int commas = countChar(s, ',');
        if (dots > 0 && commas > 0) {
            // remove dots, replace comma with dot
            s = s.replace(".", "").replace(",", ".");
        } else if (commas > 0 && dots == 0) {
            // replace comma with dot
            s = s.replace(",", ".");
        } else {
            // keep as is
        }
        try {
            return Double.parseDouble(s);
        } catch (Exception ex) {
            return 0;
        }
    }
    private static int countChar(String s, char ch) {
        int cnt = 0;
        for (char c : s.toCharArray()) if (c == ch) cnt++;
        return cnt;
    }

    // parsing LocalDate from common formats
    private static LocalDate toDate(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDate) return (LocalDate) val;
        String s = val.toString().trim();
        if (s.isEmpty()) return null;
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
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        };
        for (DateTimeFormatter fmt : fmts) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (Exception ignored) {}
        }
        // final attempt: try to extract yyyy-MM-dd inside string
        try {
            int i = s.indexOf("20");
            if (i >= 0 && s.length() >= i + 10) {
                String candidate = s.substring(i, i + 10);
                return LocalDate.parse(candidate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
        } catch (Exception ignored) {}
        return null;
    }

    // --- radni/dani helperi (ne mijenjano bitno) ---
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

    public static int countWorkingDaysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start))
            return 0;
        int workingDays = 0;
        LocalDate date = start;
        while (!date.isAfter(end)) {
            if (isWorkingDay(date))
                workingDays++;
            date = date.plusDays(1);
        }
        return workingDays;
    }

    public static int countCalendarDaysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start))
            return 0;
        return (int) (end.toEpochDay() - start.toEpochDay()) + 1;
    }

    public static boolean isTodayWorkingDay() {
        return isWorkingDay(LocalDate.now());
    }

    public static int countWorkingDaysInCurrentMonth() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay = today.withDayOfMonth(today.lengthOfMonth());
        return countWorkingDaysBetween(firstDay, lastDay);
    }

    public static LocalDate calculatePlannedDeliveryDate(int workingDaysFromToday) {
        if (workingDaysFromToday <= 0)
            return LocalDate.now();
        LocalDate today = LocalDate.now();
        int totalDays = 0;
        int workingDaysCounted = 0;
        while (workingDaysCounted < workingDaysFromToday) {
            totalDays++;
            LocalDate date = today.plusDays(totalDays);
            if (isWorkingDay(date))
                workingDaysCounted++;
        }
        return today.plusDays(totalDays);
    }

    public static String calculateAndFormatPlannedDeliveryDate(DefaultTableModel model, double m2PoSatu) {
        if (m2PoSatu <= 0)
            return "";
        Map<String, Object> stats = calculate(model, m2PoSatu);
        Object rdObj = stats.get(RADNI_DANI_PREOSTALO);
        double rd = (rdObj instanceof Number) ? ((Number) rdObj).doubleValue() : 0.0;
        if (rd <= 0)
            return LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        LocalDate plannedDate = dateAfterWorkingDays(rd);
        return plannedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    private static boolean isWorkingDay(LocalDate d) {
        Set<LocalDate> holidays = WorkingTimeCalculator.getHolidaysForYear(d.getYear());
        return !(d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY || holidays.contains(d));
    }
}