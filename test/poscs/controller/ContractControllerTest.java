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
import poscs.model.ContractDocument;
import poscs.model.ContractHandover;
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

    /**
     * Hợp đồng đủ thời hạn -- từ V26 hai mốc đó nullable và ContractController
     * chặn việc ký khi còn thiếu, nên thiếu chúng là mọi test về ký đều
     * trượt sang nhánh missing_term.
     */
    private static Contract contractWithProgress(String progressStatus) {
        Contract c = new Contract();
        c.setContractId(5);
        c.setProgressStatus(progressStatus);
        c.setEffectiveDate(sqlDate(2026, 1, 15));
        c.setEndDate(sqlDate(2026, 12, 31));
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

    /**
     * Link bản PDF của hợp đồng đi thẳng ra màn hình, KHÔNG qua bước đổi sang
     * link /preview nữa.
     *
     * <p>Ba ca cũ quanh drivePreviewUrl (đổi /view sang /preview, link không
     * phải Drive, không có link) đã xoá cùng khung nhúng 2026-09-21 -- không
     * còn iframe thì không còn gì để đổi link cho.
     */
    @Test
    public void view_primaryDocumentUrl_dayThangRaManHinh() throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");
        Contract contract = new Contract();
        contract.setContractId(5);
        ContractDocument doc = new ContractDocument();
        doc.setDocumentId(1);
        doc.setContractId(5);
        doc.setDocType(ContractDocument.TYPE_SIGNED_CONTRACT);
        doc.setFileUrl("https://drive.google.com/file/d/1AbC_de-F/view?usp=sharing");
        when(contractDAO.findDocumentsOf(5)).thenReturn(List.of(doc));
        when(contractDAO.findById(5)).thenReturn(contract);
        when(request.getRequestDispatcher("/jsp/sale/viewcontractdetail.jsp"))
                .thenReturn(mock(RequestDispatcher.class));

        controller.doGet(request, response);

        verify(request).setAttribute("primaryDocumentUrl",
                "https://drive.google.com/file/d/1AbC_de-F/view?usp=sharing");
        verify(request, never()).setAttribute(eq("drivePreviewUrl"), any());
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
        // TRANG XEM KHÔNG ĐƯỢC ĐẶT CỜ THAO TÁC NÀO. Mọi nút gây thay đổi đã
        // chuyển sang trang sửa; JSP bên đó bật nút theo các cờ này. Đặt lại ở
        // đây là trang xem mọc nút trở lại -- đúng thứ người dùng không muốn.
        for (String flag : new String[]{"canVoid", "canSign", "canClose",
                "canEditProducts", "canEditPayments", "canRecordPayment"}) {
            verify(request, never()).setAttribute(eq(flag), any());
        }
        verify(dispatcher).forward(request, response);
        verify(response, never()).sendRedirect(anyString());
    }

    // ------------------------------------------------------------------
    // POST ?action=create / update (BR-44)
    // ------------------------------------------------------------------

    /**
     * Hợp đồng ở trạng thái Nháp.
     *
     * <p>{@code new Contract()} KHÔNG dùng được nữa: progressStatus của nó là
     * null, mà mọi vị ngữ vòng đời đều hỏi "có phải Nháp không" và null trả lời
     * KHÔNG -- tức là bản ghi trắng bị coi như đã ký, và form sửa chỉ nhận hai
     * trường. Mặc định đó là cố ý (thà khoá nhầm còn hơn mở nhầm một hợp đồng
     * đã ký), nhưng nó khiến test phải nói rõ mình đang đứng ở đâu.
     */
    private static Contract draftContract() {
        Contract c = new Contract();
        c.setProgressStatus("Nháp");
        return c;
    }

    // ------------------------------------------------------------------
    // Siết form sửa + phụ lục (đợt 3)
    // ------------------------------------------------------------------

    /** Hợp đồng đã ký: mọi ô điều khoản trên form bị bỏ qua, chỉ hai trường đi tiếp. */
    @Test
    public void update_signedContract_keepsTermsFromTheStoredRecord() throws Exception {
        Contract signed = signedContract();
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractDAO.findById(5)).thenReturn(signed);
        // Form gửi lên một tiêu đề khác -- có thể là POST nặn tay, vì ô đó
        // hiển thị dạng khoá.
        stubValidContractFields();
        when(request.getParameter("title")).thenReturn("Tiêu đề bị đổi lén");
        when(request.getParameter("ownerId")).thenReturn("88");
        when(contractDAO.update(any(Contract.class), anyInt())).thenReturn(true);

        controller.doPost(request, response);

        verify(contractDAO).update(argThat((Contract c) ->
                "Hợp đồng gốc".equals(c.getTitle()) && c.getOwnerId() == 88), anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5");
    }

    /**
     * Chữa sai sót là việc của ADMIN.
     *
     * <p>Không dùng requireFullAccess như các action khác: quyền quản lý hợp
     * đồng cho phép tạo, sửa nháp, ký -- chạm vào điều khoản của hợp đồng ĐÃ KÝ
     * thì hẹp hơn hẳn.
     */
    @Test
    public void correct_nonAdmin_isRefusedAndWritesNothing() throws Exception {
        when(request.getParameter("action")).thenReturn("correct");
        when(request.getParameter("contractId")).thenReturn("5");

        controller.doPost(request, response);

        verify(contractDAO, never()).correct(any(Contract.class), anyInt(), anyString());
    }

    /** Thiếu lý do thì không gọi xuống DAO -- báo lỗi ngay, không để mất dữ liệu người dùng vừa gõ. */
    @Test
    public void correct_withoutReason_redirectsWithoutCallingDao() throws Exception {
        loginAs("Admin");
        when(request.getParameter("action")).thenReturn("correct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(contractDAO.findById(5)).thenReturn(signedContract());
        when(request.getParameter("correctionReason")).thenReturn("   ");

        controller.doPost(request, response);

        verify(contractDAO, never()).correct(any(Contract.class), anyInt(), anyString());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=missing_reason");
    }

    /**
     * Form lập phụ lục từ chối hợp đồng cha chưa ký, và đưa người dùng về đúng
     * hợp đồng đó thay vì mở một form mà bấm Lưu chắc chắn thất bại.
     */
    @Test
    public void newAmendment_draftParent_redirectsWithReason() throws Exception {
        when(request.getParameter("action")).thenReturn("newAmendment");
        when(request.getParameter("parentId")).thenReturn("3");
        when(contractDAO.findById(3)).thenReturn(draftContract());

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=3&error=amendment_not_allowed");
    }

    /** Phụ lục mang parentContractId, và khách hàng lấy từ hợp đồng cha chứ không từ form. */
    @Test
    public void createAmendment_carriesParentAndInheritsCounterparty() throws Exception {
        Contract parent = signedContract();
        parent.setContractId(3);
        parent.setEnterpriseId(10);
        when(request.getParameter("action")).thenReturn("createAmendment");
        when(request.getParameter("parentId")).thenReturn("3");
        when(contractDAO.findById(3)).thenReturn(parent);
        stubValidContractFields();
        // Form phụ lục không có ô khách hàng; giá trị lạ dưới đây phải bị bỏ qua.
        when(request.getParameter("enterpriseId")).thenReturn("777");
        when(contractDAO.insert(any(Contract.class), anyInt())).thenReturn(90);

        controller.doPost(request, response);

        verify(contractDAO).insert(argThat((Contract c) ->
                c.getParentContractId() != null && c.getParentContractId() == 3
                        && c.getEnterpriseId() == 10), anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=90");
    }

    /** Hợp đồng cha vừa đổi trạng thái giữa chừng: DAO từ chối, người dùng về trang hợp đồng cha. */
    @Test
    public void createAmendment_daoRejectsParent_redirectsToParent() throws Exception {
        Contract parent = signedContract();
        parent.setContractId(3);
        parent.setEnterpriseId(10);
        when(request.getParameter("action")).thenReturn("createAmendment");
        when(request.getParameter("parentId")).thenReturn("3");
        when(contractDAO.findById(3)).thenReturn(parent);
        stubValidContractFields();
        when(contractDAO.insert(any(Contract.class), anyInt())).thenReturn(ContractDAO.INVALID_PARENT);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=3&error=amendment_not_allowed");
    }

    // ------------------------------------------------------------------
    // Giá trị của phụ lục: ô số dương + ô chọn dấu
    // ------------------------------------------------------------------

    /**
     * "Giảm trừ" biến con số dương trên form thành một CHÊNH LỆCH ÂM.
     *
     * <p>Dấu nhập bằng ô chọn chứ không bắt gõ dấu trừ, nên chỗ ghép dấu là ở
     * controller -- và nếu nó ghép sai thì một phụ lục giảm trừ sẽ lặng lẽ CỘNG
     * thêm tiền vào hợp đồng.
     */
    @Test
    public void createAmendment_decreaseAdjustment_storesNegativeValue() throws Exception {
        Contract parent = signedContract();
        parent.setContractId(3);
        parent.setEnterpriseId(10);
        parent.setContractValue(new java.math.BigDecimal("1000000000"));
        when(request.getParameter("action")).thenReturn("createAmendment");
        when(request.getParameter("parentId")).thenReturn("3");
        when(contractDAO.findById(3)).thenReturn(parent);
        stubValidContractFields();
        when(request.getParameter("contractValue")).thenReturn("80.000.000");
        when(request.getParameter("valueAdjustment")).thenReturn("decrease");
        when(contractDAO.insert(any(Contract.class), anyInt())).thenReturn(90);

        controller.doPost(request, response);

        verify(contractDAO).insert(argThat((Contract c) ->
                new java.math.BigDecimal("-80000000").compareTo(c.getContractValue()) == 0), anyInt());
    }

    /** "Không đổi giá trị" thắng cả con số còn sót trong ô: phụ lục đó không đụng tới tiền. */
    @Test
    public void createAmendment_noAdjustment_ignoresTypedAmount() throws Exception {
        Contract parent = signedContract();
        parent.setContractId(3);
        parent.setEnterpriseId(10);
        when(request.getParameter("action")).thenReturn("createAmendment");
        when(request.getParameter("parentId")).thenReturn("3");
        when(contractDAO.findById(3)).thenReturn(parent);
        stubValidContractFields();
        when(request.getParameter("contractValue")).thenReturn("250.000.000");
        when(request.getParameter("valueAdjustment")).thenReturn("none");
        when(contractDAO.insert(any(Contract.class), anyInt())).thenReturn(90);

        controller.doPost(request, response);

        verify(contractDAO).insert(argThat((Contract c) -> c.getContractValue() == null), anyInt());
    }

    /**
     * Giảm trừ nhiều hơn giá trị còn lại thì dừng ngay ở controller, không gọi
     * xuống DAO -- người dùng nhận đúng lý do thay vì một "lưu thất bại".
     */
    @Test
    public void createAmendment_decreaseBeyondRemainingValue_rejected() throws Exception {
        Contract parent = signedContract();
        parent.setContractId(3);
        parent.setEnterpriseId(10);
        parent.setContractValue(new java.math.BigDecimal("100000000"));
        when(request.getParameter("action")).thenReturn("createAmendment");
        when(request.getParameter("parentId")).thenReturn("3");
        when(contractDAO.findById(3)).thenReturn(parent);
        stubValidContractFields();
        when(request.getParameter("contractValue")).thenReturn("150.000.000");
        when(request.getParameter("valueAdjustment")).thenReturn("decrease");

        controller.doPost(request, response);

        verify(contractDAO, never()).insert(any(Contract.class), anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=newAmendment&parentId=3&error=value_negative");
    }

    /**
     * Phụ lục ĐÃ KÝ của hợp đồng cha nới thêm chỗ cho khoản giảm trừ: giá trị
     * hiện hành mới là thứ đem ra so, không phải con số trên bản gốc.
     */
    @Test
    public void createAmendment_decreaseWithinCurrentValue_isAccepted() throws Exception {
        Contract parent = signedContract();
        parent.setContractId(3);
        parent.setEnterpriseId(10);
        parent.setContractValue(new java.math.BigDecimal("100000000"));
        parent.setAmendmentValueSigned(new java.math.BigDecimal("100000000"));
        when(request.getParameter("action")).thenReturn("createAmendment");
        when(request.getParameter("parentId")).thenReturn("3");
        when(contractDAO.findById(3)).thenReturn(parent);
        stubValidContractFields();
        when(request.getParameter("contractValue")).thenReturn("150.000.000");
        when(request.getParameter("valueAdjustment")).thenReturn("decrease");
        when(contractDAO.insert(any(Contract.class), anyInt())).thenReturn(90);

        controller.doPost(request, response);

        verify(contractDAO).insert(any(Contract.class), anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=90");
    }

    /** DAO từ chối vì một phụ lục giảm trừ khác vừa được ký -> báo đúng lý do đó. */
    @Test
    public void createAmendment_daoRejectsValue_redirectsWithValueError() throws Exception {
        Contract parent = signedContract();
        parent.setContractId(3);
        parent.setEnterpriseId(10);
        parent.setContractValue(new java.math.BigDecimal("1000000000"));
        when(request.getParameter("action")).thenReturn("createAmendment");
        when(request.getParameter("parentId")).thenReturn("3");
        when(contractDAO.findById(3)).thenReturn(parent);
        stubValidContractFields();
        when(request.getParameter("contractValue")).thenReturn("80.000.000");
        when(request.getParameter("valueAdjustment")).thenReturn("decrease");
        when(contractDAO.insert(any(Contract.class), anyInt())).thenReturn(ContractDAO.INVALID_VALUE);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=newAmendment&parentId=3&error=value_negative");
    }

    /** Form hợp đồng GỐC không gửi ô chọn dấu, nên con số đi thẳng vào như cũ. */
    @Test
    public void create_withoutAdjustmentParam_keepsValueAsTyped() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidContractFields();
        when(request.getParameter("contractValue")).thenReturn("1.500.000.000");
        when(contractDAO.insert(any(Contract.class), anyInt())).thenReturn(90);

        controller.doPost(request, response);

        verify(contractDAO).insert(argThat((Contract c) ->
                new java.math.BigDecimal("1500000000").compareTo(c.getContractValue()) == 0), anyInt());
    }

    /**
     * Đối chiếu tiền ở trang hợp đồng làm theo CỤM: giá trị hiện hành (đã cộng
     * phụ lục) so với tổng kỳ của cả cụm. So riêng bản ghi gốc thì cảnh báo
     * "không khớp" nổ ở mọi hợp đồng có phụ lục.
     */
    @Test
    public void view_paymentMismatch_comparesClusterValueWithClusterSchedule() throws Exception {
        Contract contract = signedContract();
        contract.setContractValue(new java.math.BigDecimal("1500000000"));
        contract.setAmendmentValueSigned(new java.math.BigDecimal("250000000"));
        contract.setAmendmentCount(1);
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");
        when(contractDAO.findById(5)).thenReturn(contract);
        when(contractDAO.sumScheduledPaymentsForCluster(5))
                .thenReturn(new java.math.BigDecimal("1750000000"));
        when(request.getRequestDispatcher(anyString())).thenReturn(mock(RequestDispatcher.class));

        controller.doGet(request, response);

        verify(request).setAttribute("paymentMismatch", false);
        verify(request).setAttribute(eq("clusterValue"),
                argThat((java.math.BigDecimal v) -> new java.math.BigDecimal("1750000000").compareTo(v) == 0));
    }

    // ------------------------------------------------------------------
    // Nối hợp đồng bán <-> mua
    // ------------------------------------------------------------------

    /**
     * Form chỉ gửi id hợp đồng kia; controller tự xếp bên nào là bán, bên nào
     * là mua -- bảng lưu một chiều cố định, và người dùng không phải nhớ thứ tự.
     */
    @Test
    public void linkContract_dungTuPhiaBan_xepDungThuTu() throws Exception {
        Contract sell = signedContract();
        sell.setContractId(5);
        sell.setDirection("Bán");
        Contract buy = signedContract();
        buy.setContractId(9);
        buy.setDirection("Mua");
        when(request.getParameter("action")).thenReturn("linkContract");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("otherContractId")).thenReturn("9");
        when(contractDAO.findById(5)).thenReturn(sell);
        when(contractDAO.findById(9)).thenReturn(buy);
        when(contractDAO.linkContracts(anyInt(), anyInt(), any(), anyInt())).thenReturn(ContractDAO.LINK_OK);

        controller.doPost(request, response);

        verify(contractDAO).linkContracts(eq(5), eq(9), any(), anyInt());
    }

    /** Đứng ở phía MUA mà nối thì thứ tự phải đảo lại, không phải nối ngược. */
    @Test
    public void linkContract_dungTuPhiaMua_vanXepBanTruoc() throws Exception {
        Contract buy = signedContract();
        buy.setContractId(9);
        buy.setDirection("Mua");
        Contract sell = signedContract();
        sell.setContractId(5);
        sell.setDirection("Bán");
        when(request.getParameter("action")).thenReturn("linkContract");
        when(request.getParameter("contractId")).thenReturn("9");
        when(request.getParameter("otherContractId")).thenReturn("5");
        when(contractDAO.findById(9)).thenReturn(buy);
        when(contractDAO.findById(5)).thenReturn(sell);
        when(contractDAO.linkContracts(anyInt(), anyInt(), any(), anyInt())).thenReturn(ContractDAO.LINK_OK);

        controller.doPost(request, response);

        verify(contractDAO).linkContracts(eq(5), eq(9), any(), anyInt());
    }

    /** DAO từ chối cặp trùng -> báo đúng lý do, không lẫn với "lưu thất bại". */
    @Test
    public void linkContract_capDaNoi_baoDungLyDo() throws Exception {
        Contract sell = signedContract();
        sell.setContractId(5);
        sell.setDirection("Bán");
        Contract buy = signedContract();
        buy.setContractId(9);
        buy.setDirection("Mua");
        when(request.getParameter("action")).thenReturn("linkContract");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("otherContractId")).thenReturn("9");
        when(contractDAO.findById(5)).thenReturn(sell);
        when(contractDAO.findById(9)).thenReturn(buy);
        when(contractDAO.linkContracts(anyInt(), anyInt(), any(), anyInt()))
                .thenReturn(ContractDAO.LINK_DUPLICATE);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=link_duplicate");
    }

    /**
     * Đối chiếu tiền chỉ dựng ở phía BÁN: một đơn mua phục vụ nhiều hợp đồng
     * bán, nên lấy giá trị bán trừ đi ở phía mua sẽ ra con số vô nghĩa.
     */
    @Test
    public void view_phiaBan_dungDoiChieuDauVao() throws Exception {
        Contract sell = signedContract();
        sell.setDirection("Bán");
        sell.setContractValue(new java.math.BigDecimal("1000000000"));
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");
        when(contractDAO.findById(5)).thenReturn(sell);
        when(contractDAO.sumLinkedBuyValue(5)).thenReturn(new java.math.BigDecimal("600000000"));
        when(request.getRequestDispatcher(anyString())).thenReturn(mock(RequestDispatcher.class));

        controller.doGet(request, response);

        verify(request).setAttribute("linkIsSellSide", true);
        verify(request).setAttribute(eq("linkedMargin"),
                argThat((java.math.BigDecimal v) -> new java.math.BigDecimal("400000000").compareTo(v) == 0));
    }

    @Test
    public void view_phiaMua_khongDungDoiChieu() throws Exception {
        Contract buy = signedContract();
        buy.setDirection("Mua");
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");
        when(contractDAO.findById(5)).thenReturn(buy);
        when(request.getRequestDispatcher(anyString())).thenReturn(mock(RequestDispatcher.class));

        controller.doGet(request, response);

        verify(request).setAttribute("linkIsSellSide", false);
        verify(request, never()).setAttribute(eq("linkedMargin"), any());
        verify(contractDAO, never()).sumLinkedBuyValue(anyInt());
    }

    // ------------------------------------------------------------------
    // Chặng bàn giao trên danh sách hợp đồng
    // ------------------------------------------------------------------

    /** Lọc "đang chờ ở phòng X" phải xuống CẢ findAll lẫn countAll, nếu không phân trang lệch. */
    @Test
    public void list_locTheoPhongDangCho_xuongCaHaiTruyVan() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcontract.jsp")).thenReturn(dispatcher);
        when(request.getParameter("waitingDept")).thenReturn("5");

        controller.doGet(request, response);

        verify(contractDAO).findAll(anyInt(), anyInt(), any(), any(), any(), any(), anyBoolean(),
                nullable(Period.class), any(), nullable(String.class), anyBoolean(), eq(5), any());
        verify(contractDAO).countAll(any(), any(), any(), any(), nullable(Period.class), any(),
                nullable(String.class), anyBoolean(), eq(5), any());
        verify(request).setAttribute("waitingDeptFilter", 5);
    }

    /** Bộ lọc này nằm trong khối "Lọc thêm" nên phải được đếm vào badge của nút đó. */
    @Test
    public void list_locPhongDangCho_tinhVaoSoLocNangCao() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcontract.jsp")).thenReturn(dispatcher);
        when(request.getParameter("waitingDept")).thenReturn("5");
        when(request.getParameter("type")).thenReturn("Thi công lắp đặt");

        controller.doGet(request, response);

        verify(request).setAttribute("advancedFilterCount", 2);
    }

    /** Chip "đang lọc" của nó mang link bỏ chính nó và giữ các lọc khác. */
    @Test
    public void list_chipPhongDangCho_mangLinkBoChinhNo() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcontract.jsp")).thenReturn(dispatcher);
        when(request.getParameter("waitingDept")).thenReturn("5");
        when(request.getParameter("scope")).thenReturn("root");

        controller.doGet(request, response);

        ArgumentCaptor<List<ContractController.FilterChip>> chips = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("activeFilters"), chips.capture());
        ContractController.FilterChip chip = chips.getValue().stream()
                .filter(c -> c.getLabel().startsWith("Đang chờ")).findFirst().orElseThrow();
        assertFalse(chip.getQuery(), chip.getQuery().contains("waitingDept="));
        assertTrue(chip.getQuery(), chip.getQuery().contains("scope=root"));
    }

    /** Xuất Excel phải theo đúng bộ lọc đang xem, kể cả bộ lọc mới này. */
    @Test
    public void exportExcel_mangTheoLocPhongDangCho() throws Exception {
        when(request.getParameter("action")).thenReturn("exportExcel");
        when(request.getParameter("waitingDept")).thenReturn("6");
        when(response.getOutputStream()).thenReturn(mock(jakarta.servlet.ServletOutputStream.class));

        controller.doGet(request, response);

        verify(contractDAO).findAll(anyInt(), anyInt(), any(), any(), any(), any(), anyBoolean(),
                nullable(Period.class), any(), nullable(String.class), anyBoolean(), eq(6), any());
    }

    /**
     * Trang QUẢN LÝ thì ngược với trang xem: nút đóng chặng phải mọc được ở đó,
     * nếu không cái form nằm trong JSP mà không bao giờ hiện ra.
     */
    @Test
    public void edit_datCoXacNhanChangChoDungPhong() throws Exception {
        Contract contract = signedContract();
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("5");
        when(contractDAO.findById(5)).thenReturn(contract);
        ContractHandover pending = new ContractHandover();
        pending.setHandoverId(9);
        pending.setDepartmentId(6);
        when(contractDAO.findHandoversOf(5)).thenReturn(List.of(pending));
        // Admin: vừa vào được trang quản lý (requireFullAccess), vừa xác nhận
        // thay được cho mọi phòng. Người của phòng Kế toán/Dự án KHÔNG vào được
        // trang này -- chỗ làm việc của họ là hàng đợi bàn giao.
        dangNhapVoiPhongBan(1, "Admin");
        when(request.getRequestDispatcher(anyString())).thenReturn(mock(RequestDispatcher.class));

        controller.doGet(request, response);

        verify(request).setAttribute("canCompleteHandover_9", true);
    }

    // ------------------------------------------------------------------
    // Bàn giao phòng ban
    // ------------------------------------------------------------------

    /** Form tích nhiều phòng thì giao cùng lúc cho tất cả, một lời gọi. */
    @Test
    public void handOver_giaoCungLucChoNhieuPhong() throws Exception {
        when(request.getParameter("action")).thenReturn("handOver");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameterValues("departmentId")).thenReturn(new String[]{"5", "6"});
        when(contractDAO.findById(5)).thenReturn(signedContract());
        when(contractDAO.handOverToDepartments(anyInt(), anyList(), any(), anyInt())).thenReturn(2);

        controller.doPost(request, response);

        verify(contractDAO).handOverToDepartments(eq(5), eq(List.of(5, 6)), any(), anyInt());
    }

    /** Không tích phòng nào thì báo lỗi chứ không gọi xuống DAO. */
    @Test
    public void handOver_khongChonPhong_baoLoi() throws Exception {
        when(request.getParameter("action")).thenReturn("handOver");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameterValues("departmentId")).thenReturn(null);
        when(contractDAO.findById(5)).thenReturn(signedContract());

        controller.doPost(request, response);

        verify(contractDAO, never()).handOverToDepartments(anyInt(), anyList(), any(), anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=handover_no_department");
    }

    /**
     * Xác nhận xong chặng kiểm theo PHÒNG BAN, không theo vai trò: người phòng
     * khác bấm vào thì bị từ chối, kể cả khi họ có quyền ghi hợp đồng.
     */
    @Test
    public void completeHandover_khongThuocPhongDo_biTuChoi() throws Exception {
        when(request.getParameter("action")).thenReturn("completeHandover");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("handoverId")).thenReturn("9");
        when(request.getParameter("departmentId")).thenReturn("6");
        dangNhapVoiPhongBan(2, "Sales");

        controller.doPost(request, response);

        verify(contractDAO, never()).completeHandover(anyInt(), anyInt(), any());
        verify(response).sendRedirect(CONTEXT_PATH + "/error/403.jsp");
    }

    /** Người đúng phòng thì đóng được chặng của phòng mình. */
    @Test
    public void completeHandover_dungPhong_thiDongDuocChang() throws Exception {
        when(request.getParameter("action")).thenReturn("completeHandover");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("handoverId")).thenReturn("9");
        when(request.getParameter("departmentId")).thenReturn("6");
        when(request.getParameter("doneNote")).thenReturn("đã kiểm điều khoản");
        dangNhapVoiPhongBan(6, "CSKH");
        when(contractDAO.completeHandover(anyInt(), anyInt(), any())).thenReturn(true);

        controller.doPost(request, response);

        verify(contractDAO).completeHandover(eq(9), anyInt(), eq("đã kiểm điều khoản"));
    }

    /**
     * Trang xem PHẢI mọc nút xác nhận cho phòng của chính người đang xem.
     *
     * <p>Đây là ngoại lệ duy nhất của luật "trang xem không có nút ghi", và nó
     * là lý do tồn tại của cả đường này: người đóng chặng là phòng Kế toán /
     * Dự án, mà họ không có quyền ghi hợp đồng nên showEditForm chặn thẳng --
     * trang xem là trang duy nhất họ mở được.
     */
    @Test
    public void view_datCoXacNhanChangChoPhongCuaMinh() throws Exception {
        Contract contract = signedContract();
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");
        when(contractDAO.findById(5)).thenReturn(contract);
        ContractHandover pending = new ContractHandover();
        pending.setHandoverId(9);
        pending.setDepartmentId(6);
        when(contractDAO.findHandoversOf(5)).thenReturn(List.of(pending));
        // Phòng 6, KHÔNG phải Admin và không có quyền ghi hợp đồng -- đúng hình
        // dạng người của phòng nhận bàn giao.
        dangNhapVoiPhongBan(6, "CSKH");
        when(request.getRequestDispatcher(anyString())).thenReturn(mock(RequestDispatcher.class));

        controller.doGet(request, response);

        verify(request).setAttribute("hasPendingHandover", true);
        verify(request).setAttribute("canCompleteHandover_9", true);
    }

    /**
     * Chiều ngược lại: người phòng KHÁC mở cùng trang đó thì cờ phải là false.
     *
     * <p>Không có ca này thì một lần "mở cho tiện" ở putContractWorkspace sẽ
     * biến trang xem thành chỗ ai cũng đóng được chặng của người khác, mà
     * không test nào kêu.
     */
    @Test
    public void view_khongDatCoXacNhanChangChoPhongKhac() throws Exception {
        Contract contract = signedContract();
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");
        when(contractDAO.findById(5)).thenReturn(contract);
        ContractHandover pending = new ContractHandover();
        pending.setHandoverId(9);
        pending.setDepartmentId(6);
        when(contractDAO.findHandoversOf(5)).thenReturn(List.of(pending));
        dangNhapVoiPhongBan(2, "Sales");
        when(request.getRequestDispatcher(anyString())).thenReturn(mock(RequestDispatcher.class));

        controller.doGet(request, response);

        verify(request).setAttribute("canCompleteHandover_9", false);
    }

    /**
     * Chặng ĐÃ XONG thì không đặt cờ: nút mọc lại trên một chặng đã đóng là
     * mời người ta bấm một cú chắc chắn thất bại.
     */
    @Test
    public void view_khongDatCoChoChangDaXong() throws Exception {
        Contract contract = signedContract();
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");
        when(contractDAO.findById(5)).thenReturn(contract);
        ContractHandover done = new ContractHandover();
        done.setHandoverId(9);
        done.setDepartmentId(6);
        done.setDoneAt(new java.sql.Timestamp(System.currentTimeMillis()));
        when(contractDAO.findHandoversOf(5)).thenReturn(List.of(done));
        dangNhapVoiPhongBan(6, "CSKH");
        when(request.getRequestDispatcher(anyString())).thenReturn(mock(RequestDispatcher.class));

        controller.doGet(request, response);

        verify(request).setAttribute("hasPendingHandover", false);
        verify(request, never()).setAttribute(eq("canCompleteHandover_9"), any());
    }

    /**
     * Bấm từ trang xem thì quay LẠI trang xem. Đẩy người của phòng nhận về
     * action=edit là đẩy họ vào trang requireFullAccess chặn -- xác nhận xong
     * thì ăn ngay 403, đúng thứ đường này sinh ra để tránh.
     */
    @Test
    public void completeHandover_tuTrangXem_quayLaiTrangXem() throws Exception {
        when(request.getParameter("action")).thenReturn("completeHandover");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("handoverId")).thenReturn("9");
        when(request.getParameter("departmentId")).thenReturn("6");
        when(request.getParameter("doneNote")).thenReturn("đã đối chiếu công nợ");
        when(request.getParameter("returnTo")).thenReturn("from-view");
        dangNhapVoiPhongBan(6, "CSKH");
        when(contractDAO.completeHandover(anyInt(), anyInt(), any())).thenReturn(true);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5");
    }

    /** Hàng đợi đếm theo phòng và chỉ ra chặng để lâu nhất -- hai con số giám đốc nhìn đầu tiên. */
    @Test
    public void handoverQueue_demTheoPhongVaChangLauNhat() throws Exception {
        when(request.getParameter("action")).thenReturn("handovers");
        ContractHandover a = new ContractHandover();
        a.setHandoverId(1);
        a.setDepartmentId(5);
        a.setDepartmentName("Kế toán");
        a.setHandedAt(new java.sql.Timestamp(System.currentTimeMillis() - 5L * 24 * 60 * 60 * 1000));
        ContractHandover b = new ContractHandover();
        b.setHandoverId(2);
        b.setDepartmentId(6);
        b.setDepartmentName("Dự án");
        b.setHandedAt(new java.sql.Timestamp(System.currentTimeMillis() - 20L * 24 * 60 * 60 * 1000));
        when(contractDAO.findPendingHandovers(any())).thenReturn(List.of(a, b));
        when(request.getRequestDispatcher("/jsp/sale/handoverqueue.jsp"))
                .thenReturn(mock(RequestDispatcher.class));

        controller.doGet(request, response);

        verify(request).setAttribute(eq("handoverCountByDepartment"),
                argThat((java.util.Map<String, Integer> m) ->
                        m.get("Kế toán") == 1 && m.get("Dự án") == 1));
        verify(request).setAttribute("slowestHandoverDays", 20L);
    }

    /** Đăng nhập với một phòng ban cụ thể -- quyền đóng chặng kiểm theo cột này. */
    private void dangNhapVoiPhongBan(int departmentId, String roleName) {
        User user = new User();
        user.setUserId(77);
        user.setDepartmentId(departmentId);
        Role role = new Role();
        role.setRoleName(roleName);
        user.setRole(role);
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("currentUser")).thenReturn(user);
        when(request.getSession(false)).thenReturn(session);
    }

    /** Hợp đồng đã ký với đối tác giữ đúng vai -- dùng cho nhóm test siết form. */
    private static Contract signedContract() {
        Contract c = new Contract();
        c.setContractId(5);
        c.setProgressStatus("Đã ký");
        c.setContractCode("01/2026/HĐKT-POSTEF");
        c.setTitle("Hợp đồng gốc");
        c.setContractType("Cung cấp thiết bị");
        c.setDirection("Bán");
        c.setEnterpriseId(10);
        c.setOwnerId(99);
        return c;
    }

    private void stubValidContractFields() {
        // Mã hợp đồng là ô BẮT BUỘC từ V28 -- hệ thống thôi sinh mã hộ.
        when(request.getParameter("contractCode")).thenReturn("01/2026/HĐKT-POSTEF");
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
        // Bản NHÁP: đó là trạng thái duy nhất mà form sửa còn gửi lên đủ mọi ô.
        // Hợp đồng đã ký đi đường khác hẳn -- xem
        // update_contractDaKy_chiNhanNguoiPhuTrachVaLink.
        when(contractDAO.findById(5)).thenReturn(draftContract());
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
                period.capture(), eq("Mua"), nullable(String.class), eq(false), nullable(Integer.class), any());
        assertNotNull("kỳ phải còn nguyên khi lọc theo chiều", period.getValue());
        assertEquals("Quý 2/2026", period.getValue().getLabel());
    }

    /**
     * Dải KPI phải đếm CÙNG phạm vi với bảng bên dưới. Trước bản này
     * {@code direction} được truyền xuống DAO nhưng câu lệnh bỏ quên nó, nên
     * đứng ở mục Hợp đồng mua vẫn thấy bốn con số của cả hợp đồng bán -- hai
     * con số cạnh nhau không khớp mà không gì trên màn hình giải thích nổi.
     */
    @Test
    public void list_kpiDemTheoDungChieuVaPhamViPhuLuc() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcontract.jsp")).thenReturn(dispatcher);
        when(request.getParameter("kind")).thenReturn("buy");
        when(request.getParameter("scope")).thenReturn("root");

        controller.doGet(request, response);

        verify(contractDAO).countStatusSummary(nullable(Integer.class), nullable(Period.class), eq("Mua"),
                eq(true), nullable(java.util.List.class), any(), nullable(Integer.class));
    }

    /** Bốn ô trạng thái là đường lọc; link của ô ĐANG BẬT phải là link TẮT nó đi. */
    @Test
    public void list_oTrangThaiDangBat_mangLinkTatLoc() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcontract.jsp")).thenReturn(dispatcher);
        when(request.getParameter("status")).thenReturn("Đang hiệu lực");

        controller.doGet(request, response);

        ArgumentCaptor<List<ContractController.FilterChip>> chips = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("statusChips"), chips.capture());
        ContractController.FilterChip active = chips.getValue().stream()
                .filter(ContractController.FilterChip::isOn).findFirst().orElse(null);
        assertNotNull("phải có đúng ô 'Đang hiệu lực' đang bật", active);
        assertFalse("link của ô đang bật phải BỎ status đi", active.getQuery().contains("status="));
        ContractController.FilterChip other = chips.getValue().stream()
                .filter(c -> !c.isOn()).findFirst().orElse(null);
        assertTrue("ô chưa bật thì link BẬT nó", other.getQuery().contains("status="));
    }

    /** Link của ô trạng thái phải mang theo mọi lọc khác, nếu không bấm vào là mất sạch. */
    @Test
    public void list_linkOTrangThai_giuNguyenCacLocKhac() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcontract.jsp")).thenReturn(dispatcher);
        when(request.getParameter("progress")).thenReturn("Đã ký");
        when(request.getParameter("scope")).thenReturn("root");

        controller.doGet(request, response);

        ArgumentCaptor<List<ContractController.FilterChip>> chips = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("statusChips"), chips.capture());
        String query = chips.getValue().get(0).getQuery();
        assertTrue(query, query.contains("progress="));
        assertTrue(query, query.contains("scope=root"));
    }

    /**
     * Chip "đang lọc" mang link BỎ CHÍNH NÓ và giữ các lọc còn lại -- trước đây
     * muốn bỏ một điều kiện phải nhớ nó nằm ở ô nào rồi trả ô đó về "Tất cả".
     */
    @Test
    public void list_chipDangLoc_moiCaiMangLinkBoChinhNo() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcontract.jsp")).thenReturn(dispatcher);
        when(request.getParameter("progress")).thenReturn("Đã ký");
        when(request.getParameter("scope")).thenReturn("root");

        controller.doGet(request, response);

        ArgumentCaptor<List<ContractController.FilterChip>> chips = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("activeFilters"), chips.capture());
        assertEquals(2, chips.getValue().size());
        ContractController.FilterChip progressChip = chips.getValue().stream()
                .filter(c -> c.getLabel().startsWith("Tiến độ")).findFirst().orElseThrow();
        assertFalse("bỏ tiến độ", progressChip.getQuery().contains("progress="));
        assertTrue("nhưng giữ phạm vi", progressChip.getQuery().contains("scope=root"));
    }

    /**
     * Bỏ NĂM thì bỏ luôn kỳ: Period.parse cần cả hai, nên để lại một mình
     * "quý 3" trên URL chỉ tạo ra một bộ lọc không lọc gì cả.
     */
    @Test
    public void list_boNam_thiBoLuonKy() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcontract.jsp")).thenReturn(dispatcher);
        when(request.getParameter("year")).thenReturn("2026");
        when(request.getParameter("period")).thenReturn("q3");

        controller.doGet(request, response);

        ArgumentCaptor<List<ContractController.FilterChip>> chips = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("activeFilters"), chips.capture());
        String query = chips.getValue().get(0).getQuery();
        assertFalse(query, query.contains("year="));
        assertFalse(query, query.contains("period="));
    }

    /** Số trên nút "Lọc thêm" đếm đúng ba ô nằm trong đó, không đếm cả thanh lọc. */
    @Test
    public void list_demSoLocNangCaoDangBat() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcontract.jsp")).thenReturn(dispatcher);
        when(request.getParameter("type")).thenReturn("Thi công lắp đặt");
        when(request.getParameter("year")).thenReturn("2026");
        // Hai cái dưới đây nằm NGOÀI khối "Lọc thêm" nên không được tính.
        when(request.getParameter("progress")).thenReturn("Đã ký");
        when(request.getParameter("keyword")).thenReturn("POSTEF");

        controller.doGet(request, response);

        verify(request).setAttribute("advancedFilterCount", 2);
    }

    @Test
    public void list_thieuKind_macDinhLaHopDongBan() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/sale/listcontract.jsp")).thenReturn(dispatcher);

        controller.doGet(request, response);

        verify(contractDAO).findAll(anyInt(), anyInt(), any(), any(), any(), any(), eq(false),
                nullable(Period.class), eq("Bán"), nullable(String.class), eq(false), nullable(Integer.class), any());
        verify(request).setAttribute("kind", "sell");
    }

    // ------------------------------------------------------------------
    // Giá trị hợp đồng -- ô tiền người dùng gõ tay
    // ------------------------------------------------------------------

    /** Tạo hợp đồng với một chuỗi tiền cho trước, trả về giá trị đã parse. */
    private java.math.BigDecimal contractValueFrom(String raw) throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("kind")).thenReturn("sell");
        stubValidContractFields();
        when(request.getParameter("contractValue")).thenReturn(raw);
        // findRolesOf(10) đã được stubValidContractFields() gán 'Khách mua'.
        when(contractDAO.insert(any(Contract.class), anyInt())).thenReturn(5);

        controller.doPost(request, response);

        ArgumentCaptor<Contract> saved = ArgumentCaptor.forClass(Contract.class);
        verify(contractDAO).insert(saved.capture(), anyInt());
        return saved.getValue().getContractValue();
    }

    @Test
    public void contractValue_acceptsVietnameseThousandSeparators() throws Exception {
        // Người Việt gõ dấu chấm phân nhóm theo thói quen. Bắt gõ số trần chỉ
        // tạo ra lỗi nhập liệu chứ không tạo ra dữ liệu sạch hơn.
        assertEquals(new java.math.BigDecimal("1500000000"), contractValueFrom("1.500.000.000"));
    }

    @Test
    public void contractValue_blankMeansNotAgreedYet() throws Exception {
        // Bản nháp chưa chốt giá -- để trống là hợp lệ, không phải lỗi.
        assertNull(contractValueFrom("   "));
    }

    @Test
    public void contractValue_negativeIsRejectedRatherThanStored() throws Exception {
        // Giá trị âm không có nghĩa, và nếu lọt vào thì nó âm thầm trừ đi
        // trong mọi phép cộng sau này.
        assertNull(contractValueFrom("-500000"));
    }

    @Test
    public void contractValue_garbageDoesNotBreakTheWholeSave() throws Exception {
        // Một ô tiền gõ sai không đáng làm hỏng cả lần lưu hợp đồng.
        assertNull(contractValueFrom("một tỷ rưỡi"));
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
    public void sign_contractWithoutATerm_isRefusedWithItsOwnReason() throws Exception {
        when(request.getParameter("action")).thenReturn("changeProgress");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("toStatus")).thenReturn(ContractDAO.PROGRESS_SIGNED);
        Contract noTerm = new Contract();
        noTerm.setContractId(5);
        noTerm.setProgressStatus(ContractDAO.PROGRESS_DRAFT);
        when(contractDAO.findById(5)).thenReturn(noTerm);

        controller.doPost(request, response);

        // Báo đúng lý do chứ không phải "không chuyển được trạng thái"
        // chung chung -- người dùng cần biết phải đi điền thời hạn trước.
        verify(contractDAO, never()).changeProgressStatus(anyInt(), anyString(), anyInt(), any());
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=view&id=5&error=missing_term");
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
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=add_product_invalid");
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
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5");
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
        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=add_product_invalid");
    }

    @Test
    public void removeProduct_daoFails_redirectsWithRemoveFailedError() throws Exception {
        when(request.getParameter("action")).thenReturn("removeProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("contractProductId")).thenReturn("42");
        when(contractDAO.deleteProductLine(42, 5, 99)).thenReturn(false);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5&error=remove_product_failed");
    }

    @Test
    public void removeProduct_daoSucceeds_returnsToTheEditPage() throws Exception {
        when(request.getParameter("action")).thenReturn("removeProduct");
        when(request.getParameter("contractId")).thenReturn("5");
        when(request.getParameter("contractProductId")).thenReturn("42");
        when(contractDAO.deleteProductLine(42, 5, 99)).thenReturn(true);

        controller.doPost(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/contract?action=edit&id=5");
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
