# QA Report - Task 10.2

**Kết luận:** APPROVED (ĐẠT — xác nhận lại sau bước Fix, 0 🔴 Critical / 0 🟡 Major còn tồn)
**Kết quả `ant compile-jsps test` (vòng QA lại):** `compile-jsps` báo `BUILD SUCCESSFUL` (trên máy này `compile.jsps=false` bỏ qua bước dịch trong Ant; đã dịch kiểm chứng lại 10/10 file `.jsp` đã sửa bằng `org.apache.jasper.JspC` của Tomcat 11.0 với `-source 21 -target 21 -failFast`: **0 errors**, sinh sạch toàn bộ 10 lớp `*_jsp.class`); **`ant test`: 2.266 pass / 0 fail / 0 error / 3 skipped** (92 test suites: 89 unit test suites pass 100%, 3 integration test suites trong `test/poscs/integration/` mặc định bỏ qua; `BUILD SUCCESSFUL`, tăng +14 test so với vòng QA đầu và +52 test so với Task 10.1).

**Phạm vi:** Task 10.2 — Mã hợp đồng để trống khi còn Nháp, bắt buộc khi Ký (`UC-10`, `UC-11`, `UC-33`, `UC-35`, `UC-44`, `UC-46`, cùng lưu ý `UC-54`; `BR-58`, `BR-35`, `MSG-061`). Đối chiếu với `docs/SPEC.md`, `rules.md`, và các dòng con của Task 10.2 trong `task_tracker.md`; thay đổi thực tế lấy từ `git status`, `git log main..HEAD` và `git diff main` trên nhánh `feat/task-10.2-ma-hop-dong-de-trong`.

**Xác nhận vòng QA lại:** Đạt yêu cầu UC & BR của Task 10.2. Đã rà soát toàn bộ diff sau bước Fix (3/3 mục 🟡 Major và 1 mục 🟢 Minor đã sửa triệt để kèm +14 unit test mới đi đúng nhánh mục tiêu; 1 mục 🟢 Minor còn lại về độ dài file `ContractActionController.java` đã có lý do hợp lý và được chấp nhận). Không phát hiện lỗi hồi quy; 36/36 file thuộc task hợp lệ UTF-8 không BOM, không có `javax.*`, 10/10 JSP dịch sạch qua Jasper Tomcat 11 và toàn bộ 2.266 unit tests pass 100%.

## Danh sách lỗi (lịch sử review)

Vòng QA đầu phát hiện: **0 🔴 Critical, 3 🟡 Major, 2 🟢 Minor**. Sau bước Fix: **4/5 đã sửa (`- [x]`), 1 🟢 Minor giữ nguyên kèm lý do được chấp nhận, 0 lỗi mới phát sinh**.

