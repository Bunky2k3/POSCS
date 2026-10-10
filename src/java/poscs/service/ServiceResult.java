package poscs.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result/Response wrapper trả về từ tầng Service cho Controller.
 *
 * <p>Thành công: {@link #ok(Object)}; thất bại: {@link #fail(String)} hoặc
 * {@link #fail(Map)} (lỗi theo từng trường, dùng cho validation).
 *
 * @param <T> kiểu dữ liệu trả về khi thành công
 */
public final class ServiceResult<T> {

    private final boolean success;
    private final T data;
    private final String message;
    private final Map<String, String> fieldErrors;

    private ServiceResult(boolean success, T data, String message, Map<String, String> fieldErrors) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.fieldErrors = fieldErrors == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public static <T> ServiceResult<T> ok(T data) {
        return new ServiceResult<>(true, data, null, null);
    }

    public static <T> ServiceResult<T> ok(T data, String message) {
        return new ServiceResult<>(true, data, message, null);
    }

    public static <T> ServiceResult<T> fail(String message) {
        return new ServiceResult<>(false, null, message, null);
    }

    public static <T> ServiceResult<T> fail(Map<String, String> fieldErrors) {
        return new ServiceResult<>(false, null, null, fieldErrors);
    }

    public static <T> ServiceResult<T> fail(String message, Map<String, String> fieldErrors) {
        return new ServiceResult<>(false, null, message, fieldErrors);
    }

    public static <T> ServiceResult<T> fail(poscs.validator.ValidationResult vr) {
        if (vr == null) {
            return fail((String) null, null);
        }
        String globalMsg = vr.getGlobalError();
        Map<String, String> fields = new LinkedHashMap<>(vr.getErrors());
        fields.remove(poscs.validator.ValidationResult.GLOBAL_KEY);
        return fail(globalMsg, fields.isEmpty() ? null : fields);
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public boolean hasFieldErrors() {
        return !fieldErrors.isEmpty();
    }
}
