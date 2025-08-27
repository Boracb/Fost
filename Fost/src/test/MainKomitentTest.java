package test; // ili db, ako želiš da bude u istom paketu

import java.util.List;
import db.KomitentiDatabaseHelper;

public class MainKomitentTest {
    public static void main(String[] args) {
        // Inicijaliziraj tablicu ako slučajno ne postoji
        KomitentiDatabaseHelper.initializeDatabase();
        
       

        // Učitaj sve retke iz baze
        List<Object[]> podaci = KomitentiDatabaseHelper.loadAllRows();

        System.out.println("\n=== Provjera ispisa komitenata iz baze ===");
        for (Object[] red : podaci) {
            String opis = (String) red[0];
            String predstavnik = (String) red[1];
            System.out.println(opis + " | " + predstavnik);
        }
    }
}
