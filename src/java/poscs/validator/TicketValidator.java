package poscs.validator;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import poscs.model.TechnicalRequest;

/**
 * Validator cho module Phiếu hỗ trợ kỹ thuật (UC-21, UC-22).
 * Quy tắc:
 * - BR-39: Bắt buộc Khách hàng, Loại phiếu, Mức ưu tiên, Kênh tiếp nhận, Mô tả sự cố, Kỹ thuật viên.
 *          Trạng thái và Ngày tạo do hệ thống gán, không phải trường nhập.
 * - BR-40: Trạng thái hợp lệ và luồng chuyển: Mới tiếp nhận -> Đang xử lý -> Đã đóng (MSG-051).
 * - BR-41: Mức ưu tiên ∈ {Khẩn cấp, Cao, Bình thường, Thấp}.
 * - BR-43: Cờ bảo hành Có/Không, mặc định Có.
 * - BR-47: Xóa phiếu chỉ cho phép khi Mới tiếp nhận hoặc Đã đóng; không xóa Đang xử lý.
 */
public class TicketValidator extends BaseValidator {

    public static final String FIELD_ENTERPRISE_ID = "enterpriseId";
    public static final String FIELD_CONTRACT_ID = "contractId";
    public static final String FIELD_TICKET_TYPE = "ticketType";
    public static final String FIELD_PRIORITY = "priority";
    public static final String FIELD_RECEPTION_CHANNEL = "receptionChannel";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_ASSIGNED_TECHNICIAN_ID = "assignedTechnicianId";
    public static final String FIELD_IS_WARRANTY = "isWarranty";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_CREATED_DATE = "createdDate";
    public static final String FIELD_CAUSE_CATEGORY = "causeCategory";

    // Giới hạn độ dài theo db/schema.sql
    public static final int MAX_TICKET_CODE_LENGTH = 50;
    public static final int MAX_TICKET_TYPE_LENGTH = 100;
    public static final int MAX_PRIORITY_LENGTH = 50;
    public static final int MAX_RECEPTION_CHANNEL_LENGTH = 50;
    public static final int MAX_CAUSE_CATEGORY_LENGTH = 100;
    public static final int MAX_STATUS_LENGTH = 50;

    // BR-41: Mức ưu tiên
    public static final String PRIORITY_URGENT = "Khẩn cấp";
    public static final String PRIORITY_HIGH = "Cao";
    public static final String PRIORITY_NORMAL = "Bình thường";
    public static final String PRIORITY_LOW = "Thấp";
    public static final Set<String> ALLOWED_PRIORITIES = Set.of(
            PRIORITY_URGENT, PRIORITY_HIGH, PRIORITY_NORMAL, PRIORITY_LOW
    );

    // BR-40: Trạng thái phiếu
    public static final String STATUS_NEW = "Mới tiếp nhận";
    public static final String STATUS_IN_PROGRESS = "Đang xử lý";
    public static final String STATUS_CLOSED = "Đã đóng";
    public static final Set<String> ALLOWED_STATUSES = Set.of(
            STATUS_NEW, STATUS_IN_PROGRESS, STATUS_CLOSED
    );

    // Loại phiếu và Kênh tiếp nhận chuẩn
    public static final Set<String> ALLOWED_TICKET_TYPES = Set.of(
            "Bảo hành", "Bảo trì", "Sửa chữa", "Tư vấn", "Khác"
    );
    public static final Set<String> ALLOWED_RECEPTION_CHANNELS = Set.of(
            "Điện thoại", "Email", "Trực tiếp", "Website"
    );
    public static final Set<String> ALLOWED_CAUSE_CATEGORIES = Set.of(
            "Do vận chuyển", "Do lắp đặt", "Do thiết bị", "Khác"
    );

    public static final String ROLE_BUYER = "Khách mua";

