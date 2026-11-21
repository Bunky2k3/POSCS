package poscs.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.Set;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.AccessControl;
import poscs.common.Logs;
import poscs.common.MoneyVnd;
import poscs.dao.ContractDAO;
import poscs.dao.ContractPaymentDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.ContractPayment;
import poscs.model.User;
import poscs.service.ContractPaymentService;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;

/**
 * Controller chuyên trách xử lý nghiệp vụ Quản lý Kỳ thanh toán Hợp đồng (Task 4.15, Task 10.4):
 * - UC-53, BR-60: Lập kỳ, sửa kỳ, ghi nhận đã nộp hồ sơ, ghi nhận đã thu kèm số tiền thực
 *   thu, xoá kỳ lập nhầm.
 * - BR-18: Quản lý và hiển thị số tiền kỳ thanh toán theo chuẩn tiền tệ VNĐ.
 * - RBAC: Phân quyền chặt chẽ qua PermissionService (Admin, Sales quản lý vùng; vai Tổng
 *   hợp chỉ được ghi nhận đã thu).
 *
 * <p>Trang Sửa và trang chi tiết gửi form về {@code /contract}; ContractController chuyển
 * các hành động {@link #ACTION_ADD}, {@link #ACTION_UPDATE}, {@link #ACTION_SUBMIT},
 * {@link #ACTION_MARK_PAID}, {@link #ACTION_REMOVE} sang đây.
 */
