package poscs.model;

import java.sql.Timestamp;

/**
 * Một lần đổi trạng thái của phiếu hỗ trợ kỹ thuật -- ánh xạ bảng
 * technicalrequesthistory.
 *
 * Chỉ ghi lại việc ĐỔI TRẠNG THÁI, không phải mọi lần bấm Lưu: sửa mô tả hay
 * đổi người phụ trách mà trạng thái giữ nguyên thì không sinh dòng nào. Lý do
 * là bảng này có đúng 2 cột from_status/to_status -- nó được thiết kế cho
 * dòng thời gian xử lý phiếu, không phải làm nhật ký sửa đổi chung.
 */
public class TechnicalRequestHistory {

    private int historyId;
    private int ticketId;
    private String fromStatus;
    private String toStatus;

    /** user_id của người thực hiện việc đổi trạng thái. */
    private int changedBy;

    /** Dữ liệu join từ bảng users -- chỉ có tên, đủ để hiển thị. */
    private User changedByUser;

    private Timestamp changedAt;

    /** Ghi chú nội bộ kèm theo lần đổi này; có thể để trống. */
    private String internalNote;

    public TechnicalRequestHistory() {
    }

    public int getHistoryId() {
        return historyId;
    }

    public void setHistoryId(int historyId) {
        this.historyId = historyId;
    }

    public int getTicketId() {
        return ticketId;
    }

    public void setTicketId(int ticketId) {
        this.ticketId = ticketId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public int getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(int changedBy) {
        this.changedBy = changedBy;
    }

    public User getChangedByUser() {
        return changedByUser;
    }

    public void setChangedByUser(User changedByUser) {
        this.changedByUser = changedByUser;
    }

    public Timestamp getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Timestamp changedAt) {
        this.changedAt = changedAt;
    }

    public String getInternalNote() {
        return internalNote;
    }

    public void setInternalNote(String internalNote) {
        this.internalNote = internalNote;
    }
}
