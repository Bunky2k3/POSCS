package poscs.controller;

import java.lang.reflect.Field;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.mindrot.jbcrypt.BCrypt;
import poscs.dao.AddressDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Role;
import poscs.model.User;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test tầng Controller cho AuthenticationController -- luồng bảo mật quan
 * trọng nhất trong app (login, đổi mật khẩu, session). Trọng tâm:
 * <ul>
 *   <li>Không phân biệt lỗi "sai username" vs "sai mật khẩu" (chống user
 *       enumeration) -- cả 2 phải cùng redirect error=invalid_credentials.</li>
 *   <li>Chỉ kiểm tra tài khoản bị khoá SAU KHI đã xác minh đúng mật khẩu
 *       (cùng lý do chống enumeration, xem comment gốc ở handleLogin).</li>
 *   <li>Session fixation: đăng nhập thành công phải huỷ session cũ trước khi
 *       tạo session mới, không tái sử dụng session hiện có.</li>
 *   <li>Đổi mật khẩu: bắt buộc đúng mật khẩu cũ, mật khẩu mới đủ mạnh, khác
 *       mật khẩu cũ, và phải invalidate session sau khi đổi xong.</li>
 * </ul>
 * BCrypt dùng thật (không mock) vì đây là hàm thuần, không I/O -- mock nó đi
 * sẽ làm test không còn xác minh được logic thật.
 * JUnit 4 (không phải 5) vì build-impl.xml chỉ nối sẵn Ant &lt;junit&gt; task
 * cổ điển -- xem CustomerControllerTest.
 */
