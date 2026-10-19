package poscs.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import poscs.model.Contract;
import poscs.model.ContractHistory;
import poscs.model.ContractLink;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Unit Test cho {@link ContractLinkDAO} (Task 4.6 - UC-47).
 *
 * Kiểm thử toàn diện:
 * - Khởi tạo và Dependency Injection với {@link ContractHistoryDAO}.
 * - Các hàm tiện ích: isDuplicateKeyError, canLink.
 * - Nối hợp đồng (linkContracts):
 *   + Kiểm tra tham số không hợp lệ (sellId <= 0, buyId <= 0, trùng ID, actorId <= 0).
 *   + Không tìm thấy hợp đồng bán hoặc mua, hoặc vi phạm điều kiện canLink (sai chiều, là phụ lục parent_contract_id != null).
 *   + Nối thành công với quan hệ mặc định, cắt chuỗi ghi chú an toàn (tối đa 255 ký tự).
 *   + Ghi nhận 2 dòng lịch sử EVENT_LINKED cho cả 2 hợp đồng trong transaction.
 *   + Xử lý lỗi trùng cặp UNIQUE (uq_contract_links_pair) trả về LINK_DUPLICATE.
 *   + Xử lý lỗi CSDL (rollback, trả về LINK_FAILED).
 * - Gỡ liên kết (unlinkContracts):
 *   + Kiểm tra tham số không hợp lệ.
 *   + Không tìm thấy liên kết.
 *   + Gỡ thành công: đọc mã hợp đồng trước khi xoá, xoá link, ghi lịch sử cả 2 bên.
 *   + Thất bại khi xoá (executeUpdate = 0) hoặc lỗi CSDL.
 * - Truy vấn danh sách liên kết (findLinksOf):
 *   + contractId <= 0 trả về danh sách rỗng.
 *   + Kiểm tra câu lệnh SQL được sinh ra (lọc is_deleted = 0, loại trừ phụ lục nháp, ORDER BY l.created_at, l.link_id).
 *   + Ánh xạ đầy đủ các cột (thông tin hợp đồng đầu kia, doanh nghiệp, người tạo, phụ lục, sharedCount).
 *   + Xử lý ngoại lệ CSDL an toàn.
 * - Tính tổng giá trị đầu vào (sumLinkedBuyValue):
 *   + sellContractId <= 0, kiểm tra câu lệnh SQL có b.is_deleted = 0, có kết quả, kết quả null, ngoại lệ CSDL.
 * - Kiểm tra ủy quyền từ ContractDAO sang ContractLinkDAO.
 */
public class ContractLinkDAOTest {

    private final ContractLinkDAO dao = new ContractLinkDAO();

    private static final int SELL_ID = 10;
    private static final int BUY_ID = 20;
    private static final int LINK_ID = 5;
    private static final int ACTOR_ID = 15;

    // ------------------------------------------------------------------
    // 1. Khởi tạo & Tiện ích
    // ------------------------------------------------------------------

    @Test
    public void testConstructorsAndGetters() {
        ContractLinkDAO defaultDao = new ContractLinkDAO();
        assertNotNull(defaultDao.getHistoryDAO());

        ContractHistoryDAO customHistory = new ContractHistoryDAO();
        ContractLinkDAO customDao = new ContractLinkDAO(customHistory);
        assertSame(customHistory, customDao.getHistoryDAO());

        ContractLinkDAO nullHistoryDao = new ContractLinkDAO(null);
        assertNotNull(nullHistoryDao.getHistoryDAO());
    }

    @Test
    public void testIsDuplicateKeyError() {
        assertFalse(ContractLinkDAO.isDuplicateKeyError(null, "uq_contract_links_pair"));

        SQLException otherError = new SQLException("Table not found", "42S02", 1146);
        assertFalse(ContractLinkDAO.isDuplicateKeyError(otherError, "uq_contract_links_pair"));

        SQLException dupError = new SQLException("Duplicate entry '10-20' for key 'uq_contract_links_pair'", "23000", 1062);
        assertTrue(ContractLinkDAO.isDuplicateKeyError(dupError, "uq_contract_links_pair"));
        assertFalse(ContractLinkDAO.isDuplicateKeyError(dupError, "contract_code"));
        assertTrue(ContractLinkDAO.isDuplicateKeyError(dupError, null));
        assertTrue(ContractLinkDAO.isDuplicateKeyError(dupError, ""));

        SQLException sqlStateOnly = new SQLException("Duplicate entry", "23000", 0);
        assertTrue(ContractLinkDAO.isDuplicateKeyError(sqlStateOnly, null));
    }

