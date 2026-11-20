# QA Report - Task 9.3

**Kết luận:** APPROVED (ĐẠT)
**Kết quả `ant compile-jsps test`:** `compile-jsps` thành công (đã dịch kiểm chứng riêng 6/6 file `.jsp` đã sửa bằng Jasper của Tomcat 11: `0 errors`); **2.194 pass / 0 fail / 0 error / 3 skipped** (tổng 2.197 test ở 92 lớp, `BUILD SUCCESSFUL`; 3 skipped là test tích hợp mặc định bỏ qua).

**Phạm vi:** Task 9.3 — Bỏ vai và phòng **Kỹ thuật**; chuyển quyền quản lý sản phẩm (UC-18..20) và vai trò người xử lý phiếu hỗ trợ (BR-39, UC-16, UC-21, UC-22) sang **Sales**; chuẩn hoá nhãn giao diện sang "Người xử lý" và cập nhật comment/`PERMISSIONS.md`. Đối chiếu với `task_tracker.md`, `docs/SPEC.md` và `rules.md`; thay đổi thực tế lấy từ `git status` và `git diff` trên nhánh `feat/task-9.3-bo-vai-ky-thuat`.

## Danh sách lỗi

Không có lỗi 🔴 Critical hoặc 🟡 Major (**0 🔴 Critical, 0 🟡 Major, 2 🟢 Minor**).

- [x] 🟢 `TechnicalSupportTicketController.java` › `showEditForm()` (dòng ~280–291) & `handleUpdate()` (dòng ~334–349)
  - Vi phạm: Hợp đồng Javadoc của `AccessControl.requireFullAccess` (*"Gọi ở đầu mỗi handleCreate/handleUpdate/handleDelete, TRƯỚC khi đọc tham số hay đụng tới DB"*) và tính nhất quán `MSG-022` theo `SPEC.md` §3.3 (*"mọi màn hình phiếu trả MSG-022"*).
  - Mô tả: Trước Task 9.3, hai hàm này phải gọi `ticketDAO.findById(id)` trước khi kiểm quyền vì cần đối tượng `ticket` cho `AccessControl.canUpdateAssignedTicket(request, ticket)`. Nay ngoại lệ đó đã bỏ và thay bằng `AccessControl.requireFullAccess(request, response, AccessControl.Resource.TICKET)` (không còn cần `ticket`), nhưng lệnh kiểm quyền vẫn đứng sau bước tra DB. Do đó, nếu vai không có quyền (như `Tổng hợp` hoặc tài khoản còn sót vai `Kỹ thuật` cũ) gửi `GET /ticket?action=edit&id=999` (id không tồn tại hoặc thiếu `id`) hay `POST /ticket?action=update&ticketId=999`, server trả `302 /ticket?error=notfound` thay vì `403 MSG-022` (khác với id tồn tại trả `403`, tạo chênh lệch phản hồi cho biết id có tồn tại hay không). Không làm lọt dữ liệu phiếu hay thao tác ghi nên không chặn việc đạt QA (cùng tính chất mục Minor của Task 9.2).
  - Gợi ý sửa: Chuyển `if (!AccessControl.requireFullAccess(request, response, AccessControl.Resource.TICKET)) { return; }` lên đầu `showEditForm()` và `handleUpdate()` (trước `parseIntOrNull` và `ticketDAO.findById`), giống `showCreateForm`, `handleCreate`, `handleDelete` và `ProductController.showEditForm`/`handleUpdate`.

- [x] 🟢 `TicketExportService.java` › `buildPdfLines()` (dòng ~149) & `web/jsp/customersupport/viewdetailTicket.jsp` (dòng ~328)
  - Vi phạm: Quy chuẩn trình bày / style nhỏ.
  - Mô tả:
    1. `TicketExportService.java` dòng 149 thiếu một dấu cách sau dấu phẩy: `addPair(lines, "Người xử lý",ticket.getAssignedTechnician() != null ? ...`.
    2. `web/jsp/customersupport/viewdetailTicket.jsp` dòng 328 còn HTML comment `<!-- ===== Khối 2: kỹ thuật viên xử lý đánh giá ===== -->` (xuất ra mã nguồn HTML gửi về trình duyệt, khác JSP comment `<%-- ... --%>`).
  - Gợi ý sửa: Thêm dấu cách sau dấu phẩy ở `TicketExportService.java:149` và đổi HTML comment ở `viewdetailTicket.jsp:328` thành `<!-- ===== Khối 2: người xử lý đánh giá ===== -->`.

