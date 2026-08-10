-- POSCS full database schema.
-- Run this single file once against a fresh, empty MySQL database to
-- get the exact schema the app expects. Re-running it drops and
-- recreates every table listed here (safe on a fresh/dev DB only --
-- do NOT run against a database with data you want to keep).
--
-- Usage:
--   mysql -u root -p -e "CREATE DATABASE poscs_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
--   mysql -u root -p poscs_db < db/schema.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =========================================================
-- Migration tracking
--
-- Records which files under db/migrations/ have been applied to this
-- database, so a migration is never accidentally run twice. A fresh
-- database created from this file is already up to date with every
-- migration that existed when this file was last synced -- see
-- db/migrations/README.md for the workflow.
-- =========================================================

DROP TABLE IF EXISTS `schema_migrations`;
CREATE TABLE `schema_migrations` (
  `version`    varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `applied_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Core tables
-- =========================================================

DROP TABLE IF EXISTS `provinces`;
CREATE TABLE `provinces` (
  `province_id` int NOT NULL AUTO_INCREMENT,
  `province_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`province_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `districts`;
CREATE TABLE `districts` (
  `districts_id` int NOT NULL AUTO_INCREMENT,
  `districts_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `province_id` int NOT NULL,
  PRIMARY KEY (`districts_id`),
  KEY `province_id` (`province_id`),
  CONSTRAINT `districts_ibfk_1` FOREIGN KEY (`province_id`) REFERENCES `provinces` (`province_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `addresses`;
CREATE TABLE `addresses` (
  `address_id` int NOT NULL AUTO_INCREMENT,
  `street_and_local_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `districts_id` int NOT NULL,
  PRIMARY KEY (`address_id`),
  KEY `districts_id` (`districts_id`),
  CONSTRAINT `addresses_ibfk_1` FOREIGN KEY (`districts_id`) REFERENCES `districts` (`districts_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `roles`;
CREATE TABLE `roles` (
  `role_id` int NOT NULL AUTO_INCREMENT,
  `role_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role_id` int NOT NULL,
  `last_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `middle_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `first_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `gender` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `date_of_birth` date NOT NULL,
  `citizen_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `personal_email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_id` int DEFAULT NULL,
  `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `department` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `hire_date` date NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `citizen_id` (`citizen_id`),
  UNIQUE KEY `phone` (`phone`),
  KEY `role_id` (`role_id`),
  KEY `address_id` (`address_id`),
  CONSTRAINT `users_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `roles` (`role_id`),
  CONSTRAINT `users_ibfk_2` FOREIGN KEY (`address_id`) REFERENCES `addresses` (`address_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `enterprises`;
CREATE TABLE `enterprises` (
  `enterprise_id` int NOT NULL AUTO_INCREMENT,
  `enterprise_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enterprise_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tax_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `address_id` int DEFAULT NULL,
  `legal_representative` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `logo_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `business_license_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Active',
  `current_relationship_rating` enum('Tốt','Cần theo dõi','Xấu','Có nguy cơ rời bỏ') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`enterprise_id`),
  UNIQUE KEY `enterprise_code` (`enterprise_code`),
  UNIQUE KEY `tax_code` (`tax_code`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `phone` (`phone`),
  KEY `address_id` (`address_id`),
  CONSTRAINT `enterprises_ibfk_1` FOREIGN KEY (`address_id`) REFERENCES `addresses` (`address_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `enterprisecontacts`;
CREATE TABLE `enterprisecontacts` (
  `contact_id` int NOT NULL AUTO_INCREMENT,
  `enterprise_id` int NOT NULL,
  `contact_last_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contact_middle_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_first_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contact_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `position` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`contact_id`),
  KEY `enterprise_id` (`enterprise_id`),
  CONSTRAINT `enterprisecontacts_ibfk_1` FOREIGN KEY (`enterprise_id`) REFERENCES `enterprises` (`enterprise_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `productcategories`;
CREATE TABLE `productcategories` (
  `category_id` int NOT NULL AUTO_INCREMENT,
  `category_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `parent_category_id` int DEFAULT NULL,
  `display_order` int NOT NULL DEFAULT '0',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`category_id`),
  KEY `parent_category_id` (`parent_category_id`),
  CONSTRAINT `productcategories_ibfk_1` FOREIGN KEY (`parent_category_id`) REFERENCES `productcategories` (`category_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `products`;
CREATE TABLE `products` (
  `product_id` int NOT NULL AUTO_INCREMENT,
  `product_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` longtext COLLATE utf8mb4_unicode_ci,
  `image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `catalogue_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category_id` int NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `product_code` (`product_code`),
  KEY `category_id` (`category_id`),
  CONSTRAINT `products_ibfk_1` FOREIGN KEY (`category_id`) REFERENCES `productcategories` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `contracts`;
CREATE TABLE `contracts` (
  `contract_id` int NOT NULL AUTO_INCREMENT,
  `contract_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contract_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `signing_date` date NOT NULL,
  `effective_date` date NOT NULL,
  `end_date` date NOT NULL,
  `enterprise_id` int NOT NULL,
  `owner_id` int NOT NULL,
  `attachment_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Đang hiệu lực',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`contract_id`),
  UNIQUE KEY `contract_code` (`contract_code`),
  KEY `enterprise_id` (`enterprise_id`),
  KEY `owner_id` (`owner_id`),
  CONSTRAINT `contracts_ibfk_1` FOREIGN KEY (`enterprise_id`) REFERENCES `enterprises` (`enterprise_id`),
  CONSTRAINT `contracts_ibfk_2` FOREIGN KEY (`owner_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `contractproducts`;
CREATE TABLE `contractproducts` (
  `contract_product_id` int NOT NULL AUTO_INCREMENT,
  `contract_id` int NOT NULL,
  `product_id` int NOT NULL,
  `quantity` int NOT NULL,
  `unit` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Cái',
  `notes` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`contract_product_id`),
  KEY `contract_id` (`contract_id`),
  KEY `product_id` (`product_id`),
  CONSTRAINT `contractproducts_ibfk_1` FOREIGN KEY (`contract_id`) REFERENCES `contracts` (`contract_id`) ON DELETE CASCADE,
  CONSTRAINT `contractproducts_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `technicalrequests`;
CREATE TABLE `technicalrequests` (
  `ticket_id` int NOT NULL AUTO_INCREMENT,
  `ticket_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enterprise_id` int NOT NULL,
  `contract_id` int DEFAULT NULL,
  `ticket_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `priority` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reception_channel` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sla_deadline` datetime DEFAULT NULL,
  `assigned_technician_id` int NOT NULL,
  `created_by` int NOT NULL,
  `created_date` date NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `is_warranty` tinyint(1) NOT NULL DEFAULT '1',
  `status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Mới tiếp nhận',
  `resolution_summary` text COLLATE utf8mb4_unicode_ci,
  `resolved_at` datetime DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`ticket_id`),
  UNIQUE KEY `ticket_code` (`ticket_code`),
  KEY `enterprise_id` (`enterprise_id`),
  KEY `contract_id` (`contract_id`),
  KEY `assigned_technician_id` (`assigned_technician_id`),
  KEY `created_by` (`created_by`),
  CONSTRAINT `technicalrequests_ibfk_1` FOREIGN KEY (`enterprise_id`) REFERENCES `enterprises` (`enterprise_id`),
  CONSTRAINT `technicalrequests_ibfk_2` FOREIGN KEY (`contract_id`) REFERENCES `contracts` (`contract_id`) ON DELETE SET NULL,
  CONSTRAINT `technicalrequests_ibfk_3` FOREIGN KEY (`assigned_technician_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `technicalrequests_ibfk_4` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `technicalrequestdevices`;
CREATE TABLE `technicalrequestdevices` (
  `request_device_id` int NOT NULL AUTO_INCREMENT,
  `ticket_id` int NOT NULL,
  `product_id` int DEFAULT NULL,
  `device_name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `serial_number` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fault_notes` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`request_device_id`),
  KEY `ticket_id` (`ticket_id`),
  KEY `product_id` (`product_id`),
  CONSTRAINT `technicalrequestdevices_ibfk_1` FOREIGN KEY (`ticket_id`) REFERENCES `technicalrequests` (`ticket_id`) ON DELETE CASCADE,
  CONSTRAINT `technicalrequestdevices_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `technicalrequesthistory`;
CREATE TABLE `technicalrequesthistory` (
  `history_id` int NOT NULL AUTO_INCREMENT,
  `ticket_id` int NOT NULL,
  `from_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `to_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `changed_by` int NOT NULL,
  `changed_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `internal_note` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`history_id`),
  KEY `ticket_id` (`ticket_id`),
  KEY `changed_by` (`changed_by`),
  CONSTRAINT `technicalrequesthistory_ibfk_1` FOREIGN KEY (`ticket_id`) REFERENCES `technicalrequests` (`ticket_id`) ON DELETE CASCADE,
  CONSTRAINT `technicalrequesthistory_ibfk_2` FOREIGN KEY (`changed_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `notifications`;
CREATE TABLE `notifications` (
  `notification_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `title` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`notification_id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `notifications_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Customer evaluation (purchase volume, payment timeliness,
-- relationship lifecycle) -- see PR history for the business
-- requirement this implements.
-- =========================================================

DROP TABLE IF EXISTS `contract_payments`;
CREATE TABLE `contract_payments` (
  `payment_id`     int NOT NULL AUTO_INCREMENT,
  `contract_id`    int NOT NULL,
  `invoice_amount` decimal(15,2) NOT NULL,
  `due_date`       date NOT NULL,
  `paid_date`      date DEFAULT NULL,
  `created_at`     timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`payment_id`),
  KEY `contract_id` (`contract_id`),
  CONSTRAINT `fk_payment_contract` FOREIGN KEY (`contract_id`) REFERENCES `contracts` (`contract_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `customer_evaluation_rules`;
CREATE TABLE `customer_evaluation_rules` (
  `rule_id`     int NOT NULL AUTO_INCREMENT,
  `rule_code`   varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `rule_value`  decimal(15,2) NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at`  timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`rule_id`),
  UNIQUE KEY `rule_code` (`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `customer_evaluation_rules` (`rule_code`, `rule_value`, `description`) VALUES
('LOW_PURCHASE_THRESHOLD',  50000000,  'Dưới mức này (VND) tính là "mua ít"'),
('HIGH_PURCHASE_THRESHOLD', 200000000, 'Trên mức này (VND) tính là "mua nhiều"'),
('EARLY_PAYMENT_DAYS',      3,         'Trả trước hạn từ X ngày trở lên tính là "trả sớm"'),
('LATE_PAYMENT_DAYS',       5,         'Trả sau hạn từ X ngày trở lên tính là "trả chậm"');

DROP TABLE IF EXISTS `customer_lifecycle_events`;
CREATE TABLE `customer_lifecycle_events` (
  `event_id`            int NOT NULL AUTO_INCREMENT,
  `enterprise_id`       int NOT NULL,
  `event_type`          varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `relationship_rating` enum('Tốt','Cần theo dõi','Xấu','Có nguy cơ rời bỏ') COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_auto_generated`   tinyint(1) NOT NULL DEFAULT '0',
  `description`         text COLLATE utf8mb4_unicode_ci,
  `event_date`          date NOT NULL,
  `recorded_by`         int NOT NULL,
  `created_at`          timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`event_id`),
  KEY `enterprise_id` (`enterprise_id`),
  KEY `recorded_by` (`recorded_by`),
  CONSTRAINT `fk_lifecycle_enterprise` FOREIGN KEY (`enterprise_id`) REFERENCES `enterprises` (`enterprise_id`),
  CONSTRAINT `fk_lifecycle_user` FOREIGN KEY (`recorded_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
