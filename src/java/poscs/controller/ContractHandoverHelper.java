package poscs.controller;

import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import poscs.common.AccessControl;
import poscs.model.ContractHandover;

/**
 * Lớp trợ giúp cho các tác vụ lọc và bóc tách tham số của ContractHandoverController.
 */
public final class ContractHandoverHelper {

    private ContractHandoverHelper() {}

    public static List<ContractHandover> filterAndSetPermissions(HttpServletRequest request,
                                                                 List<ContractHandover> inScope,
                                                                 Integer departmentFilter) {
        List<ContractHandover> rows = new ArrayList<>();
        if (inScope != null) {
            for (ContractHandover h : inScope) {
                if (departmentFilter != null && departmentFilter != h.getDepartmentId()) {
                    continue;
                }
                rows.add(h);
                request.setAttribute("canCompleteHandover_" + h.getHandoverId(),
                        AccessControl.canCompleteHandover(request, h.getDepartmentId()));
            }
        }
        return rows;
    }

    public static List<Integer> parseDepartmentIds(String[] raw) {
        List<Integer> ids = new ArrayList<>();
        if (raw != null) {
            for (String value : raw) {
                Integer id = ContractControllerHelper.parseIntOrNull(value);
                if (id != null && id > 0 && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    public static String resolveReturnBackUrl(HttpServletRequest request, int contractId) {
        String returnTo = request.getParameter("returnTo");
        if ("from-queue".equals(returnTo)) {
            return "/contract?action=handovers" + handoverQueueFilterQuery(request);
        } else if ("from-view".equals(returnTo)) {
            return "/contract?action=view&id=" + contractId;
        }
        return "/contract?action=edit&id=" + contractId;
    }

    public static String handoverQueueFilterQuery(HttpServletRequest request) {
        StringBuilder query = new StringBuilder();
        if (AccessControl.VIEW_MINE.equals(request.getParameter("view"))) {
            query.append("&view=").append(AccessControl.VIEW_MINE);
        }
        Integer departmentId = ContractControllerHelper.parseIntOrNull(request.getParameter("queueDepartmentId"));
        if (departmentId != null) {
            query.append("&departmentId=").append(departmentId);
        }
        return query.toString();
    }
}
