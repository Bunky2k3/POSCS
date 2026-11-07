package poscs.service;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.ServletContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import poscs.common.ExcelUtil;
import poscs.common.PdfUtil;
import poscs.common.Period;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.TechnicalRequest;

/**
 * Service phụ trách nghiệp vụ xuất dữ liệu phiếu hỗ trợ kỹ thuật ra Excel và PDF (Task 5.5 - [UC-42]).
 * Đáp ứng các yêu cầu:
 * - Xuất Excel: danh sách phiếu theo bộ lọc đang chọn, không phân trang, cấu trúc 18 cột theo BR-45.
 * - Xuất PDF: 1 phiếu hỗ trợ đầy đủ cấu trúc, tự động phân trang nhiều trang khi nội dung dài,
 *   nhúng font Noto Sans tiếng Việt.
 */
public class TicketExportService extends BaseService {

    public static final String[] TICKET_EXCEL_HEADERS = {
        "Mã phiếu", "Loại phiếu", "Khách hàng", "Hợp đồng liên quan",
        "Mức ưu tiên", "Kênh tiếp nhận", "Trạng thái", "Người xử lý", "Người tạo",
        "Ngày tạo", "Hạn xử lý (SLA)", "Thời điểm đóng", "Bảo hành",
        "Mô tả sự cố", "Nguyên nhân sự cố", "Nhóm nguyên nhân", "Phương hướng xử lý", "Kết quả xử lý"
    };

    public static final float PDF_MARGIN = 56f;

    private final TechnicalSupportTicketDAO ticketDAO;

    public TicketExportService() {
        this(new TechnicalSupportTicketDAO());
    }

    public TicketExportService(TechnicalSupportTicketDAO ticketDAO) {
        this.ticketDAO = ticketDAO != null ? ticketDAO : new TechnicalSupportTicketDAO();
    }

    public TechnicalSupportTicketDAO getTicketDAO() {
        return ticketDAO;
    }

    public String getExcelFileNamePrefix() {
        return "phieu_ho_tro";
    }

    public String getPdfFileName(TechnicalRequest ticket) {
        if (ticket == null) {
            return "phieu_export.pdf";
        }
        String code = ticket.getTicketCode();
        if (code != null && !code.trim().isEmpty()) {
            return "phieu_" + code.trim().replace("/", "-") + ".pdf";
        }
        if (ticket.getTicketId() > 0) {
            return "phieu_" + ticket.getTicketId() + ".pdf";
        }
        return "phieu_export.pdf";
    }

