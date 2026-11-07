# QA Report - Task 5.3
**Kết luận:** APPROVED - Đạt yêu cầu UC & BR
**Kết quả `ant test`:** 1841 pass / 0 fail / 3 skipped (tổng 1844; 3 skipped là test tích hợp `_it`, không chạy DB thật theo rules §0)

**Phạm vi review:**
- File code:
  - `src/java/poscs/controller/TicketListController.java`
  - `src/java/poscs/controller/TicketDetailController.java`
  - `src/java/poscs/controller/TechnicalSupportTicketController.java` (ủy quyền tương thích ngược)
- File test:
  - `test/poscs/controller/TicketListControllerTest.java`
  - `test/poscs/controller/TicketDetailControllerTest.java`
- Đối chiếu SPEC: UC-16, UC-17 và BR-12, BR-14, BR-45, BR-46 (kèm ràng buộc BR-40, BR-47 và rules §3, §4).

## Đã kiểm tra và đạt
- **Nghiệp vụ Danh sách phiếu (UC-16, BR-12, BR-14, BR-45):**
  - **Phân trang (BR-12):** Thiết lập `PAGE_SIZE = 10` chuẩn cấu hình; tham số `page` bị âm, bằng 0, rỗng hoặc chứa ký tự lạ đều được chuẩn hóa an toàn về `page = 1`.
  - **Trạng thái trống (BR-14):** Khi danh sách không có dữ liệu, trả về danh sách rỗng (`ticketList = []`), `totalPages = 1`, `totalCount = 0`, đảm bảo JSP hiển thị empty state, không gây lỗi màn hình trắng.
  - **Dải KPI & Cột dữ liệu (BR-45):** Tổng hợp dải KPI theo 4 mức (*Mới tiếp nhận*, *Đang xử lý*, *Đã đóng*, *Khẩn cấp*) đúng theo kỳ (`period`) và người xử lý (`assigneeId`) đang chọn; cung cấp đầy đủ thông tin phục vụ bảng hiển thị.
  - **Bộ lọc người xử lý (UC-16):** Kỹ thuật viên mở danh sách lần đầu (không có tham số `assignee`) được mặc định lọc "Phiếu của tôi" (`assignee=mine`), đồng thời tính dải KPI theo đúng phiếu của kỹ thuật viên đó. Admin và Sales mặc định "Tất cả" (`assignee=all`). Hỗ trợ chọn xem tất cả hoặc chọn theo một KTV cụ thể, bảo lưu người xử lý trong dropdown kể cả khi đã đổi vai trò.
  - **Bảo lưu bộ lọc & Mã hóa URL:** Từ khóa tìm kiếm được mã hóa URL an toàn (`keywordParam`) phục vụ các liên kết phân trang, lọc và xuất dữ liệu.
- **Nghiệp vụ Chi tiết phiếu (UC-17, BR-46, BR-47):**
  - **Cấu trúc dữ liệu chi tiết (BR-46):** Nạp đầy đủ Thông tin chung (`ticket`), Người tạo phiếu, Kỹ thuật viên xử lý, và Lịch sử xử lý (`ticketHistory`).
  - **Quyền thao tác Sửa / Xóa (UC-17, BR-47):** Kỹ thuật viên chỉ được phép sửa phiếu khi đang được phân công phụ trách phiếu đó (`canEdit = true`, `canDelete = false`); kỹ thuật viên khác không được sửa (`canEdit = false`). Phiếu ở trạng thái *Đang xử lý* bị khóa không cho xóa (`canDelete = false`), chỉ cho phép xóa khi ở trạng thái *Mới tiếp nhận* hoặc *Đã đóng* đối với Admin/Sales.
  - **Kiểm soát tham số ID:** Xử lý null-safe cho tham số `id`, các giá trị null, rỗng, âm hoặc không phải số đều được điều hướng an toàn về `/ticket?error=notfound`.
- **An toàn bảo mật & Phân quyền (rules §3, §4):**
  - Kiểm tra quyền xem qua `PermissionService.canViewTickets(currentUser)` theo cơ chế fail-closed ở cả hai controller; từ chối ngay bằng HTTP 403 `MSG-022` khi chưa đăng nhập, tài khoản bị xóa mềm/vô hiệu hóa, hoặc vai trò không hợp lệ.
  - Cả hai controller đều là Presentation layer thuần túy (GET), không chứa logic nghiệp vụ, ủy quyền toàn bộ xử lý sang `TicketService` và `PermissionService`.
  - Không có thao tác ghi dữ liệu, không có nguy cơ CSRF; không hardcode secret; các tham số URL được encode chống injection.
- **Tiêu chuẩn code & Giới hạn kích thước:**
  - `TicketListController.java`: 205 dòng (≤ 250 dòng). Mọi method ≤ 21 dòng (chuẩn ≤ 40 dòng).
  - `TicketDetailController.java`: 155 dòng (≤ 250 dòng). Mọi method ≤ 35 dòng (chuẩn ≤ 40 dòng).
  - Không có placeholder, không có `// TODO` hay `// rest of the code`.
  - Sử dụng chuẩn `jakarta.servlet.*` (Jakarta EE 11 / Java 21), không có `javax.*`.
  - Không làm thay đổi hành vi cũ của `TechnicalSupportTicketController`: ủy quyền sạch sẽ `showList` và `showDetail`, giữ nguyên tương thích ngược với các test suite hiện hữu.
- **Chất lượng Unit test:**
  - `TicketListControllerTest.java` (12 test cases) và `TicketDetailControllerTest.java` (10 test cases) bao phủ toàn diện cả kịch bản đúng và sai, kiểm tra chính xác từng attribute, mã lỗi HTTP 403 `MSG-022`, và URL chuyển hướng.
  - Chạy toàn bộ `ant test`: **1841 tests passed, 0 failures, 0 errors, 3 skipped** (100% pass).

## Danh sách lỗi
*Không có lỗi.*
