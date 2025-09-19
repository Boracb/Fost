package model;

import java.util.Objects;

public class Product {
    private final String productCode;
    private final String name;
    private final String mainType;        // SIROVINA | VLASTITA | TRGOVACKA
    private final String supplierCode;
    private final String baseUnit;
    private final String altUnit;
    private final Double areaPerPiece;    // m2 po komadu (ako relevantno)
    private final Double packSize;
    private final Double minOrderQty;
    private final Double purchaseUnitPrice;
    private final boolean active;

    public Product(String productCode,
                   String name,
                   String mainType,
                   String supplierCode,
                   String baseUnit,
                   String altUnit,
                   Double areaPerPiece,
                   Double packSize,
                   Double minOrderQty,
                   Double purchaseUnitPrice,
                   boolean active) {
        this.productCode = productCode;
        this.name = name;
        this.mainType = mainType;
        this.supplierCode = supplierCode;
        this.baseUnit = baseUnit;
        this.altUnit = altUnit;
        this.areaPerPiece = areaPerPiece;
        this.packSize = packSize;
        this.minOrderQty = minOrderQty;
        this.purchaseUnitPrice = purchaseUnitPrice;
        this.active = active;
    }

    public String getProductCode() { return productCode; }
    public String getName() { return name; }
    public String getMainType() { return mainType; }
    public String getSupplierCode() { return supplierCode; }
    public String getBaseUnit() { return baseUnit; }
    public String getAltUnit() { return altUnit; }
    public Double getAreaPerPiece() { return areaPerPiece; }
    public Double getPackSize() { return packSize; }
    public Double getMinOrderQty() { return minOrderQty; }
    public Double getPurchaseUnitPrice() { return purchaseUnitPrice; }
    public boolean isActive() { return active; }

    public Product withPrice(Double newPrice) {
        return new Product(productCode, name, mainType, supplierCode, baseUnit, altUnit,
                areaPerPiece, packSize, minOrderQty, newPrice, active);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product)) return false;
        Product product = (Product) o;
        return Objects.equals(productCode, product.productCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productCode);
    }
}