package poscs.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import poscs.model.Contract;
import poscs.model.ContractProduct;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Test cho ContractDAO. Trọng tâm là hai chỗ có logic thật sự chứ không chỉ
 * đọc-ghi thẳng:
 *
 * <ul>
 *   <li>{@code insertProducts} tự quản transaction (tắt autocommit, commit hoặc
 *       rollback cả batch). Bên gọi -- ContractController.handleImportPdf --
 *       dựa vào giá trị false để báo với người dùng "chưa ghi gì vào CSDL", nên
 *       nếu rollback không chạy thì thông báo đó thành nói dối, hợp đồng còn
 *       lại một nửa số hạng mục.</li>
 *   <li>{@code insert} thử lại khi contract_code bị trùng UNIQUE KEY (hai
 *       request tạo hợp đồng gần đồng thời đọc được cùng một "mã lớn nhất").</li>
 * </ul>
 *
 * Ép lỗi đúng thời điểm (batch hỏng giữa chừng, commit hỏng, setAutoCommit hỏng
 * sau khi đã commit) là thứ gần như không dựng lại được trên CSDL thật -- đây
 * chính là chỗ mock JDBC làm được việc mà integration test không làm nổi.
 * Ngược lại, xem {@link JdbcStub} để biết những test này KHÔNG kiểm chứng gì.
 * JUnit 4 -- xem CustomerControllerTest.
 */
public class ContractDAOTest {

    private final ContractDAO dao = new ContractDAO();

    private static ContractProduct item(int productId, int quantity) {
        ContractProduct p = new ContractProduct();
        p.setProductId(productId);
        p.setQuantity(quantity);
        p.setUnit("cái");
        p.setNotes(null);
        return p;
    }

    private static Contract contract(String code) {
        Contract c = new Contract();
        c.setContractCode(code);
        c.setTitle("Hợp đồng thử");
        c.setContractType("Cung cấp thiết bị");
        c.setSigningDate(Date.valueOf(LocalDate.now()));
        c.setEffectiveDate(Date.valueOf(LocalDate.now().minusDays(1)));
        c.setEndDate(Date.valueOf(LocalDate.now().plusYears(1)));
        c.setEnterpriseId(3);
        c.setOwnerId(5);
        return c;
    }

    // ------------------------------------------------------------------
    // insertProducts -- "tất cả hoặc không gì cả"
    // ------------------------------------------------------------------

    @Test
    public void insertProducts_emptyList_returnsTrueWithoutTouchingDatabase() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            assertTrue(dao.insertProducts(1, new ArrayList<>()));

