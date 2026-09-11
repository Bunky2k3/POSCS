package poscs.controller;

import java.io.IOException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.AccessControl;
import poscs.common.ExcelUtil;
import poscs.common.Logs;
import poscs.common.PdfUtil;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Contract;
import poscs.model.TechnicalRequest;
import poscs.model.User;

/**
 * Controller CRUD cho phiếu hỗ trợ kỹ thuật (bảng technicalrequests). Điều
 * hướng theo tham số "action", theo đúng khuôn mẫu của CustomerController/
 * ContractController -- quyền hạn theo PERMISSIONS.md (CSKH và Admin có
 * toàn quyền, Sales/Kỹ thuật chỉ xem) enforce bằng
 * AccessControl.requireFullAccess ở đầu mỗi hàm handleCreate/handleDelete.
 * Riêng handleUpdate có thêm ngoại lệ: Kỹ thuật được tự đổi trạng thái/ghi
 * chú xử lý của đúng phiếu đang giao cho mình, xem
 * AccessControl.canUpdateAssignedTicket.
 *
 * technicalrequestdevices (thiết bị lỗi) và technicalrequesthistory (lịch
 * sử đổi trạng thái) chưa được xử lý -- thuộc phạm vi khác.
 */
@WebServlet(name = "TechnicalSupportTicketController", urlPatterns = {"/ticket"})
public class TechnicalSupportTicketController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(TechnicalSupportTicketController.class);

    private static final int PAGE_SIZE = 10;
    private static final String LIST_VIEW = "/jsp/customersupport/listTicket.jsp";
    private static final String DETAIL_VIEW = "/jsp/customersupport/viewdetailTicket.jsp";
    private static final String CREATE_VIEW = "/jsp/customersupport/addnewTicket.jsp";
    private static final String UPDATE_VIEW = "/jsp/customersupport/updateTicket.jsp";
    /** Lề (điểm) của trang PDF xuất phiếu -- dùng cho cả 4 phía. */
    private static final float PDF_MARGIN = 56f;

    private final TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    // Dùng để kiểm hợp đồng được chọn có đúng của khách hàng trên phiếu không.
    private final ContractDAO contractDAO = new ContractDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Cho JSP biết người đang xem có quyền Full trên tài nguyên này không,
        // để ẩn các nút hành động không dùng được (Tạo/Sửa/Xoá/Nhập/Xuất) thay
        // vì để người ta bấm vào rồi nhận 403. Đây CHỈ là lớp trình bày --
        // chặn thật nằm ở AccessControl.requireFullAccess trong doPost và ở
        // đầu mỗi trang form bên dưới (gõ thẳng URL cũng không vào được).
        request.setAttribute("canManage",
                AccessControl.hasFullAccess(request, AccessControl.Resource.TICKET));
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
            case "exportPdf":
                exportPdf(request, response);
                break;
            case "exportExcel":
                exportExcel(request, response);
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
            default:
                response.sendRedirect(request.getContextPath() + "/ticket");
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
        String priorityFilter = request.getParameter("priority");

        List<TechnicalRequest> ticketList = ticketDAO.findAll(page, PAGE_SIZE, keyword, statusFilter, priorityFilter);
        int totalCount = ticketDAO.countAll(keyword, statusFilter, priorityFilter);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));

        request.setAttribute("ticketList", ticketList);
        request.setAttribute("statusSummary", ticketDAO.countStatusSummary());
        request.setAttribute("currentPage", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalCount", totalCount);
        request.setAttribute("keyword", keyword);
        request.setAttribute("statusFilter", statusFilter);
        request.setAttribute("priorityFilter", priorityFilter);

        request.getRequestDispatcher(LIST_VIEW).forward(request, response);
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        TechnicalRequest ticket = id != null ? ticketDAO.findById(id) : null;
        if (ticket == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
            return;
        }

        request.setAttribute("ticket", ticket);
        request.setAttribute("canDelete", ticketDAO.canDelete(id));
        request.setAttribute("ticketHistory", ticketDAO.findHistoryByTicketId(id));
        // Nút "Sửa" phải hiện cho cả kỹ thuật viên ĐANG ĐƯỢC GIAO phiếu này --
        // họ không có quyền Full trên Ticket, nhưng vẫn được cập nhật trạng
        // thái/ghi chú xử lý của đúng phiếu của mình (xem PERMISSIONS.md).
        request.setAttribute("canEdit",
                AccessControl.hasFullAccess(request, AccessControl.Resource.TICKET)
                        || AccessControl.canUpdateAssignedTicket(request, ticket));

        request.getRequestDispatcher(DETAIL_VIEW).forward(request, response);
    }

    /**
     * Xuất danh sách phiếu hỗ trợ ra Excel -- nút "Xuất Excel" ở listTicket.jsp.
     *
     * Xuất theo ĐÚNG bộ lọc đang áp trên màn hình (từ khoá/trạng thái/độ ưu
     * tiên) nhưng bỏ phân trang: người dùng lọc ra cái họ cần rồi muốn cả tập
     * đó, không phải đúng 10 dòng của trang đang mở. Cùng cách làm với xuất
     * Excel của khách hàng và hợp đồng.
     *
     * Nhiều cột hơn bảng trên màn hình (thêm kênh tiếp nhận, hạn SLA, thời
     * điểm đóng, bảo hành, mô tả, kết quả xử lý) -- file Excel là để đem đi
     * lọc/thống kê ngoại tuyến, không bị giới hạn bề ngang như bảng HTML.
     */
    private void exportExcel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String keyword = request.getParameter("keyword");
        String statusFilter = request.getParameter("status");
        String priorityFilter = request.getParameter("priority");

        List<TechnicalRequest> all = ticketDAO.findAll(1, Integer.MAX_VALUE, keyword, statusFilter, priorityFilter);
        String[] headers = {"Mã phiếu", "Loại phiếu", "Khách hàng", "Hợp đồng liên quan",
            "Mức ưu tiên", "Kênh tiếp nhận", "Trạng thái", "Người xử lý", "Người tạo",
            "Ngày tạo", "Hạn xử lý (SLA)", "Thời điểm đóng", "Bảo hành",
            "Mô tả sự cố", "Kết quả xử lý"};
        List<Object[]> rows = new ArrayList<>();
        for (TechnicalRequest t : all) {
            rows.add(new Object[]{
                t.getTicketCode(),
                t.getTicketType(),
                t.getEnterprise() != null ? t.getEnterprise().getEnterpriseName() : "",
                t.getContract() != null ? t.getContract().getContractCode() : "",
                t.getPriority(),
                t.getReceptionChannel(),
                t.getStatus(),
                t.getAssignedTechnician() != null ? t.getAssignedTechnician().getFullName() : "",
                t.getCreatedByUser() != null ? t.getCreatedByUser().getFullName() : "",
                formatDate(t.getCreatedDate()),
                formatDateTime(t.getSlaDeadline()),
                formatDateTime(t.getResolvedAt()),
                t.isWarranty() ? "Có" : "Không",
                t.getDescription(),
                t.getResolutionSummary()
            });
        }
        ExcelUtil.writeWorkbook(response, "phieu_ho_tro", headers, rows);
    }

    /**
     * Xuất 1 phiếu hỗ trợ ra PDF để in/gửi khách.
     *
     * Vẽ thẳng bằng PDFBox thay vì điền vào file mẫu như hợp đồng: phiếu hỗ trợ
     * không có mẫu in sẵn nào, và nội dung ở đây chỉ là các cặp nhãn/giá trị
     * cộng 2 đoạn văn -- dựng một AcroForm chỉ để điền vào là công vô ích.
     * Font tiếng Việt lấy từ PdfUtil.loadVietnameseFont (font mặc định của
     * PDFBox không có glyph tiếng Việt, xuất ra sẽ mất dấu hết).
     *
     * Dựng TOÀN BỘ nội dung thành danh sách dòng trước, rồi mới đổ ra trang
     * (xem {@link #renderPaginated}), thay vì vừa tính vừa vẽ. Lý do: "Mô tả
     * sự cố" và "Kết quả xử lý" là cột TEXT, người dùng gõ bao nhiêu cũng
     * được. Cách cũ chỉ tạo đúng 1 trang A4 và cứ trừ dần toạ độ y, nên phần
     * vượt quá chiều cao trang được vẽ ra NGOÀI vùng giấy: PDFBox không báo
     * lỗi gì, file mở lên vẫn bình thường, chỉ là mất hẳn phần cuối mà không
     * ai biết. Tách 2 bước thì việc "hết chỗ -> sang trang mới" chỉ là một
     * phép so sánh, không còn chỗ nào vẽ lố ra ngoài được nữa.
     */
    private void exportPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        TechnicalRequest t = id != null ? ticketDAO.findById(id) : null;
        if (t == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
            return;
        }

        try (PDDocument document = new PDDocument()) {
            PDFont font = PdfUtil.loadVietnameseFont(document, getServletContext());
            float contentWidth = PDRectangle.A4.getWidth() - PDF_MARGIN * 2;

            List<PdfLine> lines = new ArrayList<>();
            lines.add(new PdfLine(17, "PHIẾU HỖ TRỢ KỸ THUẬT", 22));
            lines.add(new PdfLine(11, "Mã phiếu: " + nz(t.getTicketCode()), 26));

            addPair(lines, "Khách hàng",
                    t.getEnterprise() != null ? t.getEnterprise().getEnterpriseName() : "—");
            addPair(lines, "Hợp đồng liên quan",
                    t.getContract() != null ? t.getContract().getContractCode() : "—");
            addPair(lines, "Loại phiếu", t.getTicketType());
            addPair(lines, "Mức ưu tiên", t.getPriority());
            addPair(lines, "Kênh tiếp nhận", t.getReceptionChannel());
            addPair(lines, "Trạng thái", t.getStatus());
            addPair(lines, "Ngày tạo", formatDate(t.getCreatedDate()));
            addPair(lines, "Hạn xử lý (SLA)", formatDateTime(t.getSlaDeadline()));
            addPair(lines, "Kỹ thuật viên phụ trách",
                    t.getAssignedTechnician() != null ? t.getAssignedTechnician().getFullName() : "—");
            addPair(lines, "Thời điểm đóng", formatDateTime(t.getResolvedAt()));

            addParagraph(lines, font, contentWidth, "Mô tả sự cố", t.getDescription());
            addParagraph(lines, font, contentWidth, "Kết quả xử lý", t.getResolutionSummary());

            renderPaginated(document, font, lines);

            response.setContentType("application/pdf");
            String fileName = "phieu_" + nz(t.getTicketCode()).replace("/", "-") + ".pdf";
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            document.save(response.getOutputStream());
        }
    }

    /** 1 dòng chữ đã biết cỡ chữ và khoảng cách xuống dòng kế tiếp, chưa gắn với trang nào. */
    private static final class PdfLine {

        private final float fontSize;
        private final String text;
        private final float leading;

        private PdfLine(float fontSize, String text, float leading) {
            this.fontSize = fontSize;
            this.text = text;
            this.leading = leading;
        }
    }

    /** Thêm 1 dòng "Nhãn: giá trị". */
    private void addPair(List<PdfLine> lines, String label, String value) {
        lines.add(new PdfLine(10, label + ": " + nz(value), 17));
    }

    /** Thêm 1 đoạn văn có tiêu đề, tự bẻ dòng theo bề rộng trang. */
    private void addParagraph(List<PdfLine> lines, PDFont font, float width, String title, String body)
            throws IOException {
        lines.add(new PdfLine(11, title, 16));
        List<String> wrapped = PdfUtil.wrapLines(font, 10, width, nz(body));
        for (int i = 0; i < wrapped.size(); i++) {
            // Dòng cuối của đoạn chừa thêm khoảng trống trước tiêu đề đoạn sau.
            lines.add(new PdfLine(10, wrapped.get(i), i == wrapped.size() - 1 ? 22 : 14));
        }
    }

    /**
     * Đổ danh sách dòng ra tài liệu, tự mở trang mới mỗi khi dòng kế tiếp
     * không còn nằm trọn phía trên lề dưới. Luôn tạo ít nhất 1 trang (phiếu
     * nào cũng có mã phiếu nên danh sách không bao giờ rỗng).
     */
    private void renderPaginated(PDDocument document, PDFont font, List<PdfLine> lines) throws IOException {
        float top = PDRectangle.A4.getHeight() - PDF_MARGIN;
        int index = 0;
        while (index < lines.size()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            float y = top;
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                while (index < lines.size()) {
                    PdfLine line = lines.get(index);
                    // Cỡ chữ trừ đi vì y là toạ độ CHÂN chữ: chữ còn ăn lên
                    // trên, nên chỉ cần chân chữ nằm trên lề dưới là đủ.
                    if (y - line.fontSize < PDF_MARGIN && y < top) {
                        break; // Hết chỗ -> sang trang mới.
                    }
                    PdfUtil.drawText(cs, font, line.fontSize, PDF_MARGIN, y, line.text);
                    y -= line.leading;
                    index++;
                }
            }
        }
    }

    /** Giá trị trống hiện dấu gạch thay vì để trắng, cho người đọc biết là "chưa có" chứ không phải lỗi in. */
    private String nz(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }

    private String formatDate(Date date) {
        return date == null ? "—" : new SimpleDateFormat("dd/MM/yyyy").format(date);
    }

    /**
     * Đọc giá trị của &lt;input type="datetime-local"&gt; ("yyyy-MM-ddTHH:mm")
     * thành Timestamp. Trả null khi để trống hoặc sai định dạng -- hạn SLA
     * không bắt buộc, và handleUpdate sẽ giữ lại giá trị cũ khi nhận null.
     */
    private Timestamp parseDateTimeOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Timestamp.valueOf(LocalDateTime.parse(value.trim()));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String formatDateTime(Timestamp ts) {
        return ts == null ? "—" : new SimpleDateFormat("dd/MM/yyyy HH:mm").format(ts);
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Trang form cũng là thao tác quản trị: vai trò chỉ-xem không được
        // vào đây, dù nút bấm đã ẩn ở danh sách (xem PERMISSIONS.md).
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.TICKET)) {
            return;
        }
        setDropdownAttributes(request);
        request.getRequestDispatcher(CREATE_VIEW).forward(request, response);
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        TechnicalRequest ticket = id != null ? ticketDAO.findById(id) : null;
        if (ticket == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
            return;
        }

        // Cùng điều kiện với handleUpdate: ngoài vai trò có quyền Full, kỹ
        // thuật viên ĐANG ĐƯỢC GIAO đúng phiếu này cũng được mở form (họ được
        // cập nhật trạng thái/kết quả xử lý của phiếu mình -- PERMISSIONS.md).
        if (!AccessControl.hasFullAccess(request, AccessControl.Resource.TICKET)
                && !AccessControl.canUpdateAssignedTicket(request, ticket)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Bạn không có quyền thực hiện thao tác này.");
            return;
        }

        request.setAttribute("ticket", ticket);
        setDropdownAttributes(request);
        request.getRequestDispatcher(UPDATE_VIEW).forward(request, response);
    }

    // ------------------------------------------------------------------
    // POST actions
    // ------------------------------------------------------------------

    private void handleCreate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.TICKET)) {
            return;
        }
        TechnicalRequest t = buildTicketFromRequest(request, new TechnicalRequest());
        t.setCreatedDate(new Date(System.currentTimeMillis()));
        t.setStatus(TechnicalSupportTicketDAO.STATUS_NEW);
        t.setCreatedBy(resolveCurrentUserId(request, t.getAssignedTechnicianId()));

        if (!isValid(t)) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=new&error=invalid");
            return;
        }
        if (!contractMatchesEnterprise(t)) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=new&error=contract_mismatch");
            return;
        }
        t.setTicketCode(ticketDAO.generateNextTicketCode());

        int newId = ticketDAO.insert(t);
        if (newId <= 0) {
            LOG.warn("Tao phieu ho tro that bai (actor={}, ticketCode={})", Logs.actor(request), t.getTicketCode());
            response.sendRedirect(request.getContextPath() + "/ticket?action=new&error=create_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/ticket?action=view&id=" + newId);
    }

    private void handleUpdate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = parseIntOrNull(request.getParameter("ticketId"));
        if (id == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
            return;
        }

        // Cần đọc lại phiếu hiện có trong DB để: (1) giữ nguyên contractId khi form
        // không gửi lên (dropdown "Hợp đồng" ở updateTicket.jsp mặc định disabled,
        // chỉ enable sau khi AJAX loadContracts() chạy xong -- nếu chậm/lỗi/JS tắt
        // thì trường này không được submit); và (2) không ghi đè lại resolved_at
        // nếu phiếu đã đóng từ trước, xem bên dưới.
        TechnicalRequest existing = ticketDAO.findById(id);
        if (existing == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
            return;
        }

        boolean fullAccess = AccessControl.hasFullAccess(request, AccessControl.Resource.TICKET);
        boolean assignedTechnicianUpdate = !fullAccess && AccessControl.canUpdateAssignedTicket(request, existing);
        if (!fullAccess && !assignedTechnicianUpdate) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Bạn không có quyền thực hiện thao tác này.");
            return;
        }

        // Kỹ thuật viên được giao chỉ được đổi trạng thái/ghi chú xử lý của đúng
        // phiếu của mình -- không được sửa khách hàng/hợp đồng/độ ưu tiên/người
        // xử lý, nên bỏ qua toàn bộ các trường khác dù form có gửi lên hay không.
        TechnicalRequest t = fullAccess ? buildTicketFromRequest(request, new TechnicalRequest()) : existing;
        t.setTicketId(id);
        // contractId trống có HAI nghĩa khác hẳn nhau, phải phân biệt:
        //
        //  - Ô chọn hợp đồng mặc định bị khoá, chỉ mở sau khi AJAX loadContracts()
        //    chạy xong. Form gửi đi trước lúc đó (mạng chậm, lỗi, hoặc tắt JS)
        //    thì trường này không có giá trị -- phải GIỮ hợp đồng cũ.
        //  - Người dùng đổi sang khách hàng khác: JS chủ động xoá trắng ô này.
        //    Lúc đó giữ hợp đồng cũ là SAI, vì hợp đồng đó thuộc khách hàng cũ.
        //
        // JS đặt contractLoaded=1 ngay khi danh sách hợp đồng đã tải xong, nên
        // có marker nghĩa là người dùng thật sự nhìn thấy ô chọn và để trống.
        boolean contractPickerWasUsable = "1".equals(request.getParameter("contractLoaded"));
        if (t.getContractId() == null && !contractPickerWasUsable) {
            t.setContractId(existing.getContractId());
        }
        // Hạn SLA không có ô nhập trên form sửa, nên buildTicketFromRequest
        // không bao giờ set nó. Không giữ lại từ bản cũ ở đây thì mỗi lần
        // Admin/CSKH bấm lưu (dù chỉ đổi trạng thái) là ghi đè sla_deadline
        // thành NULL -- phiếu lập tức biến mất khỏi ô "sắp/đã quá hạn" trên
        // dashboard VÀ khỏi lịch nhắc SLA của NotificationScheduler, vì cả
        // hai đều lọc "sla_deadline IS NOT NULL". Mất dữ liệu âm thầm, không
        // báo lỗi ở đâu cả.
        if (t.getSlaDeadline() == null) {
            t.setSlaDeadline(existing.getSlaDeadline());
        }
        // Chụp lại status/resolvedAt GỐC trước khi set field mới -- ở nhánh
        // không Full access, t chính LÀ existing (cùng reference), nên nếu đọc
        // existing.getStatus() sau khi đã t.setStatus() thì sẽ đọc lại đúng giá
        // trị vừa mới set, luôn thấy "đã đóng từ trước" ngay lần đóng đầu tiên
        // và không bao giờ stamp được resolved_at cho case kỹ thuật viên tự đóng.
        String previousStatus = existing.getStatus();
        Timestamp previousResolvedAt = existing.getResolvedAt();
        t.setStatus(emptyToNull(request.getParameter("status")));
        t.setResolutionSummary(emptyToNull(request.getParameter("resolutionSummary")));
        if (TechnicalSupportTicketDAO.STATUS_CLOSED.equals(t.getStatus())) {
            // Chỉ stamp resolved_at = bây giờ ở lần đầu tiên chuyển sang "Đã đóng"
            // -- nếu phiếu đã đóng từ trước (sửa lại resolutionSummary chẳng hạn),
            // giữ nguyên thời điểm đóng gốc thay vì ghi đè lại mỗi lần lưu.
            t.setResolvedAt(TechnicalSupportTicketDAO.STATUS_CLOSED.equals(previousStatus)
                    ? previousResolvedAt
                    : new Timestamp(System.currentTimeMillis()));
        } else {
            t.setResolvedAt(null);
        }

        if (!isValid(t) || t.getStatus() == null) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=edit&id=" + id + "&error=invalid");
            return;
        }
        if (!contractMatchesEnterprise(t)) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=edit&id=" + id + "&error=contract_mismatch");
            return;
        }

        // Ghi chú nội bộ chỉ đi kèm DÒNG LỊCH SỬ của lần đổi trạng thái này,
        // không lưu vào phiếu: nó trả lời "vì sao lúc đó đổi", nên gắn vào
        // đúng thời điểm đó mới có nghĩa. DAO tự bỏ qua nếu trạng thái không
        // đổi. Người thực hiện lấy từ session, không nhận từ form -- form thì
        // ai cũng sửa được, mà đây là cột dùng để quy trách nhiệm.
        int changedBy = resolveCurrentUserId(request, existing.getAssignedTechnicianId());
        boolean ok = ticketDAO.update(t, changedBy, emptyToNull(request.getParameter("internalNote")));
        if (!ok) {
            LOG.warn("Cap nhat phieu ho tro that bai (actor={}, ticketId={})", Logs.actor(request), id);
            response.sendRedirect(request.getContextPath() + "/ticket?action=edit&id=" + id + "&error=update_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/ticket?action=view&id=" + id);
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.TICKET)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        if (id == null) {
            response.sendRedirect(request.getContextPath() + "/ticket");
            return;
        }

        // Chỉ cho xoá phiếu chưa có ai xử lý dở dang (khác trạng thái "Đang xử lý").
        if (!ticketDAO.canDelete(id)) {
            response.sendRedirect(request.getContextPath() + "/ticket?action=view&id=" + id + "&error=cannot_delete");
            return;
        }

        ticketDAO.softDelete(id);
        response.sendRedirect(request.getContextPath() + "/ticket");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void setDropdownAttributes(HttpServletRequest request) {
        // Toàn bộ khách hàng chưa xoá, phục vụ dropdown "Khách hàng"
        request.setAttribute("customerList", customerDAO.findAll(1, Integer.MAX_VALUE, null, null, null));
        request.setAttribute("userList", employeeDAO.findAllActive());
    }

    private TechnicalRequest buildTicketFromRequest(HttpServletRequest request, TechnicalRequest t) {
        Integer enterpriseId = parseIntOrNull(request.getParameter("enterpriseId"));
        if (enterpriseId != null) {
            t.setEnterpriseId(enterpriseId);
        }
        t.setContractId(parseIntOrNull(request.getParameter("contractId")));
        t.setTicketType(emptyToNull(request.getParameter("ticketType")));
        t.setPriority(emptyToNull(request.getParameter("priority")));
        t.setReceptionChannel(emptyToNull(request.getParameter("receptionChannel")));
        Integer technicianId = parseIntOrNull(request.getParameter("assignedTechnicianId"));
        if (technicianId != null) {
            t.setAssignedTechnicianId(technicianId);
        }
        t.setDescription(emptyToNull(request.getParameter("description")));
        t.setWarranty("on".equals(request.getParameter("isWarranty")));
        t.setSlaDeadline(parseDateTimeOrNull(request.getParameter("slaDeadline")));
        return t;
    }

    /** Người tạo phiếu là user đang đăng nhập; session chưa enforce nên tạm dùng kỹ thuật viên được chọn khi chưa có session. */
    private int resolveCurrentUserId(HttpServletRequest request, int fallbackUserId) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object currentUser = session.getAttribute("currentUser");
            if (currentUser instanceof User) {
                return ((User) currentUser).getUserId();
            }
        }
        return fallbackUserId;
    }

    /**
     * Hợp đồng gắn vào phiếu phải thuộc đúng khách hàng của phiếu đó.
     *
     * Không có ràng buộc nào ở CSDL cho cặp (enterprise_id, contract_id), và
     * isValid() cũng không đối chiếu -- nên nếu chỉ dựa vào JS thì một request
     * tự dựng (hoặc một lỗi JS) là ghi được phiếu "khách hàng B, hợp đồng của
     * A". Trang chi tiết, file PDF và file Excel đều sẽ hiện hợp đồng của công
     * ty khác mà không ai biết.
     *
     * @return true nếu hợp lệ (kể cả khi phiếu không gắn hợp đồng nào).
     */
    private boolean contractMatchesEnterprise(TechnicalRequest t) {
        if (t.getContractId() == null) {
            return true;
        }
        Contract contract = contractDAO.findById(t.getContractId());
        return contract != null && contract.getEnterpriseId() == t.getEnterpriseId();
    }

    /** Các trường bắt buộc phải có khi tạo/sửa phiếu hỗ trợ. */
    private boolean isValid(TechnicalRequest t) {
        return t.getEnterpriseId() > 0
                && t.getTicketType() != null
                && t.getPriority() != null
                && t.getReceptionChannel() != null
                && t.getAssignedTechnicianId() > 0
                && t.getDescription() != null;
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

    private int parseIntOrDefault(String value, int defaultValue) {
        Integer parsed = parseIntOrNull(value);
        return parsed != null ? parsed : defaultValue;
    }

    private String emptyToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }
}
