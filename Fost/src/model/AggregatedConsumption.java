package model;

public class AggregatedConsumption {
    private final String sifra;
    private final String naziv;
    private final double totalQty;      // Ukupno u periodu
    private final int daysInPeriod;     // Broj dana u periodu (>=1)
    private final double avgDaily;      // Prosjek dnevno

    public AggregatedConsumption(String sifra, String naziv, double totalQty, int daysInPeriod) {
        this.sifra = sifra != null ? sifra.trim() : "";
        this.naziv = naziv != null ? naziv.trim() : "";
        this.totalQty = totalQty;
        this.daysInPeriod = Math.max(1, daysInPeriod);
        this.avgDaily = this.totalQty / this.daysInPeriod;
    }

    public String getSifra() { return sifra; }
    public String getNaziv() { return naziv; }
    public double getTotalQty() { return totalQty; }
    public int getDaysInPeriod() { return daysInPeriod; }
    public double getAvgDaily() { return avgDaily; }

    public double getAnnualConsumption(int radnihDanaUGodini) {
        int days = radnihDanaUGodini <= 0 ? 365 : radnihDanaUGodini;
        return avgDaily * days;
    }
}