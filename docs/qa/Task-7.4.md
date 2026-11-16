# Báo cáo Đối chiếu Toàn diện Hệ thống POSCS - Task 7.4

**Vai trò:** Tech Lead / System Auditor
**Phạm vi:** Đối chiếu toàn diện giữa `docs/SPEC.md` với mã nguồn thực tế (`src/java/poscs/`, `web/`), `db/schema.sql`, `PERMISSIONS.md` và `README.md` (chỉ đọc và liệt kê sai lệch theo `rules.md` §1, §2, §7).

---

## 1. Tổng quan Kết quả Đối chiếu

- **Logic nghiệp vụ cốt lõi (`poscs.service`, `poscs.validator`, `poscs.dao`) & RBAC (`PermissionService`, `AccessControl`):** Khớp 100% với các quy tắc đã chốt trong `docs/SPEC.md` và `rules.md` §1 across 54 Use Cases (`UC-01` → `UC-54`), 55 Business Rules (`BR-01` → `BR-55`), 20 NFR (`NFR-01` → `NFR-20`) và 56 mã thông báo (`MSG-001` → `MSG-056`).
- **Tổng số điểm sai lệch / lệch đồng bộ phát hiện:** **12 điểm**
  - **Cao (Critical):** 0
  - **Trung bình (Medium):** 3 (`D-03`, `D-06`, `D-09`)
  - **Thấp (Low):** 5 (`D-01`, `D-02`, `D-08`, `D-10`, `D-07`)
  - **Thông tin / Tài liệu (Info):** 4 (`D-04`, `D-05`, `D-11`, `D-12`)

---

## 2. Danh sách Chi tiết Các Điểm Sai lệch (`D-01` → `D-12`)

### 2.1. Khía cạnh 1: Use Cases (UC), Business Rules (BR) & NFR (`docs/SPEC.md` vs `src/java/poscs/` & `web/`)

