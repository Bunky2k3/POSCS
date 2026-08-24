package poscs.controller;

import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import poscs.common.AccessControl;
import poscs.common.ExcelUtil;
import poscs.common.PdfUtil;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.ContractProduct;
import poscs.model.Enterprise;

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
@WebServlet(name = "ContractController", urlPatterns = {"/contract"})
public class ContractController extends HttpServlet {

    private static final int PAGE_SIZE = 10;
    private static final String LIST_VIEW = "/jsp/sale/listcontract.jsp";
    private static final String DETAIL_VIEW = "/jsp/sale/viewcontractdetail.jsp";
    private static final String CREATE_VIEW = "/jsp/sale/addnewcontract.jsp";
    private static final String UPDATE_VIEW = "/jsp/sale/updatecontract.jsp";

    private final ContractDAO contractDAO = new ContractDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
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

        List<Contract> contractList = contractDAO.findAll(page, PAGE_SIZE, keyword, statusFilter, typeFilter);
        int totalCount = contractDAO.countAll(keyword, statusFilter, typeFilter);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));
        Map<String, Integer> statusSummary = contractDAO.countStatusSummary();

        request.setAttribute("contractList", contractList);
        request.setAttribute("statusSummary", statusSummary);
        request.setAttribute("currentPage", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalCount", totalCount);
        request.setAttribute("keyword", keyword);
        request.setAttribute("statusFilter", statusFilter);
        request.setAttribute("typeFilter", typeFilter);

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
        request.setAttribute("canDelete", contractDAO.canDelete(id));
        request.setAttribute("contractProducts", contractDAO.findProductsByContractId(id));

        request.getRequestDispatcher(DETAIL_VIEW).forward(request, response);
    }

    /** Xuất Excel toàn bộ hợp đồng khớp filter hiện tại (không phân trang) -- nút "Xuất Excel" ở listcontract.jsp. */
    private void exportExcel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String keyword = request.getParameter("keyword");
        String statusFilter = request.getParameter("status");
        String typeFilter = request.getParameter("type");

        List<Contract> all = contractDAO.findAll(1, Integer.MAX_VALUE, keyword, statusFilter, typeFilter);
        String[] headers = {"Mã HĐ", "Tiêu đề", "Loại HĐ", "Khách hàng", "Người phụ trách",
            "Ngày ký", "Ngày hiệu lực", "Ngày kết thúc", "Trạng thái"};
        List<Object[]> rows = new ArrayList<>();
        for (Contract c : all) {
            rows.add(new Object[]{
                c.getContractCode(),
                c.getTitle(),
                c.getContractType(),
                c.getEnterprise() != null ? c.getEnterprise().getEnterpriseName() : "",
                c.getOwner() != null ? c.getOwner().getFullName() : "",
                c.getSigningDate() != null ? c.getSigningDate().toString() : "",
                c.getEffectiveDate() != null ? c.getEffectiveDate().toString() : "",
                c.getEndDate() != null ? c.getEndDate().toString() : "",
                c.getStatus()
            });
        }
        ExcelUtil.writeWorkbook(response, "hop_dong", headers, rows);
    }

    /**
     * Xuất PDF 1 hợp đồng -- nút "Xuất PDF" ở viewcontractdetail.jsp.
     * contract.enterprise (từ ContractDAO) chỉ có id+tên nên phải gọi lại
     * customerDAO.findById() lấy Enterprise đầy đủ (địa chỉ/MST/người đại
     * diện) để in vào PDF. Không có đơn giá/thành tiền (xem javadoc đầu file).
     */
    private void exportPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        Contract contract = id != null ? contractDAO.findById(id) : null;
        if (contract == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }
        List<ContractProduct> items = contractDAO.findProductsByContractId(id);
        Enterprise enterprise = customerDAO.findById(contract.getEnterpriseId());

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDFont font = PdfUtil.loadVietnameseFont(document, getServletContext());
            PDFont boldFont = font; // Noto Sans chỉ nhúng bản Regular -- phân biệt tiêu đề bằng cỡ chữ thay vì in đậm

            float margin = 50f;
            float width = page.getMediaBox().getWidth() - margin * 2;
            float y = page.getMediaBox().getHeight() - margin;

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                PdfUtil.drawText(cs, boldFont, 16, margin, y, "HỢP ĐỒNG " + safe(contract.getContractCode()));
                y -= 26;
                y = PdfUtil.drawWrapped(cs, font, 11, margin, y, width, 15, safe(contract.getTitle()));
                y -= 12;

                PdfUtil.drawText(cs, boldFont, 11, margin, y, "BÊN A (Bên cung cấp)");
                y -= 16;
                y = PdfUtil.drawWrapped(cs, font, 10, margin, y, width, 14,
                        "Công ty Cổ phần Thiết bị Bưu điện (POSTEF)");
                if (contract.getOwner() != null) {
                    y = PdfUtil.drawWrapped(cs, font, 10, margin, y, width, 14,
                            "Người phụ trách: " + safe(contract.getOwner().getFullName()));
                }
                y -= 8;

                PdfUtil.drawText(cs, boldFont, 11, margin, y, "BÊN B (Khách hàng)");
                y -= 16;
                if (enterprise != null) {
                    y = PdfUtil.drawWrapped(cs, font, 10, margin, y, width, 14, "Tên: " + safe(enterprise.getEnterpriseName()));
                    y = PdfUtil.drawWrapped(cs, font, 10, margin, y, width, 14, "Mã số thuế: " + safe(enterprise.getTaxCode()));
                    String address = enterprise.getAddress() != null ? enterprise.getAddress().getFullAddress() : "";
                    y = PdfUtil.drawWrapped(cs, font, 10, margin, y, width, 14, "Địa chỉ: " + safe(address));
                    y = PdfUtil.drawWrapped(cs, font, 10, margin, y, width, 14,
                            "Người đại diện: " + safe(enterprise.getLegalRepresentative()));
                    y = PdfUtil.drawWrapped(cs, font, 10, margin, y, width, 14,
                            "Điện thoại: " + safe(enterprise.getPhone()) + "     Email: " + safe(enterprise.getEmail()));
                }
                y -= 8;

                PdfUtil.drawText(cs, font, 10, margin, y,
                        "Ngày ký: " + formatDate(contract.getSigningDate())
                        + "     Hiệu lực từ: " + formatDate(contract.getEffectiveDate())
                        + "     đến: " + formatDate(contract.getEndDate()));
                y -= 26;

                PdfUtil.drawText(cs, boldFont, 11, margin, y, "Hạng mục sản phẩm / dịch vụ");
                y -= 18;

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
                y = PdfUtil.drawTable(cs, font, boldFont, 9, margin, y, colWidths, tableHeaders, tableRows);
                y -= 44;

                float halfWidth = width / 2;
                PdfUtil.drawText(cs, boldFont, 10, margin, y, "ĐẠI DIỆN BÊN A");
                PdfUtil.drawText(cs, boldFont, 10, margin + halfWidth, y, "ĐẠI DIỆN BÊN B");
                y -= 14;
                PdfUtil.drawText(cs, font, 9, margin, y, "(Ký, ghi rõ họ tên)");
                PdfUtil.drawText(cs, font, 9, margin + halfWidth, y, "(Ký, ghi rõ họ tên)");
            }

            response.setContentType("application/pdf");
            String fileName = "hopdong_" + safe(contract.getContractCode()).replace("/", "-") + ".pdf";
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            document.save(response.getOutputStream());
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String formatDate(Date date) {
        return date == null ? "" : new SimpleDateFormat("dd/MM/yyyy").format(date);
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setDropdownAttributes(request);
        request.getRequestDispatcher(CREATE_VIEW).forward(request, response);
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        Contract contract = id != null ? contractDAO.findById(id) : null;
        if (contract == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }

        request.setAttribute("contract", contract);
        setDropdownAttributes(request);
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
        if (!isValid(c)) {
            response.sendRedirect(request.getContextPath() + "/contract?action=new&error=invalid");
            return;
        }
        c.setContractCode(contractDAO.generateNextContractCode());

        int newId = contractDAO.insert(c);
        if (newId <= 0) {
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
        if (id == null) {
            response.sendRedirect(request.getContextPath() + "/contract?error=notfound");
            return;
        }

        Contract c = buildContractFromRequest(request, new Contract());
        c.setContractId(id);
        if (!isValid(c)) {
            response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + id + "&error=invalid");
            return;
        }

        boolean ok = contractDAO.update(c);
        if (!ok) {
            response.sendRedirect(request.getContextPath() + "/contract?action=edit&id=" + id + "&error=update_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + id);
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CONTRACT)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        if (id == null) {
            response.sendRedirect(request.getContextPath() + "/contract");
            return;
        }

        // BR-46: chỉ được xoá hợp đồng ở trạng thái "Chưa hiệu lực"
        if (!contractDAO.canDelete(id)) {
            response.sendRedirect(request.getContextPath() + "/contract?action=view&id=" + id + "&error=cannot_delete");
            return;
        }

        contractDAO.softDelete(id);
        response.sendRedirect(request.getContextPath() + "/contract");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void setDropdownAttributes(HttpServletRequest request) {
        // Toàn bộ khách hàng chưa xoá, phục vụ dropdown "Khách hàng"
        request.setAttribute("customerList", customerDAO.findAll(1, Integer.MAX_VALUE, null, null, null));
        request.setAttribute("userList", employeeDAO.findAllActive());
    }

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
