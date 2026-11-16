# QA Report - Task 7.4a
**Kết luận:** APPROVED
**Kết quả `ant test`:** 1964/0/3 (88 suite, 1.964 test pass, 0 fail, 0 error, 3 suite tích hợp skipped mặc định; `ant compile-jsps`: BUILD SUCCESSFUL)

## Danh sách lỗi
APPROVED - Đạt yêu cầu UC & BR

Đã kiểm tra:
- **MSG-022 / NFR-10 (A, C):** hai chỗ `sendError(403, …)` trong `TechnicalSupportTicketController` (`showEditForm()` dòng ~294, `handleUpdate()` dòng ~355) nay dùng `AccessControl.MSG_022`; điều kiện chặn giữ nguyên. Giá trị hằng khớp từng chữ với SPEC §9 (`"Bạn không có quyền truy cập nội dung này."`). Hai đường dẫn mà hướng dẫn mô tả đều kết thúc ở `web/error403.jsp` (web.xml `<error-page>` 403), trang này in đúng câu MSG-022: kỹ thuật viên mở form tạo phiếu (`AccessControl.requireFullAccess`) và người không phải Admin mở trang nhân viên (`EmployeeController`, `PermissionService.MSG_022`). Vì vậy câu ghi trong 2 bảng "Thông báo lỗi thường gặp" đúng với câu người dùng thật sự thấy.
- **Unit test (E):** `git diff main -- test` chỉ có dòng thêm (1 import, 2 test). Hai test mới đi đúng nhánh cần khoá: vai "Kỹ thuật" không có Full access trên TICKET, phiếu giả lập giao cho kỹ thuật viên 50, người đăng nhập là 1 / 51, nên `canUpdateAssignedTicket` trả false và chạy tới `sendError`. Assertion so khớp chính xác `sendError(SC_FORBIDDEN, AccessControl.MSG_022)`, không dùng `anyString()`, nên nếu câu cũ quay lại thì test fail. Kết quả JUnit có đủ 2 test `update_technicianNotAssignedToTicket_returns403WithMsg022` và `editForm_technicianNotAssignedToThisTicket_returns403WithMsg022` (suite 67/67 pass, main có 65 test).
- **Phạm vi và Xong khi (D, F):** đang ở nhánh `fix/task-7.4a-msg-022`, `git log main..HEAD` rỗng. Không đụng file nào ở dòng "Không đụng". `grep` không còn câu "Bạn không có quyền thực hiện thao tác này." trong `src/` và `web/`. So `docs/Report6_HuongDanSuDung.docx` với bản trên `main`: chỉ `word/document.xml` khác, gồm 2 ô lỗi (mục 3.6 Phiếu hỗ trợ và 3.7.9 Nhân viên) đổi sang câu MSG-022, 1 dòng mới `16/11/2026 · S · … (Task 7.4a)` trong Lịch sử thay đổi, và tháng ở trang bìa (xem ghi chú). `lastModifiedBy` vẫn là `tools/guide/word/export_word.py`. Line ending của các file đã sửa vẫn là CRLF như trên `main`, không có thay đổi toàn file.

## Ghi chú ngoài phạm vi
- `docs/Report6_HuongDanSuDung.docx`: trang bìa đổi từ "Tháng 10 năm 2026" sang "Tháng 11 năm 2026". Đây là việc script `export_word.py` tự làm (`datetime.date.today()`, dòng ~661), không phải sửa tay. Thay đổi này đúng mong đợi khi xuất lại tài liệu và không tính là lỗi.
- `task_tracker.md` có thêm mục "Cách ghi một task mới" và dòng Task 7.4a. Đây là thay đổi quy trình do người dùng soạn, không phải do bước Code. Bước Commit vẫn sẽ add file này.
- `docs/PROMPTS.md` đang untracked và không thuộc danh sách file của Task 7.4a. Theo bước 3 của prompt Commit, file lạ không thuộc task thì không add, nên người dùng cần tự quyết có commit file này cùng task hay commit riêng.
- Còn một số câu 403 riêng: `ContractController.java` dòng ~787 ("Bạn không được ký hợp đồng…"), `ChangeRequestController`, `ContractActionController`, `AuthenticationFilter` (CSRF). Task đã loại các file này hoặc chúng nằm ngoài phạm vi. Vì `error403.jsp` không in message của `sendError`, người dùng không thấy các câu này, và không trang hướng dẫn nào trích dẫn chúng.
- Hai test mới lặp lại phần chuẩn bị dữ liệu của `update_technicianNotAssignedToTicket_returns403` và `editForm_technicianNotAssignedToThisTicket_returns403`, chỉ khác assertion. Lặp như vậy là cố ý vì task cấm sửa test cũ, không cần xử lý.
