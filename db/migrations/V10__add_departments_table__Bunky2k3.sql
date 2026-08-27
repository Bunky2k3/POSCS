-- "Add employee" used to take Phòng ban (department) as free text, which let the same department end up spelled differently across employees. Introduces a `departments` lookup table (same id+name shape as `roles`) so it can be a dropdown instead, migrates every existing users.department value into it losslessly, then swaps users.department (varchar) for users.department_id (FK).

INSERT INTO schema_migrations (version) VALUES ('V10__add_departments_table__Bunky2k3');

CREATE TABLE `departments` (
  `department_id` int NOT NULL AUTO_INCREMENT,
  `department_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`department_id`),
  UNIQUE KEY `department_name` (`department_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Canonical departments for this company, mirroring the existing roles (Admin/Sales/Kỹ thuật/CSKH).
INSERT IGNORE INTO `departments` (`department_name`) VALUES
('Ban giám đốc'),
('Kinh doanh'),
('Kỹ thuật'),
('Chăm sóc khách hàng');

-- Preserve any existing free-text department value that doesn't match the canonical list above (no data loss).
INSERT IGNORE INTO `departments` (`department_name`)
SELECT DISTINCT `department` FROM `users`
WHERE `department` NOT IN (SELECT `department_name` FROM `departments`);

ALTER TABLE `users` ADD COLUMN `department_id` int NULL AFTER `department`;

UPDATE `users` u
JOIN `departments` d ON d.`department_name` = u.`department`
SET u.`department_id` = d.`department_id`;

ALTER TABLE `users` MODIFY COLUMN `department_id` int NOT NULL;

ALTER TABLE `users`
  ADD CONSTRAINT `users_department_fk` FOREIGN KEY (`department_id`) REFERENCES `departments` (`department_id`);

ALTER TABLE `users` DROP COLUMN `department`;
