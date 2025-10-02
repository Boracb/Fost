package logic;

import model.SalesRecord;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class InventoryAnalytics {

    public static double dailyDemand(double soldQty, LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        if (days <= 0) return 0d;
        return soldQty / days;
    }

    public static double reorderPoint(double dailyDemand, int leadTimeDays) {
        return dailyDemand * leadTimeDays;
    }

    public static double orderQuantity(double reorderPoint, double onHand, double onOrder, Double minOrderQty) {
        double need = Math.max(0d, reorderPoint - (onHand + onOrder));
        if (minOrderQty != null) {
            return Math.max(minOrderQty, need);
        }
        return need;
    }

    public static double turnover(double soldQty, double avgInventoryQty) {
        if (avgInventoryQty <= 0) return 0d;
        return soldQty / avgInventoryQty;
    }
}