package poscs.validator;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Lớp cơ sở cho các Validator: các helper kiểm tra chung, không chứa
 * quy tắc nghiệp vụ riêng của module nào. Mọi helper đều null-safe và
 * bỏ qua giá trị rỗng (trừ {@link #required}); kết hợp với required() nếu
 * trường bắt buộc.
 */
public abstract class BaseValidator {

    /** Độ dài tối đa của email theo RFC 5321. */
    public static final int MAX_EMAIL_LENGTH = 254;

    // LƯU Ý (BR-09/10): matches/phoneVn/email kiểm tra trên giá trị đã trim;
    // caller PHẢI lưu giá trị đã trim (xem AuthValidator.trimToNull).

    /** BR-09: SĐT VN 10 chữ số, đầu số hợp lệ (03x, 05x, 07x, 08x, 09x). */
    public static final Pattern PHONE_VN = Pattern.compile("^0[35789]\\d{8}$");

    /** BR-10: email hợp lệ. */
    public static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$");

    protected static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Bắt buộc nhập; trả về true nếu hợp lệ. */
    protected boolean required(ValidationResult result, String field, String value, String message) {
        if (isBlank(value)) {
            result.addError(field, message);
            return false;
        }
        return true;
    }

    /** Độ dài tối thiểu (bỏ qua giá trị rỗng). */
    protected boolean minLength(ValidationResult result, String field, String value, int min, String message) {
        if (!isBlank(value) && value.trim().length() < min) {
            result.addError(field, message);
            return false;
        }
        return true;
    }

    /** Giới hạn độ dài tối đa (bỏ qua null). */
    protected boolean maxLength(ValidationResult result, String field, String value, int max, String message) {
        if (value != null && value.length() > max) {
            result.addError(field, message);
            return false;
        }
        return true;
    }

    /** Độ dài trong khoảng [min, max] (bỏ qua giá trị rỗng). */
    protected boolean lengthBetween(ValidationResult result, String field, String value, int min, int max,
            String message) {
        if (!isBlank(value) && (value.trim().length() < min || value.trim().length() > max)) {
            result.addError(field, message);
            return false;
        }
        return true;
    }

    /** Kiểm tra khớp regex đã biên dịch sẵn (bỏ qua giá trị rỗng). */
    protected boolean matches(ValidationResult result, String field, String value, Pattern pattern,
            String message) {
        if (!isBlank(value) && !pattern.matcher(value.trim()).matches()) {
            result.addError(field, message);
            return false;
        }
        return true;
    }

    /** Kiểm tra khớp regex dạng chuỗi (giữ tương thích; ưu tiên Pattern precompiled). */
    protected boolean matches(ValidationResult result, String field, String value, String regex,
            String message) {
        if (!isBlank(value) && !value.matches(regex)) {
            result.addError(field, message);
            return false;
        }
        return true;
    }

    /** BR-09: SĐT Việt Nam. */
    protected boolean phoneVn(ValidationResult result, String field, String value, String message) {
        return matches(result, field, value, PHONE_VN, message);
    }

    /** BR-10: Email (tối đa 254 ký tự theo RFC 5321). */
    protected boolean email(ValidationResult result, String field, String value, String message) {
        if (!isBlank(value) && value.trim().length() > MAX_EMAIL_LENGTH) {
            result.addError(field, message);
            return false;
        }
        return matches(result, field, value, EMAIL, message);
    }

    /** Số không được âm; NaN/Infinity bị từ chối (bỏ qua null). */
    protected boolean nonNegative(ValidationResult result, String field, Number value, String message) {
        if (value == null) {
            return true;
        }
        if (isNotFinite(value) || toBigDecimal(value).signum() < 0) {
            result.addError(field, message);
            return false;
        }
        return true;
    }

    private static boolean isNotFinite(Number n) {
        if (n instanceof Double) {
            return ((Double) n).isNaN() || ((Double) n).isInfinite();
        }
        if (n instanceof Float) {
            return ((Float) n).isNaN() || ((Float) n).isInfinite();
        }
        return false;
    }

    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal) {
            return (BigDecimal) n;
        }
        return new BigDecimal(n.toString());
    }
}
