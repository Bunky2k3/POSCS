package poscs.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.DBContext;
import poscs.dao.EmployeeDAO;
import poscs.dao.JdbcStub;
import poscs.dao.ProductDAO;
import poscs.model.Contract;
import poscs.model.ContractHistory;
import poscs.model.ContractProduct;
import poscs.model.Product;
import poscs.model.Role;
import poscs.model.User;
import poscs.validator.ContractValidator;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho ContractService (Task 4.10).
 * Bao phủ kịch bản đúng và vi phạm của UC-33, UC-34, UC-35, UC-39, UC-44, UC-46, UC-54
 * và BR-17, BR-35, BR-36, BR-37.
 */
public class ContractServiceTest {

    private FakeContractDAO contractDAO;
    private FakeCustomerDAO customerDAO;
    private FakeProductDAO productDAO;
    private FakeEmployeeDAO employeeDAO;
    private PermissionService permissionService;
    private ContractValidator contractValidator;
    private ContractService service;

    private User adminUser;
    private User salesManager;
    private User salesSubordinate;
    private User techUser;
    private User deletedUser;

    @Before
    public void setUp() {
        contractDAO = new FakeContractDAO();
        customerDAO = new FakeCustomerDAO();
        productDAO = new FakeProductDAO();
        employeeDAO = new FakeEmployeeDAO();

        adminUser = buildUser(1, PermissionService.ROLE_ADMIN, null, false);
        salesManager = buildUser(2, PermissionService.ROLE_SALES, null, false);
        salesSubordinate = buildUser(3, PermissionService.ROLE_SALES, 2, false);
        techUser = buildUser(4, PermissionService.ROLE_TECHNICAL, null, false);
        deletedUser = buildUser(5, PermissionService.ROLE_SALES, null, true);

        employeeDAO.putUser(adminUser);
        employeeDAO.putUser(salesManager);
        employeeDAO.putUser(salesSubordinate);
        employeeDAO.putUser(techUser);
        employeeDAO.putUser(deletedUser);

        customerDAO.putRoles(10, List.of(ContractService.ROLE_BUYER));
        customerDAO.putRoles(20, List.of(ContractService.ROLE_SUPPLIER));
        customerDAO.putRoles(30, List.of(ContractService.ROLE_BUYER, ContractService.ROLE_SUPPLIER));

        Product p1 = new Product();
        p1.setProductId(100);
        p1.setProductCode("SP-0100");
        p1.setProductName("Máy lọc bụi tĩnh điện ESP-500");
        productDAO.putProduct(p1);

        permissionService = new PermissionService(employeeDAO);
        contractValidator = new ContractValidator();
        service = new ContractService(contractDAO, customerDAO, productDAO, permissionService, contractValidator);
    }

    // ==================================================================
    // 1. BR-17 & Helpers: Tự tính trạng thái trục lịch và hàm tiện ích
    // ==================================================================

