package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import poscs.model.Contract;

public class ContractDAO {

    /**
     * Lấy danh sách hợp đồng của 1 khách hàng, phục vụ tab "Hợp đồng" ở
     * viewcustomerdetail.jsp. CRUD hợp đồng đầy đủ thuộc phạm vi khác,
     * chưa code ở đây.
     */
    public List<Contract> findByEnterpriseId(int enterpriseId) {
        List<Contract> result = new ArrayList<>();
        String sql = "SELECT contract_id, contract_code, title, contract_type, signing_date, " +
                     "effective_date, end_date, enterprise_id, owner_id, attachment_url, status " +
                     "FROM contracts WHERE enterprise_id = ? AND is_deleted = 0 ORDER BY signing_date DESC";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enterpriseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Contract c = new Contract();
                    c.setContractId(rs.getInt("contract_id"));
                    c.setContractCode(rs.getString("contract_code"));
                    c.setTitle(rs.getString("title"));
                    c.setContractType(rs.getString("contract_type"));
                    c.setSigningDate(rs.getDate("signing_date"));
                    c.setEffectiveDate(rs.getDate("effective_date"));
                    c.setEndDate(rs.getDate("end_date"));
                    c.setEnterpriseId(rs.getInt("enterprise_id"));
                    c.setOwnerId(rs.getInt("owner_id"));
                    c.setAttachmentUrl(rs.getString("attachment_url"));
                    c.setStatus(rs.getString("status"));
                    result.add(c);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN HOP DONG THEO KHACH HANG ---");
            ex.printStackTrace();
        }
        return result;
    }
}
