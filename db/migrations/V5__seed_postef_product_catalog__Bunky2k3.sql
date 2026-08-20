-- Replaces the generic placeholder categories from V4 with the real
-- product/category tree for 3 of POSTEF's top-level catalog sections
-- (Năng lượng tái tạo, Cáp quang & Phụ kiện, Hạ tầng viễn thông), sourced
-- from https://postef.com.vn/san-pham/ (public WooCommerce Store API).
-- Categories are looked up by name via subquery instead of hardcoded IDs
-- since auto-increment IDs aren't known ahead of insert.
--
-- ~15 entries returned by postef's own category API for "Cáp quang &
-- Phụ kiện" were skipped as not real products (e.g. "N-E", "L-N",
-- "Dòng tải định mức 32A", "Khả năng chịu được dòng xung tối đa 40kA") --
-- these read as individual spec/attribute bullet points that ended up
-- published as standalone posts on their site, not actual catalog items.

INSERT INTO schema_migrations (version) VALUES ('V5__seed_postef_product_catalog__Bunky2k3');

-- Đợt đầu chỉ dùng 5 danh mục chung chung để form thêm sản phẩm có gì đó
-- để chọn (xem V4) -- nay thay bằng cây danh mục thật lấy từ POSTEF.
DELETE FROM productcategories WHERE category_name IN
    ('Máy POS', 'Máy in hóa đơn', 'Đầu đọc thẻ', 'Phụ kiện POS', 'Phần mềm POS');

-- ===== Danh mục cấp 1 =====
INSERT INTO productcategories (category_name, parent_category_id, display_order) VALUES
('Năng lượng tái tạo', NULL, 10),
('Cáp quang & Phụ kiện', NULL, 20),
('Hạ tầng viễn thông', NULL, 30);

-- ===== Danh mục cấp 2 =====
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Ắc quy', category_id, 1 FROM productcategories WHERE category_name = 'Năng lượng tái tạo';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Hệ thống nguồn AC/DC, UPS', category_id, 2 FROM productcategories WHERE category_name = 'Năng lượng tái tạo';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Hệ thống điện mặt trời', category_id, 3 FROM productcategories WHERE category_name = 'Năng lượng tái tạo';

INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Sợi quang và cáp quang các loại', category_id, 1 FROM productcategories WHERE category_name = 'Cáp quang & Phụ kiện';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Hộp đấu nối quang, tủ phân phối quang', category_id, 2 FROM productcategories WHERE category_name = 'Cáp quang & Phụ kiện';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Phụ kiện kết nối quang', category_id, 3 FROM productcategories WHERE category_name = 'Cáp quang & Phụ kiện';

INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Thiết bị vô tuyến', category_id, 1 FROM productcategories WHERE category_name = 'Hạ tầng viễn thông';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Tủ outdoor, nguồn indoor cho trạm BTS', category_id, 2 FROM productcategories WHERE category_name = 'Hạ tầng viễn thông';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Thiết bị cắt lọc sét', category_id, 3 FROM productcategories WHERE category_name = 'Hạ tầng viễn thông';

-- ===== Danh mục cấp 3 =====
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Ăng ten cho trạm BTS', category_id, 1 FROM productcategories WHERE category_name = 'Thiết bị vô tuyến';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Hệ Thống Kích Sóng Điện Thoại Di Động Tòa Nhà', category_id, 2 FROM productcategories WHERE category_name = 'Thiết bị vô tuyến';

INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Tủ nguồn Outdoor', category_id, 1 FROM productcategories WHERE category_name = 'Tủ outdoor, nguồn indoor cho trạm BTS';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Tủ nguồn Indoor', category_id, 2 FROM productcategories WHERE category_name = 'Tủ outdoor, nguồn indoor cho trạm BTS';

