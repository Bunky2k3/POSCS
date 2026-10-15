package poscs.validator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import jakarta.servlet.http.Part;
import org.junit.Before;
import org.junit.Test;
import poscs.model.Product;
import poscs.model.ProductCategory;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho ProductValidator:
 * - BR-54: Bắt buộc Tên sản phẩm và Danh mục; mã sản phẩm không kiểm trùng ở validator.
 * - Danh mục phải còn dùng được (không phải danh mục cha hoặc đã bị xóa mềm).
 * - BR-48: Ảnh sản phẩm tùy chọn, chỉ nhận JPG/PNG/GIF/WEBP.
 * - Catalogue tùy chọn, chỉ nhận PDF.
 * - Tương thích ngược với isValidCommonFields.
 */
public class ProductValidatorTest {

    private ProductValidator validator;

    @Before
    public void setUp() {
        validator = new ProductValidator();
    }

    private Product createValidProduct() {
        Product p = new Product();
        p.setProductId(1);
        p.setProductCode("SP-0001");
        p.setProductName("Máy bán hàng POS X200");
        p.setCategoryId(10);
        p.setDescription("Mô tả sản phẩm POS");
        return p;
    }

    private List<ProductCategory> createSampleCategories() {
        // Cây danh mục mẫu:
        // 1: "Năng lượng tái tạo" (cha của 10, 11)
        //   10: "Ắc quy" (lá)
        //   11: "Điện mặt trời" (lá)
        // 2: "Cáp quang" (cha của 20, 21)
        //   20: "Cáp đơn mode" (lá)
        //   21: "Phụ kiện quang" (đã bị xóa mềm)
        // 3: "Danh mục cha có con bị xóa hết" (cha của 30)
        //   30: "Con đã xóa" (đã bị xóa mềm)
        // 4: "Danh mục độc lập" (lá, không có con)
        // 5: "Danh mục đã xóa" (đã bị xóa mềm)
        List<ProductCategory> list = new ArrayList<>();
        list.add(new ProductCategory(1, "Năng lượng tái tạo", null, 1, false));
        list.add(new ProductCategory(10, "Ắc quy", 1, 1, false));
        list.add(new ProductCategory(11, "Điện mặt trời", 1, 2, false));

        list.add(new ProductCategory(2, "Cáp quang", null, 2, false));
        list.add(new ProductCategory(20, "Cáp đơn mode", 2, 1, false));
        list.add(new ProductCategory(21, "Phụ kiện quang", 2, 2, true)); // đã xóa

        list.add(new ProductCategory(3, "Danh mục cha có con bị xóa hết", null, 3, false));
        list.add(new ProductCategory(30, "Con đã xóa", 3, 1, true)); // con đã xóa

        list.add(new ProductCategory(4, "Danh mục độc lập", null, 4, false));
        list.add(new ProductCategory(5, "Danh mục đã xóa", null, 5, true)); // đã xóa

        return list;
    }

    // -------------------------------------------------------------------------
    // 1. Tests cho isValidCommonFields (tương thích ngược)
    // -------------------------------------------------------------------------
    @Test
    public void testIsValidCommonFields_valid() {
        Product p = createValidProduct();
        assertTrue(validator.isValidCommonFields(p));
    }

    @Test
    public void testIsValidCommonFields_nullProduct() {
        assertFalse(validator.isValidCommonFields(null));
    }

    @Test
    public void testIsValidCommonFields_invalidProductName() {
        Product p = createValidProduct();

        p.setProductName(null);
        assertFalse(validator.isValidCommonFields(p));

        p.setProductName("");
        assertFalse(validator.isValidCommonFields(p));

        p.setProductName("   ");
        assertFalse(validator.isValidCommonFields(p));

        // Vượt quá 150 ký tự
        p.setProductName("A".repeat(151));
        assertFalse(validator.isValidCommonFields(p));

        // Đúng 150 ký tự
        p.setProductName("A".repeat(150));
        assertTrue(validator.isValidCommonFields(p));
    }

    @Test
    public void testIsValidCommonFields_invalidCategoryId() {
        Product p = createValidProduct();

        p.setCategoryId(0);
        assertFalse(validator.isValidCommonFields(p));

        p.setCategoryId(-1);
        assertFalse(validator.isValidCommonFields(p));
    }

