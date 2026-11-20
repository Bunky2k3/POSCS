package poscs.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.ContractDAO;
import poscs.dao.ContractDocumentDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.ContractDocument;
import poscs.model.Role;
import poscs.model.User;
import poscs.service.ContractDocumentService;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test toàn diện cho {@link ContractDocumentController} (Task 4.16 - [UC-52]).
 * Kiểm thử:
 * - Routing doGet và doPost với các tham số action chuẩn.
 * - handleAddDocument: thành công, phân quyền, thiếu/sai dữ liệu, lỗi service.
 * - handleVoidDocument: thành công, phân quyền, thiếu lý do, lỗi service.
 * - Khởi tạo getter/setter và DI.
 */
public class ContractDocumentControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private ContractDocumentController controller;
    private ContractDAO contractDAO;
    private EmployeeDAO employeeDAO;
    private ContractDocumentService documentService;
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
        documentService = mock(ContractDocumentService.class);
        permissionService = mock(PermissionService.class);

        controller = new ContractDocumentController(contractDAO, employeeDAO, documentService, permissionService);

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
        when(permissionService.canManageContractDocuments(adminUser)).thenReturn(true);
        when(permissionService.canManageContractDocuments(salesManager)).thenReturn(true);
        when(permissionService.canManageContractDocuments(salesSubordinate)).thenReturn(false);
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
    // 1. GET requests (doGet)
    // ==================================================================

    @Test
    public void doGet_unauthorizedUser_forbidden() throws Exception {
        when(permissionService.canViewContracts(adminUser)).thenReturn(false);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void doGet_missingContractId_redirectsToContractList() throws Exception {
        when(request.getParameter("contractId")).thenReturn(null);
        when(request.getParameter("id")).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
    }

    @Test
    public void doGet_contractNotFoundOrDeleted_redirectsToNotFound() throws Exception {
        when(request.getParameter("contractId")).thenReturn("99");
        when(contractDAO.findById(99)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void doGet_contractDeleted_redirectsToNotFound() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, true));

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void doGet_validContract_defaultRedirectsToEdit() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
    }

    @Test
    public void doGet_validContract_viewActionRedirectsToView() throws Exception {
        when(request.getParameter("id")).thenReturn("10");
        when(request.getParameter("action")).thenReturn("view");
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=10");
    }

    // ==================================================================
    // 2. POST routing (doPost)
    // ==================================================================

    @Test
    public void doPost_nullOrEmptyAction_redirectsToContractList() throws Exception {
        when(request.getParameter("action")).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
    }

    @Test
    public void doPost_unknownAction_redirectsToContractList() throws Exception {
        when(request.getParameter("action")).thenReturn("unknownAction");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
    }

    @Test
    public void doPost_addDocumentAliases_invokesAddHandler() throws Exception {
        for (String action : new String[]{"addDocument", "create", "add"}) {
            reset(response, documentService);
            when(request.getParameter("action")).thenReturn(action);
            when(request.getParameter("contractId")).thenReturn("10");
            when(request.getParameter("docType")).thenReturn(ContractDocument.TYPE_SIGNED_CONTRACT);
            when(request.getParameter("fileUrl")).thenReturn("https://example.com/doc.pdf");
            when(documentService.addDocument(eq(10), any(), any()))
                    .thenReturn(ServiceResult.ok(new ContractDocument(), "ok"));

            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
        }
    }

    @Test
    public void doPost_voidDocumentAliases_invokesVoidHandler() throws Exception {
        for (String action : new String[]{"voidDocument", "void", "delete", "remove"}) {
            reset(response, documentService);
            when(request.getParameter("action")).thenReturn(action);
            when(request.getParameter("contractId")).thenReturn("10");
            when(request.getParameter("documentId")).thenReturn("101");
            when(request.getParameter("reason")).thenReturn("Lý do nhầm");
            when(documentService.voidDocument(eq(10), eq(101), anyString(), any()))
                    .thenReturn(ServiceResult.ok(null, "ok"));

            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
        }
    }

    // ==================================================================
    // 3. handleAddDocument
    // ==================================================================

    @Test
    public void handleAddDocument_missingContractId_redirectsToNotFound() throws Exception {
        when(request.getParameter("contractId")).thenReturn(null);
        when(request.getParameter("id")).thenReturn(null);

        controller.handleAddDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void handleAddDocument_invalidContractId_redirectsToNotFound() throws Exception {
        when(request.getParameter("contractId")).thenReturn("abc");

        controller.handleAddDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void handleAddDocument_contractZeroOrNegative_redirectsToNotFound() throws Exception {
        when(request.getParameter("contractId")).thenReturn("0");

        controller.handleAddDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void handleAddDocument_success_redirectsToEditContract() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("docType")).thenReturn(ContractDocument.TYPE_SIGNED_CONTRACT);
        when(request.getParameter("docTitle")).thenReturn("Hợp đồng nguyên tắc");
        when(request.getParameter("fileUrl")).thenReturn("https://example.com/hd.pdf");
        when(request.getParameter("docNote")).thenReturn("Đã ký 2 bên");

        when(documentService.addDocument(eq(10), any(), eq(adminUser)))
                .thenReturn(ServiceResult.ok(new ContractDocument(), "ok"));

        controller.handleAddDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
    }

    @Test
    public void handleAddDocument_serviceForbidden_sends403() throws Exception {
        loginUser(salesSubordinate);
        when(request.getParameter("contractId")).thenReturn("10");
        when(documentService.addDocument(eq(10), any(), eq(salesSubordinate)))
                .thenReturn(ServiceResult.fail(ContractDocumentService.ERR_FORBIDDEN));

        controller.handleAddDocument(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void handleAddDocument_serviceNotFound_redirectsToNotFound() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(documentService.addDocument(eq(10), any(), eq(adminUser)))
                .thenReturn(ServiceResult.fail(ContractDocumentService.ERR_NOT_FOUND));

        controller.handleAddDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void handleAddDocument_serviceInvalidInput_redirectsWithDocumentInvalid() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(documentService.addDocument(eq(10), any(), eq(adminUser)))
                .thenReturn(ServiceResult.fail(ContractDocumentService.ERR_INVALID));

        controller.handleAddDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=document_invalid");
    }

    @Test
    public void handleAddDocument_serviceFailed_redirectsWithDocumentFailed() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(documentService.addDocument(eq(10), any(), eq(adminUser)))
                .thenReturn(ServiceResult.fail(ContractDocumentService.ERR_FAILED));

        controller.handleAddDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=document_failed");
    }

    // ------------------------------------------------------------------
    // 3b. BR-59 (Task 10.3): ngày trên giấy, số giấy, link không bắt buộc
    // ------------------------------------------------------------------

    private ContractDocument captureAddedDocument() throws Exception {
        org.mockito.ArgumentCaptor<ContractDocument> captor =
                org.mockito.ArgumentCaptor.forClass(ContractDocument.class);
        verify(documentService).addDocument(eq(10), captor.capture(), eq(adminUser));
        return captor.getValue();
    }

    @Test
    public void handleAddDocument_readsDocDateAndDocNo_linkMayBeEmpty() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("docType")).thenReturn("Biên bản giao nhận hàng hoá");
        when(request.getParameter("docDate")).thenReturn("2026-11-05");
        when(request.getParameter("docNo")).thenReturn("12/BBGN");
        when(request.getParameter("fileUrl")).thenReturn("");
        when(documentService.addDocument(eq(10), any(), eq(adminUser)))
                .thenReturn(ServiceResult.ok(new ContractDocument(), "ok"));

        controller.handleAddDocument(request, response);

        ContractDocument sent = captureAddedDocument();
        assertEquals(java.sql.Date.valueOf("2026-11-05"), sent.getDocDate());
        assertEquals("12/BBGN", sent.getDocNo());
        assertNull(sent.getFileUrl());
        assertEquals("Biên bản giao nhận hàng hoá", sent.getDocType());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
    }

    @Test
    public void handleAddDocument_blankOrMalformedDate_passesNullDateToService() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("docType")).thenReturn("Phiếu GQNCKH");
        when(request.getParameter("docNo")).thenReturn("   ");

        for (String raw : new String[]{null, "", "05/11/2026"}) {
            reset(documentService);
            when(documentService.addDocument(eq(10), any(), eq(adminUser)))
                    .thenReturn(ServiceResult.fail(ContractDocumentService.ERR_DATE_REQUIRED));
            when(request.getParameter("docDate")).thenReturn(raw);

            controller.handleAddDocument(request, response);

            ContractDocument sent = captureAddedDocument();
            assertNull("docDate=" + raw, sent.getDocDate());
            assertNull(sent.getDocNo());
        }
    }

    @Test
    public void handleAddDocument_serviceDateRequired_redirectsWithMsg062Key() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(documentService.addDocument(eq(10), any(), eq(adminUser)))
                .thenReturn(ServiceResult.fail(ContractDocumentService.ERR_DATE_REQUIRED));

        controller.handleAddDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=document_date_required");
    }

    @Test
    public void handleAddDocument_serviceDateFuture_redirectsWithMsg063Key() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(documentService.addDocument(eq(10), any(), eq(adminUser)))
                .thenReturn(ServiceResult.fail(ContractDocumentService.ERR_DATE_FUTURE));

        controller.handleAddDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=document_date_future");
    }

    @Test
    public void handleAddDocument_serviceTitleRequired_redirectsWithTitleRequiredKey() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(documentService.addDocument(eq(10), any(), eq(adminUser)))
                .thenReturn(ServiceResult.fail(ContractDocumentService.ERR_TITLE_REQUIRED));

        controller.handleAddDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=document_title_required");
    }

    @Test
    public void handleAddDocument_unknownServiceError_fallsBackToDocumentFailed() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(documentService.addDocument(eq(10), any(), eq(adminUser)))
                .thenReturn(ServiceResult.fail("loi_la"));

        controller.handleAddDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=document_failed");
    }

    // ==================================================================
    // 4. handleVoidDocument
    // ==================================================================

    @Test
    public void handleVoidDocument_missingContractIdOrDocumentId_redirectsToNotFound() throws Exception {
        when(request.getParameter("contractId")).thenReturn(null);
        when(request.getParameter("documentId")).thenReturn("101");
        controller.handleVoidDocument(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        reset(response);
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("documentId")).thenReturn(null);
        controller.handleVoidDocument(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void handleVoidDocument_invalidIds_redirectsToNotFound() throws Exception {
        when(request.getParameter("contractId")).thenReturn("abc");
        when(request.getParameter("documentId")).thenReturn("101");
        controller.handleVoidDocument(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        reset(response);
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("documentId")).thenReturn("0");
        controller.handleVoidDocument(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void handleVoidDocument_success_redirectsToEditContract() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("documentId")).thenReturn("101");
        when(request.getParameter("reason")).thenReturn("Nhầm liên kết");

        when(documentService.voidDocument(10, 101, "Nhầm liên kết", adminUser))
                .thenReturn(ServiceResult.ok(null, "ok"));

        controller.handleVoidDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
    }

    @Test
    public void handleVoidDocument_serviceForbidden_sends403() throws Exception {
        loginUser(salesSubordinate);
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("documentId")).thenReturn("101");
        when(request.getParameter("reason")).thenReturn("Lý do");
        when(documentService.voidDocument(eq(10), eq(101), anyString(), eq(salesSubordinate)))
                .thenReturn(ServiceResult.fail(ContractDocumentService.ERR_FORBIDDEN));

        controller.handleVoidDocument(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void handleVoidDocument_serviceNotFound_redirectsToNotFound() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("documentId")).thenReturn("101");
        when(documentService.voidDocument(eq(10), eq(101), any(), eq(adminUser)))
                .thenReturn(ServiceResult.fail(ContractDocumentService.ERR_NOT_FOUND));

        controller.handleVoidDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void handleVoidDocument_serviceReasonRequired_redirectsWithDocumentReasonRequired() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("documentId")).thenReturn("101");
        when(request.getParameter("reason")).thenReturn("");
        when(documentService.voidDocument(eq(10), eq(101), isNull(), eq(adminUser)))
                .thenReturn(ServiceResult.fail(ContractDocumentService.ERR_REASON_REQUIRED));

        controller.handleVoidDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=document_reason_required");
    }

    @Test
    public void handleVoidDocument_serviceFailed_redirectsWithDocumentFailed() throws Exception {
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("documentId")).thenReturn("101");
        when(request.getParameter("reason")).thenReturn("Lý do hợp lệ");
        when(documentService.voidDocument(eq(10), eq(101), anyString(), eq(adminUser)))
                .thenReturn(ServiceResult.fail(ContractDocumentService.ERR_FAILED));

        controller.handleVoidDocument(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10&error=document_failed");
    }

    // ==================================================================
    // 5. Dependency Getters, Setters & Constructors
    // ==================================================================

    @Test
    public void gettersAndSetters_workAsExpected() {
        ContractDocumentController ctrl = new ContractDocumentController();
        ContractDAO cDao = new ContractDAO();
        EmployeeDAO eDao = new EmployeeDAO();
        PermissionService ps = new PermissionService(eDao);
        ContractDocumentService ds = new ContractDocumentService();

        ctrl.setContractDAO(cDao);
        ctrl.setEmployeeDAO(eDao);
        ctrl.setPermissionService(ps);
        ctrl.setDocumentService(ds);

        assertSame(cDao, ctrl.getContractDAO());
        assertSame(eDao, ctrl.getEmployeeDAO());
        assertSame(ps, ctrl.getPermissionService());
        assertSame(ds, ctrl.getDocumentService());
    }

    @Test
    public void constructors_andLazyInitialization() {
        ContractDocumentController ctrl1 = new ContractDocumentController();
        assertNotNull(ctrl1.getContractDAO());
        assertNotNull(ctrl1.getEmployeeDAO());
        assertNotNull(ctrl1.getPermissionService());
        assertNotNull(ctrl1.getDocumentService());

        ContractDocumentController ctrl2 = new ContractDocumentController(null, null, null);
        assertNotNull(ctrl2.getContractDAO());
        assertNotNull(ctrl2.getEmployeeDAO());
        assertNotNull(ctrl2.getPermissionService());
        assertNotNull(ctrl2.getDocumentService());
    }
}
