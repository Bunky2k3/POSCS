package poscs.service;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import poscs.common.AccessControl;
import poscs.common.ListScope;
import poscs.dao.EmployeeDAO;
import poscs.model.Address;
import poscs.model.District;
import poscs.model.Enterprise;
import poscs.model.Province;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho {@link PermissionService} (Task 1.4).
 *
 * <p>Kiểm thử:
 * <ul>
 *   <li>Ma trận 3 vai trò × các thao tác (*) trong SPEC mục 3.3.</li>
 *   <li>Phân biệt Sales quản lý vùng (không có cấp trên) và Sales có cấp trên.</li>
 *   <li>Cấp trên và vai trò đọc trực tiếp từ CSDL qua EmployeeDAO theo thời gian thực.</li>
 *   <li>Fail-closed cho ListScope và các thao tác ghi khi không xác minh được DB.</li>
 *   <li>Mọi vi phạm quyền đều trả về MSG-022 ("Bạn không có quyền truy cập nội dung này.").</li>
 *   <li>Phạm vi dữ liệu "Của tôi" (ListScope & Row-level scope) cho cả trường hợp đã giao và chưa giao tỉnh.</li>
 *   <li>Toàn bộ các nhánh và kịch bản biên: null, role lạ, CS, deleted, userId <= 0.</li>
 * </ul>
 */
public class PermissionServiceTest {

    private EmployeeDAO employeeDAO;
    private PermissionService permissionService;

    private User adminUser;
    private User salesRegionalManager;
    private User salesStaff;
    private User technicalUser;

    @Before
    public void setUp() {
        employeeDAO = mock(EmployeeDAO.class);
        permissionService = new PermissionService(employeeDAO);

        adminUser = createUser(1, "admin1", PermissionService.ROLE_ADMIN, 1, null);
        salesRegionalManager = createUser(2, "sales_lead", PermissionService.ROLE_SALES, 2, null);
        salesStaff = createUser(3, "sales_staff", PermissionService.ROLE_SALES, 2, 2);
        technicalUser = createUser(4, "tech1", PermissionService.ROLE_TECHNICAL, 3, null);

        // Mặc định mock DB trả về chính User đó
        when(employeeDAO.findById(adminUser.getUserId())).thenReturn(adminUser);
        when(employeeDAO.findById(salesRegionalManager.getUserId())).thenReturn(salesRegionalManager);
        when(employeeDAO.findById(salesStaff.getUserId())).thenReturn(salesStaff);
        when(employeeDAO.findById(technicalUser.getUserId())).thenReturn(technicalUser);
    }

    private User createUser(int userId, String username, String roleName, int departmentId, Integer managerId) {
        User u = new User();
        u.setUserId(userId);
        u.setUsername(username);
        if (roleName != null) {
            u.setRole(new Role(1, roleName));
        }
        u.setDepartmentId(departmentId);
        u.setManagerId(managerId);
        u.setDeleted(false);
        return u;
    }

    private Enterprise createEnterprise(int enterpriseId, int ownerId, Integer provinceId) {
        Enterprise e = new Enterprise();
        e.setEnterpriseId(enterpriseId);
        e.setAccountOwnerId(ownerId);
        if (provinceId != null) {
            District d = new District();
            d.setDistrictId(10);
            d.setProvinceId(provinceId);
            Address a = new Address();
            a.setDistrict(d);
            e.setAddress(a);
        }
        return e;
    }

    private void assertMsg022(ServiceResult<Void> result) {
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(PermissionService.MSG_022, result.getMessage());
    }

    // ==================================================================
    // 1. Ma trận 3 vai trò × Các thao tác (*) trong SPEC mục 3.3
    // ==================================================================

    @Test
    public void admin_hasAccessToAllStarOperations() {
        // Khách hàng / NCC
        assertTrue(permissionService.canCreateCustomer(adminUser));
        assertTrue(permissionService.checkCanCreateCustomer(adminUser).isSuccess());

        assertTrue(permissionService.canUpdateCustomer(adminUser));
        assertTrue(permissionService.checkCanUpdateCustomer(adminUser).isSuccess());

        assertTrue(permissionService.canDeleteCustomer(adminUser));
        assertTrue(permissionService.checkCanDeleteCustomer(adminUser).isSuccess());

        assertTrue(permissionService.canRateCustomer(adminUser));
        assertTrue(permissionService.checkCanRateCustomer(adminUser).isSuccess());

        // Hợp đồng
        assertTrue(permissionService.canCreateContract(adminUser));
        assertTrue(permissionService.checkCanCreateContract(adminUser).isSuccess());

        assertTrue(permissionService.canUpdateContract(adminUser));
        assertTrue(permissionService.checkCanUpdateContract(adminUser).isSuccess());

        assertTrue(permissionService.canSignContract(adminUser));
        assertTrue(permissionService.checkCanSignContract(adminUser).isSuccess());

        // Huỷ HĐ: Admin được huỷ cả bản Nháp lẫn bản Đã ký
        assertTrue(permissionService.canVoidContract(adminUser, true));
        assertTrue(permissionService.checkCanVoidContract(adminUser, true).isSuccess());
        assertTrue(permissionService.canVoidContract(adminUser, false));
        assertTrue(permissionService.checkCanVoidContract(adminUser, false).isSuccess());

        assertTrue(permissionService.canCreateAmendment(adminUser));
        assertTrue(permissionService.checkCanCreateAmendment(adminUser).isSuccess());

        assertTrue(permissionService.canLinkContracts(adminUser));
        assertTrue(permissionService.checkCanLinkContracts(adminUser).isSuccess());

        assertTrue(permissionService.canInitiateHandover(adminUser));
        assertTrue(permissionService.checkCanInitiateHandover(adminUser).isSuccess());

        assertTrue(permissionService.canManageContractDocuments(adminUser));
        assertTrue(permissionService.checkCanManageContractDocuments(adminUser).isSuccess());

        assertTrue(permissionService.canManageContractPayments(adminUser));
        assertTrue(permissionService.checkCanManageContractPayments(adminUser).isSuccess());

        assertTrue(permissionService.canManageContractProducts(adminUser));
        assertTrue(permissionService.checkCanManageContractProducts(adminUser).isSuccess());
    }

