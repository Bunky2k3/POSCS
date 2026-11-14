package poscs.common;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.service.NotificationService;

/**
 * Lịch chạy nền tự động sinh thông báo nhắc hạn mỗi 60 phút:
 * - Hợp đồng "Sắp hết hạn" (BR-17, ≤ 30 ngày) báo cho người phụ trách.
 * - Phiếu hỗ trợ kỹ thuật sắp/đã quá hạn SLA (≤ 24 giờ) báo cho KTV được giao.
 *
 * Toàn bộ nghiệp vụ sinh thông báo và chống trùng lặp nằm ở {@link NotificationService}.
 */
@WebListener
public class NotificationScheduler implements ServletContextListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationScheduler.class);

    public static final long INITIAL_DELAY_MINUTES = 1;
    public static final long PERIOD_MINUTES = 60;

    private final NotificationService notificationService;
    private ScheduledExecutorService executor;

    public NotificationScheduler() {
        this(new NotificationService());
    }

    public NotificationScheduler(NotificationService notificationService) {
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        executor = createExecutor();
        executor.scheduleAtFixedRate(this::generate, INITIAL_DELAY_MINUTES, PERIOD_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    protected ScheduledExecutorService createExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "notification-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    void generate() {
        // scheduleAtFixedRate huỷ vĩnh viễn các lần chạy sau nếu 1 lần chạy
        // ném exception ra ngoài -- bắt tất cả ở đây để 1 lỗi tạm thời (vd
        // mất kết nối CSDL) không làm chết hẳn lịch nhắc hạn cho các chu kỳ sau.
        try {
            notificationService.generateScheduledNotifications();
        } catch (Exception ex) {
            LOG.error("Lỗi chạy notification scheduler", ex);
        }
    }
}