## Chi tiết kiểm tra

### A. Đối chiếu nghiệp vụ, SPEC và các mục của Task 9.3

| Mục | Kết quả |
|---|---|
| `db/migrations/V42__merge_kythuat_role_into_sales__ndat2003.sql` | Đạt. Đúng số thứ tự kế tiếp sau `V41`; ghi `schema_migrations` khớp tên file; comment đầu file rõ lý do theo mục 13 và nêu rõ thứ tự chuyển nhân sự trước khi xoá dòng danh mục. Chuyển user vai `Kỹ thuật` → `Sales` (dùng `IN` an toàn khi bảng `roles` không có UNIQUE trên `role_name`), chuyển phòng `Kỹ thuật` → `Kinh doanh` (cột `departments.department_name` có UNIQUE), rồi xoá `Kỹ thuật` ở cả `roles` và `departments`. Giữ nguyên `assigned_technician_id` trên phiếu (người xử lý giờ mang vai `Sales`) và giữ nguyên `manager_id` (Sales có cấp trên vẫn có toàn quyền sản phẩm và phiếu theo SPEC §3.3). |
| `db/schema.sql` | Đạt. Mirror đồng bộ `roles` (`Admin`, `Sales`, `Tổng hợp`) và `departments` (`Ban giám đốc`, `Kinh doanh`, `Tổng hợp`), xoá `Kỹ thuật`; cập nhật 4 tài khoản demo `kythuat2`–`kythuat5` (V12 không có `kythuat1`) sang vai `Sales`, phòng `Kinh doanh`, giữ nguyên username; không có INSERT nào hardcode `role_id` / `department_id` bị dịch chuyển. |
| `src/java/poscs/service/PermissionService.java` | Đạt. Bỏ `ROLE_TECHNICAL` và `isTechnical`. Các hàm xem (`canViewCustomers`, `canViewProducts`, `canViewContracts`, `canViewTickets`) bỏ vế `isTechnical`. `canManageProducts` thành `isAdmin(user) \|\| isSales(user)` (áp cho cả Sales có cấp trên đúng SPEC §3.3). `canUpdateTicket` bỏ nhánh kỹ thuật viên (giữ chữ ký 2 tham số cho tương thích ngược). `getCustomerListScope` và `getContractListScope` bỏ `isTechnical`, giữ `isAdmin(user) \|\| isGeneral(user)`. Mọi vi phạm trả đúng `MSG-022` = `"Bạn không có quyền truy cập nội dung này."`. |
| `src/java/poscs/common/AccessControl.java` | Đạt. `FULL_ACCESS_ROLES` đổi `Resource.PRODUCT` sang `Set.of("Admin", "Sales")`; `HIERARCHY_RESTRICTED` giữ nguyên chỉ `{CUSTOMER, CONTRACT}` nên Sales có cấp trên vẫn quản lý sản phẩm và phiếu; bỏ `canUpdateAssignedTicket` và import thừa `TechnicalRequest`; giữ nguyên vai `Tổng hợp`. |
| `src/java/poscs/service/TicketService.java` | Đạt. Đổi `TECHNICIAN_ROLE` thành `HANDLER_ROLE = PermissionService.ROLE_SALES` (theo đúng yêu cầu Task 9.3); `resolveAssigneeFilter` luôn trả `ASSIGNEE_ALL` khi vắng tham số (SPEC §5.5); `findActiveTechnicians` và `isAssignedTechnicianValid` dùng `HANDLER_ROLE` (`"Sales"`), vẫn giữ `keepUserId`/`keepTechnicianId` khi sửa phiếu cũ. |
| `src/java/poscs/controller/TechnicalSupportTicketController.java` | Đạt (1 🟢 Minor). Đổi `TECHNICIAN_ROLE` thành `HANDLER_ROLE = "Sales"` cho dropdown người xử lý; bỏ nhánh `canUpdateAssignedTicket` ở `showEditForm` và `handleUpdate`, thay bằng `AccessControl.requireFullAccess(..., TICKET)`. |
| `TicketListController.java` & `web/jsp/customersupport/listTicket.jsp` | Đạt. Bỏ attribute `isTechnician` trong `TicketListController`; `listTicket.jsp` luôn hiển thị `<option value="mine">Phiếu của tôi</option>` bên cạnh `"Tất cả người xử lý"` và danh sách người xử lý (mở lần đầu mặc định `"all"`); cột bảng là `"Người xử lý"`. |
| Nhãn UI & câu chữ (`addnewTicket.jsp`, `listTicket.jsp`, `updateTicket.jsp`, `viewdetailTicket.jsp`, `TicketExportService.java`, `TicketValidator.java`, `NotificationService.java`, `NotificationScheduler.java`, `TicketDetailController.java`, `TicketListController.java`, `TechnicalSupportTicketController.java`) | Đạt (1 🟢 Minor). Toàn bộ chữ người dùng nhìn thấy (nhãn ô, placeholder, câu lỗi nội tuyến `TicketValidator.MSG_TECHNICIAN_REQUIRED = "Vui lòng chọn người xử lý."`, tiêu đề khối `Người xử lý đánh giá`, nhãn PDF `Người xử lý`, header Excel đã là `Người xử lý`) đều đồng bộ; không còn chữ "Kỹ thuật viên"/"KTV" hiển thị ra người dùng. |
| Comment lịch sử & `PERMISSIONS.md` | Đạt. Đã cập nhật đủ 8 file comment được liệt kê trong Task 9.3 (`EmployeeDAO`, `ContractHistoryDAO`, `SqlFilters`, `ContractController`, `TicketExportController`, `listcustomer.jsp`, `viewcontractdetail.jsp`, và các Javadoc phiếu). `PERMISSIONS.md` đã bỏ vai `Kỹ thuật`, cập nhật cột `Sales` thành `Full` trên `Product` (cả manager lẫn staff), giải thích rõ V42 và việc bỏ ngoại lệ `canUpdateAssignedTicket`. |

