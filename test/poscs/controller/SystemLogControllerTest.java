package poscs.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cho SystemLogController -- màn hình xem/tải nhật ký máy chủ.
 *
 * Trọng tâm là phân quyền: log chứa thông điệp lỗi kèm ngữ cảnh nghiệp vụ và
 * stack trace, nên mọi vai trò khác Admin phải bị chặn ở CẢ hai đường -- xem
 * trên giao diện lẫn tải file, vì đường tải không đi qua JSP nào.
 *
 * Trỏ LOG_DIR vào thư mục tạm nên test đọc file thật mà không đụng log của máy.
 * JUnit 4 -- xem CustomerControllerTest.
 */
public class SystemLogControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";
    private static final String VIEW = "/jsp/admin/systemLog.jsp";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final SystemLogController controller = new SystemLogController();
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final HttpSession session = mock(HttpSession.class);

    private String previousLogDir;

    @Before
    public void setUp() throws Exception {
        previousLogDir = System.getProperty("LOG_DIR");
        System.setProperty("LOG_DIR", folder.getRoot().getAbsolutePath());
        Files.write(folder.getRoot().toPath().resolve("poscs.log"),
                "dòng 1\nERROR có lỗi ở đây\ndòng 3\n".getBytes(StandardCharsets.UTF_8));

        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);
        loginAs("Admin");
    }

    @After
    public void restoreLogDir() {
        if (previousLogDir == null) {
            System.clearProperty("LOG_DIR");
        } else {
            System.setProperty("LOG_DIR", previousLogDir);
        }
    }

    private void loginAs(String roleName) {
        User user = new User();
        user.setUserId(1);
        user.setUsername("admin1");
        user.setRole(new Role(1, roleName));
        when(session.getAttribute("currentUser")).thenReturn(user);
    }

    private RequestDispatcher stubDispatcher() {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher(VIEW)).thenReturn(dispatcher);
        return dispatcher;
    }

    private ByteArrayOutputStream captureResponseBody() throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                captured.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        });
        return captured;
    }

    // ------------------------------------------------------------------
    // Phân quyền
    // ------------------------------------------------------------------

    @Test
    public void view_nonAdminRoles_allGet403() throws Exception {
        for (String role : new String[]{"Sales", "Kỹ thuật", "CSKH"}) {
            HttpServletResponse res = mock(HttpServletResponse.class);
            loginAs(role);

            controller.doGet(request, res);

            verify(res).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
            verify(request, never()).getRequestDispatcher(VIEW);
        }
    }

    /** Đường tải file không qua JSP, nên phải tự gác quyền riêng. */
    @Test
    public void download_nonAdmin_gets403AndNoFileContent() throws Exception {
        loginAs("Sales");
        when(request.getParameter("action")).thenReturn("download");
        when(request.getParameter("file")).thenReturn("poscs.log");

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(response, never()).getOutputStream();
    }

    @Test
    public void view_sessionExpired_gets403() throws Exception {
        when(request.getSession(false)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }

    // ------------------------------------------------------------------
    // Xem log
    // ------------------------------------------------------------------

    @Test
    public void view_admin_forwardsWithFileListAndContent() throws Exception {
        RequestDispatcher dispatcher = stubDispatcher();

        controller.doGet(request, response);

        verify(dispatcher).forward(request, response);
        assertEquals("poscs.log", attribute("selectedFile"));
        assertEquals(3, ((List<?>) attribute("logContent")).size());
        assertEquals(1, ((List<?>) attribute("logFiles")).size());
    }

    /**
     * Access log được ghi ở MỌI request nên luôn là file mới nhất. Cứ lấy file
     * mới nhất thì người vào xem lỗi lại mở trúng danh sách lượt truy cập HTTP.
     */
    @Test
    public void view_defaultsToApplicationLogNotAccessLog() throws Exception {
        stubDispatcher();
        java.nio.file.Path access = folder.getRoot().toPath().resolve("localhost_access_log.2026-09-11.txt");
        Files.write(access, "GET /POSCS/dashboard\n".getBytes(StandardCharsets.UTF_8));
        // Access log mới hơn hẳn file log ứng dụng.
        Files.setLastModifiedTime(folder.getRoot().toPath().resolve("poscs.log"),
                java.nio.file.attribute.FileTime.fromMillis(1_000_000));
        Files.setLastModifiedTime(access, java.nio.file.attribute.FileTime.fromMillis(2_000_000));

        controller.doGet(request, response);

        assertEquals("poscs.log", attribute("selectedFile"));
        assertEquals("Access log vẫn phải nằm trong danh sách để chọn",
                2, ((List<?>) attribute("logFiles")).size());
    }

    @Test
    public void view_keyword_filtersLinesCaseInsensitively() throws Exception {
        stubDispatcher();
        when(request.getParameter("keyword")).thenReturn("error");

        controller.doGet(request, response);

        List<?> content = (List<?>) attribute("logContent");
        assertEquals(1, content.size());
        assertEquals("ERROR có lỗi ở đây", content.get(0));
    }

    /** Tên file bịa không được làm trang vỡ, chỉ là không có nội dung để hiện. */
    @Test
    public void view_unknownFileName_redirectsWithNotFoundInsteadOfBlankPage() throws Exception {
        when(request.getParameter("file")).thenReturn("../../etc/passwd");

        controller.doGet(request, response);

        // Trước đây trang vẫn render nhưng trống trơn, không kèm thông báo nào --
        // người dùng không biết vì sao không thấy gì. systemLog.jsp đã có sẵn
        // thông báo cho error=notfound, nhánh tải file cũng dùng đúng tham số này.
        verify(response).sendRedirect(CONTEXT_PATH + "/systemLog?error=notfound");
        verify(request, never()).getRequestDispatcher(anyString());
    }

    /** Không truyền tham số file thì vẫn mở file mặc định như cũ, không báo lỗi. */
    @Test
    public void view_noFileParameter_stillOpensDefaultLogFile() throws Exception {
        stubDispatcher();

        controller.doGet(request, response);

        verify(response, never()).sendRedirect(anyString());
        assertNotNull(attribute("logFiles"));
    }

    /** Số dòng do người dùng gửi lên phải bị kẹp, không cho kéo cả file khổng lồ ra màn hình. */
    @Test
    public void view_lineCount_isClampedAndFallsBackOnGarbage() throws Exception {
        stubDispatcher();

        when(request.getParameter("lines")).thenReturn("999999");
        controller.doGet(request, response);
        assertEquals(2000, attribute("lines"));

        when(request.getParameter("lines")).thenReturn("mười");
        controller.doGet(request, response);
        assertEquals(200, attribute("lines"));

        when(request.getParameter("lines")).thenReturn("-5");
        controller.doGet(request, response);
        assertEquals(1, attribute("lines"));
    }

    // ------------------------------------------------------------------
    // Tải file
    // ------------------------------------------------------------------

    @Test
    public void download_admin_streamsFileAsAttachment() throws Exception {
        ByteArrayOutputStream captured = captureResponseBody();
        when(request.getParameter("action")).thenReturn("download");
        when(request.getParameter("file")).thenReturn("poscs.log");

        controller.doGet(request, response);

        assertTrue(captured.toString(StandardCharsets.UTF_8).contains("ERROR có lỗi ở đây"));
        verify(response).setContentType("application/octet-stream");
        verify(response).setHeader("Content-Disposition", "attachment; filename=\"poscs.log\"");
        verify(response).setHeader("X-Content-Type-Options", "nosniff");
    }

    @Test
    public void download_unknownFile_redirectsInsteadOfStreaming() throws Exception {
        when(request.getParameter("action")).thenReturn("download");
        when(request.getParameter("file")).thenReturn("../../windows/win.ini");

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/systemLog?error=notfound");
        verify(response, never()).getOutputStream();
    }

    private Object attribute(String name) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(request, atLeastOnce()).setAttribute(eq(name), captor.capture());
        return captor.getValue();
    }
}
