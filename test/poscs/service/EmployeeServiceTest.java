package poscs.service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mindrot.jbcrypt.BCrypt;
import poscs.dao.AddressDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Department;
import poscs.model.Province;
import poscs.model.ProvinceAssignment;
import poscs.model.Role;
import poscs.model.User;
import poscs.validator.EmployeeValidator;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EmployeeServiceTest {

    @Mock
    private EmployeeDAO employeeDAO;

    @Mock
    private AddressDAO addressDAO;

    @Mock
    private PermissionService permissionService;

    @Mock
    private EmployeeService.AccountEmailSender emailSender;

    private EmployeeValidator employeeValidator;
    private EmployeeService service;

    private User adminActor;
    private User salesActor;

    @Before
    public void setUp() {
        employeeValidator = new EmployeeValidator();
        service = new EmployeeService(employeeDAO, addressDAO, permissionService,
                employeeValidator, emailSender);

        adminActor = new User();
        adminActor.setUserId(1);
        adminActor.setRoleId(1);
        adminActor.setRole(new Role(1, "Admin"));

        salesActor = new User();
        salesActor.setUserId(2);
        salesActor.setRoleId(2);
        salesActor.setRole(new Role(2, "Sales"));

        when(permissionService.canManageEmployees(adminActor)).thenReturn(true);
        when(permissionService.canManageEmployees(salesActor)).thenReturn(false);

        Role rAdmin = new Role(1, "Admin");
        Role rSales = new Role(2, "Sales");
        Role rTech = new Role(3, "Kỹ thuật");
        when(employeeDAO.findAllRoles()).thenReturn(List.of(rAdmin, rSales, rTech));
    }

    private User createSampleUser(int id, int roleId) {
        User u = new User();
        u.setUserId(id);
        u.setUsername("thanh.le");
        u.setLastName("Lê");
        u.setMiddleName("Văn");
        u.setFirstName("Thành");
        u.setDepartmentId(2);
        u.setRoleId(roleId);
        u.setHireDate(Date.valueOf(LocalDate.of(2023, 5, 1)));
        u.setPersonalEmail("thanh.le@example.com");
        u.setGender("Nam");
        u.setDateOfBirth(Date.valueOf(LocalDate.of(1992, 8, 12)));
        u.setPhone("0987654321");
        u.setCitizenId("001092001122");
        return u;
    }

    // -------------------------------------------------------------------------
    // 1. Phân quyền chung (Admin only)
    // -------------------------------------------------------------------------
    @Test
    public void testNonAdmin_cannotAccessAnyEmployeeAction() {
        assertEquals(0, service.findEmployees(1, 10, null, null, null, salesActor).size());
        assertEquals(0, service.countEmployees(null, null, null, salesActor));
        assertNull(service.findEmployeeDetail(5, salesActor));

        User u = createSampleUser(0, 2);
        ServiceResult<Integer> resCreate = service.createEmployee(u, List.of(), salesActor);
        assertFalse(resCreate.isSuccess());
        assertEquals(EmployeeService.ERR_FORBIDDEN, resCreate.getMessage());
        verify(employeeDAO, never()).insert(any());

        ServiceResult<Integer> resUpdate = service.updateEmployee(u, List.of(), salesActor);
        assertFalse(resUpdate.isSuccess());
        assertEquals(EmployeeService.ERR_FORBIDDEN, resUpdate.getMessage());
        verify(employeeDAO, never()).update(any());

        ServiceResult<Integer> resSend = service.sendAccountInformation(5, salesActor);
        assertFalse(resSend.isSuccess());
        assertEquals(EmployeeService.ERR_FORBIDDEN, resSend.getMessage());
        verify(employeeDAO, never()).updatePasswordHash(anyInt(), anyString());

        ServiceResult<Boolean> resToggle = service.toggleEmployeeStatus(5, salesActor);
        assertFalse(resToggle.isSuccess());
        assertEquals(EmployeeService.ERR_FORBIDDEN, resToggle.getMessage());
        verify(employeeDAO, never()).setActive(anyInt(), anyBoolean());
    }

    // -------------------------------------------------------------------------
    // 2. Query và Helper: findEmployees, detail, roles, provinces
    // -------------------------------------------------------------------------
    @Test
    public void testFindEmployeesAndCount_adminAllowed() {
        User u = createSampleUser(10, 2);
        when(employeeDAO.findAll(1, 10, "thanh", "active", 2)).thenReturn(List.of(u));
        when(employeeDAO.countAll("thanh", "active", 2)).thenReturn(1);

        List<User> list = service.findEmployees(1, 10, "thanh", "active", 2, adminActor);
        assertEquals(1, list.size());
        assertEquals(10, list.get(0).getUserId());

        int count = service.countEmployees("thanh", "active", 2, adminActor);
        assertEquals(1, count);
    }

    @Test
    public void testFindEmployeeDetail_adminAllowed() {
        User u = createSampleUser(10, 2);
        when(employeeDAO.findById(10)).thenReturn(u);

        User detail = service.findEmployeeDetail(10, adminActor);
        assertNotNull(detail);
        assertEquals("Thành", detail.getFirstName());
    }

    @Test
    public void testAssignableRoles_filtersAdminUnlessEditingAdmin() {
        List<Role> rolesForNew = service.assignableRoles(null);
        assertEquals(2, rolesForNew.size());
        assertTrue(rolesForNew.stream().noneMatch(r -> "Admin".equals(r.getRoleName())));

        List<Role> rolesForSales = service.assignableRoles(2);
        assertEquals(2, rolesForSales.size());

        List<Role> rolesForAdmin = service.assignableRoles(1);
        assertEquals(3, rolesForAdmin.size());
        assertTrue(rolesForAdmin.stream().anyMatch(r -> "Admin".equals(r.getRoleName())));
    }

    @Test
    public void testBranchProvinceAssignments() {
        when(addressDAO.findBranchProvinces()).thenReturn(List.of(new Province(101, "Hà Nội")));
        when(employeeDAO.findAllProvincesWithHolder()).thenReturn(List.of(
                new ProvinceAssignment(101, "Hà Nội", 5, "User 5"),
                new ProvinceAssignment(102, "Hải Phòng", null, null),
                new ProvinceAssignment(103, "Đà Nẵng", 10, "User 10")
        ));

        List<ProvinceAssignment> list = service.branchProvinceAssignments(10);
        assertEquals(2, list.size());
        assertTrue(list.stream().anyMatch(p -> p.getProvinceId() == 101));
        assertTrue(list.stream().anyMatch(p -> p.getProvinceId() == 103));
    }

    // -------------------------------------------------------------------------
    // 3. UC-26: createEmployee
    // -------------------------------------------------------------------------
    @Test
    public void testCreateEmployee_success_andBCryptVerified() {
        User u = createSampleUser(0, 2); // Sales
        when(employeeDAO.existsByPhone(eq("0987654321"), isNull())).thenReturn(false);
        when(employeeDAO.existsByCitizenId(eq("001092001122"), isNull())).thenReturn(false);
        when(employeeDAO.findAllProvincesWithHolder()).thenReturn(List.of(
                new ProvinceAssignment(101, "Hà Nội", null, null)
        ));
        when(employeeDAO.generateUniqueUsername(any(), any(), any())).thenReturn("thanhle");
        when(employeeDAO.insert(any(User.class))).thenReturn(20);
        when(employeeDAO.replaceProvincesOf(eq(20), anyList())).thenReturn(true);

        ServiceResult<Integer> result = service.createEmployee(u, List.of(101), adminActor);
        assertTrue(result.isSuccess());
        assertEquals(Integer.valueOf(20), result.getData());
        assertNull(result.getMessage());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(employeeDAO).insert(userCaptor.capture());
        User inserted = userCaptor.getValue();
        assertEquals("thanhle", inserted.getUsername());
        assertNotNull(inserted.getPasswordHash());
        assertTrue(inserted.getPasswordHash().startsWith("$2"));
    }

    @Test
    public void testCreateEmployee_phoneFormatNormalized_checksDuplicateWithCleanedNumber() {
        User u = createSampleUser(0, 2);
        u.setPhone("0987 654 321"); // có khoảng trắng
        when(employeeDAO.existsByPhone("0987654321", null)).thenReturn(false);
        when(employeeDAO.existsByCitizenId(anyString(), isNull())).thenReturn(false);
        when(employeeDAO.findAllProvincesWithHolder()).thenReturn(Collections.emptyList());
        when(employeeDAO.generateUniqueUsername(any(), any(), any())).thenReturn("thanhle");
        when(employeeDAO.insert(any(User.class))).thenReturn(21);
        when(employeeDAO.replaceProvincesOf(eq(21), anyList())).thenReturn(true);

        ServiceResult<Integer> result = service.createEmployee(u, List.of(), adminActor);
        assertTrue(result.isSuccess());
        verify(employeeDAO).existsByPhone("0987654321", null);
        assertEquals("0987654321", u.getPhone());
    }

    @Test
    public void testCreateEmployee_blankPhoneAndCitizen_normalizedToNull_skipsExistsCheck() {
        User u = createSampleUser(0, 2);
        u.setPhone("   "); // blank
        u.setCitizenId(""); // rỗng
        when(employeeDAO.findAllProvincesWithHolder()).thenReturn(Collections.emptyList());
        when(employeeDAO.generateUniqueUsername(any(), any(), any())).thenReturn("thanhle");
        when(employeeDAO.insert(any(User.class))).thenReturn(22);
        when(employeeDAO.replaceProvincesOf(eq(22), anyList())).thenReturn(true);

        ServiceResult<Integer> result = service.createEmployee(u, List.of(), adminActor);
        assertTrue(result.isSuccess());
        assertNull(u.getPhone());
        assertNull(u.getCitizenId());
        verify(employeeDAO, never()).existsByPhone(anyString(), any());
        verify(employeeDAO, never()).existsByCitizenId(anyString(), any());
    }

    @Test
    public void testCreateEmployee_nullUser_fails() {
        ServiceResult<Integer> result = service.createEmployee(null, List.of(), adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_INVALID, result.getMessage());
        verify(employeeDAO, never()).insert(any());
    }

    @Test
    public void testCreateEmployee_invalidInput_fails() {
        User u = createSampleUser(0, 2);
        u.setLastName(""); // thiếu Họ
        ServiceResult<Integer> result = service.createEmployee(u, List.of(), adminActor);
        assertFalse(result.isSuccess());
        assertTrue(result.hasFieldErrors());
        assertEquals(EmployeeValidator.ERR_REQUIRED, result.getFieldErrors().get("lastName"));
        verify(employeeDAO, never()).insert(any());
    }

    @Test
    public void testCreateEmployee_roleAdminForbidden() {
        User u = createSampleUser(0, 1); // Cố tình chọn vai Admin
        ServiceResult<Integer> result = service.createEmployee(u, List.of(), adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeValidator.ERR_ADMIN_ROLE_FORBIDDEN, result.getFieldErrors().get("roleId"));
        verify(employeeDAO, never()).insert(any());
    }

    @Test
    public void testCreateEmployee_duplicatePhone_fails() {
        User u = createSampleUser(0, 2);
        when(employeeDAO.existsByPhone(eq("0987654321"), isNull())).thenReturn(true);

        ServiceResult<Integer> result = service.createEmployee(u, List.of(), adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_DUPLICATE_PHONE, result.getMessage());
        verify(employeeDAO, never()).insert(any());
    }

    @Test
    public void testCreateEmployee_duplicateCitizen_fails() {
        User u = createSampleUser(0, 2);
        when(employeeDAO.existsByPhone(eq("0987654321"), isNull())).thenReturn(false);
        when(employeeDAO.existsByCitizenId(eq("001092001122"), isNull())).thenReturn(true);

        ServiceResult<Integer> result = service.createEmployee(u, List.of(), adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_DUPLICATE_CITIZEN, result.getMessage());
        verify(employeeDAO, never()).insert(any());
    }

    @Test
    public void testCreateEmployee_provinceTaken_fails() {
        User u = createSampleUser(0, 2);
        when(employeeDAO.existsByPhone(eq("0987654321"), isNull())).thenReturn(false);
        when(employeeDAO.existsByCitizenId(eq("001092001122"), isNull())).thenReturn(false);
        when(employeeDAO.findAllProvincesWithHolder()).thenReturn(List.of(
                new ProvinceAssignment(101, "Hà Nội", 99, "Người Khác")
        ));

        ServiceResult<Integer> result = service.createEmployee(u, List.of(101), adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_PROVINCE_TAKEN, result.getMessage());
        verify(employeeDAO, never()).insert(any());
    }

    @Test
    public void testCreateEmployee_insertFails() {
        User u = createSampleUser(0, 2);
        when(employeeDAO.existsByPhone(eq("0987654321"), isNull())).thenReturn(false);
        when(employeeDAO.existsByCitizenId(eq("001092001122"), isNull())).thenReturn(false);
        when(employeeDAO.findAllProvincesWithHolder()).thenReturn(Collections.emptyList());
        when(employeeDAO.generateUniqueUsername(any(), any(), any())).thenReturn("thanhle");
        when(employeeDAO.insert(any(User.class))).thenReturn(-1);

        ServiceResult<Integer> result = service.createEmployee(u, List.of(), adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_CREATE_FAILED, result.getMessage());
    }

    @Test
    public void testCreateEmployee_provinceNotSaved() {
        User u = createSampleUser(0, 2);
        when(employeeDAO.existsByPhone(eq("0987654321"), isNull())).thenReturn(false);
        when(employeeDAO.existsByCitizenId(eq("001092001122"), isNull())).thenReturn(false);
        when(employeeDAO.findAllProvincesWithHolder()).thenReturn(Collections.emptyList());
        when(employeeDAO.generateUniqueUsername(any(), any(), any())).thenReturn("thanhle");
        when(employeeDAO.insert(any(User.class))).thenReturn(25);
        when(employeeDAO.replaceProvincesOf(eq(25), anyList())).thenReturn(false);

        ServiceResult<Integer> result = service.createEmployee(u, List.of(101), adminActor);
        assertTrue(result.isSuccess());
        assertEquals(Integer.valueOf(25), result.getData());
        assertEquals(EmployeeService.ERR_PROVINCE_NOT_SAVED, result.getMessage());
    }

    // -------------------------------------------------------------------------
    // 4. UC-27: updateEmployee
    // -------------------------------------------------------------------------
    @Test
    public void testUpdateEmployee_success() {
        User existing = createSampleUser(10, 2);
        existing.setManagerId(5);
        when(employeeDAO.findById(10)).thenReturn(existing);

        User update = createSampleUser(10, 3); // đổi sang Kỹ thuật
        when(employeeDAO.existsByPhone(eq("0987654321"), eq(10))).thenReturn(false);
        when(employeeDAO.existsByCitizenId(eq("001092001122"), eq(10))).thenReturn(false);
        when(employeeDAO.findAllProvincesWithHolder()).thenReturn(Collections.emptyList());
        when(employeeDAO.update(any(User.class))).thenReturn(true);
        when(employeeDAO.replaceProvincesOf(eq(10), anyList())).thenReturn(true);

        ServiceResult<Integer> result = service.updateEmployee(update, List.of(), adminActor);
        assertTrue(result.isSuccess());
        assertEquals(Integer.valueOf(10), result.getData());
        assertEquals(Integer.valueOf(5), update.getManagerId());
        verify(employeeDAO).update(update);
    }

    @Test
    public void testUpdateEmployee_nullInput_fails() {
        ServiceResult<Integer> result = service.updateEmployee(null, List.of(), adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_INVALID, result.getMessage());
        verify(employeeDAO, never()).update(any());
    }

    @Test
    public void testUpdateEmployee_notFound() {
        when(employeeDAO.findById(999)).thenReturn(null);

        User update = createSampleUser(999, 2);
        ServiceResult<Integer> result = service.updateEmployee(update, List.of(), adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_NOT_FOUND, result.getMessage());
        verify(employeeDAO, never()).update(any());
    }

    @Test
    public void testUpdateEmployee_promoteToAdmin_fails() {
        User existing = createSampleUser(10, 2);
        when(employeeDAO.findById(10)).thenReturn(existing);

        User update = createSampleUser(10, 1); // cố ý thăng lên Admin
        ServiceResult<Integer> result = service.updateEmployee(update, List.of(), adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeValidator.ERR_ADMIN_ROLE_FORBIDDEN, result.getFieldErrors().get("roleId"));
        verify(employeeDAO, never()).update(any());
    }

    @Test
    public void testUpdateEmployee_existingAdminKeepsAdmin_succeeds() {
        User existing = createSampleUser(10, 1); // đã là Admin
        when(employeeDAO.findById(10)).thenReturn(existing);

        User update = createSampleUser(10, 1);
        when(employeeDAO.existsByPhone(eq("0987654321"), eq(10))).thenReturn(false);
        when(employeeDAO.existsByCitizenId(eq("001092001122"), eq(10))).thenReturn(false);
        when(employeeDAO.findAllProvincesWithHolder()).thenReturn(Collections.emptyList());
        when(employeeDAO.update(any(User.class))).thenReturn(true);
        when(employeeDAO.replaceProvincesOf(eq(10), anyList())).thenReturn(true);

        ServiceResult<Integer> result = service.updateEmployee(update, List.of(), adminActor);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testUpdateEmployee_duplicatePhone_fails() {
        User existing = createSampleUser(10, 2);
        when(employeeDAO.findById(10)).thenReturn(existing);

        User update = createSampleUser(10, 2);
        when(employeeDAO.existsByPhone(eq("0987654321"), eq(10))).thenReturn(true);

        ServiceResult<Integer> result = service.updateEmployee(update, List.of(), adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_DUPLICATE_PHONE, result.getMessage());
        verify(employeeDAO, never()).update(any());
    }

    @Test
    public void testUpdateEmployee_duplicateCitizen_fails() {
        User existing = createSampleUser(10, 2);
        when(employeeDAO.findById(10)).thenReturn(existing);

        User update = createSampleUser(10, 2);
        when(employeeDAO.existsByPhone(eq("0987654321"), eq(10))).thenReturn(false);
        when(employeeDAO.existsByCitizenId(eq("001092001122"), eq(10))).thenReturn(true);

        ServiceResult<Integer> result = service.updateEmployee(update, List.of(), adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_DUPLICATE_CITIZEN, result.getMessage());
        verify(employeeDAO, never()).update(any());
    }

    @Test
    public void testUpdateEmployee_provinceTaken_fails() {
        User existing = createSampleUser(10, 2);
        when(employeeDAO.findById(10)).thenReturn(existing);

        User update = createSampleUser(10, 2);
        when(employeeDAO.existsByPhone(eq("0987654321"), eq(10))).thenReturn(false);
        when(employeeDAO.existsByCitizenId(eq("001092001122"), eq(10))).thenReturn(false);
        when(employeeDAO.findAllProvincesWithHolder()).thenReturn(List.of(
                new ProvinceAssignment(101, "Hà Nội", 99, "Người Khác")
        ));

        ServiceResult<Integer> result = service.updateEmployee(update, List.of(101), adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_PROVINCE_TAKEN, result.getMessage());
        verify(employeeDAO, never()).update(any());
    }

    @Test
    public void testUpdateEmployee_provinceNotSaved() {
        User existing = createSampleUser(10, 2);
        when(employeeDAO.findById(10)).thenReturn(existing);

        User update = createSampleUser(10, 2);
        when(employeeDAO.existsByPhone(eq("0987654321"), eq(10))).thenReturn(false);
        when(employeeDAO.existsByCitizenId(eq("001092001122"), eq(10))).thenReturn(false);
        when(employeeDAO.findAllProvincesWithHolder()).thenReturn(Collections.emptyList());
        when(employeeDAO.update(any(User.class))).thenReturn(true);
        when(employeeDAO.replaceProvincesOf(eq(10), anyList())).thenReturn(false);

        ServiceResult<Integer> result = service.updateEmployee(update, List.of(101), adminActor);
        assertTrue(result.isSuccess());
        assertEquals(EmployeeService.ERR_PROVINCE_NOT_SAVED, result.getMessage());
    }

    // -------------------------------------------------------------------------
    // 5. UC-28: sendAccountInformation
    // -------------------------------------------------------------------------
    @Test
    public void testSendAccountInformation_success_andBCryptVerified() {
        User employee = createSampleUser(10, 2);
        when(employeeDAO.findById(10)).thenReturn(employee);
        when(emailSender.send(eq(employee.getPersonalEmail()), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(employeeDAO.updatePasswordHash(eq(10), anyString())).thenReturn(true);

        ServiceResult<Integer> result = service.sendAccountInformation(10, adminActor);
        assertTrue(result.isSuccess());

        ArgumentCaptor<String> plainCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq(employee.getPersonalEmail()), anyString(), anyString(), plainCaptor.capture());
        verify(employeeDAO).updatePasswordHash(eq(10), hashCaptor.capture());

        String plainPass = plainCaptor.getValue();
        String hashedPass = hashCaptor.getValue();
        assertNotNull(plainPass);
        assertNotNull(hashedPass);
        assertTrue(hashedPass.startsWith("$2"));
        assertTrue(BCrypt.checkpw(plainPass, hashedPass));
    }

    @Test
    public void testSendAccountInformation_noPersonalEmail_failsAndDoesNotChangePassword() {
        User employee = createSampleUser(10, 2);
        employee.setPersonalEmail(null);
        when(employeeDAO.findById(10)).thenReturn(employee);

        ServiceResult<Integer> result = service.sendAccountInformation(10, adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_NO_PERSONAL_EMAIL, result.getMessage());
        verifyNoInteractions(emailSender);
        verify(employeeDAO, never()).updatePasswordHash(anyInt(), anyString());
    }

    @Test
    public void testSendAccountInformation_blankPersonalEmail_failsAndDoesNotChangePassword() {
        User employee = createSampleUser(10, 2);
        employee.setPersonalEmail("   ");
        when(employeeDAO.findById(10)).thenReturn(employee);

        ServiceResult<Integer> result = service.sendAccountInformation(10, adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_NO_PERSONAL_EMAIL, result.getMessage());
        verifyNoInteractions(emailSender);
        verify(employeeDAO, never()).updatePasswordHash(anyInt(), anyString());
    }

    @Test
    public void testSendAccountInformation_emailSendFailed_doesNotChangePassword() {
        User employee = createSampleUser(10, 2);
        when(employeeDAO.findById(10)).thenReturn(employee);
        when(emailSender.send(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        ServiceResult<Integer> result = service.sendAccountInformation(10, adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_MAIL_FAILED, result.getMessage());
        verify(employeeDAO, never()).updatePasswordHash(anyInt(), anyString());
    }

    @Test
    public void testSendAccountInformation_updatePasswordHashFailed() {
        User employee = createSampleUser(10, 2);
        when(employeeDAO.findById(10)).thenReturn(employee);
        when(emailSender.send(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(employeeDAO.updatePasswordHash(eq(10), anyString())).thenReturn(false);

        ServiceResult<Integer> result = service.sendAccountInformation(10, adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_SEND_FAILED, result.getMessage());
    }

    // -------------------------------------------------------------------------
    // 6. UC-29: toggleEmployeeStatus (Khóa / Mở khóa)
    // -------------------------------------------------------------------------
    @Test
    public void testToggleEmployeeStatus_banActive_success() {
        User target = createSampleUser(10, 2);
        target.setDeleted(false);
        when(employeeDAO.findById(10)).thenReturn(target);
        when(employeeDAO.setActive(10, false)).thenReturn(true);

        ServiceResult<Boolean> result = service.toggleEmployeeStatus(10, adminActor);
        assertTrue(result.isSuccess());
        assertFalse(result.getData());
        verify(employeeDAO).setActive(10, false);
    }

    @Test
    public void testToggleEmployeeStatus_unbanInactive_success() {
        User target = createSampleUser(10, 2);
        target.setDeleted(true);
        when(employeeDAO.findById(10)).thenReturn(target);
        when(employeeDAO.setActive(10, true)).thenReturn(true);

        ServiceResult<Boolean> result = service.toggleEmployeeStatus(10, adminActor);
        assertTrue(result.isSuccess());
        assertTrue(result.getData());
        verify(employeeDAO).setActive(10, true);
    }

    @Test
    public void testToggleEmployeeStatus_selfBan_forbidden() {
        User target = createSampleUser(1, 1);
        when(employeeDAO.findById(1)).thenReturn(target);

        ServiceResult<Boolean> result = service.toggleEmployeeStatus(1, adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_CANNOT_SELF_BAN, result.getMessage());
        verify(employeeDAO, never()).setActive(anyInt(), anyBoolean());
    }

    @Test
    public void testToggleEmployeeStatus_notFound() {
        when(employeeDAO.findById(999)).thenReturn(null);

        ServiceResult<Boolean> result = service.toggleEmployeeStatus(999, adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_NOT_FOUND, result.getMessage());
        verify(employeeDAO, never()).setActive(anyInt(), anyBoolean());
    }

    @Test
    public void testToggleEmployeeStatus_daoFails() {
        User target = createSampleUser(10, 2);
        target.setDeleted(false);
        when(employeeDAO.findById(10)).thenReturn(target);
        when(employeeDAO.setActive(10, false)).thenReturn(false);

        ServiceResult<Boolean> result = service.toggleEmployeeStatus(10, adminActor);
        assertFalse(result.isSuccess());
        assertEquals(EmployeeService.ERR_TOGGLE_FAILED, result.getMessage());
    }
}
