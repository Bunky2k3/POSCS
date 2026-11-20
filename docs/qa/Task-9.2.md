# QA Report - Task 9.2

**Kết luận:** ĐẠT
**Kết quả `ant compile-jsps test`:** `compile-jsps` thành công; **2.184 pass / 0 fail / 0 error / 3 skipped** (tổng 2.187 test ở 92 lớp, `BUILD SUCCESSFUL`; tăng +116 test mới so với Task 9.1; 3 skipped là test tích hợp mặc định bỏ qua).

**Phạm vi:** Task 9.2 — Phòng và vai **Tổng hợp**: gộp hai phòng Kế toán và Dự án thành phòng Tổng hợp; chuyển chặng bàn giao của phòng Dự án và Kỹ thuật sang Tổng hợp (đóng chặng trùng theo luật 19/11/2026); thêm vai Tổng hợp và tài khoản test `tonghop1`; phân quyền vai Tổng hợp (xem khách hàng, hợp đồng, sản phẩm; không vào phiếu hỗ trợ; xác nhận bàn giao cho phòng mình; ghi nhận kỳ thanh toán đã thu nhưng không lập/xoá kỳ). Đối chiếu với `task_tracker.md`, `docs/SPEC.md` và `rules.md`; thay đổi thực tế lấy từ `git status` và `git diff` trên nhánh `feat/task-9.2-phong-va-vai-tong-hop`.

## Danh sách lỗi

Không có lỗi 🔴 Critical hoặc 🟡 Major (0 🔴 Critical, 0 🟡 Major, 1 🟢 Minor).

- [x] 🟢 **Minor — `ContractPaymentController.handleMarkPaid` kiểm định dạng ngày trước khi kiểm quyền** (`src/java/poscs/controller/ContractPaymentController.java` dòng 173–179): nếu người dùng **không có quyền** (ví dụ Sales có cấp trên hoặc Kỹ thuật) gửi `POST /contract?action=markPaid` kèm `paidDate` sai định dạng (ví dụ `18/11/2026`), controller trả redirect `/contract?action=view&id=<id>&error=payment_failed` thay vì `403 MSG-022`. Hành vi kiểm ngày trước này vốn có từ Task 4.15 (Task 9.2 chỉ đổi trang quay về qua `markPaidReturnPage`) và không làm lọt thao tác ghi, nên không chặn việc đạt QA; có thể giữ nguyên hoặc chuyển kiểm quyền lên trước khi parse ngày nếu muốn nhất quán trả 403.
  - Đã sửa (bước Fix): `handleMarkPaid` gọi `PermissionService.canMarkPaymentPaid(actor)` ngay sau khi kiểm mã hợp đồng/mã kỳ, trước khi đọc `paidDate`; không có quyền thì `403 MSG-022`. Thiếu hoặc sai mã vẫn về `error=notfound` như cũ. Service vẫn kiểm lại. Test thêm ở `ContractPaymentControllerTest` (mục 8): không quyền + ngày sai → 403 không redirect; không quyền + ngày hợp lệ → 403, không gọi service; chưa đăng nhập → 403; thiếu mã kỳ → vẫn notfound; `PermissionService` thật với vai Kỹ thuật + ngày sai → 403. `setUp` chỉ thêm stub `canMarkPaymentPaid` (không sửa assertion cũ). `ant test`: 2.192 test, 0 fail, 3 skipped.

## Chi tiết kiểm tra

### A. Đối chiếu nghiệp vụ, SPEC và các mục của Task 9.2

