package poscs.model;

import java.sql.Date;
import java.sql.Timestamp;

public class Enterprise {
    private int enterpriseId;
    private String enterpriseCode;
    private String enterpriseName;
    private String customerType;
    private String customerGroup;
    private String taxCode;
    private String email;
    private String phone;
    private String website;

    // Thuộc tính lưu trữ ID khóa ngoại
    private Integer addressId; // Dùng Integer thay vì int vì DB cho phép NULL
    private int accountOwnerId;

    // Thuộc tính Object để chứa dữ liệu join từ bảng khác
    private Address address;
    private User accountOwner;

    private String legalRepresentative;
    private String logoUrl;
    private String businessLicenseUrl;
    private String status;
    private Date joinDate;
    private RelationshipRating currentRelationshipRating; // Có thể null nếu chưa được đánh giá lần nào

    private Timestamp createdAt;
    private Timestamp updatedAt;
    private boolean isDeleted; // Ánh xạ với tinyint(1)

    // Constructor rỗng (Bắt buộc cho JavaBean)
    public Enterprise() {
    }

    // Constructor có tham số
    public Enterprise(int enterpriseId, String enterpriseCode, String enterpriseName, String customerType,
                       String customerGroup, String taxCode, String email, String phone, String website,
                       Integer addressId, int accountOwnerId, String legalRepresentative, String logoUrl,
                       String businessLicenseUrl, String status, Date joinDate,
                       RelationshipRating currentRelationshipRating, Timestamp createdAt,
                       Timestamp updatedAt, boolean isDeleted) {
        this.enterpriseId = enterpriseId;
        this.enterpriseCode = enterpriseCode;
        this.enterpriseName = enterpriseName;
        this.customerType = customerType;
        this.customerGroup = customerGroup;
        this.taxCode = taxCode;
        this.email = email;
        this.phone = phone;
        this.website = website;
        this.addressId = addressId;
        this.accountOwnerId = accountOwnerId;
        this.legalRepresentative = legalRepresentative;
        this.logoUrl = logoUrl;
        this.businessLicenseUrl = businessLicenseUrl;
        this.status = status;
        this.joinDate = joinDate;
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

    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }

    public String getCustomerGroup() { return customerGroup; }
    public void setCustomerGroup(String customerGroup) { this.customerGroup = customerGroup; }

    public String getTaxCode() { return taxCode; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public Integer getAddressId() { return addressId; }
    public void setAddressId(Integer addressId) { this.addressId = addressId; }

    public int getAccountOwnerId() { return accountOwnerId; }
    public void setAccountOwnerId(int accountOwnerId) { this.accountOwnerId = accountOwnerId; }

    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }

    public User getAccountOwner() { return accountOwner; }
    public void setAccountOwner(User accountOwner) { this.accountOwner = accountOwner; }

    public String getLegalRepresentative() { return legalRepresentative; }
    public void setLegalRepresentative(String legalRepresentative) { this.legalRepresentative = legalRepresentative; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getBusinessLicenseUrl() { return businessLicenseUrl; }
    public void setBusinessLicenseUrl(String businessLicenseUrl) { this.businessLicenseUrl = businessLicenseUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getJoinDate() { return joinDate; }
    public void setJoinDate(Date joinDate) { this.joinDate = joinDate; }

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
