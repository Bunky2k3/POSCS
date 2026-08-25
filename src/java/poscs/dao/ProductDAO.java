package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import poscs.model.Contract;
import poscs.model.Enterprise;
import poscs.model.Product;
import poscs.model.ProductCatalogue;
import poscs.model.ProductCategory;
import poscs.model.ProductImage;

/**
 * DAO cho sản phẩm (bảng products), ảnh/catalogue của sản phẩm (bảng
 * productimages/productcatalogues -- một sản phẩm có thể có nhiều ảnh và
 * nhiều file catalogue, khác với thiết kế cột đơn trước đây) và danh mục
 * sản phẩm (productcategories). Danh mục chỉ có truy vấn đọc ở đây
 * (findAllCategories) để đổ dropdown -- chưa có màn hình quản lý riêng cho
 * productcategories.
 */
public class ProductDAO {

    private static final String SELECT_BASE =
        "SELECT p.product_id, p.product_code, p.product_name, p.description, " +
        "       p.category_id, p.created_at, p.updated_at, p.is_deleted, " +
        "       pc.category_name, pc.parent_category_id, pc.display_order " +
        "FROM products p " +
        "LEFT JOIN productcategories pc ON p.category_id = pc.category_id ";

    /**
     * Lấy danh sách sản phẩm có phân trang + lọc, phục vụ listProduct.jsp
     * (mỗi sản phẩm đã kèm sẵn .primaryImageUrl để làm ảnh đại diện thẻ).
     */
    public List<Product> findAll(int page, int pageSize, String keyword, Integer categoryId) {
        List<Product> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, categoryId);
        sql.append(" ORDER BY p.product_id DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(Math.max(0, (page - 1) * pageSize));

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN DANH SACH SAN PHAM ---");
            ex.printStackTrace();
        }
        // Danh sách chỉ cần 1 ảnh đại diện/sản phẩm (không cần load hết ảnh +
        // catalogue như trang chi tiết) -- PAGE_SIZE nhỏ (10) nên N truy vấn
        // nhỏ theo PK ở đây rẻ hơn nhiều so với 1 câu JOIN phức tạp.
        for (Product p : result) {
            String primaryUrl = findPrimaryImageUrl(p.getProductId());
            if (primaryUrl != null) {
                ProductImage img = new ProductImage();
                img.setProductId(p.getProductId());
                img.setImageUrl(primaryUrl);
                List<ProductImage> single = new ArrayList<>();
                single.add(img);
                p.setImages(single);
            }
        }
        return result;
    }

    /** Đếm tổng số sản phẩm thoả điều kiện lọc, phục vụ phân trang (BR-12). */
    public int countAll(String keyword, Integer categoryId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM products p ");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, categoryId);

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI DEM SO LUONG SAN PHAM ---");
            ex.printStackTrace();
        }
        return 0;
    }

    /** Tra 1 sản phẩm theo mã (product_code), phục vụ nhập PDF hợp đồng. Trả về null nếu không tồn tại. */
    public Product findByCode(String productCode) {
        String sql = "SELECT product_id, product_code, product_name FROM products WHERE product_code = ? AND is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Product p = new Product();
                    p.setProductId(rs.getInt("product_id"));
                    p.setProductCode(rs.getString("product_code"));
                    p.setProductName(rs.getString("product_name"));
                    return p;
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRA SAN PHAM THEO MA ---");
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Lấy chi tiết 1 sản phẩm theo ID, đã join danh mục + toàn bộ ảnh/catalogue
     * (theo display_order). Trả về null nếu không tồn tại.
     */
    public Product findById(int productId) {
        String sql = SELECT_BASE + "WHERE p.product_id = ? AND p.is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Product p = mapRow(rs);
                    p.setImages(findImagesByProductId(productId));
                    p.setCatalogues(findCataloguesByProductId(productId));
                    return p;
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN CHI TIET SAN PHAM ---");
            ex.printStackTrace();
        }
        return null;
    }

    /** Lấy toàn bộ danh mục sản phẩm còn hiệu lực, phục vụ dropdown "Danh mục". */
    public List<ProductCategory> findAllCategories() {
        List<ProductCategory> result = new ArrayList<>();
        String sql = "SELECT category_id, category_name, parent_category_id, display_order " +
                     "FROM productcategories WHERE is_deleted = 0 ORDER BY display_order, category_name";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapCategoryRow(rs));
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN DANH MUC SAN PHAM ---");
            ex.printStackTrace();
        }
        return result;
    }

    /**
     * Đếm số sản phẩm còn hiệu lực theo từng danh mục, phục vụ panel "Danh mục
     * sản phẩm" ở listProduct.jsp (mỗi danh mục hiển thị kèm số lượng, theo
     * đúng phong cách trang postef.com.vn/san-pham/).
     */
    public Map<Integer, Integer> countByCategory() {
        Map<Integer, Integer> result = new HashMap<>();
        String sql = "SELECT category_id, COUNT(*) AS cnt FROM products WHERE is_deleted = 0 GROUP BY category_id";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getInt("category_id"), rs.getInt("cnt"));
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI DEM SAN PHAM THEO DANH MUC ---");
            ex.printStackTrace();
        }
        return result;
    }

    /**
     * Lấy danh sách hợp đồng có sử dụng sản phẩm này (qua contractproducts),
     * phục vụ mục "Hợp đồng sử dụng sản phẩm này" ở viewdetailProduct.jsp.
     */
    public List<Contract> findContractsByProductId(int productId) {
        List<Contract> result = new ArrayList<>();
        String sql = "SELECT DISTINCT c.contract_id, c.contract_code, c.title, c.contract_type, " +
                     "       c.signing_date, c.effective_date, c.end_date, c.enterprise_id, c.owner_id, " +
                     "       c.attachment_url, e.enterprise_name " +
                     "FROM contractproducts cp " +
                     "JOIN contracts c ON cp.contract_id = c.contract_id " +
                     "LEFT JOIN enterprises e ON c.enterprise_id = e.enterprise_id " +
                     "WHERE cp.product_id = ? AND c.is_deleted = 0 " +
                     "ORDER BY c.contract_id DESC";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Contract c = new Contract();
                    c.setContractId(rs.getInt("contract_id"));
                    c.setContractCode(rs.getString("contract_code"));
                    c.setTitle(rs.getString("title"));
                    c.setContractType(rs.getString("contract_type"));
                    c.setSigningDate(rs.getDate("signing_date"));
                    c.setEffectiveDate(rs.getDate("effective_date"));
                    c.setEndDate(rs.getDate("end_date"));
                    c.setEnterpriseId(rs.getInt("enterprise_id"));
                    c.setOwnerId(rs.getInt("owner_id"));
                    c.setAttachmentUrl(rs.getString("attachment_url"));
                    c.setStatus(computeContractStatus(c.getEffectiveDate(), c.getEndDate()));

                    String enterpriseName = rs.getString("enterprise_name");
                    if (enterpriseName != null) {
                        Enterprise e = new Enterprise();
                        e.setEnterpriseId(c.getEnterpriseId());
                        e.setEnterpriseName(enterpriseName);
                        c.setEnterprise(e);
                    }
                    result.add(c);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN HOP DONG THEO SAN PHAM ---");
            ex.printStackTrace();
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Ảnh sản phẩm (productimages)
    // ------------------------------------------------------------------

    /** Toàn bộ ảnh của 1 sản phẩm, theo display_order -- phục vụ trang chi tiết/sửa. */
    public List<ProductImage> findImagesByProductId(int productId) {
        List<ProductImage> result = new ArrayList<>();
        String sql = "SELECT image_id, product_id, image_url, display_order, created_at " +
                     "FROM productimages WHERE product_id = ? ORDER BY display_order, image_id";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ProductImage img = new ProductImage();
                    img.setImageId(rs.getInt("image_id"));
                    img.setProductId(rs.getInt("product_id"));
                    img.setImageUrl(rs.getString("image_url"));
                    img.setDisplayOrder(rs.getInt("display_order"));
                    img.setCreatedAt(rs.getTimestamp("created_at"));
                    result.add(img);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN ANH SAN PHAM ---");
            ex.printStackTrace();
        }
        return result;
    }

    /** Ảnh đầu tiên (display_order nhỏ nhất) của 1 sản phẩm, hoặc null nếu chưa có ảnh nào. */
    public String findPrimaryImageUrl(int productId) {
        String sql = "SELECT image_url FROM productimages WHERE product_id = ? " +
                     "ORDER BY display_order, image_id LIMIT 1";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("image_url") : null;
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN ANH DAI DIEN SAN PHAM ---");
            ex.printStackTrace();
            return null;
        }
    }

    /** Thêm 1 ảnh vào cuối danh sách ảnh hiện có của sản phẩm. Trả về image_id vừa tạo, hoặc -1 nếu lỗi. */
    public int addImage(int productId, String imageUrl) {
        String sql = "INSERT INTO productimages (product_id, image_url, display_order) " +
                     "VALUES (?, ?, ?)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, productId);
            ps.setString(2, imageUrl);
            ps.setInt(3, nextDisplayOrder(conn, "productimages", productId));
            if (ps.executeUpdate() == 0) {
                return -1;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI THEM ANH SAN PHAM ---");
            ex.printStackTrace();
            return -1;
        }
    }

    /**
     * Xoá 1 ảnh -- kèm điều kiện product_id để không cho xoá nhầm/cố ý ảnh
     * của sản phẩm khác qua imageId gửi lên từ form (removedImageIds).
     */
    public boolean deleteImage(int imageId, int productId) {
        String sql = "DELETE FROM productimages WHERE image_id = ? AND product_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, imageId);
            ps.setInt(2, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            System.err.println("--- LOI XOA ANH SAN PHAM ---");
            ex.printStackTrace();
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Catalogue sản phẩm (productcatalogues)
    // ------------------------------------------------------------------

    /** Toàn bộ file catalogue của 1 sản phẩm, theo display_order -- phục vụ trang chi tiết/sửa. */
    public List<ProductCatalogue> findCataloguesByProductId(int productId) {
        List<ProductCatalogue> result = new ArrayList<>();
        String sql = "SELECT catalogue_id, product_id, catalogue_url, file_name, display_order, created_at " +
                     "FROM productcatalogues WHERE product_id = ? ORDER BY display_order, catalogue_id";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ProductCatalogue cat = new ProductCatalogue();
                    cat.setCatalogueId(rs.getInt("catalogue_id"));
                    cat.setProductId(rs.getInt("product_id"));
                    cat.setCatalogueUrl(rs.getString("catalogue_url"));
                    cat.setFileName(rs.getString("file_name"));
                    cat.setDisplayOrder(rs.getInt("display_order"));
                    cat.setCreatedAt(rs.getTimestamp("created_at"));
                    result.add(cat);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN CATALOGUE SAN PHAM ---");
            ex.printStackTrace();
        }
        return result;
    }

    /** Thêm 1 file catalogue vào cuối danh sách hiện có của sản phẩm. Trả về catalogue_id vừa tạo, hoặc -1 nếu lỗi. */
    public int addCatalogue(int productId, String catalogueUrl, String fileName) {
        String sql = "INSERT INTO productcatalogues (product_id, catalogue_url, file_name, display_order) " +
                     "VALUES (?, ?, ?, ?)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, productId);
            ps.setString(2, catalogueUrl);
            ps.setString(3, fileName);
            ps.setInt(4, nextDisplayOrder(conn, "productcatalogues", productId));
            if (ps.executeUpdate() == 0) {
                return -1;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI THEM CATALOGUE SAN PHAM ---");
            ex.printStackTrace();
            return -1;
        }
    }

    /**
     * Xoá 1 file catalogue -- kèm điều kiện product_id để không cho xoá nhầm/cố
     * ý file của sản phẩm khác qua catalogueId gửi lên từ form (removedCatalogueIds).
     */
    public boolean deleteCatalogue(int catalogueId, int productId) {
        String sql = "DELETE FROM productcatalogues WHERE catalogue_id = ? AND product_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, catalogueId);
            ps.setInt(2, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            System.err.println("--- LOI XOA CATALOGUE SAN PHAM ---");
            ex.printStackTrace();
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Sản phẩm (products)
    // ------------------------------------------------------------------

    /** Sinh mã sản phẩm tiếp theo dạng SP-0001, SP-0002, ... */
    public String generateNextProductCode() {
        String sql = "SELECT product_code FROM products ORDER BY product_id DESC LIMIT 1";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return nextProductCodeAfter(rs.next() ? rs.getString("product_code") : null);
        } catch (SQLException ex) {
            System.err.println("--- LOI SINH MA SAN PHAM ---");
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Tính mã sản phẩm kế tiếp dựa trên 1 mã product_code đã biết, không cần
     * đọc CSDL -- dùng khi nhập hàng loạt (import Excel) để không phải lặp
     * lại truy vấn SELECT MAX cho từng dòng, chỉ cần gọi generateNextProductCode()
     * 1 lần rồi tăng dần bằng hàm này.
     */
    public String nextProductCodeAfter(String previousCode) {
        int nextNumber = 1;
        if (previousCode != null) {
            String digits = previousCode.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                nextNumber = Integer.parseInt(digits) + 1;
            }
        }
        return String.format("SP-%04d", nextNumber);
    }

    /** Thử lại tối đa bao nhiêu lần khi product_code sinh ra bị trùng (xem insert()). */
    private static final int MAX_CODE_GEN_ATTEMPTS = 5;

    /** Thêm sản phẩm mới (chưa gồm ảnh/catalogue -- gọi addImage/addCatalogue sau khi có product_id). Trả về product_id vừa tạo, hoặc -1 nếu lỗi. */
    public int insert(Product product) {
        String sql = "INSERT INTO products (product_code, product_name, description, category_id) " +
                "VALUES (?, ?, ?, ?)";

        // Cùng lý do như CustomerDAO.insert()/ContractDAO.insert(): product_code sinh
        // từ generateNextProductCode() có thể trùng nếu 2 request tạo sản phẩm gần như
        // đồng thời cùng đọc được "mã lớn nhất" giống nhau -- cột product_code có UNIQUE
        // KEY (xem db/schema.sql) nên thử sinh mã mới và INSERT lại vài lần thay vì báo
        // lỗi ngay.
        for (int attempt = 1; attempt <= MAX_CODE_GEN_ATTEMPTS; attempt++) {
            try (Connection conn = DBContext.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, product.getProductCode());
                ps.setString(2, product.getProductName());
                ps.setString(3, product.getDescription());
                ps.setInt(4, product.getCategoryId());

                int affected = ps.executeUpdate();
                if (affected == 0) {
                    return -1;
                }
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            } catch (SQLException ex) {
                if (isDuplicateKeyError(ex, "product_code") && attempt < MAX_CODE_GEN_ATTEMPTS) {
                    product.setProductCode(generateNextProductCode());
                    continue;
                }
                System.err.println("--- LOI THEM SAN PHAM ---");
                ex.printStackTrace();
                return -1;
            }
        }
        return -1;
    }

    /** Cập nhật thông tin sản phẩm đang có (product_code giữ nguyên). Trả về true nếu thành công. */
    public boolean update(Product product) {
        String sql = "UPDATE products SET product_name = ?, description = ?, category_id = ? " +
                "WHERE product_id = ? AND is_deleted = 0";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, product.getProductName());
            ps.setString(2, product.getDescription());
            ps.setInt(3, product.getCategoryId());
            ps.setInt(4, product.getProductId());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            System.err.println("--- LOI CAP NHAT SAN PHAM ---");
            ex.printStackTrace();
            return false;
        }
    }

    /** Sản phẩm đang được dùng trong hợp đồng nào thì không cho xoá (contractproducts tham chiếu tới nó). */
    public boolean isUsedInContracts(int productId) {
        String sql = "SELECT COUNT(*) FROM contractproducts cp " +
                     "JOIN contracts c ON cp.contract_id = c.contract_id " +
                     "WHERE cp.product_id = ? AND c.is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI KIEM TRA SAN PHAM DANG DUOC SU DUNG ---");
            ex.printStackTrace();
            return true; // an toàn: nếu không kiểm tra được thì coi như có, chặn xoá
        }
    }

    /** Xoá mềm sản phẩm (is_deleted = 1). Gọi isUsedInContracts() trước để không mồ côi contractproducts. */
    public boolean softDelete(int productId) {
        String sql = "UPDATE products SET is_deleted = 1 WHERE product_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            System.err.println("--- LOI XOA SAN PHAM ---");
            ex.printStackTrace();
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Helpers riêng
    // ------------------------------------------------------------------

    private void appendFilters(StringBuilder sql, List<Object> params, String keyword, Integer categoryId) {
        List<String> conditions = new ArrayList<>();
        conditions.add("p.is_deleted = 0");

        if (keyword != null && !keyword.trim().isEmpty()) {
            conditions.add("(p.product_name LIKE ? OR p.product_code LIKE ?)");
            String likeValue = "%" + keyword.trim() + "%";
            params.add(likeValue);
            params.add(likeValue);
        }
        if (categoryId != null) {
            conditions.add("p.category_id = ?");
            params.add(categoryId);
        }

        sql.append("WHERE ").append(String.join(" AND ", conditions));
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    /** MAX(display_order) + 1 hiện có của sản phẩm trong bảng con `table` (productimages/productcatalogues), hoặc 0 nếu chưa có dòng nào. */
    private int nextDisplayOrder(Connection conn, String table, int productId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(display_order), -1) + 1 FROM " + table + " WHERE product_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** true nếu ex là lỗi trùng UNIQUE KEY của MySQL (error 1062) trên đúng cột keyColumn. */
    private boolean isDuplicateKeyError(SQLException ex, String keyColumn) {
        return ex.getErrorCode() == 1062 && ex.getMessage() != null && ex.getMessage().contains(keyColumn);
    }

    /**
     * Trạng thái hợp đồng theo cùng công thức BR-17 của ContractDAO (tính lại
     * theo ngày hiện tại thay vì đọc thẳng cột status đã lưu). Lặp lại công
     * thức ở đây thay vì gọi sang ContractDAO để không tạo phụ thuộc chéo
     * giữa 2 DAO chỉ vì 1 cột hiển thị phụ.
     */
    private String computeContractStatus(java.sql.Date effectiveDate, java.sql.Date endDate) {
        if (effectiveDate == null || endDate == null) {
            return ContractDAO.STATUS_DRAFT;
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate effective = effectiveDate.toLocalDate();
        java.time.LocalDate end = endDate.toLocalDate();

        if (today.isBefore(effective)) {
            return ContractDAO.STATUS_DRAFT;
        }
        if (today.isAfter(end)) {
            return ContractDAO.STATUS_EXPIRED;
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(today, end) <= 30) {
            return ContractDAO.STATUS_SOON;
        }
        return ContractDAO.STATUS_ACTIVE;
    }

    private ProductCategory mapCategoryRow(ResultSet rs) throws SQLException {
        ProductCategory cat = new ProductCategory();
        cat.setCategoryId(rs.getInt("category_id"));
        cat.setCategoryName(rs.getString("category_name"));
        int parentId = rs.getInt("parent_category_id");
        cat.setParentCategoryId(rs.wasNull() ? null : parentId);
        cat.setDisplayOrder(rs.getInt("display_order"));
        return cat;
    }

    private Product mapRow(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setProductId(rs.getInt("product_id"));
        p.setProductCode(rs.getString("product_code"));
        p.setProductName(rs.getString("product_name"));
        p.setDescription(rs.getString("description"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        p.setUpdatedAt(rs.getTimestamp("updated_at"));
        p.setDeleted(rs.getBoolean("is_deleted"));

        String categoryName = rs.getString("category_name");
        if (categoryName != null) {
            ProductCategory cat = new ProductCategory();
            cat.setCategoryId(p.getCategoryId());
            cat.setCategoryName(categoryName);
            int parentId = rs.getInt("parent_category_id");
            cat.setParentCategoryId(rs.wasNull() ? null : parentId);
            cat.setDisplayOrder(rs.getInt("display_order"));
            p.setCategory(cat);
        }

        return p;
    }
}
