package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.model.ContractHistory;
import poscs.model.User;

/**
 * Data Access Object chuyên biệt cho nhật ký trạng thái và sự kiện hợp đồng
 * (bảng {@code contract_history} - [UC-45]).
 *
 * <p>Tách từ {@link ContractDAO} (Task 4.3) nhằm phân tách trách nhiệm lưu vết kiểm toán:
 * <ul>
 *   <li>Quản lý toàn bộ lịch sử biến động hợp đồng (khởi tạo, sửa đổi, hàng hoá, kỳ thanh toán,
 *       bàn giao, phụ lục, nối/gỡ liên kết, tài liệu, chữa sai sót, huỷ bản ghi).</li>
 *   <li>Ghi nhận các bước chuyển trên trục tiến độ (Nháp → Đã ký → Đã thanh lý / Chấm dứt sớm).</li>
 *   <li>Bắt buộc ghi nhật ký trong transaction đang mở của bên gọi (NFR-19) để bảo đảm
 *       tính nhất quán và toàn vẹn dữ liệu (cố ý không tự mở connection riêng).</li>
 * </ul>
 */
public class ContractHistoryDAO {

    private static final Logger LOG = LoggerFactory.getLogger(ContractHistoryDAO.class);

    /** Khớp varchar(500) của contract_history.detail -- xem {@link #truncate}. */
    public static final int DETAIL_MAX_LENGTH = 500;

    /**
     * Cắt chuỗi cho vừa cột detail thay vì để DB từ chối cả câu INSERT và mất luôn dòng nhật ký.
     *
     * @param text chuỗi cần cắt
     * @param max độ dài tối đa cho phép
     * @return chuỗi đã cắt gọn kèm ký tự ellipsis "…" nếu vượt quá giới hạn; trả chuỗi rỗng nếu text null hoặc max <= 0
     */
    public static String truncate(String text, int max) {
        if (text == null || max <= 0) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return max == 1 ? "…" : text.substring(0, max - 1) + "…";
    }

    /**
     * Ánh xạ trạng thái tiến độ đích sang loại sự kiện tương ứng của {@link ContractHistory}.
     *
     * @param toStatus trạng thái tiến độ đích hợp lệ
     * @return tên sự kiện vòng đời
     * @throws IllegalArgumentException nếu toStatus không hợp lệ hoặc null
     */
    public static String eventTypeOf(String toStatus) {
        if (ContractDAO.PROGRESS_SIGNED.equals(toStatus)) {
            return ContractHistory.EVENT_SIGNED;
        }
        if (ContractDAO.PROGRESS_LIQUIDATED.equals(toStatus)) {
            return ContractHistory.EVENT_LIQUIDATED;
        }
        if (ContractDAO.PROGRESS_TERMINATED.equals(toStatus)) {
            return ContractHistory.EVENT_TERMINATED;
        }
        throw new IllegalArgumentException("Invalid target progress status: " + toStatus);
    }

