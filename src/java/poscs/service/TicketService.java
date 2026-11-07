package poscs.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import poscs.common.Period;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.TechnicalSupportTicketDAO;
import poscs.model.Contract;
import poscs.model.Enterprise;
import poscs.model.TechnicalRequest;
import poscs.model.TechnicalRequestHistory;
import poscs.model.User;
import poscs.validator.TicketValidator;
import poscs.validator.ValidationResult;

/**
 * Service xử lý nghiệp vụ Phiếu hỗ trợ kỹ thuật (Task 5.2).
 * Đáp ứng: UC-16, UC-17, UC-21, UC-22, UC-23 và BR-38..43, BR-47.
 */
public class TicketService extends BaseService {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int SLA_DUE_SOON_HOURS = 24;

    public static final String ASSIGNEE_ALL = "all";
    public static final String ASSIGNEE_MINE = "mine";

    public static final String TECHNICIAN_ROLE = PermissionService.ROLE_TECHNICAL;
    public static final String ROLE_BUYER = TicketValidator.ROLE_BUYER;

    // BR-40: Trạng thái phiếu
    public static final String STATUS_NEW = TicketValidator.STATUS_NEW;
    public static final String STATUS_IN_PROGRESS = TicketValidator.STATUS_IN_PROGRESS;
    public static final String STATUS_CLOSED = TicketValidator.STATUS_CLOSED;

    // BR-41: Mức ưu tiên
    public static final String PRIORITY_URGENT = TicketValidator.PRIORITY_URGENT;
    public static final String PRIORITY_HIGH = TicketValidator.PRIORITY_HIGH;
    public static final String PRIORITY_NORMAL = TicketValidator.PRIORITY_NORMAL;
    public static final String PRIORITY_LOW = TicketValidator.PRIORITY_LOW;

    // Mã lỗi & thông báo chuẩn
    public static final String ERR_FORBIDDEN = PermissionService.MSG_022;
    public static final String ERR_NOT_FOUND = "notfound";
    public static final String ERR_INVALID = "invalid";
    public static final String ERR_NOT_BUYER = "not_buyer";
    public static final String ERR_CONTRACT_MISMATCH = "contract_mismatch";
    public static final String ERR_STATUS_TRANSITION = TicketValidator.MSG_051;
    public static final String ERR_CREATE_FAILED = "create_failed";
    public static final String ERR_UPDATE_FAILED = "update_failed";
    public static final String ERR_CANNOT_DELETE = "cannot_delete";
    public static final String ERR_DELETE_FAILED = "delete_failed";

    private final TechnicalSupportTicketDAO ticketDAO;
    private final CustomerDAO customerDAO;
    private final ContractDAO contractDAO;
    private final EmployeeDAO employeeDAO;
    private final PermissionService permissionService;
    private final TicketValidator ticketValidator;

    public TicketService() {
        this(new TechnicalSupportTicketDAO(), new CustomerDAO(), new ContractDAO(),
                new EmployeeDAO(), null, new TicketValidator());
    }

    public TicketService(TechnicalSupportTicketDAO ticketDAO, CustomerDAO customerDAO,
            ContractDAO contractDAO, EmployeeDAO employeeDAO) {
        this(ticketDAO, customerDAO, contractDAO, employeeDAO, null, new TicketValidator());
    }

    public TicketService(TechnicalSupportTicketDAO ticketDAO, CustomerDAO customerDAO,
            ContractDAO contractDAO, EmployeeDAO employeeDAO,
            PermissionService permissionService, TicketValidator ticketValidator) {
        this.ticketDAO = ticketDAO != null ? ticketDAO : new TechnicalSupportTicketDAO();
        this.customerDAO = customerDAO != null ? customerDAO : new CustomerDAO();
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.employeeDAO = employeeDAO != null ? employeeDAO : new EmployeeDAO();
        this.permissionService = permissionService != null
                ? permissionService
                : new PermissionService(this.employeeDAO);
        this.ticketValidator = ticketValidator != null ? ticketValidator : new TicketValidator();
    }

    // ------------------------------------------------------------------
    // DTO / Value Objects
    // ------------------------------------------------------------------

    public static class TicketDetail {
        private final TechnicalRequest ticket;
        private final List<TechnicalRequestHistory> history;
        private final boolean canEdit;
        private final boolean canDelete;

