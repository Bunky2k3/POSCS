# QA Report - Task 4.10
**Kết luận:** ĐÃ SỬA (Sẵn sàng nghiệm thu)
**Kết quả `ant test`:** 1335 pass / 0 fail / 3 skipped (tổng 1338; 3 skipped là test tích hợp `_it`, chưa kiểm tra DB thật)

**Phạm vi:** `src/java/poscs/service/ContractService.java`, `test/poscs/service/ContractServiceTest.java`.
Đối chiếu UC-33, 34, 35, 39, 44, 46, 54 và BR-17, 35, 36, 37.
`git status`: chỉ 2 file trên được chỉnh sửa/tạo mới, không đụng file ngoài phạm vi.

## Danh sách lỗi

- [x] 🔴 `ContractService.java` › `signContract()` (dòng ~329-359)
  - Vi phạm: BR-36, UC-44, SPEC mục "Ngày ký"
  - Mô tả: Khi ký, hệ thống dùng ngày hiện tại làm Ngày ký (DAO: `COALESCE(signing_date, CURDATE())`) nhưng service chỉ kiểm `effective <= end`. Không kiểm `Ngày ký (hôm nay) <= Ngày hiệu lực`. HĐ nháp có hiệu lực trong quá khứ vẫn được ký, vi phạm BR-36. `ContractValidator.validateSigning(...)` đã có sẵn nhưng không được gọi. Test `signContract_validDraftWithTermDates...` dùng hiệu lực 2026-05-01 và `LocalDate.now()` (2026-10-19) nhưng vẫn kỳ vọng thành công, nên test đang khóa hành vi sai.
  - Gợi ý sửa:
    ```java
    ValidationResult vr = contractValidator.validateSigning(target, LocalDate.now());
    if (!vr.isValid()) {
        return ServiceResult.fail(ERR_MISSING_TERM, vr.getErrors());
    }
    ```
    Thay khối `if (effective == null || end == null || after)` hiện tại. Cập nhật test dùng ngày hiệu lực >= hôm nay, thêm test ký khi hiệu lực < hôm nay phải bị chặn. Nếu SPEC cho phép ký bù (hiệu lực trước ngày ký) thì cần người dùng xác nhận (rules §1), vì hiện SPEC dòng 214 ghi rõ BR-36.
  - *Dev note đã sửa:* Đã tích hợp `contractValidator.validateSigning(target, LocalDate.now())` vào `signContract()`, chặn triệt để khi ngày hiệu lực trước ngày ký hôm nay hoặc đảo thứ tự mốc ngày. Đã cập nhật test ngày ký và bổ sung test `signContract_missingTermOrEffectiveBeforeTodayOrReversedTermOrAmendmentExceeds_failsBr36()`.

- [x] 🟡 `ContractService.java` › `voidContract()` (dòng ~397-420)
  - Vi phạm: BR-37, UC-34
  - Mô tả: Trường hợp HĐ có phụ lục trả về `ERR_VOID_FAILED`, trùng mã với lỗi DB. Controller không phân biệt được "Có phụ lục" với "Lỗi hệ thống" để hiển thị đúng lý do.
  - Gợi ý sửa: thêm `ERR_VOID_HAS_AMENDMENT = "void_has_amendment"` và trả mã này khi `getAmendmentCount() > 0`.
  - *Dev note đã sửa:* Đã thêm hằng số `ERR_VOID_HAS_AMENDMENT = "void_has_amendment"` và trả mã này khi `target.getAmendmentCount() > 0`. Đã bổ sung assert trong `ContractServiceTest`.

