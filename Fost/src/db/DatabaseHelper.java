package db;

import javax.swing.table.DefaultTableModel;
import logic.WorkingTimeCalculator;
import logic.DateUtils;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pomoćna klasa za rad sa SQLite bazom 'fost.db'.
 * Sadrži metode za inicijalizaciju baze, čuvanje, učitavanje i brisanje podataka.
 *
 * Ažurirano: podrška za stupac predPlanIsporuke i robusno rukovanje shemom (dodavanje stupaca ako nedostaju).
 * Dodano: getAverageDailyM2(int days) - računa stvarni dnevni prosjek m2 iz dovršenih narudžbi u zadnjih N dana.
 */
public class DatabaseHelper {

    private static final String DB_URL = "jdbc:sqlite:fost.db";

    // fallback parametri (ako nema dovoljno podataka)
    private static final double FALLBACK_M2_PER_HOUR = 10.0;
    private static final int WORK_HOURS_PER_DAY = 8;

    /**
     * Inicijalizira bazu:
     * - Kreira tablicu 'narudzbe' ako ne postoji sa svim kolonama (uključujući predPlanIsporuke i trgovackiPredstavnik).
     * - Kreira tablicu 'komitenti' ako ne postoji.
     * - Ako tablica postoji, osigurava da potrebni stupci postoje (ALTER TABLE ADD COLUMN ako nedostaje).
     */
    
    // gdje ide ova metoda?
    // pozvati je pri pokretanju aplikacije, npr. u main metodi glavne klase
    //dalje?
    // pozvati je prije prvog poziva loadFromDatabase ili saveToDatabase
    // npr. u konstruktoru GUI klase ili glavne klase aplikacije
    // primjer:
    // public class MainApp {
    //     public static void main(String[] args) {
    //         DatabaseHelper.initializeDatabase();
    //         // ostatak pokretanja aplikacije...
    