        public TicketDetail(TechnicalRequest ticket, List<TechnicalRequestHistory> history,
                boolean canEdit, boolean canDelete) {
            this.ticket = ticket;
            this.history = history != null ? history : Collections.emptyList();
            this.canEdit = canEdit;
            this.canDelete = canDelete;
        }

        public TechnicalRequest getTicket() { return ticket; }
        public List<TechnicalRequestHistory> getHistory() { return history; }
        public boolean isCanEdit() { return canEdit; }
        public boolean isCanDelete() { return canDelete; }
    }

    // ------------------------------------------------------------------
    // BR-42: Kiểm tra SLA quá hạn & sắp đến hạn
    // ------------------------------------------------------------------

    /**
     * BR-42: Phiếu bị coi là quá hạn SLA khi chưa đóng (status != Đã đóng),
     * có hạn xử lý (slaDeadline != null) và thời điểm hiện tại đã vượt qua slaDeadline.
     */
    public boolean isSlaOverdue(TechnicalRequest ticket) {
        return isSlaOverdue(ticket, new Timestamp(System.currentTimeMillis()));
    }

    public boolean isSlaOverdue(TechnicalRequest ticket, Timestamp asOf) {
        if (ticket == null || ticket.getSlaDeadline() == null || asOf == null) {
            return false;
        }
        if (STATUS_CLOSED.equals(ticket.getStatus())) {
            return false;
        }
        return asOf.after(ticket.getSlaDeadline());
    }

    /**
     * BR-42 & BR-17: Phiếu chưa đóng có hạn SLA đã quá hoặc còn trong vòng 24 giờ tới.
     */
    public boolean isSlaDueSoon(TechnicalRequest ticket) {
        return isSlaDueSoon(ticket, new Timestamp(System.currentTimeMillis()));
    }

    public boolean isSlaDueSoon(TechnicalRequest ticket, Timestamp asOf) {
        if (ticket == null || ticket.getSlaDeadline() == null || asOf == null) {
            return false;
        }
        if (STATUS_CLOSED.equals(ticket.getStatus())) {
            return false;
        }
        Instant threshold = asOf.toInstant().plus(SLA_DUE_SOON_HOURS, ChronoUnit.HOURS);
        return !ticket.getSlaDeadline().toInstant().isAfter(threshold);
    }

    public List<TechnicalRequest> findOverdueOrDueSoon() {
        return ticketDAO.findOverdueOrDueSoon();
    }

    public int countOverdueOrDueSoon() {
        return ticketDAO.countOverdueOrDueSoon();
    }

    public int countOverdueOrDueSoon(List<Integer> provinceIds, List<Integer> ownerIds) {
        return ticketDAO.countOverdueOrDueSoon(provinceIds, ownerIds);
    }

    public List<TechnicalRequest> findNeedingAttention(int limit, List<Integer> provinceIds,
            List<Integer> ownerIds) {
        return ticketDAO.findNeedingAttention(limit, provinceIds, ownerIds);
    }

    public Map<String, Integer> countStatusSummary(List<Integer> provinceIds, Period period,
            List<Integer> ownerIds) {
        return ticketDAO.countStatusSummary(provinceIds, period, ownerIds);
    }

    // ------------------------------------------------------------------
    // UC-16: Danh sách phiếu, bộ lọc & thống kê KPI
    // ------------------------------------------------------------------

    /**
     * UC-16: Chuẩn hóa bộ lọc người xử lý ("all", "mine" hoặc user_id).
     * Vắng tham số thì Kỹ thuật mặc định "mine", các vai trò khác mặc định "all".
     */
    public String resolveAssigneeFilter(String rawAssignee, User actor) {
        if (rawAssignee == null || rawAssignee.trim().isEmpty()) {
            return permissionService.isTechnical(actor) ? ASSIGNEE_MINE : ASSIGNEE_ALL;
        }
        String trimmed = rawAssignee.trim();
        if (ASSIGNEE_MINE.equals(trimmed)) {
            return ASSIGNEE_MINE;
        }
        Integer parsedId = parsePositiveInt(trimmed);
        return parsedId != null ? String.valueOf(parsedId) : ASSIGNEE_ALL;
    }

    /**
     * UC-16: Giải quyết user_id cần lọc theo bộ lọc người xử lý (null = tất cả).
     */
    public Integer resolveAssigneeId(String assigneeFilter, User actor) {
        if (ASSIGNEE_MINE.equals(assigneeFilter)) {
            return actor != null && actor.getUserId() > 0 ? actor.getUserId() : null;
        }
        if (ASSIGNEE_ALL.equals(assigneeFilter) || assigneeFilter == null) {
            return null;
        }
        return parsePositiveInt(assigneeFilter);
    }

