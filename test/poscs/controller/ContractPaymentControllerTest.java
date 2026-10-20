package poscs.controller;

import java.math.BigDecimal;
import java.sql.Date;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.ContractDAO;
import poscs.dao.ContractPaymentDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.ContractPayment;
import poscs.model.Role;
import poscs.model.User;
import poscs.service.ContractPaymentService;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test toàn diện cho {@link ContractPaymentController} (Task 4.15).
 * Đáp ứng: UC-53 (Lập kỳ, ghi nhận đã thu, xoá kỳ), BR-18 (tiền tệ VNĐ),
 * phân quyền RBAC và kiểm tra các kịch bản đúng/sai theo ma trận SPEC.
 */
public class ContractPaymentControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private ContractPaymentController controller;
    private ContractDAO contractDAO;
    private EmployeeDAO employeeDAO;
    private ContractPaymentService paymentService;
    private PermissionService permissionService;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    private User adminUser;
    private User salesManager;
    private User salesSubordinate;

    @Before
    public void setUp() {
        contractDAO = mock(ContractDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        paymentService = mock(ContractPaymentService.class);
        permissionService = mock(PermissionService.class);

        controller = new ContractPaymentController(contractDAO, employeeDAO, paymentService, permissionService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);

        adminUser = buildUser(1, PermissionService.ROLE_ADMIN, null);
        salesManager = buildUser(2, PermissionService.ROLE_SALES, null);
        salesSubordinate = buildUser(3, PermissionService.ROLE_SALES, 2);

        loginUser(adminUser);
        when(permissionService.canViewContracts(any())).thenReturn(true);
        when(permissionService.canManageContractPayments(adminUser)).thenReturn(true);
        when(permissionService.canManageContractPayments(salesManager)).thenReturn(true);
        when(permissionService.canManageContractPayments(salesSubordinate)).thenReturn(false);
    }

    private User buildUser(int userId, String roleName, Integer managerId) {
        User u = new User();
        u.setUserId(userId);
        u.setManagerId(managerId);
        u.setDeleted(false);
        Role r = new Role();
        r.setRoleName(roleName);
        u.setRole(r);
        return u;
    }

    private void loginUser(User user) {
        when(session.getAttribute("currentUser")).thenReturn(user);
    }

    private Contract sampleContract(int contractId, boolean deleted) {
        Contract c = new Contract();
        c.setContractId(contractId);
        c.setContractCode("HD-TEST-" + contractId);
        c.setTitle("Hợp đồng thử nghiệm");
        c.setDeleted(deleted);
        return c;
    }

    // ==================================================================
    // 1. GET requests
    // ==================================================================

    @Test
    public void testDoGet_unauthorizedUser_sendErrorForbidden() throws Exception {
        when(permissionService.canViewContracts(any())).thenReturn(false);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    public void testDoGet_missingContractId_redirectsToContractList() throws Exception {
        when(request.getParameter("contractId")).thenReturn(null);
        when(request.getParameter("id")).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract");

        reset(response);
        when(request.getParameter("contractId")).thenReturn("invalid_id");
        controller.doGet(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
    }

    @Test
    public void testDoGet_contractNotFound_redirectsToNotFound() throws Exception {
        when(request.getParameter("contractId")).thenReturn("99");
        when(contractDAO.findById(99)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void testDoGet_contractDeleted_redirectsToNotFound() throws Exception {
        when(request.getParameter("contractId")).thenReturn("99");
        when(contractDAO.findById(99)).thenReturn(sampleContract(99, true));

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void testDoGet_actionView_redirectsToViewContract() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("action")).thenReturn("view");
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=10");
    }

    @Test
    public void testDoGet_defaultAction_redirectsToEditContract() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("action")).thenReturn(null);
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
    }

    @Test
    public void testDoGet_usesIdParamWhenContractIdNull() throws Exception {
        when(request.getParameter("contractId")).thenReturn(null);
        when(request.getParameter("id")).thenReturn("15");
        when(contractDAO.findById(15)).thenReturn(sampleContract(15, false));

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=15");
    }

    // ==================================================================
    // 2. POST routing & Action aliases
    // ==================================================================

    @Test
    public void testDoPost_nullOrEmptyAction_redirectsToContract() throws Exception {
        when(request.getParameter("action")).thenReturn(null);
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract");

        reset(response);
        when(request.getParameter("action")).thenReturn("");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
    }

    @Test
    public void testDoPost_unknownAction_redirectsToContract() throws Exception {
        when(request.getParameter("action")).thenReturn("unknownAction");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
    }

    @Test
    public void testDoPost_routingAliases_addPayment() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(paymentService.addPayment(eq(10), any(), any()))
                .thenReturn(ServiceResult.ok(new ContractPayment()));

        for (String actionName : new String[]{"addPayment", "create", "add"}) {
            reset(response);
            when(request.getParameter("action")).thenReturn(actionName);
            controller.doPost(request, response);
            verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
        }
    }

    @Test
    public void testDoPost_routingAliases_markPaid() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("20");
        when(paymentService.markPaymentPaid(eq(10), eq(20), any(), any()))
                .thenReturn(ServiceResult.ok(null));

        for (String actionName : new String[]{"markPaid", "paid"}) {
            reset(response);
            when(request.getParameter("action")).thenReturn(actionName);
            controller.doPost(request, response);
            verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
        }
    }

    @Test
    public void testDoPost_routingAliases_removePayment() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("20");
        when(paymentService.deletePayment(eq(10), eq(20), any()))
                .thenReturn(ServiceResult.ok(null));

        for (String actionName : new String[]{"removePayment", "delete", "remove"}) {
            reset(response);
            when(request.getParameter("action")).thenReturn(actionName);
            controller.doPost(request, response);
            verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
        }
    }

    // ==================================================================
    // 3. handleAddPayment (UC-53, BR-18)
    // ==================================================================

    @Test
    public void testAddPayment_missingContractId_redirectsToNotFound() throws Exception {
        when(request.getParameter("action")).thenReturn("addPayment");
        when(request.getParameter("contractId")).thenReturn(null);
        when(request.getParameter("id")).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
        verify(paymentService, never()).addPayment(anyInt(), any(), any());

        reset(response);
        when(request.getParameter("contractId")).thenReturn("non_numeric");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        reset(response);
        when(request.getParameter("contractId")).thenReturn("-1");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        reset(response);
        when(request.getParameter("contractId")).thenReturn("0");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void testAddPayment_invalidAmountOrDueDateString_delegatesToServiceAndHandlesError() throws Exception {
        when(request.getParameter("action")).thenReturn("addPayment");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("invoiceAmount")).thenReturn("invalid_amount_abc");
        when(request.getParameter("dueDate")).thenReturn("invalid_date_xyz");

        when(paymentService.addPayment(eq(10), any(), any()))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_INVALID_AMOUNT));

        controller.doPost(request, response);

        verify(paymentService).addPayment(eq(10), argThat(p ->
                p.getInvoiceAmount() == null && p.getDueDate() == null
        ), any());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=payment_failed");
    }

    @Test
    public void testAddPayment_forbidden_sendsErrorForbidden() throws Exception {
        loginUser(salesSubordinate);
        when(request.getParameter("action")).thenReturn("addPayment");
        when(request.getParameter("contractId")).thenReturn("10");

        when(paymentService.addPayment(eq(10), any(), eq(salesSubordinate)))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_FORBIDDEN));

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    public void testAddPayment_contractNotFound_redirectsToNotFound() throws Exception {
        when(request.getParameter("action")).thenReturn("addPayment");
        when(request.getParameter("contractId")).thenReturn("99");

        when(paymentService.addPayment(eq(99), any(), any()))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_NOT_FOUND));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void testAddPayment_contractFrozen_redirectsToPaymentFailed() throws Exception {
        when(request.getParameter("action")).thenReturn("addPayment");
        when(request.getParameter("contractId")).thenReturn("10");

        when(paymentService.addPayment(eq(10), any(), any()))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_PAYMENT_FROZEN));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=payment_failed");
    }

    @Test
    public void testAddPayment_invalidAmount_redirectsToPaymentFailed() throws Exception {
        when(request.getParameter("action")).thenReturn("addPayment");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("invoiceAmount")).thenReturn("-500000");

        when(paymentService.addPayment(eq(10), any(), any()))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_INVALID_AMOUNT));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=payment_failed");
    }

    @Test
    public void testAddPayment_missingDueDate_redirectsToPaymentFailed() throws Exception {
        when(request.getParameter("action")).thenReturn("addPayment");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("invoiceAmount")).thenReturn("500000000");
        when(request.getParameter("dueDate")).thenReturn(null);

        when(paymentService.addPayment(eq(10), any(), any()))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_MISSING_DUE_DATE));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=payment_failed");
    }

    @Test
    public void testAddPayment_serviceFailed_redirectsToPaymentFailed() throws Exception {
        when(request.getParameter("action")).thenReturn("addPayment");
        when(request.getParameter("contractId")).thenReturn("10");

        when(paymentService.addPayment(eq(10), any(), any()))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_PAYMENT_FAILED));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=payment_failed");
    }

    @Test
    public void testAddPayment_success_parsesVndAndDatesAndRedirects() throws Exception {
        loginUser(salesManager);
        when(request.getParameter("action")).thenReturn("addPayment");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("invoiceAmount")).thenReturn("1.500.000.000");
        when(request.getParameter("dueDate")).thenReturn("2026-11-01");
        when(request.getParameter("paidDate")).thenReturn("2026-11-02");

        when(paymentService.addPayment(eq(10), any(), eq(salesManager)))
                .thenReturn(ServiceResult.ok(new ContractPayment()));

        controller.doPost(request, response);

        verify(paymentService).addPayment(eq(10), argThat(p ->
                p.getInvoiceAmount() != null &&
                p.getInvoiceAmount().compareTo(new BigDecimal("1500000000")) == 0 &&
                Date.valueOf("2026-11-01").equals(p.getDueDate()) &&
                Date.valueOf("2026-11-02").equals(p.getPaidDate())
        ), eq(salesManager));

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
    }

    // ==================================================================
    // 4. handleMarkPaid (UC-53)
    // ==================================================================

    @Test
    public void testMarkPaid_missingContractIdOrPaymentId_redirectsToNotFound() throws Exception {
        when(request.getParameter("action")).thenReturn("markPaid");

        when(request.getParameter("contractId")).thenReturn(null);
        when(request.getParameter("paymentId")).thenReturn("20");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        reset(response);
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn(null);
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        reset(response);
        when(request.getParameter("contractId")).thenReturn("abc");
        when(request.getParameter("paymentId")).thenReturn("xyz");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        reset(response);
        when(request.getParameter("contractId")).thenReturn("-1");
        when(request.getParameter("paymentId")).thenReturn("20");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        reset(response);
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("-5");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        verify(paymentService, never()).markPaymentPaid(anyInt(), anyInt(), any(), any());
    }

    @Test
    public void testMarkPaid_invalidDateFormat_redirectsToPaymentFailed() throws Exception {
        when(request.getParameter("action")).thenReturn("markPaid");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("20");
        when(request.getParameter("paidDate")).thenReturn("abc_not_a_date");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=payment_failed");
        verify(paymentService, never()).markPaymentPaid(anyInt(), anyInt(), any(), any());

        reset(response);
        when(request.getParameter("paidDate")).thenReturn("2026-99-99");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=payment_failed");
        verify(paymentService, never()).markPaymentPaid(anyInt(), anyInt(), any(), any());
    }

    @Test
    public void testMarkPaid_forbidden_sendsErrorForbidden() throws Exception {
        loginUser(salesSubordinate);
        when(request.getParameter("action")).thenReturn("markPaid");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("20");

        when(paymentService.markPaymentPaid(eq(10), eq(20), any(), eq(salesSubordinate)))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_FORBIDDEN));

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    public void testMarkPaid_notFound_redirectsToNotFound() throws Exception {
        when(request.getParameter("action")).thenReturn("markPaid");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("99");

        when(paymentService.markPaymentPaid(eq(10), eq(99), any(), any()))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_NOT_FOUND));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void testMarkPaid_failed_redirectsToPaymentFailed() throws Exception {
        when(request.getParameter("action")).thenReturn("markPaid");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("20");

        when(paymentService.markPaymentPaid(eq(10), eq(20), any(), any()))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_PAYMENT_FAILED));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=payment_failed");
    }

    @Test
    public void testMarkPaid_success_withExplicitPaidDate() throws Exception {
        loginUser(adminUser);
        when(request.getParameter("action")).thenReturn("markPaid");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("20");
        when(request.getParameter("paidDate")).thenReturn("2026-10-15");

        when(paymentService.markPaymentPaid(eq(10), eq(20), eq(Date.valueOf("2026-10-15")), eq(adminUser)))
                .thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(paymentService).markPaymentPaid(eq(10), eq(20), eq(Date.valueOf("2026-10-15")), eq(adminUser));
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
    }

    @Test
    public void testMarkPaid_success_withNullPaidDate() throws Exception {
        loginUser(adminUser);
        when(request.getParameter("action")).thenReturn("markPaid");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("20");
        when(request.getParameter("paidDate")).thenReturn(null);

        when(paymentService.markPaymentPaid(eq(10), eq(20), isNull(), eq(adminUser)))
                .thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(paymentService).markPaymentPaid(eq(10), eq(20), isNull(), eq(adminUser));
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
    }

    // ==================================================================
    // 5. handleRemovePayment (UC-53)
    // ==================================================================

    @Test
    public void testRemovePayment_missingContractIdOrPaymentId_redirectsToNotFound() throws Exception {
        when(request.getParameter("action")).thenReturn("removePayment");

        when(request.getParameter("contractId")).thenReturn(null);
        when(request.getParameter("paymentId")).thenReturn("20");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        reset(response);
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn(null);
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        reset(response);
        when(request.getParameter("contractId")).thenReturn("abc");
        when(request.getParameter("paymentId")).thenReturn("xyz");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        reset(response);
        when(request.getParameter("contractId")).thenReturn("-1");
        when(request.getParameter("paymentId")).thenReturn("20");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        reset(response);
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("0");
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        verify(paymentService, never()).deletePayment(anyInt(), anyInt(), any());
    }

    @Test
    public void testRemovePayment_forbidden_sendsErrorForbidden() throws Exception {
        loginUser(salesSubordinate);
        when(request.getParameter("action")).thenReturn("removePayment");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("20");

        when(paymentService.deletePayment(eq(10), eq(20), eq(salesSubordinate)))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_FORBIDDEN));

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    public void testRemovePayment_notFound_redirectsToNotFound() throws Exception {
        when(request.getParameter("action")).thenReturn("removePayment");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("99");

        when(paymentService.deletePayment(eq(10), eq(99), any()))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_NOT_FOUND));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void testRemovePayment_alreadyPaid_redirectsToPaymentFailed() throws Exception {
        when(request.getParameter("action")).thenReturn("removePayment");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("20");

        when(paymentService.deletePayment(eq(10), eq(20), any()))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_PAYMENT_ALREADY_PAID));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=payment_failed");
    }

    @Test
    public void testRemovePayment_contractFrozen_redirectsToPaymentFailed() throws Exception {
        when(request.getParameter("action")).thenReturn("removePayment");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("20");

        when(paymentService.deletePayment(eq(10), eq(20), any()))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_PAYMENT_FROZEN));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=payment_failed");
    }

    @Test
    public void testRemovePayment_serviceFailed_redirectsToPaymentFailed() throws Exception {
        when(request.getParameter("action")).thenReturn("removePayment");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("20");

        when(paymentService.deletePayment(eq(10), eq(20), any()))
                .thenReturn(ServiceResult.fail(ContractPaymentService.ERR_PAYMENT_FAILED));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=payment_failed");
    }

    @Test
    public void testRemovePayment_success_deletesAndRedirects() throws Exception {
        loginUser(salesManager);
        when(request.getParameter("action")).thenReturn("removePayment");
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("paymentId")).thenReturn("20");

        when(paymentService.deletePayment(eq(10), eq(20), eq(salesManager)))
                .thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(paymentService).deletePayment(eq(10), eq(20), eq(salesManager));
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
    }

    // ==================================================================
    // 6. Getters, Setters & Lazy Initialization
    // ==================================================================

    @Test
    public void testGettersSettersAndLazyInitialization() {
        ContractPaymentController ctrl = new ContractPaymentController();
        assertNotNull(ctrl.getContractDAO());
        assertNotNull(ctrl.getEmployeeDAO());
        PermissionService ps1 = ctrl.getPermissionService();
        PermissionService ps2 = ctrl.getPermissionService();
        assertNotNull(ps1);
        assertSame("PermissionService must be cached after first access", ps1, ps2);

        ContractPaymentService pms1 = ctrl.getPaymentService();
        ContractPaymentService pms2 = ctrl.getPaymentService();
        assertNotNull(pms1);
        assertSame("ContractPaymentService must be cached after first access", pms1, pms2);

        ContractDAO newCd = new ContractDAO();
        EmployeeDAO newEd = new EmployeeDAO();
        PermissionService newPs = new PermissionService(newEd);
        ContractPaymentService newPms = new ContractPaymentService(new ContractPaymentDAO(), newCd, newPs);

        ctrl.setContractDAO(newCd);
        ctrl.setEmployeeDAO(newEd);
        ctrl.setPermissionService(newPs);
        ctrl.setPaymentService(newPms);

        assertSame(newCd, ctrl.getContractDAO());
        assertSame(newEd, ctrl.getEmployeeDAO());
        assertSame(newPs, ctrl.getPermissionService());
        assertSame(newPms, ctrl.getPaymentService());
    }
}
