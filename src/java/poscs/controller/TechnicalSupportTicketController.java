package poscs.controller;

import java.io.IOException;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.TechnicalRequest;
import poscs.model.User;

/**
 * Controller CRUD cho phiếu hỗ trợ kỹ thuật (bảng technicalrequests). Điều
 * hướng theo tham số "action", theo đúng khuôn mẫu của CustomerController/
 * ContractController -- session/quyền hạn theo PERMISSIONS.md (CSKH và
 * Admin có toàn quyền, Sales/Kỹ thuật chỉ xem) chưa enforce ở đây vì
 * AuthenticationController/session chưa được code.
 *
 * technicalrequestdevices (thiết bị lỗi) và technicalrequesthistory (lịch
 * sử đổi trạng thái) chưa được xử lý -- thuộc phạm vi khác.
 */
@WebServlet(name = "TechnicalSupportTicketController", urlPatterns = {"/ticket"})
public class TechnicalSupportTicketController extends HttpServlet {

    private static final int PAGE_SIZE = 10;
    private static final String LIST_VIEW = "/jsp/customersupport/listTicket.jsp";
    private static final String DETAIL_VIEW = "/jsp/customersupport/viewdetailTicket.jsp";
    private static final String CREATE_VIEW = "/jsp/customersupport/addnewTicket.jsp";
    private static final String UPDATE_VIEW = "/jsp/customersupport/updateTicket.jsp";

    private final TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if (action == null) {
            action = "list";
        }
        switch (action) {
            case "view":
                showDetail(request, response);
                break;
            case "new":
                showCreateForm(request, response);
                break;
            case "edit":
                showEditForm(request, response);
                break;
            case "list":
            default:
                showList(request, response);
                break;
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if (action == null) {
            action = "";
        }
        switch (action) {
            case "create":
                handleCreate(request, response);
                break;
            case "update":
                handleUpdate(request, response);
                break;
            case "delete":
                handleDelete(request, response);
                break;
            default:
                response.sendRedirect(request.getContextPath() + "/ticket");
        }
    }

    // ------------------------------------------------------------------
    // GET actions
    // ------------------------------------------------------------------

    private void showList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int page = parseIntOrDefault(request.getParameter("page"), 1);
        if (page < 1) {
            page = 1;
        }
        String keyword = request.getParameter("keyword");
        String statusFilter = request.getParameter("status");
        String priorityFilter = request.getParameter("priority");

