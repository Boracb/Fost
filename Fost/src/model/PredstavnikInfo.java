package model;

/**
 * Jednostavan model za trgovačkog predstavnika (ID + naziv).
 */
public class PredstavnikInfo {
    private final int id;
    private final String naziv;

    public PredstavnikInfo(int id, String naziv) {
        this.id = id;
        this.naziv = naziv;
    }

    public int getId() { return id; }
    public String getNaziv() { return naziv; }

    @Override
    public String toString() {
        return "PredstavnikInfo{" + "id=" + id + ", naziv='" + naziv + '\'' + '}';
    }
}