package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.model.Contract;
import poscs.model.Enterprise;
import poscs.model.TechnicalRequest;
import poscs.model.TechnicalRequestHistory;
import poscs.model.User;

/**
 * DAO cho phiếu hỗ trợ kỹ thuật (bảng technicalrequests), kèm lịch sử đổi
 * trạng thái ở bảng con technicalrequesthistory -- xem {@link #update} và
 * {@link #findHistoryByTicketId}. Chưa xử lý technicalrequestdevices (thiết
 * bị lỗi): bảng đó chưa có model/DAO nào, thuộc phạm vi khác.
 */
public class TechnicalSupportTicketDAO {

    private static final Logger LOG = LoggerFactory.getLogger(TechnicalSupportTicketDAO.class);

    public static final String STATUS_NEW = "Mới tiếp nhận";
    public static final String STATUS_IN_PROGRESS = "Đang xử lý";
    public static final String STATUS_CLOSED = "Đã đóng";

    private static final String SELECT_BASE =
        "SELECT t.ticket_id, t.ticket_code, t.enterprise_id, t.contract_id, t.ticket_type, t.priority, " +
        "       t.reception_channel, t.sla_deadline, t.assigned_technician_id, t.created_by, t.created_date, " +
        "       t.description, t.is_warranty, t.status, t.resolution_summary, t.resolved_at, " +
        "       t.created_at, t.updated_at, t.is_deleted, " +
        "       e.enterprise_name, " +
        "       c.contract_code, " +
        "       tech.last_name AS tech_last_name, tech.middle_name AS tech_middle_name, tech.first_name AS tech_first_name, " +
        "       creator.last_name AS creator_last_name, creator.middle_name AS creator_middle_name, creator.first_name AS creator_first_name " +
        "FROM technicalrequests t " +
        "LEFT JOIN enterprises e ON t.enterprise_id = e.enterprise_id " +
        "LEFT JOIN contracts c ON t.contract_id = c.contract_id " +
        "LEFT JOIN users tech ON t.assigned_technician_id = tech.user_id " +
        "LEFT JOIN users creator ON t.created_by = creator.user_id ";

