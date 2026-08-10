package poscs.model;

public class Province {
    private int provinceId;
    private String provinceName;

    // Constructor rỗng (Bắt buộc cho JavaBean)
    public Province() {
    }

    // Constructor có tham số
    public Province(int provinceId, String provinceName) {
        this.provinceId = provinceId;
        this.provinceName = provinceName;
    }

    // Các hàm Getters và Setters
    public int getProvinceId() { return provinceId; }
    public void setProvinceId(int provinceId) { this.provinceId = provinceId; }

    public String getProvinceName() { return provinceName; }
    public void setProvinceName(String provinceName) { this.provinceName = provinceName; }

    @Override
    public String toString() {
        return "Province{" + "provinceId=" + provinceId + ", provinceName='" + provinceName + '\'' + '}';
    }
}
