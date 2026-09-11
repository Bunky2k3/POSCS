package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import org.junit.Test;
import org.mockito.MockedStatic;
import poscs.model.TechnicalRequest;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Test cho TechnicalSupportTicketDAO. Trọng tâm:
 *
 * <ul>
 *   <li>{@code canDelete} (business rule) -- chỉ phiếu chưa ai xử lý dở dang
 *       mới được xoá, và mọi trường hợp không xác định được (phiếu không tồn
 *       tại, CSDL lỗi) đều phải nghiêng về <b>không cho xoá</b>.</li>
 *   <li>{@code countStatusSummary} -- dashboard luôn cần đủ 4 ô số; thiếu khoá
 *       nào là chỗ đó nổ NullPointerException khi unbox.</li>
 *   <li>{@code update} -- {@code resolved_at} null phải ghi SQL NULL chứ không
 *       phải mốc thời gian rỗng.</li>
 * </ul>
 *
 * Xem {@link JdbcStub} để biết những test này KHÔNG kiểm chứng gì (SQL, tên
 * cột, schema). JUnit 4 -- xem CustomerControllerTest.
 */
public class TechnicalSupportTicketDAOTest {

    private final TechnicalSupportTicketDAO dao = new TechnicalSupportTicketDAO();

    private static TechnicalRequest ticket() {
        TechnicalRequest t = new TechnicalRequest();
        t.setTicketId(9);
        t.setEnterpriseId(3);
        t.setTicketType("Sự cố");
        t.setPriority("Cao");
        t.setReceptionChannel("Điện thoại");
        t.setAssignedTechnicianId(5);
        t.setDescription("Mất tín hiệu");
        t.setStatus(TechnicalSupportTicketDAO.STATUS_IN_PROGRESS);
        return t;
    }

    // ------------------------------------------------------------------
    // canDelete -- chặn xoá phiếu đang xử lý
    // ------------------------------------------------------------------

    @Test
    public void canDelete_ticketInProgress_returnsFalse() throws Exception {
        PreparedStatement ps = statementReturning(
                singleRow(row("status", TechnicalSupportTicketDAO.STATUS_IN_PROGRESS)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.canDelete(9));
            verify(ps).setInt(1, 9);
        }
    }

    @Test
    public void canDelete_newTicket_returnsTrue() throws Exception {
        PreparedStatement ps = statementReturning(
                singleRow(row("status", TechnicalSupportTicketDAO.STATUS_NEW)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.canDelete(9));
        }
    }

    @Test
    public void canDelete_closedTicket_returnsTrue() throws Exception {
        PreparedStatement ps = statementReturning(
                singleRow(row("status", TechnicalSupportTicketDAO.STATUS_CLOSED)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.canDelete(9));
        }
    }

