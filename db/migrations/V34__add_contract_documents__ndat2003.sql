-- Giấy tờ kèm theo hợp đồng: biên bản nghiệm thu, biên bản bàn giao, biên bản
-- thanh lý, phụ lục ký riêng, báo giá, hoá đơn...
--
-- Một hợp đồng thật kéo theo cả tập hồ sơ, mà tới giờ chỗ treo giấy tờ duy
-- nhất là MỘT ô `contracts.attachment_url`. Hai chỗ lộ rõ nhất: căn cứ thanh lý
-- chỉ ghi được SỐ biên bản vào cột note của contract_history (có số, không có
-- file), và phòng nhận bàn giao đóng chặng bằng một câu ghi chú chứ không đính
-- được biên bản.
--
-- CHỈ LƯU LINK, không lưu file. Khách hàng chốt như vậy (2026-09-19): hồ sơ của
-- họ đang nằm trên Drive và vẫn sẽ nằm ở đó. Hệ quả phải nói thẳng ra: hệ thống
-- KHÔNG giữ bản sao nào -- ai xoá file trên Drive thì dòng ở đây thành link
-- chết, và quyền xem file do Drive quyết chứ không phải POSCS. Đổi lại, không
-- phải lo dung lượng, sao lưu, hay chuyện một file tải lên bị phục vụ lại trên
-- chính origin của ứng dụng.
--
-- `doc_type` là VARCHAR chứ không ENUM, cùng lẽ với event_type của
-- contract_history và relation_type của contract_links: danh mục loại giấy tờ
-- còn phải chốt với khách hàng, thêm một loại thì phải là thêm một chuỗi chứ
-- không phải một lần ALTER TABLE.
--
-- XOÁ LÀ XOÁ MỀM, bắt buộc có lý do -- đúng khuôn voidRecord của hợp đồng.
-- Giấy tờ là chứng cứ: gỡ cứng thì không tra ngược được ai gỡ cái gì, mà đây
-- lại đúng là thứ người ta muốn tra khi có tranh chấp.

INSERT INTO schema_migrations (version) VALUES ('V34__add_contract_documents__ndat2003');

CREATE TABLE `contract_documents` (
  `document_id` int NOT NULL AUTO_INCREMENT,
  `contract_id` int NOT NULL,
  -- Loại giấy tờ. Danh mục hiện dùng nằm ở ContractDocument.TYPES; cột này
  -- không ràng buộc giá trị nên thêm loại mới không phải đụng CSDL.
  `doc_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  -- Tên người dùng đặt cho giấy tờ, vd "Biên bản nghiệm thu giai đoạn 1".
  -- Để trống thì màn hình hiện chính loại giấy tờ.
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  -- Link tới file (Drive...). 500 ký tự: link Drive có thể kèm tham số dài,
  -- 255 như attachment_url cũ là chật.
  `file_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `uploaded_by` int NOT NULL,
  `uploaded_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  -- Xoá mềm: dòng ở lại, chỉ thôi hiện. Ba cột đi CÙNG NHAU -- đánh dấu xoá mà
  -- không kèm ai/khi nào/vì sao thì đúng bằng xoá cứng về mặt tra cứu.
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_by` int DEFAULT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  `delete_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`document_id`),
  KEY `idx_contract_documents_contract` (`contract_id`, `is_deleted`),
  KEY `fk_contract_documents_uploader` (`uploaded_by`),
  KEY `fk_contract_documents_deleter` (`deleted_by`),
  -- KHÔNG ON DELETE CASCADE, cùng lẽ với fk_contracts_parent (V29) và
  -- fk_contract_links_* (V31): hợp đồng vốn xoá MỀM nên cascade không bao giờ
  -- nổ trong đường chạy bình thường, chỉ chực chờ một lần xoá cứng lỡ tay.
  CONSTRAINT `fk_contract_documents_contract` FOREIGN KEY (`contract_id`) REFERENCES `contracts` (`contract_id`),
  CONSTRAINT `fk_contract_documents_uploader` FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_contract_documents_deleter` FOREIGN KEY (`deleted_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Dời ô đính kèm cũ sang bảng mới, rồi BỎ cột
-- ---------------------------------------------------------------------------
-- Gộp về MỘT nguồn sự thật. Giữ lại cột cũ nghĩa là hai chỗ cùng trả lời "hợp
-- đồng này có giấy tờ gì", và sớm muộn chúng lệch nhau.
--
-- Người tải lên ghi là NGƯỜI PHỤ TRÁCH hợp đồng: dữ liệu cũ không lưu ai dán
-- link, mà owner_id là phỏng đoán gần đúng nhất và luôn có giá trị (NOT NULL).
INSERT INTO `contract_documents` (`contract_id`, `doc_type`, `title`, `file_url`, `note`, `uploaded_by`, `uploaded_at`)
SELECT c.`contract_id`, N'Hợp đồng đã ký', N'Bản PDF hợp đồng', c.`attachment_url`,
       N'Chuyển từ ô đính kèm cũ khi tách bảng giấy tờ (V34).', c.`owner_id`, c.`created_at`
FROM `contracts` c
WHERE c.`attachment_url` IS NOT NULL AND TRIM(c.`attachment_url`) <> '';

-- Nhật ký cho đúng những dòng vừa dời, giống thứ ContractDAO sinh ra khi người
-- dùng tự thêm giấy tờ.
INSERT INTO `contract_history` (`contract_id`, `event_type`, `detail`, `changed_by`, `changed_at`)
SELECT d.`contract_id`, N'Tài liệu',
       CONCAT(N'Thêm tài liệu: ', d.`doc_type`, N' — chuyển từ ô đính kèm cũ (V34).'),
       d.`uploaded_by`, d.`uploaded_at`
FROM `contract_documents` d
WHERE d.`note` LIKE N'Chuyển từ ô đính kèm cũ%';

ALTER TABLE `contracts` DROP COLUMN `attachment_url`;
