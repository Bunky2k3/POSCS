package poscs.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import poscs.dao.ContractDAO;
import poscs.model.Contract;

/**
 * Phục vụ AJAX nạp danh sách hợp đồng theo khách hàng cho dropdown "Hợp
 * đồng liên quan" ở form tạo/sửa phiếu hỗ trợ kỹ thuật -- tách servlet
 * riêng thay vì sửa ContractController để không đụng code đang chạy ổn
 * định. Tự dựng JSON thủ công như AddressController (lib/ chưa có đủ
 * jackson-core/jackson-annotations).
 */
@WebServlet(name = "ContractLookupController", urlPatterns = {"/contract/byEnterprise"})
public class ContractLookupController extends HttpServlet {

    private final ContractDAO contractDAO = new ContractDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");

        Integer enterpriseId = parseIntOrNull(request.getParameter("enterpriseId"));
        if (enterpriseId == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter out = response.getWriter()) {
                out.write("[]");
            }
            return;
        }

        List<Contract> contracts = contractDAO.findByEnterpriseId(enterpriseId);
        try (PrintWriter out = response.getWriter()) {
            out.write(toJsonArray(contracts));
        }
    }

    private String toJsonArray(List<Contract> contracts) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < contracts.size(); i++) {
            Contract c = contracts.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":").append(c.getContractId())
              .append(",\"code\":\"").append(escapeJson(c.getContractCode())).append("\"}");
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
