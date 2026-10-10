package poscs.service;

import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.MockedStatic;
import poscs.common.EmailUtil;
import poscs.dao.EmployeeDAO;
import poscs.model.User;
import poscs.validator.AuthValidator;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Test AuthService + AuthValidator (Task 1.2) -- giữ nguyên hành vi của AuthenticationController cũ. */
public class AuthServiceTest {

    private static final String IP = "10.0.0.1";
    private static final long NOW = 1_000_000_000L;

    private EmployeeDAO dao;
    private AuthService service;
    private Map<String, Object> map;
    private AttributeStore session;

    @Before
    public void setUp() {
        AuthService.resetThrottleState();
        dao = mock(EmployeeDAO.class);
        service = new AuthService(dao, new AuthValidator());
        service.setOtpMailExecutor(Runnable::run);
        map = new HashMap<>();
        session = AttributeStore.of(map);
    }

    @After
    public void tearDown() {
        AuthService.resetThrottleState();
        service.shutdown();
    }

    private User user(String username, String password) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt(4)));
        u.setPersonalEmail("a@b.com");
        return u;
    }

    // ---------------- Validator ----------------

    @Test
    public void validator_login_requiresUsernameAndPassword() {
        AuthValidator v = new AuthValidator();
        assertFalse(v.validateLogin(null, "x").isValid());
        assertFalse(v.validateLogin("  ", "x").isValid());
        assertFalse(v.validateLogin("u", null).isValid());
        assertFalse(v.validateLogin("u", "").isValid());
        assertTrue(v.validateLogin("u", "p").isValid());
    }

    @Test
    public void validator_newPassword_weakThenMismatch() {
        AuthValidator v = new AuthValidator();
        assertEquals("weak_password", v.validateNewPassword("short", "short").getError("newPassword"));
        assertEquals("weak_password", v.validateNewPassword(null, null).getError("newPassword"));
        assertEquals("mismatch", v.validateNewPassword("longenough", "different").getError("newPassword"));
        assertTrue(v.validateNewPassword("longenough", "longenough").isValid());
    }

    // ---------------- Login ----------------

    @Test
    public void login_missingFields() {
        ServiceResult<User> r = service.login(" ", "pw", IP, NOW);
        assertFalse(r.isSuccess());
        assertEquals("missing_fields", r.getMessage());
    }

    @Test
    public void login_success_clearsPasswordHash() {
        when(dao.findByUsername("bob")).thenReturn(user("bob", "secret123"));
        ServiceResult<User> r = service.login(" bob ", "secret123", IP, NOW);
        assertTrue(r.isSuccess());
        assertNull(r.getData().getPasswordHash());
    }

    @Test
    public void login_unknownUserAndWrongPassword_sameError() {
        when(dao.findByUsername("bob")).thenReturn(user("bob", "secret123"));
        assertEquals("invalid_credentials", service.login("ghost", "x", IP, NOW).getMessage());
        assertEquals("invalid_credentials", service.login("bob", "wrong", IP, NOW).getMessage());
    }

    @Test
    public void login_lockedAfterFiveFailures_thenExpires() {
        for (int i = 0; i < AuthService.MAX_LOGIN_ATTEMPTS; i++) {
            service.login("ghost", "x", IP, NOW);
        }
        assertEquals("too_many_attempts", service.login("ghost", "x", IP, NOW).getMessage());
        // Hết thời gian khoá -> thử lại bình thường.
        long later = NOW + AuthService.LOGIN_LOCKOUT_MILLIS + 1;
        assertEquals("invalid_credentials", service.login("ghost", "x", IP, later).getMessage());
    }

    @Test
    public void login_successResetsFailureCounter() {
        when(dao.findByUsername("bob")).thenAnswer(inv -> user("bob", "secret123"));
        for (int i = 0; i < AuthService.MAX_LOGIN_ATTEMPTS - 1; i++) {
            service.login("bob", "wrong", IP, NOW);
        }
        assertTrue(service.login("bob", "secret123", IP, NOW).isSuccess());
        for (int i = 0; i < AuthService.MAX_LOGIN_ATTEMPTS - 1; i++) {
            service.login("bob", "wrong", IP, NOW);
        }
        assertFalse(service.isLockedOut(IP, NOW));
    }

    @Test
    public void login_deletedAccount_reportedOnlyAfterCorrectPassword() {
        User u = user("bob", "secret123");
        u.setDeleted(true);
        when(dao.findByUsername("bob")).thenReturn(u);
        assertEquals("invalid_credentials", service.login("bob", "wrong", IP, NOW).getMessage());
        assertEquals("account_inactive", service.login("bob", "secret123", IP, NOW).getMessage());
    }

    // ---------------- Đổi mật khẩu ----------------

    @Test
    public void changePassword_sessionInvalid_whenUserMissingOrDeleted() {
        User current = user("bob", "oldpass123");
        when(dao.findByUsername("bob")).thenReturn(null);
        assertEquals("session_invalid", service.changePassword(current, "oldpass123", "newpass123", "newpass123", IP, NOW).getMessage());
        User deleted = user("bob", "oldpass123");
        deleted.setDeleted(true);
        when(dao.findByUsername("bob")).thenReturn(deleted);
        assertEquals("session_invalid", service.changePassword(current, "oldpass123", "newpass123", "newpass123", IP, NOW).getMessage());
    }

    @Test
    public void changePassword_rulesInOriginalOrder() {
        User current = user("bob", "oldpass123");
        when(dao.findByUsername("bob")).thenReturn(user("bob", "oldpass123"));
        assertEquals("wrong_old_password", service.changePassword(current, "nope", "newpass123", "newpass123", IP, NOW).getMessage());
        assertEquals("wrong_old_password", service.changePassword(current, null, "newpass123", "newpass123", IP, NOW).getMessage());
        assertEquals("weak_password", service.changePassword(current, "oldpass123", "short", "short", IP, NOW).getMessage());
        assertEquals("mismatch", service.changePassword(current, "oldpass123", "newpass123", "other1234", IP, NOW).getMessage());
        assertEquals("same_as_old", service.changePassword(current, "oldpass123", "oldpass123", "oldpass123", IP, NOW).getMessage());
        verify(dao, never()).updatePasswordByUsername(anyString(), anyString());
    }

    @Test
    public void changePassword_success_andDbFailure() {
        User current = user("bob", "oldpass123");
        when(dao.findByUsername("bob")).thenReturn(user("bob", "oldpass123"));
        when(dao.updatePasswordByUsername(eq("bob"), anyString())).thenReturn(true);
        assertTrue(service.changePassword(current, "oldpass123", "newpass123", "newpass123", IP, NOW).isSuccess());

        when(dao.updatePasswordByUsername(eq("bob"), anyString())).thenReturn(false);
        assertEquals("update_failed", service.changePassword(current, "oldpass123", "newpass123", "newpass123", IP, NOW).getMessage());
    }

    // ---------------- OTP ----------------

    @Test
    public void requestOtp_missingUsername() {
        assertEquals("missing_username", service.requestOtp("  ", IP, NOW, session).getMessage());
    }

    @Test
    public void requestOtp_realUser_sendsMail() {
        when(dao.findByUsername("bob")).thenReturn(user("bob", "x"));
        try (MockedStatic<EmailUtil> mail = mockStatic(EmailUtil.class)) {
            assertTrue(service.requestOtp("bob", IP, NOW, session).isSuccess());
            mail.verify(() -> EmailUtil.sendOtpEmail(eq("a@b.com"), anyString()));
        }
        assertEquals("bob", map.get(AuthService.SESSION_RESET_USERNAME));
        assertEquals(6, ((String) map.get(AuthService.SESSION_RESET_OTP)).length());
        assertNull(map.get(AuthService.SESSION_RESET_DECOY));
    }

    @Test
    public void requestOtp_unknownUser_decoyAndNoMail() {
        when(dao.findByUsername("ghost")).thenReturn(null);
        try (MockedStatic<EmailUtil> mail = mockStatic(EmailUtil.class)) {
            assertTrue(service.requestOtp("ghost", IP, NOW, session).isSuccess());
            mail.verifyNoInteractions();
        }
        assertEquals(Boolean.TRUE, map.get(AuthService.SESSION_RESET_DECOY));
        // Mã mồi: dù nhập đúng mã trong session vẫn bị từ chối.
        String otp = (String) map.get(AuthService.SESSION_RESET_OTP);
        assertEquals("invalid_otp", service.verifyOtp(otp, NOW, session).getMessage());
    }

    @Test
    public void requestOtp_overIpQuota_stillSucceedsButIssuesNothing() {
        when(dao.findByUsername(anyString())).thenReturn(null);
        for (int i = 0; i < AuthService.MAX_OTP_REQUESTS_PER_IP; i++) {
            service.requestOtp("u" + i, IP, NOW, AttributeStore.of(new HashMap<>()));
        }
        assertTrue(service.requestOtp("again", IP, NOW, session).isSuccess());
        assertTrue(map.isEmpty());
    }

    private String issueRealOtp() {
        when(dao.findByUsername("bob")).thenReturn(user("bob", "oldpass123"));
        try (MockedStatic<EmailUtil> mail = mockStatic(EmailUtil.class)) {
            service.requestOtp("bob", IP, NOW, session);
        }
        return (String) map.get(AuthService.SESSION_RESET_OTP);
    }

    @Test
    public void resendOtp_rules() {
        assertEquals("session_expired", service.resendOtp(IP, NOW, session).getMessage());
        issueRealOtp();
        // Vừa gửi xong (lastSent = NOW) -> chặn bởi cooldown.
        assertEquals("resend_too_soon", service.resendOtp(IP, NOW, session).getMessage());
        // Qua cooldown -> gửi lại được.
        long later = NOW + AuthService.RESEND_COOLDOWN_MILLIS + 1;
        try (MockedStatic<EmailUtil> mail = mockStatic(EmailUtil.class)) {
            assertTrue(service.resendOtp(IP, later, session).isSuccess());
            mail.verify(() -> EmailUtil.sendOtpEmail(eq("a@b.com"), anyString()));
        }
        assertEquals(later, map.get(AuthService.SESSION_RESET_OTP_LAST_SENT));
    }

    @Test
    public void verifyOtp_rules() {
        assertEquals("session_expired", service.verifyOtp("123456", NOW, session).getMessage());
        String otp = issueRealOtp();

        assertEquals("invalid_otp", service.verifyOtp("000000".equals(otp) ? "111111" : "000000", NOW, session).getMessage());
        assertEquals(1, map.get(AuthService.SESSION_RESET_OTP_ATTEMPTS));
        assertEquals("invalid_otp", service.verifyOtp(null, NOW, session).getMessage());

        assertTrue(service.verifyOtp(" " + otp + " ", NOW, session).isSuccess());
        assertEquals(Boolean.TRUE, map.get(AuthService.SESSION_OTP_VERIFIED));
        assertNull(map.get(AuthService.SESSION_RESET_OTP)); // không dùng lại được
    }

    @Test
    public void verifyOtp_expired_clearsSession_soRetryIsSessionExpired() {
        String otp = issueRealOtp();
        long late = NOW + AuthService.OTP_VALID_MILLIS + 1;
        assertEquals("expired", service.verifyOtp(otp, late, session).getMessage());
        assertNull(map.get(AuthService.SESSION_RESET_OTP));
        assertNull(map.get(AuthService.SESSION_RESET_OTP_EXPIRY));
        assertEquals("session_expired", service.verifyOtp(otp, late, session).getMessage());
    }

    @Test
    public void verifyOtp_tooManyAttempts_burnsCode() {
        issueRealOtp();
        for (int i = 0; i < AuthService.MAX_OTP_ATTEMPTS; i++) {
            service.verifyOtp("abcdef", NOW, session);
        }
        assertEquals("too_many_attempts", service.verifyOtp("abcdef", NOW, session).getMessage());
        assertNull(map.get(AuthService.SESSION_RESET_OTP));
        assertEquals("session_expired", service.verifyOtp("abcdef", NOW, session).getMessage());
    }

    @Test
    public void session_wrongTypes_areTreatedAsMissing_noClassCastException() {
        map.put(AuthService.SESSION_RESET_OTP, 123456);          // Integer thay vì String
        map.put(AuthService.SESSION_RESET_OTP_EXPIRY, "soon");   // String thay vì Long
        assertEquals("session_expired", service.verifyOtp("123456", NOW, session).getMessage());

        map.clear();
        map.put(AuthService.SESSION_RESET_USERNAME, 42);
        assertEquals("session_expired", service.resendOtp(IP, NOW, session).getMessage());

        map.clear();
        map.put(AuthService.SESSION_RESET_USERNAME, "bob");
        map.put(AuthService.SESSION_OTP_VERIFIED, "true");       // không phải Boolean
        map.put(AuthService.SESSION_OTP_VERIFIED_EXPIRY, NOW + 10000L);
        assertEquals("unauthorized", service.resetPassword("newpass123", "newpass123", NOW, session).getMessage());

        map.clear();
        when(dao.findByUsername("bob")).thenReturn(user("bob", "x"));
        String otp = issueRealOtp();
        map.put(AuthService.SESSION_RESET_OTP_ATTEMPTS, "many"); // sai kiểu -> coi như 0
        map.put(AuthService.SESSION_RESET_OTP_LAST_SENT, "x");
        assertTrue(service.verifyOtp(otp, NOW, session).isSuccess());
    }

    // ---------------- Hash null/rác, IP null, mật khẩu quá dài, user null ----------------

    @Test
    public void nullOrInvalidHash_neverThrows() {
        User noHash = user("bob", "x");
        noHash.setPasswordHash(null);
        when(dao.findByUsername("bob")).thenReturn(noHash);
        assertEquals("invalid_credentials", service.login("bob", "secret123", IP, NOW).getMessage());

        User garbage = user("bob", "x");
        garbage.setPasswordHash("not-a-bcrypt-hash");
        when(dao.findByUsername("bob")).thenReturn(garbage);
        assertEquals("invalid_credentials", service.login("bob", "secret123", IP, NOW).getMessage());
        assertEquals("wrong_old_password",
                service.changePassword(garbage, "oldpass123", "newpass123", "newpass123", IP, NOW).getMessage());

        // resetPassword: hash rác -> không coi là "trùng mật khẩu cũ", vẫn đặt lại được.
        map.put(AuthService.SESSION_RESET_USERNAME, "bob");
        map.put(AuthService.SESSION_OTP_VERIFIED, Boolean.TRUE);
        map.put(AuthService.SESSION_OTP_VERIFIED_EXPIRY, NOW + 10000L);
        when(dao.updatePasswordByUsername(eq("bob"), anyString())).thenReturn(true);
        assertTrue(service.resetPassword("newpass123", "newpass123", NOW, session).isSuccess());
    }

    @Test
    public void nullOrBlankClientIp_neverThrows_andSharesOneBucket() {
        when(dao.findByUsername(anyString())).thenReturn(null);
        for (int i = 0; i < AuthService.MAX_LOGIN_ATTEMPTS; i++) {
            service.login("ghost", "x", i % 2 == 0 ? null : "  ", NOW);
        }
        assertEquals("too_many_attempts", service.login("ghost", "x", null, NOW).getMessage());
        assertTrue(service.isLockedOut(null, NOW));

        assertTrue(service.requestOtp("ghost", null, NOW, session).isSuccess());
        assertTrue(service.resendOtp(null, NOW + AuthService.RESEND_COOLDOWN_MILLIS + 1, session).isSuccess());
    }

    @Test
    public void login_lockoutCheckedBeforeMissingFields() {
        for (int i = 0; i < AuthService.MAX_LOGIN_ATTEMPTS; i++) {
            service.login("ghost", "x", IP, NOW);
        }
        assertEquals("too_many_attempts", service.login("", "", IP, NOW).getMessage());
    }

    @Test
    public void password_over72Bytes_rejected() {
        String longPw = "a".repeat(73);
        assertTrue(AuthValidator.isPasswordTooLong(longPw));
        assertFalse(AuthValidator.isPasswordTooLong("a".repeat(72)));
        // 25 ký tự 3 byte = 75 byte dù chỉ 25 ký tự.
        assertTrue(AuthValidator.isPasswordTooLong("€".repeat(25)));

        assertEquals("weak_password", new AuthValidator().validateNewPassword(longPw, longPw).getError("newPassword"));

        // Login: tính như sai, không băm.
        assertEquals("invalid_credentials", service.login("bob", longPw, IP, NOW).getMessage());
        verify(dao, never()).findByUsername(anyString());

        // Đổi mật khẩu: mật khẩu cũ / mới quá dài.
        User current = user("bob", "oldpass123");
        when(dao.findByUsername("bob")).thenReturn(user("bob", "oldpass123"));
        assertEquals("wrong_old_password", service.changePassword(current, longPw, "newpass123", "newpass123", IP, NOW).getMessage());
        assertEquals("weak_password", service.changePassword(current, "oldpass123", longPw, longPw, IP, NOW).getMessage());
    }

    @Test
    public void changePassword_nullCurrentUser_isSessionInvalid() {
        assertEquals("session_invalid", service.changePassword(null, "a", "b", "b", IP, NOW).getMessage());
        assertEquals("session_invalid", service.changePassword(new User(), "a", "b", "b", IP, NOW).getMessage());
    }

    @Test
    public void resetPassword_deletedOrMissingAccount_isUnauthorized_andClearsState() {
        for (User account : new User[] { null, deletedUser() }) {
            map.clear();
            map.put(AuthService.SESSION_RESET_USERNAME, "bob");
            map.put(AuthService.SESSION_OTP_VERIFIED, Boolean.TRUE);
            map.put(AuthService.SESSION_OTP_VERIFIED_EXPIRY, NOW + 10000L);
            when(dao.findByUsername("bob")).thenReturn(account);
            assertEquals("unauthorized", service.resetPassword("newpass123", "newpass123", NOW, session).getMessage());
            assertTrue(map.isEmpty());
        }
        verify(dao, never()).updatePasswordByUsername(anyString(), anyString());
    }

    private User deletedUser() {
        User u = user("bob", "oldpass123");
        u.setDeleted(true);
        return u;
    }

    // ---------------- Đặt lại mật khẩu ----------------

    @Test
    public void resetPassword_unauthorizedWithoutVerifiedOtp() {
        assertEquals("unauthorized", service.resetPassword("newpass123", "newpass123", NOW, session).getMessage());
        map.put(AuthService.SESSION_RESET_USERNAME, "bob");
        assertEquals("unauthorized", service.resetPassword("newpass123", "newpass123", NOW, session).getMessage());
    }

    private void verifiedSession() {
        map.put(AuthService.SESSION_RESET_USERNAME, "bob");
        map.put(AuthService.SESSION_OTP_VERIFIED, Boolean.TRUE);
        map.put(AuthService.SESSION_OTP_VERIFIED_EXPIRY, NOW + 10000L);
    }

    @Test
    public void resetPassword_validationErrors_keepOtpState() {
        verifiedSession();
        when(dao.findByUsername("bob")).thenReturn(user("bob", "oldpass123"));
        assertEquals("weak_password", service.resetPassword("short", "short", NOW, session).getMessage());
        assertEquals("mismatch", service.resetPassword("newpass123", "x", NOW, session).getMessage());
        assertEquals("same_as_old", service.resetPassword("oldpass123", "oldpass123", NOW, session).getMessage());
        assertEquals(Boolean.TRUE, map.get(AuthService.SESSION_OTP_VERIFIED));
    }

    @Test
    public void resetPassword_success_clearsState() {
        verifiedSession();
        when(dao.findByUsername("bob")).thenReturn(user("bob", "oldpass123"));
        when(dao.updatePasswordByUsername(eq("bob"), anyString())).thenReturn(true);
        assertTrue(service.resetPassword("newpass123", "newpass123", NOW, session).isSuccess());
        assertTrue(map.isEmpty());
    }

    @Test
    public void resetPassword_dbFailure_stillClearsState() {
        verifiedSession();
        when(dao.findByUsername("bob")).thenReturn(user("bob", "oldpass123"));
        when(dao.updatePasswordByUsername(eq("bob"), anyString())).thenReturn(false);
        assertEquals("update_failed", service.resetPassword("newpass123", "newpass123", NOW, session).getMessage());
        assertTrue(map.isEmpty());
    }
}
