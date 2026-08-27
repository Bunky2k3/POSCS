package poscs.scheduler;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import poscs.dao.ContractDAO;
import poscs.dao.NotificationDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Contract;
import poscs.model.TechnicalRequest;

/**
 * Tự động sinh thông báo nhắc hạn -- hợp đồng "Sắp hết hạn" (BR-17) báo cho
 * người phụ trách, phiếu hỗ trợ kỹ thuật sắp/đã quá hạn SLA báo cho kỹ thuật
 * viên được giao -- thay vì chỉ hiển thị trên dashboard mà không ai chủ động
 * được báo (FE-08 trước đây chỉ có "xem danh sách", chưa có nơi nào tự tạo
 * thông báo mới, xem javadoc cũ của NotificationDAO). Chạy định kỳ bằng 1
 * ScheduledExecutorService thay vì cron ngoài OS, vì ứng dụng chỉ triển khai
 * dạng WAR đơn giản trong 1 servlet container, không có hạ tầng lập lịch
 * riêng.
 *
 * Idempotent theo (user_id, ref_type, ref_id) -- xem NotificationDAO -- nên
 * chạy lại nhiều lần (mỗi lần khởi động lại server, hoặc mỗi chu kỳ định kỳ)
 * không tạo thông báo trùng lặp cho cùng 1 hợp đồng/phiếu.
 */
@WebListener
public class NotificationScheduler implements ServletContextListener {

    private static final long INITIAL_DELAY_MINUTES = 1;
    private static final long PERIOD_MINUTES = 60;

    private ScheduledExecutorService executor;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "notification-scheduler");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::generate, INITIAL_DELAY_MINUTES, PERIOD_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void generate() {
        // scheduleAtFixedRate huỷ vĩnh viễn các lần chạy sau nếu 1 lần chạy
        // ném exception ra ngoài -- bắt tất cả ở đây để 1 lỗi tạm thời (vd
        // mất kết nối CSDL) không làm chết hẳn lịch nhắc hạn cho các chu kỳ
        // sau.
        try {
            generateContractExpiringNotifications();
            generateTicketSlaNotifications();
        } catch (Exception ex) {
            System.err.println("--- LOI CHAY NOTIFICATION SCHEDULER ---");
            ex.printStackTrace();
        }
    }

    private void generateContractExpiringNotifications() {
        ContractDAO contractDAO = new ContractDAO();
        NotificationDAO notificationDAO = new NotificationDAO();
        List<Contract> expiringSoon = contractDAO.findExpiringSoon(Integer.MAX_VALUE);
        for (Contract c : expiringSoon) {
            if (c.getOwnerId() <= 0) {
                continue;
            }
            if (notificationDAO.existsForUserAndRef(c.getOwnerId(), "contract_expiring", c.getContractId())) {
                continue;
            }
            long daysLeft = daysUntil(c.getEndDate());
            String title = "Hợp đồng " + c.getContractCode() + " sắp hết hạn"
                    + (daysLeft >= 0 ? " (còn " + daysLeft + " ngày)." : ".");
            notificationDAO.insert(c.getOwnerId(), title, "contract_expiring", c.getContractId());
        }
    }

    private void generateTicketSlaNotifications() {
        TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();
        NotificationDAO notificationDAO = new NotificationDAO();
        List<TechnicalRequest> dueSoon = ticketDAO.findOverdueOrDueSoon();
        for (TechnicalRequest t : dueSoon) {
            if (t.getAssignedTechnicianId() <= 0) {
                continue;
            }
            if (notificationDAO.existsForUserAndRef(t.getAssignedTechnicianId(), "ticket_sla", t.getTicketId())) {
                continue;
            }
            String title = "Phiếu " + t.getTicketCode() + " sắp/đã quá hạn xử lý SLA.";
            notificationDAO.insert(t.getAssignedTechnicianId(), title, "ticket_sla", t.getTicketId());
        }
    }

    private long daysUntil(java.sql.Date endDate) {
        if (endDate == null) {
            return -1;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), endDate.toLocalDate());
    }
}
