package poscs.integration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.Assume;
import poscs.dao.DBContext;

/**
 * Hạ tầng cho các test TÍCH HỢP -- chạy DAO trên MySQL THẬT, khác hẳn các
 * test DAO còn lại vốn mock JDBC (xem {@code poscs.dao.JdbcStub}).
 *
 * <h3>Vì sao cần</h3>
 * Test mock không bao giờ thực thi câu SQL nào, nên KHÔNG phát hiện được:
 * sai tên cột, SQL sai cú pháp, lệch schema, vi phạm khoá ngoại/UNIQUE,
 * transaction không rollback, hay hai cách tính cùng một quy tắc ở hai nơi
 * (vd trạng thái hợp đồng tính bằng Java trong {@code computeStatus()} và
 * bằng SQL trong {@code STATUS_CASE_SQL}) bị lệch nhau. Đó đúng là loại lỗi
 * đã lọt qua toàn bộ bộ test trước đây.
 *
 * <h3>Chạy ở đâu</h3>
 * Trỏ vào một CSDL DÙNG-MỘT-LẦN, mặc định {@code poscs_it} -- KHÔNG BAO GIỜ
 * chạy trên poscs_db, vì mỗi lớp test đều xoá sạch bảng trước khi chạy.
 * Đổi bằng system property khi gọi Ant:
 *
 * <pre>
 * ant -Dtest-sys-prop.DB_URL=jdbc:mysql://localhost:3306/poscs_it \
 *     -Dtest-sys-prop.DB_USER=root -Dtest-sys-prop.DB_PASSWORD=1234 test
 * </pre>
 *
 * <h3>Không có MySQL thì sao</h3>
 * Mọi test tích hợp gọi {@link #assumeAvailable()} ở đầu; không kết nối được
 * thì JUnit đánh dấu SKIP chứ không FAIL. Nhờ vậy {@code ant test} trên máy
 * chưa cài MySQL vẫn xanh, và người mới clone repo không bị chặn.
 *
 * <h3>Chốt an toàn</h3>
 * {@link #resetSchema()} TỪ CHỐI chạy nếu tên CSDL không kết thúc bằng
 * "_it" -- một lần gõ nhầm biến môi trường là đủ để xoá sạch dữ liệu thật.
 */
public final class IntegrationDb {

    /** Đường dẫn tới file schema dùng chung với môi trường thật. */
    private static final String SCHEMA_FILE = "db/schema.sql";

    private static Boolean available;
    private static boolean schemaLoaded;

    private IntegrationDb() {
    }

    /**
     * Bỏ qua (SKIP, không FAIL) test hiện tại nếu không có CSDL tích hợp.
     * Gọi ở đầu mỗi test tích hợp.
     */
    public static void assumeAvailable() {
        Assume.assumeTrue("Bỏ qua test tích hợp: cần một CSDL dùng-một-lần có tên kết thúc "
                + "bằng \"_it\". Hiện đang trỏ tới " + jdbcUrl() + ". Bật bằng: "
                + "  ant -Dtest-sys-prop.DB_URL=jdbc:mysql://localhost:3306/poscs_it "
                + "-Dtest-sys-prop.DB_USER=root -Dtest-sys-prop.DB_PASSWORD=... test",
                isAvailable());
    }

    /**
     * Chỉ coi là "có sẵn" khi vừa kết nối được VỪA đang trỏ tới CSDL
     * dùng-một-lần.
     *
     * Điều kiện thứ hai mới là điều kiện quan trọng: mặc định DBContext trỏ
     * vào poscs_db -- CSDL THẬT. Nếu chỉ kiểm "kết nối được" thì một lần chạy
     * `ant test` bình thường trên máy có MySQL sẽ khởi động test tích hợp
     * ngay trên dữ liệu thật. requireDisposableDatabase() sẽ chặn trước khi
     * kịp xoá gì, nhưng khi đó cả lớp test ERROR -- `ant test` và CI đỏ vì lý
     * do chẳng liên quan tới code. Nên ở đây SKIP, không phải FAIL.
     */
    private static synchronized boolean isAvailable() {
        if (available == null) {
            try (Connection conn = DBContext.getConnection()) {
                String catalog = conn.getCatalog();
                available = conn.isValid(5) && catalog != null && catalog.endsWith("_it");
            } catch (SQLException ex) {
                available = false;
            }
        }
        return available;
    }

    private static String jdbcUrl() {
        String fromProperty = System.getProperty("DB_URL");
        if (fromProperty != null && !fromProperty.isEmpty()) {
            return fromProperty;
        }
        return System.getenv().getOrDefault("DB_URL", "(mặc định)");
    }

