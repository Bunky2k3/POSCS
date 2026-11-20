# QA Report - Task 9.4

**Kết luận:** APPROVED (ĐẠT)
**Kết quả `ant compile-jsps test`:** `compile-jsps` báo `BUILD SUCCESSFUL` (trên máy này `compile.jsps=false` bỏ qua bước dịch trong Ant; đã dịch kiểm chứng riêng 2/2 file `.jsp` đã sửa bằng `org.apache.jasper.JspC` của Tomcat 11.0 với `-source 21 -target 21`: **0 errors**, sinh sạch `updateTicket_jsp.class` và `viewdetailTicket_jsp.class`); **`ant test`: 2.201 pass / 0 fail / 0 error / 3 skipped** (92 test suites: 89 unit test suites pass 100%, 3 integration test suites mặc định bỏ qua; `BUILD SUCCESSFUL`).

**Phạm vi:** Task 9.4 — Dọn phần còn sót sau khi bỏ vai Kỹ thuật (Task 9.3): bỏ code không còn ai đi vào ở luồng sửa phiếu (`fullAccess`, nhánh `else` của `mergeForUpdate`, nhánh `!fullAccess` và `validateTechnicianFields` ở `TicketService`; thuộc tính `lockGeneral` ở `TicketActionController`; toàn bộ khối/thuộc tính/JS `lockGeneral` ở `updateTicket.jsp`), cập nhật comment cũ nhắc vai/kỹ thuật viên ở `updateTicket.jsp`, `viewdetailTicket.jsp`, `TechnicalSupportTicketDAO.java`, `TechnicalRequest.java`, và đồng bộ `.gemini/rules.md` §1 (kèm bản gương `rules.md` §1). Đối chiếu với `task_tracker.md`, `docs/SPEC.md` và `rules.md`; thay đổi thực tế lấy từ `git status` và `git diff` trên nhánh `refactor/task-9.4-don-sau-bo-vai-ky-thuat`.

## Danh sách lỗi

Không phát hiện lỗi (**0 🔴 Critical, 0 🟡 Major, 0 🟢 Minor**).

## Chi tiết kiểm tra

### A. Đối chiếu nghiệp vụ, SPEC và các mục của Task 9.4

| Mục | Kết quả |
|---|---|
| `src/java/poscs/service/TicketService.java` | Đạt. Đã kiểm chứng `PermissionService.canUpdateTicket(actor, ...)` và `canCreateTicket(actor)` đều cùng điều kiện `isAdmin(actor) \|\| isSales(actor)` trên user đang hoạt động, nên `fullAccess` sau `checkCanUpdateTicket` luôn đúng. `updateTicket` đã bỏ biến `fullAccess`; `mergeForUpdate` bỏ tham số `fullAccess` và nhánh `else`, luôn nạp đầy đủ các trường chung từ form (giữ logic `contractPickerUsable` và `slaClearedOnPurpose`); `validateUpdateRequest` bỏ tham số `fullAccess`, bỏ nhánh gọi `validateTechnicianFields` và `if (!fullAccess) return ok`, luôn chạy đủ `ticketValidator.validateUpdate`, `isAssignedTechnicianValid` và `validateCustomerAndContract`; đã xoá `validateTechnicianFields`; giữ nguyên `copyGeneralFieldsFromExisting` (còn dùng trong `syncUpdatedFields`). Không đổi chữ ký public API và không đổi hành vi UC-22 / BR-39 / BR-40 (`MSG-051`) / `MSG-022`. |
| `src/java/poscs/controller/TicketActionController.java` | Đạt. `showEditForm` (dòng 150–172) đã bỏ `request.setAttribute("lockGeneral", !fullAccess)` và giữ nguyên `request.setAttribute("canManage", fullAccess)` đúng yêu cầu. Không đụng thứ tự kiểm quyền trước `findById` (thuộc Task 9.5 theo mục "Không đụng"). |
| `web/jsp/customersupport/updateTicket.jsp` | Đạt. Đã bỏ `<c:set var="lockGeneral" value="${not canManage}"/>`, khối alert `<c:if test="${lockGeneral}">`, nhãn `Chỉ xem`, toàn bộ 8 chỗ `${lockGeneral ? 'disabled' : ''}` trên các ô nhập (`customerPickerField`, `ticketType`, `priority`, `receptionChannel`, `slaDeadline`, `technician`, `isWarranty`, `description`), biến JS `lockGeneral` và các nhánh `if (lockGeneral)` trong `openCustomerPicker()` và `loadContracts()`. Giữ đúng class `disabled` ban đầu trên `#contractPickerField` khi đang nạp AJAX (và bỏ `disabled` khi tải xong). Nút hướng dẫn (dòng 119) trỏ thẳng `#sua`. Comment đầu trang, CSS, tiêu đề khối và khối 2 đều đã viết lại chính xác (chỉ Admin và Sales mở được form, sửa được mọi ô). Dịch sạch trên Jasper Tomcat 11 (`0 errors`). |
| `web/jsp/customersupport/viewdetailTicket.jsp` | Đạt. Đã cập nhật comment quanh dòng 257 và 291–300: đổi "kỹ thuật viên" → "người xử lý", bỏ ý "role Kỹ thuật được sửa năm trường", nêu rõ cách chia khối chỉ để dễ đọc (Admin và Sales sửa được cả hai khối). Không thay đổi HTML/EL/JS. Dịch sạch trên Jasper Tomcat 11 (`0 errors`). |
| `src/java/poscs/dao/TechnicalSupportTicketDAO.java` | Đạt. Đã cập nhật đủ 5 chỗ comment (dòng 259–261, 283–285, 336, 466–467, 488) cho đúng cơ cấu mới; không thay đổi bất kỳ dòng code thực thi hay SQL nào. |
| `src/java/poscs/model/TechnicalRequest.java` | Đạt. Đã sửa comment dòng 28 (`kỹ thuật viên` → `người xử lý`); không thay đổi bất kỳ dòng code nào. |
| `.gemini/rules.md` & `rules.md` (§1, dòng 28) | Đạt. Đổi `"Chỉ 3 vai trò: Admin, Sales, Kỹ thuật."` thành `"Chỉ 3 vai trò: Admin, Sales, Tổng hợp."` ở cả `.gemini/rules.md` (theo mục "File sửa") và `rules.md` (theo tiêu đề Task 9.4 `rules.md §1` và ghi chú Task 9.1/9.3); hai file tiếp tục giống nhau từng byte, tránh việc `rules.md` (ưu tiên số 1 trong `GEMINI.md`) mâu thuẫn với `.gemini/rules.md`. |

