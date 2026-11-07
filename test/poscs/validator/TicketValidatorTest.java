package poscs.validator;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import poscs.model.TechnicalRequest;

import static org.junit.Assert.*;

/**
 * Unit Test cho TicketValidator (UC-21, UC-22):
 * - BR-39: Bắt buộc Khách hàng, Loại phiếu, Mức ưu tiên, Kênh tiếp nhận, Mô tả sự cố, Kỹ thuật viên.
 *   Trạng thái và Ngày tạo do hệ thống gán.
 * - BR-40: Trạng thái và luồng chuyển hợp lệ: Mới tiếp nhận -> Đang xử lý -> Đã đóng (MSG-051).
 * - BR-41: Mức ưu tiên ∈ {Thấp, Bình thường, Cao, Khẩn cấp}.
 * - BR-43: Cờ bảo hành Có/Không mặc định Có.
 * - BR-47: Điều kiện xóa phiếu (không xóa phiếu Đang xử lý).
 */
public class TicketValidatorTest {

    private TicketValidator validator;

    @Before
    public void setUp() {
        validator = new TicketValidator();
    }

    private TechnicalRequest createValidTicket() {
        TechnicalRequest t = new TechnicalRequest();
        t.setTicketId(1);
        t.setTicketCode("TK-0001");
        t.setEnterpriseId(10);
        t.setContractId(20);
        t.setTicketType("Bảo hành");
        t.setPriority(TicketValidator.PRIORITY_NORMAL);
        t.setReceptionChannel("Điện thoại");
        t.setAssignedTechnicianId(5);
        t.setDescription("Thiết bị nguồn không lên điện");
        t.setWarranty(true);
        t.setStatus(TicketValidator.STATUS_NEW);
        t.setCreatedDate(Date.valueOf(LocalDate.now()));
        return t;
    }

    // -------------------------------------------------------------------------
    // 1. Tests cho isValid() (tương thích ngược)
    // -------------------------------------------------------------------------
    @Test
    public void testIsValid_validTicket() {
        TechnicalRequest t = createValidTicket();
        assertTrue(validator.isValid(t));
    }

    @Test
    public void testIsValid_nullTicket() {
        assertFalse(validator.isValid(null));
    }

    @Test
    public void testIsValid_invalidEnterpriseId() {
        TechnicalRequest t = createValidTicket();
        t.setEnterpriseId(0);
        assertFalse(validator.isValid(t));

        t.setEnterpriseId(-1);
        assertFalse(validator.isValid(t));
    }

    @Test
    public void testIsValid_invalidTicketType() {
        TechnicalRequest t = createValidTicket();

        t.setTicketType(null);
        assertFalse(validator.isValid(t));

        t.setTicketType("");
        assertFalse(validator.isValid(t));

        t.setTicketType("   ");
        assertFalse(validator.isValid(t));
    }

    @Test
    public void testIsValid_invalidPriority() {
        TechnicalRequest t = createValidTicket();

        t.setPriority(null);
        assertFalse(validator.isValid(t));

        t.setPriority("");
        assertFalse(validator.isValid(t));

        t.setPriority("Rất khẩn");
        assertFalse(validator.isValid(t));
    }

    @Test
    public void testIsValid_invalidReceptionChannel() {
        TechnicalRequest t = createValidTicket();

        t.setReceptionChannel(null);
        assertFalse(validator.isValid(t));

        t.setReceptionChannel("");
        assertFalse(validator.isValid(t));

        t.setReceptionChannel("   ");
        assertFalse(validator.isValid(t));
    }

    @Test
    public void testIsValid_invalidTechnicianId() {
        TechnicalRequest t = createValidTicket();
        t.setAssignedTechnicianId(0);
        assertFalse(validator.isValid(t));

        t.setAssignedTechnicianId(-5);
        assertFalse(validator.isValid(t));
    }

    @Test
    public void testIsValid_invalidDescription() {
        TechnicalRequest t = createValidTicket();

        t.setDescription(null);
        assertFalse(validator.isValid(t));

        t.setDescription("");
        assertFalse(validator.isValid(t));

        t.setDescription("   ");
        assertFalse(validator.isValid(t));
    }

    // -------------------------------------------------------------------------
    // 2. Tests cho BR-41 (Mức ưu tiên)
    // -------------------------------------------------------------------------
    @Test
    public void testIsAllowedPriority_validValues() {
        assertTrue(validator.isAllowedPriority(TicketValidator.PRIORITY_LOW));
        assertTrue(validator.isAllowedPriority(TicketValidator.PRIORITY_NORMAL));
        assertTrue(validator.isAllowedPriority(TicketValidator.PRIORITY_HIGH));
        assertTrue(validator.isAllowedPriority(TicketValidator.PRIORITY_URGENT));
        assertTrue(validator.isAllowedPriority("  Khẩn cấp  "));
    }

