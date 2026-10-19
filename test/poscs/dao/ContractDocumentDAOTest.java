package poscs.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import poscs.model.ContractDocument;
import poscs.model.ContractHistory;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static poscs.dao.JdbcStub.*;

/**
 * Unit Test cho {@link ContractDocumentDAO} (Task 4.5 - UC-52).
 *
 * Kiểm thử toàn diện:
 * - Khởi tạo và Dependency Injection với {@link ContractHistoryDAO}.
 * - Tiện ích trimToNull và fullName.
 * - Thêm tài liệu (addDocument):
 *   + Kịch bản thành công có tiêu đề, không có tiêu đề, ghi nhật ký EVENT_DOCUMENT và commit.
 *   + Kịch bản từ chối: doc null, contractId <= 0, actorId <= 0, docType null/rỗng/blank, fileUrl null/rỗng/blank.
 *   + Hợp đồng không tồn tại hoặc đã huỷ bản ghi (lockContract thất bại) trả về DOC_NO_CONTRACT.
 *   + Lỗi CSDL khi insert tài liệu hoặc insert nhật ký: rollback và trả về DOC_FAILED.
 * - Huỷ mềm tài liệu (voidDocument):
 *   + Kịch bản thành công: lý do hợp lệ, ghi nhận deleted_by, deleted_at, delete_reason, nhật ký EVENT_DOCUMENT và commit.
 *   + Kịch bản từ chối: reason null, rỗng, toàn khoảng trắng; documentId <= 0; actorId <= 0.
 *   + Không tìm thấy tài liệu hoặc tài liệu đã bị huỷ mềm trước đó: trả về false.
 *   + Lý do vượt quá 255 ký tự: cắt gọn an toàn cho cột DB delete_reason.
 *   + Lỗi CSDL: rollback và trả về false.
 * - Tra cứu tài liệu còn hiệu lực (findDocumentsOf):
 *   + contractId <= 0 trả về danh sách rỗng không query.
 *   + Truy vấn đúng điều kiện is_deleted = 0, ORDER BY uploaded_at, document_id; ánh xạ đầy đủ thông tin.
 *   + Lỗi CSDL trả về danh sách rỗng an toàn.
 * - Tra cứu chi tiết tài liệu theo ID (findDocumentById):
 *   + documentId <= 0 trả về null.
 *   + Ánh xạ đầy đủ cả tài liệu đang sống và tài liệu đã huỷ mềm (kèm tên người huỷ).
 *   + Lỗi CSDL trả về null an toàn.
 * - Đếm tài liệu còn sống (countActiveDocuments).
 */
public class ContractDocumentDAOTest {

    private final ContractDocumentDAO dao = new ContractDocumentDAO();

    private static final int CONTRACT_ID = 100;
    private static final int DOCUMENT_ID = 50;
    private static final int ACTOR_ID = 15;

    // ------------------------------------------------------------------
    // 1. Khởi tạo & Tiện ích
    // ------------------------------------------------------------------

    @Test
    public void constructorsAndGetters() {
        ContractDocumentDAO defaultDao = new ContractDocumentDAO();
        assertNotNull(defaultDao.getHistoryDAO());

        ContractHistoryDAO customHistory = new ContractHistoryDAO();
        ContractDocumentDAO customDao = new ContractDocumentDAO(customHistory);
        assertSame(customHistory, customDao.getHistoryDAO());

        ContractDocumentDAO nullHistoryDao = new ContractDocumentDAO(null);
        assertNotNull(nullHistoryDao.getHistoryDAO());
    }

    @Test
    public void trimToNull_variousInputs() {
        assertNull(ContractDocumentDAO.trimToNull(null));
        assertNull(ContractDocumentDAO.trimToNull(""));
        assertNull(ContractDocumentDAO.trimToNull("   "));
        assertNull(ContractDocumentDAO.trimToNull("\t\n  "));
        assertEquals("abc", ContractDocumentDAO.trimToNull("abc"));
        assertEquals("abc", ContractDocumentDAO.trimToNull("  abc  "));
    }

    @Test
    public void truncate_variousInputs() {
        assertNull(ContractDocumentDAO.truncate(null, 50));
        assertNull(ContractDocumentDAO.truncate("abc", 0));
        assertNull(ContractDocumentDAO.truncate("abc", -1));
        assertEquals("abc", ContractDocumentDAO.truncate("abc", 5));
        assertEquals("abc", ContractDocumentDAO.truncate("abc", 3));
        assertEquals("ab", ContractDocumentDAO.truncate("abc", 2));
    }

