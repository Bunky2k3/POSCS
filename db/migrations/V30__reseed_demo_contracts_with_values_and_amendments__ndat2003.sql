-- Gieo lại toàn bộ dữ liệu HỢP ĐỒNG cho khớp nghiệp vụ đã thêm từ V23 tới V29.
--
-- Bộ hợp đồng đang có ra đời trước khi hệ thống biết tới trục tiến độ (V24),
-- giá trị hợp đồng (V27) và phụ lục (V29), nên nó không diễn đạt được gì:
-- gần như toàn bộ đều "Đã ký + Đang hiệu lực", không hợp đồng nào có giá trị,
-- và cả CSDL có đúng một phụ lục demo gieo tay ở V29. Mở màn hình nào lên cũng
-- chỉ thấy một trạng thái, nên không ai kiểm được các trạng thái còn lại có
-- hiển thị đúng không.
--
-- CHỈ ĐỘNG VÀO HỢP ĐỒNG. Khách hàng, sản phẩm, nhân viên, phiếu hỗ trợ giữ
-- nguyên -- nghiệp vụ mới nằm ở hợp đồng, và xoá rộng hơn là vứt đi dữ liệu
-- không liên quan. Phiếu hỗ trợ CÓ trỏ tới hợp đồng (contract_id, ON DELETE
-- SET NULL) nên chúng được NỐI LẠI ở cuối file, không để rơi về NULL.
--
-- KHÔNG GHI CỨNG MỘT ID NÀO. Mỗi CSDL đánh số khác nhau: bản dựng từ
-- schema.sql có sales2 = user 1, còn poscs_db của máy đang dùng thì sales2 =
-- user 20. Mọi khoá ngoại ở đây tra qua `username` / `enterprise_code`, và các
-- khối đều là INSERT ... SELECT ... JOIN, nên CSDL nào thiếu một tham chiếu
-- (ví dụ bản dựng thuần từ schema.sql chưa có nhà cung cấp NCC-* của V28) thì
-- dòng đó tự rơi ra thay vì làm hỏng cả lần chạy.
--
-- MỐC THỜI GIAN TÍNH TỪ CURDATE(), không ghi ngày cứng. Trạng thái theo lịch
-- suy ra từ effective_date/end_date, nên ngày cứng nghĩa là vài tháng nữa cả
-- bộ dữ liệu trôi hết sang "Đã hết hạn" và mất đúng thứ nó sinh ra để bày.

INSERT INTO schema_migrations (version) VALUES ('V30__reseed_demo_contracts_with_values_and_amendments__ndat2003');

-- ---------------------------------------------------------------------------
-- Xoá bộ cũ
-- ---------------------------------------------------------------------------
-- Con trước cha. contractproducts/contract_payments/contract_history đều treo
-- vào contracts; riêng contracts tự trỏ vào chính nó nên PHỤ LỤC phải đi trước
-- hợp đồng gốc -- khoá ngoại fk_contracts_parent không có CASCADE (cố ý, V29).
UPDATE technicalrequests SET contract_id = NULL;
DELETE FROM contract_history;
DELETE FROM contract_payments;
DELETE FROM contractproducts;
DELETE FROM contracts WHERE parent_contract_id IS NOT NULL;
DELETE FROM contracts;

ALTER TABLE contracts AUTO_INCREMENT = 1;
ALTER TABLE contractproducts AUTO_INCREMENT = 1;
ALTER TABLE contract_payments AUTO_INCREMENT = 1;
ALTER TABLE contract_history AUTO_INCREMENT = 1;

-- ---------------------------------------------------------------------------
-- Hợp đồng gốc -- mỗi dòng bày một tình huống khác nhau
-- ---------------------------------------------------------------------------
-- Cột `status` gieo cho khớp ngày tháng ngay lúc chạy; nó chỉ là bản lưu để
-- lọc, còn nhãn hiển thị thì ContractDAO.computeStatus tính lại mỗi lần đọc.
--
-- Nhánh UNION ALL ĐẦU TIÊN phải có giá trị thật ở mọi cột: MySQL lấy kiểu của
-- cột từ nó, nên để NULL đứng đầu là các nhánh sau đổ vào một cột không kiểu.
INSERT INTO contracts
  (contract_code, title, contract_type, direction, progress_status, signing_date, effective_date, end_date,
   enterprise_id, owner_id, status, contract_value,
   signer_name, signer_position, counterparty_signer_name, counterparty_signer_position, signing_place)
SELECT x.code, x.title, x.ctype, x.direction, x.progress, x.sign_date, x.eff_date, x.end_date,
       e.enterprise_id, u.user_id, x.status, x.value,
       x.signer, x.signer_pos, x.cp_signer, x.cp_signer_pos, x.place
