-- Phụ lục hợp đồng: hợp đồng con trỏ về cha bằng parent_contract_id.
--
-- Luật KH (chốt 2026-09-15): phát sinh trong cửa sổ ký → thanh lý thì đi qua
-- PHỤ LỤC, không sửa thẳng vào hợp đồng đã ký. Cho tới giờ hệ thống không có
-- chỗ nào diễn đạt được câu đó: hai hợp đồng là hai dòng rời nhau, không quan
-- hệ nào, nên một phụ lục nhập vào chỉ là "một hợp đồng nữa" và không ai tra
-- ngược được nó sửa cho cái gì.
--
-- KHÔNG BẢNG RIÊNG, cố ý. Một phụ lục có đầy đủ mọi thứ của hợp đồng: mã trên
-- giấy, ngày hiệu lực, hàng hoá, giá trị, và chính nó cũng phải được KÝ. Dựng
-- bảng riêng nghĩa là chép lại toàn bộ vòng đời đợt 1+2 lần thứ hai, rồi có
-- ngày hai bộ luật lệch nhau -- mà chỗ chúng lệch lại đúng là chỗ pháp lý.
-- Một cột tự trỏ thì phụ lục dùng lại nguyên bộ máy đã có.
--
-- MỘT TẦNG. Phụ lục của phụ lục không tồn tại trong nghiệp vụ: mọi văn bản
-- sửa đổi đều treo vào đúng hợp đồng gốc. Cột này một mình không ép được điều
-- đó (khoá ngoại tự trỏ cho phép chuỗi dài tuỳ ý), nên luật nằm ở
-- ContractDAO.insert -- kiểm TRONG transaction, không phải ở form.
--
-- KHÔNG ON DELETE CASCADE: hợp đồng vốn xoá mềm (is_deleted), nên cascade
-- không bao giờ nổ trong đường chạy bình thường, chỉ chực chờ người sau lỡ tay
-- xoá cứng một dòng rồi mang theo cả các phụ lục của nó. Cùng lý do với
-- fk_contract_history_contract ở V23. Đường chặn thật là voidRecord: hợp đồng
-- còn phụ lục thì không huỷ bản ghi được.
--
-- KHÔNG có cột nào nói "hợp đồng này có phụ lục" ở phía cha -- đó là thứ suy
-- ra được bằng một câu đếm, và cột như vậy là nguồn sự thật thứ hai, sớm muộn
-- lệch với sự thật thứ nhất.
--
-- Trục LỊCH của hợp đồng cha KHÔNG đổi khi phụ lục gia hạn (quyết định
-- 2026-09-17). end_date của cha là thứ ghi trên bản đã ký; phụ lục mang điều
-- khoản mới và tự nó là một hợp đồng có thời hạn riêng. Nên computeStatus và
-- STATUS_CASE_SQL giữ nguyên, không đọc gì từ cột này; màn hình bù lại bằng
-- nhãn "có phụ lục" trên hợp đồng cha.

INSERT INTO schema_migrations (version) VALUES ('V29__add_contract_amendments__ndat2003');

ALTER TABLE `contracts`
  ADD COLUMN `parent_contract_id` int DEFAULT NULL AFTER `contract_id`,
  ADD KEY `idx_contracts_parent` (`parent_contract_id`),
  ADD CONSTRAINT `fk_contracts_parent`
      FOREIGN KEY (`parent_contract_id`) REFERENCES `contracts` (`contract_id`);

-- ---------------------------------------------------------------------------
-- Một phụ lục cho bộ dữ liệu demo
-- ---------------------------------------------------------------------------
-- Không có dòng nào thì khối "Phụ lục" trên trang hợp đồng mở ra là trống ở
-- mọi hợp đồng, và không ai kiểm được rằng nó hiển thị đúng.
--
-- Treo vào hợp đồng bán đã ký có giá trị lớn nhất: phụ lục gia hạn/bổ sung
-- thường rơi vào đúng nhóm đó, và chọn theo điều kiện thay vì ghi cứng id thì
-- migration còn chạy được trên CSDL dựng từ schema.sql (bảng rỗng -> mệnh đề
-- SELECT trả 0 dòng, cả khối tự bỏ qua, không lỗi).
--
-- Phụ lục gieo ở trạng thái 'Đã ký' cùng người phụ trách với cha. Thời hạn của
-- nó nối tiếp ngày kết thúc của cha -- đó là hình dạng của một phụ lục gia
-- hạn, và nó cũng cho thấy trục lịch của cha KHÔNG chạy theo (xem ghi chú
-- trên): mở hợp đồng cha ra vẫn thấy ngày kết thúc cũ.
INSERT INTO `contracts`
    (`parent_contract_id`, `contract_code`, `title`, `contract_type`, `direction`,
     `progress_status`, `signing_date`, `effective_date`, `end_date`,
     `enterprise_id`, `owner_id`, `status`, `contract_value`, `signing_place`)
