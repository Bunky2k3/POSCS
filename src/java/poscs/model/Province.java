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

    /**
     * Tên bỏ tiền tố "Tỉnh "/"Thành phố " (vd. "An Giang", "Hà Nội") -- dùng
     * cho các dropdown chọn tỉnh/thành. Lý do cần riêng: mọi provinceName
     * đều bắt đầu bằng "Tỉnh "/"Thành phố " nên gõ phím đầu để nhảy nhanh
     * trong thẻ select (tính năng có sẵn của trình duyệt) chỉ nhảy được
     * giữa vài chục lựa chọn cùng bắt đầu bằng "T" -- vô dụng. Bỏ tiền tố
     * thì gõ "H" mới thật sự nhảy tới "Hà Nội", "Hà Tĩnh"...
     */
    public String getShortName() {
        if (provinceName == null) {
            return null;
        }
        if (provinceName.startsWith("Thành phố ")) {
            return provinceName.substring("Thành phố ".length());
        }
        if (provinceName.startsWith("Tỉnh ")) {
            return provinceName.substring("Tỉnh ".length());
        }
        return provinceName;
    }

    @Override
    public String toString() {
        return "Province{" + "provinceId=" + provinceId + ", provinceName='" + provinceName + '\'' + '}';
    }
}
