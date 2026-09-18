package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.Period;
import poscs.common.SqlFilters;
import poscs.model.Address;
import poscs.model.CustomerLifecycleEvent;
import poscs.model.District;
import poscs.model.Enterprise;
import poscs.model.EnterpriseContact;
import poscs.model.Province;
import poscs.model.RelationshipRating;
import poscs.model.User;

/**
 * DAO cho khách hàng doanh nghiệp (bảng enterprises) và người liên hệ
 * (enterprisecontacts). Không xử lý contracts/technicalrequests -- các
 * bảng đó thuộc về ContractDAO/TechnicalSupportTicketDAO.
 */
public class CustomerDAO {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerDAO.class);

    private static final String SELECT_ENTERPRISE_BASE =
        "SELECT e.enterprise_id, e.enterprise_code, e.enterprise_name, e.customer_type, e.customer_group, " +
        "       e.tax_code, e.email AS ent_email, e.phone AS ent_phone, e.website, e.address_id, " +
        "       e.account_owner_id, e.support_owner_id, " +
        "       e.legal_representative, e.logo_url, e.business_license_url, e.status, e.join_date, " +
        "       e.current_relationship_rating, e.created_at, e.updated_at, e.is_deleted, " +
        "       a.street_and_local_name, a.districts_id AS addr_districts_id, " +
        "       d.districts_name, d.province_id AS dist_province_id, " +
        "       p.province_name, " +
        "       u.last_name AS owner_last_name, u.middle_name AS owner_middle_name, u.first_name AS owner_first_name, " +
        "       s.last_name AS support_last_name, s.middle_name AS support_middle_name, s.first_name AS support_first_name " +
        "FROM enterprises e " +
        "LEFT JOIN addresses a ON e.address_id = a.address_id " +
        "LEFT JOIN districts d ON a.districts_id = d.districts_id " +
        "LEFT JOIN provinces p ON d.province_id = p.province_id " +
        "LEFT JOIN users u ON e.account_owner_id = u.user_id " +
        "LEFT JOIN users s ON e.support_owner_id = s.user_id ";

    /**
     * Lấy danh sách khách hàng có phân trang + lọc, phục vụ listcustomer.jsp.
     * @param page 1-indexed
     */
    public List<Enterprise> findAll(int page, int pageSize, String keyword, String customerType, Integer accountOwnerId) {
        return findAll(page, pageSize, keyword, customerType, accountOwnerId, null, false, null);
    }

    /**
     * Như {@link #findAll(int, int, String, String, Integer)} nhưng lọc thêm theo
     * tỉnh/thành và cho phép sắp xếp theo tỉnh -- phục vụ quản lý khách hàng theo
     * địa bàn.
     *
     * @param provinceId     lọc theo tỉnh của địa chỉ khách hàng, null = mọi tỉnh
     * @param sortByProvince true thì sắp theo tên tỉnh (khách chưa có địa chỉ dồn
     *                       xuống cuối) thay vì mới nhất trước; dùng khi xuất Excel
     *                       để các dòng cùng tỉnh nằm liền nhau
     */
    public List<Enterprise> findAll(int page, int pageSize, String keyword, String customerType,
            Integer accountOwnerId, Integer provinceId, boolean sortByProvince, String role) {
        List<Enterprise> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_ENTERPRISE_BASE);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, customerType, accountOwnerId, provinceId, role);
        // "p.province_name IS NULL" đứng đầu để dồn khách chưa có địa chỉ xuống
        // cuối file -- mặc định MySQL xếp NULL lên đầu khi ORDER BY tăng dần.
        sql.append(sortByProvince
                ? " ORDER BY p.province_name IS NULL, " + AddressDAO.PROVINCE_SHORT_NAME_ORDER
                        + ", e.enterprise_id DESC LIMIT ? OFFSET ?"
                : " ORDER BY e.enterprise_id DESC LIMIT ? OFFSET ?");
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
            LOG.error("Loi truy van danh sach khach hang (accountOwnerId={})", accountOwnerId, ex);
        }
        return result;
    }

    /** Đếm tổng số khách hàng thoả điều kiện lọc, phục vụ phân trang (BR-12). */
    public int countAll(String keyword, String customerType, Integer accountOwnerId) {
        return countAll(keyword, customerType, accountOwnerId, null, null);
    }

    /** Như {@link #countAll(String, String, Integer)} nhưng lọc thêm theo tỉnh/thành. */
    public int countAll(String keyword, String customerType, Integer accountOwnerId, Integer provinceId, String role) {
        // Phải JOIN tới districts thì mới lọc được theo tỉnh; districts đã có sẵn
        // province_id nên không cần join thêm bảng provinces chỉ để đếm.
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM enterprises e "
                + "LEFT JOIN addresses a ON e.address_id = a.address_id "
                + "LEFT JOIN districts d ON a.districts_id = d.districts_id ");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, customerType, accountOwnerId, provinceId, role);

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi dem so luong khach hang (accountOwnerId={})", accountOwnerId, ex);
        }
        return 0;
    }

    /** Đếm số khách hàng có ngày tham gia (join_date) rơi vào tháng hiện tại, phục vụ KPI dashboard. */
    public int countNewThisMonth() {
        return countNewThisMonth(null);
    }

    /** Như {@link #countNewThisMonth()} nhưng chỉ đếm khách thuộc 1 tỉnh (null = toàn quốc). */
    public int countNewThisMonth(Integer provinceId) {
        return countJoined(provinceId, null, false, null);
    }

    /**
     * Đếm khách hàng MỚI trong kỳ (join_date nằm trong kỳ). period null thì
     * quay về nghĩa cũ "trong tháng hiện tại".
     */
    public int countNewInPeriod(Integer provinceId, Period period) {
        return countJoined(provinceId, period, false, null);
    }

    /** Như trên nhưng chỉ đếm khách do những người này phụ trách (rỗng/null = tất cả). */
    public int countNewInPeriod(Integer provinceId, Period period, List<Integer> ownerIds) {
        return countJoined(provinceId, period, false, ownerIds);
    }

    /**
     * Đếm LUỸ KẾ số khách hàng tính tới hết kỳ (join_date <= ngày cuối kỳ).
     * Khác countNewInPeriod: "tổng khách hàng" là con số tích luỹ, chọn quý 2
     * mà chỉ đếm khách gia nhập trong quý 2 thì ô đó thành "khách mới" thứ hai
     * trên cùng một trang. period null = đếm toàn bộ, không giới hạn thời gian.
     */
    public int countUpToEndOfPeriod(Integer provinceId, Period period) {
        return countJoined(provinceId, period, true, null);
    }

    /** Như trên nhưng chỉ đếm khách do những người này phụ trách (rỗng/null = tất cả). */
    public int countUpToEndOfPeriod(Integer provinceId, Period period, List<Integer> ownerIds) {
        return countJoined(provinceId, period, true, ownerIds);
    }

    private int countJoined(Integer provinceId, Period period, boolean cumulative, List<Integer> ownerIds) {
        String dateCondition;
        if (period == null) {
            // Không chọn kỳ: luỹ kế = toàn bộ; "mới" = trong tháng hiện tại.
            dateCondition = cumulative
                    ? ""
                    : " AND YEAR(e.join_date) = YEAR(CURDATE()) AND MONTH(e.join_date) = MONTH(CURDATE())";
        } else {
            dateCondition = cumulative ? " AND e.join_date <= ?" : " AND e.join_date BETWEEN ? AND ?";
        }
        String sql = "SELECT COUNT(*) FROM enterprises e " +
                     "LEFT JOIN addresses a ON e.address_id = a.address_id " +
                     "LEFT JOIN districts d ON a.districts_id = d.districts_id " +
                     "WHERE e.is_deleted = 0" + dateCondition +
                     (provinceId != null ? " AND d.province_id = ?" : "") +
                     // "Khách của tôi" = khách mà tôi (hoặc cấp dưới của tôi) đứng
                     // tên phụ trách, đúng cột mà màn hình khách hàng đang hiện.
                     SqlFilters.inClause("e.account_owner_id", ownerIds);
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int param = 1;
            if (period != null) {
                if (!cumulative) {
                    ps.setDate(param++, period.getFrom());
                }
                ps.setDate(param++, period.getTo());
            }
            if (provinceId != null) {
                ps.setInt(param++, provinceId);
            }
            SqlFilters.bind(ps, param, ownerIds);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi dem khach hang theo ky", ex);
        }
        return 0;
    }

    /** Lấy chi tiết 1 khách hàng theo ID, đã join địa chỉ + người phụ trách. Trả về null nếu không tồn tại. */
    public Enterprise findById(int enterpriseId) {
        String sql = SELECT_ENTERPRISE_BASE + "WHERE e.enterprise_id = ? AND e.is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enterpriseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van chi tiet khach hang (enterpriseId={})", enterpriseId, ex);
        }
        return null;
    }

    /** Lấy danh sách người liên hệ của 1 khách hàng, phục vụ viewcustomerdetail.jsp. */
    public List<EnterpriseContact> findContactsByEnterpriseId(int enterpriseId) {
        List<EnterpriseContact> result = new ArrayList<>();
        String sql = "SELECT contact_id, enterprise_id, contact_last_name, contact_middle_name, " +
                     "contact_first_name, contact_phone, contact_email, position " +
                     "FROM enterprisecontacts WHERE enterprise_id = ? ORDER BY contact_id";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enterpriseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EnterpriseContact c = new EnterpriseContact();
                    c.setContactId(rs.getInt("contact_id"));
                    c.setEnterpriseId(rs.getInt("enterprise_id"));
                    c.setContactLastName(rs.getString("contact_last_name"));
                    c.setContactMiddleName(rs.getString("contact_middle_name"));
                    c.setContactFirstName(rs.getString("contact_first_name"));
                    c.setContactPhone(rs.getString("contact_phone"));
                    c.setContactEmail(rs.getString("contact_email"));
                    c.setPosition(rs.getString("position"));
                    result.add(c);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van nguoi lien he (enterpriseId={})", enterpriseId, ex);
        }
        return result;
    }

    /** Sinh mã khách hàng tiếp theo dạng KH-0001, KH-0002, ... */
    public String generateNextEnterpriseCode() {
        String sql = "SELECT enterprise_code FROM enterprises ORDER BY enterprise_id DESC LIMIT 1";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return nextEnterpriseCodeAfter(rs.next() ? rs.getString("enterprise_code") : null);
        } catch (SQLException ex) {
            LOG.error("Loi sinh ma khach hang", ex);
            return null;
        }
    }

    /**
     * Tính mã khách hàng kế tiếp dựa trên 1 mã enterprise_code đã biết, không
     * cần đọc CSDL -- dùng khi nhập hàng loạt (import Excel/PDF) để không phải
     * lặp lại truy vấn SELECT MAX cho từng dòng, chỉ cần gọi generateNextEnterpriseCode()
     * 1 lần rồi tăng dần bằng hàm này.
     */
    public String nextEnterpriseCodeAfter(String previousCode) {
        int nextNumber = 1;
        if (previousCode != null) {
            String digits = previousCode.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                nextNumber = Integer.parseInt(digits) + 1;
            }
        }
        return String.format("KH-%04d", nextNumber);
    }

    /**
     * Thêm khách hàng mới. Nếu enterprise.getAddress() có street/district thì tự tạo
     * dòng addresses trước rồi mới gán address_id. Trả về enterprise_id vừa tạo, hoặc -1 nếu lỗi.
     */
    /** Thử lại tối đa bao nhiêu lần khi enterprise_code sinh ra bị trùng (xem insert()). */
    private static final int MAX_CODE_GEN_ATTEMPTS = 5;

    public int insert(Enterprise enterprise) {
        String sql = "INSERT INTO enterprises " +
                "(enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, " +
                " website, address_id, account_owner_id, support_owner_id, legal_representative, logo_url, " +
                " business_license_url, status, join_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // enterprise_code sinh từ generateNextEnterpriseCode() (đọc mã lớn nhất
        // hiện có rồi +1) có thể trùng nếu 2 request tạo khách hàng gần như đồng
        // thời cùng đọc được "mã lớn nhất" giống nhau -- cột enterprise_code có
        // UNIQUE KEY (xem db/schema.sql) nên lần INSERT bị trùng sẽ bị DB từ chối
        // thay vì âm thầm ghi đè; thử sinh mã mới và INSERT lại vài lần thay vì báo
        // lỗi ngay, để người dùng không phải tự bấm lưu lại. Mỗi lần thử là 1
        // transaction riêng (setAutoCommit(false) bên dưới) gồm cả insertAddress()
        // lẫn INSERT enterprises -- nên nếu 1 lần thử thất bại (vì trùng mã hay bất
        // kỳ lý do gì khác) và bị rollback, dòng addresses vừa tạo trong lần thử đó
        // cũng bị rollback theo, không để lại bản ghi mồ côi qua các lần retry.
        for (int attempt = 1; attempt <= MAX_CODE_GEN_ATTEMPTS; attempt++) {
            try (Connection conn = DBContext.getConnection()) {
                // insertAddress() (nếu có) và INSERT enterprises phải cùng thành công
                // hoặc cùng rollback -- tắt autocommit để gộp thành 1 transaction,
                // tránh để lại dòng addresses mồ côi khi bước INSERT enterprises sau
                // đó lỗi (ví dụ vi phạm ràng buộc, mất kết nối giữa chừng, hoặc
                // enterprise_code bị trùng ở lần thử này).
                conn.setAutoCommit(false);
                try {
                    Integer addressId = enterprise.getAddressId();
                    if (addressId == null && enterprise.getAddress() != null) {
                        addressId = insertAddress(conn, enterprise.getAddress());
                    }

                    try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, enterprise.getEnterpriseCode());
                        ps.setString(2, enterprise.getEnterpriseName());
                        ps.setString(3, enterprise.getCustomerType());
                        ps.setString(4, enterprise.getCustomerGroup());
                        ps.setString(5, enterprise.getTaxCode());
                        ps.setString(6, enterprise.getEmail());
                        ps.setString(7, enterprise.getPhone());
                        ps.setString(8, enterprise.getWebsite());
                        setNullableInt(ps, 9, addressId);
                        ps.setInt(10, enterprise.getAccountOwnerId());
                        setNullableInt(ps, 11, enterprise.getSupportOwnerId());
                        ps.setString(12, enterprise.getLegalRepresentative());
                        ps.setString(13, enterprise.getLogoUrl());
                        ps.setString(14, enterprise.getBusinessLicenseUrl());
                        ps.setString(15, enterprise.getStatus() != null ? enterprise.getStatus() : "Active");
                        ps.setDate(16, enterprise.getJoinDate());

                        int affected = ps.executeUpdate();
                        if (affected == 0) {
                            conn.rollback();
                            return -1;
                        }
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (!keys.next()) {
                                conn.rollback();
                                return -1;
                            }
                            int newId = keys.getInt(1);
                            conn.commit();
                            return newId;
                        }
                    }
                } catch (SQLException ex) {
                    conn.rollback();
                    if (isDuplicateKeyError(ex, "enterprise_code") && attempt < MAX_CODE_GEN_ATTEMPTS) {
                        enterprise.setEnterpriseCode(generateNextEnterpriseCode());
                        continue;
                    }
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException ex) {
                LOG.error("Loi them khach hang (enterpriseCode={})", enterprise.getEnterpriseCode(), ex);
                return -1;
            }
        }
        return -1;
    }

    /** Cập nhật thông tin khách hàng đang có. Trả về true nếu cập nhật thành công. */
    public boolean update(Enterprise enterprise) {
        String sql = "UPDATE enterprises SET " +
                "enterprise_name = ?, customer_type = ?, customer_group = ?, email = ?, phone = ?, " +
                "website = ?, address_id = ?, account_owner_id = ?, support_owner_id = ?, join_date = ?, logo_url = ? " +
                "WHERE enterprise_id = ? AND is_deleted = 0";

        try (Connection conn = DBContext.getConnection()) {
            // Cùng lý do như insert(): gộp insertAddress()/updateAddress() + UPDATE
            // enterprises vào 1 transaction để không để lại dòng addresses mồ côi
            // khi bước sau lỗi.
            conn.setAutoCommit(false);
            try {
                Integer addressId = enterprise.getAddressId();
                if (addressId != null && enterprise.getAddress() != null) {
                    // Khách hàng đã có address_id từ trước và form gửi lên địa chỉ mới
                    // -- ghi đè nội dung ngay dòng addresses cũ thay vì tạo dòng mới,
                    // tránh để lại dòng mồ côi mỗi lần bấm lưu.
                    updateAddress(conn, addressId, enterprise.getAddress());
                } else if (addressId == null && enterprise.getAddress() != null) {
                    addressId = insertAddress(conn, enterprise.getAddress());
                }

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, enterprise.getEnterpriseName());
                    ps.setString(2, enterprise.getCustomerType());
                    ps.setString(3, enterprise.getCustomerGroup());
                    ps.setString(4, enterprise.getEmail());
                    ps.setString(5, enterprise.getPhone());
                    ps.setString(6, enterprise.getWebsite());
                    setNullableInt(ps, 7, addressId);
                    ps.setInt(8, enterprise.getAccountOwnerId());
                    setNullableInt(ps, 9, enterprise.getSupportOwnerId());
                    ps.setDate(10, enterprise.getJoinDate());
                    ps.setString(11, enterprise.getLogoUrl());
                    ps.setInt(12, enterprise.getEnterpriseId());
                    boolean ok = ps.executeUpdate() > 0;
                    if (ok) {
                        conn.commit();
                    } else {
                        conn.rollback();
                    }
                    return ok;
                }
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            LOG.error("Loi cap nhat khach hang (enterpriseId={}, enterpriseCode={})",
                    enterprise.getEnterpriseId(), enterprise.getEnterpriseCode(), ex);
            return false;
        }
    }

    /** Ghi kết quả CustomerEvaluator vào enterprises.current_relationship_rating. */
    public boolean updateRelationshipRating(int enterpriseId, RelationshipRating rating) {
        String sql = "UPDATE enterprises SET current_relationship_rating = ? WHERE enterprise_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rating.getDbValue());
            ps.setInt(2, enterpriseId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            LOG.error("Loi cap nhat xep hang khach hang (enterpriseId={})", enterpriseId, ex);
            return false;
        }
    }

    /** Xoá mềm khách hàng (is_deleted = 1). Gọi hasActiveContracts() trước để áp BR-41. */
    public boolean softDelete(int enterpriseId) {
        String sql = "UPDATE enterprises SET is_deleted = 1 WHERE enterprise_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enterpriseId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            LOG.error("Loi xoa khach hang (enterpriseId={})", enterpriseId, ex);
            return false;
        }
    }

    /**
     * BR-41: kiểm tra khách hàng còn hợp đồng đang hiệu lực hay không trước khi
     * cho xoá. Tính trực tiếp theo effective_date/end_date (BR-17) thay vì đọc
     * cột contracts.status đã lưu -- cột đó chỉ được ghi lúc insert/update
     * (xem ContractDAO), nên 1 hợp đồng "Chưa hiệu lực" đã tự chuyển sang hiệu
     * lực (hoặc 1 hợp đồng đã hết hạn) mà chưa có UPDATE nào khác từ lúc đó sẽ
     * làm hàm này trả về sai nếu đọc thẳng status. "Sắp hết hạn" cũng tính là
     * còn hiệu lực vì vẫn nằm trong khoảng effective_date..end_date.
     */
    public boolean hasActiveContracts(int enterpriseId) {
        String sql = "SELECT COUNT(*) FROM contracts " +
                     "WHERE enterprise_id = ? AND is_deleted = 0 " +
                     "AND CURDATE() BETWEEN effective_date AND end_date";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enterpriseId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException ex) {
            LOG.error("Loi kiem tra hop dong con hieu luc (enterpriseId={})", enterpriseId, ex);
            return true; // an toàn: nếu không kiểm tra được thì coi như có, chặn xoá
        }
    }

    // ------------------------------------------------------------------
    // Helpers riêng
    // ------------------------------------------------------------------

    private void appendFilters(StringBuilder sql, List<Object> params, String keyword, String customerType,
            Integer accountOwnerId, Integer provinceId, String role) {
        List<String> conditions = new ArrayList<>();
        conditions.add("e.is_deleted = 0");

        if (keyword != null && !keyword.trim().isEmpty()) {
            conditions.add("(e.enterprise_name LIKE ? OR e.enterprise_code LIKE ? OR e.phone LIKE ?)");
            String likeValue = "%" + keyword.trim() + "%";
            params.add(likeValue);
            params.add(likeValue);
            params.add(likeValue);
        }
        if (customerType != null && !customerType.trim().isEmpty()) {
            conditions.add("e.customer_type = ?");
            params.add(customerType);
        }
        if (accountOwnerId != null) {
            // CHỈ soi vai phụ trách chính (cấp trên), cố ý không khớp sang cột
            // người hỗ trợ: mỗi khách hàng quy về đúng một người, nên lọc theo
            // tên ai thì ra đúng phần khách người đó chịu trách nhiệm, không
            // lẫn phần họ chỉ đứng hỗ trợ cho người khác.
            conditions.add("e.account_owner_id = ?");
            params.add(accountOwnerId);
        }
        if (provinceId != null) {
            conditions.add("d.province_id = ?");
            params.add(provinceId);
        }
        if (role != null && !role.trim().isEmpty()) {
            // EXISTS chứ KHÔNG phải JOIN: một công ty giữ cả hai vai sẽ khớp
            // hai dòng ở enterprise_roles, JOIN vào là nó xuất hiện hai lần
            // trong danh sách và đếm phân trang cũng lệch theo.
            conditions.add("EXISTS (SELECT 1 FROM enterprise_roles er "
                    + "WHERE er.enterprise_id = e.enterprise_id AND er.role = ?)");
            params.add(role);
        }

        sql.append("WHERE ").append(String.join(" AND ", conditions));
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    /** true nếu ex là lỗi trùng UNIQUE KEY của MySQL (error 1062) trên đúng cột keyColumn. */
    private boolean isDuplicateKeyError(SQLException ex, String keyColumn) {
        return ex.getErrorCode() == 1062 && ex.getMessage() != null && ex.getMessage().contains(keyColumn);
    }

    /** Tạo 1 dòng addresses mới từ Address chưa có addressId, trả về address_id vừa tạo. */
    private Integer insertAddress(Connection conn, Address address) throws SQLException {
        if (address.getStreetAndLocalName() == null || address.getDistrictId() <= 0) {
            return null;
        }
        String sql = "INSERT INTO addresses (street_and_local_name, districts_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, address.getStreetAndLocalName());
            ps.setInt(2, address.getDistrictId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : null;
            }
        }
    }

    /** Ghi đè nội dung 1 dòng addresses đã tồn tại (dùng khi khách hàng sửa địa chỉ của address_id đã gán từ trước). */
    private void updateAddress(Connection conn, int addressId, Address address) throws SQLException {
        if (address.getStreetAndLocalName() == null || address.getDistrictId() <= 0) {
            return;
        }
        String sql = "UPDATE addresses SET street_and_local_name = ?, districts_id = ? WHERE address_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, address.getStreetAndLocalName());
            ps.setInt(2, address.getDistrictId());
            ps.setInt(3, addressId);
            ps.executeUpdate();
        }
    }

    private Enterprise mapRow(ResultSet rs) throws SQLException {
        Enterprise e = new Enterprise();
        e.setEnterpriseId(rs.getInt("enterprise_id"));
        e.setEnterpriseCode(rs.getString("enterprise_code"));
        e.setEnterpriseName(rs.getString("enterprise_name"));
        e.setCustomerType(rs.getString("customer_type"));
        e.setCustomerGroup(rs.getString("customer_group"));
        e.setTaxCode(rs.getString("tax_code"));
        e.setEmail(rs.getString("ent_email"));
        e.setPhone(rs.getString("ent_phone"));
        e.setWebsite(rs.getString("website"));

        int addressId = rs.getInt("address_id");
        e.setAddressId(rs.wasNull() ? null : addressId);

        e.setAccountOwnerId(rs.getInt("account_owner_id"));
        int supportOwnerId = rs.getInt("support_owner_id");
        e.setSupportOwnerId(rs.wasNull() ? null : supportOwnerId);
        e.setLegalRepresentative(rs.getString("legal_representative"));
        e.setLogoUrl(rs.getString("logo_url"));
        e.setBusinessLicenseUrl(rs.getString("business_license_url"));
        e.setStatus(rs.getString("status"));
        e.setJoinDate(rs.getDate("join_date"));

        e.setCurrentRelationshipRating(
                RelationshipRating.fromDbValue(rs.getString("current_relationship_rating")));

        e.setCreatedAt(rs.getTimestamp("created_at"));
        e.setUpdatedAt(rs.getTimestamp("updated_at"));
        e.setDeleted(rs.getBoolean("is_deleted"));

        String street = rs.getString("street_and_local_name");
        if (street != null) {
            Address address = new Address();
            address.setAddressId(e.getAddressId() != null ? e.getAddressId() : 0);
            address.setStreetAndLocalName(street);
            int districtId = rs.getInt("addr_districts_id");
            if (!rs.wasNull()) {
                address.setDistrictId(districtId);
                String districtName = rs.getString("districts_name");
                if (districtName != null) {
                    District district = new District();
                    district.setDistrictId(districtId);
                    district.setDistrictName(districtName);
                    int provinceId = rs.getInt("dist_province_id");
                    if (!rs.wasNull()) {
                        district.setProvinceId(provinceId);
                        String provinceName = rs.getString("province_name");
                        if (provinceName != null) {
                            Province province = new Province();
                            province.setProvinceId(provinceId);
                            province.setProvinceName(provinceName);
                            district.setProvince(province);
                        }
                    }
                    address.setDistrict(district);
                }
            }
            e.setAddress(address);
        }

        String ownerLastName = rs.getString("owner_last_name");
        if (ownerLastName != null) {
            User owner = new User();
            owner.setUserId(e.getAccountOwnerId());
            owner.setLastName(ownerLastName);
            owner.setMiddleName(rs.getString("owner_middle_name"));
            owner.setFirstName(rs.getString("owner_first_name"));
            e.setAccountOwner(owner);
        }

        String supportLastName = rs.getString("support_last_name");
        if (supportLastName != null) {
            User support = new User();
            support.setUserId(e.getSupportOwnerId() != null ? e.getSupportOwnerId() : 0);
            support.setLastName(supportLastName);
            support.setMiddleName(rs.getString("support_middle_name"));
            support.setFirstName(rs.getString("support_first_name"));
            e.setSupportOwner(support);
        }

        return e;
    }

    // ==================================================================
    // Vai của khách hàng (bảng enterprise_roles)
    // ==================================================================
    //
    // 'Khách mua' = bên đó mua của mình, 'Nhà cung cấp' = bên đó bán cho mình.
    // Một công ty giữ được cả hai vai -- đó là lý do vai nằm ở bảng riêng chứ
    // không phải một cột trên enterprises. Xem ghi chú đầu V20.

    /**
     * Khách hàng đang hoạt động giữ MỘT vai, để đổ dropdown "Khách hàng" ở
     * form hợp đồng.
     *
     * <p>Hợp đồng BÁN chỉ ký được với bên giữ vai 'Khách mua', hợp đồng MUA
     * chỉ ký được với 'Nhà cung cấp' -- cặp đôi CHÉO, xem ghi chú đầu V21. Đổ cả
     * hai vai vào một ô là mời người nhập ký hợp đồng bán với nhà cung cấp.
     *
     * @param keepEnterpriseId khách đang đứng tên hợp đồng ĐANG SỬA -- giữ lại
     *        kể cả khi vai của họ đã bị gỡ. Thiếu nó thì mở form sửa lên ô
     *        trống, bấm lưu là đổi mất khách hàng dù người dùng chỉ định sửa
     *        ngày kết thúc. Cùng cái bẫy đã chữa cho ô tỉnh và ô người phụ
     *        trách.
     */
    public List<Enterprise> findAllByRole(String role, Integer keepEnterpriseId) {
        StringBuilder sql = new StringBuilder(SELECT_ENTERPRISE_BASE);
        List<Object> params = new ArrayList<>();
        sql.append("WHERE e.is_deleted = 0 AND (EXISTS (SELECT 1 FROM enterprise_roles er "
                + "WHERE er.enterprise_id = e.enterprise_id AND er.role = ?)");
        params.add(role);
        if (keepEnterpriseId != null && keepEnterpriseId > 0) {
            sql.append(" OR e.enterprise_id = ?");
            params.add(keepEnterpriseId);
        }
        sql.append(") ORDER BY e.enterprise_name");

        List<Enterprise> result = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van khach hang theo vai (role={})", role, ex);
        }
        return result;
    }

    /** Các vai của một khách hàng. Rỗng là hợp lệ về mặt CSDL (xem replaceRolesOf). */
    public List<String> findRolesOf(int enterpriseId) {
        List<String> result = new ArrayList<>();
        String sql = "SELECT role FROM enterprise_roles WHERE enterprise_id = ? ORDER BY role";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enterpriseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("role"));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van vai cua khach hang (enterpriseId={})", enterpriseId, ex);
        }
        return result;
    }

    /**
     * Đặt lại toàn bộ vai của một khách hàng thành đúng {@code roles}.
     *
     * <p>Xoá hết rồi chèn lại TRONG CÙNG MỘT TRANSACTION, không phải hai lời
     * gọi rời nhau: nửa chừng mà lỗi thì khách đó mất sạch vai và biến khỏi cả
     * hai danh sách -- không ai biết cho tới khi có người đi tìm không thấy.
     *
     * <p>Danh sách rỗng KHÔNG bị chặn ở đây: "ít nhất một vai" là luật nghiệp
     * vụ, chặn ở CustomerController cùng chỗ với các luật khác. DAO chỉ lo ghi
     * đúng cái được bảo ghi.
     *
     * @return true nếu đã ghi xong.
     */
    public boolean replaceRolesOf(int enterpriseId, List<String> roles) {
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM enterprise_roles WHERE enterprise_id = ?")) {
                    del.setInt(1, enterpriseId);
                    del.executeUpdate();
                }
                if (roles != null && !roles.isEmpty()) {
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO enterprise_roles (enterprise_id, role) VALUES (?, ?)")) {
                        for (String role : roles) {
                            ins.setInt(1, enterpriseId);
                            ins.setString(2, role);
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                }
                conn.commit();
                committed = true;
                return true;
            } finally {
                if (!committed) {
                    conn.rollback();
                }
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            LOG.error("Loi dat lai vai cua khach hang (enterpriseId={})", enterpriseId, ex);
            return false;
        }
    }

    // ==================================================================
    // Lịch sử xếp hạng quan hệ (bảng customer_lifecycle_events)
    // ==================================================================
    //
    // Các lần đánh giá/thay đổi xếp hạng quan hệ của khách hàng, do người
    // dùng chọn THỦ CÔNG ở viewcustomerdetail.jsp (xem
    // CustomerController#handleEvaluate) -- không có engine tự tính điểm nào.
    //
    // Nằm trong CustomerDAO chứ không phải một DAO riêng: bảng này khoá theo
    // enterprise_id và chỉ có nghĩa kèm theo khách hàng.

    /** Ghi 1 sự kiện mới. Trả về event_id vừa tạo, hoặc -1 nếu thất bại. */
    public int insertLifecycleEvent(CustomerLifecycleEvent event) {
        String sql = "INSERT INTO customer_lifecycle_events " +
                "(enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, event.getEnterpriseId());
            ps.setString(2, event.getEventType());
            ps.setString(3, event.getRelationshipRating().getDbValue());
            ps.setBoolean(4, event.isAutoGenerated());
            ps.setString(5, event.getDescription());
            ps.setDate(6, event.getEventDate());
            ps.setInt(7, event.getRecordedBy());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                return -1;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException ex) {
            LOG.error("Loi ghi lich su danh gia khach hang (enterpriseId={})", event.getEnterpriseId(), ex);
            return -1;
        }
    }

    /** Lịch sử đánh giá của 1 khách hàng, mới nhất trước, kèm tên người ghi nhận. */
    public List<CustomerLifecycleEvent> findLifecycleEventsByEnterpriseId(int enterpriseId) {
        List<CustomerLifecycleEvent> result = new ArrayList<>();
        String sql = "SELECT ev.event_id, ev.enterprise_id, ev.event_type, ev.relationship_rating, " +
                "       ev.is_auto_generated, ev.description, ev.event_date, ev.recorded_by, ev.created_at, " +
                "       u.last_name AS recorder_last_name, u.middle_name AS recorder_middle_name, " +
                "       u.first_name AS recorder_first_name " +
                "FROM customer_lifecycle_events ev " +
                "JOIN users u ON u.user_id = ev.recorded_by " +
                "WHERE ev.enterprise_id = ? " +
                "ORDER BY ev.event_date DESC, ev.event_id DESC";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enterpriseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CustomerLifecycleEvent ev = new CustomerLifecycleEvent();
                    ev.setEventId(rs.getInt("event_id"));
                    ev.setEnterpriseId(rs.getInt("enterprise_id"));
                    ev.setEventType(rs.getString("event_type"));
                    ev.setRelationshipRating(RelationshipRating.fromDbValue(rs.getString("relationship_rating")));
                    ev.setAutoGenerated(rs.getBoolean("is_auto_generated"));
                    ev.setDescription(rs.getString("description"));
                    ev.setEventDate(rs.getDate("event_date"));
                    ev.setRecordedBy(rs.getInt("recorded_by"));
                    ev.setCreatedAt(rs.getTimestamp("created_at"));

                    User recorder = new User();
                    recorder.setLastName(rs.getString("recorder_last_name"));
                    recorder.setMiddleName(rs.getString("recorder_middle_name"));
                    recorder.setFirstName(rs.getString("recorder_first_name"));
                    ev.setRecordedByUser(recorder);

                    result.add(ev);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi doc lich su danh gia khach hang (enterpriseId={})", enterpriseId, ex);
        }
        return result;
    }
}
