package poscs.validator;

import org.junit.Test;
import poscs.model.BranchInfo;

import static org.junit.Assert.*;

/**
 * Test BranchInfoValidator theo BR-57 (UC-55, SPEC 5.8): họ tên và chức vụ
 * GĐCN bắt buộc, tối đa 100 ký tự (MSG-058); SĐT/fax không bắt buộc, có nhập
 * thì tối đa 20 ký tự, 8–15 chữ số, chỉ có chữ số, "+" ở đầu, khoảng trắng,
 * dấu chấm, gạch ngang, ngoặc đơn (MSG-015 / MSG-059).
 */
public class BranchInfoValidatorTest {

    private static final String MSG_058 = "Vui lòng nhập họ tên và chức vụ Giám đốc chi nhánh.";
    private static final String MSG_015 = "Số điện thoại không hợp lệ.";
    private static final String MSG_059 = "Số fax không hợp lệ.";

    private final BranchInfoValidator validator = new BranchInfoValidator();

    private static BranchInfo info(String name, String position, String phone, String fax) {
        BranchInfo b = new BranchInfo();
        b.setDirectorName(name);
        b.setDirectorPosition(position);
        b.setPhone(phone);
        b.setFax(fax);
        return b;
    }

    private static BranchInfo withPhone(String phone) {
        return info("Nguyễn Văn An", "Giám đốc chi nhánh", phone, null);
    }

    private static BranchInfo withFax(String fax) {
        return info("Nguyễn Văn An", "Giám đốc chi nhánh", null, fax);
    }

    private static String repeat(char ch, int count) {
        return String.valueOf(ch).repeat(count);
    }

    // ------------------------------------------------------------------
    // Câu thông báo phải khớp SPEC từng chữ
    // ------------------------------------------------------------------

    @Test
    public void messages_matchSpecWordForWord() {
        assertEquals(MSG_058, BranchInfoValidator.MSG_REQUIRED);
        assertEquals(MSG_015, BranchInfoValidator.MSG_PHONE_INVALID);
        assertEquals(MSG_059, BranchInfoValidator.MSG_FAX_INVALID);
    }

    // ------------------------------------------------------------------
    // Dữ liệu hợp lệ
    // ------------------------------------------------------------------

    @Test
    public void validate_fullValidData_noErrors() {
        ValidationResult r = validator.validate(
                info("Nguyễn Văn An", "Giám đốc chi nhánh", "0912345678", "(0233) 3852 123"));

        assertTrue(r.isValid());
        assertTrue(r.getErrors().isEmpty());
    }

    @Test
    public void validate_phoneAndFaxOmitted_isValid() {
        assertTrue(validator.validate(info("Nguyễn Văn An", "Giám đốc", null, null)).isValid());
        assertTrue(validator.validate(info("Nguyễn Văn An", "Giám đốc", "", "")).isValid());
        assertTrue(validator.validate(info("Nguyễn Văn An", "Giám đốc", "   ", "\t")).isValid());
    }

    @Test
    public void validate_acceptedPhoneFormats() {
        String[] ok = {
            "0912345678",            // di động
            "02433852123",           // máy bàn liền
            "(024) 3852 1234",       // máy bàn có ngoặc, khoảng trắng
            "024.3852.1234",         // dấu chấm
            "0236-3822-123",         // gạch ngang
            "+84 24 3852 1234",      // có +84
            "+84912345678",
            "38521234",              // đúng 8 chữ số
            "123456789012345",       // đúng 15 chữ số
            "+84 (024) 3852.12345",  // đúng 20 ký tự
            "  0912345678  "         // trim trước khi kiểm
        };
        for (String phone : ok) {
            ValidationResult r = validator.validate(withPhone(phone));
            assertTrue("phải nhận: '" + phone + "'", r.isValid());
        }
    }

