-- Tách "người phụ trách" của khách hàng thành hai vai: account_owner_id giữ
-- nguyên nghĩa người phụ trách CHÍNH (cấp trên), thêm support_owner_id là
-- người HỖ TRỢ (cấp dưới) cùng chăm khách đó.
--
-- Cột mới để NULL được, khác với account_owner_id (NOT NULL): khách hàng có
-- thể chỉ có một người phụ trách chính mà chưa cần người hỗ trợ, và toàn bộ
-- dữ liệu đã có từ trước rơi vào đúng trường hợp đó -- không phải điền bừa
-- một người để migration chạy được.
--
-- Không ràng buộc ở CSDL chuyện "hỗ trợ phải là cấp dưới của chính": bảng
-- users chưa có cột cấp trên nào (xem phân tích cây tổ chức), nên quan hệ
-- trên/dưới hiện chỉ là quy ước nghiệp vụ khi chọn người, chưa kiểm được
-- bằng khoá ngoại. Cái duy nhất chặn được ngay là hai vai không được trùng
-- một người, và chỗ đó enforce ở tầng ứng dụng.

INSERT INTO schema_migrations (version) VALUES ('V13__add_support_owner_to_enterprises__ndat2003');

ALTER TABLE `enterprises`
  ADD COLUMN `support_owner_id` int NULL AFTER `account_owner_id`,
  ADD CONSTRAINT `fk_enterprises_support_owner`
    FOREIGN KEY (`support_owner_id`) REFERENCES `users` (`user_id`);
