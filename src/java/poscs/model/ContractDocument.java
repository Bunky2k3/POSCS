package poscs.model;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

/**
 * Một giấy tờ của hợp đồng (bảng {@code contract_documents}, V34; dạng ô tích
 * từ V44, BR-59 / UC-52): một tờ = loại, ngày trên giấy, số giấy, đường dẫn
 * (không bắt buộc), ghi chú.
 *
 * <p>KHÔNG lưu file, chỉ lưu LINK nếu có: hồ sơ của khách đang nằm trên Drive và
 * vẫn sẽ nằm ở đó. Hệ quả phải nhớ -- hệ thống không giữ bản sao nào, ai xoá file
 * trên Drive thì dòng này thành link chết, và quyền xem file do Drive quyết
 * chứ không phải POSCS.
 *
 * <p>Thay cho cột {@code contracts.attachment_url} cũ: một hợp đồng thật kéo
 * theo cả tập hồ sơ (nghiệm thu, bàn giao, thanh lý, hoá đơn...), mà cột đó
 * chỉ treo được đúng một thứ.
 */
public class ContractDocument {

    /**
     * Danh mục loại giấy tờ (BR-59), theo đúng thứ tự hiện trên tab "Giấy tờ".
     *
     * <p>Ở Java chứ không phải ENUM trong CSDL: cột {@code doc_type} là varchar
     * không ràng buộc giá trị, nên thêm một loại là sửa đúng danh sách này.
     * Nguồn: quyết định 15 trong {@code task_tracker.md} / SPEC §11 (người dùng chốt
     * 19/11/2026, dựng theo hồ sơ một hợp đồng mua bán thật). "Khác" luôn đứng
     * cuối và bắt nhập tên giấy tờ. Loại cũ trước V44 đã được đổi sang danh mục này.
     */
    public static final List<String> TYPES = List.of(
            "Phiếu GQNCKH",
            "Phiếu ĐCNB",
            "Biên bản giao nhận hàng hoá",
            "Hợp đồng đã ký",
            "Phụ lục đã ký",
            "Đề nghị tạm ứng",
            "Bảo lãnh tạm ứng",
            "Biên bản nghiệm thu bàn giao",
            "CO",
            "CQ",
            "Đề nghị thanh toán",
            "Hoá đơn GTGT",
            "Biên bản phạt",
            "Biên bản thanh lý",
            "Khác");

    /** Bản gốc của chính hợp đồng -- nút "Mở bản PDF" ở trang chi tiết lấy loại này. */
    public static final String TYPE_SIGNED_CONTRACT = "Hợp đồng đã ký";

    /** Loại "Khác": bắt nhập tên giấy tờ (BR-59), và là chỗ gom loại lạ khi hiển thị. */
    public static final String TYPE_OTHER = "Khác";

    private int documentId;
    private int contractId;
    private String docType;
    private String title;
    /** Ngày ghi trên tờ giấy (BR-59): bắt buộc, không sau hôm nay. */
    private Date docDate;
    /** Số giấy, tối đa 50 ký tự; không bắt buộc. */
    private String docNo;
    /** Đường dẫn tới bản scan, KHÔNG bắt buộc từ V44 (nhiều tờ chỉ có bản giấy). */
    private String fileUrl;
    private String note;
    private int uploadedBy;
    private Timestamp uploadedAt;

    private boolean deleted;
    private Integer deletedBy;
    private Timestamp deletedAt;
    private String deleteReason;

    /** Tên người thêm giấy tờ -- join sẵn, để màn hình không phải tra thêm. */
    private String uploadedByName;
    private String deletedByName;

    public int getDocumentId() { return documentId; }
    public void setDocumentId(int documentId) { this.documentId = documentId; }

    public int getContractId() { return contractId; }
    public void setContractId(int contractId) { this.contractId = contractId; }

    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Date getDocDate() { return docDate; }
    public void setDocDate(Date docDate) { this.docDate = docDate; }

    public String getDocNo() { return docNo; }
    public void setDocNo(String docNo) { this.docNo = docNo; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public int getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(int uploadedBy) { this.uploadedBy = uploadedBy; }

    public Timestamp getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Timestamp uploadedAt) { this.uploadedAt = uploadedAt; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public Integer getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Integer deletedBy) { this.deletedBy = deletedBy; }

    public Timestamp getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Timestamp deletedAt) { this.deletedAt = deletedAt; }

    public String getDeleteReason() { return deleteReason; }
    public void setDeleteReason(String deleteReason) { this.deleteReason = deleteReason; }

    public String getUploadedByName() { return uploadedByName; }
    public void setUploadedByName(String uploadedByName) { this.uploadedByName = uploadedByName; }

    public String getDeletedByName() { return deletedByName; }
    public void setDeletedByName(String deletedByName) { this.deletedByName = deletedByName; }

    /** Nhãn hiện trên màn hình: tên người dùng đặt, không có thì lấy loại giấy tờ. */
    public String getDisplayName() {
        return title == null || title.trim().isEmpty() ? docType : title;
    }

    /**
     * Ô tích mà tờ này nằm dưới trên tab "Giấy tờ": chính loại của nó nếu loại có
     * trong {@link #TYPES}, còn không thì "Khác". Loại cũ đã được V44 đổi hết sang
     * danh mục mới; đây chỉ là chốt để một loại lạ (ghi thẳng vào CSDL) vẫn hiện ra
     * thay vì biến mất khỏi màn hình.
     */
    public String getCategory() {
        return docType != null && TYPES.contains(docType) ? docType : TYPE_OTHER;
    }
}
