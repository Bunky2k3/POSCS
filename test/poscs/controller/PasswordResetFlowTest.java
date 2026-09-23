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
 * Test cho luồng "Quên mật khẩu" của AuthenticationController -- 3 bước
 * (username -> OTP -> mật khẩu mới), toàn bộ trạng thái tạm nằm trong
 * session. Từ V36: bước 1 nhận USERNAME (không còn "email công ty" giả để
 * nhập), và OTP luôn gửi tới {@code personal_email} ĐÃ LƯU trong hồ sơ --
 * KHÔNG còn gửi tới đúng chuỗi người dùng tự gõ như bản cũ. Trọng tâm:
 * <ul>
 *   <li>Chống user enumeration ở bước 1: username tồn tại hay không đều
 *       redirect y hệt nhau sang verifyOtp.jsp, chỉ khác ở việc OTP có thực
 *       sự được sinh/gửi hay không.</li>
 *   <li>MAX_OTP_ATTEMPTS (5 lần) -- chặn dò toàn bộ 10^6 khả năng OTP trong
 *       cửa sổ hiệu lực 5 phút.</li>
 *   <li>Bước 3 (đặt mật khẩu) phải chặn truy cập thẳng khi chưa qua bước 2
 *       (otpVerified=true), và luôn dọn sạch session dù thành công hay thất
 *       bại.</li>
 *   <li>Gửi lại mã (/ResendOtpServlet) phải tôn trọng khoảng chờ 30 giây ở
 *       phía server -- đồng hồ đếm ngược trong verifyOtp.jsp chỉ là JS, không
 *       cản được ai POST thẳng vào URL để dội mail.</li>
 * </ul>
 * Dùng session giả lưu attribute thật bằng HashMap (get/set/remove) để logic
 * session thật chạy đúng, không phải mock lại từng bước. JUnit 4 -- xem
 * CustomerControllerTest.
 */
