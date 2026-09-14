-- Gieo lại toàn bộ dữ liệu demo nghiệp vụ, thay bộ cũ do V9 gieo.
--
-- Lý do làm lại: V9 gieo TRƯỚC khi có cột nguyên nhân sự cố (V14), nên mọi
-- phiếu hỗ trợ trong đó đều có root_cause/cause_category NULL. Mở trang chi
-- tiết phiếu hay xuất Excel ra đều thấy phần "Nguyên nhân sự cố" trống trơn --
-- không demo được đúng thứ khách hàng vừa yêu cầu, và cũng không thống kê thử
-- được "tháng này bao nhiêu ca lỗi do lắp đặt".
--
-- Bộ mới bám hai thay đổi gần nhất:
--
--   * V14 (nguyên nhân sự cố): 16 phiếu trải đủ 4 nhóm nguyên nhân, và cố ý
--     chừa 5 phiếu CHƯA có nguyên nhân. Đó không phải dữ liệu thiếu: phiếu vừa
--     tiếp nhận thì chưa ai xuống hiện trường, để trống mới là đúng nghiệp vụ,
--     và nó chứng minh luôn rằng hai cột đó nullable thật.
--   * PR #94 (địa bàn miền Bắc): 12 khách hàng đặt ở 12 tỉnh miền Bắc theo tên
--     tỉnh SAU sáp nhập 2025, để bộ lọc tỉnh trên màn hình danh sách có dữ liệu
--     mà lọc.
--
-- Phiếu cũng trải trên 5 kỹ thuật viên (16, 25-28) thay vì dồn hết vào một
-- người như V9: ngoại lệ phân quyền "kỹ thuật viên chỉ sửa được phiếu giao cho
-- chính mình" chỉ kiểm chứng được khi có ít nhất hai người cùng có phiếu.
--
-- GIỮ NGUYÊN, không đụng tới: users (16 tài khoản đăng nhập -- xoá là không ai
-- vào được hệ thống nữa), roles, departments, products + productcatalogues
-- (catalogue POSTEF thật do V5/V7/V8 gieo), provinces/districts.
--
-- Link Google Drive trên HD-0015 được mang nguyên sang bộ mới: đó là link demo
-- cho khách xem, không phải rác dữ liệu.

INSERT INTO schema_migrations (version) VALUES ('V15__reseed_demo_data_with_root_cause__ndat2003');

-- Thứ tự dọn theo chiều khoá ngoại (con trước cha). contractproducts,
-- enterprisecontacts, technicalrequestdevices và technicalrequesthistory tự đi
-- theo nhờ ON DELETE CASCADE nên không cần gọi tên.
DELETE FROM technicalrequests;
DELETE FROM contract_payments;
DELETE FROM contracts;
DELETE FROM customer_lifecycle_events;
DELETE FROM enterprises;

-- Địa chỉ của khách hàng vừa dọn giờ thành mồ côi. users cũng trỏ vào
-- addresses, nên chỉ bỏ những dòng không còn ai tham chiếu -- không được dọn
-- sạch bảng, làm thế là mất luôn địa chỉ của 16 nhân viên.
DELETE a FROM addresses a
LEFT JOIN users u ON u.address_id = a.address_id
WHERE u.user_id IS NULL;

ALTER TABLE enterprises AUTO_INCREMENT = 1;
ALTER TABLE enterprisecontacts AUTO_INCREMENT = 1;
ALTER TABLE contracts AUTO_INCREMENT = 1;
ALTER TABLE contractproducts AUTO_INCREMENT = 1;
ALTER TABLE contract_payments AUTO_INCREMENT = 1;
ALTER TABLE customer_lifecycle_events AUTO_INCREMENT = 1;
ALTER TABLE technicalrequests AUTO_INCREMENT = 1;
ALTER TABLE technicalrequestdevices AUTO_INCREMENT = 1;
ALTER TABLE technicalrequesthistory AUTO_INCREMENT = 1;

-- ===== Địa chỉ (12 tỉnh miền Bắc) =====
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('Số 15 Phố Duy Tân', 1);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('12 Đường Lạch Tray', 1022);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('56 Đường Kinh Dương Vương', 775);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('88 Đường Trần Hưng Đạo', 721);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('Lô A2 Khu công nghiệp Phố Nối A', 1136);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('27 Đường Đinh Tiên Hoàng', 1240);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('140 Đại lộ Hùng Vương', 874);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('9 Đường Cách Mạng Tháng Tám', 564);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('31 Đường Lê Lợi', 656);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('76 Đường Nguyễn Huệ', 465);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('18 Đường Nguyễn Du', 1369);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('63 Đường Lê Duẩn', 1535);

