package model;

import java.time.LocalDate;

public class ProductionRow {
    private final LocalDate datum;   // može biti null
    private final String sifra;      // može biti null/prazno
    private final String naziv;      // obavezno
    private final double kolicina;   // obavezno

    // dodatno (nije obavezno, ostavljeno za buduće potrebe)
    private final String komitent;

    public ProductionRow(LocalDate datum, String sifra, String naziv, double kolicina, String komitent) {
        this.datum = datum;
        this.sifra = sifra == null ? "" : sifra.trim();
        this.naziv = naziv == null ? "" : naziv.trim();
        this.kolicina = kolicina;
        this.komitent = komitent == null ? "" : komitent.trim();
    }

    public LocalDate getDatum() { return datum; }
    public String getSifra() { return sifra; }
    public String getNaziv() { return naziv; }
    public double getKolicina() { return kolicina; }
    public String getKomitent() { return komitent; }

    @Override
    public String toString() {
        return "ProductionRow{" +
                "datum=" + datum +
                ", sifra='" + sifra + '\'' +
                ", naziv='" + naziv + '\'' +
                ", kolicina=" + kolicina +
                (komitent.isEmpty() ? "" : (", komitent='" + komitent + '\'')) +
                '}';
    }
}