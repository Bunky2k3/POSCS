package poscs.validator;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import poscs.model.Contract;

/**
 * Validator cho module Hợp đồng (UC-33, UC-35, UC-44).
 * Quy tắc: BR-17 (trạng thái tự tính), BR-35 (trường bắt buộc, đối tác đúng vai, ngày ký do hệ thống đóng dấu),
 * BR-36 & MSG-045 (thời hạn), UC-35 (giảm trừ phụ lục, khóa HĐ thanh lý/chấm dứt, HĐ đã ký chỉ đổi owner).
 */
public class ContractValidator extends BaseValidator {

    public static final String FIELD_CONTRACT_CODE = "contractCode";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_CONTRACT_TYPE = "contractType";
    public static final String FIELD_DIRECTION = "direction";
    public static final String FIELD_ENTERPRISE_ID = "enterpriseId";
    public static final String FIELD_OWNER_ID = "ownerId";
    public static final String FIELD_SIGNING_DATE = "signingDate";
    public static final String FIELD_EFFECTIVE_DATE = "effectiveDate";
    public static final String FIELD_END_DATE = "endDate";
    public static final String FIELD_CONTRACT_VALUE = "contractValue";
    public static final String FIELD_PROGRESS_STATUS = "progressStatus";

    public static final int MAX_CONTRACT_CODE_LENGTH = 50;
    public static final int MAX_TITLE_LENGTH = 255;
    public static final int MAX_CONTRACT_TYPE_LENGTH = 100;
    public static final int MAX_SIGNER_NAME_LENGTH = 100;
    public static final int MAX_SIGNER_POSITION_LENGTH = 100;
    public static final int MAX_COUNTERPARTY_SIGNER_NAME_LENGTH = 100;
    public static final int MAX_COUNTERPARTY_SIGNER_POSITION_LENGTH = 100;
    public static final int MAX_AUTHORIZATION_REF_LENGTH = 255;
    public static final int MAX_SIGNING_PLACE_LENGTH = 255;

    public static final String DIRECTION_SELL = "Bán";
    public static final String DIRECTION_BUY = "Mua";
    public static final String ROLE_BUYER = "Khách mua";
    public static final String ROLE_SUPPLIER = "Nhà cung cấp";

    // BR-17: Trục Lịch
    public static final String STATUS_DRAFT = "Chưa hiệu lực";
    public static final String STATUS_ACTIVE = "Đang hiệu lực";
    public static final String STATUS_SOON = "Sắp hết hạn";
    public static final String STATUS_EXPIRED = "Đã hết hạn";
    public static final int SOON_THRESHOLD_DAYS = 30;

    // Trục Tiến độ
    public static final String PROGRESS_DRAFT = "Nháp";
    public static final String PROGRESS_SIGNED = "Đã ký";
    public static final String PROGRESS_LIQUIDATED = "Đã thanh lý";
    public static final String PROGRESS_TERMINATED = "Chấm dứt sớm";

    public static final List<String> SELL_CONTRACT_TYPES = List.of(
            "Cung cấp thiết bị", "Thi công lắp đặt", "Bảo trì bảo dưỡng");
    public static final List<String> BUY_CONTRACT_TYPES = List.of(
            "Mua thiết bị", "Mua vật tư", "Thuê thi công lắp đặt", "Thuê bảo trì");

