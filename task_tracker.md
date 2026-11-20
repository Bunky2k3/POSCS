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

## Cách ghi một task mới

Dòng task viết như các task bên dưới (`**Task X.X:** <Tên task>`), kèm các dòng con sau. Các dòng con này là chỗ lấy nội dung điền vào bộ prompt 4 bước (Code, QA, Fix, Commit):

- **UC:** `UC-xx` (ghi "không có" nếu task không gắn UC).
- **BR / MSG / NFR:** các mã trong `docs/SPEC.md` mà task phải đáp ứng.
- **File sửa:** đường dẫn từng file, kèm việc cần làm ở file đó.
- **File test:** file test và các test cần thêm.
- **Không đụng:** file hoặc phần nằm ngoài phạm vi task.
- **Lưu ý:** bối cảnh, lệnh đặc biệt, điều kiện phải dừng lại hỏi.
- **Xong khi:** tiêu chí riêng, ngoài "Tiêu chí hoàn thành chung" ở trên.
- **Nhánh:** `<type>/task-X.X-<tên-ngắn>` (không làm trên `main`).
- **Commit:** `<type>(<module>): <mô tả ngắn> (Task X.X)`.

## Quyết định đã chốt

1. Cách lấy IP cho BR-01 (Task 1.3): **không cần thay đổi**, giữ cách hiện tại.
2. Thông báo SLA phiếu: **báo trước 24 giờ**. Thông báo hợp đồng: chốt **30 ngày** (BR-17).
3. Bảng phân công địa bàn **đã có trong DB** (`user_provinces`; tên chính xác theo `db/schema.sql`).
4. Hai gói `poscs.service` và `poscs.validator` đã được xác nhận và đã tồn tại.
5. Integration test: 3 lớp trong `test/poscs/integration/`, **mặc định bị bỏ qua (skipped)**; chỉ chạy khi trỏ vào CSDL dùng-một-lần có tên kết thúc bằng `_it` (xem Task 7.2).
6. UC-50, UC-51 (Change Request): `ChangeRequestDAO` và `ChangeRequestController` đã tồn tại độc lập kèm test, giữ nguyên không tách lại (Task 4.8 bỏ).
7. UC-54 (Chữa sai sót HĐ đã ký): tích hợp vào `ContractService` (Task 4.10) và `ContractActionController` (Task 4.12); không tạo controller riêng.
8. Luồng chi tiết UC-36 → UC-54: lấy hành vi code và JSP hiện có làm chuẩn (khóa bằng test ở Task 4.2), chỉ tách nhỏ theo rules §3 mà không thêm tính năng.
9. **Người ký bên mình (Giai đoạn 8, chốt 19/11/2026):** luôn là **Giám đốc chi nhánh (GĐCN)**. Admin khai họ tên, chức vụ, SĐT, fax một lần ở trang **"Thông tin chi nhánh"** (bảng một dòng `branch_info`). Trên form hợp đồng các ô này **chỉ hiển thị, không sửa tay**. SĐT và fax **lưu vào từng hợp đồng** (`contracts.signer_phone`, `contracts.signer_fax`) giống họ tên, chức vụ, để sau này đổi GĐCN thì hợp đồng cũ vẫn giữ đúng thông tin lúc ký. Phần người ký **bên đối tác giữ nguyên nhập tay**.
10. **Thời điểm chép thông tin GĐCN vào hợp đồng** (đề xuất lúc lập kế hoạch 19/11/2026): lúc tạo hợp đồng/phụ lục, và mỗi lần lưu khi hợp đồng còn **Nháp**, lấy GĐCN **hiện tại**. Từ **Đã ký** trở đi giữ nguyên; UC-54 (chữa sai sót) không đổi bốn ô này. Chưa khai GĐCN (**họ tên GĐCN còn trống**, kể cả dòng gieo sẵn chỉ có chức vụ; chốt thêm theo QA Task 8.1): tạo mới thì để trống **cả bốn ô**, bản Nháp đang có thì giữ giá trị cũ. Hợp đồng cũ trong CSDL **không chạy lại dữ liệu**.
11. **Người phụ trách hợp đồng (chốt 19/11/2026):** lấy theo **người tạo**, không chọn tay nữa. Sales (quản lý vùng) tạo → là chính mình; **Admin tạo → vẫn chọn một Sales**; **phụ lục → theo người phụ trách hợp đồng gốc**. Sau khi tạo **khoá luôn**, kể cả khi đã ký (bỏ việc đổi người phụ trách ở trang Sửa). Người phụ trách khác người ký bên mình (GĐCN, mục 9). **Ngoại lệ duy nhất:** Admin đổi được khi người phụ trách hiện tại đã bị khoá tài khoản (nghỉ việc), sang một Sales đang hoạt động.
12. **SĐT, fax của GĐCN tối đa 20 ký tự** (chốt 19/11/2026 theo QA Task 8.1): khớp cột `varchar(20)` của Task 8.2 (`branch_info.phone`, `branch_info.fax`, `contracts.signer_phone`, `contracts.signer_fax`); đã ghi vào BR-57. Vượt thì báo MSG-015 (SĐT) / MSG-059 (fax).
13. **Cơ cấu theo chi nhánh thật (Giai đoạn 9, chốt 19/11/2026):** chi nhánh khoảng 12 người: Ban giám đốc (GĐ, PGĐ dùng tài khoản Admin), Phòng kinh doanh (4 người ngang nhau, không gán cấp trên), Phòng tổng hợp. Không có phòng Kỹ thuật: **bỏ vai Kỹ thuật**, quản lý sản phẩm và xử lý phiếu hỗ trợ **chuyển sang Sales**. **Gộp phòng Kế toán và Dự án thành "Tổng hợp"**. Thêm **vai Tổng hợp**: xem khách hàng, hợp đồng, sản phẩm (không vào phiếu hỗ trợ); xác nhận bàn giao; "Ghi nhận đã thu" kỳ thanh toán (không lập, không xoá kỳ). Vẫn đủ ba vai: Admin, Sales, Tổng hợp. Việc kho, gọi xe, xuất-nhập kho của Phòng tổng hợp **không làm** (ngoài phạm vi).

## Quyết định còn chờ người dùng

Không còn mục nào.

---

## Giai đoạn 1: Nền tảng Service & Validator

- [x] **Task 1.1:** Khởi tạo kiến trúc tầng Service (`poscs.service`) và Validator (`poscs.validator`) cơ bản kèm Base Service & Result/Response wrapper pattern.
- [x] **Task 1.2:** Tách `AuthValidator` và `AuthService` từ `AuthenticationController`; chuyển logic kiểm tra đăng nhập, đổi mật khẩu, OTP email sang Service; Unit Test tại `test/poscs/service/AuthServiceTest.java`.
- [x] **Task 1.3:** Tách `AuthenticationController` thành `LoginController`, `LogoutController`, `PasswordResetController`, `ProfileController`; nối với `AuthService` và xóa logic trùng trong `AuthenticationController`.
  - *IP sau reverse proxy: đã chốt không cần xử lý thêm (xem "Quyết định đã chốt").*
- [x] **Task 1.4:** `[SPEC mục 3]` `[UC-30..35, UC-44, UC-46..51, UC-54]` `[NFR-10]` Xây dựng `PermissionService` gom logic phân quyền về một chỗ: ma trận 3 vai trò (Admin / Sales / Kỹ thuật), phân biệt **Sales quản lý vùng** (không có cấp trên) với **Sales có cấp trên** (chỉ xem, gửi yêu cầu thay đổi), phạm vi dữ liệu "Của tôi" theo người phụ trách/cấp dưới/tỉnh được giao. Cấp trên đọc từ DB.
  - *Đã hoàn thành:* Xây dựng `poscs.service.PermissionService` gom toàn bộ ma trận RBAC 3 vai trò, xác thực DB theo thời gian thực (fail-closed), ListScope bảo mật và kiểm tra quyền dòng; Unit Test tại `test/poscs/service/PermissionServiceTest.java` (29 test cases pass 100%).

---

## Giai đoạn 2: Module Khách hàng / Nhà cung cấp

- [x] **Task 2.1:** `[UC-30, UC-31]` `[BR-09, BR-10, BR-11, BR-27, BR-31, BR-32, BR-33, BR-52]` Xây dựng `CustomerValidator` và Unit Test.
  - *Lưu ý:* task này được làm theo SPEC cũ (`NCC-xxxx`). Xem Task 2.1a.
- [x] **Task 2.2:** `[UC-08, UC-09, UC-30, UC-31, UC-32, UC-38]` `[BR-12, BR-13, BR-14, BR-27, BR-31, BR-32, BR-34, BR-52]` Xây dựng `CustomerService` và `CustomerEvaluationService`.
- [x] **Task 2.1a:** `[UC-30]` `[BR-52]` **Sửa lỗi theo SPEC v2:** mã nhà cung cấp đúng định dạng **`NCC-xxx` (3 chữ số)**, mã khách hàng `KH-xxxx`. Rà `CustomerValidator`, bộ sinh mã và test liên quan; sửa **logic nguồn**, không sửa test để che lỗi.
  - *Đã hoàn thành:* Chuẩn hóa `SUPPLIER_CODE_PATTERN` thành `^NCC-\\d{3}$`, `CUSTOMER_CODE_PATTERN` thành `^KH-\\d{4}$` trong `CustomerValidator.java`; bộ sinh mã `CustomerDAO.generateNextSupplierCode()` sinh đúng 3 chữ số; bổ sung test case từ chối `NCC-0001`, chấp nhận `NCC-001` trong `CustomerValidatorTest.java`. Toàn bộ `ant test` pass.
- [x] **Task 2.3:** `[UC-08, UC-09, UC-30..32, UC-38]` `[BR-12, BR-27, BR-34, BR-52]` Tách `CustomerDAO` thành `CustomerDAO` (nghiệp vụ chính) và `CustomerLifecycleDAO` (đánh giá & sự kiện vòng đời).
  - *(Người dùng xác nhận đã làm và QA xong.)* Không đổi hành vi; xóa KH vẫn là **xóa mềm** và vẫn chặn khi còn hợp đồng hiệu lực (BR-34, MSG-040).
- [x] **Task 2.4:** `[UC-08, UC-09, UC-30..32, UC-37]` `[BR-12, BR-13, BR-14, BR-34]` Refactor `CustomerController` thành `CustomerListController`, `CustomerDetailController`, `CustomerActionController` (Thêm/Sửa/Xóa), `CustomerExportController` (Excel, UC-37).
  - *Đã hoàn thành:* Tách `CustomerController` thành 4 controller chuyên biệt (`CustomerListController`, `CustomerDetailController`, `CustomerActionController`, `CustomerExportController`) và `CustomerControllerHelper`. Quyền ghi và phạm vi kiểm soát chặt chẽ qua `PermissionService` (fail-closed, MSG-022, RBAC). Bộ lọc & phân trang (BR-12, BR-13, BR-14) và xuất Excel (UC-37) hoạt động đồng bộ. Unit tests đạt 100% pass (bổ sung test ngoại lệ trong `CustomerActionControllerTest`, `CustomerListControllerTest`, `CustomerDetailControllerTest`, `CustomerExportControllerTest` và `CustomerControllerTest`).

---

## Giai đoạn 3: Module Sản phẩm

- [x] **Task 3.1:** `[UC-18, UC-19]` `[BR-54]` Xây dựng `ProductValidator` (bắt buộc Tên sản phẩm và Danh mục; danh mục phải còn dùng được; ảnh chỉ JPG/PNG/GIF/WEBP) và Unit Test.
  - *Đã hoàn thành:* Xây dựng `poscs.validator.ProductValidator` kế thừa `BaseValidator` kiểm tra ràng buộc BR-54 (Tên sản phẩm bắt buộc, tối đa 150 ký tự; Danh mục bắt buộc > 0), danh mục phải còn dùng được (không xóa mềm, không phải danh mục cha, hỗ trợ keepCategoryId cho form sửa), BR-48 (ảnh chỉ nhận JPG/PNG/GIF/WEBP, catalogue chỉ nhận PDF, hỗ trợ Part và tên file). Unit test tại `test/poscs/validator/ProductValidatorTest.java` (28/28 test cases pass 100%).
  - *Lưu ý:* validator **không** kiểm trùng mã sản phẩm (BR-55).
