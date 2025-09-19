package logic;

import db.InventoryStateDatabaseHelper;
import excel.ExcelInventoryStateReader;
import model.StockState;

import java.io.File;
import java.util.List;

public class InventoryResetService {

    private final InventoryStateDatabaseHelper db;

    public InventoryResetService(InventoryStateDatabaseHelper db) {
        this.db = db;
    }

    public void clearAll() throws Exception {
        db.ensureSchema();
        db.truncateAll();
    }

    public void clearAndReimport(File excel, ExcelInventoryStateReader reader) throws Exception {
        clearAll();
        List<StockState> list = reader.parse(excel);
        db.bulkUpsert(list);
    }
}