FROM (
    -- 1. Thẳng thớm nhất: đã ký, đang chạy, kỳ thu khớp đúng giá trị.
    SELECT N'01/2026/HĐKT-POSTEF' code, N'Cung cấp ắc quy lithium cho 45 trạm BTS' title,
           N'Cung cấp thiết bị' ctype, N'Bán' direction, N'Đã ký' progress,
           DATE_SUB(CURDATE(), INTERVAL 70 DAY) sign_date, DATE_SUB(CURDATE(), INTERVAL 60 DAY) eff_date,
           DATE_ADD(CURDATE(), INTERVAL 200 DAY) end_date,
           'KH-0001' kh, 'sales2' owner, N'Đang hiệu lực' status, 1200000000.00 value,
           N'Nguyễn Văn Bình' signer, N'Phó Tổng Giám đốc' signer_pos,
           N'Trần Văn Hùng' cp_signer, N'Tổng Giám đốc' cp_signer_pos, N'Hà Nội' place

    -- 2. CÓ PHỤ LỤC BỔ SUNG: 1,5 tỷ + 250tr = 1,75 tỷ, kỳ thu lập đủ cho cả cụm.
    UNION ALL SELECT N'02/2026/HĐKT-POSTEF', N'Cung cấp nguồn POSTEF cho 30 trạm vùng lõi',
           N'Cung cấp thiết bị', N'Bán', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 130 DAY), DATE_SUB(CURDATE(), INTERVAL 120 DAY),
           DATE_ADD(CURDATE(), INTERVAL 240 DAY),
           'KH-0001', 'sales2', N'Đang hiệu lực', 1500000000.00,
           N'Nguyễn Văn Bình', N'Phó Tổng Giám đốc', N'Trần Văn Hùng', N'Tổng Giám đốc', N'Hà Nội'

    -- 3. PHỤ LỤC GIẢM TRỪ: khách cắt bớt hạng mục, 900tr - 180tr = 720tr.
    UNION ALL SELECT N'03/2026/HĐKT-POSTEF', N'Cung cấp tủ nguồn và ắc quy Gel cho tuyến trục',
           N'Cung cấp thiết bị', N'Bán', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 100 DAY), DATE_SUB(CURDATE(), INTERVAL 90 DAY),
           DATE_ADD(CURDATE(), INTERVAL 150 DAY),
           'KH-0002', 'sales3', N'Đang hiệu lực', 900000000.00,
           N'Nguyễn Văn Bình', N'Phó Tổng Giám đốc', N'Đỗ Minh Khang', N'Giám đốc Kỹ thuật', N'Hải Phòng'

    -- 4. PHỤ LỤC GIA HẠN KHÔNG ĐỔI TIỀN. Hợp đồng gốc sắp hết hạn theo lịch mà
    --    phụ lục đã nối tiếp sang năm sau -- đúng chỗ người ta hay tưởng ngày
    --    của cha phải chạy theo (nó KHÔNG, quyết định 2026-09-17).
    UNION ALL SELECT N'04/2026/HĐKT-POSTEF', N'Bảo trì hệ thống nguồn 2026',
           N'Bảo trì bảo dưỡng', N'Bán', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 300 DAY), DATE_SUB(CURDATE(), INTERVAL 290 DAY),
           DATE_ADD(CURDATE(), INTERVAL 20 DAY),
           'KH-0003', 'sales3', N'Sắp hết hạn', 600000000.00,
           N'Lê Thị Thanh', N'Giám đốc Kinh doanh', N'Phạm Quốc Bảo', N'Trưởng phòng Kỹ thuật', N'Bắc Ninh'

    -- 5. PHỤ LỤC CÒN NHÁP: giá trị chưa đổi, nhưng phải thấy được là có cái đang treo.
    UNION ALL SELECT N'05/2026/HĐKT-POSTEF', N'Cung cấp cáp quang ADSS tuyến liên tỉnh',
           N'Cung cấp thiết bị', N'Bán', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 50 DAY), DATE_SUB(CURDATE(), INTERVAL 40 DAY),
           DATE_ADD(CURDATE(), INTERVAL 300 DAY),
           'KH-0004', 'sales4', N'Đang hiệu lực', 2000000000.00,
           N'Nguyễn Văn Bình', N'Phó Tổng Giám đốc', N'Vũ Hải Đăng', N'Tổng Giám đốc', N'Quảng Ninh'

    -- 6. HAI PHỤ LỤC ĐÃ KÝ, một cộng một trừ: 1,8 tỷ + 300tr - 120tr = 1,98 tỷ.
    UNION ALL SELECT N'06/2026/HĐKT-POSTEF', N'Thi công tuyến cáp quang Hạ Long - Cẩm Phả',
           N'Thi công lắp đặt', N'Bán', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 160 DAY), DATE_SUB(CURDATE(), INTERVAL 150 DAY),
           DATE_ADD(CURDATE(), INTERVAL 120 DAY),
           'KH-0005', 'sales4', N'Đang hiệu lực', 1800000000.00,
           N'Lê Thị Thanh', N'Giám đốc Kinh doanh', N'Hoàng Trọng Nghĩa', N'Phó Giám đốc', N'Quảng Ninh'

    -- 7. KỲ THU LỆCH GIÁ TRỊ: mới lập 500tr trên 750tr -- màn hình phải cảnh báo.
    UNION ALL SELECT N'07/2026/HĐKT-POSTEF', N'Cung cấp UPS EATON cho trung tâm dữ liệu',
           N'Cung cấp thiết bị', N'Bán', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 30 DAY), DATE_SUB(CURDATE(), INTERVAL 20 DAY),
           DATE_ADD(CURDATE(), INTERVAL 180 DAY),
           'KH-0006', 'sales5', N'Đang hiệu lực', 750000000.00,
           N'Nguyễn Văn Bình', N'Phó Tổng Giám đốc', N'Ngô Thị Vân', N'Giám đốc', N'Hà Nội'

    -- 8. ĐÃ THANH LÝ: đóng băng, không sửa và không lập phụ lục được nữa.
    UNION ALL SELECT N'08/2026/HĐKT-POSTEF', N'Cung cấp pin mặt trời cho trạm vùng sâu',
           N'Cung cấp thiết bị', N'Bán', N'Đã thanh lý',
           DATE_SUB(CURDATE(), INTERVAL 400 DAY), DATE_SUB(CURDATE(), INTERVAL 390 DAY),
           DATE_SUB(CURDATE(), INTERVAL 10 DAY),
           'KH-0007', 'sales4', N'Đã hết hạn', 450000000.00,
           N'Lê Thị Thanh', N'Giám đốc Kinh doanh', N'Đinh Công Tráng', N'Giám đốc', N'Phú Thọ'

    -- 9. CHẤM DỨT SỚM: theo lịch vẫn còn hiệu lực -- đúng chỗ hai trục lệch nhau.
    UNION ALL SELECT N'09/2026/HĐKT-POSTEF', N'Cung cấp dây thuê bao đệm chặt',
           N'Cung cấp thiết bị', N'Bán', N'Chấm dứt sớm',
           DATE_SUB(CURDATE(), INTERVAL 120 DAY), DATE_SUB(CURDATE(), INTERVAL 110 DAY),
           DATE_ADD(CURDATE(), INTERVAL 250 DAY),
           'KH-0008', 'sales5', N'Đang hiệu lực', 1100000000.00,
           N'Nguyễn Văn Bình', N'Phó Tổng Giám đốc', N'Tạ Quang Huy', N'Tổng Giám đốc', N'Thái Nguyên'

    -- 10. NHÁP CHƯA CHỐT GÌ: không thời hạn, không giá (V26 cho phép) -- và vì
    --     thế KHÔNG ký được. Bản nháp thật lúc mới soạn trông đúng như vậy.
    UNION ALL SELECT N'10/2026/HĐKT-POSTEF', N'Cung cấp thiết bị đo kiểm tuyến quang (đang đàm phán)',
           N'Cung cấp thiết bị', N'Bán', N'Nháp',
           NULL, NULL, NULL,
           'KH-0009', 'sales3', N'Chưa hiệu lực', NULL,
           NULL, NULL, NULL, NULL, NULL

    -- 11. NHÁP ĐÃ ĐỦ ĐIỀU KIỆN KÝ: có thời hạn, có giá -- bấm Ký là chạy.
    UNION ALL SELECT N'11/2026/HĐKT-POSTEF', N'Cung cấp cáp quang kéo cống cho khu công nghiệp',
           N'Cung cấp thiết bị', N'Bán', N'Nháp',
           NULL, DATE_ADD(CURDATE(), INTERVAL 15 DAY), DATE_ADD(CURDATE(), INTERVAL 380 DAY),
           'KH-0010', 'sales6', N'Chưa hiệu lực', 980000000.00,
           NULL, NULL, NULL, NULL, NULL

    -- 12. HẾT HẠN THEO LỊCH MÀ CHƯA THANH LÝ -- việc còn tồn, có cảnh báo riêng.
    UNION ALL SELECT N'12/2026/HĐKT-POSTEF', N'Bảo trì hệ thống UPS 2025-2026',
           N'Bảo trì bảo dưỡng', N'Bán', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 380 DAY), DATE_SUB(CURDATE(), INTERVAL 370 DAY),
           DATE_SUB(CURDATE(), INTERVAL 5 DAY),
           'KH-0011', 'sales6', N'Đã hết hạn', 1350000000.00,
           N'Lê Thị Thanh', N'Giám đốc Kinh doanh', N'Bùi Xuân Trường', N'Giám đốc', N'Thanh Hóa'

    -- 13. SẮP HẾT HẠN: rơi vào bảng "hợp đồng sắp hết hạn" ở Dashboard.
    UNION ALL SELECT N'13/2026/HĐKT-POSTEF', N'Thi công lắp đặt tuyến cáp treo nội thị',
           N'Thi công lắp đặt', N'Bán', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 200 DAY), DATE_SUB(CURDATE(), INTERVAL 190 DAY),
           DATE_ADD(CURDATE(), INTERVAL 12 DAY),
           'KH-0012', 'sales2', N'Sắp hết hạn', 820000000.00,
           N'Nguyễn Văn Bình', N'Phó Tổng Giám đốc', N'Lương Thế Vinh', N'Phó Tổng Giám đốc', N'Nghệ An'

    -- 14. Hợp đồng mang LINK DRIVE DEMO, và là hợp đồng duy nhất có UỶ QUYỀN:
    --     người ký bên khách không phải đại diện pháp luật, nên ô "Căn cứ uỷ
    --     quyền" mới có chỗ hiện ra (nó chỉ hiện khi có giá trị). Hai thứ đó
    --     điền ở câu UPDATE ngay bên dưới.
    UNION ALL SELECT N'14/2026/HĐKT-POSTEF', N'Cung cấp sợi quang G657A1 cho đại lý',
           N'Cung cấp thiết bị', N'Bán', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 45 DAY), DATE_SUB(CURDATE(), INTERVAL 35 DAY),
           DATE_ADD(CURDATE(), INTERVAL 330 DAY),
           'KH-0009', 'sales3', N'Đang hiệu lực', 1450000000.00,
           N'Nguyễn Văn Bình', N'Phó Tổng Giám đốc', N'Hoàng Văn Phúc', N'Trưởng phòng Mua hàng', N'Lạng Sơn'

    -- ===== Chiều MUA =====
    -- Đối tác là NHÀ CUNG CẤP (NCC-*, seed ở V28), loại hợp đồng lấy từ
    -- BUY_CONTRACT_TYPES, người phụ trách là sales1 -- nhóm mua vào không chia
    -- theo địa bàn nên không rải cho đội bán hàng theo tỉnh.
    -- 15. Mua vật tư đầu vào, đã ký, tiền trả theo hai kỳ.
    UNION ALL SELECT N'01/2026/HĐMB-POSTEF', N'Mua sợi quang và vật liệu chế tạo cáp',
           N'Mua vật tư', N'Mua', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 140 DAY), DATE_SUB(CURDATE(), INTERVAL 130 DAY),
           DATE_ADD(CURDATE(), INTERVAL 230 DAY),
           'NCC-001', 'sales1', N'Đang hiệu lực', 3200000000.00,
           N'Trần Đại Nghĩa', N'Giám đốc Cung ứng', N'Phan Văn Đông', N'Tổng Giám đốc', N'Hà Nội'

    -- 16. Mua thiết bị + PHỤ LỤC BỔ SUNG -- phụ lục không phải chuyện riêng của
    --     hợp đồng bán, nên bộ dữ liệu phải có ít nhất một cái ở chiều mua.
    UNION ALL SELECT N'02/2026/HĐMB-POSTEF', N'Mua module quang và thiết bị đo kiểm',
           N'Mua thiết bị', N'Mua', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 90 DAY), DATE_SUB(CURDATE(), INTERVAL 80 DAY),
           DATE_ADD(CURDATE(), INTERVAL 280 DAY),
           'NCC-002', 'sales1', N'Đang hiệu lực', 1600000000.00,
           N'Trần Đại Nghĩa', N'Giám đốc Cung ứng', N'Nguyễn Tân Tiến', N'Giám đốc', N'Hà Nội'

    -- 17. Nháp chiều mua: bộ lọc theo kỳ lọc trên signing_date nên bản nháp rơi
    --     ra ngoài -- đúng nghĩa, và cần có dữ liệu để nhìn thấy điều đó.
    UNION ALL SELECT N'03/2026/HĐMB-POSTEF', N'Thuê bảo trì hệ thống máy nén khí (đang đàm phán)',
           N'Thuê bảo trì', N'Mua', N'Nháp',
           NULL, DATE_ADD(CURDATE(), INTERVAL 30 DAY), DATE_ADD(CURDATE(), INTERVAL 395 DAY),
           'NCC-004', 'sales1', N'Chưa hiệu lực', 500000000.00,
           NULL, NULL, NULL, NULL, NULL
) x
JOIN enterprises e ON e.enterprise_code = x.kh
JOIN users u ON u.username = x.owner;

