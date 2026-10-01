-- Tài khoản mẫu cho kiểm thử hộp đen. Chạy TRƯỚC mỗi lượt chạy tự động.
--
-- Bắt buộc chạy lại mỗi lượt vì bộ test có các ca đổi mật khẩu thật
-- (TC_CHGPWD_001, TC_RESET_001) và khoá tài khoản thật (TC_EMPBAN_001) --
-- chạy xong thì mật khẩu trong CSDL khác hằng số trong run_blackbox.py, lượt
-- sau đăng nhập không được và mọi ca phía sau trượt oan.
--
-- Từ lần cập nhật 2026-09-30:
--   * db/schema.sql đã tự gieo 12 nhân viên (sales2..6, kythuat2..5, cskh2..4)
--     với user_id tự tăng, và dữ liệu demo tra người dùng theo USERNAME -- nên
--     ở đây KHÔNG cố định user_id nữa (chèn id 1 là đè lên sales2). Khoá để
--     chèn/cập nhật là username (UNIQUE).
--   * Không còn cột email công ty (V36), không còn vai / phòng CSKH (V37, V38):
--     vai và phòng tra theo tên, không ghi cứng id.
--   * must_change_password = 0 và đủ bốn ô cá nhân, để AuthenticationFilter
--     không ép tài khoản test sang trang đổi mật khẩu / hồ sơ (V35).
--
-- Mật khẩu: admin Admin@123 | sale01, locked01, doimk01 Sales@123 | tech01, tech02 Tech@123
-- Tài khoản gieo sẵn trong schema.sql (sales2..5, kythuat2..5, cskh2..4): Poscs@123

SET NAMES utf8mb4;

INSERT INTO users (username, password_hash, must_change_password, role_id, last_name,
                   middle_name, first_name, gender, date_of_birth, citizen_id,
                   phone, personal_email, department_id, hire_date, is_deleted)
VALUES
 ('admin',    '$2a$10$nCKNnrIKggQ57ZBBItt.1.iPzP0QDo2eQQzpzpx/DO.wfZQ8VWJZO', 0,
  (SELECT role_id FROM roles WHERE role_name = 'Admin'),
  'Nguyễn', 'Quản', 'Trị',   'Nam', '1990-01-01', '001090000001', '0900000001', 'admin.poscs@gmail.com',
  (SELECT department_id FROM departments WHERE department_name = 'Ban giám đốc'), '2020-01-01', 0),
 ('sale01',   '$2a$10$nh994T46AJnTihHfc3XhMOfIxlaEZFiXlTtLDYSwbBhQEevc5tmDO', 0,
  (SELECT role_id FROM roles WHERE role_name = 'Sales'),
  'Trần',   'Kinh', 'Doanh', 'Nữ',  '1995-05-05', '001095000015', '0900000015', 'sale01.poscs@gmail.com',
  (SELECT department_id FROM departments WHERE department_name = 'Kinh doanh'), '2021-03-01', 0),
 ('tech01',   '$2a$10$sEC3JV6/mkawBgoI1u7sCu7RXoCbXnmgaLNiGVpx9z/n6zd3KkTCm', 0,
  (SELECT role_id FROM roles WHERE role_name = 'Kỹ thuật'),
  'Lê',     'Kỹ',   'Thuật', 'Nam', '1993-07-07', '001093000016', '0900000016', 'tech01.poscs@gmail.com',
  (SELECT department_id FROM departments WHERE department_name = 'Kỹ thuật'), '2021-06-01', 0),
 ('tech02',   '$2a$10$sEC3JV6/mkawBgoI1u7sCu7RXoCbXnmgaLNiGVpx9z/n6zd3KkTCm', 0,
  (SELECT role_id FROM roles WHERE role_name = 'Kỹ thuật'),
  'Vũ',     'Kỹ',   'Thuật', 'Nam', '1994-02-02', '001094000018', '0900000018', 'tech02.poscs@gmail.com',
  (SELECT department_id FROM departments WHERE department_name = 'Kỹ thuật'), '2022-02-01', 0),
 ('locked01', '$2a$10$nh994T46AJnTihHfc3XhMOfIxlaEZFiXlTtLDYSwbBhQEevc5tmDO', 0,
  (SELECT role_id FROM roles WHERE role_name = 'Sales'),
  'Đỗ',     'Bị',   'Khoá',  'Nam', '1992-04-04', '001092000019', '0900000019', 'locked01.poscs@gmail.com',
  (SELECT department_id FROM departments WHERE department_name = 'Kinh doanh'), '2022-04-01', 1),
 -- Riêng cho các ca đổi mật khẩu (TC_CHGPWD_*): đổi thật mật khẩu của tài
 -- khoản này, nên đừng dùng nó cho việc gì khác trong cùng lượt.
 ('doimk01',  '$2a$10$nh994T46AJnTihHfc3XhMOfIxlaEZFiXlTtLDYSwbBhQEevc5tmDO', 0,
  (SELECT role_id FROM roles WHERE role_name = 'Sales'),
  'Hồ',     'Đổi',  'Mật',   'Nữ',  '1997-03-03', '001097000020', '0900000020', 'doimk01.poscs@gmail.com',
  (SELECT department_id FROM departments WHERE department_name = 'Kinh doanh'), '2023-01-01', 0)
ON DUPLICATE KEY UPDATE
  password_hash        = VALUES(password_hash),
  must_change_password = 0,
  role_id              = VALUES(role_id),
  department_id        = VALUES(department_id),
  is_deleted           = VALUES(is_deleted),
  personal_email       = VALUES(personal_email),
  manager_id           = NULL;

-- Tài khoản gieo sẵn trong schema.sql: đặt lại trạng thái mà các ca tự động
-- làm thay đổi (khoá / mở khoá, cấp trên trong ca Người hỗ trợ).
UPDATE users SET is_deleted = 0, manager_id = NULL, must_change_password = 0
 WHERE username IN ('sales2', 'sales3', 'sales4', 'sales5', 'kythuat2', 'kythuat3', 'cskh2');
