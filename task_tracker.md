# POSCS Task Tracker (v2)

> **Nguyên tắc làm việc:**
> - Bám `docs/SPEC.md` (bản v2) và `.gemini/rules.md`. Không suy diễn, không thêm tính năng ngoài SPEC.
> - **Chỉ giải quyết 1 task (checklist item) trong 1 lượt chạy.**
> - Chọn task `[ ]` đầu tiên theo thứ tự, trừ khi nó bị chặn bởi mục "Quyết định đang chờ" bên dưới: khi đó **dừng và hỏi người dùng**.
> - Không viết code dồn vào một file; tách service / validator / DAO / controller rõ ràng.
> - Mỗi task kết thúc bằng báo cáo ngắn: đã làm gì, file đã đổi, kết quả test, điểm chưa chắc chắn.

## Tiêu chí hoàn thành chung (áp dụng cho MỌI task)

1. Chạy toàn bộ `ant test`: **tất cả test hiện có pass** (không chỉ test mới). Trên máy này Ant không nằm trong PATH: dùng đường dẫn đầy đủ ghi ở mục "Lệnh của dự án" trong file rule.
2. Task refactor: **không đổi hành vi**, không đổi chữ ký public API/Service, không sửa assertion hay xóa test cũ.
3. Logic hoặc nhánh mới đều có test tương ứng trong `test/`.
4. Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ.
5. Đánh `[x]` kèm ghi chú ngắn các file đã sửa. Mỗi task ghi thêm "Xong khi" nếu có tiêu chí riêng.

## Quyết định đã chốt

1. Cách lấy IP cho BR-01 (Task 1.3): **không cần thay đổi**, giữ cách hiện tại.
2. Thông báo SLA phiếu: **báo trước 24 giờ**. Thông báo hợp đồng: tạm dùng **30 ngày** (BR-17).
3. Bảng phân công địa bàn **đã có trong DB** (`user_provinces`; tên chính xác theo `db/schema.sql`).
4. Hai gói `poscs.service` và `poscs.validator` đã được xác nhận và đã tồn tại.
5. Integration test: 3 lớp trong `test/poscs/integration/`, **mặc định bị bỏ qua (skipped)**; chỉ chạy khi trỏ vào CSDL dùng-một-lần có tên kết thúc bằng `_it` (xem Task 7.2).

## Quyết định còn chờ người dùng

Không còn mục nào.

---

## Giai đoạn 1: Nền tảng Service & Validator

- [x] **Task 1.1:** Khởi tạo kiến trúc tầng Service (`poscs.service`) và Validator (`poscs.validator`) cơ bản kèm Base Service & Result/Response wrapper pattern.
- [x] **Task 1.2:** Tách `AuthValidator` và `AuthService` từ `AuthenticationController`; chuyển logic kiểm tra đăng nhập, đổi mật khẩu, OTP email sang Service; Unit Test tại `test/poscs/service/AuthServiceTest.java`.
- [x] **Task 1.3:** Tách `AuthenticationController` thành `LoginController`, `LogoutController`, `PasswordResetController`, `ProfileController`; nối với `AuthService` và xóa logic trùng trong `AuthenticationController`.
  - *IP sau reverse proxy: đã chốt không cần xử lý thêm (xem "Quyết định đã chốt").*
- [ ] **Task 1.4:** `[SPEC mục 3]` `[UC-30..35, UC-44, UC-46..51, UC-54]` `[NFR-10]` Xây dựng `PermissionService` gom logic phân quyền về một chỗ: ma trận 3 vai trò (Admin / Sales / Kỹ thuật), phân biệt **Sales quản lý vùng** (không có cấp trên) với **Sales có cấp trên** (chỉ xem, gửi yêu cầu thay đổi), phạm vi dữ liệu "Của tôi" theo người phụ trách/cấp dưới/tỉnh được giao. Cấp trên đọc từ DB.
  - *Xong khi:* có unit test phủ ma trận 3 vai trò × các thao tác `(*)` trong SPEC mục 3.3; vi phạm quyền trả `MSG-022`. Không tạo vai trò CS.

---

## Giai đoạn 2: Module Khách hàng / Nhà cung cấp

- [x] **Task 2.1:** `[UC-30, UC-31]` `[BR-09, BR-10, BR-11, BR-27, BR-31, BR-32, BR-33, BR-52]` Xây dựng `CustomerValidator` và Unit Test.
  - *Lưu ý:* task này được làm theo SPEC cũ (`NCC-xxxx`). Xem Task 2.1a.
