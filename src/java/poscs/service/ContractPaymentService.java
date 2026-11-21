package poscs.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import poscs.dao.ContractDAO;
import poscs.dao.ContractPaymentDAO;
import poscs.model.Contract;
import poscs.model.ContractPayment;
import poscs.model.User;

/**
 * Service xử lý nghiệp vụ Quản lý Kỳ thanh toán Hợp đồng (Task 4.11, Task 10.4).
 * Đáp ứng: UC-53, BR-18, BR-60 và các quy tắc phân quyền (ma trận SPEC, MSG-022),
 * toàn vẹn giao dịch (NFR-19).
 */
public class ContractPaymentService extends BaseService {

    public static final String ERR_FORBIDDEN = PermissionService.MSG_022;
    public static final String ERR_NOT_FOUND = "notfound";
    public static final String ERR_INVALID = "invalid";
    public static final String ERR_PAYMENT_FROZEN = "payment_frozen";
    public static final String ERR_PAYMENT_FAILED = "payment_failed";
    public static final String ERR_PAYMENT_ALREADY_PAID = "payment_already_paid";
    public static final String ERR_INVALID_AMOUNT = "invalid_amount";
    /**
     * Không còn dùng từ BR-60 (hạn thu để trống được khi lập). Giữ hằng vì là mã lỗi
     * công khai đã có; service không trả mã này nữa.
     */
    public static final String ERR_MISSING_DUE_DATE = "missing_due_date";
    /** BR-60: loại kỳ ngoài danh mục, điều kiện quá dài hoặc số ngày ngoài khoảng. */
    public static final String ERR_INVALID_FIELDS = "payment_invalid";
    /** BR-60, MSG-064: ngày nộp hồ sơ sau hôm nay. */
    public static final String ERR_SUBMITTED_DATE_FUTURE = "payment_submitted_future";
    /** BR-60: số tiền thực thu không lớn hơn 0 hoặc vượt giới hạn cột. */
    public static final String ERR_INVALID_PAID_AMOUNT = "payment_paid_amount_invalid";

    /** MSG-064 (BR-60), chép đúng chữ của SPEC. */
    public static final String MSG_064 = "Ngày nộp hồ sơ không được sau hôm nay.";

    public static final String FIELD_INVOICE_AMOUNT = "invoiceAmount";
    public static final String FIELD_DUE_DATE = "dueDate";
    public static final String FIELD_PAID_DATE = "paidDate";
    public static final String FIELD_PAYMENT_TYPE = "paymentType";
    public static final String FIELD_CONDITION_NOTE = "conditionNote";
    public static final String FIELD_PAYMENT_DAYS = "paymentDays";
    public static final String FIELD_SUBMITTED_DATE = "submittedDate";
    public static final String FIELD_PAID_AMOUNT = "paidAmount";

    /** Giới hạn số tiền tối đa theo schema decimal(15,2): 999.999.999.999,99. */
    public static final BigDecimal MAX_PAYMENT_AMOUNT = new BigDecimal("9999999999999.99");
    /** Độ dài cột condition_note (V45). */
    public static final int MAX_CONDITION_NOTE = 255;
    /** Số ngày phải trả kể từ khi nộp hồ sơ: số nguyên từ 1 tới 999 (ô nhập 3 chữ số). */
    public static final int MAX_PAYMENT_DAYS = 999;

    private final ContractPaymentDAO paymentDAO;
    private final ContractDAO contractDAO;
    private final PermissionService permissionService;
    private final Clock clock;

    public ContractPaymentService() {
        this(new ContractPaymentDAO(), new ContractDAO(), new PermissionService());
    }

    public ContractPaymentService(ContractPaymentDAO paymentDAO, ContractDAO contractDAO,
                                  PermissionService permissionService) {
        this(paymentDAO, contractDAO, permissionService, Clock.systemDefaultZone());
    }

