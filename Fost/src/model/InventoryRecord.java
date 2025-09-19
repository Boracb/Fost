package model;

import java.time.Instant;

public class InventoryRecord {
    private final String productCode;
    private final double quantity;        // u base_unit
    private final Double purchaseValue;   // quantity * price (može biti null)
    private final Instant lastUpdated;

    public InventoryRecord(String productCode,
                           double quantity,
                           Double purchaseValue,
                           Instant lastUpdated) {
        this.productCode = productCode;
        this.quantity = quantity;
        this.purchaseValue = purchaseValue;
        this.lastUpdated = lastUpdated;
    }

    public String getProductCode() { return productCode; }
    public double getQuantity() { return quantity; }
    public Double getPurchaseValue() { return purchaseValue; }
    public Instant getLastUpdated() { return lastUpdated; }

    public InventoryRecord withQuantity(double q, Double unitPrice) {
        Double newVal = (unitPrice != null) ? q * unitPrice : purchaseValue;
        return new InventoryRecord(productCode, q, newVal, Instant.now());
    }
}