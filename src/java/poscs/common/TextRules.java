package poscs.common;

/**
 * Ràng buộc cho các trường văn bản tự do người dùng tự gõ (họ tên, địa chỉ
 * chi tiết...).
 *
 * Lớp phòng thủ chính chống XSS vẫn là escape lúc xuất ra JSP -- đó mới là
 * chỗ sửa đúng, và toàn bộ chỗ xuất đã dùng {@code <c:out>} hoặc
 * {@code fn:escapeXml}. Hàm ở đây là lớp thứ hai: chặn ngay từ đầu vào để dữ
 * liệu nằm trong CSDL luôn sạch, phòng khi sau này có ai thêm một chỗ hiển
 * thị mới mà quên escape.
 *
 * Cố tình chỉ từ chối đúng 3 ký tự phá được ngữ cảnh HTML/thuộc tính, thay vì
 * dùng danh sách cho phép: tên người Việt có dấu, có thể kèm dấu nháy hoặc
 * gạch nối, nên allowlist quá chặt sẽ chặn nhầm tên thật của người dùng.
 */
public final class TextRules {

    private TextRules() {
    }

    /** true nếu giá trị không chứa ký tự phá được ngữ cảnh HTML. null/rỗng coi là hợp lệ (để nơi gọi tự quyết bắt buộc hay không). */
    public static boolean isSafeFreeText(String value) {
        if (value == null) {
            return true;
        }
        return value.indexOf('<') < 0 && value.indexOf('>') < 0 && value.indexOf('"') < 0;
    }
}
