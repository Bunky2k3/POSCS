package poscs.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import jakarta.servlet.http.Part;

/**
 * Lưu file người dùng tải lên (ảnh sản phẩm, catalogue PDF...) vào một thư
 * mục NGOÀI webapp -- cấu hình qua biến môi trường UPLOAD_DIR giống cách
 * DBContext đọc DB_URL/DB_USER/DB_PASSWORD. Không lưu trong web/ hay
 * build/web vì cả 2 đều bị build/deploy lại ghi đè mất nội dung; thư mục
 * này sống độc lập, được phục vụ lại qua UploadFileController ("/uploads/*").
 */
public final class FileStorage {

    private static final String UPLOAD_ROOT = System.getenv().getOrDefault(
            "UPLOAD_DIR", Paths.get(System.getProperty("user.home"), "poscs_uploads").toString());

    private FileStorage() {
    }

    public static Path getUploadRoot() {
        return Paths.get(UPLOAD_ROOT);
    }

    /**
     * Lưu 1 Part (từ request.getParts(), field kiểu file) vào thư mục con
     * `subfolder` dưới upload root, đặt lại tên file bằng UUID ngẫu nhiên
     * (chỉ giữ phần mở rộng) để tránh trùng tên, ký tự lạ, hay path
     * traversal qua tên file gốc do trình duyệt gửi lên (không đáng tin).
     *
     * @return URL tương đối theo context path (dạng "/uploads/...") để lưu
     *         vào DB, hoặc null nếu part rỗng (người dùng không chọn file).
     */
    public static String save(Part part, String subfolder) throws IOException {
        if (part == null || part.getSize() <= 0) {
            return null;
        }
        String fileName = UUID.randomUUID() + extensionOf(part.getSubmittedFileName());

        Path targetDir = getUploadRoot().resolve(subfolder);
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve(fileName);

        try (InputStream in = part.getInputStream()) {
            Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }

        return "/uploads/" + subfolder + "/" + fileName;
    }

    private static String extensionOf(String submittedFileName) {
        if (submittedFileName == null) {
            return "";
        }
        int dot = submittedFileName.lastIndexOf('.');
        if (dot < 0 || dot == submittedFileName.length() - 1) {
            return "";
        }
        String ext = submittedFileName.substring(dot).toLowerCase();
        // Chỉ giữ phần mở rộng nếu toàn chữ/số thông thường -- chặn ký tự lạ
        // (vd "..", "/") lọt vào tên file cuối cùng ghi ra đĩa.
        return ext.matches("\\.[a-z0-9]{1,10}") ? ext : "";
    }
}
