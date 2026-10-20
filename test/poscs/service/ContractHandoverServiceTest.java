package poscs.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.ContractDAO;
import poscs.dao.ContractHandoverDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.ContractHandover;
import poscs.model.Department;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit test toàn diện cho ContractHandoverService (Task 4.11).
 * Đáp ứng: UC-48, UC-49 và các quy tắc phân quyền, kiểm tra biên, xử lý lỗi.
 */
public class ContractHandoverServiceTest {

    private static final String PROGRESS_DRAFT = poscs.validator.ContractValidator.PROGRESS_DRAFT;
    private static final String PROGRESS_SIGNED = poscs.validator.ContractValidator.PROGRESS_SIGNED;
    private static final String PROGRESS_LIQUIDATED = poscs.validator.ContractValidator.PROGRESS_LIQUIDATED;
    private static final String PROGRESS_TERMINATED = poscs.validator.ContractValidator.PROGRESS_TERMINATED;

    private ContractHandoverDAO handoverDAO;
    private ContractDAO contractDAO;
    private EmployeeDAO employeeDAO;
    private PermissionService permissionService;
    private ContractHandoverService service;

    private User adminUser;
    private User salesManager;
    private User salesSubordinate;
    private User techUserDept1;
    private User employeeDept2;
    private User deletedUser;

    @Before
    public void setUp() {
        handoverDAO = mock(ContractHandoverDAO.class);
        contractDAO = mock(ContractDAO.class);
        employeeDAO = mock(EmployeeDAO.class);

        adminUser = buildUser(1, PermissionService.ROLE_ADMIN, 1, null, false);
        salesManager = buildUser(2, PermissionService.ROLE_SALES, 2, null, false);
        salesSubordinate = buildUser(3, PermissionService.ROLE_SALES, 2, 2, false);
        techUserDept1 = buildUser(4, PermissionService.ROLE_TECHNICAL, 1, null, false);
        employeeDept2 = buildUser(5, PermissionService.ROLE_SALES, 2, 2, false);
        deletedUser = buildUser(6, PermissionService.ROLE_SALES, 2, null, true);

        when(employeeDAO.findById(1)).thenReturn(adminUser);
        when(employeeDAO.findById(2)).thenReturn(salesManager);
        when(employeeDAO.findById(3)).thenReturn(salesSubordinate);
        when(employeeDAO.findById(4)).thenReturn(techUserDept1);
        when(employeeDAO.findById(5)).thenReturn(employeeDept2);
        when(employeeDAO.findById(6)).thenReturn(deletedUser);

        permissionService = new PermissionService(employeeDAO);
        service = new ContractHandoverService(handoverDAO, contractDAO, employeeDAO, permissionService);
    }

    private User buildUser(int id, String roleName, int deptId, Integer managerId, boolean deleted) {
        User u = new User();
        u.setUserId(id);
        u.setUsername("user" + id);
        u.setDepartmentId(deptId);
        u.setManagerId(managerId);
        u.setDeleted(deleted);
        Role r = new Role();
        r.setRoleId(id);
        r.setRoleName(roleName);
        u.setRole(r);
        return u;
    }

    private Contract sampleContract(int id, String progressStatus) {
        Contract c = new Contract();
        c.setContractId(id);
        c.setContractCode("HD-TEST-" + id);
        c.setTitle("Hợp đồng thử nghiệm");
        c.setProgressStatus(progressStatus);
        c.setDeleted(false);
        return c;
    }

    // ==================================================================
    // UC-48: initiateHandover - Phân quyền
    // ==================================================================

