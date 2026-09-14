package poscs.controller;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.AccessControl;
import poscs.common.Logs;
import poscs.dao.ChangeRequestDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.NotificationDAO;
import poscs.model.ChangeRequest;
import poscs.model.User;

/**
 * Yêu cầu thay đổi dữ liệu: cấp dưới gửi lên, cấp trên duyệt hoặc từ chối.
 *
 * <p>Nửa còn lại của mô hình phân cấp chốt với khách hàng 14/09/2026. V16 đã
 * lấy quyền ghi Khách hàng/Hợp đồng khỏi tầng lá; màn hình này là đường duy
 * nhất trong hệ thống để họ xin cấp trên làm hộ (xem PERMISSIONS.md).
 *
 * <p><b>Ai được làm gì</b> -- quyết định bởi VỊ TRÍ trong cây tổ chức, không
 * phải vai trò, nên không dùng {@code AccessControl.Resource} nào ở đây:
 *
 * <ul>
 *   <li>gửi yêu cầu: người có cấp trên (cấp dưới). Người không có cấp trên tự
 *       sửa được rồi, gửi yêu cầu cho chính mình là vô nghĩa.</li>
 *   <li>duyệt/từ chối: đúng cấp trên của người gửi, hoặc Admin. Kiểm lại
 *       từng yêu cầu một ở {@link #canReview}, không chỉ kiểm "có phải cấp
 *       trên của ai đó không" -- nếu không thì quản lý vùng A duyệt được cả
 *       yêu cầu của cấp dưới vùng B.</li>
 * </ul>
 */
