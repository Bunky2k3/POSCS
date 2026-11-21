-- poscs:demo-only
-- Gieo lại TOÀN BỘ dữ liệu mẫu theo cơ cấu chi nhánh thật (Giai đoạn 9) và vòng
-- đời hợp đồng mới (Giai đoạn 10). Task 10.6, quyết định 16 trong task_tracker.md.
--
-- CHỈ DÙNG DEV/DEMO. Production bỏ qua file có dòng đầu "-- poscs:demo-only"
-- (Task 11.2) và không chép bảng users (Task 11.3). Mọi tài khoản dưới đây dùng
-- mật khẩu "Poscs@123", riêng admin dùng "Admin@123": đừng nạp file này lên máy
-- chủ chạy thật.
--
-- File làm hai việc:
--   1. Xoá bộ cũ: mọi dòng của mọi bảng KHÔNG phải danh mục, trừ schema_migrations.
--      Mười bảng danh mục giữ nguyên dữ liệu: roles, departments, provinces,
--      districts, productcategories, products, productimages, productcatalogues,
--      customer_evaluation_rules, branch_info (chỉ đặt updated_by về NULL để xoá
--      được users).
--   2. Gieo bộ mới. Phần này giống hệt khối "DỮ LIỆU MẪU, CHỈ DÙNG DEV/DEMO" ở
--      cuối db/schema.sql.
--
-- Không ghi cứng ID nào: khoá ngoại tra qua username, enterprise_code, tiêu đề
-- hợp đồng, mã sản phẩm. Mọi ngày tính từ CURDATE() để trạng thái theo lịch, tình
-- trạng kỳ thanh toán và bước giao hàng không trôi theo thời gian (như V30).
-- File tải lên (logo, ảnh đại diện) của bộ cũ vẫn nằm trên đĩa, file này không đụng.

INSERT INTO schema_migrations (version) VALUES ('V46__reseed_demo_theo_co_cau_that__ndat2003');

-- ---------------------------------------------------------------------------
-- 1. Xoá bộ cũ: con trước cha theo khoá ngoại trong schema.sql
-- ---------------------------------------------------------------------------
UPDATE branch_info SET updated_by = NULL;
DELETE FROM notifications;
DELETE FROM change_requests;
DELETE FROM technicalrequesthistory;
DELETE FROM technicalrequestdevices;
DELETE FROM technicalrequests;
DELETE FROM contract_documents;
DELETE FROM contract_links;
DELETE FROM contract_handovers;
DELETE FROM contract_history;
DELETE FROM contract_payments;
DELETE FROM contractproducts;
-- Phụ lục trước hợp đồng gốc: fk_contracts_parent không CASCADE (V29, như V30).
DELETE FROM contracts WHERE parent_contract_id IS NOT NULL;
DELETE FROM contracts;
DELETE FROM customer_lifecycle_events;
DELETE FROM enterprise_roles;
DELETE FROM enterprisecontacts;
DELETE FROM enterprises;
DELETE FROM user_provinces;
-- users tự trỏ vào chính nó qua manager_id.
UPDATE users SET manager_id = NULL;
DELETE FROM users;
DELETE FROM addresses;

-- ---------------------------------------------------------------------------
-- 2. Gieo bộ mới
-- ---------------------------------------------------------------------------

-- ===== Tài khoản =====
-- Cơ cấu chi nhánh thật (quyết định 13, 16), tên người đều giả:
--   Ban giám đốc, vai Admin: GĐ admin, PGĐ tranquocbao.
--   Kinh doanh, vai Sales, không ai có cấp trên: Trưởng phòng leminhhoang, Phó
--   phòng phamthutrang, nhân viên doducanh và vuthihang.
--   Tổng hợp, vai Tổng hợp: kế toán hoangthimai, thu tiền buivannam, nhận bàn giao
--   ngothilan (mới vào làm, còn mật khẩu tạm nên bị chuyển sang trang đổi mật khẩu).
--   dangvantung: Sales đã nghỉ (khoá tài khoản), vẫn là người phụ trách một hợp
--   đồng để thử việc Admin đổi người phụ trách (quyết định 11).
-- Tên đăng nhập theo đúng cách app sinh (EmployeeDAO.generateUniqueUsername), trừ
-- admin. Hồ sơ đủ giới tính, ngày sinh, CCCD, SĐT để đăng nhập không bị bắt bổ
-- sung hồ sơ (User.isProfileIncomplete). Mỗi tài khoản một salt, băm bằng
-- lib/jbcrypt-0.4.jar.
INSERT INTO users
  (username, password_hash, must_change_password, role_id, last_name, middle_name, first_name,
   gender, date_of_birth, citizen_id, phone, personal_email, address_id,
   department_id, manager_id, hire_date, is_deleted)
SELECT x.username, x.pw, x.must_change, r.role_id, x.last_name, x.middle_name, x.first_name,
       x.gender, x.dob, x.citizen_id, x.phone, CONCAT(x.username, '@example.com'), NULL,
       d.department_id, NULL, x.hire_date, x.is_deleted
FROM (
    SELECT 1 n, 'admin' username, '$2a$10$p4nRxudw0yecEvWehCOK7.rEV5m34Ih/E/DdHpKCsOZ4mdSsn7QDi' pw,
           0 must_change, 'Admin' role, 'Ban giám đốc' dept,
           'Nguyễn' last_name, 'Văn' middle_name, 'Thành' first_name, 'Nam' gender,
           DATE('1971-04-12') dob, '001071003581' citizen_id, '0981600001' phone,
           DATE('2012-03-01') hire_date, 0 is_deleted
    UNION ALL SELECT 2, 'tranquocbao', '$2a$10$KeRvSnEwKw3QFeCw2RIY.OywVvW4tDEucbYUtHzfBZXMmF4ezj5Z2',
           0, 'Admin', 'Ban giám đốc', 'Trần', 'Quốc', 'Bảo', 'Nam',
           DATE('1977-09-02'), '034077002816', '0981600002', DATE('2014-06-16'), 0
    UNION ALL SELECT 3, 'leminhhoang', '$2a$10$LSOx6fMQZTx.TEOl4d5n3uqEf88Euulqe9YILT0fEoiPSV.De1yJa',
           0, 'Sales', 'Kinh doanh', 'Lê', 'Minh', 'Hoàng', 'Nam',
           DATE('1984-03-21'), '001084006247', '0981600003', DATE('2015-08-03'), 0
    UNION ALL SELECT 4, 'phamthutrang', '$2a$10$03euSjbFFAAteNwldpy5qeluclGk2pNfilZQccCJz2h/iAncpejFC',
           0, 'Sales', 'Kinh doanh', 'Phạm', 'Thu', 'Trang', 'Nữ',
           DATE('1988-11-08'), '031188004139', '0981600004', DATE('2017-02-13'), 0
    UNION ALL SELECT 5, 'doducanh', '$2a$10$HfbLeVonpjGtmiEwBtO5e.V4m0jHw3GpDgFEuE9YUCfOITnMoOOE6',
           0, 'Sales', 'Kinh doanh', 'Đỗ', 'Đức', 'Anh', 'Nam',
           DATE('1994-06-17'), '025094007752', '0981600005', DATE('2020-07-01'), 0
    UNION ALL SELECT 6, 'vuthihang', '$2a$10$6zDsCr5flO.81wOPjVSmUevQq3LkBbQVuHcr0sNSNLIQgxXoYYjAK',
           0, 'Sales', 'Kinh doanh', 'Vũ', 'Thị', 'Hằng', 'Nữ',
           DATE('1996-02-25'), '014196003368', '0981600006', DATE('2022-04-18'), 0
    UNION ALL SELECT 7, 'hoangthimai', '$2a$10$IFeilPaac5vCTNhSRIkcQ.dmd7GrV3hyxXTi/QruJZ.geqzf7JUEy',
           0, 'Tổng hợp', 'Tổng hợp', 'Hoàng', 'Thị', 'Mai', 'Nữ',
           DATE('1986-07-30'), '001186005914', '0981600007', DATE('2013-10-07'), 0
    UNION ALL SELECT 8, 'buivannam', '$2a$10$W5OZ5UWwvQtzEh6m2KhwceTy/tsDe6xSDOYtqk/80OQmHN0pYOFs2',
           0, 'Tổng hợp', 'Tổng hợp', 'Bùi', 'Văn', 'Nam', 'Nam',
           DATE('1991-12-03'), '036091008427', '0981600008', DATE('2018-05-21'), 0
    UNION ALL SELECT 9, 'ngothilan', '$2a$10$X4IZJRXw5NL93.3RqZRNqOKpUW/KXDBAkl.cw0bfrUSe/bjLBLa5u',
           1, 'Tổng hợp', 'Tổng hợp', 'Ngô', 'Thị', 'Lan', 'Nữ',
           DATE('2001-04-19'), '001301002695', '0981600009', CURDATE() - INTERVAL 5 DAY, 0
    UNION ALL SELECT 10, 'dangvantung', '$2a$10$IT0G826DL.vBrn4BNSumNunjBI5ACwEPXJDLJX2SRklrHCd1OTmwi',
           0, 'Sales', 'Kinh doanh', 'Đặng', 'Văn', 'Tùng', 'Nam',
           DATE('1989-08-11'), '022089006031', '0981600010', DATE('2019-04-01'), 1
) x
JOIN roles r ON r.role_name = x.role
JOIN departments d ON d.department_name = x.dept
ORDER BY x.n;

