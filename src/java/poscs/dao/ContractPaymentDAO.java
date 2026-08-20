package poscs.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import poscs.model.ContractPayment;

/**
 * DAO cho bảng contract_payments -- phục vụ tính doanh thu (dựa trên
 * paid_date, tức tiền đã thực thu, không phải due_date). Bảng và model
 * ContractPayment đã có sẵn trong schema nhưng trước đây chưa có DAO nào
 * đụng tới.
 */
public class ContractPaymentDAO {

    /** Tổng tiền đã thu trong 1 tháng cụ thể. Trả về 0 nếu không có khoản thu nào. */
    public BigDecimal sumInvoiceAmountByMonth(int year, int month) {
        String sql = "SELECT COALESCE(SUM(invoice_amount), 0) FROM contract_payments " +
                     "WHERE paid_date IS NOT NULL AND YEAR(paid_date) = ? AND MONTH(paid_date) = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, year);
            ps.setInt(2, month);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TINH DOANH THU THEO THANG ---");
            ex.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

    /**
     * Tổng tiền đã thu theo từng tháng trong N tháng gần nhất (tính cả
     * tháng hiện tại), key "yyyy-MM", đủ N tháng kể cả tháng không có
     * khoản thu nào (giá trị 0) -- phục vụ biểu đồ cột doanh thu.
     */
    public Map<String, BigDecimal> sumInvoiceAmountLastNMonths(int months) {
        LinkedHashMap<String, BigDecimal> result = new LinkedHashMap<>();
        YearMonth end = YearMonth.now();
        YearMonth start = end.minusMonths(months - 1L);
        for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
            result.put(ym.toString(), BigDecimal.ZERO);
        }

        String sql = "SELECT YEAR(paid_date) AS y, MONTH(paid_date) AS m, SUM(invoice_amount) AS total " +
                     "FROM contract_payments " +
                     "WHERE paid_date IS NOT NULL AND paid_date >= ? " +
                     "GROUP BY YEAR(paid_date), MONTH(paid_date)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(start.atDay(1)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = String.format("%04d-%02d", rs.getInt("y"), rs.getInt("m"));
                    if (result.containsKey(key)) {
                        result.put(key, rs.getBigDecimal("total"));
                    }
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TINH DOANH THU THEO N THANG ---");
            ex.printStackTrace();
        }
        return result;
    }

    /** Tổng tiền đã lập hoá đơn (thu hoặc chưa thu) của 1 hợp đồng cụ thể -- dùng làm "Giá trị" hợp đồng. */
    public BigDecimal sumInvoiceAmountByContractId(int contractId) {
        String sql = "SELECT COALESCE(SUM(invoice_amount), 0) FROM contract_payments WHERE contract_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TINH GIA TRI HOP DONG ---");
            ex.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

    /**
     * Toàn bộ khoản thu (đã thu lẫn chưa thu) của tất cả hợp đồng thuộc 1
     * khách hàng -- phục vụ CustomerEvaluator chấm điểm/xếp hạng quan hệ.
     */
    public List<ContractPayment> findByEnterpriseId(int enterpriseId) {
        List<ContractPayment> result = new ArrayList<>();
        String sql = "SELECT cp.payment_id, cp.contract_id, cp.invoice_amount, cp.due_date, cp.paid_date, cp.created_at " +
                     "FROM contract_payments cp " +
                     "JOIN contracts c ON c.contract_id = cp.contract_id " +
                     "WHERE c.enterprise_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enterpriseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ContractPayment p = new ContractPayment();
                    p.setPaymentId(rs.getInt("payment_id"));
                    p.setContractId(rs.getInt("contract_id"));
                    p.setInvoiceAmount(rs.getBigDecimal("invoice_amount"));
                    p.setDueDate(rs.getDate("due_date"));
                    p.setPaidDate(rs.getDate("paid_date"));
                    p.setCreatedAt(rs.getTimestamp("created_at"));
                    result.add(p);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI LAY LICH SU THANH TOAN THEO KHACH HANG ---");
            ex.printStackTrace();
        }
        return result;
    }
}
