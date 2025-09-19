package model;

public class Supplier {
    private final String code;
    private final String name;
    private final String contact;
    private final boolean active;

    public Supplier(String code, String name, String contact, boolean active) {
        this.code = code;
        this.name = name;
        this.contact = contact;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getContact() { return contact; }
    public boolean isActive() { return active; }
}