# QA Report - Task 9.5

**Kết luận:** APPROVED (ĐẠT)
**Kết quả `ant compile-jsps test`:** `compile-jsps` báo `BUILD SUCCESSFUL` (trên máy này `compile.jsps=false` bỏ qua bước dịch trong Ant; đã dịch kiểm chứng riêng 2/2 file `.jsp` đã sửa bằng `org.apache.jasper.JspC` của Tomcat 11.0 với `-source 21 -target 21 -failFast`: **0 errors**, sinh sạch `addnewTicket_jsp.class` và `updateTicket_jsp.class`); **`ant test`: 2.214 pass / 0 fail / 0 error / 3 skipped** (92 test suites: 89 unit test suites pass 100%, 3 integration test suites mặc định bỏ qua; `BUILD SUCCESSFUL`).

**Phạm vi:** Task 9.5 — Phiếu hỗ trợ kỹ thuật (UC-21, UC-22, BR-39, MSG-022): mở `public` cho `TicketService.isAssignedTechnicianValid(int, Integer)` để dùng chung luật kiểm người xử lý (Sales đang hoạt động) cho lối `/ticket` (`TechnicalSupportTicketController.handleCreate`, `handleUpdate`); thêm hiển thị lỗi `invalid_handler` ("Vui lòng chọn người xử lý.") ở `addnewTicket.jsp` và `updateTicket.jsp`; chuyển bước kiểm quyền `canUpdateTicket(currentUser, null)` lên trước khi đọc `id` và gọi `findById` ở lối `/ticket/action` (`TicketActionController.showEditForm`, `handleUpdate`). Đối chiếu với `task_tracker.md`, `docs/SPEC.md` và `rules.md`; thay đổi thực tế lấy từ `git status` và `git diff` trên nhánh `fix/task-9.5-kiem-nguoi-xu-ly-phieu`.

## Danh sách lỗi

Không phát hiện lỗi (**0 🔴 Critical, 0 🟡 Major, 0 🟢 Minor**).

## Chi tiết kiểm tra

### A. Đối chiếu nghiệp vụ, SPEC và các mục của Task 9.5

| Mục | Kết quả |
|---|---|
| `src/java/poscs/service/TicketService.java` | Đạt. Đổi `isAssignedTechnicianValid(int, Integer)` (dòng 478–489) từ `private` sang `public`, giữ nguyên tên, chữ ký và toàn bộ luật: `<= 0` → `false` (kể cả khi `keepTechnicianId` trùng); trùng `keepTechnicianId` → `true` (cho giữ người xử lý cũ dù đã khoá hoặc đổi vai); còn lại tra `employeeDAO.findById(technicianId)` (fail-closed khi null/SQLException), yêu cầu `!tech.isDeleted()`, `tech.getRole() != null` và `HANDLER_ROLE.equals(tech.getRole().getRoleName())` (`"Sales"`). Javadoc (dòng 462–477) ghi rõ dùng chung cho hai lối `/ticket/action` và `/ticket`. Giữ nguyên `createTicket` / `updateTicket` đúng mục "Không đụng". |
| `src/java/poscs/controller/TechnicalSupportTicketController.java` | Đạt. `handleCreate` (dòng 317–321): ngay sau `isValid(t)`, gọi `getTicketService().isAssignedTechnicianValid(t.getAssignedTechnicianId(), null)`; sai thì redirect `/ticket?action=new&error=invalid_handler` và dừng trước `customerIsBuyer`, `contractMatchesEnterprise`, `generateNextTicketCode`, `insert`. `handleUpdate` (dòng 392–397): ngay sau `isValid(t) \|\| !isAllowedStatus`, gọi `getTicketService().isAssignedTechnicianValid(t.getAssignedTechnicianId(), existing.getAssignedTechnicianId())`; giữ nguyên người xử lý cũ (kể cả đã nghỉ/khoá hoặc đổi vai) thì vẫn lưu được; sai thì redirect `/ticket?action=edit&id=...&error=invalid_handler` và dừng trước `update`. Comment trích dẫn `BR-39`; Javadoc `isValid` (dòng 545–547) cập nhật đúng hành vi tách kiểm tra. |
| `web/jsp/customersupport/addnewTicket.jsp` & `web/jsp/customersupport/updateTicket.jsp` | Đạt. Đều thêm nhánh `<c:when test="${param.error == 'invalid_handler'}">Vui lòng chọn người xử lý.</c:when>` trong khối `<c:choose>` báo lỗi theo `param.error` (dòng 116–117 ở `addnewTicket.jsp`, dòng 136–137 ở `updateTicket.jsp`). Khớp từng ký tự với `TicketValidator.MSG_TECHNICIAN_REQUIRED` (`"Vui lòng chọn người xử lý."`), không thêm MSG mới. Dùng JSP comment `<%-- ... --%>` (không xuất ra HTML trình duyệt), giữ nguyên `csrfToken` và không chứa logic nghiệp vụ. Cả 2 JSP dịch sạch qua Jasper Tomcat 11 (`0 errors`). |
| `src/java/poscs/controller/TicketActionController.java` | Đạt. `showEditForm` (dòng 150–175) và `handleUpdate` (dòng 177–206) đều lấy `currentUser = AccessControl.currentUser(request)` và kiểm `!getPermissionService().canUpdateTicket(currentUser, null)` **trước** khi đọc tham số `id`/`ticketId` và gọi `findById`; không có quyền (Tổng hợp, vai Kỹ thuật cũ, tài khoản khoá, chưa đăng nhập) thì trả `403 PermissionService.MSG_022` dù `id` có tồn tại hay không và không truy vấn CSDL phiếu. Toàn bộ 5 hành động trong `TicketActionController` nay nhất quán kiểm quyền trước khi đụng DB; phần xử lý sau bước kiểm quyền giữ nguyên. |

