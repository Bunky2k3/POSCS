# QA Report - Task 10.5

**Kết luận:** CẦN SỬA
**Kết quả `ant compile-jsps test`:** `compile-jsps` báo `BUILD SUCCESSFUL` (trên máy này `compile.jsps=false` bỏ qua bước dịch trong Ant; đã dịch kiểm chứng riêng 3/3 file `.jsp` đã sửa là `web/dashboard.jsp`, `web/jsp/sale/listcontract.jsp` và `web/jsp/sale/viewcontractdetail.jsp` bằng `org.apache.jasper.JspC` của Tomcat 11.0 với `-source 21 -target 21 -failFast`: **0 errors**, sinh sạch `dashboard_jsp.class`, `listcontract_jsp.class` và `viewcontractdetail_jsp.class`; các khối `<script>` nội tuyến kiểm tra bằng `node --check`: 0 lỗi); **`ant test`: 2.431 pass / 0 fail / 0 error / 3 skipped** (92 test suites: 89 unit test suites pass 100%, 3 integration test suites trong `test/poscs/integration/` mặc định bỏ qua; `BUILD SUCCESSFUL`, tăng +53 test so với Task 10.4).

**Phạm vi:** Task 10.5 — Bước giao hàng ("Chuẩn bị hàng" / "Đã giao – chưa ký") và nhắc ký sau 15 ngày (`UC-10`, `UC-11`, `UC-36`, `SPEC §5.7`; `BR-61`, `BR-62`, `MSG-065`, `NFR-08`). Đối chiếu với `docs/SPEC.md`, `rules.md` và các dòng con của Task 10.5 trong `task_tracker.md`; thay đổi thực tế lấy từ `git status`, `git log main..HEAD` và `git diff main` trên nhánh `feat/task-10.5-buoc-giao-hang-nhac-ky`.

## Danh sách lỗi

Phát hiện **1 🔴 Critical, 1 🟡 Major, 1 🟢 Minor**. Phần backend/SQL suy ra bước giao hàng (`BR-61`), đếm Dashboard (`UC-36`), lọc/xuất Excel, trang chi tiết (`UC-11`), lịch nhắc ký sau 15 ngày (`BR-62`, `MSG-065`), điều hướng thông báo và bộ unit test (+53 test, 0 sửa test cũ) được triển khai chắc chắn; tuy nhiên ô lọc bước giao hàng mới trên `listcontract.jsp` bị quên gắn sự kiện tự submit nên không hoạt động trên trình duyệt, đồng thời cần xác nhận phạm vi các file ngoài danh sách và 2 file bị xoá trong working tree.

- [x] 🔴 `web/jsp/sale/listcontract.jsp` › inline `<script>` tự submit bộ lọc (dòng ~811–816) & `<select id="filterDelivery">` (dòng ~541–545)
  - Vi phạm: `UC-10`, `BR-61`, tiêu chí ô lọc bước giao hàng của Task 10.5.
  - Mô tả: Form lọc `#filterForm` (dòng 341–611) **không có nút "Lọc" (`type="submit"`) chung**; toàn bộ các `<select>` và `<input type="checkbox">` tự gửi form qua mảng ID gắn sự kiện `change` tại dòng 811–816 (ngay comment dòng 805–807 đã cảnh báo: *"Thêm ô lọc MỚI thì phải thêm id vào đây, nếu không tích vào không có gì xảy ra -- form chỉ gửi khi gõ Enter ở ô tìm kiếm. Đã dính với 'filterWaitingAny' đúng một lần."*). Task 10.5 đã thêm `<select id="filterDelivery" name="delivery">` (dòng 541–545) nhưng **quên thêm `'filterDelivery'`** vào mảng ở dòng 811–812 (`web/js/appshell.js` cũng không xử lý ô này). Hệ quả: khi người dùng mở "Lọc thêm" trên trang danh sách hợp đồng và chọn `"Chuẩn bị hàng"` hoặc `"Đã giao – chưa ký"`, trang không gửi form và không lọc được dữ liệu (trừ khi tự quay lên ô tìm kiếm nhấn Enter hoặc đổi ô lọc khác).
  - Gợi ý sửa: Bổ sung `'filterDelivery'` vào mảng ID tại dòng 811–812 của `web/jsp/sale/listcontract.jsp`:
    ```javascript
    ['filterProgress', 'filterView', 'filterScope', 'filterWaitingAny', 'filterType',
     'filterWaitingDept', 'filterDelivery']
    ```
    Sau khi sửa, dịch kiểm chứng lại `web/jsp/sale/listcontract.jsp` bằng Jasper của Tomcat 11 và kiểm tra JS bằng `node --check`.
  - **Đã sửa (Fix):** thêm `'filterDelivery'` vào mảng id tự gửi form; comment ngay trên ghi thêm lần dính thứ hai. Test mới `ContractListControllerTest.listJsp_everyFilterControlInTheForm_autoSubmitsOnChange` đọc `web/jsp/sale/listcontract.jsp` (JUnit chạy ở `${basedir}`), lấy mọi `<select>` / ô tích có id trong `#filterForm` và bắt từng id phải có trong mảng tự gửi form; đã thử phép đối chiếu đó trên bản chưa sửa thì báo thiếu đúng `filterDelivery`. Dịch lại `listcontract.jsp` bằng `org.apache.jasper.JspC` (Tomcat 11, `-source 21 -target 21 -failFast`): 0 lỗi; khối `<script>` nội tuyến qua `node --check`: 0 lỗi.

