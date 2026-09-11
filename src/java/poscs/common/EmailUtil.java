package poscs.common;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gửi email qua SMTP Gmail. Cấu hình lấy từ biến môi trường (giống
 * DBContext) -- xem README/DEPLOY.md để biết các biến MAIL_* cần set khi
 * deploy thật.
 */
public class EmailUtil {

    private static final Logger LOG = LoggerFactory.getLogger(EmailUtil.class);

    private static final String SMTP_HOST = System.getenv().getOrDefault("MAIL_SMTP_HOST", "smtp.gmail.com");
    private static final String SMTP_PORT = System.getenv().getOrDefault("MAIL_SMTP_PORT", "587");
    private static final String MAIL_USERNAME = System.getenv().getOrDefault("MAIL_USERNAME", "");
    private static final String MAIL_PASSWORD = System.getenv().getOrDefault("MAIL_PASSWORD", "");
    private static final String MAIL_FROM = System.getenv().getOrDefault("MAIL_FROM", MAIL_USERNAME);

    /**
     * Gửi mã OTP tới {@code toEmail}. Trả về true nếu gửi thành công (hoặc
     * đã in ra console ở chế độ dev -- xem ghi chú bên dưới).
     *
     * Chế độ dev: nếu chưa cấu hình MAIL_USERNAME/MAIL_PASSWORD (chưa có tài
     * khoản SMTP thật), hàm sẽ KHÔNG gửi email thật mà chỉ in mã OTP ra
     * console, để có thể test toàn bộ luồng quên mật khẩu mà không cần một
     * tài khoản email thật. Khi deploy thật, set 2 biến môi trường đó là
     * email/app-password SMTP của công ty thì hàm sẽ tự chuyển sang gửi
     * email thật (xem DEPLOY.md).
     */
    public static boolean sendOtpEmail(String toEmail, String otpCode) {
        if (MAIL_USERNAME.isEmpty() || MAIL_PASSWORD.isEmpty()) {
            LOG.info("[DEV MODE] Chua cau hinh SMTP, in OTP ra log.");
            LOG.info("Gui toi: {} | Ma OTP: {}", toEmail, otpCode);
            return true;
        }

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(MAIL_USERNAME, MAIL_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(MAIL_FROM));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject("[POSCS] Ma OTP dat lai mat khau");
            message.setText(
                    "Ma OTP cua ban la: " + otpCode + "\n\n"
                    + "Ma co hieu luc trong 5 phut. Vui long khong chia se ma nay cho bat ky ai.\n"
                    + "Neu ban khong yeu cau dat lai mat khau, vui long bo qua email nay."
            );
            Transport.send(message);
            return true;
        } catch (MessagingException ex) {
            LOG.error("Loi gui email otp (toEmail={})", toEmail, ex); // KHONG log otpCode
            return false;
        }
    }

    /**
     * Gửi tài khoản vừa được Admin khởi tạo (UC-26 Create Employee) tới EMAIL
     * CÁ NHÂN của nhân viên đó (không phải email công ty vừa cấp -- email
     * công ty là địa chỉ hệ thống tự sinh, chưa có hộp thư thật để nhận mail
     * cho tới khi công ty cấp hộp thư đó, nên vẫn phải gửi qua kênh nhân
     * viên chắc chắn nhận được): email công ty vừa cấp + username đăng nhập
     * + mật khẩu tạm -- dùng cùng chế độ "dev mode in ra console" như
     * sendOtpEmail() khi chưa cấu hình SMTP thật.
     */
    public static boolean sendNewAccountEmail(String toEmail, String fullName, String companyEmail, String username, String tempPassword) {
        if (MAIL_USERNAME.isEmpty() || MAIL_PASSWORD.isEmpty()) {
            LOG.info("[DEV MODE] Chua cau hinh SMTP, in thong tin tai khoan ra log.");
            LOG.info("Gui toi: {} | Email cong ty: {} | Username: {} | Mat khau tam: {}",
                    toEmail, companyEmail, username, tempPassword);
            return true;
        }

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(MAIL_USERNAME, MAIL_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(MAIL_FROM));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject("[POSCS] Tai khoan he thong POSCS cua ban da duoc tao");
            message.setText(
                    "Xin chao " + fullName + ",\n\n"
                    + "Tai khoan cua ban tren he thong POSCS da duoc quan tri vien khoi tao:\n\n"
                    + "Email cong ty duoc cap: " + companyEmail + "\n"
                    + "Ten dang nhap: " + username + "\n"
                    + "Mat khau tam thoi: " + tempPassword + "\n\n"
                    + "Vui long dang nhap va doi mat khau ngay trong lan dau tien de dam bao an toan.\n"
                    + "Neu ban khong yeu cau tao tai khoan nay, vui long lien he quan tri vien."
            );
            Transport.send(message);
            return true;
        } catch (MessagingException ex) {
            LOG.error("Loi gui email tai khoan moi (toEmail={}, username={})", toEmail, username, ex); // KHONG log tempPassword
            return false;
        }
    }
}
