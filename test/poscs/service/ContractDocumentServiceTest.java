package poscs.service;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.ContractDAO;
import poscs.dao.ContractDocumentDAO;
import poscs.model.Contract;
import poscs.model.ContractDocument;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test toàn diện cho {@link ContractDocumentService} (Task 4.16 - [UC-52]).
 * Kiểm thử chi tiết:
 * - Kịch bản thành công và vi phạm của từng ràng buộc (URL, loại tài liệu, độ dài, lý do huỷ).
 * - Phân quyền RBAC (Admin, Sales quản lý vùng, Sales có cấp trên, Kỹ thuật).
 * - Các giá trị biên độ dài (500 ký tự URL, 50 loại, 255 tiêu đề/ghi chú/lý do).
 */
public class ContractDocumentServiceTest {

    private ContractDocumentService service;
    private ContractDocumentDAO documentDAO;
    private ContractDAO contractDAO;
    private PermissionService permissionService;

    private User adminUser;
    private User salesManager;
    private User salesSubordinate;
    private User techUser;

    @Before
    public void setUp() {
        documentDAO = mock(ContractDocumentDAO.class);
        contractDAO = mock(ContractDAO.class);
        permissionService = mock(PermissionService.class);

        service = new ContractDocumentService(documentDAO, contractDAO, permissionService);

        adminUser = buildUser(1, PermissionService.ROLE_ADMIN, null);
        salesManager = buildUser(2, PermissionService.ROLE_SALES, null);
        salesSubordinate = buildUser(3, PermissionService.ROLE_SALES, 2);
        techUser = buildUser(4, PermissionService.ROLE_TECHNICAL, null);

        when(permissionService.canManageContractDocuments(adminUser)).thenReturn(true);
        when(permissionService.canManageContractDocuments(salesManager)).thenReturn(true);
        when(permissionService.canManageContractDocuments(salesSubordinate)).thenReturn(false);
        when(permissionService.canManageContractDocuments(techUser)).thenReturn(false);
        when(permissionService.canViewContracts(any())).thenReturn(true);
    }

    private User buildUser(int userId, String roleName, Integer managerId) {
        User u = new User();
        u.setUserId(userId);
        u.setManagerId(managerId);
        u.setDeleted(false);
        Role r = new Role();
        r.setRoleName(roleName);
        u.setRole(r);
        return u;
    }

    private Contract sampleContract(int contractId, boolean deleted) {
        Contract c = new Contract();
        c.setContractId(contractId);
        c.setContractCode("HD-" + contractId);
        c.setTitle("Hợp đồng thử nghiệm");
        c.setDeleted(deleted);
        return c;
    }

    private ContractDocument sampleDocument(String docType, String url, String title, String note) {
        ContractDocument d = new ContractDocument();
        d.setDocType(docType);
        d.setFileUrl(url);
        d.setTitle(title);
        d.setNote(note);
        return d;
    }

    private ContractDocument mockExistingDoc(int docId, int contractId) {
        ContractDocument d = new ContractDocument();
        d.setDocumentId(docId);
        d.setContractId(contractId);
        d.setDocType(ContractDocument.TYPE_SIGNED_CONTRACT);
        d.setFileUrl("https://example.com/doc.pdf");
        return d;
    }

    // ==================================================================
    // 1. Thêm tài liệu (addDocument) - Kịch bản thành công
    // ==================================================================

