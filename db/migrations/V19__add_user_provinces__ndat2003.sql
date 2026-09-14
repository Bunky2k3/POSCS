-- Phân công địa bàn: ai cầm tỉnh nào.
--
-- Cho tới giờ hệ thống chỉ biết ai phụ trách KHÁCH HÀNG nào
-- (enterprises.account_owner_id), còn tỉnh thì suy gián tiếp từ địa chỉ của
-- khách. Không có chỗ nào phát biểu "tỉnh này do người này cầm", nên mô hình
-- đã chốt với khách hàng -- tầng lá 1 tỉnh = 1 người, quản lý vùng suy ra từ
-- cấp dưới -- không diễn đạt được trong dữ liệu.
--
-- LƯU Ý VỀ TRẠNG THÁI: khách hàng CHƯA xác nhận hệ thống có phải tự gán người
-- phụ trách theo tỉnh hay không. Bảng này dựng theo giả định "địa bàn là thực
-- thể riêng", và phần dùng nó ở tầng giao diện cố ý làm dạng ĐIỀN SẴN chứ
-- không khoá chặt:
--
--   * khách muốn tự gán  -> đúng như vậy, người nhập chỉ việc bấm lưu;
--   * khách muốn tự chọn -> nó thành gợi ý tiện tay, không chặn ai, không
--                           phải gỡ bỏ gì;
--   * khách muốn khoá chặt -> thêm một lần kiểm ở CustomerController, đúng
--                           một chỗ.
--
-- Chỉ lưu chiều TỈNH -> NGƯỜI, và chỉ cho tầng lá. Địa bàn của quản lý vùng
-- KHÔNG lưu ở đây mà suy ra bằng cách gộp địa bàn của cấp dưới -- nhập tay cả
-- hai tầng là tạo ra hai nguồn sự thật rồi có ngày lệch nhau.
--
-- UNIQUE trên province_id chính là quy tắc "một tỉnh chỉ một người" của khách
-- hàng, và lần này ràng buộc được ở CSDL thật (khác với chuyện tự làm cấp
-- trên của chính mình ở V16, nơi MySQL không cho CHECK tham chiếu cột
-- AUTO_INCREMENT). Một người vẫn cầm được nhiều tỉnh.

INSERT INTO schema_migrations (version) VALUES ('V19__add_user_provinces__ndat2003');

CREATE TABLE `user_provinces` (
  `assignment_id` int NOT NULL AUTO_INCREMENT,
  `province_id` int NOT NULL,
  `user_id` int NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`assignment_id`),
  UNIQUE KEY `uq_user_provinces_province` (`province_id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `fk_user_provinces_province` FOREIGN KEY (`province_id`) REFERENCES `provinces` (`province_id`),
  CONSTRAINT `fk_user_provinces_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
