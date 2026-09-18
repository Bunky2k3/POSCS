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
import poscs.common.SqlFilters;
import poscs.model.Address;
import poscs.model.Contract;
import poscs.model.ContractHandover;
import poscs.model.ContractHistory;
import poscs.model.ContractLink;
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

    /**
     * Số phụ lục còn sống của mỗi hợp đồng -- ĐẾM lúc đọc, không phải cột.
     *
     * <p>Một cột "có phụ lục" ở phía cha sẽ là nguồn sự thật thứ hai bên cạnh
     * chính các dòng phụ lục; câu đếm này thì không bao giờ lệch được. Đi qua
     * idx_contracts_parent nên rẻ.
     *
     * <p>Lọc {@code is_deleted = 0}: phụ lục bị huỷ bản ghi không được làm hợp
     * đồng cha mang nhãn "có phụ lục" mãi mãi.
     */
    private static final String AMENDMENT_COUNT_SQL =
        "(SELECT COUNT(*) FROM contracts ch WHERE ch.parent_contract_id = c.contract_id AND ch.is_deleted = 0)";

    /**
     * Điều kiện "phụ lục này đã ký" dùng trong hai câu cộng bên dưới.
     *
     * <p>Không so bằng PROGRESS_SIGNED: thanh lý và chấm dứt sớm cũng là những
     * phụ lục ĐÃ TỪNG ký, và tiền hai bên đã thoả thuận không mất đi vì về sau
     * văn bản đóng lại. Trạng thái tiến trình nói về quy trình, không nói gì về
     * giá trị -- nên loại trừ duy nhất là bản NHÁP (chưa ai ký).
     *
     * <p>{@code IS NULL} tính là đã ký, khớp {@code Contract.isTermsLocked()}:
     * hàng nào không đọc ra trạng thái thì coi như đã khoá, fail-closed.
     */
    private static final String AMENDMENT_IS_SIGNED_SQL =
        "(ch.progress_status IS NULL OR ch.progress_status <> '" + PROGRESS_DRAFT + "')";

    /**
     * Tổng điều chỉnh giá trị của các phụ lục ĐÃ KÝ -- cộng lúc đọc, cùng lẽ
     * với AMENDMENT_COUNT_SQL.
     *
     * <p>Trên dòng PHỤ LỤC, {@code contract_value} mang nghĩa CHÊNH LỆCH có dấu
     * (bổ sung thì dương, giảm trừ thì âm), không phải tổng giá trị mới. Đó là
     * cách duy nhất diễn đạt được một phụ lục giảm trừ hạng mục bằng đúng cột
     * đã có, và là lý do phép cộng ở đây là một câu SUM chứ không phải "lấy
     * phụ lục ký gần nhất".
     *
     * <p>COALESCE: hợp đồng không có phụ lục nào thì SUM trả NULL, mà NULL cộng
     * vào giá trị gốc sẽ xoá sạch nó.
     */
    private static final String AMENDMENT_VALUE_SIGNED_SQL =
        "(SELECT COALESCE(SUM(ch.contract_value), 0) FROM contracts ch " +
        " WHERE ch.parent_contract_id = c.contract_id AND ch.is_deleted = 0 AND " + AMENDMENT_IS_SIGNED_SQL + ")";

    /** Như trên nhưng của phụ lục còn NHÁP -- chưa ký thì chưa đổi được giá trị hợp đồng. */
    private static final String AMENDMENT_VALUE_PENDING_SQL =
        "(SELECT COALESCE(SUM(ch.contract_value), 0) FROM contracts ch " +
        " WHERE ch.parent_contract_id = c.contract_id AND ch.is_deleted = 0 AND NOT " + AMENDMENT_IS_SIGNED_SQL + ")";

    /**
     * Các phòng đang giữ hợp đồng, gom thành một chuỗi -- nhãn "đang chờ: Kế
     * toán, Dự án" trên danh sách.
     *
     * <p>GROUP_CONCAT trong một câu con thay vì tra thêm cho từng dòng: mười
     * dòng một trang là mười lượt đi CSDL cho một nhãn nhỏ.
     */
    private static final String PENDING_DEPARTMENTS_SQL =
        "(SELECT GROUP_CONCAT(pd.department_name ORDER BY pd.department_name SEPARATOR ', ') "
        + " FROM contract_handovers ph JOIN departments pd ON pd.department_id = ph.department_id "
        + " WHERE ph.contract_id = c.contract_id AND ph.done_at IS NULL)";

    /** Số ngày của chặng đang chờ LÂU NHẤT -- thứ giám đốc quét mắt tìm điểm nghẽn. */
    private static final String PENDING_DAYS_SQL =
        "(SELECT COALESCE(MAX(DATEDIFF(NOW(), ph.handed_at)), 0) FROM contract_handovers ph "
        + " WHERE ph.contract_id = c.contract_id AND ph.done_at IS NULL)";

    private static final String SELECT_BASE =
        "SELECT c.contract_id, c.contract_code, c.title, c.contract_type, c.direction, c.signing_date, " +
        "       c.effective_date, c.end_date, c.enterprise_id, c.owner_id, c.attachment_url, " +
        "       c.signer_name, c.signer_position, c.counterparty_signer_name, c.counterparty_signer_position, c.authorization_ref, c.signing_place, c.contract_value, " +
        "       c.progress_status, c.created_at, c.updated_at, c.is_deleted, " +
        "       c.parent_contract_id, pc.contract_code AS parent_contract_code, " +
        "       " + AMENDMENT_COUNT_SQL + " AS amendment_count, " +
        "       " + AMENDMENT_VALUE_SIGNED_SQL + " AS amendment_value_signed, " +
        "       " + AMENDMENT_VALUE_PENDING_SQL + " AS amendment_value_pending, " +
        "       " + PENDING_DEPARTMENTS_SQL + " AS pending_departments, " +
        "       " + PENDING_DAYS_SQL + " AS pending_handover_days, " +
        "       e.enterprise_name, p.province_id, p.province_name, " +
        "       u.last_name AS owner_last_name, u.middle_name AS owner_middle_name, u.first_name AS owner_first_name " +
        "FROM contracts c " +
        "LEFT JOIN enterprises e ON c.enterprise_id = e.enterprise_id " +
        JOIN_PROVINCE_OF_ENTERPRISE +
        // Alias 'pc', không phải 'p' -- 'p' đã là provinces ở JOIN_PROVINCE_OF_ENTERPRISE.
        "LEFT JOIN contracts pc ON c.parent_contract_id = pc.contract_id " +
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
        return findAll(page, pageSize, keyword, statusFilter, typeFilter, provinceId, sortByProvince,
                period, direction, progressFilter, false);
    }

    /**
     * Như trên, kèm {@code rootsOnly}: true thì bỏ các PHỤ LỤC ra khỏi danh
     * sách, chỉ còn hợp đồng gốc.
     *
     * <p>Mặc định false -- phụ lục là hợp đồng đầy đủ, có vòng đời riêng phải
     * bấm Ký được, nên nó phải tìm thấy được bằng mã như mọi hợp đồng khác.
     */
    public List<Contract> findAll(int page, int pageSize, String keyword, String statusFilter, String typeFilter,
            Integer provinceId, boolean sortByProvince, Period period, String direction, String progressFilter,
            boolean rootsOnly) {
        return findAll(page, pageSize, keyword, statusFilter, typeFilter, provinceId, sortByProvince,
                period, direction, progressFilter, rootsOnly, null);
    }

    /**
     * Như trên, kèm {@code waitingDepartmentId}: chỉ lấy hợp đồng ĐANG NẰM CHỜ
     * ở phòng đó (chặng bàn giao chưa đóng).
     *
     * <p>Chữ ký này đã dài tới mức khó đọc. Bộ lọc tiếp theo thì gom hết lại
     * thành một đối tượng chứ đừng thêm tham số thứ mười ba -- chỗ nguy hiểm
     * không phải là đọc khó, mà là truyền nhầm thứ tự hai tham số cùng kiểu.
     */
    public List<Contract> findAll(int page, int pageSize, String keyword, String statusFilter, String typeFilter,
            Integer provinceId, boolean sortByProvince, Period period, String direction, String progressFilter,
            boolean rootsOnly, Integer waitingDepartmentId) {
        List<Contract> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, statusFilter, typeFilter, provinceId, period, direction, progressFilter,
                rootsOnly, waitingDepartmentId);
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
        return countAll(keyword, statusFilter, typeFilter, provinceId, period, direction, progressFilter, false);
    }

    /** Như trên, kèm {@code rootsOnly} -- phải đi cặp với findAll, nếu không phân trang đếm một đằng liệt kê một nẻo. */
    public int countAll(String keyword, String statusFilter, String typeFilter, Integer provinceId, Period period,
            String direction, String progressFilter, boolean rootsOnly) {
        return countAll(keyword, statusFilter, typeFilter, provinceId, period, direction, progressFilter,
                rootsOnly, null);
    }

    /** Như trên, kèm bộ lọc "đang chờ ở phòng" -- phải đi cặp với findAll. */
    public int countAll(String keyword, String statusFilter, String typeFilter, Integer provinceId, Period period,
            String direction, String progressFilter, boolean rootsOnly, Integer waitingDepartmentId) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM contracts c LEFT JOIN enterprises e ON c.enterprise_id = e.enterprise_id "
            + JOIN_PROVINCE_OF_ENTERPRISE);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, keyword, statusFilter, typeFilter, provinceId, period, direction, progressFilter,
                rootsOnly, waitingDepartmentId);

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
        return countStatusSummary(provinceId, period, direction, false);
    }

    /**
     * Như trên, kèm {@code rootsOnly} -- bốn con số của dải KPI phải đếm ĐÚNG
     * tập mà bảng bên dưới đang liệt kê.
     *
     * <p>Trước bản này, {@code direction} được NHẬN nhưng không đi vào câu lệnh:
     * đứng ở mục Hợp đồng mua vẫn thấy bốn con số của cả hợp đồng bán, mà không
     * chỗ nào trên màn hình giải thích vì sao chúng không cộng lại thành tổng ở
     * thanh phân trang. Tham số có mà không dùng thì nguy hơn là không có --
     * bên gọi tưởng đã lọc rồi.
     */
    public Map<String, Integer> countStatusSummary(Integer provinceId, Period period, String direction,
            boolean rootsOnly) {
        return countStatusSummary(provinceId, period, direction, rootsOnly, null);
    }

    /**
     * Như trên nhưng chỉ đếm hợp đồng do những người này phụ trách -- phạm vi
     * "của tôi" trên Dashboard (rỗng/null = toàn chi nhánh).
     */
    public Map<String, Integer> countStatusSummary(Integer provinceId, Period period, String direction,
            boolean rootsOnly, List<Integer> ownerIds) {
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
            + (period != null ? " AND c.signing_date BETWEEN ? AND ?" : "")
            + (direction != null ? " AND c.direction = ?" : "")
            + (rootsOnly ? " AND c.parent_contract_id IS NULL" : "")
            + SqlFilters.inClause("c.owner_id", ownerIds);

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int param = 1;
            if (provinceId != null) {
                ps.setInt(param++, provinceId);
            }
            if (period != null) {
                ps.setDate(param++, period.getFrom());
                ps.setDate(param++, period.getTo());
            }
            if (direction != null) {
                ps.setString(param++, direction);
            }
            SqlFilters.bind(ps, param, ownerIds);
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
        return findExpiringSoon(limit, provinceId, null);
    }

    /** Như trên nhưng chỉ lấy hợp đồng do những người này phụ trách (rỗng/null = tất cả). */
    public List<Contract> findExpiringSoon(int limit, Integer provinceId, List<Integer> ownerIds) {
        List<Contract> result = new ArrayList<>();
        String sql = SELECT_BASE +
            "WHERE c.is_deleted = 0 AND CURDATE() BETWEEN c.effective_date AND c.end_date " +
            "AND DATEDIFF(c.end_date, CURDATE()) <= " + SOON_THRESHOLD_DAYS + " " +
            // CHỈ hợp đồng gốc. Bảng này ở Dashboard trả lời "sắp tới phải lo
            // những hợp đồng nào", mà một phụ lục gia hạn đứng riêng cạnh hợp
            // đồng cha của nó là ĐẾM HAI LẦN một việc -- và cột giá trị bên
            // cạnh thì cộng lần nữa phần tiền đã nằm trong giá trị hiện hành
            // của cha. Phụ lục vẫn tìm được ở danh sách hợp đồng.
            "AND c.parent_contract_id IS NULL " +
            (provinceId != null ? "AND d.province_id = ? " : "") +
            SqlFilters.inClause("c.owner_id", ownerIds) + " " +
            "ORDER BY c.end_date ASC LIMIT ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int param = 1;
            if (provinceId != null) {
                ps.setInt(param++, provinceId);
            }
            param = SqlFilters.bind(ps, param, ownerIds);
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
     * Giá trị trả về của {@link #insert} khi hợp đồng cha không nhận được phụ
     * lục: không tồn tại, chưa ký, đã đóng băng, hoặc chính nó đã là phụ lục.
     *
     * <p>Lại tách khỏi -1 vì lý do như {@link #DUPLICATE_CODE}: "hợp đồng này
     * chưa ký nên chưa lập phụ lục được" là thứ người dùng sửa được, còn "lưu
     * thất bại" thì không.
     */
    public static final int INVALID_PARENT = -3;

    /**
     * Giá trị trả về của {@link #insert} khi phụ lục giảm trừ nhiều hơn số tiền
     * còn lại của hợp đồng gốc -- giá trị hiện hành sẽ âm.
     *
     * <p>Không phải lỗi kỹ thuật: nó là lỗi nhập liệu người dùng sửa được ngay
     * (gõ nhầm dấu, hoặc gõ TỔNG giá trị mới vào ô chênh lệch), nên cần một câu
     * trả lời riêng chứ không lẫn vào "lưu thất bại".
     */
    public static final int INVALID_VALUE = -4;

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
                " authorization_ref, signing_place, contract_value, parent_contract_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
                    // Phụ lục: hợp đồng cha phải đọc và KHOÁ trước khi ghi con.
                    // Kiểm ở controller thôi thì không đủ -- giữa lúc form mở ra
                    // và lúc bấm Lưu, hợp đồng cha có thể vừa được thanh lý, và
                    // phụ lục sẽ treo vào một hợp đồng đã chấm dứt.
                    Contract parent = null;
                    Integer parentId = contract.getParentContractId();
                    if (parentId != null) {
                        parent = lockForUpdate(conn, parentId);
                        if (!canTakeAmendment(parent)) {
                            return INVALID_PARENT;
                        }
                        // Giá trị trên dòng phụ lục là CHÊNH LỆCH có dấu, nên
                        // một phụ lục giảm trừ có thể kéo giá trị hợp đồng
                        // xuống dưới 0. Kiểm ở đây, trong transaction đã khoá
                        // cha: controller kiểm trước chỉ để báo lỗi tử tế, còn
                        // hai người cùng lập phụ lục giảm trừ thì chỉ chỗ này
                        // thấy được cả hai.
                        if (amendedValueWouldGoNegative(conn, parent, 0, contract.getContractValue())) {
                            return INVALID_VALUE;
                        }
                    }
                    // Đối tác và chiều của phụ lục LẤY TỪ CHA, không lấy từ form:
                    // phụ lục sửa đổi cho đúng bản hợp đồng đó, nên nó không thể
                    // ký với một đối tác khác hay đổi từ bán sang mua. Form không
                    // cho chọn hai thứ này, nhưng form không phải chốt chặn.
                    int enterpriseId = parent != null ? parent.getEnterpriseId() : contract.getEnterpriseId();
                    String direction = parent != null ? parent.getDirection() : contract.getDirection();

                    int newId = -1;
                    try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, contract.getContractCode());
                        ps.setString(2, contract.getTitle());
                        ps.setString(3, contract.getContractType());
                        ps.setString(4, direction);
                        ps.setDate(5, contract.getSigningDate());
                        ps.setDate(6, contract.getEffectiveDate());
                        ps.setDate(7, contract.getEndDate());
                        ps.setInt(8, enterpriseId);
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
                        // setObject, không setInt: phụ lục của hợp đồng gốc là
                        // NULL, và setInt(0) sẽ đâm vào khoá ngoại.
                        ps.setObject(20, parentId, java.sql.Types.INTEGER);

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
                    insertHistory(conn, newId, ContractHistory.EVENT_CREATED,
                            (parent == null ? "Tạo bản nháp " : "Tạo bản nháp phụ lục ")
                                    + contract.getContractCode()
                                    + (parent == null
                                            ? (direction == null ? "" : " — hợp đồng " + direction.toLowerCase())
                                            : " — sửa đổi cho hợp đồng " + parent.getContractCode())
                                    // Nháp có thể chưa chốt thời hạn (V26). Bỏ hẳn mệnh đề
                                    // đó khi thiếu, thay vì để lại 'hiệu lực  đến ' cụt lủn.
                                    + (contract.getEffectiveDate() == null || contract.getEndDate() == null
                                            ? ", chưa chốt thời hạn"
                                            : ", dự kiến hiệu lực " + formatDate(contract.getEffectiveDate())
                                                    + " đến " + formatDate(contract.getEndDate())),
                            actorId, null);

                    // Dòng thứ hai, ghi lên HỢP ĐỒNG CHA. Đó là chỗ người ta đi
                    // tìm: câu hỏi "hợp đồng này về sau có bị sửa gì không" được
                    // hỏi khi đang mở hợp đồng gốc, không phải khi đang mở phụ lục.
                    if (parent != null) {
                        insertHistory(conn, parent.getContractId(), ContractHistory.EVENT_AMENDMENT_CREATED,
                                "Lập phụ lục " + contract.getContractCode()
                                        + " — " + truncate(contract.getTitle(), 200),
                                actorId, null);
                    }

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

    /** Ghi mọi trường -- chỉ dùng cho bản NHÁP, và cho đường chữa sai sót của Admin. */
    private static final String SQL_UPDATE_ALL = "UPDATE contracts SET " +
            "contract_code = ?, title = ?, contract_type = ?, signing_date = ?, effective_date = ?, end_date = ?, " +
            "enterprise_id = ?, owner_id = ?, attachment_url = ?, status = ?, " +
            "signer_name = ?, signer_position = ?, counterparty_signer_name = ?, counterparty_signer_position = ?, " +
            "authorization_ref = ?, signing_place = ?, contract_value = ? " +
            "WHERE contract_id = ? AND is_deleted = 0";

    /**
     * Hai cột duy nhất còn ghi được sau khi hợp đồng đã ký.
     *
     * <p>Không cột nào trong hai cột này nằm trên tờ giấy hai bên ký: người phụ
     * trách là phân công nội bộ và đổi theo nhân sự, còn link bản PDF đã ký
     * thường chỉ CÓ sau khi ký. Khoá chúng lại thì đúng lúc hồ sơ hoàn tất là
     * lúc không đính được bản scan vào.
     *
     * <p>{@code status} cố ý không nằm ở đây: nó là hàm thuần của hai mốc ngày,
     * mà hai mốc đó đã khoá.
     */
    private static final String SQL_UPDATE_ADMIN_FIELDS =
            "UPDATE contracts SET owner_id = ?, attachment_url = ? WHERE contract_id = ? AND is_deleted = 0";

    /**
     * Bản ghi sẽ được ghi xuống khi các trường điều khoản đã khoá: lấy ĐIỀU
     * KHOẢN từ {@code before} (giá trị đang nằm trong CSDL) và chỉ nhận hai
     * trường quản trị từ {@code submitted}.
     *
     * <p>Dựng một đối tượng thay vì chỉ bỏ qua lúc bind, để câu nhật ký so sánh
     * đúng thứ đã ghi -- nếu không, gõ bừa vào một ô đã khoá sẽ sinh ra dòng
     * lịch sử kể một thay đổi không hề xảy ra.
     */
    private static Contract withTermsFrom(Contract before, Contract submitted) {
        Contract merged = new Contract();
        merged.setContractId(before.getContractId());
        merged.setContractCode(before.getContractCode());
        merged.setTitle(before.getTitle());
        merged.setContractType(before.getContractType());
        merged.setDirection(before.getDirection());
        merged.setSigningDate(before.getSigningDate());
        merged.setEffectiveDate(before.getEffectiveDate());
        merged.setEndDate(before.getEndDate());
        merged.setEnterpriseId(before.getEnterpriseId());
        merged.setSignerName(before.getSignerName());
        merged.setSignerPosition(before.getSignerPosition());
        merged.setCounterpartySignerName(before.getCounterpartySignerName());
        merged.setCounterpartySignerPosition(before.getCounterpartySignerPosition());
        merged.setAuthorizationRef(before.getAuthorizationRef());
        merged.setSigningPlace(before.getSigningPlace());
        merged.setContractValue(before.getContractValue());
        merged.setProgressStatus(before.getProgressStatus());
        merged.setParentContractId(before.getParentContractId());

        merged.setOwnerId(submitted.getOwnerId());
        merged.setAttachmentUrl(submitted.getAttachmentUrl());
        return merged;
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
                // ĐÃ KÝ: chỉ hai cột đi tiếp. Mọi trường còn lại là thứ in trên
                // tờ giấy hai bên đã ký -- đổi nó phải qua phụ lục, và chữa lỗi
                // gõ sai thì đi đường correct() của Admin, có lý do, có dấu vết.
                //
                // Ở đây phải BỎ QUA các giá trị đó, không phải từ chối cả lần
                // lưu: form vẫn gửi lên đủ mọi ô (ô khoá hiện dạng chỉ đọc,
                // trình duyệt vẫn đính kèm những ô có name), nên từ chối nghĩa
                // là đổi người phụ trách cũng không lưu được.
                boolean termsLocked = before.isTermsLocked();
                Contract written = termsLocked ? withTermsFrom(before, contract) : contract;

                // Phụ lục còn nháp thì giá trị của nó vẫn sửa được, nên đường
                // này cũng đẩy được giá trị hợp đồng gốc xuống dưới 0 -- đúng
                // thứ insert() đã chặn. Chặn cả hai đường, nếu không thì lập
                // phụ lục hợp lệ rồi sửa lại là đi vòng qua được luật.
                if (!termsLocked && before.isAmendment()
                        && amendedValueWouldGoNegative(conn, before.getParentContractId(),
                                before.getContractId(), written.getContractValue())) {
                    LOG.warn("Tu choi sua phu luc vi gia tri hop dong se am (contractId={})",
                            contract.getContractId());
                    return false;
                }

                try (PreparedStatement ps = conn.prepareStatement(termsLocked ? SQL_UPDATE_ADMIN_FIELDS : SQL_UPDATE_ALL)) {
                    if (termsLocked) {
                        ps.setInt(1, written.getOwnerId());
                        ps.setString(2, written.getAttachmentUrl());
                        ps.setInt(3, contract.getContractId());
                    } else {
                        ps.setString(1, written.getContractCode());
                        ps.setString(2, written.getTitle());
                        ps.setString(3, written.getContractType());
                        ps.setDate(4, written.getSigningDate());
                        ps.setDate(5, written.getEffectiveDate());
                        ps.setDate(6, written.getEndDate());
                        ps.setInt(7, written.getEnterpriseId());
                        ps.setInt(8, written.getOwnerId());
                        ps.setString(9, written.getAttachmentUrl());
                        ps.setString(10, computeStatus(written.getEffectiveDate(), written.getEndDate()));
                        bindSigningParties(ps, 11, written);
                        ps.setInt(18, contract.getContractId());
                    }
                    if (ps.executeUpdate() == 0) {
                        return false;
                    }
                }

                // Bấm Lưu mà không đổi gì thì KHÔNG sinh dòng nhật ký. Ghi cả
                // những lần như vậy thì dòng thời gian đầy các dòng "đã sửa"
                // không nói được đã sửa cái gì, và chôn mất những lần sửa thật.
                //
                // So với `written`, không với `contract`: sau khi ký, những ô
                // người dùng gõ vào các trường đã khoá không đi vào CSDL, nên
                // nhật ký cũng không được kể rằng chúng đã đổi.
                String changes = describeChanges(conn, before, written);
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
     * ADMIN chữa một sai sót NHẬP LIỆU trên hợp đồng đã ký -- đường duy nhất
     * chạm được vào các trường điều khoản sau khi {@link #update} đã khoá chúng.
     *
     * <p>Không phải cửa sau cho việc sửa nội dung hợp đồng. Sửa đổi THẬT -- hai
     * bên thoả thuận lại điều gì đó -- thì đi qua phụ lục, và để lại một văn bản
     * riêng có chữ ký. Cái này chỉ để chữa thứ gõ sai so với chính bản giấy đang
     * cầm: gõ nhầm một chữ số trong mã hợp đồng, chọn nhầm khách hàng lúc tạo.
     *
     * <p>Vì thế nó khác {@code update} ở ba điểm, và cả ba đều cố ý:
     *
     * <ul>
     *   <li><b>Lý do bắt buộc.</b> Một thay đổi trên hợp đồng đã ký mà không ai
     *       giải thích được thì về sau không phân biệt nổi với việc sửa để che.</li>
     *   <li><b>Dòng nhật ký mang loại riêng</b> ({@code EVENT_CORRECTED}), không
     *       lẫn vào "Sửa thông tin" của các bản nháp.</li>
     *   <li><b>Vẫn KHÔNG mở sau khi đóng băng.</b> Luật KH nói rõ "sau thanh lý
     *       không được thay đổi, KỂ CẢ CẤP CAO" -- Admin không phải ngoại lệ, và
     *       nếu Admin là ngoại lệ thì câu luật đó không còn nghĩa gì.</li>
     * </ul>
     *
     * <p>Quyền Admin kiểm ở {@code ContractController}; ở đây chỉ kiểm những thứ
     * phải đúng bất kể ai gọi.
     *
     * @param reason lý do người dùng nhập; rỗng thì từ chối, không ghi gì.
     */
    public boolean correct(Contract contract, int actorId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return false;
        }
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                Contract before = lockForUpdate(conn, contract.getContractId());
                if (before == null || before.isFrozen()) {
                    return false;
                }
                // Bản nháp đã sửa thẳng được rồi -- đi đường này chỉ làm dòng
                // thời gian mọc ra những dòng "Sửa sai sót" cho thứ chưa ai ký,
                // và làm loãng đúng loại sự kiện sinh ra để nổi bật.
                if (before.isDraft()) {
                    return false;
                }

                try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_ALL)) {
                    ps.setString(1, contract.getContractCode());
                    ps.setString(2, contract.getTitle());
                    ps.setString(3, contract.getContractType());
                    // Ngày ký KHÔNG sửa được kể cả ở đây: nó là dấu của một hành
                    // động hệ thống đã đóng lúc bấm Ký, không phải một ô khai báo.
                    ps.setDate(4, before.getSigningDate());
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

                Contract written = contract;
                written.setSigningDate(before.getSigningDate());
                String changes = describeChanges(conn, before, written);
                // Ở ĐÂY thì KHÔNG đổi gì cũng vẫn ghi -- ngược với update().
                // Ai đó vừa mở hợp đồng đã ký ra, khai một lý do và bấm lưu:
                // việc đó tự nó đáng ghi lại, kể cả khi cuối cùng không đổi ô nào.
                insertHistory(conn, contract.getContractId(), ContractHistory.EVENT_CORRECTED,
                        changes == null ? "Mở sửa sai sót nhưng không đổi trường nào" : changes,
                        actorId, reason.trim());

                conn.commit();
                committed = true;
            } finally {
                finishTransaction(conn, committed, "sua sai sot hop dong", contract.getContractId());
            }
            return committed;
        } catch (SQLException ex) {
            if (isDuplicateKeyError(ex, "contract_code")) {
                LOG.warn("Ma hop dong da ton tai khi sua sai sot (contractId={}, contractCode={})",
                        contract.getContractId(), contract.getContractCode());
            } else {
                LOG.error("Loi sua sai sot hop dong (contractId={})", contract.getContractId(), ex);
            }
            return false;
        }
    }

    // ==================================================================
    // Bàn giao hợp đồng giữa các phòng
    // ==================================================================
    //
    // Kinh doanh soạn xong thì chuyển xuống Kế toán và Dự án CÙNG LÚC, mỗi
    // phòng tự báo xong. Thứ khách hàng (giám đốc) hỏi là "đang nằm ở đâu, bao
    // lâu rồi", nên mỗi lượt giao là một dòng có mốc thời gian -- xem V32.

    /** Phòng đó đang còn giữ hợp đồng, không giao lại lượt mới được. */
    public static final int HANDOVER_ALREADY_PENDING = -1;

    private static final String HANDOVER_SELECT =
        "SELECT h.handover_id, h.contract_id, h.department_id, h.handed_at, h.handed_by, "
        + "       h.done_at, h.done_by, h.handover_note, h.done_note, "
        + "       d.department_name, c.contract_code, c.title AS contract_title, "
        + "       hb.last_name AS hb_last, hb.middle_name AS hb_mid, hb.first_name AS hb_first, "
        + "       db.last_name AS db_last, db.middle_name AS db_mid, db.first_name AS db_first, "
        + "       ow.last_name AS ow_last, ow.middle_name AS ow_mid, ow.first_name AS ow_first "
        + "FROM contract_handovers h "
        + "JOIN departments d ON d.department_id = h.department_id "
        + "JOIN contracts c ON c.contract_id = h.contract_id "
        + "LEFT JOIN users hb ON hb.user_id = h.handed_by "
        + "LEFT JOIN users db ON db.user_id = h.done_by "
        + "LEFT JOIN users ow ON ow.user_id = c.owner_id ";

    /**
     * Bàn giao một hợp đồng cho nhiều phòng CÙNG LÚC (Kế toán + Dự án là mặc
     * định của luồng). Một transaction cho cả lô: giao được nửa rồi hỏng thì
     * hợp đồng nằm ở trạng thái không ai mô tả nổi.
     *
     * @return số chặng mở ra, hoặc {@link #HANDOVER_ALREADY_PENDING} nếu MỘT
     *         trong các phòng đó đang còn giữ hợp đồng này
     */
    public int handOverToDepartments(int contractId, List<Integer> departmentIds, String note, int actorId) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return 0;
        }
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                Contract contract = lockForUpdate(conn, contractId);
                if (contract == null) {
                    return -2;
                }
                // Giao lại khi phòng đó CHƯA báo xong là đẻ ra hai chặng mở của
                // cùng một phòng -- "đang chờ bao lâu" lúc đó không có câu trả
                // lời. Bị trả về sửa rồi giao lại thì được, vì lượt cũ đã đóng.
                for (Integer departmentId : departmentIds) {
                    if (hasPendingHandover(conn, contractId, departmentId)) {
                        return HANDOVER_ALREADY_PENDING;
                    }
                }

                String trimmed = note == null || note.trim().isEmpty() ? null : note.trim();
                for (Integer departmentId : departmentIds) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO contract_handovers (contract_id, department_id, handed_by, handover_note) "
                            + "VALUES (?, ?, ?, ?)")) {
                        ps.setInt(1, contractId);
                        ps.setInt(2, departmentId);
                        ps.setInt(3, actorId);
                        ps.setString(4, trimmed);
                        ps.executeUpdate();
                    }
                    insertHistory(conn, contractId, ContractHistory.EVENT_HANDOVER,
                            "Bàn giao cho phòng " + departmentName(conn, departmentId), actorId, trimmed);
                }

                conn.commit();
                committed = true;
                return departmentIds.size();
            } finally {
                finishTransaction(conn, committed, "ban giao hop dong", contractId);
            }
        } catch (SQLException ex) {
            LOG.error("Loi ban giao hop dong (contractId={})", contractId, ex);
            return -2;
        }
    }

    /**
     * Phòng nhận báo đã xử lý xong. Ghi chú BẮT BUỘC: một chặng đóng lại mà
     * không ai nói đã làm gì thì về sau không phân biệt được với việc bấm cho
     * xong -- đúng lý do thanh lý hợp đồng cũng bắt nhập lý do.
     */
    public boolean completeHandover(int handoverId, int actorId, String doneNote) {
        if (doneNote == null || doneNote.trim().isEmpty()) {
            return false;
        }
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                int contractId;
                int departmentId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT contract_id, department_id FROM contract_handovers "
                        + "WHERE handover_id = ? AND done_at IS NULL FOR UPDATE")) {
                    ps.setInt(1, handoverId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            // Không tồn tại, hoặc người khác vừa xác nhận xong.
                            return false;
                        }
                        contractId = rs.getInt("contract_id");
                        departmentId = rs.getInt("department_id");
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE contract_handovers SET done_at = NOW(), done_by = ?, done_note = ? "
                        + "WHERE handover_id = ? AND done_at IS NULL")) {
                    ps.setInt(1, actorId);
                    ps.setString(2, doneNote.trim());
                    ps.setInt(3, handoverId);
                    if (ps.executeUpdate() == 0) {
                        return false;
                    }
                }

                insertHistory(conn, contractId, ContractHistory.EVENT_HANDOVER,
                        "Phòng " + departmentName(conn, departmentId) + " báo đã xử lý xong",
                        actorId, doneNote.trim());

                conn.commit();
                committed = true;
            } finally {
                finishTransaction(conn, committed, "xac nhan xong chang ban giao", handoverId);
            }
            return committed;
        } catch (SQLException ex) {
            LOG.error("Loi xac nhan xong chang ban giao (handoverId={})", handoverId, ex);
            return false;
        }
    }

    /** Các chặng của một hợp đồng, lượt mới nhất đứng trước. */
    public List<ContractHandover> findHandoversOf(int contractId) {
        List<ContractHandover> result = new ArrayList<>();
        String sql = HANDOVER_SELECT + "WHERE h.contract_id = ? ORDER BY h.handed_at DESC, h.handover_id DESC";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapHandoverRow(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van chang ban giao (contractId={})", contractId, ex);
        }
        return result;
    }

    /**
     * Mọi chặng CHƯA XONG của toàn hệ thống, phòng nào để lâu nhất đứng trước
     * -- màn hình theo dõi của giám đốc.
     *
     * @param ownerIds giới hạn theo người phụ trách hợp đồng (rỗng/null = tất cả)
     */
    public List<ContractHandover> findPendingHandovers(List<Integer> ownerIds) {
        List<ContractHandover> result = new ArrayList<>();
        String sql = HANDOVER_SELECT
                + "WHERE h.done_at IS NULL AND c.is_deleted = 0"
                + SqlFilters.inClause("c.owner_id", ownerIds)
                + " ORDER BY h.handed_at ASC";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            SqlFilters.bind(ps, 1, ownerIds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapHandoverRow(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van chang ban giao dang cho", ex);
        }
        return result;
    }

    private boolean hasPendingHandover(Connection conn, int contractId, int departmentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM contract_handovers "
                + "WHERE contract_id = ? AND department_id = ? AND done_at IS NULL")) {
            ps.setInt(1, contractId);
            ps.setInt(2, departmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Tên phòng, để câu nhật ký đọc được thay vì "phòng #5". */
    private String departmentName(Connection conn, int departmentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT department_name FROM departments WHERE department_id = ?")) {
            ps.setInt(1, departmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "#" + departmentId;
            }
        }
    }

    private ContractHandover mapHandoverRow(ResultSet rs) throws SQLException {
        ContractHandover h = new ContractHandover();
        h.setHandoverId(rs.getInt("handover_id"));
        h.setContractId(rs.getInt("contract_id"));
        h.setDepartmentId(rs.getInt("department_id"));
        h.setDepartmentName(rs.getString("department_name"));
        h.setHandedAt(rs.getTimestamp("handed_at"));
        h.setHandedBy(rs.getInt("handed_by"));
        h.setDoneAt(rs.getTimestamp("done_at"));
        int doneBy = rs.getInt("done_by");
        h.setDoneBy(rs.wasNull() ? null : doneBy);
        h.setHandoverNote(rs.getString("handover_note"));
        h.setDoneNote(rs.getString("done_note"));
        h.setContractCode(rs.getString("contract_code"));
        h.setContractTitle(rs.getString("contract_title"));
        h.setHandedByName(fullName(rs, "hb_last", "hb_mid", "hb_first"));
        h.setDoneByName(fullName(rs, "db_last", "db_mid", "db_first"));
        h.setOwnerName(fullName(rs, "ow_last", "ow_mid", "ow_first"));
        return h;
    }

    /** Ghép họ tên từ ba cột đã join; null khi không có người. */
    private static String fullName(ResultSet rs, String lastCol, String midCol, String firstCol) throws SQLException {
        String last = rs.getString(lastCol);
        if (last == null) {
            return null;
        }
        User u = new User();
        u.setLastName(last);
        u.setMiddleName(rs.getString(midCol));
        u.setFirstName(rs.getString(firstCol));
        return u.getFullName();
    }

    // ==================================================================
    // Liên kết hợp đồng bán <-> hợp đồng mua ("đầu ra kéo theo đầu vào")
    // ==================================================================
    //
    // Yêu cầu của khách hàng: bán một hợp đồng thì phải biết nó kéo theo những
    // đơn mua vào nào. Quan hệ NHIỀU-NHIỀU (mua gom chia cho nhiều hợp đồng
    // bán), nằm ở bảng riêng contract_links -- KHÔNG dùng lại parent_contract_id,
    // cột đó đang mang đúng một nghĩa "phụ lục của".

    /**
     * Chiều hợp đồng. Hằng ở đây chứ không mượn của controller: DAO là nơi cuối
     * cùng chặn một liên kết sai chiều, không được phụ thuộc tầng trên để biết
     * hai chữ đó viết thế nào.
     */
    public static final String DIRECTION_SELL = "Bán";
    public static final String DIRECTION_BUY = "Mua";

    /** {@link #linkContracts} thành công. */
    public static final int LINK_OK = 1;

    /**
     * Hai hợp đồng không nối được: không tồn tại, cùng chiều, hoặc có cái là
     * phụ lục. Tách khỏi -1 (lỗi chung) vì người dùng sửa được -- chọn lại đúng
     * hợp đồng là xong.
     */
    public static final int LINK_INVALID = -1;

    /** Cặp này đã nối rồi. Nối hai lần làm phép cộng giá trị đầu vào đếm đôi. */
    public static final int LINK_DUPLICATE = -2;

    /**
     * Nối một hợp đồng BÁN với một hợp đồng MUA.
     *
     * <p>Ba điều kiện, kiểm TRONG transaction sau khi đã khoá cả hai dòng:
     * đúng chiều (bán với mua, không phải hai cái cùng chiều), cả hai đều là
     * hợp đồng GỐC (phụ lục là văn bản sửa đổi của một hợp đồng, đầu vào phục
     * vụ cả hợp đồng chứ không phục vụ riêng một phụ lục), và chưa nối trước
     * đó. Kiểm ở controller thôi thì hai người bấm cùng lúc không thấy nhau.
     *
     * <p>Bản NHÁP vẫn nối được, cố ý: đơn mua thường được chuẩn bị trước khi
     * ký hợp đồng bán, bắt ký xong mới nối là bắt người ta nhớ quay lại làm sau.
     */
    public int linkContracts(int sellContractId, int buyContractId, String note, int actorId) {
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                Contract sell = lockForUpdate(conn, sellContractId);
                Contract buy = lockForUpdate(conn, buyContractId);
                if (!canLink(sell, buy)) {
                    return LINK_INVALID;
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO contract_links (sell_contract_id, buy_contract_id, note, created_by) "
                        + "VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, sellContractId);
                    ps.setInt(2, buyContractId);
                    ps.setString(3, note == null || note.trim().isEmpty() ? null : note.trim());
                    ps.setInt(4, actorId);
                    if (ps.executeUpdate() == 0) {
                        return -1;
                    }
                }

                // Hai dòng nhật ký, mỗi hợp đồng một dòng, mỗi dòng kể câu
                // chuyện từ phía của nó.
                insertHistory(conn, sellContractId, ContractHistory.EVENT_LINKED,
                        "Nối với hợp đồng mua " + buy.getContractCode()
                                + " — đầu vào phục vụ hợp đồng này", actorId, note);
                insertHistory(conn, buyContractId, ContractHistory.EVENT_LINKED,
                        "Phục vụ hợp đồng bán " + sell.getContractCode(), actorId, note);

                conn.commit();
                committed = true;
                return LINK_OK;
            } finally {
                finishTransaction(conn, committed, "noi hop dong", sellContractId);
            }
        } catch (SQLException ex) {
            if (isDuplicateKeyError(ex, "uq_contract_links_pair")) {
                LOG.warn("Hai hop dong da noi voi nhau (sellId={}, buyId={})", sellContractId, buyContractId);
                return LINK_DUPLICATE;
            }
            LOG.error("Loi noi hop dong (sellId={}, buyId={})", sellContractId, buyContractId, ex);
            return -1;
        }
    }

    /**
     * true nếu hai hợp đồng này nối được với nhau.
     *
     * <p>Không kiểm trạng thái tiến độ: kể cả hợp đồng đã thanh lý vẫn phải
     * xem được nó đã mua vào những gì, và nối muộn một đơn mua cho hợp đồng vừa
     * xong là chuyện chép lại lịch sử chứ không phải sửa điều khoản.
     */
    private static boolean canLink(Contract sell, Contract buy) {
        return sell != null && buy != null
                && sell.getContractId() != buy.getContractId()
                && DIRECTION_SELL.equals(sell.getDirection())
                && DIRECTION_BUY.equals(buy.getDirection())
                && !sell.isAmendment() && !buy.isAmendment();
    }

    /** Gỡ một liên kết, kèm dòng nhật ký ở cả hai hợp đồng. */
    public boolean unlinkContracts(int linkId, int actorId) {
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                // Đọc TRƯỚC khi xoá, cùng lẽ với deleteProductLine: sau lệnh
                // xoá thì không còn gì để đọc ra mà kể lại trong nhật ký.
                int sellId = 0;
                int buyId = 0;
                String sellCode = null;
                String buyCode = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT l.sell_contract_id, l.buy_contract_id, s.contract_code AS sell_code, "
                        + "       b.contract_code AS buy_code "
                        + "FROM contract_links l "
                        + "JOIN contracts s ON s.contract_id = l.sell_contract_id "
                        + "JOIN contracts b ON b.contract_id = l.buy_contract_id "
                        + "WHERE l.link_id = ? FOR UPDATE")) {
                    ps.setInt(1, linkId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return false;
                        }
                        sellId = rs.getInt("sell_contract_id");
                        buyId = rs.getInt("buy_contract_id");
                        sellCode = rs.getString("sell_code");
                        buyCode = rs.getString("buy_code");
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM contract_links WHERE link_id = ?")) {
                    ps.setInt(1, linkId);
                    if (ps.executeUpdate() == 0) {
                        return false;
                    }
                }

                insertHistory(conn, sellId, ContractHistory.EVENT_LINKED,
                        "Gỡ liên kết với hợp đồng mua " + buyCode, actorId, null);
                insertHistory(conn, buyId, ContractHistory.EVENT_LINKED,
                        "Gỡ liên kết với hợp đồng bán " + sellCode, actorId, null);

                conn.commit();
                committed = true;
            } finally {
                finishTransaction(conn, committed, "go lien ket hop dong", linkId);
            }
            return committed;
        } catch (SQLException ex) {
            LOG.error("Loi go lien ket hop dong (linkId={})", linkId, ex);
            return false;
        }
    }

    /**
     * Các hợp đồng nối với hợp đồng này, nhìn từ chính nó: mở một hợp đồng bán
     * thì ra các đơn MUA phục vụ nó, mở đơn mua thì ra các hợp đồng BÁN mà nó
     * phục vụ. Một câu duy nhất cho cả hai chiều -- hai câu thì hai màn hình
     * sớm muộn lệch nhau.
     *
     * <p>Bỏ qua liên kết mà đầu kia đã bị huỷ bản ghi: liên kết tới một hợp
     * đồng không còn trong danh sách nào thì người đọc không tra ra được gì.
     */
    public List<ContractLink> findLinksOf(int contractId) {
        List<ContractLink> result = new ArrayList<>();
        String sql =
            "SELECT l.link_id, l.sell_contract_id, l.buy_contract_id, l.relation_type, l.note, "
            + "       l.created_by, l.created_at, "
            + "       o.contract_id AS other_id, o.contract_code AS other_code, o.title AS other_title, "
            + "       o.direction AS other_direction, o.progress_status AS other_progress, "
            + "       o.contract_value AS other_value, o.effective_date AS other_effective, "
            + "       o.end_date AS other_end, e.enterprise_name AS other_enterprise, "
            + "       u.last_name, u.middle_name, u.first_name, "
            // Hợp đồng ở đầu kia cũng có thể có phụ lục, nên giá trị đem ra đối
            // chiếu phải là giá trị HIỆN HÀNH chứ không phải con số trên bản gốc.
            + "       (SELECT COALESCE(SUM(a.contract_value), 0) FROM contracts a "
            + "         WHERE a.parent_contract_id = o.contract_id AND a.is_deleted = 0 "
            + "           AND (a.progress_status IS NULL OR a.progress_status <> '" + PROGRESS_DRAFT + "')"
            + "       ) AS other_amendment_value, "
            // Đơn mua gom: bao nhiêu hợp đồng bán KHÁC cũng đang dùng nó. Câu
            // này chỉ khớp khi đầu kia là hợp đồng MUA -- nhìn từ phía mua thì
            // buy_contract_id không phải id của chính nó, nên tự ra 0.
            + "       (SELECT COUNT(*) FROM contract_links l2 "
            + "         WHERE l2.buy_contract_id = o.contract_id AND l2.link_id <> l.link_id) AS shared_count "
            + "FROM contract_links l "
            + "JOIN contracts o ON o.contract_id = CASE WHEN l.sell_contract_id = ? "
            + "                                         THEN l.buy_contract_id ELSE l.sell_contract_id END "
            + "LEFT JOIN enterprises e ON o.enterprise_id = e.enterprise_id "
            + "LEFT JOIN users u ON l.created_by = u.user_id "
            + "WHERE (l.sell_contract_id = ? OR l.buy_contract_id = ?) AND o.is_deleted = 0 "
            + "ORDER BY l.created_at, l.link_id";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            ps.setInt(2, contractId);
            ps.setInt(3, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapLinkRow(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van lien ket hop dong (contractId={})", contractId, ex);
        }
        return result;
    }

    private ContractLink mapLinkRow(ResultSet rs) throws SQLException {
        ContractLink link = new ContractLink();
        link.setLinkId(rs.getInt("link_id"));
        link.setSellContractId(rs.getInt("sell_contract_id"));
        link.setBuyContractId(rs.getInt("buy_contract_id"));
        link.setRelationType(rs.getString("relation_type"));
        link.setNote(rs.getString("note"));
        link.setCreatedBy(rs.getInt("created_by"));
        link.setCreatedAt(rs.getTimestamp("created_at"));

        Contract other = new Contract();
        other.setContractId(rs.getInt("other_id"));
        other.setContractCode(rs.getString("other_code"));
        other.setTitle(rs.getString("other_title"));
        other.setDirection(rs.getString("other_direction"));
        other.setProgressStatus(rs.getString("other_progress"));
        other.setContractValue(rs.getBigDecimal("other_value"));
        other.setAmendmentValueSigned(rs.getBigDecimal("other_amendment_value"));
        other.setEffectiveDate(rs.getDate("other_effective"));
        other.setEndDate(rs.getDate("other_end"));
        other.setStatus(computeStatus(other.getEffectiveDate(), other.getEndDate()));
        String enterpriseName = rs.getString("other_enterprise");
        if (enterpriseName != null) {
            Enterprise e = new Enterprise();
            e.setEnterpriseName(enterpriseName);
            other.setEnterprise(e);
        }
        link.setSharedCount(rs.getInt("shared_count"));
        link.setOther(other);

        String lastName = rs.getString("last_name");
        if (lastName != null) {
            User u = new User();
            u.setUserId(link.getCreatedBy());
            u.setLastName(lastName);
            u.setMiddleName(rs.getString("middle_name"));
            u.setFirstName(rs.getString("first_name"));
            link.setCreatedByName(u.getFullName());
        }
        return link;
    }

    /**
     * Tổng giá trị HIỆN HÀNH của các đơn mua đã nối vào một hợp đồng bán --
     * vế "đầu vào" của phép đối chiếu trên màn hình.
     *
     * <p>Cộng cả phụ lục đã ký của từng đơn mua, cùng quy tắc với giá trị hiện
     * hành ở mọi chỗ khác; đơn mua bị huỷ bản ghi thì không tính.
     */
    public BigDecimal sumLinkedBuyValue(int sellContractId) {
        String sql =
            "SELECT COALESCE(SUM(COALESCE(b.contract_value, 0) + ("
            + "    SELECT COALESCE(SUM(a.contract_value), 0) FROM contracts a "
            + "     WHERE a.parent_contract_id = b.contract_id AND a.is_deleted = 0 "
            + "       AND (a.progress_status IS NULL OR a.progress_status <> '" + PROGRESS_DRAFT + "')"
            + ")), 0) "
            + "FROM contract_links l "
            + "JOIN contracts b ON b.contract_id = l.buy_contract_id "
            + "WHERE l.sell_contract_id = ? AND b.is_deleted = 0";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellContractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi tinh tong gia tri dau vao (sellContractId={})", sellContractId, ex);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Các phụ lục của một hợp đồng, phụ lục lập trước đứng trước.
     *
     * <p>Sắp theo contract_id chứ không theo ngày ký: phụ lục còn là bản nháp
     * thì chưa có ngày ký, và thứ tự LẬP mới là thứ tự người ta đánh số phụ lục
     * trên giấy (PL01, PL02...).
     */
    public List<Contract> findAmendmentsByParentId(int parentContractId) {
        List<Contract> result = new ArrayList<>();
        String sql = SELECT_BASE + "WHERE c.parent_contract_id = ? AND c.is_deleted = 0 ORDER BY c.contract_id";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parentContractId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van phu luc hop dong (parentContractId={})", parentContractId, ex);
        }
        return result;
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
                // Còn phụ lục thì không huỷ được hợp đồng cha: phụ lục trỏ về
                // nó bằng parent_contract_id, và một phụ lục treo vào hợp đồng
                // đã biến mất khỏi mọi danh sách là một văn bản sửa đổi không ai
                // tra ngược được nó sửa cho cái gì.
                //
                // Kiểm TRONG transaction, sau khi đã khoá: giữa lúc mở hộp thoại
                // huỷ và lúc bấm xác nhận, người khác có thể vừa lập phụ lục.
                // Khoá ngoại không thay được phép kiểm này -- hợp đồng xoá MỀM,
                // nên CSDL không thấy có gì bị xoá cả.
                if (countLiveAmendments(conn, contractId) > 0) {
                    return false;
                }
                // Đọc TRƯỚC khi huỷ, cùng lẽ với deleteProductLine: sau lệnh
                // ghi thì không còn gì để đọc ra mà kể lại trong nhật ký.
                Contract voided = lockForUpdate(conn, contractId);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, contractId);
                    if (ps.executeUpdate() == 0) {
                        return false;
                    }
                }
                insertHistory(conn, contractId, ContractHistory.EVENT_VOIDED,
                        "Huỷ bản ghi khỏi danh sách (dữ liệu vẫn còn trong CSDL)",
                        actorId, reason.trim());

                // Huỷ bản ghi một phụ lục ĐÃ KÝ rút luôn phần giá trị nó mang
                // theo ra khỏi hợp đồng gốc (mọi câu cộng đều lọc is_deleted).
                // Giá trị hợp đồng đổi mà không dòng nào ghi lại thì đúng bằng
                // việc sửa thẳng -- thứ mà cả đợt này dựng ra để chặn.
                if (voided != null && voided.isAmendment() && !voided.isDraft()
                        && voided.getContractValue() != null && voided.getContractValue().signum() != 0) {
                    logValueAdjustment(conn, voided.getParentContractId(), voided.getContractValue().negate(),
                            actorId, "Huỷ bản ghi phụ lục " + voided.getContractCode());
                }
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

                // KÝ một phụ lục có giá trị là khoảnh khắc giá trị hợp đồng gốc
                // thật sự đổi -- trước đó nó mới chỉ là bản nháp ai cũng sửa
                // được. Dòng này ghi lên HỢP ĐỒNG GỐC vì đó là chỗ người ta mở
                // ra để hỏi "sao giờ nó không còn là con số đã ký nữa".
                if (PROGRESS_SIGNED.equals(toStatus) && current.isAmendment()
                        && current.getContractValue() != null && current.getContractValue().signum() != 0) {
                    logValueAdjustment(conn, current.getParentContractId(), current.getContractValue(),
                            actorId, "Phụ lục " + current.getContractCode() + " được ký");
                }

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
     * true nếu hợp đồng này nhận được phụ lục. {@code parent} là bản đã khoá
     * trong transaction, null khi không tìm thấy.
     *
     * <p>Ba điều kiện, ba lý do khác nhau:
     *
     * <ul>
     *   <li><b>Phải đã ký.</b> Bản nháp sửa thẳng được, nên phụ lục ở đó chỉ là
     *       đường vòng dựng ra hai bản ghi cho một thứ chưa ai ký.</li>
     *   <li><b>Chưa đóng băng.</b> Luật KH: xong hợp đồng = đã thanh lý, sau đó
     *       không thay đổi kể cả cấp cao. Phát sinh sau thanh lý là hợp đồng
     *       MỚI -- không còn gì để sửa đổi khi hợp đồng đã chấm dứt. (Đây là
     *       chỗ KH từng hỏi ngược lại; đổi ý thì sửa đúng dòng này.)</li>
     *   <li><b>Chính nó không phải phụ lục.</b> MỘT TẦNG: mọi văn bản sửa đổi
     *       treo vào đúng hợp đồng gốc, nếu không thì "hợp đồng này đã bị sửa
     *       những gì" phải đi lần theo một chuỗi dài không ai biết trước.</li>
     * </ul>
     */
    private static boolean canTakeAmendment(Contract parent) {
        return parent != null && parent.isSigned() && !parent.isAmendment();
    }

    /**
     * Tổng điều chỉnh giá trị của các phụ lục ĐÃ KÝ, đọc trong transaction đang
     * mở. {@code excludeAmendmentId} là phụ lục đang được sửa (0 khi đang lập
     * mới) -- phải bỏ ra, nếu không thì giá trị cũ của chính nó bị cộng thêm
     * một lần nữa vào phép kiểm.
     */
    private BigDecimal sumSignedAmendmentValue(Connection conn, int parentContractId, int excludeAmendmentId)
            throws SQLException {
        String sql = "SELECT COALESCE(SUM(contract_value), 0) FROM contracts " +
                     "WHERE parent_contract_id = ? AND is_deleted = 0 AND contract_id <> ? " +
                     "AND (progress_status IS NULL OR progress_status <> ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, parentContractId);
            ps.setInt(2, excludeAmendmentId);
            ps.setString(3, PROGRESS_DRAFT);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    /**
     * true nếu cộng thêm {@code delta} vào hợp đồng {@code parent} sẽ đẩy giá
     * trị hiện hành xuống dưới 0.
     *
     * <p>Chỉ chặn phần ÂM: phụ lục bổ sung thì bao nhiêu cũng hợp lệ, còn hợp
     * đồng gốc chưa chốt giá (null) thì mọi khoản giảm trừ đều là giảm trừ trên
     * số 0 -- đó cũng là âm, và cũng vô nghĩa như nhau.
     */
    private boolean amendedValueWouldGoNegative(Connection conn, Contract parent, int excludeAmendmentId,
            BigDecimal delta) throws SQLException {
        if (delta == null || delta.signum() >= 0) {
            return false;
        }
        BigDecimal base = parent.getContractValue() == null ? BigDecimal.ZERO : parent.getContractValue();
        BigDecimal signedSoFar = sumSignedAmendmentValue(conn, parent.getContractId(), excludeAmendmentId);
        return base.add(signedSoFar).add(delta).signum() < 0;
    }

    /**
     * Như trên nhưng khi trong tay chỉ có id hợp đồng cha. Đọc giá trị cha bằng
     * SELECT thường, KHÔNG {@code FOR UPDATE}: đường gọi duy nhất ({@link #update}
     * sửa một phụ lục nháp) đã khoá dòng CON rồi, và khoá tiếp dòng CHA ở đây là
     * đi ngược thứ tự khoá của {@link #insert} -- đúng công thức dựng ra deadlock.
     * Cùng lắm là đọc phải một giá trị cha vừa đổi xong, mà giá trị của hợp đồng
     * đã ký thì chỉ correct() của Admin mới đổi được.
     */
    private boolean amendedValueWouldGoNegative(Connection conn, int parentContractId, int excludeAmendmentId,
            BigDecimal delta) throws SQLException {
        if (delta == null || delta.signum() >= 0) {
            return false;
        }
        Contract parent = new Contract();
        parent.setContractId(parentContractId);
        parent.setContractValue(readContractValue(conn, parentContractId));
        return amendedValueWouldGoNegative(conn, parent, excludeAmendmentId, delta);
    }

    /**
     * Ghi lên HỢP ĐỒNG GỐC một dòng nhật ký nói giá trị vừa đổi bao nhiêu và
     * giờ là bao nhiêu. Gọi SAU khi thay đổi đã ghi xong, trong cùng transaction
     * -- giá trị hiện hành được cộng lại từ CSDL chứ không tính tay từ hai biến
     * trong bộ nhớ, nên nó luôn là con số mà màn hình sẽ hiện ra ngay sau đó.
     *
     * @param delta phần thay đổi để KỂ LẠI (đã đổi dấu sẵn khi là huỷ bản ghi);
     *              chỉ dùng cho câu chữ, không dùng để tính tổng
     */
    private void logValueAdjustment(Connection conn, Integer parentContractId, BigDecimal delta, int actorId,
            String prefix) throws SQLException {
        if (parentContractId == null || delta == null) {
            return;
        }
        BigDecimal base = readContractValue(conn, parentContractId);
        BigDecimal current = (base == null ? BigDecimal.ZERO : base)
                .add(sumSignedAmendmentValue(conn, parentContractId, 0));
        insertHistory(conn, parentContractId, ContractHistory.EVENT_VALUE_ADJUSTED,
                prefix + " — điều chỉnh " + formatSignedMoney(delta)
                        + ", giá trị hợp đồng hiện hành " + formatMoney(current)
                        + " (giá trị theo bản gốc đã ký: "
                        + (base == null ? "chưa chốt" : formatMoney(base)) + ")",
                actorId, null);
    }

    /** Giá trị theo điều khoản của một hợp đồng, đọc trong transaction đang mở; null khi chưa chốt giá. */
    private BigDecimal readContractValue(Connection conn, int contractId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT contract_value FROM contracts WHERE contract_id = ?")) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("contract_value") : null;
            }
        }
    }

    /** Số phụ lục chưa bị huỷ bản ghi của một hợp đồng, đọc trong transaction đang mở. */
    private int countLiveAmendments(Connection conn, int contractId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM contracts WHERE parent_contract_id = ? AND is_deleted = 0";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
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
                "       authorization_ref, signing_place, contract_value, parent_contract_id " +
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
                int parentId = rs.getInt("parent_contract_id");
                c.setParentContractId(rs.wasNull() ? null : parentId);
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
            String typeFilter, Integer provinceId, Period period, String direction, String progressFilter,
            boolean rootsOnly, Integer waitingDepartmentId) {
        List<String> conditions = new ArrayList<>();
        conditions.add("c.is_deleted = 0");

        // Phụ lục nằm CÙNG bảng và mặc định hiện thành dòng riêng trên danh
        // sách: chính nó cũng phải được ký, nên phải tới được bằng tìm kiếm chứ
        // không chỉ qua trang hợp đồng cha. Ô lọc này để người dùng thu về danh
        // sách hợp đồng gốc khi cần đếm "bao nhiêu hợp đồng" theo nghĩa thường.
        if (rootsOnly) {
            conditions.add("c.parent_contract_id IS NULL");
        }

        // "Đang chờ ở phòng X": có chặng bàn giao CHƯA ĐÓNG ở phòng đó. EXISTS
        // chứ không JOIN -- một hợp đồng chờ ở hai phòng thì JOIN nhân đôi dòng,
        // và phân trang đếm một đằng liệt kê một nẻo.
        if (waitingDepartmentId != null) {
            conditions.add("EXISTS (SELECT 1 FROM contract_handovers wh "
                    + "WHERE wh.contract_id = c.contract_id AND wh.done_at IS NULL "
                    + "AND wh.department_id = ?)");
            params.add(waitingDepartmentId);
        }

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
        // SELECT_BASE vẫn luôn chọn cột này; chỗ đọc mới là chỗ từng thiếu.
        // Không có nó thì findById() trả về hợp đồng có direction = null, và
        // vì counterpartyRoleFor(null) rơi về 'Khách mua', MỌI hợp đồng MUA mở
        // form sửa lên đều đổ sai bộ loại hợp đồng rồi bấm Lưu là báo "dữ liệu
        // chưa hợp lệ" -- tức là không sửa được cái nào.
        c.setDirection(rs.getString("direction"));
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

        // getInt trả 0 cho NULL, mà 0 không phải "không có cha" -- phải hỏi
        // wasNull() mới phân biệt được hợp đồng gốc với phụ lục.
        int parentId = rs.getInt("parent_contract_id");
        c.setParentContractId(rs.wasNull() ? null : parentId);
        c.setParentContractCode(rs.getString("parent_contract_code"));
        c.setAmendmentCount(rs.getInt("amendment_count"));
        c.setAmendmentValueSigned(rs.getBigDecimal("amendment_value_signed"));
        c.setAmendmentValuePending(rs.getBigDecimal("amendment_value_pending"));
        c.setPendingDepartments(rs.getString("pending_departments"));
        c.setPendingHandoverDays(rs.getInt("pending_handover_days"));

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

    /**
     * Như trên nhưng có dấu, dùng cho phần ĐIỀU CHỈNH của phụ lục: "+250.000.000 đ"
     * và "-80.000.000 đ" đọc ra ngay là bổ sung hay giảm trừ, còn con số trơ trọi
     * thì phải tra sang chỗ khác mới biết.
     */
    private static String formatSignedMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return (amount.signum() > 0 ? "+" : "") + formatMoney(amount);
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
        return sumInvoiceAmountInPeriod(period, provinceId, null);
    }

    /** Như trên nhưng chỉ tính hợp đồng do những người này phụ trách (rỗng/null = tất cả). */
    public BigDecimal sumInvoiceAmountInPeriod(Period period, Integer provinceId, List<Integer> ownerIds) {
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
                     (provinceId != null ? " AND d.province_id = ?" : "") +
                     SqlFilters.inClause("c.owner_id", ownerIds);
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, period.getFrom());
            ps.setDate(2, period.getTo());
            int param = 3;
            if (provinceId != null) {
                ps.setInt(param++, provinceId);
            }
            SqlFilters.bind(ps, param, ownerIds);
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
        return sumInvoiceAmountByMonth(year, month, provinceId, null);
    }

    /** Như trên nhưng chỉ tính hợp đồng do những người này phụ trách (rỗng/null = tất cả). */
    public BigDecimal sumInvoiceAmountByMonth(int year, int month, Integer provinceId, List<Integer> ownerIds) {
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
                     (provinceId != null ? " AND d.province_id = ?" : "") +
                     SqlFilters.inClause("c.owner_id", ownerIds);
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, year);
            ps.setInt(2, month);
            int param = 3;
            if (provinceId != null) {
                ps.setInt(param++, provinceId);
            }
            SqlFilters.bind(ps, param, ownerIds);
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

    /**
     * Tổng tiền đã lập kỳ của CẢ CỤM hợp đồng: bản gốc cộng mọi phụ lục còn
     * sống của nó.
     *
     * <p>Đây là con số duy nhất đối chiếu được với giá trị hiện hành. Kỳ thanh
     * toán treo vào đúng bản ghi lập ra nó -- phần bổ sung theo phụ lục thường
     * được lập kỳ ngay trên phụ lục -- nên so tổng kỳ của riêng hợp đồng gốc
     * với giá trị đã cộng phụ lục thì cảnh báo "không khớp" nổ ở mọi hợp đồng
     * có phụ lục, tức là nó hết nói được điều gì.
     *
     * @param rootContractId id hợp đồng GỐC (không phải phụ lục)
     */
    public BigDecimal sumScheduledPaymentsForCluster(int rootContractId) {
        String sql = "SELECT COALESCE(SUM(p.invoice_amount), 0) FROM contract_payments p " +
                     "JOIN contracts c ON p.contract_id = c.contract_id " +
                     "WHERE c.is_deleted = 0 AND (c.contract_id = ? OR c.parent_contract_id = ?)";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rootContractId);
            ps.setInt(2, rootContractId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi tinh tong ky thanh toan ca cum (rootContractId={})", rootContractId, ex);
        }
        return BigDecimal.ZERO;
    }

}
