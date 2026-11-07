package poscs.validator;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import poscs.common.TextRules;
import poscs.model.ProvinceAssignment;
import poscs.model.User;

/**
 * Validator cho module Quản lý nhân viên (UC-24..29).
 * Quy tắc:
 * - BR-28: Bắt buộc Họ, Tên, Phòng ban, Vai trò, Ngày vào làm, Email cá nhân hợp lệ.
 * - BR-09: SĐT Việt Nam hợp lệ (nếu có nhập).
 * - BR-10: Email cá nhân đúng định dạng chuẩn RFC.
 * - BR-29: Ngày sinh trong quá khứ (nếu có nhập).
 * - BR-30: Giới tính ∈ {Nam, Nữ, Khác} (nếu có chọn).
 * - BR-27: SĐT và CCCD duy nhất (kiểm tra qua DAO).
 * - Ô Vai trò không có Admin, không thăng lên Admin.
 * - Địa bàn mỗi tỉnh một người phụ trách.
 */
public class EmployeeValidator extends BaseValidator {

    public static final String FIELD_LAST_NAME = "lastName";
    public static final String FIELD_FIRST_NAME = "firstName";
    public static final String FIELD_MIDDLE_NAME = "middleName";
    public static final String FIELD_ROLE_ID = "roleId";
    public static final String FIELD_DEPARTMENT_ID = "departmentId";
    public static final String FIELD_HIRE_DATE = "hireDate";
    public static final String FIELD_PERSONAL_EMAIL = "personalEmail";
    public static final String FIELD_GENDER = "gender";
    public static final String FIELD_DATE_OF_BIRTH = "dateOfBirth";
    public static final String FIELD_PHONE = "phone";
    public static final String FIELD_CITIZEN_ID = "citizenId";
    public static final String FIELD_ADDRESS = "address";
    public static final String FIELD_PROVINCES = "provinces";

    public static final int MAX_NAME_LENGTH = 50;
    public static final int MAX_MIDDLE_NAME_LENGTH = 100;
    public static final int MAX_CITIZEN_ID_LENGTH = 20;
    public static final int MAX_ADDRESS_DETAIL_LENGTH = 255;

    public static final String ADMIN_ROLE_NAME = "Admin";
    public static final Set<String> ALLOWED_GENDERS = Set.of("Nam", "Nữ", "Khác");
    public static final Pattern PHONE_PATTERN = Pattern.compile("^(0|\\+84)[35789][0-9]{8}$");

    public static final String ERR_INVALID = "invalid";
    public static final String ERR_REQUIRED = "required";
    public static final String ERR_INVALID_PHONE = "invalid_phone";
    public static final String ERR_INVALID_EMAIL = "invalid_email";
    public static final String ERR_INVALID_GENDER = "invalid_gender";
    public static final String ERR_INVALID_DOB = "invalid_dob";
    public static final String ERR_INVALID_HIRE_DATE = "invalid_hire_date";
    public static final String ERR_DUPLICATE_PHONE = "duplicate_phone";
    public static final String ERR_DUPLICATE_CITIZEN = "duplicate_citizen";
    public static final String ERR_ADMIN_ROLE_FORBIDDEN = "admin_role_forbidden";
    public static final String ERR_PROVINCE_TAKEN = "province_taken";
    public static final String ERR_INVALID_LENGTH = "invalid_length";
    public static final String ERR_INVALID_CHARACTERS = "invalid_characters";

    public static final String MSG_033_PHONE = "Số điện thoại này đã được sử dụng bởi tài khoản khác.";
    public static final String MSG_033_CITIZEN = "Số CCCD/CMND này đã được sử dụng bởi tài khoản khác.";
    public static final String MSG_034_DOB = "Ngày sinh phải là một ngày hợp lệ trong quá khứ.";
    public static final String MSG_035_GENDER = "Giá trị giới tính không hợp lệ.";
    public static final String MSG_COMMON_INVALID = "Vui lòng nhập đầy đủ và đúng định dạng các trường bắt buộc.";
    public static final String MSG_PROVINCE_TAKEN = "Có tỉnh trong ô Địa bàn phụ trách vừa được giao cho người khác. Chọn lại địa bàn rồi tạo lại.";

