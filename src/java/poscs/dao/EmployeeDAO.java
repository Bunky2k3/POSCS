package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import poscs.model.Address;
import poscs.model.District;
import poscs.model.Province;
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
     * Lấy đầy đủ thông tin hồ sơ (kèm role + địa chỉ đầy đủ tới tận
     * tỉnh/thành) của 1 nhân viên đang hoạt động, theo user_id -- dùng cho
     * trang "Thông tin cá nhân". Trả về null nếu không tìm thấy (vd. tài
     * khoản vừa bị xoá ở nơi khác trong lúc phiên đăng nhập vẫn còn sống).
     */
    public User findProfileById(int userId) {
        // LEFT JOIN vì address_id (và do đó cả address/district/province) có
        // thể NULL -- không phải nhân viên nào cũng đã khai báo địa chỉ.
        String sql = "SELECT u.*, r.role_name, " +
                     "a.address_id, a.street_and_local_name, a.districts_id, " +
                     "d.districts_name, d.province_id, p.province_name " +
                     "FROM users u " +
                     "JOIN roles r ON u.role_id = r.role_id " +
                     "LEFT JOIN addresses a ON u.address_id = a.address_id " +
                     "LEFT JOIN districts d ON a.districts_id = d.districts_id " +
                     "LEFT JOIN provinces p ON d.province_id = p.province_id " +
                     "WHERE u.user_id = ? AND u.is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = new User();
                    u.setUserId(rs.getInt("user_id"));
                    u.setUsername(rs.getString("username"));
                    u.setEmail(rs.getString("email"));
                    u.setRoleId(rs.getInt("role_id"));
                    u.setRole(new Role(rs.getInt("role_id"), rs.getString("role_name")));
                    u.setLastName(rs.getString("last_name"));
                    u.setMiddleName(rs.getString("middle_name"));
                    u.setFirstName(rs.getString("first_name"));
                    u.setGender(rs.getString("gender"));
                    u.setDateOfBirth(rs.getDate("date_of_birth"));
                    u.setCitizenId(rs.getString("citizen_id"));
                    u.setPhone(rs.getString("phone"));
                    u.setPersonalEmail(rs.getString("personal_email"));
                    u.setAvatarUrl(rs.getString("avatar_url"));
                    u.setDepartment(rs.getString("department"));
                    u.setHireDate(rs.getDate("hire_date"));

                    // address_id có thể NULL -- getInt() sẽ trả 0 khi NULL, nên
                    // phải kiểm tra wasNull() để phân biệt "0" thật với NULL.
                    int addressId = rs.getInt("address_id");
                    if (!rs.wasNull()) {
                        Address address = new Address();
                        address.setAddressId(addressId);
                        address.setStreetAndLocalName(rs.getString("street_and_local_name"));
                        address.setDistrictId(rs.getInt("districts_id"));

                        District district = new District();
                        district.setDistrictId(rs.getInt("districts_id"));
                        district.setDistrictName(rs.getString("districts_name"));
                        district.setProvinceId(rs.getInt("province_id"));
                        district.setProvince(new Province(rs.getInt("province_id"), rs.getString("province_name")));

                        address.setDistrict(district);
                        u.setAddress(address);
                    }

                    return u;
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN HO SO CA NHAN ---");
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Cập nhật các trường "thông tin cá nhân" (KHÔNG gồm email đăng nhập,
     * phòng ban, vai trò, ngày vào làm -- các trường đó do Admin quản lý,
     * xem section-hint trong updateProfile.jsp). Nếu user.getAddress() có
     * dữ liệu, tự tạo/cập nhật luôn dòng addresses tương ứng. Trả về true
     * nếu cập nhật thành công.
     */
    public boolean updateProfile(User user) {
        String sql = "UPDATE users SET last_name = ?, middle_name = ?, first_name = ?, gender = ?, " +
                     "date_of_birth = ?, citizen_id = ?, phone = ?, personal_email = ?, address_id = ? " +
                     "WHERE user_id = ? AND is_deleted = 0";
        try (Connection conn = DBContext.getConnection()) {
            // resolveAddressId() (insert/update dòng addresses) và UPDATE users
            // phải cùng thành công hoặc cùng rollback -- tắt autocommit để gộp
            // thành 1 transaction, tránh để địa chỉ đã đổi mà các trường khác
            // của hồ sơ lại không được lưu (hoặc ngược lại) khi 1 trong 2 bước lỗi.
            conn.setAutoCommit(false);
            try {
                Integer addressId = resolveAddressId(conn, user.getAddress());

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, user.getLastName());
                    ps.setString(2, user.getMiddleName());
                    ps.setString(3, user.getFirstName());
                    ps.setString(4, user.getGender());
                    ps.setDate(5, user.getDateOfBirth());
                    ps.setString(6, user.getCitizenId());
                    ps.setString(7, user.getPhone());
                    ps.setString(8, user.getPersonalEmail());
                    if (addressId != null) {
                        ps.setInt(9, addressId);
                    } else {
                        ps.setNull(9, Types.INTEGER);
                    }
                    ps.setInt(10, user.getUserId());
                    boolean ok = ps.executeUpdate() > 0;
                    if (ok) {
                        conn.commit();
                    } else {
                        conn.rollback();
                    }
                    return ok;
                }
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI CAP NHAT HO SO CA NHAN ---");
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Nếu address đã có addressId (>0) thì UPDATE ngay dòng addresses đó;
     * nếu chưa có (lần đầu khai báo địa chỉ) thì INSERT dòng mới và trả về
     * id vừa tạo. Trả về null nếu address rỗng (chưa nhập đủ tỉnh/huyện +
     * địa chỉ chi tiết) -- coi như không có địa chỉ.
     */
    private Integer resolveAddressId(Connection conn, Address address) throws SQLException {
        if (address == null || address.getStreetAndLocalName() == null || address.getDistrictId() <= 0) {
            return null;
        }
        if (address.getAddressId() > 0) {
            String sql = "UPDATE addresses SET street_and_local_name = ?, districts_id = ? WHERE address_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, address.getStreetAndLocalName());
                ps.setInt(2, address.getDistrictId());
                ps.setInt(3, address.getAddressId());
                ps.executeUpdate();
            }
            return address.getAddressId();
        }
        String sql = "INSERT INTO addresses (street_and_local_name, districts_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, address.getStreetAndLocalName());
            ps.setInt(2, address.getDistrictId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : null;
            }
        }
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
