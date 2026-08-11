package poscs.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.dao.AddressDAO;
import poscs.model.District;

/**
 * Phục vụ AJAX nạp xã/phường theo tỉnh cho các dropdown địa chỉ (thêm/sửa
 * khách hàng, sửa hồ sơ cá nhân) -- thay cho cách cũ nạp toàn bộ ~3.321
 * xã/phường một lần vào mỗi trang.
 *
 * Tự dựng JSON thủ công thay vì dùng Jackson: lib/ chỉ có jackson-databind,
 * thiếu jackson-core/jackson-annotations nên ObjectMapper không chạy được;
 * với hình dạng dữ liệu đơn giản {id, name} thì tự viết an toàn và không
 * cần thêm dependency.
 */
@WebServlet(name = "AddressController", urlPatterns = {"/address/wards"})
public class AddressController extends HttpServlet {

    private final AddressDAO addressDAO = new AddressDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");

        Integer provinceId = parseIntOrNull(request.getParameter("provinceId"));
        if (provinceId == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter out = response.getWriter()) {
                out.write("[]");
            }
            return;
        }

        List<District> wards = addressDAO.findWardsByProvinceId(provinceId);
        try (PrintWriter out = response.getWriter()) {
            out.write(toJsonArray(wards));
        }
    }

    private String toJsonArray(List<District> wards) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < wards.size(); i++) {
            District w = wards.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":").append(w.getDistrictId())
              .append(",\"name\":\"").append(escapeJson(w.getShortName())).append("\"}");
        }
        return sb.append(']').toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
