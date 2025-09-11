package excel;

import model.AggregatedConsumption;
import model.SalesRow;

import java.time.LocalDate;
import java.util.*;

/**
 * Agregira prodaju/proizvodnju po artiklu za zadani period.
 */
public final class SalesAggregator {
    private SalesAggregator() {}

    /**
     * @param rows           lista redova (prodaja + proizvodnja)
     * @param from           uključivo
     * @param to             uključivo
     * @param groupByCodeOnly true = grupiraj samo po šifri (ako postoji), inače (šifra|naziv)
     */
    public static java.util.List<AggregatedConsumption> aggregateByItem(
            java.util.List<SalesRow> rows,
            LocalDate from,
            LocalDate to,
            boolean groupByCodeOnly
    ) {
        if (rows == null || rows.isEmpty()) return java.util.List.of();
        Map<String, AggregatedConsumption> map = new LinkedHashMap<>();

        for (SalesRow r : rows) {
            // Filter po datumu: ako datum postoji, mora biti u [from..to]. Ako je null, uključujemo.
            LocalDate d = r.getDatum();
            if (d != null && (d.isBefore(from) || d.isAfter(to))) continue;

            String sifra = safe(r.getSifra());
            String naziv = safe(r.getNaziv());
            double qty = r.getKolicina();

            String key;
            if (groupByCodeOnly && !sifra.isEmpty()) key = "CODE:" + sifra;
            else key = (sifra.isEmpty() ? "" : ("CODE:" + sifra + "|")) + "NAME:" + naziv.toLowerCase(Locale.ROOT);

            AggregatedConsumption ac = map.get(key);
            if (ac == null) {
                // naziv zadržavamo točno kako je došao prvi put
                String displayName = naziv;
                String displayCode = sifra;
                ac = new AggregatedConsumption(displayCode, displayName);
                map.put(key, ac);
            }
            ac.add(qty, d);
        }

        return new ArrayList<>(map.values());
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
}