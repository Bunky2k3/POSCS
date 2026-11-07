package poscs.controller;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.Logs;
import poscs.model.TechnicalRequest;
import poscs.service.PermissionService;
import poscs.service.TicketService;

/**
 * Lớp tiện ích hỗ trợ TicketActionController bóc tách tham số request,
 * kiểm tra cờ và xử lý điều hướng lỗi (UC-21, UC-22, UC-23).
 */
public final class TicketActionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(TicketActionHelper.class);

    public static final String CREATE_VIEW = "/jsp/customersupport/addnewTicket.jsp";
    public static final String UPDATE_VIEW = "/jsp/customersupport/updateTicket.jsp";

    private TicketActionHelper() {
    }

    public static TechnicalRequest buildTicketFromRequest(HttpServletRequest request, TechnicalRequest t) {
        if (t == null) {
            t = new TechnicalRequest();
        }
        Integer enterpriseId = parseIntOrNull(request.getParameter("enterpriseId"));
        if (enterpriseId != null) {
            t.setEnterpriseId(enterpriseId);
        }
        t.setContractId(parseIntOrNull(request.getParameter("contractId")));
        t.setTicketType(emptyToNull(request.getParameter("ticketType")));
        t.setPriority(emptyToNull(request.getParameter("priority")));
        t.setReceptionChannel(emptyToNull(request.getParameter("receptionChannel")));
        Integer technicianId = parseIntOrNull(request.getParameter("assignedTechnicianId"));
        if (technicianId != null) {
            t.setAssignedTechnicianId(technicianId);
        }
        t.setDescription(emptyToNull(request.getParameter("description")));
        t.setWarranty("on".equals(request.getParameter("isWarranty")));
        t.setSlaDeadline(parseDateTimeOrNull(request.getParameter("slaDeadline")));
        return t;
    }

    public static TechnicalRequest extractUpdateInput(HttpServletRequest request, int id) {
        TechnicalRequest input = buildTicketFromRequest(request, new TechnicalRequest());
        input.setTicketId(id);
        input.setStatus(emptyToNull(request.getParameter("status")));
        input.setRootCause(emptyToNull(request.getParameter("rootCause")));
        input.setCauseCategory(emptyToNull(request.getParameter("causeCategory")));
        input.setHandlingPlan(emptyToNull(request.getParameter("handlingPlan")));
        input.setResolutionSummary(emptyToNull(request.getParameter("resolutionSummary")));
        return input;
    }

    public static Boolean resolveWarranty(HttpServletRequest request) {
        String param = request.getParameter("isWarranty");
        if (param == null) {
            return null; // Mặc định do validator/service xử lý (BR-43: Có)
        }
        return "on".equalsIgnoreCase(param) || "true".equalsIgnoreCase(param);
    }

    public static void setDropdownAttributes(HttpServletRequest request, TicketService ticketService,
            Integer keepUserId, Integer keepCustomerId) {
        request.setAttribute("customerList", ticketService.findBuyerCustomers(keepCustomerId));
        request.setAttribute("userList", ticketService.findActiveTechnicians(keepUserId));
    }

    public static void handleCreateError(HttpServletRequest request, HttpServletResponse response,
            String err) throws IOException {
        if (PermissionService.MSG_022.equals(err)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        LOG.warn("Tao phieu ho tro that bai (actor={}, err={})", Logs.actor(request), err);
        String errorParam = mapCreateError(err);
        response.sendRedirect(request.getContextPath() + "/ticket?action=new&error=" + errorParam);
    }

    public static void handleUpdateError(HttpServletRequest request, HttpServletResponse response,
            int id, String err) throws IOException {
        if (TicketService.ERR_NOT_FOUND.equals(err)) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
            return;
        }
        if (PermissionService.MSG_022.equals(err)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        LOG.warn("Cap nhat phieu ho tro that bai (actor={}, ticketId={}, err={})",
                Logs.actor(request), id, err);
        String errorParam = mapUpdateError(err);
        response.sendRedirect(request.getContextPath() + "/ticket?action=edit&id=" + id + "&error=" + errorParam);
    }

    public static void handleDeleteError(HttpServletRequest request, HttpServletResponse response,
            int id, String err) throws IOException {
        if (PermissionService.MSG_022.equals(err)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
            return;
        }
        if (TicketService.ERR_NOT_FOUND.equals(err)) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
            return;
        }
        if (TicketService.ERR_CANNOT_DELETE.equals(err)) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=view&id=" + id + "&error=cannot_delete");
            return;
        }
        LOG.warn("Xoa phieu ho tro that bai (actor={}, ticketId={}, err={})", Logs.actor(request), id, err);
        response.sendRedirect(request.getContextPath() + "/ticket?action=view&id=" + id + "&error=delete_failed");
    }

    public static Timestamp parseDateTimeOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Timestamp.valueOf(LocalDateTime.parse(value.trim()));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public static Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static String emptyToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    public static String mapCreateError(String err) {
        if (TicketService.ERR_NOT_BUYER.equals(err)) {
            return TicketService.ERR_NOT_BUYER;
        }
        if (TicketService.ERR_CONTRACT_MISMATCH.equals(err)) {
            return TicketService.ERR_CONTRACT_MISMATCH;
        }
        if (TicketService.ERR_INVALID.equals(err)) {
            return TicketService.ERR_INVALID;
        }
        return TicketService.ERR_CREATE_FAILED;
    }

    public static String mapUpdateError(String err) {
        if (TicketService.ERR_NOT_BUYER.equals(err)) {
            return TicketService.ERR_NOT_BUYER;
        }
        if (TicketService.ERR_CONTRACT_MISMATCH.equals(err)) {
            return TicketService.ERR_CONTRACT_MISMATCH;
        }
        if (TicketService.ERR_INVALID.equals(err) || TicketService.ERR_STATUS_TRANSITION.equals(err)) {
            return TicketService.ERR_INVALID;
        }
        return TicketService.ERR_UPDATE_FAILED;
    }
}
