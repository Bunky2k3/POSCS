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

    /** true nếu user đang đăng nhập có quyền Full (tạo/sửa/xoá) trên resource này. */
    public static boolean hasFullAccess(HttpServletRequest request, Resource resource) {
        User user = currentUser(request);
        if (user == null || user.getRole() == null) {
            return false;
        }
        Set<String> allowedRoles = FULL_ACCESS_ROLES.get(resource);
        return allowedRoles != null && allowedRoles.contains(user.getRole().getRoleName());
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

    /**
     * Ngoại lệ riêng cho TICKET: role "Kỹ thuật" chỉ View only trên toàn bộ
     * ticket (không có trong FULL_ACCESS_ROLES), nhưng vẫn cần tự cập nhật
     * tiến độ/trạng thái của đúng phiếu đang được giao cho mình -- theo yêu
     * cầu nghiệp vụ (vai trò Technical: "Handling assigned technical
     * requests, updating progress and status"). Không cấp Full access vì họ
     * không được đổi khách hàng/hợp đồng/độ ưu tiên/người xử lý của ticket.
     */
    public static boolean canUpdateAssignedTicket(HttpServletRequest request, TechnicalRequest ticket) {
        User user = currentUser(request);
        if (user == null || user.getRole() == null || ticket == null) {
            return false;
        }
        return "Kỹ thuật".equals(user.getRole().getRoleName())
                && ticket.getAssignedTechnicianId() == user.getUserId();
    }
}