| Mã | Vị trí trong SPEC / Tài liệu | Thực tế trong Code / DB | Đánh giá rủi ro | Khuyến nghị giải pháp |
| :--- | :--- | :--- | :--- | :--- |
| **D-01** | `docs/SPEC.md` (§4.5 - Bảng UC, dòng 165): Ghi `UC-23 \| Xóa phiếu (xóa mềm, ghi lý do, lưu vết)`. Trong khi đó `docs/SPEC.md` (§5.5, dòng 230), `BR-47` (dòng 303) và `MSG-048` (dòng 408) chỉ yêu cầu hộp thoại xác nhận và xóa mềm `is_deleted = 1`. | `TicketService.deleteTicket(...)` (dòng 418–448) và `TechnicalSupportTicketDAO.softDelete(...)` (dòng 485–499) chỉ cập nhật `is_deleted = 1` sau hộp thoại xác nhận `MSG-048`. Bảng `technicalrequests` trong `db/schema.sql` (dòng 4416–4462) không có cột lý do xóa phiếu. | **Thấp** (Nội bộ `SPEC.md` §4.5 lệch chữ với §5.5 và `BR-47`; code khớp §5.5 & `BR-47`). | Giữ nguyên code/DB; chỉnh lại mô tả dòng `UC-23` tại bảng §4.5 của `docs/SPEC.md` (dòng 165) thành `Xóa phiếu (xóa mềm)` để đồng nhất với §5.5 và `BR-47`. |
| **D-02** | `docs/SPEC.md` (§5.7 - Thông báo, dòng 244–248): Ghi hệ thống tự sinh thông báo cho *"đúng hai trường hợp"*: (1) Hợp đồng sắp hết hạn và (2) Phiếu hỗ trợ sắp/đã quá hạn SLA. | `NotificationService` (dòng 209–263) sinh đúng 2 loại trên theo lịch 60 phút. Tuy nhiên, `ChangeRequestController` (dòng 183–228) có sinh thêm thông báo thứ 3 (`ref_type = "CHANGE_REQUEST"`) khi nhân viên gửi yêu cầu thay đổi (`UC-50`) hoặc quản lý duyệt/từ chối (`UC-51`). | **Thấp** (Tính năng phục vụ trực tiếp `UC-50`/`UC-51`, nhưng chưa được mô tả tại §5.7 của `SPEC.md`). | Giữ nguyên logic thông báo `CHANGE_REQUEST` trong code, đồng thời bổ sung trường hợp thứ 3 (thông báo sự kiện `UC-50`/`UC-51`) vào §5.7 của `docs/SPEC.md`. |
| **D-03** | `docs/SPEC.md` (§5.1, dòng 196) & `NFR-20` (dòng 351): Phiên đăng nhập tự hết hạn sau **30 phút** không thao tác; cookie phiên đặt cờ `HttpOnly`. | `web/WEB-INF/web.xml` (dòng 14–18) đã cấu hình `<http-only>true</http-only>`, nhưng **chưa khai báo tường minh** `<session-timeout>30</session-timeout>` trong `<session-config>` (hiện phụ thuộc cấu hình mặc định 30 phút của Tomcat). | **Trung bình** (Nếu máy chủ Tomcat đổi cấu hình mặc định khác 30 phút thì ứng dụng sẽ lệch `NFR-20`). | Bổ sung `<session-timeout>30</session-timeout>` ngay trong khối `<session-config>` của `web/WEB-INF/web.xml`. |
| **D-04** | `docs/SPEC.md` (§2 - Kiến trúc hiện trạng, dòng 46–53): Ghi `controller` có 11 servlet, `dao` có 10 lớp, `model` có 21 POJO, `service` mới có 3 lớp (`AuthService`, `CustomerService`, `CustomerEvaluationService`), `validator` mới có 2 lớp. | Sau đợt refactor Giai đoạn 1 → 6, thực tế tại `src/java/poscs/` (đã mô tả đầy đủ trong `docs/ARCHITECTURE.md`) gồm: **38 file trong `poscs.controller`**, **21 file trong `poscs.service`**, **9 file trong `poscs.validator`**, **15 file trong `poscs.dao`**, **27 file trong `poscs.model`**, **18 file trong `poscs.common`**. | **Thông tin** (Chỉ là số liệu lịch sử trong `SPEC.md` §2 và `README.md` trước khi hoàn tất refactor). | Cập nhật lại bảng thống kê kiến trúc tại §2 của `docs/SPEC.md` và cây thư mục trong `README.md` (dòng 18–29) theo đúng số liệu của `docs/ARCHITECTURE.md`. |
| **D-05** | `docs/SPEC.md` (§11 - Điểm còn để ngỏ `[CẦN XÁC NHẬN]`, dòng 455–459): Còn gắn nhãn `[CẦN XÁC NHẬN]` cho ngưỡng thông báo hợp đồng sắp hết hạn và luồng chi tiết `UC-36` → `UC-54`. | Đã được chốt trong `rules.md` §1 (ngưỡng 30 ngày tại `ContractValidator.SOON_THRESHOLD_DAYS = 30` và `NotificationService.CONTRACT_EXPIRING_DAYS = 30`; giữ nguyên luồng `UC-36` → `UC-54`). | **Thông tin** (Nhãn chờ xác nhận cũ ở cuối tài liệu). | Gỡ bỏ mục `[CẦN XÁC NHẬN]` ở §11 của `docs/SPEC.md` khi cập nhật tài liệu. |

---

### 2.2. Khía cạnh 2: Cấu trúc Bảng / Cột (`db/schema.sql` vs `poscs.model` & `docs/SPEC.md` §7)

