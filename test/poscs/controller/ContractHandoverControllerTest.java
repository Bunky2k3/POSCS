package poscs.controller;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.common.AccessControl;
import poscs.dao.ContractDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.ContractHandover;
import poscs.model.Department;
import poscs.model.Role;
import poscs.model.User;
import poscs.service.ContractHandoverService;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test toàn diện cho {@link ContractHandoverController} và {@link ContractHandoverHelper} (Task 4.14).
 * Đáp ứng: UC-48, UC-49, RBAC (ma trận SPEC, MSG-022), hàng đợi bàn giao.
 */
public class ContractHandoverControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private ContractHandoverController controller;
    private ContractDAO contractDAO;
    private EmployeeDAO employeeDAO;
    private PermissionService permissionService;
    private ContractHandoverService handoverService;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    @Before
    public void setUp() {
        contractDAO = mock(ContractDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        permissionService = mock(PermissionService.class);
        handoverService = mock(ContractHandoverService.class);

        controller = new ContractHandoverController(contractDAO, employeeDAO, handoverService, permissionService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        when(request.getRequestDispatcher(ContractHandoverController.HANDOVER_VIEW)).thenReturn(dispatcher);

        loginUser(10, 1, PermissionService.ROLE_ADMIN, null);
        when(permissionService.canViewContracts(any())).thenReturn(true);
        when(permissionService.canInitiateHandover(any())).thenReturn(true);
        when(permissionService.canConfirmHandover(any(), anyInt())).thenReturn(true);
    }

    private User loginUser(int userId, int deptId, String roleName, Integer managerId) {
        User u = new User();
        u.setUserId(userId);
        u.setDepartmentId(deptId);
        u.setManagerId(managerId);
        u.setDeleted(false);
        Role r = new Role();
        r.setRoleName(roleName);
        u.setRole(r);
        when(session.getAttribute("currentUser")).thenReturn(u);
        return u;
    }

    private ContractHandover sampleHandover(int handoverId, int contractId, int deptId, String deptName, int days) {
        ContractHandover h = new ContractHandover();
        h.setHandoverId(handoverId);
        h.setContractId(contractId);
        h.setDepartmentId(deptId);
        h.setDepartmentName(deptName);
        h.setHandedAt(new Timestamp(System.currentTimeMillis() - days * 86400000L));
        return h;
    }

    // ==================================================================
    // 1. GET / showHandoverQueue
    // ==================================================================

    @Test
    public void testShowHandoverQueue_forbiddenWhenCannotViewContracts() throws Exception {
        when(permissionService.canViewContracts(any())).thenReturn(false);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(dispatcher, never()).forward(any(), any());
    }

    @Test
    public void testShowHandoverQueue_defaultScope_allBranch() throws Exception {
        when(request.getParameter("action")).thenReturn("handovers");
        when(request.getParameter("view")).thenReturn("all");

        ContractHandover h1 = sampleHandover(1, 10, 5, "Kế toán", 3);
        ContractHandover h2 = sampleHandover(2, 11, 6, "Dự án", 15);
        when(handoverService.findPendingHandovers(any(), eq(false))).thenReturn(List.of(h1, h2));

        ContractHandoverService.HandoverQueueOverview overview =
                new ContractHandoverService.HandoverQueueOverview(Map.of("Kế toán", 1, "Dự án", 1), 15L, 2);
        when(handoverService.getQueueOverview(anyList())).thenReturn(overview);

        Department d1 = new Department();
        d1.setDepartmentId(5);
        d1.setDepartmentName("Kế toán");
        when(handoverService.findAllDepartments()).thenReturn(List.of(d1));

        controller.doGet(request, response);

        verify(handoverService).findPendingHandovers(any(), eq(false));
        verify(request).setAttribute(eq("viewFilter"), eq(AccessControl.VIEW_ALL));
        verify(request).setAttribute(eq("handoverCountByDepartment"), eq(overview.getCountByDepartment()));
        verify(request).setAttribute(eq("slowestHandoverDays"), eq(15L));
        verify(request).setAttribute(eq("scopeTotal"), eq(2));
        verify(request).setAttribute(eq("departmentList"), eq(List.of(d1)));
        verify(request).setAttribute(eq("pendingHandovers"), eq(List.of(h1, h2)));
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void testShowHandoverQueue_mineScope() throws Exception {
        when(request.getParameter("action")).thenReturn("handovers");
        when(request.getParameter("view")).thenReturn("mine");

        ContractHandover h1 = sampleHandover(1, 10, 5, "Kế toán", 2);
        when(handoverService.findPendingHandovers(any(), eq(true))).thenReturn(List.of(h1));
        when(handoverService.getQueueOverview(anyList())).thenReturn(
                new ContractHandoverService.HandoverQueueOverview(Map.of("Kế toán", 1), 2L, 1));
        when(handoverService.findAllDepartments()).thenReturn(Collections.emptyList());

        controller.doGet(request, response);

        verify(handoverService).findPendingHandovers(any(), eq(true));
        verify(request).setAttribute(eq("viewFilter"), eq(AccessControl.VIEW_MINE));
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void testShowHandoverQueue_withDepartmentFilter() throws Exception {
        when(request.getParameter("action")).thenReturn("handovers");
        when(request.getParameter("departmentId")).thenReturn("5");

        ContractHandover h1 = sampleHandover(1, 10, 5, "Kế toán", 2);
        ContractHandover h2 = sampleHandover(2, 11, 6, "Dự án", 5);
        when(handoverService.findPendingHandovers(any(), anyBoolean())).thenReturn(List.of(h1, h2));
        when(handoverService.getQueueOverview(anyList())).thenReturn(
                new ContractHandoverService.HandoverQueueOverview(Map.of("Kế toán", 1, "Dự án", 1), 5L, 2));
        when(handoverService.findAllDepartments()).thenReturn(Collections.emptyList());

        controller.doGet(request, response);

        verify(request).setAttribute(eq("departmentFilter"), eq(5));
        verify(request).setAttribute(eq("pendingHandovers"), eq(List.of(h1)));
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void testShowHandoverQueue_setsPermissionFlagsForEachHandover() throws Exception {
        loginUser(20, 5, PermissionService.ROLE_SALES, 1); // Sales dept 5
        when(request.getParameter("action")).thenReturn("handovers");

        ContractHandover h1 = sampleHandover(101, 10, 5, "Kế toán", 2);
        ContractHandover h2 = sampleHandover(102, 11, 6, "Dự án", 5);
        when(handoverService.findPendingHandovers(any(), anyBoolean())).thenReturn(List.of(h1, h2));
        when(handoverService.getQueueOverview(anyList())).thenReturn(
                new ContractHandoverService.HandoverQueueOverview(Map.of(), 5L, 2));
        when(handoverService.findAllDepartments()).thenReturn(Collections.emptyList());

        controller.doGet(request, response);

        verify(request).setAttribute("canCompleteHandover_101", true);
        verify(request).setAttribute("canCompleteHandover_102", false);
    }

    @Test
    public void testDoGet_unknownAction_redirectsToContract() throws Exception {
        when(request.getParameter("action")).thenReturn("unknown_action");

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
        verify(dispatcher, never()).forward(any(), any());
    }

    // ==================================================================
    // 2. POST / handleHandOver (UC-48)
    // ==================================================================

    @Test
    public void testHandOver_contractNotFoundOrNull_redirectsNotFound() throws Exception {
        when(request.getParameter("action")).thenReturn("handOver");
        when(request.getParameter("contractId")).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void testHandOver_noDepartmentSelected_redirectsWithError() throws Exception {
        when(request.getParameter("action")).thenReturn("handOver");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameterValues("departmentId")).thenReturn(new String[]{});

        controller.doPost(request, response);

        verify(handoverService, never()).initiateHandover(anyInt(), anyList(), any(), any());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=handover_no_department");
    }

    @Test
    public void testHandOver_forbidden_sendErrorForbidden() throws Exception {
        when(request.getParameter("action")).thenReturn("handOver");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameterValues("departmentId")).thenReturn(new String[]{"5"});
        when(handoverService.initiateHandover(anyInt(), anyList(), any(), any()))
                .thenReturn(ServiceResult.fail(ContractHandoverService.ERR_FORBIDDEN));

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void testHandOver_notFound_redirectsNotFound() throws Exception {
        when(request.getParameter("action")).thenReturn("handOver");
        when(request.getParameter("contractId")).thenReturn("999");
        when(request.getParameterValues("departmentId")).thenReturn(new String[]{"5"});
        when(handoverService.initiateHandover(anyInt(), anyList(), any(), any()))
                .thenReturn(ServiceResult.fail(ContractHandoverService.ERR_NOT_FOUND));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void testHandOver_alreadyPending_redirectsWithError() throws Exception {
        when(request.getParameter("action")).thenReturn("handOver");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameterValues("departmentId")).thenReturn(new String[]{"5"});
        when(handoverService.initiateHandover(anyInt(), anyList(), any(), any()))
                .thenReturn(ServiceResult.fail(ContractHandoverService.ERR_HANDOVER_PENDING));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=handover_pending");
    }

    @Test
    public void testHandOver_frozenContract_redirectsWithError() throws Exception {
        when(request.getParameter("action")).thenReturn("handOver");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameterValues("departmentId")).thenReturn(new String[]{"5"});
        when(handoverService.initiateHandover(anyInt(), anyList(), any(), any()))
                .thenReturn(ServiceResult.fail(ContractHandoverService.ERR_HANDOVER_FROZEN));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=handover_frozen");
    }

    @Test
    public void testHandOver_serviceFailure_redirectsWithError() throws Exception {
        when(request.getParameter("action")).thenReturn("handOver");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameterValues("departmentId")).thenReturn(new String[]{"5"});
        when(handoverService.initiateHandover(anyInt(), anyList(), any(), any()))
                .thenReturn(ServiceResult.fail(ContractHandoverService.ERR_HANDOVER_FAILED));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=handover_failed");
    }

    @Test
    public void testHandOver_success_redirectsToEdit_verifiesSessionActor() throws Exception {
        when(request.getParameter("action")).thenReturn("handOver");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameterValues("departmentId")).thenReturn(new String[]{"5", "6"});
        when(request.getParameter("handoverNote")).thenReturn("Lưu ý tiến độ gấp");
        when(handoverService.initiateHandover(eq(10), eq(List.of(5, 6)), eq("Lưu ý tiến độ gấp"),
                argThat(u -> u != null && u.getUserId() == 10 && u.getRole() != null && "Admin".equals(u.getRole().getRoleName()))))
                .thenReturn(ServiceResult.ok(2));

        controller.doPost(request, response);

        verify(handoverService).initiateHandover(eq(10), eq(List.of(5, 6)), eq("Lưu ý tiến độ gấp"), any());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
    }

    // ==================================================================
    // 3. POST / handleCompleteHandover (UC-49)
    // ==================================================================

    @Test
    public void testCompleteHandover_missingRequiredParams_redirectsNotFound() throws Exception {
        when(request.getParameter("action")).thenReturn("completeHandover");
        when(request.getParameter("contractId")).thenReturn(null);
        when(request.getParameter("handoverId")).thenReturn("9");
        when(request.getParameter("departmentId")).thenReturn("5");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void testCompleteHandover_unauthorizedDepartment_sendErrorForbidden() throws Exception {
        when(request.getParameter("action")).thenReturn("completeHandover");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("handoverId")).thenReturn("9");
        when(request.getParameter("departmentId")).thenReturn("6");
        when(request.getParameter("doneNote")).thenReturn("Đã xong");
        when(handoverService.completeHandover(eq(10), eq(9), eq(6), any(), any()))
                .thenReturn(ServiceResult.fail(ContractHandoverService.ERR_FORBIDDEN));

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void testCompleteHandover_targetNotFound_redirectsNotFound() throws Exception {
        when(request.getParameter("action")).thenReturn("completeHandover");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("handoverId")).thenReturn("9");
        when(request.getParameter("departmentId")).thenReturn("6");
        when(handoverService.completeHandover(anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn(ServiceResult.fail(ContractHandoverService.ERR_NOT_FOUND));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void testCompleteHandover_serviceFails_redirectsWithError() throws Exception {
        when(request.getParameter("action")).thenReturn("completeHandover");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("handoverId")).thenReturn("9");
        when(request.getParameter("departmentId")).thenReturn("1");
        when(request.getParameter("doneNote")).thenReturn("Đã hoàn tất nghiệm thu");
        when(handoverService.completeHandover(anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn(ServiceResult.fail(ContractHandoverService.ERR_COMPLETE_FAILED));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=handover_done_failed");
    }

    @Test
    public void testCompleteHandover_fromQueue_returnsToQueueWithFilter_verifiesSessionActor() throws Exception {
        when(request.getParameter("action")).thenReturn("completeHandover");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("handoverId")).thenReturn("9");
        when(request.getParameter("departmentId")).thenReturn("1");
        when(request.getParameter("doneNote")).thenReturn("Đã xong chặng");
        when(request.getParameter("returnTo")).thenReturn("from-queue");
        when(request.getParameter("view")).thenReturn("mine");
        when(request.getParameter("queueDepartmentId")).thenReturn("5");
        when(handoverService.completeHandover(eq(10), eq(9), eq(1), eq("Đã xong chặng"),
                argThat(u -> u != null && u.getUserId() == 10 && u.getRole() != null)))
                .thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=handovers&view=mine&departmentId=5");
    }

    @Test
    public void testCompleteHandover_fromView_returnsToView() throws Exception {
        when(request.getParameter("action")).thenReturn("completeHandover");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("handoverId")).thenReturn("9");
        when(request.getParameter("departmentId")).thenReturn("1");
        when(request.getParameter("doneNote")).thenReturn("Đã xong chặng");
        when(request.getParameter("returnTo")).thenReturn("from-view");
        when(handoverService.completeHandover(anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=10");
    }

    @Test
    public void testCompleteHandover_fromEdit_returnsToEdit() throws Exception {
        when(request.getParameter("action")).thenReturn("completeHandover");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("handoverId")).thenReturn("9");
        when(request.getParameter("departmentId")).thenReturn("1");
        when(request.getParameter("doneNote")).thenReturn("Đã xong chặng");
        when(request.getParameter("returnTo")).thenReturn(null);
        when(handoverService.completeHandover(anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
    }

    @Test
    public void testDoPost_unknownAction_redirectsToContract() throws Exception {
        when(request.getParameter("action")).thenReturn("unknown_action");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
    }

    // ==================================================================
    // 4. ContractHandoverHelper utility tests
    // ==================================================================

    @Test
    public void testHelper_parseDepartmentIds() {
        assertEquals(Collections.emptyList(), ContractHandoverHelper.parseDepartmentIds(null));
        assertEquals(Collections.emptyList(), ContractHandoverHelper.parseDepartmentIds(new String[]{}));
        assertEquals(List.of(1, 2), ContractHandoverHelper.parseDepartmentIds(new String[]{"1", "2", "1", "abc", "-5"}));
    }

    @Test
    public void testHelper_handoverQueueFilterQuery() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter("view")).thenReturn("mine");
        when(req.getParameter("queueDepartmentId")).thenReturn("7");
        assertEquals("&view=mine&departmentId=7", ContractHandoverHelper.handoverQueueFilterQuery(req));

        when(req.getParameter("view")).thenReturn("all");
        when(req.getParameter("queueDepartmentId")).thenReturn("invalid");
        assertEquals("", ContractHandoverHelper.handoverQueueFilterQuery(req));
    }

    @Test
    public void testLazyHandoverServiceInitialization() {
        ContractHandoverController c = new ContractHandoverController(contractDAO, employeeDAO, permissionService);
        ContractHandoverService s1 = c.getHandoverService();
        assertNotNull(s1);
        ContractHandoverService s2 = c.getHandoverService();
        assertSame("getHandoverService must return cached instance", s1, s2);
    }
}
