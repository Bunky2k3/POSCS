-- Tài khoản test `tonghop1`: vai Tổng hợp, phòng Tổng hợp (Task 9.2, sau V40).
-- Để có người đăng nhập thử đúng phần việc của vai mới: xem khách hàng, hợp
-- đồng, sản phẩm; xác nhận bàn giao cho phòng Tổng hợp; ghi nhận kỳ thanh
-- toán đã thu; mở /ticket thì nhận 403.
--
-- KHÔNG phải Admin -- cùng lẽ với V12: repository này công khai và mọi tài
-- khoản test dùng chung một mật khẩu, gieo Admin là công bố một lối vào quản
-- trị.
--
-- !!! CHỈ DÙNG CHO DEV/TEST !!!
-- Mật khẩu "Poscs@123", cùng mật khẩu với 12 tài khoản của V12. Đừng nạp file
-- này lên máy chủ chạy thật.
--
-- Hash là bcrypt (cost 10, salt riêng) của "Poscs@123", băm bằng chính
-- lib/jbcrypt-0.4.jar của dự án -- thư viện AuthenticationController dùng để
-- kiểm mật khẩu.
--
-- role_id / department_id tra theo tên như V12 (hai bảng là AUTO_INCREMENT,
-- id không cố định giữa các CSDL). Không có cột `email`: V36 đã bỏ email công
-- ty.
--
-- Đủ giới tính, ngày sinh, CCCD, SĐT để không bị ép qua /updateProfile lúc
-- đăng nhập (User.isProfileIncomplete, V35). CCCD và SĐT nối tiếp dãy số của
-- V12 (…012357, 0912340013).
--
-- NOT EXISTS trên username thay cho INSERT IGNORE của V12: chạy lại thì không
-- thêm trùng, nhưng nếu CCCD/SĐT lỡ trùng một tài khoản tạo tay thì báo lỗi
-- ngay, chứ không lặng lẽ bỏ qua rồi để người dùng tưởng đã có `tonghop1`.

INSERT INTO schema_migrations (version) VALUES ('V41__seed_tonghop_test_account__ndat2003');

INSERT INTO `users`
  (`username`, `password_hash`, `role_id`, `last_name`, `middle_name`, `first_name`,
   `gender`, `date_of_birth`, `citizen_id`, `phone`, `personal_email`, `address_id`,
   `department_id`, `hire_date`, `is_deleted`)
SELECT 'tonghop1', '$2a$10$Zy5dXkGNLYjaOkebo0qWgeR/CYPfCpu1SUkcHX9LPe6LMsIormrA2',
       (SELECT `role_id` FROM `roles` WHERE `role_name` = N'Tổng hợp'),
       N'Nguyễn', N'Thị Thanh', N'Hương', N'Nữ', '1992-04-11', '001192012358', '0912340014',
       'tonghop1.test@example.com', NULL,
       (SELECT `department_id` FROM `departments` WHERE `department_name` = N'Tổng hợp'),
       '2021-03-01', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'tonghop1');
