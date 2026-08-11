package poscs.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.format.DateTimeFormatter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;
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
    "/login", "/changePassword", "/viewProfile", "/updateProfile", "/UpdateProfileServlet"
})
// updateProfile.jsp gửi lên dạng multipart/form-data (vì có ô tải ảnh đại
// diện) -- KHÔNG có @MultipartConfig thì request.getParameter(...) sẽ trả về
// null cho MỌI trường (kể cả text) khi content-type là multipart, không chỉ
// riêng field ảnh. Đây là annotation bắt buộc phải có để container tự parse
// các trường text ra request.getParameter() như bình thường.
@MultipartConfig
public class AuthenticationController extends HttpServlet {

    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final AddressDAO addressDAO = new AddressDAO();

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
        // Danh sách đầy đủ để đổ vào 2 dropdown Tỉnh/Thành, Quận/Huyện -- JS
        // phía client tự lọc quận/huyện theo tỉnh đã chọn (giống pattern đã
        // dùng ở updatecustomer.jsp).
        request.setAttribute("provinceList", addressDAO.findAllProvinces());
        request.setAttribute("districtList", addressDAO.findAllDistricts());
        request.getRequestDispatcher("/updateProfile.jsp").forward(request, response);
    }

    // ------------------------------------------------------------------
    // Lưu thay đổi hồ sơ cá nhân
    // ------------------------------------------------------------------

    private void handleUpdateProfile(HttpServletRequest request, HttpServletResponse response) throws IOException {
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

        // Tra 1 lần duy nhất để biết profile hiện có addressId hay chưa (dùng
        // cho setAddressFromRequest bên dưới) -- tránh tra lại DB thêm lần nữa.
        User currentProfile = employeeDAO.findProfileById(currentUser.getUserId());

        User user = new User();
        user.setUserId(currentUser.getUserId());
        user.setLastName(lastName);
        user.setMiddleName(trimToNull(request.getParameter("middleName")));
        user.setFirstName(firstName);
        user.setGender(request.getParameter("gender"));
        user.setDateOfBirth(parseDateOrNull(request.getParameter("dob")));
        user.setCitizenId(citizenId);
        user.setPhone(phone);
        user.setPersonalEmail(personalEmail);
        setAddressFromRequest(user, currentProfile, districtId, request);

        boolean ok = employeeDAO.updateProfile(user);
        if (!ok) {
            response.sendRedirect(request.getContextPath() + "/updateProfile?error=update_failed");
            return;
        }
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

        User user = employeeDAO.findByUsernameOrEmail(identifier);
        // Gộp chung 2 trường hợp "không tìm thấy user" và "sai mật khẩu" thành
        // cùng 1 thông báo lỗi ở phía client (login.jsp), để không lộ cho kẻ tấn
        // công biết username/email nào tồn tại trong hệ thống (chỉ khác nhau ở
        // bước kiểm tra: nếu user == null thì gọi BCrypt.checkpw sẽ NullPointerException,
        // nên phải kiểm tra user == null trước bằng toán tử || ngắn mạch).
        if (user == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
            redirectToLoginWithError(request, response, "invalid_credentials", identifier);
            return;
        }

        // Đăng nhập thành công: tạo session mới (true = tạo nếu chưa có) và lưu
        // user vào đó. Các servlet khác sau này sẽ đọc session.getAttribute("currentUser")
        // để biết ai đang đăng nhập và role của họ là gì (theo PERMISSIONS.md).
        HttpSession session = request.getSession(true);
        session.setAttribute("currentUser", user);
        response.sendRedirect(request.getContextPath() + "/dashboard.jsp");
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
        if (freshUser == null) {
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

    /** Trim khoảng trắng thừa; chuỗi rỗng sau khi trim coi như null (chưa nhập). */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