    // -------------------------------------------------------------------------
    // 2. Tests cho BR-54: validate(Product) cơ bản
    // -------------------------------------------------------------------------
    @Test
    public void testValidate_success() {
        Product p = createValidProduct();
        ValidationResult result = validator.validate(p);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    public void testValidate_nullProduct() {
        ValidationResult result = validator.validate(null);
        assertFalse(result.isValid());
        assertTrue(result.hasGlobalError());
        assertEquals(ProductValidator.ERR_INVALID, result.getGlobalError());
    }

    @Test
    public void testValidate_missingProductName() {
        Product p = createValidProduct();

        p.setProductName(null);
        ValidationResult r1 = validator.validate(p);
        assertFalse(r1.isValid());
        assertEquals(ProductValidator.ERR_REQUIRED, r1.getError("productName"));

        p.setProductName("");
        ValidationResult r2 = validator.validate(p);
        assertFalse(r2.isValid());
        assertEquals(ProductValidator.ERR_REQUIRED, r2.getError("productName"));

        p.setProductName("   \t\n  ");
        ValidationResult r3 = validator.validate(p);
        assertFalse(r3.isValid());
        assertEquals(ProductValidator.ERR_REQUIRED, r3.getError("productName"));
    }

    @Test
    public void testValidate_productNameTooLong() {
        Product p = createValidProduct();
        p.setProductName("X".repeat(151));
        ValidationResult result = validator.validate(p);
        assertFalse(result.isValid());
        assertEquals(ProductValidator.ERR_MAX_LENGTH, result.getError("productName"));
    }

    @Test
    public void testValidate_missingCategoryId() {
        Product p = createValidProduct();

        p.setCategoryId(0);
        ValidationResult r1 = validator.validate(p);
        assertFalse(r1.isValid());
        assertEquals(ProductValidator.ERR_REQUIRED, r1.getError("categoryId"));

        p.setCategoryId(-5);
        ValidationResult r2 = validator.validate(p);
        assertFalse(r2.isValid());
        assertEquals(ProductValidator.ERR_REQUIRED, r2.getError("categoryId"));
    }

    // -------------------------------------------------------------------------
    // 3. Tests cho Danh mục dùng được (còn hiệu lực, không phải cha/đã xóa)
    // -------------------------------------------------------------------------
    @Test
    public void testIsUsableCategory_leafCategory() {
        List<ProductCategory> categories = createSampleCategories();

        // Danh mục lá 10 và 11 thuộc cha 1
        assertTrue(validator.isUsableCategory(10, categories, null));
        assertTrue(validator.isUsableCategory(11, categories, null));

        // Danh mục lá độc lập 4
        assertTrue(validator.isUsableCategory(4, categories, null));

        // Kiểm tra qua đối tượng ProductCategory
        ProductCategory cat10 = categories.stream().filter(c -> c.getCategoryId() == 10).findFirst().get();
        assertTrue(validator.isUsableCategory(cat10, categories));
    }

    @Test
    public void testIsUsableCategory_parentCategoryRejected() {
        List<ProductCategory> categories = createSampleCategories();

        // Danh mục 1 có con đang hoạt động (10, 11) -> là danh mục cha -> không dùng được
        assertFalse(validator.isUsableCategory(1, categories, null));

        // Danh mục 2 có con 20 đang hoạt động -> là danh mục cha -> không dùng được
        assertFalse(validator.isUsableCategory(2, categories, null));

        // Kiểm tra đối tượng cha
        ProductCategory cat1 = categories.stream().filter(c -> c.getCategoryId() == 1).findFirst().get();
        assertFalse(validator.isUsableCategory(cat1, categories));
    }

    @Test
    public void testIsUsableCategory_parentWithAllChildrenDeleted() {
        List<ProductCategory> categories = createSampleCategories();

        // Danh mục 3 có con 30 nhưng 30 đã bị xóa mềm -> 3 không còn con hoạt động nào -> coi như dùng được
        assertTrue(validator.isUsableCategory(3, categories, null));
    }

    @Test
    public void testIsUsableCategory_deletedCategoryRejected() {
        List<ProductCategory> categories = createSampleCategories();

        // Danh mục 5 đã xóa mềm
        assertFalse(validator.isUsableCategory(5, categories, null));

        // Danh mục 21 đã xóa mềm
        assertFalse(validator.isUsableCategory(21, categories, null));
    }

    @Test
    public void testIsUsableCategory_nonExistentCategoryRejected() {
        List<ProductCategory> categories = createSampleCategories();

        assertFalse(validator.isUsableCategory(999, categories, null));
        assertFalse(validator.isUsableCategory(0, categories, null));
        assertFalse(validator.isUsableCategory(-1, categories, null));
    }

    @Test
    public void testIsUsableCategory_keepCategoryIdException() {
        List<ProductCategory> categories = createSampleCategories();

        // Danh mục 1 là cha, bình thường bị từ chối
        assertFalse(validator.isUsableCategory(1, categories, null));

        // Nhưng nếu là keepCategoryId (sản phẩm cũ đang lưu sẵn danh mục này khi sửa) -> cho phép giữ
        assertTrue(validator.isUsableCategory(1, categories, 1));

        // Nếu keepCategoryId khác danh mục cần kiểm tra thì vẫn từ chối
        assertFalse(validator.isUsableCategory(1, categories, 10));

        // Nhưng nếu danh mục bị xóa mềm thì kể cả keepCategoryId cũng không được
        assertFalse(validator.isUsableCategory(5, categories, 5));
    }

    @Test
    public void testGetUsableCategoryIds() {
        List<ProductCategory> categories = createSampleCategories();

        Set<Integer> usableIds = validator.getUsableCategoryIds(categories, null);
        // Bao gồm: 10, 11, 20, 3 (con đã xóa hết), 4
        assertTrue(usableIds.contains(10));
        assertTrue(usableIds.contains(11));
        assertTrue(usableIds.contains(20));
        assertTrue(usableIds.contains(3));
        assertTrue(usableIds.contains(4));

        // Không bao gồm cha và danh mục đã xóa: 1, 2, 5, 21, 30
        assertFalse(usableIds.contains(1));
        assertFalse(usableIds.contains(2));
        assertFalse(usableIds.contains(5));
        assertFalse(usableIds.contains(21));
        assertFalse(usableIds.contains(30));

        // Thử với keepCategoryId = 1
        Set<Integer> usableWithKeep = validator.getUsableCategoryIds(categories, 1);
        assertTrue(usableWithKeep.contains(1));
    }

    @Test
    public void testValidateWithCategories_successAndFailure() {
        List<ProductCategory> categories = createSampleCategories();
        Product p = createValidProduct();

        // Category 10 là lá hợp lệ
        p.setCategoryId(10);
        ValidationResult r1 = validator.validate(p, categories, null);
        assertTrue(r1.isValid());

        // Category 1 là cha -> lỗi invalid_category
        p.setCategoryId(1);
        ValidationResult r2 = validator.validate(p, categories, null);
        assertFalse(r2.isValid());
        assertEquals(ProductValidator.ERR_INVALID_CATEGORY, r2.getError("categoryId"));

        // Category 1 là cha nhưng là keepCategoryId -> hợp lệ
        ValidationResult r3 = validator.validate(p, categories, 1);
        assertTrue(r3.isValid());

        // Category 5 đã xóa -> lỗi invalid_category
        p.setCategoryId(5);
        ValidationResult r4 = validator.validate(p, categories, null);
        assertFalse(r4.isValid());
        assertEquals(ProductValidator.ERR_INVALID_CATEGORY, r4.getError("categoryId"));
    }

    // -------------------------------------------------------------------------
    // 4. Tests cho BR-48: Định dạng ảnh sản phẩm (JPG, PNG, GIF, WEBP)
    // -------------------------------------------------------------------------
    @Test
    public void testIsValidImageExtension_validExtensions() {
        assertTrue(validator.isValidImageExtension("photo.jpg"));
        assertTrue(validator.isValidImageExtension("photo.jpeg"));
        assertTrue(validator.isValidImageExtension("photo.png"));
        assertTrue(validator.isValidImageExtension("photo.gif"));
        assertTrue(validator.isValidImageExtension("photo.webp"));

        // Không phân biệt chữ hoa/thường
        assertTrue(validator.isValidImageExtension("IMAGE.JPG"));
        assertTrue(validator.isValidImageExtension("image.PNG"));
        assertTrue(validator.isValidImageExtension("PHOTO.GIF"));
        assertTrue(validator.isValidImageExtension("device.WEBP"));
        assertTrue(validator.isValidImageExtension("banner.JPEG"));

        // Tên file có nhiều dấu chấm
        assertTrue(validator.isValidImageExtension("product.v1.0.final.png"));
    }

    @Test
    public void testIsValidImageExtension_invalidExtensions() {
        // Định dạng ảnh bị cấm hoặc không được hỗ trợ (BR-48)
        assertFalse(validator.isValidImageExtension("vector.svg")); // SVG bị chặn chống XSS
        assertFalse(validator.isValidImageExtension("picture.bmp"));
        assertFalse(validator.isValidImageExtension("icon.ico"));
        assertFalse(validator.isValidImageExtension("image.tiff"));

        // Các loại file nguy hiểm hoặc tài liệu
        assertFalse(validator.isValidImageExtension("document.pdf"));
        assertFalse(validator.isValidImageExtension("script.js"));
        assertFalse(validator.isValidImageExtension("app.exe"));
        assertFalse(validator.isValidImageExtension("index.html"));
        assertFalse(validator.isValidImageExtension("code.php.jpg.exe"));

        // Tên file rỗng hoặc không có phần mở rộng
        assertFalse(validator.isValidImageExtension(null));
        assertFalse(validator.isValidImageExtension(""));
        assertFalse(validator.isValidImageExtension("   "));
        assertFalse(validator.isValidImageExtension("photo"));
        assertFalse(validator.isValidImageExtension("photo."));
    }

    @Test
    public void testValidateImageFileNames() {
        ValidationResult result = new ValidationResult();

        // Null hoặc danh sách rỗng (ảnh tùy chọn) -> hợp lệ
        assertTrue(validator.validateImageFileNames(result, null));
        assertTrue(validator.validateImageFileNames(result, Collections.emptyList()));
        assertTrue(result.isValid());

        // Danh sách ảnh hợp lệ
        assertTrue(validator.validateImageFileNames(result, Arrays.asList("pos1.jpg", "pos2.png", "pos3.webp")));
        assertTrue(result.isValid());

        // Danh sách có ảnh không hợp lệ
        ValidationResult r2 = new ValidationResult();
        assertFalse(validator.validateImageFileNames(r2, Arrays.asList("pos1.jpg", "doc.pdf", "pos2.png")));
        assertFalse(r2.isValid());
        assertEquals(ProductValidator.ERR_INVALID_IMAGE_TYPE, r2.getError("images"));
    }

    // -------------------------------------------------------------------------
    // 5. Tests cho Catalogue tài liệu (chỉ PDF)
    // -------------------------------------------------------------------------
    @Test
    public void testIsValidCatalogueExtension() {
        assertTrue(validator.isValidCatalogueExtension("catalogue.pdf"));
        assertTrue(validator.isValidCatalogueExtension("CATALOGUE.PDF"));
        assertTrue(validator.isValidCatalogueExtension("spec.sheet.v2.pdf"));

        // Không nhận các định dạng khác
        assertFalse(validator.isValidCatalogueExtension("manual.doc"));
        assertFalse(validator.isValidCatalogueExtension("manual.docx"));
        assertFalse(validator.isValidCatalogueExtension("pricing.xlsx"));
        assertFalse(validator.isValidCatalogueExtension("photo.png"));
        assertFalse(validator.isValidCatalogueExtension(null));
        assertFalse(validator.isValidCatalogueExtension(""));
        assertFalse(validator.isValidCatalogueExtension("guide"));
    }

    @Test
    public void testValidateCatalogueFileNames() {
        ValidationResult result = new ValidationResult();

        // Null hoặc rỗng (catalogue tùy chọn) -> hợp lệ
        assertTrue(validator.validateCatalogueFileNames(result, null));
        assertTrue(validator.validateCatalogueFileNames(result, Collections.emptyList()));
        assertTrue(result.isValid());

        // File PDF hợp lệ
        assertTrue(validator.validateCatalogueFileNames(result, Arrays.asList("specs.pdf", "manual.PDF")));
        assertTrue(result.isValid());

        // File không phải PDF
        ValidationResult r2 = new ValidationResult();
        assertFalse(validator.validateCatalogueFileNames(r2, Arrays.asList("specs.pdf", "presentation.pptx")));
        assertFalse(r2.isValid());
        assertEquals(ProductValidator.ERR_INVALID_CATALOGUE_TYPE, r2.getError("catalogues"));
    }

    // -------------------------------------------------------------------------
    // 6. Tests cho Jakarta Part upload
    // -------------------------------------------------------------------------
    @Test
    public void testIsValidImagePart() {
        // Part null hoặc rỗng -> hợp lệ vì ảnh là tùy chọn
        assertTrue(validator.isValidImagePart(null));

        Part emptyPart = mock(Part.class);
        when(emptyPart.getSize()).thenReturn(0L);
        assertTrue(validator.isValidImagePart(emptyPart));

        // Part có file ảnh hợp lệ
        Part validPart = mock(Part.class);
        when(validPart.getSize()).thenReturn(1024L);
        when(validPart.getSubmittedFileName()).thenReturn("avatar.png");
        assertTrue(validator.isValidImagePart(validPart));

        // Part có file sai định dạng ảnh
        Part invalidPart = mock(Part.class);
        when(invalidPart.getSize()).thenReturn(2048L);
        when(invalidPart.getSubmittedFileName()).thenReturn("exploit.svg");
        assertFalse(validator.isValidImagePart(invalidPart));
    }

    @Test
    public void testIsValidCataloguePart() {
        // Part null hoặc rỗng -> hợp lệ
        assertTrue(validator.isValidCataloguePart(null));

        Part emptyPart = mock(Part.class);
        when(emptyPart.getSize()).thenReturn(0L);
        assertTrue(validator.isValidCataloguePart(emptyPart));

        // Part có PDF hợp lệ
        Part validPart = mock(Part.class);
        when(validPart.getSize()).thenReturn(4096L);
        when(validPart.getSubmittedFileName()).thenReturn("brochure.pdf");
        assertTrue(validator.isValidCataloguePart(validPart));

        // Part không phải PDF
        Part invalidPart = mock(Part.class);
        when(invalidPart.getSize()).thenReturn(4096L);
        when(invalidPart.getSubmittedFileName()).thenReturn("brochure.docx");
        assertFalse(validator.isValidCataloguePart(invalidPart));
    }

    @Test
    public void testValidateParts_collections() {
        ValidationResult result = new ValidationResult();

        Part img1 = mock(Part.class);
        when(img1.getSize()).thenReturn(100L);
        when(img1.getSubmittedFileName()).thenReturn("img1.jpg");

        Part img2 = mock(Part.class);
        when(img2.getSize()).thenReturn(200L);
        when(img2.getSubmittedFileName()).thenReturn("img2.webp");

        assertTrue(validator.validateImages(result, Arrays.asList(img1, img2)));
        assertTrue(result.isValid());

        Part cat1 = mock(Part.class);
        when(cat1.getSize()).thenReturn(500L);
        when(cat1.getSubmittedFileName()).thenReturn("cat1.pdf");
        assertTrue(validator.validateCatalogues(result, Collections.singletonList(cat1)));
        assertTrue(result.isValid());

        // Lỗi khi có part sai
        Part badImg = mock(Part.class);
        when(badImg.getSize()).thenReturn(100L);
        when(badImg.getSubmittedFileName()).thenReturn("bad.exe");
        assertFalse(validator.validateImages(result, Collections.singletonList(badImg)));
        assertEquals(ProductValidator.ERR_INVALID_IMAGE_TYPE, result.getError("images"));
    }

    // -------------------------------------------------------------------------
    // 7. Tests cho validate toàn diện (Product + Categories + Files)
    // -------------------------------------------------------------------------
    @Test
    public void testValidateWithFileNames_allValid() {
        List<ProductCategory> categories = createSampleCategories();
        Product p = createValidProduct();
        p.setCategoryId(10); // lá hợp lệ

        ValidationResult result = validator.validateWithFileNames(
                p, categories, null,
                Arrays.asList("pos1.jpg", "pos2.png"),
                Collections.singletonList("catalogue.pdf")
        );
        assertTrue(result.isValid());
    }

    @Test
    public void testValidateWithFileNames_multipleErrors() {
        List<ProductCategory> categories = createSampleCategories();
        Product p = new Product();
        p.setProductName("   "); // Thiếu tên
        p.setCategoryId(1); // Danh mục cha

        ValidationResult result = validator.validateWithFileNames(
                p, categories, null,
                Collections.singletonList("hacked.svg"), // Sai loại ảnh
                Collections.singletonList("doc.docx") // Sai loại catalogue
        );

        assertFalse(result.isValid());
        assertEquals(ProductValidator.ERR_REQUIRED, result.getError("productName"));
        assertEquals(ProductValidator.ERR_INVALID_CATEGORY, result.getError("categoryId"));
        assertEquals(ProductValidator.ERR_INVALID_IMAGE_TYPE, result.getError("images"));
        assertEquals(ProductValidator.ERR_INVALID_CATALOGUE_TYPE, result.getError("catalogues"));
    }

    @Test
    public void testValidate_fullWithParts() {
        List<ProductCategory> categories = createSampleCategories();
        Product p = createValidProduct();
        p.setCategoryId(20);

        Part img = mock(Part.class);
        when(img.getSize()).thenReturn(100L);
        when(img.getSubmittedFileName()).thenReturn("valid.gif");

        Part cat = mock(Part.class);
        when(cat.getSize()).thenReturn(200L);
        when(cat.getSubmittedFileName()).thenReturn("valid.pdf");

        ValidationResult result = validator.validate(
                p, categories, null,
                Collections.singletonList(img),
                Collections.singletonList(cat)
        );
        assertTrue(result.isValid());
    }
}