### B & C. Bảo mật, kiến trúc, tiêu chuẩn code (§3–§4)

- **Bảo mật & chống rò rỉ tồn tại bản ghi:**
  - Server-side validation (BR-39) chặn POST tự dựng gán phiếu cho Admin, Tổng hợp, Sales đã khoá hoặc id không tồn tại ở cả hai lối `/ticket` và `/ticket/action`.
  - Thứ tự kiểm quyền trước `findById` ở `TicketActionController.showEditForm` / `handleUpdate` loại bỏ chênh lệch phản hồi `403` vs `302 notfound`, khớp `SPEC.md` §3.3 (`MSG-022`) và đồng nhất với `TechnicalSupportTicketController`.
  - Hai form JSP giữ nguyên `<input type="hidden" name="csrfToken" value="${csrfToken}">`; `param.error` chỉ so sánh trong `<c:when>` với chuỗi cứng, không echo trực tiếp (không có nguy cơ XSS); redirect `id` trong `handleUpdate` đã parse `Integer`.
- **Phân tầng & quy chuẩn code:** Không có `javax.*`, không có placeholder hay `// TODO`; dùng đúng `TicketService.isAssignedTechnicianValid` thay vì viết lặp logic kiểm vai/trạng thái người xử lý ở controller. `TicketService.isAssignedTechnicianValid` 12 dòng; `TicketActionController.showEditForm` 26 dòng, `handleUpdate` 30 dòng, toàn file 232 dòng (≤ 250); `TechnicalSupportTicketController.handleCreate` 36 dòng (≤ 40).
- **Encoding UTF-8:** Đã kiểm chứng 8/8 file thay đổi thuộc phạm vi Task 9.5 hợp lệ UTF-8 strict (không BOM).

### D. Phạm vi, Không đụng & Tiêu chí "Xong khi"

- **Nhánh & commit:** Đang ở đúng nhánh `fix/task-9.5-kiem-nguoi-xu-ly-phieu` (tách từ `0a3f2fd`, trùng `main`), chưa có commit bước Code (`git diff main HEAD` rỗng).
- **Phạm vi file:** Đúng 8 file trong mục "File sửa" và "File test" của Task 9.5 (`TicketService.java`, `TechnicalSupportTicketController.java`, `TicketActionController.java`, `addnewTicket.jsp`, `updateTicket.jsp`, `TechnicalSupportTicketControllerTest.java`, `TicketActionControllerTest.java`, `TicketServiceTest.java`).
- **Không đụng:**
  - Giữ nguyên dropdown người xử lý (`findActiveTechnicians` / `findActiveByRole`).
  - Giữ nguyên tên biến, tên cột, tham số có chữ `technician`.
  - Không sửa `TicketService.createTicket` / `updateTicket`.
  - Giữ nguyên servlet `/ticket/action`, chỉ đổi thứ tự kiểm quyền ở `showEditForm` và `handleUpdate`.
- **Tiêu chí "Xong khi":**
  1. POST tự dựng tới `/ticket` giao phiếu cho Admin hoặc `tonghop1` (và Sales đã khoá / id không tồn tại) bị từ chối với `error=invalid_handler` → hiển thị `"Vui lòng chọn người xử lý."`, không ghi DB — Đạt.
  2. Sửa phiếu cũ giữ nguyên người xử lý đã nghỉ (khoá) vẫn lưu được — Đạt.
  3. Vai không có quyền gọi `/ticket/action?action=edit&id=<id không tồn tại>` (hoặc thiếu `id`, hoặc `POST action=update`) nhận `403 MSG-022`, không chuyển về `notfound` và không gọi `findById` — Đạt.

