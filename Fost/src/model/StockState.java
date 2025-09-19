package model;

import java.util.Objects;

/**
 * Prošireni model stanja artikla.
 */
public class StockState {
    private final String productCode;
    private final String name;
    private final String unit;
    private final double quantity;
    private final Double purchaseUnitPrice;   // nabavna cijena (jedinična)
    private final Double purchaseTotalValue;  // ukupna nabavna vrijednost

    public StockState(String productCode,
                      String name,
                      String unit,
                      double quantity,
                      Double purchaseUnitPrice,
                      Double purchaseTotalValue) {
        this.productCode = productCode;
        this.name = name;
        this.unit = unit;
        this.quantity = quantity;
        this.purchaseUnitPrice = purchaseUnitPrice;
        // Ako total nije zadan, a postoji jedinična cijena – izračunaj
        this.purchaseTotalValue = (purchaseTotalValue != null)
                ? purchaseTotalValue
                : (purchaseUnitPrice != null ? quantity * purchaseUnitPrice : null);
    }

    public String getProductCode() { return productCode; }
    public String getName() { return name; }
    public String getUnit() { return unit; }
    public double getQuantity() { return quantity; }
    public Double getPurchaseUnitPrice() { return purchaseUnitPrice; }
    public Double getPurchaseTotalValue() { return purchaseTotalValue; }

    public StockState withQuantity(double newQty) {
        // Ako postoji purchaseUnitPrice – recalculiraj total
        Double newTotal = (purchaseUnitPrice != null) ? newQty * purchaseUnitPrice : purchaseTotalValue;
        return new StockState(productCode, name, unit, newQty, purchaseUnitPrice, newTotal);
    }

    public StockState withUnitPrice(double newUnitPrice) {
        Double newTotal = newUnitPrice * quantity;
        return new StockState(productCode, name, unit, quantity, newUnitPrice, newTotal);
    }

    public StockState withTotalValue(Double newTotal) {
        return new StockState(productCode, name, unit, quantity, purchaseUnitPrice, newTotal);
    }

    @Override
    public String toString() {
        return "StockState{" +
                "productCode='" + productCode + '\'' +
                ", name='" + name + '\'' +
                ", unit='" + unit + '\'' +
                ", quantity=" + quantity +
                ", purchaseUnitPrice=" + purchaseUnitPrice +
                ", purchaseTotalValue=" + purchaseTotalValue +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockState)) return false;
        StockState that = (StockState) o;
        return Double.compare(that.quantity, quantity) == 0
                && Objects.equals(productCode, that.productCode)
                && Objects.equals(name, that.name)
                && Objects.equals(unit, that.unit)
                && Objects.equals(purchaseUnitPrice, that.purchaseUnitPrice)
                && Objects.equals(purchaseTotalValue, that.purchaseTotalValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productCode, name, unit, quantity, purchaseUnitPrice, purchaseTotalValue);
    }
}