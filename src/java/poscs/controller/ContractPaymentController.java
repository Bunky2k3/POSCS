package poscs.controller;

import java.io.IOException;
import java.sql.Date;
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
 * Controller chuyên trách xử lý nghiệp vụ Quản lý Kỳ thanh toán Hợp đồng (Task 4.15):
 * - UC-53: Lập kỳ thanh toán, ghi nhận đã thu, xoá kỳ lập nhầm.
 * - BR-18: Quản lý và hiển thị số tiền kỳ thanh toán theo chuẩn tiền tệ VNĐ.
 * - RBAC: Phân quyền chặt chẽ qua PermissionService (Admin, Sales quản lý vùng).
 */
@WebServlet(name = "ContractPaymentController", urlPatterns = {"/contract/payment", "/contract/payments"})
public class ContractPaymentController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ContractPaymentController.class);

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
            case "addPayment":
            case "create":
            case "add":
                handleAddPayment(request, response);
                break;
            case "markPaid":
            case "paid":
                handleMarkPaid(request, response);
                break;
            case "removePayment":
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
        ContractPayment payment = new ContractPayment();
        payment.setInvoiceAmount(MoneyVnd.parseOrNull(request.getParameter("invoiceAmount")));
        payment.setDueDate(ContractControllerHelper.parseDateOrNull(request.getParameter("dueDate")));
        payment.setPaidDate(ContractControllerHelper.parseDateOrNull(request.getParameter("paidDate")));

        ServiceResult<ContractPayment> res = getPaymentService().addPayment(contractId, payment, actor);
        if (!res.isSuccess()) {
            handleServiceError(request, response, contractId, res.getMessage(), "Lap ky thanh toan that bai");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId);
    }

    public void handleMarkPaid(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User actor = AccessControl.currentUser(request);
        Integer contractId = parseContractId(request);
        Integer paymentId = ContractControllerHelper.parseIntOrNull(request.getParameter("paymentId"));
        if (contractId == null || contractId <= 0 || paymentId == null || paymentId <= 0) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        String rawPaidDate = request.getParameter("paidDate");
        Date paidDate = ContractControllerHelper.parseDateOrNull(rawPaidDate);
        if (rawPaidDate != null && !rawPaidDate.trim().isEmpty() && paidDate == null) {
            handleServiceError(request, response, contractId, ContractPaymentService.ERR_INVALID, "Dinh dang ngay thu khong hop le");
            return;
        }
        ServiceResult<Void> res = getPaymentService().markPaymentPaid(contractId, paymentId, paidDate, actor);
        if (!res.isSuccess()) {
            handleServiceError(request, response, contractId, res.getMessage(), "Ghi nhan da thu that bai");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId);
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
        response.sendRedirect(request.getContextPath()
                + "/contract?action=edit&id=" + contractId + "&error=payment_failed");
    }
}
