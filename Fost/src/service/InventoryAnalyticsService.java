package service;

import dao.SalesDao;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Analitika zaliha: obrtaj, DIO i ROP.
 * Radi i bez snapshotova zaliha: prosječnu zalihu aproksimira iz trenutne količine i prodaje u periodu.
 */
public class InventoryAnalyticsService {

    private final SalesDao salesDao;

    public InventoryAnalyticsService(SalesDao salesDao) {
        this.salesDao = Objects.requireNonNull(salesDao, "salesDao");
    }

    public static class TurnoverResult {
        public double soldQty;        // prodano u kom
        public double cogs;           // COGS (nabavna vrijednost prodanog)
        public double avgStockQty;    // prosječna zaliha u kom (aproksimacija)
        public Double avgStockValue;  // null ako nema vrijednosnih snapshotova (ovdje ne računamo)
        public double turnoverUnits;  // obrtaj po komadima
        public double turnoverValue;  // obrtaj po vrijednosti (ako bi imali prosječnu vrijednost zalihe)
        public double dioUnits;       // Days Inventory Outstanding (komadni)
        public double dioValue;       // DIO vrijednosni (ako bi imali avgStockValue)
        public int days;              // broj dana u periodu
        public double avgDailyUsage;  // prosječna dnevna potrošnja (kom/dan)
    }

    /**
     * Izračun obrtaja koristeći trenutnu količinu na skladištu kao polazište.
     * Prosječnu zalihu aproksimiramo: avg ≈ (currentQty + max(currentQty - soldQty, 0)) / 2
     */
    public TurnoverResult computeTurnoverWithCurrentQty(String productCode,
                                                        LocalDate from,
                                                        LocalDate to,
                                                        double currentQty) throws Exception {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Neispravan period.");
        }
        int days = Math.max(1, (int) (to.toEpochDay() - from.toEpochDay() + 1));

        TurnoverResult r = new TurnoverResult();
        r.days = days;

        r.soldQty = salesDao.getSoldQtyByRange(productCode, from, to);
        r.cogs = salesDao.getCOGSByRange(productCode, from, to); // suma nabavne vrijednosti prodanog

        double openingQtyApprox = Math.max(currentQty - r.soldQty, 0.0);
        r.avgStockQty = (currentQty + openingQtyApprox) / 2.0;

        r.avgDailyUsage = r.soldQty / days;

        r.turnoverUnits = (r.avgStockQty > 0) ? (r.soldQty / r.avgStockQty) : 0.0;
        r.dioUnits = (r.turnoverUnits > 0) ? (365.0 / r.turnoverUnits) : Double.POSITIVE_INFINITY;

        // Vrijednosni obrtaj ne računamo bez prosječne vrijednosti zalihe (nema snapshotova)
        r.avgStockValue = null;
        r.turnoverValue = 0.0;
        r.dioValue = Double.POSITIVE_INFINITY;

        return r;
    }

    /**
     * Jednostavni ROP: ROP = ADU * leadTimeDays + safetyStock
     */
    public double computeReorderPoint(double avgDailyUsage, int leadTimeDays, double safetyStock) {
        return avgDailyUsage * Math.max(0, leadTimeDays) + Math.max(0, safetyStock);
    }

    /**
     * Preporučena narudžba: naručiti do target_stock, uz poštivanje MOQ i zaokruživanje na višekratnike.
     */
    public double computeRecommendedOrder(double currentQty,
                                          double targetStock,
                                          Double moq,
                                          Double lotMultiplier) {
        double need = Math.max(0.0, targetStock - currentQty);
        if (moq != null && moq > 0) need = Math.max(need, moq);
        if (lotMultiplier != null && lotMultiplier > 0) {
            double k = Math.ceil(need / lotMultiplier);
            need = k * lotMultiplier;
        }
        return need;
    }
}