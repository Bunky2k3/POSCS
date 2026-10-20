package poscs.controller;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.common.AccessControl;
import poscs.dao.ContractDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.User;
import poscs.service.PermissionService;

/**
 * Controller cho chi tiết hợp đồng (UC-11, UC-45, BR-18).
 * Hiển thị thông tin chung, thanh vòng đời, hàng hóa, phụ lục, kỳ thanh toán,
 * chặng bàn giao, liên kết bán-mua, tài liệu và nhật ký hợp đồng.
 */
@WebServlet(name = "ContractDetailController", urlPatterns = {"/contract/detail"})
public class ContractDetailController extends HttpServlet {

    private ContractDAO contractDAO = new ContractDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private PermissionService permissionService;

    public ContractDetailController() {}

    public ContractDetailController(ContractDAO contractDAO, EmployeeDAO employeeDAO,
                                    PermissionService permissionService) {
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.employeeDAO = employeeDAO != null ? employeeDAO : new EmployeeDAO();
        this.permissionService = permissionService;
    }

    public PermissionService getPermissionService() {
        if (permissionService != null) return permissionService;
        return new PermissionService(employeeDAO);
    }
    public void setPermissionService(PermissionService ps) { this.permissionService = ps; }

    public ContractDAO getContractDAO() { return contractDAO; }
    public void setContractDAO(ContractDAO cd) { this.contractDAO = cd; }

    public EmployeeDAO getEmployeeDAO() { return employeeDAO; }
    public void setEmployeeDAO(EmployeeDAO ed) { this.employeeDAO = ed; }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = AccessControl.currentUser(request);
        if (!getPermissionService().canViewContracts(currentUser)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }

        request.setAttribute("canManage",
                AccessControl.hasFullAccess(request, AccessControl.Resource.CONTRACT));

        Integer id = ContractControllerHelper.parseIntOrNull(request.getParameter("id"));
        Contract contract = id != null ? contractDAO.findById(id) : null;
        if (contract == null) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }

        request.setAttribute("contract", contract);
        ContractControllerHelper.putContractWorkspace(request, contract, contractDAO, employeeDAO);

        request.getRequestDispatcher(ContractControllerHelper.DETAIL_VIEW).forward(request, response);
    }
}