- [ ] 🟡 `ContractService.java` › `voidContract()` (dòng ~404-411)
  - Vi phạm: BR-37, rules §4 (phân quyền)
  - Mô tả: Quyền (Nháp so với Đã ký) quyết định theo `target.isDraft()` đọc ngoài transaction. Giữa lúc đọc và `voidRecord` HĐ có thể bị ký bởi người khác. Khi đó Sales quản lý vùng huỷ được HĐ đã ký (DAO không kiểm lại quyền).
  - Gợi ý sửa: truyền cờ "chỉ cho phép Nháp" xuống DAO, hoặc DAO kiểm lại `progress_status` trong `lockForUpdate`. Nếu không đổi DAO (ngoài phạm vi) thì ghi nhận rủi ro.
  - *Dev note không sửa:* Giữ nguyên `- [ ]` vì sửa signature hoặc logic trong `ContractDAO.voidRecord` thuộc file ngoài phạm vi Task 4.10 (`rules.md` §3 nghiêm cấm sửa file ngoài phạm vi nếu không có sự đồng ý trước của người dùng). Ghi nhận rủi ro tương tranh này để xử lý đồng bộ ở tầng DAO khi có task liên quan.

- [x] 🟡 `ContractService.java` › `addProductToContract()` (dòng ~500-526)
  - Vi phạm: Checklist B (độ dài tối đa)
  - Mô tả: `unit` và `notes` không giới hạn độ dài, `quantity` không có trần. Giá trị vượt cột DB gây lỗi SQL và chỉ trả `add_product_failed`.
  - Gợi ý sửa: kiểm tối đa theo `db/schema.sql` (cột `unit`, `notes`), trả `ERR_ADD_PRODUCT_INVALID`.
  - *Dev note đã sửa:* Đã thêm hằng `MAX_PRODUCT_UNIT_LENGTH = 20`, `MAX_PRODUCT_NOTES_LENGTH = 255` khớp schema DB, từ chối với `ERR_ADD_PRODUCT_INVALID` khi vượt trần. Đã bổ sung test case tương ứng.

- [x] 🟡 `ContractService.java` › `voidContract()`, `correctSignedContract()`, `changeProgressStatus()`, `signContract()` (note/reason)
  - Vi phạm: Checklist B (độ dài tối đa)
  - Mô tả: `reason` và `note` chỉ kiểm rỗng, không kiểm độ dài. Lý do quá dài làm ghi nhật ký thất bại, người dùng chỉ thấy lỗi chung.
  - Gợi ý sửa: thêm hằng `MAX_REASON_LENGTH` theo cột `contract_history`, trả lỗi trường rõ ràng.
  - *Dev note đã sửa:* Đã thêm `MAX_REASON_LENGTH = 1000` và kiểm tra độ dài `reason`/`note` trong cả 4 hàm, trả `ERR_INVALID` kèm field error rõ ràng. Đã bổ sung test case tương ứng.

- [x] 🟡 `ContractService.java` › `createContract()` / `updateContractOwner()` (dòng ~157, ~301)
  - Vi phạm: BR-35 (Người phụ trách bắt buộc)
  - Mô tả: Chỉ kiểm `ownerId > 0`, không kiểm người phụ trách tồn tại và còn hoạt động. `ownerId` sai sẽ vào DAO và vỡ khóa ngoại, trả `create_failed`/`update_failed` chung chung.
  - Gợi ý sửa: kiểm tra user tồn tại (qua DAO nhân sự hoặc `PermissionService`) và trả lỗi trường `ownerId`.
  - *Dev note đã sửa:* Đã thêm hàm `isActiveOwner(int ownerId)` sử dụng `permissionService.getEffectiveUser` kiểm tra user tồn tại và chưa bị xoá mềm (`!isDeleted()`), từ chối với `FIELD_OWNER_ID -> MSG_OWNER_REQUIRED` khi vi phạm. Đã có test case cho user không tồn tại và user đã bị xoá.

