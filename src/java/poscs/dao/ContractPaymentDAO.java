package poscs.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.Period;
import poscs.common.SqlFilters;
import poscs.model.ContractHistory;
import poscs.model.ContractPayment;

/**
 * Data Access Object chuyên biệt cho kỳ thanh toán của hợp đồng
 * (bảng {@code contract_payments} - [UC-53] [BR-18]).
 *
 * <p>Tách từ {@link ContractDAO} (Task 4.4) nhằm phân tách trách nhiệm quản lý thanh toán:
 * <ul>
 *   <li>Quản lý kỳ thanh toán (lập kỳ, ghi nhận đã thu, xoá kỳ lập nhầm).</li>
 *   <li>Tra cứu kỳ thanh toán theo hợp đồng và đối chiếu tổng tiền theo cụm hợp đồng gốc & phụ lục.</li>
 *   <li>Tính toán số liệu doanh thu thực thu theo kỳ và theo tháng (nguồn chân lý cho Dashboard).</li>
 *   <li>Lưu vết kiểm toán đầy đủ sang {@link ContractHistoryDAO} trong cùng transaction (NFR-19).</li>
 * </ul>
 */
public class ContractPaymentDAO {

    private static final Logger LOG = LoggerFactory.getLogger(ContractPaymentDAO.class);

    private static final DateTimeFormatter DETAIL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Loại tiền của hợp đồng ĐÃ HUỶ BẢN GHI ra khỏi các phép cộng doanh thu. */
    public static final String REVENUE_LIVE_CONTRACT_SQL = "AND c.is_deleted = 0 ";

    /** Cột người phụ trách hợp đồng cho bộ lọc phân quyền Dashboard. */
    public static final List<String> DASHBOARD_OWNER_COLUMNS = List.of("c.owner_id");

    private final ContractHistoryDAO historyDAO;

    public ContractPaymentDAO() {
        this(new ContractHistoryDAO());
    }

    public ContractPaymentDAO(ContractHistoryDAO historyDAO) {
        this.historyDAO = historyDAO != null ? historyDAO : new ContractHistoryDAO();
    }

    public ContractHistoryDAO getHistoryDAO() {
        return historyDAO;
    }

