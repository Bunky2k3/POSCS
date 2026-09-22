-- Gộp vai CSKH vào Sales: xoá "CSKH" khỏi bảng roles, không còn là vai riêng.
--
-- CSKH chỉ khác Sales ở đúng một điểm trong ma trận quyền (Resource.TICKET),
-- và Sales đã có sẵn Full access ở đó ngay sau bước này -- xem
-- AccessControl.FULL_ACCESS_ROLES (đã sửa cùng lúc ở tầng Java) và
-- PERMISSIONS.md. Không có gì khác về CÁCH phiếu hỗ trợ hoạt động (tạo
-- phiếu, ngoại lệ kỹ thuật viên được giao, phạm vi "của tôi" theo hai cột) --
-- chỉ đổi TÊN VAI nào được phép làm việc đó.
--
-- Thứ tự bắt buộc: chuyển hết user đang mang role CSKH sang Sales TRƯỚC,
-- rồi mới xoá dòng CSKH khỏi roles -- ngược lại thì users.role_id (khoá
-- ngoại) trỏ vào một dòng sắp biến mất, DELETE sẽ bị FK chặn lại (đúng như
-- nó phải làm).

INSERT INTO schema_migrations (version) VALUES ('V37__merge_cskh_role_into_sales__ndat2003');

UPDATE `users`
SET `role_id` = (SELECT `role_id` FROM `roles` WHERE `role_name` = 'Sales')
WHERE `role_id` = (SELECT `role_id` FROM `roles` WHERE `role_name` = 'CSKH');

DELETE FROM `roles` WHERE `role_name` = 'CSKH';
