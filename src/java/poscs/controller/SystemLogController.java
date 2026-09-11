package poscs.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.common.AccessControl;
import poscs.common.LogFiles;

/**
 * Màn hình "Nhật ký hệ thống": xem đuôi file log của máy chủ ngay trên giao
 * diện, và tải cả file về.
 *
 * <h3>Chỉ Admin</h3>
 * Log chứa thông điệp lỗi kèm ngữ cảnh nghiệp vụ (mã hợp đồng, email, tên
 * đăng nhập của người thao tác) và cả dấu vết cấu trúc hệ thống trong stack
 * trace. Đó là thứ chỉ người quản trị được đọc, nên MỌI action ở đây đều gọi
 * AccessControl.requireAdmin trước khi làm bất cứ việc gì -- kể cả action tải
 * file, vốn không đi qua JSP nào.
 *
 * <h3>Không nhận đường dẫn từ người dùng</h3>
 * Tham số "file" chỉ là TÊN file, và luôn được đối chiếu với danh sách quét
 * được trong thư mục log (xem LogFiles.resolve). Không có chỗ nào ghép chuỗi
 * người dùng gửi lên vào đường dẫn, nên không có đường cho path traversal.
 */
@WebServlet(name = "SystemLogController", urlPatterns = {"/systemLog"})
public class SystemLogController extends HttpServlet {

    private static final String VIEW = "/jsp/admin/systemLog.jsp";

    /** Số dòng cuối hiện mặc định, và các mức cho người dùng chọn. */
    private static final int DEFAULT_LINES = 200;
    private static final int MAX_LINES = 2000;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireAdmin(request, response)) {
            return;
        }
        if ("download".equals(request.getParameter("action"))) {
            download(request, response);
            return;
        }
        showLog(request, response);
    }

    private void showLog(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        List<LogFiles.LogFile> files = LogFiles.list();
        Path directory = LogFiles.directory();

        String selected = request.getParameter("file");
        if (selected == null && !files.isEmpty()) {
            selected = defaultFileName(files);
        }
        int lines = parseLines(request.getParameter("lines"));
        String keyword = trimToNull(request.getParameter("keyword"));

        Path file = LogFiles.resolve(selected);
        List<String> content = file == null ? List.of() : LogFiles.tail(file, lines);
        if (keyword != null) {
            String needle = keyword.toLowerCase(Locale.ROOT);
            content = content.stream()
                    .filter(line -> line.toLowerCase(Locale.ROOT).contains(needle))
                    .toList();
        }

        request.setAttribute("logDirectory", directory == null ? null : directory.toString());
        request.setAttribute("logFiles", files);
        request.setAttribute("selectedFile", file == null ? null : selected);
        request.setAttribute("lines", lines);
        request.setAttribute("keyword", keyword);
        request.setAttribute("logContent", content);
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    private void download(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Path file = LogFiles.resolve(request.getParameter("file"));
        if (file == null) {
            response.sendRedirect(request.getContextPath() + "/systemLog?error=notfound");
            return;
        }
        // octet-stream + attachment: file log là văn bản thô, nhưng ép tải về
        // thay vì mở trong tab để trình duyệt không bao giờ diễn giải nội dung
        // (log có thể chứa nguyên văn dữ liệu người dùng nhập).
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + file.getFileName() + "\"");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentLengthLong(Files.size(file));
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(file, out);
        }
    }

    /**
     * File mở sẵn khi vào trang: file mới sửa gần nhất, nhưng BỎ QUA access
     * log.
     *
     * Access log được ghi ở mọi request nên nó luôn là file mới nhất -- cứ
     * lấy file mới nhất thì người vào xem lỗi lại luôn mở trúng danh sách
     * lượt truy cập HTTP, không phải nhật ký ứng dụng. Nó vẫn nằm trong danh
     * sách để chọn khi cần.
     */
    private String defaultFileName(List<LogFiles.LogFile> files) {
        for (LogFiles.LogFile file : files) {
            if (!file.getName().toLowerCase(Locale.ROOT).contains("access_log")) {
                return file.getName();
            }
        }
        return files.get(0).getName();
    }

    /** Số dòng người dùng chọn, kẹp trong [1, MAX_LINES]; sai định dạng thì về mặc định. */
    private int parseLines(String value) {
        if (value == null) {
            return DEFAULT_LINES;
        }
        try {
            return Math.max(1, Math.min(MAX_LINES, Integer.parseInt(value.trim())));
        } catch (NumberFormatException ex) {
            return DEFAULT_LINES;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
