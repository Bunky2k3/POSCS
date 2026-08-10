package poscs.model;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;

public class ContractPayment {
    private int paymentId;
    private int contractId;
    private BigDecimal invoiceAmount;
    private Date dueDate;
    private Date paidDate; // NULL nếu chưa thanh toán
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

    public BigDecimal getInvoiceAmount() { return invoiceAmount; }
    public void setInvoiceAmount(BigDecimal invoiceAmount) { this.invoiceAmount = invoiceAmount; }

    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }

    public Date getPaidDate() { return paidDate; }
    public void setPaidDate(Date paidDate) { this.paidDate = paidDate; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    // Hàm tiện ích: đã thanh toán chưa
    public boolean isPaid() {
        return paidDate != null;
    }

    // Hàm tiện ích: số ngày trả sớm/trễ so với hạn (âm = trả sớm, dương = trả trễ, null nếu chưa trả)
    public Long getDaysLateOrEarly() {
        if (paidDate == null) {
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
