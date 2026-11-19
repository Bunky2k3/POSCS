# QA Report - Task 8.3

**Kết luận:** ĐẠT (không có lỗi cần sửa)
**Kết quả `ant test`:** 2.025 pass / 0 fail / 0 error / 3 skipped (tổng 2.028 test ở 92 lớp, `BUILD SUCCESSFUL`; `BranchInfoValidatorTest` 21/21, `BranchInfoServiceTest` 15/15, `BranchInfoControllerTest` 14/14 pass; 3 skipped là test tích hợp mặc định bỏ qua)
**Kết quả `ant compile-jsps`:** `BUILD SUCCESSFUL`

**Phạm vi:** Task 8.3 xây dựng trang "Thông tin chi nhánh" (`/branch`) cho Admin xem và sửa thông tin Giám đốc chi nhánh (`BranchInfoValidator`, `BranchInfoService`, `BranchInfoController`, `branchInfo.jsp`), thêm mục menu ở `sidebar.jsp`, cập nhật `PERMISSIONS.md` và `docs/ARCHITECTURE.md`, kèm 3 lớp unit test. Đối chiếu với `task_tracker.md` (UC-55; BR-57; MSG-057, MSG-058, MSG-059, MSG-015, MSG-022), `docs/SPEC.md` (§5.8, §6, §9) và `rules.md` (§3–§4); kiểm tra trên nhánh `feat/task-8.3-branch-info-page`.

## Danh sách lỗi

Không phát hiện lỗi 🔴 Critical, 🟡 Major hay 🟢 Minor.

## Chi tiết kiểm tra

**A. Phạm vi & Git**
- Nhánh hiện tại: `feat/task-8.3-branch-info-page`, khớp dòng "Nhánh" của Task 8.3.
- Các file thuộc task (10 file):
  - `src/java/poscs/validator/BranchInfoValidator.java` (mới, 119 dòng)
  - `src/java/poscs/service/BranchInfoService.java` (mới, 115 dòng)
  - `src/java/poscs/controller/BranchInfoController.java` (mới, 124 dòng)
  - `web/jsp/admin/branchInfo.jsp` (mới, 198 dòng)
  - `web/jsp/common/sidebar.jsp` (sửa: thêm mục menu Admin và cập nhật comment `activeNav`)
  - `PERMISSIONS.md` (sửa: thêm `BranchInfoController` và dòng UC-55 chỉ Admin kèm chú thích `‡`)
  - `docs/ARCHITECTURE.md` (sửa: thêm đủ 5 lớp `BranchInfo*` vào các tầng, cập nhật số POJO 27 → 28 khớp thực tế `src/java/poscs/model/`)
  - `test/poscs/validator/BranchInfoValidatorTest.java` (mới, 275 dòng)
  - `test/poscs/service/BranchInfoServiceTest.java` (mới, 234 dòng)
  - `test/poscs/controller/BranchInfoControllerTest.java` (mới, 287 dòng)
- Phần "Không đụng": không chạm form/luồng hợp đồng (Task 8.4), `EmployeeController`, `EmployeeDAO`. Đạt.
- Mã hóa & công nghệ: cả 10 file đều là UTF-8 thuần (không BOM, không UTF-16), tiếng Việt đúng chuẩn; không dùng `javax.*` (dùng hoàn toàn `jakarta.*`). Đạt.

**B. Đối chiếu SPEC (UC-55, BR-57, MSG-057/058/059/015/022/018)**
- `BranchInfoValidator`:
  - Kế thừa `BaseValidator`, không dùng `BaseValidator.PHONE_VN`.
  - Trim trước khi kiểm ở cả 4 ô.
  - Họ tên và chức vụ GĐCN bắt buộc (thiếu → `MSG-058` nguyên văn: `"Vui lòng nhập họ tên và chức vụ Giám đốc chi nhánh."`), tối đa 100 ký tự.
  - SĐT và fax không bắt buộc; khi có nhập thì kiểm độ dài tối đa 20 ký tự, chỉ gồm chữ số `0–9`, dấu `+` ở đầu, khoảng trắng, dấu chấm, gạch ngang, ngoặc đơn, và có 8–15 chữ số ASCII. Sai → `MSG-015` (`"Số điện thoại không hợp lệ."`) / `MSG-059` (`"Số fax không hợp lệ."`) theo từng ô.
- `BranchInfoService`:
  - Kế thừa `BaseService`, có `get()`, `findUpdaterName(BranchInfo)` và `update(BranchInfo, User)`.
  - `update` kiểm quyền Admin qua `PermissionService.isAdmin(actor)` (đọc DB, fail-closed, sai trả `MSG-022`), chuẩn hóa trim/rỗng → `null`, chạy `BranchInfoValidator` (lỗi không gọi DAO), gọi `BranchInfoDAO.update`, trả `ServiceResult` kèm `MSG-057` (`"Đã lưu thông tin chi nhánh."`) hoặc `MSG_SAVE_FAILED`.
