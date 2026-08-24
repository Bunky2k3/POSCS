-- Replaces the old, partial demo product catalog (3 top-level categories, 45 placeholder products, some not real e.g. 'Máy phát điện KOHLER/YANMAR/ASKA') with the complete real catalog crawled from https://postef.com.vn/san-pham/: all 6 top-level categories, full category tree (incl. 3-level nesting), 89 real products with description + full technical specs, and their product images.
--
-- This migration intentionally DELETEs existing rows in products / productimages / productcatalogues / productcategories (and any contractproducts pointing at the old demo products, since those products are being removed) before reinserting -- it replaces seed/demo data wholesale rather than adding an incremental change. Do not run this against a database with real contract-product data you want to keep tied to the OLD demo products.

INSERT INTO schema_migrations (version) VALUES ('V7__reseed_postef_product_catalog__ndat2003');

-- Drop any contract line items pointing at products we're about to delete (products has no ON DELETE CASCADE from contractproducts).
DELETE cp FROM contractproducts cp JOIN products p ON cp.product_id = p.product_id;

-- productimages/productcatalogues cascade automatically via ON DELETE CASCADE.
DELETE FROM products;
DELETE FROM productcategories;

ALTER TABLE products AUTO_INCREMENT = 1;
ALTER TABLE productcategories AUTO_INCREMENT = 1;
ALTER TABLE productimages AUTO_INCREMENT = 1;

-- ===== Danh mục cấp 1 =====
INSERT INTO productcategories (category_name, parent_category_id, display_order) VALUES
('Năng lượng tái tạo', NULL, 10),
('Cáp quang & Phụ kiện', NULL, 20),
('Hạ tầng viễn thông', NULL, 30),
('Thiết bị bưu chính & CN hỗ trợ', NULL, 40),
('Điện thoại di động', NULL, 50),
('CNTT & IOT', NULL, 60);

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
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Băng tải tự động', category_id, 1 FROM productcategories WHERE category_name = 'Thiết bị bưu chính & CN hỗ trợ';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Smart Locker', category_id, 2 FROM productcategories WHERE category_name = 'Thiết bị bưu chính & CN hỗ trợ';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Thiết bị khai thác hành trình', category_id, 3 FROM productcategories WHERE category_name = 'Thiết bị bưu chính & CN hỗ trợ';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT '5G MiFI & 5G CPE (indoor & outdoor)', category_id, 1 FROM productcategories WHERE category_name = 'CNTT & IOT';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Giải pháp quản lý trạm BTS, tủ outdoor', category_id, 2 FROM productcategories WHERE category_name = 'CNTT & IOT';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'LoRa', category_id, 3 FROM productcategories WHERE category_name = 'CNTT & IOT';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Router wifi', category_id, 4 FROM productcategories WHERE category_name = 'CNTT & IOT';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Giải pháp giám sát cho phòng Lab bệnh viện', category_id, 5 FROM productcategories WHERE category_name = 'CNTT & IOT';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Loa IP', category_id, 6 FROM productcategories WHERE category_name = 'CNTT & IOT';

-- ===== Danh mục cấp 3 =====
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Ăng ten cho trạm BTS', category_id, 1 FROM productcategories WHERE category_name = 'Thiết bị vô tuyến';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Hệ Thống Kích Sóng Điện Thoại Di Động Tòa Nhà', category_id, 2 FROM productcategories WHERE category_name = 'Thiết bị vô tuyến';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Tủ nguồn Outdoor', category_id, 1 FROM productcategories WHERE category_name = 'Tủ outdoor, nguồn indoor cho trạm BTS';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Tủ nguồn Indoor', category_id, 2 FROM productcategories WHERE category_name = 'Tủ outdoor, nguồn indoor cho trạm BTS';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Gateways', category_id, 1 FROM productcategories WHERE category_name = 'LoRa';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Sensors', category_id, 2 FROM productcategories WHERE category_name = 'LoRa';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Audio box', category_id, 1 FROM productcategories WHERE category_name = 'Loa IP';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Transmitter box', category_id, 2 FROM productcategories WHERE category_name = 'Loa IP';
INSERT INTO productcategories (category_name, parent_category_id, display_order)
SELECT 'Loa truyền thanh', category_id, 3 FROM productcategories WHERE category_name = 'Loa IP';

-- ===== Sản phẩm (89, crawl từ postef.com.vn/san-pham/) =====
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0001', 'Ắc quy lithium', 'Ắc quy lithium viễn thông dùng công nghệ LiFePO4 với hệ thống quản lý thông minh, dùng cho viễn thông, UPS, năng lượng tái tạo. Đèn LED & màn hình LCD báo dung lượng/chế độ hoạt động; hoạt động tới 60°C không cần điều hòa; sạc nhanh 3-5h (so với 10-15h ắc quy chì); xả sâu tới 100% dung lượng ảnh hưởng tối thiểu tuổi thọ; thiết kế chuẩn rack 19"; vỏ hợp kim thép không gỉ; không cần bảo trì.

Thông số kỹ thuật: Công nghệ LiFePO4; Nhiệt độ hoạt động tới 60°C; Thời gian sạc 3-5h; Xả sâu tới 100%; Chuẩn thiết kế Rack 19"; Vật liệu vỏ thép không gỉ hợp kim. Model: PDA10-48100, PDA10-4850.', (SELECT category_id FROM productcategories WHERE category_name = 'Ắc quy'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0002', 'Ắc quy lưu động POSLI02/48100', 'Thiết bị lưu trữ năng lượng dùng công nghệ Lithium LiFePO4, hệ thống giám sát và bảo vệ tin cậy, tuổi thọ cao, sạc nhanh, dễ lắp đặt/vận hành. Thiết kế cầm tay dạng vali gọn nhẹ, triển khai khẩn cấp tại hiện trường, tích hợp nguồn sạc nhanh để hồi phục ắc quy. Chống sốc, chống nước chuẩn IP55.

Thông số kỹ thuật: Dung lượng 100Ah; Điện áp 48VDC; Công nghệ Lithium LiFePO4; Dạng vali cầm tay; Chuẩn bảo vệ IP55.', (SELECT category_id FROM productcategories WHERE category_name = 'Ắc quy'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0003', 'Ắc quy Gel', 'Dòng ắc quy GEL cho ứng dụng nạp/xả liên tục trong môi trường khắc nghiệt. Đạt chuẩn IEC 60896-21, ISO9001, ISO14001, UL, CE.

Thông số kỹ thuật: Công nghệ GEL VRLA; Dung lượng 300-500Ah; Điện áp 2V-12V; Tuổi thọ 12 năm (20°C, chế độ float); Tự xả ≤3%/tháng; Vỏ nhựa ABS chịu lực cao, chống cháy UL94-VO; Ren cực M8; Nhiệt độ hoạt động -20°C đến +50°C; thiết kế gọn, điện trở trong thấp, xả dòng lớn ổn định. Model: PNGB 2V300Ah, PNGB 2V500Ah.', (SELECT category_id FROM productcategories WHERE category_name = 'Ắc quy'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0004', 'Ắc quy acid chì kín', 'Ắc quy acid-chì kín khí, không cần bảo dưỡng, công nghệ AGM, thiết kế nhỏ gọn, an toàn, tích trữ điện năng cao, chịu dòng phóng sâu, tuổi thọ dài.