    /**
     * Định dạng tiền tệ VNĐ có dấu phân cách hàng nghìn theo quy định BR-18: "1.500.000.000 đ".
     */
    public static String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return String.format("%,.0f", amount).replace(',', '.') + " đ";
    }

    /**
     * Định dạng ngày hiển thị dd/MM/yyyy.
     */
    public static String formatDate(Date date) {
        return date == null ? "" : date.toLocalDate().format(DETAIL_DATE_FORMAT);
    }

    /**
     * Các kỳ thanh toán của một hợp đồng (tự mở kết nối), kỳ đến hạn sớm nhất trước.
     */
    public List<ContractPayment> findPaymentsByContractId(int contractId) {
        List<ContractPayment> result = new ArrayList<>();
        if (contractId <= 0) {
            return result;
        }
        String sql = "SELECT payment_id, contract_id, invoice_amount, due_date, paid_date, created_at "
                + "FROM contract_payments WHERE contract_id = ? ORDER BY due_date, payment_id";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToPayment(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van ky thanh toan (contractId={})", contractId, ex);
        }
        return result;
    }

    /**
     * Lập một kỳ thanh toán, kèm dòng nhật ký -- trong cùng transaction (tự mở kết nối).
     *
     * <p>CHẶN khi hợp đồng đã đóng băng (Đã thanh lý hoặc Chấm dứt sớm), nhưng KHÔNG chặn khi đã ký.
     */
    public boolean insertPayment(int contractId, ContractPayment payment, int actorId) {
        if (contractId <= 0 || actorId <= 0 || payment == null
                || payment.getInvoiceAmount() == null || payment.getInvoiceAmount().signum() <= 0
                || payment.getDueDate() == null) {
            return false;
        }
        String sql = "INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                if (isFrozen(conn, contractId)) {
                    return false;
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, contractId);
                    ps.setBigDecimal(2, payment.getInvoiceAmount());
                    ps.setDate(3, payment.getDueDate());
                    ps.setDate(4, payment.getPaidDate());
                    if (ps.executeUpdate() == 0) {
                        return false;
                    }
                }
                historyDAO.insertHistory(conn, contractId, ContractHistory.EVENT_PAYMENT_ADDED,
                        "Lập kỳ thanh toán " + formatMoney(payment.getInvoiceAmount())
                                + ", đến hạn " + formatDate(payment.getDueDate())
                                + (payment.getPaidDate() == null ? ""
                                        : " (đã thu " + formatDate(payment.getPaidDate()) + ")"),
                        actorId, null);
                conn.commit();
                committed = true;
                return true;
            } finally {
                finishTransaction(conn, committed, "lap ky thanh toan", contractId);
            }
        } catch (SQLException ex) {
            LOG.error("Loi lap ky thanh toan (contractId={})", contractId, ex);
            return false;
        }
    }

    /**
     * Ghi nhận tiền của một kỳ đã về (tự mở kết nối và quản lý transaction).
     *
     * <p>KHÔNG chặn kể cả khi hợp đồng đã thanh lý: tiền bảo hành giữ lại thường chỉ về sau thanh lý cả năm.
     */
    public boolean markPaymentPaid(int paymentId, int contractId, Date paidDate, int actorId) {
        if (paymentId <= 0 || contractId <= 0 || actorId <= 0 || paidDate == null) {
            return false;
        }
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                ContractPayment before = lockPayment(conn, paymentId, contractId);
                if (before == null || before.getPaidDate() != null) {
                    return false;
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE contract_payments SET paid_date = ? WHERE payment_id = ? AND contract_id = ?")) {
                    ps.setDate(1, paidDate);
                    ps.setInt(2, paymentId);
                    ps.setInt(3, contractId);
                    if (ps.executeUpdate() == 0) {
                        return false;
                    }
                }
                historyDAO.insertHistory(conn, contractId, ContractHistory.EVENT_PAYMENT_PAID,
                        "Đã thu " + formatMoney(before.getInvoiceAmount())
                                + " (kỳ đến hạn " + formatDate(before.getDueDate()) + ")"
                                + " ngày " + formatDate(paidDate),
                        actorId, null);
                conn.commit();
                committed = true;
                return true;
            } finally {
                finishTransaction(conn, committed, "ghi nhan da thu", contractId);
            }
        } catch (SQLException ex) {
            LOG.error("Loi ghi nhan da thu (paymentId={}, contractId={})", paymentId, contractId, ex);
            return false;
        }
    }

    /**
     * Xoá một kỳ lập nhầm (tự mở kết nối và quản lý transaction). Đọc TRƯỚC khi xoá để nhật ký nói được đã xoá cái gì.
     */
    public boolean deletePayment(int paymentId, int contractId, int actorId) {
        if (paymentId <= 0 || contractId <= 0 || actorId <= 0) {
            return false;
        }
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                if (isFrozen(conn, contractId)) {
                    return false;
                }
                ContractPayment before = lockPayment(conn, paymentId, contractId);
                if (before == null) {
                    return false;
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM contract_payments WHERE payment_id = ? AND contract_id = ?")) {
                    ps.setInt(1, paymentId);
                    ps.setInt(2, contractId);
                    if (ps.executeUpdate() == 0) {
                        return false;
                    }
                }
                historyDAO.insertHistory(conn, contractId, ContractHistory.EVENT_PAYMENT_REMOVED,
                        "Xoá kỳ " + formatMoney(before.getInvoiceAmount())
                                + " đến hạn " + formatDate(before.getDueDate()),
                        actorId, null);
                conn.commit();
                committed = true;
                return true;
            } finally {
                finishTransaction(conn, committed, "xoa ky thanh toan", contractId);
            }
        } catch (SQLException ex) {
            LOG.error("Loi xoa ky thanh toan (paymentId={}, contractId={})", paymentId, contractId, ex);
            return false;
        }
    }

    /**
     * Tổng tiền đã lập kỳ của CẢ CỤM hợp đồng (tự mở kết nối): bản gốc cộng mọi phụ lục còn sống của nó.
     *
     * @param rootContractId id hợp đồng GỐC (không phải phụ lục)
     */
    public BigDecimal sumScheduledPaymentsForCluster(int rootContractId) {
        if (rootContractId <= 0) {
            return BigDecimal.ZERO;
        }
        String sql = "SELECT COALESCE(SUM(p.invoice_amount), 0) FROM contract_payments p "
                + "JOIN contracts c ON p.contract_id = c.contract_id "
                + "WHERE c.is_deleted = 0 AND (c.contract_id = ? OR c.parent_contract_id = ?)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rootContractId);
            ps.setInt(2, rootContractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal sum = rs.getBigDecimal(1);
                    return sum != null ? sum : BigDecimal.ZERO;
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi tinh tong ky thanh toan ca cum (rootContractId={})", rootContractId, ex);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Tổng tiền đã thu trong một kỳ (theo ngày thanh toán), lọc thêm theo tỉnh của khách hàng đứng tên hợp đồng.
     */
    public BigDecimal sumInvoiceAmountInPeriod(Period period, Integer provinceId) {
        return sumInvoiceAmountInPeriod(period, SqlFilters.one(provinceId), null);
    }

    /**
     * Như trên nhưng thu hẹp theo nhiều tỉnh và theo người phụ trách -- bộ lọc Dashboard.
     */
    public BigDecimal sumInvoiceAmountInPeriod(Period period, List<Integer> provinceIds, List<Integer> ownerIds) {
        if (period == null) {
            return BigDecimal.ZERO;
        }
        String sql = "SELECT COALESCE(SUM(p.invoice_amount), 0) FROM contract_payments p "
                + "LEFT JOIN contracts c ON p.contract_id = c.contract_id "
                + "LEFT JOIN enterprises e ON c.enterprise_id = e.enterprise_id "
                + "LEFT JOIN addresses a ON e.address_id = a.address_id "
                + "LEFT JOIN districts d ON a.districts_id = d.districts_id "
                + "WHERE p.paid_date IS NOT NULL AND p.paid_date BETWEEN ? AND ? "
                + "AND c.direction = 'Bán' "
                + REVENUE_LIVE_CONTRACT_SQL
                + SqlFilters.scopeClause(DASHBOARD_OWNER_COLUMNS, ownerIds, "d.province_id", provinceIds);
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, period.getFrom());
            ps.setDate(2, period.getTo());
            int param = 3;
            SqlFilters.bindScope(ps, param, DASHBOARD_OWNER_COLUMNS, ownerIds, provinceIds);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal sum = rs.getBigDecimal(1);
                    return sum != null ? sum : BigDecimal.ZERO;
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi tinh doanh thu theo ky", ex);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Tổng tiền đã thu trong 1 tháng cụ thể. Trả về 0 nếu không có khoản thu nào.
     */
    public BigDecimal sumInvoiceAmountByMonth(int year, int month) {
        return sumInvoiceAmountByMonth(year, month, null);
    }

    /**
     * Như {@link #sumInvoiceAmountByMonth(int, int)} nhưng chỉ tính các khoản thu của hợp đồng thuộc 1 tỉnh.
     */
    public BigDecimal sumInvoiceAmountByMonth(int year, int month, Integer provinceId) {
        return sumInvoiceAmountByMonth(year, month, SqlFilters.one(provinceId), null);
    }

    /**
     * Như trên nhưng thu hẹp theo nhiều tỉnh và theo người phụ trách -- bộ lọc Dashboard.
     */
    public BigDecimal sumInvoiceAmountByMonth(int year, int month, List<Integer> provinceIds,
            List<Integer> ownerIds) {
        if (year <= 0 || month < 1 || month > 12) {
            return BigDecimal.ZERO;
        }
        String sql = "SELECT COALESCE(SUM(p.invoice_amount), 0) FROM contract_payments p "
                + "LEFT JOIN contracts c ON p.contract_id = c.contract_id "
                + "LEFT JOIN enterprises e ON c.enterprise_id = e.enterprise_id "
                + "LEFT JOIN addresses a ON e.address_id = a.address_id "
                + "LEFT JOIN districts d ON a.districts_id = d.districts_id "
                + "WHERE p.paid_date IS NOT NULL AND YEAR(p.paid_date) = ? AND MONTH(p.paid_date) = ? "
                + "AND c.direction = 'Bán' "
                + REVENUE_LIVE_CONTRACT_SQL
                + SqlFilters.scopeClause(DASHBOARD_OWNER_COLUMNS, ownerIds, "d.province_id", provinceIds);
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, year);
            ps.setInt(2, month);
            int param = 3;
            SqlFilters.bindScope(ps, param, DASHBOARD_OWNER_COLUMNS, ownerIds, provinceIds);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal sum = rs.getBigDecimal(1);
                    return sum != null ? sum : BigDecimal.ZERO;
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi tinh doanh thu theo thang", ex);
        }
        return BigDecimal.ZERO;
    }

    private ContractPayment lockPayment(Connection conn, int paymentId, int contractId) throws SQLException {
        String sql = "SELECT payment_id, invoice_amount, due_date, paid_date FROM contract_payments "
                + "WHERE payment_id = ? AND contract_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            ps.setInt(2, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ContractPayment p = new ContractPayment();
                p.setPaymentId(rs.getInt("payment_id"));
                p.setContractId(contractId);
                p.setInvoiceAmount(rs.getBigDecimal("invoice_amount"));
                p.setDueDate(rs.getDate("due_date"));
                p.setPaidDate(rs.getDate("paid_date"));
                return p;
            }
        }
    }

    private boolean isFrozen(Connection conn, int contractId) throws SQLException {
        String status = lockProgressStatus(conn, contractId);
        return ContractDAO.PROGRESS_LIQUIDATED.equals(status) || ContractDAO.PROGRESS_TERMINATED.equals(status);
    }

    private String lockProgressStatus(Connection conn, int contractId) throws SQLException {
        String sql = "SELECT progress_status FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("progress_status") : null;
            }
        }
    }

    private void finishTransaction(Connection conn, boolean committed, String what, Object id) {
        if (!committed) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                LOG.error("Loi rollback khi {} (id={})", what, id, rollbackEx);
            }
        }
        try {
            conn.setAutoCommit(true);
        } catch (SQLException autoCommitEx) {
            LOG.error("Loi reset autocommit sau khi {} (id={})", what, id, autoCommitEx);
        }
    }

    private ContractPayment mapResultSetToPayment(ResultSet rs) throws SQLException {
        ContractPayment p = new ContractPayment();
        p.setPaymentId(rs.getInt("payment_id"));
        p.setContractId(rs.getInt("contract_id"));
        p.setInvoiceAmount(rs.getBigDecimal("invoice_amount"));
        p.setDueDate(rs.getDate("due_date"));
        p.setPaidDate(rs.getDate("paid_date"));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        return p;
    }
}
