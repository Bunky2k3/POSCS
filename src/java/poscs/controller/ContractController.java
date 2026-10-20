package poscs.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.AccessControl;
import poscs.common.ListScope;
import poscs.common.Logs;
import poscs.common.MoneyVnd;
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
import poscs.model.Department;
import poscs.model.ContractHandover;
import poscs.model.ContractHistory;
import poscs.model.ContractPayment;
import poscs.model.ContractProduct;
import poscs.model.District;
import poscs.model.Enterprise;
import poscs.model.Product;
import poscs.model.Province;
import poscs.model.User;
import poscs.service.PermissionService;

/**
 * Controller cho hợp đồng (bảng contracts) và mọi thứ treo vào nó: hạng mục
 * sản phẩm, kỳ thanh toán, phụ lục, liên kết bán&harr;mua, bàn giao liên
 * phòng, tài liệu. Điều hướng theo tham số "action" -- quyền hạn theo
 * PERMISSIONS.md, enforce bằng AccessControl.requireFullAccess ở đầu mỗi hàm
 * handleCreate/handleUpdate/handleDelete (Kỹ thuật chỉ View only trên
 * Contract -- CSKH đã gộp vào Sales, xem PERMISSIONS.md).
 *
 * <p>Hạng mục sản phẩm (contractproducts) gắn/gỡ bằng hai action riêng
 * (addProduct / removeProduct) trên form sửa, không nằm trong form tạo:
 * hợp đồng phải tồn tại trước thì dòng hạng mục mới có chỗ để treo vào.
 *
 * <p>KHÔNG có đơn giá theo dòng. Bảng contractproducts chỉ lưu số lượng và
 * đơn vị, còn tiền nằm ở đúng một chỗ: cột {@code contracts.contract_value},
 * một số TỔNG do người dùng gõ theo bản giấy. Hệ quả cần biết khi đọc mọi
 * đoạn tính tiền ở đây: không có cách nào đối chiếu tổng giá trị với hạng
 * mục, nên phần thành tiền/VAT trong mockup UI vẫn chưa dựng được.
 */