    // Thông báo lỗi SPEC
    public static final String MSG_045 = "Điền cả hai mốc, và ngày hiệu lực phải trước hoặc bằng ngày kết thúc.";
    public static final String MSG_TERM_INVALID = MSG_045;
    public static final String MSG_CONTRACT_INVALID = "Thông tin hợp đồng chưa hợp lệ. Vui lòng kiểm tra lại các ô bắt buộc.";
    public static final String MSG_CODE_REQUIRED = "Mã hợp đồng không được để trống.";
    public static final String MSG_TITLE_REQUIRED = "Tiêu đề hợp đồng không được để trống.";
    public static final String MSG_TYPE_REQUIRED = "Vui lòng chọn loại hợp đồng.";
    public static final String MSG_CUSTOMER_REQUIRED = "Vui lòng chọn khách hàng hoặc nhà cung cấp.";
    public static final String MSG_OWNER_REQUIRED = "Vui lòng chọn người phụ trách hợp đồng.";
    public static final String MSG_DIRECTION_REQUIRED = "Vui lòng chọn chiều hợp đồng (Bán/Mua).";
    public static final String MSG_COUNTERPARTY_ROLE_MISMATCH = "Đối tác không đúng vai trò cho chiều hợp đồng này.";
    public static final String MSG_DIRECTION_IMMUTABLE = "Chiều hợp đồng (Bán/Mua) không thể thay đổi.";
    public static final String MSG_SIGNING_DATE_SYSTEM_GENERATED = "Ngày ký không phải trường nhập tay; hệ thống tự động đóng dấu khi ký hợp đồng.";
    public static final String MSG_SIGNING_DATE_IMMUTABLE = "Ngày ký của hợp đồng không thể thay đổi.";
    public static final String MSG_EXCEEDS_REMAINING_VALUE = "Khoản giảm trừ phụ lục không được vượt quá giá trị còn lại của hợp đồng gốc.";
    public static final String MSG_CANNOT_EDIT_LOCKED = "Hợp đồng đã thanh lý hoặc chấm dứt sớm, không thể chỉnh sửa.";
    public static final String MSG_SIGNED_EDIT_RESTRICTED = "Hợp đồng đã ký chỉ được phép thay đổi Người phụ trách.";
    public static final String MSG_SIGNING_AFTER_EFFECTIVE = "Ngày ký phải trước hoặc bằng ngày hiệu lực.";
    public static final String MSG_SIGNING_AFTER_END = "Ngày ký phải trước hoặc bằng ngày kết thúc.";
    public static final String MSG_ONLY_DRAFT_CAN_SIGN = "Chỉ hợp đồng ở tiến độ Nháp mới có thể ký.";

    // BR-35: Vai trò đối tác theo chiều hợp đồng (cặp đôi chéo)
    public static String counterpartyRoleFor(String direction) {
        return DIRECTION_BUY.equalsIgnoreCase(direction) ? ROLE_SUPPLIER : ROLE_BUYER;
    }

    public boolean isCounterpartyMatchingDirection(String direction, Collection<String> enterpriseRoles) {
        if (isBlank(direction) || enterpriseRoles == null) return false;
        return enterpriseRoles.contains(counterpartyRoleFor(direction.trim()));
    }

    public boolean validateCounterpartyRole(ValidationResult result, String direction, Collection<String> enterpriseRoles) {
        if (!isCounterpartyMatchingDirection(direction, enterpriseRoles)) {
            result.addError(FIELD_ENTERPRISE_ID, MSG_COUNTERPARTY_ROLE_MISMATCH);
            return false;
        }
        return true;
    }

    public static List<String> contractTypesFor(String direction) {
        return DIRECTION_BUY.equalsIgnoreCase(direction) ? BUY_CONTRACT_TYPES : SELL_CONTRACT_TYPES;
    }

    public boolean isValidContractType(String contractType, String direction) {
        if (isBlank(contractType) || isBlank(direction)) return false;
        return contractTypesFor(direction).contains(contractType.trim());
    }

    // BR-17: Tính trạng thái tự động theo ngày hiệu lực / ngày kết thúc
    public static String computeStatus(LocalDate effectiveDate, LocalDate endDate, LocalDate asOfDate) {
        if (effectiveDate == null || endDate == null) return STATUS_DRAFT;
        LocalDate today = asOfDate != null ? asOfDate : LocalDate.now();
        if (today.isBefore(effectiveDate)) return STATUS_DRAFT;
        if (today.isAfter(endDate)) return STATUS_EXPIRED;
        return ChronoUnit.DAYS.between(today, endDate) <= SOON_THRESHOLD_DAYS ? STATUS_SOON : STATUS_ACTIVE;
    }

