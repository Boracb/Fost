package ui;

import javax.swing.SwingUtilities;
import java.util.List;

import db.KomitentiDatabaseHelper;
import db.UserDatabaseHelper;
import excel.ExcelKomitentReader;
import model.KomitentInfo;

/**
 * Glavna klasa aplikacije. Inicijalizira baze podataka, učitava podatke iz
 * Excela i pokreće Login UI.
 */	

public class Main {
    public static void main(String[] args) {
        // 1. Inicijalizacija tablica u bazi
        UserDatabaseHelper.initializeUserTable();
        KomitentiDatabaseHelper.initializeDatabase();
        db.PredstavniciDatabaseHelper.initializeDatabase();

        try {
            // 2. Čitanje iz Excela → u listu model objekata
        	String excelPath = "C:\\Borko FOTO\\za bazu podataka/komitenti i trgpredstavnici.xlsx";
            ExcelKomitentReader reader = new ExcelKomitentReader(excelPath);
            List<KomitentInfo> lista = reader.readData();

            // 3. Spremanje u DB
            KomitentiDatabaseHelper.saveToDatabase(lista);

            // 4. Ispis u konzolu (automatski unutar metode)
            KomitentiDatabaseHelper.loadAllRows();

        } catch (Exception e) {
            e.printStackTrace();
        }

        // 5. Pokretanje Login UI-ja
        SwingUtilities.invokeLater(LoginUI::new);
    }
}
