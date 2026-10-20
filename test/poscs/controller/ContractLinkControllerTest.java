package poscs.controller;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.ContractDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.Role;
import poscs.model.User;
import poscs.service.ContractLinkService;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test toàn diện cho {@link ContractLinkController} (Task 4.18 - [UC-47]).
 * Kiểm thử:
 * - doGet: phân quyền, thiếu ID, không tìm thấy, xóa mềm, điều hướng edit/view.
 * - doPost: routing các action chuẩn và alias (linkContract, link, unlinkContract, unlink, default).
 * - handleLinkContract: thành công, phân quyền, thiếu ID, lỗi duplicate, lỗi invalid, lỗi not found.
 * - handleUnlinkContract: thành công, phân quyền, thiếu ID, lỗi failed, lỗi not found.
 * - Getters, setters, constructors và DI.
 */
public class ContractLinkControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private ContractLinkController controller;
    private ContractDAO contractDAO;
    private EmployeeDAO employeeDAO;
    private ContractLinkService linkService;
    private PermissionService permissionService;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    private User adminUser;

    @Before
    public void setUp() {
        contractDAO = mock(ContractDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        linkService = mock(ContractLinkService.class);
        permissionService = mock(PermissionService.class);

        controller = new ContractLinkController(contractDAO, employeeDAO, linkService, permissionService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);

        adminUser = new User();
        adminUser.setUserId(1);
        adminUser.setRole(new Role(1, PermissionService.ROLE_ADMIN));
        when(session.getAttribute("currentUser")).thenReturn(adminUser);

        when(permissionService.canViewContracts(any())).thenReturn(true);
        when(permissionService.canLinkContracts(any())).thenReturn(true);
    }

    private Contract sampleContract(int id, boolean deleted) {
        Contract c = new Contract();
        c.setContractId(id);
        c.setContractCode("HD-TEST-" + id);
        c.setDeleted(deleted);
        return c;
    }

    // ==================================================================
    // 1. doGet tests
    // ==================================================================

    @Test
    public void doGet_unauthorized_returns403() throws ServletException, IOException {
        when(permissionService.canViewContracts(adminUser)).thenReturn(false);
        controller.doGet(request, response);
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void doGet_nullUser_returns403() throws ServletException, IOException {
        when(session.getAttribute("currentUser")).thenReturn(null);
        when(permissionService.canViewContracts(null)).thenReturn(false);
        controller.doGet(request, response);
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void doGet_missingContractId_redirectsToList() throws ServletException, IOException {
        when(request.getParameter("contractId")).thenReturn(null);
        when(request.getParameter("id")).thenReturn(null);

        controller.doGet(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
    }

    @Test
    public void doGet_contractNotFoundOrDeleted_redirectsToNotFound() throws ServletException, IOException {
        when(request.getParameter("contractId")).thenReturn("10");
        when(contractDAO.findById(10)).thenReturn(null);

        controller.doGet(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        // Khi bị xóa mềm
        Contract deleted = sampleContract(10, true);
        when(contractDAO.findById(10)).thenReturn(deleted);
        controller.doGet(request, response);
        verify(response, times(2)).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void doGet_validContract_redirectsToEdit() throws ServletException, IOException {
        Contract c = sampleContract(10, false);
        when(request.getParameter("contractId")).thenReturn("10");
        when(contractDAO.findById(10)).thenReturn(c);

        controller.doGet(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=10");
    }

    @Test
    public void doGet_validContractViewAction_redirectsToView() throws ServletException, IOException {
        Contract c = sampleContract(10, false);
        when(request.getParameter("contractId")).thenReturn("10");
        when(request.getParameter("action")).thenReturn("view");
        when(contractDAO.findById(10)).thenReturn(c);

        controller.doGet(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=10");
    }

    // ==================================================================
    // 2. doPost routing & default
    // ==================================================================

    @Test
    public void doPost_nullOrDefaultAction_redirectsToList() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn(null);
        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract");

        when(request.getParameter("action")).thenReturn("unknownAction");
        controller.doPost(request, response);
        verify(response, times(2)).sendRedirect(CONTEXT_PATH + "/contract");
    }

    // ==================================================================
    // 3. handleLinkContract tests (UC-47)
    // ==================================================================

    @Test
    public void doPost_linkContract_success() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("linkContract");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("otherContractId")).thenReturn("9");
        when(request.getParameter("linkNote")).thenReturn("Nối phụ lục đầu vào");
        when(linkService.linkContracts(5, 9, "Nối phụ lục đầu vào", adminUser))
                .thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5");
    }

    @Test
    public void doPost_linkContract_nonNumericOtherContractId_redirectsInvalid() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("linkContract");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("otherContractId")).thenReturn("abc");
        when(linkService.linkContracts(eq(5), eq(0), any(), eq(adminUser)))
                .thenReturn(ServiceResult.fail(ContractLinkService.ERR_INVALID));

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=link_invalid");
    }

    @Test
    public void doPost_linkContract_missingOtherContractId_redirectsInvalid() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("linkContract");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("otherContractId")).thenReturn(null);
        when(linkService.linkContracts(eq(5), eq(0), any(), eq(adminUser)))
                .thenReturn(ServiceResult.fail(ContractLinkService.ERR_INVALID));

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=link_invalid");
    }

    @Test
    public void doPost_linkContract_usingIdParamInsteadOfContractId_succeeds() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("linkContract");
        when(request.getParameter("contractId")).thenReturn(null);
        when(request.getParameter("id")).thenReturn("5");
        when(request.getParameter("otherContractId")).thenReturn("9");
        when(linkService.linkContracts(5, 9, null, adminUser)).thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5");
    }

    @Test
    public void doPost_linkContract_missingContractId_redirectsToNotFound() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("linkContract");
        when(request.getParameter("contractId")).thenReturn("");
        when(request.getParameter("id")).thenReturn(null);

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void doPost_linkContract_serviceForbidden_returns403() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("linkContract");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("otherContractId")).thenReturn("9");
        when(linkService.linkContracts(5, 9, null, adminUser))
                .thenReturn(ServiceResult.fail(ContractLinkService.ERR_FORBIDDEN));

        controller.doPost(request, response);
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void doPost_linkContract_serviceNotFound_redirectsToNotFound() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("linkContract");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("otherContractId")).thenReturn("9");
        when(linkService.linkContracts(5, 9, null, adminUser))
                .thenReturn(ServiceResult.fail(ContractLinkService.ERR_NOT_FOUND));

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void doPost_linkContract_serviceDuplicate_redirectsWithError() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("linkContract");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("otherContractId")).thenReturn("9");
        when(linkService.linkContracts(5, 9, null, adminUser))
                .thenReturn(ServiceResult.fail(ContractLinkService.ERR_DUPLICATE));

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=link_duplicate");
    }

    @Test
    public void doPost_linkContract_serviceInvalid_redirectsWithError() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("linkContract");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("otherContractId")).thenReturn("9");
        when(linkService.linkContracts(5, 9, null, adminUser))
                .thenReturn(ServiceResult.fail(ContractLinkService.ERR_INVALID));

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=link_invalid");
    }

    // ==================================================================
    // 4. handleUnlinkContract tests (UC-47)
    // ==================================================================

    @Test
    public void doPost_unlinkContract_success() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("unlinkContract");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("linkId")).thenReturn("42");
        when(linkService.unlinkContracts(5, 42, adminUser)).thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5");
    }

    @Test
    public void doPost_unlinkContract_usingIdParamInsteadOfContractId_succeeds() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("unlinkContract");
        when(request.getParameter("contractId")).thenReturn(null);
        when(request.getParameter("id")).thenReturn("5");
        when(request.getParameter("linkId")).thenReturn("42");
        when(linkService.unlinkContracts(5, 42, adminUser)).thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5");
    }

    @Test
    public void doPost_unlinkContract_nonNumericLinkId_redirectsToNotFound() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("unlinkContract");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("linkId")).thenReturn("xyz");

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void doPost_unlinkContract_missingIds_redirectsToNotFound() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("unlinkContract");
        when(request.getParameter("contractId")).thenReturn("");
        when(request.getParameter("linkId")).thenReturn("42");

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");

        // Missing linkId
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("linkId")).thenReturn("");
        controller.doPost(request, response);
        verify(response, times(2)).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void doPost_unlinkContract_serviceForbidden_returns403() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("unlinkContract");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("linkId")).thenReturn("42");
        when(linkService.unlinkContracts(5, 42, adminUser))
                .thenReturn(ServiceResult.fail(ContractLinkService.ERR_FORBIDDEN));

        controller.doPost(request, response);
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void doPost_unlinkContract_serviceNotFound_redirectsToNotFound() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("unlinkContract");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("linkId")).thenReturn("42");
        when(linkService.unlinkContracts(5, 42, adminUser))
                .thenReturn(ServiceResult.fail(ContractLinkService.ERR_NOT_FOUND));

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void doPost_unlinkContract_serviceFailed_redirectsWithError() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("unlinkContract");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("linkId")).thenReturn("42");
        when(linkService.unlinkContracts(5, 42, adminUser))
                .thenReturn(ServiceResult.fail(ContractLinkService.ERR_UNLINK_FAILED));

        controller.doPost(request, response);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=unlink_failed");
    }

    // ==================================================================
    // 5. Getters, Setters, Constructors & DI
    // ==================================================================

    @Test
    public void constructors_and_getters_setters() {
        ContractLinkController c1 = new ContractLinkController();
        assertNotNull(c1.getContractDAO());
        assertNotNull(c1.getEmployeeDAO());
        assertNotNull(c1.getPermissionService());

        c1.setPermissionService(permissionService);
        c1.setContractDAO(contractDAO);
        ContractLinkService ls = c1.getLinkService();
        assertNotNull(ls);
        assertSame(ls, c1.getLinkService());

        ContractLinkController c2 = new ContractLinkController(contractDAO, employeeDAO, permissionService);
        assertSame(contractDAO, c2.getContractDAO());
        assertSame(employeeDAO, c2.getEmployeeDAO());
        assertSame(permissionService, c2.getPermissionService());

        ContractLinkController c3 = new ContractLinkController(contractDAO, employeeDAO, linkService, permissionService);
        assertSame(linkService, c3.getLinkService());

        ContractDAO newDao = mock(ContractDAO.class);
        c3.setContractDAO(newDao);
        assertSame(newDao, c3.getContractDAO());

        EmployeeDAO newEmpDao = mock(EmployeeDAO.class);
        c3.setEmployeeDAO(newEmpDao);
        assertSame(newEmpDao, c3.getEmployeeDAO());

        PermissionService newPs = mock(PermissionService.class);
        c3.setPermissionService(newPs);
        assertSame(newPs, c3.getPermissionService());

        ContractLinkService newLs = mock(ContractLinkService.class);
        c3.setLinkService(newLs);
        assertSame(newLs, c3.getLinkService());
    }
}
