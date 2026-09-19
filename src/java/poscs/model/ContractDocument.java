package poscs.model;

import java.sql.Timestamp;
import java.util.List;

/**
 * Một giấy tờ kèm theo hợp đồng (bảng {@code contract_documents}, V34).
 *
 * <p>CHỈ lưu LINK, không lưu file: hồ sơ của khách đang nằm trên Drive và vẫn
 * sẽ nằm ở đó. Hệ quả phải nhớ -- hệ thống không giữ bản sao nào, ai xoá file
 * trên Drive thì dòng này thành link chết, và quyền xem file do Drive quyết
 * chứ không phải POSCS.
 *
 * <p>Thay cho cột {@code contracts.attachment_url} cũ: một hợp đồng thật kéo
 * theo cả tập hồ sơ (nghiệm thu, bàn giao, thanh lý, hoá đơn...), mà cột đó
 * chỉ treo được đúng một thứ.
 */
public class ContractDocument {

    /**
     * Danh mục loại giấy tờ hiện dùng cho ô chọn.
     *
     * <p>Ở Java chứ không phải ENUM trong CSDL: cột {@code doc_type} là varchar
     * không ràng buộc giá trị, nên thêm một loại là sửa đúng danh sách này.
     * Danh mục CHÍNH THỨC còn phải chốt với khách hàng -- đây là bộ tạm đủ
     * dùng, dựng theo những giấy tờ đã nhắc tới trong lúc bàn.
     */
    public static final List<String> TYPES = List.of(
            "Hợp đồng đã ký",
            "Phụ lục đã ký",
            "Biên bản nghiệm thu",
            "Biên bản bàn giao",
            "Biên bản thanh lý",
            "Báo giá / Đơn đặt hàng",
            "Hoá đơn",
            "Khác");

    /** Loại mặc định của ô chọn -- và là loại mà V34 gán cho dữ liệu dời sang. */
    public static final String TYPE_SIGNED_CONTRACT = "Hợp đồng đã ký";

    private int documentId;
    private int contractId;
    private String docType;
    private String title;
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
}
