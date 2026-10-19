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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import poscs.model.Contract;
import poscs.model.ContractHandover;
import poscs.model.ContractHistory;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Unit Test cho {@link ContractHandoverDAO} (Task 4.7 - UC-48, UC-49).
 *
 * Kiểm thử toàn diện:
 * - Khởi tạo và Dependency Injection với {@link ContractHistoryDAO}.
 * - Các hằng số nghiệp vụ (HANDOVER_ALREADY_PENDING, WAITING_ANY_DEPARTMENT, HANDOVER_FROZEN, HANDOVER_FAILED, max lengths).
 * - Khởi tạo bàn giao phòng ban (handOverToDepartments - UC-48):
 *   + Tham số không hợp lệ (null/rỗng, contractId <= 0, actorId <= 0).
 *   + Hợp đồng không tồn tại (lock trả về null) -> HANDOVER_FAILED.
 *   + Hợp đồng đã đóng băng (LIQUIDATED, TERMINATED) -> HANDOVER_FROZEN.
 *   + Một trong các phòng đang giữ hợp đồng chưa xử lý -> HANDOVER_ALREADY_PENDING.
 *   + Bàn giao thành công một hoặc nhiều phòng cùng lúc trong transaction (NFR-19).
 *   + Ghi nhận nhật ký contract_history EVENT_HANDOVER cho từng phòng được giao.
 *   + Cắt gọt chuỗi ghi chú an toàn (tối đa 255 ký tự, chuẩn hóa null/blank).
 *   + Xử lý ngoại lệ CSDL an toàn (rollback, trả về HANDOVER_FAILED).
 * - Xác nhận xử lý xong chặng bàn giao (completeHandover - UC-49):
 *   + Tham số không hợp lệ (doneNote null/blank, handoverId <= 0, actorId <= 0).
 *   + Chặng không tồn tại hoặc đã được xác nhận trước đó (done_at != null).
 *   + Thất bại khi cập nhật (executeUpdate = 0).
 *   + Xác nhận thành công: cập nhật done_at, done_by, done_note, ghi nhật ký EVENT_HANDOVER.
 *   + Cắt gọt done_note an toàn (tối đa 500 ký tự).
 *   + Xử lý ngoại lệ CSDL an toàn.
 * - Truy vấn danh sách chặng bàn giao (findHandoversOf):
 *   + contractId <= 0 trả về danh sách rỗng.
 *   + Truy vấn danh sách sắp xếp theo handed_at DESC, handover_id DESC.
 *   + Xử lý ngoại lệ CSDL an toàn.
 * - Truy vấn chặng đang chờ toàn hệ thống (findPendingHandovers):
 *   + Lọc theo danh sách người phụ trách (ownerIds null/rỗng hoặc có ID).
 *   + Sắp xếp handed_at ASC (chờ lâu nhất đứng trước).
 *   + Xử lý ngoại lệ CSDL an toàn.
 * - Ánh xạ ResultSet sang ContractHandover (mapHandoverRow):
 *   + Đầy đủ các trường, xử lý doneBy null/không null, định dạng tên đầy đủ.
 * - Các hàm phụ trợ (hasPendingHandover, departmentName).
 * - Kiểm tra ủy quyền từ ContractDAO sang ContractHandoverDAO.
 */
public class ContractHandoverDAOTest {

    private final ContractHandoverDAO dao = new ContractHandoverDAO();

    private static final int CONTRACT_ID = 100;
    private static final int HANDOVER_ID = 50;
    private static final int DEPT_ACCOUNTING = 1;
    private static final int DEPT_PROJECT = 2;
    private static final int ACTOR_ID = 99;

    // ------------------------------------------------------------------
    // 1. Khởi tạo & Hằng số
    // ------------------------------------------------------------------

    @Test
    public void testConstructorsAndGetters() {
        ContractHandoverDAO defaultDao = new ContractHandoverDAO();
        assertNotNull(defaultDao.getHistoryDAO());

        ContractHistoryDAO customHistory = new ContractHistoryDAO();
        ContractHandoverDAO customDao = new ContractHandoverDAO(customHistory);
        assertSame(customHistory, customDao.getHistoryDAO());

        ContractHandoverDAO nullDao = new ContractHandoverDAO(null);
        assertNotNull(nullDao.getHistoryDAO());
    }

