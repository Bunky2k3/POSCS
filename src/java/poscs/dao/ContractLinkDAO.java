package poscs.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.model.Contract;
import poscs.model.ContractHistory;
import poscs.model.ContractLink;
import poscs.model.Enterprise;
import poscs.validator.ContractValidator;

/**
 * Data Access Object chuyên biệt cho liên kết giữa hợp đồng BÁN và hợp đồng MUA
 * (bảng {@code contract_links} - [UC-47]).
 *
 * <p>Tách từ {@link ContractDAO} (Task 4.6):
 * <ul>
 *   <li>Quản lý quan hệ "đầu vào phục vụ đầu ra" giữa hợp đồng Bán và hợp đồng Mua.</li>
 *   <li>Ràng buộc: đúng chiều Bán-Mua, cả hai đều là hợp đồng gốc (không phải phụ lục),
 *       không liên kết trùng cặp.</li>
 *   <li>Ghi nhận kiểm toán vào {@code contract_history} cho cả 2 hợp đồng trong cùng transaction (NFR-19).</li>
 * </ul>
 */
public class ContractLinkDAO {

    private static final Logger LOG = LoggerFactory.getLogger(ContractLinkDAO.class);

    public static final String DIRECTION_SELL = "Bán";
    public static final String DIRECTION_BUY = "Mua";

    /** {@link #linkContracts} thành công. */
    public static final int LINK_OK = 1;

    /**
     * Hai hợp đồng không nối được: không tồn tại, cùng chiều, hoặc có cái là phụ lục.
     */
    public static final int LINK_INVALID = -1;

    /** Cặp này đã nối rồi (vi phạm UNIQUE uq_contract_links_pair). */
    public static final int LINK_DUPLICATE = -2;

    /** Thao tác CSDL thất bại. */
    public static final int LINK_FAILED = -1;

    /** Giới hạn độ dài cột note theo db/schema.sql (dòng 4644). */
    public static final int NOTE_MAX_LENGTH = 255;
    public static final String DEFAULT_RELATION_TYPE = "Đầu vào phục vụ đầu ra";

    private final ContractHistoryDAO historyDAO;

    public ContractLinkDAO() {
        this(new ContractHistoryDAO());
    }

    public ContractLinkDAO(ContractHistoryDAO historyDAO) {
        this.historyDAO = historyDAO != null ? historyDAO : new ContractHistoryDAO();
    }

    public ContractHistoryDAO getHistoryDAO() {
        return historyDAO;
    }

    public static boolean isDuplicateKeyError(SQLException ex, String keyColumn) {
        if (ex == null) {
            return false;
        }
        boolean isDuplicate = ex.getErrorCode() == 1062 || "23000".equals(ex.getSQLState());
        if (!isDuplicate) {
            return false;
        }
        if (keyColumn == null || keyColumn.isEmpty()) {
            return true;
        }
        return ex.getMessage() != null && ex.getMessage().contains(keyColumn);
    }

