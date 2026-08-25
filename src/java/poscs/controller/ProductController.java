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
import org.apache.poi.ss.usermodel.Row;
import poscs.common.AccessControl;
import poscs.common.ExcelUtil;
import poscs.common.FileStorage;
import poscs.dao.ProductDAO;
import poscs.model.Product;
import poscs.model.ProductCategory;

/**
 * Controller cho toàn bộ chức năng sản phẩm (products). Điều hướng theo
 * tham số "action" -- role nào được thao tác gì xem PERMISSIONS.md, enforce
 * bằng AccessControl.requireFullAccess ở đầu mỗi hàm handleCreate/
 * handleUpdate/handleDelete (Sales/CSKH chỉ View only trên Product).
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

    private static final int PAGE_SIZE = 10;
    private static final String LIST_VIEW = "/jsp/technical/listProduct.jsp";
    private static final String DETAIL_VIEW = "/jsp/technical/viewdetailProduct.jsp";
    private static final String CREATE_VIEW = "/jsp/technical/addNewProduct.jsp";
    private static final String UPDATE_VIEW = "/jsp/technical/updateProduct.jsp";

    private static final String IMAGE_SUBFOLDER = "products/images";
    private static final String CATALOGUE_SUBFOLDER = "products/catalogues";
    private static final String IMPORT_VIEW = "/jsp/technical/importproduct.jsp";

    // Cột trong file mẫu nhập Excel (xem downloadImportTemplate/handleImportExcel).
    private static final String[] IMPORT_HEADERS = {"Tên sản phẩm*", "Mô tả", "Danh mục*"};
    private static final int COL_NAME = 0, COL_DESCRIPTION = 1, COL_CATEGORY = 2;

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
            case "importExcel":
                handleImportExcel(request, response);
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

        List<Product> productList = productDAO.findAll(page, PAGE_SIZE, keyword, categoryFilter);
        int totalCount = productDAO.countAll(keyword, categoryFilter);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));

        List<ProductCategory> categoryList = productDAO.findAllCategories();
        Map<Integer, List<ProductCategory>> childrenByParent = childrenByParent(categoryList);

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
        request.setAttribute("categoryFilter", categoryFilter);

        request.getRequestDispatcher(LIST_VIEW).forward(request, response);
    }

    /** Hiển thị form nhập Excel hàng loạt (chưa xử lý gì) -- nút "Nhập Excel" ở listProduct.jsp. */
    private void showImportForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
    }

    /** Sinh file mẫu .xlsx cho nhập sản phẩm, có dropdown Danh mục lấy đúng tên từ productcategories để tránh gõ sai. */
    private void downloadImportTemplate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<String> categoryNames = new ArrayList<>();
        for (ProductCategory c : productDAO.findAllCategories()) {
            categoryNames.add(c.getCategoryName());
        }
        Map<Integer, List<String>> dropdowns = new HashMap<>();
        dropdowns.put(COL_CATEGORY, categoryNames);
        ExcelUtil.writeTemplate(response, "mau_nhap_san_pham", IMPORT_HEADERS, dropdowns, 200);
    }

    /**
     * Nhập hàng loạt sản phẩm từ file .xlsx theo mẫu downloadImportTemplate().
     * Mỗi dòng độc lập -- 1 dòng lỗi không chặn các dòng còn lại. Danh mục
     * trong file là TÊN nên phải so khớp lại với productDAO.findAllCategories()
     * (rút gọn theo tên, bỏ khoảng trắng thừa/hoa-thường).
     */
    private void handleImportExcel(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.PRODUCT)) {
            return;
        }

        Part filePart = request.getPart("file");
        if (filePart == null || filePart.getSize() <= 0) {
            request.setAttribute("importError", "Vui lòng chọn file .xlsx để nhập.");
            request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
            return;
        }

        Map<String, Integer> categoryIdByName = new HashMap<>();
        for (ProductCategory c : productDAO.findAllCategories()) {
            categoryIdByName.put(normalize(c.getCategoryName()), c.getCategoryId());
        }

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

        String seedCode = productDAO.generateNextProductCode();
        if (seedCode == null) {
            request.setAttribute("importError", "Không sinh được mã sản phẩm -- có thể mất kết nối CSDL, hãy thử lại.");
            request.getRequestDispatcher(IMPORT_VIEW).forward(request, response);
            return;
        }

        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int rowNumber = 1; // dòng 1 là header trong file gốc
        // Sinh mã SP-xxxx 1 lần rồi tăng dần trong vòng lặp thay vì gọi lại
        // generateNextProductCode() (1 SELECT MAX riêng) cho từng dòng --
        // với vài trăm dòng, tránh vài trăm round-trip DB không cần thiết.
        String[] nextCode = {seedCode};
        for (Row row : rows) {
            rowNumber++;
            String rowError = importOneProductRow(row, rowNumber, categoryIdByName, nextCode);
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
     * @param nextCode mảng 1 phần tử giữ mã SP-xxxx sẽ dùng cho dòng này -- hàm tự
     * cập nhật lại phần tử này (dựa trên mã product_code thực sự vừa lưu
     * thành công, có thể khác nextCode[0] nếu insert() phải tự thử lại do
     * trùng mã) để dòng kế tiếp dùng, tránh phải đọc lại CSDL mỗi dòng.
     * @return null nếu insert thành công, ngược lại là mô tả lỗi kèm số dòng để hiển thị cho người dùng.
     */
    private String importOneProductRow(Row row, int rowNumber, Map<String, Integer> categoryIdByName, String[] nextCode) {
        String name = ExcelUtil.cellString(row, COL_NAME);
        String description = ExcelUtil.cellString(row, COL_DESCRIPTION);
        String categoryName = ExcelUtil.cellString(row, COL_CATEGORY);

        if (isBlank(name)) {
            return "Dòng " + rowNumber + ": thiếu tên sản phẩm.";
        }
        if (isBlank(categoryName)) {
            return "Dòng " + rowNumber + ": thiếu danh mục.";
        }
        Integer categoryId = categoryIdByName.get(normalize(categoryName));
        if (categoryId == null) {
            return "Dòng " + rowNumber + ": không tìm thấy danh mục \"" + categoryName + "\".";
        }

        Product p = new Product();
        p.setProductName(name);
        p.setDescription(emptyToNull(description));
        p.setCategoryId(categoryId);

        if (!isValidCommonFields(p)) {
            return "Dòng " + rowNumber + ": dữ liệu không hợp lệ.";
        }

        p.setProductCode(nextCode[0]);
        int newId = productDAO.insert(p);
        if (newId > 0) {
            // insert() có thể đã tự thử lại với mã khác nếu nextCode[0] bị trùng
            // (xem ProductDAO.insert) -- luôn tính mã kế tiếp từ mã thực sự vừa lưu.
            nextCode[0] = productDAO.nextProductCodeAfter(p.getProductCode());
            return null;
        }
        // Lưu thất bại hẳn (hết số lần thử lại, hoặc lỗi khác) -- đọc lại mã mới
        // nhất từ CSDL để đồng bộ lại trước khi tiếp tục các dòng sau; nếu chính
        // lần đọc lại này cũng lỗi (null), giữ nguyên nextCode[0] cũ thay vì để
        // null lọt sang dòng kế tiếp -- insert() ở dòng sau vẫn tự thử lại mã
        // khác nếu bị trùng.
        String resynced = productDAO.generateNextProductCode();
        if (resynced != null) {
            nextCode[0] = resynced;
        }
        return "Dòng " + rowNumber + ": lưu vào CSDL thất bại.";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
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
        request.setAttribute("categoryList", productDAO.findAllCategories());
        request.getRequestDispatcher(CREATE_VIEW).forward(request, response);
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        Product product = id != null ? productDAO.findById(id) : null;
        if (product == null) {
            response.sendRedirect(request.getContextPath() + "/product?error=notfound");
            return;
        }

        request.setAttribute("product", product);
        request.setAttribute("categoryList", productDAO.findAllCategories());
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

        p.setProductCode(productDAO.generateNextProductCode());
        int newId = productDAO.insert(p);
        if (newId <= 0) {
            response.sendRedirect(request.getContextPath() + "/product?action=new&error=create_failed");
            return;
        }

        saveNewImages(request, newId);
        saveNewCatalogues(request, newId);

        response.sendRedirect(request.getContextPath() + "/product?action=view&id=" + newId);
    }

    private void handleUpdate(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.PRODUCT)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("productId"));
        if (id == null) {
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

        boolean ok = productDAO.update(p);
        if (!ok) {
            response.sendRedirect(request.getContextPath() + "/product?action=edit&id=" + id + "&error=update_failed");
            return;
        }

        removeMarkedFiles(request, "removedImageIds", id, true);
        removeMarkedFiles(request, "removedCatalogueIds", id, false);
        saveNewImages(request, id);
        saveNewCatalogues(request, id);

        response.sendRedirect(request.getContextPath() + "/product?action=view&id=" + id);
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.PRODUCT)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        if (id == null) {
            response.sendRedirect(request.getContextPath() + "/product");
            return;
        }

        // Không cho xoá sản phẩm đang được tham chiếu trong contractproducts
        // (tránh để lại dòng contractproducts mồ côi hoặc hợp đồng cũ mất
        // thông tin sản phẩm đã bán).
        if (productDAO.isUsedInContracts(id)) {
            response.sendRedirect(request.getContextPath() + "/product?action=view&id=" + id + "&error=has_active_contracts");
            return;
        }

        productDAO.softDelete(id);
        response.sendRedirect(request.getContextPath() + "/product");
    }

    // ------------------------------------------------------------------
    // Helpers: upload
    // ------------------------------------------------------------------

    /** Lưu mọi file được chọn ở input "images" (name lặp lại vì có "multiple") vào productimages. */
    private void saveNewImages(HttpServletRequest request, int productId) throws ServletException, IOException {
        for (Part part : filePartsNamed(request, "images")) {
            String url = FileStorage.save(part, IMAGE_SUBFOLDER);
            if (url != null) {
                productDAO.addImage(productId, url);
            }
        }
    }

    /** Lưu mọi file được chọn ở input "catalogues" (name lặp lại vì có "multiple") vào productcatalogues. */
    private void saveNewCatalogues(HttpServletRequest request, int productId) throws ServletException, IOException {
        for (Part part : filePartsNamed(request, "catalogues")) {
            String url = FileStorage.save(part, CATALOGUE_SUBFOLDER);
            if (url != null) {
                productDAO.addCatalogue(productId, url, part.getSubmittedFileName());
            }
        }
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
     */
    private void removeMarkedFiles(HttpServletRequest request, String paramName, int productId, boolean isImage) {
        String raw = request.getParameter(paramName);
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        for (String token : raw.split(",")) {
            Integer fileId = parseIntOrNull(token);
            if (fileId == null) {
                continue;
            }
            if (isImage) {
                productDAO.deleteImage(fileId, productId);
            } else {
                productDAO.deleteCatalogue(fileId, productId);
            }
        }
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
