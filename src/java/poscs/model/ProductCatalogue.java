package poscs.model;

import java.sql.Timestamp;

public class ProductCatalogue {
    private int catalogueId;
    private int productId;
    private String catalogueUrl;
    private String fileName;
    private int displayOrder;
    private Timestamp createdAt;

    public ProductCatalogue() {
    }

    public ProductCatalogue(int catalogueId, int productId, String catalogueUrl, String fileName,
                             int displayOrder, Timestamp createdAt) {
        this.catalogueId = catalogueId;
        this.productId = productId;
        this.catalogueUrl = catalogueUrl;
        this.fileName = fileName;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
    }

    public int getCatalogueId() { return catalogueId; }
    public void setCatalogueId(int catalogueId) { this.catalogueId = catalogueId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getCatalogueUrl() { return catalogueUrl; }
    public void setCatalogueUrl(String catalogueUrl) { this.catalogueUrl = catalogueUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "ProductCatalogue{" + "catalogueId=" + catalogueId + ", productId=" + productId + '}';
    }
}
