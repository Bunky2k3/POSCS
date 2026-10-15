package poscs.validator;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import jakarta.servlet.http.Part;
import poscs.model.Product;
import poscs.model.ProductCategory;

/**
 * Validator cho module Sản phẩm (UC-18, UC-19).
 * Quy tắc: BR-54 (bắt buộc tên SP và danh mục còn dùng được); BR-48 (ảnh JPG/PNG/GIF/WEBP, catalogue PDF).
 */
public class ProductValidator extends BaseValidator {

    public static final int MAX_PRODUCT_NAME_LENGTH = 150;
    public static final int MAX_PRODUCT_CODE_LENGTH = 50;

    /** BR-48: Các đuôi file ảnh được chấp nhận (không phân biệt chữ hoa/thường). */
    public static final Set<String> IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    /** Đuôi file tài liệu catalogue được chấp nhận (chỉ PDF). */
    public static final Set<String> CATALOGUE_EXTENSIONS = Set.of(".pdf");

    // Các mã lỗi chuẩn hóa dùng cho ValidationResult và query parameter (?error=...)
    public static final String ERR_INVALID = "invalid";
    public static final String ERR_REQUIRED = "required";
    public static final String ERR_INVALID_CATEGORY = "invalid_category";
    public static final String ERR_INVALID_IMAGE_TYPE = "invalid_image_type";
    public static final String ERR_INVALID_CATALOGUE_TYPE = "invalid_catalogue_type";
    public static final String ERR_MAX_LENGTH = "max_length";

    /** BR-54: Bắt buộc tên sản phẩm và danh mục hợp lệ (giữ tương thích ngược với ProductController). */
    public boolean isValidCommonFields(Product p) {
        if (p == null) return false;
        return !isBlank(p.getProductName())
                && p.getProductName().trim().length() <= MAX_PRODUCT_NAME_LENGTH
                && p.getCategoryId() > 0;
    }