    /**
     * Nạp lại toàn bộ schema, chỉ đúng MỘT LẦN cho cả lượt chạy test.
     *
     * Không nạp lại ở từng lớp test vì db/schema.sql rất lớn (hàng nghìn dòng
     * seed sản phẩm/địa giới) -- nạp lại mỗi lớp sẽ kéo dài lượt chạy vô ích.
     * Việc dọn dữ liệu giữa các test do {@link #clearBusinessData()} lo.
     */
    public static synchronized void resetSchema() throws Exception {
        if (schemaLoaded) {
            return;
        }
        requireDisposableDatabase();

        File schema = new File(SCHEMA_FILE);
        if (!schema.isFile()) {
            throw new IllegalStateException("Không thấy " + schema.getAbsolutePath()
                    + " -- test tích hợp phải chạy từ thư mục gốc của dự án.");
        }
        try (Connection conn = DBContext.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String statement : readStatements(schema)) {
                st.execute(statement);
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
        schemaLoaded = true;
    }

    /**
     * Chặn chạy nhầm lên CSDL thật. Tên CSDL phải kết thúc bằng "_it".
     *
     * poscs_db là dữ liệu thật của người dùng và resetSchema() DROP toàn bộ
     * bảng -- không có chốt này thì một biến môi trường còn sót từ lần chạy
     * ứng dụng trước là đủ để xoá sạch.
     */
    private static void requireDisposableDatabase() throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            String catalog = conn.getCatalog();
            if (catalog == null || !catalog.endsWith("_it")) {
                throw new IllegalStateException(
                        "TỪ CHỐI chạy test tích hợp trên CSDL '" + catalog + "': "
                        + "tên phải kết thúc bằng \"_it\" vì mỗi lượt chạy sẽ xoá sạch dữ liệu. "
                        + "Đặt -Dtest-sys-prop.DB_URL=jdbc:mysql://localhost:3306/poscs_it");
            }
        }
    }

    /**
     * Xoá dữ liệu nghiệp vụ, GIỮ lại các bảng tra cứu (roles, departments,
     * provinces, districts) vì chúng là dữ liệu tham chiếu cố định và nạp lại
     * rất tốn thời gian.
     */
    public static void clearBusinessData() throws SQLException {
        String[] tables = {
            "notifications", "customer_lifecycle_events", "contract_payments",
            "technicalrequestdevices", "technicalrequesthistory", "technicalrequests",
            "contractproducts", "contracts", "enterprisecontacts", "enterprises",
            "productimages", "productcatalogues", "products", "users", "addresses"
        };
        try (Connection conn = DBContext.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : tables) {
                st.execute("DELETE FROM " + table);
                st.execute("ALTER TABLE " + table + " AUTO_INCREMENT = 1");
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    /** Chạy một câu lệnh tuỳ ý -- dùng để dựng dữ liệu đầu vào cho test. */
    public static void exec(String sql) throws SQLException {
        try (Connection conn = DBContext.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    /** Đếm số dòng thoả điều kiện -- dùng để khẳng định kết quả sau khi DAO ghi. */
    public static int count(String table, String where) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table + (where == null ? "" : " WHERE " + where);
        try (Connection conn = DBContext.getConnection();
             Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    /** Đọc 1 giá trị đơn -- dùng để kiểm đúng cột nào đã được ghi. */
    public static String scalar(String sql) throws SQLException {
        try (Connection conn = DBContext.getConnection();
             Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /**
     * Tách file .sql thành từng câu lệnh.
     *
     * Cố tình viết thủ công thay vì dùng thư viện: file chỉ chứa DDL và INSERT
     * do mysqldump sinh ra, không có stored procedure hay DELIMITER tuỳ biến,
     * nên tách theo dấu ";" cuối dòng là đủ. Vẫn phải tôn trọng chuỗi trong
     * nháy đơn, vì dữ liệu seed có chứa cả ";" lẫn "--" bên trong tên/mô tả
     * tiếng Việt -- cắt ngây thơ sẽ tạo ra câu SQL vỡ đôi.
     */
    private static java.util.List<String> readStatements(File file) throws IOException {
        java.util.List<String> statements = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!inString && (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("/*"))) {
                    continue;
                }
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == '\'' && (i == 0 || line.charAt(i - 1) != '\\')) {
                        inString = !inString;
                    }
                    current.append(c);
                }
                current.append('\n');

                if (!inString && trimmed.endsWith(";")) {
                    String sql = current.toString().trim();
                    if (!sql.isEmpty()) {
                        statements.add(sql.substring(0, sql.length() - 1));
                    }
                    current.setLength(0);
                }
            }
        }
        return statements;
    }
}
