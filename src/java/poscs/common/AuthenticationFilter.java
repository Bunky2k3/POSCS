package poscs.common;

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
import poscs.dao.EmployeeDAO;
import poscs.dao.NotificationDAO;
import poscs.model.User;

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
 *
 * Cũng là nơi enforce CSRF: mọi request POST (kể cả các form công khai như
 * login/quên mật khẩu) phải kèm đúng tham số "csrfToken" khớp với token lưu
 * trong session, nếu không sẽ bị từ chối bằng 403 -- xem CsrfUtil. Vì vậy
 * filter luôn đảm bảo session (và token trong đó) tồn tại cho MỌI request
 * (kể cả GET, để JSP có token sẵn mà nhúng vào form khi render), thay vì chỉ
 * getSession(false) như trước.
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
            "/ResetPasswordServlet",
            "/ResendOtpServlet"
    );

    // Ảnh thương hiệu đóng gói sẵn trong WAR (logo, ảnh nền panel branding).
    // login.jsp và forgotPassword.jsp nhúng chúng khi người dùng CHƯA đăng
    // nhập, nên nếu filter chặn thì trình duyệt nhận về 302 trỏ login.jsp
    // thay vì file ảnh -- logo sẽ không hiện. Chỉ mở đúng thư mục này: file
    // do người dùng tải lên nằm ở "/uploads/*" (UploadFileController) và vẫn
    // phải đăng nhập mới xem được.
    private static final String BRANDING_ASSET_PREFIX = "/img/";

    /** Khớp url-pattern của UploadFileController -- xem {@link #rendersTopbar}. */
    private static final String UPLOAD_PATH_PREFIX = "/uploads/";

    // Số thông báo gần nhất bơm sẵn cho dropdown chuông ở topbar.jsp (trang
    // "Xem tất cả" tự tra lại đầy đủ qua NotificationController, không dùng
    // request attribute này).
    private static final int RECENT_NOTIFICATIONS_LIMIT = 5;

    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        // Không cho trình duyệt tự đoán kiểu nội dung từ chính dữ liệu và bỏ
        // qua Content-Type do server khai báo -- nếu không, một file người dùng
        // tải lên có thể được diễn giải thành HTML và chạy trên origin này.
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Chặn nhúng trang vào iframe của site khác (clickjacking): mọi thao
        // tác trong hệ thống đều sau đăng nhập, không trang nào cần được nhúng.
        response.setHeader("X-Frame-Options", "DENY");

        boolean isPublicPath = PUBLIC_PATHS.contains(request.getServletPath())
                || request.getServletPath().startsWith(BRANDING_ASSET_PREFIX);

        // Riêng login.jsp: hễ có request nào chạm tới trang này (kể cả bấm
        // Back quay lại, không qua nút "Đăng xuất") mà vẫn còn session hợp
        // lệ, huỷ session đó luôn. Lý do: login.jsp đại diện cho trạng thái
        // "chưa đăng nhập" -- nếu vẫn giữ session sống, bấm nút Forward
        // hoặc gõ lại URL trang trong hệ thống sẽ vào được ngay mà không
        // cần gõ lại mật khẩu, coi như coi thường ý định "quay về màn hình
        // đăng nhập" của người dùng.
        if (isPublicPath && "/login.jsp".equals(request.getServletPath())) {
            HttpSession existing = request.getSession(false);
            if (existing != null && existing.getAttribute("currentUser") != null) {
                existing.invalidate();
            }
        }

        // getSession(true): khác với check đăng nhập (chỉ cần biết CÓ session
        // hợp lệ hay không), CSRF token cần tồn tại ngay cả cho khách chưa
        // đăng nhập -- các form công khai (login, quên mật khẩu...) cũng POST
        // và cũng cần được bảo vệ CSRF.
        HttpSession session = request.getSession(true);
        String csrfToken = CsrfUtil.getOrCreateToken(session);
        request.setAttribute("csrfToken", csrfToken);

        if ("POST".equalsIgnoreCase(request.getMethod()) && !CsrfUtil.isValid(request, session)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Yêu cầu không hợp lệ (thiếu hoặc sai CSRF token).");
            return;
        }

        if (isPublicPath) {
            chain.doFilter(request, response);
            return;
        }

        boolean loggedIn = session.getAttribute("currentUser") != null;
        if (!loggedIn) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        // Tài khoản có thể đã bị admin khoá SAU khi người này đăng nhập. Chỉ
        // chặn ở màn hình đăng nhập là không đủ: phiên đang mở vẫn xem và sửa
        // được dữ liệu cho tới khi họ tự đăng xuất hoặc session hết hạn -- tức
        // là thao tác khoá tài khoản (nhân viên nghỉ việc, lộ mật khẩu) không
        // có tác dụng ngay, đúng lúc cần nhất.
        //
        // Kiểm mỗi request thay vì cache theo thời gian: cache bao nhiêu giây
        // thì tài khoản bị khoá vẫn dùng được bấy nhiêu giây. Một câu SELECT
        // theo cột UNIQUE, và filter này vốn đã chạy 2 query thông báo cho mỗi
        // trang, nên thêm câu này không đổi bậc chi phí.
        if (!isStaticAssetPath(request.getServletPath())) {
            User sessionUser = (User) session.getAttribute("currentUser");
            User freshUser = employeeDAO.findByUsernameOrEmail(sessionUser.getUsername());
            if (freshUser == null || freshUser.isDeleted()) {
                session.invalidate();
                response.sendRedirect(request.getContextPath()
                        + "/login.jsp?error=account_inactive");
                return;
            }
            // Đã tra ra bản mới rồi thì thay luôn bản trong session, đừng chỉ
            // dùng nó để kiểm khoá tài khoản.
            //
            // AccessControl đọc role và manager_id TỪ SESSION. Giữ bản cũ nghĩa
            // là Admin đổi vai trò của một người, hoặc gán cấp trên cho họ
            // (thao tác bật cơ chế siết quyền ở PERMISSIONS.md), mà thay đổi đó
            // không có hiệu lực cho tới lần họ đăng nhập lại -- trong khi thao
            // tác khoá tài khoản ngay bên trên lại có hiệu lực tức thì. Hai
            // hành vi khác nhau cho hai thao tác quản trị cạnh nhau, không có
            // lý do gì.
            //
            // Xoá chuỗi băm mật khẩu TRƯỚC khi cất vào session, đúng như
            // AuthenticationController.handleLogin làm: mọi JSP đều đọc được
            // ${sessionScope.currentUser.*}, nên chỉ cần một lần lỡ tay in cả
            // object ra là lộ hash. findByUsernameOrEmail trả về nó vì luồng
            // đăng nhập cần, ở đây thì không.
            freshUser.setPasswordHash(null);
            session.setAttribute("currentUser", freshUser);
        }

        // Bơm sẵn dữ liệu chuông thông báo cho topbar.jsp -- topbar được
        // include ở MỌI trang sau đăng nhập mà không qua controller riêng,
        // nên nơi duy nhất chạy trước tất cả các trang đó là filter này.
        if (rendersTopbar(request.getServletPath())) {
            User currentUser = (User) session.getAttribute("currentUser");
            request.setAttribute("unreadNotifCount", notificationDAO.countUnread(currentUser.getUserId()));
            request.setAttribute("recentNotifications",
                    notificationDAO.findRecentByUser(currentUser.getUserId(), RECENT_NOTIFICATIONS_LIMIT));
        }

        chain.doFilter(request, response);
    }

    /**
     * File nằm sẵn trong WAR: CSS và JS. Không mang dữ liệu nghiệp vụ của ai,
     * nên request tới đây bỏ qua được cả phép kiểm tài khoản bị khoá.
     */
    private static boolean isStaticAssetPath(String servletPath) {
        return servletPath.startsWith("/css/") || servletPath.startsWith("/js/");
    }

    /**
     * true nếu response của đường này có vẽ topbar, tức là có cần dữ liệu
     * chuông thông báo.
     *
     * <p>Tách khỏi {@link #isStaticAssetPath} vì hai câu hỏi khác nhau, và
     * {@code /uploads/} là chỗ chúng tách ra: file người dùng tải lên MANG dữ
     * liệu nghiệp vụ nên vẫn phải qua phép kiểm tài khoản bị khoá, nhưng nó trả
     * về một tấm ảnh chứ không phải một trang HTML -- không có topbar nào để
     * bơm dữ liệu vào.
     *
     * <p>Đáng tách vì chi phí thật: mỗi tấm ảnh là 2 lượt query thừa, nên một
     * trang danh sách sản phẩm 20 ảnh tốn 40 lượt đi CSDL cho một cái chuông
     * không hề được vẽ.
     */
    private static boolean rendersTopbar(String servletPath) {
        return !isStaticAssetPath(servletPath) && !servletPath.startsWith(UPLOAD_PATH_PREFIX);
    }
}