    public List<TechnicalRequest> findTickets(int page, int pageSize, String keyword,
            String statusFilter, String priorityFilter, Period period, Integer assigneeId) {
        int safePage = Math.max(1, page);
        int safeSize = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
        return ticketDAO.findAll(safePage, safeSize, keyword, statusFilter, priorityFilter, period, assigneeId);
    }

    public int countTickets(String keyword, String statusFilter, String priorityFilter,
            Period period, Integer assigneeId) {
        return ticketDAO.countAll(keyword, statusFilter, priorityFilter, period, assigneeId);
    }

    public Map<String, Integer> countListSummary(Period period, Integer assigneeId) {
        return ticketDAO.countListSummary(period, assigneeId);
    }

    public List<Enterprise> findBuyerCustomers(Integer keepCustomerId) {
        return customerDAO.findAllByRole(ROLE_BUYER, keepCustomerId);
    }

    public List<User> findActiveTechnicians(Integer keepUserId) {
        return employeeDAO.findActiveByRole(TECHNICIAN_ROLE, keepUserId);
    }

    // ------------------------------------------------------------------
    // UC-17: Xem chi tiết phiếu & lịch sử trạng thái
    // ------------------------------------------------------------------

    public TechnicalRequest findById(int ticketId) {
        return ticketId > 0 ? ticketDAO.findById(ticketId) : null;
    }

    public List<TechnicalRequestHistory> findHistoryByTicketId(int ticketId) {
        return ticketId > 0 ? ticketDAO.findHistoryByTicketId(ticketId) : Collections.emptyList();
    }

    public List<TechnicalRequest> findByEnterpriseId(int enterpriseId) {
        return enterpriseId > 0 ? ticketDAO.findByEnterpriseId(enterpriseId) : Collections.emptyList();
    }

    public ServiceResult<TicketDetail> getTicketDetail(int ticketId, User actor) {
        ServiceResult<Void> perm = permissionService.checkCanViewTickets(actor);
        if (!perm.isSuccess()) {
            return ServiceResult.fail(perm.getMessage());
        }
        TechnicalRequest ticket = findById(ticketId);
        if (ticket == null) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        List<TechnicalRequestHistory> history = ticketDAO.findHistoryByTicketId(ticketId);
        boolean canEdit = permissionService.canUpdateTicket(actor, ticket.getAssignedTechnicianId());
        boolean canDelete = permissionService.canDeleteTicket(actor)
                && ticketValidator.canDelete(ticket.getStatus())
                && ticketDAO.canDelete(ticketId);
        return ServiceResult.ok(new TicketDetail(ticket, history, canEdit, canDelete));
    }

    // ------------------------------------------------------------------
    // UC-21: Tạo phiếu hỗ trợ kỹ thuật (BR-38, BR-39, BR-41, BR-42, BR-43)
    // ------------------------------------------------------------------

    public ServiceResult<Integer> createTicket(TechnicalRequest ticket, User actor) {
        return createTicket(ticket, null, actor);
    }

    /**
     * UC-21: Tạo mới phiếu hỗ trợ kỹ thuật.
     * - Kiểm quyền Admin hoặc Sales (Kỹ thuật bị từ chối với MSG-022).
     * - BR-39: Trạng thái mặc định "Mới tiếp nhận" và ngày tạo là hôm nay.
     * - BR-43: Cờ bảo hành mặc định true (Có) khi null.
     * - BR-38: Mã phiếu hệ thống tự sinh duy nhất, lưu phiếu kèm dòng lịch sử mở đầu trong 1 giao dịch.
     */
    public ServiceResult<Integer> createTicket(TechnicalRequest ticket, Boolean isWarranty, User actor) {
        ServiceResult<Void> perm = permissionService.checkCanCreateTicket(actor);
        if (!perm.isSuccess()) {
            return ServiceResult.fail(perm.getMessage());
        }
        if (ticket == null) {
            return ServiceResult.fail(ERR_INVALID, ticketValidator.validateCreate(null).getErrors());
        }

        ValidationResult vr = ticketValidator.validateCreate(ticket);
        if (!vr.isValid()) {
            return ServiceResult.fail(ERR_INVALID, vr.getErrors());
        }
        if (!isAssignedTechnicianValid(ticket.getAssignedTechnicianId(), null)) {
            vr.addError(TicketValidator.FIELD_ASSIGNED_TECHNICIAN_ID, TicketValidator.MSG_TECHNICIAN_REQUIRED);
            return ServiceResult.fail(ERR_INVALID, vr.getErrors());
        }

        ServiceResult<Void> relCheck = validateCustomerAndContract(ticket, null, vr);
        if (!relCheck.isSuccess()) {
            return ServiceResult.fail(relCheck.getMessage(), vr.getErrors());
        }

        applySystemCreateDefaults(ticket, isWarranty, actor);
        String nextCode = ticketDAO.generateNextTicketCode();
        if (nextCode == null || nextCode.trim().isEmpty()) {
            return ServiceResult.fail(ERR_CREATE_FAILED);
        }
        ticket.setTicketCode(nextCode.trim());

        int newId = ticketDAO.insert(ticket);
        if (newId <= 0) {
            return ServiceResult.fail(ERR_CREATE_FAILED);
        }
        ticket.setTicketId(newId);
        return ServiceResult.ok(newId);
    }