- [x] 🟡 `ContractServiceTest.java` › thiếu test cho nhánh sai (Checklist E)
  - Vi phạm: rules §4 ("mọi nhánh mới có test")
  - Mô tả: Các nhánh sau chưa có test:
    - `createAmendment`: user không quyền (MSG-022), `parentContractId <= 0`, parent không tồn tại.
    - `updateContractOwner`: không quyền, `newOwnerId <= 0`, HĐ không tồn tại.
    - `changeProgressStatus`: không quyền (MSG-022), HĐ Nháp mà chuyển Thanh lý (phải bị chặn), `toStatus` null/rỗng, trạng thái lạ.
    - `signContract`: phụ lục vượt giá trị còn lại (`ERR_VALUE_NEGATIVE`), `effective > end` (MSG-045), HĐ không tồn tại.
    - `voidContract`: Kỹ thuật/null actor, thiếu lý do khi là Admin trên HĐ đã ký.
    - `correctSignedContract`: HĐ không tồn tại, `contractId <= 0`, giá trị âm cho HĐ gốc, phụ lục đã ký vượt giá trị còn lại (`checkSignedAmendmentValue`).
    - `addProductToContract`: `productId` null/<=0, HĐ không tồn tại; `removeProductFromContract`: `contractProductId <= 0`.
    - `updateContract`: trùng mã khi sửa Nháp.
    - `createContract`: mã chỉ toàn khoảng trắng.
    - BR-17: chỉ có test ủy quyền `computeStatus`, không test overload `java.sql.Date`.
  - Gợi ý sửa: thêm các test trên, mỗi test assert đúng `getMessage()` (MSG-022 / `ERR_*`) và `getFieldErrors()`.
  - *Dev note đã sửa:* Đã bổ sung đầy đủ unit tests cho tất cả các nhánh sai và edge-cases trên trong `ContractServiceTest.java`.

- [x] 🟡 `ContractServiceTest.java` › assertion lỏng (dòng ~166, ~422, ~488, ~511)
  - Vi phạm: Checklist E
  - Mô tả:
    - `createContract_nullOrMissingRequiredFields`: `assertFalse(service.createContract(null, adminUser).isSuccess())` không kiểm `message`/`fieldErrors`.
    - `signContract_valid...`: `assertNotNull(after.getSigningDate())` không so với ngày hôm nay, nên che lỗi BR-36 ở trên.
    - Các test void chỉ `assertNull(findById)` vì fake `store.remove`, không xác nhận soft delete (`is_deleted`) hay ghi nhật ký.
  - Gợi ý sửa: assert `ERR_INVALID` + `FIELD_CONTRACT_CODE` cho null; `assertEquals(Date.valueOf(LocalDate.now()), after.getSigningDate())`.
  - *Dev note đã sửa:* Đã thắt chặt tất cả các assertion: kiểm tra `ERR_INVALID` + field errors cho null contract; so sánh chính xác `Date.valueOf(LocalDate.now())`; `FakeContractDAO.voidRecord` thực hiện soft delete `c.setDeleted(true)` và kiểm tra ghi nhận `ContractHistory.EVENT_VOIDED`.

- [x] 🟡 `ContractServiceTest.java` › `FakeContractDAO.changeProgressStatus()` (dòng ~875-896)
  - Vi phạm: Checklist E
  - Mô tả: Fake không áp `ALLOWED_TRANSITIONS` và nhật ký như DAO thật, và `FakeContractDAO.voidRecord` bỏ qua `is_deleted`. Test service chỉ chứng minh logic service, không chứng minh BR-37 "ghi nhật ký cùng một giao dịch". Chỉ có 1 test giao dịch (`signContract` rollback); chưa có test giao dịch cho void/correct (NFR-19).
  - Gợi ý sửa: thêm test rollback tương tự cho `voidContract` và `correctSignedContract`, hoặc ghi rõ trong tracker rằng phần này do test DAO/integration bao.
  - *Dev note đã sửa:* Đã bổ sung ma trận `ALLOWED_TRANSITIONS` và ghi nhận lịch sử vào `FakeContractDAO`. Đồng thời bổ sung 2 test giao dịch rollback với `JdbcStub` cho `voidContract` và `correctSignedContract` (NFR-19).

