package poscs.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import poscs.model.ContractPayment;
import poscs.model.RelationshipRating;

/**
 * Áp các ngưỡng trong customer_evaluation_rules (xem db/schema.sql) vào lịch
 * sử thanh toán (contract_payments) của 1 khách hàng để suy ra
 * RelationshipRating -- đây là engine tính điểm còn thiếu mà DEPLOY.md/
 * README.md nói tới, trước đây bảng/model đã có sẵn nhưng chưa có nơi nào
 * thực sự chạy rule.
 *
 * Thứ tự ưu tiên (từ nặng nhất): có hoá đơn quá hạn CHƯA thu tiền -> AT_RISK;
 * phần lớn hoá đơn đã thu bị trả trễ -> BAD; mua ít hoặc thỉnh thoảng trả trễ
 * -> NEEDS_REVIEW; còn lại (thanh toán đều đặn, đúng/sớm hạn) -> GOOD.
 */
public final class CustomerEvaluator {

    private CustomerEvaluator() {
    }

    public static final class Result {
        private final RelationshipRating rating;
        private final String reason;

        public Result(RelationshipRating rating, String reason) {
            this.rating = rating;
            this.reason = reason;
        }

        public RelationshipRating getRating() { return rating; }
        public String getReason() { return reason; }
    }

    public static Result evaluate(List<ContractPayment> payments, Map<String, BigDecimal> rules) {
        BigDecimal lowThreshold = rules.getOrDefault("LOW_PURCHASE_THRESHOLD", BigDecimal.ZERO);
        BigDecimal highThreshold = rules.getOrDefault("HIGH_PURCHASE_THRESHOLD", BigDecimal.ZERO);
        long lateDays = rules.getOrDefault("LATE_PAYMENT_DAYS", BigDecimal.valueOf(5)).longValue();

        if (payments.isEmpty()) {
            return new Result(RelationshipRating.NEEDS_REVIEW,
                    "Khách hàng chưa có hoá đơn/lịch sử thanh toán nào để đánh giá.");
        }

        LocalDate today = LocalDate.now();
        BigDecimal totalPaid = BigDecimal.ZERO;
        int paidCount = 0;
        int lateCount = 0;
        int overdueUnpaidCount = 0;
        long maxOverdueDays = 0;

        for (ContractPayment p : payments) {
            if (p.isPaid()) {
                paidCount++;
                totalPaid = totalPaid.add(p.getInvoiceAmount());
                Long diff = p.getDaysLateOrEarly();
                if (diff != null && diff >= lateDays) {
                    lateCount++;
                }
            } else {
                long overdueDays = ChronoUnit.DAYS.between(p.getDueDate().toLocalDate(), today);
                if (overdueDays >= lateDays) {
                    overdueUnpaidCount++;
                    maxOverdueDays = Math.max(maxOverdueDays, overdueDays);
                }
            }
        }

        if (overdueUnpaidCount > 0) {
            return new Result(RelationshipRating.AT_RISK,
                    String.format("Có %d hoá đơn quá hạn %d+ ngày vẫn chưa thanh toán (trễ nhất %d ngày).",
                            overdueUnpaidCount, lateDays, maxOverdueDays));
        }

        if (paidCount > 0 && lateCount * 2 > paidCount) {
            return new Result(RelationshipRating.BAD,
                    String.format("%d/%d hoá đơn đã thanh toán bị trả trễ từ %d ngày trở lên.",
                            lateCount, paidCount, lateDays));
        }

        if (totalPaid.compareTo(lowThreshold) < 0) {
            return new Result(RelationshipRating.NEEDS_REVIEW,
                    String.format("Tổng tiền đã thu (%s đ) dưới ngưỡng \"mua ít\" (%s đ).",
                            formatVnd(totalPaid), formatVnd(lowThreshold)));
        }

        if (lateCount > 0) {
            return new Result(RelationshipRating.NEEDS_REVIEW,
                    String.format("%d/%d hoá đơn từng bị thanh toán trễ hạn -- cần theo dõi thêm.",
                            lateCount, paidCount));
        }

        if (totalPaid.compareTo(highThreshold) >= 0) {
            return new Result(RelationshipRating.GOOD,
                    String.format("Khách hàng lớn (đã thu %s đ), toàn bộ %d hoá đơn đều đúng/sớm hạn.",
                            formatVnd(totalPaid), paidCount));
        }

        return new Result(RelationshipRating.GOOD,
                String.format("Thanh toán đầy đủ, đúng/sớm hạn (đã thu %s đ).", formatVnd(totalPaid)));
    }

    private static String formatVnd(BigDecimal amount) {
        return String.format(Locale.forLanguageTag("vi-VN"), "%,.0f", amount);
    }
}
