package poscs.common;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import poscs.model.TechnicalRequest;
import poscs.model.User;

/**
 * Enforce ma trận phân quyền ở PERMISSIONS.md phía server, không chỉ ẩn nút
 * bấm ở JSP -- theo đúng lưu ý "Notes for implementation" của tài liệu đó.
 * AuthenticationFilter đã đảm bảo request tới đây luôn có currentUser (đã
 * đăng nhập); class này chỉ lo phần "role nào được Full (create/update/
 * delete), role nào chỉ View only" cho từng loại tài nguyên.
 */
public final class AccessControl {

    private AccessControl() {
    }

    public enum Resource {
        CUSTOMER, CONTRACT, PRODUCT, TICKET, EMPLOYEE
    }

    /** role_name của quản trị viên trong bảng roles -- xem db/migrations/V2. */
    private static final String ROLE_ADMIN = "Admin";

    // role_name nào được Full (tạo/sửa/xoá) cho từng Resource -- role không có
    // trong danh sách coi như chỉ View only (hoặc No access, với EMPLOYEE).
    // Khớp đúng bảng "Access matrix" trong PERMISSIONS.md.
    private static final Map<Resource, Set<String>> FULL_ACCESS_ROLES = Map.of(
            Resource.CUSTOMER, Set.of("Admin", "Sales"),
            Resource.CONTRACT, Set.of("Admin", "Sales"),
            Resource.PRODUCT, Set.of("Admin", "Kỹ thuật"),
            Resource.TICKET, Set.of("Admin", "CSKH"),
            Resource.EMPLOYEE, Set.of("Admin")
    );

