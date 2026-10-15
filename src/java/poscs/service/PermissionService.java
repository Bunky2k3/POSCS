package poscs.service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import poscs.common.AccessControl;
import poscs.common.ListScope;
import poscs.dao.EmployeeDAO;
import poscs.model.Address;
import poscs.model.District;
import poscs.model.Enterprise;
import poscs.model.Province;
import poscs.model.User;

/**
 * Service trung tâm gom toàn bộ logic phân quyền (RBAC) của hệ thống POSCS.
 *
 * <p>Quy tắc nghiệp vụ:
 * <ul>
 *   <li>Ma trận 3 vai trò: Admin, Sales, Kỹ thuật (không có vai trò CS - NFR-10).</li>
 *   <li>Sales quản lý vùng (không có cấp trên): được thực hiện các thao tác ghi (*).</li>
 *   <li>Sales có cấp trên: chỉ xem; muốn đổi thì gửi yêu cầu thay đổi (UC-50).</li>
 *   <li>Cấp trên, trạng thái và vai trò được đối chiếu trực tiếp từ CSDL qua {@link EmployeeDAO}.</li>
 *   <li>Phạm vi dữ liệu "Của tôi" (ListScope) theo người phụ trách/cấp dưới/tỉnh được giao.</li>
 *   <li>Mọi vi phạm quyền đều trả về thông báo MSG-022: "Bạn không có quyền truy cập nội dung này."</li>
 * </ul>
 *
 * <p>Không phụ thuộc vào Servlet API.
 */
public class PermissionService extends BaseService {

    public static final String ROLE_ADMIN = "Admin";
    public static final String ROLE_SALES = "Sales";
    public static final String ROLE_TECHNICAL = "Kỹ thuật";

    public static final String ROLE_BUYER = "Khách mua";
    public static final String ROLE_SUPPLIER = "Nhà cung cấp";

    /** Thông báo hệ thống khi vi phạm quyền truy cập (MSG-022). */
    public static final String MSG_022 = "Bạn không có quyền truy cập nội dung này.";

    private final EmployeeDAO employeeDAO;

    public PermissionService() {
        this(new EmployeeDAO());
    }

