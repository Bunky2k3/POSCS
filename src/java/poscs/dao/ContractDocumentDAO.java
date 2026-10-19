package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poscs.model.ContractDocument;
import poscs.model.ContractHistory;
import poscs.model.User;

/**
 * Data Access Object chuyên biệt cho tài liệu đính kèm của hợp đồng (bảng {@code contract_documents} - [UC-52]).
 * Tách từ {@link ContractDAO} (Task 4.5): chỉ lưu URL, không upload file; huỷ mềm kèm lý do; ghi nhật ký trong transaction (NFR-19).
 */
public class ContractDocumentDAO {

    private static final Logger LOG = LoggerFactory.getLogger(ContractDocumentDAO.class);

    public static final int DOC_OK = 1;
    public static final int DOC_NO_CONTRACT = -1;
    public static final int DOC_FAILED = -99;

    /** Giới hạn độ dài các cột theo db/schema.sql (dòng 4608-4619). */
    public static final int DOC_TYPE_MAX_LENGTH = 50;
    public static final int FILE_URL_MAX_LENGTH = 500;
    public static final int TITLE_MAX_LENGTH = 255;
    public static final int NOTE_MAX_LENGTH = 255;
    public static final int DELETE_REASON_MAX_LENGTH = 255;

    private final ContractHistoryDAO historyDAO;

    public ContractDocumentDAO() {
        this(new ContractHistoryDAO());
    }

    public ContractDocumentDAO(ContractHistoryDAO historyDAO) {
        this.historyDAO = historyDAO != null ? historyDAO : new ContractHistoryDAO();
    }

    public ContractHistoryDAO getHistoryDAO() {
        return historyDAO;
    }

