-- Người ký hai bên, nơi ký, và giá trị hợp đồng.
--
-- ===== Người ký =====
-- Hệ thống đang có `enterprises.legal_representative` (đại diện pháp luật của
-- khách) và bảng `enterprisecontacts` (danh bạ người liên hệ + chức vụ). Cả
-- hai đều ở CẤP CÔNG TY, trong khi người đặt bút ký là chuyện của TỪNG HỢP
-- ĐỒNG: có uỷ quyền thì người ký không phải đại diện pháp luật, và cùng một
-- khách có thể mỗi hợp đồng một người ký khác nhau.
--
-- Nên đây là cột trên `contracts`, không phải sửa `enterprises`. Giá trị mặc
-- định lúc nhập sẽ được điền sẵn từ legal_representative cho đỡ phải gõ, nhưng
-- lưu riêng: sửa đại diện pháp luật của công ty KHÔNG được làm đổi tên người
-- đã ký trên một hợp đồng đã ký xong.
--
-- ===== Giá trị hợp đồng =====
-- Trước đây "giá trị" phải cộng ngược từ contract_payments
-- (ContractDAO.sumInvoiceAmountByContractId). Sai bản chất: giá trị hợp đồng
-- là ĐIỀU KHOẢN hai bên ký, còn tổng các kỳ thanh toán là THỰC TẾ thu/chi.
-- Hai con số đó lệch nhau chính là công nợ -- gộp làm một thì mất hẳn khái
-- niệm đó, và một hợp đồng chưa lập kỳ thanh toán nào sẽ hiện giá trị 0.
--
-- decimal(15,2) khớp contract_payments.invoice_amount, đừng dùng double: tiền
-- mà để dấu phẩy động thì cộng vài trăm dòng là lệch xu.
--
-- Nullable: bản Nháp chưa chốt giá.
--
-- GHI CHÚ PHẠM VI: vùng tài chính trước đây bị loại trừ theo tài liệu yêu cầu
-- (Project_Report.pdf mục 1.5.2). Khách hàng yêu cầu mở lại, người dùng quyết
-- định bỏ ràng buộc đó ngày 2026-09-15. Cột này là hệ quả của quyết định ấy.

INSERT INTO schema_migrations (version) VALUES ('V27__add_contract_signing_parties_and_value__ndat2003');

ALTER TABLE `contracts`
  -- Người ký bên mình (POSTEF).
  ADD COLUMN `signer_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `owner_id`,
  ADD COLUMN `signer_position` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `signer_name`,
  -- Người ký bên đối tác. "counterparty" dùng đúng từ mà code đang dùng cho
  -- bên kia của hợp đồng (xem ContractController.counterpartyRoleFor).
  ADD COLUMN `counterparty_signer_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `signer_position`,
  ADD COLUMN `counterparty_signer_position` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `counterparty_signer_name`,
  -- Số/ngày giấy uỷ quyền, khi người ký không phải đại diện pháp luật. Một ô
  -- chung cho cả hai bên: chuyện uỷ quyền hiếm, tách đôi thì phần lớn hợp đồng
  -- mang hai cột rỗng.
  ADD COLUMN `authorization_ref` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `counterparty_signer_position`,
  ADD COLUMN `signing_place` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `authorization_ref`,
  -- Giá trị theo điều khoản, KHÁC tổng các kỳ thanh toán thực tế.
  ADD COLUMN `contract_value` decimal(15,2) DEFAULT NULL AFTER `signing_place`;
