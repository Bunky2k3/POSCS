-- Thông tin chi nhánh (người ký bên mình) và SĐT, fax người ký trên hợp đồng.
--
-- ===== Bảng branch_info =====
-- Từ V27, người ký bên mình (contracts.signer_name/signer_position) do người
-- lập hợp đồng gõ tay ở TỪNG hợp đồng. Thực tế bên mình chỉ có MỘT người đặt
-- bút ký: Giám đốc chi nhánh (GĐCN). Gõ tay mỗi lần vừa mất công vừa dễ gõ sai
-- tên giám đốc. Chốt 19/11/2026 (BR-56, UC-55): Admin khai thông tin GĐCN MỘT
-- lần ở trang "Thông tin chi nhánh", form hợp đồng chỉ hiển thị, hệ thống tự
-- chép sang hợp đồng.
--
-- MỘT DÒNG DUY NHẤT, id = 1: hệ thống phục vụ một chi nhánh, và trang Thông
-- tin chi nhánh chỉ xem và sửa, không tạo/xoá (SPEC 5.8). Nên dòng được gieo
-- sẵn ngay ở đây và DAO chỉ UPDATE dòng đó. Khoá chính tinyint không
-- AUTO_INCREMENT: không có đường nào chèn dòng thứ hai.
--
-- Gieo sẵn chức vụ 'Giám đốc chi nhánh', họ tên để trống: tên giám đốc là dữ
-- liệu thật của khách, không bịa. Họ tên còn trống nghĩa là "chưa khai GĐCN"
-- (BR-56), kể cả khi đã có chức vụ gieo sẵn.
--
-- ===== Hai cột signer_phone, signer_fax trên contracts =====
-- Cùng lẽ với signer_name/signer_position ở V27: người ký là thông tin của
-- TỪNG hợp đồng tại lúc ký. Chỉ trỏ sang branch_info thì đổi GĐCN là mọi hợp
-- đồng cũ đổi theo -- sai với tờ giấy đã ký. Nên SĐT, fax cũng được CHÉP sang
-- hợp đồng như họ tên, chức vụ.
--
-- varchar(20) cho cả bốn cột SĐT/fax: khớp users.phone, và BR-57 giới hạn SĐT,
-- fax tối đa 20 ký tự.
--
-- KHÔNG chạy lại dữ liệu cho hợp đồng cũ: hai cột mới để NULL, người ký đã gõ
-- tay trên hợp đồng cũ giữ nguyên.
--
-- THỨ TỰ TRIỂN KHAI: chạy file này lên CSDL TRƯỚC khi chạy code đọc/ghi hai cột
-- mới (Task 8.4). Code mới trên CSDL cũ thì mọi lần đọc/lưu hợp đồng lỗi
-- "Unknown column".

INSERT INTO schema_migrations (version) VALUES ('V39__add_branch_info_and_contract_signer_contact__ndat2003');

CREATE TABLE `branch_info` (
  `branch_info_id`    tinyint NOT NULL,
  -- Cả bốn cột thông tin để NULL được: dòng gieo sẵn chưa có họ tên. Bắt buộc
  -- họ tên và chức vụ (BR-57) là việc của trang Thông tin chi nhánh.
  `director_name`     varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `director_position` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone`             varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fax`               varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  -- Admin sửa lần cuối. NULL = chưa ai sửa từ lúc gieo.
  `updated_by`        int DEFAULT NULL,
  `updated_at`        timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`branch_info_id`),
  KEY `fk_branch_info_updated_by` (`updated_by`),
  CONSTRAINT `fk_branch_info_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `branch_info` (`branch_info_id`, `director_position`) VALUES
(1, 'Giám đốc chi nhánh');

ALTER TABLE `contracts`
  -- SĐT, fax người ký bên mình, chép từ branch_info cùng lúc với họ tên, chức vụ.
  ADD COLUMN `signer_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `signer_position`,
  ADD COLUMN `signer_fax` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `signer_phone`;
