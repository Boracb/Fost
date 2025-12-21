package service;

import dao.InventoryDao;
import dao.SalesDao;

/**
 * Servis za masovne operacije (brisanje svih prodaja i svih zaliha).
 */
public class SalesMaintenanceService {

    private final SalesDao salesDao;
    private final InventoryDao inventoryDao;

    public SalesMaintenanceService(SalesDao salesDao, InventoryDao inventoryDao) {
        this.salesDao = salesDao;
        this.inventoryDao = inventoryDao;
    }

    public void clearAllSales() throws Exception {
        salesDao.deleteAll();
    }

    public void clearAllInventory() throws Exception {
        inventoryDao.deleteAll();
    }

    /**
     * Po potrebi: kombinirano.
     */
    public void clearAllSalesAndInventory() throws Exception {
        clearAllSales();
        clearAllInventory();
    }
}