package poscs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import poscs.common.Period;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.Enterprise;
import poscs.model.Province;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho {@link DashboardService} (UC-36, BR-15, BR-18).
 */
public class DashboardServiceTest {

    private CustomerDAO customerDAO;
    private ContractDAO contractDAO;
    private AddressDAO addressDAO;
    private EmployeeDAO employeeDAO;
    private DashboardService service;

    @Before
    public void setUp() {
        customerDAO = mock(CustomerDAO.class);
        contractDAO = mock(ContractDAO.class);
        addressDAO = mock(AddressDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        service = new DashboardService(customerDAO, contractDAO, addressDAO, employeeDAO);

        when(addressDAO.findBranchProvinces()).thenReturn(List.of(new Province(3, "Hà Nội")));
        when(contractDAO.countStatusSummary(anyList(), nullable(Period.class), isNull(), anyBoolean(),
                nullable(List.class))).thenReturn(Map.of("Đang hiệu lực", 4));
        when(contractDAO.sumInvoiceAmountInPeriod(any(Period.class), anyList(), nullable(List.class)))
                .thenReturn(BigDecimal.ZERO);
        when(contractDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), anyList(), nullable(List.class)))
                .thenReturn(BigDecimal.ZERO);
        when(contractDAO.findActiveInPeriod(anyInt(), anyList(), nullable(List.class), any(Period.class)))
                .thenReturn(List.of());
        when(customerDAO.findInScope(anyInt(), anyList(), nullable(List.class), nullable(Period.class)))
                .thenReturn(List.of());
    }

    private User user(int id, String roleName) {
        User u = new User();
        u.setUserId(id);
        u.setRole(new Role(1, roleName));
        when(employeeDAO.findTeamUserIds(id)).thenReturn(List.of(id));
        return u;
    }

    // ---- BR-15: Lọc Năm/Kỳ ----

    @Test
    public void loadDashboard_validYearAndQuarter_usesPeriodQueries() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        ServiceResult<DashboardSummary> res = service.loadDashboard(
                null, "2026", "q3", null, false, null, today);

        assertTrue(res.isSuccess());
        DashboardSummary s = res.getData();
        assertNotNull(s.getPeriod());
        assertEquals("Quý 3/2026", s.getPeriod().getLabel());
        assertEquals("Quý 3/2026", s.getContractWindowLabel());
        verify(contractDAO, times(2)).sumInvoiceAmountInPeriod(any(Period.class), eq(List.of()), isNull());
        verify(contractDAO, never()).sumInvoiceAmountByMonth(anyInt(), anyInt(), anyList(), nullable(List.class));
    }

    @Test
    public void loadDashboard_validYearWithoutPeriod_usesFullYear() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        ServiceResult<DashboardSummary> res = service.loadDashboard(
                null, "2026", "", null, false, null, today);

        assertTrue(res.isSuccess());
        assertEquals("Năm 2026", res.getData().getPeriod().getLabel());
        assertEquals("Năm 2026", res.getData().getContractWindowLabel());
    }

    @Test
    public void loadDashboard_blankOrInvalidYear_disablesPeriodFilterAndUsesCurrentMonth() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        for (String badYear : new String[]{null, "", "   ", "abc", "1969", "10000"}) {
            ServiceResult<DashboardSummary> res = service.loadDashboard(
                    null, badYear, "q3", null, false, null, today);
            assertTrue(res.isSuccess());
            assertNull("Expected null period for year=" + badYear, res.getData().getPeriod());
            assertEquals("Tháng 11/2026", res.getData().getContractWindowLabel());
        }
        verify(contractDAO, times(6)).sumInvoiceAmountByMonth(eq(2026), eq(11), eq(List.of()), isNull());
        verify(contractDAO, times(6)).sumInvoiceAmountByMonth(eq(2026), eq(10), eq(List.of()), isNull());
    }

    // ---- BR-18: Doanh thu & tăng trưởng ----

    @Test
    public void loadDashboard_positiveRevenueGrowth_calculatesTrendPercent() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        when(contractDAO.sumInvoiceAmountByMonth(2026, 11, List.of(), null))
                .thenReturn(BigDecimal.valueOf(1_500_000));
        when(contractDAO.sumInvoiceAmountByMonth(2026, 10, List.of(), null))
                .thenReturn(BigDecimal.valueOf(1_000_000));

        DashboardSummary s = service.loadDashboard(null, null, null, null, false, null, today).getData();

        assertEquals(0, BigDecimal.valueOf(1_500_000).compareTo(s.getRevenueThisMonth()));
        assertEquals(Double.valueOf(50.0), s.getRevenueTrendPercent());
    }

    @Test
    public void loadDashboard_negativeRevenueGrowth_calculatesNegativeTrendPercent() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        when(contractDAO.sumInvoiceAmountByMonth(2026, 11, List.of(), null))
                .thenReturn(BigDecimal.valueOf(750_000));
        when(contractDAO.sumInvoiceAmountByMonth(2026, 10, List.of(), null))
                .thenReturn(BigDecimal.valueOf(1_000_000));

        DashboardSummary s = service.loadDashboard(null, null, null, null, false, null, today).getData();

        assertEquals(Double.valueOf(-25.0), s.getRevenueTrendPercent());
    }

    @Test
    public void loadDashboard_zeroPreviousRevenue_leavesTrendNull() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        when(contractDAO.sumInvoiceAmountByMonth(2026, 11, List.of(), null))
                .thenReturn(BigDecimal.valueOf(500_000));
        when(contractDAO.sumInvoiceAmountByMonth(2026, 10, List.of(), null))
                .thenReturn(BigDecimal.ZERO);

        DashboardSummary s = service.loadDashboard(null, null, null, null, false, null, today).getData();

        assertNull(s.getRevenueTrendPercent());
    }

    @Test
    public void loadDashboard_nullDaoReturns_handledSafelyWithoutNpe() {
        when(addressDAO.findBranchProvinces()).thenReturn(null);
        when(contractDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), anyList(), nullable(List.class))).thenReturn(null);
        when(contractDAO.findActiveInPeriod(anyInt(), anyList(), nullable(List.class), any(Period.class))).thenReturn(null);
        when(customerDAO.findInScope(anyInt(), anyList(), nullable(List.class), nullable(Period.class))).thenReturn(null);

        DashboardSummary s = service.loadDashboard(
                null, null, null, null, false, null, LocalDate.of(2026, 11, 14)).getData();

        assertNotNull(s.getProvinceList());
        assertEquals(0, BigDecimal.ZERO.compareTo(s.getRevenueThisMonth()));
        assertNull(s.getRevenueTrendPercent());
        assertTrue(s.getWindowContracts().isEmpty());
        assertTrue(s.getScopeCustomers().isEmpty());
    }

    // ---- UC-36: Phạm vi, địa bàn, hợp đồng & nhãn ngày ----

    @Test
    public void loadDashboard_salesDefaultScopeMineWithTeam() {
        User salesLead = user(7, "Sales");
        when(employeeDAO.findTeamUserIds(7)).thenReturn(List.of(7, 8, 9));

        DashboardSummary s = service.loadDashboard(
                salesLead, null, null, null, false, null, LocalDate.of(2026, 11, 14)).getData();

        assertEquals("mine", s.getScopeFilter());
        assertEquals(3, s.getTeamSize());
        verify(contractDAO).countStatusSummary(eq(List.of()), isNull(), isNull(), eq(false), eq(List.of(7, 8, 9)));
    }

    @Test
    public void loadDashboard_adminDefaultScopeAllAndExplicitOverrideMine() {
        User admin = user(1, "Admin");

        DashboardSummary defaultSummary = service.loadDashboard(
                admin, null, null, null, false, null, LocalDate.of(2026, 11, 14)).getData();
        assertEquals("all", defaultSummary.getScopeFilter());
        assertEquals(0, defaultSummary.getTeamSize());

        DashboardSummary mineSummary = service.loadDashboard(
                admin, null, null, "mine", false, null, LocalDate.of(2026, 11, 14)).getData();
        assertEquals("mine", mineSummary.getScopeFilter());
        assertEquals(1, mineSummary.getTeamSize());
    }

    @Test
    public void loadDashboard_provinceFiltersDefaultAndManualAndSanitized() {
        User sales = user(7, "Sales");
        when(employeeDAO.findProvincesCoveredBy(7))
                .thenReturn(List.of(new Province(3, "Hà Nội"), new Province(5, "Quảng Ninh")));

        // Lần đầu mở trang -> tự tích địa bàn phụ trách
        DashboardSummary def = service.loadDashboard(
                sales, null, null, null, false, null, LocalDate.of(2026, 11, 14)).getData();
        assertEquals(List.of(3, 5), def.getProvinceFilters());
        assertTrue(def.isProvinceFilterIsMine());

        // Bỏ tích hết -> toàn chi nhánh
        DashboardSummary cleared = service.loadDashboard(
                sales, null, null, null, true, null, LocalDate.of(2026, 11, 14)).getData();
        assertTrue(cleared.getProvinceFilters().isEmpty());
        assertFalse(cleared.isProvinceFilterIsMine());

        // Chọn thủ công kèm trùng và rác -> loại rác, khử trùng
        DashboardSummary manual = service.loadDashboard(
                sales, null, null, null, true, new String[]{"9", " 9 ", "", "abc", "3"},
                LocalDate.of(2026, 11, 14)).getData();
        assertEquals(List.of(9, 3), manual.getProvinceFilters());
        assertFalse(manual.isProvinceFilterIsMine());
    }

    @Test
    public void loadDashboard_populatesContractValuesAndTodayLabel() {
        Contract c = new Contract();
        c.setContractId(12);
        c.setContractValue(BigDecimal.valueOf(1_000_000));
        c.setAmendmentValueSigned(BigDecimal.valueOf(250_000));
        when(contractDAO.findActiveInPeriod(anyInt(), anyList(), nullable(List.class), any(Period.class)))
                .thenReturn(List.of(c));
        Enterprise ent = new Enterprise();
        ent.setEnterpriseId(5);
        when(customerDAO.findInScope(anyInt(), anyList(), nullable(List.class), nullable(Period.class)))
                .thenReturn(List.of(ent));

        DashboardSummary sunday = service.loadDashboard(
                null, null, null, null, false, null, LocalDate.of(2026, 11, 15)).getData();
        assertEquals("Chủ Nhật, 15/11/2026", sunday.getTodayLabel());
        assertEquals(11, sunday.getCurrentMonthNumber());
        assertEquals(0, BigDecimal.valueOf(1_250_000).compareTo(sunday.getContractValues().get(12)));
        assertEquals(1, sunday.getScopeCustomers().size());

        DashboardSummary monday = service.loadDashboard(
                null, null, null, null, false, null, LocalDate.of(2026, 11, 16)).getData();
        assertEquals("Thứ Hai, 16/11/2026", monday.getTodayLabel());
    }

    @Test
    public void loadDashboard_windowContractsWithNullElement_skipsNullSafely() {
        Contract valid = new Contract();
        valid.setContractId(15);
        valid.setContractValue(BigDecimal.valueOf(2_000_000));
        when(contractDAO.findActiveInPeriod(anyInt(), anyList(), nullable(List.class), any(Period.class)))
                .thenReturn(Arrays.asList(null, valid, null));

        DashboardSummary s = service.loadDashboard(
                null, null, null, null, false, null, LocalDate.of(2026, 11, 14)).getData();

        assertEquals(1, s.getContractValues().size());
        assertEquals(0, BigDecimal.valueOf(2_000_000).compareTo(s.getContractValues().get(15)));
    }
}
