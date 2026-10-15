package poscs.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import jakarta.servlet.http.Part;
import poscs.common.FileStorage;
import poscs.dao.ProductDAO;
import poscs.model.Contract;
import poscs.model.Product;
import poscs.model.ProductCatalogue;
import poscs.model.ProductCategory;
import poscs.model.ProductImage;
import poscs.validator.ProductValidator;

/**
 * Service quản lý nghiệp vụ Sản phẩm (UC-12, UC-13, UC-18, UC-19, UC-20).
 *
 * <p>Quy tắc nghiệp vụ:
 * <ul>
 *   <li>BR-19: Chi tiết sản phẩm gồm: Thông tin, Hình ảnh, Catalogue, Hợp đồng sử dụng.</li>
 *   <li>BR-48: Ảnh sản phẩm tùy chọn (JPG/PNG/GIF/WEBP); Catalogue tùy chọn (PDF).</li>
 *   <li>BR-49: Xóa mềm và chặn khi SP nằm trong hợp đồng còn hiệu lực (has_active_contracts).</li>
 *   <li>BR-54: Bắt buộc Tên sản phẩm và Danh mục dùng được. Mã SP do hệ thống sinh.</li>
 *   <li>BR-55: Mã SP duy nhất nhờ UNIQUE ở DB, trùng thì tự sinh lại tối đa 5 lần không báo lỗi
 *              (MSG-056); mã không đổi sau khi tạo.</li>
 * </ul>
 */
public class ProductService extends BaseService {

    public static final String IMAGE_SUBFOLDER = "products/images";
    public static final String CATALOGUE_SUBFOLDER = "products/catalogues";
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * BR-55, MSG-056: Số lần thử lại tối đa khi sinh mã sản phẩm bị trùng UNIQUE ở DB.
     */
    public static final int MAX_CODE_GEN_ATTEMPTS = 5;

    private final ProductDAO productDAO;
    private final ProductValidator productValidator;

    public ProductService() {
        this(new ProductDAO(), new ProductValidator());
    }

    public ProductService(ProductDAO productDAO, ProductValidator productValidator) {
        this.productDAO = productDAO;
        this.productValidator = productValidator;
    }

    // ------------------------------------------------------------------
    // DTO / Value Objects
    // ------------------------------------------------------------------

    public static class ProductSaveResult {
        private final int productId;
        private final int notRemoved;
        private final int notAdded;

        public ProductSaveResult(int productId, int notRemoved, int notAdded) {
            this.productId = productId;
            this.notRemoved = notRemoved;
            this.notAdded = notAdded;
        }

        public int getProductId() { return productId; }
        public int getNotRemoved() { return notRemoved; }
        public int getNotAdded() { return notAdded; }
        public boolean hasFileErrors() { return notRemoved > 0 || notAdded > 0; }
    }

    public static class ProductDetail {
        private final Product product;
        private final List<Contract> contracts;

        public ProductDetail(Product product, List<Contract> contracts) {
            this.product = product;
            this.contracts = contracts != null ? contracts : Collections.emptyList();
        }

        public Product getProduct() { return product; }
        public List<Contract> getContracts() { return contracts; }
    }

    public static class ProductListPage {
        private final List<Product> products;
        private final int totalCount;
        private final int page;
        private final int pageSize;
        private final int totalPages;

        public ProductListPage(List<Product> products, int totalCount, int page, int pageSize) {
            this.products = products != null ? products : Collections.emptyList();
            this.totalCount = totalCount;
            this.page = page;
            this.pageSize = pageSize;
            this.totalPages = Math.max(1, (int) Math.ceil(totalCount / (double) pageSize));
        }

        public List<Product> getProducts() { return products; }
        public int getTotalCount() { return totalCount; }
        public int getPage() { return page; }
        public int getPageSize() { return pageSize; }
        public int getTotalPages() { return totalPages; }
    }

    // ------------------------------------------------------------------
    // Cây danh mục phân cấp (UC-12)
    // ------------------------------------------------------------------