    public static String computeStatus(LocalDate effectiveDate, LocalDate endDate) {
        return computeStatus(effectiveDate, endDate, LocalDate.now());
    }

    public static String computeStatus(Date effectiveDate, Date endDate, LocalDate asOfDate) {
        return computeStatus(effectiveDate != null ? effectiveDate.toLocalDate() : null,
                endDate != null ? endDate.toLocalDate() : null, asOfDate);
    }

    public static String computeStatus(Date effectiveDate, Date endDate) {
        return computeStatus(effectiveDate, endDate, LocalDate.now());
    }

    // BR-36 & MSG-045: Kiểm tra thứ tự Ngày ký <= Ngày hiệu lực <= Ngày kết thúc
    public boolean validateDates(ValidationResult result, LocalDate signingDate, LocalDate effectiveDate, LocalDate endDate) {
        boolean valid = true;
        if (effectiveDate == null && endDate != null) {
            result.addError(FIELD_EFFECTIVE_DATE, MSG_045);
            valid = false;
        } else if (effectiveDate != null && endDate == null) {
            result.addError(FIELD_END_DATE, MSG_045);
            valid = false;
        } else if (effectiveDate != null && endDate != null && effectiveDate.isAfter(endDate)) {
            result.addError(FIELD_EFFECTIVE_DATE, MSG_045);
            valid = false;
        }

        if (signingDate != null) {
            if (effectiveDate != null && signingDate.isAfter(effectiveDate)) {
                result.addError(FIELD_SIGNING_DATE, MSG_SIGNING_AFTER_EFFECTIVE);
                valid = false;
            }
            if (endDate != null && signingDate.isAfter(endDate)) {
                result.addError(FIELD_SIGNING_DATE, MSG_SIGNING_AFTER_END);
                valid = false;
            }
        }
        return valid;
    }

    public boolean validateDates(ValidationResult result, Date signingDate, Date effectiveDate, Date endDate) {
        return validateDates(result,
                signingDate != null ? signingDate.toLocalDate() : null,
                effectiveDate != null ? effectiveDate.toLocalDate() : null,
                endDate != null ? endDate.toLocalDate() : null);
    }

    public boolean isValidDates(LocalDate signingDate, LocalDate effectiveDate, LocalDate endDate) {
        ValidationResult vr = new ValidationResult();
        validateDates(vr, signingDate, effectiveDate, endDate);
        return vr.isValid();
    }

    public boolean isValidDates(Date signingDate, Date effectiveDate, Date endDate) {
        ValidationResult vr = new ValidationResult();
        validateDates(vr, signingDate, effectiveDate, endDate);
        return vr.isValid();
    }

    // Tương thích ngược với ContractController.isValid
    public boolean isValid(Contract c) {
        if (c == null) return false;
        if (isBlank(c.getContractCode()) || isBlank(c.getTitle()) || isBlank(c.getContractType())
                || c.getEnterpriseId() <= 0 || c.getOwnerId() <= 0) return false;
        if (c.getSigningDate() != null && c.getEffectiveDate() != null
                && c.getSigningDate().after(c.getEffectiveDate())) return false;
        if (c.getEffectiveDate() == null && c.getEndDate() == null) return true;
        if (c.getEffectiveDate() == null || c.getEndDate() == null) return false;
        return !c.getEffectiveDate().after(c.getEndDate());
    }

    // UC-35: Kiểm tra khoản giảm trừ phụ lục không vượt giá trị còn lại của HĐ gốc
    public boolean exceedsRemainingValue(BigDecimal remainingValue, BigDecimal delta) {
        if (delta == null || delta.signum() >= 0) return false;
        BigDecimal base = remainingValue == null ? BigDecimal.ZERO : remainingValue;
        return base.add(delta).signum() < 0;
    }

