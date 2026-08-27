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
 * ref_type/ref_id (vd "contract_expiring"/contract_id, "ticket_sla"/ticket_id)
 * không hiển thị ra UI -- chỉ dùng nội bộ để NotificationScheduler biết đã
 * tạo thông báo cho 1 hợp đồng/phiếu cụ thể chưa, tránh tạo trùng lặp mỗi
 * lần job chạy lại.
 */
public class NotificationDAO {

    /** Tạo 1 thông báo mới. refType/refId có thể null (thông báo không gắn với đối tượng nguồn cụ thể). */
    public boolean insert(int userId, String title, String refType, Integer refId) {
        String sql = "INSERT INTO notifications (user_id, title, ref_type, ref_id) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, title);
            ps.setString(3, refType);
            if (refId != null) {
                ps.setInt(4, refId);
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            System.err.println("--- LOI TAO THONG BAO ---");
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * true nếu đã có sẵn 1 thông báo cho đúng (userId, refType, refId) này rồi
     * (bất kể đã đọc hay chưa) -- dùng để NotificationScheduler không tạo
     * trùng lặp thông báo cho cùng 1 hợp đồng/phiếu mỗi lần job chạy lại.
     */
    public boolean existsForUserAndRef(int userId, String refType, int refId) {
        String sql = "SELECT 1 FROM notifications WHERE user_id = ? AND ref_type = ? AND ref_id = ? LIMIT 1";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, refType);
            ps.setInt(3, refId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI KIEM TRA THONG BAO DA TON TAI ---");
            ex.printStackTrace();
            // Lỗi đọc thì coi như "đã có" để tránh spam thông báo trùng lặp
            // nếu đây là lỗi tạm thời (an toàn hơn false -> tạo trùng).
            return true;
        }
    }

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
