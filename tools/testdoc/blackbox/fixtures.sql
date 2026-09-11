-- Tài khoản mẫu cho kiểm thử hộp đen. Chạy TRƯỚC mỗi lượt chạy tự động.
--
-- Bắt buộc chạy lại mỗi lượt vì bộ test có các ca đổi mật khẩu thật
-- (TC_CHGPWD_001, TC_RESET_001) và khoá tài khoản thật (TC_EMPBAN_001) --
-- chạy xong thì mật khẩu trong CSDL khác hằng số trong run_blackbox.py, lượt
-- sau đăng nhập không được và mọi ca phía sau trượt oan.
--
-- Các id 1, 15, 16, 17 là id mà dữ liệu mẫu của db/schema.sql đang tham chiếu
-- (enterprises.account_owner_id, contracts.owner_id,
-- technicalrequests.assigned_technician_id / created_by), nên phải giữ đúng.
--
-- Mật khẩu: admin Admin@123 | sale01 Sales@123 | tech01, tech02 Tech@123
--           cskh01 Cskh@123 | locked01 Sales@123 (tài khoản đang bị khoá)

SET NAMES utf8mb4;

INSERT INTO users (user_id, username, email, password_hash, role_id, last_name,
                   middle_name, first_name, gender, date_of_birth, citizen_id,
                   phone, personal_email, department_id, hire_date, is_deleted)
VALUES
 (1,  'admin',   'admin@poscs.vn',   '$2a$10$nCKNnrIKggQ57ZBBItt.1.iPzP0QDo2eQQzpzpx/DO.wfZQ8VWJZO', 1,
  'Nguyễn', 'Quản', 'Trị',   'Nam', '1990-01-01', '001090000001', '0900000001', 'admin.poscs@gmail.com',   1, '2020-01-01', 0),
 (15, 'sale01',  'sale01@poscs.vn',  '$2a$10$nh994T46AJnTihHfc3XhMOfIxlaEZFiXlTtLDYSwbBhQEevc5tmDO', 2,
  'Trần',   'Kinh', 'Doanh', 'Nữ',  '1995-05-05', '001095000015', '0900000015', 'sale01.poscs@gmail.com',  2, '2021-03-01', 0),
 (16, 'tech01',  'tech01@poscs.vn',  '$2a$10$sEC3JV6/mkawBgoI1u7sCu7RXoCbXnmgaLNiGVpx9z/n6zd3KkTCm', 3,
  'Lê',     'Kỹ',   'Thuật', 'Nam', '1993-07-07', '001093000016', '0900000016', 'tech01.poscs@gmail.com',  3, '2021-06-01', 0),
 (17, 'cskh01',  'cskh01@poscs.vn',  '$2a$10$wPbenX5nXj.HsE.X4nIxx.VrfynOX4PYRRsq073G5UWG6PoSGqOUy', 4,
  'Phạm',   'Chăm', 'Sóc',   'Nữ',  '1996-09-09', '001096000017', '0900000017', 'cskh01.poscs@gmail.com',  4, '2022-01-01', 0),
 (18, 'tech02',  'tech02@poscs.vn',  '$2a$10$sEC3JV6/mkawBgoI1u7sCu7RXoCbXnmgaLNiGVpx9z/n6zd3KkTCm', 3,
  'Vũ',     'Kỹ',   'Thuật', 'Nam', '1994-02-02', '001094000018', '0900000018', 'tech02.poscs@gmail.com',  3, '2022-02-01', 0),
 (19, 'locked01','locked01@poscs.vn','$2a$10$nh994T46AJnTihHfc3XhMOfIxlaEZFiXlTtLDYSwbBhQEevc5tmDO', 2,
  'Đỗ',     'Bị',   'Khoá',  'Nam', '1992-04-04', '001092000019', '0900000019', 'locked01.poscs@gmail.com',2, '2022-04-01', 1)
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  role_id       = VALUES(role_id),
  is_deleted    = VALUES(is_deleted),
  personal_email= VALUES(personal_email);