    @Test
    public void testConstants() {
        assertEquals(-1, ContractHandoverDAO.HANDOVER_ALREADY_PENDING);
        assertEquals(-1, ContractHandoverDAO.WAITING_ANY_DEPARTMENT);
        assertEquals(-3, ContractHandoverDAO.HANDOVER_FROZEN);
        assertEquals(-2, ContractHandoverDAO.HANDOVER_FAILED);
        assertEquals(255, ContractHandoverDAO.HANDOVER_NOTE_MAX_LENGTH);
        assertEquals(500, ContractHandoverDAO.DONE_NOTE_MAX_LENGTH);

        assertTrue(ContractHandoverDAO.HANDOVER_SELECT.contains("contract_handovers"));
        assertTrue(ContractHandoverDAO.HANDOVER_SELECT.contains("departments"));
        assertTrue(ContractHandoverDAO.HANDOVER_SELECT.contains("contracts"));
    }

    // ------------------------------------------------------------------
    // 2. Khởi tạo bàn giao phòng ban (handOverToDepartments - UC-48)
    // ------------------------------------------------------------------

    @Test
    public void testHandOverToDepartments_InvalidArguments() {
        // departmentIds null hoặc rỗng -> trả 0
        assertEquals(0, dao.handOverToDepartments(CONTRACT_ID, null, "ghi chu", ACTOR_ID));
        assertEquals(0, dao.handOverToDepartments(CONTRACT_ID, List.of(), "ghi chu", ACTOR_ID));

        // contractId <= 0 -> HANDOVER_FAILED
        assertEquals(ContractHandoverDAO.HANDOVER_FAILED,
                dao.handOverToDepartments(0, List.of(DEPT_ACCOUNTING), "ghi chu", ACTOR_ID));
        assertEquals(ContractHandoverDAO.HANDOVER_FAILED,
                dao.handOverToDepartments(-1, List.of(DEPT_ACCOUNTING), "ghi chu", ACTOR_ID));

        // actorId <= 0 -> HANDOVER_FAILED
        assertEquals(ContractHandoverDAO.HANDOVER_FAILED,
                dao.handOverToDepartments(CONTRACT_ID, List.of(DEPT_ACCOUNTING), "ghi chu", 0));
        assertEquals(ContractHandoverDAO.HANDOVER_FAILED,
                dao.handOverToDepartments(CONTRACT_ID, List.of(DEPT_ACCOUNTING), "ghi chu", -5));

        // departmentIds chứa phần tử null
        assertEquals(ContractHandoverDAO.HANDOVER_FAILED,
                dao.handOverToDepartments(CONTRACT_ID, java.util.Arrays.asList(DEPT_ACCOUNTING, null), "ghi chu", ACTOR_ID));

        // departmentIds chứa phần tử <= 0
        assertEquals(ContractHandoverDAO.HANDOVER_FAILED,
                dao.handOverToDepartments(CONTRACT_ID, List.of(0), "ghi chu", ACTOR_ID));
        assertEquals(ContractHandoverDAO.HANDOVER_FAILED,
                dao.handOverToDepartments(CONTRACT_ID, List.of(-1), "ghi chu", ACTOR_ID));

        // departmentIds chứa ID trùng lặp trong cùng lô
        assertEquals(ContractHandoverDAO.HANDOVER_FAILED,
                dao.handOverToDepartments(CONTRACT_ID, List.of(DEPT_ACCOUNTING, DEPT_ACCOUNTING), "ghi chu", ACTOR_ID));
    }