    public List<ProductCategory> findAllCategories() {
        return productDAO.findAllCategories();
    }

    /** Danh mục cấp 1 (không có cha), theo thứ tự findAllCategories(). */
    public List<ProductCategory> rootCategories(List<ProductCategory> categoryList) {
        List<ProductCategory> result = new ArrayList<>();
        if (categoryList == null) return result;
        for (ProductCategory c : categoryList) {
            if (c.getParentCategoryId() == null) {
                result.add(c);
            }
        }
        return result;
    }

    /** category_id cha -> danh sách danh mục con trực tiếp. */
    public Map<Integer, List<ProductCategory>> childrenByParent(List<ProductCategory> categoryList) {
        Map<Integer, List<ProductCategory>> result = new LinkedHashMap<>();
        if (categoryList == null) return result;
        for (ProductCategory c : categoryList) {
            if (c.getParentCategoryId() != null) {
                result.computeIfAbsent(c.getParentCategoryId(), k -> new ArrayList<>()).add(c);
            }
        }
        return result;
    }

    /** Tổng số sản phẩm của 1 danh mục + toàn bộ hậu duệ (đệ quy). */
    public int subtreeCount(int categoryId, Map<Integer, List<ProductCategory>> childrenByParent,
            Map<Integer, Integer> directCounts) {
        int total = directCounts != null ? directCounts.getOrDefault(categoryId, 0) : 0;
        if (childrenByParent != null) {
            List<ProductCategory> children = childrenByParent.get(categoryId);
            if (children != null) {
                for (ProductCategory child : children) {
                    total += subtreeCount(child.getCategoryId(), childrenByParent, directCounts);
                }
            }
        }
        return total;
    }

    /** categoryId + mọi hậu duệ (đệ quy), dùng để lọc danh sách sản phẩm. */
    public List<Integer> subtreeIds(int categoryId, Map<Integer, List<ProductCategory>> childrenByParent) {
        List<Integer> result = new ArrayList<>();
        result.add(categoryId);
        if (childrenByParent != null) {
            List<ProductCategory> children = childrenByParent.get(categoryId);
            if (children != null) {
                for (ProductCategory child : children) {
                    result.addAll(subtreeIds(child.getCategoryId(), childrenByParent));
                }
            }
        }
        return result;
    }

