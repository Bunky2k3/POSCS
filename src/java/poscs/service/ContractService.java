package poscs.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.ProductDAO;
import poscs.model.BranchInfo;
import poscs.model.Contract;
import poscs.model.ContractHistory;
import poscs.model.ContractProduct;
import poscs.model.Product;
import poscs.model.User;
import poscs.validator.BranchInfoValidator;
import poscs.validator.ContractValidator;
import poscs.validator.ValidationResult;

/**
 * Service xử lý nghiệp vụ Hợp đồng (Task 4.10).
 * Đáp ứng: UC-33, UC-34, UC-35, UC-39, UC-44, UC-46, UC-54 và BR-17, BR-35, BR-36, BR-37.
 */
public class ContractService extends BaseService {

    public static final String KIND_BUY = "buy";
    public static final String KIND_SELL = "sell";

    public static final String DIRECTION_SELL = ContractValidator.DIRECTION_SELL;
    public static final String DIRECTION_BUY = ContractValidator.DIRECTION_BUY;
    public static final String ROLE_BUYER = ContractValidator.ROLE_BUYER;
    public static final String ROLE_SUPPLIER = ContractValidator.ROLE_SUPPLIER;

    public static final String STATUS_DRAFT = ContractValidator.STATUS_DRAFT;
    public static final String STATUS_ACTIVE = ContractValidator.STATUS_ACTIVE;
    public static final String STATUS_SOON = ContractValidator.STATUS_SOON;
    public static final String STATUS_EXPIRED = ContractValidator.STATUS_EXPIRED;

    public static final String PROGRESS_DRAFT = ContractValidator.PROGRESS_DRAFT;
    public static final String PROGRESS_SIGNED = ContractValidator.PROGRESS_SIGNED;
    public static final String PROGRESS_LIQUIDATED = ContractValidator.PROGRESS_LIQUIDATED;
    public static final String PROGRESS_TERMINATED = ContractValidator.PROGRESS_TERMINATED;

    public static final String ERR_FORBIDDEN = PermissionService.MSG_022;
    public static final String ERR_NOT_FOUND = "notfound";
    public static final String ERR_INVALID = "invalid";
    public static final String ERR_DUPLICATE_CODE = "duplicate_code";
    public static final String ERR_CREATE_FAILED = "create_failed";
    public static final String ERR_UPDATE_FAILED = "update_failed";
    public static final String ERR_VALUE_NEGATIVE = "value_negative";
    public static final String ERR_AMENDMENT_NOT_ALLOWED = "amendment_not_allowed";
    public static final String ERR_MISSING_REASON = "missing_reason";
    public static final String ERR_MISSING_TERM = "missing_term";
    /** BR-58 & MSG-061: bấm Ký khi hợp đồng chưa có mã. */
    public static final String ERR_MISSING_CODE = "missing_code";
    public static final String ERR_PROGRESS_FAILED = "progress_failed";
    public static final String ERR_VOID_REASON_REQUIRED = "void_reason_required";
    public static final String ERR_VOID_HAS_AMENDMENT = "void_has_amendment";
    public static final String ERR_VOID_FAILED = "void_failed";
    public static final String ERR_ADD_PRODUCT_INVALID = "add_product_invalid";
    public static final String ERR_ADD_PRODUCT_FAILED = "add_product_failed";
    public static final String ERR_REMOVE_PRODUCT_FAILED = "remove_product_failed";
    public static final String ERR_CORRECT_FAILED = "correct_failed";

    public static final String FIELD_REASON = "reason";
    public static final String FIELD_NOTE = "note";
    public static final int MAX_PRODUCT_UNIT_LENGTH = 20;
    public static final int MAX_PRODUCT_NOTES_LENGTH = 255;
    public static final int MAX_REASON_LENGTH = 1000;

    private static final String MSG_DUPLICATE_CODE = "Mã hợp đồng đã tồn tại.";
    private static final String MSG_REASON_TOO_LONG = "Lý do/ghi chú không được vượt quá " + MAX_REASON_LENGTH + " ký tự.";

    private final ContractDAO contractDAO;
    private final CustomerDAO customerDAO;
    private final ProductDAO productDAO;
    private final PermissionService permissionService;
    private final ContractValidator contractValidator;
    /** Nguồn người ký bên mình (BR-56): thông tin Giám đốc chi nhánh, UC-55. */
    private final BranchInfoService branchInfoService;

    public ContractService() {
        this(new ContractDAO(), new CustomerDAO(), new ProductDAO(),
                new PermissionService(), new ContractValidator());
    }

    public ContractService(ContractDAO contractDAO, CustomerDAO customerDAO, ProductDAO productDAO) {
        this(contractDAO, customerDAO, productDAO, new PermissionService(), new ContractValidator());
    }

    public ContractService(ContractDAO contractDAO, CustomerDAO customerDAO, ProductDAO productDAO,
                           PermissionService permissionService, ContractValidator contractValidator) {
        this(contractDAO, customerDAO, productDAO, permissionService, contractValidator, null);
    }

