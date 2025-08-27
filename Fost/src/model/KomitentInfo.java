package model;

import java.util.Objects;

/**
 * 📄 Model klasa "KomitentInfo"
 * Predstavlja osnovne podatke o komitentu i pripadajućem trgovačkom predstavniku.
 */

// --- Polja klase ---
public class KomitentInfo {

    private String komitentOpis;         // 🏢 Opis komitenta
    private String trgovackiPredstavnik; // 🧑‍💼 Trgovački predstavnik

    /** 🔹 Prazan konstruktor (koristi se kod frameworks-a ili ručnog setanja vrijednosti) */
    public KomitentInfo() {
    }

    /**
     * 🔹 Konstruktor s parametrima
     * @param komitentOpis opis komitenta
     * @param trgovackiPredstavnik ime trgovačkog predstavnika
     */
    public KomitentInfo(String komitentOpis, String trgovackiPredstavnik) {
        this.komitentOpis = komitentOpis;
        this.trgovackiPredstavnik = trgovackiPredstavnik;
    }

    public String getKomitentOpis() {
        return komitentOpis;
    }

    public void setKomitentOpis(String komitentOpis) {
        this.komitentOpis = komitentOpis;
    }

    public String getTrgovackiPredstavnik() {
        return trgovackiPredstavnik;
    }

    public void setTrgovackiPredstavnik(String trgovackiPredstavnik) {
        this.trgovackiPredstavnik = trgovackiPredstavnik;
    }

    @Override
    public String toString() {
        return String.format("KomitentInfo{komitentOpis='%s', trgovackiPredstavnik='%s'}",
                komitentOpis, trgovackiPredstavnik);
    }

    @Override
    
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KomitentInfo)) return false;
        KomitentInfo that = (KomitentInfo) o;
        return Objects.equals(komitentOpis, that.komitentOpis) &&
               Objects.equals(trgovackiPredstavnik, that.trgovackiPredstavnik);
    }

    @Override
    public int hashCode() {
        return Objects.hash(komitentOpis, trgovackiPredstavnik);
    }
}
