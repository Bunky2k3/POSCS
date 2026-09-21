package poscs.integration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import poscs.controller.ContractController;
import poscs.dao.ContractDAO;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Nhập hợp đồng từ file PDF theo biểu mẫu (FE-06), chạy trên MySQL THẬT.
 *
 * <h3>Vì sao phải là test tích hợp</h3>
 *
 * Luồng này từng HỎNG HOÀN TOÀN mà không test nào thấy: {@code handleImportPdf}
 * không đặt {@code direction}, mà {@code ContractDAO.insert} luôn bind cột đó
 * thành tham số. {@code contracts.direction} là {@code NOT NULL DEFAULT 'Bán'}
 * -- và đây là chỗ đáng nhớ: DEFAULT chỉ áp dụng khi cột VẮNG MẶT khỏi câu
 * INSERT, còn truyền NULL tường minh thì MySQL từ chối thẳng
 * ({@code Column 'direction' cannot be null}). Nên mọi lần nhập đều thất bại,
 * và người dùng nhận một thông báo nói về mã hợp đồng, chẳng liên quan gì.
 *
 * <p>Test mock JDBC không bao giờ thấy được loại lỗi đó: mock trả "thành công"
 * cho mọi {@code executeUpdate()}, nên một ràng buộc NOT NULL do chính CSDL áp
 * là vô hình. Đó đúng là lý do {@link IntegrationDb} tồn tại.
 *
 * <p>Ba lỗi cùng nằm trong luồng này và cùng chỉ lộ ra khi chạy thật, nên
 * chúng được canh chung ở đây:
 *
 * <ol>
 *   <li>{@code direction} null -- ràng buộc NOT NULL của CSDL;</li>
 *   <li>khách hàng mới không có dòng nào ở {@code enterprise_roles}, nên tàng
 *       hình ở cả hai màn hình danh sách (bộ lọc vai dùng EXISTS);</li>
 *   <li>bấm Ký ghi đè {@code signing_date} bằng ngày hôm nay, xoá mất ngày ký
 *       thật đọc từ bản giấy.</li>
 * </ol>
 *
 * <h3>Cách dựng đầu vào</h3>
 *
 * Điền thẳng vào {@code web/WEB-INF/templates/hopdong_import_template.pdf} --
 * ĐÚNG file người dùng tải về và điền. Không tự dựng một AcroForm khác cho
 * gọn: làm vậy là test đi kiểm một biểu mẫu do chính nó bịa ra, và ngày ai đó
 * đổi tên field trong file mẫu thật thì test vẫn xanh trong khi tính năng đã
 * hỏng.
 *
 * <p>JUnit 4 -- xem CustomerControllerTest.
 */
public class ContractImportIntegrationTest {

    /** File mẫu thật, đường dẫn tính từ thư mục gốc dự án (xem IntegrationDb). */
    private static final String TEMPLATE = "web/WEB-INF/templates/hopdong_import_template.pdf";

    /** Tỉnh + xã có thật và KHỚP NHAU: xã 1 thuộc tỉnh 3, không phải tỉnh 1. */
    private static final String PROVINCE_NAME = "Thành phố Hà Nội";
    private static final String WARD_NAME = "Phường Ba Đình";

    private static final String CONTRACT_CODE = "99/2026/HĐKT-POSTEF";
    private static final String NEW_BUYER_TAX = "0109990001";
    private static final String NEW_BUYER_EMAIL = "khachmoi@example.com";
    private static final String NEW_BUYER_PHONE = "0243822999";
    private static final String SIGN_DATE = "05/02/2026";

    private final ContractDAO contractDAO = new ContractDAO();

    private ContractController controller;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeClass
    public static void prepareSchema() throws Exception {
        IntegrationDb.assumeAvailable();
        IntegrationDb.resetSchema();
    }

