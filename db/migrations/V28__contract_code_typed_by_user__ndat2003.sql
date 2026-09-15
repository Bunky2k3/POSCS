-- Mã hợp đồng do NGƯỜI DÙNG NHẬP, không sinh tự động nữa.
--
-- V25 thêm `contract_number` bên cạnh `contract_code` với lập luận: mã là định
-- danh nội bộ do hệ thống sinh, số là thứ in trên giấy, hai thứ khác nhau nên
-- đừng gộp.
--
-- Tiền đề đó SAI, khách hàng chốt lại 2026-09-15: mã hợp đồng chính là số ghi
-- trên giấy, và người dùng tự nhập. Khi mã đã do người nhập thì "mã" với "số"
-- là một, và giữ hai cột là hai cột cho một khái niệm -- người nhập phải gõ
-- hai lần gần như cùng một thứ, rồi có ngày hai chỗ lệch nhau.
--
-- Nên gộp: giữ `contract_code` (đã UNIQUE, đã được 5 file khác dùng làm định
-- danh hiển thị -- phiếu hỗ trợ, màn sản phẩm, thông báo sắp hết hạn), bỏ
-- `contract_number`.
--
-- HỆ QUẢ Ở TẦNG JAVA, đừng chạy migration này một mình:
--   * ContractDAO.generateNextContractCode() và vòng thử lại khi trùng mã
--     trong insert() KHÔNG còn nghĩa -- mã trùng giờ là lỗi nhập liệu, phải
--     báo cho người dùng chứ không được lặng lẽ sinh mã khác rồi lưu.
--   * Mã thành ô bắt buộc ở form tạo.

INSERT INTO schema_migrations (version) VALUES ('V28__contract_code_typed_by_user__ndat2003');

-- Giữ lại số đã nhập, nếu có: chạy trước khi bỏ cột. Chỉ ghi đè khi mã hiện
-- tại vẫn là mã máy sinh -- người đã tự sửa mã thì tôn trọng cái họ nhập.
UPDATE `contracts`
SET `contract_code` = `contract_number`
WHERE `contract_number` IS NOT NULL
  AND `contract_number` <> ''
  AND `contract_code` REGEXP '^HD-[0-9]+$';

ALTER TABLE `contracts` DROP COLUMN `contract_number`;

-- ---------------------------------------------------------------------------
-- Gieo lại mã cho dữ liệu demo
-- ---------------------------------------------------------------------------
-- CHỈ đụng những mã còn đúng dạng máy sinh 'HD-<số>'. Hợp đồng nào đã mang mã
-- thật do người nhập thì không động tới -- migration không được quyền ghi đè
-- dữ liệu người dùng gõ.
--
-- Đánh số theo NĂM KÝ, thứ tự ngày ký: 01/2026/HĐKT-POSTEF, 02/2026/... Đó là
-- cách đánh số hợp đồng kinh tế thường gặp, và nó cho bộ demo trông như thật
-- thay vì HD-0001 -- thứ khách hàng nhìn vào là biết ngay dữ liệu bịa.
--
-- Nháp chưa ký thì chưa có signing_date, lấy tạm created_at để vẫn có năm.
UPDATE `contracts` c
JOIN (
    SELECT contract_id,
           YEAR(COALESCE(signing_date, created_at)) AS yr,
           ROW_NUMBER() OVER (
               PARTITION BY YEAR(COALESCE(signing_date, created_at))
               ORDER BY COALESCE(signing_date, created_at), contract_id
           ) AS seq
    FROM `contracts`
    WHERE `contract_code` REGEXP '^HD-[0-9]+$'
) t ON t.contract_id = c.contract_id
SET c.`contract_code` = CONCAT(LPAD(t.seq, 2, '0'), '/', t.yr, '/HĐKT-POSTEF');

-- ---------------------------------------------------------------------------
-- Nhà cung cấp + hợp đồng mua cho bộ demo
-- ---------------------------------------------------------------------------
-- Trước bản này CSDL không có lấy một nhà cung cấp nào, cũng không có hợp đồng
-- mua nào (12 khách đều 'Khách mua', 15 hợp đồng đều 'Bán'). Nghĩa là hai mục
-- con "Nhà cung cấp" và "Hợp đồng mua" mở ra là trống trơn -- không demo được,
-- và cũng không ai kiểm được bộ lọc riêng của chúng.
--
-- KHÔNG ghi cứng user_id: migration này còn chạy trên CSDL dựng từ schema.sql
-- (test tích hợp) nơi bảng users rỗng, và ghi cứng id là vi phạm khoá ngoại.
-- Lấy một user bất kỳ, ưu tiên vai Sales; không có user nào thì mệnh đề SELECT
-- trả 0 dòng và cả khối gieo tự bỏ qua, không lỗi.
--
-- Cũng chặn chạy hai lần bằng NOT EXISTS trên mã: migration chỉ chạy một lần,
-- nhưng bộ dữ liệu demo hay được nạp lại bằng tay.

