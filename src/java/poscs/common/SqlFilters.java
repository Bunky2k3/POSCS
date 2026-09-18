package poscs.common;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Ghép mệnh đề {@code IN (?, ?, ...)} cho các bộ lọc "thuộc về những người
 * này" trên Dashboard.
 *
 * <p>Sinh ra vì bốn DAO (khách hàng, hợp đồng, phiếu hỗ trợ) đều cần đúng một
 * việc: thu hẹp số liệu về phần việc của người đang đăng nhập và cấp dưới của
 * họ. Chép tay vòng lặp đếm dấu hỏi ở từng nơi thì sớm muộn có chỗ lệch số
 * tham số với chỗ bind, mà lỗi đó chỉ nổ lúc chạy thật.
 *
 * <p>Danh sách rỗng hoặc null = KHÔNG lọc, trả về chuỗi rỗng. Cố ý: "toàn chi
 * nhánh" và "chưa truyền gì" là cùng một nghĩa ở đây, và một mệnh đề
 * {@code IN ()} rỗng thì MySQL từ chối cú pháp.
 */
public final class SqlFilters {

    private SqlFilters() {
    }

    public static boolean isEmpty(List<Integer> ids) {
        return ids == null || ids.isEmpty();
    }

    /** {@code " AND <column> IN (?,?,?)"}, hoặc chuỗi rỗng khi không lọc. */
    public static String inClause(String column, List<Integer> ids) {
        if (isEmpty(ids)) {
            return "";
        }
        return " AND " + column + " IN (" + placeholders(ids.size()) + ")";
    }

    /**
     * Như trên nhưng khớp NHIỀU cột bằng OR:
     * {@code " AND (a IN (?,?) OR b IN (?,?))"}.
     *
     * <p>Phiếu hỗ trợ cần nó: "phiếu của tôi" vừa là phiếu tôi được giao xử lý,
     * vừa là phiếu tôi tiếp nhận -- hai vai trò khác nhau (Kỹ thuật và CSKH)
     * nhìn cùng một trang Dashboard.
     */
    public static String inClauseAny(List<String> columns, List<Integer> ids) {
        if (isEmpty(ids) || columns == null || columns.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" AND (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(" OR ");
            }
            sb.append(columns.get(i)).append(" IN (").append(placeholders(ids.size())).append(')');
        }
        return sb.append(')').toString();
    }

    /**
     * Gán giá trị cho các dấu hỏi vừa sinh, {@code times} lần (mỗi cột một
     * lần). Trả về chỉ số tham số TIẾP THEO để bên gọi dùng tiếp.
     */
    public static int bind(PreparedStatement ps, int startIndex, List<Integer> ids, int times) throws SQLException {
        if (isEmpty(ids)) {
            return startIndex;
        }
        int index = startIndex;
        for (int t = 0; t < times; t++) {
            for (Integer id : ids) {
                ps.setInt(index++, id);
            }
        }
        return index;
    }

    /** Một lần, cho {@link #inClause(String, List)}. */
    public static int bind(PreparedStatement ps, int startIndex, List<Integer> ids) throws SQLException {
        return bind(ps, startIndex, ids, 1);
    }

    private static String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(i == 0 ? "?" : ",?");
        }
        return sb.toString();
    }
}
