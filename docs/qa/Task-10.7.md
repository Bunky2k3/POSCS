# QA Report - Task 10.7

**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 2.432 pass / 0 fail / 0 error / 3 skipped (92 test suites: 89 unit test suites pass 100%, 3 integration test suites trong `test/poscs/integration/` mặc định bỏ qua theo `rules.md` §0; `BUILD SUCCESSFUL`).
**Kiểm tra JSP:** `ant compile-jsps` trả `BUILD SUCCESSFUL` nhưng bỏ qua biên dịch do `compile.jsps=false` trong `nbproject/project.properties`; đã biên dịch trực tiếp cả 6 file `web/jsp/guide/{general,customer,contract,product,ticket,employee}.jsp` (kèm `_top.jspf`, `_bottom.jspf`) bằng `org.apache.jasper.JspC` của Tomcat 11 → `Generation completed with [0] errors` (exit code 0).

**Phạm vi:** Task 10.7 — Hướng dẫn sử dụng theo cơ cấu mới (Giai đoạn 9: 3 vai `Admin / Sales / Tổng hợp`, 3 phòng `Ban giám đốc / Kinh doanh / Tổng hợp`, bỏ vai `Kỹ thuật`) và vòng đời hợp đồng mới (Giai đoạn 10: `UC-33`, `UC-44`, `UC-48`, `UC-49`, `UC-52`, `UC-53`; `BR-58` → `BR-62`; `MSG-061` → `MSG-065`). Đối chiếu với `docs/SPEC.md`, `rules.md`, `db/schema.sql`, mã nguồn Controller/Service/DAO/Model/JSP thực tế và các dòng con của Task 10.7 trong `task_tracker.md`; thay đổi thực tế lấy từ `git status`, `git log main..HEAD` và `git diff main` trên nhánh `docs/task-10.7-guide-co-cau-vong-doi-hop-dong`.

## Danh sách lỗi

Phát hiện **0 🔴 Critical, 1 🟡 Major, 2 🟢 Minor**. Nhìn chung chất lượng cập nhật rất tốt: `contract.jsp` mô tả đầy đủ vòng đời mới từ phiếu GQNCKH tới thanh lý, khớp từng tên nút, câu báo lỗi và danh mục 15 loại giấy tờ với code thực tế; toàn bộ 75/75 ảnh trên đĩa khớp 1-1 với JSP và `tools/guide/shots/*.mjs` (0 ảnh thừa, 0 ảnh thiếu; đã `git rm` ảnh mồ côi `20-gia-tri-hien-hanh.png`); 78/78 hình trong `docs/Report6_HuongDanSuDung.docx` khớp mã băm SHA-256 với ảnh và sơ đồ mới, đánh số liên tục `Hình 2-1` → `Hình 2-78`; toàn bộ 112 anchor nội bộ, 27/27 link từ JSP ứng dụng sang `/guide` và 9/9 tham chiếu chéo giữa các phần đều hợp lệ. Tuy nhiên `customer.jsp` (và một số câu tương tự ở các trang hướng dẫn khác, lan sang bản Word) ghi sai quy tắc `BR-58` về mã hợp đồng khi nói mã *"chỉ được cấp lúc ký"*, và còn hai điểm thiếu nhất quán nhỏ về trình bày quyền ở `contract.jsp` §7.3 và bảng tổng quan của `export_word.py`.

