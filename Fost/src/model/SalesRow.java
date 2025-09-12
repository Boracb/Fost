package model;

import java.time.LocalDate;

public class SalesRow {
    private final LocalDate datum;        // može biti null
    private final String komitent;        // opcionalno
    private final String sifra;           // može biti null/prazno
    private final String naziv;           // obavezno
    private final double kolicina;        // obavezno
    private final Double netoVrijednost;  // opcionalno

    // Konstruktor usklađen s tvojim pozivom:
    // new SalesRow(datum, komitent, sifra, naziv, kolicina, neto)
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

    // Očekivani naziv u UI-u
    public Double getNetoVrijednost() { return netoVrijednost; }
    // Alias, ako negdje zatreba
    public Double getNeto() { return netoVrijednost; }

    @Override
    public String toString() {
        return "SalesRow{" +
                "datum=" + datum +
                ", komitent='" + komitent + '\'' +
                ", sifra='" + sifra + '\'' +
                ", naziv='" + naziv + '\'' +
                ", kolicina=" + kolicina +
                (netoVrijednost == null ? "" : (", neto=" + netoVrijednost)) +
                '}';
    }
}