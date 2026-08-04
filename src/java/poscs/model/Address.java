package poscs.model;

public class Address {
    private int addressId;
    private String streetAndLocalName;
    private int districtId; 

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
}