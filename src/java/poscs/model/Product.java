package poscs.model;

import java.sql.Timestamp;
import java.util.List;

public class Product {
    private int productId;
    private String productCode;
    private String productName;
    private String description;

    // Thuộc tính lưu trữ ID khóa ngoại
    private int categoryId;

    // Thuộc tính Object để chứa dữ liệu join từ bảng khác
    private ProductCategory category;
    private List<ProductImage> images;
    private List<ProductCatalogue> catalogues;

    private Timestamp createdAt;
    private Timestamp updatedAt;
    private boolean isDeleted; // Ánh xạ với tinyint(1)

    // Constructor rỗng (Bắt buộc cho JavaBean)
    public Product() {
    }

    public Product(int productId, String productCode, String productName, String description,
                    int categoryId, Timestamp createdAt, Timestamp updatedAt, boolean isDeleted) {
        this.productId = productId;
        this.productCode = productCode;
        this.productName = productName;
        this.description = description;
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

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public ProductCategory getCategory() { return category; }
    public void setCategory(ProductCategory category) { this.category = category; }

    public List<ProductImage> getImages() { return images; }
    public void setImages(List<ProductImage> images) { this.images = images; }

    public List<ProductCatalogue> getCatalogues() { return catalogues; }
    public void setCatalogues(List<ProductCatalogue> catalogues) { this.catalogues = catalogues; }

    /** URL ảnh đầu tiên (theo display_order), dùng làm ảnh đại diện ở lưới danh sách. Null nếu chưa có ảnh nào. */
    public String getPrimaryImageUrl() {
        return (images != null && !images.isEmpty()) ? images.get(0).getImageUrl() : null;
    }

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