    @Test
    public void testIsAllowedPriority_invalidValues() {
        assertFalse(validator.isAllowedPriority(null));
        assertFalse(validator.isAllowedPriority(""));
        assertFalse(validator.isAllowedPriority("   "));
        assertFalse(validator.isAllowedPriority("Trung bình"));
        assertFalse(validator.isAllowedPriority("Normal"));
    }

    // -------------------------------------------------------------------------
    // 3. Tests cho BR-40 (Trạng thái và Luồng chuyển trạng thái)
    // -------------------------------------------------------------------------
    @Test
    public void testIsAllowedStatus_validValues() {
        assertTrue(validator.isAllowedStatus(TicketValidator.STATUS_NEW));
        assertTrue(validator.isAllowedStatus(TicketValidator.STATUS_IN_PROGRESS));
        assertTrue(validator.isAllowedStatus(TicketValidator.STATUS_CLOSED));
        assertTrue(validator.isAllowedStatus("  Đang xử lý  "));
    }

    @Test
    public void testIsAllowedStatus_invalidValues() {
        assertFalse(validator.isAllowedStatus(null));
        assertFalse(validator.isAllowedStatus(""));
        assertFalse(validator.isAllowedStatus("   "));
        assertFalse(validator.isAllowedStatus("Đã hủy"));
        assertFalse(validator.isAllowedStatus("Chờ xử lý"));
    }

    @Test
    public void testIsValidStatusTransition_validTransitions() {
        // Giữ nguyên trạng thái cũ
        assertTrue(validator.isValidStatusTransition(TicketValidator.STATUS_NEW, TicketValidator.STATUS_NEW));
        assertTrue(validator.isValidStatusTransition(TicketValidator.STATUS_IN_PROGRESS, TicketValidator.STATUS_IN_PROGRESS));
        assertTrue(validator.isValidStatusTransition(TicketValidator.STATUS_CLOSED, TicketValidator.STATUS_CLOSED));

        // Tiến trình hợp lệ: Mới tiếp nhận -> Đang xử lý -> Đã đóng
        assertTrue(validator.isValidStatusTransition(TicketValidator.STATUS_NEW, TicketValidator.STATUS_IN_PROGRESS));
        assertTrue(validator.isValidStatusTransition(TicketValidator.STATUS_IN_PROGRESS, TicketValidator.STATUS_CLOSED));
    }

    @Test
    public void testIsValidStatusTransition_invalidTransitions() {
        // Nhảy cóc: Mới tiếp nhận -> Đã đóng (MSG-051)
        assertFalse(validator.isValidStatusTransition(TicketValidator.STATUS_NEW, TicketValidator.STATUS_CLOSED));

        // Quay lui: Đang xử lý -> Mới tiếp nhận
        assertFalse(validator.isValidStatusTransition(TicketValidator.STATUS_IN_PROGRESS, TicketValidator.STATUS_NEW));

        // Mở lại sau khi đóng: Đã đóng -> Mới tiếp nhận / Đang xử lý
        assertFalse(validator.isValidStatusTransition(TicketValidator.STATUS_CLOSED, TicketValidator.STATUS_NEW));
        assertFalse(validator.isValidStatusTransition(TicketValidator.STATUS_CLOSED, TicketValidator.STATUS_IN_PROGRESS));

        // Null hoặc trạng thái không hợp lệ
        assertFalse(validator.isValidStatusTransition(null, TicketValidator.STATUS_IN_PROGRESS));
        assertFalse(validator.isValidStatusTransition(TicketValidator.STATUS_NEW, null));
        assertFalse(validator.isValidStatusTransition("Chờ xử lý", TicketValidator.STATUS_IN_PROGRESS));
    }

    // -------------------------------------------------------------------------
    // 4. Tests cho BR-43 (Cờ bảo hành)
    // -------------------------------------------------------------------------
    @Test
    public void testWarranty_defaultAndResolution() {
        assertTrue(validator.isWarrantyDefault());
        assertTrue(validator.resolveWarranty(null));
        assertTrue(validator.resolveWarranty(true));
        assertFalse(validator.resolveWarranty(false));
    }

    // -------------------------------------------------------------------------
    // 5. Tests cho validateMandatoryFields()
    // -------------------------------------------------------------------------
    @Test
    public void testValidateMandatoryFields_allValid() {
        TechnicalRequest t = createValidTicket();
        ValidationResult res = validator.validateMandatoryFields(t);
        assertTrue(res.isValid());
    }

