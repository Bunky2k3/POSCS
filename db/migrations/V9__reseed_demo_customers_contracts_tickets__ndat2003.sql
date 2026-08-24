-- Old customers/contracts/tickets were disconnected demo data (laptops, printers -- unrelated to POSTEF's real catalog) left over from earlier iterations of the DB. Wipes enterprises/contracts/technicalrequests (and everything hanging off them) and reseeds a consistent demo set: 12 enterprises, contacts, contracts with real POSTEF product line items, payments, relationship lifecycle events, and support tickets -- all tied to the real seeded users (admin/sales1/kythuat1/cskh1).

INSERT INTO schema_migrations (version) VALUES ('V9__reseed_demo_customers_contracts_tickets__ndat2003');

-- Order respects FK constraints (children before parents); cascades handle
-- contractproducts/enterprisecontacts/technicalrequestdevices/technicalrequesthistory.
DELETE FROM technicalrequests;
DELETE FROM contract_payments;
DELETE FROM contracts;
DELETE FROM customer_lifecycle_events;
DELETE FROM enterprises;

ALTER TABLE enterprises AUTO_INCREMENT = 1;
ALTER TABLE enterprisecontacts AUTO_INCREMENT = 1;
ALTER TABLE contracts AUTO_INCREMENT = 1;
ALTER TABLE contractproducts AUTO_INCREMENT = 1;
ALTER TABLE contract_payments AUTO_INCREMENT = 1;
ALTER TABLE customer_lifecycle_events AUTO_INCREMENT = 1;
ALTER TABLE technicalrequests AUTO_INCREMENT = 1;
ALTER TABLE technicalrequestdevices AUTO_INCREMENT = 1;
ALTER TABLE technicalrequesthistory AUTO_INCREMENT = 1;

-- ===== Địa chỉ (12) =====
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('Số 15 Phố Duy Tân', 14);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('88 Đường Cộng Hòa', 2662);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('20 Đường Nguyễn Văn Linh', 1852);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('45 Phố Tây Sơn', 20);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('102 Đường Xô Viết Nghệ Tĩnh', 2655);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('12 Đường Lạch Tray', 1022);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('56 Đường Kinh Dương Vương', 775);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('77 Đường Phan Xích Long', 2671);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('34 Đường Đồng Khởi', 2468);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('9 Đường Trần Phú', 2177);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('18 Đường Lê Lợi', 1369);
INSERT INTO addresses (street_and_local_name, districts_id) VALUES ('63 Đường Lê Duẩn', 1535);

-- ===== Khách hàng doanh nghiệp (12) =====
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, legal_representative, current_relationship_rating, join_date) VALUES
('KH-0001', 'Công ty Cổ phần Viễn thông Sông Hồng', 'Nhà mạng viễn thông', 'VIP', '0101234501', 'contact@songhongtelecom.vn', '0243826001', 'https://songhongtelecom.vn', (SELECT address_id FROM addresses WHERE street_and_local_name = 'Số 15 Phố Duy Tân' ORDER BY address_id DESC LIMIT 1), 15, 'Trần Văn Hùng', 'Tốt', '2022-03-10');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, legal_representative, current_relationship_rating, join_date) VALUES
('KH-0002', 'Công ty TNHH Viễn thông Miền Nam Phát', 'Nhà mạng viễn thông', 'Thân thiết', '0301234502', 'info@miennamphat.vn', '0283922002', 'https://miennamphat.vn', (SELECT address_id FROM addresses WHERE street_and_local_name = '88 Đường Cộng Hòa' ORDER BY address_id DESC LIMIT 1), 15, 'Lê Thị Mai', 'Tốt', '2021-11-05');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, legal_representative, current_relationship_rating, join_date) VALUES
('KH-0003', 'Công ty Cổ phần Hạ tầng Mạng Việt Á', 'Nhà mạng viễn thông', 'Tiềm năng', '0401234503', 'contact@vietanetwork.vn', '0236377003', 'https://vietanetwork.vn', (SELECT address_id FROM addresses WHERE street_and_local_name = '20 Đường Nguyễn Văn Linh' ORDER BY address_id DESC LIMIT 1), 1, 'Phạm Quốc Bảo', 'Cần theo dõi', '2024-06-20');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, legal_representative, current_relationship_rating, join_date) VALUES
('KH-0004', 'Công ty TNHH Xây dựng Viễn thông Thăng Long', 'Nhà thầu thi công', 'VIP', '0101234504', 'contact@thanglongtelecom.vn', '0243662004', 'https://thanglongtelecom.vn', (SELECT address_id FROM addresses WHERE street_and_local_name = '45 Phố Tây Sơn' ORDER BY address_id DESC LIMIT 1), 15, 'Nguyễn Đức Thắng', 'Tốt', '2020-08-15');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, legal_representative, current_relationship_rating, join_date) VALUES
('KH-0005', 'Công ty Cổ phần Xây lắp Điện & Viễn thông Phương Nam', 'Nhà thầu thi công', 'Thân thiết', '0301234505', 'info@phuongnamelectric.vn', '0283998005', 'https://phuongnamelectric.vn', (SELECT address_id FROM addresses WHERE street_and_local_name = '102 Đường Xô Viết Nghệ Tĩnh' ORDER BY address_id DESC LIMIT 1), 15, 'Võ Thành Nam', 'Tốt', '2022-01-25');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, legal_representative, current_relationship_rating, join_date) VALUES
('KH-0006', 'Công ty TNHH Thi công Cơ điện Hải Phòng', 'Nhà thầu thi công', 'Thường', '0201234506', 'lienhe@codienhaiphong.vn', '0225382006', NULL, (SELECT address_id FROM addresses WHERE street_and_local_name = '12 Đường Lạch Tray' ORDER BY address_id DESC LIMIT 1), 15, 'Đỗ Văn Kiên', 'Cần theo dõi', '2023-09-12');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, legal_representative, current_relationship_rating, join_date) VALUES
('KH-0007', 'Công ty Cổ phần Đầu tư Xây dựng Bắc Ninh', 'Nhà thầu thi công', 'Tiềm năng', '0231234507', 'info@xaydungbacninh.vn', '0222371007', 'https://xaydungbacninh.vn', (SELECT address_id FROM addresses WHERE street_and_local_name = '56 Đường Kinh Dương Vương' ORDER BY address_id DESC LIMIT 1), 1, 'Nguyễn Thị Hòa', 'Cần theo dõi', '2025-02-18');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, legal_representative, current_relationship_rating, join_date) VALUES
('KH-0008', 'Công ty TNHH Phân phối Thiết bị Viễn thông An Phát', 'Đại lý phân phối', 'VIP', '0301234508', 'sales@anphatdistribution.vn', '0283845008', 'https://anphatdistribution.vn', (SELECT address_id FROM addresses WHERE street_and_local_name = '77 Đường Phan Xích Long' ORDER BY address_id DESC LIMIT 1), 15, 'Trịnh Minh Tuấn', 'Tốt', '2019-05-30');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, legal_representative, current_relationship_rating, join_date) VALUES
('KH-0009', 'Công ty Cổ phần Thương mại Thiết bị Công nghệ Đồng Nai', 'Đại lý phân phối', 'Thân thiết', '0361234509', 'contact@dncntech.vn', '0251389009', 'https://dncntech.vn', (SELECT address_id FROM addresses WHERE street_and_local_name = '34 Đường Đồng Khởi' ORDER BY address_id DESC LIMIT 1), 15, 'Hoàng Văn Phúc', 'Tốt', '2021-07-08');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, legal_representative, current_relationship_rating, join_date) VALUES
('KH-0010', 'Công ty TNHH Đại lý Thiết bị Viễn thông Nha Trang', 'Đại lý phân phối', 'Thường', '0421234510', 'info@nhatrangtelecom.vn', '0258356010', NULL, (SELECT address_id FROM addresses WHERE street_and_local_name = '9 Đường Trần Phú' ORDER BY address_id DESC LIMIT 1), 15, 'Lâm Thị Kim Ngân', 'Cần theo dõi', '2023-12-01');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, legal_representative, current_relationship_rating, join_date) VALUES
('KH-0011', 'Công ty Cổ phần Vật tư Viễn thông Thanh Hóa', 'Đại lý phân phối', 'Tiềm năng', '0381234511', 'lienhe@vattuthanhhoa.vn', '0237372011', 'https://vattuthanhhoa.vn', (SELECT address_id FROM addresses WHERE street_and_local_name = '18 Đường Lê Lợi' ORDER BY address_id DESC LIMIT 1), 1, 'Bùi Xuân Trường', 'Tốt', '2024-10-14');
INSERT INTO enterprises (enterprise_code, enterprise_name, customer_type, customer_group, tax_code, email, phone, website, address_id, account_owner_id, legal_representative, current_relationship_rating, join_date) VALUES
('KH-0012', 'Công ty TNHH Xây dựng Hạ tầng Viễn thông Nghệ An', 'Nhà thầu thi công', 'Thường', '0291234512', 'info@hatangnghean.vn', '0238381012', NULL, (SELECT address_id FROM addresses WHERE street_and_local_name = '63 Đường Lê Duẩn' ORDER BY address_id DESC LIMIT 1), 15, 'Cao Văn Đạt', 'Có nguy cơ rời bỏ', '2020-02-28');

