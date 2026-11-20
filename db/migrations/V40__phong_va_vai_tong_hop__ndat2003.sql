-- Phòng và vai "Tổng hợp" (Task 9.2): gộp hai phòng Kế toán, Dự án thành MỘT
-- phòng Tổng hợp, thêm vai Tổng hợp vào `roles`.
--
-- Lý do (chốt 19/11/2026, mục 13 "Quyết định đã chốt" của task_tracker): chi
-- nhánh thật có khoảng 12 người, ba khối -- Ban giám đốc, Phòng kinh doanh,
-- Phòng tổng hợp. Không có phòng Kế toán và phòng Dự án riêng như V32 giả
-- định: kế toán, nhận bàn giao hợp đồng, thu tiền đều do Phòng tổng hợp làm.
-- Giữ hai phòng thì mỗi hợp đồng phải bàn giao hai lượt cho cùng một nhóm
-- người, và hàng chờ bàn giao hiện mỗi hợp đồng hai lần.
--
-- Vai Tổng hợp: xem khách hàng, hợp đồng, sản phẩm; không vào phiếu hỗ trợ;
-- xác nhận bàn giao cho phòng mình; ghi nhận kỳ thanh toán đã thu (không lập,
-- không xoá kỳ). Quyền nằm ở PermissionService / AccessControl, bảng này chỉ
-- có tên vai -- xem PERMISSIONS.md.
--
-- ĐỔI TÊN "Kế toán" thành "Tổng hợp" chứ không thêm phòng mới rồi xoá: giữ
-- nguyên department_id, nên mọi chặng bàn giao và nhân viên đang trỏ vào Kế
-- toán tự thuộc Tổng hợp, không phải chuyển.
--
-- Phòng "Kỹ thuật" KHÔNG xoá ở đây (Task 9.3 xoá cùng lúc với vai Kỹ thuật,
-- sau khi chuyển nhân viên của phòng đó sang Kinh doanh). Ở đây chỉ chuyển
-- các chặng bàn giao của phòng Kỹ thuật sang Tổng hợp: từ nay Tổng hợp là
-- phòng DUY NHẤT nhận bàn giao (SPEC 5.4).
--
-- Thứ tự bắt buộc như V37, V38: chuyển hết nhân viên và chặng bàn giao sang
-- phòng mới TRƯỚC, rồi mới xoá phòng cũ -- ngược lại DELETE bị khoá ngoại
-- (users.department_id, contract_handovers.department_id) chặn lại.

INSERT INTO schema_migrations (version) VALUES ('V40__phong_va_vai_tong_hop__ndat2003');

-- ---------------------------------------------------------------------------
-- 1. Phòng Tổng hợp
-- ---------------------------------------------------------------------------
UPDATE `departments`
SET `department_name` = N'Tổng hợp'
WHERE `department_name` = N'Kế toán';

-- CSDL không có phòng Kế toán (chưa chạy V32) thì câu trên không đổi dòng
-- nào; tạo phòng Tổng hợp mới để các bước sau luôn có chỗ chuyển tới.
INSERT INTO `departments` (`department_name`)
SELECT * FROM (SELECT N'Tổng hợp') x
WHERE NOT EXISTS (SELECT 1 FROM `departments` WHERE `department_name` = N'Tổng hợp');

-- ---------------------------------------------------------------------------
-- 2. Nhân viên phòng Dự án sang Tổng hợp
-- ---------------------------------------------------------------------------
UPDATE `users`
SET `department_id` = (SELECT `department_id` FROM `departments` WHERE `department_name` = N'Tổng hợp')
WHERE `department_id` = (SELECT `department_id` FROM `departments` WHERE `department_name` = N'Dự án');

