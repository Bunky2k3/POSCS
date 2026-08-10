package poscs.model;

public class Address {
    private int addressId;
    private String streetAndLocalName;
    private int districtId;

    // Thuộc tính Object để chứa dữ liệu join từ bảng districts
    private District district;

    // Constructors
    public Address() {
    }

    public Address(int addressId, String streetAndLocalName, int districtId) {
        this.addressId = addressId;
        this.streetAndLocalName = streetAndLocalName;
        this.districtId = districtId;
    }

    // Getters and Setters
    public int getAddressId() { return addressId; }
    public void setAddressId(int addressId) { this.addressId = addressId; }

    public String getStreetAndLocalName() { return streetAndLocalName; }
    public void setStreetAndLocalName(String streetAndLocalName) { this.streetAndLocalName = streetAndLocalName; }

    public int getDistrictId() { return districtId; }
    public void setDistrictId(int districtId) { this.districtId = districtId; }

    public District getDistrict() { return district; }
    public void setDistrict(District district) { this.district = district; }

    // Hàm tiện ích: ghép địa chỉ đầy đủ nếu đã có dữ liệu join district/province
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (streetAndLocalName != null) {
            sb.append(streetAndLocalName);
        }
        if (district != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(district.getDistrictName());
            if (district.getProvince() != null) {
                sb.append(", ").append(district.getProvince().getProvinceName());
            }
        }
        return sb.toString();
    }
}