- [x] **Task 3.2:** `[UC-12, UC-13, UC-18, UC-19, UC-20]` `[BR-19, BR-48, BR-49, BR-54, BR-55]` Xây dựng `ProductService`: danh mục phân cấp, upload ảnh/catalogue, **sinh mã tự động dựa UNIQUE ở DB, trùng thì thử lại tối đa 5 lần không báo lỗi (BR-55, MSG-056)**, mã không đổi sau khi tạo, xóa mềm và chặn khi SP nằm trong hợp đồng còn hiệu lực.
  - *Đã hoàn thành:* Xây dựng `poscs.service.ProductService` kế thừa `BaseService`, đáp ứng đầy đủ xử lý cây danh mục 3 cấp (cộng dồn số lượng đệ quy, lọc mở rộng subtree, accordion), chi tiết sản phẩm đầy đủ (ảnh, catalogue, hợp đồng sử dụng theo BR-19), ép buộc sinh mã hệ thống `SP-xxxx` và cơ chế thử lại tối đa 5 lần khi đụng UNIQUE DB (BR-55, MSG-056), mã không đổi sau khi tạo, xóa mềm và chặn khi còn hợp đồng (`has_active_contracts`). Unit tests tại `test/poscs/service/ProductServiceTest.java` (42/42 tests pass 100%). QA report tại `docs/qa/Task-3.2.md`.
- [x] **Task 3.3:** `[UC-12, UC-13, UC-18, UC-19, UC-20]` `[BR-12, BR-14, BR-19, BR-49, BR-53]` Tinh gọn `ProductController` chỉ còn lớp Presentation, gọi qua `ProductService`.
  - *Đã hoàn thành:* Tinh gọn `poscs.controller.ProductController` về lớp Presentation thuần túy (giảm từ 638 dòng xuống 258 dòng, mọi phương thức <= 30 dòng), loại bỏ toàn bộ truy cập DAO và nghiệp vụ trùng lặp, ủy quyền đầy đủ qua `ProductService`. Hỗ trợ DI linh hoạt. Tinh chỉnh nhẹ `ProductService.removeMarkedFiles` tương thích ngược. Bổ sung test kiểm tra phân quyền RBAC và forward routing. Unit tests tại `test/poscs/controller/ProductControllerTest.java` (54/54 tests pass 100%). QA report tại `docs/qa/Task-3.3.md`.

---

## Giai đoạn 4: Module Hợp đồng (tách `ContractDAO` ~4.200 dòng và `ContractController` ~3.100 dòng)

> Phụ thuộc: cần xong **Task 1.4** trước. Mỗi DAO/controller là **một task riêng**. Chỉ **tách**, không thêm tính năng.

- [x] **Task 4.1:** `[UC-33, UC-35, UC-44]` `[BR-17, BR-35, BR-36]` Xây dựng `ContractValidator`: trường bắt buộc (Mã HĐ, Tiêu đề, KH/NCC đúng vai, Loại, Người phụ trách), `Ngày ký ≤ hiệu lực ≤ kết thúc` (MSG-045), tính trạng thái tự động (BR-17), khoản giảm trừ phụ lục không vượt giá trị còn lại của HĐ gốc.
  - *Đã hoàn thành:* Xây dựng `poscs.validator.ContractValidator` kế thừa `BaseValidator` kiểm tra ràng buộc BR-35 (Mã HĐ, Tiêu đề, Loại HĐ, Người phụ trách, KH/NCC đúng vai theo chiều Bán↔Khách mua / Mua↔Nhà cung cấp; ngày ký do hệ thống đóng dấu, không nhập tay; chiều Bán/Mua không đổi được; HĐ đã ký chỉ đổi Người phụ trách; HĐ đã thanh lý/chấm dứt sớm không sửa); BR-36 & MSG-045 (thứ tự Ngày ký ≤ Hiệu lực ≤ Kết thúc); BR-17 (tính trạng thái tự động Chưa hiệu lực / Đang hiệu lực / Sắp hết hạn ≤ 30 ngày / Đã hết hạn); UC-35 (khoản giảm trừ phụ lục không vượt giá trị còn lại HĐ gốc). Unit test tại `test/poscs/validator/ContractValidatorTest.java` (55/55 test cases pass 100%).
  - *Lưu ý:* **ngày ký không phải trường nhập** (hệ thống đóng dấu khi Ký); chiều Bán/Mua không đổi được.
- [x] **Task 4.2:** `[UC-10, UC-11, UC-33, UC-34, UC-35, UC-44]` `[BR-16, BR-17, BR-35, BR-37]` Viết **test khóa hành vi hiện tại** của `ContractDAO` trước khi tách: bộ lọc mặc định (còn hiệu lực trong tháng, "Của tôi"), trạng thái tự tính, tạo ở Nháp, ký, huỷ bản ghi.
  - *Đã hoàn thành:* Bổ sung 28 unit tests khóa hành vi hiện tại của `poscs.dao.ContractDAO` tại `test/poscs/dao/ContractDAOTest.java` (tổng 95/95 test cases pass 100%), bao phủ: bộ lọc mặc định "Của tôi" + cửa sổ hiệu lực tháng hiện tại + "Mọi thời điểm" (UC-10, BR-16); trạng thái tự tính theo ngày (BR-17, UC-10, UC-11); tạo ở Nháp không nhập ngày ký và ghi nhật ký khởi tạo (UC-33, BR-35); ký và cập nhật theo tiến độ (UC-44, UC-35, BR-35); huỷ bản ghi xóa mềm kèm lý do trong một giao dịch (UC-34, BR-37). QA report tại `docs/qa/Task-4.2.md`.
- [x] **Task 4.3:** `[UC-45]` Tách `ContractHistoryDAO` (nhật ký trạng thái/sự kiện hợp đồng).
  - *Đã hoàn thành:* Xây dựng `poscs.dao.ContractHistoryDAO` chuyên trách ghi nhận và truy vấn lịch sử hợp đồng (UC-45, bảng `contract_history`), bắt buộc thực hiện trong transaction đang mở (NFR-19); cắt chuỗi an toàn theo schema (`DETAIL_MAX_LENGTH = 500`); ánh xạ chặt chẽ sự kiện tiến độ (`eventTypeOf`). Tích hợp ủy quyền vào `poscs.dao.ContractDAO` (`historyDAO`, getter, DI constructor) duy trì 100% tương thích ngược. Unit test tại `test/poscs/dao/ContractHistoryDAOTest.java` (18/18 test cases pass 100%). QA report tại `docs/qa/Task-4.3.md`.
- [x] **Task 4.4:** `[UC-53]` `[BR-18]` Tách `ContractPaymentDAO` (kỳ thanh toán).
  - *Đã hoàn thành:* Xây dựng `poscs.dao.ContractPaymentDAO` chuyên trách quản lý các kỳ thanh toán hợp đồng (UC-53, BR-18, bảng `contract_payments`): truy vấn danh sách, thêm kỳ thanh toán, đánh dấu đã thanh toán, xóa kỳ thanh toán kèm ghi nhật ký `contract_history` trong cùng một giao dịch (NFR-19), tính tổng tiền kỳ thanh toán theo cụm và doanh thu theo kỳ/tháng cho Dashboard. Tích hợp ủy quyền vào `poscs.dao.ContractDAO` (`paymentDAO`, getter, DI constructor dùng chung `ContractHistoryDAO`), tái sử dụng hàm định dạng `formatMoney` và `formatDate`. Unit test tại `test/poscs/dao/ContractPaymentDAOTest.java` (36/36 test cases pass 100%). QA report tại `docs/qa/Task-4.4.md`.
- [x] **Task 4.5:** `[UC-52]` Tách `ContractDocumentDAO` (tài liệu đính kèm là **đường dẫn**, hủy mềm kèm lý do bắt buộc).
  - *Đã hoàn thành:* Xây dựng `poscs.dao.ContractDocumentDAO` chuyên trách quản lý hồ sơ tài liệu đính kèm hợp đồng (UC-52, bảng `contract_documents`): chỉ lưu đường dẫn URL, huỷ mềm tài liệu (`is_deleted = 1`) bắt buộc có lý do và ghi nhận kiểm toán `contract_history` (loại sự kiện `EVENT_DOCUMENT`) trong cùng giao dịch (NFR-19); chuẩn hóa khoảng trắng và kiểm soát độ dài theo schema (`doc_type` 50, `file_url` 500, `title`/`note`/`delete_reason` 255). Tích hợp ủy quyền vào `poscs.dao.ContractDAO` (`documentDAO`, getter, DI constructor), tái sử dụng các hàm tiện ích `trimToNull` và `fullName`. Unit test tại `test/poscs/dao/ContractDocumentDAOTest.java` (21/21 test cases pass 100%) và `test/poscs/dao/ContractDAOTest.java` (99/99 test cases pass 100%). QA report tại `docs/qa/Task-4.5.md`.
- [x] **Task 4.6:** `[UC-47]` Tách `ContractLinkDAO` (liên kết Bán ↔ Mua, gỡ liên kết).
  - *Đã hoàn thành:* Xây dựng `poscs.dao.ContractLinkDAO` chuyên trách quản lý quan hệ liên kết hợp đồng Bán ↔ Mua (UC-47, bảng `contract_links`): bảo đảm chiều Bán-Mua, cả hai đều là hợp đồng gốc (không phải phụ lục), không trùng cặp; ghi nhận 2 dòng nhật ký `contract_history` (loại sự kiện `EVENT_LINKED`) trong cùng transaction (NFR-19); tính tổng giá trị đầu vào phục vụ đối chiếu (`sumLinkedBuyValue`). Tích hợp ủy quyền vào `poscs.dao.ContractDAO` (`linkDAO`, getter, DI constructor) duy trì 100% tương thích ngược; tái sử dụng các tiện ích `trimToNull`, `truncate`, `fullName` từ `ContractDocumentDAO`. Unit test tại `test/poscs/dao/ContractLinkDAOTest.java` (16/16 test cases pass 100%). QA report tại `docs/qa/Task-4.6.md`.
- [x] **Task 4.7:** `[UC-48, UC-49]` Tách `ContractHandoverDAO` (chặng bàn giao phòng ban).
  - *Đã hoàn thành:* Xây dựng `poscs.dao.ContractHandoverDAO` chuyên trách quản lý các chặng bàn giao phòng ban (UC-48, UC-49, bảng `contract_handovers`): khởi tạo bàn giao phòng ban (`handOverToDepartments`) ghi nhận `contract_history` (loại `EVENT_HANDOVER`) trong cùng giao dịch (NFR-19); hoàn tất bàn giao (`completeHandover`) chặn nghiệm thu trùng lặp và ghi nhận kiểm toán `contract_history` (loại `EVENT_ACCEPTANCE`); truy vấn chặng bàn giao của hợp đồng (`findHandoversOf`) và chặng đang chờ (`findPendingHandovers`); kiểm soát độ dài theo schema (`note` ≤ 255); tách nhỏ method tuân thủ tiêu chuẩn ≤ 40 dòng. Tích hợp ủy quyền vào `poscs.dao.ContractDAO` (`handoverDAO`, getter, DI constructor) duy trì 100% tương thích ngược. Unit test tại `test/poscs/dao/ContractHandoverDAOTest.java` (24/24 test cases pass 100%) và `test/poscs/dao/ContractDAOTest.java` (100/100 test cases pass 100%). QA report tại `docs/qa/Task-4.7.md`.
- [-] **Task 4.8:** `[UC-50, UC-51]` Bỏ - theo quyết định ngày 15/10 (`ChangeRequestDAO` và `ChangeRequestController` đã tồn tại độc lập tại `poscs.dao.ChangeRequestDAO` và `poscs.controller.ChangeRequestController`, không cần tách từ `ContractDAO`).
- [x] **Task 4.9:** `[UC-10, UC-11, UC-33, UC-34, UC-35, UC-39, UC-41, UC-44, UC-46]` `[BR-12, BR-16, BR-17, BR-18, BR-35..37]` Thu gọn `ContractDAO` còn CRUD hợp đồng gốc, hàng hóa, phụ lục và lọc/tìm kiếm.
  - *Đã hoàn thành:* Tái cấu trúc và thu gọn `poscs.dao.ContractDAO` tập trung vào CRUD hợp đồng gốc, hàng hóa (`contract_products`), phụ lục và lọc/tìm kiếm/phân trang (mặc định `DEFAULT_PAGE_SIZE = 10` theo BR-12); tách toàn bộ phương thức dài để mọi hàm ≤ 39 dòng; ủy quyền `computeStatus` sang `ContractValidator.computeStatus` (BR-17); giữ nguyên ranh giới giao dịch (NFR-19), xóa mềm `is_deleted` và các hàm ủy quyền sang 5 DAO con để bảo toàn 100% tương thích ngược. Unit test tại `test/poscs/dao/ContractDAOTest.java` (133/133 test cases pass 100%). QA report tại `docs/qa/Task-4.9.md`.
