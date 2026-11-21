# POSCS Task Tracker (v2)

> **Nguyên tắc làm việc:**
> - Bám `docs/SPEC.md` (bản v2) và `rules.md`. Không suy diễn, không thêm tính năng ngoài SPEC.
> - **Chỉ giải quyết 1 task (checklist item) trong 1 lượt chạy.**
> - Chọn task `[ ]` đầu tiên theo thứ tự, trừ khi nó bị chặn bởi mục "Quyết định đang chờ" bên dưới: khi đó **dừng và hỏi người dùng**.
> - Không viết code dồn vào một file; tách service / validator / DAO / controller rõ ràng.
> - Mỗi task kết thúc bằng báo cáo ngắn: đã làm gì, file đã đổi, kết quả test, điểm chưa chắc chắn.
> - Việc triển khai lên VPS (Giai đoạn 11, Task `11.x`) cùng các quyết định của nó ghi riêng ở `task_tracker_deploy.md`.

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
14. **Vòng đời hợp đồng theo cách chi nhánh làm (Giai đoạn 10, chốt 19/11/2026; rút gọn cùng ngày: POSCS chỉ để theo dõi hợp đồng, không làm kế toán hay quản lý hồ sơ, nhưng vòng đời phải khớp cách chi nhánh làm thật):** luồng thật là phiếu GQNCKH → phiếu ĐCNB → gọi xe → giao cho khách → thương thảo, ký hợp đồng → bàn giao phòng Tổng hợp → tạm ứng (nếu có) → nghiệm thu, nộp hồ sơ thanh toán cho bên mua → bên mua trả trong số ngày đã thoả thuận → thanh lý. Hồ sơ hợp đồng Nháp mở từ lúc làm phiếu GQNCKH; **mã hợp đồng để trống tới lúc Ký** (bắt buộc khi Ký); ngày hiệu lực/kết thúc để trống tới khi chốt với khách. Ngày ký vẫn là ngày bấm Ký, hiệu lực tính từ ngày ký (không nới BR-36). **Tiến độ giữ nguyên** Nháp → Đã ký → Đã thanh lý / Chấm dứt sớm; hệ thống suy ra bước "Chuẩn bị hàng" / "Đã giao – chưa ký" từ giấy tờ đã có (mục 15). Đã giao **quá 15 ngày** chưa ký thì nhắc người phụ trách một lần. Không theo dõi số lượng hàng, tồn kho, điều xe.
15. **Giấy tờ và kỳ thanh toán, bản rút gọn (Giai đoạn 10, người dùng chốt 19/11/2026; thay bản tách giấy chung / giấy riêng từng kỳ duyệt trước đó cùng ngày):**
    - **Giấy tờ:** giữ mục Tài liệu sẵn có (bảng `contract_documents`, các lớp `ContractDocument*`), **không** làm bảng hay mô-đun mới. Thêm ngày trên giấy (bắt buộc, không sau hôm nay) và số giấy; link thành không bắt buộc. Một danh sách chung cho cả hợp đồng: không tách theo kỳ, không có mẫu giấy theo loại kỳ. Giao diện dạng ô tích theo danh mục cố định trong code cộng "Khác"; một loại có thể có nhiều tờ (giao nhiều đợt, mỗi kỳ một đề nghị thanh toán). Bỏ tích = huỷ mềm **bắt buộc lý do** như hiện nay. Sales có quyền ghi hợp đồng tích giấy. 2 tài liệu cũ (gồm link demo HD-0015) giữ nguyên.
    - **Kỳ thanh toán:** giữ số tiền, hạn thu, ngày thu. Thêm loại kỳ **Tạm ứng / Thanh toán**, điều kiện (ghi ngắn), số ngày phải trả kể từ khi nộp hồ sơ (không bắt buộc), **ngày nộp hồ sơ** và **số tiền thực thu** (mặc định bằng số tiền kỳ, khác thì không bắt lý do). Hạn thu để trống được khi lập; bấm "Đã nộp hồ sơ" thì ghi ngày nộp, kỳ có số ngày thì hạn thu = ngày nộp + số ngày, không có thì nhập hạn tay. Tình trạng suy ra: Chưa nộp hồ sơ → Chờ thu → Quá hạn → Đã thu. Ô "%" trên form chỉ tính sẵn số tiền từ giá trị hợp đồng, vẫn lưu số tiền. Thêm thao tác **sửa kỳ** (kỳ chưa thu, hợp đồng chưa thanh lý/chấm dứt). Dashboard cộng số tiền thực thu theo ngày thu, chưa tách tạm ứng; tạm ứng không thu hồi dần. **Sales** (theo quyền quản lý kỳ) bấm "Đã nộp hồ sơ" (người dùng chốt 19/11/2026); vai Tổng hợp chỉ thêm được "Đã thu" (Task 9.2), và khi bấm thì **nhập được số tiền thực thu** như ở trang Sửa (người dùng chốt 20/11/2026). Bấm "Đã nộp hồ sơ" cho kỳ không có số ngày thì hạn thu **không bắt buộc**; kỳ đã nộp hồ sơ mà chưa có hạn thu hiện **Chờ thu** (người dùng chốt 20/11/2026 lúc làm Task 10.1; thay câu "có hạn thu → Chờ thu" ở BR "Kỳ thanh toán" của Task 10.1 thành "có hạn thu, hoặc đã có ngày nộp hồ sơ → Chờ thu", đã ghi vào BR-60).
    - **Không làm:** trạng thái "Đủ hồ sơ", giấy riêng của từng kỳ, bảng "kỳ cần giấy gì", kỳ "Thanh toán 100%" tự tạo, tự tính lại số tiền kỳ khi phụ lục đổi giá trị, cảnh báo tổng tỷ lệ.
16. **Dữ liệu mẫu mới (Task 10.6, người dùng chốt 20/11/2026):** chỉ thay **dữ liệu mẫu**, giữ cấu trúc bảng và các file migration cũ. Bộ tài khoản theo chi nhánh thật, tên giả: 2 Admin (GĐ, PGĐ), 4 Sales (Trưởng phòng, Phó phòng, 2 nhân viên; không ai có cấp trên), 3 Tổng hợp (kế toán, thu tiền, nhận bàn giao) và 1 Sales đã nghỉ (tài khoản bị khoá). **Gieo cả hai Admin** vào file migration đánh dấu `-- poscs:demo-only` và phần dữ liệu mẫu của `db/schema.sql` (thay quy tắc cũ "không gieo Admin vào repo"; production theo Task 11.3 không chép bảng `users`). `poscs_db` trên máy dev **xoá sạch rồi dựng lại** từ `db/schema.sql`, không giữ gì, kể cả link demo của HD-0015 và tài khoản `nguyendat`. Bộ dữ liệu **nhỏ**, không nhiều như bộ cũ (người dùng chốt 20/11/2026): 4 khách hàng, 1 nhà cung cấp, 8 hợp đồng và 1 phụ lục, 3 phiếu hỗ trợ. Làm sau Task 10.5, trước hướng dẫn sử dụng (Task 10.7).
17. **Dọn tài liệu và hướng dẫn (người dùng chốt 21/11/2026):**
    - **Xoá:** `docs/kichbandemoposcs.html`, `docs/kichbandemoposcs.pdf`, `docs/POSCS_TaiKhoanTest.sql`, `docs/POSERD.png` (lỗi thời, không file nào trỏ tới; Task 10.8). Ảnh mồ côi `web/guide/contract/20-gia-tri-hien-hanh.png` xoá ngay trong Task 10.7, vì Task 10.7 gỡ mục ảnh đó.
    - **Đưa vào git:** `docs/PROMPTS.md`, `task_tracker_deploy.md`, `docs/HuongDan_DoiEmailGui.md` (Task 10.8). Hướng dẫn đổi email gửi sẽ gộp vào mục Email của `DEPLOY.md` ở Task 11.5 rồi xoá.
    - **Một bản quy tắc:** `rules.md` là bản duy nhất; `.gemini/rules.md` chỉ còn trỏ về `rules.md`, không chép nội dung nữa.
    - **Giữ nguyên:** báo cáo QA trong `docs/qa/`; bốn file test `.xlsx` và `tools/testdoc/` (cập nhật là việc tài liệu riêng, giống Project_Report); `DEPLOY.md` (viết lại ở Task 11.5).
18. **README gọn bằng tiếng Việt (Task 10.9, người dùng chốt 21/11/2026):** viết lại `README.md` bằng tiếng Việt, chỉ giữ thứ người mới cần để chạy được app, gồm 9 mục: POSCS là gì, công nghệ, cấu trúc thư mục, chạy trên máy, đổi cấu trúc CSDL, kiểm thử, log, bảo mật, tài liệu khác. **Không ghi tài khoản hay mật khẩu mẫu.** Bỏ danh sách tên lớp, số file và các đoạn giải thích dài. Quy ước viết log trong code chuyển sang `rules.md` §4 (agent viết code chỉ đọc `rules.md`).

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

> Yêu cầu người dùng 19/11/2026, quyết định ở mục 13 "Quyết định đã chốt". Làm **sau Giai đoạn 8**, theo thứ tự 9.1 → 9.5 (Task 9.4, 9.5 thêm ngày 20/11/2026: dọn và sửa phần còn sót sau 9.3). Rút gọn cùng ngày (POSCS chỉ để theo dõi hợp đồng): phòng và vai Tổng hợp gộp thành một task (9.2); bỏ vai Kỹ thuật chỉ sửa đúng chỗ có logic (9.3); hướng dẫn sử dụng và tài khoản test chuyển sang Task 10.6 và Task 9.2. Bộ Project_Report (diagram đang có actor "Kỹ thuật") **không** nằm trong giai đoạn này; đó là việc tài liệu riêng.

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
- [x] **Task 9.2:** Phòng và vai Tổng hợp: gộp phòng Kế toán, Dự án; vai Tổng hợp xem, xác nhận bàn giao, ghi nhận đã thu.
  - UC: `UC-10`, `UC-11`, `UC-12`, `UC-13`, `UC-48`, `UC-49`, `UC-53`; `UC-26`, `UC-27` (Admin chọn vai Tổng hợp cho nhân viên).
  - BR / MSG / NFR: `MSG-022`.
  - File sửa:
    - `db/migrations/V{số kế tiếp}__phong_va_vai_tong_hop__ndat2003.sql` (mới; số V lấy tiếp sau file lớn nhất trong `db/migrations/` lúc làm; comment đầu file theo kiểu V38, nêu lý do theo mục 13):
      - Đổi tên phòng "Kế toán" thành "Tổng hợp". Chuyển nhân viên (nếu có) và **mọi** chặng bàn giao của phòng "Dự án" và phòng "Kỹ thuật" sang Tổng hợp theo luật gộp ở dòng "Lưu ý". Xoá phòng "Dự án" (phòng "Kỹ thuật" xoá ở Task 9.3).
      - Thêm vai "Tổng hợp" vào `roles`.
    - `db/migrations/V{số kế tiếp + 1}__seed_tonghop_test_account__ndat2003.sql` (mới; dòng đầu `-- poscs:demo-only` nếu Task 11.2 đã merge): tài khoản test `tonghop1`, vai Tổng hợp, phòng Tổng hợp, cùng mật khẩu và cách băm như các tài khoản của V12. Không phải Admin.
    - `db/schema.sql`: mirror (seed `departments`, `roles`, tài khoản `tonghop1`; sửa comment V32 đang nhắc hai phòng).
    - `src/java/poscs/service/PermissionService.java`:
      - Hằng `ROLE_GENERAL` = "Tổng hợp", hàm `isGeneral`.
      - `canViewCustomers`, `canViewProducts`, `canViewContracts` (quanh dòng 162, 250, 294) cho thêm Tổng hợp; `canViewTickets` (quanh dòng 477) và quyền nhân viên **không** cho (403 MSG-022).
      - `getCustomerListScope`, `getContractListScope` (quanh dòng 593, 610): Tổng hợp xem toàn chi nhánh như Admin (họ không đứng tên hợp đồng nào).
      - `canConfirmHandover` (quanh dòng 383): kiểm lại với nhân viên vai Tổng hợp thuộc phòng Tổng hợp; sửa nếu đang giả định vai khác.
    - `src/java/poscs/common/AccessControl.java`: Tổng hợp chỉ View trên CUSTOMER, CONTRACT, PRODUCT; không vào TICKET, EMPLOYEE. Kiểm lại `canCompleteHandover` (quanh dòng 213). Sửa comment nhắc "Kế toán"/"Dự án" (quanh dòng 205).
    - `src/java/poscs/controller/ContractPaymentController.java`, `src/java/poscs/service/ContractPaymentService.java`: hành động `markPaid` cho phép thêm vai Tổng hợp; lập, xoá kỳ vẫn cần quyền quản lý kỳ như cũ. Người bấm là Tổng hợp thì quay về trang chi tiết hợp đồng, không về trang Sửa.
    - `web/jsp/sale/viewcontractdetail.jsp` (bảng kỳ thanh toán quanh dòng 860–890): nút "Ghi nhận đã thu" cho vai Tổng hợp, mở modal trong trang (nhập ngày thu).
    - `web/jsp/sale/updatecontract.jsp` (quanh dòng 1052–1058) và `web/jsp/sale/listcontract.jsp` (quanh dòng 523–529): bỏ điều kiện tên phòng "Kế toán"/"Dự án"/"Kỹ thuật", chỉ còn "Tổng hợp". Ô "Bàn giao cho phòng" ở trang Sửa tích sẵn Tổng hợp.
    - `web/jsp/common/sidebar.jsp`: ẩn "Phiếu hỗ trợ" với vai Tổng hợp.
    - `src/java/poscs/service/DashboardService.java`: vai Tổng hợp mặc định phạm vi "Toàn chi nhánh".
    - Form nhân viên (`src/java/poscs/service/EmployeeService.java`, `web/jsp/admin/addEmployee.jsp`, `web/jsp/admin/updateEmployee.jsp`): ô Vai trò có Tổng hợp; kiểm lại chỗ nào đang chỉ cho Sales.
    - Comment đang nhắc "Kế toán"/"Dự án": `src/java/poscs/controller/ContractController.java` (quanh các dòng 971–1001, 1041, 1095–1119), `src/java/poscs/dao/ContractDAO.java` (quanh dòng 236, 1340).
    - `PERMISSIONS.md`: thêm cột Tổng hợp.
  - File test: `PermissionServiceTest` (ma trận vai Tổng hợp, phạm vi danh sách); `ContractPaymentControllerTest`, `ContractPaymentServiceTest` (Tổng hợp chỉ `markPaid` được, lập/xoá kỳ bị chặn); `ContractHandoverServiceTest`, `ContractHandoverDAOTest`, `ContractControllerTest` (xác nhận được phòng mình, không được phòng khác; dữ liệu mẫu tên phòng "Kế toán"/"Dự án" đổi sang "Tổng hợp", không đổi điều đang kiểm); test controller phiếu hỗ trợ (Tổng hợp → 403); `DashboardServiceTest`; `EmployeeServiceTest`.
    - Thêm lúc code (người dùng đồng ý 20/11/2026): `test/poscs/integration/ContractStatusIntegrationTest.java`. 6 test bàn giao tra phòng "Kế toán"/"Dự án"/"Kỹ thuật" trong `db/schema.sql`, mà schema mới không còn hai phòng đầu (V40) và Task 9.3 bỏ phòng thứ ba. Đổi sang "Tổng hợp", phòng thứ hai (khi test cần hai phòng) là "Kinh doanh"; test kiểm danh mục V32 đổi thành kiểm phòng Tổng hợp có, Kế toán/Dự án không còn, vai Tổng hợp có. Được sửa assertion ở đúng các chỗ mang tên phòng.
  - Không đụng: vai và phòng Kỹ thuật (Task 9.3); quyền của Admin và Sales; trang hướng dẫn (Task 10.6).
  - Lưu ý:
    - **Luật gộp chặng bàn giao (chốt 19/11/2026, migration làm theo, không dừng hỏi):** chặng đã xong giữ nguyên, chỉ đổi phòng. Sau khi gộp, hợp đồng nào có từ hai chặng **cùng đang mở** ở Tổng hợp thì giữ chặng mở sớm nhất, các chặng còn lại đóng lại: `done_at` = lúc chạy migration, `done_by` = tài khoản `admin`, `done_note` = "Đóng khi gộp phòng Kế toán, Dự án, Kỹ thuật thành Tổng hợp (19/11/2026)".
    - Trên `poscs_db` hiện có 6 hợp đồng bàn giao cho cả Kế toán lẫn Dự án (3 cặp cùng đang mở) và 1 chặng đang mở ở phòng Kỹ thuật; report ghi số chặng đã chuyển, đã đóng. CSDL production khởi đầu trống (`task_tracker_deploy.md`), nên luật này chỉ chạm dữ liệu demo.
    - Agent không chạy migration lên CSDL nào. Report ghi: người dùng chạy hai migration lên `poscs_db` trước khi kéo code về checkout chính.
  - Xong khi: chỉ còn phòng Tổng hợp nhận bàn giao, hàng chờ bàn giao không hiện trùng hợp đồng; đăng nhập `tonghop1` xem được khách hàng, hợp đồng, sản phẩm, bấm được "Ghi nhận đã thu" và xác nhận bàn giao; mở `/ticket` nhận 403.
  - Nhánh: `feat/task-9.2-phong-va-vai-tong-hop`.
  - Commit: `feat(role): phòng và vai Tổng hợp (Task 9.2)`
  - *Đã hoàn thành:* `db/migrations/V40__phong_va_vai_tong_hop__ndat2003.sql` (Kế toán đổi tên thành Tổng hợp; nhân viên Dự án và mọi chặng bàn giao của Dự án, Kỹ thuật chuyển sang Tổng hợp; chặng trùng đang mở đóng lại, giữ chặng mở sớm nhất; xoá phòng Dự án; thêm vai Tổng hợp), `V41__seed_tonghop_test_account__ndat2003.sql` (`tonghop1` / `Poscs@123`); `db/schema.sql` mirror; `PermissionService` (`ROLE_GENERAL`, `isGeneral`, xem khách hàng/hợp đồng/sản phẩm, `canMarkPaymentPaid`, phạm vi danh sách toàn chi nhánh); `AccessControl` (Tổng hợp không có trong `FULL_ACCESS_ROLES`, comment); `ContractPaymentService`, `ContractPaymentController` (`markPaid` mở cho Tổng hợp, kiểm quyền trước khi đọc ngày thu, Tổng hợp quay về trang chi tiết); `viewcontractdetail.jsp` (nút "Ghi nhận đã thu" kèm modal ngày thu cho vai Tổng hợp); `updatecontract.jsp`, `listcontract.jsp` (chỉ còn phòng Tổng hợp, tích sẵn); `sidebar.jsp` (ẩn Phiếu hỗ trợ); `DashboardService` (Tổng hợp mặc định Toàn chi nhánh); comment `ContractController`, `ContractDAO`; `PERMISSIONS.md` cột Tổng hợp. Form nhân viên không phải sửa (ô Vai trò đọc từ CSDL). Test mới ở 9 lớp unit; sửa 6 test bàn giao trong `ContractStatusIntegrationTest` (người dùng đồng ý). Test unit cũ dùng "Kế toán"/"Dự án" làm dữ liệu mẫu giữ nguyên (đổi phải sửa assertion, rules §3). `ant test` 2.192 test, 0 fail, 3 skipped. QA report tại `docs/qa/Task-9.2.md` (ĐẠT, 1 🟢 đã sửa). Người dùng chạy V40, V41 lên `poscs_db` trước khi kéo code về checkout chính. Ngoài phạm vi, chưa vá: `ContractController.handleCompleteHandover` tin `departmentId` gửi từ form, không đối chiếu phòng thật của chặng.
