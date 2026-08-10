package poscs.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DBContext {

    // Cấu hình lấy từ biến môi trường; các giá trị mặc định chỉ dùng cho
    // môi trường dev cục bộ, KHÔNG chứa bất kỳ credential thật nào.
    private static final String DB_URL
            = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/poscs_db");
    private static final String DB_USER_NAME
            = System.getenv().getOrDefault("DB_USER", "root");
    private static final String DB_PASSWORD
            = System.getenv().getOrDefault("DB_PASSWORD", "1234");

    private static final HikariDataSource DATA_SOURCE = buildDataSource();

    private static HikariDataSource buildDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER_NAME);
        config.setPassword(DB_PASSWORD);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        return new HikariDataSource(config);
    }

    /**
     * Lấy một kết nối từ connection pool tới CSDL.
     * @return Một đối tượng Connection, hoặc null nếu có lỗi.
     */
    public static Connection getConnection() {
        try {
            return DATA_SOURCE.getConnection();
        } catch (SQLException ex) {
            System.err.println("--- LOI KET NOI CSDL ---");
            ex.printStackTrace(); // In ra lỗi chi tiết để gỡ rối
            return null;
        }
    }

    /**
     * HÀM MAIN ĐỂ KIỂM TRA KẾT NỐI.
     * Bạn có thể chạy trực tiếp file này để kiểm tra.
     */
    public static void main(String[] args) {
        System.out.println("Dang thuc hien kiem tra ket noi den MySQL...");

        // Cố gắng lấy một kết nối
        try (Connection conn = DBContext.getConnection()) {
            // Kiểm tra kết quả
            if (conn != null) {
                System.out.println("===> KET NOI THANH CONG! <===");
                System.out.println("Thong tin ket noi: " + conn.toString());
            } else {
                printFailureHelp();
            }
        } catch (SQLException e) {
            System.err.println("Loi khi dong ket noi: " + e.getMessage());
        } finally {
            DATA_SOURCE.close();
        }
    }

    private static void printFailureHelp() {
        System.err.println("===> KET NOI THAT BAI! <===");
        System.err.println("Vui long kiem tra lai cac thong tin sau:");
        System.err.println("1. MySQL Server da duoc khoi dong chua?");
        System.err.println("2. Ten CSDL (database name) trong DB_URL co dung khong?");
        System.err.println("3. Username va password co chinh xac khong?");
        System.err.println("4. Thu vien MySQL Connector/J da duoc them vao du an chua?");
    }
}
