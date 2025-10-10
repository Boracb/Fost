package model;

/**
 * Model dobavljača.
 * Ako već imaš drukčiju verziju, samo osiguraj da postoji getSupplierCode().
 */
public class Supplier {
    private final String supplierCode;
    private final String name;
    private final String contact;
    private final String phone;
    private final String email;
    private final boolean active;

    public Supplier(String supplierCode,
                    String name,
                    String contact,
                    String phone,
                    String email,
                    boolean active) {
        this.supplierCode = supplierCode;
        this.name = name;
        this.contact = contact;
        this.phone = phone;
        this.email = email;
        this.active = active;
    }

    public String getSupplierCode() { return supplierCode; }
    public String getName()        { return name; }
    public String getContact()     { return contact; }
    public String getPhone()       { return phone; }
    public String getEmail()       { return email; }
    public boolean isActive()      { return active; }

    // Ako želiš kompatibilnost sa starim kodom koji koristi getCode():
    public String getCode() { return supplierCode; }
}