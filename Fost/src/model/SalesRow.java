package model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Jedan red prodaje uvezen iz Excela.
 * Ključ: datum, (šifra – opcionalno), naziv, količina.
 */
public class SalesRow {
    private final LocalDate datum;
    private final String sifra;
    private final String naziv;
    private final double kolicina;

    public SalesRow(LocalDate datum, String sifra, String naziv, double kolicina) {
        this.datum = datum;
        this.sifra = sifra == null ? "" : sifra.trim();
        this.naziv = naziv == null ? "" : naziv.trim();
        this.kolicina = kolicina;
    }

    public LocalDate getDatum() { return datum; }
    public String getSifra() { return sifra; }
    public String getNaziv() { return naziv; }
    public double getKolicina() { return kolicina; }

    @Override
    public String toString() {
        return "SalesRow{" +
                "datum=" + datum +
                ", sifra='" + sifra + '\'' +
                ", naziv='" + naziv + '\'' +
                ", kolicina=" + kolicina +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SalesRow)) return false;
        SalesRow that = (SalesRow) o;
        return Double.compare(that.kolicina, kolicina) == 0
                && Objects.equals(datum, that.datum)
                && Objects.equals(sifra, that.sifra)
                && Objects.equals(naziv, that.naziv);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datum, sifra, naziv, kolicina);
    }
}