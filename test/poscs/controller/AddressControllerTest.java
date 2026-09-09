package poscs.controller;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Arrays;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.AddressDAO;
import poscs.model.District;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Test cho AddressController -- servlet AJAX trả JSON tự dựng thủ công
 * (không dùng Jackson, xem javadoc gốc). Trọng tâm: JSON escape đúng ký tự
 * đặc biệt (tên xã/phường có thể chứa dấu ngoặc kép/backslash), và
 * provinceId thiếu/sai phải trả 400 + mảng rỗng thay vì lỗi 500.
 */
public class AddressControllerTest {

    private AddressController controller;
    private AddressDAO addressDAO;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter output;

    @Before
    public void setUp() throws Exception {
        controller = new AddressController();
        addressDAO = mock(AddressDAO.class);
        setField(controller, "addressDAO", addressDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        output = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(output));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** name không được bắt đầu bằng "Phường "/"Xã "/"Đặc khu " -- getShortName() sẽ cắt mất tiền tố đó. */
    private static District district(int id, String name) {
        District d = new District();
        d.setDistrictId(id);
        d.setDistrictName(name);
        return d;
    }

    @Test
    public void missingProvinceId_returns400AndEmptyArray() throws Exception {
        when(request.getParameter("provinceId")).thenReturn(null);

        controller.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertEquals("[]", output.toString());
        verify(addressDAO, never()).findWardsByProvinceId(anyInt());
    }

    @Test
    public void nonNumericProvinceId_returns400AndEmptyArray() throws Exception {
        when(request.getParameter("provinceId")).thenReturn("not-a-number");

        controller.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertEquals("[]", output.toString());
    }

    @Test
    public void validProvinceId_writesJsonArrayWithShortNameAndEscaping() throws Exception {
        when(request.getParameter("provinceId")).thenReturn("1");
        when(addressDAO.findWardsByProvinceId(1)).thenReturn(Arrays.asList(
                // "Phường " phải bị cắt bởi getShortName(); dấu " còn lại trong tên phải được escape.
                district(10, "Phường \"Trung Tâm\""),
                district(20, "Xã Bình An") // "Xã " cũng phải bị cắt
        ));

        controller.doGet(request, response);

        assertEquals(
                "[{\"id\":10,\"name\":\"\\\"Trung Tâm\\\"\"},{\"id\":20,\"name\":\"Bình An\"}]",
                output.toString());
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    public void emptyResultFromDao_writesEmptyArray() throws Exception {
        when(request.getParameter("provinceId")).thenReturn("999");
        when(addressDAO.findWardsByProvinceId(999)).thenReturn(java.util.Collections.emptyList());

        controller.doGet(request, response);

        assertEquals("[]", output.toString());
    }
}
