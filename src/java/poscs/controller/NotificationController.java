package poscs.controller;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import poscs.dao.NotificationDAO;
import poscs.model.Notification;
import poscs.model.User;

/**
 * Trang "Xem tất cả thông báo" (notifications.jsp) + 2 hành động đánh dấu đã
 * đọc, thực hiện qua link GET (?action=read / ?action=readAll) theo đúng
 * tiền lệ của logout ở AuthenticationController -- không cần form CSRF cho
 * 1 thao tác chỉ đổi cờ is_read, không phá huỷ dữ liệu.
 */
@WebServlet(name = "NotificationController", urlPatterns = {"/notifications"})
public class NotificationController extends HttpServlet {

    private final NotificationDAO notificationDAO = new NotificationDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User currentUser = session != null ? (User) session.getAttribute("currentUser") : null;

        // AuthenticationFilter đã chặn request chưa đăng nhập từ trước, kiểm
        // tra lại ở đây theo đúng tiền lệ của AuthenticationController.
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        String action = request.getParameter("action");
        if ("read".equals(action)) {
            Integer id = parseIntOrNull(request.getParameter("id"));
            if (id != null) {
                notificationDAO.markAsRead(id, currentUser.getUserId());
            }
            response.sendRedirect(request.getContextPath() + "/notifications");
            return;
        }
        if ("open".equals(action)) {
            // Bấm một thông báo (chuông ở thanh trên, hoặc trang này): đánh dấu
            // đã đọc rồi mở thẳng thứ nó nói tới. Trước đây chỉ đánh dấu rồi quay
            // về trang này -- người nhận "Hợp đồng ... sắp hết hạn" phải tự đi
            // tìm hợp đồng đó. Trang đích tự kiểm quyền xem như mọi lần mở khác.
            Integer id = parseIntOrNull(request.getParameter("id"));
            Notification n = id != null ? notificationDAO.findByIdForUser(id, currentUser.getUserId()) : null;
            if (n != null) {
                notificationDAO.markAsRead(n.getNotificationId(), currentUser.getUserId());
            }
            response.sendRedirect(request.getContextPath() + targetOf(n));
            return;
        }
        if ("readAll".equals(action)) {
            notificationDAO.markAllAsRead(currentUser.getUserId());
            response.sendRedirect(request.getContextPath() + "/notifications");
            return;
        }

        request.setAttribute("notifications", notificationDAO.findAllByUser(currentUser.getUserId()));
        request.getRequestDispatcher("/notifications.jsp").forward(request, response);
    }

    /**
     * Trang của thứ thông báo nói tới -- ref_type do NotificationScheduler
     * ("contract_expiring", "ticket_sla") và ChangeRequestController
     * ("CHANGE_REQUEST") ghi. Loại lạ, thiếu id hay không tìm thấy thông báo
     * thì về trang Thông báo như trước.
     */
    static String targetOf(Notification n) {
        if (n == null || n.getRefType() == null || n.getRefId() == null) {
            return "/notifications";
        }
        switch (n.getRefType()) {
            case "contract_expiring":
                return "/contract?action=view&id=" + n.getRefId();
            case "ticket_sla":
                return "/ticket?action=view&id=" + n.getRefId();
            case "CHANGE_REQUEST":
                return "/changerequest?action=view&id=" + n.getRefId();
            default:
                return "/notifications";
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
}
