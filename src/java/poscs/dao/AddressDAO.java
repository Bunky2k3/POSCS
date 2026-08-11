package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import poscs.model.Province;
import poscs.model.Ward;

/** DAO cho bảng provinces/wards, phục vụ các dropdown địa chỉ (chính quyền 2 cấp: Tỉnh/Thành phố -> Xã/Phường). */
public class AddressDAO {

    public List<Province> findAllProvinces() {
        List<Province> result = new ArrayList<>();
        String sql = "SELECT province_id, province_name FROM provinces ORDER BY province_name";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Province p = new Province();
                p.setProvinceId(rs.getInt("province_id"));
                p.setProvinceName(rs.getString("province_name"));
                result.add(p);
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN DANH SACH TINH/THANH PHO ---");
            ex.printStackTrace();
        }
        return result;
    }

    /** Lấy toàn bộ xã/phường (mọi tỉnh) -- JS phía client tự lọc theo tỉnh đã chọn qua data-province-id. */
    public List<Ward> findAllWards() {
        List<Ward> result = new ArrayList<>();
        String sql = "SELECT ward_id, ward_name, province_id FROM wards ORDER BY ward_name";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Ward w = new Ward();
                w.setWardId(rs.getInt("ward_id"));
                w.setWardName(rs.getString("ward_name"));
                w.setProvinceId(rs.getInt("province_id"));
                result.add(w);
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN DANH SACH XA/PHUONG ---");
            ex.printStackTrace();
        }
        return result;
    }
}
