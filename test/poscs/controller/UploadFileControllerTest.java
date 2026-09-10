package poscs.controller;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import poscs.common.FileStorage;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cho UploadFileController -- phục vụ lại file tải lên nằm NGOÀI
 * webapp. Trọng tâm: chặn path traversal (vd "/../../etc/passwd") ra ngoài
 * upload root, và mapping Content-Type thủ công cho các đuôi file phổ biến
 * (Files.probeContentType có thể trả null tuỳ máy, xem javadoc gốc).
 *
 * Dùng thư mục tạm thật + MockedStatic&lt;FileStorage&gt; để trỏ
 * FileStorage.getUploadRoot() về thư mục đó, thay vì phụ thuộc biến môi
 * trường UPLOAD_DIR/thư mục home thật của máy chạy test.
 */
public class UploadFileControllerTest {

    private UploadFileController controller;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private Path uploadRoot;

    @Before
    public void setUp() throws Exception {
        controller = new UploadFileController();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));

        uploadRoot = Files.createTempDirectory("poscs-upload-test-");
    }

    @After
    public void tearDown() throws Exception {
        // Dọn sạch thư mục tạm sau mỗi test -- xoá đệ quy tay vì Path chưa có sẵn hàm này.
        if (Files.exists(uploadRoot)) {
            Files.walk(uploadRoot)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    private MockedStatic<FileStorage> mockUploadRoot() {
        MockedStatic<FileStorage> fileStorage = mockStatic(FileStorage.class);
        fileStorage.when(FileStorage::getUploadRoot).thenReturn(uploadRoot);
        return fileStorage;
    }

    @Test
    public void nullPathInfo_returns404() throws Exception {
        when(request.getPathInfo()).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void rootPathInfo_returns404() throws Exception {
        when(request.getPathInfo()).thenReturn("/");

        controller.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void pathTraversalAttempt_isBlockedWith404() throws Exception {
        // File thật này KHÔNG được nằm trong uploadRoot -- tạo ở thư mục tạm cha để mô
        // phỏng có 1 file thật tồn tại ở vị trí "../" mà kẻ tấn công đang cố đọc.
        Path secretOutsideRoot = uploadRoot.getParent().resolve("secret-" + System.nanoTime() + ".txt");
        Files.writeString(secretOutsideRoot, "should not be readable");
        try {
            when(request.getPathInfo()).thenReturn("/../" + secretOutsideRoot.getFileName());

            try (MockedStatic<FileStorage> fs = mockUploadRoot()) {
                controller.doGet(request, response);
            }

            verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
            verify(response, never()).setContentType(anyString());
        } finally {
            Files.deleteIfExists(secretOutsideRoot);
        }
    }

    @Test
    public void nonExistentFile_returns404() throws Exception {
        when(request.getPathInfo()).thenReturn("/enterprise_logos/does-not-exist.jpg");

        try (MockedStatic<FileStorage> fs = mockUploadRoot()) {
            controller.doGet(request, response);
        }

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void existingJpgFile_isServedWithMappedContentTypeAndCorrectLength() throws Exception {
        Path subDir = uploadRoot.resolve("enterprise_logos");
        Files.createDirectories(subDir);
        Path file = subDir.resolve("logo.jpg");
        byte[] content = "fake-jpg-bytes".getBytes();
        Files.write(file, content);

        when(request.getPathInfo()).thenReturn("/enterprise_logos/logo.jpg");

        try (MockedStatic<FileStorage> fs = mockUploadRoot()) {
            controller.doGet(request, response);
        }

        verify(response).setContentType("image/jpeg");
        verify(response).setContentLengthLong(content.length);
        verify(response, never()).sendError(anyInt());
    }

    @Test
    public void unknownExtension_fallsBackToProbedOrOctetStreamContentType() throws Exception {
        Path file = uploadRoot.resolve("mystery.xyz123");
        Files.write(file, "data".getBytes());

        when(request.getPathInfo()).thenReturn("/mystery.xyz123");

        try (MockedStatic<FileStorage> fs = mockUploadRoot()) {
            controller.doGet(request, response);
        }

        // Đuôi lạ không nằm trong danh sách cho phép -- phải ép tải về, không
        // để trình duyệt tự diễn giải thành nội dung chạy được.
        verify(response).setContentType("application/octet-stream");
        verify(response).setHeader("Content-Disposition", "attachment");
    }

    // ------------------------------------------------------------------
    // Không phục vụ nội dung chạy được trên chính origin của ứng dụng
    // ------------------------------------------------------------------

    @Test
    public void svgFile_isForcedToDownloadInsteadOfRenderedAsImage() throws Exception {
        Path file = uploadRoot.resolve("payload.svg");
        Files.write(file, "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>x()</script></svg>".getBytes());

        when(request.getPathInfo()).thenReturn("/payload.svg");

        try (MockedStatic<FileStorage> fs = mockUploadRoot()) {
            controller.doGet(request, response);
        }

        // Trả về image/svg+xml là trình duyệt chạy <script> bên trong ngay trên
        // origin này. Upload đã chặn .svg, nhưng file lỡ lưu từ trước vẫn còn
        // trong kho nên tầng phục vụ phải tự chặn lấy.
        verify(response, never()).setContentType("image/svg+xml");
        verify(response).setContentType("application/octet-stream");
        verify(response).setHeader("Content-Disposition", "attachment");
    }

    @Test
    public void servedFile_alwaysCarriesNoSniffHeader() throws Exception {
        Path file = uploadRoot.resolve("logo.png");
        Files.write(file, new byte[]{1, 2, 3});

        when(request.getPathInfo()).thenReturn("/logo.png");

        try (MockedStatic<FileStorage> fs = mockUploadRoot()) {
            controller.doGet(request, response);
        }

        // Thiếu header này thì trình duyệt có thể tự đoán kiểu từ nội dung và
        // bỏ qua Content-Type, vô hiệu hoá luôn việc ép octet-stream ở trên.
        verify(response).setHeader("X-Content-Type-Options", "nosniff");
    }
}