-- ===== Người liên hệ (16) =====
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0001'), 'Trần', 'Văn', 'Hùng', '0912345601', 'hung.tran@songhongtelecom.vn', 'Giám đốc');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0001'), 'Nguyễn', 'Thị', 'Lan', '0912345602', 'lan.nguyen@songhongtelecom.vn', 'Trưởng phòng Kỹ thuật');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0002'), 'Lê', 'Thị', 'Mai', '0912345603', 'mai.le@miennamphat.vn', 'Giám đốc kinh doanh');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0003'), 'Phạm', 'Quốc', 'Bảo', '0912345604', 'bao.pham@vietanetwork.vn', 'Trưởng phòng Kỹ thuật');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0004'), 'Nguyễn', 'Đức', 'Thắng', '0912345605', 'thang.nguyen@thanglongtelecom.vn', 'Giám đốc');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0004'), 'Vũ', 'Thị', 'Hạnh', '0912345606', 'hanh.vu@thanglongtelecom.vn', 'Kế toán trưởng');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0005'), 'Võ', 'Thành', 'Nam', '0912345607', 'nam.vo@phuongnamelectric.vn', 'Giám đốc điều hành');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0006'), 'Đỗ', 'Văn', 'Kiên', '0912345608', 'kien.do@codienhaiphong.vn', 'Trưởng phòng Kỹ thuật');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0007'), 'Nguyễn', 'Thị', 'Hòa', '0912345609', 'hoa.nguyen@xaydungbacninh.vn', 'Giám đốc');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0008'), 'Trịnh', 'Minh', 'Tuấn', '0912345610', 'tuan.trinh@anphatdistribution.vn', 'Giám đốc');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0008'), 'Phan', 'Thị', 'Ngọc', '0912345611', 'ngoc.phan@anphatdistribution.vn', 'Nhân viên mua hàng');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0009'), 'Hoàng', 'Văn', 'Phúc', '0912345612', 'phuc.hoang@dncntech.vn', 'Giám đốc kinh doanh');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0010'), 'Lâm', 'Thị Kim', 'Ngân', '0912345613', 'ngan.lam@nhatrangtelecom.vn', 'Chủ đại lý');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0011'), 'Bùi', 'Xuân', 'Trường', '0912345614', 'truong.bui@vattuthanhhoa.vn', 'Giám đốc');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0012'), 'Cao', 'Văn', 'Đạt', '0912345615', 'dat.cao@hatangnghean.vn', 'Giám đốc');
INSERT INTO enterprisecontacts (enterprise_id, contact_last_name, contact_middle_name, contact_first_name, contact_phone, contact_email, position) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0012'), 'Ngô', 'Thị', 'Thu', '0912345616', 'thu.ngo@hatangnghean.vn', 'Kế toán');

