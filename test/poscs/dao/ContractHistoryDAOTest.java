package poscs.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import poscs.model.ContractHistory;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Unit Test cho {@link ContractHistoryDAO} (Task 4.3 - UC-45).
 *
 * Kiểm thử toàn diện theo phản hồi QA:
 * - Tiện ích cắt chuỗi detail (truncate) an toàn trước max <= 0, max == 1, null.
 * - Ánh xạ sự kiện tiến độ (eventTypeOf) từ chối nghiêm ngặt các trạng thái sai hoặc null.
 * - Ghi nhận nhật ký trong transaction (insertHistory, insertStatusChange) bắt buộc Connection,
 *   kiểm tra tham số chặt chẽ, ném SQLException khi vi phạm.
 * - Đọc nhật ký theo ID hợp đồng (findHistoryByContractId / findByContractId):
 *   kiểm tra câu SQL ràng buộc WHERE contract_id = ?, JOIN users u, ORDER BY changed_at DESC, history_id DESC (UC-45),
 *   ánh xạ đầy đủ các cột và đối tượng User (họ tên người thực hiện).
 * - Xử lý an toàn các trường hợp biên: tham số null, rỗng, id âm, lỗi SQL.
 * - Kiểm tra ContractDAO ủy quyền cả đọc và ghi sang ContractHistoryDAO.
 */
public class ContractHistoryDAOTest {

    private final ContractHistoryDAO dao = new ContractHistoryDAO();

    private static final int CONTRACT_ID = 42;
    private static final int ACTOR_ID = 9;

    // ------------------------------------------------------------------
    // 1. Tiện ích: truncate & eventTypeOf
    // ------------------------------------------------------------------

    @Test
    public void truncate_variousScenarios() {
        assertEquals("", ContractHistoryDAO.truncate(null, 500));
        assertEquals("", ContractHistoryDAO.truncate("", 500));
        assertEquals("", ContractHistoryDAO.truncate("abc", 0));
        assertEquals("", ContractHistoryDAO.truncate("abc", -5));
        assertEquals("…", ContractHistoryDAO.truncate("abc", 1));
        assertEquals("ab…", ContractHistoryDAO.truncate("abcd", 3));
        assertEquals("12345", ContractHistoryDAO.truncate("12345", 5));
        assertEquals("1234…", ContractHistoryDAO.truncate("123456", 5));

        String longStr = "A".repeat(600);
        String truncated = ContractHistoryDAO.truncate(longStr, 500);
        assertEquals(500, truncated.length());
        assertTrue(truncated.endsWith("…"));
    }

    @Test
    public void eventTypeOf_validStatuses_returnsCorrectEventType() {
        assertEquals(ContractHistory.EVENT_SIGNED, ContractHistoryDAO.eventTypeOf(ContractDAO.PROGRESS_SIGNED));
        assertEquals(ContractHistory.EVENT_LIQUIDATED, ContractHistoryDAO.eventTypeOf(ContractDAO.PROGRESS_LIQUIDATED));
        assertEquals(ContractHistory.EVENT_TERMINATED, ContractHistoryDAO.eventTypeOf(ContractDAO.PROGRESS_TERMINATED));
    }