SELECT p.`contract_id`,
       CONCAT(p.`contract_code`, '/PL01'),
       CONCAT(N'Phụ lục 01 — gia hạn và bổ sung hạng mục: ', p.`title`),
       p.`contract_type`,
       p.`direction`,
       N'Đã ký',
       p.`end_date`,
       p.`end_date`,
       DATE_ADD(p.`end_date`, INTERVAL 6 MONTH),
       p.`enterprise_id`,
       p.`owner_id`,
       N'Chưa hiệu lực',
       250000000.00,
       N'Hà Nội'
FROM `contracts` p
WHERE p.`direction` = N'Bán'
  AND p.`progress_status` = N'Đã ký'
  AND p.`is_deleted` = 0
  AND p.`parent_contract_id` IS NULL
  AND p.`end_date` IS NOT NULL
  AND p.`contract_value` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `contracts` c2 WHERE c2.`parent_contract_id` = p.`contract_id`)
ORDER BY p.`contract_value` DESC, p.`contract_id`
LIMIT 1;

-- Nhật ký cho phụ lục vừa gieo. HAI dòng, ở HAI hợp đồng khác nhau -- giống
-- hệt thứ ContractDAO sinh ra khi lập phụ lục thật:
--   * dòng 'Lập phụ lục' ghi lên hợp đồng CHA, để mở cha ra là thấy;
--   * dòng 'Khởi tạo' ghi lên chính phụ lục, nói rõ nó sửa cho hợp đồng nào.
INSERT INTO `contract_history` (`contract_id`, `event_type`, `detail`, `changed_by`, `changed_at`)
SELECT c.`parent_contract_id`, N'Lập phụ lục',
       CONCAT(N'Lập phụ lục ', c.`contract_code`, N' — gieo sẵn cho bộ dữ liệu demo.'),
       c.`owner_id`, COALESCE(c.`signing_date`, NOW())
FROM `contracts` c
WHERE c.`contract_code` LIKE '%/PL01'
  AND c.`parent_contract_id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `contract_history` h
                  WHERE h.`contract_id` = c.`parent_contract_id`
                    AND h.`event_type` = N'Lập phụ lục');

INSERT INTO `contract_history` (`contract_id`, `event_type`, `detail`, `changed_by`, `changed_at`)
SELECT c.`contract_id`, N'Khởi tạo',
       CONCAT(N'Phụ lục của hợp đồng ', p.`contract_code`,
              N'. Gieo sẵn cho bộ dữ liệu demo, không phải ghi nhận lúc xảy ra.'),
       c.`owner_id`, COALESCE(c.`signing_date`, NOW())
FROM `contracts` c
JOIN `contracts` p ON p.`contract_id` = c.`parent_contract_id`
WHERE c.`contract_code` LIKE '%/PL01'
  AND NOT EXISTS (SELECT 1 FROM `contract_history` h WHERE h.`contract_id` = c.`contract_id`);

INSERT INTO `contract_history`
    (`contract_id`, `event_type`, `detail`, `from_status`, `to_status`, `changed_by`, `changed_at`)
SELECT c.`contract_id`, N'Ký hợp đồng', N'Nháp → Đã ký', N'Nháp', N'Đã ký',
       c.`owner_id`, c.`signing_date`
FROM `contracts` c
WHERE c.`contract_code` LIKE '%/PL01'
  AND c.`signing_date` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `contract_history` h
                  WHERE h.`contract_id` = c.`contract_id` AND h.`event_type` = N'Ký hợp đồng');
