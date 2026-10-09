package poscs.validator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kết quả validate: tập lỗi theo từng trường (giữ thứ tự thêm vào) cùng
 * lỗi toàn cục không gắn với trường cụ thể.
 */
public final class ValidationResult {

    /** Khóa dành riêng cho lỗi toàn cục trong {@link #getErrors()}. */
    public static final String GLOBAL_KEY = "_global";

    private final Map<String, String> errors = new LinkedHashMap<>();

    /** Thêm lỗi cho trường; lỗi đầu tiên của mỗi trường được giữ lại. */
    public ValidationResult addError(String field, String message) {
        errors.putIfAbsent(field == null ? GLOBAL_KEY : field, message);
        return this;
    }

    /** Thêm lỗi toàn cục (không thuộc trường nào). */
    public ValidationResult addGlobalError(String message) {
        return addError(GLOBAL_KEY, message);
    }

    /** Gộp lỗi từ kết quả khác (null-safe); lỗi đã có của cùng trường được giữ. */
    public ValidationResult merge(ValidationResult other) {
        if (other != null) {
            other.errors.forEach(errors::putIfAbsent);
        }
        return this;
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public Map<String, String> getErrors() {
        return Collections.unmodifiableMap(errors);
    }

    public String getError(String field) {
        return errors.get(field);
    }

    public String getGlobalError() {
        return errors.get(GLOBAL_KEY);
    }

    public boolean hasGlobalError() {
        return errors.containsKey(GLOBAL_KEY);
    }
}
