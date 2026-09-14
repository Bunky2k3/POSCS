package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Test cho TerritoryDAO -- phân công địa bàn (ai cầm tỉnh nào).
 *
 * <p>Trọng tâm là những chỗ sai thì hỏng đúng cái quy tắc khách hàng chốt:
 *
 * <ul>
 *   <li>"chưa ai cầm tỉnh này" phải trả null, không phải 0 hay nổ -- đó là
 *       trạng thái MẶC ĐỊNH lúc mới bật tính năng, không phải lỗi;</li>
 *   <li>đặt lại địa bàn phải nằm trong MỘT transaction: xoá xong mà chèn lỗi
 *       thì người đó mất sạch địa bàn cũ, và không ai biết cho tới khi có
 *       khách hàng rơi vào tỉnh đó;</li>
 *   <li>danh sách rỗng vẫn phải xoá hết (gỡ toàn bộ địa bàn của một người là
 *       thao tác hợp lệ), chứ không được lặng lẽ giữ nguyên.</li>
 * </ul>
 *
 * Xem {@link JdbcStub} để biết những test này KHÔNG kiểm chứng gì (SQL, tên
 * cột, ràng buộc UNIQUE -- cái đó thuộc về test tích hợp).
 */
public class TerritoryDAOTest {

    private final TerritoryDAO dao = new TerritoryDAO();

    // ------------------------------------------------------------------
    // findAssigneeOf
    // ------------------------------------------------------------------

    @Test
    public void findAssigneeOf_tinhDaCoNguoiCam_traVeUserId() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("user_id", 21)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(Integer.valueOf(21), dao.findAssigneeOf(3));
            verify(ps).setInt(1, 3);
        }
    }

    /**
     * Chưa ai cầm tỉnh là trạng thái hợp lệ -- phải null để bên gọi phân biệt
     * được với "cầm bởi user 0". Trả 0 ở đây là form tạo khách hàng sẽ đi tìm
     * một nhân viên không tồn tại.
     */
    @Test
    public void findAssigneeOf_chuaAiCam_traVeNull() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertNull(dao.findAssigneeOf(99));
        }
    }

    @Test
    public void findAssigneeOf_loiCsdl_traVeNullChuKhongNem() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertNull(dao.findAssigneeOf(3));
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
}
