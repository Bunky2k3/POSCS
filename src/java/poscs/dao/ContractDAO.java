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
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.common.Period;
import poscs.model.Address;
import poscs.model.Contract;
import poscs.model.ContractHistory;
import poscs.model.ContractPayment;
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

    // ------------------------------------------------------------------
    // Trục TIẾN ĐỘ -- người đặt, khác hẳn 4 hằng STATUS_* ở trên (trục LỊCH,
    // tính từ ngày tháng). Hai trục lệch nhau ở cả hai chiều; đừng suy cái này
    // ra từ cái kia. Xem đầu V24.
    // ------------------------------------------------------------------

    /** Chưa ký. Sửa thoải mái, xoá được, chưa là chứng cứ gì. */
    public static final String PROGRESS_DRAFT = "Nháp";

    /** Đã ký. Nội dung thành chứng cứ pháp lý; đổi phải đi qua phụ lục. */
    public static final String PROGRESS_SIGNED = "Đã ký";

    /** Đã thanh lý -- ĐÓNG BĂNG. Theo luật KH: xong hợp đồng = đã thanh lý. */
    public static final String PROGRESS_LIQUIDATED = "Đã thanh lý";

    /** Chấm dứt trước hạn -- cũng đóng băng, chỉ khác lý do. */
    public static final String PROGRESS_TERMINATED = "Chấm dứt sớm";

    /**
     * Các bước chuyển HỢP LỆ trên trục tiến độ. Không có đường nào quay lại:
     * ký rồi thì không trở về nháp được (nội dung đã thành chứng cứ), và đóng
     * băng rồi thì không đi đâu nữa.
     *
     * Để ở đây, đúng một chỗ, thay vì rải if-else trong controller: mỗi màn
     * hình tự kiểm một kiểu là chỗ đầu tiên hai nơi nói hai luật khác nhau.
     */
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            PROGRESS_DRAFT, Set.of(PROGRESS_SIGNED),
            PROGRESS_SIGNED, Set.of(PROGRESS_LIQUIDATED, PROGRESS_TERMINATED),
            PROGRESS_LIQUIDATED, Set.of(),
            PROGRESS_TERMINATED, Set.of()
    );

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
        "       c.signer_name, c.signer_position, c.counterparty_signer_name, c.counterparty_signer_position, c.authorization_ref, c.signing_place, c.contract_value, " +
        "       c.progress_status, c.created_at, c.updated_at, c.is_deleted, " +
        "       e.enterprise_name, p.province_id, p.province_name, " +
        "       u.last_name AS owner_last_name, u.middle_name AS owner_middle_name, u.first_name AS owner_first_name " +
        "FROM contracts c " +
        "LEFT JOIN enterprises e ON c.enterprise_id = e.enterprise_id " +
        JOIN_PROVINCE_OF_ENTERPRISE +
        "LEFT JOIN users u ON c.owner_id = u.user_id ";

    private static final String STATUS_CASE_SQL =
        "CASE " +
        // Nhánh này phải đứng TRƯỚC: mọi so sánh với NULL đều ra NULL, nên
        // không có nó thì bản nháp chưa chốt thời hạn rơi xuống ELSE và bị gán
        // "Đang hiệu lực", trong khi computeStatus() bên Java trả "Chưa hiệu
        // lực". Hai đường tính cùng một quy tắc lệch nhau đúng kiểu mà
        // ContractStatusIntegrationTest sinh ra để canh.
        "  WHEN c.effective_date IS NULL OR c.end_date IS NULL THEN '" + STATUS_DRAFT + "' " +
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
        return findAll(page, pageSize, keyword, statusFilter, typeFilter, provinceId, sortByProvince, null, null, null);
    }

    /**
     * Như trên, kèm lọc theo kỳ: chỉ lấy hợp đồng có NGÀY KÝ rơi vào kỳ đó
     * (null = mọi thời điểm). Chọn ngày ký chứ không phải ngày hiệu lực vì
     * "quý này phòng kinh doanh ký được bao nhiêu hợp đồng" mới là con số
     * người ta theo dõi -- ngày hiệu lực có thể rơi sang kỳ sau.
     */
    public List<Contract> findAll(int page, int pageSize, String keyword, String statusFilter, String typeFilter,
            Integer provinceId, boolean sortByProvince, Period period, String direction, String progressFilter) {
        List<Contract> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, statusFilter, typeFilter, provinceId, period, direction, progressFilter);
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
        return countAll(keyword, statusFilter, typeFilter, provinceId, null, null, null);
    }

    /** Như trên, kèm lọc theo kỳ (ngày ký). */
    public int countAll(String keyword, String statusFilter, String typeFilter, Integer provinceId, Period period,
            String direction, String progressFilter) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM contracts c LEFT JOIN enterprises e ON c.enterprise_id = e.enterprise_id "
            + JOIN_PROVINCE_OF_ENTERPRISE);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, statusFilter, typeFilter, provinceId, period, direction, progressFilter);

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
            // Gộp cả hợp đồng chưa chốt thời hạn vào đây, nếu không bốn con số
            // của dải KPI cộng lại không bằng tổng số hợp đồng.
            "  SUM(CASE WHEN c.effective_date IS NULL OR c.end_date IS NULL " +
            "           OR CURDATE() < c.effective_date THEN 1 ELSE 0 END) AS draft_count, " +
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
                if (!isDraftContract(conn, contractId)) {
                    return false;
                }
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

    /**
     * Giá trị trả về của {@link #insert} khi mã hợp đồng đã tồn tại.
     *
     * <p>Tách khỏi -1 (lỗi chung) vì hai thứ đó cần hai câu trả lời khác nhau
     * cho người dùng: "mã này đã có rồi, đổi mã khác" khác hẳn "lưu thất bại,
     * thử lại".
     */
    public static final int DUPLICATE_CODE = -2;

    /**
     * Thêm hợp đồng mới kèm dòng nhật ký "Khởi tạo". Trả về contract_id vừa
     * tạo, hoặc -1 nếu lỗi.
     *
     * @param actorId user_id người đang thao tác -- đi vào contract_history.
     */
    public int insert(Contract contract, int actorId) {
        String sql = "INSERT INTO contracts " +
                "(contract_code, title, contract_type, direction, signing_date, effective_date, end_date, " +
                " enterprise_id, owner_id, attachment_url, status, progress_status, " +
                " signer_name, signer_position, counterparty_signer_name, counterparty_signer_position, " +
                " authorization_ref, signing_place, contract_value) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // Trước V28 mã do hệ thống sinh, nên trùng mã là chuyện của máy và
        // insert() tự sinh mã khác rồi thử lại tối đa 5 lần. Giờ mã do NGƯỜI
        // DÙNG nhập, nên trùng mã là lỗi nhập liệu: phải nói cho họ biết để đổi,
        // chứ tự đổi hộ là lưu một mã khác thứ họ vừa gõ mà không báo gì.
        //
        // UNIQUE KEY trên contract_code vẫn là chốt chặn thật -- kiểm trước ở
        // controller chỉ để báo lỗi tử tế, còn hai người lưu cùng lúc cùng một
        // mã thì chỉ ràng buộc ở CSDL mới bắt được.
        {
            // Khối này từng là vòng for thử lại; giữ lại dấu ngoặc để phần thân
            // bên dưới không phải thụt lề lại toàn bộ.
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
                        // Hợp đồng mới LUÔN là bản nháp -- tạo không còn đồng
                        // nghĩa với ký. Đó là cách duy nhất diễn đạt được luật
                        // KH "nhân viên không tự ký hợp đồng được": trước đây
                        // signing_date là NOT NULL nên không có khoảnh khắc nào
                        // hợp đồng tồn tại mà chưa ký, và vì thế không có chỗ
                        // nào để chặn việc ký.
                        ps.setString(12, PROGRESS_DRAFT);
                        bindSigningParties(ps, 13, contract);

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
                            "Tạo bản nháp " + contract.getContractCode()
                                    + (direction == null ? "" : " — hợp đồng " + direction.toLowerCase())
                                    // Nháp có thể chưa chốt thời hạn (V26). Bỏ hẳn mệnh đề
                                    // đó khi thiếu, thay vì để lại 'hiệu lực  đến ' cụt lủn.
                                    + (contract.getEffectiveDate() == null || contract.getEndDate() == null
                                            ? ", chưa chốt thời hạn"
                                            : ", dự kiến hiệu lực " + formatDate(contract.getEffectiveDate())
                                                    + " đến " + formatDate(contract.getEndDate())),
                            actorId, null);

                    conn.commit();
                    committed = true;
                    return newId;
                } finally {
                    finishTransaction(conn, committed, "them hop dong", contract.getContractCode());
                }
            } catch (SQLException ex) {
                if (isDuplicateKeyError(ex, "contract_code")) {
                    LOG.warn("Ma hop dong da ton tai (contractCode={})", contract.getContractCode());
                    return DUPLICATE_CODE;
                }
                LOG.error("Loi them hop dong (contractCode={})", contract.getContractCode(), ex);
                return -1;
            }
        }
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
                if (!isDraftContract(conn, contractId)) {
                    return false;
                }
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
                "contract_code = ?, title = ?, contract_type = ?, signing_date = ?, effective_date = ?, end_date = ?, " +
                "enterprise_id = ?, owner_id = ?, attachment_url = ?, status = ?, " +
                "signer_name = ?, signer_position = ?, counterparty_signer_name = ?, counterparty_signer_position = ?, " +
                "authorization_ref = ?, signing_place = ?, contract_value = ? " +
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
                // Luật KH: sau thanh lý không được thay đổi, KỂ CẢ CẤP CAO.
                // Chặn ở DAO chứ không chỉ ẩn nút: nút ẩn thì POST thẳng vào
                // URL vẫn ghi được, mà đây là ranh giới pháp lý chứ không phải
                // chuyện giao diện.
                if (before.isFrozen()) {
                    return false;
                }

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
                    bindSigningParties(ps, 11, contract);
                    ps.setInt(18, contract.getContractId());
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
            // Sửa mã thành một mã đã tồn tại cũng đụng UNIQUE KEY. Ghi rõ ở log
            // để người trực không phải đoán, còn người dùng nhận thông báo từ
            // controller (đã kiểm trùng trước khi gọi).
            if (isDuplicateKeyError(ex, "contract_code")) {
                LOG.warn("Ma hop dong da ton tai khi sua (contractId={}, contractCode={})",
                        contract.getContractId(), contract.getContractCode());
            } else {
                LOG.error("Loi cap nhat hop dong (contractId={}, contractCode={})",
                        contract.getContractId(), contract.getContractCode(), ex);
            }
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
    // Trục tiến độ
    // ------------------------------------------------------------------

    /**
     * Chuyển hợp đồng sang một trạng thái tiến độ khác, kèm dòng nhật ký mang
     * cả from_status lẫn to_status -- TRONG CÙNG MỘT TRANSACTION.
     *
     * <p>Trạng thái hiện tại đọc bằng {@code SELECT ... FOR UPDATE} ngay trong
     * transaction chứ không nhận từ controller. Hai người cùng mở một hợp đồng
     * rồi cùng bấm Ký thì người thứ hai phải thất bại, chứ không phải ghi đè
     * lên và để lại một dòng lịch sử "Nháp → Đã ký" thứ hai cho một hợp đồng
     * đã ký từ trước.
     *
     * <p>Bước chuyển phải nằm trong {@link #ALLOWED_TRANSITIONS}: không có
     * đường quay lại. Ký rồi thì không trở về nháp (nội dung đã thành chứng
     * cứ), thanh lý rồi thì không đi đâu nữa -- đó là luật KH, "sau thanh lý
     * không được thay đổi, kể cả cấp cao".
     *
     * <p>Khi ký, hàm tự đóng dấu {@code signing_date = CURDATE()}: ngày ký là
     * ngày hành động xảy ra, không phải thứ người dùng gõ vào ô.
     *
     * @param note lý do/ghi chú người dùng nhập; bắt buộc với thanh lý và chấm
     *             dứt sớm (hai bước không thể quay lại), tuỳ chọn khi ký.
     * @return true nếu đã chuyển; false nếu hợp đồng không tồn tại, bước
     *         chuyển không hợp lệ, hoặc thiếu lý do ở bước bắt buộc.
     */
    public boolean changeProgressStatus(int contractId, String toStatus, int actorId, String note) {
        if (!ALLOWED_TRANSITIONS.containsKey(toStatus)) {
            return false;
        }
        boolean needsNote = PROGRESS_LIQUIDATED.equals(toStatus) || PROGRESS_TERMINATED.equals(toStatus);
        if (needsNote && (note == null || note.trim().isEmpty())) {
            return false;
        }

        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                Contract current = lockForUpdate(conn, contractId);
                if (current == null) {
                    return false;
                }
                String fromStatus = current.getProgressStatus();
                if (!ALLOWED_TRANSITIONS.getOrDefault(fromStatus, Set.of()).contains(toStatus)) {
                    return false;
                }
                // Hợp đồng không có thời hạn thì không phải hợp đồng. Đây là
                // thứ giữ cho việc nới effective_date/end_date thành nullable ở
                // V26 an toàn: NULL chỉ tồn tại trong quãng Nháp, không có
                // đường nào đưa một hợp đồng thiếu thời hạn sang đã ký.
                if (PROGRESS_SIGNED.equals(toStatus)
                        && (current.getEffectiveDate() == null || current.getEndDate() == null)) {
                    return false;
                }

                String sql = PROGRESS_SIGNED.equals(toStatus)
                        ? "UPDATE contracts SET progress_status = ?, signing_date = CURDATE() "
                          + "WHERE contract_id = ? AND is_deleted = 0"
                        : "UPDATE contracts SET progress_status = ? WHERE contract_id = ? AND is_deleted = 0";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, toStatus);
                    ps.setInt(2, contractId);
                    if (ps.executeUpdate() == 0) {
                        return false;
                    }
                }

                insertStatusChange(conn, contractId, fromStatus, toStatus, actorId,
                        note == null || note.trim().isEmpty() ? null : note.trim());

                conn.commit();
                committed = true;
            } finally {
                finishTransaction(conn, committed, "chuyen trang thai tien do hop dong", contractId);
            }
            return committed;
        } catch (SQLException ex) {
            LOG.error("Loi chuyen trang thai tien do hop dong (contractId={}, toStatus={})",
                    contractId, toStatus, ex);
            return false;
        }
    }

    /**
     * true nếu hợp đồng đã thanh lý hoặc chấm dứt sớm -- đọc trong transaction
     * đang mở, dùng để chặn mọi đường ghi. Hợp đồng không tồn tại thì trả false
     * và để lệnh ghi phía sau tự thất bại vì không khớp dòng nào: trả true ở
     * đây sẽ báo "đã đóng băng" cho một id không có thật, sai hẳn lý do.
     */
    private boolean isFrozen(Connection conn, int contractId) throws SQLException {
        String status = lockProgressStatus(conn, contractId);
        return PROGRESS_LIQUIDATED.equals(status) || PROGRESS_TERMINATED.equals(status);
    }

    /**
     * true nếu hợp đồng còn là bản NHÁP -- điều kiện để được gắn/gỡ hàng hoá.
     *
     * <p>Hàng hoá là nội dung hợp đồng, không phải dữ liệu quản trị nội bộ: ký
     * xong thì nó thành chứng cứ, đổi phải đi qua phụ lục chứ không sửa thẳng.
     *
     * <p>Điều kiện này chỉ áp được TỪ V24. Trước đó mọi hợp đồng đều "đã ký"
     * (signing_date NOT NULL) nên khoá theo trạng thái ký nghĩa là khoá tất --
     * đó là lý do đợt trước mới chỉ ghi vết chứ chưa chặn.
     */
    private boolean isDraftContract(Connection conn, int contractId) throws SQLException {
        return PROGRESS_DRAFT.equals(lockProgressStatus(conn, contractId));
    }

    /** Đọc và khoá trạng thái tiến độ hiện tại; null nếu hợp đồng không tồn tại. */
    private String lockProgressStatus(Connection conn, int contractId) throws SQLException {
        String sql = "SELECT progress_status FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("progress_status") : null;
            }
        }
    }

    /**
     * Dòng nhật ký của một bước chuyển tiến độ -- khác dòng sửa đổi ở chỗ có
     * from_status/to_status, nên dựng lại được cả dòng thời gian vòng đời chứ
     * không chỉ danh sách thay đổi.
     */
    private void insertStatusChange(Connection conn, int contractId, String fromStatus, String toStatus,
            int changedBy, String note) throws SQLException {
        String sql = "INSERT INTO contract_history " +
                "(contract_id, event_type, detail, from_status, to_status, changed_by, note) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            ps.setString(2, eventTypeOf(toStatus));
            ps.setString(3, truncate(fromStatus + " → " + toStatus, DETAIL_MAX_LENGTH));
            ps.setString(4, fromStatus);
            ps.setString(5, toStatus);
            ps.setInt(6, changedBy);
            ps.setString(7, note);
            ps.executeUpdate();
        }
    }

    private static String eventTypeOf(String toStatus) {
        if (PROGRESS_SIGNED.equals(toStatus)) {
            return ContractHistory.EVENT_SIGNED;
        }
        if (PROGRESS_LIQUIDATED.equals(toStatus)) {
            return ContractHistory.EVENT_LIQUIDATED;
        }
        return ContractHistory.EVENT_TERMINATED;
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

    /**
     * true nếu mã hợp đồng đã có người khác dùng.
     *
     * <p>{@code exceptId} là hợp đồng đang sửa -- bỏ chính nó ra, nếu không thì
     * mở form sửa lên bấm Lưu mà không đổi mã cũng bị báo trùng với chính mình.
     * Truyền 0 khi đang tạo mới.
     */
    public boolean contractCodeExists(String code, int exceptId) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT 1 FROM contracts WHERE contract_code = ? AND contract_id <> ? LIMIT 1";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.trim());
            ps.setInt(2, exceptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            LOG.error("Loi kiem tra trung ma hop dong (code={})", code, ex);
            // Không chặn khi chính phép kiểm hỏng: UNIQUE KEY vẫn còn đó.
            return false;
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
                "       effective_date, end_date, enterprise_id, owner_id, attachment_url, progress_status, " +
                "       signer_name, signer_position, counterparty_signer_name, counterparty_signer_position, " +
                "       authorization_ref, signing_place, contract_value " +
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
                c.setProgressStatus(rs.getString("progress_status"));
                c.setSignerName(rs.getString("signer_name"));
                c.setSignerPosition(rs.getString("signer_position"));
                c.setCounterpartySignerName(rs.getString("counterparty_signer_name"));
                c.setCounterpartySignerPosition(rs.getString("counterparty_signer_position"));
                c.setAuthorizationRef(rs.getString("authorization_ref"));
                c.setSigningPlace(rs.getString("signing_place"));
                c.setContractValue(rs.getBigDecimal("contract_value"));
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
        addChange(parts, "Mã hợp đồng", before.getContractCode(), after.getContractCode());
        addChange(parts, "Tiêu đề", before.getTitle(), after.getTitle());
        addChange(parts, "Loại hợp đồng", before.getContractType(), after.getContractType());
        addChange(parts, "Ngày ký", formatDate(before.getSigningDate()), formatDate(after.getSigningDate()));
        addChange(parts, "Ngày hiệu lực", formatDate(before.getEffectiveDate()), formatDate(after.getEffectiveDate()));
        addChange(parts, "Ngày hết hạn", formatDate(before.getEndDate()), formatDate(after.getEndDate()));
        addChange(parts, "Link đính kèm", before.getAttachmentUrl(), after.getAttachmentUrl());
        addChange(parts, "Người ký bên mình", before.getSignerName(), after.getSignerName());
        addChange(parts, "Chức vụ người ký bên mình", before.getSignerPosition(), after.getSignerPosition());
        addChange(parts, "Người ký bên đối tác", before.getCounterpartySignerName(), after.getCounterpartySignerName());
        addChange(parts, "Chức vụ người ký bên đối tác",
                before.getCounterpartySignerPosition(), after.getCounterpartySignerPosition());
        addChange(parts, "Căn cứ uỷ quyền", before.getAuthorizationRef(), after.getAuthorizationRef());
        addChange(parts, "Nơi ký", before.getSigningPlace(), after.getSigningPlace());
        addChange(parts, "Giá trị hợp đồng",
                before.getContractValue() == null ? null : before.getContractValue().toPlainString(),
                after.getContractValue() == null ? null : after.getContractValue().toPlainString());
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

    /**
     * Bind bảy cột của V27 vào statement, bắt đầu từ {@code first}.
     *
     * <p>Gom lại một chỗ vì cùng bộ cột đó xuất hiện ở cả INSERT lẫn UPDATE:
     * tách ra hai đoạn giống nhau là chỗ đầu tiên hai bên lệch nhau khi có ai
     * thêm cột thứ tám.
     */
    private void bindSigningParties(PreparedStatement ps, int first, Contract c) throws SQLException {
        ps.setString(first, c.getSignerName());
        ps.setString(first + 1, c.getSignerPosition());
        ps.setString(first + 2, c.getCounterpartySignerName());
        ps.setString(first + 3, c.getCounterpartySignerPosition());
        ps.setString(first + 4, c.getAuthorizationRef());
        ps.setString(first + 5, c.getSigningPlace());
        ps.setBigDecimal(first + 6, c.getContractValue());
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
            String typeFilter, Integer provinceId, Period period, String direction, String progressFilter) {
        List<String> conditions = new ArrayList<>();
        conditions.add("c.is_deleted = 0");

        if (keyword != null && !keyword.trim().isEmpty()) {
            // Gộp cả số hợp đồng thật: khách gọi điện đọc số in trên giấy chứ
            // không đọc mã nội bộ HD-xxxx, nên đó mới là thứ người dùng gõ vào
            // ô tìm kiếm nhiều nhất.
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
            // Lọc theo NGÀY KÝ, nên bản nháp (signing_date NULL) rơi ra ngoài
            // -- đúng nghĩa: "hợp đồng ký trong quý này" không gồm thứ chưa ký.
            // Muốn xem nháp thì bỏ bộ lọc kỳ, hoặc lọc theo trục tiến độ.
            conditions.add("c.signing_date BETWEEN ? AND ?");
            params.add(period.getFrom());
            params.add(period.getTo());
        }
        if (progressFilter != null && !progressFilter.trim().isEmpty()) {
            conditions.add("c.progress_status = ?");
            params.add(progressFilter.trim());
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
        c.setSignerName(rs.getString("signer_name"));
        c.setSignerPosition(rs.getString("signer_position"));
        c.setCounterpartySignerName(rs.getString("counterparty_signer_name"));
        c.setCounterpartySignerPosition(rs.getString("counterparty_signer_position"));
        c.setAuthorizationRef(rs.getString("authorization_ref"));
        c.setSigningPlace(rs.getString("signing_place"));
        c.setContractValue(rs.getBigDecimal("contract_value"));
        c.setStatus(computeStatus(c.getEffectiveDate(), c.getEndDate()));
        c.setProgressStatus(rs.getString("progress_status"));
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
    /**
     * Các kỳ thanh toán của một hợp đồng, kỳ đến hạn sớm nhất trước.
     *
     * <p>Bảng contract_payments đã tồn tại từ lâu và ĐANG ĐƯỢC ĐỌC (ba hàm
     * sumInvoiceAmount* bên dưới nuôi dải KPI doanh thu của dashboard), nhưng
     * cho tới trước phần này thì KHÔNG màn hình nào ghi vào đó -- nghĩa là con
     * số doanh thu trên dashboard đóng băng ở dữ liệu gieo mẫu.
     */
    public List<ContractPayment> findPaymentsByContractId(int contractId) {
        List<ContractPayment> result = new ArrayList<>();
        String sql = "SELECT payment_id, contract_id, invoice_amount, due_date, paid_date, created_at " +
                "FROM contract_payments WHERE contract_id = ? ORDER BY due_date, payment_id";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ContractPayment p = new ContractPayment();
                    p.setPaymentId(rs.getInt("payment_id"));
                    p.setContractId(rs.getInt("contract_id"));
                    p.setInvoiceAmount(rs.getBigDecimal("invoice_amount"));
                    p.setDueDate(rs.getDate("due_date"));
                    p.setPaidDate(rs.getDate("paid_date"));
                    p.setCreatedAt(rs.getTimestamp("created_at"));
                    result.add(p);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van ky thanh toan (contractId={})", contractId, ex);
        }
        return result;
    }

    /**
     * Lập một kỳ thanh toán, kèm dòng nhật ký -- trong cùng transaction.
     *
     * <p>CHẶN khi hợp đồng đã đóng băng, nhưng KHÔNG chặn khi đã ký. Khác hàng
     * hoá ở chỗ đó, và có lý do: hàng hoá là nội dung hợp đồng (ký xong là
     * chứng cứ), còn lịch thu tiền là thứ mình theo dõi trong lúc thực hiện --
     * hợp đồng ký tháng trước mà giờ mới nhập kỳ thu là chuyện bình thường.
     */
    public boolean insertPayment(int contractId, ContractPayment payment, int actorId) {
        if (payment.getInvoiceAmount() == null || payment.getInvoiceAmount().signum() <= 0
                || payment.getDueDate() == null) {
            return false;
        }
        String sql = "INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) " +
                "VALUES (?, ?, ?, ?)";
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                if (isFrozen(conn, contractId)) {
                    return false;
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, contractId);
                    ps.setBigDecimal(2, payment.getInvoiceAmount());
                    ps.setDate(3, payment.getDueDate());
                    ps.setDate(4, payment.getPaidDate());
                    if (ps.executeUpdate() == 0) {
                        return false;
                    }
                }
                insertHistory(conn, contractId, ContractHistory.EVENT_PAYMENT_ADDED,
                        "Lập kỳ thanh toán " + formatMoney(payment.getInvoiceAmount())
                                + ", đến hạn " + formatDate(payment.getDueDate())
                                + (payment.getPaidDate() == null ? ""
                                        : " (đã thu " + formatDate(payment.getPaidDate()) + ")"),
                        actorId, null);
                conn.commit();
                committed = true;
            } finally {
                finishTransaction(conn, committed, "lap ky thanh toan", contractId);
            }
            return committed;
        } catch (SQLException ex) {
            LOG.error("Loi lap ky thanh toan (contractId={})", contractId, ex);
            return false;
        }
    }

    /**
     * Ghi nhận tiền của một kỳ đã về.
     *
     * <p>KHÔNG chặn kể cả khi hợp đồng đã thanh lý: tiền bảo hành giữ lại
     * thường chỉ về sau thanh lý cả năm. Đóng băng nói về NỘI DUNG hợp đồng,
     * không nói về việc tiền có thật sự vào tài khoản hay chưa.
     */
    public boolean markPaymentPaid(int paymentId, int contractId, Date paidDate, int actorId) {
        if (paidDate == null) {
            return false;
        }
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                ContractPayment before = lockPayment(conn, paymentId, contractId);
                if (before == null || before.getPaidDate() != null) {
                    // Đã ghi nhận rồi thì thôi -- ghi đè lần nữa chỉ sinh thêm
                    // một dòng nhật ký nói về việc chẳng thay đổi gì.
                    return false;
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE contract_payments SET paid_date = ? WHERE payment_id = ? AND contract_id = ?")) {
                    ps.setDate(1, paidDate);
                    ps.setInt(2, paymentId);
                    ps.setInt(3, contractId);
                    if (ps.executeUpdate() == 0) {
                        return false;
                    }
                }
                insertHistory(conn, contractId, ContractHistory.EVENT_PAYMENT_PAID,
                        "Đã thu " + formatMoney(before.getInvoiceAmount())
                                + " (kỳ đến hạn " + formatDate(before.getDueDate()) + ")"
                                + " ngày " + formatDate(paidDate),
                        actorId, null);
                conn.commit();
                committed = true;
            } finally {
                finishTransaction(conn, committed, "ghi nhan da thu", contractId);
            }
            return committed;
        } catch (SQLException ex) {
            LOG.error("Loi ghi nhan da thu (paymentId={}, contractId={})", paymentId, contractId, ex);
            return false;
        }
    }

    /** Xoá một kỳ lập nhầm. Đọc TRƯỚC khi xoá để nhật ký nói được đã xoá cái gì. */
    public boolean deletePayment(int paymentId, int contractId, int actorId) {
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                if (isFrozen(conn, contractId)) {
                    return false;
                }
                ContractPayment before = lockPayment(conn, paymentId, contractId);
                if (before == null) {
                    return false;
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM contract_payments WHERE payment_id = ? AND contract_id = ?")) {
                    ps.setInt(1, paymentId);
                    ps.setInt(2, contractId);
                    if (ps.executeUpdate() == 0) {
                        return false;
                    }
                }
                insertHistory(conn, contractId, ContractHistory.EVENT_PAYMENT_REMOVED,
                        "Xoá kỳ " + formatMoney(before.getInvoiceAmount())
                                + " đến hạn " + formatDate(before.getDueDate()),
                        actorId, null);
                conn.commit();
                committed = true;
            } finally {
                finishTransaction(conn, committed, "xoa ky thanh toan", contractId);
            }
            return committed;
        } catch (SQLException ex) {
            LOG.error("Loi xoa ky thanh toan (paymentId={}, contractId={})", paymentId, contractId, ex);
            return false;
        }
    }

    /** Đọc và khoá một kỳ thanh toán trong transaction; null nếu không thuộc hợp đồng này. */
    private ContractPayment lockPayment(Connection conn, int paymentId, int contractId) throws SQLException {
        String sql = "SELECT payment_id, invoice_amount, due_date, paid_date FROM contract_payments " +
                "WHERE payment_id = ? AND contract_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            ps.setInt(2, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ContractPayment p = new ContractPayment();
                p.setPaymentId(rs.getInt("payment_id"));
                p.setContractId(contractId);
                p.setInvoiceAmount(rs.getBigDecimal("invoice_amount"));
                p.setDueDate(rs.getDate("due_date"));
                p.setPaidDate(rs.getDate("paid_date"));
                return p;
            }
        }
    }

    /** Tiền trong câu nhật ký viết như người Việt đọc: "1.500.000.000 đ". */
    private static String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return String.format("%,.0f", amount).replace(',', '.') + " đ";
    }

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
