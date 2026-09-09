package poscs.controller;

import java.io.IOException;
import java.security.SecureRandom;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;
import poscs.common.EmailUtil;
import poscs.dao.EmployeeDAO;
import poscs.model.User;

/**
 * Luồng "Quên mật khẩu" gồm 3 bước, mỗi bước là 1 URL riêng (khớp đúng với
 * action="..." đã có sẵn trong forgotPassword.jsp / verifyOtp.jsp /
 * resetPassword.jsp -- mở 3 file đó để xem giao diện tương ứng từng bước):
 *
 *   1. POST /ForgotPasswordServlet (email)
 *      -> sinh mã OTP 6 số, lưu vào session, gửi qua email, sang bước 2.
 *   2. POST /VerifyOtpServlet (otpCode)
 *      -> so khớp OTP + hạn dùng trong session, đánh dấu otpVerified, sang bước 3.
 *   3. POST /ResetPasswordServlet (newPassword, confirmPassword)
 *      -> chỉ cho phép nếu otpVerified=true, hash mật khẩu mới bằng BCrypt rồi lưu DB.
 *
 * Ngoài 3 bước trên còn POST /ResendOtpServlet (nút "Gửi lại mã OTP" ở bước 2)
 * -> sinh mã mới cho đúng email đang lưu trong session, không cần nhập lại email.
 *
 * Toàn bộ trạng thái tạm (email đang reset, OTP, hạn dùng, đã xác thực OTP
 * hay chưa) được lưu trong HttpSession, KHÔNG lưu xuống DB -- vì đây chỉ là
 * dữ liệu sống trong đúng 1 lần thao tác, không cần tồn tại lâu dài, và lưu
 * trong session giúp tự động "hết hạn" khi session hết hạn/bị huỷ.
 */
@WebServlet(name = "PasswordResetController", urlPatterns = {
    "/ForgotPasswordServlet", "/VerifyOtpServlet", "/ResetPasswordServlet", "/ResendOtpServlet"
})
public class PasswordResetController extends HttpServlet {

    private static final int OTP_LENGTH = 6;
    // 5 phút -- khớp với UI (verifyOtp.jsp đếm ngược 05:00 bằng JS phía client).
    private static final long OTP_VALID_MILLIS = 5 * 60 * 1000;
    // Số lần nhập sai tối đa trước khi bắt yêu cầu gửi mã OTP mới -- không có
    // giới hạn này thì 1 kẻ tấn công (đã tự kích hoạt luồng quên mật khẩu cho
    // email nạn nhân) có thể dò toàn bộ 10^OTP_LENGTH khả năng trong đúng 1
    // cửa sổ hiệu lực OTP_VALID_MILLIS bằng cách gửi liên tục không giới hạn.
    private static final int MAX_OTP_ATTEMPTS = 5;
    // Khoảng chờ tối thiểu giữa 2 lần gửi mã -- khớp với đồng hồ đếm ngược 30s ở
    // verifyOtp.jsp, nhưng phải chặn lại ở server: bộ đếm phía client chỉ là JS,
    // ai cũng có thể POST thẳng vào /ResendOtpServlet liên tục để biến hệ thống
    // thành công cụ dội mail vào hòm thư nạn nhân.
    private static final long RESEND_COOLDOWN_MILLIS = 30 * 1000;

