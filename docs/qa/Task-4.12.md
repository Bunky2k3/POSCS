# QA Report - Task 4.12
**Kết luận:** ĐÃ FIX (Chờ QA xác nhận)
**Kết quả `ant test` sau fix:** 1488 pass / 0 fail / 3 skipped (tổng 1491; 3 skipped là test tích hợp `_it`, không đụng DB thật)

**Phạm vi review:**
- File code:
  - `src/java/poscs/controller/ContractActionController.java`
  - `src/java/poscs/controller/ContractActionResultHelper.java`
  - `src/java/poscs/controller/ContractControllerHelper.java`
- File test:
  - `test/poscs/controller/ContractActionControllerTest.java`
- Đối chiếu: UC-33, UC-34, UC-35, UC-44, UC-46, UC-54 và BR-17, BR-35, BR-36, BR-37; SPEC mục 1, 4.4, 5.4, 6, 9.

**Đánh giá tổng quan:**
- **Phân tầng & Thiết kế (rules §4):** Đã tách thành công `ContractActionController` chuyên trách cho 6 use cases ghi dữ liệu hợp đồng (Tạo, Sửa, Phụ lục, Chữa sai sót, Đổi tiến độ & Ký, Huỷ bản ghi, Hàng hóa). Controller hoàn toàn không chứa logic tính toán nghiệp vụ hay câu lệnh SQL, ủy quyền nghiệp vụ toàn bộ sang `ContractService` và nhận `ServiceResult`. Kích thước các file đều tuân thủ chặt chẽ ngưỡng rules §4 (`ContractActionController.java`: 249 dòng ≤ 250, `ContractActionResultHelper.java`: 131 dòng, `ContractControllerHelper.java`: 246 dòng; mọi phương thức đều ≤ 40 dòng).
- **An toàn đầu vào & Chống lỗi dữ liệu:** Sử dụng `ContractControllerHelper` bóc tách số nguyên, ngày tháng, số lượng hàng hóa (hỗ trợ định dạng dấu chấm `1.000` của VNĐ), giá trị hợp đồng âm/dương cho phụ lục; chống XSS và Injection qua kiểm soát chặt tham số redirect.
- **CSRF & Bảo mật:** Kế thừa cơ chế phòng thủ CSRF tập trung từ `AuthenticationFilter` trên mọi request POST.
- **Tương thích giao diện & Thông báo:** Hệ thống mã lỗi điều hướng trong `ContractActionResultHelper` (`invalid`, `duplicate_code`, `update_failed`, `missing_reason`, `amendment_not_allowed`, `value_negative`, `add_product_invalid`, `add_product_failed`, `remove_product_failed`, `missing_term`, `progress_failed`, `void_reason_required`, `void_failed`, `notfound`) ánh xạ khớp 100% với các thông báo `alert-danger` và `toast-msg` trong `addnewcontract.jsp`, `updatecontract.jsp`, `viewcontractdetail.jsp`, `listcontract.jsp`.

---

## Danh sách lỗi

- [x] 🟡 `ContractActionControllerTest.java` › Thiếu unit test kịch bản phủ định (negative tests) và trường hợp biên cho các action
  - Vi phạm: rules §4 ("Mọi logic mới hoặc nhánh mới (if/else, trường hợp biên) có test tương ứng trong test/"), Checklist E.
  - Mô tả: Test suite hiện có 39 test cases bao phủ các luồng cơ bản, tuy nhiên còn thiếu các nhánh quan trọng sau:
    1. `handleDelete`: Chưa có test case khi user không có quyền (`canManageContracts(u) == false`, ví dụ Kỹ thuật) gửi POST `action=delete` -> phải trả 403 `MSG-022`; chưa có test case khi `id` không tồn tại (`findById(id) == null`) -> phải chuyển hướng `notFoundUrl`; chưa có test case xác nhận Admin huỷ bản ghi ĐÃ KÝ (`signedContract`) thành công kèm lý do hợp lệ -> phải chuyển hướng `/contract`.
    2. `handleChangeProgress`: Chưa có test case khi user không có quyền (`canUpdateContract(u) == false`) gửi POST `action=changeProgress` -> phải trả 403 `MSG-022`; chưa có test case khi `contractId` không tồn tại -> phải chuyển hướng `notFoundUrl`; chưa có test case khi chuyển tiến độ thất bại (`ERR_PROGRESS_FAILED`) -> phải chuyển hướng kèm `error=progress_failed`.
    3. `handleAddProduct` & `handleRemoveProduct`: Chưa có test case khi user không có quyền (`canUpdateContract(u) == false`) -> phải trả 403 `MSG-022`; chưa có test case khi `contractId` không tồn tại trong `addProduct` hoặc `contractId`/`contractProductId` bị null trong `removeProduct` -> phải chuyển hướng `notFoundUrl`; chưa có test case khi `removeProductFromContract` thất bại -> phải chuyển hướng kèm `error=remove_product_failed`.
    4. `handleCreateAmendment`: Chưa có test case khi user không có quyền gửi POST `action=createAmendment` -> phải trả 403 `MSG-022`; chưa có test case khi `createAmendment` trả về `ERR_VALUE_NEGATIVE` (giảm trừ vượt giá trị còn lại) -> phải chuyển hướng kèm `error=value_negative`.
    5. `handleCorrect`: Chưa có test case khi `contractId` không tồn tại -> phải chuyển hướng `notFoundUrl`; chưa có test case khi service trả về `ERR_UPDATE_FAILED` -> phải chuyển hướng kèm `error=update_failed`.
  - Gợi ý sửa: Bổ sung các test cases tương ứng vào `test/poscs/controller/ContractActionControllerTest.java` để bảo đảm độ bao phủ nhánh rẽ của Controller đạt 100%.

