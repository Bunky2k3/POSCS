package poscs.controller;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import poscs.common.EmailUtil;
import poscs.dao.EmployeeDAO;
import poscs.model.User;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cho PasswordResetController -- luồng "Quên mật khẩu" 3 bước (email ->
 * OTP -> mật khẩu mới), toàn bộ trạng thái tạm nằm trong session. Trọng tâm:
 * <ul>
 *   <li>Chống user enumeration ở bước 1: email tồn tại hay không đều
 *       redirect y hệt nhau sang verifyOtp.jsp, chỉ khác ở việc OTP có thực
 *       sự được sinh/gửi hay không.</li>
 *   <li>MAX_OTP_ATTEMPTS (5 lần) -- chặn dò toàn bộ 10^6 khả năng OTP trong
 *       cửa sổ hiệu lực 5 phút.</li>
 *   <li>Bước 3 (đặt mật khẩu) phải chặn truy cập thẳng khi chưa qua bước 2
 *       (otpVerified=true), và luôn dọn sạch session dù thành công hay thất
 *       bại.</li>
 * </ul>
 * Dùng session giả lưu attribute thật bằng HashMap (get/set/remove) để logic
 * session thật chạy đúng, không phải mock lại từng bước. JUnit 4 -- xem
 * CustomerControllerTest.
 */