    @Test
    public void addDocument_admin_success() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        when(documentDAO.addDocument(any(), eq(1))).thenReturn(ContractDocumentDAO.DOC_OK);

        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT,
                "https://drive.google.com/file/d/123", "Bản scan HĐ", "Kèm phụ lục");
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, adminUser);

        assertTrue(result.isSuccess());
        assertEquals(10, doc.getContractId());
        verify(documentDAO).addDocument(doc, 1);
    }

    @Test
    public void addDocument_salesManager_success() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        when(documentDAO.addDocument(any(), eq(2))).thenReturn(ContractDocumentDAO.DOC_OK);

        ContractDocument doc = sampleDocument("Biên bản nghiệm thu",
                "http://example.com/doc.pdf", null, null);
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, salesManager);

        assertTrue(result.isSuccess());
        verify(documentDAO).addDocument(doc, 2);
    }

    @Test
    public void addDocument_urlMaxLengthBoundary_success() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        when(documentDAO.addDocument(any(), eq(1))).thenReturn(ContractDocumentDAO.DOC_OK);

        String base = "https://example.com/";
        String url500 = base + "a".repeat(500 - base.length());
        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT, url500, null, null);
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, adminUser);

        assertTrue(result.isSuccess());
    }

    @Test
    public void addDocument_titleAndNoteMaxLength_success() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        when(documentDAO.addDocument(any(), eq(1))).thenReturn(ContractDocumentDAO.DOC_OK);

        String title255 = "T".repeat(255);
        String note255 = "N".repeat(255);
        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT,
                "https://example.com/test", title255, note255);
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, adminUser);

        assertTrue(result.isSuccess());
    }

    // ==================================================================
    // 2. Thêm tài liệu (addDocument) - Vi phạm quyền RBAC
    // ==================================================================

    @Test
    public void addDocument_salesSubordinate_forbidden() {
        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT,
                "https://example.com", "HĐ", null);
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, salesSubordinate);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_FORBIDDEN, result.getMessage());
        verify(documentDAO, never()).addDocument(any(), anyInt());
    }

    @Test
    public void addDocument_techUser_forbidden() {
        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT,
                "https://example.com", "HĐ", null);
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, techUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_FORBIDDEN, result.getMessage());
    }

    @Test
    public void addDocument_nullActor_forbidden() {
        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT,
                "https://example.com", "HĐ", null);
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, null);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_FORBIDDEN, result.getMessage());
    }

    // ==================================================================
    // 3. Thêm tài liệu (addDocument) - Vi phạm Hợp đồng không tồn tại
    // ==================================================================

    @Test
    public void addDocument_invalidContractId_notFound() {
        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT, "https://example.com", null, null);
        ServiceResult<ContractDocument> result = service.addDocument(0, doc, adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_NOT_FOUND, result.getMessage());
    }

    @Test
    public void addDocument_contractNotFoundInDB_notFound() {
        when(contractDAO.findById(99)).thenReturn(null);
        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT, "https://example.com", null, null);
        ServiceResult<ContractDocument> result = service.addDocument(99, doc, adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_NOT_FOUND, result.getMessage());
    }

    @Test
    public void addDocument_contractDeleted_notFound() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, true));
        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT, "https://example.com", null, null);
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_NOT_FOUND, result.getMessage());
    }

    // ==================================================================
    // 4. Thêm tài liệu (addDocument) - Vi phạm kiểm tra dữ liệu đầu vào (URL, Loại, Độ dài)
    // ==================================================================

    @Test
    public void addDocument_docNull_invalid() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        ServiceResult<ContractDocument> result = service.addDocument(10, null, adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_INVALID, result.getMessage());
    }

    @Test
    public void addDocument_urlNull_invalid() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT, null, "HĐ", null);
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_INVALID, result.getMessage());
    }

    @Test
    public void addDocument_urlEmptyOrBlank_invalid() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));

        ContractDocument d1 = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT, "", "HĐ", null);
        assertEquals(ContractDocumentService.ERR_INVALID, service.addDocument(10, d1, adminUser).getMessage());

        ContractDocument d2 = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT, "   ", "HĐ", null);
        assertEquals(ContractDocumentService.ERR_INVALID, service.addDocument(10, d2, adminUser).getMessage());
    }

    @Test
    public void addDocument_urlUnsafeScheme_invalid() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));

        ContractDocument d1 = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT, "javascript:alert(1)", "HĐ", null);
        assertEquals(ContractDocumentService.ERR_INVALID, service.addDocument(10, d1, adminUser).getMessage());

        ContractDocument d2 = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT, "ftp://example.com/file", "HĐ", null);
        assertEquals(ContractDocumentService.ERR_INVALID, service.addDocument(10, d2, adminUser).getMessage());

        ContractDocument d3 = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT, "https://example.com/<script>", "HĐ", null);
        assertEquals(ContractDocumentService.ERR_INVALID, service.addDocument(10, d3, adminUser).getMessage());
    }

    @Test
    public void addDocument_urlExceeds500Chars_invalid() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        String base = "https://example.com/";
        String url501 = base + "a".repeat(501 - base.length());

        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT, url501, "HĐ", null);
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_INVALID, result.getMessage());
    }

    @Test
    public void addDocument_docTypeNullOrBlank_invalid() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));

        ContractDocument d1 = sampleDocument(null, "https://example.com", "HĐ", null);
        assertEquals(ContractDocumentService.ERR_INVALID, service.addDocument(10, d1, adminUser).getMessage());

        ContractDocument d2 = sampleDocument("   ", "https://example.com", "HĐ", null);
        assertEquals(ContractDocumentService.ERR_INVALID, service.addDocument(10, d2, adminUser).getMessage());
    }

    @Test
    public void addDocument_docTypeNotInTypesList_invalid() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));

        ContractDocument doc = sampleDocument("Loại tài liệu lạ", "https://example.com", "HĐ", null);
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_INVALID, result.getMessage());
    }

    @Test
    public void addDocument_titleExceeds255Chars_invalid() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));

        String title256 = "T".repeat(256);
        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT,
                "https://example.com", title256, null);
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_INVALID, result.getMessage());
    }

    @Test
    public void addDocument_noteExceeds255Chars_invalid() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));

        String note256 = "N".repeat(256);
        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT,
                "https://example.com", "Tiêu đề", note256);
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_INVALID, result.getMessage());
    }

    // ==================================================================
    // 5. Thêm tài liệu (addDocument) - Xử lý lỗi từ DAO
    // ==================================================================

    @Test
    public void addDocument_daoReturnsNoContract_notFound() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        when(documentDAO.addDocument(any(), eq(1))).thenReturn(ContractDocumentDAO.DOC_NO_CONTRACT);

        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT, "https://example.com", null, null);
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_NOT_FOUND, result.getMessage());
    }

    @Test
    public void addDocument_daoReturnsFailed_documentFailed() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        when(documentDAO.addDocument(any(), eq(1))).thenReturn(ContractDocumentDAO.DOC_FAILED);

        ContractDocument doc = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT, "https://example.com", null, null);
        ServiceResult<ContractDocument> result = service.addDocument(10, doc, adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_FAILED, result.getMessage());
    }

    // ==================================================================
    // 6. Huỷ tài liệu (voidDocument) - Kịch bản thành công & Phân quyền
    // ==================================================================

    @Test
    public void voidDocument_admin_success() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        when(documentDAO.findDocumentsOf(10)).thenReturn(List.of(mockExistingDoc(101, 10)));
        when(documentDAO.voidDocument(101, 1, "Dán nhầm link")).thenReturn(true);

        ServiceResult<Void> result = service.voidDocument(10, 101, "Dán nhầm link", adminUser);

        assertTrue(result.isSuccess());
        verify(documentDAO).voidDocument(101, 1, "Dán nhầm link");
    }

    @Test
    public void voidDocument_salesManager_success() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        when(documentDAO.findDocumentsOf(10)).thenReturn(List.of(mockExistingDoc(102, 10)));
        when(documentDAO.voidDocument(102, 2, "Hết hiệu lực")).thenReturn(true);

        ServiceResult<Void> result = service.voidDocument(10, 102, "Hết hiệu lực", salesManager);

        assertTrue(result.isSuccess());
        verify(documentDAO).voidDocument(102, 2, "Hết hiệu lực");
    }

    @Test
    public void voidDocument_reasonBoundary255Chars_success() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        when(documentDAO.findDocumentsOf(10)).thenReturn(List.of(mockExistingDoc(101, 10)));
        String reason255 = "R".repeat(255);
        when(documentDAO.voidDocument(101, 1, reason255)).thenReturn(true);

        ServiceResult<Void> result = service.voidDocument(10, 101, reason255, adminUser);

        assertTrue(result.isSuccess());
    }

    @Test
    public void voidDocument_salesSubordinate_forbidden() {
        ServiceResult<Void> result = service.voidDocument(10, 101, "Lý do", salesSubordinate);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_FORBIDDEN, result.getMessage());
        verify(documentDAO, never()).voidDocument(anyInt(), anyInt(), anyString());
    }

    @Test
    public void voidDocument_techUser_forbidden() {
        ServiceResult<Void> result = service.voidDocument(10, 101, "Lý do", techUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_FORBIDDEN, result.getMessage());
    }

    // ==================================================================
    // 7. Huỷ tài liệu (voidDocument) - Vi phạm kiểm tra đầu vào & lý do
    // ==================================================================

    @Test
    public void voidDocument_invalidContractOrDocumentId_notFound() {
        assertEquals(ContractDocumentService.ERR_NOT_FOUND,
                service.voidDocument(0, 101, "Lý do", adminUser).getMessage());
        assertEquals(ContractDocumentService.ERR_NOT_FOUND,
                service.voidDocument(10, 0, "Lý do", adminUser).getMessage());
    }

    @Test
    public void voidDocument_contractNotFoundOrDeleted_notFound() {
        when(contractDAO.findById(10)).thenReturn(null);
        assertEquals(ContractDocumentService.ERR_NOT_FOUND,
                service.voidDocument(10, 101, "Lý do", adminUser).getMessage());

        when(contractDAO.findById(10)).thenReturn(sampleContract(10, true));
        assertEquals(ContractDocumentService.ERR_NOT_FOUND,
                service.voidDocument(10, 101, "Lý do", adminUser).getMessage());
    }

    @Test
    public void voidDocument_documentNotInContract_notFound() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        when(documentDAO.findDocumentsOf(10)).thenReturn(List.of(mockExistingDoc(101, 10)));

        ServiceResult<Void> result = service.voidDocument(10, 999, "Lý do", adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_NOT_FOUND, result.getMessage());
        verify(documentDAO, never()).voidDocument(anyInt(), anyInt(), anyString());
    }

    @Test
    public void voidDocument_findDocumentsOfNull_notFound() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        when(documentDAO.findDocumentsOf(10)).thenReturn(null);

        ServiceResult<Void> result = service.voidDocument(10, 101, "Lý do", adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_NOT_FOUND, result.getMessage());
    }

    @Test
    public void voidDocument_reasonNullOrBlank_reasonRequired() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        when(documentDAO.findDocumentsOf(10)).thenReturn(List.of(mockExistingDoc(101, 10)));

        assertEquals(ContractDocumentService.ERR_REASON_REQUIRED,
                service.voidDocument(10, 101, null, adminUser).getMessage());
        assertEquals(ContractDocumentService.ERR_REASON_REQUIRED,
                service.voidDocument(10, 101, "", adminUser).getMessage());
        assertEquals(ContractDocumentService.ERR_REASON_REQUIRED,
                service.voidDocument(10, 101, "   ", adminUser).getMessage());
    }

    @Test
    public void voidDocument_reasonExceeds255Chars_invalid() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        when(documentDAO.findDocumentsOf(10)).thenReturn(List.of(mockExistingDoc(101, 10)));
        String reason256 = "R".repeat(256);

        ServiceResult<Void> result = service.voidDocument(10, 101, reason256, adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_INVALID, result.getMessage());
    }

    @Test
    public void voidDocument_daoReturnsFalse_documentFailed() {
        when(contractDAO.findById(10)).thenReturn(sampleContract(10, false));
        when(documentDAO.findDocumentsOf(10)).thenReturn(List.of(mockExistingDoc(101, 10)));
        when(documentDAO.voidDocument(101, 1, "Lý do hợp lệ")).thenReturn(false);

        ServiceResult<Void> result = service.voidDocument(10, 101, "Lý do hợp lệ", adminUser);

        assertFalse(result.isSuccess());
        assertEquals(ContractDocumentService.ERR_FAILED, result.getMessage());
    }

    // ==================================================================
    // 8. Truy vấn tài liệu (findDocumentsOf)
    // ==================================================================

    @Test
    public void findDocumentsOf_authorizedViewer_returnsList() {
        ContractDocument d1 = sampleDocument(ContractDocument.TYPE_SIGNED_CONTRACT, "https://url1", "HĐ", null);
        when(documentDAO.findDocumentsOf(10)).thenReturn(List.of(d1));

        List<ContractDocument> list = service.findDocumentsOf(10, adminUser);

        assertEquals(1, list.size());
        assertEquals("https://url1", list.get(0).getFileUrl());
    }

    @Test
    public void findDocumentsOf_cannotView_returnsEmpty() {
        when(permissionService.canViewContracts(any())).thenReturn(false);

        List<ContractDocument> list = service.findDocumentsOf(10, adminUser);

        assertTrue(list.isEmpty());
        verify(documentDAO, never()).findDocumentsOf(anyInt());
    }

    @Test
    public void findDocumentsOf_invalidContractId_returnsEmpty() {
        List<ContractDocument> list = service.findDocumentsOf(0, adminUser);
        assertTrue(list.isEmpty());
    }

    // ==================================================================
    // 9. Khởi tạo & DI Constructor
    // ==================================================================

    @Test
    public void constructor_default_initializesDependencies() {
        ContractDocumentService s = new ContractDocumentService();
        assertNotNull(s);
    }
}