    public boolean exceedsRemainingValue(Contract parent, BigDecimal delta) {
        if (parent == null || delta == null || delta.signum() >= 0) return false;
        BigDecimal current = parent.getCurrentValue();
        return (current == null ? BigDecimal.ZERO : current).add(delta).signum() < 0;
    }

    public boolean validateAmendmentReduction(ValidationResult result, BigDecimal remainingValue, BigDecimal delta) {
        if (exceedsRemainingValue(remainingValue, delta)) {
            result.addError(FIELD_CONTRACT_VALUE, MSG_EXCEEDS_REMAINING_VALUE);
            return false;
        }
        return true;
    }

    public boolean validateAmendmentReduction(ValidationResult result, Contract parent, BigDecimal delta) {
        if (exceedsRemainingValue(parent, delta)) {
            result.addError(FIELD_CONTRACT_VALUE, MSG_EXCEEDS_REMAINING_VALUE);
            return false;
        }
        return true;
    }

    // BR-35: Kiểm tra trường bắt buộc và giới hạn độ dài
    public ValidationResult validateRequiredFields(Contract c) {
        ValidationResult result = new ValidationResult();
        if (c == null) {
            result.addGlobalError(MSG_CONTRACT_INVALID);
            return result;
        }

        if (required(result, FIELD_CONTRACT_CODE, c.getContractCode(), MSG_CODE_REQUIRED)) {
            maxLength(result, FIELD_CONTRACT_CODE, c.getContractCode(), MAX_CONTRACT_CODE_LENGTH,
                    "Mã hợp đồng không được vượt quá " + MAX_CONTRACT_CODE_LENGTH + " ký tự.");
        }
        if (required(result, FIELD_TITLE, c.getTitle(), MSG_TITLE_REQUIRED)) {
            maxLength(result, FIELD_TITLE, c.getTitle(), MAX_TITLE_LENGTH,
                    "Tiêu đề hợp đồng không được vượt quá " + MAX_TITLE_LENGTH + " ký tự.");
        }
        if (required(result, FIELD_CONTRACT_TYPE, c.getContractType(), MSG_TYPE_REQUIRED)) {
            maxLength(result, FIELD_CONTRACT_TYPE, c.getContractType(), MAX_CONTRACT_TYPE_LENGTH,
                    "Loại hợp đồng không được vượt quá " + MAX_CONTRACT_TYPE_LENGTH + " ký tự.");
        }
        if (c.getEnterpriseId() <= 0) result.addError(FIELD_ENTERPRISE_ID, MSG_CUSTOMER_REQUIRED);
        if (c.getOwnerId() <= 0) result.addError(FIELD_OWNER_ID, MSG_OWNER_REQUIRED);

        if (isBlank(c.getDirection())) {
            result.addError(FIELD_DIRECTION, MSG_DIRECTION_REQUIRED);
        } else if (!DIRECTION_SELL.equalsIgnoreCase(c.getDirection().trim())
                && !DIRECTION_BUY.equalsIgnoreCase(c.getDirection().trim())) {
            result.addError(FIELD_DIRECTION, "Chiều hợp đồng phải là 'Bán' hoặc 'Mua'.");
        }

        // Kiểm tra loại hợp đồng phù hợp với chiều
        if (!result.getErrors().containsKey(FIELD_CONTRACT_TYPE)
                && !result.getErrors().containsKey(FIELD_DIRECTION)
                && !isValidContractType(c.getContractType(), c.getDirection())) {
            result.addError(FIELD_CONTRACT_TYPE, MSG_TYPE_REQUIRED);
        }

        maxLength(result, "signerName", c.getSignerName(), MAX_SIGNER_NAME_LENGTH,
                "Tên người ký không được vượt quá " + MAX_SIGNER_NAME_LENGTH + " ký tự.");
        maxLength(result, "signerPosition", c.getSignerPosition(), MAX_SIGNER_POSITION_LENGTH,
                "Chức vụ người ký không được vượt quá " + MAX_SIGNER_POSITION_LENGTH + " ký tự.");
        maxLength(result, "counterpartySignerName", c.getCounterpartySignerName(), MAX_COUNTERPARTY_SIGNER_NAME_LENGTH,
                "Tên người ký đối tác không được vượt quá " + MAX_COUNTERPARTY_SIGNER_NAME_LENGTH + " ký tự.");
        maxLength(result, "counterpartySignerPosition", c.getCounterpartySignerPosition(), MAX_COUNTERPARTY_SIGNER_POSITION_LENGTH,
                "Chức vụ người ký đối tác không được vượt quá " + MAX_COUNTERPARTY_SIGNER_POSITION_LENGTH + " ký tự.");
        maxLength(result, "authorizationRef", c.getAuthorizationRef(), MAX_AUTHORIZATION_REF_LENGTH,
                "Số ủy quyền không được vượt quá " + MAX_AUTHORIZATION_REF_LENGTH + " ký tự.");
        maxLength(result, "signingPlace", c.getSigningPlace(), MAX_SIGNING_PLACE_LENGTH,
                "Nơi ký không được vượt quá " + MAX_SIGNING_PLACE_LENGTH + " ký tự.");

        // Giá trị hợp đồng: không âm đối với hợp đồng gốc (phụ lục được phép mang giá trị âm khi giảm trừ)
        if (c.getContractValue() != null && !c.isAmendment()) {
            nonNegative(result, FIELD_CONTRACT_VALUE, c.getContractValue(), "Giá trị hợp đồng không được là số âm.");
        }
        return result;
    }