    @Test
    public void testValidateMandatoryFields_nullTicket() {
        ValidationResult res = validator.validateMandatoryFields(null);
        assertFalse(res.isValid());
        assertEquals(TicketValidator.MSG_REQUIRED, res.getGlobalError());
    }

    @Test
    public void testValidateMandatoryFields_missingFields() {
        TechnicalRequest t = new TechnicalRequest();
        ValidationResult res = validator.validateMandatoryFields(t);
        assertFalse(res.isValid());
        assertEquals(TicketValidator.MSG_CUSTOMER_REQUIRED, res.getError(TicketValidator.FIELD_ENTERPRISE_ID));
        assertEquals(TicketValidator.MSG_TICKET_TYPE_REQUIRED, res.getError(TicketValidator.FIELD_TICKET_TYPE));
        assertEquals(TicketValidator.MSG_PRIORITY_REQUIRED, res.getError(TicketValidator.FIELD_PRIORITY));
        assertEquals(TicketValidator.MSG_CHANNEL_REQUIRED, res.getError(TicketValidator.FIELD_RECEPTION_CHANNEL));
        assertEquals(TicketValidator.MSG_TECHNICIAN_REQUIRED, res.getError(TicketValidator.FIELD_ASSIGNED_TECHNICIAN_ID));
        assertEquals(TicketValidator.MSG_DESCRIPTION_REQUIRED, res.getError(TicketValidator.FIELD_DESCRIPTION));
    }

    @Test
    public void testValidateMandatoryFields_invalidPriorityValue() {
        TechnicalRequest t = createValidTicket();
        t.setPriority("Không xác định");
        ValidationResult res = validator.validateMandatoryFields(t);
        assertFalse(res.isValid());
        assertEquals(TicketValidator.MSG_PRIORITY_INVALID, res.getError(TicketValidator.FIELD_PRIORITY));
    }

    @Test
    public void testValidateMandatoryFields_exceedMaxLength() {
        TechnicalRequest t = createValidTicket();
        t.setTicketType("A".repeat(TicketValidator.MAX_TICKET_TYPE_LENGTH + 1));
        t.setReceptionChannel("B".repeat(TicketValidator.MAX_RECEPTION_CHANNEL_LENGTH + 1));

        ValidationResult res = validator.validateMandatoryFields(t);
        assertFalse(res.isValid());
        assertNotNull(res.getError(TicketValidator.FIELD_TICKET_TYPE));
        assertNotNull(res.getError(TicketValidator.FIELD_RECEPTION_CHANNEL));
    }

    // -------------------------------------------------------------------------
    // 6. Tests cho UC-21 / validateCreate()
    // -------------------------------------------------------------------------
    @Test
    public void testValidateCreate_validWithDefaultStatusOrNull() {
        TechnicalRequest t = createValidTicket();
        t.setStatus(TicketValidator.STATUS_NEW);
        assertTrue(validator.validateCreate(t).isValid());

        t.setStatus(null);
        assertTrue(validator.validateCreate(t).isValid());
    }

    @Test
    public void testValidateCreate_rejectManualNonNewStatus() {
        // Trạng thái do hệ thống gán (mặc định Mới tiếp nhận); người dùng không được gán Đang xử lý/Đã đóng
        TechnicalRequest t = createValidTicket();
        t.setStatus(TicketValidator.STATUS_IN_PROGRESS);
        ValidationResult res = validator.validateCreate(t);
        assertFalse(res.isValid());
        assertEquals(TicketValidator.MSG_SYSTEM_ASSIGNED_FIELDS, res.getError(TicketValidator.FIELD_STATUS));

        t.setStatus(TicketValidator.STATUS_CLOSED);
        res = validator.validateCreate(t);
        assertFalse(res.isValid());
        assertEquals(TicketValidator.MSG_SYSTEM_ASSIGNED_FIELDS, res.getError(TicketValidator.FIELD_STATUS));
    }

    @Test
    public void testValidateCreate_futureCreatedDateRejected() {
        TechnicalRequest t = createValidTicket();
        t.setCreatedDate(Date.valueOf(LocalDate.now().plusDays(2)));
        ValidationResult res = validator.validateCreate(t);
        assertFalse(res.isValid());
        assertEquals(TicketValidator.MSG_CREATED_DATE_INVALID, res.getError(TicketValidator.FIELD_CREATED_DATE));
    }

    // -------------------------------------------------------------------------
    // 7. Tests cho UC-22 / validateUpdate()
    // -------------------------------------------------------------------------
    @Test
    public void testValidateUpdate_validTransition() {
        TechnicalRequest existing = createValidTicket();
        existing.setStatus(TicketValidator.STATUS_NEW);

        TechnicalRequest updating = createValidTicket();
        updating.setStatus(TicketValidator.STATUS_IN_PROGRESS);

        ValidationResult res = validator.validateUpdate(updating, existing);
        assertTrue(res.isValid());
    }

