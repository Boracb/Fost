package service;

import dao.InventoryDao;
import dao.ProductSupplierDao;
import dao.SalesDao;
import model.InventoryRecord;
import model.ProductSupplier;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * ProductService – turnover + narudžbe.
 */
public class ProductService {

    private final InventoryDao inventoryDao;
    private final SalesDao salesDao;
    private final ProductSupplierDao psDao;

    public ProductService(InventoryDao inventoryDao,
                          SalesDao salesDao,
                          ProductSupplierDao psDao) {
        this.inventoryDao = inventoryDao;
        this.salesDao = salesDao;
        this.psDao = psDao;
    }

    public enum PeriodMonths {
        M1(1), M3(3), M6(6), M12(12);
        public final int months;
        PeriodMonths(int m) { this.months = m; }
        public static PeriodMonths fromLabel(String lbl) {
            return switch (lbl) {
                case "1M" -> M1;
                case "3M" -> M3;
                case "6M" -> M6;
                case "12M" -> M12;
                default -> M3;
            };
        }
    }

    public static class OrderSuggestion {
        public final String productCode;
        public final String supplierCode;
        public final double currentQty;
        public final double dailyDemand;
        public final int leadTimeDays;
        public final double reorderPoint;
        public final double suggestedQty;
        public final Double minOrderQty;
        public final int coverageDays;
        public final double safetyStock;

        public OrderSuggestion(String productCode,
                               String supplierCode,
                               double currentQty,
                               double dailyDemand,
                               int leadTimeDays,
                               double reorderPoint,
                               double suggestedQty,
                               Double minOrderQty,
                               int coverageDays,
                               double safetyStock) {
            this.productCode = productCode;
            this.supplierCode = supplierCode;
            this.currentQty = currentQty;
            this.dailyDemand = dailyDemand;
            this.leadTimeDays = leadTimeDays;
            this.reorderPoint = reorderPoint;
            this.suggestedQty = suggestedQty;
            this.minOrderQty = minOrderQty;
            this.coverageDays = coverageDays;
            this.safetyStock = safetyStock;
        }
    }

    // STARA verzija (ostavi ako je koristi nešto drugo)
    public List<OrderSuggestion> suggestOrders(PeriodMonths pm,
                                               int coverageDays,
                                               double safetyFactor) throws Exception {
        return internalSuggest(pm, coverageDays, safetyFactor);
    }

    // NOVO: coverage = broj dana perioda
    public List<OrderSuggestion> suggestOrdersForPeriod(PeriodMonths pm,
                                                        double safetyFactor) throws Exception {
        int coverageDays = computePeriodDays(pm);
        return internalSuggest(pm, coverageDays, safetyFactor);
    }

    private int computePeriodDays(PeriodMonths pm) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(pm.months);
        return (int)Math.max(1, ChronoUnit.DAYS.between(from, to));
    }

    private List<OrderSuggestion> internalSuggest(PeriodMonths pm,
                                                  int coverageDays,
                                                  double safetyFactor) throws Exception {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(pm.months);
        long days = Math.max(1, ChronoUnit.DAYS.between(from, to));

        List<OrderSuggestion> out = new ArrayList<>();

        for (var inv : inventoryDao.findAll()) {
            String code = inv.getProductCode();
            double salesQty = salesDao.getSoldQtyByRange(code, from, to);
            if (salesQty <= 0) continue;

            double dailyDemand = salesQty / days;

            var primaryOpt = psDao.findPrimary(code);
            if (primaryOpt.isEmpty()) continue;
            ProductSupplier ps = primaryOpt.get();

            int lead = ps.getLeadTimeDays() != null ? ps.getLeadTimeDays() : 0;
            Double minOrder = ps.getMinOrderQty();

            double reorderPoint = dailyDemand * lead;
            double safetyStock = reorderPoint * safetyFactor;
            double targetStock = (dailyDemand * coverageDays) + safetyStock;
            double current = inv.getQuantity();

            if (current <= reorderPoint + safetyStock) {
                double needed = targetStock - current;
                if (needed <= 0) continue;
                if (minOrder != null && needed < minOrder) needed = minOrder;
                out.add(new OrderSuggestion(
                        code,
                        ps.getSupplierCode(),
                        current,
                        round2(dailyDemand),
                        lead,
                        round2(reorderPoint),
                        round2(needed),
                        minOrder,
                        coverageDays,
                        round2(safetyStock)
                ));
            }
        }
        // Sort kritičniji prvi
        out.sort(Comparator.comparingDouble(o -> (o.currentQty - o.reorderPoint)));
        return out;
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}