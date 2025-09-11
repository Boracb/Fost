package logic;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Planer nabave temeljen na koeficijentu obrtaja robe.
 *
 * Ulazni parametri:
 * - nazivArtikla                (naziv artikla)
 * - godisnjaPotrosnja           (kom/god)
 * - stanjeZaliha                (kom trenutno na skladištu)
 * - rokIsporukeDana             (lead time u danima)
 * - minimalnaZaliha             (sigurnosna zaliha - "safety stock")
 * - minimalnaKolicinaZaNaruciti (MOQ - minimalna narudžba u kom)
 * - koeficijentObrtaja          (broj obrtaja godišnje; npr. 12 znači mjesečni ciklus)
 * - radnihDanaUGodini           (default 365; po potrebi 250 ako računate radne dane)
 *
 * Izračuni:
 * - dnevna potrošnja = godisnjaPotrosnja / radnihDanaUGodini
 * - točka do-naručivanja (ROP) = minimalnaZaliha + dnevnaPotrošnja * rokIsporukeDana
 * - razmak naručivanja (dani) = radnihDanaUGodini / koeficijentObrtaja (ako > 0, inače default 30)
 * - ciljna ciklusna zaliha = dnevnaPotrošnja * razmakNaručivanja
 * - ciljna maksimalna razina = minimalnaZaliha + ciljna ciklusna zaliha
 * - preporučena narudžba (kom) = max(MOQ, ciljnaMax - stanjeZaliha), ali najmanje 0
 * - kada naručiti:
 *     ako stanjeZaliha <= ROP -> naručiti odmah
 *     inače daysUntilOrder = ceil((stanjeZaliha - ROP) / dnevnaPotrošnja)
 * - očekivani datum narudžbe = danas + daysUntilOrder
 * - očekivani datum dolaska = datumNarudžbe + rokIsporukeDana
 *
 * Napomena:
 * - Ako koeficijentObrtaja <= 0, koristi se default razmak naručivanja od 30 dana.
 * - Ako je godišnja potrošnja 0 ili negativna, nema preporuke za narudžbu.
 */
public final class InventoryTurnoverPlanner {

    private InventoryTurnoverPlanner() {}

    public static final class Params {
        public String nazivArtikla;
        public double godisnjaPotrosnja;            // kom/god
        public double stanjeZaliha;                 // kom
        public int rokIsporukeDana;                 // dani
        public double minimalnaZaliha;              // kom (safety stock)
        public double minimalnaKolicinaZaNaruciti;  // kom (MOQ)
        public double koeficijentObrtaja;           // x/god (npr. 12 = mjesečno)
        public int radnihDanaUGodini = 365;         // 365 ili npr. 250

        public Params() {}

        public Params withNazivArtikla(String s) { this.nazivArtikla = s; return this; }
        public Params withGodisnjaPotrosnja(double v) { this.godisnjaPotrosnja = v; return this; }
        public Params withStanjeZaliha(double v) { this.stanjeZaliha = v; return this; }
        public Params withRokIsporukeDana(int v) { this.rokIsporukeDana = v; return this; }
        public Params withMinimalnaZaliha(double v) { this.minimalnaZaliha = v; return this; }
        public Params withMinimalnaKolicinaZaNaruciti(double v) { this.minimalnaKolicinaZaNaruciti = v; return this; }
        public Params withKoeficijentObrtaja(double v) { this.koeficijentObrtaja = v; return this; }
        public Params withRadnihDanaUGodini(int v) { this.radnihDanaUGodini = v; return this; }
    }

    public static final class Plan {
        public final String nazivArtikla;
        public final double dnevnaPotrosnja;          // kom/dan
        public final double reorderPoint;             // ROP
        public final double ciljnaCiklusnaZaliha;     // kom
        public final double ciljnaMaxRazina;          // kom
        public final boolean narucitiOdmah;           // true -> naručiti danas
        public final int danaDoNabave;                // 0 ako odmah
        public final double preporucenaNarudzbaKom;   // kom (>= MOQ)
        public final LocalDate datumNarudzbe;         // danas + danaDoNabave
        public final LocalDate ocekivaniDolazak;      // datumNarudzbe + rokIsporukeDana
        public final double procijenjeniKoeficijentObrtaja; // na temelju ciljne max i min zalihe

        private Plan(String nazivArtikla,
                     double dnevnaPotrosnja,
                     double reorderPoint,
                     double ciljnaCiklusnaZaliha,
                     double ciljnaMaxRazina,
                     boolean narucitiOdmah,
                     int danaDoNabave,
                     double preporucenaNarudzbaKom,
                     LocalDate datumNarudzbe,
                     LocalDate ocekivaniDolazak,
                     double procijenjeniKoeficijentObrtaja) {
            this.nazivArtikla = nazivArtikla;
            this.dnevnaPotrosnja = dnevnaPotrosnja;
            this.reorderPoint = reorderPoint;
            this.cijlnaZastita(); // no-op for readability
            this.ciljnaCiklusnaZaliha = ciljnaCiklusnaZaliha;
            this.ciljnaMaxRazina = ciljnaMaxRazina;
            this.narucitiOdmah = narucitiOdmah;
            this.danaDoNabave = danaDoNabave;
            this.preporucenaNarudzbaKom = preporucenaNarudzbaKom;
            this.datumNarudzbe = datumNarudzbe;
            this.ocekivaniDolazak = ocekivaniDolazak;
            this.procijenjeniKoeficijentObrtaja = procijenjeniKoeficijentObrtaja;
        }

