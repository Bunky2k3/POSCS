-- Nhân viên mới không còn bị bắt Admin điền hết CCCD/SĐT/giới tính/ngày sinh
-- ngay lúc tạo tài khoản -- bốn trường này là dữ liệu cá nhân, để chính nhân
-- viên tự khai lúc đăng nhập lần đầu (qua /updateProfile, form tự phục vụ đã
-- có sẵn) thì đúng và ít sai hơn Admin gõ/đoán hộ.
--
-- Nới NOT NULL cho 4 cột này. citizen_id/phone giữ nguyên UNIQUE -- MySQL cho
-- phép nhiều dòng NULL trên cột UNIQUE, không đụng ràng buộc "không trùng".
--
-- Thêm cờ must_change_password: đánh dấu mật khẩu hiện tại là mật khẩu TẠM do
-- Admin cấp (lúc tạo tài khoản, hoặc lúc Admin bấm "Gửi lại thông tin tài
-- khoản"), chưa phải mật khẩu do chính nhân viên đặt. AuthenticationFilter
-- dùng cờ này CỘNG với 4 cột vừa nới NULL để chặn mọi trang khác, ép nhân
-- viên qua /changePassword và /updateProfile trước, tới khi cả hai việc xong.
--
-- DEFAULT '0' để toàn bộ tài khoản demo/test hiện có KHÔNG bị chặn -- họ đã
-- có đủ CCCD/SĐT/giới tính/ngày sinh và mật khẩu không phải mật khẩu tạm.
-- Chỉ nhân viên tạo MỚI sau migration này mới bị set '1' (EmployeeDAO.insert),
-- và mỗi lần Admin cấp lại mật khẩu tạm (EmployeeDAO.updatePasswordHash).

INSERT INTO schema_migrations (version) VALUES ('V35__employee_self_onboarding__ndat2003');

ALTER TABLE `users`
  MODIFY COLUMN `gender` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  MODIFY COLUMN `date_of_birth` date DEFAULT NULL,
  MODIFY COLUMN `citizen_id` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  MODIFY COLUMN `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `must_change_password` tinyint(1) NOT NULL DEFAULT '0' AFTER `password_hash`;
