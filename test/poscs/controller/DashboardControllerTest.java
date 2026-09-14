package poscs.controller;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.ContractDAO;
import poscs.dao.ContractPaymentDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Contract;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cho DashboardController -- tổng hợp số liệu trang tổng quan. Phần lớn
 * là truyền dữ liệu thẳng từ DAO (ít giá trị test), nên tập trung vào phần
 * TÍNH TOÁN thật trong controller: % tăng trưởng doanh thu so tháng trước
 * (kể cả trường hợp chia cho 0 khi tháng trước chưa có doanh thu), và số
 * ngày còn lại tới hạn của từng hợp đồng sắp hết hạn.
 */
public class DashboardControllerTest {

    private DashboardController controller;
    private CustomerDAO customerDAO;
    private ContractDAO contractDAO;
    private TechnicalSupportTicketDAO ticketDAO;
    private ContractPaymentDAO paymentDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;

    @Before
    public void setUp() throws Exception {
        controller = new DashboardController();
        customerDAO = mock(CustomerDAO.class);
        contractDAO = mock(ContractDAO.class);
        ticketDAO = mock(TechnicalSupportTicketDAO.class);
        paymentDAO = mock(ContractPaymentDAO.class);
        setField(controller, "customerDAO", customerDAO);
        setField(controller, "contractDAO", contractDAO);
        setField(controller, "ticketDAO", ticketDAO);
        setField(controller, "paymentDAO", paymentDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        // Stub chung cho mọi test -- không phải trọng tâm nhưng bắt buộc để
        // tránh NPE khi controller đọc qua các map/list này.
        when(contractDAO.countStatusSummary(nullable(Integer.class))).thenReturn(Collections.emptyMap());
        when(ticketDAO.countStatusSummary(nullable(Integer.class))).thenReturn(Collections.emptyMap());
        when(contractDAO.findExpiringSoon(anyInt(), nullable(Integer.class))).thenReturn(Collections.emptyList());
        when(ticketDAO.findNeedingAttention(anyInt(), nullable(Integer.class))).thenReturn(Collections.emptyList());
        // doGet luôn forward /dashboard.jsp ở cuối -- không stub thì
        // getRequestDispatcher trả null và .forward() ném NPE ở MỌI test.
        RequestDispatcher defaultDispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher(anyString())).thenReturn(defaultDispatcher);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void revenueGrewFromLastMonth_computesPositiveTrendPercent() throws Exception {
        LocalDate today = LocalDate.now();
        when(paymentDAO.sumInvoiceAmountByMonth(eq(today.getYear()), eq(today.getMonthValue()), nullable(Integer.class)))
                .thenReturn(BigDecimal.valueOf(1_500_000));
        LocalDate lastMonth = today.minusMonths(1);
        when(paymentDAO.sumInvoiceAmountByMonth(eq(lastMonth.getYear()), eq(lastMonth.getMonthValue()), nullable(Integer.class)))
                .thenReturn(BigDecimal.valueOf(1_000_000));

        controller.doGet(request, response);

        // (1.500.000 - 1.000.000) / 1.000.000 * 100 = 50.0%
        verify(request).setAttribute("revenueTrendPercent", 50.0);
    }

    @Test
    public void noRevenueLastMonth_skipsTrendPercentToAvoidDivisionByZero() throws Exception {
        LocalDate today = LocalDate.now();
        when(paymentDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), nullable(Integer.class))).thenReturn(BigDecimal.ZERO);

        controller.doGet(request, response);

        verify(request, never()).setAttribute(eq("revenueTrendPercent"), any());
    }

    @Test
    public void expiringContracts_computesDaysRemainingAndValuePerContract() throws Exception {
        LocalDate today = LocalDate.now();
        Contract c = new Contract();
        c.setContractId(9);
        c.setEndDate(Date.valueOf(today.plusDays(10)));
        List<Contract> expiring = Arrays.asList(c);
        when(contractDAO.findExpiringSoon(anyInt(), nullable(Integer.class))).thenReturn(expiring);
        when(paymentDAO.sumInvoiceAmountByContractId(9)).thenReturn(BigDecimal.valueOf(5_000_000));
        when(paymentDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), nullable(Integer.class))).thenReturn(BigDecimal.ZERO);

        controller.doGet(request, response);

        verify(request).setAttribute(eq("expiringContracts"), eq(expiring));
        verify(request).setAttribute(eq("contractValues"),
                argThat((Map<Integer, BigDecimal> m) -> BigDecimal.valueOf(5_000_000).equals(m.get(9))));
        verify(request).setAttribute(eq("daysRemaining"),
                argThat((Map<Integer, Long> m) -> Long.valueOf(10L).equals(m.get(9))));
    }

    @Test
    public void alwaysForwardsToDashboardJsp() throws Exception {
        when(paymentDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), nullable(Integer.class))).thenReturn(BigDecimal.ZERO);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/dashboard.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(dispatcher).forward(request, response);
    }

    // ------------------------------------------------------------------
    // Lọc theo tỉnh
    // ------------------------------------------------------------------

    /**
     * Lọc tỉnh phải xuống TỚI MỌI truy vấn của trang. Bỏ sót một chỗ là trang
     * trộn hai phạm vi: ô KPI đếm cả nước nằm ngay cạnh bảng chỉ có dữ liệu
     * một tỉnh -- sai lệch kiểu đó người dùng không có cách nào tự nhận ra.
     */
    @Test
    public void provinceSelected_narrowsEveryQueryOnThePage() throws Exception {
        when(request.getParameter("provinceId")).thenReturn("3");
        when(customerDAO.countAll(any(), any(), any(), eq(3))).thenReturn(2);
        when(paymentDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), eq(3))).thenReturn(BigDecimal.ZERO);
        when(contractDAO.countStatusSummary(3)).thenReturn(Collections.emptyMap());
        when(ticketDAO.countStatusSummary(3)).thenReturn(Collections.emptyMap());
        when(contractDAO.findExpiringSoon(anyInt(), eq(3))).thenReturn(Collections.emptyList());
        when(ticketDAO.findNeedingAttention(anyInt(), eq(3))).thenReturn(Collections.emptyList());

        controller.doGet(request, response);

        verify(customerDAO).countAll(any(), any(), any(), eq(3));
        verify(customerDAO).countNewThisMonth(3);
        verify(contractDAO).countStatusSummary(3);
        verify(contractDAO).findExpiringSoon(anyInt(), eq(3));
        verify(ticketDAO).countStatusSummary(3);
        verify(ticketDAO).countOverdueOrDueSoon(3);
        verify(ticketDAO).findNeedingAttention(anyInt(), eq(3));
        verify(paymentDAO, times(2)).sumInvoiceAmountByMonth(anyInt(), anyInt(), eq(3));
        verify(request).setAttribute("provinceFilter", 3);
    }

    /** Tham số rác trên URL không được làm trang vỡ -- coi như không lọc. */
    @Test
    public void invalidProvinceParam_fallsBackToNationwide() throws Exception {
        when(request.getParameter("provinceId")).thenReturn("khong-phai-so");
        when(paymentDAO.sumInvoiceAmountByMonth(anyInt(), anyInt(), nullable(Integer.class))).thenReturn(BigDecimal.ZERO);

        controller.doGet(request, response);

        verify(contractDAO).countStatusSummary(null);
        verify(request).setAttribute("provinceFilter", null);
    }
}
