package poscs.controller;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mindrot.jbcrypt.BCrypt;
import poscs.common.AccessControl;
import poscs.common.EmailUtil;
import poscs.dao.AddressDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Address;
import poscs.model.User;

/**
 * Controller quản lý nhân viên (FE-02 / UC-24 -> UC-29, bảng users). Khác
 * với Customer/Contract/Product/Ticket -- PERMISSIONS.md không có tier
 * "View only" nào cho Employee, chỉ Admin mới được đụng vào (kể cả xem danh
 * sách/chi tiết) -- nên gate quyền 1 lần duy nhất ở đầu doGet/doPost thay vì
 * lặp lại AccessControl.requireFullAccess() ở từng handler như các
 * controller khác.
 */
@WebServlet(name = "EmployeeController", urlPatterns = {"/employee"})
public class EmployeeController extends HttpServlet {

    /** BR-31: email công ty do hệ thống tự cấp (chưa có hộp thư thật), dạng &lt;username&gt;@postef.com.vn. */
    private static final String COMPANY_EMAIL_DOMAIN = "@postef.com.vn";

    private static final int PAGE_SIZE = 10;
    private static final String LIST_VIEW = "/jsp/admin/listEmployee.jsp";
    private static final String DETAIL_VIEW = "/jsp/admin/viewdetailEmployee.jsp";
    private static final String CREATE_VIEW = "/jsp/admin/addEmployee.jsp";
    private static final String UPDATE_VIEW = "/jsp/admin/updateEmployee.jsp";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final AddressDAO addressDAO = new AddressDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.EMPLOYEE)) {
            return;
        }
        String action = request.getParameter("action");
        if (action == null) {
            action = "list";
        }
        switch (action) {
            case "view":
                showDetail(request, response);
                break;
            case "new":
                showCreateForm(request, response);
                break;
            case "edit":
                showEditForm(request, response);
                break;
            case "list":
            default:
                showList(request, response);
                break;
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.EMPLOYEE)) {
            return;
        }
        String action = request.getParameter("action");
        if (action == null) {
            action = "";
        }
        switch (action) {
            case "create":
                handleCreate(request, response);
                break;
            case "update":
                handleUpdate(request, response);
                break;
            case "toggleStatus":
                handleToggleStatus(request, response);
                break;
            case "sendAccount":
                handleSendAccount(request, response);
                break;
            default:
                response.sendRedirect(request.getContextPath() + "/employee");
        }
    }

    // ------------------------------------------------------------------
    // GET actions
    // ------------------------------------------------------------------

    private void showList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int page = parseIntOrDefault(request.getParameter("page"), 1);
        if (page < 1) {
            page = 1;
        }
        String keyword = request.getParameter("keyword");
        String statusFilter = request.getParameter("status");
        Integer roleFilter = parseIntOrNull(request.getParameter("roleId"));

        List<User> employeeList = employeeDAO.findAll(page, PAGE_SIZE, keyword, statusFilter, roleFilter);
        int totalCount = employeeDAO.countAll(keyword, statusFilter, roleFilter);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));

        request.setAttribute("employeeList", employeeList);
        request.setAttribute("roleList", employeeDAO.findAllRoles());
        request.setAttribute("currentPage", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalCount", totalCount);
        request.setAttribute("keyword", keyword);
        request.setAttribute("statusFilter", statusFilter);
        request.setAttribute("roleFilter", roleFilter);

        request.getRequestDispatcher(LIST_VIEW).forward(request, response);
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        User employee = id != null ? employeeDAO.findById(id) : null;
        if (employee == null) {
            response.sendRedirect(request.getContextPath() + "/employee?error=notfound");
            return;
        }
        request.setAttribute("employee", employee);
        // Format sẵn thành chuỗi dd/MM/yyyy ở đây thay vì dùng <fmt:formatDate>
        // trong JSP -- cùng lý do đã ghi ở AuthenticationController.formatDate:
        // JSTL format sai với java.sql.Date (luôn ra "yyyy-MM-dd" bất kể pattern).
        request.setAttribute("dateOfBirthText", formatDate(employee.getDateOfBirth()));
        request.setAttribute("hireDateText", formatDate(employee.getHireDate()));
        request.getRequestDispatcher(DETAIL_VIEW).forward(request, response);
    }

    private static final DateTimeFormatter DATE_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private String formatDate(Date date) {
        return date != null ? date.toLocalDate().format(DATE_DISPLAY_FORMAT) : null;
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("roleList", employeeDAO.findAllRoles());
        request.setAttribute("departmentList", employeeDAO.findAllDepartments());
        request.setAttribute("provinceList", addressDAO.findAllProvinces());
        request.getRequestDispatcher(CREATE_VIEW).forward(request, response);
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        User employee = id != null ? employeeDAO.findById(id) : null;
        if (employee == null) {
            response.sendRedirect(request.getContextPath() + "/employee?error=notfound");
            return;
        }
        request.setAttribute("employee", employee);
        request.setAttribute("roleList", employeeDAO.findAllRoles());
        request.setAttribute("departmentList", employeeDAO.findAllDepartments());
        request.setAttribute("provinceList", addressDAO.findAllProvinces());
        request.getRequestDispatcher(UPDATE_VIEW).forward(request, response);
    }

    // ------------------------------------------------------------------
    // POST actions
    // ------------------------------------------------------------------

    /**
     * UC-26 Create Employee: chỉ tạo hồ sơ + tự cấp email công ty
     * (username@postef.com.vn, chưa phải hộp thư thật) + tự sinh username/mật
     * khẩu tạm (hash ngay, không giữ lại bản rõ) -- KHÔNG gửi email ở bước
     * này. Việc gửi thông tin tài khoản cho nhân viên là hành động Admin chủ
     * động bấm riêng ở trang chi tiết (xem handleSendAccount), để Admin có
     * thể rà lại thông tin trước khi gửi và gửi lại được nếu cần.
     */
    private void handleCreate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User u = buildUserFromRequest(request, new User());

        if (!isValidCommonFields(u)) {
            response.sendRedirect(request.getContextPath() + "/employee?action=new&error=invalid");
            return;
        }
        if (employeeDAO.existsByPhone(u.getPhone(), null)) {
            response.sendRedirect(request.getContextPath() + "/employee?action=new&error=duplicate_phone");
            return;
        }
        if (employeeDAO.existsByCitizenId(u.getCitizenId(), null)) {
            response.sendRedirect(request.getContextPath() + "/employee?action=new&error=duplicate_citizen");
            return;
        }

        String username = employeeDAO.generateUniqueUsername(u.getLastName(), u.getMiddleName(), u.getFirstName());
        u.setUsername(username);
        u.setEmail(username + COMPANY_EMAIL_DOMAIN);
        u.setPasswordHash(BCrypt.hashpw(generateTempPassword(), BCrypt.gensalt()));

        int newId = employeeDAO.insert(u);
        if (newId <= 0) {
            response.sendRedirect(request.getContextPath() + "/employee?action=new&error=create_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/employee?action=view&id=" + newId);
    }

    /**
     * Admin chủ động bấm "Gửi thông tin tài khoản" ở trang chi tiết (tạo mới
     * lẫn gửi lại đều dùng chung action này) -- vì mật khẩu tạm đã hash ngay
     * lúc tạo/lần gửi trước và không lưu bản rõ ở đâu cả, mỗi lần bấm đều
     * CẤP LẠI 1 mật khẩu tạm mới rồi gửi, không phải gửi lại y hệt mật khẩu
     * cũ. Gửi tới EMAIL CÁ NHÂN (kênh chắc chắn nhận được, khác email công ty
     * tự cấp chưa có hộp thư thật).
     */
    private void handleSendAccount(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        User employee = id != null ? employeeDAO.findById(id) : null;
        if (employee == null) {
            response.sendRedirect(request.getContextPath() + "/employee?error=notfound");
            return;
        }

        String tempPassword = generateTempPassword();
        boolean updated = employeeDAO.updatePasswordHash(id, BCrypt.hashpw(tempPassword, BCrypt.gensalt()));
        if (!updated) {
            response.sendRedirect(request.getContextPath() + "/employee?action=view&id=" + id + "&error=send_failed");
            return;
        }

        boolean mailSent = EmailUtil.sendNewAccountEmail(employee.getPersonalEmail(), employee.getFullName(), employee.getEmail(), employee.getUsername(), tempPassword);
        String suffix = mailSent ? "&sent=1" : "&warning=mail_failed";
        response.sendRedirect(request.getContextPath() + "/employee?action=view&id=" + id + suffix);
    }

    private void handleUpdate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = parseIntOrNull(request.getParameter("userId"));
        if (id == null || employeeDAO.findById(id) == null) {
            response.sendRedirect(request.getContextPath() + "/employee?error=notfound");
            return;
        }

        User u = buildUserFromRequest(request, new User());
        u.setUserId(id);

        if (!isValidCommonFields(u)) {
            response.sendRedirect(request.getContextPath() + "/employee?action=edit&id=" + id + "&error=invalid");
            return;
        }
        if (employeeDAO.existsByPhone(u.getPhone(), id)) {
            response.sendRedirect(request.getContextPath() + "/employee?action=edit&id=" + id + "&error=duplicate_phone");
            return;
        }
        if (employeeDAO.existsByCitizenId(u.getCitizenId(), id)) {
            response.sendRedirect(request.getContextPath() + "/employee?action=edit&id=" + id + "&error=duplicate_citizen");
            return;
        }

        boolean ok = employeeDAO.update(u);
        if (!ok) {
            response.sendRedirect(request.getContextPath() + "/employee?action=edit&id=" + id + "&error=update_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/employee?action=view&id=" + id);
    }

    /** UC-29 Ban/Unban Employee (BR-25/BR-26) -- không cho tự khóa chính tài khoản Admin đang đăng nhập. */
    private void handleToggleStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        User target = id != null ? employeeDAO.findById(id) : null;
        if (target == null) {
            response.sendRedirect(request.getContextPath() + "/employee?error=notfound");
            return;
        }

        User currentUser = AccessControl.currentUser(request);
        if (currentUser != null && currentUser.getUserId() == target.getUserId()) {
            response.sendRedirect(request.getContextPath() + "/employee?action=view&id=" + id + "&error=cannot_self_ban");
            return;
        }

        boolean makeActive = target.isDeleted(); // đang Inactive -> Mở khóa; đang Active -> Khóa
        employeeDAO.setActive(id, makeActive);
        response.sendRedirect(request.getContextPath() + "/employee?action=view&id=" + id);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** 10 ký tự ngẫu nhiên từ bộ ký tự đã loại bỏ các ký tự dễ nhầm (0/O, 1/l/I). */
    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private User buildUserFromRequest(HttpServletRequest request, User u) {
        u.setLastName(emptyToNull(request.getParameter("lastName")));
        u.setMiddleName(emptyToNull(request.getParameter("middleName")));
        u.setFirstName(emptyToNull(request.getParameter("firstName")));
        u.setGender(request.getParameter("gender"));
        u.setDateOfBirth(parseDateOrNull(request.getParameter("dateOfBirth")));
        u.setCitizenId(emptyToNull(request.getParameter("citizenId")));
        u.setPhone(emptyToNull(request.getParameter("phone")));
        u.setPersonalEmail(emptyToNull(request.getParameter("personalEmail")));
        u.setHireDate(parseDateOrNull(request.getParameter("hireDate")));

        Integer roleId = parseIntOrNull(request.getParameter("roleId"));
        if (roleId != null) {
            u.setRoleId(roleId);
        }
        Integer departmentId = parseIntOrNull(request.getParameter("departmentId"));
        if (departmentId != null) {
            u.setDepartmentId(departmentId);
        }

        Integer districtId = parseIntOrNull(request.getParameter("districtId"));
        String addressDetail = emptyToNull(request.getParameter("addressDetail"));
        if (districtId != null) {
            Address address = new Address();
            address.setStreetAndLocalName(addressDetail != null ? addressDetail : "");
            address.setDistrictId(districtId);
            u.setAddress(address);
        }
        return u;
    }

    /**
     * BR-28 (Phòng ban + Vai trò bắt buộc), BR-29 (ngày sinh hợp lệ trong
     * quá khứ), BR-30 (giới tính hợp lệ), BR-09 (định dạng SĐT), BR-31 (email
     * cá nhân bắt buộc + hợp lệ -- là kênh duy nhất để gửi tài khoản/mật khẩu
     * vì email công ty tự cấp chưa có hộp thư thật). Các trường bắt buộc
     * khác của users (họ, tên, CCCD, ngày sinh, ngày vào làm) đều NOT NULL
     * trong DB nên phải chặn ở đây trước khi tới tầng DAO.
     */
    private boolean isValidCommonFields(User u) {
        if (isBlank(u.getLastName()) || isBlank(u.getFirstName()) || isBlank(u.getCitizenId())) {
            return false;
        }
        if (u.getRoleId() <= 0 || u.getDepartmentId() <= 0) {
            return false;
        }
        if (!"Nam".equals(u.getGender()) && !"Nữ".equals(u.getGender()) && !"Khác".equals(u.getGender())) {
            return false;
        }
        if (u.getDateOfBirth() == null || !u.getDateOfBirth().toLocalDate().isBefore(LocalDate.now())) {
            return false;
        }
        if (u.getHireDate() == null) {
            return false;
        }
        if (isBlank(u.getPersonalEmail()) || !isValidEmail(u.getPersonalEmail())) {
            return false;
        }
        return isValidPhone(u.getPhone());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** BR-09: chấp nhận cả số di động lẫn số bàn Việt Nam. */
    private boolean isValidPhone(String phone) {
        if (phone == null) {
            return false;
        }
        return phone.replaceAll("[\\s.-]", "").matches("^(0|\\+84)[0-9]{9,10}$");
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
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

    private int parseIntOrDefault(String value, int defaultValue) {
        Integer parsed = parseIntOrNull(value);
        return parsed != null ? parsed : defaultValue;
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

    private String emptyToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }
}
