# QA Report - Task 2.4
**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 1052 pass / 0 fail / 0 error / 3 skipped (test tích hợp DB `_it` bị bỏ qua mặc định). `BUILD SUCCESSFUL`.

Phạm vi review:
- File code:
  - `src/java/poscs/controller/CustomerController.java`
  - `src/java/poscs/controller/CustomerListController.java`
  - `src/java/poscs/controller/CustomerDetailController.java`
  - `src/java/poscs/controller/CustomerActionController.java`
  - `src/java/poscs/controller/CustomerExportController.java`
  - `src/java/poscs/controller/CustomerControllerHelper.java`
- File test:
  - `test/poscs/controller/CustomerListControllerTest.java`
  - `test/poscs/controller/CustomerDetailControllerTest.java`
  - `test/poscs/controller/CustomerActionControllerTest.java`
  - `test/poscs/controller/CustomerExportControllerTest.java`
  - `test/poscs/controller/CustomerControllerTest.java` (test khóa hành vi cũ)
- Đối chiếu: UC-08, UC-09, UC-30, UC-31, UC-32, UC-37, UC-38; BR-12, BR-13, BR-14, BR-34, MSG-022.

**Đã kiểm tra đúng:**
- Refactor thành công `CustomerController` thành Front Controller / Facade phân phối request đến 4 controller chuyên biệt (`CustomerListController`, `CustomerDetailController`, `CustomerActionController`, `CustomerExportController`), đảm bảo tương thích 100% với các route hiện có của JSP (`/customer`, `/customer?action=...`).
- BR-12 (phân trang 10 dòng/trang, chuẩn hóa page <= 0 về 1), BR-13 (giữ nguyên bộ lọc keyword, type, assignee, province, role khi phân trang và xuất Excel), BR-14 (xử lý danh sách rỗng sạch sẽ) hoạt động chính xác.
- BR-34 (xóa mềm qua `is_deleted = 1`, chặn xóa khi còn hợp đồng hiệu lực `has_active_contracts`) được thực thi qua `CustomerService.deleteCustomer`.
- UC-37 (xuất Excel không phân trang, giữ nguyên bộ lọc, chiều đối tác và phạm vi truy cập ListScope) chuyển giao chính xác cho `CustomerService.exportCustomersToExcel`.
- Bảo vệ CSRF được đảm bảo qua `AuthenticationFilter` trên `/*` cho toàn bộ POST request. Toàn bộ 1052 unit test hiện có pass.

---

## Danh sách lỗi

- [x] 🔴 `CustomerControllerHelper.java` › `createPermissionService()` (dòng ~83-137)
  - Vi phạm: rules §1, rules §3 (an toàn bảo mật), rules §4 (phân quyền chỉ qua `PermissionService`), quyết định người dùng ngày 15/10/2026.
  - Mô tả:
    1. **Lỗ hổng leo quyền khi chưa giao tỉnh:** Dòng 130–132 ghi đè `if (provinceIds.isEmpty()) return true;`. Theo quyết định đã chốt ngày 15/10/2026 (xem `Task-1.4.md`), khi Sales chưa được giao tỉnh, hệ thống chỉ cho phép quản lý khách hàng do chính mình hoặc cấp dưới trong team đứng tên (`team.contains(customer.getAccountOwnerId())`). Việc trả về `true` cho phép Sales chưa giao tỉnh được quyền sửa/xóa bất kỳ khách hàng nào của toàn công ty.
    2. **Phá vỡ cơ chế kiểm tra DB thời gian thực (fail-closed):** Trong `getEffectiveUser()` (dòng 86–95), nếu `super.getEffectiveUser()` trả về `null` (do user trong DB đã bị xóa mềm `is_deleted=1`, vô hiệu hóa hoặc không tồn tại), phương thức lại fallback trả về `sessionUser`. Điều này cho phép một tài khoản đã bị khóa/xóa trong DB vẫn thực hiện được các thao tác ghi nếu session chưa hết hạn.
    3. **Fail-open khi không tìm thấy user:** Trong `isSubordinate()` (dòng 98–112), nếu `employeeDAO.findById()` trả về `null`, phương thức rơi về `user.getManagerId() != null`. Nếu `managerId` trong session là `null`, user không xác minh được từ DB lại được cấp quyền Sales quản lý vùng (được ghi dữ liệu).
  - Gợi ý sửa:
    Loại bỏ việc override logic RBAC trong `CustomerControllerHelper.createPermissionService`. Trả về đối tượng `PermissionService` nguyên bản:
    ```java
    public static PermissionService createPermissionService(EmployeeDAO employeeDAO) {
        return new PermissionService(employeeDAO);
    }
    ```
    Đối với các test suite cũ (`CustomerControllerTest.java`), nếu cần đảm bảo `employeeDAO.findById(99)` không bị `null`, hãy đảm bảo stub `when(employeeDAO.findById(anyInt())).thenReturn(user);` hoặc tiêm `PermissionService` đã mock thay vì làm suy yếu bảo mật ở tầng helper dùng cho môi trường production.

