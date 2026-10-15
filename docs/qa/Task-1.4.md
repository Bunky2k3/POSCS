# QA Report - Task 1.4
**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 1011 pass / 0 fail / 0 error / skipped: test tích hợp bị bỏ qua mặc định (không đếm riêng); `PermissionServiceTest`: 23/23 pass. `BUILD SUCCESSFUL`.

Phạm vi: `src/java/poscs/service/PermissionService.java`, `test/poscs/service/PermissionServiceTest.java`. Đối chiếu SPEC §3.1–3.3 (RBAC), UC-30..35, 38, 39, 44, 46..54, NFR-10, MSG-022. Task này không có BR số hiệu riêng, quy tắc nằm ở SPEC §3.2/§3.3.

**Đã kiểm tra đúng:** ma trận Admin/Kỹ thuật/Sales cho Khách hàng, Sản phẩm, Hợp đồng, Phiếu khớp §3.3. Huỷ HĐ: Nháp = Admin hoặc quản lý vùng, Đã ký = chỉ Admin. UC-54 chỉ Admin. UC-49/50/51 đúng ghi chú SPEC. Không có vai trò CS. Mọi `checkCan*` đều trả `MSG-022` đúng nguyên văn. Hàm đều ≤ 40 dòng, không `TODO`, không đụng file ngoài phạm vi (`AccessControl` không bị sửa).

## Danh sách lỗi

- [x] 🔴 `PermissionService.java` › `getCustomerListScope()` / `getContractListScope()` (dòng ~558-576)
  - Vi phạm: SPEC §3.2 (phạm vi "Của tôi"), §3.3 / MSG-022, rules §3 (bảo mật), §5 (không nới điều kiện).
  - Mô tả: `if (!isActiveUser(user) || isAdmin(user) || isTechnical(user)) return ListScope.all();`. User `null`, bị xóa mềm (`is_deleted`), hoặc role lạ/CS đều nhận `ListScope.all()`, tức xem toàn bộ dữ liệu (fail-open). Chỉ Admin và Kỹ thuật được xem toàn bộ theo thiết kế. Nhánh `!isActiveUser` không được hưởng quyền đó. Ngoài ra `ListScope.of(ownerIds, …)` trả `ALL` khi `ownerIds` rỗng. Với `userId <= 0`, `getTeamUserIds` trả danh sách rỗng nên cũng thành `ALL`. Không có test nào phủ các nhánh này.
  - Gợi ý sửa: tách nhánh, không trả `ALL` cho user không hợp lệ.
    ```java
    if (!isActiveUser(user)) { return null; /* hoặc ném/ trả ServiceResult fail MSG-022 */ }
    if (isAdmin(user) || isTechnical(user)) { return ListScope.all(); }
    if (!isSales(user)) { /* role lạ: từ chối */ }
    ```
    Nếu muốn giữ chữ ký public, trả một scope "rỗng/không khớp" (không dùng `ListScope.of` với list rỗng) và có test cho null/deleted/role lạ/`userId<=0`.

- [x] 🔴 `PermissionService.java` › `isSubordinate()` (dòng ~89-100) và các hàm gọi nó (`isSalesRegionalManager`, `canManageCustomers`, `canManageContracts`…)
  - Vi phạm: SPEC §3.2 ("cấp trên đọc trực tiếp từ DB"), rules §3 (an toàn bảo mật).
  - Mô tả: nếu `employeeDAO.findById()` trả `null` (user không còn trong DB, hoặc `EmployeeDAO` nuốt `SQLException` và trả `null`/rỗng khi DB lỗi), code rơi về `user.getManagerId()` của session. Session cũ của Sales vừa được gán cấp trên (hoặc DB lỗi) vẫn có `managerId == null`, nên bị coi là Sales quản lý vùng và được quyền ghi. Quyền ghi bị cấp khi không xác minh được từ DB, trái yêu cầu "cấp trên đọc từ DB". Test `subordinateCheck_readsDirectlyFromDatabase` chỉ phủ trường hợp DB có bản ghi, không phủ `findById == null`.
  - Gợi ý sửa: không dùng giá trị session làm căn cứ cấp quyền ghi.
    ```java
    User dbUser = employeeDAO.findById(user.getUserId());
    if (dbUser == null || dbUser.isDeleted()) { return true; /* không xác minh được: coi như bị hạn chế */ }
    return dbUser.getManagerId() != null;
    ```
    Bổ sung test `findById → null` cho Sales (phải bị từ chối ghi, trả MSG-022) và user đã bị xóa mềm trong DB.

