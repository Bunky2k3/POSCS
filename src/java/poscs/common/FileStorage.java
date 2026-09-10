package poscs.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
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

    /**
     * Đuôi file được phép cho ô chọn ảnh.
     *
     * KHÔNG có ".svg": SVG là tài liệu XML, mở thẳng bằng URL thì trình duyệt
     * thực thi &lt;script&gt; bên trong nó ngay trên origin của ứng dụng --
     * mà trang chi tiết sản phẩm có sẵn thẻ &lt;a href="..."&gt; trỏ đúng vào
     * file ảnh. Ảnh raster không có khả năng đó.
     */
    public static final Set<String> IMAGE_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    /** Đuôi file được phép cho ô chọn tài liệu (catalogue sản phẩm). */
    public static final Set<String> DOCUMENT_EXTENSIONS = Set.of(".pdf");

    /**
     * true nếu part này lưu được: hoặc người dùng bỏ trống ô chọn file (part
     * rỗng, không phải lỗi), hoặc đuôi file nằm trong danh sách cho phép.
     *
     * Dùng để kiểm TRƯỚC khi ghi bất cứ thứ gì xuống CSDL. {@link #save} cũng
     * tự từ chối file sai loại, nhưng nó trả về null giống hệt trường hợp
     * "không chọn file" -- nơi gọi không phân biệt được để báo lỗi, và nếu
     * kiểm muộn thì bản ghi chính đã được tạo mất rồi.
     */
    public static boolean isAcceptable(Part part, Set<String> allowedExtensions) {
        if (part == null || part.getSize() <= 0) {
            return true;
        }
        return allowedExtensions.contains(extensionOf(part.getSubmittedFileName()));
    }

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
     * <p>Chỉ nhận các đuôi file nằm trong {@code allowedExtensions} -- đây là
     * chỗ chặn file có thể chứa mã chạy được (SVG, HTML...) lọt vào kho upload
     * rồi được UploadFileController phục vụ lại trên chính origin của ứng dụng.
     *
     * @return URL tương đối theo context path (dạng "/uploads/...") để lưu
     *         vào DB; null nếu part rỗng (người dùng không chọn file) HOẶC
     *         đuôi file không được phép -- nơi gọi đã coi null là "không có
     *         file" nên file bị từ chối đơn giản là không được lưu.
     */
    public static String save(Part part, String subfolder, Set<String> allowedExtensions) throws IOException {
        if (part == null || part.getSize() <= 0) {
            return null;
        }
        String extension = extensionOf(part.getSubmittedFileName());
        if (!allowedExtensions.contains(extension)) {
            return null;
        }
        String fileName = UUID.randomUUID() + extension;

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