    @Test
    public void validate_acceptedFaxFormats() {
        for (String fax : new String[]{"(0233) 3852 123", "+84 233 3852 123", "0233.3852.123", "38521234"}) {
            assertTrue("phải nhận: '" + fax + "'", validator.validate(withFax(fax)).isValid());
        }
    }

    // ------------------------------------------------------------------
    // Họ tên / chức vụ bắt buộc (MSG-058)
    // ------------------------------------------------------------------

    @Test
    public void validate_missingName_msg058OnNameOnly() {
        ValidationResult r = validator.validate(info(null, "Giám đốc chi nhánh", null, null));

        assertFalse(r.isValid());
        assertEquals(MSG_058, r.getError(BranchInfoValidator.FIELD_DIRECTOR_NAME));
        assertNull(r.getError(BranchInfoValidator.FIELD_DIRECTOR_POSITION));
    }

    @Test
    public void validate_blankName_msg058() {
        for (String blank : new String[]{"", "   ", "\t \n"}) {
            ValidationResult r = validator.validate(info(blank, "Giám đốc chi nhánh", null, null));
            assertEquals("blank '" + blank + "'", MSG_058, r.getError(BranchInfoValidator.FIELD_DIRECTOR_NAME));
        }
    }

    @Test
    public void validate_missingPosition_msg058OnPositionOnly() {
        ValidationResult r = validator.validate(info("Nguyễn Văn An", "  ", null, null));

        assertEquals(MSG_058, r.getError(BranchInfoValidator.FIELD_DIRECTOR_POSITION));
        assertNull(r.getError(BranchInfoValidator.FIELD_DIRECTOR_NAME));
    }

    /** Dòng gieo sẵn của V39 chỉ có chức vụ: lưu nguyên như vậy phải bị chặn. */
    @Test
    public void validate_seedRowWithPositionOnly_msg058OnName() {
        ValidationResult r = validator.validate(info(null, "Giám đốc chi nhánh", null, null));

        assertEquals(1, r.getErrors().size());
        assertEquals(MSG_058, r.getError(BranchInfoValidator.FIELD_DIRECTOR_NAME));
    }

    @Test
    public void validate_bothMissing_msg058OnBothFields() {
        ValidationResult r = validator.validate(info(null, null, null, null));

        assertEquals(MSG_058, r.getError(BranchInfoValidator.FIELD_DIRECTOR_NAME));
        assertEquals(MSG_058, r.getError(BranchInfoValidator.FIELD_DIRECTOR_POSITION));
    }

    @Test
    public void validate_nullInfo_treatedAsAllBlank() {
        ValidationResult r = validator.validate(null);

        assertEquals(2, r.getErrors().size());
        assertEquals(MSG_058, r.getError(BranchInfoValidator.FIELD_DIRECTOR_NAME));
        assertEquals(MSG_058, r.getError(BranchInfoValidator.FIELD_DIRECTOR_POSITION));
    }

    // ------------------------------------------------------------------
    // Họ tên / chức vụ tối đa 100 ký tự
    // ------------------------------------------------------------------

    @Test
    public void validate_nameAndPositionExactly100Chars_isValid() {
        assertTrue(validator.validate(info(repeat('a', 100), repeat('b', 100), null, null)).isValid());
    }

    @Test
    public void validate_name101Chars_tooLong() {
        ValidationResult r = validator.validate(info(repeat('a', 101), "Giám đốc", null, null));

        assertEquals(BranchInfoValidator.MSG_NAME_TOO_LONG, r.getError(BranchInfoValidator.FIELD_DIRECTOR_NAME));
        assertEquals("Họ tên Giám đốc chi nhánh không được vượt quá 100 ký tự.",
                BranchInfoValidator.MSG_NAME_TOO_LONG);
    }

    @Test
    public void validate_position101Chars_tooLong() {
        ValidationResult r = validator.validate(info("Nguyễn Văn An", repeat('b', 101), null, null));

        assertEquals(BranchInfoValidator.MSG_POSITION_TOO_LONG,
                r.getError(BranchInfoValidator.FIELD_DIRECTOR_POSITION));
        assertEquals("Chức vụ Giám đốc chi nhánh không được vượt quá 100 ký tự.",
                BranchInfoValidator.MSG_POSITION_TOO_LONG);
    }