    @Test
    public void testCanLink_AllScenarios() {
        Contract sell = new Contract();
        sell.setContractId(1);
        sell.setDirection("Bán");
        sell.setParentContractId(null);

        Contract buy = new Contract();
        buy.setContractId(2);
        buy.setDirection("Mua");
        buy.setParentContractId(null);

        // Hợp lệ: Bán - Mua, 2 ID khác nhau, đều là hợp đồng gốc
        assertTrue(ContractLinkDAO.canLink(sell, buy));

        // Một trong hai null
        assertFalse(ContractLinkDAO.canLink(null, buy));
        assertFalse(ContractLinkDAO.canLink(sell, null));

        // Trùng ID
        buy.setContractId(1);
        assertFalse(ContractLinkDAO.canLink(sell, buy));
        buy.setContractId(2);

        // Chiều sai: Bán nối Bán
        buy.setDirection("Bán");
        assertFalse(ContractLinkDAO.canLink(sell, buy));
        buy.setDirection("Mua");

        // Chiều sai: Mua nối Mua
        sell.setDirection("Mua");
        assertFalse(ContractLinkDAO.canLink(sell, buy));
        sell.setDirection("Bán");

        // Hợp đồng bán là phụ lục (parentContractId != null)
        sell.setParentContractId(100);
        assertFalse(ContractLinkDAO.canLink(sell, buy));
        sell.setParentContractId(null);

        // Hợp đồng mua là phụ lục (parentContractId != null)
        buy.setParentContractId(200);
        assertFalse(ContractLinkDAO.canLink(sell, buy));
        buy.setParentContractId(null);
    }

    // ------------------------------------------------------------------
    // 2. Nối hợp đồng (linkContracts)
    // ------------------------------------------------------------------

    @Test
    public void testLinkContracts_InvalidParameters() {
        assertEquals(ContractLinkDAO.LINK_INVALID, dao.linkContracts(0, BUY_ID, "ghi chu", ACTOR_ID));
        assertEquals(ContractLinkDAO.LINK_INVALID, dao.linkContracts(-1, BUY_ID, "ghi chu", ACTOR_ID));
        assertEquals(ContractLinkDAO.LINK_INVALID, dao.linkContracts(SELL_ID, 0, "ghi chu", ACTOR_ID));
        assertEquals(ContractLinkDAO.LINK_INVALID, dao.linkContracts(SELL_ID, -5, "ghi chu", ACTOR_ID));
        assertEquals(ContractLinkDAO.LINK_INVALID, dao.linkContracts(SELL_ID, SELL_ID, "ghi chu", ACTOR_ID));
        assertEquals(ContractLinkDAO.LINK_INVALID, dao.linkContracts(SELL_ID, BUY_ID, "ghi chu", 0));
        assertEquals(ContractLinkDAO.LINK_INVALID, dao.linkContracts(SELL_ID, BUY_ID, "ghi chu", -1));
    }

