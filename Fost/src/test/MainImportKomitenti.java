package test;

import java.util.List;

import db.KomitentiDatabaseHelper;
import model.KomitentInfo;

public class MainImportKomitenti {

    public static void main(String[] args) {

        // 1. Inicijalizacija baze (kreira tablicu ako ne postoji)
        KomitentiDatabaseHelper.initializeDatabase();

        // 2. Putanja do Excel datoteke
        String excelPath = "C:\\Borko FOTO\\za bazu podataka\\komitenti i trgpredstavnici.xlsx";

        try {
            // 3. Učitavanje podataka iz Excela (kolone prepoznaje po nazivu zaglavlja)
            ExcelKomitentReader reader = new ExcelKomitentReader(excelPath);
            List<KomitentInfo> lista = reader.readData();

            // 4. Kratki pregled učitanih podataka
            System.out.println("=== Pregled podataka iz Excela ===");
            lista.stream().limit(5).forEach(k ->
                    System.out.println(k.getKomitentOpis() + " | " + k.getTrgovackiPredstavnik())
            );

            // 5. Spremanje u bazu: briše sve postojeće zapise i unosi nove
            KomitentiDatabaseHelper.saveToDatabase(lista);

            // 6. Potvrda
            System.out.println("✅ Podaci iz Excela uspješno upisani u tablicu 'komitenti'.");
            System.out.println("📦 Ukupno unosa: " + lista.size());

        } catch (Exception e) {
            System.err.println("❌ Greška pri uvozu podataka iz Excela:");
            e.printStackTrace();
        }
    }
}
