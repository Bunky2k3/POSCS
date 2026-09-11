package poscs.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/**
 * Test cho LogFiles -- lớp duy nhất chạm tới hệ thống file trong chức năng
 * "Nhật ký hệ thống". Trọng tâm là hai thứ có thể gây hại:
 *
 * <ul>
 *   <li>resolve() phải từ chối mọi tên không nằm trong danh sách quét được,
 *       nhất là các kiểu leo thư mục -- đây là lối duy nhất từ tham số HTTP
 *       tới một file trên đĩa;</li>
 *   <li>tail() phải đọc được đuôi file mà không nạp cả file vào RAM, vì
 *       catalina.out chạy lâu ngày có thể rất lớn.</li>
 * </ul>
 *
 * Dùng system property LOG_DIR (LogFiles đọc property trước biến môi trường)
 * để trỏ vào thư mục tạm -- không đụng gì tới log thật của máy.
 */
public class LogFilesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String previousLogDir;

    @Before
    public void pointLogDirAtTempFolder() {
        previousLogDir = System.getProperty("LOG_DIR");
        System.setProperty("LOG_DIR", folder.getRoot().getAbsolutePath());
    }

    @After
    public void restoreLogDir() {
        if (previousLogDir == null) {
            System.clearProperty("LOG_DIR");
        } else {
            System.setProperty("LOG_DIR", previousLogDir);
        }
    }

    private Path write(String name, String content) throws IOException {
        Path file = folder.getRoot().toPath().resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    // ------------------------------------------------------------------
    // list() -- lọc theo đuôi file và sắp mới nhất lên đầu
    // ------------------------------------------------------------------

    @Test
    public void list_keepsOnlyLogLikeFiles() throws Exception {
        write("catalina.out", "a");
        write("poscs.log", "b");
        write("notes.txt", "c");
        write("keystore.jks", "bí mật");
        write("application.properties", "db.password=...");

        List<String> names = LogFiles.list().stream().map(LogFiles.LogFile::getName).toList();

        assertTrue(names.contains("catalina.out"));
        assertTrue(names.contains("poscs.log"));
        assertTrue(names.contains("notes.txt"));
        assertFalse("File không phải log thì không được liệt kê", names.contains("keystore.jks"));
        assertFalse(names.contains("application.properties"));
    }

    @Test
    public void list_putsMostRecentlyModifiedFirst() throws Exception {
        Path older = write("old.log", "cũ");
        Path newer = write("new.log", "mới");
        Files.setLastModifiedTime(older, java.nio.file.attribute.FileTime.fromMillis(1_000_000));
        Files.setLastModifiedTime(newer, java.nio.file.attribute.FileTime.fromMillis(2_000_000));

        assertEquals("new.log", LogFiles.list().get(0).getName());
    }

    @Test
    public void list_unknownDirectory_returnsEmptyInsteadOfThrowing() {
        System.setProperty("LOG_DIR", folder.getRoot().getAbsolutePath() + "/khong-ton-tai");

        assertTrue(LogFiles.list().isEmpty());
    }

    // ------------------------------------------------------------------
    // resolve() -- lối duy nhất từ tham số HTTP tới file trên đĩa
    // ------------------------------------------------------------------

    @Test
    public void resolve_knownFile_returnsPathInsideLogDirectory() throws Exception {
        write("poscs.log", "nội dung");

        Path resolved = LogFiles.resolve("poscs.log");

        assertNotNull(resolved);
        assertEquals(folder.getRoot().toPath().toAbsolutePath(), resolved.getParent().toAbsolutePath());
    }

    @Test
    public void resolve_pathTraversal_isRejected() throws Exception {
        write("poscs.log", "nội dung");

        assertNull(LogFiles.resolve("../../windows/win.ini"));
        assertNull(LogFiles.resolve("..\\..\\windows\\win.ini"));
        assertNull(LogFiles.resolve("/etc/passwd"));
        assertNull(LogFiles.resolve("logs/poscs.log"));
    }

    @Test
    public void resolve_fileOutsideTheListing_isRejected() throws Exception {
        // Tên hợp lệ về hình thức, và file CÓ thật trong thư mục -- nhưng
        // không phải file log nên list() không nhận, resolve() cũng phải không.
        write("keystore.jks", "bí mật");

        assertNull(LogFiles.resolve("keystore.jks"));
        assertNull(LogFiles.resolve("khong-co-that.log"));
        assertNull(LogFiles.resolve(null));
    }

    // ------------------------------------------------------------------
    // tail()
    // ------------------------------------------------------------------

    @Test
    public void tail_returnsLastLinesInOriginalOrder() throws Exception {
        Path file = write("poscs.log", "dòng 1\ndòng 2\ndòng 3\ndòng 4\ndòng 5\n");

        List<String> lines = LogFiles.tail(file, 2);

        assertEquals(List.of("dòng 4", "dòng 5"), lines);
    }

    @Test
    public void tail_fileShorterThanRequested_returnsEverything() throws Exception {
        Path file = write("poscs.log", "chỉ có một dòng\n");

        assertEquals(List.of("chỉ có một dòng"), LogFiles.tail(file, 500));
    }

    @Test
    public void tail_emptyFile_returnsNoLines() throws Exception {
        Path file = write("poscs.log", "");

        assertTrue(LogFiles.tail(file, 100).isEmpty());
    }

    /**
     * File lớn hơn trần đọc: vẫn phải trả về đúng các dòng CUỐI, và không
     * được đọc quá phạm vi cho phép (nếu đọc cả file thì test này vẫn xanh,
     * nhưng nó khoá được hành vi "lấy từ cuối lên" -- thứ dễ viết ngược).
     */
    @Test
    public void tail_hugeFile_stillReturnsTheLastLines() throws Exception {
        StringBuilder big = new StringBuilder();
        for (int i = 1; i <= 60_000; i++) { // ~1 MB, vượt trần 512 KB
            big.append("dong log so ").append(i).append('\n');
        }
        Path file = write("poscs.log", big.toString());

        List<String> lines = LogFiles.tail(file, 3);

        assertEquals(List.of("dong log so 59998", "dong log so 59999", "dong log so 60000"), lines);
    }

    @Test
    public void tail_missingFile_returnsNoLinesInsteadOfThrowing() throws Exception {
        assertTrue(LogFiles.tail(folder.getRoot().toPath().resolve("khong-co.log"), 10).isEmpty());
        assertTrue(LogFiles.tail(null, 10).isEmpty());
    }

    @Test
    public void sizeText_isHumanReadable() throws Exception {
        write("nho.log", "abc");
        LogFiles.LogFile file = LogFiles.list().stream()
                .filter(f -> f.getName().equals("nho.log")).findFirst().orElseThrow();

        assertEquals("3 B", file.getSizeText());
    }
}