    @Test
    public void salesRegionalManager_hasAccessToStarOperationsExceptVoidSignedContract() {
        // Sales quản lý vùng (không có cấp trên): được thực hiện các thao tác (*)
        assertTrue(permissionService.canCreateCustomer(salesRegionalManager));
        assertTrue(permissionService.checkCanCreateCustomer(salesRegionalManager).isSuccess());

        assertTrue(permissionService.canUpdateCustomer(salesRegionalManager));
        assertTrue(permissionService.checkCanUpdateCustomer(salesRegionalManager).isSuccess());

        assertTrue(permissionService.canDeleteCustomer(salesRegionalManager));
        assertTrue(permissionService.checkCanDeleteCustomer(salesRegionalManager).isSuccess());

        assertTrue(permissionService.canRateCustomer(salesRegionalManager));
        assertTrue(permissionService.checkCanRateCustomer(salesRegionalManager).isSuccess());

        assertTrue(permissionService.canCreateContract(salesRegionalManager));
        assertTrue(permissionService.checkCanCreateContract(salesRegionalManager).isSuccess());

        assertTrue(permissionService.canUpdateContract(salesRegionalManager));
        assertTrue(permissionService.checkCanUpdateContract(salesRegionalManager).isSuccess());

        assertTrue(permissionService.canSignContract(salesRegionalManager));
        assertTrue(permissionService.checkCanSignContract(salesRegionalManager).isSuccess());

        // Huỷ HĐ bản Nháp: Sales quản lý vùng ĐƯỢC PHÉP
        assertTrue(permissionService.canVoidContract(salesRegionalManager, true));
        assertTrue(permissionService.checkCanVoidContract(salesRegionalManager, true).isSuccess());

        // Huỷ HĐ bản Đã ký: Sales quản lý vùng BỊ TỪ CHỐI (chỉ Admin mới được huỷ HĐ đã ký)
        assertFalse(permissionService.canVoidContract(salesRegionalManager, false));
        assertMsg022(permissionService.checkCanVoidContract(salesRegionalManager, false));

        assertTrue(permissionService.canCreateAmendment(salesRegionalManager));
        assertTrue(permissionService.checkCanCreateAmendment(salesRegionalManager).isSuccess());

        assertTrue(permissionService.canLinkContracts(salesRegionalManager));
        assertTrue(permissionService.checkCanLinkContracts(salesRegionalManager).isSuccess());

        assertTrue(permissionService.canInitiateHandover(salesRegionalManager));
        assertTrue(permissionService.checkCanInitiateHandover(salesRegionalManager).isSuccess());

        assertTrue(permissionService.canManageContractDocuments(salesRegionalManager));
        assertTrue(permissionService.checkCanManageContractDocuments(salesRegionalManager).isSuccess());

        assertTrue(permissionService.canManageContractPayments(salesRegionalManager));
        assertTrue(permissionService.checkCanManageContractPayments(salesRegionalManager).isSuccess());

        assertTrue(permissionService.canManageContractProducts(salesRegionalManager));
        assertTrue(permissionService.checkCanManageContractProducts(salesRegionalManager).isSuccess());
    }

    @Test
    public void salesStaffWithManager_deniedAllStarOperationsWithMsg022() {
        // Sales có cấp trên: BỊ TỪ CHỐI tất cả các thao tác (*) và trả MSG-022
        assertFalse(permissionService.canCreateCustomer(salesStaff));
        assertMsg022(permissionService.checkCanCreateCustomer(salesStaff));

        assertFalse(permissionService.canUpdateCustomer(salesStaff));
        assertMsg022(permissionService.checkCanUpdateCustomer(salesStaff));

        assertFalse(permissionService.canDeleteCustomer(salesStaff));
        assertMsg022(permissionService.checkCanDeleteCustomer(salesStaff));

        assertFalse(permissionService.canRateCustomer(salesStaff));
        assertMsg022(permissionService.checkCanRateCustomer(salesStaff));

        assertFalse(permissionService.canCreateContract(salesStaff));
        assertMsg022(permissionService.checkCanCreateContract(salesStaff));

        assertFalse(permissionService.canUpdateContract(salesStaff));
        assertMsg022(permissionService.checkCanUpdateContract(salesStaff));

        assertFalse(permissionService.canSignContract(salesStaff));
        assertMsg022(permissionService.checkCanSignContract(salesStaff));

        assertFalse(permissionService.canVoidContract(salesStaff, true));
        assertMsg022(permissionService.checkCanVoidContract(salesStaff, true));

        assertFalse(permissionService.canVoidContract(salesStaff, false));
        assertMsg022(permissionService.checkCanVoidContract(salesStaff, false));

        assertFalse(permissionService.canCreateAmendment(salesStaff));
        assertMsg022(permissionService.checkCanCreateAmendment(salesStaff));

        assertFalse(permissionService.canLinkContracts(salesStaff));
        assertMsg022(permissionService.checkCanLinkContracts(salesStaff));

        assertFalse(permissionService.canInitiateHandover(salesStaff));
        assertMsg022(permissionService.checkCanInitiateHandover(salesStaff));

        assertFalse(permissionService.canManageContractDocuments(salesStaff));
        assertMsg022(permissionService.checkCanManageContractDocuments(salesStaff));

        assertFalse(permissionService.canManageContractPayments(salesStaff));
        assertMsg022(permissionService.checkCanManageContractPayments(salesStaff));

        assertFalse(permissionService.canManageContractProducts(salesStaff));
        assertMsg022(permissionService.checkCanManageContractProducts(salesStaff));
    }

