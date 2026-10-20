package poscs.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.ContractDAO;
import poscs.dao.ContractPaymentDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.ContractPayment;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit test toàn diện cho ContractPaymentService (Task 4.11).
 * Đáp ứng: UC-53, BR-18 và các quy tắc phân quyền, kiểm tra biên, xử lý lỗi.
 */
public class ContractPaymentServiceTest {

    private static final String PROGRESS_DRAFT = poscs.validator.ContractValidator.PROGRESS_DRAFT;
    private static final String PROGRESS_SIGNED = poscs.validator.ContractValidator.PROGRESS_SIGNED;
    private static final String PROGRESS_LIQUIDATED = poscs.validator.ContractValidator.PROGRESS_LIQUIDATED;
    private static final String PROGRESS_TERMINATED = poscs.validator.ContractValidator.PROGRESS_TERMINATED;

    private ContractPaymentDAO paymentDAO;
    private ContractDAO contractDAO;
    private EmployeeDAO employeeDAO;
    private PermissionService permissionService;
    private ContractPaymentService service;

    private User adminUser;
    private User salesManager;
    private User salesSubordinate;
    private User techUser;
    private User deletedUser;

    @Before
    public void setUp() {
        paymentDAO = mock(ContractPaymentDAO.class);
        contractDAO = mock(ContractDAO.class);
        employeeDAO = mock(EmployeeDAO.class);

        adminUser = buildUser(1, PermissionService.ROLE_ADMIN, null, false);
        salesManager = buildUser(2, PermissionService.ROLE_SALES, null, false);
        salesSubordinate = buildUser(3, PermissionService.ROLE_SALES, 2, false);
        techUser = buildUser(4, PermissionService.ROLE_TECHNICAL, null, false);
        deletedUser = buildUser(5, PermissionService.ROLE_SALES, null, true);

        when(employeeDAO.findById(1)).thenReturn(adminUser);
        when(employeeDAO.findById(2)).thenReturn(salesManager);
        when(employeeDAO.findById(3)).thenReturn(salesSubordinate);
        when(employeeDAO.findById(4)).thenReturn(techUser);
        when(employeeDAO.findById(5)).thenReturn(deletedUser);

        permissionService = new PermissionService(employeeDAO);
        service = new ContractPaymentService(paymentDAO, contractDAO, permissionService);
    }

