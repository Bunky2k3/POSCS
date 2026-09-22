-- Gộp phòng "Chăm sóc khách hàng" vào "Kinh doanh" -- cho khớp với việc vai
-- CSKH đã gộp vào Sales ở V37. Trước V37, phòng ban với vai trò soi gương
-- nhau (Sales-Kinh doanh, Kỹ thuật-Kỹ thuật, CSKH-Chăm sóc khách hàng, xem
-- V10); sau V37 mà không đổi tiếp phòng ban thì còn sót 4 người mang vai
-- Sales nhưng phòng "Chăm sóc khách hàng" -- không sai kỹ thuật gì (hai cột
-- role_id/department_id độc lập, phòng ban vốn không quyết định quyền, xem
-- PERMISSIONS.md) nhưng gây khó hiểu khi đọc lại sau này.
--
-- Cùng thứ tự bắt buộc như V37: chuyển hết nhân sự sang phòng mới TRƯỚC, rồi
-- mới xoá dòng phòng cũ -- ngược lại thì DELETE bị FK (users.department_id)
-- chặn lại.

INSERT INTO schema_migrations (version) VALUES ('V38__merge_cskh_department_into_kinhdoanh__ndat2003');

UPDATE `users`
SET `department_id` = (SELECT `department_id` FROM `departments` WHERE `department_name` = 'Kinh doanh')
WHERE `department_id` = (SELECT `department_id` FROM `departments` WHERE `department_name` = 'Chăm sóc khách hàng');

DELETE FROM `departments` WHERE `department_name` = 'Chăm sóc khách hàng';