-- ===== Hợp đồng (15) =====
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0001', 'Cung cấp thiết bị nguồn trạm BTS đợt 1', 'Cung cấp thiết bị', '2025-01-10', '2025-01-15', '2027-01-14', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0001'), 15);
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0002', 'Bảo trì hệ thống cắt lọc sét năm 2026', 'Bảo trì bảo dưỡng', '2026-01-05', '2026-01-10', '2026-09-09', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0001'), 15);
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0003', 'Cung cấp thiết bị 5G CPE/MiFi đợt 1', 'Cung cấp thiết bị', '2024-05-01', '2024-05-10', '2025-05-09', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0002'), 15);
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0004', 'Thi công tuyến cáp quang kéo cống mở rộng vùng phủ', 'Thi công lắp đặt', '2026-07-01', '2026-09-01', '2027-08-31', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0002'), 15);
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0005', 'Cung cấp router wifi 6/7 cho hạ tầng mạng', 'Cung cấp thiết bị', '2026-03-15', '2026-03-20', '2027-03-19', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0003'), 1);
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0006', 'Thi công lắp đặt ăng ten và tủ nguồn trạm BTS', 'Thi công lắp đặt', '2025-06-01', '2025-06-10', '2026-06-09', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0004'), 15);
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0007', 'Bảo trì thiết bị cắt lọc sét năm 2026', 'Bảo trì bảo dưỡng', '2026-06-01', '2026-06-05', '2027-06-04', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0004'), 15);
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0008', 'Thi công lắp đặt tủ nguồn công trình mới', 'Thi công lắp đặt', '2026-02-01', '2026-02-10', '2026-09-15', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0005'), 15);
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0009', 'Cung cấp ắc quy Gel dự phòng công trình', 'Cung cấp thiết bị', '2026-04-10', '2026-04-15', '2027-04-14', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0006'), 15);
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0010', 'Thi công tuyến cáp quang treo khu công nghiệp', 'Thi công lắp đặt', '2026-08-01', '2026-09-20', '2027-09-19', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0007'), 1);
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0011', 'Cung cấp thiết bị phân phối đợt 1', 'Cung cấp thiết bị', '2025-09-01', '2025-09-05', '2026-09-04', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0008'), 15);
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0012', 'Cung cấp cảm biến IoT LoRa cho đại lý', 'Cung cấp thiết bị', '2024-11-01', '2024-11-10', '2025-11-09', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0009'), 15);
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0013', 'Cung cấp điện thoại di động Raisecom cho đại lý', 'Cung cấp thiết bị', '2026-05-15', '2026-05-20', '2027-05-19', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0010'), 15);
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0014', 'Thi công lắp đặt tủ phân phối điện trạm viễn thông', 'Thi công lắp đặt', '2025-03-01', '2025-03-10', '2026-03-09', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0012'), 15);
INSERT INTO contracts (contract_code, title, contract_type, signing_date, effective_date, end_date, enterprise_id, owner_id) VALUES
('HD-0015', 'Cung cấp sợi quang G657A1 cho đại lý', 'Cung cấp thiết bị', '2026-08-10', '2026-08-15', '2027-08-14', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0011'), 1);

-- ===== Sản phẩm trong hợp đồng (26) =====
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0001'), (SELECT product_id FROM products WHERE product_code = 'SP-0001'), 20, 'Bộ', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0001'), (SELECT product_id FROM products WHERE product_code = 'SP-0033'), 5, 'Tủ', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0001'), (SELECT product_id FROM products WHERE product_code = 'SP-0006'), 10, 'Bộ', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0002'), (SELECT product_id FROM products WHERE product_code = 'SP-0040'), 8, 'Bộ', 'Kèm bảo trì định kỳ 6 tháng/lần');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0003'), (SELECT product_id FROM products WHERE product_code = 'SP-0049'), 50, 'Cái', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0003'), (SELECT product_id FROM products WHERE product_code = 'SP-0053'), 30, 'Cái', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0004'), (SELECT product_id FROM products WHERE product_code = 'SP-0012'), 2000, 'Mét', 'Cáp kéo cống 48FO');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0004'), (SELECT product_id FROM products WHERE product_code = 'SP-0023'), 40, 'Cái', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0005'), (SELECT product_id FROM products WHERE product_code = 'SP-0078'), 15, 'Cái', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0005'), (SELECT product_id FROM products WHERE product_code = 'SP-0080'), 20, 'Cái', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0006'), (SELECT product_id FROM products WHERE product_code = 'SP-0028'), 6, 'Bộ', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0006'), (SELECT product_id FROM products WHERE product_code = 'SP-0036'), 6, 'Tủ', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0007'), (SELECT product_id FROM products WHERE product_code = 'SP-0040'), 12, 'Bộ', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0008'), (SELECT product_id FROM products WHERE product_code = 'SP-0035'), 4, 'Tủ', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0008'), (SELECT product_id FROM products WHERE product_code = 'SP-0032'), 4, 'Bộ', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0009'), (SELECT product_id FROM products WHERE product_code = 'SP-0003'), 30, 'Bình', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0010'), (SELECT product_id FROM products WHERE product_code = 'SP-0013'), 1500, 'Mét', 'Cáp treo 96FO');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0011'), (SELECT product_id FROM products WHERE product_code = 'SP-0001'), 40, 'Bộ', 'Giao theo đợt, đợt 1: 20 bộ');
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0011'), (SELECT product_id FROM products WHERE product_code = 'SP-0086'), 20, 'Cái', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0011'), (SELECT product_id FROM products WHERE product_code = 'SP-0088'), 15, 'Cái', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0012'), (SELECT product_id FROM products WHERE product_code = 'SP-0074'), 100, 'Cái', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0012'), (SELECT product_id FROM products WHERE product_code = 'SP-0077'), 60, 'Cái', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0013'), (SELECT product_id FROM products WHERE product_code = 'SP-0048'), 50, 'Cái', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0013'), (SELECT product_id FROM products WHERE product_code = 'SP-0047'), 30, 'Cái', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0014'), (SELECT product_id FROM products WHERE product_code = 'SP-0038'), 5, 'Tủ', NULL);
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0015'), (SELECT product_id FROM products WHERE product_code = 'SP-0016'), 2000, 'Mét', NULL);