    public static String truncate(String text, int max) {
        if (text == null || max <= 0) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    public static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static String fullName(ResultSet rs, String lastCol, String midCol, String firstCol) throws SQLException {
        String last = rs.getString(lastCol);
        if (last == null) {
            return null;
        }
        User u = new User();
        u.setLastName(last);
        u.setMiddleName(rs.getString(midCol));
        u.setFirstName(rs.getString(firstCol));
        return u.getFullName();
    }

    /**
     * Treo một giấy tờ vào hợp đồng (tự mở kết nối và quản lý transaction).
     */
    public int addDocument(ContractDocument doc, int actorId) {
        if (doc == null || doc.getContractId() <= 0 || actorId <= 0) {
            return DOC_FAILED;
        }
        String docType = trimToNull(doc.getDocType());
        String fileUrl = trimToNull(doc.getFileUrl());
        if (docType == null || docType.length() > DOC_TYPE_MAX_LENGTH
                || fileUrl == null || fileUrl.length() > FILE_URL_MAX_LENGTH) {
            return DOC_FAILED;
        }
        String title = truncate(trimToNull(doc.getTitle()), TITLE_MAX_LENGTH);
        String note = truncate(trimToNull(doc.getNote()), NOTE_MAX_LENGTH);

        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                if (!lockContract(conn, doc.getContractId())) {
                    return DOC_NO_CONTRACT;
                }
                if (!insertDocumentRow(conn, doc.getContractId(), docType, title, fileUrl, note, actorId)) {
                    return DOC_FAILED;
                }
                historyDAO.insertHistory(conn, doc.getContractId(), ContractHistory.EVENT_DOCUMENT,
                        "Thêm tài liệu: " + docType + (title == null ? "" : " — " + title),
                        actorId, note);
                conn.commit();
                committed = true;
                return DOC_OK;
            } finally {
                finishTransaction(conn, committed, "them tai lieu hop dong", doc.getContractId());
            }
        } catch (SQLException ex) {
            LOG.error("Loi them tai lieu hop dong (contractId={})", doc.getContractId(), ex);
            return DOC_FAILED;
        }
    }

    /**
     * Huỷ một giấy tờ: đánh dấu xoá MỀM, bắt buộc có lý do (tự mở kết nối và quản lý transaction).
     */
    public boolean voidDocument(int documentId, int actorId, String reason) {
        String cleanReason = trimToNull(reason);
        if (cleanReason == null || documentId <= 0 || actorId <= 0) {
            return false;
        }
        String safeReason = truncate(cleanReason, DELETE_REASON_MAX_LENGTH);

        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                DocumentTarget target = lockDocumentForVoid(conn, documentId);
                if (target == null) {
                    return false;
                }
                if (!updateDocumentVoidStatus(conn, documentId, actorId, safeReason)) {
                    return false;
                }
                historyDAO.insertHistory(conn, target.contractId, ContractHistory.EVENT_DOCUMENT,
                        "Huỷ tài liệu: " + target.docType
                                + (target.title == null || target.title.trim().isEmpty() ? "" : " — " + target.title),
                        actorId, cleanReason);
                conn.commit();
                committed = true;
                return true;
            } finally {
                finishTransaction(conn, committed, "huy tai lieu hop dong", documentId);
            }
        } catch (SQLException ex) {
            LOG.error("Loi huy tai lieu hop dong (documentId={})", documentId, ex);
            return false;
        }
    }

    /**
     * Danh sách giấy tờ CÒN HIỆU LỰC (chưa bị huỷ mềm) của một hợp đồng, cũ trước mới sau.
     */
    public List<ContractDocument> findDocumentsOf(int contractId) {
        List<ContractDocument> result = new ArrayList<>();
        if (contractId <= 0) {
            return result;
        }
        String sql = "SELECT d.document_id, d.contract_id, d.doc_type, d.title, d.file_url, d.note, "
                + "       d.uploaded_by, d.uploaded_at, u.last_name, u.middle_name, u.first_name "
                + "FROM contract_documents d "
                + "LEFT JOIN users u ON u.user_id = d.uploaded_by "
                + "WHERE d.contract_id = ? AND d.is_deleted = 0 "
                + "ORDER BY d.uploaded_at, d.document_id";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToActiveDocument(rs));
                }
            }
        } catch (SQLException ex) {
            LOG.error("Loi truy van tai lieu hop dong (contractId={})", contractId, ex);
        }
        return result;
    }

    private boolean lockContract(Connection conn, int contractId) throws SQLException {
        String sql = "SELECT contract_id FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean insertDocumentRow(Connection conn, int contractId, String docType,
            String title, String fileUrl, String note, int actorId) throws SQLException {
        String sql = "INSERT INTO contract_documents "
                + "(contract_id, doc_type, title, file_url, note, uploaded_by) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            ps.setString(2, docType);
            ps.setString(3, title);
            ps.setString(4, fileUrl);
            ps.setString(5, note);
            ps.setInt(6, actorId);
            return ps.executeUpdate() > 0;
        }
    }

    private DocumentTarget lockDocumentForVoid(Connection conn, int documentId) throws SQLException {
        String sql = "SELECT contract_id, doc_type, title FROM contract_documents "
                + "WHERE document_id = ? AND is_deleted = 0 FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new DocumentTarget(
                        rs.getInt("contract_id"), rs.getString("doc_type"), rs.getString("title")) : null;
            }
        }
    }

    private boolean updateDocumentVoidStatus(Connection conn, int documentId, int actorId, String reason) throws SQLException {
        String sql = "UPDATE contract_documents SET is_deleted = 1, deleted_by = ?, "
                + "deleted_at = CURRENT_TIMESTAMP, delete_reason = ? "
                + "WHERE document_id = ? AND is_deleted = 0";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, actorId);
            ps.setString(2, reason);
            ps.setInt(3, documentId);
            return ps.executeUpdate() > 0;
        }
    }

    private void finishTransaction(Connection conn, boolean committed, String what, Object id) {
        if (!committed) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                LOG.error("Loi rollback khi {} (id={})", what, id, rollbackEx);
            }
        }
        try {
            conn.setAutoCommit(true);
        } catch (SQLException autoCommitEx) {
            LOG.error("Loi reset autocommit sau khi {} (id={})", what, id, autoCommitEx);
        }
    }

    private ContractDocument mapResultSetToActiveDocument(ResultSet rs) throws SQLException {
        ContractDocument d = new ContractDocument();
        d.setDocumentId(rs.getInt("document_id"));
        d.setContractId(rs.getInt("contract_id"));
        d.setDocType(rs.getString("doc_type"));
        d.setTitle(rs.getString("title"));
        d.setFileUrl(rs.getString("file_url"));
        d.setNote(rs.getString("note"));
        d.setUploadedBy(rs.getInt("uploaded_by"));
        d.setUploadedAt(rs.getTimestamp("uploaded_at"));
        d.setUploadedByName(fullName(rs, "last_name", "middle_name", "first_name"));
        return d;
    }

    private static class DocumentTarget {
        final int contractId;
        final String docType;
        final String title;

        DocumentTarget(int contractId, String docType, String title) {
            this.contractId = contractId;
            this.docType = docType;
            this.title = title;
        }
    }
}