- [x] **Task 4.10:** `[UC-33, UC-34, UC-35, UC-39, UC-44, UC-46, UC-54]` `[BR-17, BR-35, BR-36, BR-37]` Xây dựng `ContractService`: transaction (NFR-19), Ký (Nháp → Đã ký, tự đóng dấu ngày ký, kiểm quyền qua `PermissionService`), huỷ bản ghi (lý do bắt buộc; Nháp: Admin/quản lý vùng; đã ký: **chỉ Admin**; chặn nếu có phụ lục), sửa theo tiến độ (Nháp sửa mọi ô; đã ký chỉ đổi Người phụ trách; thanh lý/chấm dứt không sửa), tích hợp method chữa sai sót HĐ đã ký (UC-54) từ `ContractDAO`.
  - *Đã hoàn thành:* Xây dựng `poscs.service.ContractService` kế thừa `BaseService`, quản lý trọn vẹn nghiệp vụ hợp đồng và ranh giới giao dịch (NFR-19): tự tính trạng thái trục lịch (BR-17); tạo hợp đồng gốc và phụ lục kiểm quyền, bắt buộc trường, đối tác đúng vai, thứ tự ngày, không nhập tay ngày ký, hạn mức giảm trừ phụ lục (UC-33, UC-46, BR-35, BR-36); sửa hợp đồng theo tiến độ và đổi người phụ trách (UC-35); ký hợp đồng và chuyển tiến độ (UC-44); huỷ bản ghi xóa mềm có lý do và phân quyền (UC-34, BR-37); chữa sai sót HĐ đã ký (UC-54); thêm/xoá hàng hóa ở Nháp (UC-39); kiểm soát độ dài và người phụ trách còn hoạt động. File tạo mới: `src/java/poscs/service/ContractService.java`. Unit test: `test/poscs/service/ContractServiceTest.java` (32/32 test cases pass 100%). QA report tại `docs/qa/Task-4.10.md`.
- [x] **Task 4.11:** `[UC-48, UC-49, UC-53]` `[BR-18]` Xây dựng `ContractHandoverService` và `ContractPaymentService`.
  - *Đã hoàn thành:* Xây dựng `poscs.service.ContractHandoverService` (UC-48, UC-49: khởi tạo bàn giao phòng ban, hoàn tất chặng bàn giao có ghi chú bắt buộc, kiểm tra quyền theo ma trận SPEC / MSG-022, chặn khi HĐ đóng băng hoặc phòng đang giữ chặng pending, đối chiếu phòng ban thực tế của chặng để chặn spoofing, tổng hợp hàng đợi bàn giao) và `poscs.service.ContractPaymentService` (UC-53, BR-18: lập kỳ thanh toán, ghi nhận đã thu với ngày mặc định hôm nay hoặc ngày tùy chọn, chặn xóa kỳ thanh toán đã thu tiền `payment_already_paid`, định dạng tiền tệ VNĐ và ngày tháng BR-18). File tạo mới: `src/java/poscs/service/ContractHandoverService.java`, `src/java/poscs/service/ContractPaymentService.java`. Unit tests: `test/poscs/service/ContractHandoverServiceTest.java` (17/17 tests pass), `test/poscs/service/ContractPaymentServiceTest.java` (18/18 tests pass). QA report tại `docs/qa/Task-4.11.md`.
- [x] **Task 4.12:** `[UC-33, UC-34, UC-35, UC-44, UC-46, UC-54]` `ContractActionController` (Tạo, Sửa, Ký, Huỷ bản ghi, Phụ lục, Chữa sai sót UC-54 gọi về `ContractService`).
  - *Đã hoàn thành:* Xây dựng `poscs.controller.ContractActionController` chuyên trách xử lý các hành động thay đổi dữ liệu hợp đồng: Tạo hợp đồng (UC-33), Sửa thông tin & đổi Người phụ trách (UC-35), Ký & chuyển tiến độ (UC-44), Lập phụ lục (UC-46), Chữa sai sót HĐ đã ký (UC-54), Huỷ bản ghi có lý do (UC-34), Thêm/Xoá hàng hóa ở Nháp (UC-39). Tách các lớp hỗ trợ `ContractActionResultHelper` (ánh xạ mã lỗi và điều hướng) và `ContractControllerHelper` (bóc tách tham số form, số lượng, ngày tháng, nạp dữ liệu workspace); bảo đảm giới hạn ≤ 250 dòng/file và ≤ 40 dòng/phương thức. File tạo mới: `src/java/poscs/controller/ContractActionController.java`, `src/java/poscs/controller/ContractActionResultHelper.java`, `src/java/poscs/controller/ContractControllerHelper.java`. Unit tests: `test/poscs/controller/ContractActionControllerTest.java` (61/61 tests pass 100%). QA report tại `docs/qa/Task-4.12.md`.
- [x] **Task 4.13:** `[UC-10, UC-11, UC-45]` `[BR-12, BR-14, BR-16, BR-18]` `ContractListController` và `ContractDetailController`.
  - Tách `ContractListController` (UC-10: danh sách, phân trang, bộ lọc) và `ContractDetailController` (UC-11, UC-45: chi tiết hợp đồng, nhật ký, workspace).
  - Tách `ContractFilterHelper` xử lý lọc, chu kỳ thời gian và dải chip trạng thái.
  - Tinh gọn `ContractController` và `ContractControllerHelper`.
  - Bổ sung unit tests cho `ContractListControllerTest` và `ContractDetailControllerTest`.
- [x] **Task 4.14:** `[UC-48, UC-49]` `ContractHandoverController`.
  - Xây dựng `ContractHandoverController` và `ContractHandoverHelper` xử lý hàng đợi bàn giao (UC-48) và hoàn tất chặng (UC-49) qua `ContractHandoverService`.
  - Bổ sung `ContractHandoverControllerTest` (25 unit tests bao phủ các nhánh và phân quyền RBAC).
  - Hoàn thiện và cập nhật `docs/qa/Task-4.14.md`.
- [x] **Task 4.15:** `[UC-53]` `ContractPaymentController`.
  - Xây dựng `ContractPaymentController` xử lý lập kỳ thanh toán, ghi nhận đã thu, xoá kỳ (UC-53, BR-18) qua `ContractPaymentService`.
  - Tinh gọn `ContractController` ủy quyền các handler thanh toán sang `ContractPaymentController`.
  - Bổ sung `ContractPaymentControllerTest` (36 unit tests) và cập nhật `ContractControllerTest` (96 tests pass 100%).
  - Hoàn thiện và cập nhật `docs/qa/Task-4.15.md`.
- [x] **Task 4.16:** `[UC-52]` `ContractDocumentController`.
  - Xây dựng `ContractDocumentController` và `ContractDocumentService` xử lý gắn đường dẫn tài liệu và huỷ mềm có lý do bắt buộc (UC-52), phân quyền RBAC (Admin, Sales quản lý vùng), phòng chống IDOR tài liệu giữa các hợp đồng.
  - Tinh gọn `ContractController` ủy quyền các handler tài liệu sang `ContractDocumentController`.
  - Bổ sung `ContractDocumentControllerTest` (27 unit tests), `ContractDocumentServiceTest` (37 unit tests) và cập nhật `ContractControllerTest` (99 tests pass 100%).
  - Hoàn thiện và cập nhật `docs/qa/Task-4.16.md`.
- [x] **Task 4.17:** `[UC-41]` `ContractExportController` (Excel, cùng bộ lọc, không phân trang, sắp theo tỉnh/thành).
  - Xây dựng `ContractExportController` và `ContractExportService` xử lý xuất danh sách hợp đồng ra Excel (UC-41, BR-16), cùng bộ lọc, không phân trang, sắp theo tỉnh/thành, tự động bỏ lọc tỉnh đối với hợp đồng Mua, phân quyền RBAC (Admin, Sales, Kỹ thuật).
  - Tinh gọn `ContractController` ủy quyền `exportExcel` sang `ContractExportController`.
  - Bổ sung `ContractExportControllerTest` (12 unit tests), `ContractExportServiceTest` (14 unit tests) và cập nhật `ContractControllerTest` (101 tests pass 100%).
  - Hoàn thiện và cập nhật `docs/qa/Task-4.17.md`.
- [x] **Task 4.18:** `[UC-47]` Xây dựng `ContractLinkController` cho liên kết hợp đồng Bán ↔ Mua (và gỡ liên kết). Phần ChangeRequest (UC-50, UC-51) giữ nguyên `ChangeRequestController` đã có.
  - Xây dựng `ContractLinkController` và `ContractLinkService` xử lý liên kết Bán ↔ Mua và gỡ liên kết (UC-47), phân quyền RBAC (Admin, Sales quản lý vùng), tự động phân định chiều, chặn HĐ phụ lục/cùng chiều/tự nối, phòng chống IDOR khi gỡ liên kết.
  - Tinh gọn `ContractController` ủy quyền `handleLinkContract` và `handleUnlinkContract` sang `ContractLinkController`.
  - Bổ sung `ContractLinkControllerTest` (24 unit tests), `ContractLinkServiceTest` (37 unit tests) và cập nhật `ContractControllerTest` (105 tests pass 100%).
  - Hoàn thiện và cập nhật `docs/qa/Task-4.18.md`.
- [x] **Task 4.19:** `[UC-54]` Kiểm thử luồng chữa sai sót hợp đồng đã ký (chỉ Admin, bắt buộc lý do, ghi nhật ký sự kiện riêng `EVENT_CORRECTED`, khóa ngày ký) trên `ContractService` (Task 4.10) và `ContractActionController` (Task 4.12); không tạo controller riêng.
  - *Đã hoàn thành:* Kiểm thử và chuẩn hóa trọn vẹn luồng chữa sai sót hợp đồng đã ký (UC-54, bảng `contracts`, `contract_history`): chỉ Admin được phép thực hiện, bắt buộc lý do chữa sai sót, ghi nhận nhật ký kiểm toán với loại sự kiện riêng `EVENT_CORRECTED`, khóa cố định ngày ký, kiểm soát trần giá trị hợp đồng khi có phụ lục giảm trừ đã ký; phân quyền tầng Controller sử dụng `PermissionService.canCorrectSignedContract(actor)`. File chỉnh sửa: `src/java/poscs/service/ContractService.java`, `src/java/poscs/controller/ContractActionController.java`, `src/java/poscs/controller/ContractActionResultHelper.java`, `src/java/poscs/controller/ContractControllerHelper.java`. Unit tests bổ sung/cập nhật: `test/poscs/service/ContractServiceTest.java` (41 tests pass), `test/poscs/controller/ContractActionControllerTest.java` (62 tests pass). QA report tại `docs/qa/Task-4.19.md`.

---

## Giai đoạn 5: Module Phiếu hỗ trợ kỹ thuật

> **BR-44 (thiết bị trong phiếu) đã chốt KHÔNG làm.** Phiếu gắn với hợp đồng; thiết bị/lỗi ghi ở Mô tả/ghi chú. Không tạo `DeviceDAO`, không làm UI thiết bị.

- [x] **Task 5.1:** `[UC-21, UC-22]` `[BR-39, BR-41, BR-43]` Xây dựng `TicketValidator`: bắt buộc Khách hàng, Loại phiếu, Mức ưu tiên, Kênh tiếp nhận, Mô tả sự cố, Kỹ thuật viên; ưu tiên ∈ {Thấp, Bình thường, Cao, Khẩn cấp}; cờ bảo hành Có/Không mặc định Có. **Trạng thái và Ngày tạo do hệ thống gán**, không phải trường nhập. Unit Test.
  - *Đã hoàn thành:* Xây dựng `poscs.validator.TicketValidator` kế thừa `BaseValidator` kiểm tra ràng buộc BR-39 (bắt buộc Khách hàng, Loại phiếu, Mức ưu tiên, Kênh tiếp nhận, Mô tả sự cố, Kỹ thuật viên; trạng thái & ngày tạo do hệ thống gán; chặn ngày tạo tương lai MSG-019); BR-41 (ưu tiên hợp lệ ∈ {Thấp, Bình thường, Cao, Khẩn cấp}); BR-43 (cờ bảo hành Có/Không, mặc định Có); BR-40 (luồng chuyển trạng thái hợp lệ Mới tiếp nhận -> Đang xử lý -> Đã đóng, vi phạm trả MSG-051); BR-47 (chặn xóa phiếu Đang xử lý); kiểm tra vai Khách mua và hợp đồng thuộc khách hàng. File tạo mới: `src/java/poscs/validator/TicketValidator.java`. Unit tests: `test/poscs/validator/TicketValidatorTest.java` (29/29 tests pass 100%). QA report tại `docs/qa/Task-5.1.md`.
