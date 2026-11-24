package poscs.controller;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.model.User;
import poscs.service.AuthService;
import poscs.service.ServiceResult;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test PasswordResetController (UC-03, UC-04, UC-05).
 *
 * <p>Trọng tâm: /VerifyOtpServlet đọc mã ở tham số {@code otpCode}, đúng tên ô
 * ẩn mà verifyOtp.jsp ghép sáu ô số vào trước khi gửi. Bản tách controller
 * từng đọc {@code otp} nên gõ đúng mã vẫn bị báo "Mã OTP không đúng"
 * (Task 12.1). Các ca OTP dùng AuthService thật: bước xác thực OTP chỉ đọc,
 * ghi phiên, không chạm CSDL.
 */
public class PasswordResetControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";
    private static final String OTP = "482915";

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final HttpSession session = mock(HttpSession.class);
    private final Map<String, Object> sessionData = new HashMap<>();
    private final PasswordResetController controller = new PasswordResetController();

    @Before
    public void setUp() {
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(anyString())).thenAnswer(inv -> sessionData.get(inv.getArgument(0)));
        doAnswer(inv -> {
            sessionData.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(session).setAttribute(anyString(), any());
        doAnswer(inv -> {
            sessionData.remove(inv.getArgument(0));
            return null;
        }).when(session).removeAttribute(anyString());
    }

    /** Phiên như sau bước 1: đã phát mã OTP, hết hạn ở mốc {@code expiry}. */
    private void issueOtp(long expiry) {
        sessionData.put(AuthService.SESSION_RESET_USERNAME, "leminhhoang");
        sessionData.put(AuthService.SESSION_RESET_OTP, OTP);
        sessionData.put(AuthService.SESSION_RESET_OTP_EXPIRY, expiry);
    }

    private void issueOtp() {
        issueOtp(System.currentTimeMillis() + 5 * 60 * 1000L);
    }

    private void postTo(String servletPath) throws Exception {
        when(request.getServletPath()).thenReturn(servletPath);
        controller.doPost(request, response);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void testInstantiate() {
        PasswordResetController controller = new PasswordResetController();
        assertNotNull(controller);
    }

    // ------------------------------------------------------------------
    // /VerifyOtpServlet
    // ------------------------------------------------------------------

    @Test
    public void verifyOtp_dungMaTrongOtpCode_sangTrangDatLaiMatKhau() throws Exception {
        issueOtp();
        when(request.getParameter("otpCode")).thenReturn(OTP);

        postTo("/VerifyOtpServlet");

        verify(response).sendRedirect(CONTEXT_PATH + "/resetPassword.jsp");
        assertEquals(Boolean.TRUE, sessionData.get(AuthService.SESSION_OTP_VERIFIED));
        assertNull("Mã đã dùng phải bị xoá khỏi phiên", sessionData.get(AuthService.SESSION_RESET_OTP));
    }

    @Test
    public void verifyOtp_saiMa_baoInvalidOtpVaTinhMotLuot() throws Exception {
        issueOtp();
        when(request.getParameter("otpCode")).thenReturn("000000");

        postTo("/VerifyOtpServlet");

        verify(response).sendRedirect(CONTEXT_PATH + "/verifyOtp.jsp?error=" + AuthService.ERR_INVALID_OTP);
        assertNull(sessionData.get(AuthService.SESSION_OTP_VERIFIED));
        assertEquals(1, sessionData.get(AuthService.SESSION_RESET_OTP_ATTEMPTS));
    }

    @Test
    public void verifyOtp_thieuOtpCode_baoLoiKhongCoiLaDung() throws Exception {
        issueOtp();
        // Tham số cũ "otp" mang đúng mã nhưng không còn được đọc.
        when(request.getParameter("otp")).thenReturn(OTP);

        postTo("/VerifyOtpServlet");

        verify(response).sendRedirect(CONTEXT_PATH + "/verifyOtp.jsp?error=" + AuthService.ERR_INVALID_OTP);
        verify(response, never()).sendRedirect(CONTEXT_PATH + "/resetPassword.jsp");
        assertNull(sessionData.get(AuthService.SESSION_OTP_VERIFIED));
    }

    @Test
    public void verifyOtp_maHetHan_veTrangQuenMatKhauVoiLoiExpired() throws Exception {
        // BR-05: mã quá 5 phút -> forgotPassword.jsp hiện MSG-007.
        issueOtp(System.currentTimeMillis() - 1000L);
        when(request.getParameter("otpCode")).thenReturn(OTP);

        postTo("/VerifyOtpServlet");

        verify(response).sendRedirect(CONTEXT_PATH + "/forgotPassword.jsp?error=" + AuthService.ERR_EXPIRED);
        assertNull(sessionData.get(AuthService.SESSION_OTP_VERIFIED));
    }

    // ------------------------------------------------------------------
    // /changePassword
    // ------------------------------------------------------------------

    @Test
    public void changePassword_mayBiChan_chuyenMaTooManyAttemptsChoTrangDoiMatKhau() throws Exception {
        // BR-01: gõ sai mật khẩu hiện tại cũng tính vào lượt chặn máy; changePassword.jsp hiện MSG-008.
        AuthService authService = mock(AuthService.class);
        setField(controller, "authService", authService);
        User user = new User();
        user.setUsername("leminhhoang");
        sessionData.put("currentUser", user);
        when(authService.changePassword(eq(user), any(), any(), any(), any(), anyLong()))
                .thenReturn(ServiceResult.fail(AuthService.ERR_TOO_MANY_ATTEMPTS));

        postTo("/changePassword");

        verify(response).sendRedirect(CONTEXT_PATH + "/changePassword.jsp?error=" + AuthService.ERR_TOO_MANY_ATTEMPTS);
        verify(session, never()).invalidate();
    }
}