    /** Trích xuất phần mở rộng (extension) dạng chữ thường kèm dấu chấm (vd: ".jpg"). */
    public static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        String ext = fileName.substring(dot).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,10}") ? ext : "";
    }

    /** BR-48: Kiểm tra file name có đuôi ảnh hợp lệ (JPG, PNG, GIF, WEBP) hay không. */
    public boolean isValidImageExtension(String fileName) {
        return !isBlank(fileName) && IMAGE_EXTENSIONS.contains(extensionOf(fileName));
    }

    /** Kiểm tra file name có đuôi catalogue hợp lệ (PDF) hay không. */
    public boolean isValidCatalogueExtension(String fileName) {
        return !isBlank(fileName) && CATALOGUE_EXTENSIONS.contains(extensionOf(fileName));
    }

    /** BR-48: Kiểm tra Part ảnh upload. Ảnh là tùy chọn nên null hoặc part rỗng là hợp lệ. */
    public boolean isValidImagePart(Part part) {
        if (part == null || part.getSize() <= 0) return true;
        return isValidImageExtension(part.getSubmittedFileName());
    }

    /** Kiểm tra Part catalogue upload. Catalogue là tùy chọn nên null hoặc part rỗng là hợp lệ. */
    public boolean isValidCataloguePart(Part part) {
        if (part == null || part.getSize() <= 0) return true;
        return isValidCatalogueExtension(part.getSubmittedFileName());
    }

    /** Kiểm tra categoryId có phải là danh mục cha của bất kỳ danh mục con nào đang hoạt động không. */
    public static boolean isParentCategory(int categoryId, Collection<ProductCategory> allCategories) {
        if (categoryId <= 0 || allCategories == null) return false;
        for (ProductCategory cat : allCategories) {
            if (cat != null && !cat.isDeleted() && cat.getParentCategoryId() != null
                    && cat.getParentCategoryId().intValue() == categoryId) {
                return true;
            }
        }
        return false;
    }

    /** Kiểm tra đối tượng danh mục có còn dùng được hay không (không bị xóa mềm và không phải cha). */
    public boolean isUsableCategory(ProductCategory category, Collection<ProductCategory> allCategories) {
        if (category == null || category.isDeleted() || category.getCategoryId() <= 0) return false;
        return !isParentCategory(category.getCategoryId(), allCategories);
    }

    /**
     * Kiểm tra categoryId có còn dùng được trong danh sách danh mục hay không.
     * Cho phép giữ lại keepCategoryId khi sửa sản phẩm cũ nếu chưa bị xóa mềm.
     */
    public boolean isUsableCategory(int categoryId, Collection<ProductCategory> allCategories, Integer keepCategoryId) {
        if (categoryId <= 0 || allCategories == null) return false;
        ProductCategory target = null;
        for (ProductCategory cat : allCategories) {
            if (cat != null && cat.getCategoryId() == categoryId) {
                target = cat;
                break;
            }
        }
        if (target == null || target.isDeleted()) return false;
        if (keepCategoryId != null && keepCategoryId.intValue() == categoryId) return true;
        return !isParentCategory(categoryId, allCategories);
    }

    /** Lấy tập hợp ID của tất cả danh mục dùng được (lá và chưa bị xóa, cộng thêm keepCategoryId). */
    public Set<Integer> getUsableCategoryIds(Collection<ProductCategory> allCategories, Integer keepCategoryId) {
        if (allCategories == null) return Collections.emptySet();
        Set<Integer> result = new LinkedHashSet<>();
        for (ProductCategory cat : allCategories) {
            if (cat != null && isUsableCategory(cat.getCategoryId(), allCategories, keepCategoryId)) {
                result.add(cat.getCategoryId());
            }
        }
        return result;
    }

    /** Kiểm tra cơ bản sản phẩm (Tên sản phẩm và Danh mục). */
    public ValidationResult validate(Product p) {
        return validate(p, null, null);
    }

    /** Validate sản phẩm có kèm kiểm tra tính hợp lệ của danh mục (còn dùng được, không phải cha/đã xóa). */
    public ValidationResult validate(Product p, Collection<ProductCategory> allCategories, Integer keepCategoryId) {
        ValidationResult result = new ValidationResult();
        if (p == null) {
            result.addGlobalError(ERR_INVALID);
            return result;
        }

        // BR-54: Bắt buộc tên sản phẩm
        if (required(result, "productName", p.getProductName(), ERR_REQUIRED)) {
            maxLength(result, "productName", p.getProductName(), MAX_PRODUCT_NAME_LENGTH, ERR_MAX_LENGTH);
        }

        // BR-54: Bắt buộc chọn danh mục
        if (p.getCategoryId() <= 0) {
            result.addError("categoryId", ERR_REQUIRED);
        } else if (allCategories != null && !isUsableCategory(p.getCategoryId(), allCategories, keepCategoryId)) {
            result.addError("categoryId", ERR_INVALID_CATEGORY);
        }

        return result;
    }

    /** Kiểm tra danh sách Part ảnh tải lên (BR-48). */
    public boolean validateImages(ValidationResult result, Collection<Part> imageParts) {
        if (imageParts == null || imageParts.isEmpty()) return true;
        for (Part part : imageParts) {
            if (!isValidImagePart(part)) {
                result.addError("images", ERR_INVALID_IMAGE_TYPE);
                return false;
            }
        }
        return true;
    }

    /** Kiểm tra danh sách Part catalogue tải lên. */
    public boolean validateCatalogues(ValidationResult result, Collection<Part> catalogueParts) {
        if (catalogueParts == null || catalogueParts.isEmpty()) return true;
        for (Part part : catalogueParts) {
            if (!isValidCataloguePart(part)) {
                result.addError("catalogues", ERR_INVALID_CATALOGUE_TYPE);
                return false;
            }
        }
        return true;
    }

    /** Kiểm tra danh sách tên file ảnh (thuận tiện cho Service / Unit test không cần Part). */
    public boolean validateImageFileNames(ValidationResult result, Collection<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) return true;
        for (String fileName : fileNames) {
            if (!isBlank(fileName) && !isValidImageExtension(fileName)) {
                result.addError("images", ERR_INVALID_IMAGE_TYPE);
                return false;
            }
        }
        return true;
    }

    /** Kiểm tra danh sách tên file catalogue (thuận tiện cho Service / Unit test không cần Part). */
    public boolean validateCatalogueFileNames(ValidationResult result, Collection<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) return true;
        for (String fileName : fileNames) {
            if (!isBlank(fileName) && !isValidCatalogueExtension(fileName)) {
                result.addError("catalogues", ERR_INVALID_CATALOGUE_TYPE);
                return false;
            }
        }
        return true;
    }

    /** Validate toàn diện thông tin sản phẩm và các file upload (Parts). */
    public ValidationResult validate(Product p, Collection<ProductCategory> allCategories, Integer keepCategoryId,
            Collection<Part> imageParts, Collection<Part> catalogueParts) {
        ValidationResult result = validate(p, allCategories, keepCategoryId);
        validateImages(result, imageParts);
        validateCatalogues(result, catalogueParts);
        return result;
    }

    /** Validate toàn diện thông tin sản phẩm và danh sách tên file upload. */
    public ValidationResult validateWithFileNames(Product p, Collection<ProductCategory> allCategories,
            Integer keepCategoryId, Collection<String> imageFileNames, Collection<String> catalogueFileNames) {
        ValidationResult result = validate(p, allCategories, keepCategoryId);
        validateImageFileNames(result, imageFileNames);
        validateCatalogueFileNames(result, catalogueFileNames);
        return result;
    }
}