public class PasswordResetControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private PasswordResetController controller;
    private EmployeeDAO employeeDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;

    @Before
    public void setUp() throws Exception {
        controller = new PasswordResetController();
        employeeDAO = mock(EmployeeDAO.class);
        setField(controller, "employeeDAO", employeeDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** Session giả lưu attribute thật bằng HashMap -- get/set/remove đều hoạt động như session thật. */
    private HttpSession fakeSession(Map<String, Object> backing) {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(anyString())).thenAnswer(inv -> backing.get(inv.getArgument(0, String.class)));
        doAnswer(inv -> {
            backing.put(inv.getArgument(0, String.class), inv.getArgument(1));
            return null;
        }).when(session).setAttribute(anyString(), any());
        doAnswer(inv -> {
            backing.remove(inv.getArgument(0, String.class));
            return null;
        }).when(session).removeAttribute(anyString());
        return session;
    }

    // ------------------------------------------------------------------
    // Bước 1: /ForgotPasswordServlet
    // ------------------------------------------------------------------

    @Test
    public void forgotPassword_invalidEmailFormat_redirectsWithError() throws Exception {
        when(request.getServletPath()).thenReturn("/ForgotPasswordServlet");
        when(request.getParameter("email")).thenReturn("not-an-email");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/forgotPassword.jsp?error=invalid_email");
        verify(employeeDAO, never()).findByUsernameOrEmail(anyString());
    }

    @Test
    public void forgotPassword_unknownEmail_redirectsSameAsSuccessButSendsNoOtp() throws Exception {
        when(request.getServletPath()).thenReturn("/ForgotPasswordServlet");
        when(request.getParameter("email")).thenReturn("ghost@example.com");
        when(employeeDAO.findByUsernameOrEmail("ghost@example.com")).thenReturn(null);
        Map<String, Object> attrs = new HashMap<>();
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);

        try (MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            controller.doPost(request, response);

            // Không lộ khác biệt "email tồn tại" vs "không tồn tại" -- cùng 1 redirect.
            verify(response).sendRedirect(CONTEXT_PATH + "/verifyOtp.jsp");
            emailUtil.verify(() -> EmailUtil.sendOtpEmail(anyString(), anyString()), never());
        }
    }

    @Test
    public void forgotPassword_knownEmail_generatesAndSendsOtp() throws Exception {
        when(request.getServletPath()).thenReturn("/ForgotPasswordServlet");
        when(request.getParameter("email")).thenReturn("annd@example.com");
        when(employeeDAO.findByUsernameOrEmail("annd@example.com")).thenReturn(new User());
        Map<String, Object> attrs = new HashMap<>();
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);

        try (MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            emailUtil.when(() -> EmailUtil.sendOtpEmail(anyString(), anyString())).thenReturn(true);

            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/verifyOtp.jsp");
            emailUtil.verify(() -> EmailUtil.sendOtpEmail(eq("annd@example.com"), anyString()));
            org.junit.Assert.assertNotNull("OTP phải được lưu vào session", attrs.get("resetOtp"));
        }
    }

    // ------------------------------------------------------------------
    // Bước 2: /VerifyOtpServlet
    // ------------------------------------------------------------------

    @Test
    public void verifyOtp_noOtpInSession_redirectsWithSessionExpiredError() throws Exception {
        when(request.getServletPath()).thenReturn("/VerifyOtpServlet");
        when(request.getSession(false)).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/forgotPassword.jsp?error=session_expired");
    }

    @Test
    public void verifyOtp_expiredOtp_redirectsWithExpiredError() throws Exception {
        when(request.getServletPath()).thenReturn("/VerifyOtpServlet");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetOtp", "123456");
        attrs.put("resetOtpExpiry", System.currentTimeMillis() - 1000); // đã hết hạn 1 giây trước
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/verifyOtp.jsp?error=expired");
    }

    @Test
    public void verifyOtp_tooManyFailedAttempts_burnsOtpAndRedirectsWithError() throws Exception {
        when(request.getServletPath()).thenReturn("/VerifyOtpServlet");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetOtp", "123456");
        attrs.put("resetOtpExpiry", System.currentTimeMillis() + 60000);
        attrs.put("resetOtpAttempts", 5); // đã chạm ngưỡng MAX_OTP_ATTEMPTS
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/verifyOtp.jsp?error=too_many_attempts");
        org.junit.Assert.assertNull("OTP phải bị xoá sau khi vượt quá số lần thử", attrs.get("resetOtp"));
    }

    @Test
    public void verifyOtp_wrongCode_incrementsAttemptCounter() throws Exception {
        when(request.getServletPath()).thenReturn("/VerifyOtpServlet");
        when(request.getParameter("otpCode")).thenReturn("000000");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetOtp", "123456");
        attrs.put("resetOtpExpiry", System.currentTimeMillis() + 60000);
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/verifyOtp.jsp?error=invalid_otp");
        org.junit.Assert.assertEquals(1, attrs.get("resetOtpAttempts"));
    }

    @Test
    public void verifyOtp_correctCode_marksVerifiedAndRedirectsToResetPassword() throws Exception {
        when(request.getServletPath()).thenReturn("/VerifyOtpServlet");
        when(request.getParameter("otpCode")).thenReturn("123456");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetOtp", "123456");
        attrs.put("resetOtpExpiry", System.currentTimeMillis() + 60000);
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/resetPassword.jsp");
        org.junit.Assert.assertEquals(Boolean.TRUE, attrs.get("otpVerified"));
        org.junit.Assert.assertNull("OTP phải bị xoá ngay sau khi dùng, chặn dùng lại lần 2", attrs.get("resetOtp"));
    }

    // ------------------------------------------------------------------
    // Bước 3: /ResetPasswordServlet
    // ------------------------------------------------------------------

    @Test
    public void resetPassword_otpNotVerifiedYet_redirectsWithUnauthorizedError() throws Exception {
        when(request.getServletPath()).thenReturn("/ResetPasswordServlet");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetEmail", "annd@example.com");
        // otpVerified chưa từng được set -- coi như chưa qua bước 2.
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/forgotPassword.jsp?error=unauthorized");
        verify(employeeDAO, never()).updatePasswordByEmail(anyString(), anyString());
    }

    @Test
    public void resetPassword_weakPassword_redirectsWithError() throws Exception {
        when(request.getServletPath()).thenReturn("/ResetPasswordServlet");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetEmail", "annd@example.com");
        attrs.put("otpVerified", Boolean.TRUE);
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);
        when(request.getParameter("newPassword")).thenReturn("short");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/resetPassword.jsp?error=weak_password");
    }

    @Test
    public void resetPassword_confirmationMismatch_redirectsWithError() throws Exception {
        when(request.getServletPath()).thenReturn("/ResetPasswordServlet");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetEmail", "annd@example.com");
        attrs.put("otpVerified", Boolean.TRUE);
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);
        when(request.getParameter("newPassword")).thenReturn("newpassword1");
        when(request.getParameter("confirmPassword")).thenReturn("different1");

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/resetPassword.jsp?error=mismatch");
    }

    @Test
    public void resetPassword_valid_updatesHashAndClearsSessionAndRedirectsToLogin() throws Exception {
        when(request.getServletPath()).thenReturn("/ResetPasswordServlet");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetEmail", "annd@example.com");
        attrs.put("otpVerified", Boolean.TRUE);
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);
        when(request.getParameter("newPassword")).thenReturn("brand-new-password1");
        when(request.getParameter("confirmPassword")).thenReturn("brand-new-password1");
        when(employeeDAO.updatePasswordByEmail(eq("annd@example.com"), anyString())).thenReturn(true);

        controller.doPost(request, response);

        verify(employeeDAO).updatePasswordByEmail(eq("annd@example.com"), anyString());
        org.junit.Assert.assertTrue("Session phải được dọn sạch sau khi đổi xong", attrs.isEmpty());
        verify(response).sendRedirect(CONTEXT_PATH + "/login.jsp?reset=success");
    }

    @Test
    public void resetPassword_daoUpdateFails_stillClearsSessionAndRedirectsWithError() throws Exception {
        when(request.getServletPath()).thenReturn("/ResetPasswordServlet");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetEmail", "annd@example.com");
        attrs.put("otpVerified", Boolean.TRUE);
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);
        when(request.getParameter("newPassword")).thenReturn("brand-new-password1");
        when(request.getParameter("confirmPassword")).thenReturn("brand-new-password1");
        when(employeeDAO.updatePasswordByEmail(eq("annd@example.com"), anyString())).thenReturn(false);

        controller.doPost(request, response);

        org.junit.Assert.assertTrue("Session vẫn phải được dọn dù update thất bại", attrs.isEmpty());
        verify(response).sendRedirect(CONTEXT_PATH + "/forgotPassword.jsp?error=update_failed");
    }
}