@WebServlet(name = "ContractController", urlPatterns = {"/contract", "/contract/byEnterprise"})
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

    private ContractDAO contractDAO = new ContractDAO();
    private CustomerDAO customerDAO = new CustomerDAO();
    private EmployeeDAO employeeDAO = new EmployeeDAO();
    private AddressDAO addressDAO = new AddressDAO();
    private ProductDAO productDAO = new ProductDAO();

    private PermissionService permissionService;
    private ContractListController listController;
    private ContractDetailController detailController;
    private ContractPaymentController paymentController;
    private ContractDocumentController documentController;
    private ContractExportController exportController;
    private ContractLinkController linkController;

    PermissionService getPermissionService() {
        if (permissionService != null) {
            return permissionService;
        }
        return new PermissionService(employeeDAO) {
            @Override
            public User getEffectiveUser(User sessionUser) {
                User u = super.getEffectiveUser(sessionUser);
                if (u != null) {
                    return u;
                }
                if (sessionUser != null && !sessionUser.isDeleted() && sessionUser.getRole() != null) {
                    return sessionUser;
                }
                return null;
            }

            // Tương tự getEffectiveUser fallback về sessionUser khi employeeDAO không tìm thấy bản ghi
            // (ví dụ mock test không stub findById), isSubordinate fallback về user.getManagerId() != null
            // thay vì fail-closed trả về true làm sai lệch quyền của test mock.
            @Override
            public boolean isSubordinate(User user) {
                if (user == null || isAdmin(user)) {
                    return false;
                }
                if (employeeDAO != null && user.getUserId() > 0) {
                    User dbUser = employeeDAO.findById(user.getUserId());
                    if (dbUser != null) {
                        return dbUser.getManagerId() != null;
                    }
                }
                return user.getManagerId() != null;
            }
        };
    }

    void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    ContractListController getListController() {
        if (listController != null) {
            return listController;
        }
        return new ContractListController(contractDAO, employeeDAO, addressDAO, getPermissionService());
    }

    void setListController(ContractListController listController) {
        this.listController = listController;
    }

    ContractDetailController getDetailController() {
        if (detailController != null) {
            return detailController;
        }
        return new ContractDetailController(contractDAO, employeeDAO, getPermissionService());
    }

    ContractPaymentController getPaymentController() {
        if (paymentController != null) {
            return paymentController;
        }
        return new ContractPaymentController(contractDAO, employeeDAO, null, getPermissionService());
    }

    void setPaymentController(ContractPaymentController paymentController) {
        this.paymentController = paymentController;
    }

    ContractDocumentController getDocumentController() {
        if (documentController == null) {
            this.documentController = new ContractDocumentController(contractDAO, employeeDAO, null, getPermissionService());
        }
        return documentController;
    }

    void setDocumentController(ContractDocumentController documentController) {
        this.documentController = documentController;
    }

    void setDetailController(ContractDetailController detailController) {
        this.detailController = detailController;
    }

    ContractExportController getExportController() {
        if (exportController == null) {
            this.exportController = new ContractExportController(contractDAO, employeeDAO, null, getPermissionService());
        }
        return exportController;
    }

    void setExportController(ContractExportController exportController) {
        this.exportController = exportController;
    }

    ContractLinkController getLinkController() {
        if (linkController == null) {
            this.linkController = new ContractLinkController(contractDAO, employeeDAO, null, getPermissionService());
        }
        return linkController;
    }

    void setLinkController(ContractLinkController linkController) {
        this.linkController = linkController;
    }

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
        // để ẩn các nút hành động không dùng được (Tạo/Sửa/Xoá/Xuất) thay
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
            case "addDocument":
                handleAddDocument(request, response);
                break;
            case "voidDocument":
                handleVoidDocument(request, response);
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
        getListController().showList(request, response);
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        getDetailController().doGet(request, response);
    }

    /** Xuất Excel toàn bộ hợp đồng khớp filter hiện tại (không phân trang) -- nút "Xuất Excel" ở listcontract.jsp (UC-41). */
    private void exportExcel(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        getExportController().doGet(request, response);
    }

    // static: FilterState (lớp lồng static) cũng dùng chung phép kiểm này.
    /**
     * Về danh sách kèm error=notfound, giữ danh sách Hợp đồng mua nếu trang gửi
     * kind=buy. Bản ghi đã không còn nên không suy được chiều từ nó -- cùng cách
     * với CustomerController.notFoundUrl.
     */
    private String notFoundUrl(HttpServletRequest request) {
        return request.getContextPath() + "/contract?error=notfound"
                + ("buy".equals(request.getParameter("kind")) ? "&kind=buy" : "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
            response.sendRedirect(notFoundUrl(request));
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
            response.sendRedirect(notFoundUrl(request));
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
            // Mang theo kind như các nhánh lỗi phía trên: thiếu nó thì tạo hợp đồng
            // MUA hỏng xong bị đưa về form hợp đồng BÁN (bộ loại, ô đối tác đổi hết).
            response.sendRedirect(request.getContextPath()
                    + "/contract?action=new&kind=" + (DIRECTION_BUY.equals(c.getDirection()) ? "buy" : "sell")
                    + "&error=create_failed");
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
            response.sendRedirect(notFoundUrl(request));
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
            response.sendRedirect(notFoundUrl(request));
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
            response.sendRedirect(notFoundUrl(request));
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
            response.sendRedirect(notFoundUrl(request));
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
     * <p>Trước đây đây là "xoá hợp đồng" theo UC-34, mở cho cả Sales và chặn
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
            response.sendRedirect(notFoundUrl(request));
            return;
        }

        // Bản NHÁP thì ai quản được hợp đồng cũng xoá được: nó chưa ký, chưa là
        // chứng cứ gì, xoá một bản nháp sai là việc thường ngày. Đây chính là
        // điều kiện đúng của UC-34 ("nháp hoặc chưa có hiệu lực") -- trước V24
        // nó không với tới được vì signing_date NOT NULL khiến mọi hợp đồng
        // đều đã ký.
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
            response.sendRedirect(notFoundUrl(request));
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
            response.sendRedirect(notFoundUrl(request));
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
        ContractControllerHelper.putContractWorkspace(request, contract, contractDAO, employeeDAO);
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
     *
     * <p>Hai trục lọc, đúng bằng hai người đọc ở trên: <b>phòng</b> cho phòng
     * nhận, <b>phạm vi</b> cho giám đốc và Sales.
     */
    private void showHandoverQueue(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // ===== Phạm vi: mặc định TOÀN CHI NHÁNH =====
        //
        // Khác hẳn hai màn danh sách, nơi Sales mở ra là thấy "của tôi". Ở đây
        // mặc định đó sẽ hỏng: người phòng Kế toán/Dự án -- một trong hai người
        // đọc chính của trang -- KHÔNG đứng tên hợp đồng nào cả, nên "của tôi"
        // với họ là màn hình trống. Hàng đợi là việc chung; ai muốn thu hẹp về
        // phần mình thì tự chọn.
        User me = AccessControl.currentUser(request);
        boolean mine = AccessControl.VIEW_MINE.equals(request.getParameter("view")) && me != null;
        List<Integer> ownerIds = mine ? employeeDAO.findTeamUserIds(me.getUserId()) : null;
        request.setAttribute("viewFilter", mine ? AccessControl.VIEW_MINE : AccessControl.VIEW_ALL);

        List<ContractHandover> inScope = contractDAO.findPendingHandovers(ownerIds);

        // Dải tổng quan đếm trên tập ĐÃ áp phạm vi nhưng CHƯA áp bộ lọc phòng.
        // Đó là điều kiện để nó làm được việc thứ hai của nó -- cái công tắc
        // chuyển phòng: lọc luôn cả nó thì chọn Kế toán xong là mất số của Dự
        // án, không còn đường bấm sang.
        //
        // Cũng vì thế mà lọc phòng làm ở Java chứ không thêm điều kiện vào SQL:
        // dải đếm cần đúng tập chưa lọc phòng, nên hỏi CSDL lần nữa chỉ để lấy
        // lại thứ đang cầm trên tay. Hàng đợi là việc đang tồn, không phải cả
        // lịch sử, nên tập này nhỏ.
        Map<String, Integer> byDepartment = new java.util.LinkedHashMap<>();
        long slowest = 0;
        for (ContractHandover h : inScope) {
            byDepartment.merge(h.getDepartmentName(), 1, Integer::sum);
            slowest = Math.max(slowest, h.getDaysWaiting());
        }
        request.setAttribute("handoverCountByDepartment", byDepartment);
        request.setAttribute("slowestHandoverDays", slowest);
        request.setAttribute("scopeTotal", inScope.size());
        // JSP cần cả id (để dựng link lọc) lẫn tên (để tra số đếm ở map trên).
        request.setAttribute("departmentList", employeeDAO.findAllDepartments());

        // ===== Bộ lọc phòng =====
        Integer departmentFilter = parseIntOrNull(request.getParameter("departmentId"));
        List<ContractHandover> rows = new ArrayList<>();
        for (ContractHandover h : inScope) {
            if (departmentFilter != null && departmentFilter != h.getDepartmentId()) {
                continue;
            }
            rows.add(h);
            request.setAttribute("canCompleteHandover_" + h.getHandoverId(),
                    AccessControl.canCompleteHandover(request, h.getDepartmentId()));
        }
        request.setAttribute("pendingHandovers", rows);
        request.setAttribute("departmentFilter", departmentFilter);
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
            response.sendRedirect(notFoundUrl(request));
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
            response.sendRedirect(notFoundUrl(request));
            return;
        }
        if (!AccessControl.canCompleteHandover(request, departmentId)) {
            LOG.warn("Tu choi xac nhan chang ban giao: khong thuoc phong do (actor={}, handoverId={})",
                    Logs.actor(request), handoverId);
            response.sendRedirect(request.getContextPath() + "/error/403.jsp");
            return;
        }
        // Quay lại đúng chỗ vừa bấm. Ba chỗ bấm được, và chỗ nào cũng phải
        // quay về chính nó: đẩy người của Kế toán/Dự án về action=edit là đẩy
        // họ vào trang họ không có quyền mở -- xác nhận xong thì ăn ngay 403.
        String returnTo = request.getParameter("returnTo");
        String back;
        if ("from-queue".equals(returnTo)) {
            // Mang theo bộ lọc đang bật. Không có nó thì người của Kế toán lọc
            // ra việc phòng mình, xác nhận một chặng, rồi bị đẩy về danh sách
            // của cả chi nhánh và phải lọc lại từ đầu cho từng dòng -- đúng
            // cái phiền mà bộ lọc sinh ra để bỏ.
            back = "/contract?action=handovers" + handoverQueueFilterQuery(request);
        } else if ("from-view".equals(returnTo)) {
            back = "/contract?action=view&id=" + contractId;
        } else {
            back = "/contract?action=edit&id=" + contractId;
        }
        if (!contractDAO.completeHandover(handoverId, actorId(request), request.getParameter("doneNote"))) {
            response.sendRedirect(request.getContextPath() + back + "&error=handover_done_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + back);
    }

    /**
     * Phần query giữ lại bộ lọc của hàng đợi bàn giao khi quay về sau khi xác
     * nhận xong một chặng.
     *
     * <p>Dựng lại từ giá trị ĐÃ KIỂM chứ không nối thẳng tham số vào chuỗi:
     * chuỗi này đi vào {@code sendRedirect}, tức là vào header Location. Chép
     * nguyên xi thứ người dùng gửi lên là mở đường nhét ký tự xuống dòng vào
     * header. Phạm vi chỉ nhận đúng một chữ, phòng ban phải đọc ra được số.
     */
    private String handoverQueueFilterQuery(HttpServletRequest request) {
        StringBuilder query = new StringBuilder();
        if (AccessControl.VIEW_MINE.equals(request.getParameter("view"))) {
            query.append("&view=").append(AccessControl.VIEW_MINE);
        }
        Integer departmentId = parseIntOrNull(request.getParameter("queueDepartmentId"));
        if (departmentId != null) {
            query.append("&departmentId=").append(departmentId);
        }
        return query.toString();
    }

    // ------------------------------------------------------------------
    // Giấy tờ kèm theo hợp đồng (V34)
    // ------------------------------------------------------------------

    /**
     * Treo một giấy tờ vào hợp đồng.
     *
     * <p>Link bắt buộc và phải là http/https -- {@code TextRules.isSafeHttpUrl}
     * chặn "javascript:...", thứ sẽ chạy trên chính origin của ứng dụng khi
     * người sau bấm vào dòng đó ở bảng giấy tờ.
     *
     * <p>Loại giấy tờ phải nằm trong danh mục: ô chọn trên form vốn chỉ có bấy
     * nhiêu, nên một giá trị khác nghĩa là POST nặn tay. Chặn ở đây để danh
     * mục còn là danh mục -- cột doc_type không ràng buộc gì ở CSDL.
     *
     * <p>KHÔNG kiểm trạng thái hợp đồng: thêm được ở mọi trạng thái, kể cả sau
     * thanh lý. Phần lớn giấy tờ chỉ sinh ra lúc hoặc sau khi thanh lý -- chính
     * biên bản thanh lý là ví dụ.
     */
    private void handleAddDocument(HttpServletRequest request, HttpServletResponse response) throws IOException {
        getDocumentController().handleAddDocument(request, response);
    }

    /**
     * Huỷ một giấy tờ -- xoá MỀM, bắt buộc có lý do.
     *
     * <p>Uỷ quyền xử lý sang {@link ContractDocumentController#handleVoidDocument}.
     */
    private void handleVoidDocument(HttpServletRequest request, HttpServletResponse response) throws IOException {
        getDocumentController().handleVoidDocument(request, response);
    }

    // ------------------------------------------------------------------
    // Liên kết hợp đồng bán <-> mua ("đầu ra kéo theo đầu vào")
    // ------------------------------------------------------------------

    /**
     * Nối hợp đồng đang mở với một hợp đồng ở chiều ngược lại (UC-47).
     * Ủy quyền xử lý sang {@link ContractLinkController#handleLinkContract}.
     */
    private void handleLinkContract(HttpServletRequest request, HttpServletResponse response) throws IOException {
        getLinkController().handleLinkContract(request, response);
    }

    /**
     * Gỡ liên kết giữa hai hợp đồng (UC-47).
     * Ủy quyền xử lý sang {@link ContractLinkController#handleUnlinkContract}.
     */
    private void handleUnlinkContract(HttpServletRequest request, HttpServletResponse response) throws IOException {
        getLinkController().handleUnlinkContract(request, response);
    }

    private void handleAddPayment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        getPaymentController().handleAddPayment(request, response);
    }

    private void handleMarkPaid(HttpServletRequest request, HttpServletResponse response) throws IOException {
        getPaymentController().handleMarkPaid(request, response);
    }

    private void handleRemovePayment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        getPaymentController().handleRemovePayment(request, response);
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

    /**
     * @param keepUserId người đang phụ trách bản ghi đang sửa -- giữ trong
     *        dropdown kể cả khi họ đã đổi vai, xem EmployeeDAO.findActiveByRole.
     */
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
        // Hợp đồng giao cho Sales, không đổ cả Admin/Kỹ thuật vào ô
        // "người phụ trách".
        request.setAttribute("userList", employeeDAO.findActiveByRole(SALES_ROLE, keepUserId));
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
        getListController().listByEnterpriseAsJson(request, response);
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
        // Ngày ký KHÔNG đọc từ request: nó được đóng dấu lúc bấm Ký
        // (ContractDAO.changeProgressStatus). Form tạo không có ô này, nhưng
        // một POST nặn tay kèm signDate từng ghi được ngày đó xuống bản nháp,
        // và COALESCE lúc ký giữ nguyên nó thay vì lấy ngày ký thật. Hai luồng
        // sửa tự chép ngày ký từ bản ghi đang có.
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
        java.math.BigDecimal amount = MoneyVnd.parseOrNull(request.getParameter("contractValue"));
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

    /** BR-36: các trường bắt buộc phải có, và Ngày ký ≤ Ngày hiệu lực ≤ Ngày kết thúc. */
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
        // phải giữ BR-36: ký <= hiệu lực <= kết thúc.
        if (c.getSigningDate() != null && c.getEffectiveDate() != null
                && c.getSigningDate().after(c.getEffectiveDate())) {
            return false;
        }
        // Có cả hai thì vẫn giữ BR-36; thiếu một trong hai thì chưa có gì để so.
        return c.getEffectiveDate() == null || c.getEndDate() == null
                || !c.getEffectiveDate().after(c.getEndDate());
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

        public FilterChip(String label, String query, String color, boolean on, int count) {
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

    static List<Integer> provinceFiltersOf(HttpServletRequest request) {
        return ContractFilterHelper.provinceFiltersOf(request);
    }

    static String provinceQuery(List<Integer> provinceIds) {
        return ContractFilterHelper.provinceQuery(provinceIds);
    }

    static void setPeriodAttributes(HttpServletRequest request, Period period) {
        ContractFilterHelper.setPeriodAttributes(request, period);
    }

    // static vì provinceFiltersOf() -- hàm dùng chung cho cả CustomerController
    // -- cũng phải static và gọi tới nó.
    private static Integer parseIntOrNull(String value) {
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
     * form thêm hàng hoá -- bỏ dấu "." phân tách hàng nghìn kiểu Việt Nam trước khi
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
