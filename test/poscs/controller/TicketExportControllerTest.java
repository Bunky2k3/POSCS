package poscs.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import poscs.common.Period;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Role;
import poscs.model.TechnicalRequest;
import poscs.model.User;
import poscs.service.PermissionService;
import poscs.service.TicketExportService;
import poscs.service.TicketService;

public class TicketExportControllerTest {

    private TechnicalSupportTicketDAO ticketDAO;
    private EmployeeDAO employeeDAO;
    private TicketExportService exportService;
    private TicketService ticketService;
    private PermissionService permissionService;

    private TicketExportController controller;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private ServletContext servletContext;
    private ByteArrayOutputStream outputStream;

    @Before
    public void setUp() throws Exception {
        ticketDAO = mock(TechnicalSupportTicketDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        exportService = mock(TicketExportService.class);
        ticketService = mock(TicketService.class);
        permissionService = mock(PermissionService.class);

        controller = new TicketExportController(ticketDAO, employeeDAO, exportService, ticketService, permissionService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        servletContext = mock(ServletContext.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn("/poscs");

        controller.setServletContext(servletContext);

        outputStream = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                outputStream.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        });

        when(exportService.getExcelFileNamePrefix()).thenReturn("phieu_ho_tro");
    }

    private User loginAs(String roleName, int userId) {
        User user = new User();
        user.setUserId(userId);
        user.setRole(new Role(1, roleName));
        when(session.getAttribute("currentUser")).thenReturn(user);
        return user;
    }

    @Test
    public void doGet_unauthorizedUser_returns403() throws Exception {
        User user = loginAs("KhachHang", 99);
        when(permissionService.canExportTickets(user)).thenReturn(false);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(response, never()).getOutputStream();
    }

    @Test
    public void doGet_nullUser_returns403() throws Exception {
        when(session.getAttribute("currentUser")).thenReturn(null);
        when(permissionService.canExportTickets(null)).thenReturn(false);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void doGet_exportExcel_adminAuthorized_writesHeadersAndExports() throws Exception {
        User user = loginAs("Admin", 1);
        when(permissionService.canExportTickets(user)).thenReturn(true);

        when(request.getParameter("action")).thenReturn("exportExcel");
        when(request.getParameter("keyword")).thenReturn("server");
        when(request.getParameter("status")).thenReturn("Đang xử lý");
        when(request.getParameter("priority")).thenReturn("Cao");
        when(request.getParameter("year")).thenReturn("2026");
        when(request.getParameter("period")).thenReturn("8");
        when(request.getParameter("assignee")).thenReturn("all");

        when(ticketService.resolveAssigneeFilter("all", user)).thenReturn("all");
        when(ticketService.resolveAssigneeId("all", user)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).setContentType("application/vnd.ms-excel");
        verify(response).setHeader(eq("Content-Disposition"), contains("phieu_ho_tro"));
        verify(exportService).exportTicketsToExcel(any(OutputStream.class), eq("server"),
                eq("Đang xử lý"), eq("Cao"), any(Period.class), isNull());
    }

    @Test
    public void doGet_exportExcel_technicianDefaultAssignee_resolvesToOwnTickets() throws Exception {
        User user = loginAs("Kỹ thuật", 50);
        when(permissionService.canExportTickets(user)).thenReturn(true);

        when(request.getParameter("action")).thenReturn("exportExcel");
        when(request.getParameter("assignee")).thenReturn(null);

        when(ticketService.resolveAssigneeFilter(null, user)).thenReturn("mine");
        when(ticketService.resolveAssigneeId("mine", user)).thenReturn(50);

        controller.doGet(request, response);

        verify(exportService).exportTicketsToExcel(any(OutputStream.class), any(),
                any(), any(), any(), eq(50));
    }

    @Test
    public void doGet_defaultAction_executesExportExcel() throws Exception {
        User user = loginAs("Sales", 20);
        when(permissionService.canExportTickets(user)).thenReturn(true);

        when(request.getParameter("action")).thenReturn(null);
        when(request.getParameter("type")).thenReturn(null);

        controller.doGet(request, response);

        verify(response).setContentType("application/vnd.ms-excel");
    }

    @Test
    public void doGet_exportPdf_ticketFound_setsPdfContentTypeAndStreams() throws Exception {
        User user = loginAs("Kỹ thuật", 50);
        when(permissionService.canExportTickets(user)).thenReturn(true);

        when(request.getParameter("action")).thenReturn("exportPdf");
        when(request.getParameter("id")).thenReturn("12");

        TechnicalRequest ticket = new TechnicalRequest();
        ticket.setTicketId(12);
        ticket.setTicketCode("TK-2026-012");
        when(ticketDAO.findById(12)).thenReturn(ticket);
        when(exportService.getPdfFileName(ticket)).thenReturn("phieu_TK-2026-012.pdf");

        controller.doGet(request, response);

        verify(response).setContentType("application/pdf");
        verify(response).setHeader(eq("Content-Disposition"), contains("phieu_TK-2026-012.pdf"));
        verify(exportService).exportTicketToPdf(any(OutputStream.class), eq(ticket), eq(servletContext));
    }

    @Test
    public void doGet_typePdf_callsExportPdf() throws Exception {
        User user = loginAs("Admin", 1);
        when(permissionService.canExportTickets(user)).thenReturn(true);

        when(request.getParameter("type")).thenReturn("pdf");
        when(request.getParameter("id")).thenReturn("12");

        TechnicalRequest ticket = new TechnicalRequest();
        ticket.setTicketId(12);
        when(ticketDAO.findById(12)).thenReturn(ticket);
        when(exportService.getPdfFileName(ticket)).thenReturn("phieu_12.pdf");

        controller.doGet(request, response);

        verify(response).setContentType("application/pdf");
    }

    @Test
    public void doGet_exportPdf_ticketNotFound_redirectsToNotFoundError() throws Exception {
        User user = loginAs("Admin", 1);
        when(permissionService.canExportTickets(user)).thenReturn(true);

        when(request.getParameter("action")).thenReturn("exportPdf");
        when(request.getParameter("id")).thenReturn("999");
        when(ticketDAO.findById(999)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect("/poscs/ticket?error=notfound");
        verify(exportService, never()).exportTicketToPdf(any(), any(), any());
    }

    @Test
    public void doGet_exportPdf_invalidOrMissingId_redirectsToNotFoundError() throws Exception {
        User user = loginAs("Admin", 1);
        when(permissionService.canExportTickets(user)).thenReturn(true);

        when(request.getParameter("action")).thenReturn("exportPdf");
        when(request.getParameter("id")).thenReturn("not-a-number");

        controller.doGet(request, response);

        verify(response).sendRedirect("/poscs/ticket?error=notfound");
    }

    @Test
    public void gettersAndSetters_workCorrectly() {
        TicketExportController c = new TicketExportController();
        assertNotNull(c.getTicketDAO());
        assertNotNull(c.getEmployeeDAO());
        assertNotNull(c.getExportService());
        assertNotNull(c.getTicketService());
        assertNotNull(c.getPermissionService());

        TechnicalSupportTicketDAO mockDao = mock(TechnicalSupportTicketDAO.class);
        c.setTicketDAO(mockDao);
        assertSame(mockDao, c.getTicketDAO());

        EmployeeDAO mockEmp = mock(EmployeeDAO.class);
        c.setEmployeeDAO(mockEmp);
        assertSame(mockEmp, c.getEmployeeDAO());

        TicketExportService mockSvc = mock(TicketExportService.class);
        c.setExportService(mockSvc);
        assertSame(mockSvc, c.getExportService());

        TicketService mockTs = mock(TicketService.class);
        c.setTicketService(mockTs);
        assertSame(mockTs, c.getTicketService());

        PermissionService mockPs = mock(PermissionService.class);
        c.setPermissionService(mockPs);
        assertSame(mockPs, c.getPermissionService());

        ServletContext mockSc = mock(ServletContext.class);
        c.setServletContext(mockSc);
        assertSame(mockSc, c.getCustomServletContext());
        assertSame(mockSc, c.resolveServletContext(request));
    }
}
