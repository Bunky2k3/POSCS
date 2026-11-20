# QA Report - Task 10.3

**Kết luận:** CẦN SỬA
**Kết quả `ant compile-jsps test`:** `compile-jsps` báo `BUILD SUCCESSFUL` (trên máy này `compile.jsps=false` bỏ qua bước dịch trong Ant; đã dịch kiểm chứng riêng 2/2 file `.jsp` đã sửa là `web/jsp/sale/updatecontract.jsp` và `web/jsp/sale/viewcontractdetail.jsp` bằng `org.apache.jasper.JspC` của Tomcat 11.0 với `-source 21 -target 21 -failFast`: **0 errors**, sinh sạch `updatecontract_jsp.class` và `viewcontractdetail_jsp.class`); **`ant test`: 2.296 pass / 0 fail / 0 error / 3 skipped** (92 test suites: 89 unit test suites pass 100%, 3 integration test suites trong `test/poscs/integration/` mặc định bỏ qua; `BUILD SUCCESSFUL`, tăng +30 test so với Task 10.2).

**Phạm vi:** Task 10.3 — Giấy tờ dạng ô tích, làm trên mục Tài liệu sẵn có (`UC-52`, `UC-11`; `BR-59`, `MSG-062`, `MSG-063`, `NFR-08`, `NFR-19`). Đối chiếu với `docs/SPEC.md`, `rules.md` và các dòng con của Task 10.3 trong `task_tracker.md`; thay đổi thực tế lấy từ `git status`, `git log main..HEAD` và `git diff main` trên nhánh `feat/task-10.3-giay-to-o-tich`.

## Danh sách lỗi

Phát hiện **0 🔴 Critical, 3 🟡 Major, 2 🟢 Minor**. Nghiệp vụ chính (`BR-59`, `MSG-062`, `MSG-063`, migration `V44`, giao diện ô tích ở hai JSP và bảo mật) đạt tốt; các điểm 🟡 Major nằm ở chất lượng/độ phủ nhánh của unit test và xác nhận phạm vi 3 file sửa ngoài danh sách theo `rules.md` §3.

