package poscs.controller;

import java.util.List;
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
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Enterprise;
import poscs.model.Role;
import poscs.model.User;
import poscs.service.CustomerEvaluationService;
import poscs.service.CustomerService;
import poscs.service.PermissionService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho {@link CustomerDetailController} (UC-09).
 */
public class CustomerDetailControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private CustomerDetailController controller;
    private CustomerDAO customerDAO;
    private ContractDAO contractDAO;
    private TechnicalSupportTicketDAO ticketDAO;
    private EmployeeDAO employeeDAO;
    private AddressDAO addressDAO;
    private CustomerService customerService;
    private CustomerEvaluationService customerEvaluationService;
    private PermissionService permissionService;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    @Before
    public void setUp() {
        customerDAO = mock(CustomerDAO.class);
        contractDAO = mock(ContractDAO.class);
        ticketDAO = mock(TechnicalSupportTicketDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        addressDAO = mock(AddressDAO.class);
        customerService = mock(CustomerService.class);
        customerEvaluationService = mock(CustomerEvaluationService.class);
        permissionService = mock(PermissionService.class);

        controller = new CustomerDetailController(customerDAO, contractDAO, ticketDAO, employeeDAO, addressDAO,
                customerService, customerEvaluationService, permissionService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        when(request.getRequestDispatcher(CustomerControllerHelper.DETAIL_VIEW)).thenReturn(dispatcher);
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

    @Test
    public void detail_withoutPermission_returns403Msg022() throws Exception {
        User user = loginAs("Unknown", null);
        when(permissionService.canViewCustomers(user)).thenReturn(false);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(customerService, never()).findById(anyInt());
    }

    @Test
    public void detail_customerNotFound_redirectsToNotFoundUrl() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canViewCustomers(user)).thenReturn(true);
        when(request.getParameter("id")).thenReturn("999");
        when(customerService.findById(999)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?error=notfound");
    }

    @Test
    public void detail_customerFound_forwardsWithAllAttributes() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canViewCustomers(user)).thenReturn(true);
        when(permissionService.canManageCustomers(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(true);

        when(request.getParameter("id")).thenReturn("5");
        Enterprise ent = new Enterprise();
        ent.setEnterpriseId(5);
        when(customerService.findById(5)).thenReturn(ent);
        when(customerService.findRolesOf(5)).thenReturn(List.of(PermissionService.ROLE_BUYER));
        when(permissionService.isCustomerInScope(eq(user), eq(ent), any())).thenReturn(true);

        controller.doGet(request, response);

        verify(request).setAttribute("customer", ent);
        verify(request).setAttribute("canManage", true);
        verify(request).setAttribute("canWrite", true);
        verify(request).setAttribute("outOfScope", false);
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void detail_salesOutsideScope_canWriteFalseOutOfScopeTrue() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canViewCustomers(user)).thenReturn(true);
        when(permissionService.canManageCustomers(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(true);

        when(request.getParameter("id")).thenReturn("6");
        Enterprise ent = new Enterprise();
        ent.setEnterpriseId(6);
        when(customerService.findById(6)).thenReturn(ent);
        when(customerService.findRolesOf(6)).thenReturn(List.of(PermissionService.ROLE_BUYER));
        when(permissionService.isCustomerInScope(eq(user), eq(ent), any())).thenReturn(false);

        controller.doGet(request, response);

        verify(request).setAttribute("canManage", true);
        verify(request).setAttribute("canWrite", false);
        verify(request).setAttribute("outOfScope", true);
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void detail_admin_canWriteTrue() throws Exception {
        User user = loginAs(PermissionService.ROLE_ADMIN, null);
        when(permissionService.canViewCustomers(user)).thenReturn(true);
        when(permissionService.canManageCustomers(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(false);

        when(request.getParameter("id")).thenReturn("6");
        Enterprise ent = new Enterprise();
        ent.setEnterpriseId(6);
        when(customerService.findById(6)).thenReturn(ent);

        controller.doGet(request, response);

        verify(request).setAttribute("canManage", true);
        verify(request).setAttribute("canWrite", true);
        verify(request).setAttribute("outOfScope", false);
        verify(dispatcher).forward(request, response);
    }
}
