package poscs.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.Part;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import poscs.common.FileStorage;
import poscs.dao.DBContext;
import poscs.dao.ProductDAO;
import poscs.model.Contract;
import poscs.model.Product;
import poscs.model.ProductCatalogue;
import poscs.model.ProductCategory;
import poscs.model.ProductImage;
import poscs.validator.ProductValidator;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Unit Test cho ProductService (Task 3.2).
 * Kiểm thử toàn diện:
 * - UC-12, BR-53: Cây danh mục phân cấp, phân trang & tìm kiếm sản phẩm
 * - UC-13, BR-19: Chi tiết sản phẩm gồm thông tin, ảnh, catalogue, hợp đồng sử dụng
 * - UC-19, BR-54: Thêm sản phẩm bắt buộc Tên & Danh mục dùng được
 * - BR-48: Upload ảnh và catalogue (định dạng hợp lệ & không hợp lệ)
 * - BR-55, MSG-056: Tự sinh mã SP, thử lại tối đa 5 lần khi đụng UNIQUE ở DB, mã không đổi khi cập nhật
 * - UC-18: Cập nhật sản phẩm, gỡ ảnh/catalogue
 * - UC-20, BR-49: Xóa mềm sản phẩm và chặn khi sản phẩm nằm trong hợp đồng còn hiệu lực
 */
public class ProductServiceTest {

    private ProductDAO productDAO;
    private ProductValidator productValidator;
    private ProductService service;

    private List<ProductCategory> sampleCategories;

    @Before
    public void setUp() {
        productDAO = mock(ProductDAO.class);
        productValidator = new ProductValidator();
        service = new ProductService(productDAO, productValidator);

        // Cây 3 cấp:
        // Cấp 1 (gốc): Viễn thông (1)
        // Cấp 2: Thiết bị vô tuyến (2, cha=1)
        // Cấp 3 (lá): Ăng ten trạm BTS (3, cha=2), Kích sóng di động (4, cha=2)
        // Cấp 1 độc lập (lá): Dịch vụ kỹ thuật (5, cha=null, không có con -> lá)
        sampleCategories = Arrays.asList(
                category(1, "Viễn thông", null),
                category(2, "Thiết bị vô tuyến", 1),
                category(3, "Ăng ten trạm BTS", 2),
                category(4, "Kích sóng di động", 2),
                category(5, "Dịch vụ kỹ thuật", null)
        );
        when(productDAO.findAllCategories()).thenReturn(sampleCategories);
    }

    private static ProductCategory category(int id, String name, Integer parentId) {
        ProductCategory c = new ProductCategory();
        c.setCategoryId(id);
        c.setCategoryName(name);
        c.setParentCategoryId(parentId);
        c.setDeleted(false);
        return c;
    }

    private Product sampleProduct(int id, String code, String name, int categoryId) {
        Product p = new Product();
        p.setProductId(id);
        p.setProductCode(code);
        p.setProductName(name);
        p.setDescription("Mô tả sản phẩm test");
        p.setCategoryId(categoryId);
        p.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        p.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        p.setDeleted(false);
        return p;
    }

    private Part mockPart(String submittedFileName, long size) {
        Part part = mock(Part.class);
        when(part.getSubmittedFileName()).thenReturn(submittedFileName);
        when(part.getSize()).thenReturn(size);
        try {
            when(part.getInputStream()).thenReturn(new ByteArrayInputStream("dummy".getBytes(StandardCharsets.UTF_8)));
        } catch (IOException ignored) {
        }
        return part;
    }

    // ==================================================================
    // 1. CÂY DANH MỤC PHÂN CẤP (UC-12)
    // ==================================================================

    @Test
    public void testRootCategories() {
        List<ProductCategory> roots = service.rootCategories(sampleCategories);
        assertEquals(2, roots.size());
        assertEquals(1, roots.get(0).getCategoryId());
        assertEquals(5, roots.get(1).getCategoryId());

        assertTrue(service.rootCategories(null).isEmpty());
        assertTrue(service.rootCategories(Collections.emptyList()).isEmpty());
    }

    @Test
    public void testChildrenByParent() {
        Map<Integer, List<ProductCategory>> children = service.childrenByParent(sampleCategories);
        assertEquals(2, children.size());
        assertEquals(1, children.get(1).size());
        assertEquals(2, children.get(1).get(0).getCategoryId()); // con của 1 là 2
        assertEquals(2, children.get(2).size()); // con của 2 là 3, 4

        assertTrue(service.childrenByParent(null).isEmpty());
    }