Thông số kỹ thuật: Công nghệ AGM VRLA; Dung lượng 100-500Ah; Điện áp 2V (300-500Ah)/12V (100-200Ah); Tuổi thọ 10 năm (20°C, standby); Tự xả ≤3%/tháng; Nhiệt độ hoạt động -20°C đến +50°C (tối ưu 25°C±3°C); Vỏ nhựa ABS chống cháy UL94-VO; Ren cực 3/8''''; Chuẩn IEC 60896-21, KS C 8515:1997, ISO9001, ISO14001, UL, CE.', (SELECT category_id FROM productcategories WHERE category_name = 'Ắc quy'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0005', 'Ắc quy BAE', 'POSTEF là nhà phân phối chính thức BAE, cung cấp ắc quy cho các trạm host lớn và trạm BTS. Tuổi thọ cao, dung lượng lớn.

Thông số kỹ thuật: Công nghệ GEL VRLA; Dung lượng 100-3500Ah; Tuổi thọ >12 năm; Nhiệt độ hoạt động -20°C đến 45°C; Khả năng phục hồi sau xả sâu tốt.', (SELECT category_id FROM productcategories WHERE category_name = 'Ắc quy'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0006', 'Nguồn POSTEF', 'Hệ thống nguồn cho các trạm quy mô vừa/lớn, dùng cho trạm MSC, HOT, trạm phát sóng, trạm BTS của các mạng viễn thông lớn (Vinaphone, Mobifone, Viettel, Sfone, Gtel).

Thông số kỹ thuật: Model ZXDU68B301 V5.0; Lắp rack chuẩn 19 inch; Mật độ dòng ≤3A/mm²; Số module chỉnh lưu tối đa 5; Công suất tối đa 5kW; Dòng ra tối đa 300A; Điện áp vào 1 pha 220VAC / 3 pha 380VAC; Tần số 50-60Hz; Cổng kết nối RJ45, RS232, RS485, USB.', (SELECT category_id FROM productcategories WHERE category_name = 'Hệ thống nguồn AC/DC, UPS'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0007', 'Nguồn UNIPOWER', 'UNIPOWER thuộc top 5 nhà cung cấp giải pháp chuyển đổi nguồn điện chuẩn (SPS) toàn cầu. POSTEF là 1 trong 200 nhà phân phối chính thức. Hiệu suất chỉnh lưu >96%, giám sát/điều khiển từ xa, bộ điều khiển hot-swap, kết nối Ethernet SNMPv3, đèn LED cảnh báo, màn hình LCD/Touchpad, lắp đặt đơn giản.

Thông số kỹ thuật: Hiệu suất >96%; Vị trí top 5 thế giới (theo Micro Technology Consultant, 3/2017); Giám sát từ xa; Bộ điều khiển hot-swap; Giao diện mạng Ethernet SNMPv3; Cảnh báo đèn LED; Màn hình LCD/Touchpad. Model: UNIPOWER GDN.S.48.M24, GDN.S.48.MS32, GDN.S.48.MS31, GDN.S.48.M27, GDN.S.48.M26.', (SELECT category_id FROM productcategories WHERE category_name = 'Hệ thống nguồn AC/DC, UPS'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0008', 'UPS EATON', 'POSTEF là nhà phân phối chính thức EATON tại Việt Nam, cung cấp giải pháp UPS lưu điện cho trạm BTS, host viễn thông lớn, hệ thống nguồn data center.

Thông số kỹ thuật: Dải công suất 22-1100 kVA; Cấu hình giám sát N+1; Truy cập từ xa TCP/IP, web browser, SNMP; Đạt chuẩn an toàn điện quốc tế. Tài liệu: EATON UPS 9395 (catalog chi tiết trên Google Drive).', (SELECT category_id FROM productcategories WHERE category_name = 'Hệ thống nguồn AC/DC, UPS'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0009', 'Pin mặt trời', 'POSTEF hợp tác với H-enterprise Solutions phát triển pin năng lượng mặt trời, hiệu suất cao, bền trong nhiều điều kiện môi trường.

Thông số kỹ thuật: Hiệu suất tế bào >16.50% (ô 156mm x 156mm); Hiệu suất ánh sáng yếu >95%; Chịu tải tuyết >5400Pa; Chịu gió >3800Pa; Chống ăn mòn muối/amoniac/cát; Dung sai công suất sạc tới 5W. Model: ESP 320/315/310/305/300.', (SELECT category_id FROM productcategories WHERE category_name = 'Hệ thống điện mặt trời'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0010', 'Cáp ADSS', 'Cáp quang treo ngoài trời, thiết kế ống đệm lỏng có chất độn, chứa 4-96 sợi quang. Sợi đơn mode chiết suất bậc, chất lượng cao theo ITU-T G.652.D và TCVN 8665:2011.

Thông số kỹ thuật: Ống đệm PBT, màu theo TIA/EIA-598, 12 sợi/ống, đường kính ngoài ≥2.0mm; Que độn nhựa PE; Phần tử gia cường trung tâm FRP đường kính ≥2.0mm; Vỏ trong HDPE dày ≥1.5mm; Lớp gia cường ngoài sợi Aramid không dẫn điện; Dây xé vỏ ngoài polyester.', (SELECT category_id FROM productcategories WHERE category_name = 'Sợi quang và cáp quang các loại'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0011', 'Cáp quang bọc chặt', 'Cáp bọc chặt (tight-buffer) cho ứng dụng trong nhà/ngoài trời, đơn hoặc đa sợi (tối đa 4 sợi). Linh hoạt, bán kính uốn nhỏ, lắp đặt dễ, phù hợp lắp đặt trong nhà. Chịu môi trường, tác động cơ học và nhiệt độ khắc nghiệt.

Thông số kỹ thuật: Số sợi 1FO/2FO/4FO; Vỏ ngoài LZZH, PVC, LLDPE; Lớp đệm đơn/đa lớp bọc sát sợi; Bóc lớp đệm dài 10-25mm dễ hàn nối; Chuẩn IEC 60794-1-2, IEC 60793-1.', (SELECT category_id FROM productcategories WHERE category_name = 'Sợi quang và cáp quang các loại'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0012', 'Cáp quang kéo cống', 'Cáp quang kéo cống dùng lắp đặt trong ống ngầm, thiết kế ống đệm lỏng có chất độn bảo vệ, có loại kim loại và phi kim loại. Sợi đơn mode chiết suất bậc chất lượng cao (ITU-T G.652.D, TCVN 8665:2011). Tuổi thọ ≥15 năm, đạt IEC 60793/60794.

Thông số kỹ thuật: Số sợi 6-144; Bện SZ quanh phần tử gia cường trung tâm FRP; Chống nước bằng băng chặn nước; Vỏ ngoài HDPE đen chịu điện áp cao; Cấu hình 6-24FO, 48FO, 6-96FO, 8-96FO, loại chôn trực tiếp 12/48/96FO.', (SELECT category_id FROM productcategories WHERE category_name = 'Sợi quang và cáp quang các loại'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0013', 'Cáp quang treo kim loại – phi kim loại', 'Cáp quang treo ngoài trời, vỏ kim loại hoặc phi kim loại, dung lượng tới 144 sợi. Sợi đơn mode chuẩn ITU-T G.652.D, TCVN 8665:2011, tuổi thọ >15 năm, đạt IEC 60794-1-2, IEC 60793-1.

Thông số kỹ thuật: Số sợi 6-144, 2-12FO/ống; Chất độn ống Thixotropic Jelly; Gia cường lõi FRP; Chống nước bằng sợi/băng chặn nước; Bện lõi SZ đảo chiều; Sợi gia cường Aramid; Vỏ cáp HDPE đen dày 1.5-2mm±0.1mm; Dây treo thép 7 sợi bện đường kính 1-1.2mm, bọc HDPE 1.0mm±0.1mm.', (SELECT category_id FROM productcategories WHERE category_name = 'Sợi quang và cáp quang các loại'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0014', 'Dây thuê bao đệm chặt', 'Dây thuê bao bọc chặt cho ứng dụng trong nhà/ngoài trời, cấu hình 1FO/2FO. Bán kính uốn nhỏ, dễ lắp đặt, phù hợp công trình xây dựng. Sợi đơn mode chiết suất bậc chất lượng cao (ITU-T G.657.A1, TCVN 8696:2011). Tuổi thọ >15 năm.

Thông số kỹ thuật: Số sợi 1FO/2FO/4FO; Vỏ ngoài LSZH hoặc LLDPE đen; Ống đệm PBT có mã màu; Gia cường 2 sợi thép ≥0.4mm hai bên; Dây treo cáp thép bện ≥0.33mm×7 sợi.', (SELECT category_id FROM productcategories WHERE category_name = 'Sợi quang và cáp quang các loại'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0015', 'Dây thuê bao quang đệm lỏng', 'Cáp quang thuê bao ngoài trời thiết kế đệm lỏng có chất độn, đơn hoặc đa sợi. Sợi đơn mode chiết suất bậc chất lượng cao, đạt ITU-T G.652.D hoặc G.657.A1.

Thông số kỹ thuật: Số sợi 1FO/2FO/4FO; Vỏ ngoài nhựa HDPE đen; Ống đệm lỏng PBT có mã màu; Chất độn không độc, chống mốc, không dẫn điện, giãn nở nhiệt thấp; Lớp phủ chính chống UV; Vỏ ngoài PE/HDPE có carbon black chống UV, phụ gia chống oxy hóa, chống mốc.', (SELECT category_id FROM productcategories WHERE category_name = 'Sợi quang và cáp quang các loại'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0016', 'Sợi quang G657A1', 'Sợi quang G.657A1 do POSTEF phát triển sau thành công với sợi đơn mode G.652D. Khác biệt chính là bán kính uốn: G.652D cần tối thiểu 30mm, G.657A1 hoạt động ổn định ở bán kính chỉ 10mm mà không ảnh hưởng truyền dẫn. Ít nhạy với uốn cong, dùng cho mạng băng rộng văn phòng, chung cư, hộ gia đình. Tương thích đầu nối LC, SC, MU, E2000 chuẩn UPC/APC.

Thông số kỹ thuật: Chuẩn ITU-T G.657A1; Loại đơn mode; Bán kính uốn tối thiểu 10mm; Đầu nối tương thích LC, SC, MU, E2000; Chuẩn tiếp xúc UPC, APC.', (SELECT category_id FROM productcategories WHERE category_name = 'Sợi quang và cáp quang các loại'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0017', 'Hộp đấu nối nhựa', 'Hộp đấu nối/phân phối quang vỏ nhựa cho lắp đặt trong nhà/ngoài trời. Nhựa ép có gioăng cửa kín bụi/nước chuẩn IP54. Bản ngoài trời có nắp chống đọng sương. Vỏ ABS gia cường sợi thủy tinh, chống cháy.

Thông số kỹ thuật: Vật liệu ABS gia cường sợi thủy tinh; Chuẩn IP54; Lắp treo tường/cột; Có khóa bảo vệ; Cấu trúc module (khối splitter, tấm adapter, khay hàn); Bán kính uốn tối thiểu ≥30mm; Mở góc 180°; Màu xám nhạt.', (SELECT category_id FROM productcategories WHERE category_name = 'Hộp đấu nối quang, tủ phân phối quang'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0018', 'Hộp đấu nối POS-HOS-R và POS-SPL-R', 'Hộp đấu nối cứng vững, không oxy hóa, thiết kế gọn cho không gian hẹp. Cổng cáp vào/ra linh hoạt hai bên. Khóa kiểu công nghiệp có nắp chống bụi/nước. Dùng trong nhà/ngoài trời, tùy chỉnh theo yêu cầu khách hàng.

Thông số kỹ thuật: Bán kính uốn tối thiểu ≥30mm; Nhiệt độ hoạt động -10°C đến 65°C; Độ ẩm tối đa 95%; Tuổi thọ cắm/rút adapter ≥500 lần; Chuẩn IP54; Vật liệu thép đặc biệt dày ≥1.2mm, sơn tĩnh điện; Lắp cột hoặc tường; Chuẩn IEC 61300 series, IEC 60068 series.', (SELECT category_id FROM productcategories WHERE category_name = 'Hộp đấu nối quang, tủ phân phối quang'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0019', 'Tủ đấu nối quang 192FO đặt bệ', 'Tủ phân phối quang ngoài trời đặt bệ cho ứng dụng FTTx-GPON. Model POS-TNS-192: dung lượng adapter 192, hàn nối 384FO.

Thông số kỹ thuật: Tỉ lệ splitter 1:2/4/8/16/32/64; Số splitter ngoài 12-24 (loại 1:8); Cổng cáp 8 cổng (144FO/cổng); Adapter tối đa 192±24; Kích thước (D×R×C) 1185×500×290mm; Chuẩn IEC 61300, IEC 60068.', (SELECT category_id FROM productcategories WHERE category_name = 'Hộp đấu nối quang, tủ phân phối quang'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0020', 'Tủ phân phối quang OCC-SPLxxxFO', 'Tủ phân phối quang vỏ kim loại công nghệ CNC, kết cấu chắc chắn, gioăng cửa liền mạch chống bụi/nước IP54. Vỏ thép dày 1.4mm sơn tĩnh điện. Cửa trước mở 180°, khóa chuyên dụng chống bụi. Gồm 5 khối: cáp vào/ra, hàn nối quang, splitter, cáp thuê bao ra, định tuyến cáp. Bán kính uốn tối thiểu ≥30mm.

Thông số kỹ thuật: Dung lượng 144-288FO; Đầu nối SC/APC hoặc SC/UPC; Suy hao chèn ≤0.30dB; Suy hao phản xạ SC/APC ≥60dB, SC/UPC ≥55dB; Ổn định sau 500 lần cắm/rút ≤0.2dB; IP54; Hút ẩm sau ngâm nước 24h ≤0.2%; Nhiệt độ hoạt động -10°C đến 65°C; Độ ẩm ≤98%; Dung lượng đầu nối 8-96FO.', (SELECT category_id FROM productcategories WHERE category_name = 'Hộp đấu nối quang, tủ phân phối quang'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0021', 'Tủ quang-đồng', 'Hệ thống tủ đấu nối kết hợp cáp quang và cáp đồng gồm 3 loại: Tủ đấu nối POS-EO-24SC (kết hợp mạng cáp đồng & quang, lắp ngoài trời); Tủ đấu nối quang 192FO đặt bệ; Tủ cáp đồng KP dung lượng 200/300, 600, 1200, 1600 đôi.

Thông số kỹ thuật: Chống mưa, ngập, bụi; Vật liệu thép CT3 dày 1.5mm, sơn tĩnh điện màu xám nhạt; Bán kính uốn ≥30mm; Cửa mở 180° có khóa và tay nắm; Tủ cáp đồng vỏ composite/ABS gia cường sợi thủy tinh (SCM/BMC), chịu nhiệt 100°C/5h, đạt IEC 61300-2-12 Method B.', (SELECT category_id FROM productcategories WHERE category_name = 'Hộp đấu nối quang, tủ phân phối quang'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0022', 'Khung – nắp hầm cáp', 'Khung, nắp hầm cáp các loại do POSTEF sản xuất tại Việt Nam, đạt chuẩn ngành viễn thông.

Thông số kỹ thuật: Thép Pomina hoặc Hòa Phát, không dùng sắt composite; Chống gỉ toàn bộ khung/nắp; Hàn kiểu vảy cá liên tục; Mác bê tông 250 hoặc 300; Đá 1x2cm, cát vàng; Xi măng PC30; Vữa xi măng-cát ≥150 mác; Bảo hành không gỉ trong thời hạn.', (SELECT category_id FROM productcategories WHERE category_name = 'Phụ kiện kết nối quang'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0023', 'Măng xông quang', 'Măng xông (hộp nối) quang dùng chôn trực tiếp, đặt ống/hầm/hào hoặc treo, chịu nhiều điều kiện địa lý/khí hậu. Bảo vệ mối hàn khỏi tác động bên ngoài. Vỏ nhựa ABS nguyên sinh chống UV, chống côn trùng cắn, chống ăn mòn/hóa chất. Có thể đóng/mở nhiều lần để bảo trì. Có tiếp địa cho phần kim loại.

Thông số kỹ thuật: Model MX 12-24 và MX 48-96; Dung lượng 6-96 sợi; Vật liệu vỏ ABS nguyên sinh; Chống UV; Lắp treo, cột hoặc chôn ngầm; Chứng nhận ISO 9001:2015 (BVC - Anh).', (SELECT category_id FROM productcategories WHERE category_name = 'Phụ kiện kết nối quang'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0024', 'ODF gắn rack 19 inch', 'ODF trong nhà dung lượng 12FO-96FO, kết cấu "trượt-xoay", kích thước tối ưu lắp rack chuẩn 19 inch.

Thông số kỹ thuật: Dung lượng 12-96FO; Vật liệu thép sơn tĩnh điện dày ≥1mm; Thiết kế module, khay tháo lắp dễ thay thế; Khay hàn 12-24 sợi/khay; Đầu nối FC hoặc SC (tùy chọn); Bán kính uốn tối thiểu ≥30mm; Nhiệt độ hoạt động -10°C đến +65°C; Độ ẩm ≤95%.', (SELECT category_id FROM productcategories WHERE category_name = 'Phụ kiện kết nối quang'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0025', 'Ống nhựa PVC-U dùng cho tuyến cáp ngầm', 'Ống nhựa PVC-U nguyên sinh không phụ gia độc hại, dùng truyền tải cáp quang/viễn thông, chịu nén và va đập cao, bền trong môi trường khắc nghiệt.

Thông số kỹ thuật: Vật liệu PVC-U nguyên sinh; Phụ gia chống UV, chống oxy hóa, chống côn trùng, tạo màu; Màu vàng/cam không phai; Chịu nén ngoài cao; Chống hóa chất/dầu/muối; Chống chuột, mối, kiến; Tuổi thọ >50 năm; Chuẩn ISO 6144:2003, ISO 7434:2004, ISO 6148:2007, TCN 6147:2003, TCN 8699:2011.', (SELECT category_id FROM productcategories WHERE category_name = 'Phụ kiện kết nối quang'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0026', 'Phụ kiện quang: Splitter, adapter, Connector, Pigtail…', 'Phụ kiện quang gồm splitter, adapter, connector, pigtail với đầu nối ST, PF, SC, LC. Công nghệ đánh bóng Ultra (UPC), Angel (APC) hoặc theo yêu cầu, dùng máy SEIKO chuyên dụng.

Thông số kỹ thuật: Đầu nối ST, PF, SC, LC; Đánh bóng UPC/APC/tùy chỉnh; Đạt chuẩn RoHS (hạn chế Pb, Cd, Hg, Cr6+, PBB, PBDE); Độ ổn định cao; Suy hao thấp.', (SELECT category_id FROM productcategories WHERE category_name = 'Phụ kiện kết nối quang'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0027', 'Giải pháp ăng ten ACE', 'POSTEF là nhà phân phối chính thức ăng ten công nghệ ACE cho trạm BTS tại Việt Nam, dải tần 1710MHz-2690MHz.

Thông số kỹ thuật: Nhiều băng tần; Góc nghiêng nhỏ; Độ lợi cao; Lắp đặt dễ, an toàn, bảo trì thuận tiện; Tùy chỉnh theo vị trí/khu vực lắp đặt. Model: XDWL-17-65V-VT, XQLHHH-16(17)-65V-iVT, XXDWH-17-65V-VT-DM-R2, XXXDWH-17-65V-iVT.', (SELECT category_id FROM productcategories WHERE category_name = 'Ăng ten cho trạm BTS'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0028', 'Giải pháp ăng ten POSTEF', 'POSTEF cung cấp thiết bị trạm BTS gồm nguồn, ắc quy, chống sét, giám sát và ăng ten cho mạng 2G/3G/4G. Cũng cung cấp giải pháp cột ăng ten ngụy trang để bảo vệ thiết bị treo và tăng tính thẩm mỹ.

Thông số kỹ thuật: Nhiều băng tần; Góc nghiêng nhỏ; Độ lợi cao; Lắp đặt dễ, an toàn cao; Bảo trì đơn giản; Tùy chỉnh theo vị trí/khu vực. Catalogue: ANTENA 4G POS_1710_2690, ANTENA 4G POS_1710_2170, ANTENA NGỤY TRANG.', (SELECT category_id FROM productcategories WHERE category_name = 'Ăng ten cho trạm BTS'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0029', 'Helix Digital Headend Unit', 'Thiết bị headend số trong hệ thống Maven DAS, hoạt động như O-RAN gateway tương thích giải pháp vBBU, cấp nguồn cho Maven NIMBUS RU qua POE, cáp hybrid hoặc quang.

Thông số kỹ thuật: Hỗ trợ O-RAN & 5G; Công suất tiêu thụ 180W; 16 cổng Ethernet CAT6a (POE++); Kết nối thay thế 8x SFP28 hoặc 16x SFP+.', (SELECT category_id FROM productcategories WHERE category_name = 'Hệ Thống Kích Sóng Điện Thoại Di Động Tòa Nhà'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0030', 'Nimbus Low Power Active DAS Remotes', 'Thiết bị tích hợp băng tần không dây truyền thống và 5G vào hệ thống DAS dung lượng cao. Linh hoạt cho hệ thống DAS 4G/5G đầy đủ, phủ sóng 5G overlay, hoặc hotspot 5G. Kích thước gọn.

Thông số kỹ thuật: Hỗ trợ đa nhà mạng trong 1 thiết bị; Dung lượng phổ tới 1600MHz/thiết bị; Tương thích cáp quang, hybrid, POE; Không quạt (không bảo trì); Hỗ trợ MIMO/SISO trong 1 hộp; Công suất ra +23dBm/băng tần; Ăng ten tích hợp hoặc ngoài; 4 băng tần/hộp.', (SELECT category_id FROM productcategories WHERE category_name = 'Hệ Thống Kích Sóng Điện Thoại Di Động Tòa Nhà'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0031', 'Stratus High Power Digital DAS Remotes', 'Giải pháp phủ sóng tối ưu cho khu vực công suất cao, bền vững. Lựa chọn hàng đầu cho nhu cầu phủ sóng kết hợp trong tòa nhà. Hỗ trợ liền mạch mọi băng tần di động từ 2G đến 5G ở cả cấu hình SISO và MIMO.

Thông số kỹ thuật: Tối đa 5 băng tần; Hỗ trợ 4x4 MIMO; Không quạt (không bảo trì); Công suất tiêu thụ tối đa 400W; Chuẩn IP66.', (SELECT category_id FROM productcategories WHERE category_name = 'Hệ Thống Kích Sóng Điện Thoại Di Động Tòa Nhà'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0032', 'Bộ chuyển đổi nguồn AC-DC Cran', 'Thiết bị chuyển đổi nguồn lắp ngoài trời (treo tường hoặc cột), thiết kế công nghiệp, nhẹ, tiết kiệm không gian. Chuyển AC 220V sang DC -48V, có khoang ắc quy Lithium, cấp nguồn liên tục cho tải, hỗ trợ hot-swap module chỉnh lưu và điều khiển.

Thông số kỹ thuật: Lắp ngoài trời (treo tường/cột); Điện áp vào AC 220V; Điện áp ra DC -48V; Loại ắc quy Lithium; Module chỉnh lưu/điều khiển hot-swap.', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Outdoor'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0033', 'Tủ nguồn Outdoor', 'Tủ điện ngoài trời PODS VN, gọn nhẹ, công nghệ tiên tiến. Tản nhiệt linh hoạt bằng quạt hoặc điều hòa tùy điều kiện môi trường. Đáp ứng mọi yêu cầu khách hàng.

Thông số kỹ thuật: Lắp ngoài trời; Tiết kiệm 60% diện tích sàn; Giảm chi phí xây dựng; Cấu hình linh hoạt, mở rộng được; Khoang ắc quy tách biệt chống hơi axit; Lắp đặt/vận hành thuận tiện.', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Outdoor'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0034', 'Tủ nguồn PODS VN-M02', 'Tủ điện lắp ngoài trời, gọn nhẹ, công nghệ tiên tiến, tản nhiệt linh hoạt bằng quạt/điều hòa.

Thông số kỹ thuật: Model PODS VN-M02; Lắp ngoài trời; Tiết kiệm 60% diện tích; Tản nhiệt động (quạt/điều hòa); Khoang ắc quy cách ly chống hơi axit; Cấu hình mở rộng/nâng cấp linh hoạt.', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Outdoor'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0035', 'Tủ nguồn PP/RU', 'Tủ nguồn ngoài trời cho viễn thông, ra -48VDC/62.5A (1 chỉnh lưu 48V/3000W), mở rộng tới 125A (2 chỉnh lưu). Bố trí khoa học, chia khối riêng dễ vận hành/bảo trì. Dây/cáp đi ngầm bên trong đảm bảo gọn gàng, an toàn cháy nổ. Lắp tường hoặc cột.

Thông số kỹ thuật: Kích thước (C×R×S) ≤525×325×190mm; Trọng lượng ≤16kg; Khung/vỏ thép mạ kẽm; Điện áp vào AC 1 pha 220V/50Hz; Dòng vào định mức ≤18A/module; Điện áp ra DC -48VDC; Dòng ra định mức 50A/module; Mở rộng tới 125A (2 module).', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Outdoor'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0036', 'Tủ nguồn PS 60/15', 'Tủ nguồn ngoài trời cho trạm BTS. Chống sét lan truyền đầu vào/ra, ổn áp bằng công nghệ switching và biến áp xuyến, hiệu suất cao, ít ồn, tản nhiệt tự nhiên phù hợp lắp ngoài trời.

Thông số kỹ thuật: Lắp ngoài trời, tản nhiệt tự nhiên; Dạng sóng ra hình sin; Ổn áp bằng switching & biến áp xuyến; Bảo vệ ngắn mạch bằng cầu chì đầu ra; Chống sét lan truyền vào/ra; Độ ồn thấp; Hiệu suất cao.', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Outdoor'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0037', 'Tủ nguồn công suất cao ZXDU98T601', 'Sản phẩm hợp tác giữa POSTEF (Việt Nam) và ZTE Corporation (Trung Quốc), lắp ráp tại nhà máy POSTEF. Tủ nguồn indoor kết hợp công nghệ quản lý nguồn tiên tiến và chất lượng lắp ráp Việt Nam.

Thông số kỹ thuật: Kích thước (R×C×S) 600×1600×400mm; Tối đa 12 module chỉnh lưu; Điện áp vào 75-300VAC; Hiệu suất cao, tiêu thụ thấp ở chế độ ngủ; Mật độ công suất cao, tiết kiệm không gian; Quản lý hệ thống từ xa; Sao lưu sự kiện & nâng cấp phần mềm qua cổng USB; Phân cấp quyền người dùng; Công nghệ số ổn định cao; Đạt chuẩn quốc tế, TCVN.', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Indoor'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0038', 'Tủ phân phối điện DB1', 'Tủ phân phối điện DB1 do POSTEF sản xuất, dùng cho trạm viễn thông trong nhà. Sử dụng rộng rãi trên mạng lưới VNPT các tỉnh thành, đáp ứng YCKT về CSHT đài trạm của Tập đoàn.

Thông số kỹ thuật: Kích thước 300x400x150mm; Lắp treo tường, cửa mở phía trước; Khung thép sơn tĩnh điện; Có tấm/giá lắp thiết bị bên trong; Cổng cáp vào/ra trên và dưới; Chuẩn IP42.', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Indoor'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0039', 'Tủ phân phối nguồn AC/DC', 'Tủ đạt chuẩn lắp rack 19", chuẩn chống nước/bụi IP55, đạt ANSI/EIA RS-310-D, IEC297-2, DIN41494. POSTEF tùy biến theo yêu cầu cụ thể.

Thông số kỹ thuật: Thiết kế module, tháo rời dễ vận chuyển/lắp đặt; Cấu hình linh hoạt nâng cấp/mở rộng; Bố trí điện/thiết bị/ắc quy hợp lý, tách biệt; Khoang ắc quy có vách ngăn chống hơi axit; Giá lắp chuẩn rack chắc chắn; Bố trí thiết bị linh hoạt theo yêu cầu; Tản nhiệt tùy chọn (thông gió/trao đổi nhiệt tiết kiệm); Sơn tĩnh điện đen hoặc xám nhạt; Chuẩn IP55. Model liên quan: PP-AC-600A, PP-DC-600A, PP-DC/1000, PP-DC/1200.', (SELECT category_id FROM productcategories WHERE category_name = 'Tủ nguồn Indoor'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0040', 'Thiết bị cắt lọc sét', 'Bảo vệ thiết bị điện/điện tử/viễn thông khỏi sét qua thiết kế 3 tầng: cắt sét sơ cấp (công nghệ MultyMOV), tầng lọc (mạch thông thấp), tầng chống sét thứ cấp.

Thông số kỹ thuật: Công nghệ MultyMOV; 3 tầng bảo vệ độc lập; Bộ lọc thông thấp kết hợp cuộn cảm & tụ điện; Bảo vệ đường nguồn cho hạ tầng viễn thông. Model: IPS34-200-200A, LPS 12/63/130, POT_SSDDC48-40kA, SSD 34-200kA và các phiên bản AC.', (SELECT category_id FROM productcategories WHERE category_name = 'Thiết bị cắt lọc sét'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0041', 'Băng tải di động con lăn', 'Băng tải con lăn vận chuyển hàng từ nhẹ đến rất nặng, môi trường thường hoặc ăn mòn/bụi. Gồm khung băng tải, cơ cấu căng, chắn mép, con lăn. Phù hợp hàng có đáy phẳng cứng (thùng, carton, pallet), chi phí đầu tư/vận hành/bảo trì thấp.

Thông số kỹ thuật: Hệ số ma sát thấp; Chống nước/bụi, kín khít; Ống thép độ chính xác cao, rung/ồn thấp; Chịu tải tốt, tuổi thọ cao; Vận hành ổn định; Phù hợp hàng nhẹ, trung, nặng.', (SELECT category_id FROM productcategories WHERE category_name = 'Băng tải tự động'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0042', 'Băng tải di động dây đai', 'Băng tải dây đai chạy điện, di động, vận chuyển bưu kiện/hàng nặng tại trung tâm khai thác bưu chính. Nâng cao năng suất, giảm thao tác thủ công, tăng tính linh hoạt di chuyển.

Thông số kỹ thuật: Vận hành 2 chiều (bốc/dỡ); Nhiệt độ hoạt động dưới 40°C; Nhiệt độ vật liệu tối đa 50°C; Điều chỉnh độ cao bằng tời điện hoặc thủy lực; Bánh xe di chuyển linh hoạt; Motor giảm tốc; Kết cấu gọn nhẹ. Thành phần: khung, con lăn chủ động/bị động, con lăn đỡ, cơ cấu căng đai, chống lệch, con lăn dẫn hướng, motor giảm tốc, dây đai.', (SELECT category_id FROM productcategories WHERE category_name = 'Băng tải tự động'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0043', 'Xe đẩy bàn', 'Xe đẩy bàn tải trọng 300kg, vận chuyển hàng cự ly ngắn, kết cấu đơn giản, đẩy tay thủ công.

Thông số kỹ thuật: Tải trọng 300kg; Chiều cao nâng tối đa 180mm; Kích thước bàn 740x480mm; Bánh xe lõi thép bọc cao su; 2 bánh trước cố định, 2 bánh sau xoay.', (SELECT category_id FROM productcategories WHERE category_name = 'Thiết bị khai thác hành trình'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0044', 'Xe đẩy công nghiệp', 'Dùng để bốc dỡ, vận chuyển bưu kiện, hàng hóa khối lượng lớn tại các trung tâm khai thác tỉnh/vùng.

Thông số kỹ thuật: Tải trọng 100-300kg; Kích thước 370x900mm; Khoảng sáng gầm 230mm; Đường kính bánh xe 105mm; Trọng lượng xe 9.5kg.', (SELECT category_id FROM productcategories WHERE category_name = 'Thiết bị khai thác hành trình'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0045', 'Xe nâng tay cao', 'Thiết bị hỗ trợ khai thác hàng nặng, bốc dỡ/vận chuyển bưu kiện tại trung tâm khai thác tỉnh/vùng. Phù hợp đặt hàng lên xe, kệ hoặc khuôn ở vị trí cao.

Thông số kỹ thuật: Tải trọng 1-2 tấn; Chiều cao nâng tối thiểu 85mm, tối đa 165mm; Chiều dài càng nâng 340-750mm (điều chỉnh được); Bánh trước 74x52mm; Bánh sau 180x50mm; Bánh lõi thép bọc PU; Phanh định vị bánh xe.', (SELECT category_id FROM productcategories WHERE category_name = 'Thiết bị khai thác hành trình'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0046', 'Xe nâng tay thấp', 'Thiết bị hỗ trợ khai thác hàng nặng, bốc dỡ/vận chuyển bưu kiện tại trung tâm khai thác tỉnh/vùng.

Thông số kỹ thuật: Tải trọng 1 tấn; Chiều cao nâng tối thiểu 85mm, tối đa 200mm; Kích thước càng (rộng×dài) 685×1220mm; Khoảng cách giữa 2 càng 360mm; Bánh lõi thép bọc nhựa PU.', (SELECT category_id FROM productcategories WHERE category_name = 'Thiết bị khai thác hành trình'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0047', 'Postef Raisecom M6511', 'Điện thoại thiết kế nhẹ, tinh tế, mỏng, di động. Pin dùng cả ngày, camera kép chụp khoảnh khắc đặc biệt, hỗ trợ mở rộng bộ nhớ, màn hình lớn hiển thị sống động.

Thông số kỹ thuật: Chip SC9863A, 8 nhân tới 1.6GHz; Pin 3000mAh; Màn hình 6.517 inch; Camera chính 5MP; Camera phụ 8MP; Hỗ trợ thẻ nhớ MicroSD tới 256GB. Tài liệu: Chứng nhận hợp quy Raisecom M6511; Catalogue Raisecom M6511.', (SELECT category_id FROM productcategories WHERE category_name = 'Điện thoại di động'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0048', 'Postef Raisecom T189', 'Điện thoại phổ thông cho nhu cầu cơ bản (gọi, nhắn tin SMS, báo thức, nhắc nhở). Thiết kế nhẹ, mỏng, hoạt động ổn định, pin bền, giá phải chăng, không cần tính năng cao cấp.

Thông số kỹ thuật: Thiết kế nhẹ, mỏng, cầm tay; Chức năng gọi, SMS, báo thức, nhắc nhở; Pin dùng lâu. Tài liệu: Chứng nhận hợp quy; Catalogue sản phẩm.', (SELECT category_id FROM productcategories WHERE category_name = 'Điện thoại di động'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0049', '5G CPE – C150', 'Bộ định tuyến di động chuyển tín hiệu không dây 4G/5G thành WiFi cho hộ gia đình/doanh nghiệp nhỏ.

Thông số kỹ thuật: Chuẩn 5G 3GPP Release 15/16 NSA/SA, Sub-6GHz; Băng tần 5G NR n1/3/5/7/8/20/28/38/40/41/71/77/78; MIMO 5G DL 4x4/UL 2x2; Băng thông Sub-6 tối đa 200MHz/120MHz; Băng tần LTE B1/2/3/4/5/7/8/20/28a/28b/34/38/39/40/41/42/43/48; Chuẩn WiFi AX1800; WiFi MIMO 2x2 (2.4G và 5G).', (SELECT category_id FROM productcategories WHERE category_name = '5G MiFI & 5G CPE (indoor & outdoor)'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0050', '5G CPE – C160', 'Bộ định tuyến di động chuyển tín hiệu 4G/5G thành WiFi cho gia đình/doanh nghiệp vừa và nhỏ.

Thông số kỹ thuật: Chuẩn 5G NR 3GPP Release 15/16 NSA/SA, Sub-6GHz; Băng tần NSA n1/3/5/7/8/20/28/38/40/41/71/77/78; MIMO DL 4x4/UL 2x2; Băng thông Sub-6 tối đa 200MHz/120MHz; Băng LTE B1/2/3/4/5/7/8/20/28a/28b/34/38/39/40/41/42/43/48; Chuẩn WiFi AX1800; WiFi MIMO 2x2 (2.4G và 5G).', (SELECT category_id FROM productcategories WHERE category_name = '5G MiFI & 5G CPE (indoor & outdoor)'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0051', '5G CPE – C170', 'Bộ định tuyến di động chuyển tín hiệu 4G/5G thành WiFi cho gia đình/doanh nghiệp nhỏ.

Thông số kỹ thuật: Chuẩn 5G NR 3GPP Release 15/16 NSA/SA, Sub-6GHz; Băng tần NSA n1/3/5/7/8/20/28/38/40/41/71/77/78; Ghép sóng mang Sub-6 TDD+TDD, TDD+FDD, FDD+FDD; MIMO DL 4x4/UL 2x2; Băng thông Sub-6 tối đa 200MHz/120MHz; Băng LTE B1/2/3/4/5/7/8/20/28a/28b/34/38/39/40/41/42/43/48; LTE MIMO DL 4x4; Chuẩn WiFi AX3600; WiFi MIMO 2x2 (2.4G và 5G).', (SELECT category_id FROM productcategories WHERE category_name = '5G MiFI & 5G CPE (indoor & outdoor)'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0052', '5G CPE – C510', 'Bộ định tuyến di động chuyển tín hiệu 4G/5G thành WiFi cho gia đình/doanh nghiệp nhỏ.

Thông số kỹ thuật: Tốc độ tải xuống 5G 2.3Gbps, tải lên 1.2Gbps; LTE Cat12 (down)/Cat13 (up); MIMO Sub-6 DL 4x4/UL 2x2; MIMO LTE DL 2x2/UL 1x1; Chuẩn WiFi 802.11a/b/g/n/ac/ax; Tốc độ WiFi tới 900Mbps; MIMO 2.4GHz và 5GHz 1x1; Bảo mật WPA/WPA2/WPA3.', (SELECT category_id FROM productcategories WHERE category_name = '5G MiFI & 5G CPE (indoor & outdoor)'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0053', '5G MIFI – M42Q', 'Bộ định tuyến di động MIFI chuyển tín hiệu 4G/5G thành WiFi cho gia đình/doanh nghiệp vừa và nhỏ.

Thông số kỹ thuật: MIMO Sub-6 DL 4x4/UL 2x2; Chuẩn WiFi 802.11ax; Băng tần 2x2 2.4GHz + 2x2 5.8GHz; Tốc độ AX1800 (~1Gbps); Kết nối tối đa 20 thiết bị; Cổng USB Type-C 3.0, phím nguồn, reset, Nano SIM, eSIM; Pin 4500mAh; Bảo mật secured boot, khóa mạng/SIM; Tính năng DHCP, DNS, DDNS, Port Forwarding, uPnP, Firewall, DMZ, VPN Passthrough, TR069, giám sát lưu lượng, WiFi Extender, FOTA, tiết kiệm pin.', (SELECT category_id FROM productcategories WHERE category_name = '5G MiFI & 5G CPE (indoor & outdoor)'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0054', '5G MIFI – M46Q', 'Bộ định tuyến di động MIFI chuyển tín hiệu 4G/5G thành WiFi cho gia đình/doanh nghiệp vừa và nhỏ.

Thông số kỹ thuật: Chuẩn 3GPP Release 16 (LTE và 5G); Băng tần NR Sub-6 n1/3/5/7/8/20/28/38/40/41/71/75/76/77/78; Băng LTE B1/3/5/7/8/20/28/32/71/38/40/41/42/43; MIMO Sub-6 DL 4x4/UL 2x2; MIMO LTE DL 4x4/UL 1x1.', (SELECT category_id FROM productcategories WHERE category_name = '5G MiFI & 5G CPE (indoor & outdoor)'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0055', '5G MIFI – M52', 'Bộ định tuyến di động MIFI chuyển tín hiệu 4G/5G thành WiFi cho gia đình/doanh nghiệp vừa và nhỏ.

Thông số kỹ thuật: MIMO Sub-6 DL 4x4/UL 2x2 hoặc 1x1; Chuẩn WiFi 802.11ax; Băng kép 2.4G hoặc 5.8G (hoạt động 1 băng tại 1 thời điểm); Tốc độ 802.11ax 300/600Mbps; SIM Nano SIM; Cổng USB Type-C 2.0/3.0; Pin 4000mAh tháo rời; Màn hình TFT LCD 2.4 inch hoặc 4 đèn LED (tùy phiên bản).', (SELECT category_id FROM productcategories WHERE category_name = '5G MiFI & 5G CPE (indoor & outdoor)'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0056', '5G Outdoor – O022', 'Thiết bị CPE 5G ngoài trời dùng nền tảng Qualcomm SDX62, hỗ trợ 5G-Sub6 NSA/SA, kết nối BLE 5.0, PoE@2.5Gbps.

Thông số kỹ thuật: Nền tảng Qualcomm SDX62; Hỗ trợ 5G-Sub6 NSA/SA; Bluetooth BLE 5.0; PoE@2.5Gbps; Hỗ trợ TR069, giám sát lưu lượng, DHCP, DNS, DDNS, Port-Forwarding, uPnP, Firewall, FOTA, VoLTE, CSFB, Dual WAN, quản trị từ WAN, VPN Passthrough, DMZ, chẩn đoán 1 chạm.', (SELECT category_id FROM productcategories WHERE category_name = '5G MiFI & 5G CPE (indoor & outdoor)'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0057', '5G Outdoor CPE – C120', 'Thiết bị CPE 5G ngoài trời, chống bụi/nước IP65, chống sét 6KV, lắp tường hoặc cột. Hoạt động -40°C đến 55°C, có cơ chế sưởi cho môi trường nhiệt độ thấp.

Thông số kỹ thuật: MIMO Sub-6 DL 4x4/UL 2x2; 8 ăng ten omni thu tín hiệu 360° (5.5dBi); 2 ăng ten định hướng tăng cường băng NR (9dBi); 1 cổng LAN 2.5CF (PoE); 1 khe Micro-SIM; 1 phím Reset; 1 cổng USB Type-C; 5 đèn LED (3 báo tín hiệu, 1 trạng thái mạng, 1 nguồn); Chuẩn IP65; Chống sét 6KV; Lắp tường/cột; Nhiệt độ hoạt động -40°C đến 55°C.', (SELECT category_id FROM productcategories WHERE category_name = '5G MiFI & 5G CPE (indoor & outdoor)'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0058', 'R011 Indoor AX3000 Router', 'Router WiFi 6 băng tần kép trong nhà, kết nối không dây tiên tiến cho hộ gia đình/doanh nghiệp.

Thông số kỹ thuật: Chip Qualcomm IPQ5018+QCN6102; Chuẩn IEEE 802.11 a/b/g/n/ac/ax; Băng kép 2.4GHz & 5GHz; Hiệu năng 2.4GHz 2x2, 20/40MHz, tới 480Mbps; Hiệu năng 5GHz 2x2, 20/40/80/160MHz, tới 2402Mbps; Kích thước 190x130x30mm; Nhiệt độ hoạt động 0-40°C; Nhiệt độ lưu kho 0-60°C; Độ ẩm 10-95% (không ngưng tụ).', (SELECT category_id FROM productcategories WHERE category_name = '5G MiFI & 5G CPE (indoor & outdoor)'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0059', 'Giải pháp POT-TMS', 'Hệ thống quản lý toàn diện cho trạm viễn thông: giám sát, điều khiển tự động thông minh (machine learning), thu thập dữ liệu đa phương thức (IoT), lưu trữ dữ liệu lớn, báo cáo trạng thái trạm. Đảm bảo an toàn thông tin liên lạc, giảm chi phí vận hành. Giám sát 24/7 thông số/cảnh báo/trạng thái vận hành: điều hòa, an ninh, nguồn điện, thiết bị làm mát, máy phát.

Thông số kỹ thuật: Thành phần gồm thiết bị hiện trường, máy chủ quản lý, phần mềm điều khiển thông minh; Kết nối LAN, WAN, GSM, tiếp điểm khô, SMS; Truy cập qua máy tính/smartphone/từ xa; Giám sát khí hậu, an ninh, nguồn, làm mát, máy phát; Điều khiển tự động tối ưu, theo kịch bản, thủ công (tại chỗ/từ xa); Báo cáo thời gian thực và định kỳ; Tính năng machine learning, IoT, Big Data, Data Mining, bảo mật.', (SELECT category_id FROM productcategories WHERE category_name = 'Giải pháp quản lý trạm BTS, tủ outdoor'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0060', 'POT-TMSG', 'Thiết bị chuyên dụng cho trạm viễn thông/công nghiệp, thu thập dữ liệu từ thiết bị trạm, kết nối phần mềm quản lý tập trung trên máy chủ trung tâm, hỗ trợ mọi phương thức kết nối mạng của trạm. Ứng dụng: trạm Macro, tủ Micro, Macro Outdoor, data center, phòng máy công nghiệp.

Thông số kỹ thuật: Giám sát/quản lý ắc quy lithium; Giám sát tủ nguồn AC/DC; Giám sát ATS và máy phát; Quản lý điều hòa DC cho trạm ngoài trời; Kết nối thêm thiết bị đo lường; Giám sát thiết bị thu phát; Kết nối LAN, WAN internet, SIM 4G; Giao thức HTTP, MQTT, TCP, UDP, SNMP V2/V3, Modbus TCP.', (SELECT category_id FROM productcategories WHERE category_name = 'Giải pháp quản lý trạm BTS, tủ outdoor'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0061', 'Indoor Femto Gateway', 'Gateway femto thiết kế cho ứng dụng IoT diện rộng: an ninh nhà ở, đọc công tơ tự động, giám sát lỗi, giám sát đèn đường. Phù hợp doanh nghiệp nhỏ, khu vực tư nhân (bãi đỗ xe, trung tâm triển lãm, khuôn viên trường), bù vùng phủ trong nhà.

Thông số kỹ thuật: Tới 8 kênh LoRa đồng thời; WLAN 2.4G 802.11b/g/n tích hợp; Chế độ repeater (bằng sáng chế) phủ sóng chặng cuối; Dải tần 470-928MHz (tùy phiên bản); Kết nối internet Ethernet/WiFi bridge; Quét kênh khả dụng theo RSSI; Cấu hình qua Web UI; Hỗ trợ Listen Before Talk; Ứng dụng định vị trong nhà; Nâng cấp firmware OTA/USB.', (SELECT category_id FROM productcategories WHERE category_name = 'Gateways'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0062', 'MiniHub Pro', 'Gateway LoRaWAN kết nối AWS IoT Core, giá thành hợp lý, dùng WiFi backhaul, có nhiều loại phích cắm tường thay thế. Có cổng sạc USB-C, phù hợp di động hoặc mở rộng vùng phủ.

Thông số kỹ thuật: Hỗ trợ băng EU, US; LoRaWAN 1.0.3; Backhaul WiFi; Nguồn qua phích cắm tường hoặc USB Type-C; WiFi 802.11 b/g/n chế độ Client; Ăng ten LoRa & WiFi tích hợp; Chip ESP32-D0WD (Espressif); Thiết kế nhỏ gọn dạng phích cắm tường; Lắp đặt nhanh; Hỗ trợ FreeRTOS, chuẩn AWS.', (SELECT category_id FROM productcategories WHERE category_name = 'Gateways'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0063', 'Outdoor Micro Gateway', 'Gateway ngoài trời cho ứng dụng smart city quy mô lớn: đọc công tơ tự động, giám sát chỉ báo lỗi, giám sát đèn đường. Triển khai dạng mạng hình sao tương tự trạm gốc di động.

Thông số kỹ thuật: Chuẩn LoRaWAN 1.0.3; Tới 16 kênh đồng thời; Backhaul 3G/4G; Dải tần 470-928MHz (tùy phiên bản); Tầm phủ >15km; 1 cổng LAN (10/100Mbps) có PoE; Hỗ trợ nền tảng LoRa Basic Station & AWS IoT Core; Downlink LBT; Quét nền, phủ sóng dự phòng đầy đủ; Chuẩn IP67; Hỗ trợ hệ điều hành Ubuntu.', (SELECT category_id FROM productcategories WHERE category_name = 'Gateways'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0064', 'Outdoor Micro Gateway V2', 'Gateway LoRaWAN ngoài trời thế hệ mới dùng chip SX1302/1303, 8/16 kênh. Thiết kế mạnh mẽ, hiệu năng cao, vùng phủ tốt, triển khai nhanh cho smart city, nông nghiệp, IoT. Hỗ trợ nhiều network server, nhiều tùy chọn backhaul, tích hợp năng lượng mặt trời.

Thông số kỹ thuật: Chuẩn LoRaWAN 1.0.4; Tới 16 kênh đồng thời; Downlink LBT; Chuẩn IP68; Cổng mạng Giga RJ45 (10/100/1000Mbps); Backhaul Ethernet, di động (4G/3G), WiFi; 2 đầu nối ăng ten LoRa; 1 đầu nối ăng ten GPS (tùy chọn); 1 đầu nối ăng ten 3G/4G (tùy chọn); 1 khe Dual-SIM (2FF, tùy chọn); Nhiều watchdog trong/ngoài.', (SELECT category_id FROM productcategories WHERE category_name = 'Gateways'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0065', 'Pico Next Indoor Gateway', 'Gateway LoRa-Cellular cho ứng dụng IoT diện rộng: an ninh nhà ở, đọc công tơ tự động, giám sát lỗi, giám sát đèn đường. Phù hợp doanh nghiệp nhỏ, khu vực tư nhân (bãi đỗ xe, trung tâm triển lãm, trường học).

Thông số kỹ thuật: Chuẩn LoRaWAN 1.0.3; Hỗ trợ chế độ Packet Forward/Basic Station; Spectral Scan & Listen Before Talk; Kết nối internet Ethernet/WiFi/3G/4G backhaul; Cấu hình qua Web UI; Nâng cấp firmware tại chỗ; Dự trữ CAN 2.0 cho truyền dữ liệu tương lai; Tùy chọn backhaul LTE-M và WiFi kèm GPS.', (SELECT category_id FROM productcategories WHERE category_name = 'Gateways'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0066', 'Ambient Light Sensor', 'Cảm biến ánh sáng môi trường dùng kết nối LoRaWAN, đo cường độ ánh sáng tương ứng phản ứng mắt người trong nhiều điều kiện chiếu sáng.

Thông số kỹ thuật: Hỗ trợ tần EU, US; Giao thức LoRaWAN 1.0.3; Cảm biến quang LTR-308ALS-WA16; Độ phân giải đo 20 bit; Dải đo 0.01-157K lux (tuyến tính); Nhiệt độ hoạt động 0-50°C.', (SELECT category_id FROM productcategories WHERE category_name = 'Sensors'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0067', 'Door & Window sensor', 'Cảm biến cửa/cửa sổ Tabs, tích hợp linh hoạt với nhiều thiết kế nội thất, lắp đặt đơn giản. Gồm thân chính (đo từ trường, truyền dữ liệu LoRaWAN khi có thay đổi) và nam châm vĩnh cửu đủ mạnh để cảm biến Hall-Effect phát hiện.

Thông số kỹ thuật: Cảm biến Hall-Effect; Tầm phát hiện 1cm; Chuẩn IP50 tương đương; Hỗ trợ tần EU, US, Asia; Giao thức LoRaWAN 1.0.3; Nhiệt độ hoạt động 0-50°C.', (SELECT category_id FROM productcategories WHERE category_name = 'Sensors'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0068', 'Healthy Home Sensor IAQ', 'Cảm biến Tabs Healthy Home dùng LoRaWAN truyền nhiệt độ, độ ẩm tương đối và hợp chất hữu cơ bay hơi từ môi trường xung quanh. Lắp trong phòng để xác định chất lượng không khí, nhiệt độ, độ ẩm có lý tưởng không.

Thông số kỹ thuật: Hỗ trợ tần EU, US, Asia; Giao thức LoRaWAN 1.0.3; Cảm biến tích hợp Bosch BME680 (IAQ); Chuẩn IP40 tương đương; Nhiệt độ hoạt động 0-50°C.', (SELECT category_id FROM productcategories WHERE category_name = 'Sensors'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0069', 'LR1110 LoRa Edge Tracker', 'Thiết bị định vị dùng chip LR1110 tiên tiến, có GNSS vệ tinh và quét WiFi MAC, cung cấp dữ liệu vị trí chính xác thời gian thực, truyền dữ liệu thô lên cloud xử lý. Tiêu thụ điện thấp, pin bền, phù hợp theo dõi tài sản giá trị hoặc phương tiện.

Thông số kỹ thuật: Chip chính Semtech LR1110 (WiFi & GNSS); Vi điều khiển STM32WB55 có BLE; Pin 2400mAh (2x1200mAh); Vỏ 52x85x27mm, chuẩn IP66; Ăng ten GNSS phân tập (Patch+PCB); Công suất phát tối đa +22dBm; Độ nhạy LoRa (SF12,125kHz) -140dBm; (SF7,125kHz) -127dBm; Độ nhạy GNSS -140dBm; Cảm biến gia tốc 3 trục & Hall-Effect; Cấu hình qua BLE.', (SELECT category_id FROM productcategories WHERE category_name = 'Sensors'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0070', 'MerryIoT Leak detection', 'Cảnh báo thời gian thực ngay khi tiếp xúc nước rò rỉ. Có thể theo dõi độ ẩm và nhiệt độ cao/thấp qua cảm biến tích hợp.

Thông số kỹ thuật: Chuẩn LoRaWAN 1.0.3; Đầu dò cảm biến nước tích hợp; Chuẩn chống nước IP67; Nguồn 2 pin AA kiềm; Tuổi thọ pin ~3 năm (tùy tần suất báo cáo, độ ẩm, nhiệt độ); Phát hiện ngập và nhỏ giọt nước; Còi báo tích hợp (tối thiểu 80dB); Lắp đặt cắm-và-chạy; Nút test kiểm tra vùng phủ.', (SELECT category_id FROM productcategories WHERE category_name = 'Sensors'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0071', 'MerryIoT Motion Detection', 'Cảm biến chuyển động LoRaWAN cho an ninh nhà ở/tòa nhà, quản lý cơ sở vật chất, giám sát sử dụng không gian làm việc, phòng họp, nhà vệ sinh.

Thông số kỹ thuật: Chuẩn LoRaWAN 1.0.3; Công nghệ 2 cảm biến hồng ngoại thụ động (PIR) kèm thấu kính Fresnel; Chuẩn IP40 tương đương; Nguồn 2 pin AA kiềm; Tuổi thọ pin ~3 năm; Lắp đặt cắm-và-chạy; Hỗ trợ giám sát bàn/phòng, nút test, cảnh báo chuyển động bất thường.', (SELECT category_id FROM productcategories WHERE category_name = 'Sensors'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0072', 'MerryIoT open/close', 'Cảm biến cửa/cửa sổ tích hợp linh hoạt với nội thất, lắp đặt đơn giản. Gồm thân chính đo từ trường + nam châm vĩnh cửu, cảm biến Hall-Effect.

Thông số kỹ thuật: Chuẩn LoRaWAN 1.0.3; Cảm biến Hall-Effect; Tầm phát hiện 1cm; Chuẩn IP40 tương đương; Gia tốc kế 3 trục tích hợp (phát hiện va đập, rung, chuyển động bất thường); Tuổi thọ pin ~3 năm; Còi báo 80dB (tối thiểu); Lắp đặt cắm-và-chạy; Nút test vùng phủ.', (SELECT category_id FROM productcategories WHERE category_name = 'Sensors'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0073', 'Motion Sensor PIR', 'Cảm biến Tabs PIR & Occupancy phát hiện sự hiện diện/chuyển động trong nhà cho mục đích an ninh, tối ưu quản lý cơ sở vật chất, giám sát sử dụng không gian làm việc, phòng họp, nhà vệ sinh.

Thông số kỹ thuật: Hỗ trợ tần EU, US, Asia; Giao thức LoRaWAN 1.0.3; 2 cảm biến hồng ngoại thụ động kèm thấu kính Fresnel; Tầm phát hiện 7m; Chuẩn IP50 tương đương; Góc phát hiện ngang 123°, dọc 93°.', (SELECT category_id FROM productcategories WHERE category_name = 'Sensors'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0074', 'Object Locator', 'Công cụ định vị đa năng cho giám sát tài sản, theo dõi thú cưng, đồ vật cá nhân, tài sản giá trị. Gồm bộ thu GNSS, nút bấm, đèn LED báo, cổng USB-C.

Thông số kỹ thuật: Hỗ trợ tần EU, US, Asia; Giao thức LoRaWAN 1.0.3; Kích thước nhỏ gọn; Pin bền; Chuẩn IP64 tương đương; Pin sạc LiPo (4.2V, 540mAh) qua USB Type-C; Gia tốc kế 3 trục.', (SELECT category_id FROM productcategories WHERE category_name = 'Sensors'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0075', 'Sound Level Sensor', 'Cảm biến mức âm thanh dùng kết nối LoRaWAN, đo mức âm thanh theo decibel (dBA) trong nhiều môi trường tòa nhà.

Thông số kỹ thuật: Hỗ trợ tần EU, US; Giao thức LoRaWAN 1.0.3; Micro loại MEMS; Độ phân giải 1dB; Dải đo 40-100dB(A); Đáp ứng tần số 100-10,000Hz; Nhiệt độ hoạt động 0-50°C.', (SELECT category_id FROM productcategories WHERE category_name = 'Sensors'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0076', 'Temperature & Humidity Sensor', 'Cảm biến nhiệt độ và độ ẩm dùng trong nhà/tòa nhà, phù hợp ứng dụng dân dụng hoặc quản lý cơ sở vật chất, thiết kế tối ưu, pin bền, thẩm mỹ khi lắp đặt.

Thông số kỹ thuật: Hỗ trợ tần EU, US, Asia; Giao thức LoRaWAN 1.0.3; Độ chính xác nhiệt độ ±1°C; Độ chính xác độ ẩm ±5%; Chuẩn IP40 tương đương; Nhiệt độ hoạt động 0-50°C.', (SELECT category_id FROM productcategories WHERE category_name = 'Sensors'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0077', 'Water Leak Sensor', 'Cảm biến rò rỉ nước LoRaWAN, gửi cảnh báo uplink khi phát hiện rò rỉ. Kết hợp phát hiện rò rỉ với giám sát nhiệt độ/độ ẩm, có đầu dò dây dài tháo rời qua cổng Micro USB, ngăn thiệt hại tốn kém.

Thông số kỹ thuật: Kết nối LoRaWAN 1.0.3; Hỗ trợ tần EU, US; Chức năng phát hiện rò rỉ nước, nhiệt độ, độ ẩm; Đầu dò dây dài tháo rời; Giao diện Micro USB cho đầu dò; Nhiệt độ hoạt động 0-50°C.', (SELECT category_id FROM productcategories WHERE category_name = 'Sensors'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0078', 'MI10 Mesh Indoor Access Point', 'Access Point mesh trong nhà (Prism MI10) nhắm thị trường doanh nghiệp vừa và nhỏ, thiết kế tri-band, backhaul qua ethernet hoặc mesh không dây. Băng thông rộng, bảo mật cao, triển khai đơn giản với tự động cấu hình/phát hiện mesh, quản lý thời gian thực.

Thông số kỹ thuật: Ăng ten tích hợp hiệu năng cao; 5GHz băng cao: 4x4 MIMO độc lập cho kết nối mesh; 5GHz băng thấp: 2x2 MIMO kép cho truy cập client; Băng 2.4GHz: 2x2 MIMO kép; Cổng WAN 2.5G-BASE-T hỗ trợ PoE 802.3bt; Chọn kênh tốt nhất, chuyển kênh liền mạch; QoS ưu tiên streaming ứng dụng; Cập nhật firmware tự động, giảm nhiễu, tối ưu airtime; Mesh đa chặng, chuyển đổi dự phòng node, tự kết nối.', (SELECT category_id FROM productcategories WHERE category_name = 'Router wifi'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0079', 'MO10 Mesh Outdoor Access Point', 'Access Point mesh ngoài trời WiFi 6 (Prism MO10) cho doanh nghiệp vừa/nhỏ, thiết kế tri-radio, hỗ trợ backhaul ethernet và mesh không dây. Băng thông rộng, bảo mật cao, triển khai dễ với tự động phát hiện mesh, quản lý thời gian thực.

Thông số kỹ thuật: Thiết kế tri-radio có liên kết mesh riêng; 5GHz băng cao 4x4 MIMO độc lập cho mesh; 5GHz băng thấp 2x2 MIMO cho client; 2.4GHz 2x2 MIMO cho client; Cổng WAN 2.5G-BASE-T PoE 802.3bt; Chọn kênh tốt nhất, chuyển liền mạch; Cân bằng tải/định hướng client tối ưu; QoS ưu tiên ứng dụng; Đảm bảo băng thông theo MAC; Giảm nhiễu, tối ưu airtime; Cập nhật firmware tự động; Chuyển đổi dự phòng node mesh, tự kết nối.', (SELECT category_id FROM productcategories WHERE category_name = 'Router wifi'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0080', 'WiFi7 BE3600 Smart Router', 'Router thông minh WiFi 7 (Model HBR7305H), kết nối nhiều thiết bị đồng thời, tốc độ tới 3600Mbps. Băng kép, kiến trúc OFDMA 4-luồng, phù hợp streaming, gaming, nhà thông minh. Công nghệ beamforming thông minh cho kết nối hiệu quả, định hướng.

Thông số kỹ thuật: Chuẩn WiFi 7; Tốc độ tới 3600Mbps; Công nghệ MU-MIMO, 1024QAM; Băng thông 160MHz; 4 luồng băng kép; 4 cổng Gigabit Ethernet (WAN/LAN thích ứng); Hỗ trợ mesh roaming liền mạch; Bảo mật mạng nâng cao; Beamforming thông minh. Model: HBR7305H.', (SELECT category_id FROM productcategories WHERE category_name = 'Router wifi'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0081', 'DATAssure™', 'Hệ thống giám sát & cảnh báo không dây cho phòng lab bệnh viện. Theo dõi liên tục nhiệt độ, độ ẩm, nồng độ CO2/O2, hợp chất hữu cơ bay hơi (VOCs), nhiệt độ/LN2 cho tủ ấp phôi. Hoạt động độc lập, tích hợp trực tiếp với mạng CNTT doanh nghiệp mà không cần máy tính/server/phần mềm riêng. Dữ liệu từ cảm biến qua transmitter tới base station, xem thời gian thực trên màn hình cảm ứng hoặc web. Hỗ trợ quản lý tập trung qua cloud server tùy chọn, truy cập web 24/7, cảnh báo, biểu đồ, báo cáo tùy chỉnh.

Thông số kỹ thuật: Giám sát nhiệt độ, độ ẩm, CO2/O2, VOCs, nhiệt độ/LN2 tủ ấp; Hoạt động độc lập không cần hạ tầng CNTT riêng; Truyền dữ liệu cảm biến→transmitter→base station không dây; Hiển thị màn hình cảm ứng hoặc dashboard web; Quản lý tập trung qua cloud server (tùy chọn); Truy cập web 24/7 phân quyền; Quản lý đa admin; Đạt chuẩn HACCP, BRC, FDA, MRHA; Tương thích mọi trình duyệt.', (SELECT category_id FROM productcategories WHERE category_name = 'Giải pháp giám sát cho phòng Lab bệnh viện'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0082', 'ICS Audio box V.1', 'Thiết bị kết nối tới hệ thống server và phần mềm quản lý tập trung cho truyền thanh, hình ảnh, thu thập dữ liệu môi trường, GPS qua Internet. Kết nối ngoại vi như loa truyền thanh, màn hình quảng cáo, cảm biến môi trường. Hỗ trợ truyền dữ liệu qua LAN, WiFi, 4G.

Thông số kỹ thuật: Cầu dao 2 cực 10A; Chống sét đường nguồn; Chống sét đường tín hiệu; Relay bảo vệ <90V, >240V; Ngõ ra âm thanh 4 loa 50W (tổng 320W); Xuất hình ảnh ra màn hình quảng cáo; Định vị GPS; Cảm biến nhiệt độ trong hộp; Điều khiển thiết bị qua relay; Kết nối hub trung tâm, hộp đấu nối, cực nối, đầu nối DC.', (SELECT category_id FROM productcategories WHERE category_name = 'Audio box'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0083', 'ICS Audio box V.2', 'Thiết bị kết nối mạng tích hợp với hệ thống server và phần mềm quản lý tập trung cho truyền thanh. Hỗ trợ truyền dữ liệu qua WiFi và 4G.

Thông số kỹ thuật: Công suất 25W (tùy chọn thêm loa 25W tạo cặp stereo); Áp suất âm thanh 102-105dB; Chống bụi/nước IP65; Nhiệt độ hoạt động -25°C đến 60°C.', (SELECT category_id FROM productcategories WHERE category_name = 'Audio box'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0084', 'ICS Audio box V.3', 'Thiết bị kết nối tới hệ thống server và phần mềm quản lý tập trung cho truyền thanh qua internet. Kết nối ngoại vi như loa truyền thanh, cảm biến môi trường. Hỗ trợ LAN, WiFi, 4G.

Thông số kỹ thuật: Board mở rộng LAN tương thích Raspberry Pi Zero; Cầu dao 2 cực 10A; Chống sét đường nguồn; Chống sét đường tín hiệu; Relay bảo vệ <90V, >240V; Kết nối hộp đấu nối có đầu nối DC; Ngõ ra âm thanh 2x30W.', (SELECT category_id FROM productcategories WHERE category_name = 'Audio box'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0085', 'Transmitter box', 'Thiết bị chuyển đổi tín hiệu FM và tín hiệu âm tần thành tín hiệu số để truyền qua internet. Hỗ trợ truyền dữ liệu qua LAN, WiFi, 4G, kết nối trực tiếp Cloud Server.

Thông số kỹ thuật: Ngõ vào jack audio 3.5mm, ngõ vào Micro; Chuẩn kết nối 3G/4G/Ethernet; Chức năng thu tín hiệu FM, âm tần từ Micro/Máy tính; Hỗ trợ mạng LAN, WiFi, 4G; Tích hợp Cloud Server trực tiếp.', (SELECT category_id FROM productcategories WHERE category_name = 'Transmitter box'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0086', 'Loa nén POSTEF 25W, vành nhôm', 'Loa nén ngoài trời cho mạng truyền thanh đơn/đa tuyến, chất lượng âm thanh cao, tương thích hệ thống loa trở kháng thấp. Lắp độc lập hoặc tích hợp hệ thống tại nhà ga, sân bay, khu dân cư, trường học.

Thông số kỹ thuật: Công suất 25W; Vành nhôm; Trở kháng 8 Ohm; Cường độ âm thanh ≥104dB; Chống bụi/nước IP65; Tầm phát 500-800m; Có giá treo (sơn tĩnh điện hoặc inox).', (SELECT category_id FROM productcategories WHERE category_name = 'Loa truyền thanh'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0087', 'LOA NÉN POSTEF 35W', 'Loa nén ngoài trời cho mạng truyền thanh đơn/đa tuyến, chất lượng âm thanh vượt trội, tương thích hệ thống trở kháng thấp. Lắp độc lập hoặc tích hợp tại nhà ga, bến tàu, sân bay, khu dân cư, trường học.

Thông số kỹ thuật: Công suất định mức 35W; Trở kháng không biến áp 8 Ohm; Trở kháng có biến áp (100V) 286 Ohm, (70V) 140 Ohm; Cường độ âm thanh 105-107dB; Đáp ứng tần số 160-6000Hz; Chống bụi/nước IP65; Vành & vỏ nhôm sơn tĩnh điện; Nón loa nhựa ABS gia cường PA, PC; Màng loa Polyimide (công nghệ tiên tiến); Khung treo thép sơn tĩnh điện.', (SELECT category_id FROM productcategories WHERE category_name = 'Loa truyền thanh'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0088', 'Loa POSTEF 60W', 'Loa truyền thanh ngoài trời cho mạng đơn/đa tuyến, âm thanh chất lượng cao cho phát thanh/thông báo, tương thích hệ thống trở kháng thấp. Lắp độc lập hoặc tích hợp tại đầu mối giao thông, cảng, sân bay, khu dân cư, trường học.

Thông số kỹ thuật: Công suất 60W; Trở kháng 8 Ohm; Mức áp suất âm 104dB (1W-1m); Dải tần hoạt động 300-5000Hz; Cường độ âm tối đa trên 110dB.', (SELECT category_id FROM productcategories WHERE category_name = 'Loa truyền thanh'));
INSERT INTO products (product_code, product_name, description, category_id) VALUES
('SP-0089', 'Loa thông tin POSTEF 25W, vành nhựa', 'Dùng ngoài trời trong mạng truyền thanh đơn/đa tuyến để phát thanh, thông báo chất lượng âm thanh cao, tương thích trở kháng thấp. Lắp độc lập hoặc tích hợp hệ thống tại đầu mối giao thông, khu dân cư, trường học.

Thông số kỹ thuật: Công suất 25W; Vành nhựa; Trở kháng 8 Ohm; Cường độ âm thanh ≥102dB; Chống bụi/nước IP54; Tầm phát 400-600m; Có giá treo (sơn tĩnh điện hoặc inox).', (SELECT category_id FROM productcategories WHERE category_name = 'Loa truyền thanh'));

-- ===== Ảnh sản phẩm =====
INSERT INTO productimages (product_id, image_url, display_order) VALUES
((SELECT product_id FROM products WHERE product_code = 'SP-0001'), 'https://postef.com.vn/wp-content/uploads/2019/11/ac-quy-lithium-4850-510x510.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0002'), 'https://postef.com.vn/wp-content/uploads/2026/08/screenshot_1786350000.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0003'), 'https://postef.com.vn/wp-content/uploads/2019/11/pngb-510x564.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0004'), 'https://postef.com.vn/wp-content/uploads/2019/11/pnb-723x800.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0005'), 'https://postef.com.vn/wp-content/uploads/2019/11/bae-510x510.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0006'), 'https://postef.com.vn/wp-content/uploads/2019/11/Chua-co-ten-300-x-300-px-600-x-600-px.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0007'), 'https://postef.com.vn/wp-content/uploads/2024/04/uni.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0008'), 'https://postef.com.vn/wp-content/uploads/2019/11/12113_Eaton-9395-a.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0009'), 'https://postef.com.vn/wp-content/uploads/2019/11/Small-no-fold-poly-with-wp-reg.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0010'), 'https://postef.com.vn/wp-content/uploads/2024/05/Capture.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0011'), 'https://postef.com.vn/wp-content/uploads/2024/04/boc-chat.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0012'), 'https://postef.com.vn/wp-content/uploads/2024/04/Cap-quang-keo-cong.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0013'), 'https://postef.com.vn/wp-content/uploads/2024/04/treo-kl-pkl.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0014'), 'https://postef.com.vn/wp-content/uploads/2024/04/dtb.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0015'), 'https://postef.com.vn/wp-content/uploads/2024/04/DTB-dem-long.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0016'), 'https://postef.com.vn/wp-content/uploads/2024/04/soi-quang-g657a1.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0017'), 'https://postef.com.vn/wp-content/uploads/2024/04/Hop-dau-noi-nhua.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0018'), 'https://postef.com.vn/wp-content/uploads/2024/04/Hop-dau-noi-POS-HOS-R-va-POS-SPL-R.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0019'), 'https://postef.com.vn/wp-content/uploads/2024/04/tu-dau-noi-1920fo-dang-be.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0020'), 'https://postef.com.vn/wp-content/uploads/2024/04/Tu-phan-phoi-quang-OCC-SPLxxxFO.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0021'), 'https://postef.com.vn/wp-content/uploads/2024/04/tu-quang-dong.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0022'), 'https://postef.com.vn/wp-content/uploads/2024/04/Khung-–-nap-ham-cap.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0023'), 'https://postef.com.vn/wp-content/uploads/2024/04/MX96-1.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0024'), 'https://postef.com.vn/wp-content/uploads/2024/04/ODF-GAN-RACK-19-INCH.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0025'), 'https://postef.com.vn/wp-content/uploads/2024/04/Ong-nhua-PVC-U-dung-cho-tuyen-cap-ngam.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0026'), 'https://postef.com.vn/wp-content/uploads/2024/04/phu-kien-quang.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0027'), 'https://postef.com.vn/wp-content/uploads/2024/04/ACE.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0028'), 'https://postef.com.vn/wp-content/uploads/2019/11/atenna-510x510.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0029'), 'https://postef.com.vn/wp-content/uploads/2024/04/helix2.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0030'), 'https://postef.com.vn/wp-content/uploads/2024/04/New-Nimbus1.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0031'), 'https://postef.com.vn/wp-content/uploads/2024/04/stratus-510x510.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0032'), 'https://postef.com.vn/wp-content/uploads/2024/09/mb02.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0033'), 'https://postef.com.vn/wp-content/uploads/2019/11/tu-outdoor_300x300.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0034'), 'https://postef.com.vn/wp-content/uploads/2024/11/z6008762354576_67aebe04e7825601a98aad4a06b469a6.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0035'), 'https://postef.com.vn/wp-content/uploads/2024/04/Tu-PP.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0036'), 'https://postef.com.vn/wp-content/uploads/2019/11/60-ps.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0037'), 'https://postef.com.vn/wp-content/uploads/2026/05/T601.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0038'), 'https://postef.com.vn/wp-content/uploads/2024/04/Asset-1.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0039'), 'https://postef.com.vn/wp-content/uploads/2024/04/tn-400x332-1.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0040'), 'https://postef.com.vn/wp-content/uploads/2024/04/cls-400x231-1.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0041'), 'https://postef.com.vn/wp-content/uploads/2024/04/Bang-tai-di-dong-con-lan.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0042'), 'https://postef.com.vn/wp-content/uploads/2024/04/Bang-tai-di-dong-day-dai-400x276-1.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0043'), 'https://postef.com.vn/wp-content/uploads/2024/04/Xe-day-ban-400x269-1.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0044'), 'https://postef.com.vn/wp-content/uploads/2024/04/Xe-day-cong-nghiep.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0045'), 'https://postef.com.vn/wp-content/uploads/2024/04/Nang-tay-cao.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0046'), 'https://postef.com.vn/wp-content/uploads/2024/04/Xe-nang-tay-thap.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0047'), 'https://postef.com.vn/wp-content/uploads/2024/06/6-800x800.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0048'), 'https://postef.com.vn/wp-content/uploads/2024/06/7-800x800.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0049'), 'https://postef.com.vn/wp-content/uploads/2024/10/5g-cpe-1.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0050'), 'https://postef.com.vn/wp-content/uploads/2024/10/c160b.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0051'), 'https://postef.com.vn/wp-content/uploads/2024/10/c170.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0052'), 'https://postef.com.vn/wp-content/uploads/2024/10/C510-768x480-1.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0053'), 'https://postef.com.vn/wp-content/uploads/2024/10/m42.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0054'), 'https://postef.com.vn/wp-content/uploads/2024/10/m46-1.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0055'), 'https://postef.com.vn/wp-content/uploads/2024/10/M52_LCD_01-768x512-1-510x340.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0056'), 'https://postef.com.vn/wp-content/uploads/2024/10/O022_3-768x512-1.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0057'), 'https://postef.com.vn/wp-content/uploads/2024/10/O022_3-768x512-1.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0058'), 'https://postef.com.vn/wp-content/uploads/2024/10/ax3000.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0059'), 'https://postef.com.vn/wp-content/uploads/2024/04/Untitled.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0060'), 'https://postef.com.vn/wp-content/uploads/2024/04/tmsg3.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0061'), 'https://postef.com.vn/wp-content/uploads/2024/04/femto.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0062'), 'https://postef.com.vn/wp-content/uploads/2024/04/minihub.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0063'), 'https://postef.com.vn/wp-content/uploads/2024/04/outdoor2.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0064'), 'https://postef.com.vn/wp-content/uploads/2024/04/outdoor-v2.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0065'), 'https://postef.com.vn/wp-content/uploads/2024/04/pico.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0066'), 'https://postef.com.vn/wp-content/uploads/2024/04/123-2.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0067'), 'https://postef.com.vn/wp-content/uploads/2024/04/dooe.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0068'), 'https://postef.com.vn/wp-content/uploads/2024/04/123-3.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0069'), 'https://postef.com.vn/wp-content/uploads/2024/04/tracker.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0070'), 'https://postef.com.vn/wp-content/uploads/2024/04/leak.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0071'), 'https://postef.com.vn/wp-content/uploads/2024/04/motion.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0072'), 'https://postef.com.vn/wp-content/uploads/2024/04/opencloe-510x510.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0073'), 'https://postef.com.vn/wp-content/uploads/2024/04/sss-2.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0074'), 'https://postef.com.vn/wp-content/uploads/2024/04/123-4.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0075'), 'https://postef.com.vn/wp-content/uploads/2024/04/123-2.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0076'), 'https://postef.com.vn/wp-content/uploads/2024/04/123-3.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0077'), 'https://postef.com.vn/wp-content/uploads/2024/04/sss-1.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0078'), 'https://postef.com.vn/wp-content/uploads/2024/04/mi10.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0079'), 'https://postef.com.vn/wp-content/uploads/2024/04/mo101.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0080'), 'https://postef.com.vn/wp-content/uploads/2024/10/Capture.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0081'), 'https://postef.com.vn/wp-content/uploads/2024/04/Dataasure-2022-1.jpg', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0082'), 'https://postef.com.vn/wp-content/uploads/2022/10/12.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0083'), 'https://postef.com.vn/wp-content/uploads/2022/10/v2.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0084'), 'https://postef.com.vn/wp-content/uploads/2022/10/V3png.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0085'), 'https://postef.com.vn/wp-content/uploads/2022/10/trans_png.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0086'), 'https://postef.com.vn/wp-content/uploads/2022/10/nen-25w-nhom.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0087'), 'https://postef.com.vn/wp-content/uploads/2022/10/nen-25w-nhua.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0088'), 'https://postef.com.vn/wp-content/uploads/2022/10/60w.png', 0),
((SELECT product_id FROM products WHERE product_code = 'SP-0089'), 'https://postef.com.vn/wp-content/uploads/2022/10/loa-thongtin-1.png', 0);
