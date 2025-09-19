package model;

import java.util.List;

/**
 * DTO spojenih podataka za UI / izvoz.
 */
public class ProductInventoryView {
    private final Product product;
    private final InventoryRecord inventory;
    private final List<String> groupCodes;

    public ProductInventoryView(Product product,
                                InventoryRecord inventory,
                                List<String> groupCodes) {
        this.product = product;
        this.inventory = inventory;
        this.groupCodes = groupCodes;
    }

    public Product getProduct() { return product; }
    public InventoryRecord getInventory() { return inventory; }
    public List<String> getGroupCodes() { return groupCodes; }

    public Double getComputedAltQuantity() {
        if (product.getAreaPerPiece() == null || product.getAreaPerPiece() == 0) return null;
        if (product.getBaseUnit() == null || product.getAltUnit() == null) return null;
        if ("m2".equalsIgnoreCase(product.getBaseUnit())) {
            return inventory.getQuantity() / product.getAreaPerPiece();
        }
        if ("kom".equalsIgnoreCase(product.getBaseUnit())
                && "m2".equalsIgnoreCase(product.getAltUnit())) {
            // quantity je u komadima -> alt je m2
            return inventory.getQuantity() * product.getAreaPerPiece();
        }
        return null;
    }

    public Double getTotalValue() {
        return inventory.getPurchaseValue();
    }
}