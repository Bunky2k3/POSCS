package poscs.controller;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import poscs.common.FileStorage;
import poscs.dao.ProductDAO;
import poscs.model.Product;
import poscs.model.ProductCategory;
import poscs.model.Role;
import poscs.model.User;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cho ProductController -- trọng tâm là cây danh mục 3 cấp (accordion
 * panel ở listProduct.jsp): rootCategories/childrenByParent/
 * subtreeCategoryCounts (cộng dồn đệ quy số sản phẩm của danh mục cha từ
 * toàn bộ hậu duệ, vì mọi sản phẩm chỉ gắn ở danh mục LÁ)/expandedCategoryIds
 * (đi ngược lên tổ tiên của danh mục đang lọc) -- đều là hàm private nên test
 * gián tiếp qua showList (doGet action=list) rồi kiểm tra request attribute.
 * Cộng thêm CRUD cơ bản + xoá file đính kèm đã đánh dấu ở form sửa. JUnit 4 +
 * Mockito, xem CustomerControllerTest.
 */
public class ProductControllerTest {

    private static final String CONTEXT_PATH = "/POSCS";

    private ProductController controller;
    private ProductDAO productDAO;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @Before
    public void setUp() throws Exception {
        controller = new ProductController();
        productDAO = mock(ProductDAO.class);
        setField(controller, "productDAO", productDAO);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(request.getContextPath()).thenReturn(CONTEXT_PATH);

        session = mock(HttpSession.class);
        User user = new User();
        user.setUserId(99);
        user.setRole(new Role(1, "Kỹ thuật")); // Full access trên PRODUCT, xem PERMISSIONS.md
        when(session.getAttribute("currentUser")).thenReturn(user);
        when(request.getSession(false)).thenReturn(session);

        // showList luôn chạy tới cuối và forward -- stub sẵn để các test không
        // liên quan tới cây danh mục khỏi phải lo NPE khi vô tình đi qua nhánh này.
        when(request.getRequestDispatcher(anyString())).thenReturn(mock(RequestDispatcher.class));
        when(request.getParts()).thenReturn(Collections.emptyList());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static ProductCategory category(int id, String name, Integer parentId) {
        ProductCategory c = new ProductCategory();
        c.setCategoryId(id);
        c.setCategoryName(name);
        c.setParentCategoryId(parentId);
        return c;
    }

    // ------------------------------------------------------------------
    // GET ?action=list -- cây danh mục 3 cấp
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void showList_buildsThreeLevelCategoryTreeWithRolledUpCounts() throws Exception {
        // Cấp 1: Viễn thông(1) ; Cấp 2: Hạ tầng(2, cha=1) ; Cấp 3: Cáp quang(3, cha=2), Ăng-ten(4, cha=2)
        // Chỉ danh mục LÁ (3, 4) có sản phẩm gắn trực tiếp -- danh mục cha (1, 2) phải CỘNG DỒN từ con/cháu.
        List<ProductCategory> categories = Arrays.asList(
                category(1, "Viễn thông", null),
                category(2, "Hạ tầng", 1),
                category(3, "Cáp quang", 2),
                category(4, "Ăng-ten", 2)
        );
        when(productDAO.findAllCategories()).thenReturn(categories);
        Map<Integer, Integer> directCounts = Map.of(3, 5, 4, 2); // (1) và (2) không có count trực tiếp nào cả
        when(productDAO.countByCategory()).thenReturn(directCounts);
        when(request.getParameter("categoryId")).thenReturn("3"); // đang lọc theo danh mục lá (3)

        controller.doGet(request, response);

        verify(request).setAttribute(eq("rootCategories"), argThat((List<ProductCategory> l) ->
                l.size() == 1 && l.get(0).getCategoryId() == 1));

        verify(request).setAttribute(eq("childrenByParent"), argThat((Map<Integer, List<ProductCategory>> m) ->
                m.get(1).size() == 1 && m.get(1).get(0).getCategoryId() == 2
                        && m.get(2).size() == 2));

        verify(request).setAttribute(eq("categoryCounts"), argThat((Map<Integer, Integer> counts) ->
                counts.get(1) == 7   // 1 = tổng toàn bộ hậu duệ (2->3,4) = 5+2 = 7
                        && counts.get(2) == 7 // 2 = con trực tiếp (3,4) = 5+2 = 7
                        && counts.get(3) == 5 // 3 = lá, count trực tiếp
                        && counts.get(4) == 2));

        // Đang lọc theo (3) -- phải mở sẵn cả tổ tiên (2) và (1), không chỉ chính nó.
        verify(request).setAttribute(eq("expandedCategoryIds"), argThat((Set<Integer> s) ->
                s.containsAll(Arrays.asList(1, 2, 3)) && s.size() == 3));
    }

    @Test
    public void showList_noFilter_expandsNoCategoryByDefault() throws Exception {
        when(productDAO.findAllCategories()).thenReturn(Collections.emptyList());
        when(productDAO.countByCategory()).thenReturn(Collections.emptyMap());
        when(request.getParameter("categoryId")).thenReturn(null);

        controller.doGet(request, response);

        verify(request).setAttribute(eq("expandedCategoryIds"), argThat((Set<?> s) -> s.isEmpty()));
    }

    // ------------------------------------------------------------------
    // GET ?action=view
    // ------------------------------------------------------------------

    @Test
    public void view_productNotFound_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("action")).thenReturn("view");
        when(request.getParameter("id")).thenReturn("5");
        when(productDAO.findById(5)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendRedirect(CONTEXT_PATH + "/product?error=notfound");
    }

