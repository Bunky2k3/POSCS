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
import poscs.model.Contract;
import poscs.model.Enterprise;
import poscs.model.TechnicalRequest;
import poscs.model.User;

/**
 * DAO cho phiếu hỗ trợ kỹ thuật (bảng technicalrequests). Chưa xử lý
 * technicalrequestdevices (thiết bị lỗi) và technicalrequesthistory (lịch
 * sử đổi trạng thái) -- 2 bảng con đó chưa có model/DAO nào, thuộc phạm vi
 * khác.
 */
public class TechnicalSupportTicketDAO {

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
            System.err.println("--- LOI TRUY VAN TICKET THEO KHACH HANG ---");
            ex.printStackTrace();
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
            System.err.println("--- LOI TRUY VAN DANH SACH PHIEU HO TRO ---");
            ex.printStackTrace();
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
            System.err.println("--- LOI DEM SO LUONG PHIEU HO TRO ---");
            ex.printStackTrace();
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
            System.err.println("--- LOI THONG KE TRANG THAI PHIEU HO TRO ---");
            ex.printStackTrace();
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
            System.err.println("--- LOI TRUY VAN PHIEU CAN CHU Y ---");
            ex.printStackTrace();
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
            System.err.println("--- LOI DEM PHIEU SAP TRE HAN ---");
            ex.printStackTrace();
        }
        return 0;
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
            System.err.println("--- LOI TRUY VAN CHI TIET PHIEU HO TRO ---");
            ex.printStackTrace();
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
            System.err.println("--- LOI SINH MA PHIEU HO TRO ---");
            ex.printStackTrace();
            return null;
        }
    }

    /** Thêm phiếu hỗ trợ mới. Trả về ticket_id vừa tạo, hoặc -1 nếu lỗi. */
    public int insert(TechnicalRequest t) {
        String sql = "INSERT INTO technicalrequests " +
                "(ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, " +
                " assigned_technician_id, created_by, created_date, description, is_warranty, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
            System.err.println("--- LOI THEM PHIEU HO TRO ---");
            ex.printStackTrace();
        }
        return -1;
    }

    /** Cập nhật phiếu hỗ trợ đang có (gồm cả đổi trạng thái xử lý). Trả về true nếu cập nhật thành công. */
    public boolean update(TechnicalRequest t) {
        String sql = "UPDATE technicalrequests SET " +
                "enterprise_id = ?, contract_id = ?, ticket_type = ?, priority = ?, reception_channel = ?, sla_deadline = ?, " +
                "assigned_technician_id = ?, description = ?, is_warranty = ?, status = ?, " +
                "resolution_summary = ?, resolved_at = ? " +
                "WHERE ticket_id = ? AND is_deleted = 0";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
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
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            System.err.println("--- LOI CAP NHAT PHIEU HO TRO ---");
            ex.printStackTrace();
            return false;
        }
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
            System.err.println("--- LOI KIEM TRA DIEU KIEN XOA PHIEU HO TRO ---");
            ex.printStackTrace();
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
            System.err.println("--- LOI XOA PHIEU HO TRO ---");
            ex.printStackTrace();
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