-- ===== Khách hàng (12) =====
-- account_owner_id = người phụ trách chính, support_owner_id = người hỗ trợ
-- (V13). Hai vai luôn khác người -- tầng ứng dụng chặn trùng, dữ liệu demo
-- không được phép vi phạm chính quy tắc đó.
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, support_owner_id, legal_representative, status, current_relationship_rating, join_date) VALUES
('KH-0001', 'Công ty Cổ phần Viễn thông Sông Hồng', 'Nhà mạng viễn thông', 'VIP', '0101234567', 'lienhe@songhong.example.com', '0243801001', 'https://songhong.example.com', (SELECT address_id FROM addresses WHERE street_and_local_name = 'Số 15 Phố Duy Tân' AND districts_id = 1 LIMIT 1), (SELECT user_id FROM users WHERE username = 'sales4'), (SELECT user_id FROM users WHERE username = 'sales2'), 'Nguyễn Văn Sơn', 'Active', 'Tốt', '2023-03-15');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, support_owner_id, legal_representative, status, current_relationship_rating, join_date) VALUES
('KH-0002', 'Công ty TNHH Hạ tầng số Hải Phòng', 'Nhà mạng viễn thông', 'Thân thiết', '0201234568', 'lienhe@htshp.example.com', '0225801002', 'https://htshp.example.com', (SELECT address_id FROM addresses WHERE street_and_local_name = '12 Đường Lạch Tray' AND districts_id = 1022 LIMIT 1), (SELECT user_id FROM users WHERE username = 'sales2'), (SELECT user_id FROM users WHERE username = 'sales3'), 'Trần Quốc Hải', 'Active', 'Tốt', '2023-07-02');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, support_owner_id, legal_representative, status, current_relationship_rating, join_date) VALUES
('KH-0003', 'Công ty Cổ phần Điện tử Kinh Bắc', 'Nhà thầu thi công', 'Thân thiết', '2301234569', 'lienhe@dtkb.example.com', '0222801003', 'https://dtkb.example.com', (SELECT address_id FROM addresses WHERE street_and_local_name = '56 Đường Kinh Dương Vương' AND districts_id = 775 LIMIT 1), (SELECT user_id FROM users WHERE username = 'sales3'), (SELECT user_id FROM users WHERE username = 'sales4'), 'Lê Đình Kiên', 'Active', 'Tốt', '2024-01-20');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, support_owner_id, legal_representative, status, current_relationship_rating, join_date) VALUES
('KH-0004', 'Công ty TNHH Truyền hình cáp Hạ Long', 'Nhà mạng viễn thông', 'Thường', '5701234570', 'lienhe@thchl.example.com', '0203801004', 'https://thchl.example.com', (SELECT address_id FROM addresses WHERE street_and_local_name = '88 Đường Trần Hưng Đạo' AND districts_id = 721 LIMIT 1), (SELECT user_id FROM users WHERE username = 'sales4'), (SELECT user_id FROM users WHERE username = 'sales5'), 'Phạm Thu Trang', 'Active', 'Cần theo dõi', '2024-05-11');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, support_owner_id, legal_representative, status, current_relationship_rating, join_date) VALUES
('KH-0005', 'Công ty Cổ phần Khu công nghiệp Phố Nối', 'Nhà thầu thi công', 'VIP', '0901234571', 'lienhe@kcnphonoi.example.com', '0221801005', 'https://kcnphonoi.example.com', (SELECT address_id FROM addresses WHERE street_and_local_name = 'Lô A2 Khu công nghiệp Phố Nối A' AND districts_id = 1136 LIMIT 1), (SELECT user_id FROM users WHERE username = 'sales5'), (SELECT user_id FROM users WHERE username = 'sales6'), 'Hoàng Minh Tuấn', 'Active', 'Tốt', '2022-11-08');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, support_owner_id, legal_representative, status, current_relationship_rating, join_date) VALUES
('KH-0006', 'Công ty TNHH Viễn thông Tràng An', 'Nhà mạng viễn thông', 'Thường', '2701234572', 'lienhe@vttrangan.example.com', '0229801006', 'https://vttrangan.example.com', (SELECT address_id FROM addresses WHERE street_and_local_name = '27 Đường Đinh Tiên Hoàng' AND districts_id = 1240 LIMIT 1), (SELECT user_id FROM users WHERE username = 'sales6'), (SELECT user_id FROM users WHERE username = 'sales4'), 'Vũ Thị Hằng', 'Active', 'Tốt', '2024-09-19');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, support_owner_id, legal_representative, status, current_relationship_rating, join_date) VALUES
('KH-0007', 'Công ty Cổ phần Đầu tư Hạ tầng Đất Tổ', 'Nhà thầu thi công', 'Thường', '2601234573', 'lienhe@dattho.example.com', '0210801007', 'https://dattho.example.com', (SELECT address_id FROM addresses WHERE street_and_local_name = '140 Đại lộ Hùng Vương' AND districts_id = 874 LIMIT 1), (SELECT user_id FROM users WHERE username = 'sales4'), (SELECT user_id FROM users WHERE username = 'sales3'), 'Đỗ Văn Phúc', 'Active', 'Cần theo dõi', '2025-02-27');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, support_owner_id, legal_representative, status, current_relationship_rating, join_date) VALUES
('KH-0008', 'Công ty TNHH Công nghệ Gang Thép', 'Nhà thầu thi công', 'Thân thiết', '4601234574', 'lienhe@cngangthep.example.com', '0208801008', 'https://cngangthep.example.com', (SELECT address_id FROM addresses WHERE street_and_local_name = '9 Đường Cách Mạng Tháng Tám' AND districts_id = 564 LIMIT 1), (SELECT user_id FROM users WHERE username = 'sales2'), (SELECT user_id FROM users WHERE username = 'sales4'), 'Bùi Xuân Thành', 'Active', 'Tốt', '2023-12-05');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, support_owner_id, legal_representative, status, current_relationship_rating, join_date) VALUES
('KH-0009', 'Công ty Cổ phần Thương mại Xứ Lạng', 'Đại lý phân phối', 'Thường', '2401234575', 'lienhe@xulang.example.com', '0205801009', 'https://xulang.example.com', (SELECT address_id FROM addresses WHERE street_and_local_name = '31 Đường Lê Lợi' AND districts_id = 656 LIMIT 1), (SELECT user_id FROM users WHERE username = 'sales3'), (SELECT user_id FROM users WHERE username = 'sales5'), 'Ngô Thị Quyên', 'Active', 'Tốt', '2025-04-16');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, support_owner_id, legal_representative, status, current_relationship_rating, join_date) VALUES
('KH-0010', 'Công ty TNHH Cửa khẩu số Lào Cai', 'Đại lý phân phối', 'Tiềm năng', '5301234576', 'lienhe@ckslaocai.example.com', '0214801010', 'https://ckslaocai.example.com', (SELECT address_id FROM addresses WHERE street_and_local_name = '76 Đường Nguyễn Huệ' AND districts_id = 465 LIMIT 1), (SELECT user_id FROM users WHERE username = 'sales4'), (SELECT user_id FROM users WHERE username = 'sales6'), 'Lý Văn Đức', 'Active', 'Cần theo dõi', '2025-08-01');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, support_owner_id, legal_representative, status, current_relationship_rating, join_date) VALUES
('KH-0011', 'Công ty Cổ phần Hạ tầng mạng Sầm Sơn', 'Nhà thầu thi công', 'Thân thiết', '2801234577', 'lienhe@htmsamson.example.com', '0237801011', 'https://htmsamson.example.com', (SELECT address_id FROM addresses WHERE street_and_local_name = '18 Đường Nguyễn Du' AND districts_id = 1369 LIMIT 1), (SELECT user_id FROM users WHERE username = 'sales5'), (SELECT user_id FROM users WHERE username = 'sales4'), 'Trịnh Bá Long', 'Active', 'Có nguy cơ rời bỏ', '2023-06-21');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, support_owner_id, legal_representative, status, current_relationship_rating, join_date) VALUES
('KH-0012', 'Công ty TNHH Giải pháp mạng Xứ Nghệ', 'Nhà mạng viễn thông', 'Thường', '2901234578', 'lienhe@gpmxunghe.example.com', '0238801012', 'https://gpmxunghe.example.com', (SELECT address_id FROM addresses WHERE street_and_local_name = '63 Đường Lê Duẩn' AND districts_id = 1535 LIMIT 1), (SELECT user_id FROM users WHERE username = 'sales6'), (SELECT user_id FROM users WHERE username = 'sales2'), 'Đặng Quang Vinh', 'Active', 'Tốt', '2024-10-30');

