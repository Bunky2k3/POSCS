package poscs.validator;

import java.sql.Date;
import java.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import poscs.model.Address;
import poscs.model.Enterprise;

import static org.junit.Assert.*;

/**
 * Unit Test cho CustomerValidator:
 * - BR-09: Định dạng SĐT Việt Nam (di động, cố định, tiền tố +84, khoảng trắng/chấm/gạch ngang).
 * - BR-10: Định dạng email (hợp lệ, max 254 ký tự, không chứa khoảng trắng).
 * - BR-11: Ràng buộc trường bắt buộc (tên, loại, nhóm, MST, địa chỉ, người phụ trách chính).
 * - BR-52: Định dạng mã KH-xxxx và NCC-xxxx.
 * - Tương thích logic gốc của isValidCommonFields.
 */
public class CustomerValidatorTest {

    private CustomerValidator validator;

    @Before
    public void setUp() {
        validator = new CustomerValidator();
    }

    // -------------------------------------------------------------
    // BR-09: Phone format tests
    // -------------------------------------------------------------
    @Test
    public void testValidPhone_standardMobile() {
        assertTrue(validator.isValidPhone("0912345678"));
        assertTrue(validator.isValidPhone("0381234567"));
        assertTrue(validator.isValidPhone("0791234567"));
        assertTrue(validator.isValidPhone("0561234567"));
        assertTrue(validator.isValidPhone("0891234567"));
    }

    @Test
    public void testValidPhone_withPrefix84AndDelimiters() {
        assertTrue(validator.isValidPhone("+84912345678"));
        assertTrue(validator.isValidPhone("+84 912 345 678"));
        assertTrue(validator.isValidPhone("0912-345-678"));
        assertTrue(validator.isValidPhone("0912.345.678"));
        // Cố định Việt Nam 11 số (VD: 024 3822 1234)
        assertTrue(validator.isValidPhone("02438221234"));
        assertTrue(validator.isValidPhone("024 3822 1234"));
        assertTrue(validator.isValidPhone("+842438221234"));
    }

    @Test
    public void testInvalidPhone() {
        assertFalse(validator.isValidPhone(null));
        assertFalse(validator.isValidPhone(""));
        assertFalse(validator.isValidPhone("   "));
        assertFalse(validator.isValidPhone("not-a-phone"));
        assertFalse(validator.isValidPhone("12345"));
        assertFalse(validator.isValidPhone("09123456789999")); // quá dài
        assertFalse(validator.isValidPhone("1912345678")); // không bắt đầu bằng 0 hoặc +84
    }

    @Test
    public void testValidMobilePhone_strictVnMobile() {
        assertTrue(validator.isValidMobilePhone("0912345678"));
        assertTrue(validator.isValidMobilePhone("+84912345678"));
        assertTrue(validator.isValidMobilePhone(" 0912-345-678 "));
        // Cố định không phải là mobile
        assertFalse(validator.isValidMobilePhone("02438221234"));
    }

    // -------------------------------------------------------------
    // BR-10: Email format tests
    // -------------------------------------------------------------
    @Test
    public void testValidEmail() {
        assertTrue(validator.isValidEmail("customer@company.com"));
        assertTrue(validator.isValidEmail("sales.support+tag@sub.domain.vn"));
        assertTrue(validator.isValidEmail("a@b.co"));
    }

    @Test
    public void testInvalidEmail() {
        assertFalse(validator.isValidEmail(null));
        assertFalse(validator.isValidEmail(""));
        assertFalse(validator.isValidEmail("   "));
        assertFalse(validator.isValidEmail("plainaddress"));
        assertFalse(validator.isValidEmail("@missingusername.com"));
        assertFalse(validator.isValidEmail("username@.com"));
        assertFalse(validator.isValidEmail("username@domain"));
        assertFalse(validator.isValidEmail("user name@domain.com")); // chứa dấu cách

        // Vượt quá 254 ký tự
        String longEmail = "a".repeat(245) + "@domain.com";
        assertFalse(validator.isValidEmail(longEmail));
    }