- [x] **Task 2.2:** `[UC-08, UC-09, UC-30, UC-31, UC-32, UC-38]` `[BR-12, BR-13, BR-14, BR-27, BR-31, BR-32, BR-34, BR-52]` Xây dựng `CustomerService` và `CustomerEvaluationService`.
- [x] **Task 2.1a:** `[UC-30]` `[BR-52]` **Sửa lỗi theo SPEC v2:** mã nhà cung cấp đúng định dạng **`NCC-xxx` (3 chữ số)**, mã khách hàng `KH-xxxx`. Rà `CustomerValidator`, bộ sinh mã và test liên quan; sửa **logic nguồn**, không sửa test để che lỗi.
  - *Đã hoàn thành:* Chuẩn hóa `SUPPLIER_CODE_PATTERN` thành `^NCC-\\d{3}$`, `CUSTOMER_CODE_PATTERN` thành `^KH-\\d{4}$` trong `CustomerValidator.java`; bộ sinh mã `CustomerDAO.generateNextSupplierCode()` sinh đúng 3 chữ số; bổ sung test case từ chối `NCC-0001`, chấp nhận `NCC-001` trong `CustomerValidatorTest.java`. Toàn bộ `ant test` pass.
- [x] **Task 2.3:** `[UC-08, UC-09, UC-30..32, UC-38]` `[BR-12, BR-27, BR-34, BR-52]` Tách `CustomerDAO` thành `CustomerDAO` (nghiệp vụ chính) và `CustomerLifecycleDAO` (đánh giá & sự kiện vòng đời).
  - *(Người dùng xác nhận đã làm và QA xong.)* Không đổi hành vi; xóa KH vẫn là **xóa mềm** và vẫn chặn khi còn hợp đồng hiệu lực (BR-34, MSG-040).
- [ ] **Task 2.4:** `[UC-08, UC-09, UC-30..32, UC-37]` `[BR-12, BR-13, BR-14, BR-34]` Refactor `CustomerController` thành `CustomerListController`, `CustomerDetailController`, `CustomerActionController` (Thêm/Sửa/Xóa), `CustomerExportController` (Excel, UC-37).
  - *Xong khi:* quyền ghi lấy từ `PermissionService` (Task 1.4); bộ lọc & từ khóa giữ nguyên khi phân trang và khi xuất Excel.

---

## Giai đoạn 3: Module Sản phẩm

- [ ] **Task 3.1:** `[UC-18, UC-19]` `[BR-54]` Xây dựng `ProductValidator` (bắt buộc Tên sản phẩm và Danh mục; danh mục phải còn dùng được; ảnh chỉ JPG/PNG/GIF/WEBP) và Unit Test.
  - *Lưu ý:* validator **không** kiểm trùng mã sản phẩm (BR-55).
- [ ] **Task 3.2:** `[UC-12, UC-13, UC-18, UC-19, UC-20]` `[BR-19, BR-48, BR-49, BR-54, BR-55]` Xây dựng `ProductService`: danh mục phân cấp, upload ảnh/catalogue, **sinh mã tự động dựa UNIQUE ở DB, trùng thì thử lại tối đa 5 lần không báo lỗi (BR-55, MSG-056)**, mã không đổi sau khi tạo, xóa mềm và chặn khi SP nằm trong hợp đồng còn hiệu lực.
- [ ] **Task 3.3:** `[UC-12, UC-13, UC-18, UC-19, UC-20]` `[BR-12, BR-14, BR-19, BR-49, BR-53]` Tinh gọn `ProductController` chỉ còn lớp Presentation, gọi qua `ProductService`.

---

## Giai đoạn 4: Module Hợp đồng (tách `ContractDAO` ~4.200 dòng và `ContractController` ~3.100 dòng)

> Phụ thuộc: cần xong **Task 1.4** trước. Mỗi DAO/controller là **một task riêng**. Chỉ **tách**, không thêm tính năng.

- [ ] **Task 4.1:** `[UC-33, UC-35, UC-44]` `[BR-17, BR-35, BR-36]` Xây dựng `ContractValidator`: trường bắt buộc (Mã HĐ, Tiêu đề, KH/NCC đúng vai, Loại, Người phụ trách), `Ngày ký ≤ hiệu lực ≤ kết thúc` (MSG-045), tính trạng thái tự động (BR-17), khoản giảm trừ phụ lục không vượt giá trị còn lại của HĐ gốc.
  - *Lưu ý:* **ngày ký không phải trường nhập** (hệ thống đóng dấu khi Ký); chiều Bán/Mua không đổi được.
