package poscs.common;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tìm và đọc file nhật ký của máy chủ, phục vụ màn hình "Nhật ký hệ thống"
 * (chỉ Admin -- xem SystemLogController).
 *
 * <h3>Đọc ở đâu</h3>
 * Ứng dụng ghi log ra stderr, và servlet container gom lại thành file. Với
 * Tomcat đó là {@code ${catalina.base}/logs}. Thứ tự tìm:
 *
 * <ol>
 *   <li>biến môi trường (hoặc system property) {@code LOG_DIR} -- cho môi
 *       trường nào đặt log ra chỗ khác, giống cách DBContext đọc DB_URL;</li>
 *   <li>{@code ${catalina.base}/logs}, rồi {@code ${catalina.home}/logs}.</li>
 * </ol>
 *
 * Không tìm được thì trả về null và màn hình nói thẳng là chưa cấu hình, thay
 * vì đoán bừa một thư mục rồi hiện danh sách rỗng khó hiểu.
 *
 * <h3>Vì sao chỉ đọc theo TÊN file, không theo đường dẫn</h3>
 * Tên file luôn được đối chiếu với danh sách {@link #list()} vừa quét được.
 * Người dùng không bao giờ đưa được đường dẫn vào đây, nên không có chỗ cho
 * {@code ../../windows/win.ini} -- an toàn hơn là tự đi chuẩn hoá và so sánh
 * tiền tố đường dẫn.
 */
public final class LogFiles {

    /** Chỉ coi là file log nếu mang một trong các đuôi này. */
    private static final Set<String> LOG_EXTENSIONS = Set.of(".log", ".out", ".txt");

    /** Tên file hợp lệ: không có dấu phân cách đường dẫn, không có "..". */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,120}");

    /**
     * Trần số byte đọc khi xem đuôi file. catalina.out chạy lâu ngày có thể
     * lên hàng trăm MB; đọc cả file vào RAM để hiện 200 dòng cuối là cách
     * nhanh nhất để hạ máy chủ bằng chính màn hình xem log.
     */
    private static final long MAX_TAIL_BYTES = 512 * 1024;

    private LogFiles() {
    }

    /** Một file log kèm thông tin đủ để hiển thị danh sách. */
    public static final class LogFile {

        private final String name;
        private final long sizeBytes;
        private final long lastModifiedMillis;

        LogFile(String name, long sizeBytes, long lastModifiedMillis) {
            this.name = name;
            this.sizeBytes = sizeBytes;
            this.lastModifiedMillis = lastModifiedMillis;
        }

        public String getName() {
            return name;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public long getLastModifiedMillis() {
            return lastModifiedMillis;
        }

        /** Kích thước cho người đọc: "12,3 MB" thay vì 12902400. */
        public String getSizeText() {
            if (sizeBytes < 1024) {
                return sizeBytes + " B";
            }
            if (sizeBytes < 1024 * 1024) {
                return String.format("%.1f KB", sizeBytes / 1024.0);
            }
            return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
        }
    }

    /** Thư mục log đang dùng, hoặc null nếu không xác định được. */
    public static Path directory() {
        String configured = config("LOG_DIR");
        if (configured != null && !configured.isEmpty()) {
            return Paths.get(configured);
        }
        for (String property : new String[]{"catalina.base", "catalina.home"}) {
            String home = System.getProperty(property);
            if (home != null && !home.isEmpty()) {
                Path logs = Paths.get(home, "logs");
                if (Files.isDirectory(logs)) {
                    return logs;
                }
            }
        }
        return null;
    }

    /** Danh sách file log, mới sửa gần đây nhất lên đầu. Rỗng nếu không đọc được thư mục. */
    public static List<LogFile> list() {
        Path dir = directory();
        if (dir == null || !Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        List<LogFile> result = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                String name = path.getFileName().toString();
                if (!Files.isRegularFile(path) || !isLogName(name)) {
                    continue;
                }
                result.add(new LogFile(name, Files.size(path), Files.getLastModifiedTime(path).toMillis()));
            }
        } catch (IOException ex) {
            return Collections.emptyList();
        }
        result.sort(Comparator.comparingLong(LogFile::getLastModifiedMillis).reversed());
        return result;
    }

    /**
     * Đường dẫn thật của file log mang tên này, hoặc null nếu tên không nằm
     * trong danh sách quét được. Mọi lối vào file đều phải đi qua đây.
     */
    public static Path resolve(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            return null;
        }
        boolean known = list().stream().anyMatch(f -> f.getName().equals(name));
        if (!known) {
            return null;
        }
        Path dir = directory();
        return dir == null ? null : dir.resolve(name);
    }

    /**
     * {@code maxLines} dòng CUỐI của file, cũ trước mới sau.
     *
     * Đọc ngược từ cuối file (RandomAccessFile) trong phạm vi
     * {@link #MAX_TAIL_BYTES}, nên chi phí không phụ thuộc file to cỡ nào.
     * Dòng đầu tiên đọc được có thể bị cắt mất phần đầu nếu chạm trần byte --
     * chấp nhận được với một màn hình xem đuôi log, và vẫn tốt hơn nhiều so
     * với việc từ chối hiển thị.
     */
    public static List<String> tail(Path file, int maxLines) throws IOException {
        List<String> lines = new ArrayList<>();
        if (file == null || !Files.isRegularFile(file) || maxLines <= 0) {
            return lines;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            long from = Math.max(0, length - MAX_TAIL_BYTES);
            byte[] buffer = new byte[(int) (length - from)];
            raf.seek(from);
            raf.readFully(buffer);

            String[] all = new String(buffer, StandardCharsets.UTF_8).split("\r?\n", -1);
            // Phần tử cuối là chuỗi rỗng khi file kết thúc bằng xuống dòng.
            int end = all.length > 0 && all[all.length - 1].isEmpty() ? all.length - 1 : all.length;
            int start = Math.max(0, end - maxLines);
            lines.addAll(Arrays.asList(all).subList(start, end));
        }
        return lines;
    }

    private static boolean isLogName(String name) {
        if (!SAFE_NAME.matcher(name).matches()) {
            return false;
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 && LOG_EXTENSIONS.contains(name.substring(dot).toLowerCase());
    }

    /** System property trước, rồi biến môi trường -- giống DBContext. */
    private static String config(String key) {
        String fromProperty = System.getProperty(key);
        if (fromProperty != null && !fromProperty.isEmpty()) {
            return fromProperty;
        }
        return System.getenv(key);
    }
}
