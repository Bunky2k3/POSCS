package poscs.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Date;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.AccessControl;
import poscs.common.ExcelUtil;
import poscs.common.FileStorage;
import poscs.common.ListScope;
import poscs.common.Logs;
import poscs.common.QueryStrings;
import poscs.dao.AddressDAO;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Address;
import poscs.model.CustomerLifecycleEvent;
import poscs.model.District;
import poscs.model.Enterprise;
import poscs.model.Province;
import poscs.model.RelationshipRating;
import poscs.model.User;
import poscs.service.CustomerEvaluationService;
import poscs.service.CustomerService;
import poscs.service.ServiceResult;
import poscs.validator.CustomerValidator;

/**
 * Controller cho toàn bộ chức năng khách hàng (enterprises). Điều hướng
 * theo tham số "action" -- role nào được thao tác gì xem PERMISSIONS.md,
 * enforce bằng AccessControl.requireFullAccess ở đầu mỗi hàm handleCreate/
 * handleUpdate/handleDelete (Kỹ thuật chỉ View only trên Customer -- CSKH đã
 * gộp vào Sales, xem PERMISSIONS.md).
 */
@WebServlet(name = "CustomerController", urlPatterns = {"/customer"})
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 10 * 1024 * 1024, fileSizeThreshold = 1024 * 1024)
public class CustomerController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerController.class);

    private static final String LOGO_SUBFOLDER = "enterprise_logos";

    private static final int PAGE_SIZE = 10;
    /** Vai được giao phụ trách khách hàng -- khớp roles.role_name, xem AccessControl. */
    private static final String SALES_ROLE = "Sales";

    /**
     * Hai vai của khách hàng, khớp enterprise_roles.role (xem ghi chú đầu V20).
     *
     * <p>Theo góc nhìn CỦA ĐỐI TÁC, không phải của mình: "Khách mua" là bên đó
     * mua của mình. Cố ý không đặt là 'Mua'/'Bán' vì hợp đồng sẽ dùng đúng hai
     * chữ đó theo chiều ngược lại.
     */
    private static final String ROLE_BUYER = "Khách mua";
    private static final String ROLE_SUPPLIER = "Nhà cung cấp";

    /**
     * Loại khách hàng, TÁCH THEO VAI -- hai danh sách khác hẳn nhau.
     *
     * <p>"Nhà mạng viễn thông", "Nhà thầu thi công", "Đại lý phân phối" là cách
     * phân loại người MUA của mình. Áp nguyên bộ đó cho nhà cung cấp thì vô
     * nghĩa: bên bán hàng cho mình được phân theo họ sản xuất, nhập khẩu hay
     * phân phối, chứ không phải theo họ là nhà mạng hay nhà thầu.
     *
     * <p>Gom về đây thay vì chép cứng trong JSP: trước đó cùng ba lựa chọn đó
     * nằm ở listcustomer, addnewcustomer và updatecustomer -- ba bản sao, sửa
     * một chỗ là hai chỗ còn lại lệch đi trong im lặng.
     */
    private static final List<String> BUYER_TYPES = List.of(
            "Nhà mạng viễn thông", "Nhà thầu thi công", "Đại lý phân phối");

    private static final List<String> SUPPLIER_TYPES = List.of(
            "Nhà sản xuất", "Nhà nhập khẩu", "Nhà phân phối", "Đơn vị dịch vụ");

    private static List<String> customerTypesFor(String role) {
        return CustomerService.customerTypesFor(role);
    }

    private static final String LIST_VIEW = "/jsp/sale/listcustomer.jsp";
    private static final String DETAIL_VIEW = "/jsp/sale/viewcustomerdetail.jsp";
    private static final String CREATE_VIEW = "/jsp/sale/addnewcustomer.jsp";
    private static final String UPDATE_VIEW = "/jsp/sale/updatecustomer.jsp";

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final AddressDAO addressDAO = new AddressDAO();
    private final ContractDAO contractDAO = new ContractDAO();
    private final TechnicalSupportTicketDAO ticketDAO = new TechnicalSupportTicketDAO();
    private final CustomerValidator customerValidator = new CustomerValidator();

    private CustomerService customerService;
    private CustomerEvaluationService customerEvaluationService;

    CustomerService getCustomerService() {
        if (customerService != null) {
            return customerService;
        }
        return new CustomerService(customerDAO, employeeDAO, addressDAO, customerValidator);
    }

    void setCustomerService(CustomerService customerService) {
        this.customerService = customerService;
    }

    CustomerEvaluationService getCustomerEvaluationService() {
        if (customerEvaluationService != null) {
            return customerEvaluationService;
        }
        return new CustomerEvaluationService(customerDAO, getCustomerService());
    }

    void setCustomerEvaluationService(CustomerEvaluationService customerEvaluationService) {
        this.customerEvaluationService = customerEvaluationService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Cho JSP biết người đang xem có quyền Full trên tài nguyên này không,
        // để ẩn các nút hành động không dùng được (Tạo/Sửa/Xoá/Nhập/Xuất) thay
        // vì để người ta bấm vào rồi nhận 403. Đây CHỈ là lớp trình bày --
        // chặn thật nằm ở AccessControl.requireFullAccess trong doPost và ở
        // đầu mỗi trang form bên dưới (gõ thẳng URL cũng không vào được).
        request.setAttribute("canManage",
                AccessControl.hasFullAccess(request, AccessControl.Resource.CUSTOMER));
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
        List<Integer> provinceFilters = ContractController.provinceFiltersOf(request);
        // Hai mục con trên thanh điều hướng đi vào cùng trang này, khác nhau ở
        // đúng tham số kind. Giá trị lạ (hoặc thiếu) thì coi như khách mua --
        // đó là danh sách cũ, và là chiều duy nhất có dữ liệu trước V20.
        String roleFilter = roleFromKind(request.getParameter("kind"));
        // ĐỊA BÀN KHÔNG ÁP CHO NHÀ CUNG CẤP. Tỉnh ở đây là địa bàn BÁN HÀNG --
        // nó quyết định ai cầm khách nào (user_provinces). Bên bán hàng cho
        // mình thì không được chia theo địa bàn, nên lọc theo tỉnh ở màn đó vừa
        // vô nghĩa vừa cắt mất kết quả.
        //
        // Bỏ ở đây chứ không chỉ ẩn ô chọn: ẩn thôi thì một provinceId còn sót
        // trên URL vẫn âm thầm thu hẹp danh sách.
        if (ROLE_SUPPLIER.equals(roleFilter)) {
            provinceFilters = List.of();
        }

        // Phạm vi mặc định: Sales mở trang ra thấy phần việc của mình (khách mình đứng
        // tên HOẶC khách trong địa bàn mình giữ), các vai khác thấy toàn bộ. Vẫn nới ra
        // được bằng view=all -- xem AccessControl.listScope.
        ListScope scope = AccessControl.listScope(request,
                () -> employeeDAO.findTeamUserIds(AccessControl.currentUser(request).getUserId()),
                () -> employeeDAO.findProvincesOf(AccessControl.currentUser(request).getUserId())
                        .stream().map(Province::getProvinceId).collect(Collectors.toList()));
        request.setAttribute("viewFilter", AccessControl.defaultView(request));
        request.setAttribute("viewNarrowed", scope.isNarrowed());
        request.setAttribute("viewProvinceCount", scope.getProvinceIds().size());
        request.setAttribute("viewToggleUrl", QueryStrings.with(request, "view",
                AccessControl.VIEW_MINE.equals(AccessControl.defaultView(request))
                        ? AccessControl.VIEW_ALL : AccessControl.VIEW_MINE));

        List<Enterprise> customerList = getCustomerService().findAll(page, PAGE_SIZE, keyword, typeFilter, assigneeFilter,
                provinceFilters, false, roleFilter, scope);
        int totalCount = getCustomerService().countAll(keyword, typeFilter, assigneeFilter, provinceFilters, roleFilter, scope);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));

        request.setAttribute("customerList", customerList);
        // "Toàn chi nhánh" vẫn hiện khách của người khác: ẩn nút Sửa/Xoá ở những
        // dòng Sales không được ghi (xem outsideSalesScope), thay vì để bấm vào
        // rồi mới bị từ chối.
        request.setAttribute("lockedIds", lockedIdsOf(request, customerList, roleFilter));
        // Khách hàng giao cho Sales, nên ô lọc "người phụ trách" chỉ liệt kê
        // Sales -- đổ cả Admin/Kỹ thuật vào là mời người dùng lọc theo những
        // người không bao giờ phụ trách khách hàng nào.
        request.setAttribute("userList", employeeDAO.findActiveByRole(SALES_ROLE));
        request.setAttribute("provinceList", addressDAO.findBranchProvinces());
        request.setAttribute("currentPage", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalCount", totalCount);
        // JSP cần pageSize để đánh STT liên tục qua các trang (trang 2 bắt đầu từ 11).
        request.setAttribute("pageSize", PAGE_SIZE);
        request.setAttribute("keyword", keyword);
        // Bản đã mã hoá URL cho các link phân trang / lọc / xuất -- xem QueryStrings.param.
        request.setAttribute("keywordParam", QueryStrings.param(keyword));
        request.setAttribute("typeFilter", typeFilter);
        request.setAttribute("assigneeFilter", assigneeFilter);
        request.setAttribute("provinceFilters", provinceFilters);
        // Xem ContractController.provinceQuery -- EL không lặp được một tham số
        // nhiều lần trong chuỗi query mà JSP tự ghép (Xuất Excel, phân trang).
        request.setAttribute("provinceQuery", ContractController.provinceQuery(provinceFilters));
        request.setAttribute("myProvinces",
                employeeDAO.findProvincesCoveredBy(AccessControl.currentUser(request).getUserId()));
        request.setAttribute("roleFilter", roleFilter);
        request.setAttribute("kind", ROLE_SUPPLIER.equals(roleFilter) ? "supplier" : "buyer");
        // JSP dựng dropdown từ đây thay vì chép cứng ba lựa chọn -- xem
        // BUYER_TYPES/SUPPLIER_TYPES.
        request.setAttribute("customerTypeOptions", customerTypesFor(roleFilter));
        request.setAttribute("showProvinceFilter", !ROLE_SUPPLIER.equals(roleFilter));

        request.getRequestDispatcher(LIST_VIEW).forward(request, response);
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        Enterprise customer = id != null ? getCustomerService().findById(id) : null;
        if (customer == null) {
            // MSG-021: khách hàng không tồn tại
            response.sendRedirect(notFoundUrl(request));
            return;
        }

        request.setAttribute("customer", customer);
        request.setAttribute("contactList", customerDAO.findContactsByEnterpriseId(id));
        request.setAttribute("contractList", contractDAO.findByEnterpriseId(id));
        request.setAttribute("ticketList", ticketDAO.findByEnterpriseId(id));
        request.setAttribute("lifecycleEventList", getCustomerEvaluationService().getLifecycleEvents(id));
        List<String> roles = getCustomerService().findRolesOf(id);
        request.setAttribute("customerRoles", roles);
        // Ba nút ghi (Sửa, Xoá, Đánh giá) tính theo TỪNG khách chứ không chỉ theo
        // vai: Sales xem được khách của người khác nhưng không ghi được. Kèm cờ
        // riêng để trang nói rõ vì sao không có nút, không để người dùng tưởng
        // mình mất quyền.
        boolean canManage = AccessControl.hasFullAccess(request, AccessControl.Resource.CUSTOMER);
        boolean outside = outsideSalesScope(request, customer, roles);
        request.setAttribute("canWrite", canManage && !outside);
        request.setAttribute("outOfScope", canManage && outside);

        request.getRequestDispatcher(DETAIL_VIEW).forward(request, response);
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Trang form cũng là thao tác quản trị: vai trò chỉ-xem không được
        // vào đây, dù nút bấm đã ẩn ở danh sách (xem PERMISSIONS.md).
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER)) {
            return;
        }
        // Hai trang riêng -- Thêm khách hàng / Thêm nhà cung cấp -- dùng chung
        // một khung JSP, khác nhau ở kind. Vai suy từ trang đang đứng chứ không
        // còn ô tick: bấm "Thêm" từ danh sách nào thì bản ghi về đúng danh sách
        // đó. Xem handleCreate cho luật người phụ trách của từng trang.
        String role = roleFromKind(request.getParameter("kind"));
        boolean supplier = ROLE_SUPPLIER.equals(role);
        request.setAttribute("kind", supplier ? "supplier" : "buyer");
        request.setAttribute("customerTypeOptions", customerTypesFor(role));
        request.setAttribute("userList", employeeDAO.findActiveByRole(SALES_ROLE));

        User me = AccessControl.currentUser(request);
        if (supplier) {
            // ĐỊA BÀN KHÔNG ÁP CHO NHÀ CUNG CẤP (xem showList): địa chỉ chọn
            // trong cả 34 tỉnh, và không nhúng bảng phân công.
            request.setAttribute("provinceList", addressDAO.findAllProvinces());
            if (AccessControl.isSales(request)) {
                request.setAttribute("lockedOwner", me);
            }
        } else {
            List<Province> myProvinces = territoryOf(request);
            if (!myProvinces.isEmpty()) {
                request.setAttribute("provinceList", myProvinces);
                request.setAttribute("lockedOwner", me);
            } else {
                request.setAttribute("provinceList", addressDAO.findBranchProvinces());
                // Phân công địa bàn, để form điền sẵn rồi KHOÁ ô người phụ trách khi
                // chọn tỉnh. Nhúng cả bảng (34 tỉnh) một lần thay vì gọi AJAX mỗi lần
                // đổi ô tỉnh -- dữ liệu nhỏ, và đỡ hẳn một endpoint phải gác quyền
                // riêng. Khoá thật nằm ở resolveAccountOwnerId, đây chỉ là tầng hiển thị.
                request.setAttribute("territoryAssignments", employeeDAO.findAllAssignments());
            }
        }
        // Để form lọc ô "Người hỗ trợ" theo người phụ trách chính -- xem
        // supportIsOwnersManager.
        request.setAttribute("managerOf", employeeDAO.findManagerMap());
        request.getRequestDispatcher(CREATE_VIEW).forward(request, response);
    }

    /**
     * Các tỉnh người đang đăng nhập TRỰC TIẾP cầm, nếu họ là Sales -- rỗng với
     * mọi trường hợp còn lại.
     *
     * <p>Không rỗng thì trang Thêm khách hàng khoá theo người đó: chỉ các tỉnh
     * này, khách đứng tên chính họ. Rỗng thì trang chạy như cũ, người phụ trách
     * suy theo địa bàn: Admin, và cả Sales chưa được giao tỉnh nào -- chốt với
     * người dùng 2026-09-24, để CSKH vẫn tạo được khách hộ người cầm tỉnh.
     *
     * <p>Không gộp tỉnh của cấp dưới: khách ở tỉnh của lính thì phải đứng tên
     * lính, không phải tên sếp.
     */
    private List<Province> territoryOf(HttpServletRequest request) {
        if (!AccessControl.isSales(request)) {
            return List.of();
        }
        List<Province> provinces = new ArrayList<>(
                employeeDAO.findProvincesOf(AccessControl.currentUser(request).getUserId()));
        // findProvincesOf xếp theo tên đầy đủ ("Thành phố ..." đứng trước mọi
        // "Tỉnh ..."), còn dropdown hiện tên ngắn -- xếp lại theo tên ngắn cho
        // giống các ô tỉnh khác.
        provinces.sort(Comparator.comparing(Province::getShortName,
                Collator.getInstance(Locale.forLanguageTag("vi"))));
        return provinces;
    }

    private List<Integer> myProvinceIds(HttpServletRequest request) {
        return territoryOf(request).stream().map(Province::getProvinceId).collect(Collectors.toList());
    }

    /**
     * Sales có bị chặn GHI lên khách này không -- Sửa, Xoá, Đánh giá xếp hạng.
     * Chốt với người dùng 2026-09-26, cùng tinh thần trang Thêm:
     *
     * <ul>
     *   <li>Không phải Sales thì không chặn ở đây: Admin quản trị toàn bộ, còn
     *       vai chỉ-xem đã bị requireFullAccess chặn từ trước.</li>
     *   <li>Khách mua -- kể cả khách vừa mua vừa bán -- đi theo địa bàn. Sales
     *       đã được giao tỉnh chỉ ghi được khách nằm trong danh sách "Của tôi"
     *       của mình: mình hoặc cấp dưới đứng tên, hoặc khách ở tỉnh mình cầm
     *       ({@link ListScope#includes}). Sales CHƯA được giao tỉnh thì không
     *       chặn, như ở trang Thêm: CSKH vẫn làm hộ người cầm tỉnh.</li>
     *   <li>Chỉ là nhà cung cấp thì không theo địa bàn: Sales nào cũng chỉ ghi
     *       được nhà cung cấp mình hoặc cấp dưới đứng tên -- trang Thêm nhà cung
     *       cấp cũng khoá tên người tạo.</li>
     * </ul>
     *
     * <p>Xét trên vai ĐANG LƯU của khách, không phải vai form gửi lên: tick thêm
     * một vai không được là cách mở khoá.
     */
    private boolean outsideSalesScope(HttpServletRequest request, Enterprise customer, List<String> roles) {
        if (!AccessControl.isSales(request)) {
            return false;
        }
        return outsideScope(customer, roles, teamOf(request), myProvinceIds(request));
    }

    private static boolean outsideScope(Enterprise customer, List<String> roles,
            List<Integer> team, List<Integer> myProvinceIds) {
        if (isSupplierOnly(roles)) {
            return !team.contains(customer.getAccountOwnerId());
        }
        if (myProvinceIds.isEmpty()) {
            return false;
        }
        return !ListScope.of(team, myProvinceIds).includes(customer.getAccountOwnerId(), provinceIdOf(customer));
    }

    /** Mình + cấp dưới -- cùng tập mà danh sách "Của tôi" dùng. */
    private List<Integer> teamOf(HttpServletRequest request) {
        return employeeDAO.findTeamUserIds(AccessControl.currentUser(request).getUserId());
    }

    /**
     * Các dòng trong trang danh sách mà người đang xem KHÔNG ghi được -- xem
     * {@link #outsideSalesScope}. Tính một lần cho cả trang thay vì hỏi lại
     * đội và địa bàn ở từng dòng.
     */
    private Set<Integer> lockedIdsOf(HttpServletRequest request, List<Enterprise> customers, String listRole) {
        if (!AccessControl.isSales(request)) {
            return Set.of();
        }
        List<Integer> team = teamOf(request);
        List<Integer> myProvinceIds = myProvinceIds(request);
        Set<Integer> locked = new HashSet<>();
        for (Enterprise c : customers) {
            // Danh sách khách mua thì dòng nào cũng mang vai khách mua. Danh sách
            // nhà cung cấp thì có dòng còn là khách mua nữa -- phải hỏi vai thật.
            List<String> roles = ROLE_BUYER.equals(listRole)
                    ? List.of(ROLE_BUYER)
                    : customerDAO.findRolesOf(c.getEnterpriseId());
            if (outsideScope(c, roles, team, myProvinceIds)) {
                locked.add(c.getEnterpriseId());
            }
        }
        return locked;
    }

    /** Chỉ mang vai nhà cung cấp. Không vai nào thì coi như khách mua, như roleFromKind. */
    private static boolean isSupplierOnly(List<String> roles) {
        return roles.contains(ROLE_SUPPLIER) && !roles.contains(ROLE_BUYER);
    }

    /** Tỉnh của khách qua xã/phường của địa chỉ, null nếu chưa có địa chỉ. */
    private static Integer provinceIdOf(Enterprise customer) {
        Address address = customer.getAddress();
        District district = address != null ? address.getDistrict() : null;
        return district != null ? district.getProvinceId() : null;
    }

    /** {@code provinces} cộng thêm tỉnh {@code provinceId} nếu còn thiếu -- xem showEditForm. */
    private List<Province> withProvince(List<Province> provinces, Integer provinceId) {
        if (provinceId == null || provinces.stream().anyMatch(p -> p.getProvinceId() == provinceId)) {
            return provinces;
        }
        List<Province> result = new ArrayList<>(provinces);
        addressDAO.findAllProvinces().stream()
                .filter(p -> p.getProvinceId() == provinceId)
                .findFirst()
                .ifPresent(result::add);
        return result;
    }

    /**
     * Trang Sửa nào cho khách này -- vai của trang, xem showEditForm.
     *
     * <p>Theo danh sách người dùng vừa đứng ({@code kind} trên URL), nếu khách
     * thật sự mang vai đó: công ty vừa mua vừa bán mở từ danh sách Nhà cung cấp
     * thì ra trang Sửa nhà cung cấp. Không có kind, hoặc kind không khớp vai
     * nào của khách (link gõ tay), thì suy từ vai: chỉ là nhà cung cấp mới ra
     * trang nhà cung cấp, còn lại là trang khách hàng -- cùng luật với mục
     * sáng trên thanh điều hướng ở trang chi tiết.
     */
    private static String editRoleOf(String kind, List<String> roles) {
        String asked = "supplier".equals(kind) ? ROLE_SUPPLIER : "buyer".equals(kind) ? ROLE_BUYER : null;
        if (asked != null && roles.contains(asked)) {
            return asked;
        }
        return isSupplierOnly(roles) ? ROLE_SUPPLIER : ROLE_BUYER;
    }

    /** {@code types} cộng thêm {@code current} nếu còn thiếu -- xem showEditForm. */
    private List<String> withCurrentType(List<String> types, String current) {
        if (isBlank(current) || types.contains(current)) {
            return types;
        }
        List<String> result = new ArrayList<>(types);
        result.add(current);
        return result;
    }

    /**
     * Trang Sửa, khách mua: Sales đã có tỉnh chỉ chuyển khách trong các tỉnh
     * mình cầm, như trang Thêm -- hoặc giữ nguyên tỉnh hiện tại của khách (khách
     * cũ có thể nằm ở tỉnh người khác, xem showEditForm). Admin và Sales chưa có
     * tỉnh thì không giới hạn.
     *
     * <p>Kiểm theo XÃ/PHƯỜNG gửi lên chứ không theo ô tỉnh rời, cùng lý do với
     * {@link #resolveAccountOwnerId}. Không gửi xã/phường nào thì địa chỉ giữ
     * nguyên (setAddressFromRequest), không có gì để kiểm.
     */
    private boolean provinceAllowedForSales(HttpServletRequest request, Enterprise existing) {
        List<Integer> myProvinceIds = myProvinceIds(request);
        Integer wardId = parseIntOrNull(request.getParameter("districtId"));
        if (myProvinceIds.isEmpty() || wardId == null) {
            return true;
        }
        Integer wardProvinceId = addressDAO.findProvinceIdOfWard(wardId);
        return wardProvinceId != null
                && (myProvinceIds.contains(wardProvinceId) || wardProvinceId.equals(provinceIdOf(existing)));
    }

    /**
     * Người phụ trách chính khi SỬA -- chỗ khoá thật, JSP chỉ khoá hình.
     *
     * <ul>
     *   <li>Sales: luôn giữ người đang phụ trách, bỏ qua tên gửi lên -- khách mua
     *       hay nhà cung cấp, đổi tỉnh cũng vậy (chốt với người dùng 2026-10-01).
     *       Sales không tự chuyển khách cho ai, kể cả cho chính mình.</li>
     *   <li>Admin, chỉ là nhà cung cấp: không theo địa bàn, chọn tay.</li>
     *   <li>Admin, khách mua: theo địa bàn, {@link #resolveAccountOwnerId}. Trang
     *       Sửa của Admin là chỗ duy nhất chuyển khách sang người khác -- vd. khi
     *       nhân viên nghỉ.</li>
     * </ul>
     */
    private Integer ownerForUpdate(HttpServletRequest request, Enterprise existing, List<String> roles) {
        if (AccessControl.isSales(request)) {
            return existing.getAccountOwnerId();
        }
        return isSupplierOnly(roles)
                ? parseIntOrNull(request.getParameter("accountOwnerId"))
                : resolveAccountOwnerId(request);
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Trang form cũng là thao tác quản trị: vai trò chỉ-xem không được
        // vào đây, dù nút bấm đã ẩn ở danh sách (xem PERMISSIONS.md).
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        Enterprise customer = id != null ? getCustomerService().findById(id) : null;
        if (customer == null) {
            response.sendRedirect(notFoundUrl(request));
            return;
        }
        List<String> roles = getCustomerService().findRolesOf(id);
        if (outsideSalesScope(request, customer, roles)) {
            response.sendRedirect(request.getContextPath()
                    + "/customer?action=view&id=" + id + "&error=not_your_customer");
            return;
        }

        request.setAttribute("customer", customer);
        // Hai trang -- Sửa khách hàng / Sửa nhà cung cấp -- chung một khung JSP
        // như trang Thêm. Trang chỉ quyết định CHỮ (tiêu đề, nhãn, loại, nhóm)
        // và vai nào là vai của trang; luật địa bàn bên dưới vẫn theo vai đang
        // lưu, không theo trang: một công ty vừa mua vừa bán mở từ danh sách
        // Nhà cung cấp vẫn là khách mua, vẫn chia theo tỉnh.
        String pageRole = editRoleOf(request.getParameter("kind"), roles);
        request.setAttribute("kind", ROLE_SUPPLIER.equals(pageRole) ? "supplier" : "buyer");
        // Cùng lý do với danh sách tỉnh ngay dưới: người đang phụ trách khách
        // này phải còn trong danh sách kể cả khi họ đã đổi vai, nếu không thì
        // mở form sửa lên ô trống rồi bấm lưu là thay mất người phụ trách.
        request.setAttribute("userList", employeeDAO.findActiveByRole(
                SALES_ROLE, customer.getAccountOwnerId(), customer.getSupportOwnerId()));
        Integer currentProvinceId = provinceIdOf(customer);
        // Sales không đổi được người đang phụ trách -- xem ownerForUpdate.
        boolean sales = AccessControl.isSales(request);
        if (sales) {
            request.setAttribute("lockedOwner", customer.getAccountOwner());
        }
        if (isSupplierOnly(roles)) {
            // ĐỊA BÀN KHÔNG ÁP CHO NHÀ CUNG CẤP, như trang Thêm nhà cung cấp: cả
            // 34 tỉnh, và không nhúng bảng phân công nên ô người phụ trách không
            // nhảy theo tỉnh.
            request.setAttribute("provinceList", addressDAO.findAllProvinces());
        } else {
            List<Province> myProvinces = territoryOf(request);
            if (myProvinces.isEmpty()) {
                // Khách cũ có thể nằm ngoài 18 tỉnh địa bàn -- giữ tỉnh đó trong
                // danh sách, nếu không thì mở form sửa lên ô tỉnh trống và bấm lưu
                // là mất địa chỉ dù người dùng chỉ định sửa số điện thoại.
                request.setAttribute("provinceList", addressDAO.findBranchProvincesIncluding(currentProvinceId));
            } else {
                // Sales đã có tỉnh, như trang Thêm: chỉ chuyển khách trong các tỉnh
                // mình cầm -- cộng tỉnh hiện tại của khách, cùng lý do như trên.
                request.setAttribute("provinceList", withProvince(myProvinces, currentProvinceId));
            }
            if (!sales) {
                // Admin: như showCreateForm, ô người phụ trách khoá theo địa bàn,
                // nếu không thì đổi tỉnh ở đây là đường vòng thoát khoá. Sales
                // thì ô đã khoá sẵn người đang phụ trách, không cần bảng này.
                request.setAttribute("territoryAssignments", employeeDAO.findAllAssignments());
            }
        }
        request.setAttribute("customerRoles", roles);
        // Loại theo trang đang đứng, cộng loại ĐANG LƯU nếu nó thuộc vai kia:
        // công ty hai vai chỉ có một cột loại, mở trang này ra mà dropdown
        // thiếu loại hiện tại thì bấm lưu là đổi mất phân loại của họ.
        request.setAttribute("customerTypeOptions",
                withCurrentType(customerTypesFor(pageRole), customer.getCustomerType()));
        request.setAttribute("managerOf", employeeDAO.findManagerMap());
        request.getRequestDispatcher(UPDATE_VIEW).forward(request, response);
    }

    /** Xuất Excel toàn bộ khách hàng khớp filter hiện tại (không phân trang) -- nút "Xuất Excel" ở listcustomer.jsp. */
    private void exportExcel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String keyword = request.getParameter("keyword");
        String typeFilter = request.getParameter("type");
        Integer assigneeFilter = parseIntOrNull(request.getParameter("assigneeId"));
        List<Integer> provinceFilters = ContractController.provinceFiltersOf(request);
        String roleFilter = roleFromKind(request.getParameter("kind"));
        if (ROLE_SUPPLIER.equals(roleFilter)) {
            provinceFilters = List.of();
        }
        ListScope scope = AccessControl.listScope(request,
                () -> employeeDAO.findTeamUserIds(AccessControl.currentUser(request).getUserId()),
                () -> employeeDAO.findProvincesOf(AccessControl.currentUser(request).getUserId())
                        .stream().map(Province::getProvinceId).collect(Collectors.toList()));

        String prefix = getCustomerService().getExportFileNamePrefix(roleFilter);
        ExcelUtil.prepareAttachmentHeaders(response, prefix);
        try (OutputStream out = response.getOutputStream()) {
            getCustomerService().exportCustomersToExcel(out, keyword, typeFilter, assigneeFilter, provinceFilters, roleFilter, scope);
        }
    }

    // ------------------------------------------------------------------
    // POST actions
    // ------------------------------------------------------------------

    private void handleCreate(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER)) {
            return;
        }
        // Vai theo trang gửi form lên (kind), không theo ô tick: mỗi trang tạo
        // đúng một vai, nên cũng không còn chuyện lưu khách không vai nào. Công
        // ty vừa mua vừa bán thì tick thêm vai thứ hai ở trang Sửa.
        String role = roleFromKind(request.getParameter("kind"));
        boolean supplier = ROLE_SUPPLIER.equals(role);
        String formUrl = request.getContextPath() + "/customer?action=new" + (supplier ? "&kind=supplier" : "");
        if (!logoIsAcceptable(request, response, formUrl)) {
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

        User me = AccessControl.currentUser(request);
        boolean isSales = AccessControl.isSales(request);
        Integer wardId = parseIntOrNull(request.getParameter("districtId"));
        String addressDetail = emptyToNull(request.getParameter("addressDetail"));
        Integer inputOwnerId = parseIntOrNull(request.getParameter("accountOwnerId"));
        Integer inputSupportOwnerId = parseIntOrNull(request.getParameter("supportOwnerId"));

        ServiceResult<Enterprise> valResult = getCustomerService().prepareAndValidateCreate(
                e, role, me, isSales, wardId, addressDetail, inputOwnerId, inputSupportOwnerId);
        if (!valResult.isSuccess()) {
            response.sendRedirect(formUrl + "&error=" + valResult.getMessage());
            return;
        }

        e.setLogoUrl(FileStorage.save(request.getPart("logo"), LOGO_SUBFOLDER, FileStorage.IMAGE_EXTENSIONS));

        ServiceResult<Integer> result = getCustomerService().insertCustomer(e, role);
        if (!result.isSuccess()) {
            LOG.warn("Tao khach hang that bai (actor={}, enterpriseCode={})", Logs.actor(request), e.getEnterpriseCode());
            response.sendRedirect(formUrl + "&error=" + result.getMessage());
            return;
        }

        int newId = result.getData();
        if ("roles_not_saved".equals(result.getMessage())) {
            LOG.warn("Ghi vai khach hang moi that bai (actor={}, enterpriseId={})", Logs.actor(request), newId);
            response.sendRedirect(request.getContextPath() + "/customer?action=view&id=" + newId + "&error=roles_not_saved");
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
        if (id == null) {
            response.sendRedirect(notFoundUrl(request));
            return;
        }

        String pageRole = roleFromKind(request.getParameter("kind"));
        String kindQuery = ROLE_SUPPLIER.equals(pageRole) ? "&kind=supplier" : "";
        String formUrl = request.getContextPath() + "/customer?action=edit&id=" + id + kindQuery;
        String detailUrl = request.getContextPath() + "/customer?action=view&id=" + id + kindQuery;

        if (!logoIsAcceptable(request, response, formUrl)) {
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

        User me = AccessControl.currentUser(request);
        boolean isSales = AccessControl.isSales(request);
        String[] submittedRoles = request.getParameterValues("roles");
        Integer wardId = parseIntOrNull(request.getParameter("districtId"));
        String addressDetail = emptyToNull(request.getParameter("addressDetail"));
        Integer inputOwnerId = parseIntOrNull(request.getParameter("accountOwnerId"));
        Integer inputSupportOwnerId = parseIntOrNull(request.getParameter("supportOwnerId"));

        ServiceResult<CustomerService.UpdatePreparedState> prepResult = getCustomerService().prepareAndValidateUpdate(
                e, submittedRoles, pageRole, me, isSales, wardId, addressDetail, inputOwnerId, inputSupportOwnerId);
        if (!prepResult.isSuccess()) {
            String error = prepResult.getMessage();
            if ("notfound".equals(error)) {
                response.sendRedirect(notFoundUrl(request));
            } else if ("not_your_customer".equals(error)) {
                response.sendRedirect(detailUrl + "&error=not_your_customer");
            } else {
                response.sendRedirect(formUrl + "&error=" + error);
            }
            return;
        }

        CustomerService.UpdatePreparedState state = prepResult.getData();
        String newLogoUrl = FileStorage.save(request.getPart("logo"), LOGO_SUBFOLDER, FileStorage.IMAGE_EXTENSIONS);
        e.setLogoUrl(newLogoUrl != null ? newLogoUrl : state.getExisting().getLogoUrl());

        ServiceResult<Enterprise> commitResult = getCustomerService().commitUpdate(e, state.getRoles());
        if (!commitResult.isSuccess()) {
            LOG.warn("Cap nhat khach hang that bai (actor={}, enterpriseId={})", Logs.actor(request), id);
            response.sendRedirect(formUrl + "&error=" + commitResult.getMessage());
            return;
        }

        if ("roles_not_saved".equals(commitResult.getMessage())) {
            LOG.warn("Ghi vai khach hang that bai (actor={}, enterpriseId={})", Logs.actor(request), id);
            response.sendRedirect(detailUrl + "&error=roles_not_saved");
            return;
        }
        response.sendRedirect(detailUrl);
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        if (id == null) {
            response.sendRedirect(notFoundUrl(request));
            return;
        }
        User me = AccessControl.currentUser(request);
        boolean isSales = AccessControl.isSales(request);
        String kind = ROLE_SUPPLIER.equals(roleFromKind(request.getParameter("kind"))) ? "supplier" : "buyer";
        String kindQuery = request.getParameter("kind") != null ? "&kind=" + kind : "";

        ServiceResult<Void> result = getCustomerService().deleteCustomer(id, me, isSales);
        if (result.isSuccess()) {
            response.sendRedirect(request.getContextPath() + "/customer?kind=" + kind);
        } else {
            String error = result.getMessage();
            if ("notfound".equals(error)) {
                response.sendRedirect(notFoundUrl(request));
            } else if ("not_your_customer".equals(error)) {
                response.sendRedirect(request.getContextPath() + "/customer?action=view&id=" + id + "&error=not_your_customer");
            } else if ("has_active_contracts".equals(error)) {
                response.sendRedirect(request.getContextPath() + "/customer?action=view&id=" + id + "&error=has_active_contracts");
            } else {
                LOG.warn("Xoa khach hang that bai (actor={}, enterpriseId={})", Logs.actor(request), id);
                response.sendRedirect(request.getContextPath() + "/customer?action=view&id=" + id + kindQuery + "&error=" + error);
            }
        }
    }

    private void handleEvaluate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.CUSTOMER)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        if (id == null) {
            response.sendRedirect(notFoundUrl(request));
            return;
        }

        User currentUser = AccessControl.currentUser(request);
        boolean isSales = AccessControl.isSales(request);
        ServiceResult<CustomerLifecycleEvent> result = getCustomerEvaluationService().evaluateCustomer(
                id, request.getParameter("rating"), emptyToNull(request.getParameter("description")), currentUser, isSales);

        if (result.isSuccess()) {
            response.sendRedirect(request.getContextPath() + "/customer?action=view&id=" + id + "&evaluated=1");
        } else {
            String error = result.getMessage();
            if ("notfound".equals(error)) {
                response.sendRedirect(notFoundUrl(request));
            } else {
                if ("evaluate_failed".equals(error)) {
                    LOG.warn("Danh gia khach hang that bai (actor={}, enterpriseId={})", Logs.actor(request), id);
                }
                response.sendRedirect(request.getContextPath() + "/customer?action=view&id=" + id + "&error=" + error);
            }
        }
    }

    private RelationshipRating parseRatingOrNull(String value) {
        return getCustomerEvaluationService().parseRatingOrNull(value);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Đổi tham số {@code kind} trên URL thành vai trong CSDL.
     *
     * <p>Chỉ "supplier" mới ra nhà cung cấp; mọi giá trị khác -- kể cả thiếu hẳn
     * -- đều ra khách mua. Cố ý KHÔNG trả null cho "xem tất cả": trộn hai
     * chiều vào một danh sách chính là cái chung chung mà việc tách này sinh
     * ra để bỏ, và một công ty giữ cả hai vai sẽ khó nói nó đang nằm ở đâu.
     */
    private String roleFromKind(String kind) {
        return "supplier".equals(kind) ? ROLE_SUPPLIER : ROLE_BUYER;
    }

    /**
     * Về danh sách kèm error=notfound, giữ đúng danh sách người dùng vừa đứng.
     *
     * <p>Bản ghi đã không còn nên không suy được vai từ nó; chỉ còn tham số kind
     * do trang gửi lên. Thiếu bước này thì mở/xoá một nhà cung cấp đã bị xoá từ
     * danh sách Nhà cung cấp sẽ bị đẩy sang danh sách Khách hàng mua, và thông
     * báo hiện ở một danh sách khác chỗ người dùng vừa bấm.
     */
    private String notFoundUrl(HttpServletRequest request) {
        return request.getContextPath() + "/customer?error=notfound"
                + (ROLE_SUPPLIER.equals(roleFromKind(request.getParameter("kind"))) ? "&kind=supplier" : "");
    }

    /**
     * Các vai sẽ lưu khi SỬA: vai của trang (luôn giữ) cộng vai kia nếu ô
     * "Đồng thời là..." được tick.
     *
     * <p>Vai của trang không bỏ được ở chính trang đó -- muốn bỏ thì sang trang
     * Sửa của vai kia mà bỏ tick (chốt với người dùng 2026-10-01). Nhờ vậy cũng
     * không còn đường lưu khách không vai nào, thứ làm khách biến khỏi cả hai
     * danh sách.
     *
     * <p>Lọc theo danh sách trắng chứ không nhận thẳng chuỗi gửi lên: cột
     * {@code role} là varchar tự do, một request nặn tay ghi được vai "Khách
     * VIP" vào đó. Xếp khách mua trước cho thứ tự ghi ổn định, trang nào gửi
     * lên cũng vậy.
     */
    private List<String> rolesForUpdate(HttpServletRequest request, String pageRole) {
        String[] submitted = request.getParameterValues("roles");
        List<String> ticked = submitted != null ? List.of(submitted) : List.of();
        List<String> result = new ArrayList<>();
        for (String role : List.of(ROLE_BUYER, ROLE_SUPPLIER)) {
            if (role.equals(pageRole) || ticked.contains(role)) {
                result.add(role);
            }
        }
        return result;
    }

    /**
     * Người phụ trách chính: địa bàn quyết định, không phải người nhập liệu.
     *
     * <p>Đây là CHỖ KHOÁ DUY NHẤT. Tỉnh của khách đã có người cầm thì trả về
     * người đó và bỏ qua hẳn ô {@code accountOwnerId} gửi lên -- khoá ở JSP
     * chỉ là khoá hình, ai mở devtools cũng gỡ được. Tỉnh chưa ai cầm thì mới
     * dùng giá trị người dùng chọn.
     *
     * <p>Nhánh "chưa ai cầm" không phải chiều lòng ai: bảng phân công mới phủ
     * một phần trong 34 tỉnh, khoá tất thì không tạo nổi khách hàng ở những
     * tỉnh còn trống -- biến "chưa phân công" thành chặn nghiệp vụ. Khi khách
     * giao đủ bảng phân công thì nhánh này tự hết đường chạy, không phải sửa
     * code.
     *
     * <p>Suy từ xã/phường chứ không từ ô tỉnh: xem {@link
     * poscs.dao.EmployeeDAO#findAssigneeOfWard}.
     */
    private Integer resolveAccountOwnerId(HttpServletRequest request) {
        Integer wardId = parseIntOrNull(request.getParameter("districtId"));
        if (wardId != null) {
            Integer territoryOwnerId = employeeDAO.findAssigneeOfWard(wardId);
            // Kiểm > 0 chứ không chỉ != null: user_id 0 không phải người nào
            // cả, mà nhận nó ở đây là khoá khách hàng vào một nhân viên không
            // tồn tại rồi vỡ ở tầng khoá ngoại -- xa chỗ gây ra hẳn một tầng.
            if (territoryOwnerId != null && territoryOwnerId > 0) {
                return territoryOwnerId;
            }
        }
        return parseIntOrNull(request.getParameter("accountOwnerId"));
    }

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
     * Kiểm ô chọn logo TRƯỚC khi đụng tới CSDL. Trả về false (và đã tự redirect
     * kèm lỗi) nếu file sai loại.
     *
     * Phải chạy sớm: FileStorage.save() trả về null cho file sai loại y hệt
     * khi người dùng không chọn gì -- nên nếu không chặn ở đây thì luồng tạo
     * mới lặng lẽ lưu khách hàng không logo, còn luồng sửa thì lặng lẽ giữ
     * nguyên logo cũ.
     */
    private boolean logoIsAcceptable(HttpServletRequest request, HttpServletResponse response,
            String redirectBase) throws ServletException, IOException {
        if (FileStorage.isAcceptable(request.getPart("logo"), FileStorage.IMAGE_EXTENSIONS)) {
            return true;
        }
        response.sendRedirect(redirectBase + "&error=invalid_image_type");
        return false;
    }

    /**
     * Người hỗ trợ có phải CẤP TRÊN TRỰC TIẾP của người phụ trách chính không.
     *
     * <p>Chốt với người dùng 2026-09-24: người hỗ trợ không được đứng cao hơn
     * người phụ trách chính -- tức không được là cấp trên của chính người đó.
     * Trưởng nhóm KHÁC (không quản lý người phụ trách) thì vẫn được. Trùng
     * chính người phụ trách chính thì {@link #isValidCommonFields} đã chặn.
     *
     * <p>Form đã lọc sẵn ô này (appshell.js, POSCS.ganLocNguoiHoTro); chặn ở
     * đây vì lọc trên form ai mở devtools cũng gỡ được. Cây tổ chức chưa có dữ
     * liệu thì luật chưa chặn ai -- xem EmployeeDAO.findManagerMap.
     */
    private boolean supportIsOwnersManager(Enterprise e) {
        Integer supportOwnerId = e.getSupportOwnerId();
        if (supportOwnerId == null || e.getAccountOwnerId() <= 0) {
            return false;
        }
        return supportOwnerId.equals(employeeDAO.findManagerMap().get(e.getAccountOwnerId()));
    }

    /**
     * BR-27: tên mã lỗi của ô đầu tiên bị trùng với khách hàng khác, hoặc null
     * nếu không trùng gì. Tên trả về dùng thẳng làm {@code ?error=...} để màn
     * hình chỉ đúng ô phải sửa, thay vì một chữ "create_failed" chung chung.
     *
     * <p>Trả về ô ĐẦU TIÊN chứ không gom cả ba: form chỉ hiện được một thông
     * báo, và người dùng sửa xong ô này bấm lưu lại sẽ thấy ngay ô tiếp theo
     * nếu còn.
     *
     * @param excludeId khách đang sửa, null khi đang tạo mới
     */
    private String findDuplicateField(Enterprise e, Integer excludeId) {
        if (customerDAO.existsByEmail(e.getEmail(), excludeId)) {
            return "duplicate_email";
        }
        if (customerDAO.existsByPhone(e.getPhone(), excludeId)) {
            return "duplicate_phone";
        }
        // Mã số thuế chỉ có trên form tạo mới -- CustomerDAO.update cố ý không
        // ghi cột đó, nên lúc sửa e.getTaxCode() là null và phép kiểm tự bỏ qua.
        if (customerDAO.existsByTaxCode(e.getTaxCode(), excludeId)) {
            return "duplicate_tax_code";
        }
        return null;
    }

    /**
     * Trường bắt buộc + BR-09 (định dạng SĐT) + BR-10 (định dạng email) +
     * BR-32 (ngày tham gia không ở tương lai). Khớp với validate phía client ở
     * addnewcustomer.jsp/updatecustomer.jsp -- trước đây chỉ có ở client nên
     * có thể bị bypass bằng cách POST thẳng.
     *
     * <p>Email bắt buộc phải nhập (không chỉ đúng định dạng khi có) vì cột
     * enterprises.email trong DB là NOT NULL + UNIQUE (xem db/schema.sql) --
     * để trống sẽ làm INSERT/UPDATE thất bại ở tầng DB thay vì báo lỗi rõ
     * ràng "invalid" ngay tại đây. Còn việc email đó có TRÙNG người khác không
     * thì {@link #findDuplicateField} lo.
     */
    private boolean isValidCommonFields(Enterprise e) {
        if (isBlank(e.getEnterpriseName()) || isBlank(e.getCustomerType()) || isBlank(e.getCustomerGroup())) {
            return false;
        }
        if (e.getAccountOwnerId() <= 0) {
            return false;
        }
        // Người hỗ trợ là vai thứ hai, không phải bản sao của vai thứ nhất:
        // để trùng một người thì cột "Người hỗ trợ" chỉ lặp lại tên đã có ở
        // cột bên cạnh, và mọi thống kê theo người sẽ đếm người đó hai lần.
        if (e.getSupportOwnerId() != null && e.getSupportOwnerId() == e.getAccountOwnerId()) {
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

    /**
     * Tên tỉnh/thành của khách hàng cho cột riêng khi xuất Excel. Bỏ tiền tố
     * "Tỉnh "/"Thành phố " để khớp với thứ tự đã sắp ở DAO, và để lọc/pivot
     * trong Excel gõ đúng tên tỉnh là ra.
     *
     * Khách chưa có địa chỉ ghi rõ "Chưa xác định" chứ không để trống: quản lý
     * theo địa bàn mà ô trống thì người đọc file không biết là thiếu dữ liệu
     * hay lỗi xuất.
     */
    private static String provinceNameOf(Enterprise enterprise) {
        Address address = enterprise.getAddress();
        District district = address != null ? address.getDistrict() : null;
        Province province = district != null ? district.getProvince() : null;
        return province != null ? province.getShortName() : "Chưa xác định";
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
