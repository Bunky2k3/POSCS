package poscs.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.mindrot.jbcrypt.BCrypt;
import poscs.common.EmailUtil;
import poscs.dao.EmployeeDAO;
import poscs.model.User;
import poscs.validator.AuthValidator;
import poscs.validator.ValidationResult;

/**
 * Nghiệp vụ xác thực: đăng nhập (chống dò mật khẩu theo IP), đổi mật khẩu,
 * quên mật khẩu bằng OTP email. Tách nguyên vẹn từ
 * {@code AuthenticationController}; không phụ thuộc Servlet API.
 *
 * <p>Khi thất bại, {@link ServiceResult#getMessage()} là MÃ LỖI trùng với giá
 * trị {@code ?error=...} mà controller vẫn redirect trước đây (vd.
 * {@code invalid_credentials}, {@code weak_password}). Controller tự lo
 * session HTTP/redirect; trạng thái OTP đi qua {@link AttributeStore}.
 */
public class AuthService extends BaseService {

    // --- Đăng nhập ---------------------------------------------------
    // Số lần đăng nhập sai tối đa từ 1 IP trước khi tạm khoá. Đếm theo IP (không theo
    // username) để không lộ tài khoản nào tồn tại và chặn password spraying.
    static final int MAX_LOGIN_ATTEMPTS = 5;
    static final long LOGIN_LOCKOUT_MILLIS = 15 * 60 * 1000L; // 15 phút
    // Dài hơn LOGIN_LOCKOUT_MILLIS, nếu không mốc bị dọn khi khoá còn hiệu lực.
    static final long LOGIN_ATTEMPT_RETENTION_MILLIS = LOGIN_LOCKOUT_MILLIS * 2;

    private static final Map<String, LoginAttemptState> LOGIN_ATTEMPTS_BY_IP = new ConcurrentHashMap<>();

    // Username không tồn tại vẫn phải tốn đúng một lượt BCrypt như khi sai mật
    // khẩu, để không lộ username qua thời gian phản hồi.
    private static final String DUMMY_PASSWORD_HASH = BCrypt.hashpw("khong-phai-mat-khau-cua-ai", BCrypt.gensalt());

    private static final class LoginAttemptState {
        private int failedCount;
        private long lockedUntilMillis;
        private long lastFailureMillis;
    }

    // --- OTP / quên mật khẩu -----------------------------------------
    static final int OTP_LENGTH = 6;
    // 5 phút -- khớp UI verifyOtp.jsp.
    static final long OTP_VALID_MILLIS = 5 * 60 * 1000;
    // Số lần nhập sai tối đa cho 1 mã OTP.
    static final int MAX_OTP_ATTEMPTS = 5;
    // Khoảng chờ tối thiểu giữa 2 lần gửi mã (chặn ở server, không tin JS).
    static final long RESEND_COOLDOWN_MILLIS = 30 * 1000;
    // Hạn mức yêu cầu OTP theo IP (chống dội mail dù xoá cookie).
    static final int MAX_OTP_REQUESTS_PER_IP = 10;
    static final long OTP_REQUEST_WINDOW_MILLIS = 15 * 60 * 1000L;

    private static final Map<String, OtpRequestState> OTP_REQUESTS_BY_IP = new ConcurrentHashMap<>();

    private static final class OtpRequestState {
        private int count;
        private long windowStartMillis;
    }

    public static final String SESSION_RESET_USERNAME = "resetUsername";
    public static final String SESSION_RESET_OTP = "resetOtp";
    public static final String SESSION_RESET_OTP_EXPIRY = "resetOtpExpiry";
    public static final String SESSION_RESET_OTP_ATTEMPTS = "resetOtpAttempts";
    public static final String SESSION_RESET_OTP_LAST_SENT = "resetOtpLastSent";
    public static final String SESSION_OTP_VERIFIED = "otpVerified";
    public static final String SESSION_OTP_VERIFIED_EXPIRY = "otpVerifiedExpiry";
    // TRUE khi mã trong session là mã "mồi" -- xem issueOtp.
    public static final String SESSION_RESET_DECOY = "resetOtpDecoy";