- [x] 🟡 `src/java/poscs/controller/ContractActionResultHelper.java` › `redirectProgressResult()` (dòng ~106–118) & `test/poscs/controller/ContractActionControllerTest.java`
  - Vi phạm: `BR-58`, `MSG-061`, `UC-44`, `rules.md` §4 (thiếu ánh xạ mã lỗi dịch vụ và thiếu test tương ứng)
  - Mô tả: `ContractService.signContract()` trả `ERR_MISSING_CODE` (kèm `MSG-061`) và `ERR_DUPLICATE_CODE` khi không đủ điều kiện ký, và Javadoc của `ContractActionController.signBlocker()` (dòng 233) khẳng định *"Service kiểm lại cả ba."* Tuy nhiên `ContractActionResultHelper.redirectProgressResult()` hiện chỉ ánh xạ `ERR_FORBIDDEN` và `ERR_MISSING_TERM`, bỏ sót `ERR_MISSING_CODE` và `ERR_DUPLICATE_CODE`. Nếu xảy ra tranh chấp đồng thời giữa `findById` ở controller và `findById` trong service (mã bị xoá trống hoặc bị hợp đồng khác chiếm ngay trước lúc ký), hoặc khi `signContract()` trả hai mã lỗi này, người dùng bị chuyển sang `?error=progress_failed` thay vì `?error=missing_code` (`MSG-061`) / `?error=duplicate_code`. `ContractActionControllerTest` cũng chưa có test nào kiểm đường trả lỗi này từ `contractService.signContract(...)`.
  - Gợi ý sửa: Bổ sung 2 nhánh trong `ContractActionResultHelper.redirectProgressResult()` (lưu ý: đây là lớp helper tách từ `ContractActionController` nhưng chưa có tên riêng trong dòng "File sửa" Task 10.2 — báo/hỏi người dùng trước khi sửa ngoài danh sách theo `rules.md` §3):
    ```java
    } else if (ContractService.ERR_MISSING_CODE.equals(res.getMessage())) {
        resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + id + "&error=missing_code");
    } else if (ContractService.ERR_DUPLICATE_CODE.equals(res.getMessage())) {
        resp.sendRedirect(req.getContextPath() + "/contract?action=view&id=" + id + "&error=duplicate_code");
    } else if (ContractService.ERR_MISSING_TERM.equals(res.getMessage())) {
    ```
    Và thêm 2 test trong `ContractActionControllerTest` xác minh khi `contractService.signContract(...)` trả `ERR_MISSING_CODE` / `ERR_DUPLICATE_CODE` thì redirect đúng `error=missing_code` / `error=duplicate_code`.
  - *Đã sửa (bước Fix, người dùng đồng ý sửa file ngoài dòng "File sửa" ngày 20/11/2026):* thêm 2 nhánh `ERR_MISSING_CODE` → `error=missing_code`, `ERR_DUPLICATE_CODE` → `error=duplicate_code` trong `redirectProgressResult()`. Test: `ContractActionControllerTest.br58_handleChangeProgress_serviceRejectsMissingCode_redirectsWithMissingCode`, `br58_handleChangeProgress_serviceRejectsDuplicateCode_redirectsWithDuplicateCode`.
  - *QA lại xác nhận:* Đạt. `ContractActionResultHelper.java` 141 dòng (≤ 250), `redirectProgressResult()` ánh xạ đủ cả 2 mã lỗi; 2 test mới dựng `draftContract(5)` có mã để đi qua `signBlocker` vào thẳng `signContract()` và kiểm chứng redirect chính xác.

- [x] 🟡 `src/java/poscs/service/ContractService.java` › `createContract()` (dòng ~312), `createAmendment()` (dòng ~356), `checkDraftUpdateRules()` (dòng ~432) & `src/java/poscs/controller/ContractController.java` › `handleCreate()` (dòng ~553), `handleUpdate()` (dòng ~619), `handleCreateAmendment()` (dòng ~735)
  - Vi phạm: `BR-58`, `SPEC.md` §5.4 (*"để trống mã thì không kiểm trùng"*), dòng "File sửa" Task 10.2 (*"`ContractService.java`, `ContractController.java`, `ContractActionController.java`: tạo/sửa không mã thì không kiểm trùng"*) & `rules.md` §4
  - Mô tả: Cả 6 vị trí tạo/sửa bản Nháp vẫn gọi `contractDAO.contractCodeExists(c.getContractCode(), ...)` khi mã là `null`. Hiện tại chỉ chạy đúng nhờ `ContractDAO.contractCodeExists()` tự trả `false` khi gặp `null`/rỗng (và trong `ContractControllerTest`, các test tạo/sửa không mã pass nhờ mock `contractDAO` mặc định trả `false`, không hề kiểm `never().contractCodeExists(...)`). Nếu dùng DAO giả/mock khác hoặc sau này tách logic, tầng Service và Controller không tự bảo vệ quy tắc "không mã thì không kiểm trùng".
  - Gợi ý sửa: Rào `hasContractCode()` tại cả 6 vị trí trong `ContractService` và `ContractController`:
    ```java
    if (contract.hasContractCode() && contractDAO.contractCodeExists(contract.getContractCode(), 0)) {
        ...
    }
    ```
    (Tương tự với `amendment.hasContractCode()` và `c.hasContractCode()`.) Đồng thời bổ sung `verify(contractDAO, never()).contractCodeExists(any(), anyInt())` vào 3 test tạo/sửa/phụ lục không mã trong `ContractControllerTest` (và kiểm tương ứng ở `ContractServiceTest`).
  - *Đã sửa:* rào `hasContractCode()` ở cả 6 vị trí (`ContractService.createContract`, `createAmendment`, `checkDraftUpdateRules`; `ContractController.handleCreate`, `handleUpdate`, `handleCreateAmendment`). Test: thêm `verify(contractDAO, never()).contractCodeExists(any(), anyInt())` vào `ContractControllerTest.br58_create_withoutCode_...`, `br58_update_draftClearingCode_isSaved`, `br58_createAmendment_withoutCode_inserts`; thêm 4 test `ContractServiceTest` trên `ContractDAO` mock: `br58_createContract_withoutCode_neverChecksDuplicate`, `br58_createAmendment_withoutCode_neverChecksDuplicate`, `br58_updateDraft_withoutCode_neverChecksDuplicate`, `br58_createContract_withCode_stillChecksDuplicate`.
  - *QA lại xác nhận:* Đạt. Đã kiểm tra đủ cả 6 điểm gọi và các assertion `never().contractCodeExists(...)` ở cả `ContractControllerTest` và `ContractServiceTest`.

