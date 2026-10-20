package poscs.controller;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.common.AccessControl;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.ProductDAO;
import poscs.model.Contract;
import poscs.model.User;
import poscs.service.ContractService;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;
import poscs.validator.ContractValidator;

/**
 * Controller xử lý các hành động Hợp đồng (Task 4.12):
 * - Tạo (UC-33), Sửa (UC-35), Phụ lục (UC-46), Ký & Tiến độ (UC-44),
 * - Huỷ bản ghi (UC-34), Chữa sai sót (UC-54), Hàng hóa (UC-39).
 */
@WebServlet(name = "ContractActionController", urlPatterns = {"/contract/action"})
public class ContractActionController extends HttpServlet {

    private ContractDAO contractDAO = new ContractDAO();
    private CustomerDAO customerDAO = new CustomerDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private AddressDAO addressDAO = new AddressDAO();
    private ProductDAO productDAO = new ProductDAO();

    private ContractService contractService;
    private PermissionService permissionService;
    private ContractValidator contractValidator = new ContractValidator();

    public ContractActionController() {}

    public ContractActionController(ContractDAO cd, CustomerDAO custDao, EmployeeDAO ed, AddressDAO ad, ProductDAO pd,
                                    ContractService cs, PermissionService ps) {
        this.contractDAO = cd != null ? cd : new ContractDAO();
        this.customerDAO = custDao != null ? custDao : new CustomerDAO();
        this.employeeDAO = ed != null ? ed : new EmployeeDAO();
        this.addressDAO = ad != null ? ad : new AddressDAO();
        this.productDAO = pd != null ? pd : new ProductDAO();
        this.contractService = cs;
        this.permissionService = ps;
    }

    public ContractService getContractService() {
        if (contractService != null) return contractService;
        return new ContractService(contractDAO, customerDAO, productDAO, getPermissionService(), contractValidator);
    }
    public void setContractService(ContractService cs) { this.contractService = cs; }

    public PermissionService getPermissionService() {
        if (permissionService != null) return permissionService;
        return new PermissionService(employeeDAO);
    }
    public void setPermissionService(PermissionService ps) { this.permissionService = ps; }

