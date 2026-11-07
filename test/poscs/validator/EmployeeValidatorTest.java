package poscs.validator;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import poscs.model.Address;
import poscs.model.ProvinceAssignment;
import poscs.model.User;

import static org.junit.Assert.*;

/**
 * Unit Test cho EmployeeValidator (UC-24..29):
 * - BR-28: Bắt buộc Họ, Tên, Phòng ban, Vai trò, Ngày vào làm, Email cá nhân hợp lệ.
 * - BR-09: SĐT Việt Nam hợp lệ (nếu có nhập).
 * - BR-10: Email cá nhân đúng định dạng chuẩn RFC.
 * - BR-29: Ngày sinh trong quá khứ (nếu có nhập).
 * - BR-30: Giới tính ∈ {Nam, Nữ, Khác} (nếu có chọn).
 * - Vai trò không được là Admin khi tạo mới, không thăng lên Admin.
 * - Địa bàn mỗi tỉnh một người phụ trách.
 */
public class EmployeeValidatorTest {

    private EmployeeValidator validator;
    private final Set<Integer> adminRoleIds = Set.of(1);

    @Before
    public void setUp() {
        validator = new EmployeeValidator();
    }

    private User createValidUser() {
        User u = new User();
        u.setUserId(10);
        u.setLastName("Nguyễn");
        u.setMiddleName("Văn");
        u.setFirstName("An");
        u.setDepartmentId(2);
        u.setRoleId(2); // Sales
        u.setHireDate(Date.valueOf(LocalDate.of(2023, 1, 15)));
        u.setPersonalEmail("an.nguyen@example.com");
        u.setGender("Nam");
        u.setDateOfBirth(Date.valueOf(LocalDate.of(1995, 5, 20)));
        u.setPhone("0912345678");
        u.setCitizenId("001095012345");
        Address addr = new Address();
        addr.setStreetAndLocalName("123 Phố Vọng");
        u.setAddress(addr);
        return u;
    }

    // -------------------------------------------------------------------------
    // 1. Kịch bản hợp lệ cơ bản
    // -------------------------------------------------------------------------
    @Test
    public void testValidateCreate_validUser() {
        User u = createValidUser();
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertTrue(vr.isValid());
    }

    @Test
    public void testValidateCreate_optionalFieldsNull_valid() {
        User u = new User();
        u.setLastName("Trần");
        u.setFirstName("Bình");
        u.setDepartmentId(1);
        u.setRoleId(3); // Kỹ thuật
        u.setHireDate(Date.valueOf("2024-02-01"));
        u.setPersonalEmail("binh.tran@gmail.com");
        // gender, dob, phone, citizenId, address đều null
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertTrue(vr.isValid());
    }

    @Test
    public void testValidateCreate_nullUser_fails() {
        ValidationResult vr = validator.validateCreate(null, adminRoleIds);
        assertFalse(vr.isValid());
    }

    // -------------------------------------------------------------------------
    // 2. BR-28: Bắt buộc Họ, Tên, Phòng ban, Vai trò, Ngày vào làm, Email
    // -------------------------------------------------------------------------
    @Test
    public void testLastName_nullOrBlank_fails() {
        User u = createValidUser();
        u.setLastName(null);
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_REQUIRED, vr.getError(EmployeeValidator.FIELD_LAST_NAME));