public class AuthenticationControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private AuthenticationController controller;
    private EmployeeDAO employeeDAO;
    private AddressDAO addressDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;

    @Before
    public void setUp() throws Exception {
        controller = new AuthenticationController();

        employeeDAO = mock(EmployeeDAO.class);
        addressDAO = mock(AddressDAO.class);
        setField(controller, "employeeDAO", employeeDAO);
        setField(controller, "addressDAO", addressDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static User userWithPassword(String plainPassword) {
        User u = new User();
        u.setUserId(7);
        u.setUsername("annd");
        u.setPasswordHash(BCrypt.hashpw(plainPassword, BCrypt.gensalt()));
        u.setRole(new Role(2, "Sales"));
        return u;
    }

    private HttpSession loggedInSession(User currentUser) {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("currentUser")).thenReturn(currentUser);
        when(request.getSession(false)).thenReturn(session);
        return session;
    }

    // ------------------------------------------------------------------
    // GET /login?action=logout
    // ------------------------------------------------------------------

    @Test
    public void logout_withActiveSession_invalidatesItAndRedirectsToLoginPage() throws Exception {
        when(request.getServletPath()).thenReturn("/login");
        when(request.getParameter("action")).thenReturn("logout");
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);

        controller.doGet(request, response);

        verify(session).invalidate();
        verify(response).sendRedirect(CONTEXT_PATH + "/login.jsp");
    }

    @Test
    public void logout_withoutSession_doesNotThrowAndRedirects() throws Exception {
        when(request.getServletPath()).thenReturn("/login");
        when(request.getParameter("action")).thenReturn("logout");
        when(request.getSession(false)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/login.jsp");
    }

    // ------------------------------------------------------------------
    // GET /viewProfile, /updateProfile
    // ------------------------------------------------------------------

    @Test
    public void viewProfile_notLoggedIn_redirectsToLoginPage() throws Exception {
        when(request.getServletPath()).thenReturn("/viewProfile");
        when(request.getSession(false)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/login.jsp");
    }

    @Test
    public void viewProfile_loggedInButProfileMissingFromDb_redirectsToLoginPage() throws Exception {
        when(request.getServletPath()).thenReturn("/viewProfile");
        loggedInSession(userWithPassword("whatever"));
        when(employeeDAO.findProfileById(7)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/login.jsp");
    }

    @Test
    public void viewProfile_found_forwardsWithProfileAttribute() throws Exception {
        when(request.getServletPath()).thenReturn("/viewProfile");
        loggedInSession(userWithPassword("whatever"));
        User profile = userWithPassword("whatever");
        when(employeeDAO.findProfileById(7)).thenReturn(profile);

        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/viewProfile.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(request).setAttribute("profile", profile);
        verify(dispatcher).forward(request, response);
    }

    // ------------------------------------------------------------------
    // POST /login (handleLogin) -- chống user enumeration + session fixation
    // ------------------------------------------------------------------

    @Test
    public void login_missingFields_redirectsWithMissingFieldsError() throws Exception {
        when(request.getServletPath()).thenReturn("/login");
        when(request.getParameter("username")).thenReturn(null);
        when(request.getParameter("password")).thenReturn("anything");

        controller.doPost(request, response);

        verify(response).sendRedirect(contains("error=missing_fields"));
    }

    @Test
    public void login_unknownUser_redirectsWithSameErrorAsWrongPassword() throws Exception {
        when(request.getServletPath()).thenReturn("/login");
        when(request.getParameter("username")).thenReturn("ghost");
        when(request.getParameter("password")).thenReturn("whatever");
        when(employeeDAO.findByUsernameOrEmail("ghost")).thenReturn(null);

        controller.doPost(request, response);

        // Không được lộ khác biệt "không tồn tại" vs "sai mật khẩu" -- cùng 1 mã lỗi.
        verify(response).sendRedirect(contains("error=invalid_credentials"));
    }

    @Test
    public void login_wrongPassword_redirectsWithInvalidCredentialsError() throws Exception {
        when(request.getServletPath()).thenReturn("/login");
        when(request.getParameter("username")).thenReturn("annd");
        when(request.getParameter("password")).thenReturn("wrong-password");
        when(employeeDAO.findByUsernameOrEmail("annd")).thenReturn(userWithPassword("correct-password"));

        controller.doPost(request, response);

        verify(response).sendRedirect(contains("error=invalid_credentials"));
    }

    @Test
    public void login_correctPasswordButAccountDeactivated_redirectsWithAccountInactiveError() throws Exception {
        when(request.getServletPath()).thenReturn("/login");
        when(request.getParameter("username")).thenReturn("annd");
        when(request.getParameter("password")).thenReturn("correct-password");
        User deactivated = userWithPassword("correct-password");
        deactivated.setDeleted(true);
        when(employeeDAO.findByUsernameOrEmail("annd")).thenReturn(deactivated);

        controller.doPost(request, response);

        verify(response).sendRedirect(contains("error=account_inactive"));
    }

    @Test
    public void login_success_invalidatesOldSessionAndStartsNewOneToPreventFixation() throws Exception {
        when(request.getServletPath()).thenReturn("/login");
        when(request.getParameter("username")).thenReturn("annd");
        when(request.getParameter("password")).thenReturn("correct-password");
        User user = userWithPassword("correct-password");
        when(employeeDAO.findByUsernameOrEmail("annd")).thenReturn(user);

        HttpSession oldSession = mock(HttpSession.class);
        HttpSession newSession = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(oldSession);
        when(request.getSession(true)).thenReturn(newSession);

        controller.doPost(request, response);

        verify(oldSession).invalidate();
        verify(newSession).setAttribute("currentUser", user);
        verify(response).sendRedirect(CONTEXT_PATH + "/dashboard");
    }

    @Test
    public void login_success_withNoPriorSession_stillCreatesNewSessionNormally() throws Exception {
        when(request.getServletPath()).thenReturn("/login");
        when(request.getParameter("username")).thenReturn("annd");
        when(request.getParameter("password")).thenReturn("correct-password");
        User user = userWithPassword("correct-password");
        when(employeeDAO.findByUsernameOrEmail("annd")).thenReturn(user);

        when(request.getSession(false)).thenReturn(null); // chưa từng có session nào
        HttpSession newSession = mock(HttpSession.class);
        when(request.getSession(true)).thenReturn(newSession);

        controller.doPost(request, response);

        verify(newSession).setAttribute("currentUser", user);
        verify(response).sendRedirect(CONTEXT_PATH + "/dashboard");
    }

    // ------------------------------------------------------------------
    // POST /changePassword
    // ------------------------------------------------------------------

    @Test
    public void changePassword_notLoggedIn_redirectsToLoginPage() throws Exception {
        when(request.getServletPath()).thenReturn("/changePassword");
        when(request.getSession(false)).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/login.jsp");
    }

    @Test
    public void changePassword_accountDeactivatedSinceLogin_invalidatesSessionAndRedirectsToLogin() throws Exception {
        when(request.getServletPath()).thenReturn("/changePassword");
        User sessionUser = userWithPassword("correct-password");
        HttpSession session = loggedInSession(sessionUser);
        User freshButDeactivated = userWithPassword("correct-password");
        freshButDeactivated.setDeleted(true);
        when(employeeDAO.findByUsernameOrEmail("annd")).thenReturn(freshButDeactivated);

        controller.doPost(request, response);

        verify(session).invalidate();
        verify(response).sendRedirect(CONTEXT_PATH + "/login.jsp");
    }

    @Test
    public void changePassword_wrongOldPassword_redirectsWithError() throws Exception {
        when(request.getServletPath()).thenReturn("/changePassword");
        loggedInSession(userWithPassword("correct-password"));
        when(employeeDAO.findByUsernameOrEmail("annd")).thenReturn(userWithPassword("correct-password"));
        when(request.getParameter("oldPassword")).thenReturn("wrong-old-password");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/changePassword.jsp?error=wrong_old_password");
        verify(employeeDAO, never()).updatePasswordByEmail(anyString(), anyString());
    }

    @Test
    public void changePassword_weakNewPassword_redirectsWithError() throws Exception {
        when(request.getServletPath()).thenReturn("/changePassword");
        loggedInSession(userWithPassword("correct-password"));
        when(employeeDAO.findByUsernameOrEmail("annd")).thenReturn(userWithPassword("correct-password"));
        when(request.getParameter("oldPassword")).thenReturn("correct-password");
        when(request.getParameter("newPassword")).thenReturn("short");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/changePassword.jsp?error=weak_password");
        verify(employeeDAO, never()).updatePasswordByEmail(anyString(), anyString());
    }

    @Test
    public void changePassword_confirmationMismatch_redirectsWithError() throws Exception {
        when(request.getServletPath()).thenReturn("/changePassword");
        loggedInSession(userWithPassword("correct-password"));
        when(employeeDAO.findByUsernameOrEmail("annd")).thenReturn(userWithPassword("correct-password"));
        when(request.getParameter("oldPassword")).thenReturn("correct-password");
        when(request.getParameter("newPassword")).thenReturn("newpassword1");
        when(request.getParameter("confirmPassword")).thenReturn("different-password");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/changePassword.jsp?error=mismatch");
    }

    @Test
    public void changePassword_sameAsOldPassword_redirectsWithError() throws Exception {
        when(request.getServletPath()).thenReturn("/changePassword");
        loggedInSession(userWithPassword("correct-password"));
        when(employeeDAO.findByUsernameOrEmail("annd")).thenReturn(userWithPassword("correct-password"));
        when(request.getParameter("oldPassword")).thenReturn("correct-password");
        when(request.getParameter("newPassword")).thenReturn("correct-password");
        when(request.getParameter("confirmPassword")).thenReturn("correct-password");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/changePassword.jsp?error=same_as_old");
        verify(employeeDAO, never()).updatePasswordByEmail(anyString(), anyString());
    }

    @Test
    public void changePassword_valid_updatesHashAndInvalidatesSessionForcingReLogin() throws Exception {
        when(request.getServletPath()).thenReturn("/changePassword");
        HttpSession session = loggedInSession(userWithPassword("correct-password"));
        User freshUser = userWithPassword("correct-password");
        freshUser.setEmail("annd@postef.com.vn");
        when(employeeDAO.findByUsernameOrEmail("annd")).thenReturn(freshUser);
        when(request.getParameter("oldPassword")).thenReturn("correct-password");
        when(request.getParameter("newPassword")).thenReturn("brand-new-password1");
        when(request.getParameter("confirmPassword")).thenReturn("brand-new-password1");
        when(employeeDAO.updatePasswordByEmail(eq("annd@postef.com.vn"), anyString())).thenReturn(true);

        controller.doPost(request, response);

        verify(employeeDAO).updatePasswordByEmail(eq("annd@postef.com.vn"), anyString());
        verify(session).invalidate();
        verify(response).sendRedirect(CONTEXT_PATH + "/login.jsp?reset=success");
    }

    // ------------------------------------------------------------------
    // POST /UpdateProfileServlet
    // ------------------------------------------------------------------

    private void stubValidUpdateProfileFields() {
        when(request.getParameter("lastName")).thenReturn("Nguyễn");
        when(request.getParameter("firstName")).thenReturn("An");
        when(request.getParameter("citizenId")).thenReturn("001201012345");
        when(request.getParameter("phone")).thenReturn("0912345678");
        when(request.getParameter("personalEmail")).thenReturn("an@example.com");
        when(request.getParameter("districtId")).thenReturn("10");
    }

    @Test
    public void updateProfile_notLoggedIn_redirectsToLoginPage() throws Exception {
        when(request.getServletPath()).thenReturn("/UpdateProfileServlet");
        when(request.getSession(false)).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/login.jsp");
    }

    @Test
    public void updateProfile_invalidPhone_redirectsWithError() throws Exception {
        when(request.getServletPath()).thenReturn("/UpdateProfileServlet");
        loggedInSession(userWithPassword("whatever"));
        stubValidUpdateProfileFields();
        when(request.getParameter("phone")).thenReturn("not-a-phone");

        controller.doPost(request, response);

        verify(employeeDAO, never()).updateProfile(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/updateProfile?error=invalid_phone");
    }

    @Test
    public void updateProfile_missingDistrict_redirectsWithMissingAddressError() throws Exception {
        when(request.getServletPath()).thenReturn("/UpdateProfileServlet");
        loggedInSession(userWithPassword("whatever"));
        stubValidUpdateProfileFields();
        when(request.getParameter("districtId")).thenReturn(null);

        controller.doPost(request, response);

        verify(employeeDAO, never()).updateProfile(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/updateProfile?error=missing_address");
    }

    @Test
    public void updateProfile_valid_updatesAndRedirectsToViewProfile() throws Exception {
        when(request.getServletPath()).thenReturn("/UpdateProfileServlet");
        loggedInSession(userWithPassword("whatever"));
        stubValidUpdateProfileFields();
        when(employeeDAO.updateProfile(any(User.class))).thenReturn(true);

        controller.doPost(request, response);

        verify(employeeDAO).updateProfile(argThat((User u) -> u.getUserId() == 7));
        verify(response).sendRedirect(CONTEXT_PATH + "/viewProfile");
    }
}
