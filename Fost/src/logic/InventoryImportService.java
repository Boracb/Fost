package logic;

import db.InventoryStateDatabaseHelper;
import model.StockState;

import java.io.File;
import java.util.List;

public class InventoryImportService {

    @FunctionalInterface
    public interface StockReader {
        List<StockState> parse(File file) throws Exception;
    }

    private final StockReader reader;
    private final InventoryStateDatabaseHelper helper;

    public InventoryImportService(StockReader reader,
                                  InventoryStateDatabaseHelper helper) {
        this.reader = reader;
        this.helper = helper;
    }

    public void importCurrentState(File excelFile) throws Exception {
        helper.ensureSchema();
        List<StockState> list = reader.parse(excelFile);
        helper.bulkUpsert(list);
    }

    public void safeReplaceAll(File excelFile) throws Exception {
        helper.ensureSchema();
        List<StockState> list = reader.parse(excelFile);
        helper.truncateAll();
        helper.bulkUpsert(list);
    }
}