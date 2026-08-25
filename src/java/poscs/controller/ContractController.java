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
import poscs.common.AccessControl;
import poscs.common.ExcelUtil;
import poscs.common.PdfUtil;
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
@WebServlet(name = "ContractController", urlPatterns = {"/contract"})
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 10 * 1024 * 1024, fileSizeThreshold = 1024 * 1024)
public class ContractController extends HttpServlet {

    private static final int PAGE_SIZE = 10;
    private static final String LIST_VIEW = "/jsp/sale/listcontract.jsp";
    private static final String DETAIL_VIEW = "/jsp/sale/viewcontractdetail.jsp";
    private static final String CREATE_VIEW = "/jsp/sale/addnewcontract.jsp";
    private static final String UPDATE_VIEW = "/jsp/sale/updatecontract.jsp";
    private static final String IMPORT_VIEW = "/jsp/sale/importcontract.jsp";

    /** Số dòng sản phẩm tối đa trong hopdong_import_template.pdf (field product1..product15). */
    private static final int IMPORT_MAX_PRODUCT_ROWS = 15;

    private final ContractDAO contractDAO = new ContractDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final AddressDAO addressDAO = new AddressDAO();
    private final ProductDAO productDAO = new ProductDAO();

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
                // không báo gì (xem javadoc drawTable).
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Hợp đồng có quá nhiều dòng sản phẩm để xuất vừa 1 trang PDF -- vui lòng liên hệ quản trị viên.");
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

            int contractId = contractDAO.insert(contract);
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
            if (!contractDAO.insertProducts(contractId, items)) {
                // giữ đúng cam kết "không ghi gì nếu có lỗi" ở javadoc đầu hàm: hợp
                // đồng vừa tạo (và khách hàng mới nếu có) không được để lại mồ côi
                // với 0 dòng sản phẩm trong khi vẫn báo import thành công
                contractDAO.softDelete(contractId);
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
            System.err.println("--- LOI NHAP HOP DONG TU PDF ---");
            ex.printStackTrace();
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
