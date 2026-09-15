package poscs.controller;

import java.lang.reflect.Field;
import java.util.List;
import java.sql.Date;
import java.time.LocalDate;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import poscs.common.Period;
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
        loginAs(roleName, null);
    }

    /**
     * @param managerId cấp trên trong cây tổ chức; null = chưa xếp vào cây, và
     *                  theo AccessControl thì "chưa xếp vào cây thì chưa bị
     *                  siết" -- đó là lý do mặc định của mọi test là null.
     */
    private void loginAs(String roleName, Integer managerId) {
        User user = new User();
        user.setUserId(99);
        user.setRole(new Role(1, roleName));
        user.setManagerId(managerId);
        when(session.getAttribute("currentUser")).thenReturn(user);
    }

    private static Contract contractWithProgress(String progressStatus) {
        Contract c = new Contract();
        c.setContractId(5);
        c.setProgressStatus(progressStatus);
        return c;
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

        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/viewcontractdetail.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(request).setAttribute("contract", contract);
        // Sales xem được hợp đồng nhưng không huỷ được bản ghi -- việc đó của
        // Admin. Trước đây cờ này tính theo trạng thái ngày tháng (BR-46 cũ).
        verify(request).setAttribute("canVoid", false);
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
        // Từ V21, đối tác phải giữ vai khớp chiều hợp đồng. Không có kind
        // trên request nghĩa là hợp đồng BÁN, nên khách #10 phải là 'Khách mua'
        // (cặp đôi CHÉO -- xem ghi chú đầu V21).
        when(customerDAO.findRolesOf(10)).thenReturn(List.of("Khách mua"));
    }

    @Test
    public void create_missingTitle_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidContractFields();
        when(request.getParameter("title")).thenReturn(null);

        controller.doPost(request, response);

        verify(contractDAO, never()).insert(any(), anyInt());
        // Redirect mang theo kind: mất nó là form tạo lại mở sai chiều, và ô
        // "Khách hàng" liệt kê nhầm nhóm đối tác.
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=new&kind=sell&error=invalid");
    }

    @Test
    public void create_signDateAfterEffectiveDate_violatesBr44_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidContractFields();
        // Ngày ký SAU ngày hiệu lực -- vi phạm BR-44 (ký <= hiệu lực <= kết thúc)
        when(request.getParameter("signDate")).thenReturn("2026-02-01");
        when(request.getParameter("effectiveDate")).thenReturn("2026-01-15");

        controller.doPost(request, response);

        verify(contractDAO, never()).insert(any(), anyInt());
        // Redirect mang theo kind: mất nó là form tạo lại mở sai chiều, và ô
        // "Khách hàng" liệt kê nhầm nhóm đối tác.
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=new&kind=sell&error=invalid");
    }

    @Test
    public void create_validFields_insertsAndRedirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidContractFields();
        when(contractDAO.generateNextContractCode()).thenReturn("HD-0001");
        when(contractDAO.insert(any(Contract.class), anyInt())).thenReturn(77);

        controller.doPost(request, response);

        verify(contractDAO).insert(any(Contract.class), anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=77");
    }

    @Test
    public void create_withoutFullAccess_returns403AndNeverInserts() throws Exception {
        loginAs("Kỹ thuật"); // không có Full access trên CONTRACT
        when(request.getParameter("action")).thenReturn("create");
        stubValidContractFields();

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(contractDAO, never()).insert(any(), anyInt());
    }

    @Test
    public void update_contractIdMissing_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("contractId")).thenReturn(null);
        stubValidContractFields();

        controller.doPost(request, response);

        verify(contractDAO, never()).update(any(), anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    @Test
    public void update_validFields_updatesAndRedirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractDAO.findById(5)).thenReturn(new Contract());
        stubValidContractFields();
        when(contractDAO.update(any(Contract.class), anyInt())).thenReturn(true);

        controller.doPost(request, response);

        verify(contractDAO).update(argThat((Contract c) -> c.getContractId() == 5), anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5");
    }

    // ------------------------------------------------------------------
    // Chiều hợp đồng: bán ra / mua vào (V21)
    // ------------------------------------------------------------------
    //
    // Cặp đôi CHÉO: hợp đồng BÁN ký với bên giữ vai 'Khách mua', hợp đồng MUA
    // ký với 'Nhà cung cấp'. CSDL không ép được (ràng buộc nằm ở enterprise_roles,
    // CHECK không với tới), nên ContractController là chốt duy nhất.

    /**
     * Chiều lấy từ mục con đang đứng, KHÔNG từ một ô nào người dùng sửa được:
     * nhận từ form là mở đường cho một request nặn tay đổi hợp đồng bán thành
     * hợp đồng mua, và con số doanh thu đã báo cáo lặng lẽ đổi nghĩa.
     */
    @Test
    public void create_kindBuy_luuChieuMuaVaDoiTacPhaiLaKhachBan() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidContractFields();
        when(request.getParameter("kind")).thenReturn("buy");
        when(customerDAO.findRolesOf(10)).thenReturn(List.of("Nhà cung cấp"));
        when(contractDAO.generateNextContractCode()).thenReturn("HD-0001");
        when(contractDAO.insert(any(Contract.class), anyInt())).thenReturn(5);

        controller.doPost(request, response);

        ArgumentCaptor<Contract> saved = ArgumentCaptor.forClass(Contract.class);
        verify(contractDAO).insert(saved.capture(), anyInt());
        assertEquals("Mua", saved.getValue().getDirection());
    }

    /**
     * Ô chọn khách hàng ở JSP đã lọc theo chiều, nhưng đó chỉ là khoá hình --
     * ai mở devtools cũng POST được một enterprise_id bất kỳ. Không chặn ở đây
     * thì hợp đồng BÁN gắn được vào nhà cung cấp, và khách đó hiện ở danh sách
     * hợp đồng bán dù không hề giữ vai khách mua.
     */
    @Test
    public void create_doiTacSaiVai_khongLuu() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidContractFields(); // không có kind -> hợp đồng BÁN
        when(customerDAO.findRolesOf(10)).thenReturn(List.of("Nhà cung cấp")); // sai chiều

        controller.doPost(request, response);

        verify(contractDAO, never()).insert(any(), anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=new&kind=sell&error=invalid");
    }

    /** Khách giữ CẢ HAI vai thì ký được cả hai chiều. */
    @Test
    public void create_doiTacGiuCaHaiVai_kyDuocChieuMua() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidContractFields();
        when(request.getParameter("kind")).thenReturn("buy");
        when(customerDAO.findRolesOf(10)).thenReturn(List.of("Khách mua", "Nhà cung cấp"));
        when(contractDAO.generateNextContractCode()).thenReturn("HD-0001");
        when(contractDAO.insert(any(Contract.class), anyInt())).thenReturn(5);

        controller.doPost(request, response);

        verify(contractDAO).insert(any(Contract.class), anyInt());
    }

    /**
     * Chiều đứng ĐỘC LẬP với kỳ. Test này canh đúng cái người dùng dặn: tách
     * hai mục con không được làm mất bộ lọc năm/quý/tháng.
     */
    @Test
    public void list_locCaChieuLanKy_khongCaiNaoNuotCaiNao() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcontract.jsp")).thenReturn(dispatcher);
        when(request.getParameter("kind")).thenReturn("buy");
        when(request.getParameter("year")).thenReturn("2026");
        when(request.getParameter("period")).thenReturn("q2");

        controller.doGet(request, response);

        ArgumentCaptor<Period> period = ArgumentCaptor.forClass(Period.class);
        verify(contractDAO).findAll(anyInt(), anyInt(), any(), any(), any(), any(), eq(false),
                period.capture(), eq("Mua"), nullable(String.class));
        assertNotNull("kỳ phải còn nguyên khi lọc theo chiều", period.getValue());
        assertEquals("Quý 2/2026", period.getValue().getLabel());
    }

    @Test
    public void list_thieuKind_macDinhLaHopDongBan() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcontract.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(contractDAO).findAll(anyInt(), anyInt(), any(), any(), any(), any(), eq(false),
                nullable(Period.class), eq("Bán"), nullable(String.class));
        verify(request).setAttribute("kind", "sell");
    }

    // ------------------------------------------------------------------
    // POST ?action=changeProgress -- trục tiến độ
    // ------------------------------------------------------------------
    //
    // KH trả lời 2026-09-15: nhân viên không tự ký hợp đồng được. Trước V24
    // câu đó không diễn đạt nổi -- signing_date NOT NULL nên tạo hợp đồng là đã
    // ký, không có khoảnh khắc nào để chặn.

    @Test
    public void sign_userWithAManager_isRefused() throws Exception {
        loginAs("Sales", 42); // có cấp trên => là nhân viên, không phải người chốt
        when(request.getParameter("action")).thenReturn("changeProgress");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("toStatus")).thenReturn(ContractDAO.PROGRESS_SIGNED);
        when(contractDAO.findById(5)).thenReturn(contractWithProgress(ContractDAO.PROGRESS_DRAFT));

        controller.doPost(request, response);

        verify(contractDAO, never()).changeProgressStatus(anyInt(), anyString(), anyInt(), any());
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }

    @Test
    public void sign_userWithoutAManager_isAllowed() throws Exception {
        when(request.getParameter("action")).thenReturn("changeProgress");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("toStatus")).thenReturn(ContractDAO.PROGRESS_SIGNED);
        when(contractDAO.findById(5)).thenReturn(contractWithProgress(ContractDAO.PROGRESS_DRAFT));
        when(contractDAO.changeProgressStatus(eq(5), eq(ContractDAO.PROGRESS_SIGNED), anyInt(), any()))
                .thenReturn(true);

        controller.doPost(request, response);

        verify(contractDAO).changeProgressStatus(eq(5), eq(ContractDAO.PROGRESS_SIGNED), eq(99), any());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5");
    }

    /**
     * Cấp dưới bị chặn ở TẦNG QUYỀN, không phải bởi luật ký.
     *
     * <p>AccessControl siết người có cấp trên xuống chỉ-xem trên Hợp đồng, nên
     * họ không thực hiện được bước tiến độ nào cả -- kể cả thanh lý, vốn không
     * liên quan gì tới chữ ký. Ghi lại ở đây vì dễ hiểu nhầm thành "luật ký
     * chặn họ", rồi có người đi nới canSign để cho thanh lý và tưởng đã xong.
     */
    @Test
    public void changeProgress_subordinate_isBlockedByAccessControl() throws Exception {
        loginAs("Sales", 42);
        when(request.getParameter("action")).thenReturn("changeProgress");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("toStatus")).thenReturn(ContractDAO.PROGRESS_LIQUIDATED);
        when(request.getParameter("progressNote")).thenReturn("Biên bản thanh lý số 12");

        controller.doPost(request, response);

        verify(contractDAO, never()).changeProgressStatus(anyInt(), anyString(), anyInt(), any());
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }

    /**
     * Ngược lại: luật ký KHÔNG được tự suy rộng sang các bước khác. Người quản
     * được hợp đồng thì thanh lý được, không phải đi qua canSign lần nữa.
     */
    @Test
    public void liquidate_managerRole_isAllowedWithReason() throws Exception {
        when(request.getParameter("action")).thenReturn("changeProgress");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("toStatus")).thenReturn(ContractDAO.PROGRESS_LIQUIDATED);
        when(request.getParameter("progressNote")).thenReturn("Biên bản thanh lý số 12");
        when(contractDAO.findById(5)).thenReturn(contractWithProgress(ContractDAO.PROGRESS_SIGNED));
        when(contractDAO.changeProgressStatus(anyInt(), anyString(), anyInt(), any())).thenReturn(true);

        controller.doPost(request, response);

        verify(contractDAO).changeProgressStatus(eq(5), eq(ContractDAO.PROGRESS_LIQUIDATED), eq(99),
                eq("Biên bản thanh lý số 12"));
    }

    @Test
    public void changeProgress_contractNotFound_redirectsWithNotFound() throws Exception {
        when(request.getParameter("action")).thenReturn("changeProgress");
        when(request.getParameter("contractId")).thenReturn("999999");
        when(request.getParameter("toStatus")).thenReturn(ContractDAO.PROGRESS_SIGNED);
        when(contractDAO.findById(999999)).thenReturn(null);

        controller.doPost(request, response);

        verify(contractDAO, never()).changeProgressStatus(anyInt(), anyString(), anyInt(), any());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    // ------------------------------------------------------------------
    // POST ?action=delete -- huỷ bản ghi NHẬP NHẦM (thay cho BR-46 cũ)
    // ------------------------------------------------------------------
    //
    // BR-46 cũ cho Sales xoá hợp đồng khi trạng thái là "Chưa hiệu lực". Điều
    // kiện đó tính theo LỊCH, nên hợp đồng ký hôm qua và hiệu lực tháng sau vẫn
    // xoá được cùng toàn bộ nội dung đã ký. Điều kiện đúng phải là chưa ký, mà
    // signing_date NOT NULL nên không bản ghi nào chưa ký -- xoá theo nghĩa
    // nghiệp vụ không còn tồn tại. Thứ còn lại là sửa hậu quả nhập liệu sai:
    // Admin, có lý do, có dấu vết.

    @Test
    public void delete_salesRole_signedContract_isRefused() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");
        when(request.getParameter("voidReason")).thenReturn("Nhập trùng");
        when(contractDAO.findById(5)).thenReturn(contractWithProgress(ContractDAO.PROGRESS_SIGNED));

        controller.doPost(request, response);

        // loginAs("Sales") ở setUp: có Full access trên CONTRACT nhưng hợp đồng
        // ĐÃ KÝ thì vẫn không huỷ được. Ranh giới này dễ bị xoá nhầm khi ai đó
        // "dọn" requireAdmin về lại requireFullAccess cho đồng bộ.
        verify(contractDAO, never()).voidRecord(anyInt(), anyInt(), anyString());
    }

    /**
     * Bản NHÁP thì ai quản được hợp đồng cũng xoá được -- nó chưa ký, chưa là
     * chứng cứ gì. Đây đúng là điều kiện của BR-46 cũ ("chưa ký"), thứ mà trước
     * V24 không với tới được vì signing_date NOT NULL khiến mọi hợp đồng đều đã
     * ký.
     */
    @Test
    public void delete_salesRole_draftContract_isAllowed() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");
        when(request.getParameter("voidReason")).thenReturn("Soạn nhầm khách hàng");
        when(contractDAO.findById(5)).thenReturn(contractWithProgress(ContractDAO.PROGRESS_DRAFT));
        when(contractDAO.voidRecord(eq(5), anyInt(), anyString())).thenReturn(true);

        controller.doPost(request, response);

        verify(contractDAO).voidRecord(5, 99, "Soạn nhầm khách hàng");
        verify(response).sendRedirect(CONTEXT_PATH + "/contract");
    }

    @Test
    public void delete_adminWithoutReason_isRefused() throws Exception {
        loginAs("Admin");
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");
        when(request.getParameter("voidReason")).thenReturn("   ");
        when(contractDAO.findById(5)).thenReturn(new Contract());

        controller.doPost(request, response);

        verify(contractDAO, never()).voidRecord(anyInt(), anyInt(), anyString());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5&error=void_reason_required");
    }

    @Test
    public void delete_adminWithReason_voidsRecordAndRedirectsToList() throws Exception {
        loginAs("Admin");
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("5");
        when(request.getParameter("voidReason")).thenReturn("Nhập trùng với HD-0042");
        when(contractDAO.findById(5)).thenReturn(contractWithProgress(ContractDAO.PROGRESS_SIGNED));
        when(contractDAO.voidRecord(eq(5), anyInt(), anyString())).thenReturn(true);

        controller.doPost(request, response);

        verify(contractDAO).voidRecord(5, 99, "Nhập trùng với HD-0042");
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

        verify(contractDAO, never()).insertProducts(anyInt(), anyList(), anyInt());
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

        verify(contractDAO, never()).insertProducts(anyInt(), anyList(), anyInt());
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
        when(contractDAO.insertProducts(eq(5), anyList(), anyInt())).thenReturn(true);

        controller.doPost(request, response);

        verify(contractDAO).insertProducts(eq(5), argThat((java.util.List<ContractProduct> items) ->
                items.size() == 1 && items.get(0).getQuantity() == 1000), anyInt());
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

        verify(contractDAO, never()).insertProducts(anyInt(), anyList(), anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5&error=add_product_invalid");
    }

    @Test
    public void removeProduct_daoFails_redirectsWithRemoveFailedError() throws Exception {
        when(request.getParameter("action")).thenReturn("removeProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("contractProductId")).thenReturn("42");
        when(contractDAO.deleteProductLine(42, 5, 99)).thenReturn(false);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5&error=remove_product_failed");
    }

    @Test
    public void removeProduct_daoSucceeds_redirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("removeProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("contractProductId")).thenReturn("42");
        when(contractDAO.deleteProductLine(42, 5, 99)).thenReturn(true);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5");
    }

    // ------------------------------------------------------------------
    // GET ?action=new / edit / importForm -- trang form cũng phải gác quyền
    // ------------------------------------------------------------------

    /**
     * Ẩn nút ở JSP (canManage) chỉ là lớp trình bày; gõ thẳng URL vẫn phải bị
     * chặn. Với importForm thì đây còn là đúng điều PERMISSIONS.md ghi -- nhập
     * PDF TẠO hợp đồng nên là thao tác Full-access, không phải "đọc".
     */
    @Test
    public void formPages_withoutFullAccess_return403InsteadOfRendering() throws Exception {
        for (String action : new String[]{"new", "edit", "importForm"}) {
            setUp(); // mock mới cho mỗi vòng, tránh verify dính lời gọi vòng trước
            loginAs("Kỹ thuật"); // chỉ View only trên CONTRACT
            when(request.getParameter("action")).thenReturn(action);
            when(request.getParameter("id")).thenReturn("5");
            when(contractDAO.findById(5)).thenReturn(new Contract());

            controller.doGet(request, response);

            verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
            verify(request, never()).getRequestDispatcher("/jsp/sale/addnewcontract.jsp");
            verify(request, never()).getRequestDispatcher("/jsp/sale/updatecontract.jsp");
            verify(request, never()).getRequestDispatcher("/jsp/sale/importcontract.jsp");
        }
    }

    @Test
    public void createForm_withFullAccess_stillRenders() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/addnewcontract.jsp")).thenReturn(dispatcher);
        when(request.getParameter("action")).thenReturn("new");

        controller.doGet(request, response);

        verify(dispatcher).forward(request, response);
        verify(response, never()).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }

    /**
     * Huỷ id không có thật: voidRecord cũng trả false cho id đó, nên nếu kiểm
     * ràng buộc trước thì người dùng nhận thông báo "không huỷ được" -- sai hẳn
     * lý do, tưởng là vướng nghiệp vụ. Phải báo không tìm thấy.
     */
    @Test
    public void delete_contractNotFound_redirectsWithNotFoundNotVoidFailed() throws Exception {
        loginAs("Admin");
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("999999");
        when(request.getParameter("voidReason")).thenReturn("Nhập nhầm");
        when(contractDAO.findById(999999)).thenReturn(null);

        controller.doPost(request, response);

        verify(contractDAO, never()).voidRecord(anyInt(), anyInt(), anyString());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    /** Sửa id không có thật: báo không tìm thấy chứ không phải dữ liệu không hợp lệ. */
    @Test
    public void update_contractNotFound_redirectsWithNotFoundNotInvalid() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("contractId")).thenReturn("999999");
        when(contractDAO.findById(999999)).thenReturn(null);

        controller.doPost(request, response);

        verify(contractDAO, never()).update(any(Contract.class), anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?error=notfound");
    }

    // ------------------------------------------------------------------
    // Bộ lọc kỳ -- giá trị đổ ra JSP
    // ------------------------------------------------------------------

    /**
     * yearFilter phải là SỐ (hoặc null), không được là chuỗi thô trên URL. JSP
     * so ${yearFilter == y} với từng số năm trong dropdown; EL gặp chuỗi không
     * phải số thì ném ELException ngay lúc ép kiểu và cả trang danh sách biến
     * thành trang lỗi -- chỉ cần ai đó gõ tay ?year=abcd. Đã dính thật khi rà
     * lại bộ lọc trên bản chạy.
     */
    @Test
    public void periodAttributes_garbageYear_exposesNullInsteadOfRawString() {
        ContractController.setPeriodAttributes(request, Period.parse("abcd", "q2"));

        verify(request).setAttribute("yearFilter", null);
        verify(request).setAttribute("periodLabel", null);
    }

    @Test
    public void periodAttributes_validYear_exposesItAsNumber() {
        ContractController.setPeriodAttributes(request, Period.parse("2026", "q2"));

        verify(request).setAttribute("yearFilter", 2026);
        verify(request).setAttribute("periodLabel", "Quý 2/2026");
    }

}
