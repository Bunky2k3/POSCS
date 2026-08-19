package poscs.model;

public class ProductCategory {
    private int categoryId;
    private String categoryName;
    private Integer parentCategoryId; // Dùng Integer thay vì int vì DB cho phép NULL
    private int displayOrder;
    private boolean isDeleted; // Ánh xạ với tinyint(1)

    // Constructor rỗng (Bắt buộc cho JavaBean)
    public ProductCategory() {
    }

    public ProductCategory(int categoryId, String categoryName, Integer parentCategoryId,
                            int displayOrder, boolean isDeleted) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.parentCategoryId = parentCategoryId;
        this.displayOrder = displayOrder;
        this.isDeleted = isDeleted;
    }

    // Các hàm Getters và Setters
    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public Integer getParentCategoryId() { return parentCategoryId; }
    public void setParentCategoryId(Integer parentCategoryId) { this.parentCategoryId = parentCategoryId; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean isDeleted) { this.isDeleted = isDeleted; }

    @Override
    public String toString() {
        return "ProductCategory{" + "categoryId=" + categoryId + ", categoryName='" + categoryName + '\'' + '}';
    }
}
