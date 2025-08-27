package db;

import javax.swing.table.DefaultTableModel;
import logic.WorkingTimeCalculator;
import java.sql.*;
import java.util.List;
import java.util.Map;

/**
 * Pomoćna klasa za rad sa SQLite bazom 'fost.db'.
 * Sadrži metode za inicijalizaciju baze, čuvanje, učitavanje i brisanje podataka.
 */ 
public class DatabaseHelper {

    private static final String DB_URL = "jdbc:sqlite:fost.db";

    /**
     * Inicijalizira bazu:
     * Kreira tablicu 'narudzbe' ako ne postoji sa svim kolonama, uključujući trgovackiPredstavnik.
     */
    public static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
// Provjera postoji li tablica 'narudzbe'
            boolean tableExists;
           
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='narudzbe'");
                 ResultSet rs = ps.executeQuery()) {
                tableExists = rs.next();
            }

            if (!tableExists) {
                try (Statement stmt = conn.createStatement()) {
                    String sql = "CREATE TABLE narudzbe (" +
                            "datumNarudzbe TEXT, " +
                            "predDatumIsporuke TEXT, " +
                            "komitentOpis TEXT, " +
                            "nazivRobe TEXT, " +
                            "netoVrijednost REAL, " +
                            "kom INTEGER, " +
                            "status TEXT, " +
                            "djelatnik TEXT, " +
                            "mm REAL, " +
                            "m REAL, " +
                            "tisucl REAL, " +
                            "m2 REAL, " +
                            "startTime TEXT, " +
                            "endTime TEXT, " +
                            "duration TEXT, " +
                            "trgovackiPredstavnik TEXT" +
                            ")";
                    stmt.execute(sql);
                }
                conn.commit();
                conn.setAutoCommit(true);
                return;
            }

            conn.commit();
            conn.setAutoCommit(true);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    /**
     * Sprema podatke iz DefaultTableModel-a u bazu:
     * Briše sve prethodne zapise iz 'narudzbe', upisuje nove.
     * Radi po imenu kolona iz modela, uključujući trgovackiPredstavnik.
     */
    public static void saveToDatabase(DefaultTableModel model) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement clean = conn.createStatement()) {

            clean.execute("DELETE FROM narudzbe");

            String insert = "INSERT INTO narudzbe (" +
                    "datumNarudzbe, predDatumIsporuke, komitentOpis, nazivRobe, " +
                    "netoVrijednost, kom, status, djelatnik, mm, m, tisucl, m2, " +
                    "startTime, endTime, duration, trgovackiPredstavnik" +
                    ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

            try (PreparedStatement ps = conn.prepareStatement(insert)) {

                // Mapiranje kolona po imenu
                int idxDatumNarudzbe     = model.findColumn("datumNarudzbe");
                int idxPredDatumIsporuke = model.findColumn("predDatumIsporuke");
                int idxKomitentOpis      = model.findColumn("komitentOpis");
                int idxNazivRobe         = model.findColumn("nazivRobe");
                int idxNetoVrijednost    = model.findColumn("netoVrijednost");
                int idxKom               = model.findColumn("kom");
                int idxStatus            = model.findColumn("status");
                int idxDjelatnik         = model.findColumn("djelatnik");
                int idxMm                = model.findColumn("mm");
                int idxM                 = model.findColumn("m");
                int idxTisucl            = model.findColumn("tisucl");
                int idxM2                = model.findColumn("m2");
                int idxStartTime         = model.findColumn("startTime");
                int idxEndTime           = model.findColumn("endTime");
                int idxDuration          = model.findColumn("duration");
                int idxTrgovacki         = model.findColumn("trgovackiPredstavnik");

                for (int r = 0; r < model.getRowCount(); r++) {
                    ps.setString(1,  (String) model.getValueAt(r, idxDatumNarudzbe));
                    ps.setString(2,  (String) model.getValueAt(r, idxPredDatumIsporuke));
                    ps.setString(3,  (String) model.getValueAt(r, idxKomitentOpis));
                    ps.setString(4,  (String) model.getValueAt(r, idxNazivRobe));

                    setNullableDouble(ps, 5, model.getValueAt(r, idxNetoVrijednost));
                    setNullableInteger(ps, 6, model.getValueAt(r, idxKom));

                    ps.setString(7,  (String) model.getValueAt(r, idxStatus));
                    ps.setString(8,  (String) model.getValueAt(r, idxDjelatnik));

                    setNullableDouble(ps, 9,  model.getValueAt(r, idxMm));
                    setNullableDouble(ps, 10, model.getValueAt(r, idxM));
                    setNullableDouble(ps, 11, model.getValueAt(r, idxTisucl));
                    setNullableDouble(ps, 12, model.getValueAt(r, idxM2));

                    ps.setString(13, (String) model.getValueAt(r, idxStartTime));
                    ps.setString(14, (String) model.getValueAt(r, idxEndTime));
                    ps.setString(15, (String) model.getValueAt(r, idxDuration));
                    ps.setString(16, (String) model.getValueAt(r, idxTrgovacki));

                    ps.addBatch();
                }
                ps.executeBatch();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Učitava sve podatke iz baze u DefaultTableModel, sada čita i trgovackiPredstavnik
     * direktno iz baze.
     */

 // Java 
 public static void loadFromDatabase(DefaultTableModel model) {
     String select = "SELECT * FROM narudzbe";
     Map<String, String> komitentMap = loadKomitentPredstavnikMap();
     try (Connection conn = DriverManager.getConnection(DB_URL);
          Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(select)) {

         model.setRowCount(0);

         while (rs.next()) {
             Object[] row = new Object[model.getColumnCount()];
             for (int c = 0; c < model.getColumnCount(); c++) {
                 String colName = model.getColumnName(c);
                 row[c] = rs.getObject(colName);
             }
             // Fill trgovackiPredstavnik if empty
             // assuming komitentOpis column exists
             // 
             int idxKomitentOpis = model.findColumn("komitentOpis");
             int idxTrgovackiPredstavnik = model.findColumn("trgovackiPredstavnik");
             if (row[idxTrgovackiPredstavnik] == null || row[idxTrgovackiPredstavnik].toString().isBlank()) {
                 String komitentOpis = row[idxKomitentOpis] == null ? "" : row[idxKomitentOpis].toString();
                 row[idxTrgovackiPredstavnik] = komitentMap.getOrDefault(komitentOpis, "");
             }
             model.addRow(row);
         }

     } catch (SQLException e) {
         e.printStackTrace();
     }
 }

    public static List<String> loadAllKomitenti() {
        List<String> lista = new java.util.ArrayList<>();
        String sql = "SELECT DISTINCT komitentOpis FROM narudzbe " +
                     "WHERE komitentOpis IS NOT NULL AND komitentOpis <> '' " +
                     "ORDER BY komitentOpis";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return lista;
    }

	/**
	 * Ažurira trajanje (duration) za zadani red u modelu na osnovu startTime i
	 * endTime. Ako su start ili end prazni, duration se postavlja na prazan string.
	 * Inače, koristi WorkingTimeCalculator za izračun trajanja.
	 */
    public static void updateDurationForRow(DefaultTableModel model, int row) {
        String start = (String) model.getValueAt(row, model.findColumn("startTime"));
        String end   = (String) model.getValueAt(row, model.findColumn("endTime"));
   
        int idxDuration = model.findColumn("duration");
// Ako su start ili end null/blank, duration postavi na prazan string
        if (start == null || end == null || start.isBlank() || end.isBlank()) {
            model.setValueAt("", row, idxDuration);
            return;
        }
        // Inače, izračunaj trajanje
        String formatted = WorkingTimeCalculator.calculateWorkingDuration(start, end);
        model.setValueAt(formatted, row, idxDuration);
    }
// Briše red iz baze na osnovu datumNarudzbe i nazivRobe
    public static void deleteRow(String datumNarudzbe, String nazivRobe) {
        String sql = "DELETE FROM narudzbe WHERE datumNarudzbe = ? AND nazivRobe = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, datumNarudzbe);
            ps.setString(2, nazivRobe);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
  
// Pomoćne metode za postavljanje nullable vrijednosti u PreparedStatement
    private static void setNullableDouble(PreparedStatement ps, int index, Object val) throws SQLException {
        if (val == null || (val instanceof String && ((String) val).isBlank())) {
            ps.setNull(index, Types.REAL);
        } else if (val instanceof Number) {
            ps.setDouble(index, ((Number) val).doubleValue());
        } else {
            ps.setDouble(index, Double.parseDouble(val.toString().replace(',', '.')));
        }
    }
   // Pomoćne metode za postavljanje nullable vrijednosti u PreparedStatement
    private static void setNullableInteger(PreparedStatement ps, int index, Object val) throws SQLException {
        if (val == null || (val instanceof String && ((String) val).isBlank())) {
            ps.setNull(index, Types.INTEGER);
        } else if (val instanceof Number) {
            ps.setInt(index, ((Number) val).intValue());
        } else {
            ps.setInt(index, Integer.parseInt(val.toString()));
        }
    }
    

 // Java //* Učitava mapu komitentOpis → trgovackiPredstavnik iz tablice komitenti
 public static Map<String, String> loadKomitentPredstavnikMap() {
     Map<String, String> map = new java.util.HashMap<>();
     String sql = "SELECT komitentOpis, trgovackiPredstavnik FROM komitenti";
     try (Connection conn = DriverManager.getConnection(DB_URL);
          Statement st = conn.createStatement();
          ResultSet rs = st.executeQuery(sql)) {
         while (rs.next()) {
             String opis = rs.getString("komitentOpis");
             String predstavnik = rs.getString("trgovackiPredstavnik");
             map.put(opis, predstavnik);
         }
     } catch (SQLException e) {
         e.printStackTrace();
     }
     return map;
 }

}
