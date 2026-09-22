package poscs.controller;

import java.lang.reflect.Field;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.common.EmailUtil;
import poscs.dao.AddressDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Province;
import poscs.model.ProvinceAssignment;
import poscs.model.Role;
import poscs.model.User;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test tầng Controller cho EmployeeController -- khác Customer/Contract/
 * Ticket ở chỗ quyền được gate DUY NHẤT 1 lần ở đầu doGet/doPost (chỉ Admin
 * được đụng vào Employee, không có tier View only), nên test luôn gọi thẳng
 * doGet/doPost thay vì gọi riêng từng handler. Tập trung vào BR-28/BR-29/
 * BR-30 (các trường bắt buộc khi tạo/sửa nhân viên), chặn trùng SĐT/CCCD, và
 * BR-25/BR-26 (không tự khoá chính tài khoản Admin đang đăng nhập).
 * JUnit 4 + Mockito, xem CustomerControllerTest.
 */
public class EmployeeControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private EmployeeController controller;
    private EmployeeDAO employeeDAO;
    private AddressDAO addressDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @Before
    public void setUp() throws Exception {
        controller = new EmployeeController();

        employeeDAO = mock(EmployeeDAO.class);
        addressDAO = mock(AddressDAO.class);

        setField(controller, "employeeDAO", employeeDAO);
        setField(controller, "addressDAO", addressDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);

        loginAs("Admin", 99); // chỉ Admin mới có quyền trên EMPLOYEE, xem PERMISSIONS.md
    }

    private void loginAs(String roleName, int userId) {
        User user = new User();
        user.setUserId(userId);
        user.setRole(new Role(1, roleName));
        when(session.getAttribute("currentUser")).thenReturn(user);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private void stubValidCreateFields() {
        when(request.getParameter("lastName")).thenReturn("Nguyễn");
        when(request.getParameter("firstName")).thenReturn("An");
        when(request.getParameter("citizenId")).thenReturn("001201012345");
        when(request.getParameter("gender")).thenReturn("Nam");
        when(request.getParameter("dateOfBirth")).thenReturn(LocalDate.now().minusYears(25).toString());
        when(request.getParameter("hireDate")).thenReturn(LocalDate.now().toString());
        when(request.getParameter("personalEmail")).thenReturn("an.nguyen@example.com");
        when(request.getParameter("phone")).thenReturn("0912345678");
        when(request.getParameter("roleId")).thenReturn("2");
        when(request.getParameter("departmentId")).thenReturn("1");
    }

    // ------------------------------------------------------------------
    // Phân quyền: chỉ Admin, gate 1 lần đầu doGet/doPost
    // ------------------------------------------------------------------

    @Test
    public void doGet_withoutAdminRole_returns403AndNeverTouchesDao() throws Exception {
        loginAs("Sales", 1);
        when(request.getParameter("action")).thenReturn("list");

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(employeeDAO, never()).findAll(anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    public void doPost_withoutAdminRole_returns403AndNeverTouchesDao() throws Exception {
        loginAs("Sales", 1);
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(employeeDAO, never()).insert(any());
    }

    // ------------------------------------------------------------------
    // POST ?action=create (BR-28, BR-29, BR-30)
    // ------------------------------------------------------------------

    @Test
    public void create_missingRequiredField_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("lastName")).thenReturn(null);

        controller.doPost(request, response);

        verify(employeeDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=new&error=invalid");
    }

    @Test
    public void create_futureBirthDate_violatesBr29_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("dateOfBirth")).thenReturn(LocalDate.now().plusDays(1).toString());

        controller.doPost(request, response);

        verify(employeeDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=new&error=invalid");
    }

    @Test
    public void create_duplicatePhone_redirectsWithDuplicatePhoneError() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(employeeDAO.existsByPhone("0912345678", null)).thenReturn(true);

        controller.doPost(request, response);

        verify(employeeDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=new&error=duplicate_phone");
    }

    @Test
    public void create_duplicateCitizenId_redirectsWithDuplicateCitizenError() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(employeeDAO.existsByCitizenId("001201012345", null)).thenReturn(true);

        controller.doPost(request, response);

        verify(employeeDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=new&error=duplicate_citizen");
    }

    /**
     * Từ V35: gender/dateOfBirth/citizenId/phone không còn bắt buộc lúc Admin
     * tạo tài khoản -- nhân viên tự bổ sung ở lần đăng nhập đầu
     * (AuthenticationController.handleUpdateProfile, ép qua
     * AuthenticationFilter). Để trống cả 4 trường này vẫn phải tạo được.
     */
    @Test
    public void create_withoutOptionalPersonalFields_stillSucceeds() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("gender")).thenReturn(null);
        when(request.getParameter("dateOfBirth")).thenReturn(null);
        when(request.getParameter("citizenId")).thenReturn(null);
        when(request.getParameter("phone")).thenReturn(null);
        when(employeeDAO.generateUniqueUsername("Nguyễn", null, "An")).thenReturn("annd");
        when(employeeDAO.insert(any(User.class))).thenReturn(15);

        controller.doPost(request, response);

        // Không nên tự đi kiểm trùng SĐT/CCCD khi người dùng chưa nhập gì --
        // existsByColumn(null) chỉ tốn 1 query vô ích, không sai nhưng thừa.
        verify(employeeDAO, never()).existsByPhone(any(), any());
        verify(employeeDAO, never()).existsByCitizenId(any(), any());
        verify(employeeDAO).insert(argThat((User u) ->
                u.getGender() == null && u.getDateOfBirth() == null
                        && u.getCitizenId() == null && u.getPhone() == null));
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=view&id=15");
    }

    @Test
    public void create_invalidGenderValue_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("gender")).thenReturn("khong-hop-le");

        controller.doPost(request, response);

        verify(employeeDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=new&error=invalid");
    }

    @Test
    public void create_malformedPhoneWhenProvided_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("phone")).thenReturn("abc");

        controller.doPost(request, response);

        verify(employeeDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=new&error=invalid");
    }

    @Test
    public void create_validFields_generatesAccountAndRedirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(employeeDAO.generateUniqueUsername("Nguyễn", null, "An")).thenReturn("annd");
        when(employeeDAO.insert(any(User.class))).thenReturn(15);

        controller.doPost(request, response);

        verify(employeeDAO).insert(argThat((User u) ->
                "annd".equals(u.getUsername())
                        && u.getPasswordHash() != null));
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=view&id=15");
    }

    // ------------------------------------------------------------------
    // POST ?action=update
    // ------------------------------------------------------------------

    @Test
    public void update_employeeNotFound_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("userId")).thenReturn("15");
        when(employeeDAO.findById(15)).thenReturn(null);
        stubValidCreateFields();

        controller.doPost(request, response);

        verify(employeeDAO, never()).update(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?error=notfound");
    }

    @Test
    public void update_validFields_updatesAndRedirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("userId")).thenReturn("15");
        when(employeeDAO.findById(15)).thenReturn(new User());
        stubValidCreateFields();
        when(employeeDAO.update(any(User.class))).thenReturn(true);

        controller.doPost(request, response);

        verify(employeeDAO).update(argThat((User u) -> u.getUserId() == 15));
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=view&id=15");
    }

    /**
     * Form sửa không còn ô "Cấp trên" (đã bỏ) nên request không gửi kèm
     * managerId nữa -- phải GIỮ NGUYÊN manager_id hiện có của người này,
     * không được lặng lẽ xoá mất chỉ vì request không có tham số đó. Cùng
     * lớp lỗi với việc ẩn tỉnh đã có người cầm khỏi ô chọn rồi bấm lưu là mất
     * dữ liệu -- chỉ khác trường.
     */
    @Test
    public void update_managerIdNoLongerSubmitted_keepsExistingManagerUnchanged() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("userId")).thenReturn("15");
        User existing = new User();
        existing.setUserId(15);
        existing.setManagerId(7);
        when(employeeDAO.findById(15)).thenReturn(existing);
        stubValidCreateFields();
        when(request.getParameter("managerId")).thenReturn(null); // form không còn ô này
        when(employeeDAO.update(any(User.class))).thenReturn(true);

        controller.doPost(request, response);

        verify(employeeDAO).update(argThat((User u) -> Integer.valueOf(7).equals(u.getManagerId())));
    }

    // ------------------------------------------------------------------
    // POST ?action=toggleStatus (BR-25/BR-26 không tự khoá chính mình)
    // ------------------------------------------------------------------

    @Test
    public void toggleStatus_cannotSelfBan() throws Exception {
        when(request.getParameter("action")).thenReturn("toggleStatus");
        when(request.getParameter("id")).thenReturn("99");
        User self = new User();
        self.setUserId(99);
        when(employeeDAO.findById(99)).thenReturn(self);

        controller.doPost(request, response);

        verify(employeeDAO, never()).setActive(anyInt(), anyBoolean());
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=view&id=99&error=cannot_self_ban");
    }

    @Test
    public void toggleStatus_activeEmployee_getsBanned() throws Exception {
        when(request.getParameter("action")).thenReturn("toggleStatus");
        when(request.getParameter("id")).thenReturn("50");
        User target = new User();
        target.setUserId(50);
        target.setDeleted(false); // đang Active
        when(employeeDAO.findById(50)).thenReturn(target);

        controller.doPost(request, response);

        verify(employeeDAO).setActive(50, false); // Active -> Khoá (makeActive=false)
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=view&id=50");
    }

    @Test
    public void toggleStatus_inactiveEmployee_getsUnbanned() throws Exception {
        when(request.getParameter("action")).thenReturn("toggleStatus");
        when(request.getParameter("id")).thenReturn("50");
        User target = new User();
        target.setUserId(50);
        target.setDeleted(true); // đang Inactive
        when(employeeDAO.findById(50)).thenReturn(target);

        controller.doPost(request, response);

        verify(employeeDAO).setActive(50, true); // Inactive -> Mở khoá (makeActive=true)
    }

    // ------------------------------------------------------------------
    // POST ?action=sendAccount
    // ------------------------------------------------------------------

    @Test
    public void sendAccount_employeeNotFound_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("action")).thenReturn("sendAccount");
        when(request.getParameter("id")).thenReturn("15");
        when(employeeDAO.findById(15)).thenReturn(null);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/employee?error=notfound");
    }

    /** Nhân viên đủ điều kiện nhận mail: có email cá nhân thật. */
    private User employeeWithPersonalEmail() {
        User employee = new User();
        employee.setUserId(15);
        employee.setLastName("Nguyễn");
        employee.setFirstName("An");
        employee.setUsername("annguyen");
        employee.setPersonalEmail("an.nguyen@gmail.com");
        return employee;
    }

    @Test
    public void sendAccount_mailFails_leavesOldPasswordUntouched() throws Exception {
        // Điểm mấu chốt: gửi mail TRƯỚC, ghi hash sau. Nếu SMTP lỗi mà vẫn đổi
        // mật khẩu thì nhân viên mất mật khẩu đang dùng được, còn mật khẩu mới
        // không tồn tại ở đâu cả -- tài khoản đang chạy bỗng nhiên chết.
        when(request.getParameter("action")).thenReturn("sendAccount");
        when(request.getParameter("id")).thenReturn("15");
        when(employeeDAO.findById(15)).thenReturn(employeeWithPersonalEmail());

        try (org.mockito.MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            emailUtil.when(() -> EmailUtil.sendNewAccountEmail(any(), any(), any(), any())).thenReturn(false);

            controller.doPost(request, response);

            verify(employeeDAO, never()).updatePasswordHash(anyInt(), anyString());
            verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=view&id=15&error=mail_failed");
        }
    }

    @Test
    public void sendAccount_noPersonalEmail_refusesBeforeTouchingPassword() throws Exception {
        // personal_email cho phép NULL. Gửi tới địa chỉ rỗng chắc chắn hỏng, nên
        // phải từ chối TRƯỚC khi đụng tới mật khẩu.
        when(request.getParameter("action")).thenReturn("sendAccount");
        when(request.getParameter("id")).thenReturn("15");
        User employee = employeeWithPersonalEmail();
        employee.setPersonalEmail(null);
        when(employeeDAO.findById(15)).thenReturn(employee);

        try (org.mockito.MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            controller.doPost(request, response);

            emailUtil.verifyNoInteractions();
            verify(employeeDAO, never()).updatePasswordHash(anyInt(), anyString());
            verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=view&id=15&error=no_personal_email");
        }
    }

    @Test
    public void sendAccount_blankPersonalEmail_refusesBeforeTouchingPassword() throws Exception {
        when(request.getParameter("action")).thenReturn("sendAccount");
        when(request.getParameter("id")).thenReturn("15");
        User employee = employeeWithPersonalEmail();
        employee.setPersonalEmail("   ");
        when(employeeDAO.findById(15)).thenReturn(employee);

        controller.doPost(request, response);

        verify(employeeDAO, never()).updatePasswordHash(anyInt(), anyString());
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=view&id=15&error=no_personal_email");
    }

    @Test
    public void sendAccount_mailSucceedsThenPasswordWriteFails_redirectsWithSendFailedError() throws Exception {
        when(request.getParameter("action")).thenReturn("sendAccount");
        when(request.getParameter("id")).thenReturn("15");
        when(employeeDAO.findById(15)).thenReturn(employeeWithPersonalEmail());
        when(employeeDAO.updatePasswordHash(eq(15), anyString())).thenReturn(false);

        try (org.mockito.MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            emailUtil.when(() -> EmailUtil.sendNewAccountEmail(any(), any(), any(), any())).thenReturn(true);

            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=view&id=15&error=send_failed");
        }
    }

    @Test
    public void sendAccount_mailSucceeds_sendsToPersonalEmailThenStoresHash() throws Exception {
        when(request.getParameter("action")).thenReturn("sendAccount");
        when(request.getParameter("id")).thenReturn("15");
        when(employeeDAO.findById(15)).thenReturn(employeeWithPersonalEmail());
        when(employeeDAO.updatePasswordHash(eq(15), anyString())).thenReturn(true);

        try (org.mockito.MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            emailUtil.when(() -> EmailUtil.sendNewAccountEmail(any(), any(), any(), any())).thenReturn(true);

            controller.doPost(request, response);

            // Gửi tới email CÁ NHÂN -- kênh THẬT duy nhất, không còn "email công ty" giả nào nữa.
            emailUtil.verify(() -> EmailUtil.sendNewAccountEmail(
                    eq("an.nguyen@gmail.com"), any(), eq("annguyen"), anyString()));
            verify(employeeDAO).updatePasswordHash(eq(15), anyString());
            verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=view&id=15&sent=1");
        }
    }

    // ------------------------------------------------------------------
    // GET ?action=new / ?action=edit -- ô "Địa bàn phụ trách" (BR: 18 tỉnh
    // địa bàn chi nhánh, không phải cả 34 tỉnh toàn quốc)
    // ------------------------------------------------------------------

    /**
     * Ô "Địa bàn phụ trách" phải lấy theo AddressDAO.findBranchProvinces()
     * (18 tỉnh chi nhánh), KHÔNG phải toàn bộ bảng provinces (34 tỉnh toàn
     * quốc) -- khác ô "Địa chỉ" cá nhân bên dưới nó, vốn dùng findAllProvinces().
     * Nhầm hai cái này thì Admin thấy mời chọn cả tỉnh công ty không hoạt động.
     */
    @Test
    public void createForm_provinceList_onlyBranchProvincesNotWholeCountry() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/admin/addEmployee.jsp")).thenReturn(dispatcher);
        when(request.getParameter("action")).thenReturn("new");
        when(addressDAO.findBranchProvinces()).thenReturn(List.of(new Province(1, "Thành phố Hà Nội")));
        when(employeeDAO.findAllProvincesWithHolder()).thenReturn(List.of(
                new ProvinceAssignment(1, "Thành phố Hà Nội", null, null),
                new ProvinceAssignment(2, "Thành phố Hồ Chí Minh", null, null))); // ngoài địa bàn chi nhánh

        controller.doGet(request, response);

        verify(request).setAttribute(eq("allProvinceAssignments"), argThat((List<?> list) ->
                list.size() == 1 && ((ProvinceAssignment) list.get(0)).getProvinceId() == 1));
    }

    /**
     * Tỉnh ngoài địa bàn chi nhánh nhưng ĐANG do chính nhân viên này cầm (dữ
     * liệu cũ/nhập nhầm) vẫn phải hiện trong ô chọn -- ẩn đi thì mở form sửa
     * lên là ô đó thiếu tỉnh, bấm lưu (không tick lại) là xoá mất tỉnh đó của
     * người này. Tỉnh ngoài địa bàn do NGƯỜI KHÁC cầm thì vẫn phải bị loại.
     */
    @Test
    public void editForm_provinceList_keepsEmployeesOwnProvinceOutsideBranch() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/admin/updateEmployee.jsp")).thenReturn(dispatcher);
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("15");
        User employee = new User();
        employee.setUserId(15);
        when(employeeDAO.findById(15)).thenReturn(employee);
        when(addressDAO.findBranchProvinces()).thenReturn(List.of(new Province(1, "Thành phố Hà Nội")));
        when(employeeDAO.findAllProvincesWithHolder()).thenReturn(List.of(
                new ProvinceAssignment(1, "Thành phố Hà Nội", null, null),
                new ProvinceAssignment(2, "Thành phố Hồ Chí Minh", 15, "Nguyễn Văn An"), // NV15 đang cầm, ngoài địa bàn
                new ProvinceAssignment(3, "Tỉnh Đồng Nai", 99, "Người khác"))); // người khác cầm, ngoài địa bàn

        controller.doGet(request, response);

        verify(request).setAttribute(eq("allProvinceAssignments"), argThat((List<?> raw) -> {
            @SuppressWarnings("unchecked")
            List<ProvinceAssignment> list = (List<ProvinceAssignment>) raw;
            return list.size() == 2
                    && list.stream().anyMatch(p -> p.getProvinceId() == 1)
                    && list.stream().anyMatch(p -> p.getProvinceId() == 2)
                    && list.stream().noneMatch(p -> p.getProvinceId() == 3);
        }));
    }

    // ------------------------------------------------------------------
    // Vai "Admin" không được chọn qua form thêm/sửa (tạo tay qua CSDL)
    // ------------------------------------------------------------------

    private static final Role ADMIN_ROLE = new Role(1, "Admin");
    private static final Role SALES_ROLE = new Role(2, "Sales");

    @Test
    public void createForm_roleList_excludesAdmin() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/admin/addEmployee.jsp")).thenReturn(dispatcher);
        when(request.getParameter("action")).thenReturn("new");
        when(employeeDAO.findAllRoles()).thenReturn(List.of(ADMIN_ROLE, SALES_ROLE));

        controller.doGet(request, response);

        verify(request).setAttribute(eq("roleList"), argThat((List<?> raw) -> {
            @SuppressWarnings("unchecked")
            List<Role> list = (List<Role>) raw;
            return list.size() == 1 && "Sales".equals(list.get(0).getRoleName());
        }));
    }

    /** Nhân viên đang sửa ĐÃ LÀ Admin -- ô chọn phải giữ "Admin" lại, không được biến mất. */
    @Test
    public void editForm_roleList_keepsAdminWhenEmployeeAlreadyIsAdmin() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/admin/updateEmployee.jsp")).thenReturn(dispatcher);
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("15");
        User employee = new User();
        employee.setUserId(15);
        employee.setRoleId(1); // đang là Admin
        when(employeeDAO.findById(15)).thenReturn(employee);
        when(employeeDAO.findAllRoles()).thenReturn(List.of(ADMIN_ROLE, SALES_ROLE));

        controller.doGet(request, response);

        verify(request).setAttribute(eq("roleList"), argThat((List<?> raw) -> {
            @SuppressWarnings("unchecked")
            List<Role> list = (List<Role>) raw;
            return list.size() == 2 && list.stream().anyMatch(r -> "Admin".equals(r.getRoleName()));
        }));
    }

    @Test
    public void create_roleIdIsAdmin_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("roleId")).thenReturn("1");
        when(employeeDAO.findAllRoles()).thenReturn(List.of(ADMIN_ROLE, SALES_ROLE));

        controller.doPost(request, response);

        verify(employeeDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=new&error=invalid");
    }

    /**
     * Dropdown đã lọc sẵn, nhưng chốt thật vẫn phải nằm ở server -- một
     * request tự dựng gửi thẳng roleId của Admin cũng phải bị chặn khi đây
     * là một lần THĂNG cấp (trước đó không phải Admin).
     */
    @Test
    public void update_promotingExistingEmployeeToAdmin_redirectsWithoutUpdating() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("userId")).thenReturn("15");
        User existing = new User();
        existing.setUserId(15);
        existing.setRoleId(2); // đang là Sales
        when(employeeDAO.findById(15)).thenReturn(existing);
        stubValidCreateFields();
        when(request.getParameter("roleId")).thenReturn("1"); // cố đổi thành Admin
        when(employeeDAO.findAllRoles()).thenReturn(List.of(ADMIN_ROLE, SALES_ROLE));

        controller.doPost(request, response);

        verify(employeeDAO, never()).update(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=edit&id=15&error=invalid");
    }

    /** Một Admin ĐÃ LÀ Admin từ trước vẫn phải lưu được bình thường khi sửa trường khác. */
    @Test
    public void update_savingExistingAdmin_stillSucceeds() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("userId")).thenReturn("15");
        User existing = new User();
        existing.setUserId(15);
        existing.setRoleId(1); // đã là Admin từ trước
        when(employeeDAO.findById(15)).thenReturn(existing);
        stubValidCreateFields();
        when(request.getParameter("roleId")).thenReturn("1"); // giữ nguyên Admin
        when(employeeDAO.findAllRoles()).thenReturn(List.of(ADMIN_ROLE, SALES_ROLE));
        when(employeeDAO.update(any(User.class))).thenReturn(true);

        controller.doPost(request, response);

        verify(employeeDAO).update(argThat((User u) -> u.getUserId() == 15));
        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=view&id=15");
    }
}
