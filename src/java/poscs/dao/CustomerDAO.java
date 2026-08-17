package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import poscs.model.Address;
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

    private static final String SELECT_ENTERPRISE_BASE =
        "SELECT e.enterprise_id, e.enterprise_code, e.enterprise_name, e.customer_type, e.customer_group, " +
        "       e.tax_code, e.email AS ent_email, e.phone AS ent_phone, e.website, e.address_id, e.account_owner_id, " +
        "       e.legal_representative, e.logo_url, e.business_license_url, e.status, e.join_date, " +
        "       e.current_relationship_rating, e.created_at, e.updated_at, e.is_deleted, " +
        "       a.street_and_local_name, a.districts_id AS addr_districts_id, " +
        "       d.districts_name, d.province_id AS dist_province_id, " +
        "       p.province_name, " +
        "       u.last_name AS owner_last_name, u.middle_name AS owner_middle_name, u.first_name AS owner_first_name " +
        "FROM enterprises e " +
        "LEFT JOIN addresses a ON e.address_id = a.address_id " +
        "LEFT JOIN districts d ON a.districts_id = d.districts_id " +
        "LEFT JOIN provinces p ON d.province_id = p.province_id " +
        "LEFT JOIN users u ON e.account_owner_id = u.user_id ";

    /**
     * Lấy danh sách khách hàng có phân trang + lọc, phục vụ listcustomer.jsp.
     * @param page 1-indexed
     */
    public List<Enterprise> findAll(int page, int pageSize, String keyword, String customerType, Integer accountOwnerId) {
        List<Enterprise> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_ENTERPRISE_BASE);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, customerType, accountOwnerId);
        sql.append(" ORDER BY e.enterprise_id DESC LIMIT ? OFFSET ?");
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
            System.err.println("--- LOI TRUY VAN DANH SACH KHACH HANG ---");
            ex.printStackTrace();
        }
        return result;
    }

    /** Đếm tổng số khách hàng thoả điều kiện lọc, phục vụ phân trang (BR-12). */
    public int countAll(String keyword, String customerType, Integer accountOwnerId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM enterprises e ");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, customerType, accountOwnerId);

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI DEM SO LUONG KHACH HANG ---");
            ex.printStackTrace();
        }
        return 0;
    }

    /** Đếm số khách hàng có ngày tham gia (join_date) rơi vào tháng hiện tại, phục vụ KPI dashboard. */
    public int countNewThisMonth() {
        String sql = "SELECT COUNT(*) FROM enterprises WHERE is_deleted = 0 " +
                     "AND YEAR(join_date) = YEAR(CURDATE()) AND MONTH(join_date) = MONTH(CURDATE())";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI DEM KHACH HANG MOI TRONG THANG ---");
            ex.printStackTrace();
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
            System.err.println("--- LOI TRUY VAN CHI TIET KHACH HANG ---");
            ex.printStackTrace();
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
            System.err.println("--- LOI TRUY VAN NGUOI LIEN HE ---");
            ex.printStackTrace();
        }
        return result;
    }

    /** Sinh mã khách hàng tiếp theo dạng KH-0001, KH-0002, ... */
    public String generateNextEnterpriseCode() {
        String sql = "SELECT enterprise_code FROM enterprises ORDER BY enterprise_id DESC LIMIT 1";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int nextNumber = 1;
            if (rs.next()) {
                String lastCode = rs.getString("enterprise_code");
                String digits = lastCode.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    nextNumber = Integer.parseInt(digits) + 1;
                }
            }
            return String.format("KH-%04d", nextNumber);
        } catch (SQLException ex) {
            System.err.println("--- LOI SINH MA KHACH HANG ---");
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Thêm khách hàng mới. Nếu enterprise.getAddress() có street/district thì tự tạo
     * dòng addresses trước rồi mới gán address_id. Trả về enterprise_id vừa tạo, hoặc -1 nếu lỗi.
     */
    public int insert(Enterprise enterprise) {
        String sql = "INSERT INTO enterprises " +
                "(enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, " +
                " website, address_id, account_owner_id, legal_representative, logo_url, business_license_url, " +
                " status, join_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBContext.getConnection()) {
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
                ps.setString(11, enterprise.getLegalRepresentative());
                ps.setString(12, enterprise.getLogoUrl());
                ps.setString(13, enterprise.getBusinessLicenseUrl());
                ps.setString(14, enterprise.getStatus() != null ? enterprise.getStatus() : "Active");
                ps.setDate(15, enterprise.getJoinDate());

                int affected = ps.executeUpdate();
                if (affected == 0) {
                    return -1;
                }
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI THEM KHACH HANG ---");
            ex.printStackTrace();
        }
        return -1;
    }

    /** Cập nhật thông tin khách hàng đang có. Trả về true nếu cập nhật thành công. */
    public boolean update(Enterprise enterprise) {
        String sql = "UPDATE enterprises SET " +
                "enterprise_name = ?, customer_type = ?, customer_group = ?, email = ?, phone = ?, " +
                "website = ?, address_id = ?, account_owner_id = ?, join_date = ? " +
                "WHERE enterprise_id = ? AND is_deleted = 0";

        try (Connection conn = DBContext.getConnection()) {
            Integer addressId = enterprise.getAddressId();
            if (addressId == null && enterprise.getAddress() != null) {
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
                ps.setDate(9, enterprise.getJoinDate());
                ps.setInt(10, enterprise.getEnterpriseId());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI CAP NHAT KHACH HANG ---");
            ex.printStackTrace();
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
            System.err.println("--- LOI XOA KHACH HANG ---");
            ex.printStackTrace();
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
            System.err.println("--- LOI KIEM TRA HOP DONG CON HIEU LUC ---");
            ex.printStackTrace();
            return true; // an toàn: nếu không kiểm tra được thì coi như có, chặn xoá
        }
    }

    // ------------------------------------------------------------------
    // Helpers riêng
    // ------------------------------------------------------------------

    private void appendFilters(StringBuilder sql, List<Object> params, String keyword, String customerType, Integer accountOwnerId) {
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
            conditions.add("e.account_owner_id = ?");
            params.add(accountOwnerId);
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
        e.setLegalRepresentative(rs.getString("legal_representative"));
        e.setLogoUrl(rs.getString("logo_url"));
        e.setBusinessLicenseUrl(rs.getString("business_license_url"));
        e.setStatus(rs.getString("status"));
        e.setJoinDate(rs.getDate("join_date"));

        String rating = rs.getString("current_relationship_rating");
        e.setCurrentRelationshipRating(rating != null ? RelationshipRating.fromDbValue(rating) : null);

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

        return e;
    }
}