    @Before
    public void freshData() throws Exception {
        IntegrationDb.assumeAvailable();
        IntegrationDb.clearBusinessData();
        Fixtures.seedAddressAndUser();
        Fixtures.seedProduct();

        controller = new ContractController();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        HttpSession session = mock(HttpSession.class);
        // user_id PHẢI là người có thật: owner_id có khoá ngoại sang users, và
        // khi file PDF bỏ trống ô "ownerUsername" thì controller lấy chính
        // người đang đăng nhập làm người phụ trách.
        User currentUser = new User();
        currentUser.setUserId(Fixtures.USER_ID);
        currentUser.setRole(new Role(Fixtures.ROLE_ID, "Admin"));
        when(session.getAttribute("currentUser")).thenReturn(currentUser);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn("/POSCS");
        when(request.getParameter("action")).thenReturn("importPdf");
        when(request.getRequestDispatcher(anyString())).thenReturn(mock(RequestDispatcher.class));
    }

    // ------------------------------------------------------------------
    // Đường thành công
    // ------------------------------------------------------------------

    /**
     * Ca chính: nhập một hợp đồng cho khách hàng CHƯA có trong hệ thống.
     *
     * <p>Khẳng định cả ba thứ mà một bản ghi hợp đồng nhập vào phải có, vì cả
     * ba đều từng sai hoặc thiếu: bản thân hợp đồng (trước đây không ghi được
     * vì direction null), khách hàng mới KÈM VAI, và các dòng hạng mục.
     */
    @Test
    public void importPdf_khachHangMoi_ghiDuHopDongKhachHangVaVai() throws Exception {
        IntegrationDb.assumeAvailable();
        givenUploadedPdf(filledTemplate());

        postToController();

        assertNoImportErrors();

        // 1) Hợp đồng đã vào CSDL. Đây là phép khẳng định mà toàn bộ bộ test
        //    mock không thể đưa ra: nó chỉ đúng khi câu INSERT thật sự chạy
        //    lọt qua ràng buộc NOT NULL của cột direction.
        assertEquals("phải ghi đúng 1 hợp đồng",
                1, IntegrationDb.count("contracts", "contract_code = '" + CONTRACT_CODE + "'"));
        assertEquals("hợp đồng nhập từ mẫu này luôn là hợp đồng BÁN",
                "Bán", IntegrationDb.scalar(
                        "SELECT direction FROM contracts WHERE contract_code = '" + CONTRACT_CODE + "'"));

        // 2) Khách hàng mới phải có VAI, nếu không nó tàng hình ở cả hai danh
        //    sách -- bộ lọc vai dùng EXISTS trên enterprise_roles, và tham số
        //    "kind" không bao giờ là null.
        String enterpriseId = IntegrationDb.scalar(
                "SELECT enterprise_id FROM enterprises WHERE tax_code = '" + NEW_BUYER_TAX + "'");
        assertNotNull("phải tạo khách hàng mới từ thông tin trong file", enterpriseId);
        assertEquals("hợp đồng bán thì đối tác giữ vai Khách mua (cặp đôi CHÉO)",
                "Khách mua", IntegrationDb.scalar(
                        "SELECT role FROM enterprise_roles WHERE enterprise_id = " + enterpriseId));

        // 3) Hạng mục hàng hoá đi cùng hợp đồng, không phải ghi rời.
        assertEquals("phải ghi 1 dòng hạng mục từ product1*",
                1, IntegrationDb.count("contractproducts",
                        "contract_id = (SELECT contract_id FROM contracts WHERE contract_code = '"
                        + CONTRACT_CODE + "')"));
    }

    /**
     * Hợp đồng nhập vào vẫn là bản NHÁP, nhưng GIỮ ngày ký đọc từ bản giấy.
     *
     * <p>Hai điều kiện kéo ngược nhau và phải cùng đúng: luật khách hàng nói
     * nhân viên không tự ký hợp đồng được (nên nhập vào không được thành "Đã
     * ký" luôn, nếu không thì import là đường vòng thoát cổng ký), mà tờ giấy
     * thì đã ký thật rồi và ngày trên đó là ngày duy nhất đúng.
     */
    @Test
    public void importPdf_laBanNhapNhungGiuNgayKyTrenGiay() throws Exception {
        IntegrationDb.assumeAvailable();
        givenUploadedPdf(filledTemplate());

        postToController();

        assertNoImportErrors();
        assertEquals("nhập vào vẫn phải là bản nháp -- import không được là đường vòng thoát cổng ký",
                "Nháp", IntegrationDb.scalar(
                        "SELECT progress_status FROM contracts WHERE contract_code = '" + CONTRACT_CODE + "'"));
        assertEquals("ngày ký phải là ngày trên bản giấy, không phải ngày nhập liệu",
                "2026-02-05", IntegrationDb.scalar(
                        "SELECT signing_date FROM contracts WHERE contract_code = '" + CONTRACT_CODE + "'"));
    }