- [x] 🟡 `web/jsp/guide/customer.jsp` › mục `5. Xem chi tiết khách hàng` (dòng ~151) & diễn đạt tương tự tại `web/jsp/guide/product.jsp` (dòng ~123), `web/jsp/guide/general.jsp` (dòng ~159), `web/jsp/guide/ticket.jsp` (dòng ~82, ~118), `web/jsp/guide/customer.jsp` (dòng ~218) (và `docs/Report6_HuongDanSuDung.docx`)
  - Vi phạm: `BR-58` (`docs/SPEC.md` dòng 342 — thuộc phạm vi *"các BR, MSG mới của Task 10.1"* của Task 10.7), `UC-33`, `UC-44`, và mâu thuẫn trực tiếp với `web/jsp/guide/contract.jsp` (dòng 38, 168, 204, 278, 602).
  - Mô tả:
    1. `web/jsp/guide/customer.jsp` dòng 151 viết: *"Hợp đồng chưa ký thì cột mã hiện `Chưa có số` và chưa có ngày ký (dòng thứ hai trên Hình 6), **vì mã hợp đồng chỉ được cấp lúc ký**."*
    2. Theo `BR-58`, `Contract.getDisplayCode()` và chính `contract.jsp` dòng 38: **mã hợp đồng không do hệ thống "cấp lúc ký"** — người dùng tự nhập số theo bản hợp đồng giấy, được để trống khi còn Nháp nhưng có thể nhập bất cứ lúc nào khi còn Nháp, và **bắt buộc phải nhập trước khi bấm Ký** (thiếu mã thì bấm Ký bị chặn bởi `MSG-061`). Hợp đồng Nháp **đã nhập mã** sẽ hiển thị đúng mã đó, không hiện `Chưa có số`; chỉ hợp đồng **chưa có mã** mới hiện `Chưa có số`. Câu *"chỉ được cấp lúc ký"* làm người dùng hiểu sai quy trình ký.
    3. Cách viết gộp *"hợp đồng chưa ký"* = *"chưa có số"* cũng xuất hiện ở:
       - `web/jsp/guide/product.jsp` dòng 123: *"Hợp đồng chưa ký thì chưa có mã, cột Mã hợp đồng ghi `Chưa có số`."*
       - `web/jsp/guide/general.jsp` dòng 159: *"kể cả hợp đồng chưa ký: các hợp đồng này hiện `Chưa có số`."*
       - `web/jsp/guide/ticket.jsp` dòng 82 (*"hợp đồng chưa ký hiện `Chưa có số`"*), dòng 118 (*"hợp đồng chưa ký ghi `Chưa có số`"*).
       - `web/jsp/guide/customer.jsp` dòng 218: *"Hợp đồng chưa ký (`Chưa có số`), đã hết hạn hay đã thanh lý thì không chặn việc xoá."* (bản Nháp dù đã nhập mã vẫn không chặn xoá theo `CustomerDAO.hasActiveContracts()`).
    4. Vì `export_word.py` dựng `docs/Report6_HuongDanSuDung.docx` trực tiếp từ 6 JSP trên, nội dung sai lệch này đang có mặt trong cả bản Word.
  - Gợi ý sửa: Chỉnh câu chữ ở các vị trí trên về đúng `BR-58` và thống nhất với `contract.jsp`, rồi chạy lại `tools/guide/word/export_word.py` xuất lại `docs/Report6_HuongDanSuDung.docx` (không cần chụp lại ảnh):
    - `customer.jsp` dòng 151: *"Hợp đồng nháp chưa có mã thì cột mã hiện `Chưa có số` và chưa có ngày ký (dòng thứ hai trên Hình 6), vì khi còn Nháp được để trống mã và chỉ bắt buộc nhập trước khi ký."*
    - `customer.jsp` dòng 218: *"Hợp đồng còn Nháp (chưa ký), đã hết hạn hay đã thanh lý thì không chặn việc xoá."*
    - `product.jsp` dòng 123: *"Hợp đồng nháp chưa có mã thì cột Mã hợp đồng ghi `Chưa có số`."*
    - `general.jsp` dòng 159: *"kể cả hợp đồng nháp (bản chưa có mã hiện `Chưa có số`)."*
    - `ticket.jsp` dòng 82 & 118: đổi *"hợp đồng chưa ký"* thành *"hợp đồng chưa có mã"* (hoặc *"hợp đồng nháp chưa có mã"*).
  - Đã sửa (bước Fix): sáu câu theo đúng gợi ý; câu ở `customer.jsp` dòng 151 thêm dẫn chéo "hướng dẫn Hợp đồng, mục 1" (Word đổi thành số mục). Đã xuất lại `docs/Report6_HuongDanSuDung.docx`: không còn "chỉ được cấp lúc ký", không còn câu gộp "chưa ký … Chưa có số". Không chụp lại ảnh (chữ trên ảnh không đổi).

