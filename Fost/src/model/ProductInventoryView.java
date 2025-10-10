package model;

import java.util.List;

/**
 * DTO spojenih podataka za UI / izvoz, sada uključuje i prodaju u odabranom periodu.
 */
public class ProductInventoryView {
    private final Product product;
    private final InventoryRecord inventory;
    private final List<String> groupCodes;
    private final Double salesQtyPeriod;   // NOVO (može biti null ako nije traženo)

    public ProductInventoryView(Product product,
                                InventoryRecord inventory,
                                List<String> groupCodes) {
        this(product, inventory, groupCodes, null);
    }

    public ProductInventoryView(Product product,
                                InventoryRecord inventory,
                                List<String> groupCodes,
                                Double salesQtyPeriod) {
        this.product = product;
        this.inventory = inventory;
        this.groupCodes = groupCodes;
        this.salesQtyPeriod = salesQtyPeriod;
    }

    public Product getProduct() { return product; }
    public InventoryRecord getInventory() { return inventory; }
    public List<String> getGroupCodes() { return groupCodes; }
    public Double getSalesQtyPeriod() { return salesQtyPeriod; }

    public Double getComputedAltQuantity() {
        if (product.getAreaPerPiece() == null || product.getAreaPerPiece() == 0) return null;
        if (product.getBaseUnit() == null || product.getAltUnit() == null) return null;
        if ("m2".equalsIgnoreCase(product.getBaseUnit())) {
            return inventory.getQuantity() / product.getAreaPerPiece();
        }
        if ("kom".equalsIgnoreCase(product.getBaseUnit())
                && "m2".equalsIgnoreCase(product.getAltUnit())) {
            return inventory.getQuantity() * product.getAreaPerPiece();
        }
        return null;
    }

    public Double getTotalValue() {
        return inventory.getPurchaseValue();
    }

    /**
     * Jednostavan obrtaj (komadni) za već pripremljeni period:
     * salesQtyPeriod / (trenutna_količina) – aproksimacija.
     */
    public Double getTurnoverApprox() {
        if (salesQtyPeriod == null) return null;
        double q = inventory.getQuantity();
        if (q <= 0) return null;
        return salesQtyPeriod / q;
    }

    /**
     * Dnevna potražnja ako znaš broj dana (pozvati izvana s realnim days).
     */
    public Double dailyDemand(long days) {
        if (salesQtyPeriod == null || days <= 0) return null;
        return salesQtyPeriod / days;
    }
}