-- Uỷ quyền + link đính kèm: chỉ một hợp đồng, nên đặt riêng cho gọn.
-- LINK DEMO, CỐ Ý GIỮ: nó trỏ vào một PDF catalogue công khai trên Drive, dùng
-- để trình diễn tính năng đính kèm cho khách. Đừng "dọn" nó đi.
UPDATE contracts
   SET authorization_ref = N'GUQ số 05/2026 ngày 10/01/2026',
       attachment_url = 'https://drive.google.com/file/d/1lAoND44iEzSuLYnXEj7DfHNGJfHMDcBD/view'
 WHERE contract_code = N'14/2026/HĐKT-POSTEF';

-- ---------------------------------------------------------------------------
-- Phụ lục
-- ---------------------------------------------------------------------------
-- contract_value trên dòng phụ lục là CHÊNH LỆCH CÓ DẤU, không phải tổng giá
-- trị mới: dương = bổ sung, âm = giảm trừ, NULL = phụ lục không đụng tới tiền
-- (gia hạn, đổi hàng hoá). Giá trị hiện hành của hợp đồng gốc cộng lúc ĐỌC,
-- bản ghi cha giữ nguyên con số đã ký.
--
-- Đối tác và chiều LẤY TỪ CHA, đúng như ContractDAO.insert làm -- phụ lục
-- không ký với một đối tác khác, cũng không đổi từ bán sang mua.
INSERT INTO contracts
  (parent_contract_id, contract_code, title, contract_type, direction, progress_status,
   signing_date, effective_date, end_date, enterprise_id, owner_id, status, contract_value,
   signer_name, signer_position, counterparty_signer_name, counterparty_signer_position, signing_place)