- [x] 🟡 Phạm vi file sửa ngoài danh sách của Task 10.5 (5 file kỹ thuật + 2 file bị xoá trong working tree)
  - Vi phạm: `rules.md` §3 (*"Trước khi xóa hoặc sửa bất cứ thứ gì ngoài phạm vi file của task, phải báo trước: xóa/sửa gì, vì sao, ảnh hưởng ra sao, rồi chờ người dùng đồng ý"*) và checklist D (`docs/PROMPTS.md`).
  - Mô tả:
    1. Có 5 file nguồn/test nằm ngoài dòng "File sửa" / "File test" của Task 10.5 trong `task_tracker.md`, dù cả 5 thay đổi đều hợp lý về mặt kỹ thuật và giữ tương thích ngược hoàn toàn:
       - `src/java/poscs/common/ListScope.java` (+ `test/poscs/common/ListScopeTest.java`, +4 test): gắn `deliveryStage` vào `ListScope` thay vì thêm tham số thứ 14 cho `ContractDAO.findAll` (comment ở `ListScope.java` dòng 36 ghi *"người dùng chốt 21/11/2026, Task 10.5"*).
       - `src/java/poscs/service/DashboardSummary.java`: thêm trường `deliveredUnsignedCount` và constructor 19 tham số (giữ nguyên constructor 18 tham số cũ mặc định `0`).
       - `src/java/poscs/controller/DashboardController.java` (+ `test/poscs/controller/DashboardControllerTest.java`, +2 test): đặt request attribute `deliveredUnsignedCount` cho `web/dashboard.jsp`.
       - Do QA chạy ở phiên chat mới không thấy lịch sử trao đổi ở bước Code, cần xác nhận rõ người dùng đã đồng ý cả 5 file này (đặc biệt 4 file chưa có ghi chú xác nhận trong code).
    2. Ngoài ra, `git status` ghi nhận 2 file đã theo dõi trên `main` đang bị **xoá** trong working tree và không liên quan đến Task 10.5: `docs/kichbandemoposcs.html` và `docs/kichbandemoposcs.pdf`. Cần làm rõ đây là xoá chủ ý ngoài task hay xoá nhầm, và **tuyệt đối không** đưa vào commit của Task 10.5 (hoặc khôi phục nếu xoá nhầm).
  - Gợi ý sửa: Không hoàn tác 5 file kỹ thuật hợp lý; ở bước Fix xác nhận sự đồng ý của người dùng (hoặc xin đồng ý bổ sung với các file chưa hỏi) và ghi rõ ngay dưới mục này, đồng thời làm rõ tình trạng 2 file `docs/kichbandemoposcs.*`.
  - **Xác nhận (Fix):**
    1. Người dùng đã đồng ý cả 5 file ở bước Code, ngày 21/11/2026, trước khi sửa: chọn "Gắn vào ListScope" (`ListScope.java` + `ListScopeTest.java`, giữ nguyên số tham số các hàm DAO nên không sửa test cũ nào) và "Sửa thêm 2 file" (`DashboardSummary.java`, `DashboardController.java` + `DashboardControllerTest.java`). Cùng lúc người dùng chốt ô Dashboard chỉ đếm hợp đồng bán, không theo kỳ. Không hoàn tác file nào.
    2. `docs/kichbandemoposcs.html`, `docs/kichbandemoposcs.pdf`: không phải do Task 10.5 xoá (đầu phiên Code hai file còn nguyên). Người dùng chọn **để nguyên, không khôi phục** (21/11/2026). Bước Commit **không** `git add` / `git rm` hai file này.

