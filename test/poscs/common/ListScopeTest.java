package poscs.common;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * {@link ListScope#includes} phải trả lời y như câu SQL của
 * {@link ListScope#predicate}: trang Sửa/Xoá khách hàng dùng nó để gác đúng thứ
 * mà danh sách "Của tôi" đang hiện. Lệch nhau là người dùng thấy khách trong
 * danh sách của mình mà bấm Sửa thì bị từ chối (hoặc ngược lại).
 *
 * <p>JUnit 4 -- xem CustomerControllerTest.
 */
public class ListScopeTest {

    @Test
    public void all_baoGomMoiBanGhi() {
        assertTrue(ListScope.all().includes(42, null));
    }

    /** Không biết "tôi" là ai thì không thu hẹp -- như ListScope.of và predicate. */
    @Test
    public void khongCoNguoiNao_coiNhuKhongThuHep() {
        assertTrue(ListScope.of(List.of(), List.of(1)).includes(42, 2));
    }

    @Test
    public void nguoiPhuTrachTrongDoi_thuocPhamVi() {
        assertTrue(ListScope.of(List.of(99, 31), List.of()).includes(31, 5));
    }

    /** HOẶC chứ không phải VÀ: khách ở tỉnh mình cầm là việc của mình dù người khác đứng tên. */
    @Test
    public void tinhMinhCam_thuocPhamViDuNguoiKhacDungTen() {
        assertTrue(ListScope.of(List.of(99), List.of(1)).includes(42, 1));
    }

    @Test
    public void nguoiKhacOTinhKhac_ngoaiPhamVi() {
        assertFalse(ListScope.of(List.of(99), List.of(1)).includes(42, 2));
    }

    @Test
    public void chuaCoDiaChi_chiXetNguoiPhuTrach() {
        ListScope scope = ListScope.of(List.of(99), List.of(1));
        assertTrue(scope.includes(99, null));
        assertFalse(scope.includes(42, null));
    }

    /** Cùng một phạm vi, SQL sinh ra đúng hai vế mà includes đang xét. */
    @Test
    public void predicate_cungHaiVeVoiIncludes() {
        List<Object> params = new ArrayList<>();

        String sql = ListScope.of(List.of(99), List.of(1)).predicate("e.account_owner_id", "d.province_id", params);

        assertEquals("(e.account_owner_id IN (?) OR d.province_id IN (?))", sql);
        assertEquals(List.of(99, 1), params);
    }
}