| Mã | Vị trí trong SPEC / DB | Thực tế trong Code / DB | Đánh giá rủi ro | Khuyến nghị giải pháp |
| :--- | :--- | :--- | :--- | :--- |
| **D-06** | `docs/SPEC.md` (§7 - Mô hình dữ liệu, dòng 315–327): Tiêu đề ghi **"22 bảng"** (trong bảng liệt kê 23 tên bảng). | `db/schema.sql` thực tế có **29 bảng** (`CREATE TABLE`). Có **6 bảng đang hoạt động** trong cả DB lẫn `poscs.model` / `poscs.dao` nhưng **chưa được liệt kê trong bảng §7 của SPEC**: <br>1. `enterprise_roles` (dòng 4708) – vai đối tác Khách mua / Nhà cung cấp (`BR-17`).<br>2. `contract_history` (dòng 4532) – lịch sử hợp đồng (`UC-45`, `UC-54`).<br>3. `contract_handovers` (dòng 4567) – bàn giao hợp đồng (`UC-48`, `UC-49`).<br>4. `contract_documents` (dòng 4605) – tài liệu hợp đồng (`UC-52`).<br>5. `contract_links` (dòng 4636) – liên kết HĐ Bán ↔ Mua (`UC-47`).<br>6. `change_requests` (dòng 4722) – yêu cầu thay đổi của cấp dưới (`UC-50`, `UC-51`). | **Trung bình** (Tài liệu §7 thiếu 6 bảng quan trọng đang chạy thực tế trong hệ thống). | Bổ sung 6 bảng trên vào bảng tổng hợp tại §7 của `docs/SPEC.md` (nâng tổng số lên 29 bảng). |
| **D-07** | `db/schema.sql` (Bảng `change_requests`, cột `intent`, dòng 4726): Comment SQL ghi `-- 'Tạo mới' \| 'Sửa' \| 'Xoá'`. | `ChangeRequest.INTENT_AMENDMENT = "Lập phụ lục"` (dòng 22), `ChangeRequestController` (dòng 85–94) và `PERMISSIONS.md` (dòng 229) sử dụng thêm giá trị thứ 4 là `'Lập phụ lục'` cho Hợp đồng. Do cột có kiểu `varchar(20)` nên lưu DB bình thường, không lỗi. | **Thấp** (Chỉ lệch comment trong `db/schema.sql`). | Cập nhật comment tại dòng 4726 của `db/schema.sql` thành `-- 'Tạo mới' \| 'Sửa' \| 'Xoá' \| 'Lập phụ lục'`. |
| **D-08** | `docs/SPEC.md` (§5.6, dòng 239) & `BR-28` (dòng 284): `personal_email` là trường **bắt buộc** khi tạo/sửa nhân viên. | Trong `db/schema.sql` (dòng 3493), cột `personal_email` của bảng `users` khai báo `varchar(100) DEFAULT NULL` (cho phép `NULL` ở tầng vật lý để tương thích bản ghi cũ trước migration), trong khi `EmployeeValidator` (dòng 96–102) và `ProfileValidator` (dòng 43–49) bắt buộc khác rỗng ở tầng ứng dụng. | **Thấp** (Hợp lý cho dữ liệu di trú, đã được khóa chặt ở tầng Validator). | Giữ nguyên thiết kế hiện tại; không cần đổi sang `NOT NULL` ở DB nếu còn tài khoản seed cũ chưa có email cá nhân. |

---

### 2.3. Khía cạnh 3: Ma trận Phân quyền (`PERMISSIONS.md` & `docs/SPEC.md` §3 vs `PermissionService` & `AccessControl`)

