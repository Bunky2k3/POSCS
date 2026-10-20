package poscs.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import poscs.dao.ContractDAO;
import poscs.dao.ContractPaymentDAO;
import poscs.model.Contract;
import poscs.model.ContractPayment;
import poscs.model.User;

/**
 * Service xử lý nghiệp vụ Quản lý Kỳ thanh toán Hợp đồng (Task 4.11).
 * Đáp ứng: UC-53, BR-18 và các quy tắc phân quyền (ma trận SPEC, MSG-022),
 * toàn vẹn giao dịch (NFR-19).
 */
public class ContractPaymentService extends BaseService {

    public static final String ERR_FORBIDDEN = PermissionService.MSG_022;
    public static final String ERR_NOT_FOUND = "notfound";
    public static final String ERR_INVALID = "invalid";
    public static final String ERR_PAYMENT_FROZEN = "payment_frozen";
    public static final String ERR_PAYMENT_FAILED = "payment_failed";
    public static final String ERR_INVALID_AMOUNT = "invalid_amount";
    public static final String ERR_MISSING_DUE_DATE = "missing_due_date";

    public static final String FIELD_INVOICE_AMOUNT = "invoiceAmount";
    public static final String FIELD_DUE_DATE = "dueDate";
    public static final String FIELD_PAID_DATE = "paidDate";

    /** Giới hạn số tiền tối đa theo schema decimal(15,2): 999.999.999.999,99. */
    public static final BigDecimal MAX_PAYMENT_AMOUNT = new BigDecimal("9999999999999.99");

    private final ContractPaymentDAO paymentDAO;
    private final ContractDAO contractDAO;
    private final PermissionService permissionService;

    public ContractPaymentService() {
        this(new ContractPaymentDAO(), new ContractDAO(), new PermissionService());
    }

    public ContractPaymentService(ContractPaymentDAO paymentDAO, ContractDAO contractDAO,
                                  PermissionService permissionService) {
        this.paymentDAO = paymentDAO != null ? paymentDAO : new ContractPaymentDAO();
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.permissionService = permissionService != null ? permissionService : new PermissionService();
    }

    /**
     * Lập một kỳ thanh toán mới cho hợp đồng [UC-53].
     * Tác nhân: Admin hoặc Sales quản lý vùng.
     * Chặn khi hợp đồng đã đóng băng (Đã thanh lý hoặc Chấm dứt sớm).
     */
    public ServiceResult<ContractPayment> addPayment(int contractId, ContractPayment payment, User actor) {
        if (!permissionService.canManageContractPayments(actor)) {
            return ServiceResult.fail(ERR_FORBIDDEN);
        }
        if (contractId <= 0) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        Contract contract = contractDAO.findById(contractId);
        if (contract == null || contract.isDeleted()) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        if (contract.isFrozen()) {
            return ServiceResult.fail(ERR_PAYMENT_FROZEN);
        }
        ServiceResult<Void> validation = validatePaymentInput(payment);
        if (!validation.isSuccess()) {
            return ServiceResult.fail(validation.getMessage(), validation.getFieldErrors());
        }
        boolean ok = paymentDAO.insertPayment(contractId, payment, actor.getUserId());
        if (!ok) {
            return ServiceResult.fail(ERR_PAYMENT_FAILED);
        }
        return ServiceResult.ok(payment, "Lập kỳ thanh toán thành công.");
    }

    private ServiceResult<Void> validatePaymentInput(ContractPayment payment) {
        if (payment == null) {
            return ServiceResult.fail(ERR_INVALID);
        }
        BigDecimal amount = payment.getInvoiceAmount();
        if (amount == null || amount.signum() <= 0) {
            return ServiceResult.fail(ERR_INVALID_AMOUNT,
                    Map.of(FIELD_INVOICE_AMOUNT, "Số tiền kỳ thanh toán phải lớn hơn 0."));
        }
        if (amount.compareTo(MAX_PAYMENT_AMOUNT) > 0) {
            return ServiceResult.fail(ERR_INVALID_AMOUNT,
                    Map.of(FIELD_INVOICE_AMOUNT, "Số tiền kỳ thanh toán vượt quá giới hạn cho phép."));
        }
        if (payment.getDueDate() == null) {
            return ServiceResult.fail(ERR_MISSING_DUE_DATE,
                    Map.of(FIELD_DUE_DATE, "Ngày đến hạn thanh toán là bắt buộc."));
        }
        return ServiceResult.ok(null);
    }