    // UC-33: Kiểm tra khi tạo hợp đồng
    public ValidationResult validateCreate(Contract c, Collection<String> enterpriseRoles) {
        ValidationResult result = validateRequiredFields(c);
        if (c == null) return result;

        // BR-35: Kiểm tra đối tác đúng vai (fail-closed)
        if (c.getEnterpriseId() > 0 && !result.getErrors().containsKey(FIELD_DIRECTION)) {
            validateCounterpartyRole(result, c.getDirection(), enterpriseRoles);
        }
        // BR-35: Ngày ký không phải trường nhập tay
        if (c.getSigningDate() != null) {
            result.addError(FIELD_SIGNING_DATE, MSG_SIGNING_DATE_SYSTEM_GENERATED);
        }
        // BR-36 & MSG-045: Kiểm tra thời hạn nếu có điền
        if (c.getEffectiveDate() != null || c.getEndDate() != null) {
            validateDates(result, c.getSigningDate(), c.getEffectiveDate(), c.getEndDate());
        }
        return result;
    }

    // UC-35: Kiểm tra khi cập nhật hợp đồng
    public ValidationResult validateUpdate(Contract c, Contract existing, Collection<String> enterpriseRoles) {
        ValidationResult result = new ValidationResult();
        if (c == null) {
            result.addGlobalError(MSG_CONTRACT_INVALID);
            return result;
        }
        if (existing == null) {
            result.addGlobalError("Không tìm thấy hợp đồng gốc cần cập nhật.");
            return result;
        }

        // Hợp đồng đã thanh lý / chấm dứt sớm không được sửa
        String existingProgress = existing.getProgressStatus();
        if (PROGRESS_LIQUIDATED.equals(existingProgress) || PROGRESS_TERMINATED.equals(existingProgress)) {
            result.addError(FIELD_PROGRESS_STATUS, MSG_CANNOT_EDIT_LOCKED);
            return result;
        }

        // Chiều Bán/Mua không đổi được
        if (existing.getDirection() != null
                && (isBlank(c.getDirection()) || !c.getDirection().trim().equalsIgnoreCase(existing.getDirection().trim()))) {
            result.addError(FIELD_DIRECTION, MSG_DIRECTION_IMMUTABLE);
        }

        // Ngày ký không đổi được (kể cả xóa trắng ngày ký sau khi đã ký)
        if (existing.getSigningDate() == null && c.getSigningDate() != null) {
            result.addError(FIELD_SIGNING_DATE, MSG_SIGNING_DATE_SYSTEM_GENERATED);
        } else if (existing.getSigningDate() != null && !existing.getSigningDate().equals(c.getSigningDate())) {
            result.addError(FIELD_SIGNING_DATE, MSG_SIGNING_DATE_IMMUTABLE);
        }

        // Hợp đồng đã ký chỉ được đổi Người phụ trách
        if (PROGRESS_SIGNED.equals(existingProgress)) {
            if (c.getOwnerId() <= 0) result.addError(FIELD_OWNER_ID, MSG_OWNER_REQUIRED);
            if (hasRestrictedFieldsChanged(c, existing)) {
                result.addError(ValidationResult.GLOBAL_KEY, MSG_SIGNED_EDIT_RESTRICTED);
            }
            return result;
        }

        // Nháp: sửa mọi ô kể cả thời hạn
        result.merge(validateRequiredFields(c));
        if (c.getEnterpriseId() > 0 && !result.getErrors().containsKey(FIELD_DIRECTION)) {
            validateCounterpartyRole(result, c.getDirection(), enterpriseRoles);
        }
        LocalDate signing = c.getSigningDate() != null ? c.getSigningDate().toLocalDate() : null;
        LocalDate effective = c.getEffectiveDate() != null ? c.getEffectiveDate().toLocalDate() : null;
        LocalDate end = c.getEndDate() != null ? c.getEndDate().toLocalDate() : null;
        validateDates(result, signing, effective, end);
        return result;
    }