    @Test
    public void testHandOverToDepartments_ContractNotFound() throws Exception {
        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsEmpty = resultSetOf(List.of());
        when(psLock.executeQuery()).thenReturn(rsEmpty);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = dao.handOverToDepartments(CONTRACT_ID, List.of(DEPT_ACCOUNTING), "ghi chu", ACTOR_ID);
            assertEquals(ContractHandoverDAO.HANDOVER_FAILED, result);
            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void testHandOverToDepartments_ContractFrozen() throws Exception {
        // Kịch bản 1: Hợp đồng đã thanh lý
        PreparedStatement psLock1 = mock(PreparedStatement.class);
        ResultSet rsLiquidated = singleRow(row(
                "contract_id", CONTRACT_ID,
                "contract_code", "HD-001",
                "progress_status", ContractDAO.PROGRESS_LIQUIDATED
        ));
        when(psLock1.executeQuery()).thenReturn(rsLiquidated);

        Connection conn1 = mock(Connection.class);
        when(conn1.prepareStatement(contains("WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock1);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn1);

            int result = dao.handOverToDepartments(CONTRACT_ID, List.of(DEPT_ACCOUNTING), "ghi chu", ACTOR_ID);
            assertEquals(ContractHandoverDAO.HANDOVER_FROZEN, result);
            verify(conn1).rollback();
        }

        // Kịch bản 2: Hợp đồng chấm dứt sớm
        PreparedStatement psLock2 = mock(PreparedStatement.class);
        ResultSet rsTerminated = singleRow(row(
                "contract_id", CONTRACT_ID,
                "contract_code", "HD-001",
                "progress_status", ContractDAO.PROGRESS_TERMINATED
        ));
        when(psLock2.executeQuery()).thenReturn(rsTerminated);

        Connection conn2 = mock(Connection.class);
        when(conn2.prepareStatement(contains("WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock2);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn2);

            int result = dao.handOverToDepartments(CONTRACT_ID, List.of(DEPT_ACCOUNTING), "ghi chu", ACTOR_ID);
            assertEquals(ContractHandoverDAO.HANDOVER_FROZEN, result);
            verify(conn2).rollback();
        }
    }

    @Test
    public void testHandOverToDepartments_AlreadyPending() throws Exception {
        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsActive = singleRow(row(
                "contract_id", CONTRACT_ID,
                "contract_code", "HD-001",
                "progress_status", "Đang thực hiện"
        ));
        when(psLock.executeQuery()).thenReturn(rsActive);

        PreparedStatement psCheckPending = mock(PreparedStatement.class);
        ResultSet rsPendingFound = singleRow(row("1", 1));
        when(psCheckPending.executeQuery()).thenReturn(rsPendingFound);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("SELECT 1 FROM contract_handovers")))
                .thenReturn(psCheckPending);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = dao.handOverToDepartments(CONTRACT_ID, List.of(DEPT_ACCOUNTING), "ghi chu", ACTOR_ID);
            assertEquals(ContractHandoverDAO.HANDOVER_ALREADY_PENDING, result);
            verify(conn).rollback();
        }
    }

    @Test
    public void testHandOverToDepartments_SecondDepartmentAlreadyPending_RollsBackAllAndDoesNotInsert() throws Exception {
        ContractHistoryDAO mockHistoryDAO = mock(ContractHistoryDAO.class);
        ContractHandoverDAO customDao = new ContractHandoverDAO(mockHistoryDAO);

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsActive = singleRow(row(
                "contract_id", CONTRACT_ID,
                "contract_code", "HD-001",
                "progress_status", "Đang thực hiện"
        ));
        when(psLock.executeQuery()).thenReturn(rsActive);

        PreparedStatement psCheckPending = mock(PreparedStatement.class);
        ResultSet rsEmpty = emptyResultSet();
        ResultSet rsPendingFound = singleRow(row("1", 1));
        when(psCheckPending.executeQuery()).thenReturn(rsEmpty, rsPendingFound);

        PreparedStatement psInsert = mock(PreparedStatement.class);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("SELECT 1 FROM contract_handovers")))
                .thenReturn(psCheckPending);
        when(conn.prepareStatement(contains("INSERT INTO contract_handovers")))
                .thenReturn(psInsert);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = customDao.handOverToDepartments(CONTRACT_ID,
                    List.of(DEPT_ACCOUNTING, DEPT_PROJECT), "ghi chu", ACTOR_ID);
            assertEquals(ContractHandoverDAO.HANDOVER_ALREADY_PENDING, result);

            verify(psInsert, never()).executeUpdate();
            verify(mockHistoryDAO, never()).insertHistory(any(), anyInt(), anyString(), anyString(), anyInt(), any());
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void testHandOverToDepartments_InsertFails_RollsBack() throws Exception {
        ContractHistoryDAO mockHistoryDAO = mock(ContractHistoryDAO.class);
        ContractHandoverDAO customDao = new ContractHandoverDAO(mockHistoryDAO);

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsActive = singleRow(row(
                "contract_id", CONTRACT_ID,
                "contract_code", "HD-001",
                "progress_status", "Đang thực hiện"
        ));
        when(psLock.executeQuery()).thenReturn(rsActive);

        PreparedStatement psCheckPending = mock(PreparedStatement.class);
        ResultSet rsEmpty = emptyResultSet();
        when(psCheckPending.executeQuery()).thenReturn(rsEmpty);

        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(0);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("SELECT 1 FROM contract_handovers")))
                .thenReturn(psCheckPending);
        when(conn.prepareStatement(contains("INSERT INTO contract_handovers")))
                .thenReturn(psInsert);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = customDao.handOverToDepartments(CONTRACT_ID, List.of(DEPT_ACCOUNTING), "ghi chu", ACTOR_ID);
            assertEquals(ContractHandoverDAO.HANDOVER_FAILED, result);

