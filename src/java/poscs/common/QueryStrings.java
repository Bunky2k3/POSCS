package poscs.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Dựng lại URL hiện tại với MỘT tham số bị đổi (hoặc bỏ), giữ nguyên mọi tham
 * số còn lại.
 *
 * <p>Sinh ra cho các nút "xem toàn chi nhánh" / "xem mọi thời điểm" trên hai
 * màn hình danh sách: bấm vào đó mà mất bộ lọc đang bật thì người dùng phải
 * chọn lại từ đầu, và họ thường không nhận ra mình vừa mất gì.
 *
 * <p>Không dùng {@code FilterState} của ContractController vì lớp đó liệt kê
 * cứng từng tham số nó biết -- thêm tham số mới phải sửa nó, mà quên một chỗ
 * thì tham số đó âm thầm rụng. Ở đây đọc thẳng từ {@code getParameterMap} nên
 * không có gì để quên.
 */
public final class QueryStrings {

    private QueryStrings() {
    }

    /**
     * @param name  tham số cần đặt
     * @param value giá trị mới; null = bỏ hẳn tham số đó khỏi URL
     * @return đường dẫn tương đối kèm query, vd {@code "/POSCS/contract?kind=sell&view=all"}
     */
    public static String with(HttpServletRequest request, String name, String value) {
        // URI rỗng thì ra link kiểu "?kind=sell&view=all" -- trình duyệt hiểu là
        // "chính trang này, đổi query", vẫn đúng. Chặn null ở đây vì một ô trống
        // không đáng làm vỡ cả trang danh sách.
        String uri = request.getRequestURI();
        StringBuilder sb = new StringBuilder(uri == null ? "" : uri);
        boolean first = true;
        Map<String, String[]> current = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : (current == null ? Map.<String, String[]>of() : current).entrySet()) {
            if (name.equals(entry.getKey())) {
                continue;
            }
            for (String v : entry.getValue()) {
                // Tham số rỗng bị bỏ đi luôn: chúng không lọc gì cả mà lại làm
                // URL dài thêm mỗi lần bấm, sau vài lượt là không đọc nổi nữa.
                if (v == null || v.isEmpty()) {
                    continue;
                }
                sb.append(first ? '?' : '&').append(encode(entry.getKey())).append('=').append(encode(v));
                first = false;
            }
        }
        if (value != null) {
            sb.append(first ? '?' : '&').append(encode(name)).append('=').append(encode(value));
        }
        return sb.toString();
    }

    private static String encode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }
}