-- ---------------------------------------------------------------------------
-- 3. MỌI chặng bàn giao của phòng Dự án và phòng Kỹ thuật sang Tổng hợp
-- ---------------------------------------------------------------------------
-- Cả chặng đã xong lẫn chặng đang mở. Chặng đã xong giữ nguyên mốc thời gian,
-- người xác nhận, ghi chú -- chỉ đổi phòng.
UPDATE `contract_handovers`
SET `department_id` = (SELECT `department_id` FROM `departments` WHERE `department_name` = N'Tổng hợp')
WHERE `department_id` IN (
    SELECT `department_id` FROM `departments` WHERE `department_name` IN (N'Dự án', N'Kỹ thuật')
);

-- ---------------------------------------------------------------------------
-- 4. Đóng chặng trùng: một hợp đồng chỉ còn MỘT chặng đang mở ở Tổng hợp
-- ---------------------------------------------------------------------------
-- Luật gộp chốt 19/11/2026: hợp đồng nào sau bước 3 có từ hai chặng CÙNG ĐANG
-- MỞ ở Tổng hợp (từng bàn giao cùng lúc cho Kế toán và Dự án, hoặc thêm cả Kỹ
-- thuật) thì giữ chặng mở SỚM NHẤT, đóng các chặng còn lại. Không đóng thì
-- hàng chờ bàn giao hiện trùng hợp đồng, và ContractHandoverDAO vốn không cho
-- hai chặng cùng mở ở một phòng (HANDOVER_ALREADY_PENDING).
--
-- Người đóng ghi là tài khoản `admin`; CSDL không có `admin` thì lấy Admin có
-- user_id nhỏ nhất. Không có Admin nào thì câu này không đóng chặng nào (CSDL
-- production khởi đầu trống nên không có chặng nào để đóng).
--
-- ROW_NUMBER trong bảng dẫn xuất (cùng cách V28): MySQL phải dựng bảng tạm
-- trước, nên UPDATE được chính bảng contract_handovers mà không vướng lỗi
-- 1093. Mở sớm nhất = handed_at nhỏ nhất, trùng giờ thì handover_id nhỏ hơn.
UPDATE `contract_handovers` h
JOIN (
    SELECT ch.`handover_id`,
           ROW_NUMBER() OVER (
               PARTITION BY ch.`contract_id`
               ORDER BY ch.`handed_at`, ch.`handover_id`
           ) AS seq
    FROM `contract_handovers` ch
    JOIN `departments` d ON d.`department_id` = ch.`department_id`
    WHERE d.`department_name` = N'Tổng hợp'
      AND ch.`done_at` IS NULL
) ranked ON ranked.`handover_id` = h.`handover_id`
CROSS JOIN (
    SELECT u.`user_id`
    FROM `users` u
    JOIN `roles` r ON r.`role_id` = u.`role_id`
    WHERE r.`role_name` = 'Admin'
    ORDER BY (u.`username` = 'admin') DESC, u.`user_id`
    LIMIT 1
) closer
SET h.`done_at` = CURRENT_TIMESTAMP,
    h.`done_by` = closer.`user_id`,
    h.`done_note` = N'Đóng khi gộp phòng Kế toán, Dự án, Kỹ thuật thành Tổng hợp (19/11/2026)'
WHERE ranked.`seq` > 1;

-- ---------------------------------------------------------------------------
-- 5. Xoá phòng Dự án (đã hết nhân viên và chặng bàn giao)
-- ---------------------------------------------------------------------------
DELETE FROM `departments` WHERE `department_name` = N'Dự án';

-- ---------------------------------------------------------------------------
-- 6. Vai Tổng hợp
-- ---------------------------------------------------------------------------
-- `roles` không có khoá UNIQUE trên role_name, nên chặn chạy hai lần bằng
-- NOT EXISTS: thêm trùng thì ô Vai trò của form nhân viên hiện hai dòng.
INSERT INTO `roles` (`role_name`)
SELECT * FROM (SELECT N'Tổng hợp') x
WHERE NOT EXISTS (SELECT 1 FROM `roles` WHERE `role_name` = N'Tổng hợp');
