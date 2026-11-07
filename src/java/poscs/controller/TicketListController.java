package poscs.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.common.AccessControl;
import poscs.common.Period;
import poscs.common.QueryStrings;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.TechnicalRequest;
import poscs.model.User;
import poscs.service.PermissionService;
import poscs.service.TicketService;
import poscs.validator.TicketValidator;

/**
 * Controller cho danh sách phiếu hỗ trợ kỹ thuật (UC-16, Task 5.3).
 * Đáp ứng các quy tắc:
 * - BR-12: Phân trang danh sách với kích thước mặc định 10.
 * - BR-14: Hiển thị trạng thái trống khi danh sách không có dữ liệu.
 * - BR-45: Cung cấp đầy đủ các cột và dải KPI đầu trang (Mới tiếp nhận, Đang xử lý, Đã đóng, Khẩn cấp)
 *   theo kỳ và người xử lý đang chọn.
 * - UC-16: Kỹ thuật viên mở lần đầu mặc định bộ lọc "Phiếu của tôi" (mine); giữ nguyên bộ lọc khi phân trang.
 * - Phân quyền qua {@link PermissionService}, vi phạm trả MSG-022.
 */
@WebServlet(name = "TicketListController", urlPatterns = {"/ticket/list"})
public class TicketListController extends HttpServlet {

    public static final int PAGE_SIZE = TicketService.DEFAULT_PAGE_SIZE;
    public static final String LIST_VIEW = "/jsp/customersupport/listTicket.jsp";
    public static final String ASSIGNEE_ALL = TicketService.ASSIGNEE_ALL;
    public static final String ASSIGNEE_MINE = TicketService.ASSIGNEE_MINE;

    private TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();
    private CustomerDAO customerDAO = new CustomerDAO();
    private ContractDAO contractDAO = new ContractDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private TicketService ticketService;
    private PermissionService permissionService;

    public TicketListController() {
    }

    public TicketListController(TechnicalSupportTicketDAO ticketDAO, CustomerDAO customerDAO,
            ContractDAO contractDAO, EmployeeDAO employeeDAO,
            TicketService ticketService, PermissionService permissionService) {
        this.ticketDAO = ticketDAO != null ? ticketDAO : new TechnicalSupportTicketDAO();
        this.customerDAO = customerDAO != null ? customerDAO : new CustomerDAO();
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.employeeDAO = employeeDAO != null ? employeeDAO : new EmployeeDAO();
        this.ticketService = ticketService;
        this.permissionService = permissionService;
    }

    public PermissionService getPermissionService() {
        if (permissionService != null) {
            return permissionService;
        }
        return new PermissionService(employeeDAO);
    }

    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public TicketService getTicketService() {
        if (ticketService != null) {
            return ticketService;
        }
        return new TicketService(ticketDAO, customerDAO, contractDAO, employeeDAO,
                getPermissionService(), new TicketValidator());
    }

    public void setTicketService(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    public TechnicalSupportTicketDAO getTicketDAO() {
        return ticketDAO;
    }

    public void setTicketDAO(TechnicalSupportTicketDAO ticketDAO) {
        this.ticketDAO = ticketDAO;
    }

    public CustomerDAO getCustomerDAO() {
        return customerDAO;
    }

    public void setCustomerDAO(CustomerDAO customerDAO) {
        this.customerDAO = customerDAO;
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

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        showList(request, response);
    }

    public void showList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = AccessControl.currentUser(request);
        PermissionService ps = getPermissionService();
        if (!ps.canViewTickets(currentUser)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }

        TicketService ts = getTicketService();
        FilterParams fp = extractFilters(request, currentUser, ts);

        List<TechnicalRequest> ticketList = ts.findTickets(fp.page, PAGE_SIZE, fp.keyword,
                fp.statusFilter, fp.priorityFilter, fp.period, fp.assigneeId);
        int totalCount = ts.countTickets(fp.keyword, fp.statusFilter, fp.priorityFilter,
                fp.period, fp.assigneeId);
        Map<String, Integer> statusSummary = ts.countListSummary(fp.period, fp.assigneeId);

        bindListAttributes(request, currentUser, ps, ts, fp, ticketList, totalCount, statusSummary);
        request.getRequestDispatcher(LIST_VIEW).forward(request, response);
    }

    private static final class FilterParams {
        int page;
        String keyword;
        String statusFilter;
        String priorityFilter;
        Period period;
        String assigneeFilter;
        Integer assigneeId;
    }

    private FilterParams extractFilters(HttpServletRequest request, User currentUser, TicketService ts) {
        FilterParams fp = new FilterParams();
        fp.page = Math.max(1, parseIntOrDefault(request.getParameter("page"), 1));
        fp.keyword = request.getParameter("keyword");
        fp.statusFilter = request.getParameter("status");
        fp.priorityFilter = request.getParameter("priority");
        fp.period = Period.parse(request.getParameter("year"), request.getParameter("period"));
        fp.assigneeFilter = ts.resolveAssigneeFilter(request.getParameter("assignee"), currentUser);
        fp.assigneeId = ts.resolveAssigneeId(fp.assigneeFilter, currentUser);
        return fp;
    }

    private void bindListAttributes(HttpServletRequest request, User currentUser,
            PermissionService ps, TicketService ts, FilterParams fp,
            List<TechnicalRequest> ticketList, int totalCount, Map<String, Integer> statusSummary) {
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));
        request.setAttribute("canManage", ps.canCreateTicket(currentUser));
        request.setAttribute("ticketList", ticketList != null ? ticketList : List.of());
        request.setAttribute("statusSummary", statusSummary != null ? statusSummary : Map.of());
        request.setAttribute("currentPage", fp.page);
        request.setAttribute("pageSize", PAGE_SIZE);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalCount", totalCount);
        request.setAttribute("keyword", fp.keyword);
        request.setAttribute("keywordParam", QueryStrings.param(fp.keyword));
        request.setAttribute("statusFilter", fp.statusFilter);
        request.setAttribute("priorityFilter", fp.priorityFilter);
        request.setAttribute("assigneeFilter", fp.assigneeFilter);
        request.setAttribute("isTechnician", ps.isTechnical(currentUser));
        Integer keepTechnicianId = ASSIGNEE_MINE.equals(fp.assigneeFilter) ? null : fp.assigneeId;
        request.setAttribute("technicianList", ts.findActiveTechnicians(keepTechnicianId));
        ContractFilterHelper.setPeriodAttributes(request, fp.period);
    }

    static Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static int parseIntOrDefault(String value, int defaultValue) {
        Integer parsed = parseIntOrNull(value);
        return parsed != null ? parsed : defaultValue;
    }
}