    /**
     * Bấm Ký một hợp đồng nhập từ PDF KHÔNG được xoá ngày ký trên giấy.
     *
     * <p>{@code changeProgressStatus} đóng dấu {@code signing_date}, nhưng
     * bằng {@code COALESCE(signing_date, CURDATE())}: cột đã có giá trị thì
     * giữ nguyên. Trước đây nó ghi {@code CURDATE()} vô điều kiện, nên ngày ký
     * thật bị thay bằng ngày người dùng tình cờ bấm nút -- mất hẳn, không có
     * dòng nhật ký nào nói rằng nó từng khác.
     */
    @Test
    public void kyHopDongNhapTuPdf_khongGhiDeNgayKyDaCo() throws Exception {
        IntegrationDb.assumeAvailable();
        givenUploadedPdf(filledTemplate());
        postToController();
        assertNoImportErrors();

        int contractId = Integer.parseInt(IntegrationDb.scalar(
                "SELECT contract_id FROM contracts WHERE contract_code = '" + CONTRACT_CODE + "'"));

        assertTrue("phải ký được bản nháp vừa nhập",
                contractDAO.changeProgressStatus(contractId, ContractDAO.PROGRESS_SIGNED,
                        Fixtures.USER_ID, null));

        assertEquals("Đã ký", IntegrationDb.scalar(
                "SELECT progress_status FROM contracts WHERE contract_id = " + contractId));
        assertEquals("ký xong ngày trên giấy phải còn nguyên",
                "2026-02-05", IntegrationDb.scalar(
                        "SELECT signing_date FROM contracts WHERE contract_id = " + contractId));
    }

    /** Khách đã có sẵn (khớp mã số thuế) thì dùng lại, không đẻ thêm bản ghi. */
    @Test
    public void importPdf_khachHangDaCo_dungLaiKhongTaoThem() throws Exception {
        IntegrationDb.assumeAvailable();
        Fixtures.seedEnterprise(); // KH-0001, mã số thuế 0100000001
        int enterprisesBefore = IntegrationDb.count("enterprises", null);

        PdfFields fields = new PdfFields();
        fields.buyerTax = "0100000001";
        givenUploadedPdf(filledTemplate(fields));

        postToController();

        assertNoImportErrors();
        assertEquals("khớp mã số thuế thì dùng lại khách cũ, không tạo thêm",
                enterprisesBefore, IntegrationDb.count("enterprises", null));
        assertEquals("hợp đồng phải trỏ vào đúng khách cũ",
                String.valueOf(Fixtures.ENTERPRISE_ID), IntegrationDb.scalar(
                        "SELECT enterprise_id FROM contracts WHERE contract_code = '" + CONTRACT_CODE + "'"));
    }

    // ------------------------------------------------------------------
    // Đường từ chối
    // ------------------------------------------------------------------

    /**
     * Email trùng khách khác: báo ĐÚNG ô, và không ghi gì cả.
     *
     * <p>{@code enterprises.email} có UNIQUE KEY, nên trước đây lỗi rơi xuống
     * tận CSDL và màn hình chỉ đoán "có thể MST/email/SĐT đã tồn tại" -- người
     * sửa file PDF không biết phải sửa ô nào. Ca này canh cả hai vế: thông báo
     * chỉ đúng chỗ, và CSDL sạch (không để lại khách hàng mồ côi).
     */
    @Test
    public void importPdf_trungEmailKhachKhac_baoDungOVaKhongGhiGi() throws Exception {
        IntegrationDb.assumeAvailable();
        Fixtures.seedEnterprise();
        // Mã số thuế KHÁC (nên không khớp khách cũ, hệ thống sẽ định tạo mới)
        // nhưng email thì trùng -- đúng cái UNIQUE KEY sẽ chặn ở tầng dưới.
        PdfFields fields = new PdfFields();
        fields.buyerEmail = "songhong@example.com";
        givenUploadedPdf(filledTemplate(fields));

        postToController();

        List<String> errors = capturedImportErrors();
        assertNotNull("phải báo lỗi chứ không ghi bừa", errors);
        assertTrue("thông báo phải chỉ đúng ô Email, không phải câu đoán ba ô: " + errors,
                errors.stream().anyMatch(e -> e.contains("Email") && e.contains("songhong@example.com")));

        assertEquals("từ chối rồi thì không được ghi hợp đồng nào",
                0, IntegrationDb.count("contracts", "contract_code = '" + CONTRACT_CODE + "'"));
        assertEquals("cũng không được để lại khách hàng mồ côi",
                0, IntegrationDb.count("enterprises", "tax_code = '" + NEW_BUYER_TAX + "'"));
    }

