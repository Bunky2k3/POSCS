package poscs.controller;

import java.lang.reflect.Field;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import poscs.common.FileStorage;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.CustomerLifecycleEventDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.dao.TerritoryDAO;
import poscs.model.CustomerLifecycleEvent;
import poscs.model.Enterprise;
import poscs.model.RelationshipRating;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test tầng Controller cho CustomerController -- tập trung vào các quy tắc
 * nghiệp vụ chạy trước khi chạm DB (BR-41 không xoá KH còn hợp đồng, BR-09/
 * BR-10 định dạng SĐT/email, phân quyền Full access CUSTOMER) chứ không test
 * lại CustomerDAO/JDBC. DAO được mock và inject thẳng vào field private
 * (không có constructor injection trong code gốc) bằng reflection.
 *
 * JUnit 4 (không phải 5) vì build-impl.xml của project (NetBeans Web
 * Application, Ant) chỉ nối sẵn Ant <junit> task cổ điển -- task này chỉ
 * nhận diện được org.junit.Test (JUnit 3/4), không chạy được JUnit 5 Jupiter.
 */
public class CustomerControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private CustomerController controller;
    private CustomerDAO customerDAO;
    private EmployeeDAO employeeDAO;
    private AddressDAO addressDAO;
    private ContractDAO contractDAO;
    private TechnicalSupportTicketDAO ticketDAO;
    private CustomerLifecycleEventDAO lifecycleEventDAO;
    private TerritoryDAO territoryDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @Before
    public void setUp() throws Exception {
        controller = new CustomerController();

        customerDAO = mock(CustomerDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        addressDAO = mock(AddressDAO.class);
        contractDAO = mock(ContractDAO.class);
        ticketDAO = mock(TechnicalSupportTicketDAO.class);
        lifecycleEventDAO = mock(CustomerLifecycleEventDAO.class);
        territoryDAO = mock(TerritoryDAO.class);

        setField(controller, "customerDAO", customerDAO);
        setField(controller, "employeeDAO", employeeDAO);
        setField(controller, "addressDAO", addressDAO);
        setField(controller, "contractDAO", contractDAO);
        setField(controller, "ticketDAO", ticketDAO);
        setField(controller, "lifecycleEventDAO", lifecycleEventDAO);
        setField(controller, "territoryDAO", territoryDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);

        loginAs("Sales"); // role được Full access trên CUSTOMER, xem PERMISSIONS.md
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

    // ------------------------------------------------------------------
    // GET ?action=view
    // ------------------------------------------------------------------

    @Test
    public void view_customerNotFound_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("123");
        when(customerDAO.findById(123)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?error=notfound");
    }

    @Test
    public void view_customerFound_forwardsToDetailView() throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");
        Enterprise enterprise = new Enterprise();
        enterprise.setEnterpriseId(5);
        when(customerDAO.findById(5)).thenReturn(enterprise);

        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/viewcustomerdetail.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(request).setAttribute("customer", enterprise);
        verify(dispatcher).forward(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    // ------------------------------------------------------------------
    // POST ?action=delete (BR-41)
    // ------------------------------------------------------------------

    @Test
    public void delete_withoutFullAccess_returns403AndNeverTouchesCustomer() throws Exception {
        loginAs("Kỹ thuật"); // không có Full access trên CUSTOMER
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("7");

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(customerDAO, never()).hasActiveContracts(anyInt());
        verify(customerDAO, never()).softDelete(anyInt());
    }

    @Test
    public void delete_customerHasActiveContracts_blocksDeletion() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("7");
        when(customerDAO.findById(7)).thenReturn(new Enterprise());
        when(customerDAO.hasActiveContracts(7)).thenReturn(true);

        controller.doPost(request, response);

        verify(customerDAO, never()).softDelete(anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=7&error=has_active_contracts");
    }

    @Test
    public void delete_customerHasNoActiveContracts_softDeletesAndRedirectsToList() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("7");
        when(customerDAO.findById(7)).thenReturn(new Enterprise());
        when(customerDAO.hasActiveContracts(7)).thenReturn(false);

        controller.doPost(request, response);

        verify(customerDAO).softDelete(7);
        verify(response).sendRedirect(CONTEXT_PATH + "/customer");
    }

    // ------------------------------------------------------------------
    // POST ?action=evaluate
    // ------------------------------------------------------------------

    @Test
    public void evaluate_invalidRating_redirectsWithErrorAndRecordsNothing() throws Exception {
        when(request.getParameter("action")).thenReturn("evaluate");
        when(request.getParameter("id")).thenReturn("3");
        when(request.getParameter("rating")).thenReturn("NOT_A_REAL_RATING");
        when(customerDAO.findById(3)).thenReturn(new Enterprise());

        controller.doPost(request, response);

        verify(customerDAO, never()).updateRelationshipRating(anyInt(), any());
        verify(lifecycleEventDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=3&error=invalid_rating");
    }

    @Test
    public void evaluate_validRating_updatesRatingAndRecordsLifecycleEvent() throws Exception {
        when(request.getParameter("action")).thenReturn("evaluate");
        when(request.getParameter("id")).thenReturn("3");
        when(request.getParameter("rating")).thenReturn("GOOD");
        when(request.getParameter("description")).thenReturn("Khách hàng thân thiết");
        when(customerDAO.findById(3)).thenReturn(new Enterprise());

        controller.doPost(request, response);

        verify(customerDAO).updateRelationshipRating(3, RelationshipRating.GOOD);
        verify(lifecycleEventDAO).insert(argThat((CustomerLifecycleEvent event) ->
                event.getEnterpriseId() == 3
                        && event.getRelationshipRating() == RelationshipRating.GOOD
                        && !event.isAutoGenerated()
                        && event.getRecordedBy() == 99));
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=3&evaluated=1");
    }

    // ------------------------------------------------------------------
    // POST ?action=create (BR-09 SĐT, BR-10 email)
    // ------------------------------------------------------------------

    private void stubValidCreateFields() {
        when(request.getParameter("customerName")).thenReturn("Cong ty ABC");
        when(request.getParameter("customerType")).thenReturn("Nhà mạng viễn thông");
        when(request.getParameter("customerGroup")).thenReturn("VIP");
        when(request.getParameter("taxCode")).thenReturn("0101234567");
        when(request.getParameter("phone")).thenReturn("0912345678");
        when(request.getParameter("email")).thenReturn("abc@example.com");
        when(request.getParameter("accountOwnerId")).thenReturn("9");
        // Địa chỉ là bắt buộc từ khi khách hàng được quản lý theo địa bàn tỉnh:
        // tỉnh chỉ suy ra được qua xã/phường của địa chỉ.
        when(request.getParameter("districtId")).thenReturn("10");
        when(request.getParameter("addressDetail")).thenReturn("Số 1 Trần Phú");
    }

    /**
     * Không có địa chỉ thì không suy ra được tỉnh, mà tỉnh là căn cứ chia địa
     * bàn/lọc báo cáo -- khách như vậy sẽ vô hình với mọi thống kê theo tỉnh,
     * nên chặn ngay từ lúc tạo thay vì để lọt rồi đi dọn sau.
     */
    @Test
    public void create_missingAddress_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("districtId")).thenReturn(null);
        when(request.getParameter("addressDetail")).thenReturn(null);

        controller.doPost(request, response);

        verify(customerDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=invalid");
    }

    /**
     * Hai vai phải là hai người khác nhau: chọn trùng thì cột "Người hỗ trợ"
     * chỉ lặp lại tên ở cột bên cạnh, và thống kê theo người đếm người đó hai
     * lần cho cùng một khách.
     */
    @Test
    public void create_supportOwnerSameAsMainOwner_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("supportOwnerId")).thenReturn("9"); // trùng accountOwnerId

        controller.doPost(request, response);

        verify(customerDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=invalid");
    }

    @Test
    public void create_invalidPhone_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("phone")).thenReturn("not-a-phone"); // vi phạm BR-09

        controller.doPost(request, response);

        verify(customerDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=invalid");
    }

    @Test
    public void create_logoWithDisallowedType_rejectsBeforeInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        Part logoPart = mock(Part.class);
        when(logoPart.getSize()).thenReturn(1024L);
        when(logoPart.getSubmittedFileName()).thenReturn("logo.svg");
        when(request.getPart("logo")).thenReturn(logoPart);

        try (MockedStatic<FileStorage> fs = mockStatic(FileStorage.class)) {
            fs.when(() -> FileStorage.isAcceptable(logoPart, FileStorage.IMAGE_EXTENSIONS)).thenReturn(false);

            controller.doPost(request, response);

            // Trước đây file sai loại chỉ khiến save() trả null -- giống hệt khi
            // không chọn file -- nên khách hàng vẫn được tạo, không có logo và
            // không có lời giải thích nào.
            verify(customerDAO, never()).insert(any());
            verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=invalid_image_type");
        }
    }

    @Test
    public void create_invalidEmail_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("email")).thenReturn("not-an-email"); // vi phạm BR-10

        controller.doPost(request, response);

        verify(customerDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=invalid");
    }

    @Test
    public void create_validFields_insertsAndRedirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0001");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(42);

        controller.doPost(request, response);

        verify(customerDAO).insert(any(Enterprise.class));
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=42");
    }

    @Test
    public void create_insertFails_redirectsWithCreateFailedError() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0001");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(0);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=create_failed");
    }

    // ------------------------------------------------------------------
    // Khoá người phụ trách theo địa bàn
    // ------------------------------------------------------------------
    //
    // Quy tắc khách hàng chốt 2026-09-15: tỉnh quyết định người phụ trách
    // chính, người nhập liệu không chọn. Khoá ở JSP chỉ là khoá hình (ô
    // disabled, ai mở devtools cũng gỡ được) nên chỗ phải đúng là đây --
    // và đó cũng là lý do các test dưới đây POST thẳng một accountOwnerId
    // khác với người cầm tỉnh, đúng như một request nặn tay sẽ làm.

    /**
     * Người cầm tỉnh THẮNG giá trị gửi lên. Nếu không, khoá chỉ tồn tại
     * trong trình duyệt và bất kỳ ai cũng tự gán khách hàng cho mình được.
     */
    @Test
    public void create_provinceHasOwner_overridesSubmittedAccountOwner() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields(); // accountOwnerId=9, districtId=10
        when(territoryDAO.findAssigneeOfWard(10)).thenReturn(42);
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0001");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(7);

        controller.doPost(request, response);

        ArgumentCaptor<Enterprise> saved = ArgumentCaptor.forClass(Enterprise.class);
        verify(customerDAO).insert(saved.capture());
        assertEquals(42, saved.getValue().getAccountOwnerId());
    }

    /**
     * Tỉnh chưa ai cầm thì KHÔNG khoá. Bảng phân công mới phủ một phần trong
     * 34 tỉnh; khoá tất thì "chưa phân công" biến thành chặn nghiệp vụ --
     * không tạo nổi khách hàng ở những tỉnh còn trống.
     */
    @Test
    public void create_provinceWithoutOwner_keepsSubmittedAccountOwner() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(territoryDAO.findAssigneeOfWard(10)).thenReturn(null);
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0001");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(7);

        controller.doPost(request, response);

        ArgumentCaptor<Enterprise> saved = ArgumentCaptor.forClass(Enterprise.class);
        verify(customerDAO).insert(saved.capture());
        assertEquals(9, saved.getValue().getAccountOwnerId());
    }

    /**
     * Địa bàn suy từ XÃ/PHƯỜNG, không từ ô tỉnh gửi kèm. Form gửi lên cả hai
     * ô nhưng chỉ xã/phường mới đi vào địa chỉ khách hàng -- tin vào ô tỉnh
     * rời thì khai tỉnh 99 để lấy người của tỉnh 99 trong khi khách nằm ở
     * tỉnh khác, đúng cái mà khoá này sinh ra để chặn.
     */
    @Test
    public void create_ignoresSubmittedProvinceId_resolvesOwnerFromWard() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("provinceId")).thenReturn("99");
        when(territoryDAO.findAssigneeOfWard(10)).thenReturn(42);
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0001");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(7);

        controller.doPost(request, response);

        verify(territoryDAO).findAssigneeOfWard(10);
        verify(territoryDAO, never()).findAssigneeOf(anyInt());
    }

    /**
     * Form sửa phải khoá y hệt form tạo, nếu không thì tạo khách xong mở
     * trang sửa đổi lại người phụ trách là đường vòng thoát khoá.
     */
    @Test
    public void update_provinceHasOwner_overridesSubmittedAccountOwner() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("customerId")).thenReturn("5");
        Enterprise existing = new Enterprise();
        existing.setEnterpriseId(5);
        existing.setAddressId(31);
        when(customerDAO.findById(5)).thenReturn(existing);

        stubValidCreateFields();
        when(territoryDAO.findAssigneeOfWard(10)).thenReturn(42);
        when(customerDAO.update(any(Enterprise.class))).thenReturn(true);

        controller.doPost(request, response);

        ArgumentCaptor<Enterprise> saved = ArgumentCaptor.forClass(Enterprise.class);
        verify(customerDAO).update(saved.capture());
        assertEquals(42, saved.getValue().getAccountOwnerId());
    }

    // ------------------------------------------------------------------
    // GET ?action=new / edit -- trang form cũng phải gác quyền
    // ------------------------------------------------------------------

    /** Ẩn nút ở JSP chỉ là lớp trình bày; gõ thẳng URL vẫn phải bị chặn. */
    @Test
    public void createForm_withoutFullAccess_returns403InsteadOfRendering() throws Exception {
        loginAs("Kỹ thuật"); // chỉ View only trên CUSTOMER
        when(request.getParameter("action")).thenReturn("new");

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(request, never()).getRequestDispatcher("/jsp/sale/addnewcustomer.jsp");
    }

    @Test
    public void editForm_withoutFullAccess_returns403InsteadOfRendering() throws Exception {
        loginAs("Kỹ thuật");
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("5");

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(request, never()).getRequestDispatcher("/jsp/sale/updatecustomer.jsp");
    }

    @Test
    public void createForm_withFullAccess_stillRenders() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/addnewcustomer.jsp")).thenReturn(dispatcher);
        when(request.getParameter("action")).thenReturn("new");

        controller.doGet(request, response);

        verify(dispatcher).forward(request, response);
        verify(response, never()).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }

    /**
     * Xoá một id không có thật phải báo không tìm thấy. Trước đây hàm chỉ kiểm
     * id có phải số hay không rồi gọi thẳng softDelete: UPDATE không chạm dòng
     * nào, người dùng bị đẩy về danh sách không kèm thông báo gì và tưởng đã
     * xoá xong.
     */
    @Test
    public void delete_customerNotFound_redirectsWithNotFoundAndNeverSoftDeletes() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("999999");
        when(customerDAO.findById(999999)).thenReturn(null);

        controller.doPost(request, response);

        verify(customerDAO, never()).hasActiveContracts(anyInt());
        verify(customerDAO, never()).softDelete(anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?error=notfound");
    }

}
