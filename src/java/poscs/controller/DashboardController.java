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
import poscs.model.User;
import poscs.service.DashboardService;
import poscs.service.DashboardSummary;
import poscs.service.ServiceResult;

/**
 * Controller tổng hợp số liệu cho trang tổng quan (dashboard.jsp) -- UC-36, BR-15, BR-18.
 * Tầng Presentation thuần: đọc tham số HTTP, ủy quyền toàn bộ tính toán cho {@link DashboardService},
 * gán attribute và forward sang {@code /dashboard.jsp}.
 */
@WebServlet(name = "DashboardController", urlPatterns = {"/dashboard"})
public class DashboardController extends HttpServlet {

    private CustomerDAO customerDAO = new CustomerDAO();
    private ContractDAO contractDAO = new ContractDAO();
    private AddressDAO addressDAO = new AddressDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private DashboardService dashboardService;

    public DashboardController() {
    }

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    DashboardService getDashboardService() {
        return dashboardService != null
                ? dashboardService
                : (dashboardService = new DashboardService(customerDAO, contractDAO, addressDAO, employeeDAO));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User me = AccessControl.currentUser(request);
        String yearParam = request.getParameter("year");
        String periodParam = request.getParameter("period");
        String scopeParam = request.getParameter("scope");
        boolean provinceSet = request.getParameter("provinceSet") != null;
        String[] rawProvinceIds = request.getParameterValues("provinceId");

        ServiceResult<DashboardSummary> result = getDashboardService().loadDashboard(
                me, yearParam, periodParam, scopeParam, provinceSet, rawProvinceIds);
        if (result.isSuccess() && result.getData() != null) {
            applySummaryAttributes(request, result.getData());
        }
        request.getRequestDispatcher("/dashboard.jsp").forward(request, response);
    }

    private static void applySummaryAttributes(HttpServletRequest request, DashboardSummary summary) {
        request.setAttribute("provinceList", summary.getProvinceList());
        ContractController.setPeriodAttributes(request, summary.getPeriod());
        request.setAttribute("scopeFilter", summary.getScopeFilter());
        request.setAttribute("teamSize", summary.getTeamSize());
        request.setAttribute("myProvinces", summary.getMyProvinces());
        request.setAttribute("provinceFilters", summary.getProvinceFilters());
        request.setAttribute("provinceFilterIsMine", summary.isProvinceFilterIsMine());
        request.setAttribute("totalCustomers", summary.getTotalCustomers());
        request.setAttribute("newCustomersThisMonth", summary.getNewCustomersThisMonth());
        request.setAttribute("contractStatusSummary", summary.getContractStatusSummary());
        request.setAttribute("revenueThisMonth", summary.getRevenueThisMonth());
        if (summary.getRevenueTrendPercent() != null) {
            request.setAttribute("revenueTrendPercent", summary.getRevenueTrendPercent());
        }
        request.setAttribute("currentMonthNumber", summary.getCurrentMonthNumber());
        request.setAttribute("todayLabel", summary.getTodayLabel());
        request.setAttribute("contractWindowLabel", summary.getContractWindowLabel());
        request.setAttribute("windowContracts", summary.getWindowContracts());
        request.setAttribute("contractValues", summary.getContractValues());
        request.setAttribute("scopeCustomers", summary.getScopeCustomers());
    }
}
