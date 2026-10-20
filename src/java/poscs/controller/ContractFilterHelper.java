package poscs.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import poscs.common.Period;
import poscs.dao.ContractDAO;
import poscs.model.Department;

/**
 * Lớp tiện ích hỗ trợ lọc và sinh chip trạng thái cho danh sách hợp đồng (UC-10, Task 4.13).
 */
public final class ContractFilterHelper {

    private ContractFilterHelper() {}

    public static Period currentMonth() {
        LocalDate today = LocalDate.now();
        return Period.parse(String.valueOf(today.getYear()), "m" + today.getMonthValue());
    }

    public static String departmentNameOf(List<Department> departments, Integer id) {
        if (id == null || departments == null) {
            return null;
        }
        for (Department d : departments) {
            if (d.getDepartmentId() == id) {
                return d.getDepartmentName();
            }
        }
        return null;
    }

    public static int countOf(Map<String, Integer> summary, String key) {
        Integer value = summary == null ? null : summary.get(key);
        return value == null ? 0 : value;
    }

    public static List<Integer> provinceFiltersOf(HttpServletRequest request) {
        String[] raw = request.getParameterValues("provinceId");
        if (raw == null) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>();
        for (String value : raw) {
            Integer id = ContractControllerHelper.parseIntOrNull(value);
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    public static String provinceQuery(List<Integer> provinceIds) {
        if (provinceIds == null || provinceIds.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Integer id : provinceIds) {
            sb.append("&provinceId=").append(id);
        }
        return sb.toString();
    }

    public static void setPeriodAttributes(HttpServletRequest request, Period period) {
        request.setAttribute("yearList", Period.availableYears());
        request.setAttribute("yearFilter", period != null ? period.getYear() : null);
        request.setAttribute("periodFilter", request.getParameter("period"));
        request.setAttribute("periodLabel", period != null ? period.getLabel() : null);
    }

    public static List<ContractController.FilterChip> buildStatusChips(FilterState state, Map<String, Integer> summary) {
        List<ContractController.FilterChip> chips = new ArrayList<>();
        chips.add(state.statusChip(ContractDAO.STATUS_ACTIVE, "Đang hiệu lực", "var(--success)",
                countOf(summary, ContractDAO.STATUS_ACTIVE)));
        chips.add(state.statusChip(ContractDAO.STATUS_SOON, "Sắp hết hạn (≤30 ngày)", "var(--warning)",
                countOf(summary, ContractDAO.STATUS_SOON)));
        chips.add(state.statusChip(ContractDAO.STATUS_EXPIRED, "Đã hết hạn", "var(--danger)",
                countOf(summary, ContractDAO.STATUS_EXPIRED)));
        chips.add(state.statusChip(ContractDAO.STATUS_DRAFT, "Chưa hiệu lực", "#9ca3af",
                countOf(summary, ContractDAO.STATUS_DRAFT)));
        return chips;
    }

    public static final class FilterState {
        private final String kind;
        private final String keyword;
        private final String status;
        private final String progress;
        private final String scope;
        private final String type;
        private final List<Integer> provinceIds;
        private final String year;
        private final String period;
        private final Integer waitingDepartmentId;
        private final String waitingDepartmentName;
        private final boolean waitingAny;

        public FilterState(String kind, String keyword, String status, String progress, String scope,
                String type, List<Integer> provinceIds, String year, String period, Integer waitingDepartmentId,
                String waitingDepartmentName, boolean waitingAny) {
            this.waitingDepartmentId = waitingDepartmentId;
            this.waitingDepartmentName = waitingDepartmentName;
            this.waitingAny = waitingAny;
            this.kind = kind;
            this.keyword = keyword;
            this.status = status;
            this.progress = progress;
            this.scope = scope;
            this.type = type;
            this.provinceIds = provinceIds == null ? List.of() : provinceIds;
            this.year = year;
            this.period = period;
        }

        private String queryWith(String name, String value) {
            StringBuilder sb = new StringBuilder("action=list");
            put(sb, "kind", "kind".equals(name) ? value : kind);
            put(sb, "keyword", "keyword".equals(name) ? value : keyword);
            put(sb, "status", "status".equals(name) ? value : status);
            put(sb, "progress", "progress".equals(name) ? value : progress);
            put(sb, "scope", "scope".equals(name) ? value : scope);
            put(sb, "type", "type".equals(name) ? value : type);
            appendProvinceParams(sb, name, value);
            put(sb, "year", "year".equals(name) ? value : year);
            String nextYear = "year".equals(name) ? value : year;
            put(sb, "period", ContractControllerHelper.isBlank(nextYear) ? null : ("period".equals(name) ? value : period));
            appendWaitingParams(sb, name, value);
            return sb.toString();
        }

        private void appendProvinceParams(StringBuilder sb, String name, String value) {
            if (!"provinceId".equals(name)) {
                for (Integer id : provinceIds) {
                    put(sb, "provinceId", String.valueOf(id));
                }
            } else {
                put(sb, "provinceId", value);
            }
        }

        private void appendWaitingParams(StringBuilder sb, String name, String value) {
            boolean anyDept = waitingDepartmentId != null
                    && waitingDepartmentId == ContractDAO.WAITING_ANY_DEPARTMENT;
            put(sb, "waitingDept", "waitingDept".equals(name) ? value
                    : (waitingDepartmentId == null || anyDept ? null : String.valueOf(waitingDepartmentId)));
            put(sb, "waiting", "waiting".equals(name) ? value : (waitingAny ? "1" : null));
        }

        private static void put(StringBuilder sb, String name, String value) {
            if (ContractControllerHelper.isBlank(value)) {
                return;
            }
            sb.append('&').append(name).append('=')
              .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }

        public ContractController.FilterChip statusChip(String key, String label, String color, int count) {
            boolean on = key.equals(status);
            return new ContractController.FilterChip(label, queryWith("status", on ? null : key), color, on, count);
        }

        public List<ContractController.FilterChip> activeFilters() {
            List<ContractController.FilterChip> chips = new ArrayList<>();
            if (!ContractControllerHelper.isBlank(keyword)) {
                chips.add(new ContractController.FilterChip("Từ khoá: " + keyword, queryWith("keyword", null), null, true, 0));
            }
            if (!ContractControllerHelper.isBlank(progress)) {
                chips.add(new ContractController.FilterChip("Tiến độ: " + progress, queryWith("progress", null), null, true, 0));
            }
            if (!ContractControllerHelper.isBlank(scope)) {
                chips.add(new ContractController.FilterChip("Chỉ hợp đồng gốc", queryWith("scope", null), null, true, 0));
            }
            if (!ContractControllerHelper.isBlank(type)) {
                chips.add(new ContractController.FilterChip("Loại: " + type, queryWith("type", null), null, true, 0));
            }
            if (!provinceIds.isEmpty()) {
                chips.add(new ContractController.FilterChip(provinceIds.size() == 1 ? "Theo tỉnh"
                        : "Theo tỉnh (" + provinceIds.size() + ")", queryWith("provinceId", null), null, true, 0));
            }
            if (!ContractControllerHelper.isBlank(year)) {
                chips.add(new ContractController.FilterChip(periodLabel(), queryWith("year", null), null, true, 0));
            }
            appendWaitingFilter(chips);
            return chips;
        }

        private void appendWaitingFilter(List<ContractController.FilterChip> chips) {
            if (waitingDepartmentId == null) {
                return;
            }
            if (waitingDepartmentId == ContractDAO.WAITING_ANY_DEPARTMENT) {
                chips.add(new ContractController.FilterChip("Đang bàn giao", queryWith("waiting", null), null, true, 0));
            } else {
                chips.add(new ContractController.FilterChip(
                        ContractControllerHelper.isBlank(waitingDepartmentName) ? "Đang chờ ở một phòng"
                                : "Đang chờ: " + waitingDepartmentName,
                        queryWith("waitingDept", null), null, true, 0));
            }
        }

        private String periodLabel() {
            Period parsed = Period.parse(year, period);
            return parsed != null ? "Kỳ: " + parsed.getLabel() : "Kỳ: " + year;
        }

        public int advancedCount() {
            int n = 0;
            if (!ContractControllerHelper.isBlank(type)) { n++; }
            if (!provinceIds.isEmpty()) { n++; }
            if (!ContractControllerHelper.isBlank(year)) { n++; }
            if (waitingDepartmentId != null && waitingDepartmentId != ContractDAO.WAITING_ANY_DEPARTMENT) { n++; }
            return n;
        }
    }
}
