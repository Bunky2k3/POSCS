package poscs.validator;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import poscs.common.TextRules;
import poscs.model.User;

public class ProfileValidator extends BaseValidator {

    public static final int MAX_NAME_LENGTH = 50;
    public static final int MAX_CITIZEN_ID_LENGTH = 12;
    public static final int MAX_ADDRESS_DETAIL_LENGTH = 100;
    public static final int MAX_EMAIL_LENGTH = 254;

    public ValidationResult validateProfileUpdate(String lastName, String middleName, String firstName,
            String citizenId, String phone, String personalEmail, String gender, String dobStr, Integer districtId, String addressDetail) {
        
        ValidationResult result = new ValidationResult();

        if (lastName == null || firstName == null || citizenId == null) {
            result.addError("general", "missing_fields");
            return result;
        }

        if (lastName.length() > MAX_NAME_LENGTH || firstName.length() > MAX_NAME_LENGTH 
                || (middleName != null && middleName.length() > MAX_NAME_LENGTH)
                || citizenId.length() > MAX_CITIZEN_ID_LENGTH
                || (addressDetail != null && addressDetail.length() > MAX_ADDRESS_DETAIL_LENGTH)) {
            result.addError("general", "invalid_length");
            return result;
        }

        if (!TextRules.isSafeFreeText(lastName) || !TextRules.isSafeFreeText(middleName)
                || !TextRules.isSafeFreeText(firstName) || !TextRules.isSafeFreeText(addressDetail)) {
            result.addError("general", "invalid_characters");
            return result;
        }

        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone == null || !normalizedPhone.matches("^(0)(3|5|7|8|9)[0-9]{8}$")) {
            result.addError("phone", "invalid_phone");
        }

        if (personalEmail == null || personalEmail.length() > MAX_EMAIL_LENGTH 
                || !personalEmail.matches("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$")) {
            result.addError("email", "invalid_email");
        }

        if (!"Nam".equals(gender) && !"Nữ".equals(gender) && !"Khác".equals(gender)) {
            result.addError("gender", "invalid_gender");
        }

        try {
            if (dobStr == null || dobStr.trim().isEmpty()) {
                result.addError("dob", "invalid_dob");
            } else {
                LocalDate dob = LocalDate.parse(dobStr.trim());
                if (dob.getYear() < 1900 || !dob.isBefore(LocalDate.now())) {
                    result.addError("dob", "invalid_dob");
                }
            }
        } catch (DateTimeParseException ex) {
            result.addError("dob", "invalid_dob");
        }

        if (districtId == null || districtId <= 0) {
            result.addError("address", "missing_address");
        }

        return result;
    }

    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        String p = phone.replaceAll("\\s", "");
        if (p.startsWith("+84")) {
            p = "0" + p.substring(3);
        }
        return p;
    }
}