- [x] 🟡 `test/poscs/dao/ContractDocumentDAOTest.java` › `addDocument_validationFailures_returnsFailedCode()` (dòng ~121–167)
  - Vi phạm: `rules.md` §4 (*"Mọi logic mới hoặc nhánh mới (if/else, trường hợp biên) có test tương ứng trong `test/`"*) và checklist E (`docs/PROMPTS.md`: test pass nhờ đi nhầm nhánh).
  - Mô tả: Trong `addDocument_validationFailures_returnsFailedCode()`, đối tượng `doc` không được gán `docDate` (`doc.getDocDate() == null`). Do `ContractDocumentDAO.addDocument()` (dòng 88) kiểm `doc.getDocDate() == null` đầu tiên và trả `DOC_FAILED` ngay lập tức, mọi assertion từ dòng 131–166 đều thoát sớm ở nhánh thiếu ngày thay vì đi vào nhánh đích:
    - Các dòng 149–156 (`// invalid fileUrl (null, empty, whitespace)`) kiểm luật **cũ** (ngược với `BR-59` vốn đã cho phép link trống) nhưng vẫn xanh do đi nhầm nhánh `docDate == null`.
    - Nhánh mới `(fileUrl != null && fileUrl.length() > FILE_URL_MAX_LENGTH)` cùng các nhánh kiểm `docType` rỗng / vượt 50 ký tự (dòng 93–94 `ContractDocumentDAO.java`) hiện chưa có test nào truyền `docDate != null` để thật sự đi qua.
  - Gợi ý sửa: Bổ sung test mới ở mục 5 của `ContractDocumentDAOTest` dùng `paperDoc(...)` (có `PAPER_DATE`) kiểm trực tiếp `docType` null/blank/`"A".repeat(51)` và `fileUrl` 501 ký tự trả `DOC_FAILED` mà không mở kết nối DB:
    ```java
    @Test
    public void addDocument_withDocDate_rejectsInvalidDocTypeAndOverlongUrl() {
        try (MockedStatic<DBContext> db = mockStatic(DBContext.class)) {
            assertEquals(ContractDocumentDAO.DOC_FAILED,
                    dao.addDocument(paperDoc(null, null, null), ACTOR_ID));
            assertEquals(ContractDocumentDAO.DOC_FAILED,
                    dao.addDocument(paperDoc("   ", null, null), ACTOR_ID));
            assertEquals(ContractDocumentDAO.DOC_FAILED,
                    dao.addDocument(paperDoc("A".repeat(51), null, null), ACTOR_ID));
            assertEquals(ContractDocumentDAO.DOC_FAILED,
                    dao.addDocument(paperDoc("CO", null, "https://example.com/" + "a".repeat(481)), ACTOR_ID));
            db.verifyNoInteractions();
        }
    }
    ```
    Với 3 dòng assertion cũ `fileUrl` null/rỗng/blank (dòng 151–156) trong `addDocument_validationFailures_returnsFailedCode()`, báo/hỏi người dùng theo `rules.md` §3 trước khi gỡ hoặc tách sang kiểm với `docDate` hợp lệ.
  - **Đã sửa (bước Fix, 20/11/2026):** thêm `addDocument_withDocDate_rejectsInvalidDocTypeAndOverlongUrl()` (có `docDate`: `docType` null / rỗng / toàn khoảng trắng / 51 ký tự và link 501 ký tự → `DOC_FAILED`, không mở kết nối) và `addDocument_linkExactly500_isWritten()` (biên 500 ký tự vẫn ghi). Link trống khi có ngày đã có `addDocument_withoutLink_insertsNullUrlWithDateAndNumber()`.
  - Ba dòng assertion `fileUrl` null / rỗng / blank trong test cũ **giữ nguyên** (rules §3: không sửa assertion, không xoá test cũ; test vẫn xanh nên không thuộc diện "test cũ đỏ khi SPEC đổi"). Đã thêm comment ở đầu test ghi rõ chúng dừng ở nhánh thiếu ngày và không còn là luật theo BR-59.

- [x] 🟡 `test/poscs/service/ContractDocumentServiceTest.java` › `sampleDocument()` (dòng ~82–91)
  - Vi phạm: `rules.md` §4 (unit test phụ thuộc vào đồng hồ hệ thống).
  - Mô tả: Trong `setUp()` (dòng 48), `service` được khởi tạo bằng constructor 3 tham số (dùng `Clock.systemDefaultZone()`), nhưng `sampleDocument()` (dòng 86) gán cứng `d.setDocDate(Date.valueOf("2026-11-01"))`. Trên bất kỳ máy hoặc môi trường CI nào có ngày hệ thống trước `01/11/2026`, toàn bộ ~15 test gọi `service.addDocument(...)` qua `sampleDocument(...)` sẽ fail hàng loạt với `ERR_DATE_FUTURE` (trong khi `addDocument_nullClock_fallsBackToSystemClock()` ở dòng 721 đã dùng ngày tương đối `LocalDate.now().minusDays(1)`).
  - Gợi ý sửa: Dùng ngày tương đối trong `sampleDocument()` (dòng 86):
    ```java
    d.setDocDate(Date.valueOf(LocalDate.now().minusDays(1)));
    ```
    (Hoặc truyền `TODAY_2026_11_20` vào `service` trong `setUp()` nếu muốn giữ mốc ngày cố định `2026-11-01`.)
  - **Đã sửa:** `sampleDocument()` dùng `Date.valueOf(LocalDate.now().minusDays(1))`, cùng đồng hồ hệ thống với `service` ở `setUp()`; các test dùng mốc cố định vẫn đi qua `clockedService()` (đồng hồ 20/11/2026).