- [x] **Task 9.3:** Bỏ vai Kỹ thuật; Sales quản lý sản phẩm và là người xử lý phiếu hỗ trợ.
  - UC: `UC-16`, `UC-18`, `UC-19`, `UC-20`, `UC-21`, `UC-22`.
  - BR / MSG / NFR: `BR-39`, `MSG-022`.
  - File sửa:
    - `db/migrations/V{số kế tiếp}__merge_kythuat_role_into_sales__ndat2003.sql` (mới), làm như V37 + V38: chuyển user vai "Kỹ thuật" sang "Sales", phòng "Kỹ thuật" sang "Kinh doanh", rồi xoá vai và phòng "Kỹ thuật" (chặng bàn giao của phòng Kỹ thuật đã chuyển ở Task 9.2). Phiếu hỗ trợ giữ nguyên người xử lý (giờ mang vai Sales).
    - `db/schema.sql`: mirror (seed `roles`, `departments`; tài khoản `kythuat1`–`kythuat5` của V12 thành Sales phòng Kinh doanh, giữ username).
    - Logic vai Kỹ thuật chỉ nằm ở 6 file (đo 19/11/2026), sửa đúng các chỗ này:
      - `src/java/poscs/service/PermissionService.java`: bỏ `ROLE_TECHNICAL` (dòng 34), `isTechnical` (dòng 93). Các hàm xem (`canViewCustomers`, `canViewProducts`, `canViewContracts`, `canViewTickets`) bỏ vế `isTechnical`. `canManageProducts` (dòng 258) thành Admin + Sales. `canUpdateTicket` (dòng 501) bỏ nhánh kỹ thuật viên. `getCustomerListScope`, `getContractListScope` (dòng 593, 610) bỏ vế `isTechnical` (giữ Admin, Tổng hợp của Task 9.2).
      - `src/java/poscs/common/AccessControl.java`: `Resource.PRODUCT` thành `Set.of("Admin", "Sales")` (dòng 41); bỏ `canUpdateAssignedTicket` (dòng 222–245).
      - `src/java/poscs/service/TicketService.java`: `TECHNICIAN_ROLE` (dòng 36) đổi thành hằng vai người xử lý = "Sales"; phạm vi mặc định của danh sách phiếu (dòng 188) luôn "Tất cả".
      - `src/java/poscs/controller/TechnicalSupportTicketController.java`: `TECHNICIAN_ROLE` (dòng 53) thành "Sales" (danh sách người xử lý lấy Sales đang hoạt động); bỏ nhánh `canUpdateAssignedTicket` (dòng 293, 353).
      - `src/java/poscs/controller/TicketListController.java` (dòng 184) và `web/jsp/customersupport/listTicket.jsp` (dòng 259): bỏ `isTechnician`.
    - Nhãn "Kỹ thuật viên"/"KTV" thành "Người xử lý", chỉ ở chữ người dùng nhìn thấy: `web/jsp/customersupport/addnewTicket.jsp`, `listTicket.jsp`, `updateTicket.jsp`, `viewdetailTicket.jsp`; câu chữ trong `TicketExportService.java`, `TicketValidator.java`, `NotificationService.java`, `common/NotificationScheduler.java`, `TicketDetailController.java`, `TicketListController.java`, `TechnicalSupportTicketController.java`.
    - Comment còn tả vai Kỹ thuật như đang tồn tại: sửa câu cho đúng, không đổi code (`EmployeeDAO.java` quanh dòng 284, 363; `ContractHistoryDAO.java` quanh dòng 170; `common/SqlFilters.java` quanh dòng 66; `ContractController.java` quanh dòng 52, 1319; `TicketExportController.java` quanh dòng 28–29; `web/jsp/sale/listcustomer.jsp` quanh dòng 326; `web/jsp/sale/viewcontractdetail.jsp` quanh dòng 781).
    - `PERMISSIONS.md`: bỏ vai Kỹ thuật; Sales thêm Full Sản phẩm.
  - File test: chạy `ant test` sau khi sửa code, **chỉ sửa test đỏ**: dữ liệu mẫu vai Kỹ thuật đổi sang Sales; test quyền riêng của Kỹ thuật (`canUpdateAssignedTicket`, phạm vi "phiếu của tôi" theo kỹ thuật viên) đổi thành test quyền mới của Sales. Test đang dùng "Kỹ thuật" như một vai không có quyền mà vẫn xanh thì **để nguyên** (người dùng chấp nhận 19/11/2026). Thêm test: Sales thêm/sửa/xoá sản phẩm (`ProductControllerTest` hoặc test service sản phẩm đang có); danh sách người xử lý phiếu chỉ gồm Sales đang hoạt động (`TicketServiceTest`). Report liệt kê test đã xoá hoặc đổi nghĩa.
  - Không đụng: vai Tổng hợp (Task 9.2); trang hướng dẫn (Task 10.6); test không đỏ.
  - Lưu ý: làm **sau Task 9.2** (cùng sửa `PermissionService`, `AccessControl`). Agent không chạy migration lên CSDL nào.
  - Xong khi: Sales thêm/sửa/xoá sản phẩm được; tạo phiếu chọn người xử lý trong danh sách Sales; ô Vai trò của form nhân viên không còn Kỹ thuật; `grep -rnE "ROLE_TECHNICAL|isTechnical|isTechnician|canUpdateAssignedTicket" src/java web/jsp` không còn kết quả.
  - Nhánh: `feat/task-9.3-bo-vai-ky-thuat`.
  - Commit: `feat(role): gộp vai Kỹ thuật vào Sales (Task 9.3)`
  - *Đã hoàn thành:* `db/migrations/V42__merge_kythuat_role_into_sales__ndat2003.sql` (vai Kỹ thuật sang Sales, phòng Kỹ thuật sang Kinh doanh, rồi xoá vai và phòng Kỹ thuật; phiếu giữ nguyên người xử lý); `db/schema.sql` mirror (`kythuat2`–`kythuat5` thành Sales phòng Kinh doanh, giữ username); `PermissionService` (bỏ `ROLE_TECHNICAL`, `isTechnical`; `canManageProducts` = Admin + mọi Sales; `canUpdateTicket` = Admin, Sales, giữ chữ ký); `AccessControl` (PRODUCT Full cho Admin, Sales; bỏ `canUpdateAssignedTicket`); `TicketService` (`HANDLER_ROLE` = Sales, bộ lọc người xử lý mặc định "Tất cả"); `TechnicalSupportTicketController` (người xử lý chọn trong Sales, bỏ nhánh kỹ thuật viên, kiểm quyền trước khi tra phiếu ở form sửa và cập nhật); `TicketListController`, `listTicket.jsp` (bỏ `isTechnician`, "Phiếu của tôi" luôn hiện); nhãn "Người xử lý" ở `addnewTicket.jsp`, `updateTicket.jsp`, `viewdetailTicket.jsp`, bản PDF, `TicketValidator`; comment các file ở dòng "File sửa"; `PERMISSIONS.md` bỏ cột Kỹ thuật, Sales Full Sản phẩm. Test: 8 lớp chỉ thay hằng `ROLE_TECHNICAL` bằng chuỗi "Kỹ thuật" (không đổi assertion); test quyền riêng của vai Kỹ thuật đổi thành test quyền mới của Sales / Tổng hợp ở 8 lớp; 9 test mới, không xoá test nào. `ant test` 2.201 test, 0 fail, 3 skipped. QA report tại `docs/qa/Task-9.3.md` (ĐẠT, 2 🟢 đã sửa). `compile-jsps` trên máy này bị bỏ qua (`compile.jsps=false`, JspC không nhận Java 21); QA đã dịch 6 JSP bằng Jasper của Tomcat 11. Người dùng chạy V42 lên `poscs_db` trước khi kéo code về checkout chính. Ngoài phạm vi, chưa dọn: nhánh "không toàn quyền" của `TicketService.updateTicket` và khối `lockGeneral` ở `updateTicket.jsp` không còn ai đi vào; comment cũ nhắc Kỹ thuật ở `TechnicalSupportTicketDAO`, `TechnicalRequest`, `rules.md` §1 và trang hướng dẫn (Task 10.6).

- [x] **Task 9.4:** Dọn phần còn sót sau khi bỏ vai Kỹ thuật: code không còn ai đi vào ở luồng sửa phiếu, comment cũ, `rules.md` §1.
  - UC: `UC-22` (chỉ dọn, không đổi hành vi).
  - BR / MSG / NFR: không thêm, không đổi; `BR-39`, `BR-40` (`MSG-051`), `MSG-022` giữ như hiện nay.
  - File sửa:
    - `src/java/poscs/service/TicketService.java`: sau Task 9.3, ai qua `checkCanUpdateTicket` (quanh dòng 372: người đang hoạt động, vai Admin hoặc Sales) thì `canCreateTicket` cũng qua, nên `fullAccess` (quanh dòng 377) luôn đúng.
      - `updateTicket`: bỏ biến `fullAccess`.
      - `mergeForUpdate` (quanh dòng 407): bỏ tham số `fullAccess` và nhánh `else`; luôn lấy các ô chung từ form như nhánh `if` hiện nay.
      - `validateUpdateRequest` (quanh dòng 451): bỏ tham số `fullAccess`, nhánh gọi `validateTechnicianFields` và dòng `if (!fullAccess) return ok`; luôn chạy `ticketValidator.validateUpdate`, kiểm người xử lý, kiểm khách hàng/hợp đồng như hiện nay.
      - Xoá `validateTechnicianFields` (quanh dòng 473). Giữ `copyGeneralFieldsFromExisting` (còn dùng ở `syncUpdatedFields`).
    - `src/java/poscs/controller/TicketActionController.java` (lối `/ticket/action`; giao diện hiện không gửi tới nhưng servlet vẫn mở): `showEditForm` (quanh dòng 160–169) bỏ dòng đặt `lockGeneral`; `canManage` giữ như cũ.
    - `web/jsp/customersupport/updateTicket.jsp`:
      - Bỏ `<c:set var="lockGeneral" ...>` (dòng 25), các khối `<c:if test="${lockGeneral}">` (quanh dòng 150, 159), mọi `${lockGeneral ? 'disabled' : ''}` trên ô nhập (quanh dòng 164–274), biến JS `lockGeneral` và các nhánh `if (lockGeneral)` (quanh dòng 414–430, 558).
      - Nút hướng dẫn (quanh dòng 127) trỏ thẳng `#sua`.
      - Comment đầu trang (dòng 14–24) và comment quanh dòng 75, 126, 247–258, 280 viết lại cho đúng: chỉ Admin và Sales mở được form, sửa được mọi ô.
    - `web/jsp/customersupport/viewdetailTicket.jsp`: comment quanh dòng 257, 294, 300 bỏ ý "role Kỹ thuật được sửa năm trường"; khối 2 là đánh giá của người xử lý.
    - Comment còn tả vai Kỹ thuật như đang tồn tại, chỉ sửa câu, không đổi code: `src/java/poscs/dao/TechnicalSupportTicketDAO.java` (quanh dòng 259, 283, 335, 465, 487), `src/java/poscs/model/TechnicalRequest.java` (dòng 28).
    - `.gemini/rules.md` §1 (dòng 28): "Chỉ 3 vai trò: Admin, Sales, Kỹ thuật" đổi thành "Admin, Sales, Tổng hợp" (mục 13 "Quyết định đã chốt").
  - File test: chạy `ant test`; không thêm test (không có logic mới). `TicketActionControllerTest` quanh dòng 340 và 372 đang `verify(request).setAttribute(eq("lockGeneral"), eq(false))`: bỏ đúng hai dòng `verify` này, phần còn lại của hai test giữ nguyên (ngoại lệ của tiêu chí chung số 2, người dùng đồng ý 20/11/2026). Test khác đỏ thì dừng hỏi, không sửa assertion.
  - Không đụng:
    - Tên biến, tên cột, tên tham số có chữ "technician" (`assignedTechnicianId`, `assigned_technician_id`, `findActiveTechnicians`, `FIELD_ASSIGNED_TECHNICIAN_ID`, `MSG_TECHNICIAN_REQUIRED`...): đổi tên là refactor lớn, chạm CSDL, form và test.
    - Comment ghi lịch sử kiểu "vai Kỹ thuật cũ đã gộp vào Sales (V42)"; chữ ký `PermissionService.canUpdateTicket(User, Integer)`.
    - Trang hướng dẫn, kể cả mục "Cập nhật xử lý (kỹ thuật viên)" của `web/jsp/guide/ticket.jsp` (Task 10.6).
    - Hai điểm QA Task 9.3 ghi là đổi hành vi: lối `/ticket` chưa kiểm ở server rằng người xử lý phải là Sales đang hoạt động; `TicketActionController` tra phiếu trước khi kiểm quyền. Hai điểm này làm ở Task 9.5.
  - Lưu ý: task dọn, không đổi hành vi, không có migration. Làm trước Task 9.5 (cùng sửa `TicketActionController.java`, `updateTicket.jsp`) và trước Task 10.2 (cùng sửa `TechnicalSupportTicketDAO.java`, `viewdetailTicket.jsp`).
  - Xong khi: `grep -n "lockGeneral\|validateTechnicianFields" src/java/poscs/service/TicketService.java src/java/poscs/controller/TicketActionController.java web/jsp/customersupport/updateTicket.jsp` và `grep -n "fullAccess" src/java/poscs/service/TicketService.java` không còn kết quả; `grep -rnE "Kỹ thuật|kỹ thuật viên" src/java web/jsp/customersupport .gemini/rules.md` chỉ còn tên phiếu "hỗ trợ kỹ thuật" và câu ghi lịch sử; Sales mở form sửa phiếu vẫn sửa được mọi ô như trước.
  - Nhánh: `refactor/task-9.4-don-sau-bo-vai-ky-thuat`.
  - Commit: `refactor(ticket): dọn phần còn sót sau khi bỏ vai Kỹ thuật (Task 9.4)`
  - *Đã hoàn thành:* `TicketService` (bỏ `fullAccess`; `mergeForUpdate`, `validateUpdateRequest` bỏ tham số và nhánh của kỹ thuật viên cũ, luôn lấy các ô chung từ form và kiểm đủ; xoá `validateTechnicianFields`, giữ `copyGeneralFieldsFromExisting`); `TicketActionController` (bỏ `lockGeneral`, giữ `canManage`); `updateTicket.jsp` (bỏ mọi `lockGeneral` ở EL và JS, nút hướng dẫn trỏ `#sua`, viết lại comment: chỉ Admin và Sales mở form, sửa được mọi ô); comment ở `viewdetailTicket.jsp`, `TechnicalSupportTicketDAO` (5 chỗ), `TechnicalRequest` ("kỹ thuật viên" thành "người xử lý"); `.gemini/rules.md` §1 và bản gương `rules.md` (người dùng đồng ý 20/11/2026) đổi thành "Admin, Sales, Tổng hợp". Test: bỏ đúng hai dòng `verify(... "lockGeneral" ...)` ở `TicketActionControllerTest`, không thêm, không xoá test. `ant test` 2.201 test, 0 fail, 3 skipped. `compile-jsps` trên máy này bị bỏ qua (`compile.jsps=false`); đã dịch 2 JSP bằng Jasper của Tomcat 11, 0 lỗi. QA report tại `docs/qa/Task-9.4.md` (ĐẠT, 0 lỗi). Không có migration.