- [x] 🟢 `src/java/poscs/dao/ContractDAO.java` › `buildStatusSummarySql()` (dòng ~640–683)
  - Vi phạm: `rules.md` §4 (*"Hàm > 40 dòng... xem xét tách module phụ trợ"*).
  - Mô tả: Trước Task 10.5 (`main`), `buildStatusSummarySql()` dài 39 dòng; sau khi thêm 4 dòng gắn `deliveryClause` (dòng 676–680), phương thức tăng lên **44 dòng** (vượt nhẹ ngưỡng 40 dòng; toàn bộ các phương thức mới/sửa khác trong Task 10.5 đều ≤ 37 dòng).
  - Gợi ý sửa: Tách khối `if (scope != null) { ... }` (dòng 667–681) sang một helper private ngắn (vd. `appendScopeToSummarySql(String sql, ListScope scope, List<Object> scopeParams)`) để `buildStatusSummarySql()` ≤ 40 dòng (hoặc giữ nguyên và ghi lý do nếu muốn hạn chế đụng thêm `ContractDAO`).
  - **Đã sửa (Fix):** tách sang `private String listScopeSummaryClauses(ListScope scope, List<Object> scopeParams)` (19 dòng; trả `""` khi `scope == null`), giữ nguyên thứ tự mệnh đề và thứ tự tham số (người/địa bàn → cửa sổ thời gian → bước giao hàng). `buildStatusSummarySql()` còn 26 dòng. Không đổi hành vi: các test sẵn có của `countStatusSummary` (gồm `countStatusSummary_withDefaultScope_appliesOwnerProvinceAndActiveWindow`, `countStatusSummary_deliveredFilter_kpiCountsTheSameSetAsTheList`) vẫn pass.

## Kết quả bước Fix

- `ant test`: **2.435 test, 2.432 pass / 0 fail / 0 error / 3 skipped** (+1 test so với lúc QA: `listJsp_everyFilterControlInTheForm_autoSubmitsOnChange`). `git diff main -- test` vẫn 0 dòng bị xoá hay sửa.
- `ant compile-jsps`: `BUILD SUCCESSFUL` nhưng không dịch gì trên máy này (`compile.jsps=false`); đã dịch riêng `web/jsp/sale/listcontract.jsp` bằng Jasper của Tomcat 11: 0 lỗi.

## Chi tiết kiểm tra

### A. Đối chiếu nghiệp vụ (UC, BR, MSG)

