package poscs.controller;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.AccessControl;
import poscs.common.Logs;
import poscs.model.BranchInfo;
import poscs.service.BranchInfoService;
import poscs.service.ServiceResult;

/**
 * Trang "Thông tin chi nhánh" (UC-55, SPEC 5.8): Admin xem và sửa thông tin
 * Giám đốc chi nhánh, nguồn người ký bên mình trên hợp đồng (BR-56).
 *
 * <p>Chỉ Admin, không có bậc "View only" cho vai nào khác: chặn một lần ở đầu
 * {@code doGet}/{@code doPost} bằng {@link AccessControl#requireAdmin}, giống
 * {@code EmployeeController}. Vai khác (kể cả gõ thẳng URL) nhận 403 MSG-022.
 *
 * <p>Một bản ghi duy nhất nên chỉ có xem và sửa, không có tạo/xoá. Lưu xong
 * redirect về GET kèm {@code saved=1} để hiện toast MSG-057 (F5 không gửi lại
 * form); dữ liệu sai thì forward lại form, giữ nguyên những gì vừa nhập.
 */
@WebServlet(name = "BranchInfoController", urlPatterns = {"/branch"})
public class BranchInfoController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(BranchInfoController.class);
    static final String VIEW = "/jsp/admin/branchInfo.jsp";
    private static final DateTimeFormatter UPDATED_AT_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private BranchInfoService branchInfoService;

    public BranchInfoController() {
    }

    public BranchInfoController(BranchInfoService branchInfoService) {
        this.branchInfoService = branchInfoService;
    }

    private BranchInfoService service() {
        return branchInfoService != null ? branchInfoService : (branchInfoService = new BranchInfoService());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!AccessControl.requireAdmin(req, resp)) {
            return;
        }
        BranchInfo stored = service().get();
        if (stored == null) {
            // MSG-018: không đọc được bản ghi thì không mở form -- lưu cũng sẽ hỏng.
            req.setAttribute("loadError", BranchInfoService.MSG_LOAD_FAILED);
        }
        if ("1".equals(req.getParameter("saved"))) {
            req.setAttribute("savedMessage", BranchInfoService.MSG_SAVED);
        }
        forwardForm(req, resp, stored, stored, Map.of(), null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!AccessControl.requireAdmin(req, resp)) {
            return;
        }
        BranchInfo submitted = readForm(req);
        ServiceResult<BranchInfo> res = service().update(submitted, AccessControl.currentUser(req));
        if (res.isSuccess()) {
            resp.sendRedirect(req.getContextPath() + "/branch?saved=1");
            return;
        }
        if (BranchInfoService.MSG_FORBIDDEN.equals(res.getMessage())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, AccessControl.MSG_022);
            return;
        }
        LOG.warn("Luu thong tin chi nhanh that bai (actor={}, fields={})",
                Logs.actor(req), res.getFieldErrors().keySet());
        forwardForm(req, resp, submitted, service().get(), res.getFieldErrors(), res.getMessage());
    }

    /**
     * @param formData    giá trị điền vào 4 ô (bản đang lưu, hoặc dữ liệu vừa gửi khi lưu lỗi)
     * @param stored      bản đang lưu, để hiện dòng "Cập nhật lần cuối"
     * @param fieldErrors lỗi theo từng ô (BR-57)
     * @param formError   lỗi chung khi CSDL không ghi được
     */
    private void forwardForm(HttpServletRequest req, HttpServletResponse resp, BranchInfo formData,
            BranchInfo stored, Map<String, String> fieldErrors, String formError)
            throws ServletException, IOException {
        req.setAttribute("branchInfo", formData != null ? formData : new BranchInfo());
        req.setAttribute("fieldErrors", fieldErrors);
        req.setAttribute("formError", formError);
        setLastUpdated(req, stored);
        req.getRequestDispatcher(VIEW).forward(req, resp);
    }

    /** Dòng "Cập nhật lần cuối bởi … lúc …"; không set gì khi chưa ai sửa từ lúc gieo. */
    private void setLastUpdated(HttpServletRequest req, BranchInfo stored) {
        if (stored == null || stored.getUpdatedBy() == null) {
            return;
        }
        String name = service().findUpdaterName(stored);
        req.setAttribute("updatedByName", name != null ? name : "NV-" + stored.getUpdatedBy());
        if (stored.getUpdatedAt() != null) {
            req.setAttribute("updatedAtText", stored.getUpdatedAt().toLocalDateTime().format(UPDATED_AT_FORMAT));
        }
    }

    /** Giữ nguyên chữ người dùng gõ; trim và kiểm BR-57 là việc của service. */
    private static BranchInfo readForm(HttpServletRequest req) {
        BranchInfo info = new BranchInfo();
        info.setDirectorName(req.getParameter("directorName"));
        info.setDirectorPosition(req.getParameter("directorPosition"));
        info.setPhone(req.getParameter("phone"));
        info.setFax(req.getParameter("fax"));
        return info;
    }
}