-- ===== Thanh toán hợp đồng (17) =====
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0001'), 450000000.00, '2025-02-15', '2025-02-10');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0001'), 320000000.00, '2026-09-15', NULL);
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0002'), 85000000.00, '2026-08-30', NULL);
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0003'), 620000000.00, '2024-06-01', '2024-06-08');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0003'), 300000000.00, '2024-12-01', '2024-11-27');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0005'), 210000000.00, '2026-04-20', '2026-04-18');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0006'), 180000000.00, '2025-07-01', '2025-07-01');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0006'), 150000000.00, '2026-01-01', '2026-01-10');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0007'), 95000000.00, '2026-07-01', '2026-06-25');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0008'), 130000000.00, '2026-09-01', NULL);
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0009'), 260000000.00, '2026-05-15', '2026-05-20');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0011'), 500000000.00, '2025-10-01', '2025-09-28');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0011'), 400000000.00, '2026-09-01', NULL);
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0012'), 340000000.00, '2025-01-01', '2025-01-15');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0013'), 175000000.00, '2026-06-20', '2026-06-19');
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0014'), 220000000.00, '2025-04-01', NULL);
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date) VALUES
((SELECT contract_id FROM contracts WHERE contract_code = 'HD-0015'), 260000000.00, '2026-09-10', NULL);

