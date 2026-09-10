package poscs.controller;

import java.lang.reflect.Field;
import java.sql.Date;
import java.time.LocalDate;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.ProductDAO;
import poscs.model.Contract;
import poscs.model.ContractProduct;
import poscs.model.Product;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test tầng Controller cho ContractController -- tập trung vào các quy tắc
 * nghiệp vụ chạy trước khi chạm DB (BR-44 ngày ký &le; hiệu lực &le; kết
 * thúc, BR-46 chỉ xoá hợp đồng "Chưa hiệu lực", parse số lượng kiểu VN khi
 * gắn sản phẩm, phân quyền Full access CONTRACT) chứ không test lại
 * ContractDAO/JDBC hay 2 action xuất/nhập PDF (exportPdf/handleImportPdf --
 * cần PDDocument mẫu thật từ WEB-INF/templates qua ServletContext, thuộc
 * phạm vi test tích hợp, không phải unit test tầng controller).
 *
 * JUnit 4 (không phải 5) vì build-impl.xml của project chỉ nối sẵn Ant
 * &lt;junit&gt; task cổ điển -- xem CustomerControllerTest.
 */
public class ContractControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private ContractController controller;
    private ContractDAO contractDAO;
    private CustomerDAO customerDAO;
    private EmployeeDAO employeeDAO;
    private AddressDAO addressDAO;
    private ProductDAO productDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @Before
    public void setUp() throws Exception {
        controller = new ContractController();

        contractDAO = mock(ContractDAO.class);
        customerDAO = mock(CustomerDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        addressDAO = mock(AddressDAO.class);
        productDAO = mock(ProductDAO.class);

        setField(controller, "contractDAO", contractDAO);
        setField(controller, "customerDAO", customerDAO);
        setField(controller, "employeeDAO", employeeDAO);
        setField(controller, "addressDAO", addressDAO);
        setField(controller, "productDAO", productDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);

        loginAs("Sales"); // role được Full access trên CONTRACT, xem PERMISSIONS.md
    }

    private void loginAs(String roleName) {
        User user = new User();
        user.setUserId(99);
        user.setRole(new Role(1, roleName));
        when(session.getAttribute("currentUser")).thenReturn(user);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Date sqlDate(int year, int month, int day) {
        return Date.valueOf(LocalDate.of(year, month, day));
    }

    // ------------------------------------------------------------------
    // GET ?action=view
    // ------------------------------------------------------------------

    @Test
    public void view_contractNotFound_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("123");
        when(contractDAO.findById(123)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    /** Mở trang chi tiết của 1 hợp đồng có link đính kèm, trả về giá trị drivePreviewUrl đã dựng. */
    private Object drivePreviewAttributeFor(String attachmentUrl) throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");
        Contract contract = new Contract();
        contract.setContractId(5);
        contract.setAttachmentUrl(attachmentUrl);
        when(contractDAO.findById(5)).thenReturn(contract);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/viewcontractdetail.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(request).setAttribute(eq("drivePreviewUrl"), captor.capture());
        return captor.getValue();
    }

    @Test
    public void view_driveLink_isConvertedToEmbeddablePreviewUrl() throws Exception {
        // Link Drive người dùng copy ra luôn ở dạng /view; chỉ bản /preview mới
        // nhúng được vào iframe.
        assertEquals("https://drive.google.com/file/d/1AbC_de-F/preview",
                drivePreviewAttributeFor("https://drive.google.com/file/d/1AbC_de-F/view?usp=sharing"));
    }

    @Test
    public void view_nonDriveLink_producesNoPreviewUrl() throws Exception {
        // Site khác thường tự chặn bị nhúng (X-Frame-Options) -- khung trắng
        // khó hiểu hơn hẳn một cái link, nên không nhúng.
        assertNull(drivePreviewAttributeFor("https://noi-bo.congty.vn/hop-dong.pdf"));
    }

    @Test
    public void view_noAttachment_producesNoPreviewUrl() throws Exception {
        assertNull(drivePreviewAttributeFor(null));
    }

    @Test
    public void view_contractFound_forwardsToDetailView() throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");
        Contract contract = new Contract();
        contract.setContractId(5);
        when(contractDAO.findById(5)).thenReturn(contract);
        when(contractDAO.canDelete(5)).thenReturn(true);

        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/viewcontractdetail.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(request).setAttribute("contract", contract);
        verify(request).setAttribute("canDelete", true);
        verify(dispatcher).forward(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    // ------------------------------------------------------------------
    // POST ?action=create / update (BR-44)
    // ------------------------------------------------------------------

    private void stubValidContractFields() {
        when(request.getParameter("title")).thenReturn("Hợp đồng cung cấp thiết bị");
        when(request.getParameter("contractType")).thenReturn("Cung cấp thiết bị");
        when(request.getParameter("signDate")).thenReturn("2026-01-01");
        when(request.getParameter("effectiveDate")).thenReturn("2026-01-15");
        when(request.getParameter("endDate")).thenReturn("2026-12-31");
        when(request.getParameter("enterpriseId")).thenReturn("10");
        when(request.getParameter("ownerId")).thenReturn("99");
    }

    @Test
    public void create_missingTitle_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidContractFields();
        when(request.getParameter("title")).thenReturn(null);

        controller.doPost(request, response);

        verify(contractDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=new&error=invalid");
    }

    @Test
    public void create_signDateAfterEffectiveDate_violatesBr44_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidContractFields();
        // Ngày ký SAU ngày hiệu lực -- vi phạm BR-44 (ký <= hiệu lực <= kết thúc)
        when(request.getParameter("signDate")).thenReturn("2026-02-01");
        when(request.getParameter("effectiveDate")).thenReturn("2026-01-15");

        controller.doPost(request, response);

        verify(contractDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=new&error=invalid");
    }

    @Test
    public void create_validFields_insertsAndRedirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidContractFields();
        when(contractDAO.generateNextContractCode()).thenReturn("HD-0001");
        when(contractDAO.insert(any(Contract.class))).thenReturn(77);

        controller.doPost(request, response);

        verify(contractDAO).insert(any(Contract.class));
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=77");
    }

    @Test
    public void create_withoutFullAccess_returns403AndNeverInserts() throws Exception {
        loginAs("Kỹ thuật"); // không có Full access trên CONTRACT
        when(request.getParameter("action")).thenReturn("create");
        stubValidContractFields();

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(contractDAO, never()).insert(any());
    }

    @Test
    public void update_contractIdMissing_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("contractId")).thenReturn(null);
        stubValidContractFields();

        controller.doPost(request, response);

        verify(contractDAO, never()).update(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void update_validFields_updatesAndRedirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("contractId")).thenReturn("5");
        stubValidContractFields();
        when(contractDAO.update(any(Contract.class))).thenReturn(true);

        controller.doPost(request, response);

        verify(contractDAO).update(argThat((Contract c) -> c.getContractId() == 5));
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5");
    }

    // ------------------------------------------------------------------
    // POST ?action=delete (BR-46)
    // ------------------------------------------------------------------

    @Test
    public void delete_contractNotDeletable_blocksDeletion() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");
        when(contractDAO.canDelete(5)).thenReturn(false);

        controller.doPost(request, response);

        verify(contractDAO, never()).softDelete(anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5&error=cannot_delete");
    }

    @Test
    public void delete_contractDeletable_softDeletesAndRedirectsToList() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");
        when(contractDAO.canDelete(5)).thenReturn(true);

        controller.doPost(request, response);

        verify(contractDAO).softDelete(5);
        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
    }

    // ------------------------------------------------------------------
    // POST ?action=addProduct / removeProduct
    // ------------------------------------------------------------------

    @Test
    public void addProduct_contractNotFound_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("action")).thenReturn("addProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractDAO.findById(5)).thenReturn(null);

        controller.doPost(request, response);

        verify(contractDAO, never()).insertProducts(anyInt(), anyList());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void addProduct_unknownProductId_redirectsWithInvalidError() throws Exception {
        when(request.getParameter("action")).thenReturn("addProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractDAO.findById(5)).thenReturn(new Contract());
        when(request.getParameter("productId")).thenReturn("999");
        when(request.getParameter("quantity")).thenReturn("10");
        when(productDAO.findById(999)).thenReturn(null);

        controller.doPost(request, response);

        verify(contractDAO, never()).insertProducts(anyInt(), anyList());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5&error=add_product_invalid");
    }

    /**
     * parseQuantityOrNull chấp nhận dấu "." phân tách hàng nghìn kiểu VN (vd
     * "1.000" -> 1000) NHƯNG chỉ khi mỗi nhóm sau dấu "." có đúng 3 chữ số --
     * "2.5" phải bị từ chối, không được âm thầm hiểu nhầm thành 25.
     */
    @Test
    public void addProduct_vietnameseThousandSeparatorQuantity_isParsedCorrectly() throws Exception {
        when(request.getParameter("action")).thenReturn("addProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractDAO.findById(5)).thenReturn(new Contract());
        when(request.getParameter("productId")).thenReturn("3");
        when(request.getParameter("quantity")).thenReturn("1.000");
        Product product = new Product();
        product.setProductId(3);
        when(productDAO.findById(3)).thenReturn(product);
        when(contractDAO.insertProducts(eq(5), anyList())).thenReturn(true);

        controller.doPost(request, response);

        verify(contractDAO).insertProducts(eq(5), argThat((java.util.List<ContractProduct> items) ->
                items.size() == 1 && items.get(0).getQuantity() == 1000));
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5");
    }

    @Test
    public void addProduct_decimalLookingQuantity_isRejectedNotMisreadAsInteger() throws Exception {
        when(request.getParameter("action")).thenReturn("addProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractDAO.findById(5)).thenReturn(new Contract());
        when(request.getParameter("productId")).thenReturn("3");
        when(request.getParameter("quantity")).thenReturn("2.5"); // không phải nhóm 3 chữ số -- phải bị từ chối
        Product product = new Product();
        product.setProductId(3);
        when(productDAO.findById(3)).thenReturn(product);

        controller.doPost(request, response);

        verify(contractDAO, never()).insertProducts(anyInt(), anyList());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5&error=add_product_invalid");
    }

    @Test
    public void removeProduct_daoFails_redirectsWithRemoveFailedError() throws Exception {
        when(request.getParameter("action")).thenReturn("removeProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("contractProductId")).thenReturn("42");
        when(contractDAO.deleteProductLine(42, 5)).thenReturn(false);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5&error=remove_product_failed");
    }

    @Test
    public void removeProduct_daoSucceeds_redirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("removeProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("contractProductId")).thenReturn("42");
        when(contractDAO.deleteProductLine(42, 5)).thenReturn(true);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5");
    }
}