    public void setContractDAO(ContractDAO cd) { this.contractDAO = cd; }
    public void setCustomerDAO(CustomerDAO cd) { this.customerDAO = cd; }
    public void setEmployeeDAO(EmployeeDAO ed) { this.employeeDAO = ed; }
    public void setAddressDAO(AddressDAO ad) { this.addressDAO = ad; }
    public void setProductDAO(ProductDAO pd) { this.productDAO = pd; }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String a = req.getParameter("action");
        if ("new".equals(a)) showCreateForm(req, resp);
        else if ("newAmendment".equals(a)) showAmendmentForm(req, resp);
        else if ("edit".equals(a)) showEditForm(req, resp);
        else resp.sendRedirect(req.getContextPath() + "/contract");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String a = req.getParameter("action");
        if ("create".equals(a)) handleCreate(req, resp);
        else if ("update".equals(a)) handleUpdate(req, resp);
        else if ("createAmendment".equals(a)) handleCreateAmendment(req, resp);
        else if ("correct".equals(a)) handleCorrect(req, resp);
        else if ("delete".equals(a)) handleDelete(req, resp);
        else if ("changeProgress".equals(a)) handleChangeProgress(req, resp);
        else if ("addProduct".equals(a)) handleAddProduct(req, resp);
        else if ("removeProduct".equals(a)) handleRemoveProduct(req, resp);
        else resp.sendRedirect(req.getContextPath() + "/contract");
    }

    public void showCreateForm(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canCreateContract(u)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        ContractControllerHelper.populateCreateForm(req, customerDAO, employeeDAO);
        req.getRequestDispatcher(ContractControllerHelper.CREATE_VIEW).forward(req, resp);
    }

    public void showAmendmentForm(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canCreateAmendment(u)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        Integer pId = ContractControllerHelper.parseIntOrNull(req.getParameter("parentId"));
        Contract parent = pId != null ? getContractService().findById(pId) : null;
        if (parent == null) { resp.sendRedirect(ContractControllerHelper.notFoundUrl(req)); return; }
        if (!parent.isSigned() || parent.isAmendment()) {
            resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + pId + "&error=amendment_not_allowed");
            return;
        }
        ContractControllerHelper.populateAmendmentForm(req, parent, customerDAO, employeeDAO);
        req.getRequestDispatcher(ContractControllerHelper.CREATE_VIEW).forward(req, resp);
    }

    public void showEditForm(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canUpdateContract(u)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        Integer id = ContractControllerHelper.parseIntOrNull(req.getParameter("id"));
        Contract c = id != null ? getContractService().findById(id) : null;
        if (c == null) { resp.sendRedirect(ContractControllerHelper.notFoundUrl(req)); return; }
        ContractControllerHelper.bindEditFormAttributes(req, c, u, contractDAO, customerDAO, employeeDAO, productDAO, getPermissionService(), canSign(u));
        req.getRequestDispatcher(ContractControllerHelper.UPDATE_VIEW).forward(req, resp);
    }

    public void handleCreate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canCreateContract(u)) { resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022); return; }
        Contract c = ContractControllerHelper.extractCreateContract(req);
        String kindParam = ContractService.DIRECTION_BUY.equals(c.getDirection()) ? ContractService.KIND_BUY : ContractService.KIND_SELL;
        ServiceResult<Integer> res = getContractService().createContract(c, u);
        ContractActionResultHelper.redirectCreateResult(req, resp, res, kindParam, c.getContractCode());
    }

    public void handleUpdate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canUpdateContract(u)) { resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022); return; }
        Integer id = ContractControllerHelper.parseIntOrNull(req.getParameter("contractId"));
        Contract existing = id != null ? getContractService().findById(id) : null;
        if (existing == null) { resp.sendRedirect(ContractControllerHelper.notFoundUrl(req)); return; }
        String back = "/contract?action=edit&id=" + id;
        ServiceResult<Void> res = updateByTermsLock(req, u, id, existing);
        ContractActionResultHelper.redirectUpdateResult(req, resp, res, id, back);
    }

    private ServiceResult<Void> updateByTermsLock(HttpServletRequest req, User u, int id, Contract existing) {
        if (existing.isTermsLocked()) {
            Integer ownerId = ContractControllerHelper.parseIntOrNull(req.getParameter("ownerId"));
            return ownerId != null ? getContractService().updateContractOwner(id, ownerId, u) : ServiceResult.fail(ContractService.ERR_INVALID);
        }
        Contract c = ContractControllerHelper.extractUpdateContract(req, existing);
        return getContractService().updateContract(c, u);
    }

    public void handleCreateAmendment(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canCreateAmendment(u)) { resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022); return; }
        Integer pId = ContractControllerHelper.parseIntOrNull(req.getParameter("parentId"));
        Contract parent = pId != null ? getContractService().findById(pId) : null;
        if (parent == null) { resp.sendRedirect(ContractControllerHelper.notFoundUrl(req)); return; }
        Contract c = ContractControllerHelper.extractAmendmentContract(req, parent);
        ServiceResult<Integer> res = getContractService().createAmendment(pId, c, u);
        ContractActionResultHelper.redirectAmendmentResult(req, resp, res, pId, "/contract?action=newAmendment&parentId=" + pId);
    }

    public void handleCorrect(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canCorrectSignedContract(u)) { resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022); return; }
        Integer id = ContractControllerHelper.parseIntOrNull(req.getParameter("contractId"));
        Contract existing = id != null ? getContractService().findById(id) : null;
        if (existing == null) { resp.sendRedirect(ContractControllerHelper.notFoundUrl(req)); return; }
        String back = "/contract?action=edit&id=" + id;
        String reason = req.getParameter("correctionReason");
        if (ContractControllerHelper.isBlank(reason)) { resp.sendRedirect(req.getContextPath() + back + "&error=missing_reason"); return; }
        Contract c = ContractControllerHelper.extractUpdateContract(req, existing);
        ServiceResult<Void> res = getContractService().correctSignedContract(c, u, reason);
        ContractActionResultHelper.redirectCorrectResult(req, resp, res, id, back);
    }

    public void handleChangeProgress(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canUpdateContract(u)) { resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022); return; }
        Integer id = ContractControllerHelper.parseIntOrNull(req.getParameter("contractId"));
        Contract target = id != null ? getContractService().findById(id) : null;
        if (target == null) { resp.sendRedirect(ContractControllerHelper.notFoundUrl(req)); return; }
        String toStatus = ContractControllerHelper.emptyToNull(req.getParameter("toStatus")), note = req.getParameter("progressNote");
        if (ContractService.PROGRESS_SIGNED.equals(toStatus)) {
            if (!canSign(u)) { resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Bạn không được ký hợp đồng. Việc này thuộc về cấp trên."); return; }
            if (target.getEffectiveDate() == null || target.getEndDate() == null) {
                resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + id + "&error=missing_term");
                return;
            }
            ContractActionResultHelper.redirectProgressResult(req, resp, getContractService().signContract(id, u, note), id, toStatus);
        } else {
            ContractActionResultHelper.redirectProgressResult(req, resp, getContractService().changeProgressStatus(id, toStatus, u, note), id, toStatus);
        }
    }

    public void handleDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = AccessControl.currentUser(req);
        Integer id = ContractControllerHelper.parseIntOrNull(req.getParameter("id"));
        Contract target = id != null ? getContractService().findById(id) : null;
        if (target == null) { resp.sendRedirect(ContractControllerHelper.notFoundUrl(req)); return; }
        if (!getPermissionService().canVoidContract(u, target.isDraft())) { resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022); return; }
        String reason = req.getParameter("voidReason");
        if (ContractControllerHelper.isBlank(reason)) { resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + id + "&error=void_reason_required"); return; }
        ContractActionResultHelper.redirectVoidResult(req, resp, getContractService().voidContract(id, u, reason), id);
    }

    public void handleAddProduct(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canUpdateContract(u)) { resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022); return; }
        Integer cId = ContractControllerHelper.parseIntOrNull(req.getParameter("contractId"));
        if (cId == null || getContractService().findById(cId) == null) { resp.sendRedirect(ContractControllerHelper.notFoundUrl(req)); return; }
        ServiceResult<Void> res = getContractService().addProductToContract(cId,
                ContractControllerHelper.parseIntOrNull(req.getParameter("productId")),
                ContractControllerHelper.parseQuantityOrNull(req.getParameter("quantity")),
                req.getParameter("unit"), req.getParameter("notes"), u);
        String back = req.getContextPath() + "/contract?action=edit&id=" + cId;
        if (res.isSuccess()) resp.sendRedirect(back);
        else if (ContractService.ERR_ADD_PRODUCT_INVALID.equals(res.getMessage())) resp.sendRedirect(back + "&error=add_product_invalid");
        else resp.sendRedirect(back + "&error=add_product_failed");
    }

    public void handleRemoveProduct(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = AccessControl.currentUser(req);
        if (!getPermissionService().canUpdateContract(u)) { resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022); return; }
        Integer cId = ContractControllerHelper.parseIntOrNull(req.getParameter("contractId"));
        Integer cpId = ContractControllerHelper.parseIntOrNull(req.getParameter("contractProductId"));
        if (cId == null || cpId == null) { resp.sendRedirect(ContractControllerHelper.notFoundUrl(req)); return; }
        ServiceResult<Void> res = getContractService().removeProductFromContract(cId, cpId, u);
        String back = req.getContextPath() + "/contract?action=edit&id=" + cId;
        if (res.isSuccess()) resp.sendRedirect(back);
        else resp.sendRedirect(back + "&error=remove_product_failed");
    }

    public boolean canSign(User user) {
        return user != null && getPermissionService().canSignContract(user) && !user.isSubordinate();
    }
}