-- ===== Lịch sử vòng đời khách hàng (12) =====
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0001'), 'Ký hợp đồng mới', 'Tốt', 0, 'Ký hợp đồng cung cấp thiết bị nguồn trạm BTS đợt 1, giá trị lớn, thanh toán đúng hạn.', '2025-01-10', 15);
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0002'), 'Đánh giá định kỳ', 'Tốt', 1, 'Mua nhiều, thanh toán sớm hầu hết các đợt, đánh giá quan hệ tốt.', '2026-06-30', 1);
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0003'), 'Khách hàng mới', 'Cần theo dõi', 0, 'Khách hàng mới ký hợp đồng đầu tiên, cần theo dõi tiến độ thanh toán các kỳ tới.', '2026-03-20', 1);
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0004'), 'Gia hạn hợp đồng', 'Tốt', 0, 'Khách hàng lâu năm, tiếp tục ký hợp đồng bảo trì mới sau khi hợp đồng thi công kết thúc.', '2026-06-01', 15);
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0005'), 'Đánh giá định kỳ', 'Tốt', 1, 'Thanh toán đúng/sớm hạn, hợp tác tốt trong quá trình thi công.', '2026-05-01', 1);
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0006'), 'Phản hồi về SLA', 'Cần theo dõi', 0, 'Từng phản hồi về thời gian xử lý phiếu bảo hành ắc quy hơi chậm, đã cải thiện sau đó.', '2026-06-15', 17);
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0007'), 'Khách hàng mới', 'Cần theo dõi', 0, 'Khách hàng mới, đang trong giai đoạn thi công hợp đồng đầu tiên, chưa có lịch sử thanh toán.', '2025-02-18', 1);
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0008'), 'Đánh giá định kỳ', 'Tốt', 1, 'Đại lý VIP, khối lượng mua lớn, thanh toán sớm hạn.', '2026-01-15', 1);
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0009'), 'Chậm thanh toán', 'Tốt', 1, 'Có 1 lần thanh toán trễ 14 ngày nhưng đã khắc phục, tổng thể vẫn là khách hàng tốt.', '2025-01-20', 1);
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0010'), 'Đánh giá định kỳ', 'Cần theo dõi', 1, 'Khối lượng mua ở mức trung bình, cần thêm chương trình thúc đẩy doanh số.', '2026-07-01', 1);
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0011'), 'Khách hàng mới', 'Tốt', 0, 'Đại lý mới, đơn hàng đầu tiên khởi đầu thuận lợi.', '2026-08-15', 1);
INSERT INTO customer_lifecycle_events (enterprise_id, event_type, relationship_rating, is_auto_generated, description, event_date, recorded_by) VALUES
((SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0012'), 'Nguy cơ rời bỏ', 'Có nguy cơ rời bỏ', 1, 'Hợp đồng đã hết hạn nhưng còn khoản thanh toán 220 triệu đồng quá hạn chưa thu hồi được, khách hàng không phản hồi liên hệ gần đây.', '2026-08-01', 15);

-- ===== Phiếu hỗ trợ kỹ thuật (14) =====
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0001', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0001'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0001'), 'Bảo hành', 'Cao', 'Điện thoại', '2026-08-27 10:00:00', 16, 17, '2026-08-20', 'Ắc quy lithium tại trạm BTS Cầu Giấy báo lỗi không sạc đầy, cần kiểm tra gấp.', 1, 'Đang xử lý', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0002', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0001'), NULL, 'Bảo trì', 'Bình thường', 'Email', '2026-07-22 17:00:00', 16, 17, '2026-07-15', 'Bảo trì định kỳ hệ thống nguồn quý 3.', 0, 'Đã đóng', 'Đã kiểm tra, vệ sinh, thay thế 2 quạt tản nhiệt tủ nguồn.', '2026-07-20 16:30:00');
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0003', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0002'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0003'), 'Sửa chữa', 'Khẩn cấp', 'Điện thoại', '2026-08-25 09:00:00', 16, 17, '2026-08-22', '5G CPE C150 mất kết nối hoàn toàn tại 12 điểm lắp đặt sau cơn bão.', 1, 'Mới tiếp nhận', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0004', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0002'), NULL, 'Tư vấn', 'Thấp', 'Website', '2026-08-17 17:00:00', 16, 15, '2026-08-10', 'Khách hàng hỏi về giải pháp mở rộng vùng phủ 5G cho khu công nghiệp mới.', 0, 'Đã đóng', 'Đã tư vấn giải pháp 5G Outdoor O022 kết hợp router mesh, gửi báo giá.', '2026-08-12 14:00:00');
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0005', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0003'), NULL, 'Bảo hành', 'Cao', 'Trực tiếp', '2026-08-28 17:00:00', 16, 17, '2026-08-05', 'Router MI10 tại văn phòng bị mất sóng mesh chập chờn.', 1, 'Đang xử lý', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0006', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0004'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0006'), 'Sửa chữa', 'Bình thường', 'Điện thoại', '2025-08-08 17:00:00', 16, 17, '2025-08-01', 'Ăng ten POSTEF lắp đặt bị lệch hướng sau thi công, tín hiệu yếu.', 1, 'Đã đóng', 'Đã hiệu chỉnh lại góc ăng ten, đo kiểm tín hiệu đạt chuẩn.', '2025-08-05 11:00:00');
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0007', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0004'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0007'), 'Bảo trì', 'Thấp', 'Email', '2026-09-05 09:00:00', 16, 15, '2026-08-18', 'Đề nghị lên lịch bảo trì định kỳ thiết bị cắt lọc sét quý 4.', 0, 'Mới tiếp nhận', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0008', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0005'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0008'), 'Sửa chữa', 'Khẩn cấp', 'Điện thoại', '2026-08-25 08:00:00', 16, 17, '2026-08-23', 'Tủ nguồn PP/RU tại công trình báo lỗi ngắt điện đột ngột, ảnh hưởng tiến độ thi công.', 1, 'Đang xử lý', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0009', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0006'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0009'), 'Bảo hành', 'Bình thường', 'Email', '2026-06-08 17:00:00', 16, 17, '2026-06-01', '3 bình ắc quy Gel phồng vỏ sau 2 tháng sử dụng.', 1, 'Đã đóng', 'Xác nhận lỗi nhà sản xuất, đã đổi mới 3 bình ắc quy.', '2026-06-10 15:00:00');
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0010', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0007'), NULL, 'Tư vấn', 'Thấp', 'Trực tiếp', '2026-09-10 17:00:00', 16, 15, '2026-08-21', 'Khách hàng tham quan nhà máy, hỏi thông tin giá cáp quang treo cho dự án mới.', 0, 'Mới tiếp nhận', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0011', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0008'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0011'), 'Sửa chữa', 'Cao', 'Điện thoại', '2026-08-26 17:00:00', 16, 17, '2026-08-19', 'Lô hàng loa nén 25W giao cho đại lý có 5 cái không lên nguồn.', 1, 'Đang xử lý', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0012', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0009'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0012'), 'Bảo hành', 'Bình thường', 'Website', '2025-11-22 17:00:00', 16, 17, '2025-11-15', 'Cảm biến Object Locator không lên được kết nối LoRaWAN.', 1, 'Đã đóng', 'Cấu hình lại tần số EU868, hoạt động bình thường.', '2025-11-20 10:00:00');
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0013', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0010'), NULL, 'Bảo trì', 'Thấp', 'Email', '2026-09-01 17:00:00', 16, 15, '2026-08-15', 'Đề nghị bảo trì kho hàng đại lý định kỳ.', 0, 'Mới tiếp nhận', NULL, NULL);
INSERT INTO technicalrequests (ticket_code, enterprise_id, contract_id, ticket_type, priority, reception_channel, sla_deadline, assigned_technician_id, created_by, created_date, description, is_warranty, status, resolution_summary, resolved_at) VALUES
('TK-0014', (SELECT enterprise_id FROM enterprises WHERE enterprise_code = 'KH-0012'), (SELECT contract_id FROM contracts WHERE contract_code = 'HD-0014'), 'Sửa chữa', 'Cao', 'Điện thoại', '2026-02-08 17:00:00', 16, 17, '2026-02-01', 'Tủ phân phối điện DB1 bị chập, cần kiểm tra gấp trước khi bàn giao.', 1, 'Đã đóng', 'Đã sửa chữa, thay cầu dao, bàn giao lại nhưng khách hàng chưa thanh toán phí sửa chữa.', '2026-02-10 09:00:00');

