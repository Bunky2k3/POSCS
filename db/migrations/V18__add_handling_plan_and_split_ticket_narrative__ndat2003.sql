-- Tách mạch xử lý phiếu thành BA mục riêng, và dọn chúng ra khỏi lịch sử.
--
-- Phiếu đang có: mô tả sự cố (hiện tượng), root_cause (nguyên nhân),
-- resolution_summary (kết quả). Thiếu hẳn khúc GIỮA hai cái sau: định làm gì
-- để xử lý. Trên thực tế khúc đó vẫn được ghi -- nhưng ghi nhờ vào ghi chú
-- nội bộ của dòng lịch sử, chỗ không dành cho nó.
--
-- Hệ quả là lịch sử bị dùng sai mục đích. Nhìn bộ demo V15 gieo ra thì rõ:
--
--   'Xác định do va đập khi vận chuyển, đã thay ngăn hỏng và nghiệm thu.'
--   'Lỗi do lắp đặt sai thiết kế, đã chỉnh lại và nghiệm thu.'
--
-- Mỗi dòng như vậy nhồi cả nguyên nhân lẫn kết quả vào một ô vốn chỉ để trả
-- lời "vì sao lúc đó đổi trạng thái". Ai muốn biết nguyên nhân phải đi đọc
-- lịch sử; ai đọc lịch sử thì thấy cùng một nội dung lặp lại ở ba chỗ.
--
-- Migration này làm hai việc, tách bạch ở hai phần bên dưới:
--
--   1. Thêm cột handling_plan -- phương hướng xử lý, đứng giữa nguyên nhân và
--      kết quả. NULL được: lúc mới tiếp nhận chưa ai định hướng gì.
--   2. Dọn dữ liệu demo: điền phương hướng cho những phiếu đã có nguyên nhân,
--      và viết lại ghi chú lịch sử cho chúng chỉ còn nói về BƯỚC CHUYỂN trạng
--      thái, không kể lại nguyên nhân/kết quả nữa.
--
-- Phần 2 chỉ đụng đúng các phiếu và ghi chú do V15 gieo (so khớp nguyên văn),
-- nên nếu ai đã sửa tay thì không bị ghi đè.

INSERT INTO schema_migrations (version) VALUES ('V18__add_handling_plan_and_split_ticket_narrative__ndat2003');

-- ===== 1. Cột mới =====
ALTER TABLE `technicalrequests`
  ADD COLUMN `handling_plan` text COLLATE utf8mb4_unicode_ci NULL AFTER `cause_category`;

-- ===== 2. Điền phương hướng xử lý cho dữ liệu demo =====
-- Chỉ những phiếu đã có nguyên nhân mới có phương hướng: chưa biết vì sao
-- hỏng thì cũng chưa định được làm gì.
UPDATE technicalrequests SET handling_plan =
  'Thay ngăn ắc quy bị nứt, nạp cân bằng lại cả khối rồi đo kiểm tải 2 giờ trước khi bàn giao.'
  WHERE ticket_code = 'TK-0001';
UPDATE technicalrequests SET handling_plan =
  'Tháo toàn bộ hộp đấu nối tại 12 điểm, lắp lại gioăng đúng chiều và thử kín nước trước khi đóng điện.'
  WHERE ticket_code = 'TK-0003';
UPDATE technicalrequests SET handling_plan =
  'Đặt quạt tản nhiệt thay thế theo đúng mã, thay trong khung giờ thấp điểm để không phải ngắt tải.'
  WHERE ticket_code = 'TK-0005';
UPDATE technicalrequests SET handling_plan =
  'Thay mới cả 4 bình cùng lúc để tránh lệch nội trở giữa bình cũ và bình mới, sau đó cân bằng điện áp.'
  WHERE ticket_code = 'TK-0006';
UPDATE technicalrequests SET handling_plan =
  'Hạ cáp tại 2 điểm treo sai, nắn lại theo đúng bán kính uốn cho phép rồi đo OTDR nghiệm thu.'
  WHERE ticket_code = 'TK-0007';
UPDATE technicalrequests SET handling_plan =
  'Đổi bộ chia quang mới theo chính sách bảo hành vận chuyển, đồng thời siết lại quy cách chèn lót cho lô sau.'
  WHERE ticket_code = 'TK-0010';
UPDATE technicalrequests SET handling_plan =
  'Chỉnh khung đỡ về đúng 25 độ và dịch giàn pin 1,5 mét ra khỏi vùng bóng cột anten.'
  WHERE ticket_code = 'TK-0011';
UPDATE technicalrequests SET handling_plan =
  'Thay module rectifier mới từ kho dự phòng, gửi module lỗi về hãng đổi bảo hành và theo dõi các module cùng lô.'
  WHERE ticket_code = 'TK-0012';
UPDATE technicalrequests SET handling_plan =
  'Đổi cuộn cáp mới cho đại lý, phổ biến lại giới hạn xếp chồng 4 tầng cho đơn vị vận chuyển.'
  WHERE ticket_code = 'TK-0014';
UPDATE technicalrequests SET handling_plan =
  'Vệ sinh và thay tiếp điểm contactor, bổ sung gioăng kín tủ; đề xuất khách chuyển sang thiết bị có cấp bảo vệ cao hơn.'
  WHERE ticket_code = 'TK-0015';
UPDATE technicalrequests SET handling_plan =
  'Nạp lại toàn bộ ắc quy và đo dung lượng thực tế, lập báo cáo khuyến nghị bổ sung số bình còn thiếu.'
  WHERE ticket_code = 'TK-0016';

-- ===== 3. Trả lịch sử về đúng việc của nó =====
-- Ghi chú lịch sử chỉ trả lời "vì sao lúc đó đổi trạng thái". Nguyên nhân,
-- phương hướng và kết quả giờ đã có ô riêng, không kể lại ở đây nữa.
UPDATE technicalrequesthistory SET internal_note = 'Đã xử lý xong, khách nghiệm thu và đồng ý đóng phiếu.'
  WHERE internal_note IN (
    'Xác định do va đập khi vận chuyển, đã thay ngăn hỏng và nghiệm thu.',
    'Lỗi do lắp đặt sai thiết kế, đã chỉnh lại và nghiệm thu.',
    'Do vận chuyển, đã đổi hàng mới và siết lại quy cách đóng gói.',
    'Do xếp chồng quá tầng khi vận chuyển, đã đổi hàng.',
    'Ắc quy hết tuổi thọ, đã thay mới và bàn giao.',
    'Lỗi linh kiện từ nhà sản xuất, đã đổi bảo hành.',
    'Nguyên nhân ngoài thiết kế dự phòng, đã khuyến nghị bổ sung ắc quy.'
  );

UPDATE technicalrequesthistory SET internal_note = 'Kỹ thuật viên nhận phiếu và bắt đầu xử lý.'
  WHERE internal_note IN (
    'Đã khảo sát 12 điểm, xác định nguyên nhân do lắp sai gioăng.',
    'Đo kiểm tại chỗ, nghi quạt tản nhiệt hỏng bạc đạn.',
    'Đo OTDR, khoanh vùng 2 điểm treo nghi vấn.',
    'Lập biên bản hàng hỏng, đối chiếu quy cách đóng gói.',
    'Khảo sát góc nghiêng và vùng bóng che.',
    'Thay thử module dự phòng để khoanh vùng lỗi.',
    'Kiểm tra mẫu và truy lại lộ trình vận chuyển.',
    'Mở máy kiểm tra, thấy tiếp điểm contactor bị oxy hoá.',
    'Kiểm tra dung lượng ắc quy sau khi mất điện kéo dài.'
  );