-- ===== Địa bàn =====
-- Các tỉnh miền Bắc chia cho 4 Sales, mỗi tỉnh một người. Admin, Tổng hợp và
-- người đã nghỉ không giữ tỉnh nào.
INSERT INTO user_provinces (user_id, province_id)
SELECT u.user_id, p.province_id
FROM users u
JOIN provinces p ON (
        (u.username = 'leminhhoang'  AND p.province_name IN ('Thành phố Hà Nội', 'Tỉnh Bắc Ninh', 'Tỉnh Hưng Yên', 'Tỉnh Ninh Bình'))
     OR (u.username = 'phamthutrang' AND p.province_name IN ('Thành phố Hải Phòng', 'Tỉnh Quảng Ninh', 'Tỉnh Lạng Sơn', 'Tỉnh Cao Bằng'))
     OR (u.username = 'doducanh'     AND p.province_name IN ('Tỉnh Phú Thọ', 'Tỉnh Thái Nguyên', 'Tỉnh Tuyên Quang', 'Tỉnh Lào Cai'))
     OR (u.username = 'vuthihang'    AND p.province_name IN ('Tỉnh Sơn La', 'Tỉnh Điện Biên', 'Tỉnh Lai Châu'))
)
ORDER BY u.user_id, p.province_id;

-- ===== Khách hàng và nhà cung cấp =====
-- 4 khách hàng (bên mua), mỗi Sales một khách ở tỉnh của mình: người phụ trách là
-- Sales giữ tỉnh đó, người hỗ trợ là một Sales khác. 1 nhà cung cấp, người phụ
-- trách là Sales tạo ra nó. Mã theo hai dãy KH-xxxx / NCC-xxx (BR-52). Tên doanh
-- nghiệp, mã số thuế, SĐT đều giả; email @example.com.
-- Không gieo customer_lifecycle_events: app không ghi dòng vòng đời nào khi tạo
-- khách hàng (dòng duy nhất app tự ghi là "Đánh giá xếp hạng" lúc người dùng bấm
-- đánh giá), nên xếp hạng quan hệ cũng để trống như khách vừa tạo.
INSERT INTO addresses (street_and_local_name, districts_id)
SELECT x.street, d.districts_id
FROM (
    SELECT 1 n, '25 Phố Trần Thái Tông' street, 'Phường Cầu Giấy' ward, 'Thành phố Hà Nội' province
    UNION ALL SELECT 2, '128 Đường Tô Hiệu', 'Phường Lê Chân', 'Thành phố Hải Phòng'
    UNION ALL SELECT 3, '46 Đường Hùng Vương', 'Phường Việt Trì', 'Tỉnh Phú Thọ'
    UNION ALL SELECT 4, '17 Đường Chu Văn Thịnh', 'Phường Tô Hiệu', 'Tỉnh Sơn La'
    UNION ALL SELECT 5, '68 Đường Lý Thái Tổ', 'Phường Kinh Bắc', 'Tỉnh Bắc Ninh'
) x
JOIN provinces p ON p.province_name = x.province
JOIN districts d ON d.province_id = p.province_id AND d.districts_name = x.ward
ORDER BY x.n;

INSERT INTO enterprises
  (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website,
   address_id, account_owner_id, support_owner_id, legal_representative, status, join_date)
SELECT x.code, x.name, x.ctype, x.cgroup, x.tax, x.email, x.phone, x.website,
       a.address_id, o.user_id, s.user_id, x.rep, 'Active', CURDATE() + INTERVAL x.join_d DAY
FROM (
    SELECT 'KH-0001' code, 'Công ty Cổ phần Mạng số Hồ Tây' name, 'Nhà mạng viễn thông' ctype,
           'VIP' cgroup, '0109876501' tax, 'lienhe@hotaynet.example.com' email, '02437650001' phone,
           'https://hotaynet.example.com' website, '25 Phố Trần Thái Tông' street,
           'leminhhoang' owner, 'phamthutrang' support, 'Nguyễn Hữu Phước' rep, -900 join_d
    UNION ALL SELECT 'KH-0002', 'Công ty TNHH Hạ tầng mạng Duyên Hải', 'Nhà thầu thi công',
           'Thân thiết', '0209876502', 'lienhe@duyenhainet.example.com', '02253650002',
           'https://duyenhainet.example.com', '128 Đường Tô Hiệu',
           'phamthutrang', 'leminhhoang', 'Trần Đức Hải', -600
    UNION ALL SELECT 'KH-0003', 'Công ty Cổ phần Kỹ thuật Viễn thông Trung Du', 'Nhà thầu thi công',
           'Thường', '2609876503', 'lienhe@trungdutel.example.com', '02103650003',
           'https://trungdutel.example.com', '46 Đường Hùng Vương',
           'doducanh', 'vuthihang', 'Phùng Văn Lực', -500
    UNION ALL SELECT 'KH-0004', 'Công ty TNHH Thiết bị Viễn thông Tây Bắc', 'Đại lý phân phối',
           'Tiềm năng', '5509876504', 'lienhe@taybactel.example.com', '02123650004',
           'https://taybactel.example.com', '17 Đường Chu Văn Thịnh',
           'vuthihang', 'doducanh', 'Lò Văn Thắng', -60
    UNION ALL SELECT 'NCC-001', 'Công ty TNHH Thiết bị Điện Kinh Bắc', 'Nhà phân phối',
           'Thân thiết', '2309876505', 'lienhe@dienkinhbac.example.com', '02223650005',
           'https://dienkinhbac.example.com', '68 Đường Lý Thái Tổ',
           'leminhhoang', NULL, 'Nguyễn Văn Toàn', -800
) x
JOIN addresses a ON a.street_and_local_name = x.street
JOIN users o ON o.username = x.owner
LEFT JOIN users s ON s.username = x.support
ORDER BY x.code;

