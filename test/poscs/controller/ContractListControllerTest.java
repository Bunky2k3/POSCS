package poscs.controller;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import poscs.common.ListScope;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.Department;
import poscs.model.Province;
import poscs.model.Role;
import poscs.model.User;
import poscs.service.PermissionService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho {@link ContractListController} (UC-10, BR-12, BR-14, BR-16, BR-18).
 */
public class ContractListControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private ContractListController controller;
    private ContractDAO contractDAO;
    private EmployeeDAO employeeDAO;
    private AddressDAO addressDAO;
    private PermissionService permissionService;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    @Before
    public void setUp() {
        contractDAO = mock(ContractDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        addressDAO = mock(AddressDAO.class);
        permissionService = mock(PermissionService.class);

        controller = new ContractListController(contractDAO, employeeDAO, addressDAO, permissionService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        when(request.getRequestDispatcher(ContractControllerHelper.LIST_VIEW)).thenReturn(dispatcher);
        when(permissionService.canViewContracts(any())).thenReturn(true);

        loginAs("Sales", null);
    }

    private User loginAs(String roleName, Integer managerId) {
        User user = new User();
        user.setUserId(99);
        user.setFirstName("Van A");
        user.setLastName("Nguyen");
        user.setRole(new Role(1, roleName));
        user.setManagerId(managerId);
        when(session.getAttribute("currentUser")).thenReturn(user);
        when(employeeDAO.findById(99)).thenReturn(user);
        return user;
    }

    @Test
    public void list_defaultView_bindsPaginationAndScope() throws Exception {
        Contract c1 = new Contract();
        c1.setContractId(1);
        when(contractDAO.findAll(anyInt(), anyInt(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(List.of(c1));
        when(contractDAO.countAll(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(1);
        when(contractDAO.countStatusSummary(nullable(List.class), any(), any(), anyBoolean(), any(), any(), any()))
                .thenReturn(Map.of(ContractDAO.STATUS_ACTIVE, 1));
        when(addressDAO.findBranchProvinces()).thenReturn(List.of(new Province(1, "Hà Nội")));

        controller.doGet(request, response);

        verify(request).setAttribute("currentPage", 1);
        verify(request).setAttribute("pageSize", 10); // BR-12
        verify(request).setAttribute("totalCount", 1);
        verify(request).setAttribute("totalPages", 1);
        verify(request).setAttribute(eq("contractList"), any());
        verify(request).setAttribute(eq("canManage"), anyBoolean());
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void list_emptyList_bindsEmptyListAndZeroTotalCount() throws Exception {
        when(contractDAO.findAll(anyInt(), anyInt(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(List.of());
        when(contractDAO.countAll(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(0);

        controller.showList(request, response);

        verify(request).setAttribute("contractList", List.of()); // BR-14
        verify(request).setAttribute("totalCount", 0);
        verify(request).setAttribute("totalPages", 1);
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void list_preservesFiltersAcrossPaging() throws Exception {
        when(request.getParameter("page")).thenReturn("2");
        when(request.getParameter("keyword")).thenReturn("POS");
        when(request.getParameter("status")).thenReturn("Đang hiệu lực");
        when(request.getParameter("type")).thenReturn("Cung cấp thiết bị");
        when(request.getParameter("progress")).thenReturn("Đã ký");
        when(request.getParameter("scope")).thenReturn("root");
        when(request.getParameter("waitingDept")).thenReturn("5");
        when(request.getParameterValues("provinceId")).thenReturn(new String[]{"3", "7"});

        when(contractDAO.findAll(eq(2), eq(10), eq("POS"), eq("Đang hiệu lực"), eq("Cung cấp thiết bị"),
                eq(List.of(3, 7)), eq(false), any(), any(), eq("Đã ký"), eq(true), eq(5), any(ListScope.class)))
                .thenReturn(List.of());
        when(contractDAO.countAll(eq("POS"), eq("Đang hiệu lực"), eq("Cung cấp thiết bị"),
                eq(List.of(3, 7)), any(), any(), eq("Đã ký"), eq(true), eq(5), any(ListScope.class)))
                .thenReturn(15);

        controller.showList(request, response);

        verify(request).setAttribute("currentPage", 2);
        verify(request).setAttribute("pageSize", 10);
        verify(request).setAttribute("totalPages", 2);
        verify(request).setAttribute("keyword", "POS");
        verify(request).setAttribute("statusFilter", "Đang hiệu lực");
        verify(request).setAttribute("progressFilter", "Đã ký");
        verify(request).setAttribute("scopeFilter", "root");
        verify(request).setAttribute("waitingDeptFilter", 5);
        verify(request).setAttribute("provinceFilters", List.of(3, 7));
    }

    @Test
    public void list_buyDirection_clearsProvinceFilters() throws Exception {
        when(request.getParameter("kind")).thenReturn("buy");
        when(request.getParameterValues("provinceId")).thenReturn(new String[]{"3", "7"});

        controller.showList(request, response);

        verify(contractDAO).findAll(anyInt(), anyInt(), any(), any(), any(), eq(List.of()), anyBoolean(),
                any(), eq("Mua"), any(), anyBoolean(), any(), any());
        verify(request).setAttribute("showProvinceFilter", false);
        verify(request).setAttribute("kind", "buy");
    }

    @Test
    public void list_allTimeMonth_removesActiveWindow() throws Exception {
        when(request.getParameter("month")).thenReturn("all");

        controller.showList(request, response);

        verify(request).setAttribute("monthFilter", "all");
        verify(request).setAttribute("monthLabel", null);
    }

    @Test
    public void list_waitingAnyParam_setsWaitingAnyDepartment() throws Exception {
        when(request.getParameter("waiting")).thenReturn("1");

        controller.showList(request, response);

        verify(contractDAO).findAll(anyInt(), anyInt(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), anyBoolean(), eq(ContractDAO.WAITING_ANY_DEPARTMENT), any());
        verify(request).setAttribute("waitingDeptFilter", null);
        verify(request).setAttribute("waitingAnyFilter", true);
    }

    @Test
    public void list_statusChips_generatesFourChipsWithProperColorsAndCounts() throws Exception {
        Map<String, Integer> summary = Map.of(
                ContractDAO.STATUS_ACTIVE, 10,
                ContractDAO.STATUS_SOON, 3,
                ContractDAO.STATUS_EXPIRED, 2,
                ContractDAO.STATUS_DRAFT, 1
        );
        when(contractDAO.countStatusSummary(nullable(List.class), any(), any(), anyBoolean(), any(), any(), any()))
                .thenReturn(summary);
        when(request.getParameter("status")).thenReturn(ContractDAO.STATUS_ACTIVE);

        controller.showList(request, response);

        ArgumentCaptor<List<ContractController.FilterChip>> captor = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("statusChips"), captor.capture());
        List<ContractController.FilterChip> chips = captor.getValue();
        assertEquals(4, chips.size());

        ContractController.FilterChip activeChip = chips.stream()
                .filter(c -> "Đang hiệu lực".equals(c.getLabel())).findFirst().orElse(null);
        assertNotNull(activeChip);
        assertTrue(activeChip.isOn());
        assertEquals(10, activeChip.getCount());
        assertFalse(activeChip.getQuery().contains("status=")); // clicking toggles off

        ContractController.FilterChip soonChip = chips.stream()
                .filter(c -> c.getLabel().startsWith("Sắp hết hạn")).findFirst().orElse(null);
        assertNotNull(soonChip);
        assertFalse(soonChip.isOn());
        assertEquals(3, soonChip.getCount());
        assertTrue(soonChip.getQuery().contains("status="));
    }

    @Test
    public void list_activeFilters_buildsChipsForActiveFilters() throws Exception {
        when(request.getParameter("keyword")).thenReturn("VNPT");
        when(request.getParameter("progress")).thenReturn("Đã ký");
        when(request.getParameter("scope")).thenReturn("root");
        when(request.getParameter("type")).thenReturn("Cung cấp thiết bị");
        when(request.getParameter("waitingDept")).thenReturn("6");
        Department dept = new Department();
        dept.setDepartmentId(6);
        dept.setDepartmentName("Phòng Kỹ thuật");
        when(employeeDAO.findAllDepartments()).thenReturn(List.of(dept));

        controller.showList(request, response);

        ArgumentCaptor<List<ContractController.FilterChip>> captor = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("activeFilters"), captor.capture());
        List<ContractController.FilterChip> chips = captor.getValue();

        assertTrue(chips.stream().anyMatch(c -> c.getLabel().contains("Từ khoá: VNPT")));
        assertTrue(chips.stream().anyMatch(c -> c.getLabel().contains("Tiến độ: Đã ký")));
        assertTrue(chips.stream().anyMatch(c -> c.getLabel().contains("Chỉ hợp đồng gốc")));
        assertTrue(chips.stream().anyMatch(c -> c.getLabel().contains("Loại: Cung cấp thiết bị")));
        assertTrue(chips.stream().anyMatch(c -> c.getLabel().contains("Đang chờ: Phòng Kỹ thuật")));
    }

    @Test
    public void byEnterpriseJson_missingEnterpriseId_returns400EmptyArray() throws Exception {
        when(request.getServletPath()).thenReturn("/contract/byEnterprise");
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        controller.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertEquals("[]", sw.toString());
    }

    @Test
    public void byEnterpriseJson_validEnterpriseId_returnsJsonArrayWithEscaping() throws Exception {
        when(request.getServletPath()).thenReturn("/contract/byEnterprise");
        when(request.getParameter("enterpriseId")).thenReturn("12");

        Contract c = new Contract();
        c.setContractId(8);
        c.setContractCode("HD-08");
        c.setTitle("Hợp đồng \"Dự án\" & Thi công");
        when(contractDAO.findByEnterpriseId(12)).thenReturn(List.of(c));

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        controller.doGet(request, response);

        String json = sw.toString();
        assertTrue(json.contains("\"id\":8"));
        assertTrue(json.contains("\"code\":\"HD-08\""));
        assertTrue(json.contains("\\\"Dự án\\\""));
    }

    @Test
    public void list_gettersAndSettersAndConstructors() {
        ContractListController clc = new ContractListController();
        assertNotNull(clc.getContractDAO());
        assertNotNull(clc.getEmployeeDAO());
        assertNotNull(clc.getAddressDAO());
        assertNotNull(clc.getPermissionService());

        clc.setContractDAO(contractDAO);
        clc.setEmployeeDAO(employeeDAO);
        clc.setAddressDAO(addressDAO);
        clc.setPermissionService(permissionService);

        assertSame(contractDAO, clc.getContractDAO());
        assertSame(employeeDAO, clc.getEmployeeDAO());
        assertSame(addressDAO, clc.getAddressDAO());
        assertSame(permissionService, clc.getPermissionService());
    }

    @Test
    public void list_userCannotViewContracts_returns403WithMsg022() throws Exception {
        when(permissionService.canViewContracts(any())).thenReturn(false);
        controller.showList(request, response);
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }

    @Test
    public void list_invalidOrNegativePage_defaultsToPageOne() throws Exception {
        when(request.getParameter("page")).thenReturn("-5");
        controller.showList(request, response);
        verify(request).setAttribute("currentPage", 1);
    }
}
