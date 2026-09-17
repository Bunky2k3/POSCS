package poscs.controller;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.AccessControl;
import poscs.common.ExcelUtil;
import poscs.common.Logs;
import poscs.common.PdfUtil;
import poscs.common.Period;
import poscs.common.TextRules;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.ProductDAO;
import poscs.model.Address;
import poscs.model.Contract;
import poscs.model.ContractProduct;
import poscs.model.District;
import poscs.model.Enterprise;
import poscs.model.Product;
import poscs.model.Province;
import poscs.model.User;

/**
 * Controller cho phần "Thông tin chung" của hợp đồng (bảng contracts).
 * Trang chi tiết (showDetail) cũng hiển thị hạng mục sản phẩm/dịch vụ
 * (contractproducts) ở dạng CHỈ ĐỌC -- bảng đó chưa có cột lưu đơn giá nên
 * chưa thể tính thành tiền/VAT/tổng cộng như mockup UI, và form thêm/sửa
 * hợp đồng ở đây chưa có UI để gắn/gỡ sản phẩm, thuộc phạm vi khác. Điều
 * hướng theo tham số "action" -- quyền hạn theo PERMISSIONS.md enforce
 * bằng AccessControl.requireFullAccess ở đầu mỗi hàm handleCreate/
 * handleUpdate/handleDelete (Kỹ thuật/CSKH chỉ View only trên Contract).
 */
