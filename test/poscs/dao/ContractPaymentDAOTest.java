package poscs.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Test cho ContractPaymentDAO -- doanh thu tính trên paid_date (tiền đã thực
 * thu), phục vụ biểu đồ cột ở dashboard.
 *
 * Trọng tâm là {@code sumInvoiceAmountLastNMonths}: hàm này không chỉ đọc CSDL
 * mà còn tự dựng sẵn khung N tháng liên tiếp với giá trị 0, rồi mới đổ dữ liệu
 * đọc được lên trên. Đó là phần logic Java thật sự -- tháng không có khoản thu
 * nào vẫn phải xuất hiện với giá trị 0 (không thì biểu đồ nhảy cóc, mất cột), và
 * dòng nằm ngoài cửa sổ N tháng phải bị bỏ qua thay vì chèn thêm cột lạ.
 *
 * Xem {@link JdbcStub} để biết những test này KHÔNG kiểm chứng được gì (SQL,
 * tên cột, schema). JUnit 4 -- xem CustomerControllerTest.
 */
public class ContractPaymentDAOTest {

    private final ContractPaymentDAO dao = new ContractPaymentDAO();

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
