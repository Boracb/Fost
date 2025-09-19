package model;

public class ProductGroup {
    private final String code;
    private final String name;
    private final String parentCode;
    private final String groupType;

    public ProductGroup(String code, String name, String parentCode, String groupType) {
        this.code = code;
        this.name = name;
        this.parentCode = parentCode;
        this.groupType = groupType;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getParentCode() { return parentCode; }
    public String getGroupType() { return groupType; }
}