    private void finishTransaction(Connection conn, boolean committed, String what, Object id) {
        if (!committed && conn != null) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                LOG.error("Loi rollback khi {} (id={})", what, id, rollbackEx);
            }
        }
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException autoCommitEx) {
                LOG.error("Loi reset autocommit sau khi {} (id={})", what, id, autoCommitEx);
            }
        }
    }

    /**
     * true nếu hai hợp đồng này nối được với nhau theo UC-47.
     */
    public static boolean canLink(Contract sell, Contract buy) {
        return sell != null && buy != null
                && sell.getContractId() != buy.getContractId()
                && DIRECTION_SELL.equals(sell.getDirection())
                && DIRECTION_BUY.equals(buy.getDirection())
                && !sell.isAmendment() && !buy.isAmendment();
    }

    private Contract lockContractForLink(Connection conn, int contractId) throws SQLException {
        String sql = "SELECT contract_id, contract_code, direction, parent_contract_id "
                + "FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Contract c = new Contract();
                c.setContractId(rs.getInt("contract_id"));
                c.setContractCode(rs.getString("contract_code"));
                c.setDirection(rs.getString("direction"));
                int parentId = rs.getInt("parent_contract_id");
                c.setParentContractId(rs.wasNull() ? null : parentId);
                return c;
            }
        }
    }

    private boolean insertLinkRow(Connection conn, int sellContractId, int buyContractId,
            String safeNote, int actorId) throws SQLException {
        String sql = "INSERT INTO contract_links (sell_contract_id, buy_contract_id, relation_type, note, created_by) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellContractId);
            ps.setInt(2, buyContractId);
            ps.setString(3, DEFAULT_RELATION_TYPE);
            ps.setString(4, safeNote);
            ps.setInt(5, actorId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Nối một hợp đồng BÁN với một hợp đồng MUA (UC-47).
     *
     * <p>Kiểm tra điều kiện TRONG transaction sau khi đã khoá cả hai dòng:
     * đúng chiều (Bán với Mua), cả hai đều là hợp đồng GỐC, và chưa nối trước đó.
     * Ghi nhận 2 dòng lịch sử vào {@code contract_history} cho cả 2 hợp đồng.
     */
    public int linkContracts(int sellContractId, int buyContractId, String note, int actorId) {
        if (sellContractId <= 0 || buyContractId <= 0 || sellContractId == buyContractId || actorId <= 0) {
            return LINK_INVALID;
        }
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                Contract sell = lockContractForLink(conn, sellContractId);
                Contract buy = lockContractForLink(conn, buyContractId);
                if (!canLink(sell, buy)) {
                    return LINK_INVALID;
                }

                String safeNote = ContractDocumentDAO.truncate(ContractDocumentDAO.trimToNull(note), NOTE_MAX_LENGTH);
                if (!insertLinkRow(conn, sellContractId, buyContractId, safeNote, actorId)) {
                    return LINK_FAILED;
                }

                historyDAO.insertHistory(conn, sellContractId, ContractHistory.EVENT_LINKED,
                        "Nối với hợp đồng mua " + buy.getContractCode()
                                + " — đầu vào phục vụ hợp đồng này", actorId, safeNote);
                historyDAO.insertHistory(conn, buyContractId, ContractHistory.EVENT_LINKED,
                        "Phục vụ hợp đồng bán " + sell.getContractCode(), actorId, safeNote);

                conn.commit();
                committed = true;
                return LINK_OK;
            } finally {
                finishTransaction(conn, committed, "noi hop dong", sellContractId);
            }
        } catch (SQLException ex) {
            if (isDuplicateKeyError(ex, "uq_contract_links_pair")) {
                LOG.warn("Hai hop dong da noi voi nhau (sellId={}, buyId={})", sellContractId, buyContractId);
                return LINK_DUPLICATE;
            }
            LOG.error("Loi noi hop dong (sellId={}, buyId={})", sellContractId, buyContractId, ex);
            return LINK_FAILED;
        }
    }

    private static class LinkTarget {
        final int sellId;
        final int buyId;
        final String sellCode;
        final String buyCode;

        LinkTarget(int sellId, int buyId, String sellCode, String buyCode) {
            this.sellId = sellId;
            this.buyId = buyId;
            this.sellCode = sellCode;
            this.buyCode = buyCode;
        }
    }

    private LinkTarget lockLinkForUnlink(Connection conn, int linkId) throws SQLException {
        String sql = "SELECT l.sell_contract_id, l.buy_contract_id, s.contract_code AS sell_code, "
                + "       b.contract_code AS buy_code "
                + "FROM contract_links l "
                + "JOIN contracts s ON s.contract_id = l.sell_contract_id "
                + "JOIN contracts b ON b.contract_id = l.buy_contract_id "
                + "WHERE l.link_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, linkId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new LinkTarget(rs.getInt("sell_contract_id"), rs.getInt("buy_contract_id"),
                        rs.getString("sell_code"), rs.getString("buy_code"));
            }
        }
    }

    private boolean deleteLinkRow(Connection conn, int linkId) throws SQLException {
        String sql = "DELETE FROM contract_links WHERE link_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, linkId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Gỡ một liên kết theo linkId, kèm dòng nhật ký ở cả hai hợp đồng (UC-47).
     */
    public boolean unlinkContracts(int linkId, int actorId) {
        if (linkId <= 0 || actorId <= 0) {
            return false;
        }
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                LinkTarget target = lockLinkForUnlink(conn, linkId);
                if (target == null || !deleteLinkRow(conn, linkId)) {
                    return false;
                }

                historyDAO.insertHistory(conn, target.sellId, ContractHistory.EVENT_LINKED,
                        "Gỡ liên kết với hợp đồng mua " + target.buyCode, actorId, null);
                historyDAO.insertHistory(conn, target.buyId, ContractHistory.EVENT_LINKED,
                        "Gỡ liên kết với hợp đồng bán " + target.sellCode, actorId, null);

                conn.commit();
                committed = true;
                return true;
            } finally {
                finishTransaction(conn, committed, "go lien ket hop dong", linkId);
            }
        } catch (SQLException ex) {
            LOG.error("Loi go lien ket hop dong (linkId={})", linkId, ex);
            return false;
        }
    }

    /**
     * Các hợp đồng nối với hợp đồng này, nhìn từ chính nó (cả chiều Bán lẫn Mua).
     * Bỏ qua liên kết mà đầu kia đã bị huỷ bản ghi (is_deleted = 1).
     */
    public List<ContractLink> findLinksOf(int contractId) {
        if (contractId <= 0) {
            return Collections.emptyList();
        }
        List<ContractLink> result = new ArrayList<>();
        String sql =
            "SELECT l.link_id, l.sell_contract_id, l.buy_contract_id, l.relation_type, l.note, "
            + "       l.created_by, l.created_at, "
            + "       o.contract_id AS other_id, o.contract_code AS other_code, o.title AS other_title, "
            + "       o.direction AS other_direction, o.progress_status AS other_progress, "
            + "       o.contract_value AS other_value, o.effective_date AS other_effective, "
            + "       o.end_date AS other_end, e.enterprise_name AS other_enterprise, "
            + "       u.last_name, u.middle_name, u.first_name, "
            + "       (SELECT COALESCE(SUM(a.contract_value), 0) FROM contracts a "
            + "         WHERE a.parent_contract_id = o.contract_id AND a.is_deleted = 0 "
            + "           AND (a.progress_status IS NULL OR a.progress_status <> '" + ContractDAO.PROGRESS_DRAFT + "')"
            + "       ) AS other_amendment_value, "
            + "       (SELECT COUNT(*) FROM contract_links l2 "
            + "         WHERE l2.buy_contract_id = o.contract_id AND l2.link_id <> l.link_id) AS shared_count "
            + "FROM contract_links l "
            + "JOIN contracts o ON o.contract_id = CASE WHEN l.sell_contract_id = ? "
            + "                                         THEN l.buy_contract_id ELSE l.sell_contract_id END "
            + "LEFT JOIN enterprises e ON o.enterprise_id = e.enterprise_id "
            + "LEFT JOIN users u ON l.created_by = u.user_id "
            + "WHERE (l.sell_contract_id = ? OR l.buy_contract_id = ?) AND o.is_deleted = 0 "
            + "ORDER BY l.created_at, l.link_id";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            ps.setInt(2, contractId);
            ps.setInt(3, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapLinkRow(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van lien ket hop dong (contractId={})", contractId, ex);
        }
        return result;
    }

    /**
     * Tổng giá trị HIỆN HÀNH của các đơn mua đã nối vào một hợp đồng bán.
     * Cộng cả phụ lục đã ký của từng đơn mua; đơn mua bị huỷ bản ghi không tính.
     */
    public BigDecimal sumLinkedBuyValue(int sellContractId) {
        if (sellContractId <= 0) {
            return BigDecimal.ZERO;
        }
        String sql =
            "SELECT COALESCE(SUM(COALESCE(b.contract_value, 0) + ("
            + "    SELECT COALESCE(SUM(a.contract_value), 0) FROM contracts a "
            + "     WHERE a.parent_contract_id = b.contract_id AND a.is_deleted = 0 "
            + "       AND (a.progress_status IS NULL OR a.progress_status <> '" + ContractDAO.PROGRESS_DRAFT + "')"
            + ")), 0) "
            + "FROM contract_links l "
            + "JOIN contracts b ON b.contract_id = l.buy_contract_id "
            + "WHERE l.sell_contract_id = ? AND b.is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellContractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal val = rs.getBigDecimal(1);
                    return val != null ? val : BigDecimal.ZERO;
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi tinh tong gia tri dau vao (sellContractId={})", sellContractId, ex);
        }
        return BigDecimal.ZERO;
    }

    public static ContractLink mapLinkRow(ResultSet rs) throws SQLException {
        ContractLink link = new ContractLink();
        link.setLinkId(rs.getInt("link_id"));
        link.setSellContractId(rs.getInt("sell_contract_id"));
        link.setBuyContractId(rs.getInt("buy_contract_id"));
        link.setRelationType(rs.getString("relation_type"));
        link.setNote(rs.getString("note"));
        link.setCreatedBy(rs.getInt("created_by"));
        link.setCreatedAt(rs.getTimestamp("created_at"));
        link.setSharedCount(rs.getInt("shared_count"));
        link.setOther(mapOtherContract(rs));
        link.setCreatedByName(ContractDocumentDAO.fullName(rs, "last_name", "middle_name", "first_name"));
        return link;
    }

    private static Contract mapOtherContract(ResultSet rs) throws SQLException {
        Contract other = new Contract();
        other.setContractId(rs.getInt("other_id"));
        other.setContractCode(rs.getString("other_code"));
        other.setTitle(rs.getString("other_title"));
        other.setDirection(rs.getString("other_direction"));
        other.setProgressStatus(rs.getString("other_progress"));
        other.setContractValue(rs.getBigDecimal("other_value"));
        other.setAmendmentValueSigned(rs.getBigDecimal("other_amendment_value"));
        other.setEffectiveDate(rs.getDate("other_effective"));
        other.setEndDate(rs.getDate("other_end"));
        other.setStatus(ContractValidator.computeStatus(other.getEffectiveDate(), other.getEndDate()));

        String enterpriseName = rs.getString("other_enterprise");
        if (enterpriseName != null) {
            Enterprise e = new Enterprise();
            e.setEnterpriseName(enterpriseName);
            other.setEnterprise(e);
        }
        return other;
    }
}
