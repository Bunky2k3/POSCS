package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import poscs.model.District;
import poscs.model.Province;

/** DAO cho bảng provinces/districts, phục vụ các dropdown địa chỉ. */
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

    /** Lấy toàn bộ quận/huyện (mọi tỉnh) -- JS phía client tự lọc theo tỉnh đã chọn qua data-province-id. */
    public List<District> findAllDistricts() {
        List<District> result = new ArrayList<>();
        String sql = "SELECT districts_id, districts_name, province_id FROM districts ORDER BY districts_name";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                District d = new District();
                d.setDistrictId(rs.getInt("districts_id"));
                d.setDistrictName(rs.getString("districts_name"));
                d.setProvinceId(rs.getInt("province_id"));
                result.add(d);
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN DANH SACH QUAN/HUYEN ---");
            ex.printStackTrace();
        }
        return result;
    }
}
