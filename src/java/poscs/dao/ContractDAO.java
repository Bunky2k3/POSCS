package poscs.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import poscs.model.Contract;
import poscs.model.ContractProduct;
import poscs.model.Enterprise;
import poscs.model.User;

/**
 * DAO cho hợp đồng (bảng contracts). Trạng thái hiển thị (Đang hiệu lực /
 * Sắp hết hạn / Đã hết hạn / Chưa hiệu lực) luôn được TÍNH LẠI theo ngày
 * hiện tại so với effective_date/end_date (BR-17), không đọc thẳng cột
 * status lưu trong DB khi hiển thị -- cột status chỉ được cập nhật lúc
 * insert/update để các truy vấn khác (vd CustomerDAO.hasActiveContracts)
 * có giá trị tương đối đúng tại thời điểm đó.
 *
 * findProductsByContractId() đọc hạng mục sản phẩm/dịch vụ (contractproducts)
 * cho mục đích hiển thị (sản phẩm/số lượng/đơn vị/ghi chú) -- vẫn CHƯA có
 * đơn giá/thành tiền/VAT vì contractproducts chưa có cột lưu giá, và form
 * thêm/sửa hợp đồng chưa có UI để gắn/gỡ sản phẩm (chỉ đọc, chưa ghi).
 */
public class ContractDAO {

    public static final String STATUS_DRAFT = "Chưa hiệu lực";
    public static final String STATUS_ACTIVE = "Đang hiệu lực";
    public static final String STATUS_SOON = "Sắp hết hạn";
    public static final String STATUS_EXPIRED = "Đã hết hạn";

    private static final int SOON_THRESHOLD_DAYS = 30;

    private static final String SELECT_BASE =
        "SELECT c.contract_id, c.contract_code, c.title, c.contract_type, c.signing_date, " +
        "       c.effective_date, c.end_date, c.enterprise_id, c.owner_id, c.attachment_url, " +
        "       c.created_at, c.updated_at, c.is_deleted, " +
        "       e.enterprise_name, " +
        "       u.last_name AS owner_last_name, u.middle_name AS owner_middle_name, u.first_name AS owner_first_name " +
        "FROM contracts c " +
        "LEFT JOIN enterprises e ON c.enterprise_id = e.enterprise_id " +
        "LEFT JOIN users u ON c.owner_id = u.user_id ";

    private static final String STATUS_CASE_SQL =
        "CASE " +
        "  WHEN CURDATE() < c.effective_date THEN '" + STATUS_DRAFT + "' " +
        "  WHEN CURDATE() > c.end_date THEN '" + STATUS_EXPIRED + "' " +
        "  WHEN DATEDIFF(c.end_date, CURDATE()) <= " + SOON_THRESHOLD_DAYS + " THEN '" + STATUS_SOON + "' " +
        "  ELSE '" + STATUS_ACTIVE + "' " +
        "END";