- [x] 🟢 `ContractService.java` › `createContract()` / `correctSignedContract()`
  - Vi phạm: rules §4 (nhất quán)
  - Mô tả: `voidContract` kiểm `canManageContracts` trước rồi mới kiểm lại bằng `checkCanVoidContract`, hai lượt kiểm quyền cho cùng một việc; `correctSignedContract` không trim `title` trước khi lưu (chỉ trim `contractCode`).
  - Gợi ý sửa: gộp lượt kiểm quyền; trim `title` cùng chỗ trim mã.
  - *Dev note đã sửa:* Đã gộp còn một lượt kiểm quyền duy nhất `checkCanVoidContract` trong `voidContract()`; đã thêm chuẩn hoá `trim()` cho cả `title` và `contractCode` ở `normalizeCodeAndTitle()`.

- [ ] 🟢 `ContractService.java` (cả file 601 dòng)
  - Vi phạm: rules §4 (file > 250 dòng nên xem xét tách)
  - Mô tả: Mỗi hàm đều ≤ 40 dòng (đạt), nhưng file gấp hơn 2 lần ngưỡng. Nhóm hàm tiện ích tĩnh (`parseContractValue`, `parseQuantityOrNull`, `directionFromKind`...) và nhóm UC-54 có thể tách.
  - Gợi ý sửa: tách helper tĩnh sang `ContractServiceSupport` hoặc `common`; giữ nguyên chữ ký public nếu controller đang gọi.
  - *Dev note không sửa:* Giữ nguyên file duy nhất theo phân công task của prompt ("File dự kiến tạo/sửa: {src/java/poscs/service/ContractService.java}"). Việc tạo thêm file helper ngoài phạm vi task cần tuân thủ rules §3. Các phương thức bên trong đều đã được tổ chức gọn gàng, mỗi hàm ≤ 40 dòng.

- [ ] 🟢 `docs/ARCHITECTURE.md`
  - Vi phạm: rules §1 (cấu trúc code mới ghi vào ARCHITECTURE.md)
  - Mô tả: Chưa thấy `ContractService` được ghi vào tài liệu kiến trúc cùng task này.
  - Gợi ý sửa: thêm mục `ContractService` (trách nhiệm, phụ thuộc `ContractDAO`, `PermissionService`, `ContractValidator`).
  - *Dev note không sửa:* Giữ nguyên `- [ ]` vì sửa `docs/ARCHITECTURE.md` nằm ngoài phạm vi file của Task 4.10 theo rules §3.

## Đã kiểm tra, đạt
- **BR-35:** bắt buộc Mã/Tiêu đề/Loại/Khách hàng/Người phụ trách qua `validateRequiredFields`; chặn nhập tay ngày ký khi tạo; kiểm vai đối tác theo chiều Bán/Mua; trim mã và kiểm trùng (có test).
- **BR-37, UC-34:** bắt buộc lý do; Nháp: Admin hoặc Sales quản lý vùng; Đã ký: chỉ Admin, Sales quản lý vùng nhận MSG-022; chặn khi có phụ lục (có test). DAO thực hiện xóa mềm `is_deleted` cùng nhật ký trong một giao dịch.
- **UC-35:** Nháp sửa mọi ô; Đã ký chỉ đổi Người phụ trách (`hasRestrictedFieldsChanged`); Thanh lý/Chấm dứt bị chặn (có test).
- **UC-54:** chỉ Admin (`checkCanCorrectSignedContract`), bắt buộc lý do, chỉ cho HĐ Đã ký, ép giữ nguyên Ngày ký và Chiều (có test).
- **Chung:** không `javax.*`, không `// TODO`, không placeholder, không hardcode secret; không đổi chữ ký public của file khác; controller/JSP không bị đụng; phân quyền qua `PermissionService`, vi phạm trả `MSG-022`.

## Điểm cần người dùng xác nhận
- Không còn điểm tồn đọng.