    @Test
    public void testSubtreeIds() {
        Map<Integer, List<ProductCategory>> children = service.childrenByParent(sampleCategories);

        // Chọn danh mục lá (3) -> chỉ có chính nó
        List<ProductCategory> leafList = sampleCategories;
        List<Integer> leafSubtree = service.subtreeIds(3, children);
        assertEquals(List.of(3), leafSubtree);

        // Chọn danh mục cấp 2 (2) -> gồm 2, 3, 4
        List<Integer> midSubtree = service.subtreeIds(2, children);
        assertEquals(3, midSubtree.size());
        assertTrue(midSubtree.containsAll(Arrays.asList(2, 3, 4)));

        // Chọn danh mục gốc (1) -> gồm 1, 2, 3, 4
        List<Integer> rootSubtree = service.subtreeIds(1, children);
        assertEquals(4, rootSubtree.size());
        assertTrue(rootSubtree.containsAll(Arrays.asList(1, 2, 3, 4)));

        // Null children map
        assertEquals(List.of(99), service.subtreeIds(99, null));
    }

    @Test
    public void testExpandedCategoryIds() {
        // Lọc theo danh mục lá (3) -> mở cả tổ tiên: 3, 2, 1
        Set<Integer> expanded = service.expandedCategoryIds(sampleCategories, 3);
        assertEquals(3, expanded.size());
        assertTrue(expanded.containsAll(Arrays.asList(1, 2, 3)));

        // Lọc theo danh mục lá độc lập (5) -> chỉ có 5
        Set<Integer> exp5 = service.expandedCategoryIds(sampleCategories, 5);
        assertEquals(Set.of(5), exp5);

        // Không lọc (null)
        assertTrue(service.expandedCategoryIds(sampleCategories, null).isEmpty());
        assertTrue(service.expandedCategoryIds(null, 3).isEmpty());
    }

    @Test
    public void testSubtreeCategoryCounts() {
        Map<Integer, List<ProductCategory>> children = service.childrenByParent(sampleCategories);
        Map<Integer, Integer> directCounts = Map.of(3, 10, 4, 5, 5, 2);

        Map<Integer, Integer> rolledUp = service.subtreeCategoryCounts(sampleCategories, children, directCounts);
        // Danh mục 3 = 10, Danh mục 4 = 5
        assertEquals(Integer.valueOf(10), rolledUp.get(3));
        assertEquals(Integer.valueOf(5), rolledUp.get(4));
        // Danh mục 2 (cha của 3, 4) = 10 + 5 = 15
        assertEquals(Integer.valueOf(15), rolledUp.get(2));
        // Danh mục 1 (cha của 2) = 15
        assertEquals(Integer.valueOf(15), rolledUp.get(1));
        // Danh mục 5 = 2
        assertEquals(Integer.valueOf(2), rolledUp.get(5));
    }

    @Test
    public void testCategoryOptions_LeafOnlyAndPreserveKeepCategoryId() {
        // Form Thêm (keepCategoryId = null): chỉ chọn được danh mục lá (3, 4, 5)
        Map<Integer, String> options = service.categoryOptions(sampleCategories, null);
        assertEquals(3, options.size());
        assertFalse(options.containsKey(1)); // cha, không chọn được
        assertFalse(options.containsKey(2)); // cha, không chọn được
        assertTrue(options.containsKey(3));
        assertTrue(options.containsKey(4));
        assertTrue(options.containsKey(5));
        assertEquals("Viễn thông › Thiết bị vô tuyến › Ăng ten trạm BTS", options.get(3));
        assertEquals("Dịch vụ kỹ thuật", options.get(5));

        // Form Sửa giữ lại danh mục cha cũ (keepCategoryId = 2)
        Map<Integer, String> editOptions = service.categoryOptions(sampleCategories, 2);
        assertTrue(editOptions.containsKey(2));
        assertEquals("Viễn thông › Thiết bị vô tuyến", editOptions.get(2));

        assertTrue(service.categoryOptions(null, null).isEmpty());
    }

    // ==================================================================
    // 2. DANH SÁCH & CHI TIẾT SẢN PHẨM (UC-12, UC-13, BR-19)
    // ==================================================================

    @Test
    public void testFindProducts_PaginationAndSubtreeFilter() {
        List<Product> mockList = List.of(sampleProduct(10, "SP-0010", "Ắc quy", 3));
        when(productDAO.findAll(eq(1), eq(10), eq("ắc"), anyList())).thenReturn(mockList);
        when(productDAO.countAll(eq("ắc"), anyList())).thenReturn(1);

        // Lọc theo category cha (1) -> service mở rộng subtree gồm 1, 2, 3, 4
        ProductService.ProductListPage page = service.findProducts(1, 10, "ắc", 1);
        assertEquals(1, page.getProducts().size());
        assertEquals(1, page.getTotalCount());
        assertEquals(1, page.getPage());
        assertEquals(1, page.getTotalPages());

        verify(productDAO).findAll(eq(1), eq(10), eq("ắc"), argThat(list ->
                list != null && list.containsAll(Arrays.asList(1, 2, 3, 4))));
    }