-- ===== Người liên hệ =====
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0001'), 'Nguyễn', 'Văn', 'An', '0912000001', 'an.nv@songhong.example.com', 'Trưởng phòng Kỹ thuật');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0002'), 'Trần', 'Thị', 'Bình', '0912000002', 'binh.tt@htshp.example.com', 'Giám đốc Hạ tầng');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0003'), 'Lê', 'Hoàng', 'Cường', '0912000003', 'cuong.lh@dtkb.example.com', 'Chỉ huy trưởng công trình');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0004'), 'Phạm', 'Thị', 'Dung', '0912000004', 'dung.pt@thchl.example.com', 'Phụ trách mua hàng');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0005'), 'Hoàng', 'Văn', 'Em', '0912000005', 'em.hv@kcnphonoi.example.com', 'Trưởng ban Quản lý dự án');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0006'), 'Vũ', 'Thị', 'Giang', '0912000006', 'giang.vt@vttrangan.example.com', 'Trưởng phòng Vận hành');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0007'), 'Đỗ', 'Minh', 'Hải', '0912000007', 'hai.dm@dattho.example.com', 'Phó Giám đốc');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0008'), 'Bùi', 'Thị', 'Hoa', '0912000008', 'hoa.bt@cngangthep.example.com', 'Trưởng phòng Vật tư');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0009'), 'Ngô', 'Văn', 'Khánh', '0912000009', 'khanh.nv@xulang.example.com', 'Chủ cửa hàng');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0010'), 'Lý', 'Thị', 'Lan', '0912000010', 'lan.lt@ckslaocai.example.com', 'Quản lý kinh doanh');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0011'), 'Trịnh', 'Văn', 'Minh', '0912000011', 'minh.tv@htmsamson.example.com', 'Trưởng phòng Kỹ thuật');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0012'), 'Đặng', 'Thị', 'Nga', '0912000012', 'nga.dt@gpmxunghe.example.com', 'Giám đốc Chi nhánh');