    @Test(expected = IllegalArgumentException.class)
    public void eventTypeOf_nullStatus_throwsIllegalArgumentException() {
        ContractHistoryDAO.eventTypeOf(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void eventTypeOf_emptyStatus_throwsIllegalArgumentException() {
        ContractHistoryDAO.eventTypeOf("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void eventTypeOf_unknownStatus_throwsIllegalArgumentException() {
        ContractHistoryDAO.eventTypeOf("TrangThaiKhac");
    }

    // ------------------------------------------------------------------
    // 2. insertHistory (Connection conn, ...)
    // ------------------------------------------------------------------

    @Test
    public void insertHistory_withConnection_success() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection conn = connectionReturning(ps);

        dao.insertHistory(conn, CONTRACT_ID, ContractHistory.EVENT_UPDATED, "Sua thong tin tieu de", ACTOR_ID, "Ghi chu bo sung");

        verify(ps).setInt(1, CONTRACT_ID);
        verify(ps).setString(2, ContractHistory.EVENT_UPDATED);
        verify(ps).setString(3, "Sua thong tin tieu de");
        verify(ps).setInt(4, ACTOR_ID);
        verify(ps).setString(5, "Ghi chu bo sung");
        verify(ps).executeUpdate();
    }

    @Test
    public void insertHistory_withConnection_longDetailTruncated() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection conn = connectionReturning(ps);

        String longDetail = "B".repeat(600);
        dao.insertHistory(conn, CONTRACT_ID, ContractHistory.EVENT_UPDATED, longDetail, ACTOR_ID, null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ps).setString(eq(3), captor.capture());
        assertEquals(500, captor.getValue().length());
        assertTrue(captor.getValue().endsWith("…"));
        verify(ps).setString(5, null);
    }

    @Test
    public void insertHistory_invalidParameters_throwsSQLException() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection conn = connectionReturning(ps);

        // conn null
        assertThrows(SQLException.class, () ->
                dao.insertHistory(null, CONTRACT_ID, ContractHistory.EVENT_CREATED, "Khoi tao", ACTOR_ID, null));

        // contractId <= 0
        assertThrows(SQLException.class, () ->
                dao.insertHistory(conn, 0, ContractHistory.EVENT_CREATED, "Khoi tao", ACTOR_ID, null));
        assertThrows(SQLException.class, () ->
                dao.insertHistory(conn, -1, ContractHistory.EVENT_CREATED, "Khoi tao", ACTOR_ID, null));

        // changedBy <= 0
        assertThrows(SQLException.class, () ->
                dao.insertHistory(conn, CONTRACT_ID, ContractHistory.EVENT_CREATED, "Khoi tao", 0, null));
        assertThrows(SQLException.class, () ->
                dao.insertHistory(conn, CONTRACT_ID, ContractHistory.EVENT_CREATED, "Khoi tao", -5, null));

        // eventType null or blank
        assertThrows(SQLException.class, () ->
                dao.insertHistory(conn, CONTRACT_ID, null, "Khoi tao", ACTOR_ID, null));
        assertThrows(SQLException.class, () ->
                dao.insertHistory(conn, CONTRACT_ID, "   ", "Khoi tao", ACTOR_ID, null));
    }

    // ------------------------------------------------------------------
    // 3. insertStatusChange (Connection conn, ...)
    // ------------------------------------------------------------------

    @Test
    public void insertStatusChange_withConnection_signed() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection conn = connectionReturning(ps);

        dao.insertStatusChange(conn, CONTRACT_ID, ContractDAO.PROGRESS_DRAFT, ContractDAO.PROGRESS_SIGNED, ACTOR_ID, "Ky ket hop dong");