- [ ] **Task 4.2:** `[UC-10, UC-11, UC-33, UC-34, UC-35, UC-44]` `[BR-16, BR-17, BR-35, BR-37]` Viết **test khóa hành vi hiện tại** của `ContractDAO` trước khi tách: bộ lọc mặc định (còn hiệu lực trong tháng, "Của tôi"), trạng thái tự tính, tạo ở Nháp, ký, huỷ bản ghi.
- [ ] **Task 4.3:** `[UC-45]` Tách `ContractHistoryDAO` (nhật ký trạng thái/sự kiện hợp đồng).
- [ ] **Task 4.4:** `[UC-53]` `[BR-18]` Tách `ContractPaymentDAO` (kỳ thanh toán).
- [ ] **Task 4.5:** `[UC-52]` Tách `ContractDocumentDAO` (tài liệu đính kèm là **đường dẫn**, hủy mềm kèm lý do bắt buộc).
- [ ] **Task 4.6:** `[UC-47]` Tách `ContractLinkDAO` (liên kết Bán ↔ Mua, gỡ liên kết).
- [ ] **Task 4.7:** `[UC-48, UC-49]` Tách `ContractHandoverDAO` (chặng bàn giao phòng ban).
- [ ] **Task 4.8:** `[UC-50, UC-51]` Tách phần yêu cầu thay đổi thành `ChangeRequestDAO` (nếu đã có trong code; không thêm tính năng).
- [ ] **Task 4.9:** `[UC-10, UC-11, UC-33, UC-34, UC-35, UC-39, UC-41, UC-44, UC-46]` `[BR-12, BR-16, BR-17, BR-18, BR-35..37]` Thu gọn `ContractDAO` còn CRUD hợp đồng gốc, hàng hóa, phụ lục và lọc/tìm kiếm.
- [ ] **Task 4.10:** `[UC-33, UC-34, UC-35, UC-39, UC-44, UC-46]` `[BR-17, BR-35, BR-36, BR-37]` Xây dựng `ContractService`: transaction (NFR-19), Ký (Nháp → Đã ký, tự đóng dấu ngày ký, kiểm quyền qua `PermissionService`), huỷ bản ghi (lý do bắt buộc; Nháp: Admin/quản lý vùng; đã ký: **chỉ Admin**; chặn nếu có phụ lục), sửa theo tiến độ (Nháp sửa mọi ô; đã ký chỉ đổi Người phụ trách; thanh lý/chấm dứt không sửa).
- [ ] **Task 4.11:** `[UC-48, UC-49, UC-53]` Xây dựng `ContractHandoverService` và `ContractPaymentService`.
- [ ] **Task 4.12:** `[UC-33, UC-34, UC-35, UC-44, UC-46]` `ContractActionController` (Tạo, Sửa, Ký, Huỷ bản ghi, Phụ lục).
- [ ] **Task 4.13:** `[UC-10, UC-11, UC-45]` `[BR-12, BR-14, BR-16, BR-18]` `ContractListController` và `ContractDetailController`.
- [ ] **Task 4.14:** `[UC-48, UC-49]` `ContractHandoverController`.
- [ ] **Task 4.15:** `[UC-53]` `ContractPaymentController`.
- [ ] **Task 4.16:** `[UC-52]` `ContractDocumentController`.
- [ ] **Task 4.17:** `[UC-41]` `ContractExportController` (Excel, cùng bộ lọc, không phân trang, sắp theo tỉnh/thành).
- [ ] **Task 4.18:** `[UC-47, UC-50, UC-51]` Controller cho liên kết Bán ↔ Mua và yêu cầu thay đổi (nếu đã có trong `ContractController` cũ).
- [ ] **Task 4.19:** `[UC-54]` Module **chữa sai sót hợp đồng đã ký** (chỉ Admin): chưa thanh lý/chấm dứt, **bắt buộc lý do**, ghi nhật ký loại sự kiện riêng, **ngày ký không sửa được**.

---

## Giai đoạn 5: Module Phiếu hỗ trợ kỹ thuật

> **BR-44 (thiết bị trong phiếu) đã chốt KHÔNG làm.** Phiếu gắn với hợp đồng; thiết bị/lỗi ghi ở Mô tả/ghi chú. Không tạo `DeviceDAO`, không làm UI thiết bị.

- [ ] **Task 5.1:** `[UC-21, UC-22]` `[BR-39, BR-41, BR-43]` Xây dựng `TicketValidator`: bắt buộc Khách hàng, Loại phiếu, Mức ưu tiên, Kênh tiếp nhận, Mô tả sự cố, Kỹ thuật viên; ưu tiên ∈ {Thấp, Bình thường, Cao, Khẩn cấp}; cờ bảo hành Có/Không mặc định Có. **Trạng thái và Ngày tạo do hệ thống gán**, không phải trường nhập. Unit Test.
- [ ] **Task 5.2:** `[UC-16, UC-17, UC-21, UC-22, UC-23]` `[BR-38..43, BR-47]` Xây dựng `TicketService`: sinh mã ticket duy nhất, chuyển trạng thái *Mới tiếp nhận → Đang xử lý → Đã đóng* (sai quy trình → MSG-051), ghi thời điểm hoàn tất lần đầu Đã đóng, SLA quá hạn, lịch sử trạng thái trong **một giao dịch**, Kỹ thuật chỉ sửa phiếu được giao, xóa mềm chỉ khi *Mới tiếp nhận* hoặc *Đã đóng*.
- [ ] **Task 5.3:** `[UC-16, UC-17]` `[BR-12, BR-14, BR-45, BR-46]` `TicketListController` (dải KPI, bộ lọc, mặc định "Phiếu của tôi" cho Kỹ thuật viên) và `TicketDetailController`.
- [ ] **Task 5.4:** `[UC-21, UC-22, UC-23]` `[BR-47]` `TicketActionController` (Tạo, Cập nhật, Xóa).
- [ ] **Task 5.5:** `[UC-42]` `TicketExportController` (xuất **Excel hoặc PDF**).