    @Test
    public void testFindProducts_DefaultPagingOnInvalidInput() {
        when(productDAO.findAll(eq(1), eq(ProductService.DEFAULT_PAGE_SIZE), isNull(), isNull()))
                .thenReturn(Collections.emptyList());
        when(productDAO.countAll(isNull(), isNull())).thenReturn(0);

        ProductService.ProductListPage page = service.findProducts(-5, 0, null, null);
        assertEquals(1, page.getPage());
        assertEquals(ProductService.DEFAULT_PAGE_SIZE, page.getPageSize());
        assertEquals(0, page.getTotalCount());
        assertEquals(1, page.getTotalPages());
    }

    @Test
    public void testCountProductsAndGrandTotal() {
        when(productDAO.countAll(eq("keyword"), anyList())).thenReturn(12);
        when(productDAO.countAll(null, null)).thenReturn(89);

        assertEquals(12, service.countProducts("keyword", 2));
        assertEquals(89, service.grandTotal());
    }

    @Test
    public void testCountByCategory() {
        when(productDAO.countByCategory()).thenReturn(Map.of(3, 5, 4, 8));
        Map<Integer, Integer> result = service.countByCategory();
        assertEquals(2, result.size());
        assertEquals(Integer.valueOf(5), result.get(3));
    }

    @Test
    public void testFindById_ValidAndInvalid() {
        Product p = sampleProduct(10, "SP-0010", "Test", 3);
        when(productDAO.findById(10)).thenReturn(p);

        assertEquals(p, service.findById(10));
        assertNull(service.findById(0));
        assertNull(service.findById(-1));
        assertNull(service.findById(999)); // không tồn tại
    }

    @Test
    public void testGetProductDetail_Success_BR19() {
        Product p = sampleProduct(10, "SP-0010", "Sản phẩm A", 3);
        Contract c = new Contract();
        c.setContractId(101);
        c.setContractCode("HD-001");

        when(productDAO.findById(10)).thenReturn(p);
        when(productDAO.findContractsByProductId(10)).thenReturn(List.of(c));

        ServiceResult<ProductService.ProductDetail> result = service.getProductDetail(10);
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(p, result.getData().getProduct());
        assertEquals(1, result.getData().getContracts().size());
        assertEquals("HD-001", result.getData().getContracts().get(0).getContractCode());
    }

    @Test
    public void testGetProductDetail_NotFoundAndInvalidId() {
        ServiceResult<ProductService.ProductDetail> r1 = service.getProductDetail(0);
        assertFalse(r1.isSuccess());
        assertEquals("invalid", r1.getMessage());

        when(productDAO.findById(999)).thenReturn(null);
        ServiceResult<ProductService.ProductDetail> r2 = service.getProductDetail(999);
        assertFalse(r2.isSuccess());
        assertEquals("notfound", r2.getMessage());
    }

    // ==================================================================
    // 3. THÊM SẢN PHẨM (UC-19, BR-48, BR-54, BR-55, MSG-056)
    // ==================================================================

    @Test
    public void testCreateProduct_Success_AutoGenerateCode_BR54() {
        // Cho dù người dùng/caller cố tình gán mã thủ công "CUSTOM-9999"
        Product p = sampleProduct(0, "CUSTOM-9999", "Sản phẩm mới", 3); // danh mục 3 là lá dùng được
        when(productDAO.generateNextProductCode()).thenReturn("SP-0015");
        when(productDAO.insert(p)).thenReturn(15);

        ServiceResult<ProductService.ProductSaveResult> result = service.createProduct(p);

        assertTrue(result.isSuccess());
        assertEquals(15, result.getData().getProductId());
        // BR-54: Mã do hệ thống sinh, bắt buộc ghi đè mã thủ công
        assertEquals("SP-0015", p.getProductCode());
        verify(productDAO).insert(p);
    }

    @Test
    public void testCreateProduct_GenerateCodeReturnsNull_Fails() {
        Product p = sampleProduct(0, null, "Sản phẩm mới", 3);
        when(productDAO.generateNextProductCode()).thenReturn(null); // Không sinh được mã do lỗi SQL

        ServiceResult<ProductService.ProductSaveResult> result = service.createProduct(p);

        assertFalse(result.isSuccess());
        assertEquals("create_failed", result.getMessage());
        verify(productDAO, never()).insert(any());
    }

    @Test
    public void testCreateProduct_NullProduct_Fails() {
        ServiceResult<ProductService.ProductSaveResult> result = service.createProduct(null);
        assertFalse(result.isSuccess());
        assertEquals("invalid", result.getMessage());
    }

