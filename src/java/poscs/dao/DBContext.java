package poscs.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBContext {

    // Các thông số kết nối CSDL đã được cập nhật


//    private static final String DB_URL = "jdbc:mysql://isp490-su25-g4-mysql-server.mysql.database.azure.com:3306/ISP490_SU25_G4?useSSL=true&requireSSL=false";
//    private static final String DB_USER_NAME = "crmadmin";
//    private static final String DB_PASSWORD = "Admin12#$";

//
//
    private static final String DB_URL = "jdbc:mysql://localhost:3306/poscs_db";
    private static final String DB_USER_NAME = "root";
    private static final String DB_PASSWORD = "1234";

//        private static final String DB_URL = "jdbc:mysql://localhost:3306/datn2";
//    private static final String DB_USER_NAME = "root";
//    private static final String DB_PASSWORD = "1234";

    /**
     * Phương thức static để lấy một kết nối mới tới CSDL.
     * @return Một đối tượng Connection, hoặc null nếu có lỗi.
     */
    public static Connection getConnection() {
        try {

            // Nạp driver của MySQL
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // Trả về một kết nối mới sử dụng các thông tin đã khai báo ở trên
            return DriverManager.getConnection(DB_URL, DB_USER_NAME, DB_PASSWORD);
            
        } catch (ClassNotFoundException | SQLException ex) {
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
        Connection conn = DBContext.getConnection();

        // Kiểm tra kết quả
        if (conn != null) {
            System.out.println("===> KET NOI THANH CONG! <===");
            System.out.println("Thong tin ket noi: " + conn.toString());
            try {
                // Luôn đóng kết nối sau khi kiểm tra xong
                conn.close();
                System.out.println("Da dong ket noi.");
            } catch (SQLException e) {
                System.err.println("Loi khi dong ket noi: " + e.getMessage());
            }
        } else {
            System.err.println("===> KET NOI THAT BAI! <===");
            System.err.println("Vui long kiem tra lai cac thong tin sau:");
            System.err.println("1. MySQL Server da duoc khoi dong chua?");
            // Đã cập nhật lại tên CSDL trong thông báo lỗi
            System.err.println("2. Ten CSDL (database name) trong DB_URL co dung  khong?");
            System.err.println("3. Username va password co chinh xac khong?");
            System.err.println("4. Thu vien MySQL Connector/J da duoc them vao du an chua?");
        }
    }
}