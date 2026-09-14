-- Yêu cầu thay đổi: đường để cấp dưới xin cấp trên sửa dữ liệu.
--
-- Nửa còn lại của thoả thuận với khách hàng ngày 14/09/2026. V16 đã lấy quyền
-- ghi Khách hàng/Hợp đồng khỏi tầng lá, nhưng chưa cho họ đường nào để xin --
-- muốn đổi gì phải gọi điện nhắn tin ngoài hệ thống, không để lại vết. Bảng
-- này là chỗ chứa cái vết đó.
--
-- Vì sao là "intent" chứ không phải "diff":
--
-- Cấp dưới bị cấm CẢ TẠO MỚI, không chỉ sửa. Một yêu cầu thêm khách hàng thì
-- KHÔNG có dòng nào sẵn để trỏ tới, nên target_id phải cho NULL và nội dung đề
-- xuất phải nằm ngay trong chính yêu cầu. Thiết kế kiểu "diff trên dòng có
-- sẵn" diễn đạt được mỗi trường hợp sửa, đến lúc làm tới tạo mới là phải đập
-- đi làm lại -- đã ghi trước điều này trong PERMISSIONS.md.
--
-- proposed_content là VĂN BẢN người dùng gõ, không phải JSON để hệ thống tự
-- áp lại. Có chủ ý: duyệt xong thì cấp trên tự thực hiện trên form thường,
-- nơi mọi ràng buộc nghiệp vụ đã có sẵn. Tự động áp một payload lưu từ trước
-- sẽ đè lên thay đổi mà người khác vừa làm trong lúc yêu cầu nằm chờ, mà
-- không ai biết. Đổi lại, cấp trên phải tự nhập -- chấp nhận, vì số lượng yêu
-- cầu ít hơn hẳn số lần thao tác trực tiếp.
--
-- Trạng thái đặt tiếng Việt cho khớp với cách technicalrequests đang làm
-- ("Mới tiếp nhận"/"Đang xử lý"/"Đã đóng"), không trộn tiếng Anh vào dữ liệu.

INSERT INTO schema_migrations (version) VALUES ('V17__add_change_requests__ndat2003');

CREATE TABLE `change_requests` (
  `request_id` int NOT NULL AUTO_INCREMENT,
  -- 'Khách hàng' | 'Hợp đồng' -- đúng hai tài nguyên mà cây tổ chức siết.
  `resource_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  -- 'Tạo mới' | 'Sửa' | 'Xoá'
  `intent` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  -- NULL khi intent = 'Tạo mới': chưa có dòng nào để trỏ tới.
  -- Không đặt khoá ngoại vì cột này trỏ sang HAI bảng khác nhau tuỳ
  -- resource_type; chỗ đọc tự tra đúng bảng theo resource_type.
  `target_id` int DEFAULT NULL,
  `proposed_content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci,
  `requested_by` int NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Chờ duyệt',
  `reviewed_by` int DEFAULT NULL,
  `review_note` text COLLATE utf8mb4_unicode_ci,
  `reviewed_at` datetime DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`request_id`),
  KEY `requested_by` (`requested_by`),
  KEY `reviewed_by` (`reviewed_by`),
  KEY `status` (`status`),
  CONSTRAINT `fk_change_requests_requester` FOREIGN KEY (`requested_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_change_requests_reviewer` FOREIGN KEY (`reviewed_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