INSERT INTO `enterprises`
    (`enterprise_code`, `enterprise_name`, `customer_type`, `customer_group`, `tax_code`,
     `email`, `phone`, `account_owner_id`, `legal_representative`, `join_date`)
SELECT seed.* FROM (
    SELECT 'NCC-001' AS a, N'Công ty CP Vật liệu Cáp quang Đông Á' AS b, N'Nhà sản xuất' AS c,
           N'Thường' AS d, '0106553311' AS e, 'sales@capquangdonga.vn' AS f, '02438765432' AS g,
           (SELECT user_id FROM `users` WHERE is_deleted = 0
             ORDER BY CASE WHEN role_id = (SELECT role_id FROM `roles` WHERE role_name = 'Sales' LIMIT 1)
                           THEN 0 ELSE 1 END, user_id LIMIT 1) AS h,
           N'Phạm Quốc Hùng' AS i, '2024-03-12' AS j
    UNION ALL SELECT 'NCC-002', N'Công ty TNHH Thiết bị Viễn thông Tân Minh', N'Nhà nhập khẩu',
           N'Thân thiết', '0107884422', 'contact@tanminhtelecom.vn', '02439991234',
           (SELECT user_id FROM `users` WHERE is_deleted = 0
             ORDER BY CASE WHEN role_id = (SELECT role_id FROM `roles` WHERE role_name = 'Sales' LIMIT 1)
                           THEN 0 ELSE 1 END, user_id LIMIT 1),
           N'Đỗ Thị Hằng', '2023-07-05'
    UNION ALL SELECT 'NCC-003', N'Công ty CP Phân phối Linh kiện Bắc Hà', N'Nhà phân phối',
           N'Thường', '0108112233', 'kinhdoanh@bacha-linhkien.vn', '02436667788',
           (SELECT user_id FROM `users` WHERE is_deleted = 0
             ORDER BY CASE WHEN role_id = (SELECT role_id FROM `roles` WHERE role_name = 'Sales' LIMIT 1)
                           THEN 0 ELSE 1 END, user_id LIMIT 1),
           N'Ngô Văn Sơn', '2025-01-20'
    UNION ALL SELECT 'NCC-004', N'Công ty TNHH Dịch vụ Kỹ thuật Hà Thành', N'Đơn vị dịch vụ',
           N'Tiềm năng', '0109443322', 'dichvu@hathanhtech.vn', '02435554466',
           (SELECT user_id FROM `users` WHERE is_deleted = 0
             ORDER BY CASE WHEN role_id = (SELECT role_id FROM `roles` WHERE role_name = 'Sales' LIMIT 1)
                           THEN 0 ELSE 1 END, user_id LIMIT 1),
           N'Lý Minh Tuấn', '2025-09-02'
) AS seed
WHERE seed.h IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `enterprises` e2 WHERE e2.enterprise_code = seed.a);

INSERT INTO `enterprise_roles` (`enterprise_id`, `role`)
SELECT e.`enterprise_id`, N'Nhà cung cấp' FROM `enterprises` e
WHERE e.`enterprise_code` LIKE 'NCC-%'
  AND NOT EXISTS (SELECT 1 FROM `enterprise_roles` r
                  WHERE r.enterprise_id = e.enterprise_id AND r.role = N'Nhà cung cấp');

-- Hợp đồng MUA: loại hợp đồng lấy từ bộ dành cho chiều mua (xem
-- ContractController.BUY_CONTRACT_TYPES), không dùng chữ của chiều bán.
INSERT INTO `contracts`
    (`contract_code`, `title`, `contract_type`, `direction`, `progress_status`,
     `signing_date`, `effective_date`, `end_date`, `enterprise_id`, `owner_id`,
     `status`, `contract_value`, `signing_place`)
