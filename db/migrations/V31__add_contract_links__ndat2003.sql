-- Liên kết hợp đồng BÁN với hợp đồng MUA: "đầu ra kéo theo đầu vào".
--
-- Yêu cầu của khách hàng, tồn từ buổi làm việc 2026-09-12: bán một hợp đồng
-- thì phải biết nó kéo theo những đơn mua vào nào, và ngược lại mở một đơn mua
-- ra phải biết nó sinh ra vì hợp đồng bán nào. Cho tới giờ hai chiều mua/bán
-- (V21) đứng cạnh nhau mà không có đường nào nối chúng lại.
--
-- BẢNG RIÊNG, KHÔNG dùng lại `contracts.parent_contract_id`. Cột đó đang mang
-- đúng một nghĩa -- "đây là PHỤ LỤC của hợp đồng kia", một tầng -- và cả phép
-- đếm phụ lục, phép cộng giá trị lẫn luật khoá điều khoản đều dựa vào nó. Nhồi
-- nghĩa thứ hai vào là mọi câu truy vấn phải tự hỏi "cha kiểu gì", đúng chỗ về
-- sau hai nghĩa lệch nhau.
--
-- NHIỀU-NHIỀU, cố ý: mua gom là chuyện bình thường (một đơn mua 60km cáp chia
-- cho ba hợp đồng bán), và một hợp đồng bán lớn cũng cần nhiều đơn mua. Khoá
-- duy nhất đặt trên CẶP, không trên từng cột.
--
-- `relation_type` để sẵn dù hiện chỉ dùng một giá trị: bảng này về sau còn phải
-- diễn đạt quan hệ "nâng cấp cho" (khách thay module phần cứng thì hợp đồng
-- mới nâng cấp cho hợp đồng cũ). Thêm loại quan hệ lúc đó chỉ là thêm một giá
-- trị chuỗi, không phải dựng bảng thứ hai -- cùng lý do event_type của
-- contract_history là varchar chứ không ENUM.

INSERT INTO schema_migrations (version) VALUES ('V31__add_contract_links__ndat2003');

CREATE TABLE `contract_links` (
  `link_id` int NOT NULL AUTO_INCREMENT,
  -- Hướng CỐ ĐỊNH: from = hợp đồng BÁN (đầu ra), to = hợp đồng MUA (đầu vào).
  -- Lưu một chiều dù màn hình cho nối từ cả hai phía, nếu không thì cùng một
  -- quan hệ tồn tại hai bản ghi ngược nhau và không phép đếm nào còn đúng.
  `sell_contract_id` int NOT NULL,
  `buy_contract_id` int NOT NULL,
  `relation_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL
      DEFAULT 'Đầu vào phục vụ đầu ra',
  -- Người dùng ghi vì sao nối hai hợp đồng này, ví dụ "mua 20km cáp cho giai
  -- đoạn 1". Không bắt buộc: phần lớn trường hợp nhìn hai mã là hiểu.
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` int NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`link_id`),
  -- Nối hai lần cùng một cặp là dữ liệu rác, và nó làm phép cộng giá trị đầu
  -- vào đếm đôi. Chặn ở CSDL vì hai người bấm cùng lúc thì kiểm ở tầng trên
  -- không thấy nhau.
  UNIQUE KEY `uq_contract_links_pair` (`sell_contract_id`, `buy_contract_id`),
  KEY `idx_contract_links_buy` (`buy_contract_id`),
  KEY `fk_contract_links_user` (`created_by`),
  -- KHÔNG ON DELETE CASCADE, cùng lẽ với fk_contracts_parent ở V29: hợp đồng
  -- vốn xoá MỀM (is_deleted) nên cascade không bao giờ nổ trong đường chạy
  -- bình thường, chỉ chực chờ một lần xoá cứng lỡ tay mang theo cả liên kết.
  CONSTRAINT `fk_contract_links_sell` FOREIGN KEY (`sell_contract_id`) REFERENCES `contracts` (`contract_id`),
  CONSTRAINT `fk_contract_links_buy` FOREIGN KEY (`buy_contract_id`) REFERENCES `contracts` (`contract_id`),
  CONSTRAINT `fk_contract_links_user` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Hai liên kết cho bộ dữ liệu demo