| Mã | Vị trí trong SPEC / PERMISSIONS.md | Thực tế trong Code (`PermissionService` / `AccessControl`) | Đánh giá rủi ro | Khuyến nghị giải pháp |
| :--- | :--- | :--- | :--- | :--- |
| **D-09** | `docs/SPEC.md` (§3.2 - Phân cấp trong Sales, dòng 70): Ghi *"Sales đã được giao tỉnh chỉ sửa được đối tác trong phạm vi 'Của tôi'..."* và trong `PermissionService` (dòng 650–664) có định nghĩa hàm `isContractInScope(User user, int contractOwnerId, Integer contractProvinceId)`. | Đối với **Khách hàng**, `CustomerService` (dòng 194–250) có kiểm tra phạm vi dòng (`isOutsideSalesScope`). Tuy nhiên đối với **Hợp đồng**, `PermissionService.isContractInScope(...)` hiện **không được gọi ở bất kỳ đâu** trong `ContractService` (mọi `Sales quản lý vùng` đều có thể sửa/chuyển trạng thái hợp đồng của vùng khác nếu xem được). Điều này khớp với mô tả ở `PERMISSIONS.md` (dòng 282–327: chỉ khóa row-level cho Customer, còn Contract khóa theo trạng thái `Nháp`/`Đã ký`), nhưng làm hàm `isContractInScope` chưa được sử dụng. | **Trung bình** (Nếu nghiệp vụ muốn khóa row-level cho cả Hợp đồng thì đang thiếu lệnh gọi; nếu chỉ khóa cho Khách hàng theo `PERMISSIONS.md` thì `isContractInScope` là hàm dự phòng chưa dùng). | Chọn 1 trong 2 phương án: <br>- **Phương án A (Theo `PERMISSIONS.md`):** Giữ nguyên hành vi hiện tại, chú thích rõ trên `isContractInScope`.<br>- **Phương án B (Khóa theo vùng cho cả HĐ):** Gọi `permissionService.isContractInScope(...)` trong `ContractService` khi sửa HĐ Nháp. |
| **D-10** | `docs/SPEC.md` (§9 - `MSG-022`, dòng 382): Quy định nội dung thông báo từ chối truy cập HTTP 403 là `"Bạn không có quyền truy cập nội dung này."` | `PermissionService.MSG_022` (dòng 29) khai báo đúng `"Bạn không có quyền truy cập nội dung này."`. Tuy nhiên, `AccessControl` (dòng 116–195) truyền chuỗi `"Bạn không có quyền thực hiện thao tác này."` / `"Chức năng này chỉ dành cho quản trị viên."` vào `response.sendError(403, ...)`, và trang `web/error403.jsp` (dòng 73–74) hiển thị cứng `"Tài khoản của bạn không có quyền thực hiện thao tác hoặc xem trang này."`. | **Thấp** (Không ảnh hưởng bảo mật vì mã HTTP 403 trả về đúng, chỉ khác câu chữ hiển thị so với `MSG-022`). | Đồng bộ câu thông báo trong `AccessControl.java` và `web/error403.jsp` về đúng nguyên văn `MSG-022` nếu cần kiểm thử tự động theo chuỗi. |
| **D-11** | `PERMISSIONS.md` (dòng 200) vs `PERMISSIONS.md` (dòng 260–267) & `docs/SPEC.md` (§3.2, dòng 67): Bảng tóm tắt ở dòng 200 của `PERMISSIONS.md` ghi `Admin picks a manager on the employee form \| done`, nhưng ngay dòng 260–267 của chính file này và `SPEC.md` §3.2 ghi rõ ô chọn "Cấp trên" đã gỡ khỏi giao diện và `manager_id` chỉ gán trong DB. | Code thực tế tại `EmployeeService` và form nhân viên đã gỡ ô chọn cấp trên (khớp `SPEC.md` §3.2 và `PERMISSIONS.md` dòng 260). Dòng 200 của `PERMISSIONS.md` bị sót từ phiên bản cũ. | **Thông tin** (Mâu thuẫn nội bộ giữa 2 đoạn trong cùng file `PERMISSIONS.md`). | Cập nhật lại dòng 200 trong `PERMISSIONS.md` cho khớp với dòng 260 và `docs/SPEC.md` §3.2. |
| **D-12** | `PERMISSIONS.md` (dòng 15–19) & `README.md` (dòng 195–198): Ghi luồng đăng nhập và kiểm tra quyền nằm ở `AuthenticationController` cùng 5 controller nguyên khối cũ (`CustomerController`, `ContractController`, `TechnicalSupportTicketController`...). | Các controller nguyên khối đã được tách thành các controller chuyên biệt (`LoginController`, `CustomerActionController`, `ContractActionController`, `TicketActionController`...) và kiểm tra quyền qua `PermissionService` + `AccessControl`. | **Thông tin** (Tên lớp cũ trong tài liệu `PERMISSIONS.md` và `README.md`). | Cập nhật tên các Controller/Service mới trong `PERMISSIONS.md` và `README.md`. |

---

## 3. Tổng hợp Hạng mục Đã Kiểm chứng Khớp 100%

1. **Phân quyền & Vai trò (RBAC):**
   - 3 vai trò duy nhất (`Admin`, `Sales`, `Kỹ thuật`), loại bỏ hoàn toàn `CS`/`CSKH`.
   - Phân biệt `Sales quản lý vùng` (`manager_id IS NULL`) và `Sales có cấp trên` (`manager_id IS NOT NULL`, chỉ xem và gửi yêu cầu thay đổi `UC-50`/`UC-51`).
   - Kỹ thuật chỉ sửa phiếu hỗ trợ được giao cho chính mình (`assigned_technician_id == userId`) và chỉ ghi đúng 5 trường kỹ thuật.
   - Quyền hủy bản ghi hợp đồng (`UC-34`/`BR-37`), chữa sai sót hợp đồng đã ký (`UC-54`), xác nhận bàn giao (`UC-49`) khớp tuyệt đối giữa `SPEC.md`, `PERMISSIONS.md` và `PermissionService`.
2. **Nghiệp vụ & Toàn vẹn Dữ liệu:**
   - Ranh giới giao dịch (`NFR-19`), xóa mềm (`is_deleted`), tự động tính trạng thái hợp đồng (`BR-17`), hạn mức phụ lục giảm trừ (`UC-35`), sinh mã tự động có retry khi đụng UNIQUE (`BR-55`, `BR-38`), cảnh báo lệch tiền hàng và thanh toán (`MSG-049`, `MSG-050`).
