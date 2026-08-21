package poscs.model;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;

public class Notification {
    private int notificationId;
    private int userId;
    private String title;
    private boolean read; // Ánh xạ với is_read (tinyint(1))
    private Timestamp createdAt;

    public Notification() {
    }

    public int getNotificationId() { return notificationId; }
    public void setNotificationId(int notificationId) { this.notificationId = notificationId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    /**
     * Thời gian tương đối kiểu "10 phút trước" / "Hôm qua" để hiển thị ở
     * chuông thông báo và trang danh sách -- tính ngay lúc getter được gọi
     * (JSP render), không lưu sẵn trong DB.
     */
    public String getRelativeTime() {
        if (createdAt == null) {
            return "";
        }
        long minutes = Duration.between(createdAt.toLocalDateTime(), LocalDateTime.now()).toMinutes();
        if (minutes < 1) {
            return "Vừa xong";
        }
        if (minutes < 60) {
            return minutes + " phút trước";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " giờ trước";
        }
        long days = hours / 24;
        if (days == 1) {
            return "Hôm qua";
        }
        if (days < 7) {
            return days + " ngày trước";
        }
        return new SimpleDateFormat("dd/MM/yyyy").format(createdAt);
    }
}
