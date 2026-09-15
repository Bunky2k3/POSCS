-- Nhật ký thay đổi hợp đồng.
--
-- Cho tới giờ hợp đồng KHÔNG có dấu vết nào. Thêm/gỡ hàng hoá
-- (contractproducts) là DELETE cứng, và ContractController chỉ LOG.warn khi
-- thao tác THẤT BẠI -- nghĩa là lần sửa thành công, đúng cái cần truy, lại là
-- lần không để lại gì. Sửa thông tin hợp đồng cũng vậy: update đè thẳng, giá
-- trị cũ mất luôn. "Nhật ký" duy nhất là file log của ứng dụng, không phải
-- bảng, nên không tra được từ giao diện và bị xoay vòng theo cấu hình log.
--
-- Với hợp đồng đã ký thì nội dung là chứng cứ pháp lý, nên đây là lỗ thật chứ
-- không phải chuyện tiện dụng.
--
-- KHÔNG có ON DELETE CASCADE -- khác technicalrequesthistory. Lịch sử tồn tại
-- để trả lời "ai đã đổi gì", nên nó phải sống lâu hơn thứ nó nói về. Hợp đồng
-- vốn xoá mềm (is_deleted) nên cascade sẽ không bao giờ nổ trong đường chạy
-- bình thường; đặt cascade ở đây chỉ để lại một khẩu súng nạp sẵn cho người
-- sau lỡ tay xoá cứng một dòng và mang theo cả bằng chứng.
--
-- event_type để dạng chuỗi tự do thay vì ENUM: vòng đời hợp đồng còn đang mở
-- (thanh lý, phụ lục, bàn giao liên phòng đều chưa làm), mà ENUM thì mỗi lần
-- thêm một loại sự kiện là một ALTER TABLE khoá bảng. Danh sách giá trị hợp
-- lệ giữ ở tầng Java (ContractHistory.EVENT_*) -- cùng chỗ với code sinh ra
-- chúng, nên không có chuyện hai nơi định nghĩa lệch nhau.
--
-- detail lưu câu mô tả ĐÃ DỰNG SẴN để hiển thị, không lưu JSON diff. Cố ý:
-- thứ người đọc cần là "Số lượng: 10 -> 12", và dựng câu đó lúc ghi thì nó
-- đứng yên vĩnh viễn; dựng lúc đọc thì mỗi lần đổi cách hiển thị là lịch sử
-- cũ kể lại một câu chuyện hơi khác.
--
-- note là lý do NGƯỜI DÙNG NHẬP, tách khỏi detail do hệ thống sinh. Trộn hai
-- thứ vào một cột thì không còn phân biệt được máy ghi hay người khai.
--
-- from_status/to_status NULL với các dòng không phải chuyển trạng thái. Hai
-- cột này để sẵn cho trục tiến độ (đợt sau) -- thêm cột vào bảng rỗng bây giờ
-- rẻ hơn ALTER TABLE khi nó đã có dữ liệu thật.

INSERT INTO schema_migrations (version) VALUES ('V23__add_contract_history__ndat2003');

CREATE TABLE `contract_history` (
  `history_id`  int NOT NULL AUTO_INCREMENT,
  `contract_id` int NOT NULL,
  -- 'Khởi tạo' | 'Sửa thông tin' | 'Thêm hàng hoá' | 'Gỡ hàng hoá' | 'Huỷ bản ghi'
  -- (đợt sau thêm 'Thanh lý', 'Lập phụ lục', 'Bàn giao phòng ban')
  `event_type`  varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `detail`      varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  -- Chỉ có giá trị với dòng chuyển trạng thái tiến độ; NULL với dòng sửa đổi.
  `from_status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `to_status`   varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `changed_by`  int NOT NULL,
  `changed_at`  timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  -- Lý do do người dùng nhập; bắt buộc với 'Huỷ bản ghi', tuỳ chọn ở chỗ khác.
  `note`        text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`history_id`),
  KEY `idx_contract_history_contract` (`contract_id`),
  KEY `changed_by` (`changed_by`),
  CONSTRAINT `fk_contract_history_contract` FOREIGN KEY (`contract_id`) REFERENCES `contracts` (`contract_id`),
  CONSTRAINT `fk_contract_history_user` FOREIGN KEY (`changed_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dựng lại một dòng mở đầu cho các hợp đồng đã có từ trước tính năng này.
--
-- Không có nó thì mọi hợp đồng hiện tại mở ra là một dòng thời gian trống
-- trơn, trông như tính năng hỏng chứ không phải như "chưa có gì xảy ra".
--
-- Dòng này là SUY RA, không phải ghi nhận: changed_by lấy owner_id (người
-- phụ trách hiện tại, chưa chắc là người đã tạo) và changed_at lấy created_at.
-- Nói thẳng điều đó trong chính câu detail -- ai đọc lịch sử cũng phải phân
-- biệt được đâu là bằng chứng, đâu là thứ mình dựng lại sau.
INSERT INTO `contract_history` (`contract_id`, `event_type`, `detail`, `changed_by`, `changed_at`)
SELECT c.contract_id,
       'Khởi tạo',
       CONCAT('Hợp đồng có trước khi bật nhật ký. Dòng này dựng lại từ ngày tạo bản ghi, ',
              'không phải ghi nhận lúc xảy ra; người đứng tên là người phụ trách hiện tại.'),
       c.owner_id,
       COALESCE(c.created_at, NOW())
FROM contracts c;
