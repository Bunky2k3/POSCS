# QA Report - Task 8.5

**Kết luận:** CẦN SỬA (0 🔴 Critical, 1 🟡 Major, 0 🟢 Minor)
**Kết quả `ant test`:** 2.126 pass / 0 fail / 0 error / 3 skipped (tổng 2.129 test ở 92 lớp, `BUILD SUCCESSFUL`; `ContractDAOTest` 131/131, `ContractServiceTest` 71/71, `ContractControllerTest` 137/137, `ContractActionControllerTest` 94/94 pass; 3 skipped là test tích hợp mặc định bỏ qua)
**Kết quả `ant compile-jsps`:** `BUILD SUCCESSFUL`

**Phạm vi:** Task 8.5 chốt luật người phụ trách hợp đồng lấy theo người tạo và khoá sau khi tạo, với ngoại lệ duy nhất Admin đổi được khi người phụ trách hiện tại đã bị khoá tài khoản (sang một Sales đang hoạt động). Đối chiếu với `task_tracker.md` (UC-33, UC-35, UC-46, UC-54; BR-35, `MSG_OWNER_REQUIRED`, `MSG_CANNOT_EDIT_LOCKED`), `docs/SPEC.md` (§5.4, §6, §11 quyết định 13) và `rules.md` (§3–§4); kiểm tra trên nhánh `feat/task-8.5-contract-owner-creator`.

## Danh sách lỗi

### 🟡 Major

- [x] **[QA-8.5-01] `ContractActionController.updateByTermsLock` ghi đổi người phụ trách xuống DB trước khi kiểm tra hợp lệ các ô của bản Nháp (partial commit khi form lỗi)**
  - *Đã sửa (bước Fix):* thêm `ContractService.updateDraftReplacingOwner(contract, newOwnerId, actor)`: kiểm mọi điều kiện của ngoại lệ (Admin, người cũ đã bị khoá, người mới là Sales đang hoạt động; tách thành `checkOwnerReplacement`, không ghi gì) → `updateContract` (kiểm hợp lệ và lưu các ô của bản Nháp) → chỉ khi lưu xong mới `contractDAO.replaceOwner`. Form lỗi (trùng mã, thiếu tiêu đề, sai ngày, phụ lục vượt giá trị) hoặc người thay không hợp lệ thì không ghi gì, kể cả `owner_id`. `updateContractOwner` dùng chung `checkOwnerReplacement`, hành vi giữ nguyên. `updateByTermsLock` với bản Nháp có chọn người thay chỉ gọi `updateDraftReplacingOwner`; hợp đồng đã ký vẫn gọi `updateContractOwner`. Test mới: `ContractServiceTest` 7 test `updateDraftReplacingOwner_*` (ô lỗi không ghi gì kể cả người phụ trách, người thay sai không lưu ô nào, hợp lệ thì lưu rồi mới đổi, Sales/người cũ còn hoạt động bị MSG-022, hợp đồng đã ký bị từ chối, `null`, DAO hỏng); `ContractActionControllerTest` thay 2 test Nháp cũ của bước Code bằng 3 test qua service giả và thêm 2 test với `ContractService` thật (trùng mã → `duplicate_code`, không `update`, không `replaceOwner`; hợp lệ → `update` rồi mới `replaceOwner`). Toàn bộ `ant test`: 2.139 test, 0 fail, 3 skipped.
  - **Vị trí:** `src/java/poscs/controller/ContractActionController.java#L169-L181`
  - **Hiện trạng:** Khi hợp đồng còn **Nháp** (`!existing.isTermsLocked()`) và thuộc ngoại lệ `canReplaceOwner(existing, u)` (Admin sửa bản Nháp có người phụ trách đã bị khoá tài khoản), nếu Admin vừa chọn `ownerId` hợp lệ vừa sửa một ô khác bị lỗi kiểm tra (ví dụ trùng mã hợp đồng, thiếu tiêu đề, sai thứ tự ngày, phụ lục vượt giá trị còn lại), `updateByTermsLock` gọi `service.updateContractOwner(id, ownerId, u)` **trước** rồi mới gọi `service.updateContract(c, u)`.
  - **Hệ quả:** `updateContractOwner` đã commit `owner_id` mới và ghi dòng nhật ký vào DB, sau đó `updateContract` trả lỗi kiểm tra và redirect về trang Sửa kèm `error=invalid` / `duplicate_code` / `value_negative`. Người dùng nhận thông báo lưu thất bại nhưng `owner_id` thực tế đã bị đổi (và ô chọn người thay biến mất khi tải lại form). Đối chiếu với lối `/contract` (`ContractController.handleUpdate` dòng 614–639), lối kia kiểm hợp lệ toàn bộ form rồi mới gọi `replaceOwner`.
  - **Đề xuất sửa:** Không gọi `updateContractOwner` trước khi xác nhận dữ liệu cập nhật của bản Nháp hợp lệ (ví dụ: kiểm hợp lệ `newOwnerId` và cập nhật bản Nháp trước khi đổi `owner_id`, hoặc gộp kiểm tra + ghi trong một đường xử lý của `ContractService` để tránh commit nửa vời khi form lỗi). Cập nhật/bổ sung unit test cho tình huống bản Nháp có `canReplaceOwner=true`, `ownerId` hợp lệ nhưng `updateContract` thất bại.

