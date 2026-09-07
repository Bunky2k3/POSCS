package poscs.controller;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Role;
import poscs.model.TechnicalRequest;
import poscs.model.User;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test tầng Controller cho TechnicalSupportTicketController -- tập trung
 * vào phân quyền TICKET (Full access CSKH/Admin, cộng ngoại lệ Kỹ thuật
 * được tự cập nhật đúng phiếu giao cho mình -- AccessControl.
 * canUpdateAssignedTicket) và quy tắc stamp resolved_at chỉ 1 lần khi
 * chuyển sang "Đã đóng". JUnit 4 + Mockito, xem CustomerControllerTest.
 */
public class TechnicalSupportTicketControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private TechnicalSupportTicketController controller;
    private TechnicalSupportTicketDAO ticketDAO;
    private CustomerDAO customerDAO;
    private EmployeeDAO employeeDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @Before
    public void setUp() throws Exception {
        controller = new TechnicalSupportTicketController();

        ticketDAO = mock(TechnicalSupportTicketDAO.class);
        customerDAO = mock(CustomerDAO.class);
        employeeDAO = mock(EmployeeDAO.class);

        setField(controller, "ticketDAO", ticketDAO);
        setField(controller, "customerDAO", customerDAO);
        setField(controller, "employeeDAO", employeeDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);

        loginAs("CSKH", 99); // role được Full access trên TICKET, xem PERMISSIONS.md
    }

    private void loginAs(String roleName, int userId) {
        User user = new User();
        user.setUserId(userId);
        user.setRole(new Role(1, roleName));
        when(session.getAttribute("currentUser")).thenReturn(user);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** Phiếu hợp lệ đầy đủ trường bắt buộc (isValid), phục vụ các test cập nhật. */
    private TechnicalRequest fullyValidExistingTicket() {
        TechnicalRequest t = new TechnicalRequest();
        t.setTicketId(3);
        t.setEnterpriseId(10);
        t.setTicketType("Bảo hành");
        t.setPriority("Cao");
        t.setReceptionChannel("Điện thoại");
        t.setAssignedTechnicianId(50);
        t.setDescription("Thiết bị lỗi nguồn");
        t.setStatus("Đang xử lý");
        return t;
    }

    // ------------------------------------------------------------------
    // GET ?action=view
    // ------------------------------------------------------------------

    @Test
    public void view_ticketNotFound_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("123");
        when(ticketDAO.findById(123)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?error=notfound");
    }

    @Test
    public void view_ticketFound_forwardsToDetailView() throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("3");
        TechnicalRequest ticket = fullyValidExistingTicket();
        when(ticketDAO.findById(3)).thenReturn(ticket);
        when(ticketDAO.canDelete(3)).thenReturn(true);

        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/customersupport/viewdetailTicket.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(request).setAttribute("ticket", ticket);
        verify(dispatcher).forward(request, response);
    }

    // ------------------------------------------------------------------
    // POST ?action=create
    // ------------------------------------------------------------------

    private void stubValidCreateFields() {
        when(request.getParameter("enterpriseId")).thenReturn("10");
        when(request.getParameter("ticketType")).thenReturn("Bảo hành");
        when(request.getParameter("priority")).thenReturn("Cao");
        when(request.getParameter("receptionChannel")).thenReturn("Điện thoại");
        when(request.getParameter("assignedTechnicianId")).thenReturn("50");
        when(request.getParameter("description")).thenReturn("Thiết bị lỗi nguồn");
    }

    @Test
    public void create_withoutFullAccess_returns403AndNeverInserts() throws Exception {
        loginAs("Sales", 1); // không có Full access trên TICKET
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(ticketDAO, never()).insert(any());
    }

    @Test
    public void create_missingDescription_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("description")).thenReturn(null);

        controller.doPost(request, response);

        verify(ticketDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=new&error=invalid");
    }

    @Test
    public void create_validFields_insertsAndRedirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(ticketDAO.generateNextTicketCode()).thenReturn("TK-0001");
        when(ticketDAO.insert(any(TechnicalRequest.class))).thenReturn(7);

        controller.doPost(request, response);

        verify(ticketDAO).insert(argThat((TechnicalRequest t) ->
                TechnicalSupportTicketDAO.STATUS_NEW.equals(t.getStatus()) && t.getCreatedBy() == 99));
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=view&id=7");
    }

    // ------------------------------------------------------------------
    // POST ?action=update -- phân quyền + BR stamp resolved_at
    // ------------------------------------------------------------------

    @Test
    public void update_ticketIdMissing_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("ticketId")).thenReturn(null);

        controller.doPost(request, response);

        verify(ticketDAO, never()).update(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?error=notfound");
    }

    @Test
    public void update_technicianNotAssignedToTicket_returns403() throws Exception {
        loginAs("Kỹ thuật", 1); // không phải Full access, và không phải người được giao phiếu này (assignedTechnicianId=50)
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("ticketId")).thenReturn("3");
        when(ticketDAO.findById(3)).thenReturn(fullyValidExistingTicket());

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(ticketDAO, never()).update(any());
    }

    @Test
    public void update_assignedTechnician_canCloseOwnTicketAndStampsResolvedAt() throws Exception {
        loginAs("Kỹ thuật", 50); // đúng người được giao phiếu (assignedTechnicianId=50)
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("ticketId")).thenReturn("3");
        when(request.getParameter("status")).thenReturn(TechnicalSupportTicketDAO.STATUS_CLOSED);
        when(request.getParameter("resolutionSummary")).thenReturn("Đã thay nguồn");
        TechnicalRequest existing = fullyValidExistingTicket(); // status ban đầu "Đang xử lý", resolvedAt=null
        when(ticketDAO.findById(3)).thenReturn(existing);
        when(ticketDAO.update(any())).thenReturn(true);

        controller.doPost(request, response);

        verify(ticketDAO).update(argThat((TechnicalRequest t) ->
                TechnicalSupportTicketDAO.STATUS_CLOSED.equals(t.getStatus()) && t.getResolvedAt() != null));
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=view&id=3");
    }

    @Test
    public void update_alreadyClosedTicket_doesNotOverwriteOriginalResolvedAt() throws Exception {
        loginAs("CSKH", 99); // Full access
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("ticketId")).thenReturn("3");
        when(request.getParameter("enterpriseId")).thenReturn("10");
        when(request.getParameter("ticketType")).thenReturn("Bảo hành");
        when(request.getParameter("priority")).thenReturn("Cao");
        when(request.getParameter("receptionChannel")).thenReturn("Điện thoại");
        when(request.getParameter("assignedTechnicianId")).thenReturn("50");
        when(request.getParameter("description")).thenReturn("Thiết bị lỗi nguồn");
        when(request.getParameter("status")).thenReturn(TechnicalSupportTicketDAO.STATUS_CLOSED);
        when(request.getParameter("resolutionSummary")).thenReturn("Sửa lại ghi chú, không đổi trạng thái");

        TechnicalRequest existing = fullyValidExistingTicket();
        existing.setStatus(TechnicalSupportTicketDAO.STATUS_CLOSED);
        Timestamp originalResolvedAt = new Timestamp(1000L);
        existing.setResolvedAt(originalResolvedAt);
        when(ticketDAO.findById(3)).thenReturn(existing);
        when(ticketDAO.update(any())).thenReturn(true);

        controller.doPost(request, response);

        verify(ticketDAO).update(argThat((TechnicalRequest t) -> t.getResolvedAt() == originalResolvedAt));
    }

    // ------------------------------------------------------------------
    // POST ?action=delete
    // ------------------------------------------------------------------

    @Test
    public void delete_withoutFullAccess_returns403() throws Exception {
        loginAs("Sales", 1);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("3");

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(ticketDAO, never()).softDelete(anyInt());
    }

    @Test
    public void delete_notDeletable_blocksDeletion() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("3");
        when(ticketDAO.canDelete(3)).thenReturn(false);

        controller.doPost(request, response);

        verify(ticketDAO, never()).softDelete(anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=view&id=3&error=cannot_delete");
    }

    @Test
    public void delete_deletable_softDeletesAndRedirectsToList() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("3");
        when(ticketDAO.canDelete(3)).thenReturn(true);

        controller.doPost(request, response);

        verify(ticketDAO).softDelete(3);
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket");
    }
}