- [x] **Task 5.2:** `[UC-16, UC-17, UC-21, UC-22, UC-23]` `[BR-38..43, BR-47]` Xây dựng `TicketService`: sinh mã ticket duy nhất, chuyển trạng thái *Mới tiếp nhận → Đang xử lý → Đã đóng* (sai quy trình → MSG-051), ghi thời điểm hoàn tất lần đầu Đã đóng, SLA quá hạn, lịch sử trạng thái trong **một giao dịch**, Kỹ thuật chỉ sửa phiếu được giao, xóa mềm chỉ khi *Mới tiếp nhận* hoặc *Đã đóng*.
  - *Đã hoàn thành:* Xây dựng `poscs.service.TicketService` quản lý nghiệp vụ phiếu hỗ trợ kỹ thuật: sinh mã ticket duy nhất `REQ-YYYY-XXX` (retry khi trùng mã, BR-38), tạo phiếu với trạng thái mặc định *Mới tiếp nhận*, ngày tạo hiện tại, bảo hành mặc định *Có* và ghi lịch sử + audit trong 1 giao dịch (BR-39, BR-43); cập nhật phiếu theo quyền vai trò (Admin/CSKH cập nhật đầy đủ; Kỹ thuật chỉ sửa trạng thái, nguyên nhân lỗi, kết quả xử lý trên phiếu được giao; kiểm tra kỹ thuật viên tồn tại, đang hoạt động và đúng vai trò Kỹ thuật; kiểm tra chuyển trạng thái hợp lệ BR-40 -> MSG-051, ghi `completed_at` lần đầu sang *Đã đóng* BR-40, ghi lịch sử trạng thái trong cùng giao dịch BR-42); xóa mềm chỉ khi *Mới tiếp nhận* hoặc *Đã đóng* (BR-47); truy vấn chi tiết/danh sách/KPI/dữ liệu form hỗ trợ. File tạo mới: `src/java/poscs/service/TicketService.java`. Unit tests: `test/poscs/service/TicketServiceTest.java` (28/28 tests pass 100%). QA report tại `docs/qa/Task-5.2.md`.
- [x] **Task 5.3:** `[UC-16, UC-17]` `[BR-12, BR-14, BR-45, BR-46]` `TicketListController` (dải KPI, bộ lọc, mặc định "Phiếu của tôi" cho Kỹ thuật viên) và `TicketDetailController`.
  - *Đã hoàn thành:* Xây dựng `poscs.controller.TicketListController` (UC-16, BR-12, BR-14, BR-45: phân trang 10 dòng/trang, chuẩn hóa `page`, kẹp biên sang trang cuối khi vượt tổng trang, bộ lọc Năm/Kỳ/từ khóa/trạng thái/ưu tiên/kỹ thuật viên, mặc định lọc "Phiếu của tôi" cho Kỹ thuật viên và cho phép chọn "Tất cả" qua `technicianId=all` hoặc `0`, tính dải KPI theo bộ lọc thời gian); xây dựng `poscs.controller.TicketDetailController` (UC-17, BR-46, BR-47: hiển thị chi tiết phiếu kèm lịch sử trạng thái, thông tin khách hàng, hợp đồng, kỹ thuật viên, tính toán cờ quyền `canEdit`, `isTechLimitedEdit`, `canDelete`). Cập nhật `poscs.controller.TechnicalSupportTicketController` ủy quyền `showList` và `showDetail` sang 2 controller mới (tương thích ngược 100%). File tạo/sửa: `src/java/poscs/controller/TicketListController.java`, `src/java/poscs/controller/TicketDetailController.java`, `src/java/poscs/controller/TechnicalSupportTicketController.java`. Unit tests: `test/poscs/controller/TicketListControllerTest.java` (12 tests), `test/poscs/controller/TicketDetailControllerTest.java` (10 tests). QA report tại `docs/qa/Task-5.3.md`.
- [x] **Task 5.4:** `[UC-21, UC-22, UC-23]` `[BR-47]` `TicketActionController` (Tạo, Cập nhật, Xóa).
  - *Đã hoàn thành:* Xây dựng `poscs.controller.TicketActionController` (UC-21, UC-22, UC-23, BR-47: hiển thị form tạo mới, tạo phiếu hỗ trợ kỹ thuật, hiển thị form sửa phiếu với cờ quyền `canManage` và `lockGeneral`, cập nhật phiếu theo vai trò, xóa mềm phiếu `Mới tiếp nhận` hoặc `Đã đóng`, chuyển hướng về `/ticket?error=notfound` khi xóa phiếu không tồn tại) và helper `poscs.controller.TicketActionHelper` phụ trách bóc tách form, gán dropdowns và ánh xạ mã lỗi redirect/forward. Bảo đảm giới hạn ≤ 250 dòng/file và ≤ 40 dòng/phương thức. File tạo mới: `src/java/poscs/controller/TicketActionController.java`, `src/java/poscs/controller/TicketActionHelper.java`. Unit tests: `test/poscs/controller/TicketActionControllerTest.java` (29/29 tests pass 100%). QA report tại `docs/qa/Task-5.4.md`.
- [x] **Task 5.5:** `[UC-42]` `TicketExportController` (xuất **Excel hoặc PDF**).
  - *Đã hoàn thành:* Xây dựng `poscs.controller.TicketExportController` và `poscs.service.TicketExportService` xử lý xuất danh sách phiếu hỗ trợ kỹ thuật ra Excel (đầy đủ 18 cột BR-45, giữ nguyên bộ lọc, không phân trang, KTV mặc định lọc phiếu của tôi) và xuất chi tiết 1 phiếu ra PDF sử dụng Noto Sans font, tự động phân trang theo chiều cao trang và ngắt dòng văn bản. Phân quyền RBAC qua `PermissionService.canExportTickets(actor)` (Admin, Sales, Kỹ thuật), chặn IDOR và xử lý lỗi không tìm thấy phiếu (`/ticket?error=notfound`). `TechnicalSupportTicketController` ủy quyền `exportExcel` và `exportPdf` sang controller mới (tương thích ngược 100%). File tạo/sửa: `src/java/poscs/controller/TicketExportController.java`, `src/java/poscs/service/TicketExportService.java`, `src/java/poscs/controller/TechnicalSupportTicketController.java`. Unit tests: `test/poscs/controller/TicketExportControllerTest.java` (10 tests), `test/poscs/service/TicketExportServiceTest.java` (15 tests) pass 100%. QA report tại `docs/qa/Task-5.5.md`.

---

## Giai đoạn 6: Nhân viên, Thông báo, Tổng quan, Nhật ký

- [x] **Task 6.1:** `[UC-24..29]` `[BR-09, BR-10, BR-25..30]` Xây dựng `EmployeeValidator` và `EmployeeService`: bắt buộc Họ, Tên, Phòng ban, Vai trò, Ngày vào làm, Email cá nhân; SĐT/CCCD duy nhất (MSG-033); ô Vai trò không có Admin, không thăng lên Admin; địa bàn mỗi tỉnh một người phụ trách; khóa/mở khóa không tự khóa mình; gửi tài khoản chỉ ghi đè mật khẩu **sau khi email gửi thành công**.
  - *Đã hoàn thành:* Xây dựng `poscs.validator.EmployeeValidator` (kiểm tra bắt buộc BR-28, SĐT BR-09, email chuẩn RFC BR-10, ngày sinh quá khứ BR-29, giới tính BR-30, chặn vai Admin, kiểm tra trùng địa bàn) và `poscs.service.EmployeeService` (phân quyền Admin fail-closed MSG-022, tạo nhân viên kèm băm BCrypt mật khẩu tạm UC-26, cập nhật nhân viên giữ managerId UC-27, gửi tài khoản qua email UC-28 chỉ ghi đè mật khẩu sau khi gửi mail thành công, khóa/mở khóa UC-29 chặn tự khóa chính mình). File tạo mới: `src/java/poscs/validator/EmployeeValidator.java`, `src/java/poscs/service/EmployeeService.java`. Unit tests: `test/poscs/validator/EmployeeValidatorTest.java` (28 tests), `test/poscs/service/EmployeeServiceTest.java` (35 tests). QA report tại `docs/qa/Task-6.1.md`.
  - Dùng bảng phân công địa bàn **đã có trong DB** (xem tên trong `db/schema.sql`), không tạo bảng mới.
- [x] **Task 6.2:** `[UC-24..29]` `[BR-12, BR-14, BR-22..24, BR-26]` Refactor `EmployeeController` và tinh gọn `EmployeeDAO`.
  - *Đã hoàn thành:* Refactor `poscs.controller.EmployeeController` về tầng Presentation thuần (248 dòng, mọi phương thức ≤ 24 dòng), kiểm quyền tập trung qua `AccessControl` và `PermissionService.canManageEmployees`, ủy quyền toàn bộ nghiệp vụ/giao dịch sang `EmployeeService`, hỗ trợ phân tích ngày chặt chẽ `dd/MM/uuuu` (`ResolverStyle.STRICT`, BR-29) và ISO date; tinh gọn `poscs.dao.EmployeeDAO` bằng cách tách các hàm mapper và helper dùng chung (`mapAuthUser`, `mapProfileUser`, `readAddress`, `bindUpdateProfile`, `collectKeepIds`, `buildFindActiveByRoleSql`, `mapActiveUserSummary`, `bindInsertUser`, `bindUpdateUser`, `deleteProvincesOf`, `insertProvincesOf`, `finalizeProvinceTx`, `mapProvinceAssignment`) bảo toàn 100% chữ ký public API và giao dịch rollback. File sửa: `src/java/poscs/controller/EmployeeController.java`, `src/java/poscs/dao/EmployeeDAO.java`. Unit tests: `test/poscs/controller/EmployeeControllerTest.java` (48 tests pass), `test/poscs/dao/EmployeeDAOTest.java` (29 tests pass). QA report tại `docs/qa/Task-6.2.md`.
- [x] **Task 6.3:** `[UC-14, UC-15]` `[BR-20, BR-21]` Xây dựng `NotificationService` và chuẩn hóa `NotificationScheduler`: chu kỳ **60 phút**, chỉ 2 loại tự sinh (HĐ sắp hết hạn → người phụ trách; phiếu sắp/đã quá hạn SLA → KTV được giao), mỗi cặp một thông báo, chạy lại không tạo trùng.
  - Ngưỡng: phiếu báo trước **24 giờ** so với hạn SLA; hợp đồng **30 ngày** (BR-17).
  - *Đã hoàn thành:* Xây dựng `src/java/poscs/service/NotificationService.java` (chỉ tự sinh đúng 2 loại thông báo `contract_expiring` ≤ 30 ngày dùng chung `ContractValidator.SOON_THRESHOLD_DAYS` và `ticket_sla` ≤ 24 giờ, idempotent theo cặp người nhận/đối tượng, `synchronized generateScheduledNotifications`, kiểm tra null/hợp lệ cho `UC-14`/`UC-15`/`BR-20`/`BR-21`); chuẩn hóa `src/java/poscs/common/NotificationScheduler.java` (chu kỳ 60 phút, ủy quyền sang `NotificationService`). Unit tests: `test/poscs/service/NotificationServiceTest.java` (24 tests pass), `test/poscs/common/NotificationSchedulerTest.java` (3 tests pass). QA report tại `docs/qa/Task-6.3.md`.
- [x] **Task 6.4:** `[UC-36]` `[BR-15, BR-18]` `DashboardService` + tinh gọn `DashboardController`: số liệu tổng hợp, doanh thu lấy từ `contract_payments`, lọc Năm/Kỳ (năm không hợp lệ/trống thì không lọc).
  - *Đã hoàn thành:* Xây dựng `src/java/poscs/service/DashboardService.java` (tổng hợp số liệu dashboard, tính doanh thu từ `contract_payments` qua `ContractDAO`/`ContractPaymentDAO`, tính tăng trưởng an toàn chia cho 0, lọc Năm/Kỳ theo BR-15 hủy lọc khi năm trống/sai định dạng, phân quyền phạm vi xem Sales/Admin, lọc địa bàn khử trùng lặp và null-safe); tinh gọn `src/java/poscs/controller/DashboardController.java` về tầng Presentation thuần (86 dòng, các hàm ≤ 22 dòng) giữ nguyên tương thích ngược với mock field của test cũ. Unit tests: `test/poscs/service/DashboardServiceTest.java` (11 tests pass), `test/poscs/controller/DashboardControllerTest.java` (48 tests pass). QA report tại `docs/qa/Task-6.4.md`.