- [x] **Task 9.5:** Phiếu hỗ trợ: kiểm ở server người xử lý phải là Sales đang hoạt động (lối `/ticket`), và kiểm quyền trước khi tra phiếu (lối `/ticket/action`).
  - UC: `UC-21`, `UC-22`.
  - BR / MSG / NFR: `BR-39` (người xử lý chọn trong Sales đang hoạt động), `MSG-022`. Không thêm MSG mới: câu báo lỗi dùng lại `TicketValidator.MSG_TECHNICIAN_REQUIRED` ("Vui lòng chọn người xử lý.").
  - File sửa:
    - `src/java/poscs/service/TicketService.java`: `isAssignedTechnicianValid(int, Integer)` (quanh dòng 490) đổi từ `private` sang `public`, giữ nguyên tên và luật (khác 0; trùng người xử lý đang có thì cho giữ; còn lại phải là user chưa khoá, vai Sales). Javadoc ghi rõ hai lối dùng chung.
    - `src/java/poscs/controller/TechnicalSupportTicketController.java` (lối `/ticket`, giao diện đang gửi tới; hiện ghi thẳng qua `ticketDAO`, không qua `TicketService.createTicket`/`updateTicket`, nên ô người xử lý chỉ được kiểm `> 0` ở `isValid`, quanh dòng 532):
      - `handleCreate` (quanh dòng 304): sau `isValid`, gọi `getTicketService().isAssignedTechnicianValid(t.getAssignedTechnicianId(), null)`; sai thì chuyển về `/ticket?action=new&error=invalid_handler`.
      - `handleUpdate` (quanh dòng 336): như trên nhưng truyền `existing.getAssignedTechnicianId()` làm người được giữ (phiếu cũ mà người xử lý đã nghỉ vẫn lưu được nếu không đổi người); sai thì về `/ticket?action=edit&id=...&error=invalid_handler`.
    - `web/jsp/customersupport/addnewTicket.jsp` (quanh dòng 110–117) và `web/jsp/customersupport/updateTicket.jsp` (khối báo lỗi theo `param.error`, quanh dòng 138–145): thêm nhánh `invalid_handler` hiện "Vui lòng chọn người xử lý."
    - `src/java/poscs/controller/TicketActionController.java` (lối `/ticket/action`): `showEditForm` (quanh dòng 150) và `handleUpdate` (quanh dòng 175) kiểm `canUpdateTicket(currentUser, null)` **trước** khi đọc `id` và gọi `findById` (tham số thứ hai không còn được dùng từ Task 9.3); không có quyền thì 403 `MSG-022` dù `id` có tồn tại hay không, giống cách Task 9.3 đã làm ở `TechnicalSupportTicketController.showEditForm`. Phần sau giữ nguyên.
  - File test:
    - `test/poscs/controller/TechnicalSupportTicketControllerTest.java`: tạo phiếu với người xử lý là Admin, là tài khoản Tổng hợp, là Sales đã khoá, là id không tồn tại → chuyển về `error=invalid_handler`, không gọi `ticketDAO.insert`; sửa phiếu đổi sang người không phải Sales → `error=invalid_handler`, không gọi `ticketDAO.update`; sửa phiếu giữ nguyên người xử lý cũ đã bị khoá → lưu được. Các test tạo/sửa đang dùng người xử lý `50` cần `employeeDAO.findById(50)` trả về Sales đang hoạt động: chỉ thêm vào phần dựng dữ liệu (`setUp` hoặc hàm dựng chung), không sửa assertion; report liệt kê.
    - `test/poscs/controller/TicketActionControllerTest.java`: vai không có quyền mở form sửa / gửi cập nhật với `id` không tồn tại → 403, không gọi `findById`.
    - `test/poscs/service/TicketServiceTest.java`: gọi thẳng `isAssignedTechnicianValid` (Sales đang hoạt động → đúng; Admin, Tổng hợp, đã khoá, 0 → sai; trùng người đang giữ → đúng).
  - Không đụng: dropdown người xử lý (`findActiveTechnicians`, đã đúng); tên biến, tên cột có chữ "technician" (xem Task 9.4); `TicketService.updateTicket`/`createTicket` (đã kiểm sẵn); có giữ servlet `/ticket/action` hay không (giữ nguyên, chỉ sửa thứ tự kiểm).
  - Lưu ý: làm **sau Task 9.4** (cùng sửa `TicketActionController.java`, `updateTicket.jsp`, `TicketService.java`). Đây là sửa lỗi, có đổi hành vi với POST tự dựng; giao diện bình thường không đổi vì dropdown đã chỉ có Sales đang hoạt động. Không có migration.
  - Xong khi: POST tự dựng tới `/ticket` giao phiếu cho Admin hoặc `tonghop1` bị từ chối với câu "Vui lòng chọn người xử lý."; sửa phiếu cũ giữ nguyên người xử lý đã nghỉ vẫn lưu được; vai không có quyền gọi `/ticket/action?action=edit&id=<id không tồn tại>` nhận 403 chứ không bị chuyển về "không tìm thấy".
  - Nhánh: `fix/task-9.5-kiem-nguoi-xu-ly-phieu`.
  - Commit: `fix(ticket): kiểm người xử lý ở server và kiểm quyền trước khi tra phiếu (Task 9.5)`
  - *Đã hoàn thành:* `TicketService` (`isAssignedTechnicianValid` mở `public`, giữ tên và luật, Javadoc ghi hai lối dùng chung); `TechnicalSupportTicketController` (`handleCreate`, `handleUpdate` kiểm người xử lý ngay sau `isValid`, sai thì về form với `error=invalid_handler`; giữ người xử lý cũ đã nghỉ vẫn lưu được); `TicketActionController` (`showEditForm`, `handleUpdate` kiểm `canUpdateTicket(currentUser, null)` trước khi đọc id và tra phiếu; bỏ lần kiểm thứ hai sau bước tra phiếu vì người xử lý không còn quyết định quyền, người dùng đồng ý 20/11/2026); `addnewTicket.jsp`, `updateTicket.jsp` (nhánh `invalid_handler` hiện "Vui lòng chọn người xử lý."). Test: 13 test mới (`TechnicalSupportTicketControllerTest` 9, `TicketActionControllerTest` 2, `TicketServiceTest` 2); `setUp` của `TechnicalSupportTicketControllerTest` thêm `employeeDAO.findById(50)` trả về Sales đang hoạt động cho 4 test tạo phiếu cũ, không sửa assertion, không xoá test. `ant test` 2.214 test, 0 fail, 3 skipped. `compile-jsps` trên máy này bị bỏ qua (`compile.jsps=false`); đã dịch 2 JSP bằng Jasper của Tomcat 11, 0 lỗi. QA report tại `docs/qa/Task-9.5.md` (ĐẠT, 0 lỗi). Không có migration.

---

## Giai đoạn 10: Vòng đời hợp đồng: chứng từ trước khi ký, giấy tờ, kỳ thanh toán

> Yêu cầu người dùng 19/11/2026, quyết định ở mục 14 và 15 "Quyết định đã chốt" (bản rút gọn chốt cùng ngày: POSCS chỉ để theo dõi hợp đồng, nhưng vòng đời phải khớp cách chi nhánh làm thật). Làm **sau Giai đoạn 9** (Task 10.4 giữ nút "Đã thu" của vai Tổng hợp từ Task 9.2), theo thứ tự 10.1 → 10.9 (Task 10.6 thêm ngày 20/11/2026: dọn dữ liệu mẫu cũ, gieo bộ mới; hướng dẫn sử dụng lùi thành Task 10.7. Task 10.8 thêm ngày 21/11/2026: dọn tài liệu lỗi thời, làm sau khi Task 10.7 merge. Task 10.9 thêm cùng ngày: README gọn bằng tiếng Việt, làm sau Task 10.8). Mẫu dựng theo hợp đồng thật 01/2026/HĐMB/QTI-POT/SMP (Viễn thông Quảng Trị, Điều 5 "Phương thức thanh toán").

- [x] **Task 10.1:** Đặc tả trong `docs/SPEC.md`: vòng đời hợp đồng theo chi nhánh, mã để trống tới lúc Ký, giấy tờ dạng ô tích, kỳ thanh toán có nộp hồ sơ và số thực thu, bước giao hàng và nhắc ký.
  - UC: sửa `UC-10`, `UC-11`, `UC-33`, `UC-44`, `UC-52` (đổi tên thành "Quản lý giấy tờ của hợp đồng"), `UC-53`; §5.7 thêm loại thông báo thứ ba.
  - BR / MSG / NFR: sửa `BR-35`, `BR-18`; thêm BR và MSG mới, **đánh số tiếp sau số lớn nhất đang có trong SPEC**, ghi đúng nội dung:
    - BR "Mã hợp đồng": không bắt buộc khi còn Nháp; bắt buộc và duy nhất trước khi Ký (UC-44). Chưa có mã thì mọi chỗ hiển thị ghi "Chưa có số".
    - BR "Giấy tờ của hợp đồng" (UC-52):
      - Một danh sách chung cho cả hợp đồng. Mỗi tờ có: loại, ngày trên giấy (bắt buộc, không sau hôm nay), số giấy, link (không bắt buộc), ghi chú; loại "Khác" thì bắt nhập tên.
      - Danh mục: Phiếu GQNCKH, Phiếu ĐCNB, Biên bản giao nhận hàng hoá, Hợp đồng đã ký, Phụ lục đã ký, Đề nghị tạm ứng, Bảo lãnh tạm ứng, Biên bản nghiệm thu bàn giao, CO, CQ, Đề nghị thanh toán, Hoá đơn GTGT, Biên bản phạt, Biên bản thanh lý, Khác.
      - Một loại có thể có nhiều tờ. "Biên bản giao nhận hàng hoá" là tờ ký lúc hàng tới khách (có thể trước ngày ký); "Biên bản nghiệm thu bàn giao" là tờ trong hồ sơ thanh toán.
      - Huỷ một tờ (bỏ tích) là huỷ mềm, bắt buộc lý do. Thêm và huỷ đều ghi nhật ký hợp đồng. Làm được ở mọi tiến độ, kể cả sau thanh lý.
    - BR "Kỳ thanh toán" (UC-53):
      - Mỗi kỳ có: loại (Tạm ứng / Thanh toán), số tiền, điều kiện (ghi ngắn), số ngày phải trả kể từ khi nộp hồ sơ (không bắt buộc), hạn thu (để trống được khi lập).
      - "Đã nộp hồ sơ": ghi ngày nộp (không sau hôm nay). Kỳ có số ngày thì hạn thu = ngày nộp + số ngày; không có thì nhập hạn tay.
      - "Đã thu": ngày thu và số tiền thực thu (mặc định bằng số tiền kỳ, lớn hơn 0; khác số tiền kỳ thì không bắt lý do).
      - Tình trạng suy ra, không lưu, xét theo thứ tự: có ngày thu → **Đã thu**; hạn thu trước hôm nay → **Quá hạn**; có hạn thu → **Chờ thu**; còn lại → **Chưa nộp hồ sơ**.
      - Lập, sửa, xoá kỳ chỉ khi kỳ chưa thu và hợp đồng chưa thanh lý/chấm dứt. "Đã nộp hồ sơ" và "Đã thu" bấm được cả sau thanh lý.
      - Form có ô "%" để tính sẵn số tiền từ giá trị hợp đồng; chỉ lưu số tiền. Tạm ứng không thu hồi dần.
    - `BR-18` sửa: doanh thu cộng **số tiền thực thu** theo **ngày thu**; chưa tách riêng tạm ứng.
    - BR "Bước giao hàng" (suy ra, không lưu, chỉ với hợp đồng Nháp): có Phiếu GQNCKH hoặc Phiếu ĐCNB mà chưa có Biên bản giao nhận hàng hoá → "Chuẩn bị hàng"; có Biên bản giao nhận hàng hoá → "Đã giao – chưa ký". Ngày giao = ngày sớm nhất trong các biên bản giao nhận chưa huỷ.
    - BR "Nhắc ký": hợp đồng "Đã giao – chưa ký" quá 15 ngày tính từ ngày giao thì nhắc người phụ trách, một lần.
    - MSG (Nội tuyến, khi bấm Ký): "Hợp đồng chưa có mã. Vui lòng nhập mã hợp đồng trước khi ký."
    - MSG (Nội tuyến): "Vui lòng nhập ngày trên giấy tờ."
    - MSG (Nội tuyến): "Ngày trên giấy tờ không được sau hôm nay."
    - MSG (Nội tuyến): "Ngày nộp hồ sơ không được sau hôm nay."
    - MSG (Thông báo): "Hợp đồng [tên hợp đồng] đã giao hàng [n] ngày nhưng chưa ký."
  - File sửa: `docs/SPEC.md`: §3.3 (dòng "Quản lý tài liệu" thành "Quản lý giấy tờ"; dòng kỳ thanh toán thêm sửa kỳ, nộp hồ sơ), §4.4 (UC-52, UC-53), §5.4 (các đoạn Tạo / Ký / Danh sách / Chi tiết; thêm đoạn vòng đời theo mục 14, đoạn giấy tờ và đoạn kỳ thanh toán), §5.7, §6, §7 (cột mới của `contract_documents`, `contract_payments`; `contracts.contract_code` cho phép NULL), §11 (thêm quyết định tóm tắt mục 14 và 15 của file này).
  - File test: không có (chỉ tài liệu); chạy `ant test`.
  - Không đụng: code, `db/`.
  - Lưu ý: Task 8.5 phải merge trước (cùng sửa `BR-35`); chưa merge thì dừng hỏi. Các task sau dùng đúng tên loại giấy, tên tình trạng kỳ ở trên.
  - Xong khi: SPEC đủ các mục trên; số đếm trong tiêu đề (số BR, số bảng) khớp nội dung; không còn chỗ nào nói tài liệu "chỉ gắn đường dẫn" là bắt buộc.
  - Nhánh: `docs/task-10.1-spec-vong-doi-giay-to-ky-thanh-toan`.
  - Commit: `docs(spec): vòng đời hợp đồng, giấy tờ và kỳ thanh toán (Task 10.1)`
  - *Đã hoàn thành:* `docs/SPEC.md`: thêm BR-58 → BR-62 (Mã hợp đồng, Giấy tờ của hợp đồng, Kỳ thanh toán, Bước giao hàng, Nhắc ký) và MSG-061 → MSG-065 (§9 thêm loại "Thông báo"); sửa BR-18, BR-35; §3.1, §3.3 (Quản lý giấy tờ; lập / sửa / xoá kỳ, ghi nhận đã nộp hồ sơ; đã thu kèm số tiền thực thu); §4.4 UC-10, UC-11, UC-33, UC-44, UC-52 (đổi tên "Quản lý giấy tờ của hợp đồng"), UC-53; §4.6 UC-36 và dòng `NotificationScheduler`; §5.4 thêm đoạn Vòng đời, Ký, Giấy tờ, Kỳ thanh toán, sửa Tạo / Tiến độ / Cập nhật / Danh sách / Chi tiết; §5.7 loại thông báo thứ ba (nhắc ký, `ref_type` riêng); §7 cột mới của `contracts`, `contract_documents`, `contract_payments` (vẫn 30 bảng); §10 mã hợp đồng; §11 sửa quyết định 7, thêm 14 và 15. Người dùng chốt thêm 20/11/2026 (ghi ở mục 15): hạn thu không bắt buộc khi nộp hồ sơ kỳ không có số ngày; kỳ đã nộp hồ sơ mà chưa có hạn thu là Chờ thu. Mốc nhắc ký ghi rõ "ngày giao ≤ hôm nay − 15 ngày". `ant test` 2.214 test, 0 fail, 3 skipped. QA report tại `docs/qa/Task-10.1.md` (APPROVED, 2 🟢 đã sửa). Lệch cũ ngoài phạm vi, chưa sửa: §5.7 ghi `ref_type` "CONTRACT" / "TICKET" trong khi code dùng `contract_expiring` / `ticket_sla`; §1 vẫn ghi "(chỉ theo dõi kỳ thanh toán: số tiền, hạn thu, đã thu)".
- [x] **Task 10.2:** Mã hợp đồng để trống khi còn Nháp, bắt buộc khi Ký.
  - UC: `UC-10`, `UC-11`, `UC-33`, `UC-35`, `UC-44`, `UC-46`.
  - BR / MSG / NFR: BR "Mã hợp đồng" và MSG khi bấm Ký của Task 10.1.
  - File sửa:
    - `db/migrations/V{số kế tiếp}__contract_code_nullable__ndat2003.sql` (mới): `contracts.contract_code` cho phép NULL (giữ UNIQUE; MySQL cho nhiều dòng NULL); chuỗi rỗng đang có (nếu có) đổi thành NULL. `db/schema.sql`: mirror.
    - `src/java/poscs/model/Contract.java`: thêm `getDisplayCode()` trả mã, hoặc "Chưa có số" khi mã trống; thêm `getParentDisplayCode()` làm tương tự cho `parentContractCode` (mã hợp đồng gốc của phụ lục).
    - `src/java/poscs/validator/ContractValidator.java`: mã không bắt buộc khi tạo/sửa bản Nháp (có thì vẫn kiểm độ dài); `validateSigning` bắt có mã.
    - `src/java/poscs/service/ContractService.java`, `src/java/poscs/controller/ContractController.java`, `ContractActionController.java`: tạo/sửa không mã thì không kiểm trùng; Ký thì kiểm có mã và không trùng.
    - `src/java/poscs/dao/ContractDAO.java`: `contractCodeExists` bỏ qua mã trống; ghi NULL thay vì chuỗi rỗng.
    - Mọi chỗ hiển thị/ghi mã dùng `displayCode` hoặc xử lý NULL, không in "null": `ContractListController.java`, `ContractHandoverDAO.java`, `ContractLinkDAO.java`, `ProductDAO.java`, `TechnicalSupportTicketDAO.java`, `model/ContractHandover.java`, `ContractExportService.java`, `NotificationService.java`, `TicketExportService.java`; JSP `web/jsp/sale/addnewcontract.jsp`, `updatecontract.jsp`, `viewcontractdetail.jsp`, `handoverqueue.jsp`, `viewcustomerdetail.jsp`, `web/jsp/customersupport/listTicket.jsp`, `viewdetailTicket.jsp`, `web/jsp/technical/viewdetailProduct.jsp`, `web/dashboard.jsp` (quanh dòng 533: mã là chữ của đường link, mã trống thì link biến mất), `web/jsp/sale/listcontract.jsp` (quanh dòng 633: "PL của" + mã gốc, dùng `parentDisplayCode`). Mã gốc của phụ lục (`parentContractCode`) ở `updatecontract.jsp`, `viewcontractdetail.jsp`, `ContractExportService.java` cũng dùng `parentDisplayCode`.
    - Form tạo/sửa: ô Mã hợp đồng bỏ dấu `*` khi còn Nháp, ghi chú "Để trống nếu chưa có số; bắt buộc trước khi ký".
  - File test: `ContractValidatorTest` (Nháp không mã hợp lệ; Ký không mã bị chặn); `ContractServiceTest`, `ContractControllerTest`, `ContractActionControllerTest` (tạo không mã; hai hợp đồng cùng không mã không bị coi là trùng; Ký không mã bị chặn); `ContractDAOTest` (NULL); `ContractExportServiceTest`, `NotificationServiceTest` (mã NULL).
  - Không đụng: giấy tờ (Task 10.3), kỳ thanh toán (Task 10.4).
  - Lưu ý: rà bằng `grep -rn "getContractCode\|contract_code\|contractCode" src/java web/jsp`; chỗ nào nối chuỗi hoặc so sánh mã phải chịu được NULL. Hợp đồng không mã vẫn tìm được theo tiêu đề. Agent không chạy migration lên CSDL nào.
  - Xong khi: tạo được hợp đồng không mã; danh sách, chi tiết, Excel, hàng chờ bàn giao, dashboard hiện "Chưa có số" (phụ lục của hợp đồng gốc chưa có mã hiện "PL của Chưa có số"); bấm Ký khi chưa có mã thì hiện MSG mới.
  - Nhánh: `feat/task-10.2-ma-hop-dong-de-trong`.
  - Commit: `feat(contract): mã hợp đồng để trống tới lúc ký (Task 10.2)`
  - *Đã hoàn thành:* `db/migrations/V43__contract_code_nullable__ndat2003.sql` (mới) + `db/schema.sql` (`contract_code` cho phép NULL, giữ UNIQUE; chuỗi rỗng đổi thành NULL). `Contract` (`getDisplayCode`, `getParentDisplayCode`, `hasContractCode`, `displayCodeOf`), `ContractHandover.getDisplayCode`. `ContractValidator` (`validateRequiredFields(c, codeRequired)`: Nháp không bắt mã, có nhập vẫn kiểm độ dài; `validateSigning` báo `MSG_061`). `ContractService` (`ERR_MISSING_CODE`; `checkSignable` kiểm theo thứ tự chưa có mã → mã trùng → thời hạn; không mã thì không kiểm trùng; mã toàn khoảng trắng ghi NULL). `ContractController`, `ContractActionController` (`signBlocker` báo `missing_code` / `duplicate_code` / `missing_term`; chữa sai sót UC-54 vẫn bắt có mã), `ContractActionResultHelper` (ánh xạ `missing_code`, `duplicate_code` khi service từ chối Ký; ngoài dòng File sửa, người dùng đồng ý 20/11/2026). `ContractDAO` (ghi NULL; chặn Ký trong giao dịch khi chưa có mã; `correct` không xoá trắng mã; nhật ký ghi "Chưa có số"). `ContractLinkDAO`, `TechnicalSupportTicketDAO` (phiếu gắn hợp đồng chưa có mã vẫn giữ hợp đồng), `ContractListController`, `ContractExportService`, `TicketExportService` hiện "Chưa có số". 10 JSP (`addnewcontract`, `updatecontract`, `viewcontractdetail`, `handoverqueue`, `viewcustomerdetail`, `listcontract`, `listTicket`, `viewdetailTicket`, `viewdetailProduct`, `dashboard`): ô Mã bỏ `*` ở bản Nháp kèm ghi chú, dòng MSG-061 dưới nút Ký, băng báo `missing_code` / `duplicate_code` ở trang chi tiết. `ContractHandoverDAO`, `ProductDAO`, `NotificationService` không phải sửa (đã chịu được NULL). Test: thêm test ở 7 lớp của dòng File test và 4 lớp ngoài danh sách (`TechnicalSupportTicketDAOTest`, `ContractListControllerTest`, `ContractLinkDAOTest`, `TicketExportServiceTest`; người dùng đồng ý 20/11/2026); 6 test cũ sửa theo BR-58 (4 test chỉ thêm mã vào dữ liệu dựng, 2 test sửa đúng dòng assertion; người dùng đồng ý 20/11/2026), không xoá test nào. `ant test` 2.269 test (2.266 pass, 0 fail, 3 skipped); `compile-jsps` trên máy này bị bỏ qua (`compile.jsps=false`), đã dịch 10 JSP bằng Jasper của Tomcat 11, 0 lỗi. QA report tại `docs/qa/Task-10.2.md` (APPROVED; 3 🟡 và 1 🟢 đã sửa, 1 🟢 về độ dài `ContractActionController.java` giữ nguyên kèm lý do). Người dùng tự chạy V43 lên `poscs_db` trước khi kéo code về checkout chính.
