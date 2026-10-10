package poscs.validator;

import org.junit.Test;

import static org.junit.Assert.*;

/** Test BaseValidator (QA: NaN/Infinity, lengthBetween trim, email 254). */
public class BaseValidatorTest {

    private static class V extends BaseValidator {
        ValidationResult nonNeg(Number n) {
            ValidationResult r = new ValidationResult();
            nonNegative(r, "n", n, "bad");
            return r;
        }

        ValidationResult between(String s, int min, int max) {
            ValidationResult r = new ValidationResult();
            lengthBetween(r, "s", s, min, max, "bad");
            return r;
        }

        ValidationResult mail(String s) {
            ValidationResult r = new ValidationResult();
            email(r, "e", s, "bad");
            return r;
        }
    }

    private final V v = new V();

    @Test
    public void nonNegative_rejectsNaNAndInfinity_withoutThrowing() {
        assertFalse(v.nonNeg(Double.NaN).isValid());
        assertFalse(v.nonNeg(Double.POSITIVE_INFINITY).isValid());
        assertFalse(v.nonNeg(Double.NEGATIVE_INFINITY).isValid());
        assertFalse(v.nonNeg(Float.NaN).isValid());
        assertFalse(v.nonNeg(-1).isValid());
        assertTrue(v.nonNeg(null).isValid());
        assertTrue(v.nonNeg(0).isValid());
        assertTrue(v.nonNeg(1.5d).isValid());
    }

    @Test
    public void lengthBetween_usesTrimmedValueForBothBounds() {
        assertTrue(v.between("  abc  ", 3, 3).isValid());   // trim = 3
        assertFalse(v.between("  abcd  ", 3, 3).isValid());
        assertFalse(v.between("ab", 3, 5).isValid());
        assertTrue(v.between("   ", 3, 5).isValid());       // rỗng bị bỏ qua
    }

    @Test
    public void email_rejectsOver254Chars() {
        String ok = "a".repeat(64) + "@" + "b".repeat(60) + ".com";
        assertTrue(v.mail(ok).isValid());
        String longLocalOver254 = "a".repeat(64) + "@" + ("b".repeat(60) + ".").repeat(4) + "com";
        assertTrue(longLocalOver254.length() > 254);
        assertFalse(v.mail(longLocalOver254).isValid());
        assertTrue(v.mail(null).isValid());
    }
}