-- ===== Hợp đồng (15) =====
-- Ngày tháng trải ra có chủ đích: tính tại thời điểm gieo (14/09/2026) thì
-- HD-0005, HD-0009 và HD-0014 nằm trong vùng sắp hết hạn (dưới 90 ngày), 12
-- hợp đồng còn lại đang chạy -- để ContractDAO.computeStatus có cái mà phân
-- loại, thay vì cả bảng cùng một màu.
--
-- Cố ý KHÔNG gieo hợp đồng đã hết hạn: trạng thái là hàm thuần của ngày tháng
-- nên chỉ cần để yên, vài tháng nữa nhóm sắp hết hạn sẽ tự trôi sang hết hạn
-- mà không phải sửa dữ liệu. Gieo sẵn một hợp đồng "chết" chỉ để đủ màu thì
-- ngày mai nó vẫn chết, chẳng minh hoạ được chuyển biến nào.
-- HD-0015 giữ nguyên link Drive demo cho khách.
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0001', 'Cung cấp ắc quy lithium cho 45 trạm BTS', 'Cung cấp thiết bị', '2026-01-15', '2026-02-01', '2027-01-31', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0001'), (SELECT user_id FROM users WHERE username = 'sales4'), NULL, 'Đang hiệu lực');
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0002', 'Bảo trì hệ thống nguồn năm 2026', 'Bảo trì bảo dưỡng', '2026-02-20', '2026-03-01', '2026-12-31', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0001'), (SELECT user_id FROM users WHERE username = 'sales4'), NULL, 'Đang hiệu lực');
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0003', 'Cung cấp thiết bị 5G CPE cho vùng phủ mới', 'Cung cấp thiết bị', '2026-03-10', '2026-03-15', '2027-03-14', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0002'), (SELECT user_id FROM users WHERE username = 'sales2'), NULL, 'Đang hiệu lực');
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0004', 'Cung cấp tủ nguồn POSTEF cho nhà máy', 'Cung cấp thiết bị', '2026-04-05', '2026-04-15', '2027-04-14', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0003'), (SELECT user_id FROM users WHERE username = 'sales3'), NULL, 'Đang hiệu lực');
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0005', 'Thi công tuyến cáp quang ADSS Hạ Long - Cẩm Phả', 'Thi công lắp đặt', '2026-05-12', '2026-06-01', '2026-11-30', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0004'), (SELECT user_id FROM users WHERE username = 'sales4'), NULL, 'Đang hiệu lực');
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0006', 'Bảo trì hệ thống UPS 2026-2027', 'Bảo trì bảo dưỡng', '2026-06-01', '2026-06-15', '2027-06-14', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0004'), (SELECT user_id FROM users WHERE username = 'sales4'), NULL, 'Đang hiệu lực');
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0007', 'Cung cấp hệ thống nguồn cho KCN Phố Nối', 'Cung cấp thiết bị', '2026-02-28', '2026-03-10', '2027-03-09', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0005'), (SELECT user_id FROM users WHERE username = 'sales5'), NULL, 'Đang hiệu lực');
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0008', 'Cung cấp phụ kiện quang cho mạng truy nhập', 'Cung cấp thiết bị', '2026-07-01', '2026-07-10', '2027-07-09', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0006'), (SELECT user_id FROM users WHERE username = 'sales6'), NULL, 'Đang hiệu lực');
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0009', 'Thi công hệ thống pin mặt trời trạm Việt Trì', 'Thi công lắp đặt', '2026-03-20', '2026-04-01', '2026-09-30', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0007'), (SELECT user_id FROM users WHERE username = 'sales4'), NULL, 'Đang hiệu lực');
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0010', 'Cung cấp nguồn UNIPOWER cho khu sản xuất', 'Cung cấp thiết bị', '2025-12-10', '2026-01-01', '2026-12-31', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0008'), (SELECT user_id FROM users WHERE username = 'sales2'), NULL, 'Đang hiệu lực');
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0011', 'Phân phối cáp quang khu vực Lạng Sơn', 'Cung cấp thiết bị', '2026-06-18', '2026-07-01', '2027-06-30', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0009'), (SELECT user_id FROM users WHERE username = 'sales3'), NULL, 'Đang hiệu lực');
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0012', 'Phân phối dây thuê bao khu vực Lào Cai', 'Cung cấp thiết bị', '2026-05-05', '2026-05-15', '2027-05-14', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0010'), (SELECT user_id FROM users WHERE username = 'sales4'), NULL, 'Đang hiệu lực');
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0013', 'Cung cấp UPS EATON cho hệ thống ven biển', 'Cung cấp thiết bị', '2026-04-22', '2026-05-01', '2027-04-30', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0011'), (SELECT user_id FROM users WHERE username = 'sales5'), NULL, 'Đang hiệu lực');
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0014', 'Bảo trì hệ thống nguồn khu vực Nghệ An', 'Bảo trì bảo dưỡng', '2026-01-08', '2026-02-01', '2026-10-31', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0012'), (SELECT user_id FROM users WHERE username = 'sales6'), NULL, 'Đang hiệu lực');
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id, attachment_url, status) VALUES
('HD-0015', 'Cung cấp sợi quang G657A1 cho đại lý', 'Cung cấp thiết bị', '2026-08-01', '2026-08-15', '2027-08-14', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0009'), (SELECT user_id FROM users WHERE username = 'sales3'), 'https://drive.google.com/file/d/1lAoND44iEzSuLYnXEj7DfHNGJfHMDcBD/view?usp=sharing', 'Đang hiệu lực');

-- ===== Hàng hoá trong hợp đồng =====
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0001'), 1, 45, 'Bình', 'Mỗi trạm BTS một bình');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0001'), 6, 8, 'Tủ', 'Tủ nguồn đi kèm');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0002'), 6, 12, 'Tủ', 'Thiết bị trong phạm vi bảo trì');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0003'), 10, 4200, 'Mét', 'Cáp ADSS phục vụ đấu nối');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0004'), 6, 15, 'Tủ', 'Tủ nguồn POSTEF');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0004'), 4, 30, 'Bình', 'Ắc quy acid chì kín');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0005'), 10, 12500, 'Mét', 'Cáp ADSS tuyến chính');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0006'), 8, 6, 'Bộ', 'UPS trong phạm vi bảo trì');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0007'), 6, 8, 'Tủ', 'Tủ nguồn khu công nghiệp');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0007'), 2, 16, 'Bình', 'Ắc quy lưu động');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0008'), 11, 6800, 'Mét', 'Cáp quang bọc chặt');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0009'), 9, 120, 'Cái', 'Tấm pin mặt trời');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0010'), 7, 10, 'Tủ', 'Nguồn UNIPOWER');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0011'), 12, 15000, 'Mét', 'Cáp quang kéo cống');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0012'), 14, 9000, 'Mét', 'Dây thuê bao đệm chặt');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0013'), 8, 4, 'Bộ', 'UPS EATON');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0014'), 6, 9, 'Tủ', 'Tủ nguồn trong phạm vi bảo trì');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0015'), 13, 20000, 'Mét', 'Sợi quang G657A1');

-- ===== Thanh toán =====
-- Có cả kỳ đã trả và kỳ chưa tới hạn để Dashboard doanh thu và cảnh báo công
-- nợ đều có dữ liệu.
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0001'), 850000000, '2026-03-01', '2026-02-26');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0001'), 850000000, '2026-09-01', NULL);
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0003'), 1200000000, '2026-04-15', '2026-04-12');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0004'), 640000000, '2026-05-15', '2026-05-10');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0005'), 2100000000, '2026-07-01', '2026-06-28');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0005'), 2100000000, '2026-10-01', NULL);
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0007'), 1750000000, '2026-04-10', '2026-04-05');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0008'), 430000000, '2026-08-10', NULL);
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0009'), 980000000, '2026-05-01', '2026-04-28');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0010'), 720000000, '2026-02-01', '2026-01-29');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0011'), 560000000, '2026-08-01', '2026-07-30');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0012'), 380000000, '2026-06-15', NULL);
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0013'), 890000000, '2026-06-01', '2026-05-27');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0015'), 1450000000, '2026-09-15', NULL);