    public PermissionService(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    // ==================================================================
    // 1. Phân biệt vai trò & Cấp bậc tổ chức (Đọc DB theo thời gian thực)
    // ==================================================================

    /**
     * Lấy thông tin người dùng có hiệu lực nhất từ CSDL.
     *
     * <p>Để bảo đảm an toàn dữ liệu, các thao tác kiểm tra quyền đối chiếu lại
     * vai trò và trạng thái khóa/xóa trực tiếp từ CSDL.
     */
    public User getEffectiveUser(User sessionUser) {
        if (sessionUser == null || sessionUser.isDeleted()) {
            return null;
        }
        if (employeeDAO != null && sessionUser.getUserId() > 0) {
            User dbUser = employeeDAO.findById(sessionUser.getUserId());
            if (dbUser == null || dbUser.isDeleted()) {
                return null;
            }
            return dbUser;
        }
        return sessionUser.getRole() != null ? sessionUser : null;
    }

    public boolean isActiveUser(User user) {
        User effective = getEffectiveUser(user);
        return effective != null && !effective.isDeleted() && effective.getRole() != null;
    }

    public boolean isAdmin(User user) {
        User effective = getEffectiveUser(user);
        return effective != null && !effective.isDeleted() && effective.getRole() != null
                && ROLE_ADMIN.equals(effective.getRole().getRoleName());
    }

    public boolean isSales(User user) {
        User effective = getEffectiveUser(user);
        return effective != null && !effective.isDeleted() && effective.getRole() != null
                && ROLE_SALES.equals(effective.getRole().getRoleName());
    }

    public boolean isTechnical(User user) {
        User effective = getEffectiveUser(user);
        return effective != null && !effective.isDeleted() && effective.getRole() != null
                && ROLE_TECHNICAL.equals(effective.getRole().getRoleName());
    }

    /**
     * Đọc cấp trên trực tiếp (manager_id) của người dùng từ CSDL.
     */
    public Integer findManagerId(int userId) {
        if (userId <= 0 || employeeDAO == null) {
            return null;
        }
        User dbUser = employeeDAO.findById(userId);
        return (dbUser != null && !dbUser.isDeleted()) ? dbUser.getManagerId() : null;
    }

    /**
     * Kiểm tra người dùng có phải là cấp dưới (có cấp trên) hay không.
     * Cấp trên được đọc trực tiếp từ CSDL.
     *
     * <p>Admin không bao giờ bị coi là cấp dưới. Nếu không xác minh được từ DB
     * (bản ghi không tồn tại hoặc lỗi kết nối), người dùng bị coi là bị hạn chế (fail-closed).
     */
    public boolean isSubordinate(User user) {
        if (user == null) {
            return false;
        }
        if (isAdmin(user)) {
            return false;
        }
        if (employeeDAO != null && user.getUserId() > 0) {
            User dbUser = employeeDAO.findById(user.getUserId());
            if (dbUser == null || dbUser.isDeleted()) {
                return true; // Không xác minh được: coi như bị hạn chế quyền ghi
            }
            return dbUser.getManagerId() != null;
        }
        return user.getManagerId() != null;
    }

    /**
     * Sales quản lý vùng = Sales KHÔNG có cấp trên trong DB (manager_id null).
     */
    public boolean isSalesRegionalManager(User user) {
        return isSales(user) && !isSubordinate(user);
    }

    /**
     * Sales có cấp trên = Sales có manager_id trong DB.
     */
    public boolean isSalesStaff(User user) {
        return isSales(user) && isSubordinate(user);
    }

    /**
     * Kiểm tra điều kiện quyền chung, vi phạm trả MSG-022.
     */
    public ServiceResult<Void> checkPermission(boolean allowed) {
        if (allowed) {
            return ServiceResult.ok(null);
        }
        return ServiceResult.fail(MSG_022);
    }

    // ==================================================================
    // 2. Khách hàng / Nhà cung cấp (Enterprises - UC-08, 09, 30..32, 37, 38)
    // ==================================================================

    public boolean canViewCustomers(User user) {
        return isAdmin(user) || isSales(user) || isTechnical(user);
    }

    public ServiceResult<Void> checkCanViewCustomers(User user) {
        return checkPermission(canViewCustomers(user));
    }

    public boolean canManageCustomers(User user) {
        return isAdmin(user) || isSalesRegionalManager(user);
    }

    public ServiceResult<Void> checkCanManageCustomers(User user) {
        return checkPermission(canManageCustomers(user));
    }

    public boolean canCreateCustomer(User user) {
        return canManageCustomers(user);
    }

    public ServiceResult<Void> checkCanCreateCustomer(User user) {
        return checkPermission(canCreateCustomer(user));
    }

    public boolean canUpdateCustomer(User user) {
        return canManageCustomers(user);
    }

    public ServiceResult<Void> checkCanUpdateCustomer(User user) {
        return checkPermission(canUpdateCustomer(user));
    }

    public boolean canUpdateCustomer(User user, Enterprise customer, List<String> roles) {
        if (!canUpdateCustomer(user)) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        return isCustomerInScope(user, customer, roles);
    }

    public ServiceResult<Void> checkCanUpdateCustomer(User user, Enterprise customer, List<String> roles) {
        return checkPermission(canUpdateCustomer(user, customer, roles));
    }

    public boolean canDeleteCustomer(User user) {
        return canManageCustomers(user);
    }

    public ServiceResult<Void> checkCanDeleteCustomer(User user) {
        return checkPermission(canDeleteCustomer(user));
    }

    public boolean canDeleteCustomer(User user, Enterprise customer, List<String> roles) {
        if (!canDeleteCustomer(user)) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        return isCustomerInScope(user, customer, roles);
    }

    public ServiceResult<Void> checkCanDeleteCustomer(User user, Enterprise customer, List<String> roles) {
        return checkPermission(canDeleteCustomer(user, customer, roles));
    }

    public boolean canRateCustomer(User user) {
        return canManageCustomers(user);
    }

    public ServiceResult<Void> checkCanRateCustomer(User user) {
        return checkPermission(canRateCustomer(user));
    }

    public boolean canExportCustomers(User user) {
        return canViewCustomers(user);
    }

    public ServiceResult<Void> checkCanExportCustomers(User user) {
        return checkPermission(canExportCustomers(user));
    }

    // ==================================================================
    // 3. Sản phẩm (Products - UC-12, 13, 18, 19, 20)
    // ==================================================================

    public boolean canViewProducts(User user) {
        return isAdmin(user) || isSales(user) || isTechnical(user);
    }

    public ServiceResult<Void> checkCanViewProducts(User user) {
        return checkPermission(canViewProducts(user));
    }

    public boolean canManageProducts(User user) {
        return isAdmin(user) || isTechnical(user);
    }

    public ServiceResult<Void> checkCanManageProducts(User user) {
        return checkPermission(canManageProducts(user));
    }

    public boolean canCreateProduct(User user) {
        return canManageProducts(user);
    }

    public ServiceResult<Void> checkCanCreateProduct(User user) {
        return checkPermission(canCreateProduct(user));
    }

    public boolean canUpdateProduct(User user) {
        return canManageProducts(user);
    }

    public ServiceResult<Void> checkCanUpdateProduct(User user) {
        return checkPermission(canUpdateProduct(user));
    }

    public boolean canDeleteProduct(User user) {
        return canManageProducts(user);
    }

    public ServiceResult<Void> checkCanDeleteProduct(User user) {
        return checkPermission(canDeleteProduct(user));
    }

    // ==================================================================
    // 4. Hợp đồng (Contracts - UC-10, 11, 33..35, 39, 41, 44..49, 52..54)
    // ==================================================================

    public boolean canViewContracts(User user) {
        return isAdmin(user) || isSales(user) || isTechnical(user);
    }

    public ServiceResult<Void> checkCanViewContracts(User user) {
        return checkPermission(canViewContracts(user));
    }

    public boolean canManageContracts(User user) {
        return isAdmin(user) || isSalesRegionalManager(user);
    }

    public ServiceResult<Void> checkCanManageContracts(User user) {
        return checkPermission(canManageContracts(user));
    }

    public boolean canCreateContract(User user) {
        return canManageContracts(user);
    }

    public ServiceResult<Void> checkCanCreateContract(User user) {
        return checkPermission(canCreateContract(user));
    }

    public boolean canUpdateContract(User user) {
        return canManageContracts(user);
    }

    public ServiceResult<Void> checkCanUpdateContract(User user) {
        return checkPermission(canUpdateContract(user));
    }

    public boolean canSignContract(User user) {
        return canManageContracts(user);
    }

    public ServiceResult<Void> checkCanSignContract(User user) {
        return checkPermission(canSignContract(user));
    }

    /**
     * Huỷ bản ghi hợp đồng:
     * - Bản Nháp: Admin hoặc Sales quản lý vùng.
     * - Bản Đã ký trở đi: CHỈ Admin (Sales quản lý vùng cũng không được huỷ).
     */
    public boolean canVoidContract(User user, boolean isDraft) {
        if (!isActiveUser(user)) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        if (isDraft) {
            return isSalesRegionalManager(user);
        }
        return false;
    }

    public ServiceResult<Void> checkCanVoidContract(User user, boolean isDraft) {
        return checkPermission(canVoidContract(user, isDraft));
    }

    public boolean canCreateAmendment(User user) {
        return canManageContracts(user);
    }

    public ServiceResult<Void> checkCanCreateAmendment(User user) {
        return checkPermission(canCreateAmendment(user));
    }

    public boolean canLinkContracts(User user) {
        return canManageContracts(user);
    }

    public ServiceResult<Void> checkCanLinkContracts(User user) {
        return checkPermission(canLinkContracts(user));
    }

    public boolean canInitiateHandover(User user) {
        return canManageContracts(user);
    }

    public ServiceResult<Void> checkCanInitiateHandover(User user) {
        return checkPermission(canInitiateHandover(user));
    }

    /**
     * Xác nhận xử lý bàn giao (UC-49): Admin hoặc nhân viên thuộc đúng phòng ban nhận bàn giao.
     */
    public boolean canConfirmHandover(User user, int targetDepartmentId) {
        if (!isActiveUser(user)) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        return targetDepartmentId > 0 && user.getDepartmentId() == targetDepartmentId;
    }

    public ServiceResult<Void> checkCanConfirmHandover(User user, int targetDepartmentId) {
        return checkPermission(canConfirmHandover(user, targetDepartmentId));
    }

    public boolean canManageContractDocuments(User user) {
        return canManageContracts(user);
    }

    public ServiceResult<Void> checkCanManageContractDocuments(User user) {
        return checkPermission(canManageContractDocuments(user));
    }

    public boolean canManageContractPayments(User user) {
        return canManageContracts(user);
    }

    public ServiceResult<Void> checkCanManageContractPayments(User user) {
        return checkPermission(canManageContractPayments(user));
    }

    public boolean canManageContractProducts(User user) {
        return canManageContracts(user);
    }

    public ServiceResult<Void> checkCanManageContractProducts(User user) {
        return checkPermission(canManageContractProducts(user));
    }

    /**
     * Chữa sai sót nhập liệu HĐ đã ký (UC-54): CHỈ Admin.
     */
    public boolean canCorrectSignedContract(User user) {
        return isAdmin(user);
    }

    public ServiceResult<Void> checkCanCorrectSignedContract(User user) {
        return checkPermission(canCorrectSignedContract(user));
    }

    public boolean canExportContracts(User user) {
        return canViewContracts(user);
    }

    public ServiceResult<Void> checkCanExportContracts(User user) {
        return checkPermission(canExportContracts(user));
    }

    // ==================================================================
    // 5. Yêu cầu thay đổi (Change Requests - UC-50, 51)
    // ==================================================================

    /**
     * Gửi yêu cầu thay đổi (UC-50): nhân viên có cấp trên trong cây tổ chức.
     */
    public boolean canSubmitChangeRequest(User user) {
        return isSubordinate(user);
    }

    public ServiceResult<Void> checkCanSubmitChangeRequest(User user) {
        return checkPermission(canSubmitChangeRequest(user));
    }

    /**
     * Duyệt / từ chối yêu cầu thay đổi (UC-51): Admin hoặc quản lý trực tiếp của người gửi.
     */
    public boolean canReviewChangeRequest(User user, int requesterUserId) {
        if (!isActiveUser(user) || requesterUserId <= 0) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        Integer managerId = findManagerId(requesterUserId);
        return managerId != null && managerId.intValue() == user.getUserId();
    }

    public ServiceResult<Void> checkCanReviewChangeRequest(User user, int requesterUserId) {
        return checkPermission(canReviewChangeRequest(user, requesterUserId));
    }

    // ==================================================================
    // 6. Phiếu hỗ trợ kỹ thuật (Technical Requests - UC-16, 17, 21..23, 42)
    // ==================================================================

    public boolean canViewTickets(User user) {
        return isAdmin(user) || isSales(user) || isTechnical(user);
    }

    public ServiceResult<Void> checkCanViewTickets(User user) {
        return checkPermission(canViewTickets(user));
    }

    /**
     * Tạo phiếu: Admin và Sales (cả quản lý vùng lẫn có cấp trên đều được tạo).
     */
    public boolean canCreateTicket(User user) {
        return isAdmin(user) || isSales(user);
    }

    public ServiceResult<Void> checkCanCreateTicket(User user) {
        return checkPermission(canCreateTicket(user));
    }

    /**
     * Cập nhật phiếu (UC-22):
     * - Admin và Sales: toàn quyền.
     * - Kỹ thuật: CHỈ được cập nhật phiếu được giao cho chính mình.
     */
    public boolean canUpdateTicket(User user, Integer assignedTechnicianId) {
        if (!isActiveUser(user)) {
            return false;
        }
        if (isAdmin(user) || isSales(user)) {
            return true;
        }
        if (isTechnical(user)) {
            return assignedTechnicianId != null && assignedTechnicianId.intValue() == user.getUserId();
        }
        return false;
    }

    public ServiceResult<Void> checkCanUpdateTicket(User user, Integer assignedTechnicianId) {
        return checkPermission(canUpdateTicket(user, assignedTechnicianId));
    }

    public boolean canDeleteTicket(User user) {
        return isAdmin(user) || isSales(user);
    }

    public ServiceResult<Void> checkCanDeleteTicket(User user) {
        return checkPermission(canDeleteTicket(user));
    }

    public boolean canExportTickets(User user) {
        return canViewTickets(user);
    }

    public ServiceResult<Void> checkCanExportTickets(User user) {
        return checkPermission(canExportTickets(user));
    }

    // ==================================================================
    // 7. Quản lý nhân viên & Nhật ký hệ thống (Admin Only - UC-24..29, 43)
    // ==================================================================

    public boolean canManageEmployees(User user) {
        return isAdmin(user);
    }

    public ServiceResult<Void> checkCanManageEmployees(User user) {
        return checkPermission(canManageEmployees(user));
    }

    public boolean canViewSystemLogs(User user) {
        return isAdmin(user);
    }

    public ServiceResult<Void> checkCanViewSystemLogs(User user) {
        return checkPermission(canViewSystemLogs(user));
    }

    public boolean canViewNotifications(User user) {
        return isActiveUser(user);
    }

    public ServiceResult<Void> checkCanViewNotifications(User user) {
        return checkPermission(canViewNotifications(user));
    }

    // ==================================================================
    // 8. Phạm vi dữ liệu "Của tôi" & Địa bàn (ListScope & Row-level Scope)
    // ==================================================================

    public List<Integer> getTeamUserIds(int userId) {
        if (userId <= 0 || employeeDAO == null) {
            return Collections.emptyList();
        }
        List<Integer> ids = employeeDAO.findTeamUserIds(userId);
        return ids != null ? ids : List.of(userId);
    }

    public List<Integer> getAssignedProvinceIds(int userId) {
        if (userId <= 0 || employeeDAO == null) {
            return Collections.emptyList();
        }
        List<Province> provinces = employeeDAO.findProvincesOf(userId);
        if (provinces == null || provinces.isEmpty()) {
            return Collections.emptyList();
        }
        return provinces.stream().map(Province::getProvinceId).collect(Collectors.toList());
    }

    public List<Province> getAssignedProvinces(int userId) {
        if (userId <= 0 || employeeDAO == null) {
            return Collections.emptyList();
        }
        List<Province> provinces = employeeDAO.findProvincesOf(userId);
        return provinces != null ? provinces : Collections.emptyList();
    }

    public ListScope getCustomerListScope(User user, String viewMode) {
        if (!isActiveUser(user) || !canViewCustomers(user)) {
            return ListScope.of(List.of(-1), Collections.emptyList());
        }
        if (isAdmin(user) || isTechnical(user)) {
            return ListScope.all();
        }
        if (AccessControl.VIEW_ALL.equals(viewMode)) {
            return ListScope.all();
        }
        List<Integer> teamIds = getTeamUserIds(user.getUserId());
        if (teamIds.isEmpty()) {
            return ListScope.of(List.of(-1), Collections.emptyList());
        }
        return ListScope.of(teamIds, getAssignedProvinceIds(user.getUserId()));
    }

    public ListScope getContractListScope(User user, String viewMode) {
        if (!isActiveUser(user) || !canViewContracts(user)) {
            return ListScope.of(List.of(-1), Collections.emptyList());
        }
        if (isAdmin(user) || isTechnical(user)) {
            return ListScope.all();
        }
        if (AccessControl.VIEW_ALL.equals(viewMode)) {
            return ListScope.all();
        }
        List<Integer> teamIds = getTeamUserIds(user.getUserId());
        if (teamIds.isEmpty()) {
            return ListScope.of(List.of(-1), Collections.emptyList());
        }
        return ListScope.of(teamIds, getAssignedProvinceIds(user.getUserId()));
    }

    public boolean isCustomerInScope(User user, Enterprise customer, List<String> customerRoles) {
        if (!isActiveUser(user) || customer == null) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        if (!isSales(user) || isSubordinate(user)) {
            return false;
        }
        List<Integer> team = getTeamUserIds(user.getUserId());
        List<Integer> provinceIds = getAssignedProvinceIds(user.getUserId());

        if (isSupplierOnly(customerRoles)) {
            return team.contains(customer.getAccountOwnerId());
        }
        if (provinceIds.isEmpty()) {
            return team.contains(customer.getAccountOwnerId());
        }
        Integer provId = provinceIdOf(customer);
        return ListScope.of(team, provinceIds).includes(customer.getAccountOwnerId(), provId);
    }

    public boolean isContractInScope(User user, int contractOwnerId, Integer contractProvinceId) {
        if (!isActiveUser(user)) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        if (!isSales(user) || isSubordinate(user)) {
            return false;
        }
        List<Integer> team = getTeamUserIds(user.getUserId());
        List<Integer> provinceIds = getAssignedProvinceIds(user.getUserId());
        if (provinceIds.isEmpty()) {
            return team.contains(contractOwnerId);
        }
        return ListScope.of(team, provinceIds).includes(contractOwnerId, contractProvinceId);
    }

    public static boolean isSupplierOnly(List<String> roles) {
        if (roles == null) {
            return false;
        }
        return roles.contains(ROLE_SUPPLIER) && !roles.contains(ROLE_BUYER);
    }

    public static Integer provinceIdOf(Enterprise customer) {
        if (customer == null) {
            return null;
        }
        Address address = customer.getAddress();
        District district = address != null ? address.getDistrict() : null;
        return district != null ? district.getProvinceId() : null;
    }
}
