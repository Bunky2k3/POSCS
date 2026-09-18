package poscs.model;

import java.sql.Timestamp;

/**
 * Một liên kết giữa hợp đồng BÁN (đầu ra) và hợp đồng MUA (đầu vào) -- "bán
 * cái này thì phải mua vào những cái kia".
 *
 * <p>Hướng CỐ ĐỊNH trong CSDL: {@code sell_contract_id} luôn là hợp đồng bán,
 * {@code buy_contract_id} luôn là hợp đồng mua. Màn hình cho nối từ cả hai
 * phía nhưng vẫn ghi xuống đúng một chiều, nếu không thì cùng một quan hệ tồn
 * tại hai bản ghi ngược nhau.
 *
 * <p>KHÁC {@code Contract.parentContractId}: cột đó là quan hệ PHỤ LỤC (văn bản
 * sửa đổi của chính hợp đồng ấy), còn đây là hai hợp đồng độc lập với hai đối
 * tác khác nhau, chỉ dính nhau ở chỗ cái này sinh ra vì cái kia.
 */
public class ContractLink {

    private int linkId;
    private int sellContractId;
    private int buyContractId;
    private String relationType;
    private String note;
    private int createdBy;
    private Timestamp createdAt;

    /**
     * Hợp đồng ở ĐẦU KIA của liên kết, nhìn từ hợp đồng đang mở: mở một hợp
     * đồng bán thì đây là đơn mua, mở đơn mua thì đây là hợp đồng bán.
     *
     * <p>Chỉ mang những trường màn hình cần (mã, tiêu đề, chiều, tiến độ, giá
     * trị, đối tác) -- không phải bản đầy đủ.
     */
    private Contract other;

    /** Họ tên người nối, join sẵn để màn hình khỏi tra thêm. */
    private String createdByName;

    /**
     * Số hợp đồng bán KHÁC cũng đang dùng chung đơn mua này (0 nếu không).
     *
     * <p>Sinh ra vì phép đối chiếu tiền: một đơn mua gom được tính TRỌN VẸN vào
     * mỗi hợp đồng bán mà nó phục vụ -- hệ thống không biết chia tỉ lệ thế nào,
     * và đoán hộ thì ra con số không đối chiếu được với chứng từ. Nên màn hình
     * phải nói rõ đơn nào là đơn dùng chung, nếu không "chênh lệch thô" âm ở
     * đó bị đọc thành lỗ.
     */
    private int sharedCount;

    public int getLinkId() { return linkId; }
    public void setLinkId(int linkId) { this.linkId = linkId; }

    public int getSellContractId() { return sellContractId; }
    public void setSellContractId(int sellContractId) { this.sellContractId = sellContractId; }

    public int getBuyContractId() { return buyContractId; }
    public void setBuyContractId(int buyContractId) { this.buyContractId = buyContractId; }

    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Contract getOther() { return other; }
    public void setOther(Contract other) { this.other = other; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public int getSharedCount() { return sharedCount; }
    public void setSharedCount(int sharedCount) { this.sharedCount = sharedCount; }

    /** true nếu đơn mua này còn phục vụ hợp đồng bán khác. */
    public boolean isShared() { return sharedCount > 0; }

    @Override
    public String toString() {
        return "ContractLink{" + sellContractId + " -> " + buyContractId + '}';
    }
}
