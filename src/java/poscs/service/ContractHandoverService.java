package poscs.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import poscs.dao.ContractDAO;
import poscs.dao.ContractHandoverDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Contract;
import poscs.model.ContractHandover;
import poscs.model.Department;
import poscs.model.User;

/**
 * Service xử lý nghiệp vụ Bàn giao phòng ban cho Hợp đồng (Task 4.11).
 * Đáp ứng: UC-48, UC-49 và các quy tắc phân quyền (ma trận SPEC, MSG-022),
 * toàn vẹn giao dịch (NFR-19).
 */
public class ContractHandoverService extends BaseService {

    public static final String ERR_FORBIDDEN = PermissionService.MSG_022;
    public static final String ERR_NOT_FOUND = "notfound";
    public static final String ERR_INVALID = "invalid";
    public static final String ERR_NO_DEPARTMENT = "handover_no_department";
    public static final String ERR_HANDOVER_PENDING = "handover_pending";
    public static final String ERR_HANDOVER_FROZEN = "handover_frozen";
    public static final String ERR_HANDOVER_FAILED = "handover_failed";
    public static final String ERR_MISSING_DONE_NOTE = "missing_done_note";
    public static final String ERR_NOTE_TOO_LONG = "note_too_long";
    public static final String ERR_COMPLETE_FAILED = "handover_done_failed";

    public static final String FIELD_DEPARTMENT_ID = "departmentId";
    public static final String FIELD_HANDOVER_NOTE = "handoverNote";
    public static final String FIELD_DONE_NOTE = "doneNote";

    public static final int HANDOVER_NOTE_MAX_LENGTH = ContractHandoverDAO.HANDOVER_NOTE_MAX_LENGTH; // 255
    public static final int DONE_NOTE_MAX_LENGTH = ContractHandoverDAO.DONE_NOTE_MAX_LENGTH;         // 500

    private final ContractHandoverDAO handoverDAO;
    private final ContractDAO contractDAO;
    private final EmployeeDAO employeeDAO;
    private final PermissionService permissionService;

    public ContractHandoverService() {
        this(new ContractHandoverDAO(), new ContractDAO(), new EmployeeDAO(), new PermissionService());
    }

    public ContractHandoverService(ContractHandoverDAO handoverDAO, ContractDAO contractDAO,
                                   EmployeeDAO employeeDAO, PermissionService permissionService) {
        this.handoverDAO = handoverDAO != null ? handoverDAO : new ContractHandoverDAO();
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.employeeDAO = employeeDAO != null ? employeeDAO : new EmployeeDAO();
        this.permissionService = permissionService != null ? permissionService : new PermissionService();
    }

    /**
     * Khởi tạo bàn giao hợp đồng cho một hoặc nhiều phòng ban [UC-48].
     * Tác nhân: Admin hoặc Sales quản lý vùng.
     */
    public ServiceResult<Integer> initiateHandover(int contractId, List<Integer> departmentIds,
                                                   String handoverNote, User actor) {
        if (!permissionService.canInitiateHandover(actor)) {
            return ServiceResult.fail(ERR_FORBIDDEN);
        }
        if (contractId <= 0) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        Contract contract = contractDAO.findById(contractId);
        if (contract == null || contract.isDeleted()) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        if (contract.isFrozen()) {
            return ServiceResult.fail(ERR_HANDOVER_FROZEN);
        }
        ServiceResult<Void> validation = validateInitiateInput(departmentIds, handoverNote);
        if (!validation.isSuccess()) {
            return ServiceResult.fail(validation.getMessage(), validation.getFieldErrors());
        }
        int result = handoverDAO.handOverToDepartments(contractId, departmentIds, handoverNote, actor.getUserId());
        return handleHandoverDaoResult(result, departmentIds.size());
    }

