package poscs.dao;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import poscs.model.District;
import poscs.model.Province;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Test cho AddressDAO -- dữ liệu tỉnh/xã gần như không đổi nên DAO này cache
 * lại trong biến static, và đó chính là chỗ đáng test:
 *
 * <ul>
 *   <li>Lần gọi thứ hai phải lấy từ cache, không mở thêm kết nối.</li>
 *   <li>Kết quả <b>rỗng</b> thì cố ý KHÔNG cache. Mọi tỉnh hợp lệ đều có
 *       xã/phường, nên rỗng nghĩa là truy vấn vừa lỗi -- cache lại sẽ khoá cứng
 *       dropdown thành trống cho tới khi khởi động lại ứng dụng.</li>
 * </ul>
 *
 * Cache là static nên sống xuyên suốt cả lớp test; {@link #clearCaches()} xoá
 * trước mỗi ca, nếu không thứ tự chạy sẽ quyết định ca nào đỗ ca nào trượt.
 *
 * Xem {@link JdbcStub} để biết những test này KHÔNG kiểm chứng gì (SQL, tên
 * cột, schema). JUnit 4 -- xem CustomerControllerTest.
 */
public class AddressDAOTest {

    private final AddressDAO dao = new AddressDAO();

    @Before
    @SuppressWarnings("unchecked")
    public void clearCaches() throws Exception {
        Field provinces = AddressDAO.class.getDeclaredField("provincesCache");
        provinces.setAccessible(true);
        provinces.set(null, null);

        Field wards = AddressDAO.class.getDeclaredField("wardsByProvinceCache");
        wards.setAccessible(true);
        ((Map<Integer, List<District>>) wards.get(null)).clear();
    }

    private static List<Map<String, Object>> twoProvinces() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("province_id", 1, "province_name", "Hà Nội"));
        rows.add(row("province_id", 2, "province_name", "Đà Nẵng"));
        return rows;
    }

    private static List<Map<String, Object>> twoWards(int provinceId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("districts_id", 10, "districts_name", "Phường Cửa Nam", "province_id", provinceId));
        rows.add(row("districts_id", 11, "districts_name", "Phường Hoàn Kiếm", "province_id", provinceId));
        return rows;
    }

    // ------------------------------------------------------------------
    // findAllProvinces
    // ------------------------------------------------------------------

    @Test
    public void findAllProvinces_mapsRows() throws Exception {
        PreparedStatement ps = statementReturning(resultSetOf(twoProvinces()));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<Province> found = dao.findAllProvinces();

            assertEquals(2, found.size());
            assertEquals(1, found.get(0).getProvinceId());
            assertEquals("Hà Nội", found.get(0).getProvinceName());
            assertEquals("Đà Nẵng", found.get(1).getProvinceName());
        }
    }

    @Test
    public void findAllProvinces_secondCall_servedFromCacheWithoutHittingDatabase() throws Exception {
        PreparedStatement ps = statementReturning(resultSetOf(twoProvinces()));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAllProvinces();
            dao.findAllProvinces();

            // Danh sách tỉnh nằm trong mọi form địa chỉ -- truy vấn lại mỗi lần
            // render là lãng phí thuần tuý.
            db.verify(DBContext::getConnection, times(1));
        }
    }

    @Test
    public void findAllProvinces_sqlError_returnsEmptyAndDoesNotPoisonTheCache() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertTrue(dao.findAllProvinces().isEmpty());
        }

        // Lần lỗi không được ghi vào cache, nếu không dropdown tỉnh sẽ trống
        // vĩnh viễn cho tới lần khởi động lại kế tiếp.
        PreparedStatement ps = statementReturning(resultSetOf(twoProvinces()));
        Connection conn = connectionReturning(ps);
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(2, dao.findAllProvinces().size());
        }
    }

    // ------------------------------------------------------------------
    // findWardsByProvinceId
    // ------------------------------------------------------------------

    @Test
    public void findWardsByProvinceId_mapsRowsAndBindsProvinceId() throws Exception {
        PreparedStatement ps = statementReturning(resultSetOf(twoWards(1)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<District> found = dao.findWardsByProvinceId(1);

            assertEquals(2, found.size());
            assertEquals(10, found.get(0).getDistrictId());
            assertEquals("Phường Cửa Nam", found.get(0).getDistrictName());
            assertEquals(1, found.get(0).getProvinceId());
            verify(ps).setInt(1, 1);
        }
    }

    @Test
    public void findWardsByProvinceId_secondCallSameProvince_servedFromCache() throws Exception {
        PreparedStatement ps = statementReturning(resultSetOf(twoWards(1)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findWardsByProvinceId(1);
            dao.findWardsByProvinceId(1);

            db.verify(DBContext::getConnection, times(1));
        }
    }

    @Test
    public void findWardsByProvinceId_differentProvince_isCachedSeparately() throws Exception {
        PreparedStatement ps1 = statementReturning(resultSetOf(twoWards(1)));
        PreparedStatement ps2 = statementReturning(resultSetOf(twoWards(2)));
        Connection conn1 = connectionReturning(ps1);
        Connection conn2 = connectionReturning(ps2);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn1, conn2);

            assertEquals(1, dao.findWardsByProvinceId(1).get(0).getProvinceId());
            // Cache theo từng tỉnh -- không được trả xã của tỉnh 1 cho tỉnh 2.
            assertEquals(2, dao.findWardsByProvinceId(2).get(0).getProvinceId());
            db.verify(DBContext::getConnection, times(2));
        }
    }

    @Test
    public void findWardsByProvinceId_emptyResult_isNotCachedSoNextCallRetries() throws Exception {
        PreparedStatement emptyPs = statementReturning(emptyResultSet());
        Connection emptyConn = connectionReturning(emptyPs);
        PreparedStatement fullPs = statementReturning(resultSetOf(twoWards(1)));
        Connection fullConn = connectionReturning(fullPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(emptyConn, fullConn);

            assertTrue(dao.findWardsByProvinceId(1).isEmpty());
            // Mọi tỉnh hợp lệ đều có xã, nên rỗng là dấu hiệu truy vấn hỏng --
            // phải thử lại chứ không đóng băng kết quả rỗng.
            assertEquals(2, dao.findWardsByProvinceId(1).size());
            db.verify(DBContext::getConnection, times(2));
        }
    }

    @Test
    public void findWardsByProvinceId_sqlError_returnsEmptyListNotNull() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            List<District> found = dao.findWardsByProvinceId(1);

            // AddressController tuần tự hoá danh sách này thành JSON cho AJAX.
            assertNotNull(found);
            assertTrue(found.isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // findBranchProvinces -- địa bàn Chi nhánh Miền Bắc
    // ------------------------------------------------------------------

    /** Đủ 34 tỉnh sau sáp nhập 2025, đúng tên như trong db/schema.sql. */
    private static List<Map<String, Object>> allThirtyFourProvinces() {
        String[] names = {
            "Thành phố Cần Thơ", "Thành phố Đà Nẵng", "Thành phố Hà Nội", "Thành phố Hải Phòng",
            "Thành phố Hồ Chí Minh", "Thành phố Huế", "Tỉnh An Giang", "Tỉnh Bắc Ninh", "Tỉnh Cà Mau",
            "Tỉnh Cao Bằng", "Tỉnh Đắk Lắk", "Tỉnh Điện Biên", "Tỉnh Đồng Nai", "Tỉnh Đồng Tháp",
            "Tỉnh Gia Lai", "Tỉnh Hà Tĩnh", "Tỉnh Hưng Yên", "Tỉnh Khánh Hòa", "Tỉnh Lai Châu",
            "Tỉnh Lạng Sơn", "Tỉnh Lào Cai", "Tỉnh Lâm Đồng", "Tỉnh Nghệ An", "Tỉnh Ninh Bình",
            "Tỉnh Phú Thọ", "Tỉnh Quảng Ngãi", "Tỉnh Quảng Ninh", "Tỉnh Quảng Trị", "Tỉnh Sơn La",
            "Tỉnh Tây Ninh", "Tỉnh Thái Nguyên", "Tỉnh Thanh Hóa", "Tỉnh Tuyên Quang", "Tỉnh Vĩnh Long"
        };
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            rows.add(row("province_id", i + 1, "province_name", names[i]));
        }
        return rows;
    }

    private List<Province> branchProvincesFromFullList() throws Exception {
        PreparedStatement ps = statementReturning(resultSetOf(allThirtyFourProvinces()));
        Connection conn = connectionReturning(ps);
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);
            return dao.findBranchProvinces();
        }
    }

    /**
     * Chốt cứng danh sách địa bàn: đúng 18 tỉnh từ Hà Tĩnh trở ra. Gõ nhầm một
     * tên trong AddressDAO thì tỉnh đó lặng lẽ biến mất khỏi mọi dropdown và
     * mọi báo cáo theo tỉnh -- không có gì báo lỗi, chỉ là một tỉnh "không tồn
     * tại" cho tới khi có người thắc mắc.
     */
    @Test
    public void findBranchProvinces_returnsExactlyTheEighteenNorthernOnes() throws Exception {
        List<Province> branch = branchProvincesFromFullList();

        assertEquals(18, branch.size());
        List<String> names = new ArrayList<>();
        for (Province p : branch) {
            names.add(p.getProvinceName());
        }
        assertTrue(names.contains("Thành phố Hà Nội"));
        assertTrue(names.contains("Tỉnh Hà Tĩnh"));   // đầu mút phía nam của địa bàn
        assertTrue(names.contains("Tỉnh Lạng Sơn"));
        assertTrue(names.contains("Tỉnh Sơn La"));
        // Ngoài địa bàn -- không được lọt vào
        assertFalse(names.contains("Thành phố Hồ Chí Minh"));
        assertFalse(names.contains("Thành phố Đà Nẵng"));
        assertFalse(names.contains("Tỉnh Quảng Trị"));
        assertFalse(names.contains("Tỉnh Khánh Hòa"));
    }

    /** Giữ nguyên thứ tự của nguồn (findAllProvinces đã sắp theo tên rút gọn). */
    @Test
    public void findBranchProvinces_keepsOrderOfTheSource() throws Exception {
        List<Province> branch = branchProvincesFromFullList();

        assertEquals("Thành phố Hà Nội", branch.get(0).getProvinceName());
        assertEquals("Tỉnh Tuyên Quang", branch.get(branch.size() - 1).getProvinceName());
    }

    /**
     * Form SỬA phải giữ được tỉnh ngoài địa bàn mà bản ghi đang dùng, nếu
     * không thì mở form lên ô tỉnh trống và bấm lưu là mất địa chỉ cũ.
     */
    @Test
    public void findBranchProvincesIncluding_keepsAnOutOfRegionProvinceAlreadyInUse() throws Exception {
        PreparedStatement ps = statementReturning(resultSetOf(allThirtyFourProvinces()));
        Connection conn = connectionReturning(ps);
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<Province> withHcm = dao.findBranchProvincesIncluding(5); // Thành phố Hồ Chí Minh
            assertEquals(19, withHcm.size());
            assertEquals("Thành phố Hồ Chí Minh", withHcm.get(withHcm.size() - 1).getProvinceName());

            // Tỉnh đã nằm trong địa bàn thì không nhân đôi.
            assertEquals(18, dao.findBranchProvincesIncluding(3).size());
            // Không có tỉnh nào đang dùng (khách chưa có địa chỉ).
            assertEquals(18, dao.findBranchProvincesIncluding(null).size());
        }
    }
}