    /**
     * Ghi 1 dòng nhật ký bằng connection ĐANG MỞ của bên gọi -- cố ý không tự mở connection riêng.
     * Ném {@link SQLException} ra ngoài để transaction của bên gọi rollback khi ghi thất bại (NFR-19).
     *
     * @param conn kết nối CSDL đang mở trong transaction
     * @param contractId ID hợp đồng
     * @param eventType loại sự kiện (ContractHistory.EVENT_*)
     * @param detail mô tả chi tiết sự kiện (được tự động cắt theo DETAIL_MAX_LENGTH)
     * @param changedBy ID người thực hiện
     * @param note lý do hoặc ghi chú người dùng nhập (có thể null)
     * @throws SQLException nếu tham số không hợp lệ hoặc thao tác CSDL gặp lỗi
     */
    public void insertHistory(Connection conn, int contractId, String eventType, String detail,
            int changedBy, String note) throws SQLException {
        if (conn == null || contractId <= 0 || changedBy <= 0
                || eventType == null || eventType.trim().isEmpty()) {
            throw new SQLException("Invalid contract history parameters");
        }
        String sql = "INSERT INTO contract_history "
                + "(contract_id, event_type, detail, changed_by, note) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            ps.setString(2, eventType);
            ps.setString(3, truncate(detail, DETAIL_MAX_LENGTH));
            ps.setInt(4, changedBy);
            ps.setString(5, note);
            ps.executeUpdate();
        }
    }

    /**
     * Ghi 1 dòng nhật ký chuyển trạng thái tiến độ bằng connection ĐANG MỞ của bên gọi.
     * Có from_status và to_status để phục vụ việc tái hiện dòng thời gian vòng đời.
     *
     * @param conn kết nối CSDL đang mở trong transaction
     * @param contractId ID hợp đồng
     * @param fromStatus trạng thái trước khi chuyển
     * @param toStatus trạng thái đích
     * @param changedBy ID người thực hiện
     * @param note lý do chuyển trạng thái (có thể null)
     * @throws SQLException nếu tham số không hợp lệ hoặc thao tác CSDL gặp lỗi
     */
    public void insertStatusChange(Connection conn, int contractId, String fromStatus, String toStatus,
            int changedBy, String note) throws SQLException {
        if (conn == null || contractId <= 0 || changedBy <= 0
                || fromStatus == null || fromStatus.trim().isEmpty()
                || toStatus == null || toStatus.trim().isEmpty()) {
            throw new SQLException("Invalid contract status change parameters");
        }
        String sql = "INSERT INTO contract_history "
                + "(contract_id, event_type, detail, from_status, to_status, changed_by, note) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            ps.setString(2, eventTypeOf(toStatus));
            ps.setString(3, truncate(fromStatus + " → " + toStatus, DETAIL_MAX_LENGTH));
            ps.setString(4, fromStatus);
            ps.setString(5, toStatus);
            ps.setInt(6, changedBy);
            ps.setString(7, note);
            ps.executeUpdate();
        }
    }

    /**
     * Lấy toàn bộ nhật ký thay đổi của 1 hợp đồng trên Connection đang mở.
     * Sắp xếp mới nhất trước (changed_at DESC, history_id DESC), kèm họ tên người thực hiện.
     *
     * @param conn kết nối CSDL
     * @param contractId ID hợp đồng cần truy vấn
     * @return danh sách các dòng nhật ký (không bao giờ trả về null)
     * @throws SQLException nếu có lỗi SQL
     */
    public List<ContractHistory> findHistoryByContractId(Connection conn, int contractId) throws SQLException {
        List<ContractHistory> result = new ArrayList<>();
        if (conn == null || contractId <= 0) {
            return result;
        }
        String sql = "SELECT h.history_id, h.contract_id, h.event_type, h.detail, h.from_status, h.to_status, "
                + "       h.changed_by, h.changed_at, h.note, "
                + "       u.last_name AS changer_last_name, u.middle_name AS changer_middle_name, "
                + "       u.first_name AS changer_first_name "
                + "FROM contract_history h "
                + "JOIN users u ON u.user_id = h.changed_by "
                + "WHERE h.contract_id = ? "
                + "ORDER BY h.changed_at DESC, h.history_id DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToHistory(rs));
                }
            }
        }
        return result;
    }

    /**
     * Lấy toàn bộ nhật ký thay đổi của 1 hợp đồng (tự mở kết nối).
     * [UC-45] Cho phép Admin, Sales, Kỹ thuật xem chi tiết lịch sử hợp đồng.
     *
     * @param contractId ID hợp đồng
     * @return danh sách các dòng nhật ký (không bao giờ null)
     */
    public List<ContractHistory> findHistoryByContractId(int contractId) {
        if (contractId <= 0) {
            return new ArrayList<>();
        }
        try (Connection conn = DBContext.getConnection()) {
            return findHistoryByContractId(conn, contractId);
        } catch (SQLException ex) {
            LOG.error("Loi truy van nhat ky hop dong (contractId={})", contractId, ex);
            return new ArrayList<>();
        }
    }

    /**
     * Bí danh (alias) ngắn gọn cho {@link #findHistoryByContractId(int)}.
     *
     * @param contractId ID hợp đồng
     * @return danh sách các dòng nhật ký
     */
    public List<ContractHistory> findByContractId(int contractId) {
        return findHistoryByContractId(contractId);
    }

    /**
     * Helper ánh xạ 1 dòng ResultSet sang đối tượng {@link ContractHistory}.
     */
    private ContractHistory mapResultSetToHistory(ResultSet rs) throws SQLException {
        ContractHistory h = new ContractHistory();
        h.setHistoryId(rs.getInt("history_id"));
        h.setContractId(rs.getInt("contract_id"));
        h.setEventType(rs.getString("event_type"));
        h.setDetail(rs.getString("detail"));
        h.setFromStatus(rs.getString("from_status"));
        h.setToStatus(rs.getString("to_status"));
        h.setChangedBy(rs.getInt("changed_by"));
        h.setChangedAt(rs.getTimestamp("changed_at"));
        h.setNote(rs.getString("note"));

        User changer = new User();
        changer.setLastName(rs.getString("changer_last_name"));
        changer.setMiddleName(rs.getString("changer_middle_name"));
        changer.setFirstName(rs.getString("changer_first_name"));
        h.setChangedByUser(changer);

        return h;
    }
}
