package poscs.service;

import poscs.dao.BranchInfoDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.BranchInfo;
import poscs.model.User;
import poscs.validator.BranchInfoValidator;
import poscs.validator.ValidationResult;

/**
 * Nghiệp vụ Thông tin chi nhánh (UC-55, SPEC 5.8): xem và sửa thông tin Giám
 * đốc chi nhánh (GĐCN), nguồn người ký bên mình trên hợp đồng (BR-56).
 *
 * <p>Bảng {@code branch_info} chỉ có một dòng gieo sẵn ở V39, nên chỉ có đọc và
 * sửa, không có tạo/xoá. Chỉ Admin được sửa: controller đã chặn bằng
 * {@code AccessControl.requireAdmin}, service kiểm lại qua
 * {@link PermissionService#isAdmin(User)} (đọc vai trò từ CSDL) để không lối
 * gọi nào bỏ qua được.
 */
public class BranchInfoService extends BaseService {

    /** MSG-057. */
    public static final String MSG_SAVED = "Đã lưu thông tin chi nhánh.";
    /** MSG-018: không đọc được dòng branch_info (lỗi CSDL, hoặc CSDL chưa chạy V39). */
    public static final String MSG_LOAD_FAILED = "Có lỗi khi tải dữ liệu. Vui lòng thử lại.";
    /** Lệnh UPDATE hỏng ở CSDL. SPEC không có mã MSG riêng cho trường hợp này. */
    public static final String MSG_SAVE_FAILED = "Không lưu được thông tin chi nhánh. Vui lòng thử lại.";
    /** MSG-022. */
    public static final String MSG_FORBIDDEN = PermissionService.MSG_022;

    private final BranchInfoDAO branchInfoDAO;
    private final EmployeeDAO employeeDAO;
    private final PermissionService permissionService;
    private final BranchInfoValidator validator;

    public BranchInfoService() {
        this(new BranchInfoDAO(), new EmployeeDAO());
    }

    private BranchInfoService(BranchInfoDAO branchInfoDAO, EmployeeDAO employeeDAO) {
        this(branchInfoDAO, employeeDAO, new PermissionService(employeeDAO), new BranchInfoValidator());
    }

    public BranchInfoService(BranchInfoDAO branchInfoDAO, EmployeeDAO employeeDAO,
            PermissionService permissionService, BranchInfoValidator validator) {
        this.branchInfoDAO = branchInfoDAO;
        this.employeeDAO = employeeDAO;
        this.permissionService = permissionService;
        this.validator = validator;
    }

    /**
     * Đọc thông tin chi nhánh.
     *
     * @return bản ghi đang lưu; {@code null} khi lỗi CSDL hoặc thiếu dòng id = 1
     */
    public BranchInfo get() {
        return branchInfoDAO.get();
    }

    /**
     * Họ tên Admin đã sửa thông tin chi nhánh lần cuối.
     *
     * @return {@code null} khi chưa ai sửa từ lúc gieo (updated_by NULL) hoặc
     *         không đọc được tài khoản đó
     */
    public String findUpdaterName(BranchInfo info) {
        if (info == null || info.getUpdatedBy() == null) {
            return null;
        }
        User updater = employeeDAO.findById(info.getUpdatedBy());
        return updater != null ? updater.getFullName() : null;
    }

    /**
     * Lưu thông tin chi nhánh (UC-55).
     *
     * <p>Mọi ô được trim, ô trống lưu thành NULL, rồi kiểm theo BR-57 trước khi
     * ghi. Dữ liệu sai thì không gọi DAO.
     *
     * @param info  dữ liệu Admin vừa gửi
     * @param actor người đang đăng nhập, ghi vào {@code updated_by}
     * @return thành công: bản đã chuẩn hoá kèm MSG-057; thất bại: MSG-022 khi
     *         không phải Admin, lỗi theo từng ô khi vi phạm BR-57, hoặc
     *         {@link #MSG_SAVE_FAILED} khi CSDL không ghi được
     */
    public ServiceResult<BranchInfo> update(BranchInfo info, User actor) {
        if (actor == null || !permissionService.isAdmin(actor)) {
            return failure(MSG_FORBIDDEN);
        }
        BranchInfo clean = normalize(info);
        ValidationResult validation = validator.validate(clean);
        if (!validation.isValid()) {
            return failure(validation);
        }
        if (!branchInfoDAO.update(clean, actor.getUserId())) {
            log.warning("Khong luu duoc thong tin chi nhanh (actorId=" + actor.getUserId() + ")");
            return failure(MSG_SAVE_FAILED);
        }
        return success(clean, MSG_SAVED);
    }

    /** Bản sao đã trim của bốn ô thông tin; ô trống thành {@code null}. */
    private static BranchInfo normalize(BranchInfo info) {
        BranchInfo clean = new BranchInfo();
        if (info == null) {
            return clean;
        }
        clean.setDirectorName(BranchInfoValidator.trimToNull(info.getDirectorName()));
        clean.setDirectorPosition(BranchInfoValidator.trimToNull(info.getDirectorPosition()));
        clean.setPhone(BranchInfoValidator.trimToNull(info.getPhone()));
        clean.setFax(BranchInfoValidator.trimToNull(info.getFax()));
        return clean;
    }
}
