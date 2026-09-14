package poscs.common;

import java.time.LocalDate;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test cho Period -- phần quy "năm + tháng/quý" thành khoảng ngày cụ thể.
 *
 * Đây là chỗ duy nhất trong luồng lọc theo kỳ có tính toán thật (biên của quý,
 * tháng 2 năm nhuận, kỳ liền trước bắc cầu qua năm). Sai một ngày ở đây thì
 * mọi báo cáo theo kỳ lệch theo mà không có gì báo lỗi, nên kiểm đúng hai đầu
 * biên chứ không chỉ kiểm "chạy được".
 */
public class PeriodTest {

    @Test
    public void quarter_coversFirstAndLastDayOfTheThreeMonths() {
        Period q3 = Period.parse("2026", "q3");

        assertEquals("2026-07-01", q3.getFrom().toString());
        assertEquals("2026-09-30", q3.getTo().toString());
        assertEquals("Quý 3/2026", q3.getLabel());
    }

    @Test
    public void quarter4_endsOnNewYearsEve() {
        Period q4 = Period.parse("2026", "q4");

        assertEquals("2026-10-01", q4.getFrom().toString());
        assertEquals("2026-12-31", q4.getTo().toString());
    }

    @Test
    public void month_endsOnTheRealLastDay() {
        assertEquals("2026-02-28", Period.parse("2026", "m2").getTo().toString());
        // 2024 nhuận -- lấy cứng 28 hay 30 ngày đều sai ở đây.
        assertEquals("2024-02-29", Period.parse("2024", "m2").getTo().toString());
        assertEquals("2026-04-30", Period.parse("2026", "m4").getTo().toString());
    }

    @Test
    public void yearWithoutPeriod_coversTheWholeYear() {
        Period year = Period.parse("2026", "");

        assertEquals("2026-01-01", year.getFrom().toString());
        assertEquals("2026-12-31", year.getTo().toString());
        assertEquals("Năm 2026", year.getLabel());
    }

    /** Không chọn năm = không lọc theo kỳ; các DAO hiểu null là "mọi thời điểm". */
    @Test
    public void noYear_meansNoPeriodFilter() {
        assertNull(Period.parse(null, "q1"));
        assertNull(Period.parse("", "q1"));
        assertNull(Period.parse("   ", null));
    }

    /**
     * Tham số rác trên URL không được làm trang vỡ. Năm hỏng thì bỏ lọc; năm
     * đúng mà kỳ hỏng thì lấy trọn năm -- người dùng đã chủ động chọn năm đó.
     */
    @Test
    public void garbageInput_degradesInsteadOfThrowing() {
        assertNull(Period.parse("hai-nghin-hai-sau", "q1"));
        assertNull(Period.parse("12", "q1")); // năm ngoài khoảng hợp lệ

        Period fallback = Period.parse("2026", "q9");
        assertEquals("2026-01-01", fallback.getFrom().toString());
        assertEquals("2026-12-31", fallback.getTo().toString());

        assertEquals("2026-01-01", Period.parse("2026", "linh tinh").getFrom().toString());
    }

    @Test
    public void previousOfQuarter_isTheQuarterBefore() {
        Period previous = Period.parse("2026", "q3").previous();

        assertEquals("2026-04-01", previous.getFrom().toString());
        assertEquals("2026-06-30", previous.getTo().toString());
    }

    /** Kỳ liền trước của quý 1 phải bắc cầu sang năm ngoái, không kẹp về 01/01. */
    @Test
    public void previousOfFirstQuarter_crossesIntoLastYear() {
        Period previous = Period.parse("2026", "q1").previous();

        assertEquals("2025-10-01", previous.getFrom().toString());
        assertEquals("2025-12-31", previous.getTo().toString());
    }

    @Test
    public void previousOfJanuary_isDecemberOfLastYear() {
        Period previous = Period.parse("2026", "m1").previous();

        assertEquals("2025-12-01", previous.getFrom().toString());
        assertEquals("2025-12-31", previous.getTo().toString());
    }

    @Test
    public void availableYears_startsAtThisYearAndGoesBackwards() {
        int thisYear = LocalDate.now().getYear();

        assertEquals(Integer.valueOf(thisYear), Period.availableYears().get(0));
        assertEquals(Integer.valueOf(thisYear - 1), Period.availableYears().get(1));
        assertEquals(5, Period.availableYears().size());
    }
}