    private void applySystemCreateDefaults(TechnicalRequest ticket, Boolean isWarranty, User actor) {
        ticket.setTicketType(ticket.getTicketType().trim());
        ticket.setPriority(ticket.getPriority().trim());
        ticket.setReceptionChannel(ticket.getReceptionChannel().trim());
        ticket.setDescription(ticket.getDescription().trim());
        // BR-39: Trạng thái và Ngày tạo luôn do hệ thống gán
        ticket.setStatus(STATUS_NEW);
        ticket.setCreatedDate(Date.valueOf(LocalDate.now()));
        ticket.setCreatedBy(actor.getUserId());
        ticket.setWarranty(ticketValidator.resolveWarranty(isWarranty));
        ticket.setRootCause(emptyToNull(ticket.getRootCause()));
        ticket.setCauseCategory(emptyToNull(ticket.getCauseCategory()));
        ticket.setHandlingPlan(emptyToNull(ticket.getHandlingPlan()));
        ticket.setResolutionSummary(null);
        ticket.setResolvedAt(null);
    }

    // ------------------------------------------------------------------
    // UC-22: Cập nhật phiếu & chuyển trạng thái (BR-39..43, NFR-19)
    // ------------------------------------------------------------------

    public ServiceResult<Void> updateTicket(TechnicalRequest ticket, String internalNote, User actor) {
        return updateTicket(ticket, internalNote, true, true, actor);
    }

    /**
     * UC-22: Cập nhật phiếu hỗ trợ kỹ thuật.
     * - Kỹ thuật chỉ được sửa phiếu giao cho chính mình (vi phạm -> MSG-022) và chỉ sửa các trường kỹ thuật.
     * - BR-40: Chuyển trạng thái một chiều Mới tiếp nhận -> Đang xử lý -> Đã đóng (vi phạm -> MSG-051).
     * - BR-40: Ghi thời điểm hoàn tất (resolvedAt) lần đầu chuyển sang Đã đóng; giữ nguyên ở các lần sửa sau.
     * - Lịch sử đổi trạng thái và bản ghi phiếu được ghi trong cùng một giao dịch ở DAO.
     */
    public ServiceResult<Void> updateTicket(TechnicalRequest input, String internalNote,
            boolean contractPickerUsable, boolean slaClearedOnPurpose, User actor) {
        if (input == null || input.getTicketId() <= 0) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        TechnicalRequest existing = ticketDAO.findById(input.getTicketId());
        if (existing == null) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }

        ServiceResult<Void> perm = permissionService.checkCanUpdateTicket(actor, existing.getAssignedTechnicianId());
        if (!perm.isSuccess()) {
            return ServiceResult.fail(perm.getMessage());
        }

        boolean fullAccess = permissionService.canCreateTicket(actor);
        TechnicalRequest merged = mergeForUpdate(input, existing, fullAccess, contractPickerUsable, slaClearedOnPurpose);

        ServiceResult<Void> validation = validateUpdateRequest(merged, existing, fullAccess);
        if (!validation.isSuccess()) {
            return validation;
        }

