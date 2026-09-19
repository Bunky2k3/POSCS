package poscs.model;

import java.sql.Date;
import java.sql.Timestamp;

public class Contract {
    private int contractId;

    /**
     * Hợp đồng gốc mà bản ghi này là PHỤ LỤC của nó; null nếu đây là hợp đồng
     * gốc.
     *
     * <p>Phụ lục là một hợp đồng đầy đủ -- mã riêng, thời hạn riêng, hàng hoá
     * riêng, và chính nó cũng phải được ký -- nên nó nằm cùng bảng chứ không
     * có bảng riêng. MỘT TẦNG: phụ lục của phụ lục bị từ chối ở
     * {@code ContractDAO.insert}, vì khoá ngoại tự trỏ không ép được điều đó.
     */
    private Integer parentContractId;

    /** Mã hợp đồng gốc -- join sẵn để màn hình khỏi tra thêm; null ở hợp đồng gốc. */
    private String parentContractCode;

    /**
     * Số phụ lục đang treo vào hợp đồng này (đã trừ bản ghi đã huỷ).
     *
     * <p>ĐẾM lúc đọc, không phải cột trong CSDL: một cột "có phụ lục" là nguồn
     * sự thật thứ hai bên cạnh chính các dòng phụ lục, và sớm muộn hai chỗ nói
     * hai điều khác nhau.
     */
    private int amendmentCount;

    /**
     * Tổng ĐIỀU CHỈNH giá trị mà các phụ lục ĐÃ KÝ mang lại cho hợp đồng này
     * (0 khi không có phụ lục nào, hoặc khi đang đứng trên chính một phụ lục).
     *
     * <p>Cộng lúc đọc, cùng lý do với {@link #amendmentCount}: ghi ngược vào
     * {@code contract_value} của hợp đồng gốc là xoá mất con số IN TRÊN TỜ GIẤY
     * đã ký, mà chênh lệch giữa con số đó và giá trị hiện hành mới là thứ đi
     * hỏi khi đối chiếu.
     *
     * <p>CÓ DẤU: phụ lục giảm trừ hạng mục mang giá trị âm.
     */
    private java.math.BigDecimal amendmentValueSigned = java.math.BigDecimal.ZERO;

    /**
     * Như trên nhưng của các phụ lục còn là bản NHÁP -- chưa ai ký nên chưa
     * đổi được giá trị hợp đồng, nhưng vẫn phải thấy được là có cái đang treo.
     */
    private java.math.BigDecimal amendmentValuePending = java.math.BigDecimal.ZERO;

    /**
     * Tên các phòng ĐANG GIỮ hợp đồng này, ngăn bằng dấu phẩy; null khi không
     * có chặng nào đang mở.
     *
     * <p>Gom ở SQL chứ không tra thêm một lượt cho mỗi dòng: danh sách hợp đồng
     * hiện 10 dòng một trang, mỗi dòng một truy vấn là 10 lượt đi CSDL cho một
     * nhãn nhỏ.
     */
    private String pendingDepartments;

    /** Số ngày của chặng đang chờ LÂU NHẤT; 0 khi không có chặng nào đang mở. */
    private int pendingHandoverDays;

    /**
     * Mã hợp đồng, chính là số ghi trên bản giấy ("01/2026/HĐKT-POSTEF").
     *
     * <p>NGƯỜI DÙNG NHẬP, không sinh tự động (V28). Trước đó hệ thống sinh
     * HD-xxxx và V25 thêm một cột `contract_number` riêng cho số thật; khách
     * hàng chốt lại rằng mã chính là số trên giấy, nên hai thứ đó gộp làm một.
     *
     * <p>Là định danh hiển thị ở khắp nơi -- phiếu hỗ trợ, màn sản phẩm, thông
     * báo sắp hết hạn đều đọc nó.
     */
    private String contractCode;
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

    // ------------------------------------------------------------------
    // Người ký của CHÍNH hợp đồng này (V27).
    //
    // Khác enterprises.legal_representative: cái kia là đại diện pháp luật ở
    // cấp công ty, còn đây là người đặt bút ký trên đúng tờ hợp đồng này. Có
    // uỷ quyền thì hai thứ khác nhau, và sửa đại diện pháp luật của công ty
    // KHÔNG được làm đổi tên người đã ký một hợp đồng đã ký xong.
    // ------------------------------------------------------------------

    /** Người ký bên mình (POSTEF). */
    private String signerName;
    private String signerPosition;

    /** Người ký bên đối tác. */
    private String counterpartySignerName;
    private String counterpartySignerPosition;

    /** Số/ngày giấy uỷ quyền, khi người ký không phải đại diện pháp luật. */
    private String authorizationRef;

    private String signingPlace;

