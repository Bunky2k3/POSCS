package poscs.service;

import java.util.Collections;
import org.junit.Test;
import poscs.validator.BaseValidator;
import poscs.validator.ValidationResult;

import static org.junit.Assert.*;

/** Test nền tảng tầng Service/Validator (Task 1.1). */
public class ServiceFoundationTest {

    private static class DemoService extends BaseService {
        ServiceResult<String> run(ValidationResult v) {
            if (!v.isValid()) {
                return failure(v);
            }
            return success("done", "ok");
        }
    }

    private static class DemoValidator extends BaseValidator {
        ValidationResult validate(String name, String code) {
            ValidationResult r = new ValidationResult();
            if (required(r, "name", name, "Tên bắt buộc")) {
                maxLength(r, "name", name, 5, "Tên quá dài");
            }
            matches(r, "code", code, "KH-\\d{4}", "Mã sai định dạng");
            return r;
        }

        ValidationResult extra(String phone, String email, String name, Integer qty) {
            ValidationResult r = new ValidationResult();
            phoneVn(r, "phone", phone, "sdt");
            email(r, "email", email, "email");
            minLength(r, "name", name, 3, "min");
            nonNegative(r, "qty", qty, "neg");
            return r;
        }
    }

    @Test
    public void okResultCarriesData() {
        ServiceResult<String> r = ServiceResult.ok("x");
        assertTrue(r.isSuccess());
        assertEquals("x", r.getData());
        assertFalse(r.hasFieldErrors());
    }

    @Test
    public void failResultCarriesMessageAndErrors() {
        ServiceResult<String> r = ServiceResult.fail("loi", Collections.singletonMap("f", "m"));
        assertFalse(r.isSuccess());
        assertNull(r.getData());
        assertEquals("loi", r.getMessage());
        assertEquals("m", r.getFieldErrors().get("f"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void fieldErrorsAreImmutable() {
        ServiceResult.fail("x").getFieldErrors().put("a", "b");
    }

    @Test
    public void validatorCollectsErrors() {
        DemoValidator v = new DemoValidator();
        assertTrue(v.validate("abc", "KH-0001").isValid());
        assertEquals("Tên bắt buộc", v.validate(" ", null).getError("name"));
        assertEquals("Tên quá dài", v.validate("abcdefg", null).getError("name"));
        assertEquals("Mã sai định dạng", v.validate("abc", "X1").getError("code"));
    }

    @Test
    public void phoneEmailMinNonNegative() {
        DemoValidator v = new DemoValidator();
        ValidationResult ok = v.extra("0912345678", "a@b.vn", "abc", 0);
        assertTrue(ok.isValid());
        assertEquals("sdt", v.extra("123", null, null, null).getError("phone"));
        assertEquals("sdt", v.extra("0123456789", null, null, null).getError("phone"));
        assertEquals("email", v.extra(null, "a@b", null, null).getError("email"));
        assertEquals("min", v.extra(null, null, "ab", null).getError("name"));
        assertEquals("neg", v.extra(null, null, null, -1).getError("qty"));
        assertTrue(v.extra(null, null, null, null).isValid());
    }

    @Test
    public void globalErrorAndMerge() {
        ValidationResult a = new ValidationResult().addError("x", "1");
        ValidationResult b = new ValidationResult().addError("x", "2").addGlobalError("g");
        a.merge(b).merge(null);
        assertEquals("1", a.getError("x"));
        assertTrue(a.hasGlobalError());
        assertEquals("g", a.getGlobalError());
        assertFalse(new ValidationResult().hasGlobalError());
    }

    @Test
    public void baseServiceMapsValidationToFailure() {
        DemoService s = new DemoService();
        ServiceResult<String> bad = s.run(new ValidationResult().addError("a", "b"));
        assertFalse(bad.isSuccess());
        assertEquals("b", bad.getFieldErrors().get("a"));
        ServiceResult<String> good = s.run(new ValidationResult());
        assertTrue(good.isSuccess());
        assertEquals("done", good.getData());
        assertEquals("ok", good.getMessage());
    }
}
