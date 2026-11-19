package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import poscs.model.BranchInfo;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Unit Test cho {@link BranchInfoDAO} (Task 8.2, UC-55).
 *
 * Kiểm thử:
 * - get(): đọc đủ cột của dòng id = 1, NULL ra null (kể cả updated_by), không
 *   có dòng hoặc lỗi CSDL thì trả null.
 * - update(): bind đúng thứ tự tham số, ghi actorId vào updated_by, chỉ nhắm
 *   dòng id = 1; tham số sai, không khớp dòng nào hoặc lỗi CSDL thì trả false.
 *
 * Như mọi test DAO dùng {@link JdbcStub}: câu SQL không chạy trên MySQL thật.
 */
public class BranchInfoDAOTest {

    private final BranchInfoDAO dao = new BranchInfoDAO();

    private static BranchInfo sampleInfo() {
        BranchInfo info = new BranchInfo();
        info.setDirectorName("Nguyễn Văn An");
        info.setDirectorPosition("Giám đốc chi nhánh");
        info.setPhone("024 3856 1234");
        info.setFax("(024) 3856 1235");
        return info;
    }

    // ------------------------------------------------------------------
    // get
    // ------------------------------------------------------------------

    @Test
    public void get_rowExists_readsAllColumnsOfRowOne() throws Exception {
        Timestamp updatedAt = Timestamp.valueOf("2026-11-19 09:30:00");
        ResultSet rs = singleRow(row(
                "director_name", "Nguyễn Văn An",
                "director_position", "Giám đốc chi nhánh",
                "phone", "024 3856 1234",
                "fax", "(024) 3856 1235",
                "updated_by", 7,
                "updated_at", updatedAt));
        PreparedStatement ps = statementReturning(rs);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            BranchInfo info = dao.get();

            assertNotNull(info);
            assertEquals("Nguyễn Văn An", info.getDirectorName());
            assertEquals("Giám đốc chi nhánh", info.getDirectorPosition());
            assertEquals("024 3856 1234", info.getPhone());
            assertEquals("(024) 3856 1235", info.getFax());
            assertEquals(Integer.valueOf(7), info.getUpdatedBy());
            assertEquals(updatedAt, info.getUpdatedAt());
            verify(ps).setInt(1, 1);
        }
    }

    @Test
    public void get_queriesBranchInfoByRowId() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.get();

            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            verify(conn).prepareStatement(sql.capture());
            assertTrue(sql.getValue().contains("FROM branch_info WHERE branch_info_id = ?"));
        }
    }

    @Test
    public void get_seededRowWithOnlyPosition_mapsNullColumnsToNull() throws Exception {
        // Đúng dòng V39 gieo: chỉ có chức vụ, chưa ai sửa (updated_by NULL).
        ResultSet rs = singleRow(row(
                "director_name", null,
                "director_position", "Giám đốc chi nhánh",
                "phone", null,
                "fax", null,
                "updated_by", null,
                "updated_at", Timestamp.valueOf("2026-11-19 08:00:00")));
        Connection conn = connectionReturning(statementReturning(rs));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            BranchInfo info = dao.get();

            assertNotNull(info);
            assertNull(info.getDirectorName());
            assertEquals("Giám đốc chi nhánh", info.getDirectorPosition());
            assertNull(info.getPhone());
            assertNull(info.getFax());
            // getInt() trả 0 cho NULL; phải ra null chứ không phải user 0.
            assertNull(info.getUpdatedBy());
        }
    }

    @Test
    public void get_noRow_returnsNull() throws Exception {
        Connection conn = connectionReturning(statementReturning(emptyResultSet()));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertNull(dao.get());
        }
    }

    @Test
    public void get_connectionFails_returnsNull() {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertNull(dao.get());
        }
    }

    @Test
    public void get_queryFails_returnsNull() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenThrow(new SQLException("Table 'branch_info' doesn't exist"));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertNull(dao.get());
        }
    }

    // ------------------------------------------------------------------
    // update
    // ------------------------------------------------------------------

    @Test
    public void update_success_bindsParametersInOrderWithActorId() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.update(sampleInfo(), 9));

            verify(ps).setString(1, "Nguyễn Văn An");
            verify(ps).setString(2, "Giám đốc chi nhánh");
            verify(ps).setString(3, "024 3856 1234");
            verify(ps).setString(4, "(024) 3856 1235");
            verify(ps).setInt(5, 9);
            verify(ps).setInt(6, 1);
        }
    }

    @Test
    public void update_sqlSetsFourFieldsAndUpdatedByOnRowOne() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.update(sampleInfo(), 9);

            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            verify(conn).prepareStatement(sql.capture());
            String s = sql.getValue();
            assertTrue(s.startsWith("UPDATE branch_info SET "));
            assertTrue(s.contains("director_name = ?, director_position = ?, phone = ?, fax = ?, updated_by = ?"));
            assertTrue(s.endsWith("WHERE branch_info_id = ?"));
        }
    }

    @Test
    public void update_optionalPhoneAndFaxNull_bindsNull() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);
        BranchInfo info = sampleInfo();
        info.setPhone(null);
        info.setFax(null);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.update(info, 9));

            verify(ps).setString(3, null);
            verify(ps).setString(4, null);
        }
    }

    @Test
    public void update_nullInfo_returnsFalseWithoutTouchingDb() {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            assertFalse(dao.update(null, 9));
            db.verifyNoInteractions();
        }
    }

    @Test
    public void update_nonPositiveActorId_returnsFalseWithoutTouchingDb() {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            assertFalse(dao.update(sampleInfo(), 0));
            assertFalse(dao.update(sampleInfo(), -3));
            db.verifyNoInteractions();
        }
    }

    @Test
    public void update_noRowMatched_returnsFalse() throws Exception {
        // Dòng id = 1 không có (CSDL chưa chạy V39 hoặc bị xoá tay).
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(0);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.update(sampleInfo(), 9));
        }
    }

    @Test
    public void update_statementFails_returnsFalse() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenThrow(new SQLException("khoá ngoại updated_by sai", "23000", 1452));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.update(sampleInfo(), 9));
        }
    }

    @Test
    public void update_connectionFails_returnsFalse() {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertFalse(dao.update(sampleInfo(), 9));
        }
    }
}
