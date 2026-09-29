package poscs.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.QueryStrings;
import poscs.common.AccessControl;
import poscs.common.FileStorage;
import poscs.common.Logs;
import poscs.dao.ProductDAO;
import poscs.model.Product;
import poscs.model.ProductCategory;

/**
 * Controller cho toàn bộ chức năng sản phẩm (products). Điều hướng theo
 * tham số "action" -- role nào được thao tác gì xem PERMISSIONS.md, enforce
 * bằng AccessControl.requireFullAccess ở đầu mỗi hàm handleCreate/
 * handleUpdate/handleDelete (Sales chỉ View only trên Product -- CSKH đã gộp
 * vào Sales, xem PERMISSIONS.md).
 *
 * addNewProduct.jsp/updateProduct.jsp gửi lên dạng multipart/form-data (ảnh +
 * catalogue tải từ máy, có thể chọn nhiều file) -- @MultipartConfig là bắt
 * buộc, nếu không request.getParameter(...) sẽ trả về null cho MỌI trường
 * (kể cả text), không chỉ riêng các trường file (xem AuthenticationController
 * cho tiền lệ). Giới hạn kích thước rộng rãi cho catalogue PDF nhưng vẫn có
 * giới hạn -- không để trống như AuthenticationController vì ở đó file chưa
 * thực sự được lưu, còn ở đây thì có.
 */
