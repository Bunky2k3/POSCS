package poscs.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
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
 * (bảng {@code contract_payments} - [UC-53] [BR-18] [BR-60]).
 *
 * <p>Tách từ {@link ContractDAO} (Task 4.4) nhằm phân tách trách nhiệm quản lý thanh toán:
 * <ul>
 *   <li>Quản lý kỳ thanh toán (lập kỳ, sửa kỳ, ghi nhận đã nộp hồ sơ, ghi nhận đã thu
 *       kèm số tiền thực thu, xoá kỳ lập nhầm).</li>
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

    /**
     * BR-18: doanh thu cộng SỐ TIỀN THỰC THU. Kỳ đã thu trước V45 đã được chép số
     * tiền kỳ sang paid_amount; COALESCE chỉ để một dòng sót NULL vẫn không làm hụt số.
     */
    public static final String COLLECTED_AMOUNT_SQL = "COALESCE(p.paid_amount, p.invoice_amount)";

    private static final String SELECT_COLUMNS = "payment_id, contract_id, payment_type, invoice_amount, "
            + "condition_note, payment_days, submitted_date, due_date, paid_date, paid_amount, created_at";

    /** Một đơn vị việc chạy trong transaction; trả false là rollback. */
    @FunctionalInterface
    private interface TxWork {
        boolean run(Connection conn) throws SQLException;
    }

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
     * Các kỳ thanh toán của một hợp đồng (tự mở kết nối), theo THỨ TỰ LẬP.
     *
     * <p>Không sắp theo hạn thu nữa: hạn thu để trống được tới lúc nộp hồ sơ (BR-60),
     * sắp theo nó thì số thứ tự kỳ nhảy mỗi lần một kỳ được nộp hồ sơ. Thứ tự lập
     * là thứ tự trong điều khoản thanh toán (Tạm ứng rồi Thanh toán) -- người dùng
     * chốt 20/11/2026.
     */
    public List<ContractPayment> findPaymentsByContractId(int contractId) {
        List<ContractPayment> result = new ArrayList<>();
        if (contractId <= 0) {
            return result;
        }
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM contract_payments WHERE contract_id = ? ORDER BY payment_id";
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
     * BR-60: hạn thu để trống được. Kỳ lập kèm ngày đã thu (nhập lại kỳ cũ) thì số thực thu
     * = số tiền kỳ, trừ khi đã có số thực thu.
     */
    public boolean insertPayment(int contractId, ContractPayment payment, int actorId) {
        if (contractId <= 0 || actorId <= 0 || payment == null
                || payment.getInvoiceAmount() == null || payment.getInvoiceAmount().signum() <= 0) {
            return false;
        }
        // Bốn cột đầu giữ đúng vị trí cũ; cột của BR-60 nối thêm phía sau.
        String sql = "INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date, "
                + "payment_type, condition_note, payment_days, paid_amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        return inTransaction("lap ky thanh toan", contractId, conn -> {
            if (isFrozen(conn, contractId)) {
                return false;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, contractId);
                ps.setBigDecimal(2, payment.getInvoiceAmount());
                ps.setDate(3, payment.getDueDate());
                ps.setDate(4, payment.getPaidDate());
                ps.setString(5, typeOrDefault(payment.getPaymentType()));
                ps.setString(6, payment.getConditionNote());
                setNullableInt(ps, 7, payment.getPaymentDays());
                ps.setBigDecimal(8, payment.getPaidDate() == null ? null : payment.getCollectedAmount());
                if (ps.executeUpdate() == 0) {
                    return false;
                }
            }
            historyDAO.insertHistory(conn, contractId, ContractHistory.EVENT_PAYMENT_ADDED,
                    "Lập kỳ " + summaryOf(payment)
                            + (payment.getPaidDate() == null ? ""
                                    : " (đã thu " + formatDate(payment.getPaidDate()) + ")"),
                    actorId, null);
            return true;
        });
    }

    /**
     * Ghi nhận tiền của một kỳ đã về, số thực thu bằng đúng số tiền kỳ.
     * Xem {@link #markPaymentPaid(int, int, Date, BigDecimal, int)}.
     */
    public boolean markPaymentPaid(int paymentId, int contractId, Date paidDate, int actorId) {
        return markPaymentPaid(paymentId, contractId, paidDate, null, actorId);
    }

    /**
     * Ghi nhận tiền của một kỳ đã về kèm SỐ TIỀN THỰC THU (BR-60, BR-18), tự mở kết nối
     * và quản lý transaction. {@code paidAmount} null là thu đủ số tiền kỳ: câu UPDATE
     * tự chép invoice_amount, không đọc ra rồi ghi lại.
     *
     * <p>KHÔNG chặn kể cả khi hợp đồng đã thanh lý: tiền bảo hành giữ lại thường chỉ về sau thanh lý cả năm.
     */
    public boolean markPaymentPaid(int paymentId, int contractId, Date paidDate, BigDecimal paidAmount,
                                   int actorId) {
        if (paymentId <= 0 || contractId <= 0 || actorId <= 0 || paidDate == null
                || (paidAmount != null && paidAmount.signum() <= 0)) {
            return false;
        }
        String sql = paidAmount == null
                ? "UPDATE contract_payments SET paid_date = ?, paid_amount = invoice_amount "
                        + "WHERE payment_id = ? AND contract_id = ?"
                : "UPDATE contract_payments SET paid_date = ?, paid_amount = ? "
                        + "WHERE payment_id = ? AND contract_id = ?";
        return inTransaction("ghi nhan da thu", contractId, conn -> {
            ContractPayment before = lockPayment(conn, paymentId, contractId);
            if (before == null || before.getPaidDate() != null) {
                return false;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                ps.setDate(i++, paidDate);
                if (paidAmount != null) {
                    ps.setBigDecimal(i++, paidAmount);
                }
                ps.setInt(i++, paymentId);
                ps.setInt(i, contractId);
                if (ps.executeUpdate() == 0) {
                    return false;
                }
            }
            historyDAO.insertHistory(conn, contractId, ContractHistory.EVENT_PAYMENT_PAID,
                    paidDetail(before, paidDate, paidAmount), actorId, null);
            return true;
        });
    }

    private static String paidDetail(ContractPayment before, Date paidDate, BigDecimal paidAmount) {
        BigDecimal invoice = before.getInvoiceAmount();
        boolean differs = paidAmount != null && invoice != null && paidAmount.compareTo(invoice) != 0;
        return "Đã thu " + formatMoney(paidAmount != null ? paidAmount : invoice)
                + (differs ? " (số tiền kỳ " + formatMoney(invoice) + ")" : "")
                + " (kỳ " + dueText(before.getDueDate()) + ")"
                + " ngày " + formatDate(paidDate);
    }

    /**
     * Sửa một kỳ CHƯA THU (BR-60): loại, số tiền, điều kiện, số ngày, hạn thu. Tự mở kết
     * nối và quản lý transaction, kèm dòng nhật ký. Ngày nộp hồ sơ và ngày thu không sửa ở đây.
     *
     * <p>CHẶN khi hợp đồng đã đóng băng hoặc kỳ đã thu -- kiểm lại trong transaction,
     * khoá dòng kỳ, để hai người bấm cùng lúc không lọt.
     */
    public boolean updatePayment(int paymentId, int contractId, ContractPayment payment, int actorId) {
        if (paymentId <= 0 || contractId <= 0 || actorId <= 0 || payment == null
                || payment.getInvoiceAmount() == null || payment.getInvoiceAmount().signum() <= 0) {
            return false;
        }
        String sql = "UPDATE contract_payments SET payment_type = ?, invoice_amount = ?, condition_note = ?, "
                + "payment_days = ?, due_date = ? WHERE payment_id = ? AND contract_id = ?";
        return inTransaction("sua ky thanh toan", contractId, conn -> {
            if (isFrozen(conn, contractId)) {
                return false;
            }
            ContractPayment before = lockPayment(conn, paymentId, contractId);
            if (before == null || before.getPaidDate() != null) {
                return false;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, typeOrDefault(payment.getPaymentType()));
                ps.setBigDecimal(2, payment.getInvoiceAmount());
                ps.setString(3, payment.getConditionNote());
                setNullableInt(ps, 4, payment.getPaymentDays());
                ps.setDate(5, payment.getDueDate());
                ps.setInt(6, paymentId);
                ps.setInt(7, contractId);
                if (ps.executeUpdate() == 0) {
                    return false;
                }
            }
            historyDAO.insertHistory(conn, contractId, ContractHistory.EVENT_PAYMENT_UPDATED,
                    "Sửa kỳ " + summaryOf(before) + " → " + summaryOf(payment), actorId, null);
            return true;
        });
    }

    /**
     * Ghi nhận ĐÃ NỘP HỒ SƠ thanh toán của một kỳ (BR-60): ghi ngày nộp và hạn thu do
     * service đã tính (ngày nộp + số ngày, hoặc hạn nhập tay, hoặc để trống). Tự mở kết
     * nối và quản lý transaction, kèm dòng nhật ký.
     *
     * <p>KHÔNG chặn khi hợp đồng đã thanh lý (BR-60), chỉ chặn kỳ đã thu.
     */
    public boolean markSubmitted(int paymentId, int contractId, Date submittedDate, Date dueDate, int actorId) {
        if (paymentId <= 0 || contractId <= 0 || actorId <= 0 || submittedDate == null) {
            return false;
        }
        String sql = "UPDATE contract_payments SET submitted_date = ?, due_date = ? "
                + "WHERE payment_id = ? AND contract_id = ?";
        return inTransaction("ghi nhan nop ho so", contractId, conn -> {
            ContractPayment before = lockPayment(conn, paymentId, contractId);
            if (before == null || before.getPaidDate() != null) {
                return false;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDate(1, submittedDate);
                ps.setDate(2, dueDate);
                ps.setInt(3, paymentId);
                ps.setInt(4, contractId);
                if (ps.executeUpdate() == 0) {
                    return false;
                }
            }
            historyDAO.insertHistory(conn, contractId, ContractHistory.EVENT_PAYMENT_SUBMITTED,
                    "Nộp hồ sơ kỳ " + typeOrDefault(before.getPaymentType()) + " "
                            + formatMoney(before.getInvoiceAmount())
                            + " ngày " + formatDate(submittedDate) + ", " + dueText(dueDate),
                    actorId, null);
            return true;
        });
    }

    /** "Thanh toán 450.528.480 đ, đến hạn 27/11/2026" -- dùng cho nhật ký lập / sửa kỳ. */
    private static String summaryOf(ContractPayment p) {
        return typeOrDefault(p.getPaymentType()) + " " + formatMoney(p.getInvoiceAmount())
                + (p.getPaymentDays() == null ? "" : ", trả trong " + p.getPaymentDays() + " ngày")
                + ", " + dueText(p.getDueDate());
    }

    /** "đến hạn dd/MM/yyyy", hoặc "chưa có hạn thu" khi hạn thu trống (BR-60). */
    private static String dueText(Date dueDate) {
        return dueDate == null ? "chưa có hạn thu" : "đến hạn " + formatDate(dueDate);
    }

    /** Loại kỳ để ghi/hiện: trống thì là "Thanh toán", như DEFAULT của cột (V45). */
    private static String typeOrDefault(String type) {
        return type == null || type.trim().isEmpty() ? ContractPayment.TYPE_PAYMENT : type;
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    /**
     * Chạy {@code work} trong một transaction trên kết nối tự mở: work trả true thì commit,
     * false hoặc ném lỗi thì rollback. Lỗi SQL ghi log và trả false.
     */
    private boolean inTransaction(String what, int contractId, TxWork work) {
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                if (!work.run(conn)) {
                    return false;
                }
                conn.commit();
                committed = true;
                return true;
            } finally {
                finishTransaction(conn, committed, what, contractId);
            }
        } catch (SQLException ex) {
            LOG.error("Loi khi {} (contractId={})", what, contractId, ex);
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
                                + " " + dueText(before.getDueDate()),
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
     * Tổng tiền THỰC THU trong một kỳ (theo ngày thu, BR-18), lọc thêm theo tỉnh của khách hàng đứng tên hợp đồng.
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
        String sql = "SELECT COALESCE(SUM(" + COLLECTED_AMOUNT_SQL + "), 0) FROM contract_payments p "
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
     * Tổng tiền THỰC THU trong 1 tháng cụ thể (theo ngày thu, BR-18). Trả về 0 nếu không có khoản thu nào.
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
        String sql = "SELECT COALESCE(SUM(" + COLLECTED_AMOUNT_SQL + "), 0) FROM contract_payments p "
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
        String sql = "SELECT payment_id, payment_type, invoice_amount, payment_days, submitted_date, due_date, "
                + "paid_date FROM contract_payments WHERE payment_id = ? AND contract_id = ? FOR UPDATE";
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
                p.setPaymentType(typeOrDefault(rs.getString("payment_type")));
                p.setInvoiceAmount(rs.getBigDecimal("invoice_amount"));
                p.setPaymentDays(nullableInt(rs, "payment_days"));
                p.setSubmittedDate(rs.getDate("submitted_date"));
                p.setDueDate(rs.getDate("due_date"));
                p.setPaidDate(rs.getDate("paid_date"));
                return p;
            }
        }
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
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
        p.setPaymentType(typeOrDefault(rs.getString("payment_type")));
        p.setInvoiceAmount(rs.getBigDecimal("invoice_amount"));
        p.setConditionNote(rs.getString("condition_note"));
        p.setPaymentDays(nullableInt(rs, "payment_days"));
        p.setSubmittedDate(rs.getDate("submitted_date"));
        p.setDueDate(rs.getDate("due_date"));
        p.setPaidDate(rs.getDate("paid_date"));
        p.setPaidAmount(rs.getBigDecimal("paid_amount"));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        return p;
    }
}
