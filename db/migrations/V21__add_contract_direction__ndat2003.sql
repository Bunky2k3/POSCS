-- Chiều của hợp đồng: mình bán ra hay mình mua vào.
--
-- Nối tiếp V20 (tách khách hàng thành khách mua / khách bán). Cho tới giờ
-- contracts.contract_type nói NỘI DUNG hợp đồng (Cung cấp thiết bị / Thi công
-- lắp đặt / Bảo trì bảo dưỡng) chứ không nói chiều tiền đi -- cả 15 hợp đồng
-- hiện có đều là mình cung cấp ra, nhưng không có chỗ nào phát biểu điều đó.
--
-- GÓC NHÌN: 'Bán'/'Mua' ở đây là CỦA MÌNH.
--
--     hợp đồng 'Bán'  = mình bán ra   -> đối tác giữ vai 'Khách mua'
--     hợp đồng 'Mua'  = mình mua vào  -> đối tác giữ vai 'Khách bán'
--
-- Cặp đôi bị CHÉO, và đó chính là lý do enterprise_roles.role ở V20 cố ý
-- không đặt là 'Mua'/'Bán': để hai bảng không dùng chung một chữ với nghĩa
-- trái nhau.
--
-- KHÔNG đặt khoá ngoại hay CHECK ràng "hợp đồng Bán thì đối tác phải có vai
-- Khách mua": ràng buộc đó nói về sự tồn tại của dòng ở enterprise_roles,
-- CHECK không với tới, và repo chưa dùng trigger ở đâu cả. Chặn nằm ở
-- ContractController, cùng chỗ với các luật nghiệp vụ khác của hợp đồng --
-- và ở tầng giao diện thì ô chọn khách hàng đã lọc sẵn theo chiều.
--
-- HỆ QUẢ PHẢI SỬA CÙNG LÚC, KHÔNG ĐƯỢC LỆCH PHA: KPI doanh thu ở dashboard
-- cộng TẤT CẢ contract_payments, không lọc chiều. Thêm cột này mà không sửa
-- ContractDAO.sumInvoiceAmountInPeriod / sumInvoiceAmountByMonth thì hợp đồng
-- mua đầu tiên nhập vào là doanh thu tự cộng cả tiền mình đi TRẢ -- sai âm
-- thầm, không ai phát hiện cho tới lúc đối chiếu sổ sách.

INSERT INTO schema_migrations (version) VALUES ('V21__add_contract_direction__ndat2003');

ALTER TABLE `contracts`
  ADD COLUMN `direction` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Bán'
      COMMENT 'Bán = mình bán ra (đối tác là Khách mua); Mua = mình mua vào (đối tác là Khách bán)'
      AFTER `contract_type`,
  ADD KEY `idx_contracts_direction` (`direction`);

-- Backfill: toàn bộ hợp đồng hiện có đều là mình BÁN ra. Không suy đoán --
-- ba loại hợp đồng đang có (Cung cấp thiết bị, Thi công lắp đặt, Bảo trì bảo
-- dưỡng) đều là mình cung cấp cho khách, và V20 cũng đã gán cả 12 khách hàng
-- hiện có vai 'Khách mua' theo cùng lập luận đó.
--
-- DEFAULT 'Bán' giữ lại sau backfill là có chủ ý: dữ liệu cũ hoặc đường ghi
-- nào quên truyền chiều sẽ rơi về chiều bán ra -- chiều duy nhất hệ thống
-- từng có, nên không đổi nghĩa bản ghi nào đang tồn tại.
UPDATE contracts SET direction = 'Bán';
