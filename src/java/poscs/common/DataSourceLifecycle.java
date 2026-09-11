package poscs.common;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import poscs.dao.DBContext;

/**
 * Dọn sạch mọi thứ phía JDBC khi ứng dụng bị gỡ khỏi container (undeploy,
 * redeploy, hoặc Tomcat dừng hẳn): đóng connection pool, dừng thread nền của
 * driver MySQL, và gỡ đăng ký driver.
 *
 * Cả ba đều là tài nguyên STATIC, nên chúng sống theo classloader của webapp
 * chứ không theo request -- Tomcat gỡ webapp ra không hề đụng tới chúng. Bỏ
 * qua thì mỗi lần redeploy (chuyện xảy ra liên tục lúc phát triển/kiểm thử)
 * để lại 10 kết nối MySQL đang mở, vài thread vẫn chạy, và cả classloader cũ
 * không được thu hồi. Triệu chứng đến muộn và trông chẳng liên quan gì tới
 * nguyên nhân: "Too many connections" ở màn hình nghiệp vụ sau vài chục lần
 * deploy, hoặc OutOfMemoryError: Metaspace.
 *
 * Tách thành listener riêng thay vì gắn vào {@link NotificationScheduler}:
 * hai việc không liên quan gì nhau ngoài chuyện cùng cần một mốc "ứng dụng
 * sắp chết". Không cần bận tâm thứ tự chạy giữa hai listener: nếu pool đóng
 * trước lúc tác vụ nhắc hạn đang chạy dở, câu truy vấn chỉ ném SQLException
 * và generate() đã bắt sẵn.
 */
@WebListener
public class DataSourceLifecycle implements ServletContextListener {

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        DBContext.shutdown();

        // Thread nền dọn connection bị bỏ rơi của Connector/J. Nó giữ tham
        // chiếu tới classloader của webapp, nên còn nó là cả classloader cũ
        // không được thu hồi -- Tomcat báo đúng bằng WARNING "appears to have
        // started a thread ... but has failed to stop it".
        AbandonedConnectionCleanupThread.checkedShutdown();

        // Driver tự đăng ký vào DriverManager (static, dùng chung cho cả JVM)
        // lúc được nạp. Tomcat có tự gỡ hộ, nhưng kèm một WARNING -- tự dọn
        // đúng chỗ thì nhật ký lúc dừng sạch sẽ, không còn cảnh báo nào để
        // người đọc phải phân vân là thật hay nhiễu.
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == getClass().getClassLoader()) {
                try {
                    DriverManager.deregisterDriver(driver);
                } catch (SQLException ex) {
                    sce.getServletContext().log("Khong go duoc dang ky JDBC driver " + driver, ex);
                }
            }
        }
    }
}
