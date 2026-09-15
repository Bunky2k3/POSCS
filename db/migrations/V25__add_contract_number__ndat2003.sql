-- Số hợp đồng THẬT, ghi trên bản giấy.
--
-- Đến giờ hợp đồng chỉ có `contract_code` -- mã nội bộ dạng HD-0015 do hệ
-- thống tự sinh. Đó là định danh của BẢN GHI, không phải của tờ hợp đồng: bên
-- ngoài không ai biết HD-0015 là gì, khách hàng gọi điện lên thì đọc số ghi
-- trên giấy, kiểu "123/2026/HĐKT-POSTEF". Hiện không có chỗ nào lưu số đó, nên
-- không tra ngược được từ giấy về hệ thống.
--
-- HAI CỘT SONG SONG, KHÔNG THAY THẾ NHAU:
--   contract_code   -- mình sinh, UNIQUE, dùng làm khoá tra cứu nội bộ
--   contract_number -- người nhập, tự do, là thứ in trên hợp đồng
-- Đừng gộp làm một. Gộp vào contract_code thì mất khả năng sinh mã tự động
-- (và mất luôn UNIQUE đang chống trùng ở đó); bỏ contract_code đi thì mọi liên
-- kết nội bộ phải bám vào một chuỗi do người gõ tay.
--
-- NULLABLE, và KHÔNG UNIQUE.
--   * nullable vì bản nháp chưa ký thì thường CHƯA có số -- số được cấp lúc
--     ký. Bắt buộc từ đầu là ép người dùng bịa ra một con số.
--   * không UNIQUE vì đây là chuỗi tự do người gõ: hai chi nhánh đánh số riêng,
--     hoặc cùng một số lặp lại qua các năm, đều có thật. Đặt UNIQUE lên đó là
--     chặn nghiệp vụ hợp lệ để bắt một lỗi gõ nhầm -- không đáng. Việc chống
--     trùng đã có contract_code lo.
--
-- Có index thường (không UNIQUE) vì đây sẽ là thứ người dùng tra nhiều nhất:
-- ContractDAO tìm theo từ khoá đã gộp cột này vào cùng mã, tiêu đề, tên khách.

INSERT INTO schema_migrations (version) VALUES ('V25__add_contract_number__ndat2003');

ALTER TABLE `contracts`
  ADD COLUMN `contract_number` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL
      AFTER `contract_code`,
  ADD KEY `idx_contracts_number` (`contract_number`);
