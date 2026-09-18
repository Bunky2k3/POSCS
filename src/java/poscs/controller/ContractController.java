package poscs.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
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
import poscs.common.ListScope;
import poscs.common.ExcelUtil;
import poscs.common.Logs;
import poscs.common.PdfUtil;
import poscs.common.Period;
import poscs.common.QueryStrings;
import poscs.common.TextRules;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.ProductDAO;
import poscs.model.Address;
import poscs.model.Contract;
import poscs.model.ContractHandover;
import poscs.model.ContractHistory;
import poscs.model.ContractLink;
import poscs.model.ContractPayment;
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

    /** Giá trị tham số "month" để bỏ cửa sổ tháng mặc định. */
    private static final String MONTH_ALL = "all";

    /** Tháng đang chạy, quy về khoảng ngày bằng chính {@link Period} của bộ lọc kỳ. */
    private static Period currentMonth() {
        LocalDate today = LocalDate.now();
        return Period.parse(String.valueOf(today.getYear()), "m" + today.getMonthValue());
    }

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

    /**
     * Loại hợp đồng, TÁCH THEO CHIỀU.
     *
     * <p>Bộ cũ -- "Cung cấp thiết bị", "Thi công lắp đặt", "Bảo trì bảo dưỡng"
     * -- viết từ góc nhìn người BÁN. Gắn nguyên bộ đó lên một hợp đồng MUA thì
     * câu chữ nói ngược: mình đi mua thiết bị chứ không "cung cấp" cho ai, và
     * mình thuê người thi công chứ không đi thi công.
     *
     * <p>Gom về đây thay vì chép cứng trong JSP: trước đó cùng ba lựa chọn nằm
     * ở listcontract, addnewcontract và updatecontract.
     */
    private static final java.util.List<String> SELL_CONTRACT_TYPES = java.util.List.of(
            "Cung cấp thiết bị", "Thi công lắp đặt", "Bảo trì bảo dưỡng");

    private static final java.util.List<String> BUY_CONTRACT_TYPES = java.util.List.of(
            "Mua thiết bị", "Mua vật tư", "Thuê thi công lắp đặt", "Thuê bảo trì");

    private static java.util.List<String> contractTypesFor(String direction) {
        return DIRECTION_BUY.equals(direction) ? BUY_CONTRACT_TYPES : SELL_CONTRACT_TYPES;
    }

    private static final String LIST_VIEW = "/jsp/sale/listcontract.jsp";
    private static final String DETAIL_VIEW = "/jsp/sale/viewcontractdetail.jsp";
    private static final String CREATE_VIEW = "/jsp/sale/addnewcontract.jsp";
    private static final String UPDATE_VIEW = "/jsp/sale/updatecontract.jsp";
    private static final String HANDOVER_VIEW = "/jsp/sale/handoverqueue.jsp";
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
            case "newAmendment":
                showAmendmentForm(request, response);
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
            case "handovers":
                showHandoverQueue(request, response);
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
            case "createAmendment":
                handleCreateAmendment(request, response);
                break;
            case "correct":
                handleCorrect(request, response);
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
            case "changeProgress":
                handleChangeProgress(request, response);
                break;
            case "addPayment":
                handleAddPayment(request, response);
                break;
            case "markPaid":
                handleMarkPaid(request, response);
                break;
            case "removePayment":
                handleRemovePayment(request, response);
                break;
            case "handOver":
                handleHandOver(request, response);
                break;
            case "completeHandover":
                handleCompleteHandover(request, response);
                break;
            case "linkContract":
                handleLinkContract(request, response);
                break;
            case "unlinkContract":
                handleUnlinkContract(request, response);
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
        // Hợp đồng MUA không lọc theo tỉnh. Tỉnh của hợp đồng suy ra từ địa chỉ
        // đối tác đứng tên, mà với hợp đồng mua thì đối tác là NHÀ CUNG CẤP --
        // nhóm không chia theo địa bàn (xem CustomerController). Lọc theo tỉnh
        // ở đó vừa vô nghĩa vừa cắt mất kết quả.
        //
        // Bỏ ở controller chứ không chỉ ẩn ô chọn: một provinceId còn sót trên
        // URL vẫn âm thầm thu hẹp danh sách.
        if (DIRECTION_BUY.equals(direction)) {
            provinceFilter = null;
        }
        // Trục tiến độ: độc lập với trục lịch (status) và với kỳ. Một hợp đồng
        // "Đã hết hạn" theo lịch mà vẫn "Đã ký" theo tiến độ chính là việc còn
        // tồn -- lọc được hai trục riêng thì mới nhìn ra chỗ đó.
        String progressFilter = request.getParameter("progress");
        // Phụ lục mặc định HIỆN thành dòng riêng: chính nó cũng phải được ký,
        // nên phải tìm thấy được bằng mã. Ô này để thu về danh sách hợp đồng
        // gốc khi người dùng muốn đếm "bao nhiêu hợp đồng" theo nghĩa thường.
        boolean rootsOnly = "root".equals(request.getParameter("scope"));
        // "Đang chờ ở phòng nào" -- câu hỏi của giám đốc, hỏi ngay trên danh
        // sách hợp đồng chứ không phải mở từng hợp đồng ra xem.
        Integer waitingDepartment = parseIntOrNull(request.getParameter("waitingDept"));

        // ===== Phạm vi mặc định của màn hình =====
        // Hai chiều, đều là MẶC ĐỊNH chứ không phải rào quyền:
        //   - ai: Sales thấy hợp đồng mình phụ trách HOẶC thuộc địa bàn mình giữ
        //   - khi nào: hợp đồng CÒN HIỆU LỰC trong tháng hiện tại
        //
        // Cửa sổ tháng áp cho MỌI vai (kể cả Admin): nó trả lời "tháng này đang làm
        // gì", không phải "được phép thấy gì". Bấm "mọi thời điểm" là bỏ.
        //
        // KHÁC bộ lọc "kỳ" ngay bên dưới: kỳ lọc theo NGÀY KÝ nên bản nháp rơi ra
        // ngoài, cái này lọc theo THỜI HẠN nên những gì đang chạy đều ở lại.
        boolean allTime = MONTH_ALL.equals(request.getParameter("month"));
        Period activeWindow = allTime ? null : currentMonth();
        ListScope scope = AccessControl.listScope(request,
                () -> employeeDAO.findTeamUserIds(AccessControl.currentUser(request).getUserId()),
                () -> employeeDAO.findProvincesOf(AccessControl.currentUser(request).getUserId())
                        .stream().map(Province::getProvinceId).collect(Collectors.toList()))
                .withActiveWindow(activeWindow);
        request.setAttribute("viewFilter", AccessControl.defaultView(request));
        request.setAttribute("viewNarrowed", scope.isNarrowed());
        request.setAttribute("viewProvinceCount", scope.getProvinceIds().size());
        request.setAttribute("monthFilter", allTime ? MONTH_ALL : null);
        request.setAttribute("monthLabel", activeWindow != null ? activeWindow.getLabel() : null);
        request.setAttribute("viewToggleUrl", QueryStrings.with(request, "view",
                AccessControl.VIEW_MINE.equals(AccessControl.defaultView(request))
                        ? AccessControl.VIEW_ALL : AccessControl.VIEW_MINE));
        request.setAttribute("monthToggleUrl", QueryStrings.with(request, "month", allTime ? null : MONTH_ALL));

        List<Contract> contractList = contractDAO.findAll(page, PAGE_SIZE, keyword, statusFilter, typeFilter,
                provinceFilter, false, period, direction, progressFilter, rootsOnly, waitingDepartment, scope);
        int totalCount = contractDAO.countAll(keyword, statusFilter, typeFilter, provinceFilter, period, direction,
                progressFilter, rootsOnly, waitingDepartment, scope);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));
        // Dải KPI trạng thái phải đếm CÙNG phạm vi với bảng bên dưới: đứng ở
        // Hợp đồng mua mà KPI gộp cả hợp đồng bán thì hai con số cạnh nhau
        // không khớp, và không có gì trên màn hình giải thích vì sao.
        Map<String, Integer> statusSummary = contractDAO.countStatusSummary(provinceFilter, period, direction,
                rootsOnly, null, scope);

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
        request.setAttribute("progressFilter", progressFilter);
        request.setAttribute("scopeFilter", rootsOnly ? "root" : null);
        request.setAttribute("waitingDeptFilter", waitingDepartment);
        request.setAttribute("departmentList", employeeDAO.findAllDepartments());
        // Loại hợp đồng khác nhau theo chiều -- xem SELL_CONTRACT_TYPES /
        // BUY_CONTRACT_TYPES. JSP dựng dropdown từ đây thay vì chép cứng.
        request.setAttribute("contractTypeOptions", contractTypesFor(direction));
        request.setAttribute("showProvinceFilter", !DIRECTION_BUY.equals(direction));
        request.setAttribute("typeFilter", typeFilter);
        request.setAttribute("provinceFilter", provinceFilter);
        request.setAttribute("kind", DIRECTION_BUY.equals(direction) ? "buy" : "sell");
        request.setAttribute("contractTypeOptions", contractTypesFor(direction));
        setPeriodAttributes(request, period);

        // Bốn ô số ở đầu trang là ĐƯỜNG LỌC theo trạng thái lịch, không còn là
        // một dải chỉ để nhìn -- nên ô chọn "Tất cả trạng thái" đã bỏ khỏi thanh
        // lọc. Link của từng ô dựng ở đây thay vì ghép chuỗi trong JSP: ghép ở
        // đó nghĩa là cùng một danh sách tham số được chép lại ở bốn chỗ, và
        // chỗ nào quên một tham số thì bấm vào là mất bộ lọc đang bật.
        String kindParam = DIRECTION_BUY.equals(direction) ? "buy" : "sell";
        FilterState state = new FilterState(kindParam, keyword, statusFilter, progressFilter,
                rootsOnly ? "root" : null, typeFilter, provinceFilter,
                request.getParameter("year"), request.getParameter("period"), waitingDepartment);
        List<FilterChip> statusChips = new ArrayList<>();
        statusChips.add(state.statusChip(ContractDAO.STATUS_ACTIVE, "Đang hiệu lực", "var(--success)",
                countOf(statusSummary, ContractDAO.STATUS_ACTIVE)));
        statusChips.add(state.statusChip(ContractDAO.STATUS_SOON, "Sắp hết hạn (≤30 ngày)", "var(--warning)",
                countOf(statusSummary, ContractDAO.STATUS_SOON)));
        statusChips.add(state.statusChip(ContractDAO.STATUS_EXPIRED, "Đã hết hạn", "var(--danger)",
                countOf(statusSummary, ContractDAO.STATUS_EXPIRED)));
        statusChips.add(state.statusChip(ContractDAO.STATUS_DRAFT, "Chưa hiệu lực", "#9ca3af",
                countOf(statusSummary, ContractDAO.STATUS_DRAFT)));
        request.setAttribute("statusChips", statusChips);
        request.setAttribute("activeFilters", state.activeFilters());
        // Số lọc đang bật trong khối "Lọc thêm" -- vừa là con số trên nút, vừa là
        // điều kiện để khối đó mở sẵn. Một bộ lọc đang thu hẹp kết quả mà bị giấu
        // sau một cái nút đóng thì người dùng không có cách nào biết.
        request.setAttribute("advancedFilterCount", state.advancedCount());

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
        // TRANG XEM KHÔNG ĐẶT MỘT CỜ can* NÀO. Mọi thao tác làm thay đổi
        // dữ liệu đã chuyển sang trang Sửa thông tin; ở đây chỉ trình bày.
        putContractWorkspace(request, contract);

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
        // Hợp đồng MUA không lọc theo tỉnh. Tỉnh của hợp đồng suy ra từ địa chỉ
        // đối tác đứng tên, mà với hợp đồng mua thì đối tác là NHÀ CUNG CẤP --
        // nhóm không chia theo địa bàn (xem CustomerController). Lọc theo tỉnh
        // ở đó vừa vô nghĩa vừa cắt mất kết quả.
        //
        // Bỏ ở controller chứ không chỉ ẩn ô chọn: một provinceId còn sót trên
        // URL vẫn âm thầm thu hẹp danh sách.
        if (DIRECTION_BUY.equals(direction)) {
            provinceFilter = null;
        }
        // Ô "Cả phụ lục / Chỉ hợp đồng gốc" phải đi theo sang file: xuất ra một
        // danh sách khác thứ đang nhìn thấy là cách chắc chắn nhất để hai con số
        // trong cùng một cuộc họp không khớp nhau.
        // Xuất ĐÚNG thứ đang nhìn thấy: cùng phạm vi mặc định với danh sách trên
        // màn hình. Thiếu dòng này thì bấm "Xuất Excel" ở màn hình 4 dòng lại ra file
        // 12 dòng, mà người xuất không cách nào biết file sai.
        ListScope scope = AccessControl.listScope(request,
                () -> employeeDAO.findTeamUserIds(AccessControl.currentUser(request).getUserId()),
                () -> employeeDAO.findProvincesOf(AccessControl.currentUser(request).getUserId())
                        .stream().map(Province::getProvinceId).collect(Collectors.toList()))
                .withActiveWindow(MONTH_ALL.equals(request.getParameter("month")) ? null : currentMonth());
        List<Contract> all = contractDAO.findAll(1, Integer.MAX_VALUE, keyword, statusFilter, typeFilter,
                provinceFilter, true, period, direction, request.getParameter("progress"),
                "root".equals(request.getParameter("scope")),
                parseIntOrNull(request.getParameter("waitingDept")), scope);
        // Giữ cột "Mã HĐ" trong file dù danh sách trên màn hình đã bỏ -- xem lý do
        // ở CustomerController.exportExcel: STT chỉ đúng trong phạm vi một file.
        //
        // MỘT cột mã, không hai. V25 từng xuất kèm cột "Số hợp đồng" riêng, hồi
        // mã là định danh máy sinh còn số là thứ in trên giấy; V28 chốt lại rằng
        // hai thứ đó là một và bỏ cột contract_number, nên cột thứ hai ở đây
        // không còn gì để đọc.
        String[] headers = {"STT", "Mã HĐ", "Phụ lục của", "Tiêu đề", "Loại HĐ", "Tỉnh/Thành phố", "Khách hàng",
            "Người phụ trách", "Ngày ký", "Ngày hiệu lực", "Ngày kết thúc", "Trạng thái", "Tiến độ"};
        List<Object[]> rows = new ArrayList<>();
        int stt = 1;
        for (Contract c : all) {
            rows.add(new Object[]{
                stt++,
                c.getContractCode(),
                // Rỗng ở hợp đồng gốc. Có cột này thì đọc file cũng phân biệt
                // được phụ lục với hợp đồng, y như nhãn trên màn hình.
                c.getParentContractCode() != null ? c.getParentContractCode() : "",
                c.getTitle(),
                c.getContractType(),
                provinceNameOf(c),
                c.getEnterprise() != null ? c.getEnterprise().getEnterpriseName() : "",
                c.getOwner() != null ? c.getOwner().getFullName() : "",
                c.getSigningDate() != null ? c.getSigningDate().toString() : "",
                c.getEffectiveDate() != null ? c.getEffectiveDate().toString() : "",
                c.getEndDate() != null ? c.getEndDate().toString() : "",
                c.getStatus(),
                c.getProgressStatus()
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
            // Từ V28 mã KHÔNG còn sinh tự động, nên thiếu mã trong file PDF là
            // lỗi phải báo -- trước đó chỗ này lặng lẽ sinh hộ một mã HD-xxxx.
            if (isBlank(contractCode)) {
                errors.add("Thiếu \"Mã hợp đồng\" trong file PDF.");
            } else if (contractDAO.contractCodeExists(contractCode.trim(), 0)) {
                // Cố ý KHÔNG lọc chiều: mã hợp đồng là duy nhất trên toàn bảng,
                // tra theo một chiều là bỏ sót trùng ở chiều kia.
                errors.add("Mã hợp đồng \"" + contractCode.trim() + "\" đã tồn tại.");
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
            // Mã lấy từ chính file PDF. Thiếu thì BÁO LỖI chứ không sinh hộ:
            // từ V28 mã là số hợp đồng thật, hệ thống không có quyền bịa ra.
            contract.setContractCode(isBlank(contractCode) ? null : contractCode.trim());
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

    // static: FilterState (lớp lồng static) cũng dùng chung phép kiểm này.
    private static boolean isBlank(String value) {
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
        request.setAttribute("contractTypeOptions", contractTypesFor(direction));
        request.getRequestDispatcher(CREATE_VIEW).forward(request, response);
    }

    /**
     * Form lập PHỤ LỤC cho một hợp đồng đã ký -- dùng lại chính trang tạo hợp
     * đồng, chỉ khác vài thuộc tính.
     *
     * <p>Dùng lại chứ không dựng trang thứ hai, vì phụ lục là một hợp đồng đầy
     * đủ: cũng mã trên giấy, cũng thời hạn, cũng hàng hoá, cũng phải ký. Trang
     * riêng sẽ là bản sao của trang này, rồi hai bản sao lệch nhau dần.
     *
     * <p>Khác biệt duy nhất: khách hàng và chiều KHÔNG cho chọn -- chúng lấy từ
     * hợp đồng cha. Form ẩn hai ô đó đi, còn chốt chặn thật nằm ở
     * ContractDAO.insert (nó đọc hai giá trị ấy từ cha, không từ form).
     */
    private void showAmendmentForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer parentId = parseIntOrNull(request.getParameter("parentId"));
        Contract parent = parentId != null ? contractDAO.findById(parentId) : null;
        if (parent == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }
        // Kiểm lại ở đây để BÁO ĐÚNG LÝ DO thay vì đưa người dùng vào một form
        // mà bấm Lưu chắc chắn thất bại. Chặn thật vẫn ở DAO, trong transaction.
        if (!parent.isSigned() || parent.isAmendment()) {
            response.sendRedirect(request.getContextPath()
                    + "/contract?action=view&id=" + parentId + "&error=amendment_not_allowed");
            return;
        }

        request.setAttribute("parentContract", parent);
        // Người phụ trách gợi ý theo cha, nhưng sửa được: phụ lục có thể do
        // người khác theo, nhất là khi nhân sự đã đổi từ lúc ký hợp đồng gốc.
        setDropdownAttributes(request, parent.getOwnerId(), parent.getDirection(), parent.getEnterpriseId());
        request.setAttribute("direction", parent.getDirection());
        request.setAttribute("kind", DIRECTION_BUY.equals(parent.getDirection()) ? "buy" : "sell");
        request.setAttribute("contractTypeOptions", contractTypesFor(parent.getDirection()));
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
        request.setAttribute("contractTypeOptions", contractTypesFor(contract.getDirection()));

        // Đây mới là trang có nút bấm. Trang xem dùng cùng dữ liệu này nhưng
        // không đặt cờ nào, nên không mọc nút.
        putContractWorkspace(request, contract);
        request.setAttribute("productOptions", productDAO.findAll(1, Integer.MAX_VALUE, null, null));

        // Hàng hoá là NỘI DUNG hợp đồng: ký xong là chốt, đổi phải đi qua phụ lục.
        request.setAttribute("canEditProducts", contract.isDraft());
        // Lịch thu tiền thì khác -- nó là thứ theo dõi trong lúc thực hiện, nên
        // sửa được cho tới khi hợp đồng đóng băng.
        request.setAttribute("canEditPayments", !contract.isFrozen());
        // Còn ghi nhận tiền về thì kể cả sau thanh lý cũng phải được: tiền bảo
        // hành giữ lại thường chỉ về sau thanh lý cả năm.
        request.setAttribute("canRecordPayment", true);

        request.setAttribute("canSign", contract.isDraft() && canSign(request));
        request.setAttribute("canClose",
                ContractDAO.PROGRESS_SIGNED.equals(contract.getProgressStatus()));
        // Còn phụ lục thì không huỷ được bản ghi cha -- phụ lục sẽ thành văn
        // bản sửa đổi không tra ngược được nó sửa cho cái gì. Chặn thật nằm ở
        // ContractDAO.voidRecord, ở đây chỉ để không mọc ra một cái nút chắc
        // chắn thất bại.
        request.setAttribute("canVoid",
                (AccessControl.isAdmin(request) || contract.isDraft()) && contract.getAmendmentCount() == 0);

        // Điều khoản khoá từ lúc ký: form chỉ còn hiển thị chúng, không nhập.
        request.setAttribute("canEditTerms", !contract.isTermsLocked());
        // Đường chữa SAI SÓT NHẬP LIỆU, khác hẳn đường sửa nội dung. Admin, bắt
        // buộc lý do, sinh dòng nhật ký loại riêng. Không mở sau khi đóng băng:
        // luật KH nói "kể cả cấp cao", và Admin không phải ngoại lệ.
        request.setAttribute("canCorrect", AccessControl.isAdmin(request) && contract.isSigned());
        // Lập phụ lục: chỉ từ hợp đồng gốc ĐÃ KÝ và chưa đóng băng.
        request.setAttribute("canAddAmendment", contract.isSigned() && !contract.isAmendment());

        // Cờ đóng chặng bàn giao đặt Ở ĐÂY, không ở putContractWorkspace: nơi
        // đó dùng chung với trang XEM, mà trang xem cố ý không mọc nút ghi nào.
        // Thiếu dòng này thì cái form trong updatecontract.jsp nằm đấy mà không
        // bao giờ hiện ra -- điều kiện luôn sai.
        for (ContractHandover h : contractDAO.findHandoversOf(contract.getContractId())) {
            if (h.isPending()) {
                request.setAttribute("canCompleteHandover_" + h.getHandoverId(),
                        AccessControl.canCompleteHandover(request, h.getDepartmentId()));
            }
        }

        // Danh sách để chọn khi nối hợp đồng: CHIỀU NGƯỢC LẠI với hợp đồng đang
        // mở, chỉ hợp đồng gốc. Đổ sẵn cả danh sách vì mỗi chiều chỉ vài chục
        // bản ghi; khi nào nhiều lên thì đổi thành ô tìm kiếm AJAX như ô khách
        // hàng, không phải đổi gì ở tầng dưới.
        if (!contract.isAmendment()) {
            String otherDirection = DIRECTION_BUY.equals(contract.getDirection())
                    ? DIRECTION_SELL : DIRECTION_BUY;
            request.setAttribute("linkCandidates", contractDAO.findAll(1, Integer.MAX_VALUE, null, null, null,
                    null, false, null, otherDirection, null, true));
        }

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
        // Kiểm trùng TRƯỚC để báo đúng lý do. Chốt chặn thật vẫn là UNIQUE KEY
        // trên contract_code: hai người lưu cùng lúc cùng một mã thì chỉ ràng
        // buộc ở CSDL mới bắt được, và insert() trả DUPLICATE_CODE.
        if (contractDAO.contractCodeExists(c.getContractCode(), 0)) {
            response.sendRedirect(request.getContextPath()
                    + "/contract?action=new&kind=" + (DIRECTION_BUY.equals(c.getDirection()) ? "buy" : "sell")
                    + "&error=duplicate_code");
            return;
        }

        int newId = contractDAO.insert(c, actorId(request));
        if (newId == ContractDAO.DUPLICATE_CODE) {
            response.sendRedirect(request.getContextPath()
                    + "/contract?action=new&kind=" + (DIRECTION_BUY.equals(c.getDirection()) ? "buy" : "sell")
                    + "&error=duplicate_code");
            return;
        }
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

        Contract existing = contractDAO.findById(id);
        Contract c;
        if (existing.isTermsLocked()) {
            // ĐÃ KÝ: form chỉ còn hai ô nhập được, các trường điều khoản hiển
            // thị dạng chữ và KHÔNG gửi lên. Nên dựng bản ghi từ giá trị đang
            // có rồi chỉ chồng hai ô đó lên -- đọc từ form thì mọi trường khoá
            // về null và isValid() từ chối ngay, tức là đổi người phụ trách
            // cũng không lưu được.
            //
            // DAO vẫn tự lọc lần nữa (ContractDAO.withTermsFrom): chỗ này chỉ
            // lo cho người dùng bình thường, còn một POST nặn tay thì không.
            c = existing;
            Integer ownerId = parseIntOrNull(request.getParameter("ownerId"));
            if (ownerId != null) {
                c.setOwnerId(ownerId);
            }
            c.setAttachmentUrl(emptyToNull(request.getParameter("attachmentUrl")));
        } else {
            c = buildContractFromRequest(request, new Contract());
        }
        c.setContractId(id);
        // Giữ nguyên chiều cũ -- DAO.update cũng không ghi cột này. Nhưng vẫn
        // phải gán để kiểm được khách hàng mới chọn có đúng vai không.
        c.setDirection(existing.getDirection());
        // Ngày ký chép từ bản ghi đang có, KHÔNG lấy từ form (form hiển thị ô
        // đó ở dạng chỉ đọc và không gửi lên): nó là dấu của một hành động đã
        // xảy ra. Thiếu dòng này thì mỗi lần bấm Lưu là xoá mất ngày ký.
        c.setSigningDate(existing.getSigningDate());
        if (!isValid(c) || !counterpartyMatchesDirection(c)) {
            response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + id + "&error=invalid");
            return;
        }
        if (contractDAO.contractCodeExists(c.getContractCode(), id)) {
            response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + id + "&error=duplicate_code");
            return;
        }
        if (!TextRules.isSafeHttpUrl(c.getAttachmentUrl())) {
            response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + id + "&error=invalid_drive_link");
            return;
        }
        // Sửa một phụ lục còn nháp cũng đổi được con số điều chỉnh, nên đường
        // này phải kiểm đúng thứ lúc lập phụ lục đã kiểm. Phụ lục đã ký thì
        // termsLocked, giá trị không đi từ form vào nữa.
        if (existing.isAmendment() && !existing.isTermsLocked()
                && exceedsRemainingValue(contractDAO.findById(existing.getParentContractId()),
                        c.getContractValue())) {
            response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + id
                    + "&error=value_negative");
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
     * Lập một phụ lục cho hợp đồng đã ký.
     *
     * <p>Đi qua chính {@code ContractDAO.insert} như mọi hợp đồng khác, chỉ
     * khác ở chỗ mang theo {@code parentContractId}. Phụ lục ra đời ở trạng
     * thái Nháp và phải được KÝ như hợp đồng thường -- đó là điểm của cả việc
     * này: một sửa đổi trên hợp đồng đã ký phải có chữ ký của người được ký,
     * chứ không phải một lần bấm Lưu trên form.
     *
     * <p>Khách hàng và chiều KHÔNG đọc từ form: DAO lấy thẳng từ hợp đồng cha.
     */
    private void handleCreateAmendment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer parentId = parseIntOrNull(request.getParameter("parentId"));
        Contract parent = parentId != null ? contractDAO.findById(parentId) : null;
        if (parent == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }

        Contract c = buildContractFromRequest(request, new Contract());
        c.setParentContractId(parentId);
        // Hai trường này không có ô trên form phụ lục; gán sẵn để isValid() và
        // counterpartyMatchesDirection() kiểm đúng thứ sẽ được ghi xuống.
        c.setEnterpriseId(parent.getEnterpriseId());
        c.setDirection(parent.getDirection());
        String back = "/contract?action=newAmendment&parentId=" + parentId;

        if (!isValid(c) || !counterpartyMatchesDirection(c)) {
            response.sendRedirect(request.getContextPath() + back + "&error=invalid");
            return;
        }
        if (!TextRules.isSafeHttpUrl(c.getAttachmentUrl())) {
            response.sendRedirect(request.getContextPath() + back + "&error=invalid_drive_link");
            return;
        }
        if (contractDAO.contractCodeExists(c.getContractCode(), 0)) {
            response.sendRedirect(request.getContextPath() + back + "&error=duplicate_code");
            return;
        }
        if (exceedsRemainingValue(parent, c.getContractValue())) {
            response.sendRedirect(request.getContextPath() + back + "&error=value_negative");
            return;
        }

        int newId = contractDAO.insert(c, actorId(request));
        if (newId == ContractDAO.INVALID_VALUE) {
            // Lọt qua phép kiểm bên trên nghĩa là có phụ lục giảm trừ khác vừa
            // được ký xong trong lúc form đang mở.
            response.sendRedirect(request.getContextPath() + back + "&error=value_negative");
            return;
        }
        if (newId == ContractDAO.DUPLICATE_CODE) {
            response.sendRedirect(request.getContextPath() + back + "&error=duplicate_code");
            return;
        }
        if (newId == ContractDAO.INVALID_PARENT) {
            // Hợp đồng cha vừa đổi trạng thái trong lúc form đang mở -- ví dụ
            // vừa được thanh lý. Đưa về trang hợp đồng cha, ở đó người dùng
            // thấy ngay vì sao.
            LOG.warn("Lap phu luc bi tu choi vi hop dong cha (actor={}, parentId={})",
                    Logs.actor(request), parentId);
            response.sendRedirect(request.getContextPath()
                    + "/contract?action=view&id=" + parentId + "&error=amendment_not_allowed");
            return;
        }
        if (newId <= 0) {
            LOG.warn("Lap phu luc that bai (actor={}, parentId={}, contractCode={})",
                    Logs.actor(request), parentId, c.getContractCode());
            response.sendRedirect(request.getContextPath() + back + "&error=create_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + newId);
    }

    /**
     * ADMIN chữa một sai sót NHẬP LIỆU trên hợp đồng đã ký.
     *
     * <p>Không phải cửa sau cho việc sửa nội dung: sửa đổi thật thì đi qua phụ
     * lục và để lại một văn bản có chữ ký. Cái này chỉ chữa thứ gõ sai so với
     * chính bản giấy đang cầm -- nhầm một chữ số trong mã, chọn nhầm khách hàng
     * lúc tạo.
     *
     * <p>Lý do là BẮT BUỘC, và DAO từ chối khi thiếu. Nó vào cột note của dòng
     * nhật ký, tách khỏi câu mô tả do hệ thống sinh, nên về sau còn phân biệt
     * được máy ghi với người khai.
     */
    private void handleCorrect(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // requireAdmin, không phải requireFullAccess: đây là thao tác chạm vào
        // điều khoản của hợp đồng đã ký, hẹp hơn hẳn quyền quản lý hợp đồng.
        if (!AccessControl.requireAdmin(request, response)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("contractId"));
        Contract existing = id != null ? contractDAO.findById(id) : null;
        if (existing == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }
        String back = "/contract?action=edit&id=" + id;

        String reason = request.getParameter("correctionReason");
        if (reason == null || reason.trim().isEmpty()) {
            response.sendRedirect(request.getContextPath() + back + "&error=missing_reason");
            return;
        }

        Contract c = buildContractFromRequest(request, new Contract());
        c.setContractId(id);
        c.setDirection(existing.getDirection());
        // Ngày ký không sửa được kể cả ở đây -- DAO cũng giữ lại giá trị cũ.
        c.setSigningDate(existing.getSigningDate());
        if (!isValid(c) || !counterpartyMatchesDirection(c)) {
            response.sendRedirect(request.getContextPath() + back + "&error=invalid");
            return;
        }
        if (contractDAO.contractCodeExists(c.getContractCode(), id)) {
            response.sendRedirect(request.getContextPath() + back + "&error=duplicate_code");
            return;
        }
        if (!TextRules.isSafeHttpUrl(c.getAttachmentUrl())) {
            response.sendRedirect(request.getContextPath() + back + "&error=invalid_drive_link");
            return;
        }

        if (!contractDAO.correct(c, actorId(request), reason)) {
            LOG.warn("Sua sai sot hop dong that bai (actor={}, contractId={})", Logs.actor(request), id);
            response.sendRedirect(request.getContextPath() + back + "&error=update_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + id);
    }

    /**
     * Một bước trên trục tiến độ: ký, thanh lý, hoặc chấm dứt sớm.
     *
     * <p><b>Ký tách khỏi tạo, và người ký phải khác nhân viên.</b> KH trả lời
     * 2026-09-15: nhân viên không tự ký hợp đồng được. Trước đây
     * {@code signing_date} là NOT NULL nên tạo hợp đồng là đã ký -- không có
     * khoảnh khắc nào hợp đồng tồn tại mà chưa ký, nên cũng không có chỗ nào
     * để chặn. Bây giờ tạo ra bản nháp, còn ký là action riêng ở đây.
     *
     * <p>Điều kiện ký dùng LẠI đúng vị ngữ mà luồng duyệt yêu cầu thay đổi đã
     * dùng cho người duyệt ({@code !currentUser.isSubordinate()}, xem
     * ChangeRequestController): người có cấp trên thì không phải người chốt.
     * Không đẻ ra khái niệm "người được ký" thứ hai -- hệ thống đã có bốn cấu
     * trúc tổ chức song song rồi, thêm cái nữa là thêm chỗ để hai nơi nói hai
     * điều khác nhau về cùng một người.
     *
     * <p>Thanh lý và chấm dứt sớm thì BẮT BUỘC có lý do: cả hai đóng băng hợp
     * đồng vĩnh viễn, không đường quay lại, nên phải biết vì sao. Luật hợp lệ
     * của từng bước chuyển nằm ở ContractDAO.ALLOWED_TRANSITIONS, đúng một chỗ.
     */
    private void handleChangeProgress(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("contractId"));
        if (id == null || contractDAO.findById(id) == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }

        String toStatus = request.getParameter("toStatus");
        if (ContractDAO.PROGRESS_SIGNED.equals(toStatus) && !canSign(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Bạn không được ký hợp đồng. Việc này thuộc về cấp trên.");
            return;
        }

        // Kiểm trước ở đây chỉ để BÁO ĐÚNG LÝ DO. Chặn thật vẫn nằm trong
        // transaction của DAO -- ở đây dữ liệu đã đọc từ trước nên có thể lỗi
        // thời, còn ở đó nó được khoá lại.
        Contract target = contractDAO.findById(id);
        if (ContractDAO.PROGRESS_SIGNED.equals(toStatus)
                && (target.getEffectiveDate() == null || target.getEndDate() == null)) {
            response.sendRedirect(request.getContextPath()
                    + "/contract?action=view&id=" + id + "&error=missing_term");
            return;
        }

        String note = request.getParameter("progressNote");
        if (!contractDAO.changeProgressStatus(id, toStatus, actorId(request), note)) {
            LOG.warn("Chuyen trang thai tien do hop dong that bai (actor={}, contractId={}, toStatus={})",
                    Logs.actor(request), id, toStatus);
            response.sendRedirect(request.getContextPath()
                    + "/contract?action=view&id=" + id + "&error=progress_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + id);
    }

    /**
     * true nếu người đang đăng nhập được KÝ hợp đồng: Admin, hoặc người không
     * có cấp trên trong cây tổ chức (quản lý vùng).
     *
     * <p>Lưu ý về hiện trạng dữ liệu: phần lớn Sales hiện có {@code manager_id}
     * null vì bảng phân công cấp trên chưa nhập, nên họ vẫn ký được. Đó là cố
     * ý và khớp với nguyên tắc đã áp ở AccessControl -- "chưa xếp vào cây thì
     * chưa bị siết" -- để bật tính năng lên không làm đứng việc của ai. Luật KH
     * chỉ thật sự có hiệu lực với từng người khi họ được gán cấp trên.
     */
    private boolean canSign(HttpServletRequest request) {
        User user = AccessControl.currentUser(request);
        return user != null && !user.isSubordinate();
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
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        // Kiểm tồn tại trước: với id không có thật thì voidRecord cũng trả
        // false, và người dùng nhận thông báo "không huỷ được" -- sai hẳn lý
        // do, tưởng là vướng ràng buộc nghiệp vụ.
        Contract target = id == null ? null : contractDAO.findById(id);
        if (target == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }

        // Bản NHÁP thì ai quản được hợp đồng cũng xoá được: nó chưa ký, chưa là
        // chứng cứ gì, xoá một bản nháp sai là việc thường ngày. Đây chính là
        // điều kiện đúng của BR-46 cũ ("chưa ký") -- trước V24 nó không với tới
        // được vì signing_date NOT NULL khiến mọi hợp đồng đều đã ký.
        //
        // Đã ký trở đi thì chỉ Admin, vì lúc đó không còn là xoá nghiệp vụ mà
        // là gỡ một bản ghi nhập nhầm ra khỏi danh sách.
        if (!target.isDraft() && !AccessControl.requireAdmin(request, response)) {
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
            response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId + "&error=add_product_invalid");
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
            response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId + "&error=add_product_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId);
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
            response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId + "&error=remove_product_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Đổ toàn bộ dữ liệu quanh một hợp đồng -- hàng hoá, kỳ thanh toán, nhật
     * ký, các mốc vòng đời, ba con số công nợ.
     *
     * <p>Dùng chung cho TRANG XEM và TRANG SỬA, và cố ý KHÔNG đặt cờ quyền nào:
     * hai trang hiển thị cùng dữ liệu nhưng chỉ trang sửa mới có nút bấm. Đặt
     * cờ ở đây thì trang xem lại mọc nút.
     */
    private void putContractWorkspace(HttpServletRequest request, Contract contract) {
        int id = contract.getContractId();

        List<ContractHistory> history = contractDAO.findHistoryByContractId(id);
        request.setAttribute("contractHistory", history);
        // Ba mốc vòng đời kéo riêng ra khỏi nhật ký để dựng thanh tiến trình.
        // Thanh đó trả lời "đang ở đâu", nhật ký trả lời "đã đi qua những gì".
        request.setAttribute("createdEvent", milestoneOf(history, ContractHistory.EVENT_CREATED));
        request.setAttribute("signedEvent", milestoneOf(history, ContractHistory.EVENT_SIGNED));
        ContractHistory closed = milestoneOf(history, ContractHistory.EVENT_LIQUIDATED);
        if (closed == null) {
            closed = milestoneOf(history, ContractHistory.EVENT_TERMINATED);
        }
        request.setAttribute("closedEvent", closed);

        // Hết hạn theo LỊCH mà tiến độ vẫn "Đã ký" = hết hạn nhưng chưa thanh
        // lý. Đây là việc còn tồn, và là toàn bộ lý do hai trục không gộp làm
        // một -- nên nó hiện thành cảnh báo chứ không để người dùng tự đối
        // chiếu hai cái nhãn.
        request.setAttribute("overdueUnclosed",
                ContractDAO.STATUS_EXPIRED.equals(contract.getStatus())
                        && ContractDAO.PROGRESS_SIGNED.equals(contract.getProgressStatus()));

        request.setAttribute("contractProducts", contractDAO.findProductsByContractId(id));

        // Phụ lục: hiện ở CẢ trang xem lẫn trang quản lý. Trên hợp đồng gốc đây
        // là danh sách các văn bản đã sửa đổi nó; trên chính một phụ lục thì
        // danh sách rỗng (một tầng) và thứ cần thấy là đường ngược về cha, cái
        // đó nằm sẵn trong contract.parentContractCode.
        request.setAttribute("amendments", contractDAO.findAmendmentsByParentId(id));

        List<ContractPayment> payments = contractDAO.findPaymentsByContractId(id);
        request.setAttribute("contractPayments", payments);

        java.math.BigDecimal scheduled = java.math.BigDecimal.ZERO;
        java.math.BigDecimal collected = java.math.BigDecimal.ZERO;
        for (ContractPayment p : payments) {
            scheduled = scheduled.add(p.getInvoiceAmount());
            if (p.getPaidDate() != null) {
                collected = collected.add(p.getInvoiceAmount());
            }
        }
        request.setAttribute("paymentScheduled", scheduled);
        request.setAttribute("paymentCollected", collected);
        request.setAttribute("paymentOutstanding", scheduled.subtract(collected));

        // Đối chiếu tiền làm theo CỤM hợp đồng (bản gốc + các phụ lục), không
        // theo từng bản ghi. Ba ô thống kê ngay trên vẫn là của riêng bản ghi
        // đang mở -- chúng phải khớp cái bảng nằm ngay dưới chúng -- nhưng câu
        // hỏi "đã lập đủ kỳ chưa" thì chỉ trả lời được ở mức cụm: phần bổ sung
        // theo phụ lục thường được lập kỳ ngay trên phụ lục, nên so riêng hợp
        // đồng gốc là cảnh báo nổ ở mọi hợp đồng có phụ lục.
        Contract root = contract.isAmendment()
                ? contractDAO.findById(contract.getParentContractId())
                : contract;
        java.math.BigDecimal clusterValue = root == null ? null : root.getCurrentValue();
        java.math.BigDecimal clusterScheduled = root == null
                ? scheduled
                : contractDAO.sumScheduledPaymentsForCluster(root.getContractId());
        // Không để một truy vấn hỏng làm nổ cả trang chi tiết: DAO trả ZERO khi
        // lỗi, nhưng chỗ so sánh bên dưới thì không được phụ thuộc vào điều đó.
        if (clusterScheduled == null) {
            clusterScheduled = java.math.BigDecimal.ZERO;
        }
        // ===== Bàn giao phòng ban =====
        List<ContractHandover> handovers = contractDAO.findHandoversOf(id);
        request.setAttribute("handovers", handovers);
        boolean pendingHandover = false;
        for (ContractHandover h : handovers) {
            if (h.isPending()) {
                pendingHandover = true;
            }
        }
        // Còn phòng chưa xong thì màn hình CẢNH BÁO chứ không chặn ký: chặn
        // cứng lúc này dễ thành kẹt hơn là kiểm soát, và khách hàng chưa nói
        // đó là điều kiện bắt buộc.
        //
        // KHÔNG đặt cờ "được xác nhận xong" ở đây: trang xem hợp đồng cố ý
        // không mọc một nút ghi nào, và chỗ phòng nhận đóng chặng của mình là
        // màn hình hàng đợi riêng (action=handovers).
        request.setAttribute("hasPendingHandover", pendingHandover);
        request.setAttribute("departmentList", employeeDAO.findAllDepartments());

        // ===== Liên kết bán <-> mua =====
        List<ContractLink> links = contractDAO.findLinksOf(id);
        request.setAttribute("contractLinks", links);
        boolean isSell = !DIRECTION_BUY.equals(contract.getDirection());
        request.setAttribute("linkIsSellSide", isSell);
        // Chỉ hợp đồng GỐC mới nối được: phụ lục là văn bản sửa đổi của một hợp
        // đồng, còn đầu vào thì phục vụ cả hợp đồng đó.
        request.setAttribute("canLinkContracts", !contract.isAmendment());
        if (isSell) {
            // Đối chiếu tiền chỉ có nghĩa ở phía BÁN: giá trị bán ra trừ đi
            // tổng các đơn mua đã nối. Ở phía mua thì một đơn có thể phục vụ
            // nhiều hợp đồng bán, nên trừ kiểu đó ra một con số vô nghĩa.
            java.math.BigDecimal inputValue = contractDAO.sumLinkedBuyValue(id);
            if (inputValue == null) {
                inputValue = java.math.BigDecimal.ZERO;
            }
            request.setAttribute("linkedInputValue", inputValue);
            java.math.BigDecimal outputValue = contract.getCurrentValue();
            request.setAttribute("linkedMargin",
                    outputValue == null ? null : outputValue.subtract(inputValue));
        }

        request.setAttribute("rootContract", root == contract ? null : root);
        request.setAttribute("clusterValue", clusterValue);
        request.setAttribute("clusterScheduled", clusterScheduled);
        // true khi cụm gồm nhiều hơn một bản ghi -- màn hình đổi câu chữ theo
        // nó, vì "tổng các kỳ" lúc đó không còn là con số ngay bên trên nữa.
        request.setAttribute("clusterHasAmendments",
                contract.isAmendment() || contract.getAmendmentCount() > 0);
        // Tổng các kỳ lệch giá trị hợp đồng nghĩa là lập thiếu hoặc lập thừa.
        // CẢNH BÁO chứ không phải lỗi: có thể còn kỳ chưa nhập.
        request.setAttribute("paymentMismatch",
                clusterValue != null && clusterValue.compareTo(clusterScheduled) != 0);
    }

    // ------------------------------------------------------------------
    // Kỳ thanh toán
    // ------------------------------------------------------------------

    /**
     * Hàng đợi bàn giao: các chặng CHƯA XONG của toàn hệ thống, để lâu nhất
     * đứng trước.
     *
     * <p>Một màn hình phục vụ hai người khác nhau, cố ý:
     *
     * <ul>
     *   <li><b>Giám đốc</b> nhìn toàn cảnh -- hợp đồng nào đang nằm ở phòng
     *       nào, bao nhiêu ngày rồi, của nhân viên nào. Đó là câu hỏi sinh ra
     *       cả tính năng này.</li>
     *   <li><b>Phòng nhận</b> (Kế toán, Dự án) thấy đúng phần việc của phòng
     *       mình và đóng chặng ngay tại đây -- họ không có quyền ghi trên hợp
     *       đồng nên trang quản lý hợp đồng không phải chỗ của họ.</li>
     * </ul>
     *
     * <p>KHÔNG đi qua requireFullAccess(CONTRACT): người phòng Kế toán/Dự án
     * phải vào được. Ai cũng xem được hàng đợi -- nó chỉ cho biết hợp đồng đang
     * nằm đâu, còn nút bấm thì lọc theo phòng của từng người.
     */
    private void showHandoverQueue(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        List<ContractHandover> pending = contractDAO.findPendingHandovers(null);
        request.setAttribute("pendingHandovers", pending);

        // Đếm theo phòng để có dải tổng quan: "Kế toán đang giữ 4, Dự án 7".
        Map<String, Integer> byDepartment = new java.util.LinkedHashMap<>();
        long slowest = 0;
        for (ContractHandover h : pending) {
            byDepartment.merge(h.getDepartmentName(), 1, Integer::sum);
            slowest = Math.max(slowest, h.getDaysWaiting());
            request.setAttribute("canCompleteHandover_" + h.getHandoverId(),
                    AccessControl.canCompleteHandover(request, h.getDepartmentId()));
        }
        request.setAttribute("handoverCountByDepartment", byDepartment);
        request.setAttribute("slowestHandoverDays", slowest);
        request.getRequestDispatcher(HANDOVER_VIEW).forward(request, response);
    }

    // ------------------------------------------------------------------
    // Bàn giao hợp đồng giữa các phòng
    // ------------------------------------------------------------------

    /**
     * Bàn giao hợp đồng cho các phòng đã tích trên form (mặc định Kế toán và
     * Dự án, hai phòng của luồng khách hàng mô tả).
     *
     * <p>Người bàn giao là người có quyền ghi trên hợp đồng -- đây là một bước
     * của quy trình soạn thảo, không phải việc của phòng nhận.
     */
    private void handleHandOver(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer contractId = parseIntOrNull(request.getParameter("contractId"));
        if (contractId == null || contractDAO.findById(contractId) == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }
        String back = "/contract?action=edit&id=" + contractId;

        List<Integer> departmentIds = new ArrayList<>();
        String[] raw = request.getParameterValues("departmentId");
        if (raw != null) {
            for (String value : raw) {
                Integer id = parseIntOrNull(value);
                if (id != null) {
                    departmentIds.add(id);
                }
            }
        }
        if (departmentIds.isEmpty()) {
            response.sendRedirect(request.getContextPath() + back + "&error=handover_no_department");
            return;
        }

        int result = contractDAO.handOverToDepartments(contractId, departmentIds,
                request.getParameter("handoverNote"), actorId(request));
        if (result == ContractDAO.HANDOVER_ALREADY_PENDING) {
            response.sendRedirect(request.getContextPath() + back + "&error=handover_pending");
            return;
        }
        if (result == ContractDAO.HANDOVER_FROZEN) {
            response.sendRedirect(request.getContextPath() + back + "&error=handover_frozen");
            return;
        }
        if (result <= 0) {
            LOG.warn("Ban giao hop dong that bai (actor={}, contractId={})", Logs.actor(request), contractId);
            response.sendRedirect(request.getContextPath() + back + "&error=handover_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + back);
    }

    /**
     * Phòng nhận báo đã xử lý xong.
     *
     * <p>KHÔNG đi qua {@code requireFullAccess(CONTRACT)}: người của phòng Kế
     * toán hay Dự án không có quyền ghi hợp đồng, và cũng không nên có -- họ
     * chỉ đóng đúng chặng của mình. Quyền kiểm theo PHÒNG BAN, xem
     * AccessControl.canCompleteHandover.
     */
    private void handleCompleteHandover(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer contractId = parseIntOrNull(request.getParameter("contractId"));
        Integer handoverId = parseIntOrNull(request.getParameter("handoverId"));
        Integer departmentId = parseIntOrNull(request.getParameter("departmentId"));
        if (contractId == null || handoverId == null || departmentId == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }
        if (!AccessControl.canCompleteHandover(request, departmentId)) {
            LOG.warn("Tu choi xac nhan chang ban giao: khong thuoc phong do (actor={}, handoverId={})",
                    Logs.actor(request), handoverId);
            response.sendRedirect(request.getContextPath() + "/error/403.jsp");
            return;
        }
        // Quay lại đúng chỗ vừa bấm: phòng nhận làm việc ở hàng đợi, còn người
        // của Kinh doanh thì bấm từ trang quản lý hợp đồng.
        String back = "from-queue".equals(request.getParameter("returnTo"))
                ? "/contract?action=handovers"
                : "/contract?action=edit&id=" + contractId;
        if (!contractDAO.completeHandover(handoverId, actorId(request), request.getParameter("doneNote"))) {
            response.sendRedirect(request.getContextPath() + back + "&error=handover_done_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + back);
    }

    // ------------------------------------------------------------------
    // Liên kết hợp đồng bán <-> mua ("đầu ra kéo theo đầu vào")
    // ------------------------------------------------------------------

    /**
     * Nối hợp đồng đang mở với một hợp đồng ở chiều ngược lại.
     *
     * <p>Form chỉ gửi lên id hợp đồng kia; BÊN NÀO LÀ BÁN, bên nào là mua thì
     * controller tự xếp theo chiều của hai bản ghi, vì bảng lưu một chiều cố
     * định. Người dùng không phải nhớ thứ tự, và một POST nặn tay cũng không
     * đảo được -- DAO kiểm lại chiều trong transaction.
     */
    private void handleLinkContract(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer contractId = parseIntOrNull(request.getParameter("contractId"));
        Integer otherId = parseIntOrNull(request.getParameter("otherContractId"));
        Contract current = contractId == null ? null : contractDAO.findById(contractId);
        Contract other = otherId == null ? null : contractDAO.findById(otherId);
        if (current == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }
        String back = "/contract?action=edit&id=" + contractId;
        if (other == null) {
            response.sendRedirect(request.getContextPath() + back + "&error=link_invalid");
            return;
        }

        boolean currentIsSell = !DIRECTION_BUY.equals(current.getDirection());
        int sellId = currentIsSell ? current.getContractId() : other.getContractId();
        int buyId = currentIsSell ? other.getContractId() : current.getContractId();

        int result = contractDAO.linkContracts(sellId, buyId, request.getParameter("linkNote"), actorId(request));
        if (result == ContractDAO.LINK_DUPLICATE) {
            response.sendRedirect(request.getContextPath() + back + "&error=link_duplicate");
            return;
        }
        if (result != ContractDAO.LINK_OK) {
            LOG.warn("Noi hop dong that bai (actor={}, contractId={}, otherId={})",
                    Logs.actor(request), contractId, otherId);
            response.sendRedirect(request.getContextPath() + back + "&error=link_invalid");
            return;
        }
        response.sendRedirect(request.getContextPath() + back);
    }

    private void handleUnlinkContract(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer contractId = parseIntOrNull(request.getParameter("contractId"));
        Integer linkId = parseIntOrNull(request.getParameter("linkId"));
        if (contractId == null || linkId == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }
        if (!contractDAO.unlinkContracts(linkId, actorId(request))) {
            LOG.warn("Go lien ket that bai (actor={}, linkId={})", Logs.actor(request), linkId);
            response.sendRedirect(request.getContextPath()
                    + "/contract?action=edit&id=" + contractId + "&error=unlink_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId);
    }

    private void handleAddPayment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer contractId = parseIntOrNull(request.getParameter("contractId"));
        if (contractId == null || contractDAO.findById(contractId) == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }

        ContractPayment payment = new ContractPayment();
        payment.setInvoiceAmount(parseMoneyOrNull(request.getParameter("invoiceAmount")));
        payment.setDueDate(parseDateOrNull(request.getParameter("dueDate")));
        payment.setPaidDate(parseDateOrNull(request.getParameter("paidDate")));

        if (!contractDAO.insertPayment(contractId, payment, actorId(request))) {
            LOG.warn("Lap ky thanh toan that bai (actor={}, contractId={})", Logs.actor(request), contractId);
            response.sendRedirect(request.getContextPath()
                    + "/contract?action=edit&id=" + contractId + "&error=payment_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId);
    }

    private void handleMarkPaid(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer contractId = parseIntOrNull(request.getParameter("contractId"));
        Integer paymentId = parseIntOrNull(request.getParameter("paymentId"));
        if (contractId == null || paymentId == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }
        // Ngày thu mặc định là hôm nay -- phần lớn thao tác là ghi nhận ngay
        // lúc tiền về; ai cần lùi ngày thì điền ô riêng.
        Date paidDate = parseDateOrNull(request.getParameter("paidDate"));
        if (paidDate == null) {
            paidDate = Date.valueOf(java.time.LocalDate.now());
        }

        if (!contractDAO.markPaymentPaid(paymentId, contractId, paidDate, actorId(request))) {
            LOG.warn("Ghi nhan da thu that bai (actor={}, contractId={}, paymentId={})",
                    Logs.actor(request), contractId, paymentId);
            response.sendRedirect(request.getContextPath()
                    + "/contract?action=edit&id=" + contractId + "&error=payment_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId);
    }

    private void handleRemovePayment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer contractId = parseIntOrNull(request.getParameter("contractId"));
        Integer paymentId = parseIntOrNull(request.getParameter("paymentId"));
        if (contractId == null || paymentId == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }

        if (!contractDAO.deletePayment(paymentId, contractId, actorId(request))) {
            LOG.warn("Xoa ky thanh toan that bai (actor={}, contractId={}, paymentId={})",
                    Logs.actor(request), contractId, paymentId);
            response.sendRedirect(request.getContextPath()
                    + "/contract?action=edit&id=" + contractId + "&error=payment_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + contractId);
    }

    /** Dòng nhật ký gần nhất của một loại mốc vòng đời, hoặc null nếu chưa xảy ra. */
    private static ContractHistory milestoneOf(List<ContractHistory> history, String eventType) {
        for (ContractHistory h : history) {
            if (eventType.equals(h.getEventType())) {
                return h;
            }
        }
        return null;
    }

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
        // Mã hợp đồng do NGƯỜI DÙNG nhập (V28), chính là số ghi trên bản giấy.
        // Không còn sinh tự động, nên đây là ô bắt buộc -- isValid() kiểm.
        c.setContractCode(emptyToNull(request.getParameter("contractCode")));
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

        c.setSignerName(emptyToNull(request.getParameter("signerName")));
        c.setSignerPosition(emptyToNull(request.getParameter("signerPosition")));
        c.setCounterpartySignerName(emptyToNull(request.getParameter("counterpartySignerName")));
        c.setCounterpartySignerPosition(emptyToNull(request.getParameter("counterpartySignerPosition")));
        c.setAuthorizationRef(emptyToNull(request.getParameter("authorizationRef")));
        c.setSigningPlace(emptyToNull(request.getParameter("signingPlace")));
        c.setContractValue(parseContractValue(request));
        return c;
    }

    /** Các lựa chọn của ô "Điều chỉnh giá trị" trên form phụ lục. */
    private static final String ADJUST_INCREASE = "increase";
    private static final String ADJUST_DECREASE = "decrease";
    private static final String ADJUST_NONE = "none";

    /**
     * Số tiền ghi trên văn bản đang nhập.
     *
     * <p>Trên form hợp đồng gốc đó là TỔNG giá trị theo điều khoản, đọc thẳng.
     * Trên form phụ lục thì cột {@code contract_value} mang nghĩa CHÊNH LỆCH có
     * dấu, nên form hỏi thêm một ô Tăng/Giảm/Không đổi và dấu được gắn ở đây --
     * bắt người dùng tự gõ dấu trừ vừa dễ nhầm, vừa cho ra một ô tiền mà in lên
     * màn hình thì không ai đọc được là bổ sung hay giảm trừ.
     *
     * <p>Không có tham số {@code valueAdjustment} = đang ở form hợp đồng gốc.
     */
    private java.math.BigDecimal parseContractValue(HttpServletRequest request) {
        java.math.BigDecimal amount = parseMoneyOrNull(request.getParameter("contractValue"));
        String adjustment = request.getParameter("valueAdjustment");
        if (adjustment == null) {
            return amount;
        }
        // "Không đổi" thắng cả ô số: phụ lục chỉ sửa hàng hoá hay gia hạn thì
        // con số còn sót trong ô không được lặng lẽ cộng vào giá trị hợp đồng.
        if (ADJUST_NONE.equals(adjustment) || amount == null) {
            return null;
        }
        return ADJUST_DECREASE.equals(adjustment) ? amount.negate() : amount;
    }

    /**
     * true nếu khoản giảm trừ {@code delta} kéo giá trị hiện hành của hợp đồng
     * {@code parent} xuống dưới 0 -- gần như luôn là gõ nhầm dấu, hoặc gõ TỔNG
     * giá trị mới vào ô chênh lệch.
     *
     * <p>Báo lỗi tử tế ở tầng này; chốt chặn thật nằm ở ContractDAO, nơi hợp
     * đồng cha đã bị khoá trong transaction.
     */
    private boolean exceedsRemainingValue(Contract parent, java.math.BigDecimal delta) {
        if (parent == null || delta == null || delta.signum() >= 0) {
            return false;
        }
        java.math.BigDecimal current = parent.getCurrentValue();
        return (current == null ? java.math.BigDecimal.ZERO : current).add(delta).signum() < 0;
    }

    /** BR-44: các trường bắt buộc phải có, và Ngày ký ≤ Ngày hiệu lực ≤ Ngày kết thúc. */
    /**
     * Đọc số tiền người dùng gõ. Chấp nhận cả "1.500.000.000" lẫn "1500000000"
     * -- người Việt gõ dấu chấm phân nhóm theo thói quen, và bắt họ gõ số trần
     * chỉ tạo ra lỗi nhập liệu chứ không tạo ra dữ liệu sạch hơn.
     *
     * <p>Trả null khi để trống (bản nháp chưa chốt giá) HOẶC khi chuỗi không
     * đọc được -- không ném ra ngoài: một ô tiền gõ sai không đáng làm hỏng cả
     * lần lưu, và isValid() bên dưới sẽ bắt nếu giá trị đó là bắt buộc.
     *
     * <p>Số âm bị từ chối: giá trị hợp đồng âm không có nghĩa, và nếu lọt vào
     * thì nó âm thầm trừ đi trong mọi phép cộng sau này.
     */
    private java.math.BigDecimal parseMoneyOrNull(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        String digits = raw.replaceAll("[.,\\s]", "");
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(digits);
            return value.signum() < 0 ? null : value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isValid(Contract c) {
        if (c.getContractCode() == null || c.getTitle() == null || c.getContractType() == null
                || c.getEnterpriseId() <= 0 || c.getOwnerId() <= 0) {
            return false;
        }
        // Thời hạn có thể TRỐNG ở bản nháp -- hai mốc đó là kết quả đàm phán,
        // lúc mới soạn chưa chốt được. Không ký được khi còn thiếu, nên NULL
        // chỉ sống trong quãng Nháp (xem ContractDAO.changeProgressStatus).
        // Ngày ký có thể TRỐNG: bản nháp chưa ký thì chưa có ngày ký, và ngày
        // đó được đóng dấu lúc bấm Ký (ContractDAO.changeProgressStatus đặt
        // CURDATE()) chứ không phải thứ người dùng gõ vào ô. Khi đã có thì vẫn
        // phải giữ BR-44: ký <= hiệu lực <= kết thúc.
        if (c.getSigningDate() != null && c.getEffectiveDate() != null
                && c.getSigningDate().after(c.getEffectiveDate())) {
            return false;
        }
        // Có cả hai thì vẫn giữ BR-44; thiếu một trong hai thì chưa có gì để so.
        return c.getEffectiveDate() == null || c.getEndDate() == null
                || !c.getEffectiveDate().after(c.getEndDate());
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
     * Số của một ô trạng thái, 0 khi thiếu khoá.
     *
     * <p>DAO luôn trả đủ bốn khoá, kể cả khi truy vấn hỏng -- nhưng một ô KPI
     * không đáng làm cả trang danh sách nổ NullPointerException nếu về sau có
     * đường nào trả về map thiếu.
     */
    private static int countOf(Map<String, Integer> summary, String key) {
        Integer value = summary == null ? null : summary.get(key);
        return value == null ? 0 : value;
    }

    /**
     * Một ô/chip lọc trên màn hình danh sách: nhãn để đọc, {@code query} là
     * đường dẫn bấm vào, và {@code on} cho biết nó đang bật hay không.
     */
    public static final class FilterChip {
        private final String label;
        private final String query;
        private final String color;
        private final boolean on;
        private final int count;

        FilterChip(String label, String query, String color, boolean on, int count) {
            this.label = label;
            this.query = query;
            this.color = color;
            this.on = on;
            this.count = count;
        }

        public String getLabel() { return label; }
        public String getQuery() { return query; }
        public String getColor() { return color; }
        public boolean isOn() { return on; }
        /** Số hợp đồng ở trạng thái này; 0 với chip "đang lọc" (nó không đếm gì). */
        public int getCount() { return count; }
    }

    /**
     * Bộ lọc hiện tại của màn hình danh sách, gom lại để dựng link.
     *
     * <p>Lý do tồn tại: mọi đường bấm trên trang (bốn ô trạng thái, dấu × trên
     * từng chip) đều phải mang theo TẤT CẢ các lọc khác. Ghép chuỗi tại chỗ thì
     * cùng một danh sách tham số bị chép lại ở năm sáu nơi, và chỗ nào thiếu
     * một tham số thì bấm vào là lọc đang bật lặng lẽ biến mất.
     */
    private static final class FilterState {
        private final String kind;
        private final String keyword;
        private final String status;
        private final String progress;
        private final String scope;
        private final String type;
        private final Integer provinceId;
        private final String year;
        private final String period;
        private final Integer waitingDepartmentId;

        FilterState(String kind, String keyword, String status, String progress, String scope,
                String type, Integer provinceId, String year, String period, Integer waitingDepartmentId) {
            this.waitingDepartmentId = waitingDepartmentId;
            this.kind = kind;
            this.keyword = keyword;
            this.status = status;
            this.progress = progress;
            this.scope = scope;
            this.type = type;
            this.provinceId = provinceId;
            this.year = year;
            this.period = period;
        }

        /** Link giữ nguyên mọi lọc khác, chỉ thay đúng một tham số. */
        private String queryWith(String name, String value) {
            StringBuilder sb = new StringBuilder("action=list");
            put(sb, "kind", "kind".equals(name) ? value : kind);
            put(sb, "keyword", "keyword".equals(name) ? value : keyword);
            put(sb, "status", "status".equals(name) ? value : status);
            put(sb, "progress", "progress".equals(name) ? value : progress);
            put(sb, "scope", "scope".equals(name) ? value : scope);
            put(sb, "type", "type".equals(name) ? value : type);
            put(sb, "provinceId", "provinceId".equals(name) ? value
                    : (provinceId == null ? null : String.valueOf(provinceId)));
            put(sb, "year", "year".equals(name) ? value : year);
            // Bỏ năm thì kỳ mất nghĩa -- Period.parse cần cả hai, nên để lại một
            // mình "quý 3" trên URL chỉ tạo ra một chip lọc không lọc gì cả.
            String nextYear = "year".equals(name) ? value : year;
            put(sb, "period", isBlank(nextYear) ? null : ("period".equals(name) ? value : period));
            put(sb, "waitingDept", "waitingDept".equals(name) ? value
                    : (waitingDepartmentId == null ? null : String.valueOf(waitingDepartmentId)));
            return sb.toString();
        }

        private static void put(StringBuilder sb, String name, String value) {
            if (isBlank(value)) {
                return;
            }
            sb.append('&').append(name).append('=')
              .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }

        /**
         * Ô trạng thái lịch. Đang bật thì link của chính nó TẮT lọc đi: người
         * dùng bấm lại ô đang sáng theo phản xạ, và không có nút tắt nào khác.
         */
        FilterChip statusChip(String key, String label, String color, int count) {
            boolean on = key.equals(status);
            // Nhãn hiển thị KHÁC key tra bảng đếm ("Sắp hết hạn (≤30 ngày)" vs
            // "Sắp hết hạn"), nên con số đi kèm ngay trong chip chứ không để JSP
            // tra lại bằng nhãn.
            return new FilterChip(label, queryWith("status", on ? null : key), color, on, count);
        }

        /** Các lọc đang bật, mỗi cái kèm link BỎ chính nó. */
        List<FilterChip> activeFilters() {
            List<FilterChip> chips = new ArrayList<>();
            if (!isBlank(keyword)) {
                chips.add(new FilterChip("Từ khoá: " + keyword, queryWith("keyword", null), null, true, 0));
            }
            if (!isBlank(progress)) {
                chips.add(new FilterChip("Tiến độ: " + progress, queryWith("progress", null), null, true, 0));
            }
            if (!isBlank(scope)) {
                chips.add(new FilterChip("Chỉ hợp đồng gốc", queryWith("scope", null), null, true, 0));
            }
            if (!isBlank(type)) {
                chips.add(new FilterChip("Loại: " + type, queryWith("type", null), null, true, 0));
            }
            if (provinceId != null) {
                chips.add(new FilterChip("Theo tỉnh", queryWith("provinceId", null), null, true, 0));
            }
            if (!isBlank(year)) {
                chips.add(new FilterChip(periodLabel(), queryWith("year", null), null, true, 0));
            }
            if (waitingDepartmentId != null) {
                chips.add(new FilterChip("Đang chờ ở một phòng", queryWith("waitingDept", null), null, true, 0));
            }
            return chips;
        }

        private String periodLabel() {
            Period parsed = Period.parse(year, period);
            return parsed != null ? "Kỳ: " + parsed.getLabel() : "Kỳ: " + year;
        }

        /** Số lọc nằm trong khối "Lọc thêm" đang bật (loại hợp đồng, tỉnh, kỳ). */
        int advancedCount() {
            int n = 0;
            if (!isBlank(type)) { n++; }
            if (provinceId != null) { n++; }
            if (!isBlank(year)) { n++; }
            if (waitingDepartmentId != null) { n++; }
            return n;
        }
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
