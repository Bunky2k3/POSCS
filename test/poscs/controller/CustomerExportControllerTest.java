package poscs.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.common.ListScope;
import poscs.dao.AddressDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Role;
import poscs.model.User;
import poscs.service.CustomerService;
import poscs.service.PermissionService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho {@link CustomerExportController} (UC-37).
 */
public class CustomerExportControllerTest {

    private CustomerExportController controller;
    private CustomerDAO customerDAO;
    private EmployeeDAO employeeDAO;
    private AddressDAO addressDAO;
    private CustomerService customerService;
    private PermissionService permissionService;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @Before
    public void setUp() {
        customerDAO = mock(CustomerDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        addressDAO = mock(AddressDAO.class);
        customerService = mock(CustomerService.class);
        permissionService = mock(PermissionService.class);

        controller = new CustomerExportController(customerDAO, employeeDAO, addressDAO, customerService, permissionService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
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

    private ServletOutputStream dummyServletOutputStream(ByteArrayOutputStream baos) {
        return new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }

            @Override
            public void write(int b) throws IOException {
                baos.write(b);
            }
        };
    }

    @Test
    public void export_withoutPermission_returns403Msg022() throws Exception {
        User user = loginAs("Unknown", null);
        when(permissionService.canExportCustomers(user)).thenReturn(false);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(customerService, never()).exportCustomersToExcel(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void export_preservesFiltersAndScope() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canExportCustomers(user)).thenReturn(true);
        when(permissionService.getCustomerListScope(eq(user), any())).thenReturn(ListScope.all());
        when(customerService.getExportFileNamePrefix(any())).thenReturn("danh_sach_khach_hang");

        when(request.getParameter("keyword")).thenReturn("Viettel");
        when(request.getParameter("type")).thenReturn("Đại lý");
        when(request.getParameter("assigneeId")).thenReturn("12");
        when(request.getParameter("kind")).thenReturn("buyer");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(dummyServletOutputStream(baos));

        controller.doGet(request, response);

        verify(customerService).exportCustomersToExcel(any(), eq("Viettel"), eq("Đại lý"), eq(12), any(),
                eq(PermissionService.ROLE_BUYER), any());
        verify(response).setHeader(eq("Content-Disposition"), contains("danh_sach_khach_hang"));
    }

    @Test
    public void export_supplierKind_clearsProvinceFilter() throws Exception {
        User user = loginAs(PermissionService.ROLE_ADMIN, null);
        when(permissionService.canExportCustomers(user)).thenReturn(true);
        when(permissionService.getCustomerListScope(eq(user), any())).thenReturn(ListScope.all());
        when(customerService.getExportFileNamePrefix(any())).thenReturn("danh_sach_nha_cung_cap");

        when(request.getParameter("kind")).thenReturn("supplier");
        when(request.getParameterValues("provinceId")).thenReturn(new String[]{"1", "2"});

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(dummyServletOutputStream(baos));

        controller.doGet(request, response);

        verify(customerService).exportCustomersToExcel(any(), any(), any(), any(), eq(List.of()),
                eq(PermissionService.ROLE_SUPPLIER), any());
    }
}