    private User buildUser(int id, String roleName, Integer managerId, boolean deleted) {
        User u = new User();
        u.setUserId(id);
        u.setUsername("user" + id);
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

    private ContractPayment samplePayment(BigDecimal amount, Date dueDate, Date paidDate) {
        return samplePayment(0, amount, dueDate, paidDate);
    }

    private ContractPayment samplePayment(int paymentId, BigDecimal amount, Date dueDate, Date paidDate) {
        ContractPayment p = new ContractPayment();
        p.setPaymentId(paymentId);
        p.setInvoiceAmount(amount);
        p.setDueDate(dueDate);
        p.setPaidDate(paidDate);
        return p;
    }

    // ==================================================================
    // UC-53: addPayment - Phân quyền
    // ==================================================================

    @Test
    public void testAddPayment_UnauthorizedActors_FailForbidden() {
        ContractPayment payment = samplePayment(new BigDecimal("10000000"), Date.valueOf("2026-11-01"), null);

        // Null actor
        ServiceResult<ContractPayment> r1 = service.addPayment(10, payment, null);
        assertFalse(r1.isSuccess());
        assertEquals(ContractPaymentService.ERR_FORBIDDEN, r1.getMessage());

        // Deleted actor
        ServiceResult<ContractPayment> r2 = service.addPayment(10, payment, deletedUser);
        assertFalse(r2.isSuccess());
        assertEquals(ContractPaymentService.ERR_FORBIDDEN, r2.getMessage());

        // Sales subordinate
        ServiceResult<ContractPayment> r3 = service.addPayment(10, payment, salesSubordinate);
        assertFalse(r3.isSuccess());
        assertEquals(ContractPaymentService.ERR_FORBIDDEN, r3.getMessage());

        // Technical user
        ServiceResult<ContractPayment> r4 = service.addPayment(10, payment, techUser);
        assertFalse(r4.isSuccess());
        assertEquals(ContractPaymentService.ERR_FORBIDDEN, r4.getMessage());
    }

    // ==================================================================
    // UC-53: addPayment - Hợp đồng & Đóng băng
    // ==================================================================

    @Test
    public void testAddPayment_ContractNotFoundOrDeleted() {
        ContractPayment payment = samplePayment(new BigDecimal("10000000"), Date.valueOf("2026-11-01"), null);

        // contractId <= 0
        ServiceResult<ContractPayment> r1 = service.addPayment(0, payment, adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractPaymentService.ERR_NOT_FOUND, r1.getMessage());

        // Not found in DB
        when(contractDAO.findById(999)).thenReturn(null);
        ServiceResult<ContractPayment> r2 = service.addPayment(999, payment, adminUser);
        assertFalse(r2.isSuccess());
        assertEquals(ContractPaymentService.ERR_NOT_FOUND, r2.getMessage());

        // Soft deleted
        Contract deleted = sampleContract(100, PROGRESS_SIGNED);
        deleted.setDeleted(true);
        when(contractDAO.findById(100)).thenReturn(deleted);
        ServiceResult<ContractPayment> r3 = service.addPayment(100, payment, adminUser);
        assertFalse(r3.isSuccess());
        assertEquals(ContractPaymentService.ERR_NOT_FOUND, r3.getMessage());
    }

    @Test
    public void testAddPayment_FrozenContract_Fails() {
        ContractPayment payment = samplePayment(new BigDecimal("10000000"), Date.valueOf("2026-11-01"), null);

        Contract liquidated = sampleContract(10, PROGRESS_LIQUIDATED);
        when(contractDAO.findById(10)).thenReturn(liquidated);

        ServiceResult<ContractPayment> r1 = service.addPayment(10, payment, adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractPaymentService.ERR_PAYMENT_FROZEN, r1.getMessage());

        Contract terminated = sampleContract(11, PROGRESS_TERMINATED);
        when(contractDAO.findById(11)).thenReturn(terminated);

        ServiceResult<ContractPayment> r2 = service.addPayment(11, payment, salesManager);
        assertFalse(r2.isSuccess());
        assertEquals(ContractPaymentService.ERR_PAYMENT_FROZEN, r2.getMessage());
    }

    // ==================================================================
    // UC-53: addPayment - Kiểm tra dữ liệu kỳ thanh toán
    // ==================================================================

    @Test
    public void testAddPayment_ValidationErrors() {
        Contract contract = sampleContract(10, PROGRESS_SIGNED);
        when(contractDAO.findById(10)).thenReturn(contract);

        // Null payment object
        ServiceResult<ContractPayment> r1 = service.addPayment(10, null, adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractPaymentService.ERR_INVALID, r1.getMessage());

        // Null amount
        ContractPayment pNullAmount = samplePayment(null, Date.valueOf("2026-11-01"), null);
        ServiceResult<ContractPayment> r2 = service.addPayment(10, pNullAmount, adminUser);
        assertFalse(r2.isSuccess());
        assertEquals(ContractPaymentService.ERR_INVALID_AMOUNT, r2.getMessage());
        assertTrue(r2.getFieldErrors().containsKey(ContractPaymentService.FIELD_INVOICE_AMOUNT));

        // Zero amount
        ContractPayment pZero = samplePayment(BigDecimal.ZERO, Date.valueOf("2026-11-01"), null);
        ServiceResult<ContractPayment> r3 = service.addPayment(10, pZero, adminUser);
        assertFalse(r3.isSuccess());
        assertEquals(ContractPaymentService.ERR_INVALID_AMOUNT, r3.getMessage());

        // Negative amount
        ContractPayment pNegative = samplePayment(new BigDecimal("-5000"), Date.valueOf("2026-11-01"), null);
        ServiceResult<ContractPayment> r4 = service.addPayment(10, pNegative, adminUser);
        assertFalse(r4.isSuccess());
        assertEquals(ContractPaymentService.ERR_INVALID_AMOUNT, r4.getMessage());

        // Amount exceeding max decimal(15,2)
        BigDecimal excessive = ContractPaymentService.MAX_PAYMENT_AMOUNT.add(BigDecimal.ONE);
        ContractPayment pExcess = samplePayment(excessive, Date.valueOf("2026-11-01"), null);
        ServiceResult<ContractPayment> r5 = service.addPayment(10, pExcess, adminUser);
        assertFalse(r5.isSuccess());
        assertEquals(ContractPaymentService.ERR_INVALID_AMOUNT, r5.getMessage());

        // Missing due date
        ContractPayment pNoDueDate = samplePayment(new BigDecimal("10000000"), null, null);
        ServiceResult<ContractPayment> r6 = service.addPayment(10, pNoDueDate, adminUser);
        assertFalse(r6.isSuccess());
        assertEquals(ContractPaymentService.ERR_MISSING_DUE_DATE, r6.getMessage());
        assertTrue(r6.getFieldErrors().containsKey(ContractPaymentService.FIELD_DUE_DATE));
    }

    @Test
    public void testAddPayment_DaoResults() {
        Contract contract = sampleContract(10, PROGRESS_SIGNED);
        when(contractDAO.findById(10)).thenReturn(contract);

        ContractPayment payment = samplePayment(new BigDecimal("50000000"), Date.valueOf("2026-12-01"), null);

        // DAO returns false
        when(paymentDAO.insertPayment(10, payment, adminUser.getUserId())).thenReturn(false);
        ServiceResult<ContractPayment> r1 = service.addPayment(10, payment, adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractPaymentService.ERR_PAYMENT_FAILED, r1.getMessage());

        // DAO returns true
        when(paymentDAO.insertPayment(10, payment, salesManager.getUserId())).thenReturn(true);
        ServiceResult<ContractPayment> r2 = service.addPayment(10, payment, salesManager);
        assertTrue(r2.isSuccess());
        assertEquals(payment, r2.getData());
    }

    // ==================================================================
    // UC-53: markPaymentPaid - Ghi nhận đã thu
    // ==================================================================

    @Test
    public void testMarkPaymentPaid_Permissions() {
        // Unauthorized
        ServiceResult<Void> r1 = service.markPaymentPaid(10, 1, null, salesSubordinate);
        assertFalse(r1.isSuccess());
        assertEquals(ContractPaymentService.ERR_FORBIDDEN, r1.getMessage());

        // Admin / Sales manager -> check passes
        Contract contract = sampleContract(10, PROGRESS_SIGNED);
        when(contractDAO.findById(10)).thenReturn(contract);
        when(paymentDAO.markPaymentPaid(eq(1), eq(10), any(Date.class), eq(adminUser.getUserId()))).thenReturn(true);

        ServiceResult<Void> r2 = service.markPaymentPaid(10, 1, null, adminUser);
        assertTrue(r2.isSuccess());
    }

    @Test
    public void testMarkPaymentPaid_InvalidIdsOrContractNotFound() {
        // contractId <= 0
        ServiceResult<Void> r1 = service.markPaymentPaid(0, 1, null, adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractPaymentService.ERR_INVALID, r1.getMessage());

        // paymentId <= 0
        ServiceResult<Void> r2 = service.markPaymentPaid(10, 0, null, adminUser);
        assertFalse(r2.isSuccess());
        assertEquals(ContractPaymentService.ERR_INVALID, r2.getMessage());

        // Contract not found
        when(contractDAO.findById(999)).thenReturn(null);
        ServiceResult<Void> r3 = service.markPaymentPaid(999, 1, null, adminUser);
        assertFalse(r3.isSuccess());
        assertEquals(ContractPaymentService.ERR_NOT_FOUND, r3.getMessage());
    }

    @Test
    public void testMarkPaymentPaid_PaidDateDefaultAndSpecified() {
        Contract contract = sampleContract(10, PROGRESS_LIQUIDATED); // Lưu ý: đã thanh lý vẫn cho thu tiền bảo lãnh
        when(contractDAO.findById(10)).thenReturn(contract);

        // PaidDate = null -> mặc định hôm nay
        Date today = Date.valueOf(LocalDate.now());
        when(paymentDAO.markPaymentPaid(eq(1), eq(10), eq(today), eq(salesManager.getUserId()))).thenReturn(true);
        ServiceResult<Void> r1 = service.markPaymentPaid(10, 1, null, salesManager);
        assertTrue(r1.isSuccess());
        verify(paymentDAO).markPaymentPaid(1, 10, today, salesManager.getUserId());

        // PaidDate cụ thể
        Date customDate = Date.valueOf("2026-10-15");
        when(paymentDAO.markPaymentPaid(eq(2), eq(10), eq(customDate), eq(salesManager.getUserId()))).thenReturn(true);
        ServiceResult<Void> r2 = service.markPaymentPaid(10, 2, customDate, salesManager);
        assertTrue(r2.isSuccess());
        verify(paymentDAO).markPaymentPaid(2, 10, customDate, salesManager.getUserId());

        // DAO thất bại
        when(paymentDAO.markPaymentPaid(eq(3), eq(10), eq(customDate), eq(salesManager.getUserId()))).thenReturn(false);
        ServiceResult<Void> r3 = service.markPaymentPaid(10, 3, customDate, salesManager);
        assertFalse(r3.isSuccess());
        assertEquals(ContractPaymentService.ERR_PAYMENT_FAILED, r3.getMessage());
    }

    // ==================================================================
    // UC-53: deletePayment - Xóa kỳ thanh toán lập nhầm
    // ==================================================================

    @Test
    public void testDeletePayment_Permissions() {
        ServiceResult<Void> r1 = service.deletePayment(10, 1, techUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractPaymentService.ERR_FORBIDDEN, r1.getMessage());
    }

    @Test
    public void testDeletePayment_InvalidIdsOrNotFound() {
        // contractId <= 0
        ServiceResult<Void> r1 = service.deletePayment(0, 1, adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractPaymentService.ERR_INVALID, r1.getMessage());

        // paymentId <= 0
        ServiceResult<Void> r2 = service.deletePayment(10, 0, adminUser);
        assertFalse(r2.isSuccess());
        assertEquals(ContractPaymentService.ERR_INVALID, r2.getMessage());

        // contract not found
        when(contractDAO.findById(999)).thenReturn(null);
        ServiceResult<Void> r3 = service.deletePayment(999, 1, adminUser);
        assertFalse(r3.isSuccess());
        assertEquals(ContractPaymentService.ERR_NOT_FOUND, r3.getMessage());
    }

    @Test
    public void testDeletePayment_FrozenContract_Fails() {
        Contract liquidated = sampleContract(10, PROGRESS_LIQUIDATED);
        when(contractDAO.findById(10)).thenReturn(liquidated);

        ServiceResult<Void> r1 = service.deletePayment(10, 1, adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractPaymentService.ERR_PAYMENT_FROZEN, r1.getMessage());

        Contract terminated = sampleContract(11, PROGRESS_TERMINATED);
        when(contractDAO.findById(11)).thenReturn(terminated);

        ServiceResult<Void> r2 = service.deletePayment(11, 1, salesManager);
        assertFalse(r2.isSuccess());
        assertEquals(ContractPaymentService.ERR_PAYMENT_FROZEN, r2.getMessage());
    }

    @Test
    public void testDeletePayment_DaoResults() {
        Contract contract = sampleContract(10, PROGRESS_SIGNED);
        when(contractDAO.findById(10)).thenReturn(contract);

        ContractPayment p1 = samplePayment(1, new BigDecimal("10000000"), Date.valueOf("2026-11-01"), null);
        ContractPayment p2 = samplePayment(2, new BigDecimal("20000000"), Date.valueOf("2026-12-01"), null);
        when(paymentDAO.findPaymentsByContractId(10)).thenReturn(List.of(p1, p2));

        // DAO returns false
        when(paymentDAO.deletePayment(1, 10, adminUser.getUserId())).thenReturn(false);
        ServiceResult<Void> r1 = service.deletePayment(10, 1, adminUser);
        assertFalse(r1.isSuccess());
        assertEquals(ContractPaymentService.ERR_PAYMENT_FAILED, r1.getMessage());

        // DAO returns true
        when(paymentDAO.deletePayment(2, 10, adminUser.getUserId())).thenReturn(true);
        ServiceResult<Void> r2 = service.deletePayment(10, 2, adminUser);
        assertTrue(r2.isSuccess());
        verify(paymentDAO).deletePayment(2, 10, adminUser.getUserId());
    }

    @Test
    public void testDeletePayment_QAFeedback_PaymentAlreadyPaid_Fails() {
        Contract contract = sampleContract(10, PROGRESS_SIGNED);
        when(contractDAO.findById(10)).thenReturn(contract);

        // Kỳ thanh toán đã được ghi nhận thu tiền (paid_date != null)
        ContractPayment paidPayment = samplePayment(1, new BigDecimal("50000000"),
                Date.valueOf("2026-10-01"), Date.valueOf("2026-10-15"));
        when(paymentDAO.findPaymentsByContractId(10)).thenReturn(List.of(paidPayment));

        ServiceResult<Void> r = service.deletePayment(10, 1, adminUser);
        assertFalse(r.isSuccess());
        assertEquals(ContractPaymentService.ERR_PAYMENT_ALREADY_PAID, r.getMessage());
        verify(paymentDAO, never()).deletePayment(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void testDeletePayment_QAFeedback_PaymentNotFoundInContract_FailsNotFound() {
        Contract contract = sampleContract(10, PROGRESS_SIGNED);
        when(contractDAO.findById(10)).thenReturn(contract);

        // Hợp đồng 10 chỉ có kỳ thanh toán 1, caller đòi xóa kỳ 999
        ContractPayment p1 = samplePayment(1, new BigDecimal("10000000"), Date.valueOf("2026-11-01"), null);
        when(paymentDAO.findPaymentsByContractId(10)).thenReturn(List.of(p1));

        ServiceResult<Void> r = service.deletePayment(10, 999, adminUser);
        assertFalse(r.isSuccess());
        assertEquals(ContractPaymentService.ERR_NOT_FOUND, r.getMessage());
    }

    // ==================================================================
    // Tra cứu & Định dạng BR-18
    // ==================================================================

    @Test
    public void testFindPaymentsByContractId() {
        assertEquals(Collections.emptyList(), service.findPaymentsByContractId(0));
        assertEquals(Collections.emptyList(), service.findPaymentsByContractId(-1));

        ContractPayment p = new ContractPayment();
        p.setPaymentId(1);
        when(paymentDAO.findPaymentsByContractId(10)).thenReturn(List.of(p));

        List<ContractPayment> list = service.findPaymentsByContractId(10);
        assertEquals(1, list.size());
        assertEquals(1, list.get(0).getPaymentId());
    }

    @Test
    public void testSumScheduledPaymentsForCluster() {
        assertEquals(BigDecimal.ZERO, service.sumScheduledPaymentsForCluster(0));
        assertEquals(BigDecimal.ZERO, service.sumScheduledPaymentsForCluster(-2));

        BigDecimal expected = new BigDecimal("150000000.00");
        when(paymentDAO.sumScheduledPaymentsForCluster(10)).thenReturn(expected);

        assertEquals(expected, service.sumScheduledPaymentsForCluster(10));
    }

    @Test
    public void testFormatMoney_BR18() {
        assertEquals("", ContractPaymentService.formatMoney(null));
        assertEquals("0 đ", ContractPaymentService.formatMoney(BigDecimal.ZERO));
        assertEquals("1.500.000.000 đ", ContractPaymentService.formatMoney(new BigDecimal("1500000000")));
        assertEquals("250.000 đ", ContractPaymentService.formatMoney(new BigDecimal("250000")));
    }

    @Test
    public void testFormatDate() {
        assertEquals("", ContractPaymentService.formatDate(null));
        assertEquals("20/10/2026", ContractPaymentService.formatDate(Date.valueOf("2026-10-20")));
    }
}