- [x] 🟡 Phạm vi file sửa ngoài danh sách của Task 10.3: `src/java/poscs/controller/ContractControllerHelper.java` (dòng 244–248), `test/poscs/controller/ContractDetailControllerTest.java` (+46 dòng), `test/poscs/integration/ContractStatusIntegrationTest.java` (dòng 480, 507)
  - Vi phạm: `rules.md` §3 (*"Trước khi xóa hoặc sửa bất cứ thứ gì ngoài phạm vi file của task, phải báo trước: xóa/sửa gì, vì sao, ảnh hưởng ra sao, rồi chờ người dùng đồng ý"*) và checklist D (`docs/PROMPTS.md`).
  - Mô tả: Cả 3 file đều không nằm trong dòng "File sửa" / "File test" của Task 10.3 trong `task_tracker.md`, dù cả 3 thay đổi đều hợp lý về kỹ thuật theo `BR-59`:
    1. `ContractControllerHelper.java` + 2 test mới ở `ContractDetailControllerTest.java`: chặn lỗi `TextRules.isSafeHttpUrl(null)` trả `true` khiến tờ `"Hợp đồng đã ký"` không có link chặn mất nút `"Mở bản PDF"` của tờ có link phía sau.
    2. `ContractStatusIntegrationTest.java`: thêm `doc.setDocDate(LocalDate.now())` cho 2 test tài liệu để khớp cột `doc_date NOT NULL` khi chạy integration test trên DB `_it`.
    - Do QA chạy ở phiên chat mới không quan sát được lịch sử trao đổi ở bước Code, cần xác nhận người dùng đã đồng ý với 3 file ngoài danh sách này (cũng như việc đổi 2 assertion `urlNull`/`urlEmptyOrBlank` ở `ContractDocumentServiceTest` và 2 assertion chuỗi nhật ký ở `ContractDocumentDAOTest` theo luật mới `BR-59`) trước khi bước Commit đưa vào commit.
  - Gợi ý sửa: Không cần hoàn tác 3 thay đổi kỹ thuật hợp lý này. Nếu người dùng **đã đồng ý** ở bước Code thì ghi rõ xác nhận ngay dưới mục này; nếu **chưa hỏi**, liệt kê rõ file/lý do/ảnh hưởng để người dùng xác nhận ở bước Fix trước khi commit.
  - **Xác nhận:** người dùng đã đồng ý ở bước Code (20/11/2026, hỏi trước khi sửa): (1) sửa nút "Mở bản PDF" ở `ContractControllerHelper` kèm test ở `ContractDetailControllerTest`; (2) thêm ngày trên giấy cho 2 test tích hợp; (3) sửa 14 test cũ đỏ vì BR-59: 10 test chỉ thêm dữ liệu dựng (ngày trên giấy, đổi loại cũ "Biên bản nghiệm thu"), 4 dòng assertion trái BR-59 (2 câu nhật ký thêm "(ngày …)", 2 test link trống giờ hợp lệ). Không xoá, không đổi tên test nào.