SELECT p.contract_id, x.code, x.title, p.contract_type, p.direction, x.progress,
       x.sign_date, x.eff_date, x.end_date, p.enterprise_id, p.owner_id, x.status, x.value,
       p.signer_name, p.signer_position, p.counterparty_signer_name, p.counterparty_signer_position,
       p.signing_place
FROM (
    -- Bổ sung 250tr: 1,5 tỷ -> 1,75 tỷ.
    SELECT N'02/2026/HĐKT-POSTEF' parent, N'02/2026/HĐKT-POSTEF/PL01' code,
           N'Phụ lục 01 — bổ sung 8 tủ nguồn ngoài trời' title, N'Đã ký' progress,
           DATE_SUB(CURDATE(), INTERVAL 40 DAY) sign_date, DATE_SUB(CURDATE(), INTERVAL 35 DAY) eff_date,
           DATE_ADD(CURDATE(), INTERVAL 240 DAY) end_date, N'Đang hiệu lực' status, 250000000.00 value

    -- Giảm trừ 180tr: khách bỏ bớt hạng mục, 900tr -> 720tr.
    UNION ALL SELECT N'03/2026/HĐKT-POSTEF', N'03/2026/HĐKT-POSTEF/PL01',
           N'Phụ lục 01 — giảm trừ hạng mục ắc quy Gel', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 25 DAY), DATE_SUB(CURDATE(), INTERVAL 20 DAY),
           DATE_ADD(CURDATE(), INTERVAL 150 DAY), N'Đang hiệu lực', -180000000.00

    -- Gia hạn thuần: KHÔNG đổi tiền (value NULL) và KHÔNG kéo dài ngày kết thúc
    -- của hợp đồng cha -- thời hạn mới nằm trên chính phụ lục này.
    UNION ALL SELECT N'04/2026/HĐKT-POSTEF', N'04/2026/HĐKT-POSTEF/PL01',
           N'Phụ lục 01 — gia hạn bảo trì sang năm 2027', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 21 DAY),
           DATE_ADD(CURDATE(), INTERVAL 385 DAY), N'Chưa hiệu lực', NULL

    -- CÒN NHÁP: chưa ai ký nên 400tr này CHƯA cộng vào hợp đồng gốc.
    UNION ALL SELECT N'05/2026/HĐKT-POSTEF', N'05/2026/HĐKT-POSTEF/PL01',
           N'Phụ lục 01 — bổ sung 12km cáp ADSS (chờ ký)', N'Nháp',
           NULL, DATE_ADD(CURDATE(), INTERVAL 10 DAY),
           DATE_ADD(CURDATE(), INTERVAL 300 DAY), N'Chưa hiệu lực', 400000000.00

    -- Hai phụ lục trên cùng một hợp đồng: +300tr rồi -120tr.
    UNION ALL SELECT N'06/2026/HĐKT-POSTEF', N'06/2026/HĐKT-POSTEF/PL01',
           N'Phụ lục 01 — bổ sung 3km tuyến nhánh', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 80 DAY), DATE_SUB(CURDATE(), INTERVAL 75 DAY),
           DATE_ADD(CURDATE(), INTERVAL 120 DAY), N'Đang hiệu lực', 300000000.00
    UNION ALL SELECT N'06/2026/HĐKT-POSTEF', N'06/2026/HĐKT-POSTEF/PL02',
           N'Phụ lục 02 — giảm trừ phần hoàn trả mặt bằng', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 15 DAY), DATE_SUB(CURDATE(), INTERVAL 10 DAY),
           DATE_ADD(CURDATE(), INTERVAL 120 DAY), N'Đang hiệu lực', -120000000.00

    -- Phụ lục ở chiều MUA.
    UNION ALL SELECT N'02/2026/HĐMB-POSTEF', N'02/2026/HĐMB-POSTEF/PL01',
           N'Phụ lục 01 — bổ sung 40 module quang 10G', N'Đã ký',
           DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 15 DAY),
           DATE_ADD(CURDATE(), INTERVAL 280 DAY), N'Đang hiệu lực', 220000000.00
) x
JOIN contracts p ON p.contract_code = x.parent AND p.parent_contract_id IS NULL;