- [x] **Task 10.3:** Giấy tờ dạng ô tích, làm trên mục Tài liệu sẵn có.
  - UC: `UC-52` (tên mới), `UC-11`.
  - BR / MSG / NFR: BR "Giấy tờ của hợp đồng" và hai MSG về ngày giấy tờ của Task 10.1.
  - File sửa:
    - `db/migrations/V{số kế tiếp}__contract_documents_date_and_number__ndat2003.sql` (mới):
      - `contract_documents` thêm `doc_date` date (dòng cũ điền ngày của `uploaded_at`, rồi đặt NOT NULL) và `doc_no` varchar(50) NULL; `file_url` cho phép NULL.
      - Đổi `doc_type` các dòng cũ sang danh mục mới: "Biên bản nghiệm thu" → "Biên bản nghiệm thu bàn giao"; "Hoá đơn" → "Hoá đơn GTGT"; "Biên bản bàn giao", "Báo giá / Đơn đặt hàng" → "Khác" (tên cũ chép vào `title` nếu `title` đang trống). Loại còn lại giữ nguyên.
      - `db/schema.sql`: mirror.
    - `src/java/poscs/model/ContractDocument.java`: `TYPES` (dòng 28) theo danh mục của BR, "Khác" đứng cuối; thêm trường `docDate`, `docNo`; sửa comment "danh mục còn phải chốt" thành nguồn mới (mục 15).
    - `src/java/poscs/service/ContractDocumentService.java` (`validateDocumentInput`, quanh dòng 128): link không bắt buộc (có thì vẫn kiểm `TextRules.isSafeHttpUrl` và độ dài); bắt `docDate`, không sau hôm nay; `docNo` tối đa 50 ký tự; loại "Khác" bắt nhập tên (`title`).
    - `src/java/poscs/dao/ContractDocumentDAO.java`: ghi/đọc hai cột mới (`addDocument`, `insertDocumentRow`, `findDocumentsOf`, `mapResultSetToActiveDocument`); dòng nhật ký có ngày và số giấy.
    - `src/java/poscs/controller/ContractDocumentController.java` (`/contract/document`): đọc `docDate`, `docNo`; báo lỗi nội tuyến theo hai MSG mới.
    - `web/jsp/sale/updatecontract.jsp`:
      - Tab Tài liệu (quanh dòng 617 nút tab, 1100–1205 nội dung, JS huỷ quanh dòng 1621) đổi tên thành "Giấy tờ".
      - Hiện đủ danh mục dạng ô tích; loại đã có tờ thì có dấu tích kèm từng tờ (ngày, số, link nếu có, nút huỷ). Bấm ô trống hoặc "Thêm tờ" thì mở form nhỏ nhập ngày, số, link, ghi chú.
      - Huỷ dùng modal `ask()` có ô lý do như hiện nay.
      - Câu nhắc tới tab Tài liệu ở đầu trang (quanh dòng 272, 497) sửa cho khớp.
    - `web/jsp/sale/viewcontractdetail.jsp` (nút tab quanh dòng 752, nội dung quanh dòng 1129): cùng danh mục dạng ô tích, chỉ đọc, có link thì có nút mở.
  - File test: `ContractDocumentServiceTest` (không ngày → lỗi; ngày tương lai → lỗi; không link → hợp lệ; link sai → lỗi; "Khác" không tên → lỗi; hai tờ cùng loại → hợp lệ); `ContractDocumentControllerTest`; `ContractDocumentDAOTest` (hai cột mới, `file_url` NULL).
  - Không đụng: kỳ thanh toán (Task 10.4), nhãn giao hàng (Task 10.5); phân quyền (giữ `PermissionService.canManageContractDocuments`). Không tạo bảng, DAO, service, controller mới.
  - Lưu ý: 2 tài liệu cũ trên `poscs_db`, trong đó có **link demo của HD-0015 người dùng dặn giữ**, phải còn nguyên và hiện đúng loại. Agent không chạy migration lên CSDL nào; report ghi: người dùng chạy migration lên `poscs_db` trước khi kéo code về checkout chính.
  - Xong khi: tab Giấy tờ hiện đủ danh mục dạng ô tích; thêm được tờ không có link; một loại có nhiều tờ; huỷ bắt lý do và có dòng nhật ký.
  - Nhánh: `feat/task-10.3-giay-to-o-tich`.
  - Commit: `feat(contract): giấy tờ dạng ô tích trên mục tài liệu (Task 10.3)`
  - *Đã hoàn thành:* `db/migrations/V44__contract_documents_date_and_number__ndat2003.sql` (mới) + `db/schema.sql` (`doc_date` NOT NULL, dòng cũ lấy ngày của `uploaded_at`; `doc_no` varchar(50); `file_url` cho phép NULL; loại cũ đổi sang danh mục BR-59, "Biên bản bàn giao" và "Báo giá / Đơn đặt hàng" thành "Khác" kèm tên cũ ở `title`; link demo HD-0015 giữ nguyên, dòng gieo trong `schema.sql` thêm `doc_date`). `ContractDocument` (`TYPES` 15 loại theo BR-59, `TYPE_OTHER`, `docDate`, `docNo`, `getCategory`). `ContractDocumentService` (link không bắt buộc; `ERR_DATE_REQUIRED` MSG-062, `ERR_DATE_FUTURE` MSG-063, `ERR_TITLE_REQUIRED` cho loại "Khác"; `docNo` tối đa 50; thêm constructor nhận `Clock`). `ContractDocumentDAO` (ghi/đọc `doc_date`, `doc_no`; nhật ký thêm và bỏ tích có "(số …, ngày dd/MM/yyyy)"). `ContractDocumentController` (đọc `docDate`, `docNo`; `errorParamOf`). `updatecontract.jsp` (tab "Giấy tờ" dạng ô tích, modal "Thêm tờ" kiểm MSG-062/063 tại chỗ, bỏ tích bằng `ask()` bắt lý do, câu báo lỗi mới ở băng lỗi, câu nhắc đầu trang), `viewcontractdetail.jsp` (cùng danh mục, chỉ đọc). Ngoài dòng File sửa (người dùng đồng ý 20/11/2026): `ContractControllerHelper` (nút "Mở bản PDF" bỏ qua tờ không có link) kèm 2 test ở `ContractDetailControllerTest`; 2 test tích hợp ở `ContractStatusIntegrationTest` thêm ngày trên giấy. Test: thêm test ở `ContractDocumentServiceTest`, `ContractDocumentControllerTest`, `ContractDocumentDAOTest`; 14 test cũ đỏ vì BR-59 sửa theo đồng ý của người dùng (10 test chỉ thêm dữ liệu dựng, 4 dòng assertion trái BR-59), 6 test `voidDocument_*` chỉ đổi chuỗi stub SQL; không xoá, không đổi tên test nào. `ant test` 2.303 test (2.300 pass, 0 fail, 3 skipped); `compile-jsps` trên máy này bị bỏ qua (`compile.jsps=false`), đã dịch 2 JSP bằng Jasper của Tomcat 11, 0 lỗi. QA report tại `docs/qa/Task-10.3.md` (CẦN SỬA; 3 🟡 và 1 🟢 đã sửa, 1 🟢 về tên hai test `_invalid` giữ nguyên kèm lý do). Người dùng chạy V44 lên `poscs_db` trước khi kéo code về checkout chính. Chưa theo: `tools/testdoc/run_blackbox.py` (TC_CTRADD_011 chưa gửi `docDate`).
- [x] **Task 10.4:** Kỳ thanh toán: loại kỳ, điều kiện, nộp hồ sơ, hạn thu, số thực thu, sửa kỳ.
  - UC: `UC-53`; dashboard (`UC-36`).
  - BR / MSG / NFR: BR "Kỳ thanh toán", `BR-18` (sửa) và MSG ngày nộp hồ sơ của Task 10.1.
  - File sửa:
    - `db/migrations/V{số kế tiếp}__payment_type_submission_paid_amount__ndat2003.sql` (mới): `contract_payments` thêm `payment_type` varchar(20) NOT NULL DEFAULT 'Thanh toán', `condition_note` varchar(255) NULL, `payment_days` int NULL, `submitted_date` date NULL, `paid_amount` decimal(15,2) NULL; `due_date` cho phép NULL. Kỳ đã thu cũ: `paid_amount` = `invoice_amount`. `db/schema.sql`: mirror.
    - `src/java/poscs/model/ContractPayment.java`: trường mới; hàm trả tình trạng suy ra theo BR (Đã thu / Quá hạn / Chờ thu / Chưa nộp hồ sơ).
    - `src/java/poscs/service/ContractPaymentService.java`:
      - `addPayment`: nhận loại, điều kiện, số ngày; hạn thu không còn bắt buộc (bỏ kiểm ở `validatePaymentInput`, quanh dòng 96).
      - Thêm `updatePayment` (kỳ chưa thu, hợp đồng chưa thanh lý/chấm dứt) và `markSubmitted` (ngày nộp không sau hôm nay; tính hạn thu theo BR).
      - `markPaymentPaid` (quanh dòng 108) nhận thêm số thực thu: mặc định bằng số tiền kỳ, lớn hơn 0, không vượt `MAX_PAYMENT_AMOUNT`. Giữ luật "Đã thu" được cả sau thanh lý.
    - `src/java/poscs/dao/ContractPaymentDAO.java`: đọc/ghi cột mới; thêm hàm sửa kỳ và ghi ngày nộp, ghi nhật ký như `markPaymentPaid`; `sumInvoiceAmountInPeriod` (quanh dòng 268) và `sumInvoiceAmountByMonth` (quanh dòng 316) cộng `COALESCE(paid_amount, invoice_amount)`.
    - `src/java/poscs/controller/ContractPaymentController.java` (`/contract/payment`): thêm hành động sửa kỳ và "Đã nộp hồ sơ"; `markPaid` đọc số thực thu. Lập, sửa, xoá, nộp hồ sơ theo quyền quản lý kỳ hiện tại; "Đã thu" giữ thêm vai Tổng hợp (Task 9.2).
    - `src/java/poscs/controller/ContractController.java`: form ở trang Sửa và trang chi tiết đều gửi về `/contract`, không gửi thẳng `/contract/payment`. Bảng chia việc (quanh dòng 322–330, đang có `addPayment`, `markPaid`, `removePayment`) thêm hai hành động sửa kỳ và "Đã nộp hồ sơ", chuyển sang `getPaymentController()` như `handleMarkPaid` (quanh dòng 1291). Tên hành động dùng chung với `ContractPaymentController`.
    - `web/jsp/sale/updatecontract.jsp` (khối Kỳ thanh toán quanh dòng 717–780, JS `markPaid` quanh dòng 1595):
      - Bảng kỳ thêm cột loại, điều kiện, hạn thu, tình trạng (Quá hạn tô đỏ).
      - Form lập/sửa kỳ có ô "%" tính sẵn số tiền từ giá trị hợp đồng (chỉ JS, lưu số tiền).
      - Nút "Đã nộp hồ sơ" (modal nhập ngày nộp; kỳ không có số ngày thì nhập thêm hạn thu) và "Đã thu" (modal nhập ngày thu, số thực thu điền sẵn).
    - `web/jsp/sale/viewcontractdetail.jsp` (bảng kỳ quanh dòng 829–930): thêm cột loại, hạn thu, tình trạng; hiện số thực thu khi khác số tiền kỳ; giữ nút "Đã thu" cho vai Tổng hợp. Modal "Ghi nhận đã thu" của vai Tổng hợp (quanh dòng 1339, hiện chỉ có ô Ngày thu) thêm ô số thực thu điền sẵn số tiền kỳ, giống modal ở trang Sửa (mục 15 "Quyết định đã chốt").
    - `src/java/poscs/service/DashboardService.java`: doanh thu theo số thực thu (qua DAO).
  - File test: `ContractPaymentServiceTest` (lập kỳ không hạn thu; sửa kỳ đã thu bị chặn; nộp hồ sơ tính đúng hạn; ngày nộp tương lai bị chặn; số thực thu mặc định; số thực thu ≤ 0 bị chặn; sau thanh lý vẫn "Đã thu" và "Đã nộp hồ sơ" được, không lập được; bốn tình trạng suy ra đúng; kỳ không có số ngày nộp hồ sơ bỏ trống hạn thu → hợp lệ, tình trạng Chờ thu); `ContractPaymentControllerTest` (vai Tổng hợp "Đã thu" kèm số thực thu khác số tiền kỳ → lưu đúng số thực thu, quay về trang chi tiết); `ContractControllerTest` (sửa kỳ và "Đã nộp hồ sơ" gửi qua `/contract` tới được `ContractPaymentController`); `ContractPaymentDAOTest` (cột mới; tổng doanh thu theo số thực thu); `DashboardServiceTest`.
  - Không đụng: giấy tờ (Task 10.3); giá trị hợp đồng và phụ lục (số tiền kỳ không tự tính lại).
  - Lưu ý: hộp thoại dùng modal trong trang (hàm `ask()` ở `updatecontract.jsp`), không dùng `confirm`/`prompt` của trình duyệt. 24 kỳ cũ của 16 hợp đồng trên `poscs_db` (11 kỳ đã thu) phải hiện đúng số tiền, tình trạng; dashboard không đổi số. Agent không chạy migration lên CSDL nào.
  - Xong khi: nhập thử hợp đồng Quảng Trị (563.160.600đ): lập hai kỳ 80% / 20% bằng ô "%" ra 450.528.480đ và 112.632.120đ, điều kiện và 7 ngày; kỳ 2 "Đã nộp hồ sơ" ngày X → hạn thu X + 7 ngày, tình trạng Chờ thu; quá hạn thì hiện Quá hạn; "Đã thu" với số thực thu thấp hơn thì dashboard cộng đúng số thực thu; đăng nhập `tonghop1` ghi nhận đã thu ở trang chi tiết nhập được số thực thu.
  - Nhánh: `feat/task-10.4-ky-thanh-toan`.
  - Commit: `feat(payment): loại kỳ, nộp hồ sơ, hạn thu và số thực thu (Task 10.4)`
  - *Đã hoàn thành:* `db/migrations/V45__payment_type_submission_paid_amount__ndat2003.sql` (mới) + `db/schema.sql` (`contract_payments` thêm `payment_type`, `condition_note`, `payment_days`, `submitted_date`, `paid_amount`; `due_date` cho phép NULL; kỳ đã thu cũ có `paid_amount` = `invoice_amount`, dòng gieo mẫu cũng vậy). `ContractPayment` (`TYPES`, `statusOn`/`getStatus` bốn tình trạng theo BR-60, `getCollectedAmount`, `isPaidAmountDifferent`). `ContractPaymentService` (hạn thu không bắt buộc; kiểm loại kỳ, điều kiện ≤ 255 ký tự, số ngày 1–999; `updatePayment`; `markSubmitted` với `MSG_064`, hạn thu = ngày nộp + số ngày, kỳ không có số ngày nhận hạn tay hoặc để trống; `markPaymentPaid` nhận số thực thu; thêm constructor nhận `Clock`). `ContractPaymentDAO` (đọc/ghi cột mới; `updatePayment`, `markSubmitted`, `markPaymentPaid` kèm số thực thu, ghi nhật ký trong cùng giao dịch; doanh thu cộng `COALESCE(paid_amount, invoice_amount)`; danh sách kỳ sắp theo thứ tự lập, người dùng chốt 20/11/2026). `ContractPaymentController` (hành động `updatePayment`, `markSubmitted`; kiểm quyền trước khi đọc form; ngày sai định dạng bị chặn; đọc `paidAmount`), `ContractController` (chuyển hai hành động mới), `DashboardService` (comment). `updatecontract.jsp` (bảng kỳ thêm loại, điều kiện, hạn thu, tình trạng, Quá hạn tô đỏ; modal Lập/Sửa kỳ có ô "%", modal "Đã nộp hồ sơ", modal "Đã thu" có số thực thu), `viewcontractdetail.jsp` (cùng các cột; modal của vai Tổng hợp thêm ô số thực thu). Ngoài dòng File sửa (người dùng đồng ý 20/11/2026): `ContractControllerHelper` (ô "Đã thu" cộng số thực thu, "Còn phải thu" là các kỳ chưa thu, attribute `paymentTypes`), `ContractHistory` (hai loại nhật ký "Sửa kỳ thanh toán", "Nộp hồ sơ thanh toán"); test kèm ở `ContractDetailControllerTest` (đồng ý 21/11/2026). Test: thêm test ở `ContractPaymentServiceTest`, `ContractPaymentControllerTest`, `ContractPaymentDAOTest`, `ContractControllerTest`, `DashboardServiceTest`; 3 đoạn assertion test cũ sửa theo BR-60 và thứ tự kỳ, `ContractStatusIntegrationTest` sửa theo BR-60 (người dùng đồng ý 21/11/2026; test tích hợp chưa chạy trên CSDL `_it`); không xoá, không đổi tên test nào. `ant test` 2.381 test (2.378 pass, 0 fail, 3 skipped); `compile-jsps` trên máy này bị bỏ qua (`compile.jsps=false`), đã dịch 2 JSP bằng Jasper của Tomcat 11, 0 lỗi. Chạy thử trên Tomcat riêng với CSDL tạm dựng từ `main` + V45 (đã xoá sau khi thử): đạt toàn bộ dòng Xong khi. QA report tại `docs/qa/Task-10.4.md` (CẦN SỬA; 4 🟡 và 2 🟢 đã sửa). `poscs_db` trên máy dev mới tới V39: người dùng chạy V40 → V45 trước khi kéo code về checkout chính.
