# QA Report - Task 5.5
**Kết luận:** APPROVED (Đã khắc phục 100% các mục góp ý của QA)
**Kết quả `ant test`:** 1892 pass / 0 fail / 3 skipped (tổng 1895; 3 skipped là test tích hợp `_it`, không chạy DB thật theo rules §0)

**Phạm vi review:**
- File code:
  - `src/java/poscs/controller/TicketExportController.java`
  - `src/java/poscs/service/TicketExportService.java`
  - `src/java/poscs/controller/TechnicalSupportTicketController.java`
- File test:
  - `test/poscs/controller/TicketExportControllerTest.java`
  - `test/poscs/service/TicketExportServiceTest.java`
- Đối chiếu SPEC: UC-42 và BR-45 (kèm ràng buộc rules §3, §4).

---

## Đánh giá tổng quan
Task 5.5 đã hoàn thành phần lớn chức năng xuất phiếu ra Excel và PDF theo đúng kiến trúc phân tầng:
- **Nghiệp vụ (UC-42, BR-45):** Tách riêng `TicketExportController` và `TicketExportService`. Xuất Excel danh sách phiếu theo đúng bộ lọc danh sách (từ khoá, trạng thái, ưu tiên, kỳ/năm, người xử lý), không phân trang, cấu trúc 18 cột đầy đủ theo BR-45. Xuất PDF chi tiết 1 phiếu hỗ trợ kèm phân trang tự động cho văn bản dài, sử dụng font Noto Sans tiếng Việt. Kỹ thuật viên mặc định lọc "Phiếu của tôi".
- **Bảo mật & Phân quyền:** Phân quyền qua `PermissionService.canExportTickets` (Admin, Sales, Kỹ thuật theo SPEC §3.3); từ chối truy cập trả về HTTP 403 với `PermissionService.MSG_022`. Xử lý an toàn khi phiếu không tìm thấy (redirect `/ticket?error=notfound`).
- **Unit test & Hồi quy:** Bổ sung 23 unit tests cho cả Service và Controller, bao phủ các trường hợp đúng, ngoại lệ, phân quyền và định dạng. Toàn bộ 1892 tests của dự án đều pass.

---

## Danh sách lỗi

- [x] 🟡 `TicketExportService.java` › `buildPdfDocument()` (dòng 111-152)
  - Vi phạm: rules §4 ("Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ.")
  - Mô tả: Phương thức `buildPdfDocument()` hiện dài 42 dòng (vượt giới hạn 40 dòng). Việc dựng danh sách các dòng văn bản (`lines`) và thêm các cặp thông tin / đoạn văn có thể tách thành phương thức phụ trợ để giữ method chính ngắn gọn.
  - *Đã khắc phục:* Đã tách phương thức phụ trợ `buildPdfLines(TechnicalRequest ticket, PDFont font, float contentWidth)` (21 dòng). `buildPdfDocument()` hiện tại chỉ còn 17 dòng. Toàn bộ file `TicketExportService.java` là 245 dòng (≤ 250 dòng).

- [x] 🟡 `TechnicalSupportTicketController.java` (nhiều vị trí)
  - Vi phạm: rules §3 (Ranh giới cấm: bảo toàn mã nguồn và tài liệu không liên quan), Tiêu chuẩn bảo toàn tài liệu.
  - Mô tả: Khi refactor uỷ quyền sang `TicketExportController`, nhiều đoạn Javadoc và chú thích nghiệp vụ quan trọng ngoài phạm vi task đã bị xoá bỏ khỏi `TechnicalSupportTicketController.java`, bao gồm:
    1. Javadoc của hằng số `TECHNICIAN_ROLE` và `ROLE_BUYER` (dòng ~45-56).
    2. Chú thích giải thích việc gán `canManage` trong `doGet` (dòng ~155-159).
    3. Chú thích lý do kiểm tra trạng thái khi xóa phiếu trong `handleDelete` (dòng ~403-410).
    4. Javadoc và chú thích của `setDropdownAttributes` (dòng ~420-432).
    5. Javadoc của `resolveCurrentUserId` (dòng ~444).
    6. Javadoc và chú thích bảo mật của `customerIsBuyer` và `contractMatchesEnterprise` (dòng ~455-470).
    7. Javadoc của `isValid` (dòng ~475-485).
  - *Đã khắc phục:* Đã khôi phục đầy đủ 100% các đoạn Javadoc và chú thích nghiệp vụ/bảo mật gốc tại `TechnicalSupportTicketController.java`.

- [x] 🟢 `TicketExportService.java` › `getPdfFileName()` (dòng ~58-61)
  - Vi phạm: rules §4 & Tiêu chuẩn giao thức HTTP / header Content-Disposition (RFC 6266 / RFC 7230).
  - Mô tả: Khi `ticketCode` của phiếu bị null hoặc rỗng, hàm fallback sử dụng `nz(code)` trả về ký tự gạch ngang dài em-dash `"—"` (`\u2014`), tạo ra chuỗi `"phieu_—.pdf"`. Việc đưa ký tự phi ASCII vào header `Content-Disposition: attachment; filename="phieu_—.pdf"` có thể gây lỗi (`IllegalArgumentException`) hoặc bị lỗi font trên một số servlet container / client HTTP khi không hỗ trợ non-ISO-8859-1 header.
  - *Đã khắc phục:* Đã sửa `getPdfFileName(...)` sử dụng fallback ASCII an toàn (`phieu_export.pdf` nếu null, `phieu_<id>.pdf` nếu mã rỗng), đồng thời bổ sung unit test kiểm tra.

- [x] 🟢 `TicketExportController.java` › `exportPdf()` (dòng ~175)
  - Vi phạm: rules §4 & Tương đồng với cơ chế chuẩn hóa của `ExcelUtil.java`.
  - Mô tả: Header `Content-Disposition` trong `exportPdf` chỉ đặt `filename="..."` mà chưa kèm `filename*=UTF-8''...` như cách `ExcelUtil` thực hiện để tuân thủ đầy đủ RFC 5987 / RFC 6266 khi tên file có dấu hoặc ký tự đặc biệt.
  - *Đã khắc phục:* Đã áp dụng `filename="..."; filename*=UTF-8''...` đồng bộ với `ExcelUtil.java` và cập nhật unit test kiểm chứng header này.