        // sitni "anchor" da olakša čitanje field-ova iz debugera
        private void cijlnaZastita() {}

        @Override
        public String toString() {
            return "Plan{" +
                    "nazivArtikla='" + nazivArtikla + '\'' +
                    ", dnevnaPotrosnja=" + round(dnevnaPotrosnja) +
                    ", reorderPoint=" + round(reorderPoint) +
                    ", ciljnaCiklusnaZaliha=" + round(ciljnaCiklusnaZaliha) +
                    ", ciljnaMaxRazina=" + round(ciljnaMaxRazina) +
                    ", narucitiOdmah=" + narucitiOdmah +
                    ", danaDoNabave=" + danaDoNabave +
                    ", preporucenaNarudzbaKom=" + round(preporucenaNarudzbaKom) +
                    ", datumNarudzbe=" + datumNarudzbe +
                    ", ocekivaniDolazak=" + ocekivaniDolazak +
                    ", procijenjeniKoeficijentObrtaja=" + round(procijenjeniKoeficijentObrtaja) +
                    '}';
        }

        private static double round(double v) {
            return Math.round(v * 100.0) / 100.0;
        }
    }

    /**
     * Glavna metoda: računa plan nabave prema koeficijentu obrtaja robe.
     */
    public static Plan compute(Params p) {
        Objects.requireNonNull(p, "Params cannot be null");
        final LocalDate today = LocalDate.now();

        // zaštite
        int daysInYear = p.radnihDanaUGodini <= 0 ? 365 : p.radnihDanaUGodini;
        if (p.godisnjaPotrosnja <= 0) {
            // bez potrošnje nema potrebe naručivati
            return new Plan(
                    p.nazivArtikla,
                    0.0,
                    p.minimalnaZaliha,
                    0.0,
                    p.minimalnaZaliha,
                    false,
                    Integer.MAX_VALUE,
                    0.0,
                    today,
                    today.plusDays(Math.max(0, p.rokIsporukeDana)),
                    0.0
            );
        }

        double dailyUsage = p.godisnjaPotrosnja / daysInYear;
        if (dailyUsage < 0) dailyUsage = 0;

        // Reorder point
        double reorderPoint = p.minimalnaZaliha + dailyUsage * Math.max(0, p.rokIsporukeDana);

        // Razmak naručivanja iz koeficijenta (ako 0 -> default 30 dana)
        double orderIntervalDays = p.koeficijentObrtaja > 0
                ? (double) daysInYear / p.koeficijentObrtaja
                : 30.0;

        // Ciljna ciklusna zaliha i maksimalna razina
        double targetCycleStock = dailyUsage * orderIntervalDays;
        double targetMaxLevel = p.minimalnaZaliha + targetCycleStock;

        // Treba li naručiti odmah?
        boolean orderNow = p.stanjeZaliha <= reorderPoint + 1e-9;

        // Ako ne odmah, za koliko dana?
        int daysUntilOrder;
        if (!orderNow && dailyUsage > 0) {
            double days = (p.stanjeZaliha - reorderPoint) / dailyUsage;
            daysUntilOrder = (int) Math.ceil(Math.max(0.0, days));
        } else {
            daysUntilOrder = 0;
        }

        LocalDate plannedOrderDate = today.plusDays(daysUntilOrder);
        LocalDate expectedArrival = plannedOrderDate.plusDays(Math.max(0, p.rokIsporukeDana));

        // Koliko naručiti do ciljne maksimalne (uz MOQ)
        double recommendedQty = Math.max(0.0, targetMaxLevel - p.stanjeZaliha);
        if (p.minimalnaKolicinaZaNaruciti > 0) {
            recommendedQty = Math.max(recommendedQty, p.minimalnaKolicinaZaNaruciti);
        }

        // Procjena koeficijenta iz ciljanih zaliha (prosjek ~ (min + max)/2)
        double avgInventory = (p.minimalnaZaliha + targetMaxLevel) / 2.0;
        double estimatedK = avgInventory > 0 ? p.godisnjaPotrosnja / avgInventory : 0.0;

        return new Plan(
                p.nazivArtikla,
                dailyUsage,
                reorderPoint,
                targetCycleStock,
                targetMaxLevel,
                orderNow,
                daysUntilOrder,
                recommendedQty,
                plannedOrderDate,
                expectedArrival,
                estimatedK
        );
    }
}