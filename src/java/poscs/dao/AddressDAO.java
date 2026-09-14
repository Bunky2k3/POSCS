package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.model.District;
import poscs.model.Province;

/**
 * DAO cho bảng provinces/districts (districts giờ lưu xã/phường sau sáp nhập
 * tỉnh 2025, xem V3__seed_provinces_and_wards_2025__Bunky2k3.sql), phục vụ
 * các dropdown địa chỉ.
 *
 * Dữ liệu tỉnh/xã gần như không đổi khi ứng dụng đang chạy, nên cache trong
 * bộ nhớ: findAllProvinces() (34 dòng) và findWardsByProvinceId() theo từng
 * tỉnh. Trước đây có findAllDistricts() nạp toàn bộ ~3.321 xã/phường trong
 * 1 lần gọi (dùng ở form thêm/sửa khách hàng và sửa hồ sơ) -- ổn khi bảng
 * này còn ~700 quận/huyện, nhưng sau sáp nhập tăng lên ~3.321 dòng thì mỗi
 * lần mở form là quét lại toàn bảng + đổ ~3.321 thẻ <option> ẩn ra HTML.
 * Đã bỏ hàm đó, thay bằng findWardsByProvinceId() nạp theo tỉnh qua AJAX
 * (xem AddressController).
 */
public class AddressDAO {

    private static final Logger LOG = LoggerFactory.getLogger(AddressDAO.class);

    private static volatile List<Province> provincesCache;
    private static final Map<Integer, List<District>> wardsByProvinceCache = new ConcurrentHashMap<>();

    /**
     * Biểu thức ORDER BY sắp tỉnh theo tên ĐÃ BỎ tiền tố "Tỉnh "/"Thành phố ",
     * tức đúng thứ tự người dùng nhìn thấy (Province.getShortName() cũng bỏ hai
     * tiền tố này). Sắp theo province_name nguyên gốc thì 6 thành phố trực thuộc
     * trung ương dồn hết xuống dưới chữ "T", nhìn như chưa sắp xếp.
     *
     * Yêu cầu câu truy vấn đặt alias bảng provinces là "p". Dùng chung cho
     * CustomerDAO/ContractDAO khi xuất Excel theo tỉnh -- để các nơi hiển thị
     * và nơi sắp xếp không lệch nhau.
     */
    public static final String PROVINCE_SHORT_NAME_ORDER =
            "REPLACE(REPLACE(p.province_name, 'Thành phố ', ''), 'Tỉnh ', '')";

    public List<Province> findAllProvinces() {
        List<Province> cached = provincesCache;
        if (cached != null) {
            return cached;
        }
        List<Province> result = new ArrayList<>();
        String sql = "SELECT p.province_id, p.province_name FROM provinces p ORDER BY " + PROVINCE_SHORT_NAME_ORDER;
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
            LOG.error("Loi truy van danh sach tinh/thanh pho", ex);
            return result;
        }
        provincesCache = result;
        return result;
    }

    /** Lấy xã/phường của 1 tỉnh, phục vụ dropdown "Xã / Phường" nạp qua AJAX sau khi chọn tỉnh. */
    public List<District> findWardsByProvinceId(int provinceId) {
        List<District> cached = wardsByProvinceCache.get(provinceId);
        if (cached != null) {
            return cached;
        }
        List<District> result = loadWardsByProvinceId(provinceId);
        // Không cache kết quả rỗng: mọi tỉnh hợp lệ đều có xã/phường, nên rỗng
        // nghĩa là truy vấn lỗi -- để lần gọi sau thử lại thay vì "khóa cứng"
        // thành rỗng tới khi restart app.
        if (!result.isEmpty()) {
            wardsByProvinceCache.put(provinceId, result);
        }
        return result;
    }

    private List<District> loadWardsByProvinceId(int provinceId) {
        List<District> result = new ArrayList<>();
        String sql = "SELECT districts_id, districts_name, province_id FROM districts " +
                     "WHERE province_id = ? ORDER BY districts_name";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, provinceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    District d = new District();
                    d.setDistrictId(rs.getInt("districts_id"));
                    d.setDistrictName(rs.getString("districts_name"));
                    d.setProvinceId(rs.getInt("province_id"));
                    result.add(d);
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van xa/phuong theo tinh (provinceId={})", provinceId, ex);
        }
        return result;
    }
}
