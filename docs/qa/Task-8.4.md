# QA Report - Task 8.4

**Kết luận:** ĐẠT (không có lỗi cần sửa)
**Kết quả `ant test`:** 2.068 pass / 0 fail / 0 error / 3 skipped (tổng 2.071 test ở 92 lớp, `BUILD SUCCESSFUL`; `ContractDAOTest` 123/123, `ContractValidatorTest` 58/58, `ContractServiceTest` 55/55, `ContractControllerTest` 116/116, `ContractActionControllerTest` 81/81 pass; 3 skipped là test tích hợp mặc định bỏ qua)
**Kết quả `ant compile-jsps`:** `BUILD SUCCESSFUL`

**Phạm vi:** Task 8.4 chuyển bốn ô người ký bên mình (họ tên, chức vụ, SĐT, fax) trên form hợp đồng sang lấy tự động từ Thông tin chi nhánh (Giám đốc chi nhánh), khoá chỉ đọc trên form tạo/sửa và hiển thị thêm SĐT/fax ở trang chi tiết hợp đồng. Đối chiếu với `task_tracker.md` (UC-33, UC-35, UC-46, UC-54, UC-11; BR-56, MSG-060), `docs/SPEC.md` (§5.4, §6, §9, §11 quyết định 12) và `rules.md` (§3–§4); kiểm tra trên nhánh `feat/task-8.4-contract-branch-signer`.

## Danh sách lỗi

Không phát hiện lỗi 🔴 Critical, 🟡 Major hay 🟢 Minor.

## Chi tiết kiểm tra

**A. Phạm vi & Git**
- Nhánh hiện tại: `feat/task-8.4-contract-branch-signer`, khớp dòng "Nhánh" của Task 8.4.
- Các file thuộc task (14 file):
  - `src/java/poscs/dao/ContractDAO.java`
  - `src/java/poscs/service/ContractService.java`
  - `src/java/poscs/controller/ContractController.java`
  - `src/java/poscs/controller/ContractActionController.java`
  - `src/java/poscs/controller/ContractControllerHelper.java`
  - `src/java/poscs/validator/ContractValidator.java`
  - `web/jsp/sale/addnewcontract.jsp`
  - `web/jsp/sale/updatecontract.jsp`
  - `web/jsp/sale/viewcontractdetail.jsp`
  - `test/poscs/dao/ContractDAOTest.java`
  - `test/poscs/validator/ContractValidatorTest.java`
  - `test/poscs/service/ContractServiceTest.java`
  - `test/poscs/controller/ContractControllerTest.java`
  - `test/poscs/controller/ContractActionControllerTest.java`
- Phần "Không đụng": người ký bên đối tác, căn cứ uỷ quyền, nơi ký vẫn giữ nhập tay; `ContractExportService` và dữ liệu hợp đồng cũ trong CSDL không bị đụng tới. Đạt.
- Mã hóa & công nghệ: cả 14 file đều là UTF-8 thuần (không BOM, không UTF-16), tiếng Việt chuẩn; không dùng `javax.*` (dùng hoàn toàn `jakarta.*`); không sửa hay xoá assertion của bất kỳ test cũ nào (`git diff -U0 test/` không có dòng xoá nào). Đạt.

**B. Đối chiếu SPEC (BR-56, MSG-060; UC-33, UC-35, UC-46, UC-54, UC-11)**
- `ContractDAO`:
  - `SELECT_BASE`, `lockForUpdate`, `mapContract` đã bổ sung đủ `signer_phone`, `signer_fax`.
  - `SQL_INSERT` (21 tham số `?`) và `SQL_UPDATE_ALL` (19 tham số `?`) đặt `signer_phone`, `signer_fax` ngay sau `signer_position`; `bindSigningParties` tăng `SIGNING_PARTY_COLUMNS` lên 9 và dịch đúng chỉ số tham số đứng sau (`parent_contract_id` = 21 trong INSERT, `contract_id` = 19 trong UPDATE).
  - `withTermsFrom` giữ nguyên 4 ô khi hợp đồng đã ký; `executeCorrectTransaction` gọi `keepBranchSignerFrom(before, contract)` khóa 4 ô từ bản ghi đang lưu kể cả khi caller truyền giá trị khác; `describeChanges` ghi thêm "Điện thoại người ký bên mình" và "Fax người ký bên mình".
- `ContractValidator`:
  - Bổ sung `MAX_SIGNER_PHONE_LENGTH = 20`, `MAX_SIGNER_FAX_LENGTH = 20` khớp cột `varchar(20)` (V39 / quyết định 12), kiểm tra độ dài trong `validateSigningPartyLengths`.
  - `hasRestrictedFieldsChanged` so sánh thêm `signerPhone` và `signerFax`.
- `ContractService`:
  - Giữ nguyên chữ ký constructor 0 và 5 tham số, bổ sung constructor 6 tham số nhận `BranchInfoService` để tiêm phụ thuộc.
  - `isBranchSignerDeclared`: coi là đã khai chỉ khi họ tên GĐCN khác rỗng sau khi trim (dòng gieo sẵn của V39 chỉ có chức vụ → chưa khai).
  - `applyBranchSigner`: chép đủ 4 ô (đã trim, rỗng → `null`) khi đã khai; chưa khai thì không đụng tới hợp đồng.
  - `createContract` và `createAmendment` (`fillSignerForNewContract`): xoá trắng 4 ô rồi chép GĐCN hiện tại; chưa khai thì để trống cả 4 ô.
  - `updateContract` (`fillSignerForUpdate`): bản Nháp (`!existing.isTermsLocked()`) giữ giá trị đang lưu nếu chưa khai, hoặc cập nhật theo GĐCN hiện tại nếu đã khai; từ Đã ký trở đi giữ nguyên 4 ô đang lưu và không đọc `branchInfoService`.
  - `correctSignedContract` (UC-54): gọi `copyBranchSigner(contract, existing)`, không đổi 4 ô người ký bên mình và không đọc `branchInfoService`.