    /** category_id đang lọc cùng toàn bộ tổ tiên để mở sẵn accordion. */
    public Set<Integer> expandedCategoryIds(List<ProductCategory> categoryList, Integer categoryFilter) {
        Set<Integer> result = new HashSet<>();
        if (categoryFilter == null || categoryList == null) {
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

    /** Số sản phẩm hiển thị cạnh mỗi danh mục = cộng dồn toàn bộ con/cháu. */
    public Map<Integer, Integer> subtreeCategoryCounts(List<ProductCategory> categoryList,
            Map<Integer, List<ProductCategory>> childrenByParent, Map<Integer, Integer> directCounts) {
        Map<Integer, Integer> result = new HashMap<>();
        if (categoryList == null) return result;
        for (ProductCategory c : categoryList) {
            result.put(c.getCategoryId(), subtreeCount(c.getCategoryId(), childrenByParent, directCounts));
        }
        return result;
    }

    /** Danh sách chọn danh mục lá kèm đường dẫn nhánh (vd: "CNTT & IOT › LoRa › Sensors"). */
    public Map<Integer, String> categoryOptions(List<ProductCategory> categoryList, Integer keepCategoryId) {
        Map<Integer, String> result = new LinkedHashMap<>();
        if (categoryList == null) return result;
        Map<Integer, List<ProductCategory>> children = childrenByParent(categoryList);
        Set<Integer> ids = new HashSet<>();
        for (ProductCategory c : categoryList) {
            ids.add(c.getCategoryId());
        }
        for (ProductCategory c : categoryList) {
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

    // ------------------------------------------------------------------
    // Danh sách & Chi tiết sản phẩm (UC-12, UC-13, BR-19)
    // ------------------------------------------------------------------

    public ProductListPage findProducts(int page, int pageSize, String keyword, Integer categoryFilter) {
        int safePage = Math.max(1, page);
        int safePageSize = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;

        List<Integer> categoryIdsToQuery = null;
        if (categoryFilter != null) {
            List<ProductCategory> categoryList = findAllCategories();
            categoryIdsToQuery = subtreeIds(categoryFilter, childrenByParent(categoryList));
        }

        List<Product> products = productDAO.findAll(safePage, safePageSize, keyword, categoryIdsToQuery);
        int totalCount = productDAO.countAll(keyword, categoryIdsToQuery);
        return new ProductListPage(products, totalCount, safePage, safePageSize);
    }

    public int countProducts(String keyword, Integer categoryFilter) {
        List<Integer> categoryIdsToQuery = null;
        if (categoryFilter != null) {
            List<ProductCategory> categoryList = findAllCategories();
            categoryIdsToQuery = subtreeIds(categoryFilter, childrenByParent(categoryList));
        }
        return productDAO.countAll(keyword, categoryIdsToQuery);
    }

    public int grandTotal() {
        return productDAO.countAll(null, null);
    }

    public Map<Integer, Integer> countByCategory() {
        return productDAO.countByCategory();
    }

    public Product findById(int productId) {
        if (productId <= 0) return null;
        return productDAO.findById(productId);
    }

    public List<Contract> findContractsByProductId(int productId) {
        if (productId <= 0) return Collections.emptyList();
        return productDAO.findContractsByProductId(productId);
    }

    /** BR-19: Chi tiết sản phẩm gồm: thông tin, hình ảnh, catalogue, hợp đồng sử dụng. */
    public ServiceResult<ProductDetail> getProductDetail(int productId) {
        if (productId <= 0) {
            return failure("invalid");
        }
        Product product = findById(productId);
        if (product == null) {
            return failure("notfound");
        }
        List<Contract> contracts = findContractsByProductId(productId);
        return success(new ProductDetail(product, contracts));
    }

    // ------------------------------------------------------------------
    // Thêm sản phẩm (UC-19, BR-48, BR-54, BR-55, MSG-056)
    // ------------------------------------------------------------------

    public ServiceResult<ProductSaveResult> createProduct(Product product) {
        return createProduct(product, Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Thêm sản phẩm mới kèm kiểm tra ràng buộc BR-54, BR-48 và tự sinh mã BR-55 (thử lại 5 lần).
     */
    public ServiceResult<ProductSaveResult> createProduct(Product product,
            Collection<Part> imageParts, Collection<Part> catalogueParts) {
        if (product == null) {
            return failure("invalid");
        }
        // BR-54: Bắt buộc Tên sản phẩm và Danh mục
        if (!productValidator.isValidCommonFields(product)) {
            return failure("invalid");
        }
        List<ProductCategory> allCategories = findAllCategories();
        if (!categoryOptions(allCategories, null).containsKey(product.getCategoryId())) {
            return failure("invalid_category");
        }
        // BR-48: Kiểm tra đuôi file ảnh và catalogue
        if (!areImagePartsAcceptable(imageParts)) {
            return failure("invalid_image_type");
        }
        if (!areCataloguePartsAcceptable(catalogueParts)) {
            return failure("invalid_catalogue_type");
        }

        // BR-54, BR-55: Mã do hệ thống sinh, người dùng không nhập tay
        product.setProductCode(productDAO.generateNextProductCode());
        if (product.getProductCode() == null) {
            return failure("create_failed");
        }

        // BR-55, MSG-056: insert dựa trên ràng buộc UNIQUE ở DB, trùng thì thử lại tối đa 5 lần không báo lỗi
        int newId = productDAO.insert(product);
        if (newId <= 0) {
            log.warning("Them san pham that bai (productCode=" + product.getProductCode() + ")");
            return failure("create_failed");
        }

        int notAdded = saveNewImages(newId, imageParts) + saveNewCatalogues(newId, catalogueParts);
        return success(new ProductSaveResult(newId, 0, notAdded));
    }

    // ------------------------------------------------------------------
    // Cập nhật sản phẩm (UC-18, BR-48, BR-54, BR-55)
    // ------------------------------------------------------------------

    public ServiceResult<ProductSaveResult> updateProduct(Product product) {
        return updateProduct(product, null, null, Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Cập nhật thông tin sản phẩm. Mã sản phẩm KHÔNG ĐỔI sau khi tạo (BR-55).
     */
    public ServiceResult<ProductSaveResult> updateProduct(Product product,
            String removedImageIdsCsv, String removedCatalogueIdsCsv,
            Collection<Part> imageParts, Collection<Part> catalogueParts) {
        if (product == null || product.getProductId() <= 0) {
            return failure("invalid");
        }
        Product existing = productDAO.findById(product.getProductId());
        if (existing == null) {
            return failure("notfound");
        }
        // BR-54: Bắt buộc Tên sản phẩm và Danh mục
        if (!productValidator.isValidCommonFields(product)) {
            return failure("invalid");
        }
        List<ProductCategory> allCategories = findAllCategories();
        if (!categoryOptions(allCategories, existing.getCategoryId()).containsKey(product.getCategoryId())) {
            return failure("invalid_category");
        }
        // BR-48: Kiểm tra file
        if (!areImagePartsAcceptable(imageParts)) {
            return failure("invalid_image_type");
        }
        if (!areCataloguePartsAcceptable(catalogueParts)) {
            return failure("invalid_catalogue_type");
        }

        // BR-55: Mã sản phẩm KHÔNG ĐỔI sau khi tạo
        product.setProductCode(existing.getProductCode());

        boolean ok = productDAO.update(product);
        if (!ok) {
            log.warning("Cap nhat san pham that bai (productId=" + product.getProductId() + ")");
            return failure("update_failed");
        }

        return success(processFileChanges(product.getProductId(), removedImageIdsCsv,
                removedCatalogueIdsCsv, existing, imageParts, catalogueParts));
    }

    private ProductSaveResult processFileChanges(int productId, String removedImageIdsCsv,
            String removedCatalogueIdsCsv, Product existing,
            Collection<Part> imageParts, Collection<Part> catalogueParts) {
        int notRemoved = removeMarkedFiles(productId, removedImageIdsCsv, existing, true)
                + removeMarkedFiles(productId, removedCatalogueIdsCsv, existing, false);
        int notAdded = saveNewImages(productId, imageParts)
                + saveNewCatalogues(productId, catalogueParts);
        return new ProductSaveResult(productId, notRemoved, notAdded);
    }

    // ------------------------------------------------------------------
    // Xóa mềm sản phẩm (UC-20, BR-49)
    // ------------------------------------------------------------------

    public boolean isUsedInContracts(int productId) {
        return productId > 0 && productDAO.isUsedInContracts(productId);
    }

    /**
     * Xóa mềm sản phẩm (is_deleted = 1). Chặn khi sản phẩm nằm trong hợp đồng còn hiệu lực.
     */
    public ServiceResult<Void> deleteProduct(int productId) {
        if (productId <= 0) {
            return failure("invalid");
        }
        Product existing = productDAO.findById(productId);
        if (existing == null) {
            return failure("notfound");
        }
        // UC-20, BR-49: chặn nếu sản phẩm nằm trong hợp đồng còn hiệu lực
        if (productDAO.isUsedInContracts(productId)) {
            return failure("has_active_contracts");
        }
        boolean ok = productDAO.softDelete(productId);
        if (!ok) {
            log.warning("Xoa san pham that bai (productId=" + productId + ")");
            return failure("delete_failed");
        }
        return success(null);
    }

    // ------------------------------------------------------------------
    // Helpers: Upload & Quản lý file ảnh/catalogue (BR-48)
    // ------------------------------------------------------------------

    public boolean areImagePartsAcceptable(Collection<Part> imageParts) {
        if (imageParts == null || imageParts.isEmpty()) return true;
        for (Part part : imageParts) {
            if (!FileStorage.isAcceptable(part, FileStorage.IMAGE_EXTENSIONS)) {
                return false;
            }
        }
        return true;
    }

    public boolean areCataloguePartsAcceptable(Collection<Part> catalogueParts) {
        if (catalogueParts == null || catalogueParts.isEmpty()) return true;
        for (Part part : catalogueParts) {
            if (!FileStorage.isAcceptable(part, FileStorage.DOCUMENT_EXTENSIONS)) {
                return false;
            }
        }
        return true;
    }

    public boolean areImageFileNamesAcceptable(Collection<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) return true;
        for (String name : fileNames) {
            if (name != null && !name.trim().isEmpty() && !productValidator.isValidImageExtension(name)) {
                return false;
            }
        }
        return true;
    }

    public boolean areCatalogueFileNamesAcceptable(Collection<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) return true;
        for (String name : fileNames) {
            if (name != null && !name.trim().isEmpty() && !productValidator.isValidCatalogueExtension(name)) {
                return false;
            }
        }
        return true;
    }

    public int saveNewImages(int productId, Collection<Part> imageParts) {
        if (imageParts == null || imageParts.isEmpty()) return 0;
        int failed = 0;
        for (Part part : imageParts) {
            if (part == null || part.getSize() <= 0) continue;
            try {
                String url = FileStorage.save(part, IMAGE_SUBFOLDER, FileStorage.IMAGE_EXTENSIONS);
                if (url == null || productDAO.addImage(productId, url) <= 0) {
                    failed++;
                }
            } catch (IOException ex) {
                log.log(Level.WARNING, "Loi luu anh san pham (productId=" + productId + ")", ex);
                failed++;
            }
        }
        return failed;
    }

    public int saveNewCatalogues(int productId, Collection<Part> catalogueParts) {
        if (catalogueParts == null || catalogueParts.isEmpty()) return 0;
        int failed = 0;
        for (Part part : catalogueParts) {
            if (part == null || part.getSize() <= 0) continue;
            try {
                String url = FileStorage.save(part, CATALOGUE_SUBFOLDER, FileStorage.DOCUMENT_EXTENSIONS);
                if (url == null || productDAO.addCatalogue(productId, url, part.getSubmittedFileName()) <= 0) {
                    failed++;
                }
            } catch (IOException ex) {
                log.log(Level.WARNING, "Loi luu catalogue san pham (productId=" + productId + ")", ex);
                failed++;
            }
        }
        return failed;
    }

    public int removeMarkedFiles(int productId, String removedIdsCsv, Product existing, boolean isImage) {
        if (removedIdsCsv == null || removedIdsCsv.trim().isEmpty() || existing == null) {
            return 0;
        }
        Set<Integer> present = new HashSet<>();
        boolean hasList = false;
        if (isImage && existing.getImages() != null) {
            hasList = true;
            existing.getImages().forEach(img -> present.add(img.getImageId()));
        } else if (!isImage && existing.getCatalogues() != null) {
            hasList = true;
            existing.getCatalogues().forEach(cat -> present.add(cat.getCatalogueId()));
        }
        int failed = 0;
        for (String token : removedIdsCsv.split(",")) {
            Integer fileId = parseIntOrNull(token);
            if (fileId == null || (hasList && !present.contains(fileId))) {
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

    public static String filesNotSavedQuery(int notRemoved, int notAdded) {
        if (notRemoved == 0 && notAdded == 0) return "";
        return "&error=files_not_saved&notRemoved=" + notRemoved + "&notAdded=" + notAdded;
    }

    public String generateNextProductCode() {
        return productDAO.generateNextProductCode();
    }

    public String nextProductCodeAfter(String previousCode) {
        return productDAO.nextProductCodeAfter(previousCode);
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
