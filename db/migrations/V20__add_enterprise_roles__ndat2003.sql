-- Tách khách hàng thành hai vai: khách mua và khách bán.
--
-- Cho tới giờ "khách hàng" là một khối chung chung: enterprises.customer_type
-- nói NGÀNH NGHỀ (Nhà mạng viễn thông / Nhà thầu thi công / Đại lý phân phối)
-- chứ không nói bên đó mua của mình hay bán cho mình. Không có chiều đó thì
-- không tách nổi màn hình quản lý làm hai, và bước sau -- hợp đồng mua / hợp
-- đồng bán -- cũng không có chỗ bấu víu.
--
-- VÌ SAO LÀ BẢNG VAI TRÒ, KHÔNG PHẢI CỘT TRÊN enterprises, CŨNG KHÔNG PHẢI
-- TÁCH enterprises LÀM HAI BẢNG:
--
--   * Tách làm hai bảng thì BỐN bảng đang trỏ khoá ngoại vào enterprises
--     (contracts, technicalrequests, enterprisecontacts,
--     customer_lifecycle_events) không biết trỏ vào đâu -- khoá ngoại không
--     trỏ được vào hai bảng cùng lúc. Chúng buộc phải thành cột đa hình không
--     khoá ngoại, đúng nhượng bộ mà change_requests.target_id đã phải chịu.
--     Nhân lên bốn chỗ là CSDL mất hẳn khả năng tự chặn dữ liệu mồ côi.
--     Thêm nữa, công ty vừa mua vừa bán sẽ thành hai bản ghi trùng mã số
--     thuế, mà enterprises.tax_code đang UNIQUE.
--
--   * Cột cờ trên enterprises thì chạy được, nhưng mỗi vai mới về sau (nhà
--     thầu phụ, đối tác thi công, đại lý...) là một lần ALTER TABLE cộng một
--     lần sửa mọi form và mọi câu lọc. Ở đây thêm vai chỉ là thêm dòng.
--
-- GIÁ TRỊ VAI CỐ Ý KHÔNG PHẢI 'Mua'/'Bán'. Bước sau sẽ có
-- contracts.direction mang đúng hai chữ đó nhưng theo GÓC NHÌN NGƯỢC LẠI --
-- mình bán ra (hợp đồng 'Bán') thì đối tác là bên mua ('Khách mua'). Để hai
-- bảng dùng chung một chữ với nghĩa trái nhau là gài sẵn bẫy cho người viết
-- câu JOIN sau này.
--
-- "Ít nhất một vai" KHÔNG ép được ở tầng CSDL: ràng buộc đó nói về sự tồn tại
-- của dòng ở bảng khác, CHECK không với tới, mà trigger thì repo này chưa
-- dùng ở đâu cả. Chặn nằm ở CustomerController, cùng chỗ với các luật nghiệp
-- vụ khác của khách hàng.

INSERT INTO schema_migrations (version) VALUES ('V20__add_enterprise_roles__ndat2003');

CREATE TABLE `enterprise_roles` (
  `enterprise_role_id` int NOT NULL AUTO_INCREMENT,
  `enterprise_id` int NOT NULL,
  -- 'Khách mua'  = bên đó MUA của mình  -> sau này gắn với hợp đồng 'Bán'
  -- 'Khách bán'  = bên đó BÁN cho mình  -> sau này gắn với hợp đồng 'Mua'
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`enterprise_role_id`),
  -- Một công ty không thể giữ cùng một vai hai lần; còn giữ HAI vai khác nhau
  -- thì được, đó chính là lý do bảng này tồn tại.
  UNIQUE KEY `uq_enterprise_roles` (`enterprise_id`, `role`),
  CONSTRAINT `fk_enterprise_roles_enterprise` FOREIGN KEY (`enterprise_id`)
      REFERENCES `enterprises` (`enterprise_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Backfill: toàn bộ khách hàng đang có đều là bên MUA của mình. Không phải
-- suy đoán -- mọi hợp đồng hiện có đều là mình cung cấp ra (Cung cấp thiết bị
-- / Thi công lắp đặt / Bảo trì bảo dưỡng), chưa có hợp đồng mua vào nào.
-- Khách chưa có hợp đồng cũng vào đây: họ được tạo qua màn hình khách hàng
-- vốn chỉ phục vụ chiều bán ra.
INSERT INTO enterprise_roles (enterprise_id, role)
SELECT enterprise_id, 'Khách mua' FROM enterprises;