- `ContractController` (lối `/contract`) & `ContractActionController` + `ContractControllerHelper` (lối `/contract/action`):
  - Cả hai `buildContractFromRequest` đã bỏ đọc `signerName`/`signerPosition` từ request; server không bao giờ nhận 4 ô này từ phía client.
  - Cả hai lối đều gắn `branchInfo` và `branchSignerMissing` qua `ContractControllerHelper.putBranchSigner` cho 3 form (`new`, `newAmendment`, `edit`).
  - `ContractController` áp dụng đúng BR-56 tại `handleCreate`, `handleCreateAmendment`, `handleUpdate` (cả nhánh Nháp và đã khoá điều khoản) và `handleCorrect`; tiêm `branchInfoService` nhất quán với các DAO của controller.

**C. Giao diện & Bảo mật (`addnewcontract.jsp`, `updatecontract.jsp`, `viewcontractdetail.jsp`)**
- `addnewcontract.jsp`:
  - Thay 2 ô nhập bằng 4 ô `disabled` và không có thuộc tính `name` (Người ký bên mình, Chức vụ, Điện thoại, Fax); chưa khai GĐCN thì để trống cả 4 ô (kể cả chức vụ gieo sẵn) và hiện cảnh báo đúng nguyên văn `MSG-060`: `"Chưa có thông tin Giám đốc chi nhánh. Admin khai tại trang Thông tin chi nhánh."` (Admin thấy thêm link `/branch`).
  - Đã cập nhật chú thích đầu mục và bỏ placeholder `"VD: Giám đốc chi nhánh"`.
- `updatecontract.jsp`:
  - 4 ô người ký bên mình đều `disabled`, không có `name` và **không gắn `data-term="1"`**, nên chế độ "Chữa sai sót nhập liệu" (UC-54) không mở khoá các ô này.
  - Bản Nháp (`canEditTerms`) hiện `branchInfo` hiện tại nếu đã khai, hiện cảnh báo `MSG-060` + giữ giá trị đang lưu trên hợp đồng nếu chưa khai; hợp đồng đã khoá điều khoản luôn hiện giá trị đang lưu trên hợp đồng.
- `viewcontractdetail.jsp`:
  - Hiển thị thêm dòng `Điện thoại:` và `Fax:` dưới người ký bên mình khi không rỗng; mọi dữ liệu đều được escape bằng `fn:escapeXml`.

**D. Quy chuẩn kiến trúc & Unit Test (rules §3–§4)**
- Các phương thức mới thêm đều ngắn gọn (≤ 15 dòng). `ContractActionController.java` (251 dòng) và `ContractControllerHelper.java` (259 dòng) tăng nhẹ từ nền sẵn có (248/250 dòng), chấp nhận được trong phạm vi task.
- Bổ sung 43 unit test mới phủ đủ toàn bộ kịch bản yêu cầu trong `task_tracker.md`:
  - `ContractDAOTest` (+6 test): INSERT bind đúng vị trí 12–21; UPDATE bản Nháp bind đúng 10–19; nhật ký ghi thay đổi SĐT/fax; UPDATE bản đã ký giữ 4 ô qua `withTermsFrom`; `correct` giữ 4 ô từ dòng đang khoá; `findById` ánh xạ `signer_phone`/`signer_fax`.
  - `ContractValidatorTest` (+3 test): giới hạn 20 ký tự cho `signerPhone`/`signerFax`, `hasRestrictedFieldsChanged`, `validateUpdate` chặn đổi `signerPhone` khi đã ký.
  - `ContractServiceTest` (+15 test): `isBranchSignerDeclared`, `applyBranchSigner`, `copyBranchSigner`, `findBranchInfo`, tạo mới/phụ lục (đã khai, chưa khai, lỗi đọc DB, không có quyền), sửa bản Nháp (đã khai/chưa khai), sửa bản đã ký, chữa sai sót UC-54.
  - `ContractControllerTest` (+11 test) và `ContractActionControllerTest` (+8 test): POST kèm `signerName`/`signerPosition`/`signerPhone`/`signerFax` tự gõ bị bỏ qua trên tạo/phụ lục/sửa/chữa sai sót; form `new`/`newAmendment`/`edit` set đúng `branchInfo` và `branchSignerMissing`.

## Ghi chú ngoài phạm vi

- `task_tracker.md` đang có thay đổi sẵn ở working tree và `docs/PROMPTS.md`, `task_tracker_deploy.md` đang untracked từ trước. Ở bước Commit Task 8.4, chỉ `git add` đúng 14 file thuộc Task 8.4 và `docs/qa/Task-8.4.md` (không dùng `git add .`).
- Nhắc lại điều kiện triển khai: CSDL `poscs_db` phải chạy migration `V39__add_branch_info_and_contract_signer_contact__ndat2003.sql` (Task 8.2) trước khi kéo code Task 8.4 về checkout chính hoặc kiểm tra thủ công trên Tomcat 8080.