    /** Mã hợp đồng đã tồn tại: từ chối ngay ở bước kiểm, không đụng CSDL. */
    @Test
    public void importPdf_trungMaHopDong_biTuChoi() throws Exception {
        IntegrationDb.assumeAvailable();
        Fixtures.seedEnterprise();
        IntegrationDb.exec(
            "INSERT INTO contracts (contract_code, title, contract_type, direction, signing_date, "
            + "effective_date, end_date, enterprise_id, owner_id) VALUES ("
            + "'" + CONTRACT_CODE + "', N'Hợp đồng đã có', N'Cung cấp thiết bị', N'Bán', "
            + "'2026-01-01', '2026-01-01', '2026-12-31', "
            + Fixtures.ENTERPRISE_ID + ", " + Fixtures.USER_ID + ")");

        givenUploadedPdf(filledTemplate());

        postToController();

        List<String> errors = capturedImportErrors();
        assertNotNull("phải báo lỗi", errors);
        assertTrue("phải nói rõ mã hợp đồng đã tồn tại: " + errors,
                errors.stream().anyMatch(e -> e.contains("đã tồn tại") && e.contains(CONTRACT_CODE)));
        assertEquals("không được ghi thêm hợp đồng thứ hai cùng mã",
                1, IntegrationDb.count("contracts", "contract_code = '" + CONTRACT_CODE + "'"));
    }