    private static final String SESSION_RESET_EMAIL = "resetEmail";
    private static final String SESSION_RESET_OTP = "resetOtp";
    private static final String SESSION_RESET_OTP_EXPIRY = "resetOtpExpiry";
    private static final String SESSION_RESET_OTP_ATTEMPTS = "resetOtpAttempts";
    private static final String SESSION_RESET_OTP_LAST_SENT = "resetOtpLastSent";
    private static final String SESSION_OTP_VERIFIED = "otpVerified";

    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final SecureRandom random = new SecureRandom();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // 3 form khác nhau POST tới 3 URL khác nhau nhưng đều do 1 servlet này
        // xử lý -- dùng getServletPath() để biết request đang ở bước nào
        // (giá trị trả về khớp với 1 trong 3 url-pattern khai báo ở @WebServlet).
        String path = request.getServletPath();
        switch (path) {
            case "/ForgotPasswordServlet":
                handleForgotPassword(request, response);
                break;
            case "/VerifyOtpServlet":
                handleVerifyOtp(request, response);
                break;
            case "/ResetPasswordServlet":
                handleResetPassword(request, response);
                break;
            case "/ResendOtpServlet":
                handleResendOtp(request, response);
                break;
            default:
                response.sendRedirect(request.getContextPath() + "/login.jsp");
        }
    }

    // ------------------------------------------------------------------
    // Bước 1: Quên mật khẩu -- nhập email, sinh + gửi OTP
    // ------------------------------------------------------------------

    private void handleForgotPassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = trimToNull(request.getParameter("email"));
        // Validate ĐỊNH DẠNG thôi (không phải "email này có tồn tại không") --
        // an toàn để báo lỗi riêng cho trường hợp sai định dạng, vì nó không
        // tiết lộ gì về việc email đó có tài khoản hay không (khác với việc
        // báo "email không tồn tại", điều mà code bên dưới cố tình tránh).
        if (email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            response.sendRedirect(request.getContextPath() + "/forgotPassword.jsp?error=invalid_email");
            return;
        }

        // Cố tình KHÔNG báo lỗi riêng khi email không tồn tại trong hệ thống:
        // nếu 2 trường hợp "email có tồn tại" và "email không tồn tại" trả về
        // 2 kết quả khác nhau, kẻ tấn công có thể dò ra danh sách email hợp lệ
        // trong hệ thống (user enumeration). Nên luôn điều hướng sang
        // verifyOtp.jsp giống nhau; nếu email không tồn tại thì đơn giản là
        // không có OTP nào được sinh/lưu/gửi, nên bước xác thực OTP ở sau chắc
        // chắn sẽ thất bại (không có gì để so khớp).
        User user = employeeDAO.findByUsernameOrEmail(email);
        if (user != null) {
            issueOtp(request.getSession(true), email);
        }

        response.sendRedirect(request.getContextPath() + "/verifyOtp.jsp");
    }

    // ------------------------------------------------------------------
    // Bước 2 (phụ): Gửi lại mã OTP
    // ------------------------------------------------------------------

    private void handleResendOtp(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        String email = session != null ? (String) session.getAttribute(SESSION_RESET_EMAIL) : null;

        // Không có email trong session nghĩa là chưa qua bước 1 (mất session,
        // hoặc POST thẳng vào URL này) -- không có địa chỉ nào để gửi tới.
        if (email == null) {
            response.sendRedirect(request.getContextPath() + "/forgotPassword.jsp?error=session_expired");
            return;
        }

        Long lastSent = (Long) session.getAttribute(SESSION_RESET_OTP_LAST_SENT);
        if (lastSent != null && System.currentTimeMillis() - lastSent < RESEND_COOLDOWN_MILLIS) {
            response.sendRedirect(request.getContextPath() + "/verifyOtp.jsp?error=resend_too_soon");
            return;
        }

        issueOtp(session, email);
        response.sendRedirect(request.getContextPath() + "/verifyOtp.jsp?resent=1");
    }

    // ------------------------------------------------------------------
    // Bước 2: Xác thực OTP
    // ------------------------------------------------------------------

    private void handleVerifyOtp(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        String expectedOtp = session != null ? (String) session.getAttribute(SESSION_RESET_OTP) : null;
        Long expiry = session != null ? (Long) session.getAttribute(SESSION_RESET_OTP_EXPIRY) : null;

        // Session chưa từng có OTP nào (vd. mất session, hoặc gõ thẳng URL
        // verifyOtp.jsp mà chưa qua bước 1) -- không có gì để so khớp.
        if (expectedOtp == null || expiry == null) {
            response.sendRedirect(request.getContextPath() + "/forgotPassword.jsp?error=session_expired");
            return;
        }

        if (System.currentTimeMillis() > expiry) {
            response.sendRedirect(request.getContextPath() + "/verifyOtp.jsp?error=expired");
            return;
        }

        // Giới hạn số lần nhập sai (MAX_OTP_ATTEMPTS) trong đúng 1 mã OTP -- nếu
        // không, mã 6 số vẫn còn hiệu lực trong OTP_VALID_MILLIS có thể bị dò
        // toàn bộ bằng cách gửi liên tục không giới hạn. Vượt quá số lần cho
        // phép thì coi như mã này "cháy", xoá luôn để bắt yêu cầu gửi mã mới.
        Integer attempts = (Integer) session.getAttribute(SESSION_RESET_OTP_ATTEMPTS);
        if (attempts == null) {
            attempts = 0;
        }
        if (attempts >= MAX_OTP_ATTEMPTS) {
            session.removeAttribute(SESSION_RESET_OTP);
            session.removeAttribute(SESSION_RESET_OTP_EXPIRY);
            session.removeAttribute(SESSION_RESET_OTP_ATTEMPTS);
            response.sendRedirect(request.getContextPath() + "/verifyOtp.jsp?error=too_many_attempts");
            return;
        }

        String inputOtp = trimToNull(request.getParameter("otpCode"));
        if (inputOtp == null || !inputOtp.equals(expectedOtp)) {
            session.setAttribute(SESSION_RESET_OTP_ATTEMPTS, attempts + 1);
            response.sendRedirect(request.getContextPath() + "/verifyOtp.jsp?error=invalid_otp");
            return;
        }

        // Đúng OTP: đánh dấu đã xác thực để bước 3 (đặt mật khẩu mới) được phép
        // chạy. Xoá resetOtp ngay để chặn dùng lại đúng mã đó lần 2.
        session.setAttribute(SESSION_OTP_VERIFIED, Boolean.TRUE);
        session.removeAttribute(SESSION_RESET_OTP);
        session.removeAttribute(SESSION_RESET_OTP_ATTEMPTS);
        response.sendRedirect(request.getContextPath() + "/resetPassword.jsp");
    }

    // ------------------------------------------------------------------
    // Bước 3: Đặt mật khẩu mới
    // ------------------------------------------------------------------

    private void handleResetPassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        String email = session != null ? (String) session.getAttribute(SESSION_RESET_EMAIL) : null;
        Boolean verified = session != null ? (Boolean) session.getAttribute(SESSION_OTP_VERIFIED) : null;

        // Chặn truy cập thẳng vào bước 3 mà chưa qua bước 2 (vd. gõ thẳng URL
        // resetPassword.jsp, hoặc POST thẳng vào ResetPasswordServlet bằng tay).
        if (email == null || verified == null || !verified) {
            response.sendRedirect(request.getContextPath() + "/forgotPassword.jsp?error=unauthorized");
            return;
        }

        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");
        if (newPassword == null || newPassword.length() < 8) {
            response.sendRedirect(request.getContextPath() + "/resetPassword.jsp?error=weak_password");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            response.sendRedirect(request.getContextPath() + "/resetPassword.jsp?error=mismatch");
            return;
        }

        String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        boolean ok = employeeDAO.updatePasswordByEmail(email, newHash);

        // Dọn sạch toàn bộ trạng thái reset trong session -- dù thành công hay
        // thất bại cũng phải xoá, không để sót cờ otpVerified=true cho request
        // sau lợi dụng (vd. tự POST lại /ResetPasswordServlet lần nữa).
        session.removeAttribute(SESSION_RESET_EMAIL);
        session.removeAttribute(SESSION_RESET_OTP);
        session.removeAttribute(SESSION_RESET_OTP_EXPIRY);
        session.removeAttribute(SESSION_RESET_OTP_ATTEMPTS);
        session.removeAttribute(SESSION_RESET_OTP_LAST_SENT);
        session.removeAttribute(SESSION_OTP_VERIFIED);

        if (!ok) {
            response.sendRedirect(request.getContextPath() + "/forgotPassword.jsp?error=update_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login.jsp?reset=success");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Sinh mã OTP mới cho email này, ghi đè toàn bộ trạng thái OTP cũ trong
     * session rồi gửi mail. Mã cũ (nếu có) mất hiệu lực ngay -- tại một thời
     * điểm chỉ có đúng 1 mã dùng được, nên bấm "gửi lại" nhiều lần không để lại
     * một loạt mã còn sống rải rác làm rộng bề mặt đoán mò.
     */
    private void issueOtp(HttpSession session, String email) {
        String otp = generateOtp();
        session.setAttribute(SESSION_RESET_EMAIL, email);
        session.setAttribute(SESSION_RESET_OTP, otp);
        session.setAttribute(SESSION_RESET_OTP_EXPIRY, System.currentTimeMillis() + OTP_VALID_MILLIS);
        session.setAttribute(SESSION_RESET_OTP_LAST_SENT, System.currentTimeMillis());
        session.removeAttribute(SESSION_RESET_OTP_ATTEMPTS); // reset bộ đếm số lần nhập sai cho mã OTP mới này
        session.removeAttribute(SESSION_OTP_VERIFIED); // reset nếu trước đó đã từng verify 1 lần khác

        EmailUtil.sendOtpEmail(email, otp);
    }

    /** Sinh mã OTP ngẫu nhiên gồm OTP_LENGTH chữ số (có thể có số 0 ở đầu, vd "004821"). */
    private String generateOtp() {
        int max = (int) Math.pow(10, OTP_LENGTH);
        int value = random.nextInt(max);
        return String.format("%0" + OTP_LENGTH + "d", value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
