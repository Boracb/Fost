package model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * DTO representing aggregated consumption data for a specific item.
 * Holds per-item totals, date range, and provides helper methods for calculations.
 */
public class AggregatedConsumption {
    
    private String sifra;          // Optional item code
    private String naziv;          // Item name (required)
    private double totalQuantity;   // Total consumed quantity
    private LocalDate minDate;     // Earliest sale date (can be null)
    private LocalDate maxDate;     // Latest sale date (can be null)
    private String komitent;       // Client/customer info (optional)
    private Double netoVrijednost; // Net value (optional)
    
    public AggregatedConsumption() {
    }
    
    public AggregatedConsumption(String sifra, String naziv, double totalQuantity, 
                               LocalDate minDate, LocalDate maxDate) {
        this.sifra = sifra;
        this.naziv = naziv;
        this.totalQuantity = totalQuantity;
        this.minDate = minDate;
        this.maxDate = maxDate;
    }
    
    /**
     * @return Total consumed quantity
     */
    public double getTotalQty() {
        return totalQuantity;
    }
    
    /**
     * Calculates average consumption per day based on min and max dates (inclusive).
     * @return Average consumption per day, or 0 if no valid date range
     */
    public double getAvgPerDay() {
        if (minDate == null || maxDate == null || totalQuantity <= 0) {
            return 0.0;
        }
        
        long days = ChronoUnit.DAYS.between(minDate, maxDate) + 1; // +1 for inclusive range
        if (days <= 0) {
            return totalQuantity; // Same day or invalid range
        }
        
        return totalQuantity / days;
    }
    
    /**
     * Estimates annual consumption based on working days.
     * @param radnihDana Number of working days per year (typically 250-260)
     * @return Estimated annual consumption
     */
    public double getAnnualConsumption(int radnihDana) {
        double avgPerDay = getAvgPerDay();
        if (avgPerDay <= 0 || radnihDana <= 0) {
            return 0.0;
        }
        return avgPerDay * radnihDana;
    }
    
    /**
     * Adds consumption data from another entry (for aggregation).
     * @param other Another consumption entry to merge
     */
    public void addConsumption(AggregatedConsumption other) {
        if (other == null) return;
        
        this.totalQuantity += other.totalQuantity;
        
        // Update date range
        if (other.minDate != null) {
            if (this.minDate == null || other.minDate.isBefore(this.minDate)) {
                this.minDate = other.minDate;
            }
        }
        
        if (other.maxDate != null) {
            if (this.maxDate == null || other.maxDate.isAfter(this.maxDate)) {
                this.maxDate = other.maxDate;
            }
        }
        
        // Merge optional fields (prefer non-null values)
        if (this.sifra == null && other.sifra != null) {
            this.sifra = other.sifra;
        }
        if (this.komitent == null && other.komitent != null) {
            this.komitent = other.komitent;
        }
        if (this.netoVrijednost == null && other.netoVrijednost != null) {
            this.netoVrijednost = other.netoVrijednost;
        } else if (this.netoVrijednost != null && other.netoVrijednost != null) {
            this.netoVrijednost += other.netoVrijednost;
        }
    }
    
    // Getters and setters
    public String getSifra() { return sifra; }
    public void setSifra(String sifra) { this.sifra = sifra; }
    
    public String getNaziv() { return naziv; }
    public void setNaziv(String naziv) { this.naziv = naziv; }
    
    public double getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(double totalQuantity) { this.totalQuantity = totalQuantity; }
    
    public LocalDate getMinDate() { return minDate; }
    public void setMinDate(LocalDate minDate) { this.minDate = minDate; }
    
    public LocalDate getMaxDate() { return maxDate; }
    public void setMaxDate(LocalDate maxDate) { this.maxDate = maxDate; }
    
    public String getKomitent() { return komitent; }
    public void setKomitent(String komitent) { this.komitent = komitent; }
    
    public Double getNetoVrijednost() { return netoVrijednost; }
    public void setNetoVrijednost(Double netoVrijednost) { this.netoVrijednost = netoVrijednost; }
    
    @Override
    public String toString() {
        return "AggregatedConsumption{" +
                "sifra='" + sifra + '\'' +
                ", naziv='" + naziv + '\'' +
                ", totalQuantity=" + totalQuantity +
                ", minDate=" + minDate +
                ", maxDate=" + maxDate +
                ", komitent='" + komitent + '\'' +
                ", netoVrijednost=" + netoVrijednost +
                '}';
    }
}