package poscs.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.common.FileStorage;

/**
 * Phục vụ lại file đã tải lên (ảnh sản phẩm, catalogue...) được lưu NGOÀI
 * webapp -- xem FileStorage. Tomcat không tự phục vụ được các file này vì
 * chúng không nằm trong docBase của ứng dụng. Vẫn đi qua
 * AuthenticationFilter như mọi request khác (không nằm trong PUBLIC_PATHS)
 * nên yêu cầu đã đăng nhập, khớp với việc cả hệ thống không có trang public.
 */
@WebServlet(name = "UploadFileController", urlPatterns = {"/uploads/*"})
public class UploadFileController extends HttpServlet {

    // Danh sách CHO PHÉP các kiểu được trả về nguyên trạng cho trình duyệt
    // hiển thị. Cố tình không dùng Files.probeContentType() làm nguồn chính:
    // nó đọc cấu hình MIME của hệ điều hành, nên một file lạ vẫn có thể được
    // gán kiểu chạy được (vd image/svg+xml, text/html) và thực thi script
    // ngay trên origin của ứng dụng khi người dùng mở thẳng URL file đó.
    //
    // KHÔNG có ".svg": SVG là tài liệu XML chạy được &lt;script&gt;. Upload
    // đã chặn từ FileStorage, nhưng file lỡ lưu trước đó vẫn nằm trong kho,
    // nên chặn thêm ở đây -- chúng sẽ rơi xuống nhánh tải về bên dưới.
    private static final Map<String, String> INLINE_CONTENT_TYPES = Map.of(
            ".jpg", "image/jpeg", ".jpeg", "image/jpeg", ".png", "image/png",
            ".gif", "image/gif", ".webp", "image/webp",
            ".pdf", "application/pdf"
    );

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Path root = FileStorage.getUploadRoot().normalize();
        Path requested = root.resolve(pathInfo.substring(1)).normalize();

        // Chặn path traversal (vd "/uploads/../../windows/win.ini"): file
        // thực tế phải nằm bên trong root sau khi normalize đường dẫn.
        if (!requested.startsWith(root) || !Files.isRegularFile(requested)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String inlineType = INLINE_CONTENT_TYPES.get(extensionOf(requested));
        if (inlineType != null) {
            response.setContentType(inlineType);
        } else {
            // Kiểu không nằm trong danh sách cho phép: ép tải về thay vì hiển
            // thị, để nội dung không bao giờ được diễn giải thành trang chạy
            // được trên origin của ứng dụng.
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment");
        }
        // Chặn trình duyệt tự đoán kiểu từ nội dung file và bỏ qua Content-Type
        // ở trên -- không có header này thì việc ép octet-stream vẫn có thể bị
        // vượt qua bằng content sniffing.
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLengthLong(Files.size(requested));

        try (OutputStream out = response.getOutputStream()) {
            Files.copy(requested, out);
        }
    }

    private static String extensionOf(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot).toLowerCase() : "";
    }
}