        u.setLastName("   ");
        vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_REQUIRED, vr.getError(EmployeeValidator.FIELD_LAST_NAME));
    }

    @Test
    public void testLastName_exceedLength_fails() {
        User u = createValidUser();
        u.setLastName("A".repeat(51));
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_LENGTH, vr.getError(EmployeeValidator.FIELD_LAST_NAME));
    }

    @Test
    public void testLastName_unsafeChars_fails() {
        User u = createValidUser();
        u.setLastName("<script>alert(1)</script>");
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_CHARACTERS, vr.getError(EmployeeValidator.FIELD_LAST_NAME));
    }

    @Test
    public void testFirstName_nullOrBlank_fails() {
        User u = createValidUser();
        u.setFirstName(null);
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_REQUIRED, vr.getError(EmployeeValidator.FIELD_FIRST_NAME));

        u.setFirstName("   ");
        vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_REQUIRED, vr.getError(EmployeeValidator.FIELD_FIRST_NAME));
    }

    @Test
    public void testFirstName_exceedLength_fails() {
        User u = createValidUser();
        u.setFirstName("B".repeat(51));
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_LENGTH, vr.getError(EmployeeValidator.FIELD_FIRST_NAME));
    }

    @Test
    public void testMiddleName_exceedLength_fails() {
        User u = createValidUser();
        u.setMiddleName("C".repeat(101));
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_LENGTH, vr.getError(EmployeeValidator.FIELD_MIDDLE_NAME));
    }

    @Test
    public void testDepartmentAndRole_invalidId_fails() {
        User u = createValidUser();
        u.setDepartmentId(0);
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_REQUIRED, vr.getError(EmployeeValidator.FIELD_DEPARTMENT_ID));

        u = createValidUser();
        u.setRoleId(-1);
        vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_REQUIRED, vr.getError(EmployeeValidator.FIELD_ROLE_ID));
    }

    @Test
    public void testHireDate_null_fails() {
        User u = createValidUser();
        u.setHireDate(null);
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_REQUIRED, vr.getError(EmployeeValidator.FIELD_HIRE_DATE));
    }

    @Test
    public void testPersonalEmail_nullOrBlank_fails() {
        User u = createValidUser();
        u.setPersonalEmail(null);
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_REQUIRED, vr.getError(EmployeeValidator.FIELD_PERSONAL_EMAIL));

        u.setPersonalEmail("  ");
        vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_REQUIRED, vr.getError(EmployeeValidator.FIELD_PERSONAL_EMAIL));
    }

    // -------------------------------------------------------------------------
    // 3. BR-10: Email cá nhân đúng chuẩn RFC, độ dài tối đa 254
    // -------------------------------------------------------------------------
    @Test
    public void testPersonalEmail_invalidFormat_fails() {
        User u = createValidUser();
        u.setPersonalEmail("invalid-email");
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_EMAIL, vr.getError(EmployeeValidator.FIELD_PERSONAL_EMAIL));

        u.setPersonalEmail("user@");
        vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_EMAIL, vr.getError(EmployeeValidator.FIELD_PERSONAL_EMAIL));

        u.setPersonalEmail("@domain.com");
        vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_EMAIL, vr.getError(EmployeeValidator.FIELD_PERSONAL_EMAIL));
    }

    @Test
    public void testPersonalEmail_exceedLength_fails() {
        User u = createValidUser();
        u.setPersonalEmail("a".repeat(245) + "@domain.com"); // > 254 chars
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_EMAIL, vr.getError(EmployeeValidator.FIELD_PERSONAL_EMAIL));
    }

    // -------------------------------------------------------------------------
    // 4. BR-09: SĐT Việt Nam hợp lệ
    // -------------------------------------------------------------------------
    @Test
    public void testPhone_validFormats() {
        assertTrue(validator.isValidPhone(null));
        assertTrue(validator.isValidPhone(""));
        assertTrue(validator.isValidPhone("   "));
        assertTrue(validator.isValidPhone("0912345678"));
        assertTrue(validator.isValidPhone("0323456789"));
        assertTrue(validator.isValidPhone("+84912345678"));
        assertTrue(validator.isValidPhone("0912 345 678"));
        assertTrue(validator.isValidPhone("0912.345.678"));
        assertTrue(validator.isValidPhone("0912-345-678"));
    }

    @Test
    public void testPhone_invalidFormats() {
        User u = createValidUser();
        u.setPhone("12345"); // quá ngắn
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_PHONE, vr.getError(EmployeeValidator.FIELD_PHONE));

        u.setPhone("0123456789"); // đầu số 01 không tồn tại ở VN
        vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_PHONE, vr.getError(EmployeeValidator.FIELD_PHONE));

        u.setPhone("09123456789"); // 11 số (vượt quá 10 số của SĐT VN)
        vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_PHONE, vr.getError(EmployeeValidator.FIELD_PHONE));

        u.setPhone("0912abcd89"); // chứa chữ
        vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_PHONE, vr.getError(EmployeeValidator.FIELD_PHONE));
    }

    @Test
    public void testNormalizePhone() {
        assertNull(EmployeeValidator.normalizePhone(null));
        assertNull(EmployeeValidator.normalizePhone(""));
        assertNull(EmployeeValidator.normalizePhone("   "));
        assertEquals("0987654321", EmployeeValidator.normalizePhone("0987 654 321"));
        assertEquals("0987654321", EmployeeValidator.normalizePhone("0987-654-321"));
        assertEquals("0987654321", EmployeeValidator.normalizePhone("0987.654.321"));
        assertEquals("0987654321", EmployeeValidator.normalizePhone("+84987654321"));
    }

    @Test
    public void testNormalizeCitizenIdAndTrim() {
        assertNull(EmployeeValidator.normalizeCitizenId(null));
        assertNull(EmployeeValidator.normalizeCitizenId(""));
        assertNull(EmployeeValidator.normalizeCitizenId("   "));
        assertEquals("001092001122", EmployeeValidator.normalizeCitizenId("  001092001122  "));

        assertNull(EmployeeValidator.trimToNull(null));
        assertNull(EmployeeValidator.trimToNull("   "));
        assertEquals("abc", EmployeeValidator.trimToNull("  abc  "));
    }

    // -------------------------------------------------------------------------
    // 5. BR-29: Ngày sinh trong quá khứ
    // -------------------------------------------------------------------------
    @Test
    public void testDateOfBirth_past_valid() {
        User u = createValidUser();
        u.setDateOfBirth(Date.valueOf(LocalDate.now().minusYears(20)));
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertNull(vr.getError(EmployeeValidator.FIELD_DATE_OF_BIRTH));
    }

    @Test
    public void testDateOfBirth_todayOrFuture_fails() {
        User u = createValidUser();
        u.setDateOfBirth(Date.valueOf(LocalDate.now())); // today
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_DOB, vr.getError(EmployeeValidator.FIELD_DATE_OF_BIRTH));

        u.setDateOfBirth(Date.valueOf(LocalDate.now().plusDays(1))); // future
        vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_DOB, vr.getError(EmployeeValidator.FIELD_DATE_OF_BIRTH));
    }

    // -------------------------------------------------------------------------
    // 6. BR-30: Giới tính ∈ {Nam, Nữ, Khác}
    // -------------------------------------------------------------------------
    @Test
    public void testGender_validAndInvalid() {
        assertTrue(validator.isValidGender(null));
        assertTrue(validator.isValidGender(""));
        assertTrue(validator.isValidGender("Nam"));
        assertTrue(validator.isValidGender("Nữ"));
        assertTrue(validator.isValidGender("Khác"));

        User u = createValidUser();
        u.setGender("Không xác định");
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_GENDER, vr.getError(EmployeeValidator.FIELD_GENDER));
    }

    // -------------------------------------------------------------------------
    // 7. Ô Vai trò không có Admin, không thăng lên Admin
    // -------------------------------------------------------------------------
    @Test
    public void testCreate_roleIsAdmin_fails() {
        User u = createValidUser();
        u.setRoleId(1); // Admin
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_ADMIN_ROLE_FORBIDDEN, vr.getError(EmployeeValidator.FIELD_ROLE_ID));
    }

    @Test
    public void testUpdate_promoteToAdmin_fails() {
        User existing = createSampleUser(10, 2); // Sales
        User u = createSampleUser(10, 1); // Thăng lên Admin
        ValidationResult vr = validator.validateUpdate(u, existing, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_ADMIN_ROLE_FORBIDDEN, vr.getError(EmployeeValidator.FIELD_ROLE_ID));
    }

    @Test
    public void testUpdate_existingAdminKeepsAdmin_valid() {
        User existing = createSampleUser(10, 1); // Đã là Admin
        User u = createSampleUser(10, 1); // Giữ nguyên Admin
        ValidationResult vr = validator.validateUpdate(u, existing, adminRoleIds);
        assertNull(vr.getError(EmployeeValidator.FIELD_ROLE_ID));
        assertTrue(vr.isValid());
    }

    // -------------------------------------------------------------------------
    // 8. Địa bàn: mỗi tỉnh một người phụ trách
    // -------------------------------------------------------------------------
    @Test
    public void testAnyProvinceHeldByOthers() {
        List<ProvinceAssignment> assignments = List.of(
                new ProvinceAssignment(101, "Hà Nội", 5, "Nguyễn Văn X"),
                new ProvinceAssignment(102, "Hải Phòng", null, null),
                new ProvinceAssignment(103, "Đà Nẵng", 10, "Nguyễn Văn An")
        );

        // Tạo mới (editingUserId = null) chọn tỉnh 101 (đang có người cầm) -> vi phạm
        assertTrue(validator.anyProvinceHeldByOthers(List.of(101), assignments, null));

        // Tạo mới chọn tỉnh 102 (chưa ai cầm) -> hợp lệ
        assertFalse(validator.anyProvinceHeldByOthers(List.of(102), assignments, null));

        // Sửa nhân viên 10 chọn tỉnh 103 (do chính mình đang cầm) -> hợp lệ
        assertFalse(validator.anyProvinceHeldByOthers(List.of(103), assignments, 10));

        // Sửa nhân viên 10 chọn tỉnh 101 (do nhân viên 5 cầm) -> vi phạm
        assertTrue(validator.anyProvinceHeldByOthers(List.of(101), assignments, 10));

        // Danh sách rỗng -> hợp lệ
        assertFalse(validator.anyProvinceHeldByOthers(List.of(), assignments, 10));
        assertFalse(validator.anyProvinceHeldByOthers(null, assignments, 10));
    }

    // -------------------------------------------------------------------------
    // 9. CCCD và Địa chỉ
    // -------------------------------------------------------------------------
    @Test
    public void testCitizenId_exceedLength_fails() {
        User u = createValidUser();
        u.setCitizenId("012345678901234567891"); // 21 chars > 20
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_LENGTH, vr.getError(EmployeeValidator.FIELD_CITIZEN_ID));
    }

    @Test
    public void testAddress_unsafeChars_fails() {
        User u = createValidUser();
        Address addr = new Address();
        addr.setStreetAndLocalName("<script>alert('hack')</script>");
        u.setAddress(addr);
        ValidationResult vr = validator.validateCreate(u, adminRoleIds);
        assertEquals(EmployeeValidator.ERR_INVALID_CHARACTERS, vr.getError(EmployeeValidator.FIELD_ADDRESS));
    }

    // -------------------------------------------------------------------------
    // 10. isValidCommonFields (tương thích ngược)
    // -------------------------------------------------------------------------
    @Test
    public void testIsValidCommonFields() {
        User u = createValidUser();
        assertTrue(validator.isValidCommonFields(u));
        assertFalse(validator.isValidCommonFields(null));

        u.setLastName("");
        assertFalse(validator.isValidCommonFields(u));
    }

    private User createSampleUser(int id, int roleId) {
        User u = createValidUser();
        u.setUserId(id);
        u.setRoleId(roleId);
        return u;
    }
}
