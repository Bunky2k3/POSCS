package poscs.controller;

import java.sql.Date;
import java.time.LocalDate;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.ProductDAO;
import poscs.model.Contract;
import poscs.model.Role;
import poscs.model.User;
import poscs.service.ContractService;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test toàn diện cho {@link ContractActionController} (Task 4.12).
 * Đáp ứng: UC-33, UC-34, UC-35, UC-44, UC-46, UC-54 và BR-17, BR-35, BR-36, BR-37.
 */
public class ContractActionControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private ContractActionController controller;
    private ContractDAO contractDAO;
    private CustomerDAO customerDAO;
    private EmployeeDAO employeeDAO;
    private AddressDAO addressDAO;
    private ProductDAO productDAO;
    private ContractService contractService;
    private PermissionService permissionService;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher createDispatcher;
    private RequestDispatcher updateDispatcher;

    @Before
    public void setUp() {
        contractDAO = mock(ContractDAO.class);
        customerDAO = mock(CustomerDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        addressDAO = mock(AddressDAO.class);
        productDAO = mock(ProductDAO.class);
        contractService = mock(ContractService.class);
        permissionService = mock(PermissionService.class);

        controller = new ContractActionController(contractDAO, customerDAO, employeeDAO, addressDAO, productDAO,
                contractService, permissionService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        createDispatcher = mock(RequestDispatcher.class);
        updateDispatcher = mock(RequestDispatcher.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        when(request.getRequestDispatcher(ContractControllerHelper.CREATE_VIEW)).thenReturn(createDispatcher);
        when(request.getRequestDispatcher(ContractControllerHelper.UPDATE_VIEW)).thenReturn(updateDispatcher);

        loginAs(PermissionService.ROLE_SALES, null);
    }

    private User loginAs(String roleName, Integer managerId) {
        User user = new User();
        user.setUserId(99);
        user.setFirstName("User");
        user.setLastName("99");
        user.setRole(new Role(1, roleName));
        user.setManagerId(managerId);
        when(session.getAttribute("currentUser")).thenReturn(user);
        when(employeeDAO.findById(99)).thenReturn(user);
        return user;
    }

    private Contract draftContract(int id) {
        Contract c = new Contract();
        c.setContractId(id);
        c.setContractCode("HD-00" + id);
        c.setTitle("Hợp đồng nháp " + id);
        c.setContractType("Cung cấp thiết bị");
        c.setDirection(ContractService.DIRECTION_SELL);
        c.setProgressStatus(ContractService.PROGRESS_DRAFT);
        c.setEffectiveDate(Date.valueOf(LocalDate.of(2026, 1, 1)));
        c.setEndDate(Date.valueOf(LocalDate.of(2026, 12, 31)));
        c.setEnterpriseId(10);
        c.setOwnerId(99);
        return c;
    }

    private Contract signedContract(int id) {
        Contract c = draftContract(id);
        c.setProgressStatus(ContractService.PROGRESS_SIGNED);
        c.setSigningDate(Date.valueOf(LocalDate.of(2026, 1, 1)));
        return c;
    }

    // ------------------------------------------------------------------
    // doGet routing & form views
    // ------------------------------------------------------------------

    @Test
    public void doGet_actionNew_withoutPermission_returns403() throws Exception {
        User u = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canCreateContract(u)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("new");

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(createDispatcher, never()).forward(any(), any());
    }

    @Test
    public void doGet_actionNew_withPermission_forwardsToCreateView() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("new");
        when(request.getParameter("kind")).thenReturn("buy");

        controller.doGet(request, response);

        verify(request).setAttribute(eq("direction"), eq(ContractService.DIRECTION_BUY));
        verify(request).setAttribute(eq("kind"), eq(ContractService.KIND_BUY));
        verify(createDispatcher).forward(request, response);
    }

    @Test
    public void doGet_actionNewAmendment_withoutPermission_returns403() throws Exception {
        User u = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canCreateAmendment(u)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("newAmendment");

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void doGet_actionNewAmendment_parentNotFound_redirectsToNotFound() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateAmendment(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("newAmendment");
        when(request.getParameter("parentId")).thenReturn("999");
        when(contractService.findById(999)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void doGet_actionNewAmendment_parentNotSigned_redirectsWithAmendmentNotAllowed() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateAmendment(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("newAmendment");
        when(request.getParameter("parentId")).thenReturn("1");
        when(contractService.findById(1)).thenReturn(draftContract(1));

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=1&error=amendment_not_allowed");
    }

    @Test
    public void doGet_actionNewAmendment_validParent_forwardsToCreateView() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateAmendment(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("newAmendment");
        when(request.getParameter("parentId")).thenReturn("1");
        Contract parent = signedContract(1);
        when(contractService.findById(1)).thenReturn(parent);

        controller.doGet(request, response);

        verify(request).setAttribute("parentContract", parent);
        verify(createDispatcher).forward(request, response);
    }

    @Test
    public void doGet_actionEdit_withoutPermission_returns403() throws Exception {
        User u = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canUpdateContract(u)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("edit");

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void doGet_actionEdit_contractNotFound_redirectsToNotFound() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("888");
        when(contractService.findById(888)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void doGet_actionEdit_validContract_forwardsToUpdateView() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(permissionService.canSignContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("5");
        Contract c = draftContract(5);
        when(contractService.findById(5)).thenReturn(c);

        controller.doGet(request, response);

        verify(request).setAttribute("contract", c);
        verify(request).setAttribute(eq("canEditProducts"), eq(true));
        verify(updateDispatcher).forward(request, response);
    }

    @Test
    public void doGet_unknownAction_redirectsToList() throws Exception {
        when(request.getParameter("action")).thenReturn("someUnknown");

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
    }

    // ------------------------------------------------------------------
    // handleCreate (UC-33, BR-35)
    // ------------------------------------------------------------------

    @Test
    public void handleCreate_withoutPermission_returns403() throws Exception {
        User u = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canCreateContract(u)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("create");

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(contractService, never()).createContract(any(), any());
    }

    @Test
    public void handleCreate_duplicateCode_redirectsWithDuplicateCodeError() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("contractCode")).thenReturn("01/2026/HĐKT");
        when(contractService.createContract(any(), eq(u))).thenReturn(ServiceResult.fail(ContractService.ERR_DUPLICATE_CODE));

        controller.doPost(request, response);

        verify(response).sendRedirect(contains("error=duplicate_code"));
    }

    @Test
    public void handleCreate_invalid_redirectsWithInvalidError() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("create");
        when(contractService.createContract(any(), eq(u))).thenReturn(ServiceResult.fail(ContractService.ERR_INVALID));

        controller.doPost(request, response);

        verify(response).sendRedirect(contains("error=invalid"));
    }

    @Test
    public void handleCreate_createFailed_redirectsWithCreateFailedError() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("create");
        when(contractService.createContract(any(), eq(u))).thenReturn(ServiceResult.fail(ContractService.ERR_CREATE_FAILED));

        controller.doPost(request, response);

        verify(response).sendRedirect(contains("error=create_failed"));
    }

    @Test
    public void handleCreate_success_redirectsToDetail() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("contractCode")).thenReturn("01/2026/HĐKT");
        when(contractService.createContract(any(), eq(u))).thenReturn(ServiceResult.ok(105));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=105");
    }

    // ------------------------------------------------------------------
    // handleUpdate (UC-35, BR-35, BR-36)
    // ------------------------------------------------------------------

    @Test
    public void handleUpdate_withoutPermission_returns403() throws Exception {
        User u = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canUpdateContract(u)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("update");

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void handleUpdate_contractNotFound_redirectsToNotFound() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("contractId")).thenReturn("55");
        when(contractService.findById(55)).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void handleUpdate_termsLocked_ownerIdMissing_redirectsWithInvalidError() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("contractId")).thenReturn("5");
        Contract signed = signedContract(5);
        when(contractService.findById(5)).thenReturn(signed);
        when(request.getParameter("ownerId")).thenReturn("");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=invalid");
    }

    @Test
    public void handleUpdate_termsLocked_updatesOwner_redirectsToDetail() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("ownerId")).thenReturn("88");
        Contract signed = signedContract(5);
        when(contractService.findById(5)).thenReturn(signed);
        when(contractService.updateContractOwner(5, 88, u)).thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(contractService).updateContractOwner(5, 88, u);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5");
    }

    @Test
    public void handleUpdate_draftContract_success_redirectsToDetail() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("contractId")).thenReturn("5");
        Contract draft = draftContract(5);
        when(contractService.findById(5)).thenReturn(draft);
        when(contractService.updateContract(any(), eq(u))).thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(contractService).updateContract(any(), eq(u));
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5");
    }

    @Test
    public void handleUpdate_valueNegative_redirectsWithValueNegativeError() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractService.findById(5)).thenReturn(draftContract(5));
        when(contractService.updateContract(any(), eq(u))).thenReturn(ServiceResult.fail(ContractService.ERR_VALUE_NEGATIVE));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=value_negative");
    }

    @Test
    public void handleUpdate_duplicateCode_redirectsWithDuplicateCodeError() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractService.findById(5)).thenReturn(draftContract(5));
        when(contractService.updateContract(any(), eq(u))).thenReturn(ServiceResult.fail(ContractService.ERR_DUPLICATE_CODE));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=duplicate_code");
    }

    @Test
    public void handleUpdate_updateFailed_redirectsWithUpdateFailedError() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractService.findById(5)).thenReturn(draftContract(5));
        when(contractService.updateContract(any(), eq(u))).thenReturn(ServiceResult.fail(ContractService.ERR_UPDATE_FAILED));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=update_failed");
    }

    // ------------------------------------------------------------------
    // handleCreateAmendment (UC-46)
    // ------------------------------------------------------------------

    @Test
    public void handleCreateAmendment_withoutPermission_returns403() throws Exception {
        User u = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canCreateAmendment(u)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("createAmendment");

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void handleCreateAmendment_parentNotFound_redirectsToNotFound() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateAmendment(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("createAmendment");
        when(request.getParameter("parentId")).thenReturn("99");
        when(contractService.findById(99)).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void handleCreateAmendment_amendmentNotAllowed_redirectsToParentView() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateAmendment(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("createAmendment");
        when(request.getParameter("parentId")).thenReturn("1");
        when(contractService.findById(1)).thenReturn(signedContract(1));
        when(contractService.createAmendment(eq(1), any(), eq(u)))
                .thenReturn(ServiceResult.fail(ContractService.ERR_AMENDMENT_NOT_ALLOWED));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=1&error=amendment_not_allowed");
    }

    @Test
    public void handleCreateAmendment_valueNegative_redirectsWithValueNegativeError() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateAmendment(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("createAmendment");
        when(request.getParameter("parentId")).thenReturn("1");
        when(contractService.findById(1)).thenReturn(signedContract(1));
        when(contractService.createAmendment(eq(1), any(), eq(u)))
                .thenReturn(ServiceResult.fail(ContractService.ERR_VALUE_NEGATIVE));

        controller.doPost(request, response);

        verify(response).sendRedirect(contains("error=value_negative"));
    }

    @Test
    public void handleCreateAmendment_duplicateCode_redirectsWithDuplicateCodeError() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateAmendment(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("createAmendment");
        when(request.getParameter("parentId")).thenReturn("1");
        when(contractService.findById(1)).thenReturn(signedContract(1));
        when(contractService.createAmendment(eq(1), any(), eq(u)))
                .thenReturn(ServiceResult.fail(ContractService.ERR_DUPLICATE_CODE));

        controller.doPost(request, response);

        verify(response).sendRedirect(contains("error=duplicate_code"));
    }

    @Test
    public void handleCreateAmendment_invalid_redirectsWithInvalidError() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateAmendment(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("createAmendment");
        when(request.getParameter("parentId")).thenReturn("1");
        when(contractService.findById(1)).thenReturn(signedContract(1));
        when(contractService.createAmendment(eq(1), any(), eq(u)))
                .thenReturn(ServiceResult.fail(ContractService.ERR_INVALID));

        controller.doPost(request, response);

        verify(response).sendRedirect(contains("error=invalid"));
    }

    @Test
    public void handleCreateAmendment_createFailed_redirectsWithCreateFailedError() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateAmendment(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("createAmendment");
        when(request.getParameter("parentId")).thenReturn("1");
        when(contractService.findById(1)).thenReturn(signedContract(1));
        when(contractService.createAmendment(eq(1), any(), eq(u)))
                .thenReturn(ServiceResult.fail(ContractService.ERR_CREATE_FAILED));

        controller.doPost(request, response);

        verify(response).sendRedirect(contains("error=create_failed"));
    }

    @Test
    public void handleCreateAmendment_success_redirectsToDetail() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateAmendment(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("createAmendment");
        when(request.getParameter("parentId")).thenReturn("1");
        when(contractService.findById(1)).thenReturn(signedContract(1));
        when(contractService.createAmendment(eq(1), any(), eq(u))).thenReturn(ServiceResult.ok(201));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=201");
    }

    // ------------------------------------------------------------------
    // handleCorrect (UC-54)
    // ------------------------------------------------------------------

    @Test
    public void handleCorrect_nonAdmin_returns403() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.isAdmin(u)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("correct");

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(contractService, never()).correctSignedContract(any(), any(), any());
    }

    @Test
    public void handleCorrect_contractNotFound_redirectsToNotFound() throws Exception {
        User u = loginAs(PermissionService.ROLE_ADMIN, null);
        when(permissionService.isAdmin(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("correct");
        when(request.getParameter("contractId")).thenReturn("999");
        when(contractService.findById(999)).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void handleCorrect_missingReason_redirectsWithMissingReasonError() throws Exception {
        User u = loginAs(PermissionService.ROLE_ADMIN, null);
        when(permissionService.isAdmin(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("correct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractService.findById(5)).thenReturn(signedContract(5));
        when(request.getParameter("correctionReason")).thenReturn("   ");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=missing_reason");
        verify(contractService, never()).correctSignedContract(any(), any(), any());
    }

    @Test
    public void handleCorrect_valid_redirectsToDetail() throws Exception {
        User u = loginAs(PermissionService.ROLE_ADMIN, null);
        when(permissionService.isAdmin(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("correct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractService.findById(5)).thenReturn(signedContract(5));
        when(request.getParameter("correctionReason")).thenReturn("Sửa mã hợp đồng gõ nhầm");
        when(contractService.correctSignedContract(any(), eq(u), eq("Sửa mã hợp đồng gõ nhầm"))).thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(contractService).correctSignedContract(any(), eq(u), eq("Sửa mã hợp đồng gõ nhầm"));
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5");
    }

    @Test
    public void handleCorrect_updateFailed_redirectsWithUpdateFailedError() throws Exception {
        User u = loginAs(PermissionService.ROLE_ADMIN, null);
        when(permissionService.isAdmin(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("correct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractService.findById(5)).thenReturn(signedContract(5));
        when(request.getParameter("correctionReason")).thenReturn("Sửa mã hợp đồng");
        when(contractService.correctSignedContract(any(), eq(u), anyString()))
                .thenReturn(ServiceResult.fail(ContractService.ERR_UPDATE_FAILED));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=update_failed");
    }

    // ------------------------------------------------------------------
    // handleChangeProgress (UC-44, BR-36)
    // ------------------------------------------------------------------

    @Test
    public void handleChangeProgress_withoutPermission_returns403() throws Exception {
        User u = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canUpdateContract(u)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("changeProgress");

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void handleChangeProgress_contractNotFound_redirectsToNotFound() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("changeProgress");
        when(request.getParameter("contractId")).thenReturn("999");
        when(contractService.findById(999)).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void handleChangeProgress_signBySubordinate_returns403() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, 2); // có managerId = 2 -> subordinate
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(permissionService.canSignContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("changeProgress");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("toStatus")).thenReturn(ContractService.PROGRESS_SIGNED);
        when(contractService.findById(5)).thenReturn(draftContract(5));

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Bạn không được ký hợp đồng. Việc này thuộc về cấp trên.");
        verify(contractService, never()).signContract(anyInt(), any(), any());
    }

    @Test
    public void handleChangeProgress_signMissingDates_redirectsWithMissingTermError() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(permissionService.canSignContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("changeProgress");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("toStatus")).thenReturn(ContractService.PROGRESS_SIGNED);
        Contract c = draftContract(5);
        c.setEffectiveDate(null);
        when(contractService.findById(5)).thenReturn(c);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5&error=missing_term");
    }

    @Test
    public void handleChangeProgress_signValid_redirectsToDetail() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(permissionService.canSignContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("changeProgress");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("toStatus")).thenReturn(ContractService.PROGRESS_SIGNED);
        when(request.getParameter("progressNote")).thenReturn("Đã ký bản cứng");
        when(contractService.findById(5)).thenReturn(draftContract(5));
        when(contractService.signContract(5, u, "Đã ký bản cứng")).thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(contractService).signContract(5, u, "Đã ký bản cứng");
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5");
    }

    @Test
    public void handleChangeProgress_progressFailed_redirectsWithProgressFailed() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(permissionService.canSignContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("changeProgress");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("toStatus")).thenReturn(ContractService.PROGRESS_SIGNED);
        when(contractService.findById(5)).thenReturn(draftContract(5));
        when(contractService.signContract(eq(5), eq(u), any()))
                .thenReturn(ServiceResult.fail(ContractService.ERR_PROGRESS_FAILED));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5&error=progress_failed");
    }

    @Test
    public void handleChangeProgress_liquidate_redirectsToDetail() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("changeProgress");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("toStatus")).thenReturn(ContractService.PROGRESS_LIQUIDATED);
        when(request.getParameter("progressNote")).thenReturn("Hoàn tất nghiệm thu và thanh lý");
        when(contractService.findById(5)).thenReturn(signedContract(5));
        when(contractService.changeProgressStatus(5, ContractService.PROGRESS_LIQUIDATED, u, "Hoàn tất nghiệm thu và thanh lý"))
                .thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(contractService).changeProgressStatus(5, ContractService.PROGRESS_LIQUIDATED, u, "Hoàn tất nghiệm thu và thanh lý");
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5");
    }

    // ------------------------------------------------------------------
    // handleDelete (UC-34, BR-37)
    // ------------------------------------------------------------------

    @Test
    public void handleDelete_withoutPermission_returns403() throws Exception {
        User u = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");
        when(contractService.findById(5)).thenReturn(draftContract(5));
        when(permissionService.canVoidContract(u, true)).thenReturn(false);

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void handleDelete_contractNotFound_redirectsToNotFound() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("999");
        when(contractService.findById(999)).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void handleDelete_signedContract_byNonAdmin_returns403() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");
        when(contractService.findById(5)).thenReturn(signedContract(5));
        when(permissionService.canVoidContract(u, false)).thenReturn(false);

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(contractService, never()).voidContract(anyInt(), any(), any());
    }

    @Test
    public void handleDelete_draftContract_missingReason_redirectsWithVoidReasonRequired() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");
        when(contractService.findById(5)).thenReturn(draftContract(5));
        when(permissionService.canVoidContract(u, true)).thenReturn(true);
        when(request.getParameter("voidReason")).thenReturn("   ");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5&error=void_reason_required");
        verify(contractService, never()).voidContract(anyInt(), any(), any());
    }

    @Test
    public void handleDelete_validDraft_redirectsToList() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");
        when(contractService.findById(5)).thenReturn(draftContract(5));
        when(permissionService.canVoidContract(u, true)).thenReturn(true);
        when(request.getParameter("voidReason")).thenReturn("Nhập trùng dữ liệu");
        when(contractService.voidContract(5, u, "Nhập trùng dữ liệu")).thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(contractService).voidContract(5, u, "Nhập trùng dữ liệu");
        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
    }

    @Test
    public void handleDelete_signedContract_byAdmin_success_redirectsToList() throws Exception {
        User u = loginAs(PermissionService.ROLE_ADMIN, null);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");
        when(contractService.findById(5)).thenReturn(signedContract(5));
        when(permissionService.canVoidContract(u, false)).thenReturn(true);
        when(request.getParameter("voidReason")).thenReturn("Bản ghi tạo thử nghiệm nhầm");
        when(contractService.voidContract(5, u, "Bản ghi tạo thử nghiệm nhầm")).thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(contractService).voidContract(5, u, "Bản ghi tạo thử nghiệm nhầm");
        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
    }

    @Test
    public void handleDelete_voidFailed_redirectsWithVoidFailed() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");
        when(contractService.findById(5)).thenReturn(draftContract(5));
        when(permissionService.canVoidContract(u, true)).thenReturn(true);
        when(request.getParameter("voidReason")).thenReturn("Lỗi nhập liệu");
        when(contractService.voidContract(eq(5), eq(u), any()))
                .thenReturn(ServiceResult.fail(ContractService.ERR_VOID_FAILED));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5&error=void_failed");
    }

    // ------------------------------------------------------------------
    // handleAddProduct & handleRemoveProduct (UC-39)
    // ------------------------------------------------------------------

    @Test
    public void handleAddProduct_withoutPermission_returns403() throws Exception {
        User u = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canUpdateContract(u)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("addProduct");

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void handleAddProduct_contractNotFound_redirectsToNotFound() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("addProduct");
        when(request.getParameter("contractId")).thenReturn("999");
        when(contractService.findById(999)).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void handleAddProduct_success_redirectsToEdit() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("addProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("productId")).thenReturn("12");
        when(request.getParameter("quantity")).thenReturn("10");
        when(request.getParameter("unit")).thenReturn("Cái");
        when(contractService.findById(5)).thenReturn(draftContract(5));
        when(contractService.addProductToContract(eq(5), eq(12), eq(10), eq("Cái"), any(), eq(u)))
                .thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5");
    }

    @Test
    public void handleAddProduct_invalid_redirectsWithAddProductInvalidError() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("addProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractService.findById(5)).thenReturn(draftContract(5));
        when(contractService.addProductToContract(anyInt(), any(), any(), any(), any(), eq(u)))
                .thenReturn(ServiceResult.fail(ContractService.ERR_ADD_PRODUCT_INVALID));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=add_product_invalid");
    }

    @Test
    public void handleAddProduct_failed_redirectsWithAddProductFailed() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("addProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractService.findById(5)).thenReturn(draftContract(5));
        when(contractService.addProductToContract(anyInt(), any(), any(), any(), any(), eq(u)))
                .thenReturn(ServiceResult.fail(ContractService.ERR_ADD_PRODUCT_FAILED));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=add_product_failed");
    }

    @Test
    public void handleRemoveProduct_withoutPermission_returns403() throws Exception {
        User u = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canUpdateContract(u)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("removeProduct");

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void handleRemoveProduct_idsMissing_redirectsToNotFound() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("removeProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("contractProductId")).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void handleRemoveProduct_success_redirectsToEdit() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("removeProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("contractProductId")).thenReturn("77");
        when(contractService.removeProductFromContract(5, 77, u)).thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5");
    }

    @Test
    public void handleRemoveProduct_failed_redirectsWithRemoveProductFailedError() throws Exception {
        User u = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateContract(u)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("removeProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("contractProductId")).thenReturn("77");
        when(contractService.removeProductFromContract(5, 77, u))
                .thenReturn(ServiceResult.fail(ContractService.ERR_REMOVE_PRODUCT_FAILED));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=remove_product_failed");
    }

    @Test
    public void doPost_unknownAction_redirectsToList() throws Exception {
        when(request.getParameter("action")).thenReturn("someUnknown");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
    }

    @Test
    public void canSign_checksPermissionAndSubordinate() {
        User user = new User();
        user.setUserId(99);
        when(permissionService.canSignContract(user)).thenReturn(true);

        // Not subordinate
        user.setManagerId(null);
        assertTrue(controller.canSign(user));

        // Subordinate
        user.setManagerId(2);
        assertFalse(controller.canSign(user));

        // No permission
        user.setManagerId(null);
        when(permissionService.canSignContract(user)).thenReturn(false);
        assertFalse(controller.canSign(user));

        // Null user
        assertFalse(controller.canSign(null));
    }
}