-- ---------------------------------------------------------------------------
-- Hạng mục hàng hoá
-- ---------------------------------------------------------------------------
INSERT INTO contractproducts (contract_id, product_id, quantity, unit, notes)
SELECT c.contract_id, p.product_id, x.qty, x.unit, x.note
FROM (
    SELECT N'01/2026/HĐKT-POSTEF' code, 'SP-0001' sp, 90 qty, N'Bình' unit, N'Ắc quy lithium 48V/100Ah' note
    UNION ALL SELECT N'01/2026/HĐKT-POSTEF', 'SP-0006', 45, N'Bộ', N'Nguồn POSTEF cho trạm BTS'
    UNION ALL SELECT N'02/2026/HĐKT-POSTEF', 'SP-0006', 30, N'Bộ', N'Nguồn POSTEF vùng lõi'
    UNION ALL SELECT N'02/2026/HĐKT-POSTEF', 'SP-0007', 30, N'Bộ', N'Nguồn UNIPOWER dự phòng'
    UNION ALL SELECT N'03/2026/HĐKT-POSTEF', 'SP-0003', 60, N'Bình', N'Ắc quy Gel'
    UNION ALL SELECT N'04/2026/HĐKT-POSTEF', 'SP-0008', 12, N'Bộ', N'UPS EATON trong phạm vi bảo trì'
    UNION ALL SELECT N'05/2026/HĐKT-POSTEF', 'SP-0010', 42000, N'Mét', N'Cáp ADSS tuyến liên tỉnh'
    UNION ALL SELECT N'06/2026/HĐKT-POSTEF', 'SP-0010', 25000, N'Mét', N'Cáp ADSS tuyến Hạ Long - Cẩm Phả'
    UNION ALL SELECT N'07/2026/HĐKT-POSTEF', 'SP-0008', 8, N'Bộ', N'UPS EATON cho trung tâm dữ liệu'
    UNION ALL SELECT N'08/2026/HĐKT-POSTEF', 'SP-0009', 120, N'Tấm', N'Pin mặt trời cho trạm vùng sâu'
    UNION ALL SELECT N'09/2026/HĐKT-POSTEF', 'SP-0014', 30000, N'Mét', N'Dây thuê bao đệm chặt'
    UNION ALL SELECT N'11/2026/HĐKT-POSTEF', 'SP-0012', 18000, N'Mét', N'Cáp quang kéo cống'
    UNION ALL SELECT N'12/2026/HĐKT-POSTEF', 'SP-0008', 20, N'Bộ', N'UPS trong phạm vi bảo trì'
    UNION ALL SELECT N'13/2026/HĐKT-POSTEF', 'SP-0013', 22000, N'Mét', N'Cáp quang treo kim loại'
    UNION ALL SELECT N'14/2026/HĐKT-POSTEF', 'SP-0013', 20000, N'Mét', N'Sợi quang G657A1'
    UNION ALL SELECT N'01/2026/HĐMB-POSTEF', 'SP-0011', 60000, N'Mét', N'Cáp quang bọc chặt mua vào'
    UNION ALL SELECT N'02/2026/HĐMB-POSTEF', 'SP-0012', 25000, N'Mét', N'Cáp quang kéo cống mua vào'
    -- Hàng hoá của phụ lục treo vào chính PHỤ LỤC, không vào hợp đồng gốc: nội
    -- dung hợp đồng đã ký là chứng cứ, phần bổ sung nằm ở văn bản sửa đổi.
    UNION ALL SELECT N'02/2026/HĐKT-POSTEF/PL01', 'SP-0006', 8, N'Bộ', N'Tủ nguồn ngoài trời bổ sung'
    UNION ALL SELECT N'06/2026/HĐKT-POSTEF/PL01', 'SP-0010', 3000, N'Mét', N'Tuyến nhánh bổ sung'
    UNION ALL SELECT N'02/2026/HĐMB-POSTEF/PL01', 'SP-0012', 4000, N'Mét', N'Phần mua thêm theo phụ lục'
) x
JOIN contracts c ON c.contract_code = x.code
JOIN products p ON p.product_code = x.sp;