-- ===== Sản phẩm: Năng lượng tái tạo (9) =====
INSERT INTO products (product_code, product_name, image_url, category_id) VALUES
('SP-0001', 'Ắc quy lưu động POSLI02/48100', 'https://postef.com.vn/wp-content/uploads/2026/08/screenshot_1786350000.png', (SELECT category_id FROM productcategories WHERE category_name = 'Ắc quy')),
('SP-0002', 'Ắc quy Gel', 'https://postef.com.vn/wp-content/uploads/2019/11/pngb.png', (SELECT category_id FROM productcategories WHERE category_name = 'Ắc quy')),
('SP-0003', 'Ắc quy acid chì kín', 'https://postef.com.vn/wp-content/uploads/2019/11/pnb.png', (SELECT category_id FROM productcategories WHERE category_name = 'Ắc quy')),
('SP-0004', 'Ắc quy lithium', 'https://postef.com.vn/wp-content/uploads/2019/11/ac-quy-lithium-4850.png', (SELECT category_id FROM productcategories WHERE category_name = 'Ắc quy')),
('SP-0005', 'Ắc quy BAE', 'https://postef.com.vn/wp-content/uploads/2019/11/bae.png', (SELECT category_id FROM productcategories WHERE category_name = 'Ắc quy')),
('SP-0006', 'Nguồn UNIPOWER', 'https://postef.com.vn/wp-content/uploads/2024/04/uni.png', (SELECT category_id FROM productcategories WHERE category_name = 'Hệ thống nguồn AC/DC, UPS')),
('SP-0007', 'Nguồn POSTEF', 'https://postef.com.vn/wp-content/uploads/2019/11/Chua-co-ten-300-x-300-px-600-x-600-px.png', (SELECT category_id FROM productcategories WHERE category_name = 'Hệ thống nguồn AC/DC, UPS')),
('SP-0008', 'UPS EATON', 'https://postef.com.vn/wp-content/uploads/2019/11/12113_Eaton-9395-a.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Hệ thống nguồn AC/DC, UPS')),
('SP-0009', 'Pin mặt trời', 'https://postef.com.vn/wp-content/uploads/2019/11/Small-no-fold-poly-with-wp-reg.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Hệ thống điện mặt trời'));

-- ===== Sản phẩm: Cáp quang & Phụ kiện (22) =====
INSERT INTO products (product_code, product_name, image_url, category_id) VALUES
('SP-0010', 'Cáp ADSS', 'https://postef.com.vn/wp-content/uploads/2024/05/Capture.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Sợi quang và cáp quang các loại')),
('SP-0011', 'Cáp quang kéo cống', 'https://postef.com.vn/wp-content/uploads/2024/04/Cap-quang-keo-cong.png', (SELECT category_id FROM productcategories WHERE category_name = 'Sợi quang và cáp quang các loại')),
('SP-0012', 'Cáp quang treo kim loại - phi kim loại', 'https://postef.com.vn/wp-content/uploads/2024/04/treo-kl-pkl.png', (SELECT category_id FROM productcategories WHERE category_name = 'Sợi quang và cáp quang các loại')),
('SP-0013', 'Cáp quang bọc chặt', 'https://postef.com.vn/wp-content/uploads/2024/04/boc-chat.png', (SELECT category_id FROM productcategories WHERE category_name = 'Sợi quang và cáp quang các loại')),
('SP-0014', 'Dây thuê bao quang đệm lỏng', 'https://postef.com.vn/wp-content/uploads/2024/04/DTB-dem-long.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Sợi quang và cáp quang các loại')),
('SP-0015', 'Sợi quang G657A1', 'https://postef.com.vn/wp-content/uploads/2024/04/soi-quang-g657a1.png', (SELECT category_id FROM productcategories WHERE category_name = 'Sợi quang và cáp quang các loại')),
('SP-0016', 'Dây thuê bao đệm chặt', 'https://postef.com.vn/wp-content/uploads/2024/04/dtb.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Sợi quang và cáp quang các loại')),
('SP-0017', 'Tủ phân phối quang OCC-SPLxxxFO', 'https://postef.com.vn/wp-content/uploads/2024/04/Tu-phan-phoi-quang-OCC-SPLxxxFO.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Hộp đấu nối quang, tủ phân phối quang')),
('SP-0018', 'Tủ quang-đồng', 'https://postef.com.vn/wp-content/uploads/2024/04/tu-quang-dong.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Hộp đấu nối quang, tủ phân phối quang')),
('SP-0019', 'Tủ đấu nối quang 192FO đặt bệ', 'https://postef.com.vn/wp-content/uploads/2024/04/tu-dau-noi-1920fo-dang-be.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Hộp đấu nối quang, tủ phân phối quang')),
('SP-0020', 'Hộp đấu nối POS-HOS-R và POS-SPL-R', 'https://postef.com.vn/wp-content/uploads/2024/04/Hop-dau-noi-POS-HOS-R-va-POS-SPL-R.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Hộp đấu nối quang, tủ phân phối quang')),
('SP-0021', 'Hộp đấu nối nhựa', 'https://postef.com.vn/wp-content/uploads/2024/04/Hop-dau-noi-nhua.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Hộp đấu nối quang, tủ phân phối quang')),
('SP-0022', 'Phụ kiện quang: Splitter, adapter, Connector, Pigtail...', 'https://postef.com.vn/wp-content/uploads/2024/04/phu-kien-quang.png', (SELECT category_id FROM productcategories WHERE category_name = 'Phụ kiện kết nối quang')),
('SP-0023', 'ODF gắn Rack 19 inch', 'https://postef.com.vn/wp-content/uploads/2024/04/ODF-GAN-RACK-19-INCH.png', (SELECT category_id FROM productcategories WHERE category_name = 'Phụ kiện kết nối quang')),
('SP-0024', 'Khung - nắp hầm cáp', 'https://postef.com.vn/wp-content/uploads/2024/04/Khung-–-nap-ham-cap.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Phụ kiện kết nối quang')),
('SP-0025', 'Ống nhựa PVC-U dùng cho tuyến cáp ngầm', 'https://postef.com.vn/wp-content/uploads/2024/04/Ong-nhua-PVC-U-dung-cho-tuyen-cap-ngam.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Phụ kiện kết nối quang')),
('SP-0026', 'Măng xông quang', 'https://postef.com.vn/wp-content/uploads/2024/04/MX96-1.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Phụ kiện kết nối quang')),
('SP-0027', 'Thiết bị cảnh báo BTS', 'https://postef.com.vn/wp-content/uploads/2019/11/bts-01.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Cáp quang & Phụ kiện')),
('SP-0028', 'Tải điện tử', 'https://postef.com.vn/wp-content/uploads/2019/11/tai-dien.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Cáp quang & Phụ kiện')),
('SP-0029', 'Máy phát điện KOHLER', 'https://postef.com.vn/wp-content/uploads/2019/11/kohler.png', (SELECT category_id FROM productcategories WHERE category_name = 'Cáp quang & Phụ kiện')),
('SP-0030', 'Máy phát điện YANMAR', 'https://postef.com.vn/wp-content/uploads/2019/11/yanmar.png', (SELECT category_id FROM productcategories WHERE category_name = 'Cáp quang & Phụ kiện')),
('SP-0031', 'Máy phát điện ASKA', 'https://postef.com.vn/wp-content/uploads/2019/11/aska.png', (SELECT category_id FROM productcategories WHERE category_name = 'Cáp quang & Phụ kiện'));