---

- [x] 🟡 `CustomerActionController.java` › toàn file (457 dòng), `handleCreate()` (dòng ~226-273), `handleUpdate()` (dòng ~275-332)
  - Vi phạm: rules §4 ("Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ").
  - Mô tả:
    - Kích thước file `CustomerActionController.java` lên tới **457 dòng** (vượt xa ngưỡng 250 dòng).
    - Phương thức `handleCreate()` dài **48 dòng** (> 40 dòng).
    - Phương thức `handleUpdate()` dài **58 dòng** (> 40 dòng).
  - Gợi ý sửa:
    - Chuyển logic trích xuất tham số form và chuyển hướng kết quả (redirect handling) sang `CustomerControllerHelper` hoặc hàm private phụ trợ ngắn gọn.
    - Cân nhắc tách riêng luồng hiển thị form (`showCreateForm`, `showEditForm`) hoặc luồng đánh giá (`handleEvaluate`) sang controller/helper phụ trợ để đưa kích thước file về ≤ 250 dòng và các hàm ≤ 40 dòng.

---

- [x] 🟡 `CustomerListController.java` › `doGet()` (dòng ~89-150)
  - Vi phạm: rules §4 ("Hàm > 40 dòng: xem xét tách module phụ trợ").
  - Mô tả: Phương thức `doGet()` dài **62 dòng** (> 40 dòng), thực hiện cùng lúc việc đọc tham số lọc, phân giải phạm vi `ListScope`, truy vấn service và đóng gói hàng loạt thuộc tính request cho JSP.
  - Gợi ý sửa: Tách việc đọc bộ lọc và gán thuộc tính hiển thị thành 2 hàm trợ giúp private:
    ```java
    private CustomerListFilter extractFilters(HttpServletRequest request, User currentUser) { ... }
    private void bindListAttributes(HttpServletRequest request, CustomerListFilter filter, ... ) { ... }
    ```

---

- [x] 🟡 `CustomerActionControllerTest.java` › thiếu test kịch bản ngoại lệ cho `delete` và `evaluate`
  - Vi phạm: rules §4 ("Mọi logic mới hoặc nhánh mới (if/else, trường hợp biên) có test tương ứng trong test/"), checklist E.
  - Mô tả:
    - Trong `CustomerActionController.handleDelete()`: Chưa có unit test cho trường hợp `deleteCustomer` trả về `notfound` (chuyển hướng `notFoundUrl`) và `not_your_customer` (chuyển hướng `?error=not_your_customer`).
    - Trong `CustomerActionController.handleEvaluate()`: Chưa có unit test cho trường hợp `evaluateCustomer` trả về `notfound` và `evaluate_failed`.
  - Gợi ý sửa: Bổ sung các test case:
    - `delete_customerNotFound_redirectsToNotFoundUrl()`
    - `delete_notYourCustomer_redirectsToDetailWithNotYourCustomer()`
    - `evaluate_customerNotFound_redirectsToNotFoundUrl()`
    - `evaluate_failed_redirectsToDetailWithError()`

---

- [ ] 🟢 Phạm vi thay đổi file quy chuẩn: `rules.md` và `.gemini/rules.md`
  - Vi phạm: rules §2, §3 (phạm vi task).
  - Mô tả: Trong `git status`, hai file `rules.md` và `.gemini/rules.md` đang ở trạng thái modified. Dù đây là nội dung chuẩn hóa quy trình 4 bước và §7, dev cần lưu ý không commit gộp các file này vào commit của Task 2.4 nếu chưa có yêu cầu riêng.
  - Ghi chú dev: Không sửa/không rollback vì đây là hướng dẫn quy trình làm việc chung; cam kết KHÔNG gộp các file này vào commit Task 2.4.
