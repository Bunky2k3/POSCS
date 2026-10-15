package poscs.controller;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.AccessControl;
import poscs.common.FileStorage;
import poscs.common.Logs;
import poscs.dao.AddressDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Enterprise;
import poscs.model.User;
import poscs.service.CustomerEvaluationService;
import poscs.model.CustomerLifecycleEvent;
import poscs.service.CustomerService;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;
import poscs.validator.CustomerValidator;

/**
 * Controller xử lý các hành động Thêm (UC-30), Sửa (UC-31), Xóa (UC-32, BR-34), Đánh giá (UC-38).
 * Kiểm soát quyền ghi chặt chẽ qua PermissionService (fail-closed, MSG-022).
 */
@WebServlet(name = "CustomerActionController", urlPatterns = {"/customer/action"})
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 10 * 1024 * 1024, fileSizeThreshold = 1024 * 1024)
public class CustomerActionController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerActionController.class);

    private CustomerDAO customerDAO = new CustomerDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private AddressDAO addressDAO = new AddressDAO();

    private CustomerService customerService;
    private CustomerEvaluationService customerEvaluationService;
    private PermissionService permissionService;

    public CustomerActionController() {}
    public CustomerActionController(CustomerDAO cd, EmployeeDAO ed, AddressDAO ad,
            CustomerService cs, CustomerEvaluationService ce, PermissionService ps) {
        this.customerDAO = cd; this.employeeDAO = ed; this.addressDAO = ad;
        this.customerService = cs; this.customerEvaluationService = ce; this.permissionService = ps;
    }

    CustomerService getCustomerService() {
        return customerService != null ? customerService : new CustomerService(customerDAO, employeeDAO, addressDAO, new CustomerValidator());
    }
    void setCustomerService(CustomerService s) { this.customerService = s; }

    CustomerEvaluationService getCustomerEvaluationService() {
        return customerEvaluationService != null ? customerEvaluationService : new CustomerEvaluationService(customerDAO, getCustomerService());
    }
    void setCustomerEvaluationService(CustomerEvaluationService s) { this.customerEvaluationService = s; }

    PermissionService getPermissionService() {
        return permissionService != null ? permissionService : CustomerControllerHelper.createPermissionService(employeeDAO);
    }
    void setPermissionService(PermissionService s) { this.permissionService = s; }
    void setCustomerDAO(CustomerDAO d) { this.customerDAO = d; }
    void setEmployeeDAO(EmployeeDAO d) { this.employeeDAO = d; }
    void setAddressDAO(AddressDAO d) { this.addressDAO = d; }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String a = req.getParameter("action");
        if ("new".equals(a)) showCreateForm(req, resp);
        else if ("edit".equals(a)) showEditForm(req, resp);
        else resp.sendRedirect(req.getContextPath() + "/customer");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String a = req.getParameter("action");
        if ("create".equals(a)) handleCreate(req, resp);
        else if ("update".equals(a)) handleUpdate(req, resp);
        else if ("delete".equals(a)) handleDelete(req, resp);
        else if ("evaluate".equals(a)) handleEvaluate(req, resp);
        else resp.sendRedirect(req.getContextPath() + "/customer");
    }

    public void showCreateForm(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canCreateCustomer(u)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        CustomerControllerHelper.populateCreateForm(req, u, employeeDAO, addressDAO, getPermissionService());
        req.getRequestDispatcher(CustomerControllerHelper.CREATE_VIEW).forward(req, resp);
    }

    public void showEditForm(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canUpdateCustomer(u)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        Integer id = CustomerControllerHelper.parseIntOrNull(req.getParameter("id"));
        Enterprise c = id != null ? getCustomerService().findById(id) : null;
        if (c == null) {
            resp.sendRedirect(CustomerControllerHelper.notFoundUrl(req));
            return;
        }
        List<String> roles = getCustomerService().findRolesOf(id);
        if (getPermissionService().isSales(u) && !getPermissionService().isCustomerInScope(u, c, roles)) {
            resp.sendRedirect(req.getContextPath() + "/customer?action=view&id=" + id + "&error=not_your_customer");
            return;
        }
        CustomerControllerHelper.populateEditForm(req, u, c, roles, employeeDAO, addressDAO, getPermissionService());
        req.getRequestDispatcher(CustomerControllerHelper.UPDATE_VIEW).forward(req, resp);
    }

    public void handleCreate(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canCreateCustomer(u)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        String role = CustomerControllerHelper.roleFromKind(req.getParameter("kind"));
        String fUrl = req.getContextPath() + "/customer?action=new" + (PermissionService.ROLE_SUPPLIER.equals(role) ? "&kind=supplier" : "");
        if (!FileStorage.isAcceptable(req.getPart("logo"), FileStorage.IMAGE_EXTENSIONS)) {
            resp.sendRedirect(fUrl + "&error=invalid_image_type");
            return;
        }
        Enterprise e = CustomerControllerHelper.extractCreateEnterprise(req);
        ServiceResult<Enterprise> val = getCustomerService().prepareAndValidateCreate(e, role, u, getPermissionService().isSales(u),
                CustomerControllerHelper.parseIntOrNull(req.getParameter("districtId")), CustomerControllerHelper.emptyToNull(req.getParameter("addressDetail")),
                CustomerControllerHelper.parseIntOrNull(req.getParameter("accountOwnerId")), CustomerControllerHelper.parseIntOrNull(req.getParameter("supportOwnerId")));
        if (!val.isSuccess()) {
            resp.sendRedirect(fUrl + "&error=" + val.getMessage());
            return;
        }
        e.setLogoUrl(FileStorage.save(req.getPart("logo"), CustomerControllerHelper.LOGO_SUBFOLDER, FileStorage.IMAGE_EXTENSIONS));
        ServiceResult<Integer> r = getCustomerService().insertCustomer(e, role);
        if (!r.isSuccess()) {
            LOG.warn("Tao khach hang that bai (actor={}, enterpriseCode={})", Logs.actor(req), e.getEnterpriseCode());
            resp.sendRedirect(fUrl + "&error=" + r.getMessage());
        } else if ("roles_not_saved".equals(r.getMessage())) {
            LOG.warn("Ghi vai khach hang moi that bai (actor={}, enterpriseId={})", Logs.actor(req), r.getData());
            resp.sendRedirect(req.getContextPath() + "/customer?action=view&id=" + r.getData() + "&error=roles_not_saved");
        } else resp.sendRedirect(req.getContextPath() + "/customer?action=view&id=" + r.getData());
    }

    public void handleUpdate(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canUpdateCustomer(u)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        Integer id = CustomerControllerHelper.parseIntOrNull(req.getParameter("customerId"));
        if (id == null) {
            resp.sendRedirect(CustomerControllerHelper.notFoundUrl(req));
            return;
        }
        String role = CustomerControllerHelper.roleFromKind(req.getParameter("kind"));
        String kq = PermissionService.ROLE_SUPPLIER.equals(role) ? "&kind=supplier" : "";
        String fUrl = req.getContextPath() + "/customer?action=edit&id=" + id + kq;
        String dUrl = req.getContextPath() + "/customer?action=view&id=" + id + kq;
        if (!FileStorage.isAcceptable(req.getPart("logo"), FileStorage.IMAGE_EXTENSIONS)) {
            resp.sendRedirect(fUrl + "&error=invalid_image_type");
            return;
        }
        Enterprise e = CustomerControllerHelper.extractUpdateEnterprise(req, id);
        ServiceResult<CustomerService.UpdatePreparedState> prep = getCustomerService().prepareAndValidateUpdate(
                e, req.getParameterValues("roles"), role, u, getPermissionService().isSales(u),
                CustomerControllerHelper.parseIntOrNull(req.getParameter("districtId")), CustomerControllerHelper.emptyToNull(req.getParameter("addressDetail")),
                CustomerControllerHelper.parseIntOrNull(req.getParameter("accountOwnerId")), CustomerControllerHelper.parseIntOrNull(req.getParameter("supportOwnerId")));
        if (!prep.isSuccess()) {
            handleUpdateFailure(resp, prep.getMessage(), req, dUrl, fUrl);
            return;
        }
        String newLogo = FileStorage.save(req.getPart("logo"), CustomerControllerHelper.LOGO_SUBFOLDER, FileStorage.IMAGE_EXTENSIONS);
        e.setLogoUrl(newLogo != null ? newLogo : prep.getData().getExisting().getLogoUrl());
        ServiceResult<Enterprise> res = getCustomerService().commitUpdate(e, prep.getData().getRoles());
        if (!res.isSuccess()) {
            LOG.warn("Cap nhat khach hang that bai (actor={}, enterpriseId={})", Logs.actor(req), id);
            resp.sendRedirect(fUrl + "&error=" + res.getMessage());
        } else if ("roles_not_saved".equals(res.getMessage())) {
            LOG.warn("Ghi vai khach hang that bai (actor={}, enterpriseId={})", Logs.actor(req), id);
            resp.sendRedirect(dUrl + "&error=roles_not_saved");
        } else resp.sendRedirect(dUrl);
    }

    private void handleUpdateFailure(HttpServletResponse resp, String err, HttpServletRequest req, String dUrl, String fUrl) throws IOException {
        if ("notfound".equals(err)) resp.sendRedirect(CustomerControllerHelper.notFoundUrl(req));
        else if ("not_your_customer".equals(err)) resp.sendRedirect(dUrl + "&error=not_your_customer");
        else resp.sendRedirect(fUrl + "&error=" + err);
    }

    public void handleDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canDeleteCustomer(u)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        Integer id = CustomerControllerHelper.parseIntOrNull(req.getParameter("id"));
        if (id == null) {
            resp.sendRedirect(CustomerControllerHelper.notFoundUrl(req));
            return;
        }
        String kind = PermissionService.ROLE_SUPPLIER.equals(CustomerControllerHelper.roleFromKind(req.getParameter("kind"))) ? "supplier" : "buyer";
        ServiceResult<Void> r = getCustomerService().deleteCustomer(id, u, getPermissionService().isSales(u));
        if (r.isSuccess()) {
            resp.sendRedirect(req.getContextPath() + "/customer?kind=" + kind);
            return;
        }
        redirectDeleteError(req, resp, id, r.getMessage(), req.getParameter("kind") != null ? "&kind=" + kind : "");
    }

    private void redirectDeleteError(HttpServletRequest req, HttpServletResponse resp, int id, String err, String kq) throws IOException {
        if ("notfound".equals(err)) resp.sendRedirect(CustomerControllerHelper.notFoundUrl(req));
        else if ("not_your_customer".equals(err) || "has_active_contracts".equals(err)) resp.sendRedirect(req.getContextPath() + "/customer?action=view&id=" + id + "&error=" + err);
        else { LOG.warn("Xoa khach hang that bai (actor={}, enterpriseId={})", Logs.actor(req), id); resp.sendRedirect(req.getContextPath() + "/customer?action=view&id=" + id + kq + "&error=" + err); }
    }

    public void handleEvaluate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canRateCustomer(u)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        Integer id = CustomerControllerHelper.parseIntOrNull(req.getParameter("id"));
        if (id == null) {
            resp.sendRedirect(CustomerControllerHelper.notFoundUrl(req));
            return;
        }
        ServiceResult<CustomerLifecycleEvent> r = getCustomerEvaluationService().evaluateCustomer(
                id, req.getParameter("rating"), CustomerControllerHelper.emptyToNull(req.getParameter("description")),
                u, getPermissionService().isSales(u));
        if (r.isSuccess()) {
            resp.sendRedirect(req.getContextPath() + "/customer?action=view&id=" + id + "&evaluated=1");
        } else if ("notfound".equals(r.getMessage())) {
            resp.sendRedirect(CustomerControllerHelper.notFoundUrl(req));
        } else {
            if ("evaluate_failed".equals(r.getMessage())) LOG.warn("Danh gia khach hang that bai (actor={}, enterpriseId={})", Logs.actor(req), id);
            resp.sendRedirect(req.getContextPath() + "/customer?action=view&id=" + id + "&error=" + r.getMessage());
        }
    }
}
