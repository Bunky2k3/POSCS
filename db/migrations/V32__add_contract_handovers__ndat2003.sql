-- Bàn giao hợp đồng giữa các phòng: Kinh doanh soạn xong thì chuyển xuống Kế
-- toán và Dự án chờ làm việc tiếp theo.
--
-- Yêu cầu của khách hàng (giám đốc công ty), làm rõ 2026-09-18: ngoài việc số
-- hoá, ông ấy muốn THEO DÕI và KIỂM SOÁT tiến độ hợp đồng của từng nhân viên
-- phụ trách -- không phải thêm một bảng thống kê nữa. Câu hỏi ông ấy hỏi là
-- "hợp đồng này đang nằm ở đâu, nằm bao lâu rồi", nên thứ phải lưu là CHẶNG
-- XỬ LÝ có mốc thời gian, không phải một cột "phòng đang giữ".
--
-- MỘT CỘT KHÔNG ĐỦ, hai lý do:
--   * Kế toán và Dự án nhận CÙNG LÚC (chốt 2026-09-18), nên tại một thời điểm
--     có hai phòng cùng giữ -- một cột chỉ nói được một phòng.
--   * Cột chỉ nói được HIỆN TẠI. Bàn giao xong là mất sạch "đã chờ mấy ngày ở
--     phòng nào", đúng con số cần để trả lời câu hỏi của giám đốc.
--
-- ĐÂY KHÔNG PHẢI QUAN HỆ PHÒNG-PHÒNG. Kết luận cũ vẫn giữ: không dựng cấu
-- trúc tổ chức thứ năm nối phòng này với phòng kia. Bảng này nối HỢP ĐỒNG với
-- PHÒNG theo thời gian -- mỗi dòng là một lượt giao, đúng nghĩa sự kiện.
--
-- KHÔNG đụng tới `progress_status`. Nháp/Đã ký/Đã thanh lý là trạng thái PHÁP
-- LÝ của hợp đồng; chặng phòng ban là công việc NỘI BỘ. Một hợp đồng đã ký vẫn
-- có thể còn nằm ở Kế toán, và một bản nháp vẫn có thể đã qua cả hai phòng --
-- hai trục không suy ra nhau, đúng lẽ mà trục lịch và trục tiến độ để riêng.

INSERT INTO schema_migrations (version) VALUES ('V32__add_contract_handovers__ndat2003');

-- ---------------------------------------------------------------------------
-- Hai phòng còn thiếu
-- ---------------------------------------------------------------------------
-- `departments` mới có Ban giám đốc, Kinh doanh, Kỹ thuật, CSKH -- đúng bốn
-- vai trò đang có. Kế toán và Dự án là hai phòng có thật trong luồng khách
-- hàng mô tả nhưng chưa từng tồn tại trong hệ thống.
--
-- NOT EXISTS: migration chạy một lần, nhưng bộ dữ liệu demo hay được nạp lại
-- bằng tay.
INSERT INTO `departments` (`department_name`)
SELECT * FROM (SELECT N'Kế toán') x
WHERE NOT EXISTS (SELECT 1 FROM `departments` WHERE `department_name` = N'Kế toán');

INSERT INTO `departments` (`department_name`)
SELECT * FROM (SELECT N'Dự án') x
WHERE NOT EXISTS (SELECT 1 FROM `departments` WHERE `department_name` = N'Dự án');