    @Test
    public void testCreateProduct_BlankOrNullName_BR54() {
        // Tên null
        Product p1 = sampleProduct(0, null, null, 3);
        ServiceResult<ProductService.ProductSaveResult> r1 = service.createProduct(p1);
        assertFalse(r1.isSuccess());
        assertEquals("invalid", r1.getMessage());

        // Tên rỗng / blank
        Product p2 = sampleProduct(0, null, "   ", 3);
        ServiceResult<ProductService.ProductSaveResult> r2 = service.createProduct(p2);
        assertFalse(r2.isSuccess());
        assertEquals("invalid", r2.getMessage());
    }

    @Test
    public void testCreateProduct_NameExceedsMaxLength_BR54() {
        String longName = "A".repeat(151); // Max 150
        Product p = sampleProduct(0, null, longName, 3);
        ServiceResult<ProductService.ProductSaveResult> result = service.createProduct(p);
        assertFalse(result.isSuccess());
        assertEquals("invalid", result.getMessage());
    }

    @Test
    public void testCreateProduct_InvalidCategory_BR54() {
        // CategoryId <= 0
        Product p1 = sampleProduct(0, null, "SP Test", 0);
        ServiceResult<ProductService.ProductSaveResult> r1 = service.createProduct(p1);
        assertFalse(r1.isSuccess());
        assertEquals("invalid", r1.getMessage());

        // CategoryId là danh mục cha (1 hoặc 2) -> không phải lá
        Product p2 = sampleProduct(0, null, "SP Test", 1);
        ServiceResult<ProductService.ProductSaveResult> r2 = service.createProduct(p2);
        assertFalse(r2.isSuccess());
        assertEquals("invalid_category", r2.getMessage());

        // CategoryId không tồn tại trong danh mục
        Product p3 = sampleProduct(0, null, "SP Test", 999);
        ServiceResult<ProductService.ProductSaveResult> r3 = service.createProduct(p3);
        assertFalse(r3.isSuccess());
        assertEquals("invalid_category", r3.getMessage());
    }

    @Test
    public void testCreateProduct_InvalidImageExtension_BR48() {
        Product p = sampleProduct(0, null, "SP Test", 3);
        Part invalidImage = mockPart("malicious.exe", 1024);

        ServiceResult<ProductService.ProductSaveResult> result =
                service.createProduct(p, List.of(invalidImage), Collections.emptyList());

        assertFalse(result.isSuccess());
        assertEquals("invalid_image_type", result.getMessage());
        verify(productDAO, never()).insert(any());
    }

    @Test
    public void testCreateProduct_InvalidCatalogueExtension_BR48() {
        Product p = sampleProduct(0, null, "SP Test", 3);
        Part invalidCatalogue = mockPart("document.docx", 2048); // Chỉ nhận .pdf

        ServiceResult<ProductService.ProductSaveResult> result =
                service.createProduct(p, Collections.emptyList(), List.of(invalidCatalogue));

        assertFalse(result.isSuccess());
        assertEquals("invalid_catalogue_type", result.getMessage());
        verify(productDAO, never()).insert(any());
    }

    @Test
    public void testCreateProduct_InsertFails() {
        Product p = sampleProduct(0, null, "SP Test", 3);
        when(productDAO.generateNextProductCode()).thenReturn("SP-0001");
        when(productDAO.insert(p)).thenReturn(-1);

        ServiceResult<ProductService.ProductSaveResult> result = service.createProduct(p);

        assertFalse(result.isSuccess());
        assertEquals("create_failed", result.getMessage());
    }

    // ==================================================================
    // 4. BR-55 & MSG-056: SINH MÃ TỰ ĐỘNG & THỬ LẠI TỐI ĐA 5 LẦN TRÊN UNIQUE DB
    // ==================================================================