    public ContractService(ContractDAO contractDAO, CustomerDAO customerDAO, ProductDAO productDAO,
                           PermissionService permissionService, ContractValidator contractValidator,
                           BranchInfoService branchInfoService) {
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.customerDAO = customerDAO != null ? customerDAO : new CustomerDAO();
        this.productDAO = productDAO != null ? productDAO : new ProductDAO();
        this.permissionService = permissionService != null ? permissionService : new PermissionService();
        this.contractValidator = contractValidator != null ? contractValidator : new ContractValidator();
        this.branchInfoService = branchInfoService != null ? branchInfoService : new BranchInfoService();
    }

    // ------------------------------------------------------------------
    // BR-17 & Helpers
    // ------------------------------------------------------------------

    public static String computeStatus(LocalDate effectiveDate, LocalDate endDate, LocalDate asOfDate) {
        return ContractValidator.computeStatus(effectiveDate, endDate, asOfDate);
    }

    public static String computeStatus(LocalDate effectiveDate, LocalDate endDate) {
        return ContractValidator.computeStatus(effectiveDate, endDate);
    }

    public static String computeStatus(Date effectiveDate, Date endDate) {
        return ContractValidator.computeStatus(effectiveDate, endDate);
    }

    public static String directionFromKind(String kindParam) {
        return KIND_BUY.equalsIgnoreCase(kindParam) ? DIRECTION_BUY : DIRECTION_SELL;
    }

    public static String kindFromDirection(String direction) {
        return DIRECTION_BUY.equalsIgnoreCase(direction) ? KIND_BUY : KIND_SELL;
    }

    public static String counterpartyRoleFor(String direction) {
        return ContractValidator.counterpartyRoleFor(direction);
    }

    public static List<String> contractTypesFor(String direction) {
        return ContractValidator.contractTypesFor(direction);
    }