- [x] **Task 10.5:** Bước giao hàng ("Chuẩn bị hàng" / "Đã giao – chưa ký") và nhắc ký sau 15 ngày.
  - UC: `UC-10`, `UC-11`; dashboard (`UC-36`); §5.7 (thông báo).
  - BR / MSG / NFR: BR "Bước giao hàng", BR "Nhắc ký" và MSG thông báo của Task 10.1.
  - File sửa:
    - `src/java/poscs/dao/ContractDAO.java`: tính bước giao hàng cho hợp đồng Nháp từ `contract_documents` chưa huỷ (Phiếu GQNCKH, Phiếu ĐCNB, Biên bản giao nhận hàng hoá) trong câu SELECT danh sách và chi tiết; điều kiện lọc theo bước; hàm đếm cho dashboard; truy vấn hợp đồng Nháp có ngày giao ≤ hôm nay − 15 ngày.
    - `src/java/poscs/model/Contract.java`: trường `deliveryStage`, `deliveredDate`.
    - `src/java/poscs/controller/ContractListController.java`, `ContractFilterHelper.java`: tham số lọc `delivery`, giữ khi phân trang và khi xuất Excel.
    - `web/jsp/sale/listcontract.jsp`: nhãn cạnh tiến độ, ô lọc. `web/jsp/sale/viewcontractdetail.jsp`: nhãn, ngày giao, số ngày đã giao.
    - `src/java/poscs/service/DashboardService.java` và `web/dashboard.jsp`: ô "Đã giao – chưa ký" (đếm theo phạm vi đang chọn), bấm vào mở danh sách đã lọc.
    - `src/java/poscs/service/NotificationService.java`: hằng `REF_CONTRACT_UNSIGNED_DELIVERY`, `UNSIGNED_DELIVERY_DAYS = 15`; hàm `generateUnsignedDeliveryNotifications()` gọi trong `generateScheduledNotifications` (quanh dòng 59), chống trùng bằng `existsForUserAndRef` như `REF_CONTRACT_EXPIRING`.
    - `src/java/poscs/controller/NotificationController.java`: `targetOf` (quanh dòng 76–89) thêm nhánh cho loại thông báo mới, dẫn tới `/contract?action=view&id=...` như `contract_expiring`; sửa comment liệt kê các loại. Thiếu nhánh này thì bấm thông báo nhắc ký chỉ về trang Thông báo.
    - `src/java/poscs/controller/ContractExportController.java` (tự đọc tham số lọc quanh dòng 100–130, không qua `ContractListController`) và `src/java/poscs/service/ContractExportService.java`: đọc `delivery`, truyền xuống truy vấn xuất, để Excel ra đúng danh sách đang lọc.
  - File test: `ContractDAOTest`, `ContractListControllerTest`, `DashboardServiceTest`; `NotificationServiceTest` (đủ 15 ngày → tạo; 14 ngày → không; đã có thông báo → không tạo lại; đã ký → không; biên bản đã huỷ → không tính); `NotificationControllerTest` (loại thông báo mới → chi tiết hợp đồng); `ContractExportControllerTest`, `ContractExportServiceTest` (lọc `delivery`).
  - Không đụng: tiến độ (`progress_status`, `ALLOWED_TRANSITIONS`), trạng thái theo lịch; chu kỳ `NotificationScheduler` (60 phút).
  - Lưu ý: không thêm giá trị tiến độ mới; bước giao hàng là trục riêng, suy ra mỗi lần đọc. Chỉ nhắc một lần mỗi hợp đồng, cho người phụ trách hiện tại, không nhắc Admin (theo câu trả lời người dùng 19/11). Muốn nhắc lặp lại thì dừng hỏi.
  - Xong khi: hợp đồng Nháp có Biên bản giao nhận hàng hoá hiện "Đã giao – chưa ký" ở danh sách, chi tiết và được đếm trên dashboard, ký xong thì nhãn mất; biên bản ngày 16 hôm trước sinh đúng một thông báo cho người phụ trách.
  - Nhánh: `feat/task-10.5-buoc-giao-hang-nhac-ky`.
  - Commit: `feat(contract): bước giao hàng và nhắc ký sau 15 ngày (Task 10.5)`
  - *Đã hoàn thành:* `ContractDAO` (bước giao hàng suy ra trong `SELECT_BASE` từ giấy tờ chưa huỷ: cột `delivery_stage`, `delivered_date`, chỉ với bản Nháp; mã lọc `delivery` = `preparing` / `delivered` qua `deliveryStageClause`, áp cho `findAll`, `countAll` và dải KPI `countStatusSummary` (tách `listScopeSummaryClauses`); `countDeliveredUnsigned` cho Dashboard; `findDeliveredUnsigned(mốc)` cho lịch nhắc). `Contract` (`deliveryStage`, `deliveredDate`, `DELIVERY_PREPARING`, `DELIVERY_DELIVERED`, `isDeliveredUnsigned`, `deliveredDaysOn`). `ContractFilterHelper` (`deliveryFilterOf` chỉ nhận hai mã; chip "Bước giao hàng: …"; giữ `delivery` ở mọi link lọc), `ContractListController`, `ContractExportController` + `ContractExportService` (bộ lọc đi trong `ListScope`, Excel ra đúng danh sách đang lọc). `DashboardService` (ô "Đã giao – chưa ký": chỉ hợp đồng bán, theo phạm vi người/địa bàn, không theo kỳ; người dùng chốt 21/11/2026). `NotificationService` (`REF_CONTRACT_UNSIGNED_DELIVERY` = `contract_unsigned_delivery`, `UNSIGNED_DELIVERY_DAYS = 15`, `generateUnsignedDeliveryNotifications` trong lượt chạy theo lịch, MSG-065, chống trùng bằng `existsForUserAndRef`, chỉ gửi người phụ trách), `NotificationController` (`targetOf` mở chi tiết hợp đồng). JSP: `listcontract.jsp` (nhãn dưới tiến độ; ô lọc trong "Lọc thêm", tự gửi form; `delivery` ở phân trang và Xuất Excel), `viewcontractdetail.jsp` (nhãn ở tiêu đề, ngày giao, số ngày đã giao), `dashboard.jsp` (ô thứ tư bấm sang danh sách đã lọc). Ngoài dòng File sửa (người dùng đồng ý 21/11/2026): `ListScope` (`withDeliveryStage`, giữ nguyên số tham số các hàm DAO nên không sửa test cũ), `DashboardSummary`, `DashboardController`, kèm test ở `ListScopeTest`, `DashboardControllerTest`. Test: thêm 54 test ở 9 lớp (gồm test đọc `listcontract.jsp`, bắt mọi ô lọc có id phải tự gửi form); không sửa, không xoá test cũ. `ant test` 2.435 test (2.432 pass, 0 fail, 3 skipped); `compile-jsps` trên máy này bị bỏ qua (`compile.jsps=false`), đã dịch 3 JSP bằng Jasper của Tomcat 11, 0 lỗi. Chạy thử trên Tomcat riêng với CSDL tạm dựng từ `schema.sql` của nhánh (đã xoá sau khi thử): đạt toàn bộ dòng Xong khi. QA report tại `docs/qa/Task-10.5.md` (CẦN SỬA; 1 🔴, 1 🟡, 1 🟢 đã xử lý). `SELECT_BASE` giờ đọc `contract_documents.doc_date` (V44): `poscs_db` phải chạy V40 → V45 trước khi kéo code về checkout chính. Lỗi có sẵn, ngoài phạm vi: link Xuất Excel và phân trang của danh sách hợp đồng không mang `month`, `view`.
- [x] **Task 10.6:** Dọn dữ liệu mẫu cũ, gieo bộ dữ liệu mẫu mới theo cơ cấu chi nhánh thật và vòng đời hợp đồng mới; người dùng dựng lại `poscs_db` từ `db/schema.sql`.
  - UC: không có UC mới; bộ dữ liệu phải bày được `UC-08`, `UC-10`, `UC-11`, `UC-14`, `UC-16`, `UC-36`, `UC-46`, `UC-47`, `UC-48`, `UC-49`, `UC-52`, `UC-53`.
  - BR / MSG / NFR: `BR-52` (mã `KH-xxxx`, `NCC-xxx`), `BR-17` (trạng thái theo lịch); `BR-58` → `BR-62` (mã hợp đồng, giấy tờ, kỳ thanh toán, bước giao hàng, nhắc ký; Task 10.1); mục 9, 10, 11, 13, 14, 15, 16 "Quyết định đã chốt".
  - File sửa:
    - `db/migrations/V{số kế tiếp}__reseed_demo_theo_co_cau_that__ndat2003.sql` (mới):
      - Dòng đầu `-- poscs:demo-only` (quy ước Task 11.2; ghi cả khi 11.2 chưa merge), rồi `INSERT INTO schema_migrations` như `db/migrations/README.md`.
      - Xoá bộ cũ: mọi dòng của mọi bảng **không phải danh mục**, trừ `schema_migrations`. Danh mục là 10 bảng Task 11.3 chép lên production: `roles`, `departments`, `provinces`, `districts`, `productcategories`, `products`, `productimages`, `productcatalogues`, `customer_evaluation_rules`, `branch_info`. Xoá con trước cha theo khoá ngoại thật trong `schema.sql` (phụ lục trước hợp đồng gốc như V30; `users.manager_id` và `branch_info.updated_by` đặt NULL trước khi xoá `users`). Không `SET FOREIGN_KEY_CHECKS = 0`, không `TRUNCATE`, không `DROP`.
      - Gieo bộ mới (mục "Bộ dữ liệu" dưới đây). Không ghi cứng ID: tra khoá ngoại qua `username`, `enterprise_code`, mã hoặc tiêu đề hợp đồng, mã sản phẩm, bằng `INSERT ... SELECT` như V30. Mọi ngày tính từ `CURDATE()` để tình trạng không trôi theo thời gian (như V30).
      - Không sửa dữ liệu 10 bảng danh mục (Task 11.3 chép nguyên chúng từ `schema.sql` lên production), trừ việc đặt `branch_info.updated_by` NULL ở trên.
    - `db/schema.sql`:
      - Gỡ mọi khối gieo dữ liệu mẫu cũ: 12 tài khoản của V12 và `tonghop1` của V41, địa chỉ, khách hàng và nhà cung cấp, người liên hệ, `enterprise_roles`, sự kiện vòng đời, hợp đồng và các bảng con, phiếu hỗ trợ và các bảng con, `user_provinces`, các khối "nối lại" và "điền phương hướng xử lý" của bộ cũ.
      - Giữ nguyên mọi `CREATE TABLE` và `INSERT` của 10 bảng danh mục.
      - Thêm **một** khối "DỮ LIỆU MẪU, CHỈ DÙNG DEV/DEMO" ở cuối file: cùng phần gieo của migration, không có phần xoá.
      - Viết lại comment "Cố ý KHÔNG seed tài khoản Admin" theo mục 16 "Quyết định đã chốt".
  - Bộ dữ liệu: tên người, tên doanh nghiệp, mã số thuế, SĐT, CCCD đều giả; email dạng `@example.com`; không dùng tên khách hàng thật của chi nhánh.
    - Tài khoản:
      - Mật khẩu `Poscs@123`; riêng `admin` dùng `Admin@123` để công cụ chụp ảnh và kiểm thử đang dùng chạy tiếp. Băm bằng `lib/jbcrypt-0.4.jar` (tiền tố `$2a$`), mỗi tài khoản một salt như V12.
      - Tên đăng nhập theo đúng cách app sinh (`EmployeeDAO.generateUniqueUsername`: họ + đệm + tên, bỏ dấu, `đ` thành `d`, viết thường), trừ `admin`.
      - Ban giám đốc, vai Admin: GĐ `admin` (Nguyễn Văn Thành), PGĐ `tranquocbao` (Trần Quốc Bảo).
      - Kinh doanh, vai Sales, không ai có `manager_id`: Trưởng phòng `leminhhoang` (Lê Minh Hoàng), Phó phòng `phamthutrang` (Phạm Thu Trang), nhân viên `doducanh` (Đỗ Đức Anh) và `vuthihang` (Vũ Thị Hằng).
      - Tổng hợp, vai Tổng hợp: kế toán `hoangthimai` (Hoàng Thị Mai), thu tiền `buivannam` (Bùi Văn Nam), nhận bàn giao `ngothilan` (Ngô Thị Lan, mới vào làm, `must_change_password = 1` để thử luồng đổi mật khẩu tạm).
      - Sales đã nghỉ: `dangvantung` (Đặng Văn Tùng), `is_deleted = 1`. Vẫn là người phụ trách hợp đồng 6 bên dưới, để thử việc Admin đổi người phụ trách (mục 11).
      - Hồ sơ đủ giới tính, ngày sinh, CCCD, SĐT (không trùng nhau), để đăng nhập không bị bắt bổ sung hồ sơ.
      - Địa bàn: chia các tỉnh miền Bắc (tên đúng theo bảng `provinces`) cho 4 Sales, mỗi tỉnh tối đa một người. Admin, Tổng hợp và người đã nghỉ không giữ tỉnh nào.
    - Khách hàng và nhà cung cấp (bộ nhỏ, mục 16):
      - 4 khách hàng (bên mua), mỗi Sales một khách ở tỉnh của mình. Người phụ trách là Sales giữ tỉnh đó; người hỗ trợ (`support_owner_id`) khác người phụ trách.
      - 1 nhà cung cấp.
      - Mỗi đơn vị có địa chỉ đúng tỉnh và xã, một người liên hệ, dòng `enterprise_roles`, và dòng vòng đời như app ghi khi tạo (đọc `CustomerLifecycleDAO`).
    - Hợp đồng: **đúng 8 hợp đồng và 1 phụ lục** như dưới đây, không thêm. Hàng hoá lấy 1 đến 2 sản phẩm POSTEF có sẵn mỗi hợp đồng. Mã theo kiểu số hợp đồng thật (vd. `05/2026/HĐMB/POT-HB`). `contract_history` có đúng các sự kiện app ghi ở từng bước (đọc `ContractHistoryDAO`).
      1. Nháp vừa mở hồ sơ: chưa mã, chưa giấy.
      2. Nháp "Chuẩn bị hàng": có Phiếu GQNCKH và Phiếu ĐCNB.
      3. Nháp "Đã giao – chưa ký": Biên bản giao nhận hàng hoá ngày `CURDATE() - 16` (để sinh nhắc ký).
      4. Đã ký, chưa bàn giao (chờ phòng Tổng hợp xác nhận); một kỳ Thanh toán chưa nộp hồ sơ.
      5. Đã ký, đã bàn giao, hết hạn trong vòng 30 ngày (để sinh thông báo sắp hết hạn), có **1 phụ lục**. Hai kỳ 80/20 như hợp đồng mẫu ở Task 10.4 (số tiền giả, có điều kiện và 7 ngày): kỳ Tạm ứng đã thu; kỳ Thanh toán đã nộp hồ sơ, hạn thu sau hôm nay (Chờ thu).
      6. Đã ký, do `dangvantung` phụ trách; một kỳ quá hạn thu.
      7. Đã thanh lý: đủ giấy (nghiệm thu bàn giao, đề nghị thanh toán, hoá đơn GTGT, biên bản thanh lý), mọi kỳ đã thu, một kỳ có số thực thu thấp hơn số tiền kỳ.
      8. Hợp đồng Mua với nhà cung cấp, liên kết với hợp đồng 5 (`contract_links`).
    - Quy tắc chung cho hợp đồng:
      - Người ký bên mình: hợp đồng đã ký mang họ tên, chức vụ, SĐT, fax của GĐ (`admin`); hợp đồng Nháp để trống bốn ô (như khi chưa khai GĐCN, mục 10). Không sửa `branch_info`.
      - Người phụ trách theo mục 11: Sales giữ tỉnh của khách, trừ hợp đồng 6 (`dangvantung`); phụ lục theo hợp đồng gốc.
      - Bàn giao cho phòng Tổng hợp. Lượt đã xác nhận ghi người xác nhận là một tài khoản Tổng hợp.
      - Các kỳ "Đã thu" có ngày thu ở các tháng khác nhau trong 12 tháng gần nhất, để dashboard có doanh thu theo tháng.
    - Phiếu hỗ trợ: 3 phiếu, mỗi trạng thái một phiếu (Mới tiếp nhận / Đang xử lý / Đã đóng); phiếu Đang xử lý sắp quá hạn SLA. Người xử lý là Sales; gắn khách hàng và hợp đồng đã ký; lịch sử như app ghi (đọc `TechnicalSupportTicketDAO`).
    - Không gieo: thông báo (để `NotificationScheduler` tự sinh), yêu cầu thay đổi (`change_requests`).
  - File test: không có test Java mới. Chạy `ant test`, và chạy cả bộ test tích hợp trên CSDL đuôi `_it` như Task 7.2 (bộ này dựng từ `schema.sql`).
  - Không đụng:
    - Cấu trúc bảng và dữ liệu 10 bảng danh mục.
    - Các file migration cũ: V9, V12, V15, V30, V33, V41 giữ nguyên làm lịch sử.
    - `src/`, `web/`, `tools/guide/` (Task 10.7).
    - `tools/testdoc/`: dựa vào bộ cũ (`sales4`, `cskh2`, `KH-0001`…) nên sẽ hỏng khi CSDL kiểm thử dựng lại từ `schema.sql` mới. Không sửa; liệt kê các script bị ảnh hưởng trong report.
  - Lưu ý:
    - Task 10.2 → 10.5 phải merge trước, vì bộ mẫu dùng các cột mới của chúng; chưa merge thì dừng hỏi. Đọc lại `schema.sql` lúc làm để lấy đúng tên cột.
    - Agent **không** xoá hay chạy gì lên `poscs_db`. Kiểm bằng hai CSDL dùng một lần đuôi `_it`: (a) dựng từ `schema.sql` mới; (b) dựng từ `git show main:db/schema.sql` rồi áp file migration mới. Hai CSDL phải ra cùng số dòng ở từng bảng. Kiểm xong thì xoá hai CSDL tạm.
    - Report ghi lệnh để **người dùng** tự dựng lại `poscs_db` (mục 16: xoá sạch, không sao lưu), chạy lúc không dùng app:
      - `DROP DATABASE poscs_db`, rồi tạo lại với utf8mb4.
      - Nạp `schema.sql` bằng `mysql --default-character-set=utf8mb4 ... -e "source db/schema.sql"`. Không dùng `<` hay `|` của PowerShell, vì chúng làm hỏng tiếng Việt.
      - Ghi vào `schema_migrations` mọi file V đang có, vì `schema.sql` chỉ tạo bảng sổ rỗng.
    - File tải lên (logo, ảnh đại diện) của bộ cũ để nguyên trên đĩa.
    - Tình huống nào app hiện không tạo ra được (dữ liệu trái BR) thì không gieo, và ghi lý do trong report.
  - Xong khi (kiểm trên CSDL (a), app chạy ở Tomcat riêng):
    - Đăng nhập được `admin`, `tranquocbao`, `leminhhoang`, `hoangthimai`. `ngothilan` bị chuyển sang trang đổi mật khẩu; `dangvantung` báo tài khoản bị khoá.
    - Danh sách hợp đồng có đúng 8 hợp đồng và 1 phụ lục; nhãn bước giao hàng và đủ bốn tình trạng kỳ (Chưa nộp hồ sơ / Chờ thu / Quá hạn / Đã thu) hiện đúng.
    - Hàng chờ bàn giao của Tổng hợp có hợp đồng 4.
    - Dashboard có doanh thu theo tháng và ô "Đã giao – chưa ký".
    - Sau một lượt `NotificationScheduler` có nhắc ký cho hợp đồng 3 và thông báo sắp hết hạn cho hợp đồng 5.
    - `grep` trong `schema.sql` không còn `sales1`…`sales6`, `kythuat`, `cskh`, `tonghop1`.
    - Hai cách dựng (a), (b) ra cùng số dòng ở từng bảng.
  - Nhánh: `chore/task-10.6-du-lieu-mau-moi`.
  - Commit: `chore(db): gieo lại dữ liệu mẫu theo cơ cấu chi nhánh và vòng đời mới (Task 10.6)`
  - *Đã hoàn thành:* `db/migrations/V46__reseed_demo_theo_co_cau_that__ndat2003.sql` (dòng đầu `-- poscs:demo-only`; xoá 19 bảng không phải danh mục, con trước cha, không tắt khoá ngoại; gieo 10 tài khoản, 15 tỉnh cho 4 Sales, 4 khách hàng và 1 nhà cung cấp, 8 hợp đồng và 1 phụ lục, 39 giấy tờ, 7 kỳ thanh toán, 4 lượt bàn giao, 1 liên kết bán – mua, 3 phiếu hỗ trợ; nhật ký hợp đồng sinh theo quy tắc từ chính các dòng vừa gieo, đúng câu chữ các DAO ghi; mọi ngày tính từ `CURDATE()`, kỳ cuối hợp đồng 7 thu hôm nay và kỳ 2 thu ngày 15 tháng trước để Dashboard mở mặc định có doanh thu tháng này và so với tháng trước). `db/schema.sql` (gỡ 12 tài khoản của V12, `tonghop1` của V41 và toàn bộ khối dữ liệu mẫu cũ; comment bảng `users` viết lại theo mục 16; thêm một khối "DỮ LIỆU MẪU, CHỈ DÙNG DEV/DEMO" ở cuối, giống hệt phần gieo của V46; mọi `CREATE TABLE` và 10 bảng danh mục giữ nguyên). Không gieo: `customer_lifecycle_events` (app không ghi dòng nào khi tạo khách hàng), `technicalrequestdevices` (chưa màn hình nào đọc), thông báo, `change_requests`. `ant test` kèm bộ test tích hợp trên `poscs_it`: 2.498 test, 0 fail, 0 skipped. Hai CSDL tạm đuôi `_it` ((a) dựng từ `schema.sql` mới, (b) từ `schema.sql` của `main` rồi áp V46) ra cùng số dòng ở mọi bảng; app chạy ở Tomcat riêng trên (a) đạt mọi dòng Xong khi. QA report tại `docs/qa/Task-10.6.md` (CẦN SỬA; 1 🟡, 1 🟢 đã sửa). Người dùng tự dựng lại `poscs_db`: xoá, tạo lại utf8mb4, nạp `schema.sql` bằng `mysql --default-character-set=utf8mb4 ... -e "source db/schema.sql"`, ghi sổ V1 → V46 vào `schema_migrations`. Ngoài phạm vi, chưa sửa: các script `tools/testdoc/` còn dựa vào bộ cũ (`sales4`, `cskh2`, `KH-0001`…) nên sẽ hỏng trên CSDL dựng lại.
