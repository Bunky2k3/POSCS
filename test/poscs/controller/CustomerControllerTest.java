package poscs.controller;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import org.junit.Before;
import org.junit.Test;
import poscs.common.ListScope;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import poscs.common.FileStorage;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Address;
import poscs.model.CustomerLifecycleEvent;
import poscs.model.District;
import poscs.model.Enterprise;
import poscs.model.Province;
import poscs.model.RelationshipRating;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test tầng Controller cho CustomerController -- tập trung vào các quy tắc
 * nghiệp vụ chạy trước khi chạm DB (BR-34 không xoá KH còn hợp đồng, BR-09/
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

        setField(controller, "customerDAO", customerDAO);
        setField(controller, "employeeDAO", employeeDAO);
        setField(controller, "addressDAO", addressDAO);
        setField(controller, "contractDAO", contractDAO);
        setField(controller, "ticketDAO", ticketDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);

        loginAs("Sales"); // role được Full access trên CUSTOMER, xem PERMISSIONS.md
        // EmployeeDAO.findTeamUserIds thật luôn có chính người hỏi, kể cả khi
        // họ chưa có cấp dưới nào.
        when(employeeDAO.findTeamUserIds(99)).thenReturn(List.of(99));
        // Đường thành công mặc định: ghi vai được. Test nào nói về ghi vai hỏng
        // thì tự stub lại false.
        when(customerDAO.replaceRolesOf(anyInt(), any())).thenReturn(true);
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
    // POST ?action=delete (BR-34)
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
        when(customerDAO.softDelete(7)).thenReturn(true);

        controller.doPost(request, response);

        verify(customerDAO).softDelete(7);
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?kind=buyer");
    }

    /**
     * Xoá xong phải về ĐÚNG danh sách người dùng vừa đứng.
     *
     * <p>Sau softDelete thì không đọc lại được vai của bản ghi nữa, nên chiều phải
     * đi theo tham số kind do form xoá gửi lên. Thiếu nó thì xoá một nhà cung cấp xong
     * bị đẩy sang danh sách khách mua -- đúng lỗi đã gặp trên bản chạy thật.
     */
    @Test
    public void delete_fromSupplierList_redirectsBackToSupplierList() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("7");
        when(request.getParameter("kind")).thenReturn("supplier");
        when(customerDAO.findById(7)).thenReturn(new Enterprise());
        when(customerDAO.hasActiveContracts(7)).thenReturn(false);
        when(customerDAO.softDelete(7)).thenReturn(true);

        controller.doPost(request, response);

        verify(customerDAO).softDelete(7);
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?kind=supplier");
    }

    /**
     * Câu UPDATE hỏng thì khách vẫn còn: về trang chi tiết kèm lỗi, không về danh
     * sách như đã xoá xong. Request không gửi kind thì không tự thêm vào -- trang
     * chi tiết tự suy từ vai của khách.
     */
    @Test
    public void delete_softDeleteFails_redirectsToDetailWithDeleteFailed() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("7");
        when(customerDAO.findById(7)).thenReturn(new Enterprise());
        when(customerDAO.hasActiveContracts(7)).thenReturn(false);
        when(customerDAO.softDelete(7)).thenReturn(false);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=7&error=delete_failed");
        verify(response, never()).sendRedirect(startsWith(CONTEXT_PATH + "/customer?kind="));
    }

    /** Xoá hỏng từ phía nhà cung cấp: trang chi tiết vẫn phải ở đúng chiều đó. */
    @Test
    public void delete_softDeleteFailsFromSupplierSide_keepsKindOnDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("7");
        when(request.getParameter("kind")).thenReturn("supplier");
        when(customerDAO.findById(7)).thenReturn(new Enterprise());
        when(customerDAO.hasActiveContracts(7)).thenReturn(false);
        when(customerDAO.softDelete(7)).thenReturn(false);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=7&kind=supplier&error=delete_failed");
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

        verify(customerDAO, never()).recordEvaluation(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=3&error=invalid_rating");
    }

    @Test
    public void evaluate_validRating_updatesRatingAndRecordsLifecycleEvent() throws Exception {
        when(request.getParameter("action")).thenReturn("evaluate");
        when(request.getParameter("id")).thenReturn("3");
        when(request.getParameter("rating")).thenReturn("GOOD");
        when(request.getParameter("description")).thenReturn("Khách hàng thân thiết");
        when(customerDAO.findById(3)).thenReturn(new Enterprise());
        when(customerDAO.recordEvaluation(any())).thenReturn(true);

        controller.doPost(request, response);

        verify(customerDAO).recordEvaluation(argThat((CustomerLifecycleEvent event) ->
                event.getEnterpriseId() == 3
                        && event.getRelationshipRating() == RelationshipRating.GOOD
                        && !event.isAutoGenerated()
                        && event.getRecordedBy() == 99));
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=3&evaluated=1");
    }

    /**
     * Ghi đánh giá hỏng (xếp hạng + lịch sử cùng một transaction): trang KHÔNG
     * được báo "Đã đánh giá lại" như trước đây.
     */
    @Test
    public void evaluate_recordFails_redirectsWithEvaluateFailed() throws Exception {
        when(request.getParameter("action")).thenReturn("evaluate");
        when(request.getParameter("id")).thenReturn("3");
        when(request.getParameter("rating")).thenReturn("GOOD");
        when(customerDAO.findById(3)).thenReturn(new Enterprise());
        when(customerDAO.recordEvaluation(any())).thenReturn(false);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=3&error=evaluate_failed");
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
        // Vai là bắt buộc từ V20: khách không vai nào sẽ không xuất hiện ở cả
        // hai danh sách, nên request hợp lệ phải có ít nhất một.
        when(request.getParameterValues("roles")).thenReturn(new String[]{"Khách mua"});
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

        verify(customerDAO, never()).recordEvaluation(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=invalid");
    }

    // ------------------------------------------------------------------
    // BR-27 -- email/SĐT/MST duy nhất, và người dùng phải BIẾT ô nào trùng
    // ------------------------------------------------------------------
    //
    // Ba cột đều có UNIQUE KEY nên luật vốn được CSDL giữ. Nhưng nếu không
    // kiểm trước thì lỗi rơi xuống tận DB, bật lên thành SQLException, và màn
    // hình chỉ hiện "create_failed" chung chung -- gõ nhầm một chữ số thành số
    // của khách khác thì không hiểu vì sao, gõ lại y nguyên rồi lại hỏng.

    @Test
    public void create_trungEmail_baoDungODoVaKhongGhi() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(customerDAO.existsByEmail("abc@example.com", null)).thenReturn(true);

        controller.doPost(request, response);

        verify(customerDAO, never()).insert(any(Enterprise.class));
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=duplicate_email");
    }

    @Test
    public void create_trungSoDienThoai_baoDungODoVaKhongGhi() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(customerDAO.existsByPhone("0912345678", null)).thenReturn(true);

        controller.doPost(request, response);

        verify(customerDAO, never()).insert(any(Enterprise.class));
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=duplicate_phone");
    }

    @Test
    public void create_trungMaSoThue_baoDungODoVaKhongGhi() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(customerDAO.existsByTaxCode("0101234567", null)).thenReturn(true);

        controller.doPost(request, response);

        verify(customerDAO, never()).insert(any(Enterprise.class));
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=duplicate_tax_code");
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

        verify(customerDAO, never()).recordEvaluation(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=invalid");
    }

    @Test
    public void create_invalidPhone_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("phone")).thenReturn("not-a-phone"); // vi phạm BR-09

        controller.doPost(request, response);

        verify(customerDAO, never()).recordEvaluation(any());
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
            verify(customerDAO, never()).recordEvaluation(any());
            verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=invalid_image_type");
        }
    }

    @Test
    public void create_invalidEmail_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("email")).thenReturn("not-an-email"); // vi phạm BR-10

        controller.doPost(request, response);

        verify(customerDAO, never()).recordEvaluation(any());
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

    /**
     * Hồ sơ lưu được mà ghi vai hỏng: khách tồn tại nhưng không nằm trong danh
     * sách nào (bộ lọc theo vai). Phải về trang chi tiết kèm roles_not_saved để
     * người dùng mở Sửa tick lại vai -- không được về như đã tạo xong, và không
     * xoá mềm bản vừa tạo (MST/email/SĐT UNIQUE cả trên bản ghi đã xoá mềm).
     */
    @Test
    public void create_rolesNotSaved_redirectsToDetailWithRolesNotSaved() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0001");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(42);
        when(customerDAO.replaceRolesOf(eq(42), any())).thenReturn(false);

        controller.doPost(request, response);

        verify(customerDAO, never()).softDelete(anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=42&error=roles_not_saved");
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
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(42);
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
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(null);
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
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(42);
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0001");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(7);

        controller.doPost(request, response);

        // Tra theo xã/phường (10), và người được lưu là người của xã/phường
        // đó -- không phải của tỉnh 99 mà request khai.
        verify(employeeDAO).findAssigneeOfWard(10);
        ArgumentCaptor<Enterprise> saved = ArgumentCaptor.forClass(Enterprise.class);
        verify(customerDAO).insert(saved.capture());
        assertEquals(42, saved.getValue().getAccountOwnerId());
    }

    /**
     * Form sửa phải khoá y hệt form tạo, nếu không thì tạo khách xong mở
     * trang sửa đổi lại người phụ trách là đường vòng thoát khoá. (Admin --
     * Sales thì luôn giữ người đang phụ trách, xem các test salesCoTinh.)
     */
    @Test
    public void update_provinceHasOwner_overridesSubmittedAccountOwner() throws Exception {
        loginAs("Admin");
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("customerId")).thenReturn("5");
        Enterprise existing = new Enterprise();
        existing.setEnterpriseId(5);
        existing.setAddressId(31);
        when(customerDAO.findById(5)).thenReturn(existing);

        stubValidCreateFields();
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(42);
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

    // ------------------------------------------------------------------
    // Vai khách hàng: khách mua / nhà cung cấp (V20)
    // ------------------------------------------------------------------
    //
    // Từ khi tách hai trang tạo (2026-09-24), vai lúc TẠO suy từ trang (kind)
    // chứ không từ ô tick -- mỗi trang đúng một vai, nên "không vai nào" và
    // "vai lạ" không lọt được qua đường tạo nữa. Trang Sửa tách theo cùng cách
    // (2026-10-01): vai của trang luôn giữ, ô tick chỉ thêm/bỏ vai KIA.
    //
    // Vai nằm ở bảng riêng (enterprise_roles) nên CSDL không ép được luật
    // "ít nhất một vai" -- ràng buộc nói về sự tồn tại của dòng ở bảng khác,
    // CHECK không với tới. Những test này canh đúng chỗ chặn duy nhất.

    /** Thiếu kind là trang khách hàng: vai Khách mua, mã dãy KH. */
    @Test
    public void create_trangKhachHang_ghiVaiKhachMua() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameterValues("roles")).thenReturn(null);
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0001");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(7);

        controller.doPost(request, response);

        verify(customerDAO).replaceRolesOf(7, List.of("Khách mua"));
        verify(customerDAO, never()).generateNextSupplierCode();
    }

    /**
     * Ô tick vai gửi kèm -- request nặn tay, hoặc form cũ còn mở sẵn trong
     * trình duyệt -- bị bỏ qua: vai theo trang, kể cả vai lạ không lọt vào.
     */
    @Test
    public void create_oTickVaiGuiLen_biBoQua() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameterValues("roles"))
                .thenReturn(new String[]{"Khách mua", "Nhà cung cấp", "Khách VIP"});
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0001");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(7);

        controller.doPost(request, response);

        verify(customerDAO).replaceRolesOf(7, List.of("Khách mua"));
    }

    /** Trang nhà cung cấp: vai Nhà cung cấp và mã dãy NCC, không phải KH. */
    @Test
    public void create_trangNhaCungCap_ghiVaiNhaCungCapVaMaNcc() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("kind")).thenReturn("supplier");
        stubValidCreateFields();
        when(customerDAO.generateNextSupplierCode()).thenReturn("NCC-005");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(7);

        controller.doPost(request, response);

        ArgumentCaptor<Enterprise> saved = ArgumentCaptor.forClass(Enterprise.class);
        verify(customerDAO).insert(saved.capture());
        assertEquals("NCC-005", saved.getValue().getEnterpriseCode());
        verify(customerDAO, never()).generateNextEnterpriseCode();
        verify(customerDAO).replaceRolesOf(7, List.of("Nhà cung cấp"));
    }

    /** Lỗi ở trang nhà cung cấp phải quay về ĐÚNG trang đó, không rơi sang trang khách hàng. */
    @Test
    public void create_trangNhaCungCapLoi_quayVeTrangNhaCungCap() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("kind")).thenReturn("supplier");
        stubValidCreateFields();
        when(request.getParameter("phone")).thenReturn("not-a-phone");

        controller.doPost(request, response);

        verify(customerDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&kind=supplier&error=invalid");
    }

    private void stubValidUpdateOfCustomer5() {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("customerId")).thenReturn("5");
        Enterprise existing = new Enterprise();
        existing.setEnterpriseId(5);
        existing.setAddressId(31);
        // Cột NOT NULL: khách nào cũng có người phụ trách, và Sales sửa thì giữ
        // nguyên người đó -- cùng người với ô form gửi lên (9).
        existing.setAccountOwnerId(9);
        when(customerDAO.findById(5)).thenReturn(existing);
        stubValidCreateFields();
    }

    /**
     * Trang Sửa khách hàng không tick gì: vẫn giữ vai khách mua của trang.
     * Trước khi tách, bỏ tick cả hai vai là khách biến khỏi CẢ HAI danh sách.
     */
    @Test
    public void update_trangKhachHangKhongTickGi_giuVaiCuaTrang() throws Exception {
        stubValidUpdateOfCustomer5();
        when(request.getParameterValues("roles")).thenReturn(null);
        when(customerDAO.update(any(Enterprise.class))).thenReturn(true);

        controller.doPost(request, response);

        verify(customerDAO).replaceRolesOf(5, List.of("Khách mua"));
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=5");
    }

    /** Trang Sửa nhà cung cấp: giữ vai nhà cung cấp, bỏ tick ô kia là bỏ vai khách mua. */
    @Test
    public void update_trangNhaCungCapKhongTickGi_chiConVaiNhaCungCap() throws Exception {
        loginAs("Admin");
        stubValidUpdateOfCustomer5();
        when(request.getParameter("kind")).thenReturn("supplier");
        when(request.getParameterValues("roles")).thenReturn(null);
        when(customerDAO.update(any(Enterprise.class))).thenReturn(true);

        controller.doPost(request, response);

        verify(customerDAO).replaceRolesOf(5, List.of("Nhà cung cấp"));
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=5&kind=supplier");
    }

    /** Lỗi ở trang Sửa nhà cung cấp quay về ĐÚNG trang đó, như trang Thêm. */
    @Test
    public void update_trangNhaCungCapLoi_quayVeTrangNhaCungCap() throws Exception {
        stubValidUpdateOfCustomer5();
        when(request.getParameter("kind")).thenReturn("supplier");
        when(request.getParameter("phone")).thenReturn("not-a-phone");

        controller.doPost(request, response);

        verify(customerDAO, never()).update(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=edit&id=5&kind=supplier&error=invalid");
    }

    /**
     * Cột enterprise_roles.role là varchar tự do. Nhận thẳng chuỗi gửi lên là
     * một request nặn tay ghi được vai "Khách VIP" vào đó -- bỏ qua, chỉ còn
     * vai của trang.
     */
    @Test
    public void update_vaiLa_biLocBo() throws Exception {
        stubValidUpdateOfCustomer5();
        when(request.getParameterValues("roles")).thenReturn(new String[]{"Khách VIP"});
        when(customerDAO.update(any(Enterprise.class))).thenReturn(true);

        controller.doPost(request, response);

        verify(customerDAO).replaceRolesOf(5, List.of("Khách mua"));
    }

    /** Công ty vừa mua vừa bán: thêm vai thứ hai ở trang Sửa, giữ cả hai. */
    @Test
    public void update_caHaiVai_ghiDuCaHai() throws Exception {
        stubValidUpdateOfCustomer5();
        when(request.getParameterValues("roles"))
                .thenReturn(new String[]{"Khách mua", "Nhà cung cấp"});
        when(customerDAO.update(any(Enterprise.class))).thenReturn(true);

        controller.doPost(request, response);

        verify(customerDAO).replaceRolesOf(5, List.of("Khách mua", "Nhà cung cấp"));
    }

    /** Hồ sơ đã cập nhật, riêng bước ghi vai hỏng (vai cũ còn nguyên): báo ở trang chi tiết. */
    @Test
    public void update_rolesNotSaved_redirectsToDetailWithRolesNotSaved() throws Exception {
        stubValidUpdateOfCustomer5();
        when(request.getParameterValues("roles"))
                .thenReturn(new String[]{"Khách mua", "Nhà cung cấp"});
        when(customerDAO.update(any(Enterprise.class))).thenReturn(true);
        when(customerDAO.replaceRolesOf(eq(5), any())).thenReturn(false);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=5&error=roles_not_saved");
    }

    /**
     * Vai chỉ được ghi SAU khi biết insert thành công. Ghi trước là để lại vai
     * trỏ vào một enterprise_id không tồn tại.
     */
    @Test
    public void create_insertThatBai_khongGhiVai() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0001");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(0);

        controller.doPost(request, response);

        verify(customerDAO, never()).replaceRolesOf(anyInt(), any());
    }

    /**
     * Hai mục con trên thanh điều hướng đi vào cùng trang, khác đúng tham số
     * kind. Thiếu kind phải ra khách mua -- đó là danh sách cũ và là chiều duy
     * nhất có dữ liệu trước V20.
     */
    @Test
    public void list_kindSupplier_locDanhSachTheoVaiKhachBan() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcustomer.jsp")).thenReturn(dispatcher);
        when(request.getParameter("kind")).thenReturn("supplier");

        controller.doGet(request, response);

        verify(customerDAO).findAll(anyInt(), anyInt(), any(), any(), any(), any(), eq(false), eq("Nhà cung cấp"), any());
        verify(customerDAO).countAll(any(), any(), any(), any(), eq("Nhà cung cấp"), any());
        verify(request).setAttribute("kind", "supplier");
    }

    /**
     * Ô lọc tỉnh giờ là bảng TÍCH NHIỀU: tham số provinceId lặp lại trên URL và
     * cả hai giá trị phải xuống tới DAO. Đọc bằng getParameter() (một giá trị)
     * thì chỉ tỉnh đầu tiên có tác dụng, tỉnh còn lại rụng lặng lẽ.
     */
    @Test
    public void list_nhieuThamSoProvinceId_xuongDaoDayDu() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcustomer.jsp")).thenReturn(dispatcher);
        when(request.getParameterValues("provinceId")).thenReturn(new String[]{"3", "17"});

        controller.doGet(request, response);

        verify(customerDAO).findAll(anyInt(), anyInt(), any(), any(), any(), eq(List.of(3, 17)),
                eq(false), any(), any());
        verify(customerDAO).countAll(any(), any(), any(), eq(List.of(3, 17)), any(), any());
        verify(request).setAttribute("provinceQuery", "&provinceId=3&provinceId=17");
    }

    /**
     * Nhà cung cấp KHÔNG chia theo địa bàn. Bỏ ở controller chứ không chỉ ẩn ô
     * chọn -- provinceId còn sót trên URL vẫn âm thầm cắt mất kết quả.
     */
    @Test
    public void list_nhaCungCap_boQuaMoiThamSoProvinceId() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcustomer.jsp")).thenReturn(dispatcher);
        when(request.getParameter("kind")).thenReturn("supplier");
        when(request.getParameterValues("provinceId")).thenReturn(new String[]{"3", "17"});

        controller.doGet(request, response);

        verify(customerDAO).countAll(any(), any(), any(), eq(List.<Integer>of()), any(), any());
    }

    @Test
    public void list_thieuKind_macDinhLaKhachMua() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcustomer.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(customerDAO).findAll(anyInt(), anyInt(), any(), any(), any(), any(), eq(false), eq("Khách mua"), any());
        verify(request).setAttribute("kind", "buyer");
    }

    // ------------------------------------------------------------------
    // Phạm vi mặc định của danh sách (view=mine / view=all)
    // ------------------------------------------------------------------

    /**
     * Sales mở danh sách ra phải thấy phần việc của mình, không phải của cả chi
     * nhánh. Trước bản này một nhân viên giữ 4 khách mở ra thấy đủ 12 khách của
     * mọi người, kèm nút sửa/xoá trên từng dòng.
     */
    @Test
    public void list_vaiSales_macDinhThuHepVePhanViecCuaMinh() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcustomer.jsp")).thenReturn(dispatcher);
        when(employeeDAO.findTeamUserIds(99)).thenReturn(List.of(99));
        when(employeeDAO.findProvincesOf(99)).thenReturn(List.of());

        controller.doGet(request, response);

        ArgumentCaptor<ListScope> captor = ArgumentCaptor.forClass(ListScope.class);
        verify(customerDAO).findAll(anyInt(), anyInt(), any(), any(), any(), any(), eq(false), any(),
                captor.capture());
        assertTrue("Sales phải bị thu hẹp", captor.getValue().isNarrowed());
        assertEquals(List.of(99), captor.getValue().getOwnerIds());
        verify(request).setAttribute("viewFilter", "mine");
    }

    /** Địa bàn được giao đi kèm người phụ trách, nối bằng HOẶC chứ không phải VÀ. */
    @Test
    public void list_vaiSales_comTheoCaDiaBanDuocGiao() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcustomer.jsp")).thenReturn(dispatcher);
        when(employeeDAO.findTeamUserIds(99)).thenReturn(List.of(99));
        when(employeeDAO.findProvincesOf(99)).thenReturn(List.of(new Province(7, "Tỉnh Lào Cai")));

        controller.doGet(request, response);

        ArgumentCaptor<ListScope> captor = ArgumentCaptor.forClass(ListScope.class);
        verify(customerDAO).findAll(anyInt(), anyInt(), any(), any(), any(), any(), eq(false), any(),
                captor.capture());
        assertEquals(List.of(7), captor.getValue().getProvinceIds());
    }

    /**
     * Admin không bị thu hẹp -- họ quản trị chứ không cầm khách, thu hẹp theo "khách
     * của tôi" là họ thấy đúng 0 dòng.
     */
    @Test
    public void list_vaiAdmin_khongThuHep() throws Exception {
        loginAs("Admin");
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcustomer.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        ArgumentCaptor<ListScope> captor = ArgumentCaptor.forClass(ListScope.class);
        verify(customerDAO).findAll(anyInt(), anyInt(), any(), any(), any(), any(), eq(false), any(),
                captor.capture());
        assertFalse("Admin không bị thu hẹp", captor.getValue().isNarrowed());
        verify(employeeDAO, never()).findTeamUserIds(anyInt());
    }

    /** Thu hẹp là MẶC ĐỊNH chứ không phải rào quyền: view=all nới lại được. */
    @Test
    public void list_viewAll_noiLaiToanChiNhanh() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcustomer.jsp")).thenReturn(dispatcher);
        when(request.getParameter("view")).thenReturn("all");

        controller.doGet(request, response);

        ArgumentCaptor<ListScope> captor = ArgumentCaptor.forClass(ListScope.class);
        verify(customerDAO).findAll(anyInt(), anyInt(), any(), any(), any(), any(), eq(false), any(),
                captor.capture());
        assertFalse(captor.getValue().isNarrowed());
        verify(request).setAttribute("viewFilter", "all");
    }

    // ------------------------------------------------------------------
    // Dropdown "người phụ trách" chỉ liệt kê Sales
    // ------------------------------------------------------------------
    //
    // Khách hàng giao cho Sales. Trước đây dropdown đổ findAllActive() --
    // toàn bộ nhân viên -- nên ô "Người phụ trách chính" mời chọn cả Admin
    // lẫn kỹ thuật viên lẫn CSKH.

    @Test
    public void createForm_chiDoNhanVienSalesVaoDropdown() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/addnewcustomer.jsp")).thenReturn(dispatcher);
        when(request.getParameter("action")).thenReturn("new");

        controller.doGet(request, response);

        verify(employeeDAO).findActiveByRole("Sales");
        verify(employeeDAO, never()).findAllActive();
    }

    /**
     * Form sửa phải giữ lại người ĐANG phụ trách kể cả khi họ đã đổi vai --
     * nếu không thì mở form lên ô trống, bấm lưu là thay mất người phụ trách
     * dù người dùng chỉ định sửa số điện thoại.
     */
    @Test
    public void editForm_giuLaiCaHaiVaiDangGanDuNguoiDoDaDoiVai() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/updatecustomer.jsp")).thenReturn(dispatcher);
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("5");

        Enterprise customer = new Enterprise();
        customer.setEnterpriseId(5);
        customer.setAccountOwnerId(41);
        customer.setSupportOwnerId(42);
        when(customerDAO.findById(5)).thenReturn(customer);

        controller.doGet(request, response);

        verify(employeeDAO).findActiveByRole("Sales", 41, 42);
        verify(employeeDAO, never()).findAllActive();
    }

    @Test
    public void listPage_oLocNguoiPhuTrachChiLietKeSales() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcustomer.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(employeeDAO).findActiveByRole("Sales");
        verify(employeeDAO, never()).findAllActive();
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

    /**
     * Xoá từ danh sách Nhà cung cấp một bản ghi đã không còn (tab khác vừa xoá):
     * phải về lại danh sách Nhà cung cấp kèm thông báo, không bị đẩy sang danh
     * sách Khách hàng mua. Form xoá ở listcustomer.jsp gửi kèm kind cho việc này.
     */
    @Test
    public void delete_notFoundFromSupplierList_keepsSupplierList() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("999999");
        when(request.getParameter("kind")).thenReturn("supplier");
        when(customerDAO.findById(999999)).thenReturn(null);

        controller.doPost(request, response);

        verify(customerDAO, never()).softDelete(anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?error=notfound&kind=supplier");
    }

    /** Cùng lẽ với trên, cho link Xem / Sửa mở từ danh sách Nhà cung cấp. */
    @Test
    public void viewVaEdit_notFoundFromSupplierList_keepsSupplierList() throws Exception {
        for (String action : new String[]{"view", "edit"}) {
            HttpServletRequest req = mock(HttpServletRequest.class);
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(req.getSession(false)).thenReturn(session);
            when(req.getContextPath()).thenReturn(CONTEXT_PATH);
            when(req.getParameter("action")).thenReturn(action);
            when(req.getParameter("id")).thenReturn("123");
            when(req.getParameter("kind")).thenReturn("supplier");
            when(customerDAO.findById(123)).thenReturn(null);

            controller.doGet(req, resp);

            verify(resp).sendRedirect(CONTEXT_PATH + "/customer?error=notfound&kind=supplier");
        }
    }

    /** kind lạ (hoặc buyer) thì về danh sách mặc định -- không chép nguyên chuỗi lên URL. */
    @Test
    public void view_notFoundWithOddKind_fallsBackToDefaultList() throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("123");
        when(request.getParameter("kind")).thenReturn("x&evil=1");
        when(customerDAO.findById(123)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?error=notfound");
    }

    // ------------------------------------------------------------------
    // Sales đã được giao tỉnh: khách đứng tên mình, chỉ trong tỉnh mình cầm
    // ------------------------------------------------------------------
    //
    // Chốt với người dùng 2026-09-24. Khoá ở JSP chỉ là khoá hình, nên các
    // test dưới POST thẳng accountOwnerId của người khác và xã/phường ngoài
    // địa bàn -- đúng như một request nặn tay.

    /** Người đang đăng nhập (user 99) trực tiếp cầm các tỉnh này. */
    private void holdsProvinces(Province... provinces) {
        when(employeeDAO.findProvincesOf(99)).thenReturn(List.of(provinces));
    }

    @Test
    public void create_salesCoTinh_khachDungTenMinhDuGuiTenNguoiKhac() throws Exception {
        holdsProvinces(new Province(1, "Thành phố Hà Nội"));
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields(); // accountOwnerId=9, districtId=10
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(99);
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0014");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(7);

        controller.doPost(request, response);

        ArgumentCaptor<Enterprise> saved = ArgumentCaptor.forClass(Enterprise.class);
        verify(customerDAO).insert(saved.capture());
        assertEquals(99, saved.getValue().getAccountOwnerId());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=7");
    }

    /**
     * Xã/phường thuộc tỉnh người khác cầm: chặn hẳn. Với Admin thì cùng request
     * này lưu được và khách đứng tên người cầm tỉnh -- Sales thì không được tạo
     * khách hộ địa bàn người khác.
     */
    @Test
    public void create_salesCoTinh_xaThuocTinhNguoiKhac_biChan() throws Exception {
        holdsProvinces(new Province(1, "Thành phố Hà Nội"));
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(42);

        controller.doPost(request, response);

        verify(customerDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=province_not_allowed");
    }

    /** Tỉnh chưa ai cầm cũng nằm ngoài địa bàn của mình. */
    @Test
    public void create_salesCoTinh_xaOTinhChuaAiCam_biChan() throws Exception {
        holdsProvinces(new Province(1, "Thành phố Hà Nội"));
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(null);

        controller.doPost(request, response);

        verify(customerDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=province_not_allowed");
    }

    /**
     * Sales CHƯA được giao tỉnh thì chạy như Admin -- người phụ trách theo địa
     * bàn -- để CSKH vẫn tạo được khách hộ người cầm tỉnh.
     */
    @Test
    public void create_salesChuaCoTinh_nguoiPhuTrachTheoDiaBan() throws Exception {
        holdsProvinces();
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(42);
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0014");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(7);

        controller.doPost(request, response);

        ArgumentCaptor<Enterprise> saved = ArgumentCaptor.forClass(Enterprise.class);
        verify(customerDAO).insert(saved.capture());
        assertEquals(42, saved.getValue().getAccountOwnerId());
    }

    /** Luật khoá chỉ dành cho tài khoản Sales: Admin có cầm tỉnh cũng vẫn theo địa bàn. */
    @Test
    public void create_adminCoCamTinh_vanTheoDiaBan() throws Exception {
        loginAs("Admin");
        holdsProvinces(new Province(1, "Thành phố Hà Nội"));
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(42);
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0014");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(7);

        controller.doPost(request, response);

        ArgumentCaptor<Enterprise> saved = ArgumentCaptor.forClass(Enterprise.class);
        verify(customerDAO).insert(saved.capture());
        assertEquals(42, saved.getValue().getAccountOwnerId());
    }

    /**
     * Nhà cung cấp không chia theo địa bàn: Sales tạo thì đứng tên chính mình,
     * kể cả khi văn phòng nhà cung cấp nằm ở tỉnh người khác cầm.
     */
    @Test
    public void create_nhaCungCapDoSalesTao_dungTenNguoiTao() throws Exception {
        holdsProvinces(new Province(1, "Thành phố Hà Nội"));
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("kind")).thenReturn("supplier");
        stubValidCreateFields();
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(42);
        when(customerDAO.generateNextSupplierCode()).thenReturn("NCC-005");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(7);

        controller.doPost(request, response);

        ArgumentCaptor<Enterprise> saved = ArgumentCaptor.forClass(Enterprise.class);
        verify(customerDAO).insert(saved.capture());
        assertEquals(99, saved.getValue().getAccountOwnerId());
    }

    /** Admin tạo nhà cung cấp: giữ người được chọn, bảng phân công không ghi đè. */
    @Test
    public void create_nhaCungCapDoAdminTao_giuNguoiDuocChon() throws Exception {
        loginAs("Admin");
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("kind")).thenReturn("supplier");
        stubValidCreateFields(); // accountOwnerId=9
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(42);
        when(customerDAO.generateNextSupplierCode()).thenReturn("NCC-005");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(7);

        controller.doPost(request, response);

        ArgumentCaptor<Enterprise> saved = ArgumentCaptor.forClass(Enterprise.class);
        verify(customerDAO).insert(saved.capture());
        assertEquals(9, saved.getValue().getAccountOwnerId());
    }

    private void openCreateForm(String kind) {
        when(request.getRequestDispatcher("/jsp/sale/addnewcustomer.jsp")).thenReturn(mock(RequestDispatcher.class));
        when(request.getParameter("action")).thenReturn("new");
        when(request.getParameter("kind")).thenReturn(kind);
    }

    /**
     * Sales cầm nhiều tỉnh: dropdown chỉ còn các tỉnh đó, xếp theo tên ngắn như
     * mọi ô tỉnh khác; người phụ trách khoá tên mình nên không nhúng bảng phân công.
     */
    @Test
    public void createForm_salesCoTinh_chiTinhMinhVaKhoaTenMinh() throws Exception {
        openCreateForm(null);
        Province haNoi = new Province(1, "Thành phố Hà Nội");
        Province bacNinh = new Province(2, "Tỉnh Bắc Ninh");
        // findProvincesOf xếp theo tên đầy đủ: "Thành phố ..." đứng trước.
        holdsProvinces(haNoi, bacNinh);

        controller.doGet(request, response);

        verify(request).setAttribute("provinceList", List.of(bacNinh, haNoi));
        verify(request).setAttribute(eq("lockedOwner"), any(User.class));
        verify(employeeDAO, never()).findAllAssignments();
        verify(addressDAO, never()).findBranchProvinces();
    }

    /** Sales chưa được giao tỉnh: trang chạy như cũ -- 18 tỉnh chi nhánh, người phụ trách theo địa bàn. */
    @Test
    public void createForm_salesChuaCoTinh_chayNhuCu() throws Exception {
        openCreateForm(null);
        holdsProvinces();

        controller.doGet(request, response);

        verify(addressDAO).findBranchProvinces();
        verify(employeeDAO).findAllAssignments();
        verify(request, never()).setAttribute(eq("lockedOwner"), any());
    }

    /** Trang nhà cung cấp: cả 34 tỉnh, không theo địa bàn; Sales bị khoá tên mình. */
    @Test
    public void createForm_nhaCungCap_ca34TinhVaKhoaTenSales() throws Exception {
        openCreateForm("supplier");
        holdsProvinces(new Province(1, "Thành phố Hà Nội"));

        controller.doGet(request, response);

        verify(request).setAttribute("kind", "supplier");
        verify(addressDAO).findAllProvinces();
        verify(addressDAO, never()).findBranchProvinces();
        verify(employeeDAO, never()).findAllAssignments();
        verify(request).setAttribute(eq("lockedOwner"), any(User.class));
    }

    @Test
    public void createForm_nhaCungCapCuaAdmin_khongKhoaTen() throws Exception {
        loginAs("Admin");
        openCreateForm("supplier");

        controller.doGet(request, response);

        verify(request, never()).setAttribute(eq("lockedOwner"), any());
    }

    // ------------------------------------------------------------------
    // Người hỗ trợ không được là cấp trên trực tiếp của người phụ trách
    // ------------------------------------------------------------------
    //
    // Chốt với người dùng 2026-09-24. Form lọc sẵn ô này, nhưng lọc trên
    // form ai mở devtools cũng gỡ được -- các test dưới POST thẳng.

    /** Người phụ trách 9 có cấp trên 77: chọn 77 làm người hỗ trợ thì chặn. */
    @Test
    public void create_nguoiHoTroLaCapTrenTrucTiep_biChan() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields(); // người phụ trách 9 (xã 10 chưa ai cầm)
        when(request.getParameter("supportOwnerId")).thenReturn("77");
        when(employeeDAO.findManagerMap()).thenReturn(Map.of(9, 77));

        controller.doPost(request, response);

        verify(customerDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=new&error=support_is_superior");
    }

    /** Trưởng nhóm KHÁC (55 quản lý người 12, không quản lý 9) thì vẫn được. */
    @Test
    public void create_truongNhomKhac_vanDuocLamNguoiHoTro() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("supportOwnerId")).thenReturn("55");
        when(employeeDAO.findManagerMap()).thenReturn(Map.of(9, 77, 12, 55));
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0014");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(7);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=7");
    }

    /** Cấp DƯỚI của người phụ trách làm người hỗ trợ thì được. */
    @Test
    public void create_capDuoiCuaNguoiPhuTrach_duocLamNguoiHoTro() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("supportOwnerId")).thenReturn("31");
        when(employeeDAO.findManagerMap()).thenReturn(Map.of(31, 9));
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0014");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(7);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=7");
    }

    /** Trang Sửa chặn y như trang tạo, nếu không thì tạo xong sửa lại là đường vòng. */
    @Test
    public void update_nguoiHoTroLaCapTrenTrucTiep_biChan() throws Exception {
        stubValidUpdateOfCustomer5();
        when(request.getParameter("supportOwnerId")).thenReturn("77");
        when(employeeDAO.findManagerMap()).thenReturn(Map.of(9, 77));

        controller.doPost(request, response);

        verify(customerDAO, never()).update(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=edit&id=5&error=support_is_superior");
    }

    @Test
    public void createForm_nhungCayCapTrenDeLocNguoiHoTro() throws Exception {
        openCreateForm(null);
        Map<Integer, Integer> cay = Map.of(22, 23);
        when(employeeDAO.findManagerMap()).thenReturn(cay);

        controller.doGet(request, response);

        verify(request).setAttribute("managerOf", cay);
    }

    // ------------------------------------------------------------------
    // Sửa / Xoá / Đánh giá: Sales chỉ ghi lên khách của mình
    // ------------------------------------------------------------------
    //
    // Chốt với người dùng 2026-09-26. Nút đã ẩn ở danh sách và trang chi tiết,
    // nhưng ẩn nút chỉ là lớp trình bày -- các test dưới gọi thẳng GET/POST.
    // Người đang đăng nhập là user 99; tỉnh 1 là tỉnh của 99, tỉnh 2 của
    // người 42, tỉnh 3 chưa ai cầm.

    private static final Province TINH_1 = new Province(1, "Thành phố Hà Nội");
    private static final Province TINH_2 = new Province(2, "Tỉnh Bắc Ninh");

    /** Khách 5 đứng tên {@code ownerId}, địa chỉ ở tỉnh {@code provinceId}. */
    private Enterprise khach5(int ownerId, int provinceId, String... roles) {
        District ward = new District();
        ward.setProvinceId(provinceId);
        Address address = new Address();
        address.setDistrict(ward);
        Enterprise e = new Enterprise();
        e.setEnterpriseId(5);
        e.setAccountOwnerId(ownerId);
        e.setAddressId(31);
        e.setAddress(address);
        when(customerDAO.findById(5)).thenReturn(e);
        when(customerDAO.findRolesOf(5)).thenReturn(List.of(roles.length == 0 ? new String[]{"Khách mua"} : roles));
        return e;
    }

    private void openEditForm5() {
        when(request.getRequestDispatcher("/jsp/sale/updatecustomer.jsp")).thenReturn(mock(RequestDispatcher.class));
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("5");
    }

    /** POST sửa khách 5, xã/phường 10 thuộc tỉnh {@code wardProvinceId}. */
    private void postUpdate5(int wardProvinceId) {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("customerId")).thenReturn("5");
        stubValidCreateFields(); // accountOwnerId=9, districtId=10, vai Khách mua
        when(addressDAO.findProvinceIdOfWard(10)).thenReturn(wardProvinceId);
        when(customerDAO.update(any(Enterprise.class))).thenReturn(true);
    }

    private int savedOwner() {
        ArgumentCaptor<Enterprise> saved = ArgumentCaptor.forClass(Enterprise.class);
        verify(customerDAO).update(saved.capture());
        return saved.getValue().getAccountOwnerId();
    }

    @Test
    public void editForm_salesCoTinh_khachNguoiKhac_quayVeChiTietKemThongBao() throws Exception {
        holdsProvinces(TINH_1);
        khach5(42, 2);
        openEditForm5();

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=5&error=not_your_customer");
        verify(request, never()).getRequestDispatcher("/jsp/sale/updatecustomer.jsp");
    }

    @Test
    public void update_salesCoTinh_khachNguoiKhac_biChanKhongGhi() throws Exception {
        holdsProvinces(TINH_1);
        khach5(42, 2);
        postUpdate5(1);

        controller.doPost(request, response);

        verify(customerDAO, never()).update(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=5&error=not_your_customer");
    }

    @Test
    public void delete_salesCoTinh_khachNguoiKhac_biChanKhongXoa() throws Exception {
        holdsProvinces(TINH_1);
        khach5(42, 2);
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");

        controller.doPost(request, response);

        verify(customerDAO, never()).softDelete(anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=5&error=not_your_customer");
    }

    @Test
    public void evaluate_salesCoTinh_khachNguoiKhac_biChanKhongGhiXepHang() throws Exception {
        holdsProvinces(TINH_1);
        khach5(42, 2);
        when(request.getParameter("action")).thenReturn("evaluate");
        when(request.getParameter("id")).thenReturn("5");
        when(request.getParameter("rating")).thenReturn("GOOD");

        controller.doPost(request, response);

        verify(customerDAO, never()).recordEvaluation(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=5&error=not_your_customer");
    }

    /** Khách ở tỉnh mình cầm mà người khác đứng tên vẫn là việc của mình -- như danh sách "Của tôi". */
    @Test
    public void editForm_salesCoTinh_khachOTinhMinhDoNguoiKhacDungTen_vanMoDuoc() throws Exception {
        holdsProvinces(TINH_1);
        khach5(42, 1);
        openEditForm5();

        controller.doGet(request, response);

        verify(response, never()).sendRedirect(anyString());
        // Mở được nhưng không chuyển khách về tên mình: ô người phụ trách khoá
        // người đang đứng tên, không nhúng bảng phân công.
        verify(request).setAttribute(eq("lockedOwner"), any());
        verify(employeeDAO, never()).findAllAssignments();
    }

    /**
     * Khách mình đứng tên nhưng nằm ở tỉnh người khác (dữ liệu cũ): mở được, ô
     * tỉnh gồm tỉnh mình cầm CỘNG tỉnh hiện tại của khách -- thiếu nó thì mở form
     * lên ô tỉnh trống, bấm lưu là mất địa chỉ.
     */
    @Test
    public void editForm_salesCoTinh_khachMinhOTinhNguoiKhac_giuTinhHienTaiTrongDanhSach() throws Exception {
        holdsProvinces(TINH_1);
        khach5(99, 2);
        when(addressDAO.findAllProvinces()).thenReturn(List.of(TINH_2, TINH_1));
        openEditForm5();

        controller.doGet(request, response);

        verify(request).setAttribute("provinceList", List.of(TINH_1, TINH_2));
        verify(addressDAO, never()).findBranchProvincesIncluding(any());
    }

    /** Chuyển khách sang một tỉnh không phải của mình và không phải tỉnh hiện tại: chặn, chưa ghi gì. */
    @Test
    public void update_salesCoTinh_chuyenSangTinhNguoiKhac_biChan() throws Exception {
        holdsProvinces(TINH_1);
        khach5(99, 1);
        postUpdate5(2);

        controller.doPost(request, response);

        verify(customerDAO, never()).update(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=edit&id=5&error=province_not_allowed");
    }

    /**
     * Sales sửa thì người phụ trách chính giữ cố định (chốt 2026-10-01): khách
     * ở tỉnh mình cầm mà người khác đứng tên, lưu xong vẫn đứng tên người đó --
     * không về tên mình theo địa bàn, không theo tên form gửi lên (9).
     */
    @Test
    public void update_salesCoTinh_trongTinhMinh_giuNguoiDangPhuTrach() throws Exception {
        holdsProvinces(TINH_1);
        khach5(42, 1);
        postUpdate5(1);
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(99);

        controller.doPost(request, response);

        assertEquals(42, savedOwner());
    }

    /** Giữ tỉnh hiện tại của khách (người khác cầm) thì lưu được, vẫn đứng tên người cũ. */
    @Test
    public void update_salesCoTinh_giuTinhHienTaiCuaNguoiKhac_giuNguoiDangPhuTrach() throws Exception {
        holdsProvinces(TINH_1);
        khach5(99, 2);
        postUpdate5(2);
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(42);

        controller.doPost(request, response);

        assertEquals(99, savedOwner());
    }

    /** Tỉnh chưa ai cầm: giữ người đang phụ trách, bỏ qua tên form gửi lên (9). */
    @Test
    public void update_salesCoTinh_tinhChuaAiCam_giuNguoiDangPhuTrach() throws Exception {
        holdsProvinces(TINH_1);
        khach5(99, 3);
        postUpdate5(3);
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(null);

        controller.doPost(request, response);

        assertEquals(99, savedOwner());
    }

    /**
     * Sales chưa có tỉnh (CSKH) vẫn sửa hộ được khách người khác -- giống trang
     * Thêm -- nhưng không đổi được người phụ trách, kể cả theo địa bàn.
     */
    @Test
    public void update_salesChuaCoTinh_khachNguoiKhac_suaDuocNhungGiuNguoiDangPhuTrach() throws Exception {
        holdsProvinces();
        khach5(42, 2);
        postUpdate5(2);
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(77);

        controller.doPost(request, response);

        assertEquals(42, savedOwner());
    }

    /** Admin vẫn đổi được: khách mua theo địa bàn -- chỗ duy nhất chuyển khách khi có người nghỉ. */
    @Test
    public void update_adminSuaKhachMua_theoDiaBan() throws Exception {
        loginAs("Admin");
        khach5(42, 2);
        postUpdate5(2);
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(77);

        controller.doPost(request, response);

        assertEquals(77, savedOwner());
    }

    /** Form sửa của Sales chưa có tỉnh: ô người phụ trách khoá, không nhúng bảng phân công. */
    @Test
    public void editForm_salesChuaCoTinh_khoaNguoiDangPhuTrach() throws Exception {
        holdsProvinces();
        khach5(42, 2);
        openEditForm5();

        controller.doGet(request, response);

        verify(request).setAttribute(eq("lockedOwner"), any());
        verify(employeeDAO, never()).findAllAssignments();
    }

    /** Nhà cung cấp của người khác: Sales nào cũng bị chặn, có tỉnh hay chưa. */
    @Test
    public void editForm_nhaCungCapCuaNguoiKhac_salesChuaCoTinhCungBiChan() throws Exception {
        holdsProvinces();
        khach5(42, 2, "Nhà cung cấp");
        openEditForm5();

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/customer?action=view&id=5&error=not_your_customer");
    }

    /** Tick thêm vai "Khách mua" lúc gửi không phải là cách mở khoá: phạm vi xét trên vai đang lưu. */
    @Test
    public void update_nhaCungCapCuaNguoiKhac_tickThemVaiKhachMua_vanBiChan() throws Exception {
        holdsProvinces();
        khach5(42, 2, "Nhà cung cấp");
        postUpdate5(2);
        when(request.getParameterValues("roles")).thenReturn(new String[]{"Khách mua", "Nhà cung cấp"});

        controller.doPost(request, response);

        verify(customerDAO, never()).update(any());
    }

    /** Nhà cung cấp của mình: giữ người đang phụ trách, không theo địa bàn, bỏ qua tên gửi lên. */
    @Test
    public void update_nhaCungCapCuaMinh_salesGiuNguoiDangPhuTrach() throws Exception {
        holdsProvinces(TINH_1);
        khach5(99, 2, "Nhà cung cấp");
        postUpdate5(2);
        when(request.getParameter("kind")).thenReturn("supplier");
        when(request.getParameterValues("roles")).thenReturn(null);
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(42);

        controller.doPost(request, response);

        assertEquals(99, savedOwner());
    }

    /**
     * Admin sửa nhà cung cấp: giữ người được chọn, bảng phân công không ghi đè --
     * như trang Thêm nhà cung cấp. Trước bản này trang Sửa vẫn ép theo địa bàn.
     */
    @Test
    public void update_nhaCungCapDoAdminSua_giuNguoiDuocChon() throws Exception {
        loginAs("Admin");
        khach5(42, 2, "Nhà cung cấp");
        postUpdate5(2);
        when(request.getParameter("kind")).thenReturn("supplier");
        when(request.getParameterValues("roles")).thenReturn(null);
        when(employeeDAO.findAssigneeOfWard(10)).thenReturn(42);

        controller.doPost(request, response);

        assertEquals(9, savedOwner());
    }

    /** Form sửa nhà cung cấp: cả 34 tỉnh, không nhúng bảng phân công, Sales bị khoá người đang phụ trách. */
    @Test
    public void editForm_nhaCungCapCuaMinh_ca34TinhVaKhoaNguoiPhuTrach() throws Exception {
        holdsProvinces(TINH_1);
        khach5(99, 2, "Nhà cung cấp");
        openEditForm5();

        controller.doGet(request, response);

        verify(addressDAO).findAllProvinces();
        verify(employeeDAO, never()).findAllAssignments();
        verify(request).setAttribute(eq("lockedOwner"), any());
    }

    /** Không có kind: nhà cung cấp thuần ra trang Sửa nhà cung cấp. */
    @Test
    public void editForm_nhaCungCapKhongKind_raTrangNhaCungCap() throws Exception {
        loginAs("Admin");
        khach5(42, 2, "Nhà cung cấp");
        openEditForm5();

        controller.doGet(request, response);

        verify(request).setAttribute("kind", "supplier");
    }

    /**
     * Công ty hai vai mở từ danh sách Nhà cung cấp: trang Sửa nhà cung cấp, nhưng
     * vẫn chia theo địa bàn -- nó vẫn là khách mua.
     */
    @Test
    public void editForm_haiVaiTuDanhSachNhaCungCap_trangNhaCungCapVanTheoDiaBan() throws Exception {
        loginAs("Admin");
        khach5(42, 2, "Khách mua", "Nhà cung cấp");
        openEditForm5();
        when(request.getParameter("kind")).thenReturn("supplier");

        controller.doGet(request, response);

        verify(request).setAttribute("kind", "supplier");
        verify(employeeDAO).findAllAssignments();
    }

    /** kind không khớp vai nào của khách (link gõ tay): suy từ vai. */
    @Test
    public void editForm_kindKhongKhopVai_suyTuVai() throws Exception {
        loginAs("Admin");
        khach5(42, 2, "Khách mua");
        openEditForm5();
        when(request.getParameter("kind")).thenReturn("supplier");

        controller.doGet(request, response);

        verify(request).setAttribute("kind", "buyer");
    }

    /**
     * Một cột loại cho cả hai vai: mở trang nhà cung cấp của công ty đang mang
     * loại khách mua thì dropdown vẫn phải có loại đó, nếu không bấm lưu là mất.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void editForm_loaiThuocVaiKia_vanCoTrongDanhSach() throws Exception {
        loginAs("Admin");
        Enterprise e = khach5(42, 2, "Khách mua", "Nhà cung cấp");
        e.setCustomerType("Nhà mạng viễn thông");
        openEditForm5();
        when(request.getParameter("kind")).thenReturn("supplier");

        controller.doGet(request, response);

        ArgumentCaptor<Object> types = ArgumentCaptor.forClass(Object.class);
        verify(request).setAttribute(eq("customerTypeOptions"), types.capture());
        List<String> options = (List<String>) types.getValue();
        assertTrue(options.contains("Nhà sản xuất"));
        assertTrue(options.contains("Nhà mạng viễn thông"));
    }

    @Test
    public void view_salesCoTinh_khachNguoiKhac_anBaNutGhi() throws Exception {
        holdsProvinces(TINH_1);
        khach5(42, 2);
        when(request.getRequestDispatcher("/jsp/sale/viewcustomerdetail.jsp")).thenReturn(mock(RequestDispatcher.class));
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");

        controller.doGet(request, response);

        verify(request).setAttribute("canWrite", false);
        verify(request).setAttribute("outOfScope", true);
    }

    @Test
    public void view_admin_khachCuaAi_cungGhiDuoc() throws Exception {
        loginAs("Admin");
        khach5(42, 2);
        when(request.getRequestDispatcher("/jsp/sale/viewcustomerdetail.jsp")).thenReturn(mock(RequestDispatcher.class));
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");

        controller.doGet(request, response);

        verify(request).setAttribute("canWrite", true);
        verify(request).setAttribute("outOfScope", false);
    }

    /** Danh sách "Toàn chi nhánh": khoá đúng dòng của người khác, dòng của mình vẫn có nút. */
    @Test
    public void list_salesCoTinh_khoaDungDongCuaNguoiKhac() throws Exception {
        holdsProvinces(TINH_1);
        when(request.getRequestDispatcher("/jsp/sale/listcustomer.jsp")).thenReturn(mock(RequestDispatcher.class));
        when(request.getParameter("view")).thenReturn("all");
        Enterprise cuaMinh = khach5(99, 1);
        Enterprise cuaNguoiKhac = new Enterprise();
        cuaNguoiKhac.setEnterpriseId(6);
        cuaNguoiKhac.setAccountOwnerId(42);
        when(customerDAO.findAll(anyInt(), anyInt(), any(), any(), any(), any(), eq(false), any(), any()))
                .thenReturn(List.of(cuaMinh, cuaNguoiKhac));

        controller.doGet(request, response);

        verify(request).setAttribute("lockedIds", Set.of(6));
    }

    @Test
    public void list_admin_khongKhoaDongNao() throws Exception {
        loginAs("Admin");
        when(request.getRequestDispatcher("/jsp/sale/listcustomer.jsp")).thenReturn(mock(RequestDispatcher.class));

        controller.doGet(request, response);

        verify(request).setAttribute("lockedIds", Set.of());
    }

}
