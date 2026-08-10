package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import poscs.model.TechnicalRequest;

public class TechnicalSupportTicketDAO {

    /**
     * Lấy danh sách phiếu hỗ trợ kỹ thuật của 1 khách hàng, phục vụ tab
     * "Phiếu hỗ trợ kỹ thuật" ở viewcustomerdetail.jsp. CRUD ticket đầy đủ
     * thuộc phạm vi khác, chưa code ở đây.
     */
    public List<TechnicalRequest> findByEnterpriseId(int enterpriseId) {
        List<TechnicalRequest> result = new ArrayList<>();
        String sql = "SELECT ticket_id, ticket_code, enterprise_id, contract_id, ticket_type, " +
                     "priority, reception_channel, assigned_technician_id, created_by, created_date, " +
                     "description, is_warranty, status " +
                     "FROM technicalrequests WHERE enterprise_id = ? AND is_deleted = 0 ORDER BY created_date DESC";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, enterpriseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TechnicalRequest t = new TechnicalRequest();
                    t.setTicketId(rs.getInt("ticket_id"));
                    t.setTicketCode(rs.getString("ticket_code"));
                    t.setEnterpriseId(rs.getInt("enterprise_id"));
                    int contractId = rs.getInt("contract_id");
                    t.setContractId(rs.wasNull() ? null : contractId);
                    t.setTicketType(rs.getString("ticket_type"));
                    t.setPriority(rs.getString("priority"));
                    t.setReceptionChannel(rs.getString("reception_channel"));
                    t.setAssignedTechnicianId(rs.getInt("assigned_technician_id"));
                    t.setCreatedBy(rs.getInt("created_by"));
                    t.setCreatedDate(rs.getDate("created_date"));
                    t.setDescription(rs.getString("description"));
                    t.setWarranty(rs.getBoolean("is_warranty"));
                    t.setStatus(rs.getString("status"));
                    result.add(t);
                }
            }
        } catch (SQLException ex) {
            System.err.println("--- LOI TRUY VAN TICKET THEO KHACH HANG ---");
            ex.printStackTrace();
        }
        return result;
    }
}