    @Test
    public void technicalRole_deniedAllStarOperationsWithMsg022() {
        // Kỹ thuật: BỊ TỪ CHỐI tất cả các thao tác (*) và trả MSG-022
        assertFalse(permissionService.canCreateCustomer(technicalUser));
        assertMsg022(permissionService.checkCanCreateCustomer(technicalUser));

        assertFalse(permissionService.canUpdateCustomer(technicalUser));
        assertMsg022(permissionService.checkCanUpdateCustomer(technicalUser));

        assertFalse(permissionService.canDeleteCustomer(technicalUser));
        assertMsg022(permissionService.checkCanDeleteCustomer(technicalUser));

        assertFalse(permissionService.canRateCustomer(technicalUser));
        assertMsg022(permissionService.checkCanRateCustomer(technicalUser));

        assertFalse(permissionService.canCreateContract(technicalUser));
        assertMsg022(permissionService.checkCanCreateContract(technicalUser));

        assertFalse(permissionService.canUpdateContract(technicalUser));
        assertMsg022(permissionService.checkCanUpdateContract(technicalUser));

        assertFalse(permissionService.canSignContract(technicalUser));
        assertMsg022(permissionService.checkCanSignContract(technicalUser));

        assertFalse(permissionService.canVoidContract(technicalUser, true));
        assertMsg022(permissionService.checkCanVoidContract(technicalUser, true));

        assertFalse(permissionService.canVoidContract(technicalUser, false));
        assertMsg022(permissionService.checkCanVoidContract(technicalUser, false));

        assertFalse(permissionService.canCreateAmendment(technicalUser));
        assertMsg022(permissionService.checkCanCreateAmendment(technicalUser));

        assertFalse(permissionService.canLinkContracts(technicalUser));
        assertMsg022(permissionService.checkCanLinkContracts(technicalUser));

        assertFalse(permissionService.canInitiateHandover(technicalUser));
        assertMsg022(permissionService.checkCanInitiateHandover(technicalUser));

        assertFalse(permissionService.canManageContractDocuments(technicalUser));
        assertMsg022(permissionService.checkCanManageContractDocuments(technicalUser));

        assertFalse(permissionService.canManageContractPayments(technicalUser));
        assertMsg022(permissionService.checkCanManageContractPayments(technicalUser));

        assertFalse(permissionService.canManageContractProducts(technicalUser));
        assertMsg022(permissionService.checkCanManageContractProducts(technicalUser));
    }

    // ==================================================================
    // 2. Chữa sai sót hợp đồng đã ký (UC-54) - Chỉ Admin
    // ==================================================================

    @Test
    public void correctSignedContract_onlyAdminAllowed() {
        assertTrue(permissionService.canCorrectSignedContract(adminUser));
        assertTrue(permissionService.checkCanCorrectSignedContract(adminUser).isSuccess());

        assertFalse(permissionService.canCorrectSignedContract(salesRegionalManager));
        assertMsg022(permissionService.checkCanCorrectSignedContract(salesRegionalManager));

        assertFalse(permissionService.canCorrectSignedContract(salesStaff));
        assertMsg022(permissionService.checkCanCorrectSignedContract(salesStaff));

        assertFalse(permissionService.canCorrectSignedContract(technicalUser));
        assertMsg022(permissionService.checkCanCorrectSignedContract(technicalUser));
    }

    // ==================================================================
    // 3. Quản lý Sản phẩm (UC-18, 19, 20) - Admin và Kỹ thuật
    // ==================================================================

    @Test
    public void manageProduct_adminAndTechnicalAllowed_salesDenied() {
        assertTrue(permissionService.canCreateProduct(adminUser));
        assertTrue(permissionService.checkCanCreateProduct(adminUser).isSuccess());
        assertTrue(permissionService.canUpdateProduct(adminUser));
        assertTrue(permissionService.checkCanUpdateProduct(adminUser).isSuccess());
        assertTrue(permissionService.canDeleteProduct(adminUser));
        assertTrue(permissionService.checkCanDeleteProduct(adminUser).isSuccess());

        assertTrue(permissionService.canCreateProduct(technicalUser));
        assertTrue(permissionService.checkCanCreateProduct(technicalUser).isSuccess());
        assertTrue(permissionService.canUpdateProduct(technicalUser));
        assertTrue(permissionService.checkCanUpdateProduct(technicalUser).isSuccess());
        assertTrue(permissionService.canDeleteProduct(technicalUser));
        assertTrue(permissionService.checkCanDeleteProduct(technicalUser).isSuccess());

        assertFalse(permissionService.canCreateProduct(salesRegionalManager));
        assertMsg022(permissionService.checkCanCreateProduct(salesRegionalManager));
        assertFalse(permissionService.canUpdateProduct(salesRegionalManager));
        assertMsg022(permissionService.checkCanUpdateProduct(salesRegionalManager));
        assertFalse(permissionService.canDeleteProduct(salesRegionalManager));
        assertMsg022(permissionService.checkCanDeleteProduct(salesRegionalManager));

        assertFalse(permissionService.canCreateProduct(salesStaff));
        assertMsg022(permissionService.checkCanCreateProduct(salesStaff));
        assertFalse(permissionService.canUpdateProduct(salesStaff));
        assertMsg022(permissionService.checkCanUpdateProduct(salesStaff));
        assertFalse(permissionService.canDeleteProduct(salesStaff));
        assertMsg022(permissionService.checkCanDeleteProduct(salesStaff));
    }

    // ==================================================================
    // 4. Phiếu hỗ trợ kỹ thuật (UC-21, 22, 23)
    // ==================================================================

    @Test
    public void ticketCreationAndDeletion_adminAndSalesAllowed_technicalDenied() {
        // Tạo phiếu (UC-21): Admin & Sales (cả 2 tier)
        assertTrue(permissionService.canCreateTicket(adminUser));
        assertTrue(permissionService.checkCanCreateTicket(adminUser).isSuccess());
        assertTrue(permissionService.canCreateTicket(salesRegionalManager));
        assertTrue(permissionService.checkCanCreateTicket(salesRegionalManager).isSuccess());
        assertTrue(permissionService.canCreateTicket(salesStaff));
        assertTrue(permissionService.checkCanCreateTicket(salesStaff).isSuccess());

        assertFalse(permissionService.canCreateTicket(technicalUser));
        assertMsg022(permissionService.checkCanCreateTicket(technicalUser));

        // Xóa phiếu (UC-23): Admin & Sales
        assertTrue(permissionService.canDeleteTicket(adminUser));
        assertTrue(permissionService.checkCanDeleteTicket(adminUser).isSuccess());
        assertTrue(permissionService.canDeleteTicket(salesRegionalManager));
        assertTrue(permissionService.checkCanDeleteTicket(salesRegionalManager).isSuccess());
        assertTrue(permissionService.canDeleteTicket(salesStaff));
        assertTrue(permissionService.checkCanDeleteTicket(salesStaff).isSuccess());

        assertFalse(permissionService.canDeleteTicket(technicalUser));
        assertMsg022(permissionService.checkCanDeleteTicket(technicalUser));
    }