- [x] **Task 6.5:** `[UC-43]` `[NFR-17]` Rà `SystemLogController`: chỉ Admin, đọc tối đa 512 KB cuối tệp log mỗi lần; bổ sung test nếu thiếu.
  - *Đã hoàn thành:* Rà soát phân quyền Admin tập trung cho mọi hành động (xem và tải tệp, chặn Sales/Kỹ thuật/chưa đăng nhập với HTTP 403 / MSG-022); xác thực giới hạn đọc tối đa 512 KB cuối tệp của `LogFiles.tail` theo đúng NFR-17; xác thực an toàn lọc từ khóa, kẹp số dòng và chống Path Traversal; bổ sung unit test toàn diện cho các kịch bản đúng và vi phạm. File sửa: `src/java/poscs/common/LogFiles.java`. Unit tests: `test/poscs/common/LogFilesTest.java` (16 tests pass), `test/poscs/controller/SystemLogControllerTest.java` (22 tests pass). QA report tại `docs/qa/Task-6.5.md`.

---

## Giai đoạn 7: Kiểm thử Tổng thể & Hoàn thiện

- [x] **Task 7.1:** Chạy toàn bộ `ant test`, đảm bảo 100% pass. *Lưu ý:* test tích hợp bị **skipped** mặc định, nên "pass" ở đây chưa kiểm tra DB thật; việc đó thuộc Task 7.2.
  - *Đã hoàn thành:* Chạy toàn bộ bộ kiểm thử `ant test` và `ant compile-jsps`, đạt 100% pass (88 test suites, 1.962 tests pass, 0 fail, 0 error, 3 integration test suites skipped mặc định). Không phát sinh lỗi cần sửa. QA report tại `docs/qa/Task-7.1.md`.
- [x] **Task 7.2:** Chạy integration test (`ContractStatusIntegrationTest`, `DaoSchemaIntegrationTest`, `WriteAndTransactionIntegrationTest` trong `test/poscs/integration/`) trên CSDL dùng-một-lần `poscs_it` để xác nhận toàn vẹn dữ liệu và transaction sau khi tách DAO/Service.
  - Lệnh: `ant -Dtest-sys-prop.DB_URL=jdbc:mysql://localhost:3306/poscs_it -Dtest-sys-prop.DB_USER=root -Dtest-sys-prop.DB_PASSWORD=<do người dùng nhập> test` (dùng đường dẫn đầy đủ tới `ant.bat`).
  - **Mỗi lượt chạy xóa sạch dữ liệu của `poscs_it`.** Code chặn nếu tên DB không kết thúc bằng `_it`.
  - **Agent không tự tạo DB, không tự điền hay lưu mật khẩu, không trỏ vào DB khác.** Nếu `poscs_it` chưa có hoặc chưa có mật khẩu: dừng và báo người dùng. Cách tạo DB xem `README.md`.
  - *Đã hoàn thành:* Chạy 3 bộ kiểm thử tích hợp (`ContractStatusIntegrationTest`: 37 tests, `DaoSchemaIntegrationTest`: 10 tests, `WriteAndTransactionIntegrationTest`: 16 tests; tổng 63/63 tests pass, 0 skipped) trên CSDL `poscs_it` cùng toàn bộ `ant test` (90 suites, 2.090/2.090 tests pass, 0 fail, 0 error, 0 skipped). Không phát sinh lỗi cần sửa. QA report tại `docs/qa/Task-7.2.md`.
- [x] **Task 7.2a:** Loại bỏ mật khẩu DB mặc định hardcode (`"1234"`) theo kết quả QA `docs/qa/Task-7.2.md`: đổi mặc định `DB_PASSWORD` trong `src/java/poscs/dao/DBContext.java` thành chuỗi rỗng `""` (giữ `setInitializationFailTimeout(-1)`, mặc định `DB_URL`/`DB_USER`, không log/in mật khẩu); cập nhật placeholder mật khẩu trong Javadoc `test/poscs/integration/IntegrationDb.java` và `README.md`.
  - *Đã hoàn thành:* Đổi mặc định `DB_PASSWORD` trong `src/java/poscs/dao/DBContext.java` thành `""`, cập nhật comment và tài liệu hướng dẫn trong `test/poscs/integration/IntegrationDb.java`, `README.md`. Toàn bộ `ant test` đạt 100% pass (3 suite tích hợp skipped mặc định). QA report tại `docs/qa/Task-7.2a.md`.
- [x] **Task 7.3:** Cập nhật **`docs/ARCHITECTURE.md`** (không sửa `docs/SPEC.md`) mô tả cấu trúc mới: `service`, `validator`, các DAO/controller đã tách.
  - *Đã hoàn thành:* Tạo mới `docs/ARCHITECTURE.md` với sơ đồ Mermaid và mô tả chi tiết các tầng Presentation (`poscs.controller`), `poscs.service`, `poscs.validator`, `poscs.dao`, `poscs.model`, `poscs.common` theo `.gemini/rules.md` §4. QA report tại `docs/qa/Task-7.3.md`.
- [x] **Task 7.4:** Đối chiếu `docs/SPEC.md` với code, `db/schema.sql`, `PERMISSIONS.md`; **chỉ liệt kê sai lệch cho người dùng**, không tự sửa SPEC.
  - *Đã hoàn thành:* Kiểm toán toàn diện hệ thống và giải quyết dứt điểm 12 điểm sai lệch (`D-01` → `D-12`) theo chỉ định của người dùng: bổ sung `<session-timeout>30</session-timeout>` trong `web/WEB-INF/web.xml` (NFR-20); chuẩn hóa thông điệp HTTP 403 (`MSG-022`) trong `src/java/poscs/common/AccessControl.java` và `web/error403.jsp`; bổ sung Javadoc trên `PermissionService.isContractInScope`; đồng bộ tài liệu `docs/SPEC.md` (§2, §4.5, §5.7, §7 đủ 29 bảng, §11), `PERMISSIONS.md`, `README.md` và comment `db/schema.sql`. QA report tại `docs/qa/Task-7.4.md`.
- [x] **Task 7.4a:** Đồng bộ câu báo lỗi 403 về MSG-022 theo QA D-10 (`docs/qa/Task-7.4.md`).
  - UC: không có (áp cho mọi trang báo vượt thẩm quyền).
  - BR / MSG / NFR: `MSG-022` = "Bạn không có quyền truy cập nội dung này." (SPEC §9; §3 ghi chú cuối bảng quyền; §5.6), `NFR-10`.
  - File sửa: `src/java/poscs/controller/TechnicalSupportTicketController.java` (2 chỗ `sendError` 403 dùng hằng có sẵn `AccessControl.MSG_022`, không đổi điều kiện); `web/jsp/guide/employee.jsp`, `web/jsp/guide/ticket.jsp` (bảng "Thông báo lỗi thường gặp": đổi ô câu cũ thành câu MSG-022, giữ cột nguyên nhân và thẻ HTML); `tools/guide/word/export_word.py` (thêm 1 dòng cuối `CHANGES`); `docs/Report6_HuongDanSuDung.docx` (xuất lại).
  - File test: `test/poscs/controller/TechnicalSupportTicketControllerTest.java`: thêm test mới cho 2 nhánh (mở form sửa và cập nhật phiếu không được giao), khẳng định `sendError(SC_FORBIDDEN, AccessControl.MSG_022)`; không sửa test cũ.
  - Không đụng: `docs/SPEC.md`, `PERMISSIONS.md`, `README.md`, `web/error403.jsp`, `AccessControl.java`, `PermissionService.java`; các câu 403 riêng ở `ChangeRequestController`, `ContractActionController`, `AuthenticationFilter` (CSRF). Không gộp hai hằng `MSG_022`.
  - Lưu ý: `web/error403.jsp` không hiện message của `sendError`, người dùng luôn thấy câu viết cứng trong JSP, nên hướng dẫn phải ghi đúng câu đó. Trước khi xuất lại docx: `lastModifiedBy` trong `docProps/core.xml` phải là `tools/guide/word/export_word.py`, khác thì dừng hỏi. Lệnh: `python tools/guide/word/export_word.py --template "C:\Users\Admin\Documents\Backup\Prj\Ref\Report6_Software User Guides.docx" --out "docs\Report6_HuongDanSuDung.docx"`. Thiếu Python/thư viện thì dừng, không tự cài.
  - Xong khi: không còn câu "Bạn không có quyền thực hiện thao tác này." trong `src/` và `web/`; docx có câu MSG-022 ở 2 bảng lỗi và dòng Task 7.4a trong Lịch sử thay đổi; `ant compile-jsps` và `ant test` pass.
  - Nhánh: `fix/task-7.4a-msg-022`.
  - Commit: `fix(guide): đồng bộ thông báo 403 về MSG-022 ở phiếu hỗ trợ và hướng dẫn sử dụng (Task 7.4a)`
  - *Đã hoàn thành:* 2 chỗ `sendError` 403 trong `src/java/poscs/controller/TechnicalSupportTicketController.java` dùng `AccessControl.MSG_022`; bảng "Thông báo lỗi thường gặp" ở `web/jsp/guide/employee.jsp`, `web/jsp/guide/ticket.jsp` ghi đúng câu MSG-022; thêm dòng Task 7.4a vào `CHANGES` của `tools/guide/word/export_word.py` và xuất lại `docs/Report6_HuongDanSuDung.docx`. Unit tests: thêm 2 test vào `test/poscs/controller/TechnicalSupportTicketControllerTest.java` (67/67 pass). `ant test` 1.964 pass / 0 fail / 3 skipped, `ant compile-jsps` pass. QA report tại `docs/qa/Task-7.4a.md`.

---

## Giai đoạn 8: Người ký bên mình lấy từ Thông tin chi nhánh

> Yêu cầu người dùng 19/11/2026: mục "Thông tin ký kết" của form hợp đồng thôi bắt gõ người ký bên mình, mà lấy sẵn thông tin Giám đốc chi nhánh (họ tên, chức vụ, SĐT, fax). Bổ sung cùng ngày: người phụ trách hợp đồng lấy theo người tạo (Task 8.5). Các điểm đã chốt: mục 9, 10, 11 ở "Quyết định đã chốt". Làm **theo thứ tự 8.1 → 8.6**: task sau dùng mã UC/BR/MSG và bảng/cột do task trước tạo.