    @Test
    public void testInitiateHandover_UnauthorizedActors_FailForbidden() {
        // Null actor
        ServiceResult<Integer> r1 = service.initiateHandover(10, List.of(1), "Ghi chú", null);
        assertFalse(r1.isSuccess());
        assertEquals(ContractHandoverService.ERR_FORBIDDEN, r1.getMessage());

        // Deleted actor
        ServiceResult<Integer> r2 = service.initiateHandover(10, List.of(1), "Ghi chú", deletedUser);
        assertFalse(r2.isSuccess());
        assertEquals(ContractHandoverService.ERR_FORBIDDEN, r2.getMessage());

        // Sales subordinate
        ServiceResult<Integer> r3 = service.initiateHandover(10, List.of(1), "Ghi chú", salesSubordinate);
        assertFalse(r3.isSuccess());
        assertEquals(ContractHandoverService.ERR_FORBIDDEN, r3.getMessage());

        // Technical user
        ServiceResult<Integer> r4 = service.initiateHandover(10, List.of(1), "Ghi chú", techUserDept1);
        assertFalse(r4.isSuccess());
        assertEquals(ContractHandoverService.ERR_FORBIDDEN, r4.getMessage());
    }

    // ==================================================================
    // UC-48: initiateHandover - Hợp đồng & Trạng thái
    // ==================================================================

    @Test
    public void testInitiateHandover_ContractNotFoundOrDeleted() {
        // contractId <= 0
        ServiceResult<Integer> r1 = service.initiateHandover(0, List.of(1), "Note", adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractHandoverService.ERR_NOT_FOUND, r1.getMessage());

        // contract not found in DB
        when(contractDAO.findById(999)).thenReturn(null);
        ServiceResult<Integer> r2 = service.initiateHandover(999, List.of(1), "Note", adminUser);
        assertFalse(r2.isSuccess());
        assertEquals(ContractHandoverService.ERR_NOT_FOUND, r2.getMessage());

        // contract soft-deleted
        Contract deletedContract = sampleContract(100, PROGRESS_DRAFT);
        deletedContract.setDeleted(true);
        when(contractDAO.findById(100)).thenReturn(deletedContract);
        ServiceResult<Integer> r3 = service.initiateHandover(100, List.of(1), "Note", adminUser);
        assertFalse(r3.isSuccess());
        assertEquals(ContractHandoverService.ERR_NOT_FOUND, r3.getMessage());
    }

    @Test
    public void testInitiateHandover_FrozenContract_Fails() {
        Contract liquidated = sampleContract(10, PROGRESS_LIQUIDATED);
        when(contractDAO.findById(10)).thenReturn(liquidated);

        ServiceResult<Integer> r1 = service.initiateHandover(10, List.of(1), "Note", adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractHandoverService.ERR_HANDOVER_FROZEN, r1.getMessage());

        Contract terminated = sampleContract(11, PROGRESS_TERMINATED);
        when(contractDAO.findById(11)).thenReturn(terminated);

        ServiceResult<Integer> r2 = service.initiateHandover(11, List.of(1), "Note", salesManager);
        assertFalse(r2.isSuccess());
        assertEquals(ContractHandoverService.ERR_HANDOVER_FROZEN, r2.getMessage());
    }

    // ==================================================================
    // UC-48: initiateHandover - Kiểm tra danh sách phòng ban & Ghi chú
    // ==================================================================

    @Test
    public void testInitiateHandover_DepartmentValidation() {
        Contract contract = sampleContract(10, PROGRESS_DRAFT);
        when(contractDAO.findById(10)).thenReturn(contract);

        // Null list
        ServiceResult<Integer> r1 = service.initiateHandover(10, null, "Note", adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractHandoverService.ERR_NO_DEPARTMENT, r1.getMessage());
        assertTrue(r1.getFieldErrors().containsKey(ContractHandoverService.FIELD_DEPARTMENT_ID));

        // Empty list
        ServiceResult<Integer> r2 = service.initiateHandover(10, Collections.emptyList(), "Note", adminUser);
        assertFalse(r2.isSuccess());
        assertEquals(ContractHandoverService.ERR_NO_DEPARTMENT, r2.getMessage());

        // List containing null
        List<Integer> listWithNull = new ArrayList<>();
        listWithNull.add(1);
        listWithNull.add(null);
        ServiceResult<Integer> r3 = service.initiateHandover(10, listWithNull, "Note", adminUser);
        assertFalse(r3.isSuccess());
        assertEquals(ContractHandoverService.ERR_INVALID, r3.getMessage());

        // List containing <= 0
        ServiceResult<Integer> r4 = service.initiateHandover(10, List.of(1, 0, 2), "Note", adminUser);
        assertFalse(r4.isSuccess());
        assertEquals(ContractHandoverService.ERR_INVALID, r4.getMessage());

        // List containing duplicates
        ServiceResult<Integer> r5 = service.initiateHandover(10, List.of(1, 2, 1), "Note", adminUser);
        assertFalse(r5.isSuccess());
        assertEquals(ContractHandoverService.ERR_INVALID, r5.getMessage());
    }