    public static BigDecimal parseContractValue(String raw, boolean allowNegative) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String normalized = raw.trim().replace(",", "").replace(" ", "");
        try {
            BigDecimal val = new BigDecimal(normalized);
            return (!allowNegative && val.signum() < 0) ? null : val;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseQuantityOrNull(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            int n = Integer.parseInt(raw.trim());
            return n > 0 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean counterpartyMatchesDirection(int enterpriseId, String direction) {
        if (enterpriseId <= 0 || direction == null || direction.isBlank()) {
            return false;
        }
        return contractValidator.isCounterpartyMatchingDirection(direction, findEnterpriseRoles(enterpriseId));
    }

    // ------------------------------------------------------------------
    // BR-56: Người ký bên mình lấy từ Thông tin chi nhánh
    // ------------------------------------------------------------------

    /**
     * Đã khai GĐCN chưa (BR-56): họ tên GĐCN còn trống sau khi trim là CHƯA,
     * kể cả dòng gieo sẵn của V39 chỉ có chức vụ.
     */
    public static boolean isBranchSignerDeclared(BranchInfo branchInfo) {
        return branchInfo != null && BranchInfoValidator.trimToNull(branchInfo.getDirectorName()) != null;
    }

    /**
     * BR-56: chép họ tên, chức vụ, SĐT, fax của GĐCN hiện tại vào hợp đồng.
     *
     * <p>Chưa khai GĐCN (xem {@link #isBranchSignerDeclared}) thì KHÔNG đụng
     * gì tới bốn ô của {@code contract}: bên gọi quyết định giá trị lúc đó (tạo
     * mới thì để trống cả bốn, bản Nháp thì giữ giá trị đang có). Không chép
     * riêng chức vụ gieo sẵn khi họ tên còn trống.
     *
     * @return {@code true} nếu đã chép thông tin GĐCN
     */
    public static boolean applyBranchSigner(Contract contract, BranchInfo branchInfo) {
        if (contract == null || !isBranchSignerDeclared(branchInfo)) {
            return false;
        }
        contract.setSignerName(BranchInfoValidator.trimToNull(branchInfo.getDirectorName()));
        contract.setSignerPosition(BranchInfoValidator.trimToNull(branchInfo.getDirectorPosition()));
        contract.setSignerPhone(BranchInfoValidator.trimToNull(branchInfo.getPhone()));
        contract.setSignerFax(BranchInfoValidator.trimToNull(branchInfo.getFax()));
        return true;
    }

    /**
     * BR-56: chép bốn ô người ký bên mình từ bản ghi đang có ({@code source})
     * sang {@code target}; {@code source} null thì xoá trắng cả bốn ô. Dùng để
     * bỏ mọi giá trị gửi lên từ request: người ký bên mình không sửa tay.
     */
    public static void copyBranchSigner(Contract target, Contract source) {
        if (target == null) {
            return;
        }
        target.setSignerName(source != null ? source.getSignerName() : null);
        target.setSignerPosition(source != null ? source.getSignerPosition() : null);
        target.setSignerPhone(source != null ? source.getSignerPhone() : null);
        target.setSignerFax(source != null ? source.getSignerFax() : null);
    }

    /** Thông tin chi nhánh hiện tại cho form hợp đồng; {@code null} khi không đọc được. */
    public BranchInfo findBranchInfo() {
        return branchInfoService.get();
    }

    /** BR-56, tạo hợp đồng/phụ lục: lấy GĐCN hiện tại, chưa khai thì để trống cả bốn ô. */
    private void fillSignerForNewContract(Contract contract) {
        copyBranchSigner(contract, null);
        applyBranchSigner(contract, branchInfoService.get());
    }

    /**
     * BR-56, sửa hợp đồng: bản Nháp lấy GĐCN hiện tại (chưa khai thì giữ giá
     * trị đang có); từ Đã ký giữ nguyên bốn ô đang lưu.
     */
    private void fillSignerForUpdate(Contract contract, Contract existing) {
        copyBranchSigner(contract, existing);
        if (!existing.isTermsLocked()) {
            applyBranchSigner(contract, branchInfoService.get());
        }
    }

    // ------------------------------------------------------------------
    // BR-35: Người phụ trách lấy theo người tạo, không đổi sau khi tạo
    // ------------------------------------------------------------------

    /**
     * BR-35, lúc tạo: chọn người phụ trách cho hợp đồng/phụ lục mới. Dùng chung
     * cho {@code ContractService} và lối {@code /contract} của
     * {@code ContractController}.
     *
     * <ul>
     *   <li>Phụ lục ({@code parent} khác null): người phụ trách hợp đồng gốc.</li>
     *   <li>Admin tạo hợp đồng gốc: giữ lựa chọn gửi lên; bên gọi vẫn phải
     *       kiểm đó là một Sales đang hoạt động.</li>
     *   <li>Người khác (Sales quản lý vùng) tạo: chính người tạo, bỏ qua giá
     *       trị gửi lên.</li>
     * </ul>
     */
    public static void applyCreateOwner(Contract contract, Contract parent, int actorId, boolean actorIsAdmin) {
        if (contract == null) {
            return;
        }
        if (parent != null) {
            contract.setOwnerId(parent.getOwnerId());
        } else if (!actorIsAdmin) {
            contract.setOwnerId(actorId);
        }
    }

    /**
     * BR-35, ngoại lệ duy nhất: Admin đổi được người phụ trách khi người hiện
     * tại đã bị khoá tài khoản (nghỉ việc), ở mọi tiến độ trừ khi hợp đồng đã
     * thanh lý/chấm dứt sớm.
     */
    public static boolean canReplaceOwner(Contract existing, boolean actorIsAdmin, boolean currentOwnerActive) {
        return existing != null && actorIsAdmin && !currentOwnerActive && !existing.isFrozen();
    }

    /** {@link #canReplaceOwner(Contract, boolean, boolean)} với vai và tài khoản đọc từ CSDL. */
    public boolean canReplaceOwner(Contract existing, User actor) {
        return existing != null
                && canReplaceOwner(existing, permissionService.isAdmin(actor), isActiveOwner(existing.getOwnerId()));
    }

    // ------------------------------------------------------------------
    // UC-33: Tạo hợp đồng gốc (BR-35, BR-36)
    // ------------------------------------------------------------------

    public ServiceResult<Integer> createContract(Contract contract, User actor) {
        ServiceResult<Void> perm = permissionService.checkCanCreateContract(actor);
        if (!perm.isSuccess()) {
            return failure(perm.getMessage());
        }
        if (contract == null) {
            return failField(ERR_INVALID, ContractValidator.FIELD_CONTRACT_CODE,
                    ContractValidator.MSG_CONTRACT_INVALID);
        }
        contract.setParentContractId(null);
        applyCreateOwner(contract, null, actor.getUserId(), permissionService.isAdmin(actor));
        fillSignerForNewContract(contract);
        ValidationResult vr = contractValidator.validateCreate(contract, findEnterpriseRoles(contract.getEnterpriseId()));
        if (!vr.isValid()) {
            return ServiceResult.fail(ERR_INVALID, vr.getErrors());
        }
        // BR-35: người phụ trách phải là một Sales đang hoạt động (Admin chọn, hoặc chính Sales tạo).
        if (!isActiveSales(contract.getOwnerId())) {
            return failField(ERR_INVALID, ContractValidator.FIELD_OWNER_ID, ContractValidator.MSG_OWNER_REQUIRED);
        }
        normalizeCodeAndTitle(contract);
        contract.setProgressStatus(PROGRESS_DRAFT);
        // BR-58: để trống mã thì không kiểm trùng.
        if (contract.hasContractCode() && contractDAO.contractCodeExists(contract.getContractCode(), 0)) {
            return failField(ERR_DUPLICATE_CODE, ContractValidator.FIELD_CONTRACT_CODE, MSG_DUPLICATE_CODE);
        }
        return mapInsertResult(contractDAO.insert(contract, actor.getUserId()));
    }

    // ------------------------------------------------------------------
    // UC-46: Tạo phụ lục hợp đồng (BR-35, BR-36)
    // ------------------------------------------------------------------

    public ServiceResult<Integer> createAmendment(int parentContractId, Contract amendment, User actor) {
        ServiceResult<Void> perm = permissionService.checkCanCreateAmendment(actor);
        if (!perm.isSuccess()) {
            return failure(perm.getMessage());
        }
        if (parentContractId <= 0) {
            return failure(ERR_NOT_FOUND);
        }
        if (amendment == null) {
            return failField(ERR_INVALID, ContractValidator.FIELD_CONTRACT_CODE,
                    ContractValidator.MSG_CONTRACT_INVALID);
        }
        Contract parent = contractDAO.findById(parentContractId);
        if (parent == null) {
            return failure(ERR_NOT_FOUND);
        }
        if (!parent.isSigned() || parent.isAmendment()) {
            return failure(ERR_AMENDMENT_NOT_ALLOWED);
        }
        prepareAmendmentFromParent(amendment, parent);
        // BR-35: phụ lục theo người phụ trách hợp đồng gốc, bỏ qua giá trị gửi lên.
        applyCreateOwner(amendment, parent, actor.getUserId(), permissionService.isAdmin(actor));
        fillSignerForNewContract(amendment);
        ValidationResult vr = contractValidator.validateCreate(amendment, findEnterpriseRoles(parent.getEnterpriseId()));
        if (!vr.isValid()) {
            return ServiceResult.fail(ERR_INVALID, vr.getErrors());
        }
        if (!isActiveOwner(amendment.getOwnerId())) {
            return failField(ERR_INVALID, ContractValidator.FIELD_OWNER_ID, ContractValidator.MSG_OWNER_REQUIRED);
        }
        if (contractValidator.exceedsRemainingValue(parent, amendment.getContractValue())) {
            return failField(ERR_VALUE_NEGATIVE, ContractValidator.FIELD_CONTRACT_VALUE,
                    ContractValidator.MSG_EXCEEDS_REMAINING_VALUE);
        }
        // BR-58: để trống mã thì không kiểm trùng.
        if (amendment.hasContractCode() && contractDAO.contractCodeExists(amendment.getContractCode(), 0)) {
            return failField(ERR_DUPLICATE_CODE, ContractValidator.FIELD_CONTRACT_CODE, MSG_DUPLICATE_CODE);
        }
        return mapInsertResult(contractDAO.insert(amendment, actor.getUserId()));
    }

    private void prepareAmendmentFromParent(Contract amendment, Contract parent) {
        amendment.setParentContractId(parent.getContractId());
        amendment.setEnterpriseId(parent.getEnterpriseId());
        amendment.setDirection(parent.getDirection());
        normalizeCodeAndTitle(amendment);
        amendment.setProgressStatus(PROGRESS_DRAFT);
    }

    private ServiceResult<Integer> mapInsertResult(int newId) {
        if (newId > 0) {
            return success(newId);
        }
        if (newId == ContractDAO.DUPLICATE_CODE) {
            return failField(ERR_DUPLICATE_CODE, ContractValidator.FIELD_CONTRACT_CODE, MSG_DUPLICATE_CODE);
        }
        if (newId == ContractDAO.INVALID_PARENT) {
            return failure(ERR_AMENDMENT_NOT_ALLOWED);
        }
        if (newId == ContractDAO.INVALID_VALUE) {
            return failField(ERR_VALUE_NEGATIVE, ContractValidator.FIELD_CONTRACT_VALUE,
                    ContractValidator.MSG_EXCEEDS_REMAINING_VALUE);
        }
        return failure(ERR_CREATE_FAILED);
    }

    // ------------------------------------------------------------------
    // UC-35: Cập nhật hợp đồng theo tiến độ (BR-35, BR-36)
    // ------------------------------------------------------------------

    public ServiceResult<Void> updateContract(Contract contract, User actor) {
        ServiceResult<Void> perm = permissionService.checkCanUpdateContract(actor);
        if (!perm.isSuccess()) {
            return failure(perm.getMessage());
        }
        if (contract == null || contract.getContractId() <= 0) {
            return failField(ERR_INVALID, ContractValidator.FIELD_CONTRACT_CODE,
                    ContractValidator.MSG_CONTRACT_INVALID);
        }
        Contract existing = contractDAO.findById(contract.getContractId());
        if (existing == null) {
            return failure(ERR_NOT_FOUND);
        }
        // BR-35/UC-35: từ Đã ký trở đi form Sửa không còn ô nào sửa được: thay đổi
        // đi qua phụ lục, sai sót nhập liệu đi qua UC-54, người phụ trách chỉ đổi
        // qua ngoại lệ của Admin (updateContractOwner).
        if (existing.isTermsLocked()) {
            return failField(ERR_UPDATE_FAILED, ContractValidator.FIELD_PROGRESS_STATUS,
                    ContractValidator.MSG_CANNOT_EDIT_LOCKED);
        }
        if (contract.getDirection() == null) {
            contract.setDirection(existing.getDirection());
        }
        contract.setParentContractId(existing.getParentContractId());
        // BR-35: người phụ trách không đổi sau khi tạo, bỏ qua giá trị gửi lên.
        contract.setOwnerId(existing.getOwnerId());
        fillSignerForUpdate(contract, existing);
        ValidationResult vr = contractValidator.validateUpdate(
                contract, existing, findEnterpriseRoles(contract.getEnterpriseId()));
        if (!vr.isValid()) {
            return ServiceResult.fail(ERR_INVALID, vr.getErrors());
        }
        ServiceResult<Void> draftCheck = checkDraftUpdateRules(contract, existing);
        if (!draftCheck.isSuccess()) {
            return draftCheck;
        }
        return contractDAO.update(contract, actor.getUserId()) ? success(null) : failure(ERR_UPDATE_FAILED);
    }

    private ServiceResult<Void> checkDraftUpdateRules(Contract contract, Contract existing) {
        normalizeCodeAndTitle(contract);
        // BR-58: để trống mã thì không kiểm trùng.
        if (contract.hasContractCode()
                && contractDAO.contractCodeExists(contract.getContractCode(), contract.getContractId())) {
            return failField(ERR_DUPLICATE_CODE, ContractValidator.FIELD_CONTRACT_CODE, MSG_DUPLICATE_CODE);
        }
        if (existing.isAmendment()) {
            Contract parent = contractDAO.findById(existing.getParentContractId());
            if (parent == null || contractValidator.exceedsRemainingValue(parent, contract.getContractValue())) {
                return failField(ERR_VALUE_NEGATIVE, ContractValidator.FIELD_CONTRACT_VALUE,
                        ContractValidator.MSG_EXCEEDS_REMAINING_VALUE);
            }
        }
        return success(null);
    }

    /**
     * BR-35, ngoại lệ duy nhất để đổi người phụ trách sau khi tạo: chỉ Admin, chỉ
     * khi người phụ trách hiện tại đã bị khoá tài khoản, người mới phải là Sales
     * đang hoạt động. Áp được cả khi đã ký, trừ khi đã thanh lý/chấm dứt sớm.
     * Ngoài ngoại lệ này trả MSG-022.
     */
    public ServiceResult<Void> updateContractOwner(int contractId, int newOwnerId, User actor) {
        ServiceResult<Void> check = checkOwnerReplacement(contractId, newOwnerId, actor);
        if (!check.isSuccess()) {
            return check;
        }
        return contractDAO.replaceOwner(contractId, newOwnerId, actor.getUserId())
                ? success(null) : failure(ERR_UPDATE_FAILED);
    }

    /**
     * BR-35, ngoại lệ của Admin trên bản Nháp: vừa thay người phụ trách đã bị
     * khoá tài khoản, vừa lưu các ô khác của form Sửa. Kiểm người thay trước,
     * lưu bản Nháp (kiểm hợp lệ mọi ô), rồi mới đổi người phụ trách -- form lỗi
     * thì không ghi gì, kể cả người phụ trách (QA-8.5-01).
     */
    public ServiceResult<Void> updateDraftReplacingOwner(Contract contract, int newOwnerId, User actor) {
        int contractId = contract != null ? contract.getContractId() : 0;
        ServiceResult<Void> check = checkOwnerReplacement(contractId, newOwnerId, actor);
        if (!check.isSuccess()) {
            return check;
        }
        ServiceResult<Void> saved = updateContract(contract, actor);
        if (!saved.isSuccess()) {
            return saved;
        }
        return contractDAO.replaceOwner(contractId, newOwnerId, actor.getUserId())
                ? success(null) : failure(ERR_UPDATE_FAILED);
    }

    /** Mọi điều kiện của ngoại lệ BR-35 ({@link #updateContractOwner}), không ghi gì xuống CSDL. */
    private ServiceResult<Void> checkOwnerReplacement(int contractId, int newOwnerId, User actor) {
        ServiceResult<Void> perm = permissionService.checkPermission(permissionService.isAdmin(actor));
        if (!perm.isSuccess()) {
            return failure(perm.getMessage());
        }
        if (contractId <= 0) {
            return failure(ERR_NOT_FOUND);
        }
        Contract existing = contractDAO.findById(contractId);
        if (existing == null) {
            return failure(ERR_NOT_FOUND);
        }
        if (existing.isFrozen()) {
            return failField(ERR_UPDATE_FAILED, ContractValidator.FIELD_PROGRESS_STATUS,
                    ContractValidator.MSG_CANNOT_EDIT_LOCKED);
        }
        if (!canReplaceOwner(existing, true, isActiveOwner(existing.getOwnerId()))) {
            return failure(ERR_FORBIDDEN);
        }
        if (!isActiveSales(newOwnerId)) {
            return failField(ERR_INVALID, ContractValidator.FIELD_OWNER_ID, ContractValidator.MSG_OWNER_REQUIRED);
        }
        return success(null);
    }

    // ------------------------------------------------------------------
    // UC-44: Ký hợp đồng & chuyển tiến độ (BR-35, BR-36)
    // ------------------------------------------------------------------

    public ServiceResult<Void> signContract(int contractId, User actor, String note) {
        ServiceResult<Void> perm = permissionService.checkCanSignContract(actor);
        if (!perm.isSuccess()) {
            return failure(perm.getMessage());
        }
        if (contractId <= 0) {
            return failure(ERR_NOT_FOUND);
        }
        if (note != null && note.trim().length() > MAX_REASON_LENGTH) {
            return failField(ERR_INVALID, FIELD_NOTE, MSG_REASON_TOO_LONG);
        }
        Contract target = contractDAO.findById(contractId);
        if (target == null) {
            return failure(ERR_NOT_FOUND);
        }
        if (!target.isDraft()) {
            return failField(ERR_PROGRESS_FAILED, ContractValidator.FIELD_PROGRESS_STATUS,
                    ContractValidator.MSG_ONLY_DRAFT_CAN_SIGN);
        }
        ServiceResult<Void> signable = checkSignable(target);
        if (!signable.isSuccess()) {
            return signable;
        }
        String trimmedNote = (note != null && !note.trim().isEmpty()) ? note.trim() : null;
        boolean ok = contractDAO.changeProgressStatus(contractId, PROGRESS_SIGNED, actor.getUserId(), trimmedNote);
        return ok ? success(null) : failure(ERR_PROGRESS_FAILED);
    }

    /**
     * Điều kiện ký một bản Nháp, không ghi gì xuống CSDL: có mã và mã không
     * trùng hợp đồng khác (BR-58, MSG-061), đủ thời hạn và Ngày ký (hôm nay)
     * ≤ hiệu lực ≤ kết thúc (BR-36), phụ lục không kéo giá trị hợp đồng gốc âm.
     */
    private ServiceResult<Void> checkSignable(Contract target) {
        if (!target.hasContractCode()) {
            return failField(ERR_MISSING_CODE, ContractValidator.FIELD_CONTRACT_CODE, ContractValidator.MSG_061);
        }
        if (contractDAO.contractCodeExists(target.getContractCode(), target.getContractId())) {
            return failField(ERR_DUPLICATE_CODE, ContractValidator.FIELD_CONTRACT_CODE, MSG_DUPLICATE_CODE);
        }
        ValidationResult vr = contractValidator.validateSigning(target, LocalDate.now());
        if (!vr.isValid()) {
            return ServiceResult.fail(ERR_MISSING_TERM, vr.getErrors());
        }
        if (target.isAmendment()) {
            Contract parent = contractDAO.findById(target.getParentContractId());
            if (parent == null || contractValidator.exceedsRemainingValue(parent, target.getContractValue())) {
                return failField(ERR_VALUE_NEGATIVE, ContractValidator.FIELD_CONTRACT_VALUE,
                        ContractValidator.MSG_EXCEEDS_REMAINING_VALUE);
            }
        }
        return success(null);
    }

    public ServiceResult<Void> changeProgressStatus(int contractId, String toStatus, User actor, String note) {
        if (PROGRESS_SIGNED.equals(toStatus != null ? toStatus.trim() : null)) {
            return signContract(contractId, actor, note);
        }
        ServiceResult<Void> perm = permissionService.checkCanManageContracts(actor);
        if (!perm.isSuccess()) {
            return failure(perm.getMessage());
        }
        if (contractId <= 0) {
            return failure(ERR_NOT_FOUND);
        }
        if (toStatus == null || toStatus.trim().isEmpty()) {
            return failure(ERR_PROGRESS_FAILED);
        }
        if (note != null && note.trim().length() > MAX_REASON_LENGTH) {
            return failField(ERR_INVALID, FIELD_NOTE, MSG_REASON_TOO_LONG);
        }
        Contract target = contractDAO.findById(contractId);
        if (target == null) {
            return failure(ERR_NOT_FOUND);
        }
        String normalizedStatus = toStatus.trim();
        if (PROGRESS_LIQUIDATED.equals(normalizedStatus) || PROGRESS_TERMINATED.equals(normalizedStatus)) {
            if (!target.isSigned()) {
                return failure(ERR_PROGRESS_FAILED);
            }
            if (note == null || note.trim().isEmpty()) {
                return failure(ERR_MISSING_REASON);
            }
        }
        boolean ok = contractDAO.changeProgressStatus(contractId, normalizedStatus, actor.getUserId(),
                note != null ? note.trim() : null);
        return ok ? success(null) : failure(ERR_PROGRESS_FAILED);
    }

    // ------------------------------------------------------------------
    // UC-34: Huỷ bản ghi hợp đồng (BR-37)
    // ------------------------------------------------------------------

    public ServiceResult<Void> voidContract(int contractId, User actor, String reason) {
        if (contractId <= 0) {
            return failure(ERR_NOT_FOUND);
        }
        Contract target = contractDAO.findById(contractId);
        if (target == null) {
            return failure(ERR_NOT_FOUND);
        }
        ServiceResult<Void> perm = permissionService.checkCanVoidContract(actor, target.isDraft());
        if (!perm.isSuccess()) {
            return failure(perm.getMessage());
        }
        if (reason == null || reason.trim().isEmpty()) {
            return failure(ERR_VOID_REASON_REQUIRED);
        }
        if (reason.trim().length() > MAX_REASON_LENGTH) {
            return failField(ERR_INVALID, FIELD_REASON, MSG_REASON_TOO_LONG);
        }
        // BR-37: Hợp đồng có phụ lục sống thì chặn huỷ với mã lỗi riêng
        if (target.getAmendmentCount() > 0) {
            return failure(ERR_VOID_HAS_AMENDMENT);
        }
        boolean ok = contractDAO.voidRecord(contractId, actor.getUserId(), reason.trim());
        return ok ? success(null) : failure(ERR_VOID_FAILED);
    }

    // ------------------------------------------------------------------
    // UC-54: Chữa sai sót nhập liệu trên hợp đồng đã ký (chỉ Admin)
    // ------------------------------------------------------------------

    public ServiceResult<Void> correctSignedContract(Contract contract, User actor, String reason) {
        ServiceResult<Void> perm = permissionService.checkCanCorrectSignedContract(actor);
        if (!perm.isSuccess()) {
            return failure(perm.getMessage());
        }
        if (contract == null || contract.getContractId() <= 0) {
            return failField(ERR_INVALID, ContractValidator.FIELD_CONTRACT_CODE,
                    ContractValidator.MSG_CONTRACT_INVALID);
        }
        if (reason == null || reason.trim().isEmpty()) {
            return failure(ERR_MISSING_REASON);
        }
        if (reason.trim().length() > MAX_REASON_LENGTH) {
            return failField(ERR_INVALID, FIELD_REASON, MSG_REASON_TOO_LONG);
        }
        Contract existing = contractDAO.findById(contract.getContractId());
        if (existing == null) {
            return failure(ERR_NOT_FOUND);
        }
        if (!existing.isSigned()) {
            return failure(ERR_CORRECT_FAILED);
        }
        // BR-56: UC-54 không đổi bốn ô người ký bên mình.
        copyBranchSigner(contract, existing);
        // BR-35: chữa sai sót cũng không đổi người phụ trách.
        contract.setOwnerId(existing.getOwnerId());
        ServiceResult<Void> validRes = validateSignedCorrection(contract, existing);
        if (!validRes.isSuccess()) {
            return validRes;
        }
        boolean ok = contractDAO.correct(contract, actor.getUserId(), reason.trim());
        return ok ? success(null) : failure(ERR_CORRECT_FAILED);
    }

    private ServiceResult<Void> validateSignedCorrection(Contract contract, Contract existing) {
        contract.setDirection(existing.getDirection());
        contract.setSigningDate(existing.getSigningDate());
        contract.setParentContractId(existing.getParentContractId());
        ValidationResult vr = contractValidator.validateRequiredFields(contract);
        if (contract.getEnterpriseId() > 0) {
            contractValidator.validateCounterpartyRole(vr, contract.getDirection(),
                    findEnterpriseRoles(contract.getEnterpriseId()));
        }
        if (contract.getEffectiveDate() == null || contract.getEndDate() == null) {
            vr.addError(ContractValidator.FIELD_EFFECTIVE_DATE, ContractValidator.MSG_045);
        } else {
            contractValidator.validateDates(vr, contract.getSigningDate(),
                    contract.getEffectiveDate(), contract.getEndDate());
        }
        if (!vr.isValid()) {
            return ServiceResult.fail(ERR_INVALID, vr.getErrors());
        }
        normalizeCodeAndTitle(contract);
        if (contractDAO.contractCodeExists(contract.getContractCode(), contract.getContractId())) {
            return failField(ERR_DUPLICATE_CODE, ContractValidator.FIELD_CONTRACT_CODE, MSG_DUPLICATE_CODE);
        }
        return checkSignedAmendmentValue(contract, existing);
    }

    private ServiceResult<Void> checkSignedAmendmentValue(Contract contract, Contract existing) {
        if (!existing.isAmendment()) {
            if (contract.getContractValue() != null && existing.getAmendmentValueSigned() != null) {
                BigDecimal current = contract.getContractValue().add(existing.getAmendmentValueSigned());
                if (current.signum() < 0) {
                    return failField(ERR_VALUE_NEGATIVE, ContractValidator.FIELD_CONTRACT_VALUE,
                            ContractValidator.MSG_EXCEEDS_REMAINING_VALUE);
                }
            }
            return success(null);
        }
        if (contract.getContractValue() == null || contract.getContractValue().signum() >= 0) {
            return success(null);
        }
        Contract parent = contractDAO.findById(existing.getParentContractId());
        if (parent == null) {
            return failure(ERR_CORRECT_FAILED);
        }
        BigDecimal oldDelta = existing.getContractValue() != null ? existing.getContractValue() : BigDecimal.ZERO;
        BigDecimal baseRemaining = (parent.getCurrentValue() != null ? parent.getCurrentValue() : BigDecimal.ZERO)
                .subtract(oldDelta);
        if (contractValidator.exceedsRemainingValue(baseRemaining, contract.getContractValue())) {
            return failField(ERR_VALUE_NEGATIVE, ContractValidator.FIELD_CONTRACT_VALUE,
                    ContractValidator.MSG_EXCEEDS_REMAINING_VALUE);
        }
        return success(null);
    }

    // ------------------------------------------------------------------
    // UC-39: Quản lý sản phẩm trong hợp đồng
    // ------------------------------------------------------------------

    public ServiceResult<Void> addProductToContract(int contractId, Integer productId, Integer quantity,
                                                    String unit, String notes, User actor) {
        ServiceResult<Void> perm = permissionService.checkCanManageContractProducts(actor);
        if (!perm.isSuccess()) {
            return failure(perm.getMessage());
        }
        if (contractId <= 0) {
            return failure(ERR_NOT_FOUND);
        }
        Contract target = contractDAO.findById(contractId);
        if (target == null) {
            return failure(ERR_NOT_FOUND);
        }
        if (!target.isDraft()) {
            return failure(ERR_ADD_PRODUCT_FAILED);
        }
        if (isInvalidProductInput(productId, quantity, unit, notes)) {
            return failure(ERR_ADD_PRODUCT_INVALID);
        }
        Product product = productDAO.findById(productId);
        if (product == null) {
            return failure(ERR_ADD_PRODUCT_INVALID);
        }
        ContractProduct item = buildContractProduct(product, quantity, unit, notes);
        boolean ok = contractDAO.insertProducts(contractId, List.of(item), actor.getUserId());
        return ok ? success(null) : failure(ERR_ADD_PRODUCT_FAILED);
    }

    public ServiceResult<Void> removeProductFromContract(int contractId, int contractProductId, User actor) {
        ServiceResult<Void> perm = permissionService.checkCanManageContractProducts(actor);
        if (!perm.isSuccess()) {
            return failure(perm.getMessage());
        }
        if (contractId <= 0) {
            return failure(ERR_NOT_FOUND);
        }
        if (contractProductId <= 0) {
            return failure(ERR_REMOVE_PRODUCT_FAILED);
        }
        Contract target = contractDAO.findById(contractId);
        if (target == null) {
            return failure(ERR_NOT_FOUND);
        }
        if (!target.isDraft()) {
            return failure(ERR_REMOVE_PRODUCT_FAILED);
        }
        boolean ok = contractDAO.deleteProductLine(contractProductId, contractId, actor.getUserId());
        return ok ? success(null) : failure(ERR_REMOVE_PRODUCT_FAILED);
    }

    private static boolean isInvalidProductInput(Integer productId, Integer quantity, String unit, String notes) {
        if (productId == null || productId <= 0 || quantity == null || quantity <= 0) {
            return true;
        }
        if (unit != null && unit.trim().length() > MAX_PRODUCT_UNIT_LENGTH) {
            return true;
        }
        return notes != null && notes.trim().length() > MAX_PRODUCT_NOTES_LENGTH;
    }

    private ContractProduct buildContractProduct(Product product, int quantity, String unit, String notes) {
        ContractProduct item = new ContractProduct();
        item.setProductId(product.getProductId());
        item.setProductCode(product.getProductCode());
        item.setProductName(product.getProductName());
        item.setQuantity(quantity);
        item.setUnit((unit == null || unit.trim().isEmpty()) ? "Cái" : unit.trim());
        item.setNotes((notes != null && !notes.trim().isEmpty()) ? notes.trim() : null);
        return item;
    }

    // ------------------------------------------------------------------
    // Truy vấn dữ liệu bổ trợ & Internal Helpers
    // ------------------------------------------------------------------

    public Contract findById(int contractId) {
        return contractId > 0 ? contractDAO.findById(contractId) : null;
    }

    public List<Contract> findByEnterpriseId(int enterpriseId) {
        return enterpriseId > 0 ? contractDAO.findByEnterpriseId(enterpriseId) : Collections.emptyList();
    }

    public List<Contract> findAmendmentsByParentId(int parentContractId) {
        return parentContractId > 0 ? contractDAO.findAmendmentsByParentId(parentContractId) : Collections.emptyList();
    }

    public List<ContractProduct> findProductsByContractId(int contractId) {
        return contractId > 0 ? contractDAO.findProductsByContractId(contractId) : Collections.emptyList();
    }

    public List<ContractHistory> findHistoryByContractId(int contractId) {
        return contractId > 0 ? contractDAO.findHistoryByContractId(contractId) : Collections.emptyList();
    }

    public boolean contractCodeExists(String contractCode, int excludeContractId) {
        return contractDAO.contractCodeExists(contractCode, excludeContractId);
    }

    private boolean isActiveOwner(int ownerId) {
        if (ownerId <= 0) {
            return false;
        }
        User probe = new User();
        probe.setUserId(ownerId);
        User dbUser = permissionService.getEffectiveUser(probe);
        return dbUser != null && !dbUser.isDeleted();
    }

    /** BR-35: người phụ trách được chọn phải giữ vai Sales và chưa bị khoá tài khoản. */
    private boolean isActiveSales(int ownerId) {
        if (ownerId <= 0) {
            return false;
        }
        User probe = new User();
        probe.setUserId(ownerId);
        return permissionService.isSales(probe);
    }

    private static void normalizeCodeAndTitle(Contract contract) {
        // BR-58: mã toàn khoảng trắng là chưa có mã -- ghi NULL, không ghi chuỗi rỗng,
        // để nhiều bản Nháp cùng chưa có mã không bị coi là trùng.
        if (contract.getContractCode() != null) {
            String code = contract.getContractCode().trim();
            contract.setContractCode(code.isEmpty() ? null : code);
        }
        if (contract.getTitle() != null) {
            contract.setTitle(contract.getTitle().trim());
        }
    }

    private List<String> findEnterpriseRoles(int enterpriseId) {
        if (enterpriseId <= 0) {
            return Collections.emptyList();
        }
        List<String> roles = customerDAO.findRolesOf(enterpriseId);
        return roles != null ? roles : Collections.emptyList();
    }

    private static <T> ServiceResult<T> failField(String message, String field, String fieldError) {
        return ServiceResult.fail(message, Map.of(field, fieldError));
    }
}
