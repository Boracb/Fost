package dao;

import model.SalesRecord;

import java.time.LocalDate;

public interface SalesDao {
    double getSoldQtyByRange(String productCode, LocalDate from, LocalDate to) throws Exception;
    double getCOGSByRange(String productCode, LocalDate from, LocalDate to) throws Exception;
    void upsert(SalesRecord rec) throws Exception;

    // NOVO
    void deleteAll() throws Exception;
}