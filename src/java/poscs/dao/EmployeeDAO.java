package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import poscs.model.Role;
import poscs.model.User;

public class EmployeeDAO {

    /**
     * Tra cứu 1 nhân viên đang hoạt động (is_deleted=0) theo username HOẶC
     * email, kèm role -- dùng cho đăng nhập. Trả về null nếu không tìm thấy.
     */
    public User findByUsernameOrEmail(String identifier) {
        // JOIN sẵn bảng roles để lấy luôn role_name, tránh phải query thêm lần 2.
        String sql = "SELECT u.user_id, u.username, u.email, u.password_hash, u.role_id, " +
                     "u.last_name, u.middle_name, u.first_name, u.department, r.role_name " +
                     "FROM users u JOIN roles r ON u.role_id = r.role_id " +
                     "WHERE (u.username = ? OR u.email = ?) AND u.is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            // Cùng 1 giá trị identifier được so khớp với cả 2 cột username và email,
            // để form đăng nhập chấp nhận nhập username hoặc email đều được.
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // Map từng cột trong ResultSet sang object User.
                    User u = new User();
                    u.setUserId(rs.getInt("user_id"));
                    u.setUsername(rs.getString("username"));
                    u.setEmail(rs.getString("email"));
                    u.setPasswordHash(rs.getString("password_hash")); // hash BCrypt, controller sẽ so khớp bằng BCrypt.checkpw
                    u.setRoleId(rs.getInt("role_id"));
                    u.setLastName(rs.getString("last_name"));
                    u.setMiddleName(rs.getString("middle_name"));
                    u.setFirstName(rs.getString("first_name"));
                    u.setDepartment(rs.getString("department"));
                    u.setRole(new Role(rs.getInt("role_id"), rs.getString("role_name")));
                    return u;
                }
                // Không tìm thấy hàng nào khớp -> trả về null, để caller tự quyết định
                // thông báo lỗi (KHÔNG throw exception vì "không tìm thấy" là kết quả
                // hợp lệ, không phải lỗi hệ thống).
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN DANG NHAP ---");
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Đặt lại mật khẩu (đã hash sẵn bằng BCrypt) cho tài khoản có email này.
     * Dùng trong luồng quên mật khẩu, SAU KHI đã xác thực OTP thành công.
     * Trả về true nếu có đúng 1 hàng được cập nhật.
     */
    public boolean updatePasswordByEmail(String email, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash = ? WHERE email = ? AND is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setString(2, email);
            // executeUpdate() trả về số hàng bị ảnh hưởng; ==1 nghĩa là cập nhật đúng 1 user.
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            System.err.println("--- LOI CAP NHAT MAT KHAU ---");
            ex.printStackTrace();
            return false;
        }
    }

    /** Lấy toàn bộ nhân viên đang hoạt động (is_deleted=0), phục vụ các dropdown "Nhân viên phụ trách". */
    public List<User> findAllActive() {
        List<User> result = new ArrayList<>();
        String sql = "SELECT user_id, last_name, middle_name, first_name, department " +
                     "FROM users WHERE is_deleted = 0 ORDER BY last_name, first_name";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                User u = new User();
                u.setUserId(rs.getInt("user_id"));
                u.setLastName(rs.getString("last_name"));
                u.setMiddleName(rs.getString("middle_name"));
                u.setFirstName(rs.getString("first_name"));
                u.setDepartment(rs.getString("department"));
                result.add(u);
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN DANH SACH NHAN VIEN ---");
            ex.printStackTrace();
        }
        return result;
    }
}
