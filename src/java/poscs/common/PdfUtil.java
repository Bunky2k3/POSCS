package poscs.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.ServletContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * Helper dùng chung để dựng PDF qua Apache PDFBox -- hiện chỉ phục vụ xuất
 * PDF phiếu hỗ trợ (TechnicalSupportTicketController), trang vẽ từ đầu chứ
 * không dựa trên file mẫu. (Xuất/nhập PDF hợp đồng theo mẫu AcroForm đã gỡ
 * theo yêu cầu khách hàng, cùng với các hàm điền/đọc field và vẽ bảng.)
 *
 * PDFBox không tự có glyph tiếng Việt trong 14 font chuẩn -- phải nhúng 1
 * font TrueType thật (Noto Sans, giấy phép OFL, xem web/WEB-INF/fonts/) qua
 * PDType0Font. Bytes của font được cache tĩnh (đọc từ đĩa 1 lần cho cả
 * server) vì bản thân file không đổi giữa các lần export, dù mỗi PDDocument
 * vẫn cần tạo PDFont riêng (PDFBox không cho dùng chung PDFont giữa 2
 * PDDocument khác nhau).
 */
public final class PdfUtil {

    private static volatile byte[] vietnameseFontBytes;

    private PdfUtil() {
    }

    public static PDFont loadVietnameseFont(PDDocument document, ServletContext servletContext) throws IOException {
        return PDType0Font.load(document, new ByteArrayInputStream(fontBytes(servletContext)));
    }

    private static byte[] fontBytes(ServletContext servletContext) throws IOException {
        byte[] cached = vietnameseFontBytes;
        if (cached != null) {
            return cached;
        }
        synchronized (PdfUtil.class) {
            if (vietnameseFontBytes == null) {
                try (InputStream in = servletContext.getResourceAsStream("/WEB-INF/fonts/NotoSans-Regular.ttf")) {
                    if (in == null) {
                        throw new IOException("Khong tim thay font /WEB-INF/fonts/NotoSans-Regular.ttf");
                    }
                    vietnameseFontBytes = in.readAllBytes();
                }
            }
            return vietnameseFontBytes;
        }
    }

    /** Vẽ 1 dòng text không tự xuống dòng, gốc toạ độ (x, y) tính từ đáy trang (chuẩn PDF). */
    public static void drawText(PDPageContentStream cs, PDFont font, float fontSize, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text == null ? "" : text);
        cs.endText();
    }

    /** Bẻ 1 đoạn text dài thành các dòng vừa maxWidth theo font/cỡ chữ hiện tại. */
    public static List<String> wrapLines(PDFont font, float fontSize, float maxWidth, String text) throws IOException {
        List<String> lines = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            lines.add("");
            return lines;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (font.getStringWidth(candidate) / 1000 * fontSize > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }
}
