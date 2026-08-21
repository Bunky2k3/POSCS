package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import poscs.model.Notification;

/**
 * DAO cho bảng notifications -- thông báo trong ứng dụng gắn với 1 người
 * dùng cụ thể (chuông thông báo ở topbar.jsp + trang danh sách đầy đủ
 * notifications.jsp, xem NotificationController).
 *
 * Bảng chỉ lưu tiêu đề (không có link/loại tham chiếu tới đối tượng gốc),
 * nên hiện tại chưa có nơi nào tự động ghi thông báo mới -- DAO này mới chỉ
 * phục vụ phần đọc/đánh dấu đã đọc.
 */
public class NotificationDAO {

    public int countUnread(int userId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI DEM THONG BAO CHUA DOC ---");
            ex.printStackTrace();
            return 0;
        }
    }

    /** N thông báo mới nhất (đã đọc lẫn chưa đọc) -- dùng cho dropdown chuông. */
    public List<Notification> findRecentByUser(int userId, int limit) {
        return findByUser(userId, limit);
    }

    /** Toàn bộ thông báo của 1 người dùng -- dùng cho trang "Xem tất cả". */
    public List<Notification> findAllByUser(int userId) {
        return findByUser(userId, -1);
    }

    private List<Notification> findByUser(int userId, int limit) {
        List<Notification> result = new ArrayList<>();
        String sql = "SELECT notification_id, user_id, title, is_read, created_at " +
                "FROM notifications WHERE user_id = ? ORDER BY created_at DESC, notification_id DESC" +
                (limit > 0 ? " LIMIT ?" : "");
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            if (limit > 0) {
                ps.setInt(2, limit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI DOC DANH SACH THONG BAO ---");
            ex.printStackTrace();
        }
        return result;
    }

    /** Đánh dấu 1 thông báo đã đọc -- kèm điều kiện user_id để 1 người không thể đánh dấu hộ thông báo của người khác. */
    public boolean markAsRead(int notificationId, int userId) {
        String sql = "UPDATE notifications SET is_read = 1 WHERE notification_id = ? AND user_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, notificationId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            System.err.println("--- LOI DANH DAU DA DOC THONG BAO ---");
            ex.printStackTrace();
            return false;
        }
    }

    public int markAllAsRead(int userId) {
        String sql = "UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("--- LOI DANH DAU TAT CA THONG BAO DA DOC ---");
            ex.printStackTrace();
            return 0;
        }
    }

    private Notification map(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setNotificationId(rs.getInt("notification_id"));
        n.setUserId(rs.getInt("user_id"));
        n.setTitle(rs.getString("title"));
        n.setRead(rs.getBoolean("is_read"));
        n.setCreatedAt(rs.getTimestamp("created_at"));
        return n;
    }
}
