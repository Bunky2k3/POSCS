package poscs.model;

import java.sql.Date;
import java.sql.Timestamp;

public class Contract {
    private int contractId;
    private String contractCode;
    private String title;
    private String contractType;
    private Date signingDate;
    private Date effectiveDate;
    private Date endDate;

    // Thuộc tính lưu trữ ID khóa ngoại
    private int enterpriseId;
    private int ownerId;

    // Thuộc tính Object để chứa dữ liệu join từ bảng khác
    private Enterprise enterprise;
    private User owner;

    private String attachmentUrl;
    private String status;

    private Timestamp createdAt;
    private Timestamp updatedAt;
    private boolean isDeleted; // Ánh xạ với tinyint(1)

    // Constructor rỗng (Bắt buộc cho JavaBean)
    public Contract() {
    }

    // Constructor có tham số
    public Contract(int contractId, String contractCode, String title, String contractType,
                     Date signingDate, Date effectiveDate, Date endDate, int enterpriseId,
                     int ownerId, String attachmentUrl, String status, Timestamp createdAt,
                     Timestamp updatedAt, boolean isDeleted) {
        this.contractId = contractId;
        this.contractCode = contractCode;
        this.title = title;
        this.contractType = contractType;
        this.signingDate = signingDate;
        this.effectiveDate = effectiveDate;
        this.endDate = endDate;
        this.enterpriseId = enterpriseId;
        this.ownerId = ownerId;
        this.attachmentUrl = attachmentUrl;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isDeleted = isDeleted;
    }

    // Các hàm Getters và Setters
    public int getContractId() { return contractId; }
    public void setContractId(int contractId) { this.contractId = contractId; }

    public String getContractCode() { return contractCode; }
    public void setContractCode(String contractCode) { this.contractCode = contractCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContractType() { return contractType; }
    public void setContractType(String contractType) { this.contractType = contractType; }

    public Date getSigningDate() { return signingDate; }
    public void setSigningDate(Date signingDate) { this.signingDate = signingDate; }

    public Date getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(Date effectiveDate) { this.effectiveDate = effectiveDate; }

    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }

    public int getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(int enterpriseId) { this.enterpriseId = enterpriseId; }

    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }

    public Enterprise getEnterprise() { return enterprise; }
    public void setEnterprise(Enterprise enterprise) { this.enterprise = enterprise; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean isDeleted) { this.isDeleted = isDeleted; }

    @Override
    public String toString() {
        return "Contract{" + "contractId=" + contractId + ", contractCode='" + contractCode + '\'' + '}';
    }
}
