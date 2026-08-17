package poscs.common;

import java.security.SecureRandom;
import java.util.Base64;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Sinh + kiểm tra CSRF token, dùng chung cho AuthenticationFilter (kiểm tra
 * mọi request POST) và các JSP (nhúng token vào form dạng hidden field, đọc
 * qua request attribute "csrfToken" mà filter đã set sẵn cho mọi request).
 *
 * Token gắn với session (sinh 1 lần, giữ nguyên suốt vòng đời session đó) --
 * đủ để chặn CSRF (kẻ tấn công không đọc được session của nạn nhân nên không
 * thể biết token) mà không cần đổi token mỗi request như 1 số scheme phức
 * tạp hơn (per-request token), vốn dễ gây lỗi "token hết hạn" khi người dùng
 * mở nhiều tab/form cùng lúc.
 */
public final class CsrfUtil {

    private static final String SESSION_ATTR = "csrfToken";
    private static final String PARAM_NAME = "csrfToken";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CsrfUtil() {
    }

    /** Lấy token đang có trong session, hoặc sinh mới (256-bit, base64url) nếu chưa có. */
    public static String getOrCreateToken(HttpSession session) {
        String token = (String) session.getAttribute(SESSION_ATTR);
        if (token == null) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            session.setAttribute(SESSION_ATTR, token);
        }
        return token;
    }

    /** true nếu tham số "csrfToken" gửi lên khớp với token đang lưu trong session. */
    public static boolean isValid(HttpServletRequest request, HttpSession session) {
        String expected = (String) session.getAttribute(SESSION_ATTR);
        String actual = request.getParameter(PARAM_NAME);
        return expected != null && constantTimeEquals(expected, actual);
    }

    /** So sánh 2 chuỗi theo thời gian không đổi -- tránh lộ thông tin qua timing side-channel. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