## Chi tiết kiểm tra

**A. Phạm vi & Git**
- Nhánh hiện tại: `feat/task-8.5-contract-owner-creator`, khớp dòng "Nhánh" của Task 8.5.
- Các file thuộc task (11 file, khớp đúng danh sách "File sửa" & "File test"):
  - `docs/SPEC.md`
  - `src/java/poscs/service/ContractService.java`
  - `src/java/poscs/controller/ContractActionController.java`
  - `src/java/poscs/controller/ContractController.java`
  - `src/java/poscs/dao/ContractDAO.java`
  - `web/jsp/sale/addnewcontract.jsp`
  - `web/jsp/sale/updatecontract.jsp`
  - `test/poscs/service/ContractServiceTest.java`
  - `test/poscs/controller/ContractControllerTest.java`
  - `test/poscs/controller/ContractActionControllerTest.java`
  - `test/poscs/dao/ContractDAOTest.java`
- Phần "Không đụng": `owner_id` dữ liệu cũ trong CSDL, `ChangeRequest*`, phạm vi "Của tôi / Toàn chi nhánh", thông báo, dashboard và người ký bên mình (Task 8.4) đều không bị đụng tới. Đạt.
- Mã hóa & công nghệ: cả 11 file đều là UTF-8 thuần (không BOM, không UTF-16), tiếng Việt chuẩn; không dùng `javax.*` (dùng hoàn toàn `jakarta.*`). Đạt.

**B. Đối chiếu SPEC (`docs/SPEC.md`, BR-35; UC-33, UC-35, UC-46, UC-54)**
- `docs/SPEC.md`:
  - `BR-35` (§6) đã bổ sung đúng câu quy định về người phụ trách theo `task_tracker.md`.
  - §5.4 đoạn "Tạo (UC-33)" đã trỏ sang BR-35; đoạn "Cập nhật (UC-35)" đã cập nhật đúng quy định người phụ trách không đổi ở mọi tiến độ (trừ ngoại lệ của Admin) và form Sửa hợp đồng đã ký không còn ô nào sửa được.
  - §11 đã bổ sung quyết định 13 tóm tắt đầy đủ mục 11 "Quyết định đã chốt". Đạt.
- `ContractService`:
  - Tách hàm tĩnh dùng chung `applyCreateOwner(Contract, Contract, int, boolean)` và `canReplaceOwner(Contract, boolean, boolean)` (cùng bản đọc DB `canReplaceOwner(Contract, User)`).
  - `createContract`: Sales tạo tự gán `ownerId` = chính actor (bỏ qua giá trị gửi lên); Admin tạo giữ lựa chọn và kiểm `isActiveSales` (vai Sales, chưa bị khoá).
  - `createAmendment`: gán `ownerId` = `parent.getOwnerId()`, bỏ qua giá trị gửi lên.
  - `updateContract`: từ Đã ký trở đi (`existing.isTermsLocked()`) từ chối ngay bằng `MSG_CANNOT_EDIT_LOCKED`; bản Nháp luôn chép `ownerId` từ `existing`.
  - `correctSignedContract` (UC-54): giữ nguyên `ownerId` từ `existing`.
  - `updateContractOwner`: chỉ cho Admin (`permissionService.isAdmin(actor)` → sai trả `MSG-022`), chặn khi hợp đồng đóng băng (`MSG_CANNOT_EDIT_LOCKED`), chặn khi người phụ trách hiện tại còn hoạt động (`ERR_FORBIDDEN`), kiểm người mới là Sales đang hoạt động (`MSG_OWNER_REQUIRED`), gọi `contractDAO.replaceOwner`.
- `ContractDAO`:
  - `withTermsFrom(before)` và `keepOwnerFrom(before, target)` đảm bảo `update()` và `correct()` luôn giữ `owner_id` cũ (kể cả bản Nháp và UC-54).
  - Thêm `replaceOwner(contractId, newOwnerId, actorId)` dùng `SQL_UPDATE_ADMIN_FIELDS`, khoá dòng `FOR UPDATE`, chặn hợp đồng đóng băng và ghi nhật ký `contract_history` ("Người phụ trách: cũ → mới") trong cùng một giao dịch.
- `ContractController` (lối `/contract`):
  - Set đúng `ownerSelectable` cho form tạo/phụ lục và `canReplaceOwner` cho form sửa.
  - Áp đúng BR-35 tại `handleCreate`, `handleCreateAmendment`, `handleUpdate` (tách `replaceOwnerOfSigned` cho nhánh đã ký; nhánh Nháp kiểm hợp lệ toàn bộ rồi mới gọi `replaceOwner` + `update`) và `handleCorrect`.
  - `isActiveAccount` và `isActiveSales` dùng `probeUser` (role = `null`), nên không rơi vào nhánh fallback sessionUser của `getPermissionService()` mà đọc đúng trạng thái tài khoản từ `employeeDAO`.