    @Test
    public void testCreateProduct_DuplicateCodeOnDb_RetriesUpToFiveTimes_Success_BR55_MSG056() throws Exception {
        // Kiểm thử thực tế qua logic ProductDAO.insert:
        // Lần 1: SQLException error 1062 duplicate key 'product_code'
        // Lần 2: PreparedStatement executeUpdate thành công trả về generated key = 77
        PreparedStatement psDuplicate = mock(PreparedStatement.class);
        SQLException duplicateEx = new SQLException("Duplicate entry 'SP-0001' for key 'product_code'", "23000", 1062);
        when(psDuplicate.executeUpdate()).thenThrow(duplicateEx);

        PreparedStatement psSuccess = mock(PreparedStatement.class);
        when(psSuccess.executeUpdate()).thenReturn(1);
        ResultSet keys = singleRow(row("1", 77));
        when(psSuccess.getGeneratedKeys()).thenReturn(keys);

        Connection conn = mock(Connection.class);
        // Lần đầu trả psDuplicate, lần sau trả psSuccess
        when(conn.prepareStatement(anyString(), anyInt())).thenReturn(psDuplicate, psSuccess);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            ProductDAO realDao = spy(new ProductDAO());
            doReturn(sampleCategories).when(realDao).findAllCategories();
            // Lần đầu tạo mã SP-0001, bị đụng trùng CSDL -> tự sinh lại SP-0002
            doReturn("SP-0001", "SP-0002").when(realDao).generateNextProductCode();

            ProductService serviceWithRealDao = new ProductService(realDao, productValidator);

            Product p = sampleProduct(0, null, "Ắc quy viễn thông", 3);
            ServiceResult<ProductService.ProductSaveResult> result = serviceWithRealDao.createProduct(p);

            assertTrue("Phải thành công ở lần thử lại mà không báo lỗi (MSG-056)", result.isSuccess());
            assertEquals(77, result.getData().getProductId());
            assertEquals("Mã sản phẩm tự sinh lại sau khi trùng", "SP-0002", p.getProductCode());
            verify(psDuplicate).executeUpdate();
            verify(psSuccess).executeUpdate();
        }
    }

    @Test
    public void testCreateProduct_DuplicateCodeOnDb_FailsAfterFiveAttempts_BR55_MSG056() throws Exception {
        // Kiểm thử 5 lần đều bị trùng -> sau 5 lần dừng lại và trả về create_failed
        PreparedStatement psDuplicate = mock(PreparedStatement.class);
        SQLException duplicateEx = new SQLException("Duplicate entry for key 'product_code'", "23000", 1062);
        when(psDuplicate.executeUpdate()).thenThrow(duplicateEx);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(anyString(), anyInt())).thenReturn(psDuplicate);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            ProductDAO realDao = spy(new ProductDAO());
            doReturn(sampleCategories).when(realDao).findAllCategories();
            doReturn("SP-0001", "SP-0002", "SP-0003", "SP-0004", "SP-0005").when(realDao).generateNextProductCode();

            ProductService serviceWithRealDao = new ProductService(realDao, productValidator);

            Product p = sampleProduct(0, null, "Ắc quy viễn thông", 3);
            ServiceResult<ProductService.ProductSaveResult> result = serviceWithRealDao.createProduct(p);

            assertFalse("Vượt quá 5 lần thử thì phải thất bại", result.isSuccess());
            assertEquals("create_failed", result.getMessage());
            verify(psDuplicate, times(ProductService.MAX_CODE_GEN_ATTEMPTS)).executeUpdate();
        }
    }

    // ==================================================================
    // 5. CẬP NHẬT SẢN PHẨM & MÃ KHÔNG ĐỔI (UC-18, BR-48, BR-54, BR-55)
    // ==================================================================

    @Test
    public void testUpdateProduct_Success_CodeImmutable_BR55() {
        Product existing = sampleProduct(10, "SP-0010", "Tên cũ", 3);
        when(productDAO.findById(10)).thenReturn(existing);
        when(productDAO.update(any(Product.class))).thenReturn(true);

        // Input người dùng cố tình đổi productCode thành "SP-9999"
        Product input = sampleProduct(10, "SP-9999", "Tên mới cập nhật", 3);

        ServiceResult<ProductService.ProductSaveResult> result = service.updateProduct(input);

        assertTrue(result.isSuccess());
        assertEquals(10, result.getData().getProductId());
        // BR-55: Mã sản phẩm KHÔNG ĐƯỢC ĐỔI sau khi tạo, phải giữ nguyên SP-0010
        assertEquals("SP-0010", input.getProductCode());
        verify(productDAO).update(argThat(p -> "SP-0010".equals(p.getProductCode())));
    }

    @Test
    public void testUpdateProduct_PreservesKeepCategoryId() {
        // Sản phẩm cũ đang lưu ở danh mục 2 (danh mục cha)
        Product existing = sampleProduct(10, "SP-0010", "Tên cũ", 2);
        when(productDAO.findById(10)).thenReturn(existing);
        when(productDAO.update(any(Product.class))).thenReturn(true);

        Product input = sampleProduct(10, "SP-0010", "Tên mới", 2);
        ServiceResult<ProductService.ProductSaveResult> result = service.updateProduct(input);

        assertTrue("Form sửa phải cho phép giữ lại danh mục cha cũ đã lưu", result.isSuccess());
    }

    @Test
    public void testUpdateProduct_NotFoundOrInvalidId() {
        ServiceResult<ProductService.ProductSaveResult> r1 = service.updateProduct(null);
        assertFalse(r1.isSuccess());
        assertEquals("invalid", r1.getMessage());

        Product pZero = sampleProduct(0, "SP-0001", "Tên", 3);
        ServiceResult<ProductService.ProductSaveResult> r2 = service.updateProduct(pZero);
        assertFalse(r2.isSuccess());
        assertEquals("invalid", r2.getMessage());

        when(productDAO.findById(99)).thenReturn(null);
        Product p99 = sampleProduct(99, "SP-0099", "Tên", 3);
        ServiceResult<ProductService.ProductSaveResult> r3 = service.updateProduct(p99);
        assertFalse(r3.isSuccess());
        assertEquals("notfound", r3.getMessage());
    }

    @Test
    public void testUpdateProduct_InvalidFieldsAndFiles() {
        Product existing = sampleProduct(10, "SP-0010", "Tên cũ", 3);
        when(productDAO.findById(10)).thenReturn(existing);

        // Tên rỗng
        Product pBlank = sampleProduct(10, "SP-0010", "   ", 3);
        ServiceResult<ProductService.ProductSaveResult> r1 = service.updateProduct(pBlank);
        assertFalse(r1.isSuccess());
        assertEquals("invalid", r1.getMessage());

        // Danh mục không hợp lệ (chọn danh mục cha 1)
        Product pParentCat = sampleProduct(10, "SP-0010", "Tên mới", 1);
        ServiceResult<ProductService.ProductSaveResult> r2 = service.updateProduct(pParentCat);
        assertFalse(r2.isSuccess());
        assertEquals("invalid_category", r2.getMessage());

        // File ảnh không hợp lệ
        Product pValid = sampleProduct(10, "SP-0010", "Tên mới", 3);
        Part badImg = mockPart("hack.svg", 100);
        ServiceResult<ProductService.ProductSaveResult> r3 =
                service.updateProduct(pValid, null, null, List.of(badImg), Collections.emptyList());
        assertFalse(r3.isSuccess());
        assertEquals("invalid_image_type", r3.getMessage());
    }

    @Test
    public void testUpdateProduct_DaoUpdateFails() {
        Product existing = sampleProduct(10, "SP-0010", "Tên cũ", 3);
        when(productDAO.findById(10)).thenReturn(existing);
        when(productDAO.update(any())).thenReturn(false);

        Product input = sampleProduct(10, "SP-0010", "Tên mới", 3);
        ServiceResult<ProductService.ProductSaveResult> result = service.updateProduct(input);

        assertFalse(result.isSuccess());
        assertEquals("update_failed", result.getMessage());
    }

    // ==================================================================
    // 6. XÓA MỀM & CHẶN KHI CÒN HỢP ĐỒNG (UC-20, BR-49)
    // ==================================================================

    @Test
    public void testDeleteProduct_Success_BR49() {
        Product existing = sampleProduct(10, "SP-0010", "Sản phẩm A", 3);
        when(productDAO.findById(10)).thenReturn(existing);
        when(productDAO.isUsedInContracts(10)).thenReturn(false); // Không có hợp đồng hiệu lực
        when(productDAO.softDelete(10)).thenReturn(true);

        ServiceResult<Void> result = service.deleteProduct(10);

        assertTrue(result.isSuccess());
        verify(productDAO).softDelete(10);
    }

    @Test
    public void testDeleteProduct_BlockedWhenUsedInContracts_BR49() {
        Product existing = sampleProduct(10, "SP-0010", "Sản phẩm A", 3);
        when(productDAO.findById(10)).thenReturn(existing);
        when(productDAO.isUsedInContracts(10)).thenReturn(true); // BR-49: còn hợp đồng hiệu lực

        ServiceResult<Void> result = service.deleteProduct(10);

        assertFalse(result.isSuccess());
        assertEquals("has_active_contracts", result.getMessage());
        // Bắt buộc KHÔNG được gọi xóa mềm
        verify(productDAO, never()).softDelete(anyInt());
    }

    @Test
    public void testDeleteProduct_NotFoundAndInvalidId() {
        ServiceResult<Void> r1 = service.deleteProduct(0);
        assertFalse(r1.isSuccess());
        assertEquals("invalid", r1.getMessage());

        ServiceResult<Void> r2 = service.deleteProduct(-3);
        assertFalse(r2.isSuccess());
        assertEquals("invalid", r2.getMessage());

        when(productDAO.findById(999)).thenReturn(null);
        ServiceResult<Void> r3 = service.deleteProduct(999);
        assertFalse(r3.isSuccess());
        assertEquals("notfound", r3.getMessage());
    }

    @Test
    public void testDeleteProduct_DaoSoftDeleteFails() {
        Product existing = sampleProduct(10, "SP-0010", "Sản phẩm A", 3);
        when(productDAO.findById(10)).thenReturn(existing);
        when(productDAO.isUsedInContracts(10)).thenReturn(false);
        when(productDAO.softDelete(10)).thenReturn(false);

        ServiceResult<Void> result = service.deleteProduct(10);

        assertFalse(result.isSuccess());
        assertEquals("delete_failed", result.getMessage());
    }

    // ==================================================================
    // 7. FILE UPLOAD & REMOVE HELPERS (BR-48)
    // ==================================================================

    @Test
    public void testRemoveMarkedFiles_ScopesToOwningProduct() {
        Product existing = sampleProduct(10, "SP-0010", "Sản phẩm A", 3);
        ProductImage img1 = new ProductImage();
        img1.setImageId(101);
        img1.setProductId(10);
        existing.setImages(List.of(img1));

        when(productDAO.deleteImage(101, 10)).thenReturn(true);

        // Gỡ ảnh 101 (thuộc SP) và ảnh 999 (không thuộc SP)
        int failed = service.removeMarkedFiles(10, "101,999", existing, true);

        assertEquals(0, failed); // 999 không thuộc SP nên bị bỏ qua
        verify(productDAO).deleteImage(101, 10);
        // Đảm bảo không gọi DAO cho 999 để tiết kiệm truy vấn DB
        verify(productDAO, never()).deleteImage(eq(999), anyInt());
    }

    @Test
    public void testSaveNewImages_SuccessAndFailure() throws Exception {
        Part validPart1 = mockPart("image1.jpg", 1024);
        Part validPart2 = mockPart("image2.png", 2048);
        Part emptyPart = mockPart("empty.jpg", 0);

        try (MockedStatic<FileStorage> fs = mockStatic(FileStorage.class)) {
            fs.when(() -> FileStorage.save(eq(validPart1), anyString(), anySet()))
                    .thenReturn("/uploads/products/images/img1.jpg");
            fs.when(() -> FileStorage.save(eq(validPart2), anyString(), anySet()))
                    .thenReturn("/uploads/products/images/img2.png");

            // Kịch bản 1: lưu thành công cả 2 part
            when(productDAO.addImage(10, "/uploads/products/images/img1.jpg")).thenReturn(1);
            when(productDAO.addImage(10, "/uploads/products/images/img2.png")).thenReturn(2);

            int failedSuccess = service.saveNewImages(10, List.of(validPart1, validPart2, emptyPart));
            assertEquals(0, failedSuccess);

            // Kịch bản 2: addImage thất bại ở DAO
            when(productDAO.addImage(10, "/uploads/products/images/img2.png")).thenReturn(-1);
            int failedDao = service.saveNewImages(10, List.of(validPart2));
            assertEquals(1, failedDao);

            // Kịch bản 3: FileStorage.save gặp IOException
            Part ioErrorPart = mockPart("io_error.jpg", 1024);
            fs.when(() -> FileStorage.save(eq(ioErrorPart), anyString(), anySet()))
                    .thenThrow(new IOException("Disk error"));
            int failedIo = service.saveNewImages(10, List.of(ioErrorPart));
            assertEquals(1, failedIo);

            // Kịch bản 4: null hoặc rỗng
            assertEquals(0, service.saveNewImages(10, null));
            assertEquals(0, service.saveNewImages(10, Collections.emptyList()));
        }
    }

    @Test
    public void testSaveNewCatalogues_SuccessAndFailure() throws Exception {
        Part validCat = mockPart("catalog.pdf", 10240);
        Part emptyPart = mockPart("empty.pdf", 0);

        try (MockedStatic<FileStorage> fs = mockStatic(FileStorage.class)) {
            fs.when(() -> FileStorage.save(eq(validCat), anyString(), anySet()))
                    .thenReturn("/uploads/products/catalogues/cat1.pdf");

            // Thành công
            when(productDAO.addCatalogue(10, "/uploads/products/catalogues/cat1.pdf", "catalog.pdf"))
                    .thenReturn(1);
            int failedSuccess = service.saveNewCatalogues(10, List.of(validCat, emptyPart));
            assertEquals(0, failedSuccess);

            // Thất bại ở DAO
            when(productDAO.addCatalogue(10, "/uploads/products/catalogues/cat1.pdf", "catalog.pdf"))
                    .thenReturn(-1);
            int failedDao = service.saveNewCatalogues(10, List.of(validCat));
            assertEquals(1, failedDao);

            // FileStorage ném IOException
            Part ioCat = mockPart("io_catalog.pdf", 10240);
            fs.when(() -> FileStorage.save(eq(ioCat), anyString(), anySet()))
                    .thenThrow(new IOException("Disk error"));
            int failedIo = service.saveNewCatalogues(10, List.of(ioCat));
            assertEquals(1, failedIo);

            // Null/rỗng
            assertEquals(0, service.saveNewCatalogues(10, null));
            assertEquals(0, service.saveNewCatalogues(10, Collections.emptyList()));
        }
    }

    @Test
    public void testCreateProduct_WithFiles_CountsNotAdded() throws Exception {
        Product p = sampleProduct(0, null, "SP Kèm Ảnh", 3);
        when(productDAO.generateNextProductCode()).thenReturn("SP-0020");
        when(productDAO.insert(p)).thenReturn(20);

        Part imgPart = mockPart("test.jpg", 1024);
        try (MockedStatic<FileStorage> fs = mockStatic(FileStorage.class)) {
            fs.when(() -> FileStorage.isAcceptable(any(Part.class), anySet())).thenReturn(true);
            fs.when(() -> FileStorage.save(eq(imgPart), anyString(), anySet()))
                    .thenReturn("/uploads/products/images/test.jpg");
            // addImage thất bại -> notAdded = 1
            when(productDAO.addImage(20, "/uploads/products/images/test.jpg")).thenReturn(-1);

            ServiceResult<ProductService.ProductSaveResult> result =
                    service.createProduct(p, List.of(imgPart), Collections.emptyList());

            assertTrue(result.isSuccess());
            assertEquals(20, result.getData().getProductId());
            assertEquals(1, result.getData().getNotAdded());
            assertTrue(result.getData().hasFileErrors());
        }
    }

    @Test
    public void testUpdateProduct_WithFiles_CountsNotRemovedAndNotAdded() throws Exception {
        Product existing = sampleProduct(10, "SP-0010", "Tên cũ", 3);
        ProductImage oldImg = new ProductImage();
        oldImg.setImageId(50);
        oldImg.setProductId(10);
        existing.setImages(List.of(oldImg));

        when(productDAO.findById(10)).thenReturn(existing);
        when(productDAO.update(any())).thenReturn(true);
        // Xóa ảnh 50 thất bại -> notRemoved = 1
        when(productDAO.deleteImage(50, 10)).thenReturn(false);

        Part newImg = mockPart("new.png", 1024);
        try (MockedStatic<FileStorage> fs = mockStatic(FileStorage.class)) {
            fs.when(() -> FileStorage.isAcceptable(any(Part.class), anySet())).thenReturn(true);
            fs.when(() -> FileStorage.save(eq(newImg), anyString(), anySet()))
                    .thenReturn("/uploads/products/images/new.png");
            // Thêm ảnh mới thành công -> notAdded = 0
            when(productDAO.addImage(10, "/uploads/products/images/new.png")).thenReturn(51);

            Product input = sampleProduct(10, "SP-0010", "Tên mới", 3);
            ServiceResult<ProductService.ProductSaveResult> result =
                    service.updateProduct(input, "50", null, List.of(newImg), Collections.emptyList());

            assertTrue(result.isSuccess());
            assertEquals(10, result.getData().getProductId());
            assertEquals(1, result.getData().getNotRemoved());
            assertEquals(0, result.getData().getNotAdded());
            assertTrue(result.getData().hasFileErrors());
        }
    }

    @Test
    public void testRemoveMarkedFiles_EmptyCsvOrNull() {
        Product existing = sampleProduct(10, "SP-0010", "Sản phẩm A", 3);
        assertEquals(0, service.removeMarkedFiles(10, null, existing, true));
        assertEquals(0, service.removeMarkedFiles(10, "   ", existing, true));
        assertEquals(0, service.removeMarkedFiles(10, "1,2", null, true));
    }

    @Test
    public void testFilesNotSavedQuery() {
        assertEquals("", ProductService.filesNotSavedQuery(0, 0));
        assertEquals("&error=files_not_saved&notRemoved=1&notAdded=2",
                ProductService.filesNotSavedQuery(1, 2));
    }

    @Test
    public void testCodeGenerationHelpers() {
        when(productDAO.generateNextProductCode()).thenReturn("SP-0005");
        when(productDAO.nextProductCodeAfter("SP-0005")).thenReturn("SP-0006");

        assertEquals("SP-0005", service.generateNextProductCode());
        assertEquals("SP-0006", service.nextProductCodeAfter("SP-0005"));
    }

    @Test
    public void testFileNameAcceptableHelpers() {
        assertTrue(service.areImageFileNamesAcceptable(List.of("photo.jpg", "banner.png", "img.webp")));
        assertFalse(service.areImageFileNamesAcceptable(List.of("photo.jpg", "file.pdf")));
        assertTrue(service.areImageFileNamesAcceptable(null));
        assertTrue(service.areImageFileNamesAcceptable(Collections.emptyList()));

        assertTrue(service.areCatalogueFileNamesAcceptable(List.of("cat.pdf")));
        assertFalse(service.areCatalogueFileNamesAcceptable(List.of("cat.pdf", "doc.docx")));
        assertTrue(service.areCatalogueFileNamesAcceptable(null));
    }
}