@WebServlet(name = "ContractController", urlPatterns = {"/contract", "/contract/byEnterprise"})
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 10 * 1024 * 1024, fileSizeThreshold = 1024 * 1024)
public class ContractController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ContractController.class);

    private static final int PAGE_SIZE = 10;
    /** Vai được giao phụ trách hợp đồng -- khớp roles.role_name, xem AccessControl. */
    private static final String SALES_ROLE = "Sales";

    /**
     * Chiều hợp đồng, khớp contracts.direction (xem ghi chú đầu V21).
     *
     * <p>Theo góc nhìn CỦA MÌNH, và cặp đôi với vai đối tác bị CHÉO: hợp đồng
     * BÁN thì bên kia là "Khách mua". Đó là lý do V20 không đặt vai khách hàng
     * là 'Mua'/'Bán'.
     */
    private static final String DIRECTION_SELL = "Bán";
    private static final String DIRECTION_BUY = "Mua";
    private static final String ROLE_BUYER = "Khách mua";
    private static final String ROLE_SUPPLIER = "Nhà cung cấp";

    private static final String LIST_VIEW = "/jsp/sale/listcontract.jsp";
    private static final String DETAIL_VIEW = "/jsp/sale/viewcontractdetail.jsp";
    private static final String CREATE_VIEW = "/jsp/sale/addnewcontract.jsp";
    private static final String UPDATE_VIEW = "/jsp/sale/updatecontract.jsp";
    private static final String IMPORT_VIEW = "/jsp/sale/importcontract.jsp";

    /** Số dòng sản phẩm tối đa trong hopdong_import_template.pdf (field product1..product15). */
    private static final int IMPORT_MAX_PRODUCT_ROWS = 15;

    /** Bắt id file trong link Drive dạng .../file/d/<id>/... -- xem drivePreviewUrl(). */
    private static final java.util.regex.Pattern DRIVE_FILE_ID =
            java.util.regex.Pattern.compile("drive\\.google\\.com/file/d/([A-Za-z0-9_-]+)");

    private final ContractDAO contractDAO = new ContractDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final AddressDAO addressDAO = new AddressDAO();
    private final ProductDAO productDAO = new ProductDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // /contract/byEnterprise trả JSON cho dropdown "Hợp đồng liên quan" ở
        // form phiếu hỗ trợ -- không đi qua tham số action như các màn hình khác.
        if ("/contract/byEnterprise".equals(request.getServletPath())) {
            listByEnterpriseAsJson(request, response);
            return;
        }
        // Cho JSP biết người đang xem có quyền Full trên tài nguyên này không,
        // để ẩn các nút hành động không dùng được (Tạo/Sửa/Xoá/Nhập/Xuất) thay
        // vì để người ta bấm vào rồi nhận 403. Đây CHỈ là lớp trình bày --
        // chặn thật nằm ở AccessControl.requireFullAccess trong doPost và ở
        // đầu mỗi trang form bên dưới (gõ thẳng URL cũng không vào được).
        request.setAttribute("canManage",
                AccessControl.hasFullAccess(request, AccessControl.Resource.CONTRACT));
        String action = request.getParameter("action");
        if (action == null) {
            action = "list";
        }
        switch (action) {
            case "view":
                showDetail(request, response);
                break;
            case "new":
                showCreateForm(request, response);
                break;
            case "edit":
                showEditForm(request, response);
                break;
            case "exportExcel":
                exportExcel(request, response);
                break;
            case "exportPdf":
                exportPdf(request, response);
                break;
            case "importForm":
                showImportForm(request, response);
                break;
            case "downloadImportTemplate":
                downloadImportTemplate(request, response);
                break;
            case "list":
            default:
                showList(request, response);
                break;
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if (action == null) {
            action = "";
        }
        switch (action) {
            case "create":
                handleCreate(request, response);
                break;
            case "update":
                handleUpdate(request, response);
                break;
            case "delete":
                handleDelete(request, response);
                break;
            case "importPdf":
                handleImportPdf(request, response);
                break;
            case "addProduct":
                handleAddProduct(request, response);
                break;
            case "removeProduct":
                handleRemoveProduct(request, response);
                break;
            default:
                response.sendRedirect(request.getContextPath() + "/contract");
        }
    }

    // ------------------------------------------------------------------
    // GET actions
    // ------------------------------------------------------------------

    private void showList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int page = parseIntOrDefault(request.getParameter("page"), 1);
        if (page < 1) {
            page = 1;
        }
        String keyword = request.getParameter("keyword");
        String statusFilter = request.getParameter("status");
        String typeFilter = request.getParameter("type");
        Integer provinceFilter = parseIntOrNull(request.getParameter("provinceId"));
        Period period = Period.parse(request.getParameter("year"), request.getParameter("period"));
        // Chiều đứng ĐỘC LẬP với kỳ -- hai mục con vẫn lọc được theo năm/quý/tháng.
        String direction = directionFromKind(request.getParameter("kind"));

        List<Contract> contractList = contractDAO.findAll(page, PAGE_SIZE, keyword, statusFilter, typeFilter,
                provinceFilter, false, period, direction);
        int totalCount = contractDAO.countAll(keyword, statusFilter, typeFilter, provinceFilter, period, direction);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));
        // Dải KPI trạng thái phải đếm CÙNG phạm vi với bảng bên dưới: đứng ở
        // Hợp đồng mua mà KPI gộp cả hợp đồng bán thì hai con số cạnh nhau
        // không khớp, và không có gì trên màn hình giải thích vì sao.
        Map<String, Integer> statusSummary = contractDAO.countStatusSummary(provinceFilter, period, direction);

        request.setAttribute("contractList", contractList);
        request.setAttribute("statusSummary", statusSummary);
        request.setAttribute("provinceList", addressDAO.findBranchProvinces());
        request.setAttribute("currentPage", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalCount", totalCount);
        // JSP cần pageSize để đánh STT liên tục qua các trang (trang 2 bắt đầu từ 11).
        request.setAttribute("pageSize", PAGE_SIZE);
        request.setAttribute("keyword", keyword);
        request.setAttribute("statusFilter", statusFilter);
        request.setAttribute("typeFilter", typeFilter);
        request.setAttribute("provinceFilter", provinceFilter);
        request.setAttribute("kind", DIRECTION_BUY.equals(direction) ? "buy" : "sell");
        setPeriodAttributes(request, period);

        request.getRequestDispatcher(LIST_VIEW).forward(request, response);
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        Contract contract = id != null ? contractDAO.findById(id) : null;
        if (contract == null) {
            // MSG-021: hợp đồng không tồn tại
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }

        request.setAttribute("contract", contract);
        // Link đính kèm được kiểm scheme LẠI ở đây, không chỉ lúc ghi.
        //
        // viewcontractdetail.jsp đổ giá trị này thẳng vào href; fn:escapeXml
        // chặn được dấu ngoặc kép nhưng KHÔNG vô hiệu hoá scheme, nên một giá
        // trị dạng "javascript:..." vẫn chạy trên origin của ứng dụng khi người
        // dùng bấm vào. handleCreate/handleUpdate đã chặn, nhưng cột
        // attachment_url không có ràng buộc nào ở CSDL và dữ liệu vẫn vào cột
        // này ngoài hai đường đó (schema.sql seed thẳng, hoặc chạy tay câu
        // UPDATE) -- view không nên phụ thuộc vào việc MỌI đường ghi từ trước
        // tới nay đều đã đúng.
        String attachmentUrl = contract.getAttachmentUrl();
        boolean attachmentIsSafe = TextRules.isSafeHttpUrl(attachmentUrl);
        request.setAttribute("attachmentUrl", attachmentIsSafe ? attachmentUrl : null);
        request.setAttribute("attachmentUnsafe", attachmentUrl != null && !attachmentIsSafe);
        request.setAttribute("drivePreviewUrl", attachmentIsSafe ? drivePreviewUrl(attachmentUrl) : null);
        // Huỷ bản ghi chỉ dành cho Admin -- xem handleDelete. Không còn điều
        // kiện theo trạng thái: hợp đồng nào cũng đã ký, nên "xoá được hay
        // không" bây giờ là câu hỏi về quyền, không phải về ngày tháng.
        request.setAttribute("canVoid", AccessControl.isAdmin(request));
        request.setAttribute("contractProducts", contractDAO.findProductsByContractId(id));
        request.setAttribute("contractHistory", contractDAO.findHistoryByContractId(id));
        // Danh sách sản phẩm còn hoạt động, phục vụ dropdown "Thêm sản phẩm" bên dưới bảng hạng mục.
        request.setAttribute("productOptions", productDAO.findAll(1, Integer.MAX_VALUE, null, null));

        request.getRequestDispatcher(DETAIL_VIEW).forward(request, response);
    }

    /** Xuất Excel toàn bộ hợp đồng khớp filter hiện tại (không phân trang) -- nút "Xuất Excel" ở listcontract.jsp. */
    private void exportExcel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String keyword = request.getParameter("keyword");
        String statusFilter = request.getParameter("status");
        String typeFilter = request.getParameter("type");
        Integer provinceFilter = parseIntOrNull(request.getParameter("provinceId"));
        Period period = Period.parse(request.getParameter("year"), request.getParameter("period"));

        // Sắp theo tỉnh: hợp đồng được giao việc theo địa bàn nên file xuất ra
        // phải gom các hợp đồng cùng tỉnh lại với nhau.
        // Xuất đúng danh sách đang xem, giữ nguyên cả kỳ lẫn chiều.
        String direction = directionFromKind(request.getParameter("kind"));
        List<Contract> all = contractDAO.findAll(1, Integer.MAX_VALUE, keyword, statusFilter, typeFilter,
                provinceFilter, true, period, direction);
        // Giữ cột "Mã HĐ" trong file dù danh sách trên màn hình đã bỏ -- xem lý do
        // ở CustomerController.exportExcel: STT chỉ đúng trong phạm vi một file.
        String[] headers = {"STT", "Mã HĐ", "Tiêu đề", "Loại HĐ", "Tỉnh/Thành phố", "Khách hàng", "Người phụ trách",
            "Ngày ký", "Ngày hiệu lực", "Ngày kết thúc", "Trạng thái"};
        List<Object[]> rows = new ArrayList<>();
        int stt = 1;
        for (Contract c : all) {
            rows.add(new Object[]{
                stt++,
                c.getContractCode(),
                c.getTitle(),
                c.getContractType(),
                provinceNameOf(c),
                c.getEnterprise() != null ? c.getEnterprise().getEnterpriseName() : "",
                c.getOwner() != null ? c.getOwner().getFullName() : "",
                c.getSigningDate() != null ? c.getSigningDate().toString() : "",
                c.getEffectiveDate() != null ? c.getEffectiveDate().toString() : "",
                c.getEndDate() != null ? c.getEndDate().toString() : "",
                c.getStatus()
            });
        }
        ExcelUtil.writeWorkbook(response,
                DIRECTION_BUY.equals(direction) ? "hop_dong_mua" : "hop_dong_ban",
                headers, rows);
    }

    /**
     * Xuất PDF 1 hợp đồng -- nút "Xuất PDF" ở viewcontractdetail.jsp.
     * contract.enterprise (từ ContractDAO) chỉ có id+tên nên phải gọi lại
     * customerDAO.findById() lấy Enterprise đầy đủ (địa chỉ/MST/người đại
     * diện) để in vào PDF. Không có đơn giá/thành tiền (xem javadoc đầu file).
     */
    /** Toạ độ y (tính từ đáy trang) nơi vùng bảng sản phẩm bắt đầu trên trang 2 của
     * hopdong_template.pdf -- phải khớp với TABLE_TOP_Y trong script đã dùng để dựng
     * file mẫu đó (xem GenerateContractTemplate, không thuộc source repo). */
    private static final float TABLE_TOP_Y = 732f;
    private static final int TABLE_PAGE_INDEX = 1;

    private void exportPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        Contract contract = id != null ? contractDAO.findById(id) : null;
        if (contract == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }
        List<ContractProduct> items = contractDAO.findProductsByContractId(id);
        Enterprise enterprise = customerDAO.findById(contract.getEnterpriseId());

        try (PDDocument document = PdfUtil.loadTemplate(getServletContext(), "/WEB-INF/templates/hopdong_template.pdf")) {
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                throw new IOException("File mẫu hopdong_template.pdf không có AcroForm để điền.");
            }

            PdfUtil.fillField(acroForm, "contractCode", safe(contract.getContractCode()));
            PdfUtil.fillField(acroForm, "signDate", formatDate(contract.getSigningDate()));
            PdfUtil.fillField(acroForm, "effectiveDate", formatDate(contract.getEffectiveDate()));
            PdfUtil.fillField(acroForm, "endDate", formatDate(contract.getEndDate()));
            PdfUtil.fillField(acroForm, "sellerRepName", contract.getOwner() != null ? safe(contract.getOwner().getFullName()) : "");
            if (enterprise != null) {
                String address = enterprise.getAddress() != null ? enterprise.getAddress().getFullAddress() : "";
                PdfUtil.fillField(acroForm, "buyerName", safe(enterprise.getEnterpriseName()));
                PdfUtil.fillField(acroForm, "buyerTax", safe(enterprise.getTaxCode()));
                PdfUtil.fillField(acroForm, "buyerAddress", address);
                PdfUtil.fillField(acroForm, "buyerRep", safe(enterprise.getLegalRepresentative()));
                PdfUtil.fillField(acroForm, "buyerPhone", safe(enterprise.getPhone()));
                PdfUtil.fillField(acroForm, "buyerEmail", safe(enterprise.getEmail()));
            }
            acroForm.flatten();

            PDPage tablePage = document.getPage(TABLE_PAGE_INDEX);
            PDFont font = PdfUtil.loadVietnameseFont(document, getServletContext());
            float margin = 50f;
            float width = tablePage.getMediaBox().getWidth() - margin * 2;

            float[] colWidths = {30, width - 30 - 60 - 70 - 140, 60, 70, 140};
            String[] tableHeaders = {"#", "Sản phẩm", "SL", "Đơn vị", "Ghi chú"};
            List<String[]> tableRows = new ArrayList<>();
            int stt = 1;
            for (ContractProduct item : items) {
                tableRows.add(new String[]{
                    String.valueOf(stt++),
                    item.getProductName() + " (" + item.getProductCode() + ")",
                    String.valueOf(item.getQuantity()),
                    safe(item.getUnit()),
                    safe(item.getNotes())
                });
            }
            int rowsDrawn;
            try (PDPageContentStream cs = new PDPageContentStream(document, tablePage,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                float minY = tablePage.getMediaBox().getLowerLeftY() + margin;
                rowsDrawn = PdfUtil.drawTable(cs, font, font, 9, margin, TABLE_TOP_Y, minY, colWidths, tableHeaders, tableRows);
            }
            if (rowsDrawn < tableRows.size()) {
                // Bảng sản phẩm dài hơn chỗ trống còn lại của trang mẫu -- không
                // gửi 1 file PDF hợp đồng bị thiếu dữ liệu cho người dùng mà
                // không báo gì (xem javadoc drawTable). Quay lại trang chi tiết
                // hợp đồng (thay vì response.sendError ra trang lỗi mặc định
                // của container, không có giao diện/điều hướng của app) kèm mã
                // lỗi để hiển thị thông báo rõ ràng -- xem viewcontractdetail.jsp.
                response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + id + "&error=pdf_overflow");
                return;
            }

            response.setContentType("application/pdf");
            String fileName = "hopdong_" + safe(contract.getContractCode()).replace("/", "-") + ".pdf";
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            document.save(response.getOutputStream());
        }
    }

    private void showImportForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Trang form cũng là thao tác quản trị: vai trò chỉ-xem không được
        // vào đây, dù nút bấm đã ẩn ở danh sách (xem PERMISSIONS.md).
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
    }

    private void downloadImportTemplate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try (PDDocument document = PdfUtil.loadTemplate(getServletContext(), "/WEB-INF/templates/hopdong_import_template.pdf")) {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"mau_nhap_hopdong.pdf\"");
            document.save(response.getOutputStream());
        }
    }

    /**
     * Nhập 1 hợp đồng mới từ file PDF điền theo mẫu downloadImportTemplate()
     * (khác nhập Excel: mỗi file PDF chỉ chứa đúng 1 hợp đồng). Vì vậy nếu có
     * bất kỳ lỗi nào (dù chỉ 1 dòng sản phẩm sai) thì KHÔNG ghi gì vào CSDL cả
     * -- validate hết 1 lượt rồi mới insert, tránh để lại khách hàng/hợp đồng
     * mồ côi nếu insert giữa chừng thất bại.
     */
    private void handleImportPdf(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }

        Part filePart = request.getPart("file");
        if (filePart == null || filePart.getSize() <= 0) {
            request.setAttribute("importError", "Vui lòng chọn file .pdf để nhập.");
            request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
            return;
        }

        PDAcroForm acroForm;
        try (PDDocument uploaded = org.apache.pdfbox.Loader.loadPDF(filePart.getInputStream().readAllBytes())) {
            acroForm = uploaded.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                request.setAttribute("importError", "File không đúng mẫu (không có field để đọc) -- hãy tải lại file mẫu và điền trên chính file đó.");
                request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
                return;
            }

            List<String> errors = new ArrayList<>();

            String contractCode = PdfUtil.readField(acroForm, "contractCode");
            String title = PdfUtil.readField(acroForm, "title");
            String contractType = PdfUtil.readField(acroForm, "contractType");
            Date signDate = parsePdfDate(PdfUtil.readField(acroForm, "signDate"));
            Date effectiveDate = parsePdfDate(PdfUtil.readField(acroForm, "effectiveDate"));
            Date endDate = parsePdfDate(PdfUtil.readField(acroForm, "endDate"));
            String ownerUsername = PdfUtil.readField(acroForm, "ownerUsername");

            String buyerTax = PdfUtil.readField(acroForm, "buyerTax");
            String buyerName = PdfUtil.readField(acroForm, "buyerName");
            String buyerType = PdfUtil.readField(acroForm, "buyerType");
            String buyerGroup = PdfUtil.readField(acroForm, "buyerGroup");
            String buyerEmail = PdfUtil.readField(acroForm, "buyerEmail");
            String buyerPhone = PdfUtil.readField(acroForm, "buyerPhone");
            String buyerWebsite = PdfUtil.readField(acroForm, "buyerWebsite");
            String buyerProvince = PdfUtil.readField(acroForm, "buyerProvince");
            String buyerWard = PdfUtil.readField(acroForm, "buyerWard");
            String buyerAddressDetail = PdfUtil.readField(acroForm, "buyerAddressDetail");
            String buyerRep = PdfUtil.readField(acroForm, "buyerRep");

            if (isBlank(title)) {
                errors.add("Thiếu \"Tiêu đề hợp đồng\".");
            }
            List<String> validTypes = java.util.Arrays.asList("Cung cấp thiết bị", "Thi công lắp đặt", "Bảo trì bảo dưỡng");
            if (isBlank(contractType) || !validTypes.contains(contractType.trim())) {
                errors.add("\"Loại hợp đồng\" không hợp lệ -- chỉ nhận: " + String.join(", ", validTypes) + ".");
            }
            if (signDate == null || effectiveDate == null || endDate == null) {
                errors.add("Ngày ký/Hiệu lực từ/Đến ngày thiếu hoặc sai định dạng (phải là dd/MM/yyyy).");
            } else if (signDate.after(effectiveDate) || effectiveDate.after(endDate)) {
                errors.add("Ngày ký phải ≤ Hiệu lực từ phải ≤ Đến ngày.");
            }
            if (!isBlank(contractCode)) {
                // Cố ý KHÔNG lọc chiều ở đây: mã hợp đồng là duy nhất trên
                // toàn bảng, tra theo một chiều là bỏ sót trùng ở chiều kia.
                boolean exists = contractDAO.findAll(1, Integer.MAX_VALUE, contractCode.trim(), null, null).stream()
                        .anyMatch(c -> c.getContractCode() != null && c.getContractCode().equalsIgnoreCase(contractCode.trim()));
                if (exists) {
                    errors.add("Mã hợp đồng \"" + contractCode.trim() + "\" đã tồn tại.");
                }
            }

            if (isBlank(buyerTax)) {
                errors.add("Thiếu \"Mã số thuế\" khách hàng.");
            }
            if (isBlank(buyerName)) {
                errors.add("Thiếu \"Tên doanh nghiệp\" khách hàng.");
            }
            if (isBlank(buyerEmail) || !isValidEmail(buyerEmail)) {
                errors.add("\"Email\" khách hàng thiếu hoặc không đúng định dạng.");
            }
            if (isBlank(buyerPhone) || !isValidPhone(buyerPhone)) {
                errors.add("\"Điện thoại\" khách hàng thiếu hoặc không đúng định dạng.");
            }

            User currentUser = AccessControl.currentUser(request);
            List<User> staff = employeeDAO.findAllActive();
            User owner = isBlank(ownerUsername) ? null : findUserByUsername(staff, ownerUsername);
            int ownerId = owner != null ? owner.getUserId() : currentUser.getUserId();

            List<Enterprise> allEnterprises = customerDAO.findAll(1, Integer.MAX_VALUE, null, null, null);
            Enterprise matchedEnterprise = null;
            if (!isBlank(buyerTax)) {
                for (Enterprise e : allEnterprises) {
                    if (e.getTaxCode() != null && e.getTaxCode().trim().equalsIgnoreCase(buyerTax.trim())) {
                        matchedEnterprise = e;
                        break;
                    }
                }
            }

            Enterprise newEnterprise = null;
            if (matchedEnterprise == null && !isBlank(buyerTax) && !isBlank(buyerName)) {
                if (isBlank(buyerType)) {
                    errors.add("Khách hàng chưa có trong hệ thống -- cần điền \"Loại KH\" để tạo mới.");
                }
                if (isBlank(buyerGroup)) {
                    errors.add("Khách hàng chưa có trong hệ thống -- cần điền \"Nhóm KH\" để tạo mới.");
                }
                Province province = null;
                District ward = null;
                if (isBlank(buyerProvince) || isBlank(buyerWard) || isBlank(buyerAddressDetail)) {
                    errors.add("Khách hàng chưa có trong hệ thống -- cần điền đủ Tỉnh/Thành, Xã/Phường, Địa chỉ chi tiết để tạo mới.");
                } else {
                    province = findProvinceByName(addressDAO.findAllProvinces(), buyerProvince);
                    if (province == null) {
                        errors.add("Không tìm thấy tỉnh/thành \"" + buyerProvince.trim() + "\".");
                    } else {
                        ward = findWardByName(addressDAO.findWardsByProvinceId(province.getProvinceId()), buyerWard);
                        if (ward == null) {
                            errors.add("Không tìm thấy xã/phường \"" + buyerWard.trim() + "\" thuộc \"" + buyerProvince.trim() + "\".");
                        }
                    }
                }
                if (province != null && ward != null) {
                    newEnterprise = new Enterprise();
                    newEnterprise.setEnterpriseName(buyerName.trim());
                    newEnterprise.setCustomerType(emptyToNull(buyerType));
                    newEnterprise.setCustomerGroup(emptyToNull(buyerGroup));
                    newEnterprise.setTaxCode(buyerTax.trim());
                    newEnterprise.setEmail(buyerEmail.trim());
                    newEnterprise.setPhone(buyerPhone.trim());
                    newEnterprise.setWebsite(emptyToNull(buyerWebsite));
                    newEnterprise.setLegalRepresentative(emptyToNull(buyerRep));
                    newEnterprise.setStatus("Active");
                    newEnterprise.setAccountOwnerId(ownerId);
                    Address address = new Address();
                    address.setStreetAndLocalName(buyerAddressDetail.trim());
                    address.setDistrictId(ward.getDistrictId());
                    newEnterprise.setAddress(address);
                }
            }

            List<ContractProduct> items = new ArrayList<>();
            for (int row = 1; row <= IMPORT_MAX_PRODUCT_ROWS; row++) {
                String code = PdfUtil.readField(acroForm, "product" + row + "Code");
                if (isBlank(code)) {
                    continue;
                }
                Product product = productDAO.findByCode(code.trim());
                if (product == null) {
                    errors.add("Dòng sản phẩm " + row + ": không tìm thấy mã sản phẩm \"" + code.trim() + "\".");
                    continue;
                }
                String qtyText = PdfUtil.readField(acroForm, "product" + row + "Qty");
                Integer qty = parseQuantityOrNull(qtyText);
                if (qty == null || qty <= 0) {
                    errors.add("Dòng sản phẩm " + row + ": số lượng không hợp lệ.");
                    continue;
                }
                String unit = PdfUtil.readField(acroForm, "product" + row + "Unit");
                String notes = PdfUtil.readField(acroForm, "product" + row + "Notes");

                ContractProduct item = new ContractProduct();
                item.setProductId(product.getProductId());
                item.setQuantity(qty);
                item.setUnit(isBlank(unit) ? "Cái" : unit.trim());
                item.setNotes(emptyToNull(notes));
                items.add(item);
            }

            if (!errors.isEmpty()) {
                request.setAttribute("importErrors", errors);
                request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
                return;
            }

            boolean createdNewEnterprise = matchedEnterprise == null;
            int enterpriseId;
            if (!createdNewEnterprise) {
                enterpriseId = matchedEnterprise.getEnterpriseId();
            } else {
                newEnterprise.setEnterpriseCode(customerDAO.generateNextEnterpriseCode());
                enterpriseId = customerDAO.insert(newEnterprise);
                if (enterpriseId <= 0) {
                    request.setAttribute("importErrors", List.of("Lưu khách hàng mới thất bại (có thể MST/email/SĐT đã tồn tại)."));
                    request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
                    return;
                }
            }

            Contract contract = new Contract();
            contract.setContractCode(isBlank(contractCode) ? contractDAO.generateNextContractCode() : contractCode.trim());
            contract.setTitle(title.trim());
            contract.setContractType(contractType.trim());
            contract.setSigningDate(signDate);
            contract.setEffectiveDate(effectiveDate);
            contract.setEndDate(endDate);
            contract.setEnterpriseId(enterpriseId);
            contract.setOwnerId(ownerId);

            int contractId = contractDAO.insert(contract, currentUser.getUserId());
            if (contractId <= 0) {
                // tránh để lại khách hàng mồ côi (không có hợp đồng nào) nếu vừa tạo
                // enterprise mới ở bước trên nhưng insert hợp đồng lại thất bại
                if (createdNewEnterprise) {
                    customerDAO.softDelete(enterpriseId);
                }
                request.setAttribute("importErrors", List.of("Lưu hợp đồng thất bại (có thể mã hợp đồng đã tồn tại)."));
                request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
                return;
            }
            if (!contractDAO.insertProducts(contractId, items, currentUser.getUserId())) {
                // giữ đúng cam kết "không ghi gì nếu có lỗi" ở javadoc đầu hàm: hợp
                // đồng vừa tạo (và khách hàng mới nếu có) không được để lại mồ côi
                // với 0 dòng sản phẩm trong khi vẫn báo import thành công
                contractDAO.voidRecord(contractId, currentUser.getUserId(),
                        "Nhập hợp đồng từ PDF thất bại ở bước ghi hạng mục hàng hoá -- bản ghi được thu hồi tự động.");
                if (createdNewEnterprise) {
                    customerDAO.softDelete(enterpriseId);
                }
                request.setAttribute("importErrors", List.of("Lưu hạng mục sản phẩm thất bại -- chưa có gì được ghi vào CSDL, hãy thử lại."));
                request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
                return;
            }

            request.setAttribute("importSuccessCode", contract.getContractCode());
            request.setAttribute("importSuccessId", contractId);
            request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
        } catch (Exception ex) {
            LOG.error("Loi nhap hop dong tu pdf", ex);
            request.setAttribute("importError", "Không đọc được file -- hãy chắc chắn đây là file .pdf đúng mẫu.");
            request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
        }
    }

    private Date parsePdfDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            sdf.setLenient(false);
            return new Date(sdf.parse(value.trim()).getTime());
        } catch (java.text.ParseException ex) {
            return null;
        }
    }

    private Province findProvinceByName(List<Province> provinces, String name) {
        String normalized = normalize(name);
        for (Province p : provinces) {
            if (normalize(p.getShortName()).equals(normalized) || normalize(p.getProvinceName()).equals(normalized)) {
                return p;
            }
        }
        return null;
    }

    private District findWardByName(List<District> wards, String name) {
        String normalized = normalize(name);
        for (District d : wards) {
            if (normalize(d.getShortName()).equals(normalized) || normalize(d.getDistrictName()).equals(normalized)) {
                return d;
            }
        }
        return null;
    }

    private User findUserByUsername(List<User> staff, String username) {
        String normalized = normalize(username);
        for (User u : staff) {
            if (u.getUsername() != null && normalize(u.getUsername()).equals(normalized)) {
                return u;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** BR-09: chấp nhận cả số di động lẫn số bàn Việt Nam (VD: 024 3822 1234). */
    private boolean isValidPhone(String phone) {
        if (phone == null) {
            return false;
        }
        return phone.replaceAll("[\\s.-]", "").matches("^(0|\\+84)[0-9]{9,10}$");
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String formatDate(Date date) {
        return date == null ? "" : new SimpleDateFormat("dd/MM/yyyy").format(date);
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Trang form cũng là thao tác quản trị: vai trò chỉ-xem không được
        // vào đây, dù nút bấm đã ẩn ở danh sách (xem PERMISSIONS.md).
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        // Chiều lấy từ mục con người dùng vừa đứng, và ô "Khách hàng" lọc
        // theo đúng chiều đó -- bấm Thêm từ Hợp đồng mua thì chỉ chọn được
        // nhà cung cấp.
        String direction = directionFromKind(request.getParameter("kind"));
        setDropdownAttributes(request, null, direction, null);
        request.setAttribute("direction", direction);
        request.setAttribute("kind", DIRECTION_BUY.equals(direction) ? "buy" : "sell");
        request.getRequestDispatcher(CREATE_VIEW).forward(request, response);
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Trang form cũng là thao tác quản trị: vai trò chỉ-xem không được
        // vào đây, dù nút bấm đã ẩn ở danh sách (xem PERMISSIONS.md).
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        Contract contract = id != null ? contractDAO.findById(id) : null;
        if (contract == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }

        request.setAttribute("contract", contract);
        // Chiều của hợp đồng đang sửa, KHÔNG phải của tham số kind trên URL:
        // mở hợp đồng mua từ link bất kỳ vẫn phải thấy danh sách nhà cung cấp.
        setDropdownAttributes(request, contract.getOwnerId(),
                contract.getDirection(), contract.getEnterpriseId());
        request.setAttribute("direction", contract.getDirection());
        request.setAttribute("kind", DIRECTION_BUY.equals(contract.getDirection()) ? "buy" : "sell");
        request.getRequestDispatcher(UPDATE_VIEW).forward(request, response);
    }

    // ------------------------------------------------------------------
    // POST actions
    // ------------------------------------------------------------------

    private void handleCreate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Contract c = buildContractFromRequest(request, new Contract());
        // Chiều lấy từ mục con đang đứng, không từ form -- xem
        // buildContractFromRequest.
        c.setDirection(directionFromKind(request.getParameter("kind")));
        if (!isValid(c) || !counterpartyMatchesDirection(c)) {
            response.sendRedirect(request.getContextPath()
                    + "/contract?action=new&kind=" + (DIRECTION_BUY.equals(c.getDirection()) ? "buy" : "sell")
                    + "&error=invalid");
            return;
        }
        if (!TextRules.isSafeHttpUrl(c.getAttachmentUrl())) {
            response.sendRedirect(request.getContextPath() + "/contract?action=new&error=invalid_drive_link");
            return;
        }
        c.setContractCode(contractDAO.generateNextContractCode());

        int newId = contractDAO.insert(c, actorId(request));
        if (newId <= 0) {
            LOG.warn("Tao hop dong that bai (actor={}, contractCode={})", Logs.actor(request), c.getContractCode());
            response.sendRedirect(request.getContextPath() + "/contract?action=new&error=create_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + newId);
    }

    private void handleUpdate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("contractId"));
        // Kiểm tồn tại trước khi kiểm dữ liệu: sửa một id không có thật mà báo
        // "dữ liệu chưa hợp lệ" thì người dùng đi sửa form mãi không xong.
        if (id == null || contractDAO.findById(id) == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }

        Contract c = buildContractFromRequest(request, new Contract());
        c.setContractId(id);
        // Giữ nguyên chiều cũ -- DAO.update cũng không ghi cột này. Nhưng vẫn
        // phải gán để kiểm được khách hàng mới chọn có đúng vai không.
        c.setDirection(contractDAO.findById(id).getDirection());
        if (!isValid(c) || !counterpartyMatchesDirection(c)) {
            response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + id + "&error=invalid");
            return;
        }
        if (!TextRules.isSafeHttpUrl(c.getAttachmentUrl())) {
            response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + id + "&error=invalid_drive_link");
            return;
        }

        boolean ok = contractDAO.update(c, actorId(request));
        if (!ok) {
            LOG.warn("Cap nhat hop dong that bai (actor={}, contractId={})", Logs.actor(request), id);
            response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + id + "&error=update_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + id);
    }

    /**
     * Huỷ một bản ghi hợp đồng NHẬP NHẦM khỏi danh sách -- không phải huỷ hợp
     * đồng ngoài đời.
     *
     * <p>Trước đây đây là "xoá hợp đồng" theo BR-46, mở cho cả Sales và chặn
     * bằng một điều kiện tính theo lịch (xem {@link ContractDAO#voidRecord} để
     * biết vì sao điều kiện đó sai). Hợp đồng đã ký là chứng cứ, không xoá được
     * trong nghiệp vụ; nên việc còn lại chỉ là sửa hậu quả của một lần nhập
     * liệu sai, và nó thuộc về Admin, có lý do, có dấu vết.
     */
    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireAdmin(request, response)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        // Kiểm tồn tại trước: với id không có thật thì voidRecord cũng trả
        // false, và người dùng nhận thông báo "không huỷ được" -- sai hẳn lý
        // do, tưởng là vướng ràng buộc nghiệp vụ.
        if (id == null || contractDAO.findById(id) == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }

        String reason = request.getParameter("voidReason");
        if (isBlank(reason)) {
            response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + id + "&error=void_reason_required");
            return;
        }

        if (!contractDAO.voidRecord(id, actorId(request), reason)) {
            LOG.warn("Huy ban ghi hop dong that bai (actor={}, contractId={})", Logs.actor(request), id);
            response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + id + "&error=void_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract");
    }

    /** Gắn thêm 1 dòng sản phẩm/dịch vụ vào hợp đồng đã có -- nút "Thêm sản phẩm" ở viewcontractdetail.jsp. */
    private void handleAddProduct(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer contractId = parseIntOrNull(request.getParameter("contractId"));
        if (contractId == null || contractDAO.findById(contractId) == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }

        Integer productId = parseIntOrNull(request.getParameter("productId"));
        Integer quantity = parseQuantityOrNull(request.getParameter("quantity"));
        String unit = request.getParameter("unit");
        String notes = request.getParameter("notes");

        Product product = productId != null ? productDAO.findById(productId) : null;
        if (product == null || quantity == null || quantity <= 0) {
            response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + contractId + "&error=add_product_invalid");
            return;
        }

        ContractProduct item = new ContractProduct();
        item.setProductId(product.getProductId());
        // Tên chỉ để dựng câu nhật ký ("Thêm 1 hạng mục: Modem quang ×5 cái").
        // Cột contractproducts không lưu tên, nên không gán ở đây thì DAO phải
        // quay lại bảng products tra một lần nữa thứ mà chỗ này vừa đọc xong.
        item.setProductName(product.getProductName());
        item.setQuantity(quantity);
        item.setUnit(isBlank(unit) ? "Cái" : unit.trim());
        item.setNotes(emptyToNull(notes));

        if (!contractDAO.insertProducts(contractId, List.of(item), actorId(request))) {
            LOG.warn("Them san pham vao hop dong that bai (actor={}, contractId={}, productId={})",
                    Logs.actor(request), contractId, item.getProductId());
            response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + contractId + "&error=add_product_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + contractId);
    }

    /** Gỡ 1 dòng sản phẩm/dịch vụ khỏi hợp đồng -- nút "Xoá" từng dòng ở viewcontractdetail.jsp. */
    private void handleRemoveProduct(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer contractId = parseIntOrNull(request.getParameter("contractId"));
        Integer contractProductId = parseIntOrNull(request.getParameter("contractProductId"));
        if (contractId == null || contractProductId == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }

        if (!contractDAO.deleteProductLine(contractProductId, contractId, actorId(request))) {
            LOG.warn("Xoa san pham khoi hop dong that bai (actor={}, contractId={}, contractProductId={})",
                    Logs.actor(request), contractId, contractProductId);
            response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + contractId + "&error=remove_product_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + contractId);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * user_id người đang thao tác, để ghi vào nhật ký hợp đồng.
     *
     * <p>Trả -1 khi không xác định được phiên. Mọi đường ghi đều đã qua
     * requireFullAccess/requireAdmin nên chuyện đó không xảy ra trong đường
     * chạy bình thường; nếu có thì -1 vi phạm khoá ngoại contract_history
     * .changed_by và cả transaction bị rollback. Cố ý để hỏng to như vậy: một
     * thay đổi không biết ai làm thì thà đừng ghi còn hơn ghi vào nhật ký một
     * dòng không truy được về người nào.
     */
    private int actorId(HttpServletRequest request) {
        User user = AccessControl.currentUser(request);
        return user == null ? -1 : user.getUserId();
    }

    /**
     * @param keepUserId người đang phụ trách bản ghi đang sửa -- giữ trong
     *        dropdown kể cả khi họ đã đổi vai, xem EmployeeDAO.findActiveByRole.
     */
    /**
     * Đổi tham số {@code kind} trên URL thành chiều hợp đồng.
     *
     * <p>Chỉ "buy" mới ra hợp đồng mua; mọi giá trị khác -- kể cả thiếu hẳn --
     * đều ra hợp đồng bán. Đó là chiều duy nhất hệ thống từng có trước V21,
     * nên link cũ và bookmark cũ vẫn mở đúng danh sách như trước.
     */
    private String directionFromKind(String kind) {
        return "buy".equals(kind) ? DIRECTION_BUY : DIRECTION_SELL;
    }

    /** Vai mà đối tác phải giữ để ký được hợp đồng chiều này -- cặp đôi CHÉO. */
    private String counterpartyRoleFor(String direction) {
        return DIRECTION_BUY.equals(direction) ? ROLE_SUPPLIER : ROLE_BUYER;
    }

    /**
     * Khách hàng đứng tên có đúng vai cho chiều này không.
     *
     * <p>Lọc ô chọn ở JSP chỉ là khoá hình: ai mở devtools cũng POST được một
     * enterprise_id bất kỳ. Không kiểm ở đây thì hợp đồng BÁN gắn được vào
     * nhà cung cấp, và khách đó xuất hiện ở danh sách hợp đồng bán trong khi
     * không hề giữ vai khách mua -- dữ liệu tự mâu thuẫn, không có gì báo.
     *
     * <p>CSDL không ép được luật này: nó nói về sự tồn tại của dòng ở
     * enterprise_roles, CHECK không với tới.
     */
    private boolean counterpartyMatchesDirection(Contract c) {
        if (c.getEnterpriseId() <= 0) {
            return false;
        }
        return customerDAO.findRolesOf(c.getEnterpriseId())
                .contains(counterpartyRoleFor(c.getDirection()));
    }

    private void setDropdownAttributes(HttpServletRequest request, Integer keepUserId) {
        setDropdownAttributes(request, keepUserId, DIRECTION_SELL, null);
    }

    /**
     * @param direction    chiều của hợp đồng đang tạo/sửa -- quyết định ô
     *                     "Khách hàng" liệt kê ai (cặp đôi CHÉO: hợp đồng BÁN
     *                     chỉ chọn được khách giữ vai 'Khách mua')
     * @param keepCustomerId khách đang đứng tên hợp đồng đang sửa -- giữ trong
     *                     ô kể cả khi vai của họ đã bị gỡ, nếu không thì mở
     *                     form sửa lên ô trống rồi bấm lưu là đổi mất khách
     *                     hàng dù người dùng chỉ định sửa ngày kết thúc
     */
    private void setDropdownAttributes(HttpServletRequest request, Integer keepUserId,
            String direction, Integer keepCustomerId) {
        request.setAttribute("customerList", customerDAO.findAllByRole(
                counterpartyRoleFor(direction), keepCustomerId));
        // Hợp đồng giao cho Sales, không đổ cả Admin/Kỹ thuật/CSKH vào ô
        // "người phụ trách".
        request.setAttribute("userList", employeeDAO.findActiveByRole(SALES_ROLE, keepUserId));
    }

    /**
     * Đổi link Drive dạng {@code .../file/d/<id>/view} sang {@code /preview} --
     * bản duy nhất Google cho phép nhúng vào iframe. Trả null nếu không nhận ra
     * là link file Drive.
     *
     * Cố ý chỉ nhúng link Drive: mọi site khác đều có thể tự chặn bị nhúng bằng
     * X-Frame-Options, và một khung trắng không lời giải thích thì tệ hơn hẳn
     * một cái link bấm được. Link lạ vẫn hiện nút "Mở PDF" như thường.
     */
    private String drivePreviewUrl(String url) {
        if (url == null) {
            return null;
        }
        java.util.regex.Matcher m = DRIVE_FILE_ID.matcher(url);
        return m.find() ? "https://drive.google.com/file/d/" + m.group(1) + "/preview" : null;
    }


    /**
     * Danh sách hợp đồng của 1 khách hàng, trả về JSON cho dropdown "Hợp đồng
     * liên quan" ở form phiếu hỗ trợ (addnewTicket.jsp/updateTicket.jsp nạp
     * qua AJAX sau khi chọn khách hàng).
     *
     * Tự dựng JSON thay vì dùng Jackson: lib/ chỉ có jackson-databind, thiếu
     * jackson-core/jackson-annotations nên ObjectMapper không chạy được.
     */
    private void listByEnterpriseAsJson(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/json;charset=UTF-8");

        Integer enterpriseId = parseIntOrNull(request.getParameter("enterpriseId"));
        if (enterpriseId == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (java.io.PrintWriter out = response.getWriter()) {
                out.write("[]");
            }
            return;
        }

        List<Contract> contracts = contractDAO.findByEnterpriseId(enterpriseId);
        try (java.io.PrintWriter out = response.getWriter()) {
            out.write(toJsonArray(contracts));
        }
    }

    private String toJsonArray(List<Contract> contracts) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < contracts.size(); i++) {
            Contract c = contracts.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":").append(c.getContractId())
              .append(",\"code\":\"").append(escapeJson(c.getContractCode())).append("\"")
              .append(",\"title\":\"").append(escapeJson(c.getTitle())).append("\"}");
        }
        return sb.append(']').toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Chiều KHÔNG đọc từ request ở đây -- bên gọi tự đặt. Lúc tạo thì lấy từ
     * tham số kind (người dùng đang đứng ở mục con nào); lúc sửa thì giữ
     * nguyên chiều cũ của hợp đồng. Nhận từ form là mở đường cho một request
     * nặn tay đổi hợp đồng bán thành hợp đồng mua, và con số doanh thu đã báo
     * cáo lặng lẽ đổi nghĩa.
     */
    private Contract buildContractFromRequest(HttpServletRequest request, Contract c) {
        c.setTitle(emptyToNull(request.getParameter("title")));
        c.setContractType(emptyToNull(request.getParameter("contractType")));
        c.setSigningDate(parseDateOrNull(request.getParameter("signDate")));
        c.setEffectiveDate(parseDateOrNull(request.getParameter("effectiveDate")));
        c.setEndDate(parseDateOrNull(request.getParameter("endDate")));

        Integer enterpriseId = parseIntOrNull(request.getParameter("enterpriseId"));
        if (enterpriseId != null) {
            c.setEnterpriseId(enterpriseId);
        }
        Integer ownerId = parseIntOrNull(request.getParameter("ownerId"));
        if (ownerId != null) {
            c.setOwnerId(ownerId);
        }
        // Link tới bản PDF đã ký, thường là file trên Google Drive -- người
        // dùng tự tải lên rồi dán link vào đây (giống cách catalogue sản phẩm
        // đang lưu link Drive). Hệ thống không đụng tới file đó.
        c.setAttachmentUrl(emptyToNull(request.getParameter("attachmentUrl")));
        return c;
    }

    /** BR-44: các trường bắt buộc phải có, và Ngày ký ≤ Ngày hiệu lực ≤ Ngày kết thúc. */
    private boolean isValid(Contract c) {
        if (c.getTitle() == null || c.getContractType() == null
                || c.getSigningDate() == null || c.getEffectiveDate() == null || c.getEndDate() == null
                || c.getEnterpriseId() <= 0 || c.getOwnerId() <= 0) {
            return false;
        }
        return !c.getSigningDate().after(c.getEffectiveDate()) && !c.getEffectiveDate().after(c.getEndDate());
    }

    /**
     * Tỉnh/thành của hợp đồng = tỉnh của khách hàng đứng tên (ContractDAO đã
     * gắn sẵn khi map). Bỏ tiền tố "Tỉnh "/"Thành phố " cho khớp thứ tự đã sắp
     * ở DAO; hợp đồng của khách chưa có địa chỉ ghi "Chưa xác định" thay vì để
     * trống, để người đọc file phân biệt được thiếu dữ liệu với lỗi xuất.
     */
    private static String provinceNameOf(Contract contract) {
        Enterprise enterprise = contract.getEnterprise();
        Address address = enterprise != null ? enterprise.getAddress() : null;
        District district = address != null ? address.getDistrict() : null;
        Province province = district != null ? district.getProvince() : null;
        return province != null ? province.getShortName() : "Chưa xác định";
    }

    /**
     * Đổ các giá trị bộ lọc kỳ ra JSP: danh sách năm cho dropdown, giá trị đang
     * chọn (để giữ lại khi submit/phân trang) và nhãn tiếng Việt của kỳ.
     */
    static void setPeriodAttributes(HttpServletRequest request, Period period) {
        request.setAttribute("yearList", Period.availableYears());
        // Đổ ra năm ĐÃ PHÂN TÍCH (số), không phải chuỗi thô trên URL: JSP so
        // ${yearFilter == y} với từng số năm trong dropdown, mà EL gặp chuỗi
        // không phải số thì ném luôn ELException lúc ép kiểu -- gõ tay
        // ?year=abcd là cả trang danh sách trắng thành trang lỗi. Không phân
        // tích được thì coi như không lọc, đúng như Period.parse đã làm.
        request.setAttribute("yearFilter", period != null ? period.getYear() : null);
        request.setAttribute("periodFilter", request.getParameter("period"));
        request.setAttribute("periodLabel", period != null ? period.getLabel() : null);
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Như parseIntOrNull, nhưng dùng riêng cho số lượng người dùng tự gõ vào
     * field PDF -- bỏ dấu "." phân tách hàng nghìn kiểu Việt Nam trước khi
     * parse (vd "1.000" -> 1000), tránh bị Integer.parseInt từ chối số lượng
     * hợp lệ chỉ vì người dùng gõ theo thói quen. Chỉ chấp nhận dấu "." khi
     * đúng vị trí ngăn cách hàng nghìn (mỗi nhóm sau dấu "." có đúng 3 chữ
     * số) -- một số thập phân thật như "2.5" phải bị từ chối chứ không được
     * âm thầm hiểu nhầm thành "25".
     */
    private Integer parseQuantityOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.matches("\\d+(\\.\\d{3})*")) {
            return null;
        }
        return parseIntOrNull(trimmed.replace(".", ""));
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        Integer parsed = parseIntOrNull(value);
        return parsed != null ? parsed : defaultValue;
    }

    private Date parseDateOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Date.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String emptyToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }
}
