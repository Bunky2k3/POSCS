package poscs.validator;

import java.time.LocalDate;
import java.util.regex.Pattern;
import poscs.model.Address;
import poscs.model.Enterprise;

/**
 * Validator cho module Khách hàng & Nhà cung cấp.
 *
 * <p>Quy tắc kiểm tra:
 * <ul>
 *   <li>BR-09: Số điện thoại định dạng Việt Nam: chấp nhận số di động (10 chữ số,
 *       đầu 03, 05, 07, 08, 09, tiền tố +84) cũng như số cố định/bàn Việt Nam
 *       (9-10 chữ số sau 0 hoặc +84, cho phép khoảng trắng/chấm/gạch ngang).</li>
 *   <li>BR-10: Email hợp lệ (theo chuẩn RFC, không chứa khoảng trắng, tối đa 254 ký tự).</li>
 *   <li>BR-11: Các trường bắt buộc không được để trống (tên khách hàng, loại, nhóm, MST khi tạo mới, địa chỉ, người phụ trách chính).</li>
 *   <li>BR-52: Mã khách hàng tự sinh dạng {@code KH-xxxx} (4 chữ số, ví dụ {@code KH-0001}), nhà cung cấp dạng {@code NCC-xxx} (3 chữ số, ví dụ {@code NCC-001}).</li>
 * </ul>
 */
public class CustomerValidator extends BaseValidator {

    /** BR-52: Mã khách hàng dạng KH-xxxx (đúng 4 chữ số: KH-0001, KH-0002, ...). */
    public static final Pattern CUSTOMER_CODE_PATTERN = Pattern.compile("^KH-\\d{4}$");

    /** BR-52: Mã nhà cung cấp dạng NCC-xxx (đúng 3 chữ số: NCC-001, NCC-002, ...). */
    public static final Pattern SUPPLIER_CODE_PATTERN = Pattern.compile("^NCC-\\d{3}$");

    /** Regex kiểm tra mã đối tác chung (KH-xxxx hoặc NCC-xxx). */
    public static final Pattern ENTERPRISE_CODE_PATTERN = Pattern.compile("^(KH-\\d{4}|NCC-\\d{3})$");

    /** BR-09: Regex kiểm tra SĐT Việt Nam khớp logic gốc của CustomerController (chấp nhận di động & cố định). */
    public static final Pattern CUSTOMER_PHONE_PATTERN = Pattern.compile("^(0|\\+84)[0-9]{9,10}$");

    public static final String ERR_INVALID = "invalid";
    public static final String ERR_REQUIRED = "required";
    public static final String ERR_INVALID_PHONE = "invalid_phone";
    public static final String ERR_INVALID_EMAIL = "invalid_email";
    public static final String ERR_INVALID_CODE = "invalid_code";
    public static final String ERR_FUTURE_JOIN_DATE = "future_join_date";
    public static final String ERR_SAME_OWNER_AND_SUPPORT = "same_owner_and_support";
    public static final String ERR_INVALID_ADDRESS = "invalid_address";

    /**
     * BR-09: Chuẩn hóa số điện thoại bằng cách loại bỏ khoảng trắng, dấu chấm và gạch ngang.
     */
    public static String cleanPhone(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.replaceAll("[\\s.-]", "");
    }

    /**
     * BR-09: Kiểm tra tính hợp lệ của số điện thoại Việt Nam theo logic gốc của CustomerController.
     * Chấp nhận cả số di động (10 số) và cố định (11 số cả mã vùng), tiền tố 0 hoặc +84.
     */
    public boolean isValidPhone(String phone) {
        if (phone == null) {
            return false;
        }
        String cleaned = cleanPhone(phone);
        return CUSTOMER_PHONE_PATTERN.matcher(cleaned).matches();
    }

    /**
     * BR-09: Kiểm tra tính hợp lệ của số điện thoại chuẩn di động (10 chữ số: 03x, 05x, 07x, 08x, 09x).
     */
    public boolean isValidMobilePhone(String phone) {
        if (phone == null) {
            return false;
        }
        String cleaned = cleanPhone(phone);
        if (cleaned.startsWith("+84")) {
            cleaned = "0" + cleaned.substring(3);
        }
        return PHONE_VN.matcher(cleaned).matches();
    }

    /**
     * BR-10: Kiểm tra tính hợp lệ của email (sử dụng BaseValidator RFC EMAIL & max 254).
     */
    public boolean isValidEmail(String email) {
        if (isBlank(email)) {
            return false;
        }
        ValidationResult vr = new ValidationResult();
        email(vr, "email", email, ERR_INVALID_EMAIL);
        return vr.isValid();
    }

    /**
     * BR-52: Kiểm tra định dạng mã đối tác (KH-xxxx đúng 4 chữ số hoặc NCC-xxx đúng 3 chữ số).
     */
    public boolean isValidEnterpriseCode(String code) {
        if (isBlank(code)) {
            return false;
        }
        return ENTERPRISE_CODE_PATTERN.matcher(code.trim()).matches();
    }

    /**
     * BR-52: Kiểm tra định dạng mã khách hàng (bắt đầu bằng KH- theo sau bởi đúng 4 chữ số).
     */
    public boolean isValidCustomerCode(String code) {
        if (isBlank(code)) {
            return false;
        }
        return CUSTOMER_CODE_PATTERN.matcher(code.trim()).matches();
    }

