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

    // NEW: broj dana iz UI prozora (od/do) za slučaj kad nemamo datirane retke
    private int fallbackDays;

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

    // NEW: postavlja fallback broj dana iz UI-a (od/do)
    public void setFallbackWindow(LocalDate from, LocalDate to) {
        if (from != null && to != null && !to.isBefore(from)) {
            this.fallbackDays = (int) (ChronoUnit.DAYS.between(from, to) + 1);
        } else {
            this.fallbackDays = 0;
        }
    }

    public String getSifra() { return sifra; }
    public String getNaziv() { return naziv; }
    public double getTotalQty() { return totalQty; }
    public LocalDate getMinDate() { return minDate; }
    public LocalDate getMaxDate() { return maxDate; }

    /**
     * Prosječna dnevna potrošnja.
     * Primarno koristi span min..max (ako imamo datume),
     * inače pada na fallbackDays (od/do iz UI-a).
     */
    public double getAvgPerDay() {
        long days;
        if (minDate != null && maxDate != null) {
            days = ChronoUnit.DAYS.between(minDate, maxDate) + 1;
        } else if (fallbackDays > 0) {
            days = fallbackDays;
        } else {
            return 0.0;
        }
        if (days <= 0) return 0.0;
        return totalQty / days;
    }

    /**
     * Skalirano na broj radnih dana u godini (npr. 365 ili 250).
     */
    public double getAnnualConsumption(int radnihDana) {
        if (radnihDana <= 0) radnihDana = 365;
        return getAvgPerDay() * radnihDana;
    }
}