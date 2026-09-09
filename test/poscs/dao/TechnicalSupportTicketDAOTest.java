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
    // update -- cột nullable
    // ------------------------------------------------------------------

    @Test
    public void update_openTicket_bindsSqlNullForResolvedAt() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            TechnicalRequest t = ticket(); // resolvedAt để null
            assertTrue(dao.update(t));

            verify(ps).setNull(eq(12), anyInt());
            verify(ps, never()).setTimestamp(eq(12), any(Timestamp.class));
        }
    }

    @Test
    public void update_closedTicket_bindsResolvedAtTimestamp() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Timestamp resolvedAt = Timestamp.valueOf("2026-09-09 10:00:00");
            TechnicalRequest t = ticket();
            t.setStatus(TechnicalSupportTicketDAO.STATUS_CLOSED);
            t.setResolvedAt(resolvedAt);

            assertTrue(dao.update(t));
            verify(ps).setTimestamp(12, resolvedAt);
        }
    }

    @Test
    public void update_nullContractId_bindsSqlNullInsteadOfZero() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Phiếu không gắn hợp đồng nào -- 0 sẽ vi phạm khoá ngoại.
            dao.update(ticket());

            verify(ps).setNull(eq(2), anyInt());
        }
    }

    @Test
    public void update_ticketAlreadySoftDeleted_returnsFalse() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(0); // WHERE is_deleted = 0 không khớp
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.update(ticket()));
        }
    }

    @Test
    public void update_sqlError_returnsFalse() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertFalse(dao.update(ticket()));
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
