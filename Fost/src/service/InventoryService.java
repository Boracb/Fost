package service;

import dao.InventoryDao;
import dao.ProductDao;
import model.InventoryRecord;
import model.ProductInventoryView;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * InventoryService – servisni sloj nad InventoryDao + ProductDao.
 * Omogućava dohvat punog pogleda, dohvat s agregiranom prodajom,
 * te jednostavne operacije podešavanja količina (adjust / set).
 */
public class InventoryService {

    private final InventoryDao inventoryDao;
    private final ProductDao productDao;
    private boolean preventNegative = false; // ako želiš blokirati negativne zalihe

    public InventoryService(InventoryDao inventoryDao, ProductDao productDao) {
        this.inventoryDao = inventoryDao;
        this.productDao = productDao;
    }

    /**
     * Ako pozoveš setPreventNegative(true) – adjust/setQuantity neće dopustiti < 0.
     */
    public InventoryService setPreventNegative(boolean prevent) {
        this.preventNegative = prevent;
        return this;
    }

    public List<ProductInventoryView> fullView() throws SQLException {
        return inventoryDao.fullView();
    }

    public List<ProductInventoryView> fullViewWithSales(LocalDate from, LocalDate to) throws SQLException {
        return inventoryDao.fullViewWithSales(from, to);
    }

    /**
     * Povećava ili smanjuje količinu (delta može biti negativan).
     * @param productCode šifra artikla
     * @param delta koliko dodati (npr. +1 ili -1)
     */
    public InventoryRecord adjustQuantity(String productCode, double delta) throws SQLException {
        Optional<InventoryRecord> currentOpt = inventoryDao.find(productCode);
        double base = currentOpt.map(InventoryRecord::getQuantity).orElse(0.0);
        double newQty = base + delta;

        if (preventNegative && newQty < 0) {
            throw new IllegalArgumentException("Rezultat bi bio negativan (" + newQty + ") – operacija odbijena.");
        }

        // Nabavna jedinična cijena (ako proizvod postoje i ima purchase_unit_price)
        var productOpt = productDao.find(productCode);
        Double unitPrice = productOpt.flatMap(p ->
                Optional.ofNullable(p.getPurchaseUnitPrice())
        ).orElse(null);

        inventoryDao.upsertQuantity(productCode, newQty, unitPrice);
        return inventoryDao.find(productCode).orElse(null);
    }

    /**
     * Postavlja apsolutnu količinu.
     */
    public InventoryRecord setQuantity(String productCode, double newQty) throws SQLException {
        if (preventNegative && newQty < 0) {
            throw new IllegalArgumentException("Negativna količina nije dopuštena: " + newQty);
        }
        var productOpt = productDao.find(productCode);
        Double unitPrice = productOpt.flatMap(p ->
                Optional.ofNullable(p.getPurchaseUnitPrice())
        ).orElse(null);

        inventoryDao.upsertQuantity(productCode, newQty, unitPrice);
        return inventoryDao.find(productCode).orElse(null);
    }
}