        verify(ps).setInt(1, CONTRACT_ID);
        verify(ps).setString(2, ContractHistory.EVENT_SIGNED);
        verify(ps).setString(3, "Nháp → Đã ký");
        verify(ps).setString(4, ContractDAO.PROGRESS_DRAFT);
        verify(ps).setString(5, ContractDAO.PROGRESS_SIGNED);
        verify(ps).setInt(6, ACTOR_ID);
        verify(ps).setString(7, "Ky ket hop dong");
        verify(ps).executeUpdate();
    }

    @Test
    public void insertStatusChange_withConnection_liquidated() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection conn = connectionReturning(ps);

        dao.insertStatusChange(conn, CONTRACT_ID, ContractDAO.PROGRESS_SIGNED, ContractDAO.PROGRESS_LIQUIDATED, ACTOR_ID, "Nghiem thu xong");

        verify(ps).setString(2, ContractHistory.EVENT_LIQUIDATED);
        verify(ps).setString(3, "Đã ký → Đã thanh lý");
        verify(ps).executeUpdate();
    }

    @Test
    public void insertStatusChange_withConnection_terminated() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection conn = connectionReturning(ps);

        dao.insertStatusChange(conn, CONTRACT_ID, ContractDAO.PROGRESS_SIGNED, ContractDAO.PROGRESS_TERMINATED, ACTOR_ID, "Doi ben thoa thuan cham dut");

        verify(ps).setString(2, ContractHistory.EVENT_TERMINATED);
        verify(ps).setString(3, "Đã ký → Chấm dứt sớm");
        verify(ps).executeUpdate();
    }

    @Test
    public void insertStatusChange_invalidParameters_throwsSQLException() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection conn = connectionReturning(ps);

        // conn null
        assertThrows(SQLException.class, () ->
                dao.insertStatusChange(null, CONTRACT_ID, ContractDAO.PROGRESS_DRAFT, ContractDAO.PROGRESS_SIGNED, ACTOR_ID, null));

        // contractId <= 0
        assertThrows(SQLException.class, () ->
                dao.insertStatusChange(conn, 0, ContractDAO.PROGRESS_DRAFT, ContractDAO.PROGRESS_SIGNED, ACTOR_ID, null));

        // changedBy <= 0
        assertThrows(SQLException.class, () ->
                dao.insertStatusChange(conn, CONTRACT_ID, ContractDAO.PROGRESS_DRAFT, ContractDAO.PROGRESS_SIGNED, 0, null));

        // fromStatus null or empty
        assertThrows(SQLException.class, () ->
                dao.insertStatusChange(conn, CONTRACT_ID, null, ContractDAO.PROGRESS_SIGNED, ACTOR_ID, null));
        assertThrows(SQLException.class, () ->
                dao.insertStatusChange(conn, CONTRACT_ID, "  ", ContractDAO.PROGRESS_SIGNED, ACTOR_ID, null));

        // toStatus null or empty
        assertThrows(SQLException.class, () ->
                dao.insertStatusChange(conn, CONTRACT_ID, ContractDAO.PROGRESS_DRAFT, null, ACTOR_ID, null));
        assertThrows(SQLException.class, () ->
                dao.insertStatusChange(conn, CONTRACT_ID, ContractDAO.PROGRESS_DRAFT, "   ", ACTOR_ID, null));
    }

    // ------------------------------------------------------------------
    // 4. findHistoryByContractId & findByContractId (UC-45)
    // ------------------------------------------------------------------

    @Test
    public void findHistoryByContractId_invalidContractId_returnsEmptyList() {
        assertTrue(dao.findHistoryByContractId(0).isEmpty());
        assertTrue(dao.findHistoryByContractId(-10).isEmpty());
    }

    @Test
    public void findHistoryByContractId_withConnection_nullConnection_returnsEmptyList() throws Exception {
        assertTrue(dao.findHistoryByContractId(null, CONTRACT_ID).isEmpty());
    }

    @Test
    public void findHistoryByContractId_success_mapsAllFieldsAndUser() throws Exception {
        Timestamp time1 = Timestamp.valueOf("2026-10-15 14:30:00");
        Timestamp time2 = Timestamp.valueOf("2026-10-14 09:00:00");

        var row1 = row(
                "history_id", 101,
                "contract_id", CONTRACT_ID,
                "event_type", ContractHistory.EVENT_SIGNED,
                "detail", "Nháp → Đã ký",
                "from_status", ContractDAO.PROGRESS_DRAFT,
                "to_status", ContractDAO.PROGRESS_SIGNED,
                "changed_by", ACTOR_ID,
                "changed_at", time1,
                "note", "Ky theo bien ban",
                "changer_last_name", "Nguyen",
                "changer_middle_name", "Van",
                "changer_first_name", "An"
        );
        var row2 = row(
                "history_id", 100,
                "contract_id", CONTRACT_ID,
                "event_type", ContractHistory.EVENT_CREATED,
                "detail", "Tạo bản nháp HD-0001",
                "from_status", null,
                "to_status", null,
                "changed_by", 5,
                "changed_at", time2,
                "note", null,
                "changer_last_name", "Tran",
                "changer_middle_name", "",
                "changer_first_name", "Binh"
        );

        ResultSet rs = resultSetOf(List.of(row1, row2));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(rs);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractHistory> list = dao.findHistoryByContractId(CONTRACT_ID);
            assertEquals(2, list.size());

            // QA 4.3 Checklist E: verify SQL có WHERE contract_id = ?, JOIN users u, ORDER BY h.changed_at DESC, h.history_id DESC
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(conn).prepareStatement(sqlCaptor.capture());
            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("WHERE h.contract_id = ?"));
            assertTrue(sql.contains("JOIN users u ON u.user_id = h.changed_by"));
            assertTrue(sql.contains("ORDER BY h.changed_at DESC, h.history_id DESC"));
            verify(ps).setInt(1, CONTRACT_ID);

            ContractHistory h1 = list.get(0);
            assertEquals(101, h1.getHistoryId());
            assertEquals(CONTRACT_ID, h1.getContractId());
            assertEquals(ContractHistory.EVENT_SIGNED, h1.getEventType());
            assertEquals("Nháp → Đã ký", h1.getDetail());
            assertEquals(ContractDAO.PROGRESS_DRAFT, h1.getFromStatus());
            assertEquals(ContractDAO.PROGRESS_SIGNED, h1.getToStatus());
            assertEquals(ACTOR_ID, h1.getChangedBy());
            assertEquals(time1, h1.getChangedAt());
            assertEquals("Ky theo bien ban", h1.getNote());
            assertTrue(h1.isStatusChange());

            User u1 = h1.getChangedByUser();
            assertNotNull(u1);
            assertEquals("Nguyen", u1.getLastName());
            assertEquals("Van", u1.getMiddleName());
            assertEquals("An", u1.getFirstName());
            assertEquals("Nguyen Van An", u1.getFullName());

            ContractHistory h2 = list.get(1);
            assertEquals(100, h2.getHistoryId());
            assertEquals(ContractHistory.EVENT_CREATED, h2.getEventType());
            assertNull(h2.getFromStatus());
            assertNull(h2.getToStatus());
            assertFalse(h2.isStatusChange());
            assertNull(h2.getNote());

            User u2 = h2.getChangedByUser();
            assertNotNull(u2);
            assertEquals("Tran Binh", u2.getFullName());
        }
    }

    @Test
    public void findByContractId_alias_delegatesToFindHistoryByContractId() throws Exception {
        var row = row(
                "history_id", 1,
                "contract_id", CONTRACT_ID,
                "event_type", ContractHistory.EVENT_CREATED,
                "detail", "Khoi tao",
                "from_status", null,
                "to_status", null,
                "changed_by", ACTOR_ID,
                "changed_at", Timestamp.valueOf("2026-10-15 10:00:00"),
                "note", null,
                "changer_last_name", "Nguyen",
                "changer_middle_name", null,
                "changer_first_name", "A"
        );
        ResultSet rs = singleRow(row);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(rs);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractHistory> aliasList = dao.findByContractId(CONTRACT_ID);
            assertEquals(1, aliasList.size());
            assertEquals(1, aliasList.get(0).getHistoryId());
        }
    }

    @Test
    public void findHistoryByContractId_sqlException_returnsEmptyList() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenThrow(new SQLException("Table locked"));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractHistory> result = dao.findHistoryByContractId(CONTRACT_ID);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // 5. ContractDAO delegation (cả đọc và ghi)
    // ------------------------------------------------------------------

    @Test
    public void contractDAO_delegatesReadAndWriteHistoryOperations() throws Exception {
        ContractDAO defaultDao = new ContractDAO();
        assertNotNull(defaultDao.getHistoryDAO());

        ContractHistoryDAO customMock = mock(ContractHistoryDAO.class);
        ContractDAO injectedDao = new ContractDAO(customMock);
        assertSame(customMock, injectedDao.getHistoryDAO());

        // 1. Kiểm tra ủy quyền đọc
        when(customMock.findHistoryByContractId(123)).thenReturn(List.of(new ContractHistory()));
        List<ContractHistory> history = injectedDao.findHistoryByContractId(123);
        assertEquals(1, history.size());
        verify(customMock).findHistoryByContractId(123);

        // 2. Kiểm tra ủy quyền ghi (insertHistory khi voidRecord)
        ResultSet noChildAmendments = singleRow(row("count", 0));
        var parentData = parentRow(ContractDAO.PROGRESS_SIGNED, null);
        ResultSet voidedRs = singleRow(parentData);
        PreparedStatement contractPs = mock(PreparedStatement.class);
        when(contractPs.executeQuery()).thenReturn(noChildAmendments, voidedRs);
        when(contractPs.executeUpdate()).thenReturn(1);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenReturn(contractPs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean ok = injectedDao.voidRecord(3, ACTOR_ID, "Ly do huy");
            assertTrue(ok);
            // Verify customMock.insertHistory được ContractDAO ủy quyền gọi
            verify(customMock).insertHistory(eq(conn), eq(3), eq(ContractHistory.EVENT_VOIDED), anyString(), eq(ACTOR_ID), eq("Ly do huy"));
        }
    }

    private static java.util.Map<String, Object> parentRow(String progressStatus, Integer parentId) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("contract_id", 3);
        map.put("contract_code", "01/2026/HĐMB-POSTEF");
        map.put("parent_contract_id", parentId);
        map.put("title", "Hợp đồng thử nghiệm");
        map.put("contract_value", new BigDecimal("1500000000"));
        map.put("enterprise_id", 1);
        map.put("owner_id", 2);
        map.put("status", "Đang hiệu lực");
        map.put("progress_status", progressStatus);
        map.put("signing_date", java.sql.Date.valueOf("2026-01-01"));
        map.put("effective_date", java.sql.Date.valueOf("2026-01-01"));
        map.put("end_date", java.sql.Date.valueOf("2026-12-31"));
        return map;
    }
}
