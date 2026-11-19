package poscs.validator;

import java.util.regex.Pattern;
import poscs.model.BranchInfo;

/**
 * Kiểm tra Thông tin chi nhánh (UC-55, SPEC 5.8) theo BR-57.
 *
 * <ul>
 *   <li>Họ tên và chức vụ Giám đốc chi nhánh (GĐCN): bắt buộc, tối đa 100 ký
 *       tự. Thiếu → MSG-058.</li>
 *   <li>SĐT và fax: không bắt buộc; có nhập thì tối đa 20 ký tự, có 8–15 chữ
 *       số, ngoài chữ số chỉ cho phép dấu {@code +} ở đầu, khoảng trắng, dấu
 *       chấm, gạch ngang, ngoặc đơn. Sai → MSG-015 (SĐT) / MSG-059 (fax).</li>
 * </ul>
 *
 * <p>Mọi ô được trim trước khi kiểm. Không dùng {@link #PHONE_VN} (BR-09) vì
 * nó chỉ nhận số di động 10 chữ số, còn số của chi nhánh thường là số máy bàn
 * kèm mã vùng.
 */
public class BranchInfoValidator extends BaseValidator {

    public static final String FIELD_DIRECTOR_NAME = "directorName";
    public static final String FIELD_DIRECTOR_POSITION = "directorPosition";
    public static final String FIELD_PHONE = "phone";
    public static final String FIELD_FAX = "fax";

    /** BR-57: họ tên, chức vụ GĐCN tối đa 100 ký tự (khớp varchar(100) của V39). */
    public static final int MAX_TEXT_LENGTH = 100;
    /** BR-57: SĐT, fax tối đa 20 ký tự (khớp varchar(20) của V39). */
    public static final int MAX_CONTACT_LENGTH = 20;
    /** BR-57: SĐT, fax có từ 8 tới 15 chữ số. */
    public static final int MIN_CONTACT_DIGITS = 8;
    public static final int MAX_CONTACT_DIGITS = 15;

    /** MSG-058. */
    public static final String MSG_REQUIRED = "Vui lòng nhập họ tên và chức vụ Giám đốc chi nhánh.";
    /** MSG-015. */
    public static final String MSG_PHONE_INVALID = "Số điện thoại không hợp lệ.";
    /** MSG-059. */
    public static final String MSG_FAX_INVALID = "Số fax không hợp lệ.";
    // SPEC không có mã MSG cho họ tên/chức vụ quá dài; viết theo cách
    // ContractValidator báo độ dài ô người ký. Form đã chặn bằng maxlength,
    // nên chỉ POST tự gõ mới tới được hai câu này.
    public static final String MSG_NAME_TOO_LONG =
            "Họ tên Giám đốc chi nhánh không được vượt quá " + MAX_TEXT_LENGTH + " ký tự.";
    public static final String MSG_POSITION_TOO_LONG =
            "Chức vụ Giám đốc chi nhánh không được vượt quá " + MAX_TEXT_LENGTH + " ký tự.";

    /**
     * BR-57: tập ký tự được phép của SĐT/fax. Dấu {@code +} chỉ được đứng đầu.
     * Số chữ số (8–15) kiểm riêng ở {@link #digitCount(String)}.
     */
    static final Pattern CONTACT_CHARS = Pattern.compile("^\\+?[0-9 .()\\-]+$");

    /**
     * Kiểm thông tin chi nhánh theo BR-57.
     *
     * @param info dữ liệu cần kiểm; {@code null} coi như bỏ trống mọi ô
     * @return lỗi theo từng ô ({@link #FIELD_DIRECTOR_NAME}, {@link #FIELD_DIRECTOR_POSITION},
     *         {@link #FIELD_PHONE}, {@link #FIELD_FAX}); rỗng khi hợp lệ
     */
    public ValidationResult validate(BranchInfo info) {
        ValidationResult result = new ValidationResult();
        BranchInfo data = info != null ? info : new BranchInfo();
        requiredText(result, FIELD_DIRECTOR_NAME, data.getDirectorName(), MSG_NAME_TOO_LONG);
        requiredText(result, FIELD_DIRECTOR_POSITION, data.getDirectorPosition(), MSG_POSITION_TOO_LONG);
        contactNumber(result, FIELD_PHONE, data.getPhone(), MSG_PHONE_INVALID);
        contactNumber(result, FIELD_FAX, data.getFax(), MSG_FAX_INVALID);
        return result;
    }

    /** BR-57: ô chữ bắt buộc (MSG-058), tối đa {@link #MAX_TEXT_LENGTH} ký tự sau khi trim. */
    private void requiredText(ValidationResult result, String field, String raw, String tooLongMessage) {
        String value = trimToNull(raw);
        if (required(result, field, value, MSG_REQUIRED)) {
            maxLength(result, field, value, MAX_TEXT_LENGTH, tooLongMessage);
        }
    }

    /** BR-57: SĐT/fax không bắt buộc; có nhập thì phải đúng độ dài, tập ký tự và số chữ số. */
    private void contactNumber(ValidationResult result, String field, String raw, String message) {
        String value = trimToNull(raw);
        if (value == null) {
            return;
        }
        int digits = digitCount(value);
        if (value.length() > MAX_CONTACT_LENGTH
                || !CONTACT_CHARS.matcher(value).matches()
                || digits < MIN_CONTACT_DIGITS
                || digits > MAX_CONTACT_DIGITS) {
            result.addError(field, message);
        }
    }

    /** Đếm chữ số ASCII 0–9 trong chuỗi. */
    static int digitCount(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= '0' && ch <= '9') {
                count++;
            }
        }
        return count;
    }

    /**
     * Trim; chuỗi rỗng hoặc chỉ có khoảng trắng thành {@code null}. Tầng gọi
     * dùng hàm này để lưu đúng giá trị đã kiểm (xem ghi chú đầu BaseValidator).
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
