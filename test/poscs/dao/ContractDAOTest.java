package poscs.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
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

    // ------------------------------------------------------------------
    // Lọc/sắp xếp theo tỉnh (hợp đồng lấy địa bàn từ khách hàng đứng tên)
    // ------------------------------------------------------------------

    private static String capturedSql(Connection conn) throws SQLException {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareStatement(sql.capture());
        return sql.getValue();
    }

    @Test
    public void findAll_withProvinceFilter_joinsFromEnterpriseToProvince() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null, 3, false);

            // contracts không có cột tỉnh: phải đi qua khách hàng -> địa chỉ ->
            // xã/phường mới tới được province_id.
            String sql = capturedSql(conn);
            assertTrue(sql.contains("LEFT JOIN addresses a ON e.address_id = a.address_id"));
            assertTrue(sql.contains("d.province_id = ?"));
            verify(ps).setObject(1, 3);
        }
    }

    /** Xem ghi chú cùng tên ở CustomerDAOTest: lệch join là bộ đếm trả 0 trong im lặng. */
    @Test
    public void countAll_withProvinceFilter_joinsTablesTheFilterNeeds() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("total", 7)));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(7, dao.countAll(null, null, null, 3));

            String sql = capturedSql(conn);
            assertTrue(sql.contains("LEFT JOIN districts d"));
            assertTrue(sql.contains("d.province_id = ?"));
        }
    }

    @Test
    public void findAll_sortByProvince_ordersByProvinceInsteadOfNewestFirst() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            dao.findAll(1, 10, null, null, null, null, true);

            assertTrue(capturedSql(conn).contains("ORDER BY p.province_name IS NULL"));
        }
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

    // ==================================================================
    // Kỳ thanh toán -- gộp từ ContractPaymentDAOTest
    // ==================================================================
    //
    // Doanh thu tính trên paid_date (tiền đã thực thu), phục vụ biểu đồ cột ở
    // dashboard.
    //
    // Trọng tâm là sumInvoiceAmountLastNMonths: hàm này không chỉ đọc CSDL mà
    // còn tự dựng sẵn khung N tháng liên tiếp với giá trị 0, rồi mới đổ dữ
    // liệu đọc được lên trên. Đó là phần logic Java thật sự -- tháng không có
    // khoản thu nào vẫn phải xuất hiện với giá trị 0 (không thì biểu đồ nhảy
    // cóc, mất cột), và dòng nằm ngoài cửa sổ N tháng phải bị bỏ qua thay vì
    // chèn thêm cột lạ.

    // ------------------------------------------------------------------
    // sumInvoiceAmountByMonth
    // ------------------------------------------------------------------

    @Test
    public void sumInvoiceAmountByMonth_returnsSumAndBindsYearThenMonth() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("total", new BigDecimal("1500000"))));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(new BigDecimal("1500000"), dao.sumInvoiceAmountByMonth(2026, 9));
            verify(ps).setInt(1, 2026);
            verify(ps).setInt(2, 9);
        }
    }

    @Test
    public void sumInvoiceAmountByMonth_sqlError_returnsZeroNotNull() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            // Dashboard chia cho giá trị này khi tính % tăng trưởng -- null sẽ nổ.
            assertEquals(BigDecimal.ZERO, dao.sumInvoiceAmountByMonth(2026, 9));
        }
    }

    // ------------------------------------------------------------------
    // sumInvoiceAmountLastNMonths -- khung N tháng liên tiếp
    // ------------------------------------------------------------------

    @Test
    public void sumInvoiceAmountLastNMonths_emptyTable_stillReturnsNConsecutiveMonthsOfZero() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Map<String, BigDecimal> result = dao.sumInvoiceAmountLastNMonths(6);

            assertEquals(6, result.size());
            YearMonth end = YearMonth.now();
            for (int i = 0; i < 6; i++) {
                String key = end.minusMonths(5L - i).toString();
                assertTrue("Thiếu tháng " + key, result.containsKey(key));
                assertEquals("Tháng không có khoản thu phải là 0, không phải bị bỏ trống",
                        BigDecimal.ZERO, result.get(key));
            }
        }
    }

    @Test
    public void sumInvoiceAmountLastNMonths_keepsChronologicalOrderEndingAtCurrentMonth() throws Exception {
        PreparedStatement ps = statementReturning(emptyResultSet());
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Map<String, BigDecimal> result = dao.sumInvoiceAmountLastNMonths(3);

            // Biểu đồ cột vẽ theo đúng thứ tự duyệt map, nên thứ tự chèn phải là
            // cũ -> mới và kết thúc ở tháng hiện tại.
            List<String> keys = new ArrayList<>(result.keySet());
            YearMonth end = YearMonth.now();
            assertEquals(end.minusMonths(2).toString(), keys.get(0));
            assertEquals(end.minusMonths(1).toString(), keys.get(1));
            assertEquals(end.toString(), keys.get(2));
        }
    }

    @Test
    public void sumInvoiceAmountLastNMonths_fillsOnlyMonthsPresentInResultSet() throws Exception {
        YearMonth current = YearMonth.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("y", current.getYear(), "m", current.getMonthValue(),
                "total", new BigDecimal("900000")));
        PreparedStatement ps = statementReturning(resultSetOf(rows));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Map<String, BigDecimal> result = dao.sumInvoiceAmountLastNMonths(3);

            assertEquals(new BigDecimal("900000"), result.get(current.toString()));
            assertEquals(BigDecimal.ZERO, result.get(current.minusMonths(1).toString()));
            assertEquals(BigDecimal.ZERO, result.get(current.minusMonths(2).toString()));
        }
    }

    @Test
    public void sumInvoiceAmountLastNMonths_rowOutsideWindow_isIgnoredNotAdded() throws Exception {
        YearMonth outside = YearMonth.now().minusMonths(11);
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("y", outside.getYear(), "m", outside.getMonthValue(),
                "total", new BigDecimal("777")));
        PreparedStatement ps = statementReturning(resultSetOf(rows));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            Map<String, BigDecimal> result = dao.sumInvoiceAmountLastNMonths(3);

            // Cửa sổ vẫn đúng 3 tháng -- dòng ngoài khung không được thêm cột lạ
            // vào biểu đồ.
            assertEquals(3, result.size());
            assertFalse(result.containsKey(outside.toString()));
        }
    }

    @Test
    public void sumInvoiceAmountLastNMonths_sqlError_stillReturnsFullZeroFilledWindow() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            Map<String, BigDecimal> result = dao.sumInvoiceAmountLastNMonths(4);

            // Khung tháng được dựng trước khi chạm CSDL, nên lỗi đọc vẫn cho ra
            // biểu đồ 4 cột rỗng thay vì map trống làm vỡ trang dashboard.
            assertEquals(4, result.size());
            for (BigDecimal value : result.values()) {
                assertEquals(BigDecimal.ZERO, value);
            }
        }
    }

    // ------------------------------------------------------------------
    // sumInvoiceAmountByContractId
    // ------------------------------------------------------------------

    @Test
    public void sumInvoiceAmountByContractId_returnsSum() throws Exception {
        PreparedStatement ps = statementReturning(singleRow(row("total", new BigDecimal("42000"))));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            assertEquals(new BigDecimal("42000"), dao.sumInvoiceAmountByContractId(11));
            verify(ps).setInt(1, 11);
        }
    }

    @Test
    public void sumInvoiceAmountByContractId_sqlError_returnsZero() throws Exception {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenThrow(new SQLException("hỏng"));

            assertEquals(BigDecimal.ZERO, dao.sumInvoiceAmountByContractId(11));
        }
    }
}
