# Workspace Rules: POSCS

Đọc theo thứ tự ưu tiên (khi mâu thuẫn, file đứng trên thắng):
1. `rules.md` (thư mục gốc): quy trình, lệnh Ant, ranh giới cấm, kiến trúc. **Bắt buộc đọc trước mỗi lượt.**
2. `docs/SPEC.md`: nguồn chân lý nghiệp vụ.
3. `task_tracker.md`: task hiện tại.
4. `.gemini/rules.md`: quy chuẩn chi tiết bổ sung.

## Core Tech Rules
1. **Jakarta EE 11 / Java 21**: luôn dùng `jakarta.*` (`jakarta.servlet.*`, `jakarta.mail.*`), không bao giờ dùng `javax.*`.
2. **Kiến trúc**: `view` / `controller` / `service` / `validator` / `dao` / `model` / `common`. Controller không chứa nghiệp vụ.
3. **Bảo mật**: CSRF token, PreparedStatement, RBAC qua `poscs.common.AccessControl`, mật khẩu băm BCrypt.
4. **Build bằng Ant, không dùng Maven.** Lệnh chính xác ở `rules.md` mục 0.