    // Thông báo lỗi chuẩn theo SPEC
    public static final String MSG_REQUIRED = "Vui lòng nhập đầy đủ các trường bắt buộc.";
    public static final String MSG_CUSTOMER_REQUIRED = "Vui lòng chọn khách hàng.";
    public static final String MSG_TICKET_TYPE_REQUIRED = "Vui lòng chọn loại phiếu.";
    public static final String MSG_PRIORITY_REQUIRED = "Vui lòng chọn mức ưu tiên.";
    public static final String MSG_PRIORITY_INVALID = "Mức ưu tiên không hợp lệ.";
    public static final String MSG_CHANNEL_REQUIRED = "Vui lòng chọn kênh tiếp nhận.";
    public static final String MSG_TECHNICIAN_REQUIRED = "Vui lòng chọn kỹ thuật viên phụ trách.";
    public static final String MSG_DESCRIPTION_REQUIRED = "Vui lòng mô tả sự cố.";
    public static final String MSG_STATUS_REQUIRED = "Trạng thái phiếu không được để trống.";
    public static final String MSG_STATUS_INVALID = "Trạng thái phiếu không hợp lệ.";
    public static final String MSG_051 = "Trạng thái chuyển không hợp lệ.";
    public static final String MSG_STATUS_TRANSITION_INVALID = MSG_051;
    public static final String MSG_NOT_BUYER = "Phiếu hỗ trợ chỉ lập cho khách hàng mua. Vui lòng chọn lại khách hàng.";
    public static final String MSG_CONTRACT_MISMATCH = "Hợp đồng đã chọn không thuộc về khách hàng của phiếu này. Vui lòng chọn lại hợp đồng, hoặc bỏ gắn hợp đồng.";
    public static final String MSG_SYSTEM_ASSIGNED_FIELDS = "Trạng thái và Ngày tạo do hệ thống gán, không phải trường nhập.";
    public static final String MSG_CANNOT_DELETE_IN_PROGRESS = "Không xoá được phiếu này vì phiếu đang có người xử lý (trạng thái Đang xử lý). Chỉ xoá được phiếu Mới tiếp nhận hoặc Đã đóng.";
    public static final String MSG_CREATED_DATE_INVALID = "Ngày nhập vào không hợp lệ.";

    /**
     * BR-39 & BR-41: Kiểm tra nhanh tính hợp lệ của phiếu, tương thích ngược với TechnicalSupportTicketController.
     */
    public boolean isValid(TechnicalRequest t) {
        if (t == null) return false;
        return t.getEnterpriseId() > 0
                && !isBlank(t.getTicketType())
                && isAllowedPriority(t.getPriority())
                && !isBlank(t.getReceptionChannel())
                && t.getAssignedTechnicianId() > 0
                && !isBlank(t.getDescription());
    }

    /**
     * BR-41: Kiểm tra mức ưu tiên hợp lệ.
     */
    public boolean isAllowedPriority(String priority) {
        return priority != null && ALLOWED_PRIORITIES.contains(priority.trim());
    }

    /**
     * BR-40: Kiểm tra trạng thái phiếu hợp lệ.
     */
    public boolean isAllowedStatus(String status) {
        return status != null && ALLOWED_STATUSES.contains(status.trim());
    }

    /**
     * BR-43: Giá trị mặc định của cờ bảo hành là true (Có).
     */
    public boolean isWarrantyDefault() {
        return true;
    }

    /**
     * BR-43: Giải quyết giá trị cờ bảo hành: nếu null thì nhận mặc định Có (true).
     */
    public boolean resolveWarranty(Boolean isWarranty) {
        return isWarranty == null ? isWarrantyDefault() : isWarranty;
    }

    /**
     * BR-40: Kiểm tra luồng chuyển trạng thái hợp lệ:
     * Giữ nguyên trạng thái cũ HOẶC chuyển tiếp: Mới tiếp nhận -> Đang xử lý -> Đã đóng.
     */
    public boolean isValidStatusTransition(String fromStatus, String toStatus) {
        if (fromStatus == null || toStatus == null) return false;
        String from = fromStatus.trim();
        String to = toStatus.trim();
        if (!isAllowedStatus(from) || !isAllowedStatus(to)) return false;
        if (from.equals(to)) return true;
        if (STATUS_NEW.equals(from) && STATUS_IN_PROGRESS.equals(to)) return true;
        if (STATUS_IN_PROGRESS.equals(from) && STATUS_CLOSED.equals(to)) return true;
        return false;
    }

    /**
     * BR-39: Kiểm tra các trường bắt buộc cơ bản khi tạo hoặc cập nhật phiếu.
     */
    public ValidationResult validateMandatoryFields(TechnicalRequest t) {
        ValidationResult result = new ValidationResult();
        if (t == null) {
            result.addGlobalError(MSG_REQUIRED);
            return result;
        }
        if (t.getEnterpriseId() <= 0) {
            result.addError(FIELD_ENTERPRISE_ID, MSG_CUSTOMER_REQUIRED);
        }
        if (isBlank(t.getTicketType())) {
            result.addError(FIELD_TICKET_TYPE, MSG_TICKET_TYPE_REQUIRED);
        } else {
            maxLength(result, FIELD_TICKET_TYPE, t.getTicketType().trim(), MAX_TICKET_TYPE_LENGTH, MSG_REQUIRED);
        }
        if (isBlank(t.getPriority())) {
            result.addError(FIELD_PRIORITY, MSG_PRIORITY_REQUIRED);
        } else if (!isAllowedPriority(t.getPriority())) {
            result.addError(FIELD_PRIORITY, MSG_PRIORITY_INVALID);
        }
        if (isBlank(t.getReceptionChannel())) {
            result.addError(FIELD_RECEPTION_CHANNEL, MSG_CHANNEL_REQUIRED);
        } else {
            maxLength(result, FIELD_RECEPTION_CHANNEL, t.getReceptionChannel().trim(), MAX_RECEPTION_CHANNEL_LENGTH, MSG_REQUIRED);
        }
        if (t.getAssignedTechnicianId() <= 0) {
            result.addError(FIELD_ASSIGNED_TECHNICIAN_ID, MSG_TECHNICIAN_REQUIRED);
        }
        if (isBlank(t.getDescription())) {
            result.addError(FIELD_DESCRIPTION, MSG_DESCRIPTION_REQUIRED);
        }
        return result;
    }

