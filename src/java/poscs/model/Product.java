package poscs.model;

import java.sql.Timestamp;

public class Product {
    private int productId;
    private String productCode;
    private String productName;
    private String description;
    private String imageUrl;
    private String catalogueUrl;

    // Thuộc tính lưu trữ ID khóa ngoại
    private int categoryId;

    // Thuộc tính Object để chứa dữ liệu join từ bảng khác
    private ProductCategory category;

    private Timestamp createdAt;
    private Timestamp updatedAt;
    private boolean isDeleted; // Ánh xạ với tinyint(1)

    // Constructor rỗng (Bắt buộc cho JavaBean)
    public Product() {
    }

    public Product(int productId, String productCode, String productName, String description,
                    String imageUrl, String catalogueUrl, int categoryId, Timestamp createdAt,
                    Timestamp updatedAt, boolean isDeleted) {
        this.productId = productId;
        this.productCode = productCode;
        this.productName = productName;
        this.description = description;
        this.imageUrl = imageUrl;
        this.catalogueUrl = catalogueUrl;
        this.categoryId = categoryId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isDeleted = isDeleted;
    }

    // Các hàm Getters và Setters
    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getCatalogueUrl() { return catalogueUrl; }
    public void setCatalogueUrl(String catalogueUrl) { this.catalogueUrl = catalogueUrl; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public ProductCategory getCategory() { return category; }
    public void setCategory(ProductCategory category) { this.category = category; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean isDeleted) { this.isDeleted = isDeleted; }

    @Override
    public String toString() {
        return "Product{" + "productId=" + productId + ", productName='" + productName + '\'' + '}';
    }
}
