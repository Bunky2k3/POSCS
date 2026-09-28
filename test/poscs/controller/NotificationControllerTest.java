package poscs.controller;

import java.lang.reflect.Field;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.NotificationDAO;
import poscs.model.Notification;
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

    // ------------------------------------------------------------------
    // action=open -- bấm vào thông báo: đánh dấu đã đọc, mở thẳng thứ nó nói tới
    // ------------------------------------------------------------------

    private static Notification notification(int id, String refType, Integer refId) {
        Notification n = new Notification();
        n.setNotificationId(id);
        n.setUserId(7);
        n.setRefType(refType);
        n.setRefId(refId);
        return n;
    }

    @Test
    public void actionOpen_contractExpiring_marksReadAndOpensTheContract() throws Exception {
        when(request.getParameter("action")).thenReturn("open");
        when(request.getParameter("id")).thenReturn("42");
        when(notificationDAO.findByIdForUser(42, 7)).thenReturn(notification(42, "contract_expiring", 15));

        controller.doGet(request, response);

        verify(notificationDAO).markAsRead(42, 7);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=15");
    }

    @Test
    public void actionOpen_ticketSla_opensTheTicket() throws Exception {
        when(request.getParameter("action")).thenReturn("open");
        when(request.getParameter("id")).thenReturn("43");
        when(notificationDAO.findByIdForUser(43, 7)).thenReturn(notification(43, "ticket_sla", 9));

        controller.doGet(request, response);

        verify(notificationDAO).markAsRead(43, 7);
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=view&id=9");
    }

    @Test
    public void actionOpen_changeRequest_opensTheRequest() throws Exception {
        when(request.getParameter("action")).thenReturn("open");
        when(request.getParameter("id")).thenReturn("44");
        when(notificationDAO.findByIdForUser(44, 7)).thenReturn(notification(44, "CHANGE_REQUEST", 3));

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/changerequest?action=view&id=3");
    }

    /** Loại lạ hoặc thiếu id: vẫn đánh dấu đã đọc, quay về trang Thông báo như trước. */
    @Test
    public void actionOpen_unknownRefType_marksReadAndFallsBackToNotificationsPage() throws Exception {
        when(request.getParameter("action")).thenReturn("open");
        when(request.getParameter("id")).thenReturn("45");
        when(notificationDAO.findByIdForUser(45, 7)).thenReturn(notification(45, "something_new", null));

        controller.doGet(request, response);

        verify(notificationDAO).markAsRead(45, 7);
        verify(response).sendRedirect(CONTEXT_PATH + "/notifications");
    }

    /** Id của người khác (hoặc không có): không đánh dấu gì, không lộ ra nó nói về hợp đồng nào. */
    @Test
    public void actionOpen_notificationOfSomeoneElse_marksNothingAndStaysOnNotificationsPage() throws Exception {
        when(request.getParameter("action")).thenReturn("open");
        when(request.getParameter("id")).thenReturn("99");
        when(notificationDAO.findByIdForUser(99, 7)).thenReturn(null);

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