-- ---------------------------------------------------------------------------
-- Kỳ thanh toán
-- ---------------------------------------------------------------------------
-- Ba tình huống cố ý khác nhau: khớp đúng giá trị hiện hành (đã tính cả phụ
-- lục), lập thiếu (màn hình cảnh báo lệch), và đã thu xong.
--
-- Kỳ thanh toán luôn DƯƠNG -- insertPayment từ chối số <= 0 -- nên một phụ lục
-- giảm trừ không sinh ra kỳ âm, nó làm NHỎ ĐI kỳ sẽ lập.
INSERT INTO contract_payments (contract_id, invoice_amount, due_date, paid_date)
SELECT c.contract_id, x.amount, x.due, x.paid
FROM (
    -- 01: 1,2 tỷ = 700tr (đã thu) + 500tr (chưa tới hạn)
    SELECT N'01/2026/HĐKT-POSTEF' code, 700000000.00 amount,
           DATE_SUB(CURDATE(), INTERVAL 30 DAY) due, DATE_SUB(CURDATE(), INTERVAL 28 DAY) paid
    UNION ALL SELECT N'01/2026/HĐKT-POSTEF', 500000000.00, DATE_ADD(CURDATE(), INTERVAL 60 DAY), NULL
    -- 02: KHỚP CẢ CỤM -- 1,5 tỷ của hợp đồng gốc + 250tr lập trên phụ lục.
    UNION ALL SELECT N'02/2026/HĐKT-POSTEF', 900000000.00, DATE_SUB(CURDATE(), INTERVAL 60 DAY), DATE_SUB(CURDATE(), INTERVAL 55 DAY)
    UNION ALL SELECT N'02/2026/HĐKT-POSTEF', 600000000.00, DATE_ADD(CURDATE(), INTERVAL 30 DAY), NULL
    UNION ALL SELECT N'02/2026/HĐKT-POSTEF/PL01', 250000000.00, DATE_ADD(CURDATE(), INTERVAL 90 DAY), NULL
    -- 03: 720tr sau giảm trừ -- kỳ thu lập đúng phần còn lại.
    UNION ALL SELECT N'03/2026/HĐKT-POSTEF', 720000000.00, DATE_ADD(CURDATE(), INTERVAL 20 DAY), NULL
    -- 06: 1,98 tỷ sau hai phụ lục (1,2 tỷ + 600tr + 180tr).
    UNION ALL SELECT N'06/2026/HĐKT-POSTEF', 1200000000.00, DATE_SUB(CURDATE(), INTERVAL 90 DAY), DATE_SUB(CURDATE(), INTERVAL 85 DAY)
    UNION ALL SELECT N'06/2026/HĐKT-POSTEF', 600000000.00, DATE_ADD(CURDATE(), INTERVAL 45 DAY), NULL
    UNION ALL SELECT N'06/2026/HĐKT-POSTEF/PL01', 180000000.00, DATE_ADD(CURDATE(), INTERVAL 60 DAY), NULL
    -- 07: LẬP THIẾU -- 500tr trên giá trị 750tr, cảnh báo phải hiện ra.
    UNION ALL SELECT N'07/2026/HĐKT-POSTEF', 500000000.00, DATE_ADD(CURDATE(), INTERVAL 15 DAY), NULL
    -- 08: đã thanh lý, tiền về đủ. Khoản bảo hành giữ lại về SAU thanh lý -- cố
    -- ý, vì ghi nhận tiền vẫn mở sau khi hợp đồng đóng băng.
    UNION ALL SELECT N'08/2026/HĐKT-POSTEF', 400000000.00, DATE_SUB(CURDATE(), INTERVAL 200 DAY), DATE_SUB(CURDATE(), INTERVAL 195 DAY)
    UNION ALL SELECT N'08/2026/HĐKT-POSTEF', 50000000.00, DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 3 DAY)
    -- 12: hết hạn mà chưa thanh lý, còn một kỳ quá hạn chưa thu.
    UNION ALL SELECT N'12/2026/HĐKT-POSTEF', 900000000.00, DATE_SUB(CURDATE(), INTERVAL 150 DAY), DATE_SUB(CURDATE(), INTERVAL 145 DAY)
    UNION ALL SELECT N'12/2026/HĐKT-POSTEF', 450000000.00, DATE_SUB(CURDATE(), INTERVAL 40 DAY), NULL
    -- 13: sắp hết hạn, đã thu xong.
    UNION ALL SELECT N'13/2026/HĐKT-POSTEF', 820000000.00, DATE_SUB(CURDATE(), INTERVAL 100 DAY), DATE_SUB(CURDATE(), INTERVAL 96 DAY)
    -- 14: 1,45 tỷ, một kỳ đã thu một kỳ chưa.
    UNION ALL SELECT N'14/2026/HĐKT-POSTEF', 1000000000.00, DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_SUB(CURDATE(), INTERVAL 8 DAY)
    UNION ALL SELECT N'14/2026/HĐKT-POSTEF', 450000000.00, DATE_ADD(CURDATE(), INTERVAL 80 DAY), NULL
    -- Chiều MUA: đây là tiền mình đi TRẢ, nên không được vào doanh thu (câu
    -- tính doanh thu lọc direction = 'Bán'); có dữ liệu ở đây để thấy điều đó.
    UNION ALL SELECT N'01/2026/HĐMB-POSTEF', 2000000000.00, DATE_SUB(CURDATE(), INTERVAL 90 DAY), DATE_SUB(CURDATE(), INTERVAL 88 DAY)
    UNION ALL SELECT N'01/2026/HĐMB-POSTEF', 1200000000.00, DATE_ADD(CURDATE(), INTERVAL 40 DAY), NULL
    UNION ALL SELECT N'02/2026/HĐMB-POSTEF', 1600000000.00, DATE_SUB(CURDATE(), INTERVAL 30 DAY), DATE_SUB(CURDATE(), INTERVAL 25 DAY)
    UNION ALL SELECT N'02/2026/HĐMB-POSTEF/PL01', 220000000.00, DATE_ADD(CURDATE(), INTERVAL 50 DAY), NULL
) x
JOIN contracts c ON c.contract_code = x.code;