    public static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);

            // Ensure narudzbe table exists
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
                            "predPlanIsporuke TEXT, " +          // NOVO
                            "trgovackiPredstavnik TEXT" +
                            ")";
                    stmt.execute(sql);
                }
            } else {
                // Ensure required columns exist; if not, add them
                Set<String> cols = getTableColumns(conn, "narudzbe");
                try (Statement stmt = conn.createStatement()) {
                    if (!cols.contains("predPlanIsporuke")) {
                        stmt.execute("ALTER TABLE narudzbe ADD COLUMN predPlanIsporuke TEXT");
                    }
                    if (!cols.contains("trgovackiPredstavnik")) {
                        stmt.execute("ALTER TABLE narudzbe ADD COLUMN trgovackiPredstavnik TEXT");
                    }
                }
            }

            // Ensure komitenti table exists (used for komitent -> predstavnik mapping)
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='komitenti'");
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    try (Statement stmt = conn.createStatement()) {
                        String sql = "CREATE TABLE komitenti (" +
                                "komitentOpis TEXT PRIMARY KEY, " +
                                "trgovackiPredstavnik TEXT" +
                                ")";
                        stmt.execute(sql);
                    }
                }
            }

            conn.commit();
            conn.setAutoCommit(true);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static Set<String> getTableColumns(Connection conn, String tableName) throws SQLException {
        Set<String> cols = new HashSet<>();
        String pragma = "PRAGMA table_info('" + tableName + "')";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(pragma)) {
            while (rs.next()) {
                cols.add(rs.getString("name"));
            }
        }
        return cols;
    }

    /**
     * Sprema podatke iz DefaultTableModel-a u bazu:
     * Briše sve prethodne zapise iz 'narudzbe', upisuje nove.
     * Radi sa fiksnim setom kolona (koje su kompatibilne s UI modelom), uključujući predPlanIsporuke.
     */
    public static void saveToDatabase(DefaultTableModel model) {
        if (model == null) return;

        String insert = "INSERT INTO narudzbe (" +
                "datumNarudzbe, predDatumIsporuke, komitentOpis, nazivRobe, " +
                "netoVrijednost, kom, status, djelatnik, mm, m, tisucl, m2, " +
                "startTime, endTime, duration, predPlanIsporuke, trgovackiPredstavnik" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement clean = conn.createStatement();
             PreparedStatement ps = conn.prepareStatement(insert)) {

            conn.setAutoCommit(false);
            clean.execute("DELETE FROM narudzbe");

            // Mapiranje indeksa (ako neki stupac ne postoji u modelu, koristimo -1)
            int idxDatumNarudzbe     = findColumnSafe(model, "datumNarudzbe");
            int idxPredDatumIsporuke = findColumnSafe(model, "predDatumIsporuke");
            int idxKomitentOpis       = findColumnSafe(model, "komitentOpis");
            int idxNazivRobe         = findColumnSafe(model, "nazivRobe");
            int idxNetoVrijednost    = findColumnSafe(model, "netoVrijednost");
            int idxKom               = findColumnSafe(model, "kom");
            int idxStatus            = findColumnSafe(model, "status");
            int idxDjelatnik         = findColumnSafe(model, "djelatnik");
            int idxMm                = findColumnSafe(model, "mm");
            int idxM                 = findColumnSafe(model, "m");
            int idxTisucl            = findColumnSafe(model, "tisucl");
            int idxM2                = findColumnSafe(model, "m2");
            int idxStartTime         = findColumnSafe(model, "startTime");
            int idxEndTime           = findColumnSafe(model, "endTime");
            int idxDuration          = findColumnSafe(model, "duration");
            int idxPredPlan          = findColumnSafe(model, "predPlanIsporuke");
            int idxTrgovacki         = findColumnSafe(model, "trgovackiPredstavnik");

            for (int r = 0; r < model.getRowCount(); r++) {
                setStringOrNull(ps, 1, idxDatumNarudzbe >= 0 ? asString(model.getValueAt(r, idxDatumNarudzbe)) : null);
                setStringOrNull(ps, 2, idxPredDatumIsporuke >= 0 ? asString(model.getValueAt(r, idxPredDatumIsporuke)) : null);
                setStringOrNull(ps, 3, idxKomitentOpis >= 0 ? asString(model.getValueAt(r, idxKomitentOpis)) : null);
                setStringOrNull(ps, 4, idxNazivRobe >= 0 ? asString(model.getValueAt(r, idxNazivRobe)) : null);

                setNullableDouble(ps, 5, idxNetoVrijednost >= 0 ? model.getValueAt(r, idxNetoVrijednost) : null);
                setNullableInteger(ps, 6, idxKom >= 0 ? model.getValueAt(r, idxKom) : null);

                setStringOrNull(ps, 7, idxStatus >= 0 ? asString(model.getValueAt(r, idxStatus)) : null);
                setStringOrNull(ps, 8, idxDjelatnik >= 0 ? asString(model.getValueAt(r, idxDjelatnik)) : null);

                setNullableDouble(ps, 9, idxMm >= 0 ? model.getValueAt(r, idxMm) : null);
                setNullableDouble(ps, 10, idxM >= 0 ? model.getValueAt(r, idxM) : null);
                setNullableDouble(ps, 11, idxTisucl >= 0 ? model.getValueAt(r, idxTisucl) : null);
                setNullableDouble(ps, 12, idxM2 >= 0 ? model.getValueAt(r, idxM2) : null);

                setStringOrNull(ps, 13, idxStartTime >= 0 ? asString(model.getValueAt(r, idxStartTime)) : null);
                setStringOrNull(ps, 14, idxEndTime >= 0 ? asString(model.getValueAt(r, idxEndTime)) : null);
                setStringOrNull(ps, 15, idxDuration >= 0 ? asString(model.getValueAt(r, idxDuration)) : null);

                // predPlanIsporuke (novo)
                setStringOrNull(ps, 16, idxPredPlan >= 0 ? asString(model.getValueAt(r, idxPredPlan)) : null);

                setStringOrNull(ps, 17, idxTrgovacki >= 0 ? asString(model.getValueAt(r, idxTrgovacki)) : null);

                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static int findColumnSafe(DefaultTableModel model, String name) {
        try {
            return model.findColumn(name);
        } catch (Exception ex) {
            return -1;
        }
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static void setStringOrNull(PreparedStatement ps, int index, String val) throws SQLException {
        if (val == null || val.isBlank()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, val);
        }
    }

    /**
     * Učitava sve podatke iz baze u DefaultTableModel, sada robustno čita i predPlanIsporuke
     * (ako postoji u DB) i popunjava samo one kolone koje su prisutne u bazi.
     */
    public static void loadFromDatabase(DefaultTableModel model) {
        String select = "SELECT * FROM narudzbe";
        Map<String, String> komitentMap = loadKomitentPredstavnikMap();

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(select)) {

            model.setRowCount(0);

            // get DB column labels for safe access
            ResultSetMetaData md = rs.getMetaData();
            Set<String> dbCols = new HashSet<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                dbCols.add(md.getColumnLabel(i));
            }

            while (rs.next()) {
                Object[] row = new Object[model.getColumnCount()];
                for (int c = 0; c < model.getColumnCount(); c++) {
                    String colName = model.getColumnName(c);
                    if (dbCols.contains(colName)) {
                        Object val = rs.getObject(colName);
                        row[c] = val;
                    } else {
                        // column not present in DB -> keep existing model default (empty string)
                        row[c] = "";
                    }
                }

                // Fill trgovackiPredstavnik if empty and mapping exists (komitenti table)
                int idxKomitentOpis = findColumnSafe(model, "komitentOpis");
                int idxTrgovackiPredstavnik = findColumnSafe(model, "trgovackiPredstavnik");
                if (idxTrgovackiPredstavnik >= 0) {
                    Object tpVal = row[idxTrgovackiPredstavnik];
                    if (tpVal == null || tpVal.toString().isBlank()) {
                        String komitentOpis = "";
                        if (idxKomitentOpis >= 0 && row[idxKomitentOpis] != null) komitentOpis = row[idxKomitentOpis].toString();
                        row[idxTrgovackiPredstavnik] = komitentMap.getOrDefault(komitentOpis, "");
                    }
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
        int idxStart = findColumnSafe(model, "startTime");
        int idxEnd = findColumnSafe(model, "endTime");
        int idxDuration = findColumnSafe(model, "duration");

        if (idxDuration < 0) return;

        String start = idxStart >= 0 ? (String) model.getValueAt(row, idxStart) : null;
        String end   = idxEnd >= 0 ? (String) model.getValueAt(row, idxEnd) : null;

        if (start == null || end == null || start.isBlank() || end.isBlank()) {
            model.setValueAt("", row, idxDuration);
            return;
        }
        String formatted = WorkingTimeCalculator.calculateWorkingDuration(start, end);
        model.setValueAt(formatted, row, idxDuration);
    }

    /**
     * Briše red iz baze na osnovu datumNarudzbe i nazivRobe
     */
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
            try {
                ps.setDouble(index, Double.parseDouble(val.toString().replace(',', '.')));
            } catch (NumberFormatException ex) {
                ps.setNull(index, Types.REAL);
            }
        }
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Object val) throws SQLException {
        if (val == null || (val instanceof String && ((String) val).isBlank())) {
            ps.setNull(index, Types.INTEGER);
        } else if (val instanceof Number) {
            ps.setInt(index, ((Number) val).intValue());
        } else {
            try {
                ps.setInt(index, Integer.parseInt(val.toString()));
            } catch (NumberFormatException ex) {
                ps.setNull(index, Types.INTEGER);
            }
        }
    }

    /**
     * Učitava mapu komitentOpis → trgovackiPredstavnik iz tablice komitenti (ako postoji).
     */
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
            // If komitenti table does not exist or other error, return empty map
        }
        return map;
    }

    /**
     * Računa stvarni prosjek m2/dan iz dovršenih (status='Izrađeno') narudžbi
     * u zadnjih 'days' dana (uključivo). Izostavlja vikende i blagdane (WorkingTimeCalculator).
     *
     * Ako nema podataka u tom periodu vraća fallback vrijednost (FALLBACK_M2_PER_HOUR * WORK_HOURS_PER_DAY).
     */
 // Ubaci/zalijepi ovu metodu u klasu db.DatabaseHelper (zamijeni postojeću getAverageDailyM2)
 // Zamijeni postojeću getAverageDailyM2(int) u db/DatabaseHelper.java ovom metodom

