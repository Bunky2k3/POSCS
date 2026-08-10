package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import poscs.model.User;

public class EmployeeDAO {

    /** Lấy toàn bộ nhân viên đang hoạt động (is_deleted=0), phục vụ các dropdown "Nhân viên phụ trách". */
    public List<User> findAllActive() {
        List<User> result = new ArrayList<>();
        String sql = "SELECT user_id, last_name, middle_name, first_name, department " +
                     "FROM users WHERE is_deleted = 0 ORDER BY last_name, first_name";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                User u = new User();
                u.setUserId(rs.getInt("user_id"));
                u.setLastName(rs.getString("last_name"));
                u.setMiddleName(rs.getString("middle_name"));
                u.setFirstName(rs.getString("first_name"));
                u.setDepartment(rs.getString("department"));
                result.add(u);
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN DANH SACH NHAN VIEN ---");
            ex.printStackTrace();
        }
        return result;
    }
}
