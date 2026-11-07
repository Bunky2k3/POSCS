package poscs.controller;

import java.io.IOException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.AccessControl;
import poscs.common.Logs;
import poscs.common.Period;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Contract;
import poscs.model.TechnicalRequest;
import poscs.model.User;
import poscs.service.PermissionService;
import poscs.service.TicketService;
import poscs.validator.TicketValidator;

/**
 * Controller CRUD cho phiếu hỗ trợ kỹ thuật (bảng technicalrequests). Điều
 * hướng theo tham số "action", theo đúng khuôn mẫu của CustomerController/
 * ContractController -- quyền hạn theo PERMISSIONS.md (Sales và Admin có
 * toàn quyền -- Sales gộp thêm việc của CSKH cũ, xem PERMISSIONS.md -- Kỹ
 * thuật chỉ xem, trừ đúng phiếu được giao) enforce bằng
 * AccessControl.requireFullAccess ở đầu mỗi hàm handleCreate/handleDelete.
 * Riêng handleUpdate có thêm ngoại lệ: Kỹ thuật được tự đổi trạng thái/ghi
 * chú xử lý của đúng phiếu đang giao cho mình, xem
 * AccessControl.canUpdateAssignedTicket.
 */
@WebServlet(name = "TechnicalSupportTicketController", urlPatterns = {"/ticket"})
public class TechnicalSupportTicketController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(TechnicalSupportTicketController.class);

    private static final int PAGE_SIZE = 10;
    /**
     * Vai được giao XỬ LÝ phiếu -- khớp roles.role_name, xem AccessControl.
     * Khác hẳn vai được quyền TẠO phiếu (Sales, gộp thêm việc của CSKH cũ):
     * người tiếp nhận và người đi sửa là hai người khác nhau.
     */
    private static final String TECHNICIAN_ROLE = "Kỹ thuật";
    /**
     * Vai khách hàng được lập phiếu hỗ trợ -- khớp enterprise_roles.role (V20).
     * Phiếu là yêu cầu hỗ trợ của bên MUA hàng của mình; nhà cung cấp không
     * gửi phiếu hỗ trợ cho mình, và chọn nhầm một nhà cung cấp thì ô "Hợp
     * đồng liên quan" liệt kê hợp đồng MUA.
     */
    private static final String ROLE_BUYER = "Khách mua";

    private static final String LIST_VIEW = "/jsp/customersupport/listTicket.jsp";
    private static final String DETAIL_VIEW = "/jsp/customersupport/viewdetailTicket.jsp";
    private static final String CREATE_VIEW = "/jsp/customersupport/addnewTicket.jsp";
    private static final String UPDATE_VIEW = "/jsp/customersupport/updateTicket.jsp";

    private TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();
    private CustomerDAO customerDAO = new CustomerDAO();
    // Dùng để kiểm hợp đồng được chọn có đúng của khách hàng trên phiếu không.
    private ContractDAO contractDAO = new ContractDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();

    private PermissionService permissionService;
    private TicketService ticketService;
    private TicketListController listController;
    private TicketDetailController detailController;
    private TicketExportController exportController;

    PermissionService getPermissionService() {
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

    void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    TicketService getTicketService() {
        if (ticketService != null) {
            return ticketService;
        }
        return new TicketService(ticketDAO, customerDAO, contractDAO, employeeDAO,
                getPermissionService(), new TicketValidator());
    }

    void setTicketService(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    TicketListController getListController() {
        if (listController != null) {
            return listController;
        }
        return new TicketListController(ticketDAO, customerDAO, contractDAO, employeeDAO,
                getTicketService(), getPermissionService());
    }

    void setListController(TicketListController listController) {
        this.listController = listController;
    }

    TicketDetailController getDetailController() {
        if (detailController != null) {
            return detailController;
        }
        return new TicketDetailController(ticketDAO, customerDAO, contractDAO, employeeDAO,
                getTicketService(), getPermissionService());
    }

    void setDetailController(TicketDetailController detailController) {
        this.detailController = detailController;
    }

    TicketExportController getExportController() {
        if (exportController == null) {
            this.exportController = new TicketExportController(ticketDAO, employeeDAO, null,
                    getTicketService(), getPermissionService());
            try {
                this.exportController.setServletContext(getServletContext());
            } catch (Exception ignored) {
            }
        } else {
            if (exportController.getTicketDAO() != ticketDAO) {
                exportController.setTicketDAO(ticketDAO);
            }
            if (exportController.getEmployeeDAO() != employeeDAO) {
                exportController.setEmployeeDAO(employeeDAO);
            }
        }
        return exportController;
    }

    void setExportController(TicketExportController exportController) {
        this.exportController = exportController;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        if (this.exportController != null) {
            try {
                this.exportController.setServletContext(config.getServletContext());
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Cho JSP biết người đang xem có quyền Full trên tài nguyên này không,
        // để ẩn các nút hành động không dùng được (Tạo/Sửa/Xoá/Nhập/Xuất) thay
        // vì để người ta bấm vào rồi nhận 403. Đây CHỈ là lớp trình bày --
        // chặn thật nằm ở AccessControl.requireFullAccess trong doPost và ở
        // đầu mỗi trang form bên dưới (gõ thẳng URL cũng không vào được).
        request.setAttribute("canManage",
                AccessControl.hasFullAccess(request, AccessControl.Resource.TICKET));
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
            case "exportPdf":
                exportPdf(request, response);
                break;
            case "exportExcel":
                exportExcel(request, response);
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
        getListController().showList(request, response);
    }

    static final String ASSIGNEE_ALL = "all";
    static final String ASSIGNEE_MINE = "mine";

    private String assigneeFilterOf(HttpServletRequest request) {
        return getTicketService().resolveAssigneeFilter(
                request.getParameter("assignee"), AccessControl.currentUser(request));
    }

    private Integer assigneeIdOf(String assigneeFilter, HttpServletRequest request) {
        return getTicketService().resolveAssigneeId(assigneeFilter, AccessControl.currentUser(request));
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        getDetailController().showDetail(request, response);
    }

    private void exportExcel(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        getExportController().doGet(request, response);
    }

    private void exportPdf(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        TicketExportController ec = getExportController();
        try {
            if (ec.getCustomServletContext() == null && getServletContext() != null) {
                ec.setServletContext(getServletContext());
            }
        } catch (Exception ignored) {
        }
        ec.doGet(request, response);
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.TICKET)) {
            return;
        }
        setDropdownAttributes(request, null, null);
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

        if (!AccessControl.hasFullAccess(request, AccessControl.Resource.TICKET)
                && !AccessControl.canUpdateAssignedTicket(request, ticket)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Bạn không có quyền thực hiện thao tác này.");
            return;
        }

        request.setAttribute("ticket", ticket);
        setDropdownAttributes(request, ticket.getAssignedTechnicianId(), ticket.getEnterpriseId());
        request.getRequestDispatcher(UPDATE_VIEW).forward(request, response);
    }

    // ------------------------------------------------------------------
    // POST actions
    // ------------------------------------------------------------------

    private void handleCreate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.TICKET)) {
            return;
        }
        TechnicalRequest t = buildTicketFromRequest(request, new TechnicalRequest());
        t.setCreatedDate(new Date(System.currentTimeMillis()));
        t.setStatus(TechnicalSupportTicketDAO.STATUS_NEW);
        t.setCreatedBy(resolveCurrentUserId(request, t.getAssignedTechnicianId()));

        if (!isValid(t)) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=new&error=invalid");
            return;
        }
        if (!customerIsBuyer(t, null)) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=new&error=not_buyer");
            return;
        }
        if (!contractMatchesEnterprise(t)) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=new&error=contract_mismatch");
            return;
        }
        t.setTicketCode(ticketDAO.generateNextTicketCode());

        int newId = ticketDAO.insert(t);
        if (newId <= 0) {
            LOG.warn("Tao phieu ho tro that bai (actor={}, ticketCode={})", Logs.actor(request), t.getTicketCode());
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

        TechnicalRequest existing = ticketDAO.findById(id);
        if (existing == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
            return;
        }

        boolean fullAccess = AccessControl.hasFullAccess(request, AccessControl.Resource.TICKET);
        boolean assignedTechnicianUpdate = !fullAccess && AccessControl.canUpdateAssignedTicket(request, existing);
        if (!fullAccess && !assignedTechnicianUpdate) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Bạn không có quyền thực hiện thao tác này.");
            return;
        }

        TechnicalRequest t = fullAccess ? buildTicketFromRequest(request, new TechnicalRequest()) : existing;
        t.setTicketId(id);

        boolean contractPickerWasUsable = "1".equals(request.getParameter("contractLoaded"));
        if (t.getContractId() == null && !contractPickerWasUsable) {
            t.setContractId(existing.getContractId());
        }

        String slaParam = request.getParameter("slaDeadline");
        boolean slaClearedOnPurpose = slaParam != null && slaParam.trim().isEmpty();
        if (t.getSlaDeadline() == null && !slaClearedOnPurpose) {
            t.setSlaDeadline(existing.getSlaDeadline());
        }

        String previousStatus = existing.getStatus();
        Timestamp previousResolvedAt = existing.getResolvedAt();
        t.setStatus(emptyToNull(request.getParameter("status")));
        t.setResolutionSummary(emptyToNull(request.getParameter("resolutionSummary")));
        t.setRootCause(emptyToNull(request.getParameter("rootCause")));
        t.setCauseCategory(emptyToNull(request.getParameter("causeCategory")));
        t.setHandlingPlan(emptyToNull(request.getParameter("handlingPlan")));
        if (TechnicalSupportTicketDAO.STATUS_CLOSED.equals(t.getStatus())) {
            t.setResolvedAt(TechnicalSupportTicketDAO.STATUS_CLOSED.equals(previousStatus)
                    ? previousResolvedAt
                    : new Timestamp(System.currentTimeMillis()));
        } else {
            t.setResolvedAt(null);
        }

        if (!isValid(t) || !TechnicalSupportTicketDAO.isAllowedStatus(t.getStatus())) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=edit&id=" + id + "&error=invalid");
            return;
        }
        if (!customerIsBuyer(t, existing.getEnterpriseId())) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=edit&id=" + id + "&error=not_buyer");
            return;
        }
        if (!contractMatchesEnterprise(t)) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=edit&id=" + id + "&error=contract_mismatch");
            return;
        }

        int changedBy = resolveCurrentUserId(request, existing.getAssignedTechnicianId());
        boolean ok = ticketDAO.update(t, changedBy, emptyToNull(request.getParameter("internalNote")));
        if (!ok) {
            LOG.warn("Cap nhat phieu ho tro that bai (actor={}, ticketId={})", Logs.actor(request), id);
            response.sendRedirect(request.getContextPath() + "/ticket?action=edit&id=" + id + "&error=update_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/ticket?action=view&id=" + id);
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.TICKET)) {
            return;
        }
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

        // Câu UPDATE hỏng (mất kết nối, khoá bảng...) thì phiếu vẫn còn nguyên:
        // về đúng trang chi tiết của nó kèm lỗi. Trước đây luôn về danh sách như
        // đã xoá xong -- lỗi chỉ nằm trong log của DAO, người dùng không hề biết.
        if (!ticketDAO.softDelete(id)) {
            LOG.warn("Xoa phieu ho tro that bai (actor={}, ticketId={})", Logs.actor(request), id);
            response.sendRedirect(request.getContextPath() + "/ticket?action=view&id=" + id + "&error=delete_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/ticket");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * @param keepUserId kỹ thuật viên đang được giao phiếu đang sửa -- giữ
     *        trong dropdown kể cả khi họ đã đổi vai, xem
     *        EmployeeDAO.findActiveByRole.
     * @param keepCustomerId khách hàng đang đứng trên phiếu đang sửa -- giữ
     *        trong ô chọn kể cả khi họ không (còn) giữ vai khách mua (phiếu cũ
     *        lập trước khi ô này lọc theo vai), nếu không thì mở form sửa lên ô
     *        trống. Cùng cái bẫy ContractController đã chữa cho ô khách hàng.
     */
    private void setDropdownAttributes(HttpServletRequest request, Integer keepUserId, Integer keepCustomerId) {
        // Chỉ khách giữ vai "Khách mua". Trước đây đổ mọi doanh nghiệp, kể cả
        // nhà cung cấp -- chọn nhầm một nhà cung cấp thì ô hợp đồng bên cạnh
        // liệt kê hợp đồng MUA của mình với họ.
        request.setAttribute("customerList", customerDAO.findAllByRole(ROLE_BUYER, keepCustomerId));
        // Phiếu giao cho Kỹ thuật. Trước đây đổ cả 15 người vào đây trong khi
        // ô này tên là "Kỹ thuật viên phụ trách" -- chọn nhầm một CSKH thì
        // phiếu nằm im vì người đó không có quyền sửa phiếu được giao.
        request.setAttribute("userList", employeeDAO.findActiveByRole(TECHNICIAN_ROLE, keepUserId));
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
        t.setSlaDeadline(parseDateTimeOrNull(request.getParameter("slaDeadline")));
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

    /**
     * Khách hàng của phiếu phải giữ vai "Khách mua".
     *
     * <p>Ô chọn ở form đã lọc theo vai, nhưng đó chỉ là khoá hình: một POST tự
     * dựng gửi được enterprise_id của một nhà cung cấp. Kiểm lại ở đây, như
     * ContractController kiểm vai của đối tác theo chiều hợp đồng.
     *
     * @param keepEnterpriseId khách đang đứng trên phiếu đang sửa (null khi tạo
     *        mới) -- giữ nguyên khách cũ thì luôn hợp lệ, để phiếu lập trước khi
     *        có luật này vẫn lưu được mà không phải đổi khách hàng.
     */
    private boolean customerIsBuyer(TechnicalRequest t, Integer keepEnterpriseId) {
        if (keepEnterpriseId != null && t.getEnterpriseId() == keepEnterpriseId) {
            return true;
        }
        return customerDAO.findRolesOf(t.getEnterpriseId()).contains(ROLE_BUYER);
    }

    /**
     * Hợp đồng gắn vào phiếu phải thuộc đúng khách hàng của phiếu đó.
     *
     * Không có ràng buộc nào ở CSDL cho cặp (enterprise_id, contract_id), và
     * isValid() cũng không đối chiếu -- nên nếu chỉ dựa vào JS thì một request
     * tự dựng (hoặc một lỗi JS) là ghi được phiếu "khách hàng B, hợp đồng của
     * A". Trang chi tiết, file PDF và file Excel đều sẽ hiện hợp đồng của công
     * ty khác mà không ai biết.
     *
     * @return true nếu hợp lệ (kể cả khi phiếu không gắn hợp đồng nào).
     */
    private boolean contractMatchesEnterprise(TechnicalRequest t) {
        if (t.getContractId() == null) {
            return true;
        }
        Contract contract = contractDAO.findById(t.getContractId());
        return contract != null && contract.getEnterpriseId() == t.getEnterpriseId();
    }

    /**
     * Các trường bắt buộc phải có khi tạo/sửa phiếu hỗ trợ.
     *
     * <p>Mức ưu tiên kiểm theo DANH SÁCH CHO PHÉP, không chỉ "khác null"
     * (BR-41). Cột {@code priority} là {@code varchar} nên CSDL nhận mọi chuỗi,
     * còn dropdown chỉ là giao diện -- một POST tự dựng đặt được giá trị thứ
     * năm, và phiếu đó rơi xuống cuối mọi bảng sắp xếp theo
     * {@code FIELD(t.priority, ...)} mà không có màn hình nào nói vì sao.
     */
    private boolean isValid(TechnicalRequest t) {
        return t.getEnterpriseId() > 0
                && t.getTicketType() != null
                && TechnicalSupportTicketDAO.isAllowedPriority(t.getPriority())
                && t.getReceptionChannel() != null
                && t.getAssignedTechnicianId() > 0
                && t.getDescription() != null;
    }

    private Timestamp parseDateTimeOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Timestamp.valueOf(LocalDateTime.parse(value.trim()));
        } catch (DateTimeParseException ex) {
            return null;
        }
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
