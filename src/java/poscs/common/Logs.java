package poscs.common;

import jakarta.servlet.http.HttpServletRequest;
import poscs.model.User;

/**
 * Tiện ích nhỏ cho việc ghi log ở tầng controller.
 *
 * Log của DAO nói được "câu SQL nào hỏng" nhưng không biết AI đang thao tác --
 * DAO không nhìn thấy request. Log của controller thì ngược lại. Ghép hai thứ
 * lại mới lần ra được một lỗi người dùng báo, nên mọi nhánh thất bại ở
 * controller đều kèm actor(request).
 */
public final class Logs {

    private Logs() {
    }

    /**
     * Người đang đăng nhập, dạng "username#userId" -- đủ để tra ngược trong
     * bảng users mà không cần in cả họ tên vào log. Trả về "?" khi không xác
     * định được (phiên vừa hết hạn, hoặc luồng công khai như quên mật khẩu).
     */
    public static String actor(HttpServletRequest request) {
        User user = AccessControl.currentUser(request);
        return user == null ? "?" : user.getUsername() + "#" + user.getUserId();
    }
}
