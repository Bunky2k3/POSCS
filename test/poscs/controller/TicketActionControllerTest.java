package poscs.controller;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Contract;
import poscs.model.Enterprise;
import poscs.model.Role;
import poscs.model.TechnicalRequest;
import poscs.model.User;
import poscs.service.PermissionService;
import poscs.service.TicketService;
import poscs.validator.TicketValidator;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho TicketActionController và TicketActionHelper (Task 5.4).
 * Đáp ứng các quy tắc và kịch bản:
 * - UC-21: Tạo phiếu hỗ trợ (BR-38, BR-39, BR-41, BR-43).
 * - UC-22: Cập nhật phiếu & phân quyền kỹ thuật viên (BR-39, BR-40, BR-41, BR-42).
 * - UC-23, BR-47: Xóa phiếu hỗ trợ (chặn Đang xử lý, xóa mềm Mới tiếp nhận/Đã đóng).
 */
public class TicketActionControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private TicketActionController controller;
    private TechnicalSupportTicketDAO ticketDAO;
    private CustomerDAO customerDAO;
    private ContractDAO contractDAO;
    private EmployeeDAO employeeDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @Before
    public void setUp() {
        ticketDAO = mock(TechnicalSupportTicketDAO.class);
        customerDAO = mock(CustomerDAO.class);
        contractDAO = mock(ContractDAO.class);
        employeeDAO = mock(EmployeeDAO.class);

        controller = new TicketActionController(ticketDAO, customerDAO, contractDAO, employeeDAO, null, null);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);

        loginAs("Sales", 99);
        when(customerDAO.findRolesOf(anyInt())).thenReturn(Collections.singletonList("Khách mua"));

        // Kỹ thuật viên mặc định (id=50, role="Kỹ thuật")
        User tech50 = new User();
        tech50.setUserId(50);
        tech50.setRole(new Role(2, "Kỹ thuật"));
        tech50.setDeleted(false);
        when(employeeDAO.findById(50)).thenReturn(tech50);
    }

    private void loginAs(String roleName, int userId) {
        User user = new User();
        user.setUserId(userId);
        user.setRole(new Role(1, roleName));
        when(session.getAttribute("currentUser")).thenReturn(user);
    }

    private TechnicalRequest fullyValidTicket() {
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

    private void stubValidCreateFields() {
        when(request.getParameter("enterpriseId")).thenReturn("10");
        when(request.getParameter("ticketType")).thenReturn("Bảo hành");
        when(request.getParameter("priority")).thenReturn("Cao");
        when(request.getParameter("receptionChannel")).thenReturn("Điện thoại");
        when(request.getParameter("assignedTechnicianId")).thenReturn("50");
        when(request.getParameter("description")).thenReturn("Thiết bị lỗi nguồn");
    }

    private void stubValidUpdateFields() {
        when(request.getParameter("ticketId")).thenReturn("3");
        when(request.getParameter("enterpriseId")).thenReturn("10");
        when(request.getParameter("ticketType")).thenReturn("Bảo hành");
        when(request.getParameter("priority")).thenReturn("Cao");
        when(request.getParameter("receptionChannel")).thenReturn("Điện thoại");
        when(request.getParameter("assignedTechnicianId")).thenReturn("50");
        when(request.getParameter("description")).thenReturn("Thiết bị lỗi nguồn");
        when(request.getParameter("status")).thenReturn("Đang xử lý");
    }

    // ==================================================================
    // 0. Chưa đăng nhập (Unauthenticated) -> 403 MSG-022 ở mọi action
    // ==================================================================

    @Test
    public void actions_unauthenticated_returns403() throws Exception {
        when(session.getAttribute("currentUser")).thenReturn(null);

        // GET new
        when(request.getParameter("action")).thenReturn("new");
        controller.doGet(request, response);
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), eq(PermissionService.MSG_022));

        // GET edit
        reset(response);
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("3");
        when(ticketDAO.findById(3)).thenReturn(fullyValidTicket());
        controller.doGet(request, response);
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), eq(PermissionService.MSG_022));

        // POST create
        reset(response);
        when(request.getParameter("action")).thenReturn("create");
        controller.doPost(request, response);
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), eq(PermissionService.MSG_022));

        // POST update
        reset(response);
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("ticketId")).thenReturn("3");
        when(ticketDAO.findById(3)).thenReturn(fullyValidTicket());
        controller.doPost(request, response);
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), eq(PermissionService.MSG_022));

        // POST delete
        reset(response);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("3");
        controller.doPost(request, response);
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), eq(PermissionService.MSG_022));
    }

    // ==================================================================
    // 1. UC-21: GET ?action=new & POST ?action=create
    // ==================================================================

    @Test
    public void showCreateForm_adminOrSales_forwardsToCreateViewWithDropdowns() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher(TicketActionHelper.CREATE_VIEW)).thenReturn(dispatcher);
        when(customerDAO.findAllByRole(eq("Khách mua"), any())).thenReturn(List.of(new Enterprise()));
        when(employeeDAO.findActiveByRole(eq("Kỹ thuật"), any())).thenReturn(List.of(new User()));

        when(request.getParameter("action")).thenReturn("new");
        controller.doGet(request, response);

        verify(request).setAttribute(eq("customerList"), any());
        verify(request).setAttribute(eq("userList"), any());
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void showCreateForm_technicianRole_returns403() throws Exception {
        loginAs("Kỹ thuật", 50);
        when(request.getParameter("action")).thenReturn("new");

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), eq(PermissionService.MSG_022));
        verify(request, never()).getRequestDispatcher(anyString());
    }

    @Test
    public void handleCreate_technicianRole_returns403() throws Exception {
        loginAs("Kỹ thuật", 50);
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), eq(PermissionService.MSG_022));
        verify(ticketDAO, never()).insert(any());
    }

    @Test
    public void handleCreate_validFields_insertsAndRedirectsToView() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("isWarranty")).thenReturn("on");
        when(ticketDAO.generateNextTicketCode()).thenReturn("TK-0001");
        when(ticketDAO.insert(any(TechnicalRequest.class))).thenReturn(7);

        controller.doPost(request, response);

        verify(ticketDAO).insert(argThat(t -> "TK-0001".equals(t.getTicketCode())
                && TicketService.STATUS_NEW.equals(t.getStatus())
                && t.isWarranty()
                && t.getCreatedBy() == 99));
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=view&id=7");
    }

    @Test
    public void handleCreate_missingWarrantyParam_defaultsToWarrantyTrue() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("isWarranty")).thenReturn(null); // không truyền -> BR-43 mặc định Có
        when(ticketDAO.generateNextTicketCode()).thenReturn("TK-0001");
        when(ticketDAO.insert(any(TechnicalRequest.class))).thenReturn(8);

        controller.doPost(request, response);

        verify(ticketDAO).insert(argThat(t -> t.isWarranty()));
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=view&id=8");
    }

    @Test
    public void handleCreate_missingMandatoryField_redirectsInvalid() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("description")).thenReturn("   "); // blank -> invalid

        controller.doPost(request, response);

        verify(ticketDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=new&error=invalid");
    }

    @Test
    public void handleCreate_customerNotBuyer_redirectsNotBuyer() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(customerDAO.findRolesOf(10)).thenReturn(List.of("Nhà cung cấp"));

        controller.doPost(request, response);

        verify(ticketDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=new&error=not_buyer");
    }

    @Test
    public void handleCreate_contractBelongsToOtherCustomer_redirectsContractMismatch() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("contractId")).thenReturn("88");
        Contract diffCustContract = new Contract();
        diffCustContract.setContractId(88);
        diffCustContract.setEnterpriseId(999);
        when(contractDAO.findById(88)).thenReturn(diffCustContract);

        controller.doPost(request, response);

        verify(ticketDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=new&error=contract_mismatch");
    }

    @Test
    public void handleCreate_insertFails_redirectsCreateFailed() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(ticketDAO.generateNextTicketCode()).thenReturn("TK-0001");
        when(ticketDAO.insert(any())).thenReturn(0); // failure

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=new&error=create_failed");
    }

    // ==================================================================
    // 2. UC-22: GET ?action=edit & POST ?action=update
    // ==================================================================

    @Test
    public void showEditForm_ticketNotFound_redirectsNotFound() throws Exception {
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("123");
        when(ticketDAO.findById(123)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?error=notfound");
    }

    @Test
    public void showEditForm_technicianNotAssigned_returns403() throws Exception {
        loginAs("Kỹ thuật", 1);
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("3");
        when(ticketDAO.findById(3)).thenReturn(fullyValidTicket()); // assigned to 50

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), eq(PermissionService.MSG_022));
    }

    @Test
    public void showEditForm_assignedTechnician_opensFormWithLockGeneralAndCanManageFalse() throws Exception {
        loginAs("Kỹ thuật", 50);
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("3");
        when(ticketDAO.findById(3)).thenReturn(fullyValidTicket());
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher(TicketActionHelper.UPDATE_VIEW)).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(request).setAttribute(eq("canManage"), eq(false));
        verify(request).setAttribute(eq("lockGeneral"), eq(true));
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void showEditForm_adminOrSales_opensFormWithCanManageTrueAndLockGeneralFalse() throws Exception {
        loginAs("Sales", 99);
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("3");
        when(ticketDAO.findById(3)).thenReturn(fullyValidTicket());
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher(TicketActionHelper.UPDATE_VIEW)).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(request).setAttribute(eq("canManage"), eq(true));
        verify(request).setAttribute(eq("lockGeneral"), eq(false));
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void handleUpdate_missingTicketIdOrNotFound_redirectsNotFound() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("ticketId")).thenReturn(null);

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?error=notfound");

        when(request.getParameter("ticketId")).thenReturn("999");
        when(ticketDAO.findById(999)).thenReturn(null);
        controller.doPost(request, response);
        verify(response, times(2)).sendRedirect(CONTEXT_PATH + "/ticket?error=notfound");
    }

    @Test
    public void handleUpdate_technicianNotAssigned_returns403() throws Exception {
        loginAs("Kỹ thuật", 1);
        when(request.getParameter("action")).thenReturn("update");
        stubValidUpdateFields();
        when(ticketDAO.findById(3)).thenReturn(fullyValidTicket());

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), eq(PermissionService.MSG_022));
        verify(ticketDAO, never()).update(any(), anyInt(), any());
    }

    @Test
    public void handleUpdate_assignedTechnician_updatesTechnicalFieldsAndLeavesGeneralUntouched() throws Exception {
        loginAs("Kỹ thuật", 50);
        when(request.getParameter("action")).thenReturn("update");
        stubValidUpdateFields();
        when(request.getParameter("status")).thenReturn("Đã đóng");
        when(request.getParameter("rootCause")).thenReturn("Hỏng tụ điện");
        when(request.getParameter("causeCategory")).thenReturn("Do thiết bị");
        when(request.getParameter("handlingPlan")).thenReturn("Thay thế tụ mới");
        when(request.getParameter("resolutionSummary")).thenReturn("Đã thay và test tải ổn định");
        // Giả mạo đổi người phụ trách sang 999
        when(request.getParameter("assignedTechnicianId")).thenReturn("999");

        TechnicalRequest existing = fullyValidTicket();
        when(ticketDAO.findById(3)).thenReturn(existing);
        when(ticketDAO.update(any(), anyInt(), any())).thenReturn(true);

        controller.doPost(request, response);

        verify(ticketDAO).update(argThat(t -> "Đã đóng".equals(t.getStatus())
                && "Hỏng tụ điện".equals(t.getRootCause())
                && "Do thiết bị".equals(t.getCauseCategory())
                && "Thay thế tụ mới".equals(t.getHandlingPlan())
                && "Đã thay và test tải ổn định".equals(t.getResolutionSummary())
                && t.getAssignedTechnicianId() == 50), // Không đổi người phụ trách
                eq(50), any());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=view&id=3");
    }

    @Test
    public void handleUpdate_invalidStatusTransition_redirectsInvalid() throws Exception {
        loginAs("Sales", 99);
        when(request.getParameter("action")).thenReturn("update");
        stubValidUpdateFields();
        when(request.getParameter("status")).thenReturn("Mới tiếp nhận"); // từ Đang xử lý về Mới tiếp nhận là sai BR-40

        when(ticketDAO.findById(3)).thenReturn(fullyValidTicket());

        controller.doPost(request, response);

        verify(ticketDAO, never()).update(any(), anyInt(), any());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=edit&id=3&error=invalid");
    }

    @Test
    public void handleUpdate_contractMismatch_redirectsContractMismatch() throws Exception {
        loginAs("Sales", 99);
        when(request.getParameter("action")).thenReturn("update");
        stubValidUpdateFields();
        when(request.getParameter("contractId")).thenReturn("99");
        Contract c = new Contract();
        c.setContractId(99);
        c.setEnterpriseId(888); // khác 10
        when(contractDAO.findById(99)).thenReturn(c);
        when(ticketDAO.findById(3)).thenReturn(fullyValidTicket());

        controller.doPost(request, response);

        verify(ticketDAO, never()).update(any(), anyInt(), any());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=edit&id=3&error=contract_mismatch");
    }

    @Test
    public void handleUpdate_daoUpdateFails_redirectsUpdateFailed() throws Exception {
        loginAs("Sales", 99);
        when(request.getParameter("action")).thenReturn("update");
        stubValidUpdateFields();
        when(ticketDAO.findById(3)).thenReturn(fullyValidTicket());
        when(ticketDAO.update(any(), anyInt(), any())).thenReturn(false);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=edit&id=3&error=update_failed");
    }

    // ==================================================================
    // 3. UC-23 & BR-47: POST ?action=delete
    // ==================================================================

    @Test
    public void handleDelete_technicianRole_returns403() throws Exception {
        loginAs("Kỹ thuật", 50);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("3");

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), eq(PermissionService.MSG_022));
        verify(ticketDAO, never()).softDelete(anyInt());
    }

    @Test
    public void handleDelete_missingId_redirectsToList() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/ticket");
        verify(ticketDAO, never()).softDelete(anyInt());
    }

    @Test
    public void handleDelete_nonExistentTicket_redirectsNotFound() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("999");
        when(ticketDAO.findById(999)).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?error=notfound");
        verify(ticketDAO, never()).softDelete(anyInt());
    }

    @Test
    public void handleDelete_ticketInProgress_blocksDeletion() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("3");
        TechnicalRequest inProgress = fullyValidTicket();
        inProgress.setStatus("Đang xử lý");
        when(ticketDAO.findById(3)).thenReturn(inProgress);
        when(ticketDAO.canDelete(3)).thenReturn(false);

        controller.doPost(request, response);

        verify(ticketDAO, never()).softDelete(anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=view&id=3&error=cannot_delete");
    }

    @Test
    public void handleDelete_ticketNewOrClosed_softDeletesAndRedirectsToList() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("3");
        TechnicalRequest closedTicket = fullyValidTicket();
        closedTicket.setStatus("Đã đóng");
        when(ticketDAO.findById(3)).thenReturn(closedTicket);
        when(ticketDAO.canDelete(3)).thenReturn(true);
        when(ticketDAO.softDelete(3)).thenReturn(true);

        controller.doPost(request, response);

        verify(ticketDAO).softDelete(3);
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket");
    }

    @Test
    public void handleDelete_softDeleteFails_redirectsDeleteFailed() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("3");
        TechnicalRequest closedTicket = fullyValidTicket();
        closedTicket.setStatus("Đã đóng");
        when(ticketDAO.findById(3)).thenReturn(closedTicket);
        when(ticketDAO.canDelete(3)).thenReturn(true);
        when(ticketDAO.softDelete(3)).thenReturn(false);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/ticket?action=view&id=3&error=delete_failed");
    }

    // ==================================================================
    // 4. Unknown actions redirect
    // ==================================================================

    @Test
    public void unknownGetAction_redirectsToList() throws Exception {
        when(request.getParameter("action")).thenReturn("random");
        controller.doGet(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket");
    }

    @Test
    public void unknownPostAction_redirectsToList() throws Exception {
        when(request.getParameter("action")).thenReturn("random");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/ticket");
    }

    // ==================================================================
    // 5. TicketActionHelper tests
    // ==================================================================

    @Test
    public void helper_parsingAndMappingTests() {
        assertNull(TicketActionHelper.parseDateTimeOrNull(null));
        assertNull(TicketActionHelper.parseDateTimeOrNull("   "));
        assertNull(TicketActionHelper.parseDateTimeOrNull("invalid-date"));
        assertNotNull(TicketActionHelper.parseDateTimeOrNull("2026-11-07T10:00"));

        assertNull(TicketActionHelper.parseIntOrNull(null));
        assertNull(TicketActionHelper.parseIntOrNull("   "));
        assertNull(TicketActionHelper.parseIntOrNull("abc"));
        assertEquals(Integer.valueOf(42), TicketActionHelper.parseIntOrNull(" 42 "));

        assertNull(TicketActionHelper.emptyToNull(null));
        assertNull(TicketActionHelper.emptyToNull("   "));
        assertEquals("test", TicketActionHelper.emptyToNull(" test "));

        assertEquals("not_buyer", TicketActionHelper.mapCreateError(TicketService.ERR_NOT_BUYER));
        assertEquals("contract_mismatch", TicketActionHelper.mapCreateError(TicketService.ERR_CONTRACT_MISMATCH));
        assertEquals("invalid", TicketActionHelper.mapCreateError(TicketService.ERR_INVALID));
        assertEquals("create_failed", TicketActionHelper.mapCreateError("unknown"));

        assertEquals("not_buyer", TicketActionHelper.mapUpdateError(TicketService.ERR_NOT_BUYER));
        assertEquals("contract_mismatch", TicketActionHelper.mapUpdateError(TicketService.ERR_CONTRACT_MISMATCH));
        assertEquals("invalid", TicketActionHelper.mapUpdateError(TicketService.ERR_INVALID));
        assertEquals("invalid", TicketActionHelper.mapUpdateError(TicketService.ERR_STATUS_TRANSITION));
        assertEquals("update_failed", TicketActionHelper.mapUpdateError("unknown"));

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter("isWarranty")).thenReturn(null);
        assertNull(TicketActionHelper.resolveWarranty(req));
        when(req.getParameter("isWarranty")).thenReturn("on");
        assertTrue(TicketActionHelper.resolveWarranty(req));
        when(req.getParameter("isWarranty")).thenReturn("false");
        assertFalse(TicketActionHelper.resolveWarranty(req));
    }
}
