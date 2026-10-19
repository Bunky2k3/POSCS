package poscs.validator;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import poscs.model.Contract;

import static org.junit.Assert.*;

/**
 * Unit Test cho ContractValidator:
 * - BR-35: Trường bắt buộc (Mã HĐ, Tiêu đề, KH/NCC đúng vai, Loại, Người phụ trách),
 *   ngày ký không phải trường nhập tay, chiều Bán/Mua không đổi được.
 * - BR-36 & MSG-045: Ngày ký ≤ Ngày hiệu lực ≤ Ngày kết thúc. Thiếu 1 mốc hoặc hiệu lực sau kết thúc báo MSG-045.
 * - BR-17: Tính trạng thái tự động theo thời hạn (Chưa hiệu lực / Đang hiệu lực / Sắp hết hạn / Đã hết hạn).
 * - UC-35: Khoản giảm trừ phụ lục không vượt giá trị còn lại của HĐ gốc.
 *   HĐ đã ký chỉ đổi được Người phụ trách; Đã thanh lý / Chấm dứt sớm không được sửa.
 * - UC-44: Ký hợp đồng (cần đủ ngày hiệu lực & kết thúc, ngày ký ≤ hiệu lực ≤ kết thúc).
 * - Tương thích ngược với isValid().
 */
public class ContractValidatorTest {

    private ContractValidator validator;

    @Before
    public void setUp() {
        validator = new ContractValidator();
    }

    private Contract createValidContract() {
        Contract c = new Contract();
        c.setContractId(1);
        c.setContractCode("HD-2026/01");
        c.setTitle("Hợp đồng cung cấp thiết bị POS cho chuỗi siêu thị");
        c.setContractType("Cung cấp thiết bị");
        c.setDirection(ContractValidator.DIRECTION_SELL);
        c.setEnterpriseId(10);
        c.setOwnerId(5);
        c.setProgressStatus(ContractValidator.PROGRESS_DRAFT);
        c.setContractValue(new BigDecimal("150000000"));
        return c;
    }

    // =========================================================================
    // 1. BR-35: KIỂM TRA TRƯỜNG BẮT BUỘC (validateRequiredFields)
    // =========================================================================

    @Test
    public void testValidateRequiredFields_valid() {
        Contract c = createValidContract();
        ValidationResult vr = validator.validateRequiredFields(c);
        assertTrue("Hợp đồng hợp lệ phải pass validation", vr.isValid());
    }

    @Test
    public void testValidateRequiredFields_nullContract() {
        ValidationResult vr = validator.validateRequiredFields(null);
        assertFalse(vr.isValid());
        assertTrue(vr.hasGlobalError());
        assertEquals(ContractValidator.MSG_CONTRACT_INVALID, vr.getGlobalError());
    }

    @Test
    public void testValidateRequiredFields_contractCode() {
        Contract c = createValidContract();

        // null
        c.setContractCode(null);
        ValidationResult vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_CODE_REQUIRED, vr.getError(ContractValidator.FIELD_CONTRACT_CODE));