| Mục | Kết quả |
|---|---|
| `db/migrations/V40__phong_va_vai_tong_hop__ndat2003.sql` | Đạt. Đúng số thứ tự kế tiếp sau `V39`; có ghi `schema_migrations` và comment đầu file theo kiểu `V38` nêu lý do theo mục 13. Đổi tên `Kế toán` → `Tổng hợp` (có fallback `INSERT` nếu CSDL chưa có `Kế toán`); chuyển nhân viên `Dự án` và **mọi** chặng bàn giao (cả đã xong lẫn đang mở) của `Dự án` và `Kỹ thuật` sang `Tổng hợp`; dùng `ROW_NUMBER() OVER (PARTITION BY contract_id ORDER BY handed_at, handover_id)` giữ chặng mở sớm nhất và đóng các chặng trùng còn lại đúng `done_at = CURRENT_TIMESTAMP`, `done_by = admin` (fallback Admin `user_id` nhỏ nhất), `done_note = N'Đóng khi gộp phòng Kế toán, Dự án, Kỹ thuật thành Tổng hợp (19/11/2026)'`; xoá phòng `Dự án` (giữ `Kỹ thuật` cho Task 9.3); thêm vai `Tổng hợp` vào `roles` kèm `NOT EXISTS`. |
| `db/migrations/V41__seed_tonghop_test_account__ndat2003.sql` | Đạt. Tạo tài khoản test `tonghop1` (vai `Tổng hợp`, phòng `Tổng hợp`, không phải Admin), chặn trùng theo `username` bằng `WHERE NOT EXISTS`. Đã kiểm chứng hash BCrypt (`$2a$10$Zy5dXkGNLYjaOkebo0qWgeR/CYPfCpu1SUkcHX9LPe6LMsIormrA2`) bằng `BCrypt.checkpw("Poscs@123", ...)` từ `lib/jbcrypt-0.4.jar` → `true`. Đủ trường hồ sơ (`gender`, `date_of_birth`, `citizen_id`, `phone`) không bị ép qua `/updateProfile`; `citizen_id` (`001192012358`) và `phone` (`0912340014`) không trùng với bất kỳ dòng nào trong `db/schema.sql`. |
| `db/schema.sql` | Đạt. Mirror đồng bộ `roles` (thêm `Tổng hợp`), `departments` (chỉ còn `Tổng hợp` thay `Kế toán` và `Dự án`), tài khoản `tonghop1`, và cập nhật comment V32 ở `departments` và `contract_handovers`. Không còn phòng `Kế toán`/`Dự án` trong seed. |
| `src/java/poscs/service/PermissionService.java` | Đạt. Thêm `ROLE_GENERAL = "Tổng hợp"` và `isGeneral(User)` (có kiểm `getEffectiveUser` và `!isDeleted`). `canViewCustomers`, `canViewProducts`, `canViewContracts` cho thêm `Tổng hợp` (kéo theo quyền xuất Excel KH/HĐ); `canViewTickets` (và toàn bộ quyền phiếu hỗ trợ), `canManageEmployees`, `canViewSystemLogs` đều từ chối (`MSG-022`). `getCustomerListScope`, `getContractListScope` trả `ListScope.all()` cho `Tổng hợp`. `canConfirmHandover` kiểm theo phòng ban, hoạt động đúng với vai `Tổng hợp` thuộc phòng `Tổng hợp`. Thêm `canMarkPaymentPaid` / `checkCanMarkPaymentPaid` (`canManageContractPayments \|\| isGeneral`), giữ `canManageContractPayments` không mở cho `Tổng hợp`. |
| `src/java/poscs/common/AccessControl.java` | Đạt. `FULL_ACCESS_ROLES` cố ý không có `Tổng hợp` (chỉ View trên CUSTOMER, CONTRACT, PRODUCT; không có quyền ghi); `defaultView` trả `VIEW_ALL` cho `Tổng hợp`; `canCompleteHandover` kiểm theo phòng ban; cập nhật comment bỏ `Kế toán`/`Dự án`. |
| `ContractPaymentService.java` & `ContractPaymentController.java` | Đạt. `markPaymentPaid` chuyển sang kiểm `canMarkPaymentPaid(actor)`; `addPayment` và `deletePayment` giữ `canManageContractPayments(actor)` (Tổng hợp nhận `403 MSG-022`). `ContractController.handleMarkPaid` uỷ quyền thẳng sang `ContractPaymentController` không qua `requireFullAccess`. `markPaidReturnPage` đưa người không có `canManageContractPayments` (vai Tổng hợp) quay về `/contract?action=view&id=<id>`, tránh ăn 403 ở trang Sửa. |
| `web/jsp/sale/viewcontractdetail.jsp` | Đạt. Nút "Ghi nhận đã thu" chỉ hiện với vai `Tổng hợp` trên kỳ chưa thu; mở modal Bootstrap trong trang (`#markPaidModal`, không dùng `prompt`/`confirm`), có `csrfToken`, `action=markPaid`, `contractId`, `paymentId`, ô `paidDate` mặc định hôm nay, có xử lý hiển thị toast khi `param.error == 'payment_failed'`. |
| `web/jsp/sale/updatecontract.jsp` & `web/jsp/sale/listcontract.jsp` | Đạt. Bỏ điều kiện tên phòng `Kế toán`/`Dự án`/`Kỹ thuật`, chỉ còn `Tổng hợp`; checkbox "Bàn giao cho phòng" ở trang Sửa tích sẵn `Tổng hợp`. |
| `web/jsp/common/sidebar.jsp` | Đạt. Ẩn mục "Phiếu hỗ trợ" khi vai là `Tổng hợp` (chốt chặn thật ở `PermissionService.canViewTickets`). |
| `src/java/poscs/service/DashboardService.java` | Đạt. `resolveScope` mặc định `"all"` (Toàn chi nhánh) cho Admin và Tổng hợp (`isGeneralUser`), vẫn tôn trọng tham số `scope=mine` nếu người dùng chủ động chọn. |
| Form nhân viên (`EmployeeService.java`, `addEmployee.jsp`, `updateEmployee.jsp`) | Đạt. Đã rà soát: `EmployeeService.assignableRoles` đọc động từ `employeeDAO.findAllRoles()` và chỉ loại vai `Admin`, không có điều kiện nào giới hạn chỉ cho Sales, nên vai `Tổng hợp` tự động xuất hiện trong dropdown Vai trò ở cả form thêm và sửa nhân viên. |
| Comment & `PERMISSIONS.md` | Đạt. Cập nhật comment ở `ContractController.java` và `ContractDAO.java`; `PERMISSIONS.md` bổ sung đầy đủ vai `Tổng hợp`, cột mới trong ma trận quyền và chú thích § cho hai thao tác ghi ngoại lệ. Rà `Kế toán`/`Dự án` trên `src/java` và `web/jsp`: không còn logic chạy nào dùng hai tên phòng cũ (chỉ còn comment lịch sử và `web/jsp/guide/contract.jsp` thuộc Task 10.6). |

