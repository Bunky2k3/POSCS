package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.model.BranchInfo;

/**
 * Data Access Object cho Thông tin chi nhánh (bảng {@code branch_info}, V39 -
 * [UC-55]): thông tin Giám đốc chi nhánh, nguồn người ký bên mình trên hợp
 * đồng (BR-56).
 *
 * <p>Bảng chỉ có MỘT dòng (id = 1) gieo sẵn ở V39, và trang Thông tin chi
 * nhánh chỉ xem và sửa, không tạo/xoá (SPEC 5.8). Nên DAO chỉ có đọc và
 * UPDATE dòng đó, không có INSERT/DELETE.
 *
 * <p>Kiểm tra dữ liệu theo BR-57 (bắt buộc, độ dài, định dạng SĐT/fax) không
 * nằm ở đây mà ở tầng validator của trang Thông tin chi nhánh.
 */
public class BranchInfoDAO {

    private static final Logger LOG = LoggerFactory.getLogger(BranchInfoDAO.class);

    /** Khoá của dòng duy nhất trong branch_info, gieo sẵn ở V39. */
    static final int ROW_ID = 1;

    /**
     * Đọc thông tin chi nhánh.
     *
     * @return thông tin GĐCN; {@code null} khi lỗi CSDL hoặc thiếu dòng id = 1
     *         (CSDL chưa chạy V39 hoặc dòng gieo bị xoá tay)
     */
    public BranchInfo get() {
        String sql = "SELECT director_name, director_position, phone, fax, updated_by, updated_at "
                + "FROM branch_info WHERE branch_info_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ROW_ID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException ex) {
            LOG.error("Loi doc thong tin chi nhanh", ex);
            return null;
        }
    }

    /**
     * Ghi đè bốn ô thông tin GĐCN của dòng id = 1 và ghi người sửa.
     *
     * <p>Giá trị được ghi đúng như truyền vào (null ghi thành NULL); chuẩn hoá
     * và kiểm tra BR-57 là việc của tầng gọi.
     *
     * @param info    thông tin mới
     * @param actorId user_id của Admin đang sửa, ghi vào {@code updated_by}
     * @return {@code true} nếu cập nhật được dòng id = 1; {@code false} khi
     *         tham số không hợp lệ, không có dòng id = 1, hoặc lỗi CSDL
     */
    public boolean update(BranchInfo info, int actorId) {
        if (info == null || actorId <= 0) {
            return false;
        }
        String sql = "UPDATE branch_info SET director_name = ?, director_position = ?, phone = ?, fax = ?, "
                + "updated_by = ? WHERE branch_info_id = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, info.getDirectorName());
            ps.setString(2, info.getDirectorPosition());
            ps.setString(3, info.getPhone());
            ps.setString(4, info.getFax());
            ps.setInt(5, actorId);
            ps.setInt(6, ROW_ID);
            // Connector/J mặc định trả số dòng KHỚP WHERE (không phải số dòng
            // đổi giá trị), nên lưu lại đúng nội dung cũ vẫn ra 1. Ra 0 chỉ
            // khi thiếu dòng id = 1.
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            LOG.error("Loi cap nhat thong tin chi nhanh (actorId={})", actorId, ex);
            return false;
        }
    }

    private static BranchInfo mapRow(ResultSet rs) throws SQLException {
        BranchInfo info = new BranchInfo();
        info.setDirectorName(rs.getString("director_name"));
        info.setDirectorPosition(rs.getString("director_position"));
        info.setPhone(rs.getString("phone"));
        info.setFax(rs.getString("fax"));
        int updatedBy = rs.getInt("updated_by");
        // getInt trả 0 cho NULL -- hỏi wasNull() để phân biệt "chưa ai sửa".
        info.setUpdatedBy(rs.wasNull() ? null : updatedBy);
        info.setUpdatedAt(rs.getTimestamp("updated_at"));
        return info;
    }
}