- [x] **Task 10.7:** Hướng dẫn sử dụng theo cơ cấu mới (Giai đoạn 9) và vòng đời hợp đồng mới (Giai đoạn 10).
  - UC: các UC đổi tác nhân ở Task 9.1; `UC-33`, `UC-44`, `UC-48`, `UC-49`, `UC-52`, `UC-53`.
  - BR / MSG / NFR: các BR, MSG mới của Task 10.1.
  - File sửa:
    - `web/jsp/guide/general.jsp`, `product.jsp`, `ticket.jsp`, `customer.jsp`, `employee.jsp`: bỏ vai Kỹ thuật; thêm vai và phòng Tổng hợp; người xử lý phiếu là Sales.
    - `web/jsp/guide/contract.jsp`: tả vòng đời theo mục 14 (mở hồ sơ từ phiếu GQNCKH, mã để trống tới lúc ký, tab Giấy tờ dạng ô tích, nhãn bước giao hàng, nhắc sau 15 ngày, bàn giao phòng Tổng hợp, kỳ thanh toán: loại kỳ, nộp hồ sơ, hạn thu, đã thu với số thực thu). Bỏ đoạn tả tab Tài liệu kiểu bắt buộc dán link.
    - `tools/guide/capture.mjs` (bảng mật khẩu) và `tools/guide/shots/*.mjs`: đổi sang bộ tài khoản mẫu của Task 10.6 (`sales1`, `sales4`, `sales5`, `cskh1`, `kythuat1`, `kythuat3` không còn); sửa phần "CHUẨN BỊ BẢN SAO CSDL" ở đầu từng kịch bản cho khớp (vd. ảnh bị buộc đổi mật khẩu tạm dùng `ngothilan`). `contract.mjs` chụp lại tab Giấy tờ và khối kỳ thanh toán.
    - `tools/guide/word/export_word.py` (dòng `CHANGES`) và xuất lại `docs/Report6_HuongDanSuDung.docx`.
    - `README.md`: nếu có liệt kê vai thì sửa.
    - `web/guide/contract/20-gia-tri-hien-hanh.png`: xoá (`git rm`). Ảnh mồ côi sau khi gỡ mục ảnh "Giá trị hiện hành" khỏi `contract.jsp` và `contract.mjs`; người dùng đồng ý xoá 21/11/2026 (mục 17 "Quyết định đã chốt").
  - File test: không có test Java mới; chạy `ant test`.
  - Không đụng: code Java; Project_Report.
  - Lưu ý: đọc code Task 9.2 → 10.6 đã merge, ghi đúng chữ trên giao diện. Chụp ảnh trên bản sao CSDL dựng từ `db/schema.sql` (bộ dữ liệu mẫu của Task 10.6). Xuất docx như Task 7.4a (kiểm `lastModifiedBy`, lệnh `export_word.py`; thiếu Python/thư viện thì dừng). Ví dụ trong hướng dẫn dùng số liệu giả, không dùng tên khách hàng hay số tài khoản thật.
  - Xong khi: `grep -rn "Kỹ thuật" web/jsp/guide` chỉ còn chữ trong tên phiếu "hỗ trợ kỹ thuật"; hướng dẫn tả đủ luồng GQNCKH → ĐCNB → giao hàng → ký → bàn giao Tổng hợp → nộp hồ sơ → thu tiền → thanh lý; docx có dòng Task 10.7; mọi ảnh trong `web/guide/` đều có JSP ở `web/jsp/guide/` trỏ tới và có kịch bản ở `tools/guide/shots/` sinh ra.
  - Nhánh: `docs/task-10.7-guide-co-cau-vong-doi-hop-dong`.
  - Commit: `docs(guide): hướng dẫn theo cơ cấu mới và vòng đời hợp đồng (Task 10.7)`
  - *Đã hoàn thành:* Sáu trang `web/jsp/guide/*.jsp` viết lại theo ba vai Admin / Sales / Tổng hợp (bỏ vai Kỹ thuật; Sales quản lý sản phẩm và là người xử lý phiếu; Tổng hợp chỉ xem, xác nhận bàn giao, ghi nhận đã thu, không vào phiếu hỗ trợ và Nhân viên). `contract.jsp` viết lại theo vòng đời mới, giữ số mục cũ: mục 1.1 bảng đối chiếu từng bước GQNCKH → ĐCNB → giao hàng → ký → bàn giao Tổng hợp → tạm ứng → nộp hồ sơ → thu tiền → thanh lý; mã để trống tới lúc ký ("Chưa có số", MSG-061); ba loại nhãn (thêm bước giao hàng, nhắc ký từ 15 ngày); mục 7.1–7.4 kỳ thanh toán (loại kỳ, ô "%", nộp hồ sơ, hạn thu, số thực thu, Tổng hợp ghi nhận ở trang chi tiết); mục 8 bàn giao phòng Tổng hợp; mục 9, 9.1 Giấy tờ dạng ô tích. Chụp lại toàn bộ 75 ảnh trên CSDL tạm dựng từ `schema.sql` (dữ liệu Task 10.6) và Tomcat riêng (người dùng chọn chụp lại hết, 21/11/2026); 4 ảnh Hợp đồng mới `13b`, `13c`, `13d`, `17b`; `git rm` `20-gia-tri-hien-hanh.png`. `capture.mjs` (bảng tài khoản mới) và 6 `tools/guide/shots/*.mjs` theo dữ liệu mới, phần "CHUẨN BỊ BẢN SAO CSDL" ghi đủ câu lệnh. Ngoài dòng File sửa (người dùng đồng ý 21/11/2026): `export_word.py` sửa bảng vai trò, câu mở đầu, Bước 2, Bước 6 cài đặt và ô `DB_PASSWORD`, đếm tài khoản theo chuỗi băm BCrypt, chốt chặn đổi thành báo khi `schema.sql` thôi gieo hai Admin; `tools/guide/word/diagrams.py` vẽ lại 3 sơ đồ (người dùng duyệt 21/11/2026), nhận thư mục ra làm đối số. `docs/Report6_HuongDanSuDung.docx` xuất lại (6 phần, 78 hình, có dòng Task 10.7). `README.md` không liệt kê vai nên không sửa. `ant test` 2.500 test (2.497 pass, 0 fail, 3 skipped); `compile-jsps` trên máy này bị bỏ qua, QA đã dịch 6 trang bằng Jasper của Tomcat 11 (0 lỗi) và cả 6 trang mở được trên Tomcat riêng. QA report tại `docs/qa/Task-10.7.md` (CẦN SỬA; 1 🟡, 2 🟢 đã sửa; chú thích sơ đồ tổng quan giữ nguyên vì đã duyệt; ô `DB_PASSWORD` sửa theo ghi chú ngoài phạm vi). Lỗi app lộ ra lúc chụp (luồng quên mật khẩu hỏng do tham số OTP, nhãn "Đã giao – chưa ký" bị cắt ở danh sách hợp đồng, link phiếu ở hồ sơ khách ra 404…) ghi thành task riêng, chưa sửa; `tools/guide/shots/general.mjs` còn đoạn lách tạm lỗi OTP.
- [x] **Task 10.8:** Dọn tài liệu: xoá file lỗi thời trong `docs/`, sửa `docs/ARCHITECTURE.md` theo cơ cấu mới, đưa các file quy trình còn nằm ngoài git vào repo, bỏ bản chép trùng của `rules.md`.
  - UC: không có.
  - BR / MSG / NFR: không thêm, không đổi.
  - File sửa:
    - Xoá bằng `git rm` (người dùng đồng ý 21/11/2026, mục 17 "Quyết định đã chốt"):
      - `docs/kichbandemoposcs.html`, `docs/kichbandemoposcs.pdf`: kịch bản demo dùng tài khoản `kythuat*`, `cskh*` đã bỏ ở Task 9.3 và Task 10.6. Thư mục làm việc đã xoá sẵn hai file này, chỉ cần đưa việc xoá vào commit.
      - `docs/POSCS_TaiKhoanTest.sql`: 12 tài khoản theo ba vai cũ Sales / Kỹ thuật / CSKH. Bộ tài khoản hiện dùng nằm ở V46 và `db/schema.sql`.
      - `docs/POSERD.png`: ERD vẽ ngày 30/06/2026, trước khoảng 40 migration; không file nào trỏ tới.
    - `docs/ARCHITECTURE.md`:
      - Dòng 147 và 241: "3 vai trò (`Admin`, `Sales`, `Kỹ thuật`)" đổi thành `Admin`, `Sales`, `Tổng hợp`.
      - Dòng 3 và tiêu đề ở dòng 58: `.gemini/rules.md` (§4) đổi thành `rules.md` (§4).
      - Rà mục 2 theo code đã merge tới Task 10.6 (giấy tờ dạng ô tích, kỳ thanh toán có nộp hồ sơ và số thực thu, bàn giao phòng Tổng hợp, nhắc ký sau 15 ngày, người xử lý phiếu là Sales): chỉ sửa câu sai so với code, không viết lại cả file, không thêm mục mới.
    - `.gemini/rules.md`: thay toàn bộ nội dung bằng vài dòng nói `rules.md` ở thư mục gốc là bản duy nhất, đọc file đó. Không chép lại nội dung.
    - `GEMINI.md`: bỏ dòng 7 (`.gemini/rules.md`: quy chuẩn chi tiết bổ sung), giữ ba nguồn còn lại.
    - `task_tracker.md` dòng 4: `.gemini/rules.md` đổi thành `rules.md`.
    - Đưa vào git, không sửa nội dung (đang chưa track): `docs/PROMPTS.md`, `task_tracker_deploy.md`, `docs/HuongDan_DoiEmailGui.md`. Task 11.5 sẽ gộp `docs/HuongDan_DoiEmailGui.md` vào `DEPLOY.md` rồi xoá.
  - File test: không có; chạy `ant test`.
  - Không đụng:
    - `docs/qa/*.md`: `rules.md` dòng 119 quy định không xoá báo cáo QA. Các báo cáo cũ có nhắc `.gemini/rules.md` cũng giữ nguyên.
    - Bốn file `docs/POSCS_*Test.xlsx` và `tools/testdoc/`: đã lỗi thời sau Giai đoạn 9 và 10, cập nhật là việc tài liệu riêng.
    - `docs/Project_Report_v8.docx`, `docs/Report6_HuongDanSuDung.docx`, `docs/SPEC.md`, `PERMISSIONS.md`, `README.md`.
    - `DEPLOY.md` (Task 11.5).
    - `tools/guide/`, `web/guide/`, `web/jsp/guide/` (Task 10.7 đã dọn).
    - Code Java, JSP, `db/`.
  - Lưu ý: làm sau khi Task 10.7 merge vào `main`, tạo nhánh từ `main` mới. Thấy file lạ khác trong `docs/` thì ghi vào report, không tự xoá. Trước khi commit ba file đang chưa track, đọc lại xem có địa chỉ email, mật khẩu, tên miền hay IP thật không; có thì dừng hỏi.
  - Xong khi:
    - `git grep -n -E "kichbandemoposcs|POSCS_TaiKhoanTest|POSERD" -- . ':!task_tracker.md' ':!docs/qa'` ra rỗng.
    - `git grep -n "\.gemini/rules" -- . ':!task_tracker.md' ':!docs/qa'` ra rỗng.
    - `grep -n "Kỹ thuật" docs/ARCHITECTURE.md` ra rỗng (chữ "phiếu hỗ trợ kỹ thuật" viết thường vẫn giữ).
    - `git ls-files docs/PROMPTS.md task_tracker_deploy.md docs/HuongDan_DoiEmailGui.md` ra đủ 3 file.
  - Nhánh: `chore/task-10.8-don-tai-lieu`.
  - Commit: `chore(docs): dọn tài liệu lỗi thời và bản chép trùng của rules (Task 10.8)`
  - *Đã hoàn thành:* `git rm` 4 file `docs/kichbandemoposcs.html`, `docs/kichbandemoposcs.pdf`, `docs/POSCS_TaiKhoanTest.sql`, `docs/POSERD.png`. Đưa vào git, không sửa nội dung: `docs/PROMPTS.md`, `task_tracker_deploy.md`, `docs/HuongDan_DoiEmailGui.md` (đã rà: không có email, mật khẩu, tên miền hay IP thật; chỉ có mật khẩu mẫu `Admin@123` / `Poscs@123` vốn đã nằm trong `db/schema.sql`). `docs/ARCHITECTURE.md`: dòng 3, 58 trỏ `rules.md`; dòng 147, 241 đổi vai `Kỹ thuật` thành `Tổng hợp`; rà mục 2 sửa hai câu sai so với code (Contract Service: "giấy tờ của hợp đồng (ô tích theo danh mục, link không bắt buộc), bàn giao cho phòng Tổng hợp"; thông báo thêm loại "hợp đồng đã giao hàng quá 15 ngày chưa ký"). `.gemini/rules.md` (trước đó giống hệt `rules.md`) chỉ còn vài dòng trỏ về `rules.md`; `GEMINI.md` bỏ dòng 7; dòng 4 file này trỏ `rules.md`. Bốn tiêu chí "Xong khi" đạt. `ant test` 2.500 test (2.497 pass, 0 fail, 3 skipped). QA report tại `docs/qa/Task-10.8.md` (APPROVED, 0 lỗi).