- [x] 🟢 `web/jsp/guide/contract.jsp` › mục `7.3. Ghi nhận đã thu` (dòng ~380)
  - Vi phạm: Nhất quán trình bày quyền tác nhân `UC-53` (`docs/SPEC.md` §3.3 & §4.4).
  - Mô tả: Mục cha `7. Kỳ thanh toán` (dòng 326) gắn dải quyền `Admin · Sales · Tổng hợp (gạch ngang)`. Mục con `7.3. Ghi nhận đã thu` (dòng 380) không có dải `.guide-who` riêng dù vai **Tổng hợp** có quyền thực hiện thao tác này (`UC-53`, như câu văn ngay bên dưới có nêu), trong khi mọi mục con khác trong `contract.jsp` có quyền khác mục cha (`5.2`, `6.2`, `7.4`, `8.1`) đều có `<div class="guide-who">` riêng.
  - Gợi ý sửa: Thêm ngay dưới `<h3 id="da-thu">7.3. Ghi nhận đã thu</h3>`:
    ```jsp
    <div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Tổng hợp</span></div>
    ```
    rồi xuất lại `docs/Report6_HuongDanSuDung.docx`.
  - Đã sửa (bước Fix): thêm đúng dải trên ngay dưới tiêu đề 7.3; đã xuất lại docx.

- [x] 🟢 `tools/guide/word/export_word.py` › `write_overview()` (dòng ~935–943) & `tools/guide/word/diagrams.py` › `tong_quan()` (dòng ~231–232)
  - Vi phạm: Nhất quán mô tả phạm vi cấm truy cập `UC-55` (`docs/SPEC.md` §3.3 & §5.8) giữa bảng tổng quan bản Word và hướng dẫn web.
  - Mô tả: Trong bảng vai trò ở mục `3.1 Tổng quan` (`export_word.py` dòng 936–943), dòng `Admin` ghi rõ *"và Thông tin chi nhánh"*, nhưng cột *"Chỉ xem, hoặc không vào được"* của `Sales` (dòng 940) và `Tổng hợp` (dòng 942) chỉ ghi *"trang Nhân viên"*, bỏ sót *"Thông tin chi nhánh"* (tương tự dòng chú thích cuối sơ đồ `diagrams.py` dòng 232). Trong khi đó `general.jsp` dòng 139 và `employee.jsp` dòng 267 đều ghi rõ cả hai trang chỉ dành cho Admin.
  - Gợi ý sửa: Bổ sung *"và Thông tin chi nhánh"* vào mô tả của `Sales` và `Tổng hợp` tại `export_word.py` dòng 940, 942 (và `diagrams.py` dòng 232 kèm vẽ lại `tong-quan.png` nếu sửa sơ đồ), rồi xuất lại `docs/Report6_HuongDanSuDung.docx`.
  - Đã sửa (bước Fix): bảng vai trò ở `export_word.py` thêm "Thông tin chi nhánh" cho Sales và Tổng hợp; đã xuất lại docx. **Không** sửa chú thích sơ đồ `diagrams.py` dòng 232: `tong-quan.png` người dùng đã duyệt ngày 21/11/2026, sửa là phải gửi duyệt lại; câu đó không sai (chỉ chưa nhắc Thông tin chi nhánh) và bảng vai trò ngay dưới sơ đồ trong bản Word đã ghi đủ.

## Chi tiết kiểm tra

### A. Đối chiếu nghiệp vụ, UC / BR / MSG và giao diện thực tế

