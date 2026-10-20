package poscs.controller;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.Logs;
import poscs.service.ContractService;
import poscs.service.PermissionService;
import poscs.service.ServiceResult;

/**
 * Tiện ích xử lý chuyển hướng (redirect / sendError) cho kết quả của ContractActionController.
 * Giúp thu gọn controller tuân thủ quy chuẩn ≤ 250 dòng và ≤ 40 dòng/phương thức.
 */
public final class ContractActionResultHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ContractActionResultHelper.class);

    private ContractActionResultHelper() {}

    public static void redirectCreateResult(HttpServletRequest req, HttpServletResponse resp,
                                           ServiceResult<Integer> res, String kindParam, String contractCode) throws IOException {
        String back = "/contract?action=new&kind=" + kindParam;
        if (res.isSuccess()) {
            resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + res.getData());
        } else if (ContractService.ERR_FORBIDDEN.equals(res.getMessage())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        } else if (ContractService.ERR_DUPLICATE_CODE.equals(res.getMessage())) {
            resp.sendRedirect(req.getContextPath() + back + "&error=duplicate_code");
        } else if (ContractService.ERR_INVALID.equals(res.getMessage())) {
            resp.sendRedirect(req.getContextPath() + back + "&error=invalid");
        } else {
            LOG.warn("Tao hop dong that bai (actor={}, contractCode={})", Logs.actor(req), contractCode);
            resp.sendRedirect(req.getContextPath() + back + "&error=create_failed");
        }
    }

    public static void redirectUpdateResult(HttpServletRequest req, HttpServletResponse resp,
                                           ServiceResult<Void> res, int id, String back) throws IOException {
        if (res.isSuccess()) {
            resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + id);
        } else if (ContractService.ERR_FORBIDDEN.equals(res.getMessage())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        } else if (ContractService.ERR_NOT_FOUND.equals(res.getMessage())) {
            resp.sendRedirect(ContractControllerHelper.notFoundUrl(req));
        } else if (ContractService.ERR_DUPLICATE_CODE.equals(res.getMessage())) {
            resp.sendRedirect(req.getContextPath() + back + "&error=duplicate_code");
        } else if (ContractService.ERR_VALUE_NEGATIVE.equals(res.getMessage())) {
            resp.sendRedirect(req.getContextPath() + back + "&error=value_negative");
        } else if (ContractService.ERR_INVALID.equals(res.getMessage())) {
            resp.sendRedirect(req.getContextPath() + back + "&error=invalid");
        } else {
            LOG.warn("Cap nhat hop dong that bai (actor={}, contractId={})", Logs.actor(req), id);
            resp.sendRedirect(req.getContextPath() + back + "&error=update_failed");
        }
    }

    public static void redirectAmendmentResult(HttpServletRequest req, HttpServletResponse resp,
                                              ServiceResult<Integer> res, int parentId, String back) throws IOException {
        if (res.isSuccess()) {
            resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + res.getData());
        } else if (ContractService.ERR_FORBIDDEN.equals(res.getMessage())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        } else if (ContractService.ERR_NOT_FOUND.equals(res.getMessage())) {
            resp.sendRedirect(ContractControllerHelper.notFoundUrl(req));
        } else if (ContractService.ERR_AMENDMENT_NOT_ALLOWED.equals(res.getMessage())) {
            resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + parentId + "&error=amendment_not_allowed");
        } else if (ContractService.ERR_DUPLICATE_CODE.equals(res.getMessage())) {
            resp.sendRedirect(req.getContextPath() + back + "&error=duplicate_code");
        } else if (ContractService.ERR_VALUE_NEGATIVE.equals(res.getMessage())) {
            resp.sendRedirect(req.getContextPath() + back + "&error=value_negative");
        } else if (ContractService.ERR_INVALID.equals(res.getMessage())) {
            resp.sendRedirect(req.getContextPath() + back + "&error=invalid");
        } else {
            LOG.warn("Lap phu luc that bai (actor={}, parentId={})", Logs.actor(req), parentId);
            resp.sendRedirect(req.getContextPath() + back + "&error=create_failed");
        }
    }

    public static void redirectCorrectResult(HttpServletRequest req, HttpServletResponse resp,
                                            ServiceResult<Void> res, int id, String back) throws IOException {
        if (res.isSuccess()) {
            resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + id);
        } else if (ContractService.ERR_FORBIDDEN.equals(res.getMessage())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        } else if (ContractService.ERR_NOT_FOUND.equals(res.getMessage())) {
            resp.sendRedirect(ContractControllerHelper.notFoundUrl(req));
        } else if (ContractService.ERR_MISSING_REASON.equals(res.getMessage())) {
            resp.sendRedirect(req.getContextPath() + back + "&error=missing_reason");
        } else if (ContractService.ERR_DUPLICATE_CODE.equals(res.getMessage())) {
            resp.sendRedirect(req.getContextPath() + back + "&error=duplicate_code");
        } else if (ContractService.ERR_INVALID.equals(res.getMessage())) {
            resp.sendRedirect(req.getContextPath() + back + "&error=invalid");
        } else {
            LOG.warn("Sua sai sot hop dong that bai (actor={}, contractId={})", Logs.actor(req), id);
            resp.sendRedirect(req.getContextPath() + back + "&error=update_failed");
        }
    }

    public static void redirectProgressResult(HttpServletRequest req, HttpServletResponse resp,
                                             ServiceResult<Void> res, int id, String toStatus) throws IOException {
        if (res.isSuccess()) {
            resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + id);
        } else if (ContractService.ERR_FORBIDDEN.equals(res.getMessage())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        } else if (ContractService.ERR_MISSING_TERM.equals(res.getMessage())) {
            resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + id + "&error=missing_term");
        } else {
            LOG.warn("Chuyen tien do that bai (actor={}, contractId={}, toStatus={})", Logs.actor(req), id, toStatus);
            resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + id + "&error=progress_failed");
        }
    }

    public static void redirectVoidResult(HttpServletRequest req, HttpServletResponse resp,
                                         ServiceResult<Void> res, int id) throws IOException {
        if (res.isSuccess()) {
            resp.sendRedirect(req.getContextPath() + "/contract");
        } else if (ContractService.ERR_FORBIDDEN.equals(res.getMessage())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        } else if (ContractService.ERR_NOT_FOUND.equals(res.getMessage())) {
            resp.sendRedirect(ContractControllerHelper.notFoundUrl(req));
        } else if (ContractService.ERR_VOID_REASON_REQUIRED.equals(res.getMessage())) {
            resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + id + "&error=void_reason_required");
        } else {
            LOG.warn("Huy ban ghi that bai (actor={}, contractId={})", Logs.actor(req), id);
            resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + id + "&error=void_failed");
        }
    }
}
