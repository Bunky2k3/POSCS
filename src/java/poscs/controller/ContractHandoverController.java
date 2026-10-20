package poscs.controller;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.common.AccessControl;
import poscs.dao.ContractDAO;
import poscs.dao.ContractHandoverDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.ContractHandover;
import poscs.model.User;
import poscs.service.ContractHandoverService;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;

/**
 * Controller chuyên trách xử lý Bàn giao hợp đồng phòng ban (Task 4.14):
 * - UC-48: Khởi tạo bàn giao hợp đồng cho một hoặc nhiều phòng ban.
 * - UC-49: Xác nhận xử lý bàn giao chặng phòng ban.
 * - Hàng đợi bàn giao (action=handovers): theo dõi tiến độ và điểm nghẽn.
 */
@WebServlet(name = "ContractHandoverController", urlPatterns = {"/contract/handover", "/contract/handovers"})
public class ContractHandoverController extends HttpServlet {

    public static final String HANDOVER_VIEW = "/jsp/sale/handoverqueue.jsp";

    private ContractDAO contractDAO = new ContractDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private ContractHandoverService handoverService;
    private PermissionService permissionService;

    public ContractHandoverController() {}

    public ContractHandoverController(ContractDAO contractDAO, EmployeeDAO employeeDAO,
                                      PermissionService permissionService) {
        this(contractDAO, employeeDAO, null, permissionService);
    }

    public ContractHandoverController(ContractDAO contractDAO, EmployeeDAO employeeDAO,
                                      ContractHandoverService handoverService,
                                      PermissionService permissionService) {
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.employeeDAO = employeeDAO != null ? employeeDAO : new EmployeeDAO();
        this.handoverService = handoverService;
        this.permissionService = permissionService;
    }

    public ContractDAO getContractDAO() { return contractDAO; }
    public void setContractDAO(ContractDAO contractDAO) { this.contractDAO = contractDAO; }

    public EmployeeDAO getEmployeeDAO() { return employeeDAO; }
    public void setEmployeeDAO(EmployeeDAO employeeDAO) { this.employeeDAO = employeeDAO; }

    public PermissionService getPermissionService() {
        if (permissionService != null) return permissionService;
        return new PermissionService(employeeDAO);
    }
    public void setPermissionService(PermissionService ps) { this.permissionService = ps; }

    public ContractHandoverService getHandoverService() {
        if (handoverService == null) {
            ContractHandoverDAO hDao = (contractDAO != null && contractDAO.getHandoverDAO() != null)
                    ? contractDAO.getHandoverDAO() : new ContractHandoverDAO();
            this.handoverService = new ContractHandoverService(hDao, contractDAO, employeeDAO, getPermissionService());
        }
        return handoverService;
    }
    public void setHandoverService(ContractHandoverService hs) { this.handoverService = hs; }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if (action == null || "handovers".equals(action) || "".equals(action)) {
            showHandoverQueue(request, response);
        } else {
            response.sendRedirect(request.getContextPath() + "/contract");
        }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if ("handOver".equals(action)) {
            handleHandOver(request, response);
        } else if ("completeHandover".equals(action)) {
            handleCompleteHandover(request, response);
        } else {
            response.sendRedirect(request.getContextPath() + "/contract");
        }
    }

    public void showHandoverQueue(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User me = AccessControl.currentUser(request);
        if (!getPermissionService().canViewContracts(me)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        boolean mine = AccessControl.VIEW_MINE.equals(request.getParameter("view")) && me != null;
        request.setAttribute("viewFilter", mine ? AccessControl.VIEW_MINE : AccessControl.VIEW_ALL);

        List<ContractHandover> inScope = getHandoverService().findPendingHandovers(me, mine);
        ContractHandoverService.HandoverQueueOverview overview = getHandoverService().getQueueOverview(inScope);
        request.setAttribute("handoverCountByDepartment", overview.getCountByDepartment());
        request.setAttribute("slowestHandoverDays", overview.getSlowestDays());
        request.setAttribute("scopeTotal", overview.getTotalScope());
        request.setAttribute("departmentList", getHandoverService().findAllDepartments());

        Integer departmentFilter = ContractControllerHelper.parseIntOrNull(request.getParameter("departmentId"));
        List<ContractHandover> rows = ContractHandoverHelper.filterAndSetPermissions(request, inScope, departmentFilter);

        request.setAttribute("pendingHandovers", rows);
        request.setAttribute("departmentFilter", departmentFilter);
        request.getRequestDispatcher(HANDOVER_VIEW).forward(request, response);
    }

    public void handleHandOver(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User actor = AccessControl.currentUser(request);
        Integer contractId = ContractControllerHelper.parseIntOrNull(request.getParameter("contractId"));
        if (contractId == null) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        String back = "/contract?action=edit&id=" + contractId;
        List<Integer> departmentIds = ContractHandoverHelper.parseDepartmentIds(request.getParameterValues("departmentId"));
        if (departmentIds.isEmpty()) {
            response.sendRedirect(request.getContextPath() + back + "&error=handover_no_department");
            return;
        }
        String note = request.getParameter("handoverNote");
        ServiceResult<Integer> res = getHandoverService().initiateHandover(contractId, departmentIds, note, actor);
        if (!res.isSuccess()) {
            if (ContractHandoverService.ERR_FORBIDDEN.equals(res.getMessage())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
                return;
            }
            if (ContractHandoverService.ERR_NOT_FOUND.equals(res.getMessage())) {
                response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
                return;
            }
            if (ContractHandoverService.ERR_HANDOVER_PENDING.equals(res.getMessage())) {
                response.sendRedirect(request.getContextPath() + back + "&error=handover_pending");
                return;
            }
            if (ContractHandoverService.ERR_HANDOVER_FROZEN.equals(res.getMessage())) {
                response.sendRedirect(request.getContextPath() + back + "&error=handover_frozen");
                return;
            }
            response.sendRedirect(request.getContextPath() + back + "&error=handover_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + back);
    }

    public void handleCompleteHandover(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer contractId = ContractControllerHelper.parseIntOrNull(request.getParameter("contractId"));
        Integer handoverId = ContractControllerHelper.parseIntOrNull(request.getParameter("handoverId"));
        Integer departmentId = ContractControllerHelper.parseIntOrNull(request.getParameter("departmentId"));
        if (contractId == null || handoverId == null || departmentId == null) {
            response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
            return;
        }
        User actor = AccessControl.currentUser(request);
        String doneNote = request.getParameter("doneNote");
        ServiceResult<Void> res = getHandoverService().completeHandover(contractId, handoverId, departmentId, doneNote, actor);
        if (!res.isSuccess()) {
            if (ContractHandoverService.ERR_FORBIDDEN.equals(res.getMessage())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
                return;
            }
            if (ContractHandoverService.ERR_NOT_FOUND.equals(res.getMessage())) {
                response.sendRedirect(ContractControllerHelper.notFoundUrl(request));
                return;
            }
            String back = ContractHandoverHelper.resolveReturnBackUrl(request, contractId);
            response.sendRedirect(request.getContextPath() + back + "&error=handover_done_failed");
            return;
        }
        String back = ContractHandoverHelper.resolveReturnBackUrl(request, contractId);
        response.sendRedirect(request.getContextPath() + back);
    }
}
