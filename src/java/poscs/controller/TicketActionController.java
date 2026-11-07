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
import poscs.model.TechnicalRequest;
import poscs.model.User;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;
import poscs.service.TicketService;
import poscs.validator.TicketValidator;

/**
 * Controller xử lý các hành động Tạo (UC-21), Cập nhật (UC-22) và Xóa (UC-23, BR-47)
 * cho phiếu hỗ trợ kỹ thuật (Task 5.4).
 */
@WebServlet(name = "TicketActionController", urlPatterns = {"/ticket/action"})
public class TicketActionController extends HttpServlet {

    private TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();
    private CustomerDAO customerDAO = new CustomerDAO();
    private ContractDAO contractDAO = new ContractDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private TicketService ticketService;
    private PermissionService permissionService;

    public TicketActionController() {
    }

    public TicketActionController(TechnicalSupportTicketDAO ticketDAO, CustomerDAO customerDAO,
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
        return new PermissionService(employeeDAO) {
            @Override
            public User getEffectiveUser(User sessionUser) {
                User u = super.getEffectiveUser(sessionUser);
                if (u != null) {
                    return u;
                }
                if (sessionUser != null && !sessionUser.isDeleted() && sessionUser.getRole() != null) {
                    return sessionUser;
                }
                return null;
            }
        };
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

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if ("new".equals(action)) {
            showCreateForm(request, response);
        } else if ("edit".equals(action)) {
            showEditForm(request, response);
        } else {
            response.sendRedirect(request.getContextPath() + "/ticket");
        }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if ("create".equals(action)) {
            handleCreate(request, response);
        } else if ("update".equals(action)) {
            handleUpdate(request, response);
        } else if ("delete".equals(action)) {
            handleDelete(request, response);
        } else {
            response.sendRedirect(request.getContextPath() + "/ticket");
        }
    }

    // ------------------------------------------------------------------
    // UC-21: Tạo phiếu hỗ trợ kỹ thuật
    // ------------------------------------------------------------------

    public void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = AccessControl.currentUser(request);
        if (!getPermissionService().canCreateTicket(currentUser)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        TicketActionHelper.setDropdownAttributes(request, getTicketService(), null, null);
        request.getRequestDispatcher(TicketActionHelper.CREATE_VIEW).forward(request, response);
    }

    public void handleCreate(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        User currentUser = AccessControl.currentUser(request);
        if (!getPermissionService().canCreateTicket(currentUser)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }

        TechnicalRequest t = TicketActionHelper.buildTicketFromRequest(request, new TechnicalRequest());
        Boolean isWarranty = TicketActionHelper.resolveWarranty(request);

        ServiceResult<Integer> result = getTicketService().createTicket(t, isWarranty, currentUser);
        if (!result.isSuccess()) {
            TicketActionHelper.handleCreateError(request, response, result.getMessage());
            return;
        }
        response.sendRedirect(request.getContextPath() + "/ticket?action=view&id=" + result.getData());
    }

    // ------------------------------------------------------------------
    // UC-22: Cập nhật phiếu & chuyển trạng thái
    // ------------------------------------------------------------------

    public void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = TicketActionHelper.parseIntOrNull(request.getParameter("id"));
        TechnicalRequest ticket = id != null ? getTicketService().findById(id) : null;
        if (ticket == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
            return;
        }

        User currentUser = AccessControl.currentUser(request);
        boolean fullAccess = getPermissionService().canCreateTicket(currentUser);
        boolean canUpdate = getPermissionService().canUpdateTicket(currentUser, ticket.getAssignedTechnicianId());
        if (!canUpdate) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }

        request.setAttribute("ticket", ticket);
        request.setAttribute("canManage", fullAccess);
        request.setAttribute("lockGeneral", !fullAccess);
        TicketActionHelper.setDropdownAttributes(request, getTicketService(),
                ticket.getAssignedTechnicianId(), ticket.getEnterpriseId());
        request.getRequestDispatcher(TicketActionHelper.UPDATE_VIEW).forward(request, response);
    }

    public void handleUpdate(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Integer id = TicketActionHelper.parseIntOrNull(request.getParameter("ticketId"));
        TechnicalRequest existing = id != null ? getTicketService().findById(id) : null;
        if (existing == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
            return;
        }

        User currentUser = AccessControl.currentUser(request);
        if (!getPermissionService().canUpdateTicket(currentUser, existing.getAssignedTechnicianId())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }

        TechnicalRequest input = TicketActionHelper.extractUpdateInput(request, id);
        boolean contractLoaded = "1".equals(request.getParameter("contractLoaded"));
        String slaParam = request.getParameter("slaDeadline");
        boolean slaCleared = slaParam != null && slaParam.trim().isEmpty();

        ServiceResult<Void> result = getTicketService().updateTicket(input,
                request.getParameter("internalNote"), contractLoaded, slaCleared, currentUser);

        if (!result.isSuccess()) {
            TicketActionHelper.handleUpdateError(request, response, id, result.getMessage());
            return;
        }
        response.sendRedirect(request.getContextPath() + "/ticket?action=view&id=" + id);
    }

    // ------------------------------------------------------------------
    // UC-23 & BR-47: Xóa phiếu hỗ trợ
    // ------------------------------------------------------------------

    public void handleDelete(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        User currentUser = AccessControl.currentUser(request);
        if (!getPermissionService().canDeleteTicket(currentUser)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        Integer id = TicketActionHelper.parseIntOrNull(request.getParameter("id"));
        if (id == null) {
            response.sendRedirect(request.getContextPath() + "/ticket");
            return;
        }

        ServiceResult<Void> result = getTicketService().deleteTicket(id, currentUser);
        if (!result.isSuccess()) {
            TicketActionHelper.handleDeleteError(request, response, id, result.getMessage());
            return;
        }
        response.sendRedirect(request.getContextPath() + "/ticket");
    }
}