-- Vai của từng đơn vị, như CustomerService.insertCustomer ghi lúc tạo.
INSERT INTO enterprise_roles (enterprise_id, role)
SELECT e.enterprise_id, CASE WHEN e.enterprise_code LIKE 'NCC-%' THEN 'Nhà cung cấp' ELSE 'Khách mua' END
FROM enterprises e
ORDER BY e.enterprise_code;

INSERT INTO enterprisecontacts
  (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position)
SELECT e.enterprise_id, x.ln, x.mn, x.fn, x.phone, x.email, x.pos
FROM (
    SELECT 'KH-0001' code, 'Đào' ln, 'Thị' mn, 'Ngọc' fn, '0904120001' phone,
           'ngoc.dt@hotaynet.example.com' email, 'Trưởng phòng Kỹ thuật' pos
    UNION ALL SELECT 'KH-0002', 'Vũ', 'Minh', 'Khôi', '0904120002', 'khoi.vm@duyenhainet.example.com', 'Phó Giám đốc'
    UNION ALL SELECT 'KH-0003', 'Hà', 'Văn', 'Sơn', '0904120003', 'son.hv@trungdutel.example.com', 'Chỉ huy trưởng công trình'
    UNION ALL SELECT 'KH-0004', 'Cầm', 'Thị', 'Hoa', '0904120004', 'hoa.ct@taybactel.example.com', 'Phụ trách mua hàng'
    UNION ALL SELECT 'NCC-001', 'Lương', 'Văn', 'Hưng', '0904120005', 'hung.lv@dienkinhbac.example.com', 'Trưởng phòng Kinh doanh'
) x
JOIN enterprises e ON e.enterprise_code = x.code
ORDER BY x.code;

-- ===== Hợp đồng =====
-- Đúng 8 hợp đồng và 1 phụ lục, mỗi hợp đồng bày một chặng của vòng đời theo
-- cách chi nhánh làm (quyết định 14, 15):
--   1. Nháp vừa mở hồ sơ: chưa mã, chưa giấy.
--   2. Nháp "Chuẩn bị hàng": có Phiếu GQNCKH và Phiếu ĐCNB.
--   3. Nháp "Đã giao – chưa ký": Biên bản giao nhận hàng hoá 16 ngày trước, nên
--      lượt NotificationScheduler đầu tiên sinh nhắc ký (BR-62).
--   4. Đã ký, chưa được phòng Tổng hợp xác nhận bàn giao; một kỳ Thanh toán chưa
--      nộp hồ sơ.
--   5. Đã ký, đã bàn giao, hết hạn sau 20 ngày (thông báo sắp hết hạn, BR-17), có
--      1 phụ lục. Hai kỳ 80/20 như hợp đồng mẫu Task 10.4: Tạm ứng đã thu, Thanh
--      toán đã nộp hồ sơ, hạn thu sau hôm nay (Chờ thu).
--   6. Đã ký, do dangvantung (đã nghỉ) phụ trách; kỳ thanh toán quá hạn thu.
--   7. Đã thanh lý: đủ giấy, mọi kỳ đã thu, kỳ cuối thu thấp hơn số tiền kỳ.
--   8. Hợp đồng Mua với nhà cung cấp, nối với hợp đồng 5 (contract_links).
-- Hồ sơ mở lúc làm phiếu GQNCKH, biểu mẫu tạo không có ô ngày và hàng hoá, mã để
-- trống tới khi có số trên bản giấy (BR-58): hợp đồng đã ký được sửa thêm mã và
-- thời hạn trước hôm Ký hai ngày, ngày ký = ngày bấm Ký = ngày hiệu lực (BR-36).
-- Người ký bên mình: hợp đồng đã ký mang họ tên, chức vụ, SĐT, fax của GĐ
-- (tài khoản admin); bản Nháp để trống bốn ô như khi chưa khai GĐCN (quyết định
-- 10), branch_info giữ nguyên. Người phụ trách theo quyết định 11. Mã theo kiểu số
-- hợp đồng thật. Cột status là bản lưu trạng thái theo lịch, tính như
-- ContractValidator.computeStatus ngay lúc chạy.
-- Nhánh UNION ALL ĐẦU TIÊN có giá trị thật ở mọi cột: MySQL lấy kiểu cột từ đó.
INSERT INTO contracts
  (contract_code, title, contract_type, direction, signing_date, effective_date, end_date,
   enterprise_id, owner_id, signer_name, signer_position, signer_phone, signer_fax,
   counterparty_signer_name, counterparty_signer_position, signing_place,
   contract_value, status, progress_status, created_at)
SELECT x.code, x.title, x.ctype, x.direction,
       CURDATE() + INTERVAL x.sign_d DAY, CURDATE() + INTERVAL x.eff_d DAY, CURDATE() + INTERVAL x.end_d DAY,
       e.enterprise_id, o.user_id,
       IF(x.progress = 'Nháp', NULL, CONCAT_WS(' ', gd.last_name, gd.middle_name, gd.first_name)),
       IF(x.progress = 'Nháp', NULL, 'Giám đốc chi nhánh'),
       IF(x.progress = 'Nháp', NULL, gd.phone),
       IF(x.progress = 'Nháp', NULL, '02437659999'),
       x.cp_signer, x.cp_position, x.place, x.value,
       CASE WHEN x.eff_d IS NULL OR x.end_d IS NULL OR x.eff_d > 0 THEN 'Chưa hiệu lực'
            WHEN x.end_d < 0 THEN 'Đã hết hạn'
            WHEN x.end_d <= 30 THEN 'Sắp hết hạn'
            ELSE 'Đang hiệu lực' END,
       x.progress,
       TIMESTAMP(CURDATE() + INTERVAL x.created_d DAY) + INTERVAL 9 HOUR