    /**
     * Gọi controller qua ĐÚNG cửa mà servlet container dùng.
     *
     * <p>{@code doPost} là {@code protected} và lớp test này nằm ở package
     * {@code poscs.integration}, không phải {@code poscs.controller} -- và nó
     * phải ở đây, vì CI nhận diện test tích hợp bằng chính tên package đó
     * (xem bước "Assert integration tests actually ran"). Đi qua
     * {@code service(...)} công khai vừa giải được chuyện package, vừa sát
     * thực tế hơn: chính HttpServlet định tuyến theo method của request.
     *
     * <p>Ép kiểu về {@code ServletRequest}/{@code ServletResponse} để chọn
     * tường minh nạp chồng CÔNG KHAI; để nguyên kiểu Http... thì người đọc
     * phải tự suy ra vì sao trình biên dịch không chọn bản protected.
     */
    private void postToController() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        controller.service((ServletRequest) request, (ServletResponse) response);
    }

    // ------------------------------------------------------------------
    // Dựng file PDF đầu vào
    // ------------------------------------------------------------------

    /** Giá trị của các ô trong file mẫu; mặc định là một bộ hợp lệ, test nào cần thì sửa một ô. */
    private static final class PdfFields {
        String contractCode = CONTRACT_CODE;
        String title = "Hợp đồng cung cấp thiết bị trạm BTS";
        String contractType = "Cung cấp thiết bị";
        String signDate = SIGN_DATE;
        String effectiveDate = "10/02/2026";
        String endDate = "31/12/2026";
        String ownerUsername = Fixtures.USERNAME;
        String buyerTax = NEW_BUYER_TAX;
        String buyerName = "Công ty TNHH Viễn thông Mới";
        String buyerType = "Nhà mạng viễn thông";
        String buyerGroup = "Tiềm năng";
        String buyerEmail = NEW_BUYER_EMAIL;
        String buyerPhone = NEW_BUYER_PHONE;
        String buyerProvince = PROVINCE_NAME;
        String buyerWard = WARD_NAME;
        String buyerAddressDetail = "Số 10 Đường Thử Nghiệm";
        String product1Code = "SP-0001";
        String product1Qty = "5";
        String product1Unit = "Bộ";
    }

    private static byte[] filledTemplate() throws Exception {
        return filledTemplate(new PdfFields());
    }

    /**
     * Điền vào chính file mẫu thật rồi trả về bytes.
     *
     * <p>{@code setNeedAppearances(true)}: không có nó, PDFBox sẽ dựng
     * appearance stream cho từng ô ngay lúc setValue và cần font có đủ chữ
     * tiếng Việt -- việc chỉ liên quan tới lúc HIỂN THỊ file, trong khi test
     * này chỉ quan tâm GIÁ TRỊ đọc lại được. Trình đọc PDF tự dựng hình khi mở.
     */
    private static byte[] filledTemplate(PdfFields f) throws Exception {
        byte[] template = Files.readAllBytes(new File(TEMPLATE).toPath());
        try (PDDocument doc = Loader.loadPDF(template)) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
            assertNotNull("file mẫu phải có AcroForm -- nếu null thì chính file mẫu đã hỏng", form);
            form.setNeedAppearances(true);

            set(form, "contractCode", f.contractCode);
            set(form, "title", f.title);
            set(form, "contractType", f.contractType);
            set(form, "signDate", f.signDate);
            set(form, "effectiveDate", f.effectiveDate);
            set(form, "endDate", f.endDate);
            set(form, "ownerUsername", f.ownerUsername);
            set(form, "buyerTax", f.buyerTax);
            set(form, "buyerName", f.buyerName);
            set(form, "buyerType", f.buyerType);
            set(form, "buyerGroup", f.buyerGroup);
            set(form, "buyerEmail", f.buyerEmail);
            set(form, "buyerPhone", f.buyerPhone);
            set(form, "buyerProvince", f.buyerProvince);
            set(form, "buyerWard", f.buyerWard);
            set(form, "buyerAddressDetail", f.buyerAddressDetail);
            set(form, "product1Code", f.product1Code);
            set(form, "product1Qty", f.product1Qty);
            set(form, "product1Unit", f.product1Unit);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Đặt giá trị một ô; tên ô sai là hỏng test ngay tại đây, không âm thầm bỏ qua. */
    private static void set(PDAcroForm form, String name, String value) throws Exception {
        PDField field = form.getField(name);
        assertNotNull("file mẫu không có ô \"" + name + "\" -- mẫu và controller đã lệch nhau", field);
        field.setValue(value);
    }

    // ------------------------------------------------------------------
    // Ghép file vào request, và đọc kết quả controller đẩy sang JSP
    // ------------------------------------------------------------------

    private void givenUploadedPdf(byte[] pdf) throws Exception {
        Part part = mock(Part.class);
        when(part.getSize()).thenReturn((long) pdf.length);
        when(part.getInputStream()).thenAnswer(inv -> new ByteArrayInputStream(pdf));
        when(part.getSubmittedFileName()).thenReturn("hopdong.pdf");
        when(request.getPart("file")).thenReturn(part);
    }

    /**
     * Danh sách lỗi controller đẩy sang JSP, hoặc null nếu không có lỗi nào.
     *
     * <p>Đọc bằng ArgumentCaptor thay vì bắt redirect: luồng này forward sang
     * importcontract.jsp kèm attribute chứ không redirect kèm mã lỗi trên URL
     * -- danh sách lỗi có thể dài và nhiều dòng.
     */
    @SuppressWarnings("unchecked")
    private List<String> capturedImportErrors() {
        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(request, atLeast(0)).setAttribute(eq("importErrors"), value.capture());
        List<Object> all = value.getAllValues();
        if (all.isEmpty()) {
            return null;
        }
        return (List<String>) all.get(all.size() - 1);
    }

    /** Khẳng định import chạy trót lọt -- in ra lỗi cụ thể khi không, để khỏi phải đoán. */
    private void assertNoImportErrors() {
        List<String> errors = capturedImportErrors();
        assertNull("import phải thành công, nhưng báo lỗi: " + errors, errors);

        ArgumentCaptor<Object> single = ArgumentCaptor.forClass(Object.class);
        verify(request, atLeast(0)).setAttribute(eq("importError"), single.capture());
        assertTrue("import phải thành công, nhưng báo lỗi: " + single.getAllValues(),
                single.getAllValues().isEmpty());
    }
}
