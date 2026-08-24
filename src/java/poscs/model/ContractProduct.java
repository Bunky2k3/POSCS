package poscs.model;

/** 1 dòng hạng mục sản phẩm trong hợp đồng (contractproducts JOIN products). */
public class ContractProduct {
    private int contractProductId;
    private int contractId;
    private int productId;
    private String productCode;
    private String productName;
    private int quantity;
    private String unit;
    private String notes;

    public ContractProduct() {
    }

    public int getContractProductId() { return contractProductId; }
    public void setContractProductId(int contractProductId) { this.contractProductId = contractProductId; }

    public int getContractId() { return contractId; }
    public void setContractId(int contractId) { this.contractId = contractId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public String toString() {
        return "ContractProduct{" + "contractProductId=" + contractProductId + ", productName='" + productName + '\'' + '}';
    }
}