- [x] 🟡 Thiếu unit test cho các nhánh/hành vi mới với mã `NULL` tại 5 lớp nguồn đã sửa: `src/java/poscs/dao/TechnicalSupportTicketDAO.java` › `mapRow()` (dòng ~936), `src/java/poscs/controller/ContractListController.java` › `toJsonArray()` (dòng ~222), `src/java/poscs/dao/ContractLinkDAO.java` › `linkContracts()` / `unlinkContracts()` (dòng ~173–254), `src/java/poscs/service/TicketExportService.java` › `buildExportRows()` / `buildPdfLines()` (dòng ~85, ~142), `src/java/poscs/model/ContractHandover.java` › `getDisplayCode()` (dòng ~75)
  - Vi phạm: `rules.md` §4 (*"Mọi logic mới hoặc nhánh mới (if/else, trường hợp biên) có test tương ứng trong `test/`"*)
  - Mô tả: Đặc biệt tại `TechnicalSupportTicketDAO.mapRow()` (dòng 936), điều kiện nhánh đã đổi từ `if (contractCode != null && t.getContractId() != null)` sang `if (t.getContractId() != null)` để phiếu gắn hợp đồng Nháp không mã không bị mất thông tin hợp đồng, nhưng `TechnicalSupportTicketDAOTest` chưa có test nào cho trường hợp `contract_id != null` và `contract_code == null`. Tương tự, JSON `/contract/byEnterprise` (`ContractListController.toJsonArray`), nhật ký nối/gỡ liên kết (`ContractLinkDAO`), xuất Excel/PDF phiếu (`TicketExportService`) và `ContractHandover.getDisplayCode()` đều chưa có test khóa nhãn `"Chưa có số"` khi `contract_code` là `NULL`/rỗng.
  - Gợi ý sửa: Bổ sung test case ngắn (1–2 test mỗi lớp) cho các nhánh `contract_code = null`/rỗng trên (hoặc báo/hỏi người dùng trước nếu muốn thêm vào các file test nằm ngoài dòng "File test" Task 10.2 theo `rules.md` §3).
  - *Đã sửa (người dùng đồng ý thêm test vào 4 file test ngoài dòng "File test" ngày 20/11/2026; chỉ thêm test, không sửa test cũ):* `TechnicalSupportTicketDAOTest.findById_contractWithoutCode_keepsContractAndShowsChuaCoSo`, `findById_noContract_leavesContractNull`; `ContractListControllerTest.byEnterpriseJson_contractWithoutCode_showsChuaCoSo`; `ContractLinkDAOTest.br58_linkContracts_withoutCodes_historySaysChuaCoSo`, `br58_unlinkContracts_withoutCodes_historySaysChuaCoSo`; `TicketExportServiceTest.br58_buildExportRows_contractWithoutCode_writesChuaCoSo`, `br58_exportTicketToPdf_contractWithoutCode_printsChuaCoSo`; `ContractHandover.getDisplayCode()` khoá ở `ContractValidatorTest.br58_contractHandover_displayCode_showsChuaCoSoWhenBlank` (file trong danh sách, cạnh các test `Contract.getDisplayCode()`).
  - *QA lại xác nhận:* Đạt. Cả 8 test bổ sung đều chặt chẽ (đọc nội dung PDF bằng `PDFTextStripper`, đối chiếu nguyên văn dòng `insertHistory`, xác nhận JSON không chứa `"null"` hay `"code":""`, kiểm đủ hai nhánh `contract_id != null` và `contract_id == null` ở `TechnicalSupportTicketDAO`), không sửa test cũ.