    /**
     * UC-21 / BR-39: Validate khi tạo mới phiếu.
     * Trạng thái và Ngày tạo do hệ thống gán (mặc định Mới tiếp nhận và ngày hôm nay).
     */
    public ValidationResult validateCreate(TechnicalRequest t) {
        ValidationResult result = validateMandatoryFields(t);
        if (t == null) return result;

        // Trạng thái khi tạo phải là null (chưa gán) hoặc đúng trạng thái hệ thống gán (STATUS_NEW)
        if (!isBlank(t.getStatus()) && !STATUS_NEW.equals(t.getStatus().trim())) {
            result.addError(FIELD_STATUS, MSG_SYSTEM_ASSIGNED_FIELDS);
        }
        // Ngày tạo nếu truyền vào không được là ngày trong tương lai
        if (t.getCreatedDate() != null && t.getCreatedDate().toLocalDate().isAfter(LocalDate.now())) {
            result.addError(FIELD_CREATED_DATE, MSG_CREATED_DATE_INVALID);
        }
        if (!isBlank(t.getCauseCategory())) {
            maxLength(result, FIELD_CAUSE_CATEGORY, t.getCauseCategory().trim(), MAX_CAUSE_CATEGORY_LENGTH, MSG_REQUIRED);
        }
        return result;
    }

    /**
     * UC-22 / BR-40: Validate khi cập nhật phiếu.
     */
    public ValidationResult validateUpdate(TechnicalRequest t, TechnicalRequest existing) {
        ValidationResult result = validateMandatoryFields(t);
        if (t == null) return result;

        if (isBlank(t.getStatus())) {
            result.addError(FIELD_STATUS, MSG_STATUS_REQUIRED);
        } else if (!isAllowedStatus(t.getStatus())) {
            result.addError(FIELD_STATUS, MSG_STATUS_INVALID);
        } else if (existing != null && !isBlank(existing.getStatus())) {
            if (!isValidStatusTransition(existing.getStatus(), t.getStatus())) {
                result.addError(FIELD_STATUS, MSG_051);
            }
        }
        if (!isBlank(t.getCauseCategory())) {
            maxLength(result, FIELD_CAUSE_CATEGORY, t.getCauseCategory().trim(), MAX_CAUSE_CATEGORY_LENGTH, MSG_REQUIRED);
        }
        return result;
    }

    /**
     * Kiểm tra đối tác có vai trò "Khách mua" hay không.
     */
    public boolean validateBuyerRole(ValidationResult result, Collection<String> enterpriseRoles) {
        if (enterpriseRoles == null || !enterpriseRoles.contains(ROLE_BUYER)) {
            result.addError(FIELD_ENTERPRISE_ID, MSG_NOT_BUYER);
            return false;
        }
        return true;
    }

    /**
     * Kiểm tra hợp đồng liên quan có thuộc đúng khách hàng của phiếu hay không.
     */
    public boolean validateContractBelongsToCustomer(ValidationResult result, Integer ticketEnterpriseId,
            Integer contractEnterpriseId) {
        if (contractEnterpriseId != null) {
            if (ticketEnterpriseId == null || !ticketEnterpriseId.equals(contractEnterpriseId)) {
                result.addError(FIELD_CONTRACT_ID, MSG_CONTRACT_MISMATCH);
                return false;
            }
        }
        return true;
    }

    /**
     * BR-47: Kiểm tra phiếu có được phép xóa hay không (chỉ Mới tiếp nhận hoặc Đã đóng).
     */
    public boolean canDelete(String status) {
        return isAllowedStatus(status) && !STATUS_IN_PROGRESS.equals(status.trim());
    }

    /**
     * BR-47: Validate thao tác xóa phiếu.
     */
    public boolean validateDelete(ValidationResult result, String status) {
        if (STATUS_IN_PROGRESS.equals(status != null ? status.trim() : "")) {
            result.addGlobalError(MSG_CANNOT_DELETE_IN_PROGRESS);
            return false;
        }
        if (!isAllowedStatus(status)) {
            result.addGlobalError(MSG_STATUS_INVALID);
            return false;
        }
        return true;
    }
}