            verify(mockHistoryDAO, never()).insertHistory(any(), anyInt(), anyString(), anyString(), anyInt(), any());
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void testHandOverToDepartments_Success_MultipleDepartments_AtomicTransaction() throws Exception {
        ContractHistoryDAO mockHistoryDAO = mock(ContractHistoryDAO.class);
        ContractHandoverDAO customDao = new ContractHandoverDAO(mockHistoryDAO);

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsActive = singleRow(row(
                "contract_id", CONTRACT_ID,
                "contract_code", "HD-001",
                "progress_status", "Đang thực hiện"
        ));
        when(psLock.executeQuery()).thenReturn(rsActive);

        PreparedStatement psCheckPending = mock(PreparedStatement.class);
        ResultSet rsPendingEmpty1 = emptyResultSet();
        ResultSet rsPendingEmpty2 = emptyResultSet();
        when(psCheckPending.executeQuery()).thenReturn(rsPendingEmpty1, rsPendingEmpty2);

        PreparedStatement psDept = mock(PreparedStatement.class);
        ResultSet rsDept1 = singleRow(row("department_name", "Kế toán"));
        ResultSet rsDept2 = singleRow(row("department_name", "Dự án"));
        when(psDept.executeQuery()).thenReturn(rsDept1, rsDept2);

        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(1);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("SELECT 1 FROM contract_handovers")))
                .thenReturn(psCheckPending);
        when(conn.prepareStatement(contains("SELECT department_name FROM departments")))
                .thenReturn(psDept);
        when(conn.prepareStatement(contains("INSERT INTO contract_handovers")))
                .thenReturn(psInsert);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = customDao.handOverToDepartments(CONTRACT_ID,
                    List.of(DEPT_ACCOUNTING, DEPT_PROJECT), "  Soạn xong nhờ kiểm  ", ACTOR_ID);
            assertEquals(2, result);

            // Kiểm tra 2 lần INSERT chặng bàn giao
            verify(psInsert, times(2)).executeUpdate();

            // Kiểm tra các tham số bind vào psInsert
            verify(psInsert, times(2)).setInt(1, CONTRACT_ID);
            verify(psInsert).setInt(2, DEPT_ACCOUNTING);
            verify(psInsert).setInt(2, DEPT_PROJECT);
            verify(psInsert, times(2)).setInt(3, ACTOR_ID);
            verify(psInsert, times(2)).setString(4, "Soạn xong nhờ kiểm");

            // Kiểm tra 2 lần ghi nhật ký lịch sử
            verify(mockHistoryDAO).insertHistory(eq(conn), eq(CONTRACT_ID),
                    eq(ContractHistory.EVENT_HANDOVER), eq("Bàn giao cho phòng Kế toán"),
                    eq(ACTOR_ID), eq("Soạn xong nhờ kiểm"));
            verify(mockHistoryDAO).insertHistory(eq(conn), eq(CONTRACT_ID),
                    eq(ContractHistory.EVENT_HANDOVER), eq("Bàn giao cho phòng Dự án"),
                    eq(ACTOR_ID), eq("Soạn xong nhờ kiểm"));

            verify(conn).commit();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void testHandOverToDepartments_NullAndBlankNote_InsertedAsNull() throws Exception {
        ContractHistoryDAO mockHistoryDAO = mock(ContractHistoryDAO.class);
        ContractHandoverDAO customDao = new ContractHandoverDAO(mockHistoryDAO);

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsLock = singleRow(row(
                "contract_id", CONTRACT_ID,
                "contract_code", "HD-001",
                "progress_status", "Nháp"
        ));
        when(psLock.executeQuery()).thenReturn(rsLock);

        PreparedStatement psCheckPending = mock(PreparedStatement.class);
        ResultSet rsCheckPending = emptyResultSet();
        when(psCheckPending.executeQuery()).thenReturn(rsCheckPending);

        PreparedStatement psDept = mock(PreparedStatement.class);
        ResultSet rsDept = singleRow(row("department_name", "Kế toán"));
        when(psDept.executeQuery()).thenReturn(rsDept);

        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(1);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("SELECT 1 FROM contract_handovers")))
                .thenReturn(psCheckPending);
        when(conn.prepareStatement(contains("SELECT department_name FROM departments")))
                .thenReturn(psDept);
        when(conn.prepareStatement(contains("INSERT INTO contract_handovers")))
                .thenReturn(psInsert);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = customDao.handOverToDepartments(CONTRACT_ID, List.of(DEPT_ACCOUNTING), "   ", ACTOR_ID);
            assertEquals(1, result);

            verify(psInsert).setString(4, null);
            verify(mockHistoryDAO).insertHistory(eq(conn), eq(CONTRACT_ID),
                    eq(ContractHistory.EVENT_HANDOVER), eq("Bàn giao cho phòng Kế toán"),
                    eq(ACTOR_ID), isNull());
            verify(conn).commit();
        }
    }

    @Test
    public void testHandOverToDepartments_NoteTruncatedTo255() throws Exception {
        ContractHistoryDAO mockHistoryDAO = mock(ContractHistoryDAO.class);
        ContractHandoverDAO customDao = new ContractHandoverDAO(mockHistoryDAO);

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsLock2 = singleRow(row(
                "contract_id", CONTRACT_ID,
                "contract_code", "HD-001",
                "progress_status", "Nháp"
        ));
        when(psLock.executeQuery()).thenReturn(rsLock2);

        PreparedStatement psCheckPending = mock(PreparedStatement.class);
        ResultSet rsCheckPending2 = emptyResultSet();
        when(psCheckPending.executeQuery()).thenReturn(rsCheckPending2);

        PreparedStatement psDept = mock(PreparedStatement.class);
        ResultSet rsDept2 = singleRow(row("department_name", "Dự án"));
        when(psDept.executeQuery()).thenReturn(rsDept2);

        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(1);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("SELECT 1 FROM contract_handovers")))
                .thenReturn(psCheckPending);
        when(conn.prepareStatement(contains("SELECT department_name FROM departments")))
                .thenReturn(psDept);
        when(conn.prepareStatement(contains("INSERT INTO contract_handovers")))
                .thenReturn(psInsert);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            String longNote = "A".repeat(300);
            int result = customDao.handOverToDepartments(CONTRACT_ID, List.of(DEPT_PROJECT), longNote, ACTOR_ID);
            assertEquals(1, result);

            ArgumentCaptor<String> noteCaptor = ArgumentCaptor.forClass(String.class);
            verify(psInsert).setString(eq(4), noteCaptor.capture());
            assertEquals(255, noteCaptor.getValue().length());
            assertTrue(noteCaptor.getValue().endsWith("…"));

            verify(mockHistoryDAO).insertHistory(eq(conn), eq(CONTRACT_ID),
                    eq(ContractHistory.EVENT_HANDOVER), eq("Bàn giao cho phòng Dự án"),
                    eq(ACTOR_ID), eq(noteCaptor.getValue()));
        }
    }

    @Test
    public void testHandOverToDepartments_SQLExceptionRollback() throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenThrow(new SQLException("DB error"));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = dao.handOverToDepartments(CONTRACT_ID, List.of(DEPT_ACCOUNTING), "ghi chu", ACTOR_ID);
            assertEquals(ContractHandoverDAO.HANDOVER_FAILED, result);
            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    // ------------------------------------------------------------------
    // 3. Xác nhận xử lý xong chặng bàn giao (completeHandover - UC-49)
    // ------------------------------------------------------------------

    @Test
    public void testCompleteHandover_InvalidArguments() {
        assertFalse(dao.completeHandover(HANDOVER_ID, ACTOR_ID, null));
        assertFalse(dao.completeHandover(HANDOVER_ID, ACTOR_ID, ""));
        assertFalse(dao.completeHandover(HANDOVER_ID, ACTOR_ID, "    "));
        assertFalse(dao.completeHandover(0, ACTOR_ID, "xong"));
        assertFalse(dao.completeHandover(-1, ACTOR_ID, "xong"));
        assertFalse(dao.completeHandover(HANDOVER_ID, 0, "xong"));
        assertFalse(dao.completeHandover(HANDOVER_ID, -10, "xong"));
    }

    @Test
    public void testCompleteHandover_NotFoundOrAlreadyDone() throws Exception {
        ContractHistoryDAO mockHistoryDAO = mock(ContractHistoryDAO.class);
        ContractHandoverDAO customDao = new ContractHandoverDAO(mockHistoryDAO);

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsEmpty = emptyResultSet();
        when(psLock.executeQuery()).thenReturn(rsEmpty);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE handover_id = ? AND done_at IS NULL FOR UPDATE")))
                .thenReturn(psLock);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean ok = customDao.completeHandover(HANDOVER_ID, ACTOR_ID, "xong đợt 1");
            assertFalse(ok);
            verify(mockHistoryDAO, never()).insertHistory(any(), anyInt(), anyString(), anyString(), anyInt(), any());
            verify(conn).rollback();
            verify(conn, never()).commit();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void testCompleteHandover_UpdateFails() throws Exception {
        ContractHistoryDAO mockHistoryDAO = mock(ContractHistoryDAO.class);
        ContractHandoverDAO customDao = new ContractHandoverDAO(mockHistoryDAO);

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsHandover = singleRow(row(
                "contract_id", CONTRACT_ID,
                "department_id", DEPT_ACCOUNTING
        ));
        when(psLock.executeQuery()).thenReturn(rsHandover);

        PreparedStatement psUpdate = mock(PreparedStatement.class);
        when(psUpdate.executeUpdate()).thenReturn(0);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE handover_id = ? AND done_at IS NULL FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("UPDATE contract_handovers")))
                .thenReturn(psUpdate);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean ok = customDao.completeHandover(HANDOVER_ID, ACTOR_ID, "xong đợt 1");
            assertFalse(ok);
            verify(mockHistoryDAO, never()).insertHistory(any(), anyInt(), anyString(), anyString(), anyInt(), any());
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void testCompleteHandover_Success() throws Exception {
        ContractHistoryDAO mockHistoryDAO = mock(ContractHistoryDAO.class);
        ContractHandoverDAO customDao = new ContractHandoverDAO(mockHistoryDAO);

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsHandover = singleRow(row(
                "contract_id", CONTRACT_ID,
                "department_id", DEPT_ACCOUNTING
        ));
        when(psLock.executeQuery()).thenReturn(rsHandover);

        PreparedStatement psUpdate = mock(PreparedStatement.class);
        when(psUpdate.executeUpdate()).thenReturn(1);

        PreparedStatement psDept = mock(PreparedStatement.class);
        ResultSet rsDept = singleRow(row("department_name", "Kế toán"));
        when(psDept.executeQuery()).thenReturn(rsDept);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE handover_id = ? AND done_at IS NULL FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("UPDATE contract_handovers")))
                .thenReturn(psUpdate);
        when(conn.prepareStatement(contains("SELECT department_name FROM departments")))
                .thenReturn(psDept);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean ok = customDao.completeHandover(HANDOVER_ID, ACTOR_ID, "  Đã lập lịch thu hai kỳ  ");
            assertTrue(ok);

            verify(psUpdate).setInt(1, ACTOR_ID);
            verify(psUpdate).setString(2, "Đã lập lịch thu hai kỳ");
            verify(psUpdate).setInt(3, HANDOVER_ID);

            verify(mockHistoryDAO).insertHistory(eq(conn), eq(CONTRACT_ID),
                    eq(ContractHistory.EVENT_HANDOVER),
                    eq("Phòng Kế toán báo đã xử lý xong"),
                    eq(ACTOR_ID), eq("Đã lập lịch thu hai kỳ"));

            verify(conn).commit();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void testCompleteHandover_DoneNoteTruncatedTo500() throws Exception {
        ContractHistoryDAO mockHistoryDAO = mock(ContractHistoryDAO.class);
        ContractHandoverDAO customDao = new ContractHandoverDAO(mockHistoryDAO);

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsLock = singleRow(row(
                "contract_id", CONTRACT_ID,
                "department_id", DEPT_ACCOUNTING
        ));
        when(psLock.executeQuery()).thenReturn(rsLock);

        PreparedStatement psUpdate = mock(PreparedStatement.class);
        when(psUpdate.executeUpdate()).thenReturn(1);

        PreparedStatement psDept = mock(PreparedStatement.class);
        ResultSet rsDept2 = singleRow(row("department_name", "Kế toán"));
        when(psDept.executeQuery()).thenReturn(rsDept2);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE handover_id = ? AND done_at IS NULL FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("UPDATE contract_handovers")))
                .thenReturn(psUpdate);
        when(conn.prepareStatement(contains("SELECT department_name FROM departments")))
                .thenReturn(psDept);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            String longDoneNote = "B".repeat(600);
            boolean ok = customDao.completeHandover(HANDOVER_ID, ACTOR_ID, longDoneNote);
            assertTrue(ok);

            ArgumentCaptor<String> noteCaptor = ArgumentCaptor.forClass(String.class);
            verify(psUpdate).setString(eq(2), noteCaptor.capture());
            assertEquals(500, noteCaptor.getValue().length());
            assertTrue(noteCaptor.getValue().endsWith("…"));

            verify(mockHistoryDAO).insertHistory(eq(conn), eq(CONTRACT_ID),
                    eq(ContractHistory.EVENT_HANDOVER),
                    eq("Phòng Kế toán báo đã xử lý xong"),
                    eq(ACTOR_ID), eq(noteCaptor.getValue()));
        }
    }

    @Test
    public void testCompleteHandover_SQLExceptionRollback() throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenThrow(new SQLException("Query failed"));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean ok = dao.completeHandover(HANDOVER_ID, ACTOR_ID, "xong");
            assertFalse(ok);
            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    // ------------------------------------------------------------------
    // 4. Truy vấn danh sách chặng bàn giao (findHandoversOf)
    // ------------------------------------------------------------------

    @Test
    public void testFindHandoversOf_ContractIdInvalid() {
        assertEquals(0, dao.findHandoversOf(0).size());
        assertEquals(0, dao.findHandoversOf(-5).size());
    }

    @Test
    public void testFindHandoversOf_Success() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ResultSet rs = resultSetOf(List.of(
                row("handover_id", 1, "contract_id", CONTRACT_ID, "department_id", 1,
                        "department_name", "Kế toán", "handed_at", now, "handed_by", 10,
                        "done_at", null, "done_by", null, "handover_note", "note 1", "done_note", null,
                        "contract_code", "HD-001", "contract_title", "Tiêu đề HD",
                        "hb_last", "Nguyễn", "hb_mid", "Văn", "hb_first", "A",
                        "db_last", null, "db_mid", null, "db_first", null,
                        "ow_last", "Trần", "ow_mid", "Thị", "ow_first", "B")
        ));
        when(ps.executeQuery()).thenReturn(rs);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE h.contract_id = ? ORDER BY h.handed_at DESC, h.handover_id DESC")))
                .thenReturn(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractHandover> list = dao.findHandoversOf(CONTRACT_ID);
            assertEquals(1, list.size());
            ContractHandover h = list.get(0);
            assertEquals(1, h.getHandoverId());
            assertEquals(CONTRACT_ID, h.getContractId());
            assertEquals("Kế toán", h.getDepartmentName());
            assertEquals("Nguyễn Văn A", h.getHandedByName());
            assertNull(h.getDoneByName());
            assertEquals("Trần Thị B", h.getOwnerName());
            assertTrue(h.isPending());

            verify(ps).setInt(1, CONTRACT_ID);
        }
    }

    @Test
    public void testFindHandoversOf_SQLExceptionReturnsEmpty() throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenThrow(new SQLException("Query fail"));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractHandover> list = dao.findHandoversOf(CONTRACT_ID);
            assertNotNull(list);
            assertTrue(list.isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // 5. Truy vấn chặng đang chờ toàn hệ thống (findPendingHandovers)
    // ------------------------------------------------------------------

    @Test
    public void testFindPendingHandovers_NullOrEmptyOwnerIds() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = resultSetOf(List.of());
        when(ps.executeQuery()).thenReturn(rs);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE h.done_at IS NULL AND c.is_deleted = 0 ORDER BY h.handed_at ASC")))
                .thenReturn(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractHandover> list1 = dao.findPendingHandovers(null);
            assertTrue(list1.isEmpty());

            List<ContractHandover> list2 = dao.findPendingHandovers(List.of());
            assertTrue(list2.isEmpty());
        }
    }

    @Test
    public void testFindPendingHandovers_WithOwnerIds() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = resultSetOf(List.of(
                row("handover_id", 10, "contract_id", CONTRACT_ID, "department_id", 2,
                        "department_name", "Dự án", "handed_at", new Timestamp(System.currentTimeMillis()),
                        "handed_by", 10, "done_at", null, "done_by", null,
                        "handover_note", "note", "done_note", null,
                        "contract_code", "HD-002", "contract_title", "HD Triển khai",
                        "hb_last", "Lê", "hb_mid", null, "hb_first", "C",
                        "db_last", null, "db_mid", null, "db_first", null,
                        "ow_last", "Phạm", "ow_mid", "Văn", "ow_first", "D")
        ));
        when(ps.executeQuery()).thenReturn(rs);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE h.done_at IS NULL AND c.is_deleted = 0 AND c.owner_id IN (?) ORDER BY h.handed_at ASC")))
                .thenReturn(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractHandover> list = dao.findPendingHandovers(List.of(5));
            assertEquals(1, list.size());
            assertEquals("Dự án", list.get(0).getDepartmentName());
            assertEquals("Lê C", list.get(0).getHandedByName());
            assertEquals("Phạm Văn D", list.get(0).getOwnerName());

            verify(ps).setInt(1, 5);
        }
    }

    @Test
    public void testFindPendingHandovers_SQLExceptionReturnsEmpty() throws Exception {
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenThrow(new SQLException("Query fail"));

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractHandover> list = dao.findPendingHandovers(null);
            assertNotNull(list);
            assertTrue(list.isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // 6. Ánh xạ dòng & Tiện ích nội bộ
    // ------------------------------------------------------------------

    @Test
    public void testMapHandoverRow_DoneByNotNull() throws Exception {
        Timestamp handedAt = new Timestamp(System.currentTimeMillis() - 100000);
        Timestamp doneAt = new Timestamp(System.currentTimeMillis());
        ResultSet rs = singleRow(row(
                "handover_id", 7,
                "contract_id", 88,
                "department_id", 3,
                "department_name", "Kỹ thuật",
                "handed_at", handedAt,
                "handed_by", 12,
                "done_at", doneAt,
                "done_by", 15,
                "handover_note", "giao",
                "done_note", "xong",
                "contract_code", "HD-088",
                "contract_title", "Tiêu đề",
                "hb_last", "Nguyễn", "hb_mid", "Văn", "hb_first", "A",
                "db_last", "Trần", "db_mid", null, "db_first", "B",
                "ow_last", "Lê", "ow_mid", null, "ow_first", "C"
        ));
        rs.next();

        ContractHandover h = ContractHandoverDAO.mapHandoverRow(rs);
        assertEquals(7, h.getHandoverId());
        assertEquals(88, h.getContractId());
        assertEquals(3, h.getDepartmentId());
        assertEquals("Kỹ thuật", h.getDepartmentName());
        assertEquals(handedAt, h.getHandedAt());
        assertEquals(12, h.getHandedBy());
        assertEquals(doneAt, h.getDoneAt());
        assertEquals(Integer.valueOf(15), h.getDoneBy());
        assertEquals("giao", h.getHandoverNote());
        assertEquals("xong", h.getDoneNote());
        assertEquals("HD-088", h.getContractCode());
        assertEquals("Tiêu đề", h.getContractTitle());
        assertEquals("Nguyễn Văn A", h.getHandedByName());
        assertEquals("Trần B", h.getDoneByName());
        assertEquals("Lê C", h.getOwnerName());
        assertFalse(h.isPending());
    }

    @Test
    public void testDepartmentName_FoundAndFallback() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rsFound = singleRow(row("department_name", "Kế toán"));
        ResultSet rsNotFound = emptyResultSet();
        when(ps.executeQuery()).thenReturn(rsFound, rsNotFound);
        when(conn.prepareStatement(contains("FROM departments WHERE department_id = ?"))).thenReturn(ps);

        assertEquals("Kế toán", dao.departmentName(conn, 1));
        assertEquals("#999", dao.departmentName(conn, 999));
    }

    @Test
    public void testHasPendingHandover() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rsHasNext = singleRow(row("1", 1));
        ResultSet rsNoNext = emptyResultSet();
        when(ps.executeQuery()).thenReturn(rsHasNext, rsNoNext);
        when(conn.prepareStatement(contains("FROM contract_handovers"))).thenReturn(ps);

        assertTrue(dao.hasPendingHandover(conn, 1, 1));
        assertFalse(dao.hasPendingHandover(conn, 1, 2));
    }

    // ------------------------------------------------------------------
    // 7. Kiểm tra ủy quyền từ ContractDAO sang ContractHandoverDAO
    // ------------------------------------------------------------------

    @Test
    public void testContractDAO_Delegation() {
        ContractHandoverDAO mockHandover = mock(ContractHandoverDAO.class);
        ContractDAO contractDAO = new ContractDAO(mockHandover);

        assertSame(mockHandover, contractDAO.getHandoverDAO());

        // handOverToDepartments
        when(mockHandover.handOverToDepartments(1, List.of(2), "note", 3)).thenReturn(1);
        assertEquals(1, contractDAO.handOverToDepartments(1, List.of(2), "note", 3));
        verify(mockHandover).handOverToDepartments(1, List.of(2), "note", 3);

        // completeHandover
        when(mockHandover.completeHandover(10, 3, "done")).thenReturn(true);
        assertTrue(contractDAO.completeHandover(10, 3, "done"));
        verify(mockHandover).completeHandover(10, 3, "done");

        // findHandoversOf
        List<ContractHandover> list = List.of(new ContractHandover());
        when(mockHandover.findHandoversOf(1)).thenReturn(list);
        assertSame(list, contractDAO.findHandoversOf(1));
        verify(mockHandover).findHandoversOf(1);

        // findPendingHandovers
        when(mockHandover.findPendingHandovers(List.of(5))).thenReturn(list);
        assertSame(list, contractDAO.findPendingHandovers(List.of(5)));
        verify(mockHandover).findPendingHandovers(List.of(5));

        // Constructors test
        ContractDAO daoDefault = new ContractDAO();
        assertNotNull(daoDefault.getHandoverDAO());
        ContractHistoryDAO hist = new ContractHistoryDAO();
        ContractDAO daoWithHist = new ContractDAO(hist);
        assertNotNull(daoWithHist.getHandoverDAO());
        assertSame(hist, daoWithHist.getHandoverDAO().getHistoryDAO());
    }
}
