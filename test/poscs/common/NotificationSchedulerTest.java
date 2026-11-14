package poscs.common;

import jakarta.servlet.ServletContextEvent;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import poscs.service.NotificationService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho {@link NotificationScheduler}:
 * - Chu kỳ 60 phút, initial delay 1 phút.
 * - Dọn executor khi contextDestroyed.
 * - Bắt exception khi chạy nền để không làm chết các chu kỳ kế tiếp.
 */
public class NotificationSchedulerTest {

    @Test
    public void constants_periodIs60Minutes() {
        assertEquals(60L, NotificationScheduler.PERIOD_MINUTES);
        assertEquals(1L, NotificationScheduler.INITIAL_DELAY_MINUTES);
    }

    @Test
    public void contextInitializedAndDestroyed_schedulesEvery60MinutesAndShutsDown() {
        NotificationService service = mock(NotificationService.class);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        NotificationScheduler scheduler = new NotificationScheduler(service) {
            @Override
            protected ScheduledExecutorService createExecutor() {
                return executor;
            }
        };

        ServletContextEvent sce = mock(ServletContextEvent.class);
        scheduler.contextInitialized(sce);

        verify(executor).scheduleAtFixedRate(any(Runnable.class), eq(1L), eq(60L), eq(TimeUnit.MINUTES));

        scheduler.contextDestroyed(sce);
        verify(executor).shutdownNow();
    }

    @Test
    public void generate_delegatesToService_andSwallowsException() {
        NotificationService service = mock(NotificationService.class);
        NotificationScheduler scheduler = new NotificationScheduler(service);

        scheduler.generate();
        verify(service).generateScheduledNotifications();

        doThrow(new RuntimeException("DB tam mat ket noi")).when(service).generateScheduledNotifications();
        scheduler.generate();
        verify(service, times(2)).generateScheduledNotifications();
    }
}