| Hạng mục | Kết quả |
|---|---|
| `BR-61` — Suy ra bước giao hàng & ngày giao (`ContractDAO.java`, `Contract.java`) | Đạt. Ba hằng tên loại giấy tờ (`Phiếu GQNCKH`, `Phiếu ĐCNB`, `Biên bản giao nhận hàng hoá`) khớp từng ký tự với `ContractDocument.TYPES` và `SPEC.md` (có test khoá). Hai nhãn bước giao hàng (`Chuẩn bị hàng`, `Đã giao – chưa ký` dùng đúng dấu `–` U+2013) khớp SPEC. Suy ra mỗi lần đọc chỉ khi `c.progress_status = 'Nháp'` và giấy tờ chưa huỷ (`is_deleted = 0`), ưu tiên `"Đã giao – chưa ký"` nếu có biên bản giao nhận; `delivered_date = MIN(dd.doc_date)` trong các biên bản giao nhận chưa huỷ; rời `Nháp` trả `NULL`. Toàn bộ 6 chỗ gọi `mapRow()` trong `ContractDAO` (`findAll`, `findActiveInPeriod`, `findExpiringSoon`, `findDeliveredUnsigned`, `findById`, `findAmendmentsByParentId`) đều truy vấn từ `SELECT_BASE`. |
| `UC-10` — Danh sách hợp đồng & lọc bước giao hàng (`ContractListController`, `ContractFilterHelper`, `listcontract.jsp`) | Chưa đạt trọn vẹn (**1 🔴 Critical**: xem #1 do thiếu `'filterDelivery'` ở mảng auto-submit JS). Phần còn lại đạt: nhãn bước giao hàng hiển thị dưới nhãn tiến độ và tự ẩn sau khi ký; tham số `delivery` được chuẩn hoá whitelist (`preparing` / `delivered`); cùng một `ListScope` đi qua `findAll`, `countAll` và `countStatusSummary` nên bảng, phân trang và 4 chip trạng thái đếm khớp tập; `FilterState` sinh chip `"Bước giao hàng: ..."` cho phép bỏ lọc, giữ `delivery` ở chip trạng thái và các chip lọc khác, tăng `advancedCount()` để mở sẵn khối "Lọc thêm"; link phân trang và nút Xuất Excel giữ `&delivery=...`. |
| `UC-41` — Xuất Excel giữ lọc bước giao hàng (`ContractExportController`, `ContractExportService`) | Đạt. `ContractExportController.resolveListScope()` gắn `.withDeliveryStage(ContractFilterHelper.deliveryFilterOf(request))` vào `ListScope` và truyền thẳng qua `ContractExportService.exportContractsToExcel()` xuống `ContractDAO.findAll()`. |
| `UC-11` — Chi tiết hợp đồng (`viewcontractdetail.jsp`) | Đạt. Hiển thị nhãn `.delivery-pill` cạnh tiến độ ở đầu trang; khối cảnh báo dưới thanh tiến trình hiện bước giao hàng, ngày giao định dạng `dd/MM/yyyy` (`NFR-08`) và số ngày đã giao (`${contract.deliveredDays} ngày`) khi ở bước `"Đã giao – chưa ký"`, hoặc thông báo `"Chuẩn bị hàng"`; khi ký xong (`deliveryStage == null`) cả nhãn lẫn cảnh báo đều biến mất. |
| `UC-36` — Ô `"Đã giao – chưa ký"` trên Dashboard (`DashboardService`, `DashboardSummary`, `DashboardController`, `dashboard.jsp`) | Đạt. Đếm hợp đồng bán `"Đã giao – chưa ký"` theo phạm vi người/địa bàn đang chọn, tính tới hôm nay (không lọc theo kỳ vì kỳ đi theo `signing_date`, bản Nháp chưa ký). Thẻ KPI mới chia lưới 4 cột (`col-lg-3`), bấm vào mở `/contract?action=list&delivery=delivered&month=all&view=...` (mang theo các `provinceId` đang chọn khi `scopeFilter != 'mine'`). |
| `BR-62`, `MSG-065` & `SPEC §5.7` loại 3 (`NotificationService`, `NotificationController`) | Đạt. `REF_CONTRACT_UNSIGNED_DELIVERY = "contract_unsigned_delivery"` (26/30 ký tự, tách biệt với `contract_expiring`), `UNSIGNED_DELIVERY_DAYS = 15`. Mốc `ngày giao ≤ hôm nay − 15 ngày` (đủ 15 ngày tạo, 14 ngày không tạo), chống trùng bằng `existsForUserAndRef`, chỉ gửi người phụ trách hiện tại (`c.getOwnerId()`, không gửi Admin), chạy trong `generateScheduledNotifications()` (được `NotificationScheduler` gọi mỗi 60 phút). Nội dung `MSG-065` khớp từng ký tự với SPEC §9 (`"Hợp đồng [tên hợp đồng] đã giao hàng [n] ngày nhưng chưa ký."`); tên dài tự cắt phần tên kèm `"…"` để giữ trọn câu trong 150 ký tự; tiêu đề trống dự phòng `displayCode`. `NotificationController.targetOf()` bổ sung nhánh `REF_CONTRACT_UNSIGNED_DELIVERY` dẫn tới `/contract?action=view&id=...`. |

### B & C. Bảo mật, an toàn đầu vào & quy chuẩn (§3–§4)

- **An toàn đầu vào & SQL Injection:** Tham số `delivery` chỉ nhận đúng 2 hằng `preparing` / `delivered` tại cả `ContractFilterHelper.deliveryFilterOf()` lẫn chốt cuối `ContractDAO.deliveryStageClause()` (chuỗi lạ, nhãn tiếng Việt hay payload SQLi `' OR 1=1 --` đều bị bỏ qua). `countDeliveredUnsigned` và `findDeliveredUnsigned` dùng `PreparedStatement`; `findDeliveredUnsigned(null)` trả danh sách rỗng không mở kết nối DB; `Contract.deliveredDaysOn` an toàn khi `deliveredDate` hoặc `today` là `null`; `NotificationService.isEligibleUnsignedDelivery` kiểm phòng thủ lại `null`, `contractId <= 0`, `ownerId <= 0`, `isDeleted`, `!isDraft`, `!isDeliveredUnsigned`, `deliveredDate == null` và `> cutoff`.
- **Bảo mật (XSS, CSRF, RBAC):**
  - **XSS:** 100% giá trị hiển thị mới trong 3 JSP (`contract.deliveryStage`, `deliveryFilter`, `deliveredUnsignedUrl`) đều bọc `fn:escapeXml(...)`, ngày dùng `<fmt:formatDate>`, số đếm là kiểu nguyên.
  - **CSRF:** Không có form POST/thao tác ghi dữ liệu mới (chỉ GET lọc danh sách và link điều hướng).
  - **RBAC:** Giữ nguyên kiểm quyền tập trung qua `PermissionService` / `AccessControl` ở `ContractListController`, `ContractExportController`, `DashboardController`, `NotificationController`.
- **Quy chuẩn:** Không có `javax.*`, không có placeholder/`// TODO`; 24/24 file nguồn/test/JSP thuộc task hợp lệ UTF-8 strict không BOM; không đổi chữ ký public API cũ (giữ nguyên constructor 12 tham số của `FilterState`, constructor 18 tham số của `DashboardSummary`, chữ ký `ContractDAO.findAll`/`countAll`/`countStatusSummary`); đúng phân tầng (`view` không chứa nghiệp vụ, `controller` gọi `service`/helper). Còn 1 hàm tăng lên 44 dòng (`ContractDAO.buildStatusSummarySql()`, xem 🟢 #1).

### D & F. Phạm vi, Không đụng & Tiêu chí "Xong khi"

- **Nhánh & Git:** Đang ở đúng nhánh `feat/task-10.5-buoc-giao-hang-nhac-ky`, không phải `main`; `git log main..HEAD` rỗng (chưa có commit bước Code); `task_tracker.md` vẫn giữ `[ ]` ở Task 10.5.
- **Phạm vi file:** Đủ 12/12 file "File sửa" và 7/7 file "File test" của Task 10.5; có 5 file kỹ thuật ngoài danh sách và 2 file bị xoá `docs/kichbandemoposcs.*` — xem 🟡 #1.
- **Không đụng:** Không đụng tiến độ (`progress_status`, `ALLOWED_TRANSITIONS`), trạng thái theo lịch (`STATUS_CASE_SQL`, `ContractValidator.computeStatus`), chu kỳ `NotificationScheduler` (60 phút), `db/`, `docs/SPEC.md`.
- **Tiêu chí "Xong khi":**
  1. Hợp đồng Nháp có Biên bản giao nhận hàng hoá hiện `"Đã giao – chưa ký"` ở danh sách, chi tiết và được đếm trên dashboard — Đạt (riêng thao tác tự chọn ô lọc bước giao hàng trên `listcontract.jsp` chưa hoạt động do lỗi 🔴 #1).
  2. Ký xong thì nhãn mất — Đạt (`DELIVERY_STAGE_SQL` và `DELIVERED_DATE_SQL` chỉ áp cho `progress_status = 'Nháp'`).
  3. Biên bản ngày 16 hôm trước sinh đúng một thông báo cho người phụ trách — Đạt (đã khoá cả SQL mốc `<= ?`, `existsForUserAndRef`, `MSG-065` 16 ngày và chạy 2 lượt chỉ tạo 1 lần).

### E. Kiểm thử

- **Kết quả `ant test`:** **2.431 pass / 0 fail / 0 error / 3 skipped** (+53 test mới so với Task 10.4: `ContractDAOTest` 155 [+17], `NotificationServiceTest` 40 [+15], `ContractListControllerTest` 20 [+6], `ListScopeTest` 11 [+4], `DashboardServiceTest` 20 [+4], `ContractExportControllerTest` 14 [+2], `DashboardControllerTest` 26 [+2], `NotificationControllerTest` 12 [+2], `ContractExportServiceTest` 17 [+1]).
- **Thay đổi trên test cũ (`rules.md` §3):** `git diff main -- test/` có **0 dòng bị xoá hay sửa** — 100% chỉ bổ sung test mới.
- **Chất lượng test mới:** Phủ đầy đủ kịch bản đúng/sai và biên (`14`/`15`/`16` ngày, đã ký, biên bản huỷ, chạy lặp 2 lần, payload SQLi, tên quá 150 ký tự, tiêu đề trống, đồng bộ `findAll`/`countAll`/`countStatusSummary`, điều hướng `NotificationController.targetOf`, lọc Excel). Không phụ thuộc đồng hồ cứng gây vỡ theo ngày (`ContractDAOTest` dùng `LocalDate.now().minusDays(16)` và mốc cố định với `deliveredDaysOn`; `NotificationServiceTest` dùng `FIXED_CLOCK`).

## Ghi chú ngoài phạm vi

- **Độ dài các lớp nguồn (> 250 dòng):** `ContractDAO.java` (2.665 dòng), `Contract.java` (426 dòng), `NotificationService.java` (285 dòng), `ContractFilterHelper.java` (263 dòng), `ContractListController.java` (254 dòng; phương thức `showList()` vốn 42 dòng trên `main` và không sửa ở Task 10.5). Các thay đổi Task 10.5 mở rộng trực tiếp các lớp sẵn có theo phạm vi task nên giữ cấu trúc này là hợp lý.
- **Tham số `month=all` và `view` trên `listcontract.jsp` (tồn tại sẵn từ `main`):** Các link "Xuất Excel" (dòng 296), phân trang (dòng 744–748) và `ContractFilterHelper.FilterState.queryWith()` vốn chưa mang theo `month` và `view`. Khi người dùng bấm từ ô `"Đã giao – chưa ký"` trên Dashboard (mở với `month=all&view=...`) rồi bấm Xuất Excel / sang trang 2 / chip trạng thái, hai tham số này sẽ rụng về mặc định (bản Nháp chưa chốt ngày hiệu lực/kết thúc vẫn khớp cửa sổ tháng hiện tại vì `NULL` được coi là khớp, chỉ ảnh hưởng bản Nháp đã nhập ngày hiệu lực ngoài tháng hiện tại hoặc khi Sales đang xem `view=all`).
- **Phụ thuộc schema `V44` trên CSDL chạy thật:** Task 10.5 không tạo migration mới, nhưng `ContractDAO.SELECT_BASE` truy vấn cột `contract_documents.doc_date` (được thêm ở `V44` của Task 10.3). Người dùng cần đảm bảo đã áp đủ `V40 → V45` lên `poscs_db` trước khi chạy nhánh Task 10.5.
- **Comment cũ còn liệt kê 2 loại thông báo tự sinh:** `NotificationScheduler.java` (dòng 15–17), `NotificationDAO.java` (dòng 18), `Notification.java` (dòng 14–15) và Javadoc `NotificationServiceTest.java` (dòng 32) vẫn ghi 2 loại cũ (`contract_expiring`, `ticket_sla`), có thể đồng bộ sau nếu cần.
- **Tình trạng working tree cho bước Commit:** Ngoài 2 file bị xoá `docs/kichbandemoposcs.*` đã nêu ở 🟡 #1, `task_tracker.md` đang có sẵn diff kế hoạch Giai đoạn 10 (Quyết định 16, Task 10.6–10.7) và Task 10.5 vẫn `[ ]`; ba file untracked `docs/HuongDan_DoiEmailGui.md`, `docs/PROMPTS.md`, `task_tracker_deploy.md` nằm ngoài phạm vi Task 10.5 — bước Commit không `git add` các file ngoài phạm vi.
- **Integration test (`test/poscs/integration/`):** 3 bộ test tích hợp mặc định `skipped` trong `ant test` (agent không tự chạy theo `rules.md` §0); cột `contract_documents.doc_date` đã có sẵn trong `db/schema.sql`, người dùng có thể tự chạy kiểm chứng trên CSDL `_it` sau khi fix.
