package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.Normalizer;
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

    // ------------------------------------------------------------------
    // Quản lý nhân viên (UC-24 -> UC-29, Admin only -- xem EmployeeController)
    // ------------------------------------------------------------------

    /** Toàn bộ role trong hệ thống (4 dòng, không cần cache), phục vụ dropdown "Vai trò". */
    public List<Role> findAllRoles() {
        List<Role> result = new ArrayList<>();
        String sql = "SELECT role_id, role_name FROM roles ORDER BY role_id";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Role(rs.getInt("role_id"), rs.getString("role_name")));
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN DANH SACH VAI TRO ---");
            ex.printStackTrace();
        }
        return result;
    }

    /**
     * Danh sách nhân viên có phân trang (BR-12), KHÔNG lọc theo is_deleted --
     * khác với các DAO khác (Customer/Contract/...) vốn ẩn hẳn bản ghi đã
     * soft-delete: ở đây is_deleted đóng vai trò "Trạng thái Inactive"
     * (BR-25), Admin vẫn cần thấy các nhân viên Inactive trong danh sách để
     * còn Mở khóa lại được (UC-29) -- ẩn theo statusFilter thay vì mặc định.
     */
    public List<User> findAll(int page, int pageSize, String keyword, String statusFilter, Integer roleFilter) {
        List<User> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT u.*, r.role_name FROM users u JOIN roles r ON u.role_id = r.role_id WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, statusFilter, roleFilter);
        sql.append(" ORDER BY u.created_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((page - 1) * pageSize);

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN DANH SACH NHAN VIEN (PHAN TRANG) ---");
            ex.printStackTrace();
        }
        return result;
    }

    public int countAll(String keyword, String statusFilter, Integer roleFilter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM users u WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, statusFilter, roleFilter);
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI DEM DANH SACH NHAN VIEN ---");
            ex.printStackTrace();
            return 0;
        }
    }

    private void appendFilters(StringBuilder sql, List<Object> params, String keyword, String statusFilter, Integer roleFilter) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (u.last_name LIKE ? OR u.middle_name LIKE ? OR u.first_name LIKE ? OR u.email LIKE ? OR u.phone LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            for (int i = 0; i < 5; i++) {
                params.add(like);
            }
        }
        if ("Active".equals(statusFilter)) {
            sql.append(" AND u.is_deleted = 0");
        } else if ("Inactive".equals(statusFilter)) {
            sql.append(" AND u.is_deleted = 1");
        }
        if (roleFilter != null) {
            sql.append(" AND u.role_id = ?");
            params.add(roleFilter);
        }
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    /**
     * Lấy 1 nhân viên theo id cho trang chi tiết/sửa của Admin -- KHÔNG lọc
     * is_deleted (khác findProfileById dành cho chính chủ tự xem hồ sơ), vì
     * Admin cần mở được cả nhân viên đang Inactive để xem lại/Mở khóa.
     */
    public User findById(int userId) {
        String sql = "SELECT u.*, r.role_name, " +
                     "a.address_id, a.street_and_local_name, a.districts_id, " +
                     "d.districts_name, d.province_id, p.province_name " +
                     "FROM users u " +
                     "JOIN roles r ON u.role_id = r.role_id " +
                     "LEFT JOIN addresses a ON u.address_id = a.address_id " +
                     "LEFT JOIN districts d ON a.districts_id = d.districts_id " +
                     "LEFT JOIN provinces p ON d.province_id = p.province_id " +
                     "WHERE u.user_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = mapRow(rs);
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
            System.err.println("--- LOI TRUY VAN CHI TIET NHAN VIEN ---");
            ex.printStackTrace();
        }
        return null;
    }

    private User mapRow(ResultSet rs) throws SQLException {
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
        u.setCreatedAt(rs.getTimestamp("created_at"));
        u.setDeleted(rs.getBoolean("is_deleted"));
        return u;
    }

    /** BR-27: email/SĐT/CCCD phải duy nhất -- true nếu email này đã có người dùng (dùng khi tạo mới/sửa). */
    public boolean existsByEmail(String email, Integer excludeUserId) {
        return existsByColumn("email", email, excludeUserId);
    }

    public boolean existsByPhone(String phone, Integer excludeUserId) {
        return existsByColumn("phone", phone, excludeUserId);
    }

    public boolean existsByCitizenId(String citizenId, Integer excludeUserId) {
        return existsByColumn("citizen_id", citizenId, excludeUserId);
    }

    private boolean existsByColumn(String column, String value, Integer excludeUserId) {
        String sql = "SELECT 1 FROM users WHERE " + column + " = ?" +
                     (excludeUserId != null ? " AND user_id <> ?" : "") + " LIMIT 1";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            if (excludeUserId != null) {
                ps.setInt(2, excludeUserId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI KIEM TRA TRUNG LAP (" + column + ") ---");
            ex.printStackTrace();
            return true; // an toàn: coi như đã trùng để chặn insert/update lỗi thay vì để lọt xuống DB
        }
    }

    /**
     * Sinh username duy nhất từ họ tên: bỏ dấu tiếng Việt, viết liền không
     * hoa/thường/khoảng trắng (VD: "Nguyễn Văn A" -> "nguyenvana"). Nếu đã
     * tồn tại thì thử thêm số đếm ở cuối (nguyenvana2, nguyenvana3, ...) cho
     * tới khi tìm được username trống -- username không cho người tạo tự gõ
     * (BR-28 chỉ bắt buộc Phòng ban + Vai trò, không có username), tự sinh
     * để tránh nhân viên trùng tên phải tự nghĩ username.
     */
    public String generateUniqueUsername(String lastName, String middleName, String firstName) {
        String base = stripDiacritics((nullToEmpty(lastName) + nullToEmpty(middleName) + nullToEmpty(firstName)))
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
        if (base.isEmpty()) {
            base = "nhanvien";
        }
        String candidate = base;
        int suffix = 1;
        while (usernameExists(candidate)) {
            suffix++;
            candidate = base + suffix;
        }
        return candidate;
    }

    private boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ? LIMIT 1";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI KIEM TRA TRUNG USERNAME ---");
            ex.printStackTrace();
            return true;
        }
    }

    private String stripDiacritics(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.replace('đ', 'd').replace('Đ', 'D');
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Tạo nhân viên mới (UC-26) kèm mật khẩu (đã hash BCrypt bởi
     * EmployeeController, hàm này chỉ lưu). Nếu user.getAddress() có dữ
     * liệu thì tạo luôn dòng addresses tương ứng, cùng transaction với
     * INSERT users -- tái dùng resolveAddressId() có sẵn.
     */
    public int insert(User user) {
        String sql = "INSERT INTO users (username, email, password_hash, role_id, last_name, middle_name, " +
                     "first_name, gender, date_of_birth, citizen_id, phone, personal_email, address_id, " +
                     "department, hire_date, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer addressId = resolveAddressId(conn, user.getAddress());
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, user.getUsername());
                    ps.setString(2, user.getEmail());
                    ps.setString(3, user.getPasswordHash());
                    ps.setInt(4, user.getRoleId());
                    ps.setString(5, user.getLastName());
                    ps.setString(6, user.getMiddleName());
                    ps.setString(7, user.getFirstName());
                    ps.setString(8, user.getGender());
                    ps.setDate(9, user.getDateOfBirth());
                    ps.setString(10, user.getCitizenId());
                    ps.setString(11, user.getPhone());
                    ps.setString(12, user.getPersonalEmail());
                    if (addressId != null) {
                        ps.setInt(13, addressId);
                    } else {
                        ps.setNull(13, Types.INTEGER);
                    }
                    ps.setString(14, user.getDepartment());
                    ps.setDate(15, user.getHireDate());
                    int affected = ps.executeUpdate();
                    if (affected == 0) {
                        conn.rollback();
                        return -1;
                    }
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        int newId = keys.next() ? keys.getInt(1) : -1;
                        conn.commit();
                        return newId;
                    }
                }
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TAO NHAN VIEN MOI ---");
            ex.printStackTrace();
            return -1;
        }
    }

    /**
     * Cập nhật thông tin nhân viên do Admin sửa (UC-27/UC-28: gồm cả phòng
     * ban + vai trò, khác updateProfile() ở trên vốn chỉ cho tự sửa thông
     * tin cá nhân). KHÔNG đổi username/password ở đây -- đổi mật khẩu có
     * luồng riêng (quên mật khẩu / đổi mật khẩu).
     */
    public boolean update(User user) {
        String sql = "UPDATE users SET email = ?, role_id = ?, last_name = ?, middle_name = ?, first_name = ?, " +
                     "gender = ?, date_of_birth = ?, citizen_id = ?, phone = ?, personal_email = ?, address_id = ?, " +
                     "department = ?, hire_date = ? WHERE user_id = ?";
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer addressId = resolveAddressId(conn, user.getAddress());
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, user.getEmail());
                    ps.setInt(2, user.getRoleId());
                    ps.setString(3, user.getLastName());
                    ps.setString(4, user.getMiddleName());
                    ps.setString(5, user.getFirstName());
                    ps.setString(6, user.getGender());
                    ps.setDate(7, user.getDateOfBirth());
                    ps.setString(8, user.getCitizenId());
                    ps.setString(9, user.getPhone());
                    ps.setString(10, user.getPersonalEmail());
                    if (addressId != null) {
                        ps.setInt(11, addressId);
                    } else {
                        ps.setNull(11, Types.INTEGER);
                    }
                    ps.setString(12, user.getDepartment());
                    ps.setDate(13, user.getHireDate());
                    ps.setInt(14, user.getUserId());
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
            System.err.println("--- LOI CAP NHAT NHAN VIEN ---");
            ex.printStackTrace();
            return false;
        }
    }

    /** BR-25/BR-26: Khóa (Inactive) hoặc Mở khóa (Active) tài khoản nhân viên -- tái dùng cột is_deleted. */
    public boolean setActive(int userId, boolean active) {
        String sql = "UPDATE users SET is_deleted = ? WHERE user_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, !active);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            System.err.println("--- LOI KHOA/MO KHOA NHAN VIEN ---");
            ex.printStackTrace();
            return false;
        }
    }
}
