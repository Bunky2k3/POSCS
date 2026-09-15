-- Đổi tên vai 'Khách bán' thành 'Nhà cung cấp'.
--
-- V20 đặt hai vai là 'Khách mua' / 'Khách bán' cho đối xứng. Thực tế dùng thì
-- "khách bán" dễ đọc nhầm: trong một câu có cả hợp đồng 'Bán' (mình bán ra)
-- lẫn vai 'Khách bán' (bên đó bán cho mình), hai chữ "bán" đứng cạnh nhau mà
-- chỉ hai chiều ngược nhau. "Nhà cung cấp" là từ nghiệp vụ chuẩn, không lẫn
-- được với chiều nào cả.
--
-- Vai còn lại GIỮ NGUYÊN 'Khách mua': nó không gây nhầm (hợp đồng 'Mua' là
-- mình mua, 'Khách mua' là bên kia mua -- vẫn chéo, nhưng chữ "khách" đã nói
-- rõ đang đứng ở phía đối tác).
--
-- KHÔNG sửa thẳng V20: migration đó đã chạy trên CSDL thật rồi, sửa lại nội
-- dung là để file trên đĩa khác với thứ đã áp dụng. Đổi tên bằng một bước
-- riêng thì lịch sử đọc được đúng như nó đã xảy ra.
--
-- Câu UPDATE dưới đây khớp 0 dòng trên bộ dữ liệu hiện tại (cả 12 khách đều
-- là 'Khách mua'). Vẫn phải có: bất kỳ CSDL nào đã kịp tạo khách hàng bán
-- giữa V20 và bản này đều phải được đổi theo, nếu không họ biến khỏi danh
-- sách nhà cung cấp mà không báo gì.

INSERT INTO schema_migrations (version) VALUES ('V22__rename_role_khach_ban_to_nha_cung_cap__ndat2003');

UPDATE enterprise_roles SET role = 'Nhà cung cấp' WHERE role = 'Khách bán';
