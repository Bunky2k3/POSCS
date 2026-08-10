package poscs.model;

import java.sql.Timestamp;

public class Enterprise {
    private int enterpriseId;
    private String enterpriseCode;
    private String enterpriseName;
    private String taxCode;
    private String email;
    private String phone;

    // Thuộc tính lưu trữ ID khóa ngoại
    private Integer addressId; // Dùng Integer thay vì int vì DB cho phép NULL

    // Thuộc tính Object để chứa dữ liệu join từ bảng khác
    private Address address;

    private String legalRepresentative;
    private String logoUrl;
    private String businessLicenseUrl;
    private String status;
    private RelationshipRating currentRelationshipRating; // Có thể null nếu chưa được đánh giá lần nào

    private Timestamp createdAt;
    private Timestamp updatedAt;
    private boolean isDeleted; // Ánh xạ với tinyint(1)

    // Constructor rỗng (Bắt buộc cho JavaBean)
    public Enterprise() {
    }

    // Constructor có tham số
    public Enterprise(int enterpriseId, String enterpriseCode, String enterpriseName, String taxCode,
                       String email, String phone, Integer addressId, String legalRepresentative,
                       String logoUrl, String businessLicenseUrl, String status,
                       RelationshipRating currentRelationshipRating, Timestamp createdAt,
                       Timestamp updatedAt, boolean isDeleted) {
        this.enterpriseId = enterpriseId;
        this.enterpriseCode = enterpriseCode;
        this.enterpriseName = enterpriseName;
        this.taxCode = taxCode;
        this.email = email;
        this.phone = phone;
        this.addressId = addressId;
        this.legalRepresentative = legalRepresentative;
        this.logoUrl = logoUrl;
        this.businessLicenseUrl = businessLicenseUrl;
        this.status = status;
        this.currentRelationshipRating = currentRelationshipRating;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isDeleted = isDeleted;
    }

    // Các hàm Getters và Setters
    public int getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(int enterpriseId) { this.enterpriseId = enterpriseId; }

    public String getEnterpriseCode() { return enterpriseCode; }
    public void setEnterpriseCode(String enterpriseCode) { this.enterpriseCode = enterpriseCode; }

    public String getEnterpriseName() { return enterpriseName; }
    public void setEnterpriseName(String enterpriseName) { this.enterpriseName = enterpriseName; }

    public String getTaxCode() { return taxCode; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Integer getAddressId() { return addressId; }
    public void setAddressId(Integer addressId) { this.addressId = addressId; }

    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }

    public String getLegalRepresentative() { return legalRepresentative; }
    public void setLegalRepresentative(String legalRepresentative) { this.legalRepresentative = legalRepresentative; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getBusinessLicenseUrl() { return businessLicenseUrl; }
    public void setBusinessLicenseUrl(String businessLicenseUrl) { this.businessLicenseUrl = businessLicenseUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public RelationshipRating getCurrentRelationshipRating() { return currentRelationshipRating; }
    public void setCurrentRelationshipRating(RelationshipRating currentRelationshipRating) { this.currentRelationshipRating = currentRelationshipRating; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean isDeleted) { this.isDeleted = isDeleted; }

    @Override
    public String toString() {
        return "Enterprise{" + "enterpriseId=" + enterpriseId + ", enterpriseName='" + enterpriseName + '\'' + '}';
    }
}
