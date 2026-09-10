package poscs.model;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test cho RelationshipRating -- enum xếp hạng quan hệ khách hàng, ánh xạ hai
 * chiều với cột ENUM trong CSDL.
 *
 * Trọng tâm: fromDbValue() KHÔNG được ném exception khi gặp giá trị lạ. Hàm
 * này chạy trong vòng lặp map từng dòng ResultSet của CustomerDAO, nên ném
 * lên là hỏng CẢ trang danh sách khách hàng (HTTP 500) chứ không phải chỉ
 * hỏng đúng dòng dữ liệu bất thường đó.
 *
 * JUnit 4 -- xem CustomerControllerTest.
 */
public class RelationshipRatingTest {

    @Test
    public void fromDbValue_roundTripsEveryConstant() {
        for (RelationshipRating rating : RelationshipRating.values()) {
            assertSame("Xếp hạng " + rating + " phải đọc ngược được từ chính giá trị nó ghi xuống",
                    rating, RelationshipRating.fromDbValue(rating.getDbValue()));
        }
    }

    @Test
    public void fromDbValue_unknownValue_returnsNullInsteadOfThrowing() {
        assertNull(RelationshipRating.fromDbValue("Hạng nào đó không có thật"));
    }

    @Test
    public void fromDbValue_mojibakeValue_returnsNullInsteadOfThrowing() {
        // Đúng chuỗi thu được khi "Có nguy cơ rời bỏ" bị nạp vào CSDL bằng công
        // cụ làm sai mã hoá -- trường hợp thật đã gặp khi dựng môi trường test.
        assertNull(RelationshipRating.fromDbValue("CÃ³ nguy cÆ¡ rá»i bá»"));
    }

    @Test
    public void fromDbValue_null_returnsNull() {
        // Cột cho phép NULL (khách hàng chưa được đánh giá lần nào), nên caller
        // gọi thẳng với giá trị đọc từ ResultSet mà không cần tự kiểm tra null.
        assertNull(RelationshipRating.fromDbValue(null));
    }

    @Test
    public void fromDbValue_isCaseAndWhitespaceSensitive() {
        // Không "đoán ý" giá trị gần đúng: sai hoa thường hay thừa khoảng trắng
        // nghĩa là dữ liệu có vấn đề, phải hiện ra là chưa xếp hạng chứ không
        // được âm thầm gán vào một hạng nào đó.
        assertNull(RelationshipRating.fromDbValue("tốt"));
        assertNull(RelationshipRating.fromDbValue(" Tốt "));
    }

    @Test
    public void getDbValue_matchesEnumColumnInSchema() {
        assertEquals("Tốt", RelationshipRating.GOOD.getDbValue());
        assertEquals("Cần theo dõi", RelationshipRating.NEEDS_REVIEW.getDbValue());
        assertEquals("Xấu", RelationshipRating.BAD.getDbValue());
        assertEquals("Có nguy cơ rời bỏ", RelationshipRating.AT_RISK.getDbValue());
    }
}
