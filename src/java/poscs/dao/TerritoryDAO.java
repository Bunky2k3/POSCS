package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.model.Province;

/**
 * Phân công địa bàn: ai cầm tỉnh nào (bảng user_provinces).
 *
 * <p>Chỉ lưu chiều TỈNH -&gt; NGƯỜI. Người cầm tỉnh KHÔNG nhất thiết là nhân
 * viên tác nghiệp: khách hàng xác nhận 2026-09-15 rằng cấp trên cũng trực
 * tiếp cầm địa bàn và tự đi đàm phán -- nên bảng này nhận mọi user, không lọc
 * theo vai trò hay theo tầng trong cây tổ chức.
 *
 * <p>{@link #findProvincesManagedBy} là thứ khác: địa bàn mà một người bao
 * phủ QUA CẤP DƯỚI. Không lưu, mà suy ra bằng cách gộp -- lưu tay cả hai thứ
 * là tạo ra hai nguồn sự thật rồi có ngày lệch nhau. Một người có thể có cả
 * hai: vài tỉnh tự cầm, vài tỉnh phủ qua lính.
 *
 * <p>Giả định của V19 ("địa bàn là thực thể riêng") đã được khách hàng xác
 * nhận 2026-09-15, kèm quyết định KHOÁ ô người phụ trách theo địa bàn ở luồng
 * tạo/sửa khách hàng -- xem {@link #findAssigneeOfWard}.
 */
public class TerritoryDAO {

    private static final Logger LOG = LoggerFactory.getLogger(TerritoryDAO.class);

    /** Các tỉnh mà {@code userId} trực tiếp cầm. */
    public List<Province> findProvincesOf(int userId) {
        String sql = "SELECT p.province_id, p.province_name " +
                     "FROM user_provinces up JOIN provinces p ON p.province_id = up.province_id " +
                     "WHERE up.user_id = ? ORDER BY p.province_name";
        return queryProvinces(sql, userId);
    }

    /**
     * Địa bàn của một quản lý vùng: gộp tỉnh của tất cả cấp dưới trực tiếp.
     *
     * Suy ra chứ không lưu -- nên đổi cấp trên của một nhân viên là địa bàn
     * quản lý tự đúng theo, không cần ai đi sửa thêm chỗ nào.
     */
    public List<Province> findProvincesManagedBy(int managerId) {
        String sql = "SELECT DISTINCT p.province_id, p.province_name " +
                     "FROM user_provinces up " +
                     "JOIN users u ON u.user_id = up.user_id " +
                     "JOIN provinces p ON p.province_id = up.province_id " +
                     "WHERE u.manager_id = ? AND u.is_deleted = 0 ORDER BY p.province_name";
        return queryProvinces(sql, managerId);
    }