-- ===== Diễn biến quan hệ khách hàng =====
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0001'), 'Khách hàng mới', 'Tốt', 0, 'Tiếp nhận khách hàng mới, bàn giao cho đội kinh doanh.', '2023-03-15', (SELECT user_id FROM users WHERE username = 'sales4'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0001'), 'Ký hợp đồng mới', 'Tốt', 0, 'Ký hợp đồng cung cấp ắc quy lithium cho 45 trạm BTS.', '2026-01-15', (SELECT user_id FROM users WHERE username = 'sales4'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0002'), 'Khách hàng mới', 'Tốt', 0, 'Tiếp nhận khách hàng mới khu vực Hải Phòng.', '2023-07-02', (SELECT user_id FROM users WHERE username = 'sales2'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0002'), 'Gia hạn hợp đồng', 'Tốt', 0, 'Khách gia hạn và mở rộng phạm vi thiết bị 5G CPE.', '2026-03-10', (SELECT user_id FROM users WHERE username = 'sales2'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0003'), 'Khách hàng mới', 'Tốt', 0, 'Nhà thầu thi công khu vực Bắc Ninh.', '2024-01-20', (SELECT user_id FROM users WHERE username = 'sales3'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0004'), 'Đánh giá định kỳ', 'Cần theo dõi', 0, 'Đánh giá quý 3, khách phàn nàn về thời gian xử lý sự cố.', '2026-08-01', (SELECT user_id FROM users WHERE username = 'sales4'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0004'), 'Phản hồi về SLA', 'Cần theo dõi', 0, 'Khách phản ánh phiếu sự cố sau bão xử lý chậm so với cam kết.', '2026-09-12', (SELECT user_id FROM users WHERE username = 'sales4'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0005'), 'Khách hàng mới', 'Tốt', 0, 'Khách hàng lớn khu công nghiệp, xếp nhóm VIP.', '2022-11-08', (SELECT user_id FROM users WHERE username = 'sales5'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0005'), 'Ký hợp đồng mới', 'Tốt', 0, 'Ký hợp đồng hệ thống nguồn cho toàn khu công nghiệp.', '2026-02-28', (SELECT user_id FROM users WHERE username = 'sales5'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0006'), 'Khách hàng mới', 'Tốt', 0, 'Tiếp nhận khách hàng khu vực Ninh Bình.', '2024-09-19', (SELECT user_id FROM users WHERE username = 'sales6'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0007'), 'Đánh giá định kỳ', 'Cần theo dõi', 0, 'Hợp đồng thi công sắp kết thúc, chưa có kế hoạch tiếp theo.', '2026-07-15', (SELECT user_id FROM users WHERE username = 'sales4'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0008'), 'Khách hàng mới', 'Tốt', 0, 'Nhà thầu khu vực Thái Nguyên.', '2023-12-05', (SELECT user_id FROM users WHERE username = 'sales2'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0009'), 'Khách hàng mới', 'Tốt', 0, 'Đại lý phân phối khu vực Lạng Sơn.', '2025-04-16', (SELECT user_id FROM users WHERE username = 'sales3'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0010'), 'Đánh giá định kỳ', 'Cần theo dõi', 0, 'Đại lý mới, sản lượng chưa ổn định.', '2026-08-20', (SELECT user_id FROM users WHERE username = 'sales4'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0011'), 'Chậm thanh toán', 'Có nguy cơ rời bỏ', 0, 'Chậm thanh toán kỳ tháng 6 quá 30 ngày.', '2026-07-05', (SELECT user_id FROM users WHERE username = 'sales5'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0011'), 'Nguy cơ rời bỏ', 'Có nguy cơ rời bỏ', 0, 'Khách đang so sánh báo giá của nhà cung cấp khác.', '2026-08-30', (SELECT user_id FROM users WHERE username = 'sales5'));
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0012'), 'Khách hàng mới', 'Tốt', 0, 'Tiếp nhận khách hàng khu vực Nghệ An.', '2024-10-30', (SELECT user_id FROM users WHERE username = 'sales6'));

