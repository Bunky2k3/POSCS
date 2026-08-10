-- Customer evaluation schema: purchase/payment tracking + relationship
-- lifecycle history, driven by configurable thresholds instead of
-- hardcoded business rules.
--
-- Evaluation criteria this supports:
--   1) mua it + tra som            -> "Tot"
--   2) mua nhieu + tra cham        -> "Can theo doi" (can nguoi review)
--   3) bien dong noi bo -> quan he di xuong theo thoi gian -> "Can theo doi"/"Xau"
--      (nhap tay, khong tu dong hoa duoc vi la thong tin dinh tinh)

-- 1) Du lieu thanh toan tho, tach theo tung dot / 1 hop dong
CREATE TABLE contract_payments (
    payment_id     INT PRIMARY KEY AUTO_INCREMENT,
    contract_id    INT NOT NULL,
    invoice_amount DECIMAL(15,2) NOT NULL,
    due_date       DATE NOT NULL,
    paid_date      DATE NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_contract FOREIGN KEY (contract_id) REFERENCES contracts(contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) Nguong cau hinh danh gia (chinh duoc, khong hardcode trong code)
CREATE TABLE customer_evaluation_rules (
    rule_id     INT PRIMARY KEY AUTO_INCREMENT,
    rule_code   VARCHAR(50) NOT NULL UNIQUE,
    rule_value  DECIMAL(15,2) NOT NULL,
    description VARCHAR(255) NULL,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO customer_evaluation_rules (rule_code, rule_value, description) VALUES
('LOW_PURCHASE_THRESHOLD',  50000000,  'Dưới mức này (VND) tính là "mua ít"'),
('HIGH_PURCHASE_THRESHOLD', 200000000, 'Trên mức này (VND) tính là "mua nhiều"'),
('EARLY_PAYMENT_DAYS',      3,         'Trả trước hạn từ X ngày trở lên tính là "trả sớm"'),
('LATE_PAYMENT_DAYS',       5,         'Trả sau hạn từ X ngày trở lên tính là "trả chậm"');

-- 3) Dong thoi gian vong doi khach hang (thang 4 muc)
CREATE TABLE customer_lifecycle_events (
    event_id             INT PRIMARY KEY AUTO_INCREMENT,
    enterprise_id        INT NOT NULL,
    event_type           VARCHAR(100) NOT NULL,
    relationship_rating  ENUM('Tốt', 'Cần theo dõi', 'Xấu', 'Có nguy cơ rời bỏ') NOT NULL,
    is_auto_generated    TINYINT(1) NOT NULL DEFAULT 0,
    description           TEXT NULL,
    event_date            DATE NOT NULL,
    recorded_by           INT NOT NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lifecycle_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprises(enterprise_id),
    CONSTRAINT fk_lifecycle_user FOREIGN KEY (recorded_by) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) Cot cache de hien thi nhanh tren danh sach khach hang, khong can JOIN
ALTER TABLE enterprises
    ADD COLUMN current_relationship_rating ENUM('Tốt', 'Cần theo dõi', 'Xấu', 'Có nguy cơ rời bỏ') NULL AFTER status;