    @Test
    public void testLinkContracts_SellOrBuyNotFoundOrInvalidDirectionOrAmendment() throws Exception {
        // Kịch bản 1: Không tìm thấy hợp đồng bán
        PreparedStatement psLockSell = mock(PreparedStatement.class);
        ResultSet rsEmpty = resultSetOf(List.of());
        when(psLockSell.executeQuery()).thenReturn(rsEmpty);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLockSell);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(ContractLinkDAO.LINK_INVALID, dao.linkContracts(SELL_ID, BUY_ID, "ghi chu", ACTOR_ID));
            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }

        // Kịch bản 2: Tìm thấy hợp đồng bán nhưng KHÔNG tìm thấy hợp đồng mua
        PreparedStatement psLockSellOnly = mock(PreparedStatement.class);
        ResultSet rsSellOnly = resultSetOf(List.of(
                row("contract_id", SELL_ID, "contract_code", "HD-BAN-01", "direction", "Bán", "parent_contract_id", null)
        ));
        ResultSet rsBuyEmpty = resultSetOf(List.of());
        when(psLockSellOnly.executeQuery()).thenReturn(rsSellOnly, rsBuyEmpty);

        Connection connBuyEmpty = mock(Connection.class);
        when(connBuyEmpty.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLockSellOnly);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connBuyEmpty);

            assertEquals(ContractLinkDAO.LINK_INVALID, dao.linkContracts(SELL_ID, BUY_ID, "ghi chu", ACTOR_ID));
            verify(connBuyEmpty).rollback();
        }

        // Kịch bản 3: Hợp đồng bán có chiều "Mua" (sai chiều)
        PreparedStatement psLockWrongDir = mock(PreparedStatement.class);
        ResultSet rsSellWrong = resultSetOf(List.of(
                row("contract_id", SELL_ID, "contract_code", "HD-01", "direction", "Mua", "parent_contract_id", null)
        ));
        ResultSet rsBuyOk = resultSetOf(List.of(
                row("contract_id", BUY_ID, "contract_code", "HD-02", "direction", "Mua", "parent_contract_id", null)
        ));
        when(psLockWrongDir.executeQuery()).thenReturn(rsSellWrong, rsBuyOk);

        Connection connWrongDir = mock(Connection.class);
        when(connWrongDir.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLockWrongDir);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connWrongDir);

            assertEquals(ContractLinkDAO.LINK_INVALID, dao.linkContracts(SELL_ID, BUY_ID, "ghi chu", ACTOR_ID));
            verify(connWrongDir).rollback();
        }

        // Kịch bản 4: Hợp đồng mua là phụ lục (parent_contract_id != null)
        PreparedStatement psLockBuyAmendment = mock(PreparedStatement.class);
        ResultSet rsSellValid = resultSetOf(List.of(
                row("contract_id", SELL_ID, "contract_code", "HD-BAN-01", "direction", "Bán", "parent_contract_id", null)
        ));
        ResultSet rsBuyAmendment = resultSetOf(List.of(
                row("contract_id", BUY_ID, "contract_code", "PL-MUA-01", "direction", "Mua", "parent_contract_id", 100)
        ));
        when(psLockBuyAmendment.executeQuery()).thenReturn(rsSellValid, rsBuyAmendment);

        Connection connBuyAmend = mock(Connection.class);
        when(connBuyAmend.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLockBuyAmendment);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connBuyAmend);

            assertEquals(ContractLinkDAO.LINK_INVALID, dao.linkContracts(SELL_ID, BUY_ID, "ghi chu", ACTOR_ID));
            verify(connBuyAmend).rollback();
        }

        // Kịch bản 5: Hợp đồng bán là phụ lục (parent_contract_id != null)
        PreparedStatement psLockSellAmendment = mock(PreparedStatement.class);
        ResultSet rsSellAmendment = resultSetOf(List.of(
                row("contract_id", SELL_ID, "contract_code", "PL-BAN-01", "direction", "Bán", "parent_contract_id", 200)
        ));
        ResultSet rsBuyValid = resultSetOf(List.of(
                row("contract_id", BUY_ID, "contract_code", "HD-MUA-01", "direction", "Mua", "parent_contract_id", null)
        ));
        when(psLockSellAmendment.executeQuery()).thenReturn(rsSellAmendment, rsBuyValid);

        Connection connSellAmend = mock(Connection.class);
        when(connSellAmend.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLockSellAmendment);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connSellAmend);

            assertEquals(ContractLinkDAO.LINK_INVALID, dao.linkContracts(SELL_ID, BUY_ID, "ghi chu", ACTOR_ID));
            verify(connSellAmend).rollback();
        }
    }

    @Test
    public void testLinkContracts_SuccessDefaultRelationType() throws Exception {
        ContractHistoryDAO mockHistory = mock(ContractHistoryDAO.class);
        ContractLinkDAO customDao = new ContractLinkDAO(mockHistory);

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsSell = resultSetOf(List.of(
                row("contract_id", SELL_ID, "contract_code", "HD-BAN-001", "direction", "Bán", "parent_contract_id", null)
        ));
        ResultSet rsBuy = resultSetOf(List.of(
                row("contract_id", BUY_ID, "contract_code", "HD-MUA-002", "direction", "Mua", "parent_contract_id", null)
        ));
        when(psLock.executeQuery()).thenReturn(rsSell, rsBuy);

        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(1);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("INSERT INTO contract_links")))
                .thenReturn(psInsert);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = customDao.linkContracts(SELL_ID, BUY_ID, "  Mua thiết bị định tuyến  ", ACTOR_ID);
            assertEquals(ContractLinkDAO.LINK_OK, result);

            verify(conn).setAutoCommit(false);
            verify(psInsert).setInt(1, SELL_ID);
            verify(psInsert).setInt(2, BUY_ID);
            verify(psInsert).setString(3, ContractLinkDAO.DEFAULT_RELATION_TYPE);
            verify(psInsert).setString(4, "Mua thiết bị định tuyến");
            verify(psInsert).setInt(5, ACTOR_ID);

            verify(mockHistory).insertHistory(
                    eq(conn),
                    eq(SELL_ID),
                    eq(ContractHistory.EVENT_LINKED),
                    eq("Nối với hợp đồng mua HD-MUA-002 — đầu vào phục vụ hợp đồng này"),
                    eq(ACTOR_ID),
                    eq("Mua thiết bị định tuyến")
            );

            verify(mockHistory).insertHistory(
                    eq(conn),
                    eq(BUY_ID),
                    eq(ContractHistory.EVENT_LINKED),
                    eq("Phục vụ hợp đồng bán HD-BAN-001"),
                    eq(ACTOR_ID),
                    eq("Mua thiết bị định tuyến")
            );

            verify(conn).commit();
            verify(conn, never()).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void testLinkContracts_SuccessWithNoteTruncation() throws Exception {
        ContractHistoryDAO mockHistory = mock(ContractHistoryDAO.class);
        ContractLinkDAO customDao = new ContractLinkDAO(mockHistory);

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsSell = resultSetOf(List.of(
                row("contract_id", SELL_ID, "contract_code", "HD-BAN-001", "direction", "Bán", "parent_contract_id", null)
        ));
        ResultSet rsBuy = resultSetOf(List.of(
                row("contract_id", BUY_ID, "contract_code", "HD-MUA-002", "direction", "Mua", "parent_contract_id", null)
        ));
        when(psLock.executeQuery()).thenReturn(rsSell, rsBuy);

        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(1);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("INSERT INTO contract_links")))
                .thenReturn(psInsert);

        String longNote = "Ghi chú hợp đồng ".repeat(30); // > 255 chars

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = customDao.linkContracts(SELL_ID, BUY_ID, "   " + longNote + "   ", ACTOR_ID);
            assertEquals(ContractLinkDAO.LINK_OK, result);

            verify(psInsert).setString(4, ContractDocumentDAO.truncate(longNote.trim(), ContractLinkDAO.NOTE_MAX_LENGTH));
            verify(conn).commit();
        }
    }

    @Test
    public void testLinkContracts_DuplicateKeyReturnsLinkDuplicate() throws Exception {
        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsSell = resultSetOf(List.of(
                row("contract_id", SELL_ID, "contract_code", "HD-BAN-001", "direction", "Bán", "parent_contract_id", null)
        ));
        ResultSet rsBuy = resultSetOf(List.of(
                row("contract_id", BUY_ID, "contract_code", "HD-MUA-002", "direction", "Mua", "parent_contract_id", null)
        ));
        when(psLock.executeQuery()).thenReturn(rsSell, rsBuy);

        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenThrow(new SQLException("Duplicate entry for key 'uq_contract_links_pair'", "23000", 1062));

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("INSERT INTO contract_links")))
                .thenReturn(psInsert);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = dao.linkContracts(SELL_ID, BUY_ID, null, ACTOR_ID);
            assertEquals(ContractLinkDAO.LINK_DUPLICATE, result);

            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void testLinkContracts_SqlExceptionGenericFailure() throws Exception {
        PreparedStatement psLock = mock(PreparedStatement.class);
        when(psLock.executeQuery()).thenThrow(new SQLException("Connection reset", "08S01", 1053));

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = dao.linkContracts(SELL_ID, BUY_ID, null, ACTOR_ID);
            assertEquals(ContractLinkDAO.LINK_FAILED, result);

            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void testLinkContracts_ExecuteUpdateReturnsZero() throws Exception {
        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsSell = resultSetOf(List.of(
                row("contract_id", SELL_ID, "contract_code", "HD-BAN-001", "direction", "Bán", "parent_contract_id", null)
        ));
        ResultSet rsBuy = resultSetOf(List.of(
                row("contract_id", BUY_ID, "contract_code", "HD-MUA-002", "direction", "Mua", "parent_contract_id", null)
        ));
        when(psLock.executeQuery()).thenReturn(rsSell, rsBuy);

        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(0);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("INSERT INTO contract_links")))
                .thenReturn(psInsert);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = dao.linkContracts(SELL_ID, BUY_ID, null, ACTOR_ID);
            assertEquals(ContractLinkDAO.LINK_FAILED, result);

            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    // ------------------------------------------------------------------
    // 3. Gỡ liên kết (unlinkContracts)
    // ------------------------------------------------------------------

    @Test
    public void testUnlinkContracts_InvalidParameters() {
        assertFalse(dao.unlinkContracts(0, ACTOR_ID));
        assertFalse(dao.unlinkContracts(-1, ACTOR_ID));
        assertFalse(dao.unlinkContracts(LINK_ID, 0));
        assertFalse(dao.unlinkContracts(LINK_ID, -1));
    }

    @Test
    public void testUnlinkContracts_NotFound() throws Exception {
        PreparedStatement psSelect = mock(PreparedStatement.class);
        ResultSet rsNotFound = resultSetOf(List.of());
        when(psSelect.executeQuery()).thenReturn(rsNotFound);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE l.link_id = ? FOR UPDATE")))
                .thenReturn(psSelect);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.unlinkContracts(LINK_ID, ACTOR_ID));
            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void testUnlinkContracts_Success() throws Exception {
        ContractHistoryDAO mockHistory = mock(ContractHistoryDAO.class);
        ContractLinkDAO customDao = new ContractLinkDAO(mockHistory);

        PreparedStatement psSelect = mock(PreparedStatement.class);
        ResultSet rsSelect = resultSetOf(List.of(
                row("sell_contract_id", SELL_ID, "buy_contract_id", BUY_ID,
                    "sell_code", "HD-BAN-001", "buy_code", "HD-MUA-002")
        ));
        when(psSelect.executeQuery()).thenReturn(rsSelect);

        PreparedStatement psDelete = mock(PreparedStatement.class);
        when(psDelete.executeUpdate()).thenReturn(1);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE l.link_id = ? FOR UPDATE"))).thenReturn(psSelect);
        when(conn.prepareStatement(contains("DELETE FROM contract_links WHERE link_id = ?"))).thenReturn(psDelete);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean result = customDao.unlinkContracts(LINK_ID, ACTOR_ID);
            assertTrue(result);

            verify(psSelect).setInt(1, LINK_ID);
            verify(psDelete).setInt(1, LINK_ID);

            verify(mockHistory).insertHistory(
                    eq(conn),
                    eq(SELL_ID),
                    eq(ContractHistory.EVENT_LINKED),
                    eq("Gỡ liên kết với hợp đồng mua HD-MUA-002"),
                    eq(ACTOR_ID),
                    isNull()
            );

            verify(mockHistory).insertHistory(
                    eq(conn),
                    eq(BUY_ID),
                    eq(ContractHistory.EVENT_LINKED),
                    eq("Gỡ liên kết với hợp đồng bán HD-BAN-001"),
                    eq(ACTOR_ID),
                    isNull()
            );

            verify(conn).commit();
            verify(conn, never()).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void testUnlinkContracts_DeleteReturnsZeroOrSqlException() throws Exception {
        // Kịch bản 1: Delete trả về 0
        PreparedStatement psSelect = mock(PreparedStatement.class);
        ResultSet rsSelect = resultSetOf(List.of(
                row("sell_contract_id", SELL_ID, "buy_contract_id", BUY_ID,
                    "sell_code", "HD-BAN-001", "buy_code", "HD-MUA-002")
        ));
        when(psSelect.executeQuery()).thenReturn(rsSelect);

        PreparedStatement psDelete = mock(PreparedStatement.class);
        when(psDelete.executeUpdate()).thenReturn(0);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("WHERE l.link_id = ? FOR UPDATE"))).thenReturn(psSelect);
        when(conn.prepareStatement(contains("DELETE FROM contract_links WHERE link_id = ?"))).thenReturn(psDelete);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.unlinkContracts(LINK_ID, ACTOR_ID));
            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }

        // Kịch bản 2: SQLException khi truy vấn
        when(psSelect.executeQuery()).thenThrow(new SQLException("Lock wait timeout", "40001", 1205));
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.unlinkContracts(LINK_ID, ACTOR_ID));
        }
    }

    // ------------------------------------------------------------------
    // 4. Truy vấn danh sách liên kết (findLinksOf)
    // ------------------------------------------------------------------

    @Test
    public void testFindLinksOf_InvalidContractId() {
        assertTrue(dao.findLinksOf(0).isEmpty());
        assertTrue(dao.findLinksOf(-1).isEmpty());
    }

    @Test
    public void testFindLinksOf_SuccessMapping() throws Exception {
        Date effective = Date.valueOf(LocalDate.now().minusDays(10));
        Date end = Date.valueOf(LocalDate.now().plusDays(20)); // Sắp hết hạn (<= 30 ngày)
        Timestamp createdAt = Timestamp.valueOf("2026-05-01 10:30:00");

        List<Map<String, Object>> rows = List.of(
                row(
                        "link_id", 101,
                        "sell_contract_id", 1,
                        "buy_contract_id", 2,
                        "relation_type", "Đầu vào phục vụ đầu ra",
                        "note", "Ghi chú liên kết",
                        "created_by", 8,
                        "created_at", createdAt,
                        "other_id", 2,
                        "other_code", "HD-MUA-002",
                        "other_title", "Mua thiết bị viễn thông",
                        "other_direction", "Mua",
                        "other_progress", "Đã ký",
                        "other_value", new BigDecimal("500000000.00"),
                        "other_amendment_value", new BigDecimal("50000000.00"),
                        "other_effective", effective,
                        "other_end", end,
                        "other_enterprise", "Công ty TNHH Cung Cấp Thiết Bị",
                        "last_name", "Nguyễn",
                        "middle_name", "Văn",
                        "first_name", "An",
                        "shared_count", 2
                )
        );

        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rsLinks = resultSetOf(rows);
        when(ps.executeQuery()).thenReturn(rsLinks);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractLink> links = dao.findLinksOf(1);
            assertEquals(1, links.size());

            // Kiểm tra câu lệnh SQL được prepare có đủ các điều kiện cốt lõi của UC-47
            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(conn).prepareStatement(sqlCaptor.capture());
            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("WHERE (l.sell_contract_id = ? OR l.buy_contract_id = ?) AND o.is_deleted = 0"));
            assertTrue(sql.contains("ORDER BY l.created_at, l.link_id"));
            assertTrue(sql.contains("shared_count"));
            assertTrue(sql.contains("other_amendment_value"));

            verify(ps).setInt(1, 1);
            verify(ps).setInt(2, 1);
            verify(ps).setInt(3, 1);

            ContractLink link = links.get(0);
            assertEquals(101, link.getLinkId());
            assertEquals(1, link.getSellContractId());
            assertEquals(2, link.getBuyContractId());
            assertEquals("Đầu vào phục vụ đầu ra", link.getRelationType());
            assertEquals("Ghi chú liên kết", link.getNote());
            assertEquals(8, link.getCreatedBy());
            assertEquals(createdAt, link.getCreatedAt());
            assertEquals("Nguyễn Văn An", link.getCreatedByName());
            assertEquals(2, link.getSharedCount());
            assertTrue(link.isShared());

            Contract other = link.getOther();
            assertNotNull(other);
            assertEquals(2, other.getContractId());
            assertEquals("HD-MUA-002", other.getContractCode());
            assertEquals("Mua thiết bị viễn thông", other.getTitle());
            assertEquals("Mua", other.getDirection());
            assertEquals("Đã ký", other.getProgressStatus());
            assertEquals(new BigDecimal("500000000.00"), other.getContractValue());
            assertEquals(new BigDecimal("50000000.00"), other.getAmendmentValueSigned());
            assertEquals(effective, other.getEffectiveDate());
            assertEquals(end, other.getEndDate());
            assertEquals("Sắp hết hạn", other.getStatus());
            assertNotNull(other.getEnterprise());
            assertEquals("Công ty TNHH Cung Cấp Thiết Bị", other.getEnterprise().getEnterpriseName());
        }
    }

    @Test
    public void testFindLinksOf_SQLExceptionReturnsEmptyList() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenThrow(new SQLException("DB connection failure"));

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractLink> links = dao.findLinksOf(1);
            assertNotNull(links);
            assertTrue(links.isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // 5. Tính tổng giá trị đầu vào (sumLinkedBuyValue)
    // ------------------------------------------------------------------

    @Test
    public void testSumLinkedBuyValue_Scenarios() throws Exception {
        assertEquals(BigDecimal.ZERO, dao.sumLinkedBuyValue(0));
        assertEquals(BigDecimal.ZERO, dao.sumLinkedBuyValue(-5));

        // Có giá trị trả về
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rsSum = resultSetOf(List.of(row("1", new BigDecimal("750000000.00"))));
        when(ps.executeQuery()).thenReturn(rsSum);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            BigDecimal sum = dao.sumLinkedBuyValue(SELL_ID);
            assertEquals(new BigDecimal("750000000.00"), sum);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(conn).prepareStatement(sqlCaptor.capture());
            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("WHERE l.sell_contract_id = ? AND b.is_deleted = 0"));
            assertTrue(sql.contains("SUM(a.contract_value)"));

            verify(ps).setInt(1, SELL_ID);
        }

        // Kết quả null
        ResultSet rsNullSum = resultSetOf(List.of(row("1", null)));
        when(ps.executeQuery()).thenReturn(rsNullSum);
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            BigDecimal sum = dao.sumLinkedBuyValue(SELL_ID);
            assertEquals(BigDecimal.ZERO, sum);
        }

        // SQLException
        when(ps.executeQuery()).thenThrow(new SQLException("Error"));
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            BigDecimal sum = dao.sumLinkedBuyValue(SELL_ID);
            assertEquals(BigDecimal.ZERO, sum);
        }
    }

    // ------------------------------------------------------------------
    // 6. Kiểm tra ủy quyền từ ContractDAO sang ContractLinkDAO
    // ------------------------------------------------------------------

    @Test
    public void testContractDAODelegation() {
        ContractLinkDAO mockLinkDAO = mock(ContractLinkDAO.class);
        ContractDAO contractDao = new ContractDAO(mockLinkDAO);
        assertSame(mockLinkDAO, contractDao.getLinkDAO());

        // linkContracts
        when(mockLinkDAO.linkContracts(SELL_ID, BUY_ID, "ghi chú", ACTOR_ID)).thenReturn(ContractLinkDAO.LINK_OK);
        assertEquals(ContractDAO.LINK_OK, contractDao.linkContracts(SELL_ID, BUY_ID, "ghi chú", ACTOR_ID));
        verify(mockLinkDAO).linkContracts(SELL_ID, BUY_ID, "ghi chú", ACTOR_ID);

        // unlinkContracts
        when(mockLinkDAO.unlinkContracts(LINK_ID, ACTOR_ID)).thenReturn(true);
        assertTrue(contractDao.unlinkContracts(LINK_ID, ACTOR_ID));
        verify(mockLinkDAO).unlinkContracts(LINK_ID, ACTOR_ID);

        // findLinksOf
        List<ContractLink> dummyLinks = List.of(new ContractLink());
        when(mockLinkDAO.findLinksOf(1)).thenReturn(dummyLinks);
        assertSame(dummyLinks, contractDao.findLinksOf(1));
        verify(mockLinkDAO).findLinksOf(1);

        // sumLinkedBuyValue
        when(mockLinkDAO.sumLinkedBuyValue(SELL_ID)).thenReturn(new BigDecimal("1000000"));
        assertEquals(new BigDecimal("1000000"), contractDao.sumLinkedBuyValue(SELL_ID));
        verify(mockLinkDAO).sumLinkedBuyValue(SELL_ID);

        // canLink
        Contract sell = new Contract();
        sell.setContractId(1);
        sell.setDirection("Bán");
        Contract buy = new Contract();
        buy.setContractId(2);
        buy.setDirection("Mua");
        assertTrue(ContractDAO.canLink(sell, buy));

        // Constructors test
        ContractDAO daoDefault = new ContractDAO();
        assertNotNull(daoDefault.getLinkDAO());
        ContractHistoryDAO hist = new ContractHistoryDAO();
        ContractDAO daoWithHist = new ContractDAO(hist);
        assertNotNull(daoWithHist.getLinkDAO());
        assertSame(hist, daoWithHist.getLinkDAO().getHistoryDAO());
    }
}