### B & C. Bảo mật, kiến trúc, tiêu chuẩn code

- **RBAC & fail-closed:** Tài khoản còn sót tên vai `"Kỹ thuật"` cũ (nếu chưa chạy V42) không còn quyền xem/ghi ở `PermissionService` (danh sách thu về `ownerIds = [-1]`, mọi quyền trả `MSG-022`) và mất quyền ghi `PRODUCT`/`TICKET` ở `AccessControl`.
- **Phân tầng & quy chuẩn (§3–§4):** Không chứa logic nghiệp vụ trong JSP; không có `// TODO` hay placeholder; các hàm chỉnh sửa đều ngắn gọn (≤ 40 dòng); giữ nguyên tương thích ngược cho `canUpdateTicket(User, Integer)` và `findActiveTechnicians(Integer)`.
- **UTF-8 & JSP:** Đã kiểm chứng 41/41 file thay đổi/thêm mới hợp lệ UTF-8 (`0` lỗi encoding); 6/6 JSP đã sửa dịch sạch trên Jasper của Tomcat 11 (`0 errors`).

### D. Phạm vi, Không đụng & Tiêu chí "Xong khi"

- **Nhánh & commit:** Đang ở đúng nhánh `feat/task-9.3-bo-vai-ky-thuat` (không phải `main`), chưa có commit của bước Code (`git diff main HEAD` rỗng).
- **Phạm vi file:** 24 file trong "File sửa" khớp 1-1 với danh sách của Task 9.3; 16 file test đều chỉ sửa các test đỏ (lỗi biên dịch do bỏ `ROLE_TECHNICAL` / `canUpdateAssignedTicket` hoặc assertion cũ của vai Kỹ thuật) và bổ sung test mới theo yêu cầu.
- **Không đụng:** Giữ nguyên vai `Tổng hợp` (Task 9.2), không đụng `web/jsp/guide/` (Task 10.6), không đụng `docs/SPEC.md`, giữ nguyên các test xanh như `TicketExportControllerTest`.
- **Tiêu chí "Xong khi":**
  1. Sales (cả quản lý vùng lẫn có cấp trên) thêm/sửa/xoá sản phẩm được — Đạt.
  2. Tạo phiếu chọn người xử lý trong danh sách Sales đang hoạt động — Đạt.
  3. Ô Vai trò của form nhân viên không còn Kỹ thuật (`EmployeeService.findAllRoles()` đọc động từ bảng `roles`, V42 + `schema.sql` đã xoá `Kỹ thuật`) — Đạt.
  4. `grep -rnE "ROLE_TECHNICAL|isTechnical|isTechnician|canUpdateAssignedTicket" src/java web/jsp` — **0 kết quả** — Đạt.

