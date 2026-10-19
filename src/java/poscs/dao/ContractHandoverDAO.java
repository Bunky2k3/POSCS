package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.SqlFilters;
import poscs.model.Contract;
import poscs.model.ContractHandover;
import poscs.model.ContractHistory;

/**
 * Data Access Object chuyên biệt cho chặng bàn giao hợp đồng cho các phòng ban
 * (bảng {@code contract_handovers} - [UC-48, UC-49]).
 *
 * <p>Tách từ {@link ContractDAO} (Task 4.7):
 * <ul>
 *   <li>UC-48: Khởi tạo bàn giao hợp đồng cho một hoặc nhiều phòng ban (Kinh doanh soạn xong
 *       chuyển xuống Kế toán, Dự án cùng lúc).</li>
 *   <li>UC-49: Xác nhận xử lý bàn giao (Admin hoặc nhân viên phòng nhận).</li>
 *   <li>Ràng buộc nghiệp vụ:
 *     <ul>
 *       <li>Chặn mở chặng mới khi hợp đồng đã đóng băng (thanh lý hoặc chấm dứt sớm - {@link #HANDOVER_FROZEN}).</li>
 *       <li>Chặn giao lại khi phòng đó đang còn giữ hợp đồng chưa xử lý ({@link #HANDOVER_ALREADY_PENDING}).</li>
 *       <li>Bàn giao nhiều phòng trong MỘT transaction nguyên tử (NFR-19).</li>
 *       <li>Ghi nhận kiểm toán vào {@code contract_history} loại sự kiện {@link ContractHistory#EVENT_HANDOVER}.</li>
 *       <li>Xác nhận xong chặng bắt buộc có ghi chú {@code doneNote} (không rỗng / blank).</li>
 *       <li>Giới hạn độ dài schema: handover_note tối đa 255 ký tự, done_note tối đa 500 ký tự.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class ContractHandoverDAO {

    private static final Logger LOG = LoggerFactory.getLogger(ContractHandoverDAO.class);

    /** Phòng đó đang còn giữ hợp đồng, không giao lại lượt mới được. [UC-48] */
    public static final int HANDOVER_ALREADY_PENDING = -1;

    /**
     * Truyền vào chỗ {@code waitingDepartmentId} để lọc "đang chờ ở BẤT KỲ phòng
     * nào" thay vì một phòng cụ thể.
     */
    public static final int WAITING_ANY_DEPARTMENT = -1;

    /** Hợp đồng đã thanh lý / chấm dứt sớm -- không mở lượt bàn giao mới nữa. [UC-48] */
    public static final int HANDOVER_FROZEN = -3;

    /** Thao tác bàn giao thất bại / Hợp đồng không tồn tại. */
    public static final int HANDOVER_FAILED = -2;

    /** Giới hạn độ dài cột handover_note theo db/schema.sql (varchar 255). */
    public static final int HANDOVER_NOTE_MAX_LENGTH = 255;

    /** Giới hạn độ dài cột done_note theo db/schema.sql (varchar 500). */
    public static final int DONE_NOTE_MAX_LENGTH = 500;

    public static final String HANDOVER_SELECT =
        "SELECT h.handover_id, h.contract_id, h.department_id, h.handed_at, h.handed_by, "
        + "       h.done_at, h.done_by, h.handover_note, h.done_note, "
        + "       d.department_name, c.contract_code, c.title AS contract_title, "
        + "       hb.last_name AS hb_last, hb.middle_name AS hb_mid, hb.first_name AS hb_first, "
        + "       db.last_name AS db_last, db.middle_name AS db_mid, db.first_name AS db_first, "
        + "       ow.last_name AS ow_last, ow.middle_name AS ow_mid, ow.first_name AS ow_first "
        + "FROM contract_handovers h "
        + "JOIN departments d ON d.department_id = h.department_id "
        + "JOIN contracts c ON c.contract_id = h.contract_id "
        + "LEFT JOIN users hb ON hb.user_id = h.handed_by "
        + "LEFT JOIN users db ON db.user_id = h.done_by "
        + "LEFT JOIN users ow ON ow.user_id = c.owner_id ";

    private final ContractHistoryDAO historyDAO;

    public ContractHandoverDAO() {
        this(new ContractHistoryDAO());
    }

    public ContractHandoverDAO(ContractHistoryDAO historyDAO) {
        this.historyDAO = historyDAO != null ? historyDAO : new ContractHistoryDAO();
    }

    public ContractHistoryDAO getHistoryDAO() {
        return historyDAO;
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

    private Contract lockContractForUpdate(Connection conn, int contractId) throws SQLException {
        String sql = "SELECT contract_id, contract_code, progress_status FROM contracts "
                + "WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Contract c = new Contract();
                c.setContractId(rs.getInt("contract_id"));
                c.setContractCode(rs.getString("contract_code"));
                c.setProgressStatus(rs.getString("progress_status"));
                return c;
            }
        }
    }

    private boolean hasInvalidOrDuplicateDepartments(List<Integer> departmentIds) {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Integer id : departmentIds) {
            if (id == null || id <= 0 || !seen.add(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean insertHandoverRow(Connection conn, int contractId, int departmentId, int actorId, String note) throws SQLException {
        String sql = "INSERT INTO contract_handovers (contract_id, department_id, handed_by, handover_note) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            ps.setInt(2, departmentId);
            ps.setInt(3, actorId);
            ps.setString(4, note);
            return ps.executeUpdate() > 0;
        }
    }

    private int[] lockPendingHandover(Connection conn, int handoverId) throws SQLException {
        String sql = "SELECT contract_id, department_id FROM contract_handovers WHERE handover_id = ? AND done_at IS NULL FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, handoverId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new int[] { rs.getInt("contract_id"), rs.getInt("department_id") };
            }
        }
    }

    private boolean updateDoneStatus(Connection conn, int handoverId, int actorId, String doneNote) throws SQLException {
        String sql = "UPDATE contract_handovers SET done_at = NOW(), done_by = ?, done_note = ? WHERE handover_id = ? AND done_at IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, actorId);
            ps.setString(2, doneNote);
            ps.setInt(3, handoverId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Kiểm tra phòng ban hiện có đang giữ hợp đồng này và chưa xử lý xong hay không.
     */
    public boolean hasPendingHandover(Connection conn, int contractId, int departmentId) throws SQLException {
        String sql = "SELECT 1 FROM contract_handovers WHERE contract_id = ? AND department_id = ? AND done_at IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            ps.setInt(2, departmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Lấy tên phòng ban để câu nhật ký đọc được thay vì "phòng #5".
     */
    public String departmentName(Connection conn, int departmentId) throws SQLException {
        String sql = "SELECT department_name FROM departments WHERE department_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, departmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "#" + departmentId;
            }
        }
    }

    /**
     * Bàn giao một hợp đồng cho nhiều phòng CÙNG LÚC (Kế toán + Dự án là mặc
     * định của luồng) - [UC-48]. Một transaction cho cả lô: giao được nửa rồi hỏng
     * thì hợp đồng nằm ở trạng thái không ai mô tả nổi (NFR-19).
     *
     * <p>CHẶN khi hợp đồng đã đóng băng (thanh lý / chấm dứt sớm).
     *
     * <p>Chỉ chặn MỞ lượt MỚI. ĐÓNG một chặng đang treo thì VẪN được ({@code completeHandover}).
     *
     * @return số chặng mở ra, {@link #HANDOVER_ALREADY_PENDING} nếu MỘT trong các
     *         phòng đó đang còn giữ hợp đồng này, {@link #HANDOVER_FROZEN} khi hợp đồng
     *         đã thanh lý / chấm dứt sớm, hoặc {@link #HANDOVER_FAILED} nếu thất bại
     */
    public int handOverToDepartments(int contractId, List<Integer> departmentIds, String note, int actorId) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return 0;
        }
        if (contractId <= 0 || actorId <= 0 || hasInvalidOrDuplicateDepartments(departmentIds)) {
            return HANDOVER_FAILED;
        }
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                Contract contract = lockContractForUpdate(conn, contractId);
                if (contract == null) {
                    return HANDOVER_FAILED;
                }
                if (contract.isFrozen()) {
                    return HANDOVER_FROZEN;
                }
                for (Integer departmentId : departmentIds) {
                    if (hasPendingHandover(conn, contractId, departmentId)) {
                        return HANDOVER_ALREADY_PENDING;
                    }
                }

                String safeNote = ContractDocumentDAO.trimToNull(note);
                if (safeNote != null) {
                    safeNote = ContractHistoryDAO.truncate(safeNote, HANDOVER_NOTE_MAX_LENGTH);
                }
                for (Integer departmentId : departmentIds) {
                    if (!insertHandoverRow(conn, contractId, departmentId, actorId, safeNote)) {
                        return HANDOVER_FAILED;
                    }
                    historyDAO.insertHistory(conn, contractId, ContractHistory.EVENT_HANDOVER,
                            "Bàn giao cho phòng " + departmentName(conn, departmentId), actorId, safeNote);
                }

                conn.commit();
                committed = true;
                return departmentIds.size();
            } finally {
                finishTransaction(conn, committed, "ban giao hop dong", contractId);
            }
        } catch (SQLException ex) {
            LOG.error("Loi ban giao hop dong (contractId={})", contractId, ex);
            return HANDOVER_FAILED;
        }
    }

    /**
     * Phòng nhận báo đã xử lý xong - [UC-49]. Ghi chú BẮT BUỘC: một chặng đóng lại mà
     * không ai nói đã làm gì thì về sau không phân biệt được với việc bấm cho xong.
     */
    public boolean completeHandover(int handoverId, int actorId, String doneNote) {
        if (doneNote == null || doneNote.trim().isEmpty() || handoverId <= 0 || actorId <= 0) {
            return false;
        }
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                int[] ids = lockPendingHandover(conn, handoverId);
                if (ids == null) {
                    return false;
                }
                int contractId = ids[0];
                int departmentId = ids[1];

                String safeDoneNote = ContractHistoryDAO.truncate(doneNote.trim(), DONE_NOTE_MAX_LENGTH);
                if (!updateDoneStatus(conn, handoverId, actorId, safeDoneNote)) {
                    return false;
                }

                historyDAO.insertHistory(conn, contractId, ContractHistory.EVENT_HANDOVER,
                        "Phòng " + departmentName(conn, departmentId) + " báo đã xử lý xong",
                        actorId, safeDoneNote);

                conn.commit();
                committed = true;
            } finally {
                finishTransaction(conn, committed, "xac nhan xong chang ban giao", handoverId);
            }
            return committed;
        } catch (SQLException ex) {
            LOG.error("Loi xac nhan xong chang ban giao (handoverId={})", handoverId, ex);
            return false;
        }
    }

    /**
     * Các chặng của một hợp đồng, lượt mới nhất đứng trước - [UC-48, UC-49].
     */
    public List<ContractHandover> findHandoversOf(int contractId) {
        List<ContractHandover> result = new ArrayList<>();
        if (contractId <= 0) {
            return result;
        }
        String sql = HANDOVER_SELECT + "WHERE h.contract_id = ? ORDER BY h.handed_at DESC, h.handover_id DESC";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapHandoverRow(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van chang ban giao (contractId={})", contractId, ex);
        }
        return result;
    }

    /**
     * Mọi chặng CHƯA XONG của toàn hệ thống, phòng nào để lâu nhất đứng trước
     * -- màn hình theo dõi của giám đốc [UC-48, UC-49].
     *
     * @param ownerIds giới hạn theo người phụ trách hợp đồng (rỗng/null = tất cả)
     */
    public List<ContractHandover> findPendingHandovers(List<Integer> ownerIds) {
        List<ContractHandover> result = new ArrayList<>();
        String sql = HANDOVER_SELECT
                + "WHERE h.done_at IS NULL AND c.is_deleted = 0"
                + SqlFilters.inClause("c.owner_id", ownerIds)
                + " ORDER BY h.handed_at ASC";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            SqlFilters.bind(ps, 1, ownerIds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapHandoverRow(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van chang ban giao dang cho", ex);
        }
        return result;
    }

    /**
     * Ánh xạ một dòng chặng bàn giao từ ResultSet - [UC-48, UC-49].
     */
    public static ContractHandover mapHandoverRow(ResultSet rs) throws SQLException {
        ContractHandover h = new ContractHandover();
        h.setHandoverId(rs.getInt("handover_id"));
        h.setContractId(rs.getInt("contract_id"));
        h.setDepartmentId(rs.getInt("department_id"));
        h.setDepartmentName(rs.getString("department_name"));
        h.setHandedAt(rs.getTimestamp("handed_at"));
        h.setHandedBy(rs.getInt("handed_by"));
        h.setDoneAt(rs.getTimestamp("done_at"));
        int doneBy = rs.getInt("done_by");
        h.setDoneBy(rs.wasNull() ? null : doneBy);
        h.setHandoverNote(rs.getString("handover_note"));
        h.setDoneNote(rs.getString("done_note"));
        h.setContractCode(rs.getString("contract_code"));
        h.setContractTitle(rs.getString("contract_title"));
        h.setHandedByName(ContractDocumentDAO.fullName(rs, "hb_last", "hb_mid", "hb_first"));
        h.setDoneByName(ContractDocumentDAO.fullName(rs, "db_last", "db_mid", "db_first"));
        h.setOwnerName(ContractDocumentDAO.fullName(rs, "ow_last", "ow_mid", "ow_first"));
        return h;
    }
}