-- ===== Thiết bị trong phiếu hỗ trợ (8) =====
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0001'), (SELECT product_id FROM products WHERE product_code = 'SP-0001'), (SELECT product_name FROM products WHERE product_code = 'SP-0001'), 'PDA10-2026-0088', 'Không sạc đầy, tự ngắt sau 70%');
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0002'), (SELECT product_id FROM products WHERE product_code = 'SP-0006'), (SELECT product_name FROM products WHERE product_code = 'SP-0006'), NULL, 'Bảo trì định kỳ, không có lỗi phát sinh');
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0005'), (SELECT product_id FROM products WHERE product_code = 'SP-0078'), (SELECT product_name FROM products WHERE product_code = 'SP-0078'), 'MI10-2026-0014', 'Mesh chập chờn, rớt kết nối nhiều lần trong ngày');
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0008'), (SELECT product_id FROM products WHERE product_code = 'SP-0035'), (SELECT product_name FROM products WHERE product_code = 'SP-0035'), 'PPRU-2026-0021', 'Ngắt điện đột ngột không rõ nguyên nhân');
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0009'), (SELECT product_id FROM products WHERE product_code = 'SP-0003'), (SELECT product_name FROM products WHERE product_code = 'SP-0003'), NULL, '3 bình phồng vỏ, không giữ được điện áp');
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0011'), (SELECT product_id FROM products WHERE product_code = 'SP-0086'), (SELECT product_name FROM products WHERE product_code = 'SP-0086'), NULL, '5 loa trong lô không lên nguồn khi cấp điện');
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0012'), (SELECT product_id FROM products WHERE product_code = 'SP-0074'), (SELECT product_name FROM products WHERE product_code = 'SP-0074'), 'OBL-2025-0033', 'Không kết nối được mạng LoRaWAN EU868');
INSERT INTO technicalrequestdevices (ticket_id, product_id, device_name, serial_number, fault_notes) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0014'), (SELECT product_id FROM products WHERE product_code = 'SP-0038'), (SELECT product_name FROM products WHERE product_code = 'SP-0038'), 'DB1-2025-0007', 'Chập cầu dao tổng, có mùi khét');