@WebServlet(name = "ContractPaymentController", urlPatterns = {"/contract/payment", "/contract/payments"})
public class ContractPaymentController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ContractPaymentController.class);

    /** Tên hành động dùng chung với ContractController (form gửi về /contract). */
    public static final String ACTION_ADD = "addPayment";
    public static final String ACTION_UPDATE = "updatePayment";
    public static final String ACTION_SUBMIT = "markSubmitted";
    public static final String ACTION_MARK_PAID = "markPaid";
    public static final String ACTION_REMOVE = "removePayment";

    /** Mã lỗi có câu báo riêng ở trang; mã khác về câu chung {@code payment_failed}. */
    private static final Set<String> OWN_ERROR_PARAMS = Set.of(
            ContractPaymentService.ERR_INVALID_FIELDS,
            ContractPaymentService.ERR_SUBMITTED_DATE_FUTURE,
            ContractPaymentService.ERR_INVALID_PAID_AMOUNT);

    private ContractDAO contractDAO = new ContractDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private ContractPaymentService paymentService;
    private PermissionService permissionService;

    public ContractPaymentController() {
    }

    public ContractPaymentController(ContractDAO contractDAO, EmployeeDAO employeeDAO,
                                     ContractPaymentService paymentService,
                                     PermissionService permissionService) {
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.employeeDAO = employeeDAO != null ? employeeDAO : new EmployeeDAO();
        this.paymentService = paymentService;
        this.permissionService = permissionService;
    }

    public ContractDAO getContractDAO() {
        return contractDAO;
    }

    public void setContractDAO(ContractDAO contractDAO) {
        this.contractDAO = contractDAO;
    }

    public EmployeeDAO getEmployeeDAO() {
        return employeeDAO;
    }

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    public PermissionService getPermissionService() {
        if (permissionService == null) {
            this.permissionService = new PermissionService(employeeDAO);
        }
        return permissionService;
    }

    public void setPermissionService(PermissionService ps) {
        this.permissionService = ps;
    }

    public ContractPaymentService getPaymentService() {
        if (paymentService == null) {
            ContractPaymentDAO pDao = (contractDAO != null && contractDAO.getPaymentDAO() != null)
                    ? contractDAO.getPaymentDAO() : new ContractPaymentDAO();
            this.paymentService = new ContractPaymentService(pDao, contractDAO, getPermissionService());
        }
        return paymentService;
    }

    public void setPaymentService(ContractPaymentService ps) {
        this.paymentService = ps;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = AccessControl.currentUser(request);
        if (!getPermissionService().canViewContracts(user)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        Integer contractId = parseContractId(request);
        if (contractId == null) {
            response.sendRedirect(request.getContextPath() + "/contract");
            return;
        }
        Contract contract = contractDAO.findById(contractId);
        if (contract == null || contract.isDeleted()) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        String action = request.getParameter("action");
        String target = "view".equals(action) ? "/contract?action=view&id=" : "/contract?action=edit&id=";
        response.sendRedirect(request.getContextPath() + target + contractId);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if (action == null) {
            action = "";
        }
        switch (action) {
            case ACTION_ADD:
            case "create":
            case "add":
                handleAddPayment(request, response);
                break;
            case ACTION_UPDATE:
                handleUpdatePayment(request, response);
                break;
            case ACTION_SUBMIT:
                handleMarkSubmitted(request, response);
                break;
            case ACTION_MARK_PAID:
            case "paid":
                handleMarkPaid(request, response);
                break;
            case ACTION_REMOVE:
            case "delete":
            case "remove":
                handleRemovePayment(request, response);
                break;
            default:
                response.sendRedirect(request.getContextPath() + "/contract");
                break;
        }
    }

    public void handleAddPayment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User actor = AccessControl.currentUser(request);
        Integer contractId = parseContractId(request);
        if (contractId == null || contractId <= 0) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        if (forbidUnlessCanManage(actor, response)) {
            return;
        }
        String formError = paymentFormError(request, "dueDate", "paidDate");
        if (formError != null) {
            handleServiceError(request, response, contractId, formError, "Form lap ky khong hop le");
            return;
        }
        ContractPayment payment = readPaymentForm(request);
        payment.setPaidDate(ContractControllerHelper.parseDateOrNull(request.getParameter("paidDate")));

        ServiceResult<ContractPayment> res = getPaymentService().addPayment(contractId, payment, actor);
        if (!res.isSuccess()) {
            handleServiceError(request, response, contractId, res.getMessage(), "Lap ky thanh toan that bai");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId);
    }

    /**
     * Sửa một kỳ chưa thu (BR-60). Cùng các ô với form lập kỳ, trừ ngày đã thu.
     */
    public void handleUpdatePayment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User actor = AccessControl.currentUser(request);
        Integer contractId = parseContractId(request);
        Integer paymentId = ContractControllerHelper.parseIntOrNull(request.getParameter("paymentId"));
        if (contractId == null || contractId <= 0 || paymentId == null || paymentId <= 0) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        if (forbidUnlessCanManage(actor, response)) {
            return;
        }
        // Sửa kỳ không có ô ngày đã thu: chỉ kiểm định dạng hạn thu.
        String formError = paymentFormError(request, "dueDate");
        if (formError != null) {
            handleServiceError(request, response, contractId, formError, "Form sua ky khong hop le");
            return;
        }
        ContractPayment payment = readPaymentForm(request);
        payment.setPaymentId(paymentId);
        ServiceResult<ContractPayment> res = getPaymentService().updatePayment(contractId, payment, actor);
        if (!res.isSuccess()) {
            handleServiceError(request, response, contractId, res.getMessage(), "Sua ky thanh toan that bai");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId);
    }

    /**
     * Ghi nhận đã nộp hồ sơ thanh toán của một kỳ (BR-60, MSG-064). Chỉ Admin và Sales
     * quản lý vùng -- bấm từ trang Sửa, nên luôn quay về trang Sửa.
     */
    public void handleMarkSubmitted(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User actor = AccessControl.currentUser(request);
        Integer contractId = parseContractId(request);
        Integer paymentId = ContractControllerHelper.parseIntOrNull(request.getParameter("paymentId"));
        if (contractId == null || contractId <= 0 || paymentId == null || paymentId <= 0) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        if (forbidUnlessCanManage(actor, response)) {
            return;
        }
        String rawSubmitted = request.getParameter("submittedDate");
        String rawDue = request.getParameter("dueDate");
        Date submittedDate = ContractControllerHelper.parseDateOrNull(rawSubmitted);
        Date dueDate = ContractControllerHelper.parseDateOrNull(rawDue);
        if ((isFilled(rawSubmitted) && submittedDate == null) || (isFilled(rawDue) && dueDate == null)) {
            handleServiceError(request, response, contractId, ContractPaymentService.ERR_INVALID,
                    "Dinh dang ngay nop ho so khong hop le");
            return;
        }
        ServiceResult<Void> res = getPaymentService().markSubmitted(contractId, paymentId, submittedDate,
                dueDate, actor);
        if (!res.isSuccess()) {
            handleServiceError(request, response, contractId, res.getMessage(), "Ghi nhan nop ho so that bai");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId);
    }

    /**
     * Lập / sửa / nộp hồ sơ kỳ chỉ cho người quản lý được kỳ (Admin, Sales quản lý vùng).
     * Kiểm TRƯỚC khi đọc form, như "Đã thu": lỗi định dạng không được che mất 403 MSG-022.
     * Service vẫn kiểm lại (chốt chặn thật). Trả true khi đã trả 403.
     */
    private boolean forbidUnlessCanManage(User actor, HttpServletResponse response) throws IOException {
        if (getPermissionService().canManageContractPayments(actor)) {
            return false;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        return true;
    }

    /**
     * Lỗi định dạng của form lập / sửa kỳ (BR-60), hoặc null nếu đọc được.
     * <ul>
     *   <li>Ô số ngày có chữ mà không phải số nguyên → {@code payment_invalid}.</li>
     *   <li>Ô ngày ({@code dateFields}) có chữ mà sai định dạng → {@code payment_failed}: hạn thu
     *       giờ để trống được, nên không chặn ở đây thì ngày gõ sai thành NULL rồi lưu im lặng
     *       (sửa kỳ còn xoá mất hạn cũ).</li>
     * </ul>
     * Số tiền không đọc được thì để service báo lỗi số tiền như trước -- đằng nào cũng không lưu.
     */
    private String paymentFormError(HttpServletRequest request, String... dateFields) {
        String rawDays = request.getParameter("paymentDays");
        if (isFilled(rawDays) && ContractControllerHelper.parseIntOrNull(rawDays.trim()) == null) {
            return ContractPaymentService.ERR_INVALID_FIELDS;
        }
        if (MoneyVnd.parseOrNull(request.getParameter("invoiceAmount")) == null) {
            return null;
        }
        for (String field : dateFields) {
            String raw = request.getParameter(field);
            if (isFilled(raw) && ContractControllerHelper.parseDateOrNull(raw) == null) {
                return ContractPaymentService.ERR_INVALID;
            }
        }
        return null;
    }

    /** Đọc các ô của form lập / sửa kỳ (BR-60); gọi sau {@link #paymentFormError}. */
    private ContractPayment readPaymentForm(HttpServletRequest request) {
        ContractPayment payment = new ContractPayment();
        payment.setPaymentType(request.getParameter("paymentType"));
        payment.setInvoiceAmount(MoneyVnd.parseOrNull(request.getParameter("invoiceAmount")));
        payment.setConditionNote(request.getParameter("conditionNote"));
        payment.setPaymentDays(ContractControllerHelper.parseIntOrNull(request.getParameter("paymentDays")));
        payment.setDueDate(ContractControllerHelper.parseDateOrNull(request.getParameter("dueDate")));
        return payment;
    }

    /** Lỗi định dạng của ô ngày thu / số thực thu ở "Đã thu" (BR-60), hoặc null. */
    private static String paidInputError(String rawPaidDate, String rawPaidAmount) {
        if (isFilled(rawPaidDate) && ContractControllerHelper.parseDateOrNull(rawPaidDate) == null) {
            return ContractPaymentService.ERR_INVALID;
        }
        // Số thực thu để trống là thu đủ số tiền kỳ; có chữ mà không đọc ra số thì báo lỗi,
        // không lặng lẽ ghi đủ số tiền kỳ.
        if (isFilled(rawPaidAmount) && MoneyVnd.parseOrNull(rawPaidAmount) == null) {
            return ContractPaymentService.ERR_INVALID_PAID_AMOUNT;
        }
        return null;
    }

    private static boolean isFilled(String raw) {
        return raw != null && !raw.trim().isEmpty();
    }

    public void handleMarkPaid(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User actor = AccessControl.currentUser(request);
        Integer contractId = parseContractId(request);
        Integer paymentId = ContractControllerHelper.parseIntOrNull(request.getParameter("paymentId"));
        if (contractId == null || contractId <= 0 || paymentId == null || paymentId <= 0) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        // Kiểm quyền TRƯỚC khi đọc ngày thu: người không được ghi nhận đã thu phải
        // nhận 403 MSG-022 ngay, kể cả khi gửi kèm ngày sai định dạng -- không để
        // lỗi định dạng che mất lỗi quyền. Service vẫn kiểm lại (chốt chặn thật).
        if (!getPermissionService().canMarkPaymentPaid(actor)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        String rawPaidDate = request.getParameter("paidDate");
        String rawPaidAmount = request.getParameter("paidAmount");
        String back = markPaidReturnPage(actor, contractId);
        String inputError = paidInputError(rawPaidDate, rawPaidAmount);
        if (inputError != null) {
            handleServiceError(request, response, contractId, back, inputError, "Du lieu da thu khong hop le");
            return;
        }
        Date paidDate = ContractControllerHelper.parseDateOrNull(rawPaidDate);
        BigDecimal paidAmount = MoneyVnd.parseOrNull(rawPaidAmount);
        ServiceResult<Void> res = paidAmount == null
                ? getPaymentService().markPaymentPaid(contractId, paymentId, paidDate, actor)
                : getPaymentService().markPaymentPaid(contractId, paymentId, paidDate, paidAmount, actor);
        if (!res.isSuccess()) {
            handleServiceError(request, response, contractId, back, res.getMessage(), "Ghi nhan da thu that bai");
            return;
        }
        response.sendRedirect(request.getContextPath() + back);
    }

    /**
     * Trang quay về sau khi bấm "Ghi nhận đã thu".
     *
     * <p>Người quản lý được kỳ (Admin, Sales quản lý vùng) bấm từ trang Sửa thì về trang
     * Sửa. Vai Tổng hợp bấm từ trang chi tiết và không mở được trang Sửa (không có quyền
     * ghi hợp đồng), đẩy họ về đó là ăn ngay 403 -- nên về trang chi tiết.
     */
    private String markPaidReturnPage(User actor, int contractId) {
        String action = getPermissionService().canManageContractPayments(actor) ? "edit" : "view";
        return "/contract?action=" + action + "&id=" + contractId;
    }

    public void handleRemovePayment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User actor = AccessControl.currentUser(request);
        Integer contractId = parseContractId(request);
        Integer paymentId = ContractControllerHelper.parseIntOrNull(request.getParameter("paymentId"));
        if (contractId == null || contractId <= 0 || paymentId == null || paymentId <= 0) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        ServiceResult<Void> res = getPaymentService().deletePayment(contractId, paymentId, actor);
        if (!res.isSuccess()) {
            handleServiceError(request, response, contractId, res.getMessage(), "Xoa ky thanh toan that bai");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId);
    }

    private Integer parseContractId(HttpServletRequest request) {
        Integer id = ContractControllerHelper.parseIntOrNull(request.getParameter("contractId"));
        return id != null ? id : ContractControllerHelper.parseIntOrNull(request.getParameter("id"));
    }

    private void handleServiceError(HttpServletRequest request, HttpServletResponse response,
                                    int contractId, String errorMessage, String logPrefix) throws IOException {
        handleServiceError(request, response, contractId, "/contract?action=edit&id=" + contractId,
                errorMessage, logPrefix);
    }

    /** Như trên, nhưng lỗi nghiệp vụ quay về {@code backPage} (trang Sửa hoặc trang chi tiết). */
    private void handleServiceError(HttpServletRequest request, HttpServletResponse response,
                                    int contractId, String backPage, String errorMessage,
                                    String logPrefix) throws IOException {
        if (ContractPaymentService.ERR_FORBIDDEN.equals(errorMessage)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        if (contractId <= 0 || ContractPaymentService.ERR_NOT_FOUND.equals(errorMessage)) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        LOG.warn("{} (actor={}, contractId={}, err={})",
                logPrefix, Logs.actor(request), contractId, errorMessage);
        response.sendRedirect(request.getContextPath() + backPage + "&error=" + errorParamOf(errorMessage));
    }

    /**
     * Mã lỗi gửi về trang: lỗi có câu riêng (ô của kỳ sai, MSG-064, số thực thu sai) giữ
     * nguyên mã; còn lại về câu chung {@code payment_failed} như trước.
     */
    static String errorParamOf(String errorMessage) {
        // Set.of(...) ném NullPointerException với contains(null): kiểm null trước.
        return errorMessage != null && OWN_ERROR_PARAMS.contains(errorMessage)
                ? errorMessage : ContractPaymentService.ERR_PAYMENT_FAILED;
    }
}
