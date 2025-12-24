package ui;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.List;

import db.KomitentiDatabaseHelper;
import db.UserDatabaseHelper;
import db.PredstavniciDatabaseHelper; // Pretpostavljam da je ovo import, ako javlja grešku provjeri package
import excel.ExcelKomitentReader;
import model.KomitentInfo;

/**
 * Glavna klasa aplikacije. 
 * Inicijalizira baze podataka, SIGURNO učitava podatke iz Excela (ako postoji) 
 * i pokreće Login UI.
 */
public class Main {
    public static void main(String[] args) {
        
        System.out.println("--------------------------------------------------");
        System.out.println("POKRETANJE APLIKACIJE FOST");
        
        // 1. Ispis putanja za debugiranje (da znaš gdje staviti datoteke)
        System.out.println("RADNA MAPA: " + System.getProperty("user.dir"));
        System.out.println("OČEKIVANA BAZA: " + new File("fost.db").getAbsolutePath());
        System.out.println("--------------------------------------------------");

        // 2. Inicijalizacija tablica u bazi
        // Ovo kreira fost.db i tablice ako ne postoje
        UserDatabaseHelper.initializeUserTable();
        KomitentiDatabaseHelper.initializeDatabase();
        PredstavniciDatabaseHelper.initializeDatabase();
        
     // --- NOVO: ISPIS SVIH KORISNIKA U KONZOLU ---
        System.out.println("\n*** PROVJERA KORISNIKA U BAZI ***");
        java.util.List<String[]> sviKorisnici = UserDatabaseHelper.getAllUsers();
        
        if (sviKorisnici.isEmpty()) {
            System.out.println("Nema korisnika u bazi!");
        } else {
            for (String[] user : sviKorisnici) {
                // user[0] je ime, user[1] je uloga
                System.out.println(" -> KORISNIK: " + user[0] + "  [Uloga: " + user[1] + "]");
            }
        }
        System.out.println("*********************************\n");
        // ---------------------------------------------

        // ... ostatak tvog koda (učitavanje Excela itd.) ...

        // 3. Sigurno čitanje iz Excela
        String excelPath = "komitenti i trgpredstavnici.xlsx";
        File excelFile = new File(excelPath);

        if (excelFile.exists() && !excelFile.isDirectory()) {
            System.out.println("Excel datoteka pronađena: " + excelPath);
            try {
                // Kreiraj reader i učitaj podatke
                ExcelKomitentReader reader = new ExcelKomitentReader(excelPath);
                List<KomitentInfo> lista = reader.readData();

                // Spremi u DB
                KomitentiDatabaseHelper.saveToDatabase(lista);
                System.out.println("Podaci iz Excela uspješno spremljeni u bazu.");

                // Ispis za provjeru
                KomitentiDatabaseHelper.loadAllRows();

            } catch (Exception e) {
                System.err.println("GREŠKA prilikom čitanja Excela: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // OVO JE ONAJ DIO KOJI SPRJEČAVA RUŠENJE
            System.out.println("!!! UPOZORENJE !!!");
            System.out.println("Excel datoteka nije pronađena na putanji: " + excelFile.getAbsolutePath());
            System.out.println("Aplikacija nastavlja s radom bez uvoza novih komitenata.");
            System.out.println("--------------------------------------------------");
        }

        // 4. Pokretanje Login UI-ja (ovo se izvršava bez obzira na Excel)
        SwingUtilities.invokeLater(() -> new LoginUI());
    }
}