### B & C. Bảo mật, kiến trúc, tiêu chuẩn code

- **CSRF**: Form modal `#markPaidForm` trong `viewcontractdetail.jsp` gửi đầy đủ `<input type="hidden" name="csrfToken" value="${csrfToken}">`.
- **RBAC & fail-closed**: Mọi chốt chặn thật nằm ở `PermissionService` / `AccessControl` (kiểm tra trạng thái tài khoản đang hoạt động từ DB qua `getEffectiveUser`), lớp JSP chỉ ẩn/hiện trình bày.
- **Tách tầng & độ dài hàm**: Các hàm bổ sung (`isGeneral`, `canMarkPaymentPaid`, `markPaidReturnPage`, `isGeneralUser`) đều ngắn gọn (< 15 dòng), đúng tầng.
- **SQL & UTF-8**: Không dùng nối chuỗi SQL từ input; toàn bộ file tiếng Việt mã hoá UTF-8 chuẩn; `ant compile-jsps` sạch lỗi.

### D. Phạm vi & Không đụng

- Đúng nhánh `feat/task-9.2-phong-va-vai-tong-hop`; `task_tracker.md` giữ `[ ]` ở Task 9.2 (chưa đánh `[x]`, chưa commit).
- Không đụng vai/phòng `Kỹ thuật` (Task 9.3), không đổi quyền của `Admin` và `Sales`, không đụng `web/jsp/guide/` (Task 10.6).
- `test/poscs/integration/ContractStatusIntegrationTest.java` sửa đúng các chỗ mang tên phòng `Kế toán`/`Dự án`/`Kỹ thuật` theo thỏa thuận với người dùng ngày 20/11/2026 đã ghi nhận trong `task_tracker.md`.

### E. Kiểm thử

- Đã bổ sung unit test bao phủ đủ các nhánh mới:
  - `PermissionServiceTest`: ma trận đầy đủ vai `Tổng hợp` (xem KH/HĐ/SP, chặn phiếu hỗ trợ/nhân viên/nhật ký hệ thống trả `MSG-022`, `canMarkPaymentPaid` so với `canManageContractPayments`, tài khoản bị khoá mất hết quyền, `ListScope.all()`, row-level scope, `canConfirmHandover` đúng phòng/khác phòng).
  - `ContractPaymentServiceTest` & `ContractPaymentControllerTest`: vai `Tổng hợp` chỉ `markPaid` được và quay về trang chi tiết (kể cả khi lỗi nghiệp vụ/sai định dạng ngày); `addPayment` và `removePayment` bị chặn `403 MSG-022`.
  - `ContractHandoverServiceTest`, `ContractHandoverDAOTest`, `ContractControllerTest`: phòng `Tổng hợp` nhận bàn giao, vai `Tổng hợp` xác nhận đúng phòng, chặn khác phòng/giả mạo phòng, chặn khởi tạo bàn giao (`403`), chặn mở trang Sửa (`403`).
  - `TechnicalSupportTicketControllerTest`: vai `Tổng hợp` nhận `403 MSG-022` ở toàn bộ GET/POST phiếu hỗ trợ (`list`, `view`, `new`, `edit`, `exportExcel`, `exportPdf`, `create`, `update`, `delete`).
  - `DashboardServiceTest`: vai `Tổng hợp` mặc định `"all"`, vẫn chọn `"mine"` được; Sales giữ mặc định `"mine"`.
  - `EmployeeServiceTest`: `assignableRoles` có `Tổng hợp`, Admin tạo được nhân viên vai `Tổng hợp`, Sales bị chặn `MSG-022`.
- Không có test cũ nào bị xoá hoặc nới lỏng assertion để che lỗi.

## Ghi chú ngoài phạm vi & Vận hành

- **Chạy migration trước khi kéo code về checkout chính:** Theo dòng Lưu ý của Task 9.2, agent không chạy migration lên CSDL. Người dùng cần tự chạy hai file `db/migrations/V40__phong_va_vai_tong_hop__ndat2003.sql` và `db/migrations/V41__seed_tonghop_test_account__ndat2003.sql` lên `poscs_db` (và ghi nhận số chặng bàn giao đã chuyển / đã đóng) trước khi kéo nhánh về checkout chính.
- **File untracked ngoài phạm vi:** `docs/PROMPTS.md` và `task_tracker_deploy.md` đang untracked và không thuộc Task 9.2 — bước Commit không `git add` hai file này.
- **Kiểm tra integration test trên DB `_it`:** 3 lớp integration test (trong đó có `ContractStatusIntegrationTest` vừa cập nhật) mặc định bị skipped khi chạy `ant test`; người dùng có thể tự chạy khi có DB `_it` để xác minh thêm trên MySQL thật.