    /**
     * Lấy danh sách phiếu hỗ trợ kỹ thuật của 1 khách hàng, phục vụ tab
     * "Phiếu hỗ trợ kỹ thuật" ở viewcustomerdetail.jsp. Giữ nguyên -- không
     * đổi signature vì CustomerController đang gọi thẳng method này.
     */
    public List<TechnicalRequest> findByEnterpriseId(int enterpriseId) {
        List<TechnicalRequest> result = new ArrayList<>();
        String sql = "SELECT ticket_id, ticket_code, enterprise_id, contract_id, ticket_type, " +
                     "priority, reception_channel, assigned_technician_id, created_by, created_date, " +
                     "description, is_warranty, status " +
                     "FROM technicalrequests WHERE enterprise_id = ? AND is_deleted = 0 ORDER BY created_date DESC";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enterpriseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TechnicalRequest t = new TechnicalRequest();
                    t.setTicketId(rs.getInt("ticket_id"));
                    t.setTicketCode(rs.getString("ticket_code"));
                    t.setEnterpriseId(rs.getInt("enterprise_id"));
                    int contractId = rs.getInt("contract_id");
                    t.setContractId(rs.wasNull() ? null : contractId);
                    t.setTicketType(rs.getString("ticket_type"));
                    t.setPriority(rs.getString("priority"));
                    t.setReceptionChannel(rs.getString("reception_channel"));
                    t.setAssignedTechnicianId(rs.getInt("assigned_technician_id"));
                    t.setCreatedBy(rs.getInt("created_by"));
                    t.setCreatedDate(rs.getDate("created_date"));
                    t.setDescription(rs.getString("description"));
                    t.setWarranty(rs.getBoolean("is_warranty"));
                    t.setStatus(rs.getString("status"));
                    result.add(t);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van ticket theo khach hang (enterpriseId={})", enterpriseId, ex);
        }
        return result;
    }

    /** Lấy danh sách phiếu hỗ trợ có phân trang + lọc, phục vụ listTicket.jsp. */
    public List<TechnicalRequest> findAll(int page, int pageSize, String keyword, String statusFilter, String priorityFilter) {
        List<TechnicalRequest> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, statusFilter, priorityFilter);
        sql.append(" ORDER BY t.ticket_id DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(Math.max(0, (page - 1) * pageSize));

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van danh sach phieu ho tro", ex);
        }
        return result;
    }

    /** Đếm tổng số phiếu hỗ trợ thoả điều kiện lọc, phục vụ phân trang. */
    public int countAll(String keyword, String statusFilter, String priorityFilter) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM technicalrequests t LEFT JOIN enterprises e ON t.enterprise_id = e.enterprise_id ");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, statusFilter, priorityFilter);

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi dem so luong phieu ho tro", ex);
        }
        return 0;
    }

    /** Đếm số phiếu theo từng trạng thái + số phiếu ưu tiên khẩn cấp, phục vụ dải KPI ở đầu trang danh sách. */
    public Map<String, Integer> countStatusSummary() {
        Map<String, Integer> summary = new HashMap<>();
        summary.put(STATUS_NEW, 0);
        summary.put(STATUS_IN_PROGRESS, 0);
        summary.put(STATUS_CLOSED, 0);
        summary.put("Khẩn cấp", 0);

        String sql =
            "SELECT " +
            "  SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS new_count, " +
            "  SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS progress_count, " +
            "  SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS closed_count, " +
            "  SUM(CASE WHEN priority = 'Khẩn cấp' THEN 1 ELSE 0 END) AS urgent_count " +
            "FROM technicalrequests WHERE is_deleted = 0";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, STATUS_NEW);
            ps.setString(2, STATUS_IN_PROGRESS);
            ps.setString(3, STATUS_CLOSED);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    summary.put(STATUS_NEW, rs.getInt("new_count"));
                    summary.put(STATUS_IN_PROGRESS, rs.getInt("progress_count"));
                    summary.put(STATUS_CLOSED, rs.getInt("closed_count"));
                    summary.put("Khẩn cấp", rs.getInt("urgent_count"));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi thong ke trang thai phieu ho tro", ex);
        }
        return summary;
    }

    /** Lấy top N phiếu chưa đóng cần chú ý, ưu tiên khẩn cấp trước rồi tới phiếu tạo lâu nhất -- phục vụ dashboard. */
    public List<TechnicalRequest> findNeedingAttention(int limit) {
        List<TechnicalRequest> result = new ArrayList<>();
        String sql = SELECT_BASE +
            "WHERE t.is_deleted = 0 AND t.status <> ? " +
            "ORDER BY FIELD(t.priority, 'Khẩn cấp', 'Cao', 'Bình thường', 'Thấp'), t.created_date ASC LIMIT ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, STATUS_CLOSED);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van phieu can chu y", ex);
        }
        return result;
    }

    /** Đếm số phiếu chưa đóng có SLA đã quá hạn hoặc còn dưới 24h -- phục vụ dashboard. */
    public int countOverdueOrDueSoon() {
        String sql = "SELECT COUNT(*) FROM technicalrequests " +
                     "WHERE is_deleted = 0 AND status <> ? " +
                     "AND sla_deadline IS NOT NULL AND sla_deadline <= DATE_ADD(NOW(), INTERVAL 24 HOUR)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, STATUS_CLOSED);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi dem phieu sap tre han", ex);
        }
        return 0;
    }

    /**
     * Như countOverdueOrDueSoon(), nhưng trả về danh sách đầy đủ thay vì chỉ
     * đếm -- phục vụ NotificationScheduler tạo thông báo nhắc từng kỹ thuật
     * viên được giao phiếu sắp/đã quá hạn SLA.
     */
    public List<TechnicalRequest> findOverdueOrDueSoon() {
        List<TechnicalRequest> result = new ArrayList<>();
        String sql = SELECT_BASE +
                     "WHERE t.is_deleted = 0 AND t.status <> ? " +
                     "AND t.sla_deadline IS NOT NULL AND t.sla_deadline <= DATE_ADD(NOW(), INTERVAL 24 HOUR)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, STATUS_CLOSED);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van phieu sap tre han", ex);
        }
        return result;
    }

    /** Lấy chi tiết 1 phiếu hỗ trợ theo ID, đã join khách hàng/hợp đồng/kỹ thuật viên/người tạo. Trả về null nếu không tồn tại. */
    public TechnicalRequest findById(int ticketId) {
        String sql = SELECT_BASE + "WHERE t.ticket_id = ? AND t.is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van chi tiet phieu ho tro (ticketId={})", ticketId, ex);
        }
        return null;
    }

    /** Sinh mã phiếu hỗ trợ tiếp theo dạng TK-0001, TK-0002, ... */
    public String generateNextTicketCode() {
        String sql = "SELECT ticket_code FROM technicalrequests ORDER BY ticket_id DESC LIMIT 1";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int nextNumber = 1;
            if (rs.next()) {
                String lastCode = rs.getString("ticket_code");
                String digits = lastCode.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    nextNumber = Integer.parseInt(digits) + 1;
                }
            }
            return String.format("TK-%04d", nextNumber);
        } catch (SQLException ex) {
            LOG.error("Loi sinh ma phieu ho tro", ex);
            return null;
        }
    }

    /** Thử lại tối đa bao nhiêu lần khi ticket_code sinh ra bị trùng (xem insert()). */
    private static final int MAX_CODE_GEN_ATTEMPTS = 5;

    /** Thêm phiếu hỗ trợ mới. Trả về ticket_id vừa tạo, hoặc -1 nếu lỗi. */
    public int insert(TechnicalRequest t) {
        String sql = "INSERT INTO technicalrequests " +
                "(ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, " +
                " assigned_technician_id, created_by, created_date, description, is_warranty, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // ticket_code sinh từ generateNextTicketCode() (đọc mã lớn nhất hiện có rồi
        // +1) có thể trùng nếu 2 request tạo phiếu gần như đồng thời cùng đọc được
        // "mã lớn nhất" giống nhau -- cột ticket_code có UNIQUE KEY (xem
        // db/schema.sql) nên lần INSERT bị trùng sẽ bị DB từ chối thay vì âm thầm
        // ghi đè; thử sinh mã mới và INSERT lại vài lần thay vì báo lỗi ngay, để
        // người dùng không phải tự bấm lưu lại.
        for (int attempt = 1; attempt <= MAX_CODE_GEN_ATTEMPTS; attempt++) {
            try (Connection conn = DBContext.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, t.getTicketCode());
                ps.setInt(2, t.getEnterpriseId());
                setNullableInt(ps, 3, t.getContractId());
                ps.setString(4, t.getTicketType());
                ps.setString(5, t.getPriority());
                ps.setString(6, t.getReceptionChannel());
                setNullableTimestamp(ps, 7, t.getSlaDeadline());
                ps.setInt(8, t.getAssignedTechnicianId());
                ps.setInt(9, t.getCreatedBy());
                ps.setDate(10, t.getCreatedDate());
                ps.setString(11, t.getDescription());
                ps.setBoolean(12, t.isWarranty());
                ps.setString(13, t.getStatus());

                int affected = ps.executeUpdate();
                if (affected == 0) {
                    return -1;
                }
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            } catch (SQLException ex) {
                if (isDuplicateKeyError(ex, "ticket_code") && attempt < MAX_CODE_GEN_ATTEMPTS) {
                    t.setTicketCode(generateNextTicketCode());
                    continue;
                }
                LOG.error("Loi them phieu ho tro (ticketCode={})", t.getTicketCode(), ex);
                return -1;
            }
        }
        return -1;
    }

    /**
     * Cập nhật phiếu hỗ trợ đang có, và nếu trạng thái đổi thì ghi kèm 1 dòng
     * vào technicalrequesthistory -- TRONG CÙNG MỘT TRANSACTION.
     *
     * Cùng transaction chứ không phải hai lời gọi rời nhau, vì hai thứ đó phải
     * đúng hoặc sai cùng nhau: phiếu đã sang "Đã đóng" mà dòng lịch sử ghi hụt
     * thì dòng thời gian xử lý nói dối, và không ai phát hiện ra.
     *
     * Trạng thái cũ đọc NGAY TRONG transaction (SELECT ... FOR UPDATE) thay vì
     * nhận từ bên gọi: controller đọc phiếu ở một thời điểm trước đó, nên nếu
     * hai người cùng sửa một phiếu thì "trạng thái cũ" mà controller biết có
     * thể đã lỗi thời, và lịch sử sẽ ghi lại một bước chuyển chưa từng xảy ra.
     *
     * @param changedBy    user_id người đang thao tác -- khoá ngoại sang users.
     * @param internalNote ghi chú nội bộ cho lần đổi trạng thái này, có thể null.
     * @return true nếu cập nhật thành công (và lịch sử, nếu có, đã ghi xong).
     */
    public boolean update(TechnicalRequest t, int changedBy, String internalNote) {
        String sql = "UPDATE technicalrequests SET " +
                "enterprise_id = ?, contract_id = ?, ticket_type = ?, priority = ?, reception_channel = ?, sla_deadline = ?, " +
                "assigned_technician_id = ?, description = ?, is_warranty = ?, status = ?, " +
                "resolution_summary = ?, resolved_at = ? " +
                "WHERE ticket_id = ? AND is_deleted = 0";

        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            // Xem ContractDAO.insertProducts để hiểu vì sao dùng cờ committed
            // thay vì 2 khối catch tách rời.
            boolean committed = false;
            try {
                String previousStatus = lockAndReadStatus(conn, t.getTicketId());
                if (previousStatus == null) {
                    return false; // phiếu không còn: id sai, hoặc vừa bị xoá mềm
                }

                int affected;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, t.getEnterpriseId());
                    setNullableInt(ps, 2, t.getContractId());
                    ps.setString(3, t.getTicketType());
                    ps.setString(4, t.getPriority());
                    ps.setString(5, t.getReceptionChannel());
                    setNullableTimestamp(ps, 6, t.getSlaDeadline());
                    ps.setInt(7, t.getAssignedTechnicianId());
                    ps.setString(8, t.getDescription());
                    ps.setBoolean(9, t.isWarranty());
                    ps.setString(10, t.getStatus());
                    ps.setString(11, t.getResolutionSummary());
                    setNullableTimestamp(ps, 12, t.getResolvedAt());
                    ps.setInt(13, t.getTicketId());
                    affected = ps.executeUpdate();
                }
                if (affected == 0) {
                    return false;
                }

                if (!previousStatus.equals(t.getStatus())) {
                    insertStatusChange(conn, t.getTicketId(), previousStatus, t.getStatus(), changedBy, internalNote);
                }

                conn.commit();
                committed = true;
                return true;
            } finally {
                if (!committed) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        LOG.error("Loi rollback khi cap nhat phieu ho tro (ticketId={})", t.getTicketId(), rollbackEx);
                    }
                }
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException autoCommitEx) {
                    LOG.error("Loi reset autocommit sau khi cap nhat phieu ho tro (ticketId={})",
                            t.getTicketId(), autoCommitEx);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi cap nhat phieu ho tro (ticketId={}, ticketCode={})",
                    t.getTicketId(), t.getTicketCode(), ex);
            return false;
        }
    }

    /**
     * Đọc trạng thái hiện tại và giữ khoá dòng đó tới hết transaction. Trả về
     * null nếu phiếu không tồn tại hoặc đã bị xoá mềm.
     */
    private String lockAndReadStatus(Connection conn, int ticketId) throws SQLException {
        String sql = "SELECT status FROM technicalrequests WHERE ticket_id = ? AND is_deleted = 0 FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("status") : null;
            }
        }
    }

    /** Ghi 1 dòng lịch sử đổi trạng thái. Ném ngoại lệ ra ngoài để transaction rollback. */
    private void insertStatusChange(Connection conn, int ticketId, String fromStatus, String toStatus,
            int changedBy, String internalNote) throws SQLException {
        String sql = "INSERT INTO technicalrequesthistory " +
                "(ticket_id, from_status, to_status, changed_by, internal_note) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            ps.setString(2, fromStatus);
            ps.setString(3, toStatus);
            ps.setInt(4, changedBy);
            ps.setString(5, internalNote);
            ps.executeUpdate();
        }
    }

    /** Lịch sử đổi trạng thái của 1 phiếu, mới nhất trước, kèm tên người đổi. */
    public List<TechnicalRequestHistory> findHistoryByTicketId(int ticketId) {
        List<TechnicalRequestHistory> result = new ArrayList<>();
        String sql = "SELECT h.history_id, h.ticket_id, h.from_status, h.to_status, h.changed_by, " +
                "       h.changed_at, h.internal_note, " +
                "       u.last_name AS changer_last_name, u.middle_name AS changer_middle_name, " +
                "       u.first_name AS changer_first_name " +
                "FROM technicalrequesthistory h " +
                "JOIN users u ON u.user_id = h.changed_by " +
                "WHERE h.ticket_id = ? " +
                "ORDER BY h.changed_at DESC, h.history_id DESC";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TechnicalRequestHistory h = new TechnicalRequestHistory();
                    h.setHistoryId(rs.getInt("history_id"));
                    h.setTicketId(rs.getInt("ticket_id"));
                    h.setFromStatus(rs.getString("from_status"));
                    h.setToStatus(rs.getString("to_status"));
                    h.setChangedBy(rs.getInt("changed_by"));
                    h.setChangedAt(rs.getTimestamp("changed_at"));
                    h.setInternalNote(rs.getString("internal_note"));

                    User changer = new User();
                    changer.setLastName(rs.getString("changer_last_name"));
                    changer.setMiddleName(rs.getString("changer_middle_name"));
                    changer.setFirstName(rs.getString("changer_first_name"));
                    h.setChangedByUser(changer);

                    result.add(h);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi doc lich su trang thai phieu ho tro (ticketId={})", ticketId, ex);
        }
        return result;
    }

    /** Chỉ cho xoá phiếu chưa có ai xử lý dở dang (khác trạng thái "Đang xử lý"). */
    public boolean canDelete(int ticketId) {
        String sql = "SELECT status FROM technicalrequests WHERE ticket_id = ? AND is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return !STATUS_IN_PROGRESS.equals(rs.getString("status"));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi kiem tra dieu kien xoa phieu ho tro (ticketId={})", ticketId, ex);
        }
        return false;
    }

    /** Xoá mềm phiếu hỗ trợ (is_deleted = 1). Gọi canDelete() trước để áp business rule. */
    public boolean softDelete(int ticketId) {
        String sql = "UPDATE technicalrequests SET is_deleted = 1 WHERE ticket_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            LOG.error("Loi xoa phieu ho tro (ticketId={})", ticketId, ex);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Helpers riêng
    // ------------------------------------------------------------------

    private void appendFilters(StringBuilder sql, List<Object> params, String keyword, String statusFilter, String priorityFilter) {
        List<String> conditions = new ArrayList<>();
        conditions.add("t.is_deleted = 0");

        if (keyword != null && !keyword.trim().isEmpty()) {
            conditions.add("(t.ticket_code LIKE ? OR t.description LIKE ? OR e.enterprise_name LIKE ?)");
            String likeValue = "%" + keyword.trim() + "%";
            params.add(likeValue);
            params.add(likeValue);
            params.add(likeValue);
        }
        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            conditions.add("t.status = ?");
            params.add(statusFilter);
        }
        if (priorityFilter != null && !priorityFilter.trim().isEmpty()) {
            conditions.add("t.priority = ?");
            params.add(priorityFilter);
        }

        sql.append("WHERE ").append(String.join(" AND ", conditions));
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    /** true nếu ex là lỗi trùng UNIQUE KEY của MySQL (error 1062) trên đúng cột keyColumn. */
    private boolean isDuplicateKeyError(SQLException ex, String keyColumn) {
        return ex.getErrorCode() == 1062 && ex.getMessage() != null && ex.getMessage().contains(keyColumn);
    }

    private void setNullableTimestamp(PreparedStatement ps, int index, Timestamp value) throws SQLException {
        if (value != null) {
            ps.setTimestamp(index, value);
        } else {
            ps.setNull(index, Types.TIMESTAMP);
        }
    }

    private TechnicalRequest mapRow(ResultSet rs) throws SQLException {
        TechnicalRequest t = new TechnicalRequest();
        t.setTicketId(rs.getInt("ticket_id"));
        t.setTicketCode(rs.getString("ticket_code"));
        t.setEnterpriseId(rs.getInt("enterprise_id"));
        int contractId = rs.getInt("contract_id");
        t.setContractId(rs.wasNull() ? null : contractId);
        t.setTicketType(rs.getString("ticket_type"));
        t.setPriority(rs.getString("priority"));
        t.setReceptionChannel(rs.getString("reception_channel"));
        t.setSlaDeadline(rs.getTimestamp("sla_deadline"));
        t.setAssignedTechnicianId(rs.getInt("assigned_technician_id"));
        t.setCreatedBy(rs.getInt("created_by"));
        t.setCreatedDate(rs.getDate("created_date"));
        t.setDescription(rs.getString("description"));
        t.setWarranty(rs.getBoolean("is_warranty"));
        t.setStatus(rs.getString("status"));
        t.setResolutionSummary(rs.getString("resolution_summary"));
        t.setResolvedAt(rs.getTimestamp("resolved_at"));
        t.setCreatedAt(rs.getTimestamp("created_at"));
        t.setUpdatedAt(rs.getTimestamp("updated_at"));
        t.setDeleted(rs.getBoolean("is_deleted"));

        String enterpriseName = rs.getString("enterprise_name");
        if (enterpriseName != null) {
            Enterprise e = new Enterprise();
            e.setEnterpriseId(t.getEnterpriseId());
            e.setEnterpriseName(enterpriseName);
            t.setEnterprise(e);
        }

        String contractCode = rs.getString("contract_code");
        if (contractCode != null && t.getContractId() != null) {
            Contract c = new Contract();
            c.setContractId(t.getContractId());
            c.setContractCode(contractCode);
            t.setContract(c);
        }

        String techLastName = rs.getString("tech_last_name");
        if (techLastName != null) {
            User tech = new User();
            tech.setUserId(t.getAssignedTechnicianId());
            tech.setLastName(techLastName);
            tech.setMiddleName(rs.getString("tech_middle_name"));
            tech.setFirstName(rs.getString("tech_first_name"));
            t.setAssignedTechnician(tech);
        }

        String creatorLastName = rs.getString("creator_last_name");
        if (creatorLastName != null) {
            User creator = new User();
            creator.setUserId(t.getCreatedBy());
            creator.setLastName(creatorLastName);
            creator.setMiddleName(rs.getString("creator_middle_name"));
            creator.setFirstName(rs.getString("creator_first_name"));
            t.setCreatedByUser(creator);
        }

        return t;
    }
}
