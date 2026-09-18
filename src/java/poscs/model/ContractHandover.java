package poscs.model;

import java.sql.Timestamp;

/**
 * Một lượt bàn giao hợp đồng cho một phòng: Kinh doanh soạn xong thì chuyển
 * xuống Kế toán và Dự án chờ làm việc tiếp theo.
 *
 * <p>Giao cho PHÒNG chứ không cho người -- hai phòng đó chưa có nhân sự nào
 * trong hệ thống, và ai trong phòng xác nhận cũng được.
 *
 * <p>{@code doneAt} null nghĩa là phòng đó ĐANG CÒN GIỮ. Hiệu giữa nó và
 * {@code handedAt} chính là con số giám đốc hỏi: chờ bao lâu rồi.
 */
public class ContractHandover {

    private int handoverId;
    private int contractId;
    private int departmentId;
    private String departmentName;
    private Timestamp handedAt;
    private int handedBy;
    private String handedByName;
    private Timestamp doneAt;
    private Integer doneBy;
    private String doneByName;
    private String handoverNote;
    private String doneNote;

    /** Mã và tiêu đề hợp đồng, join sẵn cho màn hình theo dõi của giám đốc. */
    private String contractCode;
    private String contractTitle;
    private String ownerName;

    public int getHandoverId() { return handoverId; }
    public void setHandoverId(int handoverId) { this.handoverId = handoverId; }

    public int getContractId() { return contractId; }
    public void setContractId(int contractId) { this.contractId = contractId; }

    public int getDepartmentId() { return departmentId; }
    public void setDepartmentId(int departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public Timestamp getHandedAt() { return handedAt; }
    public void setHandedAt(Timestamp handedAt) { this.handedAt = handedAt; }

    public int getHandedBy() { return handedBy; }
    public void setHandedBy(int handedBy) { this.handedBy = handedBy; }

    public String getHandedByName() { return handedByName; }
    public void setHandedByName(String handedByName) { this.handedByName = handedByName; }

    public Timestamp getDoneAt() { return doneAt; }
    public void setDoneAt(Timestamp doneAt) { this.doneAt = doneAt; }

    public Integer getDoneBy() { return doneBy; }
    public void setDoneBy(Integer doneBy) { this.doneBy = doneBy; }

    public String getDoneByName() { return doneByName; }
    public void setDoneByName(String doneByName) { this.doneByName = doneByName; }

    public String getHandoverNote() { return handoverNote; }
    public void setHandoverNote(String handoverNote) { this.handoverNote = handoverNote; }

    public String getDoneNote() { return doneNote; }
    public void setDoneNote(String doneNote) { this.doneNote = doneNote; }

    public String getContractCode() { return contractCode; }
    public void setContractCode(String contractCode) { this.contractCode = contractCode; }

    public String getContractTitle() { return contractTitle; }
    public void setContractTitle(String contractTitle) { this.contractTitle = contractTitle; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    /** true nếu phòng này còn đang giữ hợp đồng. */
    public boolean isPending() { return doneAt == null; }

    /**
     * Số ngày kể từ lúc nhận: đang chờ thì đếm tới hôm nay, xong rồi thì đếm
     * tới lúc xong.
     *
     * <p>Tính ở model chứ không ở SQL để màn hình nào cũng ra cùng một con số;
     * và tính theo NGÀY chứ không theo giờ vì đó là đơn vị người ta nói với
     * nhau khi hỏi "hợp đồng nằm đấy mấy hôm rồi".
     */
    public long getDaysWaiting() {
        if (handedAt == null) {
            return 0;
        }
        long end = doneAt != null ? doneAt.getTime() : System.currentTimeMillis();
        return Math.max(0, (end - handedAt.getTime()) / (24L * 60 * 60 * 1000));
    }

    @Override
    public String toString() {
        return "ContractHandover{contract=" + contractId + ", dept=" + departmentName
                + (isPending() ? ", đang chờ}" : ", xong}");
    }
}
