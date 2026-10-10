package poscs.validator;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProfileValidatorTest {

    @Test
    public void testNormalizePhone() {
        assertEquals("0912345678", ProfileValidator.normalizePhone("+84912345678"));
        assertEquals("0912345678", ProfileValidator.normalizePhone("0912345678"));
        assertEquals("0912345678", ProfileValidator.normalizePhone(" 0912 345 678 "));
    }

    @Test
    public void testValidateProfileUpdate() {
        ProfileValidator validator = new ProfileValidator();
        ValidationResult r1 = validator.validateProfileUpdate(
                "Nguyen", "Van", "A", "012345678901", "0912345678", "a@b.com", "Nam", "1990-01-01", 1, "Hanoi");
        assertTrue(r1.isValid());

        ValidationResult r2 = validator.validateProfileUpdate(
                "Nguyen", "Van", "A", "0123456789012345", "0912345678", "a@b.com", "Nam", "1990-01-01", 1, "Hanoi");
        assertFalse(r2.isValid());
        assertEquals("invalid_length", r2.getError("general"));
    }
}
