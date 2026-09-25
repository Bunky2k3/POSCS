package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import poscs.model.ProvinceAssignment;
import poscs.model.User;

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
 *   <li>{@code existsByPhone/CitizenId} (BR-27) -- khi đọc lỗi thì cố ý
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
    // existsBy* (BR-27) -- trùng lặp SĐT/CCCD (existsByEmail đã xoá cùng cột
    // email công ty, V36)
    // ------------------------------------------------------------------

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

    /**
     * Sắp theo created_at thôi là chưa đủ: nhiều người trùng mốc tạo thì MySQL
     * trả các dòng đó theo thứ tự tuỳ ý ở mỗi câu LIMIT/OFFSET -- chạy thật
     * trên bản sao CSDL, "Ngô Văn Hiếu" hiện ở cả trang 1 lẫn trang 2 còn
     * "Vũ Đình Nam" không ở trang nào. Phải có khoá duy nhất đứng sau.
     */
    @Test
    public void findAll_ordersByAUniqueTieBreakerSoPagesNeitherRepeatNorSkip() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(2, 10, null, null, null);

            verify(conn).prepareStatement(sqlCaptor.capture());
            assertTrue(sqlCaptor.getValue().contains("ORDER BY u.created_at DESC, u.user_id DESC"));
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
            // Từ khoá được trim rồi bọc %...% và lặp cho cả 4 cột tìm kiếm
            // (họ, tên đệm, tên, SĐT -- không còn email từ V36).
            verify(ps, times(4)).setObject(anyInt(), eq("%an%"));
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
    // findActiveByRole -- lọc dropdown "người phụ trách" theo vai
    // ------------------------------------------------------------------
    //
    // Cái sai mà những test này canh: dropdown đổ TOÀN BỘ nhân viên vào, nên
    // ô "Người phụ trách khách hàng" mời chọn cả Admin lẫn kỹ thuật viên.
    // Ràng buộc thứ hai quan trọng không kém: người ĐANG được gán phải còn
    // trong danh sách kể cả khi đã đổi vai, nếu không thì mở form sửa lên ô
    // trống rồi bấm lưu là thay mất người phụ trách.

    @Test
    public void findActiveByRole_khongCoAlsoInclude_chiLocTheoVai() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row(
                "user_id", 20, "username", "sales2", "last_name", "Tran",
                "middle_name", "Thi", "first_name", "Ha", "department_id", 2)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<User> result = dao.findActiveByRole("Sales");

            assertEquals(1, result.size());
            assertEquals(20, result.get(0).getUserId());
            verify(ps).setString(1, "Sales");
            // Không có ai để giữ thêm thì không được sinh tham số thừa.
            verify(ps, never()).setInt(eq(2), anyInt());
        }
    }

    @Test
    public void findActiveByRole_coAlsoInclude_ganThamSoChoTungNguoi() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findActiveByRole("Sales", 7, 9);

            verify(ps).setString(1, "Sales");
            verify(ps).setInt(2, 7);
            verify(ps).setInt(3, 9);
        }
    }

    /**
     * Cột người hỗ trợ là nullable nên bên gọi truyền thẳng vào được; null và
     * 0 phải bị bỏ qua chứ không thành tham số rỗng làm lệch thứ tự đặt tham
     * số của những id hợp lệ đứng sau.
     */
    @Test
    public void findActiveByRole_boQuaNullVaSoKhongVaTrungLap() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findActiveByRole("Sales", null, 0, 7, 7);

            verify(ps).setInt(2, 7);
            verify(ps, never()).setInt(eq(3), anyInt());
        }
    }

    @Test
    public void findActiveByRole_loiCsdl_traVeDanhSachRongChuKhongNem() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertTrue(dao.findActiveByRole("Sales").isEmpty());
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
    public void updatePasswordByUsername_exactlyOneRow_returnsTrue() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.updatePasswordByUsername("annd", "$2a$10$hash"));
            verify(ps).setString(1, "$2a$10$hash");
            verify(ps).setString(2, "annd");
        }
    }

    @Test
    public void updatePasswordByUsername_multipleRowsTouched_returnsFalse() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(2);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Hàm này so == 1 chứ không phải > 0: đổi mật khẩu trúng nhiều tài
            // khoản nghĩa là dữ liệu username đang hỏng, không nên báo thành công.
            assertFalse(dao.updatePasswordByUsername("annd", "$2a$10$hash"));
        }
    }

    @Test
    public void updatePasswordByUsername_noMatchingAccount_returnsFalse() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(0);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.updatePasswordByUsername("khong-ton-tai", "$2a$10$hash"));
        }
    }

    @Test
    public void updatePasswordHash_sqlError_returnsFalse() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertFalse(dao.updatePasswordHash(7, "$2a$10$hash"));
        }
    }

    // ==================================================================
    // Phân công địa bàn (user_provinces) -- gộp từ TerritoryDAOTest
    // ==================================================================
    //
    // Trọng tâm là những chỗ sai thì hỏng đúng quy tắc khách hàng chốt:
    //
    //   * "chưa ai cầm tỉnh này" phải trả null, không phải 0 hay nổ -- đó là
    //     trạng thái MẶC ĐỊNH lúc mới bật tính năng, không phải lỗi (xem
    //     findAssigneeOfWard);
    //   * đặt lại địa bàn phải nằm trong MỘT transaction: xoá xong mà chèn
    //     lỗi thì người đó mất sạch địa bàn cũ, và không ai biết cho tới khi
    //     có khách hàng rơi vào tỉnh đó;
    //   * danh sách rỗng vẫn phải xoá hết (gỡ toàn bộ địa bàn của một người
    //     là thao tác hợp lệ), chứ không được lặng lẽ giữ nguyên.
    //
    // Xem JdbcStub để biết những test này KHÔNG kiểm chứng gì (SQL, tên cột,
    // ràng buộc UNIQUE -- cái đó thuộc về test tích hợp).

    // ------------------------------------------------------------------
    // findAssigneeOfWard -- căn cứ để khoá ô người phụ trách
    // ------------------------------------------------------------------

    @Test
    public void findAssigneeOfWard_tinhChuaXaPhuongDaCoNguoiCam_traVeUserId() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("user_id", 42)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(Integer.valueOf(42), dao.findAssigneeOfWard(10));
            verify(ps).setInt(1, 10);
        }
    }

    /**
     * Null ở đây KHÔNG phải lỗi: CustomerController đọc nó là "tỉnh chưa ai
     * cầm" rồi mở ô người phụ trách cho người dùng tự chọn. Trả 0 thì khách
     * hàng được gán cho một nhân viên không tồn tại và INSERT vỡ ở tầng FK.
     */
    @Test
    public void findAssigneeOfWard_tinhChuaAiCam_traVeNull() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertNull(dao.findAssigneeOfWard(10));
        }
    }

    /**
     * Lỗi CSDL làm khoá HỞ ra chứ không chặn người dùng: cùng đường với "tỉnh
     * chưa ai cầm". Đánh đổi có chủ ý -- fail-closed ở đây nghĩa là CSDL chập
     * một nhịp thì không ai tạo được khách hàng nữa.
     */
    @Test
    public void findAssigneeOfWard_loiCsdl_traVeNullChuKhongNem() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertNull(dao.findAssigneeOfWard(10));
        }
    }

    // ------------------------------------------------------------------
    // replaceProvincesOf -- transaction
    // ------------------------------------------------------------------

    /** Connection giả tách riêng câu DELETE và câu INSERT để kiểm được thứ tự. */
    private Connection transactionalConnection(PreparedStatement del, PreparedStatement ins) throws SQLException {
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("DELETE"))).thenReturn(del);
        when(conn.prepareStatement(contains("INSERT"))).thenReturn(ins);
        return conn;
    }

    @Test
    public void replaceProvincesOf_xoaCuRoiChenMoi_trongMotTransaction() throws Exception {
        PreparedStatement del = mock(PreparedStatement.class);
        PreparedStatement ins = mock(PreparedStatement.class);
        Connection conn = transactionalConnection(del, ins);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.replaceProvincesOf(21, List.of(3, 4, 8)));

            verify(conn).setAutoCommit(false);
            verify(del).setInt(1, 21);
            verify(del).executeUpdate();
            verify(ins, times(3)).addBatch();
            verify(ins).executeBatch();
            verify(conn).commit();
            verify(conn, never()).rollback();
        }
    }

    /**
     * Gỡ toàn bộ địa bàn của một người là thao tác hợp lệ (họ chuyển vai,
     * nghỉ việc...). Danh sách rỗng vẫn phải chạy câu DELETE, không được coi
     * là "không có gì để làm" rồi bỏ qua.
     */
    @Test
    public void replaceProvincesOf_danhSachRong_vanXoaHetDiaBanCu() throws Exception {
        PreparedStatement del = mock(PreparedStatement.class);
        PreparedStatement ins = mock(PreparedStatement.class);
        Connection conn = transactionalConnection(del, ins);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.replaceProvincesOf(21, List.of()));

            verify(del).executeUpdate();
            verify(ins, never()).executeBatch();
            verify(conn).commit();
        }
    }

    /**
     * Chèn lỗi (thường là tỉnh đang do người khác cầm -- vi phạm UNIQUE) thì
     * phải rollback: nếu không, người này mất sạch địa bàn cũ mà chưa nhận
     * được địa bàn mới, và chẳng có thông báo nào.
     */
    @Test
    public void replaceProvincesOf_chenLoi_rollbackDeKhongMatDiaBanCu() throws Exception {
        PreparedStatement del = mock(PreparedStatement.class);
        PreparedStatement ins = mock(PreparedStatement.class);
        when(ins.executeBatch()).thenThrow(new SQLException("Duplicate entry for key 'uq_user_provinces_province'"));
        Connection conn = transactionalConnection(del, ins);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.replaceProvincesOf(21, List.of(3)));

            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void replaceProvincesOf_loiKetNoi_traVeFalse() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertFalse(dao.replaceProvincesOf(21, List.of(3)));
        }
    }

    // ------------------------------------------------------------------
    // Đọc danh sách
    // ------------------------------------------------------------------

    @Test
    public void findProvincesOf_loiCsdl_traVeListRongChuKhongNull() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertNotNull(dao.findProvincesOf(21));
            assertTrue(dao.findProvincesOf(21).isEmpty());
        }
    }

    @Test
    public void findAllAssignments_loiCsdl_traVeMapRongChuKhongNull() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertNotNull(dao.findAllAssignments());
            assertTrue(dao.findAllAssignments().isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // findAllProvincesWithHolder -- form thêm/sửa nhân viên (đổ CẢ tỉnh đã
    // có người cầm, kèm tên người đó, để Admin thấy vì sao khoá)
    // ------------------------------------------------------------------

    @Test
    public void findAllProvincesWithHolder_gomCaTinhChuaAiCamVaTinhDaCoNguoiCam() throws Exception {
        ResultSet rs = resultSetOf(List.of(
                row("province_id", 1, "province_name", "Tỉnh A"), // holder_id vắng mặt = NULL
                row("province_id", 2, "province_name", "Tỉnh B", "holder_id", 7,
                        "last_name", "Nguyễn", "first_name", "An")
        ));
        Connection conn = connectionReturning(statementReturning(rs));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ProvinceAssignment> result = dao.findAllProvincesWithHolder();

            assertEquals(2, result.size());
            assertNull(result.get(0).getHolderUserId());
            assertNull(result.get(0).getHolderName());
            assertEquals(Integer.valueOf(7), result.get(1).getHolderUserId());
            // Ghép họ tên đúng luật User.getFullName() (không có tên đệm).
            assertEquals("Nguyễn An", result.get(1).getHolderName());
        }
    }

    @Test
    public void findAllProvincesWithHolder_loiCsdl_traVeListRongChuKhongNull() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertNotNull(dao.findAllProvincesWithHolder());
            assertTrue(dao.findAllProvincesWithHolder().isEmpty());
        }
    }
}
