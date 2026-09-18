-- Gieo phân công địa bàn cho đội Sales: ai cầm tỉnh nào.
--
-- Bảng user_provinces dựng từ V19 nhưng CHƯA BAO GIỜ có một dòng nào. Cho tới
-- lúc này không sao, vì nơi duy nhất đọc nó là gợi ý điền sẵn người phụ trách
-- lúc thêm khách -- trống thì chỉ mất gợi ý.
--
-- Giờ thì khác: danh sách khách hàng và hợp đồng đã thu hẹp mặc định về "phần
-- việc của tôi", mà một vế của phép đó là ĐỊA BÀN. Bảng trống nghĩa là vế đó
-- không bao giờ khớp gì, tức là tính năng chạy nhưng không ai nhìn thấy nó
-- làm gì. Không gieo thì phần vừa làm không kiểm chứng được trên bản dựng sạch.
--
-- (Mã đã phòng sẵn chuyện bảng trống: chưa được giao tỉnh thì KHÔNG siết theo
-- địa bàn, chỉ lọc theo người phụ trách -- xem ListScope. Nếu không thì mọi
-- nhân viên mở danh sách ra thấy 0 dòng.)
--
-- CHIA TRỌN 18 TỈNH ĐỊA BÀN, không để tỉnh nào trống. Danh sách 18 tỉnh này là
-- AddressDAO.BRANCH_PROVINCE_NAMES; tỉnh ngoài danh sách đó không phải địa bàn
-- bán hàng nên không gán cho ai.
--
-- MỘT TỈNH CHỈ MỘT NGƯỜI -- đó là UNIQUE trên province_id từ V19, và cũng là
-- quy tắc đã chốt với khách hàng. Một người vẫn cầm được nhiều tỉnh.
--
-- CỐ Ý: Bắc Ninh giao cho sales4, trong khi khách ở Bắc Ninh (Điện tử Kinh Bắc)
-- lại do sales3 đứng tên. Đây là ca dựng sẵn để kiểm vế địa bàn -- sales4 phải
-- thấy khách đó dù không phụ trách. Đừng "dọn" cho gọn: bỏ nó đi thì mọi tỉnh
-- trùng khít với chủ sở hữu, và bộ lọc địa bàn chạy hay không cũng ra cùng một
-- kết quả, tức là không kiểm được gì.
--
-- KHÔNG GIAO TỈNH CHO sales6. Tài khoản đó đang is_deleted = 1 ở CẢ schema.sql
-- lẫn poscs_db, mà vẫn còn đứng tên 2 khách đang hoạt động -- dữ liệu mẫu vốn
-- thế, không phải do file này. Ba tỉnh của họ (Ninh Bình, Nghệ An, Điện Biên)
-- chuyển sang sales5 -- liền kề Thanh Hóa sẵn có, và để 2 khách kia vào địa bàn
-- của MỘT người đang làm thay vì không thuộc về ai. Đây cũng thành ca thứ hai
-- kiểm vế địa bàn: sales5 thấy chúng dù không phụ trách.

-- KHÔNG PHẢI BẢN DỰNG NÀO CŨNG ĐỦ 18 DÒNG, và đó là đúng:
--   * sales1 do V12 gieo   -> bản dựng thuần từ schema.sql chưa có
-- Nên bản sạch ra 17 dòng, còn poscs_db ra đủ 18. Bốn tỉnh không ai cầm thì
-- đơn giản là chưa thuộc địa bàn của ai -- không phải lỗi. Đừng bù bằng cách gán
-- sang người khác: cùng một tỉnh thuộc hai người khác nhau tùy môi trường thì
-- không ai đọc ra được nữa.
--
-- KHÔNG GHI CỨNG MỘT ID NÀO -- tra qua username và province_name, cùng lối với
-- V30. CSDL nào thiếu tham chiếu (bản dựng thuần từ schema.sql chưa có sales1
-- vì tài khoản đó do V12 gieo) thì dòng ấy tự rơi ra, không làm hỏng lần chạy.

INSERT INTO schema_migrations (version) VALUES ('V33__seed_user_provinces__ndat2003');

-- Xoá trước rồi gieo lại, để file này chạy được nhiều lần mà không vấp UNIQUE.
-- An toàn vì bảng chỉ chứa phân công, không chứa dữ liệu nghiệp vụ nào khác.
DELETE FROM user_provinces;

INSERT INTO user_provinces (user_id, province_id)
SELECT u.user_id, p.province_id
FROM users u
JOIN provinces p ON (
        (u.username = 'sales1' AND p.province_name IN ('Tỉnh Lai Châu'))
     OR (u.username = 'sales2' AND p.province_name IN ('Thành phố Hải Phòng', 'Tỉnh Thái Nguyên', 'Tỉnh Cao Bằng'))
     OR (u.username = 'sales3' AND p.province_name IN ('Tỉnh Lạng Sơn', 'Tỉnh Tuyên Quang', 'Tỉnh Hà Tĩnh'))
     OR (u.username = 'sales4' AND p.province_name IN ('Thành phố Hà Nội', 'Tỉnh Quảng Ninh', 'Tỉnh Phú Thọ', 'Tỉnh Lào Cai', 'Tỉnh Bắc Ninh'))
     OR (u.username = 'sales5' AND p.province_name IN ('Tỉnh Hưng Yên', 'Tỉnh Thanh Hóa', 'Tỉnh Sơn La',
                                                      'Tỉnh Ninh Bình', 'Tỉnh Nghệ An', 'Tỉnh Điện Biên'))
)
WHERE u.is_deleted = 0;
