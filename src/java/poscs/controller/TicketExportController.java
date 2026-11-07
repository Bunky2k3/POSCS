package poscs.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.common.AccessControl;
import poscs.common.ExcelUtil;
import poscs.common.Period;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.TechnicalRequest;
import poscs.model.User;
import poscs.service.PermissionService;
import poscs.service.TicketExportService;
import poscs.service.TicketService;
import poscs.validator.TicketValidator;

/**
 * Controller chuyên trách xử lý xuất dữ liệu phiếu hỗ trợ kỹ thuật ra Excel hoặc PDF (Task 5.5 - [UC-42]).
 * Phân quyền qua {@link PermissionService#canExportTickets}: Admin, Kỹ thuật, Sales (SPEC §3.3).
 * Vi phạm trả HTTP 403 với {@link PermissionService#MSG_022}.
 */
@WebServlet(name = "TicketExportController", urlPatterns = {"/ticket/export"})
public class TicketExportController extends HttpServlet {

    private TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private TicketExportService exportService;
    private TicketService ticketService;
    private PermissionService permissionService;
    private ServletContext customServletContext;

    public TicketExportController() {
    }

    public TicketExportController(TechnicalSupportTicketDAO ticketDAO, EmployeeDAO employeeDAO,
            TicketExportService exportService, TicketService ticketService, PermissionService permissionService) {
        this.ticketDAO = ticketDAO != null ? ticketDAO : new TechnicalSupportTicketDAO();
        this.employeeDAO = employeeDAO != null ? employeeDAO : new EmployeeDAO();
        this.exportService = exportService;
        this.ticketService = ticketService;
        this.permissionService = permissionService;
    }

    public TechnicalSupportTicketDAO getTicketDAO() {
        return ticketDAO;
    }

    public void setTicketDAO(TechnicalSupportTicketDAO ticketDAO) {
        this.ticketDAO = ticketDAO;
        if (this.exportService != null) {
            this.exportService = new TicketExportService(ticketDAO);
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

    public TicketExportService getExportService() {
        if (exportService == null) {
            this.exportService = new TicketExportService(ticketDAO);
        }
        return exportService;
    }

    public void setExportService(TicketExportService exportService) {
        this.exportService = exportService;
    }

    public TicketService getTicketService() {
        if (ticketService == null) {
            this.ticketService = new TicketService(ticketDAO, new CustomerDAO(), new ContractDAO(),
                    employeeDAO, getPermissionService(), new TicketValidator());
        }
        return ticketService;
    }

    public void setTicketService(TicketService ticketService) {
        this.ticketService = ticketService;
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

    public ServletContext getCustomServletContext() {
        return customServletContext;
    }

    public void setServletContext(ServletContext servletContext) {
        this.customServletContext = servletContext;
    }

    public ServletContext resolveServletContext(HttpServletRequest request) {
        if (customServletContext != null) {
            return customServletContext;
        }
        try {
            ServletContext sc = getServletContext();
            if (sc != null) {
                return sc;
            }
        } catch (Exception ignored) {
        }
        return request != null ? request.getServletContext() : null;
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = AccessControl.currentUser(request);
        if (!getPermissionService().canExportTickets(currentUser)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }

        String action = request.getParameter("action");
        String type = request.getParameter("type");
        if ("exportPdf".equalsIgnoreCase(action) || "pdf".equalsIgnoreCase(type)) {
            exportPdf(request, response);
        } else {
            exportExcel(request, response, currentUser);
        }
    }

    public void exportExcel(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws IOException {
        String keyword = request.getParameter("keyword");
        String statusFilter = request.getParameter("status");
        String priorityFilter = request.getParameter("priority");
        Period period = Period.parse(request.getParameter("year"), request.getParameter("period"));
        String assigneeFilter = getTicketService().resolveAssigneeFilter(
                request.getParameter("assignee"), currentUser);
        Integer assigneeId = getTicketService().resolveAssigneeId(assigneeFilter, currentUser);

        ExcelUtil.prepareAttachmentHeaders(response, getExportService().getExcelFileNamePrefix());
        try (OutputStream out = response.getOutputStream()) {
            getExportService().exportTicketsToExcel(out, keyword, statusFilter, priorityFilter, period, assigneeId);
        }
    }

    public void exportPdf(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Integer id = parseIdOrNull(request.getParameter("id"));
        TechnicalRequest ticket = id != null ? getTicketDAO().findById(id) : null;
        if (ticket == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=" + TicketService.ERR_NOT_FOUND);
            return;
        }

        response.setContentType("application/pdf");
        String fileName = getExportService().getPdfFileName(ticket);
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encodedFileName);
        getExportService().exportTicketToPdf(response.getOutputStream(), ticket, resolveServletContext(request));
    }

    private Integer parseIdOrNull(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
