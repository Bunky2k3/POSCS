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

    // Files.probeContentType() dựa vào cấu hình MIME của hệ điều hành, có
    // thể trả về null cho các đuôi file phổ biến trên một số máy Windows --
    // fallback thủ công để ảnh/PDF luôn có Content-Type đúng, trình duyệt
    // hiển thị được thay vì tải xuống dạng "unknown file".
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".jpg", "image/jpeg", ".jpeg", "image/jpeg", ".png", "image/png",
            ".gif", "image/gif", ".webp", "image/webp", ".svg", "image/svg+xml",
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

        response.setContentType(contentTypeOf(requested));
        response.setContentLengthLong(Files.size(requested));

        try (OutputStream out = response.getOutputStream()) {
            Files.copy(requested, out);
        }
    }

    private String contentTypeOf(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot).toLowerCase() : "";
        String mapped = CONTENT_TYPES.get(ext);
        if (mapped != null) {
            return mapped;
        }
        String probed = Files.probeContentType(file);
        return probed != null ? probed : "application/octet-stream";
    }
}
