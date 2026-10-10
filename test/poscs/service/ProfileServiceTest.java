package poscs.service;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import poscs.dao.AddressDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.User;
import poscs.validator.ProfileValidator;

public class ProfileServiceTest {

    @Test
    public void testUpdateProfile() {
        EmployeeDAO employeeDAO = mock(EmployeeDAO.class);
        AddressDAO addressDAO = mock(AddressDAO.class);
        ProfileValidator validator = new ProfileValidator();
        ProfileService service = new ProfileService(employeeDAO, addressDAO, validator);

        User current = new User();
        current.setUserId(1);

        when(addressDAO.findProvinceIdOfWard(1)).thenReturn(10);
        when(employeeDAO.existsByPhone(anyString(), anyInt())).thenReturn(false);
        when(employeeDAO.existsByCitizenId(anyString(), anyInt())).thenReturn(false);
        when(employeeDAO.updateProfile(any(User.class))).thenReturn(true);

        ServiceResult<User> r = service.updateProfile(current, "Nguyen", "Van", "A", 
                "012345678901", "0912345678", "a@b.com", "Nam", "1990-01-01", 1, "Hanoi", "avatar.jpg");
        assertTrue(r.isSuccess());
    }
}
