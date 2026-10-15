package poscs.controller;

import java.util.List;
import java.util.Set;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.common.ListScope;
import poscs.dao.AddressDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Enterprise;
import poscs.model.Province;
import poscs.model.Role;
import poscs.model.User;
import poscs.service.CustomerService;
import poscs.service.PermissionService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho {@link CustomerListController} (UC-08, BR-12, BR-13, BR-14).
 */
public class CustomerListControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private CustomerListController controller;
    private CustomerDAO customerDAO;
    private EmployeeDAO employeeDAO;
    private AddressDAO addressDAO;
    private CustomerService customerService;
    private PermissionService permissionService;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    @Before
    public void setUp() {
        customerDAO = mock(CustomerDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        addressDAO = mock(AddressDAO.class);
        customerService = mock(CustomerService.class);
        permissionService = mock(PermissionService.class);

        controller = new CustomerListController(customerDAO, employeeDAO, addressDAO, customerService, permissionService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        when(request.getRequestDispatcher(CustomerControllerHelper.LIST_VIEW)).thenReturn(dispatcher);
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
    public void list_withoutPermission_returns403Msg022() throws Exception {
        User user = loginAs("UnknownRole", null);
        when(permissionService.canViewCustomers(user)).thenReturn(false);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(customerService, never()).findAll(anyInt(), anyInt(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    public void list_defaultPageAndPageSize_BR12() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canViewCustomers(user)).thenReturn(true);
        when(permissionService.canManageCustomers(user)).thenReturn(true);
        when(permissionService.getCustomerListScope(eq(user), any())).thenReturn(ListScope.all());

        when(request.getParameter("page")).thenReturn(null); // Không truyền page -> mặc định 1
        when(customerService.findAll(eq(1), eq(10), any(), any(), any(), any(), eq(false), any(), any()))
                .thenReturn(List.of());
        when(customerService.countAll(any(), any(), any(), any(), any(), any())).thenReturn(0);

        controller.doGet(request, response);

        verify(request).setAttribute("currentPage", 1);
        verify(request).setAttribute("pageSize", 10);
        verify(request).setAttribute("canManage", true);
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void list_pageNegativeOrZero_normalizesToOne_BR12() throws Exception {
        User user = loginAs(PermissionService.ROLE_ADMIN, null);
        when(permissionService.canViewCustomers(user)).thenReturn(true);
        when(permissionService.canManageCustomers(user)).thenReturn(true);
        when(permissionService.getCustomerListScope(eq(user), any())).thenReturn(ListScope.all());

        when(request.getParameter("page")).thenReturn("-5");
        when(customerService.findAll(eq(1), eq(10), any(), any(), any(), any(), eq(false), any(), any()))
                .thenReturn(List.of());
        when(customerService.countAll(any(), any(), any(), any(), any(), any())).thenReturn(25);

        controller.doGet(request, response);

        verify(request).setAttribute("currentPage", 1);
        verify(request).setAttribute("totalPages", 3);
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void list_preservesFiltersAndKeyword_BR13() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canViewCustomers(user)).thenReturn(true);
        when(permissionService.canManageCustomers(user)).thenReturn(true);
        when(permissionService.getCustomerListScope(eq(user), any())).thenReturn(ListScope.all());

        when(request.getParameter("keyword")).thenReturn("Viettel");
        when(request.getParameter("type")).thenReturn("Đại lý phân phối");
        when(request.getParameter("assigneeId")).thenReturn("15");
        when(request.getParameter("page")).thenReturn("2");
        when(request.getParameter("kind")).thenReturn("buyer");

        controller.doGet(request, response);

        verify(request).setAttribute("keyword", "Viettel");
        verify(request).setAttribute("typeFilter", "Đại lý phân phối");
        verify(request).setAttribute("assigneeFilter", 15);
        verify(request).setAttribute("currentPage", 2);
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void list_supplierKind_clearsProvinceFilter() throws Exception {
        User user = loginAs(PermissionService.ROLE_ADMIN, null);
        when(permissionService.canViewCustomers(user)).thenReturn(true);
        when(permissionService.canManageCustomers(user)).thenReturn(true);
        when(permissionService.getCustomerListScope(eq(user), any())).thenReturn(ListScope.all());

        when(request.getParameter("kind")).thenReturn("supplier");
        when(request.getParameterValues("provinceId")).thenReturn(new String[]{"1", "2"});

        controller.doGet(request, response);

        verify(request).setAttribute("provinceFilters", List.of());
        verify(request).setAttribute("showProvinceFilter", false);
        verify(request).setAttribute("kind", "supplier");
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void list_emptyList_handledCleanly_BR14() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canViewCustomers(user)).thenReturn(true);
        when(permissionService.canManageCustomers(user)).thenReturn(true);
        when(permissionService.getCustomerListScope(eq(user), any())).thenReturn(ListScope.all());

        when(customerService.findAll(anyInt(), anyInt(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(List.of());
        when(customerService.countAll(any(), any(), any(), any(), any(), any())).thenReturn(0);

        controller.doGet(request, response);

        verify(request).setAttribute("customerList", List.of());
        verify(request).setAttribute("totalCount", 0);
        verify(request).setAttribute("totalPages", 1);
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void list_salesWithTerritory_computesLockedIds() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canViewCustomers(user)).thenReturn(true);
        when(permissionService.canManageCustomers(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(true);
        when(permissionService.getCustomerListScope(eq(user), any())).thenReturn(ListScope.all());

        Enterprise entInScope = new Enterprise();
        entInScope.setEnterpriseId(1);
        Enterprise entOutScope = new Enterprise();
        entOutScope.setEnterpriseId(2);

        when(customerService.findAll(anyInt(), anyInt(), any(), any(), any(), any(), eq(false), any(), any()))
                .thenReturn(List.of(entInScope, entOutScope));
        when(permissionService.isCustomerInScope(eq(user), eq(entInScope), any())).thenReturn(true);
        when(permissionService.isCustomerInScope(eq(user), eq(entOutScope), any())).thenReturn(false);

        controller.doGet(request, response);

        verify(request).setAttribute("lockedIds", Set.of(2));
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void list_admin_noLockedIds() throws Exception {
        User user = loginAs(PermissionService.ROLE_ADMIN, null);
        when(permissionService.canViewCustomers(user)).thenReturn(true);
        when(permissionService.canManageCustomers(user)).thenReturn(true);
        when(permissionService.isSales(user)).thenReturn(false);
        when(permissionService.getCustomerListScope(eq(user), any())).thenReturn(ListScope.all());

        Enterprise ent1 = new Enterprise();
        ent1.setEnterpriseId(1);
        when(customerService.findAll(anyInt(), anyInt(), any(), any(), any(), any(), eq(false), any(), any()))
                .thenReturn(List.of(ent1));

        controller.doGet(request, response);

        verify(request).setAttribute("lockedIds", Set.of());
        verify(dispatcher).forward(request, response);
    }
}
