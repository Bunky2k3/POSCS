package poscs.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import poscs.common.TextRules;
import poscs.dao.ContractDAO;
import poscs.dao.ContractDocumentDAO;
import poscs.model.Contract;
import poscs.model.ContractDocument;
import poscs.model.User;

/**
 * Service xử lý nghiệp vụ Quản lý giấy tờ của hợp đồng (Task 4.16, Task 10.3 - [UC-52], BR-59).
 * Ràng buộc:
 * - Mỗi tờ: loại trong danh mục {@link ContractDocument#TYPES}, ngày trên giấy (bắt buộc, không
 *   sau hôm nay), số giấy, đường dẫn (KHÔNG bắt buộc; có thì phải http/https), ghi chú;
 *   loại "Khác" bắt nhập tên. Một loại có nhiều tờ được.
 * - Không upload file lên hệ thống, chỉ gắn đường dẫn nếu có.
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
    /** MSG-062: thiếu ngày trên giấy tờ (BR-59). */
    public static final String ERR_DATE_REQUIRED = "document_date_required";
    /** MSG-063: ngày trên giấy tờ sau hôm nay (BR-59). */
    public static final String ERR_DATE_FUTURE = "document_date_future";
    /** Loại "Khác" mà không nhập tên giấy tờ (BR-59). */
    public static final String ERR_TITLE_REQUIRED = "document_title_required";

    private final ContractDocumentDAO documentDAO;
    private final ContractDAO contractDAO;
    private final PermissionService permissionService;
    /** Đồng hồ để biết "hôm nay" khi kiểm ngày trên giấy; test truyền đồng hồ cố định. */
    private final Clock clock;

    public ContractDocumentService() {
        this(new ContractDocumentDAO(), new ContractDAO(), new PermissionService());
    }

    public ContractDocumentService(ContractDocumentDAO documentDAO, ContractDAO contractDAO,
                                   PermissionService permissionService) {
        this(documentDAO, contractDAO, permissionService, Clock.systemDefaultZone());
    }

    public ContractDocumentService(ContractDocumentDAO documentDAO, ContractDAO contractDAO,
                                   PermissionService permissionService, Clock clock) {
        this.documentDAO = documentDAO != null ? documentDAO : new ContractDocumentDAO();
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.permissionService = permissionService != null ? permissionService : new PermissionService();
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
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

    /**
     * Kiểm một tờ theo BR-59. Thứ tự: dữ liệu sai dạng (đường dẫn, loại, độ dài) báo
     * {@link #ERR_INVALID} trước, rồi mới tới ngày trên giấy (MSG-062, MSG-063) và tên
     * của loại "Khác" -- những lỗi người nhập hay gặp, có câu báo riêng.
     */
    private ServiceResult<Void> validateDocumentInput(ContractDocument doc) {
        if (doc == null || !isValidLink(doc.getFileUrl())) {
            return ServiceResult.fail(ERR_INVALID);
        }
        String docType = doc.getDocType() != null ? doc.getDocType().trim() : null;
        if (docType == null || docType.isEmpty() || !ContractDocument.TYPES.contains(docType)
                || docType.length() > ContractDocumentDAO.DOC_TYPE_MAX_LENGTH) {
            return ServiceResult.fail(ERR_INVALID);
        }
        if (exceeds(doc.getTitle(), ContractDocumentDAO.TITLE_MAX_LENGTH)
                || exceeds(doc.getNote(), ContractDocumentDAO.NOTE_MAX_LENGTH)
                || exceeds(doc.getDocNo(), ContractDocumentDAO.DOC_NO_MAX_LENGTH)) {
            return ServiceResult.fail(ERR_INVALID);
        }
        // BR-59: ngày trên giấy bắt buộc (MSG-062) và không sau hôm nay (MSG-063).
        if (doc.getDocDate() == null) {
            return ServiceResult.fail(ERR_DATE_REQUIRED);
        }
        if (doc.getDocDate().toLocalDate().isAfter(LocalDate.now(clock))) {
            return ServiceResult.fail(ERR_DATE_FUTURE);
        }
        // BR-59: loại "Khác" thì bắt nhập tên giấy tờ.
        if (ContractDocument.TYPE_OTHER.equals(docType)
                && (doc.getTitle() == null || doc.getTitle().trim().isEmpty())) {
            return ServiceResult.fail(ERR_TITLE_REQUIRED);
        }
        return ServiceResult.ok(null);
    }

    /** BR-59: đường dẫn không bắt buộc; có nhập thì phải là http/https an toàn và không quá dài. */
    private static boolean isValidLink(String fileUrl) {
        String url = fileUrl != null ? fileUrl.trim() : "";
        return url.isEmpty()
                || (TextRules.isSafeHttpUrl(url) && url.length() <= ContractDocumentDAO.FILE_URL_MAX_LENGTH);
    }

    private static boolean exceeds(String value, int max) {
        return value != null && value.trim().length() > max;
    }
}
