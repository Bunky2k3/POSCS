-- Seeds 12 employee accounts across the three non-Admin roles, so a fresh setup has enough users to exercise things a single account per role cannot: assignee/owner dropdowns and filters, ticket assignment, the employee list's pagination (PAGE_SIZE = 10 -> 2 pages), the role filter, and the Active/Inactive status filter. `sales6` is seeded Inactive on purpose to cover the Inactive filter, the Unban flow (UC-29), and the "account locked" message on the login screen.
--
-- No Admin account is seeded here, deliberately: this repository is public and
-- every account below shares one password, so seeding one would publish a
-- working administrator login. Create Admin accounts by hand instead.
--
-- !!! DEV/TEST DATA ONLY !!!
-- Every account below shares the password "Poscs@123". Never apply this
-- migration to an internet-facing deployment -- it would hand anyone reading
-- this repository a working login. Local dev databases and CI only.

INSERT INTO schema_migrations (version) VALUES ('V12__seed_test_user_accounts__ndat2003');

-- role_id/department_id are looked up by name rather than hardcoded: both
-- tables are AUTO_INCREMENT seeds (V2 and V10), and V10 additionally back-fills
-- departments from whatever free text already existed in `users`, so the ids
-- are not guaranteed to be 1-4 on a database that had employees before V10.
--
-- Hashes are bcrypt (cost 10, one salt each) of "Poscs@123", produced with the
-- project's own lib/jbcrypt-0.4.jar -- the same library AuthenticationController
-- verifies against.
--
-- INSERT IGNORE: `username`, `email`, `citizen_id` and `phone` are all UNIQUE,
-- so re-running this on a database that already has these rows is a no-op
-- instead of a duplicate-key error.
INSERT IGNORE INTO users
  (username, email, password_hash, role_id, last_name, middle_name, first_name,
   gender, date_of_birth, citizen_id, phone, personal_email, address_id,
   department_id, hire_date, is_deleted)
VALUES
-- ===== Sales =====
('sales2', 'sales2@postef.com.vn', '$2a$10$1mygDKbqiqYHkJ0OX.vHBu4oSMPVlOZiD/UOE./KJe30DcikN1MQm',
 (SELECT role_id FROM roles WHERE role_name = 'Sales'),
 'Trần', 'Thị Thu', 'Hà', 'Nữ', '1993-07-22', '001193012346', '0912340002',
 'sales2.test@example.com', NULL,
 (SELECT department_id FROM departments WHERE department_name = 'Kinh doanh'), '2022-05-16', 0),
('sales3', 'sales3@postef.com.vn', '$2a$10$ox8uuXUmviVjlmgALZyQ5u1A0kx7MjgbM2CA9FP6d2LaPcC3Rsr3u',
 (SELECT role_id FROM roles WHERE role_name = 'Sales'),
 'Lê', 'Minh', 'Quân', 'Nam', '1990-02-08', '001190012347', '0912340003',
 'sales3.test@example.com', NULL,
 (SELECT department_id FROM departments WHERE department_name = 'Kinh doanh'), '2021-09-06', 0),
('sales4', 'sales4@postef.com.vn', '$2a$10$HSkix7iRJKzHyJufYdPmN.0EKySML96Q9jabKDWIJX1BX9yUPf6jC',
 (SELECT role_id FROM roles WHERE role_name = 'Sales'),
 'Phạm', 'Thị Ngọc', 'Anh', 'Nữ', '1995-11-30', '001195012348', '0912340004',
 'sales4.test@example.com', NULL,
 (SELECT department_id FROM departments WHERE department_name = 'Kinh doanh'), '2023-02-13', 0),
('sales5', 'sales5@postef.com.vn', '$2a$10$NmhSfBBkCmgjp/D1We/qIOmJdX8T7dpsqfttAp3b76pJFNtiOm1Iy',
 (SELECT role_id FROM roles WHERE role_name = 'Sales'),
 'Đỗ', 'Hoàng', 'Long', 'Nam', '1988-09-15', '001188012349', '0912340005',
 'sales5.test@example.com', NULL,
 (SELECT department_id FROM departments WHERE department_name = 'Kinh doanh'), '2020-11-02', 0),

