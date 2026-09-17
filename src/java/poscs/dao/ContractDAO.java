package poscs.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.Period;
import poscs.model.Address;
import poscs.model.Contract;
import poscs.model.ContractHistory;
import poscs.model.ContractProduct;
import poscs.model.District;
import poscs.model.Enterprise;
import poscs.model.Province;
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

    private static final Logger LOG = LoggerFactory.getLogger(ContractDAO.class);

    public static final String STATUS_DRAFT = "Chưa hiệu lực";
    public static final String STATUS_ACTIVE = "Đang hiệu lực";
    public static final String STATUS_SOON = "Sắp hết hạn";
    public static final String STATUS_EXPIRED = "Đã hết hạn";

    private static final int SOON_THRESHOLD_DAYS = 30;

    /** Khớp varchar(500) của contract_history.detail -- xem {@link #truncate}. */
    private static final int DETAIL_MAX_LENGTH = 500;

    /** Ngày trong câu nhật ký viết như người Việt đọc, không phải như CSDL lưu. */
    private static final DateTimeFormatter DETAIL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String SQL_ENTERPRISE_LABEL =
        "SELECT enterprise_name FROM enterprises WHERE enterprise_id = ?";
    private static final String SQL_USER_LABEL =
        "SELECT CONCAT_WS(' ', last_name, middle_name, first_name) FROM users WHERE user_id = ?";
    private static final String SQL_PRODUCT_LABEL =
        "SELECT product_name FROM products WHERE product_id = ?";

    /**
     * Hợp đồng không có cột tỉnh riêng -- địa bàn của nó là địa bàn của khách
     * hàng đứng tên, nên phải đi qua 3 bảng: enterprises -> addresses ->
     * districts (xã/phường) -> provinces. Tách ra hằng số vì cả SELECT_BASE và
     * countAll đều cần đúng chuỗi join này, lệch nhau một chữ là danh sách và
     * bộ đếm phân trang ra hai kết quả khác nhau.
     */
    private static final String JOIN_PROVINCE_OF_ENTERPRISE =
        "LEFT JOIN addresses a ON e.address_id = a.address_id " +
        "LEFT JOIN districts d ON a.districts_id = d.districts_id " +
        "LEFT JOIN provinces p ON d.province_id = p.province_id ";

    private static final String SELECT_BASE =
        "SELECT c.contract_id, c.contract_code, c.title, c.contract_type, c.direction, c.signing_date, " +
        "       c.effective_date, c.end_date, c.enterprise_id, c.owner_id, c.attachment_url, " +
        "       c.created_at, c.updated_at, c.is_deleted, " +
        "       e.enterprise_name, p.province_id, p.province_name, " +
        "       u.last_name AS owner_last_name, u.middle_name AS owner_middle_name, u.first_name AS owner_first_name " +
        "FROM contracts c " +
        "LEFT JOIN enterprises e ON c.enterprise_id = e.enterprise_id " +
        JOIN_PROVINCE_OF_ENTERPRISE +
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
        String sql = "SELECT contract_id, contract_code, title, contract_type, direction, signing_date, " +
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
        c.setDirection(rs.getString("direction"));
                    c.setDirection(rs.getString("direction"));
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
            LOG.error("Loi truy van hop dong theo khach hang (enterpriseId={})", enterpriseId, ex);
        }
        return result;
    }

    /** Lấy danh sách hợp đồng có phân trang + lọc, phục vụ listcontract.jsp. */
    public List<Contract> findAll(int page, int pageSize, String keyword, String statusFilter, String typeFilter) {
        return findAll(page, pageSize, keyword, statusFilter, typeFilter, null, false);
    }

    /**
     * Như {@link #findAll(int, int, String, String, String)} nhưng lọc thêm theo
     * tỉnh/thành của khách hàng đứng tên hợp đồng, và cho phép sắp theo tỉnh.
     *
     * @param provinceId     null = mọi tỉnh
     * @param sortByProvince true thì gom các hợp đồng cùng tỉnh nằm liền nhau
     *                       (hợp đồng của khách chưa có địa chỉ dồn xuống cuối);
     *                       dùng khi xuất Excel
     */
    public List<Contract> findAll(int page, int pageSize, String keyword, String statusFilter, String typeFilter,
            Integer provinceId, boolean sortByProvince) {
        return findAll(page, pageSize, keyword, statusFilter, typeFilter, provinceId, sortByProvince, null, null);
    }

    /**
     * Như trên, kèm lọc theo kỳ: chỉ lấy hợp đồng có NGÀY KÝ rơi vào kỳ đó
     * (null = mọi thời điểm). Chọn ngày ký chứ không phải ngày hiệu lực vì
     * "quý này phòng kinh doanh ký được bao nhiêu hợp đồng" mới là con số
     * người ta theo dõi -- ngày hiệu lực có thể rơi sang kỳ sau.
     */
    public List<Contract> findAll(int page, int pageSize, String keyword, String statusFilter, String typeFilter,
            Integer provinceId, boolean sortByProvince, Period period, String direction) {
        List<Contract> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, statusFilter, typeFilter, provinceId, period, direction);
        sql.append(sortByProvince
                ? " ORDER BY p.province_name IS NULL, " + AddressDAO.PROVINCE_SHORT_NAME_ORDER
                        + ", c.contract_id DESC LIMIT ? OFFSET ?"
                : " ORDER BY c.contract_id DESC LIMIT ? OFFSET ?");
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
            LOG.error("Loi truy van danh sach hop dong", ex);
        }
        return result;
    }

    /** Đếm tổng số hợp đồng thoả điều kiện lọc, phục vụ phân trang. */
    public int countAll(String keyword, String statusFilter, String typeFilter) {
        return countAll(keyword, statusFilter, typeFilter, null);
    }

    /** Như {@link #countAll(String, String, String)} nhưng lọc thêm theo tỉnh/thành. */
    public int countAll(String keyword, String statusFilter, String typeFilter, Integer provinceId) {
        return countAll(keyword, statusFilter, typeFilter, provinceId, null, null);
    }

    /** Như trên, kèm lọc theo kỳ (ngày ký). */
    public int countAll(String keyword, String statusFilter, String typeFilter, Integer provinceId, Period period,
            String direction) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM contracts c LEFT JOIN enterprises e ON c.enterprise_id = e.enterprise_id "
            + JOIN_PROVINCE_OF_ENTERPRISE);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, statusFilter, typeFilter, provinceId, period, direction);

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi dem so luong hop dong", ex);
        }
        return 0;
    }

    /** Đếm số hợp đồng theo từng nhóm trạng thái (BR-17), phục vụ dải KPI ở đầu trang danh sách. */
    public Map<String, Integer> countStatusSummary() {
        return countStatusSummary(null);
    }

    /** Như {@link #countStatusSummary()} nhưng chỉ đếm hợp đồng thuộc 1 tỉnh (null = toàn quốc). */
    public Map<String, Integer> countStatusSummary(Integer provinceId) {
        return countStatusSummary(provinceId, null, null);
    }

    /** Như trên, kèm lọc theo kỳ (ngày ký) -- null = mọi thời điểm. */
    public Map<String, Integer> countStatusSummary(Integer provinceId, Period period, String direction) {
        Map<String, Integer> summary = new HashMap<>();
        summary.put(STATUS_ACTIVE, 0);
        summary.put(STATUS_SOON, 0);
        summary.put(STATUS_EXPIRED, 0);
        summary.put(STATUS_DRAFT, 0);

        String sql =
            "SELECT " +
            "  SUM(CASE WHEN CURDATE() < c.effective_date THEN 1 ELSE 0 END) AS draft_count, " +
            "  SUM(CASE WHEN CURDATE() > c.end_date THEN 1 ELSE 0 END) AS expired_count, " +
            "  SUM(CASE WHEN CURDATE() BETWEEN c.effective_date AND c.end_date AND DATEDIFF(c.end_date, CURDATE()) <= " +
                 SOON_THRESHOLD_DAYS + " THEN 1 ELSE 0 END) AS soon_count, " +
            "  SUM(CASE WHEN CURDATE() BETWEEN c.effective_date AND c.end_date AND DATEDIFF(c.end_date, CURDATE()) > " +
                 SOON_THRESHOLD_DAYS + " THEN 1 ELSE 0 END) AS active_count " +
            "FROM contracts c " +
            "LEFT JOIN enterprises e ON c.enterprise_id = e.enterprise_id " +
            JOIN_PROVINCE_OF_ENTERPRISE +
            "WHERE c.is_deleted = 0" + (provinceId != null ? " AND d.province_id = ?" : "")
            + (period != null ? " AND c.signing_date BETWEEN ? AND ?" : "");

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int param = 1;
            if (provinceId != null) {
                ps.setInt(param++, provinceId);
            }
            if (period != null) {
                ps.setDate(param++, period.getFrom());
                ps.setDate(param, period.getTo());
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    summary.put(STATUS_DRAFT, rs.getInt("draft_count"));
                    summary.put(STATUS_EXPIRED, rs.getInt("expired_count"));
                    summary.put(STATUS_SOON, rs.getInt("soon_count"));
                    summary.put(STATUS_ACTIVE, rs.getInt("active_count"));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi thong ke trang thai hop dong", ex);
        }
        return summary;
    }

    /** Lấy top N hợp đồng "Sắp hết hạn" (BR-17), sắp theo ngày hết hạn gần nhất trước -- phục vụ dashboard. */
    public List<Contract> findExpiringSoon(int limit) {
        return findExpiringSoon(limit, null);
    }

    /** Như {@link #findExpiringSoon(int)} nhưng chỉ lấy hợp đồng thuộc 1 tỉnh (null = toàn quốc). */
    public List<Contract> findExpiringSoon(int limit, Integer provinceId) {
        List<Contract> result = new ArrayList<>();
        String sql = SELECT_BASE +
            "WHERE c.is_deleted = 0 AND CURDATE() BETWEEN c.effective_date AND c.end_date " +
            "AND DATEDIFF(c.end_date, CURDATE()) <= " + SOON_THRESHOLD_DAYS + " " +
            (provinceId != null ? "AND d.province_id = ? " : "") +
            "ORDER BY c.end_date ASC LIMIT ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int param = 1;
            if (provinceId != null) {
                ps.setInt(param++, provinceId);
            }
            ps.setInt(param, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van hop dong sap het han", ex);
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
            LOG.error("Loi truy van chi tiet hop dong (contractId={})", contractId, ex);
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
            LOG.error("Loi truy van san pham hop dong (contractId={})", contractId, ex);
        }
        return result;
    }

    /**
     * Gỡ 1 dòng hạng mục sản phẩm khỏi hợp đồng (contractproducts không có cột
     * is_deleted nên xoá cứng, khác với contracts/enterprises dùng xoá mềm).
     * contractId dùng để đảm bảo chỉ xoá đúng dòng thuộc hợp đồng đang thao
     * tác, tránh 1 contractProductId sai/giả mạo xoá nhầm dòng của hợp đồng
     * khác.
     */
    public boolean deleteProductLine(int contractProductId, int contractId, int actorId) {
        String sql = "DELETE FROM contractproducts WHERE contract_product_id = ? AND contract_id = ?";
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                // Đọc TRƯỚC khi xoá: đây là DELETE cứng, sau lệnh dưới thì không
                // còn chỗ nào biết dòng vừa mất là hàng hoá gì, số lượng bao
                // nhiêu -- mà đó chính là nội dung duy nhất đáng ghi lại.
                String label = describeProductLine(conn, contractProductId, contractId);
                if (label == null) {
                    return false;
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, contractProductId);
                    ps.setInt(2, contractId);
                    if (ps.executeUpdate() == 0) {
                        return false;
                    }
                }
                insertHistory(conn, contractId, ContractHistory.EVENT_PRODUCT_REMOVED,
                        "Gỡ hạng mục: " + label, actorId, null);
                conn.commit();
                committed = true;
            } finally {
                finishTransaction(conn, committed, "xoa hang muc san pham hop dong", contractId);
            }
            return committed;
        } catch (SQLException ex) {
            LOG.error("Loi xoa hang muc san pham hop dong (contractProductId={}, contractId={})", contractProductId, contractId, ex);
            return false;
        }
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
            LOG.error("Loi sinh ma hop dong", ex);
            return null;
        }
    }

    /** Thử lại tối đa bao nhiêu lần khi contract_code sinh ra bị trùng (xem insert()). */
    private static final int MAX_CODE_GEN_ATTEMPTS = 5;

    /**
     * Thêm hợp đồng mới kèm dòng nhật ký "Khởi tạo". Trả về contract_id vừa
     * tạo, hoặc -1 nếu lỗi.
     *
     * @param actorId user_id người đang thao tác -- đi vào contract_history.
     */
    public int insert(Contract contract, int actorId) {
        String sql = "INSERT INTO contracts " +
                "(contract_code, title, contract_type, direction, signing_date, effective_date, end_date, " +
                " enterprise_id, owner_id, attachment_url, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // contract_code sinh từ generateNextContractCode() (đọc mã lớn nhất hiện có
        // rồi +1) có thể trùng nếu 2 request tạo hợp đồng gần như đồng thời cùng
        // đọc được "mã lớn nhất" giống nhau -- cột contract_code có UNIQUE KEY
        // (xem db/schema.sql) nên lần INSERT bị trùng sẽ bị DB từ chối thay vì âm
        // thầm ghi đè; thử sinh mã mới và INSERT lại vài lần thay vì báo lỗi ngay,
        // để người dùng không phải tự bấm lưu lại.
        for (int attempt = 1; attempt <= MAX_CODE_GEN_ATTEMPTS; attempt++) {
            try (Connection conn = DBContext.getConnection()) {
                conn.setAutoCommit(false);
                boolean committed = false;
                try {
                    int newId = -1;
                    try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, contract.getContractCode());
                        ps.setString(2, contract.getTitle());
                        ps.setString(3, contract.getContractType());
                        ps.setString(4, contract.getDirection());
                        ps.setDate(5, contract.getSigningDate());
                        ps.setDate(6, contract.getEffectiveDate());
                        ps.setDate(7, contract.getEndDate());
                        ps.setInt(8, contract.getEnterpriseId());
                        ps.setInt(9, contract.getOwnerId());
                        ps.setString(10, contract.getAttachmentUrl());
                        ps.setString(11, computeStatus(contract.getEffectiveDate(), contract.getEndDate()));

                        if (ps.executeUpdate() == 0) {
                            return -1;
                        }
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (keys.next()) {
                                newId = keys.getInt(1);
                            }
                        }
                    }
                    if (newId == -1) {
                        return -1;
                    }

                    // Chiều có thể null: luồng nhập PDF không gán direction, cột
                    // trong CSDL tự rơi về DEFAULT 'Bán'. Câu nhật ký không được
                    // đoán hộ giá trị đó, và càng không được ném NPE làm hỏng cả
                    // lần tạo hợp đồng chỉ vì một dòng mô tả.
                    String direction = contract.getDirection();
                    insertHistory(conn, newId, ContractHistory.EVENT_CREATED,
                            "Tạo hợp đồng " + contract.getContractCode()
                                    + (direction == null ? "" : " — hợp đồng " + direction.toLowerCase())
                                    + ", hiệu lực " + formatDate(contract.getEffectiveDate())
                                    + " đến " + formatDate(contract.getEndDate()),
                            actorId, null);

                    conn.commit();
                    committed = true;
                    return newId;
                } finally {
                    finishTransaction(conn, committed, "them hop dong", contract.getContractCode());
                }
            } catch (SQLException ex) {
                if (isDuplicateKeyError(ex, "contract_code") && attempt < MAX_CODE_GEN_ATTEMPTS) {
                    contract.setContractCode(generateNextContractCode());
                    continue;
                }
                LOG.error("Loi them hop dong (contractCode={})", contract.getContractCode(), ex);
                return -1;
            }
        }
        return -1;
    }

    /**
     * Thêm hàng loạt hạng mục sản phẩm/dịch vụ cho 1 hợp đồng (phục vụ nhập PDF
     * hợp đồng). Tắt autocommit và tự commit/rollback quanh cả batch -- mặc định
     * autocommit=true của connection thì 1 statement lỗi giữa batch vẫn để lại
     * các statement trước đó đã thi hành thành công, trong khi hàm này cần trả
     * về true/false đúng nghĩa "tất cả hoặc không gì cả" để bên gọi (xem
     * ContractController.handleImportPdf) có thể tin tưởng báo "chưa ghi gì vào
     * CSDL" khi trả về false.
     */
    public boolean insertProducts(int contractId, List<ContractProduct> items, int actorId) {
        if (items.isEmpty()) {
            return true;
        }
        String sql = "INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (ContractProduct item : items) {
                        ps.setInt(1, contractId);
                        ps.setInt(2, item.getProductId());
                        ps.setInt(3, item.getQuantity());
                        ps.setString(4, item.getUnit());
                        ps.setString(5, item.getNotes());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                // MỘT lời gọi = MỘT dòng nhật ký, kể cả khi thêm nhiều hạng mục:
                // luồng nhập PDF gắn cả chục dòng hàng hoá trong một lần, tách ra
                // mỗi hạng mục một dòng lịch sử thì dòng thời gian của hợp đồng
                // ngập trong các dòng của đúng một thao tác.
                insertHistory(conn, contractId, ContractHistory.EVENT_PRODUCT_ADDED,
                        "Thêm " + items.size() + " hạng mục: " + describeItems(conn, items),
                        actorId, null);
                conn.commit();
                committed = true;
            } finally {
                finishTransaction(conn, committed, "them hang muc san pham hop dong", contractId);
            }
            return committed;
        } catch (SQLException ex) {
            LOG.error("Loi them hang muc san pham hop dong (contractId={})", contractId, ex);
            return false;
        }
    }

    /**
     * Cập nhật thông tin chung của hợp đồng đang có. Trả về true nếu thành công.
     *
     * <p>CỐ Ý KHÔNG đổi {@code direction}: chiều được chốt lúc tạo. Đổi chiều
     * của một hợp đồng đã ký nghĩa là đối tác đang giữ sai vai, và mọi con số
     * doanh thu đã báo cáo trước đó lặng lẽ đổi nghĩa. Cần đổi thì xoá và tạo
     * lại, để có dấu vết.
     */
    public boolean update(Contract contract, int actorId) {
        String sql = "UPDATE contracts SET " +
                "title = ?, contract_type = ?, signing_date = ?, effective_date = ?, end_date = ?, " +
                "enterprise_id = ?, owner_id = ?, attachment_url = ?, status = ? " +
                "WHERE contract_id = ? AND is_deleted = 0";

        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                // Giá trị cũ đọc NGAY TRONG transaction (SELECT ... FOR UPDATE)
                // chứ không nhận từ controller: controller đọc hợp đồng ở một
                // thời điểm trước đó, nên nếu hai người cùng sửa một hợp đồng
                // thì "giá trị cũ" mà nó biết có thể đã lỗi thời, và nhật ký sẽ
                // ghi lại một thay đổi chưa từng xảy ra. Cùng lý do với
                // TechnicalSupportTicketDAO.update.
                Contract before = lockForUpdate(conn, contract.getContractId());
                if (before == null) {
                    return false;
                }

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, contract.getTitle());
                    ps.setString(2, contract.getContractType());
                    ps.setDate(3, contract.getSigningDate());
                    ps.setDate(4, contract.getEffectiveDate());
                    ps.setDate(5, contract.getEndDate());
                    ps.setInt(6, contract.getEnterpriseId());
                    ps.setInt(7, contract.getOwnerId());
                    ps.setString(8, contract.getAttachmentUrl());
                    ps.setString(9, computeStatus(contract.getEffectiveDate(), contract.getEndDate()));
                    ps.setInt(10, contract.getContractId());
                    if (ps.executeUpdate() == 0) {
                        return false;
                    }
                }

                // Bấm Lưu mà không đổi gì thì KHÔNG sinh dòng nhật ký. Ghi cả
                // những lần như vậy thì dòng thời gian đầy các dòng "đã sửa"
                // không nói được đã sửa cái gì, và chôn mất những lần sửa thật.
                String changes = describeChanges(conn, before, contract);
                if (changes != null) {
                    insertHistory(conn, contract.getContractId(), ContractHistory.EVENT_UPDATED,
                            changes, actorId, null);
                }

                conn.commit();
                committed = true;
            } finally {
                finishTransaction(conn, committed, "cap nhat hop dong", contract.getContractId());
            }
            return committed;
        } catch (SQLException ex) {
            LOG.error("Loi cap nhat hop dong (contractId={}, contractCode={})",
                    contract.getContractId(), contract.getContractCode(), ex);
            return false;
        }
    }

    /**
     * Huỷ mềm một bản ghi hợp đồng (is_deleted = 1), kèm lý do bắt buộc.
     *
     * <p>THAY CHO cặp canDelete()/softDelete() cũ. BR-46 cũ cho xoá khi trạng
     * thái là "Chưa hiệu lực" -- mà trạng thái đó tính theo LỊCH, nên hợp đồng
     * ký hôm qua và hiệu lực tháng sau vẫn xoá được, mang theo cả nội dung đã
     * ký. Điều kiện đúng phải là CHƯA KÝ; nhưng signing_date là NOT NULL nên
     * không bản ghi nào chưa ký, tức là xoá theo nghĩa nghiệp vụ không còn tồn
     * tại nữa.
     *
     * <p>Thứ còn lại là gỡ một bản ghi NHẬP NHẦM khỏi danh sách -- không phải
     * huỷ hợp đồng ngoài đời. Vì thế nó dành cho Admin (chặn ở
     * ContractController.handleDelete) và bắt buộc có lý do: một bản ghi biến
     * mất không dấu vết thì sau này không ai phân biệt được nhập nhầm với xoá
     * để che.
     *
     * @param reason lý do người dùng nhập; rỗng thì từ chối, không ghi gì.
     */
    public boolean voidRecord(int contractId, int actorId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return false;
        }
        String sql = "UPDATE contracts SET is_deleted = 1 WHERE contract_id = ? AND is_deleted = 0";
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, contractId);
                    if (ps.executeUpdate() == 0) {
                        return false;
                    }
                }
                insertHistory(conn, contractId, ContractHistory.EVENT_VOIDED,
                        "Huỷ bản ghi khỏi danh sách (dữ liệu vẫn còn trong CSDL)",
                        actorId, reason.trim());
                conn.commit();
                committed = true;
            } finally {
                finishTransaction(conn, committed, "huy ban ghi hop dong", contractId);
            }
            return committed;
        } catch (SQLException ex) {
            LOG.error("Loi huy ban ghi hop dong (contractId={})", contractId, ex);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Nhật ký thay đổi (contract_history)
    // ------------------------------------------------------------------

    /**
     * Ghi 1 dòng nhật ký bằng connection ĐANG MỞ của bên gọi -- cố ý không tự
     * mở connection riêng.
     *
     * <p>Dòng nhật ký và thay đổi mà nó nói về phải đúng hoặc sai cùng nhau.
     * Hai connection rời nhau thì sẽ có lúc hàng hoá đã gỡ xong mà dòng nhật ký
     * ghi hụt, và không ai phát hiện ra -- đúng cái lỗ mà bảng này sinh ra để
     * bịt. Ném SQLException ra ngoài để transaction của bên gọi rollback.
     */
    private void insertHistory(Connection conn, int contractId, String eventType, String detail,
            int changedBy, String note) throws SQLException {
        String sql = "INSERT INTO contract_history " +
                "(contract_id, event_type, detail, changed_by, note) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            ps.setString(2, eventType);
            ps.setString(3, truncate(detail, DETAIL_MAX_LENGTH));
            ps.setInt(4, changedBy);
            ps.setString(5, note);
            ps.executeUpdate();
        }
    }

    /** Nhật ký của 1 hợp đồng, mới nhất trước, kèm tên người thực hiện. */
    public List<ContractHistory> findHistoryByContractId(int contractId) {
        List<ContractHistory> result = new ArrayList<>();
        String sql = "SELECT h.history_id, h.contract_id, h.event_type, h.detail, h.from_status, h.to_status, " +
                "       h.changed_by, h.changed_at, h.note, " +
                "       u.last_name AS changer_last_name, u.middle_name AS changer_middle_name, " +
                "       u.first_name AS changer_first_name " +
                "FROM contract_history h " +
                "JOIN users u ON u.user_id = h.changed_by " +
                "WHERE h.contract_id = ? " +
                "ORDER BY h.changed_at DESC, h.history_id DESC";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ContractHistory h = new ContractHistory();
                    h.setHistoryId(rs.getInt("history_id"));
                    h.setContractId(rs.getInt("contract_id"));
                    h.setEventType(rs.getString("event_type"));
                    h.setDetail(rs.getString("detail"));
                    h.setFromStatus(rs.getString("from_status"));
                    h.setToStatus(rs.getString("to_status"));
                    h.setChangedBy(rs.getInt("changed_by"));
                    h.setChangedAt(rs.getTimestamp("changed_at"));
                    h.setNote(rs.getString("note"));

                    User changer = new User();
                    changer.setLastName(rs.getString("changer_last_name"));
                    changer.setMiddleName(rs.getString("changer_middle_name"));
                    changer.setFirstName(rs.getString("changer_first_name"));
                    h.setChangedByUser(changer);

                    result.add(h);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van nhat ky hop dong (contractId={})", contractId, ex);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Helpers riêng
    // ------------------------------------------------------------------

    /**
     * Dọn dẹp cuối transaction: rollback nếu chưa commit, rồi trả autocommit về
     * true.
     *
     * <p>Cả hai lệnh tự bắt lỗi riêng để KHÔNG bao giờ ghi đè lên kết quả
     * "committed" thật sự -- tránh trường hợp đã commit xong nhưng
     * setAutoCommit(true) ném lỗi khiến hàm gọi báo thất bại sai sự thật. Và vì
     * nó luôn được gọi trong finally, nó không được phép ném ra: một ngoại lệ ở
     * đây sẽ nuốt mất ngoại lệ gốc đang trên đường bay ra.
     */
    private void finishTransaction(Connection conn, boolean committed, String what, Object id) {
        if (!committed) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                LOG.error("Loi rollback khi {} (id={})", what, id, rollbackEx);
            }
        }
        try {
            conn.setAutoCommit(true);
        } catch (SQLException autoCommitEx) {
            LOG.error("Loi reset autocommit sau khi {} (id={})", what, id, autoCommitEx);
        }
    }

    /** Đọc và khoá bản ghi hợp đồng trong transaction đang mở; null nếu không có. */
    private Contract lockForUpdate(Connection conn, int contractId) throws SQLException {
        String sql = "SELECT contract_id, contract_code, title, contract_type, direction, signing_date, " +
                "       effective_date, end_date, enterprise_id, owner_id, attachment_url " +
                "FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Contract c = new Contract();
                c.setContractId(rs.getInt("contract_id"));
                c.setContractCode(rs.getString("contract_code"));
                c.setTitle(rs.getString("title"));
                c.setContractType(rs.getString("contract_type"));
                c.setDirection(rs.getString("direction"));
                c.setSigningDate(rs.getDate("signing_date"));
                c.setEffectiveDate(rs.getDate("effective_date"));
                c.setEndDate(rs.getDate("end_date"));
                c.setEnterpriseId(rs.getInt("enterprise_id"));
                c.setOwnerId(rs.getInt("owner_id"));
                c.setAttachmentUrl(rs.getString("attachment_url"));
                return c;
            }
        }
    }

    /**
     * Câu mô tả những gì đổi giữa hai phiên bản hợp đồng, hoặc null nếu không
     * đổi gì. Chỉ so các trường mà form sửa thật sự gửi lên -- direction không
     * nằm trong đó vì update() cố ý không ghi cột ấy.
     */
    private String describeChanges(Connection conn, Contract before, Contract after) throws SQLException {
        List<String> parts = new ArrayList<>();
        addChange(parts, "Tiêu đề", before.getTitle(), after.getTitle());
        addChange(parts, "Loại hợp đồng", before.getContractType(), after.getContractType());
        addChange(parts, "Ngày ký", formatDate(before.getSigningDate()), formatDate(after.getSigningDate()));
        addChange(parts, "Ngày hiệu lực", formatDate(before.getEffectiveDate()), formatDate(after.getEffectiveDate()));
        addChange(parts, "Ngày hết hạn", formatDate(before.getEndDate()), formatDate(after.getEndDate()));
        addChange(parts, "Link đính kèm", before.getAttachmentUrl(), after.getAttachmentUrl());
        // Hai trường dưới là khoá ngoại -- tra tên ra để nhật ký đọc được,
        // "Khách hàng: #3 -> #7" thì không ai hiểu. Chỉ tra khi có đổi thật,
        // nên lần bấm Lưu bình thường không tốn thêm truy vấn nào.
        if (before.getEnterpriseId() != after.getEnterpriseId()) {
            addChange(parts, "Khách hàng",
                    lookupLabel(conn, SQL_ENTERPRISE_LABEL, before.getEnterpriseId()),
                    lookupLabel(conn, SQL_ENTERPRISE_LABEL, after.getEnterpriseId()));
        }
        if (before.getOwnerId() != after.getOwnerId()) {
            addChange(parts, "Người phụ trách",
                    lookupLabel(conn, SQL_USER_LABEL, before.getOwnerId()),
                    lookupLabel(conn, SQL_USER_LABEL, after.getOwnerId()));
        }
        return parts.isEmpty() ? null : String.join("; ", parts);
    }

    private static void addChange(List<String> parts, String label, String before, String after) {
        String b = before == null ? "" : before.trim();
        String a = after == null ? "" : after.trim();
        if (b.equals(a)) {
            return;
        }
        parts.add(label + ": " + (b.isEmpty() ? "(trống)" : b) + " → " + (a.isEmpty() ? "(trống)" : a));
    }

    /** Liệt kê các hạng mục vừa thêm, dạng "Tên hàng ×5 cái; ...". */
    private String describeItems(Connection conn, List<ContractProduct> items) throws SQLException {
        List<String> parts = new ArrayList<>();
        for (ContractProduct item : items) {
            String name = item.getProductName();
            if (name == null || name.trim().isEmpty()) {
                name = lookupLabel(conn, SQL_PRODUCT_LABEL, item.getProductId());
            }
            parts.add(name.trim() + " ×" + item.getQuantity()
                    + (item.getUnit() == null ? "" : " " + item.getUnit()));
        }
        return String.join("; ", parts);
    }

    /** Mô tả 1 dòng hàng hoá đang có; null nếu dòng đó không thuộc hợp đồng này. */
    private String describeProductLine(Connection conn, int contractProductId, int contractId) throws SQLException {
        String sql = "SELECT p.product_name, cp.quantity, cp.unit " +
                "FROM contractproducts cp JOIN products p ON cp.product_id = p.product_id " +
                "WHERE cp.contract_product_id = ? AND cp.contract_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractProductId);
            ps.setInt(2, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String unit = rs.getString("unit");
                return rs.getString("product_name") + " ×" + rs.getInt("quantity")
                        + (unit == null ? "" : " " + unit);
            }
        }
    }

    /** Tên hiển thị của 1 khoá ngoại; trả "#id" khi không tra được, không bao giờ null. */
    private String lookupLabel(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String label = rs.getString(1);
                    if (label != null && !label.trim().isEmpty()) {
                        return label.trim();
                    }
                }
            }
        }
        return "#" + id;
    }

    private static String formatDate(Date date) {
        return date == null ? "" : date.toLocalDate().format(DETAIL_DATE_FORMAT);
    }

    /** Cắt cho vừa cột detail thay vì để MySQL từ chối cả câu INSERT và mất luôn dòng nhật ký. */
    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private void appendFilters(StringBuilder sql, List<Object> params, String keyword, String statusFilter,
            String typeFilter, Integer provinceId, Period period, String direction) {
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
        if (provinceId != null) {
            conditions.add("d.province_id = ?");
            params.add(provinceId);
        }
        if (period != null) {
            conditions.add("c.signing_date BETWEEN ? AND ?");
            params.add(period.getFrom());
            params.add(period.getTo());
        }
        // Chiều đứng ĐỘC LẬP với kỳ: hai mục con Hợp đồng bán / Hợp đồng mua
        // vẫn phải lọc được theo năm/quý/tháng như trước.
        if (direction != null && !direction.trim().isEmpty()) {
            conditions.add("c.direction = ?");
            params.add(direction);
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
            // Chỉ gắn tỉnh (địa bàn của hợp đồng), KHÔNG dựng địa chỉ đầy đủ:
            // màn hình danh sách và file Excel hợp đồng chỉ cần tên tỉnh, còn
            // số nhà/xã phường thì xem ở màn hình khách hàng.
            String provinceName = rs.getString("province_name");
            if (provinceName != null) {
                Province province = new Province();
                province.setProvinceId(rs.getInt("province_id"));
                province.setProvinceName(provinceName);
                District district = new District();
                district.setProvinceId(province.getProvinceId());
                district.setProvince(province);
                Address address = new Address();
                address.setDistrict(district);
                e.setAddress(address);
            }
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

    // ==================================================================
    // Kỳ thanh toán của hợp đồng (bảng contract_payments)
    // ==================================================================
    //
    // Doanh thu tính theo paid_date -- tiền ĐÃ THỰC THU, không phải due_date
    // (tiền đáng lẽ phải thu). Hai cột đó lệch nhau chính là chuyện trả chậm,
    // mà lấy nhầm due_date là báo cáo doanh thu tự cộng cả khoản chưa về.
    //
    // Nằm trong ContractDAO chứ không phải một DAO riêng: kỳ thanh toán không
    // đứng một mình được, mọi câu ở đây đều JOIN về contracts.

    /**
     * Tổng tiền đã thu trong một kỳ (theo ngày thanh toán), lọc thêm theo tỉnh
     * của khách hàng đứng tên hợp đồng. period null = trả về 0 phần tiền chưa
     * xác định kỳ -- bên gọi tự quyết định dùng hàm theo tháng bên dưới.
     */
    public BigDecimal sumInvoiceAmountInPeriod(Period period, Integer provinceId) {
        if (period == null) {
            return BigDecimal.ZERO;
        }
        String sql = "SELECT COALESCE(SUM(p.invoice_amount), 0) FROM contract_payments p " +
                     "LEFT JOIN contracts c ON p.contract_id = c.contract_id " +
                     "LEFT JOIN enterprises e ON c.enterprise_id = e.enterprise_id " +
                     "LEFT JOIN addresses a ON e.address_id = a.address_id " +
                     "LEFT JOIN districts d ON a.districts_id = d.districts_id " +
                     "WHERE p.paid_date IS NOT NULL AND p.paid_date BETWEEN ? AND ? " +
                     // CHỈ hợp đồng BÁN RA. Doanh thu là tiền về, không phải
                     // mọi khoản đi qua contract_payments -- thiếu điều kiện này
                     // thì hợp đồng MUA đầu tiên nhập vào là KPI tự cộng cả tiền
                     // mình đi trả, sai âm thầm cho tới lúc đối chiếu sổ sách.
                     "AND c.direction = 'Bán' " +
                     (provinceId != null ? " AND d.province_id = ?" : "");
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, period.getFrom());
            ps.setDate(2, period.getTo());
            if (provinceId != null) {
                ps.setInt(3, provinceId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi tinh doanh thu theo ky", ex);
        }
        return BigDecimal.ZERO;
    }

    /** Tổng tiền đã thu trong 1 tháng cụ thể. Trả về 0 nếu không có khoản thu nào. */
    public BigDecimal sumInvoiceAmountByMonth(int year, int month) {
        return sumInvoiceAmountByMonth(year, month, null);
    }

    /**
     * Như {@link #sumInvoiceAmountByMonth(int, int)} nhưng chỉ tính các khoản
     * thu của hợp đồng thuộc 1 tỉnh (null = toàn quốc). Khoản thu không mang
     * địa bàn riêng -- nó thừa hưởng tỉnh của khách hàng đứng tên hợp đồng, nên
     * phải đi qua contracts -> enterprises -> addresses -> districts.
     */
    public BigDecimal sumInvoiceAmountByMonth(int year, int month, Integer provinceId) {
        String sql = "SELECT COALESCE(SUM(p.invoice_amount), 0) FROM contract_payments p " +
                     "LEFT JOIN contracts c ON p.contract_id = c.contract_id " +
                     "LEFT JOIN enterprises e ON c.enterprise_id = e.enterprise_id " +
                     "LEFT JOIN addresses a ON e.address_id = a.address_id " +
                     "LEFT JOIN districts d ON a.districts_id = d.districts_id " +
                     "WHERE p.paid_date IS NOT NULL AND YEAR(p.paid_date) = ? AND MONTH(p.paid_date) = ? " +
                     // CHỈ hợp đồng BÁN RA. Doanh thu là tiền về, không phải
                     // mọi khoản đi qua contract_payments -- thiếu điều kiện này
                     // thì hợp đồng MUA đầu tiên nhập vào là KPI tự cộng cả tiền
                     // mình đi trả, sai âm thầm cho tới lúc đối chiếu sổ sách.
                     "AND c.direction = 'Bán' " +
                     (provinceId != null ? " AND d.province_id = ?" : "");
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, year);
            ps.setInt(2, month);
            if (provinceId != null) {
                ps.setInt(3, provinceId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi tinh doanh thu theo thang", ex);
        }
        return BigDecimal.ZERO;
    }

    /** Tổng tiền đã lập hoá đơn (thu hoặc chưa thu) của 1 hợp đồng cụ thể -- dùng làm "Giá trị" hợp đồng. */
    public BigDecimal sumInvoiceAmountByContractId(int contractId) {
        String sql = "SELECT COALESCE(SUM(invoice_amount), 0) FROM contract_payments WHERE contract_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi tinh gia tri hop dong (contractId={})", contractId, ex);
        }
        return BigDecimal.ZERO;
    }
}
