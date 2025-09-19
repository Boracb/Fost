package logic;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;

/**
 * 🕒 Pomoćna klasa za izračun ukupnog radnog vremena između dva datuma/vremena.
 * U obzir uzima:
 *  - Radno vrijeme (07:00 - 15:00)
 *  - Vikende (subota, nedjelja)
 *  - Neradne dane/blagdane u Hrvatskoj
 *
 * Ova verzija:
 * - zadržava sve stare značajke,
 * - sadrži robusno parsiranje stringova u LocalDateTime (fallback ako DateUtils nije dostupan),
 * - izlaže calculateWorkingMinutes(LocalDateTime, LocalDateTime) koji UI koristi za predviđeni plan isporuke,
 * - kešira blagdane po godini (thread-safe).
 */
public class WorkingTimeCalculator {

    // ⏰ Početak i kraj radnog vremena (možeš prilagoditi po potrebi)
    private static final LocalTime WORK_START = LocalTime.of(7, 0);
    private static final LocalTime WORK_END   = LocalTime.of(15, 0);

    // Keš blagdana po godini (thread-safe)
    private static final Map<Integer, Set<LocalDate>> HOLIDAY_CACHE = new ConcurrentHashMap<>();

    /**
     * Izračun radnog trajanja između dva stringa (pokušava normalizirati/parsirati stringove).
     * @param startStr početni datum/vrijeme (različiti formati podržani)
     * @param endStr završni datum/vrijeme (različiti formati podržani)
     * @return format "sat X minuta Y" (ili "sat 0 minuta 0" ako neuspješno)
     */
    public static String calculateWorkingDuration(String startStr, String endStr) {
        LocalDateTime start = null;
        LocalDateTime end = null;

        // Pokušaj koristiti DateUtils ako je dostupan (refleksijom)
        try {
            Class<?> du = Class.forName("logic.DateUtils");
            try {
                // normalize ako postoji
                try {
                    java.lang.reflect.Method mNorm = du.getMethod("normalize", String.class);
                    startStr = (String) mNorm.invoke(null, startStr);
                    endStr = (String) mNorm.invoke(null, endStr);
                } catch (NoSuchMethodException ignored) { /* ignore */ }

                // parse ako postoji
                try {
                    java.lang.reflect.Method mParse = du.getMethod("parse", String.class);
                    Object ps = mParse.invoke(null, startStr);
                    if (ps instanceof LocalDateTime) start = (LocalDateTime) ps;
                    Object pe = mParse.invoke(null, endStr);
                    if (pe instanceof LocalDateTime) end = (LocalDateTime) pe;
                } catch (NoSuchMethodException ignored) { /* ignore */ }
            } catch (Exception ignored) {
                // if reflection invocation fails, fallback to internal parsing
            }
        } catch (ClassNotFoundException ignored) {
            // DateUtils not present -> fallback parsing below
        } catch (Throwable ignored) {
            // any other reflection problem -> continue with fallback
        }

        // Ako DateUtils nije dao rezultat, pokušaj interno parsirati
        if (start == null) start = tryParseLocalDateTime(startStr);
        if (end == null) end = tryParseLocalDateTime(endStr);

        if (start == null || end == null || !end.isAfter(start)) {
            return "sat 0 minuta 0";
        }

        long totalMinutes = calculateWorkingMinutes(start, end);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format("sat %d minuta %d", hours, minutes);
    }

    /**
     * Robusni izračun radnih minuta između dva LocalDateTime-a.
     * Uklanja potrebu za parsiranjem tekstualnog outputa iz calculateWorkingDuration.
     * @param start početak
     * @param end kraj (ako end <= start vraća 0)
     * @return ukupne radne minute unutar intervala (uzimajući u obzir radno vrijeme, vikende i blagdane)
     */
    public static long calculateWorkingMinutes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0L;
        if (!end.isAfter(start)) return 0L;

        LocalDate startDate = start.toLocalDate();
        LocalDate endDate   = end.toLocalDate();

        long totalMinutes = 0L;

        // Skupljanje blagdana za sve godine u intervalu
        Set<LocalDate> holidays = new HashSet<>();
        for (int y = startDate.getYear(); y <= endDate.getYear(); y++) {
            holidays.addAll(getHolidaysForYear(y));
        }