@WebServlet(name = "ChangeRequestController", urlPatterns = {"/changerequest"})
public class ChangeRequestController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ChangeRequestController.class);

    private static final String LIST_VIEW = "/jsp/changerequest/listChangeRequest.jsp";
    private static final String CREATE_VIEW = "/jsp/changerequest/addChangeRequest.jsp";
    private static final String DETAIL_VIEW = "/jsp/changerequest/viewdetailChangeRequest.jsp";

    private final ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = AccessControl.currentUser(request);
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        String action = request.getParameter("action");
        if ("new".equals(action)) {
            showCreateForm(request, response, currentUser);
        } else if ("view".equals(action)) {
            showDetail(request, response, currentUser);
        } else {
            showList(request, response, currentUser);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User currentUser = AccessControl.currentUser(request);
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        String action = request.getParameter("action");
        if ("create".equals(action)) {
            handleCreate(request, response, currentUser);
        } else if ("review".equals(action)) {
            handleReview(request, response, currentUser);
        } else {
            response.sendRedirect(request.getContextPath() + "/changerequest");
        }
    }

    // ------------------------------------------------------------------
    // GET
    // ------------------------------------------------------------------

    /**
     * Danh sách. Cùng một URL phục vụ hai vai khác hẳn nhau, phân biệt bằng
     * chỗ đứng trong cây chứ không bằng tham số -- cấp dưới thấy yêu cầu MÌNH
     * đã gửi, cấp trên thấy yêu cầu cần MÌNH duyệt.
     */
    private void showList(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws ServletException, IOException {
        String statusFilter = emptyToNull(request.getParameter("status"));
        boolean reviewer = !currentUser.isSubordinate();

        if (AccessControl.isAdmin(request)) {
            request.setAttribute("requestList", changeRequestDAO.findAll(statusFilter));
        } else if (reviewer) {
            request.setAttribute("requestList", changeRequestDAO.findForManager(currentUser.getUserId(), statusFilter));
        } else {
            request.setAttribute("requestList", changeRequestDAO.findByRequester(currentUser.getUserId()));
        }
        request.setAttribute("isReviewer", reviewer);
        request.setAttribute("canSubmit", currentUser.isSubordinate());
        request.setAttribute("statusFilter", statusFilter);
        request.getRequestDispatcher(LIST_VIEW).forward(request, response);
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws ServletException, IOException {
        if (!currentUser.isSubordinate()) {
            // Người không có cấp trên tự sửa được, không có ai để gửi lên.
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Bạn tự thực hiện được thay đổi này, không cần gửi yêu cầu.");
            return;
        }
        // Cho phép mở sẵn form từ màn Khách hàng/Hợp đồng: ?resourceType=...&intent=...&targetId=...
        request.setAttribute("presetResourceType", emptyToNull(request.getParameter("resourceType")));
        request.setAttribute("presetIntent", emptyToNull(request.getParameter("intent")));
        request.setAttribute("presetTargetId", parseIntOrNull(request.getParameter("targetId")));
        request.getRequestDispatcher(CREATE_VIEW).forward(request, response);
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        ChangeRequest r = id != null ? changeRequestDAO.findById(id) : null;
        if (r == null) {
            response.sendRedirect(request.getContextPath() + "/changerequest?error=notfound");
            return;
        }
        if (!canSee(request, currentUser, r)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Bạn không có quyền xem yêu cầu này.");
            return;
        }
        request.setAttribute("changeRequest", r);
        request.setAttribute("canReview", canReview(request, currentUser, r));
        request.getRequestDispatcher(DETAIL_VIEW).forward(request, response);
    }

    // ------------------------------------------------------------------
    // POST
    // ------------------------------------------------------------------

    private void handleCreate(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws IOException {
        if (!currentUser.isSubordinate()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Bạn tự thực hiện được thay đổi này, không cần gửi yêu cầu.");
            return;
        }

        ChangeRequest r = new ChangeRequest();
        r.setResourceType(emptyToNull(request.getParameter("resourceType")));
        r.setIntent(emptyToNull(request.getParameter("intent")));
        r.setTargetId(parseIntOrNull(request.getParameter("targetId")));
        r.setProposedContent(emptyToNull(request.getParameter("proposedContent")));
        r.setReason(emptyToNull(request.getParameter("reason")));
        // Người gửi lấy từ session, KHÔNG nhận từ form: đây là cột dùng để quy
        // trách nhiệm, mà form thì ai cũng sửa được.
        r.setRequestedBy(currentUser.getUserId());

        if (!isValid(r)) {
            response.sendRedirect(request.getContextPath() + "/changerequest?action=new&error=invalid");
            return;
        }

        int newId = changeRequestDAO.insert(r);
        if (newId <= 0) {
            LOG.warn("Gui yeu cau thay doi that bai (actor={})", Logs.actor(request));
            response.sendRedirect(request.getContextPath() + "/changerequest?action=new&error=create_failed");
            return;
        }

        // Báo cho cấp trên. Yêu cầu nằm im không ai biết thì cả tính năng này
        // chỉ là một cái hộp câm.
        notificationDAO.insert(currentUser.getManagerId(),
                "Có yêu cầu thay đổi mới cần bạn duyệt",
                "CHANGE_REQUEST", newId);

        response.sendRedirect(request.getContextPath() + "/changerequest?action=view&id=" + newId);
    }

    private void handleReview(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws IOException {
        Integer id = parseIntOrNull(request.getParameter("requestId"));
        ChangeRequest r = id != null ? changeRequestDAO.findById(id) : null;
        if (r == null) {
            response.sendRedirect(request.getContextPath() + "/changerequest?error=notfound");
            return;
        }
        if (!canReview(request, currentUser, r)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Bạn không có quyền duyệt yêu cầu này.");
            return;
        }

        String decision = request.getParameter("decision");
        String newStatus = "approve".equals(decision) ? ChangeRequest.STATUS_APPROVED
                         : "reject".equals(decision) ? ChangeRequest.STATUS_REJECTED
                         : null;
        if (newStatus == null) {
            response.sendRedirect(request.getContextPath() + "/changerequest?action=view&id=" + id + "&error=invalid");
            return;
        }

        boolean ok = changeRequestDAO.review(id, newStatus, currentUser.getUserId(),
                emptyToNull(request.getParameter("reviewNote")));
        if (!ok) {
            // Gần như luôn là vì người khác đã quyết định trước -- câu UPDATE
            // chỉ ăn khi yêu cầu còn ở trạng thái chờ.
            response.sendRedirect(request.getContextPath() + "/changerequest?action=view&id=" + id + "&error=already_reviewed");
            return;
        }

        // Ghép chuỗi kiểu "đã được " + status sẽ ra "đã được đã duyệt" -- trạng
        // thái vốn đã là một mệnh đề hoàn chỉnh, không phải tính từ để nối vào.
        notificationDAO.insert(r.getRequestedBy(),
                ChangeRequest.STATUS_APPROVED.equals(newStatus)
                        ? "Yêu cầu thay đổi của bạn đã được duyệt"
                        : "Yêu cầu thay đổi của bạn bị từ chối",
                "CHANGE_REQUEST", id);

        response.sendRedirect(request.getContextPath() + "/changerequest?action=view&id=" + id);
    }

    // ------------------------------------------------------------------
    // Quyền
    // ------------------------------------------------------------------

    /** Người gửi xem được yêu cầu của mình; người duyệt được xem yêu cầu mình duyệt. */
    private boolean canSee(HttpServletRequest request, User currentUser, ChangeRequest r) {
        return r.getRequestedBy() == currentUser.getUserId() || canReview(request, currentUser, r);
    }

    /**
     * Duyệt được yêu cầu này không.
     *
     * Phải tra lại cấp trên CỦA CHÍNH NGƯỜI GỬI, chứ không chỉ hỏi "người đang
     * đăng nhập có phải cấp trên của ai đó không": hai câu đó khác nhau, và
     * nhầm câu thứ hai thì quản lý vùng A duyệt được yêu cầu của cấp dưới
     * vùng B. Admin duyệt được tất cả.
     */
    private boolean canReview(HttpServletRequest request, User currentUser, ChangeRequest r) {
        if (AccessControl.isAdmin(request)) {
            return true;
        }
        // Đọc lại người gửi từ CSDL thay vì tin vào bản join sẵn: cấp trên có
        // thể vừa được đổi sau khi yêu cầu được tạo, và quyền phải theo cây
        // tổ chức HIỆN TẠI chứ không theo lúc gửi.
        User requester = employeeDAO.findById(r.getRequestedBy());
        return requester != null
                && requester.getManagerId() != null
                && requester.getManagerId() == currentUser.getUserId();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Các trường bắt buộc. targetId chỉ bắt buộc khi KHÔNG phải "Tạo mới". */
    private boolean isValid(ChangeRequest r) {
        boolean resourceOk = ChangeRequest.RESOURCE_CUSTOMER.equals(r.getResourceType())
                || ChangeRequest.RESOURCE_CONTRACT.equals(r.getResourceType());
        boolean intentOk = ChangeRequest.INTENT_CREATE.equals(r.getIntent())
                || ChangeRequest.INTENT_UPDATE.equals(r.getIntent())
                || ChangeRequest.INTENT_DELETE.equals(r.getIntent());
        if (!resourceOk || !intentOk || r.getProposedContent() == null) {
            return false;
        }
        return ChangeRequest.INTENT_CREATE.equals(r.getIntent()) || r.getTargetId() != null;
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

    private String emptyToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }
}