- [x] 🟢 `src/java/poscs/dao/ContractDocumentDAO.java` › `voidDocument()` & `lockDocumentForVoid()` (dòng ~149–152, 238–248)
  - Vi phạm: Nhất quán nhật ký kiểm toán theo `BR-59` (*"Một loại có thể có nhiều tờ... Thêm và huỷ đều ghi nhật ký hợp đồng"*).
  - Mô tả: `addDocument()` đã nối `paperRef(docNo, doc.getDocDate())` (`(số ..., ngày ...)`) vào dòng nhật ký và modal bỏ tích ở `updatecontract.jsp` (dòng 1237) cũng hiện đủ số giấy và ngày trên giấy. Tuy nhiên `voidDocument()` vẫn chỉ SELECT `contract_id, doc_type, title` và ghi `"Huỷ tài liệu: " + target.docType` (không có số giấy, ngày trên giấy), nên khi một hợp đồng có nhiều tờ cùng loại không đặt `title` (vd. 2 `"Biên bản giao nhận hàng hoá"`), đọc nhật ký không phân biệt được tờ nào đã bị bỏ tích. (Xếp 🟢 Minor vì dòng "File sửa" Task 10.3 chỉ nêu `addDocument`, `insertDocumentRow`, `findDocumentsOf`, `mapResultSetToActiveDocument`, và sửa sẽ ảnh hưởng chuỗi SQL/assertion của các test `voidDocument_*` sẵn có trong `ContractDocumentDAOTest`.)
  - Gợi ý sửa: Cân nhắc bổ sung `doc_date, doc_no` trong `lockDocumentForVoid()` và nối `paperRef(target.docNo, target.docDate)` trong `voidDocument()` (nếu sửa thì xin ý kiến người dùng trước khi cập nhật mock/assertion các test `voidDocument_*` cũ, hoặc giữ nguyên và ghi lý do).
  - **Đã sửa (người dùng đồng ý ở bước Fix, 20/11/2026):** `lockDocumentForVoid()` lấy thêm `doc_no, doc_date`; `voidDocument()` nối `paperRef(...)` nên nhật ký bỏ tích có "(số …, ngày dd/MM/yyyy)" như lúc thêm. 6 test `voidDocument_*` cũ chỉ đổi chuỗi stub SQL (`SELECT contract_id, doc_type, title, doc_no, doc_date FROM contract_documents`), giữ nguyên assertion (dòng giả lập không có số/ngày nên câu nhật ký không đổi). Test mới: `voidDocument_historyIncludesNumberAndDateOfSheet()`, `voidDocument_blankNumber_historyHasDateOnly()`.

- [ ] 🟢 `test/poscs/service/ContractDocumentServiceTest.java` › `addDocument_urlNull_invalid()` & `addDocument_urlEmptyOrBlank_invalid()` (dòng ~241–263)
  - Vi phạm: Đặt tên test gây hiểu nhầm (`rules.md` §7 — style/đặt tên).
  - Mô tả: Sau khi đảo kỳ vọng theo `BR-59` (link không bắt buộc), hai phương thức test vẫn giữ tên hậu tố `_invalid` dù assertion bên trong đã đổi thành `assertTrue(...isSuccess())`.
  - Gợi ý sửa: Giữ nguyên nếu bám chặt nguyên tắc không đổi tên test cũ (`rules.md` §3) và ghi rõ lý do, hoặc đổi tên sang `..._allowedWithoutLink` sau khi người dùng đồng ý.
  - **Không sửa:** người dùng chọn giữ tên (bước Fix, 20/11/2026) theo quy tắc không đổi tên test cũ (rules §3). Trong hai test đã có comment ghi luật mới của BR-59 (link không bắt buộc).

## Chi tiết kiểm tra

### A. Đối chiếu nghiệp vụ (UC, BR, MSG)

