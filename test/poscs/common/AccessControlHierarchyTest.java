package poscs.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test tầng phân quyền THỨ HAI: vị trí trong cây tổ chức.
 *
 * <p>Vai trò cho đủ quyền rồi thì vị trí còn lấy bớt lại -- theo yêu cầu khách
 * hàng 14/09/2026, trên Khách hàng và Hợp đồng chỉ cấp trên mới được tác động
 * lên dữ liệu, cấp dưới chỉ xem. Ba thứ dễ làm sai và được chốt ở đây:
 *
 * <ul>
 *   <li>siết ĐÚNG hai tài nguyên Customer/Contract, không lan sang Ticket hay
 *       Product -- kỹ thuật viên là cấp dưới của ai đó vẫn phải ghi được
 *       nguyên nhân trên phiếu của mình (ngoại lệ đã có từ V14);</li>
 *   <li>Admin không bao giờ bị siết, nếu không thì cây tổ chức nhập sai một
 *       lần là không còn ai gỡ được;</li>
 *   <li>chưa xếp vào cây (manager_id null) thì KHÔNG bị siết -- đây là chốt
 *       an toàn lúc triển khai, bật tính năng lên không được cướp quyền của
 *       ai cho tới khi cây tổ chức thật được nhập.</li>
 * </ul>
 */
public class AccessControlHierarchyTest {

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @Before
    public void setUp() {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
    }

    /** Đăng nhập với vai trò {@code roleName}; {@code managerId} null = không có cấp trên. */
    private void loginAs(String roleName, Integer managerId) {
        User user = new User();
        user.setUserId(7);
        user.setRole(new Role(1, roleName));
        user.setManagerId(managerId);
        when(session.getAttribute("currentUser")).thenReturn(user);
    }

    // ------------------------------------------------------------------
    // Tầng trên vs tầng lá trên Khách hàng / Hợp đồng
    // ------------------------------------------------------------------

    @Test
    public void salesKhongCoCapTren_vanGiuToanQuyenKhachHangVaHopDong() {
        loginAs("Sales", null);

        assertTrue(AccessControl.hasFullAccess(request, AccessControl.Resource.CUSTOMER));
        assertTrue(AccessControl.hasFullAccess(request, AccessControl.Resource.CONTRACT));
    }

    @Test
    public void salesCoCapTren_matQuyenGhiKhachHangVaHopDong() {
        loginAs("Sales", 3); // cấp dưới của user 3

        assertFalse(AccessControl.hasFullAccess(request, AccessControl.Resource.CUSTOMER));
        assertFalse(AccessControl.hasFullAccess(request, AccessControl.Resource.CONTRACT));
    }

    /**
     * requireFullAccess phải trả 403 chứ không âm thầm cho qua: đây là hàm mà
     * mọi handleCreate/handleUpdate/handleDelete của Customer và Contract gọi
     * ở dòng đầu tiên, nên nó là chỗ chặn thật.
     */
    @Test
    public void salesCoCapTren_bịChan403KhiPostThangVaoUrl() throws Exception {
        loginAs("Sales", 3);

        assertFalse(AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER));
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }

    @Test
    public void salesKhongCoCapTren_khongBiChan() throws Exception {
        loginAs("Sales", null);

        assertTrue(AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER));
        verify(response, never()).sendError(anyInt(), anyString());
    }

    // ------------------------------------------------------------------
    // Ranh giới: chỉ siết Customer/Contract
    // ------------------------------------------------------------------

    /**
     * Sales có cấp trên vẫn phải giữ nguyên quyền trên Sản phẩm (Task 9.3: Sales
     * nhận việc quản lý sản phẩm của vai Kỹ thuật cũ, SPEC 3.3 không đánh (*) ở
     * dòng này). Nếu việc siết lan sang đây thì cây tổ chức vô tình khoá luôn
     * việc quản lý sản phẩm của họ, chuyện chẳng ai yêu cầu.
     */
    @Test
    public void salesCoCapTren_vanGiuToanQuyenSanPham() {
        loginAs("Sales", 3);

        assertTrue(AccessControl.hasFullAccess(request, AccessControl.Resource.PRODUCT));
    }

    /**
     * TICKET không nằm trong HIERARCHY_RESTRICTED (chỉ Customer/Contract mới
     * bị siết theo cây tổ chức) -- Sales là role giữ quyền TICKET (gộp thêm
     * việc của CSKH cũ, xem PERMISSIONS.md), có cấp trên hay không cũng không
     * đổi gì ở đây, khác hẳn Customer/Contract phía trên.
     */
    @Test
    public void salesCoCapTren_vanGiuToanQuyenPhieuHoTro() {
        loginAs("Sales", 3);

        assertTrue(AccessControl.hasFullAccess(request, AccessControl.Resource.TICKET));
    }

    /**
     * Task 9.3: vai Kỹ thuật đã gộp vào Sales (V42), ngoại lệ "sửa phiếu được giao
     * cho mình" (canUpdateAssignedTicket) bỏ theo. Tài khoản còn sót tên vai cũ
     * không được giữ lại quyền ghi Sản phẩm hay Phiếu hỗ trợ.
     */
    @Test
    public void vaiKyThuatCu_khongConQuyenGhiSanPhamVaPhieu() {
        loginAs("Kỹ thuật", null);

        assertFalse(AccessControl.hasFullAccess(request, AccessControl.Resource.PRODUCT));
        assertFalse(AccessControl.hasFullAccess(request, AccessControl.Resource.TICKET));
    }

    // ------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------

    @Test
    public void adminCoCapTren_vanKhongBiSiet() {
        loginAs("Admin", 3);

        assertTrue(AccessControl.hasFullAccess(request, AccessControl.Resource.CUSTOMER));
        assertTrue(AccessControl.hasFullAccess(request, AccessControl.Resource.CONTRACT));
        assertTrue(AccessControl.hasFullAccess(request, AccessControl.Resource.EMPLOYEE));
    }

    // ------------------------------------------------------------------
    // Những vai vốn đã View only thì cây tổ chức không đổi gì
    // ------------------------------------------------------------------

    @Test
    public void kyThuatVanKhongCoQuyenGhiKhachHang_duCoHayKhongCapTren() {
        loginAs("Kỹ thuật", null);
        assertFalse(AccessControl.hasFullAccess(request, AccessControl.Resource.CUSTOMER));

        loginAs("Kỹ thuật", 3);
        assertFalse(AccessControl.hasFullAccess(request, AccessControl.Resource.CUSTOMER));
    }

    @Test
    public void chuaDangNhap_khongCoQuyenGi() {
        when(session.getAttribute("currentUser")).thenReturn(null);

        assertFalse(AccessControl.hasFullAccess(request, AccessControl.Resource.CUSTOMER));
        assertFalse(AccessControl.hasFullAccess(request, AccessControl.Resource.CONTRACT));
    }
}
