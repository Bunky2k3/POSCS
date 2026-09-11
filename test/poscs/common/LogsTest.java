package poscs.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.Test;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Test cho Logs.actor -- chuỗi định danh người thao tác, được nhúng vào mọi
 * dòng log ở nhánh thất bại của controller.
 *
 * Nhỏ nhưng đáng test: hàm này chạy ĐÚNG lúc có sự cố, nên nếu nó ném
 * NullPointerException khi phiên đã hết hạn thì nó che mất chính cái lỗi mà
 * nó sinh ra để ghi lại.
 */
public class LogsTest {

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpSession session = mock(HttpSession.class);

    @Test
    public void actor_loggedInUser_returnsUsernameAndId() {
        User user = new User();
        user.setUserId(15);
        user.setUsername("sales1");
        user.setRole(new Role(2, "Sales"));
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("currentUser")).thenReturn(user);

        assertEquals("sales1#15", Logs.actor(request));
    }

    @Test
    public void actor_noSession_returnsPlaceholderInsteadOfThrowing() {
        when(request.getSession(false)).thenReturn(null);

        assertEquals("?", Logs.actor(request));
    }

    @Test
    public void actor_sessionWithoutUser_returnsPlaceholder() {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("currentUser")).thenReturn(null);

        assertEquals("?", Logs.actor(request));
    }
}