        // empty
        c.setContractCode("");
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_CODE_REQUIRED, vr.getError(ContractValidator.FIELD_CONTRACT_CODE));

        // blank
        c.setContractCode("   ");
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_CODE_REQUIRED, vr.getError(ContractValidator.FIELD_CONTRACT_CODE));

        // Vượt quá 50 ký tự
        c.setContractCode("A".repeat(51));
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertTrue(vr.getError(ContractValidator.FIELD_CONTRACT_CODE).contains("50"));

        // Đúng 50 ký tự: hợp lệ
        c.setContractCode("A".repeat(50));
        vr = validator.validateRequiredFields(c);
        assertTrue(vr.isValid());
    }

    @Test
    public void testValidateRequiredFields_title() {
        Contract c = createValidContract();

        // null
        c.setTitle(null);
        ValidationResult vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_TITLE_REQUIRED, vr.getError(ContractValidator.FIELD_TITLE));

        // empty
        c.setTitle("");
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_TITLE_REQUIRED, vr.getError(ContractValidator.FIELD_TITLE));

        // blank
        c.setTitle("   \t ");
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_TITLE_REQUIRED, vr.getError(ContractValidator.FIELD_TITLE));

        // Vượt quá 255 ký tự
        c.setTitle("T".repeat(256));
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertTrue(vr.getError(ContractValidator.FIELD_TITLE).contains("255"));

        // Đúng 255 ký tự: hợp lệ
        c.setTitle("T".repeat(255));
        vr = validator.validateRequiredFields(c);
        assertTrue(vr.isValid());
    }

    @Test
    public void testValidateRequiredFields_contractType() {
        Contract c = createValidContract();

        // null
        c.setContractType(null);
        ValidationResult vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_TYPE_REQUIRED, vr.getError(ContractValidator.FIELD_CONTRACT_TYPE));

        // empty
        c.setContractType("");
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_TYPE_REQUIRED, vr.getError(ContractValidator.FIELD_CONTRACT_TYPE));

        // blank
        c.setContractType("   ");
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_TYPE_REQUIRED, vr.getError(ContractValidator.FIELD_CONTRACT_TYPE));

        // Vượt quá 100 ký tự
        c.setContractType("K".repeat(101));
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertTrue(vr.getError(ContractValidator.FIELD_CONTRACT_TYPE).contains("100"));
    }

    @Test
    public void testValidateRequiredFields_enterpriseId() {
        Contract c = createValidContract();

        c.setEnterpriseId(0);
        ValidationResult vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_CUSTOMER_REQUIRED, vr.getError(ContractValidator.FIELD_ENTERPRISE_ID));

        c.setEnterpriseId(-5);
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_CUSTOMER_REQUIRED, vr.getError(ContractValidator.FIELD_ENTERPRISE_ID));
    }

    @Test
    public void testValidateRequiredFields_ownerId() {
        Contract c = createValidContract();

        c.setOwnerId(0);
        ValidationResult vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_OWNER_REQUIRED, vr.getError(ContractValidator.FIELD_OWNER_ID));

        c.setOwnerId(-1);
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_OWNER_REQUIRED, vr.getError(ContractValidator.FIELD_OWNER_ID));
    }

    @Test
    public void testValidateRequiredFields_direction() {
        Contract c = createValidContract();

        // null
        c.setDirection(null);
        ValidationResult vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_DIRECTION_REQUIRED, vr.getError(ContractValidator.FIELD_DIRECTION));

        // empty / blank
        c.setDirection("  ");
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_DIRECTION_REQUIRED, vr.getError(ContractValidator.FIELD_DIRECTION));

        // Sai giá trị
        c.setDirection("Thuê ngoài");
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertTrue(vr.getError(ContractValidator.FIELD_DIRECTION).contains("Bán"));

        // Hợp lệ Bán / Mua
        c.setDirection("Bán");
        assertTrue(validator.validateRequiredFields(c).isValid());

        c.setDirection("Mua");
        c.setContractType("Mua thiết bị");
        assertTrue(validator.validateRequiredFields(c).isValid());
    }

    @Test
    public void testValidateRequiredFields_optionalFieldsLengths() {
        Contract c = createValidContract();

        c.setSignerName("A".repeat(101));
        ValidationResult vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertTrue(vr.getError("signerName").contains("100"));

        c.setSignerName("Nguyễn Văn A");
        c.setSignerPosition("B".repeat(101));
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertTrue(vr.getError("signerPosition").contains("100"));

        c.setSignerPosition("Giám đốc");
        c.setCounterpartySignerName("C".repeat(101));
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertTrue(vr.getError("counterpartySignerName").contains("100"));

        c.setCounterpartySignerName("Trần Văn B");
        c.setCounterpartySignerPosition("D".repeat(101));
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertTrue(vr.getError("counterpartySignerPosition").contains("100"));

        c.setCounterpartySignerPosition("Trưởng phòng Mua hàng");
        c.setAuthorizationRef("E".repeat(256));
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertTrue(vr.getError("authorizationRef").contains("255"));

        c.setAuthorizationRef("UQ-01/2026");
        c.setSigningPlace("F".repeat(256));
        vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertTrue(vr.getError("signingPlace").contains("255"));

        c.setSigningPlace("Hà Nội");
        assertTrue(validator.validateRequiredFields(c).isValid());
    }

    @Test
    public void testValidateRequiredFields_contractValue() {
        Contract c = createValidContract();

        // null: hợp lệ (hợp đồng chưa chốt giá)
        c.setContractValue(null);
        assertTrue(validator.validateRequiredFields(c).isValid());

        // 0: hợp lệ
        c.setContractValue(BigDecimal.ZERO);
        assertTrue(validator.validateRequiredFields(c).isValid());

        // Dương: hợp lệ
        c.setContractValue(new BigDecimal("100000000"));
        assertTrue(validator.validateRequiredFields(c).isValid());

        // Âm: vi phạm
        c.setContractValue(new BigDecimal("-1"));
        ValidationResult vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertTrue(vr.getError(ContractValidator.FIELD_CONTRACT_VALUE).contains("không được là số âm"));
    }

    // =========================================================================
    // 2. BR-35: KHÁCH HÀNG / NCC ĐÚNG VAI TRÒ (CẶP ĐÔI CHÉO)
    // =========================================================================

    @Test
    public void testCounterpartyRoleFor() {
        assertEquals(ContractValidator.ROLE_BUYER, ContractValidator.counterpartyRoleFor("Bán"));
        assertEquals(ContractValidator.ROLE_BUYER, ContractValidator.counterpartyRoleFor("bán"));
        assertEquals(ContractValidator.ROLE_SUPPLIER, ContractValidator.counterpartyRoleFor("Mua"));
        assertEquals(ContractValidator.ROLE_SUPPLIER, ContractValidator.counterpartyRoleFor("mua"));
    }

    @Test
    public void testCounterpartyMatchingDirection_sellSide() {
        // Chiều Bán -> đối tác phải giữ vai "Khách mua"
        assertTrue(validator.isCounterpartyMatchingDirection("Bán", Set.of("Khách mua")));
        assertTrue(validator.isCounterpartyMatchingDirection("Bán", Set.of("Khách mua", "Nhà cung cấp")));
        assertFalse(validator.isCounterpartyMatchingDirection("Bán", Set.of("Nhà cung cấp")));
        assertFalse(validator.isCounterpartyMatchingDirection("Bán", Collections.emptySet()));
        assertFalse(validator.isCounterpartyMatchingDirection("Bán", null));
    }

    @Test
    public void testCounterpartyMatchingDirection_buySide() {
        // Chiều Mua -> đối tác phải giữ vai "Nhà cung cấp"
        assertTrue(validator.isCounterpartyMatchingDirection("Mua", Set.of("Nhà cung cấp")));
        assertTrue(validator.isCounterpartyMatchingDirection("Mua", Set.of("Khách mua", "Nhà cung cấp")));
        assertFalse(validator.isCounterpartyMatchingDirection("Mua", Set.of("Khách mua")));
        assertFalse(validator.isCounterpartyMatchingDirection("Mua", Collections.emptySet()));
        assertFalse(validator.isCounterpartyMatchingDirection("Mua", null));
    }

    @Test
    public void testValidateCounterpartyRole_violation() {
        ValidationResult vr = new ValidationResult();
        boolean ok = validator.validateCounterpartyRole(vr, "Bán", Set.of("Nhà cung cấp"));
        assertFalse(ok);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_COUNTERPARTY_ROLE_MISMATCH,
                vr.getError(ContractValidator.FIELD_ENTERPRISE_ID));
    }

    // =========================================================================
    // 3. DANH MỤC LOẠI HỢP ĐỒNG THEO CHIỀU
    // =========================================================================

    @Test
    public void testContractTypesForDirection() {
        List<String> sellTypes = ContractValidator.contractTypesFor("Bán");
        assertTrue(sellTypes.contains("Cung cấp thiết bị"));
        assertTrue(sellTypes.contains("Thi công lắp đặt"));
        assertTrue(sellTypes.contains("Bảo trì bảo dưỡng"));
        assertFalse(sellTypes.contains("Mua thiết bị"));

        List<String> buyTypes = ContractValidator.contractTypesFor("Mua");
        assertTrue(buyTypes.contains("Mua thiết bị"));
        assertTrue(buyTypes.contains("Mua vật tư"));
        assertTrue(buyTypes.contains("Thuê thi công lắp đặt"));
        assertTrue(buyTypes.contains("Thuê bảo trì"));
        assertFalse(buyTypes.contains("Cung cấp thiết bị"));

        assertTrue(validator.isValidContractType("Cung cấp thiết bị", "Bán"));
        assertFalse(validator.isValidContractType("Mua thiết bị", "Bán"));
        assertTrue(validator.isValidContractType("Mua vật tư", "Mua"));
        assertFalse(validator.isValidContractType("Thi công lắp đặt", "Mua"));
        assertFalse(validator.isValidContractType(null, "Bán"));
    }

    // =========================================================================
    // 4. BR-36 & MSG-045: RÀNG BUỘC NGÀY KÝ ≤ HIỆU LỰC ≤ KẾT THÚC
    // =========================================================================

    @Test
    public void testValidateDates_draftBothNull_valid() {
        // Bản nháp chưa chốt thời hạn -> cả 2 mốc cùng null là hợp lệ
        ValidationResult vr = new ValidationResult();
        assertTrue(validator.validateDates(vr, (LocalDate) null, null, null));
        assertTrue(vr.isValid());
    }

    @Test
    public void testValidateDates_onlyOneDateFilled_MSG045() {
        // Chỉ điền hiệu lực mà thiếu kết thúc -> MSG-045
        ValidationResult vr1 = new ValidationResult();
        boolean ok1 = validator.validateDates(vr1, (LocalDate) null, LocalDate.of(2026, 1, 1), null);
        assertFalse(ok1);
        assertEquals(ContractValidator.MSG_045, vr1.getError(ContractValidator.FIELD_END_DATE));

        // Chỉ điền kết thúc mà thiếu hiệu lực -> MSG-045
        ValidationResult vr2 = new ValidationResult();
        boolean ok2 = validator.validateDates(vr2, (LocalDate) null, null, LocalDate.of(2026, 12, 31));
        assertFalse(ok2);
        assertEquals(ContractValidator.MSG_045, vr2.getError(ContractValidator.FIELD_EFFECTIVE_DATE));
    }

    @Test
    public void testValidateDates_effectiveAfterEnd_MSG045() {
        // Hiệu lực sau ngày kết thúc -> MSG-045
        ValidationResult vr = new ValidationResult();
        boolean ok = validator.validateDates(vr, null,
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1));
        assertFalse(ok);
        assertEquals(ContractValidator.MSG_045, vr.getError(ContractValidator.FIELD_EFFECTIVE_DATE));
    }

    @Test
    public void testValidateDates_effectiveEqualsEnd_valid() {
        // Hiệu lực = kết thúc (hợp đồng 1 ngày) -> hợp lệ
        ValidationResult vr = new ValidationResult();
        LocalDate d = LocalDate.of(2026, 5, 20);
        assertTrue(validator.validateDates(vr, d, d, d));
        assertTrue(vr.isValid());
    }

    @Test
    public void testValidateDates_signingAfterEffective_violation() {
        // Ngày ký sau ngày hiệu lực -> vi phạm BR-36
        ValidationResult vr = new ValidationResult();
        LocalDate signing = LocalDate.of(2026, 2, 1);
        LocalDate effective = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        assertFalse(validator.validateDates(vr, signing, effective, end));
        assertEquals(ContractValidator.MSG_SIGNING_AFTER_EFFECTIVE,
                vr.getError(ContractValidator.FIELD_SIGNING_DATE));
    }

    @Test
    public void testValidateDates_signingAfterEnd_violation() {
        // Ngày ký sau ngày kết thúc (khi cả 3 mốc có mặt, signing > effective sẽ được ghi nhận trước)
        ValidationResult vr = new ValidationResult();
        LocalDate signing = LocalDate.of(2027, 1, 1);
        LocalDate effective = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        assertFalse(validator.validateDates(vr, signing, effective, end));
        assertEquals(ContractValidator.MSG_SIGNING_AFTER_EFFECTIVE,
                vr.getError(ContractValidator.FIELD_SIGNING_DATE));

        // Nhánh signing > end khi effective == null
        ValidationResult vrEndOnly = new ValidationResult();
        assertFalse(validator.validateDates(vrEndOnly, signing, null, end));
        assertEquals(ContractValidator.MSG_SIGNING_AFTER_END,
                vrEndOnly.getError(ContractValidator.FIELD_SIGNING_DATE));
    }

    @Test
    public void testValidateDates_validSequence() {
        // Ngày ký <= ngày hiệu lực <= ngày kết thúc
        ValidationResult vr = new ValidationResult();
        LocalDate signing = LocalDate.of(2026, 1, 1);
        LocalDate effective = LocalDate.of(2026, 1, 15);
        LocalDate end = LocalDate.of(2026, 12, 31);
        assertTrue(validator.validateDates(vr, signing, effective, end));
        assertTrue(vr.isValid());
    }

    @Test
    public void testValidateDates_sqlDateOverload() {
        Date signing = Date.valueOf("2026-01-01");
        Date effective = Date.valueOf("2026-01-15");
        Date end = Date.valueOf("2026-12-31");

        assertTrue(validator.isValidDates(signing, effective, end));

        // Vi phạm: hiệu lực sau kết thúc
        assertFalse(validator.isValidDates(signing, end, effective));

        // Vi phạm: ký sau hiệu lực
        assertFalse(validator.isValidDates(effective, signing, end));
    }

    // =========================================================================
    // 5. BR-17: TÍNH TRẠNG THÁI TỰ ĐỘNG THEO THỜI HẠN (computeStatus)
    // =========================================================================

    @Test
    public void testComputeStatus_nullDates() {
        assertEquals(ContractValidator.STATUS_DRAFT,
                ContractValidator.computeStatus((LocalDate) null, (LocalDate) null, LocalDate.of(2026, 5, 1)));
        assertEquals(ContractValidator.STATUS_DRAFT,
                ContractValidator.computeStatus(LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 5, 1)));
        assertEquals(ContractValidator.STATUS_DRAFT,
                ContractValidator.computeStatus(null, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 5, 1)));
    }

    @Test
    public void testComputeStatus_beforeEffectiveDate() {
        // asOfDate trước ngày hiệu lực -> Chưa hiệu lực
        LocalDate effective = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        LocalDate asOf = LocalDate.of(2026, 5, 31);

        assertEquals(ContractValidator.STATUS_DRAFT,
                ContractValidator.computeStatus(effective, end, asOf));
    }

    @Test
    public void testComputeStatus_afterEndDate() {
        // asOfDate sau ngày kết thúc -> Đã hết hạn
        LocalDate effective = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        LocalDate asOf = LocalDate.of(2026, 7, 1);

        assertEquals(ContractValidator.STATUS_EXPIRED,
                ContractValidator.computeStatus(effective, end, asOf));
    }

    @Test
    public void testComputeStatus_activeMoreThan30Days() {
        // Còn 31 ngày tới kết thúc -> Đang hiệu lực
        LocalDate effective = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        LocalDate asOf = LocalDate.of(2026, 11, 30); // Cách 31 ngày

        assertEquals(ContractValidator.STATUS_ACTIVE,
                ContractValidator.computeStatus(effective, end, asOf));
    }

    @Test
    public void testComputeStatus_soonThresholdBoundary() {
        LocalDate effective = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);

        // Đúng 30 ngày -> Sắp hết hạn (BR-17: còn ≤ 30 ngày)
        LocalDate asOf30 = LocalDate.of(2026, 12, 1);
        assertEquals(ContractValidator.STATUS_SOON,
                ContractValidator.computeStatus(effective, end, asOf30));

        // 10 ngày -> Sắp hết hạn
        LocalDate asOf10 = LocalDate.of(2026, 12, 21);
        assertEquals(ContractValidator.STATUS_SOON,
                ContractValidator.computeStatus(effective, end, asOf10));

        // Đúng ngày kết thúc (còn 0 ngày) -> Sắp hết hạn
        LocalDate asOfEnd = LocalDate.of(2026, 12, 31);
        assertEquals(ContractValidator.STATUS_SOON,
                ContractValidator.computeStatus(effective, end, asOfEnd));
    }

    @Test
    public void testComputeStatus_sqlDateOverloads() {
        Date effective = Date.valueOf("2026-01-01");
        Date end = Date.valueOf("2026-12-31");

        assertEquals(ContractValidator.STATUS_ACTIVE,
                ContractValidator.computeStatus(effective, end, LocalDate.of(2026, 6, 1)));

        assertEquals(ContractValidator.STATUS_EXPIRED,
                ContractValidator.computeStatus(effective, end, LocalDate.of(2027, 1, 1)));

        assertEquals(ContractValidator.STATUS_DRAFT,
                ContractValidator.computeStatus(effective, end, LocalDate.of(2025, 12, 31)));
    }

    // =========================================================================
    // 6. UC-35: KHOẢN GIẢM TRỪ PHỤ LỤC KHÔNG VƯỢT GIÁ TRỊ CÒN LẠI CỦA HĐ GỐC
    // =========================================================================

    @Test
    public void testExceedsRemainingValue_nullOrPositiveDelta() {
        // Delta null hoặc dương (tăng giá) -> không vượt
        assertFalse(validator.exceedsRemainingValue(new BigDecimal("100000000"), null));
        assertFalse(validator.exceedsRemainingValue(new BigDecimal("100000000"), BigDecimal.ZERO));
        assertFalse(validator.exceedsRemainingValue(new BigDecimal("100000000"), new BigDecimal("50000000")));

        Contract parent = new Contract();
        parent.setContractValue(new BigDecimal("100000000"));
        assertFalse(validator.exceedsRemainingValue(parent, new BigDecimal("20000000")));
    }

    @Test
    public void testExceedsRemainingValue_reductionWithinLimit() {
        // HĐ gốc 100tr, giảm trừ 40tr -> còn 60tr >= 0: hợp lệ
        BigDecimal remaining = new BigDecimal("100000000");
        BigDecimal delta = new BigDecimal("-40000000");
        assertFalse(validator.exceedsRemainingValue(remaining, delta));

        // Giảm trừ đúng 100tr -> còn 0 >= 0: hợp lệ
        assertFalse(validator.exceedsRemainingValue(remaining, new BigDecimal("-100000000")));
    }

    @Test
    public void testExceedsRemainingValue_reductionExceedsLimit() {
        // HĐ gốc 100tr, giảm trừ 100.000.001 -> âm: vượt
        BigDecimal remaining = new BigDecimal("100000000");
        BigDecimal delta = new BigDecimal("-100000001");
        assertTrue(validator.exceedsRemainingValue(remaining, delta));

        // HĐ gốc chưa chốt giá (null coi như 0), giảm trừ 1 đồng cũng là vượt
        assertTrue(validator.exceedsRemainingValue((BigDecimal) null, new BigDecimal("-1")));
    }

    @Test
    public void testExceedsRemainingValue_parentWithSignedAmendments() {
        // HĐ gốc 200tr, đã có phụ lục giảm 50tr -> hiện hành 150tr
        Contract parent = new Contract();
        parent.setContractValue(new BigDecimal("200000000"));
        parent.setAmendmentValueSigned(new BigDecimal("-50000000"));

        // Giảm tiếp 150tr -> còn 0: hợp lệ
        assertFalse(validator.exceedsRemainingValue(parent, new BigDecimal("-150000000")));

        // Giảm tiếp 150.000.001 -> âm: vượt
        assertTrue(validator.exceedsRemainingValue(parent, new BigDecimal("-150000001")));
    }

    @Test
    public void testValidateAmendmentReduction() {
        ValidationResult vr = new ValidationResult();
        boolean ok = validator.validateAmendmentReduction(vr,
                new BigDecimal("50000000"),
                new BigDecimal("-60000000"));
        assertFalse(ok);
        assertEquals(ContractValidator.MSG_EXCEEDS_REMAINING_VALUE,
                vr.getError(ContractValidator.FIELD_CONTRACT_VALUE));
    }

    // =========================================================================
    // 7. UC-33: TẠO HỢP ĐỒNG (validateCreate)
    // =========================================================================

    @Test
    public void testValidateCreate_success() {
        Contract c = createValidContract();
        ValidationResult vr = validator.validateCreate(c, Set.of("Khách mua"));
        assertTrue(vr.isValid());
    }

    @Test
    public void testValidateCreate_signingDateForbidden() {
        // Lưu ý: ngày ký không phải trường nhập (hệ thống đóng dấu khi Ký)
        Contract c = createValidContract();
        c.setSigningDate(Date.valueOf("2026-02-15"));

        ValidationResult vr = validator.validateCreate(c, Set.of("Khách mua"));
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_SIGNING_DATE_SYSTEM_GENERATED,
                vr.getError(ContractValidator.FIELD_SIGNING_DATE));
    }

    @Test
    public void testValidateCreate_counterpartyRoleMismatch() {
        Contract c = createValidContract(); // Bán -> cần Khách mua
        ValidationResult vr = validator.validateCreate(c, Set.of("Nhà cung cấp"));
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_COUNTERPARTY_ROLE_MISMATCH,
                vr.getError(ContractValidator.FIELD_ENTERPRISE_ID));
    }

    @Test
    public void testValidateCreate_invalidDatesIfProvided() {
        Contract c = createValidContract();
        // Điền 1 mốc hiệu lực nhưng thiếu kết thúc
        c.setEffectiveDate(Date.valueOf("2026-01-01"));
        c.setEndDate(null);

        ValidationResult vr = validator.validateCreate(c, Set.of("Khách mua"));
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_045, vr.getError(ContractValidator.FIELD_END_DATE));
    }

    // =========================================================================
    // 8. UC-35: CẬP NHẬT HỢP ĐỒNG (validateUpdate)
    // =========================================================================

    @Test
    public void testValidateUpdate_directionImmutable() {
        // Lưu ý: Chiều Bán/Mua không đổi được
        Contract existing = createValidContract();
        existing.setDirection(ContractValidator.DIRECTION_SELL);

        Contract updated = createValidContract();
        updated.setDirection(ContractValidator.DIRECTION_BUY); // Cố tình đổi sang Mua

        ValidationResult vr = validator.validateUpdate(updated, existing, Set.of("Khách mua"));
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_DIRECTION_IMMUTABLE,
                vr.getError(ContractValidator.FIELD_DIRECTION));
    }

    @Test
    public void testValidateUpdate_signingDateNotEditable() {
        // Existing nháp chưa có ngày ký, form cố tình gửi ngày ký lên
        Contract existing = createValidContract();
        existing.setSigningDate(null);

        Contract updated = createValidContract();
        updated.setSigningDate(Date.valueOf("2026-03-01"));

        ValidationResult vr = validator.validateUpdate(updated, existing, Set.of("Khách mua"));
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_SIGNING_DATE_SYSTEM_GENERATED,
                vr.getError(ContractValidator.FIELD_SIGNING_DATE));

        // Existing đã có ngày ký, cố tình đổi ngày ký khác
        existing.setSigningDate(Date.valueOf("2026-01-10"));
        updated.setSigningDate(Date.valueOf("2026-01-20"));
        vr = validator.validateUpdate(updated, existing, Set.of("Khách mua"));
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_SIGNING_DATE_IMMUTABLE,
                vr.getError(ContractValidator.FIELD_SIGNING_DATE));
    }

    @Test
    public void testValidateUpdate_lockedProgressStatus() {
        // Đã thanh lý hoặc Chấm dứt sớm: không sửa
        Contract existing = createValidContract();
        existing.setProgressStatus(ContractValidator.PROGRESS_LIQUIDATED);

        Contract updated = createValidContract();
        ValidationResult vr = validator.validateUpdate(updated, existing, Set.of("Khách mua"));
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_CANNOT_EDIT_LOCKED,
                vr.getError(ContractValidator.FIELD_PROGRESS_STATUS));

        existing.setProgressStatus(ContractValidator.PROGRESS_TERMINATED);
        vr = validator.validateUpdate(updated, existing, Set.of("Khách mua"));
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_CANNOT_EDIT_LOCKED,
                vr.getError(ContractValidator.FIELD_PROGRESS_STATUS));
    }

    @Test
    public void testValidateUpdate_signedContractRestricted() {
        // Đã ký: chỉ được đổi Người phụ trách
        Contract existing = createValidContract();
        existing.setProgressStatus(ContractValidator.PROGRESS_SIGNED);
        existing.setSigningDate(Date.valueOf("2026-01-01"));
        existing.setEffectiveDate(Date.valueOf("2026-01-01"));
        existing.setEndDate(Date.valueOf("2026-12-31"));

        // Chỉ đổi ownerId: HỢP LỆ
        Contract updatedValid = createValidContract();
        updatedValid.setProgressStatus(ContractValidator.PROGRESS_SIGNED);
        updatedValid.setSigningDate(existing.getSigningDate());
        updatedValid.setEffectiveDate(existing.getEffectiveDate());
        updatedValid.setEndDate(existing.getEndDate());
        updatedValid.setOwnerId(99); // Đổi từ 5 sang 99

        ValidationResult vr = validator.validateUpdate(updatedValid, existing, Set.of("Khách mua"));
        assertTrue(vr.isValid());

        // Đổi tiêu đề khi đã ký: VI PHẠM
        Contract updatedInvalid = createValidContract();
        updatedInvalid.setProgressStatus(ContractValidator.PROGRESS_SIGNED);
        updatedInvalid.setSigningDate(existing.getSigningDate());
        updatedInvalid.setEffectiveDate(existing.getEffectiveDate());
        updatedInvalid.setEndDate(existing.getEndDate());
        updatedInvalid.setTitle("Tiêu đề bị sửa lén");

        vr = validator.validateUpdate(updatedInvalid, existing, Set.of("Khách mua"));
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_SIGNED_EDIT_RESTRICTED, vr.getError(ValidationResult.GLOBAL_KEY));
    }

    @Test
    public void testValidateUpdate_draftAllowsEditingAllFields() {
        Contract existing = createValidContract();
        existing.setProgressStatus(ContractValidator.PROGRESS_DRAFT);

        Contract updated = createValidContract();
        updated.setTitle("Tiêu đề cập nhật ở bản nháp");
        updated.setContractType("Bảo trì bảo dưỡng");
        updated.setEffectiveDate(Date.valueOf("2026-02-01"));
        updated.setEndDate(Date.valueOf("2026-08-30"));
        updated.setContractValue(new BigDecimal("200000000"));

        ValidationResult vr = validator.validateUpdate(updated, existing, Set.of("Khách mua"));
        assertTrue(vr.isValid());
    }

    // =========================================================================
    // 9. UC-44: KÝ HỢP ĐỒNG (validateSigning)
    // =========================================================================

    @Test
    public void testValidateSigning_success() {
        Contract c = createValidContract();
        c.setProgressStatus(ContractValidator.PROGRESS_DRAFT);
        c.setEffectiveDate(Date.valueOf("2026-02-01"));
        c.setEndDate(Date.valueOf("2026-12-31"));

        LocalDate signingDate = LocalDate.of(2026, 1, 15); // signing <= effective <= end
        ValidationResult vr = validator.validateSigning(c, signingDate);
        assertTrue(vr.isValid());
    }

    @Test
    public void testValidateSigning_notDraft() {
        Contract c = createValidContract();
        c.setProgressStatus(ContractValidator.PROGRESS_SIGNED);
        c.setEffectiveDate(Date.valueOf("2026-02-01"));
        c.setEndDate(Date.valueOf("2026-12-31"));

        ValidationResult vr = validator.validateSigning(c, LocalDate.of(2026, 1, 15));
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_ONLY_DRAFT_CAN_SIGN,
                vr.getError(ContractValidator.FIELD_PROGRESS_STATUS));
    }

    @Test
    public void testValidateSigning_missingTerms_MSG045() {
        Contract c = createValidContract();
        c.setProgressStatus(ContractValidator.PROGRESS_DRAFT);
        c.setEffectiveDate(null);
        c.setEndDate(null);

        ValidationResult vr = validator.validateSigning(c, LocalDate.of(2026, 1, 15));
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_045, vr.getError(ContractValidator.FIELD_EFFECTIVE_DATE));
    }

    @Test
    public void testValidateSigning_signingAfterEffective() {
        Contract c = createValidContract();
        c.setProgressStatus(ContractValidator.PROGRESS_DRAFT);
        c.setEffectiveDate(Date.valueOf("2026-01-01"));
        c.setEndDate(Date.valueOf("2026-12-31"));

        // Ký ngày 2026-01-10 sau ngày hiệu lực 2026-01-01 -> vi phạm BR-36
        ValidationResult vr = validator.validateSigning(c, LocalDate.of(2026, 1, 10));
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_SIGNING_AFTER_EFFECTIVE,
                vr.getError(ContractValidator.FIELD_SIGNING_DATE));
    }

    // =========================================================================
    // 10. TƯƠNG THÍCH NGƯỢC (isValid)
    // =========================================================================

    @Test
    public void testIsValid_backwardCompatibility() {
        Contract c = createValidContract();
        assertTrue(validator.isValid(c));

        // null contract
        assertFalse(validator.isValid(null));

        // blank code
        c.setContractCode("");
        assertFalse(validator.isValid(c));
        c.setContractCode("HD-01");

        // enterpriseId <= 0
        c.setEnterpriseId(0);
        assertFalse(validator.isValid(c));
        c.setEnterpriseId(10);

        // ownerId <= 0
        c.setOwnerId(0);
        assertFalse(validator.isValid(c));
        c.setOwnerId(5);

        // effective > end -> false
        c.setEffectiveDate(Date.valueOf("2026-12-31"));
        c.setEndDate(Date.valueOf("2026-01-01"));
        assertFalse(validator.isValid(c));

        // effective <= end -> true
        c.setEffectiveDate(Date.valueOf("2026-01-01"));
        c.setEndDate(Date.valueOf("2026-12-31"));
        assertTrue(validator.isValid(c));

        // signing > effective -> false
        c.setSigningDate(Date.valueOf("2026-02-01"));
        assertFalse(validator.isValid(c));

        // signing <= effective -> true
        c.setSigningDate(Date.valueOf("2026-01-01"));
        assertTrue(validator.isValid(c));
    }

    // =========================================================================
    // 11. CÁC TEST BỔ SUNG THEO BÁO CÁO QA TASK 4.1
    // =========================================================================

    @Test
    public void testValidateUpdate_signingDateCleared_violation() {
        // HĐ đã ký, cố tình xóa trắng ngày ký -> vi phạm
        Contract existing = createValidContract();
        existing.setSigningDate(Date.valueOf("2026-01-01"));

        Contract updated = createValidContract();
        updated.setSigningDate(null);

        ValidationResult vr = validator.validateUpdate(updated, existing, Set.of("Khách mua"));
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_SIGNING_DATE_IMMUTABLE,
                vr.getError(ContractValidator.FIELD_SIGNING_DATE));
    }

    @Test
    public void testValidateUpdate_directionCleared_violation() {
        // HĐ đã có chiều Bán, cố tình xóa null hoặc rỗng -> vi phạm
        Contract existing = createValidContract();
        existing.setDirection(ContractValidator.DIRECTION_SELL);

        Contract updated = createValidContract();
        updated.setDirection(null);
        ValidationResult vr1 = validator.validateUpdate(updated, existing, Set.of("Khách mua"));
        assertFalse(vr1.isValid());
        assertEquals(ContractValidator.MSG_DIRECTION_IMMUTABLE,
                vr1.getError(ContractValidator.FIELD_DIRECTION));

        updated.setDirection("   ");
        ValidationResult vr2 = validator.validateUpdate(updated, existing, Set.of("Khách mua"));
        assertFalse(vr2.isValid());
        assertEquals(ContractValidator.MSG_DIRECTION_IMMUTABLE,
                vr2.getError(ContractValidator.FIELD_DIRECTION));
    }

    @Test
    public void testValidateRequiredFields_contractTypeMismatchDirection() {
        Contract c = createValidContract();
        c.setDirection(ContractValidator.DIRECTION_SELL);
        c.setContractType("Mua thiết bị"); // Loại thuộc chiều Mua, không hợp lệ cho Bán

        ValidationResult vr = validator.validateRequiredFields(c);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_TYPE_REQUIRED,
                vr.getError(ContractValidator.FIELD_CONTRACT_TYPE));
    }

    @Test
    public void testValidateCreate_failClosedCounterpartyRoles() {
        Contract c = createValidContract();

        // null roles -> fail-closed
        ValidationResult vrNull = validator.validateCreate(c, null);
        assertFalse(vrNull.isValid());
        assertEquals(ContractValidator.MSG_COUNTERPARTY_ROLE_MISMATCH,
                vrNull.getError(ContractValidator.FIELD_ENTERPRISE_ID));

        // empty roles -> fail-closed
        ValidationResult vrEmpty = validator.validateCreate(c, Collections.emptySet());
        assertFalse(vrEmpty.isValid());
        assertEquals(ContractValidator.MSG_COUNTERPARTY_ROLE_MISMATCH,
                vrEmpty.getError(ContractValidator.FIELD_ENTERPRISE_ID));
    }

    @Test
    public void testValidateUpdate_failClosedCounterpartyRoles() {
        Contract existing = createValidContract();
        Contract updated = createValidContract();

        ValidationResult vr = validator.validateUpdate(updated, existing, null);
        assertFalse(vr.isValid());
        assertEquals(ContractValidator.MSG_COUNTERPARTY_ROLE_MISMATCH,
                vr.getError(ContractValidator.FIELD_ENTERPRISE_ID));
    }

    @Test
    public void testValidateRequiredFields_amendmentNegativeValueAllowed() {
        // HĐ gốc: giá trị âm bị từ chối
        Contract normal = createValidContract();
        normal.setContractValue(new BigDecimal("-10000000"));
        ValidationResult vrNormal = validator.validateRequiredFields(normal);
        assertFalse(vrNormal.isValid());
        assertTrue(vrNormal.getError(ContractValidator.FIELD_CONTRACT_VALUE).contains("không được là số âm"));

        // Phụ lục (isAmendment == true): giá trị âm (khoản giảm trừ) hợp lệ
        Contract amendment = createValidContract();
        amendment.setParentContractId(10);
        amendment.setContractValue(new BigDecimal("-10000000"));
        ValidationResult vrAmend = validator.validateRequiredFields(amendment);
        assertTrue(vrAmend.isValid());
    }

    @Test
    public void testValidateUpdate_draftAmendmentReductionValid() {
        Contract existing = createValidContract();
        existing.setParentContractId(10);

        Contract updated = createValidContract();
        updated.setParentContractId(10);
        updated.setContractValue(new BigDecimal("-20000000"));

        ValidationResult vr = validator.validateUpdate(updated, existing, Set.of("Khách mua"));
        assertTrue(vr.isValid());
    }
}
