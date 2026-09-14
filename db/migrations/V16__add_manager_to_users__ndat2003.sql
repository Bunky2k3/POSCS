-- Cây tổ chức 2 tầng: thêm cột cấp trên cho nhân viên.
--
-- Khách hàng chốt ngày 14/09/2026 (xem PERMISSIONS.md, mục "Hierarchy-based
-- write access"): cấp trên là người tác động lên dữ liệu, kể cả dữ liệu của
-- cấp dưới; cấp dưới chỉ xem và gửi yêu cầu lên khi muốn đổi. Trước đây bảng
-- users không có chỗ nào ghi ai là cấp trên của ai, nên quyền chỉ xét được
-- theo vai trò -- không phân biệt nổi quản lý vùng với nhân viên cầm tỉnh dù
-- cả hai cùng mang role Sales.
--
-- Mô hình đã chốt với khách là ĐÚNG HAI TẦNG:
--
--   manager_id IS NULL     -> tầng trên (quản lý vùng), hoặc người chưa xếp
--                             vào cây tổ chức
--   manager_id IS NOT NULL -> tầng lá (nhân viên cầm tỉnh), cấp trên là người
--                             mà cột này trỏ tới
--
-- Chỉ lưu chiều CON -> CHA, không có bảng phân công riêng: quản lý vùng quản
-- ai thì suy ra bằng cách tìm ngược những người trỏ về mình. Lưu cả hai chiều
-- là tự tạo ra hai nguồn sự thật rồi có ngày lệch nhau.
--
-- Để NULL và không có giá trị mặc định là CÓ CHỦ Ý -- đây là chốt an toàn khi
-- triển khai: ngay sau khi chạy migration này thì chưa ai có cấp trên, nghĩa
-- là chưa ai bị coi là cấp dưới, nên KHÔNG AI bị mất quyền đang có. Việc siết
-- quyền chỉ thực sự bắt đầu với từng người khi cây tổ chức được nhập thật.
-- Bảng phân công thật (ai quản vùng nào, ai cầm tỉnh nào) vẫn đang chờ khách
-- hàng cung cấp.
--
-- Ở đây KHÔNG có ràng buộc CHECK chặn "tự làm cấp trên của chính mình", dù
-- đó là điều đầu tiên muốn chặn. Lý do là giới hạn của MySQL chứ không phải
-- quên: CHECK không được tham chiếu cột AUTO_INCREMENT, mà user_id đúng là
-- cột đó -- chạy thử ra thẳng lỗi
--
--   ERROR 3818 (HY000): Check constraint 'chk_users_manager_not_self'
--   cannot refer to an auto-increment column.
--
-- Nên toàn bộ việc giữ cây đúng HAI tầng nằm ở tầng ứng dụng, và nằm ở hai
-- chỗ chứ không phải một: EmployeeDAO.findEligibleManagers chỉ đưa ra những
-- người bản thân chưa có cấp trên (ô chọn không mời gọi làm sai), còn
-- EmployeeController.isValidManagerChoice kiểm lại cả "không phải chính mình"
-- lẫn "người đó chưa có cấp trên" ngay trước khi ghi -- vì form thì ai cũng
-- sửa được, một request tự dựng không bị ô chọn ràng buộc.

INSERT INTO schema_migrations (version) VALUES ('V16__add_manager_to_users__ndat2003');

ALTER TABLE `users`
  ADD COLUMN `manager_id` int NULL AFTER `department_id`,
  ADD CONSTRAINT `fk_users_manager`
    FOREIGN KEY (`manager_id`) REFERENCES `users` (`user_id`);
