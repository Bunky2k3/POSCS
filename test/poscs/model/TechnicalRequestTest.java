package poscs.model;

import java.sql.Timestamp;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test cho hai cờ cảnh báo hạn SLA của phiếu hỗ trợ.
 *
 * Hai giá trị này KHÔNG lưu xuống CSDL mà tính lại theo thời điểm xem, cùng lý
 * do với trạng thái hợp đồng ở ContractDAO.computeStatus: lưu xuống thì hôm sau
 * đã sai. Ngưỡng "sắp tới hạn" là 24 giờ, khớp với câu đếm
 * TechnicalSupportTicketDAO.countOverdueOrDueSoon đang dùng cho Dashboard --
 * hai nơi lệch ngưỡng thì con số trên Dashboard và nhãn trên danh sách sẽ mâu
 * thuẫn nhau.
 */
public class TechnicalRequestTest {

    private static TechnicalRequest ticket(String status, Long deadlineOffsetMillis) {
        TechnicalRequest t = new TechnicalRequest();
        t.setStatus(status);
        if (deadlineOffsetMillis != null) {
            t.setSlaDeadline(new Timestamp(System.currentTimeMillis() + deadlineOffsetMillis));
        }
        return t;
    }

    private static final long HOUR = 60L * 60 * 1000;

    @Test
    public void slaOverdue_deadlinePassedAndStillOpen_isTrue() {
        assertTrue(ticket("Đang xử lý", -HOUR).isSlaOverdue());
    }

    @Test
    public void slaOverdue_deadlinePassedButTicketClosed_isFalse() {
        // Phiếu đã đóng thì không còn gì để trễ -- nếu vẫn báo quá hạn thì
        // danh sách đầy nhãn đỏ cho việc đã xong, người trực bỏ qua hết.
        assertFalse(ticket("Đã đóng", -HOUR).isSlaOverdue());
    }

    @Test
    public void slaOverdue_noDeadline_isFalse() {
        assertFalse(ticket("Đang xử lý", null).isSlaOverdue());
    }

    @Test
    public void slaOverdue_deadlineStillAhead_isFalse() {
        assertFalse(ticket("Đang xử lý", 2 * HOUR).isSlaOverdue());
    }

    @Test
    public void slaDueSoon_lessThan24HoursLeft_isTrue() {
        assertTrue(ticket("Mới tiếp nhận", 2 * HOUR).isSlaDueSoon());
    }

    @Test
    public void slaDueSoon_exactlyAtThreshold_isTrue() {
        // 24 giờ là giá trị biên, phải nằm TRONG vùng cảnh báo để khớp với
        // điều kiện "<= NOW() + INTERVAL 24 HOUR" của câu đếm trên Dashboard.
        assertTrue(ticket("Mới tiếp nhận", 24 * HOUR - 1000).isSlaDueSoon());
    }

    @Test
    public void slaDueSoon_moreThan24HoursLeft_isFalse() {
        assertFalse(ticket("Mới tiếp nhận", 30 * HOUR).isSlaDueSoon());
    }

    @Test
    public void slaDueSoon_alreadyOverdue_isFalseSoOnlyOneBadgeShows() {
        TechnicalRequest t = ticket("Đang xử lý", -HOUR);
        assertTrue(t.isSlaOverdue());
        assertFalse("Quá hạn rồi thì không hiện thêm nhãn 'sắp tới hạn'", t.isSlaDueSoon());
    }

    @Test
    public void slaDueSoon_ticketClosed_isFalse() {
        assertFalse(ticket("Đã đóng", 2 * HOUR).isSlaDueSoon());
    }
}
