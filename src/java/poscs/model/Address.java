package poscs.model;

public class Address {
    private int addressId;
    private String streetAndLocalName;
    private int wardId;

    // Thuộc tính Object để chứa dữ liệu join từ bảng wards
    private Ward ward;

    // Constructors
    public Address() {
    }

    public Address(int addressId, String streetAndLocalName, int wardId) {
        this.addressId = addressId;
        this.streetAndLocalName = streetAndLocalName;
        this.wardId = wardId;
    }

    // Getters and Setters
    public int getAddressId() { return addressId; }
    public void setAddressId(int addressId) { this.addressId = addressId; }

    public String getStreetAndLocalName() { return streetAndLocalName; }
    public void setStreetAndLocalName(String streetAndLocalName) { this.streetAndLocalName = streetAndLocalName; }

    public int getWardId() { return wardId; }
    public void setWardId(int wardId) { this.wardId = wardId; }

    public Ward getWard() { return ward; }
    public void setWard(Ward ward) { this.ward = ward; }

    // Hàm tiện ích: ghép địa chỉ đầy đủ nếu đã có dữ liệu join ward/province
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (streetAndLocalName != null) {
            sb.append(streetAndLocalName);
        }
        if (ward != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(ward.getWardName());
            if (ward.getProvince() != null) {
                sb.append(", ").append(ward.getProvince().getProvinceName());
            }
        }
        return sb.toString();
    }
}