    @Test
    public void testInitiateHandover_NoteLengthValidation() {
        Contract contract = sampleContract(10, PROGRESS_SIGNED);
        when(contractDAO.findById(10)).thenReturn(contract);

        String tooLongNote = "A".repeat(ContractHandoverService.HANDOVER_NOTE_MAX_LENGTH + 1);
        ServiceResult<Integer> r1 = service.initiateHandover(10, List.of(1, 2), tooLongNote, adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractHandoverService.ERR_NOTE_TOO_LONG, r1.getMessage());
        assertTrue(r1.getFieldErrors().containsKey(ContractHandoverService.FIELD_HANDOVER_NOTE));

        // Exact limit (255 chars) -> succeeds
        String exactLimitNote = "B".repeat(ContractHandoverService.HANDOVER_NOTE_MAX_LENGTH);
        when(handoverDAO.handOverToDepartments(10, List.of(1, 2), exactLimitNote, adminUser.getUserId()))
                .thenReturn(2);
        ServiceResult<Integer> r2 = service.initiateHandover(10, List.of(1, 2), exactLimitNote, adminUser);
        assertTrue(r2.isSuccess());
        assertEquals(Integer.valueOf(2), r2.getData());
    }

    // ==================================================================
    // UC-48: initiateHandover - Xử lý kết quả từ DAO
    // ==================================================================

    @Test
    public void testInitiateHandover_DaoResults() {
        Contract contract = sampleContract(10, PROGRESS_SIGNED);
        when(contractDAO.findById(10)).thenReturn(contract);

        // Already pending
        when(handoverDAO.handOverToDepartments(10, List.of(1), "Note", salesManager.getUserId()))
                .thenReturn(ContractHandoverDAO.HANDOVER_ALREADY_PENDING);
        ServiceResult<Integer> r1 = service.initiateHandover(10, List.of(1), "Note", salesManager);
        assertFalse(r1.isSuccess());
        assertEquals(ContractHandoverService.ERR_HANDOVER_PENDING, r1.getMessage());

        // DAO frozen
        when(handoverDAO.handOverToDepartments(10, List.of(1), "Note", salesManager.getUserId()))
                .thenReturn(ContractHandoverDAO.HANDOVER_FROZEN);
        ServiceResult<Integer> r2 = service.initiateHandover(10, List.of(1), "Note", salesManager);
        assertFalse(r2.isSuccess());
        assertEquals(ContractHandoverService.ERR_HANDOVER_FROZEN, r2.getMessage());

        // DAO failed <= 0
        when(handoverDAO.handOverToDepartments(10, List.of(1), "Note", salesManager.getUserId()))
                .thenReturn(ContractHandoverDAO.HANDOVER_FAILED);
        ServiceResult<Integer> r3 = service.initiateHandover(10, List.of(1), "Note", salesManager);
        assertFalse(r3.isSuccess());
        assertEquals(ContractHandoverService.ERR_HANDOVER_FAILED, r3.getMessage());

        // Success
        when(handoverDAO.handOverToDepartments(10, List.of(1, 2), "Note", salesManager.getUserId()))
                .thenReturn(2);
        ServiceResult<Integer> r4 = service.initiateHandover(10, List.of(1, 2), "Note", salesManager);
        assertTrue(r4.isSuccess());
        assertEquals(Integer.valueOf(2), r4.getData());
    }

    // ==================================================================
    // UC-49: completeHandover - Phân quyền & Kiểm tra
    // ==================================================================

