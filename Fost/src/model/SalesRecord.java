package model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents a single sales (or document) line aggregated at (product_code + date + doc_type + doc_no) level.
 * Matches UNIQUE(product_code, date, doc_type, doc_no) constraint in 'sales' table.
 */
public class SalesRecord {

    private String productCode;
    private LocalDate date;
    private double quantity;

    private String docType;   // may be empty but never null when persisted
    private String docNo;     // may be empty but never null when persisted

    // Monetary amounts (nullable)
    private BigDecimal netAmount;
    private BigDecimal grossAmount;
    private BigDecimal vatAmount;
    private BigDecimal discountAmount;

    // Optional extra fields (nullable)
    private String customerCode;
    private Double cogsAmount;     // cost of goods sold (per line total, not unit)

    public SalesRecord() {
    }

    public SalesRecord(String productCode,
                       LocalDate date,
                       double quantity,
                       String docType,
                       String docNo,
                       BigDecimal netAmount,
                       BigDecimal grossAmount,
                       BigDecimal vatAmount,
                       BigDecimal discountAmount,
                       String customerCode,
                       Double cogsAmount) {
        this.productCode = productCode;
        this.date = date;
        this.quantity = quantity;
        this.docType = docType;
        this.docNo = docNo;
        this.netAmount = netAmount;
        this.grossAmount = grossAmount;
        this.vatAmount = vatAmount;
        this.discountAmount = discountAmount;
        this.customerCode = customerCode;
        this.cogsAmount = cogsAmount;
    }

    // Getters
    public String getProductCode() { return productCode; }
    public LocalDate getDate() { return date; }
    public double getQuantity() { return quantity; }
    public String getDocType() { return docType; }
    public String getDocNo() { return docNo; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getVatAmount() { return vatAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public String getCustomerCode() { return customerCode; }
    public Double getCogsAmount() { return cogsAmount; }

    // Setters (needed by importer)
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public void setDate(LocalDate date) { this.date = date; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    public void setDocType(String docType) { this.docType = docType; }
    public void setDocNo(String docNo) { this.docNo = docNo; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
    public void setVatAmount(BigDecimal vatAmount) { this.vatAmount = vatAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    public void setCogsAmount(Double cogsAmount) { this.cogsAmount = cogsAmount; }

    // Convenience fluent builders (optional)
    public SalesRecord withCustomer(String customerCode) { this.customerCode = customerCode; return this; }
    public SalesRecord withCogs(Double cogsAmount) { this.cogsAmount = cogsAmount; return this; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SalesRecord)) return false;
        SalesRecord that = (SalesRecord) o;
        return Objects.equals(productCode, that.productCode)
                && Objects.equals(date, that.date)
                && Objects.equals(docType, that.docType)
                && Objects.equals(docNo, that.docNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productCode, date, docType, docNo);
    }

    @Override
    public String toString() {
        return "SalesRecord{" +
                "productCode='" + productCode + '\'' +
                ", date=" + date +
                ", quantity=" + quantity +
                ", docType='" + docType + '\'' +
                ", docNo='" + docNo + '\'' +
                '}';
    }
}