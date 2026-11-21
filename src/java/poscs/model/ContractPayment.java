package poscs.model;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

/**
 * Một kỳ thanh toán của hợp đồng (bảng {@code contract_payments}, UC-53, BR-60).
 *
 * <p>Theo mẫu điều khoản thanh toán của hợp đồng thật: kỳ Tạm ứng, rồi kỳ Thanh
 * toán sau khi nghiệm thu và nộp hồ sơ; bên mua trả trong số ngày đã thoả thuận.
 * Tình trạng kỳ SUY RA mỗi lần đọc ({@link #statusOn(LocalDate)}), không lưu.
 */
public class ContractPayment {

    /** BR-60: loại kỳ. */
    public static final String TYPE_ADVANCE = "Tạm ứng";
    public static final String TYPE_PAYMENT = "Thanh toán";
    /** Danh mục loại kỳ, theo thứ tự hiện trên form. Kỳ cũ (trước V45) là "Thanh toán". */
    public static final List<String> TYPES = List.of(TYPE_ADVANCE, TYPE_PAYMENT);

    /** BR-60: bốn tình trạng suy ra. */
    public static final String STATUS_PAID = "Đã thu";
    public static final String STATUS_OVERDUE = "Quá hạn";
    public static final String STATUS_WAITING = "Chờ thu";
    public static final String STATUS_NOT_SUBMITTED = "Chưa nộp hồ sơ";

    private int paymentId;
    private int contractId;
    private String paymentType = TYPE_PAYMENT;
    private BigDecimal invoiceAmount;
    private String conditionNote;
    private Integer paymentDays; // NULL nếu điều khoản không ghi số ngày
    private Date submittedDate; // NULL nếu chưa nộp hồ sơ
    private Date dueDate; // NULL nếu chưa có hạn thu (BR-60)
    private Date paidDate; // NULL nếu chưa thanh toán
    private BigDecimal paidAmount; // Số tiền thực thu, NULL nếu chưa thu
    private Timestamp createdAt;

    // Constructor rỗng (Bắt buộc cho JavaBean)
    public ContractPayment() {
    }

    // Constructor có tham số
    public ContractPayment(int paymentId, int contractId, BigDecimal invoiceAmount,
                            Date dueDate, Date paidDate, Timestamp createdAt) {
        this.paymentId = paymentId;
        this.contractId = contractId;
        this.invoiceAmount = invoiceAmount;
        this.dueDate = dueDate;
        this.paidDate = paidDate;
        this.createdAt = createdAt;
    }

    // Các hàm Getters và Setters
    public int getPaymentId() { return paymentId; }
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }

    public int getContractId() { return contractId; }
    public void setContractId(int contractId) { this.contractId = contractId; }

    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }

    public BigDecimal getInvoiceAmount() { return invoiceAmount; }
    public void setInvoiceAmount(BigDecimal invoiceAmount) { this.invoiceAmount = invoiceAmount; }

    public String getConditionNote() { return conditionNote; }
    public void setConditionNote(String conditionNote) { this.conditionNote = conditionNote; }

    public Integer getPaymentDays() { return paymentDays; }
    public void setPaymentDays(Integer paymentDays) { this.paymentDays = paymentDays; }

    public Date getSubmittedDate() { return submittedDate; }
    public void setSubmittedDate(Date submittedDate) { this.submittedDate = submittedDate; }

    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }

    public Date getPaidDate() { return paidDate; }
    public void setPaidDate(Date paidDate) { this.paidDate = paidDate; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    // Hàm tiện ích: đã thanh toán chưa
    public boolean isPaid() {
        return paidDate != null;
    }

    /** Đã ghi nhận nộp hồ sơ thanh toán chưa (BR-60). */
    public boolean isSubmitted() {
        return submittedDate != null;
    }

    /**
     * BR-18: số tiền thực thu của kỳ đã thu. Kỳ đã thu mà chưa có cột thực thu
     * (dữ liệu trước V45) thì coi như thu đủ số tiền kỳ. Chưa thu thì 0.
     */
    public BigDecimal getCollectedAmount() {
        if (!isPaid()) {
            return BigDecimal.ZERO;
        }
        if (paidAmount != null) {
            return paidAmount;
        }
        return invoiceAmount != null ? invoiceAmount : BigDecimal.ZERO;
    }

    /** BR-60: đã thu với số thực thu khác số tiền kỳ (màn hình hiện thêm số thực thu). */
    public boolean isPaidAmountDifferent() {
        return isPaid() && paidAmount != null && invoiceAmount != null
                && paidAmount.compareTo(invoiceAmount) != 0;
    }

    /**
     * BR-60: tình trạng suy ra, xét theo thứ tự: có ngày thu → Đã thu; hạn thu
     * trước hôm nay → Quá hạn; có hạn thu, hoặc đã nộp hồ sơ dù chưa có hạn thu
     * → Chờ thu; còn lại → Chưa nộp hồ sơ.
     */
    public String statusOn(LocalDate today) {
        if (isPaid()) {
            return STATUS_PAID;
        }
        if (dueDate != null && today != null && dueDate.toLocalDate().isBefore(today)) {
            return STATUS_OVERDUE;
        }
        if (dueDate != null || submittedDate != null) {
            return STATUS_WAITING;
        }
        return STATUS_NOT_SUBMITTED;
    }

    /** Tình trạng theo ngày hôm nay của máy chủ -- cho JSP ({@code ${pm.status}}). */
    public String getStatus() {
        return statusOn(LocalDate.now());
    }

    /** Kỳ đang quá hạn (màn hình tô đỏ). */
    public boolean isOverdue() {
        return STATUS_OVERDUE.equals(getStatus());
    }

    // Hàm tiện ích: số ngày trả sớm/trễ so với hạn (âm = trả sớm, dương = trả trễ, null nếu chưa trả
    // hoặc kỳ không có hạn thu)
    public Long getDaysLateOrEarly() {
        if (paidDate == null || dueDate == null) {
            return null;
        }
        long millisPerDay = 24L * 60 * 60 * 1000;
        return (paidDate.getTime() - dueDate.getTime()) / millisPerDay;
    }

    @Override
    public String toString() {
        return "ContractPayment{" + "paymentId=" + paymentId + ", contractId=" + contractId
                + ", invoiceAmount=" + invoiceAmount + '}';
    }
}