public class PasswordResetFlowTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private AuthenticationController controller;
    private EmployeeDAO employeeDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;

    @Before
    public void setUp() throws Exception {
        controller = new AuthenticationController();
        employeeDAO = mock(EmployeeDAO.class);
        setField(controller, "employeeDAO", employeeDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        // Hạn mức yêu cầu OTP đếm theo IP trong một map static, sống xuyên suốt
        // cả lớp test -- cho mỗi test một IP riêng để chúng không tiêu lượt của
        // nhau và kết quả không phụ thuộc thứ tự chạy.
        when(request.getRemoteAddr()).thenReturn(java.util.UUID.randomUUID().toString());
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

    /** Tài khoản tìm được ở bước 1/gửi lại -- luôn cần personal_email để issueOtp có nơi gửi tới. */
    private User userWithPersonalEmail(String personalEmail) {
        User user = new User();
        user.setPersonalEmail(personalEmail);
        return user;
    }

    // ------------------------------------------------------------------
    // Bước 1: /ForgotPasswordServlet
    // ------------------------------------------------------------------

    @Test
    public void forgotPassword_missingUsername_redirectsWithError() throws Exception {
        when(request.getServletPath()).thenReturn("/ForgotPasswordServlet");
        when(request.getParameter("username")).thenReturn("   "); // trim -> null

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/forgotPassword.jsp?error=missing_username");
        verify(employeeDAO, never()).findByUsername(anyString());
    }

    @Test
    public void forgotPassword_unknownUsername_redirectsSameAsSuccessButSendsNoOtp() throws Exception {
        when(request.getServletPath()).thenReturn("/ForgotPasswordServlet");
        when(request.getParameter("username")).thenReturn("ghost");
        when(employeeDAO.findByUsername("ghost")).thenReturn(null);
        Map<String, Object> attrs = new HashMap<>();
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);

        try (MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            controller.doPost(request, response);

            // Không lộ khác biệt "username tồn tại" vs "không tồn tại" -- cùng 1 redirect.
            verify(response).sendRedirect(CONTEXT_PATH + "/verifyOtp.jsp");
            emailUtil.verify(() -> EmailUtil.sendOtpEmail(anyString(), anyString()), never());
        }
    }

    @Test
    public void forgotPassword_accountWithoutPersonalEmail_sendsNoOtpEither() throws Exception {
        // BR-11 bắt buộc personal_email lúc tạo, nhưng dữ liệu cũ/hỏng vẫn có
        // thể thiếu -- không có địa chỉ nào để gửi thì cũng phải im lặng như
        // "không tồn tại", không báo lỗi riêng (vẫn là nguyên tắc chống dò).
        when(request.getServletPath()).thenReturn("/ForgotPasswordServlet");
        when(request.getParameter("username")).thenReturn("annd");
        when(employeeDAO.findByUsername("annd")).thenReturn(userWithPersonalEmail(null));
        Map<String, Object> attrs = new HashMap<>();
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);

        try (MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/verifyOtp.jsp");
            emailUtil.verify(() -> EmailUtil.sendOtpEmail(anyString(), anyString()), never());
        }
    }

    @Test
    public void forgotPassword_knownUsername_generatesAndSendsOtpToPersonalEmail() throws Exception {
        when(request.getServletPath()).thenReturn("/ForgotPasswordServlet");
        when(request.getParameter("username")).thenReturn("annd");
        when(employeeDAO.findByUsername("annd")).thenReturn(userWithPersonalEmail("an.nguyen@gmail.com"));
        Map<String, Object> attrs = new HashMap<>();
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);

        try (MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            emailUtil.when(() -> EmailUtil.sendOtpEmail(anyString(), anyString())).thenReturn(true);

            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/verifyOtp.jsp");
            // Gửi tới EMAIL CÁ NHÂN đã lưu, không phải chuỗi "annd" người dùng gõ.
            emailUtil.verify(() -> EmailUtil.sendOtpEmail(eq("an.nguyen@gmail.com"), anyString()));
            org.junit.Assert.assertNotNull("OTP phải được lưu vào session", attrs.get("resetOtp"));
            org.junit.Assert.assertEquals("annd", attrs.get("resetUsername"));
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

        // Về bước 1, KHÔNG về verifyOtp.jsp: mã vừa bị xoá nên trang đó sẽ đá
        // tiếp về bước 1 với câu chung chung, nuốt mất thông báo này.
        verify(response).sendRedirect(CONTEXT_PATH + "/forgotPassword.jsp?error=too_many_attempts");
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

    @Test
    public void forgotPassword_beyondPerIpQuota_stopsSendingMail() throws Exception {
        when(request.getServletPath()).thenReturn("/ForgotPasswordServlet");
        when(request.getParameter("username")).thenReturn("annd");
        when(employeeDAO.findByUsername("annd")).thenReturn(userWithPersonalEmail("an.nguyen@gmail.com"));
        Map<String, Object> attrs = new HashMap<>();
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);

        try (MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            emailUtil.when(() -> EmailUtil.sendOtpEmail(anyString(), anyString())).thenReturn(true);

            // Cooldown 30 giây chỉ gắn với session, xoá cookie là lách được --
            // nên hạn mức theo IP mới là thứ chặn được kiểu dội mail liên tục.
            for (int i = 0; i < 15; i++) {
                controller.doPost(request, response);
            }

            emailUtil.verify(() -> EmailUtil.sendOtpEmail(anyString(), anyString()), times(10));
        }
    }

    @Test
    public void forgotPassword_quotaExhausted_stillRedirectsIdenticallyToHideTheLimit() throws Exception {
        when(request.getServletPath()).thenReturn("/ForgotPasswordServlet");
        when(request.getParameter("username")).thenReturn("annd");
        when(employeeDAO.findByUsername("annd")).thenReturn(userWithPersonalEmail("an.nguyen@gmail.com"));
        Map<String, Object> attrs = new HashMap<>();
        HttpSession session = fakeSession(attrs);
        when(request.getSession(true)).thenReturn(session);

        try (MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            emailUtil.when(() -> EmailUtil.sendOtpEmail(anyString(), anyString())).thenReturn(true);

            for (int i = 0; i < 12; i++) {
                controller.doPost(request, response);
            }

            // Nếu lần bị chặn trả về trang/tham số khác, kẻ tấn công dò được
            // đúng ngưỡng và cả việc username nào có tài khoản -- phá luôn
            // nguyên tắc chống enumeration mà bước 1 đang giữ.
            verify(response, times(12)).sendRedirect(CONTEXT_PATH + "/verifyOtp.jsp");
        }
    }

    // ------------------------------------------------------------------
    // Bước 2 (phụ): /ResendOtpServlet
    // ------------------------------------------------------------------

    @Test
    public void resendOtp_noSession_redirectsWithSessionExpiredError() throws Exception {
        when(request.getServletPath()).thenReturn("/ResendOtpServlet");
        when(request.getSession(false)).thenReturn(null);

        try (MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/forgotPassword.jsp?error=session_expired");
            emailUtil.verify(() -> EmailUtil.sendOtpEmail(anyString(), anyString()), never());
        }
    }

    @Test
    public void resendOtp_sessionWithoutUsername_redirectsWithSessionExpiredError() throws Exception {
        when(request.getServletPath()).thenReturn("/ResendOtpServlet");
        // Session tồn tại nhưng chưa qua bước 1 -- không biết gửi lại cho ai.
        HttpSession session = fakeSession(new HashMap<>());
        when(request.getSession(false)).thenReturn(session);

        try (MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/forgotPassword.jsp?error=session_expired");
            emailUtil.verify(() -> EmailUtil.sendOtpEmail(anyString(), anyString()), never());
        }
    }

    @Test
    public void resendOtp_withinCooldown_sendsNothingAndKeepsExistingOtp() throws Exception {
        when(request.getServletPath()).thenReturn("/ResendOtpServlet");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetUsername", "annd");
        attrs.put("resetOtp", "123456");
        attrs.put("resetOtpLastSent", System.currentTimeMillis() - 5000); // mới gửi 5 giây trước
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);

        try (MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/verifyOtp.jsp?error=resend_too_soon");
            // Không có mail nào được gửi -- đây chính là điều ngăn endpoint này
            // bị gọi liên tục để dội mail vào hòm thư nạn nhân.
            emailUtil.verify(() -> EmailUtil.sendOtpEmail(anyString(), anyString()), never());
            org.junit.Assert.assertEquals("Mã đang có hiệu lực không được đụng tới", "123456", attrs.get("resetOtp"));
        }
    }

    @Test
    public void resendOtp_afterCooldown_sendsNewOtpToPersonalEmailOnFile() throws Exception {
        when(request.getServletPath()).thenReturn("/ResendOtpServlet");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetUsername", "annd");
        attrs.put("resetOtpLastSent", System.currentTimeMillis() - 31_000); // đã quá 30 giây
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);
        when(employeeDAO.findByUsername("annd")).thenReturn(userWithPersonalEmail("an.nguyen@gmail.com"));

        try (MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            emailUtil.when(() -> EmailUtil.sendOtpEmail(anyString(), anyString())).thenReturn(true);

            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/verifyOtp.jsp?resent=1");
            // Tra lại personal_email MỚI NHẤT từ username lưu trong session,
            // không phải gửi tới đúng chuỗi cũ -- người dùng không phải gõ lại.
            emailUtil.verify(() -> EmailUtil.sendOtpEmail(eq("an.nguyen@gmail.com"), anyString()));
            org.junit.Assert.assertNotNull("Mã mới phải được lưu vào session", attrs.get("resetOtp"));
        }
    }

    @Test
    public void resendOtp_neverSentBefore_isAllowed() throws Exception {
        when(request.getServletPath()).thenReturn("/ResendOtpServlet");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetUsername", "annd");
        // resetOtpLastSent chưa từng được set -- không có gì để so, phải cho qua.
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);
        when(employeeDAO.findByUsername("annd")).thenReturn(userWithPersonalEmail("an.nguyen@gmail.com"));

        try (MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            emailUtil.when(() -> EmailUtil.sendOtpEmail(anyString(), anyString())).thenReturn(true);

            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/verifyOtp.jsp?resent=1");
            emailUtil.verify(() -> EmailUtil.sendOtpEmail(eq("an.nguyen@gmail.com"), anyString()));
        }
    }

    @Test
    public void resendOtp_replacesOldCodeAndResetsAttemptCounter() throws Exception {
        when(request.getServletPath()).thenReturn("/ResendOtpServlet");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetUsername", "annd");
        attrs.put("resetOtp", "111111");
        attrs.put("resetOtpAttempts", 3);
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);
        when(employeeDAO.findByUsername("annd")).thenReturn(userWithPersonalEmail("an.nguyen@gmail.com"));

        try (MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            emailUtil.when(() -> EmailUtil.sendOtpEmail(anyString(), anyString())).thenReturn(true);

            controller.doPost(request, response);

            // Mã cũ chết ngay: tại một thời điểm chỉ đúng 1 mã dùng được, nên bấm
            // "gửi lại" nhiều lần không để lại một loạt mã còn sống rải rác.
            org.junit.Assert.assertNotEquals("Mã cũ phải bị thay", "111111", attrs.get("resetOtp"));
            org.junit.Assert.assertNull("Bộ đếm nhập sai phải reset cho mã mới", attrs.get("resetOtpAttempts"));
        }
    }

    // ------------------------------------------------------------------
    // Bước 3: /ResetPasswordServlet
    // ------------------------------------------------------------------

    @Test
    public void resetPassword_otpNotVerifiedYet_redirectsWithUnauthorizedError() throws Exception {
        when(request.getServletPath()).thenReturn("/ResetPasswordServlet");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetUsername", "annd");
        // otpVerified chưa từng được set -- coi như chưa qua bước 2.
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/forgotPassword.jsp?error=unauthorized");
        verify(employeeDAO, never()).updatePasswordByUsername(anyString(), anyString());
    }

    @Test
    public void resetPassword_weakPassword_redirectsWithError() throws Exception {
        when(request.getServletPath()).thenReturn("/ResetPasswordServlet");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetUsername", "annd");
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
        attrs.put("resetUsername", "annd");
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
        attrs.put("resetUsername", "annd");
        attrs.put("otpVerified", Boolean.TRUE);
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);
        when(request.getParameter("newPassword")).thenReturn("brand-new-password1");
        when(request.getParameter("confirmPassword")).thenReturn("brand-new-password1");
        when(employeeDAO.updatePasswordByUsername(eq("annd"), anyString())).thenReturn(true);

        controller.doPost(request, response);

        verify(employeeDAO).updatePasswordByUsername(eq("annd"), anyString());
        org.junit.Assert.assertTrue("Session phải được dọn sạch sau khi đổi xong", attrs.isEmpty());
        verify(response).sendRedirect(CONTEXT_PATH + "/login.jsp?reset=success");
    }

    @Test
    public void resetPassword_daoUpdateFails_stillClearsSessionAndRedirectsWithError() throws Exception {
        when(request.getServletPath()).thenReturn("/ResetPasswordServlet");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("resetUsername", "annd");
        attrs.put("otpVerified", Boolean.TRUE);
        HttpSession session = fakeSession(attrs);
        when(request.getSession(false)).thenReturn(session);
        when(request.getParameter("newPassword")).thenReturn("brand-new-password1");
        when(request.getParameter("confirmPassword")).thenReturn("brand-new-password1");
        when(employeeDAO.updatePasswordByUsername(eq("annd"), anyString())).thenReturn(false);

        controller.doPost(request, response);

        org.junit.Assert.assertTrue("Session vẫn phải được dọn dù update thất bại", attrs.isEmpty());
        verify(response).sendRedirect(CONTEXT_PATH + "/forgotPassword.jsp?error=update_failed");
    }
}