-- ---------------------------------------------------------------------------
-- Chặng xử lý
-- ---------------------------------------------------------------------------
CREATE TABLE `contract_handovers` (
  `handover_id`   int NOT NULL AUTO_INCREMENT,
  `contract_id`   int NOT NULL,
  -- Giao cho PHÒNG, không cho người: hai phòng mới chưa có nhân sự nào, giao
  -- cho phòng thì luồng chạy được ngay, và gán người vào sau không phải sửa gì.
  `department_id` int NOT NULL,
  `handed_at`     timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `handed_by`     int NOT NULL,
  -- NULL = phòng đó đang còn giữ. Đây là cột trả lời "đang chờ ở đâu", và hiệu
  -- của nó với handed_at trả lời "chờ bao lâu rồi".
  `done_at`       timestamp NULL DEFAULT NULL,
  `done_by`       int DEFAULT NULL,
  -- Ghi chú lúc giao (người giao dặn gì) và lúc xong (phòng nhận đã làm gì).
  -- Tách hai cột: gộp làm một thì lời của phòng nhận đè lên lời người giao.
  `handover_note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `done_note`     varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`handover_id`),
  -- Tra "hợp đồng này đang ở đâu" và "phòng này đang ôm những gì" là hai câu
  -- chạy suốt, mỗi câu một chỉ mục.
  KEY `idx_handover_contract` (`contract_id`),
  KEY `idx_handover_department_open` (`department_id`, `done_at`),
  KEY `fk_handover_handed_by` (`handed_by`),
  KEY `fk_handover_done_by` (`done_by`),
  -- KHÔNG cascade, cùng lẽ với contract_history: hợp đồng xoá MỀM, nên cascade
  -- không bao giờ nổ trong đường chạy bình thường.
  CONSTRAINT `fk_handover_contract` FOREIGN KEY (`contract_id`) REFERENCES `contracts` (`contract_id`),
  CONSTRAINT `fk_handover_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`department_id`),
  CONSTRAINT `fk_handover_handed_by` FOREIGN KEY (`handed_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `fk_handover_done_by` FOREIGN KEY (`done_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- KHÔNG đặt UNIQUE trên (contract_id, department_id): một hợp đồng bị trả về
-- sửa rồi bàn giao lại là chuyện bình thường, và mỗi lượt phải là một dòng
-- riêng thì mới đếm được "phải làm lại mấy lần". Luật "phòng đó đang còn giữ
-- thì không giao lại" nằm ở ContractDAO.handOverToDepartments, nơi kiểm được
-- trong transaction.

-- ---------------------------------------------------------------------------
-- Vài chặng cho bộ dữ liệu demo
-- ---------------------------------------------------------------------------
-- Không có dòng nào thì khối "Bàn giao xử lý" rỗng ở mọi hợp đồng và không ai
-- kiểm được nó hiển thị đúng -- bài học của phụ lục demo ở V29.
--
-- Ba tình huống cố ý khác nhau, vì đó là ba thứ giám đốc cần phân biệt:
--   * hợp đồng CẢ HAI phòng đã xong,
--   * hợp đồng một phòng xong một phòng còn chờ (điểm nghẽn),
--   * hợp đồng vừa giao, cả hai còn chờ.
INSERT INTO `contract_handovers`
    (`contract_id`, `department_id`, `handed_at`, `handed_by`, `done_at`, `done_by`, `handover_note`, `done_note`)
SELECT c.`contract_id`, d.`department_id`,
       x.handed_at, c.`owner_id`, x.done_at,
       CASE WHEN x.done_at IS NULL THEN NULL ELSE c.`owner_id` END,
       x.handover_note, x.done_note
FROM (
    SELECT N'01/2026/HĐKT-POSTEF' code, N'Kế toán' dept,
           DATE_SUB(NOW(), INTERVAL 75 DAY) handed_at, DATE_SUB(NOW(), INTERVAL 73 DAY) done_at,
           N'Đã soạn xong, nhờ kiểm điều khoản thanh toán.' handover_note,
           N'Điều khoản thanh toán hai kỳ, đã lập lịch thu.' done_note
    UNION ALL SELECT N'01/2026/HĐKT-POSTEF', N'Dự án',
           DATE_SUB(NOW(), INTERVAL 75 DAY), DATE_SUB(NOW(), INTERVAL 71 DAY),
           N'Kiểm khả năng thực hiện trước khi trình ký.',
           N'Đủ nhân lực và vật tư, nhận triển khai.'
    -- Điểm nghẽn: Kế toán xong từ lâu, Dự án vẫn đang giữ.
    UNION ALL SELECT N'07/2026/HĐKT-POSTEF', N'Kế toán',
           DATE_SUB(NOW(), INTERVAL 28 DAY), DATE_SUB(NOW(), INTERVAL 26 DAY),
           N'Kiểm điều khoản thanh toán cho đơn UPS.',
           N'Đã kiểm, đề nghị thu trước 50%.'
    UNION ALL SELECT N'07/2026/HĐKT-POSTEF', N'Dự án',
           DATE_SUB(NOW(), INTERVAL 28 DAY), NULL,
           N'Xem lịch lắp đặt cho trung tâm dữ liệu.', NULL
    -- Vừa bàn giao, cả hai phòng còn chờ.
    UNION ALL SELECT N'11/2026/HĐKT-POSTEF', N'Kế toán',
           DATE_SUB(NOW(), INTERVAL 3 DAY), NULL,
           N'Bản nháp đã chốt giá, nhờ kiểm trước khi trình ký.', NULL
    UNION ALL SELECT N'11/2026/HĐKT-POSTEF', N'Dự án',
           DATE_SUB(NOW(), INTERVAL 3 DAY), NULL,
           N'Xem tiến độ kéo cáp khu công nghiệp.', NULL
) x
JOIN `contracts` c ON c.`contract_code` = x.code AND c.`is_deleted` = 0
JOIN `departments` d ON d.`department_name` = x.dept
WHERE NOT EXISTS (SELECT 1 FROM `contract_handovers` h
                  WHERE h.`contract_id` = c.`contract_id` AND h.`department_id` = d.`department_id`);

-- Nhật ký cho các chặng vừa gieo -- đúng những dòng ContractDAO sinh ra khi
-- người dùng bàn giao thật.
INSERT INTO `contract_history` (`contract_id`, `event_type`, `detail`, `changed_by`, `changed_at`)
SELECT h.`contract_id`, N'Bàn giao phòng ban',
       CONCAT(N'Bàn giao cho phòng ', d.`department_name`, N' — gieo sẵn cho bộ dữ liệu demo.'),
       h.`handed_by`, h.`handed_at`
FROM `contract_handovers` h
JOIN `departments` d ON d.`department_id` = h.`department_id`
WHERE NOT EXISTS (SELECT 1 FROM `contract_history` hi
                  WHERE hi.`contract_id` = h.`contract_id`
                    AND hi.`event_type` = N'Bàn giao phòng ban'
                    AND hi.`detail` LIKE CONCAT('%', d.`department_name`, '%'));

INSERT INTO `contract_history` (`contract_id`, `event_type`, `detail`, `note`, `changed_by`, `changed_at`)
SELECT h.`contract_id`, N'Bàn giao phòng ban',
       CONCAT(N'Phòng ', d.`department_name`, N' báo đã xử lý xong.'),
       h.`done_note`, h.`done_by`, h.`done_at`
FROM `contract_handovers` h
JOIN `departments` d ON d.`department_id` = h.`department_id`
WHERE h.`done_at` IS NOT NULL;
