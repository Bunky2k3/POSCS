-- Giấy tờ của hợp đồng dạng ô tích (Task 10.3, BR-59, UC-52).
--
-- Vẫn trên bảng contract_documents sẵn có (quyết định 15, SPEC §11): không có
-- bảng hay mô-đun mới. Mỗi tờ giờ có NGÀY TRÊN GIẤY (bắt buộc) và SỐ GIẤY (không
-- bắt buộc); đường dẫn thành không bắt buộc vì nhiều tờ chỉ có bản giấy.
--
-- Dòng cũ: ngày trên giấy lấy theo ngày thêm vào hệ thống (uploaded_at), vì lúc
-- thêm không ai ghi ngày trên giấy. uploaded_at cho phép NULL nên còn một lượt
-- lấy ngày chạy migration cho chắc, rồi mới đặt NOT NULL.
--
-- Danh mục loại đổi theo BR-59 (ContractDocument.TYPES). Loại cũ không còn trong
-- danh mục thì chuyển sang tên mới; "Biên bản bàn giao" và "Báo giá / Đơn đặt
-- hàng" không có chỗ tương ứng nên thành "Khác", tên cũ chép vào title (nếu title
-- đang trống) để vẫn đọc ra được đó là giấy gì. Đổi cả dòng đã huỷ mềm để nhật ký
-- và màn hình đọc cùng một danh mục.
--
-- Link demo của 14/2026/HĐKT-POSTEF (loại "Hợp đồng đã ký") giữ nguyên: loại đó
-- vẫn có trong danh mục, link không bị đụng.

INSERT INTO schema_migrations (version) VALUES ('V44__contract_documents_date_and_number__ndat2003');

ALTER TABLE `contract_documents`
  ADD COLUMN `doc_date` date DEFAULT NULL AFTER `title`;

ALTER TABLE `contract_documents`
  ADD COLUMN `doc_no` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `doc_date`;

ALTER TABLE `contract_documents`
  MODIFY COLUMN `file_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL;

UPDATE `contract_documents` SET `doc_date` = DATE(`uploaded_at`) WHERE `doc_date` IS NULL;
UPDATE `contract_documents` SET `doc_date` = CURRENT_DATE WHERE `doc_date` IS NULL;

ALTER TABLE `contract_documents`
  MODIFY COLUMN `doc_date` date NOT NULL;

UPDATE `contract_documents` SET `doc_type` = N'Biên bản nghiệm thu bàn giao'
 WHERE `doc_type` = N'Biên bản nghiệm thu';

UPDATE `contract_documents` SET `doc_type` = N'Hoá đơn GTGT'
 WHERE `doc_type` = N'Hoá đơn';

UPDATE `contract_documents` SET `title` = `doc_type`
 WHERE `doc_type` IN (N'Biên bản bàn giao', N'Báo giá / Đơn đặt hàng')
   AND (`title` IS NULL OR TRIM(`title`) = '');

UPDATE `contract_documents` SET `doc_type` = N'Khác'
 WHERE `doc_type` IN (N'Biên bản bàn giao', N'Báo giá / Đơn đặt hàng');
