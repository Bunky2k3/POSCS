-- Mã hợp đồng để trống được khi còn Nháp (Task 10.2, BR-58).
--
-- Chi nhánh mở hồ sơ hợp đồng Nháp từ lúc làm phiếu GQNCKH, lúc đó chưa có số
-- trên bản giấy (quyết định 14, SPEC §5.4). Mã chỉ bắt buộc trước khi Ký: chặn
-- ở ContractValidator.validateSigning / ContractService.signContract và chốt
-- trong giao dịch ở ContractDAO.changeProgressStatus (chưa có mã → MSG-061).
--
-- Giữ UNIQUE KEY `contract_code`: InnoDB cho nhiều dòng NULL trong một khoá
-- UNIQUE, nên nhiều bản Nháp cùng chưa có mã không bị coi là trùng, còn mã đã
-- nhập vẫn không được trùng hợp đồng khác.
--
-- Chuỗi rỗng / toàn khoảng trắng (nếu có) đổi thành NULL: code ghi NULL thay
-- vì chuỗi rỗng, và màn hình hiện "Chưa có số" cho cả hai.

INSERT INTO schema_migrations (version) VALUES ('V43__contract_code_nullable__ndat2003');

ALTER TABLE `contracts`
  MODIFY COLUMN `contract_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL;

UPDATE `contracts` SET `contract_code` = NULL WHERE TRIM(`contract_code`) = '';