            // Hợp đồng không có hạng mục nào là hợp lệ -- không được mở kết nối
            // chỉ để chạy một batch rỗng.
            db.verify(DBContext::getConnection, never());
        }
    }

    @Test
    public void insertProducts_allRowsSucceed_commitsAndRestoresAutoCommit() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertTrue(dao.insertProducts(1, Arrays.asList(item(10, 2), item(11, 5))));

            InOrder inOrder = inOrder(conn, ps);
            inOrder.verify(conn).setAutoCommit(false);
            inOrder.verify(ps).executeBatch();
            inOrder.verify(conn).commit();
            inOrder.verify(conn).setAutoCommit(true);
            verify(ps, times(2)).addBatch();
            verify(conn, never()).rollback();
        }
    }

    @Test
    public void insertProducts_batchFails_rollsBackAndReturnsFalse() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeBatch()).thenThrow(new SQLException("khoá ngoại product_id không tồn tại"));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.insertProducts(1, Arrays.asList(item(10, 2), item(11, 5))));

            // Không rollback thì các dòng trước chỗ hỏng vẫn nằm lại trong CSDL,
            // trong khi hàm báo false -- hợp đồng có một nửa hạng mục.
            verify(conn).rollback();
            verify(conn, never()).commit();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void insertProducts_commitFails_rollsBackAndReturnsFalse() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection conn = connectionReturning(ps);
        doThrow(new SQLException("mất kết nối lúc commit")).when(conn).commit();

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertFalse(dao.insertProducts(1, Arrays.asList(item(10, 2))));

            verify(conn).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void insertProducts_autoCommitResetFailsAfterCommit_stillReportsSuccess() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection conn = connectionReturning(ps);
        // commit() đã thành công, chỉ khâu dọn dẹp sau đó hỏng.
        doThrow(new SQLException("kết nối chết khi trả autocommit")).when(conn).setAutoCommit(true);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Dữ liệu đã nằm trong CSDL rồi. Báo false ở đây là nói dối theo
            // hướng nguy hiểm: bên gọi sẽ bảo người dùng "chưa ghi gì" và họ
            // nhập lại, tạo ra hạng mục trùng.
            assertTrue("Lỗi dọn dẹp sau commit không được biến thành thất bại",
                    dao.insertProducts(1, Arrays.asList(item(10, 2))));
            verify(conn, never()).rollback();
        }
    }

    @Test
    public void insertProducts_rollbackAlsoFails_stillReturnsFalseWithoutThrowing() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeBatch()).thenThrow(new SQLException("batch hỏng"));
        Connection conn = connectionReturning(ps);
        doThrow(new SQLException("rollback cũng hỏng")).when(conn).rollback();

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            // Lỗi chồng lỗi vẫn phải thoát ra bằng giá trị trả về, không được
            // ném ngoại lệ lên tận servlet thành trang 500.
            assertFalse(dao.insertProducts(1, Arrays.asList(item(10, 2))));
        }
    }

    @Test
    public void insertProducts_bindsEveryItemOntoTheSameStatement() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.insertProducts(77, Arrays.asList(item(10, 2), item(11, 5)));

            verify(ps, times(2)).setInt(1, 77); // contract_id lặp lại cho từng dòng
            verify(ps).setInt(2, 10);
            verify(ps).setInt(3, 2);
            verify(ps).setInt(2, 11);
            verify(ps).setInt(3, 5);
        }
    }

    // ------------------------------------------------------------------
    // insert -- thử lại khi trùng contract_code
    // ------------------------------------------------------------------

    @Test
    public void insert_returnsGeneratedContractId() throws Exception {
        // singleRow() tự chạy vài lệnh when() bên trong, nên phải lấy ra biến
        // trước; gọi lồng trong when(...) sẽ ném UnfinishedStubbingException.
        ResultSet keys = singleRow(row("id", 123));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(123, dao.insert(contract("HD-0001")));
            verify(conn).prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS));
        }
    }

    @Test
    public void insert_noRowAffected_returnsMinusOne() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(0);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(-1, dao.insert(contract("HD-0001")));
        }
    }

    @Test
    public void insert_duplicateContractCode_regeneratesCodeAndRetries() throws Exception {
        ResultSet keys = singleRow(row("id", 55));
        PreparedStatement insertPs = mock(PreparedStatement.class);
        when(insertPs.executeUpdate())
                .thenThrow(duplicateKeyError("contract_code")) // lần 1: đụng UNIQUE KEY
                .thenReturn(1);                                // lần 2: mã mới, thành công
        when(insertPs.getGeneratedKeys()).thenReturn(keys);

        // generateNextContractCode() chạy xen giữa 2 lần thử, đọc mã lớn nhất hiện có.
        PreparedStatement codePs = statementReturning(singleRow(row("contract_code", "HD-0007")));

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString(), anyInt())).thenReturn(insertPs);
        when(conn.prepareStatement(anyString())).thenReturn(codePs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Contract c = contract("HD-0007");
            assertEquals(55, dao.insert(c));

            // Mã phải được sinh lại chứ không thử lại y nguyên mã cũ -- lặp lại
            // cùng một mã sẽ trùng mãi cho tới khi hết lượt thử.
            assertEquals("HD-0008", c.getContractCode());
            verify(insertPs, times(2)).executeUpdate();
        }
    }

    @Test
    public void insert_nonDuplicateSqlError_failsImmediatelyWithoutRetrying() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenThrow(new SQLException("khoá ngoại enterprise_id sai", "23000", 1452));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(-1, dao.insert(contract("HD-0001")));

            // Sinh lại mã không cứu được lỗi này -- thử lại 5 lần chỉ tổ chậm.
            verify(ps, times(1)).executeUpdate();
        }
    }

    @Test
    public void insert_duplicateCodeEveryAttempt_givesUpAfterMaxAttempts() throws Exception {
        PreparedStatement insertPs = mock(PreparedStatement.class);
        when(insertPs.executeUpdate()).thenThrow(duplicateKeyError("contract_code"));
        PreparedStatement codePs = statementReturning(singleRow(row("contract_code", "HD-0007")));

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString(), anyInt())).thenReturn(insertPs);
        when(conn.prepareStatement(anyString())).thenReturn(codePs);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(-1, dao.insert(contract("HD-0007")));

            // Vòng lặp phải dừng, không quay vô hạn khi mã mới vẫn cứ trùng.
            verify(insertPs, times(5)).executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // computeStatus -- private, kiểm gián tiếp qua tham số bind của insert()
    // ------------------------------------------------------------------

    @Test
    public void insert_contractNotYetEffective_storesDraftStatus() throws Exception {
        PreparedStatement ps = captureStatusFor(
                Date.valueOf(LocalDate.now().plusDays(10)),
                Date.valueOf(LocalDate.now().plusYears(1)));

        verify(ps).setString(10, ContractDAO.STATUS_DRAFT);
    }

    @Test
    public void insert_contractPastEndDate_storesExpiredStatus() throws Exception {
        PreparedStatement ps = captureStatusFor(
                Date.valueOf(LocalDate.now().minusYears(2)),
                Date.valueOf(LocalDate.now().minusDays(1)));

        verify(ps).setString(10, ContractDAO.STATUS_EXPIRED);
    }

    @Test
    public void insert_contractEndingWithin30Days_storesExpiringSoonStatus() throws Exception {
        PreparedStatement ps = captureStatusFor(
                Date.valueOf(LocalDate.now().minusMonths(6)),
                Date.valueOf(LocalDate.now().plusDays(10)));

        verify(ps).setString(10, ContractDAO.STATUS_SOON);
    }

    @Test
    public void insert_contractEndingWellBeyond30Days_storesActiveStatus() throws Exception {
        PreparedStatement ps = captureStatusFor(
                Date.valueOf(LocalDate.now().minusMonths(6)),
                Date.valueOf(LocalDate.now().plusDays(31)));

        verify(ps).setString(10, ContractDAO.STATUS_ACTIVE);
    }

    @Test
    public void insert_missingDates_fallsBackToDraftStatus() throws Exception {
        PreparedStatement ps = captureStatusFor(null, null);

        verify(ps).setString(10, ContractDAO.STATUS_DRAFT);
    }

    /** Chạy insert() với cặp ngày cho trước rồi trả về statement để soi tham số đã bind. */
    private PreparedStatement captureStatusFor(Date effectiveDate, Date endDate) throws Exception {
        ResultSet keys = singleRow(row("id", 1));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeUpdate()).thenReturn(1);
        when(ps.getGeneratedKeys()).thenReturn(keys);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Contract c = contract("HD-0001");
            c.setEffectiveDate(effectiveDate);
            c.setEndDate(endDate);
            dao.insert(c);
        }
        return ps;
    }
}
