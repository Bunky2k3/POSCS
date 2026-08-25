package poscs.controller;

import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import poscs.common.AccessControl;
import poscs.common.ExcelUtil;
import poscs.common.FileStorage;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.CustomerLifecycleEventDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Address;
import poscs.model.CustomerLifecycleEvent;
import poscs.model.District;
import poscs.model.Enterprise;
import poscs.model.Province;
import poscs.model.RelationshipRating;
import poscs.model.User;

/**
 * Controller cho toàn bộ chức năng khách hàng (enterprises). Điều hướng
 * theo tham số "action" -- role nào được thao tác gì xem PERMISSIONS.md,
 * enforce bằng AccessControl.requireFullAccess ở đầu mỗi hàm handleCreate/
 * handleUpdate/handleDelete (Kỹ thuật/CSKH chỉ View only trên Customer).
 */
@WebServlet(name = "CustomerController", urlPatterns = {"/customer"})
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 10 * 1024 * 1024, fileSizeThreshold = 1024 * 1024)
public class CustomerController extends HttpServlet {

    private static final String LOGO_SUBFOLDER = "enterprise_logos";

    private static final int PAGE_SIZE = 10;
    private static final String LIST_VIEW = "/jsp/sale/listcustomer.jsp";
    private static final String DETAIL_VIEW = "/jsp/sale/viewcustomerdetail.jsp";
    private static final String CREATE_VIEW = "/jsp/sale/addnewcustomer.jsp";
    private static final String UPDATE_VIEW = "/jsp/sale/updatecustomer.jsp";
    private static final String IMPORT_VIEW = "/jsp/sale/importcustomer.jsp";

    // Cột trong file mẫu nhập Excel (xem downloadImportTemplate/handleImportExcel).
    private static final String[] IMPORT_HEADERS = {
        "Tên doanh nghiệp*", "Loại KH*", "Nhóm KH*", "Mã số thuế*", "Email*", "Số điện thoại*",
        "Website", "Tỉnh/Thành*", "Xã/Phường*", "Địa chỉ chi tiết*", "Người phụ trách (username)"
    };
    private static final int COL_NAME = 0, COL_TYPE = 1, COL_GROUP = 2, COL_TAX = 3, COL_EMAIL = 4,
            COL_PHONE = 5, COL_WEBSITE = 6, COL_PROVINCE = 7, COL_WARD = 8, COL_ADDRESS_DETAIL = 9,
            COL_OWNER_USERNAME = 10;

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final AddressDAO addressDAO = new AddressDAO();
    private final ContractDAO contractDAO = new ContractDAO();
    private final TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();
    private final CustomerLifecycleEventDAO lifecycleEventDAO = new CustomerLifecycleEventDAO();

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
            case "importForm":
                showImportForm(request, response);
                break;
            case "downloadTemplate":
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
            case "evaluate":
                handleEvaluate(request, response);
                break;
            case "importExcel":
                handleImportExcel(request, response);
                break;
            default:
                response.sendRedirect(request.getContextPath() + "/customer");
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
        String typeFilter = request.getParameter("type");
        Integer assigneeFilter = parseIntOrNull(request.getParameter("assigneeId"));

