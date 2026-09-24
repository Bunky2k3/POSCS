package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.model.Address;
import poscs.model.Department;
import poscs.model.District;
import poscs.model.Province;
import poscs.model.ProvinceAssignment;
import poscs.model.Role;
import poscs.model.User;

public class EmployeeDAO {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeDAO.class);

    /**
     * Tra cứu 1 nhân viên theo username, kèm role -- dùng cho đăng nhập. Trả
     * về null nếu không tìm thấy. LƯU Ý: không lọc theo is_deleted ở đây --
     * tài khoản bị khóa (is_deleted=1) vẫn được trả về để controller có thể
     * phân biệt "sai mật khẩu" với "tài khoản bị khóa" và báo thông báo lỗi
     * phù hợp (chỉ sau khi đã xác minh đúng mật khẩu, để không lộ trạng thái
     * tài khoản cho kẻ dò mật khẩu).
     *
     * <p>Từ V36: KHÔNG còn nhánh "hoặc email" -- users.email (chuỗi
     * &lt;username&gt;@postef.com.vn hệ thống tự bịa, chưa từng gắn hộp thư
     * thật) đã xoá, username là định danh đăng nhập duy nhất.
     */
    public User findByUsername(String username) {
        // JOIN sẵn bảng roles để lấy luôn role_name, tránh phải query thêm lần 2.
        // gender/date_of_birth/citizen_id/phone + must_change_password: cần
        // ngay từ lúc đăng nhập (không phải chỉ lúc xem hồ sơ) để
        // AuthenticationFilter biết có phải ép qua /changePassword,
        // /updateProfile hay không -- xem User.isProfileIncomplete, V35.
        // personal_email: luồng quên mật khẩu gửi OTP tới đúng địa chỉ này.
        String sql = "SELECT u.user_id, u.username, u.password_hash, u.must_change_password, u.role_id, " +
                     "u.last_name, u.middle_name, u.first_name, u.gender, u.date_of_birth, u.citizen_id, u.phone, " +
                     "u.personal_email, u.department_id, u.manager_id, u.avatar_url, u.is_deleted, r.role_name " +
                     "FROM users u JOIN roles r ON u.role_id = r.role_id " +
                     "WHERE u.username = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // Map từng cột trong ResultSet sang object User.
                    User u = new User();
                    u.setUserId(rs.getInt("user_id"));
                    u.setUsername(rs.getString("username"));
                    u.setPasswordHash(rs.getString("password_hash")); // hash BCrypt, controller sẽ so khớp bằng BCrypt.checkpw
                    u.setMustChangePassword(rs.getBoolean("must_change_password"));
                    u.setRoleId(rs.getInt("role_id"));
                    u.setLastName(rs.getString("last_name"));
                    u.setMiddleName(rs.getString("middle_name"));
                    u.setFirstName(rs.getString("first_name"));
                    u.setGender(rs.getString("gender"));
                    u.setDateOfBirth(rs.getDate("date_of_birth"));
                    u.setCitizenId(rs.getString("citizen_id"));
                    u.setPhone(rs.getString("phone"));
                    // Quên map cột này thì getPersonalEmail() luôn null, và
                    // quên mật khẩu im lặng không gửi OTP cho ai cả -- đúng
                    // như đã xảy ra từ V36 tới khi có test tích hợp chặn lại.
                    u.setPersonalEmail(rs.getString("personal_email"));
                    u.setDepartmentId(rs.getInt("department_id"));
                    // Vị trí trong cây tổ chức PHẢI đi cùng User vào session:
                    // AccessControl đọc thẳng từ đó để biết người này là cấp
                    // trên hay cấp dưới. Quên map ở đây thì managerId luôn null,
                    // ai cũng thành cấp trên, và toàn bộ phần siết quyền im
                    // lặng không chạy -- không lỗi, không log, chỉ là không có
                    // tác dụng gì.
                    int managerId = rs.getInt("manager_id");
                    u.setManagerId(rs.wasNull() ? null : managerId);
                    u.setAvatarUrl(rs.getString("avatar_url")); // topbar doc anh dai dien tu session
                    u.setDeleted(rs.getBoolean("is_deleted"));
                    u.setRole(new Role(rs.getInt("role_id"), rs.getString("role_name")));
                    return u;
                }
                // Không tìm thấy hàng nào khớp -> trả về null, để caller tự quyết định
                // thông báo lỗi (KHÔNG throw exception vì "không tìm thấy" là kết quả
                // hợp lệ, không phải lỗi hệ thống).
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van dang nhap (username={})", username, ex);
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
        String sql = "SELECT u.*, r.role_name, dep.department_name, " +
                     "a.address_id, a.street_and_local_name, a.districts_id, " +
                     "d.districts_name, d.province_id, p.province_name " +
                     "FROM users u " +
                     "JOIN roles r ON u.role_id = r.role_id " +
                     "JOIN departments dep ON u.department_id = dep.department_id " +
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
                    u.setDepartmentId(rs.getInt("department_id"));
                    u.setDepartment(new Department(rs.getInt("department_id"), rs.getString("department_name")));
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
            LOG.error("Loi truy van ho so ca nhan (userId={})", userId, ex);
        }
        return null;
    }

    /**
     * Cập nhật các trường "thông tin cá nhân" (KHÔNG gồm username đăng nhập,
     * phòng ban, vai trò, ngày vào làm -- các trường đó do Admin quản lý,
     * xem section-hint trong updateProfile.jsp). Nếu user.getAddress() có
     * dữ liệu, tự tạo/cập nhật luôn dòng addresses tương ứng. Trả về true
     * nếu cập nhật thành công.
     */
    public boolean updateProfile(User user) {
        String sql = "UPDATE users SET last_name = ?, middle_name = ?, first_name = ?, gender = ?, " +
                     "date_of_birth = ?, citizen_id = ?, phone = ?, personal_email = ?, address_id = ?, " +
                     "avatar_url = ? " +
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
                    ps.setString(10, user.getAvatarUrl());
                    ps.setInt(11, user.getUserId());
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
            LOG.error("Loi cap nhat ho so ca nhan (userId={}, username={})", user.getUserId(), user.getUsername(), ex);
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
     * Đặt lại mật khẩu (đã hash sẵn bằng BCrypt) cho tài khoản có username
     * này. Dùng trong luồng quên mật khẩu (SAU KHI đã xác thực OTP thành
     * công) VÀ luồng tự đổi mật khẩu (AuthenticationController.
     * handleChangePassword, sau khi đã xác minh đúng mật khẩu cũ). Trả về
     * true nếu có đúng 1 hàng được cập nhật.
     *
     * <p>Từ V36: khoá theo username, không còn email công ty (đã xoá cột).
     *
     * <p>Luôn tắt kèm {@code must_change_password}: cả hai luồng đều là lúc
     * người dùng vừa tự đặt một mật khẩu chỉ họ biết, khác mật khẩu tạm Admin
     * cấp -- xem V35, AuthenticationFilter.
     */
    public boolean updatePasswordByUsername(String username, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash = ?, must_change_password = 0 WHERE username = ? AND is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setString(2, username);
            // executeUpdate() trả về số hàng bị ảnh hưởng; ==1 nghĩa là cập nhật đúng 1 user.
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            LOG.error("Loi cap nhat mat khau (username={})", username, ex);
            return false;
        }
    }

    /**
     * Nhân viên đang hoạt động của MỘT vai, để đổ dropdown "người phụ trách".
     *
     * <p>Mỗi màn hình giao việc cho một vai khác nhau -- khách hàng và hợp
     * đồng giao cho Sales, phiếu hỗ trợ giao cho Kỹ thuật. {@link
     * #findAllActive()} trả về tất cả và KHÔNG dùng được ở những chỗ đó: nó
     * mời người nhập chọn cả Admin lẫn kỹ thuật viên vào ô "người phụ trách
     * khách hàng".
     *
     * @param alsoInclude user_id vẫn giữ trong danh sách dù sai vai -- truyền
     *        người đang được gán của bản ghi ĐANG SỬA vào đây. Thiếu nó thì
     *        bản ghi cũ có người phụ trách sai vai (đổi vai, hoặc dữ liệu tạo
     *        trước thay đổi này) sẽ mở form lên với ô trống, và bấm lưu là
     *        thay mất người phụ trách dù người dùng chỉ định sửa số điện
     *        thoại. Đúng cái bẫy mà {@code
     *        AddressDAO.findBranchProvincesIncluding} đã sinh ra để tránh.
     *        Bỏ qua phần tử null, nên truyền thẳng cột nullable vào được.
     */
    public List<User> findActiveByRole(String roleName, Integer... alsoInclude) {
        List<Integer> keep = new ArrayList<>();
        if (alsoInclude != null) {
            for (Integer id : alsoInclude) {
                if (id != null && id > 0 && !keep.contains(id)) {
                    keep.add(id);
                }
            }
        }
        StringBuilder sql = new StringBuilder(
                "SELECT u.user_id, u.username, u.last_name, u.middle_name, u.first_name, u.department_id " +
                "FROM users u JOIN roles r ON r.role_id = u.role_id " +
                "WHERE u.is_deleted = 0 AND (r.role_name = ?");
        for (int i = 0; i < keep.size(); i++) {
            sql.append(i == 0 ? " OR u.user_id IN (?" : ",?");
        }
        if (!keep.isEmpty()) {
            sql.append(")");
        }
        sql.append(") ORDER BY u.last_name, u.first_name");

        List<User> result = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, roleName);
            for (int i = 0; i < keep.size(); i++) {
                ps.setInt(i + 2, keep.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    User u = new User();
                    u.setUserId(rs.getInt("user_id"));
                    u.setUsername(rs.getString("username"));
                    u.setLastName(rs.getString("last_name"));
                    u.setMiddleName(rs.getString("middle_name"));
                    u.setFirstName(rs.getString("first_name"));
                    u.setDepartmentId(rs.getInt("department_id"));
                    result.add(u);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van nhan vien theo vai (roleName={})", roleName, ex);
        }
        return result;
    }

    /**
     * Lấy toàn bộ nhân viên đang hoạt động (is_deleted=0), KHÔNG lọc vai.
     *
     * <p>Chỉ còn dùng cho việc so khớp username lúc nhập Excel hàng loạt
     * (cần username nên SELECT cả cột đó). Dropdown "người phụ trách" thì
     * dùng {@link #findActiveByRole} -- đổ tất cả vào đó là mời người nhập
     * gán khách hàng cho một kỹ thuật viên.
     */
    public List<User> findAllActive() {
        List<User> result = new ArrayList<>();
        String sql = "SELECT user_id, username, last_name, middle_name, first_name, department_id " +
                     "FROM users WHERE is_deleted = 0 ORDER BY last_name, first_name";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                User u = new User();
                u.setUserId(rs.getInt("user_id"));
                u.setUsername(rs.getString("username"));
                u.setLastName(rs.getString("last_name"));
                u.setMiddleName(rs.getString("middle_name"));
                u.setFirstName(rs.getString("first_name"));
                u.setDepartmentId(rs.getInt("department_id"));
                result.add(u);
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van danh sach nhan vien", ex);
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
            LOG.error("Loi truy van danh sach vai tro", ex);
        }
        return result;
    }

    /** Toàn bộ phòng ban trong hệ thống, phục vụ dropdown "Phòng ban". */
    public List<Department> findAllDepartments() {
        List<Department> result = new ArrayList<>();
        String sql = "SELECT department_id, department_name FROM departments ORDER BY department_id";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Department(rs.getInt("department_id"), rs.getString("department_name")));
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van danh sach phong ban", ex);
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
                "SELECT u.*, r.role_name, dep.department_name FROM users u " +
                "JOIN roles r ON u.role_id = r.role_id " +
                "JOIN departments dep ON u.department_id = dep.department_id WHERE 1=1");
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
            LOG.error("Loi truy van danh sach nhan vien (phan trang)", ex);
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
            LOG.error("Loi dem danh sach nhan vien", ex);
            return 0;
        }
    }

    private void appendFilters(StringBuilder sql, List<Object> params, String keyword, String statusFilter, Integer roleFilter) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (u.last_name LIKE ? OR u.middle_name LIKE ? OR u.first_name LIKE ? OR u.phone LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            for (int i = 0; i < 4; i++) {
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
        String sql = "SELECT u.*, r.role_name, dep.department_name, " +
                     "a.address_id, a.street_and_local_name, a.districts_id, " +
                     "d.districts_name, d.province_id, p.province_name " +
                     "FROM users u " +
                     "JOIN roles r ON u.role_id = r.role_id " +
                     "JOIN departments dep ON u.department_id = dep.department_id " +
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
            LOG.error("Loi truy van chi tiet nhan vien (userId={})", userId, ex);
        }
        return null;
    }

    /** Bind Integer có thể null: null phải thành SQL NULL, không phải 0 (0 sẽ vi phạm khoá ngoại). */
    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    // findEligibleManagers (ô chọn "Cấp trên" trên form thêm/sửa) đã xoá cùng
    // với chính ô đó -- KHÔNG còn đường nào qua UI để gán/đổi manager_id, xem
    // PERMISSIONS.md. Cây tổ chức đã gán vẫn đọc/dùng nguyên (AccessControl,
    // findProvincesManagedBy...), chỉ mất chỗ NHẬP MỚI; đổi cấp trên hiện phải
    // sửa trực tiếp trong CSDL cho tới khi có UI khác thay thế.

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
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
        u.setDepartmentId(rs.getInt("department_id"));
        u.setDepartment(new Department(rs.getInt("department_id"), rs.getString("department_name")));
        int managerId = rs.getInt("manager_id");
        u.setManagerId(rs.wasNull() ? null : managerId);
        u.setHireDate(rs.getDate("hire_date"));
        u.setCreatedAt(rs.getTimestamp("created_at"));
        u.setDeleted(rs.getBoolean("is_deleted"));
        u.setMustChangePassword(rs.getBoolean("must_change_password"));
        return u;
    }

    // existsByEmail đã xoá cùng cột email công ty (V36, xem PERMISSIONS.md) --
    // BR-27 giờ chỉ còn SĐT/CCCD. personal_email (email cá nhân, cột khác)
    // không có ràng buộc UNIQUE nên vốn cũng chưa từng cần kiểm trùng.

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
            LOG.error("Loi kiem tra trung lap (column={}, excludeUserId={})", column, excludeUserId, ex);
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
            LOG.error("Loi kiem tra trung username (username={})", username, ex);
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
     *
     * <p>{@code must_change_password} luôn ghi '1': mật khẩu vừa tạo là mật
     * khẩu tạm (EmployeeController sinh ngẫu nhiên), nhân viên chưa hề biết
     * tới nó cho tới khi Admin bấm "Gửi thông tin tài khoản" -- xem V35.
     * gender/date_of_birth/citizen_id/phone từ V35 được phép NULL: không còn
     * ép Admin điền hết lúc tạo, nhân viên tự bổ sung ở lần đăng nhập đầu.
     */
    public int insert(User user) {
        String sql = "INSERT INTO users (username, password_hash, must_change_password, role_id, last_name, " +
                     "middle_name, first_name, gender, date_of_birth, citizen_id, phone, personal_email, address_id, " +
                     "department_id, hire_date, manager_id, is_deleted) VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer addressId = resolveAddressId(conn, user.getAddress());
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, user.getUsername());
                    ps.setString(2, user.getPasswordHash());
                    ps.setInt(3, user.getRoleId());
                    ps.setString(4, user.getLastName());
                    ps.setString(5, user.getMiddleName());
                    ps.setString(6, user.getFirstName());
                    ps.setString(7, user.getGender());
                    ps.setDate(8, user.getDateOfBirth());
                    ps.setString(9, user.getCitizenId());
                    ps.setString(10, user.getPhone());
                    ps.setString(11, user.getPersonalEmail());
                    if (addressId != null) {
                        ps.setInt(12, addressId);
                    } else {
                        ps.setNull(12, Types.INTEGER);
                    }
                    ps.setInt(13, user.getDepartmentId());
                    ps.setDate(14, user.getHireDate());
                    setNullableInt(ps, 15, user.getManagerId());
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
            LOG.error("Loi tao nhan vien moi (username={})", user.getUsername(), ex);
            return -1;
        }
    }

    /**
     * Cập nhật thông tin nhân viên do Admin sửa (UC-27/UC-28: gồm cả phòng
     * ban + vai trò, khác updateProfile() ở trên vốn chỉ cho tự sửa thông
     * tin cá nhân). KHÔNG đổi username/password ở đây -- username do hệ
     * thống tự sinh lúc tạo (gắn chết, không sửa lại), đổi mật khẩu có luồng
     * riêng (quên mật khẩu / đổi mật khẩu).
     */
    public boolean update(User user) {
        String sql = "UPDATE users SET role_id = ?, last_name = ?, middle_name = ?, first_name = ?, " +
                     "gender = ?, date_of_birth = ?, citizen_id = ?, phone = ?, personal_email = ?, address_id = ?, " +
                     "department_id = ?, hire_date = ?, manager_id = ? WHERE user_id = ?";
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer addressId = resolveAddressId(conn, user.getAddress());
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, user.getRoleId());
                    ps.setString(2, user.getLastName());
                    ps.setString(3, user.getMiddleName());
                    ps.setString(4, user.getFirstName());
                    ps.setString(5, user.getGender());
                    ps.setDate(6, user.getDateOfBirth());
                    ps.setString(7, user.getCitizenId());
                    ps.setString(8, user.getPhone());
                    ps.setString(9, user.getPersonalEmail());
                    if (addressId != null) {
                        ps.setInt(10, addressId);
                    } else {
                        ps.setNull(10, Types.INTEGER);
                    }
                    ps.setInt(11, user.getDepartmentId());
                    ps.setDate(12, user.getHireDate());
                    setNullableInt(ps, 13, user.getManagerId());
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
            LOG.error("Loi cap nhat nhan vien (userId={}, username={})", user.getUserId(), user.getUsername(), ex);
            return false;
        }
    }

    /**
     * Ghi đè password_hash bằng mật khẩu tạm mới -- dùng khi Admin bấm
     * "Gửi thông tin tài khoản" (FE-02): mỗi lần gửi/gửi lại đều cấp 1 mật
     * khẩu tạm mới thay vì lưu lại mật khẩu tạm cũ dạng plaintext ở đâu đó
     * chờ gửi (không an toàn), nên "gửi lại" thực chất là "cấp lại rồi gửi".
     *
     * <p>Luôn bật lại {@code must_change_password}: mật khẩu vừa ghi là mật
     * khẩu TẠM, kể cả khi nhân viên trước đó đã tự đổi và cờ này đã tắt --
     * Admin cấp lại nghĩa là mật khẩu cũ (do nhân viên tự đặt) không còn dùng
     * được nữa, phải ép đổi lại từ đầu. Xem V35, AuthenticationFilter.
     */
    public boolean updatePasswordHash(int userId, String passwordHash) {
        String sql = "UPDATE users SET password_hash = ?, must_change_password = 1 WHERE user_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            LOG.error("Loi cap nhat mat khau tam (userId={})", userId, ex);
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
            LOG.error("Loi khoa/mo khoa nhan vien (userId={})", userId, ex);
            return false;
        }
    }

    // ==================================================================
    // Phân công địa bàn (bảng user_provinces)
    // ==================================================================
    //
    // Chỉ lưu chiều TỈNH -> NGƯỜI. Người cầm tỉnh KHÔNG nhất thiết là nhân
    // viên tác nghiệp: khách hàng xác nhận 2026-09-15 rằng cấp trên cũng trực
    // tiếp cầm địa bàn và tự đi đàm phán -- nên phần này nhận mọi user, không
    // lọc theo vai trò hay theo tầng trong cây tổ chức.
    //
    // findProvincesManagedBy là thứ khác: địa bàn mà một người bao phủ QUA
    // CẤP DƯỚI. Không lưu, mà suy ra bằng cách gộp -- lưu tay cả hai thứ là
    // tạo ra hai nguồn sự thật rồi có ngày lệch nhau. Một người có thể có cả
    // hai: vài tỉnh tự cầm, vài tỉnh phủ qua lính.
    //
    // Nằm trong EmployeeDAO chứ không phải một DAO riêng: "ai cầm tỉnh nào"
    // là thuộc tính của nhân viên, cùng miền với cây tổ chức (manager_id) của
    // chính bảng users, và màn hình quản lý nó cũng là màn hình nhân viên.

    /** Các tỉnh mà {@code userId} trực tiếp cầm. */
    public List<Province> findProvincesOf(int userId) {
        String sql = "SELECT p.province_id, p.province_name " +
                     "FROM user_provinces up JOIN provinces p ON p.province_id = up.province_id " +
                     "WHERE up.user_id = ? ORDER BY p.province_name";
        return queryProvinces(sql, userId);
    }

    /**
     * Phạm vi "của tôi" trên Dashboard: chính người đó, cộng các cấp dưới trực
     * tiếp của họ.
     *
     * <p>MỘT tầng là đủ, không phải đi đệ quy: người đã là cấp dưới thì không
     * được chọn làm cấp trên của ai (xem điều kiện {@code manager_id IS NULL} ở
     * danh sách chọn cấp trên), nên cây nhân sự chỉ sâu hai tầng. Nếu sau này
     * luật đó nới ra thì đây là chỗ phải đổi.
     *
     * <p>Luôn chứa chính {@code userId}, kể cả khi người đó không quản lý ai --
     * bên gọi dùng danh sách này làm mệnh đề IN, và trả về rỗng sẽ bị hiểu là
     * "không lọc", tức là cho xem toàn bộ.
     */
    public List<Integer> findTeamUserIds(int userId) {
        List<Integer> ids = new ArrayList<>();
        ids.add(userId);
        String sql = "SELECT user_id FROM users WHERE manager_id = ? AND is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("user_id"));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van cap duoi (userId={})", userId, ex);
        }
        return ids;
    }

    /**
     * Cấp trên trực tiếp của từng người, dạng {@code user_id -> manager_id} --
     * chỉ gồm người CÓ cấp trên.
     *
     * <p>Dùng cho ô "Người hỗ trợ" của khách hàng: người hỗ trợ không được là
     * cấp trên trực tiếp của người phụ trách chính (chốt với người dùng
     * 2026-09-24). Trả cả bảng một lần như {@link #findAllAssignments()}: form
     * nhúng sẵn để lọc lại ngay khi đổi người phụ trách, không phải gọi AJAX.
     *
     * <p>Cây chưa có dữ liệu thì trả map rỗng -- luật tự chưa chặn ai, cùng
     * nguyên tắc "chưa xếp vào cây thì chưa bị siết" của AccessControl.
     */
    public Map<Integer, Integer> findManagerMap() {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        String sql = "SELECT user_id, manager_id FROM users WHERE is_deleted = 0 AND manager_id IS NOT NULL";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getInt("user_id"), rs.getInt("manager_id"));
            }
        } catch (SQLException ex) {
            LOG.error("Loi tra cay cap tren", ex);
        }
        return result;
    }

    /**
     * Địa bàn mà Dashboard coi là "của tôi": tỉnh mình trực tiếp cầm CỘNG tỉnh
     * của cấp dưới trực tiếp.
     *
     * <p>Gộp cả cấp dưới để khớp với ô "Của tôi" ngay cạnh nó -- ô đó vốn đã
     * gộp cấp dưới (xem {@link #findTeamUserIds}). Lấy riêng tỉnh mình cầm thì
     * trưởng nhóm mở trang ra thấy số người của cả nhóm mà địa bàn chỉ của
     * mình, hai vế của cùng một phép lọc nói hai chuyện khác nhau.
     *
     * <p>MỘT câu lệnh chứ không gộp hai lời gọi ở tầng trên: DISTINCT phải do
     * CSDL làm, người vừa tự cầm Hà Nội vừa có lính cầm Hà Nội thì gộp tay ra
     * hai dòng trùng.
     */
    public List<Province> findProvincesCoveredBy(int userId) {
        String sql = "SELECT DISTINCT p.province_id, p.province_name " +
                     "FROM user_provinces up " +
                     "JOIN users u ON u.user_id = up.user_id AND u.is_deleted = 0 " +
                     "JOIN provinces p ON p.province_id = up.province_id " +
                     "WHERE up.user_id = ? OR u.manager_id = ? ORDER BY p.province_name";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            return readProvinces(ps);
        } catch (SQLException ex) {
            LOG.error("Loi tra dia ban cua nguoi dung (userId={})", userId, ex);
            return new ArrayList<>();
        }
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
     * Người cầm tỉnh chứa xã/phường này, hoặc null nếu tỉnh đó chưa ai cầm.
     *
     * Suy từ xã/phường chứ KHÔNG nhận provinceId rời từ request: form gửi lên
     * cả hai ô, nhưng chỉ ô xã/phường mới thực sự đi vào địa chỉ khách hàng.
     * Tin vào provinceId rời thì một request nặn tay khai được tỉnh A để lấy
     * người của tỉnh A trong khi địa chỉ nằm ở tỉnh B -- đúng cái mà khoá ô
     * người phụ trách sinh ra để chặn. Đó cũng là lý do ở đây KHÔNG có bản
     * tra theo province_id: có là mời người ta dùng nhầm.
     *
     * "Chưa ai cầm" là trạng thái hợp lệ và là mặc định lúc mới bật tính năng
     * -- bên gọi phải xử lý null chứ không được coi là lỗi.
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
     * Toàn bộ tỉnh/thành, kèm người đang cầm (nếu có) -- dùng cho form
     * thêm/sửa nhân viên. Đổ CẢ tỉnh đã có người cầm ra màn hình (khác {@code
     * findSelectableProvinces} cũ, đã xoá) vì Admin cần thấy NGAY ai đang giữ
     * tỉnh đó thay vì tự hỏi "sao tỉnh này biến mất khỏi ô chọn" -- JSP tự
     * khoá checkbox của tỉnh thuộc NGƯỜI KHÁC, dựa vào {@code holderUserId}.
     *
     * <p>KHÔNG lọc theo {@code is_deleted} của người cầm, khác {@link
     * #findAllAssignments()}: tỉnh do một tài khoản đã bị khoá cầm vẫn CHIẾM
     * đúng một dòng UNIQUE trong {@code user_provinces} -- INSERT cho người
     * khác vẫn vi phạm ràng buộc đó dù người cầm cũ không còn hoạt động, nên
     * ô chọn phải khoá đúng như CSDL sẽ khoá, không phải khoá theo cảm tính.
     */
    public List<ProvinceAssignment> findAllProvincesWithHolder() {
        String sql = "SELECT p.province_id, p.province_name, up.user_id AS holder_id, " +
                     "u.last_name, u.middle_name, u.first_name " +
                     "FROM provinces p " +
                     "LEFT JOIN user_provinces up ON up.province_id = p.province_id " +
                     "LEFT JOIN users u ON u.user_id = up.user_id " +
                     "ORDER BY p.province_name";
        List<ProvinceAssignment> result = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int holderIdRaw = rs.getInt("holder_id");
                Integer holderId = rs.wasNull() ? null : holderIdRaw;
                String holderName = null;
                if (holderId != null) {
                    // Tái dùng đúng luật ghép họ tên của User.getFullName()
                    // thay vì chép lại CONCAT trong SQL, để hai nơi không lệch
                    // nhau khi luật đó đổi (vd. thêm học vị/chức danh).
                    User holder = new User();
                    holder.setLastName(rs.getString("last_name"));
                    holder.setMiddleName(rs.getString("middle_name"));
                    holder.setFirstName(rs.getString("first_name"));
                    holderName = holder.getFullName();
                }
                result.add(new ProvinceAssignment(rs.getInt("province_id"), rs.getString("province_name"),
                        holderId, holderName));
            }
        } catch (SQLException ex) {
            LOG.error("Loi tra danh sach tinh kem nguoi cam", ex);
        }
        return result;
    }

    private List<Province> queryProvinces(String sql, int param) {
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, param);
            return readProvinces(ps);
        } catch (SQLException ex) {
            LOG.error("Loi truy van dia ban (param={})", param, ex);
            return new ArrayList<>();
        }
    }

    /** Đọc kết quả thành danh sách tỉnh -- dùng chung cho các câu tra địa bàn. */
    private static List<Province> readProvinces(PreparedStatement ps) throws SQLException {
        List<Province> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Province(rs.getInt("province_id"), rs.getString("province_name")));
            }
        }
        return result;
    }
}
