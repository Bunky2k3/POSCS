package poscs.controller;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.AccessControl;
import poscs.common.EmailUtil;
import poscs.common.Logs;
import poscs.common.TextRules;
import poscs.dao.AddressDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Address;
import poscs.model.Province;
import poscs.model.ProvinceAssignment;
import poscs.model.Role;
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

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeController.class);

    // "Email công ty" (<username>@postef.com.vn) đã bỏ (V36) -- chuỗi hệ
    // thống tự bịa, chưa từng gắn hộp thư thật, và một email thường phải đi
    // kèm bước ĐĂNG KÝ hộp thư thật chứ không phải chỉ ghép chuỗi. Đăng nhập
    // giờ chỉ bằng username; xem PERMISSIONS.md.

    /** role_name của Admin trong bảng roles -- xem AccessControl.ROLE_ADMIN. */
    private static final String ADMIN_ROLE_NAME = "Admin";

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
        // Địa bàn trực tiếp cầm, và -- nếu người này là cấp trên -- địa bàn
        // suy ra từ cấp dưới. Hai thứ khác nhau nên hiện tách bạch: tầng lá
        // cầm tỉnh, quản lý vùng KHÔNG nhập tay mà bao phủ theo cấp dưới.
        request.setAttribute("assignedProvinces", employeeDAO.findProvincesOf(id));
        request.setAttribute("managedProvinces", employeeDAO.findProvincesManagedBy(id));
        request.getRequestDispatcher(DETAIL_VIEW).forward(request, response);
    }

    private static final DateTimeFormatter DATE_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private String formatDate(Date date) {
        return date != null ? date.toLocalDate().format(DATE_DISPLAY_FORMAT) : null;
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("allProvinceAssignments", branchProvinceAssignments(null));
        request.setAttribute("roleList", assignableRoles(null));
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
        request.setAttribute("allProvinceAssignments", branchProvinceAssignments(id));
        request.setAttribute("roleList", assignableRoles(employee.getRoleId()));
        request.setAttribute("departmentList", employeeDAO.findAllDepartments());
        request.setAttribute("provinceList", addressDAO.findAllProvinces());
        request.getRequestDispatcher(UPDATE_VIEW).forward(request, response);
    }

    /**
     * Danh sách tỉnh cho ô "Địa bàn phụ trách": chỉ 18 tỉnh địa bàn chi nhánh
     * (AddressDAO.findBranchProvinces), KHÔNG phải cả 34 tỉnh toàn quốc --
     * đây là địa bàn KINH DOANH (giống dropdown tỉnh của khách hàng/hợp
     * đồng), khác hẳn ô "Địa chỉ" bên dưới (hộ khẩu cá nhân, dùng
     * addressDAO.findAllProvinces() vì nhân viên chi nhánh miền Bắc vẫn có
     * thể quê ở nơi khác).
     *
     * <p>Vẫn giữ lại tỉnh nằm NGOÀI địa bàn nếu nhân viên đang sửa
     * ({@code editingEmployeeId}) đã cầm nó từ trước (dữ liệu cũ/nhập nhầm)
     * -- cùng lý do {@code AddressDAO.findBranchProvincesIncluding} tồn tại:
     * ẩn nó đi thì mở form sửa lên ô đó thiếu tỉnh, bấm lưu (mà không tick
     * lại) là xoá mất tỉnh ngoài địa bàn đó của người này.
     */
    private List<ProvinceAssignment> branchProvinceAssignments(Integer editingEmployeeId) {
        Set<Integer> branchIds = new HashSet<>();
        for (Province p : addressDAO.findBranchProvinces()) {
            branchIds.add(p.getProvinceId());
        }
        List<ProvinceAssignment> result = new ArrayList<>();
        for (ProvinceAssignment pa : employeeDAO.findAllProvincesWithHolder()) {
            boolean inBranch = branchIds.contains(pa.getProvinceId());
            boolean heldByEditingEmployee = editingEmployeeId != null
                    && pa.getHolderUserId() != null && pa.getHolderUserId().equals(editingEmployeeId);
            if (inBranch || heldByEditingEmployee) {
                result.add(pa);
            }
        }
        return result;
    }

    /**
     * Danh sách vai trò cho dropdown "Vai trò" ở form thêm/sửa: bỏ "Admin"
     * ra khỏi lựa chọn -- tài khoản Admin phải tạo tay (qua CSDL), không qua
     * form nhân viên thường, để tránh bấm nhầm cấp nhầm quyền cao nhất qua
     * đúng luồng mật khẩu tạm gửi email như một Sales/Kỹ thuật bình thường.
     *
     * <p>NGOẠI LỆ: nếu nhân viên đang sửa ({@code editingRoleId}) hiện đã là
     * Admin, vẫn giữ "Admin" trong danh sách (đã chọn sẵn) -- ẩn đi thì ô
     * chọn hiện trống, bấm lưu (mà không tick lại, vì còn tick được đâu) là
     * hạ cấp mất một Admin chỉ vì Admin đó (hoặc Admin khác) sửa trường khác.
     * {@link EmployeeDAO#findAllRoles} vẫn dùng nguyên cho ô lọc ở danh sách
     * -- lọc "xem ai đang là Admin" là việc hợp lệ, khác với việc CẤP Admin.
     */
    private List<Role> assignableRoles(Integer editingRoleId) {
        List<Role> result = new ArrayList<>();
        for (Role r : employeeDAO.findAllRoles()) {
            if (!ADMIN_ROLE_NAME.equals(r.getRoleName())
                    || (editingRoleId != null && r.getRoleId() == editingRoleId)) {
                result.add(r);
            }
        }
        return result;
    }

    /** true nếu roleId này chính là vai Admin -- dùng để chặn cấp/giữ Admin qua form, xem {@link #assignableRoles}. */
    private boolean isAdminRoleId(int roleId) {
        for (Role r : employeeDAO.findAllRoles()) {
            if (ADMIN_ROLE_NAME.equals(r.getRoleName())) {
                return r.getRoleId() == roleId;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // POST actions
    // ------------------------------------------------------------------

    /**
     * UC-26 Create Employee: chỉ tạo hồ sơ + tự sinh username/mật khẩu tạm
     * (hash ngay, không giữ lại bản rõ) -- KHÔNG gửi email ở bước này. Việc
     * gửi thông tin tài khoản cho nhân viên là hành động Admin chủ động bấm
     * riêng ở trang chi tiết (xem handleSendAccount), để Admin có thể rà lại
     * thông tin trước khi gửi và gửi lại được nếu cần.
     */
    private void handleCreate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User u = buildUserFromRequest(request, new User());

        if (!isValidCommonFields(u)) {
            response.sendRedirect(request.getContextPath() + "/employee?action=new&error=invalid");
            return;
        }
        // Dropdown đã lọc sẵn (assignableRoles), nhưng đó chỉ là tiện cho
        // người dùng -- một request tự dựng gửi thẳng roleId nào cũng được,
        // nên phải chặn lại ở đây trước khi ghi. Nhân viên MỚI không bao giờ
        // được tạo thẳng thành Admin qua form này.
        if (isAdminRoleId(u.getRoleId())) {
            response.sendRedirect(request.getContextPath() + "/employee?action=new&error=invalid");
            return;
        }
        // Từ V35: phone/citizenId có thể null (chưa nhập) -- bỏ qua kiểm trùng
        // trong trường hợp đó, gọi existsByColumn(null) chỉ tốn 1 query vô ích
        // (SQL "cột = NULL" không khớp gì nên luôn trả false, không sai, chỉ
        // thừa) và dễ đọc nhầm là đang kiểm tra thật.
        if (u.getPhone() != null && employeeDAO.existsByPhone(u.getPhone(), null)) {
            response.sendRedirect(request.getContextPath() + "/employee?action=new&error=duplicate_phone");
            return;
        }
        if (u.getCitizenId() != null && employeeDAO.existsByCitizenId(u.getCitizenId(), null)) {
            response.sendRedirect(request.getContextPath() + "/employee?action=new&error=duplicate_citizen");
            return;
        }

        String username = employeeDAO.generateUniqueUsername(u.getLastName(), u.getMiddleName(), u.getFirstName());
        u.setUsername(username);
        u.setPasswordHash(BCrypt.hashpw(generateTempPassword(), BCrypt.gensalt()));

        int newId = employeeDAO.insert(u);
        if (newId > 0) {
            employeeDAO.replaceProvincesOf(newId, parseIntList(request.getParameterValues("provinceIds")));
        }
        if (newId <= 0) {
            LOG.warn("Tao nhan vien that bai (actor={}, username={})", Logs.actor(request), u.getUsername());
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
     * cũ. Gửi tới EMAIL CÁ NHÂN -- kênh THẬT duy nhất nhân viên có (từ V36,
     * không còn "email công ty" giả để mà cân nhắc gửi tới đó).
     */
    private void handleSendAccount(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        User employee = id != null ? employeeDAO.findById(id) : null;
        if (employee == null) {
            response.sendRedirect(request.getContextPath() + "/employee?error=notfound");
            return;
        }

        // personal_email cho phép NULL trong CSDL, nên phải chặn ở đây: gửi tới
        // địa chỉ rỗng chắc chắn thất bại, mà mật khẩu thì đã bị đổi mất rồi.
        if (employee.getPersonalEmail() == null || employee.getPersonalEmail().trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/employee?action=view&id=" + id + "&error=no_personal_email");
            return;
        }

        // THỨ TỰ QUAN TRỌNG: gửi mail TRƯỚC, ghi hash sau.
        //
        // Làm ngược lại (đổi mật khẩu rồi mới gửi) thì khi SMTP lỗi hoặc email
        // cá nhân trống, mật khẩu đang dùng được của nhân viên đã bị ghi đè
        // mất, còn mật khẩu mới thì không tồn tại ở đâu cả (không lưu bản rõ) --
        // tài khoản đang chạy bình thường bỗng nhiên không đăng nhập được, mà
        // dấu hiệu duy nhất là một tham số warning trên URL của Admin.
        String tempPassword = generateTempPassword();
        boolean mailSent = EmailUtil.sendNewAccountEmail(employee.getPersonalEmail(), employee.getFullName(),
                employee.getUsername(), tempPassword);
        if (!mailSent) {
            // Chưa đụng tới mật khẩu cũ -- nhân viên vẫn đăng nhập được như trước.
            response.sendRedirect(request.getContextPath() + "/employee?action=view&id=" + id + "&error=mail_failed");
            return;
        }

        // Mail đã đi. Nếu bước ghi hash lỗi, mật khẩu trong mail sẽ không dùng
        // được -- vẫn tốt hơn trường hợp cũ (mất mật khẩu cũ mà không ai biết
        // mật khẩu mới), và Admin bấm lại là cấp mã mới.
        boolean updated = employeeDAO.updatePasswordHash(id, BCrypt.hashpw(tempPassword, BCrypt.gensalt()));
        if (!updated) {
            LOG.warn("Gui email tai khoan that bai (actor={}, userId={})", Logs.actor(request), id);
            response.sendRedirect(request.getContextPath() + "/employee?action=view&id=" + id + "&error=send_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/employee?action=view&id=" + id + "&sent=1");
    }

    private void handleUpdate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = parseIntOrNull(request.getParameter("userId"));
        User existing = id != null ? employeeDAO.findById(id) : null;
        if (existing == null) {
            response.sendRedirect(request.getContextPath() + "/employee?error=notfound");
            return;
        }

        User u = buildUserFromRequest(request, new User());
        u.setUserId(id);
        // Không có form nào (thêm/sửa) còn ô "Cấp trên" -- giữ NGUYÊN giá trị
        // hiện có, đừng để chỗ này lặng lẽ xoá mất cấp trên đã gán chỉ vì
        // request không gửi kèm managerId. Muốn đổi cấp trên hiện phải sửa
        // trực tiếp trong CSDL (xem PERMISSIONS.md).
        u.setManagerId(existing.getManagerId());

        if (!isValidCommonFields(u)) {
            response.sendRedirect(request.getContextPath() + "/employee?action=edit&id=" + id + "&error=invalid");
            return;
        }
        // Chỉ CHẶN khi đây là một lần THĂNG lên Admin (trước đó không phải,
        // request lại gửi roleId Admin) -- một Admin ĐÃ LÀ Admin từ trước
        // (giữ nguyên qua dropdown, xem assignableRoles) vẫn phải lưu được
        // bình thường, không thể tự khoá chính diện họ vào một vòng lặp.
        if (isAdminRoleId(u.getRoleId()) && !isAdminRoleId(existing.getRoleId())) {
            response.sendRedirect(request.getContextPath() + "/employee?action=edit&id=" + id + "&error=invalid");
            return;
        }
        if (u.getPhone() != null && employeeDAO.existsByPhone(u.getPhone(), id)) {
            response.sendRedirect(request.getContextPath() + "/employee?action=edit&id=" + id + "&error=duplicate_phone");
            return;
        }
        if (u.getCitizenId() != null && employeeDAO.existsByCitizenId(u.getCitizenId(), id)) {
            response.sendRedirect(request.getContextPath() + "/employee?action=edit&id=" + id + "&error=duplicate_citizen");
            return;
        }

        boolean ok = employeeDAO.update(u);
        if (ok) {
            // Địa bàn lưu ở bảng riêng nên phải ghi tách khỏi hồ sơ nhân viên.
            employeeDAO.replaceProvincesOf(id, parseIntList(request.getParameterValues("provinceIds")));
        }
        if (!ok) {
            LOG.warn("Cap nhat nhan vien that bai (actor={}, userId={})", Logs.actor(request), id);
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
        // Từ V35: Admin không còn bắt buộc chọn giới tính lúc tạo/sửa -- ô
        // chọn có option rỗng ("-- Để nhân viên tự chọn --"), request trả về
        // chuỗi rỗng khi Admin để vậy, phải quy về null như các trường tự do
        // khác thay vì lưu chuỗi rỗng (sẽ trượt qua check isValidCommonFields
        // dưới vì "" không khớp Nam/Nữ/Khác NHƯNG cũng không null).
        u.setGender(emptyToNull(request.getParameter("gender")));
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
        // KHÔNG đọc managerId từ request ở đây -- form thêm/sửa không còn ô
        // "Cấp trên" (bỏ theo yêu cầu, xem PERMISSIONS.md). Nhân viên mới mặc
        // định chưa có cấp trên (User.managerId để nguyên null); handleUpdate
        // tự gán lại giá trị hiện có ngay sau khi gọi hàm này, đừng lặp ở đây.

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
     * BR-28 (Phòng ban + Vai trò bắt buộc), BR-29 (ngày sinh hợp lệ trong quá
     * khứ, NẾU có nhập), BR-30 (giới tính hợp lệ, NẾU có chọn), BR-09 (định
     * dạng SĐT, NẾU có nhập), BR-11 (email cá nhân bắt buộc + hợp lệ -- là
     * kênh duy nhất để gửi tài khoản/mật khẩu, từ V36 không còn "email công
     * ty" nào để mà cân nhắc thay thế).
     *
     * <p>Từ V35: gender/dateOfBirth/citizenId/phone KHÔNG còn bắt buộc ở đây
     * -- đây là dữ liệu cá nhân, Admin có thể để trống lúc tạo/sửa, nhân viên
     * tự bổ sung ở lần đăng nhập đầu (AuthenticationController.
     * handleUpdateProfile áp đúng 4 quy tắc BR-29/BR-30/BR-09 này, ép qua
     * AuthenticationFilter). Chỉ khi CÓ nhập thì mới kiểm đúng định dạng --
     * để trống không còn là lỗi, nhập sai định dạng vẫn là lỗi.
     */
    private boolean isValidCommonFields(User u) {
        if (isBlank(u.getLastName()) || isBlank(u.getFirstName())) {
            return false;
        }
        if (!TextRules.isSafeFreeText(u.getLastName())
                || !TextRules.isSafeFreeText(u.getMiddleName())
                || !TextRules.isSafeFreeText(u.getFirstName())
                || (u.getAddress() != null && !TextRules.isSafeFreeText(u.getAddress().getStreetAndLocalName()))) {
            return false;
        }
        if (u.getRoleId() <= 0 || u.getDepartmentId() <= 0) {
            return false;
        }
        if (u.getGender() != null
                && !"Nam".equals(u.getGender()) && !"Nữ".equals(u.getGender()) && !"Khác".equals(u.getGender())) {
            return false;
        }
        if (u.getDateOfBirth() != null && !u.getDateOfBirth().toLocalDate().isBefore(LocalDate.now())) {
            return false;
        }
        if (u.getHireDate() == null) {
            return false;
        }
        if (isBlank(u.getPersonalEmail()) || !isValidEmail(u.getPersonalEmail())) {
            return false;
        }
        return u.getPhone() == null || isValidPhone(u.getPhone());
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

    /** Danh sách id từ ô chọn nhiều; bỏ qua giá trị rỗng/không phải số. */
    private java.util.List<Integer> parseIntList(String[] values) {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            Integer parsed = parseIntOrNull(value);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
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