        // Iteracija po danima uključujući granice
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) continue;
            if (holidays.contains(d)) continue;

            LocalDateTime dayWorkStart = LocalDateTime.of(d, WORK_START);
            LocalDateTime dayWorkEnd   = LocalDateTime.of(d, WORK_END);

            LocalDateTime segStart = max(start, dayWorkStart);
            LocalDateTime segEnd   = min(end, dayWorkEnd);

            if (segEnd.isAfter(segStart)) {
                long minutes = ChronoUnit.MINUTES.between(segStart, segEnd);
                if (minutes > 0) totalMinutes += minutes;
            }
        }

        return totalMinutes;
    }

    /** Vraća kasniji od dva LocalDateTime objekta */
    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return (a.isAfter(b)) ? a : b;
    }

    /** Vraća raniji od dva LocalDateTime objekta */
    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return (a.isBefore(b)) ? a : b;
    }

    /**
     * Pokušaj parsirati razne formate datuma i vremena u LocalDateTime.
     * Podržava: dd.MM.yyyy HH:mm , dd/MM/yyyy HH:mm , d.M.yyyy H:mm , yyyy-MM-dd HH:mm, i varijante bez vremena.
     * Ako nema vremena, vraća LocalDate at 00:00.
     */
    private static LocalDateTime tryParseLocalDateTime(String s) {
        if (s == null) return null;
        String str = s.trim();
        if (str.isEmpty()) return null;

        // Ukloni nepotrebne točke na kraju ili dodatne whitespace-e
        if (str.endsWith(".")) str = str.substring(0, str.length() - 1).trim();

        DateTimeFormatter[] fmts = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("dd.MM.yyyy H:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d.M.yyyy H:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d.M.yyyy HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy H:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/yyyy HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE
        };

        for (DateTimeFormatter fmt : fmts) {
            try {
                // prvo pokušaj parsirati kao LocalDateTime
                try {
                    return LocalDateTime.parse(str, fmt);
                } catch (DateTimeParseException ex) {
                    // pokušaj kao LocalDate i vrati atStartOfDay
                    try {
                        LocalDate ld = LocalDate.parse(str, fmt);
                        return ld.atStartOfDay();
                    } catch (DateTimeParseException ignored) {
                        // nastavi dalje
                    }
                }
            } catch (Exception ignored) {
                // continue
            }
        }
        return null;
    }

    /**
     * Interna klasa s popisom hrvatskih državnih blagdana (uključujući pokretne blagdane vezane uz Uskrs).
     */
    public static class HrHolidays {
        static Set<LocalDate> forYear(int year) {
            Set<LocalDate> s = new HashSet<>();

            // Fiksni blagdani (uvrsti one koji su relevantni)
            s.add(LocalDate.of(year, 1, 1));   // Nova godina
            s.add(LocalDate.of(year, 1, 6));   // Sveta tri kralja
            s.add(LocalDate.of(year, 5, 1));   // Praznik rada
            s.add(LocalDate.of(year, 5, 30));  // Dan državnosti
            s.add(LocalDate.of(year, 6, 22));  // Dan antifašističke borbe
            s.add(LocalDate.of(year, 8, 5));   // Dan pobjede i domovinske zahvalnosti
            s.add(LocalDate.of(year, 8, 15));  // Velika Gospa
            s.add(LocalDate.of(year, 11, 1));  // Svi sveti
            s.add(LocalDate.of(year, 11, 18)); // Dan sjećanja na žrtve Domovinskog rata
            s.add(LocalDate.of(year, 12, 25)); // Božić
            s.add(LocalDate.of(year, 12, 26)); // Sveti Stjepan

            // Pokretni blagdani vezani uz Uskrs
            LocalDate easterSunday = easterSunday(year);
            s.add(easterSunday);                // Uskrs (nedjelja) - obično neradni
            s.add(easterSunday.plusDays(1));    // Uskrsni ponedjeljak
            s.add(easterSunday.plusDays(60));   // Tijelovo (ponekad 60 dana poslije)

            return s;
        }

        /** Izračun datuma Uskrsa (Meeus/Jones/Butcher) */
        private static LocalDate easterSunday(int y) {
            int a = y % 19;
            int b = y / 100;
            int c = y % 100;
            int d = b / 4;
            int e = b % 4;
            int f = (b + 8) / 25;
            int g = (b - f + 1) / 3;
            int h = (19 * a + b - d - g + 15) % 30;
            int i = c / 4;
            int k = c % 4;
            int l = (32 + 2 * e + 2 * i - h - k) % 7;
            int m = (a + 11 * h + 22 * l) / 451;
            int month = (h + l - 7 * m + 114) / 31;
            int day   = ((h + l - 7 * m + 114) % 31) + 1;
            return LocalDate.of(y, month, day);
        }
    }

    /**
     * Javna metoda za dohvat blagdana po godini (keširana).
     */
    public static Set<LocalDate> getHolidaysForYear(int year) {
        return HOLIDAY_CACHE.computeIfAbsent(year, Yr -> HrHolidays.forYear(Yr));
    }

    /**
     * Pomoćna metoda za vanjski test: provjerava je li dan neradni (vikend ili blagdan).
     */
    public static boolean isHolidayOrWeekend(LocalDate d) {
        if (d == null) return false;
        DayOfWeek dow = d.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return true;
        Set<LocalDate> hol = getHolidaysForYear(d.getYear());
        return hol.contains(d);
    }
    
    
}