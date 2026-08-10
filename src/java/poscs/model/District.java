package poscs.model;

public class District {
    private int districtId;
    private String districtName;

    // Thuộc tính lưu trữ ID khóa ngoại
    private int provinceId;

    // Thuộc tính Object để chứa dữ liệu join từ bảng provinces
    private Province province;

    // Constructor rỗng (Bắt buộc cho JavaBean)
    public District() {
    }

    // Constructor có tham số
    public District(int districtId, String districtName, int provinceId) {
        this.districtId = districtId;
        this.districtName = districtName;
        this.provinceId = provinceId;
    }

    // Các hàm Getters và Setters
    public int getDistrictId() { return districtId; }
    public void setDistrictId(int districtId) { this.districtId = districtId; }

    public String getDistrictName() { return districtName; }
    public void setDistrictName(String districtName) { this.districtName = districtName; }

    public int getProvinceId() { return provinceId; }
    public void setProvinceId(int provinceId) { this.provinceId = provinceId; }

    public Province getProvince() { return province; }
    public void setProvince(Province province) { this.province = province; }

    @Override
    public String toString() {
        return "District{" + "districtId=" + districtId + ", districtName='" + districtName + '\'' + '}';
    }
}
