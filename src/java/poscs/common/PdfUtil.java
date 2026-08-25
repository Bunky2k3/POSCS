package poscs.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.ServletContext;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

/**
 * Helper dùng chung để dựng PDF qua Apache PDFBox -- hiện chỉ phục vụ xuất
 * PDF hợp đồng (ContractController). Nội dung pháp lý cố định (quốc hiệu,
 * các điều khoản, khối chữ ký) nằm sẵn trong file mẫu có AcroForm
 * (web/WEB-INF/templates/hopdong_template.pdf); ContractController chỉ điền
 * các field động (mã hợp đồng, thông tin khách hàng, ngày tháng...) qua
 * loadTemplate/fillField, rồi tự vẽ thêm bảng hạng mục sản phẩm (số dòng
 * thay đổi theo từng hợp đồng nên không thể là 1 field cố định) đè lên vùng
 * trống đã chừa sẵn trong file mẫu bằng drawTable.
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

    /** Nạp 1 file PDF mẫu (đặt trong WEB-INF, ví dụ hợp đồng có sẵn AcroForm) từ webapp resource. */
    public static PDDocument loadTemplate(ServletContext servletContext, String resourcePath) throws IOException {
        try (InputStream in = servletContext.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Khong tim thay PDF template: " + resourcePath);
            }
            return Loader.loadPDF(in.readAllBytes());
        }
    }

    /**
     * Điền giá trị 1 field AcroForm theo tên, bỏ qua an toàn nếu field không
     * tồn tại hoặc giá trị null -- nhưng vẫn ghi log cảnh báo khi field không
     * tồn tại, để phát hiện được nếu file mẫu bị đổi tên field (khác với
     * "không có AcroForm" thì báo lỗi thẳng, xem loadTemplate/exportPdf).
     */
    public static void fillField(PDAcroForm acroForm, String name, String value) throws IOException {
        if (acroForm == null) {
            return;
        }
        PDField field = acroForm.getField(name);
        if (field == null) {
            System.err.println("--- CANH BAO: FIELD PDF \"" + name + "\" KHONG TON TAI TRONG FILE MAU -- BO QUA ---");
            return;
        }
        field.setValue(value == null ? "" : value);
    }

    /**
     * Đọc giá trị 1 field AcroForm theo tên, đã trim; trả về "" nếu field không
     * tồn tại/rỗng/null. Chỉ với field kiểu combo (PDComboBox), getValueAsString()
     * của PDFBox trả về dạng "[Giá trị]" (bọc ngoặc vuông như mảng) thay vì
     * chuỗi thuần -- bóc lớp ngoặc đó ra nếu có. Field kiểu text giữ nguyên giá
     * trị, kể cả khi người dùng gõ dấu "[" "]" thật trong nội dung.
     */
    public static String readField(PDAcroForm acroForm, String name) {
        if (acroForm == null) {
            return "";
        }
        PDField field = acroForm.getField(name);
        if (field == null) {
            return "";
        }
        String value = field.getValueAsString();
        if (value == null) {
            return "";
        }
        value = value.trim();
        if (field instanceof PDComboBox
                && value.length() >= 2 && value.charAt(0) == '[' && value.charAt(value.length() - 1) == ']') {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
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

    /**
     * Vẽ 1 bảng cột cố định (viền + header nền xám + wrap text từng ô) bắt
     * đầu tại (x, topY), trả về y sau khi vẽ xong toàn bộ bảng. Không tự
     * chia trang (không xây layout engine đa trang cho 1 chỗ dùng duy nhất)
     * -- nhưng để tránh vẽ đè lên nội dung tĩnh bên dưới bảng khi có quá
     * nhiều dòng/ghi chú dài, sẽ dừng lại ngay khi dòng tiếp theo vượt quá
     * `minY` và ghi log cảnh báo số dòng bị bỏ qua thay vì âm thầm vẽ tràn.
     */
    public static float drawTable(PDPageContentStream cs, PDFont font, PDFont boldFont, float fontSize,
            float x, float topY, float minY, float[] columnWidths, String[] headers, List<String[]> rows) throws IOException {
        float rowPadding = 4f;
        float lineHeight = fontSize + 3f;
        float tableWidth = sum(columnWidths);
        float y = topY;

        // Header
        float headerHeight = lineHeight + rowPadding * 2;
        cs.setNonStrokingColor(0.93f, 0.93f, 0.93f);
        cs.addRect(x, y - headerHeight, tableWidth, headerHeight);
        cs.fill();
        cs.setNonStrokingColor(0f, 0f, 0f);
        float colX = x;
        for (int c = 0; c < headers.length; c++) {
            drawText(cs, boldFont, fontSize, colX + rowPadding, y - rowPadding - fontSize, headers[c]);
            colX += columnWidths[c];
        }
        drawGridLines(cs, x, y, y - headerHeight, columnWidths, tableWidth);
        y -= headerHeight;

        // Data rows
        for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
            int maxLines = 1;
            List<List<String>> wrappedCells = new ArrayList<>();
            for (int c = 0; c < columnWidths.length; c++) {
                float innerWidth = columnWidths[c] - rowPadding * 2;
                List<String> wrapped = wrapLines(font, fontSize, innerWidth, c < row.length ? row[c] : "");
                wrappedCells.add(wrapped);
                maxLines = Math.max(maxLines, wrapped.size());
            }
            float rowHeight = maxLines * lineHeight + rowPadding * 2;

            if (y - rowHeight < minY) {
                System.err.println("--- CANH BAO: BANG PDF TRAN TRANG -- BO QUA " + (rows.size() - r) + " DONG CON LAI ---");
                break;
            }

            colX = x;
            for (int c = 0; c < columnWidths.length; c++) {
                float textY = y - rowPadding - fontSize;
                for (String line : wrappedCells.get(c)) {
                    drawText(cs, font, fontSize, colX + rowPadding, textY, line);
                    textY -= lineHeight;
                }
                colX += columnWidths[c];
            }
            drawGridLines(cs, x, y, y - rowHeight, columnWidths, tableWidth);
            y -= rowHeight;
        }

        return y;
    }

    private static void drawGridLines(PDPageContentStream cs, float x, float topY, float bottomY,
            float[] columnWidths, float tableWidth) throws IOException {
        cs.setStrokingColor(0.85f, 0.85f, 0.85f);
        cs.setLineWidth(0.5f);
        cs.moveTo(x, topY);
        cs.lineTo(x + tableWidth, topY);
        cs.stroke();
        cs.moveTo(x, bottomY);
        cs.lineTo(x + tableWidth, bottomY);
        cs.stroke();
        float colX = x;
        for (float width : columnWidths) {
            cs.moveTo(colX, topY);
            cs.lineTo(colX, bottomY);
            cs.stroke();
            colX += width;
        }
        cs.moveTo(colX, topY);
        cs.lineTo(colX, bottomY);
        cs.stroke();
    }

    private static float sum(float[] values) {
        float total = 0f;
        for (float v : values) {
            total += v;
        }
        return total;
    }
}
