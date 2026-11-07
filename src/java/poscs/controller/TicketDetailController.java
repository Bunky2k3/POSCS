package poscs.controller;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.common.AccessControl;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.User;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;
import poscs.service.TicketService;
import poscs.service.TicketService.TicketDetail;
import poscs.validator.TicketValidator;

/**
 * Controller cho chi tiết phiếu hỗ trợ kỹ thuật (UC-17, Task 5.3).
 * Đáp ứng các quy tắc:
 * - BR-46: Hiển thị đầy đủ Thông tin chung, Người tạo phiếu, Kỹ thuật viên xử lý, Lịch sử xử lý.
 * - Phân quyền qua {@link PermissionService}, vi phạm trả MSG-022.
 * - Cờ canEdit (Admin, Sales hoặc Kỹ thuật viên đang được giao phiếu) và canDelete (Admin, Sales
 *   và phiếu ở trạng thái Mới tiếp nhận hoặc Đã đóng - BR-47).
 */
@WebServlet(name = "TicketDetailController", urlPatterns = {"/ticket/detail"})
public class TicketDetailController extends HttpServlet {

    public static final String DETAIL_VIEW = "/jsp/customersupport/viewdetailTicket.jsp";

    private TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();
    private CustomerDAO customerDAO = new CustomerDAO();
    private ContractDAO contractDAO = new ContractDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private TicketService ticketService;
    private PermissionService permissionService;

    public TicketDetailController() {
    }

    public TicketDetailController(TechnicalSupportTicketDAO ticketDAO, CustomerDAO customerDAO,
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
        showDetail(request, response);
    }

    public void showDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = AccessControl.currentUser(request);
        PermissionService ps = getPermissionService();
        if (!ps.canViewTickets(currentUser)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }

        request.setAttribute("canManage", ps.canDeleteTicket(currentUser));

        Integer id = TicketListController.parseIntOrNull(request.getParameter("id"));
        if (id == null || id <= 0) {
            response.sendRedirect(notFoundUrl(request));
            return;
        }

        ServiceResult<TicketDetail> detailResult = getTicketService().getTicketDetail(id, currentUser);
        if (!detailResult.isSuccess()) {
            if (PermissionService.MSG_022.equals(detailResult.getMessage())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
                return;
            }
            response.sendRedirect(notFoundUrl(request));
            return;
        }

        TicketDetail detail = detailResult.getData();
        request.setAttribute("ticket", detail.getTicket());
        request.setAttribute("ticketHistory", detail.getHistory());
        request.setAttribute("canEdit", detail.isCanEdit());
        request.setAttribute("canDelete", detail.isCanDelete());

        request.getRequestDispatcher(DETAIL_VIEW).forward(request, response);
    }

    public static String notFoundUrl(HttpServletRequest request) {
        return request.getContextPath() + "/ticket?error=" + TicketService.ERR_NOT_FOUND;
    }
}