-- ===== Sản phẩm: Hạ tầng viễn thông (14) =====
INSERT INTO products (product_code, product_name, image_url, category_id) VALUES
('SP-0032', 'Giải pháp ăng ten ACE', 'https://postef.com.vn/wp-content/uploads/2024/04/ACE.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Ăng ten cho trạm BTS')),
('SP-0033', 'Giải pháp ăng ten POSTEF', 'https://postef.com.vn/wp-content/uploads/2019/11/atenna.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Ăng ten cho trạm BTS')),
('SP-0034', 'Helix Digital Headend Unit', 'https://postef.com.vn/wp-content/uploads/2024/04/helix2.png', (SELECT category_id FROM productcategories WHERE category_name = 'Hệ Thống Kích Sóng Điện Thoại Di Động Tòa Nhà')),
('SP-0035', 'Stratus High Power Digital DAS Remotes', 'https://postef.com.vn/wp-content/uploads/2024/04/stratus.png', (SELECT category_id FROM productcategories WHERE category_name = 'Hệ Thống Kích Sóng Điện Thoại Di Động Tòa Nhà')),
('SP-0036', 'Nimbus Low Power Active DAS Remotes', 'https://postef.com.vn/wp-content/uploads/2024/04/New-Nimbus1.png', (SELECT category_id FROM productcategories WHERE category_name = 'Hệ Thống Kích Sóng Điện Thoại Di Động Tòa Nhà')),
('SP-0037', 'Thiết bị Cắt lọc sét POSTEF', 'https://postef.com.vn/wp-content/uploads/2024/04/cls-400x231-1.png', (SELECT category_id FROM productcategories WHERE category_name = 'Thiết bị cắt lọc sét')),
('SP-0038', 'Tủ nguồn công suất cao ZXDU98T601', 'https://postef.com.vn/wp-content/uploads/2026/05/T601.png', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Indoor')),
('SP-0039', 'Tủ phân phối điện DB1', 'https://postef.com.vn/wp-content/uploads/2024/04/Asset-1.png', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Indoor')),
('SP-0040', 'Tủ phân phối nguồn AC/DC', 'https://postef.com.vn/wp-content/uploads/2024/04/tn-400x332-1.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Indoor')),
('SP-0041', 'Tủ nguồn PODS VN-M02', 'https://postef.com.vn/wp-content/uploads/2024/11/z6008762354576_67aebe04e7825601a98aad4a06b469a6.jpg', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Outdoor')),
('SP-0042', 'Bộ chuyển đổi nguồn AC-DC Cran', 'https://postef.com.vn/wp-content/uploads/2024/09/mb02.png', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Outdoor')),
('SP-0043', 'Tủ nguồn PS 60/15', 'https://postef.com.vn/wp-content/uploads/2019/11/60-ps.png', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Outdoor')),
('SP-0044', 'Tủ nguồn PP/RU', 'https://postef.com.vn/wp-content/uploads/2024/04/Tu-PP.png', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Outdoor')),
('SP-0045', 'Tủ nguồn Outdoor', 'https://postef.com.vn/wp-content/uploads/2019/11/tu-outdoor_300x300.png', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Outdoor'));
