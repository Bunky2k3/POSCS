package poscs.controller;

import java.util.List;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import poscs.common.FileStorage;
import poscs.dao.AddressDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.CustomerLifecycleEvent;
import poscs.model.Enterprise;
import poscs.model.Role;
import poscs.model.User;
import poscs.service.CustomerEvaluationService;
import poscs.service.CustomerService;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho {@link CustomerActionController} (UC-30, UC-31, UC-32, UC-38, BR-34).
 */
public class CustomerActionControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private CustomerActionController controller;
    private CustomerDAO customerDAO;
    private EmployeeDAO employeeDAO;
    private AddressDAO addressDAO;
    private CustomerService customerService;
    private CustomerEvaluationService customerEvaluationService;
    private PermissionService permissionService;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher createDispatcher;
    private RequestDispatcher updateDispatcher;

    @Before
    public void setUp() {
        customerDAO = mock(CustomerDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        addressDAO = mock(AddressDAO.class);
        customerService = mock(CustomerService.class);
        customerEvaluationService = mock(CustomerEvaluationService.class);
        permissionService = mock(PermissionService.class);

        controller = new CustomerActionController(customerDAO, employeeDAO, addressDAO,
                customerService, customerEvaluationService, permissionService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        createDispatcher = mock(RequestDispatcher.class);
        updateDispatcher = mock(RequestDispatcher.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        when(request.getRequestDispatcher(CustomerControllerHelper.CREATE_VIEW)).thenReturn(createDispatcher);
        when(request.getRequestDispatcher(CustomerControllerHelper.UPDATE_VIEW)).thenReturn(updateDispatcher);
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

    // ------------------------------------------------------------------
    // showCreateForm / showEditForm
    // ------------------------------------------------------------------

    @Test
    public void createForm_withoutPermission_returns403Msg022() throws Exception {
        User user = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canCreateCustomer(user)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("new");

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(createDispatcher, never()).forward(request, response);
    }

    @Test
    public void createForm_salesRegionalManager_forwardsToCreateView() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateCustomer(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("new");
        when(request.getParameter("kind")).thenReturn("buyer");

        controller.doGet(request, response);

        verify(request).setAttribute("kind", "buyer");
        verify(createDispatcher).forward(request, response);
    }

    @Test
    public void editForm_withoutPermission_returns403Msg022() throws Exception {
        User user = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canUpdateCustomer(user)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("edit");

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(updateDispatcher, never()).forward(request, response);
    }

    @Test
    public void editForm_customerNotFound_redirectsToNotFound() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateCustomer(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("888");
        when(customerService.findById(888)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?error=notfound");
    }

    @Test
    public void editForm_salesOutsideScope_redirectsNotYourCustomer() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateCustomer(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("5");

        Enterprise ent = new Enterprise();
        ent.setEnterpriseId(5);
        when(customerService.findById(5)).thenReturn(ent);
        when(customerService.findRolesOf(5)).thenReturn(List.of(PermissionService.ROLE_BUYER));
        when(permissionService.isCustomerInScope(eq(user), eq(ent), any())).thenReturn(false);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=5&error=not_your_customer");
    }

    @Test
    public void editForm_salesInsideScope_forwardsToUpdateView() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateCustomer(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("5");

        Enterprise ent = new Enterprise();
        ent.setEnterpriseId(5);
        when(customerService.findById(5)).thenReturn(ent);
        when(customerService.findRolesOf(5)).thenReturn(List.of(PermissionService.ROLE_BUYER));
        when(permissionService.isCustomerInScope(eq(user), eq(ent), any())).thenReturn(true);

        controller.doGet(request, response);

        verify(request).setAttribute("customer", ent);
        verify(updateDispatcher).forward(request, response);
    }

    // ------------------------------------------------------------------
    // handleCreate (UC-30)
    // ------------------------------------------------------------------

    @Test
    public void create_withoutPermission_returns403Msg022() throws Exception {
        User user = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canCreateCustomer(user)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("create");

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(customerService, never()).prepareAndValidateCreate(any(), any(), any(), anyBoolean(), any(), any(), any(), any());
    }

    @Test
    public void create_invalidLogo_redirectsInvalidImageType() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateCustomer(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("kind")).thenReturn("buyer");

        try (MockedStatic<FileStorage> fs = mockStatic(FileStorage.class)) {
            fs.when(() -> FileStorage.isAcceptable(any(), any())).thenReturn(false);

            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=invalid_image_type");
            verify(customerService, never()).insertCustomer(any(), any());
        }
    }

    @Test
    public void create_validationFailed_redirectsWithError() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateCustomer(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("kind")).thenReturn("buyer");

        try (MockedStatic<FileStorage> fs = mockStatic(FileStorage.class)) {
            fs.when(() -> FileStorage.isAcceptable(any(), any())).thenReturn(true);
            when(customerService.prepareAndValidateCreate(any(), any(), any(), anyBoolean(), any(), any(), any(), any()))
                    .thenReturn(ServiceResult.fail("invalid_phone"));

            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=invalid_phone");
            verify(customerService, never()).insertCustomer(any(), any());
        }
    }

    @Test
    public void create_success_redirectsToDetail() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canCreateCustomer(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("kind")).thenReturn("buyer");

        try (MockedStatic<FileStorage> fs = mockStatic(FileStorage.class)) {
            fs.when(() -> FileStorage.isAcceptable(any(), any())).thenReturn(true);
            when(customerService.prepareAndValidateCreate(any(), any(), any(), anyBoolean(), any(), any(), any(), any()))
                    .thenReturn(ServiceResult.ok(new Enterprise()));
            when(customerService.insertCustomer(any(), any())).thenReturn(ServiceResult.ok(10));

            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=10");
        }
    }

    // ------------------------------------------------------------------
    // handleUpdate (UC-31)
    // ------------------------------------------------------------------

    @Test
    public void update_withoutPermission_returns403Msg022() throws Exception {
        User user = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canUpdateCustomer(user)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("update");

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(customerService, never()).prepareAndValidateUpdate(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any());
    }

    @Test
    public void update_customerNotFound_redirectsToNotFound() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateCustomer(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("customerId")).thenReturn("777");

        try (MockedStatic<FileStorage> fs = mockStatic(FileStorage.class)) {
            fs.when(() -> FileStorage.isAcceptable(any(), any())).thenReturn(true);
            when(customerService.prepareAndValidateUpdate(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any()))
                    .thenReturn(ServiceResult.fail("notfound"));

            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/customer?error=notfound");
        }
    }

    @Test
    public void update_success_redirectsToDetail() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canUpdateCustomer(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("customerId")).thenReturn("5");

        try (MockedStatic<FileStorage> fs = mockStatic(FileStorage.class)) {
            fs.when(() -> FileStorage.isAcceptable(any(), any())).thenReturn(true);
            Enterprise existing = new Enterprise();
            existing.setLogoUrl("/old.png");
            CustomerService.UpdatePreparedState state = new CustomerService.UpdatePreparedState(existing, List.of(PermissionService.ROLE_BUYER), existing);
            when(customerService.prepareAndValidateUpdate(any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any()))
                    .thenReturn(ServiceResult.ok(state));
            when(customerService.commitUpdate(any(), any())).thenReturn(ServiceResult.ok(existing));

            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=5");
        }
    }

    // ------------------------------------------------------------------
    // handleDelete (UC-32, BR-34)
    // ------------------------------------------------------------------

    @Test
    public void delete_withoutPermission_returns403Msg022_BR34() throws Exception {
        User user = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canDeleteCustomer(user)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(customerService, never()).deleteCustomer(anyInt(), any(), anyBoolean());
    }

    @Test
    public void delete_activeContracts_blocksDeletion_BR34() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canDeleteCustomer(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");

        when(customerService.deleteCustomer(eq(5), eq(user), eq(true)))
                .thenReturn(ServiceResult.fail("has_active_contracts"));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=5&error=has_active_contracts");
    }

    @Test
    public void delete_success_redirectsToList_BR34() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canDeleteCustomer(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");
        when(request.getParameter("kind")).thenReturn("buyer");

        when(customerService.deleteCustomer(eq(5), eq(user), eq(true)))
                .thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?kind=buyer");
    }

    @Test
    public void delete_supplierKind_redirectsToSupplierList() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canDeleteCustomer(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");
        when(request.getParameter("kind")).thenReturn("supplier");

        when(customerService.deleteCustomer(eq(5), eq(user), eq(true)))
                .thenReturn(ServiceResult.ok(null));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?kind=supplier");
    }

    @Test
    public void delete_customerNotFound_redirectsToNotFoundUrl() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canDeleteCustomer(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("999");

        when(customerService.deleteCustomer(eq(999), eq(user), eq(true)))
                .thenReturn(ServiceResult.fail("notfound"));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?error=notfound");
    }

    @Test
    public void delete_notYourCustomer_redirectsToDetailWithNotYourCustomer() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canDeleteCustomer(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");

        when(customerService.deleteCustomer(eq(5), eq(user), eq(true)))
                .thenReturn(ServiceResult.fail("not_your_customer"));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=5&error=not_your_customer");
    }

    // ------------------------------------------------------------------
    // handleEvaluate (UC-38)
    // ------------------------------------------------------------------

    @Test
    public void evaluate_withoutPermission_returns403Msg022() throws Exception {
        User user = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canRateCustomer(user)).thenReturn(false);
        when(request.getParameter("action")).thenReturn("evaluate");

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(customerEvaluationService, never()).evaluateCustomer(anyInt(), any(String.class), any(), any(), anyBoolean());
    }

    @Test
    public void evaluate_success_redirectsToDetailWithEvaluated() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canRateCustomer(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("evaluate");
        when(request.getParameter("id")).thenReturn("5");
        when(request.getParameter("rating")).thenReturn("GOOD");

        when(customerEvaluationService.evaluateCustomer(eq(5), eq("GOOD"), any(), eq(user), eq(true)))
                .thenReturn(ServiceResult.ok(new CustomerLifecycleEvent()));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=5&evaluated=1");
    }

    @Test
    public void evaluate_customerNotFound_redirectsToNotFoundUrl() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canRateCustomer(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("evaluate");
        when(request.getParameter("id")).thenReturn("999");
        when(request.getParameter("rating")).thenReturn("GOOD");

        when(customerEvaluationService.evaluateCustomer(eq(999), eq("GOOD"), any(), eq(user), eq(true)))
                .thenReturn(ServiceResult.fail("notfound"));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?error=notfound");
    }

    @Test
    public void evaluate_failed_redirectsToDetailWithError() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canRateCustomer(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(true);
        when(request.getParameter("action")).thenReturn("evaluate");
        when(request.getParameter("id")).thenReturn("5");
        when(request.getParameter("rating")).thenReturn("GOOD");

        when(customerEvaluationService.evaluateCustomer(eq(5), eq("GOOD"), any(), eq(user), eq(true)))
                .thenReturn(ServiceResult.fail("evaluate_failed"));

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=5&error=evaluate_failed");
    }
}
