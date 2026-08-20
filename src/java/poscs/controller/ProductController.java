package poscs.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import poscs.common.AccessControl;
import poscs.common.FileStorage;
import poscs.dao.ProductDAO;
import poscs.model.Product;

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

        List<Product> productList = productDAO.findAll(page, PAGE_SIZE, keyword, categoryFilter);
        int totalCount = productDAO.countAll(keyword, categoryFilter);
        int totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) PAGE_SIZE));

        request.setAttribute("productList", productList);
        request.setAttribute("categoryList", productDAO.findAllCategories());
        request.setAttribute("categoryCounts", productDAO.countByCategory());
        request.setAttribute("grandTotal", productDAO.countAll(null, null));
        request.setAttribute("currentPage", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalCount", totalCount);
        request.setAttribute("keyword", keyword);
        request.setAttribute("categoryFilter", categoryFilter);

        request.getRequestDispatcher(LIST_VIEW).forward(request, response);
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
