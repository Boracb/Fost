package dao;

import java.time.LocalDate;

public interface SalesDao {
    double getSoldQtyByRange(String productCode, LocalDate from, LocalDate to) throws Exception;

    // NOVO: suma nabavne vrijednosti (COGS) za proizvod u periodu
    double getCOGSByRange(String productCode, LocalDate from, LocalDate to) throws Exception;

    void upsert(model.SalesRecord rec) throws Exception;
}