- [x] 🟡 `PermissionService.java` › `isCustomerInScope()` vs `isContractInScope()` (dòng ~578-617)
  - Vi phạm: SPEC §3.2 mơ hồ, rules §1 (mơ hồ thì hỏi, không đoán).
  - Mô tả: khi Sales chưa được giao tỉnh, khách hàng buyer trả `true` (cho sửa mọi khách), còn hợp đồng chỉ cho sửa theo `team.contains(owner)`. Hai hàm xử lý "chưa giao tỉnh" khác nhau mà SPEC không nói rõ. SPEC chỉ nói "Sales đã được giao tỉnh chỉ sửa được trong phạm vi Của tôi". Chưa có test cho nhánh `provinceIds.isEmpty()` của cả hai hàm.
  - **Quyết định của người dùng (2026-10-15):** thống nhất theo hợp đồng, tức khi chưa giao tỉnh thì chỉ được sửa dữ liệu do mình hoặc cấp dưới đứng tên (chặt hơn). Áp dụng cho cả `isCustomerInScope` (bỏ nhánh `provinceIds.isEmpty() → true` với buyer) và `isContractInScope`.
  - Gợi ý sửa: bỏ nhánh `if (provinceIds.isEmpty()) return true;` trong `isCustomerInScope`, thay bằng `return team.contains(customer.getAccountOwnerId());`, và thêm test cho nhánh chưa giao tỉnh của cả hai hàm.

