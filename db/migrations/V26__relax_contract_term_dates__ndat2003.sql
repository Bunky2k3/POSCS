-- Nới ngày hiệu lực / ngày kết thúc cho phép trống.
--
-- V24 đã tách "tạo" khỏi "ký": hợp đồng mới ra ở trạng thái Nháp. Nhưng form
-- tạo vẫn bắt nhập đủ ngày hiệu lực và ngày kết thúc, trong khi lúc mới soạn
-- thảo thì hai mốc đó **chưa chốt được** -- chúng là kết quả đàm phán. Bắt
-- nhập nghĩa là ép người dùng điền một con số tạm rồi sửa lại sau, và trong
-- lúc đó CSDL mang một thời hạn chưa ai đồng ý.
--
-- signing_date đã nullable từ V24; đây là nốt hai cột còn lại của bộ ba.
--
-- HỆ QUẢ PHẢI XỬ LÝ CÙNG LÚC, đừng chạy migration này một mình:
--
--   1. ContractDAO.STATUS_CASE_SQL phải có nhánh IS NULL đứng TRƯỚC. Không có
--      nó thì mọi so sánh với NULL đều ra NULL, CASE rơi xuống ELSE và bản
--      nháp bị SQL gán nhãn "Đang hiệu lực" -- trong khi computeStatus() bên
--      Java trả "Chưa hiệu lực". Đúng kiểu lệch hai đường tính mà
--      ContractStatusIntegrationTest sinh ra để canh.
--   2. countStatusSummary gộp hợp đồng chưa có ngày vào nhóm "Chưa hiệu lực",
--      nếu không bốn con số của dải KPI cộng lại không bằng tổng.
--   3. KHÔNG ký được khi chưa có đủ hai mốc: hợp đồng không có thời hạn thì
--      không phải hợp đồng. Chặn ở ContractDAO.changeProgressStatus.
--
-- Vì (3) mà việc nới này an toàn: NULL chỉ tồn tại trong quãng Nháp, và không
-- có đường nào đưa một hợp đồng thiếu thời hạn sang trạng thái đã ký.

INSERT INTO schema_migrations (version) VALUES ('V26__relax_contract_term_dates__ndat2003');

ALTER TABLE `contracts`
  MODIFY COLUMN `effective_date` date DEFAULT NULL,
  MODIFY COLUMN `end_date` date DEFAULT NULL;
