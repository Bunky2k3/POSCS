package poscs.model;

import java.sql.Timestamp;

/**
 * Một sự kiện đã xảy ra với hợp đồng -- ánh xạ bảng contract_history.
 *
 * Khác {@link TechnicalRequestHistory}: bảng của phiếu hỗ trợ chỉ ghi việc ĐỔI
 * TRẠNG THÁI, còn bảng này ghi MỌI thứ chạm vào hợp đồng. Lý do là hợp đồng đã
 * ký là chứng cứ pháp lý, nên câu hỏi cần trả lời không phải "phiếu này đi qua
 * những bước nào" mà "ai đã đổi cái gì, lúc nào".
 *
 * Vì thế bảng chứa HAI loại dòng, nằm chung một dòng thời gian:
 *
 *   * Mốc vòng đời -- có fromStatus/toStatus (thanh lý, chấm dứt sớm... sẽ
 *     dùng ở đợt trục tiến độ; hiện chưa loại nào sinh ra dòng kiểu này).
 *   * Sửa đổi -- fromStatus/toStatus để null, chỉ có detail mô tả thay đổi.
 *
 * detail là câu ĐÃ DỰNG SẴN lúc ghi, không phải dữ liệu thô dựng câu lúc đọc:
 * lịch sử phải kể đúng một câu chuyện mãi mãi, kể cả khi sau này đổi cách hiển
 * thị. note thì ngược lại -- là lý do do người dùng gõ vào, nên tách khỏi
 * detail do hệ thống sinh, để còn phân biệt được máy ghi hay người khai.
 */
public class ContractHistory {

    /** Hợp đồng được tạo. */
    public static final String EVENT_CREATED = "Khởi tạo";

    /** Sửa thông tin trên chính bản ghi hợp đồng (tiêu đề, ngày, người phụ trách...). */
    public static final String EVENT_UPDATED = "Sửa thông tin";

    /** Gắn thêm một dòng hàng hoá/dịch vụ vào hợp đồng. */
    public static final String EVENT_PRODUCT_ADDED = "Thêm hàng hoá";

    /** Gỡ một dòng hàng hoá/dịch vụ khỏi hợp đồng. */
    public static final String EVENT_PRODUCT_REMOVED = "Gỡ hàng hoá";

    /** Lập thêm một kỳ thanh toán theo điều khoản hợp đồng. */
    public static final String EVENT_PAYMENT_ADDED = "Thêm kỳ thanh toán";

    /** Ghi nhận tiền của một kỳ đã về. */
    public static final String EVENT_PAYMENT_PAID = "Ghi nhận đã thu";

    /** Xoá một kỳ thanh toán lập nhầm. */
    public static final String EVENT_PAYMENT_REMOVED = "Xoá kỳ thanh toán";

    /**
     * Lập một phụ lục cho hợp đồng này. Dòng này ghi lên HỢP ĐỒNG CHA, không
     * phải lên phụ lục -- phụ lục tự có dòng "Khởi tạo" của nó.
     *
     * <p>Đó là chỗ người đọc sẽ đi tìm: câu hỏi "hợp đồng này về sau có bị sửa
     * gì không" được hỏi khi đang mở hợp đồng gốc.
     */
    public static final String EVENT_AMENDMENT_CREATED = "Lập phụ lục";

    /**
     * Giá trị hợp đồng đổi vì một phụ lục được KÝ (hoặc bị huỷ bản ghi). Ghi
     * lên HỢP ĐỒNG GỐC, không lên phụ lục: câu "vì sao hợp đồng này giờ là 1,75
     * tỷ chứ không phải 1,5 tỷ" được hỏi khi đang mở hợp đồng gốc.
     *
     * <p>Tách khỏi {@link #EVENT_AMENDMENT_CREATED}: lập phụ lục mới chỉ là
     * soạn một bản nháp, còn tiền chỉ đổi khi bản nháp đó được ký.
     */
    public static final String EVENT_VALUE_ADJUSTED = "Điều chỉnh giá trị";

    /**
     * Admin chữa một sai sót NHẬP LIỆU trên hợp đồng đã ký.
     *
     * <p>Tách hẳn khỏi {@link #EVENT_UPDATED}: sửa một bản nháp là chuyện bình
     * thường, còn chạm vào điều khoản của hợp đồng đã ký thì phải phân biệt
     * được ngay trên dòng thời gian. Luôn kèm lý do người dùng nhập ở
     * {@code note} -- đường ghi từ chối khi thiếu.
     *
     * <p>KHÔNG phải cửa sau cho việc sửa nội dung: sửa đổi thật thì đi qua phụ
     * lục. Cái này chỉ để chữa thứ gõ sai so với chính bản giấy đang cầm.
     */
    public static final String EVENT_CORRECTED = "Sửa sai sót";

    // Ba loại dưới đây là MỐC VÒNG ĐỜI: dòng của chúng có fromStatus/toStatus,
    // khác mọi loại ở trên (dòng sửa đổi, hai cột đó để null).

    /** Nháp → Đã ký. Từ đây nội dung là chứng cứ pháp lý. */
    public static final String EVENT_SIGNED = "Ký hợp đồng";

    /** Đã ký → Đã thanh lý. Theo luật KH đây là lúc hợp đồng thật sự xong. */
    public static final String EVENT_LIQUIDATED = "Thanh lý";

    /** Đã ký → Chấm dứt sớm. Cũng đóng băng, chỉ khác lý do. */
    public static final String EVENT_TERMINATED = "Chấm dứt sớm";

    /**
     * Xoá mềm bản ghi hợp đồng. Tên là "Huỷ bản ghi" chứ không phải "Xoá hợp
     * đồng": hợp đồng đã ký thì không xoá được trong nghiệp vụ, thao tác này
     * chỉ để gỡ một bản ghi NHẬP NHẦM khỏi danh sách, nên bắt buộc có lý do.
     */
    public static final String EVENT_VOIDED = "Huỷ bản ghi";

    private int historyId;
    private int contractId;
    private String eventType;

    /** Câu mô tả thay đổi, dựng sẵn lúc ghi để hiển thị thẳng. */
    private String detail;

    /** Chỉ có giá trị ở dòng chuyển trạng thái tiến độ; null ở dòng sửa đổi. */
    private String fromStatus;
    private String toStatus;

    /** user_id người thực hiện -- khoá ngoại sang users. */
    private int changedBy;

    /** Dữ liệu join từ bảng users -- chỉ có tên, đủ để hiển thị. */
    private User changedByUser;

    private Timestamp changedAt;

    /** Lý do do người dùng nhập; có thể để trống trừ khi huỷ bản ghi. */
    private String note;

    public ContractHistory() {
    }

    /** true nếu dòng này là một bước chuyển trên trục tiến độ, không phải một lần sửa đổi. */
    public boolean isStatusChange() {
        return toStatus != null;
    }

    public int getHistoryId() {
        return historyId;
    }

    public void setHistoryId(int historyId) {
        this.historyId = historyId;
    }

    public int getContractId() {
        return contractId;
    }

    public void setContractId(int contractId) {
        this.contractId = contractId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
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

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
