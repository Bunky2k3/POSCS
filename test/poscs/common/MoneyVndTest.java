package poscs.common;

import java.math.BigDecimal;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test cho MoneyVnd -- đọc số tiền người dùng gõ vào ô VNĐ.
 *
 * Ca đáng giá nhất là {@link #chuoiTuCsdlKhongBiNhan100()}: form sửa hợp đồng
 * đổ thẳng DECIMAL(15,2) ra ô nhập nên chuỗi vào là "980000000.00". Bản cũ xoá
 * mọi dấu chấm nên mở form ra, không sửa gì, bấm Lưu là giá trị hợp đồng nhân
 * 100 -- đã xảy ra thật trên bản chạy ngày 21/09/2026.
 *
 * So sánh bằng compareTo chứ không assertEquals: BigDecimal("980000000") và
 * BigDecimal("980000000.00") KHÁC nhau theo equals (khác scale) nhưng cùng một
 * số tiền, và cái cần khẳng định ở đây là số tiền.
 *
 * JUnit 4 -- xem CustomerControllerTest.
 */
public class MoneyVndTest {

    private static void bang(String mongDoi, String raw) {
        BigDecimal thucTe = MoneyVnd.parseOrNull(raw);
        assertNotNull("đọc được: " + raw, thucTe);
        assertEquals("đọc " + raw, 0, new BigDecimal(mongDoi).compareTo(thucTe));
    }

    // ------------------------------------------------------------------
    // Chỗ lỗi đã xảy ra
    // ------------------------------------------------------------------

    @Test
    public void parseOrNull_chuoiTuCsdl_khongBiNhan100() {
        // Giá trị mặc định của ô sửa, do JSP đổ BigDecimal scale 2 ra.
        bang("980000000", "980000000.00");
        bang("1500000000", "1500000000.00");
        // Và lần lưu thứ hai: nếu bản cũ đã nhân 100 thì chuỗi này còn dài hơn.
        bang("98000000000", "98000000000.00");
    }

    @Test
    public void parseOrNull_dauPhayThapPhan_khongBiNhan100() {
        // Cùng một bản ghi nhưng locale khác thì JSP có thể ra dấu phẩy.
        bang("980000000", "980000000,00");
    }

    // ------------------------------------------------------------------
    // Người gõ tay
    // ------------------------------------------------------------------

    @Test
    public void parseOrNull_soTran_docDuoc() {
        bang("1500000000", "1500000000");
        bang("0", "0");
    }

    @Test
    public void parseOrNull_dauChamPhanNhomKieuVietNam_docDung() {
        bang("1500000000", "1.500.000.000");
        bang("980000000", "980.000.000");
    }

    @Test
    public void parseOrNull_dauPhayPhanNhomKieuAnhMy_docDung() {
        // Có người quen gõ kiểu này, và Excel dán ra cũng hay ra dạng này.
        bang("1500000000", "1,500,000,000");
    }

    @Test
    public void parseOrNull_nhomCuoiDuBaChuSo_hieuLaPhanNhom() {
        // "1.500" ở Việt Nam là một nghìn rưỡi, không phải một phẩy năm.
        bang("1500", "1.500");
        bang("1500", "1,500");
    }

    @Test
    public void parseOrNull_nhomCuoiKhacBaChuSo_hieuLaThapPhan() {
        bang("1.5", "1.5");
        bang("1.5", "1,5");
    }

    @Test
    public void parseOrNull_caHaiLoaiDau_dauSauLaThapPhan() {
        bang("1500.75", "1.500,75");
        bang("1500.75", "1,500.75");
    }

    @Test
    public void parseOrNull_chuoiCoKhoangTrang_boQuaKhoangTrang() {
        bang("980000000", " 980 000 000 ");
        // Dán từ Excel / trang web hay mang theo non-breaking space.
        bang("980000000", "980 000 000");
    }

    @Test
    public void parseOrNull_nhieuHonHaiChuSoThapPhan_lamTron() {
        // Cột là DECIMAL(15,2); làm tròn ở đây để CSDL không tự quyết hộ.
        bang("1234.57", "1234.5678");
    }

    // ------------------------------------------------------------------
    // Trả null
    // ------------------------------------------------------------------

    @Test
    public void parseOrNull_chuoiRong_traNull() {
        // Bản nháp chưa chốt giá là hợp lệ, không phải lỗi nhập liệu.
        assertNull(MoneyVnd.parseOrNull(null));
        assertNull(MoneyVnd.parseOrNull(""));
        assertNull(MoneyVnd.parseOrNull("   "));
    }

    @Test
    public void parseOrNull_chuoiKhongDocDuoc_traNullChuKhongNem() {
        // Một ô tiền gõ sai không đáng làm hỏng cả lần lưu.
        assertNull(MoneyVnd.parseOrNull("abc"));
        assertNull(MoneyVnd.parseOrNull("12abc"));
        assertNull(MoneyVnd.parseOrNull("1.500 đ"));
        assertNull(MoneyVnd.parseOrNull("."));
        assertNull(MoneyVnd.parseOrNull(".5"));
        assertNull(MoneyVnd.parseOrNull("5."));
    }

    @Test
    public void parseOrNull_soAm_biTuChoi() {
        // Giá trị hợp đồng âm không có nghĩa; phụ lục giảm trừ lấy dấu từ ô
        // chọn Bổ sung/Giảm trừ chứ không từ dấu trừ người dùng gõ.
        assertNull(MoneyVnd.parseOrNull("-5"));
        assertNull(MoneyVnd.parseOrNull("-1.500.000"));
    }
}
