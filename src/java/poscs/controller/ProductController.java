package poscs.controller;

import java.io.IOException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.AccessControl;
import poscs.common.Logs;
import poscs.common.QueryStrings;
import poscs.dao.ProductDAO;
import poscs.model.Product;
import poscs.model.ProductCategory;
import poscs.service.ProductService;
import poscs.service.ServiceResult;
import poscs.validator.ProductValidator;

/**
 * Controller cho toàn bộ chức năng sản phẩm (UC-12, UC-13, UC-18, UC-19, UC-20).
 *
 * <p>Đóng vai trò lớp Presentation thuần túy:
 * <ul>
 *   <li>Kiểm tra phân quyền qua {@link AccessControl} (BR-22, NFR-10).</li>
 *   <li>Thu nhận, bóc tách tham số HTTP và tệp đính kèm multipart.</li>
 *   <li>Gọi thực thi nghiệp vụ qua {@link ProductService}.</li>
 *   <li>Thiết lập dữ liệu hiển thị (Request Attributes) và điều hướng (JSP/Redirect).</li>
 * </ul>
 *
 * <p>Quy tắc hiển thị:
 * <ul>
 *   <li>BR-12: Phân trang danh sách sản phẩm với kích thước mặc định 10.</li>
 *   <li>BR-14: Danh sách rỗng hiển thị thông báo trạng thái rỗng (MSG-023).</li>
 *   <li>BR-19: Chi tiết sản phẩm gồm: Thông tin, Hình ảnh, Catalogue, Hợp đồng sử dụng.</li>
 *   <li>BR-49: Xóa sản phẩm có hộp thoại xác nhận (MSG-054), chặn xóa khi còn hợp đồng (MSG-055).</li>
 *   <li>BR-53: Danh sách sản phẩm dạng card grid (hình ảnh, danh mục, mã, tên, ngày cập nhật).</li>
 * </ul>
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

    private ProductDAO productDAO = new ProductDAO();
    private ProductService productService;

    public ProductController() {
    }

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    public ProductController(ProductDAO productDAO, ProductService productService) {
        this.productDAO = productDAO;
        this.productService = productService;
    }

    ProductService getProductService() {
        if (productService != null) {
            return productService;
        }
        return new ProductService(productDAO, new ProductValidator());
    }

    void setProductService(ProductService productService) {
        this.productService = productService;
    }

    ProductDAO getProductDAO() {
        return productDAO;
    }

    void setProductDAO(ProductDAO productDAO) {
        this.productDAO = productDAO;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
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
                break;
        }
    }

    // ------------------------------------------------------------------
    // GET actions (UC-12, UC-13, BR-12, BR-14, BR-19, BR-53)
    // ------------------------------------------------------------------

    private void showList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int page = parseIntOrDefault(request.getParameter("page"), 1);
        String keyword = request.getParameter("keyword");
        Integer categoryFilter = parseIntOrNull(request.getParameter("categoryId"));

        ProductService svc = getProductService();
        List<ProductCategory> categoryList = svc.findAllCategories();
        Map<Integer, List<ProductCategory>> childrenByParent = svc.childrenByParent(categoryList);

        ProductService.ProductListPage listPage = svc.findProducts(page, PAGE_SIZE, keyword, categoryFilter);

        // BR-12, BR-14, BR-53: Thiết lập các thuộc tính hiển thị cho card grid và cây phân cấp
        request.setAttribute("productList", listPage.getProducts());
        request.setAttribute("categoryList", categoryList);
        request.setAttribute("rootCategories", svc.rootCategories(categoryList));
        request.setAttribute("childrenByParent", childrenByParent);
        request.setAttribute("expandedCategoryIds", svc.expandedCategoryIds(categoryList, categoryFilter));
        request.setAttribute("categoryCounts",
                svc.subtreeCategoryCounts(categoryList, childrenByParent, svc.countByCategory()));
        request.setAttribute("grandTotal", svc.grandTotal());
        request.setAttribute("currentPage", listPage.getPage());
        request.setAttribute("totalPages", listPage.getTotalPages());
        request.setAttribute("totalCount", listPage.getTotalCount());
        request.setAttribute("keyword", keyword);
        request.setAttribute("keywordParam", QueryStrings.param(keyword));
        request.setAttribute("categoryFilter", categoryFilter);

        request.getRequestDispatcher(LIST_VIEW).forward(request, response);
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer id = parseIntOrNull(request.getParameter("id"));
        if (id == null || id <= 0) {
            response.sendRedirect(request.getContextPath() + "/product?error=notfound");
            return;
        }

        // BR-19: Chi tiết sản phẩm gồm: Thông tin, Hình ảnh, Catalogue, Hợp đồng sử dụng
        ServiceResult<ProductService.ProductDetail> result = getProductService().getProductDetail(id);
        if (!result.isSuccess()) {
            response.sendRedirect(request.getContextPath() + "/product?error=notfound");
            return;
        }

        ProductService.ProductDetail detail = result.getData();
        request.setAttribute("product", detail.getProduct());
        request.setAttribute("contractList", detail.getContracts());

        request.getRequestDispatcher(DETAIL_VIEW).forward(request, response);
    }

    private void showCreateForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.PRODUCT)) {
            return;
        }
        ProductService svc = getProductService();
        request.setAttribute("categoryOptions", svc.categoryOptions(svc.findAllCategories(), null));
        request.getRequestDispatcher(CREATE_VIEW).forward(request, response);
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.PRODUCT)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        if (id == null || id <= 0) {
            response.sendRedirect(request.getContextPath() + "/product?error=notfound");
            return;
        }

        ProductService svc = getProductService();
        Product product = svc.findById(id);
        if (product == null) {
            response.sendRedirect(request.getContextPath() + "/product?error=notfound");
            return;
        }

        request.setAttribute("product", product);
        request.setAttribute("categoryOptions",
                svc.categoryOptions(svc.findAllCategories(), product.getCategoryId()));
        request.getRequestDispatcher(UPDATE_VIEW).forward(request, response);
    }

    // ------------------------------------------------------------------
    // POST actions (UC-18, UC-19, UC-20, BR-48, BR-49, BR-54, BR-55)
    // ------------------------------------------------------------------

    private void handleCreate(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.PRODUCT)) {
            return;
        }
        Product p = extractProductFromRequest(request, null);
        List<Part> imageParts = filePartsNamed(request, "images");
        List<Part> catalogueParts = filePartsNamed(request, "catalogues");

        ServiceResult<ProductService.ProductSaveResult> result =
                getProductService().createProduct(p, imageParts, catalogueParts);

        if (!result.isSuccess()) {
            String errorCode = result.getMessage();
            if ("create_failed".equals(errorCode)) {
                LOG.warn("Tao san pham that bai (actor={}, productCode={})", Logs.actor(request), p.getProductCode());
            }
            response.sendRedirect(request.getContextPath() + "/product?action=new&error=" + errorCode);
            return;
        }

        ProductService.ProductSaveResult saveResult = result.getData();
        if (saveResult.hasFileErrors()) {
            LOG.warn("Tep san pham chua luu duoc (actor={}, productId={}, chuaThem={})",
                    Logs.actor(request), saveResult.getProductId(), saveResult.getNotAdded());
        }
        response.sendRedirect(request.getContextPath() + "/product?action=view&id=" + saveResult.getProductId()
                + ProductService.filesNotSavedQuery(0, saveResult.getNotAdded()));
    }

    private void handleUpdate(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.PRODUCT)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("productId"));
        if (id == null || id <= 0) {
            response.sendRedirect(request.getContextPath() + "/product?error=notfound");
            return;
        }

        Product p = extractProductFromRequest(request, id);
        String removedImageIds = request.getParameter("removedImageIds");
        String removedCatalogueIds = request.getParameter("removedCatalogueIds");
        List<Part> imageParts = filePartsNamed(request, "images");
        List<Part> catalogueParts = filePartsNamed(request, "catalogues");

        ServiceResult<ProductService.ProductSaveResult> result =
                getProductService().updateProduct(p, removedImageIds, removedCatalogueIds, imageParts, catalogueParts);

        if (!result.isSuccess()) {
            handleUpdateError(request, response, id, result.getMessage());
            return;
        }

        ProductService.ProductSaveResult saveResult = result.getData();
        if (saveResult.hasFileErrors()) {
            LOG.warn("Tep san pham chua luu duoc (actor={}, productId={}, chuaGo={}, chuaThem={})",
                    Logs.actor(request), id, saveResult.getNotRemoved(), saveResult.getNotAdded());
        }
        response.sendRedirect(request.getContextPath() + "/product?action=view&id=" + id
                + ProductService.filesNotSavedQuery(saveResult.getNotRemoved(), saveResult.getNotAdded()));
    }

    private void handleUpdateError(HttpServletRequest request, HttpServletResponse response,
            int id, String errorCode) throws IOException {
        if ("notfound".equals(errorCode)) {
            response.sendRedirect(request.getContextPath() + "/product?error=notfound");
        } else {
            if ("update_failed".equals(errorCode)) {
                LOG.warn("Cap nhat san pham that bai (actor={}, productId={})", Logs.actor(request), id);
            }
            response.sendRedirect(request.getContextPath() + "/product?action=edit&id=" + id + "&error=" + errorCode);
        }
    }

    private Product extractProductFromRequest(HttpServletRequest request, Integer id) {
        Product p = new Product();
        if (id != null) {
            p.setProductId(id);
        }
        p.setProductName(emptyToNull(request.getParameter("productName")));
        p.setDescription(emptyToNull(request.getParameter("description")));
        Integer categoryId = parseIntOrNull(request.getParameter("categoryId"));
        if (categoryId != null) {
            p.setCategoryId(categoryId);
        }
        return p;
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.PRODUCT)) {
            return;
        }
        Integer id = parseIntOrNull(request.getParameter("id"));
        if (id == null || id <= 0) {
            response.sendRedirect(request.getContextPath() + "/product?error=notfound");
            return;
        }

        ServiceResult<Void> result = getProductService().deleteProduct(id);
        if (!result.isSuccess()) {
            String errorCode = result.getMessage();
            if ("notfound".equals(errorCode)) {
                response.sendRedirect(request.getContextPath() + "/product?error=notfound");
            } else {
                if ("delete_failed".equals(errorCode)) {
                    LOG.warn("Xoa san pham that bai (actor={}, productId={})", Logs.actor(request), id);
                }
                response.sendRedirect(request.getContextPath() + "/product?action=view&id=" + id + "&error=" + errorCode);
            }
            return;
        }
        response.sendRedirect(request.getContextPath() + "/product");
    }

    // ------------------------------------------------------------------
    // Presentation Helpers
    // ------------------------------------------------------------------

    private List<Part> filePartsNamed(HttpServletRequest request, String name) throws ServletException, IOException {
        List<Part> result = new ArrayList<>();
        for (Part part : request.getParts()) {
            if (name.equals(part.getName()) && part.getSize() > 0) {
                result.add(part);
            }
        }
        return result;
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