| Hạng mục | Kết quả |
|---|---|
| `db/migrations/V44__contract_documents_date_and_number__ndat2003.sql` & `db/schema.sql` | Đạt. Đúng số kế tiếp `V44` sau `V43`; có `INSERT INTO schema_migrations` khớp tên file; thêm `doc_date date DEFAULT NULL` (sau `title`), `doc_no varchar(50) NULL`, sửa `file_url varchar(500) NULL`; điền `doc_date = DATE(uploaded_at)` rồi fallback `CURRENT_DATE` phòng `uploaded_at IS NULL`, sau đó đặt `NOT NULL`; đổi đúng các loại cũ (`Biên bản nghiệm thu` → `Biên bản nghiệm thu bàn giao`; `Hoá đơn` → `Hoá đơn GTGT`; `Biên bản bàn giao` và `Báo giá / Đơn đặt hàng` chép vào `title` khi trống **trước** khi đổi `doc_type = N'Khác'`); link demo của `14/2026/HĐKT-POSTEF` (HD-0015, loại `Hợp đồng đã ký`) giữ nguyên ở cả `V44` lẫn `db/schema.sql` (dòng 5067–5073). Cấu trúc `CREATE TABLE contract_documents` trong `db/schema.sql` (dòng 4664–4675) khớp 100% với `V44`. |
| `ContractDocument.java` (`TYPES`, `docDate`, `docNo`, `getCategory`) | Đạt. `TYPES` (dòng 32–47) khớp từng ký tự và đúng thứ tự 15 loại của `BR-59` (`Phiếu GQNCKH` → `Biên bản thanh lý`, `"Khác"` đứng cuối); bỏ comment `"danh mục còn phải chốt"` và trỏ về Quyết định 15 (`SPEC §11`); bổ sung `docDate`, `docNo`, `TYPE_OTHER`, `getCategory()` (gom loại lạ/null vào `"Khác"`). Giữ nguyên toàn bộ public getter/setter cũ. |
| `BR-59` — Kiểm tra đầu vào ở `ContractDocumentService.java` | Đạt. Link không bắt buộc (`null`, `""`, `"   "` đều hợp lệ); nếu có nhập thì kiểm `TextRules.isSafeHttpUrl` (chặn `javascript:`, `ftp:`, `<`, `>`, `"`) và tối đa 500 ký tự; `docType` phải nằm trong `TYPES` (từ chối 4 loại cũ đã gỡ); `docNo` tối đa 50 ký tự, `title`/`note` tối đa 255 ký tự; thiếu `docDate` trả `ERR_DATE_REQUIRED` (`MSG-062`), ngày sau hôm nay trả `ERR_DATE_FUTURE` (`MSG-063`), hôm nay hợp lệ; loại `"Khác"` bắt nhập `title` (`ERR_TITLE_REQUIRED`); cho phép nhiều tờ cùng loại; cho phép thêm/huỷ ở mọi tiến độ kể cả sau thanh lý (không chặn `isFrozen`). |
| `MSG-062` & `MSG-063` (so từng ký tự với SPEC §9) | Đạt. `MSG-062`: `"Vui lòng nhập ngày trên giấy tờ."` khớp 100% ở `updatecontract.jsp` dòng 260 (băng lỗi server) và dòng 1758 (JS modal). `MSG-063`: `"Ngày trên giấy tờ không được sau hôm nay."` khớp 100% ở `updatecontract.jsp` dòng 261 và dòng 1759. `ContractDocumentController.errorParamOf()` (dòng 213–222) ánh xạ đầy đủ `ERR_DATE_REQUIRED`, `ERR_DATE_FUTURE`, `ERR_TITLE_REQUIRED`, `ERR_REASON_REQUIRED`, `ERR_INVALID` và fallback `document_failed`. |
| `ContractDocumentDAO.java` | Đạt (1 🟢 Minor ở `voidDocument`). `addDocument` yêu cầu `docDate != null`, cho phép `fileUrl == null` (ghi SQL `NULL` khi blank), cắt gọn an toàn `docNo` ≤ 50, ghi đủ 8 cột qua `PreparedStatement` và ghi nhật ký `EVENT_DOCUMENT` kèm `paperRef(docNo, docDate)` (`(số ..., ngày dd/MM/yyyy)`) trong một giao dịch khoá `FOR UPDATE` (`NFR-19`); `findDocumentsOf` SELECT và map đủ `doc_date`, `doc_no`, `file_url` NULL. |
| `updatecontract.jsp` (UC-52) & `viewcontractdetail.jsp` (UC-11) | Đạt. Đổi tên tab thành `"Giấy tờ"` (có bộ đếm số tờ) và cập nhật các câu nhắc ở đầu trang `updatecontract.jsp` (dòng 236, 314, 545). Cả hai trang hiển thị đủ 15 loại `documentTypes` dạng ô tích; loại đã có tờ hiện dấu tích, nhãn số tờ khi > 1 và danh sách từng tờ (ngày `dd/MM/yyyy` theo `NFR-08`, số giấy, tên, ghi chú, người/ngày thêm, nút `"Mở"` chỉ hiện khi có `fileUrl` với `target="_blank" rel="noopener noreferrer"`). `updatecontract.jsp` bấm ô trống hoặc `"+ Thêm tờ"` mở modal `#paperModal` (nằm ngoài các form khác, kiểm ngày/tên/link tại chỗ), bỏ tích mở modal `ask()` bắt buộc lý do; `viewcontractdetail.jsp` chỉ đọc hoàn toàn. Băng lỗi `param.error` nằm ngoài `<c:if test="${not contract.frozen}">` nên hợp đồng đã thanh lý vẫn hiện lỗi đầy đủ. |