        applyResolvedTimestamp(merged, existing);
        boolean updated = ticketDAO.update(merged, actor.getUserId(), emptyToNull(internalNote));
        if (!updated) {
            return ServiceResult.fail(ERR_UPDATE_FAILED);
        }
        syncUpdatedFields(input, merged);
        return ServiceResult.ok(null);
    }

    private void syncUpdatedFields(TechnicalRequest target, TechnicalRequest source) {
        copyGeneralFieldsFromExisting(target, source);
        target.setTicketCode(source.getTicketCode());
        target.setCreatedBy(source.getCreatedBy());
        target.setCreatedDate(source.getCreatedDate());
        target.setStatus(source.getStatus());
        target.setRootCause(source.getRootCause());
        target.setCauseCategory(source.getCauseCategory());
        target.setHandlingPlan(source.getHandlingPlan());
        target.setResolutionSummary(source.getResolutionSummary());
        target.setResolvedAt(source.getResolvedAt());
    }

    private TechnicalRequest mergeForUpdate(TechnicalRequest input, TechnicalRequest existing,
            boolean fullAccess, boolean contractPickerUsable, boolean slaClearedOnPurpose) {
        TechnicalRequest target = new TechnicalRequest();
        target.setTicketId(existing.getTicketId());
        target.setTicketCode(existing.getTicketCode());
        target.setCreatedBy(existing.getCreatedBy());
        target.setCreatedDate(existing.getCreatedDate());

        if (fullAccess) {
            target.setEnterpriseId(input.getEnterpriseId());
            target.setContractId(input.getContractId() != null || contractPickerUsable
                    ? input.getContractId() : existing.getContractId());
            target.setTicketType(emptyToNull(input.getTicketType()));
            target.setPriority(emptyToNull(input.getPriority()));
            target.setReceptionChannel(emptyToNull(input.getReceptionChannel()));
            target.setSlaDeadline(input.getSlaDeadline() != null || slaClearedOnPurpose
                    ? input.getSlaDeadline() : existing.getSlaDeadline());
            target.setAssignedTechnicianId(input.getAssignedTechnicianId());
            target.setDescription(emptyToNull(input.getDescription()));
            target.setWarranty(input.isWarranty());
        } else {
            copyGeneralFieldsFromExisting(target, existing);
        }

        target.setStatus(emptyToNull(input.getStatus()));
        target.setRootCause(emptyToNull(input.getRootCause()));
        target.setCauseCategory(emptyToNull(input.getCauseCategory()));
        target.setHandlingPlan(emptyToNull(input.getHandlingPlan()));
        target.setResolutionSummary(emptyToNull(input.getResolutionSummary()));
        return target;
    }

    private void copyGeneralFieldsFromExisting(TechnicalRequest target, TechnicalRequest existing) {
        target.setEnterpriseId(existing.getEnterpriseId());
        target.setContractId(existing.getContractId());
        target.setTicketType(existing.getTicketType());
        target.setPriority(existing.getPriority());
        target.setReceptionChannel(existing.getReceptionChannel());
        target.setSlaDeadline(existing.getSlaDeadline());
        target.setAssignedTechnicianId(existing.getAssignedTechnicianId());
        target.setDescription(existing.getDescription());
        target.setWarranty(existing.isWarranty());
    }

    private ServiceResult<Void> validateUpdateRequest(TechnicalRequest merged, TechnicalRequest existing,
            boolean fullAccess) {
        ValidationResult vr = fullAccess
                ? ticketValidator.validateUpdate(merged, existing)
                : validateTechnicianFields(merged, existing);
        if (!vr.isValid()) {
            String statusErr = vr.getErrors().get(TicketValidator.FIELD_STATUS);
            if (TicketValidator.MSG_051.equals(statusErr)) {
                return ServiceResult.fail(ERR_STATUS_TRANSITION, vr.getErrors());
            }
            return ServiceResult.fail(ERR_INVALID, vr.getErrors());
        }
        if (!fullAccess) {
            return ServiceResult.ok(null);
        }
        if (!isAssignedTechnicianValid(merged.getAssignedTechnicianId(), existing.getAssignedTechnicianId())) {
            vr.addError(TicketValidator.FIELD_ASSIGNED_TECHNICIAN_ID, TicketValidator.MSG_TECHNICIAN_REQUIRED);
            return ServiceResult.fail(ERR_INVALID, vr.getErrors());
        }
        return validateCustomerAndContract(merged, existing.getEnterpriseId(), vr);
    }

    private ValidationResult validateTechnicianFields(TechnicalRequest merged, TechnicalRequest existing) {
        ValidationResult vr = new ValidationResult();
        if (merged.getStatus() == null || merged.getStatus().trim().isEmpty()) {
            vr.addError(TicketValidator.FIELD_STATUS, TicketValidator.MSG_STATUS_REQUIRED);
        } else if (!ticketValidator.isAllowedStatus(merged.getStatus())) {
            vr.addError(TicketValidator.FIELD_STATUS, TicketValidator.MSG_STATUS_INVALID);
        } else if (existing != null && existing.getStatus() != null && !existing.getStatus().trim().isEmpty()
                && !ticketValidator.isValidStatusTransition(existing.getStatus(), merged.getStatus())) {
            vr.addError(TicketValidator.FIELD_STATUS, TicketValidator.MSG_051);
        }
        if (merged.getCauseCategory() != null
                && merged.getCauseCategory().trim().length() > TicketValidator.MAX_CAUSE_CATEGORY_LENGTH) {
            vr.addError(TicketValidator.FIELD_CAUSE_CATEGORY, TicketValidator.MSG_REQUIRED);
        }
        return vr;
    }

    private boolean isAssignedTechnicianValid(int technicianId, Integer keepTechnicianId) {
        if (technicianId <= 0) {
            return false;
        }
        if (keepTechnicianId != null && technicianId == keepTechnicianId.intValue()) {
            return true;
        }
        User tech = employeeDAO.findById(technicianId);
        return tech != null && !tech.isDeleted()
                && tech.getRole() != null
                && TECHNICIAN_ROLE.equals(tech.getRole().getRoleName());
    }

    private void applyResolvedTimestamp(TechnicalRequest merged, TechnicalRequest existing) {
        if (STATUS_CLOSED.equals(merged.getStatus())) {
            boolean alreadyClosed = STATUS_CLOSED.equals(existing.getStatus()) && existing.getResolvedAt() != null;
            merged.setResolvedAt(alreadyClosed
                    ? existing.getResolvedAt()
                    : new Timestamp(System.currentTimeMillis()));
        } else {
            merged.setResolvedAt(null);
        }
    }

    // ------------------------------------------------------------------
    // UC-23 & BR-47: Xóa mềm phiếu hỗ trợ
    // ------------------------------------------------------------------

    /**
     * UC-23 & BR-47: Xóa mềm phiếu hỗ trợ kỹ thuật (is_deleted = 1).
     * - Chỉ Admin và Sales có quyền xóa (Kỹ thuật bị chặn với MSG-022).
     * - Chỉ xóa khi trạng thái là Mới tiếp nhận hoặc Đã đóng; chặn khi Đang xử lý.
     */
    public ServiceResult<Void> deleteTicket(int ticketId, User actor) {
        ServiceResult<Void> perm = permissionService.checkCanDeleteTicket(actor);
        if (!perm.isSuccess()) {
            return ServiceResult.fail(perm.getMessage());
        }
        if (ticketId <= 0) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        TechnicalRequest existing = ticketDAO.findById(ticketId);
        if (existing == null) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }

        ValidationResult vr = new ValidationResult();
        if (!ticketValidator.validateDelete(vr, existing.getStatus()) || !ticketDAO.canDelete(ticketId)) {
            return ServiceResult.fail(ERR_CANNOT_DELETE, vr.getErrors());
        }
        if (!ticketDAO.softDelete(ticketId)) {
            return ServiceResult.fail(ERR_DELETE_FAILED);
        }
        return ServiceResult.ok(null);
    }

    // ------------------------------------------------------------------
    // Helpers kiểm tra quan hệ Khách hàng & Hợp đồng
    // ------------------------------------------------------------------

    private ServiceResult<Void> validateCustomerAndContract(TechnicalRequest ticket,
            Integer keepEnterpriseId, ValidationResult vr) {
        if (keepEnterpriseId == null || ticket.getEnterpriseId() != keepEnterpriseId.intValue()) {
            List<String> roles = customerDAO.findRolesOf(ticket.getEnterpriseId());
            if (!ticketValidator.validateBuyerRole(vr, roles)) {
                return ServiceResult.fail(ERR_NOT_BUYER, vr.getErrors());
            }
        }
        if (ticket.getContractId() != null) {
            Contract contract = contractDAO.findById(ticket.getContractId());
            Integer contractEnterpriseId = contract != null ? contract.getEnterpriseId() : -1;
            if (!ticketValidator.validateContractBelongsToCustomer(vr, ticket.getEnterpriseId(), contractEnterpriseId)) {
                return ServiceResult.fail(ERR_CONTRACT_MISMATCH, vr.getErrors());
            }
        }
        return ServiceResult.ok(null);
    }

    private static Integer parsePositiveInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }
}
