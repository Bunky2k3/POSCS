package poscs.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mindrot.jbcrypt.BCrypt;
import poscs.common.EmailUtil;
import poscs.dao.AddressDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Department;
import poscs.model.Province;
import poscs.model.ProvinceAssignment;
import poscs.model.Role;
import poscs.model.User;
import poscs.validator.EmployeeValidator;
import poscs.validator.ValidationResult;

/**
 * Service xử lý nghiệp vụ Quản lý nhân viên (UC-24..29).
 * Đáp ứng: BR-09, BR-10, BR-25..30.
 *
 * <p>Mã lỗi ánh xạ sang UI:
 * <ul>
 *   <li>{@code ERR_FORBIDDEN} -> MSG-022: Vượt thẩm quyền</li>
 *   <li>{@code ERR_DUPLICATE_PHONE} -> MSG-033: Trùng số điện thoại</li>
 *   <li>{@code ERR_DUPLICATE_CITIZEN} -> MSG-033: Trùng CCCD/CMND</li>
 *   <li>{@code ERR_CANNOT_SELF_BAN} -> "Không thể tự khóa tài khoản đang đăng nhập..."</li>
 *   <li>{@code ERR_PROVINCE_TAKEN} -> "Có tỉnh trong ô Địa bàn vừa được giao..."</li>
 * </ul>
 */
public class EmployeeService extends BaseService {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final String ADMIN_ROLE_NAME = EmployeeValidator.ADMIN_ROLE_NAME;
    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    public static final String ERR_FORBIDDEN = PermissionService.MSG_022;
    public static final String ERR_NOT_FOUND = "notfound";
    public static final String ERR_INVALID = EmployeeValidator.ERR_INVALID;
    public static final String ERR_DUPLICATE_PHONE = EmployeeValidator.ERR_DUPLICATE_PHONE;
    public static final String ERR_DUPLICATE_CITIZEN = EmployeeValidator.ERR_DUPLICATE_CITIZEN;
    public static final String ERR_PROVINCE_TAKEN = EmployeeValidator.ERR_PROVINCE_TAKEN;
    public static final String ERR_PROVINCE_NOT_SAVED = "province_not_saved";
    public static final String ERR_CREATE_FAILED = "create_failed";
    public static final String ERR_UPDATE_FAILED = "update_failed";
    public static final String ERR_CANNOT_SELF_BAN = "cannot_self_ban";
    public static final String ERR_TOGGLE_FAILED = "toggle_failed";
    public static final String ERR_NO_PERSONAL_EMAIL = "no_personal_email";
    public static final String ERR_MAIL_FAILED = "mail_failed";
    public static final String ERR_SEND_FAILED = "send_failed";

    @FunctionalInterface
    public interface AccountEmailSender {
        boolean send(String toEmail, String fullName, String username, String tempPassword);
    }

    private final EmployeeDAO employeeDAO;
    private final AddressDAO addressDAO;
    private final PermissionService permissionService;
    private final EmployeeValidator employeeValidator;
    private final AccountEmailSender emailSender;
    private final SecureRandom random = new SecureRandom();

    public EmployeeService() {
        this(new EmployeeDAO(), new AddressDAO(), new PermissionService(),
                new EmployeeValidator(), EmailUtil::sendNewAccountEmail);
    }

    public EmployeeService(EmployeeDAO employeeDAO, AddressDAO addressDAO,
            PermissionService permissionService, EmployeeValidator employeeValidator) {
        this(employeeDAO, addressDAO, permissionService, employeeValidator,
                EmailUtil::sendNewAccountEmail);
    }

    public EmployeeService(EmployeeDAO employeeDAO, AddressDAO addressDAO,
            PermissionService permissionService, EmployeeValidator employeeValidator,
            AccountEmailSender emailSender) {
        this.employeeDAO = employeeDAO;
        this.addressDAO = addressDAO;
        this.permissionService = permissionService;
        this.employeeValidator = employeeValidator;
        this.emailSender = emailSender != null ? emailSender : EmailUtil::sendNewAccountEmail;
    }

    public List<User> findEmployees(int page, int pageSize, String keyword,
            String statusFilter, Integer roleFilter, User actor) {
        if (!permissionService.canManageEmployees(actor)) return List.of();
        return employeeDAO.findAll(page, pageSize, keyword, statusFilter, roleFilter);
    }

    public int countEmployees(String keyword, String statusFilter, Integer roleFilter, User actor) {
        if (!permissionService.canManageEmployees(actor)) return 0;
        return employeeDAO.countAll(keyword, statusFilter, roleFilter);
    }

    public User findEmployeeDetail(int userId, User actor) {
        if (!permissionService.canManageEmployees(actor)) return null;
        return employeeDAO.findById(userId);
    }