    // ------------------------------------------------------------------
    // POST ?action=create
    // ------------------------------------------------------------------

    @Test
    public void create_withoutFullAccess_returns403AndNeverInserts() throws Exception {
        User noAccess = new User();
        noAccess.setRole(new Role(2, "Sales")); // không có Full access trên PRODUCT
        when(session.getAttribute("currentUser")).thenReturn(noAccess);
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("productName")).thenReturn("Router ABC");
        when(request.getParameter("categoryId")).thenReturn("1");

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(productDAO, never()).insert(any());
    }

    @Test
    public void create_missingCategory_redirectsWithoutInserting() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("productName")).thenReturn("Router ABC");
        when(request.getParameter("categoryId")).thenReturn(null);

        controller.doPost(request, response);

        verify(productDAO, never()).insert(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/product?action=new&error=invalid");
    }

    @Test
    public void create_validFields_insertsAndRedirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("productName")).thenReturn("Router ABC");
        when(request.getParameter("categoryId")).thenReturn("1");
        when(productDAO.generateNextProductCode()).thenReturn("SP-0001");
        when(productDAO.insert(any(Product.class))).thenReturn(42);

        controller.doPost(request, response);

        verify(productDAO).insert(any(Product.class));
        verify(response).sendRedirect(CONTEXT_PATH + "/product?action=view&id=42");
    }

    @Test
    public void create_withUploadedImage_savesFileAndLinksToNewProduct() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("productName")).thenReturn("Router ABC");
        when(request.getParameter("categoryId")).thenReturn("1");
        when(productDAO.generateNextProductCode()).thenReturn("SP-0001");
        when(productDAO.insert(any(Product.class))).thenReturn(42);

        Part imagePart = mock(Part.class);
        when(imagePart.getName()).thenReturn("images");
        when(imagePart.getSize()).thenReturn(1024L);
        when(imagePart.getSubmittedFileName()).thenReturn("anh.jpg");
        when(request.getParts()).thenReturn(Arrays.asList(imagePart));

        try (MockedStatic<FileStorage> fs = mockStatic(FileStorage.class)) {
            // Cả lớp FileStorage bị mock nên isAcceptable() mặc định trả false;
            // phải stub rõ, nếu không controller sẽ từ chối file ngay từ đầu.
            fs.when(() -> FileStorage.isAcceptable(imagePart, FileStorage.IMAGE_EXTENSIONS)).thenReturn(true);
            fs.when(() -> FileStorage.save(imagePart, "products/images", FileStorage.IMAGE_EXTENSIONS))
                    .thenReturn("/uploads/products/images/x.jpg");

            controller.doPost(request, response);

            verify(productDAO).addImage(42, "/uploads/products/images/x.jpg");
        }
    }

    @Test
    public void create_withDisallowedImageType_rejectsBeforeCreatingTheProduct() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("productName")).thenReturn("Router ABC");
        when(request.getParameter("categoryId")).thenReturn("1");

        Part svgPart = mock(Part.class);
        when(svgPart.getName()).thenReturn("images");
        when(svgPart.getSize()).thenReturn(1024L);
        when(svgPart.getSubmittedFileName()).thenReturn("payload.svg");
        when(request.getParts()).thenReturn(Arrays.asList(svgPart));

        try (MockedStatic<FileStorage> fs = mockStatic(FileStorage.class)) {
            fs.when(() -> FileStorage.isAcceptable(svgPart, FileStorage.IMAGE_EXTENSIONS)).thenReturn(false);

            controller.doPost(request, response);

            // Kiểm phải xảy ra TRƯỚC khi ghi: nếu để saveNewImages() tự bỏ qua
            // file thì sản phẩm đã được tạo rồi, người dùng nhận về một bản ghi
            // thiếu ảnh mà không có lời giải thích nào.
            verify(productDAO, never()).insert(any(Product.class));
            verify(productDAO, never()).addImage(anyInt(), anyString());
            verify(response).sendRedirect(CONTEXT_PATH + "/product?action=new&error=invalid_image_type");
        }
    }

    @Test
    public void create_withDisallowedCatalogueType_rejectsBeforeCreatingTheProduct() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        when(request.getParameter("productName")).thenReturn("Router ABC");
        when(request.getParameter("categoryId")).thenReturn("1");

        Part docPart = mock(Part.class);
        when(docPart.getName()).thenReturn("catalogues");
        when(docPart.getSize()).thenReturn(1024L);
        when(docPart.getSubmittedFileName()).thenReturn("tai-lieu.docx");
        when(request.getParts()).thenReturn(Arrays.asList(docPart));

        try (MockedStatic<FileStorage> fs = mockStatic(FileStorage.class)) {
            fs.when(() -> FileStorage.isAcceptable(docPart, FileStorage.DOCUMENT_EXTENSIONS)).thenReturn(false);

            controller.doPost(request, response);

            verify(productDAO, never()).insert(any(Product.class));
            verify(response).sendRedirect(CONTEXT_PATH + "/product?action=new&error=invalid_catalogue_type");
        }
    }

    // ------------------------------------------------------------------
    // POST ?action=update
    // ------------------------------------------------------------------

    @Test
    public void update_productIdMissing_redirectsWithNotFoundError() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("productId")).thenReturn(null);

        controller.doPost(request, response);

        verify(productDAO, never()).update(any());
        verify(response).sendRedirect(CONTEXT_PATH + "/product?error=notfound");
    }

    @Test
    public void update_validFields_updatesAndRedirectsToDetail() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("productId")).thenReturn("7");
        when(request.getParameter("productName")).thenReturn("Router XYZ");
        when(request.getParameter("categoryId")).thenReturn("2");
        when(productDAO.update(any(Product.class))).thenReturn(true);

        controller.doPost(request, response);

        verify(productDAO).update(argThat((Product p) -> p.getProductId() == 7));
        verify(response).sendRedirect(CONTEXT_PATH + "/product?action=view&id=7");
    }

    /**
     * "removedImageIds" là CSV do JS gộp lại (vd "3,7,x,12") -- token không
     * parse được số (vd "x") phải bị bỏ qua thay vì làm hỏng cả danh sách,
     * các id hợp lệ còn lại vẫn phải được xoá.
     */
    @Test
    public void update_removedImageIdsWithGarbageToken_deletesOnlyValidIdsSkipsInvalid() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("productId")).thenReturn("70"); // khác hẳn các id ảnh bên dưới, tránh trùng số
        when(request.getParameter("productName")).thenReturn("Router XYZ");
        when(request.getParameter("categoryId")).thenReturn("2");
        when(request.getParameter("removedImageIds")).thenReturn("3,7,x,12");
        when(productDAO.update(any(Product.class))).thenReturn(true);

        controller.doPost(request, response);

        verify(productDAO).deleteImage(3, 70);
        verify(productDAO).deleteImage(7, 70);
        verify(productDAO).deleteImage(12, 70);
        // Token "x" không parse được số -- phải bị bỏ qua, không có lần gọi thứ 4 nào khác.
        verify(productDAO, times(3)).deleteImage(anyInt(), anyInt());
    }

    // ------------------------------------------------------------------
    // POST ?action=delete
    // ------------------------------------------------------------------

    @Test
    public void delete_productUsedInContracts_blocksDeletion() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("7");
        when(productDAO.isUsedInContracts(7)).thenReturn(true);

        controller.doPost(request, response);

        verify(productDAO, never()).softDelete(anyInt());
        verify(response).sendRedirect(CONTEXT_PATH + "/product?action=view&id=7&error=has_active_contracts");
    }

    @Test
    public void delete_productNotUsedInContracts_softDeletesAndRedirectsToList() throws Exception {
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("7");
        when(productDAO.isUsedInContracts(7)).thenReturn(false);

        controller.doPost(request, response);

        verify(productDAO).softDelete(7);
        verify(response).sendRedirect(CONTEXT_PATH + "/product");
    }

    // ------------------------------------------------------------------
    // GET ?action=new / edit -- trang form cũng phải gác quyền
    // ------------------------------------------------------------------

    private void loginWithoutProductAccess() {
        User noAccess = new User();
        noAccess.setRole(new Role(2, "Sales")); // chỉ View only trên PRODUCT
        when(session.getAttribute("currentUser")).thenReturn(noAccess);
    }

    /** Ẩn nút ở JSP chỉ là lớp trình bày; gõ thẳng URL vẫn phải bị chặn. */
    @Test
    public void createForm_withoutFullAccess_returns403InsteadOfRendering() throws Exception {
        loginWithoutProductAccess();
        when(request.getParameter("action")).thenReturn("new");

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(request, never()).getRequestDispatcher("/jsp/technical/addNewProduct.jsp");
    }

    @Test
    public void editForm_withoutFullAccess_returns403InsteadOfRendering() throws Exception {
        loginWithoutProductAccess();
        when(request.getParameter("action")).thenReturn("edit");
        when(request.getParameter("id")).thenReturn("5");

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(request, never()).getRequestDispatcher("/jsp/technical/updateProduct.jsp");
    }

    @Test
    public void createForm_withFullAccess_stillRenders() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/jsp/technical/addNewProduct.jsp")).thenReturn(dispatcher);
        when(request.getParameter("action")).thenReturn("new");

        controller.doGet(request, response);

        verify(dispatcher).forward(request, response);
        verify(response, never()).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }
}
