package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.model.ChangeRequest;
import poscs.model.User;

/**
 * DAO cho yêu cầu thay đổi (bảng change_requests) -- đường để cấp dưới xin cấp
 * trên sửa dữ liệu Khách hàng/Hợp đồng. Xem ghi chú đầu V17 và PERMISSIONS.md.
 */
public class ChangeRequestDAO {

    private static final Logger LOG = LoggerFactory.getLogger(ChangeRequestDAO.class);

    private static final String SELECT_BASE =
        "SELECT c.request_id, c.resource_type, c.intent, c.target_id, c.proposed_content, c.reason, " +
        "       c.requested_by, c.status, c.reviewed_by, c.review_note, c.reviewed_at, c.created_at, " +
        "       req.last_name AS req_last_name, req.middle_name AS req_middle_name, req.first_name AS req_first_name, " +
        "       rev.last_name AS rev_last_name, rev.middle_name AS rev_middle_name, rev.first_name AS rev_first_name " +
        "FROM change_requests c " +
        "LEFT JOIN users req ON c.requested_by = req.user_id " +
        "LEFT JOIN users rev ON c.reviewed_by = rev.user_id ";

    /** Thêm yêu cầu mới, trả về request_id vừa tạo hoặc -1 nếu lỗi. */
    public int insert(ChangeRequest r) {
        String sql = "INSERT INTO change_requests " +
                "(resource_type, intent, target_id, proposed_content, reason, requested_by, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.getResourceType());
            ps.setString(2, r.getIntent());
            setNullableInt(ps, 3, r.getTargetId());
            ps.setString(4, r.getProposedContent());
            ps.setString(5, r.getReason());
            ps.setInt(6, r.getRequestedBy());
            ps.setString(7, ChangeRequest.STATUS_PENDING);
            if (ps.executeUpdate() == 0) {
                return -1;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException ex) {
            LOG.error("Loi them yeu cau thay doi (requestedBy={})", r.getRequestedBy(), ex);
            return -1;
        }
    }

    /**
     * Những yêu cầu mà {@code managerId} có quyền duyệt: do chính cấp dưới của
     * người đó gửi lên.
     *
     * Lọc bằng chiều CON -> CHA có sẵn trong users.manager_id, không cần bảng
     * phân công riêng. Ai không có cấp dưới nào thì nhận về danh sách rỗng --
     * đúng, chứ không phải lỗi.
     */
    public List<ChangeRequest> findForManager(int managerId, String statusFilter) {
        String sql = SELECT_BASE +
                "WHERE req.manager_id = ? " +
                (statusFilter != null ? "AND c.status = ? " : "") +
                "ORDER BY c.created_at DESC";
        List<Object> params = new ArrayList<>();
        params.add(managerId);
        if (statusFilter != null) {
            params.add(statusFilter);
        }
        return query(sql, params);
    }

    /** Toàn bộ yêu cầu trong hệ thống -- dành cho Admin. */
    public List<ChangeRequest> findAll(String statusFilter) {
        String sql = SELECT_BASE +
                (statusFilter != null ? "WHERE c.status = ? " : "") +
                "ORDER BY c.created_at DESC";
        List<Object> params = new ArrayList<>();
        if (statusFilter != null) {
            params.add(statusFilter);
        }
        return query(sql, params);
    }

    /** Những yêu cầu do chính {@code userId} gửi lên -- để họ theo dõi kết quả. */
    public List<ChangeRequest> findByRequester(int userId) {
        List<Object> params = new ArrayList<>();
        params.add(userId);
        return query(SELECT_BASE + "WHERE c.requested_by = ? ORDER BY c.created_at DESC", params);
    }

    public ChangeRequest findById(int requestId) {
        List<Object> params = new ArrayList<>();
        params.add(requestId);
        List<ChangeRequest> found = query(SELECT_BASE + "WHERE c.request_id = ?", params);
        return found.isEmpty() ? null : found.get(0);
    }

    /** Số yêu cầu đang chờ duyệt của cấp dưới người này -- để hiện chấm đỏ. */
    public int countPendingForManager(int managerId) {
        String sql = "SELECT COUNT(*) FROM change_requests c JOIN users req ON c.requested_by = req.user_id " +
                     "WHERE req.manager_id = ? AND c.status = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, managerId);
            ps.setString(2, ChangeRequest.STATUS_PENDING);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            LOG.error("Loi dem yeu cau cho duyet (managerId={})", managerId, ex);
            return 0;
        }
    }

    /**
     * Ghi quyết định duyệt/từ chối.
     *
     * Điều kiện {@code status = 'Chờ duyệt'} nằm ngay trong câu UPDATE chứ
     * không kiểm ở tầng trên rồi mới ghi: hai người cùng mở một yêu cầu thì
     * người bấm sau phải trượt, không được đè lên quyết định của người bấm
     * trước. Trả về false khi đó, để màn hình báo lại.
     *
     * @return true nếu ghi được quyết định (tức yêu cầu vẫn đang chờ).
     */
    public boolean review(int requestId, String newStatus, int reviewerId, String note) {
        String sql = "UPDATE change_requests SET status = ?, reviewed_by = ?, review_note = ?, reviewed_at = ? " +
                     "WHERE request_id = ? AND status = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, reviewerId);
            ps.setString(3, note);
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.setInt(5, requestId);
            ps.setString(6, ChangeRequest.STATUS_PENDING);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            LOG.error("Loi ghi quyet dinh duyet (requestId={})", requestId, ex);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private List<ChangeRequest> query(String sql, List<Object> params) {
        List<ChangeRequest> result = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van yeu cau thay doi", ex);
        }
        return result;
    }

    private ChangeRequest mapRow(ResultSet rs) throws SQLException {
        ChangeRequest r = new ChangeRequest();
        r.setRequestId(rs.getInt("request_id"));
        r.setResourceType(rs.getString("resource_type"));
        r.setIntent(rs.getString("intent"));
        int targetId = rs.getInt("target_id");
        r.setTargetId(rs.wasNull() ? null : targetId);
        r.setProposedContent(rs.getString("proposed_content"));
        r.setReason(rs.getString("reason"));
        r.setRequestedBy(rs.getInt("requested_by"));
        r.setStatus(rs.getString("status"));
        int reviewedBy = rs.getInt("reviewed_by");
        r.setReviewedBy(rs.wasNull() ? null : reviewedBy);
        r.setReviewNote(rs.getString("review_note"));
        r.setReviewedAt(rs.getTimestamp("reviewed_at"));
        r.setCreatedAt(rs.getTimestamp("created_at"));

        String reqLastName = rs.getString("req_last_name");
        if (reqLastName != null) {
            User requester = new User();
            requester.setUserId(r.getRequestedBy());
            requester.setLastName(reqLastName);
            requester.setMiddleName(rs.getString("req_middle_name"));
            requester.setFirstName(rs.getString("req_first_name"));
            r.setRequester(requester);
        }
        String revLastName = rs.getString("rev_last_name");
        if (revLastName != null && r.getReviewedBy() != null) {
            User reviewer = new User();
            reviewer.setUserId(r.getReviewedBy());
            reviewer.setLastName(revLastName);
            reviewer.setMiddleName(rs.getString("rev_middle_name"));
            reviewer.setFirstName(rs.getString("rev_first_name"));
            r.setReviewer(reviewer);
        }
        return r;
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }
}
