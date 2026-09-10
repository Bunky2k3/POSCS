package poscs.model;

/**
 * Ánh xạ với ENUM('Tốt','Cần theo dõi','Xấu','Có nguy cơ rời bỏ')
 * trong các cột enterprises.current_relationship_rating và
 * customer_lifecycle_events.relationship_rating.
 */
public enum RelationshipRating {
    GOOD("Tốt"),
    NEEDS_REVIEW("Cần theo dõi"),
    BAD("Xấu"),
    AT_RISK("Có nguy cơ rời bỏ");

    private final String dbValue;

    RelationshipRating(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    /**
     * Đọc ngược giá trị lưu trong CSDL thành hằng số enum.
     *
     * Trả về null khi gặp giá trị lạ (hoặc null) thay vì ném exception: hàm
     * này chạy trong vòng lặp map từng dòng ResultSet, nên một dòng dữ liệu
     * bất thường mà ném lên sẽ làm hỏng CẢ danh sách khách hàng (HTTP 500),
     * chứ không phải chỉ hỏng đúng dòng đó. Cột trong CSDL là ENUM nên bình
     * thường không thể lệch, nhưng vẫn xảy ra khi dữ liệu được nạp bằng công
     * cụ làm sai mã hoá -- lúc đó chuỗi đọc lên là "CÃ³ nguy cÆ¡..." và không
     * khớp hằng số nào.
     */
    public static RelationshipRating fromDbValue(String dbValue) {
        for (RelationshipRating rating : values()) {
            if (rating.dbValue.equals(dbValue)) {
                return rating;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return dbValue;
    }
}
