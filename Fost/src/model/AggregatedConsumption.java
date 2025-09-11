package model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Agregat (po artiklu) za prikaz u tabeli i izračune.
 */
public class AggregatedConsumption {
    private final String sifra;
    private final String naziv;

    private double totalQty;
    private LocalDate minDate;
    private LocalDate maxDate;

    public AggregatedConsumption(String sifra, String naziv) {
        this.sifra = sifra == null ? "" : sifra.trim();
        this.naziv = naziv == null ? "" : naziv.trim();
    }

    public void add(double qty, LocalDate date) {
        this.totalQty += qty;
        if (date != null) {
            if (minDate == null || date.isBefore(minDate)) minDate = date;
            if (maxDate == null || date.isAfter(maxDate))  maxDate = date;
        }
    }

    public String getSifra() { return sifra; }
    public String getNaziv() { return naziv; }
    public double getTotalQty() { return totalQty; }
    public LocalDate getMinDate() { return minDate; }
    public LocalDate getMaxDate() { return maxDate; }

    /**
     * Prosječna dnevna potrošnja na temelju pokrivenih dana (min..max), uključivo.
     * Ako nema datuma, vraća 0 (ne možemo pouzdano računati tempo).
     */
    public double getAvgPerDay() {
        if (minDate == null || maxDate == null) return 0.0;
        long days = ChronoUnit.DAYS.between(minDate, maxDate) + 1;
        if (days <= 0) return 0.0;
        return totalQty / days;
    }

    /**
     * Skalirano na broj radnih dana u godini (npr. 365 ili 250).
     * Računa se iz prosjeka/dan, kako ne bi “nagradio” periode s malo dana.
     */
    public double getAnnualConsumption(int radnihDana) {
        if (radnihDana <= 0) radnihDana = 365;
        return getAvgPerDay() * radnihDana;
    }
}