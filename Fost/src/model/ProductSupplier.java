package model;

/**
 * Veza proizvod–dobavljač + podaci za nabavu.
 */
public class ProductSupplier {

    private final String productCode;
    private final String supplierCode;
    private final boolean primary;
    private final Integer leadTimeDays;   // rok dobave u danima
    private final Double minOrderQty;     // minimalna količina narudžbe
    private final Double lastPrice;       // zadnja nabavna (opcionalno)

    public ProductSupplier(String productCode,
                           String supplierCode,
                           boolean primary,
                           Integer leadTimeDays,
                           Double minOrderQty,
                           Double lastPrice) {
        this.productCode = productCode;
        this.supplierCode = supplierCode;
        this.primary = primary;
        this.leadTimeDays = leadTimeDays;
        this.minOrderQty = minOrderQty;
        this.lastPrice = lastPrice;
    }

    public String getProductCode() { return productCode; }
    public String getSupplierCode() { return supplierCode; }
    public boolean isPrimary() { return primary; }
    public Integer getLeadTimeDays() { return leadTimeDays; }
    public Double getMinOrderQty() { return minOrderQty; }
    public Double getLastPrice() { return lastPrice; }

    public ProductSupplier withPrimary(boolean p) {
        return new ProductSupplier(productCode, supplierCode, p, leadTimeDays, minOrderQty, lastPrice);
    }
}