    @Test
    public void ticketUpdate_technicalOnlyAllowedForAssignedTicket() {
        // Admin và Sales cập nhật được mọi phiếu
        assertTrue(permissionService.canUpdateTicket(adminUser, 999));
        assertTrue(permissionService.checkCanUpdateTicket(adminUser, 999).isSuccess());
        assertTrue(permissionService.canUpdateTicket(salesRegionalManager, 999));
        assertTrue(permissionService.checkCanUpdateTicket(salesRegionalManager, 999).isSuccess());
        assertTrue(permissionService.canUpdateTicket(salesStaff, 999));
        assertTrue(permissionService.checkCanUpdateTicket(salesStaff, 999).isSuccess());

        // Kỹ thuật: chỉ cập nhật phiếu được giao cho mình
        assertTrue(permissionService.canUpdateTicket(technicalUser, technicalUser.getUserId()));
        assertTrue(permissionService.checkCanUpdateTicket(technicalUser, technicalUser.getUserId()).isSuccess());

        // Kỹ thuật khác được giao: từ chối với MSG-022
        assertFalse(permissionService.canUpdateTicket(technicalUser, 999));
        assertMsg022(permissionService.checkCanUpdateTicket(technicalUser, 999));

        // Chưa giao ai (null): từ chối với MSG-022
        assertFalse(permissionService.canUpdateTicket(technicalUser, null));
        assertMsg022(permissionService.checkCanUpdateTicket(technicalUser, null));
    }

    // ==================================================================
    // 5. Yêu cầu thay đổi (Change Request - UC-50, 51)
    // ==================================================================

    @Test
    public void submitChangeRequest_onlySubordinateAllowed() {
        // Sales có cấp trên: ĐƯỢC phép gửi yêu cầu thay đổi
        assertTrue(permissionService.canSubmitChangeRequest(salesStaff));
        assertTrue(permissionService.checkCanSubmitChangeRequest(salesStaff).isSuccess());

        // Kỹ thuật có cấp trên: ĐƯỢC phép gửi yêu cầu thay đổi
        User techSubordinate = createUser(15, "tech_sub", PermissionService.ROLE_TECHNICAL, 3, 4);
        when(employeeDAO.findById(15)).thenReturn(techSubordinate);
        assertTrue(permissionService.canSubmitChangeRequest(techSubordinate));
        assertTrue(permissionService.checkCanSubmitChangeRequest(techSubordinate).isSuccess());

        // Người không có cấp trên (Sales quản lý vùng, Admin): không cần và không được gửi
        assertFalse(permissionService.canSubmitChangeRequest(salesRegionalManager));
        assertMsg022(permissionService.checkCanSubmitChangeRequest(salesRegionalManager));
        assertFalse(permissionService.canSubmitChangeRequest(adminUser));
        assertMsg022(permissionService.checkCanSubmitChangeRequest(adminUser));
    }

    @Test
    public void reviewChangeRequest_adminOrDirectManagerAllowed() {
        int staffUserId = salesStaff.getUserId();

        // Admin duyệt được mọi yêu cầu
        assertTrue(permissionService.canReviewChangeRequest(adminUser, staffUserId));
        assertTrue(permissionService.checkCanReviewChangeRequest(adminUser, staffUserId).isSuccess());

        // Cấp trên trực tiếp (salesRegionalManager có userId = 2): duyệt được
        assertTrue(permissionService.canReviewChangeRequest(salesRegionalManager, staffUserId));
        assertTrue(permissionService.checkCanReviewChangeRequest(salesRegionalManager, staffUserId).isSuccess());

        // Sales khác không phải cấp trên trực tiếp: bị từ chối với MSG-022
        User otherSales = createUser(7, "sales_other", PermissionService.ROLE_SALES, 2, null);
        when(employeeDAO.findById(7)).thenReturn(otherSales);
        assertFalse(permissionService.canReviewChangeRequest(otherSales, staffUserId));
        assertMsg022(permissionService.checkCanReviewChangeRequest(otherSales, staffUserId));

        // Người kỹ thuật: bị từ chối
        assertFalse(permissionService.canReviewChangeRequest(technicalUser, staffUserId));
        assertMsg022(permissionService.checkCanReviewChangeRequest(technicalUser, staffUserId));

        // Người gửi không có cấp trên trong DB (managerId == null): non-Admin bị từ chối
        int leadUserId = salesRegionalManager.getUserId();
        assertFalse(permissionService.canReviewChangeRequest(otherSales, leadUserId));
        assertMsg022(permissionService.checkCanReviewChangeRequest(otherSales, leadUserId));

        // Người gửi không tồn tại trong DB (findById trả về null)
        assertFalse(permissionService.canReviewChangeRequest(salesRegionalManager, 9999));
        assertMsg022(permissionService.checkCanReviewChangeRequest(salesRegionalManager, 9999));

        // Requester ID <= 0: bị từ chối
        assertFalse(permissionService.canReviewChangeRequest(adminUser, 0));
        assertFalse(permissionService.canReviewChangeRequest(adminUser, -1));
    }

    // ==================================================================
    // 6. Xác nhận bàn giao hợp đồng (UC-49)
    // ==================================================================

