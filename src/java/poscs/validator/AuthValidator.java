package poscs.validator;

/**
 * Validator cho luồng xác thực (đăng nhập, đổi mật khẩu, quên mật khẩu/OTP).
 * Tách từ {@code AuthenticationController}, giữ nguyên các quy tắc gốc.
 *
 * <p>Thông điệp lỗi chính là MÃ LỖI mà controller gắn vào
 * {@code ?error=...} khi redirect (vd. {@code missing_fields},
 * {@code weak_password}) nên hành vi hiển thị phía JSP không đổi.
 */
public class AuthValidator extends BaseValidator {

    /** Độ dài tối thiểu của mật khẩu mới. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /** BCrypt chỉ băm 72 byte đầu; dài hơn bị cắt âm thầm và tốn tài nguyên. */
    public static final int MAX_PASSWORD_BYTES = 72;

    public static final String ERR_MISSING_FIELDS = "missing_fields";
    public static final String ERR_MISSING_USERNAME = "missing_username";
    public static final String ERR_WEAK_PASSWORD = "weak_password";
    public static final String ERR_MISMATCH = "mismatch";

    /** Độ dài tối đa của username. */
    public static final int MAX_USERNAME_LENGTH = 50;

    /** Trim; chuỗi rỗng sau khi trim coi như null (chưa nhập). */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Đăng nhập: bắt buộc có username (đã trim) và mật khẩu không rỗng. */
    public ValidationResult validateLogin(String identifier, String password) {
        ValidationResult result = new ValidationResult();
        String id = trimToNull(identifier);
        if (id == null || id.length() > MAX_USERNAME_LENGTH || password == null || password.isEmpty()) {
            result.addError("credentials", ERR_MISSING_FIELDS);
        }
        return result;
    }

    /** Mật khẩu mới: 8..72 byte UTF-8 (BCrypt chỉ dùng 72 byte đầu) và khớp ô xác nhận. */
    public ValidationResult validateNewPassword(String newPassword, String confirmPassword) {
        ValidationResult result = new ValidationResult();
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < MIN_PASSWORD_LENGTH
                || isPasswordTooLong(newPassword)) {
            result.addError("newPassword", ERR_WEAK_PASSWORD);
        } else if (!newPassword.equals(confirmPassword)) {
            result.addError("newPassword", ERR_MISMATCH);
        }
        return result;
    }

    /** true nếu mật khẩu vượt {@link #MAX_PASSWORD_BYTES} byte UTF-8 (chặn DoS băm + cắt âm thầm). */
    public static boolean isPasswordTooLong(String password) {
        return password != null
                && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }

    /** Quên mật khẩu bước 1: bắt buộc nhập username. */
    public ValidationResult validateForgotPassword(String username) {
        ValidationResult result = new ValidationResult();
        String id = trimToNull(username);
        if (id == null || id.length() > MAX_USERNAME_LENGTH) {
            result.addError("username", ERR_MISSING_USERNAME);
        }
        return result;
    }

    /** Mã OTP nhập vào: bắt buộc không rỗng sau khi trim. */
    public boolean hasOtpInput(String otpInput) {
        return trimToNull(otpInput) != null;
    }
}