-- ===== Phiếu hỗ trợ kỹ thuật (16) =====
--
-- Phân bố nhóm nguyên nhân (cột thống kê được):
--   Do vận chuyển : TK-0001, TK-0010, TK-0014            (3)
--   Do lắp đặt    : TK-0003, TK-0007, TK-0011            (3)
--   Do thiết bị   : TK-0005, TK-0006, TK-0012            (3)
--   Khác          : TK-0015, TK-0016                     (2)
--   Chưa đánh giá : TK-0002, TK-0004, TK-0008, TK-0009, TK-0013  (5)
--
-- Năm phiếu cuối để NULL có chủ đích, gồm hai kiểu:
--   * phiếu "Mới tiếp nhận" (TK-0008, TK-0009, TK-0013) -- chưa ai xuống hiện
--     trường thì chưa thể biết vì sao hỏng;
--   * phiếu bảo trì định kỳ và tư vấn (TK-0002, TK-0004) -- không có sự cố nào
--     để mà truy nguyên nhân.
-- Cả hai kiểu đều là dữ liệu đúng, không phải dữ liệu thiếu.
--
-- Hạn SLA cũng trải ra: TK-0003 đã quá hạn, TK-0005 và TK-0009 sắp tới hạn,
-- còn lại trong hạn -- để ô "sắp/đã quá hạn" trên dashboard và lịch nhắc của
-- NotificationScheduler có việc mà làm.
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0001', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0001'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0001'), 'Bảo hành', 'Cao', 'Điện thoại', '2026-09-20 10:00:00', (SELECT user_id FROM users WHERE username = 'kythuat2'), (SELECT user_id FROM users WHERE username = 'cskh4'), '2026-09-10', 'Ắc quy lithium tại trạm BTS Cầu Giấy báo lỗi không sạc đầy, dừng ở mức 80 phần trăm.', 'Vỏ cell ngăn số 3 nứt do va đập trong quá trình vận chuyển lên trạm, rò điện giải khiến mạch BMS chủ động ngắt sạc.', 'Do vận chuyển', 1, 'Đã đóng', 'Đã thay ngăn ắc quy bị nứt, nạp đầy và đo kiểm tải trong 2 giờ, trạm hoạt động bình thường.', '2026-09-12 16:30:00');
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0002', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0001'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0002'), 'Bảo trì', 'Bình thường', 'Email', '2026-09-25 17:00:00', (SELECT user_id FROM users WHERE username = 'kythuat2'), (SELECT user_id FROM users WHERE username = 'cskh4'), '2026-09-05', 'Bảo trì định kỳ hệ thống nguồn quý 3 theo hợp đồng.', NULL, NULL, 0, 'Đã đóng', 'Đã vệ sinh tủ nguồn, thay 2 quạt tản nhiệt và siết lại toàn bộ đầu cốt.', '2026-09-08 16:00:00');
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0003', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0002'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0003'), 'Sửa chữa', 'Khẩn cấp', 'Điện thoại', '2026-09-13 09:00:00', (SELECT user_id FROM users WHERE username = 'kythuat3'), (SELECT user_id FROM users WHERE username = 'cskh2'), '2026-09-11', 'Thiết bị 5G CPE mất kết nối hoàn toàn tại 12 điểm lắp đặt sau cơn bão.', 'Nước mưa tràn vào hộp đấu nối do gioăng chống nước bị lắp ngược chiều tại cả 12 điểm.', 'Do lắp đặt', 1, 'Đang xử lý', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0004', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0002'), NULL, 'Tư vấn', 'Thấp', 'Website', '2026-09-30 17:00:00', (SELECT user_id FROM users WHERE username = 'kythuat2'), (SELECT user_id FROM users WHERE username = 'sales4'), '2026-09-02', 'Khách hàng hỏi giải pháp mở rộng vùng phủ 5G cho khu công nghiệp mới.', NULL, NULL, 0, 'Đã đóng', 'Đã tư vấn giải pháp 5G Outdoor kết hợp router mesh và gửi báo giá sơ bộ.', '2026-09-04 14:00:00');
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0005', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0003'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0004'), 'Bảo hành', 'Cao', 'Điện thoại', '2026-09-15 12:00:00', (SELECT user_id FROM users WHERE username = 'kythuat4'), (SELECT user_id FROM users WHERE username = 'cskh3'), '2026-09-09', 'Tủ nguồn POSTEF báo lỗi quá nhiệt và tự ngắt 3 lần trong một tuần.', 'Quạt tản nhiệt hỏng bạc đạn sau 26 tháng chạy liên tục, lưu lượng gió đo được chỉ còn khoảng 40 phần trăm so với thiết kế.', 'Do thiết bị', 1, 'Đang xử lý', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0006', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0003'), NULL, 'Sửa chữa', 'Bình thường', 'Trực tiếp', '2026-08-30 17:00:00', (SELECT user_id FROM users WHERE username = 'kythuat4'), (SELECT user_id FROM users WHERE username = 'cskh3'), '2026-08-25', 'Ắc quy Gel tại trạm Yên Phong sụt áp nhanh khi mất điện lưới.', 'Ắc quy đã hết tuổi thọ thiết kế 5 năm, dung lượng đo được còn 62 phần trăm so với danh định.', 'Do thiết bị', 0, 'Đã đóng', 'Đã thay mới toàn bộ 4 bình ắc quy Gel, cân bằng điện áp và bàn giao cho khách.', '2026-08-29 15:00:00');
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0007', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0004'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0005'), 'Sửa chữa', 'Cao', 'Điện thoại', '2026-09-18 09:00:00', (SELECT user_id FROM users WHERE username = 'kythuat5'), (SELECT user_id FROM users WHERE username = 'cskh2'), '2026-09-12', 'Tuyến cáp quang ADSS Hạ Long - Cẩm Phả suy hao vượt ngưỡng cho phép.', 'Cáp bị uốn quá bán kính cho phép tại 2 điểm treo trong lúc kéo cáp, sợi bị vi uốn gây suy hao cục bộ.', 'Do lắp đặt', 1, 'Đang xử lý', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0008', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0004'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0006'), 'Bảo trì', 'Thấp', 'Email', '2026-10-05 17:00:00', (SELECT user_id FROM users WHERE username = 'kythuat5'), (SELECT user_id FROM users WHERE username = 'cskh4'), '2026-09-13', 'Khách đề nghị kiểm tra định kỳ hệ thống UPS theo chu kỳ 6 tháng.', NULL, NULL, 0, 'Mới tiếp nhận', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0009', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0005'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0007'), 'Bảo hành', 'Khẩn cấp', 'Điện thoại', '2026-09-14 18:00:00', (SELECT user_id FROM users WHERE username = 'kythuat2'), (SELECT user_id FROM users WHERE username = 'cskh4'), '2026-09-13', 'Toàn bộ 8 tủ nguồn tại khu công nghiệp Phố Nối mất điện DC lúc 2 giờ sáng.', NULL, NULL, 1, 'Mới tiếp nhận', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0010', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0006'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0008'), 'Sửa chữa', 'Bình thường', 'Trực tiếp', '2026-09-05 17:00:00', (SELECT user_id FROM users WHERE username = 'kythuat3'), (SELECT user_id FROM users WHERE username = 'cskh3'), '2026-08-28', 'Bộ chia quang bị vỡ vỏ, phát hiện ngay khi nhận hàng tại kho Ninh Bình.', 'Thùng hàng bị rơi trong lúc bốc dỡ, lớp xốp chèn không đủ dày so với quy cách đóng gói đã ban hành.', 'Do vận chuyển', 1, 'Đã đóng', 'Đã đổi bộ chia quang mới theo chính sách bảo hành vận chuyển và cập nhật quy cách đóng gói cho các lô sau.', '2026-09-02 11:00:00');
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0011', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0007'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0009'), 'Bảo hành', 'Cao', 'Email', '2026-09-19 17:00:00', (SELECT user_id FROM users WHERE username = 'kythuat4'), (SELECT user_id FROM users WHERE username = 'cskh4'), '2026-09-08', 'Hệ thống pin mặt trời tại trạm Việt Trì sụt hiệu suất khoảng 30 phần trăm.', 'Tấm pin được lắp nghiêng 12 độ thay vì 25 độ theo thiết kế, lại nằm trong vùng bóng cột anten che khoảng 3 giờ mỗi ngày.', 'Do lắp đặt', 1, 'Đã đóng', 'Đã chỉnh khung đỡ về đúng 25 độ và dịch vị trí 1,5 mét ra khỏi vùng bóng, hiệu suất phục hồi.', '2026-09-11 16:00:00');
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0012', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0008'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0010'), 'Sửa chữa', 'Cao', 'Điện thoại', '2026-08-20 09:00:00', (SELECT user_id FROM users WHERE username = 'kythuat5'), (SELECT user_id FROM users WHERE username = 'cskh2'), '2026-08-15', 'Nguồn UNIPOWER báo lỗi module rectifier số 2, hệ thống chạy thiếu dự phòng.', 'Module rectifier lỗi tụ đầu vào ngay từ nhà sản xuất, cùng lô với 3 ca đã ghi nhận trước đó.', 'Do thiết bị', 1, 'Đã đóng', 'Đã thay module rectifier mới và gửi module lỗi về hãng để đổi bảo hành.', '2026-08-19 14:30:00');
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0013', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0009'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0011'), 'Tư vấn', 'Thấp', 'Website', '2026-10-10 17:00:00', (SELECT user_id FROM users WHERE username = 'kythuat2'), (SELECT user_id FROM users WHERE username = 'cskh4'), '2026-09-13', 'Đại lý hỏi quy cách đóng gói cáp quang cho đơn hàng đi Cao Bằng.', NULL, NULL, 0, 'Mới tiếp nhận', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0014', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0010'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0012'), 'Bảo hành', 'Bình thường', 'Điện thoại', '2026-09-02 17:00:00', (SELECT user_id FROM users WHERE username = 'kythuat3'), (SELECT user_id FROM users WHERE username = 'cskh3'), '2026-08-26', 'Dây thuê bao đệm chặt bị đứt lõi khi bóc vỏ, tỉ lệ hỏng khoảng 8 phần trăm.', 'Cuộn cáp bị ép biến dạng do xếp chồng quá 4 tầng trên xe tải, lõi gãy ngầm bên trong mà nhìn ngoài không thấy.', 'Do vận chuyển', 1, 'Đã đóng', 'Đã thu hồi và đổi cuộn cáp mới, đồng thời phổ biến lại giới hạn xếp chồng cho đơn vị vận chuyển.', '2026-09-01 10:00:00');
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0015', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0011'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0013'), 'Sửa chữa', 'Cao', 'Trực tiếp', '2026-09-16 09:00:00', (SELECT user_id FROM users WHERE username = 'kythuat4'), (SELECT user_id FROM users WHERE username = 'cskh4'), '2026-09-10', 'UPS EATON tại Sầm Sơn liên tục chuyển sang chế độ bypass.', 'Hơi muối biển và độ ẩm cao gây oxy hoá tiếp điểm contactor. Môi trường lắp đặt ven biển nằm ngoài điều kiện vận hành khuyến cáo của thiết bị.', 'Khác', 0, 'Đang xử lý', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, root_cause, cause_category, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0016', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0012'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0014'), 'Bảo trì', 'Bình thường', 'Email', '2026-08-31 17:00:00', (SELECT user_id FROM users WHERE username = 'kythuat5'), (SELECT user_id FROM users WHERE username = 'cskh2'), '2026-08-24', 'Kiểm tra hệ thống nguồn sau sự cố mất điện lưới diện rộng toàn khu vực.', 'Mất điện lưới kéo dài 11 giờ, vượt mức dự phòng 8 giờ mà hệ thống ắc quy được thiết kế để gánh.', 'Khác', 0, 'Đã đóng', 'Đã nạp lại toàn bộ ắc quy, đo dung lượng còn 94 phần trăm và khuyến nghị bổ sung thêm 2 bình.', '2026-08-28 17:00:00');