---

## Giai đoạn 6: Nhân viên, Thông báo, Tổng quan, Nhật ký

- [ ] **Task 6.1:** `[UC-24..29]` `[BR-09, BR-10, BR-25..30]` Xây dựng `EmployeeValidator` và `EmployeeService`: bắt buộc Họ, Tên, Phòng ban, Vai trò, Ngày vào làm, Email cá nhân; SĐT/CCCD duy nhất (MSG-033); ô Vai trò không có Admin, không thăng lên Admin; địa bàn mỗi tỉnh một người phụ trách; khóa/mở khóa không tự khóa mình; gửi tài khoản chỉ ghi đè mật khẩu **sau khi email gửi thành công**.
  - Dùng bảng phân công địa bàn **đã có trong DB** (xem tên trong `db/schema.sql`), không tạo bảng mới.
- [ ] **Task 6.2:** `[UC-24..29]` `[BR-12, BR-14, BR-22..24, BR-26]` Refactor `EmployeeController` và tinh gọn `EmployeeDAO`.
- [ ] **Task 6.3:** `[UC-14, UC-15]` `[BR-20, BR-21]` Xây dựng `NotificationService` và chuẩn hóa `NotificationScheduler`: chu kỳ **60 phút**, chỉ 2 loại tự sinh (HĐ sắp hết hạn → người phụ trách; phiếu sắp/đã quá hạn SLA → KTV được giao), mỗi cặp một thông báo, chạy lại không tạo trùng.
  - Ngưỡng: phiếu báo trước **24 giờ** so với hạn SLA; hợp đồng **30 ngày** (BR-17).
- [ ] **Task 6.4:** `[UC-36]` `[BR-15, BR-18]` `DashboardService` + tinh gọn `DashboardController`: số liệu tổng hợp, doanh thu lấy từ `contract_payments`, lọc Năm/Kỳ (năm không hợp lệ/trống thì không lọc).
- [ ] **Task 6.5:** `[UC-43]` `[NFR-17]` Rà `SystemLogController`: chỉ Admin, đọc tối đa 512 KB cuối tệp log mỗi lần; bổ sung test nếu thiếu.

---

## Giai đoạn 7: Kiểm thử Tổng thể & Hoàn thiện

- [ ] **Task 7.1:** Chạy toàn bộ `ant test`, đảm bảo 100% pass. *Lưu ý:* test tích hợp bị **skipped** mặc định, nên "pass" ở đây chưa kiểm tra DB thật; việc đó thuộc Task 7.2.
- [ ] **Task 7.2:** Chạy integration test (`ContractStatusIntegrationTest`, `DaoSchemaIntegrationTest`, `WriteAndTransactionIntegrationTest` trong `test/poscs/integration/`) trên CSDL dùng-một-lần `poscs_it` để xác nhận toàn vẹn dữ liệu và transaction sau khi tách DAO/Service.
  - Lệnh: `ant -Dtest-sys-prop.DB_URL=jdbc:mysql://localhost:3306/poscs_it -Dtest-sys-prop.DB_USER=root -Dtest-sys-prop.DB_PASSWORD=<do người dùng nhập> test` (dùng đường dẫn đầy đủ tới `ant.bat`).
  - **Mỗi lượt chạy xóa sạch dữ liệu của `poscs_it`.** Code chặn nếu tên DB không kết thúc bằng `_it`.
  - **Agent không tự tạo DB, không tự điền hay lưu mật khẩu, không trỏ vào DB khác.** Nếu `poscs_it` chưa có hoặc chưa có mật khẩu: dừng và báo người dùng. Cách tạo DB xem `README.md`.
- [ ] **Task 7.3:** Cập nhật **`docs/ARCHITECTURE.md`** (không sửa `docs/SPEC.md`) mô tả cấu trúc mới: `service`, `validator`, các DAO/controller đã tách.
- [ ] **Task 7.4:** Đối chiếu `docs/SPEC.md` với code, `db/schema.sql`, `PERMISSIONS.md`; **chỉ liệt kê sai lệch cho người dùng**, không tự sửa SPEC.