FROM (
    -- 5.
    SELECT '02/2026/HĐMB/POT-HTN' code, 'Cung cấp UPS EATON cho trung tâm điều hành mạng' title,
           'Cung cấp thiết bị' ctype, 'Bán' direction, 'Đã ký' progress,
           -345 sign_d, -345 eff_d, 20 end_d, -372 created_d,
           'KH-0001' partner, 'leminhhoang' owner, 742500000.00 value,
           'Nguyễn Hữu Phước' cp_signer, 'Tổng Giám đốc' cp_position, 'Hà Nội' place
    -- 8.
    UNION ALL SELECT '01/2026/HĐMB/DKB-POT', 'Mua UPS EATON phục vụ dự án trung tâm điều hành mạng',
           'Mua thiết bị', 'Mua', 'Đã ký', -368, -368, 180, -371,
           'NCC-001', 'leminhhoang', 528000000.00, 'Nguyễn Văn Toàn', 'Giám đốc', 'Bắc Ninh'
    -- 7.
    UNION ALL SELECT '04/2026/HĐMB/POT-TDT', 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ',
           'Cung cấp thiết bị', 'Bán', 'Đã thanh lý', -298, -298, -12, -330,
           'KH-0003', 'doducanh', 834000000.00, 'Phùng Văn Lực', 'Giám đốc', 'Phú Thọ'
    -- 6.
    UNION ALL SELECT '03/2026/HĐKT/POT-DHN', 'Thi công lắp đặt tuyến cáp quang treo khu đô thị Duyên Hải',
           'Thi công lắp đặt', 'Bán', 'Đã ký', -203, -203, 162, -230,
           'KH-0002', 'dangvantung', 958000000.00, 'Trần Đức Hải', 'Giám đốc', 'Hải Phòng'
    -- 4.
    UNION ALL SELECT '05/2026/HĐMB/POT-HTN', 'Cung cấp nguồn POSTEF và ắc quy lithium cho 12 trạm nội thành',
           'Cung cấp thiết bị', 'Bán', 'Đã ký', -7, -7, 358, -40,
           'KH-0001', 'leminhhoang', 1185600000.00, 'Nguyễn Hữu Phước', 'Tổng Giám đốc', 'Hà Nội'
    -- 3.
    UNION ALL SELECT NULL, 'Cung cấp cáp quang kéo cống tuyến Lê Chân – Ngô Quyền',
           'Cung cấp thiết bị', 'Bán', 'Nháp', NULL, NULL, NULL, -30,
           'KH-0002', 'phamthutrang', 612750000.00, NULL, NULL, NULL
    -- 2.
    UNION ALL SELECT NULL, 'Cung cấp tủ nguồn Outdoor cho trạm phát sóng Sơn La',
           'Cung cấp thiết bị', 'Bán', 'Nháp', NULL, NULL, NULL, -12,
           'KH-0004', 'vuthihang', 486200000.00, NULL, NULL, NULL
    -- 1.
    UNION ALL SELECT NULL, 'Cung cấp ắc quy lithium cho trạm BTS khu vực Việt Trì',
           'Cung cấp thiết bị', 'Bán', 'Nháp', NULL, NULL, NULL, -2,
           'KH-0003', 'doducanh', NULL, NULL, NULL, NULL
) x
JOIN enterprises e ON e.enterprise_code = x.partner
JOIN users o ON o.username = x.owner
JOIN users gd ON gd.username = 'admin'
ORDER BY x.created_d;

-- Phụ lục của hợp đồng 5: không đụng tới tiền (contract_value NULL) nên không có
-- dòng "Điều chỉnh giá trị". Người phụ trách, người ký theo hợp đồng gốc.
INSERT INTO contracts
  (parent_contract_id, contract_code, title, contract_type, direction, signing_date, effective_date, end_date,
   enterprise_id, owner_id, signer_name, signer_position, signer_phone, signer_fax,
   counterparty_signer_name, counterparty_signer_position, signing_place,
   contract_value, status, progress_status, created_at)
SELECT p.contract_id, '02/2026/HĐMB/POT-HTN/PL01', 'Phụ lục 01 — điều chỉnh địa điểm giao hàng đợt 2',
       p.contract_type, p.direction,
       CURDATE() - INTERVAL 118 DAY, CURDATE() - INTERVAL 118 DAY, CURDATE() + INTERVAL 20 DAY,
       p.enterprise_id, p.owner_id, p.signer_name, p.signer_position, p.signer_phone, p.signer_fax,
       p.counterparty_signer_name, p.counterparty_signer_position, p.signing_place,
       NULL, 'Sắp hết hạn', 'Đã ký', TIMESTAMP(CURDATE() - INTERVAL 125 DAY) + INTERVAL 9 HOUR
FROM contracts p
WHERE p.contract_code = '02/2026/HĐMB/POT-HTN';

-- Hàng hoá: 1 đến 2 sản phẩm POSTEF có sẵn mỗi hợp đồng, thêm khi còn Nháp.
INSERT INTO contractproducts (contract_id, product_id, quantity, unit)
SELECT c.contract_id, p.product_id, x.qty, x.unit
FROM (
    SELECT 1 n, 'Cung cấp UPS EATON cho trung tâm điều hành mạng' ckey, 'SP-0008' sp, 4 qty, 'Bộ' unit
    UNION ALL SELECT 2, 'Mua UPS EATON phục vụ dự án trung tâm điều hành mạng', 'SP-0008', 4, 'Bộ'
    UNION ALL SELECT 3, 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'SP-0009', 40, 'Tấm'
    UNION ALL SELECT 4, 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'SP-0003', 32, 'Bình'
    UNION ALL SELECT 5, 'Thi công lắp đặt tuyến cáp quang treo khu đô thị Duyên Hải', 'SP-0013', 15, 'km'
    UNION ALL SELECT 6, 'Thi công lắp đặt tuyến cáp quang treo khu đô thị Duyên Hải', 'SP-0023', 30, 'Bộ'
    UNION ALL SELECT 7, 'Cung cấp nguồn POSTEF và ắc quy lithium cho 12 trạm nội thành', 'SP-0006', 12, 'Bộ'
    UNION ALL SELECT 8, 'Cung cấp nguồn POSTEF và ắc quy lithium cho 12 trạm nội thành', 'SP-0001', 24, 'Bình'
    UNION ALL SELECT 9, 'Cung cấp cáp quang kéo cống tuyến Lê Chân – Ngô Quyền', 'SP-0012', 8, 'km'
    UNION ALL SELECT 10, 'Cung cấp cáp quang kéo cống tuyến Lê Chân – Ngô Quyền', 'SP-0023', 20, 'Bộ'
    UNION ALL SELECT 11, 'Cung cấp tủ nguồn Outdoor cho trạm phát sóng Sơn La', 'SP-0033', 6, 'Tủ'
    UNION ALL SELECT 12, 'Cung cấp ắc quy lithium cho trạm BTS khu vực Việt Trì', 'SP-0001', 24, 'Bình'
) x
JOIN contracts c ON c.title = x.ckey
JOIN products p ON p.product_code = x.sp
ORDER BY x.n;

-- ===== Giấy tờ (BR-59) =====
-- Danh mục ContractDocument.TYPES, mỗi tờ có số và ngày trên giấy, không link
-- (link không bắt buộc). Người tích là người phụ trách hợp đồng, trong ngày ghi
-- trên giấy. Bước giao hàng (BR-61) suy ra từ chính các tờ này.
-- Nhánh đầu có ghi chú để cột note không bị suy ra kiểu rỗng.
-- Hợp đồng 7: nghiệm thu và hồ sơ kỳ 2 tính lùi từ ngày 15 tháng trước (ngày thu
-- kỳ 2, xem phần kỳ thanh toán), luôn rơi trong khoảng 62 đến 27 ngày trước.
INSERT INTO contract_documents (contract_id, doc_type, title, doc_date, doc_no, file_url, note, uploaded_by, uploaded_at)
SELECT c.contract_id, x.dtype, NULL, CURDATE() + INTERVAL x.d DAY, x.doc_no, NULL, x.note, c.owner_id,
       TIMESTAMP(CURDATE() + INTERVAL x.d DAY) + INTERVAL x.m MINUTE
