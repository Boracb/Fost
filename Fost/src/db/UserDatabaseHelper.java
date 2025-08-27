package db;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;

public class UserDatabaseHelper {

    private static final String DB_URL = "jdbc:sqlite:fost.db";
    private static final String LAST_USER_FILE = "Fost/lastuser.txt";

    // SQL konstante
    private static final String SQL_CREATE_USERS =
            "CREATE TABLE IF NOT EXISTS korisnici (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "korisnicko_ime TEXT UNIQUE NOT NULL, " +
            "lozinka TEXT NOT NULL, " +
            "uloga TEXT CHECK(uloga IN ('Djelatnik','Administrator')) NOT NULL" +
            ")";

    private static final String SQL_CREATE_SETTINGS =
            "CREATE TABLE IF NOT EXISTS user_table_settings (" +
            "username TEXT NOT NULL, " +
            "column_index INTEGER NOT NULL, " +
            "width INTEGER NOT NULL, " +
            "PRIMARY KEY(username, column_index)" +
            ")";

    private static final String SQL_INSERT_USER =
            "INSERT OR IGNORE INTO korisnici (korisnicko_ime, lozinka, uloga) VALUES (?, ?, ?)";

    private static final String SQL_USER_EXISTS =
            "SELECT 1 FROM korisnici WHERE korisnicko_ime = ?";

    private static final String SQL_AUTHENTICATE =
            "SELECT lozinka FROM korisnici WHERE korisnicko_ime = ? AND uloga = ?";

    private static final String SQL_UPDATE_PASSWORD =
            "UPDATE korisnici SET lozinka = ? WHERE korisnicko_ime = ?";

    private static final String SQL_UPDATE_ROLE =
            "UPDATE korisnici SET uloga = ? WHERE korisnicko_ime = ?";

    private static final String SQL_DELETE_USER =
            "DELETE FROM korisnici WHERE korisnicko_ime = ?";

    private static final String SQL_SELECT_ALL_USERS =
            "SELECT korisnicko_ime, uloga FROM korisnici ORDER BY korisnicko_ime";

    private static final String SQL_INSERT_SETTINGS =
            "REPLACE INTO user_table_settings (username, column_index, width) VALUES (?, ?, ?)";

    private static final String SQL_SELECT_SETTINGS =
            "SELECT column_index, width FROM user_table_settings WHERE username = ?";

    /** Inicijalizira bazu i dodaje default admina */
    public static void initializeUserTable() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            stmt.execute(SQL_CREATE_USERS);
            stmt.execute(SQL_CREATE_SETTINGS);

            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_USER)) {
                ps.setString(1, "admin");
                ps.setString(2, hashPassword("admin123"));
                ps.setString(3, "Administrator");
                ps.executeUpdate();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Provjera postoji li korisnik */
    public static boolean userExists(String username) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_USER_EXISTS)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Autentifikacija korisnika prema hash lozinci i ulozi */
    public static boolean authenticateHashed(String username, String password, String role) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_AUTHENTICATE)) {
            ps.setString(1, username);
            ps.setString(2, role);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("lozinka");
                    return storedHash.equals(hashPassword(password));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    /** Dodaje novog korisnika */
    public static boolean addUser(String username, String password, String role) {
        if (userExists(username)) return false; // spriječi duplikat
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT_USER)) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password));
            ps.setString(3, role);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /** Ažurira lozinku */
    public static boolean updatePassword(String username, String newPassword) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_PASSWORD)) {
            ps.setString(1, hashPassword(newPassword));
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Ažurira ulogu */
    public static boolean updateRole(String username, String newRole) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_ROLE)) {
            ps.setString(1, newRole);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Briše korisnika — nikad admina */
    public static boolean deleteUser(String username) {
        if ("admin".equalsIgnoreCase(username)) return false;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE_USER)) {
            ps.setString(1, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Dohvaća sve korisnike */
    public static List<String[]> getAllUsers() {
        List<String[]> users = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SQL_SELECT_ALL_USERS)) {
            while (rs.next()) {
                users.add(new String[]{rs.getString("korisnicko_ime"), rs.getString("uloga")});
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    /** Dohvaća samo korisnička imena */
    public static List<String> getAllUsernames() {
        List<String> list = new ArrayList<>();
        for (String[] u : getAllUsers()) {
            list.add(u[0]);
        }
        return list;
    }
    /** Sprema zadnjeg korisnika */
    public static void saveLastUser(String username) {
        try (FileWriter fw = new FileWriter(LAST_USER_FILE)) {
            fw.write(safeString(username));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Učitava zadnjeg korisnika */
    public static String loadLastUser() {
        try (BufferedReader br = new BufferedReader(new FileReader(LAST_USER_FILE))) {
            return br.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    /** Sprema širine stupaca */
    public static void saveUserTableSettings(String username, JTable table) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT_SETTINGS)) {
            for (int viewIndex = 0; viewIndex < table.getColumnCount(); viewIndex++) {
                int modelIndex = table.convertColumnIndexToModel(viewIndex);
                int width = table.getColumnModel().getColumn(viewIndex).getWidth();
                ps.setString(1, username);
                ps.setInt(2, modelIndex);
                ps.setInt(3, width);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Učitava širine stupaca */
    public static void loadUserTableSettings(String username, JTable table) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_SETTINGS)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int modelIndex = rs.getInt("column_index");
                    int width = rs.getInt("width");
                    int viewIndex = table.convertColumnIndexToView(modelIndex);
                    if (viewIndex != -1) {
                        table.getColumnModel().getColumn(viewIndex).setPreferredWidth(width);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Hashiranje lozinke SHA-256 */
    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Greška: SHA-256 algoritam nije dostupan.", e);
        }
    }

    /** Helper za siguran string (bez null vrijednosti) */
    private static String safeString(String val) {
        return val != null ? val.trim() : "";
    }
}
