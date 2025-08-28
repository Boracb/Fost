package model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import logic.DateUtils;

/**
 * Model Podaci - s dodatnim helperima za predviđeni plan isporuke.
 * 
 * Ovaj update zadržava sve stare značajke klase, a dodaje:
 * - pomoćne metode za dobavljanje/podešavanje predPlanIsporuke kao String (format dd/MM/yyyy)
 *   te fallback parsiranje pomoću DateUtils (ako je potrebno).
 */
public class Podaci {

    private LocalDate datumNarudzbe; // datum narudžbe
    private LocalDate predDatumIsporuke; // originalno predDatumIsporuke (ako postoji)
    private LocalDate predPlanIsporuke; // NOVO: predviđeni plan isporuke
    private KomitentInfo komitentInfo;    // info o komitentu
    private String nazivRobe; // Naziv robe
    private double netoVrijednost;
    private int kom; // količina u komadima
    private String status; // status narudžbe
    private int mm; // milimetri
    private int m; // metri
    private int tisucl; // tisućice
    private double m2; // kvadratura
    private LocalTime startTime; // vrijeme početka
    private LocalTime endTime; // vrijeme završetka
    private Duration duration; // trajanje
    private String trgovackiPredstavnik;

    private static final DateTimeFormatter OUT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public Podaci() {
    }

    public Podaci(LocalDate datumNarudzbe, LocalDate predDatumIsporuke, LocalDate predPlanIsporuke,
                  KomitentInfo komitentInfo, String nazivRobe, double netoVrijednost, int kom, String status,
                  int mm, int m, int tisucl, double m2,
                  LocalTime startTime, LocalTime endTime, Duration duration,
                  String trgovackiPredstavnik) {
        this.datumNarudzbe = datumNarudzbe;
        this.predDatumIsporuke = predDatumIsporuke;
        this.predPlanIsporuke = predPlanIsporuke;
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
        this.trgovackiPredstavnik = trgovackiPredstavnik;
    }

    public LocalDate getDatumNarudzbe() { return datumNarudzbe; }
    public void setDatumNarudzbe(LocalDate datumNarudzbe) { this.datumNarudzbe = datumNarudzbe; }

    public LocalDate getPredDatumIsporuke() { return predDatumIsporuke; }
    public void setPredDatumIsporuke(LocalDate predDatumIsporuke) { this.predDatumIsporuke = predDatumIsporuke; }

    public LocalDate getPredPlanIsporuke() { return predPlanIsporuke; }
    public void setPredPlanIsporuke(LocalDate predPlanIsporuke) { this.predPlanIsporuke = predPlanIsporuke; }

    /**
     * Vraća predPlanIsporuke formatirano kao dd/MM/yyyy ili prazan string ako null.
     */
    public String getPredPlanIsporukeString() {
        return predPlanIsporuke == null ? "" : predPlanIsporuke.format(OUT_DATE);
    }

    /**
     * Pokušava parsirati predPlanIsporuke iz raznih formata.
     * Prihvaća dd/MM/yyyy, dd.MM.yyyy, yyyy-MM-dd i druge formate podržane u DateUtils.parse.
     * Ako parsing ne uspije postavlja null.
     */
    public void setPredPlanIsporukeFromString(String s) {
        if (s == null) {
            this.predPlanIsporuke = null;
            return;
        }
        String trimmed = s.trim();
        if (trimmed.isEmpty()) {
            this.predPlanIsporuke = null;
            return;
        }

        // Prvo pokušaj lokalnog formata dd/MM/yyyy
        try {
            LocalDate ld = LocalDate.parse(trimmed, OUT_DATE);
            this.predPlanIsporuke = ld;
            return;
        } catch (Exception ignored) {}

        // Fallback: pokušaj parsiranja pomoću DateUtils (vrati LocalDateTime -> LocalDate)
        try {
            LocalDateTime dt = DateUtils.parse(trimmed);
            if (dt != null) {
                this.predPlanIsporuke = dt.toLocalDate();
                return;
            }
        } catch (Exception ignored) {}

        // Ako ništa ne radi, pokušaj još sa ISO formatom
        try {
            LocalDate ldIso = LocalDate.parse(trimmed);
            this.predPlanIsporuke = ldIso;
        } catch (Exception ignored) {
            this.predPlanIsporuke = null;
        }
    }

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

    public String getTrgovackiPredstavnik() { return trgovackiPredstavnik; }
    public void setTrgovackiPredstavnik(String trgovackiPredstavnik) { this.trgovackiPredstavnik = trgovackiPredstavnik; }

    @Override
    public String toString() {
        return "Podaci{" +
                "datumNarudzbe=" + datumNarudzbe +
                ", predDatumIsporuke=" + predDatumIsporuke +
                ", predPlanIsporuke=" + predPlanIsporuke +
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
                ", trgovackiPredstavnik='" + trgovackiPredstavnik + '\'' +
                '}';
    }
}