    public List<Object[]> buildExportRows(List<TechnicalRequest> tickets) {
        List<Object[]> rows = new ArrayList<>();
        if (tickets == null) {
            return rows;
        }
        for (TechnicalRequest t : tickets) {
            if (t == null) {
                continue;
            }
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
                t.getRootCause(),
                t.getCauseCategory(),
                t.getHandlingPlan(),
                t.getResolutionSummary()
            });
        }
        return rows;
    }

    public void exportTicketsToExcel(OutputStream outputStream, List<TechnicalRequest> tickets) throws IOException {
        if (outputStream == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }
        List<Object[]> rows = buildExportRows(tickets);
        ExcelUtil.writeWorkbook(outputStream, TICKET_EXCEL_HEADERS, rows);
    }

    public void exportTicketsToExcel(OutputStream outputStream, String keyword, String statusFilter,
            String priorityFilter, Period period, Integer assigneeId) throws IOException {
        List<TechnicalRequest> all = ticketDAO.findAll(1, Integer.MAX_VALUE, keyword, statusFilter,
                priorityFilter, period, assigneeId);
        exportTicketsToExcel(outputStream, all);
    }

    public PDDocument buildPdfDocument(TechnicalRequest ticket, ServletContext servletContext) throws IOException {
        if (ticket == null || servletContext == null) {
            throw new IllegalArgumentException("Ticket and ServletContext cannot be null");
        }
        PDDocument document = new PDDocument();
        try {
            PDFont font = PdfUtil.loadVietnameseFont(document, servletContext);
            float contentWidth = PDRectangle.A4.getWidth() - PDF_MARGIN * 2;
            List<PdfLine> lines = buildPdfLines(ticket, font, contentWidth);
            renderPaginated(document, font, lines);
            return document;
        } catch (IOException | RuntimeException e) {
            document.close();
            throw e;
        }
    }

    private List<PdfLine> buildPdfLines(TechnicalRequest ticket, PDFont font, float contentWidth) throws IOException {
        List<PdfLine> lines = new ArrayList<>();
        lines.add(new PdfLine(17, "PHIẾU HỖ TRỢ KỸ THUẬT", 22));
        lines.add(new PdfLine(11, "Mã phiếu: " + nz(ticket.getTicketCode()), 26));
        addPair(lines, "Khách hàng", ticket.getEnterprise() != null ? ticket.getEnterprise().getEnterpriseName() : "—");
        addPair(lines, "Hợp đồng liên quan", ticket.getContract() != null ? ticket.getContract().getContractCode() : "—");
        addPair(lines, "Loại phiếu", ticket.getTicketType());
        addPair(lines, "Mức ưu tiên", ticket.getPriority());
        addPair(lines, "Kênh tiếp nhận", ticket.getReceptionChannel());
        addPair(lines, "Trạng thái", ticket.getStatus());
        addPair(lines, "Ngày tạo", formatDate(ticket.getCreatedDate()));
        addPair(lines, "Hạn xử lý (SLA)", formatDateTime(ticket.getSlaDeadline()));
        addPair(lines, "Kỹ thuật viên phụ trách", ticket.getAssignedTechnician() != null ? ticket.getAssignedTechnician().getFullName() : "—");
        addPair(lines, "Thời điểm đóng", formatDateTime(ticket.getResolvedAt()));
        addParagraph(lines, font, contentWidth, "Mô tả sự cố", ticket.getDescription());
        addParagraph(lines, font, contentWidth, "Nguyên nhân sự cố", causeForPrint(ticket));
        addParagraph(lines, font, contentWidth, "Phương hướng xử lý", ticket.getHandlingPlan());
        addParagraph(lines, font, contentWidth, "Kết quả xử lý", ticket.getResolutionSummary());
        return lines;
    }

    public void exportTicketToPdf(OutputStream outputStream, TechnicalRequest ticket, ServletContext servletContext)
            throws IOException {
        if (outputStream == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }
        try (PDDocument document = buildPdfDocument(ticket, servletContext)) {
            document.save(outputStream);
        }
    }

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
                    if (y - line.fontSize < PDF_MARGIN && y < top) {
                        break;
                    }
                    PdfUtil.drawText(cs, font, line.fontSize, PDF_MARGIN, y, line.text);
                    y -= line.leading;
                    index++;
                }
            }
        }
    }

    private void addPair(List<PdfLine> lines, String label, String value) {
        lines.add(new PdfLine(10, label + ": " + nz(value), 17));
    }

    private void addParagraph(List<PdfLine> lines, PDFont font, float width, String title, String body)
            throws IOException {
        lines.add(new PdfLine(11, title, 16));
        List<String> wrapped = PdfUtil.wrapLines(font, 10, width, nz(body));
        for (int i = 0; i < wrapped.size(); i++) {
            lines.add(new PdfLine(10, wrapped.get(i), i == wrapped.size() - 1 ? 22 : 14));
        }
    }

    public String causeForPrint(TechnicalRequest t) {
        if (t == null) {
            return null;
        }
        String category = t.getCauseCategory();
        String detail = t.getRootCause();
        if (category == null || category.trim().isEmpty()) {
            return detail;
        }
        if (detail == null || detail.trim().isEmpty()) {
            return "[" + category + "]";
        }
        return "[" + category + "] " + detail;
    }

    public String nz(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }

    public String formatDate(Date date) {
        return date == null ? "—" : new SimpleDateFormat("dd/MM/yyyy").format(date);
    }

    public String formatDateTime(Timestamp ts) {
        return ts == null ? "—" : new SimpleDateFormat("dd/MM/yyyy HH:mm").format(ts);
    }

    public static final class PdfLine {

        final float fontSize;
        final String text;
        final float leading;

        public PdfLine(float fontSize, String text, float leading) {
            this.fontSize = fontSize;
            this.text = text;
            this.leading = leading;
        }

        public float getFontSize() { return fontSize; }
        public String getText() { return text; }
        public float getLeading() { return leading; }
    }
}
