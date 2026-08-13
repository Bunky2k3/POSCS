package poscs.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.dao.ContractDAO;
import poscs.dao.ContractPaymentDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Contract;
import poscs.model.TechnicalRequest;

/**
 * Tổng hợp số liệu cho trang tổng quan (dashboard.jsp). Hiển thị số liệu
 * toàn hệ thống, không lọc theo phạm vi người dùng đăng nhập -- không có
 * tiền lệ nào trong CustomerController/ContractController/
 * TechnicalSupportTicketController lọc theo owner hiện tại, và
 * PERMISSIONS.md cũng không quy định việc này.
 */
@WebServlet(name = "DashboardController", urlPatterns = {"/dashboard"})
public class DashboardController extends HttpServlet {

    private static final int EXPIRING_CONTRACTS_LIMIT = 5;
    private static final int ATTENTION_TICKETS_LIMIT = 5;
    private static final int REVENUE_CHART_MONTHS = 6;
    private static final String[] WEEKDAY_VI = {
        "Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"
    };

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final ContractDAO contractDAO = new ContractDAO();
    private final TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();
    private final ContractPaymentDAO paymentDAO = new ContractPaymentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        LocalDate today = LocalDate.now();

        // ===== KPI: khách hàng =====
        request.setAttribute("totalCustomers", customerDAO.countAll(null, null, null));
        request.setAttribute("newCustomersThisMonth", customerDAO.countNewThisMonth());

        // ===== KPI: hợp đồng =====
        Map<String, Integer> contractStatusSummary = contractDAO.countStatusSummary();
        request.setAttribute("contractStatusSummary", contractStatusSummary);

        // ===== KPI: doanh thu =====
        BigDecimal revenueThisMonth = paymentDAO.sumInvoiceAmountByMonth(today.getYear(), today.getMonthValue());
        LocalDate lastMonth = today.minusMonths(1);
        BigDecimal revenueLastMonth = paymentDAO.sumInvoiceAmountByMonth(lastMonth.getYear(), lastMonth.getMonthValue());
        request.setAttribute("revenueThisMonth", revenueThisMonth);
        if (revenueLastMonth.compareTo(BigDecimal.ZERO) > 0) {
            double trendPercent = revenueThisMonth.subtract(revenueLastMonth)
                    .divide(revenueLastMonth, 4, java.math.RoundingMode.HALF_UP)
                    .doubleValue() * 100;
            request.setAttribute("revenueTrendPercent", Math.round(trendPercent * 10) / 10.0);
        }

        // ===== KPI: phiếu hỗ trợ =====
        Map<String, Integer> ticketStatusSummary = ticketDAO.countStatusSummary();
        request.setAttribute("ticketStatusSummary", ticketStatusSummary);
        request.setAttribute("overdueOrDueSoonCount", ticketDAO.countOverdueOrDueSoon());

        request.setAttribute("currentMonthNumber", today.getMonthValue());

        // ===== Badge ngày hôm nay =====
        Calendar cal = Calendar.getInstance();
        String weekday = WEEKDAY_VI[cal.get(Calendar.DAY_OF_WEEK) - 1];
        request.setAttribute("todayLabel", weekday + ", " + String.format("%02d/%02d/%04d",
                today.getDayOfMonth(), today.getMonthValue(), today.getYear()));

        // ===== Biểu đồ doanh thu N tháng gần nhất =====
        Map<String, BigDecimal> revenueByMonth = paymentDAO.sumInvoiceAmountLastNMonths(REVENUE_CHART_MONTHS);
        List<String> chartMonthLabels = new ArrayList<>();
        List<Double> chartRevenueValues = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : revenueByMonth.entrySet()) {
            String[] parts = entry.getKey().split("-");
            chartMonthLabels.add("Th." + Integer.parseInt(parts[1]));
            // Đổi sang đơn vị tỷ đồng, làm tròn 1 chữ số thập phân, khớp nhãn "đơn vị: tỷ đồng" trên biểu đồ.
            double billions = entry.getValue().divide(new BigDecimal("1000000000"), 4, java.math.RoundingMode.HALF_UP).doubleValue();
            chartRevenueValues.add(Math.round(billions * 10) / 10.0);
        }
        request.setAttribute("chartMonthLabels", chartMonthLabels);
        request.setAttribute("chartRevenueValues", chartRevenueValues);

        // ===== Bảng hợp đồng sắp hết hạn =====
        List<Contract> expiringContracts = contractDAO.findExpiringSoon(EXPIRING_CONTRACTS_LIMIT);
        Map<Integer, BigDecimal> contractValues = new HashMap<>();
        Map<Integer, Long> daysRemaining = new HashMap<>();
        for (Contract c : expiringContracts) {
            contractValues.put(c.getContractId(), paymentDAO.sumInvoiceAmountByContractId(c.getContractId()));
            LocalDate endDate = c.getEndDate().toLocalDate();
            daysRemaining.put(c.getContractId(), ChronoUnit.DAYS.between(today, endDate));
        }
        request.setAttribute("expiringContracts", expiringContracts);
        request.setAttribute("contractValues", contractValues);
        request.setAttribute("daysRemaining", daysRemaining);

        // ===== Bảng phiếu hỗ trợ cần xử lý =====
        List<TechnicalRequest> attentionTickets = ticketDAO.findNeedingAttention(ATTENTION_TICKETS_LIMIT);
        request.setAttribute("attentionTickets", attentionTickets);

        request.getRequestDispatcher("/dashboard.jsp").forward(request, response);
    }
}