### E. Kiểm thử & Danh sách test đã đổi nghĩa

- Không có test nào bị xoá để che lỗi.
- **8 lớp test chỉ thay hằng `PermissionService.ROLE_TECHNICAL` → `"Kỹ thuật"` để hết lỗi biên dịch (không đổi assertion):** `ContractActionControllerTest`, `ContractExportControllerTest`, `ContractPaymentControllerTest`, `CustomerActionControllerTest`, `ContractDocumentServiceTest`, `ContractHandoverServiceTest`, `ContractPaymentServiceTest`, `ContractServiceTest`.
- **Các test đổi nghĩa theo nghiệp vụ mới (vai Kỹ thuật → Sales / Tổng hợp) và test mới thêm:**
  - `AccessControlHierarchyTest`: đổi `kyThuatCoCapTren_vanGiuToanQuyenSanPham` → `salesCoCapTren_vanGiuToanQuyenSanPham`; đổi `kyThuatCoCapTren_vanSuaDuocPhieuDuocGiaoChoMinh` → `vaiKyThuatCu_khongConQuyenGhiSanPhamVaPhieu`.
  - `ProductControllerTest`: `setUp` đổi vai mặc định `"Kỹ thuật"` → `"Sales"` (bao phủ toàn bộ luồng Sales thêm/sửa/xoá sản phẩm); `create_withoutFullAccess_*` và `loginWithoutProductAccess` đổi `"Sales"` → `"Tổng hợp"`; **thêm mới** `create_bySalesWithManager_insertsAndRedirectsToDetail`.
  - `PermissionServiceTest`: đổi `manageProduct_adminAndTechnicalAllowed_salesDenied` → `manageProduct_adminAndAllSalesAllowed_generalDenied`; **thêm mới** `vaiKyThuatCu_khongConQuyenNao`; đổi `ticketUpdate_technicalOnlyAllowedForAssignedTicket` → `ticketUpdate_adminAndSalesAllowedOnAnyTicket_othersDenied`; đổi `listScope_adminAndTechnicalSeeAll` → `listScope_adminSeesAll`; bỏ `technicalUser` khỏi `viewAndExport_allowedForStandardRoles_deniedForInvalidUser`.
  - `TechnicalSupportTicketControllerTest`: đổi các test sửa/đóng/ghi nguyên nhân/phương hướng/mở form sửa của người xử lý từ `"Kỹ thuật"` sang `"Sales"`, và đổi test chống sửa lén thành `update_legacyTechnicianRole_cannotUpdateEvenAssignedTicket` (403 `MSG-022`); đổi test mặc định danh sách/xuất Excel của kỹ thuật viên sang Sales chọn `assignee=mine` (không còn `isTechnician`); đổi test dropdown sang kiểm `findActiveByRole("Sales", ...)`.
  - `TicketActionControllerTest`: `setUp` thêm Sales `51`/`52` làm người xử lý mặc định (giữ `50` vai Kỹ thuật cho test bị chặn); đổi test `showEditForm`/`handleUpdate` của người xử lý sang Sales toàn quyền (`canManage=true`, `lockGeneral=false`); **thêm mới** `showEditForm_legacyTechnicianAssigned_returns403` và `handleUpdate_reassignToNonSales_redirectsInvalid`.
  - `TicketDetailControllerTest`: đổi 2 test vai Kỹ thuật sang `detail_assignedSalesHandler_canManageEditAndDelete_UC17` và `detail_generalRole_returns403Msg022_UC17`.
  - `TicketListControllerTest`: đổi 3 test vai Kỹ thuật sang `Sales`/`Tổng hợp` (403 `MSG-022`), xác nhận `never().setAttribute("isTechnician", ...)` và `findActiveByRole("Sales", ...)`.
  - `TicketServiceTest`: `setUp` đổi `techAssigned`/`techOther` sang `ROLE_SALES` và thêm `generalUser`; cập nhật các test RBAC tạo/sửa/xoá/xem chi tiết, kiểm tra người xử lý phải là Sales đang hoạt động, phiếu cũ ưu tiên ngoài danh mục, và mặc định lọc `"all"` cho mọi vai; **thêm mới** `findActiveTechnicians_listsOnlyActiveSalesPlusKeptHandler`.