    public List<Province> findProvincesOf(int userId) {
        return employeeDAO.findProvincesOf(userId);
    }

    public List<Province> findProvincesManagedBy(int managerId) {
        return employeeDAO.findProvincesManagedBy(managerId);
    }

    public List<Role> findAllRoles() {
        return employeeDAO.findAllRoles();
    }

    public List<Department> findAllDepartments() {
        return employeeDAO.findAllDepartments();
    }

    public Set<Integer> getAdminRoleIds() {
        Set<Integer> set = new HashSet<>();
        for (Role r : employeeDAO.findAllRoles()) {
            if (ADMIN_ROLE_NAME.equals(r.getRoleName())) set.add(r.getRoleId());
        }
        return set;
    }

    public boolean isAdminRole(int roleId) {
        return getAdminRoleIds().contains(roleId);
    }

    public List<Role> assignableRoles(Integer editingRoleId) {
        List<Role> result = new ArrayList<>();
        for (Role r : employeeDAO.findAllRoles()) {
            if (!ADMIN_ROLE_NAME.equals(r.getRoleName())
                    || (editingRoleId != null && r.getRoleId() == editingRoleId)) {
                result.add(r);
            }
        }
        return result;
    }

    public List<ProvinceAssignment> branchProvinceAssignments(Integer editingEmployeeId) {
        Set<Integer> branchIds = new HashSet<>();
        for (Province p : addressDAO.findBranchProvinces()) {
            branchIds.add(p.getProvinceId());
        }
        List<ProvinceAssignment> result = new ArrayList<>();
        for (ProvinceAssignment pa : employeeDAO.findAllProvincesWithHolder()) {
            boolean inBranch = branchIds.contains(pa.getProvinceId());
            boolean heldByEditing = editingEmployeeId != null && pa.getHolderUserId() != null
                    && pa.getHolderUserId().equals(editingEmployeeId);
            if (inBranch || heldByEditing) result.add(pa);
        }
        return result;
    }

