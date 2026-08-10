package poscs.controller;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;
import poscs.dao.EmployeeDAO;
import poscs.model.User;

/**
 * Đăng nhập / đăng xuất. Đăng nhập thành công lưu {@link User} (đã kèm
 * {@link poscs.model.Role}) vào session dưới key "currentUser" -- các
 * controller khác dựa vào key này để enforce phân quyền theo PERMISSIONS.md.
 */
@WebServlet(name = "AuthenticationController", urlPatterns = {"/login"})
public class AuthenticationController extends HttpServlet {

    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if ("logout".equals(action)) {
            // getSession(false): KHÔNG tự tạo session mới nếu chưa có -- nếu
            // request này chưa từng đăng nhập thì session sẽ là null, và ta
            // không cần làm gì thêm (đằng nào cũng chưa có gì để huỷ).
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate(); // xoá toàn bộ session, bao gồm cả currentUser
            }
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }
        // GET tới /login mà không phải logout (vd. gõ thẳng URL) -- servlet này
        // không tự vẽ form, chỉ điều hướng sang login.jsp (trang tĩnh) để hiển thị.
        response.sendRedirect(request.getContextPath() + "/login.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Form login.jsp gửi lên field "username", nhưng người dùng có thể gõ
        // username HOẶC email vào đó -- EmployeeDAO.findByUsernameOrEmail sẽ
        // khớp cả 2 khả năng.
        String identifier = trimToNull(request.getParameter("username"));
        String password = request.getParameter("password");

        if (identifier == null || password == null || password.isEmpty()) {
            redirectWithError(request, response, "missing_fields");
            return;
        }

        User user = employeeDAO.findByUsernameOrEmail(identifier);
        // Gộp chung 2 trường hợp "không tìm thấy user" và "sai mật khẩu" thành
        // cùng 1 thông báo lỗi ở phía client (login.jsp), để không lộ cho kẻ tấn
        // công biết username/email nào tồn tại trong hệ thống (chỉ khác nhau ở
        // bước kiểm tra: nếu user == null thì gọi BCrypt.checkpw sẽ NullPointerException,
        // nên phải kiểm tra user == null trước bằng toán tử || ngắn mạch).
        if (user == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
            redirectWithError(request, response, "invalid_credentials");
            return;
        }

        // Đăng nhập thành công: tạo session mới (true = tạo nếu chưa có) và lưu
        // user vào đó. Các servlet khác sau này sẽ đọc session.getAttribute("currentUser")
        // để biết ai đang đăng nhập và role của họ là gì (theo PERMISSIONS.md).
        HttpSession session = request.getSession(true);
        session.setAttribute("currentUser", user);
        response.sendRedirect(request.getContextPath() + "/dashboard.jsp");
    }

    /** Điều hướng về login.jsp kèm mã lỗi trên query string để hiển thị banner báo lỗi. */
    private void redirectWithError(HttpServletRequest request, HttpServletResponse response, String error)
            throws IOException {
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + error);
    }

    /** Trim khoảng trắng thừa; chuỗi rỗng sau khi trim coi như null (chưa nhập). */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
