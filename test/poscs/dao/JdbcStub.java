package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bộ giả lập JDBC dùng chung cho các test DAO.
 *
 * Mọi DAO trong dự án đều lấy kết nối qua {@code DBContext.getConnection()}
 * (static) rồi tự mở PreparedStatement/ResultSet trong try-with-resources, nên
 * cách duy nhất chen vào mà không đổi code sản xuất là mockStatic DBContext và
 * trả về chuỗi mock Connection -> PreparedStatement -> ResultSet.
 *
 * <p><b>Giới hạn cần biết khi đọc kết quả các test này:</b> ở đây không có
 * MySQL nào chạy, nên câu SQL trong DAO KHÔNG hề được kiểm chứng -- sai tên
 * cột, sai cú pháp, lệch schema đều lọt qua hết. Những test này chỉ chứng minh
 * phần logic Java quanh JDBC là đúng: ánh xạ ResultSet sang model, giá trị trả
 * về khi nuốt SQLException, và trình tự commit/rollback/setAutoCommit. Muốn bắt
 * lỗi SQL thì phải là integration test chạy trên CSDL thật, chưa có trong dự án.
 */
public final class JdbcStub {

    private JdbcStub() {
    }

    /** Connection giả, mọi prepareStatement(...) đều trả về {@code ps}. */
    public static Connection connectionReturning(PreparedStatement ps) throws SQLException {
        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(conn.prepareStatement(anyString(), anyInt())).thenReturn(ps);
        return conn;
    }

    /** PreparedStatement giả trả về sẵn 1 ResultSet cho executeQuery(). */
    public static PreparedStatement statementReturning(ResultSet rs) throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(rs);
        return ps;
    }

    /** Một dòng dữ liệu, viết gọn: {@code row("user_id", 7, "title", "Xin chào")}. */
    public static Map<String, Object> row(Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("row() cần số lượng tham số chẵn (cột, giá trị)");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }

    /**
     * ResultSet giả chạy tuần tự qua {@code rows}: next() trả true đúng số dòng
     * rồi false, các getter đọc theo tên cột hoặc theo chỉ số 1-based (thứ tự
     * chèn của map) -- đủ cho cả hai kiểu truy cập mà DAO trong dự án đang dùng.
     */
    public static ResultSet resultSetOf(List<Map<String, Object>> rows) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        int[] cursor = {-1};

        when(rs.next()).thenAnswer(inv -> {
            cursor[0]++;
            return cursor[0] < rows.size();
        });

        when(rs.getInt(anyString())).thenAnswer(inv -> asInt(cell(rows, cursor, inv.getArgument(0, String.class))));
        when(rs.getInt(anyInt())).thenAnswer(inv -> asInt(cellByIndex(rows, cursor, inv.getArgument(0, Integer.class))));
        when(rs.getString(anyString())).thenAnswer(inv -> (String) cell(rows, cursor, inv.getArgument(0, String.class)));
        when(rs.getString(anyInt())).thenAnswer(inv -> (String) cellByIndex(rows, cursor, inv.getArgument(0, Integer.class)));
        when(rs.getBoolean(anyString())).thenAnswer(inv -> asBoolean(cell(rows, cursor, inv.getArgument(0, String.class))));
        when(rs.getBigDecimal(anyString())).thenAnswer(inv -> (java.math.BigDecimal) cell(rows, cursor, inv.getArgument(0, String.class)));
        when(rs.getBigDecimal(anyInt())).thenAnswer(inv -> (java.math.BigDecimal) cellByIndex(rows, cursor, inv.getArgument(0, Integer.class)));
        when(rs.getTimestamp(anyString())).thenAnswer(inv -> (java.sql.Timestamp) cell(rows, cursor, inv.getArgument(0, String.class)));
        when(rs.getDate(anyString())).thenAnswer(inv -> (java.sql.Date) cell(rows, cursor, inv.getArgument(0, String.class)));
        when(rs.getObject(anyString())).thenAnswer(inv -> cell(rows, cursor, inv.getArgument(0, String.class)));

        return rs;
    }

    /** ResultSet giả cho 1 dòng duy nhất. */
    public static ResultSet singleRow(Map<String, Object> row) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row);
        return resultSetOf(rows);
    }

    /** ResultSet giả rỗng -- next() trả false ngay lần đầu. */
    public static ResultSet emptyResultSet() throws SQLException {
        return resultSetOf(new ArrayList<>());
    }

    /** SQLException mang mã lỗi 1062 của MySQL (vi phạm UNIQUE KEY) trên cột chỉ định. */
    public static SQLException duplicateKeyError(String keyColumn) {
        return new SQLException("Duplicate entry 'X' for key '" + keyColumn + "'", "23000", 1062);
    }

    private static Object cell(List<Map<String, Object>> rows, int[] cursor, String column) {
        return rows.get(cursor[0]).get(column);
    }

    private static Object cellByIndex(List<Map<String, Object>> rows, int[] cursor, int oneBasedIndex) {
        return new ArrayList<>(rows.get(cursor[0]).values()).get(oneBasedIndex - 1);
    }

    /** getInt() của JDBC trả 0 cho SQL NULL, không phải null. */
    private static int asInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private static boolean asBoolean(Object value) {
        if (value == null) {
            return false;
        }
        return value instanceof Boolean ? (Boolean) value : ((Number) value).intValue() != 0;
    }
}