### B & C. Bảo mật, an toàn đầu vào & quy chuẩn (§3–§4)

- **An toàn đầu vào:** Xử lý đủ `null`, `""`, `"   "`, sai định dạng ngày (`05/11/2026` → `parseDateOrNull` trả `null` → `MSG-062`), ngày tương lai (`MSG-063`), `docNo` > 50, `title`/`note` > 255, `fileUrl` > 500, URL không phải `http://`/`https://` hoặc chứa `<`, `>`, `"`, loại cũ/ngoài danh mục, loại `"Khác"` thiếu tên.
- **Bảo mật:**
  - CSRF: `#paperForm` (dòng 1454) và `#opForm` (dòng 1506) ở `updatecontract.jsp` đều mang `<input type="hidden" name="csrfToken" value="${csrfToken}">`.
  - XSS: 100% giá trị hiển thị mới trong `updatecontract.jsp` và `viewcontractdetail.jsp` (`t`, `doc.docNo`, `doc.title`, `doc.note`, `doc.uploadedByName`, `doc.fileUrl`, `doc.displayName`) đều bọc `fn:escapeXml(...)`; ngày dùng `<fmt:formatDate>`; JS gán thông điệp bằng `textContent` (không dùng `innerHTML` cho dữ liệu người dùng); `param.error` chỉ so sánh hằng trong `<c:when>`.
  - Link ngoài: nút `"Mở"` luôn có `target="_blank" rel="noopener noreferrer"`; `TextRules.isSafeHttpUrl` chặn scheme nguy hiểm lúc thêm và `ContractControllerHelper` kiểm lại cho `primaryDocumentUrl`.
  - Phân quyền RBAC: giữ nguyên `PermissionService.canManageContractDocuments` (Admin, Sales quản lý vùng; Sales có cấp trên và Tổng hợp trả 403 `MSG-022`); chống IDOR khi huỷ (`isDocumentInContract`).
  - Xoá mềm & giao dịch: `voidDocument` vẫn xoá mềm `is_deleted = 1` bắt buộc lý do; `addDocument` và `voidDocument` ghi hai bảng trong cùng một giao dịch nguyên tử (`NFR-19`).
- **Quy chuẩn:** Không có `javax.*`, không có placeholder/`// TODO`; 14/14 file thuộc task hợp lệ UTF-8 strict không BOM; không đổi chữ ký public API cũ (chỉ thêm overload constructor 4 tham số ở `ContractDocumentService` và getter/setter mới ở `ContractDocument`); mọi hàm sửa/thêm mới đều ≤ 40 dòng (`ContractDocumentDAO.addDocument` 40 dòng, `ContractDocumentService.validateDocumentInput` 28 dòng, `ContractDocumentController.handleAddDocument` 26 dòng, `errorParamOf` 10 dòng; `ContractDocument` 140 dòng, `ContractDocumentService` 191 dòng, `ContractDocumentController` 223 dòng).

### D & F. Phạm vi, Không đụng & Tiêu chí "Xong khi"

