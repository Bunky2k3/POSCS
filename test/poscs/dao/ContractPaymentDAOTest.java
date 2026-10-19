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
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import poscs.common.Period;
import poscs.model.ContractHistory;
import poscs.model.ContractPayment;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Unit Test cho {@link ContractPaymentDAO} (Task 4.4 - UC-53, BR-18).
 *
 * Kiểm thử toàn diện:
 * - Định dạng tiền VNĐ và ngày tháng theo quy định BR-18 (formatMoney, formatDate).
 * - Tra cứu kỳ thanh toán (findPaymentsByContractId).
 * - Lập kỳ thanh toán (insertPayment): kiểm tra dữ liệu bắt buộc (số tiền > 0, ngày đến hạn),
 *   chặn khi hợp đồng đã đóng băng (Đã thanh lý / Chấm dứt sớm), rollback và không commit khi đóng băng,
 *   ghi nhật ký EVENT_PAYMENT_ADDED, rollback khi lỗi.
 * - Ghi nhận đã thu (markPaymentPaid): kiểm tra ngày thu, không ghi đè kỳ đã thu,
 *   cho phép ghi nhận ngay cả khi hợp đồng đã thanh lý (tiền bảo hành sau thanh lý),
 *   ghi nhật ký EVENT_PAYMENT_PAID, rollback khi lỗi.
 * - Xoá kỳ lập nhầm (deletePayment): chặn và rollback khi hợp đồng đóng băng, kiểm tra tồn tại trước khi xoá,
 *   ghi nhật ký EVENT_PAYMENT_REMOVED, rollback khi lỗi.
 * - Tính tổng kỳ cụm hợp đồng (sumScheduledPaymentsForCluster).
 * - Tính toán doanh thu thực thu cho Dashboard (sumInvoiceAmountInPeriod, sumInvoiceAmountByMonth):
 *   chặn đầu vào năm/tháng sai, kiểm tra câu SQL chứa c.direction = 'Bán', c.is_deleted = 0, p.paid_date IS NOT NULL,
 *   kiểm tra bind đầy đủ các tham số scope (provinceIds, ownerIds), an toàn lỗi SQL trả BigDecimal.ZERO.
 * - Ủy quyền đầy đủ từ {@link ContractDAO} sang {@link ContractPaymentDAO}.
 */
public class ContractPaymentDAOTest {

    private final ContractPaymentDAO dao = new ContractPaymentDAO();

    private static final int CONTRACT_ID = 10;
    private static final int PAYMENT_ID = 55;
    private static final int ACTOR_ID = 9;

    // ------------------------------------------------------------------
    // 1. Tiện ích: formatMoney & formatDate (BR-18)
    // ------------------------------------------------------------------

    @Test
    public void formatMoney_variousAmounts() {
        assertEquals("", ContractPaymentDAO.formatMoney(null));
        assertEquals("0 đ", ContractPaymentDAO.formatMoney(BigDecimal.ZERO));
        assertEquals("500.000 đ", ContractPaymentDAO.formatMoney(new BigDecimal("500000")));
        assertEquals("1.500.000.000 đ", ContractPaymentDAO.formatMoney(new BigDecimal("1500000000")));
    }

    @Test
    public void formatDate_variousDates() {
        assertEquals("", ContractPaymentDAO.formatDate(null));
        assertEquals("19/10/2026", ContractPaymentDAO.formatDate(Date.valueOf(LocalDate.of(2026, 10, 19))));
        assertEquals("01/01/2026", ContractPaymentDAO.formatDate(Date.valueOf(LocalDate.of(2026, 1, 1))));
    }

    // ------------------------------------------------------------------
    // 2. findPaymentsByContractId
    // ------------------------------------------------------------------

    @Test
    public void findPaymentsByContractId_invalidId_returnsEmptyList() {
        assertTrue(dao.findPaymentsByContractId(0).isEmpty());
        assertTrue(dao.findPaymentsByContractId(-5).isEmpty());
    }

