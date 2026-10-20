package poscs.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.common.AccessControl;
import poscs.common.ListScope;
import poscs.common.Period;
import poscs.common.QueryStrings;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.Department;
import poscs.model.Province;
import poscs.model.User;
import poscs.service.ContractService;
import poscs.service.PermissionService;

/**
 * Controller cho danh sách hợp đồng (UC-10).
 * Đáp ứng các quy tắc:
 * - BR-12: Phân trang danh sách với kích thước mặc định 10.
 * - BR-14: Trạng thái trống khi không có dữ liệu (JSP hiển thị emptyState).
 * - BR-16: Đầy đủ các cột theo quy định.
 * - BR-18: Doanh thu / giá trị hợp đồng định dạng VND.
 * - Giữ nguyên bộ lọc khi phân trang.
 */
@WebServlet(name = "ContractListController", urlPatterns = {"/contract/list"})
public class ContractListController extends HttpServlet {

    public static final int PAGE_SIZE = 10;
    public static final String MONTH_ALL = "all";

    private ContractDAO contractDAO = new ContractDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private AddressDAO addressDAO = new AddressDAO();
    private PermissionService permissionService;

    public ContractListController() {}

    public ContractListController(ContractDAO contractDAO, EmployeeDAO employeeDAO, AddressDAO addressDAO,
                                  PermissionService permissionService) {
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.employeeDAO = employeeDAO != null ? employeeDAO : new EmployeeDAO();
        this.addressDAO = addressDAO != null ? addressDAO : new AddressDAO();
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
    public AddressDAO getAddressDAO() { return addressDAO; }
    public void setAddressDAO(AddressDAO ad) { this.addressDAO = ad; }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("/contract/byEnterprise".equals(request.getServletPath())) {
            listByEnterpriseAsJson(request, response);
            return;
        }
        showList(request, response);
    }

    public void showList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = AccessControl.currentUser(request);
        if (!getPermissionService().canViewContracts(currentUser)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }

        request.setAttribute("canManage",
                AccessControl.hasFullAccess(request, AccessControl.Resource.CONTRACT));

        int page = Math.max(1, ContractControllerHelper.parseIntOrDefault(request.getParameter("page"), 1));
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
        boolean waitingAny = "1".equals(request.getParameter("waiting"));

        ListScope scope = resolveListScope(request);
        bindScopeAttributes(request, scope);

        List<Contract> contracts = contractDAO.findAll(page, PAGE_SIZE, keyword, statusFilter, typeFilter,
                provinceFilters, false, period, direction, progressFilter, rootsOnly, waitingDept, scope);
        int totalCount = contractDAO.countAll(keyword, statusFilter, typeFilter, provinceFilters, period, direction,
                progressFilter, rootsOnly, waitingDept, scope);
        Map<String, Integer> statusSummary = contractDAO.countStatusSummary(provinceFilters, period, direction,
                rootsOnly, null, scope, waitingDept);

        bindListAttributes(request, contracts, totalCount, page, keyword, statusFilter, progressFilter, rootsOnly, waitingDept, waitingAny);
        bindFilterAttributes(request, direction, typeFilter, provinceFilters, period);
        bindFilterChips(request, direction, keyword, statusFilter, progressFilter, rootsOnly, typeFilter,
                provinceFilters, waitingDept, waitingAny, statusSummary);

