package poscs.model;

public class Ward {
    private int wardId;
    private String wardName;

    // Thuộc tính lưu trữ ID khóa ngoại
    private int provinceId;

    // Thuộc tính Object để chứa dữ liệu join từ bảng provinces
    private Province province;

    // Constructor rỗng (Bắt buộc cho JavaBean)
    public Ward() {
    }

    // Constructor có tham số
    public Ward(int wardId, String wardName, int provinceId) {
        this.wardId = wardId;
        this.wardName = wardName;
        this.provinceId = provinceId;
    }

    // Các hàm Getters và Setters
    public int getWardId() { return wardId; }
    public void setWardId(int wardId) { this.wardId = wardId; }

    public String getWardName() { return wardName; }
    public void setWardName(String wardName) { this.wardName = wardName; }

    public int getProvinceId() { return provinceId; }
    public void setProvinceId(int provinceId) { this.provinceId = provinceId; }

    public Province getProvince() { return province; }
    public void setProvince(Province province) { this.province = province; }

    @Override
    public String toString() {
        return "Ward{" + "wardId=" + wardId + ", wardName='" + wardName + '\'' + '}';
    }
}
