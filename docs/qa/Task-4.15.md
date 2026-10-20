# QA Report - Task 4.15
**Kết luận:** ĐÃ FIX (4/4 mục đã sửa)
**Kết quả `ant test`:** 1577 pass / 0 fail / 3 skipped (tổng 1580 tests; 3 skipped là test tích hợp `_it`, không đụng DB thật)

**Phạm vi review:**
- File code: `src/java/poscs/controller/ContractPaymentController.java`, `src/java/poscs/controller/ContractController.java`
- File test: `test/poscs/controller/ContractPaymentControllerTest.java`, `test/poscs/controller/ContractControllerTest.java`
- Đối chiếu: UC-53 và BR-18 trong `docs/SPEC.md`; quy chuẩn kiến trúc và bảo mật trong `rules.md`, `.gemini/rules.md`.

**Đánh giá tổng quan:**
Code của Task 4.15 đã hoàn thành phần lớn mục tiêu thiết kế và bảo mật: xây dựng `ContractPaymentController` chuyên trách đáp ứng UC-53 và BR-18, xử lý phân quyền qua `PermissionService` (vi phạm trả HTTP 403 `MSG-022`), bóc tách số tiền VNĐ qua `MoneyVnd`, và có bộ test suite phong phú gồm 36 test cases pass 100%. Tất cả các mục QA phản hồi (1 🟡, 3 🟢) đã được xử lý triệt để và bổ sung unit test tương ứng.

---

## Danh sách lỗi

- [x] 🟡 `ContractController.java` › `handleAddPayment()`, `handleMarkPaid()`, `handleRemovePayment()` (dòng ~1344-1414)
  - Vi phạm: rules §4 (Tinh gọn Controller và ủy quyền sang Controller chuyên trách)
  - Mô tả: `ContractController.java` hiện vẫn chứa hơn 70 dòng logic quản lý kỳ thanh toán cũ gọi trực tiếp `contractDAO.insertPayment/markPaymentPaid/deletePayment` và sử dụng cơ chế kiểm quyền cũ `AccessControl.requireFullAccess`. Trong khi đó, các form giao diện tại `web/jsp/sale/updatecontract.jsp` (dòng 722 và 1354) đều gửi request POST về `/contract`. Nếu `ContractController` không ủy quyền sang `ContractPaymentController`, mọi thao tác của người dùng trên UI thực tế sẽ chạy qua code cũ của `ContractController` thay vì `ContractPaymentController` và `ContractPaymentService`.
  - Gợi ý sửa: Thêm trường `paymentController`, getter/setter và ủy quyền 3 handler sang `ContractPaymentController`.
  - *Dev note đã sửa:* Đã thêm `paymentController` cùng getter/setter lazy initialization vào `ContractController.java`, đồng thời tinh gọn 3 handler thành các lệnh ủy quyền trực tiếp sang `getPaymentController()`. Đã bổ sung 4 unit tests mới trong `ContractControllerTest.java` kiểm tra định tuyến và lazy init (tổng 96 tests pass 100%).

- [x] 🟢 `ContractPaymentController.java` › `getPermissionService()` (dòng ~69-74)
  - Vi phạm: rules §4 (Kiến trúc phân tầng & Quản lý Dependency Injection)
  - Mô tả: Phương thức `getPermissionService()` kiểm tra `if (permissionService != null) return permissionService;` nhưng ở nhánh dưới lại `return new PermissionService(employeeDAO);` mà không gán ngược lại vào `this.permissionService`. Điều này làm mất tác dụng của cơ chế lazy initialization và tạo mới instance `PermissionService` mỗi lần được gọi.
  - Gợi ý sửa: Gán `this.permissionService = new PermissionService(employeeDAO);` khi `permissionService == null`.
  - *Dev note đã sửa:* Đã cập nhật `getPermissionService()` gán trường `this.permissionService`. Bổ sung assertion kiểm tra instance caching trong `ContractPaymentControllerTest.testGettersSettersAndLazyInitialization()`.

- [x] 🟢 `ContractPaymentController.java` › `handleServiceError()` (dòng ~202-216)
  - Vi phạm: Checklist B (An toàn đầu vào & xử lý ID hợp đồng không hợp lệ)
  - Mô tả: Khi client truyền `contractId <= 0` (ví dụ `contractId = -1`), các phương thức `markPaymentPaid` và `deletePayment` trong `ContractPaymentService` trả về mã lỗi `ERR_INVALID`. Trong `handleServiceError`, mã lỗi này không khớp với `ERR_FORBIDDEN` hay `ERR_NOT_FOUND`, dẫn đến controller chuyển hướng người dùng về URL `/contract?action=edit&id=-1&error=payment_failed` chứa ID âm không hợp lệ (sau đó trang đích mới redirect tiếp về `notfound`).
  - Gợi ý sửa: Kiểm tra `contractId <= 0` để điều hướng ngay về `notFoundUrl(request)`.
  - *Dev note đã sửa:* Đã cập nhật `handleServiceError()` kiểm tra `contractId <= 0 || ContractPaymentService.ERR_NOT_FOUND.equals(errorMessage)`, đồng thời chặn ID không hợp lệ ngay tại `handleAddPayment`, `handleMarkPaid`, `handleRemovePayment`. Bổ sung unit tests cho ID âm/0/non-numeric trong `ContractPaymentControllerTest.java`.

- [x] 🟢 `ContractPaymentController.java` › `handleMarkPaid()` (dòng ~172-173)
  - Vi phạm: Checklist B (Xử lý chuỗi ngày tháng sai định dạng)
  - Mô tả: Trong `handleMarkPaid`, chuỗi `paidDate` được phân tích bằng `ContractControllerHelper.parseDateOrNull(request.getParameter("paidDate"))`. Nếu người dùng cố ý hoặc vô tình truyền chuỗi ngày tháng không rỗng nhưng sai định dạng (ví dụ `paidDate=abc` hoặc `paidDate=2026-99-99`), `parseDateOrNull` trả về `null`. Service nhận `paidDate == null` sẽ âm thầm gán ngày mặc định là ngày hiện tại (`LocalDate.now()`). Điều này có thể dẫn đến việc ghi nhận sai ngày thanh toán ngoài ý muốn của người dùng.
  - Gợi ý sửa: Kiểm tra chuỗi thô từ request, nếu có nhập nhưng parse ra `null` thì báo lỗi thay vì âm thầm lấy ngày hôm nay.
  - *Dev note đã sửa:* Đã thêm validation chuỗi ngày thô `rawPaidDate != null && !rawPaidDate.trim().isEmpty() && paidDate == null` thì điều hướng báo lỗi `payment_failed` (ERR_INVALID). Bổ sung unit test `testMarkPaid_invalidDateFormat_redirectsToPaymentFailed()` trong `ContractPaymentControllerTest.java`.
