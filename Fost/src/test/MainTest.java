package test;

import db.KomitentiDatabaseHelper;
import db.PredstavniciDatabaseHelper;
import ui.KomitentiUI;

import javax.swing.SwingUtilities;

/**
 * MainTest - jednostavan tester koji inicijalizira baze i otvara samo KomitentiUI.
 * Pokreni ovu klasu (Run as Java Application) i prati konzolu.
 */
public class MainTest {
    public static void main(String[] args) {
        System.out.println("MainTest start...");
        // init baze (sigurno idempotentno)
        PredstavniciDatabaseHelper.initializeDatabase();
        KomitentiDatabaseHelper.initializeDatabase();
       
        SwingUtilities.invokeLater(() -> {
            System.out.println("Otvaram KomitentiUI na EDT...");
            KomitentiUI ui = new KomitentiUI();
            // KomitentiUI sada poziva setVisible(true) u konstruktoru
            System.out.println("ok, KomitentiUI otvoren.");
          
        });
        
      
    }
}