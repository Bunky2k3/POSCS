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
        // Bắt buộc khai báo rõ driver: chạy trong Tomcat mỗi webapp có
        // classloader riêng, HikariCP không tự tìm thấy driver qua
        // DriverManager service-loader như khi chạy "java -cp" trực tiếp.
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setUsername(DB_USER_NAME);
        config.setPassword(DB_PASSWORD);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        // Tắt fail-fast lúc khởi tạo pool: mặc định HikariCP thử kết nối
        // ngay khi tạo HikariDataSource và ném lỗi nếu không kết nối được.
        // Vì DATA_SOURCE là static final, lỗi đó sẽ làm hỏng vĩnh viễn
        // việc load class DBContext (ExceptionInInitializerError rồi
        // NoClassDefFoundError ở mọi lần gọi sau) nếu CSDL chưa sẵn sàng
        // đúng lúc Tomcat khởi động -- kể cả khi CSDL sống lại sau đó.
        // -1 = không chờ/không fail lúc khởi tạo; lỗi kết nối chỉ ném ra
        // ở getConnection(), nơi DAO đã có catch (SQLException) sẵn.
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    /**
     * Lấy một kết nối từ connection pool tới CSDL.
     * Ném SQLException khi không kết nối được thay vì trả về null --
     * mọi lời gọi trong DAO đều nằm trong try-with-resources kèm
     * catch (SQLException), nên để ngoại lệ thoát ra tự nhiên thay vì
     * nuốt thành null (nuốt thành null từng gây NullPointerException ở
     * chỗ gọi conn.prepareStatement() khi CSDL bị mất kết nối).
     * @return Một đối tượng Connection.
     * @throws SQLException nếu không lấy được kết nối từ pool.
     */
    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    /**
     * HÀM MAIN ĐỂ KIỂM TRA KẾT NỐI.
     * Bạn có thể chạy trực tiếp file này để kiểm tra.
     */
    public static void main(String[] args) {
        System.out.println("Dang thuc hien kiem tra ket noi den MySQL...");

        // Cố gắng lấy một kết nối
        try (Connection conn = DBContext.getConnection()) {
            System.out.println("===> KET NOI THANH CONG! <===");
            System.out.println("Thong tin ket noi: " + conn.toString());
        } catch (SQLException e) {
            System.err.println("Loi khi ket noi: " + e.getMessage());
            printFailureHelp();
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