-- Seeded Inactive (is_deleted = 1) on purpose -- see the header comment.
('sales6', 'sales6@postef.com.vn', '$2a$10$OLJACVeFg8NV7j2D5VCx0uCjKgaYaCvJl4egHBQxSEnhf.9IGb.Ta',
 (SELECT role_id FROM roles WHERE role_name = 'Sales'),
 'Vũ', 'Thị Bích', 'Ngọc', 'Nữ', '1996-05-03', '001196012350', '0912340006',
 'sales6.test@example.com', NULL,
 (SELECT department_id FROM departments WHERE department_name = 'Kinh doanh'), '2023-08-21', 1),

-- ===== Kỹ thuật =====
('kythuat2', 'kythuat2@postef.com.vn', '$2a$10$DAGqjm1tkfcQwNW/Eg4heea3pcFZjnBx61.02OXXc5k2.wlOo/SN2',
 (SELECT role_id FROM roles WHERE role_name = 'Kỹ thuật'),
 'Vũ', 'Đình', 'Nam', 'Nam', '1991-03-19', '001191012351', '0912340007',
 'kythuat2.test@example.com', NULL,
 (SELECT department_id FROM departments WHERE department_name = 'Kỹ thuật'), '2021-06-14', 0),
('kythuat3', 'kythuat3@postef.com.vn', '$2a$10$sZGBam7M3NbxjvgR4t6RAOPIDXvqnM0UT9Yy4DtshErcoiGwUSUxS',
 (SELECT role_id FROM roles WHERE role_name = 'Kỹ thuật'),
 'Hoàng', 'Thị', 'Lan', 'Nữ', '1994-12-05', '001194012352', '0912340008',
 'kythuat3.test@example.com', NULL,
 (SELECT department_id FROM departments WHERE department_name = 'Kỹ thuật'), '2022-10-03', 0),
('kythuat4', 'kythuat4@postef.com.vn', '$2a$10$RoLmNuQu9Ip6Zsu86mf0uOiPm9xokvqtK57spfCn0Dexw/45Ro1qy',
 (SELECT role_id FROM roles WHERE role_name = 'Kỹ thuật'),
 'Bùi', 'Tiến', 'Dũng', 'Nam', '1989-06-27', '001189012353', '0912340009',
 'kythuat4.test@example.com', NULL,
 (SELECT department_id FROM departments WHERE department_name = 'Kỹ thuật'), '2020-04-20', 0),
('kythuat5', 'kythuat5@postef.com.vn', '$2a$10$ClxCdcFUyHKrbyQHYG0yA.5.wx.B8uE.3G8zavmgr8gO4qdL61OfS',
 (SELECT role_id FROM roles WHERE role_name = 'Kỹ thuật'),
 'Ngô', 'Văn', 'Hiếu', 'Nam', '1997-01-14', '001197012354', '0912340010',
 'kythuat5.test@example.com', NULL,
 (SELECT department_id FROM departments WHERE department_name = 'Kỹ thuật'), '2024-01-08', 0),

-- ===== CSKH =====
('cskh2', 'cskh2@postef.com.vn', '$2a$10$7NMKL70QjZZ4oRdU3qlTguMawMGrf6AbZsC0GmvWwD61EUAISbzMu',
 (SELECT role_id FROM roles WHERE role_name = 'CSKH'),
 'Đặng', 'Thị Mai', 'Phương', 'Nữ', '1992-08-09', '001192012355', '0912340011',
 'cskh2.test@example.com', NULL,
 (SELECT department_id FROM departments WHERE department_name = 'Chăm sóc khách hàng'), '2022-03-07', 0),
('cskh3', 'cskh3@postef.com.vn', '$2a$10$Mm5Z3WSoEkUuvLQyPKg9fOEky1oqSg9Pc1ycSnniJhM0X.HMRjDhS',
 (SELECT role_id FROM roles WHERE role_name = 'CSKH'),
 'Lý', 'Quốc', 'Cường', 'Nam', '1990-10-21', '001190012356', '0912340012',
 'cskh3.test@example.com', NULL,
 (SELECT department_id FROM departments WHERE department_name = 'Chăm sóc khách hàng'), '2021-12-13', 0),
('cskh4', 'cskh4@postef.com.vn', '$2a$10$0qHqBjhyHxqLRkROcpud4eXVu9EsH/Qw9WWpiElq3pfeczYSQtzmC',
 (SELECT role_id FROM roles WHERE role_name = 'CSKH'),
 'Trịnh', 'Thị Hồng', 'Nhung', 'Nữ', '1998-04-02', '001198012357', '0912340013',
 'cskh4.test@example.com', NULL,
 (SELECT department_id FROM departments WHERE department_name = 'Chăm sóc khách hàng'), '2024-06-17', 0);
