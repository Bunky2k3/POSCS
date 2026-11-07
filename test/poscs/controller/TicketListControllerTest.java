package poscs.controller;

import java.util.List;
import java.util.Map;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import poscs.common.Period;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Role;
import poscs.model.TechnicalRequest;
import poscs.model.User;
import poscs.service.PermissionService;
import poscs.service.TicketService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho {@link TicketListController} (UC-16, BR-12, BR-14, BR-45).
 */
public class TicketListControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private TicketListController controller;
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

        controller = new TicketListController(ticketDAO, customerDAO, contractDAO, employeeDAO,
                ticketService, permissionService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        when(request.getRequestDispatcher(TicketListController.LIST_VIEW)).thenReturn(dispatcher);

        loginAs(PermissionService.ROLE_SALES, 99, null);
    }

    private User loginAs(String roleName, int userId, Integer managerId) {
        User user = new User();
        user.setUserId(userId);
        user.setFirstName("Van A");
        user.setLastName("Nguyen");
        user.setRole(new Role(1, roleName));
        user.setManagerId(managerId);
        when(session.getAttribute("currentUser")).thenReturn(user);
        when(employeeDAO.findById(userId)).thenReturn(user);
        return user;
    }

    // ------------------------------------------------------------------
    // Phân quyền RBAC (UC-16, MSG-022)
    // ------------------------------------------------------------------

    @Test
    public void list_unauthenticatedUser_returns403Msg022() throws Exception {
        when(session.getAttribute("currentUser")).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(ticketDAO, never()).findAll(anyInt(), anyInt(), any(), any(), any(), any(), any());
        verify(dispatcher, never()).forward(request, response);
    }

    @Test
    public void list_deletedUserInDb_returns403Msg022() throws Exception {
        User deleted = loginAs(PermissionService.ROLE_SALES, 99, null);
        deleted.setDeleted(true);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(ticketDAO, never()).findAll(anyInt(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    public void list_unknownRole_returns403Msg022() throws Exception {
        loginAs("Guest", 99, null);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(ticketDAO, never()).findAll(anyInt(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    public void list_adminSalesAndTechnicianRoles_areAllowedAndCanManageFlagMatchesRole() throws Exception {
        // Admin -> canManage = true
        loginAs(PermissionService.ROLE_ADMIN, 1, null);
        controller.doGet(request, response);
        verify(request).setAttribute("canManage", true);

        // Sales có cấp trên -> vẫn được Tạo/Xoá phiếu hỗ trợ (UC-21, UC-23) -> canManage = true
        reset(request);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        when(request.getRequestDispatcher(TicketListController.LIST_VIEW)).thenReturn(dispatcher);
        loginAs(PermissionService.ROLE_SALES, 20, 5);
        controller.doGet(request, response);
        verify(request).setAttribute("canManage", true);

        // Kỹ thuật -> chỉ xem / sửa phiếu được giao -> canManage = false
        reset(request);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        when(request.getRequestDispatcher(TicketListController.LIST_VIEW)).thenReturn(dispatcher);
        loginAs(PermissionService.ROLE_TECHNICAL, 50, null);
        controller.doGet(request, response);
        verify(request).setAttribute("canManage", false);
    }

    // ------------------------------------------------------------------
    // BR-12: Phân trang (kích thước mặc định 10, trang không hợp lệ chuẩn hóa về 1)
    // ------------------------------------------------------------------

    @Test
    public void list_defaultPagination_usesPageOneAndPageSizeTen_BR12() throws Exception {
        TechnicalRequest t1 = new TechnicalRequest();
        t1.setTicketId(1);
        when(ticketDAO.findAll(eq(1), eq(10), any(), any(), any(), any(), any()))
                .thenReturn(List.of(t1));
        when(ticketDAO.countAll(any(), any(), any(), any(), any())).thenReturn(25);

        controller.doGet(request, response);

        verify(request).setAttribute("currentPage", 1);
        verify(request).setAttribute("pageSize", 10);
        verify(request).setAttribute("totalCount", 25);
        verify(request).setAttribute("totalPages", 3);
        verify(request).setAttribute("ticketList", List.of(t1));
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void list_negativeZeroOrGarbagePage_normalizesToPageOne_BR12() throws Exception {
        for (String badPage : new String[]{"0", "-3", "abc", "   "}) {
            reset(request, ticketDAO);
            when(request.getSession(false)).thenReturn(session);
            when(request.getContextPath()).thenReturn(CONTEXT_PATH);
            when(request.getRequestDispatcher(TicketListController.LIST_VIEW)).thenReturn(dispatcher);
            when(request.getParameter("page")).thenReturn(badPage);

            controller.doGet(request, response);

            verify(ticketDAO).findAll(eq(1), eq(10), any(), any(), any(), any(), any());
            verify(request).setAttribute("currentPage", 1);
        }
    }

    // ------------------------------------------------------------------
    // BR-14: Danh sách trống hiển thị trạng thái trống
    // ------------------------------------------------------------------

    @Test
    public void list_emptyResults_bindsEmptyListAndTotalPagesOne_BR14() throws Exception {
        when(ticketDAO.findAll(anyInt(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(ticketDAO.countAll(any(), any(), any(), any(), any())).thenReturn(0);

        controller.doGet(request, response);

        verify(request).setAttribute("ticketList", List.of());
        verify(request).setAttribute("totalCount", 0);
        verify(request).setAttribute("totalPages", 1);
        verify(dispatcher).forward(request, response);
    }

    // ------------------------------------------------------------------
    // BR-45 & UC-16: Dải KPI, bộ lọc và mặc định "Phiếu của tôi" cho Kỹ thuật
    // ------------------------------------------------------------------

    @Test
    public void list_technicianWithoutAssigneeParam_defaultsToMineAndCountsOwnKpi_UC16_BR45() throws Exception {
        loginAs(PermissionService.ROLE_TECHNICAL, 50, null);
        Map<String, Integer> kpi = Map.of(
                TicketService.STATUS_NEW, 2,
                TicketService.STATUS_IN_PROGRESS, 3,
                TicketService.STATUS_CLOSED, 5,
                TicketService.PRIORITY_URGENT, 1
        );
        when(ticketDAO.countListSummary(isNull(), eq(50))).thenReturn(kpi);

        controller.doGet(request, response);

        verify(ticketDAO).findAll(eq(1), eq(10), isNull(), isNull(), isNull(), isNull(), eq(50));
        verify(ticketDAO).countAll(isNull(), isNull(), isNull(), isNull(), eq(50));
        verify(ticketDAO).countListSummary(isNull(), eq(50));
        verify(request).setAttribute("statusSummary", kpi);
        verify(request).setAttribute("assigneeFilter", "mine");
        verify(request).setAttribute("isTechnician", true);
        verify(employeeDAO).findActiveByRole(PermissionService.ROLE_TECHNICAL, (Integer) null);
    }

    @Test
    public void list_salesOrAdminWithoutAssigneeParam_defaultsToAll_UC16() throws Exception {
        loginAs(PermissionService.ROLE_SALES, 99, null);

        controller.doGet(request, response);

        verify(ticketDAO).findAll(eq(1), eq(10), isNull(), isNull(), isNull(), isNull(), isNull());
        verify(ticketDAO).countListSummary(isNull(), isNull());
        verify(request).setAttribute("assigneeFilter", "all");
        verify(request).setAttribute("isTechnician", false);
    }

    @Test
    public void list_technicianExplicitlyChoosesAll_seesEveryone_UC16() throws Exception {
        loginAs(PermissionService.ROLE_TECHNICAL, 50, null);
        when(request.getParameter("assignee")).thenReturn("all");

        controller.doGet(request, response);

        verify(ticketDAO).findAll(eq(1), eq(10), any(), any(), any(), any(), isNull());
        verify(ticketDAO).countListSummary(any(), isNull());
        verify(request).setAttribute("assigneeFilter", "all");
    }

    @Test
    public void list_specificTechnicianFilter_filtersByUserIdAndKeepsInDropdown_UC16() throws Exception {
        when(request.getParameter("assignee")).thenReturn("16");

        controller.doGet(request, response);

        verify(ticketDAO).findAll(eq(1), eq(10), any(), any(), any(), any(), eq(16));
        verify(ticketDAO).countListSummary(any(), eq(16));
        verify(request).setAttribute("assigneeFilter", "16");
        verify(employeeDAO).findActiveByRole(PermissionService.ROLE_TECHNICAL, 16);
    }

    @Test
    public void list_garbageOrNegativeAssigneeParam_fallsBackToAll_UC16() throws Exception {
        for (String badAssignee : new String[]{"x' OR 1=1", "-5", "0"}) {
            reset(request, ticketDAO);
            when(request.getSession(false)).thenReturn(session);
            when(request.getContextPath()).thenReturn(CONTEXT_PATH);
            when(request.getRequestDispatcher(TicketListController.LIST_VIEW)).thenReturn(dispatcher);
            when(request.getParameter("assignee")).thenReturn(badAssignee);

            controller.doGet(request, response);

            verify(ticketDAO).findAll(eq(1), eq(10), any(), any(), any(), any(), isNull());
            verify(request).setAttribute("assigneeFilter", "all");
        }
    }

    @Test
    public void list_preservesKeywordStatusPriorityPeriodAndUrlEncodesKeyword_BR45() throws Exception {
        when(request.getParameter("page")).thenReturn("2");
        when(request.getParameter("keyword")).thenReturn("BTS & Nguồn #1");
        when(request.getParameter("status")).thenReturn("Đang xử lý");
        when(request.getParameter("priority")).thenReturn("Khẩn cấp");
        when(request.getParameter("year")).thenReturn("2026");
        when(request.getParameter("period")).thenReturn("q3");
        when(request.getParameter("assignee")).thenReturn("12");
        when(ticketDAO.countAll(any(), any(), any(), any(), any())).thenReturn(14);

        controller.doGet(request, response);

        ArgumentCaptor<Period> periodCaptor = ArgumentCaptor.forClass(Period.class);
        verify(ticketDAO).findAll(eq(2), eq(10), eq("BTS & Nguồn #1"),
                eq("Đang xử lý"), eq("Khẩn cấp"), periodCaptor.capture(), eq(12));
        assertNotNull(periodCaptor.getValue());
        assertEquals(2026, periodCaptor.getValue().getYear());

        verify(ticketDAO).countListSummary(eq(periodCaptor.getValue()), eq(12));
        verify(request).setAttribute("currentPage", 2);
        verify(request).setAttribute("totalPages", 2);
        verify(request).setAttribute("keyword", "BTS & Nguồn #1");
        verify(request).setAttribute("keywordParam", "BTS+%26+Ngu%E1%BB%93n+%231");
        verify(request).setAttribute("statusFilter", "Đang xử lý");
        verify(request).setAttribute("priorityFilter", "Khẩn cấp");
        verify(request).setAttribute("yearFilter", 2026);
        verify(request).setAttribute("periodFilter", "q3");
    }

    @Test
    public void list_gettersSettersAndDefaultConstructor() {
        TicketListController c = new TicketListController();
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