    @Test
    public void computeStatus_delegatesCorrectlyForAllCalendarStatesAndDateOverloads() {
        LocalDate today = LocalDate.of(2026, 6, 15);
        assertEquals(ContractService.STATUS_DRAFT,
                ContractService.computeStatus(null, LocalDate.of(2026, 12, 31), today));
        assertEquals(ContractService.STATUS_DRAFT,
                ContractService.computeStatus(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), today));
        assertEquals(ContractService.STATUS_ACTIVE,
                ContractService.computeStatus(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 1), today));
        assertEquals(ContractService.STATUS_SOON,
                ContractService.computeStatus(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 15), today));
        assertEquals(ContractService.STATUS_EXPIRED,
                ContractService.computeStatus(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 14), today));

        // Overload LocalDate (2 tham số) và java.sql.Date (2 tham số)
        LocalDate now = LocalDate.now();
        assertEquals(ContractService.STATUS_DRAFT,
                ContractService.computeStatus(now.plusDays(5), now.plusDays(60)));
        assertEquals(ContractService.STATUS_ACTIVE,
                ContractService.computeStatus(Date.valueOf(now.minusDays(5)), Date.valueOf(now.plusDays(60))));
        assertEquals(ContractService.STATUS_SOON,
                ContractService.computeStatus(Date.valueOf(now.minusDays(5)), Date.valueOf(now.plusDays(15))));
        assertEquals(ContractService.STATUS_EXPIRED,
                ContractService.computeStatus(Date.valueOf(now.minusDays(30)), Date.valueOf(now.minusDays(1))));
        assertEquals(ContractService.STATUS_DRAFT,
                ContractService.computeStatus((Date) null, Date.valueOf(now.plusDays(10))));
    }

    @Test
    public void helpers_parseAndDirectionMappingsWorkCorrectly() {
        assertEquals(ContractService.DIRECTION_BUY, ContractService.directionFromKind("buy"));
        assertEquals(ContractService.DIRECTION_SELL, ContractService.directionFromKind("sell"));
        assertEquals(ContractService.KIND_BUY, ContractService.kindFromDirection("Mua"));
        assertEquals(ContractService.KIND_SELL, ContractService.kindFromDirection("Bán"));
        assertEquals(ContractService.ROLE_BUYER, ContractService.counterpartyRoleFor("Bán"));
        assertEquals(ContractService.ROLE_SUPPLIER, ContractService.counterpartyRoleFor("Mua"));
        assertFalse(ContractService.contractTypesFor("Bán").isEmpty());
        assertFalse(ContractService.contractTypesFor("Mua").isEmpty());

        assertTrue(service.counterpartyMatchesDirection(10, "Bán"));
        assertFalse(service.counterpartyMatchesDirection(10, "Mua"));
        assertFalse(service.counterpartyMatchesDirection(0, "Bán"));

        assertEquals(new BigDecimal("1500000"), ContractService.parseContractValue("1,500,000", false));
        assertNull(ContractService.parseContractValue("-500", false));
        assertEquals(new BigDecimal("-500"), ContractService.parseContractValue("-500", true));
        assertNull(ContractService.parseContractValue("abc", true));

        assertEquals(Integer.valueOf(5), ContractService.parseQuantityOrNull(" 5 "));
        assertNull(ContractService.parseQuantityOrNull("0"));
        assertNull(ContractService.parseQuantityOrNull("-3"));
        assertNull(ContractService.parseQuantityOrNull("xyz"));
    }

    // ==================================================================
    // 2. UC-33 (BR-35, BR-36): Tạo hợp đồng gốc
    // ==================================================================

    @Test
    public void createContract_validSellContractBySalesManager_succeedsAtDraft() {
        Contract c = validDraftSellContract("  HD-2026-001  ");
        c.setTitle("  Hợp đồng lọc bụi ESP  ");
        ServiceResult<Integer> res = service.createContract(c, salesManager);

        assertTrue(res.isSuccess());
        assertEquals(Integer.valueOf(1), res.getData());
        assertEquals("HD-2026-001", c.getContractCode());
        assertEquals("Hợp đồng lọc bụi ESP", c.getTitle());
        assertEquals(ContractService.PROGRESS_DRAFT, c.getProgressStatus());
        assertNull(c.getParentContractId());
    }

    @Test
    public void createContract_validBuyContractByAdmin_succeeds() {
        Contract c = validDraftBuyContract("HDM-2026-001");
        ServiceResult<Integer> res = service.createContract(c, adminUser);

        assertTrue(res.isSuccess());
        assertEquals(Integer.valueOf(1), res.getData());
    }

    @Test
    public void createContract_unauthorizedUsers_failWithMsg022() {
        Contract c = validDraftSellContract("HD-2026-002");

        ServiceResult<Integer> subRes = service.createContract(c, salesSubordinate);
        assertFalse(subRes.isSuccess());
        assertEquals(PermissionService.MSG_022, subRes.getMessage());

        ServiceResult<Integer> techRes = service.createContract(c, techUser);
        assertFalse(techRes.isSuccess());
        assertEquals(PermissionService.MSG_022, techRes.getMessage());

        ServiceResult<Integer> nullRes = service.createContract(c, null);
        assertFalse(nullRes.isSuccess());
        assertEquals(PermissionService.MSG_022, nullRes.getMessage());
    }

    @Test
    public void createContract_nullOrBlankCodeOrMissingRequiredFields_failsValidation() {
        ServiceResult<Integer> nullRes = service.createContract(null, adminUser);
        assertFalse(nullRes.isSuccess());
        assertEquals(ContractService.ERR_INVALID, nullRes.getMessage());
        assertEquals(ContractValidator.MSG_CONTRACT_INVALID,
                nullRes.getFieldErrors().get(ContractValidator.FIELD_CONTRACT_CODE));

        // Mã hợp đồng chỉ chứa khoảng trắng
        Contract blankCode = validDraftSellContract("    ");
        ServiceResult<Integer> blankRes = service.createContract(blankCode, adminUser);
        assertFalse(blankRes.isSuccess());
        assertEquals(ContractService.ERR_INVALID, blankRes.getMessage());
        assertEquals(ContractValidator.MSG_CODE_REQUIRED,
                blankRes.getFieldErrors().get(ContractValidator.FIELD_CONTRACT_CODE));

        Contract empty = new Contract();
        ServiceResult<Integer> res = service.createContract(empty, adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractService.ERR_INVALID, res.getMessage());
        assertTrue(res.getFieldErrors().containsKey(ContractValidator.FIELD_CONTRACT_CODE));
        assertTrue(res.getFieldErrors().containsKey(ContractValidator.FIELD_TITLE));
        assertTrue(res.getFieldErrors().containsKey(ContractValidator.FIELD_CONTRACT_TYPE));
        assertTrue(res.getFieldErrors().containsKey(ContractValidator.FIELD_ENTERPRISE_ID));
        assertTrue(res.getFieldErrors().containsKey(ContractValidator.FIELD_OWNER_ID));
        assertTrue(res.getFieldErrors().containsKey(ContractValidator.FIELD_DIRECTION));
    }

    @Test
    public void createContract_nonExistentOrDeletedOwner_failsValidationBr35() {
        Contract nonExistentOwner = validDraftSellContract("HD-OWNER-999");
        nonExistentOwner.setOwnerId(999);
        ServiceResult<Integer> res1 = service.createContract(nonExistentOwner, adminUser);
        assertFalse(res1.isSuccess());
        assertEquals(ContractService.ERR_INVALID, res1.getMessage());
        assertEquals(ContractValidator.MSG_OWNER_REQUIRED,
                res1.getFieldErrors().get(ContractValidator.FIELD_OWNER_ID));

        Contract deletedOwner = validDraftSellContract("HD-OWNER-DEL");
        deletedOwner.setOwnerId(deletedUser.getUserId());
        ServiceResult<Integer> res2 = service.createContract(deletedOwner, adminUser);
        assertFalse(res2.isSuccess());
        assertEquals(ContractService.ERR_INVALID, res2.getMessage());
        assertEquals(ContractValidator.MSG_OWNER_REQUIRED,
                res2.getFieldErrors().get(ContractValidator.FIELD_OWNER_ID));
    }

    @Test
    public void createContract_overMaxLengthAndNegativeRootValue_failsValidation() {
        Contract c = validDraftSellContract("A".repeat(51));
        c.setTitle("T".repeat(256));
        c.setContractValue(new BigDecimal("-1"));

        ServiceResult<Integer> res = service.createContract(c, adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractService.ERR_INVALID, res.getMessage());
        assertTrue(res.getFieldErrors().containsKey(ContractValidator.FIELD_CONTRACT_CODE));
        assertTrue(res.getFieldErrors().containsKey(ContractValidator.FIELD_TITLE));
        assertTrue(res.getFieldErrors().containsKey(ContractValidator.FIELD_CONTRACT_VALUE));
    }

    @Test
    public void createContract_counterpartyRoleMismatch_failsBr35() {
        Contract sellWithSupplier = validDraftSellContract("HD-SELL-ERR");
        sellWithSupplier.setEnterpriseId(20);
        ServiceResult<Integer> res1 = service.createContract(sellWithSupplier, adminUser);
        assertFalse(res1.isSuccess());
        assertEquals(ContractService.ERR_INVALID, res1.getMessage());
        assertEquals(ContractValidator.MSG_COUNTERPARTY_ROLE_MISMATCH,
                res1.getFieldErrors().get(ContractValidator.FIELD_ENTERPRISE_ID));

        Contract buyWithBuyer = validDraftBuyContract("HD-BUY-ERR");
        buyWithBuyer.setEnterpriseId(10);
        ServiceResult<Integer> res2 = service.createContract(buyWithBuyer, adminUser);
        assertFalse(res2.isSuccess());
        assertEquals(ContractService.ERR_INVALID, res2.getMessage());
        assertEquals(ContractValidator.MSG_COUNTERPARTY_ROLE_MISMATCH,
                res2.getFieldErrors().get(ContractValidator.FIELD_ENTERPRISE_ID));
    }

    @Test
    public void createContract_manualSigningDateOrInvalidTermDates_failsBr35AndBr36() {
        Contract withSigningDate = validDraftSellContract("HD-SIGN-ERR");
        withSigningDate.setSigningDate(Date.valueOf(LocalDate.now()));
        ServiceResult<Integer> res1 = service.createContract(withSigningDate, adminUser);
        assertFalse(res1.isSuccess());
        assertEquals(ContractValidator.MSG_SIGNING_DATE_SYSTEM_GENERATED,
                res1.getFieldErrors().get(ContractValidator.FIELD_SIGNING_DATE));

        Contract onlyEffective = validDraftSellContract("HD-TERM-1");
        onlyEffective.setEffectiveDate(Date.valueOf(LocalDate.now().plusDays(1)));
        onlyEffective.setEndDate(null);
        ServiceResult<Integer> res2 = service.createContract(onlyEffective, adminUser);
        assertFalse(res2.isSuccess());
        assertEquals(ContractValidator.MSG_045, res2.getFieldErrors().get(ContractValidator.FIELD_END_DATE));

        Contract reversedDates = validDraftSellContract("HD-TERM-2");
        reversedDates.setEffectiveDate(Date.valueOf(LocalDate.now().plusDays(30)));
        reversedDates.setEndDate(Date.valueOf(LocalDate.now().plusDays(10)));
        ServiceResult<Integer> res3 = service.createContract(reversedDates, adminUser);
        assertFalse(res3.isSuccess());
        assertEquals(ContractValidator.MSG_045, res3.getFieldErrors().get(ContractValidator.FIELD_EFFECTIVE_DATE));
    }

    @Test
    public void createContract_duplicateCode_fails() {
        Contract first = validDraftSellContract("HD-DUP-001");
        assertTrue(service.createContract(first, adminUser).isSuccess());

        Contract second = validDraftSellContract("HD-DUP-001");
        ServiceResult<Integer> res = service.createContract(second, adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractService.ERR_DUPLICATE_CODE, res.getMessage());
        assertTrue(res.getFieldErrors().containsKey(ContractValidator.FIELD_CONTRACT_CODE));
    }

    // ==================================================================
    // 3. UC-46 (BR-35, BR-36): Tạo phụ lục hợp đồng
    // ==================================================================

    @Test
    public void createAmendment_validReductionWithinRemainingValue_succeedsAndInheritsParentFields() {
        Contract parent = signedParentContract(10, "HD-PARENT-01", new BigDecimal("100000000"));
        contractDAO.putContract(parent);

        Contract amendment = validDraftSellContract("PL-01");
        amendment.setEnterpriseId(999);
        amendment.setDirection("Mua");
        amendment.setContractValue(new BigDecimal("-30000000"));

        ServiceResult<Integer> res = service.createAmendment(parent.getContractId(), amendment, salesManager);
        assertTrue(res.isSuccess());
        assertEquals(Integer.valueOf(parent.getContractId()), amendment.getParentContractId());
        assertEquals(parent.getEnterpriseId(), amendment.getEnterpriseId());
        assertEquals(parent.getDirection(), amendment.getDirection());
        assertEquals(ContractService.PROGRESS_DRAFT, amendment.getProgressStatus());
    }

    @Test
    public void createAmendment_unauthorizedOrMissingParentOrInvalidOwner_fails() {
        Contract parent = signedParentContract(10, "HD-PARENT-AUTH", new BigDecimal("100000000"));
        contractDAO.putContract(parent);

        // Không có quyền -> MSG-022
        ServiceResult<Integer> subRes = service.createAmendment(10, validDraftSellContract("PL-SUB"), salesSubordinate);
        assertFalse(subRes.isSuccess());
        assertEquals(PermissionService.MSG_022, subRes.getMessage());

        ServiceResult<Integer> techRes = service.createAmendment(10, validDraftSellContract("PL-TECH"), techUser);
        assertFalse(techRes.isSuccess());
        assertEquals(PermissionService.MSG_022, techRes.getMessage());

        // parentContractId <= 0 hoặc không tồn tại -> ERR_NOT_FOUND
        ServiceResult<Integer> zeroParent = service.createAmendment(0, validDraftSellContract("PL-0"), adminUser);
        assertFalse(zeroParent.isSuccess());
        assertEquals(ContractService.ERR_NOT_FOUND, zeroParent.getMessage());

        ServiceResult<Integer> missingParent = service.createAmendment(9999, validDraftSellContract("PL-999"), adminUser);
        assertFalse(missingParent.isSuccess());
        assertEquals(ContractService.ERR_NOT_FOUND, missingParent.getMessage());

        // Amendment null -> ERR_INVALID
        ServiceResult<Integer> nullAmend = service.createAmendment(10, null, adminUser);
        assertFalse(nullAmend.isSuccess());
        assertEquals(ContractService.ERR_INVALID, nullAmend.getMessage());

        // Người phụ trách không tồn tại -> ERR_INVALID + FIELD_OWNER_ID
        Contract badOwnerAmend = validDraftSellContract("PL-BAD-OWNER");
        badOwnerAmend.setOwnerId(999);
        ServiceResult<Integer> ownerRes = service.createAmendment(10, badOwnerAmend, adminUser);
        assertFalse(ownerRes.isSuccess());
        assertEquals(ContractService.ERR_INVALID, ownerRes.getMessage());
        assertTrue(ownerRes.getFieldErrors().containsKey(ContractValidator.FIELD_OWNER_ID));
    }

    @Test
    public void createAmendment_parentNotSignedOrAlreadyAmendment_fails() {
        Contract draftParent = validDraftSellContract("HD-DRAFT-P");
        draftParent.setContractId(11);
        draftParent.setProgressStatus(ContractService.PROGRESS_DRAFT);
        contractDAO.putContract(draftParent);

        ServiceResult<Integer> resDraft = service.createAmendment(11, validDraftSellContract("PL-D"), adminUser);
        assertFalse(resDraft.isSuccess());
        assertEquals(ContractService.ERR_AMENDMENT_NOT_ALLOWED, resDraft.getMessage());

        Contract liquidatedParent = signedParentContract(12, "HD-LIQ-P", new BigDecimal("50000000"));
        liquidatedParent.setProgressStatus(ContractService.PROGRESS_LIQUIDATED);
        contractDAO.putContract(liquidatedParent);

        ServiceResult<Integer> resLiq = service.createAmendment(12, validDraftSellContract("PL-L"), adminUser);
        assertFalse(resLiq.isSuccess());
        assertEquals(ContractService.ERR_AMENDMENT_NOT_ALLOWED, resLiq.getMessage());

        Contract nestedParent = signedParentContract(13, "PL-PARENT", new BigDecimal("20000000"));
        nestedParent.setParentContractId(10);
        contractDAO.putContract(nestedParent);

        ServiceResult<Integer> resNested = service.createAmendment(13, validDraftSellContract("PL-N"), adminUser);
        assertFalse(resNested.isSuccess());
        assertEquals(ContractService.ERR_AMENDMENT_NOT_ALLOWED, resNested.getMessage());
    }

    @Test
    public void createAmendment_reductionExceedsRemainingValue_failsWithValueNegative() {
        Contract parent = signedParentContract(10, "HD-PARENT-02", new BigDecimal("100000000"));
        parent.setAmendmentValueSigned(new BigDecimal("-40000000"));
        contractDAO.putContract(parent);

        Contract amendment = validDraftSellContract("PL-OVER");
        amendment.setContractValue(new BigDecimal("-60000001"));

        ServiceResult<Integer> res = service.createAmendment(10, amendment, adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractService.ERR_VALUE_NEGATIVE, res.getMessage());
        assertEquals(ContractValidator.MSG_EXCEEDS_REMAINING_VALUE,
                res.getFieldErrors().get(ContractValidator.FIELD_CONTRACT_VALUE));
    }

    // ==================================================================
    // 4. UC-35 (BR-35, BR-36): Cập nhật hợp đồng theo tiến độ
    // ==================================================================

    @Test
    public void updateContract_draftContract_allowsEditingAllFieldsAndChecksDuplicateAndOwner() {
        Contract existing = validDraftSellContract("HD-DRAFT-01");
        existing.setContractId(20);
        existing.setProgressStatus(ContractService.PROGRESS_DRAFT);
        contractDAO.putContract(existing);

        Contract other = validDraftSellContract("HD-OTHER-22");
        other.setContractId(22);
        other.setProgressStatus(ContractService.PROGRESS_DRAFT);
        contractDAO.putContract(other);

        // Đổi trùng mã với HĐ khác -> ERR_DUPLICATE_CODE
        Contract dupCode = validDraftSellContract("HD-OTHER-22");
        dupCode.setContractId(20);
        ServiceResult<Void> dupRes = service.updateContract(dupCode, salesManager);
        assertFalse(dupRes.isSuccess());
        assertEquals(ContractService.ERR_DUPLICATE_CODE, dupRes.getMessage());
        assertTrue(dupRes.getFieldErrors().containsKey(ContractValidator.FIELD_CONTRACT_CODE));

        // Người phụ trách không tồn tại -> ERR_INVALID + FIELD_OWNER_ID
        Contract badOwner = validDraftSellContract("HD-DRAFT-01");
        badOwner.setContractId(20);
        badOwner.setOwnerId(999);
        ServiceResult<Void> ownerRes = service.updateContract(badOwner, salesManager);
        assertFalse(ownerRes.isSuccess());
        assertEquals(ContractService.ERR_INVALID, ownerRes.getMessage());
        assertTrue(ownerRes.getFieldErrors().containsKey(ContractValidator.FIELD_OWNER_ID));

        // Cập nhật hợp lệ
        Contract edited = validDraftSellContract("HD-DRAFT-01-NEW");
        edited.setContractId(20);
        edited.setTitle("  Tiêu đề đã cập nhật  ");
        edited.setEffectiveDate(Date.valueOf(LocalDate.now().plusDays(10)));
        edited.setEndDate(Date.valueOf(LocalDate.now().plusMonths(8)));
        edited.setContractValue(new BigDecimal("250000000"));

        ServiceResult<Void> res = service.updateContract(edited, salesManager);
        assertTrue(res.isSuccess());
        Contract saved = contractDAO.findById(20);
        assertEquals("HD-DRAFT-01-NEW", saved.getContractCode());
        assertEquals("Tiêu đề đã cập nhật", saved.getTitle());
    }

    @Test
    public void updateContract_draftAmendmentReductionExceedsParent_fails() {
        Contract parent = signedParentContract(10, "HD-ROOT", new BigDecimal("50000000"));
        contractDAO.putContract(parent);

        Contract draftAmend = validDraftSellContract("PL-DRAFT");
        draftAmend.setContractId(21);
        draftAmend.setParentContractId(10);
        draftAmend.setProgressStatus(ContractService.PROGRESS_DRAFT);
        draftAmend.setContractValue(new BigDecimal("-10000000"));
        contractDAO.putContract(draftAmend);

        Contract edited = validDraftSellContract("PL-DRAFT");
        edited.setContractId(21);
        edited.setContractValue(new BigDecimal("-60000000"));

        ServiceResult<Void> res = service.updateContract(edited, adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractService.ERR_VALUE_NEGATIVE, res.getMessage());
        assertTrue(res.getFieldErrors().containsKey(ContractValidator.FIELD_CONTRACT_VALUE));
    }

    @Test
    public void updateContract_signedContract_allowsOnlyOwnerChangeAndBlocksOtherFields() {
        Contract signed = signedParentContract(30, "HD-SIGNED-30", new BigDecimal("100000000"));
        contractDAO.putContract(signed);

        // Không có quyền -> MSG-022
        ServiceResult<Void> unauthRes = service.updateContractOwner(30, 3, salesSubordinate);
        assertFalse(unauthRes.isSuccess());
        assertEquals(PermissionService.MSG_022, unauthRes.getMessage());

        // newOwnerId <= 0 hoặc không tồn tại -> ERR_INVALID + FIELD_OWNER_ID
        ServiceResult<Void> zeroOwner = service.updateContractOwner(30, 0, salesManager);
        assertFalse(zeroOwner.isSuccess());
        assertEquals(ContractService.ERR_INVALID, zeroOwner.getMessage());
        assertTrue(zeroOwner.getFieldErrors().containsKey(ContractValidator.FIELD_OWNER_ID));

        ServiceResult<Void> missingOwner = service.updateContractOwner(30, 999, salesManager);
        assertFalse(missingOwner.isSuccess());
        assertEquals(ContractService.ERR_INVALID, missingOwner.getMessage());
        assertTrue(missingOwner.getFieldErrors().containsKey(ContractValidator.FIELD_OWNER_ID));

        // HĐ không tồn tại -> ERR_NOT_FOUND
        ServiceResult<Void> notFoundRes = service.updateContractOwner(9999, 3, salesManager);
        assertFalse(notFoundRes.isSuccess());
        assertEquals(ContractService.ERR_NOT_FOUND, notFoundRes.getMessage());

        // Đổi người phụ trách hợp lệ qua updateContractOwner -> thành công
        ServiceResult<Void> ownerRes = service.updateContractOwner(30, 3, salesManager);
        assertTrue(ownerRes.isSuccess());
        assertEquals(3, contractDAO.findById(30).getOwnerId());

        // Cố tình sửa tiêu đề trên HĐ đã ký -> bị chặn
        Contract illegalEdit = signedParentContract(30, "HD-SIGNED-30", new BigDecimal("100000000"));
        illegalEdit.setOwnerId(3);
        illegalEdit.setTitle("Sửa tiêu đề trái phép");

        ServiceResult<Void> failRes = service.updateContract(illegalEdit, adminUser);
        assertFalse(failRes.isSuccess());
        assertEquals(ContractService.ERR_INVALID, failRes.getMessage());
    }

    @Test
    public void updateContract_liquidatedOrTerminatedContract_isBlockedCompletely() {
        Contract liquidated = signedParentContract(31, "HD-LIQ-31", new BigDecimal("100000000"));
        liquidated.setProgressStatus(ContractService.PROGRESS_LIQUIDATED);
        contractDAO.putContract(liquidated);

        ServiceResult<Void> res1 = service.updateContract(liquidated, adminUser);
        assertFalse(res1.isSuccess());
        assertEquals(ContractService.ERR_UPDATE_FAILED, res1.getMessage());

        ServiceResult<Void> res2 = service.updateContractOwner(31, 3, adminUser);
        assertFalse(res2.isSuccess());
        assertEquals(ContractService.ERR_UPDATE_FAILED, res2.getMessage());
    }

    // ==================================================================
    // 5. UC-44 (BR-35, BR-36): Ký hợp đồng & chuyển tiến độ
    // ==================================================================

    @Test
    public void signContract_validDraftWithTermDates_succeedsAndStampsTodaySigningDate() {
        Contract draft = validDraftSellContract("HD-SIGN-01");
        draft.setContractId(40);
        draft.setProgressStatus(ContractService.PROGRESS_DRAFT);
        draft.setEffectiveDate(Date.valueOf(LocalDate.now().plusDays(1)));
        draft.setEndDate(Date.valueOf(LocalDate.now().plusMonths(6)));
        contractDAO.putContract(draft);

        // Cấp dưới không được ký -> MSG-022
        ServiceResult<Void> subRes = service.signContract(40, salesSubordinate, "Ký HĐ");
        assertFalse(subRes.isSuccess());
        assertEquals(PermissionService.MSG_022, subRes.getMessage());

        // HĐ không tồn tại -> ERR_NOT_FOUND
        assertEquals(ContractService.ERR_NOT_FOUND,
                service.signContract(9999, salesManager, "Ký").getMessage());

        // Ghi chú vượt quá MAX_REASON_LENGTH -> ERR_INVALID
        ServiceResult<Void> longNote = service.signContract(
                40, salesManager, "N".repeat(ContractService.MAX_REASON_LENGTH + 1));
        assertFalse(longNote.isSuccess());
        assertEquals(ContractService.ERR_INVALID, longNote.getMessage());
        assertTrue(longNote.getFieldErrors().containsKey(ContractService.FIELD_NOTE));

        // Sales quản lý vùng ký thành công, hệ thống tự đóng dấu ngày ký = hôm nay
        ServiceResult<Void> mgrRes = service.signContract(40, salesManager, "Đã ký chính thức");
        assertTrue(mgrRes.isSuccess());
        Contract after = contractDAO.findById(40);
        assertEquals(ContractService.PROGRESS_SIGNED, after.getProgressStatus());
        assertEquals(Date.valueOf(LocalDate.now()), after.getSigningDate());
    }

    @Test
    public void signContract_missingTermOrEffectiveBeforeTodayOrReversedTermOrAmendmentExceeds_failsBr36() {
        // Thiếu thời hạn
        Contract noDates = validDraftSellContract("HD-NODATE");
        noDates.setContractId(41);
        noDates.setProgressStatus(ContractService.PROGRESS_DRAFT);
        noDates.setEffectiveDate(null);
        noDates.setEndDate(null);
        contractDAO.putContract(noDates);

        ServiceResult<Void> res1 = service.signContract(41, adminUser, null);
        assertFalse(res1.isSuccess());
        assertEquals(ContractService.ERR_MISSING_TERM, res1.getMessage());
        assertEquals(ContractValidator.MSG_045,
                res1.getFieldErrors().get(ContractValidator.FIELD_EFFECTIVE_DATE));

        // Ngày hiệu lực < Ngày ký (hôm nay) -> vi phạm BR-36
        Contract pastEffective = validDraftSellContract("HD-PAST-EFF");
        pastEffective.setContractId(44);
        pastEffective.setProgressStatus(ContractService.PROGRESS_DRAFT);
        pastEffective.setEffectiveDate(Date.valueOf(LocalDate.now().minusDays(1)));
        pastEffective.setEndDate(Date.valueOf(LocalDate.now().plusMonths(3)));
        contractDAO.putContract(pastEffective);

        ServiceResult<Void> pastRes = service.signContract(44, adminUser, null);
        assertFalse(pastRes.isSuccess());
        assertEquals(ContractService.ERR_MISSING_TERM, pastRes.getMessage());
        assertEquals(ContractValidator.MSG_SIGNING_AFTER_EFFECTIVE,
                pastRes.getFieldErrors().get(ContractValidator.FIELD_SIGNING_DATE));

        // Ngày hiệu lực > Ngày kết thúc -> vi phạm BR-36 (MSG-045)
        Contract reversed = validDraftSellContract("HD-REV-EFF");
        reversed.setContractId(45);
        reversed.setProgressStatus(ContractService.PROGRESS_DRAFT);
        reversed.setEffectiveDate(Date.valueOf(LocalDate.now().plusDays(20)));
        reversed.setEndDate(Date.valueOf(LocalDate.now().plusDays(5)));
        contractDAO.putContract(reversed);

        ServiceResult<Void> revRes = service.signContract(45, adminUser, null);
        assertFalse(revRes.isSuccess());
        assertEquals(ContractService.ERR_MISSING_TERM, revRes.getMessage());
        assertEquals(ContractValidator.MSG_045,
                revRes.getFieldErrors().get(ContractValidator.FIELD_EFFECTIVE_DATE));

        // Phụ lục nháp giảm trừ vượt giá trị còn lại của HĐ gốc lúc bấm ký -> ERR_VALUE_NEGATIVE
        Contract parent = signedParentContract(46, "HD-PARENT-46", new BigDecimal("50000000"));
        contractDAO.putContract(parent);

        Contract draftAmend = validDraftSellContract("PL-47");
        draftAmend.setContractId(47);
        draftAmend.setParentContractId(46);
        draftAmend.setProgressStatus(ContractService.PROGRESS_DRAFT);
        draftAmend.setContractValue(new BigDecimal("-60000000"));
        contractDAO.putContract(draftAmend);

        ServiceResult<Void> amendRes = service.signContract(47, adminUser, null);
        assertFalse(amendRes.isSuccess());
        assertEquals(ContractService.ERR_VALUE_NEGATIVE, amendRes.getMessage());
        assertTrue(amendRes.getFieldErrors().containsKey(ContractValidator.FIELD_CONTRACT_VALUE));

        // HĐ đã ký không thể ký lại -> ERR_PROGRESS_FAILED
        Contract alreadySigned = signedParentContract(42, "HD-ALREADY", new BigDecimal("10000000"));
        contractDAO.putContract(alreadySigned);

        ServiceResult<Void> res2 = service.signContract(42, adminUser, null);
        assertFalse(res2.isSuccess());
        assertEquals(ContractService.ERR_PROGRESS_FAILED, res2.getMessage());
    }

    @Test
    public void changeProgressStatus_liquidateAndTerminate_requireReasonSignedStateAndValidStatus() {
        Contract signed = signedParentContract(43, "HD-PROG-43", new BigDecimal("50000000"));
        contractDAO.putContract(signed);

        Contract draft = validDraftSellContract("HD-PROG-DRAFT");
        draft.setContractId(48);
        draft.setProgressStatus(ContractService.PROGRESS_DRAFT);
        contractDAO.putContract(draft);

        // Không có quyền -> MSG-022
        ServiceResult<Void> unauth = service.changeProgressStatus(
                43, ContractService.PROGRESS_LIQUIDATED, salesSubordinate, "Lý do");
        assertFalse(unauth.isSuccess());
        assertEquals(PermissionService.MSG_022, unauth.getMessage());

        // Bản Nháp chuyển thẳng sang Thanh lý -> bị chặn
        ServiceResult<Void> draftToLiq = service.changeProgressStatus(
                48, ContractService.PROGRESS_LIQUIDATED, salesManager, "Thanh lý nháp");
        assertFalse(draftToLiq.isSuccess());
        assertEquals(ContractService.ERR_PROGRESS_FAILED, draftToLiq.getMessage());

        // toStatus null/rỗng hoặc trạng thái lạ -> ERR_PROGRESS_FAILED
        assertEquals(ContractService.ERR_PROGRESS_FAILED,
                service.changeProgressStatus(43, "  ", salesManager, "Lý do").getMessage());
        assertEquals(ContractService.ERR_PROGRESS_FAILED,
                service.changeProgressStatus(43, "Trạng thái lạ", salesManager, "Lý do").getMessage());

        // Thiếu lý do khi thanh lý -> ERR_MISSING_REASON
        ServiceResult<Void> noReason = service.changeProgressStatus(
                43, ContractService.PROGRESS_LIQUIDATED, salesManager, "   ");
        assertFalse(noReason.isSuccess());
        assertEquals(ContractService.ERR_MISSING_REASON, noReason.getMessage());

        // Lý do quá dài -> ERR_INVALID + FIELD_NOTE
        ServiceResult<Void> longReason = service.changeProgressStatus(
                43, ContractService.PROGRESS_LIQUIDATED, salesManager,
                "R".repeat(ContractService.MAX_REASON_LENGTH + 1));
        assertFalse(longReason.isSuccess());
        assertEquals(ContractService.ERR_INVALID, longReason.getMessage());
        assertTrue(longReason.getFieldErrors().containsKey(ContractService.FIELD_NOTE));

        // Có lý do hợp lệ -> thành công và ghi lịch sử
        ServiceResult<Void> okRes = service.changeProgressStatus(
                43, ContractService.PROGRESS_LIQUIDATED, salesManager, "Hoàn tất nghiệm thu");
        assertTrue(okRes.isSuccess());
        assertEquals(ContractService.PROGRESS_LIQUIDATED, contractDAO.findById(43).getProgressStatus());
        assertFalse(service.findHistoryByContractId(43).isEmpty());
    }

    // ==================================================================
    // 6. UC-34 (BR-37): Huỷ bản ghi hợp đồng
    // ==================================================================

    @Test
    public void voidContract_draftContract_allowedForSalesManagerAndAdminWithReasonAndSoftDeletes() {
        Contract draft = validDraftSellContract("HD-VOID-DRAFT");
        draft.setContractId(50);
        draft.setProgressStatus(ContractService.PROGRESS_DRAFT);
        contractDAO.putContract(draft);

        // HĐ không tồn tại -> ERR_NOT_FOUND
        assertEquals(ContractService.ERR_NOT_FOUND,
                service.voidContract(9999, adminUser, "Lý do").getMessage());

        // Kỹ thuật / null actor / Sales cấp dưới -> bị chặn MSG-022
        assertEquals(PermissionService.MSG_022,
                service.voidContract(50, techUser, "Nhập nhầm").getMessage());
        assertEquals(PermissionService.MSG_022,
                service.voidContract(50, null, "Nhập nhầm").getMessage());
        assertEquals(PermissionService.MSG_022,
                service.voidContract(50, salesSubordinate, "Nhập nhầm").getMessage());

        // Thiếu lý do -> bị chặn ERR_VOID_REASON_REQUIRED
        ServiceResult<Void> noReason = service.voidContract(50, salesManager, "  ");
        assertFalse(noReason.isSuccess());
        assertEquals(ContractService.ERR_VOID_REASON_REQUIRED, noReason.getMessage());

        // Lý do vượt quá MAX_REASON_LENGTH -> ERR_INVALID + FIELD_REASON
        ServiceResult<Void> longReason = service.voidContract(
                50, salesManager, "X".repeat(ContractService.MAX_REASON_LENGTH + 1));
        assertFalse(longReason.isSuccess());
        assertEquals(ContractService.ERR_INVALID, longReason.getMessage());
        assertTrue(longReason.getFieldErrors().containsKey(ContractService.FIELD_REASON));

        // Sales quản lý vùng huỷ bản Nháp -> xóa mềm (is_deleted = true) + ghi lịch sử EVENT_VOIDED
        ServiceResult<Void> okRes = service.voidContract(50, salesManager, "Tạo nhầm đối tác");
        assertTrue(okRes.isSuccess());
        assertNull(contractDAO.findById(50));
        assertTrue(draft.isDeleted());
        List<ContractHistory> history = service.findHistoryByContractId(50);
        assertEquals(1, history.size());
        assertEquals(ContractHistory.EVENT_VOIDED, history.get(0).getEventType());
        assertEquals("Tạo nhầm đối tác", history.get(0).getNote());
    }

    @Test
    public void voidContract_signedContract_onlyAdminAllowedAndBlockedIfHasLiveAmendments() {
        Contract signed = signedParentContract(51, "HD-VOID-SIGNED", new BigDecimal("100000000"));
        contractDAO.putContract(signed);

        // Sales quản lý vùng KHÔNG được huỷ HĐ đã ký -> MSG-022
        ServiceResult<Void> mgrRes = service.voidContract(51, salesManager, "Muốn huỷ HĐ đã ký");
        assertFalse(mgrRes.isSuccess());
        assertEquals(PermissionService.MSG_022, mgrRes.getMessage());

        // Admin thiếu lý do trên HĐ đã ký -> ERR_VOID_REASON_REQUIRED
        ServiceResult<Void> adminNoReason = service.voidContract(51, adminUser, "");
        assertFalse(adminNoReason.isSuccess());
        assertEquals(ContractService.ERR_VOID_REASON_REQUIRED, adminNoReason.getMessage());

        // BR-37: HĐ có phụ lục sống -> trả mã riêng ERR_VOID_HAS_AMENDMENT
        signed.setAmendmentCount(1);
        ServiceResult<Void> hasAmendRes = service.voidContract(51, adminUser, "Huỷ khi còn phụ lục");
        assertFalse(hasAmendRes.isSuccess());
        assertEquals(ContractService.ERR_VOID_HAS_AMENDMENT, hasAmendRes.getMessage());

        // Khi không còn phụ lục sống -> Admin huỷ thành công (is_deleted = true + ghi lịch sử)
        signed.setAmendmentCount(0);
        ServiceResult<Void> adminOk = service.voidContract(51, adminUser, "Huỷ do nhập trùng mã");
        assertTrue(adminOk.isSuccess());
        assertNull(contractDAO.findById(51));
        assertTrue(signed.isDeleted());
    }

    // ==================================================================
    // 7. UC-54: Chữa sai sót nhập liệu trên hợp đồng đã ký (chỉ Admin)
    // ==================================================================

    @Test
    public void correctSignedContract_onlyAdminAllowedAndRequiresReasonAndTrimsTitle() {
        Contract signed = signedParentContract(60, "HD-CORR-60", new BigDecimal("100000000"));
        contractDAO.putContract(signed);

        Contract edited = signedParentContract(60, "  HD-CORR-60-FIXED  ", new BigDecimal("120000000"));
        edited.setTitle("  Tiêu đề đính chính  ");

        // Sales quản lý vùng bị chặn MSG-022
        ServiceResult<Void> mgrRes = service.correctSignedContract(edited, salesManager, "Sửa lỗi gõ máy");
        assertFalse(mgrRes.isSuccess());
        assertEquals(PermissionService.MSG_022, mgrRes.getMessage());

        // contract null hoặc contractId <= 0 -> ERR_INVALID
        assertEquals(ContractService.ERR_INVALID,
                service.correctSignedContract(null, adminUser, "Lý do").getMessage());
        Contract zeroId = signedParentContract(0, "HD-0", BigDecimal.TEN);
        assertEquals(ContractService.ERR_INVALID,
                service.correctSignedContract(zeroId, adminUser, "Lý do").getMessage());

        // HĐ không tồn tại -> ERR_NOT_FOUND
        Contract notFound = signedParentContract(9999, "HD-9999", BigDecimal.TEN);
        assertEquals(ContractService.ERR_NOT_FOUND,
                service.correctSignedContract(notFound, adminUser, "Lý do").getMessage());

        // Admin thiếu lý do hoặc lý do quá dài -> bị chặn
        ServiceResult<Void> noReason = service.correctSignedContract(edited, adminUser, "   ");
        assertFalse(noReason.isSuccess());
        assertEquals(ContractService.ERR_MISSING_REASON, noReason.getMessage());

        ServiceResult<Void> longReason = service.correctSignedContract(
                edited, adminUser, "L".repeat(ContractService.MAX_REASON_LENGTH + 1));
        assertFalse(longReason.isSuccess());
        assertEquals(ContractService.ERR_INVALID, longReason.getMessage());
        assertTrue(longReason.getFieldErrors().containsKey(ContractService.FIELD_REASON));

        // Admin có lý do -> thành công, giữ nguyên ngày ký và chiều hợp đồng gốc, trim cả mã và tiêu đề
        edited.setSigningDate(Date.valueOf("2099-01-01"));
        edited.setDirection("Mua");
        ServiceResult<Void> adminOk = service.correctSignedContract(edited, adminUser, "Đính chính giá trị và mã HĐ");
        assertTrue(adminOk.isSuccess());

        Contract after = contractDAO.findById(60);
        assertEquals("HD-CORR-60-FIXED", after.getContractCode());
        assertEquals("Tiêu đề đính chính", after.getTitle());
        assertEquals(signed.getSigningDate(), after.getSigningDate());
        assertEquals(ContractService.DIRECTION_SELL, after.getDirection());
        assertEquals("Đính chính giá trị và mã HĐ", contractDAO.lastCorrectionReason);
    }

    @Test
    public void correctSignedContract_draftOrFrozenContract_isRejected() {
        Contract draft = validDraftSellContract("HD-DRAFT-61");
        draft.setContractId(61);
        draft.setProgressStatus(ContractService.PROGRESS_DRAFT);
        contractDAO.putContract(draft);

        ServiceResult<Void> draftRes = service.correctSignedContract(draft, adminUser, "Sửa nháp");
        assertFalse(draftRes.isSuccess());
        assertEquals(ContractService.ERR_CORRECT_FAILED, draftRes.getMessage());

        Contract liquidated = signedParentContract(62, "HD-LIQ-62", new BigDecimal("100000000"));
        liquidated.setProgressStatus(ContractService.PROGRESS_LIQUIDATED);
        contractDAO.putContract(liquidated);

        ServiceResult<Void> liqRes = service.correctSignedContract(liquidated, adminUser, "Sửa HĐ đã thanh lý");
        assertFalse(liqRes.isSuccess());
        assertEquals(ContractService.ERR_CORRECT_FAILED, liqRes.getMessage());
    }

    @Test
    public void correctSignedContract_invalidDatesOrDuplicateOrNegativeRootOrAmendmentExceeds_fails() {
        Contract signed1 = signedParentContract(63, "HD-CORR-63", new BigDecimal("100000000"));
        Contract signed2 = signedParentContract(64, "HD-CORR-64", new BigDecimal("100000000"));
        contractDAO.putContract(signed1);
        contractDAO.putContract(signed2);

        // Trùng mã với HĐ khác
        Contract dupEdit = signedParentContract(63, "HD-CORR-64", new BigDecimal("100000000"));
        ServiceResult<Void> dupRes = service.correctSignedContract(dupEdit, adminUser, "Đổi mã");
        assertFalse(dupRes.isSuccess());
        assertEquals(ContractService.ERR_DUPLICATE_CODE, dupRes.getMessage());

        // Ngày hiệu lực trước ngày ký gốc -> vi phạm BR-36
        Contract badDateEdit = signedParentContract(63, "HD-CORR-63", new BigDecimal("100000000"));
        badDateEdit.setEffectiveDate(Date.valueOf(signed1.getSigningDate().toLocalDate().minusDays(5)));
        ServiceResult<Void> dateRes = service.correctSignedContract(badDateEdit, adminUser, "Đổi ngày");
        assertFalse(dateRes.isSuccess());
        assertEquals(ContractService.ERR_INVALID, dateRes.getMessage());

        // Giá trị âm cho HĐ gốc -> ERR_INVALID + FIELD_CONTRACT_VALUE
        Contract negRootEdit = signedParentContract(63, "HD-CORR-63", new BigDecimal("-5000"));
        ServiceResult<Void> negRootRes = service.correctSignedContract(negRootEdit, adminUser, "Đổi giá trị âm");
        assertFalse(negRootRes.isSuccess());
        assertEquals(ContractService.ERR_INVALID, negRootRes.getMessage());
        assertTrue(negRootRes.getFieldErrors().containsKey(ContractValidator.FIELD_CONTRACT_VALUE));

        // Chọn đối tác sai vai trò (HĐ Bán nhưng chọn Nhà cung cấp id=20)
        Contract badRoleEdit = signedParentContract(63, "HD-CORR-63", new BigDecimal("100000000"));
        badRoleEdit.setEnterpriseId(20);
        ServiceResult<Void> roleRes = service.correctSignedContract(badRoleEdit, adminUser, "Đổi đối tác");
        assertFalse(roleRes.isSuccess());
        assertEquals(ContractService.ERR_INVALID, roleRes.getMessage());

        // Đính chính phụ lục đã ký vượt quá giá trị còn lại của HĐ gốc -> ERR_VALUE_NEGATIVE
        Contract signedAmend = signedParentContract(65, "PL-CORR-65", new BigDecimal("-20000000"));
        signedAmend.setParentContractId(63);
        signed1.setAmendmentValueSigned(new BigDecimal("-20000000")); // HĐ gốc 100tr, hiện hành 80tr
        contractDAO.putContract(signedAmend);

        Contract overAmendEdit = signedParentContract(65, "PL-CORR-65", new BigDecimal("-100000001"));
        overAmendEdit.setParentContractId(63);
        ServiceResult<Void> overRes = service.correctSignedContract(overAmendEdit, adminUser, "Đính chính phụ lục");
        assertFalse(overRes.isSuccess());
        assertEquals(ContractService.ERR_VALUE_NEGATIVE, overRes.getMessage());
        assertTrue(overRes.getFieldErrors().containsKey(ContractValidator.FIELD_CONTRACT_VALUE));
    }

    // ==================================================================
    // 8. UC-39: Thêm / xoá sản phẩm trong hợp đồng
    // ==================================================================

    @Test
    public void addAndRemoveProduct_onDraftContract_succeedsAndValidatesInputsAndMaxLengths() {
        Contract draft = validDraftSellContract("HD-PROD-70");
        draft.setContractId(70);
        draft.setProgressStatus(ContractService.PROGRESS_DRAFT);
        contractDAO.putContract(draft);

        // Người dùng không có quyền -> MSG-022
        assertEquals(PermissionService.MSG_022,
                service.addProductToContract(70, 100, 1, "Cái", null, salesSubordinate).getMessage());
        assertEquals(PermissionService.MSG_022,
                service.removeProductFromContract(70, 1, salesSubordinate).getMessage());

        // HĐ không tồn tại -> ERR_NOT_FOUND
        assertEquals(ContractService.ERR_NOT_FOUND,
                service.addProductToContract(9999, 100, 1, "Cái", null, salesManager).getMessage());
        assertEquals(ContractService.ERR_NOT_FOUND,
                service.removeProductFromContract(9999, 1, salesManager).getMessage());

        // productId null/<=0, số lượng <= 0, hoặc sản phẩm không tồn tại -> ERR_ADD_PRODUCT_INVALID
        assertEquals(ContractService.ERR_ADD_PRODUCT_INVALID,
                service.addProductToContract(70, null, 2, "Bộ", null, salesManager).getMessage());
        assertEquals(ContractService.ERR_ADD_PRODUCT_INVALID,
                service.addProductToContract(70, 0, 2, "Bộ", null, salesManager).getMessage());
        assertEquals(ContractService.ERR_ADD_PRODUCT_INVALID,
                service.addProductToContract(70, 100, 0, "Bộ", null, salesManager).getMessage());
        assertEquals(ContractService.ERR_ADD_PRODUCT_INVALID,
                service.addProductToContract(70, 9999, 2, "Bộ", null, salesManager).getMessage());

        // Vượt độ dài cột unit (> 20) hoặc notes (> 255) -> ERR_ADD_PRODUCT_INVALID
        assertEquals(ContractService.ERR_ADD_PRODUCT_INVALID,
                service.addProductToContract(70, 100, 2, "U".repeat(21), null, salesManager).getMessage());
        assertEquals(ContractService.ERR_ADD_PRODUCT_INVALID,
                service.addProductToContract(70, 100, 2, "Bộ", "N".repeat(256), salesManager).getMessage());

        // Đơn vị để trống tự mặc định là "Cái"
        ServiceResult<Void> addOk = service.addProductToContract(70, 100, 3, "  ", "Lắp tại xưởng A", salesManager);
        assertTrue(addOk.isSuccess());
        List<ContractProduct> items = service.findProductsByContractId(70);
        assertEquals(1, items.size());
        assertEquals("Cái", items.get(0).getUnit());
        assertEquals(3, items.get(0).getQuantity());

        // Xoá dòng sản phẩm với contractProductId <= 0 -> ERR_REMOVE_PRODUCT_FAILED
        assertEquals(ContractService.ERR_REMOVE_PRODUCT_FAILED,
                service.removeProductFromContract(70, 0, salesManager).getMessage());

        // Xoá dòng sản phẩm hợp lệ
        int lineId = items.get(0).getContractProductId();
        ServiceResult<Void> rmOk = service.removeProductFromContract(70, lineId, salesManager);
        assertTrue(rmOk.isSuccess());
        assertTrue(service.findProductsByContractId(70).isEmpty());
    }

    @Test
    public void addAndRemoveProduct_onSignedContract_isBlocked() {
        Contract signed = signedParentContract(71, "HD-PROD-SIGNED", new BigDecimal("100000000"));
        contractDAO.putContract(signed);

        ServiceResult<Void> addRes = service.addProductToContract(71, 100, 2, "Cái", null, adminUser);
        assertFalse(addRes.isSuccess());
        assertEquals(ContractService.ERR_ADD_PRODUCT_FAILED, addRes.getMessage());

        ServiceResult<Void> rmRes = service.removeProductFromContract(71, 1, adminUser);
        assertFalse(rmRes.isSuccess());
        assertEquals(ContractService.ERR_REMOVE_PRODUCT_FAILED, rmRes.getMessage());
    }

    // ==================================================================
    // 9. NFR-19: Kiểm thử giao dịch (Transaction rollback cho sign, void, correct)
    // ==================================================================

    @Test
    public void signContract_whenDatabaseFailsMidTransaction_rollsBackAndReturnsFailure() throws SQLException {
        ResultSet lockedRow = JdbcStub.singleRow(JdbcStub.row(
                "progress_status", ContractService.PROGRESS_DRAFT,
                "parent_contract_id", null,
                "contract_value", new BigDecimal("100000000"),
                "effective_date", Date.valueOf(LocalDate.now().plusDays(5)),
                "end_date", Date.valueOf(LocalDate.now().plusMonths(6))));
        PreparedStatement updatePs = mock(PreparedStatement.class);
        when(updatePs.executeQuery()).thenReturn(lockedRow);
        when(updatePs.executeUpdate()).thenReturn(1);

        PreparedStatement historyPs = mock(PreparedStatement.class);
        when(historyPs.executeUpdate()).thenThrow(new SQLException("DB failure during history log"));

        Connection conn = JdbcStub.connectionRoutingOn("contract_history", historyPs, updatePs);

        ContractDAO transactionalDAO = new ContractDAO() {
            @Override
            public Contract findById(int contractId) {
                return validDraftForSign(contractId);
            }
        };

        ContractService txService = new ContractService(
                transactionalDAO, customerDAO, productDAO, permissionService, contractValidator);

        try (MockedStatic<DBContext> dbMock = mockStatic(DBContext.class)) {
            dbMock.when(DBContext::getConnection).thenReturn(conn);

            ServiceResult<Void> res = txService.signContract(80, adminUser, "Ký HĐ");
            assertFalse(res.isSuccess());
            assertEquals(ContractService.ERR_PROGRESS_FAILED, res.getMessage());
            verify(conn).setAutoCommit(false);
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void voidContract_whenDatabaseFailsMidTransaction_rollsBackAndReturnsFailure() throws SQLException {
        ResultSet zeroAmendments = JdbcStub.singleRow(JdbcStub.row("cnt", 0));
        ResultSet lockedRow = JdbcStub.singleRow(JdbcStub.row(
                "progress_status", ContractService.PROGRESS_DRAFT,
                "parent_contract_id", null,
                "contract_value", new BigDecimal("100000000"),
                "effective_date", Date.valueOf(LocalDate.now().plusDays(5)),
                "end_date", Date.valueOf(LocalDate.now().plusMonths(6))));

        PreparedStatement mainPs = mock(PreparedStatement.class);
        when(mainPs.executeQuery()).thenReturn(zeroAmendments, lockedRow);
        when(mainPs.executeUpdate()).thenReturn(1);

        PreparedStatement historyPs = mock(PreparedStatement.class);
        when(historyPs.executeUpdate()).thenThrow(new SQLException("DB failure during void history log"));

        Connection conn = JdbcStub.connectionRoutingOn("contract_history", historyPs, mainPs);

        ContractDAO transactionalDAO = new ContractDAO() {
            @Override
            public Contract findById(int contractId) {
                return validDraftForSign(contractId);
            }
        };

        ContractService txService = new ContractService(
                transactionalDAO, customerDAO, productDAO, permissionService, contractValidator);

        try (MockedStatic<DBContext> dbMock = mockStatic(DBContext.class)) {
            dbMock.when(DBContext::getConnection).thenReturn(conn);

            ServiceResult<Void> res = txService.voidContract(81, adminUser, "Huỷ do nhập nhầm");
            assertFalse(res.isSuccess());
            assertEquals(ContractService.ERR_VOID_FAILED, res.getMessage());
            verify(conn).setAutoCommit(false);
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void correctSignedContract_whenDatabaseFailsMidTransaction_rollsBackAndReturnsFailure() throws SQLException {
        ResultSet lockedRow = JdbcStub.singleRow(JdbcStub.row(
                "progress_status", ContractService.PROGRESS_SIGNED,
                "parent_contract_id", null,
                "contract_value", new BigDecimal("100000000"),
                "effective_date", Date.valueOf(LocalDate.now().minusDays(5)),
                "end_date", Date.valueOf(LocalDate.now().plusMonths(6))));

        PreparedStatement updatePs = mock(PreparedStatement.class);
        when(updatePs.executeQuery()).thenReturn(lockedRow);
        when(updatePs.executeUpdate()).thenReturn(1);

        PreparedStatement historyPs = mock(PreparedStatement.class);
        when(historyPs.executeUpdate()).thenThrow(new SQLException("DB failure during correction history log"));

        Connection conn = JdbcStub.connectionRoutingOn("contract_history", historyPs, updatePs);

        ContractDAO transactionalDAO = new ContractDAO() {
            @Override
            public Contract findById(int contractId) {
                return signedParentContract(contractId, "HD-TX-CORR", new BigDecimal("100000000"));
            }

            @Override
            public boolean contractCodeExists(String contractCode, int excludeContractId) {
                return false;
            }
        };

        ContractService txService = new ContractService(
                transactionalDAO, customerDAO, productDAO, permissionService, contractValidator);

        try (MockedStatic<DBContext> dbMock = mockStatic(DBContext.class)) {
            dbMock.when(DBContext::getConnection).thenReturn(conn);

            Contract edited = signedParentContract(82, "HD-TX-CORR-NEW", new BigDecimal("110000000"));
            ServiceResult<Void> res = txService.correctSignedContract(edited, adminUser, "Sửa mã HĐ");
            assertFalse(res.isSuccess());
            assertEquals(ContractService.ERR_CORRECT_FAILED, res.getMessage());
            verify(conn).setAutoCommit(false);
            verify(conn).rollback();
            verify(conn, never()).commit();
        }
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

    private Contract validDraftSellContract(String code) {
        LocalDate now = LocalDate.now();
        Contract c = new Contract();
        c.setContractCode(code);
        c.setTitle("Hợp đồng cung cấp hệ thống lọc bụi");
        c.setContractType("Cung cấp thiết bị");
        c.setDirection(ContractService.DIRECTION_SELL);
        c.setEnterpriseId(10);
        c.setOwnerId(2);
        c.setEffectiveDate(Date.valueOf(now.plusDays(5)));
        c.setEndDate(Date.valueOf(now.plusMonths(6)));
        c.setContractValue(new BigDecimal("100000000"));
        return c;
    }

    private Contract validDraftBuyContract(String code) {
        LocalDate now = LocalDate.now();
        Contract c = new Contract();
        c.setContractCode(code);
        c.setTitle("Hợp đồng mua vật tư điện");
        c.setContractType("Mua vật tư");
        c.setDirection(ContractService.DIRECTION_BUY);
        c.setEnterpriseId(20);
        c.setOwnerId(2);
        c.setEffectiveDate(Date.valueOf(now.plusDays(5)));
        c.setEndDate(Date.valueOf(now.plusMonths(6)));
        c.setContractValue(new BigDecimal("50000000"));
        return c;
    }

    private Contract signedParentContract(int id, String code, BigDecimal value) {
        LocalDate now = LocalDate.now();
        Contract c = validDraftSellContract(code);
        c.setContractId(id);
        c.setProgressStatus(ContractService.PROGRESS_SIGNED);
        c.setSigningDate(Date.valueOf(now.minusDays(10)));
        c.setEffectiveDate(Date.valueOf(now.minusDays(5)));
        c.setEndDate(Date.valueOf(now.plusMonths(6)));
        c.setContractValue(value);
        c.setAmendmentValueSigned(BigDecimal.ZERO);
        return c;
    }

    private Contract validDraftForSign(int id) {
        Contract c = validDraftSellContract("HD-TX-" + id);
        c.setContractId(id);
        c.setProgressStatus(ContractService.PROGRESS_DRAFT);
        return c;
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

    private static class FakeProductDAO extends ProductDAO {
        private final Map<Integer, Product> products = new HashMap<>();

        void putProduct(Product p) {
            products.put(p.getProductId(), p);
        }

        @Override
        public Product findById(int productId) {
            return products.get(productId);
        }
    }

    private static class FakeContractDAO extends ContractDAO {
        private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
                PROGRESS_DRAFT, Set.of(PROGRESS_SIGNED),
                PROGRESS_SIGNED, Set.of(PROGRESS_LIQUIDATED, PROGRESS_TERMINATED),
                PROGRESS_LIQUIDATED, Set.of(),
                PROGRESS_TERMINATED, Set.of()
        );

        private final Map<Integer, Contract> store = new HashMap<>();
        private final Map<Integer, List<ContractProduct>> productsByContract = new HashMap<>();
        private final Map<Integer, List<ContractHistory>> historyByContract = new HashMap<>();
        private int seq = 1;
        private int prodSeq = 1;
        String lastCorrectionReason;

        void putContract(Contract c) {
            store.put(c.getContractId(), c);
            if (c.getContractId() >= seq) {
                seq = c.getContractId() + 1;
            }
        }

        @Override
        public Contract findById(int contractId) {
            Contract c = store.get(contractId);
            return (c != null && !c.isDeleted()) ? c : null;
        }

        @Override
        public boolean contractCodeExists(String contractCode, int excludeContractId) {
            if (contractCode == null) {
                return false;
            }
            for (Contract c : store.values()) {
                if (!c.isDeleted() && c.getContractId() != excludeContractId
                        && contractCode.trim().equalsIgnoreCase(c.getContractCode())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int insert(Contract contract, int actorId) {
            if (contractCodeExists(contract.getContractCode(), 0)) {
                return DUPLICATE_CODE;
            }
            int id = seq++;
            contract.setContractId(id);
            if (contract.getProgressStatus() == null) {
                contract.setProgressStatus(PROGRESS_DRAFT);
            }
            store.put(id, contract);
            recordHistory(id, ContractHistory.EVENT_CREATED, "Khởi tạo hợp đồng", null, PROGRESS_DRAFT, null, actorId);
            return id;
        }

        @Override
        public boolean update(Contract contract, int actorId) {
            Contract existing = findById(contract.getContractId());
            if (existing == null || existing.isFrozen()) {
                return false;
            }
            if (existing.isTermsLocked()) {
                existing.setOwnerId(contract.getOwnerId());
                recordHistory(existing.getContractId(), ContractHistory.EVENT_UPDATED,
                        "Đổi người phụ trách", null, null, null, actorId);
                return true;
            }
            store.put(contract.getContractId(), contract);
            recordHistory(contract.getContractId(), ContractHistory.EVENT_UPDATED,
                    "Cập nhật hợp đồng", null, null, null, actorId);
            return true;
        }

        @Override
        public boolean correct(Contract contract, int actorId, String reason) {
            Contract existing = findById(contract.getContractId());
            if (existing == null || !existing.isSigned() || reason == null || reason.trim().isEmpty()) {
                return false;
            }
            contract.setSigningDate(existing.getSigningDate());
            contract.setDirection(existing.getDirection());
            contract.setProgressStatus(existing.getProgressStatus());
            store.put(contract.getContractId(), contract);
            this.lastCorrectionReason = reason.trim();
            recordHistory(contract.getContractId(), ContractHistory.EVENT_CORRECTED,
                    "Đính chính hợp đồng đã ký", null, null, this.lastCorrectionReason, actorId);
            return true;
        }

        @Override
        public boolean changeProgressStatus(int contractId, String toStatus, int actorId, String note) {
            Contract c = findById(contractId);
            if (c == null || toStatus == null) {
                return false;
            }
            String fromStatus = c.getProgressStatus();
            if (!ALLOWED_TRANSITIONS.getOrDefault(fromStatus, Set.of()).contains(toStatus)) {
                return false;
            }
            if (PROGRESS_SIGNED.equals(toStatus)) {
                if (c.getEffectiveDate() == null || c.getEndDate() == null) {
                    return false;
                }
                if (c.getSigningDate() == null) {
                    c.setSigningDate(Date.valueOf(LocalDate.now()));
                }
                c.setProgressStatus(PROGRESS_SIGNED);
                recordHistory(contractId, ContractHistory.EVENT_SIGNED,
                        fromStatus + " -> " + toStatus, fromStatus, toStatus, note, actorId);
                return true;
            }
            if (note == null || note.trim().isEmpty()) {
                return false;
            }
            c.setProgressStatus(toStatus);
            String evt = PROGRESS_LIQUIDATED.equals(toStatus)
                    ? ContractHistory.EVENT_LIQUIDATED : ContractHistory.EVENT_TERMINATED;
            recordHistory(contractId, evt, fromStatus + " -> " + toStatus, fromStatus, toStatus, note, actorId);
            return true;
        }

        @Override
        public boolean voidRecord(int contractId, int actorId, String reason) {
            Contract c = findById(contractId);
            if (c == null || reason == null || reason.trim().isEmpty() || c.getAmendmentCount() > 0) {
                return false;
            }
            c.setDeleted(true);
            recordHistory(contractId, ContractHistory.EVENT_VOIDED,
                    "Huỷ bản ghi hợp đồng", c.getProgressStatus(), null, reason.trim(), actorId);
            return true;
        }

        @Override
        public boolean insertProducts(int contractId, List<ContractProduct> items, int actorId) {
            Contract c = findById(contractId);
            if (c == null || !c.isDraft() || items == null) {
                return false;
            }
            List<ContractProduct> list = productsByContract.computeIfAbsent(contractId, k -> new ArrayList<>());
            for (ContractProduct item : items) {
                item.setContractProductId(prodSeq++);
                item.setContractId(contractId);
                list.add(item);
            }
            recordHistory(contractId, ContractHistory.EVENT_PRODUCT_ADDED,
                    "Thêm sản phẩm", null, null, null, actorId);
            return true;
        }

        @Override
        public boolean deleteProductLine(int contractProductId, int contractId, int actorId) {
            Contract c = findById(contractId);
            if (c == null || !c.isDraft()) {
                return false;
            }
            List<ContractProduct> list = productsByContract.get(contractId);
            if (list == null) {
                return false;
            }
            boolean removed = list.removeIf(p -> p.getContractProductId() == contractProductId);
            if (removed) {
                recordHistory(contractId, ContractHistory.EVENT_PRODUCT_REMOVED,
                        "Xoá sản phẩm", null, null, null, actorId);
            }
            return removed;
        }

        @Override
        public List<ContractProduct> findProductsByContractId(int contractId) {
            return productsByContract.getOrDefault(contractId, Collections.emptyList());
        }

        @Override
        public List<ContractHistory> findHistoryByContractId(int contractId) {
            return historyByContract.getOrDefault(contractId, Collections.emptyList());
        }

        private void recordHistory(int contractId, String eventType, String detail,
                                   String fromStatus, String toStatus, String note, int actorId) {
            ContractHistory h = new ContractHistory();
            h.setContractId(contractId);
            h.setEventType(eventType);
            h.setDetail(detail);
            h.setFromStatus(fromStatus);
            h.setToStatus(toStatus);
            h.setNote(note);
            h.setChangedBy(actorId);
            historyByContract.computeIfAbsent(contractId, k -> new ArrayList<>()).add(h);
        }
    }
}
