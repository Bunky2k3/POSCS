-- Trục tiến độ của hợp đồng, và ký thành một bước riêng.
--
-- KH trả lời 2026-09-15: **nhân viên không tự ký hợp đồng được**.
--
-- Hệ thống hiện KHÔNG diễn đạt nổi câu đó. signing_date là NOT NULL, nên tạo
-- hợp đồng là đã ký: không có khoảnh khắc nào một hợp đồng tồn tại mà chưa ký,
-- và vì thế không có chỗ nào để chặn việc ký. Ai tạo được thì đã ký rồi.
--
-- Cấp dưới (users.manager_id khác null) vốn đã bị AccessControl siết xuống chỉ
-- xem trên Hợp đồng, nên với nhóm đó luật đã đúng sẵn. Chỗ hở thật là Sales
-- CHƯA xếp vào cây (manager_id null) -- phần lớn nhân viên ở thời điểm này,
-- vì bảng phân công cấp trên còn chưa nhập. Họ tạo hợp đồng và nó đã ký luôn.
--
-- Nên tách làm hai: TẠO ra bản nháp, KÝ là hành động khác, người khác bấm.
--
-- HAI TRỤC, ĐỪNG TRỘN. Cột status sẵn có là trục LỊCH (Chưa hiệu lực / Đang
-- hiệu lực / Sắp hết hạn / Đã hết hạn) -- hàm thuần của effective_date và
-- end_date, không ai đặt được, giữ nguyên. progress_status là trục TIẾN ĐỘ:
-- do người đặt, có chứng từ. Hai trục lệch nhau ở CẢ HAI chiều (hết hạn mà
-- chưa thanh lý; thanh lý sớm mà theo lịch vẫn đang hiệu lực), nên không suy
-- ra được cái này từ cái kia. Chính độ lệch đó là hàng đợi việc còn tồn.
--
-- KHÔNG thêm cột liquidated_at/liquidated_by như dự tính ban đầu. Từ khi có
-- contract_history thì dòng chuyển sang 'Đã thanh lý' đã mang sẵn changed_by
-- và changed_at; thêm cột nữa là dựng nguồn sự thật thứ hai, rồi có ngày hai
-- chỗ nói hai điều khác nhau về cùng một lần thanh lý.

INSERT INTO schema_migrations (version) VALUES ('V24__add_contract_progress_status__ndat2003');

-- 'Nháp' | 'Đã ký' | 'Đã thanh lý' | 'Chấm dứt sớm'
-- Chuỗi tự do, không ENUM -- cùng lý do với contract_history.event_type: vòng
-- đời còn đang mở (phụ lục, bàn giao liên phòng chưa làm), mà ENUM thì mỗi
-- lần thêm một trạng thái là một ALTER TABLE khoá bảng.
ALTER TABLE `contracts`
  ADD COLUMN `progress_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Nháp'
      AFTER `status`,
  ADD KEY `idx_contracts_progress` (`progress_status`);

-- Mọi hợp đồng đang có đều đã ký (signing_date vốn NOT NULL), nên chúng đứng ở
-- 'Đã ký' chứ không phải mặc định 'Nháp' của cột.
--
-- Chạy TRƯỚC khi nới signing_date: lúc này cột còn NOT NULL nên không dòng nào
-- có thể lọt vào diện "chưa ký" giữa chừng.
UPDATE `contracts` SET `progress_status` = 'Đã ký';

-- Nới signing_date: bản nháp chưa ký thì chưa có ngày ký, và điền đại một
-- ngày vào đó là ghi vào CSDL một sự kiện chưa xảy ra.
--
-- effective_date/end_date GIỮ NGUYÊN NOT NULL: đó là ngày dự kiến, người soạn
-- nháp vẫn biết và vẫn phải điền. Nới cả ba sẽ kéo theo computeStatus và toàn
-- bộ bộ lọc theo kỳ phải xử lý null, đổi nhiều thứ hơn hẳn phần việc này cần.
ALTER TABLE `contracts` MODIFY COLUMN `signing_date` date DEFAULT NULL;