    // Mã lỗi (khớp giá trị ?error= cũ của controller).
    public static final String ERR_INVALID_CREDENTIALS = "invalid_credentials";
    public static final String ERR_TOO_MANY_ATTEMPTS = "too_many_attempts";
    public static final String ERR_ACCOUNT_INACTIVE = "account_inactive";
    public static final String ERR_SESSION_INVALID = "session_invalid";
    public static final String ERR_WRONG_OLD_PASSWORD = "wrong_old_password";
    public static final String ERR_SAME_AS_OLD = "same_as_old";
    public static final String ERR_UPDATE_FAILED = "update_failed";
    public static final String ERR_SESSION_EXPIRED = "session_expired";
    public static final String ERR_RESEND_TOO_SOON = "resend_too_soon";
    public static final String ERR_EXPIRED = "expired";
    public static final String ERR_INVALID_OTP = "invalid_otp";
    public static final String ERR_UNAUTHORIZED = "unauthorized";

    private final EmployeeDAO employeeDAO;
    private final AuthValidator validator;
    private final SecureRandom random = new SecureRandom();

    // Gửi mail OTP ở luồng nền: mã "mồi" không gửi gì nên trả về tức thì; gửi
    // đồng bộ thì bấm giờ phản hồi là phân biệt được username thật/giả.
    private final ExecutorService otpMailPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "otp-mail");
        t.setDaemon(true);
        return t;
    });
    // Tách khỏi otpMailPool để test thay được bằng Runnable::run.
    private Executor otpMailExecutor = otpMailPool;

    public AuthService() {
        this(new EmployeeDAO(), new AuthValidator());
    }

    public AuthService(EmployeeDAO employeeDAO, AuthValidator validator) {
        this.employeeDAO = employeeDAO;
        this.validator = validator;
    }

    /** Dùng cho test: thay executor gửi mail (vd. {@code Runnable::run}). */
    public void setOtpMailExecutor(Executor executor) {
        this.otpMailExecutor = executor;
    }

    /** Gọi khi servlet huỷ: mail OTP đang xếp hàng vẫn gửi nốt. */
    public void shutdown() {
        otpMailPool.shutdown();
    }

    /** Xoá toàn bộ bộ đếm theo IP (dùng cho test). */
    public static void resetThrottleState() {
        LOGIN_ATTEMPTS_BY_IP.clear();
        OTP_REQUESTS_BY_IP.clear();
    }

    // ------------------------------------------------------------------
    // Đăng nhập
    // ------------------------------------------------------------------

    /**
     * Xác thực đăng nhập. Thành công trả về {@link User} đã bỏ chuỗi băm mật
     * khẩu (an toàn để cất vào session); thất bại trả mã lỗi.
     */
    public ServiceResult<User> login(String username, String password, String clientIp, long now) {
        String ip = normalizeIp(clientIp);
        // Kiểm khoá IP TRƯỚC mọi thứ khác (BR-01): IP đang bị khoá không được
        // dùng thông báo khác để thăm dò.
        if (isLockedOut(ip, now)) {
            return failure(ERR_TOO_MANY_ATTEMPTS);
        }
        String identifier = AuthValidator.trimToNull(username);
        ValidationResult validation = validator.validateLogin(identifier, password);
        if (!validation.isValid()) {
            return failure(AuthValidator.ERR_MISSING_FIELDS);
        }
        // Mật khẩu quá dài không thể đúng (BCrypt chỉ dùng 72 byte đầu) -- không
        // tốn công băm, tính như một lần sai.
        if (AuthValidator.isPasswordTooLong(password)) {
            recordFailedLoginAttempt(ip, now);
            return failure(ERR_INVALID_CREDENTIALS);
        }

        User user = employeeDAO.findByUsername(identifier);
        // Gộp "không tìm thấy user" và "sai mật khẩu" thành cùng 1 lỗi; user không
        // tồn tại vẫn chạy BCrypt với DUMMY_PASSWORD_HASH.
        boolean passwordMatches = safeCheck(password,
                user != null ? user.getPasswordHash() : DUMMY_PASSWORD_HASH);
        if (user == null || !passwordMatches) {
            recordFailedLoginAttempt(ip, now);
            return failure(ERR_INVALID_CREDENTIALS);
        }
        // Mật khẩu đúng -- xoá bộ đếm sai của IP này.
        LOGIN_ATTEMPTS_BY_IP.remove(ip);

        // Chỉ SAU KHI xác minh mật khẩu mới báo tài khoản bị khoá (chống user enumeration).
        if (user.isDeleted()) {
            return failure(ERR_ACCOUNT_INACTIVE);
        }
        // Bỏ chuỗi băm trước khi cất vào session (JSP đọc được currentUser.*).
        user.setPasswordHash(null);
        return success(user);
    }

    /**
     * BCrypt.checkpw an toàn: hash null/rỗng/sai định dạng trả false thay vì
     * ném NPE/IllegalArgumentException (sẽ thành lỗi 500).
     */
    static boolean safeCheck(String plain, String hash) {
        if (plain == null || hash == null || hash.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plain, hash);
        } catch (IllegalArgumentException | StringIndexOutOfBoundsException | NullPointerException ex) {
            return false;
        }
    }

    /** IP null/rỗng gom về 1 khoá chung, tránh NPE ở ConcurrentHashMap. */
    static String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    }

    boolean isLockedOut(String clientIp, long now) {
        LoginAttemptState state = LOGIN_ATTEMPTS_BY_IP.get(normalizeIp(clientIp));
        return state != null && state.lockedUntilMillis > now;
    }

    /** Tăng bộ đếm sai của 1 IP; đủ MAX_LOGIN_ATTEMPTS lần thì khoá tạm LOGIN_LOCKOUT_MILLIS. */
    void recordFailedLoginAttempt(String clientIp, long now) {
        // Dọn các mốc đã hết hiệu lực để map không phình mãi.
        LOGIN_ATTEMPTS_BY_IP.values().removeIf(s -> s.lastFailureMillis > 0
                && now - s.lastFailureMillis > LOGIN_ATTEMPT_RETENTION_MILLIS);

        LoginAttemptState state = LOGIN_ATTEMPTS_BY_IP.computeIfAbsent(normalizeIp(clientIp), k -> new LoginAttemptState());
        synchronized (state) {
            // Đợt khoá trước đã hết hạn -> đếm lại từ đầu.
            if (state.lockedUntilMillis > 0 && now > state.lockedUntilMillis) {
                state.failedCount = 0;
                state.lockedUntilMillis = 0;
            }
            state.failedCount++;
            state.lastFailureMillis = now;
            if (state.failedCount >= MAX_LOGIN_ATTEMPTS) {
                state.lockedUntilMillis = now + LOGIN_LOCKOUT_MILLIS;
            }
        }
    }

    // ------------------------------------------------------------------
    // Đổi mật khẩu (đã đăng nhập + biết mật khẩu hiện tại)
    // ------------------------------------------------------------------

    /**
     * Đổi mật khẩu. Lỗi {@code session_invalid} nghĩa là tài khoản không còn
     * (hoặc đã bị khoá) -- controller phải huỷ session và đá về login.
     * Thành công thì controller huỷ session và bắt đăng nhập lại.
     */
    public ServiceResult<Void> changePassword(User currentUser, String oldPassword,
            String newPassword, String confirmPassword, String clientIp, long now) {
        String ip = normalizeIp(clientIp);
        if (isLockedOut(ip, now)) {
            return failure(ERR_TOO_MANY_ATTEMPTS);
        }
        if (currentUser == null || currentUser.getUsername() == null) {
            return failure(ERR_SESSION_INVALID);
        }
        // Luôn tra user MỚI NHẤT từ DB, không dùng hash cũ trong session.
        User freshUser = employeeDAO.findByUsername(currentUser.getUsername());
        if (freshUser == null || freshUser.isDeleted()) {
            return failure(ERR_SESSION_INVALID);
        }
        if (AuthValidator.isPasswordTooLong(oldPassword) || !safeCheck(oldPassword, freshUser.getPasswordHash())) {
            recordFailedLoginAttempt(ip, now);
            return failure(ERR_WRONG_OLD_PASSWORD);
        }
        LOGIN_ATTEMPTS_BY_IP.remove(ip);
        ValidationResult validation = validator.validateNewPassword(newPassword, confirmPassword);
        if (!validation.isValid()) {
            return failure(validation.getError("newPassword"));
        }
        // "Không trùng với mật khẩu cũ".
        if (safeCheck(newPassword, freshUser.getPasswordHash())) {
            return failure(ERR_SAME_AS_OLD);
        }
        String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        if (!employeeDAO.updatePasswordByUsername(freshUser.getUsername(), newHash)) {
            return failure(ERR_UPDATE_FAILED);
        }
        return success(null);
    }

    // ------------------------------------------------------------------
    // Quên mật khẩu bước 1: yêu cầu OTP
    // ------------------------------------------------------------------

    /**
     * Sinh + gửi OTP tới personal_email ĐÃ LƯU của username. Cố tình trả
     * thành công kể cả khi username không tồn tại hoặc quá hạn mức IP (không
     * lộ username; username lạ nhận mã "mồi"). Chỉ lỗi {@code missing_username}.
     */
    public ServiceResult<Void> requestOtp(String username, String clientIp, long now, AttributeStore session) {
        String name = AuthValidator.trimToNull(username);
        ValidationResult validation = validator.validateForgotPassword(name);
        if (!validation.isValid()) {
            return failure(validation.getError("username"));
        }
        // Kiểm hạn mức TRƯỚC khi tra CSDL; quá hạn vẫn trả thành công.
        if (!allowOtpRequest(clientIp, now)) {
            return success(null);
        }
        User user = employeeDAO.findByUsername(name);
        issueOtp(session, name, user != null ? user.getPersonalEmail() : null, now);
        return success(null);
    }

    /** Gửi lại mã OTP. Lỗi: {@code session_expired}, {@code resend_too_soon}. */
    public ServiceResult<Void> resendOtp(String clientIp, long now, AttributeStore session) {
        String username = getString(session, SESSION_RESET_USERNAME);
        // Không có username nghĩa là chưa qua bước 1.
        if (username == null) {
            return failure(ERR_SESSION_EXPIRED);
        }
        Long lastSent = getLong(session, SESSION_RESET_OTP_LAST_SENT);
        if (lastSent != null && now - lastSent < RESEND_COOLDOWN_MILLIS) {
            return failure(ERR_RESEND_TOO_SOON);
        }
        // Gửi lại cũng tiêu lượt của hạn mức theo IP.
        if (!allowOtpRequest(clientIp, now)) {
            return failure(ERR_RESEND_TOO_SOON);
        }
        // Tra lại email MỚI NHẤT từ username; mã mồi thì cấp mã mồi mới.
        User user = employeeDAO.findByUsername(username);
        issueOtp(session, username, user != null ? user.getPersonalEmail() : null, now);
        return success(null);
    }

    // ------------------------------------------------------------------
    // Bước 2: Xác thực OTP
    // ------------------------------------------------------------------

    /**
     * Lỗi: {@code session_expired}, {@code expired} (mã đã bị xoá),
     * {@code too_many_attempts} (mã đã bị xoá, phải xin mã mới), {@code invalid_otp}.
     */
    public ServiceResult<Void> verifyOtp(String otpInput, long now, AttributeStore session) {
        String expectedOtp = getString(session, SESSION_RESET_OTP);
        Long expiry = getLong(session, SESSION_RESET_OTP_EXPIRY);
        if (expectedOtp == null || expiry == null) {
            return failure(ERR_SESSION_EXPIRED);
        }
        if (now > expiry) {
            // Mã hết hạn thì xoá luôn, bắt xin mã mới (BR-05).
            session.remove(SESSION_RESET_OTP);
            session.remove(SESSION_RESET_OTP_EXPIRY);
            session.remove(SESSION_RESET_OTP_ATTEMPTS);
            return failure(ERR_EXPIRED);
        }
        int attempts = getInt(session, SESSION_RESET_OTP_ATTEMPTS);
        if (attempts >= MAX_OTP_ATTEMPTS) {
            // Mã "cháy": xoá để bắt yêu cầu gửi mã mới.
            session.remove(SESSION_RESET_OTP);
            session.remove(SESSION_RESET_OTP_EXPIRY);
            session.remove(SESSION_RESET_OTP_ATTEMPTS);
            return failure(ERR_TOO_MANY_ATTEMPTS);
        }
        // Mã mồi không bao giờ đúng, kể cả khi đoán trúng.
        boolean decoy = getBool(session, SESSION_RESET_DECOY);
        String input = AuthValidator.trimToNull(otpInput);
        // So sánh hằng thời gian để không lộ dần từng ký tự qua thời gian phản hồi.
        boolean matches = input != null && MessageDigest.isEqual(
                input.getBytes(StandardCharsets.UTF_8), expectedOtp.getBytes(StandardCharsets.UTF_8));
        if (decoy || !matches) {
            session.set(SESSION_RESET_OTP_ATTEMPTS, attempts + 1);
            return failure(ERR_INVALID_OTP);
        }
        // Đúng OTP: đánh dấu đã xác thực, xoá mã để không dùng lại.
        session.set(SESSION_OTP_VERIFIED, Boolean.TRUE);
        session.set(SESSION_OTP_VERIFIED_EXPIRY, now + 10 * 60 * 1000L); // 10 phút
        session.remove(SESSION_RESET_OTP);
        session.remove(SESSION_RESET_OTP_ATTEMPTS);
        return success(null);
    }

    // ------------------------------------------------------------------
    // Bước 3: Đặt mật khẩu mới
    // ------------------------------------------------------------------

    /**
     * Lỗi: {@code unauthorized} (cũng khi tài khoản không còn/đã bị khoá --
     * trạng thái reset được dọn sạch), {@code weak_password}, {@code mismatch},
     * {@code same_as_old} (giữ trạng thái OTP để nhập lại), {@code update_failed}
     * (đã dọn sạch trạng thái reset).
     */
    public ServiceResult<Void> resetPassword(String newPassword, String confirmPassword, long now, AttributeStore session) {
        String username = getString(session, SESSION_RESET_USERNAME);
        Long expiry = getLong(session, SESSION_OTP_VERIFIED_EXPIRY);
        // Chặn truy cập thẳng bước 3 khi chưa qua bước 2.
        if (username == null || !getBool(session, SESSION_OTP_VERIFIED) || expiry == null || now > expiry) {
            clearResetState(session);
            return failure(ERR_UNAUTHORIZED);
        }
        ValidationResult validation = validator.validateNewPassword(newPassword, confirmPassword);
        if (!validation.isValid()) {
            return failure(validation.getError("newPassword"));
        }
        User account = employeeDAO.findByUsername(username);
        // Tài khoản không còn hoặc đã bị khoá sau khi qua OTP: không được đặt lại.
        if (account == null || account.isDeleted()) {
            clearResetState(session);
            return failure(ERR_UNAUTHORIZED);
        }
        if (account.getPasswordHash() != null && safeCheck(newPassword, account.getPasswordHash())) {
            return failure(ERR_SAME_AS_OLD);
        }
        String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        boolean ok = employeeDAO.updatePasswordByUsername(username, newHash);

        // Dù thành công hay thất bại đều phải dọn sạch trạng thái reset.
        clearResetState(session);

        if (!ok) {
            String safeUsername = username.replace('\n', '_').replace('\r', '_');
            log.warning("Dat lai mat khau that bai o buoc ghi CSDL (username=" + safeUsername + ")");
            return failure(ERR_UPDATE_FAILED);
        }
        return success(null);
    }

    // ------------------------------------------------------------------
    // Nội bộ OTP
    // ------------------------------------------------------------------

    /**
     * true nếu IP này còn lượt yêu cầu mã trong cửa sổ hiện tại (reset theo
     * mốc: hết OTP_REQUEST_WINDOW_MILLIS thì đếm lại).
     */
    boolean allowOtpRequest(String clientIp, long now) {
        String ip = normalizeIp(clientIp);
        OTP_REQUESTS_BY_IP.values().removeIf(s -> now - s.windowStartMillis > OTP_REQUEST_WINDOW_MILLIS);

        OtpRequestState state = OTP_REQUESTS_BY_IP.computeIfAbsent(ip, k -> {
            OtpRequestState fresh = new OtpRequestState();
            fresh.windowStartMillis = now;
            return fresh;
        });
        synchronized (state) {
            if (now - state.windowStartMillis > OTP_REQUEST_WINDOW_MILLIS) {
                state.windowStartMillis = now;
                state.count = 0;
            }
            state.count++;
            return state.count <= MAX_OTP_REQUESTS_PER_IP;
        }
    }

    /**
     * Sinh OTP mới, ghi đè toàn bộ trạng thái OTP cũ rồi gửi mail tới
     * {@code personalEmail}. Email rỗng (username lạ / chưa khai email) thì
     * sinh mã "mồi": session y hệt mã thật nhưng không gửi đi đâu và
     * {@link #verifyOtp} luôn từ chối.
     */
    private void issueOtp(AttributeStore session, String username, String personalEmail, long now) {
        String otp = generateOtp();
        session.set(SESSION_RESET_USERNAME, username);
        session.set(SESSION_RESET_OTP, otp);
        session.set(SESSION_RESET_OTP_EXPIRY, now + OTP_VALID_MILLIS);
        session.set(SESSION_RESET_OTP_LAST_SENT, now);
        session.remove(SESSION_RESET_OTP_ATTEMPTS); // reset bộ đếm sai cho mã mới
        session.remove(SESSION_OTP_VERIFIED); // reset nếu trước đó đã verify

        if (personalEmail == null || personalEmail.isBlank()) {
            session.set(SESSION_RESET_DECOY, Boolean.TRUE);
            return;
        }
        session.remove(SESSION_RESET_DECOY);
        otpMailExecutor.execute(() -> EmailUtil.sendOtpEmail(personalEmail, otp));
    }

    // Đọc session an toàn: giá trị sai kiểu coi như không có (tránh ClassCastException).
    private static String getString(AttributeStore s, String key) {
        Object v = s != null ? s.get(key) : null;
        return v instanceof String ? (String) v : null;
    }

    private static Long getLong(AttributeStore s, String key) {
        Object v = s != null ? s.get(key) : null;
        return v instanceof Long ? (Long) v : null;
    }

    private static int getInt(AttributeStore s, String key) {
        Object v = s != null ? s.get(key) : null;
        return v instanceof Integer ? (Integer) v : 0;
    }

    private static boolean getBool(AttributeStore s, String key) {
        return s != null && Boolean.TRUE.equals(s.get(key));
    }

    private static void clearResetState(AttributeStore session) {
        session.remove(SESSION_RESET_USERNAME);
        session.remove(SESSION_RESET_OTP);
        session.remove(SESSION_RESET_OTP_EXPIRY);
        session.remove(SESSION_RESET_OTP_ATTEMPTS);
        session.remove(SESSION_RESET_OTP_LAST_SENT);
        session.remove(SESSION_OTP_VERIFIED);
        session.remove(SESSION_OTP_VERIFIED_EXPIRY);
        session.remove(SESSION_RESET_DECOY);
    }

    /** Sinh mã OTP ngẫu nhiên gồm OTP_LENGTH chữ số (có thể có số 0 ở đầu). */
    private String generateOtp() {
        int max = (int) Math.pow(10, OTP_LENGTH);
        int value = random.nextInt(max);
        return String.format("%0" + OTP_LENGTH + "d", value);
    }
}