    @Test
    public void findPaymentsByContractId_success_mapsAllFieldsAndOrder() throws Exception {
        Date due1 = Date.valueOf("2026-03-01");
        Date paid1 = Date.valueOf("2026-02-28");
        Timestamp created1 = Timestamp.valueOf("2026-01-01 10:00:00");

        Date due2 = Date.valueOf("2026-06-01");
        Timestamp created2 = Timestamp.valueOf("2026-01-01 10:05:00");

        var row1 = row(
                "payment_id", 1,
                "contract_id", CONTRACT_ID,
                "invoice_amount", new BigDecimal("700000000.00"),
                "due_date", due1,
                "paid_date", paid1,
                "created_at", created1
        );
        var row2 = row(
                "payment_id", 2,
                "contract_id", CONTRACT_ID,
                "invoice_amount", new BigDecimal("300000000.00"),
                "due_date", due2,
                "paid_date", null,
                "created_at", created2
        );

        ResultSet rs = resultSetOf(List.of(row1, row2));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(rs);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractPayment> payments = dao.findPaymentsByContractId(CONTRACT_ID);
            assertEquals(2, payments.size());

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(conn).prepareStatement(sqlCaptor.capture());
            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("WHERE contract_id = ?"));
            assertTrue(sql.contains("ORDER BY due_date, payment_id"));
            verify(ps).setInt(1, CONTRACT_ID);

            ContractPayment p1 = payments.get(0);
            assertEquals(1, p1.getPaymentId());
            assertEquals(CONTRACT_ID, p1.getContractId());
            assertEquals(new BigDecimal("700000000.00"), p1.getInvoiceAmount());
            assertEquals(due1, p1.getDueDate());
            assertEquals(paid1, p1.getPaidDate());
            assertTrue(p1.isPaid());
            assertEquals(created1, p1.getCreatedAt());