-- ---------------------------------------------------------------------------
-- Không có dòng nào thì khối "Đầu vào / Đầu ra" trên trang hợp đồng rỗng ở mọi
-- hợp đồng, và không ai kiểm được nó hiển thị đúng hay không -- đúng bài học
-- của phụ lục demo ở V29.
--
-- Chọn theo ĐIỀU KIỆN thay vì ghi cứng id: migration còn phải chạy được trên
-- CSDL dựng từ schema.sql, nơi chưa có hợp đồng mua nào (nhà cung cấp NCC-* do
-- V28 gieo). Không khớp dòng nào thì cả khối tự bỏ qua, không lỗi.
--
-- Cặp thứ nhất: hợp đồng bán cáp ADSS nối với đơn mua sợi quang -- đúng hình
-- dạng "bán ra kéo theo mua vào" mà khách hàng mô tả.
INSERT INTO `contract_links` (`sell_contract_id`, `buy_contract_id`, `note`, `created_by`)
SELECT s.`contract_id`, b.`contract_id`,
       N'Mua sợi quang phục vụ tuyến ADSS liên tỉnh — gieo sẵn cho bộ dữ liệu demo.',
       s.`owner_id`
FROM `contracts` s
JOIN `contracts` b
  ON b.`direction` = N'Mua' AND b.`is_deleted` = 0 AND b.`parent_contract_id` IS NULL
 AND b.`contract_code` = N'01/2026/HĐMB-POSTEF'
WHERE s.`contract_code` = N'05/2026/HĐKT-POSTEF'
  AND s.`is_deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `contract_links` l
                  WHERE l.`sell_contract_id` = s.`contract_id` AND l.`buy_contract_id` = b.`contract_id`);

-- Cặp thứ hai dùng CHUNG đơn mua ở trên, để bộ dữ liệu thể hiện được quan hệ
-- nhiều-nhiều: một đơn mua gom phục vụ hai hợp đồng bán khác nhau.
INSERT INTO `contract_links` (`sell_contract_id`, `buy_contract_id`, `note`, `created_by`)
SELECT s.`contract_id`, b.`contract_id`,
       N'Cùng đơn mua gom, chia cho tuyến Hạ Long - Cẩm Phả — gieo sẵn cho bộ dữ liệu demo.',
       s.`owner_id`
FROM `contracts` s
JOIN `contracts` b
  ON b.`direction` = N'Mua' AND b.`is_deleted` = 0 AND b.`parent_contract_id` IS NULL
 AND b.`contract_code` = N'01/2026/HĐMB-POSTEF'
WHERE s.`contract_code` = N'06/2026/HĐKT-POSTEF'
  AND s.`is_deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `contract_links` l
                  WHERE l.`sell_contract_id` = s.`contract_id` AND l.`buy_contract_id` = b.`contract_id`);

-- Nhật ký cho hai liên kết vừa gieo, ghi lên CẢ HAI hợp đồng -- giống hệt thứ
-- ContractDAO sinh ra khi người dùng nối thật: câu "vì sao có đơn mua này" hỏi
-- ở phía mua, còn "đã mua gì cho đơn này" hỏi ở phía bán.
INSERT INTO `contract_history` (`contract_id`, `event_type`, `detail`, `changed_by`, `changed_at`)
SELECT l.`sell_contract_id`, N'Nối hợp đồng',
       CONCAT(N'Nối với hợp đồng mua ', b.`contract_code`, N' — gieo sẵn cho bộ dữ liệu demo.'),
       l.`created_by`, l.`created_at`
FROM `contract_links` l
JOIN `contracts` b ON b.`contract_id` = l.`buy_contract_id`
WHERE NOT EXISTS (SELECT 1 FROM `contract_history` h
                  WHERE h.`contract_id` = l.`sell_contract_id` AND h.`event_type` = N'Nối hợp đồng');

INSERT INTO `contract_history` (`contract_id`, `event_type`, `detail`, `changed_by`, `changed_at`)
SELECT l.`buy_contract_id`, N'Nối hợp đồng',
       CONCAT(N'Phục vụ hợp đồng bán ', s.`contract_code`, N' — gieo sẵn cho bộ dữ liệu demo.'),
       l.`created_by`, l.`created_at`
FROM `contract_links` l
JOIN `contracts` s ON s.`contract_id` = l.`sell_contract_id`;