        request.getRequestDispatcher(ContractControllerHelper.LIST_VIEW).forward(request, response);
    }

    private Integer resolveWaitingDept(HttpServletRequest request) {
        Integer dept = ContractControllerHelper.parseIntOrNull(request.getParameter("waitingDept"));
        if (dept == null && "1".equals(request.getParameter("waiting"))) {
            return ContractDAO.WAITING_ANY_DEPARTMENT;
        }
        return dept;
    }

    private ListScope resolveListScope(HttpServletRequest request) {
        boolean allTime = MONTH_ALL.equals(request.getParameter("month"));
        Period activeWindow = allTime ? null : ContractFilterHelper.currentMonth();
        User u = AccessControl.currentUser(request);
        return AccessControl.listScope(request,
                () -> u != null ? employeeDAO.findTeamUserIds(u.getUserId()) : List.of(),
                () -> u != null ? employeeDAO.findProvincesOf(u.getUserId()).stream().map(Province::getProvinceId).collect(Collectors.toList()) : List.of())
                .withActiveWindow(activeWindow);
    }

    private void bindScopeAttributes(HttpServletRequest request, ListScope scope) {
        boolean allTime = MONTH_ALL.equals(request.getParameter("month"));
        Period activeWindow = scope.getActiveWindow();
        request.setAttribute("viewFilter", AccessControl.defaultView(request));
        request.setAttribute("viewNarrowed", scope.isNarrowed());
        request.setAttribute("viewProvinceCount", scope.getProvinceIds().size());
        request.setAttribute("monthFilter", allTime ? MONTH_ALL : null);
        request.setAttribute("monthLabel", activeWindow != null ? activeWindow.getLabel() : null);
        request.setAttribute("viewToggleUrl", QueryStrings.with(request, "view",
                AccessControl.VIEW_MINE.equals(AccessControl.defaultView(request))
                        ? AccessControl.VIEW_ALL : AccessControl.VIEW_MINE));
        request.setAttribute("monthToggleUrl", QueryStrings.with(request, "month", allTime ? null : MONTH_ALL));
    }

    private void bindListAttributes(HttpServletRequest req, List<Contract> contracts, int total, int page,
                                    String kw, String status, String prog, boolean rootsOnly, Integer wDept, boolean wAny) {
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        req.setAttribute("contractList", contracts);
        req.setAttribute("provinceList", addressDAO.findBranchProvinces());
        req.setAttribute("currentPage", page);
        req.setAttribute("totalPages", totalPages);
        req.setAttribute("totalCount", total);
        req.setAttribute("pageSize", PAGE_SIZE);
        req.setAttribute("keyword", kw);
        req.setAttribute("keywordParam", QueryStrings.param(kw));
        req.setAttribute("statusFilter", status);
        req.setAttribute("progressFilter", prog);
        req.setAttribute("scopeFilter", rootsOnly ? "root" : null);
        req.setAttribute("waitingDeptFilter", (wDept != null && wDept == ContractDAO.WAITING_ANY_DEPARTMENT) ? null : wDept);
        req.setAttribute("waitingAnyFilter", wAny);
    }

    private void bindFilterAttributes(HttpServletRequest req, String dir, String type, List<Integer> provs, Period p) {
        boolean isBuy = ContractService.DIRECTION_BUY.equals(dir);
        req.setAttribute("contractTypeOptions", ContractService.contractTypesFor(dir));
        req.setAttribute("showProvinceFilter", !isBuy);
        req.setAttribute("typeFilter", type);
        req.setAttribute("provinceFilters", provs);
        req.setAttribute("provinceQuery", ContractFilterHelper.provinceQuery(provs));
        User u = AccessControl.currentUser(req);
        req.setAttribute("myProvinces", u != null ? employeeDAO.findProvincesCoveredBy(u.getUserId()) : List.of());
        req.setAttribute("kind", isBuy ? ContractService.KIND_BUY : ContractService.KIND_SELL);
        ContractFilterHelper.setPeriodAttributes(req, p);
    }

    private void bindFilterChips(HttpServletRequest req, String dir, String kw, String st, String prog,
                                 boolean rootsOnly, String type, List<Integer> provs, Integer wDept, boolean wAny,
                                 Map<String, Integer> summary) {
        List<Department> depts = employeeDAO.findAllDepartments();
        req.setAttribute("departmentList", depts);
        req.setAttribute("statusSummary", summary);
        String kind = ContractService.DIRECTION_BUY.equals(dir) ? ContractService.KIND_BUY : ContractService.KIND_SELL;
        ContractFilterHelper.FilterState state = new ContractFilterHelper.FilterState(kind, kw, st, prog,
                rootsOnly ? "root" : null, type, provs, req.getParameter("year"), req.getParameter("period"),
                wDept, ContractFilterHelper.departmentNameOf(depts, wDept), wAny);
        req.setAttribute("statusChips", ContractFilterHelper.buildStatusChips(state, summary));
        req.setAttribute("activeFilters", state.activeFilters());
        req.setAttribute("advancedFilterCount", state.advancedCount());
    }

    public void listByEnterpriseAsJson(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        Integer enterpriseId = ContractControllerHelper.parseIntOrNull(request.getParameter("enterpriseId"));
        if (enterpriseId == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (java.io.PrintWriter out = response.getWriter()) {
                out.write("[]");
            }
            return;
        }
        List<Contract> contracts = contractDAO.findByEnterpriseId(enterpriseId);
        try (java.io.PrintWriter out = response.getWriter()) {
            out.write(toJsonArray(contracts));
        }
    }

    private String toJsonArray(List<Contract> contracts) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < contracts.size(); i++) {
            Contract c = contracts.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(c.getContractId())
              .append(",\"code\":\"").append(escapeJson(c.getContractCode())).append("\"")
              .append(",\"title\":\"").append(escapeJson(c.getTitle())).append("\"}");
        }
        return sb.append(']').toString();
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
