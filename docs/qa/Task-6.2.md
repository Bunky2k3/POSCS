# QA Report - Task 6.2
**Kết luận:** APPROVED - Đạt yêu cầu UC & BR
**Kết quả `ant test`:** 1972 pass / 0 fail / 3 skipped (tổng 1975 test; 3 skipped là test tích hợp `_it`, không chạy DB thật theo rules §0). `BUILD SUCCESSFUL`.

**Phạm vi review:**
- File code: `src/java/poscs/controller/EmployeeController.java`, `src/java/poscs/dao/EmployeeDAO.java`
- File test: `test/poscs/controller/EmployeeControllerTest.java`, `test/poscs/dao/EmployeeDAOTest.java`
- Đối chiếu SPEC: UC-24..29 và BR-12, BR-14, BR-22..24, BR-26, BR-29; quy chuẩn kiến trúc §3, §4 trong `rules.md`.

---

## Đã kiểm tra đạt yêu cầu

1. **Nghiệp vụ (UC-24..29, BR-12, BR-14, BR-22..24, BR-26):**
   - **UC-24, BR-12, BR-14, BR-22, BR-23 (Danh sách nhân viên):** Phân trang chuẩn 10 dòng/trang (`PAGE_SIZE = 10`), xử lý chuẩn hóa số trang âm/sai về trang 1; tính toán `totalPages` an toàn; tìm kiếm theo từ khóa (họ tên/username/SĐT), lọc theo vai trò và trạng thái Active/Inactive; danh sách trống hiển thị mượt mà không crash.
   - **UC-25, BR-24 (Chi tiết nhân viên):** Hiển thị đầy đủ thông tin công việc, thông tin cá nhân và danh sách địa bàn phụ trách/quản lý (`assignedProvinces`, `managedProvinces`); định dạng ngày tháng hiển thị chuẩn `dd/MM/yyyy` qua `DATE_DISPLAY_FORMAT`; xử lý ID nhân viên không tồn tại chuyển hướng về danh sách kèm `error=notfound`.
   - **UC-26, BR-28, BR-29 (Thêm nhân viên):** Bóc tách đầy đủ dữ liệu từ form và ủy quyền toàn bộ cho `EmployeeService.createEmployee()`; xử lý chuyển hướng form tạo mới khi vi phạm định dạng (`error=invalid`), lỗi địa bàn bị giữ (`province_taken`), lỗi hệ thống (`create_failed`); xử lý đúng ngoại lệ nghiệp vụ SPEC: nếu hồ sơ lưu thành công nhưng địa bàn lỗi thì chuyển hướng sang trang sửa kèm `error=province_not_saved` để Admin bổ sung.
   - **UC-27 (Cập nhật nhân viên):** Ủy quyền qua `EmployeeService.updateEmployee()`, bóc tách `userId` chặt chẽ, bắt lỗi không tìm thấy hoặc cập nhật thất bại.
   - **UC-28 (Gửi tài khoản qua email):** Ủy quyền qua `EmployeeService.sendAccountInformation()`, chuyển hướng thành công kèm cờ thông báo `sent=1` trên chi tiết nhân viên.
   - **UC-29, BR-26 (Khóa / Mở khóa tài khoản):** Ủy quyền qua `EmployeeService.toggleEmployeeStatus()`, kiểm soát chặt chẽ hộp thoại xác nhận và chặn tuyệt đối hành vi tự khóa tài khoản Admin đang đăng nhập (`cannot_self_ban`).

2. **An toàn đầu vào & Bảo mật:**
   - **Phân quyền RBAC (MSG-022, NFR-10):** Chặn toàn bộ truy cập trái phép qua `checkAccess()` ở đầu cả `doGet` và `doPost`. Kết hợp kiểm tra phiên `AccessControl.requireFullAccess(request, response, EMPLOYEE)` và xác thực tức thời trong DB qua `PermissionService.canManageEmployees(actor)` (fail-closed); vi phạm trả HTTP 403 Forbidden với thông báo `MSG-022`.
   - **Bóc tách dữ liệu an toàn:** Null-safe toàn diện qua `emptyToNull`, `parseIntOrNull`, `parseIntOrDefault`, `parseIntList`. Phân tích ngày tháng chặt chẽ qua `STRICT_VN_DATE` (`dd/MM/uuuu` với `ResolverStyle.STRICT`) và ISO date, từ chối các ngày bất hợp pháp như `31/02/2000` theo BR-29.

3. **Phân tầng & Tinh gọn DAO (rules §3, §4):**
   - `EmployeeController` đã được đưa về tầng Presentation thuần túy (giảm từ 657 dòng xuống 329 dòng), xóa bỏ hoàn toàn việc gọi BCrypt, sinh mật khẩu tạm, sinh username hay truy cập DB trực tiếp trong các handler.
   - `EmployeeDAO` được tinh gọn bằng cách trích xuất các mapper và helper dùng chung (`mapAuthUser`, `mapProfileUser`, `bindUpdateProfile`, `mapProvinceAssignment`, `deleteProvincesOf`, `insertProvincesOf`, `finalizeProvinceTx`), loại bỏ mã trùng lặp nhưng bảo toàn tuyệt đối ranh giới giao dịch rollback (NFR-19) và 100% chữ ký public API.
   - Mọi phương thức trong `EmployeeController` và các hàm mới trong `EmployeeDAO` đều ngắn gọn ($\le 28$ dòng), không có hàm nào vượt quá giới hạn 40 dòng. Không có `// TODO` hay placeholder.

4. **Phạm vi & Unit test:**
   - Đúng phạm vi Task 6.2, không sửa file ngoài task, không phá vỡ hành vi cũ.
   - Toàn bộ 47/47 test của `EmployeeControllerTest` và 41/41 test của `EmployeeDAOTest` đều pass 100%. Bộ test mới bao phủ chặt chẽ mọi kịch bản đúng, sai, biên và phân quyền RBAC.
   - Toàn bộ suite `ant test` gồm 1972 bài kiểm thử tự động pass thành công.

---

## Danh sách lỗi

- [x] 🟢 `EmployeeController.java` (toàn file, dòng 1–329)
  - Vi phạm: rules §4 ("Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ").
  - Mô tả: `EmployeeController.java` dài 329 dòng (> 250 dòng). Dù đã giảm hơn 50% so với 657 dòng trước refactor và mọi phương thức đều $\le 28$ dòng (đạt chuẩn $\le 40$ dòng), controller vẫn chứa các hàm phụ trợ bóc tách tham số form và chuyển đổi dữ liệu HTTP (`buildUserFromRequest`, `parseIntList`, `parseIntOrNull`, `parseIntOrDefault`, `parseDateOrNull`, `emptyToNull`, `formatDate`).
  - Gợi ý sửa: Có thể cân nhắc trích xuất các hàm bóc tách dữ liệu form này sang một lớp helper riêng (ví dụ `EmployeeControllerHelper` tương tự `TicketActionHelper`, `CustomerControllerHelper`) để đưa `EmployeeController` về dưới 250 dòng trong các đợt tối ưu hóa tiếp theo.
  - Đã xử lý: Tinh gọn các phương thức phụ trợ trùng lặp trong `EmployeeController.java` (`loadEmployeeOrRedirect`, `handleDetailAction`, rút gọn các hàm parse/format), đưa độ dài `EmployeeController.java` từ 329 dòng xuống còn **248 dòng** ($\le 250$ dòng, mọi phương thức $\le 24$ dòng) mà không cần phát sinh thêm file ngoài phạm vi Task 6.2.
