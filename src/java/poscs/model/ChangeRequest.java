package poscs.model;

import java.sql.Timestamp;

/**
 * Một yêu cầu thay đổi dữ liệu do cấp dưới gửi lên cấp trên.
 *
 * <p>Sinh ra từ mô hình phân cấp chốt với khách hàng 14/09/2026: trên Khách
 * hàng và Hợp đồng, tầng lá chỉ được xem, muốn đổi gì thì gửi yêu cầu (xem
 * PERMISSIONS.md). Không có bảng này thì việc xin xỏ diễn ra ngoài hệ thống và
 * không để lại vết nào.
 *
 * <p>{@link #targetId} để null khi {@link #intent} là "Tạo mới" -- lúc đó chưa
 * có dòng nào để trỏ tới, nội dung đề xuất nằm trong
 * {@link #proposedContent}.
 */
public class ChangeRequest {

    /** Giá trị hợp lệ của resource_type -- đúng hai tài nguyên mà cây tổ chức siết. */
    public static final String RESOURCE_CUSTOMER = "Khách hàng";
    public static final String RESOURCE_CONTRACT = "Hợp đồng";

    /** Giá trị hợp lệ của intent. */
    public static final String INTENT_CREATE = "Tạo mới";
    public static final String INTENT_UPDATE = "Sửa";
    public static final String INTENT_DELETE = "Xoá";

    /**
     * Đề nghị lập PHỤ LỤC cho một hợp đồng đã ký.
     *
     * <p>Sinh ra vì luật vòng đời hợp đồng mâu thuẫn với hai intent bên trên:
     * hợp đồng đã ký thì không sửa và không xoá được nữa, kể cả bởi cấp trên
     * -- nên một yêu cầu "Sửa hợp đồng" gửi lên là yêu cầu cấp trên làm một
     * việc hệ thống không cho làm. Thứ cấp dưới thật sự cần xin là một PHỤ
     * LỤC, và đó là việc cấp trên làm được.
     *
     * <p>Chỉ dùng cho {@link #RESOURCE_CONTRACT} -- khách hàng không có khái
     * niệm phụ lục, ở đó "Sửa" vẫn đúng nghĩa.
     */
    public static final String INTENT_AMENDMENT = "Lập phụ lục";

    /** Giá trị hợp lệ của status. */
    public static final String STATUS_PENDING = "Chờ duyệt";
    public static final String STATUS_APPROVED = "Đã duyệt";
    public static final String STATUS_REJECTED = "Từ chối";

    private int requestId;
    private String resourceType;
    private String intent;
    /** null khi intent = "Tạo mới". */
    private Integer targetId;
    private String proposedContent;
    private String reason;

    private int requestedBy;
    private Integer reviewedBy;
    private String status;
    private String reviewNote;
    private Timestamp reviewedAt;
    private Timestamp createdAt;

    /** Join sẵn để màn hình khỏi phải tra thêm. */
    private User requester;
    private User reviewer;

    public ChangeRequest() {
    }

    public int getRequestId() { return requestId; }
    public void setRequestId(int requestId) { this.requestId = requestId; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public Integer getTargetId() { return targetId; }
    public void setTargetId(Integer targetId) { this.targetId = targetId; }

    public String getProposedContent() { return proposedContent; }
    public void setProposedContent(String proposedContent) { this.proposedContent = proposedContent; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public int getRequestedBy() { return requestedBy; }
    public void setRequestedBy(int requestedBy) { this.requestedBy = requestedBy; }

    public Integer getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Integer reviewedBy) { this.reviewedBy = reviewedBy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public Timestamp getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Timestamp reviewedAt) { this.reviewedAt = reviewedAt; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public User getRequester() { return requester; }
    public void setRequester(User requester) { this.requester = requester; }

    public User getReviewer() { return reviewer; }
    public void setReviewer(User reviewer) { this.reviewer = reviewer; }

    /** Còn chờ duyệt -- chỉ những yêu cầu này mới cho duyệt/từ chối. */
    public boolean isPending() { return STATUS_PENDING.equals(status); }

    /**
     * Đường dẫn tới màn hình mà cấp trên cần mở để thực hiện yêu cầu này.
     *
     * Hệ thống KHÔNG tự áp thay đổi khi duyệt (xem ghi chú đầu V17): duyệt
     * xong thì cấp trên tự làm trên form thường, nơi mọi ràng buộc nghiệp vụ
     * đã có sẵn. Hàm này chỉ đưa họ tới đúng chỗ, đỡ phải tự dò.
     *
     * @return phần đường dẫn sau context path, hoặc null nếu không xác định được.
     */
    public String getActionPath() {
        String base = RESOURCE_CUSTOMER.equals(resourceType) ? "/customer"
                    : RESOURCE_CONTRACT.equals(resourceType) ? "/contract"
                    : null;
        if (base == null) {
            return null;
        }
        if (INTENT_CREATE.equals(intent)) {
            return base + "?action=new";
        }
        if (targetId == null) {
            return base;
        }
        // Lập phụ lục có form riêng, và targetId ở đây là hợp đồng CHA.
        if (INTENT_AMENDMENT.equals(intent)) {
            return base + "?action=newAmendment&parentId=" + targetId;
        }
        // Sửa và Xoá đều dẫn tới trang chi tiết: xoá là thao tác có xác nhận
        // riêng ở đó, không đưa thẳng người duyệt tới một nút xoá.
        return base + "?action=" + (INTENT_UPDATE.equals(intent) ? "edit" : "view") + "&id=" + targetId;
    }
}
