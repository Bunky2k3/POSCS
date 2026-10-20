package poscs.controller;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.AccessControl;
import poscs.common.Logs;
import poscs.dao.ContractDAO;
import poscs.dao.ContractLinkDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.User;
import poscs.service.ContractLinkService;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;

/**
 * Controller chuyên trách xử lý liên kết hợp đồng Bán ↔ Mua và gỡ liên kết (Task 4.18 - [UC-47]):
 * - Liên kết hợp đồng Bán với hợp đồng Mua (và ngược lại).
 * - Gỡ liên kết đã tạo giữa hai hợp đồng.
 * - Phân quyền RBAC qua {@link PermissionService} (Admin, Sales quản lý vùng).
 */
@WebServlet(name = "ContractLinkController", urlPatterns = {"/contract/link"})
public class ContractLinkController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ContractLinkController.class);

    private ContractDAO contractDAO = new ContractDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private ContractLinkService linkService;
    private PermissionService permissionService;

    public ContractLinkController() {
    }

    public ContractLinkController(ContractDAO contractDAO, EmployeeDAO employeeDAO,
                                  PermissionService permissionService) {
        this(contractDAO, employeeDAO, null, permissionService);
    }

    public ContractLinkController(ContractDAO contractDAO, EmployeeDAO employeeDAO,
                                  ContractLinkService linkService,
                                  PermissionService permissionService) {
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.employeeDAO = employeeDAO != null ? employeeDAO : new EmployeeDAO();
        this.linkService = linkService;
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

    public ContractLinkService getLinkService() {
        if (linkService == null) {
            ContractLinkDAO lDao = (contractDAO != null && contractDAO.getLinkDAO() != null)
                    ? contractDAO.getLinkDAO() : new ContractLinkDAO();
            this.linkService = new ContractLinkService(contractDAO, lDao, getPermissionService());
        }
        return linkService;
    }

    public void setLinkService(ContractLinkService linkService) {
        this.linkService = linkService;
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
            case "linkContract":
                handleLinkContract(request, response);
                break;
            case "unlinkContract":
                handleUnlinkContract(request, response);
                break;
            default:
                response.sendRedirect(request.getContextPath() + "/contract");
                break;
        }
    }

    public void handleLinkContract(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User actor = AccessControl.currentUser(request);
        Integer contractId = parseContractId(request);
        Integer otherId = ContractControllerHelper.parseIntOrNull(request.getParameter("otherContractId"));
        if (contractId == null || contractId <= 0) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        String note = request.getParameter("linkNote");
        ServiceResult<Void> result = getLinkService().linkContracts(
                contractId, otherId != null ? otherId : 0, note, actor);
        if (!result.isSuccess()) {
            handleServiceError(request, response, contractId, result.getMessage(), "Noi hop dong that bai");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId);
    }

    public void handleUnlinkContract(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User actor = AccessControl.currentUser(request);
        Integer contractId = parseContractId(request);
        Integer linkId = ContractControllerHelper.parseIntOrNull(request.getParameter("linkId"));
        if (contractId == null || contractId <= 0 || linkId == null || linkId <= 0) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        ServiceResult<Void> result = getLinkService().unlinkContracts(contractId, linkId, actor);
        if (!result.isSuccess()) {
            handleServiceError(request, response, contractId, result.getMessage(), "Go lien ket that bai");
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
        if (ContractLinkService.ERR_FORBIDDEN.equals(errorMessage)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        if (contractId <= 0 || ContractLinkService.ERR_NOT_FOUND.equals(errorMessage)) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        LOG.warn("{} (actor={}, contractId={}, err={})",
                logPrefix, Logs.actor(request), contractId, errorMessage);
        String errParam;
        if (ContractLinkService.ERR_DUPLICATE.equals(errorMessage)) {
            errParam = "link_duplicate";
        } else if (ContractLinkService.ERR_UNLINK_FAILED.equals(errorMessage)) {
            errParam = "unlink_failed";
        } else {
            errParam = "link_invalid";
        }
        response.sendRedirect(request.getContextPath()
                + "/contract?action=edit&id=" + contractId + "&error=" + errParam);
    }
}
