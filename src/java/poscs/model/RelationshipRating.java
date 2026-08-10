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

    public static RelationshipRating fromDbValue(String dbValue) {
        for (RelationshipRating rating : values()) {
            if (rating.dbValue.equals(dbValue)) {
                return rating;
            }
        }
        throw new IllegalArgumentException("Unknown relationship rating: " + dbValue);
    }

    @Override
    public String toString() {
        return dbValue;
    }
}