    /**
     * Người đang cầm tỉnh này, hoặc null nếu chưa ai.
     *
     * "Chưa ai cầm" là trạng thái hợp lệ và là mặc định lúc mới bật tính năng
     * -- bên gọi phải xử lý null chứ không được coi là lỗi.
     */
    public Integer findAssigneeOf(int provinceId) {
        String sql = "SELECT up.user_id FROM user_provinces up " +
                     "JOIN users u ON u.user_id = up.user_id AND u.is_deleted = 0 " +
                     "WHERE up.province_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, provinceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        } catch (SQLException ex) {
            LOG.error("Loi tra nguoi cam tinh (provinceId={})", provinceId, ex);
            return null;
        }
    }

    /**
     * Người cầm tỉnh chứa xã/phường này, hoặc null nếu tỉnh đó chưa ai cầm.
     *
     * Suy từ xã/phường chứ KHÔNG nhận provinceId rời từ request: form gửi lên
     * cả hai ô, nhưng chỉ ô xã/phường mới thực sự đi vào địa chỉ khách hàng.
     * Tin vào provinceId rời thì một request nặn tay khai được tỉnh A để lấy
     * người của tỉnh A trong khi địa chỉ nằm ở tỉnh B -- đúng cái mà khoá ô
     * người phụ trách sinh ra để chặn.
     *
     * "Chưa ai cầm" là trạng thái hợp lệ, xem {@link #findAssigneeOf}.
     */
    public Integer findAssigneeOfWard(int wardId) {
        String sql = "SELECT up.user_id FROM districts d " +
                     "JOIN user_provinces up ON up.province_id = d.province_id " +
                     "JOIN users u ON u.user_id = up.user_id AND u.is_deleted = 0 " +
                     "WHERE d.districts_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, wardId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        } catch (SQLException ex) {
            LOG.error("Loi tra nguoi cam dia ban theo xa/phuong (wardId={})", wardId, ex);
            return null;
        }
    }

    /**
     * Toàn bộ phân công, dạng {@code province_id -> user_id}. Dùng để nhúng
     * sẵn vào trang tạo khách hàng: 34 tỉnh thì gửi kèm một lần rẻ hơn hẳn so
     * với gọi AJAX mỗi lần người dùng đổi ô tỉnh.
     */
    public Map<Integer, Integer> findAllAssignments() {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        String sql = "SELECT up.province_id, up.user_id FROM user_provinces up " +
                     "JOIN users u ON u.user_id = up.user_id AND u.is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getInt("province_id"), rs.getInt("user_id"));
            }
        } catch (SQLException ex) {
            LOG.error("Loi tra danh sach phan cong dia ban", ex);
        }
        return result;
    }

    /**
     * Đặt lại toàn bộ địa bàn của một người thành đúng {@code provinceIds}.
     *
     * Xoá hết rồi chèn lại TRONG CÙNG MỘT TRANSACTION, không phải hai lời gọi
     * rời nhau: nửa chừng mà lỗi thì người đó mất sạch địa bàn cũ mà chưa có
     * địa bàn mới, và không ai biết cho tới khi có khách hàng rơi vào tỉnh đó.
     *
     * Tỉnh đang do người KHÁC cầm sẽ làm câu INSERT vi phạm UNIQUE và cả
     * transaction bị huỷ -- đúng quy tắc "một tỉnh một người" của khách hàng.
     * Bên gọi nên lọc trước để báo lỗi tử tế thay vì để nổ ở đây.
     *
     * @return true nếu đã ghi xong.
     */
    public boolean replaceProvincesOf(int userId, List<Integer> provinceIds) {
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM user_provinces WHERE user_id = ?")) {
                    del.setInt(1, userId);
                    del.executeUpdate();
                }
                if (provinceIds != null && !provinceIds.isEmpty()) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO user_provinces (province_id, user_id) VALUES (?, ?)")) {
                        for (Integer provinceId : provinceIds) {
                            ins.setInt(1, provinceId);
                            ins.setInt(2, userId);
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                }
                conn.commit();
                committed = true;
                return true;
            } finally {
                if (!committed) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        LOG.error("Loi rollback khi phan cong dia ban (userId={})", userId, rollbackEx);
                    }
                }
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException autoCommitEx) {
                    LOG.error("Loi reset autocommit sau phan cong dia ban (userId={})", userId, autoCommitEx);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi phan cong dia ban (userId={})", userId, ex);
            return false;
        }
    }

    /**
     * Những tỉnh mà {@code userId} chọn được: chưa ai cầm, hoặc chính họ đang
     * cầm. Lọc sẵn để ô chọn không mời người dùng cướp tỉnh của đồng nghiệp
     * rồi ăn lỗi UNIQUE.
     */
    public List<Province> findSelectableProvinces(Integer userId) {
        String sql = "SELECT p.province_id, p.province_name FROM provinces p " +
                     "LEFT JOIN user_provinces up ON up.province_id = p.province_id " +
                     "WHERE up.province_id IS NULL" +
                     (userId != null ? " OR up.user_id = ?" : "") +
                     " ORDER BY p.province_name";
        List<Province> result = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userId != null) {
                ps.setInt(1, userId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Province(rs.getInt("province_id"), rs.getString("province_name")));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi tra danh sach tinh chon duoc (userId={})", userId, ex);
        }
        return result;
    }

    private List<Province> queryProvinces(String sql, int param) {
        List<Province> result = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Province(rs.getInt("province_id"), rs.getString("province_name")));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van dia ban (param={})", param, ex);
        }
        return result;
    }
}