    @Test
    public void fullName_variousInputs() throws Exception {
        ResultSet rsNull = mock(ResultSet.class);
        when(rsNull.getString("last_name")).thenReturn(null);
        assertNull(ContractDocumentDAO.fullName(rsNull, "last_name", "middle_name", "first_name"));

        ResultSet rsFull = mock(ResultSet.class);
        when(rsFull.getString("last_name")).thenReturn("Nguyễn");
        when(rsFull.getString("middle_name")).thenReturn("Văn");
        when(rsFull.getString("first_name")).thenReturn("An");
        assertEquals("Nguyễn Văn An", ContractDocumentDAO.fullName(rsFull, "last_name", "middle_name", "first_name"));

        ResultSet rsNoMiddle = mock(ResultSet.class);
        when(rsNoMiddle.getString("last_name")).thenReturn("Trần");
        when(rsNoMiddle.getString("middle_name")).thenReturn(null);
        when(rsNoMiddle.getString("first_name")).thenReturn("Bình");
        assertEquals("Trần Bình", ContractDocumentDAO.fullName(rsNoMiddle, "last_name", "middle_name", "first_name"));

        ResultSet rsBlankMiddle = mock(ResultSet.class);
        when(rsBlankMiddle.getString("last_name")).thenReturn("Lê");
        when(rsBlankMiddle.getString("middle_name")).thenReturn("   ");
        when(rsBlankMiddle.getString("first_name")).thenReturn("Châu");
        assertEquals("Lê Châu", ContractDocumentDAO.fullName(rsBlankMiddle, "last_name", "middle_name", "first_name"));
    }

    // ------------------------------------------------------------------
    // 2. addDocument (UC-52)
    // ------------------------------------------------------------------

    @Test
    public void addDocument_validationFailures_returnsFailedCode() {
        // null doc
        assertEquals(ContractDocumentDAO.DOC_FAILED, dao.addDocument(null, ACTOR_ID));

        // invalid contractId
        ContractDocument doc = new ContractDocument();
        doc.setContractId(0);
        doc.setDocType("Hợp đồng đã ký");
        doc.setFileUrl("https://drive.google.com/test");
        assertEquals(ContractDocumentDAO.DOC_FAILED, dao.addDocument(doc, ACTOR_ID));

        doc.setContractId(-10);
        assertEquals(ContractDocumentDAO.DOC_FAILED, dao.addDocument(doc, ACTOR_ID));

        // invalid actorId
        doc.setContractId(CONTRACT_ID);
        assertEquals(ContractDocumentDAO.DOC_FAILED, dao.addDocument(doc, 0));
        assertEquals(ContractDocumentDAO.DOC_FAILED, dao.addDocument(doc, -5));

        // invalid docType (null, empty, whitespace)
        doc.setDocType(null);
        assertEquals(ContractDocumentDAO.DOC_FAILED, dao.addDocument(doc, ACTOR_ID));
        doc.setDocType("");
        assertEquals(ContractDocumentDAO.DOC_FAILED, dao.addDocument(doc, ACTOR_ID));
        doc.setDocType("    ");
        assertEquals(ContractDocumentDAO.DOC_FAILED, dao.addDocument(doc, ACTOR_ID));

        // invalid fileUrl (null, empty, whitespace)
        doc.setDocType("Hợp đồng đã ký");
        doc.setFileUrl(null);
        assertEquals(ContractDocumentDAO.DOC_FAILED, dao.addDocument(doc, ACTOR_ID));
        doc.setFileUrl("");
        assertEquals(ContractDocumentDAO.DOC_FAILED, dao.addDocument(doc, ACTOR_ID));
        doc.setFileUrl("    ");
        assertEquals(ContractDocumentDAO.DOC_FAILED, dao.addDocument(doc, ACTOR_ID));

        // docType exceeding max length (50)
        doc.setDocType("A".repeat(51));
        doc.setFileUrl("https://drive.google.com/test");
        assertEquals(ContractDocumentDAO.DOC_FAILED, dao.addDocument(doc, ACTOR_ID));

        // fileUrl exceeding max length (500)
        doc.setDocType("Hợp đồng đã ký");
        doc.setFileUrl("https://example.com/" + "A".repeat(485));
        assertEquals(ContractDocumentDAO.DOC_FAILED, dao.addDocument(doc, ACTOR_ID));
    }

