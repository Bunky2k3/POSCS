package poscs.controller;

import java.lang.reflect.Field;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.NotificationDAO;
import poscs.model.User;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Test cho NotificationController -- trang "Xem tất cả" + đánh dấu đã đọc. */
public class NotificationControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private NotificationController controller;
    private NotificationDAO notificationDAO;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @Before
    public void setUp() throws Exception {
        controller = new NotificationController();
        notificationDAO = mock(NotificationDAO.class);
        setField(controller, "notificationDAO", notificationDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);

        session = mock(HttpSession.class);
        User user = new User();
        user.setUserId(7);
        when(session.getAttribute("currentUser")).thenReturn(user);
        when(request.getSession(false)).thenReturn(session);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void notLoggedIn_redirectsToLoginPage() throws Exception {
        when(request.getSession(false)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/login.jsp");
        verify(notificationDAO, never()).findAllByUser(anyInt());
    }

    @Test
    public void actionRead_withValidId_marksAsReadForCurrentUserAndRedirects() throws Exception {
        when(request.getParameter("action")).thenReturn("read");
        when(request.getParameter("id")).thenReturn("42");

        controller.doGet(request, response);

        verify(notificationDAO).markAsRead(42, 7);
        verify(response).sendRedirect(CONTEXT_PATH + "/notifications");
    }

    @Test
    public void actionRead_withoutId_doesNotCallDaoButStillRedirects() throws Exception {
        when(request.getParameter("action")).thenReturn("read");
        when(request.getParameter("id")).thenReturn(null);

        controller.doGet(request, response);

        verify(notificationDAO, never()).markAsRead(anyInt(), anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/notifications");
    }

    @Test
    public void actionReadAll_marksAllAsReadForCurrentUserAndRedirects() throws Exception {
        when(request.getParameter("action")).thenReturn("readAll");

        controller.doGet(request, response);

        verify(notificationDAO).markAllAsRead(7);
        verify(response).sendRedirect(CONTEXT_PATH + "/notifications");
    }

    @Test
    public void noAction_forwardsToNotificationsPageWithList() throws Exception {
        when(request.getParameter("action")).thenReturn(null);
        when(notificationDAO.findAllByUser(7)).thenReturn(java.util.Collections.emptyList());
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/notifications.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(request).setAttribute("notifications", java.util.Collections.emptyList());
        verify(dispatcher).forward(request, response);
    }
}