FROM (
    SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ' ckey, 'Biên bản phạt' dtype,
           -11 d, 540 m, '02/BBP-POT' doc_no, 'Phạt chậm tiến độ lắp đặt, trừ 5% kỳ thanh toán cuối' note
    -- 5.
    UNION ALL SELECT 'Cung cấp UPS EATON cho trung tâm điều hành mạng', 'Phiếu GQNCKH', -372, 660, '41/GQNCKH-POT', NULL
    UNION ALL SELECT 'Cung cấp UPS EATON cho trung tâm điều hành mạng', 'Phiếu ĐCNB', -368, 540, '47/ĐCNB-POT', NULL
    UNION ALL SELECT 'Cung cấp UPS EATON cho trung tâm điều hành mạng', 'Biên bản giao nhận hàng hoá', -362, 540, '39/BBGN-POT', NULL
    UNION ALL SELECT 'Cung cấp UPS EATON cho trung tâm điều hành mạng', 'Hợp đồng đã ký', -345, 600, '02/2026/HĐMB/POT-HTN', NULL
    UNION ALL SELECT 'Cung cấp UPS EATON cho trung tâm điều hành mạng', 'Đề nghị tạm ứng', -343, 540, '01/ĐNTU-POT', NULL
    UNION ALL SELECT 'Cung cấp UPS EATON cho trung tâm điều hành mạng', 'Bảo lãnh tạm ứng', -343, 600, '0412/BLTU', NULL
    UNION ALL SELECT 'Cung cấp UPS EATON cho trung tâm điều hành mạng', 'Phụ lục đã ký', -118, 600, '02/2026/HĐMB/POT-HTN/PL01', NULL
    UNION ALL SELECT 'Cung cấp UPS EATON cho trung tâm điều hành mạng', 'Biên bản nghiệm thu bàn giao', -5, 540, '21/BBNT-POT', NULL
    UNION ALL SELECT 'Cung cấp UPS EATON cho trung tâm điều hành mạng', 'Đề nghị thanh toán', -1, 540, '33/ĐNTT-POT', NULL
    UNION ALL SELECT 'Cung cấp UPS EATON cho trung tâm điều hành mạng', 'Hoá đơn GTGT', -1, 600, '0001287', NULL
    -- 8.
    UNION ALL SELECT 'Mua UPS EATON phục vụ dự án trung tâm điều hành mạng', 'Hợp đồng đã ký', -368, 600, '01/2026/HĐMB/DKB-POT', NULL
    -- 7.
    UNION ALL SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'Phiếu GQNCKH', -330, 660, '05/GQNCKH-POT', NULL
    UNION ALL SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'Phiếu ĐCNB', -326, 540, '06/ĐCNB-POT', NULL
    UNION ALL SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'Biên bản giao nhận hàng hoá', -320, 540, '04/BBGN-POT', NULL
    UNION ALL SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'Hợp đồng đã ký', -298, 600, '04/2026/HĐMB/POT-TDT', NULL
    UNION ALL SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'Đề nghị tạm ứng', -296, 540, '02/ĐNTU-POT', NULL
    UNION ALL SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'Biên bản nghiệm thu bàn giao',
           DATEDIFF(DATE_FORMAT(CURDATE() - INTERVAL 1 MONTH, '%Y-%m-15'), CURDATE()) - 15, 540, '07/BBNT-POT', NULL
    UNION ALL SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'Đề nghị thanh toán',
           DATEDIFF(DATE_FORMAT(CURDATE() - INTERVAL 1 MONTH, '%Y-%m-15'), CURDATE()) - 13, 540, '11/ĐNTT-POT', NULL
    UNION ALL SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'Hoá đơn GTGT',
           DATEDIFF(DATE_FORMAT(CURDATE() - INTERVAL 1 MONTH, '%Y-%m-15'), CURDATE()) - 13, 600, '0000958', NULL
    UNION ALL SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'Biên bản thanh lý', -10, 540, '04/BBTL-POT', NULL
    UNION ALL SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'Đề nghị thanh toán', -5, 540, '29/ĐNTT-POT', NULL
    UNION ALL SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'Hoá đơn GTGT', -5, 600, '0001196', NULL
    -- 6.
    UNION ALL SELECT 'Thi công lắp đặt tuyến cáp quang treo khu đô thị Duyên Hải', 'Phiếu GQNCKH', -230, 660, '08/GQNCKH-POT', NULL
    UNION ALL SELECT 'Thi công lắp đặt tuyến cáp quang treo khu đô thị Duyên Hải', 'Phiếu ĐCNB', -226, 540, '09/ĐCNB-POT', NULL
    UNION ALL SELECT 'Thi công lắp đặt tuyến cáp quang treo khu đô thị Duyên Hải', 'Biên bản giao nhận hàng hoá', -220, 540, '06/BBGN-POT', NULL
    UNION ALL SELECT 'Thi công lắp đặt tuyến cáp quang treo khu đô thị Duyên Hải', 'Hợp đồng đã ký', -203, 600, '03/2026/HĐKT/POT-DHN', NULL
    UNION ALL SELECT 'Thi công lắp đặt tuyến cáp quang treo khu đô thị Duyên Hải', 'Biên bản nghiệm thu bàn giao', -60, 540, '15/BBNT-POT', NULL
    UNION ALL SELECT 'Thi công lắp đặt tuyến cáp quang treo khu đô thị Duyên Hải', 'Đề nghị thanh toán', -58, 540, '24/ĐNTT-POT', NULL
    UNION ALL SELECT 'Thi công lắp đặt tuyến cáp quang treo khu đô thị Duyên Hải', 'Hoá đơn GTGT', -58, 600, '0001214', NULL
    -- 4.
    UNION ALL SELECT 'Cung cấp nguồn POSTEF và ắc quy lithium cho 12 trạm nội thành', 'Phiếu GQNCKH', -40, 660, '16/GQNCKH-POT', NULL
    UNION ALL SELECT 'Cung cấp nguồn POSTEF và ắc quy lithium cho 12 trạm nội thành', 'Phiếu ĐCNB', -36, 540, '18/ĐCNB-POT', NULL
    UNION ALL SELECT 'Cung cấp nguồn POSTEF và ắc quy lithium cho 12 trạm nội thành', 'Biên bản giao nhận hàng hoá', -30, 540, '14/BBGN-POT', NULL
    UNION ALL SELECT 'Cung cấp nguồn POSTEF và ắc quy lithium cho 12 trạm nội thành', 'Hợp đồng đã ký', -7, 600, '05/2026/HĐMB/POT-HTN', NULL
    -- 3.
    UNION ALL SELECT 'Cung cấp cáp quang kéo cống tuyến Lê Chân – Ngô Quyền', 'Phiếu GQNCKH', -30, 660, '19/GQNCKH-POT', NULL
    UNION ALL SELECT 'Cung cấp cáp quang kéo cống tuyến Lê Chân – Ngô Quyền', 'Phiếu ĐCNB', -25, 540, '22/ĐCNB-POT', NULL
    UNION ALL SELECT 'Cung cấp cáp quang kéo cống tuyến Lê Chân – Ngô Quyền', 'Biên bản giao nhận hàng hoá', -16, 960, '17/BBGN-POT', NULL
    -- 2.
    UNION ALL SELECT 'Cung cấp tủ nguồn Outdoor cho trạm phát sóng Sơn La', 'Phiếu GQNCKH', -12, 660, '27/GQNCKH-POT', NULL
    UNION ALL SELECT 'Cung cấp tủ nguồn Outdoor cho trạm phát sóng Sơn La', 'Phiếu ĐCNB', -9, 540, '31/ĐCNB-POT', NULL
) x
JOIN contracts c ON c.title = x.ckey
ORDER BY x.d, x.m;

