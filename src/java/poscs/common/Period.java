package poscs.common;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Một kỳ báo cáo (tháng hoặc quý của một năm) đã quy về khoảng ngày cụ thể,
 * dùng chung cho các bộ lọc "theo tháng / theo quý" ở trang tổng quan, danh
 * sách hợp đồng và danh sách phiếu hỗ trợ.
 *
 * <p>Quy về [from, to] ngay tại tầng Java thay vì để SQL gọi YEAR()/QUARTER()
 * trên cột ngày vì hai lý do: hàm bọc quanh cột thì MySQL không dùng được
 * index trên cột đó, và mỗi màn hình lọc theo một cột ngày khác nhau (hợp
 * đồng theo ngày ký, phiếu theo ngày tạo, doanh thu theo ngày thanh toán) --
 * có sẵn một khoảng ngày thì mọi nơi chỉ cần "cột BETWEEN ? AND ?".
 *
 * <p>Tháng và quý cố ý gộp chung vào một tham số "period" thay vì hai tham số
 * riêng: người dùng chỉ chọn được một trong hai, tách ra thì phải xử lý thêm
 * trường hợp vô nghĩa "quý 2 và tháng 7".
 */
public final class Period {

    /** Bao nhiêu năm gần đây được liệt kê trong dropdown chọn năm. */
    private static final int YEARS_IN_DROPDOWN = 5;

    private final int year;
    private final Date from;
    private final Date to;
    private final String label;

    private Period(int year, LocalDate from, LocalDate to, String label) {
        this.year = year;
        this.from = Date.valueOf(from);
        this.to = Date.valueOf(to);
        this.label = label;
    }

    /**
     * Dựng kỳ từ tham số trên URL. Trả về null khi không lọc theo kỳ -- tức là
     * khi không chọn năm; các DAO hiểu null là "mọi thời điểm".
     *
     * @param yearParam   năm, vd "2026"; rỗng/không hợp lệ = không lọc
     * @param periodParam "q1".."q4" cho quý, "m1".."m12" cho tháng, rỗng = trọn năm
     */
    public static Period parse(String yearParam, String periodParam) {
        Integer year = parseIntOrNull(yearParam);
        if (year == null || year < 1970 || year > 9999) {
            return null;
        }
        String p = periodParam != null ? periodParam.trim().toLowerCase() : "";

        if (p.startsWith("q")) {
            Integer q = parseIntOrNull(p.substring(1));
            if (q != null && q >= 1 && q <= 4) {
                int firstMonth = (q - 1) * 3 + 1;
                LocalDate from = LocalDate.of(year, firstMonth, 1);
                LocalDate to = from.plusMonths(2).withDayOfMonth(from.plusMonths(2).lengthOfMonth());
                return new Period(year, from, to, "Quý " + q + "/" + year);
            }
        } else if (p.startsWith("m")) {
            Integer m = parseIntOrNull(p.substring(1));
            if (m != null && m >= 1 && m <= 12) {
                LocalDate from = LocalDate.of(year, m, 1);
                return new Period(year, from, from.withDayOfMonth(from.lengthOfMonth()),
                        "Tháng " + m + "/" + year);
            }
        }
        // Chọn năm nhưng không chọn tháng/quý (hoặc giá trị rác) -- lấy trọn năm
        // thay vì bỏ lọc, vì người dùng đã chủ động chọn một năm cụ thể.
        return new Period(year, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), "Năm " + year);
    }

    /**
     * Kỳ liền trước cùng độ dài (tháng trước / quý trước / năm trước), phục vụ
     * ô so sánh tăng trưởng ở trang tổng quan.
     */
    public Period previous() {
        LocalDate f = from.toLocalDate();
        LocalDate t = to.toLocalDate();
        long months = (t.getYear() - f.getYear()) * 12L + (t.getMonthValue() - f.getMonthValue()) + 1;
        LocalDate newFrom = f.minusMonths(months);
        LocalDate newTo = f.minusDays(1);
        return new Period(newFrom.getYear(), newFrom, newTo, "kỳ trước");
    }

    /** Danh sách năm cho dropdown: năm nay và vài năm gần đây. */
    public static List<Integer> availableYears() {
        int thisYear = LocalDate.now().getYear();
        List<Integer> years = new ArrayList<>();
        for (int y = thisYear; y > thisYear - YEARS_IN_DROPDOWN; y--) {
            years.add(y);
        }
        return years;
    }

    public int getYear() { return year; }

    /** Ngày đầu kỳ (đã bao gồm) -- dùng trực tiếp cho "cột >= ?". */
    public Date getFrom() { return from; }

    /** Ngày cuối kỳ (đã bao gồm) -- dùng trực tiếp cho "cột <= ?". */
    public Date getTo() { return to; }

    /** Nhãn tiếng Việt để hiển thị, vd "Quý 3/2026". */
    public String getLabel() { return label; }

    private static Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "Period{" + label + ": " + from + " -> " + to + '}';
    }
}
