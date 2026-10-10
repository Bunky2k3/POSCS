package poscs.service;

import java.sql.Date;
import java.time.LocalDate;
import poscs.dao.AddressDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Address;
import poscs.model.User;
import poscs.validator.ProfileValidator;
import poscs.validator.ValidationResult;

public class ProfileService extends BaseService {

    private final EmployeeDAO employeeDAO;
    private final AddressDAO addressDAO;
    private final ProfileValidator validator;

    public ProfileService() {
        this.employeeDAO = new EmployeeDAO();
        this.addressDAO = new AddressDAO();
        this.validator = new ProfileValidator();
    }

    public ProfileService(EmployeeDAO employeeDAO, AddressDAO addressDAO, ProfileValidator validator) {
        this.employeeDAO = employeeDAO;
        this.addressDAO = addressDAO;
        this.validator = validator;
    }

    public ServiceResult<User> updateProfile(User currentUser, String lastName, String middleName, String firstName,
            String citizenId, String phone, String personalEmail, String gender, String dobStr, Integer districtId, 
            String addressDetail, String newAvatarUrl) {

        if (currentUser == null) {
            return failure("unauthorized");
        }

        ValidationResult validation = validator.validateProfileUpdate(lastName, middleName, firstName, citizenId, phone, personalEmail, gender, dobStr, districtId, addressDetail);
        if (!validation.isValid()) {
            return failure(validation.getErrors().values().iterator().next());
        }

        String normalizedPhone = ProfileValidator.normalizePhone(phone);

        if (employeeDAO.existsByPhone(normalizedPhone, currentUser.getUserId())) {
            return failure("duplicate_phone");
        }
        if (employeeDAO.existsByCitizenId(citizenId, currentUser.getUserId())) {
            return failure("duplicate_citizen");
        }
        
        // Verify district (ward) exists
        if (addressDAO.findProvinceIdOfWard(districtId) == null) {
            return failure("invalid_address");
        }

        User currentProfile = employeeDAO.findProfileById(currentUser.getUserId());

        User user = new User();
        user.setUserId(currentUser.getUserId());
        user.setLastName(lastName);
        user.setMiddleName(middleName);
        user.setFirstName(firstName);
        user.setGender(gender);
        user.setDateOfBirth(Date.valueOf(LocalDate.parse(dobStr.trim())));
        user.setCitizenId(citizenId);
        user.setPhone(normalizedPhone);
        user.setPersonalEmail(personalEmail);

        user.setAvatarUrl(newAvatarUrl != null ? newAvatarUrl
                : (currentProfile != null ? currentProfile.getAvatarUrl() : null));

        Address address = new Address();
        if (currentProfile != null && currentProfile.getAddress() != null) {
            address.setAddressId(currentProfile.getAddress().getAddressId());
        }
        address.setStreetAndLocalName(addressDetail != null ? addressDetail : "");
        address.setDistrictId(districtId);
        user.setAddress(address);

        boolean ok = employeeDAO.updateProfile(user);
        if (!ok) {
            return failure("update_failed");
        }

        return success(user);
    }
}
