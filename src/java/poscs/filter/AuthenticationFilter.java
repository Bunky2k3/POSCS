package poscs.filter;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Chặn TOÀN BỘ request khi chưa đăng nhập (session không có "currentUser"),
 * trừ đúng các trang/servlet thuộc luồng đăng nhập / quên mật khẩu liệt kê
 * trong PUBLIC_PATHS -- đây là danh sách "cho phép" (allow-list), mặc định
 * MỌI url khác đều bị coi là cần đăng nhập, kể cả những url chưa tồn tại
 * hoặc sẽ được thêm sau này (an toàn hơn so với danh sách "chặn" (deny-list),
 * vì lỡ quên thêm route mới vào deny-list thì route đó sẽ bị public "hớ hênh"
 * -- còn với allow-list, quên thêm thì route mới lại mặc định BỊ chặn, chỉ
 * cần thêm vào PUBLIC_PATHS khi thật sự muốn nó công khai).
 *
 * Đồng thời tắt cache trình duyệt cho mọi response đi qua filter này -- lý do:
 * nếu không có các header no-cache, sau khi đăng xuất (session đã bị huỷ ở
 * server) mà bấm nút Back trên trình duyệt, trình duyệt có thể hiển thị lại
 * bản HTML đã cache từ lúc còn đăng nhập MÀ KHÔNG gửi request mới lên server
 * -- nhìn như vẫn còn đăng nhập, dù session thật đã chết từ lâu.
 */
@WebFilter(urlPatterns = "/*")
public class AuthenticationFilter implements Filter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/login.jsp",
            "/forgotPassword.jsp",
            "/verifyOtp.jsp",
            "/resetPassword.jsp",
            "/login",
            "/ForgotPasswordServlet",
            "/VerifyOtpServlet",
            "/ResetPasswordServlet"
    );

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        if (PUBLIC_PATHS.contains(request.getServletPath())) {
            // Riêng login.jsp: hễ có request nào chạm tới trang này (kể cả bấm
            // Back quay lại, không qua nút "Đăng xuất") mà vẫn còn session hợp
            // lệ, huỷ session đó luôn. Lý do: login.jsp đại diện cho trạng thái
            // "chưa đăng nhập" -- nếu vẫn giữ session sống, bấm nút Forward
            // hoặc gõ lại URL trang trong hệ thống sẽ vào được ngay mà không
            // cần gõ lại mật khẩu, coi như coi thường ý định "quay về màn hình
            // đăng nhập" của người dùng.
            if ("/login.jsp".equals(request.getServletPath())) {
                HttpSession existing = request.getSession(false);
                if (existing != null && existing.getAttribute("currentUser") != null) {
                    existing.invalidate();
                }
            }
            chain.doFilter(request, response);
            return;
        }

        // getSession(false): không tự tạo session mới chỉ để kiểm tra --
        // nếu chưa từng đăng nhập thì session là null, coi như chưa đăng nhập.
        HttpSession session = request.getSession(false);
        boolean loggedIn = session != null && session.getAttribute("currentUser") != null;
        if (!loggedIn) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        chain.doFilter(request, response);
    }
}