    /** Lấy User đang đăng nhập từ session, hoặc null nếu chưa đăng nhập. */
    public static User currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object attr = session != null ? session.getAttribute("currentUser") : null;
        return attr instanceof User ? (User) attr : null;
    }

    // Tài nguyên mà cây tổ chức có tiếng nói: cấp dưới chỉ được xem, dù vai
    // trò của họ vốn có quyền Full. Khớp hai ô đánh dấu trong "Access matrix"
    // của PERMISSIONS.md. Phiếu hỗ trợ và Sản phẩm KHÔNG nằm ở đây -- kỹ
    // thuật viên là cấp dưới của ai đó không làm họ mất quyền ghi trên phiếu
    // được giao cho mình.
    private static final Set<Resource> HIERARCHY_RESTRICTED = Set.of(
            Resource.CUSTOMER, Resource.CONTRACT
    );

    /** true nếu user đang đăng nhập có quyền Full (tạo/sửa/xoá) trên resource này. */
    public static boolean hasFullAccess(HttpServletRequest request, Resource resource) {
        User user = currentUser(request);
        if (user == null || user.getRole() == null) {
            return false;
        }
        Set<String> allowedRoles = FULL_ACCESS_ROLES.get(resource);
        if (allowedRoles == null || !allowedRoles.contains(user.getRole().getRoleName())) {
            return false;
        }
        return !isRestrictedBySubordinateRank(user, resource);
    }

    /**
     * Tầng thứ hai của phân quyền: vai trò cho đủ quyền rồi, nhưng VỊ TRÍ
     * trong cây tổ chức lấy bớt lại.
     *
     * Theo yêu cầu khách hàng (14/09/2026): trên Khách hàng và Hợp đồng, cấp
     * trên là người tác động lên dữ liệu -- kể cả dữ liệu của cấp dưới -- còn
     * cấp dưới chỉ xem, muốn đổi gì thì gửi yêu cầu lên. Xem PERMISSIONS.md.
     *
     * Ba điều cần biết khi đọc hàm này:
     *
     *  - Admin không bao giờ bị siết. Quản trị hệ thống mà bị cây tổ chức
     *    chặn thì không còn ai gỡ được khi cây bị nhập sai.
     *  - "Cấp dưới" = có manager_id, chứ không phải một role riêng. Nhân viên
     *    cầm tỉnh và quản lý vùng cùng mang role Sales, chỉ khác nhau ở chỗ
     *    đứng trong cây -- đó chính là lý do phải có cột manager_id.
     *  - Chưa xếp vào cây (manager_id null) thì KHÔNG bị siết. Nhờ vậy bật
     *    tính năng lên không cướp quyền của ai; việc siết chỉ bắt đầu với
     *    từng người khi họ được gán cấp trên thật.
     */
    private static boolean isRestrictedBySubordinateRank(User user, Resource resource) {
        if (!HIERARCHY_RESTRICTED.contains(resource)) {
            return false;
        }
        if (ROLE_ADMIN.equals(user.getRole().getRoleName())) {
            return false;
        }
        return user.isSubordinate();
    }

    /**
     * Chặn action tạo/sửa/xoá nếu role hiện tại chỉ được View only (hoặc No
     * access) trên resource này -- trả về 403 và false. Gọi ở đầu mỗi
     * handleCreate/handleUpdate/handleDelete, TRƯỚC khi đọc tham số hay đụng
     * tới DB, để không role nào bypass được bằng cách POST thẳng vào URL.
     */
    public static boolean requireFullAccess(HttpServletRequest request, HttpServletResponse response,
            Resource resource) throws IOException {
        if (hasFullAccess(request, resource)) {
            return true;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "Bạn không có quyền thực hiện thao tác này.");
        return false;
    }

    /** true nếu người đang đăng nhập giữ vai trò Admin. */
    public static boolean isAdmin(HttpServletRequest request) {
        User user = currentUser(request);
        return user != null && user.getRole() != null && ROLE_ADMIN.equals(user.getRole().getRoleName());
    }

    /** Giá trị tham số "view" trên URL hai màn hình danh sách. */
    public static final String VIEW_MINE = "mine";
    public static final String VIEW_ALL = "all";

    /** Vai duy nhất bị thu hẹp danh sách về phần việc của mình. */
    private static final String ROLE_SALES = "Sales";

    /**
     * Phạm vi mặc định khi mở danh sách khách hàng / hợp đồng.
     *
     * <p>CHỈ Sales bị thu hẹp. Admin nhìn toàn bộ (họ quản trị, không cầm khách).
     * Kỹ thuật và CSKH cũng nhìn toàn bộ: họ không đứng tên khách nào cả, thu hẹp
     * theo "khách của tôi" là họ thấy đúng 0 dòng và hết làm việc được.
     *
     * <p>Thu hẹp này là MẶC ĐỊNH, KHÔNG phải rào quyền: còn {@code view=all} để xem
     * toàn chi nhánh, và trang chi tiết từng bản ghi không chặn gì thêm. Ai cần rào
     * thật thì phải chặn ở tầng quyền, không phải ở đây.
     *
     * @param request   để đọc người đăng nhập và tham số "view"
     * @param teamIds   mình + cấp dưới (EmployeeDAO.findTeamUserIds)
     * @param provinceIds địa bàn được giao; rỗng = chưa giao, không siết theo địa bàn
     */
    public static ListScope listScope(HttpServletRequest request,
            java.util.function.Supplier<java.util.List<Integer>> teamIds,
            java.util.function.Supplier<java.util.List<Integer>> provinceIds) {
        if (!VIEW_MINE.equals(defaultView(request))) {
            return ListScope.all();
        }
        return ListScope.of(teamIds.get(), provinceIds.get());
    }

    /**
     * "mine" hay "all" cho lần hiển thị này. Không có tham số trên URL = lần đầu mở
     * trang, lấy mặc định theo vai; có tham số thì nghe theo người dùng.
     *
     * <p>Cùng cách làm với tham số "scope" của Dashboard. Tên khác ("view") vì trên
     * danh sách hợp đồng "scope" ĐÃ mang nghĩa khác -- {@code scope=root} là "chỉ hợp
     * đồng gốc". Dùng lại là hai nghĩa chồng nhau trên cùng một URL.
     */
    public static String defaultView(HttpServletRequest request) {
        String param = request.getParameter("view");
        if (param != null) {
            return VIEW_ALL.equals(param) ? VIEW_ALL : VIEW_MINE;
        }
        User user = currentUser(request);
        boolean isSales = user != null && user.getRole() != null
                && ROLE_SALES.equals(user.getRole().getRoleName());
        return isSales ? VIEW_MINE : VIEW_ALL;
    }

    /**
     * Chặn mọi vai trò trừ Admin -- trả về 403 và false.
     *
     * Khác requireFullAccess(...) ở chỗ nó không gắn với tài nguyên nghiệp vụ
     * nào trong ma trận: dùng cho các chức năng quản trị hệ thống (nhật ký máy
     * chủ chẳng hạn) vốn không phải Customer/Contract/Product/Ticket/Employee,
     * và không có bậc "View only" cho ai khác.
     */
    public static boolean requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (isAdmin(request)) {
            return true;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "Chức năng này chỉ dành cho quản trị viên.");
        return false;
    }

    /**
     * Ngoại lệ riêng cho TICKET: role "Kỹ thuật" chỉ View only trên toàn bộ
     * ticket (không có trong FULL_ACCESS_ROLES), nhưng vẫn cần tự cập nhật
     * tiến độ/trạng thái của đúng phiếu đang được giao cho mình -- theo yêu
     * cầu nghiệp vụ (vai trò Technical: "Handling assigned technical
     * requests, updating progress and status"). Không cấp Full access vì họ
     * không được đổi khách hàng/hợp đồng/độ ưu tiên/người xử lý của ticket.
     *
     * Hàm này chỉ trả lời "có phải kỹ thuật viên của đúng phiếu này không";
     * DANH SÁCH CỘT được sửa nằm ở TechnicalSupportTicketController.
     * handleUpdate (hiện là status, root_cause, cause_category, handling_plan,
     * resolution_summary -- xem PERMISSIONS.md). Ba cột nguyên nhân/phương
     * hướng nằm trong danh sách đó vì theo yêu cầu khách hàng, cả mạch chẩn
     * đoán lẫn hướng xử lý đều là phần do chính nhân viên kỹ thuật đánh giá.
     */
    /**
     * true nếu người này được xác nhận "đã xử lý xong" cho chặng bàn giao của
     * một phòng.
     *
     * <p>Kiểm theo PHÒNG BAN chứ không theo vai trò, khác mọi chỗ còn lại của
     * lớp này. Lý do: Kế toán và Dự án là hai phòng trong luồng bàn giao nhưng
     * KHÔNG có vai trò tương ứng trong `roles` -- người của hai phòng đó vẫn
     * mang một trong bốn vai cũ. Bắt theo vai thì hoặc phải đẻ thêm vai, hoặc
     * mở quyền hợp đồng cho cả những người không liên quan.
     *
     * <p>Admin xác nhận được mọi phòng: hai phòng mới hiện chưa có nhân sự nào,
     * mà luồng phải chạy được ngay hôm nay.
     */
    public static boolean canCompleteHandover(HttpServletRequest request, int departmentId) {
        User user = currentUser(request);
        if (user == null) {
            return false;
        }
        return isAdmin(request) || user.getDepartmentId() == departmentId;
    }

    public static boolean canUpdateAssignedTicket(HttpServletRequest request, TechnicalRequest ticket) {
        User user = currentUser(request);
        if (user == null || user.getRole() == null || ticket == null) {
            return false;
        }
        return "Kỹ thuật".equals(user.getRole().getRoleName())
                && ticket.getAssignedTechnicianId() == user.getUserId();
    }
}
