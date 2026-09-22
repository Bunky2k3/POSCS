package poscs.model;

/**
 * Một tỉnh/thành kèm người đang cầm nó (nếu có) -- dùng riêng cho form quản
 * lý nhân viên (thêm/sửa), nơi Admin cần thấy NGAY trên danh sách chọn ai đã
 * cầm tỉnh nào, thay vì tỉnh đã có người cầm biến mất im lặng khỏi ô chọn
 * (như {@code EmployeeDAO.findSelectableProvinces} làm trước đây). {@code
 * holderUserId} null nghĩa là chưa ai cầm.
 */
public class ProvinceAssignment extends Province {

    private Integer holderUserId;
    private String holderName;

    public ProvinceAssignment() {
    }

    public ProvinceAssignment(int provinceId, String provinceName, Integer holderUserId, String holderName) {
        super(provinceId, provinceName);
        this.holderUserId = holderUserId;
        this.holderName = holderName;
    }

    public Integer getHolderUserId() { return holderUserId; }
    public void setHolderUserId(Integer holderUserId) { this.holderUserId = holderUserId; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
}