    private ServiceResult<Void> validateInitiateInput(List<Integer> departmentIds, String note) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return ServiceResult.fail(ERR_NO_DEPARTMENT,
                    Map.of(FIELD_DEPARTMENT_ID, "Vui lòng chọn ít nhất một phòng ban nhận bàn giao."));
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer id : departmentIds) {
            if (id == null || id <= 0 || !seen.add(id)) {
                return ServiceResult.fail(ERR_INVALID,
                        Map.of(FIELD_DEPARTMENT_ID, "Danh sách phòng ban không hợp lệ hoặc có phòng trùng lặp."));
            }
        }
        if (note != null && note.trim().length() > HANDOVER_NOTE_MAX_LENGTH) {
            return ServiceResult.fail(ERR_NOTE_TOO_LONG,
                    Map.of(FIELD_HANDOVER_NOTE, "Ghi chú bàn giao không được vượt quá " + HANDOVER_NOTE_MAX_LENGTH + " ký tự."));
        }
        return ServiceResult.ok(null);
    }

    private ServiceResult<Integer> handleHandoverDaoResult(int result, int expectedCount) {
        if (result == ContractHandoverDAO.HANDOVER_ALREADY_PENDING) {
            return ServiceResult.fail(ERR_HANDOVER_PENDING);
        }
        if (result == ContractHandoverDAO.HANDOVER_FROZEN) {
            return ServiceResult.fail(ERR_HANDOVER_FROZEN);
        }
        if (result <= 0) {
            return ServiceResult.fail(ERR_HANDOVER_FAILED);
        }
        return ServiceResult.ok(result, "Khởi tạo bàn giao thành công cho " + result + " phòng ban.");
    }

    /**
     * Xác nhận xử lý bàn giao [UC-49].
     * Tác nhân: Admin hoặc nhân viên thuộc đúng phòng ban nhận bàn giao.
     */
    public ServiceResult<Void> completeHandover(int handoverId, int departmentId,
                                                String doneNote, User actor) {
        return completeHandover(0, handoverId, departmentId, doneNote, actor);
    }

    /**
     * Xác nhận xử lý bàn giao có kiểm tra hợp đồng [UC-49].
     */
    public ServiceResult<Void> completeHandover(int contractId, int handoverId, int departmentId,
                                                String doneNote, User actor) {
        if (handoverId <= 0 || departmentId <= 0 || contractId < 0) {
            return ServiceResult.fail(ERR_INVALID);
        }
        ContractHandover targetHandover = findHandoverTarget(contractId, handoverId);
        if (targetHandover == null) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        int actualDeptId = targetHandover.getDepartmentId();
        if (departmentId != actualDeptId) {
            return ServiceResult.fail(ERR_INVALID);
        }
        if (!permissionService.canConfirmHandover(actor, actualDeptId)) {
            return ServiceResult.fail(ERR_FORBIDDEN);
        }
        if (targetHandover.getDoneAt() != null) {
            return ServiceResult.fail(ERR_COMPLETE_FAILED);
        }
        if (contractId > 0) {
            Contract contract = contractDAO.findById(contractId);
            if (contract == null || contract.isDeleted()) {
                return ServiceResult.fail(ERR_NOT_FOUND);
            }
        }
        ServiceResult<Void> noteValidation = validateDoneNote(doneNote);
        if (!noteValidation.isSuccess()) {
            return noteValidation;
        }
        boolean ok = handoverDAO.completeHandover(handoverId, actor.getUserId(), doneNote.trim());
        if (!ok) {
            return ServiceResult.fail(ERR_COMPLETE_FAILED);
        }
        return ServiceResult.ok(null, "Xác nhận xử lý bàn giao thành công.");
    }

    private ContractHandover findHandoverTarget(int contractId, int handoverId) {
        if (contractId > 0) {
            List<ContractHandover> list = handoverDAO.findHandoversOf(contractId);
            if (list != null) {
                for (ContractHandover h : list) {
                    if (h.getHandoverId() == handoverId) {
                        return h;
                    }
                }
            }
            return null;
        }
        List<ContractHandover> pending = handoverDAO.findPendingHandovers(null);
        if (pending != null) {
            for (ContractHandover h : pending) {
                if (h.getHandoverId() == handoverId) {
                    return h;
                }
            }
        }
        return null;
    }

    private ServiceResult<Void> validateDoneNote(String doneNote) {
        if (doneNote == null || doneNote.trim().isEmpty()) {
            return ServiceResult.fail(ERR_MISSING_DONE_NOTE,
                    Map.of(FIELD_DONE_NOTE, "Ghi chú xác nhận xử lý là bắt buộc."));
        }
        if (doneNote.trim().length() > DONE_NOTE_MAX_LENGTH) {
            return ServiceResult.fail(ERR_NOTE_TOO_LONG,
                    Map.of(FIELD_DONE_NOTE, "Ghi chú xử lý không được vượt quá " + DONE_NOTE_MAX_LENGTH + " ký tự."));
        }
        return ServiceResult.ok(null);
    }

    /**
     * Danh sách chặng bàn giao của một hợp đồng [UC-48, UC-49].
     */
    public List<ContractHandover> findHandoversOf(int contractId) {
        if (contractId <= 0) {
            return Collections.emptyList();
        }
        return handoverDAO.findHandoversOf(contractId);
    }

    /**
     * Danh sách chặng bàn giao đang chờ xử lý theo phạm vi người phụ trách [UC-48, UC-49].
     */
    public List<ContractHandover> findPendingHandovers(User actor, boolean viewMine) {
        if (actor == null) {
            return Collections.emptyList();
        }
        List<Integer> ownerIds = null;
        if (viewMine) {
            ownerIds = employeeDAO.findTeamUserIds(actor.getUserId());
        }
        return handoverDAO.findPendingHandovers(ownerIds);
    }

    /**
     * Tổng hợp số liệu hàng đợi bàn giao cho Dashboard/View [UC-48, UC-49].
     */
    public HandoverQueueOverview getQueueOverview(List<ContractHandover> pendingHandovers) {
        Map<String, Integer> byDept = new LinkedHashMap<>();
        long slowest = 0;
        if (pendingHandovers != null) {
            for (ContractHandover h : pendingHandovers) {
                if (h.getDepartmentName() != null) {
                    byDept.merge(h.getDepartmentName(), 1, Integer::sum);
                }
                slowest = Math.max(slowest, h.getDaysWaiting());
            }
        }
        return new HandoverQueueOverview(byDept, slowest, pendingHandovers != null ? pendingHandovers.size() : 0);
    }

    /**
     * Danh sách toàn bộ phòng ban phục vụ giao diện lọc và phân công.
     */
    public List<Department> findAllDepartments() {
        return employeeDAO.findAllDepartments();
    }

    /**
     * DTO tổng hợp trạng thái hàng đợi bàn giao.
     */
    public static final class HandoverQueueOverview {
        private final Map<String, Integer> countByDepartment;
        private final long slowestDays;
        private final int totalScope;

        public HandoverQueueOverview(Map<String, Integer> countByDepartment, long slowestDays, int totalScope) {
            this.countByDepartment = Collections.unmodifiableMap(countByDepartment);
            this.slowestDays = slowestDays;
            this.totalScope = totalScope;
        }

        public Map<String, Integer> getCountByDepartment() {
            return countByDepartment;
        }

        public long getSlowestDays() {
            return slowestDays;
        }

        public int getTotalScope() {
            return totalScope;
        }
    }
}