3. **Bảo mật (`NFR-01` → `NFR-20`):**
   - Mật khẩu băm BCrypt (`BR-03`), CSRF token (`CsrfUtil` / `AuthenticationFilter`), `PreparedStatement` 100% ở tầng DAO, giới hạn đọc log 512 KB (`NFR-17`), kiểm tra định dạng file tải lên (`BR-48`).

---

## 4. Trạng thái Giải quyết (Bước Fix - Task 7.4)

- [x] **D-01 (`docs/SPEC.md` §4.5 dòng 165):** Đã sửa mô tả `UC-23` trong bảng từ `Xóa phiếu (xóa mềm, ghi lý do, lưu vết)` thành `Xóa phiếu (xóa mềm)` để đồng nhất với §5.5, `BR-47` và `MSG-048`.
- [x] **D-02 (`docs/SPEC.md` §5.7):** Đã bổ sung mô tả thông báo sự kiện `CHANGE_REQUEST` khi nhân viên gửi yêu cầu thay đổi (`UC-50`) hoặc quản lý duyệt/từ chối (`UC-51`) bên cạnh 2 loại thông báo định kỳ của `NotificationScheduler`.
- [x] **D-03 (`web/WEB-INF/web.xml`):** Đã bổ sung `<session-timeout>30</session-timeout>` vào trong khối `<session-config>` để tường minh hóa `NFR-20`.
- [x] **D-04 (`docs/SPEC.md` §2 & `README.md`):** Đã cập nhật số lượng lớp theo gói tại `docs/SPEC.md` §2 và cây thư mục/tên controller trong `README.md` khớp với `docs/ARCHITECTURE.md` (38 file `controller`, 21 file `service`, 9 file `validator`, 15 file `dao`, 27 file `model`, 18 file `common`; thay `AuthenticationController` bằng `LoginController`, `LogoutController`, `PasswordResetController`, `ProfileController`).
- [x] **D-05 (`docs/SPEC.md` §11):** Đã cập nhật mục §11 ghi nhận các điểm `[CẦN XÁC NHẬN]` đã được chốt theo `rules.md` §1.
- [x] **D-06 (`docs/SPEC.md` §7):** Đã cập nhật tiêu đề và bảng §7 từ 22 bảng lên **29 bảng**, bổ sung đầy đủ 6 bảng còn thiếu: `enterprise_roles`, `contract_history`, `contract_handovers`, `contract_documents`, `contract_links`, `change_requests`.
- [x] **D-07 (`db/schema.sql` dòng 4726):** Đã cập nhật comment của cột `intent` bảng `change_requests` thành `-- 'Tạo mới' | 'Sửa' | 'Xoá' | 'Lập phụ lục'` (giữ nguyên cấu trúc bảng/cột).
- [x] **D-08 (`users.personal_email`):** Đã chốt giữ nguyên thiết kế hiện tại (nullable ở DB để tương thích dữ liệu cũ, bắt buộc ở `EmployeeValidator` và `ProfileValidator`).
- [x] **D-09 (`src/java/poscs/service/PermissionService.java`):** Đã chốt Phương án A theo `PERMISSIONS.md` (giữ nguyên hành vi phân quyền Hợp đồng); đã bổ sung Javadoc trên hàm `isContractInScope` giải thích rõ hàm này dùng cho kiểm tra phạm vi hợp đồng khi cần mở rộng row-level write lock.
- [x] **D-10 (`src/java/poscs/common/AccessControl.java` & `web/error403.jsp`):** Đã chuẩn hóa thông điệp trả về và hiển thị trên `web/error403.jsp` theo đúng chuẩn `MSG-022` (`"Bạn không có quyền truy cập nội dung này."`), giữ nguyên mã HTTP 403 và đảm bảo toàn bộ unit test pass.
- [x] **D-11 (`PERMISSIONS.md` dòng 200):** Đã sửa bảng tóm tắt khớp với dòng 260–267 và `docs/SPEC.md` §3.2 (`manager_id` gán trực tiếp trong DB, đã gỡ ô chọn cấp trên khỏi biểu mẫu nhân viên).
- [x] **D-12 (`PERMISSIONS.md` dòng 15–19 & `README.md`):** Đã cập nhật tên các Controller/Service mới sau khi tách (`LoginController`, `LogoutController`, `PasswordResetController`, `ProfileController`, `CustomerActionController`, `ContractActionController`, `TicketActionController`, `PermissionService`...).


