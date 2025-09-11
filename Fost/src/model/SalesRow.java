package model;

import java.time.LocalDate;

/**
 * Redak potrošnje: koristi se i za prodaju i za proizvodnju.
 * Za agregaciju koristimo Datum, Šifra, Naziv i Količina.
 */
public class SalesRow {
    private final LocalDate datum;
    private final String sifra;
    private final String naziv;
    private final double kolicina;

    public SalesRow(LocalDate datum, String sifra, String naziv, double kolicina) {
        this.datum = datum;
        this.sifra = sifra != null ? sifra.trim() : "";
        this.naziv = naziv != null ? naziv.trim() : "";
        this.kolicina = kolicina;
    }

    public LocalDate getDatum() { return datum; }
    public String getSifra() { return sifra; }
    public String getNaziv() { return naziv; }
    public double getKolicina() { return kolicina; }
}