    public static String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String normalizeCitizenId(String citizenId) {
        return trimToNull(citizenId);
    }

    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        String p = phone.replaceAll("[\\s.-]", "");
        if (p.startsWith("+84")) {
            p = "0" + p.substring(3);
        }
        return p.isEmpty() ? null : p;
    }

    public boolean isValidPhone(String phone) {
        String normalized = normalizePhone(phone);
        if (normalized == null) return true;
        return PHONE_VN.matcher(normalized).matches();
    }

    public boolean isValidEmail(String email) {
        String trimmed = trimToNull(email);
        if (trimmed == null || trimmed.length() > MAX_EMAIL_LENGTH) return false;
        return EMAIL.matcher(trimmed).matches();
    }

    public boolean isValidGender(String gender) {
        String trimmed = trimToNull(gender);
        return trimmed == null || ALLOWED_GENDERS.contains(trimmed);
    }

    public boolean isValidDateOfBirth(Date dob) {
        return dob == null || dob.toLocalDate().isBefore(LocalDate.now());
    }

    private void checkName(ValidationResult result, String field, String value, int maxLen, boolean required) {
        if (isBlank(value)) {
            if (required) result.addError(field, ERR_REQUIRED);
            return;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLen) {
            result.addError(field, ERR_INVALID_LENGTH);
        } else if (!TextRules.isSafeFreeText(trimmed)) {
            result.addError(field, ERR_INVALID_CHARACTERS);
        }
    }

    public ValidationResult validateMandatoryFields(User u) {
        ValidationResult result = new ValidationResult();
        if (u == null) {
            result.addGlobalError(ERR_INVALID);
            return result;
        }
        checkName(result, FIELD_LAST_NAME, u.getLastName(), MAX_NAME_LENGTH, true);
        checkName(result, FIELD_FIRST_NAME, u.getFirstName(), MAX_NAME_LENGTH, true);
        checkName(result, FIELD_MIDDLE_NAME, u.getMiddleName(), MAX_MIDDLE_NAME_LENGTH, false);

        if (u.getDepartmentId() <= 0) result.addError(FIELD_DEPARTMENT_ID, ERR_REQUIRED);
        if (u.getRoleId() <= 0) result.addError(FIELD_ROLE_ID, ERR_REQUIRED);
        if (u.getHireDate() == null) result.addError(FIELD_HIRE_DATE, ERR_REQUIRED);

        if (isBlank(u.getPersonalEmail())) {
            result.addError(FIELD_PERSONAL_EMAIL, ERR_REQUIRED);
        } else if (!isValidEmail(u.getPersonalEmail())) {
            result.addError(FIELD_PERSONAL_EMAIL, ERR_INVALID_EMAIL);
        }
        return result;
    }

    public ValidationResult validateOptionalFields(User u) {
        ValidationResult result = new ValidationResult();
        if (u == null) return result;

        if (!isValidGender(u.getGender())) {
            result.addError(FIELD_GENDER, ERR_INVALID_GENDER);
        }
        if (!isValidDateOfBirth(u.getDateOfBirth())) {
            result.addError(FIELD_DATE_OF_BIRTH, ERR_INVALID_DOB);
        }
        if (!isValidPhone(u.getPhone())) {
            result.addError(FIELD_PHONE, ERR_INVALID_PHONE);
        }
        String citizen = normalizeCitizenId(u.getCitizenId());
        if (citizen != null && citizen.length() > MAX_CITIZEN_ID_LENGTH) {
            result.addError(FIELD_CITIZEN_ID, ERR_INVALID_LENGTH);
        }
        if (u.getAddress() != null && !isBlank(u.getAddress().getStreetAndLocalName())) {
            String street = u.getAddress().getStreetAndLocalName().trim();
            if (street.length() > MAX_ADDRESS_DETAIL_LENGTH) {
                result.addError(FIELD_ADDRESS, ERR_INVALID_LENGTH);
            } else if (!TextRules.isSafeFreeText(street)) {
                result.addError(FIELD_ADDRESS, ERR_INVALID_CHARACTERS);
            }
        }
        return result;
    }

    public ValidationResult validateRole(int roleId, boolean isCreate, boolean existingIsAdmin,
            Set<Integer> adminRoleIds) {
        ValidationResult result = new ValidationResult();
        if (adminRoleIds != null && adminRoleIds.contains(roleId)) {
            if (isCreate || !existingIsAdmin) {
                result.addError(FIELD_ROLE_ID, ERR_ADMIN_ROLE_FORBIDDEN);
            }
        }
        return result;
    }

    public ValidationResult validateCreate(User u, Set<Integer> adminRoleIds) {
        ValidationResult result = validateMandatoryFields(u);
        result.merge(validateOptionalFields(u));
        if (u != null) {
            result.merge(validateRole(u.getRoleId(), true, false, adminRoleIds));
        }
        return result;
    }

    public ValidationResult validateUpdate(User u, User existing, Set<Integer> adminRoleIds) {
        ValidationResult result = validateMandatoryFields(u);
        result.merge(validateOptionalFields(u));
        if (u != null) {
            boolean existingIsAdmin = existing != null && adminRoleIds != null
                    && adminRoleIds.contains(existing.getRoleId());
            result.merge(validateRole(u.getRoleId(), false, existingIsAdmin, adminRoleIds));
        }
        return result;
    }

    public boolean anyProvinceHeldByOthers(List<Integer> provinceIds,
            Collection<ProvinceAssignment> allAssignments, Integer editingUserId) {
        if (provinceIds == null || provinceIds.isEmpty() || allAssignments == null) {
            return false;
        }
        Set<Integer> wanted = new HashSet<>(provinceIds);
        for (ProvinceAssignment pa : allAssignments) {
            if (wanted.contains(pa.getProvinceId()) && pa.getHolderUserId() != null
                    && !pa.getHolderUserId().equals(editingUserId)) {
                return true;
            }
        }
        return false;
    }

    public boolean isValidCommonFields(User u) {
        if (u == null || isBlank(u.getLastName()) || isBlank(u.getFirstName())) {
            return false;
        }
        if (!TextRules.isSafeFreeText(u.getLastName())
                || !TextRules.isSafeFreeText(u.getMiddleName())
                || !TextRules.isSafeFreeText(u.getFirstName())
                || (u.getAddress() != null && !TextRules.isSafeFreeText(u.getAddress().getStreetAndLocalName()))) {
            return false;
        }
        if (u.getRoleId() <= 0 || u.getDepartmentId() <= 0) {
            return false;
        }
        if (!isValidGender(u.getGender()) || !isValidDateOfBirth(u.getDateOfBirth())) {
            return false;
        }
        if (u.getHireDate() == null || isBlank(u.getPersonalEmail()) || !isValidEmail(u.getPersonalEmail())) {
            return false;
        }
        return isValidPhone(u.getPhone());
    }
}
