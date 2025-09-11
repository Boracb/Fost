package logic;

import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Kalkulator proizvodnih statistika.
 * Sada podržava TableModel i automatsko pronalaženje stupaca po nazivima zaglavlja.
 *
 * Važno:
 * - Za rokove i preostalu proizvodnju status "izrađeno" se NE uračunava.
 * - Stara metoda calculate(DefaultTableModel, double) ostaje kao prije (bez planStart/planEnd, KD od "danas").
 * - Nova metoda s StartMode dodaje planStart/planEnd i računa KD od izabranog početka.
 * - Pravilo 10:00: ako je početak planiranja nakon 10:00, prvi dan planirane isporuke pomiče se na idući radni dan 07:00.
 *   Ako je prije ili točno u 10:00, računa se s istim danom.
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

    // Novi ključevi za planirani raspored
    public static final String PLAN_START = "planStart";
    public static final String PLAN_END = "planEnd";

    // očekivani default/fallback indeksi (0-based)
    private static final int FALLBACK_PRED_DATUM = 1;
    private static final int FALLBACK_NETO = 4;
    private static final int FALLBACK_KOM = 5;
    private static final int FALLBACK_STATUS = 6;
    private static final int FALLBACK_M2 = 11;
    private static final int FALLBACK_ENDTIME = 13;

    // Radno vrijeme (07:00–15:00), 8h/dan
    private static final LocalTime WORK_START = LocalTime.of(7, 0);
    private static final LocalTime WORK_END = LocalTime.of(15, 0);
    private static final int WORK_DAY_MIN = 8 * 60;

    // Cutoff pravilo: ako je početak nakon ove ure, prvi dan planirane isporuke ide na idući radni dan
    private static final LocalTime DELIVERY_CUTOFF = LocalTime.of(10, 0);

    public enum StartMode {
        NOW,
        TOMORROW_7
    }

    /**
     * STARA metoda (backward-compatible) – "kao što je bilo":
     * - Fiksni indeksi i status logika
     * - Prosjek iz izrađenog po danima (kolona 1)
     * - KD od danas (countCalendarDaysFromToday)
     * - NE dodaje PLAN_START/PLAN_END
     */

 // Java
 public static Map<String, Object> calculate(DefaultTableModel model, double m2PoSatu) {
     if (m2PoSatu <= 0) throw new IllegalArgumentException("Kapacitet m²/h > 0");

     // Fiksni indeksi "kao prije"
     final int IDX_PRED_DATUM = 1;
     final int IDX_NETO = 4;
     final int IDX_KOM = 5;
     final int IDX_STATUS = 6;
     final int IDX_M2 = 11;

     double totalKom = 0, totalM2 = 0, totalNeto = 0;
     double komIzr = 0, m2Izr = 0, netoIzr = 0;
     double komZai = 0, m2Zai = 0, netoZai = 0;
     Map<LocalDate, Double> m2PoDanuIzradjeno = new HashMap<>();

     for (int r = 0; r < model.getRowCount(); r++) {
         double kom  = toDouble(model.getValueAt(r, IDX_KOM));
         double m2   = toDouble(model.getValueAt(r, IDX_M2));
         double neto = toDouble(model.getValueAt(r, IDX_NETO));
         String status = (model.getValueAt(r, IDX_STATUS) == null) ? "" : model.getValueAt(r, IDX_STATUS).toString();

         totalKom  += kom; totalM2 += m2; totalNeto += neto;
         String statusNorm = status.trim().toLowerCase(Locale.ROOT);

         if (statusNorm.equals("izrađeno")) {
             komIzr += kom; m2Izr += m2; netoIzr += neto;
             // use the defined index instead of a hardcoded 1
             LocalDate datum = toDate(model.getValueAt(r, IDX_PRED_DATUM));
             if (datum != null) m2PoDanuIzradjeno.merge(datum, m2, Double::sum);
         } else if (statusNorm.equals("u izradi") || statusNorm.isEmpty()) {
             komZai += kom; m2Zai += m2; netoZai += neto;
         } else {
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


    /**
     * Nova metoda: prima TableModel, traži stupce po zaglavlju ili koristi fallback indekse.
     * Zadržava "novo" ponašanje i dodaje planStart/planEnd (start: sutra 07:00).
     */
    public static Map<String, Object> calculate(TableModel model, double m2PoSatu) {
        return calculate(model, m2PoSatu, StartMode.TOMORROW_7);
    }

    /**
     * Nova metoda: prima TableModel i StartMode (od sada ili od sutra 07:00).
     * Dodaje u rezultat i planirani početak/završetak te KD od starta.
     * Primjenjuje se pravilo 10:00 (ako je start nakon 10:00, prvi dan planirane isporuke je idući radni dan).
     */
    public static Map<String, Object> calculate(TableModel model, double m2PoSatu, StartMode startMode) {
        if (m2PoSatu <= 0) throw new IllegalArgumentException("Kapacitet m²/h mora biti > 0");

        // pronađi kolone prema imenima (normaliziraj nazive) — koristi TOČNO podudaranje, ne "contains"
        int idxPredDatum = findColumnIndex(model,
                "preddatumisporuke","plandatumisporuke","plandatumisporuke","planisporuke","preddatum","datumisporuke","plan");
        int idxNeto = findColumnIndex(model, "neto","net");
        // KOM: poredaj od najčešćih i specifičnih — "kom" zadnji (točno podudaranje)
        int idxKom = findColumnIndex(model, "kolicina","kolicina_kom","kolicina_komada","komada","qty","quantity","kom");
        int idxStatus = findColumnIndex(model, "status","stanje");
        int idxM2 = findColumnIndex(model, "m2","m^2","m²","povrsina","površina");
        int idxEndTime = findColumnIndex(model, "endtime","end_time","endtime","end","vrijemezavrsetka","vrijeme_zavrsetka","zavrsetak","završetak","zavrseno");

        // fallback indeksi (kao stari raspored)
        if (idxPredDatum == -1) idxPredDatum = FALLBACK_PRED_DATUM;
        if (idxNeto == -1) idxNeto = FALLBACK_NETO;
        if (idxKom == -1) idxKom = FALLBACK_KOM;
        if (idxStatus == -1) idxStatus = FALLBACK_STATUS;
        if (idxM2 == -1) idxM2 = FALLBACK_M2;
        if (idxEndTime == -1) idxEndTime = FALLBACK_ENDTIME;

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
                // datum završetka ako postoji, inače predDatum
                LocalDate datum = toDate(getModelValue(model, r, idxEndTime));
                if (datum == null) datum = toDate(getModelValue(model, r, idxPredDatum));
                if (datum != null && m2 > 0) {
                    m2PoDanuIzradjeno.merge(datum, m2, Double::sum);
                }
            } else {
                // ZA IZRADITI
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

        // prosjek m2 po danu (fallback na m2PoSatu * 8h ako nema povijesti)
        final double radniSatiPoDanu = 8.0;
        double fallbackDaily = m2PoSatu * radniSatiPoDanu;
        double prosjek = m2PoDanuIzradjeno.isEmpty() ? fallbackDaily : (m2Izr / m2PoDanuIzradjeno.size());
        result.put(PROSJEK_M2_PO_DANU, prosjek);

        if (prosjek > 0) {
            // Radni dani potrebni (na razini radnih dana)
            double rd = Math.ceil((m2Zai / prosjek) * 100) / 100.0;

            // Odaberi početak plana prema startMode
            LocalDateTime start = (startMode == StartMode.NOW)
                ? LocalDateTime.now()
                : LocalDate.now().plusDays(1).atTime(7, 0);

            // Normalizacija na radni prozor + primjena pravila 10:00
            start = normalizeStartToWorkingWindow(start);

            // Ukupne radne minute potrebne
            long workMinutes = Math.max(1, Math.round(rd * WORK_DAY_MIN));
            LocalDateTime finish = addWorkingMinutes(start, workMinutes);

            // Kalendarski dani od izabranog starta do završetka
            double kdExact = ChronoUnit.MINUTES.between(start, finish) / (60.0 * 24.0);
            double kd = Math.ceil(kdExact * 100) / 100.0;

            result.put(RADNI_DANI_PREOSTALO, rd);
            result.put(KAL_DANI_PREOSTALO, kd);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            result.put(PLAN_START, dtf.format(start));
            result.put(PLAN_END, dtf.format(finish));
        } else {
            result.put(RADNI_DANI_PREOSTALO, 0.0);
            result.put(KAL_DANI_PREOSTALO, 0.0);
            result.put(PLAN_START, "-");
            result.put(PLAN_END, "-");
        }

        return result;
    }

    // --- helper: pronađi index stupca prema listi mogućih naziva (TOČNO podudaranje) ---
    private static int findColumnIndex(TableModel model, String... possibleNames) {
        if (model == null) return -1;

        // pripremi normalizirane moguće nazive
        List<String> candidates = new ArrayList<>();
        for (String p : possibleNames) candidates.add(normalize(p));

        int cols = model.getColumnCount();
        for (int c = 0; c < cols; c++) {
            String name = null;
            try {
                Object cn = model.getColumnName(c);
                name = (cn == null) ? "" : cn.toString();
            } catch (Exception ignored) {}
            String norm = normalize(name);

            // traži točno podudaranje s bilo kojim kandidatom
            for (String want : candidates) {
                if (norm.equals(want)) return c;
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
        s = s.replaceAll("\\s+", "");
        int dots = countChar(s, '.');
        int commas = countChar(s, ',');
        if (dots > 0 && commas > 0) {
            s = s.replace(".", "").replace(",", ".");
        } else if (commas > 0 && dots == 0) {
            s = s.replace(",", ".");
        }
        try {
            return Double.parseDouble(s);
        } catch (Exception ex) {
            return 0;
        }
    }

    // DODANO: helper za stare pozive (fiksni indeksi koriste toDouble)
    private static double toDouble(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        String s = val.toString().trim();
        if (s.isEmpty()) return 0;
        s = s.replaceAll("\\s+", "");
        int dots = countChar(s, '.');
        int commas = countChar(s, ',');
        if (dots > 0 && commas > 0) {
            s = s.replace(".", "").replace(",", ".");
        } else if (commas > 0 && dots == 0) {
            s = s.replace(",", ".");
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

    // --- radni/dani helperi (zadržano zbog kompatibilnosti) ---
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

    // --- planiranje u okviru radnog vremena ---

    // Normalizira početak na radni prozor (07:00-15:00) i prvi idući radni dan.
    // DODANO: ako je radni dan i sat > 10:00, pomakni na sljedeći radni dan 07:00 (cutoff pravilo).
    private static LocalDateTime normalizeStartToWorkingWindow(LocalDateTime dt) {
        LocalDate d = dt.toLocalDate();
        LocalTime t = dt.toLocalTime();

        // Ako je prije početka radnog vremena, postavi na 07:00
        if (t.isBefore(WORK_START)) t = WORK_START;

        // Ako nije radni dan -> sljedeći radni dan u 07:00
        if (!isWorkingDay(d)) {
            d = d.plusDays(1);
            while (!isWorkingDay(d)) d = d.plusDays(1);
            t = WORK_START;
            return LocalDateTime.of(d, t);
        }

        // Ako je izvan radnog prozora (>= 15:00) -> sljedeći radni dan u 07:00
        if (!t.isBefore(WORK_END)) {
            d = d.plusDays(1);
            while (!isWorkingDay(d)) d = d.plusDays(1);
            t = WORK_START;
            return LocalDateTime.of(d, t);
        }

        // Pravilo 10:00 — ako je nakon 10:00 (strogo), prvi dan planirane isporuke je idući radni dan 07:00
        // Napomena: točno u 10:00 računa se s istim danom.
        if (t.isAfter(DELIVERY_CUTOFF)) {
            d = d.plusDays(1);
            while (!isWorkingDay(d)) d = d.plusDays(1);
            t = WORK_START;
        }

        return LocalDateTime.of(d, t);
    }

    // Dodaje radne minute kroz kalendar (poštuje 07-15, vikende, blagdane)
    private static LocalDateTime addWorkingMinutes(LocalDateTime start, long minutes) {
        LocalDateTime cur = normalizeStartToWorkingWindow(start);
        long remaining = minutes;

        while (remaining > 0) {
            LocalDateTime dayEnd = LocalDateTime.of(cur.toLocalDate(), WORK_END);
            long available = Math.max(0, ChronoUnit.MINUTES.between(cur, dayEnd));

            if (available == 0) {
                // Prelazak na sljedeći radni dan 07:00
                LocalDate d = cur.toLocalDate().plusDays(1);
                while (!isWorkingDay(d)) d = d.plusDays(1);
                cur = LocalDateTime.of(d, WORK_START);
                continue;
            }

            long used = Math.min(available, remaining);
            cur = cur.plusMinutes(used);
            remaining -= used;

            if (remaining > 0) {
                // Preskoči na sljedeći radni dan
                LocalDate d = cur.toLocalDate().plusDays(1);
                while (!isWorkingDay(d)) d = d.plusDays(1);
                cur = LocalDateTime.of(d, WORK_START);
            }
        }
        return cur;
    }
}