- [x] **Task 8.1:** Bổ sung đặc tả "Thông tin chi nhánh" và người ký bên mình vào `docs/SPEC.md`.
  - UC: thêm mới `UC-55` (Cập nhật thông tin chi nhánh, chỉ Admin); sửa mô tả liên quan ở `UC-33`, `UC-35`, `UC-46`, `UC-54`.
  - BR / MSG / NFR: thêm `BR-56`, `BR-57`, `MSG-057` → `MSG-060`, ghi **đúng nội dung dưới đây**, không tự đặt câu khác:
    - `BR-56`: Người ký bên mình (họ tên, chức vụ, SĐT, fax) luôn lấy từ Thông tin chi nhánh, không sửa tay trên form hợp đồng. Hệ thống chép vào hợp đồng khi tạo (UC-33, UC-46) và mỗi lần lưu khi hợp đồng còn Nháp (UC-35). Từ Đã ký trở đi giữ nguyên; UC-54 không đổi bốn ô này. Chưa khai thông tin chi nhánh (họ tên GĐCN còn trống): tạo mới thì để trống cả bốn ô, bản Nháp giữ giá trị đang có.
    - `BR-57`: Thông tin chi nhánh: họ tên và chức vụ GĐCN bắt buộc, tối đa 100 ký tự. SĐT và fax không bắt buộc; nếu nhập thì tối đa 20 ký tự, phải có 8–15 chữ số, ngoài chữ số chỉ cho phép dấu `+` ở đầu, khoảng trắng, dấu chấm, gạch ngang, ngoặc đơn (nhận cả số máy bàn, nên không áp BR-09).
    - `MSG-057` (Toast) Lưu thông tin chi nhánh thành công: "Đã lưu thông tin chi nhánh."
    - `MSG-058` (Nội tuyến) Thiếu họ tên hoặc chức vụ GĐCN: "Vui lòng nhập họ tên và chức vụ Giám đốc chi nhánh."
    - `MSG-059` (Nội tuyến) Fax sai định dạng: "Số fax không hợp lệ." (SĐT sai định dạng dùng lại `MSG-015`.)
    - `MSG-060` (Nội tuyến, trên form tạo/sửa hợp đồng) Chưa khai thông tin chi nhánh: "Chưa có thông tin Giám đốc chi nhánh. Admin khai tại trang Thông tin chi nhánh."
  - File sửa: `docs/SPEC.md`:
    - §3.3: thêm dòng "Cập nhật thông tin chi nhánh (UC-55)", chỉ Admin.
    - §4: tiêu đề thành "UC-01 → UC-55"; thêm mục 4.8 "Thông tin chi nhánh (chỉ Admin)" gồm UC-55.
    - §5.4, đoạn "Tạo (UC-33)": đổi "người ký & chức vụ hai bên" thành "người ký bên mình do hệ thống tự điền (BR-56); người ký & chức vụ bên đối tác tuỳ chọn".
    - Thêm §5.8 "Thông tin chi nhánh (Admin)": một bản ghi duy nhất, chỉ xem và sửa, không tạo/xoá; ràng buộc BR-57; thông báo MSG-057, MSG-058, MSG-059, MSG-015; vai khác vào thì MSG-022.
    - §6: tiêu đề thành "BR-01 → BR-57", thêm hai dòng BR.
    - §7: thành "30 bảng"; nhóm "Tài khoản & tổ chức (8)" thêm `branch_info`.
    - §9: thêm bốn dòng MSG.
    - §11: thêm quyết định 12, tóm tắt mục 9 và 10 "Quyết định đã chốt" của file này.
  - File test: không có (chỉ sửa tài liệu). Vẫn chạy `ant test` theo tiêu chí chung.
  - Không đụng: code, `db/`, `README.md`, `PERMISSIONS.md` (sửa ở Task 8.3 cùng lúc với code chặn quyền, để file đó không ghi một quyền chưa có code).
  - Lưu ý: trước khi ghi, `grep` cả repo để chắc `UC-55`, `BR-56`, `BR-57`, `MSG-057` → `MSG-060` chưa ai dùng; trùng thì dừng hỏi.
  - Xong khi: SPEC có đủ các mục trên; mọi số đếm trong tiêu đề (số UC, số BR, số bảng) khớp nội dung.
  - Nhánh: `docs/task-8.1-spec-thong-tin-chi-nhanh`.
  - Commit: `docs(spec): đặc tả thông tin chi nhánh và người ký bên mình (Task 8.1)`
  - *Đã hoàn thành:* `docs/SPEC.md` thêm UC-55 (§3.3, §4 tiêu đề "UC-01 → UC-55", mục 4.8), §5.4 sửa đoạn "Tạo (UC-33)", "Cập nhật (UC-35)" và thêm gạch đầu dòng "Người ký bên mình (BR-56)", mục 5.8 "Thông tin chi nhánh (Admin)", BR-56 và BR-57 (§6 "BR-01 → BR-57"), §7 "30 bảng" thêm `branch_info`, MSG-057 → MSG-060 (§9), §11 quyết định 12. Theo QA đã chốt thêm: SĐT/fax tối đa 20 ký tự (quyết định 12), "chưa khai" = họ tên GĐCN còn trống (quyết định 10). Không có test mới; `ant test` 1.964 test, 0 fail, 3 skipped. QA report tại `docs/qa/Task-8.1.md`.
- [x] **Task 8.2:** Tạo bảng `branch_info`, thêm `signer_phone`/`signer_fax` vào `contracts`, kèm model và DAO.
  - UC: `UC-55` (dữ liệu); `UC-33`, `UC-35` (hai cột mới của hợp đồng).
  - BR / MSG / NFR: `BR-56`, `BR-57`.
  - File sửa:
    - `db/migrations/V39__add_branch_info_and_contract_signer_contact__ndat2003.sql` (mới): ghi dòng `schema_migrations` như các file V trước. Tạo `branch_info`: `branch_info_id` tinyint PK (một dòng duy nhất, id = 1), `director_name` varchar(100) NULL, `director_position` varchar(100) NULL, `phone` varchar(20) NULL, `fax` varchar(20) NULL, `updated_by` int NULL FK → `users.user_id`, `updated_at` timestamp tự cập nhật. Gieo sẵn dòng id = 1 với `director_position` = 'Giám đốc chi nhánh', các cột khác NULL. `ALTER TABLE contracts` thêm `signer_phone` varchar(20) NULL và `signer_fax` varchar(20) NULL ngay sau `signer_position`. Comment đầu file giải thích lý do, theo kiểu V27.
    - `db/schema.sql`: mirror đúng V39 (bảng mới đặt cạnh nhóm bảng tổ chức, hai cột mới trong `CREATE TABLE contracts`, dòng gieo `branch_info`).
    - `src/java/poscs/model/BranchInfo.java` (mới): 4 trường thông tin, cộng `updatedBy`, `updatedAt`.
    - `src/java/poscs/model/Contract.java`: thêm `signerPhone`, `signerFax` kèm getter/setter, đặt cạnh `signerName`/`signerPosition`.
    - `src/java/poscs/dao/BranchInfoDAO.java` (mới): `BranchInfo get()` (lỗi hoặc không có dòng thì trả `null`, ghi log như các DAO khác) và `boolean update(BranchInfo info, int actorId)` (UPDATE dòng id = 1, ghi `updated_by`). Được có DAO riêng vì miền này có mục menu, controller và model riêng (Task 8.3).
  - File test: `test/poscs/dao/BranchInfoDAOTest.java` (mới, dùng `JdbcStub` như các DAO test khác): đọc đủ cột; không có dòng → `null`; `SQLException` → `null` / `false`; `update` bind đúng thứ tự tham số và `actorId`.
  - Không đụng: `ContractDAO` và mọi chỗ đọc/ghi hợp đồng (Task 8.4); controller, JSP (Task 8.3, 8.4). Không đổi hai cột `signer_name`, `signer_position`.
  - Lưu ý: **agent không chạy V39 lên CSDL nào** (rules §3). Ghi vào report cho người dùng: trước khi kéo code Task 8.4 về checkout chính phải chạy V39 lên `poscs_db` (code mới + CSDL cũ thì lưu hợp đồng hỏng). `ant test` nạp `db/schema.sql` cho test tích hợp, nên bản mirror phải chạy được từ đầu.
  - Xong khi: V39 và `schema.sql` khớp nhau từng cột; `BranchInfoDAOTest` pass.
  - Nhánh: `feat/task-8.2-branch-info-schema`.
  - Commit: `feat(db): thêm bảng branch_info và SĐT, fax người ký bên mình (Task 8.2)`
  - *Đã hoàn thành:* `db/migrations/V39__add_branch_info_and_contract_signer_contact__ndat2003.sql` (mới: bảng `branch_info` một dòng id = 1 gieo sẵn chức vụ, `contracts.signer_phone`/`signer_fax` sau `signer_position`); `db/schema.sql` mirror V39 (bảng mới ngay sau `users`, khớp từng cột); `src/java/poscs/model/BranchInfo.java` (mới); `src/java/poscs/model/Contract.java` thêm `signerPhone`, `signerFax`; `src/java/poscs/dao/BranchInfoDAO.java` (mới: `get()`, `update(info, actorId)`). Test: `test/poscs/dao/BranchInfoDAOTest.java` 14 test; `ant test` 1.978 test, 0 fail, 3 skipped. **Chưa chạy V39 lên `poscs_db`**: phải chạy trước khi kéo code Task 8.4 về checkout chính. QA report tại `docs/qa/Task-8.2.md`.
- [x] **Task 8.3:** Trang "Thông tin chi nhánh" cho Admin (xem và sửa thông tin GĐCN).
  - UC: `UC-55`.
  - BR / MSG / NFR: `BR-57`, `MSG-057`, `MSG-058`, `MSG-059`, `MSG-015`, `MSG-022`.
  - File sửa:
    - `src/java/poscs/validator/BranchInfoValidator.java` (mới, kế thừa `BaseValidator`): kiểm theo BR-57, trim trước khi kiểm, trả lỗi theo từng ô.
    - `src/java/poscs/service/BranchInfoService.java` (mới, kế thừa `BaseService`): `get()`; `update(BranchInfo, User actor)` chạy validator rồi gọi `BranchInfoDAO.update`, trả `ServiceResult`.
    - `src/java/poscs/controller/BranchInfoController.java` (mới, `@WebServlet` `/branch`): GET hiện form, POST lưu. Chặn quyền một lần ở đầu `doGet`/`doPost` bằng `AccessControl.requireAdmin`, giống `EmployeeController`. Lưu xong chuyển về GET kèm MSG-057; lỗi kiểm tra thì hiện lại form, giữ dữ liệu vừa nhập.
    - `web/jsp/admin/branchInfo.jsp` (mới): 4 ô Họ tên GĐCN, Chức vụ, Điện thoại, Fax; dòng "Cập nhật lần cuối bởi … lúc …"; ô ẩn `csrfToken` như các form khác (AuthenticationFilter kiểm); khung trang/CSS như `web/jsp/admin/updateEmployee.jsp`; set `activeNav = 'branch'`.
    - `web/jsp/common/sidebar.jsp`: thêm mục "Thông tin chi nhánh" ngay dưới "Nhân viên", chỉ hiện với Admin, sáng khi `activeNav == 'branch'`; thêm `branch` vào danh sách giá trị hợp lệ của `activeNav` trong comment đầu file.
    - `PERMISSIONS.md`: thêm dòng UC-55 chỉ Admin, thêm `BranchInfoController` vào danh sách controller.
    - `docs/ARCHITECTURE.md`: thêm `BranchInfoController`, `BranchInfoService`, `BranchInfoValidator`, `BranchInfoDAO`, `BranchInfo` vào các tầng tương ứng.
  - File test: `test/poscs/validator/BranchInfoValidatorTest.java` (thiếu họ tên/chức vụ; quá 100 ký tự; SĐT/fax đúng: số di động, số máy bàn có khoảng trắng/ngoặc/dấu chấm, có `+84`; SĐT/fax sai: có chữ cái, dưới 8 hoặc trên 15 chữ số, đủ 8–15 chữ số nhưng tổng quá 20 ký tự như `(024) 3852 1234 - 1105`); `test/poscs/service/BranchInfoServiceTest.java` (dữ liệu sai thì không gọi DAO; DAO trả `false` thì báo lỗi); `test/poscs/controller/BranchInfoControllerTest.java` (Admin xem và lưu được; Sales, Kỹ thuật → 403 MSG-022; chưa đăng nhập bị chặn; lưu xong có MSG-057; lỗi kiểm tra giữ dữ liệu đã nhập).
  - Không đụng: form và luồng hợp đồng (Task 8.4); `EmployeeController`, `EmployeeDAO`.
  - Lưu ý: chỉ sửa, không có nút tạo/xoá (một bản ghi duy nhất). Không dùng `BaseValidator.PHONE_VN` cho SĐT/fax: nó chỉ nhận số di động. Cần hộp xác nhận thì dùng modal trong trang, không dùng `alert`/`confirm` của trình duyệt.
  - Xong khi: đăng nhập `admin` thấy mục menu, sửa và lưu được; đăng nhập Sales gõ thẳng `/POSCS/branch` nhận 403.
  - Nhánh: `feat/task-8.3-branch-info-page`.
  - Commit: `feat(branch): trang Thông tin chi nhánh cho Admin (Task 8.3)`
  - *Đã hoàn thành:* `src/java/poscs/validator/BranchInfoValidator.java` (mới: BR-57, trim trước khi kiểm, lỗi theo từng ô, MSG-058/MSG-015/MSG-059); `src/java/poscs/service/BranchInfoService.java` (mới: `get()`, `findUpdaterName()`, `update(info, actor)` kiểm lại Admin qua `PermissionService.isAdmin`, dữ liệu sai không gọi DAO, lưu xong trả MSG-057); `src/java/poscs/controller/BranchInfoController.java` (mới, `/branch`: `AccessControl.requireAdmin` ở đầu `doGet`/`doPost`, lưu xong redirect `saved=1`, lỗi kiểm tra hiện lại form giữ dữ liệu, không đọc được bản ghi thì MSG-018); `web/jsp/admin/branchInfo.jsp` (mới); `web/jsp/common/sidebar.jsp` (mục "Thông tin chi nhánh" dưới "Nhân viên", chỉ Admin); `PERMISSIONS.md`; `docs/ARCHITECTURE.md`. Test: `BranchInfoValidatorTest` 21, `BranchInfoServiceTest` 15, `BranchInfoControllerTest` 14; `ant test` 2.028 test, 0 fail, 3 skipped; `compile-jsps` pass. **Chưa thử trên Tomcat**: `poscs_db` chưa chạy V39 nên `/branch` sẽ báo MSG-018 tới khi chạy V39. QA report tại `docs/qa/Task-8.3.md`.