| Hạng mục | Kết quả |
|---|---|
| Cơ cấu 3 vai (`Admin / Sales / Tổng hợp`) & 3 phòng (`Ban giám đốc / Kinh doanh / Tổng hợp`), bỏ vai `Kỹ thuật` (Giai đoạn 9, Quyết định 13) | Đạt. Đã đối chiếu cả 6 JSP hướng dẫn (`general.jsp`, `customer.jsp`, `contract.jsp`, `product.jsp`, `ticket.jsp`, `employee.jsp`), `export_word.py`, `diagrams.py` và `docs/Report6_HuongDanSuDung.docx` với `sidebar.jsp`, `PermissionService`, `AccessControl`, `branchInfo.jsp`, `TechnicalSupportTicketController`, `EmployeeDAO.findActiveByRole`. Mọi bảng quyền, dải `Ai làm được`, mô tả người xử lý phiếu (`Sales`), quản lý sản phẩm (`Admin / Sales`), và quyền vai `Tổng hợp` (xem KH/HĐ/SP, xác nhận bàn giao phòng Tổng hợp, ghi nhận đã thu kèm số thực thu, chặn vào Phiếu hỗ trợ với `MSG-022`) đều khớp code thực tế (trừ 2 điểm 🟢 Minor về trình bày ở trên). |
| Vòng đời hợp đồng, mã hợp đồng (`UC-33`, `UC-44`, `BR-58`, `MSG-061`) | Chưa đạt trọn vẹn (**1 🟡 Major**). `contract.jsp` tả đúng hoàn toàn `BR-58` (để trống khi còn Nháp, hiện `Chưa có số`, bắt buộc trước khi Ký, chặn bằng `MSG-061`, không tự sinh mã). Tuy nhiên `customer.jsp` dòng 151 ghi sai *"vì mã hợp đồng chỉ được cấp lúc ký"*, và các trang `product.jsp`, `general.jsp`, `ticket.jsp`, `customer.jsp` còn gộp *"hợp đồng chưa ký"* với *"chưa có số"* (xem 🟡 #1). |
| Giấy tờ dạng ô tích (`UC-52`, `BR-59`, `MSG-062`, `MSG-063`) | Đạt. `contract.jsp` mục 9 & 9.1 khớp 100% `ContractDocument.TYPES` (đủ 15 loại theo đúng thứ tự trên màn hình), tên nút (`Thêm tờ`, `Lưu tờ này`, `Mở`, `Mở bản PDF`), độ dài số giấy ≤ 50 ký tự, ngày trên giấy bắt buộc & không sau hôm nay (`MSG-062`, `MSG-063`), link `http://`/`https://` không bắt buộc, loại `Khác` bắt buộc tên, bỏ tích = huỷ mềm bắt buộc lý do, làm được ở mọi tiến độ kể cả sau thanh lý. Đã gỡ bỏ hoàn toàn đoạn tả tab Tài liệu kiểu bắt buộc dán link cũ. |
| Kỳ thanh toán (`UC-53`, `BR-18`, `BR-60`, `MSG-064`) | Đạt. `contract.jsp` mục 7, 7.1–7.4 khớp `ContractPaymentService`, `ContractPayment.statusOn()`, `ContractControllerHelper`, `updatecontract.jsp` và `viewcontractdetail.jsp`: loại `Tạm ứng / Thanh toán`, ô `%` chỉ tính sẵn không lưu, số ngày 1–999, hạn thu để trống được khi lập, `Đã nộp hồ sơ` (`MSG-064`, hạn tự tính hoặc nhập tay để trống được, nộp lại tính lại), `Đã thu` kèm số thực thu > 0 không bắt lý do (cả vai Tổng hợp ở trang chi tiết), đủ 4 tình trạng suy ra (`Chưa nộp hồ sơ → Chờ thu → Quá hạn → Đã thu`), sửa kỳ đã nộp có số ngày tự tính lại hạn thu, doanh thu Dashboard cộng số thực thu theo ngày thu (nhận xét nhỏ về `.guide-who` ở 🟢 #1). |
| Bước giao hàng & nhắc ký sau 15 ngày (`UC-10`, `UC-11`, `UC-36`, `BR-61`, `BR-62`, `MSG-065`) | Đạt. `contract.jsp`, `general.jsp` (mục 5 & 6) và `vong-doi-hop-dong.png` khớp `Contract`, `ContractDAO`, `DashboardService`, `NotificationService`: `Chuẩn bị hàng` (GQNCKH/ĐCNB), `Đã giao – chưa ký` (Biên bản giao nhận hàng hoá, ngày giao sớm nhất), mất nhãn sau khi Ký, ô Dashboard không theo kỳ bấm sang danh sách đã lọc, nhắc người phụ trách một lần khi đủ ≥ 15 ngày với câu mẫu đúng `MSG-065`. |
| Bàn giao phòng Tổng hợp (`UC-48`, `UC-49`) & Thông tin chi nhánh (`UC-55`, `BR-56`, `BR-57`, `MSG-057..060`) | Đạt. `contract.jsp` mục 8 & 8.1 khớp `updatecontract.jsp`, `viewcontractdetail.jsp`, `handoverqueue.jsp` (phòng Tổng hợp tích sẵn, cảnh báo vàng không chặn ký, dòng *"Chỉ người của phòng Tổng hợp xác nhận được"*). `employee.jsp` mục 9 khớp `branchInfo.jsp` (đủ 4 trường, độ dài, định dạng SĐT/fax, `MSG-057..060`). |
| Thông báo lỗi trên giao diện | Đạt. Đã đối chiếu toàn bộ bảng *"Thông báo lỗi thường gặp"* của 6 JSP hướng dẫn (`general`, `customer`, `contract`, `product`, `ticket`, `employee`) với chuỗi thực tế trong `login.jsp`, `forgotPassword.jsp`, `verifyOtp.jsp`, `resetPassword.jsp`, `changePassword.jsp`, `updateProfile.jsp`, `updatecontract.jsp`, `viewcontractdetail.jsp`, `handoverqueue.jsp`, `addnewTicket.jsp`, `updateTicket.jsp`, `viewdetailProduct.jsp`, `branchInfo.jsp`: khớp nguyên văn. |

### B & C. Kịch bản chụp ảnh, ảnh chụp, file Word & quy chuẩn (§3–§4)

- **Kịch bản `tools/guide/capture.mjs` & `tools/guide/shots/*.mjs`:** Đã chuyển hoàn toàn sang bộ tài khoản mẫu Task 10.6 (`admin`, `tranquocbao`, `leminhhoang`, `phamthutrang`, `doducanh`, `vuthihang`, `hoangthimai`, `buivannam`, `ngothilan`); không còn `sales1..6`, `cskh*`, `kythuat*`, `tonghop1`, `nguyendat`, `HD-0015`. Đã đối chiếu toàn bộ ID cố định (`user_id`, `enterprise_id`, `contract_id`, `province_id`) và khối SQL *"CHUẨN BỊ BẢN SAO CSDL"* với `db/schema.sql`: khớp 100% (bao gồm câu `UPDATE users` trả lại thông tin `tranquocbao` và câu `UPDATE branch_info` ở `contract.mjs`).
- **Đối chiếu ảnh chụp & khoanh số:** Kiểm tra tự động xác nhận 100% bước đánh số trong `<ol class="guide-steps">` của cả 6 JSP khớp tập số khoanh đỏ `marks` trong 6 kịch bản `.mjs`. Đã xem trực quan 3 sơ đồ (`tong-quan.png`, `vong-doi-hop-dong.png`, `trang-thai-phieu.png`) và các ảnh chụp mới/trọng yếu của cả 6 phân hệ (`contract/01..07, 09..11, 13, 13b..13d, 14..19, 21`, `general/01..08, 10..11`, `customer/01, 06, 08b`, `product/03, 04`, `ticket/01..07`, `employee/01..04, 06, 08`): hiển thị rõ nét, dữ liệu giả theo Task 10.6, không có dữ liệu khách hàng hay số tài khoản thật.
- **Liên kết & tham chiếu chéo:** 112/112 link nội bộ `#anchor` trong 6 JSP hợp lệ; 27/27 nút/link `/guide?module=...#...` từ JSP ứng dụng trỏ tới đúng `id` có thật; 9/9 tham chiếu chéo *"hướng dẫn X, mục Y"* hợp lệ và đã được `export_word.py` chuyển sạch sang số mục trong Word.
- **File `docs/Report6_HuongDanSuDung.docx`:**
  - `docProps/core.xml`: `lastModifiedBy = tools/guide/word/export_word.py`, `title = Report 6 – Hướng dẫn sử dụng phần mềm POSCS`, bìa `– Hà Nội, Tháng 11 năm 2026 –`.
  - Mục `I. Lịch sử thay đổi` có dòng `21/11/2026 | S | ... (Task 10.7)`.
  - Mục `II.1` & `II.2`: khớp `V1 … V46` và `10 tài khoản thử (mật khẩu Poscs@123, riêng admin là Admin@123)`; câu `INSERT INTO users` Bước 6 dùng đúng `'Ban giám đốc'`.
  - 78/78 chú thích hình (`Hình 2-1` → `Hình 2-78`) liên tục, không còn chuỗi `Hình N` hay `hướng dẫn X, mục Y` chưa đổi; mã băm SHA-256 của 75 ảnh trong `web/guide/` + 3 sơ đồ trong `tools/guide/word/` khớp 100% với 78/79 tệp media trong `.docx` (tệp còn lại `word/media/image1.png` là logo trang bìa từ file mẫu).
- **Quy chuẩn (§3–§4):** Cả 6 JSP hướng dẫn thuần hiển thị (không logic nghiệp vụ), UTF-8 hợp lệ, biên dịch sạch bằng Jasper Tomcat 11; không có `TODO`/`FIXME`/placeholder (chỉ có ghi chú `// TẠM (Task 10.7)` có chủ đích tại `tools/guide/shots/general.mjs` dòng 185 cho lỗi OTP đã tách sang Task 12.1).

### D, E & F. Phạm vi, Kiểm thử & Tiêu chí "Xong khi"

- **Nhánh & Git:** Đang ở đúng nhánh `docs/task-10.7-guide-co-cau-vong-doi-hop-dong`, không phải `main`; `git log main..HEAD` rỗng (chưa commit bước Code); Task 10.7 trong `task_tracker.md` vẫn giữ `[ ]`.
- **Phạm vi & Không đụng:**
  - `git diff main -- src/ test/ docs/Project_Report_v8.docx docs/SPEC.md PERMISSIONS.md` hoàn toàn rỗng — không đụng code Java, test cũ hay `Project_Report`.
  - `README.md` không liệt kê 3 vai trò (chỉ trỏ sang `PERMISSIONS.md`), giữ nguyên là đúng dòng *"nếu có liệt kê vai thì sửa"*.
  - `web/guide/contract/20-gia-tri-hien-hanh.png` đã được `git rm` (staged deleted) theo Quyết định 17.
  - Ngoài danh sách nêu tên ở dòng *"File sửa"*, nhánh có cập nhật `tools/guide/word/diagrams.py` cùng 3 ảnh sơ đồ `tools/guide/word/{tong-quan,trang-thai-phieu,vong-doi-hop-dong}.png` và thêm 4 ảnh mới `web/guide/contract/{13b-lap-ky,13c-nop-ho-so,13d-da-thu,17b-them-to}.png` (hiện untracked) — hợp lý và cần thiết để bản Word và `contract.jsp` đồng bộ với cơ cấu và vòng đời hợp đồng mới.
- **Kiểm thử:** `ant test` pass 2.432 / 0 fail / 0 error / 3 skipped (`BUILD SUCCESSFUL`); 6 JSP hướng dẫn dịch sạch 0 lỗi qua `JspC` Tomcat 11.
- **Tiêu chí "Xong khi":**
  - `grep -rn "Kỹ thuật" web/jsp/guide` chỉ còn chữ trong tên phiếu *"hỗ trợ kỹ thuật"* → **Đạt** (0 kết quả viết hoa `Kỹ thuật`; kiểm không phân biệt hoa thường chỉ còn *"Phiếu hỗ trợ kỹ thuật"* và *"thông số kỹ thuật"* trong `product.jsp`).
  - Hướng dẫn tả đủ luồng `GQNCKH → ĐCNB → giao hàng → ký → bàn giao Tổng hợp → nộp hồ sơ → thu tiền → thanh lý` → **Đạt** (xem thêm 🟡 #1 để sửa câu về `BR-58` ở `customer.jsp` và các trang phụ cho chuẩn xác tuyệt đối).
  - File `.docx` có dòng Task 10.7 → **Đạt**.
  - Mọi ảnh trong `web/guide/` đều có JSP ở `web/jsp/guide/` trỏ tới và có kịch bản ở `tools/guide/shots/` sinh ra → **Đạt** (**75 trên đĩa = 75 trong JSP = 75 trong `shots/*.mjs`**, 0 thừa, 0 thiếu).

## Ghi chú ngoài phạm vi

- **Mật khẩu mặc định `DB_PASSWORD = '1234'` trong `tools/guide/word/export_word.py` dòng 867 (có sẵn từ trước Task 10.7):** `DBContext.java` dòng 22 đã bỏ mật khẩu mặc định hardcode từ Task 7.2a (`config("DB_PASSWORD", "")`, không có mặc định), khớp `README.md` dòng 154. Bảng Bước 4 trong `Report6_HuongDanSuDung.docx` hiện vẫn in `1234` ở cột *"Bỏ trống thì"* — nên sửa thành *"Rỗng (bắt buộc khai báo)"* ở lần cập nhật tài liệu triển khai (`Task 10.9` / `11.5`) hoặc tranh thủ chỉnh cùng lượt xuất lại `.docx` nếu người dùng đồng ý.
  - Đã sửa (bước Fix, người dùng đồng ý 21/11/2026): ô "Bỏ trống thì" của `DB_PASSWORD` thành "Không có mặc định (rỗng), phải khai báo…"; dòng Task 10.7 ở Lịch sử thay đổi ghi thêm ý này; đã xuất lại docx.
- **Các lỗi ứng dụng lộ ra lúc chụp ảnh đã chốt tách sang Giai đoạn 12 (21/11/2026, không tính là lỗi của Task 10.7):**
  - `tools/guide/shots/general.mjs` dòng 185–194 có đoạn lách tạm chèn ô ẩn `otp` (sẽ gỡ ở Task 12.1 sau khi sửa `PasswordResetController`); ảnh `general/01-dang-nhap.png` và `04-quen-mat-khau.png` còn dòng *"Phòng Nhân sự / IT"* của giao diện hiện tại (Task 12.1).
  - Ảnh `contract/03-doc-dong.png` khoanh số 4 bị cắt *"Đã giao – chưa ký"* thành *"…"* do CSS `.delivery-tag` của `listcontract.jsp`; ảnh `product/04-chi-tiet.png` gãy chữ *"Chưa hiệu / lực"* và thừa dòng trống đầu ô Mô tả (Task 12.2 sẽ sửa và chụp lại đúng các ảnh này).
  - Mục Xuất Excel ở `contract.jsp` dòng 602 (câu *"cùng phạm vi … và cùng dải 'Đang xem'"* có sẵn từ `main`) và `customer.jsp` dòng 229: thực tế link Xuất Excel ở `listcontract.jsp` dòng 296 chưa gửi `view`/`month` (đã ghi nhận ở Task 10.5) và `listcustomer.jsp` dòng 319 chưa gửi `view` (Task 12.3).
- **Tình trạng working tree cho bước Commit:**
  - Cần nhớ `git add` đủ 4 ảnh mới đang untracked (`web/guide/contract/13b-lap-ky.png`, `13c-nop-ho-so.png`, `13d-da-thu.png`, `17b-them-to.png`), `tools/guide/word/diagrams.py`, 3 sơ đồ `tools/guide/word/{tong-quan,trang-thai-phieu,vong-doi-hop-dong}.png`, và `docs/qa/Task-10.7.md`. Ảnh `web/guide/contract/20-gia-tri-hien-hanh.png` đã nằm sẵn ở staged deleted.
  - Các file ngoài Task 10.7 đang có trong working tree (KHÔNG add vào commit Task 10.7; thuộc Task 10.8): 2 file đã xoá `docs/kichbandemoposcs.html`, `docs/kichbandemoposcs.pdf` và 3 file untracked `docs/HuongDan_DoiEmailGui.md`, `docs/PROMPTS.md`, `task_tracker_deploy.md`. Riêng `task_tracker.md` đang chứa thêm phần cập nhật kế hoạch ngày 21/11/2026 (Quyết định 17–18, Task 10.8, 10.9, Giai đoạn 12) cùng dòng xoá `20-gia-tri-hien-hanh.png` của Task 10.7.