@WebServlet(name = "ProductController", urlPatterns = {"/product"})
@MultipartConfig(maxFileSize = 20 * 1024 * 1024, maxRequestSize = 100 * 1024 * 1024, fileSizeThreshold = 1024 * 1024)
public class ProductController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ProductController.class);

    private static final int PAGE_SIZE = 10;
    private static final String LIST_VIEW = "/jsp/technical/listProduct.jsp";
    private static final String DETAIL_VIEW = "/jsp/technical/viewdetailProduct.jsp";
    private static final String CREATE_VIEW = "/jsp/technical/addNewProduct.jsp";
    private static final String UPDATE_VIEW = "/jsp/technical/updateProduct.jsp";

    private static final String IMAGE_SUBFOLDER = "products/images";
    private static final String CATALOGUE_SUBFOLDER = "products/catalogues";

    private final ProductDAO productDAO = new ProductDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Cho JSP biết người đang xem có quyền Full trên tài nguyên này không,
        // để ẩn các nút hành động không dùng được (Tạo/Sửa/Xoá/Nhập/Xuất) thay
        // vì để người ta bấm vào rồi nhận 403. Đây CHỈ là lớp trình bày --
        // chặn thật nằm ở AccessControl.requireFullAccess trong doPost và ở
        // đầu mỗi trang form bên dưới (gõ thẳng URL cũng không vào được).
        request.setAttribute("canManage",
                AccessControl.hasFullAccess(request, AccessControl.Resource.PRODUCT));
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
                response.sendRedirect(request.getContextPath() + "/product");
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
        Integer categoryFilter = parseIntOrNull(request.getParameter("categoryId"));

        List<ProductCategory> categoryList = productDAO.findAllCategories();
        Map<Integer, List<ProductCategory>> childrenByParent = childrenByParent(categoryList);

        // categoryFilter có thể là danh mục CHA/GIỮA cây (vd "Năng lượng tái
        // tạo") -- sản phẩm CHỈ gắn ở danh mục LÁ (xem subtreeCategoryCounts
        // bên dưới), nên lọc phải mở rộng xuống hết hậu duệ. Lọc đúng bằng
        // category_id đã chọn thì luôn ra danh sách RỖNG dù panel bên cạnh
        // hiện đúng tổng số > 0 -- hai chỗ nói hai chuyện khác nhau.
        List<Integer> categoryIdsToQuery = categoryFilter != null
                ? subtreeIds(categoryFilter, childrenByParent) : null;

        List<Product> productList = productDAO.findAll(page, PAGE_SIZE, keyword, categoryIdsToQuery);
        int totalCount = productDAO.countAll(keyword, categoryIdsToQuery);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));

        request.setAttribute("productList", productList);
        request.setAttribute("categoryList", categoryList);
        request.setAttribute("rootCategories", rootCategories(categoryList));
        request.setAttribute("childrenByParent", childrenByParent);
        request.setAttribute("expandedCategoryIds", expandedCategoryIds(categoryList, categoryFilter));
        request.setAttribute("categoryCounts",
                subtreeCategoryCounts(categoryList, childrenByParent, productDAO.countByCategory()));
        request.setAttribute("grandTotal", productDAO.countAll(null, null));
        request.setAttribute("currentPage", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalCount", totalCount);
        request.setAttribute("keyword", keyword);
        // Bản đã mã hoá URL cho các link phân trang / lọc / xuất -- xem QueryStrings.param.
        request.setAttribute("keywordParam", QueryStrings.param(keyword));
        request.setAttribute("categoryFilter", categoryFilter);

        request.getRequestDispatcher(LIST_VIEW).forward(request, response);
    }

    // ------------------------------------------------------------------
    // Helpers: cây danh mục 3 cấp cho panel "Danh mục sản phẩm" (accordion)
    // ------------------------------------------------------------------

    /** Danh mục cấp 1 (không có cha), theo đúng thứ tự trả về từ findAllCategories(). */
    private List<ProductCategory> rootCategories(List<ProductCategory> categoryList) {
        List<ProductCategory> result = new ArrayList<>();
        for (ProductCategory c : categoryList) {
            if (c.getParentCategoryId() == null) {
                result.add(c);
            }
        }
        return result;
    }

    /** category_id cha -> danh sách danh mục con trực tiếp, dùng để đổ từng nhánh accordion (cấp 2, cấp 3). */
    private Map<Integer, List<ProductCategory>> childrenByParent(List<ProductCategory> categoryList) {
        Map<Integer, List<ProductCategory>> result = new LinkedHashMap<>();
        for (ProductCategory c : categoryList) {
            if (c.getParentCategoryId() != null) {
                result.computeIfAbsent(c.getParentCategoryId(), k -> new ArrayList<>()).add(c);
            }
        }
        return result;
    }

    /**
     * Số sản phẩm hiển thị cạnh mỗi danh mục trong panel = tổng số sản phẩm của
     * chính danh mục đó CỘNG DỒN toàn bộ danh mục con/cháu (không chỉ số sản
     * phẩm gắn trực tiếp). Danh mục cha/giữa cây (Năng lượng tái tạo, Hạ tầng
     * viễn thông, Thiết bị vô tuyến...) không có sản phẩm nào gắn trực tiếp --
     * mọi sản phẩm đều nằm ở danh mục lá -- nên nếu không cộng dồn, các danh
     * mục cha sẽ luôn hiện (0) dù bên trong có hàng chục sản phẩm.
     */
    private Map<Integer, Integer> subtreeCategoryCounts(List<ProductCategory> categoryList,
            Map<Integer, List<ProductCategory>> childrenByParent, Map<Integer, Integer> directCounts) {
        Map<Integer, Integer> result = new HashMap<>();
        for (ProductCategory c : categoryList) {
            result.put(c.getCategoryId(), subtreeCount(c.getCategoryId(), childrenByParent, directCounts));
        }
        return result;
    }

    /** Tổng số sản phẩm của 1 danh mục + toàn bộ hậu duệ (đệ quy theo childrenByParent). */
    private int subtreeCount(int categoryId, Map<Integer, List<ProductCategory>> childrenByParent,
            Map<Integer, Integer> directCounts) {
        int total = directCounts.getOrDefault(categoryId, 0);
        List<ProductCategory> children = childrenByParent.get(categoryId);
        if (children != null) {
            for (ProductCategory child : children) {
                total += subtreeCount(child.getCategoryId(), childrenByParent, directCounts);
            }
        }
        return total;
    }

    /**
     * categoryId + mọi hậu duệ (đệ quy theo childrenByParent), dùng để lọc
     * danh sách sản phẩm khi chọn 1 danh mục -- cùng logic cộng dồn với
     * {@link #subtreeCount}, chỉ khác là trả về tập id thay vì tổng số.
     */
    private List<Integer> subtreeIds(int categoryId, Map<Integer, List<ProductCategory>> childrenByParent) {
        List<Integer> result = new ArrayList<>();
        result.add(categoryId);
        List<ProductCategory> children = childrenByParent.get(categoryId);
        if (children != null) {
            for (ProductCategory child : children) {
                result.addAll(subtreeIds(child.getCategoryId(), childrenByParent));
            }
        }
        return result;
    }

    /**
     * category_id của danh mục đang được lọc (nếu có) cùng toàn bộ tổ tiên của nó,
     * để JSP biết nhánh accordion nào cần mở sẵn (show) thay vì để người dùng phải
     * tự bấm mở từng cấp mới thấy được mục đang chọn.
     */
    private Set<Integer> expandedCategoryIds(List<ProductCategory> categoryList, Integer categoryFilter) {
        Set<Integer> result = new HashSet<>();
        if (categoryFilter == null) {
            return result;
        }
        Map<Integer, ProductCategory> byId = new HashMap<>();
        for (ProductCategory c : categoryList) {
            byId.put(c.getCategoryId(), c);
        }
        Integer current = categoryFilter;
        while (current != null && result.add(current)) {
            ProductCategory c = byId.get(current);
            current = (c != null) ? c.getParentCategoryId() : null;
        }
        return result;
    }

    /**
     * Ô "Danh mục" của form Thêm / Sửa: CHỈ danh mục cuối (lá), mỗi mục ghi kèm
     * cả nhánh -- "CNTT &amp; IOT › LoRa › Sensors" -- theo đúng thứ tự của cây
     * ở panel danh sách. handleCreate/handleUpdate cũng kiểm bằng chính danh
     * sách này, nên form đưa ra gì thì server nhận đúng thứ đó.
     *
     * <p>Trước đây ô này đổ phẳng cả 33 danh mục, lẫn danh mục cha: không nhìn
     * ra mục nào thuộc nhánh nào, và chọn được "Năng lượng tái tạo" dù mọi sản
     * phẩm đều nằm ở danh mục lá (xem {@link #subtreeCategoryCounts}).
     *
     * @param keepCategoryId danh mục ĐANG LƯU của sản phẩm (form Sửa), null ở
     *        form Thêm. Lỡ là danh mục cha (dữ liệu cũ) thì vẫn giữ trong danh
     *        sách -- không thì mở form lên ô trống, bấm lưu là báo lỗi dù người
     *        dùng không đụng tới ô này.
     */
    private Map<Integer, String> categoryOptions(List<ProductCategory> categoryList, Integer keepCategoryId) {
        Map<Integer, List<ProductCategory>> children = childrenByParent(categoryList);
        Set<Integer> ids = new HashSet<>();
        for (ProductCategory c : categoryList) {
            ids.add(c.getCategoryId());
        }
        Map<Integer, String> result = new LinkedHashMap<>();
        for (ProductCategory c : categoryList) {
            // Gốc = không có cha, hoặc cha không còn trong danh sách (đã xoá):
            // danh mục đó vẫn chọn được như trước, chỉ không có tên nhánh.
            if (c.getParentCategoryId() == null || !ids.contains(c.getParentCategoryId())) {
                addLeafOptions(c, "", children, keepCategoryId, result);
            }
        }
        return result;
    }

    private void addLeafOptions(ProductCategory category, String branch,
            Map<Integer, List<ProductCategory>> children, Integer keepCategoryId, Map<Integer, String> result) {
        String label = branch + category.getCategoryName();
        List<ProductCategory> kids = children.get(category.getCategoryId());
        if (kids == null || Integer.valueOf(category.getCategoryId()).equals(keepCategoryId)) {
            result.put(category.getCategoryId(), label);
        }
        if (kids != null) {
            for (ProductCategory kid : kids) {
                addLeafOptions(kid, label + " › ", children, keepCategoryId, result);
            }
        }
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        Product product = id != null ? productDAO.findById(id) : null;
        if (product == null) {
            response.sendRedirect(request.getContextPath() + "/product?error=notfound");
            return;
        }

        request.setAttribute("product", product);
        request.setAttribute("contractList", productDAO.findContractsByProductId(id));

        request.getRequestDispatcher(DETAIL_VIEW).forward(request, response);
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Trang form cũng là thao tác quản trị: vai trò chỉ-xem không được
        // vào đây, dù nút bấm đã ẩn ở danh sách (xem PERMISSIONS.md).
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.PRODUCT)) {
            return;
        }
        request.setAttribute("categoryOptions", categoryOptions(productDAO.findAllCategories(), null));
        request.getRequestDispatcher(CREATE_VIEW).forward(request, response);
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Trang form cũng là thao tác quản trị: vai trò chỉ-xem không được
        // vào đây, dù nút bấm đã ẩn ở danh sách (xem PERMISSIONS.md).
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.PRODUCT)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        Product product = id != null ? productDAO.findById(id) : null;
        if (product == null) {
            response.sendRedirect(request.getContextPath() + "/product?error=notfound");
            return;
        }

        request.setAttribute("product", product);
        request.setAttribute("categoryOptions",
                categoryOptions(productDAO.findAllCategories(), product.getCategoryId()));
        request.getRequestDispatcher(UPDATE_VIEW).forward(request, response);
    }

    // ------------------------------------------------------------------
    // POST actions
    // ------------------------------------------------------------------

    private void handleCreate(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.PRODUCT)) {
            return;
        }
        Product p = new Product();
        p.setProductName(emptyToNull(request.getParameter("productName")));
        p.setDescription(emptyToNull(request.getParameter("description")));

        Integer categoryId = parseIntOrNull(request.getParameter("categoryId"));
        if (categoryId != null) {
            p.setCategoryId(categoryId);
        }

        if (!isValidCommonFields(p)) {
            response.sendRedirect(request.getContextPath() + "/product?action=new&error=invalid");
            return;
        }
        // Chỉ nhận đúng các mục ô chọn đưa ra: danh mục cuối còn hiệu lực. Danh
        // mục cha, danh mục đã xoá (form mở từ trước) hay request nặn tay đều bị
        // từ chối ở đây, không thì sản phẩm lọt vào giữa cây.
        if (!categoryOptions(productDAO.findAllCategories(), null).containsKey(p.getCategoryId())) {
            response.sendRedirect(request.getContextPath() + "/product?action=new&error=invalid_category");
            return;
        }

        if (!filesAreAcceptable(request, response, request.getContextPath() + "/product?action=new")) {
            return;
        }

        p.setProductCode(productDAO.generateNextProductCode());
        int newId = productDAO.insert(p);
        if (newId <= 0) {
            LOG.warn("Tao san pham that bai (actor={}, productCode={})", Logs.actor(request), p.getProductCode());
            response.sendRedirect(request.getContextPath() + "/product?action=new&error=create_failed");
            return;
        }

        int notAdded = saveNewImages(request, newId) + saveNewCatalogues(request, newId);

        response.sendRedirect(request.getContextPath() + "/product?action=view&id=" + newId
                + filesNotSavedQuery(request, newId, 0, notAdded));
    }

    private void handleUpdate(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.PRODUCT)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("productId"));
        // Kiểm tồn tại trước khi kiểm dữ liệu -- xem ghi chú cùng loại ở
        // ContractController.handleUpdate.
        Product existing = id != null ? productDAO.findById(id) : null;
        if (existing == null) {
            response.sendRedirect(request.getContextPath() + "/product?error=notfound");
            return;
        }

        Product p = new Product();
        p.setProductId(id);
        p.setProductName(emptyToNull(request.getParameter("productName")));
        p.setDescription(emptyToNull(request.getParameter("description")));

        Integer categoryId = parseIntOrNull(request.getParameter("categoryId"));
        if (categoryId != null) {
            p.setCategoryId(categoryId);
        }

        if (!isValidCommonFields(p)) {
            response.sendRedirect(request.getContextPath() + "/product?action=edit&id=" + id + "&error=invalid");
            return;
        }
        // Như handleCreate, cộng thêm danh mục đang lưu của sản phẩm -- form Sửa
        // cũng giữ nó trong ô chọn (xem categoryOptions).
        if (!categoryOptions(productDAO.findAllCategories(), existing.getCategoryId()).containsKey(p.getCategoryId())) {
            response.sendRedirect(request.getContextPath() + "/product?action=edit&id=" + id + "&error=invalid_category");
            return;
        }

        if (!filesAreAcceptable(request, response, request.getContextPath() + "/product?action=edit&id=" + id)) {
            return;
        }

        boolean ok = productDAO.update(p);
        if (!ok) {
            LOG.warn("Cap nhat san pham that bai (actor={}, productId={})", Logs.actor(request), id);
            response.sendRedirect(request.getContextPath() + "/product?action=edit&id=" + id + "&error=update_failed");
            return;
        }

        int notRemoved = removeMarkedFiles(request, "removedImageIds", id, existing, true)
                + removeMarkedFiles(request, "removedCatalogueIds", id, existing, false);
        int notAdded = saveNewImages(request, id) + saveNewCatalogues(request, id);

        response.sendRedirect(request.getContextPath() + "/product?action=view&id=" + id
                + filesNotSavedQuery(request, id, notRemoved, notAdded));
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.PRODUCT)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        // Kiểm tồn tại trước ràng buộc nghiệp vụ -- xem ghi chú cùng loại ở
        // CustomerController.handleDelete.
        if (id == null || productDAO.findById(id) == null) {
            response.sendRedirect(request.getContextPath() + "/product?error=notfound");
            return;
        }

        // Không cho xoá sản phẩm đang được tham chiếu trong contractproducts
        // (tránh để lại dòng contractproducts mồ côi hoặc hợp đồng cũ mất
        // thông tin sản phẩm đã bán).
        if (productDAO.isUsedInContracts(id)) {
            response.sendRedirect(request.getContextPath() + "/product?action=view&id=" + id + "&error=has_active_contracts");
            return;
        }

        // Câu UPDATE hỏng thì sản phẩm vẫn còn nguyên: về trang chi tiết của nó
        // kèm lỗi, không về danh sách như đã xoá xong -- xem ghi chú cùng loại ở
        // TechnicalSupportTicketController.handleDelete.
        if (!productDAO.softDelete(id)) {
            LOG.warn("Xoa san pham that bai (actor={}, productId={})", Logs.actor(request), id);
            response.sendRedirect(request.getContextPath() + "/product?action=view&id=" + id + "&error=delete_failed");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/product");
    }

    // ------------------------------------------------------------------
    // Helpers: upload
    // ------------------------------------------------------------------

    /**
     * Lưu mọi file được chọn ở input "images" (name lặp lại vì có "multiple") vào
     * productimages. Trả về số tệp KHÔNG lưu được (ghi đĩa hỏng, hoặc ghi CSDL
     * hỏng) -- trước đây những tệp đó rơi mất lặng lẽ, trang vẫn báo lưu xong.
     */
    private int saveNewImages(HttpServletRequest request, int productId) throws ServletException, IOException {
        int failed = 0;
        for (Part part : filePartsNamed(request, "images")) {
            String url = FileStorage.save(part, IMAGE_SUBFOLDER, FileStorage.IMAGE_EXTENSIONS);
            if (url == null || productDAO.addImage(productId, url) <= 0) {
                failed++;
            }
        }
        return failed;
    }

    /** Như saveNewImages, cho input "catalogues" và bảng productcatalogues. */
    private int saveNewCatalogues(HttpServletRequest request, int productId) throws ServletException, IOException {
        int failed = 0;
        for (Part part : filePartsNamed(request, "catalogues")) {
            String url = FileStorage.save(part, CATALOGUE_SUBFOLDER, FileStorage.DOCUMENT_EXTENSIONS);
            if (url == null || productDAO.addCatalogue(productId, url, part.getSubmittedFileName()) <= 0) {
                failed++;
            }
        }
        return failed;
    }

    /**
     * Đuôi query báo tệp chưa lưu được, "" nếu không có. Thông tin sản phẩm đã
     * lưu rồi nên vẫn về trang chi tiết -- chỉ kèm thêm số tệp để trang nói rõ
     * phần nào chưa xong, người dùng mở Sửa làm lại đúng phần đó.
     */
    private String filesNotSavedQuery(HttpServletRequest request, int productId, int notRemoved, int notAdded) {
        if (notRemoved == 0 && notAdded == 0) {
            return "";
        }
        LOG.warn("Tep san pham chua luu duoc (actor={}, productId={}, chuaGo={}, chuaThem={})",
                Logs.actor(request), productId, notRemoved, notAdded);
        return "&error=files_not_saved&notRemoved=" + notRemoved + "&notAdded=" + notAdded;
    }

    /**
     * Kiểm mọi file người dùng vừa chọn TRƯỚC khi đụng tới CSDL. Trả về false
     * (và đã tự redirect kèm lỗi) nếu có file sai loại.
     *
     * Phải chạy trước insert/update: saveNewImages() chạy sau khi sản phẩm đã
     * được ghi, nên nếu để nó tự bỏ qua file sai loại thì người dùng nhận về
     * một sản phẩm đã tạo nhưng thiếu ảnh, không có lời giải thích nào.
     */
    private boolean filesAreAcceptable(HttpServletRequest request, HttpServletResponse response,
            String redirectBase) throws ServletException, IOException {
        for (Part part : filePartsNamed(request, "images")) {
            if (!FileStorage.isAcceptable(part, FileStorage.IMAGE_EXTENSIONS)) {
                response.sendRedirect(redirectBase + "&error=invalid_image_type");
                return false;
            }
        }
        for (Part part : filePartsNamed(request, "catalogues")) {
            if (!FileStorage.isAcceptable(part, FileStorage.DOCUMENT_EXTENSIONS)) {
                response.sendRedirect(redirectBase + "&error=invalid_catalogue_type");
                return false;
            }
        }
        return true;
    }

    /** request.getParts() lọc theo tên field + bỏ qua part rỗng (input file để trống vẫn gửi lên 1 part size=0). */
    private List<Part> filePartsNamed(HttpServletRequest request, String name) throws ServletException, IOException {
        List<Part> result = new ArrayList<>();
        for (Part part : request.getParts()) {
            if (name.equals(part.getName()) && part.getSize() > 0) {
                result.add(part);
            }
        }
        return result;
    }

    /**
     * Xoá các ảnh/catalogue mà người dùng bấm "x" ở form sửa -- JS gộp id vào
     * 1 hidden input dạng CSV (vd "3,7,12") thay vì tự submit xoá ngay, để
     * việc xoá chỉ thật sự có hiệu lực khi bấm "Lưu thay đổi" (khớp với thao
     * tác "Hủy" ở form vẫn bỏ được các lựa chọn xoá đó).
     *
     * <p>Trả về số tệp KHÔNG gỡ được. Lệnh xoá trả false vì hai lẽ: CSDL hỏng,
     * hoặc tệp đã không còn (gỡ ở tab khác từ trước, id rác). Chỉ lẽ thứ nhất
     * là lỗi, nên đối chiếu với danh sách tệp của sản phẩm đọc ở đầu request:
     * tệp còn trong đó mà xoá không được mới tính.
     */
    private int removeMarkedFiles(HttpServletRequest request, String paramName, int productId, Product product,
            boolean isImage) {
        String raw = request.getParameter(paramName);
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        Set<Integer> present = new HashSet<>();
        if (isImage && product.getImages() != null) {
            product.getImages().forEach(img -> present.add(img.getImageId()));
        } else if (!isImage && product.getCatalogues() != null) {
            product.getCatalogues().forEach(cat -> present.add(cat.getCatalogueId()));
        }
        int failed = 0;
        for (String token : raw.split(",")) {
            Integer fileId = parseIntOrNull(token);
            if (fileId == null) {
                continue;
            }
            boolean deleted = isImage
                    ? productDAO.deleteImage(fileId, productId)
                    : productDAO.deleteCatalogue(fileId, productId);
            if (!deleted && present.contains(fileId)) {
                failed++;
            }
        }
        return failed;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Trường bắt buộc: tên sản phẩm + danh mục hợp lệ. Khớp với validate phía
     * client ở addNewProduct.jsp/updateProduct.jsp -- trước đây chỉ có ở
     * client nên có thể bị bypass bằng cách POST thẳng.
     */
    private boolean isValidCommonFields(Product p) {
        return !isBlank(p.getProductName()) && p.getCategoryId() > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
