package poscs.common;

import jakarta.servlet.http.Part;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Test cho FileStorage -- cụ thể là danh sách đuôi file được phép.
 *
 * Chỉ kiểm các nhánh TỪ CHỐI: chúng thoát ra trước khi hàm chạm tới đĩa, nên
 * chạy được mà không ghi gì vào thư mục upload thật (đường dẫn đó lấy từ biến
 * môi trường UPLOAD_DIR hoặc thư mục home của máy chạy test -- không nên đụng
 * vào trong unit test). Nhánh chấp nhận thực sự có ghi file, thuộc phạm vi
 * integration test.
 *
 * JUnit 4 -- xem CustomerControllerTest.
 */
public class FileStorageTest {

    private static Part partNamed(String submittedFileName) {
        Part part = mock(Part.class);
        when(part.getSize()).thenReturn(1024L);
        when(part.getSubmittedFileName()).thenReturn(submittedFileName);
        return part;
    }

    @Test
    public void save_svgAsImage_isRejected() throws Exception {
        // SVG là tài liệu XML chạy được <script>; mở thẳng URL file là script
        // chạy trên chính origin của ứng dụng. Trang chi tiết sản phẩm có sẵn
        // thẻ <a> trỏ vào file ảnh, nên đây là đường khai thác có thật.
        assertNull(FileStorage.save(partNamed("payload.svg"), "products/images",
                FileStorage.IMAGE_EXTENSIONS));
    }

    @Test
    public void save_htmlAsImage_isRejected() throws Exception {
        assertNull(FileStorage.save(partNamed("payload.html"), "products/images",
                FileStorage.IMAGE_EXTENSIONS));
    }

    @Test
    public void save_pdfAsImage_isRejected() throws Exception {
        // Mỗi ô chọn file có danh sách riêng -- tài liệu không lọt vào ô ảnh.
        assertNull(FileStorage.save(partNamed("catalogue.pdf"), "products/images",
                FileStorage.IMAGE_EXTENSIONS));
    }

    @Test
    public void save_imageAsDocument_isRejected() throws Exception {
        assertNull(FileStorage.save(partNamed("anh.png"), "products/catalogues",
                FileStorage.DOCUMENT_EXTENSIONS));
    }

    @Test
    public void save_fileWithoutExtension_isRejected() throws Exception {
        // extensionOf() trả về chuỗi rỗng, không nằm trong danh sách nào.
        assertNull(FileStorage.save(partNamed("khong-co-duoi"), "products/images",
                FileStorage.IMAGE_EXTENSIONS));
    }

    @Test
    public void save_doubleExtensionEndingInSvg_isRejected() throws Exception {
        // Chỉ phần sau dấu chấm CUỐI cùng được xét, nên "anh.png.svg" là .svg.
        assertNull(FileStorage.save(partNamed("anh.png.svg"), "products/images",
                FileStorage.IMAGE_EXTENSIONS));
    }

    @Test
    public void save_emptyPart_returnsNull() throws Exception {
        Part empty = mock(Part.class);
        when(empty.getSize()).thenReturn(0L);

        // Ô chọn file để trống vẫn gửi lên 1 part size=0 -- không phải lỗi.
        assertNull(FileStorage.save(empty, "products/images", FileStorage.IMAGE_EXTENSIONS));
    }

    @Test
    public void save_nullPart_returnsNull() throws Exception {
        assertNull(FileStorage.save(null, "products/images", FileStorage.IMAGE_EXTENSIONS));
    }

    @Test
    public void imageExtensions_doNotIncludeSvg() {
        assertFalse("SVG không bao giờ được nằm trong danh sách ảnh cho phép",
                FileStorage.IMAGE_EXTENSIONS.contains(".svg"));
    }
}