    public String generateTempPassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(random.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private void sanitizeInput(User u) {
        u.setPhone(EmployeeValidator.normalizePhone(u.getPhone()));
        u.setCitizenId(EmployeeValidator.normalizeCitizenId(u.getCitizenId()));
        u.setPersonalEmail(EmployeeValidator.trimToNull(u.getPersonalEmail()));
        u.setLastName(EmployeeValidator.trimToNull(u.getLastName()));
        u.setMiddleName(EmployeeValidator.trimToNull(u.getMiddleName()));
        u.setFirstName(EmployeeValidator.trimToNull(u.getFirstName()));
    }

    public ServiceResult<Integer> createEmployee(User user, List<Integer> provinceIds, User actor) {
        // MSG-022: Kiểm tra quyền quản lý nhân viên (Admin only)
        if (!permissionService.canManageEmployees(actor)) return ServiceResult.fail(ERR_FORBIDDEN);
        if (user == null) return ServiceResult.fail(ERR_INVALID);

        sanitizeInput(user);
        // BR-28: Kiểm tra bắt buộc Họ, Tên, Phòng ban, Vai trò, Ngày vào làm, Email; ô Vai trò không có Admin
        ValidationResult vr = employeeValidator.validateCreate(user, getAdminRoleIds());
        if (!vr.isValid()) return ServiceResult.fail(vr);

        // BR-27 / MSG-033: SĐT và CCCD duy nhất (chỉ kiểm tra khi có nhập)
        if (user.getPhone() != null && employeeDAO.existsByPhone(user.getPhone(), null)) {
            return ServiceResult.fail(ERR_DUPLICATE_PHONE);
        }
        if (user.getCitizenId() != null && employeeDAO.existsByCitizenId(user.getCitizenId(), null)) {
            return ServiceResult.fail(ERR_DUPLICATE_CITIZEN);
        }
        // Địa bàn mỗi tỉnh một người phụ trách
        if (employeeValidator.anyProvinceHeldByOthers(provinceIds,
                employeeDAO.findAllProvincesWithHolder(), null)) {
            return ServiceResult.fail(ERR_PROVINCE_TAKEN);
        }

        // UC-26: Tự sinh username và mật khẩu tạm ngẫu nhiên băm BCrypt
        user.setUsername(employeeDAO.generateUniqueUsername(user.getLastName(),
                user.getMiddleName(), user.getFirstName()));
        user.setPasswordHash(BCrypt.hashpw(generateTempPassword(), BCrypt.gensalt()));

        int newId = employeeDAO.insert(user);
        if (newId <= 0) return ServiceResult.fail(ERR_CREATE_FAILED);

        // SPEC dòng 237: Lưu hồ sơ xong mới lưu địa bàn (nếu địa bàn lỗi: hồ sơ đã lưu, báo rõ chưa lưu)
        if (!employeeDAO.replaceProvincesOf(newId, provinceIds)) {
            return ServiceResult.ok(newId, ERR_PROVINCE_NOT_SAVED);
        }
        return ServiceResult.ok(newId);
    }

    public ServiceResult<Integer> updateEmployee(User user, List<Integer> provinceIds, User actor) {
        // MSG-022: Kiểm tra quyền quản lý nhân viên (Admin only)
        if (!permissionService.canManageEmployees(actor)) return ServiceResult.fail(ERR_FORBIDDEN);
        if (user == null) return ServiceResult.fail(ERR_INVALID);
        User existing = employeeDAO.findById(user.getUserId());
        if (existing == null) return ServiceResult.fail(ERR_NOT_FOUND);

        sanitizeInput(user);
        user.setManagerId(existing.getManagerId());
        // BR-28: Kiểm tra bắt buộc và định dạng; không cho phép thăng cấp lên Admin
        ValidationResult vr = employeeValidator.validateUpdate(user, existing, getAdminRoleIds());
        if (!vr.isValid()) return ServiceResult.fail(vr);

        // BR-27 / MSG-033: SĐT và CCCD duy nhất (chỉ kiểm tra khi có nhập)
        if (user.getPhone() != null && employeeDAO.existsByPhone(user.getPhone(), user.getUserId())) {
            return ServiceResult.fail(ERR_DUPLICATE_PHONE);
        }
        if (user.getCitizenId() != null && employeeDAO.existsByCitizenId(user.getCitizenId(), user.getUserId())) {
            return ServiceResult.fail(ERR_DUPLICATE_CITIZEN);
        }
        // Địa bàn mỗi tỉnh một người phụ trách
        if (employeeValidator.anyProvinceHeldByOthers(provinceIds,
                employeeDAO.findAllProvincesWithHolder(), user.getUserId())) {
            return ServiceResult.fail(ERR_PROVINCE_TAKEN);
        }

        if (!employeeDAO.update(user)) return ServiceResult.fail(ERR_UPDATE_FAILED);
        // SPEC dòng 237: Lưu hồ sơ xong mới lưu địa bàn
        if (!employeeDAO.replaceProvincesOf(user.getUserId(), provinceIds)) {
            return ServiceResult.ok(user.getUserId(), ERR_PROVINCE_NOT_SAVED);
        }
        return ServiceResult.ok(user.getUserId());
    }

    public ServiceResult<Integer> sendAccountInformation(int userId, User actor) {
        // MSG-022: Kiểm tra quyền quản lý nhân viên (Admin only)
        if (!permissionService.canManageEmployees(actor)) return ServiceResult.fail(ERR_FORBIDDEN);
        User employee = employeeDAO.findById(userId);
        if (employee == null) return ServiceResult.fail(ERR_NOT_FOUND);

        // UC-28: Chưa có email cá nhân -> dừng, không đổi mật khẩu
        String email = EmployeeValidator.trimToNull(employee.getPersonalEmail());
        if (email == null) {
            return ServiceResult.fail(ERR_NO_PERSONAL_EMAIL);
        }

        String tempPass = generateTempPassword();
        // UC-28: Gửi email thành công TRƯỚC, chỉ khi thành công mới băm BCrypt và ghi đè
        boolean sent = emailSender.send(email, employee.getFullName(),
                employee.getUsername(), tempPass);
        if (!sent) return ServiceResult.fail(ERR_MAIL_FAILED);

        boolean updated = employeeDAO.updatePasswordHash(userId, BCrypt.hashpw(tempPass, BCrypt.gensalt()));
        if (!updated) return ServiceResult.fail(ERR_SEND_FAILED);
        return ServiceResult.ok(userId);
    }

    public ServiceResult<Boolean> toggleEmployeeStatus(int targetUserId, User actor) {
        // MSG-022: Kiểm tra quyền quản lý nhân viên (Admin only)
        if (!permissionService.canManageEmployees(actor)) return ServiceResult.fail(ERR_FORBIDDEN);
        User target = employeeDAO.findById(targetUserId);
        if (target == null) return ServiceResult.fail(ERR_NOT_FOUND);

        // BR-26 / UC-29: Không tự khóa chính tài khoản Admin đang đăng nhập
        if (actor != null && actor.getUserId() == target.getUserId()) {
            return ServiceResult.fail(ERR_CANNOT_SELF_BAN);
        }

        // BR-25: Inactive (is_deleted=1) ẩn khỏi DS đang hoạt động; đảo trạng thái
        boolean makeActive = target.isDeleted();
        if (!employeeDAO.setActive(targetUserId, makeActive)) {
            return ServiceResult.fail(ERR_TOGGLE_FAILED);
        }
        return ServiceResult.ok(makeActive);
    }
}