-- ===== Lịch sử trạng thái phiếu hỗ trợ =====
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0001'), '', 'Mới tiếp nhận', 17, '2026-08-20 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0001'), 'Mới tiếp nhận', 'Đang xử lý', 16, '2026-08-20 09:30:00', 'Kỹ thuật viên tiếp nhận xử lý.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0002'), '', 'Mới tiếp nhận', 17, '2026-07-15 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0002'), 'Mới tiếp nhận', 'Đang xử lý', 16, '2026-07-15 09:30:00', 'Kỹ thuật viên tiếp nhận xử lý.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0002'), 'Đang xử lý', 'Đã đóng', 16, '2026-07-20 16:30:00', 'Đã xử lý xong, đóng phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0003'), '', 'Mới tiếp nhận', 17, '2026-08-22 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0004'), '', 'Mới tiếp nhận', 15, '2026-08-10 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0004'), 'Mới tiếp nhận', 'Đang xử lý', 16, '2026-08-10 09:30:00', 'Kỹ thuật viên tiếp nhận xử lý.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0004'), 'Đang xử lý', 'Đã đóng', 16, '2026-08-12 14:00:00', 'Đã xử lý xong, đóng phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0005'), '', 'Mới tiếp nhận', 17, '2026-08-05 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0005'), 'Mới tiếp nhận', 'Đang xử lý', 16, '2026-08-05 09:30:00', 'Kỹ thuật viên tiếp nhận xử lý.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0006'), '', 'Mới tiếp nhận', 17, '2025-08-01 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0006'), 'Mới tiếp nhận', 'Đang xử lý', 16, '2025-08-01 09:30:00', 'Kỹ thuật viên tiếp nhận xử lý.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0006'), 'Đang xử lý', 'Đã đóng', 16, '2025-08-05 11:00:00', 'Đã xử lý xong, đóng phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0007'), '', 'Mới tiếp nhận', 15, '2026-08-18 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0008'), '', 'Mới tiếp nhận', 17, '2026-08-23 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0008'), 'Mới tiếp nhận', 'Đang xử lý', 16, '2026-08-23 09:30:00', 'Kỹ thuật viên tiếp nhận xử lý.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0009'), '', 'Mới tiếp nhận', 17, '2026-06-01 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0009'), 'Mới tiếp nhận', 'Đang xử lý', 16, '2026-06-01 09:30:00', 'Kỹ thuật viên tiếp nhận xử lý.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0009'), 'Đang xử lý', 'Đã đóng', 16, '2026-06-10 15:00:00', 'Đã xử lý xong, đóng phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0010'), '', 'Mới tiếp nhận', 15, '2026-08-21 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0011'), '', 'Mới tiếp nhận', 17, '2026-08-19 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0011'), 'Mới tiếp nhận', 'Đang xử lý', 16, '2026-08-19 09:30:00', 'Kỹ thuật viên tiếp nhận xử lý.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0012'), '', 'Mới tiếp nhận', 17, '2025-11-15 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0012'), 'Mới tiếp nhận', 'Đang xử lý', 16, '2025-11-15 09:30:00', 'Kỹ thuật viên tiếp nhận xử lý.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0012'), 'Đang xử lý', 'Đã đóng', 16, '2025-11-20 10:00:00', 'Đã xử lý xong, đóng phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0013'), '', 'Mới tiếp nhận', 15, '2026-08-15 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0014'), '', 'Mới tiếp nhận', 17, '2026-02-01 09:00:00', 'Tiếp nhận phiếu.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0014'), 'Mới tiếp nhận', 'Đang xử lý', 16, '2026-02-01 09:30:00', 'Kỹ thuật viên tiếp nhận xử lý.');
INSERT INTO technicalrequesthistory (ticket_id, from_status, to_status, changed_by, changed_at, internal_note) VALUES
((SELECT ticket_id FROM technicalrequests WHERE ticket_code = 'TK-0014'), 'Đang xử lý', 'Đã đóng', 16, '2026-02-10 09:00:00', 'Đã xử lý xong, đóng phiếu.');