    @Test
    public void testValidateUpdate_invalidTransition() {
        TechnicalRequest existing = createValidTicket();
        existing.setStatus(TicketValidator.STATUS_NEW);

        TechnicalRequest updating = createValidTicket();
        updating.setStatus(TicketValidator.STATUS_CLOSED); // Chuyển nhảy cóc

        ValidationResult res = validator.validateUpdate(updating, existing);
        assertFalse(res.isValid());
        assertEquals(TicketValidator.MSG_051, res.getError(TicketValidator.FIELD_STATUS));
    }

    @Test
    public void testValidateUpdate_missingOrInvalidStatus() {
        TechnicalRequest existing = createValidTicket();
        TechnicalRequest updating = createValidTicket();

        updating.setStatus(null);
        ValidationResult res = validator.validateUpdate(updating, existing);
        assertFalse(res.isValid());
        assertEquals(TicketValidator.MSG_STATUS_REQUIRED, res.getError(TicketValidator.FIELD_STATUS));

        updating.setStatus("Trạng thái lạ");
        res = validator.validateUpdate(updating, existing);
        assertFalse(res.isValid());
        assertEquals(TicketValidator.MSG_STATUS_INVALID, res.getError(TicketValidator.FIELD_STATUS));
    }

    // -------------------------------------------------------------------------
    // 8. Tests cho vai Khách mua & Kiểm tra Hợp đồng thuộc khách hàng
    // -------------------------------------------------------------------------
    @Test
    public void testValidateBuyerRole() {
        ValidationResult res = new ValidationResult();
        assertTrue(validator.validateBuyerRole(res, List.of("Khách mua", "Nhà cung cấp")));
        assertTrue(res.isValid());

        res = new ValidationResult();
        assertFalse(validator.validateBuyerRole(res, List.of("Nhà cung cấp")));
        assertFalse(res.isValid());
        assertEquals(TicketValidator.MSG_NOT_BUYER, res.getError(TicketValidator.FIELD_ENTERPRISE_ID));

        res = new ValidationResult();
        assertFalse(validator.validateBuyerRole(res, null));
        assertFalse(res.isValid());
        assertEquals(TicketValidator.MSG_NOT_BUYER, res.getError(TicketValidator.FIELD_ENTERPRISE_ID));
    }

    @Test
    public void testValidateContractBelongsToCustomer() {
        ValidationResult res = new ValidationResult();
        // Không gắn hợp đồng -> luôn hợp lệ
        assertTrue(validator.validateContractBelongsToCustomer(res, 10, null));
        assertTrue(res.isValid());

        // Hợp đồng khớp khách hàng
        res = new ValidationResult();
        assertTrue(validator.validateContractBelongsToCustomer(res, 10, 10));
        assertTrue(res.isValid());

        // Hợp đồng không khớp khách hàng
        res = new ValidationResult();
        assertFalse(validator.validateContractBelongsToCustomer(res, 10, 99));
        assertFalse(res.isValid());
        assertEquals(TicketValidator.MSG_CONTRACT_MISMATCH, res.getError(TicketValidator.FIELD_CONTRACT_ID));
    }

    // -------------------------------------------------------------------------
    // 9. Tests cho BR-47 (Điều kiện xóa phiếu)
    // -------------------------------------------------------------------------
    @Test
    public void testCanDeleteAndValidateDelete() {
        // Cho phép xóa ở trạng thái Mới tiếp nhận hoặc Đã đóng
        assertTrue(validator.canDelete(TicketValidator.STATUS_NEW));
        assertTrue(validator.canDelete(TicketValidator.STATUS_CLOSED));

        ValidationResult res = new ValidationResult();
        assertTrue(validator.validateDelete(res, TicketValidator.STATUS_NEW));
        assertTrue(res.isValid());

        res = new ValidationResult();
        assertTrue(validator.validateDelete(res, TicketValidator.STATUS_CLOSED));
        assertTrue(res.isValid());

        // Cấm xóa ở trạng thái Đang xử lý
        assertFalse(validator.canDelete(TicketValidator.STATUS_IN_PROGRESS));
        res = new ValidationResult();
        assertFalse(validator.validateDelete(res, TicketValidator.STATUS_IN_PROGRESS));
        assertEquals(TicketValidator.MSG_CANNOT_DELETE_IN_PROGRESS, res.getGlobalError());

        // Trạng thái không hợp lệ
        assertFalse(validator.canDelete("Khác"));
        res = new ValidationResult();
        assertFalse(validator.validateDelete(res, "Khác"));
        assertEquals(TicketValidator.MSG_STATUS_INVALID, res.getGlobalError());
    }
}
