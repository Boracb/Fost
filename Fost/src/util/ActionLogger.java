package util;

import javax.swing.table.DefaultTableModel; import java.io.File; import java.io.FileWriter; import java.io.IOException; import java.time.LocalDateTime; import java.time.format.DateTimeFormatter;

public class ActionLogger {

private static final String LOG_DIR = "Fost";
private static final String LOG_FILE = LOG_DIR + "/user.actions.log";

/**
 * Ensures the log directory exists
 */
private static void ensureLogDirExists() {
    File logDir = new File(LOG_DIR);
    if (!logDir.exists()) {
        logDir.mkdirs();
    }
}

/**
 * Returns the path to the user's log file
 */
private static String getUserLogFile(String username) {
    return LOG_DIR + "/" + username + ".log";
}

/**
 * Logs a basic action
 */
public static void log(String username, String action) {
    ensureLogDirExists();
    try (FileWriter fw = new FileWriter(LOG_FILE, true)) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        fw.write(String.format("%s | %s | %s%n", timestamp, username, action));
    } catch (IOException e) {
        e.printStackTrace();
    }
}

/**
 * Logs a table action with key data
 */
public static void logTableAction(String username, String action, DefaultTableModel model, int modelRow) {
    ensureLogDirExists();
    String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    String nazivRobe = safe(model.getValueAt(modelRow, 3));
    String status    = safe(model.getValueAt(modelRow, 6));
    String startTime = safe(model.getValueAt(modelRow, 12));
    String endTime   = safe(model.getValueAt(modelRow, 13));

    String zapis = String.format(
            "%s | %s | %s | Naziv robe: %s | Status: %s | startTime: %s | endTime: %s",
            timestamp, username, action, nazivRobe, status, startTime, endTime
    );

    try (FileWriter fw = new FileWriter(LOG_FILE, true)) {
        fw.write(zapis + System.lineSeparator());
    } catch (IOException e) {
        e.printStackTrace();
    }
}

/**
 * NEW function — logs cell change by user
 */
public static void logCellChange(String username, int row, int col, Object oldValue, Object newValue) {
    ensureLogDirExists();
    String zapis = String.format(
            "%s | %s | Promjena ćelije [row=%d, col=%d] | stara: %s | nova: %s",
            now(), username, row, col, safe(oldValue), safe(newValue)
    );
    try (FileWriter fw = new FileWriter(getUserLogFile(username), true)) {
        fw.write(zapis + System.lineSeparator());
    } catch (IOException e) {
        e.printStackTrace();
    }
}

/**
 * Helper — safe conversion to String
 */
private static String safe(Object val) {
    return val == null ? "" : val.toString();
}

/**
 * Helper — current timestamp
 */
private static String now() {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
}
}