        List<Enterprise> customerList = customerDAO.findAll(page, PAGE_SIZE, keyword, typeFilter, assigneeFilter);
        int totalCount = customerDAO.countAll(keyword, typeFilter, assigneeFilter);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));

        request.setAttribute("customerList", customerList);
        request.setAttribute("userList", employeeDAO.findAllActive());
        request.setAttribute("currentPage", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalCount", totalCount);
        request.setAttribute("keyword", keyword);
        request.setAttribute("typeFilter", typeFilter);
        request.setAttribute("assigneeFilter", assigneeFilter);

        request.getRequestDispatcher(LIST_VIEW).forward(request, response);
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        Enterprise customer = id != null ? customerDAO.findById(id) : null;
        if (customer == null) {
            // MSG-021: khách hàng không tồn tại
            response.sendRedirect(request.getContextPath() + "/customer?error=notfound");
            return;
        }

        request.setAttribute("customer", customer);
        request.setAttribute("contactList", customerDAO.findContactsByEnterpriseId(id));
        request.setAttribute("contractList", contractDAO.findByEnterpriseId(id));
        request.setAttribute("ticketList", ticketDAO.findByEnterpriseId(id));
        request.setAttribute("lifecycleEventList", lifecycleEventDAO.findByEnterpriseId(id));

        request.getRequestDispatcher(DETAIL_VIEW).forward(request, response);
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("userList", employeeDAO.findAllActive());
        request.setAttribute("provinceList", addressDAO.findAllProvinces());
        request.getRequestDispatcher(CREATE_VIEW).forward(request, response);
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        Enterprise customer = id != null ? customerDAO.findById(id) : null;
        if (customer == null) {
            response.sendRedirect(request.getContextPath() + "/customer?error=notfound");
            return;
        }

        request.setAttribute("customer", customer);
        request.setAttribute("userList", employeeDAO.findAllActive());
        request.setAttribute("provinceList", addressDAO.findAllProvinces());
        request.getRequestDispatcher(UPDATE_VIEW).forward(request, response);
    }

    /** Xuất Excel toàn bộ khách hàng khớp filter hiện tại (không phân trang) -- nút "Xuất Excel" ở listcustomer.jsp. */
    private void exportExcel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String keyword = request.getParameter("keyword");
        String typeFilter = request.getParameter("type");
        Integer assigneeFilter = parseIntOrNull(request.getParameter("assigneeId"));

        List<Enterprise> all = customerDAO.findAll(1, Integer.MAX_VALUE, keyword, typeFilter, assigneeFilter);
        String[] headers = {"Mã KH", "Tên doanh nghiệp", "Loại KH", "Nhóm KH", "MST", "Email", "SĐT", "Website",
            "Địa chỉ", "Người phụ trách", "Ngày tham gia", "Xếp hạng quan hệ"};
        List<Object[]> rows = new ArrayList<>();
        for (Enterprise e : all) {
            rows.add(new Object[]{
                e.getEnterpriseCode(),
                e.getEnterpriseName(),
                e.getCustomerType(),
                e.getCustomerGroup(),
                e.getTaxCode(),
                e.getEmail(),
                e.getPhone(),
                e.getWebsite(),
                e.getAddress() != null ? e.getAddress().getFullAddress() : "",
                e.getAccountOwner() != null ? e.getAccountOwner().getFullName() : "",
                e.getJoinDate() != null ? e.getJoinDate().toString() : "",
                e.getCurrentRelationshipRating() != null ? e.getCurrentRelationshipRating().toString() : ""
            });
        }
        ExcelUtil.writeWorkbook(response, "khach_hang", headers, rows);
    }

    /** Hiển thị form nhập Excel hàng loạt (chưa xử lý gì) -- nút "Nhập Excel" ở listcustomer.jsp. */
    private void showImportForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
    }

    /** Sinh file mẫu .xlsx cho nhập khách hàng, có dropdown Loại KH/Nhóm KH/Tỉnh-Thành để hạn chế gõ sai. */
    private void downloadImportTemplate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<Integer, List<String>> dropdowns = new HashMap<>();
        dropdowns.put(COL_TYPE, Arrays.asList("Nhà mạng viễn thông", "Nhà thầu thi công", "Đại lý phân phối"));
        dropdowns.put(COL_GROUP, Arrays.asList("VIP", "Thân thiết", "Tiềm năng", "Thường"));
        List<String> provinceNames = new ArrayList<>();
        for (Province p : addressDAO.findAllProvinces()) {
            provinceNames.add(p.getShortName());
        }
        dropdowns.put(COL_PROVINCE, provinceNames);
        ExcelUtil.writeTemplate(response, "mau_nhap_khach_hang", IMPORT_HEADERS, dropdowns, 200);
    }

    /**
     * Nhập hàng loạt khách hàng từ file .xlsx theo mẫu downloadImportTemplate().
     * Mỗi dòng độc lập -- 1 dòng lỗi không chặn các dòng còn lại. Tỉnh/Thành
     * và Xã/Phường trong file là TÊN (không phải ID) nên phải so khớp lại với
     * addressDAO.findAllProvinces()/findWardsByProvinceId() -- không có sẵn
     * hàm tra theo tên nào trong AddressDAO nên so khớp thủ công ở đây
     * (rút gọn theo getShortName(), bỏ dấu cách/hoa-thường).
     */
    private void handleImportExcel(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER)) {
            return;
        }

        Part filePart = request.getPart("file");
        if (filePart == null || filePart.getSize() <= 0) {
            request.setAttribute("importError", "Vui lòng chọn file .xlsx để nhập.");
            request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
            return;
        }

        List<Province> provinces = addressDAO.findAllProvinces();
        List<User> staff = employeeDAO.findAllActive();
        User currentUser = AccessControl.currentUser(request);

        List<String> errors = new ArrayList<>();
        int successCount = 0;

        List<Row> rows;
        try (java.io.InputStream in = filePart.getInputStream()) {
            rows = ExcelUtil.readRows(in, 0, IMPORT_HEADERS);
        } catch (Exception ex) {
            String detail = ex.getMessage();
            request.setAttribute("importError", "Không đọc được file -- hãy chắc chắn đây là file .xlsx đúng mẫu."
                    + (detail != null ? " (" + detail + ")" : ""));
            request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
            return;
        }

        int rowNumber = 1; // dòng 1 là header trong file gốc
        // Sinh mã KH-xxxx 1 lần rồi tăng dần trong vòng lặp thay vì gọi lại
        // generateNextEnterpriseCode() (1 SELECT MAX riêng) cho từng dòng --
        // với vài trăm dòng, tránh vài trăm round-trip DB không cần thiết.
        String[] nextCode = {customerDAO.generateNextEnterpriseCode()};
        for (Row row : rows) {
            rowNumber++;
            String rowError = importOneCustomerRow(row, rowNumber, provinces, staff, currentUser, nextCode);
            if (rowError == null) {
                successCount++;
            } else {
                errors.add(rowError);
            }
        }

        request.setAttribute("importSuccessCount", successCount);
        request.setAttribute("importErrorCount", errors.size());
        request.setAttribute("importErrors", errors);
        request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
    }

    /**
     * @param nextCode mảng 1 phần tử giữ mã KH-xxxx sẽ dùng cho dòng này -- hàm tự
     * cập nhật lại phần tử này (dựa trên mã enterprise_code thực sự vừa lưu
     * thành công, có thể khác nextCode[0] nếu insert() phải tự thử lại do
     * trùng mã) để dòng kế tiếp dùng, tránh phải đọc lại CSDL mỗi dòng.
     * @return null nếu insert thành công, ngược lại là mô tả lỗi kèm số dòng để hiển thị cho người dùng.
     */
    private String importOneCustomerRow(Row row, int rowNumber, List<Province> provinces, List<User> staff,
            User currentUser, String[] nextCode) {
        String name = ExcelUtil.cellString(row, COL_NAME);
        String type = ExcelUtil.cellString(row, COL_TYPE);
        String group = ExcelUtil.cellString(row, COL_GROUP);
        String tax = ExcelUtil.cellString(row, COL_TAX);
        String email = ExcelUtil.cellString(row, COL_EMAIL);
        String phone = ExcelUtil.cellString(row, COL_PHONE);
        String website = ExcelUtil.cellString(row, COL_WEBSITE);
        String provinceName = ExcelUtil.cellString(row, COL_PROVINCE);
        String wardName = ExcelUtil.cellString(row, COL_WARD);
        String addressDetail = ExcelUtil.cellString(row, COL_ADDRESS_DETAIL);
        String ownerUsername = ExcelUtil.cellString(row, COL_OWNER_USERNAME);

        if (isBlank(name) || isBlank(type) || isBlank(group) || isBlank(tax) || isBlank(email)
                || isBlank(phone) || isBlank(provinceName) || isBlank(wardName) || isBlank(addressDetail)) {
            return "Dòng " + rowNumber + ": thiếu trường bắt buộc (đánh dấu *).";
        }
        if (!isValidPhone(phone)) {
            return "Dòng " + rowNumber + ": số điện thoại không đúng định dạng.";
        }
        if (!isValidEmail(email)) {
            return "Dòng " + rowNumber + ": email không đúng định dạng.";
        }

        Province province = findProvinceByName(provinces, provinceName);
        if (province == null) {
            return "Dòng " + rowNumber + ": không tìm thấy tỉnh/thành \"" + provinceName + "\".";
        }
        District ward = findWardByName(addressDAO.findWardsByProvinceId(province.getProvinceId()), wardName);
        if (ward == null) {
            return "Dòng " + rowNumber + ": không tìm thấy xã/phường \"" + wardName + "\" thuộc \"" + provinceName + "\".";
        }

        Enterprise e = new Enterprise();
        e.setEnterpriseName(name);
        e.setCustomerType(type);
        e.setCustomerGroup(group);
        e.setTaxCode(tax);
        e.setEmail(email);
        e.setPhone(phone);
        e.setWebsite(emptyToNull(website));
        e.setStatus("Active");

        Address address = new Address();
        address.setStreetAndLocalName(addressDetail);
        address.setDistrictId(ward.getDistrictId());
        e.setAddress(address);

        User owner = isBlank(ownerUsername) ? null : findUserByUsername(staff, ownerUsername);
        e.setAccountOwnerId(owner != null ? owner.getUserId() : currentUser.getUserId());

        if (!isValidCommonFields(e)) {
            return "Dòng " + rowNumber + ": dữ liệu không hợp lệ (kiểm tra lại định dạng các trường).";
        }

        e.setEnterpriseCode(nextCode[0]);
        int newId = customerDAO.insert(e);
        if (newId > 0) {
            // insert() có thể đã tự thử lại với mã khác nếu nextCode[0] bị trùng
            // (xem CustomerDAO.insert) -- luôn tính mã kế tiếp từ mã thực sự vừa lưu.
            nextCode[0] = customerDAO.nextEnterpriseCodeAfter(e.getEnterpriseCode());
            return null;
        }
        // Lưu thất bại hẳn (hết số lần thử lại, hoặc lỗi khác) -- đọc lại mã mới
        // nhất từ CSDL để đồng bộ lại trước khi tiếp tục các dòng sau.
        nextCode[0] = customerDAO.generateNextEnterpriseCode();
        return "Dòng " + rowNumber + ": lưu vào CSDL thất bại (có thể MST/email/SĐT đã tồn tại).";
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
        String normalized = username.trim().toLowerCase();
        for (User u : staff) {
            if (u.getUsername() != null && u.getUsername().trim().toLowerCase().equals(normalized)) {
                return u;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    // ------------------------------------------------------------------
    // POST actions
    // ------------------------------------------------------------------

    private void handleCreate(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER)) {
            return;
        }
        Enterprise e = new Enterprise();
        e.setEnterpriseName(request.getParameter("customerName"));
        e.setCustomerType(request.getParameter("customerType"));
        e.setCustomerGroup(request.getParameter("customerGroup"));
        e.setTaxCode(request.getParameter("taxCode"));
        e.setPhone(request.getParameter("phone"));
        e.setEmail(emptyToNull(request.getParameter("email")));
        e.setWebsite(emptyToNull(request.getParameter("website")));
        e.setStatus("Active");
        e.setJoinDate(parseDateOrNull(request.getParameter("joinDate")));
        e.setLogoUrl(FileStorage.save(request.getPart("logo"), LOGO_SUBFOLDER));

        Integer accountOwnerId = parseIntOrNull(request.getParameter("accountOwnerId"));
        if (accountOwnerId != null) {
            e.setAccountOwnerId(accountOwnerId);
        }

        setAddressFromRequest(e, request, null);

        if (!isValidCommonFields(e) || isBlank(e.getTaxCode())) {
            response.sendRedirect(request.getContextPath() + "/customer?action=new&error=invalid");
            return;
        }

        e.setEnterpriseCode(customerDAO.generateNextEnterpriseCode());
        int newId = customerDAO.insert(e);
        if (newId <= 0) {
            response.sendRedirect(request.getContextPath() + "/customer?action=new&error=create_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/customer?action=view&id=" + newId);
    }

    private void handleUpdate(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("customerId"));
        Enterprise existing = id != null ? customerDAO.findById(id) : null;
        if (existing == null) {
            response.sendRedirect(request.getContextPath() + "/customer?error=notfound");
            return;
        }

        Enterprise e = new Enterprise();
        e.setEnterpriseId(id);
        e.setEnterpriseName(request.getParameter("customerName"));
        e.setCustomerType(request.getParameter("customerType"));
        e.setCustomerGroup(request.getParameter("customerGroup"));
        e.setPhone(request.getParameter("phone"));
        e.setEmail(emptyToNull(request.getParameter("email")));
        e.setWebsite(emptyToNull(request.getParameter("website")));
        e.setJoinDate(parseDateOrNull(request.getParameter("joinDate")));

        // Chỉ ghi đè logo khi người dùng thực sự chọn ảnh mới -- input file để
        // trống vẫn gửi lên 1 Part rỗng (size=0), FileStorage.save trả về null
        // trong trường hợp đó, nên giữ nguyên logo cũ thay vì xoá mất.
        String newLogoUrl = FileStorage.save(request.getPart("logo"), LOGO_SUBFOLDER);
        e.setLogoUrl(newLogoUrl != null ? newLogoUrl : existing.getLogoUrl());

        Integer accountOwnerId = parseIntOrNull(request.getParameter("accountOwnerId"));
        if (accountOwnerId != null) {
            e.setAccountOwnerId(accountOwnerId);
        }

        setAddressFromRequest(e, request, existing.getAddressId());

        if (!isValidCommonFields(e)) {
            response.sendRedirect(request.getContextPath() + "/customer?action=edit&id=" + id + "&error=invalid");
            return;
        }

        boolean ok = customerDAO.update(e);
        if (!ok) {
            response.sendRedirect(request.getContextPath() + "/customer?action=edit&id=" + id + "&error=update_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/customer?action=view&id=" + id);
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        if (id == null) {
            response.sendRedirect(request.getContextPath() + "/customer");
            return;
        }

        // BR-41: không cho xoá khách hàng còn hợp đồng đang hiệu lực
        if (customerDAO.hasActiveContracts(id)) {
            response.sendRedirect(request.getContextPath() + "/customer?action=view&id=" + id + "&error=has_active_contracts");
            return;
        }

        customerDAO.softDelete(id);
        response.sendRedirect(request.getContextPath() + "/customer");
    }

    /**
     * Ghi 1 lần đánh giá xếp hạng THỦ CÔNG do người dùng chọn trong box ở
     * viewcustomerdetail.jsp (không có engine tự động tính điểm nào cả --
     * customer_evaluation_rules/contract_payments vẫn nằm trong schema
     * nhưng không có chỗ nào đụng tới) -- ghi kết quả vào
     * enterprises.current_relationship_rating và thêm 1 dòng
     * customer_lifecycle_events (is_auto_generated=0). Cùng quyền Full
     * access với create/update/delete vì đây cũng là 1 thao tác ghi dữ
     * liệu khách hàng.
     */
    private void handleEvaluate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        if (id == null || customerDAO.findById(id) == null) {
            response.sendRedirect(request.getContextPath() + "/customer?error=notfound");
            return;
        }

        RelationshipRating rating = parseRatingOrNull(request.getParameter("rating"));
        if (rating == null) {
            response.sendRedirect(request.getContextPath() + "/customer?action=view&id=" + id + "&error=invalid_rating");
            return;
        }

        customerDAO.updateRelationshipRating(id, rating);

        User currentUser = AccessControl.currentUser(request);
        CustomerLifecycleEvent event = new CustomerLifecycleEvent();
        event.setEnterpriseId(id);
        event.setEventType("Đánh giá xếp hạng");
        event.setRelationshipRating(rating);
        event.setAutoGenerated(false);
        event.setDescription(emptyToNull(request.getParameter("description")));
        event.setEventDate(Date.valueOf(LocalDate.now()));
        event.setRecordedBy(currentUser.getUserId());
        lifecycleEventDAO.insert(event);

        response.sendRedirect(request.getContextPath() + "/customer?action=view&id=" + id + "&evaluated=1");
    }

    /** Chỉ chấp nhận đúng 1 trong 4 tên hằng số của RelationshipRating (khớp value của các <option> trong box chọn). */
    private RelationshipRating parseRatingOrNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return RelationshipRating.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * @param existingAddressId address_id khách hàng đã có sẵn (null nếu tạo mới hoặc
     *                          chưa từng có địa chỉ) -- truyền xuống để CustomerDAO cập
     *                          nhật ngay dòng addresses cũ thay vì tạo dòng mới mỗi lần
     *                          lưu (trước đây luôn INSERT mới, để lại rác không giới hạn).
     */
    private void setAddressFromRequest(Enterprise e, HttpServletRequest request, Integer existingAddressId) {
        Integer districtId = parseIntOrNull(request.getParameter("districtId"));
        String addressDetail = emptyToNull(request.getParameter("addressDetail"));
        if (districtId != null && addressDetail != null) {
            Address address = new Address();
            address.setStreetAndLocalName(addressDetail);
            address.setDistrictId(districtId);
            e.setAddress(address);
        }
        // Luôn gán lại addressId hiện có (kể cả khi request này không gửi lên
        // districtId/addressDetail) -- nếu không, CustomerDAO sẽ coi enterprise
        // này chưa từng có địa chỉ và ghi đè address_id thành NULL.
        e.setAddressId(existingAddressId);
    }

    /**
     * Trường bắt buộc + BR-09 (định dạng SĐT) + BR-10 (định dạng email) + ngày tham gia
     * không ở tương lai. Khớp với validate phía client ở addnewcustomer.jsp/updatecustomer.jsp
     * -- trước đây chỉ có ở client nên có thể bị bypass bằng cách POST thẳng.
     *
     * Email bắt buộc phải nhập (không chỉ đúng định dạng khi có) vì cột
     * enterprises.email trong DB là NOT NULL + UNIQUE (xem db/schema.sql) --
     * để trống sẽ làm INSERT/UPDATE thất bại ở tầng DB thay vì báo lỗi rõ
     * ràng "invalid" ngay tại đây.
     */
    private boolean isValidCommonFields(Enterprise e) {
        if (isBlank(e.getEnterpriseName()) || isBlank(e.getCustomerType()) || isBlank(e.getCustomerGroup())) {
            return false;
        }
        if (e.getAccountOwnerId() <= 0) {
            return false;
        }
        if (!isValidPhone(e.getPhone())) {
            return false;
        }
        if (isBlank(e.getEmail()) || !isValidEmail(e.getEmail())) {
            return false;
        }
        return e.getJoinDate() == null || !e.getJoinDate().toLocalDate().isAfter(java.time.LocalDate.now());
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

    /** BR-10 */
    private boolean isValidEmail(String email) {
        return email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
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