    /** Như trên, kèm đồng hồ để test chốt được "hôm nay" (MSG-064). */
    public ContractPaymentService(ContractPaymentDAO paymentDAO, ContractDAO contractDAO,
                                  PermissionService permissionService, Clock clock) {
        this.paymentDAO = paymentDAO != null ? paymentDAO : new ContractPaymentDAO();
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.permissionService = permissionService != null ? permissionService : new PermissionService();
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
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

    /**
     * BR-60: kiểm và chuẩn hoá ô nhập của kỳ khi lập / sửa. Hạn thu KHÔNG còn bắt buộc.
     * Loại kỳ trống thì là "Thanh toán" (DEFAULT của cột); điều kiện toàn khoảng trắng
     * thì lưu NULL.
     */
    private ServiceResult<Void> validatePaymentInput(ContractPayment payment) {
        if (payment == null) {
            return ServiceResult.fail(ERR_INVALID);
        }
        ServiceResult<Void> amountCheck = checkAmount(payment.getInvoiceAmount(), ERR_INVALID_AMOUNT,
                FIELD_INVOICE_AMOUNT, "Số tiền kỳ thanh toán");
        if (!amountCheck.isSuccess()) {
            return amountCheck;
        }
        normalizeFields(payment);
        Map<String, String> errors = new LinkedHashMap<>();
        if (!ContractPayment.TYPES.contains(payment.getPaymentType())) {
            errors.put(FIELD_PAYMENT_TYPE, "Loại kỳ phải là Tạm ứng hoặc Thanh toán.");
        }
        if (payment.getConditionNote() != null && payment.getConditionNote().length() > MAX_CONDITION_NOTE) {
            errors.put(FIELD_CONDITION_NOTE, "Điều kiện tối đa " + MAX_CONDITION_NOTE + " ký tự.");
        }
        Integer days = payment.getPaymentDays();
        if (days != null && (days < 1 || days > MAX_PAYMENT_DAYS)) {
            errors.put(FIELD_PAYMENT_DAYS, "Số ngày phải trả là số nguyên từ 1 đến " + MAX_PAYMENT_DAYS + ".");
        }
        if (!errors.isEmpty()) {
            return ServiceResult.fail(ERR_INVALID_FIELDS, errors);
        }
        return ServiceResult.ok(null);
    }

    private static void normalizeFields(ContractPayment payment) {
        String type = payment.getPaymentType();
        payment.setPaymentType(type == null || type.trim().isEmpty() ? ContractPayment.TYPE_PAYMENT : type.trim());
        String note = payment.getConditionNote();
        payment.setConditionNote(note == null || note.trim().isEmpty() ? null : note.trim());
    }

    /** Số tiền phải lớn hơn 0 và không vượt decimal(15,2) của cột. */
    private static ServiceResult<Void> checkAmount(BigDecimal amount, String errCode, String field, String label) {
        if (amount == null || amount.signum() <= 0) {
            return ServiceResult.fail(errCode, Map.of(field, label + " phải lớn hơn 0."));
        }
        if (amount.compareTo(MAX_PAYMENT_AMOUNT) > 0) {
            return ServiceResult.fail(errCode, Map.of(field, label + " vượt quá giới hạn cho phép."));
        }
        return ServiceResult.ok(null);
    }

    /**
     * Ghi nhận một kỳ thanh toán đã thu tiền [UC-53], thu đủ số tiền kỳ.
     * Xem {@link #markPaymentPaid(int, int, Date, BigDecimal, User)}.
     */
    public ServiceResult<Void> markPaymentPaid(int contractId, int paymentId, Date paidDate, User actor) {
        return markPaymentPaid(contractId, paymentId, paidDate, null, actor);
    }

    /**
     * Ghi nhận một kỳ thanh toán đã thu tiền kèm SỐ TIỀN THỰC THU [UC-53, BR-60, BR-18].
     * Tác nhân: Admin, Sales quản lý vùng, hoặc vai Tổng hợp (SPEC 3.3: Tổng hợp chỉ có
     * thao tác này, không lập, sửa, xoá kỳ, không ghi nhận nộp hồ sơ).
     * {@code paidAmount} null là mặc định bằng số tiền kỳ; có nhập thì phải lớn hơn 0, khác
     * số tiền kỳ thì không bắt lý do. Không chặn kể cả khi hợp đồng đã thanh lý (tiền bảo
     * lãnh/bảo hành thường về sau thanh lý).
     */
    public ServiceResult<Void> markPaymentPaid(int contractId, int paymentId, Date paidDate,
                                               BigDecimal paidAmount, User actor) {
        if (!permissionService.canMarkPaymentPaid(actor)) {
            return ServiceResult.fail(ERR_FORBIDDEN);
        }
        if (contractId <= 0 || paymentId <= 0) {
            return ServiceResult.fail(ERR_INVALID);
        }
        if (paidAmount != null) {
            ServiceResult<Void> amountCheck = checkAmount(paidAmount, ERR_INVALID_PAID_AMOUNT,
                    FIELD_PAID_AMOUNT, "Số tiền thực thu");
            if (!amountCheck.isSuccess()) {
                return amountCheck;
            }
        }
        Contract contract = contractDAO.findById(contractId);
        if (contract == null || contract.isDeleted()) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        Date actualPaidDate = paidDate != null ? paidDate : Date.valueOf(LocalDate.now(clock));
        // Không nhập số thực thu thì đi đúng đường cũ: DAO chép số tiền kỳ ngay trong câu UPDATE.
        boolean ok = paidAmount == null
                ? paymentDAO.markPaymentPaid(paymentId, contractId, actualPaidDate, actor.getUserId())
                : paymentDAO.markPaymentPaid(paymentId, contractId, actualPaidDate, paidAmount, actor.getUserId());
        if (!ok) {
            return ServiceResult.fail(ERR_PAYMENT_FAILED);
        }
        return ServiceResult.ok(null, "Ghi nhận đã thu thành công.");
    }

    /**
     * Sửa một kỳ thanh toán [UC-53, BR-60]: loại, số tiền, điều kiện, số ngày, hạn thu.
     * Tác nhân: Admin hoặc Sales quản lý vùng. Chỉ khi kỳ CHƯA THU và hợp đồng chưa đóng
     * băng. Kỳ đã nộp hồ sơ mà có số ngày thì hạn thu tính lại = ngày nộp + số ngày.
     */
    public ServiceResult<ContractPayment> updatePayment(int contractId, ContractPayment payment, User actor) {
        if (!permissionService.canManageContractPayments(actor)) {
            return ServiceResult.fail(ERR_FORBIDDEN);
        }
        if (contractId <= 0 || payment == null || payment.getPaymentId() <= 0) {
            return ServiceResult.fail(ERR_INVALID);
        }
        Contract contract = contractDAO.findById(contractId);
        if (contract == null || contract.isDeleted()) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        if (contract.isFrozen()) {
            return ServiceResult.fail(ERR_PAYMENT_FROZEN);
        }
        ContractPayment current = findPaymentInContract(contractId, payment.getPaymentId());
        if (current == null) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        if (current.isPaid()) {
            return ServiceResult.fail(ERR_PAYMENT_ALREADY_PAID);
        }
        ServiceResult<Void> validation = validatePaymentInput(payment);
        if (!validation.isSuccess()) {
            return ServiceResult.fail(validation.getMessage(), validation.getFieldErrors());
        }
        if (current.isSubmitted() && payment.getPaymentDays() != null) {
            payment.setDueDate(dueFrom(current.getSubmittedDate(), payment.getPaymentDays()));
        }
        boolean ok = paymentDAO.updatePayment(payment.getPaymentId(), contractId, payment, actor.getUserId());
        if (!ok) {
            return ServiceResult.fail(ERR_PAYMENT_FAILED);
        }
        return ServiceResult.ok(payment, "Sửa kỳ thanh toán thành công.");
    }

    /**
     * Ghi nhận ĐÃ NỘP HỒ SƠ thanh toán của một kỳ [UC-53, BR-60].
     * Tác nhân: Admin hoặc Sales quản lý vùng (vai Tổng hợp không có thao tác này).
     * Ngày nộp trống thì là hôm nay; sau hôm nay → MSG-064. Kỳ có số ngày thì hạn thu =
     * ngày nộp + số ngày; không có thì lấy hạn nhập tay (để trống được). Bấm được cả sau
     * thanh lý; chặn kỳ đã thu.
     */
    public ServiceResult<Void> markSubmitted(int contractId, int paymentId, Date submittedDate,
                                             Date manualDueDate, User actor) {
        if (!permissionService.canManageContractPayments(actor)) {
            return ServiceResult.fail(ERR_FORBIDDEN);
        }
        if (contractId <= 0 || paymentId <= 0) {
            return ServiceResult.fail(ERR_INVALID);
        }
        LocalDate today = LocalDate.now(clock);
        Date actualSubmitted = submittedDate != null ? submittedDate : Date.valueOf(today);
        if (actualSubmitted.toLocalDate().isAfter(today)) {
            return ServiceResult.fail(ERR_SUBMITTED_DATE_FUTURE, Map.of(FIELD_SUBMITTED_DATE, MSG_064));
        }
        Contract contract = contractDAO.findById(contractId);
        if (contract == null || contract.isDeleted()) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        ContractPayment current = findPaymentInContract(contractId, paymentId);
        if (current == null) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        if (current.isPaid()) {
            return ServiceResult.fail(ERR_PAYMENT_ALREADY_PAID);
        }
        Date dueDate = current.getPaymentDays() != null
                ? dueFrom(actualSubmitted, current.getPaymentDays())
                : manualDueDate;
        boolean ok = paymentDAO.markSubmitted(paymentId, contractId, actualSubmitted, dueDate, actor.getUserId());
        if (!ok) {
            return ServiceResult.fail(ERR_PAYMENT_FAILED);
        }
        return ServiceResult.ok(null, "Ghi nhận đã nộp hồ sơ thành công.");
    }

    /** BR-60: hạn thu = ngày nộp hồ sơ + số ngày phải trả. */
    private static Date dueFrom(Date submittedDate, int paymentDays) {
        return Date.valueOf(submittedDate.toLocalDate().plusDays(paymentDays));
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
        ContractPayment targetPayment = findPaymentInContract(contractId, paymentId);
        if (targetPayment == null) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        if (targetPayment.getPaidDate() != null) {
            return ServiceResult.fail(ERR_PAYMENT_ALREADY_PAID);
        }
        boolean ok = paymentDAO.deletePayment(paymentId, contractId, actor.getUserId());
        if (!ok) {
            return ServiceResult.fail(ERR_PAYMENT_FAILED);
        }
        return ServiceResult.ok(null, "Xoá kỳ thanh toán thành công.");
    }

    private ContractPayment findPaymentInContract(int contractId, int paymentId) {
        List<ContractPayment> list = paymentDAO.findPaymentsByContractId(contractId);
        if (list != null) {
            for (ContractPayment p : list) {
                if (p.getPaymentId() == paymentId) {
                    return p;
                }
            }
        }
        return null;
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
