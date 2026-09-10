package poscs.common;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.NotificationDAO;
import poscs.model.Role;
import poscs.model.User;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cho AuthenticationFilter -- lớp chặn duy nhất đứng trước MỌI request
 * (urlPatterns "/*"), nên đây là nơi rủi ro cao nhất nếu có bug: sai 1 chỗ ở
 * đây là hở toàn bộ app hoặc khoá nhầm toàn bộ app. Trọng tâm:
 * <ul>
 *   <li>allow-list PUBLIC_PATHS: chỉ đúng các path đó được qua khi CHƯA đăng
 *       nhập, mọi path khác (kể cả path lạ/mới) mặc định bị chặn.</li>
 *   <li>CSRF được enforce cho MỌI request POST, kể cả các path công khai
 *       (login/quên mật khẩu) -- không được chỉ áp dụng cho path cần đăng
 *       nhập.</li>
 *   <li>bấm Back về login.jsp khi vẫn còn session hợp lệ phải huỷ session đó.</li>
 * </ul>
 * Dùng session giả lưu attribute thật bằng HashMap (thay vì mock trả cố
 * định) để CsrfUtil.getOrCreateToken/isValid chạy đúng logic thật, không
 * phải mock lại toàn bộ CsrfUtil. JUnit 4 -- xem CustomerControllerTest.
 */
public class AuthenticationFilterTest {

    private AuthenticationFilter filter;
    private NotificationDAO notificationDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @Before
    public void setUp() throws Exception {
        filter = new AuthenticationFilter();
        notificationDAO = mock(NotificationDAO.class);
        setField(filter, "notificationDAO", notificationDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        when(request.getContextPath()).thenReturn("/POSCS");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** Session giả lưu attribute thật bằng HashMap, để CsrfUtil hoạt động đúng logic thật. */
    private HttpSession fakeSession(Map<String, Object> backing) {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(anyString())).thenAnswer(inv -> backing.get(inv.getArgument(0, String.class)));
        doAnswer(inv -> {
            backing.put(inv.getArgument(0, String.class), inv.getArgument(1));
            return null;
        }).when(session).setAttribute(anyString(), any());
        return session;
    }

    private User loggedInUser() {
        User u = new User();
        u.setUserId(7);
        u.setRole(new Role(2, "Sales"));
        return u;
    }

    // ------------------------------------------------------------------
    // Allow-list PUBLIC_PATHS
    // ------------------------------------------------------------------

    @Test
    public void get_publicPath_notLoggedIn_isAllowedThrough() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/forgotPassword.jsp");
        Map<String, Object> attrs = new HashMap<>();
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);
        when(request.getSession(false)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    public void get_unknownNonPublicPath_notLoggedIn_isBlockedAndRedirectedToLogin() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        // Path chưa từng được thêm vào PUBLIC_PATHS -- vd 1 route mới quên
        // thêm vào allow-list vẫn phải mặc định BỊ CHẶN, đúng thiết kế allow-list.
        when(request.getServletPath()).thenReturn("/some/brand-new-route");
        Map<String, Object> attrs = new HashMap<>();
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);
        when(request.getSession(false)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(response).sendRedirect("/POSCS/login.jsp");
    }

    @Test
    public void get_brandingImage_notLoggedIn_isAllowedThrough() throws Exception {
        // login.jsp nhúng logo/ảnh nền khi chưa đăng nhập: nếu filter chặn thì
        // trình duyệt nhận 302 về login.jsp thay vì file ảnh, logo mất tiêu.
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/img/postef-logo.png");
        Map<String, Object> attrs = new HashMap<>();
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);
        when(request.getSession(false)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    public void get_uploadedFile_notLoggedIn_isStillBlocked() throws Exception {
        // Mở "/img/" KHÔNG được kéo theo file người dùng tải lên ("/uploads/*"):
        // ảnh đại diện, đính kèm hợp đồng vẫn phải đăng nhập mới xem được.
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/uploads/avatars/a1b2.png");
        Map<String, Object> attrs = new HashMap<>();
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);
        when(request.getSession(false)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(response).sendRedirect("/POSCS/login.jsp");
    }

    @Test
    public void get_nonPublicPath_loggedIn_isAllowedThroughAndInjectsNotificationData() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/customer");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("currentUser", loggedInUser());
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);
        when(notificationDAO.countUnread(7)).thenReturn(3);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(request).setAttribute("unreadNotifCount", 3);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    public void get_staticAssetPath_loggedIn_skipsNotificationQuery() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/css/app.css");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("currentUser", loggedInUser());
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(notificationDAO, never()).countUnread(anyInt());
    }

    @Test
    public void get_loginJspWithStillValidSession_invalidatesThatSession() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/login.jsp");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("currentUser", loggedInUser());
        HttpSession existingSession = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(existingSession);
        // Sau invalidate(), filter vẫn gọi getSession(true) để lấy/tạo session cho CSRF token
        // -- giả lập bằng 1 session mới (đại diện session được tạo lại sau invalidate).
        HttpSession newSession = fakeSession(new HashMap<>());
        when(request.getSession(true)).thenReturn(newSession);

        filter.doFilter(request, response, chain);

        verify(existingSession).invalidate();
        verify(chain).doFilter(request, response); // login.jsp vẫn là public path, cho qua bình thường
    }

    // ------------------------------------------------------------------
    // CSRF -- áp dụng cho MỌI POST, kể cả path công khai
    // ------------------------------------------------------------------

    @Test
    public void post_missingCsrfToken_isRejectedWith403EvenOnPublicPath() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn("/login"); // public path
        Map<String, Object> attrs = new HashMap<>();
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);
        when(request.getParameter("csrfToken")).thenReturn(null); // không gửi token

        filter.doFilter(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    public void post_wrongCsrfToken_isRejectedWith403() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn("/customer");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("currentUser", loggedInUser());
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);

        // Lần gọi doFilter đầu tiên để filter tự sinh + lưu token thật vào session
        // (getOrCreateToken), sau đó gửi lên 1 token SAI khác hẳn.
        filter.doFilter(mockGetRequestForTokenSeed(session), response, mock(FilterChain.class));
        when(request.getParameter("csrfToken")).thenReturn("hoan-toan-sai-token");

        filter.doFilter(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    public void post_correctCsrfToken_isAllowedThrough() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn("/customer");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("currentUser", loggedInUser());
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);

        // Sinh token thật vào session trước (mô phỏng lượt GET trước đó render form).
        String realToken = poscs.common.CsrfUtil.getOrCreateToken(session);
        when(request.getParameter("csrfToken")).thenReturn(realToken);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    private HttpServletRequest mockGetRequestForTokenSeed(HttpSession session) {
        HttpServletRequest seedRequest = mock(HttpServletRequest.class);
        when(seedRequest.getContextPath()).thenReturn("/POSCS");
        when(seedRequest.getMethod()).thenReturn("GET");
        when(seedRequest.getServletPath()).thenReturn("/customer");
        when(seedRequest.getSession(true)).thenReturn(session);
        return seedRequest;
    }

    // ------------------------------------------------------------------
    // Header chống cache -- luôn set cho mọi response đi qua filter
    // ------------------------------------------------------------------

    @Test
    public void everyResponse_getsNoCacheHeaders() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getServletPath()).thenReturn("/login.jsp");
        Map<String, Object> attrs = new HashMap<>();
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);
        when(request.getSession(false)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        verify(response).setHeader("Pragma", "no-cache");
        verify(response).setDateHeader("Expires", 0);
    }
}
