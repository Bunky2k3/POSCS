package poscs.controller;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Arrays;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.ContractDAO;
import poscs.model.Contract;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Test cho nhánh /contract/byEnterprise của ContractController -- endpoint
 * AJAX trả JSON tự dựng thủ
 * công (như AddressController). Trọng tâm: escape đúng ký tự đặc biệt trong
 * tiêu đề hợp đồng, và enterpriseId thiếu/sai phải trả 400 + mảng rỗng.
 */
public class ContractLookupControllerTest {

    private ContractController controller;
    private ContractDAO contractDAO;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter output;

    @Before
    public void setUp() throws Exception {
        controller = new ContractController();
        contractDAO = mock(ContractDAO.class);
        setField(controller, "contractDAO", contractDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        // ContractController phân nhánh theo servlet path TRƯỚC khi đọc tham số
        // "action" -- thiếu stub này thì request rơi vào nhánh danh sách HTML.
        when(request.getServletPath()).thenReturn("/contract/byEnterprise");
        output = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(output));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Contract contract(int id, String code, String title) {
        Contract c = new Contract();
        c.setContractId(id);
        c.setContractCode(code);
        c.setTitle(title);
        return c;
    }

    @Test
    public void missingEnterpriseId_returns400AndEmptyArray() throws Exception {
        when(request.getParameter("enterpriseId")).thenReturn(null);

        controller.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertEquals("[]", output.toString());
        verify(contractDAO, never()).findByEnterpriseId(anyInt());
    }

    @Test
    public void validEnterpriseId_writesJsonArrayWithEscapedTitle() throws Exception {
        when(request.getParameter("enterpriseId")).thenReturn("5");
        when(contractDAO.findByEnterpriseId(5)).thenReturn(Arrays.asList(
                contract(1, "HD-0001", "Hợp đồng \"đặc biệt\"")
        ));

        controller.doGet(request, response);

        assertEquals("[{\"id\":1,\"code\":\"HD-0001\",\"title\":\"Hợp đồng \\\"đặc biệt\\\"\"}]", output.toString());
    }

    @Test
    public void enterpriseWithNoContracts_writesEmptyArray() throws Exception {
        when(request.getParameter("enterpriseId")).thenReturn("5");
        when(contractDAO.findByEnterpriseId(5)).thenReturn(java.util.Collections.emptyList());

        controller.doGet(request, response);

        assertEquals("[]", output.toString());
        verify(response, never()).setStatus(anyInt());
    }
}