    @Test
    public void testCompleteHandover_Permissions() {
        // Admin confirms for any department -> permitted
        when(handoverDAO.completeHandover(100, adminUser.getUserId(), "Đã xong"))
                .thenReturn(true);
        ServiceResult<Void> r1 = service.completeHandover(100, 2, "Đã xong", adminUser);
        assertTrue(r1.isSuccess());

        // User belonging to matching department (dept 1) -> permitted
        when(handoverDAO.completeHandover(101, techUserDept1.getUserId(), "Kỹ thuật đã nghiệm thu"))
                .thenReturn(true);
        ServiceResult<Void> r2 = service.completeHandover(101, 1, "Kỹ thuật đã nghiệm thu", techUserDept1);
        assertTrue(r2.isSuccess());

        // User in department 2 trying to complete department 1 -> MSG-022 Forbidden
        ServiceResult<Void> r3 = service.completeHandover(101, 1, "Kế toán duyệt", employeeDept2);
        assertFalse(r3.isSuccess());
        assertEquals(ContractHandoverService.ERR_FORBIDDEN, r3.getMessage());

        // Deleted user -> Forbidden
        ServiceResult<Void> r4 = service.completeHandover(101, 2, "Duyệt", deletedUser);
        assertFalse(r4.isSuccess());
        assertEquals(ContractHandoverService.ERR_FORBIDDEN, r4.getMessage());
    }

    @Test
    public void testCompleteHandover_InvalidIds() {
        // handoverId <= 0
        ServiceResult<Void> r1 = service.completeHandover(0, 1, "Done", adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractHandoverService.ERR_INVALID, r1.getMessage());

        // departmentId <= 0
        ServiceResult<Void> r2 = service.completeHandover(10, 0, "Done", adminUser);
        assertFalse(r2.isSuccess());
        assertEquals(ContractHandoverService.ERR_INVALID, r2.getMessage());

        // contractId < 0
        ServiceResult<Void> r3 = service.completeHandover(-1, 10, 1, "Done", adminUser);
        assertFalse(r3.isSuccess());
        assertEquals(ContractHandoverService.ERR_INVALID, r3.getMessage());

        // contractId > 0 but contract not found
        when(contractDAO.findById(999)).thenReturn(null);
        ServiceResult<Void> r4 = service.completeHandover(999, 10, 1, "Done", adminUser);
        assertFalse(r4.isSuccess());
        assertEquals(ContractHandoverService.ERR_NOT_FOUND, r4.getMessage());
    }

    @Test
    public void testCompleteHandover_DoneNoteValidation() {
        // Null doneNote
        ServiceResult<Void> r1 = service.completeHandover(10, 1, null, adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractHandoverService.ERR_MISSING_DONE_NOTE, r1.getMessage());
        assertTrue(r1.getFieldErrors().containsKey(ContractHandoverService.FIELD_DONE_NOTE));

        // Empty doneNote
        ServiceResult<Void> r2 = service.completeHandover(10, 1, "", adminUser);
        assertFalse(r2.isSuccess());
        assertEquals(ContractHandoverService.ERR_MISSING_DONE_NOTE, r2.getMessage());

        // Blank doneNote
        ServiceResult<Void> r3 = service.completeHandover(10, 1, "   \t\n  ", adminUser);
        assertFalse(r3.isSuccess());
        assertEquals(ContractHandoverService.ERR_MISSING_DONE_NOTE, r3.getMessage());

        // Too long doneNote (> 500)
        String tooLong = "X".repeat(ContractHandoverService.DONE_NOTE_MAX_LENGTH + 1);
        ServiceResult<Void> r4 = service.completeHandover(10, 1, tooLong, adminUser);
        assertFalse(r4.isSuccess());
        assertEquals(ContractHandoverService.ERR_NOTE_TOO_LONG, r4.getMessage());
        assertTrue(r4.getFieldErrors().containsKey(ContractHandoverService.FIELD_DONE_NOTE));
    }