-- ===== Kỳ thanh toán (BR-60) =====
-- Thứ tự lập là thứ tự trong điều khoản (Tạm ứng rồi Thanh toán). Hạn thu = ngày
-- nộp hồ sơ + số ngày. Đủ bốn tình trạng: Chưa nộp hồ sơ (hợp đồng 4), Chờ thu
-- và Đã thu (hợp đồng 5), Quá hạn (hợp đồng 6). Kỳ Chờ thu nộp hồ sơ hôm qua, hạn
-- thu còn 6 ngày, để vài ngày sau khi dựng lại CSDL vẫn còn kỳ Chờ thu.
-- Ngày thu của các kỳ đã thu rơi vào bốn tháng khác nhau trong 12 tháng gần nhất.
-- Dashboard mở mặc định so doanh thu tháng này với tháng trước, nên hợp đồng 7 thu
-- kỳ cuối hôm nay (sau thanh lý, BR-60 cho phép) và thu kỳ 2 ngày 15 tháng trước:
-- hai ngày đó luôn rơi đúng tháng này và tháng trước, ngày nào dựng lại cũng vậy.
INSERT INTO contract_payments
  (contract_id, payment_type, invoice_amount, condition_note, payment_days,
   submitted_date, due_date, paid_date, paid_amount, created_at)
SELECT c.contract_id, x.ptype, x.amount, x.cond, x.days,
       CURDATE() + INTERVAL x.sub_d DAY, CURDATE() + INTERVAL x.due_d DAY, CURDATE() + INTERVAL x.paid_d DAY,
       x.paid_amount, TIMESTAMP(CURDATE() + INTERVAL x.cr_d DAY) + INTERVAL x.cr_m MINUTE
FROM (
    -- 7. Tạm ứng 30% / Thanh toán 60% / Thanh toán 10% (bị trừ 5% theo biên bản phạt).
    SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ' ckey, 'Tạm ứng' ptype,
           250200000.00 amount, 'Tạm ứng 30% sau khi ký hợp đồng và nhận đề nghị tạm ứng' cond, 7 days,
           -296 sub_d, -289 due_d, -290 paid_d, 250200000.00 paid_amount, -297 cr_d, 600 cr_m
    UNION ALL SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'Thanh toán',
           500400000.00, 'Thanh toán 60% sau khi nghiệm thu bàn giao và nhận đủ hồ sơ thanh toán', 15,
           DATEDIFF(DATE_FORMAT(CURDATE() - INTERVAL 1 MONTH, '%Y-%m-15'), CURDATE()) - 13,
           DATEDIFF(DATE_FORMAT(CURDATE() - INTERVAL 1 MONTH, '%Y-%m-15'), CURDATE()) + 2,
           DATEDIFF(DATE_FORMAT(CURDATE() - INTERVAL 1 MONTH, '%Y-%m-15'), CURDATE()),
           500400000.00, -297, 660
    UNION ALL SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'Thanh toán',
           83400000.00, 'Thanh toán 10% còn lại sau khi hết hạn bảo hành lắp đặt', 15,
           -5, 10, 0, 79230000.00, -297, 720
    -- 5. Hai kỳ 80/20, có điều kiện và 7 ngày như hợp đồng mẫu Task 10.4.
    UNION ALL SELECT 'Cung cấp UPS EATON cho trung tâm điều hành mạng', 'Tạm ứng',
           594000000.00, 'Tạm ứng 80% giá trị hợp đồng sau khi ký và nhận bảo lãnh tạm ứng', 7,
           -343, -336, -338, 594000000.00, -344, 660
    UNION ALL SELECT 'Cung cấp UPS EATON cho trung tâm điều hành mạng', 'Thanh toán',
           148500000.00, 'Thanh toán 20% còn lại sau khi nghiệm thu bàn giao và nhận đủ hồ sơ thanh toán', 7,
           -1, 6, NULL, NULL, -344, 720
    -- 6. Đã nộp hồ sơ, quá hạn thu.
    UNION ALL SELECT 'Thi công lắp đặt tuyến cáp quang treo khu đô thị Duyên Hải', 'Thanh toán',
           958000000.00, 'Thanh toán 100% sau khi nghiệm thu và nhận đủ hồ sơ thanh toán', 15,
           -58, -43, NULL, NULL, -202, 600
    -- 4. Chưa nộp hồ sơ.
    UNION ALL SELECT 'Cung cấp nguồn POSTEF và ắc quy lithium cho 12 trạm nội thành', 'Thanh toán',
           1185600000.00, 'Thanh toán 100% sau khi nghiệm thu bàn giao và nhận đủ hồ sơ thanh toán', 30,
           NULL, NULL, NULL, NULL, -6, 600
) x
JOIN contracts c ON c.title = x.ckey
ORDER BY x.cr_d, x.cr_m;

-- ===== Bàn giao cho phòng Tổng hợp (UC-48, UC-49) =====
-- Hợp đồng 4 còn nằm trong hàng chờ; các lượt đã xong do kế toán hoangthimai xác nhận.
INSERT INTO contract_handovers
  (contract_id, department_id, handed_at, handed_by, done_at, done_by, handover_note, done_note)
SELECT c.contract_id, dp.department_id,
       TIMESTAMP(CURDATE() + INTERVAL x.handed_d DAY) + INTERVAL 9 HOUR, hb.user_id,
       TIMESTAMP(CURDATE() + INTERVAL x.done_d DAY) + INTERVAL 10 HOUR, db.user_id,
       x.note, x.done_note
FROM (
    SELECT 'Cung cấp UPS EATON cho trung tâm điều hành mạng' ckey, 'leminhhoang' handed_by, -344 handed_d,
           'Chuyển hồ sơ để phòng theo dõi tạm ứng và thanh toán.' note,
           'hoangthimai' done_by, -342 done_d, 'Đã nhận hồ sơ gốc, mở sổ theo dõi công nợ.' done_note
    UNION ALL SELECT 'Cung cấp pin mặt trời và ắc quy Gel cho trạm vùng đồi Phú Thọ', 'doducanh', -297,
           'Chuyển hồ sơ để theo dõi tạm ứng và các đợt thanh toán.',
           'hoangthimai', -295, 'Đã nhận hồ sơ, mở sổ theo dõi.'
    UNION ALL SELECT 'Thi công lắp đặt tuyến cáp quang treo khu đô thị Duyên Hải', 'dangvantung', -202,
           'Chuyển hồ sơ hợp đồng thi công để theo dõi thanh toán.',
           'hoangthimai', -200, 'Đã nhận hồ sơ, lập sổ theo dõi thanh toán.'
    UNION ALL SELECT 'Cung cấp nguồn POSTEF và ắc quy lithium cho 12 trạm nội thành', 'leminhhoang', -6,
           'Hồ sơ gốc gửi kèm, nhờ phòng mở sổ theo dõi thanh toán.', NULL, NULL, NULL
) x
JOIN contracts c ON c.title = x.ckey
JOIN departments dp ON dp.department_name = 'Tổng hợp'
JOIN users hb ON hb.username = x.handed_by
LEFT JOIN users db ON db.username = x.done_by
ORDER BY x.handed_d;

-- ===== Liên kết bán – mua (V31) =====
INSERT INTO contract_links (sell_contract_id, buy_contract_id, created_by, created_at)
SELECT s.contract_id, b.contract_id, u.user_id, TIMESTAMP(CURDATE() - INTERVAL 344 DAY) + INTERVAL 10 HOUR
FROM contracts s
JOIN contracts b ON b.title = 'Mua UPS EATON phục vụ dự án trung tâm điều hành mạng'
JOIN users u ON u.username = 'leminhhoang'
WHERE s.title = 'Cung cấp UPS EATON cho trung tâm điều hành mạng';

-- ===== Nhật ký hợp đồng =====
-- Sinh theo QUY TẮC từ chính các dòng vừa gieo, đúng câu chữ ContractDAO,
-- ContractHistoryDAO, ContractDocumentDAO, ContractPaymentDAO, ContractHandoverDAO,
-- ContractLinkDAO ghi ở từng bước: dữ liệu mẫu không kể một câu chuyện khác với
-- thứ hệ thống sinh ra. Mốc giờ trong ngày chỉ để các bước xếp đúng thứ tự.