### E. Kiểm thử

- **Không sửa assertion hay xoá test cũ (rules §3):**
  - `test/poscs/controller/TechnicalSupportTicketControllerTest.java` (75 → 84 tests, +9 test mới): chỉ thêm 1 dòng stub dữ liệu nền `when(employeeDAO.findById(50)).thenReturn(employee(50, "Sales", false));` trong `setUp()` kèm helper tạo `User` `employee(int, String, boolean)`. Đã rà toàn bộ các test có sẵn gọi `loginAs(..., 50)` (dòng 397, 1237, 1298, 1345): đều đi qua `AccessControl.requireFullAccess` đọc role từ session nên không bị ảnh hưởng bởi stub `employeeDAO.findById(50)`. Bao phủ đầy đủ các kịch bản tạo phiếu với Admin, Tổng hợp, Sales đã khoá, id không tồn tại, Sales đang hoạt động khác; sửa phiếu đổi sang Admin/Tổng hợp, đổi sang Sales đã khoá, giữ nguyên người xử lý cũ đã khoá, đổi sang Sales đang hoạt động khác.
  - `test/poscs/controller/TicketActionControllerTest.java` (31 → 33 tests, +2 test mới): không sửa test cũ; thêm 2 test kiểm 403 `MSG-022` trước `findById` cho cả `showEditForm` (Tổng hợp, id không tồn tại và thiếu id) và `handleUpdate` (vai Kỹ thuật cũ, `ticketId` không tồn tại và thiếu `ticketId`).
  - `test/poscs/service/TicketServiceTest.java` (29 → 31 tests, +2 test mới): không sửa test cũ; thêm 2 test gọi thẳng `isAssignedTechnicianValid` cho tạo mới (Sales quản lý vùng, Sales có cấp trên → `true`; Admin, Tổng hợp, Sales đã khoá, id 999, 0, -1 → `false`) và cập nhật (giữ Sales đã khoá, giữ người đổi sang Tổng hợp, đổi sang Sales đang hoạt động → `true`; đổi sang Admin, đổi sang Sales đã khoá, bỏ trống 0 → `false`).
- **Kết quả các bộ test liên quan trực tiếp:** `TechnicalSupportTicketControllerTest` (84/84), `TicketActionControllerTest` (33/33), `TicketServiceTest` (31/31), `TechnicalSupportTicketDAOTest` (34/34), `TicketDetailControllerTest` (10/10), `TicketListControllerTest` (14/14), `TicketExportControllerTest` (10/10), `TicketExportServiceTest` (16/16), `TicketValidatorTest` (29/29). Toàn bộ `ant test`: **2.214 pass / 0 fail / 0 error / 3 skipped**.

## Ghi chú ngoài phạm vi & Bước Commit

- **Không có migration CSDL:** Task 9.5 không chạm `db/` và không đổi schema.
- **Tình trạng Git cho bước Commit:**
  - `task_tracker.md` đang có sẵn diff kế hoạch của người dùng (quyết định 14–15, Giai đoạn 10, Task 9.5, ghi chú `task_tracker_deploy.md`) và vẫn giữ `[ ]` ở Task 9.5.
  - Hai file untracked `docs/PROMPTS.md` và `task_tracker_deploy.md` nằm ngoài phạm vi Task 9.5 — bước Commit không `git add` hai file này.
  - Danh sách file của Task 9.5 cho bước Commit: 8 file code/JSP/test nêu ở mục D, `docs/qa/Task-9.5.md`, và phần cập nhật `[x]` cho Task 9.5 trong `task_tracker.md` (theo chỉ dẫn của người dùng ở bước Commit).
- **Quan sát ngoài phạm vi (không phải lỗi của Task 9.5):**
  - `TicketService.updateTicket` vẫn gọi `ticketDAO.findById` trước `permissionService.checkCanUpdateTicket`; khi đi qua `TicketActionController.handleUpdate` yêu cầu đã được chặn quyền trước đó ở controller, và Task 9.5 quy định rõ không đụng `TicketService.updateTicket`/`createTicket`.
  - `TechnicalSupportTicketController.handleUpdate` dài 75 dòng (vốn 69 dòng trước Task 9.5 ở controller legacy 588 dòng); việc bổ sung 6 dòng kiểm tra tại chỗ bám đúng chỉ dẫn mục "File sửa" của Task 9.5.
- **Integration test (`test/poscs/integration/`):** 3 bộ integration test mặc định skipped khi chạy `ant test`; Task 9.5 không thay đổi SQL, schema hay ranh giới giao dịch.