-- ---------------------------------------------------------------------------
-- Nhật ký hợp đồng
-- ---------------------------------------------------------------------------
-- Sinh theo QUY TẮC chứ không chép tay từng dòng: đây đúng là những dòng mà
-- ContractDAO tự ghi khi các việc ấy diễn ra thật, nên viết theo quy tắc thì
-- dữ liệu demo không kể một câu chuyện khác với thứ hệ thống sinh ra.

-- 1) Khởi tạo -- mọi hợp đồng đều có.
INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at)
SELECT c.contract_id, N'Khởi tạo',
       CASE WHEN c.parent_contract_id IS NULL
            THEN CONCAT(N'Tạo bản nháp ', c.contract_code, N' — hợp đồng ', LOWER(c.direction))
            ELSE CONCAT(N'Tạo bản nháp phụ lục ', c.contract_code, N' — sửa đổi cho hợp đồng ',
                        (SELECT p.contract_code FROM contracts p WHERE p.contract_id = c.parent_contract_id))
       END,
       c.owner_id,
       COALESCE(c.signing_date - INTERVAL 7 DAY, CURDATE() - INTERVAL 7 DAY)
FROM contracts c;

-- 2) Ký hợp đồng -- mọi bản ghi không còn là bản nháp.
INSERT INTO contract_history (contract_id, event_type, detail, from_status, to_status, changed_by, changed_at)
SELECT c.contract_id, N'Ký hợp đồng', N'Nháp → Đã ký', N'Nháp', N'Đã ký', c.owner_id, c.signing_date
FROM contracts c
WHERE c.progress_status <> N'Nháp' AND c.signing_date IS NOT NULL;

