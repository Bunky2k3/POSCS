package poscs.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import poscs.common.Period;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.Enterprise;
import poscs.model.Province;
import poscs.model.User;

/**
 * Service tổng hợp số liệu trang tổng quan (UC-36, BR-15, BR-18).
 * Tách toàn bộ nghiệp vụ lọc phạm vi, địa bàn, kỳ báo cáo, tính doanh thu
 * và tăng trưởng ra khỏi {@link poscs.controller.DashboardController}.
 */
public class DashboardService extends BaseService {

    public static final int CONTRACTS_IN_WINDOW_LIMIT = 8;
    public static final int CUSTOMERS_IN_SCOPE_LIMIT = 8;
    public static final String SCOPE_MINE = "mine";
    public static final String SCOPE_ALL = "all";

    private static final String[] WEEKDAY_VI = {
        "Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"
    };

    private final CustomerDAO customerDAO;
    private final ContractDAO contractDAO;
    private final AddressDAO addressDAO;
    private final EmployeeDAO employeeDAO;

    public DashboardService() {
        this(new CustomerDAO(), new ContractDAO(), new AddressDAO(), new EmployeeDAO());
    }

    public DashboardService(CustomerDAO customerDAO, ContractDAO contractDAO,
            AddressDAO addressDAO, EmployeeDAO employeeDAO) {
        this.customerDAO = customerDAO;
        this.contractDAO = contractDAO;
        this.addressDAO = addressDAO;
        this.employeeDAO = employeeDAO;
    }

    /**
     * Tổng hợp toàn bộ dữ liệu hiển thị cho Dashboard theo bộ lọc Năm/Kỳ, phạm vi và địa bàn.
     */
    public ServiceResult<DashboardSummary> loadDashboard(User currentUser, String yearParam,
            String periodParam, String scopeParam, boolean provinceSet, String[] rawProvinceIds) {
        return loadDashboard(currentUser, yearParam, periodParam, scopeParam,
                provinceSet, rawProvinceIds, LocalDate.now());
    }

    ServiceResult<DashboardSummary> loadDashboard(User currentUser, String yearParam,
            String periodParam, String scopeParam, boolean provinceSet, String[] rawProvinceIds, LocalDate today) {
        LocalDate refDate = today != null ? today : LocalDate.now();
        // UC-36, BR-15: Năm trống hoặc không hợp lệ -> Period.parse trả null (coi như không lọc).
        Period period = Period.parse(yearParam, periodParam);
        ScopeResolution scope = resolveScope(currentUser, scopeParam);
        ProvinceResolution provinces = resolveProvinces(currentUser, provinceSet, rawProvinceIds);

        List<Province> provinceList = safeList(addressDAO.findBranchProvinces());
        int totalCustomers = customerDAO.countUpToEndOfPeriod(provinces.filters, period, scope.ownerIds);
        int newCustomersThisMonth = customerDAO.countNewInPeriod(provinces.filters, period, scope.ownerIds);
        Map<String, Integer> contractStatusSummary = contractDAO.countStatusSummary(
                provinces.filters, period, null, false, scope.ownerIds);

        RevenueMetrics revenue = computeRevenue(period, refDate, provinces.filters, scope.ownerIds);
        Period contractWindow = period != null ? period : currentMonth(refDate);
        List<Contract> windowContracts = safeList(contractDAO.findActiveInPeriod(
                CONTRACTS_IN_WINDOW_LIMIT, provinces.filters, scope.ownerIds, contractWindow));
        Map<Integer, BigDecimal> contractValues = buildContractValues(windowContracts);
        List<Enterprise> scopeCustomers = safeList(customerDAO.findInScope(
                CUSTOMERS_IN_SCOPE_LIMIT, provinces.filters, scope.ownerIds, period));

        DashboardSummary summary = new DashboardSummary(
                provinceList, period, scope.scopeFilter, scope.teamSize,
                provinces.myProvinces, provinces.filters, provinces.isMine,
                totalCustomers, newCustomersThisMonth, contractStatusSummary,
                revenue.thisPeriod, revenue.trendPercent, refDate.getMonthValue(),
                formatTodayLabel(refDate), contractWindow.getLabel(),
                windowContracts, contractValues, scopeCustomers);
        return success(summary);
    }