- [x] **Task 8.4:** Form hợp đồng lấy người ký bên mình từ Thông tin chi nhánh (khoá ô, thêm SĐT và fax).
  - UC: `UC-33`, `UC-35`, `UC-46`, `UC-54`, `UC-11`.
  - BR / MSG / NFR: `BR-56`, `MSG-060`.
  - File sửa:
    - `src/java/poscs/dao/ContractDAO.java`: thêm `signer_phone`, `signer_fax` vào `SQL_INSERT`, `SQL_UPDATE_ALL`, câu SELECT và hai chỗ map `ResultSet` (quanh dòng 1976 và 2240). `bindSigningParties` bind thêm hai cột: **dịch lại chỉ số** của các tham số `?` đứng sau trong INSERT/UPDATE. `withTermsFrom` giữ thêm hai trường. Nhật ký thay đổi (quanh dòng 2003) thêm "Điện thoại người ký bên mình", "Fax người ký bên mình".
    - `src/java/poscs/service/ContractService.java`: thêm hàm tĩnh `applyBranchSigner(Contract c, BranchInfo b)` chép 4 ô theo BR-56. Gọi trong `createContract`, `createAmendment`, và trong `updateContract` khi hợp đồng chưa khoá điều khoản (`!existing.isTermsLocked()`). `correctSignedContract` chép 4 ô từ bản ghi đang có.
    - `src/java/poscs/controller/ContractController.java` (lối `/contract`: hai form hợp đồng post vào đây và lối này gọi thẳng `contractDAO`): `buildContractFromRequest` (quanh dòng 1327) thôi đọc `signerName`/`signerPosition` từ request. `handleCreate`, `handleCreateAmendment`, và `handleUpdate` khi chưa khoá điều khoản gọi `ContractService.applyBranchSigner`; `handleCorrect` chép 4 ô từ bản ghi đang có. Khi mở form tạo/sửa thì set thuộc tính `branchInfo` cho JSP. Lấy thông tin qua `BranchInfoService`, tiêm phụ thuộc đúng cách controller đang tiêm `contractDAO` để test mock được.
    - `src/java/poscs/controller/ContractControllerHelper.java` (lối `/contract/action`): `buildContractFromRequest` (quanh dòng 73) thôi đọc hai ô người ký bên mình; chỗ set `canEditTerms` (quanh dòng 154) set thêm `branchInfo`.
    - `src/java/poscs/validator/ContractValidator.java`: giới hạn 20 ký tự cho `signerPhone`, `signerFax`; `hasRestrictedFieldsChanged` so thêm hai trường này.
    - `web/jsp/sale/addnewcontract.jsp`, `web/jsp/sale/updatecontract.jsp`, mục "Thông tin ký kết": thay hai ô nhập người ký bên mình bằng 4 ô **chỉ đọc** (Người ký bên mình, Chức vụ, Điện thoại, Fax). Form tạo và bản Nháp hiện `branchInfo`; hợp đồng đã khoá điều khoản hiện giá trị đang lưu trên hợp đồng. Chưa khai GĐCN thì hiện MSG-060 (Admin thấy thêm link sang `/branch`). Sửa dòng chú thích đầu mục cho đúng cách làm mới; bỏ placeholder "VD: Giám đốc chi nhánh".
    - `web/jsp/sale/viewcontractdetail.jsp` (quanh dòng 562): hiện thêm Điện thoại, Fax dưới người ký bên mình khi có.
  - File test: `test/poscs/dao/ContractDAOTest.java` (insert/update bind hai cột mới đúng vị trí; `withTermsFrom` giữ 4 ô khi đã ký; nhật ký ghi thay đổi SĐT/fax); `test/poscs/validator/ContractValidatorTest.java` (độ dài, `hasRestrictedFieldsChanged`); `test/poscs/service/ContractServiceTest.java` (`applyBranchSigner`; tạo, phụ lục, sửa bản Nháp lấy GĐCN hiện tại; chưa khai thì bản Nháp giữ giá trị cũ; chưa khai (chỉ có chức vụ gieo sẵn, họ tên trống) thì tạo mới để trống cả bốn ô; chữa sai sót giữ nguyên); `test/poscs/controller/ContractControllerTest.java` và `test/poscs/controller/ContractActionControllerTest.java` (POST kèm `signerName` tự gõ bị bỏ qua; form có thuộc tính `branchInfo`). Không sửa assertion của test cũ; nếu có test đang khẳng định `signerName` lấy từ request thì dừng, báo trong report rằng hành vi đó đổi theo BR-56.
  - Không đụng: người ký bên đối tác, căn cứ uỷ quyền, nơi ký (vẫn nhập tay); `ContractExportService` (Excel chưa có cột người ký); dữ liệu hợp đồng cũ trong CSDL.
  - Lưu ý: ô `disabled` không được gửi lên khi submit, nên server **không bao giờ** lấy 4 ô này từ request, kể cả ở lối chữa sai sót (UC-54). "Chưa khai GĐCN" = họ tên GĐCN trống sau khi trim, kể cả dòng gieo sẵn của V39 chỉ có chức vụ; khi đó tạo mới để trống cả bốn ô, **không** chép chức vụ gieo sẵn, và form hiện MSG-060 (BR-56). CSDL đang chạy phải có V39 (Task 8.2) trước khi build vào checkout chính: Tomcat 8080 của người dùng chạy thẳng `build/web`, và NetBeans tự dịch class ngay khi kéo code.
  - Xong khi: tạo hợp đồng mới thấy 4 ô đã điền sẵn GĐCN, không gõ được; lưu xong trang chi tiết hiện đủ 4 thông tin; Admin đổi GĐCN thì bản Nháp lưu lại sẽ đổi theo, hợp đồng đã ký giữ nguyên.
  - Nhánh: `feat/task-8.4-contract-branch-signer`.
  - Commit: `feat(contract): người ký bên mình lấy từ thông tin chi nhánh (Task 8.4)`
  - *Đã hoàn thành:* `ContractDAO` thêm `signer_phone`, `signer_fax` vào INSERT/UPDATE/SELECT (chỉ số tham số phía sau dịch theo `SIGNING_PARTY_COLUMNS = 9`), nhật ký ghi thay đổi SĐT/fax, `withTermsFrom` và `correct` giữ 4 ô đang lưu; `ContractService` thêm `applyBranchSigner`, `copyBranchSigner`, `isBranchSignerDeclared`, `findBranchInfo` và constructor 6 tham số nhận `BranchInfoService`; `ContractController` và `ContractControllerHelper` (hàm mới `putBranchSigner`) thôi đọc người ký bên mình từ request, set `branchInfo`/`branchSignerMissing` cho form; `ContractActionController` (ngoài dòng "File sửa", người dùng đồng ý) gọi `putBranchSigner` ở 3 form; `ContractValidator` giới hạn 20 ký tự SĐT/fax, `hasRestrictedFieldsChanged` so thêm hai ô; `addnewcontract.jsp`, `updatecontract.jsp` 4 ô chỉ đọc + MSG-060, `viewcontractdetail.jsp` hiện SĐT/fax. Test: +43 test ở 5 lớp; `ant test` 2.071 test, 0 fail, 3 skipped; 3 JSP dịch qua Jasper của Tomcat 11 không lỗi (`compile-jsps` của dự án không dịch JSP). QA report tại `docs/qa/Task-8.4.md`.