    /**
     * Ghi nhận một kỳ thanh toán đã thu tiền [UC-53].
     * Tác nhân: Admin hoặc Sales quản lý vùng.
     * Không chặn kể cả khi hợp đồng đã thanh lý (tiền bảo lãnh/bảo hành thường về sau thanh lý).
     */
    public ServiceResult<Void> markPaymentPaid(int contractId, int paymentId, Date paidDate, User actor) {
        if (!permissionService.canManageContractPayments(actor)) {
            return ServiceResult.fail(ERR_FORBIDDEN);
        }
        if (contractId <= 0 || paymentId <= 0) {
            return ServiceResult.fail(ERR_INVALID);
        }
        Contract contract = contractDAO.findById(contractId);
        if (contract == null || contract.isDeleted()) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        Date actualPaidDate = paidDate != null ? paidDate : Date.valueOf(LocalDate.now());
        boolean ok = paymentDAO.markPaymentPaid(paymentId, contractId, actualPaidDate, actor.getUserId());
        if (!ok) {
            return ServiceResult.fail(ERR_PAYMENT_FAILED);
        }
        return ServiceResult.ok(null, "Ghi nhận đã thu thành công.");
    }

    /**
     * Xóa một kỳ thanh toán lập nhầm [UC-53].
     * Tác nhân: Admin hoặc Sales quản lý vùng.
     * Chặn khi hợp đồng đã đóng băng.
     */
    public ServiceResult<Void> deletePayment(int contractId, int paymentId, User actor) {
        if (!permissionService.canManageContractPayments(actor)) {
            return ServiceResult.fail(ERR_FORBIDDEN);
        }
        if (contractId <= 0 || paymentId <= 0) {
            return ServiceResult.fail(ERR_INVALID);
        }
        Contract contract = contractDAO.findById(contractId);
        if (contract == null || contract.isDeleted()) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        if (contract.isFrozen()) {
            return ServiceResult.fail(ERR_PAYMENT_FROZEN);
        }
        boolean ok = paymentDAO.deletePayment(paymentId, contractId, actor.getUserId());
        if (!ok) {
            return ServiceResult.fail(ERR_PAYMENT_FAILED);
        }
        return ServiceResult.ok(null, "Xoá kỳ thanh toán thành công.");
    }

    /**
     * Tra cứu danh sách kỳ thanh toán của một hợp đồng [UC-53].
     */
    public List<ContractPayment> findPaymentsByContractId(int contractId) {
        if (contractId <= 0) {
            return Collections.emptyList();
        }
        return paymentDAO.findPaymentsByContractId(contractId);
    }

    /**
     * Tổng tiền đã lập kỳ của cả cụm hợp đồng (gốc + phụ lục) [UC-53, BR-18].
     */
    public BigDecimal sumScheduledPaymentsForCluster(int rootContractId) {
        if (rootContractId <= 0) {
            return BigDecimal.ZERO;
        }
        return paymentDAO.sumScheduledPaymentsForCluster(rootContractId);
    }

    /**
     * Định dạng tiền tệ VNĐ có dấu phân cách hàng nghìn theo BR-18: "1.500.000.000 đ".
     */
    public static String formatMoney(BigDecimal amount) {
        return ContractPaymentDAO.formatMoney(amount);
    }

    /**
     * Định dạng ngày dd/MM/yyyy.
     */
    public static String formatDate(Date date) {
        return ContractPaymentDAO.formatDate(date);
    }
}