    /**
     * BR-52: Kiểm tra định dạng mã nhà cung cấp (bắt đầu bằng NCC- theo sau bởi đúng 3 chữ số).
     */
    public boolean isValidSupplierCode(String code) {
        if (isBlank(code)) {
            return false;
        }
        return SUPPLIER_CODE_PATTERN.matcher(code.trim()).matches();
    }

    /**
     * Kiểm tra tính hợp lệ của đối tượng Address: phải có địa chỉ chi tiết (streetAndLocalName)
     * và quận/huyện/xã (districtId > 0).
     */
    public boolean isValidAddress(Address address) {
        if (address == null) {
            return false;
        }
        return !isBlank(address.getStreetAndLocalName()) && address.getDistrictId() > 0;
    }

    /**
     * Kiểm tra các trường chung của đối tượng Enterprise (giữ nguyên logic gốc của isValidCommonFields).
     *
     * @param e Đối tượng Enterprise
     * @return true nếu tất cả các trường chung hợp lệ
     */
    public boolean isValidCommonFields(Enterprise e) {
        if (e == null) {
            return false;
        }
        if (isBlank(e.getEnterpriseName()) || isBlank(e.getCustomerType()) || isBlank(e.getCustomerGroup())) {
            return false;
        }
        if (e.getAccountOwnerId() <= 0) {
            return false;
        }
        // Người hỗ trợ không được trùng với người phụ trách chính
        if (e.getSupportOwnerId() != null && e.getSupportOwnerId().intValue() == e.getAccountOwnerId()) {
            return false;
        }
        if (!isValidPhone(e.getPhone())) {
            return false;
        }
        if (isBlank(e.getEmail()) || !isValidEmail(e.getEmail())) {
            return false;
        }
        return e.getJoinDate() == null || !e.getJoinDate().toLocalDate().isAfter(LocalDate.now());
    }

    /**
     * Validate đối tượng Enterprise đầy đủ với ValidationResult chi tiết theo từng field.
     * Tận dụng triệt để các helper method từ BaseValidator.
     *
     * @param e Đối tượng Enterprise cần kiểm tra
     * @param isCreate true nếu là luồng tạo mới (yêu cầu bắt buộc taxCode và address), false nếu là luồng cập nhật
     * @return ValidationResult chứa các lỗi chi tiết
     */
    public ValidationResult validate(Enterprise e, boolean isCreate) {
        ValidationResult result = new ValidationResult();
        if (e == null) {
            result.addGlobalError(ERR_INVALID);
            return result;
        }

        // BR-11: Bắt buộc các trường chung qua BaseValidator.required
        required(result, "customerName", e.getEnterpriseName(), ERR_REQUIRED);
        required(result, "customerType", e.getCustomerType(), ERR_REQUIRED);
        required(result, "customerGroup", e.getCustomerGroup(), ERR_REQUIRED);

        if (isCreate) {
            required(result, "taxCode", e.getTaxCode(), ERR_REQUIRED);
            if (e.getAddress() == null) {
                result.addError("address", ERR_REQUIRED);
            } else if (!isValidAddress(e.getAddress())) {
                result.addError("address", ERR_INVALID_ADDRESS);
            }
        } else {
            // Khi cập nhật: nếu có truyền Address mới thì phải hợp lệ, nếu không có Address mới thì phải có addressId cũ hợp lệ
            if (e.getAddress() != null) {
                if (!isValidAddress(e.getAddress())) {
                    result.addError("address", ERR_INVALID_ADDRESS);
                }
            } else if (e.getAddressId() == null || e.getAddressId() <= 0) {
                result.addError("address", ERR_REQUIRED);
            }
        }

        if (e.getAccountOwnerId() <= 0) {
            result.addError("accountOwnerId", ERR_REQUIRED);
        }

        if (e.getSupportOwnerId() != null && e.getSupportOwnerId().intValue() == e.getAccountOwnerId()) {
            result.addError("supportOwnerId", ERR_SAME_OWNER_AND_SUPPORT);
        }

        // BR-09: SĐT Việt Nam
        if (required(result, "phone", e.getPhone(), ERR_REQUIRED)) {
            matches(result, "phone", cleanPhone(e.getPhone()), CUSTOMER_PHONE_PATTERN, ERR_INVALID_PHONE);
        }

        // BR-10: Email RFC qua BaseValidator
        if (required(result, "email", e.getEmail(), ERR_REQUIRED)) {
            email(result, "email", e.getEmail(), ERR_INVALID_EMAIL);
        }

        // BR-32: Ngày tham gia không ở tương lai
        if (e.getJoinDate() != null && e.getJoinDate().toLocalDate().isAfter(LocalDate.now())) {
            result.addError("joinDate", ERR_FUTURE_JOIN_DATE);
        }

        // BR-52: Mã đối tác nếu có
        if (e.getEnterpriseCode() != null && !e.getEnterpriseCode().trim().isEmpty()) {
            matches(result, "enterpriseCode", e.getEnterpriseCode(), ENTERPRISE_CODE_PATTERN, ERR_INVALID_CODE);
        }

        return result;
    }
}
