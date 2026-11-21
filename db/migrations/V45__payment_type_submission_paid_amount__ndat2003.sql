-- Kỳ thanh toán theo điều khoản thanh toán của hợp đồng thật (Task 10.4, BR-60,
-- BR-18, UC-53): tạm ứng, rồi thanh toán sau khi nghiệm thu và nộp hồ sơ; bên
-- mua trả trong số ngày đã thoả thuận.
--
-- Cột mới của contract_payments:
--   payment_type   'Tạm ứng' | 'Thanh toán' (ContractPayment.TYPES), mặc định
--                  'Thanh toán' -- kỳ cũ đều là kỳ thu tiền thường.
--   condition_note điều kiện của kỳ, ghi ngắn (vd. "sau khi nghiệm thu").
--   payment_days   số ngày bên mua phải trả kể từ khi nộp hồ sơ, không bắt buộc.
--   submitted_date ngày nộp hồ sơ thanh toán; kỳ có số ngày thì hạn thu =
--                  ngày nộp + số ngày.
--   paid_amount    số tiền thực thu (BR-18: doanh thu cộng cột này theo ngày thu).
-- due_date (hạn thu) cho phép NULL: lập kỳ được khi chưa biết hạn.
--
-- Tình trạng kỳ (Đã thu / Quá hạn / Chờ thu / Chưa nộp hồ sơ) SUY RA mỗi lần
-- đọc (ContractPayment.statusOn), không có cột lưu.
--
-- Kỳ đã thu cũ: số thực thu = số tiền kỳ, để dashboard không đổi số.

INSERT INTO schema_migrations (version) VALUES ('V45__payment_type_submission_paid_amount__ndat2003');

ALTER TABLE `contract_payments`
  ADD COLUMN `payment_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Thanh toán' AFTER `contract_id`;

ALTER TABLE `contract_payments`
  ADD COLUMN `condition_note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `invoice_amount`;

ALTER TABLE `contract_payments`
  ADD COLUMN `payment_days` int DEFAULT NULL AFTER `condition_note`;

ALTER TABLE `contract_payments`
  ADD COLUMN `submitted_date` date DEFAULT NULL AFTER `payment_days`;

ALTER TABLE `contract_payments`
  MODIFY COLUMN `due_date` date DEFAULT NULL;

ALTER TABLE `contract_payments`
  ADD COLUMN `paid_amount` decimal(15,2) DEFAULT NULL AFTER `paid_date`;

UPDATE `contract_payments` SET `paid_amount` = `invoice_amount`
 WHERE `paid_date` IS NOT NULL AND `paid_amount` IS NULL;
