package poscs.common;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test cho TextRules -- lớp phòng thủ ở đầu vào cho các ô văn bản tự do.
 *
 * {@code isSafeHttpUrl} đáng chú ý nhất: link hợp đồng do người dùng dán vào
 * rồi được đặt thẳng vào thuộc tính {@code href}. Escape lúc hiển thị làm
 * chuỗi an toàn về mặt HTML nhưng KHÔNG ngăn {@code javascript:...} chạy khi
 * người dùng bấm -- chặn scheme ở đây mới là thứ ngăn được.
 *
 * JUnit 4 -- xem CustomerControllerTest.
 */
public class TextRulesTest {

    // ------------------------------------------------------------------
    // isSafeFreeText
    // ------------------------------------------------------------------

    @Test
    public void freeText_vietnameseNameWithDiacritics_isAccepted() {
        // Bộ lọc chỉ chặn 3 ký tự phá ngữ cảnh HTML, không được chặn nhầm
        // tên thật của người dùng.
        assertTrue(TextRules.isSafeFreeText("Nguyễn Đình Dũng"));
    }

    @Test
    public void freeText_nullIsAccepted() {
        // Nơi gọi tự quyết trường đó có bắt buộc hay không.
        assertTrue(TextRules.isSafeFreeText(null));
    }

    @Test
    public void freeText_angleBracketsRejected() {
        assertFalse(TextRules.isSafeFreeText("<script>alert(1)</script>"));
    }

    @Test
    public void freeText_doubleQuoteRejected() {
        // Dấu này thoát ra khỏi value="..." của form.
        assertFalse(TextRules.isSafeFreeText("Trần\" onfocus=alert(1) x=\""));
    }

    // ------------------------------------------------------------------
    // isSafeHttpUrl
    // ------------------------------------------------------------------

    @Test
    public void url_googleDriveLinkIsAccepted() {
        assertTrue(TextRules.isSafeHttpUrl("https://drive.google.com/file/d/1AbC/view"));
    }

    @Test
    public void url_plainHttpIsAccepted() {
        assertTrue(TextRules.isSafeHttpUrl("http://noi-bo.congty.vn/hop-dong.pdf"));
    }

    @Test
    public void url_emptyOrNullIsAccepted() {
        // Ô link không bắt buộc -- bỏ trống không phải lỗi.
        assertTrue(TextRules.isSafeHttpUrl(null));
        assertTrue(TextRules.isSafeHttpUrl(""));
    }

    @Test
    public void url_javascriptSchemeRejected() {
        // Đây là lý do hàm này tồn tại: escape lúc hiển thị không cứu được.
        assertFalse(TextRules.isSafeHttpUrl("javascript:alert(document.domain)"));
    }

    @Test
    public void url_javascriptSchemeInMixedCaseRejected() {
        assertFalse(TextRules.isSafeHttpUrl("JaVaScRiPt:alert(1)"));
    }

    @Test
    public void url_dataSchemeRejected() {
        assertFalse(TextRules.isSafeHttpUrl("data:text/html;base64,PHNjcmlwdD4="));
    }

    @Test
    public void url_schemeRelativeRejected() {
        // "//evil.test/x" trình duyệt hiểu là sang site khác, không phải link nội bộ.
        assertFalse(TextRules.isSafeHttpUrl("//evil.test/hop-dong.pdf"));
    }

    @Test
    public void url_httpButContainingQuoteRejected() {
        // Đúng scheme nhưng vẫn thoát được ra khỏi thuộc tính href.
        assertFalse(TextRules.isSafeHttpUrl("https://ok.test/\" onmouseover=alert(1) x=\""));
    }
}