- [x] 🟢 `web/jsp/sale/addnewcontract.jsp` (dòng ~158)
  - Vi phạm: `BR-58` (phòng thủ NULL cho thuộc tính `placeholder` khi lập phụ lục)
  - Mô tả: Dòng 158 dùng `placeholder="${empty parentContract ? 'VD: 01/2026/HĐKT-POSTEF' : fn:escapeXml(parentContract.contractCode).concat('/PL01')}"`. Thông thường hợp đồng gốc đã ký nên luôn có mã, nhưng nếu gặp bản ghi hợp đồng gốc chưa có mã thì `fn:escapeXml(null)` trả `""` khiến placeholder ra `"/PL01"` (trong khi dòng 92 ngay bên trên đã dùng `parentContract.displayCode`).
  - Gợi ý sửa:
    ```jsp
    placeholder="${empty parentContract or not parentContract.hasContractCode() ? 'VD: 01/2026/HĐKT-POSTEF' : fn:escapeXml(parentContract.contractCode).concat('/PL01')}"
    ```
  - *Đã sửa:* đúng như gợi ý. JSP dịch sạch qua `org.apache.jasper.JspC` của Tomcat 11.0 (0 errors). Không có unit test cho JSP.
  - *QA lại xác nhận:* Đạt. Đã tự dịch kiểm chứng lại toàn bộ 10/10 JSP bằng Jasper Tomcat 11 (0 errors).

- [ ] 🟢 `src/java/poscs/controller/ContractActionController.java` (toàn file 289 dòng)
  - Vi phạm: `rules.md` §4 (*"file > 250 dòng: xem xét tách module phụ trợ"*)
  - Mô tả: Bổ sung `signBlocker(Contract)` đẩy `ContractActionController.java` từ 266 lên 289 dòng (> 250 dòng) và lặp lại 3 điều kiện kiểm tra vốn đã nằm trong `ContractService.checkSignable()`. (Các phương thức thêm/sửa đều ≤ 40 dòng: `handleChangeProgress` 19 dòng, `signBlocker` 12 dòng.)
  - Gợi ý sửa: Cân nhắc chuyển `signBlocker` sang `ContractControllerHelper` hoặc sau khi sửa lỗi #1 có thể để `ContractService.signContract()` kiểm tập trung (lưu ý tương thích các test mock `never().signContract` trong `ContractActionControllerTest`).
  - *Không sửa (bước Fix), lý do:* file đã 270 dòng trên `main` trước Task 10.2, nên vượt 250 không do task này gây ra. Chuyển `signBlocker` sang `ContractControllerHelper` (đã 259 dòng, ngoài dòng "File sửa") chỉ dời phần vượt sang file khác. Bỏ hẳn bước kiểm trước ở controller để service kiểm tập trung thì phải bỏ luôn kiểm thời hạn có sẵn từ trước (test cũ `handleChangeProgress_signMissingDates_redirectsWithMissingTermError` không giả lập `signContract`), hoặc giữ kiểm thời hạn mà bỏ kiểm mã thì báo sai thứ tự SPEC §5.4 (thiếu cả mã lẫn thời hạn phải báo mã trước). Sau khi sửa #1, `ContractActionResultHelper` đã đỡ đúng thông báo khi service từ chối, nên bước kiểm trước ở controller chỉ để báo sớm và không gọi service.
  - *QA lại xác nhận:* Chấp nhận lý do. Các phương thức sửa/thêm đều ≤ 40 dòng và không làm giảm chất lượng nghiệp vụ/bảo mật.