## Ghi chú ngoài phạm vi & Vận hành

- **Chạy migration V42 trước khi kéo code về checkout chính:** Agent không chạy migration lên CSDL nào. Người dùng cần tự chạy `db/migrations/V42__merge_kythuat_role_into_sales__ndat2003.sql` lên `poscs_db` (sau V40, V41) trước khi kéo nhánh về checkout chính.
- **Lỗ hổng/nợ kỹ thuật cũ ngoài phạm vi Task 9.3:**
  - `TechnicalSupportTicketController.handleCreate` / `handleUpdate` (lối `/ticket`) chưa kiểm tra lại vai trò `Sales` của `assignedTechnicianId` ở server như `TicketService.isAssignedTechnicianValid` đã làm (chỉ lọc dropdown và kiểm `> 0`); đây là hành vi có sẵn trước Task 9.3.
  - `TicketActionController.showEditForm` / `handleUpdate` (không thuộc "File sửa" của Task 9.3) cũng đang gọi `findById` trước `canUpdateTicket`; nhánh `else` của `TicketService.mergeForUpdate` / `validateTechnicianFields` và khối `<c:if test="${lockGeneral}">` ở `updateTicket.jsp` hiện không còn vai nào đi vào (an toàn, có thể dọn ở task refactor sau).
  - Một số comment cũ nhắc "Kỹ thuật"/"kỹ thuật viên" nằm ở file ngoài phạm vi Task 9.3: `TechnicalSupportTicketDAO.java` (dòng 259, 283, 335, 465, 487), `TechnicalRequest.java` (dòng 28), `rules.md` §1 (dòng 28), `web/jsp/guide/` (thuộc Task 10.6), và các JSP comment nội bộ (`<%-- ... --%>`) trong `updateTicket.jsp` / `viewdetailTicket.jsp`.
- **Tình trạng Git & file ngoài phạm vi cho bước Commit:**
  - `task_tracker.md` đang có sẵn diff kế hoạch của người dùng (bổ sung Task 9.3, Giai đoạn 10, ghi chú `task_tracker_deploy.md`) và vẫn giữ `[ ]` ở Task 9.3; hai file untracked `docs/PROMPTS.md` và `task_tracker_deploy.md` không thuộc phạm vi Task 9.3 — bước Commit không `git add` hai file untracked này.
  - Nhánh `feat/task-9.3-bo-vai-ky-thuat` tách từ `77df861` (cây file giống hệt `main` `64fe7a0`).
- **Integration test (`test/poscs/integration/`):** 3 lớp integration test mặc định bị skipped khi chạy `ant test`; đã kiểm tra tĩnh xác nhận `Fixtures.java`, `WriteAndTransactionIntegrationTest.java` và `ContractStatusIntegrationTest.java` không còn phụ thuộc vai/phòng `Kỹ thuật` hay ID bị dịch chuyển. Người dùng có thể tự chạy trên DB `_it` để xác minh thêm trên MySQL thật.
