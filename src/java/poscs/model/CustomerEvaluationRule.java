package poscs.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class CustomerEvaluationRule {
    private int ruleId;
    private String ruleCode;
    private BigDecimal ruleValue;
    private String description;
    private Timestamp updatedAt;

    // Constructor rỗng (Bắt buộc cho JavaBean)
    public CustomerEvaluationRule() {
    }

    // Constructor có tham số
    public CustomerEvaluationRule(int ruleId, String ruleCode, BigDecimal ruleValue,
                                   String description, Timestamp updatedAt) {
        this.ruleId = ruleId;
        this.ruleCode = ruleCode;
        this.ruleValue = ruleValue;
        this.description = description;
        this.updatedAt = updatedAt;
    }

    // Các hàm Getters và Setters
    public int getRuleId() { return ruleId; }
    public void setRuleId(int ruleId) { this.ruleId = ruleId; }

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }

    public BigDecimal getRuleValue() { return ruleValue; }
    public void setRuleValue(BigDecimal ruleValue) { this.ruleValue = ruleValue; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "CustomerEvaluationRule{" + "ruleCode='" + ruleCode + '\'' + ", ruleValue=" + ruleValue + '}';
    }
}