    @Test
    public void testCompleteHandover_DaoFailsAndSucceeds() {
        // DAO returns false
        when(handoverDAO.completeHandover(10, adminUser.getUserId(), "Đã xử lý"))
                .thenReturn(false);
        ServiceResult<Void> r1 = service.completeHandover(10, 1, "Đã xử lý", adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractHandoverService.ERR_COMPLETE_FAILED, r1.getMessage());

        // DAO returns true with trim verification
        when(handoverDAO.completeHandover(11, adminUser.getUserId(), "Đã xử lý"))
                .thenReturn(true);
        ServiceResult<Void> r2 = service.completeHandover(11, 1, "   Đã xử lý   ", adminUser);
        assertTrue(r2.isSuccess());
        verify(handoverDAO).completeHandover(11, adminUser.getUserId(), "Đã xử lý");
    }

    // ==================================================================
    // Truy vấn & Overview
    // ==================================================================

    @Test
    public void testFindHandoversOf() {
        assertEquals(Collections.emptyList(), service.findHandoversOf(0));
        assertEquals(Collections.emptyList(), service.findHandoversOf(-5));

        ContractHandover h1 = new ContractHandover();
        h1.setHandoverId(1);
        when(handoverDAO.findHandoversOf(10)).thenReturn(List.of(h1));

        List<ContractHandover> list = service.findHandoversOf(10);
        assertEquals(1, list.size());
        assertEquals(1, list.get(0).getHandoverId());
    }

    @Test
    public void testFindPendingHandovers() {
        // Null actor
        assertEquals(Collections.emptyList(), service.findPendingHandovers(null, false));

        // viewMine = false -> ownerIds = null
        ContractHandover h = new ContractHandover();
        when(handoverDAO.findPendingHandovers(null)).thenReturn(List.of(h));
        List<ContractHandover> all = service.findPendingHandovers(salesManager, false);
        assertEquals(1, all.size());

        // viewMine = true -> uses team IDs
        when(employeeDAO.findTeamUserIds(salesManager.getUserId())).thenReturn(List.of(2, 3));
        when(handoverDAO.findPendingHandovers(List.of(2, 3))).thenReturn(List.of(h));
        List<ContractHandover> mine = service.findPendingHandovers(salesManager, true);
        assertEquals(1, mine.size());
        verify(employeeDAO).findTeamUserIds(salesManager.getUserId());
    }

    @Test
    public void testGetQueueOverview() {
        // Empty / null list
        ContractHandoverService.HandoverQueueOverview emptyOverview = service.getQueueOverview(null);
        assertEquals(0, emptyOverview.getTotalScope());
        assertEquals(0, emptyOverview.getSlowestDays());
        assertTrue(emptyOverview.getCountByDepartment().isEmpty());

        // Multiple handovers
        Instant now = Instant.now();
        ContractHandover h1 = new ContractHandover();
        h1.setDepartmentName("Kế toán");
        h1.setHandedAt(Timestamp.from(now.minusSeconds(86400 * 5))); // 5 days ago

        ContractHandover h2 = new ContractHandover();
        h2.setDepartmentName("Kế toán");
        h2.setHandedAt(Timestamp.from(now.minusSeconds(86400 * 2))); // 2 days ago

        ContractHandover h3 = new ContractHandover();
        h3.setDepartmentName("Dự án");
        h3.setHandedAt(Timestamp.from(now.minusSeconds(86400 * 10))); // 10 days ago

        ContractHandoverService.HandoverQueueOverview overview = service.getQueueOverview(List.of(h1, h2, h3));
        assertEquals(3, overview.getTotalScope());
        assertEquals(Integer.valueOf(2), overview.getCountByDepartment().get("Kế toán"));
        assertEquals(Integer.valueOf(1), overview.getCountByDepartment().get("Dự án"));
        assertTrue(overview.getSlowestDays() >= 9);
    }

    @Test
    public void testFindAllDepartments() {
        Department d = new Department();
        d.setDepartmentId(1);
        d.setDepartmentName("Kế toán");
        when(employeeDAO.findAllDepartments()).thenReturn(List.of(d));

        List<Department> departments = service.findAllDepartments();
        assertEquals(1, departments.size());
        assertEquals("Kế toán", departments.get(0).getDepartmentName());
    }
}