-- Khởi tạo: biểu mẫu tạo không có ô ngày, mã để trống tới khi có số.
INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at)
SELECT c.contract_id, 'Khởi tạo',
       CONCAT('Tạo bản nháp Chưa có số — hợp đồng ', LOWER(c.direction), ', chưa chốt thời hạn'),
       c.owner_id, c.created_at
FROM contracts c
WHERE c.parent_contract_id IS NULL;

INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at)
SELECT c.contract_id, 'Khởi tạo',
       CONCAT('Tạo bản nháp phụ lục Chưa có số — sửa đổi cho hợp đồng ', p.contract_code, ', chưa chốt thời hạn'),
       c.owner_id, c.created_at
FROM contracts c
JOIN contracts p ON p.contract_id = c.parent_contract_id;

-- Lập phụ lục: ghi lên hợp đồng CHA.
INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at)
SELECT p.contract_id, 'Lập phụ lục', CONCAT('Lập phụ lục Chưa có số — ', c.title), c.owner_id, c.created_at
FROM contracts c
JOIN contracts p ON p.contract_id = c.parent_contract_id;

-- Thêm hàng hoá: một lần thêm = một dòng, một giờ sau khi mở hồ sơ.
INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at)
SELECT c.contract_id, 'Thêm hàng hoá',
       CONCAT('Thêm ', COUNT(*), ' hạng mục: ',
              GROUP_CONCAT(CONCAT(p.product_name, ' ×', cp.quantity, ' ', cp.unit)
                           ORDER BY cp.contract_product_id SEPARATOR '; ')),
       c.owner_id, c.created_at + INTERVAL 1 HOUR
FROM contracts c
JOIN contractproducts cp ON cp.contract_id = c.contract_id
JOIN products p ON p.product_id = cp.product_id
GROUP BY c.contract_id, c.owner_id, c.created_at;

-- Giấy tờ: mỗi tờ một dòng "Thêm tài liệu", ghi chú của tờ ở cột note.
INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at, note)
SELECT d.contract_id, 'Tài liệu',
       CONCAT('Thêm tài liệu: ', d.doc_type, ' (số ', d.doc_no, ', ngày ', DATE_FORMAT(d.doc_date, '%d/%m/%Y'), ')'),
       d.uploaded_by, d.uploaded_at, d.note
FROM contract_documents d;

-- Sửa thông tin: hai ngày trước hôm Ký, nhập mã theo bản giấy và chốt thời hạn.
INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at)
SELECT c.contract_id, 'Sửa thông tin',
       CONCAT('Mã hợp đồng: (trống) → ', c.contract_code,
              '; Ngày hiệu lực: (trống) → ', DATE_FORMAT(c.effective_date, '%d/%m/%Y'),
              '; Ngày hết hạn: (trống) → ', DATE_FORMAT(c.end_date, '%d/%m/%Y')),
       c.owner_id, TIMESTAMP(c.signing_date - INTERVAL 2 DAY) + INTERVAL 9 HOUR
FROM contracts c
WHERE c.progress_status <> 'Nháp';

-- Ký hợp đồng: ngày ký là ngày bấm Ký.
INSERT INTO contract_history (contract_id, event_type, detail, from_status, to_status, changed_by, changed_at)
SELECT c.contract_id, 'Ký hợp đồng', 'Nháp → Đã ký', 'Nháp', 'Đã ký', c.owner_id,
       TIMESTAMP(c.signing_date) + INTERVAL 9 HOUR
FROM contracts c
WHERE c.progress_status <> 'Nháp';

-- Thanh lý: căn cứ là biên bản thanh lý, ghi ở cột note như changeProgressStatus.
INSERT INTO contract_history (contract_id, event_type, detail, from_status, to_status, note, changed_by, changed_at)
SELECT c.contract_id, 'Thanh lý', 'Đã ký → Đã thanh lý', 'Đã ký', 'Đã thanh lý',
       CONCAT('Biên bản thanh lý số ', d.doc_no, ' ngày ', DATE_FORMAT(d.doc_date, '%d/%m/%Y')),
       c.owner_id, d.uploaded_at + INTERVAL 1 HOUR
FROM contracts c
JOIN contract_documents d ON d.contract_id = c.contract_id AND d.doc_type = 'Biên bản thanh lý'
WHERE c.progress_status = 'Đã thanh lý';

-- Bàn giao: lượt giao và lượt phòng nhận báo xong.
INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at, note)
SELECT h.contract_id, 'Bàn giao phòng ban', CONCAT('Bàn giao cho phòng ', dp.department_name),
       h.handed_by, h.handed_at, h.handover_note
FROM contract_handovers h
JOIN departments dp ON dp.department_id = h.department_id;

INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at, note)
SELECT h.contract_id, 'Bàn giao phòng ban', CONCAT('Phòng ', dp.department_name, ' báo đã xử lý xong'),
       h.done_by, h.done_at, h.done_note
FROM contract_handovers h
JOIN departments dp ON dp.department_id = h.department_id
WHERE h.done_at IS NOT NULL;

-- Nối hợp đồng: một dòng ở mỗi bên.
INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at, note)
SELECT l.sell_contract_id, 'Nối hợp đồng',
       CONCAT('Nối với hợp đồng mua ', b.contract_code, ' — đầu vào phục vụ hợp đồng này'),
       l.created_by, l.created_at, l.note
FROM contract_links l
JOIN contracts b ON b.contract_id = l.buy_contract_id;

INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at, note)
SELECT l.buy_contract_id, 'Nối hợp đồng', CONCAT('Phục vụ hợp đồng bán ', s.contract_code),
       l.created_by, l.created_at, l.note
FROM contract_links l
JOIN contracts s ON s.contract_id = l.sell_contract_id;

-- Kỳ thanh toán: lập kỳ (chưa có hạn thu), nộp hồ sơ, ghi nhận đã thu. Người lập
-- và người bấm nộp hồ sơ là người phụ trách; người ghi nhận đã thu là buivannam.
-- Kỳ thu hôm nay (hợp đồng 7) ghi giờ không muộn hơn lúc nạp, để nhật ký không có
-- dòng ở tương lai.
INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at)
SELECT pm.contract_id, 'Thêm kỳ thanh toán',
       CONCAT('Lập kỳ ', pm.payment_type, ' ', REPLACE(FORMAT(pm.invoice_amount, 0), ',', '.'), ' đ',
              IF(pm.payment_days IS NULL, '', CONCAT(', trả trong ', pm.payment_days, ' ngày')),
              ', chưa có hạn thu'),
       c.owner_id, pm.created_at
FROM contract_payments pm
JOIN contracts c ON c.contract_id = pm.contract_id;

INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at)
SELECT pm.contract_id, 'Nộp hồ sơ thanh toán',
       CONCAT('Nộp hồ sơ kỳ ', pm.payment_type, ' ', REPLACE(FORMAT(pm.invoice_amount, 0), ',', '.'), ' đ',
              ' ngày ', DATE_FORMAT(pm.submitted_date, '%d/%m/%Y'), ', ',
              IF(pm.due_date IS NULL, 'chưa có hạn thu', CONCAT('đến hạn ', DATE_FORMAT(pm.due_date, '%d/%m/%Y')))),
       c.owner_id, TIMESTAMP(pm.submitted_date) + INTERVAL 11 HOUR
