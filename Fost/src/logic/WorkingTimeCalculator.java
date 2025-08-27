package logic;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 🕒 Pomoćna klasa za izračun ukupnog radnog vremena između dva datuma/vremena.
 * U obzir uzima:
 *  - Radno vrijeme (07:00 - 15:00)
 *  - Vikende (subota, nedjelja)
 *  - Neradne dane/blagdane u Hrvatskoj
 */
public class WorkingTimeCalculator {

    // ⏰ Početak i kraj radnog vremena
    private static final LocalTime WORK_START = LocalTime.of(7, 0);
    private static final LocalTime WORK_END   = LocalTime.of(15, 0);

    // Cache za već izračunate blagdane po godini
    private static final Map<Integer, Set<LocalDate>> HOLIDAY_CACHE = new HashMap<>();

    /**
     * 📏 Izračun radnog trajanja između dva vremena, unutar definiranog radnog vremena.
     * @param startStr početni datum/vrijeme (string)
     * @param endStr   završni datum/vrijeme (string)
     * @return         format "sat X minuta Y"
     */
    public static String calculateWorkingDuration(String startStr, String endStr) {
        // Normalizacija formata datuma/vremena
        String startNorm = DateUtils.normalize(startStr);
        String endNorm   = DateUtils.normalize(endStr);

        // Parsiranje u LocalDateTime
        LocalDateTime start = DateUtils.parse(startNorm);
        LocalDateTime end   = DateUtils.parse(endNorm);

        // Ako su datumi neispravni ili završetak nije nakon početka
        if (start == null || end == null || !end.isAfter(start)) {
            return "sat 0 minuta 0";
        }

        LocalDate startDate = start.toLocalDate();
        LocalDate endDate   = end.toLocalDate();
        long totalMinutes = 0;

        // 📅 Skupljanje svih blagdana između početne i završne godine
        Set<LocalDate> holidays = new HashSet<>();
        for (int y = startDate.getYear(); y <= endDate.getYear(); y++) {
            holidays.addAll(getHolidaysForYear(y));
        }

        // 🔁 Petlja po svim danima u intervalu
        // Za svaki dan provjerava je li radni dan, i ako jest,
        // računa radno vrijeme unutar tog dana
        // uključujući samo radne sate (07:00-15:00)
        // Preskače vikende i blagdane
        // Ako segment počinje ili završava unutar radnog vremena, računa samo taj dio
        // Na kraju zbraja sve radne minute
        // Vraća ukupno radno vrijeme u satima i minutama
        // Primjer: ako je start u 14:00, a kraj u 10:00 sljedećeg dana,
        //	računa 1 sat prvog dana (14:00-15:00)
        //    i 3 sata drugog dana (07:00-10:00), ukupno 4 sata
        // Ako je start i kraj isti dan unutar radnog vremena,
        // računa samo taj interval
        // Ako je start i kraj izvan radnog vremena,
        // računa samo preklapanje s radnim vremenom
        // Ako je cijeli interval izvan radnog vremena,
        // rezultat je 0 sati 0 minuta
        // Ako je interval preko vikenda ili blagdana,
        //	preskače te dane
        // Ako je interval unutar jednog dana koji je neradni,
        // rezultat je 0 sati 0 minuta
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            // Preskoči vikende
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) continue;
            // Preskoči blagdane
            if (holidays.contains(d)) continue;

            // Radni period za taj dan
            LocalDateTime dayStart = LocalDateTime.of(d, WORK_START);
            LocalDateTime dayEnd   = LocalDateTime.of(d, WORK_END);

            // Početak i kraj segmenta unutar tog dana
            LocalDateTime segStart = max(start, dayStart);
            LocalDateTime segEnd   = min(end, dayEnd);

            // Ako ima preklapanja, dodaj trajanje u minutama
            if (segEnd.isAfter(segStart)) {
                totalMinutes += ChronoUnit.MINUTES.between(segStart, segEnd);
            }
        }

        // Pretvorba minuta u sate i minute
        long hours   = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        return String.format("sat %d minuta %d", hours, minutes);
    }

    /** 🔍 Vraća kasniji od dva LocalDateTime objekta */
    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    /** 🔍 Vraća raniji od dva LocalDateTime objekta */
    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    /**
     * 📅 Interna klasa s popisom hrvatskih državnih blagdana
     *    (uključujući pokretne blagdane vezane uz Uskrs).
     */
    public static class HrHolidays {
        static Set<LocalDate> forYear(int year) {
            Set<LocalDate> s = new HashSet<>();

            // Fiksni blagdani
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
            s.add(easterSunday.plusDays(1));   // Uskrsni ponedjeljak
            s.add(easterSunday.plusDays(60));  // Tijelovo

            return s;
        }

        /** 📅 Izračun datuma Uskrsa za određenu godinu */
        // Algoritam Meeus/Jones/Butcher
        // Vraća datum Uskrsa kao LocalDate
        // Primjer: easterSunday(2024) vraća 31. ožujka 2024.
        private static LocalDate easterSunday(int y) {
            int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4;
            int f = (b + 8) / 25, g = (b - f + 1) / 3;
            int h = (19 * a + b - d - g + 15) % 30;
            int i = c / 4, k = c % 4;
            int l = (32 + 2 * e + 2 * i - h - k) % 7;
            int m = (a + 11 * h + 22 * l) / 451;
            int month = (h + l - 7 * m + 114) / 31;
            int day   = ((h + l - 7 * m + 114) % 31) + 1;
            return LocalDate.of(y, month, day);
        }
    }

    /**
     * 📤 Javna metoda za dohvat blagdana izvana (koristi se u drugim klasama)
     */
    // Kešira rezultate po godini radi performansi
    // Ako godina već postoji u mapi, vraća postojeći skup
    // Inače, izračunava nove blagdane i sprema ih u mapu
    
    public static Set<LocalDate> getHolidaysForYear(int year) {
        return HOLIDAY_CACHE.computeIfAbsent(year, HrHolidays::forYear);
    }
}
