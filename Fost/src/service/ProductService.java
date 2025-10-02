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
 * Servis za izračun obrtaja i generiranje prijedloga narudžbe.
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

    public static class TurnoverResult {
        public final String productCode;
        public final double salesQty;
        public final double avgQty;
        public final double turnover;

        public TurnoverResult(String productCode,
                              double salesQty,
                              double avgQty,
                              double turnover) {
            this.productCode = productCode;
            this.salesQty = salesQty;
            this.avgQty = avgQty;
            this.turnover = turnover;
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

        public OrderSuggestion(String productCode, String supplierCode,
                               double currentQty, double dailyDemand,
                               int leadTimeDays, double reorderPoint,
                               double suggestedQty, Double minOrderQty) {
            this.productCode = productCode;
            this.supplierCode = supplierCode;
            this.currentQty = currentQty;
            this.dailyDemand = dailyDemand;
            this.leadTimeDays = leadTimeDays;
            this.reorderPoint = reorderPoint;
            this.suggestedQty = suggestedQty;
            this.minOrderQty = minOrderQty;
        }
    }

    public TurnoverResult computeTurnover(String productCode, PeriodMonths pm) throws Exception {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(pm.months);
        double salesQty = salesDao.getSoldQtyByRange(productCode, from, to);
        double currentQty = inventoryDao.find(productCode)
                .map(InventoryRecord::getQuantity)
                .orElse(0.0);
        double avgQty = currentQty; // aproksimacija bez snapshotova
        double turnover = (avgQty > 0 ? salesQty / avgQty : 0);
        return new TurnoverResult(productCode, salesQty, avgQty, turnover);
    }

    /**
     * coverageDays = koliko dana zaliha želimo nakon dolaska
     * safetyFactor  = npr. 0.2 -> +20% na reorder point
     */
    public List<OrderSuggestion> suggestOrders(PeriodMonths pm,
                                               int coverageDays,
                                               double safetyFactor) throws Exception {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(pm.months);
        long days = ChronoUnit.DAYS.between(from, to);
        if (days <= 0) days = 1;

        List<OrderSuggestion> out = new ArrayList<>();

        for (var inv : inventoryDao.findAll()) {
            String code = inv.getProductCode();
            double salesQty = salesDao.getSoldQtyByRange(code, from, to);
            if (salesQty <= 0) continue;

            double dailyDemand = salesQty / days;

            var primaryOpt = psDao.findPrimary(code);
            if (primaryOpt.isEmpty()) continue;
            ProductSupplier ps = primaryOpt.get();

            int ltd = ps.getLeadTimeDays() != null ? ps.getLeadTimeDays() : 0;
            Double minOrder = ps.getMinOrderQty();

            double reorderPoint = dailyDemand * ltd;
            double safetyStock = reorderPoint * safetyFactor;
            double target = (dailyDemand * coverageDays) + safetyStock;
            double current = inv.getQuantity();

            if (current <= reorderPoint + safetyStock) {
                double needed = target - current;
                if (needed < 0) continue;
                if (minOrder != null && needed < minOrder) needed = minOrder;
                out.add(new OrderSuggestion(
                        code,
                        ps.getSupplierCode(),
                        current,
                        dailyDemand,
                        ltd,
                        round2(reorderPoint),
                        round2(needed),
                        minOrder
                ));
            }
        }

        out.sort(Comparator.comparingDouble(o -> -(o.reorderPoint - o.currentQty)));
        return out;
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}