    // -------------------------------------------------------------
    // BR-52: Enterprise Code tests (KH-xxxx, NCC-xxxx)
    // -------------------------------------------------------------
    @Test
    public void testValidCustomerCode() {
        assertTrue(validator.isValidCustomerCode("KH-0001"));
        assertTrue(validator.isValidCustomerCode("KH-1234"));
        assertTrue(validator.isValidCustomerCode("KH-1"));
        assertTrue(validator.isValidCustomerCode("KH-99999"));

        assertFalse(validator.isValidCustomerCode(null));
        assertFalse(validator.isValidCustomerCode(""));
        assertFalse(validator.isValidCustomerCode("NCC-0001"));
        assertFalse(validator.isValidCustomerCode("KH-ABCD"));
        assertFalse(validator.isValidCustomerCode("KH_0001"));
        assertFalse(validator.isValidCustomerCode("0001"));
    }

    @Test
    public void testValidSupplierCode() {
        assertTrue(validator.isValidSupplierCode("NCC-001"));
        assertTrue(validator.isValidSupplierCode("NCC-0001"));
        assertTrue(validator.isValidSupplierCode("NCC-99"));

        assertFalse(validator.isValidSupplierCode(null));
        assertFalse(validator.isValidSupplierCode(""));
        assertFalse(validator.isValidSupplierCode("KH-0001"));
        assertFalse(validator.isValidSupplierCode("NCC-XYZ"));
        assertFalse(validator.isValidSupplierCode("NCC_001"));
    }

    @Test
    public void testValidEnterpriseCode() {
        assertTrue(validator.isValidEnterpriseCode("KH-0001"));
        assertTrue(validator.isValidEnterpriseCode("NCC-001"));
        assertTrue(validator.isValidEnterpriseCode("KH-100"));
        assertTrue(validator.isValidEnterpriseCode("NCC-1000"));

        assertFalse(validator.isValidEnterpriseCode(null));
        assertFalse(validator.isValidEnterpriseCode(""));
        assertFalse(validator.isValidEnterpriseCode("HD-0001"));
        assertFalse(validator.isValidEnterpriseCode("SP-001"));
    }

    // -------------------------------------------------------------
    // BR-11 & Logic gốc: isValidCommonFields tests
    // -------------------------------------------------------------
    @Test
    public void testIsValidCommonFields_valid() {
        Enterprise e = createValidEnterprise();
        assertTrue(validator.isValidCommonFields(e));
    }

    @Test
    public void testIsValidCommonFields_missingRequiredFields() {
        Enterprise e = createValidEnterprise();
        e.setEnterpriseName(null);
        assertFalse(validator.isValidCommonFields(e));

        e = createValidEnterprise();
        e.setCustomerType("");
        assertFalse(validator.isValidCommonFields(e));

        e = createValidEnterprise();
        e.setCustomerGroup("   ");
        assertFalse(validator.isValidCommonFields(e));

        e = createValidEnterprise();
        e.setAccountOwnerId(0);
        assertFalse(validator.isValidCommonFields(e));
    }

    @Test
    public void testIsValidCommonFields_sameOwnerAndSupport() {
        Enterprise e = createValidEnterprise();
        e.setAccountOwnerId(10);
        e.setSupportOwnerId(10); // Trùng người phụ trách
        assertFalse(validator.isValidCommonFields(e));

        e.setSupportOwnerId(11); // Khác người phụ trách
        assertTrue(validator.isValidCommonFields(e));

        e.setSupportOwnerId(null); // Không có người hỗ trợ -> vẫn hợp lệ
        assertTrue(validator.isValidCommonFields(e));
    }

    @Test
    public void testIsValidCommonFields_phoneAndEmail() {
        Enterprise e = createValidEnterprise();
        e.setPhone("not-a-phone");
        assertFalse(validator.isValidCommonFields(e));

        e = createValidEnterprise();
        e.setEmail("not-an-email");
        assertFalse(validator.isValidCommonFields(e));

        e = createValidEnterprise();
        e.setEmail(null);
        assertFalse(validator.isValidCommonFields(e));
    }

    @Test
    public void testIsValidCommonFields_futureJoinDate() {
        Enterprise e = createValidEnterprise();
        e.setJoinDate(Date.valueOf(LocalDate.now().plusDays(1)));
        assertFalse(validator.isValidCommonFields(e));

        e.setJoinDate(Date.valueOf(LocalDate.now()));
        assertTrue(validator.isValidCommonFields(e));

        e.setJoinDate(null);
        assertTrue(validator.isValidCommonFields(e));
    }