FROM contract_payments pm
JOIN contracts c ON c.contract_id = pm.contract_id
WHERE pm.submitted_date IS NOT NULL;

INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at)
SELECT pm.contract_id, 'Ghi nhận đã thu',
       CONCAT('Đã thu ', REPLACE(FORMAT(pm.paid_amount, 0), ',', '.'), ' đ',
              IF(pm.paid_amount <> pm.invoice_amount,
                 CONCAT(' (số tiền kỳ ', REPLACE(FORMAT(pm.invoice_amount, 0), ',', '.'), ' đ)'), ''),
              ' (kỳ ', IF(pm.due_date IS NULL, 'chưa có hạn thu', CONCAT('đến hạn ', DATE_FORMAT(pm.due_date, '%d/%m/%Y'))), ')',
              ' ngày ', DATE_FORMAT(pm.paid_date, '%d/%m/%Y')),
       u.user_id, LEAST(TIMESTAMP(pm.paid_date) + INTERVAL 14 HOUR, NOW())
FROM contract_payments pm
JOIN users u ON u.username = 'buivannam'
WHERE pm.paid_date IS NOT NULL;

-- ===== Phiếu hỗ trợ =====
-- Mỗi trạng thái một phiếu, người xử lý là Sales, gắn khách hàng và hợp đồng đã ký
-- của khách đó. Phiếu Đang xử lý có hạn SLA trong 24 giờ tới, tính từ lúc nạp, để
-- lượt NotificationScheduler đầu tiên có việc mà làm. Không gieo thiết bị lỗi
-- (technicalrequestdevices): chưa màn hình nào đọc bảng đó.
INSERT INTO technicalrequests
  (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline,
   assigned_technician_id, created_by, created_date, description, root_cause, cause_category,
   handling_plan, is_warranty, status, resolution_summary, resolved_at)
SELECT 'TK-0001', e.enterprise_id, c.contract_id, 'Bảo hành', 'Cao', 'Điện thoại',
       TIMESTAMP(CURDATE() - INTERVAL 147 DAY) + INTERVAL 17 HOUR, h.user_id, h.user_id,
       CURDATE() - INTERVAL 150 DAY,
       'Hai măng xông quang trên tuyến cáp treo khu đô thị bị vào nước, suy hao tăng cao sau trận mưa lớn.',
       'Gioăng cao su của hai măng xông lão hoá sớm do lô hàng lưu kho quá lâu trước khi lắp.',
       'Do thiết bị',
       'Thay gioăng, hàn nối lại hai măng xông và đo suy hao toàn tuyến sau khi xử lý.',
       1, 'Đã đóng',
       'Đã thay gioăng, hàn lại 24 mối nối; suy hao toàn tuyến về mức thiết kế, khách đồng ý đóng phiếu.',
       TIMESTAMP(CURDATE() - INTERVAL 148 DAY) + INTERVAL 16 HOUR
FROM enterprises e
JOIN contracts c ON c.title = 'Thi công lắp đặt tuyến cáp quang treo khu đô thị Duyên Hải'
JOIN users h ON h.username = 'leminhhoang'
WHERE e.enterprise_code = 'KH-0002';

INSERT INTO technicalrequests
  (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline,
   assigned_technician_id, created_by, created_date, description, root_cause, cause_category,
   handling_plan, is_warranty, status, resolution_summary, resolved_at)
SELECT 'TK-0002', e.enterprise_id, c.contract_id, 'Sửa chữa', 'Khẩn cấp', 'Điện thoại',
       DATE_FORMAT(NOW() + INTERVAL 20 HOUR, '%Y-%m-%d %H:00:00'), h.user_id, h.user_id,
       DATE(NOW() - INTERVAL 2 HOUR),
       'UPS số 2 tại trung tâm điều hành mạng báo lỗi quạt làm mát và tự chuyển sang chế độ bypass.',
       'Quạt làm mát module công suất hỏng bạc đạn, nhiệt độ module vượt ngưỡng nên UPS tự chuyển bypass.',
       'Do thiết bị',
       'Thay quạt làm mát từ hàng dự phòng của hãng, chạy thử tải 2 giờ trước khi bàn giao lại.',
       1, 'Đang xử lý', NULL, NULL
FROM enterprises e
JOIN contracts c ON c.title = 'Cung cấp UPS EATON cho trung tâm điều hành mạng'
JOIN users h ON h.username = 'leminhhoang'
WHERE e.enterprise_code = 'KH-0001';

INSERT INTO technicalrequests
  (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline,
   assigned_technician_id, created_by, created_date, description, root_cause, cause_category,
   handling_plan, is_warranty, status, resolution_summary, resolved_at)
SELECT 'TK-0003', e.enterprise_id, c.contract_id, 'Tư vấn', 'Bình thường', 'Email',
       TIMESTAMP(CURDATE() + INTERVAL 3 DAY) + INTERVAL 17 HOUR, h.user_id, h.user_id,
       DATE(NOW() - INTERVAL 30 MINUTE),
       'Khách hỏi cách cài cảnh báo từ xa cho tủ nguồn POSTEF vừa lắp ở 12 trạm nội thành.',
       NULL, NULL, NULL, 0, 'Mới tiếp nhận', NULL, NULL
FROM enterprises e
JOIN contracts c ON c.title = 'Cung cấp nguồn POSTEF và ắc quy lithium cho 12 trạm nội thành'
JOIN users h ON h.username = 'phamthutrang'
WHERE e.enterprise_code = 'KH-0001';

-- Lịch sử như TechnicalSupportTicketDAO ghi: dòng lập phiếu có from_status rỗng,
-- người lập, không ghi chú; mỗi lần đổi trạng thái một dòng kèm ghi chú nội bộ.
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note)
SELECT t.ticket_id, x.from_s, x.to_s, u.user_id, x.ts, x.note
FROM (
    SELECT 'TK-0001' code, 'Mới tiếp nhận' from_s, 'Đang xử lý' to_s, 'leminhhoang' actor,
           TIMESTAMP(CURDATE() - INTERVAL 150 DAY) + INTERVAL 810 MINUTE ts,
           'Đã lên tuyến kiểm tra, xác định hai măng xông bị vào nước.' note
    UNION ALL SELECT 'TK-0001', '', 'Mới tiếp nhận', 'leminhhoang',
           TIMESTAMP(CURDATE() - INTERVAL 150 DAY) + INTERVAL 510 MINUTE, NULL
    UNION ALL SELECT 'TK-0001', 'Đang xử lý', 'Đã đóng', 'leminhhoang',
           TIMESTAMP(CURDATE() - INTERVAL 148 DAY) + INTERVAL 960 MINUTE,
           'Đã xử lý xong, khách nghiệm thu và đồng ý đóng phiếu.'
    UNION ALL SELECT 'TK-0002', '', 'Mới tiếp nhận', 'leminhhoang', NOW() - INTERVAL 2 HOUR, NULL
    UNION ALL SELECT 'TK-0002', 'Mới tiếp nhận', 'Đang xử lý', 'leminhhoang', NOW() - INTERVAL 1 HOUR,
           'Đã liên hệ hãng EATON, quạt thay thế đang được chuyển tới.'
    UNION ALL SELECT 'TK-0003', '', 'Mới tiếp nhận', 'phamthutrang', NOW() - INTERVAL 30 MINUTE, NULL
) x
JOIN technicalrequests t ON t.ticket_code = x.code
JOIN users u ON u.username = x.actor
ORDER BY x.ts;
