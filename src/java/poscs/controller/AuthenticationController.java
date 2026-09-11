package poscs.controller;

import java.io.IOException;
import java.security.SecureRandom;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.EmailUtil;
import poscs.common.FileStorage;
import poscs.common.Logs;
import poscs.common.TextRules;
import poscs.dao.AddressDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Address;
import poscs.model.User;

/**
 * Đăng nhập / đăng xuất / đổi mật khẩu / xem & sửa hồ sơ cá nhân (khi đã
 * đăng nhập). Đăng nhập thành công lưu {@link User} (đã kèm
 * {@link poscs.model.Role}) vào session dưới key "currentUser" -- các
 * controller khác dựa vào key này để enforce phân quyền theo PERMISSIONS.md.
 */
@WebServlet(name = "AuthenticationController", urlPatterns = {
    "/login", "/changePassword", "/viewProfile", "/updateProfile", "/UpdateProfileServlet",
    "/ForgotPasswordServlet", "/VerifyOtpServlet", "/ResetPasswordServlet", "/ResendOtpServlet"
})
// updateProfile.jsp gửi lên dạng multipart/form-data (vì có ô tải ảnh đại
// diện) -- KHÔNG có @MultipartConfig thì request.getParameter(...) sẽ trả về
// null cho MỌI trường (kể cả text) khi content-type là multipart, không chỉ
// riêng field ảnh. Đây là annotation bắt buộc phải có để container tự parse
// các trường text ra request.getParameter() như bình thường.
@MultipartConfig
public class AuthenticationController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationController.class);

    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final AddressDAO addressDAO = new AddressDAO();

    /** Thư mục con trong kho upload dành cho ảnh đại diện -- xem FileStorage. */
    private static final String AVATAR_SUBFOLDER = "avatars";

    // Số lần đăng nhập sai tối đa cho phép từ 1 địa chỉ IP trước khi tạm khoá
    // -- không có giới hạn này thì /login có thể bị dò mật khẩu (brute-force)
    // hoặc rải mật khẩu qua nhiều tài khoản (password spraying) không giới
    // hạn số lần, giống lỗ hổng OTP đã sửa ở luồng quên mật khẩu bên dưới.
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_LOCKOUT_MILLIS = 15 * 60 * 1000L; // 15 phút

    /**
     * Đếm theo ĐỊA CHỈ IP, không theo username/email đã gõ -- nếu đếm theo
     * định danh tài khoản, việc có bị khoá hay không sẽ vô tình lộ ra tài
     * khoản đó CÓ TỒN TẠI (chỉ tài khoản thật mới tích luỹ được số lần sai),
     * phá vỡ đúng nguyên tắc chống user-enumeration mà handleLogin đã cố
     * gắng giữ (cùng 1 lỗi "invalid_credentials" cho cả 2 trường hợp không
     * tồn tại lẫn sai mật khẩu). Đếm theo IP còn chặn được cả kiểu tấn công
     * rải mật khẩu qua nhiều username khác nhau từ cùng 1 nguồn.
     */
    private static final Map<String, LoginAttemptState> LOGIN_ATTEMPTS_BY_IP = new ConcurrentHashMap<>();

    // Giữ mốc đếm của 1 IP thêm bao lâu sau lần gõ sai cuối. Phải dài hơn
    // LOGIN_LOCKOUT_MILLIS, nếu không mốc bị dọn khi khoá còn hiệu lực và
    // người đang bị khoá lại thử được ngay.
    private static final long LOGIN_ATTEMPT_RETENTION_MILLIS = LOGIN_LOCKOUT_MILLIS * 2;

    private static final class LoginAttemptState {
        private int failedCount;
        private long lockedUntilMillis;
        private long lastFailureMillis;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("/viewProfile".equals(request.getServletPath())) {
            handleViewProfile(request, response);
            return;
        }
        if ("/updateProfile".equals(request.getServletPath())) {
            handleEditProfileForm(request, response);
            return;
        }

        String action = request.getParameter("action");
        if ("logout".equals(action)) {
            // getSession(false): KHÔNG tự tạo session mới nếu chưa có -- nếu
            // request này chưa từng đăng nhập thì session sẽ là null, và ta
            // không cần làm gì thêm (đằng nào cũng chưa có gì để huỷ).
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate(); // xoá toàn bộ session, bao gồm cả currentUser
            }
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }
        // GET tới /login mà không phải logout (vd. gõ thẳng URL) -- servlet này
        // không tự vẽ form, chỉ điều hướng sang login.jsp (trang tĩnh) để hiển thị.
        response.sendRedirect(request.getContextPath() + "/login.jsp");
    }

    // ------------------------------------------------------------------
    // Xem hồ sơ cá nhân
    // ------------------------------------------------------------------

    private void handleViewProfile(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User currentUser = session != null ? (User) session.getAttribute("currentUser") : null;

        // AuthenticationFilter đã chặn request chưa đăng nhập từ trước rồi,
        // nhưng vẫn kiểm tra lại ở đây cho chắc (không tin tưởng mù quáng vào
        // 1 lớp bảo vệ duy nhất) -- xem thêm PERMISSIONS.md.
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        // Luôn tra lại từ DB thay vì dùng thẳng currentUser trong session --
        // session chỉ lưu vài trường cơ bản dùng cho việc đăng nhập/phân
        // quyền (xem EmployeeDAO.findByUsernameOrEmail), không có đủ thông
        // tin để hiển thị hồ sơ đầy đủ (địa chỉ, CCCD, ngày sinh...).
        User profile = employeeDAO.findProfileById(currentUser.getUserId());
        if (profile == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        request.setAttribute("profile", profile);
        // Format ngày tháng sẵn thành chuỗi ở đây thay vì dùng <fmt:formatDate>
        // trong JSP -- thư viện JSTL (org.apache.taglibs.standard) có lỗi đã
        // biết: khi value là java.sql.Date (kiểu trả về từ ResultSet.getDate),
        // pattern bị bỏ qua và tự động in ra dạng mặc định "yyyy-MM-dd" thay vì
        // pattern đã khai báo -- format thủ công ở đây tránh hẳn lỗi đó.
        request.setAttribute("hireDateText", formatDate(profile.getHireDate()));
        request.setAttribute("dateOfBirthText", formatDate(profile.getDateOfBirth()));
        request.getRequestDispatcher("/viewProfile.jsp").forward(request, response);
    }

    private static final DateTimeFormatter DATE_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private String formatDate(Date date) {
        return date != null ? date.toLocalDate().format(DATE_DISPLAY_FORMAT) : null;
    }

    // ------------------------------------------------------------------
    // Hiện form sửa hồ sơ cá nhân (điền sẵn dữ liệu hiện tại)
    // ------------------------------------------------------------------

    private void handleEditProfileForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User currentUser = session != null ? (User) session.getAttribute("currentUser") : null;
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        User profile = employeeDAO.findProfileById(currentUser.getUserId());
        if (profile == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        request.setAttribute("profile", profile);
        request.setAttribute("hireDateText", formatDate(profile.getHireDate()));
        // Dropdown "Tỉnh/Thành phố" đổ sẵn (34 dòng, rẻ); dropdown "Xã/Phường"
        // nạp qua AJAX theo tỉnh đã chọn (xem AddressController) thay vì đổ
        // sẵn toàn bộ ~3.321 xã/phường vào trang.
        request.setAttribute("provinceList", addressDAO.findAllProvinces());
        request.getRequestDispatcher("/updateProfile.jsp").forward(request, response);
    }

    // ------------------------------------------------------------------
    // Lưu thay đổi hồ sơ cá nhân
    // ------------------------------------------------------------------

    private void handleUpdateProfile(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User currentUser = session != null ? (User) session.getAttribute("currentUser") : null;
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        String lastName = trimToNull(request.getParameter("lastName"));
        String firstName = trimToNull(request.getParameter("firstName"));
        String citizenId = trimToNull(request.getParameter("citizenId"));
        String phone = trimToNull(request.getParameter("phone"));
        String personalEmail = trimToNull(request.getParameter("personalEmail"));

        // Validate lại phía server -- form đã validate bằng JS rồi, nhưng JS có
        // thể bị tắt/bypass, không được coi đó là lớp bảo vệ duy nhất.
        if (lastName == null || firstName == null || citizenId == null) {
            response.sendRedirect(request.getContextPath() + "/updateProfile?error=missing_fields");
            return;
        }
        // Họ tên/địa chỉ là ô văn bản tự do duy nhất người dùng tự gõ ở đây
        // (SĐT/email đã có regex riêng bên dưới) -- chặn ký tự phá ngữ cảnh
        // HTML để dữ liệu vào CSDL luôn sạch, xem TextRules.
        String middleName = trimToNull(request.getParameter("middleName"));
        String addressDetail = trimToNull(request.getParameter("addressDetail"));
        if (!TextRules.isSafeFreeText(lastName) || !TextRules.isSafeFreeText(middleName)
                || !TextRules.isSafeFreeText(firstName) || !TextRules.isSafeFreeText(addressDetail)) {
            response.sendRedirect(request.getContextPath() + "/updateProfile?error=invalid_characters");
            return;
        }
        if (phone == null || !phone.replaceAll("\\s", "").matches("^(0|\\+84)(3|5|7|8|9)[0-9]{8}$")) {
            response.sendRedirect(request.getContextPath() + "/updateProfile?error=invalid_phone");
            return;
        }
        if (personalEmail == null || !personalEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            response.sendRedirect(request.getContextPath() + "/updateProfile?error=invalid_email");
            return;
        }
        // Bắt buộc chọn Xã/Phường (và do đó cả Tỉnh/Thành, vì mỗi xã/phường
        // chỉ thuộc đúng 1 tỉnh) -- địa chỉ chi tiết thì không bắt buộc.
        Integer districtId = parseIntOrNull(request.getParameter("districtId"));
        if (districtId == null) {
            response.sendRedirect(request.getContextPath() + "/updateProfile?error=missing_address");
            return;
        }
        // phone và citizen_id đều có UNIQUE KEY. Không kiểm trước ở đây thì một
        // số trùng (gõ nhầm 1 chữ số thành số của đồng nghiệp) sẽ rơi xuống tận
        // DB, bật lên thành SQLException và người dùng chỉ thấy "update_failed"
        // chung chung -- không hiểu vì sao, gõ lại y nguyên rồi lại hỏng.
        // EmployeeController.handleUpdate đã kiểm đúng cách từ trước, đường sửa
        // hồ sơ tự phục vụ này thì bị bỏ sót.
        if (employeeDAO.existsByPhone(phone, currentUser.getUserId())) {
            response.sendRedirect(request.getContextPath() + "/updateProfile?error=duplicate_phone");
            return;
        }
        if (employeeDAO.existsByCitizenId(citizenId, currentUser.getUserId())) {
            response.sendRedirect(request.getContextPath() + "/updateProfile?error=duplicate_citizen");
            return;
        }

        // Kiểm ảnh đại diện TRƯỚC khi ghi, giống các ô chọn file khác -- file
        // sai loại phải báo lỗi chứ không được lặng lẽ biến mất.
        if (!FileStorage.isAcceptable(request.getPart("avatar"), FileStorage.IMAGE_EXTENSIONS)) {
            response.sendRedirect(request.getContextPath() + "/updateProfile?error=invalid_image_type");
            return;
        }

        // Tra 1 lần duy nhất để biết profile hiện có addressId hay chưa (dùng
        // cho setAddressFromRequest bên dưới) -- tránh tra lại DB thêm lần nữa.
        User currentProfile = employeeDAO.findProfileById(currentUser.getUserId());

        User user = new User();
        user.setUserId(currentUser.getUserId());
        user.setLastName(lastName);
        user.setMiddleName(middleName);
        user.setFirstName(firstName);
        user.setGender(request.getParameter("gender"));
        user.setDateOfBirth(parseDateOrNull(request.getParameter("dob")));
        user.setCitizenId(citizenId);
        user.setPhone(phone);
        user.setPersonalEmail(personalEmail);
        // Ô chọn ảnh để trống vẫn gửi lên 1 Part rỗng -> save() trả null; khi đó
        // giữ ảnh cũ thay vì xoá mất chỉ vì lần lưu này người dùng không đổi ảnh.
        String newAvatarUrl = FileStorage.save(request.getPart("avatar"), AVATAR_SUBFOLDER,
                FileStorage.IMAGE_EXTENSIONS);
        user.setAvatarUrl(newAvatarUrl != null ? newAvatarUrl
                : (currentProfile != null ? currentProfile.getAvatarUrl() : null));
        setAddressFromRequest(user, currentProfile, districtId, request);

        boolean ok = employeeDAO.updateProfile(user);
        if (!ok) {
            LOG.warn("Cap nhat ho so ca nhan that bai (actor={})", Logs.actor(request));
            response.sendRedirect(request.getContextPath() + "/updateProfile?error=update_failed");
            return;
        }
        // Topbar lấy ảnh từ currentUser trong session (có mặt ở mọi trang), nên
        // phải cập nhật luôn -- không thì ảnh mới chỉ xuất hiện sau lần đăng
        // nhập kế tiếp, người dùng tưởng lưu hỏng.
        currentUser.setAvatarUrl(user.getAvatarUrl());
        response.sendRedirect(request.getContextPath() + "/viewProfile");
    }

    /** districtId đã được validate là bắt buộc trước khi gọi hàm này -- addressDetail thì không. */
    private void setAddressFromRequest(User user, User currentProfile, int districtId, HttpServletRequest request) {
        String addressDetail = trimToNull(request.getParameter("addressDetail"));
        Address address = new Address();
        // Nếu profile hiện tại đã có địa chỉ, giữ nguyên addressId để
        // EmployeeDAO.updateProfile() UPDATE đúng dòng đó thay vì tạo dòng mới.
        if (currentProfile != null && currentProfile.getAddress() != null) {
            address.setAddressId(currentProfile.getAddress().getAddressId());
        }
        // street_and_local_name là NOT NULL trong DB -- dùng chuỗi rỗng thay vì
        // null khi người dùng không nhập địa chỉ chi tiết.
        address.setStreetAndLocalName(addressDetail != null ? addressDetail : "");
        address.setDistrictId(districtId);
        user.setAddress(address);
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Date parseDateOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Date.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Nhiều form khác nhau POST tới nhiều URL khác nhau nhưng cùng 1
        // servlet này xử lý -- dùng getServletPath() để biết đang xử lý
        // request nào (khớp với url-pattern ở @WebServlet).
        String path = request.getServletPath();
        if ("/changePassword".equals(path)) {
            handleChangePassword(request, response);
            return;
        }
        if ("/UpdateProfileServlet".equals(path)) {
            handleUpdateProfile(request, response);
            return;
        }
        // Luồng "Quên mật khẩu" 3 bước + gửi lại mã, xem phần cuối lớp này.
        switch (path) {
            case "/ForgotPasswordServlet":
                handleForgotPassword(request, response);
                return;
            case "/VerifyOtpServlet":
                handleVerifyOtp(request, response);
                return;
            case "/ResetPasswordServlet":
                handleResetPassword(request, response);
                return;
            case "/ResendOtpServlet":
                handleResendOtp(request, response);
                return;
            default:
                break;
        }
        handleLogin(request, response);
    }

    // ------------------------------------------------------------------
    // Đăng nhập
    // ------------------------------------------------------------------

    private void handleLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Form login.jsp gửi lên field "username", nhưng người dùng có thể gõ
        // username HOẶC email vào đó -- EmployeeDAO.findByUsernameOrEmail sẽ
        // khớp cả 2 khả năng.
        String identifier = trimToNull(request.getParameter("username"));
        String password = request.getParameter("password");

        if (identifier == null || password == null || password.isEmpty()) {
            redirectToLoginWithError(request, response, "missing_fields", identifier);
            return;
        }

        String clientIp = request.getRemoteAddr();
        long now = System.currentTimeMillis();
        if (isLockedOut(clientIp, now)) {
            redirectToLoginWithError(request, response, "too_many_attempts", identifier);
            return;
        }

        User user = employeeDAO.findByUsernameOrEmail(identifier);
        // Gộp chung 2 trường hợp "không tìm thấy user" và "sai mật khẩu" thành
        // cùng 1 thông báo lỗi ở phía client (login.jsp), để không lộ cho kẻ tấn
        // công biết username/email nào tồn tại trong hệ thống (chỉ khác nhau ở
        // bước kiểm tra: nếu user == null thì gọi BCrypt.checkpw sẽ NullPointerException,
        // nên phải kiểm tra user == null trước bằng toán tử || ngắn mạch).
        if (user == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
            recordFailedLoginAttempt(clientIp, now);
            redirectToLoginWithError(request, response, "invalid_credentials", identifier);
            return;
        }
        // Mật khẩu đúng -- xoá bộ đếm sai của IP này, không giữ lại tính vào lần sau.
        LOGIN_ATTEMPTS_BY_IP.remove(clientIp);

        // Chỉ SAU KHI đã xác minh đúng mật khẩu mới kiểm tra tài khoản có bị
        // khóa (is_deleted=1) hay không, rồi mới báo riêng "tài khoản bị khóa"
        // -- nếu kiểm tra trước bước xác minh mật khẩu, kẻ dò mật khẩu có thể
        // lợi dụng sự khác biệt giữa 2 thông báo lỗi để suy ra tài khoản nào
        // tồn tại (user enumeration), kể cả khi không biết đúng mật khẩu.
        if (user.isDeleted()) {
            redirectToLoginWithError(request, response, "account_inactive", identifier);
            return;
        }

        // Đăng nhập thành công: huỷ session cũ (nếu có) trước khi tạo session mới,
        // thay vì tái sử dụng session hiện tại -- nếu không, 1 session ID được kẻ
        // tấn công cài sẵn từ trước (session fixation, vd qua URL dạng
        // ;jsessionid=..., hoặc set cookie từ 1 origin liên quan) sẽ được gắn thẳng
        // với currentUser vừa đăng nhập, cho phép kẻ tấn công dùng session ID đó
        // để mạo danh nạn nhân ngay sau khi họ đăng nhập thành công.
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        // Bỏ chuỗi băm mật khẩu trước khi cất object này vào session: mọi JSP
        // đều đọc được ${sessionScope.currentUser.*}, nên chỉ cần một lần lỡ
        // tay in cả object ra là lộ hash. Sau checkpw() ở trên thì không còn
        // chỗ nào cần tới nó nữa.
        user.setPasswordHash(null);
        session.setAttribute("currentUser", user);
        response.sendRedirect(request.getContextPath() + "/dashboard");
    }

    // ------------------------------------------------------------------
    // Đổi mật khẩu (yêu cầu đã đăng nhập + biết đúng mật khẩu hiện tại)
    // ------------------------------------------------------------------

    private void handleChangePassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        User currentUser = session != null ? (User) session.getAttribute("currentUser") : null;

        // Chưa đăng nhập thì không có gì để đổi -- đá về trang login.
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        String oldPassword = request.getParameter("oldPassword");
        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");

        // Luôn tra lại user MỚI NHẤT từ DB để lấy password_hash hiện hành,
        // KHÔNG dùng hash cũ đang cache trong session -- phòng trường hợp mật
        // khẩu đã bị đổi ở nơi khác (vd. một tab khác) từ lúc đăng nhập tới giờ.
        User freshUser = employeeDAO.findByUsernameOrEmail(currentUser.getUsername());
        // freshUser.isDeleted(): tài khoản có thể đã bị khóa bởi admin từ lúc
        // đăng nhập tới giờ (findByUsernameOrEmail giờ trả về cả tài khoản bị
        // khóa, xem ghi chú ở EmployeeDAO) -- coi như phiên hết hiệu lực, huỷ
        // session và đá về màn hình đăng nhập, KHÔNG cho đổi mật khẩu.
        if (freshUser == null || freshUser.isDeleted()) {
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        // Bắt buộc phải đúng mật khẩu hiện tại thì mới cho đổi -- đây chính là
        // yêu cầu cốt lõi của tính năng này, khác với luồng "quên mật khẩu"
        // (nơi xác thực bằng OTP email thay vì bằng mật khẩu cũ).
        if (oldPassword == null || !BCrypt.checkpw(oldPassword, freshUser.getPasswordHash())) {
            response.sendRedirect(request.getContextPath() + "/changePassword.jsp?error=wrong_old_password");
            return;
        }

        if (newPassword == null || newPassword.length() < 8) {
            response.sendRedirect(request.getContextPath() + "/changePassword.jsp?error=weak_password");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            response.sendRedirect(request.getContextPath() + "/changePassword.jsp?error=mismatch");
            return;
        }
        // Yêu cầu ghi rõ trong hint-list của changePassword.jsp: "Không trùng với mật khẩu cũ".
        if (BCrypt.checkpw(newPassword, freshUser.getPasswordHash())) {
            response.sendRedirect(request.getContextPath() + "/changePassword.jsp?error=same_as_old");
            return;
        }

        String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        boolean ok = employeeDAO.updatePasswordByEmail(freshUser.getEmail(), newHash);
        if (!ok) {
            LOG.warn("Doi mat khau that bai o buoc ghi CSDL (actor={})", Logs.actor(request));
            response.sendRedirect(request.getContextPath() + "/changePassword.jsp?error=update_failed");
            return;
        }

        // Đổi mật khẩu xong thì huỷ luôn phiên đăng nhập hiện tại và bắt đăng
        // nhập lại bằng mật khẩu mới -- không chỉ để chuyển màn hình, mà còn để
        // phòng trường hợp phiên (session) hiện tại đã bị lộ/đánh cắp: đổi mật
        // khẩu xong mà vẫn giữ nguyên session cũ thì kẻ chiếm được session đó
        // vẫn tiếp tục dùng được như thường, không có tác dụng bảo vệ gì cả.
        session.invalidate();
        response.sendRedirect(request.getContextPath() + "/login.jsp?reset=success");
    }

    /**
     * Điều hướng về login.jsp kèm mã lỗi VÀ giữ lại giá trị đã gõ ở ô
     * username/email, để người dùng không phải gõ lại từ đầu khi chỉ sai mật
     * khẩu. KHÔNG làm tương tự với mật khẩu -- không được đưa mật khẩu vào
     * URL (sẽ lộ ra lịch sử trình duyệt, log server), nên ô mật khẩu vẫn phải
     * để trống sau khi redirect, người dùng phải gõ lại là bình thường.
     */
    private void redirectToLoginWithError(HttpServletRequest request, HttpServletResponse response,
            String error, String identifier) throws IOException {
        String url = request.getContextPath() + "/login.jsp?error=" + error;
        if (identifier != null) {
            url += "&username=" + URLEncoder.encode(identifier, StandardCharsets.UTF_8);
        }
        response.sendRedirect(url);
    }

    private boolean isLockedOut(String clientIp, long now) {
        LoginAttemptState state = LOGIN_ATTEMPTS_BY_IP.get(clientIp);
        return state != null && state.lockedUntilMillis > now;
    }

    /** Tăng bộ đếm sai của 1 IP; đủ MAX_LOGIN_ATTEMPTS lần thì khoá tạm LOGIN_LOCKOUT_MILLIS. */
    private void recordFailedLoginAttempt(String clientIp, long now) {
        // Chỉ IP đăng nhập THÀNH CÔNG mới được xoá khỏi map (xem handleLogin),
        // nên IP chỉ toàn gõ sai sẽ nằm lại mãi -- người quét cả dải IP hay chỉ
        // là lượng người dùng tích tụ theo thời gian đều làm map phình không
        // giới hạn. Dọn các mốc đã hết hiệu lực trước khi thêm mốc mới.
        LOGIN_ATTEMPTS_BY_IP.values().removeIf(s -> s.lastFailureMillis > 0
                && now - s.lastFailureMillis > LOGIN_ATTEMPT_RETENTION_MILLIS);

        LoginAttemptState state = LOGIN_ATTEMPTS_BY_IP.computeIfAbsent(clientIp, k -> new LoginAttemptState());
        synchronized (state) {
            // Đợt khoá trước đã hết hạn -> bắt đầu lại từ đầu. Không reset thì
            // failedCount chỉ có tăng, nên sau lần bị khoá đầu tiên, CHỈ CẦN gõ
            // sai thêm 1 lần là lại dính đủ 15 phút nữa (6 >= 5), lặp vô hạn.
            // Bộ dọn theo LOGIN_ATTEMPT_RETENTION_MILLIS ở trên không cứu được,
            // vì mỗi lần sai lại làm mới lastFailureMillis.
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

    /** Trim khoảng trắng thừa; chuỗi rỗng sau khi trim coi như null (chưa nhập). */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ------------------------------------------------------------------
    // Quên mật khẩu: nhập email -> nhận OTP -> đặt lại mật khẩu
    // ------------------------------------------------------------------

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

    // Cooldown ở trên gắn với session, nên chỉ cần xoá cookie là lách được:
    // mỗi request "mới tinh" lại có session mới, chưa từng gửi mã, và luồng
    // bước 1 sẽ gửi thêm một email nữa cho địa chỉ nạn nhân. Vì thế phải giới
    // hạn thêm theo IP -- cùng cách /login đang chặn dò mật khẩu -- để không
    // ai biến hệ thống thành công cụ dội mail vào hòm thư người khác.
    private static final int MAX_OTP_REQUESTS_PER_IP = 10;
    private static final long OTP_REQUEST_WINDOW_MILLIS = 15 * 60 * 1000L;

    private static final Map<String, OtpRequestState> OTP_REQUESTS_BY_IP = new ConcurrentHashMap<>();

    private static final class OtpRequestState {
        private int count;
        private long windowStartMillis;
    }

    private static final String SESSION_RESET_EMAIL = "resetEmail";
    private static final String SESSION_RESET_OTP = "resetOtp";
    private static final String SESSION_RESET_OTP_EXPIRY = "resetOtpExpiry";
    private static final String SESSION_RESET_OTP_ATTEMPTS = "resetOtpAttempts";
    private static final String SESSION_RESET_OTP_LAST_SENT = "resetOtpLastSent";
    private static final String SESSION_OTP_VERIFIED = "otpVerified";

    private final SecureRandom random = new SecureRandom();

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
        // Kiểm hạn mức TRƯỚC khi tra CSDL: quá hạn thì không gửi mail, cũng
        // không chạm DB. Vẫn điều hướng sang verifyOtp.jsp như mọi trường hợp
        // khác để không tiết lộ email nào có tài khoản (xem ghi chú bên trên).
        if (!allowOtpRequest(clientIp(request), System.currentTimeMillis())) {
            response.sendRedirect(request.getContextPath() + "/verifyOtp.jsp");
            return;
        }

        User user = employeeDAO.findByUsernameOrEmail(email);
        if (user != null) {
            issueOtp(request.getSession(true), email);
        }

        response.sendRedirect(request.getContextPath() + "/verifyOtp.jsp");
    }

    /**
     * true nếu IP này còn lượt yêu cầu mã trong cửa sổ hiện tại. Cửa sổ trượt
     * kiểu "reset theo mốc": hết OTP_REQUEST_WINDOW_MILLIS thì bắt đầu đếm lại
     * từ đầu -- đủ để chặn dội mail mà không phải giữ lịch sử từng lần gọi.
     */
    private boolean allowOtpRequest(String clientIp, long now) {
        // Dọn các mốc đã hết hạn để map không phình mãi theo số IP từng ghé qua.
        OTP_REQUESTS_BY_IP.values().removeIf(s -> now - s.windowStartMillis > OTP_REQUEST_WINDOW_MILLIS);

        OtpRequestState state = OTP_REQUESTS_BY_IP.computeIfAbsent(clientIp, k -> {
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

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
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

        // Gửi lại cũng tiêu lượt của cùng hạn mức theo IP: chỉ chờ hết 30 giây
        // mỗi lần thì vẫn dội được đều đặn vào một hòm thư suốt cả ngày.
        if (!allowOtpRequest(clientIp(request), System.currentTimeMillis())) {
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
            LOG.warn("Dat lai mat khau that bai o buoc ghi CSDL (email={})", email);
            response.sendRedirect(request.getContextPath() + "/forgotPassword.jsp?error=update_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login.jsp?reset=success");
    }


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
}
