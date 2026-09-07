package poscs.controller;

import java.lang.reflect.Field;
import java.sql.Date;
import java.time.LocalDate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import poscs.common.EmailUtil;
import poscs.dao.AddressDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Role;
import poscs.model.User;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test tầng Controller cho EmployeeController -- khác Customer/Contract/
 * Ticket ở chỗ quyền được gate DUY NHẤT 1 lần ở đầu doGet/doPost (chỉ Admin
 * được đụng vào Employee, không có tier View only), nên test luôn gọi thẳng
 * doGet/doPost thay vì gọi riêng từng handler. Tập trung vào BR-28..BR-31
 * (các trường bắt buộc khi tạo/sửa nhân viên), chặn trùng SĐT/CCCD, và
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
    // POST ?action=create (BR-28..BR-31)
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

    @Test
    public void create_validFields_generatesAccountAndRedirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(employeeDAO.generateUniqueUsername("Nguyễn", null, "An")).thenReturn("annd");
        when(employeeDAO.insert(any(User.class))).thenReturn(15);

        controller.doPost(request, response);

        verify(employeeDAO).insert(argThat((User u) ->
                "annd".equals(u.getUsername())
                        && "annd@postef.com.vn".equals(u.getEmail())
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

    @Test
    public void sendAccount_passwordUpdateFails_redirectsWithSendFailedError() throws Exception {
        when(request.getParameter("action")).thenReturn("sendAccount");
        when(request.getParameter("id")).thenReturn("15");
        User employee = new User();
        employee.setUserId(15);
        when(employeeDAO.findById(15)).thenReturn(employee);
        when(employeeDAO.updatePasswordHash(eq(15), anyString())).thenReturn(false);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=view&id=15&error=send_failed");
    }

    @Test
    public void sendAccount_mailSucceeds_redirectsWithSentFlag() throws Exception {
        when(request.getParameter("action")).thenReturn("sendAccount");
        when(request.getParameter("id")).thenReturn("15");
        User employee = new User();
        employee.setUserId(15);
        employee.setLastName("Nguyễn");
        employee.setFirstName("An");
        when(employeeDAO.findById(15)).thenReturn(employee);
        when(employeeDAO.updatePasswordHash(eq(15), anyString())).thenReturn(true);

        try (org.mockito.MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            emailUtil.when(() -> EmailUtil.sendNewAccountEmail(any(), any(), any(), any(), any())).thenReturn(true);

            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=view&id=15&sent=1");
        }
    }

    @Test
    public void sendAccount_mailFails_redirectsWithWarningFlag() throws Exception {
        when(request.getParameter("action")).thenReturn("sendAccount");
        when(request.getParameter("id")).thenReturn("15");
        User employee = new User();
        employee.setUserId(15);
        employee.setLastName("Nguyễn");
        employee.setFirstName("An");
        when(employeeDAO.findById(15)).thenReturn(employee);
        when(employeeDAO.updatePasswordHash(eq(15), anyString())).thenReturn(true);

        try (org.mockito.MockedStatic<EmailUtil> emailUtil = mockStatic(EmailUtil.class)) {
            emailUtil.when(() -> EmailUtil.sendNewAccountEmail(any(), any(), any(), any(), any())).thenReturn(false);

            controller.doPost(request, response);

            verify(response).sendRedirect(CONTEXT_PATH + "/employee?action=view&id=15&warning=mail_failed");
        }
    }
}