---

## 5. Kết luận Nghiệm thu (Re-verify sau Fix)

**Kết quả: ✅ PASS**: cả 12 điểm `D-01` → `D-12` đã được kiểm chứng độc lập, không phát hiện sai lệch mới.

| Mã | Kết quả | Bằng chứng |
| :--- | :---: | :--- |
| D-01 | ✅ | `SPEC.md` §4.5: `UC-23 \| Xóa phiếu (xóa mềm)`, khớp §5.5 / `BR-47` / `MSG-048`. |
| D-02 | ✅ | `SPEC.md` §5.7 có mục thông báo sự kiện `ref_type = "CHANGE_REQUEST"` (`UC-50`, `UC-51`). |
| D-03 | ✅ | `web.xml`: `<session-timeout>30</session-timeout>` và `<http-only>true</http-only>` nằm trong `<session-config>`. |
| D-04 | ✅ | Đếm thực tế `src/java/poscs/`: 38 controller, 21 service, 9 validator, 15 dao, 27 model, 18 common; khớp `SPEC.md` §2 và `README.md`. Tên `LoginController`, `LogoutController`, `PasswordResetController`, `ProfileController` đã có. |
| D-05 | ✅ | `SPEC.md` §11 không còn mục `[CẦN XÁC NHẬN]` (chỉ còn dòng giải thích quy ước ở đầu file), các quyết định đã chốt theo `rules.md` §1. |
| D-06 | ✅ | `db/schema.sql` có 29 `CREATE TABLE`. Bảng §7 có 7+5+4+7+3+3 = 29 bảng. Mọi tên bảng trong schema đều có trong §7 (gồm 6 bảng bổ sung). |
| D-07 | ✅ | `schema.sql` dòng 4726: `-- 'Tạo mới' \| 'Sửa' \| 'Xoá' \| 'Lập phụ lục'`. Diff schema chỉ 1 dòng, cấu trúc không đổi. |
| D-08 | ✅ | `users.personal_email` vẫn `DEFAULT NULL`. Validator bắt buộc nhập giữ nguyên (theo `EmployeeValidator`/`ProfileValidator`, không có diff). |
| D-09 | ✅ | `PermissionService.isContractInScope` có Javadoc nêu mục đích và Phương án A; chỉ thêm comment, hành vi không đổi. |
| D-10 | ✅ | `AccessControl.MSG_022` = "Bạn không có quyền truy cập nội dung này." Cả hai `sendError(403, MSG_022)` dùng hằng này; `error403.jsp` hiển thị đúng câu chuẩn; HTTP 403 giữ nguyên. |
| D-11 | ✅ | `PERMISSIONS.md`: dòng bảng tóm tắt đã ghi `manager_id` gán trong DB, đã gỡ dropdown, khớp `SPEC.md` §3.2. |
| D-12 | ✅ | `PERMISSIONS.md` và `README.md` đã dùng tên controller/service mới (`AuthenticationController` đã được thay). |

**Kiểm thử (`rules.md` §0):**
- `ant compile-jsps`: BUILD SUCCESSFUL.
- `ant test`: 90 suite, **2027 test, 0 failure, 0 error, 3 skipped** (test tích hợp bị bỏ qua mặc định theo `rules.md` §0, chưa kiểm tra DB thật).
- `git diff --stat -- test` cho thấy **không có file test nào bị sửa**, tuân thủ `rules.md` §3.
- Phạm vi thay đổi của Dev: `PERMISSIONS.md`, `README.md`, `db/schema.sql` (chỉ comment), `docs/SPEC.md`, `AccessControl.java`, `PermissionService.java`, `web.xml`, `error403.jsp`. Không có thay đổi vượt phạm vi.

**Lưu ý nhỏ (không chặn nghiệm thu):** `docs/SPEC.md` đã được Dev sửa, trong khi `rules.md` §1 quy định Agent không sửa SPEC. Task 7.4 yêu cầu rõ việc cập nhật tài liệu này nên được chấp nhận; người dùng nên rà lại diff `SPEC.md` trước khi commit.

**Kết luận: PASS, có thể chuyển sang bước Commit.**
