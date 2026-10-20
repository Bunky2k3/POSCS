# QA Report - Task 4.19
**Kết luận:** CẦN SỬA (0 🔴 / 3 🟡 / 2 🟢 — không có lỗi sai BR/bảo mật, chỉ thiếu test và một điểm phạm vi cần xác nhận)
**Kết quả `ant test`:** 1755 pass / 0 fail / 3 skipped (tổng 1758; 3 skipped là test tích hợp, không chạy DB thật, theo rules §0)

**Phạm vi đã đối chiếu:** UC-54 (SPEC §4, dòng 155, §3 ma trận quyền dòng 86), `ContractService.correctSignedContract()` (UC-54), `ContractActionController.handleCorrect()`, `ContractActionResultHelper.redirectCorrectResult()`, `ContractDAO.correct()` (chỉ đọc để đối chiếu), test `ContractServiceTest`, `ContractActionControllerTest`, `ContractDAOTest`.
Lưu ý: tracker chỉ ghi UC-54, SPEC không có BR riêng cho UC-54. Các ràng buộc được lấy từ dòng UC-54: chỉ Admin, chưa thanh lý/chấm dứt, bắt buộc lý do, nhật ký loại riêng, ngày ký không sửa được.

## Đã kiểm tra và đạt
- Chỉ Admin: service trả `MSG-022` qua `checkCanCorrectSignedContract`; controller trả 403 + `MSG_022`. Có test cho Sales quản lý vùng, Sales có cấp trên, Kỹ thuật, user đã xóa, user null.
- Lý do: null, blank và `> 1000` ký tự đều bị chặn. Có test biên.
- Hợp đồng nháp / đã thanh lý / chấm dứt bị từ chối ở service. DAO cũng khóa dòng (`lockForUpdate`) và từ chối lại trong transaction.
- Ngày ký và chiều Bán/Mua được lấy lại từ bản ghi gốc ở cả controller, service và DAO.
- Ghi `contracts` và `contract_history` trong một transaction, có rollback. Nhật ký dùng `EVENT_CORRECTED` kèm lý do. Có `ContractDAOTest` khóa hành vi này.
- CSRF do `AuthenticationFilter` áp dụng cho mọi POST. Không có hardcode secret. Không có `// TODO`. Hàm đều ≤ 40 dòng.

## Danh sách lỗi
- [x] 🟡 `ContractServiceTest.java` › `correctSignedContract_recordsAuditHistoryWithEventCorrectedAndReason()` (dòng ~1000-1015, cuối nhóm test UC-54)
  - Vi phạm: UC-54 (nhật ký loại riêng); rules §4 (test phải có giá trị kiểm chứng)
  - Mô tả: Test khẳng định `EVENT_CORRECTED` và note đã trim, nhưng cả hai giá trị đều do **fake DAO** trong test tự ghi (`recordHistory(...)` trong `correct()` giả). Test chỉ kiểm chứng đoạn giả, không kiểm chứng logic service. Phần service chỉ có `trim()` lý do, nên phần còn lại thực chất là kiểm tra mock. Hành vi thật chỉ được `ContractDAOTest.correct_storesReasonAndItsOwnEventType` khóa.
  - Gợi ý sửa: Ở test service, dùng `ArgumentCaptor` hoặc spy trên DAO để xác nhận service truyền đúng `actor.getUserId()` và lý do đã trim. Ghi chú trong test rằng nhật ký thật được kiểm ở DAO test.
    ```java
    assertEquals(adminUser.getUserId(), contractDAO.lastCorrectionActorId);
    assertEquals("Đối chiếu lại hợp đồng gốc số 77/2026", contractDAO.lastCorrectionReason);
    ```
  - *Đã sửa:* Đã bổ sung `lastCorrectionActorId` và `lastCorrectionReason` trong `FakeContractDAO`, assert định danh actor và lý do đã trim truyền từ service xuống DAO; ghi chú đối chiếu tầng DAO thật tại `ContractDAOTest.correct_storesReasonAndItsOwnEventType`.

- [x] 🟡 `ContractServiceTest.java` › các test `correctSignedContract_*` (nhánh thất bại)
  - Vi phạm: UC-54 (không ghi gì khi vi phạm); rules §4 (nhánh mới phải có test)
  - Mô tả: Các test nhánh sai (thiếu lý do, không phải Admin, nháp/thanh lý, trùng mã, ngày sai, phụ lục vượt trần) chỉ assert `ServiceResult`. Không test nào xác nhận dữ liệu hợp đồng **không đổi** và **không phát sinh dòng nhật ký `EVENT_CORRECTED`** khi bị từ chối. Nếu sau này có người dời `contractDAO.correct()` lên trước bước validate, test vẫn xanh.
  - Gợi ý sửa:
    ```java
    assertEquals(before.getContractCode(), contractDAO.findById(60).getContractCode());
    assertTrue(service.findHistoryByContractId(60).stream()
        .noneMatch(h -> ContractHistory.EVENT_CORRECTED.equals(h.getEventType())));
    ```
  - *Đã sửa:* Đã bổ sung kiểm tra dữ liệu hợp đồng trong kho lưu trữ không bị sửa đổi và lịch sử hoàn toàn không phát sinh `EVENT_CORRECTED` trong các test case nhánh thất bại (`correctSignedContract_onlyAdminAllowedAndRequiresReasonAndTrimsTitle`, `correctSignedContract_draftOrFrozenContract_isRejected`, `correctSignedContract_invalidDatesOrDuplicateOrNegativeRootOrAmendmentExceeds_fails`, và test hạ giá gốc quá mức).

