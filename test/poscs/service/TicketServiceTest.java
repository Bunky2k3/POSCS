package poscs.service;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import poscs.common.Period;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.DBContext;
import poscs.dao.EmployeeDAO;
import poscs.dao.JdbcStub;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Contract;
import poscs.model.Enterprise;
import poscs.model.Role;
import poscs.model.TechnicalRequest;
import poscs.model.TechnicalRequestHistory;
import poscs.model.User;
import poscs.validator.TicketValidator;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho TicketService (Task 5.2).
 * Bao phủ kịch bản đúng và vi phạm của UC-16, UC-17, UC-21, UC-22, UC-23
 * và BR-38, BR-39, BR-40, BR-41, BR-42, BR-43, BR-47.
 */
public class TicketServiceTest {

    private FakeTicketDAO ticketDAO;
    private FakeCustomerDAO customerDAO;
    private FakeContractDAO contractDAO;
    private FakeEmployeeDAO employeeDAO;
    private PermissionService permissionService;
    private TicketValidator ticketValidator;
    private TicketService service;

    private User adminUser;
    private User salesManager;
    private User salesSubordinate;
    private User techAssigned;
    private User techOther;
    private User deletedUser;

    @Before
    public void setUp() {
        ticketDAO = new FakeTicketDAO();
        customerDAO = new FakeCustomerDAO();
        contractDAO = new FakeContractDAO();
        employeeDAO = new FakeEmployeeDAO();

        adminUser = buildUser(1, PermissionService.ROLE_ADMIN, null, false);
        salesManager = buildUser(2, PermissionService.ROLE_SALES, null, false);
        salesSubordinate = buildUser(3, PermissionService.ROLE_SALES, 2, false);
        techAssigned = buildUser(4, PermissionService.ROLE_TECHNICAL, null, false);
        techOther = buildUser(5, PermissionService.ROLE_TECHNICAL, null, false);
        deletedUser = buildUser(6, PermissionService.ROLE_SALES, null, true);

        employeeDAO.putUser(adminUser);
        employeeDAO.putUser(salesManager);
        employeeDAO.putUser(salesSubordinate);
        employeeDAO.putUser(techAssigned);
        employeeDAO.putUser(techOther);
        employeeDAO.putUser(deletedUser);

        customerDAO.putRoles(10, List.of(TicketService.ROLE_BUYER));
        customerDAO.putRoles(20, List.of(PermissionService.ROLE_SUPPLIER));
        customerDAO.putRoles(30, List.of(TicketService.ROLE_BUYER, PermissionService.ROLE_SUPPLIER));

        Contract c100 = new Contract();
        c100.setContractId(100);
        c100.setContractCode("HD-0100");
        c100.setEnterpriseId(10);
        contractDAO.putContract(c100);

        Contract c200 = new Contract();
        c200.setContractId(200);
        c200.setContractCode("HD-0200");
        c200.setEnterpriseId(30);
        contractDAO.putContract(c200);

        permissionService = new PermissionService(employeeDAO);
        ticketValidator = new TicketValidator();
        service = new TicketService(ticketDAO, customerDAO, contractDAO, employeeDAO,
                permissionService, ticketValidator);
    }

    // ==================================================================
    // 1. BR-38: Sinh mã ticket duy nhất & tạo mới trong giao dịch
    // ==================================================================

    @Test
    public void createTicket_generatesSequentialUniqueCodeAndWritesInitialHistory() {
        TechnicalRequest t1 = validCreateRequest();
        ServiceResult<Integer> r1 = service.createTicket(t1, salesManager);
        assertTrue(r1.isSuccess());
        assertEquals(Integer.valueOf(1), r1.getData());
        assertEquals("TK-0001", t1.getTicketCode());

        TechnicalRequest t2 = validCreateRequest();
        ServiceResult<Integer> r2 = service.createTicket(t2, salesSubordinate);
        assertTrue(r2.isSuccess());
        assertEquals(Integer.valueOf(2), r2.getData());
        assertEquals("TK-0002", t2.getTicketCode());

        List<TechnicalRequestHistory> history = service.findHistoryByTicketId(1);
        assertEquals(1, history.size());
        assertEquals("", history.get(0).getFromStatus());
        assertEquals(TicketService.STATUS_NEW, history.get(0).getToStatus());
        assertEquals(salesManager.getUserId(), history.get(0).getChangedBy());
    }

    @Test
    public void createTicket_ignoresUserSuppliedTicketCodeAndOverwritesWithSystemCode() {
        TechnicalRequest t = validCreateRequest();
        t.setTicketCode("MANUAL-9999");

        ServiceResult<Integer> res = service.createTicket(t, adminUser);
        assertTrue(res.isSuccess());
        assertEquals("TK-0001", t.getTicketCode());
    }

