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
}
