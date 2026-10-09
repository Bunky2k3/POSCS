package poscs.service;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import poscs.validator.ValidationResult;

/**
 * Lớp cơ sở cho mọi Service: cung cấp logger và các helper tạo
 * {@link ServiceResult} thống nhất. Service chỉ chứa nghiệp vụ, không
 * phụ thuộc Servlet API.
 */
public abstract class BaseService {

    protected final Logger log = Logger.getLogger(getClass().getName());

    protected <T> ServiceResult<T> success(T data) {
        return ServiceResult.ok(data);
    }

    protected <T> ServiceResult<T> success(T data, String message) {
        return ServiceResult.ok(data, message);
    }

    protected <T> ServiceResult<T> failure(String message) {
        return ServiceResult.fail(message);
    }

    protected <T> ServiceResult<T> failure(Map<String, String> fieldErrors) {
        return ServiceResult.fail(fieldErrors);
    }

    /** Chuyển kết quả validate thất bại thành ServiceResult thất bại. */
    protected <T> ServiceResult<T> failure(ValidationResult validation) {
        return ServiceResult.fail(validation.getErrors());
    }

    /** Ghi log lỗi và trả về kết quả thất bại với thông điệp thân thiện. */
    protected <T> ServiceResult<T> error(String userMessage, Throwable cause) {
        log.log(Level.SEVERE, userMessage, cause);
        return ServiceResult.fail(userMessage);
    }
}
