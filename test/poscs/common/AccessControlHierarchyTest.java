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
     * Kỹ thuật viên có cấp trên vẫn phải giữ nguyên quyền trên Sản phẩm. Nếu
     * việc siết lan sang đây thì cây tổ chức vô tình khoá luôn công việc
     * chuyên môn của họ, chuyện chẳng ai yêu cầu.
     */
    @Test
    public void kyThuatCoCapTren_vanGiuToanQuyenSanPham() {
        loginAs("Kỹ thuật", 3);

        assertTrue(AccessControl.hasFullAccess(request, AccessControl.Resource.PRODUCT));
    }

    @Test
    public void cskhCoCapTren_vanGiuToanQuyenPhieuHoTro() {
        loginAs("CSKH", 3);

        assertTrue(AccessControl.hasFullAccess(request, AccessControl.Resource.TICKET));
    }

    /**
     * Ngoại lệ của role Kỹ thuật trên phiếu được giao (V14) nằm ở nhánh khác
     * hẳn, không đi qua FULL_ACCESS_ROLES -- nên cây tổ chức không được đụng
     * tới nó. Kỹ thuật viên là cấp dưới vẫn phải sửa được phiếu của mình.
     */
    @Test
    public void kyThuatCoCapTren_vanSuaDuocPhieuDuocGiaoChoMinh() {
        loginAs("Kỹ thuật", 3);
        poscs.model.TechnicalRequest ticket = new poscs.model.TechnicalRequest();
        ticket.setAssignedTechnicianId(7); // đúng người đang đăng nhập

        assertTrue(AccessControl.canUpdateAssignedTicket(request, ticket));
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
