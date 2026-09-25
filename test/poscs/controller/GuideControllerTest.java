package poscs.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Trang Hướng dẫn sử dụng (/guide?module=...). Mở ở tab mới từ nút "Hướng dẫn"
 * trên các trang của từng phân hệ, nên đường dẫn phải ổn định: module lạ báo
 * 404 thay vì rơi vào một trang hướng dẫn khác mà người đọc không hay.
 *
 * JUnit 4 -- xem CustomerControllerTest.
 */
public class GuideControllerTest {

    private GuideController controller;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private RequestDispatcher dispatcher;

    @Before
    public void setUp() {
        controller = new GuideController();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
    }

    @Test
    public void moduleCustomer_moTrangHuongDanKhachHang() throws Exception {
        when(request.getParameter("module")).thenReturn("customer");

        controller.doGet(request, response);

        verify(request).getRequestDispatcher("/jsp/guide/customer.jsp");
        verify(request).setAttribute("guideModule", "customer");
        verify(dispatcher).forward(request, response);
    }

    /** Nút "Hướng dẫn" trên các trang hợp đồng mở /guide?module=contract#mục. */
    @Test
    public void moduleContract_moTrangHuongDanHopDong() throws Exception {
        when(request.getParameter("module")).thenReturn("contract");

        controller.doGet(request, response);

        verify(request).getRequestDispatcher("/jsp/guide/contract.jsp");
        verify(request).setAttribute("guideModule", "contract");
        verify(dispatcher).forward(request, response);
    }

    /** Nút "Hướng dẫn" trên các trang phiếu hỗ trợ mở /guide?module=ticket#mục. */
    @Test
    public void moduleTicket_moTrangHuongDanPhieuHoTro() throws Exception {
        when(request.getParameter("module")).thenReturn("ticket");

        controller.doGet(request, response);

        verify(request).getRequestDispatcher("/jsp/guide/ticket.jsp");
        verify(request).setAttribute("guideModule", "ticket");
        verify(dispatcher).forward(request, response);
    }

    /** Link cũ hoặc gõ tay /guide không kèm module: mở phân hệ mặc định, không báo lỗi. */
    @Test
    public void thieuModule_moPhanHeMacDinh() throws Exception {
        when(request.getParameter("module")).thenReturn(null);

        controller.doGet(request, response);

        verify(request).getRequestDispatcher("/jsp/guide/customer.jsp");
        verify(dispatcher).forward(request, response);
    }

    /**
     * Phân hệ chưa có hướng dẫn (hoặc gõ sai) trả 404 -- KHÔNG đưa về trang của
     * phân hệ khác, và không dùng module làm đường dẫn JSP (tránh ?module=../..).
     */
    @Test
    public void moduleLa_tra404() throws Exception {
        when(request.getParameter("module")).thenReturn("../admin/listEmployee");

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
        verify(dispatcher, never()).forward(any(), any());
    }
}
