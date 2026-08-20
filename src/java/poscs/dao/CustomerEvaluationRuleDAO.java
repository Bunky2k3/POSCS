package poscs.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * DAO cho bảng customer_evaluation_rules -- các ngưỡng dùng để chấm điểm/
 * xếp hạng quan hệ khách hàng (xem poscs.common.CustomerEvaluator và
 * db/schema.sql để biết ý nghĩa từng rule_code).
 */
public class CustomerEvaluationRuleDAO {

    /** Toàn bộ rule hiện có, key = rule_code (VD: "LOW_PURCHASE_THRESHOLD"). */
    public Map<String, BigDecimal> findAllAsMap() {
        Map<String, BigDecimal> rules = new HashMap<>();
        String sql = "SELECT rule_code, rule_value FROM customer_evaluation_rules";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rules.put(rs.getString("rule_code"), rs.getBigDecimal("rule_value"));
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI DOC QUY TAC CHAM DIEM KHACH HANG ---");
            ex.printStackTrace();
        }
        return rules;
    }
}
