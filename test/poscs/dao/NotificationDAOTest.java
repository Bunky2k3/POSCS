package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.mockito.MockedStatic;
import poscs.model.Notification;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Test cho NotificationDAO. Trọng tâm là chỗ DAO này khác các DAO còn lại:
 * khi đọc lỗi, {@code existsForUserAndRef} cố tình trả về <b>true</b> ("coi như
 * đã có") chứ không phải false -- vì bên gọi là NotificationScheduler chạy lặp
 * mỗi giờ, trả false khi CSDL trục trặc sẽ khiến job tưởng chưa có thông báo
 * nào và tạo lại mỗi chu kỳ, dội trùng lặp vào chuông người dùng.
 *
 * Xem {@link JdbcStub} để biết những test này KHÔNG kiểm chứng được gì (SQL,
 * tên cột, schema). JUnit 4 -- xem CustomerControllerTest.
 */
public class NotificationDAOTest {

    private final NotificationDAO dao = new NotificationDAO();

    // ------------------------------------------------------------------
    // existsForUserAndRef -- chống tạo thông báo trùng lặp
    // ------------------------------------------------------------------

    @Test
    public void existsForUserAndRef_rowFound_returnsTrue() throws Exception {
        ResultSet rs = singleRow(row("1", 1));
        PreparedStatement ps = statementReturning(rs);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.existsForUserAndRef(7, "contract_expiring", 42));
            verify(ps).setInt(1, 7);
            verify(ps).setString(2, "contract_expiring");
            verify(ps).setInt(3, 42);
        }
    }

    @Test
    public void existsForUserAndRef_noRow_returnsFalse() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.existsForUserAndRef(7, "contract_expiring", 42));
        }
    }

    @Test
    public void existsForUserAndRef_sqlError_returnsTrueToAvoidDuplicateSpam() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("CSDL mất kết nối"));

            // Lỗi đọc phải nghiêng về "đã có" -- trả false ở đây khiến scheduler
            // tạo lại thông báo mỗi chu kỳ suốt thời gian CSDL còn trục trặc.
            assertTrue("Lỗi đọc phải coi như đã tồn tại", dao.existsForUserAndRef(7, "ticket_sla", 9));
        }
    }

    // ------------------------------------------------------------------
    // insert
    // ------------------------------------------------------------------

    @Test
    public void insert_nullRefId_bindsSqlNullInsteadOfZero() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.insert(7, "Thông báo chung", null, null));
            // setInt(4, 0) sẽ ghi số 0 vào ref_id thay vì NULL, biến thông báo
            // rời thành thông báo "gắn với đối tượng số 0".
            verify(ps).setNull(4, java.sql.Types.INTEGER);
            verify(ps, never()).setInt(eq(4), anyInt());
        }
    }

    @Test
    public void insert_withRefId_bindsRefId() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.insert(7, "Hợp đồng sắp hết hạn", "contract_expiring", 42));
            verify(ps).setInt(4, 42);
            verify(ps, never()).setNull(eq(4), anyInt());
        }
    }

    @Test
    public void insert_noRowAffected_returnsFalse() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(0);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.insert(7, "Tiêu đề", null, null));
        }
    }

    @Test
    public void insert_sqlError_returnsFalse() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertFalse(dao.insert(7, "Tiêu đề", null, null));
        }
    }

    // ------------------------------------------------------------------
    // Đọc danh sách + ánh xạ
    // ------------------------------------------------------------------

    @Test
    public void findAllByUser_mapsEveryColumnOntoModel() throws Exception {
        Timestamp createdAt = Timestamp.valueOf("2026-09-09 08:30:00");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("notification_id", 3, "user_id", 7, "title", "Phiếu quá hạn SLA",
                "is_read", 1, "created_at", createdAt));
        PreparedStatement ps = statementReturning(resultSetOf(rows));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<Notification> found = dao.findAllByUser(7);

            assertEquals(1, found.size());
            Notification n = found.get(0);
            assertEquals(3, n.getNotificationId());
            assertEquals(7, n.getUserId());
            assertEquals("Phiếu quá hạn SLA", n.getTitle());
            assertTrue(n.isRead());
            assertEquals(createdAt, n.getCreatedAt());
        }
    }

    @Test
    public void findAllByUser_bindsNoLimitParameter() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAllByUser(7);

            // "Xem tất cả" đi qua findByUser(userId, -1) -- câu SQL không có
            // mệnh đề LIMIT nên chỉ được bind đúng 1 tham số.
            verify(ps).setInt(1, 7);
            verify(ps, never()).setInt(eq(2), anyInt());
        }
    }

    @Test
    public void findRecentByUser_bindsLimitAsSecondParameter() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findRecentByUser(7, 5);

            verify(ps).setInt(1, 7);
            verify(ps).setInt(2, 5);
        }
    }

    @Test
    public void findAllByUser_sqlError_returnsEmptyListNotNull() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            List<Notification> found = dao.findAllByUser(7);

            // Trả null sẽ làm topbar.jsp nổ NullPointerException ở mọi trang.
            assertNotNull(found);
            assertTrue(found.isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // Đếm + đánh dấu đã đọc
    // ------------------------------------------------------------------

    @Test
    public void countUnread_returnsCount() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("c", 4)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(4, dao.countUnread(7));
        }
    }

    @Test
    public void countUnread_sqlError_returnsZero() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertEquals(0, dao.countUnread(7));
        }
    }

    @Test
    public void markAsRead_scopesUpdateToOwningUser() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.markAsRead(3, 7));

            // user_id phải nằm trong WHERE, nếu không ai cũng đánh dấu hộ được
            // thông báo của người khác chỉ bằng cách đoán notification_id.
            verify(ps).setInt(1, 3);
            verify(ps).setInt(2, 7);
        }
    }

    @Test
    public void markAsRead_notificationBelongsToAnotherUser_returnsFalse() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(0); // WHERE user_id = ? không khớp dòng nào
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.markAsRead(3, 999));
        }
    }

    @Test
    public void markAllAsRead_returnsAffectedRowCount() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(6);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(6, dao.markAllAsRead(7));
            verify(ps).setInt(1, 7);
        }
    }

    @Test
    public void markAllAsRead_sqlError_returnsZero() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertEquals(0, dao.markAllAsRead(7));
        }
    }
}