- `BranchInfoController`:
  - `@WebServlet(name = "BranchInfoController", urlPatterns = {"/branch"})`.
  - Chặn quyền một lần ở đầu `doGet`/`doPost` bằng `AccessControl.requireAdmin(req, resp)` (vai khác hoặc chưa đăng nhập nhận 403 `MSG-022`, không gọi service). Nếu service trả `MSG_FORBIDDEN` (phiên Admin cũ đã bị đổi vai ở DB) cũng trả 403 `MSG-022`.
  - `doGet`: tải dữ liệu qua `service().get()` (lỗi/thiếu dòng → `MSG-018` không mở form), hiển thị toast `MSG-057` khi `saved=1`, hiển thị dòng "Cập nhật lần cuối" với fallback `NV-<id>` khi không tìm thấy tên.
  - `doPost`: lưu thành công redirect PRG sang `/branch?saved=1`; lỗi validation hoặc lỗi lưu thì forward lại form, giữ nguyên dữ liệu vừa nhập và vẫn nạp lại dòng "Cập nhật lần cuối".

**C. Giao diện & Bảo mật (`branchInfo.jsp`, `sidebar.jsp`)**
- `branchInfo.jsp`: chỉ có xem và sửa (không có nút tạo/xoá), đủ 4 ô thông tin, khung trang/CSS đồng bộ với `updateEmployee.jsp`, set `activeNav = 'branch'`, có dòng "Cập nhật lần cuối bởi … lúc …", không dùng `alert`/`confirm` trình duyệt.
- Bảo mật: form POST có `<input type="hidden" name="csrfToken" value="${csrfToken}">` (được `AuthenticationFilter` kiểm tra); mọi dữ liệu xuất ra đều escape bằng `fn:escapeXml` hoặc `<c:out>`.
- Kiểm tra client-side JS đồng bộ quy tắc BR-57 và nội dung `MSG-058`/`MSG-015`/`MSG-059` với server.
- `sidebar.jsp`: mục "Thông tin chi nhánh" đặt ngay dưới "Nhân viên", chỉ hiện với Admin, sáng khi `activeNav == 'branch'`; đã bổ sung `branch` vào comment danh sách `activeNav`.

**D. Quy chuẩn kiến trúc & Unit Test (rules §3–§4)**
- Phân tầng sạch: JSP chỉ hiển thị, Controller chỉ kiểm quyền/đọc tham số/điều hướng, Service lo nghiệp vụ, Validator lo BR-57.
- Độ dài file & hàm: cả 3 lớp nguồn chính đều ≤ 124 dòng (≤ 250), mọi phương thức ≤ 18 dòng (≤ 40).
- Unit test phủ đầy đủ các kịch bản yêu cầu trong `task_tracker.md`:
  - `BranchInfoValidatorTest` (21/21): thiếu họ tên/chức vụ, dòng gieo chỉ có chức vụ, `null`, quá 100 ký tự, trim trước khi đo, các định dạng SĐT/fax hợp lệ (di động, máy bàn, ngoặc, khoảng trắng, dấu chấm, gạch ngang, `+84`, 8–15 chữ số, đúng 20 ký tự), các định dạng sai (chữ cái, < 8 hoặc > 15 chữ số, 8–15 chữ số nhưng > 20 ký tự như `(024) 3852 1234 - 1105`, dấu `+` sai chỗ, chữ số toàn chiều rộng), khớp nguyên văn câu MSG.
  - `BranchInfoServiceTest` (15/15): lưu thành công trả bản đã trim + `MSG-057`, dữ liệu sai không gọi DAO, DAO trả `false` báo lỗi, phân quyền `null`/Sales/Kỹ thuật/Admin bị hạ quyền ở DB → `MSG-022`, `get()`, `findUpdaterName()`.
  - `BranchInfoControllerTest` (14/14): Admin xem/lưu, Sales/Kỹ thuật/chưa đăng nhập GET & POST → 403 `MSG-022`, dòng "Cập nhật lần cuối" (đủ thông tin, fallback `NV-5`, dòng gieo chưa sửa), lỗi tải `MSG-018`, redirect `saved=1` → `MSG-057`, lỗi kiểm tra và lỗi lưu giữ nguyên dữ liệu vừa nhập, service từ chối → 403.

## Ghi chú ngoài phạm vi

- `task_tracker.md` đang có thay đổi sẵn ở working tree (kế hoạch Giai đoạn 9–10, Task 8.3 vẫn `[ ]`) và `docs/PROMPTS.md` đang untracked từ trước. Ở bước Commit Task 8.3, chỉ `git add` đúng các file thuộc Task 8.3 và `docs/qa/Task-8.3.md` (không dùng `git add .`).
- Kiểm tra thủ công trên Tomcat (đăng nhập `admin` mở menu "Thông tin chi nhánh" sửa/lưu; đăng nhập Sales gõ thẳng `/POSCS/branch` nhận 403) cần thực hiện trên môi trường đã chạy migration V39 lên `poscs_db`.