            ContractPayment p2 = payments.get(1);
            assertEquals(2, p2.getPaymentId());
            assertEquals(new BigDecimal("300000000.00"), p2.getInvoiceAmount());
            assertEquals(due2, p2.getDueDate());
            assertNull(p2.getPaidDate());
            assertFalse(p2.isPaid());
        }
    }

    @Test
    public void findPaymentsByContractId_sqlException_returnsEmptyList() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenThrow(new SQLException("DB connection failure"));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractPayment> result = dao.findPaymentsByContractId(CONTRACT_ID);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ------------------------------------------------------------------
    // 3. insertPayment (UC-53, BR-18)
    // ------------------------------------------------------------------

    @Test
    public void insertPayment_validationFailures_returnsFalse() {
        // null payment
        assertFalse(dao.insertPayment(CONTRACT_ID, null, ACTOR_ID));

        // null amount
        ContractPayment p = new ContractPayment();
        p.setDueDate(Date.valueOf("2026-06-01"));
        assertFalse(dao.insertPayment(CONTRACT_ID, p, ACTOR_ID));

        // zero amount
        p.setInvoiceAmount(BigDecimal.ZERO);
        assertFalse(dao.insertPayment(CONTRACT_ID, p, ACTOR_ID));

        // negative amount
        p.setInvoiceAmount(new BigDecimal("-1000"));
        assertFalse(dao.insertPayment(CONTRACT_ID, p, ACTOR_ID));

        // null dueDate
        p.setInvoiceAmount(new BigDecimal("1000000"));
        p.setDueDate(null);
        assertFalse(dao.insertPayment(CONTRACT_ID, p, ACTOR_ID));

        // invalid contractId or actorId
        p.setDueDate(Date.valueOf("2026-06-01"));
        assertFalse(dao.insertPayment(0, p, ACTOR_ID));
        assertFalse(dao.insertPayment(-1, p, ACTOR_ID));
        assertFalse(dao.insertPayment(CONTRACT_ID, p, 0));
        assertFalse(dao.insertPayment(CONTRACT_ID, p, -1));
    }

    @Test
    public void insertPayment_contractFrozen_returnsFalseAndRollsBack() throws Exception {
        ContractPayment p = new ContractPayment();
        p.setInvoiceAmount(new BigDecimal("500000000"));
        p.setDueDate(Date.valueOf("2026-06-01"));

        // Case 1: Đã thanh lý
        PreparedStatement psStatusLiquidated = statementReturning(singleRow(row("progress_status", ContractDAO.PROGRESS_LIQUIDATED)));
        Connection connLiq = connectionReturning(psStatusLiquidated);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connLiq);

            assertFalse(dao.insertPayment(CONTRACT_ID, p, ACTOR_ID));
            verify(connLiq, never()).commit();
            verify(connLiq).rollback();
            verify(connLiq).setAutoCommit(true);
        }

        // Case 2: Chấm dứt sớm
        PreparedStatement psStatusTerminated = statementReturning(singleRow(row("progress_status", ContractDAO.PROGRESS_TERMINATED)));
        Connection connTerm = connectionReturning(psStatusTerminated);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connTerm);

            assertFalse(dao.insertPayment(CONTRACT_ID, p, ACTOR_ID));
            verify(connTerm, never()).commit();
            verify(connTerm).rollback();
            verify(connTerm).setAutoCommit(true);
        }
    }

    @Test
    public void insertPayment_success_writesHistoryAndCommits() throws Exception {
        ContractHistoryDAO historyMock = mock(ContractHistoryDAO.class);
        ContractPaymentDAO paymentDao = new ContractPaymentDAO(historyMock);

        ContractPayment p = new ContractPayment();
        p.setInvoiceAmount(new BigDecimal("500000000.00"));
        Date due = Date.valueOf("2026-06-15");
        p.setDueDate(due);
        p.setPaidDate(null);

        PreparedStatement psStatus = statementReturning(singleRow(row("progress_status", ContractDAO.PROGRESS_SIGNED)));
        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(1);

        Connection conn = connectionRoutingOn("INSERT INTO contract_payments", psInsert, psStatus);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean result = paymentDao.insertPayment(CONTRACT_ID, p, ACTOR_ID);
            assertTrue(result);

            verify(conn).setAutoCommit(false);
            verify(psInsert).setInt(1, CONTRACT_ID);
            verify(psInsert).setBigDecimal(2, p.getInvoiceAmount());
            verify(psInsert).setDate(3, due);
            verify(psInsert).setDate(4, null);
            verify(psInsert).executeUpdate();

            // Verify ContractHistory.EVENT_PAYMENT_ADDED được gọi
            ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
            verify(historyMock).insertHistory(eq(conn), eq(CONTRACT_ID), eq(ContractHistory.EVENT_PAYMENT_ADDED),
                    detailCaptor.capture(), eq(ACTOR_ID), isNull());
            assertTrue(detailCaptor.getValue().contains("500.000.000 đ"));
            assertTrue(detailCaptor.getValue().contains("15/06/2026"));
            assertFalse(detailCaptor.getValue().contains("đã thu"));

            verify(conn).commit();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void insertPayment_withPaidDate_detailIncludesPaidDate() throws Exception {
        ContractHistoryDAO historyMock = mock(ContractHistoryDAO.class);
        ContractPaymentDAO paymentDao = new ContractPaymentDAO(historyMock);

        ContractPayment p = new ContractPayment();
        p.setInvoiceAmount(new BigDecimal("200000000.00"));
        Date due = Date.valueOf("2026-05-01");
        Date paid = Date.valueOf("2026-04-28");
        p.setDueDate(due);
        p.setPaidDate(paid);

        PreparedStatement psStatus = statementReturning(singleRow(row("progress_status", ContractDAO.PROGRESS_DRAFT)));
        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(1);

        Connection conn = connectionRoutingOn("INSERT INTO contract_payments", psInsert, psStatus);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean result = paymentDao.insertPayment(CONTRACT_ID, p, ACTOR_ID);
            assertTrue(result);

            ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
            verify(historyMock).insertHistory(eq(conn), eq(CONTRACT_ID), eq(ContractHistory.EVENT_PAYMENT_ADDED),
                    detailCaptor.capture(), eq(ACTOR_ID), isNull());
            assertTrue(detailCaptor.getValue().contains("200.000.000 đ"));
            assertTrue(detailCaptor.getValue().contains("01/05/2026"));
            assertTrue(detailCaptor.getValue().contains("đã thu 28/04/2026"));
        }
    }

    @Test
    public void insertPayment_historyFails_rollsBackTransaction() throws Exception {
        ContractHistoryDAO historyMock = mock(ContractHistoryDAO.class);
        doThrow(new SQLException("History DB error"))
                .when(historyMock).insertHistory(any(), anyInt(), anyString(), anyString(), anyInt(), any());
        ContractPaymentDAO paymentDao = new ContractPaymentDAO(historyMock);

        ContractPayment p = new ContractPayment();
        p.setInvoiceAmount(new BigDecimal("100000000"));
        p.setDueDate(Date.valueOf("2026-05-01"));

        PreparedStatement psStatus = statementReturning(singleRow(row("progress_status", ContractDAO.PROGRESS_SIGNED)));
        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(1);

        Connection conn = connectionRoutingOn("INSERT INTO contract_payments", psInsert, psStatus);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean ok = paymentDao.insertPayment(CONTRACT_ID, p, ACTOR_ID);
            assertFalse(ok);

            verify(conn, never()).commit();
            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void insertPayment_executeUpdateZero_rollsBack() throws Exception {
        ContractPayment p = new ContractPayment();
        p.setInvoiceAmount(new BigDecimal("100000000"));
        p.setDueDate(Date.valueOf("2026-05-01"));

        PreparedStatement psStatus = statementReturning(singleRow(row("progress_status", ContractDAO.PROGRESS_SIGNED)));
        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(0);

        Connection conn = connectionRoutingOn("INSERT INTO contract_payments", psInsert, psStatus);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.insertPayment(CONTRACT_ID, p, ACTOR_ID));
            verify(conn, never()).commit();
            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    // ------------------------------------------------------------------
    // 4. markPaymentPaid (UC-53, BR-18)
    // ------------------------------------------------------------------

    @Test
    public void markPaymentPaid_validationFailures_returnsFalse() {
        Date paid = Date.valueOf("2026-05-10");

        assertFalse(dao.markPaymentPaid(PAYMENT_ID, CONTRACT_ID, null, ACTOR_ID));
        assertFalse(dao.markPaymentPaid(0, CONTRACT_ID, paid, ACTOR_ID));
        assertFalse(dao.markPaymentPaid(PAYMENT_ID, 0, paid, ACTOR_ID));
        assertFalse(dao.markPaymentPaid(PAYMENT_ID, CONTRACT_ID, paid, 0));
    }

    @Test
    public void markPaymentPaid_notFoundOrAlreadyPaid_returnsFalse() throws Exception {
        // Case 1: not found
        PreparedStatement psNotFound = statementReturning(emptyResultSet());
        Connection connNotFound = connectionReturning(psNotFound);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connNotFound);

            assertFalse(dao.markPaymentPaid(PAYMENT_ID, CONTRACT_ID, Date.valueOf("2026-05-10"), ACTOR_ID));
            verify(connNotFound, never()).commit();
            verify(connNotFound).rollback();
            verify(connNotFound).setAutoCommit(true);
        }

        // Case 2: already paid
        var alreadyPaidRow = row(
                "payment_id", PAYMENT_ID,
                "invoice_amount", new BigDecimal("500000000"),
                "due_date", Date.valueOf("2026-05-01"),
                "paid_date", Date.valueOf("2026-04-20")
        );
        PreparedStatement psAlreadyPaid = statementReturning(singleRow(alreadyPaidRow));
        Connection connAlreadyPaid = connectionReturning(psAlreadyPaid);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connAlreadyPaid);

            assertFalse(dao.markPaymentPaid(PAYMENT_ID, CONTRACT_ID, Date.valueOf("2026-05-10"), ACTOR_ID));
            verify(connAlreadyPaid, never()).commit();
            verify(connAlreadyPaid).rollback();
            verify(connAlreadyPaid).setAutoCommit(true);
        }
    }

    @Test
    public void markPaymentPaid_success_allowedEvenWhenContractLiquidated() throws Exception {
        ContractHistoryDAO historyMock = mock(ContractHistoryDAO.class);
        ContractPaymentDAO paymentDao = new ContractPaymentDAO(historyMock);

        Date paid = Date.valueOf("2026-05-15");
        var unpaidRow = row(
                "payment_id", PAYMENT_ID,
                "invoice_amount", new BigDecimal("450000000.00"),
                "due_date", Date.valueOf("2026-05-01"),
                "paid_date", null
        );

        PreparedStatement psLock = statementReturning(singleRow(unpaidRow));
        PreparedStatement psUpdate = mock(PreparedStatement.class);
        when(psUpdate.executeUpdate()).thenReturn(1);

        Connection conn = connectionRoutingOn("UPDATE contract_payments", psUpdate, psLock);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean result = paymentDao.markPaymentPaid(PAYMENT_ID, CONTRACT_ID, paid, ACTOR_ID);
            assertTrue(result);

            verify(conn).setAutoCommit(false);
            verify(psUpdate).setDate(1, paid);
            verify(psUpdate).setInt(2, PAYMENT_ID);
            verify(psUpdate).setInt(3, CONTRACT_ID);
            verify(psUpdate).executeUpdate();

            // Verify ContractHistory.EVENT_PAYMENT_PAID
            ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
            verify(historyMock).insertHistory(eq(conn), eq(CONTRACT_ID), eq(ContractHistory.EVENT_PAYMENT_PAID),
                    detailCaptor.capture(), eq(ACTOR_ID), isNull());
            assertTrue(detailCaptor.getValue().contains("450.000.000 đ"));
            assertTrue(detailCaptor.getValue().contains("01/05/2026"));
            assertTrue(detailCaptor.getValue().contains("15/05/2026"));

            verify(conn).commit();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void markPaymentPaid_updateFails_rollsBackTransaction() throws Exception {
        var unpaidRow = row(
                "payment_id", PAYMENT_ID,
                "invoice_amount", new BigDecimal("100000000"),
                "due_date", Date.valueOf("2026-05-01"),
                "paid_date", null
        );

        PreparedStatement psLock = statementReturning(singleRow(unpaidRow));
        PreparedStatement psUpdate = mock(PreparedStatement.class);
        when(psUpdate.executeUpdate()).thenReturn(0); // 0 rows updated

        Connection conn = connectionRoutingOn("UPDATE contract_payments", psUpdate, psLock);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean result = dao.markPaymentPaid(PAYMENT_ID, CONTRACT_ID, Date.valueOf("2026-05-15"), ACTOR_ID);
            assertFalse(result);

            verify(conn, never()).commit();
            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    // ------------------------------------------------------------------
    // 5. deletePayment (UC-53, BR-18)
    // ------------------------------------------------------------------

    @Test
    public void deletePayment_validationFailures_returnsFalse() {
        assertFalse(dao.deletePayment(0, CONTRACT_ID, ACTOR_ID));
        assertFalse(dao.deletePayment(PAYMENT_ID, 0, ACTOR_ID));
        assertFalse(dao.deletePayment(PAYMENT_ID, CONTRACT_ID, 0));
    }

    @Test
    public void deletePayment_contractFrozen_returnsFalseAndRollsBack() throws Exception {
        // Contract is liquidated -> blocked and rolled back
        PreparedStatement psStatusLiq = statementReturning(singleRow(row("progress_status", ContractDAO.PROGRESS_LIQUIDATED)));
        Connection connLiq = connectionReturning(psStatusLiq);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connLiq);

            assertFalse(dao.deletePayment(PAYMENT_ID, CONTRACT_ID, ACTOR_ID));
            verify(connLiq, never()).commit();
            verify(connLiq).rollback();
            verify(connLiq).setAutoCommit(true);
        }

        // Contract is terminated -> blocked and rolled back
        PreparedStatement psStatusTerm = statementReturning(singleRow(row("progress_status", ContractDAO.PROGRESS_TERMINATED)));
        Connection connTerm = connectionReturning(psStatusTerm);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(connTerm);

            assertFalse(dao.deletePayment(PAYMENT_ID, CONTRACT_ID, ACTOR_ID));
            verify(connTerm, never()).commit();
            verify(connTerm).rollback();
            verify(connTerm).setAutoCommit(true);
        }
    }

    @Test
    public void deletePayment_paymentNotFound_returnsFalseAndRollsBack() throws Exception {
        PreparedStatement psStatus = statementReturning(singleRow(row("progress_status", ContractDAO.PROGRESS_DRAFT)));
        PreparedStatement psLock = statementReturning(emptyResultSet());

        Connection conn = connectionRoutingOn("FOR UPDATE", psLock, psStatus);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.deletePayment(PAYMENT_ID, CONTRACT_ID, ACTOR_ID));
            verify(conn, never()).commit();
            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void deletePayment_success_deletesAndWritesHistory() throws Exception {
        ContractHistoryDAO historyMock = mock(ContractHistoryDAO.class);
        ContractPaymentDAO paymentDao = new ContractPaymentDAO(historyMock);

        var paymentRow = row(
                "payment_id", PAYMENT_ID,
                "invoice_amount", new BigDecimal("350000000.00"),
                "due_date", Date.valueOf("2026-08-01"),
                "paid_date", null
        );

        PreparedStatement psStatus = statementReturning(singleRow(row("progress_status", ContractDAO.PROGRESS_DRAFT)));
        PreparedStatement psLock = statementReturning(singleRow(paymentRow));
        PreparedStatement psDelete = mock(PreparedStatement.class);
        when(psDelete.executeUpdate()).thenReturn(1);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("DELETE FROM contract_payments")) {
                return psDelete;
            }
            if (sql.contains("FROM contract_payments") && sql.contains("FOR UPDATE")) {
                return psLock;
            }
            return psStatus;
        });

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean ok = paymentDao.deletePayment(PAYMENT_ID, CONTRACT_ID, ACTOR_ID);
            assertTrue(ok);

            verify(conn).setAutoCommit(false);
            verify(psDelete).setInt(1, PAYMENT_ID);
            verify(psDelete).setInt(2, CONTRACT_ID);
            verify(psDelete).executeUpdate();

            // Verify ContractHistory.EVENT_PAYMENT_REMOVED
            ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
            verify(historyMock).insertHistory(eq(conn), eq(CONTRACT_ID), eq(ContractHistory.EVENT_PAYMENT_REMOVED),
                    detailCaptor.capture(), eq(ACTOR_ID), isNull());
            assertTrue(detailCaptor.getValue().contains("350.000.000 đ"));
            assertTrue(detailCaptor.getValue().contains("01/08/2026"));

            verify(conn).commit();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void deletePayment_deleteFails_rollsBackTransaction() throws Exception {
        var paymentRow = row(
                "payment_id", PAYMENT_ID,
                "invoice_amount", new BigDecimal("100000000"),
                "due_date", Date.valueOf("2026-08-01"),
                "paid_date", null
        );

        PreparedStatement psStatus = statementReturning(singleRow(row("progress_status", ContractDAO.PROGRESS_SIGNED)));
        PreparedStatement psLock = statementReturning(singleRow(paymentRow));
        PreparedStatement psDelete = mock(PreparedStatement.class);
        when(psDelete.executeUpdate()).thenReturn(0);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0, String.class);
            if (sql.contains("DELETE FROM contract_payments")) {
                return psDelete;
            }
            if (sql.contains("FROM contract_payments") && sql.contains("FOR UPDATE")) {
                return psLock;
            }
            return psStatus;
        });

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean ok = dao.deletePayment(PAYMENT_ID, CONTRACT_ID, ACTOR_ID);
            assertFalse(ok);

            verify(conn, never()).commit();
            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    // ------------------------------------------------------------------
    // 6. sumScheduledPaymentsForCluster
    // ------------------------------------------------------------------

    @Test
    public void sumScheduledPaymentsForCluster_scenarios() throws Exception {
        assertEquals(BigDecimal.ZERO, dao.sumScheduledPaymentsForCluster(0));
        assertEquals(BigDecimal.ZERO, dao.sumScheduledPaymentsForCluster(-1));

        PreparedStatement ps = statementReturning(singleRow(row("total", new BigDecimal("1250000000"))));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(new BigDecimal("1250000000"), dao.sumScheduledPaymentsForCluster(CONTRACT_ID));
            verify(ps).setInt(1, CONTRACT_ID);
            verify(ps).setInt(2, CONTRACT_ID);

            // SQL Exception returns BigDecimal.ZERO
            when(ps.executeQuery()).thenThrow(new SQLException("DB dead"));
            assertEquals(BigDecimal.ZERO, dao.sumScheduledPaymentsForCluster(CONTRACT_ID));
        }
    }

    // ------------------------------------------------------------------
    // 7. sumInvoiceAmountInPeriod & sumInvoiceAmountByMonth (UC-36, BR-18)
    // ------------------------------------------------------------------

    @Test
    public void sumInvoiceAmountInPeriod_scenarios() throws Exception {
        assertEquals(BigDecimal.ZERO, dao.sumInvoiceAmountInPeriod(null, 1));
        assertEquals(BigDecimal.ZERO, dao.sumInvoiceAmountInPeriod(null, List.of(1), List.of(2)));

        Period period = Period.parse("2026", "m9");
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenAnswer(inv -> singleRow(row("total", new BigDecimal("650000000"))));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // 1. Kiểm tra overload (Period, Integer provinceId)
            BigDecimal sum1 = dao.sumInvoiceAmountInPeriod(period, 5);
            assertEquals(new BigDecimal("650000000"), sum1);

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(conn).prepareStatement(sqlCaptor.capture());
            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("p.paid_date IS NOT NULL AND p.paid_date BETWEEN ? AND ?"));
            assertTrue(sql.contains("AND c.direction = 'Bán'"));
            assertTrue(sql.contains("AND c.is_deleted = 0"));
            assertTrue(sql.contains("d.province_id IN (?)"));

            verify(ps).setDate(1, period.getFrom());
            verify(ps).setDate(2, period.getTo());
            verify(ps).setInt(3, 5);

            // 2. Kiểm tra overload (Period, List provinceIds, List ownerIds)
            BigDecimal sum2 = dao.sumInvoiceAmountInPeriod(period, List.of(5, 6), List.of(9));
            assertEquals(new BigDecimal("650000000"), sum2);

            ArgumentCaptor<String> sqlCaptor2 = ArgumentCaptor.forClass(String.class);
            verify(conn, atLeastOnce()).prepareStatement(sqlCaptor2.capture());
            String sql2 = sqlCaptor2.getValue();
            assertTrue(sql2.contains("p.paid_date IS NOT NULL AND p.paid_date BETWEEN ? AND ?"));
            assertTrue(sql2.contains("AND c.direction = 'Bán'"));
            assertTrue(sql2.contains("AND c.is_deleted = 0"));
            assertTrue(sql2.contains("d.province_id IN (?,?)"));
            assertTrue(sql2.contains("c.owner_id IN (?)"));

            verify(ps).setInt(3, 9);
            verify(ps).setInt(4, 5);
            verify(ps).setInt(5, 6);

            // Exception returns ZERO
            when(ps.executeQuery()).thenThrow(new SQLException("Query fail"));
            assertEquals(BigDecimal.ZERO, dao.sumInvoiceAmountInPeriod(period, 5));
        }
    }

    @Test
    public void sumInvoiceAmountByMonth_scenarios() throws Exception {
        // Kiểm tra an toàn đầu vào (năm/tháng không hợp lệ)
        assertEquals(BigDecimal.ZERO, dao.sumInvoiceAmountByMonth(0, 10));
        assertEquals(BigDecimal.ZERO, dao.sumInvoiceAmountByMonth(-1, 10));
        assertEquals(BigDecimal.ZERO, dao.sumInvoiceAmountByMonth(2026, 0));
        assertEquals(BigDecimal.ZERO, dao.sumInvoiceAmountByMonth(2026, 13));
        assertEquals(BigDecimal.ZERO, dao.sumInvoiceAmountByMonth(0, 10, 5));
        assertEquals(BigDecimal.ZERO, dao.sumInvoiceAmountByMonth(2026, -1, List.of(5), List.of(9)));

        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenAnswer(inv -> singleRow(row("total", new BigDecimal("800000000"))));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // 1. Overload: (int year, int month)
            assertEquals(new BigDecimal("800000000"), dao.sumInvoiceAmountByMonth(2026, 10));
            verify(ps).setInt(1, 2026);
            verify(ps).setInt(2, 10);

            // 2. Overload: (int year, int month, Integer provinceId)
            assertEquals(new BigDecimal("800000000"), dao.sumInvoiceAmountByMonth(2026, 10, 3));
            verify(ps, atLeastOnce()).setInt(3, 3);

            // 3. Overload: (int year, int month, List provinceIds, List ownerIds)
            assertEquals(new BigDecimal("800000000"), dao.sumInvoiceAmountByMonth(2026, 10, List.of(3, 4), List.of(9)));

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(conn, atLeastOnce()).prepareStatement(sqlCaptor.capture());
            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("YEAR(p.paid_date) = ? AND MONTH(p.paid_date) = ?"));
            assertTrue(sql.contains("AND c.direction = 'Bán'"));
            assertTrue(sql.contains("AND c.is_deleted = 0"));
            assertTrue(sql.contains("d.province_id IN (?,?)"));
            assertTrue(sql.contains("c.owner_id IN (?)"));

            verify(ps).setInt(3, 9);
            verify(ps).setInt(4, 3);
            verify(ps).setInt(5, 4);

            // 4. SQL Exception returns ZERO
            when(ps.executeQuery()).thenThrow(new SQLException("Query fail"));
            assertEquals(BigDecimal.ZERO, dao.sumInvoiceAmountByMonth(2026, 10));
        }
    }

    // ------------------------------------------------------------------
    // 8. ContractDAO delegation
    // ------------------------------------------------------------------

    @Test
    public void contractDAO_delegatesAllPaymentOperations() {
        ContractDAO defaultDao = new ContractDAO();
        assertNotNull(defaultDao.getPaymentDAO());
        assertSame(defaultDao.getHistoryDAO(), defaultDao.getPaymentDAO().getHistoryDAO());

        ContractPaymentDAO mockPayment = mock(ContractPaymentDAO.class);
        ContractDAO injectedDao = new ContractDAO(mockPayment);
        assertSame(mockPayment, injectedDao.getPaymentDAO());

        // findPaymentsByContractId
        injectedDao.findPaymentsByContractId(CONTRACT_ID);
        verify(mockPayment).findPaymentsByContractId(CONTRACT_ID);

        // insertPayment
        ContractPayment p = new ContractPayment();
        injectedDao.insertPayment(CONTRACT_ID, p, ACTOR_ID);
        verify(mockPayment).insertPayment(CONTRACT_ID, p, ACTOR_ID);

        // markPaymentPaid
        Date paid = Date.valueOf("2026-05-15");
        injectedDao.markPaymentPaid(PAYMENT_ID, CONTRACT_ID, paid, ACTOR_ID);
        verify(mockPayment).markPaymentPaid(PAYMENT_ID, CONTRACT_ID, paid, ACTOR_ID);

        // deletePayment
        injectedDao.deletePayment(PAYMENT_ID, CONTRACT_ID, ACTOR_ID);
        verify(mockPayment).deletePayment(PAYMENT_ID, CONTRACT_ID, ACTOR_ID);

        // sumScheduledPaymentsForCluster
        injectedDao.sumScheduledPaymentsForCluster(CONTRACT_ID);
        verify(mockPayment).sumScheduledPaymentsForCluster(CONTRACT_ID);

        // sumInvoiceAmountInPeriod
        Period period = Period.parse("2026", "m9");
        injectedDao.sumInvoiceAmountInPeriod(period, 5);
        verify(mockPayment).sumInvoiceAmountInPeriod(period, 5);
        injectedDao.sumInvoiceAmountInPeriod(period, List.of(5), List.of(9));
        verify(mockPayment).sumInvoiceAmountInPeriod(period, List.of(5), List.of(9));

        // sumInvoiceAmountByMonth
        injectedDao.sumInvoiceAmountByMonth(2026, 10);
        verify(mockPayment).sumInvoiceAmountByMonth(2026, 10);
        injectedDao.sumInvoiceAmountByMonth(2026, 10, 5);
        verify(mockPayment).sumInvoiceAmountByMonth(2026, 10, 5);
        injectedDao.sumInvoiceAmountByMonth(2026, 10, List.of(5), List.of(9));
        verify(mockPayment).sumInvoiceAmountByMonth(2026, 10, List.of(5), List.of(9));
    }
}