- `ContractActionController` (lối `/contract/action`):
  - Set đúng `ownerSelectable` và `canReplaceOwner` cho 3 form; kích thước file 245 dòng (≤ 250 dòng), các hàm đều ≤ 40 dòng.
  - Ghi nhận lỗi 🟡 **QA-8.5-01** ở `updateByTermsLock` cho nhánh bản Nháp có `canReplaceOwner = true`.

**C. Giao diện & Bảo mật (`addnewcontract.jsp`, `updatecontract.jsp`)**
- `addnewcontract.jsp`:
  - Phụ lục: ô chỉ đọc `disabled` (không có `name`) hiện `parentContract.owner.fullName` (escape bằng `fn:escapeXml`) kèm chú thích; bỏ comment "VẪN cho đổi".
  - Admin tạo hợp đồng gốc (`ownerSelectable`): hiện dropdown Sales như cũ.
  - Sales tạo hợp đồng gốc: ô chỉ đọc `disabled` hiện `sessionScope.currentUser.fullName` kèm chú thích.
  - JS kiểm tra form chỉ thêm `'owner'` khi `${ownerSelectable and empty parentContract}`.
- `updatecontract.jsp`:
  - Bình thường: ô Người phụ trách dạng chỉ đọc `disabled` (không có `name`) hiện `contract.owner.fullName`.
  - Khi `canReplaceOwner`: hiện dropdown Sales đang hoạt động (`EmployeeDAO.findActiveByRole` lọc `u.is_deleted = 0`, nên không lẫn người đã bị khoá) kèm chú thích `"Người phụ trách đã bị khoá tài khoản, chọn người thay."`.
  - Khung "Hợp đồng đã ký" đã bỏ câu "Chỉ còn người phụ trách sửa được".
  - Nút "Lưu thay đổi" ẩn khi đã khoá điều khoản, chỉ hiện nếu `canReplaceOwner` hoặc khi Admin bấm "Chữa sai sót nhập liệu"; lúc bật chế độ chữa sai sót, JS khoá thêm `#owner` để tránh hiểu nhầm.
  - JS đã bỏ `'owner'` khỏi danh sách kiểm bắt buộc.
  - CSRF token vẫn giữ đầy đủ trên cả hai form.

**D. Unit Test & Kiểm soát sửa test cũ (rules §3–§4)**
- Bổ sung +58 test mới (2.071 → 2.129) phủ đầy đủ các nhánh: Sales/Admin tạo mới, phụ lục, sửa bản Nháp, sửa hợp đồng đã ký, chữa sai sót UC-54, POST tự gõ `ownerId`, Admin đổi khi người cũ còn hoạt động/đã khoá, Sales đổi, `replaceOwner` ghi nhật ký và chặn hợp đồng đóng băng.
- Các test cũ được điều chỉnh đều nằm đúng nhóm hành vi thay đổi theo BR-35 (có comment rõ `Task 8.5 (BR-35)`), không có dấu hiệu che lỗi:
  - `ContractServiceTest`: `createAmendment` (ca `orphanParent`), `updateContract` (bản Nháp bỏ qua `ownerId`, bản đã ký bị từ chối kể cả ca BR-56), `updateContractOwner` (chỉ Admin khi người cũ bị khoá), `correctSignedContract` (bỏ qua `ownerId` gửi lên).
  - `ContractActionControllerTest`: 2 test `handleUpdate_termsLocked_*` chuyển từ kỳ vọng gọi `updateContractOwner` sang đi qua `updateContract` và bị từ chối (tách riêng test cho ngoại lệ Admin).
  - `ContractControllerTest`: 2 test `handleUpdate` trên hợp đồng đã ký (cập nhật kỳ vọng sang ngoại lệ Admin gọi `replaceOwner` hoặc bị từ chối).
  - `ContractDAOTest`: 1 test đổi người phụ trách trên hợp đồng đã ký chuyển sang gọi `dao.replaceOwner` và thêm test khóa `dao.update` giữ nguyên `owner_id`.

## Ghi chú ngoài phạm vi

- Ở `ContractController.handleUpdate` (lối `/contract`), khi Admin vừa thay người phụ trách vừa sửa bản Nháp, `contractDAO.replaceOwner` và `contractDAO.update` hiện chạy trong 2 giao dịch kế tiếp nhau (đã kiểm hợp lệ toàn bộ trước khi ghi nên rủi ro thực tế rất thấp; chỉ đáng lưu ý nếu gặp lỗi DB đột ngột ở câu `update` thứ hai).
- `task_tracker.md` đang có thay đổi sẵn ở working tree và `docs/PROMPTS.md`, `task_tracker_deploy.md` đang untracked từ trước. Ở bước Commit Task 8.5, chỉ `git add` đúng 11 file thuộc Task 8.5 và `docs/qa/Task-8.5.md` (không dùng `git add .`).
