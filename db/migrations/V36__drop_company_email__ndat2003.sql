-- Bỏ khái niệm "email công ty" (users.email, dạng <username>@postef.com.vn):
-- hệ thống tự bịa ra chuỗi này lúc tạo nhân viên, chưa từng gắn với hộp thư
-- thật nào (không có DNS/MX, không nhận được mail) -- một địa chỉ email
-- thường phải đi kèm một bước ĐĂNG KÝ hộp thư thật, không phải chỉ ghép
-- chuỗi. Đăng nhập từ nay CHỈ bằng username (đã UNIQUE sẵn, không cần cột
-- email làm identifier thứ hai).
--
-- Hệ quả kéo theo, đã sửa cùng lúc ở tầng Java:
--   * EmployeeDAO.findByUsernameOrEmail -> findByUsername (bỏ nhánh OR email).
--   * EmployeeDAO.updatePasswordByEmail -> updatePasswordByUsername (đổi mật
--     khẩu tự phục vụ VÀ luồng quên mật khẩu đều khoá theo username).
--   * Quên mật khẩu (AuthenticationController) đổi ô nhập từ "email" sang
--     "username", và mã OTP giờ gửi tới personal_email của tài khoản tìm
--     được -- KHÔNG còn gửi tới chuỗi người dùng tự gõ như trước (trước đây
--     "email" nhập vào vừa dùng để tìm tài khoản vừa là nơi nhận OTP; giờ
--     tách hẳn: username để tìm, personal_email (đã lưu sẵn, không phải input)
--     để gửi).
--   * EmployeeDAO.existsByEmail xoá luôn -- không còn cột để kiểm trùng.
--
-- KHÔNG cần cập nhật gì cho account_owner/support_owner hay các bảng khác:
-- cột này chưa từng được FK từ đâu tới.

INSERT INTO schema_migrations (version) VALUES ('V36__drop_company_email__ndat2003');

ALTER TABLE `users` DROP COLUMN `email`;
