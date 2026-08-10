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
 * Đăng nhập / đăng xuất / đổi mật khẩu (khi đã đăng nhập). Đăng nhập thành
 * công lưu {@link User} (đã kèm {@link poscs.model.Role}) vào session dưới
 * key "currentUser" -- các controller khác dựa vào key này để enforce phân
 * quyền theo PERMISSIONS.md.
 */
@WebServlet(name = "AuthenticationController", urlPatterns = {"/login", "/changePassword"})
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
        // 2 form khác nhau (login.jsp và changePassword.jsp) POST tới 2 URL
        // khác nhau nhưng cùng 1 servlet này xử lý -- dùng getServletPath() để
        // biết đang xử lý request nào (khớp với url-pattern ở @WebServlet).
        if ("/changePassword".equals(request.getServletPath())) {
            handleChangePassword(request, response);
            return;
        }
        handleLogin(request, response);
    }

    // ------------------------------------------------------------------
    // Đăng nhập
    // ------------------------------------------------------------------

    private void handleLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Form login.jsp gửi lên field "username", nhưng người dùng có thể gõ
        // username HOẶC email vào đó -- EmployeeDAO.findByUsernameOrEmail sẽ
        // khớp cả 2 khả năng.
        String identifier = trimToNull(request.getParameter("username"));
        String password = request.getParameter("password");

        if (identifier == null || password == null || password.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=missing_fields");
            return;
        }

        User user = employeeDAO.findByUsernameOrEmail(identifier);
        // Gộp chung 2 trường hợp "không tìm thấy user" và "sai mật khẩu" thành
        // cùng 1 thông báo lỗi ở phía client (login.jsp), để không lộ cho kẻ tấn
        // công biết username/email nào tồn tại trong hệ thống (chỉ khác nhau ở
        // bước kiểm tra: nếu user == null thì gọi BCrypt.checkpw sẽ NullPointerException,
        // nên phải kiểm tra user == null trước bằng toán tử || ngắn mạch).
        if (user == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=invalid_credentials");
            return;
        }

        // Đăng nhập thành công: tạo session mới (true = tạo nếu chưa có) và lưu
        // user vào đó. Các servlet khác sau này sẽ đọc session.getAttribute("currentUser")
        // để biết ai đang đăng nhập và role của họ là gì (theo PERMISSIONS.md).
        HttpSession session = request.getSession(true);
        session.setAttribute("currentUser", user);
        response.sendRedirect(request.getContextPath() + "/dashboard.jsp");
    }

    // ------------------------------------------------------------------
    // Đổi mật khẩu (yêu cầu đã đăng nhập + biết đúng mật khẩu hiện tại)
    // ------------------------------------------------------------------

    private void handleChangePassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        User currentUser = session != null ? (User) session.getAttribute("currentUser") : null;

        // Chưa đăng nhập thì không có gì để đổi -- đá về trang login.
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        String oldPassword = request.getParameter("oldPassword");
        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");

        // Luôn tra lại user MỚI NHẤT từ DB để lấy password_hash hiện hành,
        // KHÔNG dùng hash cũ đang cache trong session -- phòng trường hợp mật
        // khẩu đã bị đổi ở nơi khác (vd. một tab khác) từ lúc đăng nhập tới giờ.
        User freshUser = employeeDAO.findByUsernameOrEmail(currentUser.getUsername());
        if (freshUser == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        // Bắt buộc phải đúng mật khẩu hiện tại thì mới cho đổi -- đây chính là
        // yêu cầu cốt lõi của tính năng này, khác với luồng "quên mật khẩu"
        // (nơi xác thực bằng OTP email thay vì bằng mật khẩu cũ).
        if (oldPassword == null || !BCrypt.checkpw(oldPassword, freshUser.getPasswordHash())) {
            response.sendRedirect(request.getContextPath() + "/changePassword.jsp?error=wrong_old_password");
            return;
        }

        if (newPassword == null || newPassword.length() < 8) {
            response.sendRedirect(request.getContextPath() + "/changePassword.jsp?error=weak_password");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            response.sendRedirect(request.getContextPath() + "/changePassword.jsp?error=mismatch");
            return;
        }
        // Yêu cầu ghi rõ trong hint-list của changePassword.jsp: "Không trùng với mật khẩu cũ".
        if (BCrypt.checkpw(newPassword, freshUser.getPasswordHash())) {
            response.sendRedirect(request.getContextPath() + "/changePassword.jsp?error=same_as_old");
            return;
        }

        String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        boolean ok = employeeDAO.updatePasswordByEmail(freshUser.getEmail(), newHash);
        if (!ok) {
            response.sendRedirect(request.getContextPath() + "/changePassword.jsp?error=update_failed");
            return;
        }

        // Cập nhật luôn hash trong session để các request tiếp theo trong cùng
        // phiên đăng nhập này thấy đúng mật khẩu mới (không cần đăng nhập lại).
        currentUser.setPasswordHash(newHash);
        response.sendRedirect(request.getContextPath() + "/changePassword.jsp?success=1");
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
