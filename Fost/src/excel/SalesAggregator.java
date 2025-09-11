package excel;

import model.AggregatedConsumption;
import model.SalesRow;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Agregacija potrošnje iz više izvora (prodaja + proizvodnja).
 * Podržava grupiranje:
 * - groupByCodeOnly = true  -> grupiranje striktno po Šifri (ako šifra prazna, koristi Naziv kao ključ)
 * - groupByCodeOnly = false -> grupiranje po ("Šifra || Naziv") da razlikuje varijante naziva
 */
public final class SalesAggregator {

    private SalesAggregator() {}

    public static List<AggregatedConsumption> aggregateByItem(List<SalesRow> rows, LocalDate from, LocalDate toInclusive) {
        return aggregateByItem(rows, from, toInclusive, true);
    }

    public static List<AggregatedConsumption> aggregateByItem(List<SalesRow> rows, LocalDate from, LocalDate toInclusive, boolean groupByCodeOnly) {
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        if (from == null || toInclusive == null) return Collections.emptyList();

        LocalDate fromDate = from;
        LocalDate toDate = toInclusive;
        if (toDate.isBefore(fromDate)) {
            LocalDate tmp = fromDate; fromDate = toDate; toDate = tmp;
        }
        final int days = Math.max(1, (int) ChronoUnit.DAYS.between(fromDate, toDate) + 1);

        Map<String, Agg> map = new LinkedHashMap<>();

        for (SalesRow r : rows) {
            if (r.getDatum() == null) continue;
            if (r.getDatum().isBefore(fromDate) || r.getDatum().isAfter(toDate)) continue;

            String sifra = safe(r.getSifra());
            String naziv = safe(r.getNaziv());

            String key;
            if (groupByCodeOnly) {
                key = !sifra.isEmpty() ? ("S:" + sifra.toLowerCase(Locale.ROOT)) : ("N:" + naziv.toLowerCase(Locale.ROOT));
            } else {
                key = (sifra + "||" + naziv).toLowerCase(Locale.ROOT);
            }

            Agg a = map.computeIfAbsent(key, k -> new Agg(sifra, naziv));
            a.total += r.getKolicina();
        }

        List<AggregatedConsumption> out = new ArrayList<>();
        for (Agg a : map.values()) {
            out.add(new AggregatedConsumption(a.sifra, a.naziv, a.total, days));
        }
        out.sort(Comparator.comparingDouble(AggregatedConsumption::getTotalQty).reversed());
        return out;
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private static final class Agg {
        String sifra, naziv;
        double total;
        Agg(String sifra, String naziv) { this.sifra = sifra; this.naziv = naziv; }
    }
}