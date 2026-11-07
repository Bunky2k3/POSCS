package poscs.controller;

import java.util.List;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Role;
import poscs.model.TechnicalRequest;
import poscs.model.TechnicalRequestHistory;
import poscs.model.User;
import poscs.service.PermissionService;
import poscs.service.TicketService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho {@link TicketDetailController} (UC-17, BR-46, BR-47).
 */
public class TicketDetailControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private TicketDetailController controller;
    private TechnicalSupportTicketDAO ticketDAO;
    private CustomerDAO customerDAO;
    private ContractDAO contractDAO;
    private EmployeeDAO employeeDAO;
    private TicketService ticketService;
    private PermissionService permissionService;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    @Before
    public void setUp() {
        ticketDAO = mock(TechnicalSupportTicketDAO.class);
        customerDAO = mock(CustomerDAO.class);
        contractDAO = mock(ContractDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        permissionService = new PermissionService(employeeDAO);
        ticketService = new TicketService(ticketDAO, customerDAO, contractDAO, employeeDAO, permissionService, null);

        controller = new TicketDetailController(ticketDAO, customerDAO, contractDAO, employeeDAO,
                ticketService, permissionService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        when(request.getRequestDispatcher(TicketDetailController.DETAIL_VIEW)).thenReturn(dispatcher);

        loginAs(PermissionService.ROLE_SALES, 99);
    }

    private User loginAs(String roleName, int userId) {
        User user = new User();
        user.setUserId(userId);
        user.setFirstName("Van A");
        user.setLastName("Nguyen");
        user.setRole(new Role(1, roleName));
        when(session.getAttribute("currentUser")).thenReturn(user);
        when(employeeDAO.findById(userId)).thenReturn(user);
        return user;
    }

    private TechnicalRequest sampleTicket(int id, String status, int assignedTechId) {
        TechnicalRequest t = new TechnicalRequest();
        t.setTicketId(id);
        t.setTicketCode("TK-000" + id);
        t.setEnterpriseId(10);
        t.setTicketType("Bảo hành");
        t.setPriority("Cao");
        t.setReceptionChannel("Điện thoại");
        t.setAssignedTechnicianId(assignedTechId);
        t.setDescription("Thiết bị lỗi nguồn");
        t.setStatus(status);
        return t;
    }

    // ------------------------------------------------------------------
    // Phân quyền RBAC (UC-17, MSG-022)
    // ------------------------------------------------------------------

    @Test
    public void detail_unauthenticatedUser_returns403Msg022() throws Exception {
        when(session.getAttribute("currentUser")).thenReturn(null);
        when(request.getParameter("id")).thenReturn("3");

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(ticketDAO, never()).findById(anyInt());
        verify(dispatcher, never()).forward(request, response);
    }

    @Test
    public void detail_deletedUserInDb_returns403Msg022() throws Exception {
        User deleted = loginAs(PermissionService.ROLE_SALES, 99);
        deleted.setDeleted(true);
        when(request.getParameter("id")).thenReturn("3");

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(ticketDAO, never()).findById(anyInt());
    }

    @Test
    public void detail_unknownRole_returns403Msg022() throws Exception {
        loginAs("Guest", 99);
        when(request.getParameter("id")).thenReturn("3");

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(ticketDAO, never()).findById(anyInt());
    }

    // ------------------------------------------------------------------
    // Kiểm tra tham số ID & Không tìm thấy bản ghi (UC-17)
    // ------------------------------------------------------------------

    @Test
    public void detail_missingZeroNegativeOrGarbageId_redirectsToNotFound() throws Exception {
        for (String badId : new String[]{null, "", "   ", "0", "-7", "abc"}) {
            reset(response);
            when(request.getParameter("id")).thenReturn(badId);

            controller.doGet(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/ticket?error=notfound");
        }
        verify(ticketDAO, never()).findById(anyInt());
    }

    @Test
    public void detail_ticketNotFoundInDao_redirectsToNotFound() throws Exception {
        when(request.getParameter("id")).thenReturn("123");
        when(ticketDAO.findById(123)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?error=notfound");
        verify(dispatcher, never()).forward(request, response);
    }

    // ------------------------------------------------------------------
    // BR-46 & BR-47: Nạp chi tiết phiếu, lịch sử xử lý và cờ canEdit / canDelete
    // ------------------------------------------------------------------

    @Test
    public void detail_salesViewsNewTicket_bindsTicketHistoryCanEditTrueCanDeleteTrue_BR46_BR47() throws Exception {
        when(request.getParameter("id")).thenReturn("3");
        TechnicalRequest ticket = sampleTicket(3, TicketService.STATUS_NEW, 50);
        TechnicalRequestHistory h1 = new TechnicalRequestHistory();
        h1.setFromStatus("");
        h1.setToStatus(TicketService.STATUS_NEW);

        when(ticketDAO.findById(3)).thenReturn(ticket);
        when(ticketDAO.findHistoryByTicketId(3)).thenReturn(List.of(h1));
        when(ticketDAO.canDelete(3)).thenReturn(true);

        controller.doGet(request, response);

        verify(request).setAttribute("ticket", ticket);
        verify(request).setAttribute("ticketHistory", List.of(h1));
        verify(request).setAttribute("canManage", true);
        verify(request).setAttribute("canEdit", true);
        verify(request).setAttribute("canDelete", true);
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void detail_ticketInProgress_canDeleteIsFalse_BR47() throws Exception {
        when(request.getParameter("id")).thenReturn("3");
        TechnicalRequest ticket = sampleTicket(3, TicketService.STATUS_IN_PROGRESS, 50);
        when(ticketDAO.findById(3)).thenReturn(ticket);
        when(ticketDAO.findHistoryByTicketId(3)).thenReturn(List.of());
        when(ticketDAO.canDelete(3)).thenReturn(false);

        controller.doGet(request, response);

        verify(request).setAttribute("canEdit", true);
        verify(request).setAttribute("canDelete", false);
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void detail_assignedTechnician_canEditTrueAndCanDeleteFalse_UC17() throws Exception {
        loginAs(PermissionService.ROLE_TECHNICAL, 50);
        when(request.getParameter("id")).thenReturn("3");
        TechnicalRequest ticket = sampleTicket(3, TicketService.STATUS_NEW, 50);
        when(ticketDAO.findById(3)).thenReturn(ticket);
        when(ticketDAO.canDelete(3)).thenReturn(true);

        controller.doGet(request, response);

        verify(request).setAttribute("canManage", false);
        verify(request).setAttribute("canEdit", true);
        verify(request).setAttribute("canDelete", false);
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void detail_otherTechnicianNotAssigned_canEditFalseAndCanDeleteFalse_UC17() throws Exception {
        loginAs(PermissionService.ROLE_TECHNICAL, 51);
        when(request.getParameter("id")).thenReturn("3");
        TechnicalRequest ticket = sampleTicket(3, TicketService.STATUS_NEW, 50);
        when(ticketDAO.findById(3)).thenReturn(ticket);
        when(ticketDAO.canDelete(3)).thenReturn(true);

        controller.doGet(request, response);

        verify(request).setAttribute("canManage", false);
        verify(request).setAttribute("canEdit", false);
        verify(request).setAttribute("canDelete", false);
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void detail_gettersSettersAndDefaultConstructor() {
        TicketDetailController c = new TicketDetailController();
        assertNotNull(c.getTicketDAO());
        assertNotNull(c.getCustomerDAO());
        assertNotNull(c.getContractDAO());
        assertNotNull(c.getEmployeeDAO());
        assertNotNull(c.getPermissionService());
        assertNotNull(c.getTicketService());

        c.setTicketDAO(ticketDAO);
        c.setCustomerDAO(customerDAO);
        c.setContractDAO(contractDAO);
        c.setEmployeeDAO(employeeDAO);
        c.setPermissionService(permissionService);
        c.setTicketService(ticketService);

        assertSame(ticketDAO, c.getTicketDAO());
        assertSame(customerDAO, c.getCustomerDAO());
        assertSame(contractDAO, c.getContractDAO());
        assertSame(employeeDAO, c.getEmployeeDAO());
        assertSame(permissionService, c.getPermissionService());
        assertSame(ticketService, c.getTicketService());
    }
}
