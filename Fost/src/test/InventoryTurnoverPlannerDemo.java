package test;

import logic.InventoryTurnoverPlanner;

public class InventoryTurnoverPlannerDemo {
    public static void main(String[] args) {
        InventoryTurnoverPlanner.Params p = new InventoryTurnoverPlanner.Params()
                .withNazivArtikla("ALU profil 40x40")
                .withGodisnjaPotrosnja(24000)     // kom/god
                .withStanjeZaliha(1500)          // kom trenutno
                .withRokIsporukeDana(14)         // dana
                .withMinimalnaZaliha(800)        // safety stock
                .withMinimalnaKolicinaZaNaruciti(500) // MOQ
                .withKoeficijentObrtaja(12)      // 12x godišnje (mjesečno)
                .withRadnihDanaUGodini(365);     // ili 250 ako želiš radne dane

        InventoryTurnoverPlanner.Plan plan = InventoryTurnoverPlanner.compute(p);
        System.out.println(plan);
    }
}