    private ScopeResolution resolveScope(User me, String scopeParam) {
        boolean isAdmin = isAdminUser(me);
        boolean mine = scopeParam == null ? (me != null && !isAdmin) : SCOPE_MINE.equals(scopeParam);
        List<Integer> ownerIds = mine && me != null ? employeeDAO.findTeamUserIds(me.getUserId()) : null;
        boolean effectiveMine = ownerIds != null;
        int teamSize = ownerIds == null ? 0 : ownerIds.size();
        return new ScopeResolution(effectiveMine ? SCOPE_MINE : SCOPE_ALL, ownerIds, teamSize);
    }

    private ProvinceResolution resolveProvinces(User me, boolean provinceSet, String[] rawProvinceIds) {
        List<Province> myProvinces = me == null ? List.of() : safeList(employeeDAO.findProvincesCoveredBy(me.getUserId()));
        List<Integer> filters = provinceSet ? parseProvinceIds(rawProvinceIds) : provinceIdsOf(myProvinces);
        boolean isMine = !filters.isEmpty()
                && filters.size() == myProvinces.size()
                && filters.containsAll(provinceIdsOf(myProvinces));
        return new ProvinceResolution(myProvinces, filters, isMine);
    }

    // UC-36, BR-18: Doanh thu lấy từ contract_payments (qua ContractDAO -> ContractPaymentDAO).
    private RevenueMetrics computeRevenue(Period period, LocalDate today,
            List<Integer> provinceFilters, List<Integer> ownerIds) {
        BigDecimal current;
        BigDecimal previous;
        if (period != null) {
            current = safeAmount(contractDAO.sumInvoiceAmountInPeriod(period, provinceFilters, ownerIds));
            previous = safeAmount(contractDAO.sumInvoiceAmountInPeriod(period.previous(), provinceFilters, ownerIds));
        } else {
            current = safeAmount(contractDAO.sumInvoiceAmountByMonth(
                    today.getYear(), today.getMonthValue(), provinceFilters, ownerIds));
            LocalDate lastMonth = today.minusMonths(1);
            previous = safeAmount(contractDAO.sumInvoiceAmountByMonth(
                    lastMonth.getYear(), lastMonth.getMonthValue(), provinceFilters, ownerIds));
        }
        Double trend = null;
        if (previous.compareTo(BigDecimal.ZERO) > 0) {
            double raw = current.subtract(previous)
                    .divide(previous, 4, RoundingMode.HALF_UP)
                    .doubleValue() * 100;
            trend = Math.round(raw * 10) / 10.0;
        }
        return new RevenueMetrics(current, trend);
    }

    private static Map<Integer, BigDecimal> buildContractValues(List<Contract> contracts) {
        Map<Integer, BigDecimal> values = new HashMap<>();
        for (Contract c : contracts) {
            if (c != null) {
                values.put(c.getContractId(), c.getCurrentValue());
            }
        }
        return values;
    }

    private static boolean isAdminUser(User user) {
        return user != null && user.getRole() != null
                && PermissionService.ROLE_ADMIN.equals(user.getRole().getRoleName());
    }

    private static List<Integer> parseProvinceIds(String[] raw) {
        if (raw == null) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>();
        for (String value : raw) {
            Integer id = parseIntOrNull(value);
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static List<Integer> provinceIdsOf(List<Province> provinces) {
        List<Integer> ids = new ArrayList<>();
        for (Province p : provinces) {
            ids.add(p.getProvinceId());
        }
        return ids;
    }

    private static Period currentMonth(LocalDate today) {
        return Period.parse(String.valueOf(today.getYear()), "m" + today.getMonthValue());
    }

    private static String formatTodayLabel(LocalDate today) {
        String weekday = WEEKDAY_VI[today.getDayOfWeek().getValue() % 7];
        return weekday + ", " + String.format("%02d/%02d/%04d",
                today.getDayOfMonth(), today.getMonthValue(), today.getYear());
    }

    private static BigDecimal safeAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static <T> List<T> safeList(List<T> list) {
        return list != null ? list : List.of();
    }

    private static Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ScopeResolution(String scopeFilter, List<Integer> ownerIds, int teamSize) {}
    private record ProvinceResolution(List<Province> myProvinces, List<Integer> filters, boolean isMine) {}
    private record RevenueMetrics(BigDecimal thisPeriod, Double trendPercent) {}
}