SELECT seed.* FROM (
    SELECT N'01/2026/HĐMB-POSTEF' AS a, N'Mua sợi quang G657A1 phục vụ sản xuất quý I' AS b,
           N'Mua vật tư' AS c, N'Mua' AS d, N'Đã ký' AS e,
           DATE '2026-01-20' AS f, DATE '2026-02-01' AS g, DATE '2026-12-31' AS h,
           (SELECT enterprise_id FROM `enterprises` WHERE enterprise_code = 'NCC-001') AS i,
           (SELECT account_owner_id FROM `enterprises` WHERE enterprise_code = 'NCC-001') AS j,
           N'Đang hiệu lực' AS k, 3200000000.00 AS l, N'Hà Nội' AS m
    UNION ALL SELECT N'02/2026/HĐMB-POSTEF', N'Mua module quang SFP+ 10G nhập khẩu',
           N'Mua thiết bị', N'Mua', N'Đã ký',
           DATE '2026-03-05', DATE '2026-03-15', DATE '2027-03-14',
           (SELECT enterprise_id FROM `enterprises` WHERE enterprise_code = 'NCC-002'),
           (SELECT account_owner_id FROM `enterprises` WHERE enterprise_code = 'NCC-002'),
           N'Đang hiệu lực', 1850000000.00, N'Hà Nội'
    UNION ALL SELECT N'03/2026/HĐMB-POSTEF', N'Thuê bảo trì hệ thống dây chuyền kéo sợi',
           N'Thuê bảo trì', N'Mua', N'Nháp',
           NULL, NULL, NULL,
           (SELECT enterprise_id FROM `enterprises` WHERE enterprise_code = 'NCC-004'),
           (SELECT account_owner_id FROM `enterprises` WHERE enterprise_code = 'NCC-004'),
           N'Chưa hiệu lực', NULL, NULL
) AS seed
WHERE seed.i IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `contracts` c2 WHERE c2.contract_code = seed.a);

-- Nhật ký cho ba hợp đồng vừa gieo: không có dòng nào thì mở ra dòng thời gian
-- trống trơn, trông như tính năng hỏng (cùng lý do với phần backfill ở V23).
INSERT INTO `contract_history` (`contract_id`, `event_type`, `detail`, `changed_by`, `changed_at`)
SELECT c.contract_id, N'Khởi tạo',
       N'Hợp đồng gieo sẵn cho bộ dữ liệu demo. Dòng này dựng lại, không phải ghi nhận lúc xảy ra.',
       c.owner_id, COALESCE(c.signing_date, NOW())
FROM `contracts` c
WHERE c.`contract_code` LIKE '%/HĐMB-POSTEF'
  AND NOT EXISTS (SELECT 1 FROM `contract_history` h WHERE h.contract_id = c.contract_id);

INSERT INTO `contract_history`
    (`contract_id`, `event_type`, `detail`, `from_status`, `to_status`, `changed_by`, `changed_at`)
SELECT c.contract_id, N'Ký hợp đồng', N'Nháp → Đã ký', N'Nháp', N'Đã ký', c.owner_id, c.signing_date
FROM `contracts` c
WHERE c.`contract_code` LIKE '%/HĐMB-POSTEF' AND c.`signing_date` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `contract_history` h
                  WHERE h.contract_id = c.contract_id AND h.event_type = N'Ký hợp đồng');

-- Vài kỳ thanh toán cho hợp đồng mua, để màn kỳ thanh toán có gì mà xem.
-- KHÔNG đụng doanh thu: sumInvoiceAmount* đã lọc direction = 'Bán' nên các kỳ
-- của hợp đồng mua không cộng vào KPI doanh thu (xem ghi chú ở V21).
INSERT INTO `contract_payments` (`contract_id`, `invoice_amount`, `due_date`, `paid_date`)
SELECT c.contract_id, 1600000000.00, DATE '2026-03-31', DATE '2026-03-28'
FROM `contracts` c
WHERE c.`contract_code` = N'01/2026/HĐMB-POSTEF'
  AND NOT EXISTS (SELECT 1 FROM `contract_payments` p WHERE p.contract_id = c.contract_id);

INSERT INTO `contract_payments` (`contract_id`, `invoice_amount`, `due_date`, `paid_date`)
SELECT c.contract_id, 1600000000.00, DATE '2026-09-30', NULL
FROM `contracts` c
WHERE c.`contract_code` = N'01/2026/HĐMB-POSTEF'
  AND (SELECT COUNT(*) FROM `contract_payments` p WHERE p.contract_id = c.contract_id) = 1;
