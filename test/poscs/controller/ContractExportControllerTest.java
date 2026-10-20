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
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import poscs.common.ListScope;
import poscs.common.Period;
import poscs.dao.ContractDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Province;
import poscs.model.Role;
import poscs.model.User;
import poscs.service.ContractExportService;
import poscs.service.ContractService;
import poscs.service.PermissionService;

/**
 * Unit Test cho {@link ContractExportController} (UC-41).
 */
public class ContractExportControllerTest {

    private ContractExportController controller;
    private ContractDAO contractDAO;
    private EmployeeDAO employeeDAO;
    private ContractExportService exportService;
    private PermissionService permissionService;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @Before
    public void setUp() {
        contractDAO = mock(ContractDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        exportService = mock(ContractExportService.class);
        permissionService = mock(PermissionService.class);

        controller = new ContractExportController(contractDAO, employeeDAO, exportService, permissionService);

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
        when(permissionService.canExportContracts(user)).thenReturn(false);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        verify(exportService, never()).exportContractsToExcel(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    public void export_unauthenticatedUser_returns403Msg022() throws Exception {
        when(session.getAttribute("currentUser")).thenReturn(null);
        when(permissionService.canExportContracts(null)).thenReturn(false);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void export_adminUser_authorizedAndStreamsExcel() throws Exception {
        User user = loginAs(PermissionService.ROLE_ADMIN, null);
        when(permissionService.canExportContracts(user)).thenReturn(true);
        when(exportService.getExportFileNamePrefix(any())).thenReturn("hop_dong_ban");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(dummyServletOutputStream(baos));

        controller.doGet(request, response);

        verify(response).setContentType("application/vnd.ms-excel");
        verify(response).setHeader(eq("Content-Disposition"), contains("hop_dong_ban"));
        verify(exportService).exportContractsToExcel(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    public void export_technicalUser_authorizedAndStreamsExcel() throws Exception {
        User user = loginAs(PermissionService.ROLE_TECHNICAL, null);
        when(permissionService.canExportContracts(user)).thenReturn(true);
        when(exportService.getExportFileNamePrefix(any())).thenReturn("hop_dong_ban");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(dummyServletOutputStream(baos));

        controller.doGet(request, response);

        verify(response).setContentType("application/vnd.ms-excel");
        verify(exportService).exportContractsToExcel(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    public void export_salesUser_authorizedAndStreamsExcel() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canExportContracts(user)).thenReturn(true);
        when(exportService.getExportFileNamePrefix(any())).thenReturn("hop_dong_ban");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(dummyServletOutputStream(baos));

        controller.doGet(request, response);

        verify(response).setContentType("application/vnd.ms-excel");
        verify(exportService).exportContractsToExcel(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    public void export_preservesFiltersAndScope() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canExportContracts(user)).thenReturn(true);
        when(exportService.getExportFileNamePrefix(any())).thenReturn("hop_dong_ban");

        when(request.getParameter("keyword")).thenReturn("POS-VNPT");
        when(request.getParameter("status")).thenReturn(ContractDAO.STATUS_ACTIVE);
        when(request.getParameter("type")).thenReturn("Trọn gói");
        when(request.getParameter("kind")).thenReturn("sell");
        when(request.getParameterValues("provinceId")).thenReturn(new String[]{"1", "2"});
        when(request.getParameter("year")).thenReturn("2026");
        when(request.getParameter("period")).thenReturn("q2");
        when(request.getParameter("progress")).thenReturn("Đang thực hiện");
        when(request.getParameter("scope")).thenReturn("root");
        when(request.getParameter("waitingDept")).thenReturn("5");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(dummyServletOutputStream(baos));

        controller.doGet(request, response);

        verify(exportService).exportContractsToExcel(
                any(),
                eq("POS-VNPT"),
                eq(ContractDAO.STATUS_ACTIVE),
                eq("Trọn gói"),
                eq(List.of(1, 2)),
                argThat(p -> p != null && p.getYear() == 2026),
                eq("Bán"),
                eq("Đang thực hiện"),
                eq(true), // rootsOnly because scope=root
                eq(5),
                any(ListScope.class)
        );
    }

    @Test
    public void export_buyKind_clearsProvinceFilterAndUsesBuyPrefix() throws Exception {
        User user = loginAs(PermissionService.ROLE_SALES, null);
        when(permissionService.canExportContracts(user)).thenReturn(true);
        when(exportService.getExportFileNamePrefix("Mua")).thenReturn("hop_dong_mua");

        when(request.getParameter("kind")).thenReturn("buy");
        when(request.getParameterValues("provinceId")).thenReturn(new String[]{"1", "2"});

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(dummyServletOutputStream(baos));

        controller.doGet(request, response);

        verify(exportService).exportContractsToExcel(
                any(),
                isNull(),
                isNull(),
                isNull(),
                eq(List.of()), // provinceFilters cleared for buy contracts
                isNull(),
                eq("Mua"),
                isNull(),
                eq(false),
                isNull(),
                any(ListScope.class)
        );
        verify(response).setHeader(eq("Content-Disposition"), contains("hop_dong_mua"));
    }

    @Test
    public void export_waitingFlag1_setsWaitingAnyDepartment() throws Exception {
        User user = loginAs(PermissionService.ROLE_ADMIN, null);
        when(permissionService.canExportContracts(user)).thenReturn(true);
        when(request.getParameter("waiting")).thenReturn("1");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(dummyServletOutputStream(baos));

        controller.doGet(request, response);

        verify(exportService).exportContractsToExcel(
                any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                eq(ContractDAO.WAITING_ANY_DEPARTMENT),
                any(ListScope.class)
        );
    }

    @Test
    public void export_monthAll_clearsActiveWindow() throws Exception {
        User user = loginAs(PermissionService.ROLE_ADMIN, null);
        when(permissionService.canExportContracts(user)).thenReturn(true);
        when(request.getParameter("month")).thenReturn("all");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(dummyServletOutputStream(baos));

        controller.doGet(request, response);

        verify(exportService).exportContractsToExcel(
                any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(),
                argThat(scope -> scope != null && scope.getActiveWindow() == null)
        );
    }

    @Test
    public void resolveWaitingDept_variousInputs() {
        when(request.getParameter("waitingDept")).thenReturn("8");
        assertEquals(Integer.valueOf(8), controller.resolveWaitingDept(request));

        when(request.getParameter("waitingDept")).thenReturn(null);
        when(request.getParameter("waiting")).thenReturn("1");
        assertEquals(Integer.valueOf(ContractDAO.WAITING_ANY_DEPARTMENT), controller.resolveWaitingDept(request));

        when(request.getParameter("waiting")).thenReturn("0");
        assertNull(controller.resolveWaitingDept(request));

        when(request.getParameter("waiting")).thenReturn(null);
        assertNull(controller.resolveWaitingDept(request));
    }

    @Test
    public void resolveListScope_withProvincesAndTeam() {
        User u = loginAs(PermissionService.ROLE_SALES, 10);
        when(employeeDAO.findTeamUserIds(99)).thenReturn(List.of(99, 100));
        Province p = new Province(1, "Hà Nội");
        when(employeeDAO.findProvincesOf(99)).thenReturn(List.of(p));
        when(request.getParameter("view")).thenReturn("mine");

        ListScope scope = controller.resolveListScope(request);
        assertNotNull(scope);
        assertNotNull(scope.getActiveWindow());
    }

    @Test
    public void gettersAndSetters_lazyInitAndCaching() {
        ContractExportController ctrl = new ContractExportController();

        ContractExportService s1 = ctrl.getExportService();
        assertNotNull(s1);
        assertSame(s1, ctrl.getExportService());

        PermissionService ps1 = ctrl.getPermissionService();
        assertNotNull(ps1);
        assertSame(ps1, ctrl.getPermissionService());

        ContractExportService customService = mock(ContractExportService.class);
        ctrl.setExportService(customService);
        assertSame(customService, ctrl.getExportService());

        PermissionService customPs = mock(PermissionService.class);
        ctrl.setPermissionService(customPs);
        assertSame(customPs, ctrl.getPermissionService());

        ContractDAO newDao = mock(ContractDAO.class);
        ctrl.setContractDAO(newDao);
        assertSame(newDao, ctrl.getContractDAO());
        assertNotNull(ctrl.getExportService());

        EmployeeDAO newEmpDao = mock(EmployeeDAO.class);
        ctrl.setEmployeeDAO(newEmpDao);
        assertSame(newEmpDao, ctrl.getEmployeeDAO());
        assertNotNull(ctrl.getPermissionService());
    }
}