- [x] 🟡 `PermissionServiceTest.java` › thiếu test cho các nhánh `can*` / scope (toàn file)
  - Vi phạm: rules §4 ("Mọi nhánh mới có test"), task "Xong khi" (ma trận 3 vai trò × thao tác).
  - Mô tả: các hàm sau chưa được test, hoặc chỉ test một vai trò:
    - Nhóm "xem": `canViewCustomers`, `canViewProducts`, `canViewContracts`, `canViewTickets`, `canViewNotifications` và các `canExport*` (gồm cả trường hợp `null`/role lạ bị từ chối với MSG-022). Nhóm này chỉ phủ gián tiếp.
    - Overload `canUpdateCustomer/canDeleteCustomer(user, customer, roles)`: hoàn toàn chưa test (Admin, quản lý vùng trong/ngoài phạm vi, Sales có cấp trên, Kỹ thuật).
    - `isCustomerInScope`/`isContractInScope`: thiếu Admin (luôn true), Sales có cấp trên, Kỹ thuật, nhánh `provinceIds` rỗng, `customer == null`. `isSupplierOnly`/`provinceIdOf` với `null`.
    - `canReviewChangeRequest`: thiếu ca Sales khác (không phải cấp trên của người gửi) bị từ chối, người gửi không có cấp trên (`findById → null` hoặc `managerId == null`).
    - `canSubmitChangeRequest` cho Kỹ thuật có cấp trên; `canConfirmHandover` với `user == null`.
    - `getTeamUserIds`/`getAssignedProvinceIds`/`getAssignedProvinces` với `userId <= 0`, DAO trả `null`.
    - `getCustomerListScope`/`getContractListScope` cho user null/bị xóa/role lạ (liên quan lỗi 🔴 #1), và cho Sales có cấp trên.
    - `canUpdateProduct/canDeleteProduct` của Sales (chỉ `canCreateProduct` được kiểm tra).
  - Gợi ý sửa: thêm test theo từng nhánh trên. Với mỗi ca từ chối, assert đủ `MSG_022` qua `assertMsg022(...)`.

- [x] 🟡 `PermissionService.java` › `canConfirmHandover()` (dòng ~351-356)
  - Vi phạm: SPEC §3.3 ghi chú UC-49.
  - Mô tả: so sánh `user.getDepartmentId() == targetDepartmentId` không kiểm `targetDepartmentId > 0`. Nếu phòng ban đích là 0/không hợp lệ và user chưa có phòng ban (mặc định 0), kết quả `true` (cho phép nhầm). Không có test cho biên này.
  - Gợi ý sửa: `if (targetDepartmentId <= 0) return isAdmin(user);` (hoặc từ chối hẳn) kèm test.

- [x] 🟡 `PermissionService.java` › `isActiveUser()` / `isAdmin()` / `isSales()` (dòng ~56-70)
  - Vi phạm: SPEC §3.2, rules §3 (bảo mật).
  - Mô tả: vai trò và `isDeleted` lấy từ đối tượng `User` trong session, chỉ cấp trên mới đọc lại từ DB. Admin bị đổi vai trò hoặc khóa/xóa trong DB vẫn giữ quyền đến khi session hết hạn. Yêu cầu task chỉ nói "cấp trên đọc từ DB", nên đây là ghi nhận rủi ro, không phải vi phạm cứng. Cần người dùng xác nhận có muốn xác thực lại vai trò/trạng thái từ DB hay không.
  - **Quyết định của người dùng (2026-10-15):** CÓ đọc lại `role` và `is_deleted` từ DB cho thao tác ghi (mục này nâng thành bắt buộc sửa).
  - Gợi ý sửa: đọc `role`/`is_deleted` từ `employeeDAO.findById` cho các thao tác ghi, kèm test (Admin bị đổi vai trò hoặc xóa trong DB phải bị từ chối với MSG-022).

- [ ] 🟢 `PermissionService.java` › toàn file (635 dòng)
  - Vi phạm: rules §4 (file > 250 dòng nên xem xét tách).
  - Mô tả: gom cả ma trận quyền, quan hệ tổ chức và phạm vi dữ liệu trong một lớp. Có thể tách phần scope ("Của tôi") ra lớp phụ trợ (ví dụ `DataScopeService`) nếu muốn. Không bắt buộc.
  - Gợi ý sửa: tách mục 8 (ListScope & Row-level Scope) ra lớp riêng, giữ `PermissionService` làm facade nếu cần giữ chữ ký.
  > *Dev ghi chú (2026-10-15): Giữ nguyên trong một file `PermissionService.java` để đảm bảo đúng ranh giới cấm/ràng buộc của prompt ("File dự kiến tạo/sửa: {src/java/poscs/.../PermissionService.java}", không tạo file ngoài danh sách khi chưa được yêu cầu). Tất cả các phương thức đều ngắn gọn ≤ 40 dòng.*

- [ ] 🟢 `PermissionService.java` › `isSubordinate()` / `isSalesRegionalManager()` / `canManage*`
  - Vi phạm: không vi phạm BR, chỉ là hiệu năng.
  - Mô tả: mỗi lần gọi `canManage*` hoặc `check*` đều thực hiện một truy vấn `findById` (N+1 khi dùng trong vòng lặp hiển thị danh sách). Chấp nhận được ở bước này vì SPEC yêu cầu đọc DB, nhưng nên lưu ý khi controller/JSP gọi nhiều lần trên một request.
  - Gợi ý sửa: cache trong phạm vi một request (ở lớp gọi), không cache giữa các request.
  > *Dev ghi chú (2026-10-15): Giữ nguyên truy vấn trực tiếp từ DAO theo yêu cầu của SPEC. Việc cache trong phạm vi request (Request-scoped caching) sẽ do controller xử lý khi gọi Service.*

- [x] 🟢 `PermissionServiceTest.java` › `reviewChangeRequest_adminOrDirectManagerAllowed()` (dòng ~398)
  - Vi phạm: style.
  - Mô tả: `when(employeeDAO.findById(staffUserId)).thenReturn(salesStaff);` thừa vì `setUp()` đã stub sẵn y hệt. Comment bên cạnh dễ gây hiểu nhầm.
  - Gợi ý sửa: xóa dòng thừa.

## Tóm tắt
🔴 2 | 🟡 4 | 🟢 3. Cần Dev xử lý các mục 🔴 (và hỏi người dùng về mục 🟡 chưa tô rõ trong SPEC) rồi chạy lại `ant test` trước khi QA duyệt lại.