- [ ] **Task 10.9:** Viết lại `README.md` gọn, bằng tiếng Việt; chuyển quy ước viết log sang `rules.md`.
  - UC: không có.
  - BR / MSG / NFR: không thêm, không đổi.
  - File sửa:
    - `README.md`: viết lại toàn bộ bằng tiếng Việt, khoảng 100 dòng trở xuống (bản hiện tại 262 dòng tiếng Anh), đúng 9 mục theo thứ tự (mục 18 "Quyết định đã chốt"):
      1. POSCS là gì: 2–3 câu (theo dõi khách hàng, hợp đồng, phiếu hỗ trợ của chi nhánh; ba vai Admin / Sales / Tổng hợp theo mục 13).
      2. Công nghệ: mỗi thứ một dòng (Java 21, Jakarta EE 11 Servlet + JSP, Tomcat 11, MySQL, Ant / NetBeans).
      3. Cấu trúc thư mục: cây ngắn `src/java/poscs/{controller,service,validator,dao,model,common}`, `web/`, `db/`, `test/`, `tools/`, `docs/`, mỗi dòng một câu tả. Không đếm file, không liệt kê tên lớp; chi tiết trỏ tới `docs/ARCHITECTURE.md`.
      4. Chạy trên máy, 4 bước: tạo CSDL `poscs_db` (utf8mb4) và nạp `db/schema.sql` (2 lệnh `mysql` như bản cũ); đặt `DB_URL`, `DB_USER` (có mặc định), `DB_PASSWORD` (bắt buộc, không có mặc định); `MAIL_USERNAME`, `MAIL_PASSWORD` không đặt thì thư (mã OTP, mật khẩu tạm) in ra log thay vì gửi; build bằng NetBeans hoặc `ant dist` (hai tham số `-D`, trỏ tới `DEPLOY.md` §2) rồi deploy lên Tomcat.
      5. Đổi cấu trúc CSDL: 1 câu, thêm file vào `db/migrations/`, cách làm xem `db/migrations/README.md`.
      6. Kiểm thử: 3 lệnh (`ant test`; test tích hợp trên CSDL tên đuôi `_it` với ba tham số `-Dtest-sys-prop.DB_*`; `node --test 'test/js/**/*.test.js'`), thêm 1 câu: không trỏ tới CSDL `_it` thì test tích hợp tự bỏ qua, và CSDL `_it` bị xoá sạch mỗi lần chạy.
      7. Log: ghi vào log của Tomcat (`logs/catalina.*`); Admin xem và tải ở màn hình Nhật ký hệ thống (`/systemLog`); đổi thư mục log bằng `LOG_DIR`.
      8. Bảo mật: 4–5 gạch đầu dòng ngắn, không kèm tên lớp (CSRF cho mọi POST, tạo lại session khi đăng nhập, đăng nhập và quên mật khẩu không lộ username có thật, giới hạn số lần nhập và gửi lại OTP); phân quyền trỏ tới `PERMISSIONS.md`.
      9. Tài liệu khác: danh sách link `docs/SPEC.md`, `PERMISSIONS.md`, `docs/ARCHITECTURE.md`, `DEPLOY.md`, `rules.md`, `task_tracker.md`, hướng dẫn sử dụng (trang `/guide` trong app, `docs/Report6_HuongDanSuDung.docx`).
      - Bỏ: danh sách tên lớp và số file từng gói; đoạn giải thích vì sao test dùng system property, vì sao test JS chạy trong `vm`, hai "bài học" ở cuối mục test JS; chi tiết màn hình Nhật ký hệ thống (512 KB, đuôi `.log` / `.out` / `.txt`); mục "Roles and permissions" (gộp vào mục 1 và mục 8).
      - **Không ghi tài khoản hay mật khẩu mẫu** (người dùng chốt 21/11/2026).
    - `rules.md` §4: thêm 3–4 dòng quy ước viết log, lấy từ mục "Logging" của README cũ: ghi lỗi qua SLF4J, không `printStackTrace()`; DAO log thao tác, tham số khoá và exception; Controller log `warn` ở mọi nhánh redirect `?error=..._failed`, kèm `actor` (`Logs.actor`); chữ trong log không dấu; không bao giờ log mật khẩu, chuỗi băm, mã OTP, mật khẩu tạm (trừ chế độ DEV của `EmailUtil` khi chưa cấu hình SMTP).
  - File test: không có; chạy `ant test`.
  - Không đụng: `DEPLOY.md` (Task 11.5), `PERMISSIONS.md`, `db/migrations/README.md`, `tools/testdoc/README.md`, `docs/SPEC.md`, code Java, JSP.
  - Lưu ý: làm sau khi Task 10.8 merge (khi đó `rules.md` là bản duy nhất, không phải chép sang `.gemini/rules.md`; 10.8 chưa merge thì dừng hỏi). Đọc code trước khi viết, ghi đúng tên biến và giá trị mặc định: `poscs.dao.DBContext` (`DB_*`, đọc system property trước rồi tới biến môi trường), `poscs.common.EmailUtil` (`MAIL_*`), `poscs.common.LogFiles` (`LOG_DIR`), `DEPLOY.md` §2 (hai tham số `-D` của `ant dist`), `.github/workflows/` (lệnh test). Mọi lệnh trong README phải chạy được đúng như ghi. Tên vai, tên phòng theo `docs/SPEC.md`.
  - Xong khi:
    - `README.md` viết bằng tiếng Việt, khoảng 100 dòng trở xuống, đủ 9 mục theo đúng thứ tự trên.
    - `grep -n -E "Poscs@123|Admin@123|leminhhoang|hoangthimai" README.md` ra rỗng.
    - Không còn số file hay danh sách tên lớp của từng gói.
    - Mọi link trong `README.md` trỏ tới file có thật.
    - `rules.md` §4 có quy ước viết log.
  - Nhánh: `docs/task-10.9-readme-tieng-viet`.
  - Commit: `docs(readme): viết lại README gọn bằng tiếng Việt (Task 10.9)`
- [ ] **Task 10.10:** Người dùng dựng lại `poscs_db` từ `db/schema.sql` (dữ liệu mẫu Task 10.6), đưa CSDL dev từ V39 lên V46.
  - UC: không có.
  - BR / MSG / NFR: không đổi; mục 16 "Quyết định đã chốt" (xoá sạch, không sao lưu).
  - File sửa: không có. **Người dùng tự chạy** trên máy, lúc không dùng app; agent không chạy gì lên `poscs_db` (rules §3). Ba bước, chạy ở thư mục repo:
    1. `mysql -u root -p -e "DROP DATABASE poscs_db; CREATE DATABASE poscs_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"`
    2. `mysql --default-character-set=utf8mb4 -u root -p poscs_db -e "source C:/Users/Admin/Documents/GitHub/POSCS/db/schema.sql"`. Không dùng `<` hay `|` của PowerShell, vì chúng làm hỏng tiếng Việt.
    3. Ghi sổ mọi file migration vào `schema_migrations` (`schema.sql` chỉ tạo bảng sổ rỗng). Trong Git Bash: `mysql -u root -p poscs_db -e "$(for f in db/migrations/V[0-9]*.sql; do printf "INSERT INTO schema_migrations (version) VALUES ('%s');" "$(basename "$f" .sql)"; done)"`. Mẫu `V{version}__...sql` không khớp `V[0-9]*` nên không bị ghi.
  - File test: không có.
  - Không đụng: file trong repo; CSDL kiểm thử (`poscs_it`, `poscs_bbtest`, các CSDL tạm).
  - Lưu ý: đo 21/11/2026, `poscs_db` mới tới V39 trong khi code `main` cần từ V40, nên app chạy trên `poscs_db` (Tomcat 8080 của NetBeans) hỏng tới khi dựng lại. Dữ liệu cũ mất hết, kể cả link demo của HD-0015 (người dùng đã chọn ở mục 16). File tải lên của bộ cũ để nguyên trên đĩa.
  - Xong khi: `SELECT COUNT(*) FROM poscs_db.schema_migrations` bằng số file `V[0-9]*.sql` trong `db/migrations/` (46 lúc ghi task) và có dòng V46; app 8080 đăng nhập được `admin`; danh sách hợp đồng có 8 hợp đồng và 1 phụ lục.
  - Nhánh: không có (không đổi file trong repo).
  - Commit: không có; người dùng tự đánh `[x]` khi xong.

---

## Giai đoạn 12: Sửa lỗi lộ ra khi chụp lại hướng dẫn sử dụng (Task 10.7)

> Phát hiện 21/11/2026 lúc chụp lại toàn bộ ảnh hướng dẫn trên CSDL dựng từ `db/schema.sql` (dữ liệu mẫu Task 10.6). Người dùng chọn ghi thành task, không sửa trong Task 10.7 (21/11/2026). Số giai đoạn 12 vì 11 là triển khai (`task_tracker_deploy.md`). Làm **sau khi Task 10.7 merge**; 12.1 trước vì luồng quên mật khẩu đang hỏng hẳn. Task nào đổi giao diện có ảnh trong hướng dẫn thì chụp lại đúng ảnh đó bằng `tools/guide/capture.mjs` (CSDL dựng từ `schema.sql`, xem đầu kịch bản) và xem ảnh trước khi dùng.

- [ ] **Task 12.1:** Quên mật khẩu: trang nhập OTP gửi đúng tham số controller đọc; đủ câu báo lỗi của luồng mật khẩu.
  - UC: `UC-03`, `UC-04`, `UC-05`.
  - BR / MSG / NFR: `BR-01`, `BR-04`, `BR-05`; `MSG-007` (OTP hết hạn), `MSG-008` (sai quá nhiều lần).
  - File sửa:
    - `src/java/poscs/controller/PasswordResetController.java` (`handleVerifyOtp`, dòng 135): đọc tham số `otpCode` như `web/verifyOtp.jsp` gửi (ô ẩn dòng 234, JS ghép sáu ô ở dòng 327). Lỗi có từ lần tách controller (`d282317`): `AuthenticationController` cũ đọc `otpCode`, bản tách đọc `otp`, nên gõ đúng mã vẫn báo "Mã OTP không đúng".
    - `web/forgotPassword.jsp` (khối `param.error`, quanh dòng 242–246): thêm nhánh `expired` (controller đẩy về đây khi mã hết hạn) với câu MSG-007. Dòng 282 "liên hệ Phòng Nhân sự / IT" đổi thành "liên hệ Admin" (cơ cấu mới không có phòng đó).
    - `web/login.jsp` (dòng 284): như trên, "liên hệ Admin để được cấp tài khoản".
    - `web/changePassword.jsp` (khối `param.error`, quanh dòng 304–307): thêm nhánh `too_many_attempts` (sai mật khẩu hiện tại cũng tính vào lượt chặn máy, `AuthService.changePassword`) với câu MSG-008; hiện tại rơi vào câu chung "Đã có lỗi xảy ra".
    - `tools/guide/shots/general.mjs` (quanh dòng 185–192): bỏ đoạn "TẠM (Task 10.7)" chèn ô ẩn `otp`; chụp lại ảnh `05`, `06` của phần Bắt đầu để chắc luồng chạy không cần lách.
    - `web/jsp/guide/general.jsp`: chỉ sửa nếu câu trên giao diện đổi khác chữ đang ghi ở mục 1, 3, 8.
  - File test: `test/poscs/controller/PasswordResetControllerTest.java`: gửi `otpCode` đúng → sang `resetPassword.jsp`; `otpCode` sai → `error=invalid_otp`; thiếu `otpCode` → báo lỗi, không coi là đúng. Không sửa assertion test cũ; test cũ nào đang dựng tham số `otp` thì dừng hỏi.
  - Không đụng: `AuthService` (luật OTP, hạn mức), `EmailUtil`, tên ô ẩn `otpCode` trong `verifyOtp.jsp`.
  - Lưu ý: chạy thật trên Tomcat riêng với `MAIL_*` để trống (mã OTP in ra log): đi trọn quên mật khẩu → nhập mã → đặt mật khẩu mới → đăng nhập lại bằng một tài khoản mẫu, trên CSDL tạm, không phải `poscs_db`.
  - Xong khi: gõ đúng mã OTP ở `verifyOtp.jsp` sang được trang đặt lại mật khẩu mà không lách gì; `grep -n "TẠM" tools/guide/shots/general.mjs` không còn kết quả; `grep -rn "Nhân sự / IT" web` không còn kết quả.
  - Nhánh: `fix/task-12.1-otp-quen-mat-khau`.
  - Commit: `fix(auth): trang nhập OTP gửi đúng tham số, đủ câu báo lỗi luồng mật khẩu (Task 12.1)`
- [ ] **Task 12.2:** Lỗi hiển thị ở danh sách hợp đồng, chi tiết sản phẩm, hồ sơ khách hàng và các trang phụ.
  - UC: `UC-10`, `UC-17`, `UC-09`, `UC-55`, `UC-26`, `UC-27`, `UC-16`.
  - BR / MSG / NFR: `BR-61` (nhãn bước giao hàng phải đọc được), `MSG-022`.
  - File sửa:
    - `web/jsp/sale/listcontract.jsp` (CSS `.delivery-tag`, dòng 218): nhãn đang `display: inline-flex` nên đứng cùng dòng với nhãn tiến độ trong ô `nowrap` + `text-overflow: ellipsis` (dòng 182, cột 12% ở dòng 190), "Đã giao – chưa ký" bị cắt thành "…". Cho nhãn xuống dòng dưới nhãn tiến độ (vd. `display: flex`).
    - `web/jsp/technical/viewdetailProduct.jsp`: `.status-pill` (dòng 125) thêm `white-space: nowrap` (bảng hợp đồng của sản phẩm đang gãy "Chưa hiệu / lực"); ô Mô tả (`.view-value.multiline`, dòng 86 và 227) bỏ dòng trống đầu/cuối do `white-space: pre-line` giữ cả xuống dòng của mã JSP.
    - `web/jsp/sale/viewcustomerdetail.jsp` (tab phiếu hỗ trợ, dòng 476): link mã phiếu `ticketDetail.jsp?id=` (file không tồn tại, 404) đổi thành `/ticket?action=view&id=`. Tab này hiện mã và mô tả phiếu cho cả vai Tổng hợp dù vai này không vào được phiếu: chỉ hiện khi người xem có quyền xem phiếu (`PermissionService.canViewTickets`, đặt cờ ở `src/java/poscs/controller/CustomerDetailController.java`, quanh dòng 130).
    - `web/jsp/admin/addEmployee.jsp`, `web/jsp/admin/updateEmployee.jsp` (`isValidPhone`, dòng 317–318): kiểm cùng luật với server (`BaseValidator.PHONE_VN` = `^0[35789]\d{8}$`, `+84` đổi thành `0` như `EmployeeValidator.normalizePhone`); hiện trình duyệt cho qua số mà server từ chối bằng câu chung.
    - `web/jsp/admin/branchInfo.jsp`: thêm nút "Hướng dẫn" (`.guide-btn`, `target=_blank`) trỏ `/guide?module=employee#thong-tin-chi-nhanh`, như các trang admin khác.
    - `web/jsp/customersupport/listTicket.jsp` (hộp xoá, dòng 404–405): "Hủy" / "Xóa phiếu" đổi thành "Huỷ bỏ" / "Xoá phiếu" cho khớp hộp xoá ở trang chi tiết.
    - Ảnh hướng dẫn chụp lại: `web/guide/contract/03-doc-dong.png` (khoanh số 4 phải thấy trọn "Đã giao – chưa ký"), `web/guide/product/04-chi-tiet.png`; ảnh khác nếu giao diện trong ảnh đổi.
  - File test: `test/poscs/controller/CustomerDetailControllerTest.java` (vai Tổng hợp không nhận danh sách phiếu / cờ xem phiếu là false; Sales thì có). Các sửa JSP/CSS còn lại kiểm bằng chạy thật và ảnh chụp; thêm test `node --test` ở `test/js/` nếu tách được hàm kiểm SĐT.
  - Không đụng: logic bước giao hàng (`ContractDAO`), quyền phiếu ở `PermissionService`, `EmployeeValidator`.
  - Lưu ý: dịch các JSP đã sửa bằng Jasper của Tomcat 11 (`compile-jsps` trên máy này không chạy) và mở từng trang trên Tomcat riêng.
  - Xong khi: danh sách hợp đồng hiện trọn "Đã giao – chưa ký" dưới nhãn Nháp ở khổ 1600 và 1920; bấm mã phiếu ở hồ sơ khách mở đúng phiếu; đăng nhập vai Tổng hợp không thấy phiếu trong hồ sơ khách; form nhân viên báo lỗi SĐT ngay trên trình duyệt với số `0212345678`.
  - Nhánh: `fix/task-12.2-loi-hien-thi`.
  - Commit: `fix(ui): nhãn giao hàng, link phiếu ở hồ sơ khách và các lỗi hiển thị nhỏ (Task 12.2)`