-- ===== Lịch sử đổi trạng thái =====
-- Dòng đầu mỗi phiếu có from_status rỗng = mốc lập phiếu, đúng quy ước mà
-- viewdetailTicket.jsp đang dựa vào để hiện chữ "Tạo phiếu".
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0001'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh4'), '2026-09-10 09:00:00', 'Tiếp nhận phiếu từ khách hàng.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0001'), 'Mới tiếp nhận', 'Đang xử lý', (SELECT user_id FROM users WHERE username = 'kythuat2'), '2026-09-10 13:30:00', 'Kỹ thuật viên lên trạm kiểm tra.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0001'), 'Đang xử lý', 'Đã đóng', (SELECT user_id FROM users WHERE username = 'kythuat2'), '2026-09-12 16:30:00', 'Xác định do va đập khi vận chuyển, đã thay ngăn hỏng và nghiệm thu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0002'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh4'), '2026-09-05 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0002'), 'Mới tiếp nhận', 'Đang xử lý', (SELECT user_id FROM users WHERE username = 'kythuat2'), '2026-09-06 08:30:00', 'Lên lịch bảo trì định kỳ.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0002'), 'Đang xử lý', 'Đã đóng', (SELECT user_id FROM users WHERE username = 'kythuat2'), '2026-09-08 16:00:00', 'Bảo trì xong, khách ký biên bản.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0003'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh2'), '2026-09-11 09:00:00', 'Khách báo sự cố diện rộng sau bão.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0003'), 'Mới tiếp nhận', 'Đang xử lý', (SELECT user_id FROM users WHERE username = 'kythuat3'), '2026-09-11 10:15:00', 'Đã khảo sát 12 điểm, xác định nguyên nhân do lắp sai gioăng.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0004'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'sales4'), '2026-09-02 09:00:00', 'Yêu cầu tư vấn từ website.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0004'), 'Mới tiếp nhận', 'Đang xử lý', (SELECT user_id FROM users WHERE username = 'kythuat2'), '2026-09-03 09:00:00', 'Chuẩn bị phương án và báo giá.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0004'), 'Đang xử lý', 'Đã đóng', (SELECT user_id FROM users WHERE username = 'kythuat2'), '2026-09-04 14:00:00', 'Đã gửi báo giá, khách xác nhận đủ thông tin.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0005'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh3'), '2026-09-09 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0005'), 'Mới tiếp nhận', 'Đang xử lý', (SELECT user_id FROM users WHERE username = 'kythuat4'), '2026-09-09 14:00:00', 'Đo kiểm tại chỗ, nghi quạt tản nhiệt hỏng bạc đạn.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0006'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh3'), '2026-08-25 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0006'), 'Mới tiếp nhận', 'Đang xử lý', (SELECT user_id FROM users WHERE username = 'kythuat4'), '2026-08-26 08:00:00', 'Kiểm tra dung lượng ắc quy.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0006'), 'Đang xử lý', 'Đã đóng', (SELECT user_id FROM users WHERE username = 'kythuat4'), '2026-08-29 15:00:00', 'Ắc quy hết tuổi thọ, đã thay mới và bàn giao.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0007'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh2'), '2026-09-12 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0007'), 'Mới tiếp nhận', 'Đang xử lý', (SELECT user_id FROM users WHERE username = 'kythuat5'), '2026-09-12 15:00:00', 'Đo OTDR, khoanh vùng 2 điểm treo nghi vấn.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0008'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh4'), '2026-09-13 09:00:00', 'Tiếp nhận yêu cầu bảo trì định kỳ.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0009'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh4'), '2026-09-13 03:20:00', 'Khách gọi báo sự cố khẩn lúc rạng sáng.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0010'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh3'), '2026-08-28 09:00:00', 'Phát hiện hàng hỏng khi nghiệm thu tại kho.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0010'), 'Mới tiếp nhận', 'Đang xử lý', (SELECT user_id FROM users WHERE username = 'kythuat3'), '2026-08-29 09:00:00', 'Lập biên bản hàng hỏng, đối chiếu quy cách đóng gói.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0010'), 'Đang xử lý', 'Đã đóng', (SELECT user_id FROM users WHERE username = 'kythuat3'), '2026-09-02 11:00:00', 'Do vận chuyển, đã đổi hàng mới và siết lại quy cách đóng gói.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0011'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh4'), '2026-09-08 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0011'), 'Mới tiếp nhận', 'Đang xử lý', (SELECT user_id FROM users WHERE username = 'kythuat4'), '2026-09-09 08:30:00', 'Khảo sát góc nghiêng và vùng bóng che.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0011'), 'Đang xử lý', 'Đã đóng', (SELECT user_id FROM users WHERE username = 'kythuat4'), '2026-09-11 16:00:00', 'Lỗi do lắp đặt sai thiết kế, đã chỉnh lại và nghiệm thu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0012'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh2'), '2026-08-15 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0012'), 'Mới tiếp nhận', 'Đang xử lý', (SELECT user_id FROM users WHERE username = 'kythuat5'), '2026-08-16 09:00:00', 'Thay thử module dự phòng để khoanh vùng lỗi.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0012'), 'Đang xử lý', 'Đã đóng', (SELECT user_id FROM users WHERE username = 'kythuat5'), '2026-08-19 14:30:00', 'Lỗi linh kiện từ nhà sản xuất, đã đổi bảo hành.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0013'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh4'), '2026-09-13 10:00:00', 'Đại lý gửi câu hỏi qua website.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0014'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh3'), '2026-08-26 09:00:00', 'Đại lý báo tỉ lệ đứt lõi bất thường.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0014'), 'Mới tiếp nhận', 'Đang xử lý', (SELECT user_id FROM users WHERE username = 'kythuat3'), '2026-08-27 09:00:00', 'Kiểm tra mẫu và truy lại lộ trình vận chuyển.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0014'), 'Đang xử lý', 'Đã đóng', (SELECT user_id FROM users WHERE username = 'kythuat3'), '2026-09-01 10:00:00', 'Do xếp chồng quá tầng khi vận chuyển, đã đổi hàng.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0015'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh4'), '2026-09-10 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0015'), 'Mới tiếp nhận', 'Đang xử lý', (SELECT user_id FROM users WHERE username = 'kythuat4'), '2026-09-10 15:30:00', 'Mở máy kiểm tra, thấy tiếp điểm contactor bị oxy hoá.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0016'), '', 'Mới tiếp nhận', (SELECT user_id FROM users WHERE username = 'cskh2'), '2026-08-24 09:00:00', 'Tiếp nhận sau sự cố mất điện diện rộng.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0016'), 'Mới tiếp nhận', 'Đang xử lý', (SELECT user_id FROM users WHERE username = 'kythuat5'), '2026-08-25 08:00:00', 'Kiểm tra dung lượng ắc quy sau khi mất điện kéo dài.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0016'), 'Đang xử lý', 'Đã đóng', (SELECT user_id FROM users WHERE username = 'kythuat5'), '2026-08-28 17:00:00', 'Nguyên nhân ngoài thiết kế dự phòng, đã khuyến nghị bổ sung ắc quy.');