    /**
     * Lấy danh sách hợp đồng của 1 khách hàng, phục vụ tab "Hợp đồng" ở
     * viewcustomerdetail.jsp.
     */
    public List<Contract> findByEnterpriseId(int enterpriseId) {
        List<Contract> result = new ArrayList<>();
        String sql = "SELECT contract_id, contract_code, title, contract_type, signing_date, " +
                     "effective_date, end_date, enterprise_id, owner_id, attachment_url " +
                     "FROM contracts WHERE enterprise_id = ? AND is_deleted = 0 ORDER BY signing_date DESC";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enterpriseId);
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
                    c.setStatus(computeStatus(c.getEffectiveDate(), c.getEndDate()));
                    result.add(c);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN HOP DONG THEO KHACH HANG ---");
            ex.printStackTrace();
        }
        return result;
    }

    /** Lấy danh sách hợp đồng có phân trang + lọc, phục vụ listcontract.jsp. */
    public List<Contract> findAll(int page, int pageSize, String keyword, String statusFilter, String typeFilter) {
        List<Contract> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, statusFilter, typeFilter);
        sql.append(" ORDER BY c.contract_id DESC LIMIT ? OFFSET ?");
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
            System.err.println("--- LOI TRUY VAN DANH SACH HOP DONG ---");
            ex.printStackTrace();
        }
        return result;
    }

    /** Đếm tổng số hợp đồng thoả điều kiện lọc, phục vụ phân trang. */
    public int countAll(String keyword, String statusFilter, String typeFilter) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM contracts c LEFT JOIN enterprises e ON c.enterprise_id = e.enterprise_id ");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, statusFilter, typeFilter);

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI DEM SO LUONG HOP DONG ---");
            ex.printStackTrace();
        }
        return 0;
    }

    /** Đếm số hợp đồng theo từng nhóm trạng thái (BR-17), phục vụ dải KPI ở đầu trang danh sách. */
    public Map<String, Integer> countStatusSummary() {
        Map<String, Integer> summary = new HashMap<>();
        summary.put(STATUS_ACTIVE, 0);
        summary.put(STATUS_SOON, 0);
        summary.put(STATUS_EXPIRED, 0);
        summary.put(STATUS_DRAFT, 0);

        String sql =
            "SELECT " +
            "  SUM(CASE WHEN CURDATE() < effective_date THEN 1 ELSE 0 END) AS draft_count, " +
            "  SUM(CASE WHEN CURDATE() > end_date THEN 1 ELSE 0 END) AS expired_count, " +
            "  SUM(CASE WHEN CURDATE() BETWEEN effective_date AND end_date AND DATEDIFF(end_date, CURDATE()) <= " +
                 SOON_THRESHOLD_DAYS + " THEN 1 ELSE 0 END) AS soon_count, " +
            "  SUM(CASE WHEN CURDATE() BETWEEN effective_date AND end_date AND DATEDIFF(end_date, CURDATE()) > " +
                 SOON_THRESHOLD_DAYS + " THEN 1 ELSE 0 END) AS active_count " +
            "FROM contracts WHERE is_deleted = 0";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                summary.put(STATUS_DRAFT, rs.getInt("draft_count"));
                summary.put(STATUS_EXPIRED, rs.getInt("expired_count"));
                summary.put(STATUS_SOON, rs.getInt("soon_count"));
                summary.put(STATUS_ACTIVE, rs.getInt("active_count"));
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI THONG KE TRANG THAI HOP DONG ---");
            ex.printStackTrace();
        }
        return summary;
    }

    /** Lấy top N hợp đồng "Sắp hết hạn" (BR-17), sắp theo ngày hết hạn gần nhất trước -- phục vụ dashboard. */
    public List<Contract> findExpiringSoon(int limit) {
        List<Contract> result = new ArrayList<>();
        String sql = SELECT_BASE +
            "WHERE c.is_deleted = 0 AND CURDATE() BETWEEN c.effective_date AND c.end_date " +
            "AND DATEDIFF(c.end_date, CURDATE()) <= " + SOON_THRESHOLD_DAYS + " " +
            "ORDER BY c.end_date ASC LIMIT ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN HOP DONG SAP HET HAN ---");
            ex.printStackTrace();
        }
        return result;
    }

    /** Lấy chi tiết 1 hợp đồng theo ID, đã join khách hàng + người phụ trách. Trả về null nếu không tồn tại. */
    public Contract findById(int contractId) {
        String sql = SELECT_BASE + "WHERE c.contract_id = ? AND c.is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN CHI TIET HOP DONG ---");
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Hạng mục sản phẩm/dịch vụ của 1 hợp đồng (contractproducts JOIN products),
     * theo thứ tự thêm vào -- phục vụ mục "Hạng mục sản phẩm / dịch vụ" ở
     * viewcontractdetail.jsp. Không có đơn giá/thành tiền vì contractproducts
     * chưa có cột lưu giá (xem javadoc đầu file) -- chỉ hiển thị sản phẩm,
     * số lượng, đơn vị, ghi chú.
     */
    public List<ContractProduct> findProductsByContractId(int contractId) {
        List<ContractProduct> result = new ArrayList<>();
        String sql = "SELECT cp.contract_product_id, cp.contract_id, cp.product_id, cp.quantity, cp.unit, cp.notes, " +
                     "       p.product_code, p.product_name " +
                     "FROM contractproducts cp JOIN products p ON cp.product_id = p.product_id " +
                     "WHERE cp.contract_id = ? ORDER BY cp.contract_product_id";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ContractProduct cp = new ContractProduct();
                    cp.setContractProductId(rs.getInt("contract_product_id"));
                    cp.setContractId(rs.getInt("contract_id"));
                    cp.setProductId(rs.getInt("product_id"));
                    cp.setProductCode(rs.getString("product_code"));
                    cp.setProductName(rs.getString("product_name"));
                    cp.setQuantity(rs.getInt("quantity"));
                    cp.setUnit(rs.getString("unit"));
                    cp.setNotes(rs.getString("notes"));
                    result.add(cp);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN SAN PHAM HOP DONG ---");
            ex.printStackTrace();
        }
        return result;
    }

    /** Sinh mã hợp đồng tiếp theo dạng HD-0001, HD-0002, ... */
    public String generateNextContractCode() {
        String sql = "SELECT contract_code FROM contracts ORDER BY contract_id DESC LIMIT 1";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int nextNumber = 1;
            if (rs.next()) {
                String lastCode = rs.getString("contract_code");
                String digits = lastCode.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    nextNumber = Integer.parseInt(digits) + 1;
                }
            }
            return String.format("HD-%04d", nextNumber);
        } catch (SQLException ex) {
            System.err.println("--- LOI SINH MA HOP DONG ---");
            ex.printStackTrace();
            return null;
        }
    }

    /** Thử lại tối đa bao nhiêu lần khi contract_code sinh ra bị trùng (xem insert()). */
    private static final int MAX_CODE_GEN_ATTEMPTS = 5;

    /** Thêm hợp đồng mới. Trả về contract_id vừa tạo, hoặc -1 nếu lỗi. */
    public int insert(Contract contract) {
        String sql = "INSERT INTO contracts " +
                "(contract_code, title, contract_type, signing_date, effective_date, end_date, " +
                " enterprise_id, owner_id, attachment_url, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // contract_code sinh từ generateNextContractCode() (đọc mã lớn nhất hiện có
        // rồi +1) có thể trùng nếu 2 request tạo hợp đồng gần như đồng thời cùng
        // đọc được "mã lớn nhất" giống nhau -- cột contract_code có UNIQUE KEY
        // (xem db/schema.sql) nên lần INSERT bị trùng sẽ bị DB từ chối thay vì âm
        // thầm ghi đè; thử sinh mã mới và INSERT lại vài lần thay vì báo lỗi ngay,
        // để người dùng không phải tự bấm lưu lại.
        for (int attempt = 1; attempt <= MAX_CODE_GEN_ATTEMPTS; attempt++) {
            try (Connection conn = DBContext.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, contract.getContractCode());
                ps.setString(2, contract.getTitle());
                ps.setString(3, contract.getContractType());
                ps.setDate(4, contract.getSigningDate());
                ps.setDate(5, contract.getEffectiveDate());
                ps.setDate(6, contract.getEndDate());
                ps.setInt(7, contract.getEnterpriseId());
                ps.setInt(8, contract.getOwnerId());
                ps.setString(9, contract.getAttachmentUrl());
                ps.setString(10, computeStatus(contract.getEffectiveDate(), contract.getEndDate()));

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
                if (isDuplicateKeyError(ex, "contract_code") && attempt < MAX_CODE_GEN_ATTEMPTS) {
                    contract.setContractCode(generateNextContractCode());
                    continue;
                }
                System.err.println("--- LOI THEM HOP DONG ---");
                ex.printStackTrace();
                return -1;
            }
        }
        return -1;
    }

    /** Thêm hàng loạt hạng mục sản phẩm/dịch vụ cho 1 hợp đồng (phục vụ nhập PDF hợp đồng). */
    public boolean insertProducts(int contractId, List<ContractProduct> items) {
        if (items.isEmpty()) {
            return true;
        }
        String sql = "INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ContractProduct item : items) {
                ps.setInt(1, contractId);
                ps.setInt(2, item.getProductId());
                ps.setInt(3, item.getQuantity());
                ps.setString(4, item.getUnit());
                ps.setString(5, item.getNotes());
                ps.addBatch();
            }
            ps.executeBatch();
            return true;
        } catch (SQLException ex) {
            System.err.println("--- LOI THEM HANG MUC SAN PHAM HOP DONG ---");
            ex.printStackTrace();
            return false;
        }
    }

    /** Cập nhật thông tin chung của hợp đồng đang có. Trả về true nếu cập nhật thành công. */
    public boolean update(Contract contract) {
        String sql = "UPDATE contracts SET " +
                "title = ?, contract_type = ?, signing_date = ?, effective_date = ?, end_date = ?, " +
                "enterprise_id = ?, owner_id = ?, status = ? " +
                "WHERE contract_id = ? AND is_deleted = 0";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, contract.getTitle());
            ps.setString(2, contract.getContractType());
            ps.setDate(3, contract.getSigningDate());
            ps.setDate(4, contract.getEffectiveDate());
            ps.setDate(5, contract.getEndDate());
            ps.setInt(6, contract.getEnterpriseId());
            ps.setInt(7, contract.getOwnerId());
            ps.setString(8, computeStatus(contract.getEffectiveDate(), contract.getEndDate()));
            ps.setInt(9, contract.getContractId());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            System.err.println("--- LOI CAP NHAT HOP DONG ---");
            ex.printStackTrace();
            return false;
        }
    }

    /** BR-46: chỉ được xoá hợp đồng khi trạng thái hiện tại là "Chưa hiệu lực". */
    public boolean canDelete(int contractId) {
        String sql = "SELECT effective_date, end_date FROM contracts WHERE contract_id = ? AND is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String status = computeStatus(rs.getDate("effective_date"), rs.getDate("end_date"));
                    return STATUS_DRAFT.equals(status);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI KIEM TRA DIEU KIEN XOA HOP DONG ---");
            ex.printStackTrace();
        }
        return false;
    }

    /** Xoá mềm hợp đồng (is_deleted = 1). Gọi canDelete() trước để áp BR-46. */
    public boolean softDelete(int contractId) {
        String sql = "UPDATE contracts SET is_deleted = 1 WHERE contract_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            System.err.println("--- LOI XOA HOP DONG ---");
            ex.printStackTrace();
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Helpers riêng
    // ------------------------------------------------------------------

    private void appendFilters(StringBuilder sql, List<Object> params, String keyword, String statusFilter, String typeFilter) {
        List<String> conditions = new ArrayList<>();
        conditions.add("c.is_deleted = 0");

        if (keyword != null && !keyword.trim().isEmpty()) {
            conditions.add("(c.contract_code LIKE ? OR c.title LIKE ? OR e.enterprise_name LIKE ?)");
            String likeValue = "%" + keyword.trim() + "%";
            params.add(likeValue);
            params.add(likeValue);
            params.add(likeValue);
        }
        if (typeFilter != null && !typeFilter.trim().isEmpty()) {
            conditions.add("c.contract_type = ?");
            params.add(typeFilter);
        }
        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            conditions.add("(" + STATUS_CASE_SQL + ") = ?");
            params.add(statusFilter);
        }

        sql.append("WHERE ").append(String.join(" AND ", conditions));
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    /** true nếu ex là lỗi trùng UNIQUE KEY của MySQL (error 1062) trên đúng cột keyColumn. */
    private boolean isDuplicateKeyError(SQLException ex, String keyColumn) {
        return ex.getErrorCode() == 1062 && ex.getMessage() != null && ex.getMessage().contains(keyColumn);
    }

    /** BR-17: tính trạng thái hiển thị theo ngày hiện tại so với effective_date/end_date. */
    private String computeStatus(Date effectiveDate, Date endDate) {
        if (effectiveDate == null || endDate == null) {
            return STATUS_DRAFT;
        }
        LocalDate today = LocalDate.now();
        LocalDate effective = effectiveDate.toLocalDate();
        LocalDate end = endDate.toLocalDate();

        if (today.isBefore(effective)) {
            return STATUS_DRAFT;
        }
        if (today.isAfter(end)) {
            return STATUS_EXPIRED;
        }
        if (ChronoUnit.DAYS.between(today, end) <= SOON_THRESHOLD_DAYS) {
            return STATUS_SOON;
        }
        return STATUS_ACTIVE;
    }

    private Contract mapRow(ResultSet rs) throws SQLException {
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
        c.setStatus(computeStatus(c.getEffectiveDate(), c.getEndDate()));
        c.setCreatedAt(rs.getTimestamp("created_at"));
        c.setUpdatedAt(rs.getTimestamp("updated_at"));
        c.setDeleted(rs.getBoolean("is_deleted"));

        String enterpriseName = rs.getString("enterprise_name");
        if (enterpriseName != null) {
            Enterprise e = new Enterprise();
            e.setEnterpriseId(c.getEnterpriseId());
            e.setEnterpriseName(enterpriseName);
            c.setEnterprise(e);
        }

        String ownerLastName = rs.getString("owner_last_name");
        if (ownerLastName != null) {
            User owner = new User();
            owner.setUserId(c.getOwnerId());
            owner.setLastName(ownerLastName);
            owner.setMiddleName(rs.getString("owner_middle_name"));
            owner.setFirstName(rs.getString("owner_first_name"));
            c.setOwner(owner);
        }

        return c;
    }
}
