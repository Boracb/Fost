package model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Jedan red prodaje uvezen iz Excela.
 * Polja koja koristimo za popunjavanje JTable-a:
 * - datum        (OPCIONALNO; može biti null)
 * - komitent     (OPCIONALNO; može biti prazan)
 * - sifra        (OPCIONALNO; nije nužna za prikaz)
 * - naziv        (OBAVEZNO za prikaz)
 * - kolicina     (OBAVEZNO za prikaz)
 * - netoVrijednost (OPCIONALNO)
 */
public class SalesRow {
    private final LocalDate datum;
    private final String komitent;
    private final String sifra;
    private final String naziv;
    private final double kolicina;
    private final Double netoVrijednost;

    public SalesRow(LocalDate datum, String komitent, String sifra, String naziv, double kolicina, Double netoVrijednost) {
        this.datum = datum;
        this.komitent = komitent == null ? "" : komitent.trim();
        this.sifra = sifra == null ? "" : sifra.trim();
        this.naziv = naziv == null ? "" : naziv.trim();
        this.kolicina = kolicina;
        this.netoVrijednost = netoVrijednost;
    }

    public LocalDate getDatum() { return datum; }
    public String getKomitent() { return komitent; }
    public String getSifra() { return sifra; }
    public String getNaziv() { return naziv; }
    public double getKolicina() { return kolicina; }
    public Double getNetoVrijednost() { return netoVrijednost; }

    @Override
    public String toString() {
        return "SalesRow{" +
                "datum=" + datum +
                ", komitent='" + komitent + '\'' +
                ", sifra='" + sifra + '\'' +
                ", naziv='" + naziv + '\'' +
                ", kolicina=" + kolicina +
                ", neto=" + netoVrijednost +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SalesRow)) return false;
        SalesRow that = (SalesRow) o;
        return Double.compare(that.kolicina, kolicina) == 0
                && Objects.equals(datum, that.datum)
                && Objects.equals(komitent, that.komitent)
                && Objects.equals(sifra, that.sifra)
                && Objects.equals(naziv, that.naziv)
                && Objects.equals(netoVrijednost, that.netoVrijednost);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datum, komitent, sifra, naziv, kolicina, netoVrijednost);
    }
}