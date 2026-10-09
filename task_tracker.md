# POSCS Task Tracker

> **Nguyên tắc làm việc:**
> - Bám sát Single Source of Truth tại `docs/SPEC.md` và quy chuẩn tại `.gemini/rules.md`.
> - **Chỉ giải quyết 1 task (checklist item) duy nhất trong 1 lượt chạy.**
> - Sau khi hoàn thành và toàn bộ test liên quan pass, đánh dấu `[x]` vào task đó.
> - Tuyệt đối không viết code dồn cục vào 1 file duy nhất; tách service/module/validator rõ ràng.

---

## Giai đoạn 1: Chuẩn bị Kiến trúc & Khởi tạo Tầng Dịch vụ (Service & Validator Foundation)

- [x] **Task 1.1:** Khởi tạo kiến trúc tầng Service (`poscs.service`) và Validator (`poscs.validator`) cơ bản kèm Base Service & Result/Response wrapper pattern.
- [ ] **Task 1.2:** Tách `AuthValidator` và `AuthService` từ `AuthenticationController`, chuyển giao toàn bộ logic kiểm tra đăng nhập, đổi mật khẩu, OTP email sang Service; viết Unit Test trong `test/poscs/service/AuthServiceTest.java`.
- [ ] **Task 1.3:** Tách `AuthenticationController` thành các controller đơn nhiệm: `LoginController`, `LogoutController`, `PasswordResetController`, `ProfileController`.

---

## Giai đoạn 2: Tái cấu trúc Module Khách hàng (Customer & Partner Module)

- [ ] **Task 2.1:** Xây dựng `CustomerValidator` (kiểm tra định dạng SĐT VN, email, mã KH-xxxx/NCC-xxxx theo BR-09..11, BR-52) và viết Unit Test cho validator.
- [ ] **Task 2.2:** Xây dựng `CustomerService` và `CustomerEvaluationService`, chuyển toàn bộ logic nghiệp vụ đánh giá vòng đời, lọc địa bàn và xuất Excel từ Controller về Service.
- [ ] **Task 2.3:** Tách nhỏ `CustomerDAO` thành `CustomerDAO` (nghiệp vụ chính) và `CustomerLifecycleDAO` (đánh giá & sự kiện vòng đời).
- [ ] **Task 2.4:** Refactor `CustomerController` thành các Controller chuyên biệt: `CustomerListController`, `CustomerDetailController`, `CustomerActionController` (Thêm/Sửa/Xóa), `CustomerExportController`.

---

## Giai đoạn 3: Tái cấu trúc Module Sản phẩm (Product Module)

- [ ] **Task 3.1:** Xây dựng `ProductValidator` (kiểm tra tên, danh mục, quy tắc tự sinh mã duy nhất BR-54, BR-55) và Unit Test tương ứng.
- [ ] **Task 3.2:** Xây dựng `ProductService`, chuyển toàn bộ xử lý danh mục phân cấp, upload ảnh/catalogue và ràng buộc xóa sản phẩm (BR-49) sang Service.
- [ ] **Task 3.3:** Tinh gọn `ProductController` chỉ giữ vai trò Presentation Layer, gọi qua `ProductService`.

---

## Giai đoạn 4: Tái cấu trúc Module Hợp đồng (Contract Monolith Decomposition)

- [ ] **Task 4.1:** Xây dựng `ContractValidator` (kiểm tra loại hợp đồng, thời hạn ngày ký/hiệu lực BR-16, BR-17, tính toán trạng thái tự động) và Unit Test.
- [ ] **Task 4.2:** Tách `ContractDAO` (~4,200 dòng) thành các DAO thành phần:
  - `ContractDAO` (CRUD hợp đồng gốc & lọc tìm kiếm)
  - `ContractHandoverDAO` (Chặng bàn giao phòng ban)
  - `ContractPaymentDAO` (Kỳ thanh toán hợp đồng)
  - `ContractDocumentDAO` (Tài liệu đính kèm & hủy mềm)
  - `ContractLinkDAO` (Liên kết mua - bán)
  - `ContractHistoryDAO` (Nhật ký trạng thái)
- [ ] **Task 4.3:** Xây dựng `ContractService`, `ContractHandoverService`, `ContractPaymentService` quản lý toàn bộ transaction và nghiệp vụ chuyển trạng thái/ký hợp đồng.
- [ ] **Task 4.4:** Tách `ContractController` (~3,100 dòng) thành các Controller gọn nhẹ theo từng chức năng:
  - `ContractListController`
  - `ContractDetailController`
  - `ContractActionController` (Tạo, Sửa, Ký, Hủy dự thảo)
  - `ContractHandoverController`
  - `ContractPaymentController`
  - `ContractDocumentController`
  - `ContractExportController`
- [ ] **Task 4.5:** Xây dựng module/action chữa sai sót hợp đồng đã ký dành cho Admin kèm ghi log kiểm toán bắt buộc (Audit Trail).

---

## Giai đoạn 5: Tái cấu trúc Module Hỗ trợ Kỹ thuật & Triển khai Tính năng Còn thiếu (Ticket & Devices)

- [ ] **Task 5.1:** Xây dựng `TicketValidator` (kiểm tra các trường bắt buộc, mức ưu tiên, cờ bảo hành BR-39..43) và Unit Test.
- [ ] **Task 5.2:** Xây dựng `TicketService`, chuyển giao logic chuyển trạng thái (*Mới tiếp nhận → Đang xử lý → Đã đóng*), tính hạn SLA và ghi nhận lịch sử xử lý.
- [ ] **Task 5.3:** **(Tính năng mới BR-44)** Triển khai `DeviceDAO` và tích hợp bảng `technicalrequestdevices`: Thêm, xem, sửa, xóa thông tin thiết bị (Tên thiết bị, Serial, Ghi chú lỗi) theo từng ticket.
- [ ] **Task 5.4:** **(Tính năng mới BR-44)** Cập nhật giao diện chi tiết Ticket (`ticket-detail.jsp`) và Controller để hiển thị và thao tác danh sách thiết bị gặp sự cố.
- [ ] **Task 5.5:** Refactor `TechnicalSupportTicketController` thành các controller phân tách: `TicketListController`, `TicketDetailController`, `TicketActionController`, `TicketExportController`.

---

## Giai đoạn 6: Tái cấu trúc Module Nhân viên, Phân quyền & Thông báo

- [ ] **Task 6.1:** Xây dựng `EmployeeService` & `EmployeeValidator` quản lý nghiệp vụ tài khoản, phân công địa bàn (`user_provinces`), khóa/mở khóa và gửi email thông tin đăng nhập.
- [ ] **Task 6.2:** Refactor `EmployeeController` và tinh gọn `EmployeeDAO`.
- [ ] **Task 6.3:** Xây dựng `NotificationService` và chuẩn hóa bộ quét lập lịch `NotificationScheduler`.
- [ ] **Task 6.4:** Tái cấu trúc `DashboardController` sử dụng `DashboardService` tổng hợp số liệu doanh thu và KPI thống nhất.

---

## Giai đoạn 7: Kiểm thử Tổng thể & Hoàn thiện Hệ thống

- [ ] **Task 7.1:** Chạy toàn bộ bộ kiểm thử Unit Test (`ant test`) và đảm bảo 100% test case pass.
- [ ] **Task 7.2:** Chạy kiểm thử Integration Test (`_it` database) xác nhận tính toàn vẹn của dữ liệu và transaction sau khi tách DAO/Service.
- [ ] **Task 7.3:** Đồng bộ tài liệu kiểm thử và cập nhật `docs/SPEC.md` với các cấu trúc mới.