### B & C. Bảo mật, kiến trúc, tiêu chuẩn code (§3–§4)

- **Không đổi hành vi & giữ nguyên public API:** Toàn bộ phương thức bị đổi chữ ký hoặc xoá trong `TicketService` đều là `private`; `PermissionService.canUpdateTicket(User, Integer)` được giữ nguyên.
- **Bảo mật:** `updateTicket.jsp` giữ nguyên `<input type="hidden" name="csrfToken" value="${csrfToken}">`, giữ nguyên `fn:escapeXml` cho mọi dữ liệu đầu ra; kiểm soát quyền ở server (`AccessControl.requireFullAccess`, `PermissionService.checkCanUpdateTicket`) không thay đổi.
- **Quy chuẩn code:** Không có `javax.*`, không có placeholder hay `// TODO`; mọi phương thức đã chỉnh đều ngắn gọn ≤ 40 dòng (`TicketService.updateTicket` 30 dòng, `mergeForUpdate` 27 dòng, `validateUpdateRequest` 15 dòng; `TicketActionController.showEditForm` 23 dòng, toàn file 228 dòng ≤ 250).
- **Encoding UTF-8:** Đã kiểm chứng 9/9 file thay đổi hợp lệ UTF-8 strict (không BOM).

### D. Phạm vi, Không đụng & Tiêu chí "Xong khi"

- **Nhánh & commit:** Đang ở đúng nhánh `refactor/task-9.4-don-sau-bo-vai-ky-thuat` (tách từ `abfc65c`, trùng `main`), chưa có commit bước Code (`git diff main HEAD` rỗng).
- **Phạm vi file:** 9 file thay đổi (`.gemini/rules.md`, `rules.md`, `TicketActionController.java`, `TechnicalSupportTicketDAO.java`, `TechnicalRequest.java`, `TicketService.java`, `TicketActionControllerTest.java`, `updateTicket.jsp`, `viewdetailTicket.jsp`) khớp phạm vi Task 9.4 (`rules.md` đi kèm `.gemini/rules.md` như giải thích ở mục A).
- **Không đụng:**
  - Giữ nguyên toàn bộ tên biến, cột DB, tham số, hằng mang chữ `technician` (`assignedTechnicianId`, `assigned_technician_id`, `findActiveTechnicians`, `FIELD_ASSIGNED_TECHNICIAN_ID`, `MSG_TECHNICIAN_REQUIRED`, ...).
  - Giữ nguyên các comment lịch sử giải thích "vai Kỹ thuật cũ đã gộp vào Sales (V42)" và chữ ký `PermissionService.canUpdateTicket(User, Integer)`.
  - Không đụng `web/jsp/guide/` (Task 10.6), không đụng `docs/SPEC.md`, không đụng hai điểm đổi hành vi để dành cho Task 9.5 (`TechnicalSupportTicketController` kiểm người xử lý Sales ở server và thứ tự kiểm quyền trước `findById` ở `TicketActionController`).