-- ===== Thiết bị lỗi kèm phiếu =====
-- Chưa có model/DAO nào đọc bảng này (xem ghi chú đầu TechnicalSupportTicketDAO)
-- nên không màn hình nào hiện ra, nhưng bộ demo cũ có dữ liệu ở đây -- gieo lại
-- để bộ mới không nghèo hơn bộ cũ, và để khi nào làm tới phần thiết bị lỗi thì
-- đã có sẵn cái mà hiển thị.
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0001'), (SELECT product_id FROM products WHERE product_code = 'SP-0001'), (SELECT product_name FROM products WHERE product_code = 'SP-0001'), 'PDA10-2026-0088', 'Ngăn số 3 nứt vỏ, rò điện giải, BMS ngắt sạc ở 80 phần trăm');
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0005'), (SELECT product_id FROM products WHERE product_code = 'SP-0006'), (SELECT product_name FROM products WHERE product_code = 'SP-0006'), 'PSU-2024-0412', 'Quạt tản nhiệt hỏng bạc đạn, tủ tự ngắt do quá nhiệt');
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0006'), (SELECT product_id FROM products WHERE product_code = 'SP-0003'), (SELECT product_name FROM products WHERE product_code = 'SP-0003'), 'GEL-2021-1177', 'Dung lượng còn 62 phần trăm so với danh định');
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0010'), (SELECT product_id FROM products WHERE product_code = 'SP-0011'), (SELECT product_name FROM products WHERE product_code = 'SP-0011'), NULL, 'Vỡ vỏ bộ chia quang, phát hiện khi nghiệm thu tại kho');
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0011'), (SELECT product_id FROM products WHERE product_code = 'SP-0009'), (SELECT product_name FROM products WHERE product_code = 'SP-0009'), 'SOL-2026-0031', 'Hiệu suất giảm khoảng 30 phần trăm do lắp sai góc nghiêng');
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0012'), (SELECT product_id FROM products WHERE product_code = 'SP-0007'), (SELECT product_name FROM products WHERE product_code = 'SP-0007'), 'UNI-2025-0903', 'Module rectifier số 2 lỗi tụ đầu vào');
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0014'), (SELECT product_id FROM products WHERE product_code = 'SP-0014'), (SELECT product_name FROM products WHERE product_code = 'SP-0014'), NULL, 'Đứt lõi ngầm khi bóc vỏ, tỉ lệ khoảng 8 phần trăm');
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0015'), (SELECT product_id FROM products WHERE product_code = 'SP-0008'), (SELECT product_name FROM products WHERE product_code = 'SP-0008'), 'EAT-2026-0550', 'Tiếp điểm contactor oxy hoá, máy liên tục nhảy bypass');
