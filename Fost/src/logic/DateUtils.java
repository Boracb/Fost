package logic;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 🕒 Pomoćna klasa za rad s datumima i vremenom.
 *  - Formatiranje LocalDateTime objekata u string
 *  - Parsiranje stringova u LocalDateTime
 *  - Normalizacija formata datuma/vremena
 */
public class DateUtils {
// --- Formati datuma i vremena ---
    // 📅 Osnovni format datuma i vremena: dan.mjesec.godina sati:minute
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    // 📅 Alternativni (fallback) formati — podrška za slične zapise
    private static final DateTimeFormatter[] FALLBACK_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("dd.MM.yyyy. HH:mm"), // s točkom nakon godine
            DateTimeFormatter.ofPattern("dd.MM.yy HH:mm"),    // skraćena godina bez točke
            DateTimeFormatter.ofPattern("dd.MM.yy. HH:mm")    // skraćena godina s točkom
    };

    /**
     * 📤 Formatira LocalDateTime objekt u string koristeći osnovni FORMAT.
     * @param dateTime datum/vrijeme za formatiranje
     * @return formatirani string ili prazan string ako je null
     */
    public static String format(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(FORMAT) : "";
    }

    /**
     * 📤 Formatira bez sekundi — ovdje ista logika kao format() jer FORMAT nema sekunde.
     * @param dateTime datum/vrijeme
     * @return formatirani string ili prazan string
     */
    public static String formatWithoutSeconds(LocalDateTime dateTime) {
        return format(dateTime);
    }

    /**
     * 📥 Pokušava parsirati string u LocalDateTime objekt.
     *    Najprije koristi osnovni FORMAT, a ako ne uspije — prolazi kroz fallback formate.
     * @param dateTimeStr ulazni string datuma/vremena
     * @return LocalDateTime objekt ili null ako parsiranje nije uspjelo
     */
    
    public static LocalDateTime parse(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) return null;
        String s = dateTimeStr.trim();
        try {
            return LocalDateTime.parse(s, FORMAT);
        } catch (DateTimeParseException ignored) {}
        for (DateTimeFormatter f : FALLBACK_FORMATS) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    /**
     * 🔄 Normalizira datum/vrijeme u standardni FORMAT neovisno o ulaznom obliku (ako je prepoznat).
     * @param dateTimeStr ulazni string
     * @return formatirani string ili prazan string ako parsiranje nije uspjelo
     */
    // Normalizacija znači: ako je ulazni string valjan datum/vrijeme u nekom od podržanih formata,
    public static String normalize(String dateTimeStr) {
        LocalDateTime dt = parse(dateTimeStr);
        return dt != null ? format(dt) : "";
    }
}
