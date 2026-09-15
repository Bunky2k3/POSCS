package poscs.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import poscs.model.Address;
import poscs.model.CustomerLifecycleEvent;
import poscs.model.Enterprise;
import poscs.model.RelationshipRating;

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

    /** Khách chưa bố trí người hỗ trợ: cột phải là NULL, không phải 0 (0 vi phạm khoá ngoại). */
    @Test
    public void insert_noSupportOwner_bindsSqlNull() throws Exception {
        ResultSet keys = singleRow(row("id", 88));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.insert(enterprise("KH-0001")); // chỉ có người phụ trách chính

            verify(ps).setNull(11, java.sql.Types.INTEGER);
        }
    }

    @Test
    public void insert_withSupportOwner_bindsItAfterMainOwner() throws Exception {
        ResultSet keys = singleRow(row("id", 88));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Enterprise e = enterprise("KH-0001");
            e.setSupportOwnerId(7);
            dao.insert(e);

            verify(ps).setInt(10, 5); // phụ trách chính
            verify(ps).setInt(11, 7); // người hỗ trợ
        }
    }

    /**
     * Lọc "người phụ trách" chỉ soi vai CHÍNH, cố ý không khớp sang cột người
     * hỗ trợ: mỗi khách quy về đúng một người chịu trách nhiệm, lọc tên ai thì
     * ra đúng phần của người đó, không lẫn phần họ chỉ đứng hỗ trợ.
     */
    @Test
    public void findAll_filterByOwner_matchesMainRoleOnly() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, 7, null, false, null);

            String sql = capturedSql(conn);
            assertTrue(sql.contains("e.account_owner_id = ?"));
            assertFalse(sql.contains("support_owner_id = ?"));
            verify(ps).setObject(1, 7);
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

            verify(ps).setString(15, "Active");
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

            verify(ps).setString(15, "Inactive");
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

    // ------------------------------------------------------------------
    // Lọc/sắp xếp theo tỉnh (quản lý khách hàng theo địa bàn)
    // ------------------------------------------------------------------

    /** Câu SQL của lần prepareStatement duy nhất trong một lời gọi DAO đọc dữ liệu. */
    private static String capturedSql(Connection conn) throws SQLException {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareStatement(sql.capture());
        return sql.getValue();
    }

    @Test
    public void findAll_withProvinceFilter_bindsProvinceIdOfDistrict() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null, 3, false, null);

            // Tỉnh nằm ở districts.province_id chứ không phải trên enterprises.
            assertTrue(capturedSql(conn).contains("d.province_id = ?"));
            verify(ps).setObject(1, 3);
        }
    }

    /**
     * countAll phải join đúng những bảng mà điều kiện lọc tỉnh tham chiếu tới.
     * Query đếm vốn không join addresses/districts; thiếu chỗ này thì lọc tỉnh
     * chạy được ở danh sách nhưng bộ đếm ném SQLException rồi trả 0 -- hỏng
     * lặng lẽ, bảng có dữ liệu mà vẫn hiện "0 khách hàng".
     */
    @Test
    public void countAll_withProvinceFilter_joinsTablesTheFilterNeeds() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("total", 4)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(4, dao.countAll(null, null, null, 3, null));

            String sql = capturedSql(conn);
            assertTrue(sql.contains("LEFT JOIN addresses a"));
            assertTrue(sql.contains("LEFT JOIN districts d"));
            assertTrue(sql.contains("d.province_id = ?"));
        }
    }

    @Test
    public void findAll_sortByProvince_ordersByProvinceAndPushesMissingAddressLast() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null, null, true, null);

            String sql = capturedSql(conn);
            assertTrue(sql.contains("ORDER BY p.province_name IS NULL"));
            assertTrue(sql.contains(AddressDAO.PROVINCE_SHORT_NAME_ORDER));
        }
    }

    /** Màn hình danh sách vẫn giữ thứ tự mới nhất trước, không đổi theo tỉnh. */
    @Test
    public void findAll_withoutSortFlag_keepsNewestFirst() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null, null, false, null);

            String sql = capturedSql(conn);
            assertTrue(sql.contains("ORDER BY e.enterprise_id DESC"));
            assertFalse(sql.contains("province_name IS NULL"));
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

    // ==================================================================
    // Vai của khách hàng (enterprise_roles)
    // ==================================================================

    /**
     * Lọc theo vai phải dùng EXISTS, KHÔNG phải JOIN: công ty giữ cả hai vai
     * khớp hai dòng ở enterprise_roles, JOIN vào là nó hiện hai lần trong
     * danh sách và số đếm phân trang cũng lệch theo.
     */
    @Test
    public void findAll_locTheoVai_dungExistsChuKhongJoin() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null, null, false, "Nhà cung cấp");

            String sql = capturedSql(conn);
            assertTrue("phải lọc bằng EXISTS", sql.contains("EXISTS"));
            assertFalse("không được JOIN enterprise_roles",
                    sql.contains("JOIN enterprise_roles"));
            // bindParams dùng setObject cho mọi tham số, không phải setString.
            verify(ps).setObject(1, "Nhà cung cấp");
        }
    }

    @Test
    public void findAll_khongLocVai_khongThemDieuKienNao() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null, null, false, null);

            assertFalse(capturedSql(conn).contains("enterprise_roles"));
        }
    }

    /**
     * Danh sách rỗng vẫn phải XOÁ HẾT: gỡ toàn bộ vai là thao tác hợp lệ ở
     * tầng DAO (luật "ít nhất một vai" chặn ở CustomerController), chứ không
     * được lặng lẽ giữ nguyên vai cũ.
     */
    @Test
    public void replaceRolesOf_danhSachRong_vanXoaHet() throws Exception {
        PreparedStatement del = mock(PreparedStatement.class);
        PreparedStatement ins = mock(PreparedStatement.class);
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("DELETE"))).thenReturn(del);
        when(conn.prepareStatement(contains("INSERT"))).thenReturn(ins);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.replaceRolesOf(5, List.of()));

            verify(del).executeUpdate();
            verify(ins, never()).executeBatch();
            verify(conn).commit();
        }
    }

    /**
     * Xoá rồi chèn phải nằm trong MỘT transaction: chèn lỗi giữa chừng mà đã
     * commit phần xoá thì khách đó mất sạch vai và biến khỏi cả hai danh sách.
     */
    @Test
    public void replaceRolesOf_chenLoi_rollbackChuKhongDeMatVaiCu() throws Exception {
        PreparedStatement del = mock(PreparedStatement.class);
        PreparedStatement ins = mock(PreparedStatement.class);
        when(ins.executeBatch()).thenThrow(new SQLException("trùng vai"));
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("DELETE"))).thenReturn(del);
        when(conn.prepareStatement(contains("INSERT"))).thenReturn(ins);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.replaceRolesOf(5, List.of("Khách mua")));

            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void findRolesOf_traVeDuCaHaiVai() throws Exception {
        PreparedStatement ps = statementReturning(resultSetOf(List.of(
                row("role", "Nhà cung cấp"), row("role", "Khách mua"))));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(List.of("Nhà cung cấp", "Khách mua"), dao.findRolesOf(5));
            verify(ps).setInt(1, 5);
        }
    }

    // ==================================================================
    // Lịch sử xếp hạng quan hệ -- gộp từ CustomerLifecycleEventDAOTest
    // ==================================================================
    //
    // Điểm đáng chú ý: xếp hạng đi qua enum RelationshipRating ở CẢ HAI
    // chiều -- ghi xuống bằng getDbValue(), đọc lên bằng fromDbValue(). Lệch
    // một chiều là lịch sử đánh giá hiển thị sai hạng.

    private static CustomerLifecycleEvent event() {
        CustomerLifecycleEvent e = new CustomerLifecycleEvent();
        e.setEnterpriseId(3);
        e.setEventType("Đánh giá định kỳ");
        e.setRelationshipRating(RelationshipRating.values()[0]);
        e.setAutoGenerated(false);
        e.setDescription("Khách hàng hợp tác tốt");
        e.setEventDate(Date.valueOf("2026-09-09"));
        e.setRecordedBy(5);
        return e;
    }

    // ------------------------------------------------------------------
    // insert
    // ------------------------------------------------------------------

    @Test
    public void insert_returnsGeneratedEventId() throws Exception {
        ResultSet keys = singleRow(row("id", 31));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(31, dao.insertLifecycleEvent(event()));
            verify(ps).setInt(1, 3);
            verify(ps).setInt(7, 5);
        }
    }

    @Test
    public void insert_writesRatingAsItsDbValueNotEnumName() throws Exception {
        ResultSet keys = singleRow(row("id", 31));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            RelationshipRating rating = RelationshipRating.values()[0];
            CustomerLifecycleEvent e = event();
            e.setRelationshipRating(rating);
            dao.insertLifecycleEvent(e);

            // Ghi enum.name() sẽ tạo giá trị mà fromDbValue() không đọc ngược được.
            verify(ps).setString(3, rating.getDbValue());
        }
    }

    @Test
    public void insert_noRowAffected_returnsMinusOne() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(0);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(-1, dao.insertLifecycleEvent(event()));
        }
    }

    @Test
    public void insert_noGeneratedKey_returnsMinusOne() throws Exception {
        ResultSet emptyKeys = emptyResultSet();
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(emptyKeys);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(-1, dao.insertLifecycleEvent(event()));
        }
    }

    @Test
    public void insert_sqlError_returnsMinusOne() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertEquals(-1, dao.insertLifecycleEvent(event()));
        }
    }

    // ------------------------------------------------------------------
    // findByEnterpriseId
    // ------------------------------------------------------------------

    @Test
    public void findByEnterpriseId_mapsEventAndRecorderName() throws Exception {
        RelationshipRating rating = RelationshipRating.values()[0];
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(
                "event_id", 31,
                "enterprise_id", 3,
                "event_type", "Đánh giá định kỳ",
                "relationship_rating", rating.getDbValue(),
                "is_auto_generated", 0,
                "description", "Khách hàng hợp tác tốt",
                "event_date", Date.valueOf("2026-09-09"),
                "recorded_by", 5,
                "created_at", null,
                "recorder_last_name", "Nguyễn",
                "recorder_middle_name", "Văn",
                "recorder_first_name", "An"));
        PreparedStatement ps = statementReturning(resultSetOf(rows));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<CustomerLifecycleEvent> found = dao.findLifecycleEventsByEnterpriseId(3);

            assertEquals(1, found.size());
            CustomerLifecycleEvent e = found.get(0);
            assertEquals(31, e.getEventId());
            assertEquals("Đánh giá định kỳ", e.getEventType());
            // Chiều đọc ngược của enum phải khớp chiều ghi ở insert().
            assertEquals(rating, e.getRelationshipRating());
            assertFalse(e.isAutoGenerated());
            assertNotNull("Tên người ghi nhận hiển thị trên timeline", e.getRecordedByUser());
            assertEquals("Nguyễn", e.getRecordedByUser().getLastName());
            assertEquals("An", e.getRecordedByUser().getFirstName());
            verify(ps).setInt(1, 3);
        }
    }

    @Test
    public void findByEnterpriseId_noHistory_returnsEmptyList() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.findLifecycleEventsByEnterpriseId(3).isEmpty());
        }
    }

    @Test
    public void findByEnterpriseId_sqlError_returnsEmptyListNotNull() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            List<CustomerLifecycleEvent> found = dao.findLifecycleEventsByEnterpriseId(3);

            // viewcustomerdetail.jsp lặp thẳng trên danh sách này.
            assertNotNull(found);
            assertTrue(found.isEmpty());
        }
    }
}