    /** Trim trước khi đo: 100 ký tự kèm khoảng trắng hai đầu vẫn hợp lệ. */
    @Test
    public void validate_lengthMeasuredAfterTrim() {
        ValidationResult r = validator.validate(info("  " + repeat('a', 100) + "  ", "Giám đốc", null, null));

        assertTrue(r.isValid());
    }

    // ------------------------------------------------------------------
    // SĐT sai (MSG-015)
    // ------------------------------------------------------------------

    @Test
    public void validate_rejectedPhoneFormats_msg015() {
        String[] bad = {
            "09123abc45",               // có chữ cái
            "hotline",                  // không có chữ số
            "1234567",                  // 7 chữ số
            "1234567890123456",         // 16 chữ số
            "(024) 3852 1234 - 1105",   // 15 chữ số nhưng 22 ký tự
            "+84 (024)  3852.12345",    // 14 chữ số nhưng 21 ký tự
            "84+912345678",             // dấu + không ở đầu
            "++84912345678",            // hai dấu +
            "0912_345_678",             // gạch dưới
            "0912/345/678",             // gạch chéo
            "0912345678#12",            // dấu #
            "０９１２３４５６７８"          // chữ số toàn chiều rộng (không phải 0-9 ASCII)
        };
        for (String phone : bad) {
            ValidationResult r = validator.validate(withPhone(phone));
            assertEquals("phải chặn: '" + phone + "'", MSG_015, r.getError(BranchInfoValidator.FIELD_PHONE));
            assertNull(r.getError(BranchInfoValidator.FIELD_FAX));
        }
    }

    // ------------------------------------------------------------------
    // Fax sai (MSG-059)
    // ------------------------------------------------------------------

    @Test
    public void validate_rejectedFaxFormats_msg059() {
        String[] bad = {
            "fax 0233385",
            "1234567",
            "1234567890123456",
            "(024) 3852 1234 - 1105",
            "0233+3852123"
        };
        for (String fax : bad) {
            ValidationResult r = validator.validate(withFax(fax));
            assertEquals("phải chặn: '" + fax + "'", MSG_059, r.getError(BranchInfoValidator.FIELD_FAX));
            assertNull(r.getError(BranchInfoValidator.FIELD_PHONE));
        }
    }

    @Test
    public void validate_phoneAndFaxBothWrong_eachGetsOwnMessage() {
        ValidationResult r = validator.validate(info("Nguyễn Văn An", "Giám đốc", "abc", "xyz"));

        assertEquals(2, r.getErrors().size());
        assertEquals(MSG_015, r.getError(BranchInfoValidator.FIELD_PHONE));
        assertEquals(MSG_059, r.getError(BranchInfoValidator.FIELD_FAX));
    }

    @Test
    public void validate_allFieldsWrong_fourErrors() {
        ValidationResult r = validator.validate(info(" ", null, "12", "34"));

        assertEquals(4, r.getErrors().size());
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    @Test
    public void digitCount_countsAsciiDigitsOnly() {
        assertEquals(0, BranchInfoValidator.digitCount(""));
        assertEquals(10, BranchInfoValidator.digitCount("(024) 3852-123"));
        assertEquals(11, BranchInfoValidator.digitCount("+84 912 345 678"));
        assertEquals(0, BranchInfoValidator.digitCount("０１２"));
    }

    @Test
    public void trimToNull_blankBecomesNull() {
        assertNull(BranchInfoValidator.trimToNull(null));
        assertNull(BranchInfoValidator.trimToNull(""));
        assertNull(BranchInfoValidator.trimToNull("  \t "));
        assertEquals("Giám đốc", BranchInfoValidator.trimToNull("  Giám đốc "));
    }
}
