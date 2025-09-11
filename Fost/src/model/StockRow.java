package model;

public class StockRow {
    private final String sifra;
    private final String nazivArtikla;
    private final String jedinicaMjere;
    private final double kolicina;
    private final double nabavnaCijena;
    private final double nabavnaVrijednost;

    public StockRow(String sifra, String nazivArtikla, String jedinicaMjere,
                    double kolicina, double nabavnaCijena, double nabavnaVrijednost) {
        this.sifra = sifra;
        this.nazivArtikla = nazivArtikla;
        this.jedinicaMjere = jedinicaMjere;
        this.kolicina = kolicina;
        this.nabavnaCijena = nabavnaCijena;
        this.nabavnaVrijednost = nabavnaVrijednost;
    }

    public String getSifra() { return sifra; }
    public String getNazivArtikla() { return nazivArtikla; }
    public String getJedinicaMjere() { return jedinicaMjere; }
    public double getKolicina() { return kolicina; }
    public double getNabavnaCijena() { return nabavnaCijena; }
    public double getNabavnaVrijednost() { return nabavnaVrijednost; }
}