---

- [x] 🟡 `ContractActionController.java` › `handleDelete()` (dòng ~208–213)
  - Vi phạm: rules §4 ("Phân quyền chỉ qua PermissionService/AccessControl, không rải if (role == ...) trong controller"), BR-37.
  - Mô tả: Phương thức đang tự ghép logic điều kiện `if (!target.isDraft() && !getPermissionService().isAdmin(u))`. Trong khi đó, `PermissionService` đã có sẵn phương thức chuyên trách `canVoidContract(User user, boolean isDraft)` đóng gói chính xác quy tắc BR-37 (Bản nháp: Admin hoặc Sales quản lý vùng; Bản đã ký trở đi: chỉ Admin).
  - Gợi ý sửa:
    ```java
    Integer id = ContractControllerHelper.parseIntOrNull(req.getParameter("id"));
    Contract target = id != null ? getContractService().findById(id) : null;
    if (target == null) { resp.sendRedirect(ContractControllerHelper.notFoundUrl(req)); return; }
    if (!getPermissionService().canVoidContract(u, target.isDraft())) {
        resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        return;
    }
    ```

---

- [x] 🟢 `ContractActionController.java` › `showAmendmentForm()` & `handleCreateAmendment()` (dòng ~106, ~165)
  - Vi phạm: rules §4 (Kiến trúc phân tầng và nhất quán nghiệp vụ UC-46)
  - Mô tả: Hai phương thức trên đang gọi `getPermissionService().canCreateContract(u)`. Trong `PermissionService` đã định nghĩa riêng phương thức nghiệp vụ chuyên biệt `canCreateAmendment(User user)` cho use case UC-46. Dù hiện tại cả hai hàm ủy quyền về cùng logic, việc gọi đúng `canCreateAmendment(u)` giúp đảm bảo tính tường minh và độc lập của use case.
  - Gợi ý sửa: Thay `getPermissionService().canCreateContract(u)` thành `getPermissionService().canCreateAmendment(u)`.

---

- [x] 🟢 `ContractControllerHelper.java` › `bindEditFormAttributes()` (dòng ~149)
  - Vi phạm: rules §4 (Quy chuẩn an toàn hiển thị cờ giao diện)
  - Mô tả: Cờ `canVoid` đang được tính bằng `(ps.isAdmin(u) || c.isDraft()) && c.getAmendmentCount() == 0`. Nếu phương thức này được tái sử dụng trong ngữ cảnh user là Sales có cấp trên (subordinate) hoặc Kỹ thuật, biểu thức trên vẫn đánh giá `true` đối với bản ghi nháp.
  - Gợi ý sửa: Sử dụng `ps.canVoidContract(u, c.isDraft()) && c.getAmendmentCount() == 0` để đồng bộ hoàn toàn với ma trận phân quyền.

---

- [x] 🟢 `ContractActionController.java` › `handleChangeProgress()` (dòng ~194–195)
  - Vi phạm: Checklist B (Chuẩn hóa chuỗi đầu vào)
  - Mô tả: Tham số `toStatus` được đọc trực tiếp từ `req.getParameter("toStatus")` mà chưa trim khoảng trắng. Nếu client gửi kèm khoảng trắng thừa (ví dụ `" Đã ký "`), nhánh `ContractService.PROGRESS_SIGNED.equals(toStatus)` sẽ bị false và rơi xuống nhánh else, bỏ qua kiểm tra `canSign(u)` và `missing_term` ở controller (mặc dù service bên dưới vẫn có cơ chế chặn dự phòng).
  - Gợi ý sửa:
    ```java
    String toStatus = ContractControllerHelper.emptyToNull(req.getParameter("toStatus"));
    String note = req.getParameter("progressNote");
    ```

---

- [ ] 🟢 Kiến trúc định tuyến & Tích hợp: `ContractController.java` (Quan sát phối hợp)
  - Vi phạm: N/A (Ghi chú kiến trúc cho các task tiếp theo)
  - Mô tả: `ContractActionController` đã hoàn thành và sẵn sàng tại route `/contract/action`, với các phương thức `public` hỗ trợ Dependency Injection. Hiện tại form JSP vẫn gửi POST về `/contract`. Khi thực hiện các task tiếp theo (4.13 - 4.18), `ContractController` cần được refactor thành Facade để chuyển tiếp request về `ContractActionController` theo đúng mô hình của Task 2.4 (`CustomerController`).
  - Phản hồi Dev: Ghi nhận. Theo phạm vi Task 4.12 và nguyên tắc cô lập test, giữ nguyên `ContractController.java` hiện tại để không ảnh hưởng 92 unit tests cũ. Việc refactor `ContractController` thành Facade ủy quyền sẽ được thực hiện tại các task tích hợp tiếp theo (4.13 - 4.18).
