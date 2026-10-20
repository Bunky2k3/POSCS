package poscs.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.common.AccessControl;
import poscs.common.ExcelUtil;
import poscs.common.ListScope;
import poscs.common.Period;
import poscs.dao.ContractDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Province;
import poscs.model.User;
import poscs.service.ContractExportService;
import poscs.service.ContractService;
import poscs.service.PermissionService;

/**
 * Controller xuất danh sách hợp đồng ra Excel (UC-41).
 * Giữ nguyên bộ lọc, không phân trang, sắp theo tỉnh/thành.
 */
@WebServlet(name = "ContractExportController", urlPatterns = {"/contract/export"})
public class ContractExportController extends HttpServlet {

    private ContractDAO contractDAO = new ContractDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private ContractExportService exportService;
    private PermissionService permissionService;

    public ContractExportController() {
    }

    public ContractExportController(ContractDAO contractDAO, EmployeeDAO employeeDAO,
            ContractExportService exportService, PermissionService permissionService) {
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.employeeDAO = employeeDAO != null ? employeeDAO : new EmployeeDAO();
        this.exportService = exportService;
        this.permissionService = permissionService;
    }

    public ContractExportService getExportService() {
        if (exportService == null) {
            this.exportService = new ContractExportService(contractDAO);
        }
        return exportService;
    }

    public void setExportService(ContractExportService exportService) {
        this.exportService = exportService;
    }

    public PermissionService getPermissionService() {
        if (permissionService == null) {
            this.permissionService = new PermissionService(employeeDAO);
        }
        return permissionService;
    }

    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public ContractDAO getContractDAO() {
        return contractDAO;
    }

    public void setContractDAO(ContractDAO contractDAO) {
        this.contractDAO = contractDAO;
        if (this.exportService != null) {
            this.exportService = new ContractExportService(contractDAO);
        }
    }

    public EmployeeDAO getEmployeeDAO() {
        return employeeDAO;
    }

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
        if (this.permissionService != null) {
            this.permissionService = new PermissionService(employeeDAO);
        }
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = AccessControl.currentUser(request);
        if (!getPermissionService().canExportContracts(currentUser)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }

        String keyword = request.getParameter("keyword");
        String statusFilter = request.getParameter("status");
        String typeFilter = request.getParameter("type");
        String direction = ContractService.directionFromKind(request.getParameter("kind"));
        List<Integer> provinceFilters = ContractService.DIRECTION_BUY.equals(direction)
                ? List.of() : ContractFilterHelper.provinceFiltersOf(request);
        Period period = Period.parse(request.getParameter("year"), request.getParameter("period"));
        String progressFilter = request.getParameter("progress");
        boolean rootsOnly = "root".equals(request.getParameter("scope"));
        Integer waitingDept = resolveWaitingDept(request);
        ListScope scope = resolveListScope(request);

        String prefix = getExportService().getExportFileNamePrefix(direction);
        ExcelUtil.prepareAttachmentHeaders(response, prefix);
        try (OutputStream out = response.getOutputStream()) {
            getExportService().exportContractsToExcel(out, keyword, statusFilter, typeFilter,
                    provinceFilters, period, direction, progressFilter, rootsOnly, waitingDept, scope);
        }
    }

    Integer resolveWaitingDept(HttpServletRequest request) {
        Integer dept = ContractControllerHelper.parseIntOrNull(request.getParameter("waitingDept"));
        if (dept == null && "1".equals(request.getParameter("waiting"))) {
            return ContractDAO.WAITING_ANY_DEPARTMENT;
        }
        return dept;
    }

    ListScope resolveListScope(HttpServletRequest request) {
        boolean allTime = ContractListController.MONTH_ALL.equals(request.getParameter("month"));
        Period activeWindow = allTime ? null : ContractFilterHelper.currentMonth();
        User u = AccessControl.currentUser(request);
        return AccessControl.listScope(request,
                () -> u != null ? employeeDAO.findTeamUserIds(u.getUserId()) : List.of(),
                () -> u != null ? employeeDAO.findProvincesOf(u.getUserId()).stream().map(Province::getProvinceId).collect(Collectors.toList()) : List.of())
                .withActiveWindow(activeWindow);
    }
}
