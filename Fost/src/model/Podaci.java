package model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

public class Podaci {

    private LocalDate datumNarudzbe; // ✅ Promijenjeno iz String u LocalDate
    private LocalDate predDatumIsporuke; // ✅ Promijenjeno iz String u LocalDate
    private KomitentInfo komitentInfo;    // ✅ Sada koristimo posebnu klasu umjesto 2 polja
    private String nazivRobe; // Naziv robe
    private double netoVrijednost; //
    private int kom;// količina u komadima
    private String status; // status narudžbe
    private int mm; // milimetri
    private int m; // metri
    private int tisucl; // tisućice
    private double m2; // kvadratura
    private LocalTime startTime; // vrijeme početka
    private LocalTime endTime; // vrijeme završetka
    private Duration duration; // trajanje
    
   
    public Podaci(LocalDate datumNarudzbe, LocalDate predDatumIsporuke, KomitentInfo komitentInfo,
                  String nazivRobe, double netoVrijednost, int kom, String status,
                  int mm, int m, int tisucl, double m2,
                  LocalTime startTime, LocalTime endTime, Duration duration) {
        this.datumNarudzbe = datumNarudzbe;
        this.predDatumIsporuke = predDatumIsporuke;
        this.komitentInfo = komitentInfo;
        this.nazivRobe = nazivRobe;
        this.netoVrijednost = netoVrijednost;
        this.kom = kom;
        this.status = status;
        this.mm = mm;
        this.m = m;
        this.tisucl = tisucl;
        this.m2 = m2;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = duration;
    }

    public LocalDate getDatumNarudzbe() { return datumNarudzbe; }
    public void setDatumNarudzbe(LocalDate datumNarudzbe) { this.datumNarudzbe = datumNarudzbe; }

    public LocalDate getPredDatumIsporuke() { return predDatumIsporuke; }
    public void setPredDatumIsporuke(LocalDate predDatumIsporuke) { this.predDatumIsporuke = predDatumIsporuke; }

    public KomitentInfo getKomitentInfo() { return komitentInfo; }
    public void setKomitentInfo(KomitentInfo komitentInfo) { this.komitentInfo = komitentInfo; }

    public String getNazivRobe() { return nazivRobe; }
    public void setNazivRobe(String nazivRobe) { this.nazivRobe = nazivRobe; }

    public double getNetoVrijednost() { return netoVrijednost; }
    public void setNetoVrijednost(double netoVrijednost) { this.netoVrijednost = netoVrijednost; }

    public int getKom() { return kom; }
    public void setKom(int kom) { this.kom = kom; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getMm() { return mm; }
    public void setMm(int mm) { this.mm = mm; }

    public int getM() { return m; }
    public void setM(int m) { this.m = m; }

    public int getTisucl() { return tisucl; }
    public void setTisucl(int tisucl) { this.tisucl = tisucl; }

    public double getM2() { return m2; }
    public void setM2(double m2) { this.m2 = m2; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public Duration getDuration() { return duration; }
    public void setDuration(Duration duration) { this.duration = duration; }

    @Override
    public String toString() {
        return "Podaci{" +
                "datumNarudzbe=" + datumNarudzbe +
                ", predDatumIsporuke=" + predDatumIsporuke +
                ", komitentInfo=" + komitentInfo +
                ", nazivRobe='" + nazivRobe + '\'' +
                ", netoVrijednost=" + netoVrijednost +
                ", kom=" + kom +
                ", status='" + status + '\'' +
                ", mm=" + mm +
                ", m=" + m +
                ", tisucl=" + tisucl +
                ", m2=" + m2 +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", duration=" + duration +
                '}';
    }
}