    @Test
    public void confirmHandover_adminOrTargetDepartmentStaffAllowed() {
        int targetDept = 4; // Kế toán
        User accountant = createUser(5, "ketoan1", "Kế toán", targetDept, null);
        User projectStaff = createUser(6, "duan1", "Dự án", 5, null);
        when(employeeDAO.findById(5)).thenReturn(accountant);
        when(employeeDAO.findById(6)).thenReturn(projectStaff);

        // Admin: xác nhận được bàn giao cho mọi phòng ban
        assertTrue(permissionService.canConfirmHandover(adminUser, targetDept));
        assertTrue(permissionService.checkCanConfirmHandover(adminUser, targetDept).isSuccess());

        // Đúng phòng ban nhận: được phép
        assertTrue(permissionService.canConfirmHandover(accountant, targetDept));
        assertTrue(permissionService.checkCanConfirmHandover(accountant, targetDept).isSuccess());

        // Khác phòng ban nhận: bị từ chối
        assertFalse(permissionService.canConfirmHandover(projectStaff, targetDept));
        assertMsg022(permissionService.checkCanConfirmHandover(projectStaff, targetDept));

        // targetDepartmentId <= 0: non-Admin bị từ chối, Admin vẫn được phép
        assertFalse(permissionService.canConfirmHandover(accountant, 0));
        assertMsg022(permissionService.checkCanConfirmHandover(accountant, 0));
        assertFalse(permissionService.canConfirmHandover(accountant, -1));
        assertMsg022(permissionService.checkCanConfirmHandover(accountant, -1));
        assertTrue(permissionService.canConfirmHandover(adminUser, 0));

        // User null: từ chối
        assertFalse(permissionService.canConfirmHandover(null, targetDept));
        assertMsg022(permissionService.checkCanConfirmHandover(null, targetDept));
    }

    // ==================================================================
    // 7. Quản lý nhân viên (UC-24..29) & Nhật ký hệ thống (UC-43)
    // ==================================================================

    @Test
    public void manageEmployeesAndLogs_onlyAdminAllowed() {
        assertTrue(permissionService.canManageEmployees(adminUser));
        assertTrue(permissionService.checkCanManageEmployees(adminUser).isSuccess());
        assertTrue(permissionService.canViewSystemLogs(adminUser));
        assertTrue(permissionService.checkCanViewSystemLogs(adminUser).isSuccess());

        assertFalse(permissionService.canManageEmployees(salesRegionalManager));
        assertMsg022(permissionService.checkCanManageEmployees(salesRegionalManager));
        assertFalse(permissionService.canManageEmployees(salesStaff));
        assertMsg022(permissionService.checkCanManageEmployees(salesStaff));
        assertFalse(permissionService.canManageEmployees(technicalUser));
        assertMsg022(permissionService.checkCanManageEmployees(technicalUser));

        assertFalse(permissionService.canViewSystemLogs(salesRegionalManager));
        assertMsg022(permissionService.checkCanViewSystemLogs(salesRegionalManager));
        assertFalse(permissionService.canViewSystemLogs(technicalUser));
        assertMsg022(permissionService.checkCanViewSystemLogs(technicalUser));
    }

    // ==================================================================
    // 8. Đọc cấp trên & vai trò từ CSDL (Fresh read from DB / Fail-closed)
    // ==================================================================

    @Test
    public void subordinateCheck_readsDirectlyFromDatabase() {
        User userInSession = createUser(10, "sales10", PermissionService.ROLE_SALES, 2, null);
        // Trong session managerId là null, nhưng trong DB vừa được gán cấp trên
        User userInDB = createUser(10, "sales10", PermissionService.ROLE_SALES, 2, 2);
        when(employeeDAO.findById(10)).thenReturn(userInDB);

        // Đọc từ DB: phát hiện có cấp trên -> là subordinate!
        assertTrue(permissionService.isSubordinate(userInSession));
        assertTrue(permissionService.isSalesStaff(userInSession));
        assertFalse(permissionService.isSalesRegionalManager(userInSession));

        // Trường hợp ngược lại: session có managerId nhưng DB đã xóa cấp trên
        User userInSession2 = createUser(11, "sales11", PermissionService.ROLE_SALES, 2, 2);
        User userInDB2 = createUser(11, "sales11", PermissionService.ROLE_SALES, 2, null);
        when(employeeDAO.findById(11)).thenReturn(userInDB2);

        assertFalse(permissionService.isSubordinate(userInSession2));
        assertFalse(permissionService.isSalesStaff(userInSession2));
        assertTrue(permissionService.isSalesRegionalManager(userInSession2));
    }

    @Test
    public void subordinateCheck_whenDbReturnsNull_failsClosed() {
        // Sales session có managerId == null, nhưng DB trả về null (lỗi DB hoặc bị xóa)
        User unverifiedSales = createUser(12, "sales12", PermissionService.ROLE_SALES, 2, null);
        when(employeeDAO.findById(12)).thenReturn(null);

        // Khi DB trả null: fail-closed, coi như bị hạn chế quyền ghi
        assertTrue(permissionService.isSubordinate(unverifiedSales));
        assertFalse(permissionService.isSalesRegionalManager(unverifiedSales));
        assertFalse(permissionService.canCreateCustomer(unverifiedSales));
        assertMsg022(permissionService.checkCanCreateCustomer(unverifiedSales));
        assertFalse(permissionService.canCreateContract(unverifiedSales));
        assertMsg022(permissionService.checkCanCreateContract(unverifiedSales));
    }

    @Test
    public void dbRoleChange_takesEffectImmediately() {
        // User trong session là Admin, nhưng trong DB đã bị hạ xuống Sales
        User demotedAdmin = createUser(1, "admin1", PermissionService.ROLE_ADMIN, 1, null);
        User dbUserAsSales = createUser(1, "admin1", PermissionService.ROLE_SALES, 2, 2);
        when(employeeDAO.findById(1)).thenReturn(dbUserAsSales);

        assertFalse(permissionService.isAdmin(demotedAdmin));
        assertTrue(permissionService.isSales(demotedAdmin));
        assertTrue(permissionService.isSubordinate(demotedAdmin));
        assertFalse(permissionService.canManageEmployees(demotedAdmin));
        assertMsg022(permissionService.checkCanManageEmployees(demotedAdmin));
    }

