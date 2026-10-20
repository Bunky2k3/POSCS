package poscs.service;

import java.util.Collections;
import java.util.List;
import poscs.common.TextRules;
import poscs.dao.ContractDAO;
import poscs.dao.ContractDocumentDAO;
import poscs.model.Contract;
import poscs.model.ContractDocument;
import poscs.model.User;

/**
 * Service xử lý nghiệp vụ Quản lý Tài liệu đính kèm Hợp đồng (Task 4.16 - [UC-52]).
 * Ràng buộc:
 * - Chỉ gắn đường dẫn (URL), không upload file lên hệ thống.
 * - Huỷ mềm (is_deleted = 1), bắt buộc có lý do giải thích.
 * - Phân quyền theo ma trận SPEC: Admin, Sales quản lý vùng (MSG-022 nếu vi phạm).
 * - Không chặn trạng thái hợp đồng: hồ sơ thêm/huỷ được ở mọi giai đoạn (kể cả sau thanh lý).
 */
public class ContractDocumentService extends BaseService {

    public static final String ERR_FORBIDDEN = PermissionService.MSG_022;
    public static final String ERR_NOT_FOUND = "notfound";
    public static final String ERR_INVALID = "document_invalid";
    public static final String ERR_REASON_REQUIRED = "document_reason_required";
    public static final String ERR_FAILED = "document_failed";

    private final ContractDocumentDAO documentDAO;
    private final ContractDAO contractDAO;
    private final PermissionService permissionService;

    public ContractDocumentService() {
        this(new ContractDocumentDAO(), new ContractDAO(), new PermissionService());
    }

    public ContractDocumentService(ContractDocumentDAO documentDAO, ContractDAO contractDAO,
                                   PermissionService permissionService) {
        this.documentDAO = documentDAO != null ? documentDAO : new ContractDocumentDAO();
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.permissionService = permissionService != null ? permissionService : new PermissionService();
    }

    /**
     * Thêm tài liệu đính kèm vào hợp đồng [UC-52].
     */
    public ServiceResult<ContractDocument> addDocument(int contractId, ContractDocument doc, User actor) {
        if (!permissionService.canManageContractDocuments(actor)) {
            return ServiceResult.fail(ERR_FORBIDDEN);
        }
        if (contractId <= 0) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        Contract contract = contractDAO.findById(contractId);
        if (contract == null || contract.isDeleted()) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        ServiceResult<Void> validation = validateDocumentInput(doc);
        if (!validation.isSuccess()) {
            return ServiceResult.fail(validation.getMessage(), validation.getFieldErrors());
        }

        doc.setContractId(contractId);
        int result = documentDAO.addDocument(doc, actor.getUserId());
        if (result == ContractDocumentDAO.DOC_NO_CONTRACT) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        if (result != ContractDocumentDAO.DOC_OK) {
            return ServiceResult.fail(ERR_FAILED);
        }
        return ServiceResult.ok(doc, "Thêm tài liệu thành công.");
    }

    /**
     * Huỷ tài liệu đính kèm (xoá mềm), bắt buộc có lý do [UC-52].
     */
    public ServiceResult<Void> voidDocument(int contractId, int documentId, String reason, User actor) {
        if (!permissionService.canManageContractDocuments(actor)) {
            return ServiceResult.fail(ERR_FORBIDDEN);
        }
        if (contractId <= 0 || documentId <= 0) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        Contract contract = contractDAO.findById(contractId);
        if (contract == null || contract.isDeleted()) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        if (!isDocumentInContract(contractId, documentId)) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        String cleanReason = reason != null ? reason.trim() : null;
        if (cleanReason == null || cleanReason.isEmpty()) {
            return ServiceResult.fail(ERR_REASON_REQUIRED);
        }
        if (cleanReason.length() > ContractDocumentDAO.DELETE_REASON_MAX_LENGTH) {
            return ServiceResult.fail(ERR_INVALID);
        }

        boolean ok = documentDAO.voidDocument(documentId, actor.getUserId(), cleanReason);
        if (!ok) {
            return ServiceResult.fail(ERR_FAILED);
        }
        return ServiceResult.ok(null, "Huỷ tài liệu thành công.");
    }

    /**
     * Truy vấn danh sách tài liệu còn hiệu lực của hợp đồng.
     */
    public List<ContractDocument> findDocumentsOf(int contractId, User viewer) {
        if (!permissionService.canViewContracts(viewer) || contractId <= 0) {
            return Collections.emptyList();
        }
        return documentDAO.findDocumentsOf(contractId);
    }

    private boolean isDocumentInContract(int contractId, int documentId) {
        List<ContractDocument> docs = documentDAO.findDocumentsOf(contractId);
        if (docs == null) {
            return false;
        }
        for (ContractDocument d : docs) {
            if (d != null && d.getDocumentId() == documentId) {
                return true;
            }
        }
        return false;
    }

    private ServiceResult<Void> validateDocumentInput(ContractDocument doc) {
        if (doc == null) {
            return ServiceResult.fail(ERR_INVALID);
        }
        String url = doc.getFileUrl() != null ? doc.getFileUrl().trim() : null;
        if (url == null || url.isEmpty() || !TextRules.isSafeHttpUrl(url)
                || url.length() > ContractDocumentDAO.FILE_URL_MAX_LENGTH) {
            return ServiceResult.fail(ERR_INVALID);
        }
        String docType = doc.getDocType() != null ? doc.getDocType().trim() : null;
        if (docType == null || docType.isEmpty() || !ContractDocument.TYPES.contains(docType)
                || docType.length() > ContractDocumentDAO.DOC_TYPE_MAX_LENGTH) {
            return ServiceResult.fail(ERR_INVALID);
        }
        if (doc.getTitle() != null && doc.getTitle().trim().length() > ContractDocumentDAO.TITLE_MAX_LENGTH) {
            return ServiceResult.fail(ERR_INVALID);
        }
        if (doc.getNote() != null && doc.getNote().trim().length() > ContractDocumentDAO.NOTE_MAX_LENGTH) {
            return ServiceResult.fail(ERR_INVALID);
        }
        return ServiceResult.ok(null);
    }
}