- [x] **Task 8.5:** Người phụ trách hợp đồng lấy theo người tạo, không đổi được sau khi tạo.
  - UC: `UC-33`, `UC-35`, `UC-46`, `UC-54`.
  - BR / MSG / NFR: sửa `BR-35`; dùng lại `MSG_OWNER_REQUIRED`, `MSG_CANNOT_EDIT_LOCKED` đã có trong `ContractValidator`; không thêm MSG mới.
  - File sửa:
    - `docs/SPEC.md` (Task 8.1 phải merge trước, vì cùng sửa §5.4):
      - `BR-35` thêm câu: "Người phụ trách: Sales tạo → chính người tạo, không chọn; Admin tạo → chọn một Sales đang hoạt động; phụ lục → người phụ trách của hợp đồng gốc. Không đổi được sau khi tạo; ngoại lệ: Admin đổi được khi người phụ trách hiện tại đã bị khoá tài khoản."
      - §5.4 đoạn "Tạo (UC-33)": trỏ tới BR-35 cho người phụ trách.
      - §5.4 đoạn "Cập nhật (UC-35)": đổi "**đã ký** chỉ đổi được **Người phụ trách**" thành "Người phụ trách không đổi được ở mọi tiến độ (trừ ngoại lệ của Admin ở BR-35); **đã ký** thì form Sửa không còn ô nào sửa được (thay đổi đi qua phụ lục, sai sót nhập liệu đi qua UC-54)".
      - §11: thêm quyết định 13, tóm tắt mục 11 "Quyết định đã chốt" của file này.
    - `src/java/poscs/service/ContractService.java`:
      - `createContract`: actor là Sales thì `ownerId` = chính actor, bỏ qua giá trị gửi lên; actor là Admin thì giữ lựa chọn và vẫn kiểm `isActiveOwner`.
      - `createAmendment`: `ownerId` = người phụ trách hợp đồng gốc.
      - `updateContract`, `correctSignedContract`: `ownerId` luôn chép từ bản ghi đang có. Hợp đồng đã khoá điều khoản mà gửi form Sửa (không phải chữa sai sót) thì từ chối bằng `MSG_CANNOT_EDIT_LOCKED`.
      - `updateContractOwner` chỉ còn cho Admin, và chỉ khi người phụ trách hiện tại đã bị khoá tài khoản (`!isActiveOwner(...)`); người mới phải là Sales đang hoạt động; áp được cả khi đã ký, trừ khi đã thanh lý/chấm dứt. Ngoài ngoại lệ đó không còn đường đổi người phụ trách.
      - Phần chọn người phụ trách lúc tạo đặt trong một hàm tĩnh dùng chung, để `ContractController` gọi lại chứ không viết hai lần.
    - `src/java/poscs/controller/ContractActionController.java` (lối `/contract/action`): `updateByTermsLock` chỉ gọi `updateContractOwner` khi actor là Admin và người phụ trách hiện tại đã bị khoá; các trường hợp khác theo luật trên.
    - `src/java/poscs/controller/ContractController.java` (lối `/contract`, gọi thẳng `contractDAO`): áp đúng các luật trên ở `handleCreate`, `handleCreateAmendment`, `handleUpdate` (cả nhánh đã ký đang đọc `ownerId` từ request, quanh dòng 585) và `handleCorrect`; nhánh đã ký chỉ còn nhận `ownerId` trong đúng ngoại lệ của Admin. `buildContractFromRequest` vẫn đọc `ownerId` (Admin tạo cần), các handler ghi đè theo luật.
    - `src/java/poscs/dao/ContractDAO.java`: `update` luôn ghi `owner_id` của bản ghi cũ (`before`), kể cả trong `withTermsFrom`. Giữ `SQL_UPDATE_ADMIN_FIELDS` cho ngoại lệ của Admin; nhật ký vẫn ghi dòng đổi người phụ trách khi Admin đổi theo ngoại lệ đó.
    - `web/jsp/sale/addnewcontract.jsp`: ô Người phụ trách: Sales thấy tên chính mình dạng chỉ đọc; Admin thấy ô chọn Sales như cũ; phụ lục thấy tên người phụ trách hợp đồng gốc dạng chỉ đọc (bỏ comment "VẪN cho đổi"). Bỏ `'owner'` khỏi danh sách kiểm JS (quanh dòng 377) khi không có ô chọn.
    - `web/jsp/sale/updatecontract.jsp`: ô Người phụ trách (quanh dòng 313) thành chỉ đọc; riêng Admin, khi người phụ trách hiện tại đã bị khoá tài khoản, thấy ô chọn Sales kèm chú thích "Người phụ trách đã bị khoá tài khoản, chọn người thay". Khung báo "Hợp đồng đã ký" (quanh dòng 261) bỏ câu "Chỉ còn người phụ trách sửa được". Khi đã khoá điều khoản thì ẩn nút "Lưu thay đổi", chỉ hiện lúc Admin bật "Chữa sai sót nhập liệu" hoặc khi Admin đang thấy ô chọn người thay. Bỏ `'owner'` khỏi danh sách kiểm JS (quanh dòng 1392).
  - File test: `test/poscs/service/ContractServiceTest.java` (Sales tạo kèm `ownerId` người khác → vẫn là chính mình; Admin tạo → theo lựa chọn, chọn người không phải Sales đang hoạt động → lỗi; phụ lục → người phụ trách gốc; sửa bản Nháp kèm `ownerId` khác → giữ nguyên; sửa hợp đồng đã ký → bị từ chối; chữa sai sót → giữ nguyên); `test/poscs/controller/ContractControllerTest.java` và `test/poscs/controller/ContractActionControllerTest.java` (các luật trên ở cả hai lối, kể cả POST tự gõ `ownerId`); `test/poscs/dao/ContractDAOTest.java` (`update` giữ `owner_id` cũ). Các assertion đang gọi `updateContractOwner` (`ContractServiceTest` quanh dòng 488–533, `ContractActionControllerTest` quanh dòng 361–365) được sửa theo luật mới; thêm test: Admin đổi khi người cũ còn hoạt động → bị từ chối; Sales đổi → bị từ chối; Admin đổi khi người cũ đã bị khoá → thành công, có dòng nhật ký. Ghi rõ trong report đã sửa những test nào.
  - Không đụng: `owner_id` của hợp đồng cũ trong CSDL; luồng yêu cầu thay đổi (`ChangeRequest*`); phạm vi "Của tôi / Toàn chi nhánh", thông báo, dashboard; người ký bên mình (Task 8.4).
  - Lưu ý: làm **sau Task 8.4** (cùng sửa `ContractController`, `ContractService`, `ContractDAO` và hai JSP form). Người dùng đã chọn "khoá luôn" với đúng một ngoại lệ: Admin đổi khi người phụ trách đã bị khoá tài khoản (nghỉ việc). Phòng kinh doanh không gán cấp trên (mục 13), nên ai tạo hợp đồng thì đứng tên. Không tự thêm ngoại lệ nào khác; cần thì dừng hỏi.
  - Xong khi: Sales quản lý vùng tạo hợp đồng không chọn được người phụ trách, lưu xong là chính mình; Admin tạo vẫn chọn Sales; phụ lục mang người phụ trách của hợp đồng gốc; trang Sửa không đổi được người phụ trách ở mọi tiến độ, trừ Admin khi người phụ trách hiện tại đã bị khoá tài khoản.
  - Nhánh: `feat/task-8.5-contract-owner-creator`.
  - Commit: `feat(contract): người phụ trách lấy theo người tạo, không đổi sau khi tạo (Task 8.5)`
  - *Đã hoàn thành:* `docs/SPEC.md` (BR-35, §5.4 Tạo/Cập nhật, §11 quyết định 13); `ContractService` thêm hàm tĩnh `applyCreateOwner`, `canReplaceOwner`, `updateContractOwner` chỉ còn ngoại lệ Admin thay người đã bị khoá (khác thì MSG-022), `updateDraftReplacingOwner` (kiểm người thay → lưu bản Nháp → mới đổi người, theo QA-8.5-01), `updateContract` từ chối hợp đồng đã ký, sửa/chữa sai sót giữ người phụ trách cũ; `ContractDAO.update`/`correct` luôn giữ `owner_id` cũ, thêm `replaceOwner` (một giao dịch, có nhật ký); `ContractController` và `ContractActionController` áp luật ở cả hai lối, set `ownerSelectable`/`canReplaceOwner`; `addnewcontract.jsp`, `updatecontract.jsp` ô người phụ trách chỉ đọc (Admin chọn khi tạo, chọn người thay khi người cũ bị khoá), ẩn nút Lưu với hợp đồng đã ký. Test: +68 test ở 4 lớp, sửa các test cũ khẳng định "đổi được người phụ trách" (ghi rõ trong QA report); `ant test` 2.139 test, 0 fail, 3 skipped; 2 JSP dịch qua Jasper của Tomcat 11 không lỗi. Không có migration. QA report tại `docs/qa/Task-8.5.md`.
- [x] **Task 8.6:** Cập nhật hướng dẫn sử dụng (trang trong app và bản Word) cho Thông tin chi nhánh, người ký bên mình và người phụ trách.
  - UC: `UC-55`, `UC-33`, `UC-35`.
  - BR / MSG / NFR: `BR-35`, `BR-56`, `BR-57`, `MSG-060`.
  - File sửa:
    - `web/jsp/guide/contract.jsp`: dòng "Người phụ trách" (quanh dòng 152) theo BR-35 mới; dòng "Thông tin ký kết" (quanh dòng 154): người ký bên mình hệ thống tự lấy từ Thông tin chi nhánh, chỉ nhập người ký bên đối tác; bảng tiến độ (quanh dòng 44), đoạn quanh dòng 211 và dòng 274 đang nói người phụ trách "còn đổi được" thì sửa cho đúng.
    - `web/jsp/guide/employee.jsp`: thêm mục ngắn "Thông tin chi nhánh" cho Admin (mở từ menu, 4 ô, ràng buộc BR-57).
    - `tools/guide/shots/contract.mjs`: mục 'Thông tin ký kết' đang `n: 6`, chỉnh nếu số ô đổi; xem lại ảnh có ô Người phụ trách.
    - `tools/guide/word/export_word.py`: thêm 1 dòng cuối `CHANGES`.
    - `docs/Report6_HuongDanSuDung.docx`: xuất lại.
  - File test: không có test Java mới; chạy `ant test`.
  - Không đụng: code Java, `docs/SPEC.md`.
  - Lưu ý: đọc code của Task 8.3, 8.4, 8.5 đã merge trước khi viết, ghi đúng chữ trên giao diện. Xuất docx như Task 7.4a: `lastModifiedBy` trong `docProps/core.xml` phải là `tools/guide/word/export_word.py`, khác thì dừng hỏi. Lệnh: `python tools/guide/word/export_word.py --template "C:\Users\Admin\Documents\Backup\Prj\Ref\Report6_Software User Guides.docx" --out "docs\Report6_HuongDanSuDung.docx"`. Thiếu Python/thư viện thì dừng, không tự cài.
  - Xong khi: hai trang hướng dẫn và docx mô tả đúng cách làm mới; docx có dòng Task 8.6 trong Lịch sử thay đổi.
  - Nhánh: `docs/task-8.6-guide-branch-signer-owner`.
  - Commit: `docs(guide): hướng dẫn thông tin chi nhánh, người ký bên mình và người phụ trách (Task 8.6)`
  - *Đã hoàn thành:* `web/jsp/guide/contract.jsp` (bảng tiến độ, bảng quyền thêm dòng chọn/thay người phụ trách; mục 3 người phụ trách theo BR-35, người ký bên mình theo BR-56, cảnh báo MSG-060; mục 4, mục 5; mục mới 5.2 "Thay người phụ trách đã nghỉ việc"; mục 6.1, 6.2; phụ lục); `web/jsp/guide/employee.jsp` (mục mới 9 "Thông tin chi nhánh" theo BR-57, lỗi chung thành mục 10, bước nghỉ việc trỏ Hợp đồng mục 5.2); `tools/guide/shots/contract.mjs` (ảnh 04 khoanh ô người phụ trách chỉ đọc, ảnh 11 còn 3 khoanh); chụp lại `web/guide/contract/` 04, 07, 11, 12, 19 trên Tomcat 8081 với bản sao `poscs_db` (người dùng đồng ý; GĐCN mẫu tên và số giả; đã dọn CSDL tạm); `tools/guide/word/export_word.py` thêm dòng CHANGES; `docs/Report6_HuongDanSuDung.docx` xuất lại. Không có test Java mới; `ant test` 2.204 test, 0 fail, 3 skipped. QA report tại `docs/qa/Task-8.6.md`.

---

## Giai đoạn 9: Vai trò và phòng ban theo cơ cấu chi nhánh thật

> Yêu cầu người dùng 19/11/2026, quyết định ở mục 13 "Quyết định đã chốt". Làm **sau Giai đoạn 8**, theo thứ tự 9.1 → 9.5. Bộ Project_Report (diagram đang có actor "Kỹ thuật") **không** nằm trong giai đoạn này; đó là việc tài liệu riêng.

- [x] **Task 9.1:** Cập nhật `docs/SPEC.md` theo cơ cấu mới: ba vai Admin / Sales / Tổng hợp, ba phòng Ban giám đốc / Kinh doanh / Tổng hợp.
  - UC: đổi tác nhân Kỹ thuật → Sales ở `UC-12`, `UC-13`, `UC-16` → `UC-23`, `UC-42`; phòng nhận là Tổng hợp ở `UC-48`, `UC-49`; thêm Tổng hợp vào `UC-53` (chỉ ghi nhận đã thu).
  - BR / MSG / NFR: `BR-39` (ô "Kỹ thuật viên được chỉ định" thành "Người xử lý", chọn trong Sales); `MSG-022`.
  - File sửa: `docs/SPEC.md`:
    - §3.1: ba tác nhân Admin, Sales, Tổng hợp. Sales thêm quản lý sản phẩm và xử lý phiếu hỗ trợ. Tổng hợp: xem khách hàng, hợp đồng, sản phẩm; không vào phiếu hỗ trợ; xác nhận bàn giao cho phòng mình; ghi nhận kỳ thanh toán đã thu (không lập, không xoá kỳ). Khung "Đã chốt" đổi thành ba vai mới, ghi ngày đổi.
    - §3.3: ma trận theo cột Admin / Sales / Tổng hợp.
    - §4.3, §4.5: đổi tác nhân; §4.4: UC-49, UC-53 thêm Tổng hợp.
    - §5.4 (bàn giao): phòng nhận là Tổng hợp. §5.5: "Kỹ thuật viên" thành "Người xử lý (Sales)".
    - §7: ghi chú `roles` có ba vai, `departments` có ba phòng.
    - §11 quyết định 1: đổi thành ba vai mới, nêu lý do (cơ cấu chi nhánh thật, 19/11/2026).
  - File test: không có (chỉ tài liệu); chạy `ant test`.
  - Không đụng: code, `db/`, `PERMISSIONS.md` (sửa cùng code ở Task 9.3, 9.4).
  - Lưu ý: sau khi sửa, `grep -n "Kỹ thuật" docs/SPEC.md` chỉ còn chỗ nói lịch sử/quyết định cũ hoặc chữ "hỗ trợ kỹ thuật" trong tên phiếu; liệt kê các chỗ còn lại trong report.
  - Xong khi: SPEC không còn giao việc nào cho vai Kỹ thuật; ma trận §3.3 có cột Tổng hợp.
  - Nhánh: `docs/task-9.1-spec-co-cau-chi-nhanh`.
  - Commit: `docs(spec): vai trò và phòng ban theo cơ cấu chi nhánh (Task 9.1)`
  - *Đã hoàn thành:* `docs/SPEC.md`: §3.1 ba vai Admin / Sales / Tổng hợp kèm dòng Phòng ban, khung "Đã chốt" ghi ngày đổi; §3.3 ma trận cột Admin / Sales / Tổng hợp, tách dòng "Ghi nhận kỳ thanh toán đã thu"; §4.1–§4.6 đổi tác nhân (UC-48, UC-49 phòng Tổng hợp; UC-53 Tổng hợp chỉ ghi nhận đã thu); §5.4 thêm mục Bàn giao; §5.5, BR-39, BR-46 đổi thành "Người xử lý" (Sales), danh sách phiếu mở lần đầu mặc định "Tất cả"; §5.7, §5.8, §7; §11 quyết định 1. `grep "Kỹ thuật"` chỉ còn tên phiếu "hỗ trợ kỹ thuật" và câu ghi lịch sử (dòng 64, 473). `ant test` 2.071 test, 0 fail, 3 skipped. QA report tại `docs/qa/Task-9.1.md` (ĐẠT). `rules.md` §1 vẫn ghi vai Kỹ thuật (ngoài dòng "File sửa"), chưa sửa.