- [x] 🟡 `ContractService.java` › `checkSignedAmendmentValue()` (dòng ~502-518)
  - Vi phạm: UC-54 / SPEC §5.3 dòng 217 (khoản giảm trừ lớn hơn giá trị còn lại bị từ chối); cần xác nhận SPEC, **không tự đoán** (rules §1)
  - Mô tả: Hàm chỉ kiểm khi hợp đồng đang chữa là **phụ lục** có giá trị âm. Khi chữa **hợp đồng gốc** đã có phụ lục giảm trừ, giảm `contractValue` của hợp đồng gốc xuống dưới tổng phụ lục đã ký thì giá trị hiện hành (`currentValue`) có thể thành âm. Code và test đều không chặn và không bàn tới trường hợp này. Test `...NegativeRoot...` chỉ kiểm giá trị âm trực tiếp của hợp đồng gốc.
  - Gợi ý: Hỏi người dùng/SPEC xem trường hợp này có cần chặn không. Nếu cần, bổ sung kiểm tra `newRootValue + amendmentValueSigned < 0` kèm test. Nếu không cần, ghi chú rõ vào SPEC hoặc code.
  - *Đã sửa:* Đã bổ sung kiểm tra trong `checkSignedAmendmentValue` đảm bảo khi chữa hợp đồng gốc có phụ lục giảm trừ đã ký, nếu giá trị mới làm cho `contractValue + amendmentValueSigned < 0` thì lập tức từ chối với mã lỗi `ERR_VALUE_NEGATIVE` / `MSG_EXCEEDS_REMAINING_VALUE`. Bổ sung unit test `correctSignedContract_rootContractValueReducedBelowSignedAmendments_failsWithValueNegative`.

- [x] 🟢 `ContractControllerHelper.java` › `extractUpdateContract()` (dòng ~104) và `ContractActionResultHelper.java` › `redirectUpdateResult()` (nhánh `value_negative`, dòng ~32)
  - Vi phạm: Phạm vi task (mục D); tránh đổi hành vi cũ
  - Mô tả: Hai hàm này dùng chung cho **UC-35 (`handleUpdate`)**, không chỉ UC-54. Dòng thêm `c.setParentContractId(...)` và nhánh redirect `value_negative` làm đổi hành vi luồng cập nhật bản nháp phụ lục. Thay đổi hợp lý (không mất `parent_contract_id` khi lưu), nhưng nằm ngoài phạm vi UC-54. Trong phần diff đã xem, test controller mới chỉ thấy cho `handleCorrect`. Chưa thấy test khóa riêng hai thay đổi này ở luồng `update`.
  - Gợi ý sửa: Thêm 1 test cho `handleUpdate` (nháp phụ lục giữ `parentContractId` qua `extractUpdateContract`, và `ERR_VALUE_NEGATIVE` redirect `error=value_negative`). Báo người dùng biết phạm vi thay đổi này khi commit.
  - *Đã sửa:* Đã bổ sung unit test `handleUpdate_draftAmendment_preservesParentContractId_andRedirectsValueNegative` trong `ContractActionControllerTest` kiểm chứng luồng `update` bảo toàn `parentContractId` khi sửa nháp phụ lục và điều hướng đúng `&error=value_negative`.

- [x] 🟢 `ContractActionController.java` › `handleCorrect()` (dòng 176)
  - Vi phạm: rules §4 (phân quyền chỉ qua `PermissionService`)
  - Mô tả: Controller gọi `isAdmin(u)`, trong khi quy tắc nghiệp vụ nằm ở `canCorrectSignedContract(u)`. Hiện hai hàm cho cùng kết quả. Nếu SPEC đổi quyền UC-54, hai nơi có thể lệch nhau.
  - Gợi ý sửa:
    ```java
    if (!getPermissionService().canCorrectSignedContract(u)) { resp.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022); return; }
    ```
    Nếu đổi thì cập nhật các test dòng ~622-640, vì đang stub `isAdmin`.
  - *Đã sửa:* Đã cập nhật `handleCorrect` gọi qua `getPermissionService().canCorrectSignedContract(u)` và chuẩn hóa toàn bộ 16 test cases trong `ContractActionControllerTest` stub đúng phương thức phân quyền này.
