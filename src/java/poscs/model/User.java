package poscs.model;

import java.sql.Date;
import java.sql.Timestamp;

public class User {
    private int userId;
    private String username;
    private String email;
    private String passwordHash;
    
    // Thuộc tính lưu trữ ID khóa ngoại
    private int roleId;
    private int departmentId;
    private Integer addressId; // Dùng Integer thay vì int vì DB cho phép NULL

    // Thuộc tính Object để chứa dữ liệu join từ các bảng khác
    private Role role;
    private Department department;
    private Address address;

    private String lastName;
    private String middleName;
    private String firstName;
    private String gender;
    private Date dateOfBirth; // Dùng java.sql.Date cho trường DATE trong MySQL
    private String citizenId;
    private String phone;
    private String personalEmail;
    private String avatarUrl;
    private Date hireDate;
    
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private boolean isDeleted; // Ánh xạ với tinyint(1)

    // Constructor rỗng (Bắt buộc cho JavaBean)
    public User() {
    }

    // Constructor có tham số (Có thể tạo thêm constructor với ít tham số hơn tùy nhu cầu)
    public User(int userId, String username, String email, String passwordHash, int roleId,
                String lastName, String middleName, String firstName, String gender,
                Date dateOfBirth, String citizenId, String phone, String personalEmail,
                Integer addressId, String avatarUrl, int departmentId, Date hireDate,
                Timestamp createdAt, Timestamp updatedAt, boolean isDeleted) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roleId = roleId;
        this.lastName = lastName;
        this.middleName = middleName;
        this.firstName = firstName;
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        this.citizenId = citizenId;
        this.phone = phone;
        this.personalEmail = personalEmail;
        this.addressId = addressId;
        this.avatarUrl = avatarUrl;
        this.departmentId = departmentId;
        this.hireDate = hireDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isDeleted = isDeleted;
    }

    // Các hàm Getters và Setters
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public int getRoleId() { return roleId; }
    public void setRoleId(int roleId) { this.roleId = roleId; }

    public Integer getAddressId() { return addressId; }
    public void setAddressId(Integer addressId) { this.addressId = addressId; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    // Hàm tiện ích lấy họ và tên đầy đủ
    public String getFullName() {
        if (middleName != null && !middleName.trim().isEmpty()) {
            return lastName + " " + middleName + " " + firstName;
        }
        return lastName + " " + firstName;
    }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Date getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(Date dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getCitizenId() { return citizenId; }
    public void setCitizenId(String citizenId) { this.citizenId = citizenId; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPersonalEmail() { return personalEmail; }
    public void setPersonalEmail(String personalEmail) { this.personalEmail = personalEmail; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public int getDepartmentId() { return departmentId; }
    public void setDepartmentId(int departmentId) { this.departmentId = departmentId; }

    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }

    public Date getHireDate() { return hireDate; }
    public void setHireDate(Date hireDate) { this.hireDate = hireDate; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean isDeleted) { this.isDeleted = isDeleted; }
}