    @Test
    public void addDocument_successWithTitleAndNote() throws Exception {
        ContractHistoryDAO mockHistory = mock(ContractHistoryDAO.class);
        ContractDocumentDAO customDao = new ContractDocumentDAO(mockHistory);

        ContractDocument doc = new ContractDocument();
        doc.setContractId(CONTRACT_ID);
        doc.setDocType("Hợp đồng đã ký");
        doc.setTitle("  Bản scan có chữ ký & đóng dấu đỏ  ");
        doc.setFileUrl("https://drive.google.com/file/d/xyz");
        doc.setNote("  Lưu trữ phòng Hành chính  ");

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsLock = resultSetOf(List.of(row("contract_id", CONTRACT_ID)));
        when(psLock.executeQuery()).thenReturn(rsLock);

        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(1);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("INSERT INTO contract_documents")))
                .thenReturn(psInsert);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = customDao.addDocument(doc, ACTOR_ID);
            assertEquals(ContractDocumentDAO.DOC_OK, result);

            verify(conn).setAutoCommit(false);
            verify(psLock).setInt(1, CONTRACT_ID);

            verify(psInsert).setInt(1, CONTRACT_ID);
            verify(psInsert).setString(2, "Hợp đồng đã ký");
            verify(psInsert).setString(3, "Bản scan có chữ ký & đóng dấu đỏ");
            verify(psInsert).setString(4, "https://drive.google.com/file/d/xyz");
            verify(psInsert).setString(5, "Lưu trữ phòng Hành chính");
            verify(psInsert).setInt(6, ACTOR_ID);

            verify(mockHistory).insertHistory(
                    eq(conn),
                    eq(CONTRACT_ID),
                    eq(ContractHistory.EVENT_DOCUMENT),
                    eq("Thêm tài liệu: Hợp đồng đã ký — Bản scan có chữ ký & đóng dấu đỏ"),
                    eq(ACTOR_ID),
                    eq("Lưu trữ phòng Hành chính")
            );

            verify(conn).commit();
            verify(conn, never()).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void addDocument_titleAndNoteExceeding255_safelyTruncatedAndWhitespaceTrimmed() throws Exception {
        ContractHistoryDAO mockHistory = mock(ContractHistoryDAO.class);
        ContractDocumentDAO customDao = new ContractDocumentDAO(mockHistory);

        ContractDocument doc = new ContractDocument();
        doc.setContractId(CONTRACT_ID);
        doc.setDocType("   Hợp đồng đã ký   ");
        String longTitle = "Tiêu đề: " + "T".repeat(300);
        doc.setTitle("   " + longTitle + "   ");
        doc.setFileUrl("   https://drive.google.com/doc   ");
        String longNote = "Ghi chú: " + "N".repeat(300);
        doc.setNote("   " + longNote + "   ");

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsLock = resultSetOf(List.of(row("contract_id", CONTRACT_ID)));
        when(psLock.executeQuery()).thenReturn(rsLock);

        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(1);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("INSERT INTO contract_documents")))
                .thenReturn(psInsert);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = customDao.addDocument(doc, ACTOR_ID);
            assertEquals(ContractDocumentDAO.DOC_OK, result);

            // Kiểm tra các giá trị đã được trim và cắt gọn đúng 255 ký tự
            verify(psInsert).setInt(1, CONTRACT_ID);
            verify(psInsert).setString(2, "Hợp đồng đã ký");

            ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
            verify(psInsert).setString(eq(3), titleCaptor.capture());
            assertEquals(255, titleCaptor.getValue().length());

            verify(psInsert).setString(4, "https://drive.google.com/doc");

            ArgumentCaptor<String> noteCaptor = ArgumentCaptor.forClass(String.class);
            verify(psInsert).setString(eq(5), noteCaptor.capture());
            assertEquals(255, noteCaptor.getValue().length());

            verify(mockHistory).insertHistory(
                    eq(conn),
                    eq(CONTRACT_ID),
                    eq(ContractHistory.EVENT_DOCUMENT),
                    contains("Thêm tài liệu: Hợp đồng đã ký — "),
                    eq(ACTOR_ID),
                    eq(noteCaptor.getValue())
            );

            verify(conn).commit();
        }
    }

    @Test
    public void addDocument_successWithoutTitle_noTrailingHyphenInHistory() throws Exception {
        ContractHistoryDAO mockHistory = mock(ContractHistoryDAO.class);
        ContractDocumentDAO customDao = new ContractDocumentDAO(mockHistory);

        ContractDocument doc = new ContractDocument();
        doc.setContractId(CONTRACT_ID);
        doc.setDocType("Phụ lục đã ký");
        doc.setTitle("   ");
        doc.setFileUrl("https://drive.google.com/file/d/xyz");
        doc.setNote(null);

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsLock = resultSetOf(List.of(row("contract_id", CONTRACT_ID)));
        when(psLock.executeQuery()).thenReturn(rsLock);

        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(1);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("INSERT INTO contract_documents")))
                .thenReturn(psInsert);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = customDao.addDocument(doc, ACTOR_ID);
            assertEquals(ContractDocumentDAO.DOC_OK, result);

            verify(psInsert).setString(3, null);
            verify(psInsert).setString(5, null);

            verify(mockHistory).insertHistory(
                    eq(conn),
                    eq(CONTRACT_ID),
                    eq(ContractHistory.EVENT_DOCUMENT),
                    eq("Thêm tài liệu: Phụ lục đã ký"),
                    eq(ACTOR_ID),
                    isNull()
            );

            verify(conn).commit();
        }
    }

    @Test
    public void addDocument_contractNotFoundOrDeleted_returnsNoContractCode() throws Exception {
        ContractHistoryDAO mockHistory = mock(ContractHistoryDAO.class);
        ContractDocumentDAO customDao = new ContractDocumentDAO(mockHistory);

        ContractDocument doc = new ContractDocument();
        doc.setContractId(CONTRACT_ID);
        doc.setDocType("Hợp đồng đã ký");
        doc.setFileUrl("https://drive.google.com/test");

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsLock = resultSetOf(List.of()); // contract not found or deleted
        when(psLock.executeQuery()).thenReturn(rsLock);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = customDao.addDocument(doc, ACTOR_ID);
            assertEquals(ContractDocumentDAO.DOC_NO_CONTRACT, result);

            verify(conn, never()).commit();
            verify(conn).rollback();
            verify(mockHistory, never()).insertHistory(any(), anyInt(), anyString(), anyString(), anyInt(), any());
        }
    }

    @Test
    public void addDocument_insertFailed_rollbackAndReturnsFailedCode() throws Exception {
        ContractDocument doc = new ContractDocument();
        doc.setContractId(CONTRACT_ID);
        doc.setDocType("Hợp đồng đã ký");
        doc.setFileUrl("https://drive.google.com/test");

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsLock = resultSetOf(List.of(row("contract_id", CONTRACT_ID)));
        when(psLock.executeQuery()).thenReturn(rsLock);

        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenReturn(0); // 0 rows affected

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("INSERT INTO contract_documents")))
                .thenReturn(psInsert);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = dao.addDocument(doc, ACTOR_ID);
            assertEquals(ContractDocumentDAO.DOC_FAILED, result);

            verify(conn, never()).commit();
            verify(conn).rollback();
        }
    }

    @Test
    public void addDocument_sqlExceptionOnInsert_rollbackAndReturnsFailedCode() throws Exception {
        ContractDocument doc = new ContractDocument();
        doc.setContractId(CONTRACT_ID);
        doc.setDocType("Hợp đồng đã ký");
        doc.setFileUrl("https://drive.google.com/test");

        PreparedStatement psLock = mock(PreparedStatement.class);
        ResultSet rsLock = resultSetOf(List.of(row("contract_id", CONTRACT_ID)));
        when(psLock.executeQuery()).thenReturn(rsLock);

        PreparedStatement psInsert = mock(PreparedStatement.class);
        when(psInsert.executeUpdate()).thenThrow(new SQLException("Duplicate or disk error"));

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("FROM contracts WHERE contract_id = ? AND is_deleted = 0 FOR UPDATE")))
                .thenReturn(psLock);
        when(conn.prepareStatement(contains("INSERT INTO contract_documents")))
                .thenReturn(psInsert);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            int result = dao.addDocument(doc, ACTOR_ID);
            assertEquals(ContractDocumentDAO.DOC_FAILED, result);

            verify(conn, never()).commit();
            verify(conn).rollback();
        }
    }

    // ------------------------------------------------------------------
    // 3. voidDocument (UC-52)
    // ------------------------------------------------------------------

    @Test
    public void voidDocument_validationFailures_returnsFalse() {
        // null or blank reason
        assertFalse(dao.voidDocument(DOCUMENT_ID, ACTOR_ID, null));
        assertFalse(dao.voidDocument(DOCUMENT_ID, ACTOR_ID, ""));
        assertFalse(dao.voidDocument(DOCUMENT_ID, ACTOR_ID, "   "));
        assertFalse(dao.voidDocument(DOCUMENT_ID, ACTOR_ID, "\t\n  "));

        // invalid documentId
        assertFalse(dao.voidDocument(0, ACTOR_ID, "Ly do hop le"));
        assertFalse(dao.voidDocument(-1, ACTOR_ID, "Ly do hop le"));

        // invalid actorId
        assertFalse(dao.voidDocument(DOCUMENT_ID, 0, "Ly do hop le"));
        assertFalse(dao.voidDocument(DOCUMENT_ID, -10, "Ly do hop le"));
    }

    @Test
    public void voidDocument_successWithTitle() throws Exception {
        ContractHistoryDAO mockHistory = mock(ContractHistoryDAO.class);
        ContractDocumentDAO customDao = new ContractDocumentDAO(mockHistory);

        PreparedStatement psSelect = mock(PreparedStatement.class);
        ResultSet rsSelect = resultSetOf(List.of(
                row("contract_id", CONTRACT_ID, "doc_type", "Biên bản nghiệm thu", "title", "Nghiệm thu đợt 1")
        ));
        when(psSelect.executeQuery()).thenReturn(rsSelect);

        PreparedStatement psUpdate = mock(PreparedStatement.class);
        when(psUpdate.executeUpdate()).thenReturn(1);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("SELECT contract_id, doc_type, title FROM contract_documents")))
                .thenReturn(psSelect);
        when(conn.prepareStatement(contains("UPDATE contract_documents SET is_deleted = 1")))
                .thenReturn(psUpdate);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean result = customDao.voidDocument(DOCUMENT_ID, ACTOR_ID, "   Đã lập biên bản thay thế mới   ");
            assertTrue(result);

            verify(conn).setAutoCommit(false);
            verify(psSelect).setInt(1, DOCUMENT_ID);

            verify(psUpdate).setInt(1, ACTOR_ID);
            verify(psUpdate).setString(2, "Đã lập biên bản thay thế mới");
            verify(psUpdate).setInt(3, DOCUMENT_ID);

            verify(mockHistory).insertHistory(
                    eq(conn),
                    eq(CONTRACT_ID),
                    eq(ContractHistory.EVENT_DOCUMENT),
                    eq("Huỷ tài liệu: Biên bản nghiệm thu — Nghiệm thu đợt 1"),
                    eq(ACTOR_ID),
                    eq("Đã lập biên bản thay thế mới")
            );

            verify(conn).commit();
            verify(conn, never()).rollback();
            verify(conn).setAutoCommit(true);
        }
    }

    @Test
    public void voidDocument_successWithoutTitle_noTrailingHyphenInHistory() throws Exception {
        ContractHistoryDAO mockHistory = mock(ContractHistoryDAO.class);
        ContractDocumentDAO customDao = new ContractDocumentDAO(mockHistory);

        PreparedStatement psSelect = mock(PreparedStatement.class);
        ResultSet rsSelect = resultSetOf(List.of(
                row("contract_id", CONTRACT_ID, "doc_type", "Hoá đơn", "title", null)
        ));
        when(psSelect.executeQuery()).thenReturn(rsSelect);

        PreparedStatement psUpdate = mock(PreparedStatement.class);
        when(psUpdate.executeUpdate()).thenReturn(1);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("SELECT contract_id, doc_type, title FROM contract_documents")))
                .thenReturn(psSelect);
        when(conn.prepareStatement(contains("UPDATE contract_documents SET is_deleted = 1")))
                .thenReturn(psUpdate);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean result = customDao.voidDocument(DOCUMENT_ID, ACTOR_ID, "Huỷ hoá đơn xuất nhầm");
            assertTrue(result);

            verify(mockHistory).insertHistory(
                    eq(conn),
                    eq(CONTRACT_ID),
                    eq(ContractHistory.EVENT_DOCUMENT),
                    eq("Huỷ tài liệu: Hoá đơn"),
                    eq(ACTOR_ID),
                    eq("Huỷ hoá đơn xuất nhầm")
            );

            verify(conn).commit();
        }
    }

    @Test
    public void voidDocument_reasonExceeding255Chars_safelyTruncatedForColumn() throws Exception {
        ContractHistoryDAO mockHistory = mock(ContractHistoryDAO.class);
        ContractDocumentDAO customDao = new ContractDocumentDAO(mockHistory);

        PreparedStatement psSelect = mock(PreparedStatement.class);
        ResultSet rsSelect = resultSetOf(List.of(
                row("contract_id", CONTRACT_ID, "doc_type", "Hoá đơn", "title", "VAT-01")
        ));
        when(psSelect.executeQuery()).thenReturn(rsSelect);

        PreparedStatement psUpdate = mock(PreparedStatement.class);
        when(psUpdate.executeUpdate()).thenReturn(1);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("SELECT contract_id, doc_type, title FROM contract_documents")))
                .thenReturn(psSelect);
        when(conn.prepareStatement(contains("UPDATE contract_documents SET is_deleted = 1")))
                .thenReturn(psUpdate);

        String longReason = "Lý do dài: " + "X".repeat(300);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean result = customDao.voidDocument(DOCUMENT_ID, ACTOR_ID, longReason);
            assertTrue(result);

            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            verify(psUpdate).setString(eq(2), reasonCaptor.capture());
            assertEquals(255, reasonCaptor.getValue().length());

            verify(mockHistory).insertHistory(
                    eq(conn),
                    eq(CONTRACT_ID),
                    eq(ContractHistory.EVENT_DOCUMENT),
                    contains("Huỷ tài liệu: Hoá đơn — VAT-01"),
                    eq(ACTOR_ID),
                    eq(longReason)
            );
        }
    }

    @Test
    public void voidDocument_notFoundOrAlreadyVoided_returnsFalse() throws Exception {
        ContractHistoryDAO mockHistory = mock(ContractHistoryDAO.class);
        ContractDocumentDAO customDao = new ContractDocumentDAO(mockHistory);

        PreparedStatement psSelect = mock(PreparedStatement.class);
        ResultSet rsSelect = resultSetOf(List.of()); // not found or is_deleted = 1
        when(psSelect.executeQuery()).thenReturn(rsSelect);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("SELECT contract_id, doc_type, title FROM contract_documents")))
                .thenReturn(psSelect);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean result = customDao.voidDocument(DOCUMENT_ID, ACTOR_ID, "Huỷ nhầm");
            assertFalse(result);

            verify(conn, never()).commit();
            verify(conn).rollback();
            verify(mockHistory, never()).insertHistory(any(), anyInt(), anyString(), anyString(), anyInt(), any());
        }
    }

    @Test
    public void voidDocument_updateAffectedZeroRows_returnsFalse() throws Exception {
        PreparedStatement psSelect = mock(PreparedStatement.class);
        ResultSet rsSelect = resultSetOf(List.of(
                row("contract_id", CONTRACT_ID, "doc_type", "Hoá đơn", "title", null)
        ));
        when(psSelect.executeQuery()).thenReturn(rsSelect);

        PreparedStatement psUpdate = mock(PreparedStatement.class);
        when(psUpdate.executeUpdate()).thenReturn(0);

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("SELECT contract_id, doc_type, title FROM contract_documents")))
                .thenReturn(psSelect);
        when(conn.prepareStatement(contains("UPDATE contract_documents SET is_deleted = 1")))
                .thenReturn(psUpdate);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean result = dao.voidDocument(DOCUMENT_ID, ACTOR_ID, "Huỷ");
            assertFalse(result);

            verify(conn, never()).commit();
            verify(conn).rollback();
        }
    }

    @Test
    public void voidDocument_sqlExceptionOnUpdate_returnsFalse() throws Exception {
        PreparedStatement psSelect = mock(PreparedStatement.class);
        ResultSet rsSelect = resultSetOf(List.of(
                row("contract_id", CONTRACT_ID, "doc_type", "Hoá đơn", "title", null)
        ));
        when(psSelect.executeQuery()).thenReturn(rsSelect);

        PreparedStatement psUpdate = mock(PreparedStatement.class);
        when(psUpdate.executeUpdate()).thenThrow(new SQLException("Deadlock / connection loss"));

        Connection conn = mock(Connection.class);
        when(conn.prepareStatement(contains("SELECT contract_id, doc_type, title FROM contract_documents")))
                .thenReturn(psSelect);
        when(conn.prepareStatement(contains("UPDATE contract_documents SET is_deleted = 1")))
                .thenReturn(psUpdate);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            boolean result = dao.voidDocument(DOCUMENT_ID, ACTOR_ID, "Huỷ");
            assertFalse(result);

            verify(conn, never()).commit();
            verify(conn).rollback();
        }
    }

    // ------------------------------------------------------------------
    // 4. findDocumentsOf (UC-52)
    // ------------------------------------------------------------------

    @Test
    public void findDocumentsOf_invalidContractId_returnsEmptyList() {
        assertTrue(dao.findDocumentsOf(0).isEmpty());
        assertTrue(dao.findDocumentsOf(-10).isEmpty());
    }

    @Test
    public void findDocumentsOf_success_mapsAllFieldsAndOrder() throws Exception {
        Timestamp time1 = Timestamp.valueOf("2026-03-01 10:00:00");
        Timestamp time2 = Timestamp.valueOf("2026-03-02 15:30:00");

        var row1 = row(
                "document_id", 1,
                "contract_id", CONTRACT_ID,
                "doc_type", "Hợp đồng đã ký",
                "title", "Bản PDF hợp đồng gốc",
                "file_url", "https://drive.google.com/doc1",
                "note", "Đã đối chiếu bản cứng",
                "uploaded_by", 10,
                "uploaded_at", time1,
                "last_name", "Nguyễn",
                "middle_name", "Văn",
                "first_name", "An"
        );
        var row2 = row(
                "document_id", 2,
                "contract_id", CONTRACT_ID,
                "doc_type", "Biên bản nghiệm thu",
                "title", null,
                "file_url", "https://drive.google.com/doc2",
                "note", null,
                "uploaded_by", 20,
                "uploaded_at", time2,
                "last_name", "Trần",
                "middle_name", null,
                "first_name", "Bình"
        );

        ResultSet rs = resultSetOf(List.of(row1, row2));
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenReturn(rs);
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractDocument> list = dao.findDocumentsOf(CONTRACT_ID);
            assertEquals(2, list.size());

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(conn).prepareStatement(sqlCaptor.capture());
            String sql = sqlCaptor.getValue();
            assertTrue(sql.contains("WHERE d.contract_id = ? AND d.is_deleted = 0"));
            assertTrue(sql.contains("ORDER BY d.uploaded_at, d.document_id"));
            verify(ps).setInt(1, CONTRACT_ID);

            ContractDocument d1 = list.get(0);
            assertEquals(1, d1.getDocumentId());
            assertEquals(CONTRACT_ID, d1.getContractId());
            assertEquals("Hợp đồng đã ký", d1.getDocType());
            assertEquals("Bản PDF hợp đồng gốc", d1.getTitle());
            assertEquals("https://drive.google.com/doc1", d1.getFileUrl());
            assertEquals("Đã đối chiếu bản cứng", d1.getNote());
            assertEquals(10, d1.getUploadedBy());
            assertEquals(time1, d1.getUploadedAt());
            assertEquals("Nguyễn Văn An", d1.getUploadedByName());

            ContractDocument d2 = list.get(1);
            assertEquals(2, d2.getDocumentId());
            assertEquals("Biên bản nghiệm thu", d2.getDocType());
            assertNull(d2.getTitle());
            assertEquals("Trần Bình", d2.getUploadedByName());
        }
    }

    @Test
    public void findDocumentsOf_sqlException_returnsEmptyList() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.executeQuery()).thenThrow(new SQLException("DB error"));
        Connection conn = connectionReturning(ps);

        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            db.when(DBContext::getConnection).thenReturn(conn);

            List<ContractDocument> list = dao.findDocumentsOf(CONTRACT_ID);
            assertNotNull(list);
            assertTrue(list.isEmpty());
        }
    }
}
