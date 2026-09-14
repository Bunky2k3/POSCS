package poscs.controller;

import java.lang.reflect.Field;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.ChangeRequestDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.NotificationDAO;
import poscs.model.ChangeRequest;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cho ChangeRequestController -- đường để cấp dưới xin cấp trên sửa dữ
 * liệu, sau khi V16 đã lấy quyền ghi Khách hàng/Hợp đồng khỏi tầng lá.
 *
 * <p>Trọng tâm là những chỗ sai thì hỏng cả ý nghĩa của tính năng:
 *
 * <ul>
 *   <li>chỉ CẤP DƯỚI mới gửi được yêu cầu -- người tự sửa được mà vẫn gửi thì
 *       chỉ tạo rác;</li>
 *   <li>chỉ ĐÚNG cấp trên của người gửi mới duyệt được, không phải bất kỳ ai
 *       đang làm quản lý -- nhầm chỗ này thì quản lý vùng A quyết định thay
 *       cho vùng B;</li>
 *   <li>người gửi lấy từ session, không nhận từ form -- đây là cột quy trách
 *       nhiệm;</li>
 *   <li>yêu cầu đã bị người khác quyết định thì không ghi đè được.</li>
 * </ul>
 */
public class ChangeRequestControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private ChangeRequestController controller;
    private ChangeRequestDAO changeRequestDAO;
    private NotificationDAO notificationDAO;
    private EmployeeDAO employeeDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @Before
    public void setUp() throws Exception {
        controller = new ChangeRequestController();
        changeRequestDAO = mock(ChangeRequestDAO.class);
        notificationDAO = mock(NotificationDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        setField(controller, "changeRequestDAO", changeRequestDAO);
        setField(controller, "notificationDAO", notificationDAO);
        setField(controller, "employeeDAO", employeeDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
    }

    private User loginAs(int userId, String roleName, Integer managerId) {
        User user = new User();
        user.setUserId(userId);
        user.setRole(new Role(1, roleName));
        user.setManagerId(managerId);
        when(session.getAttribute("currentUser")).thenReturn(user);
        return user;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private void stubValidCreateParams() {
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("resourceType")).thenReturn(ChangeRequest.RESOURCE_CUSTOMER);
        when(request.getParameter("intent")).thenReturn(ChangeRequest.INTENT_UPDATE);
        when(request.getParameter("targetId")).thenReturn("12");
        when(request.getParameter("proposedContent")).thenReturn("Đổi số điện thoại sang 0912345678");
    }

    // ------------------------------------------------------------------
    // Gửi yêu cầu
    // ------------------------------------------------------------------

    @Test
    public void capDuoi_guiDuocYeuCau_vaBaoChoCapTren() throws Exception {
        loginAs(21, "Sales", 20); // cấp dưới của user 20
        stubValidCreateParams();
        when(changeRequestDAO.insert(any(ChangeRequest.class))).thenReturn(5);

        controller.doPost(request, response);

        verify(changeRequestDAO).insert(argThat((ChangeRequest r) ->
                r.getRequestedBy() == 21
                        && ChangeRequest.RESOURCE_CUSTOMER.equals(r.getResourceType())
                        && r.getTargetId() == 12));
        // Yêu cầu nằm im không ai biết thì cả tính năng chỉ là cái hộp câm.
        verify(notificationDAO).insert(eq(20), anyString(), eq("CHANGE_REQUEST"), eq(5));
        verify(response).sendRedirect(CONTEXT_PATH + "/changerequest?action=view&id=5");
    }

    /** Người không có cấp trên tự sửa được -- gửi yêu cầu cho chính mình là vô nghĩa. */
    @Test
    public void nguoiKhongCoCapTren_khongGuiDuocYeuCau() throws Exception {
        loginAs(20, "Sales", null);
        stubValidCreateParams();

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(changeRequestDAO, never()).insert(any());
    }

    /**
     * requested_by phải lấy từ SESSION. Form gửi lên requestedBy khác cũng
     * không được nghe theo -- đây là cột dùng để quy trách nhiệm.
     */
    @Test
    public void nguoiGui_layTuSessionChuKhongPhaiTuForm() throws Exception {
        loginAs(21, "Sales", 20);
        stubValidCreateParams();
        when(request.getParameter("requestedBy")).thenReturn("999");
        when(changeRequestDAO.insert(any(ChangeRequest.class))).thenReturn(5);

        controller.doPost(request, response);

        verify(changeRequestDAO).insert(argThat((ChangeRequest r) -> r.getRequestedBy() == 21));
    }

    /** "Sửa"/"Xoá" phải có mã bản ghi; thiếu thì không ghi gì cả. */
    @Test
    public void yeuCauSuaMaThieuMaBanGhi_khongDuocGhi() throws Exception {
        loginAs(21, "Sales", 20);
        stubValidCreateParams();
        when(request.getParameter("targetId")).thenReturn(null);

        controller.doPost(request, response);

        verify(changeRequestDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/changerequest?action=new&error=invalid");
    }

    /**
     * Ngược lại, "Tạo mới" KHÔNG cần mã bản ghi -- chưa có dòng nào để trỏ
     * tới. Đây đúng là trường hợp mà thiết kế kiểu diff không diễn đạt nổi.
     */
    @Test
    public void yeuCauTaoMoi_khongCanMaBanGhi() throws Exception {
        loginAs(21, "Sales", 20);
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("resourceType")).thenReturn(ChangeRequest.RESOURCE_CONTRACT);
        when(request.getParameter("intent")).thenReturn(ChangeRequest.INTENT_CREATE);
        when(request.getParameter("proposedContent")).thenReturn("Tạo hợp đồng mới cho khách A");
        when(changeRequestDAO.insert(any(ChangeRequest.class))).thenReturn(8);

        controller.doPost(request, response);

        verify(changeRequestDAO).insert(argThat((ChangeRequest r) -> r.getTargetId() == null));
        verify(response).sendRedirect(CONTEXT_PATH + "/changerequest?action=view&id=8");
    }

    @Test
    public void loaiDuLieuLa_khongDuocGhi() throws Exception {
        loginAs(21, "Sales", 20);
        stubValidCreateParams();
        when(request.getParameter("resourceType")).thenReturn("Sản phẩm"); // ngoài phạm vi cây tổ chức

        controller.doPost(request, response);

        verify(changeRequestDAO, never()).insert(any());
    }

    // ------------------------------------------------------------------
    // Duyệt / từ chối
    // ------------------------------------------------------------------

    private ChangeRequest pendingRequestFrom(int requesterId) {
        ChangeRequest r = new ChangeRequest();
        r.setRequestId(5);
        r.setRequestedBy(requesterId);
        r.setStatus(ChangeRequest.STATUS_PENDING);
        r.setResourceType(ChangeRequest.RESOURCE_CUSTOMER);
        r.setIntent(ChangeRequest.INTENT_UPDATE);
        r.setTargetId(12);
        return r;
    }

    /** Người gửi có cấp trên là {@code managerId} -- controller đọc lại từ CSDL. */
    private void requesterHasManager(int requesterId, Integer managerId) {
        User requester = new User();
        requester.setUserId(requesterId);
        requester.setManagerId(managerId);
        when(employeeDAO.findById(requesterId)).thenReturn(requester);
    }

    @Test
    public void dungCapTren_duyetDuoc_vaBaoLaiChoNguoiGui() throws Exception {
        loginAs(20, "Sales", null);
        requesterHasManager(21, 20);
        when(request.getParameter("action")).thenReturn("review");
        when(request.getParameter("requestId")).thenReturn("5");
        when(request.getParameter("decision")).thenReturn("approve");
        when(changeRequestDAO.findById(5)).thenReturn(pendingRequestFrom(21));
        when(changeRequestDAO.review(anyInt(), anyString(), anyInt(), any())).thenReturn(true);

        controller.doPost(request, response);

        verify(changeRequestDAO).review(eq(5), eq(ChangeRequest.STATUS_APPROVED), eq(20), any());
        verify(notificationDAO).insert(eq(21), anyString(), eq("CHANGE_REQUEST"), eq(5));
    }

    /**
     * Quản lý vùng khác KHÔNG được quyết định thay. Đây là chỗ dễ làm sai nhất:
     * chỉ hỏi "người này có phải cấp trên của ai đó không" là lọt.
     */
    @Test
    public void quanLyKhac_khongDuyetDuocYeuCauCuaVungKhac() throws Exception {
        loginAs(30, "Sales", null); // cũng là quản lý, nhưng không phải của người gửi
        requesterHasManager(21, 20);
        when(request.getParameter("action")).thenReturn("review");
        when(request.getParameter("requestId")).thenReturn("5");
        when(request.getParameter("decision")).thenReturn("approve");
        when(changeRequestDAO.findById(5)).thenReturn(pendingRequestFrom(21));

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(changeRequestDAO, never()).review(anyInt(), anyString(), anyInt(), any());
    }

    @Test
    public void nguoiGui_khongTuDuyetYeuCauCuaChinhMinh() throws Exception {
        loginAs(21, "Sales", 20);
        requesterHasManager(21, 20);
        when(request.getParameter("action")).thenReturn("review");
        when(request.getParameter("requestId")).thenReturn("5");
        when(request.getParameter("decision")).thenReturn("approve");
        when(changeRequestDAO.findById(5)).thenReturn(pendingRequestFrom(21));

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(changeRequestDAO, never()).review(anyInt(), anyString(), anyInt(), any());
    }

    @Test
    public void admin_duyetDuocMoiYeuCau() throws Exception {
        loginAs(1, "Admin", null);
        requesterHasManager(21, 20);
        when(request.getParameter("action")).thenReturn("review");
        when(request.getParameter("requestId")).thenReturn("5");
        when(request.getParameter("decision")).thenReturn("reject");
        when(changeRequestDAO.findById(5)).thenReturn(pendingRequestFrom(21));
        when(changeRequestDAO.review(anyInt(), anyString(), anyInt(), any())).thenReturn(true);

        controller.doPost(request, response);

        verify(changeRequestDAO).review(eq(5), eq(ChangeRequest.STATUS_REJECTED), eq(1), any());
    }

    /**
     * Hai người cùng mở một yêu cầu: người bấm sau phải trượt. DAO trả false
     * khi câu UPDATE không còn ăn (yêu cầu đã hết trạng thái chờ), màn hình
     * phải báo lại chứ không im lặng coi như xong.
     */
    @Test
    public void yeuCauDaDuocNguoiKhacQuyetDinh_baoLaiChoNguoiBamSau() throws Exception {
        loginAs(20, "Sales", null);
        requesterHasManager(21, 20);
        when(request.getParameter("action")).thenReturn("review");
        when(request.getParameter("requestId")).thenReturn("5");
        when(request.getParameter("decision")).thenReturn("approve");
        when(changeRequestDAO.findById(5)).thenReturn(pendingRequestFrom(21));
        when(changeRequestDAO.review(anyInt(), anyString(), anyInt(), any())).thenReturn(false);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/changerequest?action=view&id=5&error=already_reviewed");
        verify(notificationDAO, never()).insert(anyInt(), anyString(), anyString(), anyInt());
    }

    @Test
    public void quyetDinhLa_khongGhiGiCa() throws Exception {
        loginAs(20, "Sales", null);
        requesterHasManager(21, 20);
        when(request.getParameter("action")).thenReturn("review");
        when(request.getParameter("requestId")).thenReturn("5");
        when(request.getParameter("decision")).thenReturn("xoa-luon");
        when(changeRequestDAO.findById(5)).thenReturn(pendingRequestFrom(21));

        controller.doPost(request, response);

        verify(changeRequestDAO, never()).review(anyInt(), anyString(), anyInt(), any());
    }

    // ------------------------------------------------------------------
    // Danh sách: cùng URL, hai vai khác nhau
    // ------------------------------------------------------------------

    @Test
    public void capDuoi_thayYeuCauCuaChinhMinh() throws Exception {
        loginAs(21, "Sales", 20);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(changeRequestDAO).findByRequester(21);
        verify(changeRequestDAO, never()).findForManager(anyInt(), any());
        verify(request).setAttribute("isReviewer", false);
    }

    @Test
    public void capTren_thayYeuCauCuaCapDuoiMinh() throws Exception {
        loginAs(20, "Sales", null);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(changeRequestDAO).findForManager(eq(20), any());
        verify(changeRequestDAO, never()).findByRequester(anyInt());
        verify(request).setAttribute("isReviewer", true);
    }

    @Test
    public void nguoiKhongCoCapTren_khongMoDuocFormGuiYeuCau() throws Exception {
        loginAs(20, "Sales", null);
        when(request.getParameter("action")).thenReturn("new");

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }

    @Test
    public void khongLienQuan_khongXemDuocChiTietYeuCau() throws Exception {
        loginAs(99, "Sales", 30); // không phải người gửi, cũng không phải cấp trên của họ
        requesterHasManager(21, 20);
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");
        when(changeRequestDAO.findById(5)).thenReturn(pendingRequestFrom(21));

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }
}