    @Test
    public void dbUserDeleted_revokesAllPermissionsImmediately() {
        // User trong session trông như Admin bình thường, nhưng DB đã đánh dấu is_deleted = 1
        User deletedInDbAdmin = createUser(1, "admin1", PermissionService.ROLE_ADMIN, 1, null);
        User dbDeleted = createUser(1, "admin1", PermissionService.ROLE_ADMIN, 1, null);
        dbDeleted.setDeleted(true);
        when(employeeDAO.findById(1)).thenReturn(dbDeleted);

        assertFalse(permissionService.isActiveUser(deletedInDbAdmin));
        assertFalse(permissionService.isAdmin(deletedInDbAdmin));
        assertFalse(permissionService.canCreateCustomer(deletedInDbAdmin));
        assertMsg022(permissionService.checkCanCreateCustomer(deletedInDbAdmin));
    }

    // ==================================================================
    // 9. Phạm vi dữ liệu "Của tôi" (ListScope & Row-level Scope)
    // ==================================================================

    @Test
    public void listScope_adminAndTechnicalSeeAll() {
        assertEquals(ListScope.all(), permissionService.getCustomerListScope(adminUser, "mine"));
        assertEquals(ListScope.all(), permissionService.getCustomerListScope(technicalUser, "mine"));
        assertEquals(ListScope.all(), permissionService.getContractListScope(adminUser, "mine"));
        assertEquals(ListScope.all(), permissionService.getContractListScope(technicalUser, "mine"));
    }

    @Test
    public void listScope_salesViewAllReturnsAll() {
        assertEquals(ListScope.all(), permissionService.getCustomerListScope(salesRegionalManager, AccessControl.VIEW_ALL));
        assertEquals(ListScope.all(), permissionService.getContractListScope(salesRegionalManager, AccessControl.VIEW_ALL));
    }

    @Test
    public void listScope_salesViewMineFiltersByTeamAndProvinces() {
        int leadId = salesRegionalManager.getUserId();
        when(employeeDAO.findTeamUserIds(leadId)).thenReturn(List.of(leadId, 3, 4));
        when(employeeDAO.findProvincesOf(leadId)).thenReturn(List.of(new Province(1, "Hà Nội"), new Province(2, "Hải Phòng")));

        ListScope customerScope = permissionService.getCustomerListScope(salesRegionalManager, "mine");
        assertTrue(customerScope.isNarrowed());
        assertEquals(List.of(leadId, 3, 4), customerScope.getOwnerIds());
        assertEquals(List.of(1, 2), customerScope.getProvinceIds());

        ListScope contractScope = permissionService.getContractListScope(salesRegionalManager, "mine");
        assertTrue(contractScope.isNarrowed());
        assertEquals(List.of(leadId, 3, 4), contractScope.getOwnerIds());
        assertEquals(List.of(1, 2), contractScope.getProvinceIds());
    }

    @Test
    public void listScope_salesStaffViewMine_filtersByStaffScope() {
        int staffId = salesStaff.getUserId();
        when(employeeDAO.findTeamUserIds(staffId)).thenReturn(List.of(staffId));
        when(employeeDAO.findProvincesOf(staffId)).thenReturn(List.of(new Province(3, "Đà Nẵng")));

        ListScope staffScope = permissionService.getCustomerListScope(salesStaff, "mine");
        assertTrue(staffScope.isNarrowed());
        assertEquals(List.of(staffId), staffScope.getOwnerIds());
        assertEquals(List.of(3), staffScope.getProvinceIds());
    }

    @Test
    public void listScope_invalidOrUntrustedUser_returnsDeniedScopeNotAll() {
        // User null -> trả scope khóa [-1], không lộ toàn bộ dữ liệu
        ListScope nullUserScope = permissionService.getCustomerListScope(null, "all");
        assertTrue(nullUserScope.isNarrowed());
        assertEquals(List.of(-1), nullUserScope.getOwnerIds());
        assertFalse(nullUserScope.includes(1, 1));

        // User bị xóa mềm -> trả scope khóa
        User deletedUser = createUser(50, "del50", PermissionService.ROLE_ADMIN, 1, null);
        deletedUser.setDeleted(true);
        ListScope delScope = permissionService.getCustomerListScope(deletedUser, "all");
        assertTrue(delScope.isNarrowed());
        assertEquals(List.of(-1), delScope.getOwnerIds());

        // Role lạ / CS -> trả scope khóa
        User csUser = createUser(51, "cs51", "CS", 2, null);
        ListScope csScope = permissionService.getContractListScope(csUser, "all");
        assertTrue(csScope.isNarrowed());
        assertEquals(List.of(-1), csScope.getOwnerIds());

        // User với userId <= 0
        User invalidIdUser = createUser(0, "inv", PermissionService.ROLE_SALES, 2, null);
        ListScope invScope = permissionService.getCustomerListScope(invalidIdUser, "mine");
        assertTrue(invScope.isNarrowed());
        assertEquals(List.of(-1), invScope.getOwnerIds());
    }

    @Test
    public void rowLevelCustomerScope_supplierOnlyFollowsTeam() {
        int leadId = salesRegionalManager.getUserId();
        when(employeeDAO.findTeamUserIds(leadId)).thenReturn(List.of(leadId, 3));
        when(employeeDAO.findProvincesOf(leadId)).thenReturn(List.of(new Province(1, "Hà Nội")));

        Enterprise mySupplier = createEnterprise(101, leadId, 99); // Khác tỉnh nhưng thuộc team
        Enterprise otherSupplier = createEnterprise(102, 888, 1);  // Thuộc tỉnh nhưng không thuộc team

        // Supplier-only: chỉ theo team, không theo địa bàn tỉnh
        List<String> supplierRoles = List.of(PermissionService.ROLE_SUPPLIER);
        assertTrue(permissionService.isCustomerInScope(salesRegionalManager, mySupplier, supplierRoles));
        assertFalse(permissionService.isCustomerInScope(salesRegionalManager, otherSupplier, supplierRoles));
    }