    // -------------------------------------------------------------
    // Detailed validate() tests
    // -------------------------------------------------------------
    @Test
    public void testValidateCreate_success() {
        Enterprise e = createValidEnterprise();
        e.setEnterpriseCode("KH-0001");
        ValidationResult result = validator.validate(e, true);
        assertTrue(result.isValid());
    }

    @Test
    public void testValidateCreate_missingTaxCodeOrAddress() {
        Enterprise e = createValidEnterprise();
        e.setTaxCode(null);
        ValidationResult r1 = validator.validate(e, true);
        assertFalse(r1.isValid());
        assertEquals(CustomerValidator.ERR_REQUIRED, r1.getError("taxCode"));

        e = createValidEnterprise();
        e.setAddress(null);
        ValidationResult r2 = validator.validate(e, true);
        assertFalse(r2.isValid());
        assertEquals(CustomerValidator.ERR_REQUIRED, r2.getError("address"));
    }

    @Test
    public void testValidateUpdate_allowsExistingAddressId() {
        Enterprise e = createValidEnterprise();
        e.setTaxCode(null); // Khi update không bắt buộc taxCode
        e.setAddress(null); // Không gửi địa chỉ mới
        e.setAddressId(100); // Nhưng đã có addressId cũ
        ValidationResult result = validator.validate(e, false);
        assertTrue(result.isValid());
    }

    @Test
    public void testValidate_invalidCodePattern() {
        Enterprise e = createValidEnterprise();
        e.setEnterpriseCode("INVALID-CODE");
        ValidationResult result = validator.validate(e, true);
        assertFalse(result.isValid());
        assertEquals(CustomerValidator.ERR_INVALID_CODE, result.getError("enterpriseCode"));
    }

    // -------------------------------------------------------------
    // Address validation tests (QA 1.2)
    // -------------------------------------------------------------
    @Test
    public void testIsValidAddress() {
        Address valid = new Address();
        valid.setStreetAndLocalName("123 Phố Huế");
        valid.setDistrictId(1);
        assertTrue(validator.isValidAddress(valid));

        assertFalse(validator.isValidAddress(null));

        Address emptyStreet = new Address();
        emptyStreet.setStreetAndLocalName("   ");
        emptyStreet.setDistrictId(1);
        assertFalse(validator.isValidAddress(emptyStreet));

        Address missingDistrict = new Address();
        missingDistrict.setStreetAndLocalName("123 Phố Huế");
        missingDistrict.setDistrictId(0);
        assertFalse(validator.isValidAddress(missingDistrict));
    }

    @Test
    public void testValidateCreate_invalidAddressObject_failsValidation() {
        Enterprise e = createValidEnterprise();
        Address emptyAddr = new Address();
        emptyAddr.setStreetAndLocalName(""); // Rỗng
        emptyAddr.setDistrictId(0);
        e.setAddress(emptyAddr);

        ValidationResult result = validator.validate(e, true);
        assertFalse(result.isValid());
        assertEquals(CustomerValidator.ERR_INVALID_ADDRESS, result.getError("address"));
    }

    @Test
    public void testValidateUpdate_invalidAddressObject_failsValidation() {
        Enterprise e = createValidEnterprise();
        e.setAddressId(10);
        Address emptyAddr = new Address();
        emptyAddr.setStreetAndLocalName("");
        emptyAddr.setDistrictId(0);
        e.setAddress(emptyAddr); // Cung cấp address mới nhưng không hợp lệ

        ValidationResult result = validator.validate(e, false);
        assertFalse(result.isValid());
        assertEquals(CustomerValidator.ERR_INVALID_ADDRESS, result.getError("address"));
    }

    private Enterprise createValidEnterprise() {
        Enterprise e = new Enterprise();
        e.setEnterpriseName("Công ty TNHH Giải Pháp Công Nghệ");
        e.setCustomerType("Khách hàng B2B");
        e.setCustomerGroup("Doanh nghiệp");
        e.setTaxCode("0101234567");
        e.setPhone("0912345678");
        e.setEmail("contact@solutions.vn");
        e.setAccountOwnerId(5);
        e.setSupportOwnerId(6);
        e.setJoinDate(Date.valueOf(LocalDate.now()));
        Address address = new Address();
        address.setStreetAndLocalName("123 Phố Huế");
        address.setDistrictId(1);
        e.setAddress(address);
        return e;
    }
}
