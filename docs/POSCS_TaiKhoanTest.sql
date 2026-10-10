-- =====================================================================
-- POSCS -- Dữ liệu nhân viên phục vụ kiểm thử
--
-- Thêm 12 tài khoản (11 Active + 1 Inactive để kiểm thử khoá/mở khoá),
-- trải đều 3 vai Sales/Kỹ thuật/CSKH. Cố ý không seed tài khoản Admin
-- nào -- vai Admin dùng tài khoản `admin` có sẵn.
--
-- KHÔNG đụng tới 4 tài khoản có từ trước (admin / sales1 / kythuat1 / cskh1).
--
-- Mật khẩu của cả 12 tài khoản: Poscs@123
-- (băm bằng chính jbcrypt-0.4.jar của dự án, cost 10, mỗi tài khoản
--  một salt riêng -- không phải cùng một chuỗi băm dán lại)
--
-- Chạy lại nhiều lần vẫn an toàn: INSERT IGNORE bỏ qua dòng đã tồn tại.
-- =====================================================================

INSERT IGNORE INTO users
  (username, email, password_hash, role_id, last_name, middle_name, first_name,
   gender, date_of_birth, citizen_id, phone, personal_email, address_id,
   department_id, hire_date, is_deleted)
VALUES
-- ===== Sales (role_id = 2, Kinh doanh) =====
('sales2', 'sales2@postef.com.vn', '$2a$10$1mygDKbqiqYHkJ0OX.vHBu4oSMPVlOZiD/UOE./KJe30DcikN1MQm',
 2, 'Trần', 'Thị Thu', 'Hà', 'Nữ', '1993-07-22', '001193012346', '0912340002',
 'sales2.test@example.com', NULL, 2, '2022-05-16', 0),
('sales3', 'sales3@postef.com.vn', '$2a$10$ox8uuXUmviVjlmgALZyQ5u1A0kx7MjgbM2CA9FP6d2LaPcC3Rsr3u',
 2, 'Lê', 'Minh', 'Quân', 'Nam', '1990-02-08', '001190012347', '0912340003',
 'sales3.test@example.com', NULL, 2, '2021-09-06', 0),
('sales4', 'sales4@postef.com.vn', '$2a$10$HSkix7iRJKzHyJufYdPmN.0EKySML96Q9jabKDWIJX1BX9yUPf6jC',
 2, 'Phạm', 'Thị Ngọc', 'Anh', 'Nữ', '1995-11-30', '001195012348', '0912340004',
 'sales4.test@example.com', NULL, 2, '2023-02-13', 0),
('sales5', 'sales5@postef.com.vn', '$2a$10$NmhSfBBkCmgjp/D1We/qIOmJdX8T7dpsqfttAp3b76pJFNtiOm1Iy',
 2, 'Đỗ', 'Hoàng', 'Long', 'Nam', '1988-09-15', '001188012349', '0912340005',
 'sales5.test@example.com', NULL, 2, '2020-11-02', 0),

-- Tài khoản CHỦ ĐÍCH để ở trạng thái Inactive: dùng để kiểm thử bộ lọc
-- trạng thái ở danh sách nhân viên, luồng Mở khoá (UC-29), và thông báo
-- "tài khoản bị khoá" ở màn đăng nhập.
('sales6', 'sales6@postef.com.vn', '$2a$10$OLJACVeFg8NV7j2D5VCx0uCjKgaYaCvJl4egHBQxSEnhf.9IGb.Ta',
 2, 'Vũ', 'Thị Bích', 'Ngọc', 'Nữ', '1996-05-03', '001196012350', '0912340006',
 'sales6.test@example.com', NULL, 2, '2023-08-21', 1),

-- ===== Kỹ thuật (role_id = 3, Kỹ thuật) =====
('kythuat2', 'kythuat2@postef.com.vn', '$2a$10$DAGqjm1tkfcQwNW/Eg4heea3pcFZjnBx61.02OXXc5k2.wlOo/SN2',
 3, 'Vũ', 'Đình', 'Nam', 'Nam', '1991-03-19', '001191012351', '0912340007',
 'kythuat2.test@example.com', NULL, 3, '2021-06-14', 0),
('kythuat3', 'kythuat3@postef.com.vn', '$2a$10$sZGBam7M3NbxjvgR4t6RAOPIDXvqnM0UT9Yy4DtshErcoiGwUSUxS',
 3, 'Hoàng', 'Thị', 'Lan', 'Nữ', '1994-12-05', '001194012352', '0912340008',
 'kythuat3.test@example.com', NULL, 3, '2022-10-03', 0),
('kythuat4', 'kythuat4@postef.com.vn', '$2a$10$RoLmNuQu9Ip6Zsu86mf0uOiPm9xokvqtK57spfCn0Dexw/45Ro1qy',
 3, 'Bùi', 'Tiến', 'Dũng', 'Nam', '1989-06-27', '001189012353', '0912340009',
 'kythuat4.test@example.com', NULL, 3, '2020-04-20', 0),
('kythuat5', 'kythuat5@postef.com.vn', '$2a$10$ClxCdcFUyHKrbyQHYG0yA.5.wx.B8uE.3G8zavmgr8gO4qdL61OfS',
 3, 'Ngô', 'Văn', 'Hiếu', 'Nam', '1997-01-14', '001197012354', '0912340010',
 'kythuat5.test@example.com', NULL, 3, '2024-01-08', 0),

-- ===== CSKH (role_id = 4, Chăm sóc khách hàng) =====
('cskh2', 'cskh2@postef.com.vn', '$2a$10$7NMKL70QjZZ4oRdU3qlTguMawMGrf6AbZsC0GmvWwD61EUAISbzMu',
 4, 'Đặng', 'Thị Mai', 'Phương', 'Nữ', '1992-08-09', '001192012355', '0912340011',
 'cskh2.test@example.com', NULL, 4, '2022-03-07', 0),
('cskh3', 'cskh3@postef.com.vn', '$2a$10$Mm5Z3WSoEkUuvLQyPKg9fOEky1oqSg9Pc1ycSnniJhM0X.HMRjDhS',
 4, 'Lý', 'Quốc', 'Cường', 'Nam', '1990-10-21', '001190012356', '0912340012',
 'cskh3.test@example.com', NULL, 4, '2021-12-13', 0),
('cskh4', 'cskh4@postef.com.vn', '$2a$10$0qHqBjhyHxqLRkROcpud4eXVu9EsH/Qw9WWpiElq3pfeczYSQtzmC',
 4, 'Trịnh', 'Thị Hồng', 'Nhung', 'Nữ', '1998-04-02', '001198012357', '0912340013',
 'cskh4.test@example.com', NULL, 4, '2024-06-17', 0);

-- Kiểm tra kết quả
SELECT u.user_id, u.username, u.email, r.role_name, d.department_name,
       CASE u.is_deleted WHEN 0 THEN 'Active' ELSE 'Inactive' END AS trang_thai
FROM users u
JOIN roles r ON r.role_id = u.role_id
JOIN departments d ON d.department_id = u.department_id
ORDER BY u.role_id, u.user_id;