- **Nhánh & Git:** Đang ở đúng nhánh `feat/task-10.3-giay-to-o-tich`, không phải `main`; `git log main..HEAD` rỗng (chưa có commit của bước Code); `task_tracker.md` vẫn giữ `[ ]` ở Task 10.3.
- **Phạm vi file:** Đủ 8 file "File sửa" + 3 file "File test" của Task 10.3; có thêm 3 file ngoài danh sách (`ContractControllerHelper.java`, `ContractDetailControllerTest.java`, `ContractStatusIntegrationTest.java`) — xem mục 🟡 #3.
- **Không đụng:** Không chạm kỳ thanh toán (Task 10.4), nhãn bước giao hàng (Task 10.5), phân quyền (`PermissionService`); không tạo bảng, DAO, service, controller mới. Agent không chạy migration lên CSDL nào.
- **Tiêu chí "Xong khi":**
  1. Tab `Giấy tờ` hiện đủ danh mục 15 loại dạng ô tích (cả trang Sửa và trang Chi tiết chỉ đọc) — Đạt.
  2. Thêm được tờ không có link (ghi `file_url = NULL`, có `doc_date` và `doc_no`) — Đạt.
  3. Một loại có thể có nhiều tờ — Đạt.
  4. Huỷ (bỏ tích) bắt buộc lý do (modal `ask()` + kiểm ở service/DAO) và có dòng nhật ký — Đạt.

### E. Kiểm thử

- **Kết quả `ant test`:** **2.296 pass / 0 fail / 0 error / 3 skipped** (+30 test mới so với Task 10.2: `ContractDocumentServiceTest` 53 [+16], `ContractDocumentControllerTest` 33 [+6], `ContractDocumentDAOTest` 27 [+6], `ContractDetailControllerTest` 13 [+2]; `ContractControllerTest` 150 pass).
- **Kiểm tra thay đổi trên test cũ (`rules.md` §3):** Không có test nào bị xoá. Có chỉnh sửa fixture (`doc.setDocDate(...)`, đổi `"Biên bản nghiệm thu"` → `"Biên bản nghiệm thu bàn giao"`) và cập nhật 4 assertion theo luật mới `BR-59` (`ContractDocumentServiceTest` dòng 249, 259, 262 cho link không bắt buộc; `ContractDocumentDAOTest` dòng 215, 326 cho hậu tố `(ngày 01/03/2026)` trong nhật ký thêm tờ) — không phải che lỗi, nhưng cần ghi rõ xác nhận ở bước Fix/Commit. Cần xử lý 2 lỗi test 🟡 #1 (`ContractDocumentDAOTest` đi nhầm nhánh `docDate == null`) và 🟡 #2 (`ContractDocumentServiceTest.sampleDocument` gán cứng `"2026-11-01"` với đồng hồ hệ thống).

## Ghi chú ngoài phạm vi

- **Độ dài file `ContractDocumentDAO.java` (323 dòng > 250 dòng):** File vốn đã 274 dòng trên `main` trước Task 10.3; mọi phương thức bên trong đều ≤ 40 dòng và Task 10.3 cấm tạo DAO mới nên giữ nguyên là hợp lý.
- **`ContractStatusIntegrationTest.java` dòng 505:** Test tích hợp `taiLieu_huyLaXoaMemVaBatBuocLyDo` vẫn đặt `doc.setDocType("Hoá đơn")` (loại cũ trước V44); test vẫn chạy được vì `ContractDAO.addDocument` không kiểm `TYPES` (tầng Service mới kiểm), nhưng nên lưu ý nếu sau này dùng lại fixture đó qua Service.
- **Migration CSDL cho người dùng:** Trước khi chạy ứng dụng trên `poscs_db` với nhánh Task 10.3, người dùng tự áp `db/migrations/V44__contract_documents_date_and_number__ndat2003.sql` lên `poscs_db` (agent không chạy SQL lên CSDL theo `rules.md` §3).
- **Tình trạng working tree cho bước Commit:** `task_tracker.md` đang có sẵn diff kế hoạch Giai đoạn 10 (Quyết định 16, Task 10.6–10.7) và Task 10.3 vẫn `[ ]`; ba file untracked `docs/HuongDan_DoiEmailGui.md`, `docs/PROMPTS.md`, `task_tracker_deploy.md` nằm ngoài phạm vi Task 10.3 — bước Commit không `git add` ba file này.
- **Integration test (`test/poscs/integration/`):** 3 bộ test tích hợp mặc định `skipped` trong `ant test`; người dùng có thể tự chạy trên DB `_it` để xác minh thêm `V44` và cột `doc_date NOT NULL` / `file_url NULL` trên MySQL thật.
