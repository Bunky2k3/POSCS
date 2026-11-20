-- Bỏ vai và phòng "Kỹ thuật" (Task 9.3): nhân viên vai Kỹ thuật sang Sales,
-- nhân viên phòng Kỹ thuật sang Kinh doanh, rồi xoá vai và phòng Kỹ thuật.
--
-- Lý do (chốt 19/11/2026, mục 13 "Quyết định đã chốt" của task_tracker): chi
-- nhánh thật không có phòng Kỹ thuật. Quản lý sản phẩm và xử lý phiếu hỗ trợ
-- chuyển sang Sales, nên từ nay chỉ còn ba vai Admin, Sales, Tổng hợp và ba
-- phòng Ban giám đốc, Kinh doanh, Tổng hợp (SPEC 3.1). Quyền nằm ở
-- PermissionService / AccessControl, bảng `roles` chỉ có tên vai -- xem
-- PERMISSIONS.md.
--
-- Làm như V37 (gộp vai CSKH vào Sales) + V38 (gộp phòng Chăm sóc khách hàng
-- vào Kinh doanh). Phiếu hỗ trợ giữ nguyên người xử lý: assigned_technician_id
-- vẫn trỏ đúng người cũ, giờ người đó mang vai Sales -- danh sách người xử lý
-- (Sales đang hoạt động) vẫn có họ, nên mở form sửa phiếu không bị trống ô.
--
-- Chặng bàn giao của phòng Kỹ thuật đã chuyển sang Tổng hợp ở V40 (Task 9.2).
-- Nếu DELETE phòng bị khoá ngoại contract_handovers.department_id chặn lại thì
-- có chặng bàn giao cho phòng Kỹ thuật được tạo SAU V40: dừng lại xem tay,
-- đừng xoá chặng đó.
--
-- Thứ tự bắt buộc như V37, V38: chuyển hết nhân viên sang vai / phòng mới
-- TRƯỚC, rồi mới xoá dòng cũ -- ngược lại DELETE bị khoá ngoại
-- (users.role_id, users.department_id) chặn lại.

INSERT INTO schema_migrations (version) VALUES ('V42__merge_kythuat_role_into_sales__ndat2003');

-- ---------------------------------------------------------------------------
-- 1. Vai Kỹ thuật sang Sales
-- ---------------------------------------------------------------------------
-- Không đụng manager_id: người có cấp trên thành "Sales có cấp trên" (chỉ xem
-- khách hàng, hợp đồng -- SPEC 3.2), nhưng quyền sản phẩm và phiếu hỗ trợ của
-- Sales không xét cấp trên, nên việc cũ của họ vẫn làm được.
UPDATE `users`
SET `role_id` = (SELECT `role_id` FROM `roles` WHERE `role_name` = 'Sales')
WHERE `role_id` IN (SELECT `role_id` FROM `roles` WHERE `role_name` = N'Kỹ thuật');

-- ---------------------------------------------------------------------------
-- 2. Phòng Kỹ thuật sang Kinh doanh
-- ---------------------------------------------------------------------------
UPDATE `users`
SET `department_id` = (SELECT `department_id` FROM `departments` WHERE `department_name` = N'Kinh doanh')
WHERE `department_id` = (SELECT `department_id` FROM `departments` WHERE `department_name` = N'Kỹ thuật');

-- ---------------------------------------------------------------------------
-- 3. Xoá vai và phòng Kỹ thuật (đã hết người)
-- ---------------------------------------------------------------------------
DELETE FROM `roles` WHERE `role_name` = N'Kỹ thuật';

DELETE FROM `departments` WHERE `department_name` = N'Kỹ thuật';