-- 3) Lập phụ lục -- ghi lên hợp đồng CHA, vì đó là chỗ người ta đi tìm.
INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at)
SELECT c.parent_contract_id, N'Lập phụ lục',
       CONCAT(N'Lập phụ lục ', c.contract_code, N' — ', c.title),
       c.owner_id, COALESCE(c.signing_date - INTERVAL 7 DAY, CURDATE() - INTERVAL 7 DAY)
FROM contracts c
WHERE c.parent_contract_id IS NOT NULL;

-- 4) Điều chỉnh giá trị -- chỉ phụ lục ĐÃ KÝ và có tiền. Phụ lục còn nháp không
--    sinh dòng này: chưa ai ký thì giá trị hợp đồng chưa đổi.
--    Con số "hiện hành" cộng luỹ kế tới đúng phụ lục đó, nên hợp đồng có hai
--    phụ lục để lại hai dòng kể đúng thứ tự tiền đã đổi.
INSERT INTO contract_history (contract_id, event_type, detail, changed_by, changed_at)
SELECT c.parent_contract_id, N'Điều chỉnh giá trị',
       CONCAT(N'Phụ lục ', c.contract_code, N' được ký — điều chỉnh ',
              CASE WHEN c.contract_value > 0 THEN '+' ELSE '' END,
              REPLACE(FORMAT(c.contract_value, 0), ',', '.'), N' đ, giá trị hợp đồng hiện hành ',
              REPLACE(FORMAT(p.contract_value + (
                  SELECT COALESCE(SUM(s.contract_value), 0) FROM contracts s
                  WHERE s.parent_contract_id = p.contract_id AND s.is_deleted = 0
                    AND s.progress_status <> N'Nháp' AND s.contract_id <= c.contract_id), 0), ',', '.'),
              N' đ (giá trị theo bản gốc đã ký: ',
              REPLACE(FORMAT(p.contract_value, 0), ',', '.'), N' đ)'),
       c.owner_id, c.signing_date
FROM contracts c
JOIN contracts p ON p.contract_id = c.parent_contract_id
WHERE c.progress_status <> N'Nháp'
  AND c.contract_value IS NOT NULL AND c.contract_value <> 0;

-- 5) Thanh lý / chấm dứt sớm -- căn cứ (số biên bản) nằm ở cột note, đúng chỗ
--    ContractDAO.changeProgressStatus đặt nó. KHÔNG có cột liquidated_at riêng:
--    chính dòng này đã mang người làm và thời điểm.
INSERT INTO contract_history (contract_id, event_type, detail, from_status, to_status, note, changed_by, changed_at)
SELECT c.contract_id, N'Thanh lý', N'Đã ký → Đã thanh lý', N'Đã ký', N'Đã thanh lý',
       N'Biên bản thanh lý số 12/2026/BBTL, nghiệm thu đợt cuối',
       c.owner_id, CURDATE() - INTERVAL 8 DAY
FROM contracts c WHERE c.progress_status = N'Đã thanh lý';

INSERT INTO contract_history (contract_id, event_type, detail, from_status, to_status, note, changed_by, changed_at)
SELECT c.contract_id, N'Chấm dứt sớm', N'Đã ký → Chấm dứt sớm', N'Đã ký', N'Chấm dứt sớm',
       N'Hai bên thống nhất dừng do khách thay đổi quy hoạch tuyến',
       c.owner_id, CURDATE() - INTERVAL 30 DAY
FROM contracts c WHERE c.progress_status = N'Chấm dứt sớm';

-- ---------------------------------------------------------------------------
-- Nối lại phiếu hỗ trợ
-- ---------------------------------------------------------------------------
-- Phiếu hỗ trợ giữ nguyên, nhưng contract_id của chúng vừa bị cắt ở đầu file.
-- Nối lại theo ĐÚNG KHÁCH của phiếu, và chỉ vào hợp đồng gốc đã ký chiều BÁN --
-- phiếu bảo hành treo vào một bản nháp, một phụ lục, hay vào hợp đồng mua vào
-- thì đều vô nghĩa. Khách nào không còn hợp đồng bán nào thì phiếu để trống,
-- đúng như cột này vốn cho phép (nullable).
UPDATE technicalrequests t
SET t.contract_id = (
    SELECT c.contract_id FROM contracts c
    WHERE c.enterprise_id = t.enterprise_id
      AND c.direction = N'Bán'
      AND c.parent_contract_id IS NULL
      AND c.progress_status <> N'Nháp'
      AND c.is_deleted = 0
    ORDER BY c.signing_date DESC
    LIMIT 1);