    /**
     * Giá trị hợp đồng theo ĐIỀU KHOẢN hai bên ký.
     *
     * <p>KHÁC tổng các kỳ thanh toán (contract_payments): cái kia là thực tế
     * thu/chi. Chỗ lệch giữa hai con số chính là công nợ.
     */
    private java.math.BigDecimal contractValue;

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
                     int ownerId, String status, Timestamp createdAt,
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
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isDeleted = isDeleted;
    }

    // Các hàm Getters và Setters
    public int getContractId() { return contractId; }
    public void setContractId(int contractId) { this.contractId = contractId; }

    public Integer getParentContractId() { return parentContractId; }
    public void setParentContractId(Integer parentContractId) { this.parentContractId = parentContractId; }

    public String getParentContractCode() { return parentContractCode; }
    public void setParentContractCode(String parentContractCode) { this.parentContractCode = parentContractCode; }

    public int getAmendmentCount() { return amendmentCount; }
    public void setAmendmentCount(int amendmentCount) { this.amendmentCount = amendmentCount; }

    public java.math.BigDecimal getAmendmentValueSigned() { return amendmentValueSigned; }
    public void setAmendmentValueSigned(java.math.BigDecimal v) {
        this.amendmentValueSigned = v == null ? java.math.BigDecimal.ZERO : v;
    }

    public java.math.BigDecimal getAmendmentValuePending() { return amendmentValuePending; }
    public void setAmendmentValuePending(java.math.BigDecimal v) {
        this.amendmentValuePending = v == null ? java.math.BigDecimal.ZERO : v;
    }

    public String getPendingDepartments() { return pendingDepartments; }
    public void setPendingDepartments(String pendingDepartments) { this.pendingDepartments = pendingDepartments; }

    public int getPendingHandoverDays() { return pendingHandoverDays; }
    public void setPendingHandoverDays(int pendingHandoverDays) { this.pendingHandoverDays = pendingHandoverDays; }

    /** true nếu hợp đồng đang nằm chờ ở ít nhất một phòng. */
    public boolean isWaitingAtDepartment() {
        return pendingDepartments != null && !pendingDepartments.isEmpty();
    }

    /** true nếu có phụ lục đã ký làm đổi giá trị -- điều kiện để màn hình bày ba dòng giá trị. */
    public boolean isValueAdjusted() {
        return amendmentValueSigned.signum() != 0;
    }

    /**
     * Giá trị hợp đồng HIỆN HÀNH: giá trị gốc cộng mọi điều chỉnh của phụ lục
     * đã ký. Đây là con số trả lời câu "bây giờ hợp đồng này bao nhiêu tiền".
     *
     * <p>null khi hợp đồng chưa chốt giá VÀ chưa có phụ lục nào đổi giá -- giữ
     * nguyên nghĩa "chưa chốt" của {@link #getContractValue()} thay vì hiện 0,
     * vì 0 đồng là một điều khoản có thật, khác hẳn chỗ còn để trống.
     */
    public java.math.BigDecimal getCurrentValue() {
        if (contractValue == null) {
            return isValueAdjusted() ? amendmentValueSigned : null;
        }
        return contractValue.add(amendmentValueSigned);
    }

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

    public String getSignerName() { return signerName; }
    public void setSignerName(String signerName) { this.signerName = signerName; }

    public String getSignerPosition() { return signerPosition; }
    public void setSignerPosition(String signerPosition) { this.signerPosition = signerPosition; }

    public String getCounterpartySignerName() { return counterpartySignerName; }
    public void setCounterpartySignerName(String v) { this.counterpartySignerName = v; }

    public String getCounterpartySignerPosition() { return counterpartySignerPosition; }
    public void setCounterpartySignerPosition(String v) { this.counterpartySignerPosition = v; }

    public String getAuthorizationRef() { return authorizationRef; }
    public void setAuthorizationRef(String authorizationRef) { this.authorizationRef = authorizationRef; }

    public String getSigningPlace() { return signingPlace; }
    public void setSigningPlace(String signingPlace) { this.signingPlace = signingPlace; }

    public java.math.BigDecimal getContractValue() { return contractValue; }
    public void setContractValue(java.math.BigDecimal contractValue) { this.contractValue = contractValue; }

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
     * true nếu hợp đồng đã ký và CHƯA đóng băng -- quãng duy nhất lập được phụ
     * lục.
     *
     * <p>Hai đầu đều bị loại có lý do khác nhau: bản nháp thì sửa thẳng được
     * nên phụ lục chỉ là đường vòng, còn sau thanh lý thì hợp đồng đã chấm dứt
     * -- phát sinh lúc đó là hợp đồng MỚI, không phải sửa đổi của một thứ đã
     * hết hiệu lực.
     */
    public boolean isSigned() {
        return !isDraft() && !isFrozen();
    }

    /** true nếu bản ghi này là phụ lục của một hợp đồng khác. */
    public boolean isAmendment() { return parentContractId != null; }

    /**
     * true nếu các trường ĐIỀU KHOẢN đã khoá, chỉ còn dữ liệu quản trị nội bộ
     * sửa được.
     *
     * <p>Từ lúc ký trở đi, nội dung hợp đồng là chứng cứ pháp lý: đổi nó phải
     * đi qua phụ lục. Còn người phụ trách và link bản PDF đã ký thì không nằm
     * trên tờ giấy nào -- chúng vẫn sửa được, và link thường chỉ có SAU khi ký.
     * Chốt chặn thật nằm ở {@code ContractDAO.update}, không phải ở JSP.
     */
    public boolean isTermsLocked() { return !isDraft(); }

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