- [ ] **Task 12.3:** Phạm vi và bộ lọc: trang chủ, danh sách và xuất Excel khách hàng.
  - UC: `UC-36`, `UC-08`, `UC-37`, `UC-31`.
  - BR / MSG / NFR: phạm vi "Của tôi" / "Toàn chi nhánh" theo §3 SPEC (`PermissionService.getCustomerListScope`).
  - File sửa:
    - `web/dashboard.jsp` (dòng 434): link "Xem toàn chi nhánh" là `/dashboard?provinceSet=1`, không mang `scope=all`, nên Sales bấm vào lại rơi về "Của tôi" không tỉnh (chỉ còn việc của chính mình); thêm `scope=all`. Câu ghi chú dòng 428 ("hai bảng cuối trang luôn tính tới hôm nay") sửa cho đúng code: hai bảng lọc theo kỳ (`findActiveInPeriod`, `findInScope(…, period)`), chỉ ô "Đã giao – chưa ký" không theo kỳ.
    - `web/jsp/sale/listcustomer.jsp` (link Xuất Excel, dòng 319): thêm tham số `view` để file ra đúng phạm vi đang xem (đo 21/11: đang xem Toàn chi nhánh mà file chỉ có khách của "Của tôi"). Ô Phạm vi mất lựa chọn "Của tôi" khi đang ở `view=all`: dùng `viewToggleUrl` mà `CustomerListController` (dòng 147) đã đặt sẵn.
    - Controller xuất Excel khách hàng (tra `action=exportExcel` của `/customer`): đọc `view` giống danh sách, nếu hiện chưa đọc.
  - File test: `test/poscs/controller/CustomerListControllerTest.java`, test của controller/service xuất Excel khách hàng (`view=all` ra toàn chi nhánh; không truyền `view` giữ mặc định cũ); `DashboardControllerTest` nếu có đổi controller.
  - Không đụng: luật phạm vi trong `PermissionService`; phần hợp đồng (link phân trang / Excel của danh sách hợp đồng thiếu `month`, `view`, đã ghi ở Task 10.5).
  - Lưu ý: **dừng hỏi trước khi code**: Sales chưa được giao tỉnh thì sửa được mọi khách mua (theo `PERMISSIONS.md` và `CustomerService.outsideScope`) hay chỉ khách của mình (theo `PermissionService.isCustomerInScope`, quyết định dấu khoá và nút trên giao diện)? Hai lớp đang trả lời khác nhau; dữ liệu mẫu hiện không có Sales nào như vậy nên hướng dẫn Khách hàng đã bỏ câu này. Chốt xong thì sửa lớp lệch trong cùng task.
  - Xong khi: Sales bấm "Xem toàn chi nhánh" ở trang chủ thấy số liệu toàn chi nhánh; xuất Excel khách hàng khi đang xem Toàn chi nhánh ra đủ khách; từ Toàn chi nhánh chọn lại được "Của tôi" ngay trên ô Phạm vi.
  - Nhánh: `fix/task-12.3-pham-vi-bo-loc`.
  - Commit: `fix(scope): phạm vi trang chủ, danh sách và xuất Excel khách hàng (Task 12.3)`

---

## Giai đoạn 13: Tài liệu nộp theo Giai đoạn 8–10

> Yêu cầu người dùng 21/11/2026. `docs/Project_Report_v8.docx` và bốn file `docs/POSCS_*Test.xlsx` cập nhật lần cuối 02/10/2026 (PR #176, #177), trước Giai đoạn 8–10; mục 17 "Quyết định đã chốt" để riêng việc này. Đo 21/11 trên bản trong repo: Project_Report có 64 chỗ "Kỹ thuật" (gồm cả tên phiếu), 0 chỗ "Tổng hợp", chưa có UC-55, BR-56 → BR-62, MSG-057 → MSG-065; `tools/testdoc/` còn khoảng 380 dòng dựa vào tài khoản, vai cũ (`sales2`…, `kythuat*`, `cskh*`) nên hỏng trên CSDL dựng từ `schema.sql` mới (Task 10.6). Làm **sau khi Task 10.8 và Giai đoạn 12 merge**, để tài liệu khớp code cuối; thứ tự 13.1 → 13.5. Giữ quy ước các đợt tài liệu trước: đọc code `main` trước khi viết; ca mới thêm cuối chức năng vì mã ca đánh theo vị trí; diagram vẽ xong gửi ảnh cho người dùng duyệt, chưa duyệt thì không chèn.

- [ ] **Task 13.1:** `tools/testdoc/` chạy lại được trên CSDL dựng từ `db/schema.sql` (dữ liệu mẫu Task 10.6) và ba vai Admin / Sales / Tổng hợp.
  - UC: các UC đổi tác nhân ở Task 9.1.
  - BR / MSG / NFR: không đổi.
  - File sửa:
    - `tools/testdoc/blackbox/fixtures.sql`: bỏ phần dựa vào 12 nhân viên cũ của `schema.sql` (`sales2`…`sales6`, `kythuat2`…`kythuat5`, `cskh2`…`cskh4`) và vai Kỹ thuật; tài khoản kiểm thử theo ba vai, có ít nhất một tài khoản Tổng hợp. Vẫn khoá theo `username`, tra vai và phòng theo tên; vẫn nạp lại trước mỗi lượt để đặt lại mật khẩu và trạng thái khoá.
    - `run_blackbox.py`, `run_blackbox_ui.py`, `run_blackbox_branches.py`, `run_blackbox_rest.py`, `run_systemtest.py`: tài khoản, mã khách hàng, mã / tiêu đề hợp đồng, mã phiếu của bộ cũ đổi sang bộ Task 10.6 hoặc tài khoản của `fixtures.sql`; tra id qua `testdb.py`, không ghi cứng. Bước nào dựa vào hành vi cũ (mã hợp đồng nhập lúc Nháp, tab Tài liệu kiểu dán link, kỳ thanh toán cấu trúc cũ, người xử lý phiếu là Kỹ thuật) thì sửa cho khớp giao diện hiện tại.
    - `blackbox/*.json`, `systemtest/*.json`, `uat_checklist.json`: bước và dữ liệu nhắc vai Kỹ thuật / CSKH, phòng Kế toán / Dự án hay tài khoản cũ viết lại theo ba vai (người xử lý phiếu là Sales; Sales quản lý sản phẩm; Tổng hợp chỉ xem, xác nhận bàn giao, ghi nhận đã thu). Ca đổi nội dung thì kết quả cũ về "Chưa chạy". Ca của chức năng không còn thì liệt kê trong report, dừng hỏi trước khi xoá.
    - `tools/testdoc/README.md`: mục dựng CSDL kiểm thử và tài khoản theo bộ mới; gỡ cảnh báo cũ về vai CSKH và bộ 12 nhân viên.
  - File test: không có test Java; chạy `ant test`. Chạy thử từng script trên CSDL kiểm thử dựng từ `schema.sql` + `fixtures.sql` (không phải `poscs_db`).
  - Không đụng: ca mới cho Giai đoạn 8–10 (Task 13.2); bốn file `docs/POSCS_*Test.xlsx` và `*_results.json` (Task 13.3); `gen_unittest_function.py`; `src/`, `web/`, `db/`.
  - Lưu ý: giữ nguyên chặn chạy vào `poscs_db` trong `testdb.py`. Hỏi trước khi tạo và trước khi xoá CSDL kiểm thử. Ca trượt vì lỗi app thật thì ghi vào report, không sửa code trong task này.
  - Xong khi: `grep -rnE "sales[0-9]|kythuat[0-9]|cskh[0-9]|tonghop1" tools/testdoc --include=*.py --include=*.json --include=*.sql --exclude=*_results.json` ra rỗng; mọi script chạy hết lượt trên CSDL kiểm thử mới, không ca nào trượt vì sai tài khoản hay không tìm thấy dữ liệu.
  - Nhánh: `docs/task-13.1-testdoc-du-lieu-moi`.
  - Commit: `docs(testdoc): chạy lại được trên dữ liệu mẫu và ba vai mới (Task 13.1)`
- [ ] **Task 13.2:** Thêm ca kiểm thử cho Thông tin chi nhánh, người ký bên mình (Giai đoạn 8) và vòng đời hợp đồng mới (Giai đoạn 10).
  - UC: `UC-55`; `UC-10`, `UC-11`, `UC-33`, `UC-35`, `UC-36`, `UC-44`, `UC-48`, `UC-49`, `UC-52`, `UC-53`.
  - BR / MSG / NFR: `BR-18`, `BR-35`, `BR-56` → `BR-62`; `MSG-057` → `MSG-065`.
  - File sửa:
    - `tools/testdoc/blackbox/*.json` (theo cách gom chức năng hiện có): trang Thông tin chi nhánh (Admin xem / sửa, lỗi từng ô theo BR-57, vai khác bị chặn); form hợp đồng khoá người ký, lấy SĐT / fax (BR-56); người phụ trách theo người tạo (BR-35); mã để trống khi Nháp, bắt buộc khi Ký (BR-58, MSG-061); giấy tờ ô tích và ngày giấy tờ (BR-59); kỳ thanh toán: loại kỳ, nộp hồ sơ, hạn thu, số thực thu, sửa kỳ (BR-60, BR-18); nhãn bước giao hàng (BR-61); nhắc ký sau 15 ngày (BR-62); bàn giao và xác nhận của phòng Tổng hợp.
    - `tools/testdoc/systemtest/*.json`: luồng trọn vòng đời GQNCKH → ĐCNB → giao hàng → ký → bàn giao Tổng hợp → nộp hồ sơ → thu tiền → thanh lý, đi bằng các vai Sales, Tổng hợp, Admin.
    - `tools/testdoc/uat_checklist.json`: mục UAT cho cùng các luồng trên.
    - `run_blackbox*.py`, `run_systemtest.py`: ca tự động được thì thêm vào script; ca không tự động được giữ "Chưa chạy", nhãn `TAY` như lệ cũ.
  - File test: không có test Java; chạy `ant test`; chạy thử ca mới trên CSDL kiểm thử như Task 13.1.
  - Không đụng: ca cũ đã sửa ở Task 13.1; bốn file Excel (Task 13.3); code app.
  - Lưu ý: làm sau Task 13.1. Ca mới thêm **cuối** chức năng / luồng. Câu mong đợi lấy đúng chữ MSG trong `docs/SPEC.md` và đúng chữ trên giao diện. Ca nhắc ký cần ngày giao ≤ hôm nay − 15 ngày: dùng hợp đồng 3 của bộ mẫu hoặc dựng bằng `testdb.py`.
  - Xong khi: mỗi mã `BR-56` → `BR-62` và `MSG-057` → `MSG-065` có ít nhất một ca hộp đen trỏ tới; system test có luồng trọn vòng đời; mỗi ca mới hoặc có kết quả chạy thật, hoặc ghi "Chưa chạy" kèm nhãn `TAY`.
  - Nhánh: `docs/task-13.2-testdoc-ca-moi`.
  - Commit: `docs(testdoc): ca kiểm thử cho thông tin chi nhánh và vòng đời hợp đồng (Task 13.2)`
- [ ] **Task 13.3:** Chạy lại toàn bộ kiểm thử, sinh lại bốn file `docs/POSCS_*Test.xlsx`.
  - UC: không có.
  - BR / MSG / NFR: không đổi.
  - File sửa:
    - `docs/POSCS_UnitTest_Function.xlsx`: `ant test` (kèm test tích hợp trên CSDL `_it`), `node --test` xuất junit, rồi `gen_unittest_function.py -o docs/POSCS_UnitTest_Function.xlsx` theo `tools/testdoc/README.md`; tiêu đề test mới chưa có bản dịch thì thêm vào `vi_glossary.json`.
    - `docs/POSCS_IntegrationTest_Blackbox.xlsx`, `docs/POSCS_SystemTest.xlsx`, `docs/POSCS_UserAcceptanceTest.xlsx`: chạy `run_blackbox*.py`, `run_systemtest.py` trên Tomcat riêng (cổng 8099) + CSDL kiểm thử, rồi `gen_integration_blackbox.py`, `gen_system_test.py`, `gen_uat.py` (đặt `PYTHONIOENCODING=utf-8` trên máy này), ghi đè bản trong `docs/`.
    - `tools/testdoc/blackbox_results.json`, `systemtest_results.json`: kết quả lượt chạy này.
  - File test: không có test mới.
  - Không đụng: nội dung ca (Task 13.1, 13.2); code app.
  - Lưu ý: làm sau Task 13.2. Nạp lại `fixtures.sql` trước mỗi lượt. Ca trượt vì lỗi app thật thì ghi "Trượt" đúng sự thật và ghi vào report, không sửa code trong task này. Hỏi trước khi tạo và xoá CSDL kiểm thử. Report ghi đủ số liệu cho mục 5.5 của Project_Report (Task 13.4): số lớp / ca unit Java và JS, tích hợp JUnit, hộp đen Đạt / Trượt / N/A / Chưa chạy, system, UAT.
  - Xong khi: bốn file Excel trong `docs/` sinh lại từ lượt chạy này; không ca nào ghi "Đạt" mà chưa chạy; report có đủ số liệu mục 5.5.
  - Nhánh: `docs/task-13.3-sinh-lai-excel-kiem-thu`.
  - Commit: `docs(testdoc): chạy lại kiểm thử và sinh lại tài liệu Excel (Task 13.3)`
- [ ] **Task 13.4:** `docs/Project_Report_v8.docx`, phần chữ: theo ba vai, Thông tin chi nhánh và vòng đời hợp đồng mới.
  - UC: `UC-55` (mới); các UC đổi tác nhân ở Task 9.1; `UC-10`, `UC-11`, `UC-33`, `UC-35`, `UC-36`, `UC-44`, `UC-52` (tên mới "Quản lý giấy tờ của hợp đồng"), `UC-53`.
  - BR / MSG / NFR: thêm `BR-56` → `BR-62`, `MSG-057` → `MSG-065`; sửa `BR-18`, `BR-35`, `BR-39`, `BR-46`.
  - File sửa: `docs/Project_Report_v8.docx`, theo `docs/SPEC.md` hiện tại (sau Task 8.1, 9.1, 10.1) và code `main`:
    - Tác nhân: bỏ vai Kỹ thuật, CSKH; thêm vai và phòng Tổng hợp (gộp Kế toán, Dự án); người xử lý phiếu là Sales; Sales quản lý sản phẩm. Giữ chữ "hỗ trợ kỹ thuật" trong tên phiếu.
    - Danh sách chức năng (FE), bảng UC, ma trận quyền (cột Admin / Sales / Tổng hợp như SPEC §3.3), đặc tả các UC đổi tác nhân hoặc đổi luồng; thêm đặc tả UC-55.
    - Bảng BR, MSG: thêm mã mới, sửa mã đổi nội dung, câu chữ đúng SPEC.
    - Mô tả thực thể (mục 3.1.5.2): thêm `branch_info`, cột mới của `contracts`, `contract_documents`, `contract_payments` (SPEC §7, 30 bảng).
    - Chương hướng dẫn sử dụng trong báo cáo: theo `web/jsp/guide/*.jsp` đã viết lại ở Task 10.7.
    - Mục 5.5: số liệu kiểm thử theo report Task 13.3.
  - File test: không có; chạy `ant test`.
  - Không đụng: hình (Task 13.5); `docs/SPEC.md`; code.
  - Lưu ý: làm sau Task 13.3. Sửa bằng script trên `word/document.xml` hoặc python-docx, giữ định dạng, chú thích, mục lục; xem trang bằng Word COM để kiểm. Chỗ nào không chắc theo code hay theo SPEC thì ghi report, dừng hỏi. Report liệt kê các hình còn sai sau khi sửa chữ (đầu vào Task 13.5).
  - Xong khi: chữ "Kỹ thuật" chỉ còn trong tên phiếu "hỗ trợ kỹ thuật"; không còn vai CSKH, phòng Kế toán / Dự án; có UC-55, `BR-56` → `BR-62`, `MSG-057` → `MSG-065`; ma trận quyền có cột Tổng hợp; docx mở trong Word không báo lỗi.
  - Nhánh: `docs/task-13.4-report-chu-giai-doan-8-10`.
  - Commit: `docs(report): cập nhật chữ theo cơ cấu mới và vòng đời hợp đồng (Task 13.4)`
- [ ] **Task 13.5:** `docs/Project_Report_v8.docx`, phần hình: diagram và ảnh màn hình theo cơ cấu mới và vòng đời hợp đồng.
  - UC: như Task 13.4.
  - BR / MSG / NFR: như Task 13.4.
  - File sửa: `docs/Project_Report_v8.docx`:
    - Use Case tổng: actor Tổng hợp thay Kỹ thuật, thêm UC-55, tên mới của UC-52; chỉ actor chính, giới hạn quyền ghi vào nhãn.
    - ERD (mục 3.1.5.1 và Hình 4-7, cùng một ảnh): vẽ lại từ `information_schema` của CSDL dựng từ `schema.sql` (V46, 30 bảng, bỏ `schema_migrations`).
    - Cụm Hợp đồng: class / sequence cho tạo (mã để trống), ký, giấy tờ, kỳ thanh toán, bàn giao Tổng hợp, nhắc ký. Cụm Phiếu: người xử lý là Sales. Cụm Nhân viên: vai Tổng hợp. Thêm cụm Thông tin chi nhánh (UC-55). Dùng đúng tên lớp, hàm, JSP trong code.
    - Ảnh chụp màn hình (mục 3.2 "Màn hình 3-x" và chương hướng dẫn): chụp lại màn hình đã đổi giao diện ở Giai đoạn 8–10, ảnh sạch không khoanh; chương hướng dẫn được lấy ảnh `web/guide/` của Task 10.7.
  - File test: không có; chạy `ant test`.
  - Không đụng: phần chữ (Task 13.4), trừ chú thích và số hình đi theo hình thêm / bớt.
  - Lưu ý: làm sau Task 13.4, theo danh sách hình còn sai trong report của 13.4. **Vẽ xong gửi ảnh riêng cho người dùng duyệt, chưa được đồng ý thì không chèn**; xem ảnh trước khi gửi (chữ không dính lifeline, ảnh cao tối đa 9,2 in). Làm theo từng cụm, mỗi cụm duyệt riêng; quá dài cho một lượt thì dừng hỏi tách 13.5a, 13.5b. Thêm / bớt hình thì đánh lại số hình liền mạch và sửa mọi chỗ nhắc số hình. Chụp ảnh trên CSDL tạm dựng từ `schema.sql` và Tomcat riêng, hỏi trước khi tạo và xoá.
  - Xong khi: mọi hình đã duyệt được chèn; không hình nào còn actor Kỹ thuật / CSKH hay tên lớp không có trong code; số hình liền mạch; docx mở trong Word không báo lỗi.
  - Nhánh: `docs/task-13.5-report-hinh-giai-doan-8-10`.
  - Commit: `docs(report): vẽ lại hình theo cơ cấu mới và vòng đời hợp đồng (Task 13.5)`