        List<TechnicalRequest> ticketList = ticketDAO.findAll(page, PAGE_SIZE, keyword, statusFilter, priorityFilter);
        int totalCount = ticketDAO.countAll(keyword, statusFilter, priorityFilter);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));

        request.setAttribute("ticketList", ticketList);
        request.setAttribute("statusSummary", ticketDAO.countStatusSummary());
        request.setAttribute("currentPage", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalCount", totalCount);
        request.setAttribute("keyword", keyword);
        request.setAttribute("statusFilter", statusFilter);
        request.setAttribute("priorityFilter", priorityFilter);

        request.getRequestDispatcher(LIST_VIEW).forward(request, response);
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        TechnicalRequest ticket = id != null ? ticketDAO.findById(id) : null;
        if (ticket == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
            return;
        }

        request.setAttribute("ticket", ticket);
        request.setAttribute("canDelete", ticketDAO.canDelete(id));

        request.getRequestDispatcher(DETAIL_VIEW).forward(request, response);
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setDropdownAttributes(request);
        request.getRequestDispatcher(CREATE_VIEW).forward(request, response);
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        TechnicalRequest ticket = id != null ? ticketDAO.findById(id) : null;
        if (ticket == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
            return;
        }

        request.setAttribute("ticket", ticket);
        setDropdownAttributes(request);
        request.getRequestDispatcher(UPDATE_VIEW).forward(request, response);
    }

    // ------------------------------------------------------------------
    // POST actions
    // ------------------------------------------------------------------

    private void handleCreate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        TechnicalRequest t = buildTicketFromRequest(request, new TechnicalRequest());
        t.setCreatedDate(new Date(System.currentTimeMillis()));
        t.setStatus(TechnicalSupportTicketDAO.STATUS_NEW);
        t.setCreatedBy(resolveCurrentUserId(request, t.getAssignedTechnicianId()));

        if (!isValid(t)) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=new&error=invalid");
            return;
        }
        t.setTicketCode(ticketDAO.generateNextTicketCode());

        int newId = ticketDAO.insert(t);
        if (newId <= 0) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=new&error=create_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/ticket?action=view&id=" + newId);
    }

    private void handleUpdate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = parseIntOrNull(request.getParameter("ticketId"));
        if (id == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
            return;
        }

        // Cần đọc lại phiếu hiện có trong DB để: (1) giữ nguyên contractId khi form
        // không gửi lên (dropdown "Hợp đồng" ở updateTicket.jsp mặc định disabled,
        // chỉ enable sau khi AJAX loadContracts() chạy xong -- nếu chậm/lỗi/JS tắt
        // thì trường này không được submit); và (2) không ghi đè lại resolved_at
        // nếu phiếu đã đóng từ trước, xem bên dưới.
        TechnicalRequest existing = ticketDAO.findById(id);
        if (existing == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
            return;
        }

        TechnicalRequest t = buildTicketFromRequest(request, new TechnicalRequest());
        t.setTicketId(id);
        if (t.getContractId() == null) {
            t.setContractId(existing.getContractId());
        }
        t.setStatus(emptyToNull(request.getParameter("status")));
        t.setResolutionSummary(emptyToNull(request.getParameter("resolutionSummary")));
        if (TechnicalSupportTicketDAO.STATUS_CLOSED.equals(t.getStatus())) {
            // Chỉ stamp resolved_at = bây giờ ở lần đầu tiên chuyển sang "Đã đóng"
            // -- nếu phiếu đã đóng từ trước (sửa lại resolutionSummary chẳng hạn),
            // giữ nguyên thời điểm đóng gốc thay vì ghi đè lại mỗi lần lưu.
            t.setResolvedAt(TechnicalSupportTicketDAO.STATUS_CLOSED.equals(existing.getStatus())
                    ? existing.getResolvedAt()
                    : new Timestamp(System.currentTimeMillis()));
        } else {
            t.setResolvedAt(null);
        }

        if (!isValid(t) || t.getStatus() == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=edit&id=" + id + "&error=invalid");
            return;
        }

        boolean ok = ticketDAO.update(t);
        if (!ok) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=edit&id=" + id + "&error=update_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/ticket?action=view&id=" + id);
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        if (id == null) {
            response.sendRedirect(request.getContextPath() + "/ticket");
            return;
        }

        // Chỉ cho xoá phiếu chưa có ai xử lý dở dang (khác trạng thái "Đang xử lý").
        if (!ticketDAO.canDelete(id)) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=view&id=" + id + "&error=cannot_delete");
            return;
        }

        ticketDAO.softDelete(id);
        response.sendRedirect(request.getContextPath() + "/ticket");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void setDropdownAttributes(HttpServletRequest request) {
        // Toàn bộ khách hàng chưa xoá, phục vụ dropdown "Khách hàng"
        request.setAttribute("customerList", customerDAO.findAll(1, Integer.MAX_VALUE, null, null, null));
        request.setAttribute("userList", employeeDAO.findAllActive());
    }

    private TechnicalRequest buildTicketFromRequest(HttpServletRequest request, TechnicalRequest t) {
        Integer enterpriseId = parseIntOrNull(request.getParameter("enterpriseId"));
        if (enterpriseId != null) {
            t.setEnterpriseId(enterpriseId);
        }
        t.setContractId(parseIntOrNull(request.getParameter("contractId")));
        t.setTicketType(emptyToNull(request.getParameter("ticketType")));
        t.setPriority(emptyToNull(request.getParameter("priority")));
        t.setReceptionChannel(emptyToNull(request.getParameter("receptionChannel")));
        Integer technicianId = parseIntOrNull(request.getParameter("assignedTechnicianId"));
        if (technicianId != null) {
            t.setAssignedTechnicianId(technicianId);
        }
        t.setDescription(emptyToNull(request.getParameter("description")));
        t.setWarranty("on".equals(request.getParameter("isWarranty")));
        return t;
    }

    /** Người tạo phiếu là user đang đăng nhập; session chưa enforce nên tạm dùng kỹ thuật viên được chọn khi chưa có session. */
    private int resolveCurrentUserId(HttpServletRequest request, int fallbackUserId) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object currentUser = session.getAttribute("currentUser");
            if (currentUser instanceof User) {
                return ((User) currentUser).getUserId();
            }
        }
        return fallbackUserId;
    }

    /** Các trường bắt buộc phải có khi tạo/sửa phiếu hỗ trợ. */
    private boolean isValid(TechnicalRequest t) {
        return t.getEnterpriseId() > 0
                && t.getTicketType() != null
                && t.getPriority() != null
                && t.getReceptionChannel() != null
                && t.getAssignedTechnicianId() > 0
                && t.getDescription() != null;
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        Integer parsed = parseIntOrNull(value);
        return parsed != null ? parsed : defaultValue;
    }

    private String emptyToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }
}