## Chi tiết kiểm tra

### A. Đối chiếu nghiệp vụ (UC, BR, MSG)

| Hạng mục | Kết quả |
|---|---|
| `db/migrations/V43__contract_code_nullable__ndat2003.sql` & `db/schema.sql` | Đạt. Đúng số kế tiếp `V43` sau `V42`; có `INSERT INTO schema_migrations`; `ALTER TABLE contracts MODIFY COLUMN contract_code varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL` chạy **trước** `UPDATE contracts SET contract_code = NULL WHERE TRIM(contract_code) = ''`; giữ nguyên `UNIQUE KEY contract_code` (MySQL InnoDB cho nhiều dòng `NULL`). `db/schema.sql` dòng 4418 đồng bộ chính xác. |
| `BR-58` — Tạo/sửa bản Nháp không mã (`UC-33`, `UC-35`, `UC-46`) | Đạt hoàn toàn. `ContractValidator` thêm overload `validateRequiredFields(c, boolean)` (Nháp truyền `false`, vẫn kiểm `maxLength(50)`); `ContractDAO.insert`/`update` dùng `ContractDocumentDAO.trimToNull` ghi `NULL` thay vì chuỗi rỗng; `ContractService.normalizeCodeAndTitle` chuyển blank → `null`; cả `ContractService` và `ContractController` rào `hasContractCode()` trước khi gọi `contractCodeExists(...)` (không kiểm trùng khi để trống mã); hai bản Nháp cùng không mã không bị coi là trùng; tìm kiếm theo tiêu đề (`c.contract_code LIKE ? OR c.title LIKE ? OR e.enterprise_name LIKE ?`) vẫn tìm được khi `contract_code IS NULL`. |
| `BR-58` & `MSG-061` — Ký hợp đồng (`UC-44`) | Đạt hoàn toàn ở mọi tầng (`ContractValidator.validateSigning`, `ContractService.signContract`, `ContractDAO.changeProgressStatus` với `FOR UPDATE`, `ContractController.handleChangeProgress`, `ContractActionController.signBlocker`, `ContractActionResultHelper.redirectProgressResult`, `viewcontractdetail.jsp`, `updatecontract.jsp`). So từng ký tự `MSG-061`: `"Hợp đồng chưa có mã. Vui lòng nhập mã hợp đồng trước khi ký."` khớp 100% giữa SPEC §9, `ContractValidator.MSG_061`, `updatecontract.jsp` (dòng 939) và `viewcontractdetail.jsp` (dòng 304). Câu báo trùng mã khi ký ở `viewcontractdetail.jsp` (dòng 311) khớp 100% SPEC §9. |
| `BR-58` & `UC-54` — Chữa sai sót HĐ đã ký (lưu ý từ QA Task 10.1) | Đạt hoàn toàn. Chặn xoá trắng mã ở cả 4 lớp: `updatecontract.jsp` (`not canEditTerms` → hiện `*`, `data-required="1"`, JS chặn khi bật chế độ chữa sai sót), `ContractController.handleCorrect` (dòng 816), `ContractService.correctSignedContract` → `validateRequiredFields(contract)` (1 tham số, `codeRequired = true`), và `ContractDAO.correct` (dòng 1391, trong giao dịch `FOR UPDATE`). Đã có unit test ở cả 4 lớp test. |
| `BR-58` — Hiển thị `"Chưa có số"` ở mọi màn hình / xuất file / nhật ký | Đạt hoàn toàn. Đã quét toàn bộ repo bằng `git grep` theo `getContractCode\|contract_code\|contractCode\|parentContractCode\|buyCode\|sellCode`: mọi vị trí hiển thị (danh sách `listcontract.jsp` `PL của Chưa có số`, chi tiết `viewcontractdetail.jsp`, sửa `updatecontract.jsp`, tạo phụ lục `addnewcontract.jsp` gồm cả `placeholder`, hàng chờ bàn giao `handoverqueue.jsp`, tổng quan `dashboard.jsp`, chi tiết KH `viewcustomerdetail.jsp`, chi tiết SP `viewdetailProduct.jsp`, danh sách/chi tiết phiếu `listTicket.jsp` / `viewdetailTicket.jsp`, JSON picker `/contract/byEnterprise`, Excel HĐ `ContractExportService`, Excel & PDF phiếu `TicketExportService`, nhật ký `ContractDAO` / `ContractLinkDAO`) đều dùng `displayCode` / `parentDisplayCode` / `displayCodeOf`, không in `"null"`. Ô `<input id="contractCode">` ở `updatecontract.jsp` dòng 298 giữ nguyên `contract.contractCode` để không tự điền chữ `"Chưa có số"` vào ô nhập. `ContractDAO.addChange` in `(trống)` khi đổi mã từ/về `NULL`. |
| Form tạo/sửa bỏ dấu `*` khi Nháp & ghi chú đúng câu | Đạt. `addnewcontract.jsp` (dòng 155–159, bỏ kiểm JS dòng 415) và `updatecontract.jsp` (dòng 294–302, JS dòng 1445–1452) bỏ `*` khi còn Nháp và hiện đúng `"Để trống nếu chưa có số; bắt buộc trước khi ký."`. |

