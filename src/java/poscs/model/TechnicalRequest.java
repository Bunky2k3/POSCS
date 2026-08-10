package poscs.model;

import java.sql.Date;
import java.sql.Timestamp;

public class TechnicalRequest {
    private int ticketId;
    private String ticketCode;

    // Thuộc tính lưu trữ ID khóa ngoại
    private int enterpriseId;
    private Integer contractId; // Dùng Integer thay vì int vì DB cho phép NULL
    private int assignedTechnicianId;
    private int createdBy;

    // Thuộc tính Object để chứa dữ liệu join từ bảng khác
    private Enterprise enterprise;
    private Contract contract;
    private User assignedTechnician;
    private User createdByUser;

    private String ticketType;
    private String priority;
    private String receptionChannel;
    private Timestamp slaDeadline;
    private Date createdDate;
    private String description;
    private boolean warranty; // Ánh xạ với is_warranty (tinyint(1))
    private String status;
    private String resolutionSummary;
    private Timestamp resolvedAt;

    private Timestamp createdAt;
    private Timestamp updatedAt;
    private boolean isDeleted; // Ánh xạ với tinyint(1)

    // Constructor rỗng (Bắt buộc cho JavaBean)
    public TechnicalRequest() {
    }

    // Constructor có tham số
    public TechnicalRequest(int ticketId, String ticketCode, int enterpriseId, Integer contractId,
                             String ticketType, String priority, String receptionChannel,
                             Timestamp slaDeadline, int assignedTechnicianId, int createdBy,
                             Date createdDate, String description, boolean warranty, String status,
                             String resolutionSummary, Timestamp resolvedAt, Timestamp createdAt,
                             Timestamp updatedAt, boolean isDeleted) {
        this.ticketId = ticketId;
        this.ticketCode = ticketCode;
        this.enterpriseId = enterpriseId;
        this.contractId = contractId;
        this.ticketType = ticketType;
        this.priority = priority;
        this.receptionChannel = receptionChannel;
        this.slaDeadline = slaDeadline;
        this.assignedTechnicianId = assignedTechnicianId;
        this.createdBy = createdBy;
        this.createdDate = createdDate;
        this.description = description;
        this.warranty = warranty;
        this.status = status;
        this.resolutionSummary = resolutionSummary;
        this.resolvedAt = resolvedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isDeleted = isDeleted;
    }

    // Các hàm Getters và Setters
    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }

    public int getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(int enterpriseId) { this.enterpriseId = enterpriseId; }

    public Integer getContractId() { return contractId; }
    public void setContractId(Integer contractId) { this.contractId = contractId; }

    public int getAssignedTechnicianId() { return assignedTechnicianId; }
    public void setAssignedTechnicianId(int assignedTechnicianId) { this.assignedTechnicianId = assignedTechnicianId; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public Enterprise getEnterprise() { return enterprise; }
    public void setEnterprise(Enterprise enterprise) { this.enterprise = enterprise; }

    public Contract getContract() { return contract; }
    public void setContract(Contract contract) { this.contract = contract; }

    public User getAssignedTechnician() { return assignedTechnician; }
    public void setAssignedTechnician(User assignedTechnician) { this.assignedTechnician = assignedTechnician; }

    public User getCreatedByUser() { return createdByUser; }
    public void setCreatedByUser(User createdByUser) { this.createdByUser = createdByUser; }

    public String getTicketType() { return ticketType; }
    public void setTicketType(String ticketType) { this.ticketType = ticketType; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getReceptionChannel() { return receptionChannel; }
    public void setReceptionChannel(String receptionChannel) { this.receptionChannel = receptionChannel; }

    public Timestamp getSlaDeadline() { return slaDeadline; }
    public void setSlaDeadline(Timestamp slaDeadline) { this.slaDeadline = slaDeadline; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isWarranty() { return warranty; }
    public void setWarranty(boolean warranty) { this.warranty = warranty; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResolutionSummary() { return resolutionSummary; }
    public void setResolutionSummary(String resolutionSummary) { this.resolutionSummary = resolutionSummary; }

    public Timestamp getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Timestamp resolvedAt) { this.resolvedAt = resolvedAt; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean isDeleted) { this.isDeleted = isDeleted; }

    @Override
    public String toString() {
        return "TechnicalRequest{" + "ticketId=" + ticketId + ", ticketCode='" + ticketCode + '\'' + '}';
    }
}
