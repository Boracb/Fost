package model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProducedExcelRow {
    private final LocalDateTime datum;       // Datum izrade (iz Excela) - informativno
    private final String komitentOpis;       // "Komitent / opis Šifra"
    private final String nazivRobe;          // "Naziv robe"
    private final int kolicina;              // "količina" (NE koristimo u podudaranju po tvojoj zadnjoj uputi)
    private final BigDecimal netoVrijednost; // "Neto vrijednost" (NE koristimo u podudaranju po tvojoj zadnjoj uputi)

    public ProducedExcelRow(LocalDateTime datum, String komitentOpis, String nazivRobe,
                            int kolicina, BigDecimal netoVrijednost) {
        this.datum = datum;
        this.komitentOpis = safe(komitentOpis);
        this.nazivRobe = safe(nazivRobe);
        this.kolicina = kolicina;
        this.netoVrijednost = netoVrijednost == null ? BigDecimal.ZERO : netoVrijednost;
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    public LocalDateTime getDatum() { return datum; }
    public String getKomitentOpis() { return komitentOpis; }
    public String getNazivRobe() { return nazivRobe; }
    public int getKolicina() { return kolicina; }
    public BigDecimal getNetoVrijednost() { return netoVrijednost; }
}