### B & C. Bảo mật, an toàn đầu vào & quy chuẩn (§3–§4)

- **An toàn đầu vào:** Xử lý đủ `null`, `""`, `"   "`, vượt 50 ký tự (ở `ContractValidator`), mã trùng kèm khoảng trắng đầu/cuối.
- **Bảo mật:** Giữ nguyên `csrfToken` trên mọi form ghi (`addnewcontract.jsp`, `updatecontract.jsp`); 100% vị trí hiển thị `displayCode` / `parentDisplayCode` trong 10 file JSP đều bọc `fn:escapeXml(...)`; JSON `/contract/byEnterprise` đi qua `escapeJson(...)`; `param.error` chỉ so sánh hằng trong `<c:if>`, không echo trực tiếp; không thay đổi hay nới lỏng phân quyền (`PermissionService` / `AccessControl`).
- **Quy chuẩn:** Không có `javax.*`, không có placeholder/`// TODO`; 36/36 file thuộc task đều hợp lệ UTF-8 strict không BOM; không đổi chữ ký public API cũ (chỉ thêm method/overload mới); mọi thao tác ghi nhiều bảng vẫn nằm trong giao dịch nguyên tử; các hàm thêm/sửa mới đều ≤ 40 dòng.

### D. Phạm vi, Không đụng & Tiêu chí "Xong khi"

- **Nhánh & Git:** Đang ở đúng nhánh `feat/task-10.2-ma-hop-dong-de-trong`; `git log main..HEAD` rỗng (chưa có commit của bước Code/Fix).
- **Phạm vi file (36 file thuộc Task 10.2):** 1 migration mới (`V43`), `db/schema.sql`, 13 file Java nguồn (gồm `ContractActionResultHelper.java` đã được người dùng đồng ý sửa ở bước Fix), 10 file JSP, 11 file unit test (gồm 4 file test bổ sung đã được người dùng đồng ý ở bước Fix: `ContractListControllerTest`, `ContractLinkDAOTest`, `TechnicalSupportTicketDAOTest`, `TicketExportServiceTest`). Ba file trong danh sách "File sửa" (`ContractHandoverDAO.java`, `ProductDAO.java`, `NotificationService.java`) không có diff vì đã rà thấy an toàn sẵn với `NULL`.
- **Không đụng:** Không chạm giấy tờ (`ContractDocument*`, Task 10.3) hay kỳ thanh toán (`ContractPayment*`, Task 10.4). Không tự chạy migration lên CSDL nào.
- **Tiêu chí "Xong khi":** Đạt toàn bộ — tạo được hợp đồng không mã; danh sách (`PL của Chưa có số`, theo BR-16 bảng danh sách không có cột mã riêng), chi tiết, Excel, hàng chờ bàn giao, dashboard đều hiện `"Chưa có số"`; bấm Ký khi chưa có mã hiện đúng `MSG-061`.

