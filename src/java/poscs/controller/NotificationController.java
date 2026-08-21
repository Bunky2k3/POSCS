package poscs.controller;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import poscs.dao.NotificationDAO;
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
        if ("readAll".equals(action)) {
            notificationDAO.markAllAsRead(currentUser.getUserId());
            response.sendRedirect(request.getContextPath() + "/notifications");
            return;
        }

        request.setAttribute("notifications", notificationDAO.findAllByUser(currentUser.getUserId()));
        request.getRequestDispatcher("/notifications.jsp").forward(request, response);
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