- **Tiêu chí "Xong khi":**
  1. `git grep -n -E "lockGeneral|validateTechnicianFields"` trên `TicketService.java`, `TicketActionController.java`, `updateTicket.jsp` (và toàn bộ `src/`, `web/`, `test/`): **0 kết quả** — Đạt.
  2. `git grep -n "fullAccess" -- src/java/poscs/service/TicketService.java`: **0 kết quả** — Đạt.
  3. `git grep -n -E "Kỹ thuật|kỹ thuật viên" -- src/java web/jsp/customersupport .gemini/rules.md rules.md`: chỉ còn 8 dòng comment ghi lịch sử V42 (và khi tìm không phân biệt hoa/thường chỉ có thêm tên phiếu "hỗ trợ kỹ thuật" / cụm từ kỹ thuật chung không liên quan vai trò); không còn bất kỳ chuỗi "kỹ thuật viên" hay "KTV" nào — Đạt.
  4. Sales mở form sửa phiếu vẫn sửa được mọi ô như trước (đã đối chiếu `updateTicket.jsp`, `TicketService.updateTicket`, `TicketServiceTest` 29/29 và `TicketActionControllerTest` 31/31) — Đạt.

### E. Kiểm thử

- Không thêm test mới (đúng yêu cầu: task refactor dọn code chết, không có logic mới).
- `test/poscs/controller/TicketActionControllerTest.java`: chỉ bỏ đúng 2 dòng `verify(request).setAttribute(eq("lockGeneral"), eq(false))` (tại dòng cũ 340 và 372, đúng ngoại lệ người dùng đồng ý ngày 20/11/2026); giữ nguyên toàn bộ phần còn lại của hai test (bao gồm cả tên hàm `showEditForm_adminOrSales_opensFormWithCanManageTrueAndLockGeneralFalse` đúng chỉ dẫn "phần còn lại của hai test giữ nguyên") và không sửa bất kỳ file test nào khác.
- Kết quả các bộ test liên quan trực tiếp: `TicketServiceTest` (29/29), `TicketActionControllerTest` (31/31), `TechnicalSupportTicketControllerTest` (75/75), `TechnicalSupportTicketDAOTest` (34/34), `TicketDetailControllerTest` (10/10), `TicketListControllerTest` (14/14), `TicketExportControllerTest` (10/10), `TicketExportServiceTest` (16/16), `TicketValidatorTest` (29/29). Toàn bộ `ant test`: **2.201 pass / 0 fail / 0 error / 3 skipped**.

## Ghi chú ngoài phạm vi & Bước Commit

- **Không có migration CSDL:** Task 9.4 không chạm `db/` và không đổi schema.
- **Tình trạng Git cho bước Commit:**
  - `task_tracker.md` đang có sẵn diff kế hoạch của người dùng (quyết định 14–15, Giai đoạn 10, Task 9.4–9.5, ghi chú `task_tracker_deploy.md`) và vẫn giữ `[ ]` ở Task 9.4.
  - Hai file untracked `docs/PROMPTS.md` và `task_tracker_deploy.md` nằm ngoài phạm vi Task 9.4 — bước Commit không `git add` hai file này.
  - Lưu ý `rules.md` (bản gương của `.gemini/rules.md`, được nêu ở tiêu đề Task 9.4) cũng đã được đồng bộ dòng 28 — bước Commit liệt kê rõ cả hai file này cùng 7 file code/JSP/test và `docs/qa/Task-9.4.md`.
- **Nợ kỹ thuật giữ nguyên cho Task 9.5:** Hai mục đã được tách rõ sang Task 9.5 (kiểm người xử lý là Sales đang hoạt động ở lối `/ticket` của `TechnicalSupportTicketController`, và kiểm quyền trước `findById` ở `TicketActionController`) được giữ nguyên đúng mục "Không đụng".
- **Integration test (`test/poscs/integration/`):** 3 bộ integration test mặc định skipped khi chạy `ant test`; Task 9.4 không thay đổi SQL, schema hay ranh giới giao dịch.