public static double getAverageDailyM2(int days) {
    if (days <= 0) days = 30;
    LocalDate today = LocalDate.now();
    LocalDate from = today.minusDays(days - 1); // include today

    double totalM2 = 0.0;

    // 1) Primary: sum m2 of completed orders (status = 'Izrađeno') where endTime parses into last N days
    try (Connection conn = DriverManager.getConnection(DB_URL);
         PreparedStatement ps = conn.prepareStatement(
                 "SELECT m2, endTime, datumNarudzbe, status FROM narudzbe WHERE m2 IS NOT NULL")) {

        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                double m2 = rs.getDouble("m2");
                if (rs.wasNull()) continue;

                String endTs = rs.getString("endTime");
                String ordDate = rs.getString("datumNarudzbe");
                String status = rs.getString("status");

                if (status == null || !status.equalsIgnoreCase("Izrađeno")) continue;

                LocalDateTime parsedEnd = null;
                LocalDate parsedOrd = null;

                if (endTs != null && !endTs.isBlank()) {
                    try {
                        parsedEnd = DateUtils.parse(endTs);
                    } catch (Exception ignored) {
                        parsedEnd = null;
                    }
                }

                if (ordDate != null && !ordDate.isBlank()) {
                    try {
                        LocalDateTime dt = DateUtils.parse(ordDate);
                        if (dt != null) parsedOrd = dt.toLocalDate();
                    } catch (Exception ignored) {
                        parsedOrd = null;
                    }
                }

                boolean inRange = false;
                if (parsedEnd != null) {
                    LocalDate ld = parsedEnd.toLocalDate();
                    if (!ld.isBefore(from) && !ld.isAfter(today)) inRange = true;
                } else if (parsedOrd != null) {
                    LocalDate ld = parsedOrd;
                    if (!ld.isBefore(from) && !ld.isAfter(today)) inRange = true;
                }

                if (inRange) {
                    totalM2 += m2;
                }
            }
        }
    } catch (SQLException ex) {
        ex.printStackTrace();
    }

    // Count working days in interval (exclude weekends/holidays)
    int workDays = 0;
    for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
        if (!WorkingTimeCalculator.isHolidayOrWeekend(d)) workDays++;
    }

    double avg = 0.0;
    if (totalM2 > 0 && workDays > 0) {
        avg = totalM2 / (double) workDays;
        System.out.println("getAverageDailyM2: days=" + days
                + ", from=" + from + ", to=" + today
                + ", totalM2=" + totalM2
                + ", workDays=" + workDays
                + ", avgDailyM2=" + avg + "  (primary)");
        return avg;
    }

    // Strategy A: sum all m2 where datumNarudzbe in range (ignore status)
    double totalM2ByOrderDate = 0.0;
    try (Connection conn = DriverManager.getConnection(DB_URL);
         PreparedStatement ps = conn.prepareStatement("SELECT m2, datumNarudzbe FROM narudzbe WHERE m2 IS NOT NULL")) {
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                double m2 = rs.getDouble("m2");
                String ordDate = rs.getString("datumNarudzbe");
                if (ordDate == null || ordDate.isBlank()) continue;
                try {
                    LocalDateTime dt = DateUtils.parse(ordDate);
                    if (dt == null) continue;
                    LocalDate ld = dt.toLocalDate();
                    if (!ld.isBefore(from) && !ld.isAfter(today)) totalM2ByOrderDate += m2;
                } catch (Exception ignored) {}
            }
        }
    } catch (SQLException ex) {
        ex.printStackTrace();
    }

    if (totalM2ByOrderDate > 0 && workDays > 0) {
        avg = totalM2ByOrderDate / (double) workDays;
        System.out.println("getAverageDailyM2: fallback by datumNarudzbe: totalM2=" + totalM2ByOrderDate
                + ", workDays=" + workDays + ", avgDailyM2=" + avg);
        return avg;
    }

    // Strategy B: sum all m2 in last N calendar days (ignore date fields) and divide by days (calendar)
    double totalM2Calendar = 0.0;
    try (Connection conn = DriverManager.getConnection(DB_URL);
         PreparedStatement ps = conn.prepareStatement("SELECT m2, endTime, datumNarudzbe FROM narudzbe WHERE m2 IS NOT NULL")) {
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                double m2 = rs.getDouble("m2");
                String endTs = rs.getString("endTime");
                String ordDate = rs.getString("datumNarudzbe");
                LocalDateTime parsedEnd = null;
                LocalDate parsedOrd = null;
                if (endTs != null && !endTs.isBlank()) {
                    try { parsedEnd = DateUtils.parse(endTs); } catch (Exception ignored) { parsedEnd = null; }
                }
                if (ordDate != null && !ordDate.isBlank()) {
                    try { LocalDateTime dt = DateUtils.parse(ordDate); if (dt != null) parsedOrd = dt.toLocalDate(); } catch (Exception ignored) { parsedOrd = null; }
                }
                boolean inRange = false;
                if (parsedEnd != null) {
                    LocalDate ld = parsedEnd.toLocalDate();
                    if (!ld.isBefore(from) && !ld.isAfter(today)) inRange = true;
                } else if (parsedOrd != null) {
                    LocalDate ld = parsedOrd;
                    if (!ld.isBefore(from) && !ld.isAfter(today)) inRange = true;
                }
                if (inRange) totalM2Calendar += m2;
            }
        }
    } catch (SQLException ex) {
        ex.printStackTrace();
    }

    if (totalM2Calendar > 0) {
        avg = totalM2Calendar / (double) days; // calendar days
        System.out.println("getAverageDailyM2: fallback calendar-sum last " + days + " days: totalM2=" + totalM2Calendar
                + ", avgDailyCalendar=" + avg);
        return avg;
    }

    // Final fallback: use configured default (m2 per hour * work hours per day)
    double fallback = FALLBACK_M2_PER_HOUR * WORK_HOURS_PER_DAY;
    System.out.println("getAverageDailyM2: no data found for last " + days + " days, using fallback=" + fallback);
    return fallback;
}
// pokaži mi polja u bazu kako se zovu i koji su im tipovi i brojevi po redoslijedu	
// Polja u tablici 'narudzbe':
// datumNarudzbe TEXT 1
// predDatumIsporuke TEXT 2
// komitentOpis TEXT 3
// nazivRobe TEXT 4
// netoVrijednost REAL 5
// kom INTEGER 6
// status TEXT 7
// djelatnik TEXT 8
// mm REAL 9
// m REAL 10
// tisucl REAL 11
// m2 REAL 12
// startTime TEXT 13
// endTime TEXT 14
// duration TEXT 15
// predPlanIsporuke TEXT 16
// trgovackiPredstavnik TEXT 17


}