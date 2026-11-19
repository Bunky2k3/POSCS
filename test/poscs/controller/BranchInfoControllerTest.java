package poscs.controller;

import java.sql.Timestamp;
import java.util.Map;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import poscs.model.BranchInfo;
import poscs.model.Role;
import poscs.model.User;
import poscs.service.BranchInfoService;
import poscs.service.ServiceResult;
import poscs.validator.BranchInfoValidator;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test BranchInfoController (/branch, UC-55).
 *
 * <p>Trọng tâm: chỉ Admin vào được cả hai đường GET/POST (Sales, Kỹ thuật và
 * phiên hết hạn đều nhận 403 MSG-022, service không bị gọi); lưu xong
 * redirect kèm saved=1 rồi GET hiện MSG-057; dữ liệu sai thì forward lại form
 * giữ nguyên dữ liệu vừa nhập.
 */
public class BranchInfoControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";
    private static final String VIEW = "/jsp/admin/branchInfo.jsp";
    private static final String MSG_022 = "Bạn không có quyền truy cập nội dung này.";

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final HttpSession session = mock(HttpSession.class);
    private final RequestDispatcher dispatcher = mock(RequestDispatcher.class);
    private final BranchInfoService service = mock(BranchInfoService.class);
    private final BranchInfoController controller = new BranchInfoController(service);

    private User admin;

    @Before
    public void setUp() {
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        when(request.getRequestDispatcher(VIEW)).thenReturn(dispatcher);
        admin = loginAs(1, "Admin");
    }

    private User loginAs(int id, String roleName) {
        User user = new User();
        user.setUserId(id);
        user.setUsername("user" + id);
        user.setRole(new Role(id, roleName));
        when(session.getAttribute("currentUser")).thenReturn(user);
        return user;
    }

    private static BranchInfo info(String name, String position, String phone, String fax) {
        BranchInfo b = new BranchInfo();
        b.setDirectorName(name);
        b.setDirectorPosition(position);
        b.setPhone(phone);
        b.setFax(fax);
        return b;
    }

    private void postForm(String name, String position, String phone, String fax) {
        when(request.getParameter("directorName")).thenReturn(name);
        when(request.getParameter("directorPosition")).thenReturn(position);
        when(request.getParameter("phone")).thenReturn(phone);
        when(request.getParameter("fax")).thenReturn(fax);
    }

    // ------------------------------------------------------------------
    // Phân quyền
    // ------------------------------------------------------------------

    @Test
    public void get_salesAndTechnical_get403Msg022AndNoData() throws Exception {
        for (String role : new String[]{"Sales", "Kỹ thuật"}) {
            HttpServletResponse res = mock(HttpServletResponse.class);
            loginAs(7, role);

            controller.doGet(request, res);

            verify(res).sendError(HttpServletResponse.SC_FORBIDDEN, MSG_022);
        }
        verifyNoInteractions(service);
        verify(request, never()).getRequestDispatcher(anyString());
    }

    /** Gõ thẳng POST vào /branch cũng bị chặn trước khi đọc tham số hay gọi service. */
    @Test
    public void post_salesAndTechnical_get403Msg022AndNothingSaved() throws Exception {
        postForm("Kẻ Gian", "Giám đốc", null, null);
        for (String role : new String[]{"Sales", "Kỹ thuật"}) {
            HttpServletResponse res = mock(HttpServletResponse.class);
            loginAs(7, role);

            controller.doPost(request, res);

            verify(res).sendError(HttpServletResponse.SC_FORBIDDEN, MSG_022);
            verify(res, never()).sendRedirect(anyString());
        }
        verifyNoInteractions(service);
    }

    @Test
    public void get_notLoggedIn_blocked() throws Exception {
        when(request.getSession(false)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, MSG_022);
        verifyNoInteractions(service);
    }

    @Test
    public void post_notLoggedIn_blocked() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        postForm("Nguyễn Văn An", "Giám đốc", null, null);

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, MSG_022);
        verifyNoInteractions(service);
    }

    // ------------------------------------------------------------------
    // Admin xem
    // ------------------------------------------------------------------

    @Test
    public void get_admin_showsStoredInfo() throws Exception {
        BranchInfo stored = info("Nguyễn Văn An", "Giám đốc chi nhánh", "0912345678", "(0233) 3852 123");
        when(service.get()).thenReturn(stored);

        controller.doGet(request, response);

        verify(request).setAttribute("branchInfo", stored);
        verify(request).setAttribute("fieldErrors", Map.of());
        verify(request, never()).setAttribute(eq("loadError"), any());
        verify(request, never()).setAttribute(eq("savedMessage"), any());
        verify(dispatcher).forward(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    public void get_admin_showsLastUpdatedLine() throws Exception {
        BranchInfo stored = info("Nguyễn Văn An", "Giám đốc", null, null);
        stored.setUpdatedBy(5);
        stored.setUpdatedAt(Timestamp.valueOf("2026-11-19 14:30:00"));
        when(service.get()).thenReturn(stored);
        when(service.findUpdaterName(stored)).thenReturn("Trần Thị Bình");

        controller.doGet(request, response);

        verify(request).setAttribute("updatedByName", "Trần Thị Bình");
        verify(request).setAttribute("updatedAtText", "19/11/2026 14:30");
    }

    @Test
    public void get_admin_updaterNameMissing_fallsBackToEmployeeCode() throws Exception {
        BranchInfo stored = info("Nguyễn Văn An", "Giám đốc", null, null);
        stored.setUpdatedBy(5);
        when(service.get()).thenReturn(stored);
        when(service.findUpdaterName(stored)).thenReturn(null);

        controller.doGet(request, response);

        verify(request).setAttribute("updatedByName", "NV-5");
        verify(request, never()).setAttribute(eq("updatedAtText"), any());
    }

    /** Dòng gieo sẵn (updated_by NULL): không có dòng "Cập nhật lần cuối". */
    @Test
    public void get_admin_seedRowNeverUpdated_noLastUpdatedLine() throws Exception {
        when(service.get()).thenReturn(info(null, "Giám đốc chi nhánh", null, null));

        controller.doGet(request, response);

        verify(request, never()).setAttribute(eq("updatedByName"), any());
        verify(service, never()).findUpdaterName(any());
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void get_admin_loadFails_showsMsg018WithEmptyForm() throws Exception {
        when(service.get()).thenReturn(null);

        controller.doGet(request, response);

        verify(request).setAttribute("loadError", BranchInfoService.MSG_LOAD_FAILED);
        assertEquals("Có lỗi khi tải dữ liệu. Vui lòng thử lại.", BranchInfoService.MSG_LOAD_FAILED);
        verify(request).setAttribute(eq("branchInfo"), any(BranchInfo.class));
        verify(dispatcher).forward(request, response);
    }

    @Test
    public void get_admin_afterSave_showsMsg057() throws Exception {
        when(service.get()).thenReturn(info("Nguyễn Văn An", "Giám đốc", null, null));
        when(request.getParameter("saved")).thenReturn("1");

        controller.doGet(request, response);

        verify(request).setAttribute("savedMessage", "Đã lưu thông tin chi nhánh.");
    }

    // ------------------------------------------------------------------
    // Admin lưu
    // ------------------------------------------------------------------

    @Test
    public void post_admin_valid_savesAndRedirectsWithSavedFlag() throws Exception {
        postForm(" Nguyễn Văn An ", "Giám đốc chi nhánh", "0912345678", "");
        when(service.update(any(BranchInfo.class), eq(admin)))
                .thenReturn(ServiceResult.ok(new BranchInfo(), BranchInfoService.MSG_SAVED));

        controller.doPost(request, response);

        ArgumentCaptor<BranchInfo> sent = ArgumentCaptor.forClass(BranchInfo.class);
        verify(service).update(sent.capture(), eq(admin));
        assertEquals(" Nguyễn Văn An ", sent.getValue().getDirectorName());
        assertEquals("Giám đốc chi nhánh", sent.getValue().getDirectorPosition());
        assertEquals("0912345678", sent.getValue().getPhone());
        assertEquals("", sent.getValue().getFax());
        verify(response).sendRedirect(CONTEXT_PATH + "/branch?saved=1");
        verify(dispatcher, never()).forward(any(), any());
    }

    @Test
    public void post_admin_validationError_redisplaysFormWithEnteredData() throws Exception {
        postForm("", "Giám đốc chi nhánh", "09abc", "(0233) 3852 123");
        Map<String, String> errors = Map.of(
                BranchInfoValidator.FIELD_DIRECTOR_NAME, BranchInfoValidator.MSG_REQUIRED,
                BranchInfoValidator.FIELD_PHONE, BranchInfoValidator.MSG_PHONE_INVALID);
        when(service.update(any(BranchInfo.class), eq(admin))).thenReturn(ServiceResult.fail(errors));
        when(service.get()).thenReturn(info("Tên Cũ", "Giám đốc", null, null));

        controller.doPost(request, response);

        ArgumentCaptor<BranchInfo> shown = ArgumentCaptor.forClass(BranchInfo.class);
        verify(request).setAttribute(eq("branchInfo"), shown.capture());
        assertEquals("", shown.getValue().getDirectorName());
        assertEquals("Giám đốc chi nhánh", shown.getValue().getDirectorPosition());
        assertEquals("09abc", shown.getValue().getPhone());
        assertEquals("(0233) 3852 123", shown.getValue().getFax());
        verify(request).setAttribute("fieldErrors", errors);
        verify(dispatcher).forward(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    public void post_admin_saveFails_redisplaysFormWithErrorAndData() throws Exception {
        postForm("Nguyễn Văn An", "Giám đốc chi nhánh", null, null);
        when(service.update(any(BranchInfo.class), eq(admin)))
                .thenReturn(ServiceResult.fail(BranchInfoService.MSG_SAVE_FAILED));

        controller.doPost(request, response);

        verify(request).setAttribute("formError", BranchInfoService.MSG_SAVE_FAILED);
        ArgumentCaptor<BranchInfo> shown = ArgumentCaptor.forClass(BranchInfo.class);
        verify(request).setAttribute(eq("branchInfo"), shown.capture());
        assertEquals("Nguyễn Văn An", shown.getValue().getDirectorName());
        verify(dispatcher).forward(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    /** Phiên còn ghi Admin nhưng CSDL đã đổi vai: service từ chối thì trả 403, không hiện form. */
    @Test
    public void post_admin_serviceForbids_returns403() throws Exception {
        postForm("Nguyễn Văn An", "Giám đốc chi nhánh", null, null);
        when(service.update(any(BranchInfo.class), eq(admin)))
                .thenReturn(ServiceResult.fail(BranchInfoService.MSG_FORBIDDEN));

        controller.doPost(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, MSG_022);
        verify(dispatcher, never()).forward(any(), any());
        verify(response, never()).sendRedirect(anyString());
    }
}
