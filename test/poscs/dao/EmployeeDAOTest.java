package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Test cho EmployeeDAO. Ba mảng có logic Java thật sự:
 *
 * <ul>
 *   <li>{@code generateUniqueUsername} -- bỏ dấu tiếng Việt rồi dò số đuôi cho
 *       tới khi trống. Người tạo nhân viên không được gõ username (BR-28), nên
 *       nếu hàm này trả về tên đã có thì INSERT sẽ vỡ ở tầng CSDL.</li>
 *   <li>{@code existsByEmail/Phone/CitizenId} (BR-27) -- khi đọc lỗi thì cố ý
 *       trả <b>true</b> ("coi như đã trùng"), chặn ở tầng ứng dụng thay vì thả
 *       cho UNIQUE KEY của CSDL từ chối bằng một trang lỗi khó hiểu.</li>
 *   <li>{@code findAll} -- bộ lọc động và phép tính OFFSET của phân trang.</li>
 * </ul>
 *
 * Xem {@link JdbcStub} để biết những test này KHÔNG kiểm chứng gì (SQL, tên
 * cột, schema). JUnit 4 -- xem CustomerControllerTest.
 */
public class EmployeeDAOTest {

    private final EmployeeDAO dao = new EmployeeDAO();

    /** PreparedStatement mà executeQuery() trả về lần lượt từng ResultSet đã dựng sẵn. */
    private static PreparedStatement statementReturningInTurn(ResultSet first, ResultSet... rest)
            throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(first, rest);
        return ps;
    }

    // ------------------------------------------------------------------
    // generateUniqueUsername
    // ------------------------------------------------------------------

    @Test
    public void generateUniqueUsername_stripsVietnameseDiacriticsAndSpaces() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet()); // chưa ai dùng
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals("nguyenvana", dao.generateUniqueUsername("Nguyễn", "Văn", "A"));
        }
    }

    @Test
    public void generateUniqueUsername_mapsDStrokeToPlainD() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // "đ" không phải chữ d kèm dấu phụ nên NFD không tách được -- phải
            // thay tay, nếu sót thì regex [^a-z0-9] nuốt mất cả chữ cái.
            assertEquals("dangdinhdung", dao.generateUniqueUsername("Đặng", "Đình", "Dũng"));
        }
    }

    @Test
    public void generateUniqueUsername_allNamesEmpty_fallsBackToDefaultBase() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Chuỗi rỗng làm username sẽ vi phạm NOT NULL/UNIQUE ngay lần thứ hai.
            assertEquals("nhanvien", dao.generateUniqueUsername(null, null, null));
        }
    }

    @Test
    public void generateUniqueUsername_nameTaken_appendsCounterStartingAtTwo() throws Exception {
        ResultSet taken = singleRow(row("1", 1));
        ResultSet free = emptyResultSet();
        PreparedStatement ps = statementReturningInTurn(taken, free);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Đếm bắt đầu từ 2, không phải 1 -- "nguyenvana1" cạnh
            // "nguyenvana" đọc rất khó hiểu.
            assertEquals("nguyenvana2", dao.generateUniqueUsername("Nguyễn", "Văn", "A"));
        }
    }

    @Test
    public void generateUniqueUsername_severalNamesTaken_keepsCounting() throws Exception {
        ResultSet taken1 = singleRow(row("1", 1));
        ResultSet taken2 = singleRow(row("1", 1));
        ResultSet free = emptyResultSet();
        PreparedStatement ps = statementReturningInTurn(taken1, taken2, free);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals("nguyenvana3", dao.generateUniqueUsername("Nguyễn", "Văn", "A"));
        }
    }

    // ------------------------------------------------------------------
    // existsBy* (BR-27) -- trùng lặp email/SĐT/CCCD
    // ------------------------------------------------------------------

    @Test
    public void existsByEmail_rowFound_returnsTrue() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("1", 1)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.existsByEmail("an@congty.vn", null));
            verify(ps).setString(1, "an@congty.vn");
            verify(ps, never()).setInt(anyInt(), anyInt());
        }
    }

    @Test
    public void existsByEmail_noRow_returnsFalse() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.existsByEmail("moi@congty.vn", null));
        }
    }

    @Test
    public void existsByEmail_editingOwnRecord_excludesThatUserId() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.existsByEmail("an@congty.vn", 7));

            // Không loại trừ chính mình thì sửa hồ sơ mà giữ nguyên email sẽ bị
            // báo "email đã tồn tại" -- do chính dòng của mình.
            verify(ps).setInt(2, 7);
        }
    }

    @Test
    public void existsByPhone_sqlError_returnsTrueToBlockTheWrite() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            // Trả false ở đây sẽ để lệnh ghi chạy tiếp rồi vỡ ở UNIQUE KEY của
            // CSDL, người dùng nhận trang 500 thay vì thông báo dễ hiểu.
            assertTrue(dao.existsByPhone("0901234567", null));
        }
    }

    @Test
    public void existsByCitizenId_sqlError_returnsTrueToBlockTheWrite() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertTrue(dao.existsByCitizenId("001099012345", null));
        }
    }

    // ------------------------------------------------------------------
    // findAll / countAll -- lọc động + phân trang
    // ------------------------------------------------------------------

    @Test
    public void findAll_firstPage_usesZeroOffset() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null);

            verify(ps).setObject(1, 10); // LIMIT
            verify(ps).setObject(2, 0);  // OFFSET
        }
    }

    @Test
    public void findAll_thirdPage_offsetSkipsPrecedingPages() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(3, 10, null, null, null);

            // (3-1)*10 -- lệch một trang ở đây làm người dùng mất hoặc thấy lặp
            // đúng 10 nhân viên mà không có dấu hiệu gì.
            verify(ps).setObject(2, 20);
        }
    }

    @Test
    public void findAll_keyword_isWrappedInWildcardsAndBoundToEverySearchedColumn() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, "  an  ", null, null);

            verify(conn).prepareStatement(sqlCaptor.capture());
            assertTrue(sqlCaptor.getValue().contains("last_name LIKE ?"));
            // Từ khoá được trim rồi bọc %...% và lặp cho cả 5 cột tìm kiếm.
            verify(ps, times(5)).setObject(anyInt(), eq("%an%"));
        }
    }

    @Test
    public void findAll_blankKeyword_addsNoSearchClause() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, "   ", null, null);

            verify(conn).prepareStatement(sqlCaptor.capture());
            assertFalse("Ô tìm kiếm bỏ trống không được sinh mệnh đề LIKE",
                    sqlCaptor.getValue().contains("LIKE"));
        }
    }

    @Test
    public void findAll_statusFilter_mapsOntoIsDeletedFlag() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, "Inactive", null);

            verify(conn).prepareStatement(sqlCaptor.capture());
            assertTrue(sqlCaptor.getValue().contains("u.is_deleted = 1"));
        }
    }

    @Test
    public void findAll_unknownStatusFilter_isIgnoredRatherThanReturningNothing() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, "Xoá hết đi", null);

            verify(conn).prepareStatement(sqlCaptor.capture());
            assertFalse(sqlCaptor.getValue().contains("is_deleted ="));
        }
    }

    @Test
    public void findAll_roleFilter_isBoundBeforePagingParameters() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, 4);

            // Thứ tự bind phải khớp thứ tự dấu ? trong câu SQL: vai trò trước,
            // rồi mới tới LIMIT/OFFSET.
            verify(ps).setObject(1, 4);
            verify(ps).setObject(2, 10);
            verify(ps).setObject(3, 0);
        }
    }

    @Test
    public void countAll_returnsCount() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("c", 42)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(42, dao.countAll(null, null, null));
        }
    }

    @Test
    public void countAll_sqlError_returnsZero() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertEquals(0, dao.countAll(null, null, null));
        }
    }

    @Test
    public void findAll_sqlError_returnsEmptyListNotNull() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertNotNull(dao.findAll(1, 10, null, null, null));
            assertTrue(dao.findAll(1, 10, null, null, null).isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // Khoá/mở khoá + đổi mật khẩu
    // ------------------------------------------------------------------

    @Test
    public void setActive_unlocking_clearsIsDeletedFlag() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.setActive(7, true));
            // Cột lưu là is_deleted nên giá trị phải đảo so với "active".
            verify(ps).setBoolean(1, false);
            verify(ps).setInt(2, 7);
        }
    }

    @Test
    public void setActive_locking_setsIsDeletedFlag() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.setActive(7, false));
            verify(ps).setBoolean(1, true);
        }
    }

    @Test
    public void updatePasswordByEmail_exactlyOneRow_returnsTrue() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.updatePasswordByEmail("an@congty.vn", "$2a$10$hash"));
            verify(ps).setString(1, "$2a$10$hash");
            verify(ps).setString(2, "an@congty.vn");
        }
    }

    @Test
    public void updatePasswordByEmail_multipleRowsTouched_returnsFalse() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(2);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Hàm này so == 1 chứ không phải > 0: đổi mật khẩu trúng nhiều tài
            // khoản nghĩa là dữ liệu email đang hỏng, không nên báo thành công.
            assertFalse(dao.updatePasswordByEmail("an@congty.vn", "$2a$10$hash"));
        }
    }

    @Test
    public void updatePasswordByEmail_noMatchingAccount_returnsFalse() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(0);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.updatePasswordByEmail("khong-ton-tai@congty.vn", "$2a$10$hash"));
        }
    }

    @Test
    public void updatePasswordHash_sqlError_returnsFalse() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertFalse(dao.updatePasswordHash(7, "$2a$10$hash"));
        }
    }
}
