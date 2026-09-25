package poscs.controller;

import java.io.IOException;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Trang Hướng dẫn sử dụng của từng phân hệ: /guide?module=customer ...
 *
 * <p>Mở ở tab mới từ nút "Hướng dẫn" trên các trang của phân hệ đó (chốt với
 * người dùng 2026-09-24), để đọc song song với trang đang làm và in được.
 * Nội dung là JSP tĩnh ở /jsp/guide/, ảnh ở /guide/&lt;phân hệ&gt;/ do
 * tools/guide/capture.mjs chụp từ app thật.
 *
 * <p>Cần đăng nhập như mọi trang khác (AuthenticationFilter), và ảnh cũng
 * CỐ Ý không nằm dưới /img/ -- đường đó công khai cho trang đăng nhập, mà ảnh
 * hướng dẫn chụp cả dữ liệu khách hàng lẫn tên nhân viên.
 *
 * <p>Không phân quyền theo vai: đây là tài liệu, người không làm được một việc
 * thì đọc cũng chỉ biết việc đó cần ai làm -- chính trang hướng dẫn cũng ghi
 * rõ "Ai làm được" ở từng mục.
 */
@WebServlet(name = "GuideController", urlPatterns = {"/guide"})
public class GuideController extends HttpServlet {

    /** Phân hệ đã có hướng dẫn -> trang nội dung. Thêm phân hệ mới thì thêm dòng ở đây. */
    private static final Map<String, String> PAGES = Map.of(
            "customer", "/jsp/guide/customer.jsp",
            "contract", "/jsp/guide/contract.jsp",
            "ticket", "/jsp/guide/ticket.jsp");

    private static final String DEFAULT_MODULE = "customer";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String module = request.getParameter("module");
        if (module == null || module.isBlank()) {
            module = DEFAULT_MODULE;
        }
        String page = PAGES.get(module);
        if (page == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Chưa có hướng dẫn cho mục này.");
            return;
        }
        request.setAttribute("guideModule", module);
        request.getRequestDispatcher(page).forward(request, response);
    }
}
