package excel;

import model.AggregatedConsumption;
import java.time.LocalDate;
import java.util.*;

/**
 * Utility class for aggregating sales data into consumption summaries.
 * Groups sales rows by item name and optionally by item code.
 */
public class SalesAggregator {
    
    /**
     * Represents a single sales row.
     */
    public static class SalesRow {
        private LocalDate datum;        // Optional date
        private String sifra;          // Optional item code  
        private String naziv;          // Required item name
        private double kolicina;       // Required quantity
        private String komitent;       // Optional client
        private Double netoVrijednost; // Optional net value
        
        public SalesRow(LocalDate datum, String sifra, String naziv, double kolicina) {
            this.datum = datum;
            this.sifra = sifra;
            this.naziv = naziv;
            this.kolicina = kolicina;
        }
        
        public SalesRow(LocalDate datum, String sifra, String naziv, double kolicina, 
                       String komitent, Double netoVrijednost) {
            this(datum, sifra, naziv, kolicina);
            this.komitent = komitent;
            this.netoVrijednost = netoVrijednost;
        }
        
        // Getters and setters
        public LocalDate getDatum() { return datum; }
        public void setDatum(LocalDate datum) { this.datum = datum; }
        
        public String getSifra() { return sifra; }
        public void setSifra(String sifra) { this.sifra = sifra; }
        
        public String getNaziv() { return naziv; }
        public void setNaziv(String naziv) { this.naziv = naziv; }
        
        public double getKolicina() { return kolicina; }
        public void setKolicina(double kolicina) { this.kolicina = kolicina; }
        
        public String getKomitent() { return komitent; }
        public void setKomitent(String komitent) { this.komitent = komitent; }
        
        public Double getNetoVrijednost() { return netoVrijednost; }
        public void setNetoVrijednost(Double netoVrijednost) { this.netoVrijednost = netoVrijednost; }
        
        @Override
        public String toString() {
            return "SalesRow{" +
                    "datum=" + datum +
                    ", sifra='" + sifra + '\'' +
                    ", naziv='" + naziv + '\'' +
                    ", kolicina=" + kolicina +
                    ", komitent='" + komitent + '\'' +
                    ", netoVrijednost=" + netoVrijednost +
                    '}';
        }
    }
    
    /**
     * Aggregates a list of sales rows into consumption summaries.
     * Groups by item name and optionally by item code if available.
     * 
     * @param salesRows List of sales data
     * @return List of aggregated consumption data
     */
    public static List<AggregatedConsumption> aggregateConsumption(List<SalesRow> salesRows) {
        if (salesRows == null || salesRows.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Group by combination of sifra (if available) and naziv
        Map<String, AggregatedConsumption> aggregationMap = new LinkedHashMap<>();
        
        for (SalesRow row : salesRows) {
            if (row.getNaziv() == null || row.getNaziv().trim().isEmpty()) {
                continue; // Skip rows without name (required field)
            }
            
            if (row.getKolicina() <= 0) {
                continue; // Skip rows with zero or negative quantity
            }
            
            // Create aggregation key: use sifra if available, otherwise just naziv
            String key = createAggregationKey(row.getSifra(), row.getNaziv());
            
            AggregatedConsumption existing = aggregationMap.get(key);
            if (existing == null) {
                // Create new aggregation entry
                existing = new AggregatedConsumption();
                existing.setSifra(row.getSifra());
                existing.setNaziv(row.getNaziv().trim());
                existing.setTotalQuantity(row.getKolicina());
                existing.setMinDate(row.getDatum());
                existing.setMaxDate(row.getDatum());
                existing.setKomitent(row.getKomitent());
                existing.setNetoVrijednost(row.getNetoVrijednost());
                
                aggregationMap.put(key, existing);
            } else {
                // Merge with existing entry
                AggregatedConsumption toAdd = new AggregatedConsumption();
                toAdd.setSifra(row.getSifra());
                toAdd.setNaziv(row.getNaziv());
                toAdd.setTotalQuantity(row.getKolicina());
                toAdd.setMinDate(row.getDatum());
                toAdd.setMaxDate(row.getDatum());
                toAdd.setKomitent(row.getKomitent());
                toAdd.setNetoVrijednost(row.getNetoVrijednost());
                
                existing.addConsumption(toAdd);
            }
        }
        
        return new ArrayList<>(aggregationMap.values());
    }
    
    /**
     * Creates a unique aggregation key for grouping items.
     * Uses sifra if available, otherwise falls back to normalized naziv.
     * 
     * @param sifra Item code (optional)
     * @param naziv Item name (required)
     * @return Aggregation key
     */
    private static String createAggregationKey(String sifra, String naziv) {
        String normalizedNaziv = naziv != null ? naziv.trim().toLowerCase() : "";
        
        if (sifra != null && !sifra.trim().isEmpty()) {
            // Use both sifra and naziv for more precise grouping
            return sifra.trim().toLowerCase() + "|" + normalizedNaziv;
        } else {
            // Use only naziv
            return normalizedNaziv;
        }
    }
    
    /**
     * Filters aggregated consumption by date range.
     * 
     * @param aggregations List of aggregated consumption data
     * @param startDate Optional start date (inclusive)
     * @param endDate Optional end date (inclusive)
     * @return Filtered list
     */
    public static List<AggregatedConsumption> filterByDateRange(
            List<AggregatedConsumption> aggregations, 
            LocalDate startDate, 
            LocalDate endDate) {
        
        if (aggregations == null || aggregations.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<AggregatedConsumption> filtered = new ArrayList<>();
        
        for (AggregatedConsumption agg : aggregations) {
            boolean include = true;
            
            // Check if aggregation overlaps with date range
            if (startDate != null && agg.getMaxDate() != null && agg.getMaxDate().isBefore(startDate)) {
                include = false;
            }
            if (endDate != null && agg.getMinDate() != null && agg.getMinDate().isAfter(endDate)) {
                include = false;
            }
            
            if (include) {
                filtered.add(agg);
            }
        }
        
        return filtered;
    }
    
    /**
     * Sorts aggregated consumption data by total quantity (descending).
     * 
     * @param aggregations List to sort (modified in-place)
     */
    public static void sortByQuantityDesc(List<AggregatedConsumption> aggregations) {
        if (aggregations != null) {
            aggregations.sort((a, b) -> Double.compare(b.getTotalQty(), a.getTotalQty()));
        }
    }
    
    /**
     * Sorts aggregated consumption data by item name.
     * 
     * @param aggregations List to sort (modified in-place)
     */
    public static void sortByName(List<AggregatedConsumption> aggregations) {
        if (aggregations != null) {
            aggregations.sort((a, b) -> {
                String nameA = a.getNaziv() != null ? a.getNaziv() : "";
                String nameB = b.getNaziv() != null ? b.getNaziv() : "";
                return nameA.compareToIgnoreCase(nameB);
            });
        }
    }
}