    public boolean hasRestrictedFieldsChanged(Contract c, Contract existing) {
        if (c == null || existing == null) return false;
        return !isSameString(c.getContractCode(), existing.getContractCode())
                || !isSameString(c.getTitle(), existing.getTitle())
                || !isSameString(c.getContractType(), existing.getContractType())
                || c.getEnterpriseId() != existing.getEnterpriseId()
                || !Objects.equals(c.getEffectiveDate(), existing.getEffectiveDate())
                || !Objects.equals(c.getEndDate(), existing.getEndDate())
                || !isSameBigDecimal(c.getContractValue(), existing.getContractValue())
                || !isSameString(c.getSignerName(), existing.getSignerName())
                || !isSameString(c.getSignerPosition(), existing.getSignerPosition())
                || !isSameString(c.getCounterpartySignerName(), existing.getCounterpartySignerName())
                || !isSameString(c.getCounterpartySignerPosition(), existing.getCounterpartySignerPosition())
                || !isSameString(c.getAuthorizationRef(), existing.getAuthorizationRef())
                || !isSameString(c.getSigningPlace(), existing.getSigningPlace());
    }

    // UC-44: Ký hợp đồng
    public ValidationResult validateSigning(Contract c, LocalDate signingDate) {
        ValidationResult result = new ValidationResult();
        if (c == null) {
            result.addGlobalError(MSG_CONTRACT_INVALID);
            return result;
        }
        if (signingDate == null) {
            result.addError(FIELD_SIGNING_DATE, "Ngày ký không được để trống.");
            return result;
        }
        if (!PROGRESS_DRAFT.equals(c.getProgressStatus())) {
            result.addError(FIELD_PROGRESS_STATUS, MSG_ONLY_DRAFT_CAN_SIGN);
        }
        if (c.getEffectiveDate() == null || c.getEndDate() == null) {
            result.addError(FIELD_EFFECTIVE_DATE, MSG_045);
        } else {
            validateDates(result, signingDate, c.getEffectiveDate().toLocalDate(), c.getEndDate().toLocalDate());
        }
        return result;
    }

    public ValidationResult validateSigning(Contract c, Date signingDate) {
        return validateSigning(c, signingDate != null ? signingDate.toLocalDate() : null);
    }

    private static boolean isSameString(String a, String b) {
        return (a == null ? "" : a.trim()).equals(b == null ? "" : b.trim());
    }

    private static boolean isSameBigDecimal(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }
}
