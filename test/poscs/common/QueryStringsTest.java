package poscs.common;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * QueryStrings.param -- mã hoá một giá trị cho các link dựng tay trong JSP
 * (phân trang, lọc danh mục, xuất Excel).
 */
public class QueryStringsTest {

    @Test
    public void param_null_isEmpty() {
        assertEquals("", QueryStrings.param(null));
    }

    /**
     * "&" và "#" là hai ký tự đã làm cụt từ khoá trên link phân trang: "&" tách
     * thành tham số khác, "#" biến phần sau thành neo trang.
     */
    @Test
    public void param_ampersandAndHash_areEncoded() {
        assertEquals("A%26B+%231", QueryStrings.param("A&B #1"));
    }

    /** Tiếng Việt có dấu đi qua UTF-8 -- đọc lại bằng getParameter ra đúng chữ gốc. */
    @Test
    public void param_vietnamese_isUtf8Encoded() {
        assertEquals("C%C3%A1p+quang", QueryStrings.param("Cáp quang"));
    }

    /** Kết quả không còn ký tự nào phá được thuộc tính href (", <, >, '). */
    @Test
    public void param_htmlSpecialChars_areEncodedToo() {
        String out = QueryStrings.param("\"><script>'");
        assertFalse(out.matches(".*[\"<>'].*"));
    }
}
