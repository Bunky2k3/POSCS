package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import poscs.model.Address;
import poscs.model.Enterprise;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Test cho CustomerDAO. Trọng tâm là transaction gộp hai bảng: một khách hàng
 * có địa chỉ được ghi bằng hai câu lệnh (INSERT addresses rồi INSERT/UPDATE
 * enterprises), và hai câu đó phải cùng sống hoặc cùng chết. Nếu bước sau hỏng
 * mà bước trước không bị rollback, bảng addresses tích lại dòng mồ côi không ai
 * trỏ tới -- lặng lẽ, không báo lỗi, chỉ phình dần.
 *
 * Chỗ tinh tế nhất là vòng thử lại khi enterprise_code trùng: mỗi lần thử là
 * một transaction riêng, nên dòng addresses tạo ở lần thử hỏng cũng phải biến
 * mất theo, không được sống sót sang lần thử sau.
 *
 * Xem {@link JdbcStub} để biết những test này KHÔNG kiểm chứng gì (SQL, tên
 * cột, schema). JUnit 4 -- xem CustomerControllerTest.
 */
public class CustomerDAOTest {

    private final CustomerDAO dao = new CustomerDAO();

    private static Enterprise enterprise(String code) {
        Enterprise e = new Enterprise();
        e.setEnterpriseCode(code);
        e.setEnterpriseName("Công ty TNHH Thử Nghiệm");
        e.setCustomerType("Doanh nghiệp");
        e.setEmail("lienhe@thunghiem.vn");
        e.setPhone("0901234567");
        e.setAccountOwnerId(5);
        return e;
    }

    private static Address address() {
        Address a = new Address();
        a.setStreetAndLocalName("12 Nguyễn Trãi");
        a.setDistrictId(3);
        return a;
    }

    // ------------------------------------------------------------------
    // insert -- transaction gộp addresses + enterprises
    // ------------------------------------------------------------------

    @Test
    public void insert_success_commitsAndReturnsNewId() throws Exception {
        ResultSet keys = singleRow(row("id", 88));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(88, dao.insert(enterprise("KH-0001")));

            InOrder inOrder = inOrder(conn);
            inOrder.verify(conn).setAutoCommit(false);
            inOrder.verify(conn).commit();
            inOrder.verify(conn).setAutoCommit(true);
            verify(conn, never()).rollback();
        }
    }

    @Test
    public void insert_noRowAffected_rollsBackAndReturnsMinusOne() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(0);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(-1, dao.insert(enterprise("KH-0001")));

            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void insert_noGeneratedKeyReturned_rollsBackAndReturnsMinusOne() throws Exception {
        ResultSet emptyKeys = emptyResultSet();
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(emptyKeys);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Ghi được dòng nhưng không lấy được id thì bên gọi không có gì để
            // chuyển hướng tới -- coi như thất bại và trả CSDL về nguyên trạng.
            assertEquals(-1, dao.insert(enterprise("KH-0001")));
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void insert_enterpriseInsertFails_rollsBackSoAddressRowIsNotOrphaned() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenThrow(new SQLException("account_owner_id không tồn tại", "23000", 1452));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Enterprise e = enterprise("KH-0001");
            e.setAddress(address());

            assertEquals(-1, dao.insert(e));

            // Không rollback thì dòng addresses vừa chèn nằm lại vĩnh viễn,
            // không enterprise nào trỏ tới và không ai biết để dọn.
            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void insert_nullAddressId_bindsSqlNullInsteadOfZero() throws Exception {
        ResultSet keys = singleRow(row("id", 88));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Khách hàng không nhập địa chỉ -- address_id phải là NULL, không
            // phải 0 (0 sẽ vi phạm khoá ngoại hoặc trỏ vào dòng không tồn tại).
            dao.insert(enterprise("KH-0001"));

            verify(ps).setNull(9, java.sql.Types.INTEGER);
        }
    }

    @Test
    public void insert_noStatusGiven_defaultsToActive() throws Exception {
        ResultSet keys = singleRow(row("id", 88));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.insert(enterprise("KH-0001")); // status để null

            verify(ps).setString(14, "Active");
        }
    }

    @Test
    public void insert_explicitStatus_isKept() throws Exception {
        ResultSet keys = singleRow(row("id", 88));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Enterprise e = enterprise("KH-0001");
            e.setStatus("Inactive");
            dao.insert(e);

            verify(ps).setString(14, "Inactive");
        }
    }

    // ------------------------------------------------------------------
    // insert -- thử lại khi trùng enterprise_code
    // ------------------------------------------------------------------

    @Test
    public void insert_duplicateEnterpriseCode_rollsBackThatAttemptThenRetries() throws Exception {
        ResultSet keys = singleRow(row("id", 91));
        PreparedStatement insertPs = mock(PreparedStatement.class);
        when(insertPs.executeUpdate())
                .thenThrow(duplicateKeyError("enterprise_code"))
                .thenReturn(1);
        when(insertPs.getGeneratedKeys()).thenReturn(keys);

        // generateNextEnterpriseCode() đọc mã lớn nhất giữa hai lần thử.
        PreparedStatement codePs = statementReturning(singleRow(row("enterprise_code", "KH-0004")));

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString(), anyInt())).thenReturn(insertPs);
        when(conn.prepareStatement(anyString())).thenReturn(codePs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Enterprise e = enterprise("KH-0004");
            assertEquals(91, dao.insert(e));

            // Lần thử hỏng phải rollback trước khi sang lần sau, nếu không dòng
            // addresses của lần đó sống sót thành bản ghi mồ côi.
            verify(conn, atLeastOnce()).rollback();
            assertEquals("KH-0005", e.getEnterpriseCode());
            verify(insertPs, times(2)).executeUpdate();
        }
    }

    @Test
    public void insert_nonDuplicateSqlError_failsImmediatelyWithoutRetrying() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenThrow(new SQLException("cột sai kiểu", "42000", 1054));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(-1, dao.insert(enterprise("KH-0001")));

            verify(ps, times(1)).executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // update
    // ------------------------------------------------------------------

    @Test
    public void update_success_commitsAndReturnsTrue() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Enterprise e = enterprise("KH-0001");
            e.setEnterpriseId(12);

            assertTrue(dao.update(e));
            verify(conn).commit();
            verify(conn, never()).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void update_matchesNoRow_rollsBackAndReturnsFalse() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(0); // đã bị xoá mềm, hoặc id không tồn tại
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Enterprise e = enterprise("KH-0001");
            e.setEnterpriseId(12);

            assertFalse(dao.update(e));
            // Địa chỉ có thể đã được ghi trước đó trong cùng transaction -- phải
            // rollback, không thì địa chỉ đổi mà khách hàng thì không.
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void update_existingAddressId_updatesInPlaceInsteadOfInsertingNewRow() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Enterprise e = enterprise("KH-0001");
            e.setEnterpriseId(12);
            e.setAddressId(77);
            e.setAddress(address());

            assertTrue(dao.update(e));

            // Chèn dòng addresses mới mỗi lần bấm lưu sẽ để lại dòng cũ mồ côi,
            // nên phải ghi đè đúng dòng đang có.
            verify(conn, never()).prepareStatement(anyString(), anyInt());
            verify(ps).setInt(7, 77); // address_id giữ nguyên
        }
    }

    @Test
    public void update_sqlError_returnsFalseWithoutThrowing() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("mất kết nối"));

            Enterprise e = enterprise("KH-0001");
            e.setEnterpriseId(12);

            assertFalse(dao.update(e));
        }
    }
}