    @Test
    public void rowLevelCustomerScope_buyerFollowsTeamOrProvince() {
        int leadId = salesRegionalManager.getUserId();
        when(employeeDAO.findTeamUserIds(leadId)).thenReturn(List.of(leadId, 3));
        when(employeeDAO.findProvincesOf(leadId)).thenReturn(List.of(new Province(1, "Hà Nội")));

        Enterprise myBuyerOutsideProvince = createEnterprise(201, leadId, 99); // Trong team, ngoài tỉnh
        Enterprise otherBuyerInProvince = createEnterprise(202, 888, 1);      // Ngoài team, trong tỉnh
        Enterprise outsideBuyer = createEnterprise(203, 888, 99);              // Ngoài team, ngoài tỉnh

        List<String> buyerRoles = List.of(PermissionService.ROLE_BUYER);
        assertTrue(permissionService.isCustomerInScope(salesRegionalManager, myBuyerOutsideProvince, buyerRoles));
        assertTrue(permissionService.isCustomerInScope(salesRegionalManager, otherBuyerInProvince, buyerRoles));
        assertFalse(permissionService.isCustomerInScope(salesRegionalManager, outsideBuyer, buyerRoles));
    }

    @Test
    public void rowLevelCustomerScope_whenNoProvinceAssigned_onlyFollowsTeam() {
        // Quyết định 2026-10-15: Khi chưa giao tỉnh, cả customer lẫn contract đều chỉ được sửa theo team
        int leadId = salesRegionalManager.getUserId();
        when(employeeDAO.findTeamUserIds(leadId)).thenReturn(List.of(leadId, 3));
        when(employeeDAO.findProvincesOf(leadId)).thenReturn(Collections.emptyList());

        Enterprise myCustomer = createEnterprise(204, leadId, 99);
        Enterprise otherCustomer = createEnterprise(205, 888, 99);

        List<String> buyerRoles = List.of(PermissionService.ROLE_BUYER);
        assertTrue(permissionService.isCustomerInScope(salesRegionalManager, myCustomer, buyerRoles));
        assertFalse(permissionService.isCustomerInScope(salesRegionalManager, otherCustomer, buyerRoles));
    }

    @Test
    public void rowLevelContractScope_followsTeamOrProvince() {
        int leadId = salesRegionalManager.getUserId();
        when(employeeDAO.findTeamUserIds(leadId)).thenReturn(List.of(leadId, 3));
        when(employeeDAO.findProvincesOf(leadId)).thenReturn(List.of(new Province(1, "Hà Nội")));

        // Trong team -> được phép
        assertTrue(permissionService.isContractInScope(salesRegionalManager, leadId, 99));
        // Trong tỉnh được giao -> được phép
        assertTrue(permissionService.isContractInScope(salesRegionalManager, 888, 1));
        // Ngoài team và ngoài tỉnh -> từ chối
        assertFalse(permissionService.isContractInScope(salesRegionalManager, 888, 99));

        // Khi chưa giao tỉnh (provinces rỗng)
        when(employeeDAO.findProvincesOf(leadId)).thenReturn(Collections.emptyList());
        assertTrue(permissionService.isContractInScope(salesRegionalManager, leadId, 99));
        assertFalse(permissionService.isContractInScope(salesRegionalManager, 888, 99));
    }

    @Test
    public void rowLevelScope_adminAlwaysAllowed_subordinateAndTechAlwaysDenied() {
        Enterprise cust = createEnterprise(301, 999, 999);
        List<String> roles = List.of(PermissionService.ROLE_BUYER);

        // Admin: luôn trong phạm vi
        assertTrue(permissionService.isCustomerInScope(adminUser, cust, roles));
        assertTrue(permissionService.isContractInScope(adminUser, 999, 999));

        // Sales có cấp trên: luôn từ chối quyền ghi
        assertFalse(permissionService.isCustomerInScope(salesStaff, cust, roles));
        assertFalse(permissionService.isContractInScope(salesStaff, 999, 999));

        // Kỹ thuật: luôn từ chối
        assertFalse(permissionService.isCustomerInScope(technicalUser, cust, roles));
        assertFalse(permissionService.isContractInScope(technicalUser, 999, 999));

        // null customer
        assertFalse(permissionService.isCustomerInScope(salesRegionalManager, null, roles));
    }

    @Test
    public void canUpdateAndDeleteCustomerOverload_checksRowLevelScope() {
        int leadId = salesRegionalManager.getUserId();
        when(employeeDAO.findTeamUserIds(leadId)).thenReturn(List.of(leadId));
        when(employeeDAO.findProvincesOf(leadId)).thenReturn(List.of(new Province(1, "Hà Nội")));

        Enterprise inScope = createEnterprise(401, leadId, 1);
        Enterprise outOfScope = createEnterprise(402, 999, 999);
        List<String> roles = List.of(PermissionService.ROLE_BUYER);

        // Admin: sửa và xóa được cả khách ngoài phạm vi
        assertTrue(permissionService.canUpdateCustomer(adminUser, outOfScope, roles));
        assertTrue(permissionService.checkCanUpdateCustomer(adminUser, outOfScope, roles).isSuccess());
        assertTrue(permissionService.canDeleteCustomer(adminUser, outOfScope, roles));
        assertTrue(permissionService.checkCanDeleteCustomer(adminUser, outOfScope, roles).isSuccess());

        // Sales quản lý vùng: chỉ sửa/xóa được khách trong phạm vi
        assertTrue(permissionService.canUpdateCustomer(salesRegionalManager, inScope, roles));
        assertTrue(permissionService.checkCanUpdateCustomer(salesRegionalManager, inScope, roles).isSuccess());
        assertTrue(permissionService.canDeleteCustomer(salesRegionalManager, inScope, roles));
        assertTrue(permissionService.checkCanDeleteCustomer(salesRegionalManager, inScope, roles).isSuccess());

        assertFalse(permissionService.canUpdateCustomer(salesRegionalManager, outOfScope, roles));
        assertMsg022(permissionService.checkCanUpdateCustomer(salesRegionalManager, outOfScope, roles));
        assertFalse(permissionService.canDeleteCustomer(salesRegionalManager, outOfScope, roles));
        assertMsg022(permissionService.checkCanDeleteCustomer(salesRegionalManager, outOfScope, roles));

        // Sales có cấp trên và Kỹ thuật: từ chối kể cả khách trong phạm vi
        assertFalse(permissionService.canUpdateCustomer(salesStaff, inScope, roles));
        assertMsg022(permissionService.checkCanUpdateCustomer(salesStaff, inScope, roles));
        assertFalse(permissionService.canDeleteCustomer(salesStaff, inScope, roles));
        assertMsg022(permissionService.checkCanDeleteCustomer(salesStaff, inScope, roles));

        assertFalse(permissionService.canUpdateCustomer(technicalUser, inScope, roles));
        assertMsg022(permissionService.checkCanUpdateCustomer(technicalUser, inScope, roles));
    }

