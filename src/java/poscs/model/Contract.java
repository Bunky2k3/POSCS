package poscs.model;

import java.sql.Date;
import java.sql.Timestamp;

public class Contract {
    private int contractId;

    /** Mã NỘI BỘ do hệ thống sinh, dạng HD-0015. Định danh của bản ghi. */
    private String contractCode;

    /**
     * Số hợp đồng THẬT in trên bản giấy, kiểu "123/2026/HĐKT-POSTEF".
     *
     * <p>Khác {@link #contractCode}: cái kia là mã nội bộ mình tự sinh, cái này
     * là thứ khách hàng đọc khi gọi điện. Có thể null -- bản nháp chưa ký thì
     * thường chưa được cấp số.
     */
    private String contractNumber;
    private String title;
    private String contractType;
    /** 'Bán' = mình bán ra, 'Mua' = mình mua vào -- xem ghi chú đầu V21. */
    private String direction;
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

    /**
     * Trục LỊCH: "Chưa hiệu lực"/"Đang hiệu lực"/"Sắp hết hạn"/"Đã hết hạn".
     * Tính lại từ effective_date/end_date mỗi lần đọc (BR-17), không ai đặt
     * được -- xem ContractDAO.computeStatus.
     */
    private String status;

    /**
     * Trục TIẾN ĐỘ: "Nháp"/"Đã ký"/"Đã thanh lý"/"Chấm dứt sớm". Do người đặt.
     *
     * ĐỪNG trộn với {@link #status}. Hai trục lệch nhau ở CẢ HAI chiều -- hết
     * hạn theo lịch mà chưa thanh lý, và thanh lý sớm mà theo lịch vẫn đang
     * hiệu lực -- nên không suy ra cái này từ cái kia được.
     */
    private String progressStatus;

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

    public String getContractNumber() { return contractNumber; }
    public void setContractNumber(String contractNumber) { this.contractNumber = contractNumber; }

    public String getContractCode() { return contractCode; }
    public void setContractCode(String contractCode) { this.contractCode = contractCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContractType() { return contractType; }
    public void setContractType(String contractType) { this.contractType = contractType; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

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

    public String getProgressStatus() { return progressStatus; }
    public void setProgressStatus(String progressStatus) { this.progressStatus = progressStatus; }

    // Chuỗi viết lại ở đây thay vì tham chiếu ContractDAO.PROGRESS_*: model
    // không phụ thuộc ngược lên tầng DAO. Có test canh hai bên không lệch nhau
    // (ContractDAOTest.progressConstants_matchTheOnesOnTheModel).
    private static final String DRAFT = "Nháp";
    private static final String LIQUIDATED = "Đã thanh lý";
    private static final String TERMINATED = "Chấm dứt sớm";

    /** true nếu hợp đồng chưa ký -- còn sửa thoải mái, và còn xoá được. */
    public boolean isDraft() { return DRAFT.equals(progressStatus); }

    /**
     * true nếu hợp đồng đã đóng băng (thanh lý hoặc chấm dứt sớm): không đổi
     * được nữa, kể cả cấp cao. Phát sinh sau đó phải đi qua hợp đồng mới.
     */
    public boolean isFrozen() {
        return LIQUIDATED.equals(progressStatus) || TERMINATED.equals(progressStatus);
    }

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