    @Test
    public void canDelete_ticketNotFound_returnsFalse() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Phiếu đã bị xoá mềm hoặc id bịa ra -- không có gì để xoá.
            assertFalse(dao.canDelete(999));
        }
    }

    @Test
    public void canDelete_sqlError_returnsFalse() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            // Không đọc được trạng thái thì không được đoán bừa là xoá được.
            assertFalse(dao.canDelete(9));
        }
    }

    // ------------------------------------------------------------------
    // countStatusSummary -- dashboard cần đủ 4 khoá
    // ------------------------------------------------------------------

    @Test
    public void countStatusSummary_readsEveryBucket() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row(
                "new_count", 4, "progress_count", 7, "closed_count", 12, "urgent_count", 2)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Map<String, Integer> summary = dao.countStatusSummary();

            assertEquals(Integer.valueOf(4), summary.get(TechnicalSupportTicketDAO.STATUS_NEW));
            assertEquals(Integer.valueOf(7), summary.get(TechnicalSupportTicketDAO.STATUS_IN_PROGRESS));
            assertEquals(Integer.valueOf(12), summary.get(TechnicalSupportTicketDAO.STATUS_CLOSED));
            assertEquals(Integer.valueOf(2), summary.get("Khẩn cấp"));
        }
    }

    @Test
    public void countStatusSummary_emptyTable_stillReturnsAllFourKeysAtZero() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Map<String, Integer> summary = dao.countStatusSummary();

            // Dashboard unbox thẳng các giá trị này -- thiếu khoá nào là
            // NullPointerException tại đúng ô đó.
            assertEquals(Integer.valueOf(0), summary.get(TechnicalSupportTicketDAO.STATUS_NEW));
            assertEquals(Integer.valueOf(0), summary.get(TechnicalSupportTicketDAO.STATUS_IN_PROGRESS));
            assertEquals(Integer.valueOf(0), summary.get(TechnicalSupportTicketDAO.STATUS_CLOSED));
            assertEquals(Integer.valueOf(0), summary.get("Khẩn cấp"));
        }
    }

    @Test
    public void countStatusSummary_sqlError_stillReturnsAllFourKeysAtZero() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            Map<String, Integer> summary = dao.countStatusSummary();

            assertEquals(4, summary.size());
            for (Integer value : summary.values()) {
                assertEquals(Integer.valueOf(0), value);
            }
        }
    }

    // ------------------------------------------------------------------
    // update -- cột nullable, transaction, và lịch sử đổi trạng thái
    // ------------------------------------------------------------------

    private static final int CHANGED_BY = 7;

    /** PreparedStatement của câu UPDATE trong lần gọi update() vừa rồi. */
    private PreparedStatement updatePs;
    /** PreparedStatement của câu INSERT vào technicalrequesthistory (nếu có gọi). */
    private PreparedStatement historyPs;

    /**
     * Connection giả cho luồng update() có transaction: trả về 3 statement khác
     * nhau cho 3 câu SQL (SELECT ... FOR UPDATE / UPDATE / INSERT lịch sử), nhờ
     * vậy test phân biệt được câu nào đã chạy -- {@link JdbcStub#connectionReturning}
     * trả chung một mock nên không làm được việc đó.
     *
     * @param currentStatus trạng thái đang lưu trong CSDL; null = phiếu không còn.
     * @param affectedRows  số dòng câu UPDATE tác động.
     */
    private Connection transactionalConnection(String currentStatus, int affectedRows) throws Exception {
        PreparedStatement selectPs = statementReturning(currentStatus == null
                ? resultSetOf(java.util.List.of())
                : singleRow(row("status", currentStatus)));
        updatePs = mock(PreparedStatement.class);
        when(updatePs.executeUpdate()).thenReturn(affectedRows);
        historyPs = mock(PreparedStatement.class);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("FOR UPDATE"))).thenReturn(selectPs);
        when(conn.prepareStatement(contains("UPDATE technicalrequests"))).thenReturn(updatePs);
        when(conn.prepareStatement(contains("technicalrequesthistory"))).thenReturn(historyPs);
        return conn;
    }

    @Test
    public void update_openTicket_bindsSqlNullForResolvedAt() throws Exception {
        Connection conn = transactionalConnection(TechnicalSupportTicketDAO.STATUS_IN_PROGRESS, 1);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            TechnicalRequest t = ticket(); // resolvedAt để null
            assertTrue(dao.update(t, CHANGED_BY, null));

            verify(updatePs).setNull(eq(12), anyInt());
            verify(updatePs, never()).setTimestamp(eq(12), any(Timestamp.class));
        }
    }

    @Test
    public void update_closedTicket_bindsResolvedAtTimestamp() throws Exception {
        Connection conn = transactionalConnection(TechnicalSupportTicketDAO.STATUS_IN_PROGRESS, 1);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Timestamp resolvedAt = Timestamp.valueOf("2026-09-09 10:00:00");
            TechnicalRequest t = ticket();
            t.setStatus(TechnicalSupportTicketDAO.STATUS_CLOSED);
            t.setResolvedAt(resolvedAt);

            assertTrue(dao.update(t, CHANGED_BY, null));
            verify(updatePs).setTimestamp(12, resolvedAt);
        }
    }

    @Test
    public void update_nullContractId_bindsSqlNullInsteadOfZero() throws Exception {
        Connection conn = transactionalConnection(TechnicalSupportTicketDAO.STATUS_IN_PROGRESS, 1);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Phiếu không gắn hợp đồng nào -- 0 sẽ vi phạm khoá ngoại.
            dao.update(ticket(), CHANGED_BY, null);

            verify(updatePs).setNull(eq(2), anyInt());
        }
    }

    @Test
    public void update_ticketAlreadySoftDeleted_returnsFalse() throws Exception {
        // SELECT ... WHERE is_deleted = 0 không thấy dòng nào.
        Connection conn = transactionalConnection(null, 1);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.update(ticket(), CHANGED_BY, null));
            verify(updatePs, never()).executeUpdate();
            verify(conn, never()).commit();
            verify(conn).rollback();
        }
    }

    @Test
    public void update_sqlError_returnsFalse() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertFalse(dao.update(ticket(), CHANGED_BY, null));
        }
    }

    /**
     * Trạng thái đổi -> phải ghi đúng 1 dòng lịch sử, kèm trạng thái CŨ đọc
     * từ CSDL (không phải trạng thái nào bên gọi tự truyền vào).
     */
    @Test
    public void update_statusChanged_writesHistoryRowInSameTransaction() throws Exception {
        Connection conn = transactionalConnection(TechnicalSupportTicketDAO.STATUS_NEW, 1);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            TechnicalRequest t = ticket(); // status = Đang xử lý
            assertTrue(dao.update(t, CHANGED_BY, "Đã liên hệ khách"));

            verify(historyPs).setInt(1, t.getTicketId());
            verify(historyPs).setString(2, TechnicalSupportTicketDAO.STATUS_NEW);
            verify(historyPs).setString(3, TechnicalSupportTicketDAO.STATUS_IN_PROGRESS);
            verify(historyPs).setInt(4, CHANGED_BY);
            verify(historyPs).setString(5, "Đã liên hệ khách");
            verify(historyPs).executeUpdate();
            verify(conn).commit();
        }
    }

    /**
     * Sửa mô tả/người phụ trách mà không đổi trạng thái thì KHÔNG sinh dòng
     * lịch sử nào -- nếu không, dòng thời gian đầy những bước "A -> A" vô nghĩa
     * và người đọc không còn thấy được diễn biến thật.
     */
    @Test
    public void update_statusUnchanged_writesNoHistoryRow() throws Exception {
        Connection conn = transactionalConnection(TechnicalSupportTicketDAO.STATUS_IN_PROGRESS, 1);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.update(ticket(), CHANGED_BY, "ghi chú không đi kèm bước chuyển nào"));

            verify(conn, never()).prepareStatement(contains("technicalrequesthistory"));
            verify(conn).commit();
        }
    }

    /**
     * Câu UPDATE không tác động dòng nào (phiếu vừa bị xoá mềm giữa chừng) thì
     * rollback, và tuyệt đối không được để lại dòng lịch sử mồ côi.
     */
    @Test
    public void update_noRowAffected_rollsBackAndWritesNoHistory() throws Exception {
        Connection conn = transactionalConnection(TechnicalSupportTicketDAO.STATUS_NEW, 0);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.update(ticket(), CHANGED_BY, null));

            verify(conn, never()).prepareStatement(contains("technicalrequesthistory"));
            verify(conn, never()).commit();
            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    /** Ghi lịch sử hỏng -> cả lần cập nhật cũng phải bị huỷ, không commit nửa vời. */
    @Test
    public void update_historyInsertFails_rollsBackTheWholeUpdate() throws Exception {
        Connection conn = transactionalConnection(TechnicalSupportTicketDAO.STATUS_NEW, 1);
        when(historyPs.executeUpdate()).thenThrow(new SQLException("khoá ngoại changed_by"));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.update(ticket(), CHANGED_BY, null));

            verify(conn, never()).commit();
            verify(conn).rollback();
        }
    }

    // ------------------------------------------------------------------
    // Sinh mã + xoá mềm
    // ------------------------------------------------------------------

    @Test
    public void generateNextTicketCode_emptyTable_startsAtOne() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertNotNull(dao.generateNextTicketCode());
        }
    }

    @Test
    public void softDelete_marksRowInsteadOfRemovingIt() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.softDelete(9));
            verify(ps).setInt(1, 9);
        }
    }

    @Test
    public void softDelete_sqlError_returnsFalse() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertFalse(dao.softDelete(9));
        }
    }

    @Test
    public void findAll_thirdPage_offsetSkipsPrecedingPages() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(3, 10, null, null, null);

            verify(ps).setObject(1, 10);
            verify(ps).setObject(2, 20);
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
}
