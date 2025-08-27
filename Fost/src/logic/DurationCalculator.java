package logic;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * ⏳ Pomoćna klasa za izračun trajanja između dva datuma/vremena.
 * 
 * Metoda calculateDuration:
 *  - Prima početni i završni datum/vrijeme kao stringove
 *  - Normalizira i parsira ih u LocalDateTime
 *  - Računa razliku u satima i minutama
 *  - Vraća rezultat u formatu "sat X minuta Y"
 */

public class DurationCalculator {

    /**
     * 📏 Izračunava trajanje između dva vremena.
     * 
     * @param startStr  početno vrijeme (string)
     * @param endStr    završno vrijeme (string)
     * @return          format "sat {broj} minuta {broj}" ili "sat 0 minuta 0"
     *                  ako podaci nisu ispravni ili trajanje < 1 minute
     */
	// Indeksi posebnih kolona (0-based)
	// Djelatnik - 0
	// Start Time - 4
	// End Time - 5
	// Duration - 6
	// Format povratnog stringa: "sat X minuta Y"
	// Ako je trajanje manje od 1 minute ili su podaci neispravni, vraća "sat 0 minuta 0"
	// Ako je bilo koji ulaz null ili prazan, vraća "sat 0 minuta 0"
	// Ako je end prije start, vraća "sat 0 minuta 0"
    public static String calculateDuration(String startStr, String endStr) {
        // 🛡 Provjera null vrijednosti — odmah vraća "0 sati 0 minuta"
        if (startStr == null || endStr == null) return "sat 0 minuta 0";

        // 🔄 Normalizacija formata datuma/vremena pomoću DateUtils
        String startNorm = DateUtils.normalize(startStr);
        String endNorm   = DateUtils.normalize(endStr);

        // Ako nakon normalizacije ostane prazan string — prekid
        if (startNorm.isBlank() || endNorm.isBlank()) return "sat 0 minuta 0";

        // 📅 Parsiranje normaliziranih stringova u LocalDateTime
        LocalDateTime start = DateUtils.parse(startNorm);
        LocalDateTime end   = DateUtils.parse(endNorm);

        // 🛡 Provjera: null vrijednosti ili završno vrijeme prije početnog
        if (start == null || end == null || end.isBefore(start)) {
            return "sat 0 minuta 0";
        }

        // ⏱ Izračun ukupnog trajanja u minutama
        long totalMinutes = ChronoUnit.MINUTES.between(start, end);
        if (totalMinutes < 1) return "sat 0 minuta 0";

        // Pretvaranje ukupnih minuta u sate i minute
        long hours   = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        // 📤 Vraćanje trajanja u traženom formatu
        return String.format("sat %d minuta %d", hours, minutes);
    }
}
