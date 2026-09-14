-- Phiếu hỗ trợ đang kể thiếu một khúc: có "mô tả sự cố" (hiện tượng khách
-- báo) và "kết quả xử lý" (đã làm gì), nhưng không có chỗ nào ghi VÌ SAO nó
-- hỏng. Khách hàng yêu cầu mạch phiếu phải là hiện tượng -> nguyên nhân ->
-- kết quả, và phần nguyên nhân do kỹ thuật viên đánh giá sau khi xuống hiện
-- trường, chứ không phải do người tiếp nhận đoán lúc lập phiếu.
--
-- Tách làm HAI cột chứ không nhét chung vào một ô chữ:
--
--  - root_cause: diễn giải tự do, mỗi ca một khác ("gãy chân cắm nguồn do va
--    đập khi vận chuyển"). TEXT giống description/resolution_summary vì kỹ
--    thuật viên gõ bao nhiêu cũng được.
--  - cause_category: xếp ca đó vào một nhóm cố định (do vận chuyển / do lắp
--    đặt / do thiết bị / khác). Đây mới là cột thống kê được -- đếm "tháng
--    này bao nhiêu ca lỗi do lắp đặt" không thể làm trên một ô chữ tự do.
--
-- Cả hai đều NULL: toàn bộ phiếu đã có từ trước chưa ai đánh giá nguyên nhân,
-- và ngay cả phiếu mới cũng chưa biết nguyên nhân lúc vừa tiếp nhận -- NOT
-- NULL sẽ buộc phải điền bừa một giá trị vô nghĩa ngay từ lúc lập phiếu.
--
-- Chưa ràng buộc cause_category bằng ENUM/bảng danh mục: danh sách nhóm còn
-- có thể thay đổi theo phản hồi của khách, mà đổi ENUM là ALTER TABLE cả
-- bảng. Hiện danh sách chốt ở ô chọn trên form (updateTicket.jsp), giống
-- cách ticket_type/priority đang làm.

INSERT INTO schema_migrations (version) VALUES ('V14__add_root_cause_to_technicalrequests__ndat2003');

ALTER TABLE `technicalrequests`
  ADD COLUMN `root_cause` text COLLATE utf8mb4_unicode_ci NULL AFTER `description`,
  ADD COLUMN `cause_category` varchar(100) COLLATE utf8mb4_unicode_ci NULL AFTER `root_cause`;
