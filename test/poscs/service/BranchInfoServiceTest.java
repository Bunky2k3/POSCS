package poscs.service;

import java.sql.Timestamp;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import poscs.dao.BranchInfoDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.BranchInfo;
import poscs.model.Role;
import poscs.model.User;
import poscs.validator.BranchInfoValidator;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test BranchInfoService (UC-55): dữ liệu sai theo BR-57 thì không gọi DAO;
 * chỉ Admin được lưu (MSG-022); DAO hỏng thì báo lỗi; lưu xong trả MSG-057
 * kèm bản đã trim. Validator dùng bản thật để khoá đúng câu thông báo.
 */
public class BranchInfoServiceTest {

    private static final int ADMIN_ID = 1;

    private BranchInfoDAO branchInfoDAO;
    private EmployeeDAO employeeDAO;
    private PermissionService permissionService;
    private BranchInfoService service;
    private User admin;

    @Before
    public void setUp() {
        branchInfoDAO = mock(BranchInfoDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        permissionService = mock(PermissionService.class);
        service = new BranchInfoService(branchInfoDAO, employeeDAO, permissionService, new BranchInfoValidator());

        admin = user(ADMIN_ID, "Admin");
        when(permissionService.isAdmin(admin)).thenReturn(true);
    }

    private static User user(int id, String roleName) {
        User u = new User();
        u.setUserId(id);
        u.setUsername("user" + id);
        u.setRole(new Role(id, roleName));
        return u;
    }

    private static BranchInfo info(String name, String position, String phone, String fax) {
        BranchInfo b = new BranchInfo();
        b.setDirectorName(name);
        b.setDirectorPosition(position);
        b.setPhone(phone);
        b.setFax(fax);
        return b;
    }

    // ------------------------------------------------------------------
    // Lưu thành công
    // ------------------------------------------------------------------

    @Test
    public void update_validData_savesTrimmedValuesWithActorAndReturnsMsg057() {
        when(branchInfoDAO.update(any(BranchInfo.class), eq(ADMIN_ID))).thenReturn(true);

        ServiceResult<BranchInfo> res = service.update(
                info("  Nguyễn Văn An ", " Giám đốc chi nhánh ", " (0233) 3852 123 ", "   "), admin);

        assertTrue(res.isSuccess());
        assertEquals("Đã lưu thông tin chi nhánh.", res.getMessage());
        ArgumentCaptor<BranchInfo> saved = ArgumentCaptor.forClass(BranchInfo.class);
        verify(branchInfoDAO).update(saved.capture(), eq(ADMIN_ID));
        assertEquals("Nguyễn Văn An", saved.getValue().getDirectorName());
        assertEquals("Giám đốc chi nhánh", saved.getValue().getDirectorPosition());
        assertEquals("(0233) 3852 123", saved.getValue().getPhone());
        assertNull("ô fax trống phải lưu NULL", saved.getValue().getFax());
        assertSame(saved.getValue(), res.getData());
    }

    @Test
    public void msgSaved_matchesSpecMsg057() {
        assertEquals("Đã lưu thông tin chi nhánh.", BranchInfoService.MSG_SAVED);
    }

    // ------------------------------------------------------------------
    // Dữ liệu sai: không gọi DAO
    // ------------------------------------------------------------------

    @Test
    public void update_missingName_failsWithMsg058AndNeverCallsDao() {
        ServiceResult<BranchInfo> res = service.update(info("  ", "Giám đốc chi nhánh", null, null), admin);

        assertFalse(res.isSuccess());
        assertEquals(BranchInfoValidator.MSG_REQUIRED,
                res.getFieldErrors().get(BranchInfoValidator.FIELD_DIRECTOR_NAME));
        verify(branchInfoDAO, never()).update(any(), anyInt());
    }

    @Test
    public void update_invalidPhone_failsWithMsg015AndNeverCallsDao() {
        ServiceResult<BranchInfo> res = service.update(info("Nguyễn Văn An", "Giám đốc", "09abc", null), admin);

        assertFalse(res.isSuccess());
        assertEquals("Số điện thoại không hợp lệ.", res.getFieldErrors().get(BranchInfoValidator.FIELD_PHONE));
        verify(branchInfoDAO, never()).update(any(), anyInt());
    }

    @Test
    public void update_invalidFax_failsWithMsg059AndNeverCallsDao() {
        ServiceResult<BranchInfo> res = service.update(
                info("Nguyễn Văn An", "Giám đốc", null, "(024) 3852 1234 - 1105"), admin);

        assertFalse(res.isSuccess());
        assertEquals("Số fax không hợp lệ.", res.getFieldErrors().get(BranchInfoValidator.FIELD_FAX));
        verify(branchInfoDAO, never()).update(any(), anyInt());
    }

    @Test
    public void update_nullInfo_failsValidationAndNeverCallsDao() {
        ServiceResult<BranchInfo> res = service.update(null, admin);

        assertFalse(res.isSuccess());
        assertEquals(2, res.getFieldErrors().size());
        verify(branchInfoDAO, never()).update(any(), anyInt());
    }

    // ------------------------------------------------------------------
    // DAO không ghi được
    // ------------------------------------------------------------------

    @Test
    public void update_daoReturnsFalse_failsWithSaveFailedMessage() {
        when(branchInfoDAO.update(any(BranchInfo.class), eq(ADMIN_ID))).thenReturn(false);

        ServiceResult<BranchInfo> res = service.update(info("Nguyễn Văn An", "Giám đốc", null, null), admin);

        assertFalse(res.isSuccess());
        assertEquals(BranchInfoService.MSG_SAVE_FAILED, res.getMessage());
        assertFalse(res.hasFieldErrors());
    }

    // ------------------------------------------------------------------
    // Phân quyền (MSG-022)
    // ------------------------------------------------------------------

    @Test
    public void update_nullActor_forbiddenAndNeverCallsDao() {
        ServiceResult<BranchInfo> res = service.update(info("Nguyễn Văn An", "Giám đốc", null, null), null);

        assertFalse(res.isSuccess());
        assertEquals("Bạn không có quyền truy cập nội dung này.", res.getMessage());
        verify(branchInfoDAO, never()).update(any(), anyInt());
    }

    @Test
    public void update_nonAdminActor_forbiddenAndNeverCallsDao() {
        for (String role : new String[]{"Sales", "Kỹ thuật"}) {
            User actor = user(7, role);
            when(permissionService.isAdmin(actor)).thenReturn(false);

            ServiceResult<BranchInfo> res = service.update(info("Nguyễn Văn An", "Giám đốc", null, null), actor);

            assertFalse(role, res.isSuccess());
            assertEquals(role, BranchInfoService.MSG_FORBIDDEN, res.getMessage());
        }
        verify(branchInfoDAO, never()).update(any(), anyInt());
    }

    /** Phiên còn ghi Admin nhưng CSDL đã đổi vai: PermissionService đọc CSDL nên vẫn chặn. */
    @Test
    public void update_sessionSaysAdminButDatabaseDoesNot_forbidden() {
        User staleAdmin = user(9, "Admin");
        when(permissionService.isAdmin(staleAdmin)).thenReturn(false);

        ServiceResult<BranchInfo> res = service.update(info("Nguyễn Văn An", "Giám đốc", null, null), staleAdmin);

        assertEquals(BranchInfoService.MSG_FORBIDDEN, res.getMessage());
        verify(branchInfoDAO, never()).update(any(), anyInt());
    }

    // ------------------------------------------------------------------
    // Đọc
    // ------------------------------------------------------------------

    @Test
    public void get_delegatesToDao() {
        BranchInfo stored = info("Nguyễn Văn An", "Giám đốc", null, null);
        when(branchInfoDAO.get()).thenReturn(stored);

        assertSame(stored, service.get());
    }

    @Test
    public void get_daoReturnsNull_returnsNull() {
        when(branchInfoDAO.get()).thenReturn(null);

        assertNull(service.get());
    }

    @Test
    public void findUpdaterName_returnsFullNameOfUpdater() {
        BranchInfo stored = info("Nguyễn Văn An", "Giám đốc", null, null);
        stored.setUpdatedBy(5);
        stored.setUpdatedAt(Timestamp.valueOf("2026-11-19 14:30:00"));
        User updater = user(5, "Admin");
        updater.setLastName("Trần");
        updater.setMiddleName("Thị");
        updater.setFirstName("Bình");
        when(employeeDAO.findById(5)).thenReturn(updater);

        assertEquals("Trần Thị Bình", service.findUpdaterName(stored));
    }

    @Test
    public void findUpdaterName_neverUpdated_returnsNullWithoutQuery() {
        BranchInfo seeded = info(null, "Giám đốc chi nhánh", null, null);

        assertNull(service.findUpdaterName(seeded));
        assertNull(service.findUpdaterName(null));
        verifyNoInteractions(employeeDAO);
    }

    @Test
    public void findUpdaterName_updaterNotFound_returnsNull() {
        BranchInfo stored = info("Nguyễn Văn An", "Giám đốc", null, null);
        stored.setUpdatedBy(42);
        when(employeeDAO.findById(42)).thenReturn(null);

        assertNull(service.findUpdaterName(stored));
    }
}
