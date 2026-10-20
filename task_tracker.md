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
2. Thông báo SLA phiếu: **báo trước 24 giờ**. Thông báo hợp đồng: chốt **30 ngày** (BR-17).
3. Bảng phân công địa bàn **đã có trong DB** (`user_provinces`; tên chính xác theo `db/schema.sql`).
4. Hai gói `poscs.service` và `poscs.validator` đã được xác nhận và đã tồn tại.
5. Integration test: 3 lớp trong `test/poscs/integration/`, **mặc định bị bỏ qua (skipped)**; chỉ chạy khi trỏ vào CSDL dùng-một-lần có tên kết thúc bằng `_it` (xem Task 7.2).
6. UC-50, UC-51 (Change Request): `ChangeRequestDAO` và `ChangeRequestController` đã tồn tại độc lập kèm test, giữ nguyên không tách lại (Task 4.8 bỏ).
7. UC-54 (Chữa sai sót HĐ đã ký): tích hợp vào `ContractService` (Task 4.10) và `ContractActionController` (Task 4.12); không tạo controller riêng.
8. Luồng chi tiết UC-36 → UC-54: lấy hành vi code và JSP hiện có làm chuẩn (khóa bằng test ở Task 4.2), chỉ tách nhỏ theo rules §3 mà không thêm tính năng.

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
- [ ] **Task 4.12:** `[UC-33, UC-34, UC-35, UC-44, UC-46, UC-54]` `ContractActionController` (Tạo, Sửa, Ký, Huỷ bản ghi, Phụ lục, Chữa sai sót UC-54 gọi về `ContractService`).
- [ ] **Task 4.13:** `[UC-10, UC-11, UC-45]` `[BR-12, BR-14, BR-16, BR-18]` `ContractListController` và `ContractDetailController`.
- [ ] **Task 4.14:** `[UC-48, UC-49]` `ContractHandoverController`.
- [ ] **Task 4.15:** `[UC-53]` `ContractPaymentController`.
- [ ] **Task 4.16:** `[UC-52]` `ContractDocumentController`.
- [ ] **Task 4.17:** `[UC-41]` `ContractExportController` (Excel, cùng bộ lọc, không phân trang, sắp theo tỉnh/thành).
- [ ] **Task 4.18:** `[UC-47]` Xây dựng `ContractLinkController` cho liên kết hợp đồng Bán ↔ Mua (và gỡ liên kết). Phần ChangeRequest (UC-50, UC-51) giữ nguyên `ChangeRequestController` đã có.
- [ ] **Task 4.19:** `[UC-54]` Kiểm thử luồng chữa sai sót hợp đồng đã ký (chỉ Admin, bắt buộc lý do, ghi nhật ký sự kiện riêng `EVENT_CORRECTED`, khóa ngày ký) trên `ContractService` (Task 4.10) và `ContractActionController` (Task 4.12); không tạo controller riêng.

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
