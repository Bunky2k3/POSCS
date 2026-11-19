package poscs.model;

import java.sql.Timestamp;

/**
 * Thông tin chi nhánh (bảng {@code branch_info}, V39, UC-55): thông tin Giám
 * đốc chi nhánh (GĐCN), là nguồn người ký bên mình trên hợp đồng (BR-56).
 *
 * <p>Bảng chỉ có MỘT dòng (id = 1, gieo sẵn ở V39) nên model không mang id.
 * Mọi trường thông tin đều có thể null: dòng gieo sẵn chỉ có chức vụ, họ tên
 * để trống tới khi Admin khai.
 */
public class BranchInfo {

    private String directorName;
    private String directorPosition;
    private String phone;
    private String fax;

    /** Admin sửa lần cuối; null = chưa ai sửa từ lúc gieo. */
    private Integer updatedBy;
    private Timestamp updatedAt;

    public String getDirectorName() { return directorName; }
    public void setDirectorName(String directorName) { this.directorName = directorName; }

    public String getDirectorPosition() { return directorPosition; }
    public void setDirectorPosition(String directorPosition) { this.directorPosition = directorPosition; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getFax() { return fax; }
    public void setFax(String fax) { this.fax = fax; }

    public Integer getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