### E. Kiểm thử

- **Tổng kết `ant test`:** **2.266 pass / 0 fail / 0 error / 3 skipped** (tăng +52 test so với 2.214 ở Task 10.1; 11 bộ test có thay đổi đều pass 100%: `ContractValidatorTest` 68, `ContractServiceTest` 92, `ContractControllerTest` 150, `ContractActionControllerTest` 103, `ContractListControllerTest` 14, `ContractDAOTest` 138, `ContractLinkDAOTest` 21, `TechnicalSupportTicketDAOTest` 36, `ContractExportServiceTest` 16, `TicketExportServiceTest` 18, `NotificationServiceTest` 25).
- **Kiểm tra thay đổi trên test cũ (rules §3):** Không có test nào bị xoá; 4 file test thêm ở bước Fix chỉ chứa dòng thêm; các điều chỉnh trên test sẵn có từ bước Code đều là cập nhật fixture hoặc thay đổi kỳ vọng trực tiếp theo luật mới `BR-58` (`ContractServiceTest` dòng 211–221, `ContractExportServiceTest` dòng 179), không che lỗi.
- **Chất lượng test mới:** Đầy đủ kịch bản đúng và sai, đi đúng nhánh mục tiêu, assertion chặt (`MSG_061`, `ERR_MISSING_CODE`, `ERR_DUPLICATE_CODE`, `never().contractCodeExists(...)`, kiểm nội dung PDF thật qua `PDFTextStripper`, kiểm `null` vs `""`, kiểm rollback giao dịch) và dùng ngày tương đối (`LocalDate.now()`).

## Ghi chú ngoài phạm vi

- **`ContractValidator.isValid(Contract)` (dòng 189):** Hàm tương thích ngược cũ này không được gọi ở bất kỳ lớp nguồn nào trong `src/java/` và vẫn giữ `isBlank(c.getContractCode())` cùng test cũ `testIsValid_backwardCompatibility` (dòng 831) — không thuộc luồng chạy thực tế nên giữ nguyên là phù hợp.
- **Migration CSDL cho người dùng:** Trước khi chạy ứng dụng trên `poscs_db` với nhánh Task 10.2, người dùng tự áp `db/migrations/V43__contract_code_nullable__ndat2003.sql` lên `poscs_db` (agent không chạy SQL lên CSDL theo `rules.md` §3).
- **Tình trạng working tree cho bước Commit:** `task_tracker.md` đang có sẵn diff kế hoạch Giai đoạn 10 (Task 10.6, 10.7, Quyết định 16) và Task 10.2 vẫn `[ ]`; hai file untracked `docs/PROMPTS.md` và `task_tracker_deploy.md` nằm ngoài phạm vi Task 10.2 — bước Commit không `git add` hai file untracked này. Danh sách cần commit của Task 10.2 gồm 36 file task ở mục D, `docs/qa/Task-10.2.md`, và phần cập nhật `[x]` cho Task 10.2 trong `task_tracker.md`.
- **Integration test (`test/poscs/integration/`):** 3 bộ test tích hợp mặc định `skipped` trong `ant test`; sau khi áp migration `V43` trên DB `_it`, người dùng có thể chạy kiểm chứng ràng buộc `UNIQUE` cho phép nhiều dòng `contract_code = NULL`.