    // ==================================================================
    // 10. Quyền xem (Read-only) & Xuất dữ liệu
    // ==================================================================

    @Test
    public void viewAndExport_allowedForStandardRoles_deniedForInvalidUser() {
        for (User u : List.of(adminUser, salesRegionalManager, salesStaff, technicalUser)) {
            assertTrue(permissionService.canViewCustomers(u));
            assertTrue(permissionService.checkCanViewCustomers(u).isSuccess());
            assertTrue(permissionService.canExportCustomers(u));
            assertTrue(permissionService.checkCanExportCustomers(u).isSuccess());

            assertTrue(permissionService.canViewProducts(u));
            assertTrue(permissionService.checkCanViewProducts(u).isSuccess());

            assertTrue(permissionService.canViewContracts(u));
            assertTrue(permissionService.checkCanViewContracts(u).isSuccess());
            assertTrue(permissionService.canExportContracts(u));
            assertTrue(permissionService.checkCanExportContracts(u).isSuccess());

            assertTrue(permissionService.canViewTickets(u));
            assertTrue(permissionService.checkCanViewTickets(u).isSuccess());
            assertTrue(permissionService.canExportTickets(u));
            assertTrue(permissionService.checkCanExportTickets(u).isSuccess());

            assertTrue(permissionService.canViewNotifications(u));
            assertTrue(permissionService.checkCanViewNotifications(u).isSuccess());
        }

        // Bị từ chối với user null
        assertFalse(permissionService.canViewCustomers(null));
        assertMsg022(permissionService.checkCanViewCustomers(null));
        assertFalse(permissionService.canViewProducts(null));
        assertMsg022(permissionService.checkCanViewProducts(null));
        assertFalse(permissionService.canViewContracts(null));
        assertMsg022(permissionService.checkCanViewContracts(null));
        assertFalse(permissionService.canViewTickets(null));
        assertMsg022(permissionService.checkCanViewTickets(null));
        assertFalse(permissionService.canViewNotifications(null));
        assertMsg022(permissionService.checkCanViewNotifications(null));
        assertFalse(permissionService.canExportCustomers(null));
        assertMsg022(permissionService.checkCanExportCustomers(null));
        assertFalse(permissionService.canExportContracts(null));
        assertMsg022(permissionService.checkCanExportContracts(null));
        assertFalse(permissionService.canExportTickets(null));
        assertMsg022(permissionService.checkCanExportTickets(null));

        // Bị từ chối với role lạ / CS
        User csUser = createUser(60, "cs60", "CS", 2, null);
        assertFalse(permissionService.canViewCustomers(csUser));
        assertMsg022(permissionService.checkCanViewCustomers(csUser));
        assertFalse(permissionService.canViewProducts(csUser));
        assertMsg022(permissionService.checkCanViewProducts(csUser));
        assertFalse(permissionService.canViewContracts(csUser));
        assertMsg022(permissionService.checkCanViewContracts(csUser));
    }

    // ==================================================================
    // 11. Các phương thức phụ trợ DAO & Helper methods biên
    // ==================================================================

    @Test
    public void helperMethods_boundaryCases() {
        // getTeamUserIds
        assertEquals(Collections.emptyList(), permissionService.getTeamUserIds(0));
        assertEquals(Collections.emptyList(), permissionService.getTeamUserIds(-1));
        when(employeeDAO.findTeamUserIds(99)).thenReturn(null);
        assertEquals(List.of(99), permissionService.getTeamUserIds(99));

        // getAssignedProvinceIds & getAssignedProvinces
        assertEquals(Collections.emptyList(), permissionService.getAssignedProvinceIds(0));
        assertEquals(Collections.emptyList(), permissionService.getAssignedProvinceIds(-1));
        when(employeeDAO.findProvincesOf(99)).thenReturn(null);
        assertEquals(Collections.emptyList(), permissionService.getAssignedProvinceIds(99));
        assertEquals(Collections.emptyList(), permissionService.getAssignedProvinces(99));

        // isSupplierOnly
        assertFalse(PermissionService.isSupplierOnly(null));
        assertFalse(PermissionService.isSupplierOnly(Collections.emptyList()));
        assertTrue(PermissionService.isSupplierOnly(List.of(PermissionService.ROLE_SUPPLIER)));
        assertFalse(PermissionService.isSupplierOnly(List.of(PermissionService.ROLE_BUYER)));
        assertFalse(PermissionService.isSupplierOnly(List.of(PermissionService.ROLE_BUYER, PermissionService.ROLE_SUPPLIER)));

        // provinceIdOf
        assertNull(PermissionService.provinceIdOf(null));
        assertNull(PermissionService.provinceIdOf(new Enterprise()));
        Enterprise entWithEmptyAddr = new Enterprise();
        entWithEmptyAddr.setAddress(new Address());
        assertNull(PermissionService.provinceIdOf(entWithEmptyAddr));

        // findManagerId với id <= 0
        assertNull(permissionService.findManagerId(0));
        assertNull(permissionService.findManagerId(-5));

        // default constructor instantiate test
        PermissionService defaultService = new PermissionService();
        assertNotNull(defaultService);
    }
}
