package poscs.model;

public class EnterpriseContact {
    private int contactId;
    private int enterpriseId;
    private String contactLastName;
    private String contactMiddleName;
    private String contactFirstName;
    private String contactPhone;
    private String contactEmail;
    private String position;

    // Constructor rỗng (Bắt buộc cho JavaBean)
    public EnterpriseContact() {
    }

    // Constructor có tham số
    public EnterpriseContact(int contactId, int enterpriseId, String contactLastName,
                              String contactMiddleName, String contactFirstName,
                              String contactPhone, String contactEmail, String position) {
        this.contactId = contactId;
        this.enterpriseId = enterpriseId;
        this.contactLastName = contactLastName;
        this.contactMiddleName = contactMiddleName;
        this.contactFirstName = contactFirstName;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        this.position = position;
    }

    // Các hàm Getters và Setters
    public int getContactId() { return contactId; }
    public void setContactId(int contactId) { this.contactId = contactId; }

    public int getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(int enterpriseId) { this.enterpriseId = enterpriseId; }

    public String getContactLastName() { return contactLastName; }
    public void setContactLastName(String contactLastName) { this.contactLastName = contactLastName; }

    public String getContactMiddleName() { return contactMiddleName; }
    public void setContactMiddleName(String contactMiddleName) { this.contactMiddleName = contactMiddleName; }

    public String getContactFirstName() { return contactFirstName; }
    public void setContactFirstName(String contactFirstName) { this.contactFirstName = contactFirstName; }

    // Hàm tiện ích lấy họ và tên đầy đủ
    public String getFullName() {
        if (contactMiddleName != null && !contactMiddleName.trim().isEmpty()) {
            return contactLastName + " " + contactMiddleName + " " + contactFirstName;
        }
        return contactLastName + " " + contactFirstName;
    }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    @Override
    public String toString() {
        return "EnterpriseContact{" + "contactId=" + contactId + ", fullName='" + getFullName() + '\'' + '}';
    }
}
