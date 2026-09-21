package poscs.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Đọc số tiền VNĐ người dùng gõ vào form.
 *
 * <p>Chấp nhận cả "1.500.000.000" lẫn "1500000000" -- người Việt gõ dấu chấm
 * phân nhóm theo thói quen, và bắt họ gõ số trần chỉ tạo ra lỗi nhập liệu chứ
 * không tạo ra dữ liệu sạch hơn.
 *
 * <p><b>Lý do lớp này tồn tại thay vì một dòng {@code replaceAll("[.,]", "")}:</b>
 * chuỗi vào không chỉ đến từ bàn phím. Form sửa hợp đồng đổ thẳng
 * {@code contract_value} (kiểu DECIMAL(15,2)) ra ô nhập, nên giá trị mặc định
 * của ô là "980000000.00". Xoá mọi dấu chấm thì chuỗi đó thành 98000000000 --
 * mở form ra, không sửa gì, bấm Lưu là giá trị hợp đồng tự nhân 100, và lần
 * lưu sau lại nhân tiếp. Lỗi này đã xảy ra thật trên bản chạy (21/09/2026).
 *
 * <p>Nên dấu phân nhóm phải được phân biệt với dấu thập phân, theo đúng cách
 * con người đọc:
 * <ul>
 *   <li>Có cả "." lẫn "," -- dấu xuất hiện SAU là dấu thập phân:
 *       "1.500,75" = 1500,75 và "1,500.75" = 1500,75.</li>
 *   <li>Chỉ một loại dấu, nhóm cuối đúng 3 chữ số -- là phân nhóm:
 *       "1.500" = 1500 (quy ước Việt Nam), "980.000.000" = 980 triệu.</li>
 *   <li>Chỉ một loại dấu, nhóm cuối KHÁC 3 chữ số -- là thập phân:
 *       "980000000.00" = 980 triệu, "1.5" = 1,5.</li>
 * </ul>
 *
 * <p>Trả null khi để trống (bản nháp chưa chốt giá) HOẶC khi chuỗi không đọc
 * được -- không ném ra ngoài: một ô tiền gõ sai không đáng làm hỏng cả lần
 * lưu, và phần kiểm tra bắt buộc ở nơi gọi sẽ bắt nếu giá trị đó là bắt buộc.
 *
 * <p>Số âm bị từ chối: giá trị hợp đồng âm không có nghĩa, và nếu lọt vào thì
 * nó âm thầm trừ đi trong mọi phép cộng sau này. Phụ lục giảm trừ KHÔNG đi
 * đường này -- dấu của nó do ô chọn Bổ sung/Giảm trừ quyết định, xem
 * {@code ContractController.parseContractValue}.
 */
public final class MoneyVnd {

    private MoneyVnd() {
    }

    /** Đúng bằng scale của cột DECIMAL(15,2) -- nhập lẻ hơn thì làm tròn ở đây, không để CSDL tự quyết. */
    private static final int SCALE = 2;

    /** Số nguyên, có thể kèm các cụm phân cách: "12", "1.500", "1,500.75". Không nhận ".5" hay "5.". */
    private static final String SHAPE = "[0-9]+([.,][0-9]+)*";

    /**
     * Số tiền đọc được từ {@code raw}, hoặc null nếu để trống / không đọc được
     * / âm.
     */
    public static BigDecimal parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        // Gồm cả khoảng trắng hẹp và non-breaking space: dán từ Excel hay từ
        // một trang web khác rất hay mang theo U+00A0 thay cho dấu cách.
        String s = raw.replaceAll("[\\s\\u00A0\\u202F]", "");
        if (s.isEmpty()) {
            return null;
        }
        if (!s.matches(SHAPE)) {
            // Bắt luôn cả dấu trừ ở đây: "-5" không khớp SHAPE.
            return null;
        }

        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        int sep = Math.max(lastDot, lastComma);

        String intPart = s;
        String fracPart = "";
        if (sep >= 0) {
            String tail = s.substring(sep + 1);
            boolean caHaiLoai = lastDot >= 0 && lastComma >= 0;
            if (caHaiLoai || tail.length() != 3) {
                intPart = stripSeparators(s.substring(0, sep));
                fracPart = tail;
            } else {
                intPart = stripSeparators(s);
            }
        }
        if (intPart.isEmpty()) {
            intPart = "0";
        }

        try {
            BigDecimal value = new BigDecimal(fracPart.isEmpty() ? intPart : intPart + "." + fracPart);
            return value.scale() > SCALE ? value.setScale(SCALE, RoundingMode.HALF_UP) : value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stripSeparators(String s) {
        return s.replace(".", "").replace(",", "");
    }
}