    @Test
    public void createTicket_failsWhenCodeGenerationReturnsNullOrInsertFails() {
        ticketDAO.nextCodeOverride = "";
        ServiceResult<Integer> r1 = service.createTicket(validCreateRequest(), adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(TicketService.ERR_CREATE_FAILED, r1.getMessage());

        ticketDAO.nextCodeOverride = null;
        ticketDAO.forceInsertFailure = true;
        ServiceResult<Integer> r2 = service.createTicket(validCreateRequest(), adminUser);
        assertFalse(r2.isSuccess());
        assertEquals(TicketService.ERR_CREATE_FAILED, r2.getMessage());
    }

    @Test
    public void createTicket_jdbcTransaction_retriesOnDuplicateTicketCodeAndCommitsWithInitialHistory() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement psGenCode = JdbcStub.statementReturning(
                JdbcStub.singleRow(JdbcStub.row("ticket_code", "TK-0005")));
        PreparedStatement psInsertTicket = mock(PreparedStatement.class);
        ResultSet rsKeys = JdbcStub.singleRow(JdbcStub.row("GENERATED_KEY", 42));
        when(psInsertTicket.getGeneratedKeys()).thenReturn(rsKeys);
        when(psInsertTicket.executeUpdate())
                .thenThrow(JdbcStub.duplicateKeyError("ticket_code"))
                .thenReturn(1);

        PreparedStatement psInsertHistory = mock(PreparedStatement.class);
        when(psInsertHistory.executeUpdate()).thenReturn(1);

        when(conn.prepareStatement(startsWith("SELECT ticket_code FROM technicalrequests"))).thenReturn(psGenCode);
        when(conn.prepareStatement(startsWith("INSERT INTO technicalrequests"), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenReturn(psInsertTicket);
        when(conn.prepareStatement(startsWith("INSERT INTO technicalrequesthistory"))).thenReturn(psInsertHistory);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            TicketService realDaoService = new TicketService(
                    new TechnicalSupportTicketDAO(), customerDAO, contractDAO, employeeDAO,
                    permissionService, ticketValidator);

            TechnicalRequest t = validCreateRequest();
            ServiceResult<Integer> res = realDaoService.createTicket(t, adminUser);

            assertTrue(res.isSuccess());
            assertEquals(Integer.valueOf(42), res.getData());
            verify(psInsertTicket, times(2)).executeUpdate();
            verify(psInsertHistory, times(1)).executeUpdate();
            verify(conn, atLeastOnce()).commit();
        }
    }

    @Test
    public void createTicket_jdbcTransaction_rollsBackWhenInitialHistoryInsertFails() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement psGenCode = JdbcStub.statementReturning(JdbcStub.emptyResultSet());
        PreparedStatement psInsertTicket = mock(PreparedStatement.class);
        ResultSet rsKeys = JdbcStub.singleRow(JdbcStub.row("GENERATED_KEY", 99));
        when(psInsertTicket.executeUpdate()).thenReturn(1);
        when(psInsertTicket.getGeneratedKeys()).thenReturn(rsKeys);
        PreparedStatement psInsertHistory = mock(PreparedStatement.class);
        when(psInsertHistory.executeUpdate()).thenThrow(new SQLException("History table failure"));

        when(conn.prepareStatement(startsWith("SELECT ticket_code FROM technicalrequests"))).thenReturn(psGenCode);
        when(conn.prepareStatement(startsWith("INSERT INTO technicalrequests"), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenReturn(psInsertTicket);
        when(conn.prepareStatement(startsWith("INSERT INTO technicalrequesthistory"))).thenReturn(psInsertHistory);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            TicketService realDaoService = new TicketService(
                    new TechnicalSupportTicketDAO(), customerDAO, contractDAO, employeeDAO,
                    permissionService, ticketValidator);

            ServiceResult<Integer> res = realDaoService.createTicket(validCreateRequest(), salesManager);

            assertFalse(res.isSuccess());
            assertEquals(TicketService.ERR_CREATE_FAILED, res.getMessage());
            verify(conn).setAutoCommit(false);
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    // ==================================================================
    // 2. BR-39: Các trường bắt buộc & trường do hệ thống gán khi tạo mới
    // ==================================================================

    @Test
    public void createTicket_assignsSystemStatusNewAndTodayCreatedDate() {
        TechnicalRequest t = validCreateRequest();
        t.setStatus(null);
        t.setCreatedDate(null);

        ServiceResult<Integer> res = service.createTicket(t, salesManager);
        assertTrue(res.isSuccess());
        assertEquals(TicketService.STATUS_NEW, t.getStatus());
        assertEquals(Date.valueOf(LocalDate.now()), t.getCreatedDate());
        assertEquals(salesManager.getUserId(), t.getCreatedBy());

        // BR-39: Người gọi truyền ngày quá khứ -> hệ thống vẫn ghi đè thành ngày hiện tại
        TechnicalRequest pastDateTicket = validCreateRequest();
        pastDateTicket.setCreatedDate(Date.valueOf(LocalDate.now().minusDays(15)));
        ServiceResult<Integer> rPast = service.createTicket(pastDateTicket, salesManager);
        assertTrue(rPast.isSuccess());
        assertEquals(Date.valueOf(LocalDate.now()), pastDateTicket.getCreatedDate());
        assertEquals(Date.valueOf(LocalDate.now()), ticketDAO.findById(rPast.getData()).getCreatedDate());
    }

    @Test
    public void createTicket_rejectsNullOrMissingMandatoryFields() {
        // Null request
        ServiceResult<Integer> rNull = service.createTicket(null, adminUser);
        assertFalse(rNull.isSuccess());
        assertEquals(TicketService.ERR_INVALID, rNull.getMessage());

        // Thiếu khách hàng (enterpriseId <= 0)
        TechnicalRequest noEnterprise = validCreateRequest();
        noEnterprise.setEnterpriseId(0);
        ServiceResult<Integer> rEnt = service.createTicket(noEnterprise, adminUser);
        assertFalse(rEnt.isSuccess());
        assertEquals(TicketValidator.MSG_CUSTOMER_REQUIRED,
                rEnt.getFieldErrors().get(TicketValidator.FIELD_ENTERPRISE_ID));

        // Thiếu loại phiếu (null / blank / quá 100 ký tự)
        TechnicalRequest blankType = validCreateRequest();
        blankType.setTicketType("   ");
        ServiceResult<Integer> rType = service.createTicket(blankType, adminUser);
        assertFalse(rType.isSuccess());
        assertEquals(TicketValidator.MSG_TICKET_TYPE_REQUIRED,
                rType.getFieldErrors().get(TicketValidator.FIELD_TICKET_TYPE));

        TechnicalRequest longType = validCreateRequest();
        longType.setTicketType("A".repeat(TicketValidator.MAX_TICKET_TYPE_LENGTH + 1));
        assertFalse(service.createTicket(longType, adminUser).isSuccess());

        // Thiếu kênh tiếp nhận (blank / quá 50 ký tự)
        TechnicalRequest blankChannel = validCreateRequest();
        blankChannel.setReceptionChannel("");
        ServiceResult<Integer> rChan = service.createTicket(blankChannel, adminUser);
        assertFalse(rChan.isSuccess());
        assertEquals(TicketValidator.MSG_CHANNEL_REQUIRED,
                rChan.getFieldErrors().get(TicketValidator.FIELD_RECEPTION_CHANNEL));

        TechnicalRequest longChannel = validCreateRequest();
        longChannel.setReceptionChannel("C".repeat(TicketValidator.MAX_RECEPTION_CHANNEL_LENGTH + 1));
        assertFalse(service.createTicket(longChannel, adminUser).isSuccess());

        // Thiếu kỹ thuật viên (assignedTechnicianId <= 0)
        TechnicalRequest noTech = validCreateRequest();
        noTech.setAssignedTechnicianId(-1);
        ServiceResult<Integer> rTech = service.createTicket(noTech, adminUser);
        assertFalse(rTech.isSuccess());
        assertEquals(TicketValidator.MSG_TECHNICIAN_REQUIRED,
                rTech.getFieldErrors().get(TicketValidator.FIELD_ASSIGNED_TECHNICIAN_ID));

        // Thiếu mô tả sự cố (null / blank)
        TechnicalRequest noDesc = validCreateRequest();
        noDesc.setDescription("  \t ");
        ServiceResult<Integer> rDesc = service.createTicket(noDesc, adminUser);
        assertFalse(rDesc.isSuccess());
        assertEquals(TicketValidator.MSG_DESCRIPTION_REQUIRED,
                rDesc.getFieldErrors().get(TicketValidator.FIELD_DESCRIPTION));

        // Nhóm nguyên nhân vượt 100 ký tự
        TechnicalRequest longCauseCat = validCreateRequest();
        longCauseCat.setCauseCategory("X".repeat(TicketValidator.MAX_CAUSE_CATEGORY_LENGTH + 1));
        assertFalse(service.createTicket(longCauseCat, adminUser).isSuccess());
    }

    @Test
    public void createTicket_rejectsPrePopulatedNonNewStatusOrFutureCreatedDate() {
        TechnicalRequest wrongStatus = validCreateRequest();
        wrongStatus.setStatus(TicketService.STATUS_IN_PROGRESS);
        ServiceResult<Integer> rStatus = service.createTicket(wrongStatus, adminUser);
        assertFalse(rStatus.isSuccess());
        assertEquals(TicketValidator.MSG_SYSTEM_ASSIGNED_FIELDS,
                rStatus.getFieldErrors().get(TicketValidator.FIELD_STATUS));

        TechnicalRequest futureDate = validCreateRequest();
        futureDate.setCreatedDate(Date.valueOf(LocalDate.now().plusDays(1)));
        ServiceResult<Integer> rDate = service.createTicket(futureDate, adminUser);
        assertFalse(rDate.isSuccess());
        assertEquals(TicketValidator.MSG_CREATED_DATE_INVALID,
                rDate.getFieldErrors().get(TicketValidator.FIELD_CREATED_DATE));
    }

    // ==================================================================
    // 3. BR-41: Mức ưu tiên hợp lệ
    // ==================================================================

    @Test
    public void createAndUpdateTicket_acceptAllFourAllowedPrioritiesAndRejectInvalidOnes() {
        for (String p : List.of(TicketService.PRIORITY_LOW, TicketService.PRIORITY_NORMAL,
                TicketService.PRIORITY_HIGH, TicketService.PRIORITY_URGENT)) {
            TechnicalRequest t = validCreateRequest();
            t.setPriority(p);
            ServiceResult<Integer> res = service.createTicket(t, adminUser);
            assertTrue("Priority should be allowed: " + p, res.isSuccess());
        }

        for (String invalid : new String[]{null, "", "   ", "Rất khẩn cấp", "CRITICAL"}) {
            TechnicalRequest badCreate = validCreateRequest();
            badCreate.setPriority(invalid);
            ServiceResult<Integer> rCreate = service.createTicket(badCreate, adminUser);
            assertFalse(rCreate.isSuccess());
            assertTrue(rCreate.getFieldErrors().containsKey(TicketValidator.FIELD_PRIORITY));

            TechnicalRequest badUpdate = copyOf(ticketDAO.findById(1));
            badUpdate.setPriority(invalid);
            ServiceResult<Void> rUpdate = service.updateTicket(badUpdate, null, adminUser);
            assertFalse(rUpdate.isSuccess());
            assertTrue(rUpdate.getFieldErrors().containsKey(TicketValidator.FIELD_PRIORITY));
        }
    }

    // ==================================================================
    // 4. BR-43: Cờ bảo hành Có/Không, mặc định Có
    // ==================================================================

    @Test
    public void createTicket_warrantyDefaultsToTrueWhenNullAndRespectsExplicitBoolean() {
        // Overload 2 tham số với bản ghi mới tạo (chưa gọi setWarranty) -> mặc định Có (true) theo BR-43
        TechnicalRequest newPojo = new TechnicalRequest();
        newPojo.setEnterpriseId(10);
        newPojo.setTicketType("Bảo hành");
        newPojo.setPriority(TicketService.PRIORITY_HIGH);
        newPojo.setReceptionChannel("Điện thoại");
        newPojo.setAssignedTechnicianId(techAssigned.getUserId());
        newPojo.setDescription("Lỗi nguồn");
        assertFalse(newPojo.isWarranty());

        ServiceResult<Integer> rTwoArg = service.createTicket(newPojo, adminUser);
        assertTrue(rTwoArg.isSuccess());
        assertTrue(newPojo.isWarranty());
        assertTrue(ticketDAO.findById(rTwoArg.getData()).isWarranty());

        TechnicalRequest defaultWarranty = validCreateRequest();
        ServiceResult<Integer> r1 = service.createTicket(defaultWarranty, null, adminUser);
        assertTrue(r1.isSuccess());
        assertTrue(ticketDAO.findById(r1.getData()).isWarranty());

        TechnicalRequest falseWarranty = validCreateRequest();
        ServiceResult<Integer> r2 = service.createTicket(falseWarranty, Boolean.FALSE, adminUser);
        assertTrue(r2.isSuccess());
        assertFalse(ticketDAO.findById(r2.getData()).isWarranty());

        TechnicalRequest trueWarranty = validCreateRequest();
        ServiceResult<Integer> r3 = service.createTicket(trueWarranty, Boolean.TRUE, adminUser);
        assertTrue(r3.isSuccess());
        assertTrue(ticketDAO.findById(r3.getData()).isWarranty());
    }

    // ==================================================================
    // 5. BR-40 & UC-22: Chuyển trạng thái, MSG-051, resolvedAt & Giao dịch
    // ==================================================================

    @Test
    public void updateTicket_validForwardFlow_NewToInProgressToClosed_recordsHistoryAndStampsResolvedAtOnce()
            throws Exception {
        int id = service.createTicket(validCreateRequest(), salesManager).getData();

        // Bước 1: Mới tiếp nhận -> Đang xử lý (bởi Kỹ thuật được giao)
        TechnicalRequest step1 = copyOf(ticketDAO.findById(id));
        step1.setStatus(TicketService.STATUS_IN_PROGRESS);
        step1.setRootCause("Hỏng tụ nguồn bo mạch chính");
        step1.setCauseCategory("Do thiết bị");
        step1.setHandlingPlan("Thay tụ nguồn mới");

        ServiceResult<Void> r1 = service.updateTicket(step1, "Đã tiếp nhận và kiểm tra tại hiện trường", techAssigned);
        assertTrue(r1.isSuccess());

        TechnicalRequest afterStep1 = ticketDAO.findById(id);
        assertEquals(TicketService.STATUS_IN_PROGRESS, afterStep1.getStatus());
        assertNull(afterStep1.getResolvedAt());
        assertEquals("Hỏng tụ nguồn bo mạch chính", afterStep1.getRootCause());

        // Bước 2: Đang xử lý -> Đã đóng (lần đầu đóng -> ghi resolvedAt)
        TechnicalRequest step2 = copyOf(afterStep1);
        step2.setStatus(TicketService.STATUS_CLOSED);
        step2.setResolutionSummary("Đã thay tụ nguồn, chạy thử ổn định 24h");

        ServiceResult<Void> r2 = service.updateTicket(step2, "Hoàn tất bàn giao", techAssigned);
        assertTrue(r2.isSuccess());

        TechnicalRequest afterStep2 = ticketDAO.findById(id);
        assertEquals(TicketService.STATUS_CLOSED, afterStep2.getStatus());
        Timestamp firstResolvedAt = afterStep2.getResolvedAt();
        assertNotNull("First transition to Closed must stamp resolvedAt", firstResolvedAt);
        assertNotNull(step2.getResolvedAt());

        // Bước 3: Đã đóng -> Đã đóng (sửa bổ sung kết quả xử lý, giữ nguyên resolvedAt gốc)
        Timestamp fixedPastTime = Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS));
        afterStep2.setResolvedAt(fixedPastTime);
        ticketDAO.putTicket(afterStep2);

        TechnicalRequest step3 = copyOf(afterStep2);
        step3.setResolutionSummary("Đã thay tụ nguồn, nghiệm thu có biên bản");
        ServiceResult<Void> r3 = service.updateTicket(step3, "Bổ sung ghi chú biên bản", techAssigned);
        assertTrue(r3.isSuccess());

        TechnicalRequest afterStep3 = ticketDAO.findById(id);
        assertEquals(fixedPastTime, afterStep3.getResolvedAt());
        assertEquals("Đã thay tụ nguồn, nghiệm thu có biên bản", afterStep3.getResolutionSummary());

        // Kiểm tra lịch sử trạng thái: 1 dòng mở đầu + 2 dòng chuyển trạng thái (step3 giữ nguyên trạng thái nên không thêm dòng)
        List<TechnicalRequestHistory> history = service.findHistoryByTicketId(id);
        assertEquals(3, history.size());
    }

    @Test
    public void updateTicket_invalidStatusTransitions_returnMsg051() {
        int id = service.createTicket(validCreateRequest(), salesManager).getData();

        // 1. Nhảy cóc: Mới tiếp nhận -> Đã đóng
        TechnicalRequest jump = copyOf(ticketDAO.findById(id));
        jump.setStatus(TicketService.STATUS_CLOSED);
        ServiceResult<Void> rJump = service.updateTicket(jump, "Đóng tắt", adminUser);
        assertFalse(rJump.isSuccess());
        assertEquals(TicketValidator.MSG_051, rJump.getMessage());
        assertEquals(TicketValidator.MSG_051, rJump.getFieldErrors().get(TicketValidator.FIELD_STATUS));

        // Chuyển hợp lệ sang Đang xử lý
        TechnicalRequest toInProgress = copyOf(ticketDAO.findById(id));
        toInProgress.setStatus(TicketService.STATUS_IN_PROGRESS);
        assertTrue(service.updateTicket(toInProgress, null, adminUser).isSuccess());

        // 2. Đi lùi: Đang xử lý -> Mới tiếp nhận
        TechnicalRequest backToNew = copyOf(ticketDAO.findById(id));
        backToNew.setStatus(TicketService.STATUS_NEW);
        ServiceResult<Void> rBack = service.updateTicket(backToNew, "Lùi lại", adminUser);
        assertFalse(rBack.isSuccess());
        assertEquals(TicketValidator.MSG_051, rBack.getMessage());

        // Chuyển hợp lệ sang Đã đóng
        TechnicalRequest toClosed = copyOf(ticketDAO.findById(id));
        toClosed.setStatus(TicketService.STATUS_CLOSED);
        assertTrue(service.updateTicket(toClosed, null, adminUser).isSuccess());

        // 3. Mở lại phiếu đã đóng: Đã đóng -> Đang xử lý
        TechnicalRequest reopenProgress = copyOf(ticketDAO.findById(id));
        reopenProgress.setStatus(TicketService.STATUS_IN_PROGRESS);
        ServiceResult<Void> rReopen1 = service.updateTicket(reopenProgress, "Mở lại", adminUser);
        assertFalse(rReopen1.isSuccess());
        assertEquals(TicketValidator.MSG_051, rReopen1.getMessage());

        // 4. Mở lại phiếu đã đóng: Đã đóng -> Mới tiếp nhận
        TechnicalRequest reopenNew = copyOf(ticketDAO.findById(id));
        reopenNew.setStatus(TicketService.STATUS_NEW);
        ServiceResult<Void> rReopen2 = service.updateTicket(reopenNew, "Mở lại", adminUser);
        assertFalse(rReopen2.isSuccess());
        assertEquals(TicketValidator.MSG_051, rReopen2.getMessage());
    }

    @Test
    public void updateTicket_rejectsBlankOrUnknownStatus() {
        int id = service.createTicket(validCreateRequest(), salesManager).getData();

        TechnicalRequest blankStatus = copyOf(ticketDAO.findById(id));
        blankStatus.setStatus("   ");
        ServiceResult<Void> rBlank = service.updateTicket(blankStatus, null, adminUser);
        assertFalse(rBlank.isSuccess());
        assertEquals(TicketService.ERR_INVALID, rBlank.getMessage());
        assertEquals(TicketValidator.MSG_STATUS_REQUIRED, rBlank.getFieldErrors().get(TicketValidator.FIELD_STATUS));

        TechnicalRequest unknownStatus = copyOf(ticketDAO.findById(id));
        unknownStatus.setStatus("Tạm hoãn");
        ServiceResult<Void> rUnknown = service.updateTicket(unknownStatus, null, adminUser);
        assertFalse(rUnknown.isSuccess());
        assertEquals(TicketService.ERR_INVALID, rUnknown.getMessage());
        assertEquals(TicketValidator.MSG_STATUS_INVALID, rUnknown.getFieldErrors().get(TicketValidator.FIELD_STATUS));
    }

    @Test
    public void updateTicket_jdbcTransaction_rollsBackTicketUpdateWhenHistoryInsertFails() throws Exception {
        int id = service.createTicket(validCreateRequest(), salesManager).getData();
        TechnicalRequest existing = ticketDAO.findById(id);

        Connection conn = mock(Connection.class);
        PreparedStatement psLock = JdbcStub.statementReturning(
                JdbcStub.singleRow(JdbcStub.row("status", TicketService.STATUS_NEW)));
        PreparedStatement psUpdate = mock(PreparedStatement.class);
        when(psUpdate.executeUpdate()).thenReturn(1);
        PreparedStatement psHistory = mock(PreparedStatement.class);
        when(psHistory.executeUpdate()).thenThrow(new SQLException("Simulated history insert failure"));

        when(conn.prepareStatement(startsWith("SELECT status FROM technicalrequests"))).thenReturn(psLock);
        when(conn.prepareStatement(startsWith("UPDATE technicalrequests SET"))).thenReturn(psUpdate);
        when(conn.prepareStatement(startsWith("INSERT INTO technicalrequesthistory"))).thenReturn(psHistory);

        TechnicalSupportTicketDAO spyDao = spy(new TechnicalSupportTicketDAO());
        doReturn(existing).when(spyDao).findById(id);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            TicketService txService = new TicketService(spyDao, customerDAO, contractDAO, employeeDAO,
                    permissionService, ticketValidator);

            TechnicalRequest updateReq = copyOf(existing);
            updateReq.setStatus(TicketService.STATUS_IN_PROGRESS);

            ServiceResult<Void> res = txService.updateTicket(updateReq, "Nhận xử lý", techAssigned);

            assertFalse(res.isSuccess());
            assertEquals(TicketService.ERR_UPDATE_FAILED, res.getMessage());
            verify(conn).setAutoCommit(false);
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    // ==================================================================
    // 6. UC-21 & UC-22: Phân quyền (RBAC), Khóa trường Kỹ thuật & Quan hệ KH/HĐ
    // ==================================================================

    @Test
    public void createTicket_rbac_allowsAdminAndSalesButBlocksTechnicianAndInvalidUsers() {
        assertTrue(service.createTicket(validCreateRequest(), adminUser).isSuccess());
        assertTrue(service.createTicket(validCreateRequest(), salesManager).isSuccess());
        assertTrue(service.createTicket(validCreateRequest(), salesSubordinate).isSuccess());

        ServiceResult<Integer> rTech = service.createTicket(validCreateRequest(), techAssigned);
        assertFalse(rTech.isSuccess());
        assertEquals(PermissionService.MSG_022, rTech.getMessage());

        ServiceResult<Integer> rNull = service.createTicket(validCreateRequest(), null);
        assertFalse(rNull.isSuccess());
        assertEquals(PermissionService.MSG_022, rNull.getMessage());

        ServiceResult<Integer> rDeleted = service.createTicket(validCreateRequest(), deletedUser);
        assertFalse(rDeleted.isSuccess());
        assertEquals(PermissionService.MSG_022, rDeleted.getMessage());
    }

    @Test
    public void updateTicket_rbac_technicianCanOnlyUpdateAssignedTicketAndCannotModifyGeneralFields() {
        TechnicalRequest createReq = validCreateRequest();
        createReq.setPriority(TicketService.PRIORITY_NORMAL);
        createReq.setContractId(100);
        createReq.setWarranty(true);
        int id = service.createTicket(createReq, salesManager).getData();

        // Kỹ thuật viên KHÁC (không được giao phiếu) -> bị chặn với MSG-022
        TechnicalRequest otherAttempt = copyOf(ticketDAO.findById(id));
        otherAttempt.setStatus(TicketService.STATUS_IN_PROGRESS);
        ServiceResult<Void> rOther = service.updateTicket(otherAttempt, "Sửa hộ", techOther);
        assertFalse(rOther.isSuccess());
        assertEquals(PermissionService.MSG_022, rOther.getMessage());

        // Kỹ thuật viên ĐƯỢC GIAO cố tình sửa khách hàng, hợp đồng, mức ưu tiên, người xử lý, mô tả, bảo hành
        TechnicalRequest techAttempt = copyOf(ticketDAO.findById(id));
        techAttempt.setEnterpriseId(30);
        techAttempt.setContractId(200);
        techAttempt.setTicketType("Tư vấn");
        techAttempt.setPriority(TicketService.PRIORITY_URGENT);
        techAttempt.setReceptionChannel("Website");
        techAttempt.setAssignedTechnicianId(techOther.getUserId());
        techAttempt.setDescription("Mô tả bị sửa trái phép");
        techAttempt.setWarranty(false);
        // Các trường kỹ thuật được phép sửa:
        techAttempt.setStatus(TicketService.STATUS_IN_PROGRESS);
        techAttempt.setRootCause("Lỗi cảm biến áp suất");
        techAttempt.setCauseCategory("Do lắp đặt");
        techAttempt.setHandlingPlan("Hiệu chuẩn lại cảm biến");
        techAttempt.setResolutionSummary("Đang hiệu chuẩn");

        ServiceResult<Void> rAssigned = service.updateTicket(techAttempt, "Bắt đầu xử lý", techAssigned);
        assertTrue(rAssigned.isSuccess());

        TechnicalRequest stored = ticketDAO.findById(id);
        // Trường quản trị phải được giữ nguyên từ bản ghi gốc:
        assertEquals(10, stored.getEnterpriseId());
        assertEquals(Integer.valueOf(100), stored.getContractId());
        assertEquals("Bảo hành", stored.getTicketType());
        assertEquals(TicketService.PRIORITY_NORMAL, stored.getPriority());
        assertEquals("Điện thoại", stored.getReceptionChannel());
        assertEquals(techAssigned.getUserId(), stored.getAssignedTechnicianId());
        assertEquals("Hệ thống lọc bụi báo lỗi áp suất cao", stored.getDescription());
        assertTrue(stored.isWarranty());
        // Trường kỹ thuật được cập nhật:
        assertEquals(TicketService.STATUS_IN_PROGRESS, stored.getStatus());
        assertEquals("Lỗi cảm biến áp suất", stored.getRootCause());
        assertEquals("Do lắp đặt", stored.getCauseCategory());
        assertEquals("Hiệu chuẩn lại cảm biến", stored.getHandlingPlan());
        assertEquals("Đang hiệu chuẩn", stored.getResolutionSummary());
    }

    @Test
    public void createAndUpdateTicket_validateAssignedTechnicianMustBeActiveTechnician() {
        User deletedTech = buildUser(7, PermissionService.ROLE_TECHNICAL, null, true);
        employeeDAO.putUser(deletedTech);

        // 1. Tạo phiếu với userId không tồn tại (999), Sales, Admin, hoặc Kỹ thuật đã bị khóa -> ERR_INVALID
        for (int invalidTechId : new int[]{999, salesManager.getUserId(), adminUser.getUserId(), deletedTech.getUserId()}) {
            TechnicalRequest badTech = validCreateRequest();
            badTech.setAssignedTechnicianId(invalidTechId);
            ServiceResult<Integer> res = service.createTicket(badTech, adminUser);
            assertFalse("Should reject assignedTechnicianId=" + invalidTechId, res.isSuccess());
            assertEquals(TicketService.ERR_INVALID, res.getMessage());
            assertEquals(TicketValidator.MSG_TECHNICIAN_REQUIRED,
                    res.getFieldErrors().get(TicketValidator.FIELD_ASSIGNED_TECHNICIAN_ID));
        }

        // 2. Tạo phiếu hợp lệ rồi cập nhật đổi sang Sales hoặc user không tồn tại -> ERR_INVALID
        int id = service.createTicket(validCreateRequest(), adminUser).getData();

        TechnicalRequest updToSales = copyOf(ticketDAO.findById(id));
        updToSales.setAssignedTechnicianId(salesManager.getUserId());
        ServiceResult<Void> rUpdSales = service.updateTicket(updToSales, null, adminUser);
        assertFalse(rUpdSales.isSuccess());
        assertEquals(TicketService.ERR_INVALID, rUpdSales.getMessage());
        assertEquals(TicketValidator.MSG_TECHNICIAN_REQUIRED,
                rUpdSales.getFieldErrors().get(TicketValidator.FIELD_ASSIGNED_TECHNICIAN_ID));

        // 3. Giữ nguyên kỹ thuật viên cũ trên phiếu (kể cả khi người đó sau này đổi vai trò) -> vẫn cho lưu
        User roleChangedTech = buildUser(techAssigned.getUserId(), PermissionService.ROLE_SALES, null, false);
        employeeDAO.putUser(roleChangedTech);
        TechnicalRequest keepSameTech = copyOf(ticketDAO.findById(id));
        keepSameTech.setDescription("Cập nhật mô tả giữ nguyên người xử lý cũ");
        assertTrue(service.updateTicket(keepSameTech, null, adminUser).isSuccess());
        employeeDAO.putUser(techAssigned);
    }

    @Test
    public void updateTicket_technicianCanUpdateLegacyTicketWithOutOfCatalogGeneralFields() {
        TechnicalRequest legacy = validCreateRequest();
        legacy.setTicketId(50);
        legacy.setTicketCode("TK-0050");
        legacy.setEnterpriseId(20); // Nhà cung cấp (dữ liệu cũ)
        legacy.setPriority("Ưu tiên cũ ngoài danh mục");
        legacy.setStatus(TicketService.STATUS_NEW);
        legacy.setAssignedTechnicianId(techAssigned.getUserId());
        ticketDAO.putTicket(legacy);

        TechnicalRequest techUpdate = copyOf(legacy);
        techUpdate.setStatus(TicketService.STATUS_IN_PROGRESS);
        techUpdate.setRootCause("Đứt cáp tín hiệu");
        techUpdate.setCauseCategory("Do vận chuyển");
        ServiceResult<Void> res = service.updateTicket(techUpdate, "Tiếp nhận phiếu cũ", techAssigned);
        assertTrue(res.isSuccess());
        assertEquals(TicketService.STATUS_IN_PROGRESS, ticketDAO.findById(50).getStatus());

        // Kỹ thuật gửi nhóm nguyên nhân vượt quá 100 ký tự -> bị chặn
        TechnicalRequest badCause = copyOf(ticketDAO.findById(50));
        badCause.setCauseCategory("Z".repeat(TicketValidator.MAX_CAUSE_CATEGORY_LENGTH + 1));
        ServiceResult<Void> rBadCause = service.updateTicket(badCause, null, techAssigned);
        assertFalse(rBadCause.isSuccess());
        assertEquals(TicketService.ERR_INVALID, rBadCause.getMessage());
        assertTrue(rBadCause.getFieldErrors().containsKey(TicketValidator.FIELD_CAUSE_CATEGORY));
    }

    @Test
    public void createAndUpdateTicket_validateCustomerBuyerRoleAndContractOwnership() {
        // Tạo phiếu cho Nhà cung cấp (không có vai Khách mua) -> ERR_NOT_BUYER
        TechnicalRequest supplierTicket = validCreateRequest();
        supplierTicket.setEnterpriseId(20);
        ServiceResult<Integer> rSupplier = service.createTicket(supplierTicket, adminUser);
        assertFalse(rSupplier.isSuccess());
        assertEquals(TicketService.ERR_NOT_BUYER, rSupplier.getMessage());
        assertEquals(TicketValidator.MSG_NOT_BUYER,
                rSupplier.getFieldErrors().get(TicketValidator.FIELD_ENTERPRISE_ID));

        // Tạo phiếu gắn hợp đồng của khách hàng khác -> ERR_CONTRACT_MISMATCH
        TechnicalRequest wrongContract = validCreateRequest();
        wrongContract.setEnterpriseId(10);
        wrongContract.setContractId(200); // thuộc enterprise 30
        ServiceResult<Integer> rMismatch = service.createTicket(wrongContract, adminUser);
        assertFalse(rMismatch.isSuccess());
        assertEquals(TicketService.ERR_CONTRACT_MISMATCH, rMismatch.getMessage());
        assertEquals(TicketValidator.MSG_CONTRACT_MISMATCH,
                rMismatch.getFieldErrors().get(TicketValidator.FIELD_CONTRACT_ID));

        // Tạo phiếu gắn hợp đồng không tồn tại -> ERR_CONTRACT_MISMATCH
        TechnicalRequest ghostContract = validCreateRequest();
        ghostContract.setContractId(9999);
        ServiceResult<Integer> rGhost = service.createTicket(ghostContract, adminUser);
        assertFalse(rGhost.isSuccess());
        assertEquals(TicketService.ERR_CONTRACT_MISMATCH, rGhost.getMessage());
        assertEquals(TicketValidator.MSG_CONTRACT_MISMATCH,
                rGhost.getFieldErrors().get(TicketValidator.FIELD_CONTRACT_ID));

        // Tạo phiếu hợp lệ với hợp đồng đúng của khách hàng 10
        TechnicalRequest validWithContract = validCreateRequest();
        validWithContract.setContractId(100);
        int id = service.createTicket(validWithContract, adminUser).getData();

        // Cập nhật phiếu đổi sang khách hàng 20 (Nhà cung cấp) -> ERR_NOT_BUYER
        TechnicalRequest updSupplier = copyOf(ticketDAO.findById(id));
        updSupplier.setEnterpriseId(20);
        updSupplier.setContractId(null);
        ServiceResult<Void> rUpdSupplier = service.updateTicket(updSupplier, null, adminUser);
        assertFalse(rUpdSupplier.isSuccess());
        assertEquals(TicketService.ERR_NOT_BUYER, rUpdSupplier.getMessage());
        assertEquals(TicketValidator.MSG_NOT_BUYER,
                rUpdSupplier.getFieldErrors().get(TicketValidator.FIELD_ENTERPRISE_ID));

        // Giữ nguyên khách hàng cũ trên phiếu cũ (kể cả khi khách đó sau này mất vai Khách mua) -> vẫn cho lưu
        customerDAO.putRoles(10, List.of(PermissionService.ROLE_SUPPLIER));
        TechnicalRequest keepSameCustomer = copyOf(ticketDAO.findById(id));
        keepSameCustomer.setDescription("Cập nhật mô tả giữ nguyên khách cũ");
        assertTrue(service.updateTicket(keepSameCustomer, null, adminUser).isSuccess());
    }

    @Test
    public void updateTicket_preservesContractAndSlaWhenNotExplicitlyCleared() {
        Timestamp sla = Timestamp.from(Instant.now().plus(48, ChronoUnit.HOURS));
        TechnicalRequest createReq = validCreateRequest();
        createReq.setContractId(100);
        createReq.setSlaDeadline(sla);
        int id = service.createTicket(createReq, salesManager).getData();

        // contractPickerUsable = false và slaClearedOnPurpose = false -> giữ nguyên contractId & slaDeadline cũ
        TechnicalRequest updPreserve = copyOf(ticketDAO.findById(id));
        updPreserve.setContractId(null);
        updPreserve.setSlaDeadline(null);
        assertTrue(service.updateTicket(updPreserve, null, false, false, salesManager).isSuccess());

        TechnicalRequest afterPreserve = ticketDAO.findById(id);
        assertEquals(Integer.valueOf(100), afterPreserve.getContractId());
        assertEquals(sla, afterPreserve.getSlaDeadline());

        // contractPickerUsable = true và slaClearedOnPurpose = true -> xóa cả hai về null
        TechnicalRequest updClear = copyOf(afterPreserve);
        updClear.setContractId(null);
        updClear.setSlaDeadline(null);
        assertTrue(service.updateTicket(updClear, null, true, true, salesManager).isSuccess());

        TechnicalRequest afterClear = ticketDAO.findById(id);
        assertNull(afterClear.getContractId());
        assertNull(afterClear.getSlaDeadline());
    }

    @Test
    public void updateTicket_returnsNotFoundWhenTicketMissingOrInvalidId() {
        assertEquals(TicketService.ERR_NOT_FOUND,
                service.updateTicket(null, null, adminUser).getMessage());

        TechnicalRequest zeroId = validCreateRequest();
        zeroId.setTicketId(0);
        assertEquals(TicketService.ERR_NOT_FOUND,
                service.updateTicket(zeroId, null, adminUser).getMessage());

        TechnicalRequest missing = validCreateRequest();
        missing.setTicketId(999);
        assertEquals(TicketService.ERR_NOT_FOUND,
                service.updateTicket(missing, null, adminUser).getMessage());
    }

    // ==================================================================
    // 7. BR-42: Kiểm tra SLA quá hạn & sắp đến hạn (24 giờ)
    // ==================================================================

    @Test
    public void isSlaOverdueAndDueSoon_evaluateAccuratelyAcrossStatusesAndDeadlines() {
        Instant base = Instant.parse("2026-06-15T10:00:00Z");
        Timestamp asOf = Timestamp.from(base);

        TechnicalRequest ticket = validCreateRequest();
        ticket.setStatus(TicketService.STATUS_NEW);

        // 1. Không có slaDeadline hoặc null -> false
        assertFalse(service.isSlaOverdue(null, asOf));
        assertFalse(service.isSlaOverdue(ticket, asOf));
        assertFalse(service.isSlaDueSoon(null, asOf));
        assertFalse(service.isSlaDueSoon(ticket, asOf));

        // 2. Hạn SLA đã qua 1 giờ và phiếu đang mở -> quá hạn = true, sắp đến hạn = true
        ticket.setSlaDeadline(Timestamp.from(base.minus(1, ChronoUnit.HOURS)));
        assertTrue(service.isSlaOverdue(ticket, asOf));
        assertTrue(service.isSlaDueSoon(ticket, asOf));

        // 3. Hạn SLA còn 12 giờ (<= 24h) -> quá hạn = false, sắp đến hạn = true
        ticket.setSlaDeadline(Timestamp.from(base.plus(12, ChronoUnit.HOURS)));
        assertFalse(service.isSlaOverdue(ticket, asOf));
        assertTrue(service.isSlaDueSoon(ticket, asOf));

        // 4. Hạn SLA đúng bằng 24 giờ -> quá hạn = false, sắp đến hạn = true
        ticket.setSlaDeadline(Timestamp.from(base.plus(24, ChronoUnit.HOURS)));
        assertFalse(service.isSlaOverdue(ticket, asOf));
        assertTrue(service.isSlaDueSoon(ticket, asOf));

        // 5. Hạn SLA còn 25 giờ (> 24h) -> quá hạn = false, sắp đến hạn = false
        ticket.setSlaDeadline(Timestamp.from(base.plus(25, ChronoUnit.HOURS)));
        assertFalse(service.isSlaOverdue(ticket, asOf));
        assertFalse(service.isSlaDueSoon(ticket, asOf));

        // 6. Phiếu ĐÃ ĐÓNG dù hạn SLA ở quá khứ -> KHÔNG bị đánh dấu quá hạn hay sắp trễ hạn
        ticket.setSlaDeadline(Timestamp.from(base.minus(5, ChronoUnit.DAYS)));
        ticket.setStatus(TicketService.STATUS_CLOSED);
        assertFalse(service.isSlaOverdue(ticket, asOf));
        assertFalse(service.isSlaDueSoon(ticket, asOf));

        // 7. Tạo phiếu với SLA trống, SLA quá khứ, SLA tương lai xa và kiểm tra hàm 1 tham số
        TechnicalRequest noSla = validCreateRequest();
        noSla.setSlaDeadline(null);
        int idNoSla = service.createTicket(noSla, adminUser).getData();
        assertFalse(service.isSlaOverdue(ticketDAO.findById(idNoSla)));
        assertFalse(service.isSlaDueSoon(ticketDAO.findById(idNoSla)));

        TechnicalRequest pastSla = validCreateRequest();
        pastSla.setSlaDeadline(Timestamp.from(Instant.now().minus(2, ChronoUnit.HOURS)));
        int idPastSla = service.createTicket(pastSla, adminUser).getData();
        assertTrue(service.isSlaOverdue(ticketDAO.findById(idPastSla)));
        assertTrue(service.isSlaDueSoon(ticketDAO.findById(idPastSla)));

        TechnicalRequest farFutureSla = validCreateRequest();
        farFutureSla.setSlaDeadline(Timestamp.from(Instant.now().plus(72, ChronoUnit.HOURS)));
        int idFutureSla = service.createTicket(farFutureSla, adminUser).getData();
        assertFalse(service.isSlaOverdue(ticketDAO.findById(idFutureSla)));
        assertFalse(service.isSlaDueSoon(ticketDAO.findById(idFutureSla)));
    }

    // ==================================================================
    // 8. UC-23 & BR-47: Xóa mềm chỉ khi Mới tiếp nhận hoặc Đã đóng
    // ==================================================================

    @Test
    public void deleteTicket_allowsSoftDeleteWhenNewOrClosedForAdminAndSales() {
        // Xóa khi Mới tiếp nhận
        int idNew = service.createTicket(validCreateRequest(), salesManager).getData();
        ServiceResult<Void> rDelNew = service.deleteTicket(idNew, salesSubordinate);
        assertTrue(rDelNew.isSuccess());
        assertNull(ticketDAO.findById(idNew));

        // Xóa khi Đã đóng
        int idClosed = service.createTicket(validCreateRequest(), adminUser).getData();
        TechnicalRequest toInProg = copyOf(ticketDAO.findById(idClosed));
        toInProg.setStatus(TicketService.STATUS_IN_PROGRESS);
        assertTrue(service.updateTicket(toInProg, null, adminUser).isSuccess());

        TechnicalRequest toClosed = copyOf(ticketDAO.findById(idClosed));
        toClosed.setStatus(TicketService.STATUS_CLOSED);
        assertTrue(service.updateTicket(toClosed, null, adminUser).isSuccess());

        ServiceResult<Void> rDelClosed = service.deleteTicket(idClosed, adminUser);
        assertTrue(rDelClosed.isSuccess());
        assertNull(ticketDAO.findById(idClosed));
    }

    @Test
    public void deleteTicket_blocksWhenInProgressAndBlocksTechnicianRole() {
        int id = service.createTicket(validCreateRequest(), salesManager).getData();

        // Kỹ thuật viên không có quyền xóa (kể cả phiếu Mới tiếp nhận giao cho mình) -> MSG-022
        ServiceResult<Void> rTech = service.deleteTicket(id, techAssigned);
        assertFalse(rTech.isSuccess());
        assertEquals(PermissionService.MSG_022, rTech.getMessage());
        assertNotNull(ticketDAO.findById(id));

        // Chuyển sang Đang xử lý -> Admin/Sales cũng bị chặn xóa theo BR-47
        TechnicalRequest toInProg = copyOf(ticketDAO.findById(id));
        toInProg.setStatus(TicketService.STATUS_IN_PROGRESS);
        assertTrue(service.updateTicket(toInProg, null, techAssigned).isSuccess());

        ServiceResult<Void> rInProg = service.deleteTicket(id, adminUser);
        assertFalse(rInProg.isSuccess());
        assertEquals(TicketService.ERR_CANNOT_DELETE, rInProg.getMessage());
        assertNotNull(ticketDAO.findById(id));
    }

    @Test
    public void deleteTicket_returnsNotFoundOrDeleteFailedOnEdgeCases() {
        assertEquals(TicketService.ERR_NOT_FOUND, service.deleteTicket(0, adminUser).getMessage());
        assertEquals(TicketService.ERR_NOT_FOUND, service.deleteTicket(999, adminUser).getMessage());

        int id = service.createTicket(validCreateRequest(), adminUser).getData();
        ticketDAO.forceDeleteFailure = true;
        assertEquals(TicketService.ERR_DELETE_FAILED, service.deleteTicket(id, adminUser).getMessage());
    }

    // ==================================================================
    // 9. UC-16 & UC-17: Danh sách, Bộ lọc người xử lý & Xem chi tiết
    // ==================================================================

    @Test
    public void resolveAssigneeFilterAndId_defaultsMineForTechnicianAndAllForOthers() {
        assertEquals(TicketService.ASSIGNEE_MINE, service.resolveAssigneeFilter(null, techAssigned));
        assertEquals(Integer.valueOf(techAssigned.getUserId()),
                service.resolveAssigneeId(TicketService.ASSIGNEE_MINE, techAssigned));

        assertEquals(TicketService.ASSIGNEE_ALL, service.resolveAssigneeFilter(null, salesManager));
        assertEquals(TicketService.ASSIGNEE_ALL, service.resolveAssigneeFilter("  ", adminUser));
        assertNull(service.resolveAssigneeId(TicketService.ASSIGNEE_ALL, adminUser));

        // Kỹ thuật chọn tường minh "all"
        assertEquals(TicketService.ASSIGNEE_ALL, service.resolveAssigneeFilter("all", techAssigned));
        assertNull(service.resolveAssigneeId("all", techAssigned));

        // Lọc theo user_id cụ thể
        assertEquals("4", service.resolveAssigneeFilter("4", adminUser));
        assertEquals(Integer.valueOf(4), service.resolveAssigneeId("4", adminUser));

        // Giá trị lạ / âm -> về "all"
        assertEquals(TicketService.ASSIGNEE_ALL, service.resolveAssigneeFilter("-5", adminUser));
        assertEquals(TicketService.ASSIGNEE_ALL, service.resolveAssigneeFilter("abc", adminUser));
    }

    @Test
    public void getTicketDetail_returnsPermissionsAndHistoryAccordingToRoleAndStatus() {
        int id = service.createTicket(validCreateRequest(), salesManager).getData();

        // Kỹ thuật được giao xem chi tiết: canEdit = true, canDelete = false
        ServiceResult<TicketService.TicketDetail> rAssigned = service.getTicketDetail(id, techAssigned);
        assertTrue(rAssigned.isSuccess());
        assertTrue(rAssigned.getData().isCanEdit());
        assertFalse(rAssigned.getData().isCanDelete());
        assertEquals(1, rAssigned.getData().getHistory().size());

        // Kỹ thuật khác xem chi tiết: canEdit = false, canDelete = false
        ServiceResult<TicketService.TicketDetail> rOtherTech = service.getTicketDetail(id, techOther);
        assertTrue(rOtherTech.isSuccess());
        assertFalse(rOtherTech.getData().isCanEdit());
        assertFalse(rOtherTech.getData().isCanDelete());

        // Sales xem chi tiết khi Mới tiếp nhận: canEdit = true, canDelete = true
        ServiceResult<TicketService.TicketDetail> rSales = service.getTicketDetail(id, salesManager);
        assertTrue(rSales.isSuccess());
        assertTrue(rSales.getData().isCanEdit());
        assertTrue(rSales.getData().isCanDelete());

        // Không có quyền xem (user null/deleted) -> MSG-022
        assertEquals(PermissionService.MSG_022, service.getTicketDetail(id, null).getMessage());
        // Không tìm thấy phiếu -> ERR_NOT_FOUND
        assertEquals(TicketService.ERR_NOT_FOUND, service.getTicketDetail(999, adminUser).getMessage());
    }

    @Test
    public void listAndSummaryDelegates_workCorrectly() {
        service.createTicket(validCreateRequest(), salesManager);
        TechnicalRequest second = validCreateRequest();
        second.setPriority(TicketService.PRIORITY_URGENT);
        service.createTicket(second, salesManager);

        List<TechnicalRequest> list = service.findTickets(0, 0, null, null, null, null, null);
        assertEquals(2, list.size());
        assertEquals(2, service.countTickets(null, null, null, null, null));

        Map<String, Integer> summary = service.countListSummary(null, null);
        assertEquals(Integer.valueOf(2), summary.get(TicketService.STATUS_NEW));
        assertEquals(Integer.valueOf(1), summary.get(TicketService.PRIORITY_URGENT));

        assertEquals(2, service.findByEnterpriseId(10).size());
        assertTrue(service.findByEnterpriseId(0).isEmpty());
    }

    // ==================================================================
    // Helpers & In-Memory Fakes
    // ==================================================================

    private User buildUser(int userId, String roleName, Integer managerId, boolean deleted) {
        User u = new User();
        u.setUserId(userId);
        u.setManagerId(managerId);
        u.setDeleted(deleted);
        Role r = new Role();
        r.setRoleId(userId);
        r.setRoleName(roleName);
        u.setRole(r);
        return u;
    }

    private TechnicalRequest validCreateRequest() {
        TechnicalRequest t = new TechnicalRequest();
        t.setEnterpriseId(10);
        t.setTicketType("Bảo hành");
        t.setPriority(TicketService.PRIORITY_HIGH);
        t.setReceptionChannel("Điện thoại");
        t.setAssignedTechnicianId(techAssigned.getUserId());
        t.setDescription("Hệ thống lọc bụi báo lỗi áp suất cao");
        t.setWarranty(true);
        return t;
    }

    private static TechnicalRequest copyOf(TechnicalRequest src) {
        if (src == null) {
            return null;
        }
        TechnicalRequest copy = new TechnicalRequest();
        copy.setTicketId(src.getTicketId());
        copy.setTicketCode(src.getTicketCode());
        copy.setEnterpriseId(src.getEnterpriseId());
        copy.setContractId(src.getContractId());
        copy.setTicketType(src.getTicketType());
        copy.setPriority(src.getPriority());
        copy.setReceptionChannel(src.getReceptionChannel());
        copy.setSlaDeadline(src.getSlaDeadline());
        copy.setAssignedTechnicianId(src.getAssignedTechnicianId());
        copy.setCreatedBy(src.getCreatedBy());
        copy.setCreatedDate(src.getCreatedDate());
        copy.setDescription(src.getDescription());
        copy.setRootCause(src.getRootCause());
        copy.setCauseCategory(src.getCauseCategory());
        copy.setHandlingPlan(src.getHandlingPlan());
        copy.setWarranty(src.isWarranty());
        copy.setStatus(src.getStatus());
        copy.setResolutionSummary(src.getResolutionSummary());
        copy.setResolvedAt(src.getResolvedAt());
        copy.setDeleted(src.isDeleted());
        return copy;
    }

    private static class FakeEmployeeDAO extends EmployeeDAO {
        private final Map<Integer, User> users = new HashMap<>();

        void putUser(User u) {
            users.put(u.getUserId(), u);
        }

        @Override
        public User findById(int userId) {
            return users.get(userId);
        }
    }

    private static class FakeCustomerDAO extends CustomerDAO {
        private final Map<Integer, List<String>> rolesByEnterprise = new HashMap<>();

        void putRoles(int enterpriseId, List<String> roles) {
            rolesByEnterprise.put(enterpriseId, roles);
        }

        @Override
        public List<String> findRolesOf(int enterpriseId) {
            return rolesByEnterprise.getOrDefault(enterpriseId, Collections.emptyList());
        }
    }

    private static class FakeContractDAO extends ContractDAO {
        private final Map<Integer, Contract> contracts = new HashMap<>();

        void putContract(Contract c) {
            contracts.put(c.getContractId(), c);
        }

        @Override
        public Contract findById(int contractId) {
            return contracts.get(contractId);
        }
    }

    private static class FakeTicketDAO extends TechnicalSupportTicketDAO {
        private final Map<Integer, TechnicalRequest> store = new HashMap<>();
        private final Map<Integer, List<TechnicalRequestHistory>> historyByTicket = new HashMap<>();
        private int seq = 1;
        String nextCodeOverride;
        boolean forceInsertFailure;
        boolean forceDeleteFailure;

        void putTicket(TechnicalRequest t) {
            store.put(t.getTicketId(), copyOf(t));
            if (t.getTicketId() >= seq) {
                seq = t.getTicketId() + 1;
            }
        }

        @Override
        public String generateNextTicketCode() {
            if (nextCodeOverride != null) {
                return nextCodeOverride;
            }
            return String.format("TK-%04d", seq);
        }

        @Override
        public int insert(TechnicalRequest t) {
            if (forceInsertFailure) {
                return -1;
            }
            int id = seq++;
            TechnicalRequest copy = copyOf(t);
            copy.setTicketId(id);
            store.put(id, copy);
            recordHistory(id, "", copy.getStatus(), copy.getCreatedBy(), null);
            return id;
        }

        @Override
        public TechnicalRequest findById(int ticketId) {
            TechnicalRequest t = store.get(ticketId);
            return (t != null && !t.isDeleted()) ? copyOf(t) : null;
        }

        @Override
        public boolean update(TechnicalRequest t, int changedBy, String internalNote) {
            TechnicalRequest existing = store.get(t.getTicketId());
            if (existing == null || existing.isDeleted()) {
                return false;
            }
            String prevStatus = existing.getStatus();
            store.put(t.getTicketId(), copyOf(t));
            if (!prevStatus.equals(t.getStatus())) {
                recordHistory(t.getTicketId(), prevStatus, t.getStatus(), changedBy, internalNote);
            }
            return true;
        }

        @Override
        public boolean canDelete(int ticketId) {
            TechnicalRequest t = store.get(ticketId);
            return t != null && !t.isDeleted() && !STATUS_IN_PROGRESS.equals(t.getStatus());
        }

        @Override
        public boolean softDelete(int ticketId) {
            if (forceDeleteFailure) {
                return false;
            }
            TechnicalRequest t = store.get(ticketId);
            if (t == null || t.isDeleted()) {
                return false;
            }
            t.setDeleted(true);
            return true;
        }

        @Override
        public List<TechnicalRequestHistory> findHistoryByTicketId(int ticketId) {
            return historyByTicket.getOrDefault(ticketId, Collections.emptyList());
        }

        @Override
        public List<TechnicalRequest> findAll(int page, int pageSize, String keyword,
                String statusFilter, String priorityFilter, Period period, Integer assigneeId) {
            List<TechnicalRequest> res = new ArrayList<>();
            for (TechnicalRequest t : store.values()) {
                if (!t.isDeleted()) {
                    res.add(copyOf(t));
                }
            }
            return res;
        }

        @Override
        public int countAll(String keyword, String statusFilter, String priorityFilter,
                Period period, Integer assigneeId) {
            int count = 0;
            for (TechnicalRequest t : store.values()) {
                if (!t.isDeleted()) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Map<String, Integer> countListSummary(Period period, Integer assigneeId) {
            Map<String, Integer> summary = new HashMap<>();
            summary.put(STATUS_NEW, 0);
            summary.put(STATUS_IN_PROGRESS, 0);
            summary.put(STATUS_CLOSED, 0);
            summary.put(PRIORITY_URGENT, 0);
            for (TechnicalRequest t : store.values()) {
                if (!t.isDeleted()) {
                    summary.computeIfPresent(t.getStatus(), (k, v) -> v + 1);
                    if (PRIORITY_URGENT.equals(t.getPriority())) {
                        summary.put(PRIORITY_URGENT, summary.get(PRIORITY_URGENT) + 1);
                    }
                }
            }
            return summary;
        }

        @Override
        public List<TechnicalRequest> findByEnterpriseId(int enterpriseId) {
            List<TechnicalRequest> res = new ArrayList<>();
            for (TechnicalRequest t : store.values()) {
                if (!t.isDeleted() && t.getEnterpriseId() == enterpriseId) {
                    res.add(copyOf(t));
                }
            }
            return res;
        }

        private void recordHistory(int ticketId, String fromStatus, String toStatus,
                int changedBy, String internalNote) {
            TechnicalRequestHistory h = new TechnicalRequestHistory();
            h.setTicketId(ticketId);
            h.setFromStatus(fromStatus);
            h.setToStatus(toStatus);
            h.setChangedBy(changedBy);
            h.setInternalNote(internalNote);
            historyByTicket.computeIfAbsent(ticketId, k -> new ArrayList<>()).add(h);
        }
    }
}
